package com.vitaltriage.app

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.vitaltriage.core.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Source catalog and row XML. Interface localization is independent of processing capability. */
object LegacyDialogs {
    fun language(context: Context, title: Int, selected: Language, uiOnly:Boolean=false, onSelect: (Language) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.vv_dialog_languages, null)
        view.findViewById<View>(R.id.progressBar3).visibility = View.GONE
        val list = view.findViewById<ListView>(R.id.list_view_dialog)
        list.visibility = View.VISIBLE
        // Model capability is not model readiness or proof of translation quality.
        val displayLocale=context.resources.configuration.locales[0]
        val languages = (if(uiOnly)Language.uiLanguages.map { it.tag }.toSet() else LegacyLanguageCatalog.tags(UserPreferences(context).languagesNNQualityLow))
            .sortedWith(compareBy<String> { !LegacyLanguageCatalog.selectable(it) }.thenBy { java.util.Locale.forLanguageTag(it).getDisplayLanguage(displayLocale) })
        list.adapter = object : ArrayAdapter<String>(context, R.layout.vv_component_row_language, languages) {
            override fun areAllItemsEnabled() = false
            override fun isEnabled(position:Int) = LegacyLanguageCatalog.selectable(languages[position])
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: LayoutInflater.from(context).inflate(R.layout.vv_component_row_language, parent, false)
                val tag = languages[position]
                val language = Language.entries.firstOrNull { it.tag==tag }
                val label = language?.label ?: java.util.Locale.forLanguageTag(tag).getDisplayLanguage(displayLocale)
                row.findViewById<TextView>(R.id.languageName).text = label +
                    if(language==null) " — "+context.getString(R.string.vv_catalog_unsupported) else if(!uiOnly && language.whisperTag==null) " — "+UiCopy.text("writtenOnly",UserPreferences(context).language) else ""
                row.isEnabled = language != null
                row.alpha = if(language==null).5f else 1f
                row.findViewById<View>(R.id.isSelected).visibility = if (language == selected) View.VISIBLE else View.GONE
                row.contentDescription = label + if (language == selected) ", selected" else ""
                return row
            }
        }
        val dialog = AlertDialog.Builder(context).setTitle(title).setView(view).create()
        list.setOnItemClickListener { _, _, position, _ ->
            Language.entries.firstOrNull { it.tag==languages[position] }?.let { onSelect(it); dialog.dismiss() }
        }
        dialog.show()
    }

    fun name(context: Context, onSaved: () -> Unit) {
        val prefs = UserPreferences(context)
        val view = LayoutInflater.from(context).inflate(R.layout.vv_dialog_edit_name, null)
        val field = view.findViewById<EditText>(R.id.editText)
        field.setText(prefs.nickname)
        val dialog = AlertDialog.Builder(context).setTitle(R.string.vv_dialog_insert_name).setView(view)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = field.text.toString().trim()
                if (value.isBlank()) field.error = context.getString(R.string.vv_error_missing_username)
                else { prefs.nickname = value; onSaved(); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    fun document(context:Context,title:Int,asset:String) {
        require(asset in setOf("PRIVACY_AND_SAFETY.txt","THIRD_PARTY_NOTICES.txt"))
        val text=TextView(context).apply {
            val padding=(20*resources.displayMetrics.density).toInt()
            setPadding(padding,padding,padding,padding)
            textSize=16f
            setTextIsSelectable(true)
            text=context.assets.open(asset).bufferedReader().use { it.readText() }
            android.text.util.Linkify.addLinks(this,android.text.util.Linkify.WEB_URLS)
            movementMethod=android.text.method.LinkMovementMethod.getInstance()
        }
        AlertDialog.Builder(context).setTitle(title)
            .setView(ScrollView(context).apply { addView(text) })
            .setPositiveButton(android.R.string.ok,null).show()
    }

    fun tts(context: Context) {
        try { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
        catch (_: android.content.ActivityNotFoundException) {
            AlertDialog.Builder(context).setMessage("Device TTS settings are unavailable. The speaker requires an installed offline voice for the selected language; opening settings alone does not verify playback.")
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }
}

/** Only the explicitly selected local profile photo is stored. No broad gallery permission. */
abstract class LegacyProfileActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.light(android.graphics.Color.WHITE,android.graphics.Color.WHITE))
    }
    override fun attachBaseContext(newBase:Context) { super.attachBaseContext(LegacyLocale.wrap(newBase)) }
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
                    }
                    val bitmap = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
                        ?: error("Image unavailable")
                    val file = AtomicFile(File(filesDir, "profile.png"))
                    val output = file.startWrite()
                    try { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); file.finishWrite(output) }
                    catch (error: Exception) { file.failWrite(output); throw error }
                    finally { bitmap.recycle() }
                }.isSuccess
            }
            if (success) refreshProfile() else Toast.makeText(this@LegacyProfileActivity, "Unable to read that image.", Toast.LENGTH_LONG).show()
        }
    }
    fun selectProfileImage() {
        val exists = File(filesDir, "profile.png").exists()
        val choices = if (exists) arrayOf(getString(R.string.vv_menu_image_select_from_gallery), getString(R.string.vv_menu_image_remove))
            else arrayOf(getString(R.string.vv_menu_image_select_from_gallery))
        AlertDialog.Builder(this).setTitle(R.string.vv_title_image_selector).setItems(choices) { _, which ->
            if (which == 0) picker.launch("image/*")
            else { File(filesDir, "profile.png").delete(); refreshProfile() }
        }.show()
    }
    abstract fun refreshProfile()
}

fun bindProfile(image: ImageView) {
    // Framework tint is applied here so custom profile photos keep their original colors.
    val bitmap = BitmapFactory.decodeFile(File(image.context.filesDir, "profile.png").path)
    if (bitmap == null) {
        image.setImageResource(R.drawable.vv_user_icon)
        image.imageTintList = android.content.res.ColorStateList.valueOf(image.context.getColor(R.color.vv_primary_very_dark))
    } else {
        image.imageTintList = null
        image.setImageDrawable(RoundedBitmapDrawableFactory.create(image.resources, bitmap).apply { isCircular = true })
    }
    image.contentDescription = "Profile image"
}
