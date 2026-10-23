package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real ONNX inference; no downloads, cloud calls or transcript/translation injection. */
class NllbTranslationTest {
    @Test fun originalModelsProduceTranslationsAndExposeSemanticRegressions() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val directory=context.getExternalFilesDir("nllb")!! // isolated diagnostic fixtures, never normal app data
        assertTrue("Explicitly prepared original model set required",NllbModels.verified(directory))
        val rows=mutableListOf<Map<String,Any>>()
        val report=linkedMapOf<String,Any>("engine" to "RTranslator NLLB_CACHE / ORT1.23.0","paidCalls" to 0,"keywordSentinelsNotClinicalValidation" to true)
        val start=SystemClock.elapsedRealtime()
        try {
            NllbEngine(directory).use { engine ->
                report["loadMs"]=SystemClock.elapsedRealtime()-start
                report["loadPssKb"]=android.os.Debug.getPss()
                report["memoryMetric"]="Process PSS sampled after load/each translation; not a true peak measurement"
                val examples=linkedMapOf(
                    "tr" to "Sentetik örnek: Yetişkin bir kişinin iki gündür boğazı ağrıyor, ateşi yok.",
                    "fr" to "Exemple synthétique : une personne adulte a mal à la gorge depuis deux jours, sans fièvre.",
                    "ar" to "مثال اصطناعي: شخص بالغ يعاني من ألم في الحلق منذ يومين دون حمى."
                )
                for((language,input) in examples) {
                    val t=SystemClock.elapsedRealtime()
                    val result=engine.translate(input,language,"en").lowercase()
                    rows+=mapOf("language" to language,"ms" to SystemClock.elapsedRealtime()-t,
                        "pssKb" to android.os.Debug.getPss(),
                        "throat" to result.contains("throat"),"fever" to result.contains("fever"),
                        "negative" to Regex("\\b(no|not|without)\\b|n't").containsMatchIn(result),
                        "twoDays" to Regex("(two|2) days").containsMatchIn(result),
                        "distinct" to (result!=input.lowercase()))
                }
                val legal="il n'avait pas non plus le pouvoir de déroger aux lois fiscales ni à celles sur les droits de douane entre les états"
                val output=engine.translate(legal,"fr","en").lowercase()
                report["publicLegalReference"]=legal
                report["publicLegalTranslation"]=output
                report["publicLegalDoubleNegation"]=Regex("nor.{0,30}\\bnot\\b").containsMatchIn(output)
                report["results"]=rows
                for(row in rows)for(key in listOf("throat","fever","negative","twoDays","distinct"))
                    assertEquals("NLLB semantic sentinel: "+row["language"]+"/"+key,true,row[key])
                assertFalse("Public legal double negative regression",report["publicLegalDoubleNegation"]==true)
            }
        } finally {
            report["results"]=rows
            File(context.getExternalFilesDir(null),"nllb-quality.json").writeText(Contract.gson.toJson(report))
        }
    }
    @Test fun typographicApostropheUsesEquivalentTokensAndRealNegatedTranslation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val directory=context.getExternalFilesDir("nllb")!!
        val original="Je n’ai pas de fièvre."
        val straight="Je n'ai pas de fièvre."
        NllbTokenizer(File(directory,"sentencepiece_bpe.model")).use { tokenizer ->
            assertArrayEquals(tokenizer.encode(straight,"fr"),
                tokenizer.encode(NllbInput.forTokenizer(original),"fr"))
        }
        NllbEngine(directory).use { engine ->
            val started=SystemClock.elapsedRealtime()
            val output=engine.translate(original,"fr","en").lowercase()
            assertTrue("Actual fever translation absent",output.contains("fever"))
            assertTrue("Negation must survive punctuation adaptation",
                Regex("\\b(no|not|without)\\b|n't").containsMatchIn(output))
            assertThrows(IllegalArgumentException::class.java) {
                engine.translate("Je n'ai pas de fièvre. \u0378","fr","en")
            }
            println("VT_NLLB_TYPOGRAPHY elapsedMs="+(SystemClock.elapsedRealtime()-started)+" paidCalls=0")
        }
    }
    // Raw vocabulary limitation remains a separate diagnostic; do not weaken its exact round-trip assertion.
    @Test fun tokenizerUsesOriginalFourLanguageIdsAndRoundTripsUnicode() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        NllbTokenizer(File(context.getExternalFilesDir("nllb"),"sentencepiece_bpe.model")).use { tokenizer ->
            val examples=mapOf("en" to "No chest pain.","tr" to "Göğüs ağrım yok.","fr" to "Je n’ai pas de fièvre.","ar" to "لا يوجد ألم في الصدر.")
            val expected=mapOf("en" to 256047L,"tr" to 256184L,"fr" to 256057L,"ar" to 256011L)
            for((lang,text) in examples) {
                val ids=tokenizer.encode(text,lang)
                assertEquals(expected[lang],ids.first())
                assertEquals(2L,ids.last())
                assertEquals(text,tokenizer.decode(ids.toList()))
            }
        }
    }
}
