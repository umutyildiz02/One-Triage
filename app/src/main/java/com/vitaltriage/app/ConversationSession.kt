package com.vitaltriage.app

import androidx.lifecycle.ViewModel
import com.vitaltriage.core.Language

internal data class ConversationMessage(val text:String,val language:Language,val outgoing:Boolean,
    var translated:String?=null,var translatedLanguage:Language?=null,
    var translationError:Boolean=false,var provider:String?=null)

/** Presentation only: original stays available for explicit review and sharing. */
internal fun ConversationMessage.targetText(target:Language):String? =
    translated?.takeIf { it.isNotBlank() && !translationError && translatedLanguage==target }
        ?: text.takeIf { language==target }

/** Retained across Activity recreation only; no disk/saved-state serialization of health text. */
internal class ConversationMemoryViewModel:ViewModel() {
    val messages=mutableListOf<ConversationMessage>()
    var draft=""
    var previousTranscript=""
    private var peer:String?=null
    fun bindPeer(id:String) {
        if(peer!=null && peer!=id){messages.clear();draft="";previousTranscript=""}
        peer=id
    }
    fun add(message:ConversationMessage) {
        messages.add(message)
        trim()
    }
    fun trim() {
        while(messages.size>100 || messages.sumOf { it.text.length+(it.translated?.length ?: 0) }>100_000)
            messages.removeAt(0)
    }
}
