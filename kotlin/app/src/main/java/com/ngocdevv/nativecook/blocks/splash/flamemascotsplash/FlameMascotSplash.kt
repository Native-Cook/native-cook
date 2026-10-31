package com.ngocdevv.nativecook.blocks.splash.flamemascotsplash

import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Seconds. Every motion follows the React Native reference's eight-second clock. */
const val LOOP = 8.0
private val BACKGROUND = Color(0xFFF3F4F8)
private val INK = Color(0xFF16141A)

// 0–1: orange rest; 1–1.8: 3000→150 dp logarithmic shrink; 1.8–3: flicker/gaze.
// 3–3.6: back-eased rise to 34%, grow to 190 dp, stagger the welcome page.
// 3.6–6.5: welcome; 6.5–7: sink; 7–7.8: logarithmic swell; 7.8–8: orange rest.
// Taps add a 26 dp / 0.5 s sine hop while autoplay continues, exactly as in RN.
private fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
private fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun easeOutBack(x: Double) = 1 + 2.5 * (x - 1).pow(3) + 1.5 * (x - 1).pow(2)
private fun logLerp(a: Double, b: Double, u: Double) = exp(ln(a) + (ln(b) - ln(a)) * u)
private fun pageAt(t: Double) = seg(t, 3.0, 3.6) * (1 - seg(t, 6.5, 6.8))

private class Stage(t: Double) {
    var size = 3000.0
    var y = 0.5
    val page = pageAt(t)
    var blink = 0.0
    val gaze = sin(2 * PI * seg(t, 1.9, 3.0)) * 0.6 + sin(2 * PI * seg(t, 4.0, 5.6)) * 0.4

    init {
        if (t >= 1 && t < 3) {
            size = logLerp(3000.0, 150.0, easeInOut(seg(t, 1.0, 1.8)))
        } else if (t >= 3 && t < 6.5) {
            val u = easeOutBack(seg(t, 3.0, 3.6))
            size = 150 + 40 * u
            y = 0.5 - 0.16 * u
        } else if (t >= 6.5 && t < 7) {
            val u = easeInOut(seg(t, 6.5, 7.0))
            size = 190 - 40 * u
            y = 0.34 + 0.16 * u
        } else if (t >= 7) {
            size = logLerp(150.0, 3000.0, seg(t, 7.0, 7.8).pow(3))
        }
        for (at in doubleArrayOf(0.55, 2.4, 4.8)) {
            val x = t - at
            if (x > 0 && x < 0.16) blink = sin(PI * x / 0.16)
        }
    }
}

@Composable
fun FlameMascotSplash(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val elapsed = remember { mutableDoubleStateOf(0.0) }
    val clock = remember { mutableDoubleStateOf(if (autoPlay && !reduced) 0.0 else 4.5) }
    val hopAt = remember { mutableDoubleStateOf(-10.0) }
    LaunchedEffect(active, reduced, autoPlay) {
        if (reduced || !autoPlay) clock.doubleValue = 4.5
        if (reduced || !active) return@LaunchedEffect
        val accumulated = elapsed.doubleValue
        val origin = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            elapsed.doubleValue = accumulated + (now - origin) / 1e9
            if (autoPlay) clock.doubleValue = elapsed.doubleValue % LOOP
        }
    }
    val tap = {
        hopAt.doubleValue = elapsed.doubleValue
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        Unit
    }
    val bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    val art = remember { FlameMascotSplashArt() }
    BoxWithConstraints(
        modifier.fillMaxSize().background(BACKGROUND)
            .pointerInput(reduced) {
                if (!reduced) detectTapGestures(onLongPress = {}, onTap = { tap() })
            }
            .semantics {
                contentDescription = "Ember, a little flame. Tap to make it hop."
                if (!reduced) onClick(label = "Make the flame hop") { tap(); true }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val age = elapsed.doubleValue - hopAt.doubleValue
            val hop = if (!reduced && age > 0 && age < 0.5) 26 * sin(PI * age / 0.5) else 0.0
            val c = drawContext.canvas.nativeCanvas
            c.save()
            c.scale(density, density)
            art.draw(c, clock.doubleValue, hop.toFloat(), size.width / density, size.height / density)
            c.restore()
        }
        fun Modifier.reveal(delay: Double) = graphicsLayer {
            val u = ((pageAt(clock.doubleValue) - delay) / (1 - delay)).coerceIn(0.0, 1.0).toFloat()
            alpha = u
            translationY = 18 * (1 - u) * density
        }
        Column(
            Modifier.offset(y = maxHeight * 0.54f).fillMaxWidth().padding(horizontal = 32.dp).reveal(0.0),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Welcome to Ember", style = label(30, FontWeight.Black).copy(letterSpacing = (-0.8).sp), textAlign = TextAlign.Center)
            Text(
                "Your streaks, kept warm. One small habit a day is enough to keep the fire going.",
                modifier = Modifier.padding(top = 10.dp),
                style = label(16).copy(lineHeight = 22.sp, color = INK.copy(alpha = 0.6f)),
                textAlign = TextAlign.Center,
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = bottom + 14.dp)) {
            CheckRow("I agree to the terms and the privacy notice", Modifier.padding(bottom = 12.dp).reveal(0.13))
            CheckRow("Send me a gentle nudge at 8 pm", Modifier.padding(bottom = 12.dp).reveal(0.26))
            Box(
                Modifier.padding(top = 8.dp).fillMaxWidth().height(58.dp).reveal(0.39).background(INK, RoundedCornerShape(29.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Continue", style = label(17, FontWeight.Bold).copy(color = Color.White))
            }
        }
    }
}

private fun label(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = (size * 1.2f).sp, letterSpacing = 0.sp,
    fontWeight = weight, color = INK,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
private fun CheckRow(text: String, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).background(Color(0xFFFF7A12), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            Text("✓", style = label(14, FontWeight.Black).copy(color = Color.White))
        }
        Text(text, modifier = Modifier.weight(1f), style = label(15).copy(color = INK.copy(alpha = 0.75f)))
    }
}

private class FlameMascotSplashArt {
    private val flame = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0.66f, 0f, -0.82f, intArrayOf(0xFFFF9B22.toInt(), 0xFFFF7A12.toInt(), 0xFFFF4E12.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0.32f, 0.42f, intArrayOf(0xBFFFD65A.toInt(), 0x00FFD65A), null, Shader.TileMode.CLAMP)
    }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NativeColor.WHITE }
    private val pupil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A0E08.toInt() }
    private val smile = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A0E08.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val blobs = listOf(
        AuroraBlob(0.15f, 0.05f, 190.0, 0xFFBE8C),
        AuroraBlob(0.75f, 0.02f, 210.0, 0xD696FF),
        AuroraBlob(0.95f, 0.3f, 170.0, 0xA0CDFF),
        AuroraBlob(0.1f, 0.35f, 140.0, 0xFFDCB4),
    )

    fun draw(c: NativeCanvas, t: Double, hop: Float, width: Float, height: Float) {
        val st = Stage(t)
        val cx = width / 2
        val fy = height * st.y.toFloat() - hop
        if (st.page > 0) for (blob in blobs) blob.draw(c, width, height, st.page)
        val w = 2 * PI / LOOP
        val a = (0.05 * sin(5 * w * t)).toFloat()
        val b = (0.06 * sin(7 * w * t + 1.3)).toFloat()
        val d = (0.05 * sin(6 * w * t + 2.1)).toFloat()
        val sway = (0.04 * sin(4 * w * t)).toFloat()
        flame.rewind()
        flame.moveTo(0f, 0.66f)
        flame.cubicTo(-0.36f, 0.66f, -0.53f, 0.4f, -0.5f, 0.1f)
        flame.cubicTo(-0.48f, -0.1f, -0.42f, -0.24f, -0.34f + sway, -0.4f + a)
        flame.cubicTo(-0.26f, -0.28f, -0.18f, -0.22f, -0.13f, -0.24f)
        flame.cubicTo(-0.12f, -0.46f, -0.02f, -0.64f, 0.08f + sway, -0.82f + b)
        flame.cubicTo(0.12f, -0.62f, 0.2f, -0.5f, 0.31f + sway, -0.5f + d)
        flame.cubicTo(0.44f, -0.34f, 0.53f, -0.12f, 0.5f, 0.1f)
        flame.cubicTo(0.53f, 0.4f, 0.36f, 0.66f, 0f, 0.66f)
        flame.close()
        c.save()
        c.translate(cx, fy)
        c.scale(st.size.toFloat(), st.size.toFloat())
        c.translate(0f, -0.12f)
        c.drawPath(flame, fill)
        c.clipPath(flame)
        c.drawCircle(0f, 0.32f, 0.42f, glow)
        c.restore()

        val k = (min(1.0, st.size / 150) * if (st.page > 0) 1 + 0.2 * st.page else 1.0).toFloat()
        val eyeR = 9 * k
        val gx = st.gaze.toFloat() * 3 * k
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * 22 * k
            val ey = fy - 3 * k
            val h = eyeR * (1 - 0.9 * st.blink).toFloat()
            c.drawOval(ex - eyeR, ey - h, ex + eyeR, ey + h, white)
            if (st.blink < 0.6) c.drawCircle(ex + gx + side * 1.2f * k, ey + 0.5f * k, 5.2f * k * (1 - st.blink).toFloat(), pupil)
        }
        smile.strokeWidth = 3.2f * k
        c.drawArc(cx - 9 * k, fy - 2 * k, cx + 9 * k, fy + 12 * k, 25f, 130f, false, smile)
    }
}

/** Cached radial coverage of a Gaussian-blurred disk, supported on every Android API. */
private class AuroraBlob(private val x: Float, private val y: Float, radius: Double, rgb: Int) {
    // The 3x RN reference's Skia mask filter caps its requested 70 pt sigma at 128 px.
    private val sigma = 128.0 / 3
    private val extent = (radius + 3 * sigma).toFloat()
    private val paint: Paint

    init {
        val samples = 256
        val dx = 2 * radius / samples
        val nodes = DoubleArray(samples + 1) { -radius + it * dx }
        val weights = DoubleArray(samples + 1) { i ->
            val halfHeight = sqrt((radius * radius - nodes[i] * nodes[i]).coerceAtLeast(0.0))
            val simpson = if (i == 0 || i == samples) 1 else if (i % 2 == 0) 2 else 4
            simpson * erf(halfHeight / (sqrt(2.0) * sigma))
        }
        val colors = IntArray(129) { i ->
            val distance = extent * i / 128.0
            var integral = 0.0
            for (j in nodes.indices) {
                val q = (nodes[j] - distance) / sigma
                integral += weights[j] * exp(-0.5 * q * q)
            }
            val alpha = if (i == 128) 0 else (255 * integral * dx / (3 * sqrt(2 * PI) * sigma)).roundToInt().coerceIn(0, 255)
            (alpha shl 24) or rgb
        }
        paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NativeColor.WHITE
            shader = RadialGradient(0f, 0f, extent, colors, FloatArray(129) { it / 128f }, Shader.TileMode.CLAMP)
        }
    }

    fun draw(c: NativeCanvas, width: Float, height: Float, page: Double) {
        // RN's setAlphaf replaces the alpha supplied with the color, rather than multiplying it.
        paint.alpha = (255 * page * 0.8).roundToInt()
        c.save()
        c.translate(width * x, height * y)
        c.drawCircle(0f, 0f, extent, paint)
        c.restore()
    }
}

private fun erf(x: Double): Double {
    val z = abs(x)
    val t = 1 / (1 + 0.3275911 * z)
    val p = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t
    return (1 - p * exp(-z * z)) * if (x >= 0) 1 else -1
}
