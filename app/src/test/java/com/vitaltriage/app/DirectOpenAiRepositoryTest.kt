package com.vitaltriage.app

import com.google.gson.JsonParser
import com.vitaltriage.core.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class DirectOpenAiRepositoryTest {
    private val syntheticKey = "sk-synthetic_test_key_not_a_real_credential"
    private fun request(lang:String="en")=TriageRequest("Synthetic mild irritation for two days",lang,listOf(lang),true)
    private fun response(r:TriageRequest)=Contract.gson.toJson(mapOf(
        "id" to "resp_synthetic", "model" to LiveBudget.MODEL, "status" to "completed",
        "usage" to mapOf("input_tokens" to 100,"output_tokens" to 200),
        "output" to listOf(mapOf("type" to "message","content" to listOf(mapOf(
            "type" to "output_text","text" to Contract.encode(Demo.evaluate(r.copy(scenario="LOW")).result)))))))
    private fun repository(server:MockWebServer,path:Path,online:Boolean=true,timeout:Long=2000)=DirectOpenAiRepository(
        {syntheticKey},{"Synthetic prompt"},{LiveBudget(path)},server.url("/v1/responses").toString(),
        {online},OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .callTimeout(timeout,TimeUnit.MILLISECONDS).build())
    private fun entries(path:Path)=JsonParser.parseString(String(Files.readAllBytes(path))).asJsonObject["attempts"].asJsonArray
    private fun fixture(block:(MockWebServer,Path)->Unit) {
        val server=MockWebServer();server.start()
        try { block(server,Files.createTempDirectory("direct-mock").resolve("ledger.json")) } finally {server.shutdown()}
    }
    @Test fun fourLanguagesPreserveOriginalStrictSchemaAndDirectProvenance()=fixture { server,path ->
        Language.uiLanguages.forEach { language ->
            val r=request(language.tag)
            server.enqueue(MockResponse().setBody(response(r)).setHeader("x-request-id","req_synthetic"))
            val result=runBlocking { repository(server,path).assess(r,"https://wrong.invalid","not-the-key") }
            assertEquals("DIRECT_OPENAI",result.mode);assertFalse(result.failed);assertEquals(language.tag,result.result.language)
            val recorded=server.takeRequest(2,TimeUnit.SECONDS)!!
            assertEquals("Bearer $syntheticKey",recorded.getHeader("Authorization"))
            val body=JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals(LiveBudget.MODEL,body["model"].asString);assertFalse(body["store"].asBoolean)
            assertEquals(1800,body["max_output_tokens"].asInt)
            assertEquals(r.symptoms,JsonParser.parseString(body["input"].asString).asJsonObject["symptoms"].asString)
            val format=body["text"].asJsonObject["format"].asJsonObject
            assertTrue(format["strict"].asBoolean)
            assertFalse(format["schema"].asJsonObject["additionalProperties"].asBoolean)
        }
        assertEquals(4,entries(path).size())
        val persisted=String(Files.readAllBytes(path))
        assertFalse(persisted.contains(syntheticKey));assertFalse(persisted.contains("Synthetic mild"))
        assertEquals(100,entries(path)[0].asJsonObject["inputTokens"].asInt)
    }
    @Test fun authenticationCreditModelAndRateErrorsNeverBecomeDemo()=fixture { server,path ->
        listOf(401 to "OPENAI_AUTH",403 to "OPENAI_PERMISSION",404 to "OPENAI_MODEL",429 to "OPENAI_RATE_LIMIT",500 to "OPENAI_REQUEST").forEach { (status,code) ->
            server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
            val failure=runCatching { runBlocking { repository(server,path).assess(request(),"","") } }.exceptionOrNull()
            assertEquals(code,(failure as BackendFailure).code)
        }
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"code":"insufficient_quota"}}"""))
        val failure=runCatching { runBlocking { repository(server,path).assess(request(),"","") } }.exceptionOrNull()
        assertEquals("OPENAI_CREDIT",(failure as BackendFailure).code)
        assertEquals(6,server.requestCount)
    }
    @Test fun refusalIncompleteWrongColorAndLanguageAreRejected()=fixture { server,path ->
        val valid=response(request())
        listOf(
            """{"status":"incomplete","output":[]}""",
            """{"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"no"}]}]}""",
            valid.replace("#176B47","#ffffff"),
            response(request("fr"))
        ).forEach { raw ->
            server.enqueue(MockResponse().setBody(raw))
            assertTrue(runCatching { runBlocking { repository(server,path).assess(request(),"","") } }.exceptionOrNull() is BackendFailure)
        }
        assertTrue(entries(path).all { it.asJsonObject["status"].asString!="VALIDATED" })
    }
    @Test fun clinicalSafetyConflictIsRejectedRatherThanLow()=fixture { server,path ->
        server.enqueue(MockResponse().setBody(response(request())))
        val failure=runCatching { runBlocking { repository(server,path).assess(request().copy(symptoms="Synthetic chest pain for two days"),"","") } }.exceptionOrNull()
        assertEquals("OPENAI_SAFETY_CONFLICT",(failure as BackendFailure).code)
    }
    @Test fun noConsentOrKnownOfflineCannotReserveOrSend()=fixture { server,path ->
        assertTrue(runCatching { runBlocking { repository(server,path).assess(request().copy(confirmed=false),"","") } }.isFailure)
        val failure=runCatching { runBlocking { repository(server,path,false).assess(request(),"","") } }.exceptionOrNull()
        assertEquals("OPENAI_NETWORK",(failure as BackendFailure).code)
        assertEquals(0,server.requestCount);assertFalse(Files.exists(path))
    }
    @Test fun timeoutCountsOnceWithFullReservationAndNoRetry()=fixture { server,path ->
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val failure=runCatching { runBlocking { repository(server,path,timeout=200).assess(request(),"","") } }.exceptionOrNull()
        assertEquals("OPENAI_NETWORK",(failure as BackendFailure).code)
        assertEquals(1,server.requestCount);assertEquals(1,entries(path).size())
        assertEquals(LiveBudget.RESERVATION,entries(path)[0].asJsonObject["chargedUpperUsd"].asDouble,1e-9)
    }
    @Test fun cancellationStopsPendingCallWithoutAnotherAttempt()=fixture { server,path ->
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        runBlocking {
            val job=launch(Dispatchers.Default) { repository(server,path).assess(request(),"","") }
            assertNotNull(server.takeRequest(2,TimeUnit.SECONDS))
            withTimeout(1500) { job.cancelAndJoin() }
        }
        assertEquals(1,server.requestCount);assertEquals(1,entries(path).size())
    }
    @Test fun redirectsNeverForwardCredentials()=fixture { server,path ->
        MockWebServer().use { other ->
            other.start();server.enqueue(MockResponse().setResponseCode(307).setHeader("Location",other.url("/")))
            val failure=runCatching { runBlocking { repository(server,path).assess(request(),"","") } }.exceptionOrNull()
            assertEquals("OPENAI_REQUEST",(failure as BackendFailure).code)
            assertEquals(0,other.requestCount)
        }
    }
    @Test fun oversizedBodyIsBoundedAndRejected()=fixture { server,path ->
        server.enqueue(MockResponse().setBody("x".repeat(128001)))
        val failure=runCatching { runBlocking { repository(server,path).assess(request(),"","") } }.exceptionOrNull()
        assertEquals("OPENAI_INVALID_RESPONSE",(failure as BackendFailure).code)
    }
    @Test fun inheritedTwentyOneAttemptsSurviveRestartAndUpdateAndStopAtOneHundred() {
        val old=Files.createTempDirectory("budget-history").resolve("old.json")
        repeat(21) { LiveBudget(old).reserve() }
        val seed=String(Files.readAllBytes(old))
        val phone=old.resolveSibling("phone.json")
        repeat(79) { LiveBudget(phone,seed).reserve() }
        // A later APK with an older seed must never overwrite the saved 100 attempts.
        assertEquals(100, entries(phone).size())
        val failure=runCatching { LiveBudget(phone,seed).reserve() }.exceptionOrNull()
        assertEquals("TEST_BUDGET_LIMIT",(failure as LiveFailure).code)
        assertEquals(100,entries(phone).size())
        repeat(21) { assertEquals(entries(old)[it],entries(phone)[it]) }
    }
    @Test fun corruptInheritedBudgetDoesNotCreateFreshAllowance() {
        val path=Files.createTempDirectory("budget-corrupt").resolve("phone.json")
        assertTrue(runCatching { LiveBudget(path,"broken").reserve() }.isFailure)
        assertFalse(Files.exists(path))
    }
}
