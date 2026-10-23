package com.vitaltriage.app

import com.vitaltriage.core.Language

/**
 * Source WalkieTalkieService ordering: capture -> text/translation -> TTS done -> next capture.
 * This adapter never approves/shares/assesses text. A user-started automatic session alone repeats.
 * All callbacks run on the main thread. Revision checks reject late TTS/scheduled callbacks.
 */
internal class WalkieTurnLoop(
    private val capture:()->Unit,
    private val stopCapture:()->Unit,
    private val play:(String,Language,(Boolean)->Unit)->Unit,
    private val stopPlayback:()->Unit,
    private val automaticChanged:(Boolean)->Unit,
    private val schedule:((()->Unit))->Unit
) {
    var active=false
        private set
    private var automatic=false
    private var revision=0
    fun start(continuous:Boolean) {
        stop()
        active=true;automatic=continuous
        automaticChanged(continuous)
        capture()
    }
    fun completed(text:String,language:Language,readAloud:Boolean) {
        if(!active)return
        stopCapture()
        val current=revision
        fun advance() {
            if(!active || current!=revision)return
            if(!automatic){stop();return}
            // A brief acoustic tail gap is supplied by Android; never listen while TTS is active.
            schedule { if(active && current==revision)capture() }
        }
        if(readAloud && text.isNotBlank())play(text,language) { success ->
            if(active && current==revision) {
                if(success)advance() else stop()
            }
        } else advance()
    }
    fun stop() {
        revision++;active=false;automatic=false
        automaticChanged(false)
        stopCapture();stopPlayback()
    }
}
