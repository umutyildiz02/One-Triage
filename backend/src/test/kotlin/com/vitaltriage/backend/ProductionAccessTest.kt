package com.vitaltriage.backend

import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.time.Instant

class ProductionAccessTest {
    @get:Rule val temporary=TemporaryFolder()
    private val token="synthetic_test_credential_not_for_real_use_123456"
    private fun row(id:String="user1",credential:String=token,enabled:Boolean=true,expiry:String="2030-01-01T00:00:00Z",minute:Int=2,day:Int=3)=
        mapOf("id" to id,"tokenSha256" to MessageDigest.getInstance("SHA-256").digest(credential.toByteArray()).joinToString(""){"%02x".format(it)},
            "enabled" to enabled,"expiresAt" to expiry,"requestsPerMinute" to minute,"requestsPerDay" to day)
    private fun users(vararg rows:Map<String,Any>)=Contract.gson.toJson(mapOf("version" to 1,"users" to rows.toList()))
    @Test fun distinctCredentialsAndRevocationAreEnforced() {
        val file=temporary.newFile("users.json");val usage=temporary.root.toPath().resolve("usage.json")
        file.writeText(users(row(),row("user2",token+"2")))
        val access=ProductionAccess(file.toPath(),usage) { Instant.parse("2026-10-20T00:00:00Z") }
        assertEquals("user1",access.authenticate("Bearer "+token)!!.id)
        assertEquals("user2",access.authenticate("Bearer "+token+"2")!!.id)
        for(header in listOf(null,"","Bearer short","Basic "+token,"Bearer "+token+"x"))assertNull(access.authenticate(header))
        file.writeText(users(row(enabled=false),row("user2",token+"2")))
        assertNull(access.authenticate("Bearer "+token))
        assertNotNull(access.authenticate("Bearer "+token+"2"))
    }
    @Test fun expiredCredentialIsRejected() {
        val file=temporary.newFile("users.json");file.writeText(users(row(expiry="2026-10-20T00:00:00Z")))
        val access=ProductionAccess(file.toPath(),temporary.root.toPath().resolve("usage")) { Instant.parse("2026-10-20T00:00:00Z") }
        assertNull(access.authenticate("Bearer "+token))
    }
    @Test fun quotaSurvivesRestartAndDoesNotRefillOnClockRollback() {
        val file=temporary.newFile("users.json");file.writeText(users(row()))
        val usage=temporary.root.toPath().resolve("usage")
        var now=Instant.parse("2026-10-20T00:00:00Z")
        var access=ProductionAccess(file.toPath(),usage) { now }
        val principal=access.authenticate("Bearer "+token)!!
        assertTrue(access.reserve(principal));assertTrue(access.reserve(principal))
        access=ProductionAccess(file.toPath(),usage) { now }
        assertFalse(access.reserve(principal))
        now=now.minusSeconds(60);assertFalse(access.reserve(principal))
        now=now.plusSeconds(121);assertTrue(access.reserve(principal))
        now=now.plusSeconds(61);assertFalse(access.reserve(principal))
        now=now.plusSeconds(86400);assertTrue(access.reserve(principal))
        val persisted=java.nio.file.Files.readString(usage)
        assertFalse(persisted.contains(token));assertFalse(persisted.contains("symptoms"))
    }
    @Test fun duplicateCredentialsAndMalformedFilesFailClosed() {
        val file=temporary.newFile("users.json");val usage=temporary.root.toPath().resolve("usage")
        file.writeText(users(row(),row("user2")))
        assertThrows(IllegalArgumentException::class.java) { ProductionAccess(file.toPath(),usage) }
        file.writeText(users(row()))
        val access=ProductionAccess(file.toPath(),usage) { Instant.parse("2026-10-20T00:00:00Z") }
        file.writeText("{}")
        assertThrows(Exception::class.java) { access.authenticate("Bearer "+token) }
    }
}
