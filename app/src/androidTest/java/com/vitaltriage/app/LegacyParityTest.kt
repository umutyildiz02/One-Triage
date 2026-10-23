package com.vitaltriage.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers.*
import org.junit.*
import org.junit.Assert.*

class LegacyParityTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Before fun before()=IsolatedTestState.reset(completed=true)
    @After fun after()=IsolatedTestState.finishActivities()
    @Test fun emptyHomeHasNoTechnicalRowAndConversationOpensEditor() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText(UiCopy.text("backend",com.vitaltriage.core.Language.EN)).assertDoesNotExist()
            compose.onNodeWithText(UiCopy.text("about",com.vitaltriage.core.Language.EN)).assertDoesNotExist()
            compose.onNodeWithText(UiCopy.text("write",com.vitaltriage.core.Language.EN)).assertDoesNotExist()
            compose.onNodeWithTag("write").performClick()
            compose.onNodeWithTag("symptoms").performTextInput("Synthetic editable text")
            compose.onNodeWithTag("editor-review").performScrollTo().performClick()
            compose.onNodeWithTag("analyze").assertIsNotEnabled()
        }
    }
    @Test fun qualitySwitchesPersistAndMappedProcessingLanguagesAreSelectable() {
        ActivityScenario.launch(LegacySettingsActivity::class.java).use { scenario ->
            onView(withText(R.string.vv_preference_title_languages_quality)).perform(scrollTo(),click())
            onView(withText(R.string.vv_preference_title_support_tts_quality)).perform(scrollTo(),click())
            assertTrue(UserPreferences(IsolatedTestState.context).languagesNNQualityLow)
            assertTrue(UserPreferences(IsolatedTestState.context).languagesQualityLow)
            scenario.recreate()
            assertTrue(UserPreferences(IsolatedTestState.context).languagesNNQualityLow)
            assertTrue(UserPreferences(IsolatedTestState.context).languagesQualityLow)
            scenario.onActivity { activity -> LegacyDialogs.language(activity,R.string.vv_dialog_select_first_language,UserPreferences(activity).activeSpeech) { UserPreferences(activity).activeSpeech=it } }
            onData(equalTo("af")).inAdapterView(withId(R.id.list_view_dialog)).check(matches(androidx.test.espresso.matcher.ViewMatchers.isEnabled()))
            onData(equalTo("af")).inAdapterView(withId(R.id.list_view_dialog)).check { view,error ->
                if(error!=null)throw error
                val label=view.findViewById<android.widget.TextView>(R.id.languageName)
                val margins=label.layoutParams as android.view.ViewGroup.MarginLayoutParams
                assertTrue("Catalog explanation extends beyond its row",label.right<=view.width-margins.marginEnd)
            }
            onData(equalTo("af")).inAdapterView(withId(R.id.list_view_dialog)).perform(click())
            assertEquals(com.vitaltriage.core.Language.AF,UserPreferences(IsolatedTestState.context).activeSpeech)
            assertEquals(com.vitaltriage.core.Language.EN,UserPreferences(IsolatedTestState.context).language)
            onView(withId(R.id.list_view_dialog)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            onView(withText(R.string.vv_preference_title_languages_quality)).perform(scrollTo(),click())
            assertFalse(UserPreferences(IsolatedTestState.context).languagesNNQualityLow)
            scenario.onActivity { activity -> LegacyDialogs.language(activity,R.string.vv_dialog_select_first_language,UserPreferences(activity).activeSpeech) { UserPreferences(activity).activeSpeech=it } }
            onView(withId(R.id.list_view_dialog)).check { view,error ->
                if(error!=null)throw error
                assertEquals(31,(view as android.widget.ListView).adapter.count)
            }
        }
    }
    @Test fun displayedPaletteMatchesApprovedOneTriageReferenceRatherThanModelFreeText() {
        val context=IsolatedTestState.context
        assertEquals(android.graphics.Color.parseColor("#2AA43C"),context.getColor(R.color.urgency_low))
        assertEquals(android.graphics.Color.parseColor("#FFBD00"),context.getColor(R.color.urgency_medium))
        assertEquals(android.graphics.Color.parseColor("#FF5200"),context.getColor(R.color.urgency_high))
        assertEquals(android.graphics.Color.parseColor("#E60000"),context.getColor(R.color.urgency_critical))
    }
}
