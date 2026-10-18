package com.romme.traductor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/** Paleta: azul marino, celeste, blanco, grises y toques de rojo. */
object Pal {
    val Bg = Color(0xFF071230)
    val Bg2 = Color(0xFF0C1C44)
    val Card = Color(0xFF13254F)
    val Card2 = Color(0xFF1A2F5E)
    val Line = Color(0x33A9C7FF)
    val Text = Color(0xFFF4F7FC)
    val Sub = Color(0xFF9FB0CF)
    val Cyan = Color(0xFF3DD6F5)
    val Es = Color(0xFF74C3EE)      // celeste argentino
    val EsDark = Color(0xFF2E86C1)
    val En = Color(0xFFEF4E62)      // rojo
    val EnDark = Color(0xFFB0283C)
    val Red = Color(0xFFE8384F)
    val Ok = Color(0xFF4ADE80)
    val Warn = Color(0xFFFFD166)
    val Teal = Color(0xFF1B6F78)
    val TealLight = Color(0xFF47BFC9)
    val Wine = Color(0xFF8C1C2A)
    val WineLight = Color(0xFFDB5566)
    val Gold = Color(0xFFB5860F)
    val GoldLight = Color(0xFFF2C14E)
}

val Lang.color: Color get() = if (this == Lang.ES) Pal.Es else Pal.En
val Lang.colorDark: Color get() = if (this == Lang.ES) Pal.EsDark else Pal.EnDark
val Lang.code: String get() = if (this == Lang.ES) "AR" else "US"

val BgBrush = Brush.verticalGradient(listOf(Pal.Bg2, Pal.Bg))

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Cyan,
            onPrimary = Color(0xFF03131F),
            secondary = Pal.Red,
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

// ─────────────────────────────────────────────────────────────
// FONDO: azul marino con ondas de malla celeste que se mueven lento
// ─────────────────────────────────────────────────────────────
@Composable
fun WaveBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "ondas")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "fase",
    )
    Box(modifier.background(BgBrush)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun band(centerY: Float, amp: Float, lines: Int, spread: Float, color: Color, speed: Float) {
                for (i in 0 until lines) {
                    val k = i.toFloat() / lines
                    val path = Path()
                    var x = 0f
                    val step = w / 60f
                    while (x <= w + step) {
                        val y = centerY + (k - 0.5f) * spread +
                            amp * sin(x / w * 2.6f * PI.toFloat() + phase * speed + k * 2.2f) +
                            amp * 0.45f * sin(x / w * 5.1f * PI.toFloat() - phase * speed * 0.7f + k * 3.1f)
                        if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                        x += step
                    }
                    val a = 0.05f + 0.16f * sin(k * PI.toFloat())
                    drawPath(path, color.copy(alpha = a), style = Stroke(width = 1.1.dp.toPx()))
                }
            }
            band(h * 0.30f, h * 0.035f, 22, h * 0.10f, Pal.Cyan, 1f)
            band(h * 0.86f, h * 0.045f, 26, h * 0.13f, Pal.TealLight, -0.8f)
        }
        content()
    }
}

// ─────────────────────────────────────────────────────────────
// BANDERAS dibujadas (se ven nítidas en cualquier tamaño)
// ─────────────────────────────────────────────────────────────
@Composable
fun FlagCircle(lang: Lang, size: Dp = 36.dp) {
    Canvas(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
    ) {
        val w = this.size.width
        val h = this.size.height
        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, h)) }
        clipPath(circle) {
            if (lang == Lang.ES) {
                val sky = Color(0xFF74ACDF)
                drawRect(sky, Offset(0f, 0f), Size(w, h / 3f))
                drawRect(Color.White, Offset(0f, h / 3f), Size(w, h / 3f))
                drawRect(sky, Offset(0f, 2 * h / 3f), Size(w, h / 3f))
                drawCircle(Color(0xFFF6B40E), radius = w * 0.11f, center = Offset(w / 2, h / 2))
                drawCircle(Color(0xFF85340A), radius = w * 0.11f, center = Offset(w / 2, h / 2), style = Stroke(w * 0.015f))
            } else {
                val stripe = h / 13f
                for (i in 0 until 13) {
                    drawRect(if (i % 2 == 0) Color(0xFFB22234) else Color.White, Offset(0f, i * stripe), Size(w, stripe + 0.5f))
                }
                val cw = w * 0.5f
                val ch = stripe * 7
                drawRect(Color(0xFF3C3B6E), Offset(0f, 0f), Size(cw, ch))
                val cols = 5
                val rows = 4
                for (r in 0 until rows) for (c in 0 until cols) {
                    drawCircle(
                        Color.White, radius = w * 0.018f,
                        center = Offset(cw * (c + 0.6f) / (cols + 0.2f), ch * (r + 0.6f) / (rows + 0.2f)),
                    )
                }
            }
        }
    }
}

/** Pastilla de idioma: bandera + AR / US. */
@Composable
fun LangPill(lang: Lang, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Pal.Card.copy(alpha = 0.9f))
            .border(1.dp, Pal.Line, RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlagCircle(lang, 38.dp)
        Spacer(Modifier.width(10.dp))
        Text(lang.code, color = Pal.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

// Compatibilidad con pantallas que usan la versión vieja
@Composable
fun LangBadge(lang: Lang, big: Boolean = false) = LangPill(lang)

// ─────────────────────────────────────────────────────────────
// LOGO e imagen del personaje
// ─────────────────────────────────────────────────────────────
@Composable
fun LogoCard(modifier: Modifier = Modifier) {
    Box(
        modifier
            .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = Pal.Cyan, spotColor = Pal.Cyan)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF3F3F3))
            .border(2.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_romme),
            contentDescription = "Romme Traductor",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun AvatarBadge(size: Dp = 40.dp) {
    Image(
        painter = painterResource(R.drawable.avatar_romme),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
    )
}

// ─────────────────────────────────────────────────────────────
// BARRA SUPERIOR y botones redondos
// ─────────────────────────────────────────────────────────────
@Composable
fun TopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = Pal.Text, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.width(4.dp))
        Text(title, color = Pal.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(bg.copy(alpha = 1f), bg.copy(alpha = 0.85f))))
            .border(1.dp, Pal.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Botón de "repetir" (parlante), bien visible. */
@Composable
fun SpeakerButton(onClick: () -> Unit, size: Dp = 56.dp) {
    Box(
        Modifier
            .size(size)
            .shadow(10.dp, CircleShape, ambientColor = Pal.Cyan, spotColor = Pal.Cyan)
            .clip(CircleShape)
            .background(Pal.Bg2)
            .border(2.dp, Pal.Cyan.copy(alpha = 0.8f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.VolumeUp, "Repetir",
            tint = Color.White, modifier = Modifier.size(size * 0.52f),
        )
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (online) Icons.Rounded.Wifi else Icons.Rounded.WifiOff, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            if (online) lang.t("Con IA", "AI on") else lang.t("Sin conexión", "Offline"),
            color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─────────────────────────────────────────────────────────────
// MICRÓFONO: rojo con aro celeste. Al escuchar salen ondas a los costados.
// Un toque empieza, otro toque termina. Responde en toda el área.
// ─────────────────────────────────────────────────────────────
@Composable
fun MicButton(
    lang: Lang,
    listening: Boolean,
    working: Boolean,
    level: Float,
    onClick: () -> Unit,
    size: Dp = 104.dp,
) {
    val haptic = LocalHapticFeedback.current
    val t = rememberInfiniteTransition(label = "mic")
    val wave by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "onda",
    )
    val glow by t.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "brillo",
    )
    val voice by animateFloatAsState(if (listening) 1f + level * 0.12f else 1f, tween(90), label = "voz")
    val micSize = size

    Box(
        Modifier
            .width(size * 2.6f)
            .height(size * 1.35f)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = !working) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        // Ondas de sonido a los costados
        if (listening) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(this.size.width / 2, this.size.height / 2)
                val base = micSize.toPx() * 0.62f
                for (i in 0 until 5) {
                    val r = base + i * 9.dp.toPx() + wave * 9.dp.toPx()
                    val alpha = (0.9f - i * 0.17f) * (0.6f + level * 0.4f)
                    val col = if (i % 2 == 0) Pal.Red else Pal.Cyan
                    val sw = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    val topLeft = Offset(c.x - r, c.y - r)
                    val sz = Size(r * 2, r * 2)
                    drawArc(col.copy(alpha = alpha.coerceIn(0f, 1f)), 145f, 70f, false, topLeft, sz, style = sw)
                    drawArc(col.copy(alpha = alpha.coerceIn(0f, 1f)), -35f, 70f, false, topLeft, sz, style = sw)
                }
            }
        }
        // Brillo celeste
        Box(
            Modifier
                .size(size * 1.12f)
                .scale(voice)
                .clip(CircleShape)
                .background(Pal.Cyan.copy(alpha = if (listening) glow * 0.45f else 0.18f))
        )
        // Aro celeste
        Box(
            Modifier
                .size(size)
                .scale(voice)
                .clip(CircleShape)
                .border(4.dp, Pal.Cyan, CircleShape)
                .padding(9.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFF2526A), Color(0xFFC81E3A)))),
            contentAlignment = Alignment.Center,
        ) {
            if (working) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(size * 0.36f))
            } else {
                Icon(
                    if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (listening) "Terminar" else "Hablar",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.42f),
                )
            }
        }
    }
}

/** Tarjeta base. */
@Composable
fun CardBox(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Pal.Card2.copy(alpha = 0.92f), Pal.Card.copy(alpha = 0.92f))))
            .border(1.dp, accent?.copy(alpha = 0.45f) ?: Pal.Line, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) { content() }
}

@Composable
fun SmallLabel(text: String, color: Color = Pal.Sub) {
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

/** Botón grande de color (inicio). */
@Composable
fun BigColorButton(title: String, icon: ImageVector, base: Color, light: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(86.dp)
            .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = light, spotColor = light)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(light.copy(alpha = 0.55f).compositeOver(base), base)))
            .border(2.dp, light.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
    }
}

private fun Color.compositeOver(bg: Color): Color {
    val a = this.alpha
    return Color(
        red = this.red * a + bg.red * (1 - a),
        green = this.green * a + bg.green * (1 - a),
        blue = this.blue * a + bg.blue * (1 - a),
        alpha = 1f,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, color: Color = Pal.Cyan, enabled: Boolean = true, icon: ImageVector? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else Pal.Card2)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Color(0xFF07121E) else Pal.Sub, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (enabled) Color(0xFF07121E) else Pal.Sub, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

val BigText = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Pal.Text)
