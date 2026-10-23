package com.vitaltriage.app

/** Pinned public multilingual GGML artifacts; no arbitrary model path or remote model loading. */
enum class WhisperModel(val key:String,val fileName:String,val bytes:Long,val sha256:String,val label:String) {
    SMALL("small","ggml-small-q5_1.bin",190085487L,
        "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb","Small · faster · 190 MB"),
    MEDIUM("medium","ggml-medium-q5_0.bin",539212467L,
        "19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f","Medium · slower · 539 MB");
    val asset get()="models/$fileName"
    internal val artifact get()=ModelArtifact(fileName,bytes,sha256,
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName")
    companion object { fun fromKey(key:String?)=entries.firstOrNull { it.key==key } ?: MEDIUM }
}
