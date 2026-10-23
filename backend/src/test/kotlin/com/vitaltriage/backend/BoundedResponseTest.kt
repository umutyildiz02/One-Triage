package com.vitaltriage.backend

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import com.vitaltriage.core.TriageRequest
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class BoundedResponseTest {
    private fun exercise(kind: String, expected: String) {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            calls.incrementAndGet(); e.requestBody.use { it.readBytes() }
            runCatching {
                when(kind) {
                    "large" -> { e.sendResponseHeaders(200, 128001); e.responseBody.write(ByteArray(128001)) }
                    "stall" -> {
                        e.sendResponseHeaders(200, 100); e.responseBody.write(123); e.responseBody.flush()
                        Thread.sleep(2200)
                    }
                    "partial" -> { e.sendResponseHeaders(200, 100); e.responseBody.write(byteArrayOf(123, 125)); e.responseBody.flush() }
                }
            }
            runCatching { e.close() }
        }
        server.start()
        try {
            val path = Files.createTempDirectory("bounded-response").resolve("ledger.json")
            val gateway = OpenAiGateway("synthetic", LiveBudget.MODEL, "synthetic", LiveBudget(path),
                URI("http://127.0.0.1:${server.address.port}/"), Duration.ofMillis(600))
            val started = System.nanoTime()
            val failure = runCatching { gateway.assess(TriageRequest("Synthetic test only", "en", listOf("en"), true)) }.exceptionOrNull()
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertEquals(expected, (failure as LiveFailure).code)
            assertEquals(1, calls.get())
            assertTrue("Body processing exceeded its total deadline: $elapsedMs", elapsedMs < 1800)
            val entries = JsonParser.parseString(Files.readString(path)).asJsonObject["attempts"].asJsonArray
            assertEquals(1, entries.size())
            assertEquals(expected, entries[0].asJsonObject["status"].asString)
            assertEquals(LiveBudget.RESERVATION, entries[0].asJsonObject["chargedUpperUsd"].asDouble, 1e-9)
            if(kind == "stall") assertEquals("REQUEST_TIMEOUT", entries[0].asJsonObject["transportIssue"].asString)
        } finally { server.stop(0) }
    }
    @Test fun oversizedBodyIsRejectedWhileReceiving() = exercise("large", "RESPONSE_TOO_LARGE")
    @Test fun stalledBodyHasOverallDeadline() = exercise("stall", "OPENAI_NETWORK")
    @Test fun truncatedBodyNeverBecomesAValidAssessment() = exercise("partial", "OPENAI_NETWORK")
}
