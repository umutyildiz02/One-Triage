package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

/** Fixed fictional translation fixtures only. No arbitrary user text is written to reports. */
class NllbSafetyTest {
    private fun directory()=InstrumentationRegistry.getInstrumentation().targetContext.let {
        check(it.packageName.endsWith(".uitest"));it.getExternalFilesDir("nllb")!!
    }
    @Test fun negationDirectionSeverityAndDurationRemainVisible() {
        data class Case(val id:String,val source:String,val target:String,val input:String,val checks:List<String>)
        val cases=listOf(
            Case("tr_absent","tr","en","Göğsümde ağrı yok.",listOf("chest","pain","\\b(no|not|without)\\b|n't")),
            Case("tr_present","tr","en","Göğsümde ağrı var.",listOf("chest","pain")),
            Case("tr_left_duration","tr","en","Sol kolum iki saattir çok ağrıyor.",listOf("left","arm","(two|2) hours","(very|lot|bad|sever|much)")),
            Case("fr_absent","fr","en","Je n'ai pas de fièvre et je n'ai pas de douleur thoracique.",listOf("fever","(chest|thoracic)","pain","\\b(no|not|without)\\b|n't")),
            Case("fr_right_duration","fr","en","J'ai une forte douleur au bras droit depuis deux heures.",listOf("right","arm","(two|2) hours","(sever|strong|bad|intense)")),
            Case("ar_absent","ar","en","لا توجد حمى ولا ألم في الصدر.",listOf("fever","chest","pain","\\b(no|not|without)\\b|n't")),
            Case("ar_left_duration","ar","en","لدي ألم شديد في ذراعي اليسرى منذ ساعتين.",listOf("left","arm","(two|2) hours","(sever|strong|bad|intense)")),
            Case("en_to_tr","en","tr","I have a sore throat for two days, but no fever.",listOf("boğaz","iki","ateş","yok|değil|olmadan"))
        )
        val rows=mutableListOf<Map<String,Any>>()
        try {
            NllbEngine(directory()).use { engine ->
                for(case in cases) {
                    val started=SystemClock.elapsedRealtime()
                    val output=engine.translate(case.input,case.source,case.target)
                    val normalized=output.lowercase(java.util.Locale.forLanguageTag(case.target))
                    val missing=case.checks.filterNot { Regex(it,RegexOption.IGNORE_CASE).containsMatchIn(normalized) }
                    val unwantedNegation=case.id=="tr_present" && Regex("\\b(no|not|without)\\b|n't",RegexOption.IGNORE_CASE).containsMatchIn(output)
                    rows+=mapOf("id" to case.id,"source" to case.source,"target" to case.target,
                        "syntheticInput" to case.input,"syntheticOutput" to output,"ms" to SystemClock.elapsedRealtime()-started,
                        "missingSentinels" to missing,"unwantedNegation" to unwantedNegation,
                        "passed" to (missing.isEmpty() && !unwantedNegation))
                }
            }
        } finally {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null),"nllb-synthetic-safety.json").writeText(Contract.gson.toJson(
                mapOf("paidCalls" to 0,"fixedSyntheticFixturesOnly" to true,"clinicalValidation" to false,"cases" to rows)))
        }
        assertEquals(cases.size,rows.size)
        assertTrue("Translation semantics require review: "+rows.filter { it["passed"]!=true }.map { it["id"] },
            rows.all { it["passed"]==true })
    }
    @Test fun invalidInputsAndCancellationNeverProduceFallbackText() {
        NllbEngine(directory()).use { engine ->
            assertThrows(IllegalArgumentException::class.java) { engine.translate("","tr","en") }
            assertThrows(IllegalArgumentException::class.java) { engine.translate("test","xx","en") }
            assertThrows(IllegalArgumentException::class.java) { engine.translate("x".repeat(4001),"tr","en") }
            // The original curly-apostrophe case is now supported by the narrow input adapter.
            // Keep its meaning assertion, and independently retain unknown-character rejection.
            val adapted=engine.translate("Je n’ai pas de fièvre.","fr","en").lowercase()
            assertTrue(adapted.contains("fever") && Regex("\\b(no|not|without)\\b|n't").containsMatchIn(adapted))
            assertThrows(IllegalArgumentException::class.java) { engine.translate("Je n’ai pas de fièvre. \u0378","fr","en") }
            engine.cancel()
            assertThrows(CancellationException::class.java) { engine.translate("Göğüs ağrım yok.","tr","en") }
        }
    }
    @Test fun memoryPressureRejectsLoadingAndInferenceWithoutFallback() {
        assertThrows(SpeechMemoryException::class.java) {
            NllbEngine(File(directory(),"absent"),memoryPressure={true})
        }
        var pressure=false
        NllbEngine(directory(),memoryPressure={pressure}).use { engine ->
            pressure=true
            assertThrows(SpeechMemoryException::class.java) {
                engine.translate("Göğüs ağrım yok.","tr","en")
            }
        }
    }
    @Test fun missingModelFailsBeforeAnyTranslation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val directory=File(context.cacheDir,"nllb-missing-fixture").apply { mkdirs() }
        assertFalse(NllbModels.verified(directory))
        assertThrows(IllegalStateException::class.java) { NllbEngine(directory) }
    }
}
