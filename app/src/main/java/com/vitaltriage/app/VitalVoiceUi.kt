package com.vitaltriage.app

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitaltriage.core.*

private val VoiceBlue = Color(0xFFDDF7E8)
private val VoiceDark = Color(0xFF096827)
private val VoiceInk = Color(0xFF172622)
private val VoiceBubble = Color(0xFFD5F2FC)
private val NunitoSemibold = FontFamily(Font(R.font.nunito_sans_semibold, FontWeight.SemiBold))
private val NunitoLight = FontFamily(Font(R.font.nunito_sans_light))
private val Nunito = FontFamily(Font(R.font.nunito_sans))

@Composable
fun VitalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = VoiceDark, onPrimary = Color.White, primaryContainer = VoiceBlue,
            background = Color.White, surface = Color.White, onSurface = VoiceInk,
            secondary = VoiceBlue, onSurfaceVariant = Color(0xFF5A5A5A)
        ),
        content = content
    )
}

/** A single WalkieTalkie surface. Screen states select dialogs, never replacement pages.
 * Interactions originate in RTranslator-2.00; visual tokens follow the owner's OneVoice references.
 * Network, consent and triage decisions remain in TriageViewModel/core.
 */
@Composable
fun VitalApp(
    s: UiState,
    vm: TriageViewModel,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onPermissionSettings: () -> Unit,
    onSettings: (() -> Unit)? = null,
    onLanguage: (() -> Unit)? = null,
    onSpeak: (() -> Unit)? = null,
    onImportAudio: (() -> Unit)? = null,
    onReview: (() -> Unit)? = null,
    onPrepareTranslation: (() -> Unit)? = null
) {
    fun t(key: String) = UiCopy.text(key, s.language)
    var editor by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var information by remember { mutableStateOf(false) }
    var speechDisclosure by remember { mutableStateOf<Boolean?>(null) }
    var speechDisclosed by remember { mutableStateOf(false) }
    val busy = s.screen == Screen.ANALYZING
    fun go(screen: Screen) { onStop(); vm.navigate(screen) }
    fun openEditor() { go(Screen.INPUT); editor = true }
    fun requestReview() { onStop(); if(onReview!=null)onReview() else vm.review() }
    fun startVoice(automatic: Boolean) {
        if ((s.listening || s.automaticSession) && s.autoLanguage == automatic) {
            onRecord()
        } else {
            onStop()
            vm.listening(false)
            vm.autoLanguage(automatic)
            vm.navigate(Screen.INPUT)
            onRecord()
        }
    }
    fun requestVoice(automatic: Boolean) {
        if (speechDisclosed || s.listening || s.automaticSession) startVoice(automatic)
        else speechDisclosure = automatic
    }
    BackHandler(!busy && s.screen in setOf(Screen.INPUT, Screen.RESULT) && !editor && !details && !information && speechDisclosure == null) { if(s.listening || s.automaticSession || s.speaking)onStop() /* Original default destination; back stops capture. */ }
    BackHandler(busy) { go(Screen.REVIEW) }
    CompositionLocalProvider(LocalLayoutDirection provides if (s.language == Language.AR) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        val navBottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(Modifier.fillMaxSize().background(VoiceBlue).drawBehind {
            val height=navBottom.toPx()
            drawRect(Color.White,androidx.compose.ui.geometry.Offset(0f,size.height-height),androidx.compose.ui.geometry.Size(size.width,height))
        }.safeDrawingPadding().imePadding().testTag("walkie-home")) {
            // Brand and physical controls keep their reference positions, including in RTL locales.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight().padding(start=24.dp, end=8.dp), contentAlignment = Alignment.CenterStart) {
                    Image(
                        painterResource(R.drawable.onevoice_logo), "OneVoice",
                        Modifier.fillMaxWidth().heightIn(max=42.dp).testTag("onevoice-logo"),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit, alignment = Alignment.CenterStart
                    )
                }
                VoiceIcon(R.drawable.vv_sound_icon, t(if(s.speaking) "stopReading" else "readAloud"), "sound", !busy && !s.listening && onSpeak != null) { onSpeak?.invoke() }
                VoiceIcon(R.drawable.settings_icon, t("settings"), "settings", !busy) { if (onSettings != null) onSettings() else go(Screen.SETTINGS) }
                Spacer(Modifier.width(8.dp))
            }
            }
            Surface(
                onClick = { if (onLanguage != null) onLanguage() else go(Screen.LANGUAGE) }, enabled = !busy && !s.listening,
                color = VoiceDark, shape = RoundedCornerShape(dimensionResource(R.dimen.voice_language_radius)),
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = dimensionResource(R.dimen.voice_language_margin), vertical = 16.dp
                ).testTag("language-picker")
            ) {
                Text(s.activeSpeech.label, Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = Color.White, fontFamily = NunitoSemibold, fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xFFFCFDFD))
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .semantics {
                            role = Role.Button
                            if (busy || s.listening) disabled()
                            onClick(label = t("write")) { if (!busy && !s.listening) { openEditor(); true } else false }
                        }
                        .pointerInput(busy, s.listening) {
                            if (!busy && !s.listening) detectTapGestures(onTap = { openEditor() })
                        }
                        .testTag("write").verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalArrangement = if (s.symptoms.isBlank() && s.partialSpeech.isBlank() && s.messages.isEmpty()) Arrangement.Top else Arrangement.Bottom
                ) {
                    if (s.symptoms.isBlank() && s.partialSpeech.isBlank() && s.messages.isEmpty()) {
                        Text(t("voiceDescription"), Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                            color = Color(0xFFBDBDBD), fontSize = 14.sp, textAlign = TextAlign.Center)
                    } else {
                        val displayed=(s.messages+listOfNotNull(
                            WalkieMessage(s.currentMessageId,s.symptoms,s.currentMessageLanguage,s.speechTranslation,s.translationFailed,s.translationEngine,s.translationTarget)
                                .takeIf { it.original.isNotBlank() })).sortedBy { it.id }
                        displayed.forEach { message ->
                            val active=message.id==s.currentMessageId
                            Row(Modifier.fillMaxWidth().padding(start = 72.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                                Surface(color=VoiceBubble,shape=RoundedCornerShape(18.dp),
                                    modifier=Modifier.testTag(if(active)"transcript" else "message-${message.id}")
                                        .clickable(enabled=!busy && !s.listening,onClickLabel=t("review")) {
                                            if(active)openEditor() else vm.selectMessage(message.id)
                                        }) {
                                    val targetText=message.targetText()
                                    Column(Modifier.padding(horizontal=16.dp,vertical=10.dp)) {
                                        Text(targetText ?: t(if(active && s.translationBusy)"translationProcessing" else "targetTranslationUnavailable"),
                                            Modifier.testTag(if(message.translation!=null && targetText!=null)
                                                (if(active)"speech-translation" else "translation-${message.id}") else "message-display-${message.id}"),
                                            color=Color(0xFF006FAF),fontSize=18.sp,lineHeight=23.sp,
                                            style=TextStyle(textDirection=TextDirection.ContentOrLtr))
                                        if(active && targetText==null && !s.translationBusy && !s.listening && onPrepareTranslation!=null) {
                                            TranslationReadiness(s,onPrepareTranslation)
                                        }
                                        if(message.translation!=null && targetText!=null && message.translationEngine!="nllb")
                                            GoogleTranslationBadge()
                                    }
                                }
                            }
                        }
                        // Partial source transcripts stay in state; only final target text is shown here.
                    }
                    if (s.listening) {
                        Text(t(if(s.speechProcessing) "speechProcessing" else "listening") + if(s.autoLanguage && !s.speechProcessing) "\n" + t("auto") else "", Modifier.padding(16.dp), color = VoiceDark, fontFamily = NunitoLight, fontSize = 12.sp)
                        s.detectedLanguage?.let { Text(it, Modifier.padding(horizontal = 16.dp), fontSize = 12.sp) }
                    }
                    if(!s.listening) s.detectedLanguage?.let { tag ->
                        Text(t("detectedSpeech")+": "+(Language.entries.firstOrNull { it.tag==tag }?.label ?: tag),
                            Modifier.padding(horizontal=16.dp).testTag("detected-language"),fontSize=12.sp,color=VoiceDark)
                    }
                    s.speechError?.let { error ->
                        Text(t(error), Modifier.padding(16.dp).testTag("speech-error"), color = Color(0xFF812332), fontSize = 14.sp)
                        if (error == "permission") TextButton(onClick = onPermissionSettings) { Text(t("systemSettings")) }
                        if(error in setOf("speechLanguageUncertain","speechModelUnavailable","speechLanguageUnavailable") && onSettings!=null)
                            TextButton(onClick=onSettings) { Text(t("speechSetup")) }
                        TextButton(onClick = ::openEditor, modifier = Modifier.testTag("typed-fallback")) { Text(t("write")) }
                    }
                    if (isEmergency(s) || s.evaluation?.result?.urgency in listOf(Urgency.HIGH, Urgency.CRITICAL)) {
                        Text(Words.emergency(s.language.tag), Modifier.padding(16.dp).testTag("emergency"),
                            color = Color(0xFF812332), fontSize = 14.sp)
                    }
                    s.error?.let { Text(t(it), Modifier.padding(16.dp), color = Color(0xFF812332)) }
                    if(s.evaluation?.failed==true) s.backendFailure?.let {
                        Text(UiCopy.backendError(it,s.language),Modifier.padding(16.dp),color=Color(0xFF812332))
                    }
                    if (s.symptoms.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = ::openEditor, enabled = !busy && !s.listening) { Text(t("edit"), fontSize = 12.sp) }
                            TextButton(onClick = { go(Screen.INPUT); requestReview() }, enabled = !busy && !s.listening,
                                modifier = Modifier.testTag("review")) { Text(t("review"), fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Surface(shadowElevation = 0.dp, color = Color.White) {
                    Column(Modifier.fillMaxWidth()) {
                        val evaluation = s.evaluation
                        val stripColor = evaluation?.let { urgencyColor(it.result.urgency) } ?: Color(0xFFF1F5F3)
                        Column(
                            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
                                .background(stripColor)
                                .clickable(enabled = BuildConfig.LOCAL_DEVELOPMENT && !busy && evaluation != null, onClickLabel = t("details")) { details = true }
                                .padding(8.dp).testTag(evaluation?.let { "risk-" + it.result.urgency.name } ?: "result-strip")
                        ) {
                            val summary = when {
                                busy -> t("responseWaiting")
                                evaluation != null -> (if (evaluation.mode == "DEMO") "DEMO · " else "") +
                                    t("summaryPossible") + ": " + evaluation.result.assessment
                                else -> t("resultPlaceholder")
                            }
                            Text(summary, color = evaluation?.let { urgencyTextColor(it.result.urgency) } ?: VoiceDark,
                                modifier = Modifier.heightIn(max = 56.dp).verticalScroll(rememberScrollState())
                                    .clickable(enabled = BuildConfig.LOCAL_DEVELOPMENT && evaluation != null && !busy, onClickLabel = t("details")) { details = true }
                                    .testTag("result-summary"),
                                fontSize = 16.sp, lineHeight = 22.sp)
                            // Keep urgency visible even when a long assessment or large font needs scrolling.
                            if (evaluation != null && !busy) Text(
                                t("summaryUrgency") + ": " + t("summary" + evaluation.result.urgency.name),
                                color = urgencyTextColor(evaluation.result.urgency),
                                modifier = Modifier.testTag("result-urgency"), fontSize = 16.sp, lineHeight = 22.sp)
                        }
                        if (busy) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = VoiceDark)
                                Text(t("waiting"), Modifier.weight(1f).padding(8.dp), fontSize = 11.sp)
                                TextButton(onClick = { go(Screen.REVIEW) }, modifier = Modifier.testTag("cancel-analysis")) { Text(t("cancel")) }
                            }
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = dimensionResource(R.dimen.voice_mic_edge_margin))
                                .padding(top = 4.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom
                        ) {
                            VoiceMicrophone(
                                label = s.activeSpeech.label, automatic = false, muted = s.autoLanguage,
                                listening = s.listening && !s.autoLanguage, level = s.speechLevel, enabled = !busy,
                                actionLabel = t(if (s.listening && !s.autoLanguage) "stop" else "record"),
                                onToggle = { requestVoice(false) },
                                onReleaseLongPress = { if (vm.state.value.listening && !vm.state.value.autoLanguage) onRecord() }
                            )
                            VoiceMicrophone(
                                label = t("automatic"), automatic = true, muted = !s.autoLanguage,
                                listening = s.listening && s.autoLanguage, level = s.speechLevel, enabled = !busy,
                                actionLabel = t(if ((s.listening || s.automaticSession) && s.autoLanguage) "stop" else "record"),
                                onToggle = { requestVoice(true) }, onReleaseLongPress = {}
                            )
                        }
                        }
                    }
                }
            }
        }

        if (s.screen == Screen.LANGUAGE) {
            VoiceDialog(t("languages"), { go(Screen.INPUT) }) {
                Text(t("appLanguage"), Modifier.padding(horizontal = 24.dp), fontFamily = NunitoSemibold, color = VoiceDark, fontSize = 14.sp)
                Language.uiLanguages.sortedBy { it.label }.forEach { language ->
                    LanguageRow(language.label, s.language == language, "app-" + language.tag) { vm.language(language) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(t("resultLanguage"), Modifier.padding(horizontal=24.dp),fontFamily=NunitoSemibold,color=VoiceDark,fontSize=14.sp)
                Language.entries.sortedBy { it.label }.forEach { language ->
                    LanguageRow(language.label,s.resultLanguage==language,"result-"+language.tag) { vm.resultLanguage(language) }
                }
                HorizontalDivider(Modifier.padding(vertical=8.dp))
                Text(t("patientLanguages"), Modifier.padding(horizontal = 24.dp), fontFamily = NunitoSemibold, color = VoiceDark, fontSize = 14.sp)
                Language.entries.sortedBy { it.label }.forEach { language ->
                    LanguageRow(language.label, language in s.patientLanguages, "patient-" + language.tag) {
                        vm.toggleLanguage(language)
                        if (language in vm.state.value.patientLanguages) vm.activeLanguage(language)
                    }
                }
                Text(t("speechLanguage"), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 12.sp)
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.patientLanguages.forEach { language ->
                        TextButton(onClick = { vm.activeLanguage(language) }) {
                            Text(language.label, fontWeight = if (s.activeSpeech == language) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                        }
                    }
                }
                DialogAction(t("continue"), "continue") { go(Screen.INPUT) }
            }
        }
        if (editor) {
            VoiceDialog(t("input"), { editor = false }) {
                val editorFocus = androidx.compose.ui.platform.LocalFocusManager.current
                val editorKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t("inputHint"), fontSize = 13.sp, color = Color.DarkGray)
                    BasicTextField(
                        value = s.symptoms, onValueChange = vm::text, textStyle = TextStyle(color = VoiceInk, fontSize = 18.sp, textDirection=TextDirection.ContentOrLtr),
                        // The device keyboard language may differ from the selected source language.
                        // Never request automatic substitutions in the reviewed symptom text.
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("symptoms"),
                        decorationBox = { inner -> Box { if (s.symptoms.isEmpty()) Text(t("symptoms"), color = Color.Gray, fontSize = 18.sp); inner() } }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(s.immediateDanger, vm::danger, Modifier.testTag("danger"))
                        Text(t("danger"), Modifier.weight(1f), fontSize = 13.sp)
                    }
                    if (isEmergency(s)) Text(Words.emergency(s.language.tag), color = Color(0xFF812332), fontSize = 13.sp)
                    s.error?.let { Text(t(it), color = Color(0xFF812332)) }
                }
                DialogAction(t("review"), "editor-review") {
                    editorFocus.clearFocus()
                    editorKeyboard?.hide()
                    requestReview()
                    if (vm.state.value.screen == Screen.REVIEW) editor = false
                }
            }
        }
        if (s.screen == Screen.REVIEW) {
            VoiceDialog(t("reviewTitle"), { vm.confirm(false); go(Screen.INPUT) }) {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = VoiceBubble, shape = RoundedCornerShape(18.dp)) {
                        Text(s.symptoms, Modifier.padding(16.dp), fontSize = 16.sp, style=TextStyle(textDirection=TextDirection.ContentOrLtr))
                    }
                    TranslationText(s)
                    if(s.translationBusy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(t("translationProcessing"),fontSize=12.sp)
                        TextButton(onClick=onStop,modifier=Modifier.testTag("cancel-translation")) { Text(t("cancel")) }
                    }
                    Text(s.patientLanguages.joinToString(" · ") { it.label }, fontFamily = NunitoLight, fontSize = 12.sp)
                    Text(t("resultLanguage")+": "+s.resultLanguage.label,fontSize=12.sp)
                    if(s.translationFailed) TextButton(onClick={go(Screen.INPUT);if(onPrepareTranslation!=null)onPrepareTranslation() else onSettings?.invoke()}) { Text(t("prepareTranslation")) }
                    Text(t(if (s.demo) "demo" else "backend"), fontSize = 12.sp, color = VoiceDark)
                    if (isEmergency(s)) Text(Words.emergency(s.language.tag), color = Color(0xFF812332))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(s.confirmed, vm::confirm, Modifier.testTag("confirm"),enabled=!s.translationBusy)
                        Text(t("confirm"), Modifier.weight(1f), fontSize = 14.sp)
                    }
                    s.error?.let { Text(t(it), color = Color(0xFF812332)) }
                }
                DialogAction(t("analyze"), "analyze", s.confirmed && !s.translationBusy) { vm.analyze() }
                TextButton(onClick = { vm.confirm(false); openEditor() }, modifier = Modifier.align(Alignment.End).padding(end = 16.dp)) {
                    Text(t("edit"))
                }
            }
        }
        if (BuildConfig.LOCAL_DEVELOPMENT && details && s.evaluation != null) {
            val e = s.evaluation
            VoiceDialog(t("result"), { details = false }) {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (BuildConfig.LOCAL_DEVELOPMENT) Text(t(provenance(e)), fontSize = 12.sp, color = VoiceDark)
                    if (e.failed) {
                        Text(Words.failure(s.language.tag), color = Color(0xFF812332))
                        s.backendFailure?.let { Text(UiCopy.backendError(it, s.language), fontSize = 14.sp, color = Color(0xFF812332)); Text((if(BuildConfig.DIRECT_OPENAI) "OpenAI: " else "Backend: ") + it, fontSize = 12.sp) }
                    }
                    ResultCard(e.result, s.language)
                    Detail(t("rationale"), listOf(e.result.rationale))
                    Detail(t("redFlags"), e.result.redFlags)
                    Detail(t("missing"), e.result.missingInformation)
                    Detail(t("questions"), e.result.followUpQuestions)
                }
                DialogAction(t("new"), "new") { details = false; vm.reset() }
                TextButton(onClick = { details = false; openEditor() }, modifier = Modifier.align(Alignment.End).padding(end = 16.dp)) { Text(t("edit")) }
            }
        }
        if (s.screen == Screen.SETTINGS) {
            VoiceDialog(if(BuildConfig.LOCAL_DEVELOPMENT)"Developer / Test settings" else t("server"), { go(Screen.INPUT) }) {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if(BuildConfig.LOCAL_DEVELOPMENT) Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(s.demo, vm::demo, modifier = Modifier.testTag("demoSwitch"))
                        Text(t("localDemo"), Modifier.padding(start = 8.dp), fontSize = 14.sp)
                    }
                    if (BuildConfig.LOCAL_DEVELOPMENT && s.demo) {
                    Text(t("fixture"), fontSize = 13.sp)
                    Scenario.entries.forEach { scenario ->
                        Row(Modifier.fillMaxWidth().selectable(s.scenario == scenario, onClick = { vm.scenario(scenario) })
                            .testTag("scenario-" + scenario.name).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(s.scenario == scenario, null)
                            Text(t(scenario.name), Modifier.padding(start = 8.dp), fontSize = 14.sp)
                        }
                    }
                    }
                    if (BuildConfig.DIRECT_OPENAI) Text(t("directConfiguration"),fontSize=14.sp)
                    if (!s.demo && !BuildConfig.DIRECT_OPENAI) {
                        OutlinedTextField(s.endpoint, vm::endpoint, label = { Text(t("server")) }, singleLine = true,
                            enabled=!s.accessBusy, modifier = Modifier.fillMaxWidth().testTag("endpoint"))
                        OutlinedTextField(s.accessToken, vm::token, label = { Text(t("token")) }, singleLine = true,
                            enabled=!s.accessBusy, visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("backend-token"))
                        Text(stringResource(R.string.vv_access_note),fontSize=12.sp)
                        TextButton(onClick=vm::saveAccess,enabled=!s.accessBusy && s.accessToken.isNotEmpty(),
                            modifier=Modifier.testTag("save-backend-access")) { Text(stringResource(R.string.vv_access_save)) }
                        TextButton(onClick=vm::forgetAccess,enabled=!s.accessBusy,
                            modifier=Modifier.testTag("forget-backend-access")) { Text(stringResource(R.string.vv_access_forget)) }
                        if(s.accessBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        s.accessStorage?.let { status -> Text(stringResource(when(status) {
                            "saved" -> R.string.vv_access_saved
                            "forgotten" -> R.string.vv_access_forgotten
                            else -> R.string.vv_access_error
                        }),fontSize=12.sp,modifier=Modifier.testTag("backend-access-status")) }
                    }
                    Text(t("privacy"), fontSize = 13.sp)
                    s.evaluation?.let { Text(t(provenance(it)), fontSize = 12.sp) }
                    TextButton(onClick = { go(Screen.INPUT); information = true }) { Text(t("about")) }
                    if(onImportAudio!=null) {
                        Text(t("importAudioNote"),fontSize=12.sp)
                        TextButton(onClick={go(Screen.INPUT);onImportAudio()},modifier=Modifier.testTag("import-audio")) { Text(t("importAudio")) }
                    }
                }
                DialogAction(t("back"), "settingsBack") { go(Screen.INPUT) }
            }
        }
        if (information) {
            VoiceDialog(t("about"), { information = false }) {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("One Triage", fontFamily = NunitoSemibold, color = VoiceDark)
                    Text(t("disclaimer"))
                    Text(t("speechPrivacy"), fontSize = 14.sp)
                    Text(t("privacy"), fontSize = 14.sp)
                    Text(t("scoreNote"), fontSize = 14.sp)
                    Text(t("translationPrivacy"), fontSize=12.sp)
                    Text(TranslationNotices.DISCLAIMER,fontSize=11.sp)
                    Text(androidx.compose.ui.res.stringResource(R.string.vv_nllb_notice),fontSize=12.sp)
                    val links=LocalUriHandler.current
                    TextButton(onClick={runCatching { links.openUri("https://developers.google.com/ml-kit/terms") }}) { Text("Google Translate / ML Kit privacy") }
                    TextButton(onClick={runCatching { links.openUri("https://cloud.google.com/translate") }}) { Text("Google Translate information") }
                }
                DialogAction(t("back"), "info-back") { information = false }
            }
        }
        speechDisclosure?.let { automatic ->
            VoiceDialog(t("record"), { speechDisclosure = null }) {
                Text(t("speechPrivacy"), Modifier.padding(horizontal = 24.dp), fontSize = 14.sp)
                Text(if (automatic) t("auto") else s.activeSpeech.label, Modifier.padding(24.dp), fontSize = 13.sp)
                DialogAction(t("record"), "speech-start") {
                    speechDisclosed = true
                    speechDisclosure = null
                    startVoice(automatic)
                }
            }
        }
    }
}

@Composable
private fun TranslationText(s:UiState,tag:String="speech-translation") {
    s.speechTranslation?.let { translated ->
        Text(UiCopy.text("speechTranslation",s.language)+" · "+(Language.entries.firstOrNull { it.tag==s.translationTarget }?.label ?: s.translationTarget),Modifier.padding(top=8.dp),fontSize=11.sp,color=VoiceDark)
        Text(translated,Modifier.testTag(tag),fontSize=14.sp,color=VoiceInk,
            style=TextStyle(textDirection=TextDirection.ContentOrLtr))
        if(s.translationEngine=="nllb") Text("NLLB · CC-BY-NC-4.0",fontSize=11.sp,color=VoiceDark)
        else GoogleTranslationBadge()
    }
    if(s.translationFailed) Text(UiCopy.text("translationFailed",s.language),fontSize=12.sp,color=Color(0xFF812332))
}

@Composable
private fun GoogleTranslationBadge() {
    // Official unmodified attribution sheet. Display only its complete full-color first badge.
    val image=ImageBitmap.imageResource(R.drawable.google_translation_attribution)
    Canvas(Modifier.size(194.dp,30.dp).semantics { contentDescription="Powered by Google Translate" }) {
        drawImage(image,srcOffset=IntOffset(12,14),srcSize=IntSize(194,30),
            dstSize=IntSize(size.width.toInt(),size.height.toInt()))
    }
}

@Composable
private fun VoiceIcon(icon: Int, label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag)) {
        Icon(painterResource(icon), label, tint = if (enabled) VoiceDark else Color.Gray, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick).testTag(tag)
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), color = VoiceInk, fontSize = 17.sp)
        if (selected) Icon(painterResource(R.drawable.check_icon), null, tint = Color(0xFF757575), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun VoiceDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).widthIn(max = 560.dp)
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp),
            shape = RoundedCornerShape(4.dp), color = Color.White, tonalElevation = 0.dp
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), fontFamily = NunitoSemibold, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.cancel_icon), UiCopy.text("back", Language.EN), tint = VoiceDark) }
                }
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
private fun ColumnScope.DialogAction(label: String, tag: String, enabled: Boolean = true, action: () -> Unit) {
    TextButton(
        onClick = action, enabled = enabled,
        modifier = Modifier.align(Alignment.End).padding(start = 16.dp, end = 16.dp, top = 8.dp).testTag(tag)
    ) { Text(label, fontFamily = NunitoSemibold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

@Composable
internal fun VoiceMicrophone(
    label: String, automatic: Boolean, muted: Boolean, listening: Boolean, level: Float, enabled: Boolean,
    actionLabel: String, onToggle: () -> Unit, onReleaseLongPress: () -> Unit
) {
    val diameter by animateDpAsState(
        dimensionResource(if (listening) R.dimen.voice_mic_listening else if (muted) R.dimen.voice_mic_muted else R.dimen.voice_mic_normal),
        tween(300), label = "microphone-size"
    )
    val currentToggle by rememberUpdatedState(onToggle)
    val currentRelease by rememberUpdatedState(onReleaseLongPress)
    val currentListening by rememberUpdatedState(listening)
    val background = if (!enabled) Color(0xFF9DA39F) else if (automatic || listening) VoiceDark else VoiceBlue
    val foreground = if (!enabled || automatic || listening) Color.White else VoiceDark
    Column(Modifier.width(90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(76.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(diameter).shadow(2.dp,CircleShape).clip(CircleShape).background(background)
                    .semantics {
                        role = Role.Button
                        contentDescription = "$label · $actionLabel"
                        stateDescription = if (listening) "listening" else if (muted) "muted" else "ready"
                        if (!enabled) disabled()
                        onClick { if (enabled) { currentToggle(); true } else false }
                    }
                    .testTag(if (automatic) "microphone-auto" else "microphone")
                    .pointerInput(enabled, automatic) {
                        if (enabled) detectTapGestures(onPress = {
                            val started = SystemClock.uptimeMillis()
                            val wasListening = currentListening
                            if (!automatic) currentToggle()
                            val released = tryAwaitRelease()
                            if (automatic) {
                                if (released) currentToggle()
                            } else if (!wasListening && (!released || SystemClock.uptimeMillis() - started > 700)) {
                                currentRelease()
                            }
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                if (listening) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(21f, 26f, 15f).forEach { max ->
                            val height by animateDpAsState((5f + level.coerceIn(0f, 1f) * (max - 5f)).dp, tween(70), label = "voice-level")
                            Box(Modifier.width(5.dp).height(height).clip(RoundedCornerShape(5.dp)).background(foreground))
                        }
                    }
                } else Icon(painterResource(R.drawable.mic_icon), null, tint = foreground, modifier = Modifier.size(28.dp))
            }
        }
        Text(label, Modifier.fillMaxWidth().padding(top = 4.dp), fontFamily = NunitoLight,
            color = if(enabled) VoiceDark else Color.Gray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private fun isEmergency(s: UiState) = Safety.emergency(
    TriageRequest(s.symptoms, s.language.tag, s.patientLanguages.map { it.tag }, true, s.immediateDanger)
)

private fun provenance(e: Evaluation) = when (e.mode) { "DEMO" -> "demo"; "LIVE" -> "live"; "DIRECT_OPENAI" -> "directLive"; else -> "unavailable" }

@Composable
internal fun urgencyColor(urgency: Urgency): Color = when (urgency) {
    Urgency.LOW -> colorResource(R.color.urgency_low)
    Urgency.MEDIUM -> colorResource(R.color.urgency_medium)
    Urgency.HIGH -> colorResource(R.color.urgency_high)
    Urgency.CRITICAL -> colorResource(R.color.urgency_critical)
    Urgency.UNDETERMINED -> Color(0xFF526277)
}

private fun urgencyTextColor(urgency: Urgency): Color = if(urgency == Urgency.MEDIUM) Color(0xFF332600) else Color.White

@Composable
fun ResultCard(a: Assessment, language: Language) {
    fun t(key: String) = UiCopy.text(key, language)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t(a.urgency.name), Modifier.fillMaxWidth().background(urgencyColor(a.urgency)).padding(8.dp).testTag("risk-" + a.urgency.name),
            color = urgencyTextColor(a.urgency), fontSize = 14.sp)
        Text(t("possible"), fontSize = 12.sp)
        Text(a.assessment, fontSize = 16.sp)
        Text(t("score"), fontFamily = NunitoSemibold, fontSize = 14.sp)
        Text(a.criticality?.let { "$it / 100" } ?: t("unknown"), fontFamily = NunitoSemibold, fontSize = 24.sp)
        Text(t("scoreNote"), fontSize = 12.sp)
    }
}

@Composable
private fun Detail(title: String, items: List<String>) {
    if (items.isNotEmpty()) {
        Text(title, fontFamily = NunitoSemibold, fontSize = 16.sp)
        items.forEach { Text("• $it", fontSize = 14.sp) }
    }
}
