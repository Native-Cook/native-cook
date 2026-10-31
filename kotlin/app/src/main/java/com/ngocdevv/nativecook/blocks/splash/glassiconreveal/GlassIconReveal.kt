package com.ngocdevv.nativecook.blocks.splash.glassiconreveal

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Seconds. The RN reference is the source of layout, drawing and timing values. */
const val LOOP = 7.0
private const val ICON = 200f
private val BACKGROUND = Color(0xFF05070C)

private fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0).toFloat()
private fun cubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun back(x: Float) = 1f + 2.8f * (x - 1f).pow(3) + 1.8f * (x - 1f).pow(2)
private fun glow(t: Double) = cubic(seg(t, 2.4, 3.4)) * (1f - cubic(seg(t, 5.8, 6.6)))

// 0–1.2 clear glass; 1.2–2.6 flood; 1.8–3 runner; 2.9–3.3 head;
// 2.4–3.4 glow; 4.2–5 sheen; 5.8–6.6 drain; 6.6–7 identical rest.
// Like RN, a tap restarts at 1.2 immediately, and reduced motion freezes at t=4.
@Composable
fun GlassIconReveal(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val elapsed = remember { mutableDoubleStateOf(0.0) }
    val origin = remember { mutableDoubleStateOf(0.0) }
    val clock = remember { mutableDoubleStateOf(if (autoPlay && !reduced) 0.0 else 4.0) }
    LaunchedEffect(active, reduced, autoPlay) {
        if (reduced || !autoPlay) {
            clock.doubleValue = 4.0
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                elapsed.doubleValue += (now - previous) / 1e9
                previous = now
                clock.doubleValue = ((elapsed.doubleValue - origin.doubleValue) % LOOP + LOOP) % LOOP
            }
        }
    }
    val density = LocalDensity.current.density
    val painter = remember(density) { GlassIconRevealPainter(density) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tap = {
        origin.doubleValue = elapsed.doubleValue - 1.2
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        Unit
    }
    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        Canvas(Modifier.fillMaxSize()) {
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(density, density)
            canvas.translate(size.width / density / 2f - ICON / 2f, size.height / density * 0.47f - ICON / 2f)
            painter.draw(canvas, clock.doubleValue)
            canvas.restore()
        }
        Column(
            Modifier.offset(y = maxHeight * 0.17f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Now available", style = label(34f, FontWeight.Black, 41f).copy(letterSpacing = (-1).sp))
            Text(
                "Stride · Running coach",
                modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = 0.55f + 0.45f * glow(clock.doubleValue) },
                style = label(17f, FontWeight.Bold, 20f),
            )
        }
        Box(
            Modifier.offset(x = (maxWidth - ICON.dp) / 2f, y = maxHeight * 0.47f - (ICON / 2f).dp)
                .size(ICON.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Stride app icon. Tap to light it."
                    if (autoPlay && !reduced) onClick { tap(); true }
                }
                .pointerInput(autoPlay, reduced) {
                    if (autoPlay && !reduced) detectTapGestures(onTap = { tap() })
                },
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = bottom + 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("★★★★★  4.9 · 12k ratings", modifier = Modifier.padding(bottom = 16.dp), style = label(15f, FontWeight.Bold, 18f, 0.75f))
            Box(
                Modifier.fillMaxWidth().height(58.dp).background(Color.White, RoundedCornerShape(29.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Get it free", style = label(17f, FontWeight.Bold, 20f).copy(color = BACKGROUND))
            }
            Text("Couch to 5K in eight weeks, at your own pace.", modifier = Modifier.padding(top = 12.dp), maxLines = 1, style = label(14f, FontWeight.Normal, 17f, 0.55f))
        }
    }
}

private fun label(size: Float, weight: FontWeight, line: Float, alpha: Float = 1f) = TextStyle(
    color = Color.White.copy(alpha = alpha), fontSize = size.sp, fontWeight = weight, lineHeight = line.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Static blurred material is rasterized once. The animated layers use hardware canvas shaders,
 * including a Gaussian coverage ramp, so the same effect works below API 33 without AGSL. */
private class GlassIconRevealPainter(density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val icon = Path().apply { addRoundRect(0f, 0f, ICON, ICON, 50f, 50f, Path.Direction.CW) }
    private val runner = Path().apply {
        moveTo(44f, 74f)
        cubicTo(66f, 64f, 96f, 62f, 114f, 74f)
        cubicTo(130f, 86f, 110f, 104f, 92f, 110f)
        cubicTo(110f, 118f, 130f, 130f, 140f, 150f)
        moveTo(92f, 110f)
        cubicTo(78f, 126f, 60f, 138f, 42f, 138f)
    }
    private val measure = PathMeasure()
    private val trimmed = Path()
    private val length = PathMeasure(runner, false).let { m ->
        var total = m.length
        while (m.nextContour()) total += m.length
        total
    }
    private val floodGradient = LinearGradient(0f, ICON, ICON, 0f,
        intArrayOf(0xFF6A5CFF.toInt(), 0xFF5B8CFF.toInt(), 0xFF62C6FF.toInt()), null, Shader.TileMode.CLAMP)
    private val glowGradient = RadialGradient(100f, 100f, ICON,
        intArrayOf(NativeColor.argb(191, 80, 120, 255), NativeColor.argb(0, 80, 120, 255)), null, Shader.TileMode.CLAMP)
    private val rimGradient = LinearGradient(0f, 0f, ICON, ICON,
        intArrayOf(NativeColor.argb(140, 255, 255, 255), NativeColor.argb(20, 255, 255, 255), NativeColor.argb(153, 160, 190, 255)), null, Shader.TileMode.CLAMP)
    private val material = material(density)
    private val sheen = sheen(density)
    private val materialRect = RectF(-120f, -120f, 320f, 320f)
    private val sheenRect = RectF()
    private var floodRadius = -1f
    private var floodShader: Shader? = null

    private fun ink(color: Int = NativeColor.WHITE, alpha: Float = 1f): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.color = color
        paint.alpha = (NativeColor.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        return paint
    }

    fun draw(c: NativeCanvas, t: Double) {
        val drain = 1f - cubic(seg(t, 5.8, 6.6))
        val light = glow(t)
        c.drawCircle(100f, 100f, ICON * (0.6f + 0.35f * light), ink(alpha = light).apply { shader = glowGradient })
        c.drawBitmap(material, null, materialRect, ink())
        val radius = 300f * cubic(seg(t, 1.2, 2.6)) * drain
        val clip = c.save()
        c.clipPath(icon)
        if (radius > 0f && drain > 0f) {
            c.drawRect(0f, 0f, ICON, ICON, ink(alpha = drain).apply { shader = flood(radius) })
        }
        val sheenU = seg(t, 4.2, 5.0)
        if (sheenU > 0f && sheenU < 1f) {
            val x = -120f + (ICON + 240f) * sheenU
            c.save()
            c.rotate((0.35 * 180 / Math.PI).toFloat(), 100f, 100f)
            sheenRect.set(x - 50f, -100f, x + 100f, 300f)
            c.drawBitmap(sheen, null, sheenRect, ink(alpha = 0.5f * drain))
            c.restore()
        }
        c.restoreToCount(clip)
        val trim = cubic(seg(t, 1.8, 3.0))
        if (trim > 0f && drain > 0f) {
            trimmed.rewind()
            measure.setPath(runner, false)
            var remaining = length * trim
            do {
                measure.getSegment(0f, minOf(remaining, measure.length), trimmed, true)
                remaining -= measure.length
            } while (remaining > 0f && measure.nextContour())
            c.drawPath(trimmed, ink(alpha = 0.95f * drain).apply { style = Paint.Style.STROKE; strokeWidth = 17f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
        }
        val head = 11f * max(0f, back(seg(t, 2.9, 3.3))) * drain
        if (head > 0f) c.drawCircle(142f, 48f, head, ink(alpha = drain))
        c.drawRoundRect(0.75f, 0.75f, 199.25f, 199.25f, 49f, 49f,
            ink().apply { shader = rimGradient; style = Paint.Style.STROKE; strokeWidth = 1.5f })
    }

    private fun flood(radius: Float): Shader {
        if (radius == floodRadius) return requireNotNull(floodShader)
        val extent = radius + 24f
        val distances = (listOf(0f) + (0..16).map { radius + (it - 8) * 3f }.filter { it > 0f }).distinct()
        val mask = RadialGradient(0f, ICON, extent,
            distances.map { NativeColor.argb((255f * circleCoverage(it, radius)).roundToInt().coerceIn(0, 255), 255, 255, 255) }.toIntArray(),
            distances.map { it / extent }.toFloatArray(), Shader.TileMode.CLAMP)
        return ComposeShader(floodGradient, mask, PorterDuff.Mode.DST_IN).also {
            floodRadius = radius
            floodShader = it
        }
    }

    // Integrate a disk against a sigma-6 Gaussian, including the low coverage of a tiny disk.
    // Radial stops keep the animated blur on the GPU on every supported Android version.
    private fun circleCoverage(distance: Float, radius: Float): Float {
        val left = max(-radius, distance - 24f)
        val right = minOf(radius, distance + 24f)
        if (right <= left) return 0f
        val step = (right - left) / 48f
        var sum = 0f
        for (i in 0..48) {
            val x = left + step * i
            val y = sqrt(max(0f, radius * radius - x * x))
            val normal = exp(-(x - distance).pow(2) / 72f)
            val weight = if (i == 0 || i == 48) 1f else if (i % 2 == 0) 2f else 4f
            sum += weight * normal * erf(y / 8.485281f)
        }
        return (sum * step / (3f * 15.03977f)).coerceIn(0f, 1f)
    }

    private fun erf(x: Float): Float {
        val u = 1f / (1f + 0.3275911f * x)
        return 1f - (((((1.0614054f * u - 1.4531521f) * u) + 1.4214138f) * u - 0.28449672f) * u + 0.2548296f) * u * exp(-x * x)
    }

    private fun blur(sigma: Float) = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL)
    private fun material(density: Float): Bitmap {
        val result = Bitmap.createBitmap(ceil(440 * density).toInt(), ceil(440 * density).toInt(), Bitmap.Config.ARGB_8888)
        val c = NativeCanvas(result)
        c.scale(density, density)
        c.translate(120f, 120f)
        fun blobs(sigma: Float) {
            c.drawCircle(30f, 180f, 60f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NativeColor.argb(89, 90, 110, 200); maskFilter = blur(sigma) })
            c.drawCircle(170f, 30f, 50f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NativeColor.argb(64, 120, 140, 180); maskFilter = blur(sigma) })
        }
        c.save()
        c.clipOutPath(icon)
        blobs(30f)
        c.restore()
        c.save()
        c.clipPath(icon)
        blobs(sqrt(30f * 30f + 16f * 16f))
        c.drawPath(icon, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, ICON, ICON,
                intArrayOf(NativeColor.argb(82, 120, 140, 170), NativeColor.argb(102, 60, 75, 110)), null, Shader.TileMode.CLAMP)
        })
        c.restore()
        return result
    }

    private fun sheen(density: Float): Bitmap {
        val result = Bitmap.createBitmap(ceil(150 * density).toInt(), ceil(400 * density).toInt(), Bitmap.Config.ARGB_8888)
        NativeCanvas(result).apply {
            scale(density, density)
            drawRect(50f, 60f, 100f, 340f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NativeColor.argb(230, 255, 255, 255); maskFilter = blur(14f) })
        }
        return result
    }
}
