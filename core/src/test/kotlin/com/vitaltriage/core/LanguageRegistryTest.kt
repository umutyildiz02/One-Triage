package com.vitaltriage.core

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class LanguageRegistryTest {
    @Test fun legacyTokenizerPositionsAndCapabilitiesAreExplicit() {
        val ids=mapOf("de" to 256042L,"bg" to 256033L,"da" to 256041L,"nl" to 256127L,
            "fi" to 256055L,"gl" to 256062L,"hr" to 256070L,"ja" to 256079L,"ca" to 256034L,"ko" to 256098L)
        ids.forEach { (tag,id)-> val language=Language.from(tag);assertEquals(id,language.nllbId);assertEquals(tag,language.whisperTag) }
        assertEquals(71,Language.entries.size)
        assertEquals(71,Language.entries.map { it.nllbId }.distinct().size)
        assertNull(Language.AK.whisperTag)
        assertEquals("jw",Language.JV.whisperTag)
    }
    @Test fun unknownRegionsAndScriptsNeverFallBackToEnglish() {
        assertNull(Language.find("xx"));assertNull(Language.fromSpeechTag("de-XX"))
        assertNull(Language.fromSpeechTag("zh-Hant"));assertNull(Language.fromSpeechTag("zh-TW"))
        assertEquals(Language.DE,Language.fromSpeechTag("de-DE"))
        assertEquals(Language.TR,Language.fromSpeechTag("tr-TR"))
        assertEquals(Language.JV,Language.fromWhisper("jw"))
        assertFalse(Language.matchesVoice(Language.ZH,java.util.Locale.forLanguageTag("zh-Hant-TW")))
        assertEquals(listOf(Language.EN,Language.TR,Language.FR,Language.AR),Language.uiLanguages)
    }
    @Test fun everyRegisteredResultCodeUsesStrictRequestedSchemaAndOriginalInput() {
        Language.entries.forEach { language ->
            val request=TriageRequest("Synthetischer Test: Ich habe seit zwei Tagen Halsschmerzen, aber kein Fieber.",language.tag,listOf("de"),true)
            assertTrue(Safety.valid(request))
            val encoded=OpenAiProtocol.request(request,LiveBudget.MODEL,"Synthetic test instructions")
            val input=JsonParser.parseString(encoded["input"].asString).asJsonObject
            assertEquals(request.symptoms,input["symptoms"].asString)
            assertEquals(language.tag,input["response_language"].asString)
            assertEquals("LANGUAGE_COVERAGE_UNVERIFIED",input["local_screening"].asString)
            val schema=encoded.getAsJsonObject("text").getAsJsonObject("format").getAsJsonObject("schema")
            assertFalse(schema["additionalProperties"].asBoolean)
            assertEquals(listOf(language.tag),schema.getAsJsonObject("properties").getAsJsonObject("language").getAsJsonArray("enum").map { it.asString })
            val output=Safety.unknown(language.tag)
            assertEquals(language.tag,Contract.parse(Contract.encode(output),language.tag).language)
        }
    }
    @Test fun coverageAndNegationAreNotAnAllClearOrABypass() {
        val request=TriageRequest("Ich habe keine Schmerzen, aber die Angaben sind unvollständig.","de",listOf("de"),true)
        assertEquals("LANGUAGE_COVERAGE_UNVERIFIED",Safety.localScreening(request))
        assertEquals(Urgency.UNDETERMINED,Safety.enforce(Safety.unknown("de").copy(urgency=Urgency.LOW),request).urgency)
        assertFalse(Safety.valid(request.copy(patientLanguages=listOf("xx"))))
        assertFalse(Safety.valid(request.copy(language="de-XX")))
        assertFalse(Safety.valid(request.copy(confirmed=false)))
        assertTrue(Safety.emergency(request.copy(immediateDanger=true)))
        assertTrue(runCatching { Contract.parse(Contract.encode(Safety.unknown("de")),"ja") }.isFailure)
        assertTrue(runCatching { Contract.parse(Contract.encode(Safety.unknown("xx")),"xx") }.isFailure)
    }
}
