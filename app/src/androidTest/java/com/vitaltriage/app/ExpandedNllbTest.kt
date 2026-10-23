package com.vitaltriage.app

import android.os.SystemClock
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real offline model execution. Synthetic sentences only; never sends OpenAI requests. */
class ExpandedNllbTest {
    /** Additional cases, never replacements for the failing original fixtures below. */
    @Test fun additionalJapaneseAndKoreanWordingUsesTheRealModel() {
        val context=IsolatedTestState.context
        check(context.packageName.endsWith(".uitest"))
        val directory=context.getExternalFilesDir("nllb")!!
        assertTrue(NllbModels.verified(directory))
        val rows=ArrayList<Map<String,Any>>()
        val failures=ArrayList<String>()
        try {
            NllbEngine(directory).use { engine ->
                mapOf("ja" to "二日前から軽い喉の痛みがあります。発熱はありません。",
                    "ko" to "목이 아파요. 열은 없어요.").forEach { (tag,original) ->
                    val translated=engine.translate(original,tag,"en")
                    val lower=translated.lowercase()
                    val passed="throat" in lower && "fever" in lower && Regex("\\b(no|not|without)\\b|n['’]t").containsMatchIn(lower)
                    rows.add(mapOf("source" to tag,"syntheticOriginal" to original,"translation" to translated,"passed" to passed))
                    if(!passed)failures.add(tag)
                }
            }
        } finally {
            File(context.getExternalFilesDir(null),"expanded-nllb-additional.json").writeText(Contract.gson.toJson(rows))
        }
        assertTrue("Additional meaning checks failed: "+failures.joinToString(),failures.isEmpty())
    }
    @Test fun tenRequestedLanguagesPreserveNegationDurationAndSymptomInRealTranslation() {
        val context=IsolatedTestState.context
        check(context.packageName.endsWith(".uitest"))
        val directory=context.getExternalFilesDir("nllb")!!
        assertTrue("Verified isolated NLLB model files are required",NllbModels.verified(directory))
        val examples=linkedMapOf(
            "de" to "Ich habe seit zwei Tagen leichte Halsschmerzen. Ich habe kein Fieber.",
            "bg" to "От два дни имам лека болка в гърлото. Нямам температура.",
            "da" to "Jeg har haft let ondt i halsen i to dage. Jeg har ikke feber.",
            "nl" to "Ik heb sinds twee dagen lichte keelpijn. Ik heb geen koorts.",
            "fi" to "Minulla on ollut lievää kurkkukipua kaksi päivää. Minulla ei ole kuumetta.",
            "gl" to "Teño unha leve dor de gorxa desde hai dous días. Non teño febre.",
            "hr" to "Imam blagu grlobolju dva dana. Nemam temperaturu.",
            "ja" to "二日前から軽い喉の痛みがあります。熱はありません。",
            "ca" to "Tinc un mal de coll lleu des de fa dos dies. No tinc febre.",
            "ko" to "이틀 전부터 목이 조금 아픕니다. 열은 없습니다."
        )
        val rows=ArrayList<Map<String,Any>>()
        val failures=ArrayList<String>()
        val start=SystemClock.elapsedRealtime()
        try {
            NllbEngine(directory).use { engine->
                val loadMs=SystemClock.elapsedRealtime()-start
                examples.forEach { (tag,original)->
                    val began=SystemClock.elapsedRealtime()
                    val translated=try { engine.translate(original,tag,"en") } catch(error:IllegalArgumentException) {
                        val unknownCodepoints=NllbTokenizer(File(directory,"sentencepiece_bpe.model")).use { tokenizer ->
                            original.codePoints().toArray().filter { codepoint ->
                                3L in tokenizer.encode(String(Character.toChars(codepoint)),tag)
                            }.map { "U+"+it.toString(16).uppercase() }
                        }
                        failures.add(tag)
                        rows.add(linkedMapOf("source" to tag,"syntheticOriginal" to original,
                            "error" to "Unrepresentable input or input limit", "unknownCodepoints" to unknownCodepoints,
                            "passed" to false,"elapsedMs" to SystemClock.elapsedRealtime()-began))
                        return@forEach
                    }
                    val lower=translated.lowercase()
                    val negation=Regex("\\b(no|not|without)\\b|n['’]t").containsMatchIn(lower)
                    val duration=Regex("\\b(two|2)\\b").containsMatchIn(lower) && "day" in lower
                    val symptom="throat" in lower && "fever" in lower
                    val mild=listOf("mild","slight","little","light").any { it in lower }
                    val reverse=engine.translate("I have no fever.","en",tag)
                    val valid=negation && duration && symptom && mild && reverse.isNotBlank() && reverse!="I have no fever."
                    if(!valid)failures.add(tag)
                    rows.add(linkedMapOf("source" to tag,"syntheticOriginal" to original,"translation" to translated,
                        "reverseSynthetic" to reverse,"negation" to negation,"duration" to duration,
                        "symptom" to symptom,"severity" to mild,"passed" to valid,
                        "elapsedMs" to SystemClock.elapsedRealtime()-began,"modelLoadMs" to loadMs))
                }
            }
        } finally {
            File(context.getExternalFilesDir(null),"expanded-nllb.json").writeText(Contract.gson.toJson(
                mapOf("paidCalls" to 0,"engine" to "NLLB","examplesPerLanguage" to 1,"results" to rows)))
        }
        assertTrue("Meaning checks failed for: "+failures.joinToString(),failures.isEmpty())
    }
}
