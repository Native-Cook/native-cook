package com.ngocdevv.nativecook.blocks.cards.meshgradientweather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.ngocdevv.nativecook.blocks.cards.meshgradientweather.MeshGradientWeatherMotion.Sym
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * SF Symbols are not on Android, so the weather glyphs are drawn: the multicolour set of the hourly row and the
 * forecast (yellow sun, white cloud and moon, cyan rain) and the monochrome ones of the headers and tiles. Each is
 * laid out in a unit box (-1..1) and drawn at `glyph` size inside a frame of the RN symbol's size.
 */
internal enum class Mono { Location, CloudRain, Umbrella, Wind, Calendar }

private val SunYellow = Color(0xFFFFCC00)
private val RainCyan = Color(0xFF40C8F0)
private val CloudWhite = Color.White

@Composable
internal fun WeatherSymbol(sym: Sym, frame: Dp, glyph: Dp, modifier: Modifier = Modifier, sunK: Float = 0.92f) {
    Canvas(modifier.size(frame)) {
        val s = glyph.toPx() / 2f
        drawMulticolor(sym, Offset(size.width / 2, size.height / 2), s, sunK)
    }
}

@Composable
internal fun MonoSymbol(sym: Mono, frame: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(frame)) {
        val s = size.minDimension / 2f
        drawMono(sym, Offset(size.width / 2, size.height / 2), s, tint)
    }
}

// ----------------------------------------------------------------------------- shapes in the unit box

private fun circle(path: Path, c: Offset, s: Float, x: Float, y: Float, r: Float) {
    path.addOval(Rect(c.x + (x - r) * s, c.y + (y - r) * s, c.x + (x + r) * s, c.y + (y + r) * s))
}

/** A cloud centred on (x, y) in unit coordinates, `k` its scale (1 fills the box's width). */
private fun cloudPath(c: Offset, s: Float, x: Float, y: Float, k: Float): Path {
    val p = Path()
    circle(p, c, s, x + 0.08f * k, y - 0.16f * k, 0.46f * k)
    circle(p, c, s, x - 0.48f * k, y + 0.12f * k, 0.32f * k)
    circle(p, c, s, x + 0.52f * k, y + 0.08f * k, 0.36f * k)
    val base = Path()
    base.addRoundRect(
        RoundRect(
            Rect(c.x + (x - 0.8f * k) * s, c.y + (y + 0.02f * k) * s, c.x + (x + 0.88f * k) * s, c.y + (y + 0.44f * k) * s),
            CornerRadius(0.21f * k * s),
        ),
    )
    val out = Path()
    out.op(p, base, PathOperation.Union)
    return out
}

private fun moonPath(c: Offset, s: Float, x: Float, y: Float, r: Float): Path {
    val a = Path(); circle(a, c, s, x, y, r)
    val b = Path(); circle(b, c, s, x + 0.55f * r, y - 0.5f * r, 0.82f * r)
    val out = Path()
    out.op(a, b, PathOperation.Difference)
    return out
}

private fun sparkle(c: Offset, s: Float, x: Float, y: Float, r: Float): Path {
    val p = Path()
    val cx = c.x + x * s
    val cy = c.y + y * s
    val R = r * s
    val w = R * 0.28f
    p.moveTo(cx, cy - R)
    p.quadraticTo(cx + w * 0.3f, cy - w * 0.3f, cx + R, cy)
    p.quadraticTo(cx + w * 0.3f, cy + w * 0.3f, cx, cy + R)
    p.quadraticTo(cx - w * 0.3f, cy + w * 0.3f, cx - R, cy)
    p.quadraticTo(cx - w * 0.3f, cy - w * 0.3f, cx, cy - R)
    p.close()
    return p
}

private fun DrawScope.sun(c: Offset, s: Float, x: Float, y: Float, k: Float, color: Color, upperHalfOnly: Boolean = false) {
    val cx = c.x + x * s
    val cy = c.y + y * s
    if (upperHalfOnly) {
        val p = Path()
        p.addArc(Rect(cx - 0.36f * k * s, cy - 0.36f * k * s, cx + 0.36f * k * s, cy + 0.36f * k * s), 180f, 180f)
        p.close()
        drawPath(p, color)
    } else {
        drawCircle(color, 0.36f * k * s, Offset(cx, cy))
    }
    val n = 8
    for (i in 0 until n) {
        val a = (i * 2 * PI / n - PI / 2).toFloat()
        if (upperHalfOnly && sin(a) > 0.01f) continue
        val r0 = 0.56f * k * s
        val r1 = 0.8f * k * s
        drawLine(
            color, Offset(cx + cos(a) * r0, cy + sin(a) * r0), Offset(cx + cos(a) * r1, cy + sin(a) * r1),
            strokeWidth = 0.13f * k * s, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drops(c: Offset, s: Float, y: Float, long: Boolean, color: Color, xs: FloatArray = floatArrayOf(-0.36f, 0f, 0.36f)) {
    val len = if (long) 0.3f else 0.16f
    for ((i, x) in xs.withIndex()) {
        val top = y + (if (!long && i % 2 == 1) 0.08f else 0f)
        drawLine(
            color,
            Offset(c.x + (x + 0.08f) * s, c.y + top * s),
            Offset(c.x + (x - 0.06f) * s, c.y + (top + len) * s),
            strokeWidth = 0.12f * s, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMulticolor(sym: Sym, c: Offset, s: Float, sunK: Float) {
    when (sym) {
        Sym.Sun -> sun(c, s, 0f, 0f, sunK, SunYellow)
        Sym.Sunrise, Sym.Sunset -> {
            sun(c, s, 0f, 0.3f, 0.95f, SunYellow, upperHalfOnly = true)
            drawLine(CloudWhite, Offset(c.x - 0.92f * s, c.y + 0.42f * s), Offset(c.x + 0.92f * s, c.y + 0.42f * s), 0.12f * s, StrokeCap.Round)
            drawLine(CloudWhite, Offset(c.x - 0.5f * s, c.y + 0.72f * s), Offset(c.x + 0.5f * s, c.y + 0.72f * s), 0.12f * s, StrokeCap.Round)
            val up = sym == Sym.Sunrise
            val tip = if (up) -0.98f else -0.32f
            val tail = if (up) -0.32f else -0.98f
            drawLine(CloudWhite, Offset(c.x, c.y + tail * s), Offset(c.x, c.y + tip * s), 0.12f * s, StrokeCap.Round)
            val dir = if (up) 1f else -1f
            val arrow = Path().apply {
                moveTo(c.x - 0.22f * s, c.y + (tip + dir * 0.22f) * s)
                lineTo(c.x, c.y + tip * s)
                lineTo(c.x + 0.22f * s, c.y + (tip + dir * 0.22f) * s)
            }
            drawPath(arrow, CloudWhite, style = Stroke(0.12f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Sym.Cloud -> drawPath(cloudPath(c, s, 0f, 0f, 1f), CloudWhite)
        Sym.CloudSun -> {
            sun(c, s, 0.38f, -0.32f, 0.72f, SunYellow)
            drawPath(cloudPath(c, s, -0.2f, 0.2f, 0.78f), CloudWhite)
        }
        Sym.CloudMoon -> {
            drawPath(moonPath(c, s, 0.42f, -0.28f, 0.42f), CloudWhite)
            drawPath(cloudPath(c, s, -0.2f, 0.2f, 0.78f), CloudWhite)
        }
        Sym.Drizzle, Sym.Rain -> {
            drawPath(cloudPath(c, s, 0f, -0.3f, 0.92f), CloudWhite)
            drops(c, s, 0.42f, sym == Sym.Rain, RainCyan)
        }
        Sym.CloudSunRain -> {
            sun(c, s, 0.42f, -0.4f, 0.66f, SunYellow)
            drawPath(cloudPath(c, s, -0.18f, -0.14f, 0.72f), CloudWhite)
            drops(c, s, 0.5f, false, RainCyan, floatArrayOf(-0.46f, -0.18f, 0.1f))
        }
        Sym.MoonStars -> {
            drawPath(moonPath(c, s, -0.12f, 0.16f, 0.66f), CloudWhite)
            drawPath(sparkle(c, s, 0.42f, -0.62f, 0.26f), CloudWhite)
            drawPath(sparkle(c, s, 0.72f, -0.12f, 0.2f), CloudWhite)
        }
        Sym.Moon -> drawPath(moonPath(c, s, -0.05f, 0.05f, 0.82f), CloudWhite)
    }
}

private fun DrawScope.drawMono(sym: Mono, c: Offset, s: Float, tint: Color) {
    when (sym) {
        Mono.Location -> {
            // the navigation arrow, pointing up and to the right
            val p = Path().apply {
                moveTo(c.x + 0.86f * s, c.y - 0.86f * s)
                lineTo(c.x + 0.06f * s, c.y + 0.9f * s)
                lineTo(c.x - 0.04f * s, c.y + 0.04f * s)
                lineTo(c.x - 0.9f * s, c.y - 0.06f * s)
                close()
            }
            drawPath(p, tint)
        }
        Mono.CloudRain -> {
            drawPath(cloudPath(c, s, 0f, -0.32f, 0.95f), tint)
            drops(c, s, 0.42f, false, tint)
        }
        Mono.Umbrella -> {
            val dome = Path().apply {
                addArc(Rect(c.x - 0.9f * s, c.y - 0.8f * s, c.x + 0.9f * s, c.y + 0.9f * s), 180f, 180f)
                close()
            }
            drawPath(dome, tint)
            val handle = Path().apply {
                moveTo(c.x, c.y + 0.05f * s)
                lineTo(c.x, c.y + 0.68f * s)
                quadraticTo(c.x, c.y + 0.9f * s, c.x - 0.2f * s, c.y + 0.9f * s)
                quadraticTo(c.x - 0.34f * s, c.y + 0.9f * s, c.x - 0.36f * s, c.y + 0.72f * s)
            }
            drawPath(handle, tint, style = Stroke(0.14f * s, cap = StrokeCap.Round))
        }
        Mono.Wind -> {
            val st = Stroke(0.12f * s, cap = StrokeCap.Round)
            val a = Path().apply {
                moveTo(c.x - 0.9f * s, c.y - 0.3f * s); lineTo(c.x + 0.4f * s, c.y - 0.3f * s)
                cubicTo(c.x + 0.9f * s, c.y - 0.3f * s, c.x + 0.8f * s, c.y - 0.86f * s, c.x + 0.4f * s, c.y - 0.78f * s)
            }
            val b = Path().apply {
                moveTo(c.x - 0.9f * s, c.y + 0.02f * s); lineTo(c.x + 0.6f * s, c.y + 0.02f * s)
            }
            val d = Path().apply {
                moveTo(c.x - 0.9f * s, c.y + 0.34f * s); lineTo(c.x + 0.2f * s, c.y + 0.34f * s)
                cubicTo(c.x + 0.7f * s, c.y + 0.34f * s, c.x + 0.6f * s, c.y + 0.9f * s, c.x + 0.2f * s, c.y + 0.82f * s)
            }
            drawPath(a, tint, style = st); drawPath(b, tint, style = st); drawPath(d, tint, style = st)
        }
        Mono.Calendar -> {
            val r = Rect(c.x - 0.86f * s, c.y - 0.78f * s, c.x + 0.86f * s, c.y + 0.82f * s)
            drawRoundRect(tint, r.topLeft, r.size, CornerRadius(0.22f * s), style = Stroke(0.12f * s))
            drawRect(tint, Offset(r.left, r.top), androidx.compose.ui.geometry.Size(r.width, 0.32f * s))
            for (row in 0 until 3) for (col in 0 until 4) {
                if (row == 0 && col == 0) continue
                drawCircle(tint, 0.06f * s, Offset(c.x + (-0.45f + col * 0.3f) * s, c.y + (-0.1f + row * 0.28f) * s))
            }
        }
    }
}
