package com.vitaltriage.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Optional per-user backend credential, never an OpenAI key or patient text.
 * AES-GCM authenticates the destination URL as well as the encrypted token.
 * Android Keystore owns the non-exportable key; encrypted file is excluded from backup.
 * Hardware backing is device-dependent and is not claimed.
 */
internal class BackendAccessStore(context:Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"backend-access.enc"))
    private val alias=context.packageName+".backend-access.v1"
    private fun key(create:Boolean):SecretKey? {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        if(!create)return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun destination(endpoint:String)=endpoint.trim().trimEnd('/').toByteArray(Charsets.UTF_8)
    fun save(endpoint:String,token:String)=synchronized(lock) {
        require(BackendConnectionPolicy.permits(endpoint,false))
        require(token.length in 32..256 && token.none{it.isWhitespace() || it.code<33 || it.code>126})
        require(!token.startsWith("sk-")){"Use the separate backend credential, never an OpenAI key"}
        val clear=token.toByteArray(Charsets.UTF_8)
        try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,requireNotNull(key(true)))
            cipher.updateAAD(destination(endpoint))
            val encrypted=cipher.doFinal(clear)
            val output=file.startWrite()
            try { output.write(byteArrayOf(1,cipher.iv.size.toByte()));output.write(cipher.iv);output.write(encrypted);file.finishWrite(output) }
            catch(error:Exception){file.failWrite(output);throw error}
        } finally {clear.fill(0)}
    }
    fun load(endpoint:String):String?=synchronized(lock) {
        if(!BackendConnectionPolicy.permits(endpoint,false) || !file.baseFile.exists())return@synchronized null
        runCatching {
            require(file.baseFile.length() in 30..512)
            val bytes=file.readFully()
            require(bytes[0].toInt()==1 && bytes[1].toInt()==12)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(false) ?: return@synchronized null,GCMParameterSpec(128,bytes.copyOfRange(2,14)))
            cipher.updateAAD(destination(endpoint))
            val clear=cipher.doFinal(bytes.copyOfRange(14,bytes.size))
            try {String(clear,Charsets.UTF_8)} finally {clear.fill(0)}
        }.getOrNull() // Unavailable/invalidated key or changed host fails closed.
    }
    fun clear()=synchronized(lock){file.delete()}
    companion object {private val lock=Any()}
}
