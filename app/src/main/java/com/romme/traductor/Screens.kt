package com.romme.traductor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File

// ═════════════════════════════════════════════════════════════
// RAÍZ: elige la pantalla y muestra los avisos
// ═════════════════════════════════════════════════════════════
@Composable
fun AppRoot(vm: AppViewModel) {
    BackHandler(enabled = vm.screen != Screen.HOME && vm.screen != Screen.SETUP) { vm.go(Screen.HOME) }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBrush)
    ) {
        when (vm.screen) {
            Screen.SETUP -> SetupScreen(vm)
            Screen.HOME -> HomeScreen(vm)
            Screen.INDIVIDUAL -> IndividualScreen(vm)
            Screen.SHARED -> SharedScreen(vm)
            Screen.READ -> ReadScreen(vm)
            Screen.SETTINGS -> SettingsScreen(vm)
        }

        // Aviso flotante
        val notice = vm.notice
        LaunchedEffect(notice) {
            if (notice != null) {
                delay(4200)
                vm.notice = null
            }
        }
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Pal.Card2)
                    .border(1.dp, Pal.Warn.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable { vm.notice = null }
                    .padding(16.dp)
            ) {
                Text(notice ?: "", color = Pal.Text, fontSize = 15.sp)
            }
        }
    }
}

/** Pide el permiso del micrófono la primera vez y después escucha. */
@Composable
fun rememberMic(vm: AppViewModel): (Lang) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Lang?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val l = pending
        pending = null
        if (granted && l != null) vm.toggleListening(l)
        else if (!granted) vm.notice = "Sin permiso de micrófono no puedo escuchar. Activalo en Ajustes del celular."
    }
    return { lang ->
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.toggleListening(lang)
        } else {
            pending = lang
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

private fun openSafe(ctx: Context, vararg intents: Intent) {
    for (i in intents) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}

fun openVoiceSettings(ctx: Context) = openSafe(
    ctx,
    Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
    Intent(Settings.ACTION_LOCALE_SETTINGS),
    Intent(Settings.ACTION_SETTINGS),
)

fun openTtsSettings(ctx: Context) = openSafe(
    ctx,
    Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
    Intent("com.android.settings.TTS_SETTINGS"),
    Intent(Settings.ACTION_SETTINGS),
)

// ═════════════════════════════════════════════════════════════
// BIENVENIDA (solo la primera vez)
// ═════════════════════════════════════════════════════════════
@Composable
fun SetupScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var micOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micOk = it }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        AppLogo()
        Spacer(Modifier.height(18.dp))
        Text("¡Bienvenido!", color = Pal.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Welcome!", color = Pal.Sub, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Preparemos todo para que funcione también sin internet. Son 3 pasos y se hacen una sola vez, con wifi.",
            color = Pal.Sub, fontSize = 15.sp, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(22.dp))

        // Idioma de quien usa este celular
        CardBox(Modifier.fillMaxWidth()) {
            Column {
                SmallLabel("¿Quién usa este celular? · Who uses this phone?")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Lang.entries.forEach { l ->
                        val sel = vm.myLang == l
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sel) l.color.copy(alpha = 0.18f) else Pal.Card2)
                                .border(if (sel) 2.dp else 1.dp, if (sel) l.color else Pal.Line, RoundedCornerShape(16.dp))
                                .clickable { vm.updateMyLang(l) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(l.flag, fontSize = 26.sp)
                                Text(l.t("Hablo español", "I speak English"), color = if (sel) l.color else Pal.Text, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SetupStep(
            n = 1, title = "Permiso de micrófono",
            text = "Para escuchar lo que dicen.",
            done = micOk, button = "Permitir",
        ) { askMic.launch(Manifest.permission.RECORD_AUDIO) }

        SetupStep(
            n = 2, title = "Traducción sin internet",
            text = vm.downloadMsg ?: "Descarga español e inglés (unos 60 MB).",
            done = vm.offlineReady, busy = vm.downloading, button = "Descargar",
        ) { vm.downloadOffline() }

        SetupStep(
            n = 3, title = "Voz sin internet",
            text = "En la pantalla que se abre, buscá \"Reconocimiento sin conexión\" o \"Idiomas sin conexión\" y descargá Español e English. Si tu celular no la muestra, no pasa nada: con internet funciona igual.",
            done = false, button = "Abrir",
        ) { openVoiceSettings(ctx) }

        Spacer(Modifier.height(20.dp))
        PrimaryButton("Empezar · Start", onClick = { vm.finishSetup() })
        Spacer(Modifier.height(8.dp))
        Text(
            "Podés volver a esto cuando quieras desde Ajustes.",
            color = Pal.Sub, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SetupStep(
    n: Int, title: String, text: String, done: Boolean, button: String,
    busy: Boolean = false, onClick: () -> Unit,
) {
    CardBox(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        accent = if (done) Pal.Ok else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (done) Pal.Ok.copy(alpha = 0.2f) else Pal.Card2),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.CheckCircle, null, tint = Pal.Ok, modifier = Modifier.size(22.dp))
                else Text("$n", color = Pal.Text, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(2.dp))
                Text(text, color = Pal.Sub, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            when {
                busy -> CircularProgressIndicator(color = Pal.Es, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                !done -> Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Pal.Es.copy(alpha = 0.16f))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) { Text(button, color = Pal.Es, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
fun AppLogo(size: Int = 64) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3).dp))
            .background(Brush.linearGradient(listOf(Pal.Es, Pal.En))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Translate, null, tint = Color(0xFF07121E), modifier = Modifier.size((size * 0.52).dp))
    }
}

// ═════════════════════════════════════════════════════════════
// INICIO
// ═════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(vm: AppViewModel) {
    val l = vm.myLang
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo(52)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Traductor", color = Pal.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Español", color = Pal.Es, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("  ⇄  ", color = Pal.Sub, fontSize = 14.sp)
                    Text("English", color = Pal.En, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            RoundIconButton(Icons.Rounded.Settings, "Ajustes", { vm.go(Screen.SETTINGS) })
        }

        Spacer(Modifier.height(22.dp))

        // Selector de conexión
        CardBox(Modifier.fillMaxWidth()) {
            Column {
                SmallLabel(l.t("Modo de traducción", "Translation mode"))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeToggle(
                        Modifier.weight(1f), selected = !vm.online,
                        title = l.t("Sin conexión", "Offline"),
                        sub = l.t("Funciona en cualquier lado", "Works anywhere"),
                    ) { vm.updateOnline(false) }
                    ModeToggle(
                        Modifier.weight(1f), selected = vm.online,
                        title = l.t("Con internet", "Online"),
                        sub = l.t("IA: entiende el lunfardo", "AI: natural & slang"),
                    ) { vm.updateOnline(true) }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SmallLabel(l.t("Elegí cómo conversar", "Choose how to talk"))
        Spacer(Modifier.height(10.dp))

        ModeCard(
            icon = Icons.Rounded.PhoneAndroid,
            title = l.t("Modo individual", "Solo mode"),
            text = l.t("Cada uno con su celular. Hablás y la voz lo dice en el otro idioma.", "Each person uses their own phone. You speak, it says it in the other language."),
            accent = Pal.Es,
        ) { vm.go(Screen.INDIVIDUAL) }

        ModeCard(
            icon = Icons.Rounded.Groups,
            title = l.t("Modo compartido", "Shared mode"),
            text = l.t("Un solo celular en la mesa, pantalla dividida para los dos.", "One phone on the table, split screen for both people."),
            accent = Pal.En,
        ) { vm.go(Screen.SHARED) }

        ModeCard(
            icon = Icons.Rounded.CameraAlt,
            title = l.t("Foto y texto", "Photo & text"),
            text = l.t("Traducí carteles, menús o mensajes escritos.", "Translate signs, menus or typed messages."),
            accent = Color(0xFFB794F6),
        ) { vm.go(Screen.READ) }

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { if (!vm.offlineReady) vm.autoPrepare() }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ok = vm.offlineReady
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (ok) Pal.Ok else Pal.Warn)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    ok -> l.t("Listo para usar sin internet", "Ready to use offline")
                    vm.downloading -> l.t("Preparando el modo sin internet…", "Getting offline mode ready…")
                    else -> l.t("El modo sin internet se prepara solo cuando haya wifi o datos", "Offline mode sets itself up when there's wifi or data")
                },
                color = Pal.Sub, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ModeToggle(modifier: Modifier, selected: Boolean, title: String, sub: String, onClick: () -> Unit) {
    val c = if (selected) Pal.Ok else Pal.Line
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Pal.Ok.copy(alpha = 0.10f) else Pal.Card2)
            .border(if (selected) 2.dp else 1.dp, c, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, color = if (selected) Pal.Ok else Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(2.dp))
        Text(sub, color = Pal.Sub, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, text: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.16f), Pal.Card)))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(text, color = Pal.Sub, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

// ═════════════════════════════════════════════════════════════
// MODO INDIVIDUAL: cada uno con su celular
// ═════════════════════════════════════════════════════════════
@Composable
fun IndividualScreen(vm: AppViewModel) {
    val me = vm.myLang
    val mic = rememberMic(vm)
    val listening = vm.listeningLang == me
    val listState = rememberLazyListState()

    LaunchedEffect(vm.turns.size) {
        if (vm.turns.isNotEmpty()) listState.animateScrollToItem(vm.turns.size - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar(me.t("Modo individual", "Solo mode"), onBack = { vm.go(Screen.HOME) }) {
            OnlineChip(vm.online, me) { vm.updateOnline(!vm.online) }
            Spacer(Modifier.width(8.dp))
        }

        // Dirección de la traducción
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            LangBadge(me, big = true)
            Spacer(Modifier.width(10.dp))
            RoundIconButton(Icons.Rounded.SwapHoriz, "Cambiar idioma", { vm.updateMyLang(me.other) }, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            LangBadge(me.other, big = true)
        }

        // Conversación
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (vm.turns.isEmpty() && !listening) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.RecordVoiceOver, null, tint = Pal.Sub.copy(alpha = 0.6f), modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        me.t("Tocá el micrófono y hablá en español.\nLa voz lo va a decir en inglés.", "Tap the mic and speak English.\nThe voice will say it in Spanish."),
                        color = Pal.Sub, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.turns, key = { it.id }) { turn ->
                    TurnCard(turn, highlight = turn == vm.turns.lastOrNull(), onReplay = { vm.replay(turn) })
                }
                if (listening && vm.partial.isNotBlank()) {
                    item {
                        CardBox(Modifier.fillMaxWidth(), accent = me.color) {
                            Text(vm.partial, color = Pal.Text.copy(alpha = 0.8f), fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        // Controles
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RoundIconButton(Icons.Rounded.DeleteSweep, "Borrar", { vm.clearTurns() }, tint = Pal.Sub, size = 52.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MicButton(
                    lang = me,
                    listening = listening,
                    working = vm.workingLang == me,
                    level = vm.micLevel,
                    onClick = { mic(me) },
                )
                Text(
                    when {
                        listening -> me.t("Escuchando… tocá para terminar", "Listening… tap to finish")
                        vm.workingLang == me -> me.t("Traduciendo…", "Translating…")
                        vm.speaker.speaking -> me.t("Hablando…", "Speaking…")
                        else -> me.t("Tocá y hablá", "Tap and speak")
                    },
                    color = Pal.Sub, fontSize = 13.sp,
                )
            }
            RoundIconButton(Icons.Rounded.Keyboard, "Foto y texto", { vm.go(Screen.READ) }, tint = Pal.Sub, size = 52.dp)
        }
    }
}

@Composable
private fun TurnCard(turn: Turn, highlight: Boolean, onReplay: () -> Unit) {
    val target = turn.from.other
    CardBox(Modifier.fillMaxWidth(), accent = if (highlight) target.color else null) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(turn.from.flag, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(turn.original, color = Pal.Sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    turn.translated,
                    style = if (highlight) BigText.copy(color = target.color) else BigText.copy(fontSize = 19.sp, lineHeight = 25.sp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.AutoMirrored.Rounded.VolumeUp, "Repetir", onReplay, tint = target.color, size = 40.dp, bg = Pal.Card2)
            }
            if (turn.online) {
                Spacer(Modifier.height(6.dp))
                Text("IA", color = Pal.Ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
// MODO COMPARTIDO: un celular, pantalla dividida
// ═════════════════════════════════════════════════════════════
@Composable
fun SharedScreen(vm: AppViewModel) {
    val mic = rememberMic(vm)
    val top = vm.sharedTop
    val bottom = top.other

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Mitad de arriba: girada para la persona de enfrente
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .rotate(180f)
        ) {
            SharedHalf(vm, top) { mic(top) }
        }

        // Barra del medio
        Row(
            Modifier
                .fillMaxWidth()
                .background(Pal.Bg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Icons.Rounded.Home, "Inicio", { vm.go(Screen.HOME) }, size = 40.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                OnlineChip(vm.online, bottom) { vm.updateOnline(!vm.online) }
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Rounded.DeleteSweep, "Borrar", { vm.clearTurns() }, tint = Pal.Sub, size = 40.dp)
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Rounded.SwapVert, "Invertir", { vm.swapShared() }, size = 40.dp)
        }

        // Mitad de abajo
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            SharedHalf(vm, bottom) { mic(bottom) }
        }
    }
}

@Composable
private fun SharedHalf(vm: AppViewModel, lang: Lang, onMic: () -> Unit) {
    val listening = vm.listeningLang == lang
    val working = vm.workingLang == lang
    val last = vm.turns.lastOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(lang.color.copy(alpha = 0.10f), Color.Transparent)))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LangBadge(lang)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                listening -> Text(
                    vm.partial.ifBlank { lang.t("Te escucho…", "Listening…") },
                    style = BigText.copy(color = Pal.Text.copy(alpha = 0.85f)), textAlign = TextAlign.Center,
                )
                last == null -> Text(
                    lang.t("Tocá el micrófono y hablá en español", "Tap the mic and speak English"),
                    color = Pal.Sub, fontSize = 17.sp, textAlign = TextAlign.Center,
                )
                // Lo que dijo la otra persona, traducido para mí
                last.from == lang.other -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SmallLabel(lang.t("Te dijo", "They said"), color = lang.color)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        last.translated, style = BigText.copy(fontSize = 28.sp, lineHeight = 34.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable { vm.replay(last) },
                    )
                }
                // Lo que dije yo
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SmallLabel(lang.t("Dijiste", "You said"))
                    Spacer(Modifier.height(6.dp))
                    Text(last.original, color = Pal.Text, fontSize = 20.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("→ " + last.translated, color = Pal.Sub, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        }
        MicButton(lang, listening, working, vm.micLevel, onMic, size = 92.dp)
    }
}

// ═════════════════════════════════════════════════════════════
// FOTO Y TEXTO
// ═════════════════════════════════════════════════════════════
@Composable
fun ReadScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val me = vm.myLang
    val clipboard = LocalClipboardManager.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var typed by rememberSaveable { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = photoUri
        if (ok && u != null) vm.translatePhoto(u)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.translatePhoto(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(me.t("Foto y texto", "Photo & text"), onBack = { vm.go(Screen.HOME) }) {
            OnlineChip(vm.online, me) { vm.updateOnline(!vm.online) }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            // Pestañas
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Pal.Card)
                    .padding(4.dp)
            ) {
                listOf(me.t("Foto", "Photo") to Icons.Rounded.CameraAlt, me.t("Escribir", "Type") to Icons.Rounded.Keyboard)
                    .forEachIndexed { i, (label, icon) ->
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (tab == i) Pal.Card2 else Color.Transparent)
                                .clickable { tab = i }
                                .padding(vertical = 11.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, null, tint = if (tab == i) Pal.Text else Pal.Sub, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = if (tab == i) Pal.Text else Pal.Sub, fontWeight = FontWeight.SemiBold)
                        }
                    }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                LangBadge(vm.readFrom, big = true)
                Spacer(Modifier.width(10.dp))
                RoundIconButton(Icons.Rounded.SwapHoriz, "Cambiar", { vm.swapRead() }, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                LangBadge(vm.readFrom.other, big = true)
            }
            Spacer(Modifier.height(14.dp))

            if (tab == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigAction(Modifier.weight(1f), Icons.Rounded.CameraAlt, me.t("Sacar foto", "Take photo")) {
                        val dir = File(ctx.cacheDir, "fotos").apply { mkdirs() }
                        val f = File(dir, "foto_${System.currentTimeMillis()}.jpg")
                        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                        photoUri = u
                        try {
                            takePicture.launch(u)
                        } catch (_: Exception) {
                            vm.notice = "No se pudo abrir la cámara."
                        }
                    }
                    BigAction(Modifier.weight(1f), Icons.Rounded.PhotoLibrary, me.t("Galería", "Gallery")) {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            } else {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    placeholder = { Text(vm.readFrom.t("Escribí acá…", "Type here…"), color = Pal.Sub) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = Pal.Text),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = vm.readFrom.color,
                        unfocusedBorderColor = Pal.Line,
                        cursorColor = vm.readFrom.color,
                        focusedContainerColor = Pal.Card,
                        unfocusedContainerColor = Pal.Card,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    me.t("Traducir", "Translate"), onClick = { vm.translateTyped(typed) },
                    color = vm.readFrom.other.color, enabled = typed.isNotBlank() && !vm.readBusy,
                    icon = Icons.Rounded.Translate,
                )
            }

            Spacer(Modifier.height(16.dp))
            if (vm.readBusy) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = vm.readFrom.other.color) }
            }
            if (vm.readOriginal.isNotBlank()) {
                CardBox(Modifier.fillMaxWidth()) {
                    Column {
                        SmallLabel(vm.readFrom.flag + "  " + vm.readFrom.label)
                        Spacer(Modifier.height(6.dp))
                        Text(vm.readOriginal, color = Pal.Sub, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (vm.readTranslated.isNotBlank()) {
                val to = vm.readFrom.other
                CardBox(Modifier.fillMaxWidth(), accent = to.color) {
                    Column {
                        SmallLabel(to.flag + "  " + to.label, color = to.color)
                        Spacer(Modifier.height(8.dp))
                        Text(vm.readTranslated, style = BigText.copy(fontSize = 21.sp, lineHeight = 28.sp))
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RoundIconButton(Icons.AutoMirrored.Rounded.VolumeUp, "Escuchar", { vm.speakText(vm.readTranslated, to) }, tint = to.color, bg = Pal.Card2)
                            RoundIconButton(Icons.Rounded.ContentCopy, "Copiar", {
                                clipboard.setText(AnnotatedString(vm.readTranslated))
                                vm.notice = me.t("Copiado", "Copied")
                            }, tint = Pal.Sub, bg = Pal.Card2)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BigAction(modifier: Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Pal.Card)
            .border(1.dp, Pal.Line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Pal.Text, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = Pal.Text, fontWeight = FontWeight.SemiBold)
    }
}

// ═════════════════════════════════════════════════════════════
// AJUSTES
// ═════════════════════════════════════════════════════════════
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val me = vm.myLang
    var keyText by remember { mutableStateOf(vm.apiKey) }
    var modelText by remember { mutableStateOf(vm.model) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(me.t("Ajustes", "Settings"), onBack = {
            vm.updateApiKey(keyText)
            vm.updateModel(modelText)
            vm.go(Screen.HOME)
        })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Idioma
            CardBox(Modifier.fillMaxWidth()) {
                Column {
                    SmallLabel(me.t("Idioma de este celular", "This phone's language"))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Lang.entries.forEach { l ->
                            val sel = vm.myLang == l
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(2.dp, if (sel) l.color else Color.Transparent, RoundedCornerShape(50))
                                    .clickable { vm.updateMyLang(l) }
                                    .padding(2.dp)
                            ) { LangBadge(l, big = true) }
                        }
                    }
                }
            }

            // Voz
            CardBox(Modifier.fillMaxWidth()) {
                Column {
                    SmallLabel(me.t("Voz", "Voice"))
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(me.t("Leer la traducción en voz alta", "Read translation aloud"), vm.autoSpeak) { vm.updateAutoSpeak(it) }
                    Spacer(Modifier.height(10.dp))
                    Text(me.t("Velocidad de la voz", "Voice speed") + "  ·  " + String.format("%.1fx", vm.speechRate), color = Pal.Text, fontSize = 15.sp)
                    Slider(
                        value = vm.speechRate,
                        onValueChange = { vm.updateSpeechRate((it * 10).toInt() / 10f) },
                        valueRange = 0.6f..1.5f,
                        colors = SliderDefaults.colors(thumbColor = Pal.Es, activeTrackColor = Pal.Es),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SmallButton(me.t("Probar voz ES", "Test ES voice")) { vm.speakText("Hola, ¿cómo andás? Bienvenidos a la Argentina.", Lang.ES) }
                        SmallButton(me.t("Probar voz EN", "Test EN voice")) { vm.speakText("Hi! Welcome to Argentina, it's great to see you.", Lang.EN) }
                    }
                    if (vm.speaker.missingVoices.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Falta la voz de: " + vm.speaker.missingVoices.joinToString { it.label } + ". Tocá \"Voces del celular\".",
                            color = Pal.Warn, fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SmallButton(me.t("Voces del celular", "Phone voices")) { openTtsSettings(ctx) }
                }
            }

            // Sin internet
            CardBox(Modifier.fillMaxWidth(), accent = if (vm.offlineReady) Pal.Ok else Pal.Warn) {
                Column {
                    SmallLabel(me.t("Uso sin internet", "Offline use"))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (vm.offlineReady) Icons.Rounded.CheckCircle else Icons.Rounded.Download, null,
                            tint = if (vm.offlineReady) Pal.Ok else Pal.Warn, modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (vm.offlineReady) me.t("Traducción sin internet lista", "Offline translation ready")
                            else me.t("Falta descargar los idiomas", "Languages not downloaded"),
                            color = Pal.Text, fontSize = 15.sp, modifier = Modifier.weight(1f),
                        )
                        if (vm.downloading) CircularProgressIndicator(color = Pal.Es, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                    }
                    vm.downloadMsg?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = Pal.Sub, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!vm.offlineReady) SmallButton(me.t("Descargar idiomas", "Download languages")) { vm.downloadOffline() }
                        SmallButton(me.t("Voz sin internet", "Offline speech")) { openVoiceSettings(ctx) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        me.t(
                            "\"Voz sin internet\": descargá Español e English en \"Reconocimiento sin conexión\".",
                            "\"Offline speech\": download English and Spanish under \"Offline speech recognition\".",
                        ),
                        color = Pal.Sub, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
            }

            // Con internet (IA)
            CardBox(Modifier.fillMaxWidth()) {
                Column {
                    SmallLabel(me.t("Modo con internet (IA)", "Online mode (AI)"))
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(me.t("Usar IA cuando hay internet", "Use AI when online"), vm.online) { vm.updateOnline(it) }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (vm.hasBuiltInKey) me.t("✓ La IA ya viene incluida. No hace falta hacer nada.", "✓ AI is built in. Nothing to set up.")
                        else me.t("Esta versión no trae la IA incluida.", "This version has no built-in AI."),
                        color = if (vm.hasBuiltInKey) Pal.Ok else Pal.Warn, fontSize = 14.sp,
                    )
                    var advanced by remember { mutableStateOf(false) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (advanced) me.t("Ocultar opciones avanzadas", "Hide advanced") else me.t("Opciones avanzadas", "Advanced options"),
                        color = Pal.Sub, fontSize = 13.sp,
                        modifier = Modifier.clickable { advanced = !advanced }.padding(vertical = 6.dp),
                    )
                    if (advanced) {
                        Spacer(Modifier.height(6.dp))
                        SettingsField(keyText, { keyText = it; vm.updateApiKey(it) }, me.t("Clave propia (opcional)", "Own key (optional)"), secret = true)
                        Spacer(Modifier.height(8.dp))
                        SettingsField(modelText, { modelText = it; vm.updateModel(it) }, me.t("Modelo", "Model"))
                    }
                }
            }

            Text(
                "Versión 0.2",
                color = Pal.Sub.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Pal.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Pal.Ok),
        )
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Pal.Card2)
            .border(1.dp, Pal.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { Text(text, color = Pal.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun SettingsField(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Pal.Es,
            unfocusedBorderColor = Pal.Line,
            focusedLabelColor = Pal.Es,
            unfocusedLabelColor = Pal.Sub,
            focusedTextColor = Pal.Text,
            unfocusedTextColor = Pal.Text,
            cursorColor = Pal.Es,
        ),
    )
}
