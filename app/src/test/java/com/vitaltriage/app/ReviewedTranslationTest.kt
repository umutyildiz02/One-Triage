package com.vitaltriage.app

import com.vitaltriage.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewedTranslationTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun cleanup(){Dispatchers.resetMain()}
    @Test fun localTranslationRequiresFreshConsentAndAssessmentUsesOriginal()=runTest {
        var sent:TriageRequest?=null
        val vm=TriageViewModel(AssessmentRepository { r,_,_->sent=r;Evaluation(Safety.unknown(r.language),"LIVE") })
        vm.demo(false);vm.toggleLanguage(Language.TR);vm.activeLanguage(Language.TR)
        vm.text("Sentetik örnek: ağrım yok.");vm.review();vm.confirm(true)
        val snapshot=vm.state.value
        vm.translationStarted();vm.confirm(true);vm.analyze();advanceUntilIdle()
        assertFalse(vm.state.value.confirmed);assertNull(sent)
        vm.reviewedTranslation(snapshot.currentMessageId,snapshot.symptoms,"Synthetic: no pain.","en","nllb",false)
        assertFalse(vm.state.value.confirmed);assertFalse(vm.state.value.translationBusy)
        vm.confirm(true);vm.analyze();advanceUntilIdle()
        assertEquals(snapshot.symptoms,sent!!.symptoms)
        assertEquals("en",sent!!.language)
        assertEquals("tr",snapshot.currentMessageLanguage)
    }
    @Test fun editedOrCancelledReviewRejectsStaleTranslation() {
        val vm=TriageViewModel();vm.text("Original synthetic text");vm.review();vm.translationStarted()
        val old=vm.state.value
        vm.text("Edited synthetic text")
        vm.reviewedTranslation(old.currentMessageId,old.symptoms,"Stale","ar","nllb",false)
        assertNull(vm.state.value.speechTranslation)
        vm.review();vm.translationStarted();vm.translationCancelled()
        vm.reviewedTranslation(vm.state.value.currentMessageId,vm.state.value.symptoms,"Stale","fr","nllb",false)
        assertNull(vm.state.value.speechTranslation);assertFalse(vm.state.value.confirmed)
    }
    @Test fun translationFailureLeavesOriginalAvailableWithoutFabricatingOutput() {
        val vm=TriageViewModel();vm.text("Original");vm.review();vm.translationStarted()
        vm.reviewedTranslation(vm.state.value.currentMessageId,"Original",null,"ar","nllb",true)
        assertEquals("Original",vm.state.value.symptoms)
        assertTrue(vm.state.value.translationFailed);assertNull(vm.state.value.speechTranslation)
        assertFalse(vm.state.value.translationBusy);assertFalse(vm.state.value.confirmed)
    }
    @Test fun nextRecordingKeepsPriorTranslationAndSelectedTargetMetadata() {
        val vm=TriageViewModel();vm.utterance("Original one","en","ترجمة",engine="nllb",target="ar")
        vm.listening(true)
        assertEquals("ترجمة",vm.state.value.speechTranslation)
        vm.utterance("Original two","fr","İkinci",engine="nllb",target="tr")
        val archived=vm.state.value.messages.single()
        assertEquals("ar",archived.translationTarget)
        vm.selectMessage(archived.id)
        assertEquals("ar",vm.state.value.translationTarget)
        assertEquals("ترجمة",vm.state.value.speechTranslation)
    }
}
