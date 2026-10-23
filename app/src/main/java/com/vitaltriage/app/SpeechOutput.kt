package com.vitaltriage.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vitaltriage.core.Language

/** Uses installed offline voices; explicit playback or an opted-in user-started conversation turn. */
class SpeechOutput(context: Context, private val changed: (Boolean) -> Unit, private val failure: () -> Unit) {
    private val preferences=UserPreferences(context.applicationContext)
    private val main=Handler(Looper.getMainLooper())
    private var ready=false
    private var closed=false
    private var speaking=false
    private var sequence=0L
    private var activeId:String?=null
    private var completion:((Boolean)->Unit)?=null
    private val startTimeout=Runnable {
        if(activeId!=null && !speaking) {
            val done=completion;stop();failure();done?.invoke(false)
        }
    }
    internal val isReady get()=ready && !closed
    private val tts=TextToSpeech(context.applicationContext) { status -> ready=status==TextToSpeech.SUCCESS && !closed }
    init {
        tts.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(id:String?) { main.post { if(!closed && id!=null && id==activeId) { main.removeCallbacks(startTimeout);speaking=true;changed(true) } } }
            override fun onDone(id:String?) { main.post { if(!closed && id!=null && id==activeId) {
                main.removeCallbacks(startTimeout)
                val done=completion;completion=null;activeId=null;speaking=false;changed(false);done?.invoke(true)
            } } }
            override fun onStop(id:String?,interrupted:Boolean) { main.post {
                if(!closed && id!=null && id==activeId) {
                    main.removeCallbacks(startTimeout)
                    val done=completion;completion=null;activeId=null;speaking=false;changed(false);done?.invoke(false)
                }
            } }
            @Deprecated("Framework callback") override fun onError(id:String?) { main.post {
                if(!closed && id!=null && id==activeId) {
                    main.removeCallbacks(startTimeout)
                    val done=completion;completion=null;activeId=null;speaking=false;changed(false);failure();done?.invoke(false)
                }
            } }
        })
    }
    fun toggle(text:String, language:Language) {
        if(activeId!=null) { stop();return }
        play(text,language)
    }
    fun play(text:String,language:Language,finished:(Boolean)->Unit={}) {
        if(activeId!=null)stop()
        if(!ready || closed || text.isBlank()) { failure();finished(false);return }
        val voice=tts.voices?.filter {
            Language.matchesVoice(language,it.locale) &&
                LegacyTtsQuality.allows(it.quality,it.features.orEmpty(),it.isNetworkConnectionRequired,preferences.languagesQualityLow)
        }?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }.thenBy { it.name })?.firstOrNull()
        if(voice==null || tts.setVoice(voice)!=TextToSpeech.SUCCESS) { failure();finished(false);return }
        val id="vitaltriage-output-${++sequence}"
        activeId=id;completion=finished
        main.postDelayed(startTimeout,15000)
        val result=tts.speak(text.take(TextToSpeech.getMaxSpeechInputLength()),TextToSpeech.QUEUE_FLUSH,null,id)
        if(result==TextToSpeech.ERROR) { main.removeCallbacks(startTimeout);activeId=null;completion=null;failure();finished(false) }
    }
    fun stop() { main.removeCallbacks(startTimeout);completion=null;activeId=null;tts.stop();speaking=false;changed(false) }
    fun close() { closed=true;ready=false;stop();tts.shutdown();main.removeCallbacksAndMessages(null) }
}
