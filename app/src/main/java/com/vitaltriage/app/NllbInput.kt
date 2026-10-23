package com.vitaltriage.app

/**
 * Narrow tokenizer compatibility adaptation, not clinical rewriting.
 * Only U+2019 between letters is treated as the ASCII apostrophe used by this vocabulary.
 * Original text remains untouched in the message, review and assessment payload.
 * Quotes, unknown symbols, words, numbers and negations are not discarded or replaced.
 */
internal object NllbInput {
    private val wordApostrophe = Regex("(?<=\\p{L})\u2019(?=\\p{L})")
    fun forTokenizer(original: String): String = wordApostrophe.replace(original, "'")

    /**
     * Locale sentence boundaries adapted from RTranslator Translator.java 453–460.
     * Do not rejoin short sentences: the pinned greedy model omitted a complete second
     * negated sentence in the recorded EN→TR regression. This is not semantic validation.
     * Keep exact substrings (including whitespace), bounded input and no partial result.
     */
    fun sentences(original: String, language: String): List<String> {
        require(original.isNotBlank() && original.length <= 4000)
        val iterator = java.text.BreakIterator.getSentenceInstance(java.util.Locale.forLanguageTag(language))
        iterator.setText(original)
        val result = ArrayList<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != java.text.BreakIterator.DONE) {
            result.add(original.substring(start, end))
            start = end
            end = iterator.next()
        }
        check(result.joinToString("") == original) { "Incomplete sentence segmentation" }
        val nonBlank = result.filter { it.isNotBlank() }
        require(nonBlank.isNotEmpty() && nonBlank.size <= 16) { "Too many translation sentences; review smaller messages" }
        return nonBlank
    }
}
