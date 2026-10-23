package com.vitaltriage.app

import com.vitaltriage.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class WalkieMessageTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun cleanup(){Dispatchers.resetMain()}
    @Test fun capturedTurnsRemainSeparateAndOnlySelectedOriginalIsAssessed()=runTest {
        var sent:TriageRequest?=null
        val vm=TriageViewModel(AssessmentRepository { r,_,_->sent=r;Evaluation(Safety.unknown(r.language),"LIVE") })
        vm.demo(false)
        vm.utterance("First synthetic symptom","en")
        vm.confirm(true);vm.danger(true)
        vm.utterance("Deuxième exemple synthétique","fr","Second synthetic example",engine="nllb")
        assertEquals("Deuxième exemple synthétique",vm.state.value.symptoms)
        assertEquals(1,vm.state.value.messages.size)
        assertFalse(vm.state.value.confirmed);assertFalse(vm.state.value.immediateDanger);assertNull(sent)
        vm.review();vm.confirm(true);vm.analyze();advanceUntilIdle()
        assertEquals("Deuxième exemple synthétique",sent!!.symptoms)
        vm.selectMessage(vm.state.value.messages.single().id)
        assertEquals("First synthetic symptom",vm.state.value.symptoms)
        assertNull(vm.state.value.evaluation);assertFalse(vm.state.value.confirmed)
        assertEquals("Deuxième exemple synthétique",vm.state.value.messages.single().original)
    }
    @Test fun invalidTurnKeepsExistingTextAndNeverCreatesHistory() {
        val vm=TriageViewModel();vm.text("Existing synthetic text")
        vm.utterance(" ","en");assertEquals("speechNoMatch",vm.state.value.speechError)
        vm.utterance("x".repeat(4001),"en");assertEquals("speechTooLong",vm.state.value.speechError)
        vm.utterance("Synthetic","xx");assertEquals("speechLanguageUnsupported",vm.state.value.speechError)
        assertEquals("Existing synthetic text",vm.state.value.symptoms);assertTrue(vm.state.value.messages.isEmpty())
    }
    @Test fun messageSelectionDuringListeningCannotChangeCase() {
        val vm=TriageViewModel();vm.utterance("First","en");vm.utterance("Second","tr")
        val id=vm.state.value.messages.single().id
        vm.listening(true);vm.selectMessage(id)
        assertEquals("Second",vm.state.value.symptoms)
    }
    @Test fun editsDoNotMutateArchivedOriginalAndResetErasesSession() {
        val vm=TriageViewModel();vm.utterance("First","en");vm.utterance("Second","ar","Translation")
        vm.text("Edited second")
        assertEquals("First",vm.state.value.messages.single().original)
        assertNull(vm.state.value.speechTranslation)
        vm.reset();assertTrue(vm.state.value.messages.isEmpty());assertEquals("",vm.state.value.symptoms)
    }
    @Test fun historyHasBothCountAndCharacterBoundsAndStableOrder() {
        val messages=(0L..30L).map { WalkieMessage(it,"a".repeat(4000),"en","b".repeat(4000)) }
        val bounded=boundedMessages(messages.reversed())
        assertEquals(listOf(26L,27L,28L,29L,30L),bounded.map { it.id })
        assertTrue(bounded.sumOf { it.original.length+it.translation!!.length }<=40000)
        assertEquals(20,boundedMessages((0L..30L).map { WalkieMessage(it,"x","en") }).size)
    }
}
