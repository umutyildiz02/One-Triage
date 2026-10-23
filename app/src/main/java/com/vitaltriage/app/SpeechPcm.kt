package com.vitaltriage.app

/** Matches the native near-silence gate before loading hundreds of MB of weights.
 * This is an energy filter, not a speech or clinical classifier. Never edits non-silent PCM.
 */
internal object SpeechPcm {
    fun hasSignal(samples:ShortArray):Boolean {
        if(samples.size !in 1600..29*16000)return false
        var energy=0.0
        var active=0
        for(sample in samples) {
            val normalized=sample/32768.0
            energy+=normalized*normalized
            if(kotlin.math.abs(sample.toInt())>400)active++
        }
        return kotlin.math.sqrt(energy/samples.size)>=0.003 && active>=160
    }
}
