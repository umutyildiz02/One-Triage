package com.vitaltriage.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import androidx.core.content.ContextCompat
import com.vitaltriage.core.Language
import java.util.UUID

/**
 * Foreground-only BLE conversation transport. Both peers must bond through Android and
 * explicitly accept the session. Encrypted/MITM GATT permissions reject unsecured peers.
 * This is a secured protocol revision, not legacy insecure-peer interoperability.
 */
@SuppressLint("MissingPermission") // Every public radio entry checks runtime grants; callbacks fail closed on revocation.
class ConversationBluetooth(context:Context,private val nickname:String,private val listener:Listener):AutoCloseable {
    interface Listener {
        fun discovered(id:String,name:String)
        fun status(code:String)
        fun requested(id:String,name:String)
        fun connected(id:String,name:String)
        fun received(message:ConversationProtocol.Message)
        fun sent()
    }
    private val context=context.applicationContext
    private val handler=Handler(Looper.getMainLooper())
    private val manager=context.getSystemService(BluetoothManager::class.java)
    private val adapter get()=manager?.adapter
    private val devices=linkedMapOf<String,BluetoothDevice>()
    private var server:BluetoothGattServer?=null
    private var client:BluetoothGatt?=null
    private var peer:BluetoothDevice?=null
    private var pendingBond:BluetoothDevice?=null
    private var clientRole=false
    private var subscribed=false
    private var scanning=false
    private var advertising=false
    private var closed=false
    private var mtu=23
    private var nextId=0
    private var activeSend=false
    private var notifyTextSent=false
    private var disconnectAfterSend=false
    private val queue=ArrayDeque<ByteArray>()
    private val receiver=ConversationProtocol.Receiver{SystemClock.elapsedRealtime()}
    private val session=ConversationSession()
    private val timeout=Runnable { fail("timeout") }
    private val scanTimeout=Runnable { stopScan();listener.status("searchStopped") }
    private fun post(action:()->Unit){handler.post {if(!closed)try{action()}catch(_:Exception){fail("connectionError")}}}
    private fun granted()=permissions().all { ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED }
    private fun ready():Boolean {
        if(closed)return false
        if(!granted()){listener.status("permission");return false}
        if(adapter==null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){listener.status("unsupported");return false}
        if(adapter?.isEnabled!=true){listener.status("bluetoothOff");return false}
        return true
    }
    private fun deadline(milliseconds:Long=30_000){handler.removeCallbacks(timeout);handler.postDelayed(timeout,milliseconds)}
    private val bonds=object:BroadcastReceiver(){
        override fun onReceive(c:Context,i:Intent){
            if(i.action!=BluetoothDevice.ACTION_BOND_STATE_CHANGED)return
            @Suppress("DEPRECATION") val device=i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if(device.address!=pendingBond?.address)return
            when(i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.BOND_NONE)){
                BluetoothDevice.BOND_BONDED->{pendingBond=null;post{connectBonded(device)}}
                BluetoothDevice.BOND_NONE->{pendingBond=null;post{fail("pairingRejected")}}
            }
        }
    }
    init {
        ContextCompat.registerReceiver(context,bonds,IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),ContextCompat.RECEIVER_EXPORTED)
    }
    private val scan=object:ScanCallback(){
        override fun onScanResult(type:Int,result:ScanResult)=post {
            if(!scanning || devices.size>=64)return@post
            val device=result.device
            if(devices.containsKey(device.address))return@post
            devices[device.address]=device
            listener.discovered(device.address,result.scanRecord?.deviceName ?: device.name ?: "One Triage")
        }
        override fun onScanFailed(code:Int)=post{stopScan();listener.status("scanFailed")}
    }
    private val advertise=object:AdvertiseCallback(){
        override fun onStartSuccess(settings:AdvertiseSettings?)=post{advertising=true;listener.status("discoverable")}
        override fun onStartFailure(errorCode:Int)=post{advertising=false;listener.status("advertiseFailed")}
    }
    fun startDiscovery(){
        if(!ready())return
        try{
            if(server==null){
                server=manager?.openGattServer(context,serverCallbacks) ?: error("No GATT server")
                val service=BluetoothGattService(SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY)
                service.addCharacteristic(BluetoothGattCharacteristic(RX,BluetoothGattCharacteristic.PROPERTY_WRITE,BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM))
                val tx=BluetoothGattCharacteristic(TX,BluetoothGattCharacteristic.PROPERTY_INDICATE,BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM)
                tx.addDescriptor(BluetoothGattDescriptor(CCCD,BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM))
                service.addCharacteristic(tx)
                check(server!!.addService(service))
            }else startAdvertising()
            stopScan();devices.clear()
            val scanner=adapter!!.bluetoothLeScanner ?: error("No scanner")
            scanning=true
            scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scan)
            handler.postDelayed(scanTimeout,30_000)
            listener.status("searching")
        }catch(_:Exception){fail("connectionError")}
    }
    private fun startAdvertising(){
        if(advertising)return
        val advertiser=adapter?.bluetoothLeAdvertiser ?: run{listener.status("advertiseFailed");return}
        advertiser.startAdvertising(AdvertiseSettings.Builder().setConnectable(true).setTimeout(0)
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).build(),
            AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE)).setIncludeDeviceName(false).build(),advertise)
    }
    fun stopScan(){
        handler.removeCallbacks(scanTimeout)
        if(scanning)runCatching{adapter?.bluetoothLeScanner?.stopScan(scan)}
        scanning=false
    }
    fun connect(id:String){
        if(!ready() || peer!=null || pendingBond!=null)return
        val device=devices[id] ?: return
        stopScan();deadline()
        if(device.bondState==BluetoothDevice.BOND_BONDED)connectBonded(device)
        else{
            pendingBond=device;deadline(120_000);listener.status("pairing")
            if(!device.createBond()){pendingBond=null;fail("pairingRejected")}
        }
    }
    private fun connectBonded(device:BluetoothDevice){
        check(granted() && device.bondState==BluetoothDevice.BOND_BONDED)
        clientRole=true;peer=device;mtu=23;receiver.clear()
        session.request(device.address,true)
        client=device.connectGatt(context,false,clientCallbacks,BluetoothDevice.TRANSPORT_LE)
        check(client!=null);deadline();listener.status("connecting")
    }
    fun accept(id:String){
        if(!ready() || clientRole || peer?.address!=id || !subscribed)return
        try{session.accept(id);sendControl("a"+nickname.take(18));listener.connected(id,nicknameForPeer)}
        catch(_:Exception){fail("connectionError")}
    }
    fun reject(){if(!clientRole && subscribed)runCatching{sendControl("r")};handler.postDelayed({disconnect()},300)}
    private var nicknameForPeer="One Triage"
    /** Explicit user disconnect also closes the remote app session, even if Android retains a shared BLE link. */
    fun requestDisconnect(){
        if(!ready() || session.phase!=ConversationSession.Phase.CONNECTED || activeSend || queue.isNotEmpty()){
            disconnect();return
        }
        try{disconnectAfterSend=true;sendControl("d")}
        catch(_:Exception){disconnect()}
    }
    fun approveAndSend(id:String,text:String,language:Language){
        if(!ready())return
        try{
            check(!activeSend && queue.isEmpty())
            session.approve(id,text,language)
            val payload=session.send(id,text,language)
            enqueue(payload,true)
        }catch(_:Exception){listener.status("sendRejected")}
    }
    private fun sendControl(text:String)=enqueue(text.toByteArray(Charsets.UTF_8),false)
    private fun enqueue(payload:ByteArray,isText:Boolean){
        check(peer?.bondState==BluetoothDevice.BOND_BONDED && !activeSend && queue.isEmpty())
        queue.addAll(ConversationProtocol.frames(nextId++,payload,mtu))
        notifyTextSent=isText;deadline();sendNext()
    }
    @Suppress("DEPRECATION")
    private fun sendNext(){
        if(activeSend)return
        val bytes=queue.firstOrNull()
        if(bytes==null){
            if(disconnectAfterSend){disconnect();return}
            if(session.phase==ConversationSession.Phase.AWAITING_APPROVAL)deadline() else handler.removeCallbacks(timeout)
            if(notifyTextSent){notifyTextSent=false;listener.sent()}
            return
        }
        check(granted() && peer?.bondState==BluetoothDevice.BOND_BONDED)
        activeSend=true
        if(clientRole){
            val gatt=client ?: error("Disconnected")
            val characteristic=gatt.getService(SERVICE)?.getCharacteristic(RX) ?: error("Missing characteristic")
            val ok=if(Build.VERSION.SDK_INT>=33)gatt.writeCharacteristic(characteristic,bytes,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS
                else {characteristic.value=bytes;characteristic.writeType=BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;gatt.writeCharacteristic(characteristic)}
            check(ok)
        }else{
            check(subscribed)
            val gatt=server ?: error("Disconnected")
            val characteristic=gatt.getService(SERVICE)?.getCharacteristic(TX) ?: error("Missing characteristic")
            val ok=if(Build.VERSION.SDK_INT>=33)gatt.notifyCharacteristicChanged(peer!!,characteristic,true,bytes)==BluetoothStatusCodes.SUCCESS
                else{characteristic.value=bytes;gatt.notifyCharacteristicChanged(peer!!,characteristic,true)}
            check(ok)
        }
    }
    private fun written(status:Int){
        check(status==BluetoothGatt.GATT_SUCCESS && activeSend && queue.isNotEmpty())
        queue.removeFirst();activeSend=false;deadline();sendNext()
    }
    private fun incoming(bytes:ByteArray){
        check(peer?.bondState==BluetoothDevice.BOND_BONDED && granted())
        val payload=receiver.accept(bytes) ?: return
        val kind=payload[0].toInt().toChar()
        when(kind){
            'h'->{
                check(!clientRole && session.phase==ConversationSession.Phase.DISCONNECTED)
                session.request(peer!!.address,true)
                nicknameForPeer=ConversationProtocol.utf8(payload.copyOfRange(1,payload.size)).take(18)
                require(nicknameForPeer.isNotBlank());deadline()
                listener.requested(peer!!.address,nicknameForPeer)
            }
            'a'->{
                check(clientRole && session.phase==ConversationSession.Phase.AWAITING_APPROVAL)
                nicknameForPeer=ConversationProtocol.utf8(payload.copyOfRange(1,payload.size)).take(18)
                require(nicknameForPeer.isNotBlank());session.accept(peer!!.address)
                handler.removeCallbacks(timeout);listener.connected(peer!!.address,nicknameForPeer)
            }
            'r'->{listener.status("rejected");disconnect()}
            'd'->{check(payload.size==1 && session.phase==ConversationSession.Phase.CONNECTED);disconnect()}
            'm'->{check(session.phase==ConversationSession.Phase.CONNECTED);listener.received(ConversationProtocol.parseText(payload))}
            else->error("Unsupported message")
        }
    }
    private val serverCallbacks=object:BluetoothGattServerCallback(){
        override fun onServiceAdded(status:Int,service:BluetoothGattService)=post {
            if(status==BluetoothGatt.GATT_SUCCESS)startAdvertising() else fail("advertiseFailed")
        }
        override fun onConnectionStateChange(device:BluetoothDevice,status:Int,newState:Int)=post {
            if(newState==BluetoothProfile.STATE_CONNECTED){
                // Android reports the shared physical link to our GATT server even when
                // this instance initiated it as a client. Cancelling that same peer here
                // races the client's encrypted subscription/indication handshake.
                if(peer!=null){
                    if(peer?.address!=device.address)server?.cancelConnection(device)
                    return@post
                }
                // SMP bonding may establish the physical link first. No subscription/message is
                // accepted until the OS bond and encrypted/MITM GATT checks both succeed.
                peer=device;clientRole=false;mtu=23;receiver.clear()
                deadline(if(device.bondState==BluetoothDevice.BOND_BONDED)30_000 else 120_000)
            }else if(peer?.address==device.address && !clientRole)disconnect()
        }
        override fun onMtuChanged(device:BluetoothDevice,value:Int)=post{if(peer?.address==device.address)mtu=value.coerceIn(23,247)}
        override fun onDescriptorWriteRequest(device:BluetoothDevice,id:Int,descriptor:BluetoothGattDescriptor,prepared:Boolean,response:Boolean,offset:Int,value:ByteArray)=post {
            val valid=device.address==peer?.address && device.bondState==BluetoothDevice.BOND_BONDED &&
                descriptor.uuid==CCCD && !prepared && offset==0 && value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            if(response)server?.sendResponse(device,id,if(valid)BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_WRITE_NOT_PERMITTED,offset,null)
            if(valid)subscribed=true
        }
        override fun onCharacteristicWriteRequest(device:BluetoothDevice,id:Int,c:BluetoothGattCharacteristic,prepared:Boolean,response:Boolean,offset:Int,value:ByteArray)=post {
            val valid=device.address==peer?.address && device.bondState==BluetoothDevice.BOND_BONDED && c.uuid==RX && !prepared && offset==0 && subscribed
            if(response)server?.sendResponse(device,id,if(valid)BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_WRITE_NOT_PERMITTED,offset,null)
            if(valid)incoming(value.clone())
        }
        override fun onNotificationSent(device:BluetoothDevice,status:Int)=post{if(device.address==peer?.address)written(status)}
    }
    private val clientCallbacks=object:BluetoothGattCallback(){
        override fun onConnectionStateChange(gatt:BluetoothGatt,status:Int,newState:Int)=post {
            if(gatt!=client)return@post
            if(status!=BluetoothGatt.GATT_SUCCESS || newState==BluetoothProfile.STATE_DISCONNECTED){disconnect();return@post}
            if(newState==BluetoothProfile.STATE_CONNECTED)check(gatt.discoverServices())
        }
        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(gatt:BluetoothGatt,status:Int)=post {
            check(gatt==client && status==BluetoothGatt.GATT_SUCCESS)
            val tx=gatt.getService(SERVICE)?.getCharacteristic(TX) ?: error("Wrong protocol")
            check(gatt.setCharacteristicNotification(tx,true))
            val descriptor=tx.getDescriptor(CCCD) ?: error("Missing subscription")
            val ok=if(Build.VERSION.SDK_INT>=33)gatt.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)==BluetoothStatusCodes.SUCCESS
                else{descriptor.value=BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;gatt.writeDescriptor(descriptor)}
            check(ok)
        }
        override fun onDescriptorWrite(gatt:BluetoothGatt,d:BluetoothGattDescriptor,status:Int)=post {
            check(gatt==client && status==BluetoothGatt.GATT_SUCCESS)
            subscribed=true;sendControl("h"+nickname.take(18));deadline()
        }
        override fun onCharacteristicWrite(gatt:BluetoothGatt,c:BluetoothGattCharacteristic,status:Int)=post{if(gatt==client)written(status)}
        override fun onCharacteristicChanged(gatt:BluetoothGatt,c:BluetoothGattCharacteristic,value:ByteArray)=post{if(gatt==client && c.uuid==TX)incoming(value.clone())}
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt:BluetoothGatt,c:BluetoothGattCharacteristic){
            val bytes=c.value?.clone() ?: return
            post{if(gatt==client && c.uuid==TX)incoming(bytes)}
        }
    }
    private fun fail(code:String){disconnect();listener.status(code)}
    fun disconnect(){
        handler.removeCallbacks(timeout)
        pendingBond=null;activeSend=false;notifyTextSent=false;disconnectAfterSend=false;queue.clear();receiver.clear();session.disconnect()
        val old=peer;peer=null;subscribed=false
        runCatching{client?.disconnect();client?.close()};client=null
        if(old!=null)runCatching{server?.cancelConnection(old)}
        listener.status("disconnected")
    }
    override fun close(){
        if(closed)return
        stopScan();disconnect()
        runCatching{adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertise)};advertising=false
        runCatching{server?.close()};server=null
        runCatching{context.unregisterReceiver(bonds)}
        handler.removeCallbacksAndMessages(null);closed=true
    }
    companion object{
        val SERVICE:UUID=UUID.fromString("27b4e43d-12c9-4f46-b20d-696574767432")
        private val RX:UUID=UUID.fromString("27b4e43e-12c9-4f46-b20d-696574767432")
        private val TX:UUID=UUID.fromString("27b4e43f-12c9-4f46-b20d-696574767432")
        private val CCCD:UUID=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        fun permissions():Array<String> = if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
