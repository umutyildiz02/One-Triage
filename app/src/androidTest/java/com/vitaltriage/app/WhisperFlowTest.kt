package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class WhisperFlowTest {
    @Test fun actualPcmThroughCaptureSegmentationToEditableUnapprovedText() = verify(WhisperModel.SMALL)
    @Test fun mediumPcmUsesProductionMemoryGuardAndStillRequiresApproval() = verify(WhisperModel.MEDIUM)
    private fun verify(model:WhisperModel) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val prefs=UserPreferences(context)
        val previous=prefs.whisperModel
        prefs.whisperModel=model;prefs.setupComplete=true
        if(prefs.nickname.isBlank())prefs.nickname="Synthetic controller test"
        val activity=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        val calls=AtomicInteger()
        val vm=TriageViewModel(AssessmentRepository { _,_,_-> calls.incrementAndGet();error("No automatic request allowed") })
        val audio=WhisperEngineTest.syntheticSpeech("en","This is a synthetic test. My throat hurts today.")
        val segment=VoiceSegmenter(CaptureSettings(),true)
        val report=linkedMapOf<String,Any>("paidCalls" to 0,"waveformSource" to "real offline TTS","hardwareMicrophone" to false,"model" to model.key)
        var controller:SpeechController?=null
        try {
            var offset=0
            while(offset<audio.size && !segment.finished) {
                val frame=audio.copyOfRange(offset,minOf(offset+VoiceSegmenter.FRAME,audio.size))
                segment.accept(frame);frame.fill(0);offset+=VoiceSegmenter.FRAME
            }
            repeat(65){if(!segment.finished)segment.accept(ShortArray(VoiceSegmenter.FRAME))}
            assertTrue(segment.hasVoice)
            val utterance=segment.finish()
            report["samples"]=utterance.size
            instrumentation.runOnMainSync {
                vm.activeLanguage(Language.EN);vm.autoLanguage(false)
                controller=SpeechController(context,vm)
                controller!!.transcribePcm(utterance)
            }
            val deadline=SystemClock.elapsedRealtime()+140000
            while(vm.state.value.listening && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
            val text=vm.state.value.symptoms
            report["recognizedCharacters"]=text.length
            report["expectedWords"]=text.lowercase().let { "throat" in it && "today" in it }
            assertTrue("Native transcript did not reach the editor",report["expectedWords"]==true)
            assertFalse(vm.state.value.confirmed);assertNull(vm.state.value.evaluation);assertEquals(0,calls.get())
            instrumentation.runOnMainSync { vm.review() }
            assertEquals(Screen.REVIEW,vm.state.value.screen)
            assertEquals(text,vm.state.value.symptoms)
            report["explicitApprovalRequired"]=true
        } finally {
            audio.fill(0);segment.clear()
            instrumentation.runOnMainSync { controller?.destroy() }
            activity.close();prefs.whisperModel=previous
            val name=if(model==WhisperModel.MEDIUM)"whisper-flow-medium.json" else "whisper-flow.json"
            File(context.getExternalFilesDir(null),name).writeText(Contract.gson.toJson(report))
        }
    }
}
