package com.vitaltriage.app

import com.vitaltriage.core.Language
import org.junit.Assert.*
import org.junit.Test

class ConversationProtocolTest {
    private fun rejects(action:()->Unit){assertTrue(runCatching(action).isFailure)}
    @Test fun fourLanguageUnicodeSurvivesRealByteFragmentation() {
        val samples=mapOf(Language.EN to "No chest pain.",Language.TR to "Göğsümde ağrı yok.",Language.FR to "Je n'ai pas de fièvre.",Language.AR to "لا توجد حمى ولا ألم في الصدر.")
        for((language,text)in samples) {
            val receiver=ConversationProtocol.Receiver()
            var output:ByteArray?=null
            for(frame in ConversationProtocol.frames(17,ConversationProtocol.text(text,language)))output=receiver.accept(frame)
            val message=ConversationProtocol.parseText(output!!)
            assertEquals(text,message.original);assertEquals(language,message.language)
        }
    }
    @Test fun gapsReplaysAndMixedMessagesFailClosed() {
        val frames=ConversationProtocol.frames(1,ConversationProtocol.text("Long repeated test ".repeat(10),Language.EN))
        val r=ConversationProtocol.Receiver()
        rejects{r.accept(frames[1])}
        r.accept(frames[0]);rejects{r.accept(frames[2])}
        var result:ByteArray?=null
        frames.forEach{result=r.accept(it)}
        assertNotNull(result)
        rejects{r.accept(frames[0])}
        r.clear();r.accept(frames[0])
        rejects{r.accept(ConversationProtocol.frames(2,byteArrayOf(1))[0])}
    }
    @Test fun truncatedInvalidUtf8AndUnsupportedLanguagesAreRejected() {
        rejects{ConversationProtocol.Receiver().accept(byteArrayOf(1,2))}
        rejects{ConversationProtocol.parseText(byteArrayOf(109,-61,40,101,110,50))}
        rejects{ConversationProtocol.parseText("mhellozz2".toByteArray())}
        rejects{ConversationProtocol.text(" ",Language.EN)}
        rejects{ConversationProtocol.text("x".repeat(4001),Language.EN)}
    }
    @Test fun incompleteMessagesExpireAndMemoryIsBounded() {
        var now=0L
        val receiver=ConversationProtocol.Receiver{now}
        val frames=ConversationProtocol.frames(4,ByteArray(200){97})
        receiver.accept(frames[0]);now=30_001
        rejects{receiver.accept(frames[1])}
        rejects{ConversationProtocol.frames(1,ByteArray(ConversationProtocol.MAX_BYTES+1))}
    }
    @Test fun bondingApprovalExactRecipientAndSingleUseConsentAreRequired() {
        val session=ConversationSession()
        rejects{session.request("peer",false)}
        session.request("peer",true)
        rejects{session.send("peer","test",Language.EN)}
        session.accept("peer")
        rejects{session.send("peer","test",Language.EN)}
        session.approve("peer","test",Language.EN)
        rejects{session.send("other","test",Language.EN)}
        rejects{session.send("peer","changed",Language.EN)}
        assertEquals("test",ConversationProtocol.parseText(session.send("peer","test",Language.EN)).original)
        rejects{session.send("peer","test",Language.EN)}
    }
    @Test fun disconnectDiscardsApprovalAndNeverQueuesAutomaticResend() {
        val s=ConversationSession()
        s.request("p",true);s.accept("p");s.approve("p","test",Language.EN);s.disconnect()
        s.request("p",true);s.accept("p")
        rejects{s.send("p","test",Language.EN)}
        assertEquals(ConversationSession.Phase.CONNECTED,s.phase)
    }
}
