/*
 * Adapted from RTranslator SeekBarPreference.java, Copyright 2016 Luca Martino.
 * Apache License 2.0, http://www.apache.org/licenses/LICENSE-2.0
 * Distributed AS IS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */
package com.vitaltriage.app
import android.content.Context
import android.widget.ImageButton
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import java.util.Locale

class CapturePreference(context:Context,preferenceKey:String):Preference(context) {
    init { key=preferenceKey;layoutResource=R.layout.vv_preference_seekbar;isPersistent=false;isIconSpaceReserved=false }
    override fun onBindViewHolder(holder:PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val prefs=UserPreferences(context)
        val slider=holder.findViewById(R.id.seekBar) as Slider
        val label=holder.findViewById(R.id.textViewValue) as TextView
        val titleView=holder.findViewById(R.id.title) as TextView
        val summary=holder.findViewById(R.id.summary) as TextView
        val reset=holder.findViewById(R.id.buttonRestore) as ImageButton
        val sensitivity=key=="micSensibilitySetting"
        val silence=key=="SpeechTimeoutSetting"
        titleView.setText(if(sensitivity) R.string.vv_preference_title_mic_sensitivity else if(silence) R.string.vv_preference_title_speech_timeout else R.string.vv_preference_title_prev_voice_duration)
        summary.setText(if(sensitivity) R.string.vv_preference_description_mic_sensitivity else if(silence) R.string.vv_preference_description_speech_timeout else R.string.vv_preference_description_prev_voice_duration)
        slider.clearOnChangeListeners();slider.clearOnSliderTouchListeners()
        slider.valueFrom=if(sensitivity)0f else 0.1f
        slider.valueTo=if(sensitivity)100f else if(silence)5f else 1.8f
        slider.stepSize=if(sensitivity)1f else 0f
        fun show(value:Float){label.text=if(sensitivity)value.toInt().toString() else String.format(Locale.US,"%.2f s",value)}
        fun save(value:Float) {
            when { sensitivity->prefs.micSensitivity=value.toInt();silence->prefs.silenceMs=(value*1000).toInt();else->prefs.preRollMs=(value*1000).toInt() }
        }
        slider.value=if(sensitivity)prefs.micSensitivity.toFloat() else (if(silence)prefs.silenceMs else prefs.preRollMs)/1000f
        slider.contentDescription=titleView.text
        show(slider.value)
        slider.addOnChangeListener { _,value,fromUser->show(value);if(fromUser)save(value) }
        reset.contentDescription="Reset "+titleView.text
        reset.setOnClickListener { slider.value=if(sensitivity)50f else 1.3f;save(slider.value) }
    }
}
