package com.vitaltriage.app

import org.junit.Assert.*
import org.junit.Test

class MessagePresentationTest {
    @Test fun conversationShowsOnlyMatchingTargetAndRetainsSource() {
        val item=ConversationMessage("Ateşim yok",com.vitaltriage.core.Language.TR,false,
            "I have no fever",com.vitaltriage.core.Language.EN,provider="nllb")
        assertEquals("I have no fever",item.targetText(com.vitaltriage.core.Language.EN))
        assertEquals("Ateşim yok",item.text)
        assertNull(item.targetText(com.vitaltriage.core.Language.FR))
        item.translationError=true
        assertNull(item.targetText(com.vitaltriage.core.Language.EN))
        assertEquals("Ateşim yok",item.targetText(com.vitaltriage.core.Language.TR))
    }
    @Test fun translatedTargetDoesNotReplaceOriginalForAssessment() {
        val message=WalkieMessage(1,"Ağrım yok","tr","I have no pain",translationEngine="nllb",translationTarget="en")
        assertEquals("I have no pain",message.targetText())
        assertEquals("Ağrım yok",message.original)
    }
    @Test fun sameLanguageHasOneOriginalWithoutInventedTranslation() {
        assertEquals("Synthetic example",WalkieMessage(1,"Synthetic example","en",translationTarget="en").targetText())
    }
    @Test fun failedOrMissingTranslationNeverMasqueradesAsTargetText() {
        assertNull(WalkieMessage(1,"Ağrım yok","tr",translationTarget="en").targetText())
        assertNull(WalkieMessage(1,"Ağrım yok","tr","stale",translationFailed=true,translationTarget="en").targetText())
        assertNull(WalkieMessage(1,"Ağrım yok","tr","   ",translationTarget="en").targetText())
    }
    @Test fun historyKeepsIndependentTargetAndOriginalForEveryTurn() {
        val messages=boundedMessages(listOf(
            WalkieMessage(1,"Original français","fr","English target",translationTarget="en"),
            WalkieMessage(2,"English original","en","Türkçe hedef",translationTarget="tr")))
        assertEquals(listOf("English target","Türkçe hedef"),messages.map { it.targetText() })
        assertEquals(listOf("Original français","English original"),messages.map { it.original })
    }
}
