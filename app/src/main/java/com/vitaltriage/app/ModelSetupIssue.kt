package com.vitaltriage.app

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Safe diagnostics only: no signed download URLs, headers, paths or exception text in UI/logs. */
internal enum class ModelSetupIssue {
    NETWORK, TIMEOUT, TLS, STORAGE, INTEGRITY, SERVER, OTHER;
    companion object {
        fun from(error:Throwable):ModelSetupIssue = when(error) {
            is ModelPreparationException -> error.issue
            is SSLException -> TLS
            is SocketTimeoutException -> TIMEOUT
            is UnknownHostException, is IOException -> NETWORK
            else -> OTHER
        }
    }
}
internal class ModelPreparationException(val issue:ModelSetupIssue):IllegalStateException(issue.name)
