package com.vitaltriage.app

import com.vitaltriage.core.Language
import org.junit.Test
import org.junit.Assert.*

class ConversationMemoryTest {
    @Test fun reconnectSamePeerPreservesButDifferentPeerSeparatesMessages() {
        val memory=ConversationMemoryViewModel()
        memory.bindPeer("one");memory.add(ConversationMessage("Synthetic first",Language.EN,false))
        memory.draft="Pending";memory.previousTranscript="Recognized"
        memory.bindPeer("one");assertEquals(1,memory.messages.size);assertEquals("Pending",memory.draft)
        memory.bindPeer("two");assertTrue(memory.messages.isEmpty());assertEquals("",memory.draft);assertEquals("",memory.previousTranscript)
    }
    @Test fun translatedCharactersAlsoCountTowardMemoryLimit() {
        val memory=ConversationMemoryViewModel()
        repeat(30){memory.add(ConversationMessage("a".repeat(4000),Language.EN,false,"b".repeat(4000)))}
        assertEquals(12,memory.messages.size)
        assertTrue(memory.messages.sumOf { it.text.length+it.translated!!.length }<=100000)
    }
    @Test fun manyShortMessagesKeepOrderedLatestHundred() {
        val memory=ConversationMemoryViewModel()
        repeat(150){memory.add(ConversationMessage("Synthetic "+it,Language.FR,it%2==0))}
        assertEquals(100,memory.messages.size);assertEquals("Synthetic 50",memory.messages.first().text)
        assertEquals("Synthetic 149",memory.messages.last().text)
    }
}
