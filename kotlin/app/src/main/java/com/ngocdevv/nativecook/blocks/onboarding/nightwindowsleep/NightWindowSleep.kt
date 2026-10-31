package com.ngocdevv.nativecook.blocks.onboarding.nightwindowsleep

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// Port of the React Native reference: a 236 x 300 dp arched window, centered at 45% of
// the full screen height, looks onto a seeded city. The room follows the sky's light.
// LOOP = 9 seconds:
//   0.0–1.2: day, p = 0
//   1.2–4.2: day to sunset, p = 0 -> 0.5, ease-in-out sine
//   4.2–6.0: sunset to night, p = 0.5 -> 1, ease-in-out sine
//   6.0–7.6: night; moon rise is driven by p = 0.7 -> 1
//   7.6–9.0: dawn, p = 1 -> 0, ease-in-out sine
// The tag swings +/-7 degrees every 3 seconds, including during manual scrubbing.
// Touch begins by freezing p, then horizontal translation adds 0.004 per dp. Two seconds
// after release, resume the sunset timeline at its closest 0.1-second sample, just as RN.
// Reduced motion shows the still night scene and disables the gesture and frame clock.

/** Seconds. */
private const val LOOP = 9.0
private const val WW = 236f
private const val WH = 300f
private const val HOLD = 2.0
private val YELLOW = Color(0xFFF2E66B)

private fun clamp01(value: Double) = value.coerceIn(0.0, 1.0)
private fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
private fun easeInOutSine(value: Double) = -(cos(PI * value) - 1.0) / 2.0

// Keep the hash in Double: Float arithmetic produces a different skyline and stars.
private fun hash(n: Int): Double {
    val value = sin(n * 127.1 + 311.7) * 43758.5453
    return value - floor(value)
}

private fun phaseAt(t: Double): Double = when {
    t < 4.2 -> 0.5 * easeInOutSine(seg(t, 1.2, 4.2))
    t < 7.6 -> 0.5 + 0.5 * easeInOutSine(seg(t, 4.2, 6.0))
    else -> 1.0 - easeInOutSine(seg(t, 7.6, LOOP))
}

private class SkyColor(val r: Double, val g: Double, val b: Double) {
    fun mix(other: SkyColor, u: Double) = SkyColor(
        r + (other.r - r) * u,
        g + (other.g - g) * u,
        b + (other.b - b) * u,
    )

    fun argb(alpha: Double = 1.0): Int = AndroidColor.argb(
        (clamp01(alpha) * 255).roundToInt(),
        r.roundToInt().coerceIn(0, 255),
        g.roundToInt().coerceIn(0, 255),
        b.roundToInt().coerceIn(0, 255),
    )
}

private val DAY = arrayOf(SkyColor(62.0, 123.0, 255.0), SkyColor(159.0, 211.0, 255.0))
private val DUSK = arrayOf(SkyColor(59.0, 63.0, 154.0), SkyColor(255.0, 154.0, 77.0))
private val NIGHT = arrayOf(SkyColor(8.0, 12.0, 52.0), SkyColor(32.0, 40.0, 112.0))

private fun skyAt(p: Double): Array<SkyColor> {
    val from = if (p < 0.5) DAY else DUSK
    val to = if (p < 0.5) DUSK else NIGHT
    val u = if (p < 0.5) p / 0.5 else (p - 0.5) / 0.5
    return Array(2) { from[it].mix(to[it], u) }
}

private fun roomAt(p: Double): Color {
    val top = skyAt(p)[0]
    return Color(SkyColor(top.r * 0.32, top.g * 0.32, top.b * 0.32 + 10).argb())
}

/** Only the draw and graphics-layer lambdas observe the changing phase and clock. */
private class NightWindowSleepClock(reducedMotion: Boolean) {
    var phase by mutableDoubleStateOf(if (reducedMotion) 1.0 else 0.0)
    var clock by mutableDoubleStateOf(0.0)
    private var elapsed = 0.0
    private var origin = 0.0
    private var touching = false
    private var manual = false
    private var released = -10.0
    private var grabbed = 0.0

    fun tick(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        val t = ((elapsed - origin) % LOOP + LOOP) % LOOP
        clock = t
        if (touching) return
        if (manual) {
            if (elapsed - released > HOLD) {
                manual = false
                var best = 0.0
                var bestDistance = 9.0
                for (sample in 0..76) {
                    val ts = sample * 0.1
                    val distance = abs(phaseAt(ts) - phase)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = ts
                    }
                }
                origin = elapsed - best
            }
            return
        }
        if (autoPlay) phase = phaseAt(t)
    }

    fun begin() {
        touching = true
        manual = true
        grabbed = phase
    }

    fun drag(translationX: Double) {
        phase = clamp01(grabbed + translationX * 0.004)
    }

    fun release() {
        touching = false
        released = elapsed
    }
}

private class NightWindowSleepArt(density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val moonPath = Path()
    private val cutPath = Path()
    private val shadow = Bitmap.createBitmap(
        (340 * density).roundToInt(),
        (72 * density).roundToInt(),
        Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        val canvas = AndroidCanvas(bitmap)
        canvas.scale(density, density)
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(64, 0, 0, 20)
            maskFilter = BlurMaskFilter((8f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRect(32f, 32f, 308f, 40f, shade)
    }
    private val stars = List(28) { i ->
        doubleArrayOf(hash(i) * WW, hash(i + 40) * WH * 0.6, 0.8 + 1.2 * hash(i + 120), 0.4 + 0.6 * hash(i + 80))
    }
    private val buildings = List(11) { b -> doubleArrayOf(18 + 20 * hash(b + 200), 24 + 70 * hash(b + 300)) }

    private fun solid(color: Int, alpha: Double = 1.0, stroke: Float = 0f): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        paint.alpha = (AndroidColor.alpha(color) * clamp01(alpha)).roundToInt()
        if (stroke > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
        }
        return paint
    }

    private fun gradient(shader: Shader, alpha: Double = 1.0): Paint {
        // A gradient must start opaque, independent of the preceding star/window's alpha.
        solid(AndroidColor.WHITE, alpha)
        paint.shader = shader
        return paint
    }

    private fun radial(x: Float, y: Float, radius: Float, from: Int, to: Int): Shader =
        RadialGradient(x, y, radius, intArrayOf(from, to), null, Shader.TileMode.CLAMP)

    private fun linear(y0: Float, y1: Float, from: Int, to: Int): Shader =
        LinearGradient(0f, y0, 0f, y1, intArrayOf(from, to), null, Shader.TileMode.CLAMP)

    fun draw(canvas: AndroidCanvas, width: Float, height: Float, p: Double) {
        val cx = width / 2
        val cy = height * 0.45f
        val x = cx - WW / 2
        val y = cy - WH / 2
        val sky = skyAt(p)
        val night = clamp01((p - 0.55) / 0.35)

        path.rewind()
        path.moveTo(x, y + WW / 2)
        path.arcTo(RectF(x, y, x + WW, y + WW), 180f, 180f, false)
        path.lineTo(x + WW, y + WH - 18)
        path.quadTo(x + WW, y + WH, x + WW - 18, y + WH)
        path.lineTo(x + 18, y + WH)
        path.quadTo(x, y + WH, x, y + WH - 18)
        path.close()

        canvas.drawCircle(cx, cy, WH, gradient(radial(cx, cy, WH, sky[1].argb(0.35 * (1 - night) + 0.12), sky[1].argb(0.0))))
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(x, y, x + WW, y + WH, gradient(linear(y, y + WH * 0.75f, sky[0].argb(), sky[1].argb())))
        for (star in stars) {
            canvas.drawCircle(x + star[0].toFloat(), y + star[1].toFloat(), star[2].toFloat(), solid(AndroidColor.WHITE, night * star[3]))
        }
        val horizon = y + WH * 0.72f
        val sunY = y + WH * 0.2f + (horizon - (y + WH * 0.2f) + 30) * clamp01(p / 0.55).toFloat()
        if (p < 0.6) {
            val glow = if (p > 0.3) AndroidColor.argb(217, 255, 190, 90) else AndroidColor.argb(204, 255, 255, 230)
            canvas.drawCircle(cx, sunY, 90f, gradient(radial(cx, sunY, 90f, glow, AndroidColor.argb(0, 255, 190, 90))))
            canvas.drawCircle(cx, sunY, 15f, solid(if (p > 0.3) 0xFFFFD27A.toInt() else AndroidColor.WHITE))
        }
        val moon = clamp01((p - 0.7) / 0.3)
        if (moon > 0) {
            val mx = cx + 52
            val my = horizon - 20 - 120 * moon.toFloat()
            canvas.drawCircle(mx, my, 50f, gradient(radial(mx, my, 50f, AndroidColor.argb(102, 200, 210, 255), AndroidColor.argb(0, 200, 210, 255)), moon))
            moonPath.rewind()
            moonPath.addCircle(mx, my, 13f, Path.Direction.CW)
            cutPath.rewind()
            cutPath.addCircle(mx + 6, my - 4, 11f, Path.Direction.CW)
            moonPath.op(cutPath, Path.Op.DIFFERENCE)
            canvas.drawPath(moonPath, solid(0xFFF4F1FF.toInt(), moon))
        }
        canvas.drawRect(
            x, horizon - 50, x + WW, horizon,
            gradient(linear(horizon - 50, horizon, AndroidColor.argb(0, 255, 140, 80), AndroidColor.argb(179, 255, 140, 80)), (1 - abs(p - 0.48) / 0.22).coerceAtLeast(0.0)),
        )
        val cityColor = SkyColor(40.0, 52.0, 90.0).mix(SkyColor(6.0, 8.0, 22.0), clamp01(p * 1.4)).argb()
        var bx = x - 6
        buildings.forEachIndexed { b, building ->
            val bw = building[0].toFloat()
            val bh = building[1].toFloat()
            canvas.drawRect(bx, horizon - bh + 30, bx + bw, horizon + 30 + WH, solid(cityColor))
            for (r in 0..5) {
                for (q in 0..1) {
                    val n = b * 31 + r * 7 + q
                    if (hash(n + 500) > 0.45) continue
                    val wy = horizon - bh + 38 + r * 11
                    if (wy > y + WH - 6) continue
                    val wx = bx + 4 + q * (bw / 2 - 2)
                    canvas.drawRect(wx, wy, wx + 4, wy + 5, solid(0xFFFFD889.toInt(), clamp01((night - hash(n + 700) * 0.6) / 0.4)))
                }
            }
            bx += bw + 2
        }
        canvas.restore()

        canvas.drawPath(path, solid(0xFFE9E4F6.toInt(), stroke = 14f))
        canvas.drawRect(cx - 4, y + 6, cx + 4, y + WH, solid(0xFFE9E4F6.toInt()))
        canvas.drawRect(x, y + WH * 0.5f - 4, x + WW, y + WH * 0.5f + 4, solid(0xFFE9E4F6.toInt()))
        canvas.drawBitmap(shadow, null, RectF(x - 52, y + WH - 24, x + 288, y + WH + 48), solid(AndroidColor.WHITE))
        canvas.drawRoundRect(x - 22, y + WH - 2, x + WW + 22, y + WH + 12, 4f, 4f, solid(0xFFF4F0FB.toInt()))
        // The reference dims only the outer frame, leaving the crossbars and sill bright.
        canvas.drawPath(path, solid(0xFF0A0C28.toInt(), 0.45 * night, 14f))
    }
}

private fun labelStyle(size: Float, weight: FontWeight, color: Color, lineHeight: Float) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
fun NightWindowSleep(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val motion = remember(reduced) { NightWindowSleepClock(reduced) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val art = remember(density) { NightWindowSleepArt(density) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val safeTop = insets.calculateTopPadding()
    val safeBottom = insets.calculateBottomPadding()

    LaunchedEffect(lifecycleOwner, autoPlay, reduced, motion) {
        if (reduced) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    motion.tick(if (previous == 0L) 0.0 else (now - previous) / 1e9, autoPlay)
                    previous = now
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().drawBehind { drawRect(roomAt(motion.phase)) }) {
        val cy = maxHeight.value * 0.45f
        Box(
            Modifier.fillMaxSize().pointerInput(motion, reduced) {
                if (reduced) return@pointerInput
                awaitEachGesture {
                    // RN onBegin runs on finger down, before the 4 dp pan threshold.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    motion.begin()
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    var activated = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.position - down.position
                            if (!activated && delta.getDistance() >= 4.dp.toPx()) activated = true
                            if (activated) {
                                motion.drag((delta.x / density).toDouble())
                                change.consume()
                            }
                        }
                    } finally {
                        motion.release()
                    }
                }
            },
        ) {
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = safeTop + 16.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText("Meet", style = labelStyle(15f, FontWeight.SemiBold, Color.White.copy(alpha = 0.65f), 18f))
                BasicText("Lull", style = labelStyle(46f, FontWeight.Black, Color.White, 55f).copy(letterSpacing = (-1.5f).sp))
                Spacer(Modifier.height(4.dp))
                BasicText("A window to wind down by.", style = labelStyle(16f, FontWeight.Normal, Color.White.copy(alpha = 0.7f), 19f))
            }
            Canvas(
                Modifier.fillMaxSize().semantics {
                    contentDescription = "A window from day to night. Drag across it to change the hour."
                },
            ) {
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                art.draw(canvas, size.width / density, size.height / density, motion.phase)
                canvas.restore()
            }
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (cy + 160).dp).size(180.dp, 110.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        rotationZ = (7 * sin(motion.clock / 3 * PI * 2)).toFloat()
                    },
            ) {
                Box(Modifier.align(Alignment.TopCenter).size(2.dp, 70.dp).background(YELLOW))
                Box(
                    Modifier.align(Alignment.BottomCenter).size(150.dp, 40.dp)
                        .background(Color(0xD91E1E1E), RoundedCornerShape(14.dp))
                        .border(2.dp, YELLOW, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        "Wake 07:00",
                        style = labelStyle(16f, FontWeight.ExtraBold, YELLOW, 19f),
                        modifier = Modifier.graphicsLayer { alpha = (1 - clamp01((motion.phase - 0.45) / 0.15)).toFloat() },
                    )
                    BasicText(
                        "Bedtime 22:30",
                        style = labelStyle(16f, FontWeight.ExtraBold, YELLOW, 19f),
                        modifier = Modifier.graphicsLayer { alpha = clamp01((motion.phase - 0.45) / 0.15).toFloat() },
                    )
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = safeBottom + 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth().height(58.dp).background(YELLOW, RoundedCornerShape(29.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Set my bedtime", style = labelStyle(17f, FontWeight.ExtraBold, Color(0xFF1A1A1A), 20f))
            }
            Spacer(Modifier.height(12.dp))
            BasicText("Lull follows your local sunset.", style = labelStyle(14f, FontWeight.Normal, Color.White.copy(alpha = 0.6f), 17f))
        }
    }
}
