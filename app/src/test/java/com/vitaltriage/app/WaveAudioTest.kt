package com.vitaltriage.app
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WaveAudioTest {
    private fun wav(floating:Boolean=false):ByteArray {
        val width=if(floating)4 else 2;val size=1600*width
        return ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray());putInt(36+size);put("WAVEfmt ".toByteArray());putInt(16)
            putShort((if(floating)3 else 1).toShort());putShort(1);putInt(16000);putInt(16000*width)
            putShort(width.toShort());putShort((width*8).toShort());put("data".toByteArray());putInt(size)
            repeat(1600){if(floating)putFloat(0.5f)else putShort(16384)}
        }.array()
    }
    @Test fun decodesActualPcmAndFloatSamples() {
        assertEquals(16384,WaveAudio.decode(wav())[0].toInt())
        assertEquals(16383,WaveAudio.decode(wav(true))[0].toInt())
    }
    @Test fun rejectsTruncationWrongRateChannelsAndCodec() {
        val source=wav()
        assertTrue(runCatching { WaveAudio.decode(source.copyOf(source.size-1)) }.isFailure)
        listOf(20 to 7,22 to 2,24 to 1).forEach { (offset,value)->
            val invalid=source.copyOf();invalid[offset]=value.toByte()
            assertTrue(runCatching { WaveAudio.decode(invalid) }.isFailure)
        }
    }
    @Test fun rejectsNonFiniteAndOversizedAudio() {
        val invalid=wav(true)
        ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putFloat(44,Float.NaN)
        assertTrue(runCatching { WaveAudio.decode(invalid) }.isFailure)
        assertTrue(runCatching { WaveAudio.decode(ByteArray(WaveAudio.MAX_BYTES+1)) }.isFailure)
    }
}
