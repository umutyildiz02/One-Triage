package com.vitaltriage.app

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Language
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

/** Explicit two-AVD test: bonded BLE, two ordered messages in each direction, all four language tags. */
class ConversationDuplexProbeTest {
    @Test fun bondedPeersExchangeOrderedMessagesBothWays() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val role=InstrumentationRegistry.getArguments().getString("conversationRadioRole")
        require(role in setOf("server","client"))
        val context=instrumentation.targetContext
        require(context.packageName.endsWith(".uitest"))
        val clientTexts=listOf("Synthetic sequence one. ".repeat(20) to Language.EN,"رسالة اختبار اصطناعية ثانية" to Language.AR)
        val serverTexts=listOf("Première réponse synthétique." to Language.FR,"İkinci sentetik yanıt." to Language.TR)
        val outgoing=if(role=="client")clientTexts else serverTexts
        val expected=if(role=="client")serverTexts else clientTexts
        val done=CountDownLatch(1)
        val disconnected=CountDownLatch(1)
        val events=CopyOnWriteArrayList<String>()
        var radio:ConversationBluetooth?=null
        var peer:String?=null;var connecting=false;var sent=0;var received=0
        var failure:String?=null
        fun complete(){if(sent==outgoing.size && received==expected.size)done.countDown()}
        fun sendNext(){if(sent<outgoing.size)radio?.approveAndSend(peer!!,outgoing[sent].first,outgoing[sent].second)}
        val foreground=ActivityScenario.launch(ConversationActivity::class.java)
        val listener=object:ConversationBluetooth.Listener {
            override fun discovered(id:String,name:String){
                if(role=="client" && !connecting){connecting=true;radio?.connect(id)}
            }
            override fun requested(id:String,name:String){if(role=="server")radio?.accept(id)}
            override fun connected(id:String,name:String){
                peer=id;events.add("bonded-session")
                if(role=="client")sendNext()
            }
            override fun received(message:ConversationProtocol.Message){
                if(received>=expected.size || message.original!=expected[received].first || message.language!=expected[received].second) {
                    failure="Order, language or exact payload mismatch";done.countDown();return
                }
                events.add("received-"+message.language.tag);received++
                if(role=="server" && received==expected.size)sendNext()
                complete()
            }
            override fun sent(){
                events.add("sent-"+outgoing[sent].second.tag);sent++
                if(sent<outgoing.size)sendNext()
                complete()
            }
            override fun status(code:String){
                events.add(code)
                if(code=="disconnected" && sent==outgoing.size && received==expected.size)disconnected.countDown()
                if(code in setOf("permission","unsupported","bluetoothOff","scanFailed","advertiseFailed","timeout","pairingRejected","connectionError","sendRejected")){
                    failure=code;done.countDown()
                }
            }
        }
        try {
            instrumentation.runOnMainSync {radio=ConversationBluetooth(context,"VT duplex "+role,listener);radio!!.startDiscovery()}
            assertTrue("Duplex timeout: "+events.joinToString(),done.await(180,TimeUnit.SECONDS))
            assertNull("Duplex failure: "+events.joinToString(),failure)
            assertEquals(2,sent);assertEquals(2,received)
            // Let final indication acknowledgement settle, then close from the server side.
            // Both app sessions must close even if Android retains a shared physical BLE link.
            SystemClock.sleep(1000)
            if(role=="server")instrumentation.runOnMainSync { radio!!.requestDisconnect() }
            assertTrue("Remote disconnect missing: "+events.joinToString(),disconnected.await(15,TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {radio?.close()}
            foreground.close()
            println("VT_DUPLEX role="+role+" sent="+sent+" received="+received+" events="+events.joinToString())
        }
    }
}
