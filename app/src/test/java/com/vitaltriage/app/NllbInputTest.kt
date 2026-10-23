package com.vitaltriage.app

import org.junit.Assert.*
import org.junit.Test

class NllbInputTest {
    @Test fun frenchNegationContractionUsesSupportedPunctuationWithoutChangingOriginal() {
        val original = "Je n’ai pas de fièvre et je n’ai pas mal."
        assertEquals("Je n'ai pas de fièvre et je n'ai pas mal.", NllbInput.forTokenizer(original))
        assertEquals("Je n’ai pas de fièvre et je n’ai pas mal.", original)
    }
    @Test fun quotesAndUnknownSymbolsArePreservedForFailClosedValidation() {
        val text = "‘fever’ / ’ / \u0378 / 🤒"
        assertEquals(text, NllbInput.forTokenizer(text))
    }
    @Test fun clinicalQualifiersAndFourLanguageTextAreNeverRewritten() {
        listOf(
            "No severe pain in my right arm for 2 hours.",
            "Sol kolum iki saattir çok ağrıyor; ateşim yok.",
            "Je n'ai pas de douleur à droite depuis deux heures.",
            "لا يوجد ألم شديد في الذراع اليسرى منذ ساعتين."
        ).forEach { assertEquals(it, NllbInput.forTokenizer(it)) }
    }
    @Test fun fourLanguageSentencesKeepOriginalOrderAndEveryCharacter() {
        mapOf(
            "en" to "I have a mild sore throat. No fever.",
            "tr" to "Boğazım ağrıyor. Ateşim yok.",
            "fr" to "J'ai mal à la gorge. Je n'ai pas de fièvre.",
            "ar" to "أعاني من ألم في الحلق. لا توجد حمى."
        ).forEach { (language, text) ->
            val parts = NllbInput.sentences(text, language)
            assertEquals(2, parts.size)
            assertEquals(text, parts.joinToString(""))
        }
    }
    @Test fun decimalAndWhitespaceAreNotDroppedOrRewritten() {
        val text = "  Pain for 1.5 hours. No fever.  "
        val parts = NllbInput.sentences(text, "en")
        assertEquals(2, parts.size)
        assertEquals(text, parts.joinToString(""))
        assertTrue(parts.first().contains("1.5"))
    }
    @Test fun sentenceBoundsRejectBeforeInferenceInsteadOfTruncating() {
        assertThrows(IllegalArgumentException::class.java) { NllbInput.sentences(" ", "en") }
        assertThrows(IllegalArgumentException::class.java) { NllbInput.sentences("A".repeat(4001), "en") }
        assertThrows(IllegalArgumentException::class.java) { NllbInput.sentences("No fever. ".repeat(17), "en") }
        assertEquals(16, NllbInput.sentences("No fever. ".repeat(16), "en").size)
    }
    @Test fun otherPunctuationWhitespaceAndLimitsRemainAvailableToValidator() {
        val text = "  dʼaccord — 2,5;\n" + "a".repeat(4001)
        assertEquals(text, NllbInput.forTokenizer(text))
    }
}
