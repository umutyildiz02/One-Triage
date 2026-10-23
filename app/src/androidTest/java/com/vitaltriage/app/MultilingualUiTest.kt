package com.vitaltriage.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.vitaltriage.core.Language
import org.junit.Rule
import org.junit.Test

class MultilingualUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun fourLanguageInputConsentAndExplicitDemoResults() {
        val examples=mapOf(Language.EN to "Synthetic adult with a sore throat for one day.",
            Language.TR to "Sentetik yetişkin vakası: bir gündür boğaz ağrısı.",
            Language.FR to "Cas fictif : adulte avec mal de gorge depuis un jour.",
            Language.AR to "حالة اصطناعية: شخص بالغ يعاني من ألم في الحلق منذ يوم.")
        Language.uiLanguages.forEach { language ->
            IsolatedTestState.reset()
            val prefs=UserPreferences(IsolatedTestState.context)
            prefs.nickname="Language test";prefs.setupComplete=true;prefs.language=language;prefs.demo=true
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.onNodeWithTag("write").performClick()
                compose.onNodeWithTag("symptoms").performTextInput(examples.getValue(language))
                compose.onNodeWithTag("editor-review").performScrollTo().performClick()
                compose.onNodeWithTag("analyze").assertIsNotEnabled()
                compose.onNodeWithTag("confirm").performScrollTo().performClick()
                compose.onNodeWithTag("analyze").performScrollTo().performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithTag("risk-MEDIUM").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("result-urgency",useUnmergedTree=true).assertIsDisplayed()
                    .assertTextContains(UiCopy.text("summaryMEDIUM",language),substring=true)
                compose.onNodeWithTag("result-summary").assertTextContains("DEMO · ",substring=true)
                IsolatedTestState.screenshot("integration-"+language.tag+"-explicit-demo")
            }
            IsolatedTestState.finishActivities()
        }
    }
}
