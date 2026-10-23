package com.vitaltriage.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Language
import org.junit.Assert.*
import org.junit.Test

class LivePreferencesTest {
    @Test fun liveChoiceAndFourLanguagesSurviveRecreationWithoutSecrets() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        IsolatedTestState.reset()
        val prefs=UserPreferences(context)
        Language.uiLanguages.forEach { language ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val first=TriageViewModel()
                first.attachPreferences(prefs);first.demo(false)
                first.endpoint("http://127.0.0.1:8787")
                first.language(language)
                first.text("SYNTHETIC_TRANSIENT");first.token("SYNTHETIC_SECRET")
                val second=TriageViewModel();second.attachPreferences(UserPreferences(context))
                assertFalse(second.state.value.demo)
                assertEquals(language,second.state.value.language)
                assertEquals("http://127.0.0.1:8787",second.state.value.endpoint)
                assertEquals("",second.state.value.symptoms)
                assertEquals("",second.state.value.accessToken)
            }
        }
        val stored=context.getSharedPreferences(UserPreferences.FILE,Context.MODE_PRIVATE).all.toString()
        assertFalse(stored.contains("SYNTHETIC_TRANSIENT"));assertFalse(stored.contains("SYNTHETIC_SECRET"))
        prefs.endpoint="https://user:secret@example.invalid"
        assertEquals("http://127.0.0.1:8787",prefs.endpoint)
        prefs.endpoint="https://example.invalid?token=secret"
        assertEquals("http://127.0.0.1:8787",prefs.endpoint)
    }
}
