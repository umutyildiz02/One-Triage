package com.vitaltriage.app

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.vitaltriage.core.Language
import org.junit.*
import org.junit.Assert.*

/** Injects decoded synthetic peer messages, not translations. Actual NLLB queue; radio tested separately. */
class ConversationTranslationTest {
    @Before fun before()=IsolatedTestState.reset(completed=true)
    @After fun after()=IsolatedTestState.finishActivities()
    @Test fun queuedPeerOriginalsKeepOrderAndReceiveActualIndependentTranslations() = verifyQueue(false)
    @Test fun optedInPeerTranslationsFinishRealTtsBeforeNextTurn() = verifyQueue(true)
    private fun verifyQueue(readAloud:Boolean) {
        val context=IsolatedTestState.context
        UserPreferences(context).apply {englishVoiceTranslation=true;translationEngine="nllb";language=Language.EN;readTranslatedTurns=readAloud}
        val originals=listOf("Ateşim yok." to Language.TR,"Je n'ai pas de fièvre." to Language.FR)
        ActivityScenario.launch(ConversationActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.translationModelDirectory={context.getExternalFilesDir("nllb")!!}
                activity.connected("synthetic-test-peer","Synthetic peer")
                originals.forEach { activity.received(ConversationProtocol.Message(it.first,it.second)) }
            }
            val deadline=SystemClock.elapsedRealtime()+90000
            var result:List<ConversationMessage> = emptyList()
            var speakingPreviously=false
            var actualPlaybackStarts=0
            var automaticReading=false
            do {
                scenario.onActivity { activity ->
                    result=ViewModelProvider(activity)[ConversationMemoryViewModel::class.java].messages.map { it.copy() }
                    val speaking=ViewModelProvider(activity)[TriageViewModel::class.java].state.value.speaking
                    if(speaking && !speakingPreviously)actualPlaybackStarts++
                    speakingPreviously=speaking
                    automaticReading=activity.automaticallyReading
                }
                if(result.size==2 && result.all { it.translated!=null || it.translationError } && !automaticReading)break
                SystemClock.sleep(100)
            } while(SystemClock.elapsedRealtime()<deadline)
            assertFalse("TTS or translation queue did not finish",automaticReading)
            assertEquals("Actual TTS onStart callbacks must match the opted-in translated turns",
                if(readAloud)2 else 0,actualPlaybackStarts)
            assertEquals(originals.map { it.first },result.map { it.text })
            assertEquals(originals.map { it.second },result.map { it.language })
            assertTrue(result.all { !it.outgoing && !it.translationError && it.provider=="nllb" })
            result.forEach { item ->
                val translated=item.translated.orEmpty().lowercase()
                assertTrue("Actual fever translation absent",translated.contains("fever"))
                assertTrue("Negation absent",Regex("\\b(no|not|without)\\b|n't").containsMatchIn(translated))
                assertEquals(Language.EN,item.translatedLanguage)
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(item.text))
                    .check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            }
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(result.last().translated))
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            scenario.onActivity { activity ->
                val state=ViewModelProvider(activity)[TriageViewModel::class.java].state.value
                assertEquals("",state.symptoms);assertNull(state.evaluation);assertFalse(state.confirmed)
            }
            IsolatedTestState.screenshot("product-conversation-real-nllb")
        }
    }
}
