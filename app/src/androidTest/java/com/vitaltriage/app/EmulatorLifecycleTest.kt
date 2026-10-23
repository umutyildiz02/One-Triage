package com.vitaltriage.app

import android.Manifest
import android.content.ComponentCallbacks2
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.rule.GrantPermissionRule
import com.vitaltriage.core.Language
import org.junit.*
import org.junit.Assert.*

class EmulatorLifecycleTest {
    @get:Rule val compose=createEmptyComposeRule()
    @get:Rule val microphonePermission=GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    @Before fun before()=IsolatedTestState.reset(completed=true)
    @After fun after()=IsolatedTestState.finishActivities()

    @Test fun recreationAndBackgroundPreserveDraftButDoNotApproveOrSend() {
        val prefs=UserPreferences(IsolatedTestState.context)
        prefs.demo=false;prefs.offlineWhisper=true;prefs.whisperModel=WhisperModel.MEDIUM
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm:TriageViewModel
            scenario.onActivity {vm=ViewModelProvider(it)[TriageViewModel::class.java]}
            compose.onNodeWithTag("write").performClick()
            compose.onNodeWithTag("symptoms").performTextInput("Synthetic draft retained during recreation.")
            compose.onNodeWithTag("editor-review").performScrollTo().performClick()
            scenario.recreate()
            compose.onNodeWithTag("analyze").assertIsNotEnabled()
            assertEquals("Synthetic draft retained during recreation.",vm.state.value.symptoms)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("analyze").assertIsNotEnabled()
            assertFalse(vm.state.value.demo);assertNull(vm.state.value.evaluation)
            assertEquals(WhisperModel.MEDIUM,prefs.whisperModel)
        }
    }
    @Test fun realManualCaptureStopsOnBackgroundAndCanBeStartedAgain() {
        UserPreferences(IsolatedTestState.context).offlineWhisper=true
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm:TriageViewModel
            scenario.onActivity {vm=ViewModelProvider(it)[TriageViewModel::class.java]}
            compose.onNodeWithTag("microphone").performClick()
            compose.onNodeWithTag("speech-start").performScrollTo().performClick()
            compose.waitUntil(5000){vm.state.value.listening || vm.state.value.speechError!=null}
            assertNull(vm.state.value.speechError);assertTrue(vm.state.value.listening)
            assertFalse(vm.state.value.autoLanguage)
            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(vm.state.value.listening)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("microphone-auto").performClick()
            compose.waitUntil(5000){vm.state.value.listening || vm.state.value.speechError!=null}
            assertNull(vm.state.value.speechError);assertTrue(vm.state.value.listening)
            assertTrue(vm.state.value.autoLanguage)
            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(vm.state.value.listening)
            assertEquals("",vm.state.value.symptoms)
            assertNull(vm.state.value.evaluation)
        }
    }
    @Test fun lowMemoryPreflightRejectsAudioBeforeLoadingAndPreservesDraft() {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val vm=TriageViewModel()
        val pcm=ShortArray(16000){1000}
        lateinit var controller:SpeechController
        instrumentation.runOnMainSync {
            vm.text("Existing synthetic draft")
            controller=SpeechController(IsolatedTestState.context,vm,memoryPressure={true})
            controller.transcribePcm(pcm)
        }
        assertEquals("speechMemory",vm.state.value.speechError)
        assertFalse(vm.state.value.listening)
        assertEquals("Existing synthetic draft",vm.state.value.symptoms)
        assertTrue(pcm.all {it==0.toShort()})
        assertNull(vm.state.value.evaluation)
        instrumentation.runOnMainSync {controller.destroy()}
    }
    @Test fun criticalMemorySignalShowsHonestErrorAndTypedInputRemains() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm:TriageViewModel
            scenario.onActivity {
                vm=ViewModelProvider(it)[TriageViewModel::class.java]
                it.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
            }
            compose.onNodeWithText(UiCopy.text("speechMemory",Language.EN)).assertIsDisplayed()
            compose.onNodeWithTag("typed-fallback").performClick()
            compose.onNodeWithTag("symptoms").performTextInput("Synthetic typed fallback after memory warning.")
            assertFalse(vm.state.value.listening);assertFalse(vm.state.value.confirmed)
            assertNull(vm.state.value.evaluation)
        }
    }
    @Test fun landscapeAndPortraitKeepLongArabicDraftEditable() {
        UserPreferences(IsolatedTestState.context).language=Language.AR
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm:TriageViewModel
            scenario.onActivity {vm=ViewModelProvider(it)[TriageViewModel::class.java]}
            compose.onNodeWithTag("write").performClick()
            val text="حالة اصطناعية للمراجعة وليست بيانات مريض. ".repeat(24)
            compose.onNodeWithTag("symptoms").performTextInput(text)
            scenario.onActivity {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("walkie-home").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.let {it.width>it.height}==true
            }
            if(compose.onAllNodesWithTag("symptoms").fetchSemanticsNodes().isEmpty())
                compose.onNodeWithTag("write").performClick()
            compose.onNodeWithTag("symptoms").assertTextEquals(text)
            compose.onNodeWithTag("editor-review").performScrollTo().performClick()
            compose.onNodeWithTag("analyze").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            assertNull(vm.state.value.evaluation)
            // A semantic visibility assertion alone does not detect a system ANR overlay.
            onView(androidx.test.espresso.matcher.ViewMatchers.isRoot()).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check { view, error ->
                if(error!=null)throw error
                assertTrue("Review dialog must own window focus",view.hasWindowFocus())
            }
            IsolatedTestState.screenshot("final-ar-landscape-review")
            scenario.onActivity {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("walkie-home").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.let {it.height>it.width}==true
            }
            compose.onNodeWithTag("analyze").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        }
    }
    @Test fun nativeSetupAndSettingsUseSelectedLanguageAndArabicDirection() {
        for(language in Language.uiLanguages) {
            IsolatedTestState.reset()
            val prefs=UserPreferences(IsolatedTestState.context)
            prefs.language=language
            ActivityScenario.launch(LegacyAccessActivity::class.java).use { scenario ->
                onView(withText(R.string.vv_title_fragment_notice)).check(matches(isDisplayed()))
                scenario.onActivity {
                    assertEquals(language.tag,it.resources.configuration.locales[0].language)
                    assertEquals(if(language==Language.AR)View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                        it.resources.configuration.layoutDirection)
                }
                IsolatedTestState.screenshot("final-"+language.tag+"-notice")
            }
            prefs.nickname="Synthetic profile";prefs.setupComplete=true
            ActivityScenario.launch(LegacySettingsActivity::class.java).use {
                onView(withText(R.string.vv_title_activity_settings)).check(matches(isDisplayed()))
                onView(withText(R.string.vv_preference_title_mic_sensitivity)).check(matches(isDisplayed()))
                IsolatedTestState.screenshot("final-"+language.tag+"-settings")
            }
            IsolatedTestState.finishActivities()
        }
    }
}
