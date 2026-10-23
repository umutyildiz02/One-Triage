package com.vitaltriage.app

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vitaltriage.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Personal device build only. Embedded credentials are extractable, not protected by obfuscation. */
internal class DirectOpenAiRepository(
    private val key: () -> String,
    private val prompt: () -> String,
    private val budget: () -> LiveBudget,
    private val endpoint: String = "https://api.openai.com/v1/responses",
    private val networkAvailable: () -> Boolean = { true },
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()
) : AssessmentRepository {
    companion object {
        fun personal(context: Context): DirectOpenAiRepository {
            val app = context.applicationContext
            fun asset(name: String) = app.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return DirectOpenAiRepository(
                key = { asset("personal-openai-key.txt").trim() },
                prompt = { asset("triage-system.txt") },
                budget = { LiveBudget(File(app.noBackupFilesDir, "openai-live-budget.json").toPath(),
                    asset("openai-budget-seed.json")) },
                networkAvailable = {
                    val cm=app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                })
        }
    }
    override suspend fun assess(r: TriageRequest, endpoint: String, token: String): Evaluation = withContext(Dispatchers.IO) {
        // A known offline state consumes no paid-attempt reservation.
        if (!networkAvailable()) throw BackendFailure("OPENAI_NETWORK")
        if (Safety.insufficient(r)) throw BackendFailure("INSUFFICIENT_INFORMATION")
        // endpoint/token from optional backend settings can never redirect the embedded API key.
        val payload = try { OpenAiProtocol.request(r, LiveBudget.MODEL, prompt()) }
            catch (e: LiveFailure) { throw BackendFailure(e.code) }
            catch (_: Exception) { throw BackendFailure("DIRECT_CONFIGURATION") }
        val apiKey = try { key().also { require(it.matches(Regex("sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}"))) } }
            catch (_: Exception) { throw BackendFailure("DIRECT_CONFIGURATION") }
        val ledger = try { budget() } catch (_: Exception) { throw BackendFailure("TEST_BUDGET_STATE") }
        val id = try { ledger.reserve() } catch (e: LiveFailure) { throw BackendFailure(e.code) }
            catch (_: Exception) { throw BackendFailure("TEST_BUDGET_STATE") }
        val request = Request.Builder().url(this@DirectOpenAiRepository.endpoint)
            .header("Authorization", "Bearer $apiKey").header("X-Client-Request-Id", id)
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Cancellation/unknown charges retain the full durable reservation.
                    runCatching { ledger.record(id, if(call.isCanceled()) "CANCELLED" else "OPENAI_NETWORK") }
                    if (continuation.isActive) continuation.resumeWith(Result.failure(BackendFailure("OPENAI_NETWORK")))
                }
                override fun onResponse(call: Call, response: Response) {
                    var root: JsonObject? = null
                    val requestId = response.header("x-request-id")
                    val result = runCatching {
                        response.use {
                            val body = it.body ?: throw LiveFailure("OPENAI_INVALID_RESPONSE")
                            val output = ByteArrayOutputStream()
                            body.byteStream().use { input ->
                                val buffer = ByteArray(4096)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > 128000) throw LiveFailure("OPENAI_INVALID_RESPONSE")
                                    output.write(buffer, 0, count)
                                }
                            }
                            val raw = output.toString("UTF-8")
                            root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
                            if (!it.isSuccessful) throw LiveFailure(OpenAiProtocol.httpError(it.code, root))
                            val assessment = OpenAiProtocol.validate(raw, r)
                            ledger.record(id, "VALIDATED", requestId, root)
                            Evaluation(assessment, "DIRECT_OPENAI", false)
                        }
                    }.recoverCatching { error ->
                        val code = when(error) {
                            is LiveFailure -> error.code
                            is IOException -> "OPENAI_NETWORK"
                            else -> "OPENAI_INVALID_RESPONSE"
                        }
                        // If usage is missing/malformed, never reduce the original reservation.
                        runCatching { ledger.record(id, code, requestId, root?.takeIf { it.has("usage") && !it["usage"].isJsonNull }) }
                        throw BackendFailure(code)
                    }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
    }
}
