package com.vitaltriage.app

import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import com.vitaltriage.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ExpandedSpeechTest {
    @Test fun installedVoiceCatalogIsIndependentFromTypedAndTranslationCapabilities() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=IsolatedTestState.context
        check(context.packageName.endsWith(".uitest"))
        val ready=CountDownLatch(1)
        var status=TextToSpeech.ERROR
        lateinit var tts:TextToSpeech
        instrumentation.runOnMainSync { tts=TextToSpeech(context){status=it;ready.countDown()} }
        try {
            assertTrue(ready.await(20,TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS,status)
            val rows=ArrayList<Map<String,Any>>()
            instrumentation.runOnMainSync {
                Language.entries.forEach { language->
                    val voices=tts.voices.orEmpty().filter { Language.matchesVoice(language,it.locale) }
                    val installed=voices.filter { !it.isNetworkConnectionRequired &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
                    rows.add(mapOf("language" to language.tag,"offlineInstalledVoices" to installed.size,
                        "normalQualityVoices" to installed.count { it.quality>=android.speech.tts.Voice.QUALITY_NORMAL },
                        "whisperMapping" to (language.whisperTag!=null),"nllbToken" to language.nllbId))
                    val vm=TriageViewModel()
                    vm.utterance("Synthetic typed original",language.tag,target=language.tag)
                    assertEquals(language.tag,vm.state.value.currentMessageLanguage)
                    assertNull(vm.state.value.speechError)
                }
            }
            File(context.getExternalFilesDir(null),"language-voice-catalog.json").writeText(Contract.gson.toJson(rows))
        } finally {instrumentation.runOnMainSync{tts.shutdown()}}
    }

    @Test fun germanAndJapaneseRealWaveformsUseManualAndAutomaticWhisper() {
        IsolatedTestState.reset(completed=true)
        val context=IsolatedTestState.context
        UserPreferences(context).whisperModel=WhisperModel.MEDIUM
        val rows=ArrayList<Map<String,Any>>()
        ActivityScenario.launch(MainActivity::class.java).use {
            val engine=WhisperEngine(context)
            try {
                listOf(
                    Triple("de","Dies ist ein künstlicher Test. Ich habe seit zwei Tagen Halsschmerzen. Ich habe kein Fieber.",listOf("hals","fieber")),
                    Triple("ja","これはテストです。二日前から喉が痛いです。熱はありません。",listOf("喉","熱"))
                ).forEach { (tag,phrase,words)->
                    val audio=WhisperEngineTest.syntheticSpeech(tag,phrase)
                    try {
                        listOf(tag,"auto").forEach { requested->
                            engine.begin()
                            val began=SystemClock.elapsedRealtime()
                            val result=engine.transcribe(audio,requested)
                            val matches=result!=null && words.all { it in result.text.lowercase() }
                            rows.add(mapOf("source" to tag,"requested" to requested,"detected" to (result?.language ?: ""),
                                "syntheticTranscript" to (result?.text ?: ""),"ms" to SystemClock.elapsedRealtime()-began,
                                "sentinelWords" to matches,"hardwareMicrophone" to false,"paidCalls" to 0))
                            assertEquals("Detected language mismatch",tag,result?.language)
                            assertTrue("Synthetic sentinel words missing for $tag/$requested",matches)
                        }
                    } finally { audio.fill(0) }
                }
            } finally {
                engine.close()
                File(context.getExternalFilesDir(null),"expanded-whisper.json").writeText(Contract.gson.toJson(rows))
            }
        }
    }
}
