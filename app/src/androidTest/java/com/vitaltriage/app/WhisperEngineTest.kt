package com.vitaltriage.app

import android.media.AudioFormat
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real offline TTS waveform -> real local Whisper. No prewritten transcript injection. */
class WhisperEngineTest {
    private var previousModel:WhisperModel?=null
    private var activity:androidx.test.core.app.ActivityScenario<MainActivity>?=null
    @org.junit.Before fun selectRequestedModel() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val prefs=UserPreferences(context)
        previousModel=prefs.whisperModel
        prefs.whisperModel=if(InstrumentationRegistry.getArguments().getString("qualityModel")=="medium-q5")
            WhisperModel.MEDIUM else WhisperModel.SMALL
        prefs.setupComplete=true
        if(prefs.nickname.isBlank())prefs.nickname="Native model diagnostic"
        activity=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
    }
    @org.junit.After fun restoreModel() {
        activity?.close();IsolatedTestState.finishActivities()
        previousModel?.let { UserPreferences(InstrumentationRegistry.getInstrumentation().targetContext).whisperModel=it }
    }
    @Test fun englishPcmManualAndAutomaticRecognition() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val report=linkedMapOf<String,Any>("paidCalls" to 0,"engine" to "whisper.cpp-v1.8.0","abi" to Build.SUPPORTED_ABIS.first())
        report["model"]=UserPreferences(context).whisperModel.key
        report["availableProcessors"]=Runtime.getRuntime().availableProcessors()
        report["selectedThreads"]=WhisperThreads.select(Build.SUPPORTED_ABIS.first(),Runtime.getRuntime().availableProcessors())
        val engine=WhisperEngine(context)
        val audio=syntheticSpeech("en","This is a synthetic test. My throat hurts today.")
        try {
            val start=SystemClock.elapsedRealtime()
            engine.begin();engine.load()
            report["modelLoadMs"]=SystemClock.elapsedRealtime()-start
            report["cpuFeatures"]=WhisperNative.systemInfo()
            for(language in listOf("en","auto")) {
                engine.begin()
                val began=SystemClock.elapsedRealtime()
                val cpuStart=android.os.Process.getElapsedCpuTime()
                val result=engine.transcribe(audio,language)
                report[language+"Ms"]=SystemClock.elapsedRealtime()-began
                report[language+"CpuMs"]=android.os.Process.getElapsedCpuTime()-cpuStart
                report[language+"PssKb"]=Debug.getPss()
                val memory=android.app.ActivityManager.MemoryInfo()
                context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(memory)
                report[language+"AvailableMemoryBytes"]=memory.availMem
                report[language+"LowMemory"]=memory.lowMemory
                report[language+"Detected"]=result?.language ?: ""
                report[language+"Characters"]=result?.text?.length ?: 0
                report[language+"ExpectedWords"]=result?.text?.lowercase()?.let { "throat" in it && "today" in it }==true
                assertNotNull("Real native transcription missing for $language",result)
                assertEquals("en",result!!.language)
                assertTrue("Expected synthetic words missing for $language",report[language+"ExpectedWords"]==true)
                engine.cancel() // next begin() must recover from cancellation
            }
        } finally {
            audio.fill(0);engine.close()
            val suffix=if(UserPreferences(context).whisperModel==WhisperModel.MEDIUM)"-medium" else ""
            File(context.getExternalFilesDir(null),"whisper-probe$suffix.json").writeText(Contract.gson.toJson(report))
        }
    }
    @Test fun nativeSilenceCancellationAndInvalidModel() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val invalid=File(context.cacheDir,"invalid-model.bin")
        invalid.writeBytes(byteArrayOf(0,1,2,3))
        try { assertEquals(0L,WhisperNative.load(invalid.absolutePath,context.applicationInfo.nativeLibraryDir)) } finally { invalid.delete() }
        assertEquals(0L,WhisperNative.load(invalid.absolutePath,context.applicationInfo.nativeLibraryDir))
        val engine=WhisperEngine(context)
        try {
            engine.begin();engine.load()
            assertNull(engine.transcribe(ShortArray(16000),"auto"))
            engine.cancel()
            assertNull(engine.transcribe(ShortArray(16000){1000},"en"))
        } finally { engine.close() }
    }

    companion object {
        fun syntheticSpeech(language:String,text:String):ShortArray {
            val instrumentation=InstrumentationRegistry.getInstrumentation()
            val context=instrumentation.targetContext
            val ready=CountDownLatch(1);val done=CountDownLatch(1)
            val bytes=ByteArrayOutputStream()
            var rate=0;var encoding=0;var channels=0;var success=false
            var initializationStatus=TextToSpeech.ERROR
            lateinit var tts:TextToSpeech
            val file=File(context.cacheDir,"whisper-synthetic.wav")
            try {
                instrumentation.runOnMainSync { tts=TextToSpeech(context){status->initializationStatus=status;ready.countDown()} }
                assertTrue("TTS initialization timeout",ready.await(20,TimeUnit.SECONDS))
                assertEquals("TTS initialization failed",TextToSpeech.SUCCESS,initializationStatus)
                // Some device engines publish their installed catalog shortly after onInit.
                // Wait only for the same installed offline voice; never download or use a network voice.
                var voice:android.speech.tts.Voice?=null
                val catalogDeadline=SystemClock.elapsedRealtime()+5000
                do {
                    instrumentation.runOnMainSync {
                        voice=tts.voices.orEmpty().filter { it.locale.language==language && !it.isNetworkConnectionRequired &&
                            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }.maxByOrNull { it.quality }
                    }
                    if(voice==null)SystemClock.sleep(100)
                } while(voice==null && SystemClock.elapsedRealtime()<catalogDeadline)
                instrumentation.runOnMainSync {
                    assertNotNull("No installed offline synthetic voice: $language",voice)
                    tts.voice=voice
                    tts.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
                        override fun onStart(id:String?){}
                        override fun onBeginSynthesis(id:String?,sampleRateInHz:Int,audioFormat:Int,channelCount:Int){rate=sampleRateInHz;encoding=audioFormat;channels=channelCount}
                        override fun onAudioAvailable(id:String?,audio:ByteArray?){if(audio!=null && bytes.size()+audio.size<3_000_000)bytes.write(audio)}
                        override fun onDone(id:String?){success=true;done.countDown()}
                        @Deprecated("Framework callback") override fun onError(id:String?){done.countDown()}
                    })
                    assertEquals(TextToSpeech.SUCCESS,tts.synthesizeToFile(text,Bundle(),file,"whisper-probe"))
                }
                assertTrue("Synthetic audio timeout",done.await(30,TimeUnit.SECONDS))
                assertTrue(success);assertEquals(AudioFormat.ENCODING_PCM_16BIT,encoding);assertEquals(1,channels)
                val raw=bytes.toByteArray();val count=raw.size/2
                assertTrue(count>1000)
                fun sample(i:Int):Int { val p=i.coerceIn(0,count-1)*2;return ((raw[p].toInt() and 255) or (raw[p+1].toInt() shl 8)).toShort().toInt() }
                val audio=ShortArray((count.toLong()*16000/rate).toInt()){i->
                    val pos=i.toDouble()*rate/16000;val left=pos.toInt();val fraction=pos-left
                    (sample(left)*(1-fraction)+sample(left+1)*fraction).toInt().toShort()
                }
                raw.fill(0)
                return audio
            } finally { instrumentation.runOnMainSync { runCatching { tts.shutdown() } };file.delete() }
        }
    }
}
