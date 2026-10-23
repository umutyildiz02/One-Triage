package com.vitaltriage.app

/** Strict, bounded local test WAV decoding. No transcription, network, or persistence. */
object WaveAudio {
    const val MAX_BYTES=2_000_000
    fun decode(bytes:ByteArray):ShortArray {
        require(bytes.size in 44..MAX_BYTES)
        fun u16(p:Int)=(bytes[p].toInt() and 255) or ((bytes[p+1].toInt() and 255) shl 8)
        fun u32(p:Int)=(0..3).fold(0L) { value,i->value or ((bytes[p+i].toLong() and 255) shl (8*i)) }
        fun tag(p:Int)=String(bytes,p,4,Charsets.US_ASCII)
        require(tag(0)=="RIFF" && tag(8)=="WAVE" && u32(4)+8==bytes.size.toLong())
        var codec=0;var bits=0;var dataOffset=-1;var dataLength=0;var position=12
        while(position+8<=bytes.size) {
            val size=u32(position+4)
            require(size<=bytes.size-position-8)
            val offset=position+8
            when(tag(position)) {
                "fmt " -> {
                    require(codec==0 && size>=16)
                    codec=u16(offset);bits=u16(offset+14)
                    require(u16(offset+2)==1 && u32(offset+4)==16000L)
                    require((codec==1 && bits==16)||(codec==3 && bits==32))
                    require(u16(offset+12)==bits/8 && u32(offset+8)==16000L*bits/8)
                }
                "data" -> { require(dataOffset==-1);dataOffset=offset;dataLength=size.toInt() }
            }
            position=offset+size.toInt()+(size.toInt() and 1)
        }
        require(codec!=0 && dataOffset>=0 && dataLength%(bits/8)==0)
        val count=dataLength/(bits/8)
        require(count in 1600..29*16000)
        return ShortArray(count) { index->
            val p=dataOffset+index*(bits/8)
            if(codec==1)u16(p).toShort() else {
                val value=Float.fromBits(u32(p).toInt())
                require(value.isFinite())
                (value.coerceIn(-1f,1f)*32767).toInt().toShort()
            }
        }
    }
}
