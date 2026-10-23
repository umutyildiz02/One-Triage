package com.vitaltriage.app

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import com.vitaltriage.core.Language
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ModelDeliveryUiTest {
    @get:Rule val state=CompletedSetupRule()
    @Test fun nllbChoiceIsExplicitAndPersistsWithoutEnablingTranslation() {
        val prefs=UserPreferences(IsolatedTestState.context)
        ActivityScenario.launch(LegacySettingsActivity::class.java).use { activity ->
            onView(withText(R.string.vv_translation_engine)).perform(scrollTo(),click())
            onView(withText("NLLB · CC-BY-NC-4.0")).perform(click())
            onView(withText(android.R.string.ok)).perform(click())
            assertEquals("nllb",prefs.translationEngine)
            assertFalse(prefs.englishVoiceTranslation)
            activity.recreate()
            assertEquals("nllb",UserPreferences(IsolatedTestState.context).translationEngine)
            assertFalse(UserPreferences(IsolatedTestState.context).englishVoiceTranslation)
            IsolatedTestState.screenshot("product-nllb-settings")
        }
    }
    @Test fun fourLanguagePreparationIsExplicitAndHasNoFakeProgress() {
        val prefs=UserPreferences(IsolatedTestState.context)
        for(language in Language.uiLanguages) {
            prefs.language=language
            ActivityScenario.launch<ModelPreparationActivity>(
                Intent(IsolatedTestState.context,ModelPreparationActivity::class.java).putExtra("model","nllb")).use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(activity.getString(R.string.vv_nllb_title),activity.findViewById<TextView>(R.id.accessTitle).text.toString())
                    assertEquals(activity.getString(R.string.vv_model_prepare),activity.findViewById<TextView>(R.id.buttonConfirm).text.toString())
                    assertEquals(View.GONE,activity.findViewById<View>(R.id.progressBar).visibility)
                    assertEquals(View.GONE,activity.findViewById<View>(R.id.pauseButton).visibility)
                    assertTrue(activity.findViewById<TextView>(R.id.progress_description).text.contains("CC-BY-NC-4.0"))
                }
                IsolatedTestState.screenshot("product-nllb-prepare-"+language.tag)
            }
        }
    }
    @Test fun verifiedLocalWhisperIsReusedWithoutNewDownload() {
        val context=IsolatedTestState.context
        // Validate the fixture first; if absent, fail instead of making a test download.
        val file=WhisperEngine.prepareModel(context,WhisperModel.MEDIUM)
        val before=file.lastModified()
        ActivityScenario.launch<ModelPreparationActivity>(
            Intent(context,ModelPreparationActivity::class.java).putExtra("model",WhisperModel.MEDIUM.key)).use { scenario ->
            onView(withId(R.id.buttonConfirm)).perform(click())
            val deadline=SystemClock.elapsedRealtime()+15000
            var ready=false
            do {
                scenario.onActivity { ready=it.findViewById<TextView>(R.id.progress_description).text.toString()==it.getString(R.string.vv_model_ready) }
                if(!ready)SystemClock.sleep(100)
            } while(!ready && SystemClock.elapsedRealtime()<deadline)
            assertTrue("Existing verified model should be reused",ready)
            assertEquals(before,file.lastModified())
            assertFalse(java.io.File(file.parentFile,file.name+".part").exists())
        }
    }
}
