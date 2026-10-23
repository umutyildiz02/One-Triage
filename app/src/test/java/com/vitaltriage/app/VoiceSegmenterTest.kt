package com.vitaltriage.app
import org.junit.Assert.*
import org.junit.Test

class VoiceSegmenterTest {
    private fun frame(value:Int)=ShortArray(320){value.toShort()}
    @Test fun preservesOriginalSensitivityCurve() {
        assertEquals(15000,CaptureSettings(0).threshold)
        assertEquals(2000,CaptureSettings(50).threshold)
        assertEquals(400,CaptureSettings(100).threshold)
    }
    @Test fun isolatedSpikesDoNotTriggerAndPreRollPreservesMilliseconds() {
        val segment=VoiceSegmenter(CaptureSettings(preRollMs=1300),true)
        repeat(70){segment.accept(frame(100))}
        val noise=frame(100);repeat(14){noise[it]=3000}
        segment.accept(noise);assertFalse(segment.hasVoice);assertEquals(0,segment.capturedSamples)
        segment.accept(frame(3000))
        val samples=segment.finish()
        assertEquals(20800+320,samples.size)
        assertEquals(100,samples.first().toInt())
        assertEquals(3000,samples.last().toInt())
    }
    @Test fun shortPauseDoesNotCutSpeechAndSilenceEndsAtConfiguredDuration() {
        val segment=VoiceSegmenter(CaptureSettings(silenceMs=1300),true)
        segment.accept(frame(4000))
        repeat(64){assertFalse(segment.accept(frame(0)))}
        assertTrue(segment.accept(frame(0)))
    }
    @Test fun sensitivityAndTimeoutValuesActuallyChangeSegmentation() {
        val sensitive=VoiceSegmenter(CaptureSettings(100,100,100),true)
        val insensitive=VoiceSegmenter(CaptureSettings(0,5000,100),true)
        sensitive.accept(frame(500));insensitive.accept(frame(500))
        assertTrue(sensitive.hasVoice);assertFalse(insensitive.hasVoice)
        repeat(5){sensitive.accept(frame(0))}
        assertTrue(sensitive.finished);assertFalse(insensitive.finished)
    }
    @Test fun manualCaptureKeepsSilenceUntilStopAndCapsDuration() {
        val segment=VoiceSegmenter(CaptureSettings(),false)
        repeat(100){assertFalse(segment.accept(frame(0)))}
        repeat(2000){segment.accept(frame(100))}
        assertTrue(segment.finished);assertEquals(VoiceSegmenter.MAX_SAMPLES,segment.finish().size)
    }
    @Test fun waitingForVoiceIsBoundedAndCancellationWipesBufferedAudio() {
        val segment=VoiceSegmenter(CaptureSettings(),true)
        repeat(1500){segment.accept(frame(0))}
        assertTrue(segment.finished);assertEquals(0,segment.finish().size)
        assertTrue(segment.bufferedSamples<=20800)
        segment.clear();assertEquals(0,segment.bufferedSamples)
    }
}
