package com.vitaltriage.app
import org.junit.Assert.*
import org.junit.Test
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
class ModelSetupIssueTest {
    @Test fun networkTimeoutAndTlsHaveDistinctSafeCategories() {
        assertEquals(ModelSetupIssue.NETWORK,ModelSetupIssue.from(UnknownHostException("private.example")))
        assertEquals(ModelSetupIssue.TIMEOUT,ModelSetupIssue.from(SocketTimeoutException("signed URL must not escape")))
        assertEquals(ModelSetupIssue.TLS,ModelSetupIssue.from(SSLHandshakeException("certificate details")))
    }
    @Test fun storageIntegrityAndServerErrorsAreNeverReportedReady() {
        for(issue in listOf(ModelSetupIssue.STORAGE,ModelSetupIssue.INTEGRITY,ModelSetupIssue.SERVER)) {
            assertEquals(issue,ModelSetupIssue.from(ModelPreparationException(issue)))
        }
        assertEquals(ModelSetupIssue.OTHER,ModelSetupIssue.from(IllegalArgumentException("not a public error")))
    }
}
