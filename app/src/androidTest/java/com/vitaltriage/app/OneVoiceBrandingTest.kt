package com.vitaltriage.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic UI fixtures only. No API, microphone, model or normal-profile mutation. */
class OneVoiceBrandingTest {
    @get:Rule val rule=createComposeRule()
    private fun screenshot(name:String) {
        rule.waitForIdle()
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        val directory=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"ui-comparison/onevoice").apply{mkdirs()}
        File(directory,"fixture-"+name+".png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun emptyHomeKeepsBrandAndIndependentControls() {
        var settings=0;var languages=0;var speak=0
        rule.setContent { VitalTheme { VitalApp(UiState(),TriageViewModel(),{},{},{},
            onSettings={settings++},onLanguage={languages++},onSpeak={speak++}) } }
        rule.onNodeWithContentDescription("OneVoice").assertIsDisplayed()
        rule.onNodeWithContentDescription("VitalVoice").assertDoesNotExist()
        rule.onNodeWithText("BACKEND · Network required").assertDoesNotExist()
        rule.onNodeWithTag("language-picker").assertIsDisplayed().performClick()
        rule.onNodeWithTag("settings").performClick()
        rule.onNodeWithTag("sound").performClick()
        assertEquals(1,settings);assertEquals(1,languages);assertEquals(1,speak)
        val logo=rule.onNodeWithTag("onevoice-logo").fetchSemanticsNode().boundsInRoot
        val picker=rule.onNodeWithTag("language-picker").fetchSemanticsNode().boundsInRoot
        val left=rule.onNodeWithTag("microphone").fetchSemanticsNode().boundsInRoot
        val right=rule.onNodeWithTag("microphone-auto").fetchSemanticsNode().boundsInRoot
        assertTrue(logo.bottom<=picker.top);assertTrue(left.right<right.left)
        assertEquals(left.width,right.width,1f)
        screenshot("empty")
        rule.onNodeWithTag("write").performClick()
        rule.onNodeWithTag("symptoms").assertIsDisplayed()
    }
    @Test fun fourRiskBandsShowExactlyOneTargetMessage() {
        var state by mutableStateOf(UiState())
        val cases=listOf(
            "LOW" to "My ankle is a little swollen after twisting it but I can walk.",
            "MEDIUM" to "I have had a fever and cough for two days.",
            "HIGH" to "I am breathing faster than usual.",
            "CRITICAL" to "I suddenly feel very short of breath even when sitting still.")
        rule.setContent { VitalTheme { VitalApp(state,TriageViewModel(),{},{},{}) } }
        cases.forEach { (risk,text) ->
            val evaluation=Demo.evaluate(TriageRequest(text,"en",listOf("en"),true,false,risk))
            rule.runOnIdle { state=UiState(symptoms="Kaynak sentetik metin",currentMessageLanguage="tr",evaluation=evaluation,
                speechTranslation=text,translationTarget="en",translationEngine="nllb") }
            rule.onNodeWithTag("risk-"+evaluation.result.urgency.name).assertIsDisplayed()
            val urgency=rule.onNodeWithTag("result-urgency",useUnmergedTree=true)
            urgency.assertIsDisplayed().assertTextContains(UiCopy.text("summary"+risk,Language.EN),substring=true)
            assertTrue("Urgency must remain above the microphones",
                urgency.fetchSemanticsNode().boundsInRoot.bottom <=
                    rule.onNodeWithTag("microphone").fetchSemanticsNode().boundsInRoot.top)
            rule.onNodeWithTag("result-summary",useUnmergedTree=true).assertTextContains("Possible diagnosis:",substring=true)
            rule.onNodeWithTag("speech-translation",useUnmergedTree=true).assertIsDisplayed()
            rule.onAllNodesWithText(text,useUnmergedTree=true).assertCountEquals(1)
            rule.onNodeWithText("Kaynak sentetik metin",useUnmergedTree=true).assertDoesNotExist()
            rule.onNodeWithTag("transcript").assertTextContains(text,substring=true)
            screenshot(risk.lowercase())
        }
    }
    @Test fun fourLanguagesAndListeningStatesFit() {
        var state by mutableStateOf(UiState())
        rule.setContent { VitalTheme { VitalApp(state,TriageViewModel(),{},{},{}) } }
        Language.uiLanguages.forEach { lang ->
            rule.runOnIdle { state=UiState(language=lang,activeSpeech=lang,currentMessageLanguage=lang.tag,translationTarget=lang.tag,symptoms=if(lang==Language.AR)
                "هذا نص اصطناعي لاختبار اتجاه الكتابة وعرض الرسائل الطويلة دون إرسال تحليل."
                else "Synthetic text for layout and editing; no API request.",listening=true,autoLanguage=false,speechLevel=.8f) }
            rule.onNodeWithTag("onevoice-logo").assertIsDisplayed()
            rule.onNodeWithTag("microphone").assertIsDisplayed()
            rule.onNodeWithTag("microphone-auto").assertIsDisplayed()
            rule.onNodeWithTag("transcript").assertIsDisplayed()
            assertTrue(rule.onNodeWithTag("onevoice-logo").fetchSemanticsNode().boundsInRoot.right <
                rule.onNodeWithTag("settings").fetchSemanticsNode().boundsInRoot.left)
            assertTrue(rule.onNodeWithTag("microphone").fetchSemanticsNode().boundsInRoot.right <
                rule.onNodeWithTag("microphone-auto").fetchSemanticsNode().boundsInRoot.left)
            screenshot("listening-"+lang.tag)
        }
    }
}
