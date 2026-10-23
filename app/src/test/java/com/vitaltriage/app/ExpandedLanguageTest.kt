package com.vitaltriage.app
import com.vitaltriage.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ExpandedLanguageTest {
    @Before fun before(){Dispatchers.setMain(StandardTestDispatcher())}
    @After fun after(){Dispatchers.resetMain()}
    @Test fun uiSpeechTargetAndResultAreIndependentAndConsentKeepsOriginal()=runTest {
        var sent:TriageRequest?=null
        val vm=TriageViewModel(AssessmentRepository { r,_,_->sent=r;Evaluation(Safety.unknown(r.language),"LIVE") })
        vm.language(Language.TR);vm.resultLanguage(Language.JA);vm.demo(false)
        vm.toggleLanguage(Language.DE);vm.activeLanguage(Language.DE)
        vm.utterance("Synthetischer Test: Ich habe kein Fieber.","de","Synthetic test: I have no fever.",engine="nllb",target="en")
        assertEquals(Language.TR,vm.state.value.language)
        assertEquals(Language.JA,vm.state.value.resultLanguage)
        assertEquals("en",vm.state.value.translationTarget)
        vm.review();vm.analyze();advanceUntilIdle();assertNull(sent)
        vm.confirm(true);vm.analyze();advanceUntilIdle()
        assertEquals("ja",sent!!.language);assertEquals(listOf("de"),sent!!.patientLanguages)
        assertEquals("Synthetischer Test: Ich habe kein Fieber.",sent!!.symptoms)
        vm.resultLanguage(Language.BG);assertFalse(vm.state.value.confirmed);assertNull(vm.state.value.evaluation)
    }
    @Test fun providerFailureUsesInterfaceCopyWithoutInventingAnAssessmentInTheRequestedLanguage()=runTest {
        val vm=TriageViewModel(AssessmentRepository { _,_,_->throw BackendFailure("OPENAI_WRONG_LANGUAGE") })
        vm.language(Language.TR);vm.resultLanguage(Language.DE);vm.demo(false)
        vm.utterance("Synthetic original sufficiently long", "de", target="de")
        vm.review();vm.confirm(true);vm.analyze();advanceUntilIdle()
        val failed=vm.state.value.evaluation!!
        assertTrue(failed.failed);assertEquals(Urgency.UNDETERMINED,failed.result.urgency)
        assertEquals("tr",failed.result.language);assertEquals(Words.unknown("tr"),failed.result.assessment)
        assertEquals(Language.DE,vm.state.value.resultLanguage)
        assertEquals("OPENAI_WRONG_LANGUAGE",vm.state.value.backendFailure)
    }
    @Test fun allRegistryCodesHaveSafeUiLookupAndSingleTargetPresentation() {
        Language.entries.forEach { language ->
            assertTrue(UiCopy.text("confirm",language).isNotBlank())
            val vm=TriageViewModel();vm.resultLanguage(language)
            vm.utterance("Synthetic original",language.tag,"Synthetic target",engine="nllb",target="en")
            assertEquals(language.tag,vm.state.value.currentMessageLanguage)
            assertNull(vm.state.value.speechError)
            val turn=WalkieMessage(1,"Synthetic original",language.tag,"Synthetic target",translationTarget="en")
            assertEquals("Synthetic target",turn.targetText())
            assertEquals(language,ConversationProtocol.parseText(ConversationProtocol.text("Synthetic peer",language)).language)
        }
        val vm=TriageViewModel();vm.utterance("Synthetic invalid input","xx")
        assertEquals("speechLanguageUnsupported",vm.state.value.speechError)
        assertTrue(vm.state.value.symptoms.isEmpty())
    }
}
