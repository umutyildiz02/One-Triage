package com.vitaltriage.app

import android.content.Context
import android.os.Looper
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.TimeUnit

/**
 * The source speechtranslator project's source-language -> Translator -> text flow, with
 * explicit model preparation, original retention, bounded waits and deterministic client cleanup.
 * Input/output stay on-device. Google SDK may send usage/performance metrics; see app notice.
 */
object LocalTextTranslation {
    val languages:List<String> get()=com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages()
        .filter { it!="en" && com.vitaltriage.core.Language.find(it)!=null }
    private fun options(language:String,target:String="en")=TranslatorOptions.Builder().setSourceLanguage(language).setTargetLanguage(target).build()
    private fun worker(){check(Looper.myLooper()!=Looper.getMainLooper())}
    fun installed(language:String):Boolean {
        worker();require(language in languages)
        return Tasks.await(RemoteModelManager.getInstance().isModelDownloaded(
            TranslateRemoteModel.Builder(language).build()),10,TimeUnit.SECONDS)
    }
    /** Explicit setup action only. Never invoked by recognition or analysis callbacks. */
    fun prepare(context:Context,language:String) {
        worker();require(language in languages)
        if(installed(language))return
        check(context.filesDir.usableSpace>400_000_000L) { "Insufficient model storage" }
        val client=Translation.getClient(options(language))
        try {
            Tasks.await(client.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()),120,TimeUnit.SECONDS)
            check(installed(language))
        } finally { client.close() }
    }
    /** No download here: missing model is a visible optional-translation failure. */
    fun toEnglish(original:String,language:String):String = translate(original,language,"en")
    fun translate(original:String,source:String,target:String):String {
        worker();require(source in languages+"en" && target in languages+"en" && original.isNotBlank() && original.length<=4000)
        if(source==target)return original
        for(language in setOf(source,target)-"en")check(installed(language)) { "Translation model not prepared" }
        val client=Translation.getClient(options(source,target))
        try {
            val translated=Tasks.await(client.translate(original),20,TimeUnit.SECONDS)
            check(translated.isNotBlank() && translated.length<=8000)
            return translated
        } finally { client.close() }
    }
}
