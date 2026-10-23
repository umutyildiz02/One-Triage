package com.vitaltriage.app

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Same Medium weights and real waveform; no API or injected transcript. */
class WhisperPerformanceTest {
    @Test fun compareThreadsWithoutChangingModelOrDecoder() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val prefs=UserPreferences(context)
        prefs.setupComplete=true;prefs.nickname="Performance diagnostic"
        val report=linkedMapOf<String,Any>("paidCalls" to 0,"model" to WhisperModel.MEDIUM.key,
            "cores" to Runtime.getRuntime().availableProcessors(),"physicalMicrophone" to false)
        val runs=mutableListOf<Map<String,Any>>()
        report["runs"]=runs
        val file=File(context.getExternalFilesDir(null),"whisper-performance.json")
        fun save(){file.writeText(Contract.gson.toJson(report))}
        val audio=WhisperEngineTest.syntheticSpeech("en","This is a synthetic test. My throat hurts today.")
        var handle=0L
        val handler=Handler(Looper.getMainLooper())
        val lastTick=AtomicLong(SystemClock.elapsedRealtime())
        val maxGap=AtomicLong()
        val heartbeat=object:Runnable {
            override fun run() {
                val now=SystemClock.elapsedRealtime()
                val gap=now-lastTick.getAndSet(now)
                maxGap.updateAndGet { maxOf(it,gap) }
                handler.postDelayed(this,50)
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                val prepare=SystemClock.elapsedRealtime()
                val model=WhisperEngine.prepareModel(context,WhisperModel.MEDIUM)
                report["prepareMs"]=SystemClock.elapsedRealtime()-prepare
                val load=SystemClock.elapsedRealtime()
                handle=WhisperNative.load(model.absolutePath,context.applicationInfo.nativeLibraryDir)
                report["loadMs"]=SystemClock.elapsedRealtime()-load
                assertNotEquals(0L,handle)
                report["cpuFeatures"]=WhisperNative.systemInfo()
                var baseline:String?=null
                handler.post(heartbeat)
                for((threads,language) in listOf(2 to "en",4 to "en",2 to "en",4 to "en",2 to "auto",4 to "auto")) {
                    maxGap.set(0);lastTick.set(SystemClock.elapsedRealtime())
                    WhisperNative.prepare(handle)
                    val began=SystemClock.elapsedRealtime();val cpu=Process.getElapsedCpuTime()
                    val bytes=WhisperNative.run(handle,audio,language,false,false,threads)
                    val elapsed=SystemClock.elapsedRealtime()-began
                    val result=bytes?.toString(Charsets.UTF_8).orEmpty();bytes?.fill(0)
                    val text=result.substringAfter('\n',"").trim().lowercase()
                    if(baseline==null)baseline=text
                    val info=Debug.MemoryInfo();Debug.getMemoryInfo(info)
                    runs+=mapOf("threads" to threads,"language" to language,"elapsedMs" to elapsed,
                        "cpuMs" to Process.getElapsedCpuTime()-cpu,"pssKb" to info.totalPss,
                        "nativeHeapBytes" to Debug.getNativeHeapAllocatedSize(),"maxUiHeartbeatGapMs" to maxGap.get(),
                        "detected" to result.substringBefore('\n',""),
                        "sameTextAsBaseline" to (text==baseline),
                        "expectedWords" to ("throat" in text && "today" in text))
                    save()
                    assertTrue("Expected words missing; performance must not replace quality","throat" in text && "today" in text)
                    assertEquals("en",result.substringBefore('\n',""))
                    assertEquals("Thread experiment changed transcript",baseline,text)
                }
            } finally {
                handler.removeCallbacks(heartbeat)
                if(handle!=0L)WhisperNative.free(handle)
                audio.fill(0);save()
            }
        }
    }
}
