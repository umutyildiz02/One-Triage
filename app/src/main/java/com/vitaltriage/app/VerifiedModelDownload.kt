package com.vitaltriage.app

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal data class ModelArtifact(val name: String, val bytes: Long, val sha256: String, val url: String) {
    init {
        require(name.matches(Regex("[A-Za-z0-9_.-]+")) && name != "." && name != "..")
        require(bytes > 0 && sha256.matches(Regex("[a-f0-9]{64}")))
        val parsed = java.net.URI(url)
        require(parsed.scheme == "https" && parsed.host != null && parsed.userInfo == null && parsed.fragment == null)
    }
}

/** Explicit setup only. Fixed HTTPS artifacts, resumable bytes, final hash, atomic install. */
internal class VerifiedModelDownload(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.MINUTES).followSslRedirects(false).retryOnConnectionFailure(false).build()) {
    private val cancelled = AtomicBoolean()
    @Volatile private var call: Call? = null
    fun cancel() { cancelled.set(true); call?.cancel() }
    private fun active() { if(cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException("Model preparation cancelled") }
    fun prepare(directory: File, artifact: ModelArtifact, progress: (Long, Long) -> Unit): File {
        active()
        check(directory.isDirectory || directory.mkdirs()) { "Model directory unavailable" }
        val target = File(directory, artifact.name)
        if (valid(target, artifact)) { progress(artifact.bytes, artifact.bytes); return target }
        val partial = File(directory, artifact.name + ".part")
        check(!partial.exists() || partial.length() <= artifact.bytes) { "Invalid partial model; manual inspection required" }
        var offset = if(partial.isFile) partial.length() else 0L
        if(directory.usableSpace < artifact.bytes - offset + 128_000_000L) throw ModelPreparationException(ModelSetupIssue.STORAGE)
        if(offset < artifact.bytes) {
            val request = Request.Builder().url(artifact.url)
                .header("Accept-Encoding", "identity").apply { if(offset > 0) header("Range", "bytes=$offset-") }.build()
            val current = client.newCall(request)
            call = current
            try {
                active()
                current.execute().use { response ->
                    active()
                    check(response.request.url.isHttps) { "Model redirect must use HTTPS" }
                    if(response.code == 200) offset = 0
                    else {
                        if(response.code != 206) throw ModelPreparationException(ModelSetupIssue.SERVER)
                        val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.header("Content-Range").orEmpty())
                        check(range != null && range.groupValues[1].toLong() == offset &&
                            range.groupValues[2].toLong() == artifact.bytes - 1 &&
                            range.groupValues[3].toLong() == artifact.bytes) { "Invalid resumed model range" }
                    }
                    response.body!!.byteStream().use { input ->
                        RandomAccessFile(partial, "rw").use { output ->
                            output.setLength(offset); output.seek(offset)
                            val buffer = ByteArray(65536)
                            var done = offset
                            while(true) {
                                active()
                                val count = input.read(buffer)
                                if(count < 0) break
                                check(done + count <= artifact.bytes) { "Model exceeds expected size" }
                                output.write(buffer, 0, count); done += count
                                progress(done, artifact.bytes)
                            }
                            output.fd.sync()
                            check(done == artifact.bytes) { "Incomplete model; partial download retained" }
                        }
                    }
                }
            } finally { call = null }
        }
        active()
        if (!valid(partial, artifact)) {
            // Only this task-owned partial is disposable; preserve any existing installed model.
            partial.delete()
            throw ModelPreparationException(ModelSetupIssue.INTEGRITY)
        }
        active()
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        progress(artifact.bytes, artifact.bytes)
        return target
    }
    companion object {
        fun valid(file: File, artifact: ModelArtifact): Boolean {
            if(!file.isFile || file.length() != artifact.bytes) return false
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(65536)
                while(true) { val n=input.read(buffer); if(n<0)break; digest.update(buffer,0,n) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) } == artifact.sha256
        }
    }
}
