package com.vitaltriage.backend

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import com.vitaltriage.core.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class LiveGatewayTest {
    private val request = TriageRequest("Synthetic stable irritation for one day", "tr", listOf("en"), true)
    private fun ledger() = Files.createTempDirectory("vt-budget-test").resolve("ledger.json")
    private fun response(assessment: Assessment): String = Contract.gson.toJson(mapOf(
        "id" to "resp_test", "model" to LiveBudget.MODEL, "status" to "completed",
        "usage" to mapOf("input_tokens" to 100, "output_tokens" to 200),
        "output" to listOf(mapOf("type" to "message", "content" to listOf(mapOf("type" to "output_text", "text" to Contract.encode(assessment)))))
    ))
    @Test fun reservationsSurviveRestartAndFailure() {
        val path=ledger()
        repeat(LiveBudget.MAX_CALLS) { LiveBudget(path).reserve() }
        assertEquals("TEST_BUDGET_LIMIT", (runCatching { LiveBudget(path).reserve() }.exceptionOrNull() as LiveFailure).code)
        val state=JsonParser.parseString(Files.readString(path)).asJsonObject
        assertEquals(100,state["attempts"].asJsonArray.size())
        assertEquals(LiveBudget.RESERVATION,state["attempts"].asJsonArray[0].asJsonObject["chargedUpperUsd"].asDouble,0.00000001)
    }
    @Test fun additionalAuthorizationPreservesHistoricalAttempts() {
        val path=ledger()
        val budget=LiveBudget(path)
        repeat(21) { budget.record(budget.reserve(),"HISTORICAL_FAILURE") }
        val historical=JsonParser.parseString(Files.readString(path)).asJsonObject["attempts"].asJsonArray.deepCopy()
        repeat(79) { LiveBudget(path).reserve() }
        assertEquals("TEST_BUDGET_LIMIT",(runCatching { LiveBudget(path).reserve() }.exceptionOrNull() as LiveFailure).code)
        val attempts=JsonParser.parseString(Files.readString(path)).asJsonObject["attempts"].asJsonArray
        assertEquals(100, attempts.size())
        repeat(21) { assertEquals(historical[it],attempts[it]) }
        assertTrue(LiveBudget.USD_LIMIT < LiveBudget.AUTHORIZED_USD)
    }
    @Test fun nextReservationMustRemainBelowCostThreshold() {
        val path=ledger()
        LiveBudget(path).reserve()
        val state=JsonParser.parseString(Files.readString(path)).asJsonObject
        state["attempts"].asJsonArray[0].asJsonObject.addProperty("chargedUpperUsd",LiveBudget.USD_LIMIT-LiveBudget.RESERVATION)
        val existing=state.toString()
        Files.writeString(path,existing)
        assertEquals("TEST_BUDGET_LIMIT",(runCatching { LiveBudget(path).reserve() }.exceptionOrNull() as LiveFailure).code)
        assertEquals(existing,Files.readString(path))
    }
    @Test fun corruptedLedgerFailsClosed() {
        val path=ledger(); Files.writeString(path,"not json")
        assertTrue(runCatching { LiveBudget(path).reserve() }.isFailure)
        assertEquals("not json",Files.readString(path))
    }
    @Test fun budgetNeverStoresContentOrCredentials() {
        val path=ledger(); val budget=LiveBudget(path);val id=budget.reserve()
        val root=JsonParser.parseString(response(Safety.unknown("tr"))).asJsonObject
        root.addProperty("private","never persist"); budget.record(id,"VALIDATED","req_test",root)
        val raw=Files.readString(path)
        assertFalse(raw.contains("never persist")); assertFalse(raw.contains("assessment"))
        assertEquals(0.00036,JsonParser.parseString(raw).asJsonObject["attempts"].asJsonArray[0].asJsonObject["chargedUpperUsd"].asDouble,0.00000001)
    }
    @Test fun realHttpFormatAndStrictStructuredResponse() {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        var received:JsonObject?=null; var auth:String?=null
        server.createContext("/responses") { e ->
            auth=e.requestHeaders.getFirst("Authorization")
            received=JsonParser.parseString(e.requestBody.bufferedReader().readText()).asJsonObject
            val bytes=response(Safety.unknown("tr")).toByteArray()
            e.responseHeaders.add("x-request-id","req_test");e.sendResponseHeaders(200,bytes.size.toLong())
            e.responseBody.use { it.write(bytes) };e.close()
        }
        server.start()
        try {
            val result=OpenAiGateway("synthetic-test-token",LiveBudget.MODEL,"synthetic system prompt",LiveBudget(ledger()),
                URI("http://127.0.0.1:${server.address.port}/responses")).assess(request)
            assertEquals(Urgency.UNDETERMINED,result.urgency)
            assertEquals("Bearer synthetic-test-token",auth)
            val body=received!!
            assertEquals(LiveBudget.MODEL,body["model"].asString);assertFalse(body["store"].asBoolean)
            assertEquals(1800,body["max_output_tokens"].asInt)
            val format=body["text"].asJsonObject["format"].asJsonObject
            assertEquals("json_schema",format["type"].asString);assertTrue(format["strict"].asBoolean)
            assertFalse(format["schema"].asJsonObject["additionalProperties"].asBoolean)
            assertEquals("[\"tr\"]",format["schema"].asJsonObject["properties"].asJsonObject["language"].asJsonObject["enum"].toString())
            assertTrue(body["input"].asString.contains("response_language"))
        } finally { server.stop(0) }
    }
    @Test fun successiveRequestsReconnectAfterServerClosesConnection() {
        val count = AtomicInteger()
        val protocols = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            count.incrementAndGet(); protocols.add(e.protocol)
            e.requestBody.use { it.readBytes() }
            val bytes = response(Safety.unknown("tr")).toByteArray()
            e.responseHeaders.add("Connection", "close")
            e.sendResponseHeaders(200, bytes.size.toLong())
            e.responseBody.use { it.write(bytes) }; e.close()
        }
        server.start()
        try {
            val path = ledger()
            val gateway = OpenAiGateway("synthetic-test-token", LiveBudget.MODEL, "synthetic", LiveBudget(path),
                URI("http://127.0.0.1:${server.address.port}/"))
            repeat(2) { assertEquals(Urgency.UNDETERMINED, gateway.assess(request).urgency) }
            assertEquals(2, count.get())
            assertEquals(listOf("HTTP/1.1", "HTTP/1.1"), protocols)
            val entries = JsonParser.parseString(Files.readString(path)).asJsonObject["attempts"].asJsonArray
            assertEquals(2, entries.size())
            assertTrue(entries.all { it.asJsonObject["status"].asString == "VALIDATED" })
        } finally { server.stop(0) }
    }
    @Test fun authCreditModelErrorsAreSafeAndNotRetried() {
        listOf(401 to "OPENAI_AUTH",429 to "OPENAI_CREDIT",404 to "OPENAI_MODEL").forEach { (status,expected) ->
            val count=AtomicInteger()
            val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
            server.createContext("/") { e ->
                count.incrementAndGet()
                val raw="""{"error":{"code":"${if(status==429)"insufficient_quota" else "other"}","message":"private provider details"}}""".toByteArray()
                e.sendResponseHeaders(status,raw.size.toLong());e.responseBody.use { it.write(raw) };e.close()
            };server.start()
            try {
                val failure=runCatching { OpenAiGateway("fake",LiveBudget.MODEL,"prompt",LiveBudget(ledger()),URI("http://127.0.0.1:${server.address.port}/")).assess(request) }.exceptionOrNull()
                assertEquals(expected,(failure as LiveFailure).code);assertEquals(1,count.get())
                assertFalse(failure.message!!.contains("private"))
            } finally { server.stop(0) }
        }
    }
    @Test fun inconsistentColorsLanguageScoresAndFlagsRejected() {
        val base=Safety.unknown("tr")
        listOf(base.copy(riskColor="#000000"),base.copy(language="en"),base.copy(criticality=40),
            base.copy(urgency=Urgency.LOW,riskColor=Urgency.LOW.color,redFlags=listOf("reported warning"))).forEach {
            assertTrue(runCatching { OpenAiGateway.decodeResponse(response(it),"tr") }.isFailure)
        }
    }
    @Test fun emptyAndIncompleteResponseRejected() {
        listOf("""{"status":"completed","output":[]}""","""{"status":"incomplete","output":[]}""","{}").forEach {
            assertTrue(runCatching { OpenAiGateway.decodeResponse(it,"tr") }.isFailure)
        }
    }
}
