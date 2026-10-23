package com.vitaltriage.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
class MainFlowTest {
    val rule=createAndroidComposeRule<MainActivity>()
    @get:Rule val chain:org.junit.rules.RuleChain=org.junit.rules.RuleChain.outerRule(CompletedSetupRule()).around(rule)
    @Test fun typedSymptomsReviewDemoResult(){
        rule.onNodeWithTag("write").performClick()
        rule.onNodeWithTag("symptoms").performTextInput("Synthetic mild irritation for two days")
        rule.onNodeWithTag("editor-review").performScrollTo().performClick()
        rule.onNodeWithTag("analyze").assertIsNotEnabled()
        rule.onNodeWithTag("confirm").performScrollTo().performClick()
        rule.onNodeWithTag("analyze").performScrollTo().performClick()
        rule.waitUntil(10000){rule.onAllNodesWithTag("risk-MEDIUM").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("risk-MEDIUM").assertIsDisplayed().performClick()
        rule.onNodeWithText("45 / 100").assertExists()
    }
    @Test fun turkishAndMultiplePatientLanguages(){
        rule.onNodeWithTag("settings").performClick()
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Personal language"))
            .perform(androidx.test.espresso.action.ViewActions.scrollTo(), androidx.test.espresso.action.ViewActions.click())
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Türkçe"))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click())
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.vv_patient_languages))
            .perform(androidx.test.espresso.action.ViewActions.scrollTo(), androidx.test.espresso.action.ViewActions.click())
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Türkçe"))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click())
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
            .perform(androidx.test.espresso.action.ViewActions.click())
        val prefs=UserPreferences(IsolatedTestState.context)
        org.junit.Assert.assertTrue(com.vitaltriage.core.Language.EN in prefs.patientLanguages)
        org.junit.Assert.assertTrue(com.vitaltriage.core.Language.TR in prefs.patientLanguages)
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5000) { rule.activity.hasWindowFocus() }
        rule.onNodeWithTag("language-picker").performClick()
        // The processing catalog now has real additional languages; scroll the adapter to Turkish.
        androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.equalTo("tr"))
            .inAdapterView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.list_view_dialog))
            .perform(androidx.test.espresso.action.ViewActions.click())
        org.junit.Assert.assertEquals(com.vitaltriage.core.Language.TR,prefs.activeSpeech)
        rule.onNodeWithTag("write").performClick()
        rule.onNodeWithText("Belirtilerinizi anlatın").assertIsDisplayed()
    }
}
