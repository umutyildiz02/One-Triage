package com.vitaltriage.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario

/** Focused checks; no recording, downloads, normal-preference changes, API calls or screenshots. */
class HotfixTranslationTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun missingModelsOfferExplicitPreparation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val empty=java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(),"hotfix-empty-models-").toFile()
        val preferenceName="hotfix-readiness-"+System.nanoTime()
        val wrapped=object:android.content.ContextWrapper(context) {
            override fun getNoBackupFilesDir()=empty
            override fun getSharedPreferences(name:String,mode:Int)=context.getSharedPreferences(preferenceName,mode)
        }
        val prefs=UserPreferences(wrapped)
        prefs.translationEngine="nllb";prefs.englishVoiceTranslation=true
        var prepared=0
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent { VitalTheme {
                        CompositionLocalProvider(LocalContext provides wrapped) {
                            TranslationReadiness(UiState(currentMessageLanguage="tr"),{prepared++})
                        }
                    } }
                }
                val notice=UiCopy.text("nllbModelsMissing",com.vitaltriage.core.Language.EN)
                compose.waitUntil(10000){compose.onAllNodesWithText(notice).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(notice).assertIsDisplayed()
                compose.onNodeWithTag("prepare-offline-translation").assertIsDisplayed().performClick()
                assertEquals(1,prepared)
                assertTrue(empty.listFiles().orEmpty().isEmpty())
            }
        } finally { context.deleteSharedPreferences(preferenceName);empty.deleteRecursively() }
    }
    @Test fun preparedNllbTranslatesTurkishToEnglish() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=NllbModels.directory(context)
        assertTrue("Existing verified NLLB models required",NllbModels.verified(directory))
        ActivityScenario.launch(MainActivity::class.java).use {
        NllbEngine(directory).use { engine ->
            val source="Sentetik örnek: Yetişkin bir kişinin iki gündür boğazı ağrıyor, ateşi yok."
            val output=engine.translate(source,"tr","en")
            val normalized=output.lowercase(java.util.Locale.ROOT)
            assertTrue(normalized.contains("throat"))
            assertTrue(normalized.contains("fever"))
            assertTrue(Regex("\\b(no|not|without)\\b|n't").containsMatchIn(normalized))
            assertTrue(Regex("(two|2) days").containsMatchIn(normalized))
            assertNotEquals(source,output)
            println("HOTFIX_NLLB_SYNTHETIC="+output)
        }
        }
    }
    @Test fun sameLanguageRetainsOneOriginalWithoutTranslationModel() {
        val original="Ateşim yok."
        val message=WalkieMessage(1,original,"tr",translationTarget="tr",translationEngine="nllb")
        assertEquals(original,message.targetText())
        // The no-translation path in the Android engine also exits before looking up a model.
        assertEquals(original,LocalTextTranslation.translate(original,"tr","tr"))
        assertNull(message.copy(translationTarget="en").targetText())
    }
}
