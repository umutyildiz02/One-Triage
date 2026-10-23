package com.vitaltriage.app

import com.vitaltriage.core.Language

/** Manual recognition is fixed; automatic recognition requires an actual engine language result. */
internal object SpeechLanguagePolicy {
    fun resolve(automatic: Boolean, selected: Language, detected: String?, whisper: Boolean): Language? {
        val language = if (automatic) Language.fromSpeechTag(detected) else selected
        return language?.takeIf { !whisper || it.whisperTag != null }
    }
}
