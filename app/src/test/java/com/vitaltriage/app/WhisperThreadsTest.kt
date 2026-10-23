package com.vitaltriage.app
import org.junit.Assert.assertEquals
import org.junit.Test
class WhisperThreadsTest {
    @Test fun measuredEmulatorUsesAtMostFourAndNeverOversubscribesAvailableCores() {
        assertEquals(4,WhisperThreads.select("x86_64",8))
        assertEquals(4,WhisperThreads.select("x86_64",4))
        assertEquals(2,WhisperThreads.select("x86_64",2))
        assertEquals(1,WhisperThreads.select("x86_64",0))
    }
    @Test fun unmeasuredArchitecturesKeepConservativeBaseline() {
        assertEquals(2,WhisperThreads.select("arm64-v8a",8))
        assertEquals(1,WhisperThreads.select("arm64-v8a",1))
        assertEquals(2,WhisperThreads.select("unknown",8))
    }
}
