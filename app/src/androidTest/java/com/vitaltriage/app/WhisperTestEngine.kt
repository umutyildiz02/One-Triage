package com.vitaltriage.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest

/** Test-only public model comparison. Never accepts arbitrary paths or changes the app model. */
internal class WhisperTestEngine(private val context:Context) {
    private val choice=InstrumentationRegistry.getArguments().getString("qualityModel")
    val quality=choice!=null
    val model=when(choice) { "small-q8"->"small-q8_0";"medium-q5"->"medium-q5_0";else->"small-q5_1" }
    val suffix=when(choice) { "small-q8"->"-q8";"medium-q5"->"-medium";else->"" }
    private val expectedBytes=if(choice=="medium-q5")539212467L else 264464607L
    private val expectedHash=if(choice=="medium-q5")"19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f" else "49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f"
    private val normal=WhisperEngine(context)
    private var handle=0L
    init { require(choice==null || choice in setOf("small-q8","medium-q5"));check(context.packageName.endsWith(".uitest")) }
    fun begin() { if(!quality)normal.begin() }
    fun transcribe(audio:ShortArray,language:String,translate:Boolean=false,beamSearch:Boolean=false):WhisperText? {
        if(!quality)return normal.transcribe(audio,language,translate,beamSearch)
        if(handle==0L) {
            val external=File(context.getExternalFilesDir(null),"ggml-$model.bin")
            val file=if(choice=="medium-q5" && !external.exists())
                WhisperEngine.prepareModel(context,WhisperModel.MEDIUM) else external
            check(file.length()==expectedBytes) { "Publisher model size mismatch" }
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input->
                val buffer=ByteArray(65536)
                while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}
            }
            check(digest.digest().joinToString(""){"%02x".format(it)}==expectedHash) { "Publisher model hash mismatch" }
            handle=WhisperNative.load(file.absolutePath,context.applicationInfo.nativeLibraryDir)
            check(handle!=0L) { "Quality model load failed" }
        }
        WhisperNative.prepare(handle)
        return WhisperNative.run(handle,audio,language,translate,beamSearch)?.let { bytes->
            val decoded=bytes.toString(Charsets.UTF_8);bytes.fill(0)
            WhisperText(decoded.substringBefore('\n'),decoded.substringAfter('\n',"").trim())
        }
    }
    fun close(){normal.close();if(handle!=0L){WhisperNative.free(handle);handle=0}}
}
