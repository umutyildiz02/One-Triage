package com.vitaltriage.backend

import com.vitaltriage.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URI
import java.net.http.*
import java.security.MessageDigest
import java.time.Instant

class ProductionServerTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun realHttpRouteRequiresPerUserAuthAndPersistsQuota() {
        val token="synthetic_production_test_credential_123456789"
        val credential=temporary.newFile("users.json")
        credential.writeText(Contract.gson.toJson(mapOf("version" to 1,"users" to listOf(mapOf(
            "id" to "test-user","tokenSha256" to MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString(""){"%02x".format(it)},
            "enabled" to true,"expiresAt" to "2030-01-01T00:00:00Z","requestsPerMinute" to 1,"requestsPerDay" to 1)))))
        val access=ProductionAccess(credential.toPath(),temporary.root.toPath().resolve("usage.json")) { Instant.parse("2026-10-20T00:00:00Z") }
        var calls=0
        val input=TriageRequest("Synthetic mild irritation lasting two days","en",listOf("en"),true,false,"LOW")
        TriageServer(0,"LIVE",gateway=ModelGateway { calls++;Safety.unknown(it.language) },productionAccess=access).use { server ->
            server.start()
            fun post(secret:String)=HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:"+server.port+"/v1/triage"))
                    .header("Content-Type","application/json").header("Authorization","Bearer "+secret)
                    .POST(HttpRequest.BodyPublishers.ofString(Contract.gson.toJson(input))).build(),HttpResponse.BodyHandlers.ofString())
            assertEquals(401,post("incorrect").statusCode());assertEquals(0,calls)
            assertEquals(200,post(token).statusCode());assertEquals(1,calls)
            assertEquals(429,post(token).statusCode());assertEquals(1,calls)
            credential.writeText("{}")
            val rejected=post(token)
            assertEquals(503,rejected.statusCode());assertEquals(1,calls)
            assertFalse(rejected.body().contains(input.symptoms));assertFalse(rejected.body().contains(token))
        }
    }
}
