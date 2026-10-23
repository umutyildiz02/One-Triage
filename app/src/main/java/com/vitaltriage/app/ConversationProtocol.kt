package com.vitaltriage.app

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import com.vitaltriage.core.Language

/**
 * RTranslator BluetoothMessage's 4+3+1 ASCII header and m/text/language-length payload,
 * adapted with strict ordered reassembly, bounded memory and explicit consent.
 * Original design Copyright2016 Luca Martino, Apache-2.0; see THIRD_PARTY_NOTICES.
 * The secured transport uses a separate service UUID; legacy insecure peers are not compatible.
 */
object ConversationProtocol {
    const val MAX_BYTES=16_384
    private fun number(value:Int,width:Int):ByteArray {
        require(value>=0)
        var remaining=value
        val out=ByteArray(width)
        for(i in width-1 downTo 0){out[i]=(32+remaining%95).toByte();remaining/=95}
        require(remaining==0)
        return out
    }
    private fun number(bytes:ByteArray,start:Int,width:Int):Int {
        var value=0
        for(i in start until start+width){
            val digit=(bytes[i].toInt() and 255)-32
            require(digit in 0..94)
            value=value*95+digit
        }
        return value
    }
    fun frames(id:Int,payload:ByteArray,mtu:Int=23):List<ByteArray> {
        require(payload.size in 1..MAX_BYTES && mtu in 23..517)
        val capacity=minOf(mtu-3-8,236)
        return payload.toList().chunked(capacity).mapIndexed { index,chunk ->
            number(id,4)+number(index,3)+byteArrayOf(if((index+1)*capacity>=payload.size)50 else 49)+chunk.toByteArray()
        }
    }
    fun text(value:String,language:Language):ByteArray {
        require(value.isNotBlank() && value.length<=4000)
        return ("m"+value+language.tag+language.tag.length).toByteArray(Charsets.UTF_8).also { require(it.size<=MAX_BYTES) }
    }
    data class Message(val original:String,val language:Language)
    fun parseText(payload:ByteArray):Message {
        require(payload.size in 5..MAX_BYTES)
        val text=utf8(payload)
        require(text.first()=='m')
        val count=text.last().digitToIntOrNull() ?: error("Invalid language length")
        require(count in 2..5 && text.length>count+2)
        val tag=text.substring(text.length-1-count,text.length-1)
        val language=Language.entries.firstOrNull { it.tag==tag } ?: error("Unsupported language")
        val original=text.substring(1,text.length-1-count)
        require(original.isNotBlank() && original.length<=4000)
        return Message(original,language)
    }
    fun utf8(bytes:ByteArray):String=Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
    class Receiver(private val now:()->Long=System::currentTimeMillis) {
        private var id:Int?=null
        private var expected=0
        private var started=0L
        private val data=ByteArrayOutputStream()
        private val completed=ArrayDeque<Int>()
        fun clear(){id=null;expected=0;data.reset();completed.clear()}
        fun accept(frame:ByteArray):ByteArray? {
            try {
                require(frame.size in 9..244)
                val incoming=number(frame,0,4)
                val sequence=number(frame,4,3)
                val type=frame[7].toInt()
                require(type==49 || type==50)
                require(incoming !in completed)
                if(id==null){require(sequence==0);id=incoming;started=now()}
                require(id==incoming && sequence==expected && now()-started in 0..30_000)
                require(data.size()+frame.size-8<=MAX_BYTES)
                data.write(frame,8,frame.size-8);expected++
                if(type==49)return null
                val result=data.toByteArray()
                completed.addLast(incoming);if(completed.size>128)completed.removeFirst()
                id=null;expected=0;data.reset()
                return result
            } catch(error:Exception){id=null;expected=0;data.reset();throw error}
        }
    }
}

/** Pure state boundary shared by real transport and simulated disconnect/consent tests. */
class ConversationSession {
    enum class Phase { DISCONNECTED, AWAITING_APPROVAL, CONNECTED }
    var phase=Phase.DISCONNECTED;private set
    var peer:String?=null;private set
    private var approvedText:String?=null
    private var approvedLanguage:Language?=null
    fun request(peerId:String,bonded:Boolean) {
        require(phase==Phase.DISCONNECTED && peerId.isNotBlank() && bonded)
        peer=peerId;phase=Phase.AWAITING_APPROVAL
    }
    fun accept(peerId:String) {
        require(phase==Phase.AWAITING_APPROVAL && peer==peerId)
        phase=Phase.CONNECTED
    }
    fun approve(peerId:String,text:String,language:Language) {
        require(phase==Phase.CONNECTED && peer==peerId)
        ConversationProtocol.text(text,language)
        approvedText=text;approvedLanguage=language
    }
    fun send(peerId:String,text:String,language:Language):ByteArray {
        require(phase==Phase.CONNECTED && peer==peerId && approvedText==text && approvedLanguage==language)
        approvedText=null;approvedLanguage=null // consent is single-use, including failed attempts
        return ConversationProtocol.text(text,language)
    }
    fun disconnect(){phase=Phase.DISCONNECTED;peer=null;approvedText=null;approvedLanguage=null}
}
