package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import org.junit.Before
import org.junit.After
import com.vitaltriage.core.Language
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Actual installed offline TTS playback callbacks, not merely opening its settings. */
class SpeechOutputTest {
    private var activity:ActivityScenario<MainActivity>?=null
    @Before fun foregroundApp() {
        IsolatedTestState.reset()
        UserPreferences(IsolatedTestState.context).apply { setupComplete=true;nickname="Playback test" }
        activity=ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun closeActivity() { activity?.close();IsolatedTestState.finishActivities() }
    @Test fun installedEnglishPlaybackStopsAndCanRestart()=verify(Language.EN)
    @Test fun installedTurkishPlaybackStopsAndCanRestart()=verify(Language.TR)
    @Test fun installedFrenchPlaybackStopsAndCanRestart()=verify(Language.FR)
    @Test fun installedArabicPlaybackStopsAndCanRestart()=verify(Language.AR)
    @Test fun naturalCompletionIsDistinctFromExplicitStop() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val done=CountDownLatch(1);val cancelled=java.util.concurrent.atomic.AtomicInteger()
        var failed=false
        lateinit var output:SpeechOutput
        instrumentation.runOnMainSync {output=SpeechOutput(instrumentation.targetContext,{}, {failed=true})}
        try {
            val deadline=SystemClock.elapsedRealtime()+20000
            var ready=false
            while(!ready && SystemClock.elapsedRealtime()<deadline) {
                instrumentation.runOnMainSync {ready=output.isReady}
                if(!ready)SystemClock.sleep(100)
            }
            assertTrue(ready)
            instrumentation.runOnMainSync {
                output.play("Synthetic cancelled playback.",Language.EN){cancelled.incrementAndGet()}
                output.stop()
                output.play("Synthetic test complete.",Language.EN){if(it)done.countDown()}
            }
            assertTrue("Actual onDone did not arrive",done.await(20,TimeUnit.SECONDS))
            assertEquals("Stopped utterance must not resume capture",0,cancelled.get())
            assertFalse(failed)
        } finally {instrumentation.runOnMainSync {output.close()}}
    }
    private fun verify(language:Language) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName.endsWith(".uitest"))
        val started=CountDownLatch(1)
        val restarted=CountDownLatch(1)
        val second=AtomicBoolean(false)
        val speaking=AtomicBoolean(false)
        val failed=AtomicBoolean(false)
        lateinit var output:SpeechOutput
        instrumentation.runOnMainSync {
            output=SpeechOutput(instrumentation.targetContext,{ value->
                speaking.set(value)
                if(value) { if(second.get())restarted.countDown() else started.countDown() }
            },{failed.set(true)})
        }
        try {
            val deadline=SystemClock.elapsedRealtime()+20000
            var ready=false
            while(!ready && SystemClock.elapsedRealtime()<deadline) {
                instrumentation.runOnMainSync { ready=output.isReady }
                if(!ready)SystemClock.sleep(100)
            }
            assertTrue("Installed TTS initialization failed",ready)
            val phrase=when(language) {
                Language.EN->"This is a synthetic playback test. No medical advice is being given."
                Language.TR->"Bu sentetik bir sesli okuma testidir. Tıbbi öneri verilmemektedir."
                Language.FR->"Ceci est un test de lecture synthétique. Aucun conseil médical n’est donné."
                Language.AR->"هذا اختبار اصطناعي للقراءة الصوتية. لا يتم تقديم نصيحة طبية."
                else->error("No synthetic playback fixture for this language")
            }
            instrumentation.runOnMainSync { output.toggle(phrase,language) }
            val didStart=started.await(15,TimeUnit.SECONDS)
            assertTrue("Actual playback did not start; safe error callback="+failed.get(),didStart)
            instrumentation.runOnMainSync { output.stop() }
            SystemClock.sleep(500)
            assertFalse("Late callback revived stopped playback",speaking.get())
            second.set(true)
            instrumentation.runOnMainSync { output.toggle(phrase,language) }
            assertTrue("Playback did not restart",restarted.await(15,TimeUnit.SECONDS))
            assertFalse("Playback reported an error",failed.get())
        } finally {
            instrumentation.runOnMainSync { output.close() }
        }
        SystemClock.sleep(500)
        assertFalse(speaking.get())
    }
}
