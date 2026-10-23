package com.ngocdevv.nativecook.blocks.splash.petalburstsplash

import android.app.Activity
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*

private const val LOOP = 8.0
private fun ease(value: Double): Double { val u = value.coerceIn(0.0, 1.0); return u * u * (3 - 2 * u) }

/** One self-contained splash. previewTime supports deterministic still previews. */
@Composable
fun PetalBurstSplash(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }
    val frameTime = remember { mutableDoubleStateOf(0.0) }
    val elapsed = remember { mutableDoubleStateOf(0.0) }
    val held = remember { mutableDoubleStateOf(0.0) }
    val released = remember { mutableDoubleStateOf(-1.0) }
    val touching = remember { mutableStateOf(false) }
    LaunchedEffect(active, reduced, autoPlay, previewTime) {
        if (previewTime != null || reduced) { frameTime.doubleValue = previewTime?.rem(LOOP) ?: 4.8; return@LaunchedEffect }
        if (!active) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                elapsed.doubleValue += (now - previous) / 1e9; previous = now
                val age = elapsed.doubleValue - released.doubleValue
                frameTime.doubleValue = when {
                    touching.value -> held.doubleValue
                    released.doubleValue >= 0 && age < 2 -> held.doubleValue
                    released.doubleValue >= 0 && age < 2.6 -> held.doubleValue * (1 - ease((age - 2) / 0.6))
                    released.doubleValue >= 0 -> if (autoPlay) (age - 2.6) % LOOP else 0.0
                    autoPlay -> elapsed.doubleValue % LOOP
                    else -> 0.0
                }
            }
        }
    }
    val painter = remember { PetalBurstSplashPainter() }
    Canvas(Modifier.fillMaxSize()
        .semantics { contentDescription = "Flori. Good things start with hello. Drag to open the petals." }
        .pointerInput(reduced, previewTime) {
            if (reduced || previewTime != null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                touching.value = true
                held.doubleValue = (down.position.x / size.width * 7.4).coerceIn(0.0, 7.4)
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                var last = down.position
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        held.doubleValue = (last.x / size.width * 7.4).coerceIn(0.0, 7.4)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                    if (abs(last.x - down.position.x) < 8 * density) held.doubleValue = 4.8
                } finally { touching.value = false; released.doubleValue = elapsed.doubleValue }
            }
        }) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawColor(android.graphics.Color.parseColor("#FFF9F7"))
        val scale = min(size.width / 393f, size.height / 852f)
        canvas.save(); canvas.translate((size.width - 393 * scale) / 2, (size.height - 852 * scale) / 2); canvas.scale(scale, scale)
        painter.draw(canvas, frameTime.doubleValue)
        canvas.restore()
    }
}

private class PetalBurstSplashPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun ink(color: String, alpha: Double = 1.0): Paint {
        paint.reset(); paint.isAntiAlias = true
        paint.color = android.graphics.Color.parseColor(color)
        paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        return paint
    }
    private fun circle(x: Double, y: Double, radius: Double) = Path().apply { addCircle(x.toFloat(), y.toFloat(), radius.toFloat(), Path.Direction.CW) }
    private fun roundRect(x: Double, y: Double, width: Double, height: Double, radius: Double) = Path().apply {
        addRoundRect(RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat()), radius.toFloat(), radius.toFloat(), Path.Direction.CW)
    }
    private fun fill(c: NativeCanvas, path: Path, color: String, alpha: Double = 1.0) { c.drawPath(path, ink(color, alpha)) }
    private fun stroke(c: NativeCanvas, path: Path, color: String, width: Double, alpha: Double = 1.0) {
        val p = ink(color, alpha); p.style = Paint.Style.STROKE; p.strokeWidth = width.toFloat(); p.strokeCap = Paint.Cap.ROUND; c.drawPath(path, p)
    }
    private fun gradient(c: NativeCanvas, path: Path, colors: List<String>, start: List<Double>, end: List<Double>, alpha: Double = 1.0) {
        val p = ink("#FFFFFF", alpha)
        p.shader = LinearGradient(start[0].toFloat(), start[1].toFloat(), end[0].toFloat(), end[1].toFloat(), colors.map { android.graphics.Color.parseColor(it) }.toIntArray(), null, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
    }
    private fun label(c: NativeCanvas, text: String, x: Double, y: Double, size: Double, color: String, bold: Boolean = false, alpha: Double = 1.0) {
        val p = ink(color, alpha); p.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.textSize = size.toFloat(); p.textAlign = Paint.Align.CENTER
        c.drawText(text, x.toFloat(), y.toFloat() - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
    }
    fun draw(c: NativeCanvas, t: Double) {
        val reset = 1 - ease((t - 5.8) / 1.6)
        val bloom = ease((t - 0.6) / 1.4) * reset
        val open = ease((t - 2) / 1.2) * reset
        val reveal = ease((t - 3) / 0.6) * (1 - ease((t - 5.8) / 0.5))
        val radius = (22 + 26 * bloom) * (1 + 18 * open)
        val petals = Path()
        for (i in 0..240) {
            val a = i / 240.0 * PI * 2
            val r = radius * (1 + 0.24 * cos(6 * a))
            val x = (196.5 + r * cos(a + bloom * PI / 3)).toFloat()
            val y = (426 + r * sin(a + bloom * PI / 3)).toFloat()
            if (i == 0) petals.moveTo(x, y) else petals.lineTo(x, y)
        }
        petals.close(); gradient(c, petals, listOf("#FF885E", "#FF526B"), listOf(80.0, 0.0), listOf(313.0, 852.0))
        for (i in 0 until 12) {
            val a = i * PI / 6 + bloom * 0.65
            val distance = 52 + 54 * bloom
            fill(c, circle(196.5 + cos(a) * distance, 426 + sin(a) * distance, 1.5 + i % 3), if (i % 2 == 0) "#FF526B" else "#FF885E", bloom * (1 - open))
        }
        val emblem = Path()
        for (i in 0..120) {
            val a = i / 120.0 * PI * 2
            val r = 14 * (1 + 0.24 * cos(6 * a))
            val x = (140 + r * cos(a)).toFloat()
            val y = (426 - 20 * reveal + r * sin(a)).toFloat()
            if (i == 0) emblem.moveTo(x, y) else emblem.lineTo(x, y)
        }
        emblem.close(); fill(c, emblem, "#FFFFFF", reveal)
        label(c, "flori", 220.0, 426 - 20 * reveal, 46.0, "#FFFFFF", true, reveal)
        label(c, "Good things start", 196.5, 568 + 16 * (1 - reveal), 30.0, "#FFFFFF", true, reveal)
        label(c, "with hello.", 196.5, 605 + 16 * (1 - reveal), 30.0, "#FFFFFF", true, reveal)
        label(c, "A little spark. A new connection.", 196.5, 649.0, 14.0, "#FFFFFF", false, reveal * 0.88)
        fill(c, roundRect(28.0, 700.0, 337.0, 54.0, 27.0), "#FFFFFF", reveal)
        label(c, "Make a connection", 196.5, 727.0, 15.0, "#FF526B", true, reveal)
        label(c, "Your next chapter starts here", 196.5, 783.0, 11.0, "#FFFFFF", false, reveal * 0.8)
        label(c, "FLORI", 196.5, 494.0, 12.0, "#FF526B", true, 1 - ease((t - 1.2) / 0.5) * reset)
    }

}
