package com.vitaltriage.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import com.vitaltriage.core.Language
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExpandedLanguageDeviceTest {
    @get:Rule val state=CompletedSetupRule()
    @Test fun languageRolesMigratePersistAndRecreateWithoutChangingConsent() {
        val context=IsolatedTestState.context
        val storage=context.getSharedPreferences(UserPreferences.FILE,Context.MODE_PRIVATE)
        storage.edit().putString("language","tr").remove("result_language").commit()
        val prefs=UserPreferences(context)
        assertEquals(Language.TR,prefs.resultLanguage)
        prefs.language=Language.FR
        assertEquals("Migration must preserve the previous result choice",Language.TR,prefs.resultLanguage)
        prefs.language=Language.TR; prefs.resultLanguage=Language.TR
        prefs.patientLanguages=setOf(Language.DE,Language.JA,Language.KO)
        prefs.activeSpeech=Language.DE; prefs.translationTarget=Language.EN
        ActivityScenario.launch(MainActivity::class.java).use { scenario->
            scenario.onActivity { activity->
                val vm=ViewModelProvider(activity)[TriageViewModel::class.java]
                assertEquals(Language.TR,vm.state.value.language)
                assertEquals(Language.TR,vm.state.value.resultLanguage)
                assertEquals(Language.DE,vm.state.value.activeSpeech)
                assertEquals("en",vm.state.value.translationTarget)
                assertFalse(vm.state.value.confirmed)
            }
            scenario.recreate()
            scenario.onActivity { activity->
                val vm=ViewModelProvider(activity)[TriageViewModel::class.java]
                assertEquals(Language.TR,vm.state.value.resultLanguage)
                assertEquals(setOf(Language.DE,Language.JA,Language.KO),vm.state.value.patientLanguages)
                assertFalse(vm.state.value.confirmed)
            }
        }
        Language.entries.forEach { language->
            prefs.activeSpeech=language; prefs.resultLanguage=language; prefs.translationTarget=language
            val reopened=UserPreferences(context)
            assertEquals(language,reopened.activeSpeech);assertEquals(language,reopened.resultLanguage)
            assertEquals(language,reopened.translationTarget);assertEquals(Language.TR,reopened.language)
        }
        assertEquals(71,LegacyLanguageCatalog.tags(true).size)
        assertNull(Language.find("xx"));assertNull(Language.fromSpeechTag("zh-Hant-TW"))
    }
}
