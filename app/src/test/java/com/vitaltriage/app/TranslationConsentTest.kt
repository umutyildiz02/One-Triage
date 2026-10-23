package com.vitaltriage.app

import com.vitaltriage.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TranslationConsentTest {
    @Before fun setup(){Dispatchers.setMain(StandardTestDispatcher())}
    @After fun cleanup(){Dispatchers.resetMain()}
    @Test fun bothEnginesLeaveOriginalUnapprovedAndNeverSendTranslation()=runTest {
        for(engine in listOf("mlkit","nllb")) {
            var calls=0
            var received:TriageRequest?=null
            val vm=TriageViewModel(AssessmentRepository { request,_,_ ->
                calls++;received=request;Evaluation(Safety.unknown(request.language),"LIVE")
            })
            vm.demo(false)
            val original="Yapay örnek: iki gündür boğaz ağrısı, ateş yok."
            vm.transcript(original,"Deliberately different test translation",translationEngine=engine)
            assertEquals(engine,vm.state.value.translationEngine)
            assertEquals(original,vm.state.value.symptoms)
            vm.review();vm.analyze();advanceUntilIdle()
            assertEquals(0,calls);assertFalse(vm.state.value.confirmed)
            vm.confirm(true);vm.analyze();advanceUntilIdle()
            assertEquals(1,calls);assertEquals(original,received!!.symptoms)
            vm.text("Changed original")
            assertNull(vm.state.value.speechTranslation);assertNull(vm.state.value.evaluation)
            assertFalse(vm.state.value.confirmed)
        }
    }
    @Test fun failedOptionalTranslationCannotCreateOrKeepAssessment() {
        val vm=TriageViewModel()
        vm.text("Existing text");vm.confirm(true)
        vm.transcript("Additional original",translationFailed=true,translationEngine="nllb")
        assertEquals("Existing text\nAdditional original",vm.state.value.symptoms)
        assertFalse(vm.state.value.confirmed);assertNull(vm.state.value.evaluation)
        assertTrue(vm.state.value.translationFailed);assertNull(vm.state.value.speechTranslation)
    }
}
