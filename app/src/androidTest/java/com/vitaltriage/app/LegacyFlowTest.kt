package com.vitaltriage.app

import android.content.Context
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Language
import org.hamcrest.Matchers.not
import org.junit.*

class LegacyFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Before fun before() = IsolatedTestState.reset()
    @After fun after() = IsolatedTestState.finishActivities()
    private val prefs get() = UserPreferences(IsolatedTestState.context)
    private fun awaitFocusedWindowWithoutIme() {
        if(android.os.Build.VERSION.SDK_INT>=30) compose.waitUntil(10000) {
            var hidden=false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val focused=android.view.inspector.WindowInspector.getGlobalWindowViews().firstOrNull { it.hasWindowFocus() }
                hidden=focused?.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==false
            }
            hidden
        }
    }
    private fun completeProfile() {
        onView(withId(R.id.input_name)).perform(replaceText("Synthetic User"), closeSoftKeyboard())
        onView(withId(R.id.checkBoxPrivacy)).perform(scrollTo(), click())
        onView(withId(R.id.buttonConfirm)).perform(scrollTo(), click())
    }
    private fun home() {
        prefs.nickname = "Synthetic User"; prefs.setupComplete = true
        ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("walkie-home").assertIsDisplayed()
    }

    @Test fun firstLaunchValidationBackAndCompletion() {
        ActivityScenario.launch(LegacyLoadingActivity::class.java)
        onView(withText("Notice")).check(matches(isDisplayed()))
        IsolatedTestState.screenshot("01-notice")
        onView(withId(R.id.buttonConfirm)).perform(scrollTo(), click())
        onView(withText("Insert your data")).check(matches(isDisplayed()))
        IsolatedTestState.screenshot("02-profile")
        onView(withId(R.id.buttonConfirm)).perform(scrollTo(), click())
        onView(withId(R.id.input_name)).check(matches(hasErrorText("Insert a name")))
        onView(withId(R.id.input_name)).perform(replaceText("Synthetic User"), closeSoftKeyboard())
        onView(withId(R.id.buttonConfirm)).perform(scrollTo(), click())
        Assert.assertFalse(prefs.setupComplete)
        Assert.assertEquals("", prefs.nickname)
        completeProfile()
        onView(withText("Preparation")).check(matches(isDisplayed()))
        onView(withId(R.id.progressBar)).check(matches(not(isDisplayed())))
        onView(withId(R.id.progress_description)).check { status, error ->
            if (error != null) throw error
            val button = status.rootView.findViewById<View>(R.id.buttonConfirm)
            val statusPosition = IntArray(2); val buttonPosition = IntArray(2)
            status.getLocationOnScreen(statusPosition); button.getLocationOnScreen(buttonPosition)
            Assert.assertTrue("Readiness must not overlap Forward", statusPosition[1] + status.height <= buttonPosition[1])
        }
        IsolatedTestState.screenshot("03-preparation")
        pressBack()
        onView(withText("Insert your data")).check(matches(isDisplayed()))
        pressBack()
        onView(withText("Notice")).check(matches(isDisplayed()))
        onView(withId(R.id.buttonConfirm)).perform(scrollTo(), click())
        completeProfile()
        onView(withId(R.id.buttonConfirm)).perform(click())
        compose.onNodeWithTag("walkie-home").assertIsDisplayed()
        Assert.assertTrue(prefs.setupComplete)
        IsolatedTestState.screenshot("04-home-after-setup")
        // Launcher route, not just recreation, must skip completed setup.
        IsolatedTestState.finishActivities()
        ActivityScenario.launch(LegacyLoadingActivity::class.java)
        compose.onNodeWithTag("walkie-home").assertIsDisplayed()
    }

    @Test fun partialSetupResumesAtPreparationAndRotationPreservesStage() {
        prefs.nickname = "Synthetic User"
        ActivityScenario.launch(LegacyAccessActivity::class.java).use { scenario ->
            onView(withText("Preparation")).check(matches(isDisplayed()))
            Assert.assertFalse(prefs.setupComplete)
            pressBack()
            onView(withText("Insert your data")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText("Insert your data")).check(matches(isDisplayed()))
            onView(withId(R.id.input_name)).check(matches(withText("Synthetic User")))
            pressBack()
            onView(withText("Notice")).check(matches(isDisplayed()))
        }
    }

    @Test fun settingsNameLanguageReturnAndRelaunchPersist() {
        home()
        compose.onNodeWithTag("settings").performClick()
        onView(withText("Settings")).check(matches(isDisplayed()))
        IsolatedTestState.screenshot("05-settings-profile-input")
        onView(withId(R.id.editNameButton)).perform(scrollTo(), click())
        onView(withId(R.id.editText)).perform(replaceText("Demo Profile"), closeSoftKeyboard())
        IsolatedTestState.screenshot("06-edit-name")
        onView(withId(android.R.id.button1)).perform(click())
        onView(withId(R.id.username)).check(matches(withText("Demo Profile")))
        onView(withText("Personal language")).perform(scrollTo(), click())
        IsolatedTestState.screenshot("07-personal-language")
        onView(withText("Türkçe")).perform(click())
        onView(withText(R.string.vv_preference_title_tts)).perform(scrollTo())
        IsolatedTestState.screenshot("08-settings-language-output")
        onView(withText("Demo / backend")).perform(scrollTo())
        IsolatedTestState.screenshot("09-settings-advanced-developer")
        pressBack()
        compose.onNodeWithTag("walkie-home").assertIsDisplayed()
        compose.onNodeWithTag("language-picker").performClick()
        IsolatedTestState.screenshot("10-primary-language")
        androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.equalTo("tr"))
            .inAdapterView(withId(R.id.list_view_dialog)).perform(click())
        Assert.assertEquals(Language.TR, prefs.activeSpeech)
        IsolatedTestState.finishActivities()
        ActivityScenario.launch(LegacyLoadingActivity::class.java)
        compose.onNodeWithTag("language-picker").assertTextContains("Türkçe")
        Assert.assertEquals("Demo Profile", prefs.nickname)
        Assert.assertEquals(Language.TR, prefs.language)
        compose.onNodeWithTag("write").performClick()
        compose.onNodeWithText("Belirtilerinizi anlatın").assertIsDisplayed()
        compose.onNodeWithTag("symptoms").performTextInput("Sentetik demo belirtisi")
        compose.onNodeWithTag("editor-review").performScrollTo().performClick()
        compose.onNodeWithTag("analyze").assertIsNotEnabled()
        IsolatedTestState.screenshot("11-explicit-consent")
        compose.onNodeWithTag("confirm").performScrollTo().performClick()
        compose.onNodeWithTag("analyze").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("risk-MEDIUM").fetchSemanticsNodes().isNotEmpty() }
        IsolatedTestState.screenshot("12-demo-result-tr")
    }

    @Test fun developerSettingsSeparateAndBackPreservesDraft() {
        home()
        compose.onNodeWithTag("write").performClick()
        compose.onNodeWithTag("symptoms").performTextInput("Synthetic draft")
        // The editor is a Compose Dialog; target its focused root, not the Activity behind it.
        onView(androidx.test.espresso.matcher.ViewMatchers.isRoot()).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(closeSoftKeyboard())
        // IME dismissal is asynchronous; do not send Back into its closing animation.
        awaitFocusedWindowWithoutIme()
        pressBack()
        compose.waitForIdle()
        // Android may consume the first Back to finish dismissing the IME.
        if (compose.onAllNodesWithTag("symptoms").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("symptoms").assertTextEquals("Synthetic draft")
            pressBack()
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("symptoms").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("symptoms").assertDoesNotExist()
        compose.onNodeWithTag("settings").performClick()
        onView(withText("Microphone sensitivity")).check(matches(isDisplayed()))
        onView(withText("Demo / backend")).perform(scrollTo(), click())
        compose.onNodeWithText("Developer / Test settings").assertIsDisplayed()
        IsolatedTestState.screenshot("13-developer-settings")
        compose.onNodeWithTag("settingsBack").performScrollTo().performClick()
        // Clickable message selection merges child text into the bubble semantics.
        compose.onNodeWithTag("transcript").assertIsDisplayed().assertTextContains("Synthetic draft")
        compose.onNode(hasText("Synthetic draft") and hasAnyAncestor(hasTestTag("transcript")), useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("walkie-home").assertIsDisplayed()
    }

    @Test fun unsupportedSettingsExplainDependenciesWithoutFakeSliders() {
        home()
        compose.onNodeWithTag("settings").performClick()
        onView(withText("Microphone sensitivity")).perform(scrollTo(), click())
        onView(withText(org.hamcrest.Matchers.containsString("Android SpeechRecognizer alone does not expose"))).check(matches(isDisplayed()))
        IsolatedTestState.screenshot("14-missing-recorder-explanation")
        onView(withId(android.R.id.button1)).perform(click())
        onView(withText("Voice anticipation duration")).perform(scrollTo(), click())
        onView(withText(org.hamcrest.Matchers.containsString("pre-roll buffer"))).check(matches(isDisplayed()))
        onView(withId(android.R.id.button1)).perform(click())
        pressBack()
        compose.onNodeWithTag("walkie-home").assertIsDisplayed()
    }

    @Test fun preferenceWhitelistNeverStoresPatientTextOrTokens() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val vm = TriageViewModel()
            vm.attachPreferences(prefs)
            vm.language(Language.TR)
            vm.resultLanguage(Language.DE)
            vm.toggleLanguage(Language.FR)
            vm.activeLanguage(Language.FR)
            vm.autoLanguage(false)
            vm.text("SYNTHETIC_PRIVATE_DRAFT")
            vm.token("synthetic-session-secret")
            vm.endpoint("https://example.invalid")
        }
        val all = IsolatedTestState.context.getSharedPreferences(UserPreferences.FILE, Context.MODE_PRIVATE).all
        Assert.assertEquals(setOf("language", "result_language", "patient_languages", "active_speech", "auto_language", "backend_endpoint"), all.keys)
        Assert.assertFalse(all.toString().contains("SYNTHETIC_PRIVATE_DRAFT"))
        Assert.assertFalse(all.toString().contains("synthetic-session-secret"))
        Assert.assertEquals(Language.FR, prefs.activeSpeech)
        Assert.assertEquals(Language.TR, prefs.language)
        Assert.assertEquals(Language.DE, prefs.resultLanguage)
        Assert.assertFalse(prefs.autoLanguage)
    }
}
