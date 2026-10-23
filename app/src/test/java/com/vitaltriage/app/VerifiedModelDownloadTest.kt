package com.vitaltriage.app

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class VerifiedModelDownloadTest {
    @get:Rule val temporary=TemporaryFolder()
    private val payload="verified model fixture".toByteArray()
    private val artifact=ModelArtifact("model.bin",payload.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) },
        "https://models.example.invalid/model.bin")
    private fun client(reply:(Request)->Response)=OkHttpClient.Builder().addInterceptor { reply(it.request()) }.build()
    private fun response(request:Request,body:ByteArray,code:Int=200,range:String?=null)=
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(body.toResponseBody()).apply { if(range!=null)header("Content-Range",range) }.build()

    @Test fun installsOnlyVerifiedBytesAndReusesWithoutNetwork() {
        val dir=temporary.newFolder();var requests=0
        val downloader=VerifiedModelDownload(client { requests++;response(it,payload) })
        val file=downloader.prepare(dir,artifact) { _,_-> }
        assertArrayEquals(payload,file.readBytes())
        downloader.prepare(dir,artifact) { _,_-> }
        assertEquals(1,requests)
    }
    @Test fun resumesAtVerifiedRangeOffset() {
        val dir=temporary.newFolder()
        File(dir,"model.bin.part").writeBytes(payload.copyOfRange(0,5))
        val downloader=VerifiedModelDownload(client {
            assertEquals("bytes=5-",it.header("Range"))
            response(it,payload.copyOfRange(5,payload.size),206,"bytes 5-${payload.lastIndex}/${payload.size}")
        })
        assertArrayEquals(payload,downloader.prepare(dir,artifact) { _,_-> }.readBytes())
    }
    @Test fun serverIgnoringRangeRestartsPartialSafely() {
        val dir=temporary.newFolder()
        File(dir,"model.bin.part").writeBytes(byteArrayOf(1,2,3))
        val downloader=VerifiedModelDownload(client { response(it,payload) })
        assertArrayEquals(payload,downloader.prepare(dir,artifact) { _,_-> }.readBytes())
    }
    @Test fun incorrectRangeCannotInstall() {
        val dir=temporary.newFolder()
        File(dir,"model.bin.part").writeBytes(payload.copyOfRange(0,5))
        val downloader=VerifiedModelDownload(client { response(it,payload,206,"bytes 0-${payload.lastIndex}/${payload.size}") })
        assertThrows(IllegalStateException::class.java) { downloader.prepare(dir,artifact) { _,_-> } }
        assertFalse(File(dir,"model.bin").exists())
    }
    @Test fun corruptedDownloadPreservesExistingFileAndAllowsExplicitRetry() {
        val dir=temporary.newFolder();val original=byteArrayOf(4,5)
        File(dir,"model.bin").writeBytes(original)
        val downloader=VerifiedModelDownload(client { response(it,ByteArray(payload.size)) })
        assertThrows(IllegalStateException::class.java) { downloader.prepare(dir,artifact) { _,_-> } }
        assertArrayEquals(original,File(dir,"model.bin").readBytes())
        assertFalse(File(dir,"model.bin.part").exists())
    }
    @Test fun cancelNeverInstallsPartial() {
        val dir=temporary.newFolder()
        val downloader=VerifiedModelDownload(client { response(it,payload) })
        assertThrows(CancellationException::class.java) { downloader.prepare(dir,artifact) { _,_->downloader.cancel() } }
        assertFalse(File(dir,"model.bin").exists())
    }
    @Test fun unsafeArtifactSourcesAndPathsAreRejected() {
        for(name in listOf("../model","..","a/b","a\\b")) {
            assertThrows(IllegalArgumentException::class.java) { artifact.copy(name=name) }
        }
        for(url in listOf("http://models.example/model","https://user:secret@models.example/model","file:///model")) {
            assertThrows(IllegalArgumentException::class.java) { artifact.copy(url=url) }
        }
    }
}
