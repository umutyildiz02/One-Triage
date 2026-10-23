package com.vitaltriage.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A readiness hint only; NLLB verifies all checksums on its inference worker. */
@Composable
internal fun TranslationReadiness(state:UiState,prepare:()->Unit) {
    val context=LocalContext.current
    val prefs=UserPreferences(context)
    val engine=prefs.translationEngine
    val enabled=prefs.englishVoiceTranslation
    val target=prefs.translationTarget.tag
    val reason by produceState("translationFailed",state.currentMessageLanguage,target,engine,enabled) {
        value=withContext(Dispatchers.IO) {
            when {
                !enabled -> "translationDisabled"
                state.currentMessageLanguage==target -> "translationFailed"
                engine=="nllb" && !NllbModels.artifacts.all {
                    java.io.File(NllbModels.directory(context),it.name).let { file->file.isFile && file.length()==it.bytes }
                } -> "nllbModelsMissing"
                engine=="mlkit" -> try {
                    val languages=setOf(state.currentMessageLanguage,target)-"en"
                    if(languages.any { it !in LocalTextTranslation.languages })"translationEngineUnsupported"
                    else if(languages.any { !LocalTextTranslation.installed(it) })"mlkitModelsMissing"
                    else "translationFailed"
                } catch(_:Exception) { "mlkitModelsMissing" }
                else -> "translationFailed"
            }
        }
    }
    Text(UiCopy.text(reason,state.language))
    TextButton(onClick=prepare,modifier=Modifier.testTag("prepare-offline-translation")) {
        Text(UiCopy.text("prepareTranslation",state.language))
    }
}
