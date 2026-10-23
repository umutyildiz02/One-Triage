package com.vitaltriage.app

import java.net.URI
import java.util.Locale

/** Debuggability never grants access to a development-only transport. */
internal object BackendConnectionPolicy {
    fun permits(endpoint:String,localDevelopment:Boolean):Boolean = runCatching {
        val uri=URI(endpoint.trim())
        val host=uri.host?.lowercase(Locale.ROOT) ?: return false
        if(uri.userInfo!=null || uri.query!=null || uri.fragment!=null || uri.port !in -1..65535)return false
        val loopback=host in setOf("localhost","127.0.0.1","::1","[::1]") || host.endsWith(".localhost")
        val emulatorHost=host in setOf("10.0.2.2","10.0.3.2")
        when(uri.scheme?.lowercase(Locale.ROOT)) {
            "https" -> localDevelopment || (!loopback && !emulatorHost)
            "http" -> localDevelopment && host in setOf("localhost","127.0.0.1")
            else -> false
        }
    }.getOrDefault(false)

    fun acceptsSource(mode:String,localDevelopment:Boolean)=
        mode=="LIVE" || (localDevelopment && mode=="DEMO")
}
