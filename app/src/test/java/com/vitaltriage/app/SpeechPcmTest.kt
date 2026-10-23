package com.vitaltriage.app

import org.junit.Assert.*
import org.junit.Test

class SpeechPcmTest {
    @Test fun silenceNoiseAndTooShortFramesDoNotNeedModel() {
        assertFalse(SpeechPcm.hasSignal(shortArrayOf()))
        assertFalse(SpeechPcm.hasSignal(ShortArray(16000)))
        assertFalse(SpeechPcm.hasSignal(ShortArray(16000){7}))
        assertFalse(SpeechPcm.hasSignal(ShortArray(1599){2000}))
        assertFalse(SpeechPcm.hasSignal(ShortArray(29*16000+1){2000}))
    }
    @Test fun isolatedClicksAreRejectedButVoiceEnergyAndNegativePeakRemainUntouched() {
        val click=ShortArray(16000)
        repeat(159){click[it]=Short.MIN_VALUE}
        assertFalse(SpeechPcm.hasSignal(click))
        click[159]=Short.MIN_VALUE
        val original=click.copyOf()
        assertTrue(SpeechPcm.hasSignal(click))
        assertArrayEquals(original,click)
        val wave=ShortArray(16000){if(it%2==0)1500 else -1500}
        assertTrue(SpeechPcm.hasSignal(wave))
    }
}
