package com.vitaltriage.app

import android.content.Context
import java.io.File

/** Fixed original RTranslator 2.0.0 research model set; no arbitrary URL/path configuration. */
internal object NllbModels {
    private const val release = "https://github.com/niedev/RTranslator/releases/download/2.0.0/"
    val artifacts = listOf(
        ModelArtifact("NLLB_cache_initializer.onnx",25368443L,"4150b3cba7db9487a94e3b693f79d0f89772e75d80bffd0e60b06de1b574e3d9",release+"NLLB_cache_initializer.onnx"),
        ModelArtifact("NLLB_decoder.onnx",179109694L,"86028825495fc34085c7ebb80fb5f0eaa18ac6600f9d4dbc55bc731cc6981159",release+"NLLB_decoder.onnx"),
        ModelArtifact("NLLB_embed_and_lm_head.onnx",524712277L,"2745a6f932237b8bb0c35ded54987ea3fc455b696215d48d32271c28c785dd50",release+"NLLB_embed_and_lm_head.onnx"),
        ModelArtifact("NLLB_encoder.onnx",266487014L,"9b25d62f4875e5087ffde53c2794308c07f60f4c77670fc5bbfe308a1ef88cd9",release+"NLLB_encoder.onnx"),
        ModelArtifact("sentencepiece_bpe.model",4852054L,"14bb8dfb35c0ffdea7bc01e56cea38b9e3d5efcdcb9c251d6b40538e1aab555a","https://huggingface.co/facebook/nllb-200-distilled-600M/resolve/main/sentencepiece.bpe.model")
    )
    val totalBytes = artifacts.sumOf { it.bytes }
    fun directory(context: Context) = File(context.noBackupFilesDir, "nllb")
    /** Worker only: verify all weights before any native session is created. */
    fun verified(directory: File) = artifacts.all { VerifiedModelDownload.valid(File(directory,it.name),it) }
    fun prepare(context: Context, download: VerifiedModelDownload, progress: (Long,Long)->Unit) {
        var completed=0L
        for(artifact in artifacts) {
            download.prepare(directory(context),artifact) { bytes,_ -> progress(completed+bytes,totalBytes) }
            completed+=artifact.bytes
        }
    }
}
