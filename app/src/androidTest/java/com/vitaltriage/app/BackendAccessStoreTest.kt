package com.vitaltriage.app

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import org.junit.*
import org.junit.Assert.*

/** Real Android Keystore tests; never touch the normal package or its saved credential. */
class BackendAccessStoreTest {
    private val parent=InstrumentationRegistry.getInstrumentation().targetContext
    private val context=object:ContextWrapper(parent) {
        override fun getPackageName()=parent.packageName+".vault_test"
        override fun getNoBackupFilesDir()=File(parent.noBackupFilesDir,"vault-test").apply{mkdirs()}
    }
    private val endpoint="https://triage.example.invalid"
    private val synthetic="synthetic-test-only-credential-0000000001"
    @Before fun before() { check(parent.packageName.endsWith(".uitest")); BackendAccessStore(context).clear() }
    @After fun after() {
        BackendAccessStore(context).clear()
        KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry(context.packageName+".backend-access.v1")}
    }
    @Test fun survivesNewStoreWithoutPlaintextOnDisk() {
        BackendAccessStore(context).save(endpoint,synthetic)
        assertEquals(synthetic,BackendAccessStore(context).load(endpoint))
        assertEquals(synthetic,BackendAccessStore(context).load(endpoint+"/"))
        val bytes=File(context.noBackupFilesDir,"backend-access.enc").readBytes()
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains(synthetic))
    }
    @Test fun differentDestinationAndTamperingFailClosed() {
        val store=BackendAccessStore(context);store.save(endpoint,synthetic)
        assertNull(store.load("https://other.example.invalid"))
        assertNull(store.load(endpoint+"/other"))
        val file=File(context.noBackupFilesDir,"backend-access.enc")
        val bytes=file.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();file.writeBytes(bytes)
        assertNull(store.load(endpoint))
    }
    @Test fun rejectsOpenAiKeysAndInsecureDestination() {
        val store=BackendAccessStore(context)
        assertTrue(runCatching{store.save(endpoint,"sk-synthetic-not-a-real-key-000000000000")}.isFailure)
        assertTrue(runCatching{store.save("http://127.0.0.1:8787",synthetic)}.isFailure)
        assertTrue(runCatching{store.save(endpoint,"short")}.isFailure)
        assertNull(store.load(endpoint))
    }
    @Test fun forgetRemovesOnlyEncryptedCredential() {
        val unrelated=File(context.noBackupFilesDir,"unrelated-test-preference").apply{writeText("preserve")}
        try {
            val store=BackendAccessStore(context);store.save(endpoint,synthetic);store.clear()
            assertNull(BackendAccessStore(context).load(endpoint))
            assertEquals("preserve",unrelated.readText())
        } finally { unrelated.delete() }
    }
}
