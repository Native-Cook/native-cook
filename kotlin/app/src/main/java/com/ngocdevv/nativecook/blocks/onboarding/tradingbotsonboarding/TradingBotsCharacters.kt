package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class BotKind { Orb, Green, Purple, Red, Avatar }

/** Eye pills per bot, as fractions of the bot's size (`TradingBotsCharacter.eyes`). */
private data class Eyes(val gap: Float, val left: Triple<Float, Float, Float>, val right: Triple<Float, Float, Float>, val offsetX: Float, val offsetY: Float, val rightDrop: Float = 0f)

private fun eyesOf(kind: BotKind) = when (kind) {
    BotKind.Orb -> Eyes(0.04f, Triple(0.135f, 0.075f, 4f), Triple(0.14f, 0.075f, -3f), -0.065f, -0.06f)
    BotKind.Purple -> Eyes(0.06f, Triple(0.19f, 0.085f, 0f), Triple(0.19f, 0.085f, 0f), 0f, 0f)
    BotKind.Green -> Eyes(0.08f, Triple(0.085f, 0.115f, -4f), Triple(0.085f, 0.115f, 4f), -0.025f, -0.01f)
    BotKind.Red -> Eyes(0.07f, Triple(0.1f, 0.13f, -8f), Triple(0.1f, 0.13f, 8f), -0.02f, 0.02f, rightDrop = 0.03f)
    BotKind.Avatar -> Eyes(0.09f, Triple(0.11f, 0.15f, 0f), Triple(0.11f, 0.15f, 0f), -0.04f, 0f)
}

private fun imageOf(kind: BotKind) = when (kind) {
    BotKind.Green -> R.drawable.tradingbotsonboarding_bot_green
    BotKind.Purple -> R.drawable.tradingbotsonboarding_bot_purple
    BotKind.Red -> R.drawable.tradingbotsonboarding_bot_red
    else -> R.drawable.tradingbotsonboarding_bot_avatar
}

/** Saturation and brightness as a colour matrix (Rec. 709 weights, like SwiftUI's `.saturation`). */
private fun gradeFilter(saturation: Float, brightness: Float): ColorFilter? {
    if (saturation == 1f && brightness == 0f) return null
    val s = saturation
    val (lr, lg, lb) = Triple(0.2126f, 0.7152f, 0.0722f)
    val b = brightness * 255f
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        lr * (1 - s) + s, lg * (1 - s), lb * (1 - s), 0f, b,
        lr * (1 - s), lg * (1 - s) + s, lb * (1 - s), 0f, b,
        lr * (1 - s), lg * (1 - s), lb * (1 - s) + s, 0f, b,
        0f, 0f, 0f, 1f, 0f,
    )))
}

/**
 * The bots. The selector's orb is a live shader; the other bodies are rendered images. Eyes stay
 * vector so they can look around and blink. `phase`, `look` and `blink` are read while drawing.
 */
@Composable
internal fun Character(
    kind: BotKind,
    size: Float,
    modifier: Modifier = Modifier,
    phase: () -> Float = { 0f },
    look: () -> Offset = { Offset.Zero },
    blink: () -> Float = { 0f },
    saturation: Float = 1f,
    brightness: Float = 0f,
    frost: Float = 0f,
) {
    val image: ImageBitmap? = if (kind == BotKind.Orb) null else ImageBitmap.imageResource(imageOf(kind))
    val orb = remember { if (kind == BotKind.Orb && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(OrbShader) }.getOrNull() else null }
    val grade = remember(saturation, brightness) { gradeFilter(saturation, brightness) }
    Canvas(modifier.size(size.dp)) {
        val s = this.size.width
        if (kind == BotKind.Orb) {
            drawOrb(orb, s, phase())
        } else if (image != null) {
            drawImage(image, dstSize = IntSize(s.roundToInt(), s.roundToInt()), dstOffset = IntOffset.Zero, colorFilter = grade)
            if (kind == BotKind.Green) {
                // the reference's ball is milky over its upper left
                val inset = s * 0.045f
                drawCircle(
                    Brush.linearGradient(listOf(white(0.6f), white(0.18f), white(0f)), start = Offset(inset, inset), end = Offset(s * 0.62f, s * 0.62f)),
                    radius = s / 2 - inset, blendMode = BlendMode.Screen,
                )
            }
        }
        drawEyes(kind, s, look(), blink())
        if (frost > 0f) {
            val side = s * 0.9f
            val left = s * 0.05f
            drawRoundRect(
                Brush.linearGradient(listOf(white(frost), white(frost * 0.25f), white(0f)), start = Offset(left, left), end = Offset(left + side * 0.7f, left + side * 0.75f)),
                topLeft = Offset(left, left), size = Size(side, side), cornerRadius = CornerRadius(s * 0.3f), blendMode = BlendMode.Screen,
            )
        }
    }
}

private fun DrawScope.drawOrb(shader: RuntimeShader?, s: Float, phase: Float) {
    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        shader.setFloatUniform("size", s, s)
        shader.setFloatUniform("phase", phase)
        drawCircle(ShaderBrush(shader), radius = s / 2)
        return
    }
    // Fallback below Android 13: navy heart, blue/teal shell and a frosted rim.
    drawCircle(Brush.radialGradient(listOf(hex(0x000012), hex(0x00052e), hex(0x1b2a9e), hex(0x2f6e86), hex(0x6c86a0)), radius = s / 2), radius = s / 2)
    drawCircle(white(0.18f), radius = s / 2 - 1.5f, style = Stroke(3f))
}

private fun DrawScope.drawEyes(kind: BotKind, s: Float, look: Offset, blink: Float) {
    val eyes = eyesOf(kind)
    val lw = eyes.left.first * s
    val rw = eyes.right.first * s
    val total = lw + eyes.gap * s + rw
    val cx = s / 2 + eyes.offsetX * s + look.x * s
    val cy = s / 2 + eyes.offsetY * s + look.y * s
    drawEye(s, cx - total / 2 + lw / 2, cy, lw, eyes.left.second * s, eyes.left.third, blink)
    drawEye(s, cx + total / 2 - rw / 2, cy + eyes.rightDrop * s, rw, eyes.right.second * s, eyes.right.third, blink)
}

/** A glossy white capsule with a white rim, a soft glow and a faint drop shadow. */
private fun DrawScope.drawEye(s: Float, cx: Float, cy: Float, width: Float, fullHeight: Float, tilt: Float, blink: Float) {
    val height = max(1f, fullHeight * (1 - 0.88f * blink))
    val radius = min(width, height) / 2
    val rim = max(0.8f * density, s * 0.011f)
    rotate(tilt, Offset(cx, cy)) {
        drawIntoCanvas { canvas ->
            // white glow (radius 1.2 % of the bot) and a dark drop (1 %, 0.6 % down)
            val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(max(0.5f, s * 0.012f), 0f, 0f, white(0.45f).toArgb())
            }
            val drop = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(max(0.5f, s * 0.01f), 0f, s * 0.006f, Color.Black.copy(alpha = 0.35f).toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2, radius, radius, glow)
            canvas.nativeCanvas.drawRoundRect(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2, radius, radius, drop)
        }
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White, hex(0xededed), hex(0xadadad)), startY = cy - fullHeight / 2, endY = cy + fullHeight / 2),
            topLeft = Offset(cx - width / 2, cy - height / 2), size = Size(width, height), cornerRadius = CornerRadius(radius),
        )
        drawRoundRect(
            Color.White,
            topLeft = Offset(cx - width / 2 + rim / 2, cy - height / 2 + rim / 2),
            size = Size(width - rim, max(0.5f, height - rim)),
            cornerRadius = CornerRadius(max(0f, radius - rim / 2)),
            style = Stroke(rim),
        )
    }
}

/** The selector's orb with its idle life: the shell's colour turns, the eyes wander and blink. */
@Composable
internal fun LivingOrb(reduced: Boolean) {
    val clock by rememberClock(!reduced)
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 71.5.dp.toPx())
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 2.dp.toPx()
                    color = white(0.2f).toArgb()
                    maskFilter = android.graphics.BlurMaskFilter(1.25f.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawCircle(center.x, center.y, 70.dp.toPx(), paint)
            }
        }
        Character(
            BotKind.Orb,
            136f,
            phase = { val t = if (reduced) 0f else clock; (0.7f - t * 0.45f) % (2 * PI.toFloat()) },
            look = { val t = if (reduced) 0f else clock; Offset(0.02f * sin(t * 0.9f), 0.012f * sin(t * 0.7f + 1)) },
            blink = {
                val t = if (reduced) 0f else clock
                val cycle = (t + 2.3f) % 3.4f
                if (cycle < 0.18f) sin(cycle / 0.18f * PI.toFloat()) else 0f
            },
        )
    }
}
