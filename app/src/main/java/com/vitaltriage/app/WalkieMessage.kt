package com.vitaltriage.app

/** Session memory only; never serialized into preferences, saved state, logs or backend history. */
data class WalkieMessage(
    val id:Long, val original:String, val language:String,
    val translation:String?=null, val translationFailed:Boolean=false,
    val translationEngine:String="mlkit", val translationTarget:String="en"
)
/** Never present source-language text as a successful target-language translation. */
internal fun WalkieMessage.targetText():String? =
    translation?.takeIf { it.isNotBlank() && !translationFailed }
        ?: original.takeIf { language == translationTarget }
internal fun boundedMessages(messages:List<WalkieMessage>):List<WalkieMessage> {
    val kept=messages.distinctBy { it.id }.sortedBy { it.id }.takeLast(20).toMutableList()
    while(kept.sumOf { it.original.length+(it.translation?.length ?: 0) }>40000) kept.removeAt(0)
    return kept
}
