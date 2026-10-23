package com.vitaltriage.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.AttributeSet
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.preference.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vitaltriage.core.Language
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LegacySettingsActivity : LegacyProfileActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.vv_activity_settings)
        setActionBar(findViewById<Toolbar>(R.id.toolbarSettings))
        actionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_settings_container, LegacySettingsFragment()).commit()
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
    override fun refreshProfile() {
        findViewById<ImageView>(R.id.user_image_settings)?.let(::bindProfile)
    }
}

class ProfileImagePreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.user_image_settings) as ImageView).apply {
            bindProfile(this)
            setOnClickListener { (context as? LegacyProfileActivity)?.selectProfileImage() }
        }
    }
}

class ProfileNamePreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.username) as TextView).text = UserPreferences(context).nickname
        val edit = { LegacyDialogs.name(context) { notifyChanged() } }
        holder.itemView.setOnClickListener { edit() }
        holder.findViewById(R.id.editNameButton)?.apply {
            contentDescription = "Edit name"
            setOnClickListener { edit() }
        }
    }
}

class LegacySettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = UserPreferences.FILE
        setPreferencesFromResource(R.xml.vv_preferences, rootKey)
        findPreference<Preference>("ShowOriginalTranscriptionMsgPreference")?.isVisible = false
        // Only expose recording controls when this app owns the capture pipeline.
        listOf("micSensibilitySetting", "SpeechTimeoutSetting", "PrevVoiceDurationSetting").forEach { key ->
            findPreference<Preference>(key)?.isVisible = false
        }
        val capturePrefs=UserPreferences(requireContext())
        findPreference<SwitchPreferenceCompat>("languagesNNQualityLow")?.apply {
            isPersistent=false
            isChecked=capturePrefs.languagesNNQualityLow
            summary=getString(R.string.vv_catalog_quality_summary)
            setOnPreferenceChangeListener { _,value ->
                capturePrefs.languagesNNQualityLow=value as Boolean
                true
            }
        }
        findPreference<SwitchPreferenceCompat>("languagesQualityLow")?.apply {
            isPersistent=false
            isChecked=capturePrefs.languagesQualityLow
            summary=getString(R.string.vv_tts_quality_summary)
            setOnPreferenceChangeListener { _,value ->
                capturePrefs.languagesQualityLow=value as Boolean
                true
            }
        }
        findPreference<SwitchPreferenceCompat>("sourceCapture")?.apply {
            isPersistent=false
            isEnabled=android.os.Build.VERSION.SDK_INT>=33
            isChecked=capturePrefs.sourceCapture
            setOnPreferenceChangeListener { _,value ->
                capturePrefs.sourceCapture=value as Boolean
                onCreatePreferences(null,null)
                true
            }
        }
        findPreference<SwitchPreferenceCompat>("offlineWhisper")?.apply {
            isPersistent=false
            isChecked=capturePrefs.offlineWhisper
            isEnabled=WhisperEngine.bundled(requireContext())
            if(!isEnabled)summary=getString(R.string.vv_model_missing)
            setOnPreferenceChangeListener { _,value->
                capturePrefs.offlineWhisper=value as Boolean
                onCreatePreferences(null,null)
                true
            }
        }
        findPreference<ListPreference>("whisperModel")?.apply {
            isPersistent=false
            val available=WhisperModel.entries
            entries=available.map { it.label }.toTypedArray()
            entryValues=available.map { it.key }.toTypedArray()
            value=capturePrefs.whisperModel.key
            isEnabled=available.isNotEmpty()
            summary=capturePrefs.whisperModel.label+" — "+getString(R.string.vv_model_summary)
            setOnPreferenceChangeListener { _,chosen ->
                val selected=available.firstOrNull { it.key==chosen }
                if(selected==null)false else {
                    capturePrefs.whisperModel=selected
                    onCreatePreferences(null,null)
                    true
                }
            }
        }
        findPreference<Preference>("prepareWhisper")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(),ModelPreparationActivity::class.java)
                .putExtra("model",UserPreferences(requireContext()).whisperModel.key))
            true
        }
        val translationRow=findPreference<SwitchPreferenceCompat>("englishVoiceTranslation")!!
        translationRow.parent?.addPreference(ListPreference(requireContext()).apply {
            key="translationEngine";isPersistent=false
            title=getString(R.string.vv_translation_engine)
            entries=arrayOf("Google Translate · ML Kit","NLLB · CC-BY-NC-4.0")
            entryValues=arrayOf("mlkit","nllb")
            value=capturePrefs.translationEngine
            summary=entry
            order=translationRow.order
            isIconSpaceReserved=false
            setOnPreferenceChangeListener { _,chosen ->
                if(chosen=="nllb") {
                    AlertDialog.Builder(requireContext()).setTitle(R.string.vv_nllb_title)
                        .setMessage(R.string.vv_nllb_notice)
                        .setPositiveButton(android.R.string.ok) { _,_->capturePrefs.translationEngine="nllb";onCreatePreferences(null,null) }
                        .setNegativeButton(android.R.string.cancel,null).show()
                    false
                } else { capturePrefs.translationEngine="mlkit";onCreatePreferences(null,null);true }
            }
        })
        translationRow.parent?.addPreference(ListPreference(requireContext()).apply {
            key="translationTarget";isPersistent=false
            title=getString(R.string.vv_translation_target)
            entries=Language.entries.map { it.label }.toTypedArray()
            entryValues=Language.entries.map { it.tag }.toTypedArray()
            value=capturePrefs.translationTarget.tag
            summary=entry
            order=translationRow.order
            isIconSpaceReserved=false
            setOnPreferenceChangeListener { _,chosen ->
                val selected=Language.entries.firstOrNull { it.tag==chosen }
                if(selected==null)false else {
                    capturePrefs.translationTarget=selected
                    value=selected.tag;summary=selected.label;true
                }
            }
        })
        translationRow.parent?.addPreference(SwitchPreferenceCompat(requireContext()).apply {
            key="readTranslatedTurns";isPersistent=false
            title=getString(R.string.vv_read_translated_turns)
            summary=getString(R.string.vv_read_translated_turns_summary)
            order=translationRow.order;isIconSpaceReserved=false
            isChecked=capturePrefs.readTranslatedTurns
            isEnabled=capturePrefs.englishVoiceTranslation
            setOnPreferenceChangeListener { _,value->capturePrefs.readTranslatedTurns=value as Boolean;true }
        })
        findPreference<SwitchPreferenceCompat>("englishVoiceTranslation")?.apply {
            isPersistent=false
            isEnabled=true
            isChecked=capturePrefs.englishVoiceTranslation
            summary=getString(R.string.vv_translation_summary)
            setOnPreferenceChangeListener { _,value ->
                capturePrefs.englishVoiceTranslation=value as Boolean
                findPreference<SwitchPreferenceCompat>("readTranslatedTurns")?.isEnabled=value
                true
            }
        }
        findPreference<Preference>("translationModels")?.apply {
            if(capturePrefs.translationEngine=="nllb")summary=getString(R.string.vv_nllb_notice)
            setOnPreferenceClickListener {
                if(capturePrefs.translationEngine=="nllb") {
                    startActivity(Intent(requireContext(),ModelPreparationActivity::class.java).putExtra("model","nllb"))
                    return@setOnPreferenceClickListener true
                }
                val requested=(capturePrefs.patientLanguages.map { it.tag }+capturePrefs.translationTarget.tag).toSet()-"en"
                val unsupported=requested-LocalTextTranslation.languages.toSet()
                if(unsupported.isNotEmpty()) {
                    AlertDialog.Builder(requireContext()).setMessage(UiCopy.text("translationEngineUnsupported",capturePrefs.language)+" "+unsupported.joinToString())
                        .setPositiveButton(android.R.string.ok,null).show()
                    return@setOnPreferenceClickListener true
                }
                AlertDialog.Builder(requireContext()).setTitle(R.string.vv_translation_prepare)
                    .setMessage("Prepare selected ML Kit languages: "+requested.joinToString()+". About 30 MB each over Wi-Fi. Text remains on-device; Google may receive SDK metrics. Review the original; translation is not clinically validated. Downloads may continue if this screen closes. No OpenAI request is made.")
                    .setPositiveButton("Prepare") { _,_ ->
                        val application=requireContext().applicationContext
                        val row=this
                        viewLifecycleOwner.lifecycleScope.launch {
                            row.isEnabled=false
                            try {
                                for(language in requested) {
                                    row.summary="Preparing "+language+"…"
                                    withContext(Dispatchers.IO) { LocalTextTranslation.prepare(application,language) }
                                }
                                row.summary="Ready: "+requested.joinToString()+". Review automatic translations."
                            } catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
                              catch(_:Exception) { row.summary="Preparation not complete. Check Wi-Fi and available storage; retry explicitly. Existing models/text are preserved." }
                            finally { row.isEnabled=true }
                        }
                    }.setNegativeButton(android.R.string.cancel,null).show()
                true
            }
        }
        if(capturePrefs.sourceCapture || capturePrefs.offlineWhisper) {
            listOf("micSensibilitySetting","SpeechTimeoutSetting","PrevVoiceDurationSetting").forEach { key->
                val old=findPreference<Preference>(key)!!
                val group=old.parent!!
                val position=old.order
                group.removePreference(old)
                group.addPreference(CapturePreference(requireContext(),key).apply { order=position })
            }
        }
        findPreference<Preference>("language")?.apply {
            summary = UserPreferences(context).language.label + " — "+getString(R.string.vv_language_summary)
            setOnPreferenceClickListener {
                LegacyDialogs.language(requireContext(), R.string.vv_dialog_select_personal_language, UserPreferences(context).language,uiOnly=true) { value ->
                    UserPreferences(context).language = value
                    summary = value.label + " — "+getString(R.string.vv_language_summary)
                    requireActivity().recreate()
                }; true
            }
        }
        findPreference<Preference>("language")?.parent?.addPreference(ListPreference(requireContext()).apply {
            key="resultLanguage";isPersistent=false;isIconSpaceReserved=false
            title=UiCopy.text("resultLanguage",capturePrefs.language)
            entries=Language.entries.map { it.label }.toTypedArray()
            entryValues=Language.entries.map { it.tag }.toTypedArray()
            value=capturePrefs.resultLanguage.tag;summary=entry
            order=findPreference<Preference>("language")!!.order+1
            setOnPreferenceChangeListener { _,chosen ->
                val language=Language.find(chosen as? String)
                if(language==null)false else {capturePrefs.resultLanguage=language;value=language.tag;summary=language.label;true}
            }
        })
        findPreference<Preference>("conversation")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ConversationActivity::class.java)); true
        }
        findPreference<Preference>("tts")?.setOnPreferenceClickListener { LegacyDialogs.tts(requireContext()); true }
        findPreference<Preference>("patientLanguages")?.setOnPreferenceClickListener {
            val prefs = UserPreferences(requireContext())
            val languages = Language.entries
            val selected = prefs.patientLanguages.toMutableSet()
            AlertDialog.Builder(requireContext()).setTitle(R.string.vv_patient_languages)
                .setMultiChoiceItems(languages.map { it.label }.toTypedArray(), languages.map { it in selected }.toBooleanArray()) { dialog, position, checked ->
                    val language = languages[position]
                    if (checked) selected.add(language)
                    else if (selected.size > 1) selected.remove(language)
                    else (dialog as AlertDialog).listView.setItemChecked(position, true)
                }.setPositiveButton(android.R.string.ok) { _, _ -> prefs.patientLanguages = selected }
                .setNegativeButton(android.R.string.cancel, null).show()
            true
        }
        findPreference<Preference>("developer")?.parent?.let { group ->
            listOf(
                Triple("privacy",R.string.vv_privacy_title,"PRIVACY_AND_SAFETY.txt"),
                Triple("licenses",R.string.vv_licenses_title,"THIRD_PARTY_NOTICES.txt")
            ).forEach { (id,label,asset) ->
                group.addPreference(Preference(requireContext()).apply {
                    key=id;title=getString(label);isIconSpaceReserved=false
                    setOnPreferenceClickListener { LegacyDialogs.document(requireContext(),label,asset);true }
                })
            }
        }
        if(!BuildConfig.LOCAL_DEVELOPMENT) findPreference<Preference>("developer")?.apply {
            parent?.title="One Triage"
            title=if(BuildConfig.DIRECT_OPENAI) UiCopy.text("server",UserPreferences(requireContext()).language) else getString(R.string.vv_connection_settings)
            summary=if(BuildConfig.DIRECT_OPENAI) UiCopy.text("directConfiguration",UserPreferences(requireContext()).language) else getString(R.string.vv_connection_settings_summary)
        }
        findPreference<Preference>("developer")?.setOnPreferenceClickListener {
            requireActivity().setResult(Activity.RESULT_OK, Intent().putExtra("openDeveloper", true))
            requireActivity().finish()
            true
        }
    }
    override fun onResume() {
        super.onResume()
        val ready=WhisperEngine.bundled(requireContext())
        findPreference<SwitchPreferenceCompat>("offlineWhisper")?.apply {
            isEnabled=ready
            summary=getString(if(ready)R.string.vv_whisper_summary else R.string.vv_model_missing)
        }
    }
    private fun unavailable(key: String, titleRes: Int, reasonRes: Int) {
        val prefs=UserPreferences(requireContext())
        val reason=UiCopy.text("captureRequiresEngine",prefs.language)
        findPreference<Preference>(key)?.apply {
            title = getString(titleRes)
            summary = reason
            isPersistent = false
            setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext()).setTitle(title).setMessage(reason)
                    .setPositiveButton(UiCopy.text("enableSourceCapture",prefs.language)) { _,_->
                        if(android.os.Build.VERSION.SDK_INT>=33) {
                            prefs.sourceCapture=true
                            onCreatePreferences(null,null)
                        } else startActivity(Intent(requireContext(),ModelPreparationActivity::class.java).putExtra("model",prefs.whisperModel.key))
                    }
                    .setNeutralButton("Offline Whisper") { _,_->
                        if(WhisperEngine.bundled(requireContext())) {
                            prefs.offlineWhisper=true
                            onCreatePreferences(null,null)
                        } else startActivity(Intent(requireContext(),ModelPreparationActivity::class.java).putExtra("model",prefs.whisperModel.key))
                    }
                    .setNegativeButton(android.R.string.cancel,null).show()
                true
            }
        }
    }
    // Preserve the original NestedScrollView + preference row arrangement while allowing
    // PreferenceFragmentCompat to manage its own RecyclerView lifecycle.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val root = inflater.inflate(R.layout.vv_fragment_settings, container, false)
        val placeholder = root.findViewById<RecyclerView>(R.id.preference_recyclerview)
        val parent = placeholder.parent as ViewGroup
        val params = placeholder.layoutParams
        parent.removeView(placeholder)
        (listView.parent as? ViewGroup)?.removeView(listView)
        listView.layoutParams = params
        listView.isNestedScrollingEnabled = false
        parent.addView(listView)
        return root
    }
}
