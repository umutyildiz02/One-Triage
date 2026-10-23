package com.vitaltriage.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NLLB_CACHE encoder -> cross-attention cache -> greedy decoder -> LM head,
 * adapted from Luca Martino's RTranslator Translator.java (Apache-2.0).
 * Model weights are separately CC-BY-NC-4.0; research-only, not clinically validated.
 * No network, patient logging, automatic assessment, or source-text mutation.
 * One worker owns each engine. cancel() is safe from the UI thread; close() is worker-only.
 */
internal class NllbEngine(directory: File, threads: Int = 2,
    private val memoryPressure: () -> Boolean = { false }) : Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val cancelled = AtomicBoolean(false)
    private val resources = ArrayList<AutoCloseable>()
    @Volatile private var running: OrtSession.RunOptions? = null
    private val tokenizer: NllbTokenizer
    private val encoder: OrtSession
    private val decoder: OrtSession
    private val embedding: OrtSession
    private val cache: OrtSession

    init {
        try {
            checkActive()
            tokenizer = NllbTokenizer(File(directory, "sentencepiece_bpe.model")).also { resources.add(it) }
            fun session(name: String): OrtSession = OrtSession.SessionOptions().use { options ->
                checkActive()
                options.setIntraOpNumThreads(threads.coerceIn(1, 4))
                options.setInterOpNumThreads(1)
                options.setMemoryPatternOptimization(false)
                options.setCPUArenaAllocator(false)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                env.createSession(File(directory, name).absolutePath, options).also { resources.add(it) }
            }
            encoder = session("NLLB_encoder.onnx")
            decoder = session("NLLB_decoder.onnx")
            embedding = session("NLLB_embed_and_lm_head.onnx")
            cache = session("NLLB_cache_initializer.onnx")
            checkActive()
        } catch (failure: Throwable) {
            resources.asReversed().forEach { runCatching { it.close() } }
            throw failure
        }
    }

    fun cancel() {
        cancelled.set(true)
        synchronized(this) { running?.let { runCatching { it.setTerminate(true) } } }
    }

    private fun checkActive() {
        if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException("Translation cancelled")
        if (memoryPressure()) throw SpeechMemoryException()
    }

    private fun run(session: OrtSession, input: Map<String, OnnxTensor>, outputs: Set<String>? = null): OrtSession.Result {
        checkActive()
        val options = OrtSession.RunOptions()
        synchronized(this) { running = options; if (cancelled.get()) options.setTerminate(true) }
        try {
            return if (outputs == null) session.run(input, options) else session.run(input, outputs, options)
        } finally {
            synchronized(this) { running = null; options.close() }
        }
    }

    private fun longs(values: LongArray) = OnnxTensor.createTensor(env, LongBuffer.wrap(values), longArrayOf(1, values.size.toLong()))
    private fun emptyFloat(vararg shape: Long) = OnnxTensor.createTensor(env, FloatBuffer.allocate(shape.fold(1L) { a, b -> a * b }.toInt()), shape)
    private fun tensor(result: OrtSession.Result, name: String) = result.get(name).orElseThrow { IllegalStateException("Missing translation tensor") } as OnnxTensor

    /** Bounded sentence input; never silently truncate medically relevant text. */
    fun translate(original: String, source: String, target: String): String {
        checkActive()
        require(original.isNotBlank() && original.length <= 4000)
        val targetId = NllbTokenizer.languageId(target)
        NllbTokenizer.languageId(source)
        if (source == target) return original
        val adapted = NllbInput.forTokenizer(original)
        val ids = tokenizer.encode(adapted, source)
        require(3L !in ids) { "Translation vocabulary cannot represent all input characters; preserve the original." }
        require(ids.size <= 256) { "Translation input exceeds the model's verified window; split and review the original." }
        // Reuse the same model sessions; reset decoder/cache per sentence, as in the
        // legacy loop. Never publish a partial translation if any segment fails.
        val segments = NllbInput.sentences(adapted, source).map { tokenizer.encode(it, source) }
        require(segments.all { it.size <= 256 && 3L !in it })
        return segments.joinToString(" ") { segment ->
            checkActive()
            translateTokens(segment, targetId)
        }
    }

    private fun translateTokens(ids: LongArray, targetId: Long): String {
        longs(ids).use { sourceIds ->
            longs(LongArray(ids.size) { 1 }).use { mask ->
                emptyFloat(1, 1, 1024).use { noLogits ->
                    OnnxTensor.createTensor(env, booleanArrayOf(false)).use { embedMode ->
                        OnnxTensor.createTensor(env, booleanArrayOf(true)).use { headMode ->
                            OnnxTensor.createTensor(env, LongBuffer.allocate(2), longArrayOf(1, 2)).use { noIds ->
                                run(embedding, mapOf("input_ids" to sourceIds, "pre_logits" to noLogits, "use_lm_head" to embedMode), setOf("embed_matrix")).use { sourceEmbedding ->
                                    run(encoder, mapOf("input_ids" to sourceIds, "attention_mask" to mask, "embed_matrix" to tensor(sourceEmbedding, "embed_matrix"))).use { encoded ->
                                        run(cache, mapOf("encoder_hidden_states" to tensor(encoded, "last_hidden_state"))).use { initialized ->
                                            emptyFloat(1, 16, 0, 64).use { emptyPast ->
                                                var previous: OrtSession.Result? = null
                                                val generated = ArrayList<Long>()
                                                var next = 2L
                                                try {
                                                    repeat(256) { step ->
                                                        checkActive()
                                                        longs(longArrayOf(next)).use { decoderIds ->
                                                            run(embedding, mapOf("input_ids" to decoderIds, "pre_logits" to noLogits, "use_lm_head" to embedMode), setOf("embed_matrix")).use { decodedEmbedding ->
                                                                val input = hashMapOf("input_ids" to decoderIds, "encoder_attention_mask" to mask, "embed_matrix" to tensor(decodedEmbedding, "embed_matrix"))
                                                                for (layer in 0 until 12) {
                                                                    for (kind in listOf("key", "value")) {
                                                                        input["past_key_values.$layer.encoder.$kind"] = tensor(initialized, "present.$layer.encoder.$kind")
                                                                        input["past_key_values.$layer.decoder.$kind"] = previous?.let { tensor(it, "present.$layer.decoder.$kind") } ?: emptyPast
                                                                    }
                                                                }
                                                                val current = run(decoder, input)
                                                                previous?.close()
                                                                previous = current
                                                                if (step == 0) {
                                                                    // Force the requested language, as in the legacy first decoder iteration.
                                                                    next = targetId
                                                                } else {
                                                                    run(embedding, mapOf("input_ids" to noIds, "pre_logits" to tensor(current, "pre_logits"), "use_lm_head" to headMode), setOf("logits")).use { head ->
                                                                        val logits = tensor(head, "logits").floatBuffer
                                                                        var best = Float.NEGATIVE_INFINITY
                                                                        var bestId = -1
                                                                        var index = 0
                                                                        while (logits.hasRemaining()) {
                                                                            val value = logits.get()
                                                                            if (value.isFinite() && value > best) { best = value; bestId = index }
                                                                            index++
                                                                        }
                                                                        check(bestId >= 0) { "Invalid translation logits" }
                                                                        next = bestId.toLong()
                                                                    }
                                                                    if (next == 2L) {
                                                                        val output = tokenizer.decode(generated)
                                                                        check(output.isNotBlank()) { "Empty translation" }
                                                                        return output
                                                                    }
                                                                    check(next != 3L) { "Translation contains an unknown vocabulary item" }
                                                                    generated.add(next)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    error("Translation did not finish within the output limit")
                                                } finally { previous?.close() }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        cancel()
        resources.asReversed().forEach { runCatching { it.close() } }
        resources.clear()
    }
}
