package com.vitaltriage.app

import java.io.Closeable
import java.io.File

/**
 * RTranslator Tokenizer's NLLB fairseq offset and language-prefix convention.
 * SentencePiece itself is Apache-2.0. Model vocabulary licensing is separate.
 * Native strings cross JNI as UTF-8 bytes, not JNI modified UTF-8.
 */
internal class NllbTokenizer(model: File) : Closeable {
    private var handle = nativeOpen(model.absolutePath.toByteArray(Charsets.UTF_8))
    init { check(handle != 0L) { "Tokenizer model could not be loaded" } }
    fun encode(text: String, language: String): LongArray {
        check(handle != 0L)
        val pieces = nativeEncode(handle, text.toByteArray(Charsets.UTF_8))
        return longArrayOf(languageId(language)) + pieces.map {
            when (it) { 0 -> 3L; 1 -> 0L; 2 -> 2L; else -> it.toLong() + 1 }
        }.toLongArray() + longArrayOf(2L)
    }
    fun decode(tokens: List<Long>): String {
        check(handle != 0L)
        return String(nativeDecode(handle, tokens.filter { it in 3L..256000L }
            .map { if(it==3L)0 else (it - 1).toInt() }.toIntArray()), Charsets.UTF_8)
    }
    override fun close() { if (handle != 0L) { nativeClose(handle); handle = 0 } }
    private external fun nativeOpen(path: ByteArray): Long
    private external fun nativeEncode(handle: Long, text: ByteArray): IntArray
    private external fun nativeDecode(handle: Long, ids: IntArray): ByteArray
    private external fun nativeClose(handle: Long)
    companion object {
        init { System.loadLibrary("vt-sentencepiece") }
        // Positions verified against the original Tokenizer's FLORES-200 ordering.
        fun languageId(language: String): Long = com.vitaltriage.core.Language.from(language).nllbId
    }
}
