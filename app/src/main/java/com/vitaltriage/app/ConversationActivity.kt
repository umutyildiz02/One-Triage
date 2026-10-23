package com.vitaltriage.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.vitaltriage.core.Language
import kotlinx.coroutines.*

/** Source Pairing/Conversation/Peers layouts; no automatic text sharing or assessment. */
class ConversationActivity:LegacyProfileActivity(),ConversationBluetooth.Listener {
    private val vm:TriageViewModel by viewModels()
    private val session:ConversationMemoryViewModel by viewModels()
    private var radio:ConversationBluetooth?=null
    private lateinit var speech:SpeechController
    private lateinit var output:SpeechOutput
    private val peers=linkedMapOf<String,String>()
    private var connectedId:String?=null
    private var connectedName=""
    private var chat=false
    private var searching=false
    private var draft:String
        get()=session.draft
        set(value){session.draft=value}
    private var pending:ConversationMessage?=null
    private var previousTranscript:String
        get()=session.previousTranscript
        set(value){session.previousTranscript=value}
    private var lastSpeechError:String?=null
    private var stopping=false
    private var translationJob:Job?=null
    internal var automaticallyReading=false
        private set
    internal var translationModelDirectory:()->java.io.File={NllbModels.directory(applicationContext)}
    @Volatile private var translationEngine:NllbEngine?=null
    private val messages get()=session.messages
    private val translationQueue=java.util.ArrayDeque<ConversationMessage>()
    private var list:RecyclerView?=null
    private val messageAdapter=MessageAdapter()
    private val nearby=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->
        if(grants.values.all{it})beginSearch() else status("permission")
    }
    private val microphone=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        vm.permission(granted);if(granted)speech.start()
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        if(!BuildConfig.LOCAL_DEVELOPMENT)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        vm.attachPreferences(UserPreferences(this))
        speech=SpeechController(this,vm)
        output=SpeechOutput(this,vm::speaking){vm.speechError("ttsUnavailable")}
        showPairing()
        onBackPressedDispatcher.addCallback(this){finish()}
        lifecycleScope.launch {
            vm.state.collect { state->
                if(chat){
                    findViewById<View>(R.id.buttonMic)?.apply{
                        isEnabled=true
                        scaleX=1f+state.speechLevel.coerceIn(0f,1f)*.08f;scaleY=scaleX
                        contentDescription=UiCopy.text(if(state.listening)"stop" else "record",state.language)
                    }
                    listOf(R.id.leftLine,R.id.centerLine,R.id.rightLine).forEach { id->
                        findViewById<View>(id)?.visibility=if(state.listening)View.VISIBLE else View.GONE
                    }
                }
                if(!state.listening && state.symptoms.isNotBlank() && state.symptoms!=previousTranscript){
                    previousTranscript=state.symptoms;draft=state.symptoms
                    if(chat && !isFinishing)editDraft()
                }
                if(!state.listening && !state.speaking)processNextTranslation()
                if(state.speechError!=lastSpeechError){
                    lastSpeechError=state.speechError
                    state.speechError?.let{if(!isFinishing)Toast.makeText(this@ConversationActivity,UiCopy.text(it,state.language),Toast.LENGTH_LONG).show()}
                }
            }
        }
    }
    override fun onStart(){
        super.onStart();stopping=false
        if(radio==null)radio=ConversationBluetooth(this,UserPreferences(this).nickname.ifBlank{"One Triage"},this)
    }
    override fun onStop(){
        stopping=true
        translationQueue.forEach { it.translationError=true };translationQueue.clear()
        translationEngine?.cancel();translationJob?.cancel()
        speech.releaseModel();vm.listening(false);stopReading()
        radio?.close();radio=null
        connectedId=null;pending=null
        super.onStop()
    }
    override fun onResume(){
        super.onResume()
        if(chat && connectedId==null)showPairing()
    }
    override fun onDestroy(){speech.destroy();output.close();super.onDestroy()}
    override fun refreshProfile()=Unit
    private fun showPairing(){
        chat=false;list=null
        setContentView(R.layout.vv_fragment_pairing)
        findViewById<View>(R.id.exitButton).apply{contentDescription=getString(R.string.vv_bt_disconnect);setOnClickListener{finish()}}
        findViewById<View>(R.id.settingsButton).apply{contentDescription=UiCopy.text("settings",UserPreferences(this@ConversationActivity).language);setOnClickListener{startActivity(Intent(this@ConversationActivity,LegacySettingsActivity::class.java))}}
        findViewById<ImageButton>(R.id.searchButton).apply{
            setImageResource(R.drawable.vv_reload_icon);contentDescription=getString(R.string.vv_bt_search)
            setOnClickListener{
                if(searching){radio?.stopScan();searching=false;status("searchStopped")}
                else AlertDialog.Builder(this@ConversationActivity).setTitle(R.string.vv_bt_title_fragment_conversation)
                    .setMessage(R.string.vv_bt_disclosure).setPositiveButton(R.string.vv_bt_search){_,_->nearby.launch(ConversationBluetooth.permissions())}
                    .setNegativeButton(android.R.string.cancel,null).show()
            }
        }
        renderPeers()
    }
    private fun beginSearch(){peers.clear();searching=true;radio?.startDiscovery();renderPeers()}
    override fun discovered(id:String,name:String){peers[id]=name;renderPeers()}
    private fun renderPeers(){
        if(chat)return
        val view=findViewById<ListView>(R.id.list_view)
        val ids=peers.keys.toList()
        view.visibility=if(ids.isEmpty())View.GONE else View.VISIBLE
        findViewById<View>(R.id.noDevices).visibility=if(ids.isEmpty())View.VISIBLE else View.GONE
        // RTranslator PairingFragment moves discovery copy below a non-empty peer list.
        val description=findViewById<TextView>(R.id.discoveryDescription)
        val bottom=findViewById<TextView>(R.id.discoveryDescriptionBottom)
        description.visibility=if(ids.isEmpty())View.VISIBLE else View.GONE
        bottom.visibility=if(ids.isEmpty())View.INVISIBLE else View.VISIBLE
        bottom.text=description.text
        view.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,ids.map{peers[it]+" · "+it.takeLast(5)})
        view.setOnItemClickListener{_,_,position,_->
            val id=ids[position]
            AlertDialog.Builder(this).setTitle(peers[id]).setMessage(R.string.vv_bt_disclosure)
                .setPositiveButton(R.string.vv_bt_connection){_,_->radio?.connect(id)}
                .setNegativeButton(android.R.string.cancel,null).show()
        }
    }
    override fun requested(id:String,name:String){
        AlertDialog.Builder(this).setTitle(name).setMessage(R.string.vv_bt_disclosure)
            .setPositiveButton(R.string.vv_bt_accept){_,_->radio?.accept(id)}
            .setNegativeButton(R.string.vv_bt_reject){_,_->radio?.reject()}
            .setOnCancelListener{radio?.reject()}.show()
    }
    override fun connected(id:String,name:String){session.bindPeer(id);connectedId=id;connectedName=name;showConversation()}
    private fun showConversation(){
        chat=true
        setContentView(R.layout.vv_fragment_conversation)
        findViewById<View>(R.id.exitButton).apply{contentDescription=getString(R.string.vv_bt_disconnect);setOnClickListener{finish()}}
        findViewById<View>(R.id.settingsButton).apply{contentDescription=UiCopy.text("settings",UserPreferences(this@ConversationActivity).language);setOnClickListener{startActivity(Intent(this@ConversationActivity,LegacySettingsActivity::class.java))}}
        findViewById<View>(R.id.searchButton).visibility=View.GONE
        val chatView=layoutInflater.inflate(R.layout.vv_fragment_conversation_main,null)
        val peersView=layoutInflater.inflate(R.layout.vv_fragment_peers_info,null)
        val pages=listOf(chatView,peersView)
        val pager=findViewById<ViewPager>(R.id.tabs)
        pager.adapter=object:PagerAdapter(){
            override fun getCount()=pages.size
            override fun isViewFromObject(view:View,item:Any)=view===item
            override fun instantiateItem(container:ViewGroup,position:Int):Any=pages[position].also{container.addView(it)}
            override fun destroyItem(container:ViewGroup,position:Int,item:Any){container.removeView(item as View)}
        }
        findViewById<View>(R.id.tabConversation).setOnClickListener{pager.currentItem=0}
        findViewById<View>(R.id.tabConnection).setOnClickListener{pager.currentItem=1}
        list=chatView.findViewById<RecyclerView>(R.id.recycler_view).apply{
            visibility=View.VISIBLE;layoutManager=LinearLayoutManager(this@ConversationActivity).apply{stackFromEnd=true};adapter=messageAdapter
        }
        chatView.findViewById<TextView>(R.id.description).apply{text=getString(R.string.vv_bt_share_notice);visibility=if(messages.isEmpty())View.VISIBLE else View.GONE}
        chatView.findViewById<View>(R.id.buttonKeyboard).apply{contentDescription=getString(R.string.vv_bt_hint_edit_text_message);setOnClickListener{editDraft()}}
        chatView.findViewById<View>(R.id.buttonSound).apply{
            contentDescription=UiCopy.text("readAloud",vm.state.value.language)
            setOnClickListener{messages.lastOrNull()?.let(::speakMessage)}
        }
        chatView.findViewById<View>(R.id.buttonMic).setOnLongClickListener {
            val available=vm.state.value.patientLanguages.toList()
            AlertDialog.Builder(this).setTitle(R.string.vv_patient_languages)
                .setItems(available.map{it.label}.toTypedArray()){_,position->
                    speech.releaseModel();vm.listening(false);vm.activeLanguage(available[position]);vm.autoLanguage(false)
                }.show();true
        }
        chatView.findViewById<View>(R.id.buttonMic).setOnClickListener{
            if(translationJob?.isActive==true){
                AlertDialog.Builder(this).setMessage(R.string.vv_bt_model_busy)
                    .setPositiveButton(R.string.vv_bt_cancel_translation){_,_->translationEngine?.cancel();translationJob?.cancel()}
                    .setNegativeButton(android.R.string.cancel,null).show();return@setOnClickListener
            }
            stopReading()
            if(vm.state.value.listening)speech.stop()
            else AlertDialog.Builder(this).setMessage(UiCopy.text("speechPrivacy",vm.state.value.language))
                .setPositiveButton(UiCopy.text("record",vm.state.value.language)){_,_->microphone.launch(Manifest.permission.RECORD_AUDIO)}
                .setNegativeButton(android.R.string.cancel,null).show()
        }
        peersView.findViewById<TextView>(R.id.discoveryDescription).text=getString(R.string.vv_bt_disclosure)
        peersView.findViewById<ListView>(R.id.connection_list).apply{
            adapter=ArrayAdapter(this@ConversationActivity,android.R.layout.simple_list_item_1,listOf(connectedName))
            setOnItemClickListener{_,_,_,_->AlertDialog.Builder(this@ConversationActivity).setTitle(connectedName)
                .setPositiveButton(R.string.vv_bt_disconnect){_,_->radio?.requestDisconnect()}
                .setNegativeButton(android.R.string.cancel,null).show()}
        }
    }
    private fun editDraft(){
        if(connectedId==null || pending!=null)return
        val field=EditText(this).apply{setText(draft);maxLines=8;filters=arrayOf(android.text.InputFilter.LengthFilter(4000));hint=getString(R.string.vv_bt_hint_edit_text_message)}
        val dialog=AlertDialog.Builder(this).setTitle(connectedName).setMessage(R.string.vv_bt_share_notice).setView(field)
            .setPositiveButton(R.string.vv_bt_share,null).setNegativeButton(android.R.string.cancel){_,_->draft=field.text.toString()}.create()
        dialog.setOnShowListener{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                val text=field.text.toString()
                if(text.isBlank()){field.error=getString(R.string.vv_bt_hint_edit_text_message);return@setOnClickListener}
                val id=connectedId ?: return@setOnClickListener
                if(pending!=null)return@setOnClickListener
                draft=text;pending=ConversationMessage(text,vm.state.value.activeSpeech,true)
                radio?.approveAndSend(id,text,vm.state.value.activeSpeech);dialog.dismiss()
            }
        }
        dialog.show()
    }
    override fun received(message:ConversationProtocol.Message){
        val item=ConversationMessage(message.original,message.language,false)
        addMessage(item)
        translate(item)
    }
    override fun sent(){pending?.let{addMessage(it)};pending=null;draft="";Toast.makeText(this,R.string.vv_bt_sent,Toast.LENGTH_SHORT).show()}
    private fun addMessage(message:ConversationMessage){
        session.add(message)
        findViewById<View>(R.id.description)?.visibility=View.GONE
        messageAdapter.notifyDataSetChanged();list?.scrollToPosition(messages.lastIndex)
    }
    private fun translate(item:ConversationMessage){
        val prefs=UserPreferences(this)
        if(!prefs.englishVoiceTranslation || item.language==prefs.translationTarget)return
        if(translationQueue.size>=20){item.translationError=true;messageAdapter.notifyDataSetChanged();return}
        translationQueue.addLast(item)
        processNextTranslation()
    }
    private fun processNextTranslation(){
        if(stopping || isFinishing || automaticallyReading || vm.state.value.listening || vm.state.value.speaking ||
            translationJob?.isActive==true || translationQueue.isEmpty())return
        val item=translationQueue.removeFirst()
        if(item !in messages){processNextTranslation();return}
        val prefs=UserPreferences(this)
        // LAZY avoids re-entry from speech state callbacks before this job is assigned.
        translationJob=lifecycleScope.launch(start=CoroutineStart.LAZY){
            try{
                speech.releaseForTranslation()
                val target=prefs.translationTarget
                val result=withContext(Dispatchers.IO){
                    check(!SpeechMemory.isLow(applicationContext))
                    if(prefs.translationEngine=="nllb"){
                        val directory=translationModelDirectory()
                        check(NllbModels.verified(directory))
                        NllbEngine(directory,memoryPressure={SpeechMemory.isLow(applicationContext)}).use{engine->
                            translationEngine=engine
                            if(!isActive)engine.cancel()
                            engine.translate(item.text,item.language.tag,target.tag)
                        }
                    }else{LocalTextTranslation.translate(item.text,item.language.tag,target.tag)}
                }
                ensureActive()
                item.translated=result;item.translatedLanguage=target;item.provider=prefs.translationEngine
                session.trim()
            }catch(cancel:CancellationException){item.translationError=true;throw cancel}
             catch(_:Exception){item.translationError=true}
             catch(_:LinkageError){item.translationError=true}
            finally{
                translationEngine=null;translationJob=null
                messageAdapter.notifyDataSetChanged()
                if(!stopping && !isFinishing && connectedId!=null && item in messages && !item.translationError &&
                    item.translated!=null && UserPreferences(this@ConversationActivity).readTranslatedTurns) {
                    // Hold the queue from submission until the real TTS completion callback.
                    // No microphone restart, sharing or assessment is triggered by peer playback.
                    automaticallyReading=true
                    output.play(item.translated!!,item.translatedLanguage!!) {
                        automaticallyReading=false
                        if(!stopping)processNextTranslation()
                    }
                } else if(!stopping)processNextTranslation()
            }
        }
        translationJob?.start()
    }
    private fun stopReading() {
        automaticallyReading=false
        output.stop()
    }
    private fun speakMessage(item:ConversationMessage) {
        if(translationJob?.isActive==true)return
        speech.close();vm.listening(false)
        if(automaticallyReading) {
            stopReading()
            processNextTranslation()
        } else output.toggle(item.translated ?: item.text,item.translatedLanguage ?: item.language)
    }
    override fun status(code:String){
        if(code=="disconnected"){
            connectedId=null;pending=null
            stopReading()
            if(chat && !stopping && !isFinishing)showPairing()
        }
        if(code=="sendRejected")pending=null
        val resource=when(code){
            "permission"->R.string.vv_bt_error_missing_location_permissions;"unsupported"->R.string.vv_bt_error_missing_bluetooth_le
            "searching"->R.string.vv_bt_status_searching;"discoverable"->R.string.vv_bt_status_discoverable;"searchStopped"->R.string.vv_bt_status_searchStopped
            "bluetoothOff"->R.string.vv_bt_status_bluetoothOff;"pairing"->R.string.vv_bt_status_pairing;"connecting"->R.string.vv_bt_status_connecting
            "disconnected"->R.string.vv_bt_status_disconnected;"scanFailed"->R.string.vv_bt_status_scanFailed;"advertiseFailed"->R.string.vv_bt_status_advertiseFailed
            "timeout"->R.string.vv_bt_status_timeout;"pairingRejected"->R.string.vv_bt_status_pairingRejected;"rejected"->R.string.vv_bt_status_rejected
            "sendRejected"->R.string.vv_bt_status_sendRejected;else->R.string.vv_bt_status_connectionError
        }
        if(!chat){
            findViewById<TextView>(R.id.discoveryDescription)?.setText(resource)
            findViewById<TextView>(R.id.discoveryDescriptionBottom)?.setText(resource)
        }
        else if(!isFinishing)Toast.makeText(this,resource,Toast.LENGTH_LONG).show()
        if(code in setOf("searchStopped","scanFailed","permission","unsupported","bluetoothOff"))searching=false
    }
    private inner class MessageAdapter:RecyclerView.Adapter<MessageHolder>(){
        override fun onCreateViewHolder(parent:ViewGroup,type:Int)=MessageHolder(FrameLayout(parent.context).apply{
            layoutParams=RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        override fun getItemCount()=messages.size
        override fun onBindViewHolder(holder:MessageHolder,position:Int){
            val item=messages[position];val dp=resources.displayMetrics.density
            val content=TextView(this@ConversationActivity).apply{
                val prefs=UserPreferences(this@ConversationActivity)
                val target=prefs.translationTarget
                val targetText=item.targetText(target)
                text=targetText ?: UiCopy.text("targetTranslationUnavailable",prefs.language)
                if(targetText!=null && item.translated!=null && item.provider=="mlkit")append("\nPowered by Google Translate")
                setOnClickListener {
                    AlertDialog.Builder(this@ConversationActivity).setTitle(UiCopy.text("review",prefs.language))
                        .setMessage(item.text).setPositiveButton(android.R.string.ok,null).show()
                }
                textSize=18f;setTextColor(Color.rgb(0,111,175));typeface=ResourcesCompat.getFont(context,R.font.nunito_sans)
                textDirection=View.TEXT_DIRECTION_FIRST_STRONG
                setPadding((16*dp).toInt(),(8*dp).toInt(),(16*dp).toInt(),(8*dp).toInt())
                background=GradientDrawable().apply{setColor(Color.rgb(224,242,241));cornerRadius=18*dp}
                setOnLongClickListener{speakMessage(item);true}
            }
            holder.root.removeAllViews()
            holder.root.addView(content,FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,
                if(item.outgoing)Gravity.END else Gravity.START).apply{topMargin=(8*dp).toInt();bottomMargin=(8*dp).toInt();if(item.outgoing)marginStart=(80*dp).toInt() else marginEnd=(80*dp).toInt()})
        }
    }
    private class MessageHolder(val root:FrameLayout):RecyclerView.ViewHolder(root)
}
