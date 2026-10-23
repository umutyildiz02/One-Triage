package com.vitaltriage.app

import android.Manifest
import android.media.AudioFormat
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.vitaltriage.core.Contract
import com.vitaltriage.core.Language
import org.junit.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Device-dependent diagnostic: actual synthesized PCM -> actual provider, never transcript injection. */
class SpeechEngineProbeTest {
    private lateinit var tts:TextToSpeech
    private lateinit var controller:SpeechController
    @get:Rule val permission=GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    @Test fun syntheticAudioThroughRealSpeechProvider() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".uitest")) { "Probe must use isolated test package" }
        val prefs=UserPreferences(context)
        prefs.nickname="Speech probe";prefs.setupComplete=true;prefs.demo=true
        val report=linkedMapOf<String,Any>("syntheticAudio" to true,"paidRequests" to 0)
        val ready=CountDownLatch(1);val synthesized=CountDownLatch(1)
        val pcm=ByteArrayOutputStream()
        var rate=0;var encoding=0;var channels=0;var completed=false
        val vm=TriageViewModel()
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            instrumentation.runOnMainSync {
                tts=TextToSpeech(context) { status -> report["ttsInit"]=status;ready.countDown() }
            }
            Assert.assertTrue("TTS initialization timed out",ready.await(20,TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                val voices=tts.voices.orEmpty().filter { !it.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
                report["offlineVoiceLanguages"]=voices.map { it.locale.language }.filter { it in listOf("en","tr","fr","ar") }.distinct().sorted()
                val voice=voices.filter { it.locale.language=="en" }.maxByOrNull { it.quality }
                Assert.assertNotNull("No installed offline English TTS voice",voice)
                tts.voice=voice
                tts.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
                    override fun onStart(id:String?) {}
                    override fun onBeginSynthesis(id:String?,sampleRateInHz:Int,audioFormat:Int,channelCount:Int) {
                        rate=sampleRateInHz;encoding=audioFormat;channels=channelCount
                    }
                    override fun onAudioAvailable(id:String?,audio:ByteArray?) {
                        if(audio!=null && pcm.size()+audio.size<=3_000_000) pcm.write(audio)
                    }
                    override fun onDone(id:String?) { completed=true;synthesized.countDown() }
                    @Deprecated("Framework callback") override fun onError(id:String?) { synthesized.countDown() }
                })
                tts.synthesizeToFile("This is a synthetic test. My throat hurts today.",Bundle(),java.io.File(context.cacheDir,"synthetic-probe.wav"),"probe")
            }
            Assert.assertTrue("TTS synthesis timed out",synthesized.await(30,TimeUnit.SECONDS))
            report["ttsCompleted"]=completed;report["pcmBytes"]=pcm.size();report["sampleRate"]=rate
            report["encoding"]=encoding;report["channels"]=channels
            Assert.assertTrue("TTS did not generate real audio",completed && pcm.size()>1000)
            Assert.assertEquals(AudioFormat.ENCODING_PCM_16BIT,encoding);Assert.assertEquals(1,channels)
            // Normalize genuine PCM to the reference recorder's 16 kHz format.
            // This changes audio samples only; no recognition result is injected.
            val source=pcm.toByteArray()
            val sourceSamples=source.size/2
            val targetRate=16000
            val targetSamples=(sourceSamples.toLong()*targetRate/rate).toInt()
            val normalized=ByteArray(targetSamples*2)
            fun sample(index:Int):Int {
                val offset=index.coerceIn(0,sourceSamples-1)*2
                return ((source[offset].toInt() and 255) or (source[offset+1].toInt() shl 8)).toShort().toInt()
            }
            for(i in 0 until targetSamples) {
                val position=i.toDouble()*rate/targetRate
                val left=position.toInt()
                val fraction=position-left
                val value=(sample(left)*(1-fraction)+sample(left+1)*fraction).toInt().coerceIn(-32768,32767)
                normalized[i*2]=value.toByte();normalized[i*2+1]=(value shr 8).toByte()
            }
            val segment=VoiceSegmenter(CaptureSettings(),true)
            var offset=0
            while(offset<targetSamples && !segment.finished) {
                val count=minOf(VoiceSegmenter.FRAME,targetSamples-offset)
                val frame=ShortArray(count) { index->
                    val p=(offset+index)*2
                    ((normalized[p].toInt() and 255) or (normalized[p+1].toInt() shl 8)).toShort()
                }
                segment.accept(frame);frame.fill(0);offset+=count
            }
            repeat(65){if(!segment.finished)segment.accept(ShortArray(VoiceSegmenter.FRAME))}
            val utterance=segment.finish()
            report["sourceVadDetectedSpeech"]=segment.hasVoice
            report["sourceVadSamples"]=utterance.size
            Assert.assertTrue("Real synthesized speech not detected by source VAD",segment.hasVoice && utterance.isNotEmpty())
            val segmented=ByteArray(utterance.size*2)
            for(i in utterance.indices){segmented[i*2]=utterance[i].toByte();segmented[i*2+1]=(utterance[i].toInt() shr 8).toByte()}
            utterance.fill(0);segment.clear();normalized.fill(0);source.fill(0)
            report["recognizerSampleRate"]=targetRate
            report["recognizerPcmBytes"]=segmented.size
            val pipe=ParcelFileDescriptor.createPipe()
            instrumentation.runOnMainSync {
                vm.activeLanguage(Language.EN);vm.autoLanguage(false)
                controller=SpeechController(context,vm)
                controller.start(pipe[0],targetRate)
            }
            val writer=Thread {
                runCatching { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(segmented) } }
            }.apply { isDaemon=true;start() }
            val deadline=SystemClock.elapsedRealtime()+35000
            while(SystemClock.elapsedRealtime()<deadline && vm.state.value.listening) SystemClock.sleep(100)
            val result=vm.state.value
            report["speechError"]=result.speechError ?: ""
            report["recognizedCharacters"]=result.symptoms.length
            report["recognizedExpectedWords"]=result.symptoms.lowercase().let { "throat" in it || "synthetic" in it }
            pipe[0].close();runCatching { pipe[1].close() };writer.join(1000);segmented.fill(0)
            Assert.assertTrue("Real STT failed: "+result.speechError,report["recognizedExpectedWords"]==true)
            Assert.assertFalse("Recognition must not approve analysis",result.confirmed)
            Assert.assertNull(result.evaluation)
        } finally {
            instrumentation.runOnMainSync {
                if(::controller.isInitialized)controller.destroy()
                if(::tts.isInitialized)tts.shutdown()
            }
            java.io.File(context.getExternalFilesDir(null),"speech-probe.json").writeText(Contract.gson.toJson(report))
            java.io.File(context.cacheDir,"synthetic-probe.wav").delete()
            scenario.close()
        }
    }
}
