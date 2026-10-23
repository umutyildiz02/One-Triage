package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Fixed synthetic written originals -> actual local NLLB -> explicit review -> free mock assessment. */
class NllbReviewFlowTest {
    private fun arabicNegatedFever(text:String)=text.contains("حمى") && listOf("لا","ليس","لست").any(text::contains)
    @Test fun fourTargetsUseRealTranslationAndNeverReplaceAssessmentOriginal() {
        assertTrue(arabicNegatedFever("أنا لستُ مصابًا بالحمى."))
        assertFalse(arabicNegatedFever("أنا مصاب بالحمى."))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val prefs=UserPreferences(context)
        val oldTarget=prefs.translationTarget;val oldEngine=prefs.translationEngine
        val oldEnabled=prefs.englishVoiceTranslation;val oldSetup=prefs.setupComplete;val oldName=prefs.nickname
        prefs.setupComplete=true;prefs.nickname="Synthetic review"
        prefs.englishVoiceTranslation=true;prefs.translationEngine="nllb"
        val calls=AtomicInteger();var received:String?=null
        val vm=TriageViewModel(AssessmentRepository { r,_,_->calls.incrementAndGet();received=r.symptoms;Evaluation(Safety.unknown(r.language),"LIVE") })
        var controller:SpeechController?=null
        val activity=ActivityScenario.launch(MainActivity::class.java)
        val rows=mutableListOf<Map<String,Any>>()
        val cases=listOf(
            Triple("en",Language.TR,"I have no fever."),
            Triple("tr",Language.FR,"Ateşim yok."),
            Triple("fr",Language.AR,"Je n'ai pas de fièvre."),
            Triple("ar",Language.EN,"ليس لدي حمى.")
        )
        try {
            instrumentation.runOnMainSync {
                vm.demo(false)
                controller=SpeechController(context,vm,translationModelDirectory={context.getExternalFilesDir("nllb")!!})
            }
            for((source,target,original) in cases) {
                prefs.translationTarget=target
                val start=SystemClock.elapsedRealtime()
                instrumentation.runOnMainSync {
                    vm.utterance(original,source)
                    controller!!.reviewWithTranslation()
                }
                val deadline=start+90000
                while(vm.state.value.translationBusy && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
                val state=vm.state.value
                val output=state.speechTranslation.orEmpty().lowercase(java.util.Locale.forLanguageTag(target.tag))
                val meaning=when(target) {
                    Language.EN->output.contains("fever") && Regex("\\b(no|not|without)\\b|n't").containsMatchIn(output)
                    Language.TR->output.contains("ateş") && (output.contains("yok") || output.contains("değil"))
                    Language.FR->output.contains("fièvre") && (output.contains("pas") || output.contains("sans"))
                    Language.AR->arabicNegatedFever(output)
                    else->error("This preserved quality corpus covers the original four targets only")
                }
                rows+=mapOf("source" to source,"target" to target.tag,"original" to original,"translation" to output,
                    "ms" to SystemClock.elapsedRealtime()-start,"meaningSentinel" to meaning,"completed" to !state.translationBusy,
                    "failed" to state.translationFailed)
                assertEquals(original,state.symptoms);assertFalse(state.confirmed)
                assertEquals(0,calls.get())
                instrumentation.runOnMainSync { vm.analyze() }
                assertEquals(0,calls.get())
            }
            for(row in rows) {
                assertEquals("Translation completion "+row["target"],true,row["completed"])
                assertEquals("Synthetic fever/negation "+row["target"],true,row["meaningSentinel"])
            }
            val original=vm.state.value.symptoms
            instrumentation.runOnMainSync { vm.confirm(true);vm.analyze() }
            val deadline=SystemClock.elapsedRealtime()+5000
            while(vm.state.value.screen==Screen.ANALYZING && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(20)
            assertEquals(1,calls.get());assertEquals(original,received)
        } finally {
            instrumentation.runOnMainSync { controller?.destroy() }
            activity.close()
            prefs.translationTarget=oldTarget;prefs.translationEngine=oldEngine;prefs.englishVoiceTranslation=oldEnabled
            prefs.setupComplete=oldSetup;prefs.nickname=oldName
            File(context.getExternalFilesDir(null),"nllb-review-flow.json").writeText(Contract.gson.toJson(
                mapOf("paidCalls" to 0,"assessment" to "mock","input" to "fixed synthetic written text","results" to rows)))
        }
    }
}
