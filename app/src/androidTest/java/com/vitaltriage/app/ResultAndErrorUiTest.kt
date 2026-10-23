package com.vitaltriage.app
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.*
import java.io.File
import org.junit.Rule
import org.junit.Test
class ResultAndErrorUiTest {
    @get:Rule val rule=createComposeRule()
    private fun snapshot(name:String,dialog:Boolean=false) {
        rule.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"ui-comparison").apply{mkdirs()}
        // Capture the composed app surface; emulator system dialogs are not fixture evidence.
        val bitmap=(if(dialog)rule.onNode(isDialog()) else rule.onRoot()).captureToImage().asAndroidBitmap()
        File(directory,name+".png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun everyRiskLevelHasVisibleDistinctStatus(){
        var level by mutableStateOf(Urgency.LOW)
        rule.setContent{
            val r=TriageRequest("Synthetic symptom lasting two days","en",listOf("en"),true,false,level.name)
            VitalTheme{ResultCard(Demo.evaluate(r).result,Language.EN)}
        }
        Urgency.entries.forEach{u->
            rule.runOnIdle{level=u}
            rule.onNodeWithTag("risk-"+u.name).assertIsDisplayed()
            if(u==Urgency.UNDETERMINED)rule.onNodeWithText("Undetermined").assertExists()
        }
    }
    @Test fun longAssessmentScrollsAndFinalDetailRemainsReachable() {
        val base=Demo.evaluate(TriageRequest("Synthetic UI layout fixture only","en",listOf("en"),true,false,"MEDIUM"))
        val assessment=("Synthetic layout text with a deliberately long explanation. ").repeat(24)
        val marker="Final follow-up fixture remains accessible"
        val state=UiState(evaluation=base.copy(result=base.result.copy(assessment=assessment,followUpQuestions=listOf(marker))))
        rule.setContent { VitalTheme { VitalApp(state,TriageViewModel(),{},{},{}) } }
        val summary=rule.onNodeWithTag("result-summary",useUnmergedTree=true)
        summary.assertIsDisplayed()
        org.junit.Assert.assertTrue("Long strip must be scrollable",
            summary.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].maxValue()>0f)
        rule.onNodeWithTag("result-urgency",useUnmergedTree=true).assertIsDisplayed()
            .assertTextContains(UiCopy.text("summaryMEDIUM",Language.EN),substring=true)
        summary.performTouchInput { swipeUp() }
        org.junit.Assert.assertTrue("Strip scroll must move the content",
            summary.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()>0f)
        rule.onNodeWithTag("result-summary",useUnmergedTree=true).assertIsDisplayed().performClick()
        rule.onNode(hasText("• $marker") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
        snapshot("fixture-long-result-details",dialog=true)
    }
    @Test fun localDeadlineHasDistinctFourLanguageErrorAndTypedFallback() {
        var state by mutableStateOf(UiState(speechError="speechTimeout"))
        rule.setContent { VitalTheme { VitalApp(state,TriageViewModel(),{},{},{}) } }
        Language.uiLanguages.forEach { language ->
            rule.runOnIdle { state=state.copy(language=language) }
            rule.onNodeWithText(UiCopy.text("speechTimeout",language)).assertExists()
            rule.onNodeWithText(UiCopy.text("speechNoMatch",language)).assertDoesNotExist()
            rule.onNodeWithTag("typed-fallback").assertIsEnabled()
        }
    }
    @Test fun permissionAndSpeechErrorsKeepTypedInput(){
        var state by mutableStateOf(UiState(speechError="permission"))
        val vm=TriageViewModel()
        rule.setContent{VitalTheme{VitalApp(state,vm,{},{},{})}}
        rule.onNodeWithText(UiCopy.text("permission",Language.EN)).assertExists()
        snapshot("fixture-permission-error")
        rule.onNodeWithTag("typed-fallback").performClick()
        rule.onNodeWithTag("symptoms").assertExists()
        rule.onNodeWithTag("editor-review").performScrollTo().assertIsEnabled()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.runOnIdle{state=state.copy(speechError="speechUnavailable")}
        rule.onNodeWithText(UiCopy.text("speechUnavailable",Language.EN)).assertExists()
        snapshot("fixture-speech-unavailable")
        rule.onNodeWithTag("typed-fallback").performClick()
        rule.onNodeWithTag("symptoms").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.runOnIdle{state=UiState(language=Language.TR,activeSpeech=Language.TR)}
        snapshot("fixture-microphones-idle")
        rule.runOnIdle{state=state.copy(listening=true,autoLanguage=false,speechLevel=.8f)}
        rule.onNodeWithTag("microphone").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"listening"))
        snapshot("fixture-microphone-manual-listening")
        rule.runOnIdle{state=state.copy(autoLanguage=true,speechLevel=.6f)}
        rule.onNodeWithTag("microphone-auto").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"listening"))
        snapshot("fixture-microphone-auto-listening")
        rule.runOnIdle{state=state.copy(screen=Screen.ANALYZING,listening=false)}
        rule.onNodeWithTag("microphone").assertIsNotEnabled()
        rule.onNodeWithTag("microphone-auto").assertIsNotEnabled()
        snapshot("fixture-analysis-disabled")
    }
}
