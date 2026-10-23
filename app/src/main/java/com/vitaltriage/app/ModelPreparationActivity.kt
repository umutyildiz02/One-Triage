package com.vitaltriage.app

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import android.widget.ImageButton
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Original preparation layout, now driven by real cancellable model-byte progress. */
class ModelPreparationActivity : LegacyProfileActivity() {
    private var download: VerifiedModelDownload?=null
    private var work: Job?=null
    private var complete=false
    private val selected by lazy { intent.getStringExtra("model").orEmpty() }
    private val whisper by lazy { WhisperModel.entries.firstOrNull { it.key==selected } }
    private val nllb get()=selected=="nllb"
    private val mlkit get()=selected=="mlkit"
    private val source by lazy { intent.getStringExtra("source") ?: UserPreferences(this).activeSpeech.tag }
    private val target by lazy { intent.getStringExtra("target") ?: UserPreferences(this).translationTarget.tag }
    private val description get()=findViewById<TextView>(R.id.progress_description)
    private val confirm get()=findViewById<MaterialButton>(R.id.buttonConfirm)
    private val pause get()=findViewById<ImageButton>(R.id.pauseButton)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(!nllb && !mlkit && whisper==null) { finish();return }
        setContentView(R.layout.vv_fragment_download)
        listOf(R.id.text_error_download,R.id.text_error_transfer,R.id.text_error_storage,R.id.retryButton)
            .forEach { findViewById<View>(it).visibility=View.GONE }
        findViewById<TextView>(R.id.accessTitle).text=if(nllb)getString(R.string.vv_nllb_title) else if(mlkit)"ML Kit" else "Offline Whisper"
        findViewById<TextView>(R.id.downloadSummary).text=if(nllb)getString(R.string.vv_nllb_summary) else if(mlkit)"$source → $target · about 30 MB per language · Wi-Fi" else whisper!!.label
        description.text=if(mlkit)UiCopy.text("translationPrivacy",UserPreferences(this).language) else getString(if(nllb)R.string.vv_nllb_notice else R.string.vv_model_network_notice)
        showProgress(false)
        pause.contentDescription=getString(R.string.vv_model_pause)
        confirm.text=getString(R.string.vv_model_prepare)
        confirm.setOnClickListener { if(complete){setResult(RESULT_OK);finish()} else prepare() }
        pause.setOnClickListener { stop() }
        findViewById<View>(R.id.retryButton).setOnClickListener { prepare() }
    }
    private fun prepare() {
        if(work?.isCompleted==false)return
        val downloader=VerifiedModelDownload()
        download=downloader
        showProgress(true)
        findViewById<View>(R.id.retryButton).visibility=View.GONE
        description.text=getString(R.string.vv_model_preparing)
        work=lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    var last=0L
                    val progress:(Long,Long)->Unit={done,total->
                        val now=SystemClock.elapsedRealtime()
                        if(now-last>=250 || done==total) {
                            last=now
                            runOnUiThread {
                                if(download===downloader && !isFinishing && !isDestroyed) {
                                    findViewById<LinearProgressIndicator>(R.id.progressBar).progress=(done*100/total).toInt()
                                    findViewById<TextView>(R.id.progress_numbers).text="${done/1_000_000} / ${total/1_000_000} MB"
                                }
                            }
                        }
                    }
                    if(nllb)NllbModels.prepare(applicationContext,downloader,progress)
                    else if(mlkit) {
                        require(source in LocalTextTranslation.languages+"en" && target in LocalTextTranslation.languages+"en")
                        if(source!=target)for(language in setOf(source,target)-"en")LocalTextTranslation.prepare(applicationContext,language)
                        Unit
                    }
                    else downloader.prepare(File(noBackupFilesDir,"models"),whisper!!.artifact,progress)
                }
                if(intent.getBooleanExtra("enableTranslation",false) && (nllb || mlkit)) {
                    UserPreferences(this@ModelPreparationActivity).apply {
                        translationEngine=selected
                        englishVoiceTranslation=true
                    }
                }
                complete=true
                description.text=getString(R.string.vv_model_ready)
                confirm.text=getString(R.string.vv_forward)
            } catch(cancel:kotlinx.coroutines.CancellationException) {
                description.text=getString(R.string.vv_model_paused)
                throw cancel
            } catch(failure:Exception) {
                val issue=ModelSetupIssue.from(failure)
                android.util.Log.w("VTModelSetup","Preparation failed: "+issue.name)
                description.text=getString(when(issue) {
                    ModelSetupIssue.NETWORK -> R.string.vv_model_error_network
                    ModelSetupIssue.TIMEOUT -> R.string.vv_model_error_timeout
                    ModelSetupIssue.TLS -> R.string.vv_model_error_tls
                    ModelSetupIssue.STORAGE -> R.string.vv_model_error_storage
                    ModelSetupIssue.INTEGRITY -> R.string.vv_model_error_integrity
                    ModelSetupIssue.SERVER -> R.string.vv_model_error_server
                    ModelSetupIssue.OTHER -> R.string.vv_model_error
                })
                findViewById<View>(R.id.retryButton).visibility=View.VISIBLE
            } finally {
                download=null;showProgress(false)
            }
        }
    }
    private fun showProgress(running:Boolean) {
        confirm.visibility=if(running)View.GONE else View.VISIBLE
        findViewById<LinearProgressIndicator>(R.id.progressBar).isIndeterminate=mlkit && running
        if(mlkit)findViewById<TextView>(R.id.progress_numbers).text=""
        listOf(R.id.progressBar,R.id.progress_numbers,R.id.pauseButton).forEach {
            findViewById<View>(it).visibility=if(running)View.VISIBLE else View.GONE
        }
        val parent=description.parent as androidx.constraintlayout.widget.ConstraintLayout
        androidx.constraintlayout.widget.ConstraintSet().apply {
            clone(parent)
            connect(R.id.progress_description,androidx.constraintlayout.widget.ConstraintSet.BOTTOM,
                if(running)R.id.progressBar else R.id.buttonConfirm,androidx.constraintlayout.widget.ConstraintSet.TOP,
                (24*resources.displayMetrics.density).toInt())
            applyTo(parent)
        }
    }
    private fun stop() { download?.cancel();work?.cancel() }
    override fun refreshProfile() = Unit // This preparation page contains no profile view.
    override fun onStop() { stop();super.onStop() }
}
