package com.vitaltriage.app

import android.os.Debug
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class WhisperCancellationTest {
    @Test fun realInferenceCancelRestartAndSampledMemory() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val prefs=UserPreferences(context)
        val previous=prefs.whisperModel
        prefs.setupComplete=true;prefs.nickname="Cancellation diagnostic";prefs.whisperModel=WhisperModel.MEDIUM
        try {
        // Keep the diagnostic foreground before binding TTS, as in WhisperEngineTest.
        ActivityScenario.launch(MainActivity::class.java).use {
            val audio=WhisperEngineTest.syntheticSpeech("en","This is a synthetic test. My throat hurts today.")
            val worker=Executors.newSingleThreadExecutor()
            val sampler=Executors.newSingleThreadScheduledExecutor()
            val peak=AtomicInteger()
            val engine=WhisperEngine(context)
            val report=linkedMapOf<String,Any>("paidCalls" to 0,"model" to "medium","physicalMicrophone" to false)
            try {
                sampler.scheduleAtFixedRate({
                    val info=Debug.MemoryInfo();Debug.getMemoryInfo(info)
                    peak.updateAndGet { maxOf(it,info.totalPss) }
                },0,1,TimeUnit.SECONDS)
                worker.submit {engine.begin();engine.load()}.get(60,TimeUnit.SECONDS)
                val warm=SystemClock.elapsedRealtime()
                worker.submit {engine.load()}.get(10,TimeUnit.SECONDS)
                report["warmLoadMs"]=SystemClock.elapsedRealtime()-warm
                for(mode in listOf("en","auto")) {
                    val started=CountDownLatch(1)
                    val pending=worker.submit<WhisperText?> {
                        engine.begin();started.countDown();engine.transcribe(audio,mode)
                    }
                    assertTrue(started.await(5,TimeUnit.SECONDS))
                    SystemClock.sleep(2000)
                    val cancelledAt=SystemClock.elapsedRealtime()
                    engine.cancel()
                    assertNull("Cancelled audio must not produce a usable transcript",pending.get(135,TimeUnit.SECONDS))
                    val releaseMs=SystemClock.elapsedRealtime()-cancelledAt
                    report[mode+"CancelReleaseMs"]=releaseMs
                    assertTrue("Native cancellation exceeded five seconds: $mode/$releaseMs",releaseMs<5000)
                }
                val restarted=worker.submit<WhisperText?> {engine.begin();engine.transcribe(audio,"en")}.get(135,TimeUnit.SECONDS)
                assertNotNull(restarted)
                assertTrue(restarted!!.text.lowercase().let {"throat" in it && "today" in it})
                report["restartExpectedWords"]=true
            } finally {
                engine.cancel()
                // Free only after the serial inference task has returned; never free an in-use JNI context.
                worker.submit {engine.close()}.get(150,TimeUnit.SECONDS)
                worker.shutdown();sampler.shutdownNow()
                report["sampledPeakPssKb"]=peak.get()
                report["memorySamplingPeriodMs"]=1000
                File(context.getExternalFilesDir(null),"whisper-cancellation.json").writeText(Contract.gson.toJson(report))
                audio.fill(0)
            }
        }
        } finally { prefs.whisperModel=previous }
    }
}
