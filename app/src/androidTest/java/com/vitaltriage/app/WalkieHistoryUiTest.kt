package com.vitaltriage.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.*
import org.junit.Assert.*

/** UI/state evidence only; real Whisper recognition remains covered by native waveform tests. */
class WalkieHistoryUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Before fun before()=IsolatedTestState.reset(completed=true)
    @After fun after()=IsolatedTestState.finishActivities()
    @Test fun separateBubblesSelectExactlyOneOriginalAndStillRequireConsent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val vm=ViewModelProvider(activity)[TriageViewModel::class.java]
                vm.utterance("First synthetic irritation","en")
                vm.utterance("Deuxième exemple synthétique","fr","Second synthetic example",engine="nllb")
            }
            compose.onNodeWithTag("message-1").performScrollTo().assertIsDisplayed().performClick()
            scenario.onActivity { activity ->
                val state=ViewModelProvider(activity)[TriageViewModel::class.java].state.value
                assertEquals("First synthetic irritation",state.symptoms)
                assertFalse(state.confirmed);assertNull(state.evaluation)
                assertEquals("Deuxième exemple synthétique",state.messages.single().original)
            }
            compose.onNodeWithTag("review").performScrollTo().performClick()
            compose.onNodeWithTag("analyze").assertIsNotEnabled()
        }
    }
    @Test fun recreationRetainsSessionMessagesWithoutCombiningArabicAndFrench() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val vm=ViewModelProvider(activity)[TriageViewModel::class.java]
                vm.utterance("Exemple synthétique français","fr","French synthetic example",engine="nllb")
                vm.utterance("مثال اصطناعي للاختبار","ar","Synthetic test example",engine="nllb")
            }
            scenario.recreate()
            compose.onNodeWithTag("transcript").performScrollTo().assertIsDisplayed()
            scenario.onActivity { activity ->
                val state=ViewModelProvider(activity)[TriageViewModel::class.java].state.value
                assertEquals("مثال اصطناعي للاختبار",state.symptoms)
                assertEquals("ar",state.currentMessageLanguage)
                assertEquals("Exemple synthétique français",state.messages.single().original)
                assertFalse(state.confirmed)
            }
            IsolatedTestState.screenshot("product-walkie-message-history")
        }
    }
}
