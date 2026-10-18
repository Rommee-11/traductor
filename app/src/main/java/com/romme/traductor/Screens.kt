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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File

// ═════════════════════════════════════════════════════════════
// RAÍZ: fondo con ondas, la pantalla elegida y los avisos
// ═════════════════════════════════════════════════════════════
@Composable
fun AppRoot(vm: AppViewModel) {
    BackHandler(enabled = vm.screen != Screen.HOME) { vm.go(Screen.HOME) }

    WaveBackground(Modifier.fillMaxSize()) {
        when (vm.screen) {
            Screen.SETUP, Screen.HOME -> HomeScreen(vm)
            Screen.INDIVIDUAL -> IndividualScreen(vm)
            Screen.SHARED -> SharedScreen(vm)
            Screen.READ -> ReadScreen(vm)
            Screen.SETTINGS -> SettingsScreen(vm)
        }

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
                    .border(1.dp, Pal.Warn.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            LogoCard(Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))

            // Con internet / sin conexión
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { vm.updateOnline(!vm.online) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        l.t("Con internet / Sin conexión", "Online / Offline"),
                        color = Pal.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp,
                    )
                    Text(
                        if (vm.online) l.t("Con internet: traducción con IA", "Online: AI translation")
                        else l.t("Sin conexión: funciona en cualquier lado", "Offline: works anywhere"),
                        color = Pal.Sub, fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = vm.online, onCheckedChange = { vm.updateOnline(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759),
                        uncheckedThumbColor = Color.White, uncheckedTrackColor = Pal.Card2,
                    ),
                )
            }
            Spacer(Modifier.height(18.dp))

            BigColorButton(l.t("Modo\nindividual", "Solo\nmode"), Icons.Rounded.Person, Pal.Teal, Pal.TealLight) { vm.go(Screen.INDIVIDUAL) }
            Spacer(Modifier.height(14.dp))
            BigColorButton(l.t("Modo\ncompartido", "Shared\nmode"), Icons.Rounded.Groups, Pal.Wine, Pal.WineLight) { vm.go(Screen.SHARED) }
            Spacer(Modifier.height(14.dp))
            BigColorButton(l.t("Foto y texto", "Photo & text"), Icons.Rounded.CameraAlt, Pal.Gold, Pal.GoldLight) { vm.go(Screen.READ) }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { if (!vm.offlineReady) vm.autoPrepare() }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
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
                        else -> l.t("El modo sin internet se prepara solo con wifi o datos", "Offline mode sets itself up with wifi or data")
                    },
                    color = Pal.Sub, fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Barra inferior
        Row(
            Modifier
                .fillMaxWidth()
                .background(Pal.Bg.copy(alpha = 0.85f))
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItem(Icons.Rounded.Home, l.t("Inicio", "Home"), selected = true) {}
            NavItem(Icons.Rounded.Groups, l.t("Compartido", "Shared"), selected = false) { vm.go(Screen.SHARED) }
            NavItem(Icons.Rounded.Settings, l.t("Ajustes", "Settings"), selected = false) { vm.go(Screen.SETTINGS) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (selected) Pal.Cyan else Pal.Sub, modifier = Modifier.size(28.dp))
        Text(label, color = if (selected) Pal.Cyan else Pal.Sub, fontSize = 11.sp)
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

    LaunchedEffect(vm.turns.size, listening) {
        val count = vm.turns.size + if (listening) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
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
            AvatarBadge(40.dp)
            Spacer(Modifier.width(8.dp))
        }

        // Idiomas
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            LangPill(me)
            Spacer(Modifier.width(12.dp))
            RoundIconButton(Icons.Rounded.SwapHoriz, "Cambiar idioma", { vm.updateMyLang(me.other) }, size = 50.dp, tint = Pal.Cyan)
            Spacer(Modifier.width(12.dp))
            LangPill(me.other)
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
                    Text(
                        me.t("Tocá el micrófono y hablá en español.", "Tap the mic and speak English."),
                        color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        me.t("Tocá de nuevo para terminar y la voz lo dice en inglés.", "Tap again to finish and the voice says it in Spanish."),
                        color = Pal.Sub, fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(vm.turns, key = { it.id }) { turn ->
                    val last = turn == vm.turns.lastOrNull() && !listening
                    if (last) MainTurnCard(turn) { vm.replay(turn) }
                    else SmallTurnCard(turn) { vm.replay(turn) }
                }
                if (listening) {
                    item {
                        CardBox(Modifier.fillMaxWidth(), accent = Pal.Cyan) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                SmallLabel(me.t("Te escucho…", "Listening…"), color = Pal.Cyan)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    vm.liveText.ifBlank { "…" },
                                    style = BigText.copy(fontSize = 24.sp), textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Controles
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIconButton(Icons.Rounded.DeleteSweep, "Borrar", { vm.clearTurns() }, tint = Pal.Sub, size = 50.dp)
            MicButton(
                lang = me,
                listening = listening,
                working = vm.workingLang == me,
                level = vm.micLevel,
                onClick = { mic(me) },
            )
            RoundIconButton(Icons.Rounded.CameraAlt, "Foto y texto", { vm.go(Screen.READ) }, tint = Pal.GoldLight, size = 50.dp)
        }
        Text(
            when {
                listening -> me.t("Escuchando… tocá para terminar", "Listening… tap to finish")
                vm.workingLang == me -> me.t("Traduciendo…", "Translating…")
                vm.speaker.speaking -> me.t("Hablando…", "Speaking…")
                else -> me.t("Tocá para hablar", "Tap to speak")
            },
            color = if (listening) Pal.Cyan else Pal.Text,
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** La última traducción, grande, como en el diseño. */
@Composable
private fun MainTurnCard(turn: Turn, onReplay: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 30.dp)
    ) {
        CardBox(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SmallLabel("Texto traducido / Translated Text")
                Spacer(Modifier.height(10.dp))
                Text(turn.original, style = BigText.copy(fontSize = 24.sp, lineHeight = 30.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .width(48.dp)
                        .height(2.dp)
                        .background(Pal.Sub.copy(alpha = 0.5f))
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    turn.translated,
                    style = BigText.copy(fontSize = 30.sp, lineHeight = 36.sp, color = Pal.Cyan),
                    textAlign = TextAlign.Center,
                )
                if (turn.online) {
                    Spacer(Modifier.height(6.dp))
                    Text("IA", color = Pal.Ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 28.dp)
        ) { SpeakerButton(onReplay) }
    }
}

@Composable
private fun SmallTurnCard(turn: Turn, onReplay: () -> Unit) {
    CardBox(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagCircle(turn.from, 24.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(turn.original, color = Pal.Sub, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(turn.translated, color = Pal.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.AutoMirrored.Rounded.VolumeUp, "Repetir", onReplay, tint = Pal.Cyan, size = 40.dp, bg = Pal.Bg2)
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
        ) { SharedHalf(vm, top) { mic(top) } }

        // Barra del medio
        Row(
            Modifier
                .fillMaxWidth()
                .background(Pal.Bg.copy(alpha = 0.9f))
                .border(1.dp, Pal.Line)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Icons.Rounded.Home, "Inicio", { vm.go(Screen.HOME) }, size = 42.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                OnlineChip(vm.online, bottom) { vm.updateOnline(!vm.online) }
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Rounded.DeleteSweep, "Borrar", { vm.clearTurns() }, tint = Pal.Sub, size = 42.dp)
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Rounded.SwapVert, "Invertir", { vm.swapShared() }, size = 42.dp, tint = Pal.Cyan)
        }

        // Mitad de abajo
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { SharedHalf(vm, bottom) { mic(bottom) } }
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagCircle(lang, 34.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    listening -> lang.t("Te escucho…", "Listening…")
                    last != null && last.from == lang.other -> lang.t("Te dijo:", "They said:")
                    last != null -> lang.t("Dijiste:", "You said:")
                    else -> lang.t("Tu idioma: español", "Your language: English")
                },
                color = if (listening) Pal.Cyan else Pal.Sub, fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            if (last != null && last.from == lang.other && !listening) {
                SpeakerButton({ vm.replay(last) }, size = 46.dp)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            when {
                listening -> Text(
                    vm.liveText.ifBlank { "…" },
                    style = BigText.copy(fontSize = 28.sp, lineHeight = 34.sp, color = Pal.Text.copy(alpha = 0.9f)),
                )
                last == null -> Text(
                    lang.t("Tocá el micrófono y hablá", "Tap the mic and speak"),
                    color = Pal.Sub, fontSize = 20.sp,
                )
                last.from == lang.other -> Text(
                    last.translated,
                    style = BigText.copy(fontSize = 34.sp, lineHeight = 40.sp),
                    modifier = Modifier.clickable { vm.replay(last) },
                )
                else -> Column {
                    Text(last.original, color = Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("→ " + last.translated, color = Pal.Sub, fontSize = 16.sp)
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MicButton(lang, listening, working, vm.micLevel, onMic, size = 84.dp)
        }
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
                    .clip(RoundedCornerShape(18.dp))
                    .background(Pal.Card.copy(alpha = 0.9f))
                    .border(1.dp, Pal.Line, RoundedCornerShape(18.dp))
                    .padding(4.dp)
            ) {
                val tabs = listOf(
                    Pair(me.t("Foto", "Photo"), Icons.Rounded.CameraAlt),
                    Pair(me.t("Escribir", "Type"), Icons.Rounded.Keyboard),
                )
                tabs.forEachIndexed { i, pair ->
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (tab == i) Pal.Gold else Color.Transparent)
                            .clickable { tab = i }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(pair.second, null, tint = if (tab == i) Color.White else Pal.Sub, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(pair.first, color = if (tab == i) Color.White else Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                LangPill(vm.readFrom)
                Spacer(Modifier.width(12.dp))
                RoundIconButton(Icons.Rounded.SwapHoriz, "Cambiar", { vm.swapRead() }, size = 48.dp, tint = Pal.Cyan)
                Spacer(Modifier.width(12.dp))
                LangPill(vm.readFrom.other)
            }
            Spacer(Modifier.height(14.dp))

            if (tab == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    textStyle = TextStyle(fontSize = 18.sp, color = Pal.Text),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Pal.Cyan,
                        unfocusedBorderColor = Pal.Line,
                        cursorColor = Pal.Cyan,
                        focusedContainerColor = Pal.Card,
                        unfocusedContainerColor = Pal.Card,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    me.t("Traducir", "Translate"), onClick = { vm.translateTyped(typed) },
                    color = Pal.Cyan, enabled = typed.isNotBlank() && !vm.readBusy,
                    icon = Icons.Rounded.Translate,
                )
            }

            Spacer(Modifier.height(16.dp))
            if (vm.readBusy) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Pal.Cyan) }
            }
            if (vm.readOriginal.isNotBlank()) {
                CardBox(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlagCircle(vm.readFrom, 22.dp)
                            Spacer(Modifier.width(8.dp))
                            SmallLabel(vm.readFrom.label)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(vm.readOriginal, color = Pal.Sub, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (vm.readTranslated.isNotBlank()) {
                val to = vm.readFrom.other
                CardBox(Modifier.fillMaxWidth(), accent = Pal.Cyan) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlagCircle(to, 22.dp)
                            Spacer(Modifier.width(8.dp))
                            SmallLabel(to.label, color = Pal.Cyan)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(vm.readTranslated, style = BigText.copy(fontSize = 22.sp, lineHeight = 29.sp, color = Pal.Cyan))
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SpeakerButton({ vm.speakText(vm.readTranslated, to) }, size = 50.dp)
                            RoundIconButton(Icons.Rounded.ContentCopy, "Copiar", {
                                clipboard.setText(AnnotatedString(vm.readTranslated))
                                vm.notice = me.t("Copiado", "Copied")
                            }, tint = Pal.Sub, bg = Pal.Bg2)
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
            .background(Brush.verticalGradient(listOf(Pal.GoldLight.copy(alpha = 0.35f), Pal.Gold.copy(alpha = 0.55f))))
            .border(2.dp, Pal.GoldLight.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                    .border(2.dp, if (sel) Pal.Cyan else Color.Transparent, RoundedCornerShape(50))
                                    .padding(2.dp)
                            ) { LangPill(l) { vm.updateMyLang(l) } }
                        }
                    }
                }
            }

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
                        colors = SliderDefaults.colors(thumbColor = Pal.Cyan, activeTrackColor = Pal.Cyan),
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
                            when {
                                vm.offlineReady -> me.t("Traducción sin internet lista", "Offline translation ready")
                                vm.downloading -> me.t("Preparando…", "Preparing…")
                                else -> me.t("Se prepara sola con wifi o datos", "Sets itself up with wifi or data")
                            },
                            color = Pal.Text, fontSize = 15.sp, modifier = Modifier.weight(1f),
                        )
                        if (vm.downloading) CircularProgressIndicator(color = Pal.Cyan, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    SmallButton(me.t("Voz sin internet (avanzado)", "Offline speech (advanced)")) { openVoiceSettings(ctx) }
                }
            }

            CardBox(Modifier.fillMaxWidth()) {
                Column {
                    SmallLabel(me.t("Modo con internet (IA)", "Online mode (AI)"))
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(me.t("Usar IA cuando hay internet", "Use AI when online"), vm.online) { vm.updateOnline(it) }
                    Spacer(Modifier.height(8.dp))
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
                        modifier = Modifier
                            .clickable { advanced = !advanced }
                            .padding(vertical = 6.dp),
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
                "Romme Traductor · versión 0.3",
                color = Pal.Sub.copy(alpha = 0.7f), fontSize = 12.sp,
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
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759)),
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
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Pal.Cyan,
            unfocusedBorderColor = Pal.Line,
            focusedLabelColor = Pal.Cyan,
            unfocusedLabelColor = Pal.Sub,
            focusedTextColor = Pal.Text,
            unfocusedTextColor = Pal.Text,
            cursorColor = Pal.Cyan,
        ),
    )
}
