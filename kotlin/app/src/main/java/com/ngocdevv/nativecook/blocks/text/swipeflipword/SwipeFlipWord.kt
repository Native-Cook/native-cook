package com.ngocdevv.nativecook.blocks.text.swipeflipword

import android.content.Context
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Swipe Flip Word — a gesture lesson whose big word "swipe" rolls over like a twisted ribbon. A port
 of the SwiftUI block (SwipeFlipWord.swift); the numbers are the same.

 The word (Archivo Expanded Black, 80 % of the screen's width, hot pink #FF2E6E on violet #430090)
 is printed on all four faces of a square prism lying along it, its half side R = half the word's
 glyph height + 0.04 em, its axis R behind the screen, seen through a camera F = 320 dp in front.
 Every column of the word turns about that axis by its own angle: faces that roll up go back and
 shrink, the next face comes up from below, and faces turned from the viewer are shaded down to
 55 %. Every face shows the same word, so after a quarter turn the word is back at rest. From API 33
 an AGSL runtime shader does it per pixel (as the Metal shader does); below that the word is drawn in
 2 dp strips, each mapped onto its turned faces with a perspective matrix.

 A roll: column u (0 at the side the roll starts from, 1 at the other) turns by ±90° · ease(q), where
 q = clamp(a − u·b), and ease is easeInOutBack with overshoot k (a small wind-up and overshoot).
   autoPlay roll: a = T / 0.9 s, b = 0.35 / 0.9, k = 1.0 — each column turns in 0.9 s, the last
   one starting 0.35 s after the first, so a roll takes 1.25 s and the whole word twists at once.

 autoPlay timeline (LOOP = 4.8 s):
   0.00 … 0.60  rest.
   0.60 … 1.85  roll up, travelling left to right (a swipe to the right).
   1.85 … 3.00  rest.
   3.00 … 4.25  roll down, travelling right to left (a swipe to the left).
   4.25 … 4.80  rest; t = 4.8 is the same frame as t = 0.

 Touch: a horizontal drag scrubs a roll in its direction (right = up, left = down): P = |dx| / (55 %
 of the width), q = clamp(P·1.4 − u·0.4), k = 0; each whole roll passed gives a haptic and the drag
 rolls on. On release the roll finishes (P past 0.5, or a fling over 500 dp/s in its direction) or
 falls back, over 0.4 s (easeOutCubic). A tap plays one roll to the right. 2 s after the last touch
 the loop starts again from rest.
 Reduced motion: the word stays still and the loop is off; a tap dims and restores it (0.5 s).
 */

private const val LOOP = 4.8
private val ROLLS = listOf(0.6 to 1.0, 3.0 to -1.0)
private const val TURN = 0.9
private const val STAGGER = 0.35
private const val OVERSHOOT = 1.0
private const val SCRUB_WIDTH = 0.4
private const val FOCAL = 320.0
private const val HALF_PI = PI / 2
private const val FONT_ASSET = "swipeflipword/swipeflipword_ArchivoExpandedBlack.ttf"

private val Violet = Color(0xFF430090)
private val Pink = Color(0xFFFF2E6E)
private val Lilac = Color(0xFFC7A6FF)

private const val SHADER = """
uniform shader content;
uniform float4 geo;   // cx, cy, R, F (pixels)
uniform float4 word;  // left, width, direction, -
uniform float4 roll;  // a, b, sign, overshoot

float ease(float x, float c1) {
    float c2 = c1 * 1.525;
    if (x < 0.5) {
        float t = 2.0 * x;
        return t * t * ((c2 + 1.0) * t - c2) / 2.0;
    }
    float t = 2.0 * x - 2.0;
    return (t * t * ((c2 + 1.0) * t + c2) + 2.0) / 2.0;
}

float angle(float x) {
    float u = clamp((x - word.x) / word.y, 0.0, 1.0);
    if (word.z < 0.0) u = 1.0 - u;
    float q = clamp(roll.x - u * roll.y, 0.0, 1.0);
    return roll.z * 1.5707963 * ease(q, roll.w);
}

half4 main(float2 pos) {
    float cx = geo.x;
    float cy = geo.y;
    float R = geo.z;
    float F = geo.w;
    float d = pos.y - cy;
    half4 result = half4(0.0);
    float nearest = -1e9;
    for (int k = -1; k <= 2; k++) {
        float x = pos.x;
        float v = 0.0;
        float z = 0.0;
        float psi = 0.0;
        for (int i = 0; i < 3; i++) {
            psi = angle(x) - float(k) * 1.5707963;
            float s = sin(psi);
            float c = cos(psi);
            v = -(F * R * s + d * (F + R - R * c)) / (d * s + F * c);
            z = -R + R * c - v * s;
            x = cx + (pos.x - cx) * (F - z) / F;
        }
        float c = cos(psi);
        if (c > 0.001 && abs(v) <= R && z > nearest) {
            nearest = z;
            half4 color = content.eval(float2(x, cy - v));
            half shade = half(0.55 + 0.45 * c);
            result = half4(color.rgb * shade, color.a);
        }
    }
    return result;
}
"""

private fun nowSeconds() = System.nanoTime() / 1e9

/** What the drawing needs from the roll: q = clamp(a − u·b), the turn's sign, the overshoot and the direction. */
private data class Roll(val a: Double = 0.0, val b: Double = 1.0, val sign: Double = 1.0, val overshoot: Double = 0.0, val direction: Double = 1.0) {
    companion object {
        val Rest = Roll()

        fun auto(elapsed: Double, sign: Double): Roll =
            if (elapsed >= TURN + STAGGER) Rest else Roll(elapsed / TURN, STAGGER / TURN, sign, OVERSHOOT, sign)

        fun scrub(sign: Double, progress: Double): Roll {
            val p = progress - floor(progress)
            if (p <= 0) return Rest
            return Roll(p * (1 + SCRUB_WIDTH), SCRUB_WIDTH, sign, 0.0, sign)
        }
    }
}

private fun ease(x: Double, c1: Double): Double {
    val c2 = c1 * 1.525
    return if (x < 0.5) {
        val t = 2 * x
        t * t * ((c2 + 1) * t - c2) / 2
    } else {
        val t = 2 * x - 2
        (t * t * ((c2 + 1) * t + c2) + 2) / 2
    }
}

/** The word at the size that makes it 80 % of the width, and the prism around it (dp). */
private class WordMetrics(val text: String, screen: Double, typeface: Typeface) {
    val fontSize: Double
    val width: Double
    /** How far the glyphs' vertical centre sits above the baseline. */
    val glyphMid: Double
    /** The prism's half side: half the glyphs' height plus 0.04 em. */
    val radius: Double

    init {
        val paint = Paint().apply { this.typeface = typeface; textSize = 100f }
        // The advance character by character (no kerning, as in every stack).
        fun advance() = text.sumOf { paint.measureText(it.toString()).toDouble() }
        fontSize = screen * 0.8 / max(advance() / 100, 0.1)
        paint.textSize = fontSize.toFloat()
        width = advance()
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        glyphMid = -(bounds.top + bounds.bottom) / 2.0
        radius = bounds.height() / 2.0 + 0.04 * fontSize
    }
}

private class SwipeController(val reduced: Boolean) {
    var frame by mutableDoubleStateOf(nowSeconds())
    var autoStart: Double? = null
    /** A drag: its direction and progress in rolls. */
    var scrubSign = 1.0
    var scrubProgress: Double? = null
    /** A release: from and to (in rolls), its sign and when. */
    var settle: DoubleArray? = null
    var tapRoll: Double? = null
    var dim: Double? = null
    var resume: Job? = null

    fun roll(now: Double): Roll {
        if (reduced) return Roll.Rest
        scrubProgress?.let { return Roll.scrub(scrubSign, it) }
        settle?.let { s ->
            val t = ((now - s[3]) / 0.4).coerceIn(0.0, 1.0)
            val e = 1 - (1 - t).pow(3)
            return Roll.scrub(s[2], s[0] + (s[1] - s[0]) * e)
        }
        tapRoll?.let { return Roll.auto(now - it, 1.0) }
        val start = autoStart
        if (start != null && now >= start) {
            val e = (now - start) % LOOP
            for ((at, sign) in ROLLS.reversed()) if (e >= at) return Roll.auto(e - at, sign)
        }
        return Roll.Rest
    }

    fun dimming(now: Double): Float {
        val since = dim ?: return 1f
        val t = (now - since) / 0.5
        return if (t >= 1) 1f else (1 - 0.6 * sin(PI * max(0.0, t))).toFloat()
    }
}

@Composable
fun SwipeFlipWord(
    modifier: Modifier = Modifier,
    word: String = "swipe",
    lesson: String = "LESSON 01",
    topic: String = "GESTURES",
    caption: String = "One motion to clear a card.\nRight keeps it, left lets it go.",
    action: String = "Continue",
    autoPlay: Boolean = false,
    onAction: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val typeface = remember { Typeface.createFromAsset(context.assets, FONT_ASSET) }
    val family = remember { FontFamily(Font(FONT_ASSET, context.assets)) }
    val c = remember(reduced) { SwipeController(reduced) }
    val scope = rememberCoroutineScope()
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) RuntimeShader(SHADER) else null }

    LaunchedEffect(c) {
        if (autoPlay && !reduced) c.autoStart = nowSeconds()
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Violet)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val density = LocalDensity.current.density
        val metrics = remember(word, width) { WordMetrics(word, width, typeface) }
        val centerY = height * 0.42
        val layerHeight = metrics.radius * 4 + 40
        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(c, width) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        c.resume?.cancel()
                        c.autoStart = null
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var lastWhole = 0
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) break
                            if (reduced) continue
                            val dx = (change.position.x - down.position.x) / density
                            if (abs(dx) <= 4 && !moved) continue
                            if (!moved) {
                                // A drag takes over from whatever plays; a roll in progress is simply replaced.
                                moved = true
                                c.tapRoll = null
                                c.settle = null
                            }
                            val sign = if (dx >= 0) 1.0 else -1.0
                            val progress = abs(dx) / (width * 0.55)
                            if (sign == c.scrubSign && progress.toInt() > lastWhole) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            lastWhole = progress.toInt()
                            c.scrubSign = sign
                            c.scrubProgress = progress
                            change.consume()
                        }
                        val now = nowSeconds()
                        val progress = c.scrubProgress
                        if (progress != null) {
                            val whole = floor(progress)
                            val fling = tracker.calculateVelocity().x / density * c.scrubSign > 500
                            val target = if (progress - whole > 0.5 || fling) whole + 1 else whole
                            if (target > whole) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            c.settle = doubleArrayOf(progress, target, c.scrubSign, now)
                            c.scrubProgress = null
                        } else if (!moved) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (reduced) {
                                c.dim = now
                            } else {
                                c.settle = null
                                c.tapRoll = now
                            }
                        }
                        if (autoPlay && !reduced) {
                            c.resume = scope.launch {
                                delay(2000)
                                c.settle = null
                                c.tapRoll = null
                                c.autoStart = nowSeconds()
                            }
                        }
                    }
                },
        )

        // The word, turned by the shader (API 33+) or drawn in turned strips.
        Canvas(
            Modifier
                .offset(y = (centerY - layerHeight / 2).dp)
                .fillMaxWidth()
                .height(layerHeight.dp)
                .semantics { contentDescription = word }
                .graphicsLayer {
                    alpha = c.dimming(c.frame)
                    if (shader != null && Build.VERSION.SDK_INT >= 33) {
                        renderEffect = rollEffect(shader, c.roll(c.frame), metrics, width, layerHeight, density)
                    }
                },
        ) {
            // With the shader the word is drawn once and only the layer's effect changes per frame.
            if (shader != null) {
                drawFlat(metrics, typeface, paint, width, layerHeight)
            } else {
                drawStrips(metrics, typeface, paint, width, layerHeight, c.roll(c.frame))
            }
        }

        // Header.
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (top + 16).dp)
                .padding(horizontal = 24.dp),
        ) {
            val style = TextStyle(fontFamily = family, fontSize = 11.sp, letterSpacing = 1.6.sp, color = Lilac.copy(alpha = 0.75f))
            BasicText(lesson, style = style, maxLines = 1)
            Spacer(Modifier.weight(1f))
            BasicText(topic, style = style, maxLines = 1)
        }

        BasicText(
            caption,
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp, color = Lilac, textAlign = TextAlign.Center),
            modifier = Modifier
                .offset(x = 24.dp, y = (centerY + metrics.radius * 1.6 + 24).dp)
                .width((width - 48).dp),
        )

        // Page dots and the button.
        val buttonTop = height - max(bottomInset.toDouble(), 20.0) - 16 - 56
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (buttonTop - 30).dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Box(Modifier.size(20.dp, 6.dp).background(Pink, RoundedCornerShape(3.dp)))
            Box(Modifier.size(6.dp).background(Lilac.copy(alpha = 0.4f), CircleShape))
            Box(Modifier.size(6.dp).background(Lilac.copy(alpha = 0.4f), CircleShape))
        }
        Box(
            Modifier
                .offset(x = 24.dp, y = buttonTop.dp)
                .width((width - 48).dp)
                .height(56.dp)
                .background(Pink, RoundedCornerShape(28.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onAction() },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(action, style = TextStyle(fontFamily = family, fontSize = 15.sp, letterSpacing = 0.6.sp, color = Violet), maxLines = 1)
        }
    }
}

@RequiresApi(33)
private fun rollEffect(shader: RuntimeShader, roll: Roll, m: WordMetrics, width: Double, layerHeight: Double, density: Float): androidx.compose.ui.graphics.RenderEffect {
    val left = (width - m.width) / 2
    shader.setFloatUniform("geo", (width / 2 * density).toFloat(), (layerHeight / 2 * density).toFloat(), (m.radius * density).toFloat(), (FOCAL * density).toFloat())
    shader.setFloatUniform("word", (left * density).toFloat(), (m.width * density).toFloat(), roll.direction.toFloat(), 0f)
    shader.setFloatUniform("roll", roll.a.toFloat(), roll.b.toFloat(), roll.sign.toFloat(), roll.overshoot.toFloat())
    return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}

/** The word flat, for the shader to turn. */
private fun DrawScope.drawFlat(m: WordMetrics, typeface: Typeface, paint: Paint, width: Double, layerHeight: Double) {
    paint.typeface = typeface
    paint.textSize = m.fontSize.toFloat()
    paint.color = Pink.toArgb()
    val canvas = drawContext.canvas.nativeCanvas
    canvas.save()
    canvas.scale(density, density)
    canvas.drawText(m.text, ((width - m.width) / 2).toFloat(), (layerHeight / 2 + m.glyphMid).toFloat(), paint)
    canvas.restore()
}

/** Below API 33: the word in 2 dp strips, each mapped onto its turned faces with a perspective matrix. */
private fun DrawScope.drawStrips(m: WordMetrics, typeface: Typeface, paint: Paint, width: Double, layerHeight: Double, roll: Roll) {
    paint.typeface = typeface
    paint.textSize = m.fontSize.toFloat()
    val canvas = drawContext.canvas.nativeCanvas
    val cx = width / 2
    val cy = layerHeight / 2
    val r = m.radius
    val left = (width - m.width) / 2
    val baseline = (cy + m.glyphMid).toFloat()
    val matrix = Matrix()
    val src = FloatArray(8)
    val dst = FloatArray(8)
    val pink = Pink
    canvas.save()
    canvas.scale(density, density)
    var x0 = left - 2
    while (x0 < left + m.width + 2) {
        val x1 = x0 + 2
        var u = ((x0 + 1 - left) / m.width).coerceIn(0.0, 1.0)
        if (roll.direction < 0) u = 1 - u
        val q = (roll.a - u * roll.b).coerceIn(0.0, 1.0)
        val phi = roll.sign * HALF_PI * ease(q, roll.overshoot)
        // The faces facing the viewer, the farthest first.
        val faces = (-1..2).map { k -> phi - k * HALF_PI }.filter { cos(it) > 0.001 }.sortedBy { cos(it) }
        for (psi in faces) {
            val s = sin(psi)
            val co = cos(psi)
            fun corner(x: Double, v: Double, out: FloatArray, i: Int) {
                val yy = -r * s - v * co
                val zz = -r + r * co - v * s
                val p = FOCAL / (FOCAL - zz)
                out[i] = (cx + (x - cx) * p).toFloat()
                out[i + 1] = (cy + yy * p).toFloat()
            }
            corner(x0, r, dst, 0)
            corner(x1, r, dst, 2)
            corner(x1, -r, dst, 4)
            corner(x0, -r, dst, 6)
            src[0] = x0.toFloat(); src[1] = (cy - r).toFloat()
            src[2] = x1.toFloat(); src[3] = (cy - r).toFloat()
            src[4] = x1.toFloat(); src[5] = (cy + r).toFloat()
            src[6] = x0.toFloat(); src[7] = (cy + r).toFloat()
            matrix.setPolyToPoly(src, 0, dst, 0, 4)
            val shade = (0.55 + 0.45 * co).toFloat()
            paint.color = Color(pink.red * shade, pink.green * shade, pink.blue * shade).toArgb()
            canvas.save()
            canvas.concat(matrix)
            canvas.clipRect(x0.toFloat(), (cy - r).toFloat(), x1.toFloat() + 0.5f, (cy + r).toFloat())
            canvas.drawText(m.text, left.toFloat(), baseline, paint)
            canvas.restore()
        }
        x0 = x1
    }
    canvas.restore()
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
