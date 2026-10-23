package com.vitaltriage.app

import com.vitaltriage.core.Language
import org.junit.Assert.*
import org.junit.Test

class SpeechLanguagePolicyTest {
    @Test fun automaticAcceptsRealSupportedDetectionOutsideFavorites() {
        for(language in listOf(Language.TR,Language.EN,Language.DE,Language.FR,Language.AR))
            assertEquals(language,SpeechLanguagePolicy.resolve(true,Language.EN,language.speechTag,true))
    }
    @Test fun manualIgnoresProviderDetectionAndKeepsExplicitLanguage() {
        for(language in listOf(Language.TR,Language.EN,Language.DE))
            assertEquals(language,SpeechLanguagePolicy.resolve(false,language,"fr",true))
    }
    @Test fun automaticNeverGuessesMissingUnknownOrUnsupportedLanguage() {
        assertNull(SpeechLanguagePolicy.resolve(true,Language.TR,null,true))
        assertNull(SpeechLanguagePolicy.resolve(true,Language.EN,"unknown-ZZ",true))
        val typedOnly=Language.entries.first { it.whisperTag==null }
        assertNull(SpeechLanguagePolicy.resolve(true,Language.EN,typedOnly.tag,true))
        assertEquals(typedOnly,SpeechLanguagePolicy.resolve(true,Language.EN,typedOnly.tag,false))
    }
}
