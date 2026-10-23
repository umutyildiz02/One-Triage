package com.vitaltriage.app

import com.vitaltriage.core.Language
import org.junit.Assert.*
import org.junit.Test

class WalkieTurnLoopTest {
    private class Harness {
        val events=mutableListOf<String>()
        val scheduled=ArrayDeque<()->Unit>()
        var completed:((Boolean)->Unit)?=null
        val loop=WalkieTurnLoop({events+="capture"},{events+="capture-stopped"},
            {_,language,done->events+="play-"+language.tag;completed=done},
            {events+="play-stopped"},{events+="automatic-"+it},{scheduled.add(it)})
    }
    @Test fun automaticCaptureWaitsForTtsCompletionAndAcousticTail() {
        val h=Harness();h.loop.start(true)
        h.loop.completed("Synthetic translation",Language.FR,true)
        assertEquals(1,h.events.count{it=="capture"})
        assertTrue(h.events.indexOfLast{it=="capture-stopped"}<h.events.indexOf("play-fr"))
        assertTrue(h.scheduled.isEmpty())
        h.completed!!(true)
        assertEquals(1,h.events.count{it=="capture"})
        h.scheduled.removeFirst().invoke()
        assertEquals(2,h.events.count{it=="capture"})
    }
    @Test fun manualCaptureDoesNotRestartAfterReading() {
        val h=Harness();h.loop.start(false);h.loop.completed("Synthetic",Language.EN,true)
        h.completed!!(true)
        assertFalse(h.loop.active);assertTrue(h.scheduled.isEmpty())
        assertEquals(1,h.events.count{it=="capture"})
    }
    @Test fun stopRejectsLatePlaybackAndQueuedRestart() {
        val h=Harness();h.loop.start(true);h.loop.completed("Synthetic",Language.TR,true)
        val old=h.completed!!
        h.loop.stop();old(true)
        assertTrue(h.scheduled.isEmpty())
        h.loop.start(true);h.loop.completed("",Language.TR,false)
        h.loop.stop();h.scheduled.removeFirst().invoke()
        assertEquals(2,h.events.count{it=="capture"})
    }
    @Test fun failedPlaybackStopsInsteadOfFeedingBackOrRetrying() {
        val h=Harness();h.loop.start(true);h.loop.completed("Synthetic",Language.AR,true)
        h.completed!!(false)
        assertFalse(h.loop.active);assertTrue(h.scheduled.isEmpty())
        assertEquals(1,h.events.count{it=="capture"})
    }
    @Test fun oldSessionCallbackCannotRestartNewSession() {
        val h=Harness();h.loop.start(true);h.loop.completed("Old",Language.EN,true)
        val old=h.completed!!
        h.loop.start(false);old(true)
        assertTrue(h.scheduled.isEmpty());assertTrue(h.loop.active)
        assertEquals(2,h.events.count{it=="capture"})
    }
}
