package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Real synthetic waveform -> real Whisper; inject memory pressure only at optional translation boundary. */
class NllbMemoryFlowTest {
    @Before fun before()=IsolatedTestState.reset(completed=true)
    @After fun after()=IsolatedTestState.finishActivities()
    @Test fun translationMemoryCancellationKeepsRecognizedOriginalWithoutSendingOrRestarting() {
        val context=IsolatedTestState.context
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val prefs=UserPreferences(context).apply {
            offlineWhisper=true;whisperModel=WhisperModel.MEDIUM
            englishVoiceTranslation=true;translationEngine="nllb";translationTarget=Language.FR
            patientLanguages=setOf(Language.EN);activeSpeech=Language.EN;autoLanguage=false
        }
        val pressure=AtomicBoolean(false)
        val boundary=AtomicInteger()
        val turns=AtomicInteger()
        val calls=AtomicInteger()
        val vm=TriageViewModel(AssessmentRepository { request,_,_->
            calls.incrementAndGet();Evaluation(Safety.unknown(request.language),"LIVE")
        })
        var controller:SpeechController?=null
        val audio=WhisperEngineTest.syntheticSpeech("en","This is a synthetic test. I have a sore throat today.")
        try {
            instrumentation.runOnMainSync {
                vm.attachPreferences(prefs);vm.demo(false)
                controller=SpeechController(context,vm,
                    memoryPressure={pressure.get() || SpeechMemory.isLow(context)},
                    translationModelDirectory={
                        boundary.incrementAndGet();pressure.set(true)
                        val end=SystemClock.elapsedRealtime()+5000
                        while(vm.state.value.speechError!="speechMemory" && SystemClock.elapsedRealtime()<end)SystemClock.sleep(20)
                        context.getExternalFilesDir("nllb")!!
                    },onUtterance={turns.incrementAndGet()})
                controller!!.transcribePcm(audio)
            }
            val end=SystemClock.elapsedRealtime()+150000
            while(vm.state.value.listening && SystemClock.elapsedRealtime()<end)SystemClock.sleep(50)
            val state=vm.state.value
            assertEquals(1,boundary.get())
            assertEquals("speechMemory",state.speechError)
            assertTrue("Completed real recognition must survive optional translation cancellation",
                state.symptoms.lowercase().contains("throat") && state.symptoms.lowercase().contains("today"))
            assertTrue(state.translationFailed);assertNull(state.speechTranslation)
            assertFalse(state.confirmed);assertNull(state.evaluation)
            assertFalse(state.listening);assertFalse(state.speechProcessing)
            assertEquals(0,calls.get());assertEquals(0,turns.get())
            instrumentation.runOnMainSync {vm.review();vm.analyze()}
            assertEquals(0,calls.get())
        } finally {
            pressure.set(false);audio.fill(0)
            instrumentation.runOnMainSync {controller?.destroy()}
        }
    }
}
