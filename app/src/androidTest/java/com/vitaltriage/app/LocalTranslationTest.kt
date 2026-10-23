package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual on-device translator. Model download requires the explicit instrumentation argument. */
class LocalTranslationTest {
    @Test fun severityDurationAndNegationRemainAttachedToCorrectSymptom() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val examples=linkedMapOf(
            "tr" to "Göğüs ağrısı yok. İki gündür hafif boğaz ağrısı var. Şiddetli nefes darlığı yirmi dakika önce başladı.",
            "fr" to "Il n'y a pas de douleur thoracique. Le mal de gorge est léger depuis deux jours. Une forte difficulté à respirer a commencé il y a vingt minutes.",
            "ar" to "لا يوجد ألم في الصدر. يوجد ألم خفيف في الحلق منذ يومين. بدأت صعوبة شديدة في التنفس قبل عشرين دقيقة."
        )
        val rows=mutableListOf<Map<String,Any>>()
        try {
            for((language,text) in examples) {
                assertTrue("Installed model required; no implicit download",LocalTextTranslation.installed(language))
                val output=LocalTextTranslation.toEnglish(text,language).lowercase()
                rows+=mapOf("language" to language,
                    "noChestPain" to Regex("(no|not|without).{0,30}chest pain").containsMatchIn(output),
                    "mildThroat" to Regex("(mild|slight|light).{0,30}(throat|sore)|(throat|sore).{0,30}(mild|slight|light)").containsMatchIn(output),
                    "twoDays" to Regex("(two|2) days").containsMatchIn(output),
                    "severeBreathing" to Regex("(severe|strong|serious).{0,40}(breath|respir)|(breath|respir).{0,40}(severe|strong|serious)").containsMatchIn(output),
                    "twentyMinutes" to Regex("(twenty|20) minutes").containsMatchIn(output))
            }
            for(row in rows)for((key,value) in row)if(key!="language")
                assertEquals("Meaning sentinel failed: "+row["language"]+"/"+key,true,value)
        } finally {
            File(context.getExternalFilesDir(null),"translation-severity.json").writeText(Contract.gson.toJson(mapOf(
                "paidCalls" to 0,"limitedKeywordSentinelsNotClinicalValidation" to true,"results" to rows)))
        }
    }
    @Test fun installedModelsTranslateThreeSyntheticLanguagesAndPreserveNegation() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val allowDownload=InstrumentationRegistry.getArguments().getString("allowTranslationDownload")=="true"
        val examples=linkedMapOf(
            "fr" to "Exemple synthétique : une personne adulte a mal à la gorge depuis deux jours, sans fièvre.",
            "tr" to "Sentetik örnek: Yetişkin bir kişinin iki gündür boğazı ağrıyor, ateşi yok.",
            "ar" to "مثال اصطناعي: شخص بالغ يعاني من ألم في الحلق منذ يومين دون حمى."
        )
        val report=linkedMapOf<String,Any>("engine" to "ML Kit Translate17.0.3","paidCalls" to 0,"audioTest" to false)
        try {
            for((language,original) in examples) {
                if(allowDownload)LocalTextTranslation.prepare(context,language)
                assertTrue("Translation model not prepared: "+language,LocalTextTranslation.installed(language))
                val start=SystemClock.elapsedRealtime()
                val translated=LocalTextTranslation.toEnglish(original,language).lowercase()
                report[language+"Ms"]=SystemClock.elapsedRealtime()-start
                report[language+"Throat"]="throat" in translated
                report[language+"Fever"]="fever" in translated
                report[language+"Negative"]=Regex("\\b(no|not|without)\\b|n't").containsMatchIn(translated)
                report[language+"Duration"]="two" in translated || "2" in translated
                // Record every language before asserting; one accuracy failure must not hide the others.
                report[language+"NeckInsteadOfThroat"]="neck" in translated
                report[language+"StraitInsteadOfThroat"]="strait" in translated
            }
            val publicFrench="il n'avait pas non plus le pouvoir de déroger aux lois fiscales ni à celles sur les droits de douane entre les états"
            val english=LocalTextTranslation.toEnglish(publicFrench,"fr").lowercase()
            report["publicLegalReference"]=publicFrench
            report["publicLegalTranslation"]=english
            report["publicLegalDoubleNegation"]=Regex("nor.{0,30}\\bnot\\b").containsMatchIn(english)
            assertFalse("Public legal negative clause became double negation",report["publicLegalDoubleNegation"]==true)
            for(language in examples.keys) {
                assertTrue("Throat meaning missing: "+language,report[language+"Throat"]==true)
                assertTrue("Fever meaning missing: "+language,report[language+"Fever"]==true)
                assertTrue("Negation lost: "+language,report[language+"Negative"]==true)
                assertTrue("Duration lost: "+language,report[language+"Duration"]==true)
            }
            assertTrue(english.contains("tax") || english.contains("fiscal"))
            assertTrue(english.contains("custom") || english.contains("duties"))
            assertTrue("Second negative clause lost",Regex("\\b(nor|or)\\b").containsMatchIn(english))
            assertTrue("Negation lost",Regex("\\b(no|not)\\b|n't").containsMatchIn(english))
        } finally {
            // No synthetic patient text/audio or credentials are written; only public legal fixture text.
            File(context.getExternalFilesDir(null),"local-translation.json").writeText(Contract.gson.toJson(report))
        }
    }
}
