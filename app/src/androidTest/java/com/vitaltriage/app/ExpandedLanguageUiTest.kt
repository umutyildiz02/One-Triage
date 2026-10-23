package com.vitaltriage.app

import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Language
import org.junit.*
import org.junit.Assert.*

/** Typed synthetic original -> real NLLB -> one target bubble. No paid assessment. */
class ExpandedLanguageUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Before fun before()=IsolatedTestState.reset(completed=true)
    @After fun after()=IsolatedTestState.finishActivities()
    @Test fun germanOriginalEnglishBubbleTurkishInterfaceJapaneseResultRemainSeparate() {
        val context=IsolatedTestState.context
        UserPreferences(context).apply {
            language=Language.TR;resultLanguage=Language.JA
            activeSpeech=Language.DE;translationTarget=Language.EN
            englishVoiceTranslation=true;translationEngine="nllb"
        }
        val original="Ich habe seit zwei Tagen Halsschmerzen. Ich habe kein Fieber."
        var controller:SpeechController?=null
        lateinit var vm:TriageViewModel
        ActivityScenario.launch(MainActivity::class.java).use { scenario->
            try {
                scenario.onActivity { activity->
                    vm=ViewModelProvider(activity)[TriageViewModel::class.java]
                    controller=SpeechController(activity,vm,translationModelDirectory={context.getExternalFilesDir("nllb")!!})
                    vm.text(original)
                    controller!!.reviewWithTranslation()
                }
                val deadline=SystemClock.elapsedRealtime()+90000
                while(vm.state.value.translationBusy && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
                assertFalse(vm.state.value.translationBusy)
                assertFalse(vm.state.value.translationFailed)
                val translated=vm.state.value.speechTranslation!!
                assertTrue(translated.lowercase().contains("fever"))
                assertEquals(original,vm.state.value.symptoms)
                assertFalse(vm.state.value.confirmed);assertNull(vm.state.value.evaluation)
                compose.onNodeWithTag("analyze").assertIsNotEnabled()
                IsolatedTestState.screenshot("expanded-review-de-en-tr-ja")
                scenario.onActivity { vm.navigate(Screen.INPUT) }
                compose.onNodeWithText(translated).assertExists()
                compose.onNodeWithText(original).assertDoesNotExist()
                compose.onNodeWithTag("analyze").assertDoesNotExist()
                // Android dialog exit animation outlives Compose semantics; capture only after it settles.
                SystemClock.sleep(500)
                IsolatedTestState.screenshot("expanded-single-target-de-en")
                assertEquals(Language.TR,vm.state.value.language)
                assertEquals(Language.JA,vm.state.value.resultLanguage)
                scenario.recreate()
                scenario.onActivity { activity->
                    val resumed=ViewModelProvider(activity)[TriageViewModel::class.java].state.value
                    assertEquals(Language.TR,resumed.language);assertEquals(Language.JA,resumed.resultLanguage)
                    assertEquals(Language.DE,resumed.activeSpeech)
                }
            } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync {controller?.destroy()}}
        }
    }
}
