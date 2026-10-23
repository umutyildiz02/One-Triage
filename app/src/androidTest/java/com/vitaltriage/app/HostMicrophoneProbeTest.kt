package com.vitaltriage.app

import android.Manifest
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.vitaltriage.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opt-in, bounded host speaker -> microphone -> emulator AudioRecord diagnostic.
 * Never part of default regression. No audio is saved; only fixed-phrase keyword/timing metrics.
 * Run with -e hostMicrophoneProbe true while emulator hostmicon is enabled; always disable afterwards.
 */
class HostMicrophoneProbeTest {
    @get:Rule val permission=GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    @Test fun permittedSyntheticPlaybackTraversesRealCaptureAndWhisper() {
        require(InstrumentationRegistry.getArguments().getString("hostMicrophoneProbe")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        require(context.packageName.endsWith(".uitest"))
        val prefs=UserPreferences(context)
        val oldModel=prefs.whisperModel;val oldSetup=prefs.setupComplete;val oldName=prefs.nickname
        prefs.whisperModel=WhisperModel.MEDIUM;prefs.setupComplete=true;prefs.nickname="Audio probe"
        val capture=SourceAudioCapture()
        var output:SpeechOutput?=null
        var samples:ShortArray?=null
        var error=""
        val delivered=CountDownLatch(1);val frames=CountDownLatch(1);val playback=CountDownLatch(1)
        val ttsFailed=AtomicBoolean(false)
        val report=linkedMapOf<String,Any>("paidCalls" to 0,"physicalAndroidDevice" to false,
            "source" to "explicit synthetic host-speaker/microphone diagnostic","audioSaved" to false)
        val foreground=ActivityScenario.launch(MainActivity::class.java)
        try {
            instrumentation.runOnMainSync {
                output=SpeechOutput(context,{if(it)playback.countDown()},{ttsFailed.set(true)})
            }
            val readyDeadline=SystemClock.elapsedRealtime()+15000
            var ready=false
            while(!ready && SystemClock.elapsedRealtime()<readyDeadline) {
                instrumentation.runOnMainSync {ready=output!!.isReady}
                if(!ready)SystemClock.sleep(100)
            }
            assertTrue("Offline TTS unavailable",ready)
            instrumentation.runOnMainSync {
                capture.start(CaptureSettings(),false,{frames.countDown()},{
                    samples=it;delivered.countDown()
                },{error=it;delivered.countDown();frames.countDown()})
            }
            assertTrue("No AudioRecord frames",frames.await(5,TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                output!!.toggle("This is a synthetic audio test. The blue bicycle is beside the window.",Language.EN)
            }
            assertTrue("Synthetic playback did not start",playback.await(5,TimeUnit.SECONDS))
            // Bounded acquisition only. No background/continuous listening.
            SystemClock.sleep(7000)
            instrumentation.runOnMainSync {capture.finish();output!!.stop()}
            assertTrue("Capture timeout",delivered.await(5,TimeUnit.SECONDS))
            assertEquals("",error);assertFalse(ttsFailed.get())
            val audio=samples ?: error("No captured PCM")
            report["samples"]=audio.size
            report["peakAbsolute"]=audio.maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0
            assertTrue(audio.isNotEmpty())
            val started=SystemClock.elapsedRealtime()
            val engine=WhisperEngine(context)
            val result=try {engine.begin();engine.transcribe(audio,"en")} finally {engine.close()}
            assertNotNull("No speech recognized from captured signal",result)
            val text=result!!.text.lowercase()
            report["inferenceMs"]=SystemClock.elapsedRealtime()-started
            report["language"]=result.language
            report["blue"]=text.contains("blue");report["bicycle"]=text.contains("bicycle");report["window"]=text.contains("window")
            assertTrue("Captured signal did not reproduce the permitted synthetic phrase",text.contains("blue") && text.contains("bicycle") && text.contains("window"))
        } finally {
            samples?.fill(0)
            instrumentation.runOnMainSync {capture.destroy();output?.close()}
            foreground.close()
            prefs.whisperModel=oldModel;prefs.setupComplete=oldSetup;prefs.nickname=oldName
            File(context.getExternalFilesDir(null),"host-microphone-probe.json").writeText(Contract.gson.toJson(report))
        }
    }
}
