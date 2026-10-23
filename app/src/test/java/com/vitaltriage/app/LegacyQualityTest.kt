package com.vitaltriage.app

import org.junit.Assert.*
import org.junit.Test

class LegacyQualityTest {
    @Test fun catalogPreservesQualityFilterAndEnablesMappedProcessingLanguages() {
        val normal=LegacyLanguageCatalog.tags(false)
        val extended=LegacyLanguageCatalog.tags(true)
        assertEquals(31,normal.size)
        assertEquals(71,extended.size)
        assertTrue(extended.containsAll(normal))
        assertFalse("Indonesian is commented out in original NLLB XML","id" in extended)
        assertFalse("Afrikaans is excluded by normal-quality intersection","af" in normal)
        assertTrue("Low-quality setting exposes Afrikaans in original catalog","af" in extended)
        assertTrue(LegacyLanguageCatalog.selectable("af"))
        assertFalse(LegacyLanguageCatalog.selectable("xx"))
        assertEquals(com.vitaltriage.core.Language.entries.map { it.tag }.toSet(),extended.filter(LegacyLanguageCatalog::selectable).toSet())
    }
    @Test fun normalQualityRejectsLegacyAndWeakVoices() {
        assertFalse(LegacyTtsQuality.allows(200,emptySet(),false,false))
        assertTrue(LegacyTtsQuality.allows(300,emptySet(),false,false))
        assertFalse(LegacyTtsQuality.allows(500,setOf("legacySetLanguageVoice"),false,false))
    }
    @Test fun lowQualitySettingActuallyAdmitsInstalledWeakAndLegacyVoices() {
        assertTrue(LegacyTtsQuality.allows(100,emptySet(),false,true))
        assertTrue(LegacyTtsQuality.allows(500,setOf("legacySetLanguageVoice"),false,true))
        assertFalse(LegacyTtsQuality.allows(99,emptySet(),false,true))
    }
    @Test fun qualityOptInNeverEnablesNetworkOrUninstalledVoices() {
        for(low in listOf(false,true)) {
            assertFalse(LegacyTtsQuality.allows(500,emptySet(),true,low))
            assertFalse(LegacyTtsQuality.allows(500,setOf("notInstalled"),false,low))
        }
    }
}
