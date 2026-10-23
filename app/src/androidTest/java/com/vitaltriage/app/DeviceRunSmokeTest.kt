package com.vitaltriage.app

import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicit non-destructive normal-package device-variant check. Never run the reset-based
 * isolated suite against the normal package. Uses already prepared private models, no download/API. */
class DeviceRunSmokeTest {
    @Test fun personalAssetsAndAtomicBudgetWorkOnAndroid() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="com.vitaltriage.app" && BuildConfig.BUILD_TYPE=="device")
        assertTrue(BuildConfig.DIRECT_OPENAI)
        assertEquals(100, com.vitaltriage.core.LiveBudget.MAX_CALLS)
        assertEquals(3.0, com.vitaltriage.core.LiveBudget.USD_LIMIT, 0.0)
        assertFalse(UserPreferences(context).demo)
        // Values stay in process memory; assertion diagnostics never include the key.
        val configured=context.assets.open("personal-openai-key.txt").bufferedReader().use { it.readText() }
        assertTrue("Personal key asset configuration",configured.matches(Regex("sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}")))
        val seed=context.assets.open("openai-budget-seed.json").bufferedReader().use { it.readText() }
        assertTrue(com.google.gson.JsonParser.parseString(seed).asJsonObject["attempts"].asJsonArray.size()>=17)
        val prompt=context.assets.open("triage-system.txt").bufferedReader().use { it.readText() }
        assertTrue(prompt.contains("strict JSON contract replaces the original two-line"))
        // Exercise Android FileChannel + atomic move in a synthetic scratch ledger, no network.
        val scratch=java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(),"budget-smoke-")
        try {
            val path=scratch.resolve("synthetic.json")
            val ledger=com.vitaltriage.core.LiveBudget(path)
            val id=ledger.reserve()
            ledger.record(id,"SYNTHETIC_TEST")
            com.vitaltriage.core.LiveBudget(path).reserve()
            val entries=com.google.gson.JsonParser.parseString(String(java.nio.file.Files.readAllBytes(path)))
                .asJsonObject["attempts"].asJsonArray
            assertEquals(2,entries.size())
            assertEquals(id,entries[0].asJsonObject["clientRequestId"].asString)
        } finally { scratch.toFile().deleteRecursively() }
    }
    @Test fun normalPhoneVariantRunsPreparedModelsWithoutChangingPreferences() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="com.vitaltriage.app")
        check(BuildConfig.BUILD_TYPE=="device" && !BuildConfig.LOCAL_DEVELOPMENT)
        assertTrue(BuildConfig.DEFAULT_ENDPOINT.isEmpty() || BuildConfig.DEFAULT_ENDPOINT.startsWith("https://"))
        val prefs=context.getSharedPreferences(UserPreferences.FILE,android.content.Context.MODE_PRIVATE)
        val before=prefs.all.toMap()
        val report=linkedMapOf<String,Any>("package" to context.packageName,"variant" to BuildConfig.BUILD_TYPE,
            "abi" to Build.SUPPORTED_ABIS.first(),"paidCalls" to 0,"hardwareMicrophone" to false,
            "source" to "installed offline TTS waveform","models" to "prepared app-private files")
        val activity=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        var audio:ShortArray?=null
        val whisper=WhisperEngine(context)
        try {
            val samples=WhisperEngineTest.syntheticSpeech("en","This is a synthetic test. My throat hurts today.")
            audio=samples
            val speechStart=SystemClock.elapsedRealtime()
            whisper.begin()
            val recognized=whisper.transcribe(samples,"en")
            report["whisperMs"]=SystemClock.elapsedRealtime()-speechStart
            assertNotNull(recognized)
            assertEquals("en",recognized!!.language)
            assertTrue(recognized.text.lowercase().contains("throat"))
            assertTrue(recognized.text.lowercase().contains("today"))
            report["recognizedCharacters"]=recognized.text.length
            whisper.close()
            val directory=NllbModels.directory(context)
            assertTrue("Prepared private NLLB models are required",NllbModels.verified(directory))
            val translationStart=SystemClock.elapsedRealtime()
            NllbEngine(directory,memoryPressure={SpeechMemory.isLow(context)}).use { engine ->
                val translated=engine.translate("I have no fever.","en","tr").lowercase(java.util.Locale.forLanguageTag("tr"))
                assertTrue(translated.contains("ateş") && (translated.contains("yok") || translated.contains("değil")))
                report["translatedCharacters"]=translated.length
            }
            report["nllbMs"]=SystemClock.elapsedRealtime()-translationStart
            assertTrue("Normal preferences changed",before==prefs.all)
            report["preferencesUnchanged"]=true
            report["passed"]=true
        } finally {
            audio?.fill(0);whisper.close();activity.close()
            File(context.getExternalFilesDir(null),"device-run-smoke.json").writeText(Contract.gson.toJson(report))
        }
    }
}
