package com.vitaltriage.backend

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import com.vitaltriage.core.*
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Deterministic transport/contract comparisons, NOT tests of a model's clinical reasoning. */
class LegacyPromptParityTest {
    private val prompt=javaClass.getResource("/triage-system.txt")!!.readText()
    // Reproduces only the legacy parser, not its unsafe automatic-send behavior.
    private fun legacyUrgency(text:String):Urgency? {
        val value=text.lines().firstOrNull { it.trim().lowercase().startsWith("urgency:") }
            ?.trim()?.substring("Urgency:".length)?.lowercase() ?: return null
        return listOf(Urgency.CRITICAL,Urgency.HIGH,Urgency.MEDIUM,Urgency.LOW)
            .firstOrNull { value.contains(it.name.lowercase()) }
    }
    @Test fun originalPromptAndNecessaryAdaptationsAreExplicit() {
        val legacy=javaClass.getResource("/legacy-vitalvoice-prompt.txt")!!.readText()
        assertTrue(legacy.startsWith("You are an AI that performs a triage-style risk assessment."))
        assertTrue(prompt.startsWith(legacy.substringBefore(" Return your answer")))
        assertTrue(legacy.contains("1–3 words"))
        assertTrue(prompt.contains("1–3 words"))
        assertFalse("No legacy numerical scoring formula exists in this prompt",legacy.contains("criticality",true))
        assertTrue(prompt.contains("no numeric score formula"))
        assertTrue(prompt.contains("UNDETERMINED"))
        assertTrue(prompt.contains("Never diagnose definitively"))
        assertTrue(prompt.contains("strict JSON contract replaces the original two-line"))
    }
    @Test fun allFourLegacyCategoriesKeepTheirMeaningAndTranslatedParserBugIsNotCopied() {
        mapOf("Low Urgency" to Urgency.LOW,"Medium Urgency" to Urgency.MEDIUM,
            "High Urgency" to Urgency.HIGH,"Critical" to Urgency.CRITICAL).forEach { (label,expected) ->
            assertEquals(expected,legacyUrgency("Possible diagnosis: Synthetic\nUrgency: $label"))
            assertTrue(prompt.contains("$label -> "+expected.name))
        }
        assertNull(legacyUrgency("Olası tanı: Sentetik\nAciliyet: Yüksek"))
        assertEquals(Urgency.HIGH,Contract.parse(Contract.encode(Safety.unknown("tr").copy(
            urgency=Urgency.HIGH,riskColor=Urgency.HIGH.color,criticality=70)),"tr").urgency)
    }
    @Test fun sourceTextNegationMultipleSymptomsAndFourLanguagesReachMockResponsesUntranslated() {
        val cases=listOf(
            Triple("Synthetic adult 24, slight localized itch for one day, no fever.","en",Urgency.LOW),
            Triple("Synthetic adult with persistent discomfort for two days.","en",Urgency.MEDIUM),
            Triple("Synthetic adult with severe worsening symptoms for two hours.","en",Urgency.HIGH),
            Triple("Synthetic sudden chest pain and difficulty breathing.","en",Urgency.CRITICAL),
            Triple("Unwell","en",Urgency.UNDETERMINED),
            Triple("Synthetic unclear discomfort, timing unknown.","en",Urgency.UNDETERMINED),
            Triple("Synthetic no fever and no nausea; localized itch for one day.","en",Urgency.LOW),
            Triple("Synthetic cough and fatigue for two days.","en",Urgency.MEDIUM),
            Triple("Sentetik: Ateşim yok, iki gündür boğazım ağrıyor.","tr",Urgency.MEDIUM),
            Triple("Fictif : je n’ai pas de fièvre, la gorge me fait mal depuis deux jours.","fr",Urgency.MEDIUM),
            Triple("حالة اصطناعية: لا توجد حمى ولدي ألم في الحلق منذ يومين.","ar",Urgency.MEDIUM)
        )
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        var expected:TriageRequest?=null
        var answer:Assessment?=null
        var received:String?=null
        var receivedLanguage:String?=null
        var instructions:String?=null
        server.createContext("/responses") { exchange ->
            val body=JsonParser.parseString(exchange.requestBody.bufferedReader().readText()).asJsonObject
            val input=JsonParser.parseString(body["input"].asString).asJsonObject
            received=input["symptoms"].asString
            receivedLanguage=input["response_language"].asString
            instructions=body["instructions"].asString
            val bytes=Contract.gson.toJson(mapOf("status" to "completed",
                "usage" to mapOf("input_tokens" to 20,"output_tokens" to 30),
                "output" to listOf(mapOf("type" to "message","content" to listOf(
                    mapOf("type" to "output_text","text" to Contract.encode(answer!!))))))).toByteArray()
            exchange.sendResponseHeaders(200,bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) };exchange.close()
        }
        server.start()
        try {
            val gateway=OpenAiGateway("synthetic-only",LiveBudget.MODEL,prompt,
                LiveBudget(Files.createTempDirectory("legacy-parity-mock").resolve("ledger.json")),
                URI("http://127.0.0.1:${server.address.port}/responses"))
            for((text,language,urgency) in cases) {
                expected=TriageRequest(text,language,listOf(language),true)
                answer=if(urgency==Urgency.UNDETERMINED)Safety.unknown(language) else
                    Safety.unknown(language).copy(assessment=Words.pick(language,"Synthetic assessment","Sentetik değerlendirme","Évaluation fictive","تقييم اصطناعي"),
                        urgency=urgency,riskColor=urgency.color,criticality=50,missingInformation=emptyList())
                val actual=gateway.assess(expected!!)
                assertEquals(answer,actual)
                assertEquals(text,received)
                assertEquals(language,receivedLanguage)
                assertEquals(prompt,instructions)
                assertEquals(urgency.color,actual.riskColor)
            }
        } finally { server.stop(0) }
    }
}
