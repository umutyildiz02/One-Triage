package com.vitaltriage.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Explicit synthetic diagnostic assets for later UI import. No microphone or OpenAI claim. */
class SyntheticWaveformTest {
    @Test fun installedOfflineVoicesProduceBoundedMultilingualWavFixtures() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val prefs=UserPreferences(context)
        prefs.setupComplete=true
        if(prefs.nickname.isBlank())prefs.nickname="Synthetic audio preparation"
        val phrases=linkedMapOf(
            "tr" to "Bu sentetik bir test vakasıdır. Yetişkin bir kişide yirmi dakikadır şiddetli göğüs ağrısı ve nefes darlığı var.",
            "fr" to "Ceci est un cas de test synthétique. Un adulte a une forte douleur thoracique et du mal à respirer depuis vingt minutes.",
            "ar" to "هذه حالة اختبار اصطناعية. يعاني شخص بالغ من ألم شديد في الصدر وصعوبة في التنفس منذ عشرين دقيقة."
        )
        val metrics=mutableListOf<Map<String,Any>>()
        ActivityScenario.launch(MainActivity::class.java).use {
            for((language,phrase) in phrases) {
                val samples=WhisperEngineTest.syntheticSpeech(language,phrase)
                try {
                    assertTrue("Fixture duration must fit real import",samples.size in 1600..29*16000)
                    val peak=samples.maxOf { sample->kotlin.math.abs(sample.toInt()) }
                    assertTrue("No audible PCM produced: "+language,peak>100)
                    val buffer=ByteBuffer.allocate(44+samples.size*2).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.put("RIFF".toByteArray(Charsets.US_ASCII));buffer.putInt(36+samples.size*2)
                    buffer.put("WAVEfmt ".toByteArray(Charsets.US_ASCII));buffer.putInt(16)
                    buffer.putShort(1);buffer.putShort(1);buffer.putInt(16000);buffer.putInt(32000)
                    buffer.putShort(2);buffer.putShort(16)
                    buffer.put("data".toByteArray(Charsets.US_ASCII));buffer.putInt(samples.size*2)
                    samples.forEach { sample->buffer.putShort(sample) }
                    val bytes=buffer.array()
                    try {
                        val target=File(context.getExternalFilesDir(null),"synthetic-live-$language.wav")
                        target.writeBytes(bytes)
                        metrics+=mapOf("language" to language,"samples" to samples.size,"peakAbsolute" to peak,
                            "bytes" to bytes.size,"sha256" to MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
                    } finally { bytes.fill(0) }
                } finally { samples.fill(0) }
            }
        }
        // Only synthetic fixture metadata. No patient text, transcript, audio bytes or credentials in logs.
        File(context.getExternalFilesDir(null),"synthetic-waveforms.json").writeText(Contract.gson.toJson(
            mapOf("engine" to "installed offline Android TTS","paidCalls" to 0,"microphone" to false,
                "transcriptionProven" to false,"fixtures" to metrics)))
    }
}
