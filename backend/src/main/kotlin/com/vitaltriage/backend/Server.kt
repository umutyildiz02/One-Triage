package com.vitaltriage.backend

import com.vitaltriage.core.*
import com.google.gson.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

fun interface ModelGateway { fun assess(r: TriageRequest): Assessment }
class OpenAiGateway(private val key: String, private val model: String, private val prompt: String, private val budget: LiveBudget,
                    private val endpoint: URI = URI("https://api.openai.com/v1/responses"),
                    private val timeout: Duration = Duration.ofSeconds(45)): ModelGateway {
    init { require(model == LiveBudget.MODEL) { "Model has no reviewed budget pricing." } }
    // Explicit HTTP/1.1 avoids the persistent HTTP/2 transport used during intermittent local failures.
    // Keep one bounded client and never automatically retry a potentially billed POST.
    private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10)).build()
    override fun assess(r: TriageRequest): Assessment {
        val body = OpenAiProtocol.request(r, model, prompt)
        val attempt = budget.reserve()
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout).header("Authorization", "Bearer $key")
            .header("Content-Type","application/json").header("X-Client-Request-Id", attempt).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        var requestId: String? = null
        var root: JsonObject? = null
        try {
            val pending = client.sendAsync(request) { BoundedResponseBody(128000) }
            val response = try {
                // Covers headers AND body. ofInputStream + readNBytes did not bound a stalled body.
                pending.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                pending.cancel(true)
                throw java.net.http.HttpTimeoutException("Provider response deadline")
            } catch (e: java.util.concurrent.ExecutionException) {
                throw (e.cause ?: e)
            } catch (e: InterruptedException) {
                pending.cancel(true); Thread.currentThread().interrupt(); throw e
            }
            requestId = response.headers().firstValue("x-request-id").orElse(null)
            val raw = response.body()
            root = runCatching { JsonParser.parseString(String(raw, StandardCharsets.UTF_8)).asJsonObject }.getOrNull()
            if (response.statusCode() !in 200..299) {
                val code = root?.getAsJsonObject("error")?.get("code")?.asString
                throw LiveFailure(when {
                    response.statusCode() == 401 -> "OPENAI_AUTH"
                    code == "insufficient_quota" -> "OPENAI_CREDIT"
                    response.statusCode() == 403 -> "OPENAI_PERMISSION"
                    response.statusCode() == 404 || code == "model_not_found" -> "OPENAI_MODEL"
                    response.statusCode() == 429 -> "OPENAI_RATE_LIMIT"
                    else -> "OPENAI_REQUEST"
                })
            }
            val result = decodeResponse(String(raw, StandardCharsets.UTF_8), r.language)
            if (Safety.enforce(result, r) != result) throw LiveFailure("OPENAI_SAFETY_CONFLICT")
            budget.record(attempt, "VALIDATED", requestId, root)
            return result
        } catch (e: Exception) {
            val safe = if (e is LiveFailure) e.code else if (e is java.io.IOException) "OPENAI_NETWORK" else "OPENAI_INVALID_RESPONSE"
            // No automatic retries. Uncertain charges keep their full reservation.
            budget.record(attempt, safe, requestId, root?.takeIf { it.has("usage") && !it["usage"].isJsonNull }, TransportIssue.classify(e))
            throw LiveFailure(safe)
        }
    }
    companion object {
        fun decodeResponse(raw: String, language: String): Assessment = OpenAiProtocol.decode(raw, language)

    }
}

class TriageServer(port: Int, private val mode: String = "DEMO", private val token: String = "",
                   private val gateway: ModelGateway? = null, private val productionAccess: ProductionAccess? = null): AutoCloseable {
    private val pool = Executors.newFixedThreadPool(4)
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1",port), 16)
    private val inFlight = Semaphore(2)
    private var window = System.currentTimeMillis()
    private val count = AtomicInteger()
    val port: Int get() = server.address.port
    init {
        require(mode in setOf("DEMO","LIVE"))
        if(mode == "LIVE") require(gateway != null && (productionAccess != null || token.length >= 32))
        require(productionAccess == null || mode == "LIVE")
        server.executor = pool
        server.createContext("/health") { e -> send(e,200,"{\"status\":\"ok\",\"mode\":\"$mode\"}") }
        server.createContext("/v1/triage") { e -> handle(e) }
    }
    fun start() = server.start()
    @Synchronized private fun allowed(): Boolean {
        val now=System.currentTimeMillis()
        if(now-window>60000){ window=now; count.set(0) }
        return count.incrementAndGet()<=30
    }
    private fun handle(e: HttpExchange) {
        if(e.requestURI.path!="/v1/triage" || e.requestMethod!="POST") { send(e,405,error("METHOD")); return }
        if(e.requestHeaders.getFirst("Origin")!=null) { send(e,403,error("ORIGIN")); return }
        if(!e.requestHeaders.getFirst("Content-Type").orEmpty().startsWith("application/json")) { send(e,415,error("CONTENT_TYPE")); return }
        var principal:ProductionAccess.Principal?=null
        if(mode=="LIVE") {
            if(productionAccess!=null) {
                principal=try { productionAccess.authenticate(e.requestHeaders.getFirst("Authorization")) }
                    catch(_:Exception) { send(e,503,error("UNAVAILABLE"));return }
                if(principal==null) { send(e,401,error("AUTH"));return }
            } else if(!MessageDigest.isEqual(("Bearer $token").toByteArray(),
                e.requestHeaders.getFirst("Authorization").orEmpty().toByteArray())) { send(e,401,error("AUTH"));return }
        }
        if(!allowed() || !inFlight.tryAcquire()) { send(e,429,error("BUSY")); return }
        try {
            val bytes=e.requestBody.use { it.readNBytes(16001) }
            if(bytes.size>16000) { send(e,413,error("TOO_LARGE")); return }
            val r=try { Contract.request(String(bytes,StandardCharsets.UTF_8)) } catch (_:Exception) { send(e,400,error("INVALID_REQUEST")); return }
            // A local information guard is not a successful model response.
            if(mode=="LIVE" && Safety.insufficient(r)) { send(e,422,error("INSUFFICIENT_INFORMATION")); return }
            if(principal!=null && !productionAccess!!.reserve(principal)) { send(e,429,error("BUSY"));return }
            val result = try {
                if(Safety.insufficient(r)) Evaluation(Safety.unknown(r.language),mode)
                else if(mode=="DEMO") Demo.evaluate(r)
                else Evaluation(Safety.enforce(Contract.parse(Contract.encode(gateway!!.assess(r)),r.language),r),mode)
            } catch (failure:LiveFailure) { send(e,502,error(failure.code)); return }
              catch (_:Exception) { send(e,502,error("ASSESSMENT_UNAVAILABLE")); return }
            send(e,200,Contract.gson.toJson(result))
        } catch (_:Exception) {
            runCatching { send(e,500,error("UNAVAILABLE")) }
        } finally { inFlight.release(); e.close() }
    }
    private fun error(code:String) = """{"error":"$code"}"""
    private fun send(e:HttpExchange,code:Int,body:String) {
        val bytes=body.toByteArray(StandardCharsets.UTF_8)
        e.responseHeaders.set("Content-Type","application/json; charset=utf-8")
        e.responseHeaders.set("Cache-Control","no-store")
        e.responseHeaders.set("X-Content-Type-Options","nosniff")
        e.sendResponseHeaders(code,bytes.size.toLong())
        e.responseBody.use { it.write(bytes) }
        e.close()
    }
    override fun close(){ server.stop(0); pool.shutdownNow() }
}

fun main() {
    val deployment=System.getenv("VT_DEPLOYMENT")?.uppercase() ?: "LOCAL"
    require(deployment in setOf("LOCAL","PRODUCTION")) { "Unknown deployment mode" }
    val production=deployment=="PRODUCTION"
    val mode=System.getenv("VT_MODE")?.uppercase() ?: "DEMO"
    require(!production || mode=="LIVE") { "Production deployment cannot silently use Demo" }
    val gateway=if(mode=="LIVE") {
        val key=System.getenv("OPENAI_API_KEY").orEmpty()
        val model=System.getenv("OPENAI_MODEL").orEmpty()
        require(key.isNotBlank() && model.isNotBlank()) { "Configure server-side OPENAI_API_KEY and OPENAI_MODEL." }
        val custom=System.getenv("TRIAGE_PROMPT_FILE")
        val prompt=if(custom.isNullOrBlank()) object {}.javaClass.getResource("/triage-system.txt")!!.readText()
            else java.io.File(custom).readText()
        System.setProperty("jdk.httpclient.disableRetryConnect", "true")
        // JDK17 defaults to a 1200-second HTTP/1.1 idle cache. Avoid reusing old local-proxy sockets.
        // This changes idle retention only, not the active request deadline, TLS checks or retries.
        System.setProperty("jdk.httpclient.keepalive.timeout", "5")
        val ledger = java.nio.file.Path.of(System.getenv("VT_BUDGET_FILE") ?: ".runtime/live-budget.json").toAbsolutePath()
        if(production)require(!System.getenv("VT_BUDGET_FILE").isNullOrBlank() && java.nio.file.Files.isRegularFile(ledger)) {
            "Production requires an explicit existing budget ledger; preserve authorized usage history."
        }
        OpenAiGateway(key,model,prompt,LiveBudget(ledger))
    } else null
    val access=if(production) {
        val users=System.getenv("VT_PRODUCTION_USERS_FILE").orEmpty()
        val usage=System.getenv("VT_PRODUCTION_USAGE_FILE").orEmpty()
        require(users.isNotBlank() && usage.isNotBlank()) { "Configure production user credentials and durable usage paths." }
        ProductionAccess(java.nio.file.Path.of(users),java.nio.file.Path.of(usage))
    } else null
    val server=TriageServer(System.getenv("PORT")?.toIntOrNull() ?: 8787,mode,
        if(production)"" else System.getenv("VT_ACCESS_TOKEN").orEmpty(),gateway,access)
    server.start()
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
    println("VitalTriage " + mode + " listening on loopback port " + server.port + ". Request/response logging disabled.")
}
