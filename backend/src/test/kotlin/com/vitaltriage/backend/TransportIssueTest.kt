package com.vitaltriage.backend

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import com.vitaltriage.core.TriageRequest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException

class TransportIssueTest {
    @Test fun fixedCategoriesDoNotPersistExceptionMessages() {
        val examples = listOf(
            HttpConnectTimeoutException("private") to TransportIssue.CONNECT_TIMEOUT,
            HttpTimeoutException("private") to TransportIssue.REQUEST_TIMEOUT,
            IOException("private", UnknownHostException("private")) to TransportIssue.DNS,
            SSLHandshakeException("private") to TransportIssue.TLS,
            java.net.ConnectException("private") to TransportIssue.CONNECTION,
            IOException("private") to TransportIssue.IO
        )
        val path = Files.createTempDirectory("transport-test").resolve("ledger.json")
        val budget = LiveBudget(path)
        examples.forEach { (error, expected) ->
            assertEquals(expected, TransportIssue.classify(error))
            budget.record(budget.reserve(), "OPENAI_NETWORK", transportIssue = TransportIssue.classify(error))
        }
        assertNull(TransportIssue.classify(IllegalArgumentException("private")))
        val raw = Files.readString(path)
        assertFalse(raw.contains("private"))
        val entries = JsonParser.parseString(raw).asJsonObject["attempts"].asJsonArray
        assertEquals(examples.size, entries.size())
        examples.forEachIndexed { index, (_, expected) ->
            assertEquals(expected.name, entries[index].asJsonObject["transportIssue"].asString)
            assertEquals(LiveBudget.RESERVATION, entries[index].asJsonObject["chargedUpperUsd"].asDouble, 1e-9)
        }
    }
    @Test fun localTimeoutIsNotRetriedAndRetainsReservation() {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            calls.incrementAndGet()
            Thread.sleep(700)
            runCatching { exchange.sendResponseHeaders(503, -1) }
            exchange.close()
        }
        server.start()
        try {
            val path = Files.createTempDirectory("timeout-test").resolve("ledger.json")
            val gateway = OpenAiGateway("synthetic-token", LiveBudget.MODEL, "synthetic", LiveBudget(path),
                URI("http://127.0.0.1:${server.address.port}/"), Duration.ofMillis(250))
            val failure = runCatching { gateway.assess(TriageRequest("Synthetic test only", "en", listOf("en"), true)) }.exceptionOrNull()
            assertEquals("OPENAI_NETWORK", (failure as LiveFailure).code)
            assertEquals(1, calls.get())
            val entries = JsonParser.parseString(Files.readString(path)).asJsonObject["attempts"].asJsonArray
            assertEquals(1, entries.size())
            assertEquals("REQUEST_TIMEOUT", entries[0].asJsonObject["transportIssue"].asString)
            assertEquals(LiveBudget.RESERVATION, entries[0].asJsonObject["chargedUpperUsd"].asDouble, 1e-9)
        } finally { server.stop(0) }
    }
}
