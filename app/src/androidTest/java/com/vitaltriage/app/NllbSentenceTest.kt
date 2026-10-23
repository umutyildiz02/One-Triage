package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

/** Non-destructive normal-package diagnostic. Fixed synthetic text, actual prepared NLLB,
 * no model downloads, microphone capture, preference writes or paid assessment. */
class NllbSentenceTest {
    @Test fun normalPreparedModelsKeepEverySentenceAndNegation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="com.vitaltriage.app")
        val prefs=context.getSharedPreferences(UserPreferences.FILE,android.content.Context.MODE_PRIVATE)
        val before=prefs.all.toMap()
        val directory=NllbModels.directory(context)
        assertTrue("Prepared private models required",NllbModels.verified(directory))
        data class Case(val source:String,val target:String,val text:String,val expected:List<String>)
        val cases=listOf(
            Case("en","tr","I have a mild sore throat since yesterday. No fever.",
                listOf("boğaz","hafif","dünden","ateş","yok|değil")),
            Case("tr","en","İki gündür boğazım ağrıyor. Ateşim yok.",
                listOf("throat","(two|2) days","fever","\\b(no|not|without)\\b|n't")),
            Case("fr","en","J'ai mal à la gorge depuis deux jours. Je n'ai pas de fièvre.",
                listOf("throat","(two|2) days","fever","\\b(no|not|without)\\b|n't")),
            Case("ar","en","أعاني من ألم في الحلق منذ يومين. لا توجد حمى.",
                listOf("throat","(two|2) days","fever","\\b(no|not|without)\\b|n't"))
        )
        val rows=mutableListOf<Map<String,Any>>()
        val activity=ActivityScenario.launch(MainActivity::class.java)
        try {
            NllbEngine(directory,memoryPressure={SpeechMemory.isLow(context)}).use { engine ->
                for(case in cases) {
                    val start=SystemClock.elapsedRealtime()
                    val output=engine.translate(case.text,case.source,case.target)
                    val normalized=output.lowercase(Locale.forLanguageTag(case.target))
                    val missing=case.expected.filterNot { Regex(it,RegexOption.IGNORE_CASE).containsMatchIn(normalized) }
                    rows+=mapOf("source" to case.source,"target" to case.target,"syntheticInput" to case.text,
                        "syntheticOutput" to output,"missing" to missing,"passed" to missing.isEmpty(),
                        "ms" to SystemClock.elapsedRealtime()-start)
                    println("VT_NLLB_SENTENCES source="+case.source+" passed="+missing.isEmpty()+" missingCount="+missing.size)
                }
            }
            assertEquals(cases.size,rows.size)
            assertTrue("A sentence or qualifier was omitted: "+rows.filter{it["passed"]!=true}.map{it["source"]},
                rows.all{it["passed"]==true})
            assertEquals("Normal preferences must remain intact",before,prefs.all)
        } finally {
            activity.close()
            File(context.getExternalFilesDir(null),"nllb-sentences.json").writeText(Contract.gson.toJson(
                mapOf("paidCalls" to 0,"fixedSyntheticOnly" to true,"clinicalValidation" to false,"cases" to rows)))
        }
    }
}
