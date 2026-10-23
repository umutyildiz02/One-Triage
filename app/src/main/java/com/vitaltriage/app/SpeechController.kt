package com.vitaltriage.app
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.*
import androidx.core.content.ContextCompat

class SpeechController(private val context:Context,private val vm:TriageViewModel,
    private val memoryPressure:()->Boolean={SpeechMemory.isLow(context)},
    private val translationModelDirectory:()->java.io.File={NllbModels.directory(context)},
    private val onUtterance:(UiState)->Unit={}) {
    private var recognizer:SpeechRecognizer?=null
    private var accepting=false
    private val capture=SourceAudioCapture()
    private val whisper=WhisperEngine(context.applicationContext)
    @Volatile private var nllb:NllbEngine?=null
    private val whisperWorker=java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it,"vt-whisper") }
    @Volatile private var generation=0
    private data class PendingRecognized(val generation:Int,val text:WhisperText,val engine:String,val target:String)
    @Volatile private var pendingRecognized:PendingRecognized?=null
    private var processing=false
    private var audioPipe:Array<android.os.ParcelFileDescriptor>?=null
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private val timeout=Runnable { if(accepting){close();vm.speechError("speechNoMatch")} }
    private val memoryCheck=object:Runnable {
        override fun run() {
            if(!processing)return
            if(memoryPressure()) {
                val recognized=pendingRecognized?.takeIf { it.generation==generation }
                releaseModel()
                // Translation is optional: memory cancellation must not lose completed recognition.
                recognized?.let { vm.utterance(it.text.text,it.text.language,null,true,it.engine,it.target) }
                vm.speechError("speechMemory")
            }
            else handler.postDelayed(this,1000)
        }
    }
    fun start(audio: android.os.ParcelFileDescriptor? = null, sampleRate: Int = 16000) {
        close()
        val sessionState=vm.state.value
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){vm.permission(false);return}
        if(audio==null && UserPreferences(context).offlineWhisper) {
            if(!vm.state.value.autoLanguage && vm.state.value.activeSpeech.whisperTag==null){vm.speechError("speechLanguageUnsupported");return}
            vm.detected(null);vm.listening(true)
            capture.start(UserPreferences(context).captureSettings(),vm.state.value.autoLanguage,vm::speechLevel,::transcribePcm,vm::speechError)
            return
        }
        if(sessionState.autoLanguage && android.os.Build.VERSION.SDK_INT<34){vm.speechError("speechLanguageUncertain");return}
        if(!SpeechRecognizer.isRecognitionAvailable(context)){vm.speechError("speechUnavailable");return}
        if(audio==null && UserPreferences(context).sourceCapture) {
            val prefs=UserPreferences(context)
            vm.detected(null);vm.listening(true)
            capture.start(prefs.captureSettings(),vm.state.value.autoLanguage,vm::speechLevel,::recognizeCaptured,vm::speechError)
            return
        }
        try {
            // Availability alone does not mean an on-device language model is installed.
            // Use the configured provider; do not silently choose an unprepared on-device engine.
            val sr=SpeechRecognizer.createSpeechRecognizer(context)
            recognizer=sr; accepting=true
            sr.setRecognitionListener(object:RecognitionListener {
                override fun onReadyForSpeech(params:Bundle?){}
                override fun onBeginningOfSpeech(){}
                override fun onRmsChanged(rmsdB:Float){if(accepting && recognizer===sr)vm.speechLevel(rmsdB/10f)}
                override fun onBufferReceived(buffer:ByteArray?){}
                override fun onEndOfSpeech(){}
                override fun onError(error:Int){
                    if(!accepting || recognizer!==sr)return
                    handler.removeCallbacks(timeout)
                    accepting=false
                    releasePipe()
                    vm.speechError(when(error){
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission"
                        SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "speechNetwork"
                        SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speechNoMatch"
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "speechLanguageUnsupported"
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "speechLanguageUnavailable"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "speechBusy"
                        SpeechRecognizer.ERROR_AUDIO -> "speechAudio"
                        else -> "speechUnavailable"
                    })
                }
                override fun onResults(results:Bundle?){
                    if(!accepting || recognizer!==sr)return
                    handler.removeCallbacks(timeout)
                    accepting=false
                    releasePipe()
                    val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if(text.isNullOrBlank()) vm.speechError("speechNoMatch")
                    else {
                        val language=SpeechLanguagePolicy.resolve(sessionState.autoLanguage,sessionState.activeSpeech,vm.state.value.detectedLanguage,false)
                        if(language==null)vm.speechError("speechLanguageUncertain")
                        else {
                            val current=++generation
                            val prefs=UserPreferences(context)
                            processing=true;vm.processingSpeech()
                            handler.removeCallbacks(memoryCheck);handler.postDelayed(memoryCheck,1000)
                            whisperWorker.execute {
                                completeRecognized(current,WhisperText(language.tag,text),prefs.englishVoiceTranslation,prefs.translationEngine,prefs.translationTarget.tag)
                            }
                        }
                    }
                }
                override fun onPartialResults(partialResults:Bundle?){
                    if(accepting && recognizer===sr)vm.partial(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onEvent(eventType:Int,params:Bundle?){}
                override fun onLanguageDetection(results:Bundle){
                    if(accepting && recognizer===sr && sessionState.autoLanguage && Build.VERSION.SDK_INT>=34) {
                        val detected=com.vitaltriage.core.Language.fromSpeechTag(results.getString(SpeechRecognizer.DETECTED_LANGUAGE))?.tag
                        val confidence=results.getInt(SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL)
                        val switchResult=results.getInt(SpeechRecognizer.LANGUAGE_SWITCH_RESULT,SpeechRecognizer.LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED)
                        if(switchResult==SpeechRecognizer.LANGUAGE_SWITCH_RESULT_FAILED ||
                            switchResult==SpeechRecognizer.LANGUAGE_SWITCH_RESULT_SKIPPED_NO_MODEL)
                            vm.detected(null)
                        else vm.detected(detected.takeIf {
                            confidence>=SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT
                        })
                    }
                }
            })
            val state=vm.state.value
            vm.detected(null)
            vm.listening(true)
            handler.postDelayed(timeout,30000)
            sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,state.activeSpeech.speechTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                if(audio!=null && Build.VERSION.SDK_INT>=33) {
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,audio)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,1)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,android.media.AudioFormat.ENCODING_PCM_16BIT)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,sampleRate)
                }
                if(Build.VERSION.SDK_INT>=34 && state.autoLanguage){
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,true)
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                    // Let the provider detect/switch among its installed languages, not a UI favorites list.
                }
            })
        } catch(_:SecurityException){vm.permission(false);close()}
          catch(_:Exception){vm.speechError("speechUnavailable");close()}
    }
    /** Explicit Developer/Test file import; not a microphone-capture diagnostic. */
    fun importWave(uri:android.net.Uri) {
        close()
        if(!UserPreferences(context).offlineWhisper){vm.speechError("speechModelUnavailable");return}
        val current=++generation
        processing=true;vm.detected(null);vm.processingSpeech()
        whisperWorker.execute {
            if(current!=generation)return@execute
            var samples:ShortArray?=null
            try {
                context.contentResolver.openInputStream(uri)?.use { input->
                    val output=java.io.ByteArrayOutputStream()
                    val buffer=ByteArray(8192)
                    while(true) {
                        val count=input.read(buffer)
                        if(count<0)break
                        require(output.size()+count<=WaveAudio.MAX_BYTES)
                        output.write(buffer,0,count)
                    }
                    buffer.fill(0)
                    val bytes=output.toByteArray()
                    try { samples=WaveAudio.decode(bytes) } finally { bytes.fill(0) }
                }
            } catch(_:Exception) { /* safe localized error; no URI/content logging */ }
            val audio=samples
            handler.post {
                if(current!=generation)audio?.fill(0)
                else if(audio==null){processing=false;vm.speechError("audioFileError")}
                else transcribePcm(audio)
            }
        }
    }
    fun stop(){
        if(processing){close();vm.listening(false);return}
        if(capture.running){capture.finish();return}
        handler.removeCallbacks(timeout)
        try { recognizer?.stopListening(); handler.postDelayed(timeout,5000) }
        catch(_:Exception){close();vm.speechError("speechUnavailable")}
    }
    /** Consumes real 16 kHz PCM, from capture or a diagnostic waveform; never a transcript. */
    internal fun transcribePcm(samples:ShortArray) {
        if(memoryPressure()){releaseModel();samples.fill(0);vm.speechError("speechMemory");return}
        val current=++generation
        val state=vm.state.value
        val language=if(state.autoLanguage)"auto" else state.activeSpeech.tag
        val prefs=UserPreferences(context)
        val translate=prefs.englishVoiceTranslation
        val translationEngine=prefs.translationEngine
        val target=prefs.translationTarget.tag
        processing=true;vm.processingSpeech()
        handler.removeCallbacks(memoryCheck);handler.postDelayed(memoryCheck,1000)
        whisperWorker.execute {
            if(current!=generation){samples.fill(0);return@execute}
            var result:WhisperText?=null
            var error:String?=null
            try {
                whisper.begin()
                if(current==generation)result=whisper.transcribe(samples,language)
                val original=result
                if(original!=null) {
                    val resolved=SpeechLanguagePolicy.resolve(state.autoLanguage,state.activeSpeech,original.language,true)
                    if(resolved==null)error="speechLanguageUnsupported"
                    else {
                        completeRecognized(current,original.copy(language=resolved.tag),translate,translationEngine,target)
                        return@execute
                    }
                }
            } catch(_:SpeechMemoryException){error="speechMemory"}
              catch(_:SpeechTimeoutException){error="speechTimeout"}
              catch(_:Exception){error="speechModelUnavailable"}
              catch(_:LinkageError){error="speechModelUnavailable"}
            finally { samples.fill(0);if(current!=generation)pendingRecognized=null }
            handler.post {
                if(current==generation) {
                    handler.removeCallbacks(memoryCheck)
                    pendingRecognized=null
                    processing=false
                    when {
                        error!=null -> vm.speechError(error!!)
                        result==null -> vm.speechError("speechNoMatch")
                        else -> vm.speechError("speechLanguageUncertain")
                    }
                }
            }
        }
    }
    /** Shared post-recognition path for Whisper and Android. Never submits an assessment. */
    private fun completeRecognized(current:Int,original:WhisperText,translate:Boolean,engine:String,target:String) {
        if(current!=generation)return
        pendingRecognized=PendingRecognized(current,original,engine,target)
        var translated:String?=null
        if(translate && original.language!=target) {
            try { translated=translateLocal(original.text,original.language,target,engine,current) }
            catch(_:Exception) { /* Keep the recognized original for review and preparation/retry. */ }
            catch(_:LinkageError) { /* Missing native translation must not discard recognition. */ }
        }
        val failed=original.language!=target && translated.isNullOrBlank()
        handler.post {
            if(current==generation) {
                handler.removeCallbacks(memoryCheck)
                pendingRecognized=null;processing=false
                vm.utterance(original.text,original.language,translated,failed,engine,target)
                if(vm.state.value.speechError==null)onUtterance(vm.state.value)
            }
        }
    }
    private fun translateLocal(original:String,source:String,target:String,engineName:String,current:Int):String? {
        if(current!=generation)return null
        if(engineName!="nllb")return LocalTextTranslation.translate(original,source,target)
        // Serialized on the same worker: Whisper has fully released before NLLB allocation.
        whisper.close()
        check(!memoryPressure())
        val directory=translationModelDirectory()
        check(NllbModels.verified(directory))
        if(current!=generation)return null
        return NllbEngine(directory,memoryPressure=memoryPressure).use { engine ->
            nllb=engine
            try { if(current!=generation)engine.cancel();engine.translate(original,source,target) }
            finally { nllb=null }
        }
    }
    /** Explicit review action may perform opted-in local translation, never OpenAI submission. */
    fun reviewWithTranslation() {
        close();vm.listening(false);vm.review()
        val snapshot=vm.state.value
        if(snapshot.screen!=Screen.REVIEW)return
        val prefs=UserPreferences(context)
        if(!prefs.englishVoiceTranslation)return
        val target=prefs.translationTarget.tag
        val source=snapshot.currentMessageLanguage
        if(snapshot.speechTranslation!=null && snapshot.translationTarget==target && snapshot.translationEngine==prefs.translationEngine)return
        vm.translationStarted()
        if(source==target) {
            vm.reviewedTranslation(snapshot.currentMessageId,snapshot.symptoms,null,target,prefs.translationEngine,false)
            return
        }
        val engine=prefs.translationEngine
        val current=++generation
        whisperWorker.execute {
            var translated:String?=null
            try {
                if(current==generation)translated=translateLocal(snapshot.symptoms,source,target,engine,current)
            } catch(_:Exception) { /* Local optional failure preserves the original and renewed consent. */ }
              catch(_:LinkageError) { /* Unsupported native runtime is not a fabricated translation. */ }
            handler.post {
                if(current==generation)vm.reviewedTranslation(snapshot.currentMessageId,snapshot.symptoms,translated,target,engine,translated.isNullOrBlank())
            }
        }
    }
    private fun recognizeCaptured(samples:ShortArray) {
        val pipe=try { android.os.ParcelFileDescriptor.createPipe() } catch(_:Exception) {
            samples.fill(0);vm.speechError("speechAudio");return
        }
        start(pipe[0],VoiceSegmenter.RATE)
        audioPipe=pipe
        if(!vm.state.value.listening){releasePipe();samples.fill(0);return}
        Thread({
            val bytes=ByteArray(samples.size*2)
            try {
                for(i in samples.indices){bytes[i*2]=samples[i].toByte();bytes[i*2+1]=(samples[i].toInt() shr 8).toByte()}
                samples.fill(0)
                android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(bytes) }
            } catch(_:Exception) {
                // The provider may reject the supplied source. Its callback/timeout reports failure.
            } finally { samples.fill(0);bytes.fill(0) }
        },"vt-speech-pipe").apply { isDaemon=true;start() }
    }
    private fun releasePipe(){audioPipe?.forEach { runCatching { it.close() } };audioPipe=null}
    fun close(){
        ++generation;processing=false;pendingRecognized=null;handler.removeCallbacks(memoryCheck);whisper.cancel();nllb?.cancel();vm.translationCancelled()
        capture.cancel();releasePipe();handler.removeCallbacks(timeout);accepting=false
        runCatching{recognizer?.cancel()};runCatching{recognizer?.destroy()};recognizer=null
    }
    /** Release large native weights after leaving the screen; never free while inference owns them. */
    fun releaseModel(){close();if(!whisperWorker.isShutdown)whisperWorker.execute { whisper.close() }}
    /** Await the inference worker's release before another large model is loaded. */
    suspend fun releaseForTranslation() {
        close()
        if(whisperWorker.isShutdown)return
        val released=kotlinx.coroutines.CompletableDeferred<Unit>()
        whisperWorker.execute {
            runCatching { whisper.close() }.fold({released.complete(Unit)},{released.completeExceptionally(it)})
        }
        released.await()
    }
    fun destroy(){releaseModel();capture.destroy();whisperWorker.shutdown()}
}
