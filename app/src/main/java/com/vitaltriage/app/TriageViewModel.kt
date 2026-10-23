package com.vitaltriage.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitaltriage.core.*
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

enum class Screen { LANGUAGE, INPUT, REVIEW, ANALYZING, RESULT, SETTINGS }
data class UiState(
    val screen:Screen=Screen.INPUT, val language:Language=Language.EN, val resultLanguage:Language=Language.EN,
    val patientLanguages:Set<Language> = setOf(Language.EN), val activeSpeech:Language=Language.EN,
    val autoLanguage:Boolean=true, val symptoms:String="", val confirmed:Boolean=false,
    val messages:List<WalkieMessage> = emptyList(), val currentMessageId:Long=0, val currentMessageLanguage:String="en",
    val immediateDanger:Boolean=false, val demo:Boolean=BuildConfig.LOCAL_DEVELOPMENT, val scenario:Scenario=Scenario.MEDIUM,
    val endpoint:String=BuildConfig.DEFAULT_ENDPOINT, val accessToken:String="",
    val accessBusy:Boolean=false, val accessStorage:String?=null,
    val evaluation:Evaluation?=null, val error:String?=null, val speechError:String?=null,
    val backendFailure:String?=null, val speaking:Boolean=false, val automaticSession:Boolean=false,
    val speechTranslation:String?=null, val translationFailed:Boolean=false, val translationEngine:String="mlkit",
    val translationTarget:String="en", val translationBusy:Boolean=false,
    val listening:Boolean=false, val speechProcessing:Boolean=false, val speechLevel:Float=0f, val detectedLanguage:String?=null, val partialSpeech:String=""
)
fun interface AssessmentRepository { suspend fun assess(r:TriageRequest, endpoint:String, token:String):Evaluation }
class BackendFailure(val code:String):Exception(code)
class BackendRepository:AssessmentRepository {
    private val client=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS)
        .readTimeout(55,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).followRedirects(false).retryOnConnectionFailure(false).build()
    override suspend fun assess(r:TriageRequest,endpoint:String,token:String):Evaluation = withContext(Dispatchers.IO) {
        require(BackendConnectionPolicy.permits(endpoint,BuildConfig.LOCAL_DEVELOPMENT))
        val request=Request.Builder().url(endpoint.trimEnd('/')+"/v1/triage")
            .header("Authorization","Bearer $token")
            .post(Contract.gson.toJson(r).toRequestBody("application/json".toMediaType())).build()
        suspendCancellableCoroutine { continuation ->
            val call=client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:java.io.IOException) { continuation.resumeWith(Result.failure(e)) }
                override fun onResponse(call:Call,response:Response) {
                    val result=runCatching { response.use { decodeResponse(it,r) } }
                    continuation.resumeWith(result)
                }
            })
        }
    }
    private fun decodeResponse(response:Response,r:TriageRequest):Evaluation {
            if (!response.isSuccessful) {
                val error = response.peekBody(256).string()
                val code = runCatching { JsonParser.parseString(error).asJsonObject["error"].asString }.getOrNull()
                val allowed = setOf("AUTH","BUSY","TEST_BUDGET_LIMIT","INPUT_LIMIT","INSUFFICIENT_INFORMATION","OPENAI_AUTH","OPENAI_CREDIT",
                    "OPENAI_PERMISSION","OPENAI_MODEL","OPENAI_RATE_LIMIT","OPENAI_REQUEST","OPENAI_NETWORK","OPENAI_INVALID_RESPONSE",
                    "OPENAI_WRONG_LANGUAGE","OPENAI_INVALID_URGENCY","OPENAI_INVALID_COLOR","OPENAI_INVALID_SCORE","OPENAI_CONTRADICTORY_FLAGS","OPENAI_SAFETY_CONFLICT")
                throw BackendFailure(code?.takeIf { it in allowed } ?: "BACKEND_UNAVAILABLE")
            }
            val raw=response.body!!.charStream().use { reader ->
                val buffer=CharArray(32001); var total=0
                while(total<buffer.size) { val n=reader.read(buffer,total,buffer.size-total); if(n<0) break; total+=n }
                check(total<=32000); String(buffer,0,total)
            }
            val o=JsonParser.parseString(raw).asJsonObject
            require(o.keySet()==setOf("result","mode","failed"))
            require(o["mode"].isJsonPrimitive && o["mode"].asJsonPrimitive.isString)
            require(o["failed"].isJsonPrimitive && o["failed"].asJsonPrimitive.isBoolean)
            val mode=o["mode"].asString; require(BackendConnectionPolicy.acceptsSource(mode,BuildConfig.LOCAL_DEVELOPMENT))
            val failed=o["failed"].asBoolean
            val a=Contract.parse(o["result"].toString(),r.language)
            return Evaluation(if(failed) Safety.unknown(r.language) else Safety.enforce(a,r),mode,failed)
    }
}
class TriageViewModel(private val repository:AssessmentRepository=BackendRepository()):ViewModel() {
    private val mutable=MutableStateFlow(UiState())
    val state=mutable.asStateFlow()
    private var job:Job?=null
    private var nextMessageId=1L
    private var preferences:UserPreferences?=null
    fun attachPreferences(value:UserPreferences) {
        val first=preferences==null
        preferences=value; refreshPreferences()
        if(first) restoreAccess()
    }
    private fun restoreAccess() {
        val store=preferences?.backendAccess ?: return
        val endpoint=state.value.endpoint
        if(state.value.accessToken.isNotEmpty() || !BackendConnectionPolicy.permits(endpoint,false))return
        mutable.update { it.copy(accessBusy=true) }
        viewModelScope.launch {
            val saved=withContext(Dispatchers.IO) { store.load(endpoint) }
            mutable.update { current -> current.copy(accessBusy=false,
                accessToken=if(current.endpoint==endpoint && current.accessToken.isEmpty())saved.orEmpty() else current.accessToken,
                accessStorage=if(saved!=null && current.endpoint==endpoint)"saved" else null) }
        }
    }
    fun saveAccess() {
        if(state.value.accessBusy)return
        val store=preferences?.backendAccess ?: return
        val snapshot=state.value
        mutable.update { it.copy(accessBusy=true,accessStorage=null) }
        viewModelScope.launch {
            val success=withContext(Dispatchers.IO) { runCatching { store.save(snapshot.endpoint,snapshot.accessToken) }.isSuccess }
            mutable.update { it.copy(accessBusy=false,accessStorage=if(success)"saved" else "error") }
        }
    }
    fun forgetAccess() {
        if(state.value.accessBusy)return
        val store=preferences?.backendAccess ?: return
        invalidatePending()
        mutable.update { it.copy(accessBusy=true,accessToken="",confirmed=false,accessStorage=null) }
        viewModelScope.launch {
            val success=withContext(Dispatchers.IO) { runCatching { store.clear() }.isSuccess }
            mutable.update { it.copy(accessBusy=false,accessStorage=if(success)"forgotten" else "error") }
        }
    }
    fun refreshPreferences() {
        val p=preferences ?: return
        val previous=state.value
        if(previous.language!=p.language || previous.resultLanguage!=p.resultLanguage || previous.patientLanguages!=p.patientLanguages || previous.demo!=p.demo || previous.endpoint!=p.endpoint) invalidatePending()
        mutable.update { current ->
            val changed=current.language!=p.language || current.resultLanguage!=p.resultLanguage || current.patientLanguages!=p.patientLanguages || current.demo!=p.demo || current.endpoint!=p.endpoint
            current.copy(language=p.language,resultLanguage=p.resultLanguage,patientLanguages=p.patientLanguages,activeSpeech=p.activeSpeech,
                autoLanguage=p.autoLanguage,demo=p.demo,endpoint=p.endpoint,
                translationTarget=if(current.symptoms.isBlank())p.translationTarget.tag else current.translationTarget,
                accessToken=if(current.endpoint!=p.endpoint)"" else current.accessToken,
                confirmed=if(changed)false else current.confirmed,
                evaluation=if(changed)null else current.evaluation)
        }
    }
    private fun saveLanguagePreferences() {
        val p=preferences ?: return
        val s=state.value
        p.language=s.language; p.resultLanguage=s.resultLanguage; p.patientLanguages=s.patientLanguages
        p.activeSpeech=s.activeSpeech; p.autoLanguage=s.autoLanguage
    }
    private fun invalidatePending() {
        job?.cancel()
        mutable.update { if(it.screen==Screen.ANALYZING) it.copy(screen=Screen.INPUT,confirmed=false,evaluation=null) else it }
    }
    fun language(l:Language){ require(l in Language.uiLanguages); invalidatePending(); mutable.update{it.copy(language=l,confirmed=false,evaluation=null)}; saveLanguagePreferences() }
    fun resultLanguage(l:Language){invalidatePending();mutable.update{it.copy(resultLanguage=l,confirmed=false,evaluation=null)};preferences?.resultLanguage=l}
    fun toggleLanguage(l:Language){ invalidatePending(); mutable.update {
        val next=if(l in it.patientLanguages) it.patientLanguages-l else it.patientLanguages+l
        if(next.isEmpty()) it else it.copy(patientLanguages=next,activeSpeech=if(it.activeSpeech in next) it.activeSpeech else next.first(),confirmed=false)
    }; saveLanguagePreferences() }
    fun activeLanguage(l:Language){ mutable.update{if(l in it.patientLanguages) it.copy(activeSpeech=l) else it}; saveLanguagePreferences() }
    fun autoLanguage(v:Boolean){mutable.update{it.copy(autoLanguage=v)}; saveLanguagePreferences()}
    fun text(value:String){invalidatePending();mutable.update{it.copy(symptoms=value.take(4000),currentMessageLanguage=if(it.detectedLanguage==null)it.activeSpeech.tag else it.currentMessageLanguage,confirmed=false,evaluation=null,error=null,speechTranslation=null,translationFailed=false,translationBusy=false)}}
    fun danger(v:Boolean){invalidatePending();mutable.update{it.copy(immediateDanger=v,confirmed=false)}}
    fun confirm(v:Boolean){mutable.update{it.copy(confirmed=v && !it.translationBusy)}}
    fun demo(v:Boolean){if(v && !BuildConfig.LOCAL_DEVELOPMENT)return;invalidatePending();mutable.update{it.copy(demo=v,confirmed=false,evaluation=null,backendFailure=null)};preferences?.demo=v}
    fun scenario(s:Scenario){mutable.update{it.copy(scenario=s,evaluation=null,confirmed=false)}}
    fun endpoint(v:String){if(state.value.accessBusy)return;invalidatePending();mutable.update{it.copy(endpoint=v,accessToken=if(it.endpoint==v)it.accessToken else "",accessStorage=null,confirmed=false,evaluation=null)};preferences?.endpoint=v}
    fun token(v:String){if(state.value.accessBusy)return;invalidatePending();mutable.update{it.copy(accessToken=v,accessStorage=null,confirmed=false)}}
    fun navigate(screen:Screen){job?.cancel(); mutable.update{it.copy(screen=screen,listening=false,partialSpeech="",confirmed=if(it.screen==Screen.ANALYZING)false else it.confirmed)}}
    fun review(){ if(state.value.symptoms.isBlank()) mutable.update{it.copy(error="empty")} else navigate(Screen.REVIEW) }
    fun permission(granted:Boolean){mutable.update{it.copy(speechError=if(granted)null else "permission",listening=false)}}
    fun speechError(key:String){mutable.update{it.copy(speechError=key,listening=false,speechProcessing=false,partialSpeech="")}}
    fun listening(v:Boolean){mutable.update{it.copy(listening=v,speechProcessing=false,speechLevel=0f,speechError=null,partialSpeech="",detectedLanguage=if(v)null else it.detectedLanguage)}}
    fun automaticSession(value:Boolean){mutable.update { it.copy(automaticSession=value) }}
    fun endListening(){mutable.update { it.copy(listening=false,speechProcessing=false,speechLevel=0f,partialSpeech="") }}
    fun processingSpeech(){mutable.update{it.copy(listening=true,speechProcessing=true,speechLevel=0f,speechError=null)}}
    fun speechLevel(value:Float){if(value.isFinite())mutable.update{if(it.listening)it.copy(speechLevel=value.coerceIn(0f,1f))else it}}
    fun speaking(value:Boolean){mutable.update{it.copy(speaking=value)}}
    fun partial(s:String){mutable.update{it.copy(partialSpeech=s)}}
    fun detected(s:String?){mutable.update{it.copy(detectedLanguage=s)}}
    fun transcript(s:String,englishTranslation:String?=null,translationFailed:Boolean=false,translationEngine:String="mlkit"){
        val incoming=s.trim()
        if(incoming.isEmpty()){speechError("speechNoMatch");return}
        val combined=listOf(state.value.symptoms.trim(),incoming).filter{it.isNotEmpty()}.joinToString("\n")
        if(combined.length>4000) {
            mutable.update { it.copy(confirmed=false,evaluation=null) }
            speechError("speechTooLong")
            return
        }
        text(combined); listening(false)
        mutable.update { it.copy(speechTranslation=englishTranslation?.takeIf(String::isNotBlank)?.take(4000),translationFailed=translationFailed,translationEngine=translationEngine) }
    }
    private fun snapshot(s:UiState)=WalkieMessage(s.currentMessageId,s.symptoms,s.currentMessageLanguage,
        s.speechTranslation,s.translationFailed,s.translationEngine,s.translationTarget)
    /** Each captured turn is a separate candidate for assessment, never implicit patient concatenation. */
    fun utterance(value:String,language:String,translation:String?=null,translationFailed:Boolean=false,engine:String="mlkit",target:String="en") {
        val incoming=value.trim()
        if(incoming.isEmpty()){speechError("speechNoMatch");return}
        if(incoming.length>4000){mutable.update { it.copy(confirmed=false,evaluation=null) };speechError("speechTooLong");return}
        if(Language.find(language)==null || Language.find(target)==null){speechError("speechLanguageUnsupported");return}
        invalidatePending()
        mutable.update { old -> old.copy(screen=Screen.INPUT,
            messages=boundedMessages(old.messages+listOfNotNull(snapshot(old).takeIf { it.original.isNotBlank() })),
            currentMessageId=nextMessageId++,currentMessageLanguage=language,symptoms=incoming,
            confirmed=false,immediateDanger=false,evaluation=null,error=null,speechError=null,backendFailure=null,
            listening=false,speechProcessing=false,partialSpeech="",detectedLanguage=language,
            speechTranslation=translation?.takeIf(String::isNotBlank)?.take(4000),
            translationFailed=translationFailed,translationEngine=engine,translationTarget=target,translationBusy=false) }
    }
    /** Explicit bubble selection revokes consent and restores only that message's original text. */
    fun selectMessage(id:Long) {
        if(state.value.listening || state.value.screen==Screen.ANALYZING)return
        val selected=state.value.messages.firstOrNull { it.id==id } ?: return
        invalidatePending()
        mutable.update { old -> old.copy(screen=Screen.INPUT,
            messages=boundedMessages(old.messages.filterNot { it.id==id }+listOfNotNull(snapshot(old).takeIf { it.original.isNotBlank() })),
            currentMessageId=selected.id,currentMessageLanguage=selected.language,symptoms=selected.original,
            detectedLanguage=selected.language,speechTranslation=selected.translation,translationFailed=selected.translationFailed,
            translationEngine=selected.translationEngine,translationTarget=selected.translationTarget,translationBusy=false,confirmed=false,immediateDanger=false,evaluation=null,error=null,backendFailure=null) }
    }
    fun translationStarted(){mutable.update { it.copy(translationBusy=true,confirmed=false) }}
    fun translationCancelled(){mutable.update { if(it.translationBusy)it.copy(translationBusy=false,confirmed=false) else it }}
    fun reviewedTranslation(id:Long,original:String,translated:String?,target:String,engine:String,failed:Boolean) {
        mutable.update { current ->
            if(current.currentMessageId!=id || current.symptoms!=original || current.screen!=Screen.REVIEW || !current.translationBusy)current
            else current.copy(translationBusy=false,confirmed=false,speechTranslation=translated?.takeIf(String::isNotBlank)?.take(4000),
                translationTarget=target,translationEngine=engine,translationFailed=failed)
        }
    }
    fun reset(){job?.cancel();mutable.update{it.copy(screen=Screen.INPUT,symptoms="",messages=emptyList(),translationBusy=false,confirmed=false,immediateDanger=false,evaluation=null,error=null,speechError=null,partialSpeech="",speechTranslation=null,translationFailed=false,detectedLanguage=null)}}
    fun analyze(){
        if(state.value.screen==Screen.ANALYZING || state.value.translationBusy)return
        val s=state.value
        val request=TriageRequest(s.symptoms,s.resultLanguage.tag,listOf(s.currentMessageLanguage),s.confirmed,s.immediateDanger,s.scenario.name)
        if(!Safety.valid(request)) {mutable.update{it.copy(error="confirm")};return}
        job?.cancel()
        mutable.update{it.copy(screen=Screen.ANALYZING,error=null,evaluation=null,backendFailure=null)}
        job=viewModelScope.launch {
            val evaluation=try {
                if(s.demo){delay(650);Demo.evaluate(request)}else repository.assess(request,s.endpoint,s.accessToken)
            } catch(c:CancellationException){throw c}
              catch(e:Exception){
                  mutable.update{it.copy(backendFailure=if(e is BackendFailure)e.code else "BACKEND_UNAVAILABLE")}
                  Evaluation(Safety.unknown(request.language),if(s.demo)"DEMO" else "UNAVAILABLE",true)
              }
            ensureActive()
            // Technical failures are interface messages, not an English assessment mislabeled
            // as the requested processing language. Successful model results stay untouched.
            val displayed=if(evaluation.failed)evaluation.copy(result=Safety.unknown(s.language.tag)) else evaluation
            mutable.update{it.copy(screen=Screen.RESULT,evaluation=displayed,confirmed=false)}
        }
    }
}
