package com.vitaltriage.app

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal object WhisperNative {
    init { System.loadLibrary("vt-whisper") }
    external fun load(path:String,libraryDirectory:String):Long
    external fun systemInfo():String
    external fun prepare(handle:Long)
    external fun run(handle:Long,pcm:ShortArray,language:String,translate:Boolean,beamSearch:Boolean,threads:Int=WhisperThreads.select(android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),Runtime.getRuntime().availableProcessors())):ByteArray?
    external fun timedOut(handle:Long):Boolean
    external fun cancel(handle:Long)
    external fun free(handle:Long)
}
data class WhisperText(val language:String,val text:String)
class SpeechTimeoutException:Exception("Local speech recognition time limit exceeded")

/** All load/transcribe/close calls belong to one worker. cancel() may run on the UI thread. */
class WhisperEngine(private val context:Context) {
    private val lock=Any()
    private var handle=0L
    private var loadedModel:WhisperModel?=null
    @Volatile private var cancelled=false
    fun begin() { synchronized(lock) { cancelled=false;if(handle!=0L)WhisperNative.prepare(handle) } }
    fun load() {
        check(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()) { "Model loading must use worker" }
        val selected=UserPreferences(context).whisperModel
        if(handle!=0L && loadedModel==selected)return
        if(handle!=0L)close()
        if(SpeechMemory.isLow(context))throw SpeechMemoryException()
        val file=prepareModel(context,selected)
        val loaded=WhisperNative.load(file.absolutePath,context.applicationInfo.nativeLibraryDir)
        check(loaded!=0L) { "Whisper model could not be loaded" }
        synchronized(lock) {
            handle=loaded;loadedModel=selected;WhisperNative.prepare(handle)
            if(cancelled)WhisperNative.cancel(handle)
        }
    }
    fun transcribe(pcm:ShortArray,language:String,translate:Boolean=false,beamSearch:Boolean=false):WhisperText? {
        val selected = if(language=="auto")null else com.vitaltriage.core.Language.from(language)
        require(language=="auto" || selected?.whisperTag!=null) { "Whisper has no language model for this code" }
        require(pcm.size<=29*16000)
        // Reject cancelled/near-silent work before model preparation/loading, too.
        if(cancelled || !SpeechPcm.hasSignal(pcm))return null
        load()
        if(cancelled)return null
        val bytes=WhisperNative.run(handle,pcm,selected?.whisperTag ?: "auto",translate,beamSearch)
        if(bytes==null) {
            if(!cancelled && WhisperNative.timedOut(handle))throw SpeechTimeoutException()
            return null
        }
        val decoded=bytes.toString(Charsets.UTF_8);bytes.fill(0)
        if(cancelled)return null
        val tag=com.vitaltriage.core.Language.fromWhisper(decoded.substringBefore('\n'))?.tag
            ?: throw IllegalArgumentException("Detected language is outside the configured registry")
        val text=decoded.substringAfter('\n',"").trim()
        return if(text.isBlank())null else WhisperText(tag,text.take(4000))
    }
    fun cancel() { synchronized(lock) { cancelled=true;if(handle!=0L)WhisperNative.cancel(handle) } }
    fun close() { synchronized(lock) { if(handle!=0L){WhisperNative.free(handle);handle=0;loadedModel=null} } }
    companion object {
        // Readiness hint only; prepareModel verifies SHA256 on the worker before native load.
        fun bundled(context:Context,selected:WhisperModel=UserPreferences(context).whisperModel)=
            File(context.noBackupFilesDir,"models/"+selected.fileName).let { it.isFile && it.length()==selected.bytes } ||
            runCatching { context.assets.openFd(selected.asset).use { it.length==selected.bytes } }.getOrDefault(false)
        @Synchronized fun prepareModel(context:Context,selected:WhisperModel=UserPreferences(context).whisperModel):File {
            val directory=File(context.noBackupFilesDir,"models").apply { mkdirs() }
            val model=File(directory,selected.fileName)
            fun valid(file:File):Boolean {
                if(!file.isFile || file.length()!=selected.bytes)return false
                val digest=MessageDigest.getInstance("SHA-256")
                file.inputStream().buffered().use { input->
                    val buffer=ByteArray(65536)
                    while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}
                }
                return digest.digest().joinToString("") { "%02x".format(it) }==selected.sha256
            }
            if(valid(model))return model
            val temporary=File(directory,"model.tmp")
            try {
                context.assets.open(selected.asset).use { input-> temporary.outputStream().use { output->input.copyTo(output,65536);output.fd.sync() } }
                check(valid(temporary)) { "Model checksum mismatch" }
                java.nio.file.Files.move(temporary.toPath(),model.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                return model
            } finally { temporary.delete() }
        }
    }
}
