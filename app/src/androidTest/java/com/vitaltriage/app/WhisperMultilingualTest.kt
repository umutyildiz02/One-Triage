package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

/** Licensed non-clinical public waveform, not microphone or paid API evidence. */
class WhisperMultilingualTest {
    @Test fun turkishManualAndDetected() = verify("tr")
    @Test fun frenchManualAndDetected() = verify("fr")
    @Test fun arabicManualAndDetected() = verify("ar")
    private fun verify(language:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val assets=instrumentation.context.assets
        val manifest=assets.open("speech-fixtures/manifest.json").bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
        val fixture=manifest.first { it.asJsonObject["language"].asString==language }.asJsonObject
        val raw=assets.open("speech-fixtures/$language.pcm").use { it.readBytes() }
        val audio=ShortArray(raw.size/2) { i->((raw[i*2].toInt() and 255) or (raw[i*2+1].toInt() shl 8)).toShort() }
        raw.fill(0)
        val expected=fixture["reference"].asString
        val beam=InstrumentationRegistry.getArguments().getString("whisperBeam")=="true"
        val publicTextDiagnostics=InstrumentationRegistry.getArguments().getString("publicFixtureText")=="true"
        val engine=WhisperTestEngine(context)
        val report=linkedMapOf<String,Any>("language" to language,"paidCalls" to 0,"dataset" to "google/fleurs","id" to fixture["id"].asInt,
            "microphone" to false,"samples" to audio.size,"beamSearch" to beam,"model" to engine.model)
        val results=mutableListOf<Pair<String,WhisperText?>>()
        try {
            for(mode in listOf(language,"auto")) {
                engine.begin()
                val start=SystemClock.elapsedRealtime()
                val result=engine.transcribe(audio,mode,beamSearch=beam)
                report[mode+"Ms"]=SystemClock.elapsedRealtime()-start
                report[mode+"Detected"]=result?.language.orEmpty()
                report[mode+"Characters"]=result?.text?.length ?: 0
                report[mode+"WordErrorRate"]=wer(expected,result?.text.orEmpty(),language)
                // Only this pinned public non-clinical fixture, never app/user audio.
                if(publicTextDiagnostics) { report["publicReference"]=expected;report[mode+"PublicRecognition"]=result?.text.orEmpty() }
                results += mode to result
            }
        } finally {
            audio.fill(0);engine.close()
            File(context.getExternalFilesDir(null),"whisper-$language${engine.suffix}${if(beam)"-beam" else ""}.json").writeText(Contract.gson.toJson(report))
        }
        for((mode,result) in results) {
            assertNotNull("No real transcription: $language/$mode",result)
            assertEquals("Detected language: $mode",language,result!!.language)
            assertTrue("Word error rate too high: $language/$mode = ${report[mode+"WordErrorRate"]}",
                (report[mode+"WordErrorRate"] as Double)<=0.35)
        }
    }
    private fun wer(reference:String,actual:String,language:String):Double {
        fun words(value:String)=value.lowercase(Locale.forLanguageTag(language))
            .replace(Regex("[\\u064B-\\u065F\\u0670]"),"")
            .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val a=words(reference);val b=words(actual)
        var previous=IntArray(b.size+1){it}
        for(i in a.indices) {
            val current=IntArray(b.size+1);current[0]=i+1
            for(j in b.indices)current[j+1]=minOf(current[j]+1,previous[j+1]+1,previous[j]+if(a[i]==b[j])0 else 1)
            previous=current
        }
        return previous[b.size].toDouble()/a.size.coerceAtLeast(1)
    }
}
