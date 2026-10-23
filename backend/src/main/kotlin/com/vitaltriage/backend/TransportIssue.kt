package com.vitaltriage.backend

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import javax.net.ssl.SSLException

/** Fixed categories only: exception messages may contain sensitive request information. */
enum class TransportIssue {
    CONNECT_TIMEOUT, REQUEST_TIMEOUT, DNS, TLS, CONNECTION, IO;
    companion object {
        fun classify(error: Throwable): TransportIssue? {
            val chain = generateSequence(error) { it.cause }.take(8).toList()
            return when {
                chain.any { it is HttpConnectTimeoutException } -> CONNECT_TIMEOUT
                chain.any { it is HttpTimeoutException } -> REQUEST_TIMEOUT
                chain.any { it is UnknownHostException } -> DNS
                chain.any { it is SSLException } -> TLS
                chain.any { it is ConnectException } -> CONNECTION
                chain.any { it is IOException } -> IO
                else -> null
            }
        }
    }
}
