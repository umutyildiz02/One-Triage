package com.vitaltriage.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.vitaltriage.core.Language
import org.junit.*
import org.junit.Assert.*

/** UI and preference evidence. Does not replace real NLLB inference diagnostics. */
class TranslationReviewUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Before fun before()=IsolatedTestState.reset(completed=true)
    @After fun after()=IsolatedTestState.finishActivities()
    @Test fun eachTargetPersistsIndependentlyAndArabicTranslationKeepsOriginal() {
        val prefs=UserPreferences(IsolatedTestState.context)
        prefs.language=Language.FR;prefs.activeSpeech=Language.TR
        for(target in Language.uiLanguages) {
            prefs.translationTarget=target
            assertEquals(target,UserPreferences(IsolatedTestState.context).translationTarget)
            assertEquals(Language.FR,prefs.language)
            assertEquals(Language.TR,prefs.activeSpeech)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val vm=ViewModelProvider(activity)[TriageViewModel::class.java]
                vm.utterance("Sentetik özgün metin","tr","نص اصطناعي مترجم",engine="nllb",target="ar")
            }
            compose.onNodeWithTag("speech-translation",useUnmergedTree=true).performScrollTo().assertTextEquals("نص اصطناعي مترجم")
            compose.onNodeWithText("Sentetik özgün metin",useUnmergedTree=true).assertDoesNotExist()
            scenario.onActivity { activity ->
                assertEquals("Sentetik özgün metin",ViewModelProvider(activity)[TriageViewModel::class.java].state.value.symptoms)
            }
            compose.onNodeWithTag("transcript").performClick()
            compose.onNodeWithTag("symptoms").assertTextContains("Sentetik özgün metin",substring=true)
            compose.onNodeWithContentDescription("Back").performClick()
            scenario.recreate()
            compose.onNodeWithTag("speech-translation",useUnmergedTree=true).performScrollTo().assertTextEquals("نص اصطناعي مترجم")
        }
    }
    @Test fun busyReviewCannotApproveAndCancelKeepsEditableOriginal() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val vm=ViewModelProvider(activity)[TriageViewModel::class.java]
                vm.text("Synthetic original text");vm.review();vm.translationStarted()
            }
            compose.onNodeWithTag("confirm").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("analyze").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("cancel-translation").performScrollTo().performClick()
            compose.onNodeWithTag("confirm").performScrollTo().assertIsEnabled().assertIsOff()
            scenario.onActivity { activity ->
                val state=ViewModelProvider(activity)[TriageViewModel::class.java].state.value
                assertEquals("Synthetic original text",state.symptoms)
                assertNull(state.evaluation);assertFalse(state.translationBusy)
            }
        }
    }
}
