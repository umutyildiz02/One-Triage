package com.vitaltriage.app

import android.content.Context
import com.vitaltriage.core.Language

/** Only user preferences belong here. Never persist a UiState, transcript, result or token. */
class UserPreferences(context: Context) {
    internal val backendAccess = BackendAccessStore(context.applicationContext)
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    var setupComplete: Boolean
        get() = prefs.getBoolean("setup_complete", false)
        set(value) { prefs.edit().putBoolean("setup_complete", value).apply() }
    var nickname: String
        get() = prefs.getString("nickname", "") ?: ""
        set(value) { prefs.edit().putString("nickname", value.trim().take(18)).apply() }
    var language: Language
        get() = decode(prefs.getString("language", null)) ?: Language.EN
        set(value) {
            require(value in Language.uiLanguages)
            val edit=prefs.edit()
            if(!prefs.contains("result_language"))edit.putString("result_language",language.tag)
            edit.putString("language",value.tag).apply()
        }
    /** Missing new key preserves the prior combined interface/result choice without rewriting data. */
    var resultLanguage: Language
        get() = decode(prefs.getString("result_language",null)) ?: language
        set(value) { prefs.edit().putString("result_language",value.tag).apply() }
    var patientLanguages: Set<Language>
        get() = prefs.getStringSet("patient_languages", null)?.mapNotNull(::decode)?.toSet()
            ?.takeIf { it.isNotEmpty() } ?: setOf(Language.EN)
        set(value) {
            require(value.isNotEmpty())
            prefs.edit().putStringSet("patient_languages", value.map { it.tag }.toSet()).apply()
        }
    var activeSpeech: Language
        get() = decode(prefs.getString("active_speech", null))?.takeIf { it in patientLanguages }
            ?: patientLanguages.first()
        set(value) {
            patientLanguages = patientLanguages + value
            prefs.edit().putString("active_speech", value.tag).apply()
        }
    var autoLanguage: Boolean
        get() = prefs.getBoolean("auto_language", true)
        set(value) { prefs.edit().putBoolean("auto_language", value).apply() }
    var demo: Boolean
        get() = BuildConfig.LOCAL_DEVELOPMENT && prefs.getBoolean("offline_demo", true)
        set(value) { prefs.edit().putBoolean("offline_demo", value).apply() }
    var endpoint: String
        get() = prefs.getString("backend_endpoint", BuildConfig.DEFAULT_ENDPOINT)
            ?.takeIf { BackendConnectionPolicy.permits(it,BuildConfig.LOCAL_DEVELOPMENT) }
            ?: BuildConfig.DEFAULT_ENDPOINT
        set(value) {
            // Never store URL-embedded credentials or query tokens.
            if (BackendConnectionPolicy.permits(value,BuildConfig.LOCAL_DEVELOPMENT))
                prefs.edit().putString("backend_endpoint", value.trim()).apply()
        }
    // Original RTranslator preference keys. These change catalogs, not model weights.
    var languagesNNQualityLow:Boolean
        get()=prefs.getBoolean("languagesNNQualityLow",false)
        set(value){prefs.edit().putBoolean("languagesNNQualityLow",value).apply()}
    var languagesQualityLow:Boolean
        get()=prefs.getBoolean("languagesQualityLow",false)
        set(value){prefs.edit().putBoolean("languagesQualityLow",value).apply()}
    var offlineWhisper:Boolean
        get()=prefs.getBoolean("offline_whisper",false)
        set(value){prefs.edit().putBoolean("offline_whisper",value).apply()}
    var whisperModel:WhisperModel
        get()=WhisperModel.fromKey(prefs.getString("whisper_model",null))
        set(value){prefs.edit().putString("whisper_model",value.key).apply()}
    var englishVoiceTranslation:Boolean
        get()=prefs.getBoolean("english_voice_translation",false)
        set(value){prefs.edit().putBoolean("english_voice_translation",value).apply()}
    var readTranslatedTurns:Boolean
        get()=prefs.getBoolean("read_translated_turns",false)
        set(value){prefs.edit().putBoolean("read_translated_turns",value).apply()}
    var translationTarget:Language
        get()=decode(prefs.getString("translation_target",null)) ?: Language.EN
        set(value){prefs.edit().putString("translation_target",value.tag).apply()}
    var translationEngine:String
        get()=prefs.getString("translation_engine","mlkit")?.takeIf { it in setOf("mlkit","nllb") } ?: "mlkit"
        set(value){require(value in setOf("mlkit","nllb"));prefs.edit().putString("translation_engine",value).apply()}
    var sourceCapture:Boolean
        get() = android.os.Build.VERSION.SDK_INT>=33 && prefs.getBoolean("source_capture",false)
        set(value) { prefs.edit().putBoolean("source_capture",value).apply() }
    var micSensitivity:Int
        get()=prefs.getInt("mic_sensitivity",50).coerceIn(0,100)
        set(value){prefs.edit().putInt("mic_sensitivity",value.coerceIn(0,100)).apply()}
    var silenceMs:Int
        get()=prefs.getInt("silence_ms",1300).coerceIn(100,5000)
        set(value){prefs.edit().putInt("silence_ms",value.coerceIn(100,5000)).apply()}
    var preRollMs:Int
        get()=prefs.getInt("pre_roll_ms",1300).coerceIn(100,1800)
        set(value){prefs.edit().putInt("pre_roll_ms",value.coerceIn(100,1800)).apply()}
    fun captureSettings()=CaptureSettings(micSensitivity,silenceMs,preRollMs)
    fun initialStage(): String = if (nickname.isBlank()) "notice" else "preparation"
    companion object {
        const val FILE = "vitalvoice_user"
        private fun decode(tag: String?) = Language.entries.firstOrNull { it.tag == tag }
    }
}
