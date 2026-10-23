package com.vitaltriage.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase:android.content.Context) { super.attachBaseContext(LegacyLocale.wrap(newBase)) }
    private val vm: TriageViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                TriageViewModel(if (BuildConfig.DIRECT_OPENAI) DirectOpenAiRepository.personal(this@MainActivity)
                    else BackendRepository()) as T
        }
    }
    private lateinit var speech: SpeechController
    private lateinit var output: SpeechOutput
    private lateinit var turns: WalkieTurnLoop
    private val turnHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private val audioFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null && ::speech.isInitialized) {
            turns.stop()
            vm.navigate(Screen.INPUT)
            speech.importWave(uri)
        }
    }
    private val translationSetup = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.refreshPreferences()
        if(result.resultCode==RESULT_OK && ::speech.isInitialized && vm.state.value.symptoms.isNotBlank())
            speech.reviewWithTranslation()
    }
    private val settings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.refreshPreferences()
        if (result.resultCode == RESULT_OK && result.data?.getBooleanExtra("openDeveloper", false) == true)
            vm.navigate(Screen.SETTINGS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.light(android.graphics.Color.WHITE,android.graphics.Color.WHITE))
        val preferences = UserPreferences(this)
        if (!preferences.setupComplete) {
            startActivity(Intent(this, LegacyAccessActivity::class.java))
            finish()
            return
        }
        vm.attachPreferences(preferences)
        if (!BuildConfig.LOCAL_DEVELOPMENT) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        output = SpeechOutput(this, vm::speaking) { vm.speechError("ttsUnavailable") }
        turns=WalkieTurnLoop(
            capture={speech.start()},
            stopCapture={speech.close();vm.endListening()},
            play={text,language,done->output.play(text,language,done)},
            stopPlayback=output::stop,
            automaticChanged=vm::automaticSession,
            schedule={action->turnHandler.postDelayed({action()},350)})
        speech = SpeechController(this, vm, onUtterance={ completed ->
            if(completed.translationFailed)turns.stop()
            else {
                val target=com.vitaltriage.core.Language.entries.firstOrNull { it.tag==completed.translationTarget } ?: completed.activeSpeech
                turns.completed(completed.speechTranslation.orEmpty(),target,
                    UserPreferences(this).readTranslatedTurns && completed.speechTranslation!=null)
            }
        })
        lifecycleScope.launch {
            vm.state.collect { if(it.speechError!=null && turns.active)turns.stop() }
        }
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                vm.permission(granted)
                if (granted) turns.start(vm.state.value.autoLanguage) else turns.stop()
            }
            VitalTheme {
                VitalApp(state, vm,
                    onImportAudio = if(BuildConfig.LOCAL_DEVELOPMENT) ({
                        turns.stop()
                        audioFile.launch(arrayOf("audio/wav","audio/x-wav","audio/vnd.wave"))
                    }) else null,
                    onReview = { turns.stop(); speech.reviewWithTranslation() },
                    onPrepareTranslation = {
                        turns.stop()
                        val prefs=UserPreferences(this)
                        translationSetup.launch(Intent(this,ModelPreparationActivity::class.java)
                            .putExtra("model",prefs.translationEngine)
                            .putExtra("source",vm.state.value.currentMessageLanguage)
                            .putExtra("target",prefs.translationTarget.tag)
                            .putExtra("enableTranslation",true))
                    },
                    onSpeak = {
                        val wasSpeaking=vm.state.value.speaking
                        turns.stop()
                        val state=vm.state.value
                        val result=state.evaluation?.takeUnless { it.failed }?.result
                        val spoken=result?.assessment
                            ?: state.speechTranslation ?: state.symptoms
                        val spokenTag=if(state.speechTranslation!=null)state.translationTarget else state.currentMessageLanguage
                        val spokenLanguage=com.vitaltriage.core.Language.entries.firstOrNull { it.tag==spokenTag } ?: state.activeSpeech
                        if(!wasSpeaking)output.toggle(spoken, if(result!=null) state.resultLanguage else spokenLanguage)
                    },
                    onRecord = {
                        val current=vm.state.value
                        when {
                            current.automaticSession || current.speechProcessing -> turns.stop()
                            current.listening -> speech.stop()
                            else -> { turns.stop();permission.launch(Manifest.permission.RECORD_AUDIO) }
                        }
                    },
                    onStop = { turns.stop() },
                    onPermissionSettings = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
                    },
                    onSettings = {
                        turns.stop()
                        vm.listening(false)
                        settings.launch(Intent(this, LegacySettingsActivity::class.java))
                    },
                    onLanguage = {
                        turns.stop()
                        val context = android.view.ContextThemeWrapper(this, R.style.VitalLegacy)
                        LegacyDialogs.language(context, R.string.vv_dialog_select_first_language, vm.state.value.activeSpeech) { language ->
                            if (language !in vm.state.value.patientLanguages) vm.toggleLanguage(language)
                            vm.activeLanguage(language)
                        }
                    })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPreferences()
        if(resources.configuration.locales[0].language!=UserPreferences(this).language.tag) recreate()
    }

    override fun onStop() {
        if (::turns.isInitialized)turns.stop()
        turnHandler.removeCallbacksAndMessages(null)
        if (::speech.isInitialized) speech.releaseModel()
        if (::output.isInitialized) output.stop()
        if (vm.state.value.listening) vm.listening(false)
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        if(level==android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL && ::speech.isInitialized) {
            if(::turns.isInitialized)turns.stop()
            speech.releaseModel()
            vm.speechError("speechMemory")
        }
        super.onTrimMemory(level)
    }

    override fun onDestroy() {
        if (::speech.isInitialized) speech.destroy()
        if (::output.isInitialized) output.close()
        super.onDestroy()
    }
}
