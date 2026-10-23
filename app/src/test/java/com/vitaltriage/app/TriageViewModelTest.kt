package com.vitaltriage.app
import com.vitaltriage.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
@OptIn(ExperimentalCoroutinesApi::class)
class TriageViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}
    private fun ready(vm:TriageViewModel){vm.text("Synthetic irritation lasting two days");vm.review();vm.confirm(true)}
    @Test fun typedTextRequiresReviewAndConfirmation(){
        val vm=TriageViewModel();vm.text("Synthetic text");vm.review()
        assertEquals(Screen.REVIEW,vm.state.value.screen);vm.analyze()
        assertEquals(Screen.REVIEW,vm.state.value.screen);assertNotNull(vm.state.value.error)
    }
    @Test fun textEditInvalidatesConfirmation(){val vm=TriageViewModel();ready(vm);vm.text("Changed synthetic text");assertFalse(vm.state.value.confirmed)}
    @Test fun allLanguageSelections(){val vm=TriageViewModel();Language.uiLanguages.forEach{vm.language(it);assertEquals(it,vm.state.value.language)}}
    @Test fun cannotRemoveFinalPatientLanguage(){val vm=TriageViewModel();vm.toggleLanguage(Language.EN);assertEquals(setOf(Language.EN),vm.state.value.patientLanguages)}
    @Test fun multiplePatientLanguages(){val vm=TriageViewModel();vm.toggleLanguage(Language.TR);vm.activeLanguage(Language.TR);assertEquals(Language.TR,vm.state.value.activeSpeech)}
    @Test fun permissionDeniedKeepsTextEditable(){val vm=TriageViewModel();vm.permission(false);vm.text("Synthetic typed fallback");assertFalse(vm.state.value.listening);assertEquals("permission",vm.state.value.speechError);assertTrue(vm.state.value.symptoms.isNotBlank())}
    @Test fun unavailableSpeechDoesNotBlockTyping(){val vm=TriageViewModel();vm.speechError("speechUnavailable");ready(vm);assertTrue(vm.state.value.confirmed)}
    @Test fun transcriptAppendsAndMustBeReviewed(){val vm=TriageViewModel();ready(vm);vm.transcript("additional synthetic words");assertFalse(vm.state.value.confirmed);assertTrue(vm.state.value.symptoms.contains("additional"))}
    @Test fun mockCompletesAllLevels()=runTest {
        Urgency.entries.forEach{u->val vm=TriageViewModel();vm.scenario(Scenario.valueOf(u.name));ready(vm);vm.analyze();advanceUntilIdle();assertEquals(u,vm.state.value.evaluation!!.result.urgency)}
    }
    @Test fun offlineReturnsUndetermined()=runTest {
        val vm=TriageViewModel();vm.scenario(Scenario.OFFLINE);ready(vm);vm.analyze();advanceUntilIdle()
        assertEquals(Urgency.UNDETERMINED,vm.state.value.evaluation!!.result.urgency);assertTrue(vm.state.value.evaluation!!.failed)
    }
    @Test fun backendFailureContainsNoExceptionText()=runTest {
        val vm=TriageViewModel(AssessmentRepository{_,_,_->error("private synthetic message")});vm.demo(false);ready(vm);vm.analyze();advanceUntilIdle()
        assertEquals("UNAVAILABLE",vm.state.value.evaluation!!.mode);assertFalse(vm.state.value.evaluation.toString().contains("private"))
    }
    @Test fun cancelPreventsLateNavigation()=runTest {
        val vm=TriageViewModel();ready(vm);vm.analyze();vm.navigate(Screen.INPUT);advanceUntilIdle()
        assertEquals(Screen.INPUT,vm.state.value.screen);assertNull(vm.state.value.evaluation)
        assertFalse("Cancelled analysis needs renewed consent",vm.state.value.confirmed)
    }
    @Test fun failedLiveRequestClearsPreviousSuccessAndNeverUsesDemo()=runTest {
        var calls=0
        val vm=TriageViewModel(AssessmentRepository { request,_,_ ->
            calls++
            if(calls==1) Evaluation(Demo.evaluate(request).result,"LIVE") else throw BackendFailure("OPENAI_CREDIT")
        })
        vm.demo(false);ready(vm);vm.analyze();advanceUntilIdle()
        assertFalse(vm.state.value.evaluation!!.failed)
        vm.text("Different synthetic symptoms");vm.review();vm.confirm(true);vm.analyze()
        assertNull(vm.state.value.evaluation)
        advanceUntilIdle()
        assertEquals(2,calls);assertTrue(vm.state.value.evaluation!!.failed)
        assertEquals("UNAVAILABLE",vm.state.value.evaluation!!.mode)
        assertEquals("OPENAI_CREDIT",vm.state.value.backendFailure)
        assertEquals(Urgency.UNDETERMINED,vm.state.value.evaluation!!.result.urgency)
    }
    @Test fun fourResultLanguagesAreIndependentOfSpeechLanguage()=runTest {
        Language.uiLanguages.forEach { language ->
            val vm=TriageViewModel(AssessmentRepository { request,_,_ ->
                assertEquals(language.tag,request.language)
                assertEquals(listOf("en"),request.patientLanguages)
                Evaluation(Safety.unknown(request.language),"LIVE")
            })
            vm.demo(false);vm.resultLanguage(language);ready(vm);vm.analyze();advanceUntilIdle()
            assertEquals(language.tag,vm.state.value.evaluation!!.result.language)
        }
    }
    @Test fun translationNeverReplacesApprovedOriginal()=runTest {
        var received:TriageRequest?=null
        val vm=TriageViewModel(AssessmentRepository { request,_,_-> received=request;Evaluation(Safety.unknown(request.language),"LIVE") })
        vm.demo(false)
        vm.detected("fr")
        vm.transcript("Exemple synthétique avec une irritation depuis deux jours.","Synthetic irritation lasting two days.")
        assertEquals("fr",vm.state.value.detectedLanguage)
        assertNotNull(vm.state.value.speechTranslation)
        assertFalse(vm.state.value.confirmed)
        assertNull(received)
        vm.review();vm.confirm(true);vm.analyze();advanceUntilIdle()
        assertEquals("Exemple synthétique avec une irritation depuis deux jours.",received!!.symptoms)
        vm.text("Edited synthetic original")
        assertNull(vm.state.value.speechTranslation)
        assertFalse(vm.state.value.confirmed)
    }
    @Test fun failedTranslationPreservesOriginalAndResetClearsBoth() {
        val vm=TriageViewModel()
        vm.transcript("Synthetic original",translationFailed=true)
        assertEquals("Synthetic original",vm.state.value.symptoms)
        assertTrue(vm.state.value.translationFailed)
        assertNull(vm.state.value.speechTranslation)
        vm.reset()
        assertFalse(vm.state.value.translationFailed)
        assertNull(vm.state.value.speechTranslation)
        assertEquals("",vm.state.value.symptoms)
    }
    @Test fun oversizedSpeechDoesNotSilentlyTruncateNewInformation() {
        val vm=TriageViewModel()
        val original="Synthetic ".repeat(390)
        vm.text(original);vm.confirm(true)
        vm.transcript("Additional speech ".repeat(20))
        assertEquals(original,vm.state.value.symptoms)
        assertEquals("speechTooLong",vm.state.value.speechError)
        assertFalse(vm.state.value.confirmed)
        assertNull(vm.state.value.evaluation)
        assertFalse(vm.state.value.listening)
    }
    @Test fun blankTranscriptIsNotARecognizedUtterance() {
        val vm=TriageViewModel()
        vm.text("Existing synthetic text");vm.listening(true)
        vm.transcript("  ")
        assertEquals("Existing synthetic text",vm.state.value.symptoms)
        assertEquals("speechNoMatch",vm.state.value.speechError)
        assertFalse(vm.state.value.listening)
    }
    @Test fun repeatedAnalyzeDoesNotCancelAndResendPaidRequest()=runTest {
        var calls=0
        val vm=TriageViewModel(AssessmentRepository { r,_,_->calls++;delay(1000);Evaluation(Safety.unknown(r.language),"LIVE") })
        vm.demo(false);ready(vm);vm.analyze();runCurrent()
        vm.analyze();vm.analyze();advanceUntilIdle()
        assertEquals(1,calls);assertEquals(Screen.RESULT,vm.state.value.screen)
    }
    @Test fun editingRequestInputsCancelsStaleResultAndRequiresFreshConsent()=runTest {
        val changes:List<(TriageViewModel)->Unit> = listOf(
            {it.text("Different synthetic symptoms")},{it.language(Language.AR)},
            {it.danger(true)},{it.endpoint("http://127.0.0.1:9999")},
            {it.token("different-session-token")},{it.demo(true)},{it.toggleLanguage(Language.TR)}
        )
        changes.forEach { change ->
            var finished=false
            val vm=TriageViewModel(AssessmentRepository { r,_,_->delay(1000);finished=true;Evaluation(Safety.unknown(r.language),"LIVE") })
            vm.demo(false);ready(vm);vm.analyze();runCurrent()
            change(vm);advanceUntilIdle()
            assertFalse(finished);assertNull(vm.state.value.evaluation)
            assertFalse(vm.state.value.confirmed);assertEquals(Screen.INPUT,vm.state.value.screen)
        }
    }
    @Test fun newAssessmentClearsHealthText(){val vm=TriageViewModel();ready(vm);vm.danger(true);vm.reset();assertEquals("",vm.state.value.symptoms);assertFalse(vm.state.value.immediateDanger)}
}
