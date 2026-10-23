package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real local audio -> Whisper -> NLLB. Assessment is a free mock and requires explicit consent. */
class NllbFlowTest {
    @Test fun realTurkishAudioKeepsOriginalThroughTranslationAndApproval() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val prefs=UserPreferences(context)
        val oldModel=prefs.whisperModel
        val oldTranslate=prefs.englishVoiceTranslation
        val oldEngine=prefs.translationEngine
        val oldTarget=prefs.translationTarget
        val oldLanguage=prefs.language
        val oldLanguages=prefs.patientLanguages
        val oldSpeech=prefs.activeSpeech
        val oldAuto=prefs.autoLanguage
        val oldDemo=prefs.demo
        val oldSetup=prefs.setupComplete
        val oldName=prefs.nickname
        val calls=AtomicInteger()
        val lowSignals=AtomicInteger()
        val sampleAt=java.util.concurrent.atomic.AtomicLong()
        val peakPss=java.util.concurrent.atomic.AtomicLong()
        val minimumAvailable=java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE)
        var received:String?=null
        var controller:SpeechController?=null
        val report=linkedMapOf<String,Any>("paidCalls" to 0,"assessment" to "mock",
            "audioSource" to "offline Turkish TTS waveform","hardwareMicrophone" to false)
        prefs.whisperModel=WhisperModel.MEDIUM
        prefs.englishVoiceTranslation=true;prefs.translationEngine="nllb";prefs.translationTarget=Language.EN
        prefs.language=Language.TR;prefs.patientLanguages=setOf(Language.TR);prefs.activeSpeech=Language.TR;prefs.autoLanguage=false
        prefs.setupComplete=true
        if(prefs.nickname.isBlank())prefs.nickname="Synthetic flow"
        val activity=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        val vm=TriageViewModel(AssessmentRepository { request,_,_ ->
            calls.incrementAndGet();received=request.symptoms
            Evaluation(Safety.unknown(request.language),"LIVE")
        })
        var audio:ShortArray?=null
        val start=SystemClock.elapsedRealtime()
        try {
            audio=WhisperEngineTest.syntheticSpeech("tr","Bu yapay bir testtir. İki gündür boğazım ağrıyor. Ateşim yok.")
            instrumentation.runOnMainSync {
                vm.attachPreferences(prefs);vm.demo(false)
                controller=SpeechController(context,vm,memoryPressure={
                    val info=android.app.ActivityManager.MemoryInfo()
                    context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
                    if(info.lowMemory)lowSignals.incrementAndGet()
                    minimumAvailable.updateAndGet { minOf(it,info.availMem/1024) }
                    val now=SystemClock.elapsedRealtime();val last=sampleAt.get()
                    if(now-last>=1000 && sampleAt.compareAndSet(last,now))peakPss.updateAndGet { maxOf(it,android.os.Debug.getPss()) }
                    info.lowMemory
                },translationModelDirectory={context.getExternalFilesDir("nllb")!!})
                controller!!.transcribePcm(audio!!)
            }
            val deadline=start+180000
            while(vm.state.value.listening && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
            val state=vm.state.value
            report["pipelineMs"]=SystemClock.elapsedRealtime()-start
            report["recognizedCharacters"]=state.symptoms.length
            report["translatedCharacters"]=state.speechTranslation?.length ?: 0
            report["speechError"]=state.speechError ?: "none"
            report["lowMemorySignals"]=lowSignals.get()
            report["sampledPeakPssKb"]=peakPss.get()
            report["minimumAvailableKb"]=minimumAvailable.get()
            assertTrue("Real Turkish transcript missing",state.symptoms.lowercase().contains("boğaz"))
            val translated=state.speechTranslation.orEmpty().lowercase()
            assertTrue("Real NLLB translation missing",translated.contains("throat") && translated.contains("fever"))
            assertFalse(state.translationFailed)
            assertEquals("nllb",state.translationEngine)
            assertEquals(0,calls.get());assertFalse(state.confirmed);assertNull(state.evaluation)
            instrumentation.runOnMainSync { vm.review();vm.analyze() }
            assertEquals(0,calls.get())
            val original=state.symptoms
            instrumentation.runOnMainSync { vm.confirm(true);vm.analyze() }
            val resultDeadline=SystemClock.elapsedRealtime()+5000
            while(vm.state.value.screen==Screen.ANALYZING && SystemClock.elapsedRealtime()<resultDeadline)SystemClock.sleep(20)
            assertEquals(1,calls.get());assertEquals(original,received)
            assertEquals(Screen.RESULT,vm.state.value.screen)
            report["explicitConsentAndOriginalPreserved"]=true
        } finally {
            audio?.fill(0)
            instrumentation.runOnMainSync { controller?.destroy() }
            activity.close()
            prefs.whisperModel=oldModel;prefs.englishVoiceTranslation=oldTranslate;prefs.translationEngine=oldEngine;prefs.translationTarget=oldTarget
            prefs.language=oldLanguage;prefs.patientLanguages=oldLanguages;prefs.activeSpeech=oldSpeech;prefs.autoLanguage=oldAuto
            prefs.demo=oldDemo;prefs.setupComplete=oldSetup;prefs.nickname=oldName
            File(context.getExternalFilesDir(null),"nllb-flow.json").writeText(Contract.gson.toJson(report))
        }
    }
}
