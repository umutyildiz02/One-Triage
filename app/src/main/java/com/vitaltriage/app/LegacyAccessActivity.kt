package com.vitaltriage.app

import android.content.Intent
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

class LegacyLoadingActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.vv_activity_loading)
        val destination = if (UserPreferences(this).setupComplete) MainActivity::class.java else LegacyAccessActivity::class.java
        startActivity(Intent(this, destination))
        finish()
    }
}

class LegacyAccessActivity : LegacyProfileActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (UserPreferences(this).setupComplete) { openHome(); return }
        setContentView(R.layout.vv_activity_access)
        if (savedInstanceState == null) showStage(UserPreferences(this).initialStage())
        onBackPressedDispatcher.addCallback(this) {
            when ((supportFragmentManager.findFragmentById(R.id.fragment_initialization_container) as? LegacyAccessFragment)?.stage) {
                "preparation" -> showStage("profile")
                "profile" -> showStage("notice")
                else -> finish()
            }
        }
    }
    fun showStage(stage: String) {
        supportFragmentManager.beginTransaction().setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_initialization_container, LegacyAccessFragment().apply {
                arguments = Bundle().apply { putString("stage", stage) }
            }).commit()
    }
    fun openHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    override fun refreshProfile() {
        findViewById<ImageView>(R.id.user_image_initialization)?.let(::bindProfile)
    }
}

class LegacyAccessFragment : Fragment() {
    val stage get() = requireArguments().getString("stage") ?: "notice"
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(when (stage) {
            "profile" -> R.layout.vv_fragment_user_data
            "preparation" -> R.layout.vv_fragment_download
            else -> R.layout.vv_fragment_notice
        }, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as LegacyAccessActivity
        val prefs = UserPreferences(host)
        when (stage) {
            "profile" -> {
                val field = view.findViewById<EditText>(R.id.input_name)
                field.filters = arrayOf(InputFilter.LengthFilter(18))
                if (savedInstanceState == null) field.setText(prefs.nickname)
                view.findViewById<ImageView>(R.id.user_image_initialization).apply {
                    bindProfile(this); setOnClickListener { host.selectProfileImage() }
                }
                view.findViewById<View>(R.id.buttonChangeTTS).setOnClickListener { LegacyDialogs.tts(host) }
                view.findViewById<View>(R.id.buttonConfirm).setOnClickListener {
                    val privacy = view.findViewById<CheckBox>(R.id.checkBoxPrivacy)
                    when {
                        field.text.toString().isBlank() -> field.error = getString(R.string.vv_error_missing_username)
                        !privacy.isChecked -> { privacy.error = getString(R.string.vv_notice_accept); privacy.requestFocus() }
                        else -> {
                            prefs.nickname = field.text.toString()
                            (host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                                .hideSoftInputFromWindow(field.windowToken, 0)
                            host.showStage("preparation")
                        }
                    }
                }
            }
            "preparation" -> {
                // Bundled weights are verified on first use; this screen performs no download.
                listOf(R.id.progressBar, R.id.pauseButton, R.id.progress_numbers, R.id.retryButton,
                    R.id.text_error_download, R.id.text_error_transfer, R.id.text_error_storage).forEach {
                    view.findViewById<View>(it).visibility = View.GONE
                }
                view.findViewById<TextView>(R.id.progress_description).apply {
                    text = if (WhisperEngine.bundled(host))
                        getString(R.string.vv_ready_whisper)
                    else if (SpeechRecognizer.isRecognitionAvailable(host))
                        getString(R.string.vv_ready_provider)
                    else getString(R.string.vv_ready_typed)
                }
                view.findViewById<View>(R.id.buttonConfirm).setOnClickListener {
                    prefs.setupComplete = true
                    host.openHome()
                }
            }
            else -> view.findViewById<View>(R.id.buttonConfirm).setOnClickListener { host.showStage("profile") }
        }
    }
}
