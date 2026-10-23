package com.vitaltriage.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vitaltriage.core.Contract
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.nio.file.StandardCopyOption.*
import java.time.Instant
import java.util.UUID

class LiveFailure(val code: String) : RuntimeException(code)

/** Local, durable, fail-closed budget. Contains IDs/counts/costs only, never content or credentials. */
class LiveBudget(private val path: Path, private val initialState: String? = null) {
    companion object {
        private val processLock = Any()
        const val MODEL = "gpt-4.1-mini"
        const val INPUT_LIMIT = 32000
        const val OUTPUT_LIMIT = 1800
        // Owner authorization 2026-10-23: 100 total attempts; all existing history is retained.
        const val MAX_CALLS = 100
        const val AUTHORIZED_USD = 5.0
        const val USD_LIMIT = 3.0
        const val INPUT_USD = 0.40 / 1_000_000
        const val OUTPUT_USD = 1.60 / 1_000_000
        const val RESERVATION = INPUT_LIMIT * INPUT_USD + OUTPUT_LIMIT * OUTPUT_USD
    }
    private fun <T> transaction(block: (JsonObject) -> T): T = synchronized(processLock) {
        Files.createDirectories(path.toAbsolutePath().parent)
        FileChannel.open(path.resolveSibling(path.fileName.toString() + ".lock"), CREATE, WRITE).use { channel ->
            channel.lock().use {
                val state = if (Files.exists(path)) JsonParser.parseString(String(Files.readAllBytes(path), Charsets.UTF_8)).asJsonObject else if (initialState != null) JsonParser.parseString(initialState).asJsonObject else JsonObject().apply {
                    addProperty("version", 1); add("attempts", com.google.gson.JsonArray())
                }
                check(state["version"].asInt == 1)
                val entries = state["attempts"].asJsonArray
                check(entries.map { it.asJsonObject["clientRequestId"].asString }.distinct().size == entries.size())
                check(entries.all { val cost = it.asJsonObject["chargedUpperUsd"].asDouble; cost.isFinite() && cost >= 0 })
                val result = block(state)
                val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
                FileChannel.open(temp, CREATE, TRUNCATE_EXISTING, WRITE).use { out ->
                    val buffer = java.nio.ByteBuffer.wrap(Contract.gson.toJson(state).toByteArray())
                    while (buffer.hasRemaining()) out.write(buffer)
                    out.force(true)
                }
                // Refuse to run rather than silently downgrade atomic budget persistence.
                Files.move(temp, path, ATOMIC_MOVE, REPLACE_EXISTING)
                return@synchronized result
            }
        }
    }
    @Synchronized fun reserve(): String = transaction { state ->
        val attempts = state["attempts"].asJsonArray
        val spent = attempts.sumOf { it.asJsonObject["chargedUpperUsd"].asDouble }
        if (attempts.size() >= MAX_CALLS || spent + RESERVATION >= USD_LIMIT) throw LiveFailure("TEST_BUDGET_LIMIT")
        val id = UUID.randomUUID().toString()
        attempts.add(JsonObject().apply {
            addProperty("clientRequestId", id); addProperty("time", Instant.now().toString())
            addProperty("model", MODEL); addProperty("status", "RESERVED")
            addProperty("chargedUpperUsd", RESERVATION)
        })
        id
    }
    @Synchronized fun record(id: String, status: String, requestId: String? = null, root: JsonObject? = null,
                             transportIssue: Enum<*>? = null) = transaction { state ->
        val entry = state["attempts"].asJsonArray.first { it.asJsonObject["clientRequestId"].asString == id }.asJsonObject
        entry.addProperty("status", status)
        transportIssue?.let { entry.addProperty("transportIssue", it.name) }
        fun safe(value: String?) = value?.takeIf { it.length <= 160 && it.matches(Regex("[A-Za-z0-9_.:-]+")) }
        safe(requestId)?.let { entry.addProperty("requestId", it) }
        safe(root?.get("id")?.asString)?.let { entry.addProperty("responseId", it) }
        safe(root?.get("model")?.asString)?.let { entry.addProperty("returnedModel", it) }
        val usage = root?.getAsJsonObject("usage")
        if (usage != null) {
            val input = usage["input_tokens"].asInt
            val output = usage["output_tokens"].asInt
            check(input in 0..INPUT_LIMIT && output in 0..OUTPUT_LIMIT)
            entry.addProperty("inputTokens", input); entry.addProperty("outputTokens", output)
            // Ignore cached discounts: deliberately conservative standard-tier estimate.
            entry.addProperty("chargedUpperUsd", input * INPUT_USD + output * OUTPUT_USD)
        }
    }
}
