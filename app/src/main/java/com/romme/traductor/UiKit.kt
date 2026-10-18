package com.romme.traductor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta: azul noche, celeste para el español y ámbar para el inglés. */
object Pal {
    val Bg = Color(0xFF0A1020)
    val Bg2 = Color(0xFF111A30)
    val Card = Color(0xFF162038)
    val Card2 = Color(0xFF1C2844)
    val Line = Color(0x1FFFFFFF)
    val Text = Color(0xFFF2F5FB)
    val Sub = Color(0xFF8E9BB5)
    val Es = Color(0xFF4FC3F7)
    val EsDark = Color(0xFF0B7FB8)
    val En = Color(0xFFFFB547)
    val EnDark = Color(0xFFD9822B)
    val Ok = Color(0xFF4ADE80)
    val Warn = Color(0xFFFFD166)
}

val Lang.color: Color get() = if (this == Lang.ES) Pal.Es else Pal.En
val Lang.colorDark: Color get() = if (this == Lang.ES) Pal.EsDark else Pal.EnDark

val BgBrush = Brush.verticalGradient(listOf(Pal.Bg2, Pal.Bg))

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Es,
            onPrimary = Color(0xFF03131F),
            secondary = Pal.En,
            background = Pal.Bg,
            onBackground = Pal.Text,
            surface = Pal.Card,
            onSurface = Pal.Text,
            surfaceVariant = Pal.Card2,
            onSurfaceVariant = Pal.Sub,
            outline = Pal.Line,
        ),
        typography = Typography(),
        content = content,
    )
}

/** Barra superior simple con botón de volver. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onBack)
        Spacer(Modifier.width(6.dp))
        Text(title, color = Pal.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    tint: Color = Pal.Text,
    size: Dp = 46.dp,
    bg: Color = Pal.Card,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, Pal.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Pastilla que muestra y cambia el modo con/sin internet. */
@Composable
fun OnlineChip(online: Boolean, lang: Lang, onClick: () -> Unit) {
    val color = if (online) Pal.Ok else Pal.Sub
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (online) Icons.Rounded.Wifi else Icons.Rounded.WifiOff, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (online) lang.t("Con internet · IA", "Online · AI") else lang.t("Sin conexión", "Offline"),
            color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun LangBadge(lang: Lang, big: Boolean = false) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(lang.color.copy(alpha = 0.14f))
            .border(1.dp, lang.color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = if (big) 14.dp else 10.dp, vertical = if (big) 7.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(lang.flag, fontSize = if (big) 18.sp else 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(lang.label, color = lang.color, fontSize = if (big) 16.sp else 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * El botón grande del micrófono. Late mientras escucha
 * y crece según la fuerza de la voz.
 */
@Composable
fun MicButton(
    lang: Lang,
    listening: Boolean,
    working: Boolean,
    level: Float,
    onClick: () -> Unit,
    size: Dp = 112.dp,
) {
    val pulse = rememberInfiniteTransition(label = "pulso")
    val ring by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.45f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "aro",
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.45f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "aroAlfa",
    )
    val voice by animateFloatAsState(if (listening) 1f + level * 0.18f else 1f, tween(90), label = "voz")

    val haptic = LocalHapticFeedback.current
    // Toda el área (también el aro) responde al toque, con vibración
    Box(
        Modifier
            .size(size * 1.5f)
            .clip(CircleShape)
            .clickable(enabled = !working) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (listening) {
            Box(
                Modifier
                    .size(size)
                    .scale(ring)
                    .clip(CircleShape)
                    .background(lang.color.copy(alpha = ringAlpha))
            )
            Box(
                Modifier
                    .size(size)
                    .scale(1f + level * 0.32f)
                    .clip(CircleShape)
                    .background(lang.color.copy(alpha = 0.18f))
            )
        }
        Box(
            Modifier
                .size(size)
                .scale(voice)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(lang.color, lang.colorDark))),
            contentAlignment = Alignment.Center,
        ) {
            if (working) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(size * 0.38f))
            } else {
                Icon(
                    if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (listening) "Terminar" else "Hablar",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.44f),
                )
            }
        }
    }
}

/** Tarjeta base con borde suave. */
@Composable
fun CardBox(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Pal.Card)
            .border(1.dp, accent?.copy(alpha = 0.35f) ?: Pal.Line, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) { content() }
}

@Composable
fun SmallLabel(text: String, color: Color = Pal.Sub) {
    Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, color: Color = Pal.Es, enabled: Boolean = true, icon: ImageVector? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else Pal.Card2)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Color(0xFF07121E) else Pal.Sub, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (enabled) Color(0xFF07121E) else Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

val BigText = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, color = Pal.Text)
