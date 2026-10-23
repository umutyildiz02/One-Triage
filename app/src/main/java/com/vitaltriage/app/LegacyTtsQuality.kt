package com.vitaltriage.app

/** RTranslator tools/TTS.java filter (Apache-2.0, Luca Martino).
 * Voice.QUALITY_NORMAL=300; QUALITY_VERY_LOW=100. No engine/model replacement.
 * Offline/installed restrictions are a deliberate VitalTriage privacy safeguard.
 */
object LegacyTtsQuality {
    fun allows(quality:Int, features:Set<String>, network:Boolean, allowLow:Boolean):Boolean =
        !network && "notInstalled" !in features &&
            quality >= (if(allowLow)100 else 300) &&
            (allowLow || "legacySetLanguageVoice" !in features)
}
