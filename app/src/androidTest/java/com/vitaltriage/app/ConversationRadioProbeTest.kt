package com.vitaltriage.app

import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.vitaltriage.core.Language
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Explicit opt-in two-AVD BLE stack probe. OS pairing approval remains interactive;
 * no privileged pairing bypass, transcript injection or paid API call.
 * Run only with -e conversationRadioRole server/client on separate devices.
 */
class ConversationRadioProbeTest {
    @Test fun bondedRadioTransfersOnlyExplicitSyntheticText() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val role=InstrumentationRegistry.getArguments().getString("conversationRadioRole")
        require(role in setOf("server","client")){"Explicit two-device probe role required"}
        val context=instrumentation.targetContext
        require(context.packageName.endsWith(".uitest")){"Isolated package required"}
        val foreground=androidx.test.core.app.ActivityScenario.launch(ConversationActivity::class.java)
        val done=CountDownLatch(1)
        val events=CopyOnWriteArrayList<String>()
        var radio:ConversationBluetooth?=null
        var connecting=false
        var failure:String?=null
        val synthetic="Synthetic connection test: no health information."
        val listener=object:ConversationBluetooth.Listener {
            override fun discovered(id:String,name:String){
                events.add("discovered")
                if(role=="client" && !connecting){connecting=true;radio?.connect(id)}
            }
            override fun requested(id:String,name:String){
                events.add("request")
                // Explicit test-owner acceptance; Android bond still requires its own UI approval.
                if(role=="server")radio?.accept(id)
            }
            override fun connected(id:String,name:String){
                events.add("connected")
                if(role=="client")radio?.approveAndSend(id,synthetic,Language.EN)
            }
            override fun received(message:ConversationProtocol.Message){
                if(role!="server" || message.original!=synthetic || message.language!=Language.EN)failure="Unexpected payload"
                else events.add("received-exact-synthetic")
                done.countDown()
            }
            override fun sent(){events.add("sent");if(role=="client")done.countDown()}
            override fun status(code:String){
                events.add(code)
                if(code in setOf("permission","unsupported","bluetoothOff","scanFailed","advertiseFailed","timeout","pairingRejected","connectionError","sendRejected")){
                    failure=code;done.countDown()
                }
            }
        }
        try{
            instrumentation.runOnMainSync {radio=ConversationBluetooth(context,"VT synthetic "+role,listener);radio!!.startDiscovery()}
            assertTrue("No completion; events="+events.joinToString(),done.await(180,TimeUnit.SECONDS))
            assertNull("Radio probe failed; events="+events.joinToString(),failure)
            assertTrue("No bonded application session",events.contains("connected"))
            assertTrue(events.contains(if(role=="client")"sent" else "received-exact-synthetic"))
        }finally{
            instrumentation.runOnMainSync{radio?.close()}
            foreground.close()
            println("VT_RADIO role="+role+" events="+events.joinToString())
        }
    }
}
