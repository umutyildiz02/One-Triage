package com.vitaltriage.core
import org.junit.Assert.*
import org.junit.Test
class TriageTest {
    private fun request(text:String="Synthetic symptom lasting two days",scenario:String="LOW",language:String="en") =
        TriageRequest(text,language,listOf(language),true,false,scenario)
    @Test fun allLevelsAndLanguagesRoundTrip() {
        Language.entries.forEach { lang -> Urgency.entries.forEach { u ->
            val a=Demo.evaluate(request(scenario=u.name,language=lang.tag)).result
            assertEquals(u,a.urgency); assertEquals(u.color,a.riskColor)
            assertEquals(a,Contract.parse(Contract.encode(a),lang.tag)); assertTrue(a.experimental)
        } }
    }
    @Test fun emptyInputRejected(){ assertFalse(Safety.valid(request(""))) }
    @Test fun confirmationRequired(){ assertFalse(Safety.valid(request().copy(confirmed=false))) }
    @Test fun patientLanguageRequired(){ assertFalse(Safety.valid(request().copy(patientLanguages=emptyList()))) }
    @Test fun unsupportedLanguageRejected(){ assertFalse(Safety.valid(request(language="xx"))) }
    @Test fun multipleLanguagesAccepted(){ assertTrue(Safety.valid(request().copy(patientLanguages=listOf("en","tr","fr","ar")))) }
    @Test fun excessiveInputRejected(){ assertFalse(Safety.valid(request("x".repeat(4001)))) }
    @Test fun incompleteInputNeverLow(){ assertEquals(Urgency.UNDETERMINED,Demo.evaluate(request("pain")).result.urgency) }
    @Test fun emergencyWordsNeverLow() {
        listOf("I have chest pain right now","Şiddetli göğüs ağrısı başladı","douleur thoracique depuis une heure","ألم في الصدر منذ ساعة").forEach {
            assertEquals(Urgency.UNDETERMINED,Demo.evaluate(request(it)).result.urgency)
        }
    }
    @Test fun selfReportedDangerNeverLow(){ assertEquals(Urgency.UNDETERMINED,Demo.evaluate(request().copy(immediateDanger=true)).result.urgency) }
    @Test fun malformedDemoFailsClosed(){ val e=Demo.evaluate(request(scenario="MALFORMED")); assertTrue(e.failed); assertNull(e.result.criticality) }
    @Test(expected=java.io.IOException::class) fun offlineDoesNotInventResult(){ Demo.evaluate(request(scenario="OFFLINE")) }
    @Test fun malformedContractsRejected(){
        val good=Contract.encode(Demo.evaluate(request()).result)
        val invalid=listOf("not json","{}",good.replace("\"LOW\"","\"INVALID\""),good.replace("\"criticality\":15","\"criticality\":101"),
            good.replace("\"criticality\":15","\"criticality\":15.5"),good.replace("\"experimental\":true","\"experimental\":false"),
            good.replace("#176B47","#000000"),good.replace("\"language\":\"en\"","\"language\":\"tr\""),
            good.dropLast(1)+",\"extra\":1}")
        invalid.forEach { raw -> assertTrue(runCatching { Contract.parse(raw,"en") }.isFailure) }
    }
    @Test fun unknownMustHaveNullScore(){
        val raw=Contract.encode(Safety.unknown("en")).replace("\"criticality\":null","\"criticality\":0")
        assertTrue(runCatching { Contract.parse(raw,"en") }.isFailure)
    }
    @Test fun lowWithMissingInformationBecomesUnknown(){
        val r=request()
        val a=Demo.evaluate(r).result.copy(missingInformation=listOf("Duration"))
        assertEquals(Urgency.UNDETERMINED,Safety.enforce(a,r).urgency)
    }
}
