package com.vitaltriage.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Language
import org.junit.*
import org.junit.Assert.*

/** Real French waveform reaches production controller, bilingual bubble and unapproved review. */
class VoiceTranslationFlowTest {
    private val rule=createAndroidComposeRule<MainActivity>()
    @get:Rule val chain:org.junit.rules.RuleChain=org.junit.rules.RuleChain.outerRule(CompletedSetupRule()).around(rule)
    @Test fun realFrenchAudioShowsOriginalAndOptionalEnglishBeforeConsent() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val prefs=UserPreferences(context)
        val selected=if(InstrumentationRegistry.getArguments().getString("qualityModel")=="medium-q5")WhisperModel.MEDIUM else WhisperModel.SMALL
        val raw=InstrumentationRegistry.getInstrumentation().context.assets.open("speech-fixtures/fr.pcm").use { it.readBytes() }
        val audio=ShortArray(raw.size/2) { i->((raw[2*i].toInt() and 255) or (raw[2*i+1].toInt() shl 8)).toShort() }
        raw.fill(0)
        lateinit var vm:TriageViewModel
        var controller:SpeechController?=null
        try {
            rule.runOnIdle {
                prefs.whisperModel=selected;prefs.offlineWhisper=true;prefs.englishVoiceTranslation=true
                vm=ViewModelProvider(rule.activity)[TriageViewModel::class.java]
                vm.toggleLanguage(Language.FR);vm.activeLanguage(Language.FR);vm.autoLanguage(false)
                controller=SpeechController(rule.activity,vm)
                controller!!.transcribePcm(audio)
            }
            rule.waitUntil(180000) { !vm.state.value.listening }
            val state=vm.state.value
            java.io.File(context.getExternalFilesDir(null),"whisper-translation-flow.json").writeText(com.vitaltriage.core.Contract.gson.toJson(mapOf(
                "dataset" to "google/fleurs","id" to 1570,"paidCalls" to 0,"microphone" to false,"model" to selected.key,
                "publicOriginal" to state.symptoms,"publicEnglishTranslation" to state.speechTranslation,
                "error" to state.speechError,"translationFailed" to state.translationFailed)))
            assertNull(state.speechError)
            assertEquals("fr",state.detectedLanguage)
            assertTrue(state.symptoms.lowercase().contains("lois"))
            assertNotNull(state.speechTranslation)
            assertTrue(state.speechTranslation!!.lowercase().let { "tax" in it || "fiscal" in it })
            assertFalse(state.translationFailed)
            assertFalse(state.confirmed)
            assertNull(state.evaluation)
            rule.onNodeWithTag("speech-translation").assertIsDisplayed()
            rule.onNodeWithTag("detected-language").assertIsDisplayed()
            IsolatedTestState.screenshot("15-real-french-bilingual-waveform")
            rule.onNodeWithTag("review").performClick()
            rule.onNodeWithTag("analyze").performScrollTo().assertIsNotEnabled()
            rule.onNode(hasTestTag("speech-translation") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
            assertEquals(state.symptoms,vm.state.value.symptoms)
            assertFalse(vm.state.value.confirmed)
            IsolatedTestState.screenshot("16-real-french-translation-review")
        } finally {
            audio.fill(0)
            rule.runOnIdle {
                controller?.destroy()
                prefs.englishVoiceTranslation=false;prefs.offlineWhisper=false;prefs.whisperModel=WhisperModel.SMALL
            }
        }
    }
}
