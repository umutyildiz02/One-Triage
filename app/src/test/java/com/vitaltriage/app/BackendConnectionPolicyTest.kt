package com.vitaltriage.app

import org.junit.Assert.*
import org.junit.Test

class BackendConnectionPolicyTest {
    @Test fun independentDeviceRequiresHttpsWithoutHostLoopback() {
        assertTrue(BackendConnectionPolicy.permits("https://assessment.example.org",false))
        assertTrue(BackendConnectionPolicy.permits("https://assessment.example.org/service",false))
        listOf("http://assessment.example.org","http://127.0.0.1:8787",
            "https://127.0.0.1","https://localhost","https://[::1]","https://10.0.2.2","https://10.0.3.2",
            "https://anything.localhost","", "not a URL").forEach {
            assertFalse(it,BackendConnectionPolicy.permits(it,false))
        }
    }
    @Test fun developmentHttpIsLimitedToExplicitLoopback() {
        assertTrue(BackendConnectionPolicy.permits("http://127.0.0.1:8787",true))
        assertTrue(BackendConnectionPolicy.permits("http://localhost:8787",true))
        assertFalse(BackendConnectionPolicy.permits("http://example.org",true))
        assertFalse(BackendConnectionPolicy.permits("http://10.0.2.2",true))
    }
    @Test fun credentialsAndQueryTokensNeverBelongInEndpoint() {
        for(local in listOf(false,true))for(url in listOf(
            "https://user:password@example.org","https://example.org?token=synthetic",
            "https://example.org#synthetic","https://example.org:70000"
        ))assertFalse(BackendConnectionPolicy.permits(url,local))
    }
    @Test fun independentDeviceRejectsDemoBackendEvenWhenJsonIsValid() {
        assertTrue(BackendConnectionPolicy.acceptsSource("LIVE",false))
        assertFalse(BackendConnectionPolicy.acceptsSource("DEMO",false))
        assertTrue(BackendConnectionPolicy.acceptsSource("DEMO",true))
        assertFalse(BackendConnectionPolicy.acceptsSource("unknown",true))
    }
    @Test fun missingModelPreferenceKeepsVerifiedMediumQualityDefault() {
        assertEquals(WhisperModel.MEDIUM,WhisperModel.fromKey(null))
        assertEquals(WhisperModel.MEDIUM,WhisperModel.fromKey("unknown"))
        assertEquals(WhisperModel.SMALL,WhisperModel.fromKey("small"))
    }
}
