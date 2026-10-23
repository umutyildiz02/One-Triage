package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual licensed French waveform, local native translation, no network or injected text. */
class WhisperTranslationTest {
    private var activity:androidx.test.core.app.ActivityScenario<MainActivity>?=null
    @org.junit.Before fun foreground() {
        IsolatedTestState.reset()
        UserPreferences(IsolatedTestState.context).apply { setupComplete=true;nickname="Translation test" }
        activity=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
    }
    @org.junit.After fun finish() { activity?.close();IsolatedTestState.finishActivities() }
    @Test fun frenchAudioToEnglishPreservesOriginalRecognition() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val raw=instrumentation.context.assets.open("speech-fixtures/fr.pcm").use { it.readBytes() }
        val pcm=ShortArray(raw.size/2) { i->((raw[i*2].toInt() and 255) or (raw[i*2+1].toInt() shl 8)).toShort() }
        raw.fill(0)
        val engine=WhisperTestEngine(context)
        val beam=InstrumentationRegistry.getArguments().getString("whisperBeam")=="true"
        val report=linkedMapOf<String,Any>("dataset" to "google/fleurs", "id" to 1570,"paidCalls" to 0,"microphone" to false,"model" to engine.model,"beamSearch" to beam)
        try {
            engine.begin()
            val original=engine.transcribe(pcm,"fr",beamSearch=beam)
            assertNotNull(original)
            assertEquals("fr",original!!.language)
            val originalText=original.text
            engine.begin()
            val start=SystemClock.elapsedRealtime()
            val translated=engine.transcribe(pcm,"fr",translate=true,beamSearch=beam)
            report["translationMs"]=SystemClock.elapsedRealtime()-start
            assertNotNull(translated)
            val english=translated!!.text.lowercase()
            report["originalCharacters"]=originalText.length
            report["translatedCharacters"]=english.length
            report["fiscalMeaning"]= "tax" in english || "fiscal" in english
            report["customsMeaning"]="custom" in english || "duties" in english || "duty" in english
            report["distinctFromOriginal"]=translated.text!=originalText
            report["negativeCoordination"]=Regex("\\b(nor|or)\\b").containsMatchIn(english)
            if(InstrumentationRegistry.getArguments().getString("publicFixtureText")=="true") {
                report["publicOriginal"]=originalText
                report["publicTranslation"]=translated.text
            }
            assertTrue("English fiscal meaning missing",report["fiscalMeaning"]==true)
            assertTrue("English customs meaning missing",report["customsMeaning"]==true)
            assertTrue("Translation lost the second negative clause",report["negativeCoordination"]==true)
            assertNotEquals(originalText,translated.text)
            assertEquals(originalText,original.text)
        } finally {
            pcm.fill(0);engine.close()
            File(context.getExternalFilesDir(null),"whisper-translation-fr${engine.suffix}.json").writeText(Contract.gson.toJson(report))
        }
    }
}
