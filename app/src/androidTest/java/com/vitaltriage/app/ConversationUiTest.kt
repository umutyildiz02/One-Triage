package com.vitaltriage.app

import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.vitaltriage.core.Language
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI callback simulation only. These tests are not evidence of Bluetooth radio pairing. */
class ConversationUiTest {
    @get:Rule val state=CompletedSetupRule()

    @Test fun fourLanguagePairingIsIdleUntilExplicitDiscoveryAndSurvivesRecreation() {
        for(language in Language.uiLanguages){
            UserPreferences(IsolatedTestState.context).language=language
            ActivityScenario.launch(ConversationActivity::class.java).use { scenario->
                scenario.onActivity { activity->
                    assertEquals(View.VISIBLE,activity.findViewById<View>(R.id.noDevices).visibility)
                    assertTrue(activity.findViewById<TextView>(R.id.discoveryDescription).text.isNotBlank())
                }
                scenario.recreate()
                onView(withId(R.id.searchButton)).check(matches(isDisplayed()))
                IsolatedTestState.screenshot("product-pairing-"+language.tag)
            }
        }
    }
    @Test fun discoveredPeersMoveStatusBelowListAndKeepErrorsVisible() {
        ActivityScenario.launch(ConversationActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.discovered("synthetic-peer", "Synthetic peer")
                it.status("timeout")
                assertEquals(View.GONE, it.findViewById<View>(R.id.discoveryDescription).visibility)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.discoveryDescriptionBottom).visibility)
                assertEquals(it.getString(R.string.vv_bt_status_timeout),
                    it.findViewById<TextView>(R.id.discoveryDescriptionBottom).text.toString())
            }
            // The source ConstraintLayout animates peer-list visibility changes.
            onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
                override fun getConstraints() = isRoot()
                override fun getDescription() = "Wait for the bounded legacy layout transition"
                override fun perform(ui: androidx.test.espresso.UiController, view: View) {
                    ui.loopMainThreadForAtLeast(500)
                }
            })
            IsolatedTestState.screenshot("product-pairing-discovered-status")
            onView(withId(R.id.discoveryDescriptionBottom)).check { view, error ->
                if (error != null) throw error
                val list = view.rootView.findViewById<View>(R.id.list_view)
                val statusPosition = IntArray(2)
                val listPosition = IntArray(2)
                view.getLocationOnScreen(statusPosition)
                list.getLocationOnScreen(listPosition)
                assertTrue("Discovery status overlaps peer list: statusY="+statusPosition[1]+
                    ", listY="+listPosition[1]+", listHeight="+list.height,
                    statusPosition[1] >= listPosition[1] + list.height)
            }
            onView(withText("Synthetic peer · -peer")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withId(R.id.discoveryDescription)).check(matches(isDisplayed()))
            onView(withId(R.id.noDevices)).check(matches(isDisplayed()))
        }
    }
    @Test fun sameLanguageConversationShowsOneMessageAndKeyboardRequiresExplicitSend() {
        UserPreferences(IsolatedTestState.context).apply {englishVoiceTranslation=false;language=Language.AR;translationTarget=Language.AR}
        ActivityScenario.launch(ConversationActivity::class.java).use { scenario->
            scenario.onActivity {
                it.connected("synthetic-peer","Synthetic peer")
                it.received(ConversationProtocol.Message("لا أعاني من ألم في الصدر",Language.AR))
            }
            onView(withText("لا أعاني من ألم في الصدر")).check(matches(isDisplayed()))
            onView(withId(R.id.buttonKeyboard)).perform(click())
            onView(withText(R.string.vv_bt_share_notice)).check(matches(isDisplayed()))
            onView(withText(android.R.string.cancel)).perform(click())
            onView(withId(R.id.tabConnection)).perform(click())
            onView(withText("Synthetic peer")).check(matches(isDisplayed()))
            IsolatedTestState.screenshot("product-conversation-simulated-peer")
        }
    }
    @Test fun recreationKeepsPeerScopedMessagesButNeverReconnectsAutomatically() {
        UserPreferences(IsolatedTestState.context).englishVoiceTranslation=false
        ActivityScenario.launch(ConversationActivity::class.java).use { scenario->
            scenario.onActivity {
                it.connected("synthetic-one","First peer")
                it.received(ConversationProtocol.Message("First peer synthetic text",Language.EN))
            }
            scenario.recreate()
            onView(withId(R.id.searchButton)).check(matches(isDisplayed()))
            scenario.onActivity { it.connected("synthetic-one","First peer") }
            onView(withText("First peer synthetic text")).check(matches(isDisplayed()))
            scenario.onActivity { it.connected("synthetic-two","Second peer") }
            onView(withText("First peer synthetic text")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        }
    }
    @Test fun speakerStopsCaptureBeforeAttemptingPlayback() {
        UserPreferences(IsolatedTestState.context).englishVoiceTranslation=false
        ActivityScenario.launch(ConversationActivity::class.java).use { scenario->
            scenario.onActivity {
                it.connected("synthetic-peer","Synthetic peer")
                it.received(ConversationProtocol.Message("Synthetic playback text",Language.EN))
                androidx.lifecycle.ViewModelProvider(it)[TriageViewModel::class.java].listening(true)
            }
            onView(withId(R.id.buttonSound)).perform(click())
            scenario.onActivity {
                assertFalse(androidx.lifecycle.ViewModelProvider(it)[TriageViewModel::class.java].state.value.listening)
            }
        }
    }
    @Test fun backgroundClosesSessionAndReturnsToPairingWithoutSharing() {
        ActivityScenario.launch(ConversationActivity::class.java).use { scenario->
            scenario.onActivity {it.connected("synthetic-peer","Synthetic peer")}
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            onView(withId(R.id.searchButton)).check(matches(isDisplayed()))
            onView(withId(R.id.noDevices)).check(matches(isDisplayed()))
        }
    }
    @Test fun privacyAndLicenseDocumentsRemainAccessibleWithoutNetworkOrConsent() {
        ActivityScenario.launch(LegacySettingsActivity::class.java).use {
            onView(withText(R.string.vv_privacy_title)).perform(scrollTo(),click())
            onView(withText(org.hamcrest.Matchers.containsString("this does not promise zero provider retention")))
                .check(matches(isDisplayed()))
            onView(withText(android.R.string.ok)).perform(click())
            onView(withText(R.string.vv_licenses_title)).perform(scrollTo(),click())
            onView(withText(org.hamcrest.Matchers.containsString("SIL OPEN FONT LICENSE")))
                .check(matches(isDisplayed()))
            onView(withText(android.R.string.ok)).perform(click())
        }
    }
    @Test fun settingsToolbarRemainsBelowStatusBarAfterRecreation() {
        ActivityScenario.launch(LegacySettingsActivity::class.java).use { scenario ->
            repeat(2) {
                onView(withId(R.id.toolbarSettings)).check { view, error ->
                    if (error != null) throw error
                    val position = IntArray(2)
                    view.getLocationOnScreen(position)
                    val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
                    val top = requireNotNull(insets).getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
                    assertTrue("Toolbar overlaps the status bar", position[1] >= top)
                }
                scenario.recreate()
            }
        }
    }
    @Test fun settingsOpensConversationAndBackReturnsWithoutChangingSetup() {
        val prefs=UserPreferences(IsolatedTestState.context)
        val before=prefs.language
        ActivityScenario.launch(LegacySettingsActivity::class.java).use {
            onView(withText(R.string.vv_bt_open)).perform(scrollTo(),click())
            onView(withId(R.id.searchButton)).check(matches(isDisplayed()))
            androidx.test.espresso.Espresso.pressBack()
            assertEquals(before,prefs.language)
            onView(withText(R.string.vv_bt_open)).check(matches(isDisplayed()))
        }
    }
}
