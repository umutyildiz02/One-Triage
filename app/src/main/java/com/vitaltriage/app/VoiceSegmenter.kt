/*
 * Derived from RTranslator Recorder.java / Global.java.
 * Copyright 2016 Google Inc. and RTranslator contributors.
 * Licensed under the Apache License, Version 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0
 * Distributed AS IS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 * Changes: bounded PCM16 storage, sample-clock timing, exact millisecond pre-roll,
 * explicit one-utterance completion and independently testable capture logic.
 */
package com.vitaltriage.app

import kotlin.math.abs
import kotlin.math.roundToInt

data class CaptureSettings(val sensitivity:Int=50,val silenceMs:Int=1300,val preRollMs:Int=1300) {
    init { require(sensitivity in 0..100 && silenceMs in 100..5000 && preRollMs in 100..1800) }
    val threshold:Int get() {
        val fraction=1f-sensitivity/100f
        return if(fraction<0.5f) (400+1600*fraction*2).roundToInt()
        else (2000+13000*(fraction-0.5f)*2).roundToInt()
    }
}
/** One bounded utterance. Accept fixed 20 ms frames; silence timing uses samples, not wall clock. */
class VoiceSegmenter(val settings:CaptureSettings,private val automatic:Boolean) {
    private val pre=ShortArray(RATE*settings.preRollMs/1000)
    private var preHead=0;private var preSize=0
    private val utterance=ShortArray(MAX_SAMPLES)
    private var size=0;private var silence=0;private var elapsed=0
    private var started=!automatic
    var finished=false; private set
    var hasVoice=false; private set
    var level=0f; private set
    val capturedSamples:Int get()=size
    val bufferedSamples:Int get()=preSize+size
    fun accept(frame:ShortArray,count:Int=frame.size):Boolean {
        require(count in 0..frame.size && count<=FRAME)
        if(finished || count==0)return finished
        elapsed+=count
        var above=0;var sum=0L
        for(i in 0 until count) { val amplitude=abs(frame[i].toInt());sum+=amplitude;if(amplitude>settings.threshold)above++ }
        val voiced=above>=15
        level=(sum.toFloat()/count/settings.threshold*2).coerceIn(0f,1f)
        if(voiced)hasVoice=true
        if(!started && voiced) {
            started=true
            for(i in 0 until preSize) append(pre[(preHead-preSize+i+pre.size)%pre.size])
            preSize=0
        }
        if(started) {
            for(i in 0 until count)append(frame[i])
            silence=if(voiced)0 else silence+count
            if(automatic && silence>=RATE*settings.silenceMs/1000)finished=true
        } else {
            for(i in 0 until count) { pre[preHead]=frame[i];preHead=(preHead+1)%pre.size;preSize=(preSize+1).coerceAtMost(pre.size) }
        }
        if(elapsed>=RATE*30)finished=true
        return finished
    }
    private fun append(value:Short) { if(size<utterance.size)utterance[size++]=value; if(size==utterance.size)finished=true }
    fun finish():ShortArray { finished=true;return utterance.copyOf(size) }
    fun clear() { pre.fill(0);utterance.fill(0);size=0;preSize=0;finished=true }
    companion object { const val RATE=16000;const val FRAME=320;const val MAX_SAMPLES=RATE*29 }
}
