package com.ngocdevv.nativecook.blocks.splash.glowsearchsplash

import android.app.Activity
import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 Glow Search Splash — the launch of an invented visual-search app, Trove. A port of the SwiftUI block
 (GlowSearchSplash.swift is the reference): the same 393-wide design space, field at 44 % of the height, and the
 same 12 s timeline, written out at the top of the Swift file.
*/
private const val LOOP = 12.0
private const val REST_TIME = 5.0
private val BEATS = doubleArrayOf(5.0, 6.6, 8.3, 10.0)

@Composable
fun GlowSearchSplash(autoPlay: Boolean = false, previewTime: Double? = null) {
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
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    // The card bitmaps, drawn once off the main thread at the screen's pixel density (at most 3 px a point).
    val density = LocalDensity.current.density
    val widthDp = LocalConfiguration.current.screenWidthDp
    var art by remember { mutableStateOf<GlowSearchSplashArt?>(null) }
    LaunchedEffect(widthDp, density) {
        val scale = (widthDp * density / 393f).coerceIn(1f, 3f)
        art = withContext(Dispatchers.Default) { GlowSearchSplashArt.make(scale) }
    }

    val frameTime = remember { mutableDoubleStateOf(0.0) }
    val blend = remember { mutableDoubleStateOf(0.0) }
    val elapsed = remember { mutableDoubleStateOf(0.0) }
    val held = remember { mutableDoubleStateOf(0.0) }
    val released = remember { mutableDoubleStateOf(-1.0) }
    val touching = remember { mutableStateOf(false) }
    LaunchedEffect(active, reduced, autoPlay, previewTime) {
        if (previewTime != null || reduced) { frameTime.doubleValue = previewTime?.rem(LOOP) ?: REST_TIME; blend.doubleValue = 0.0; return@LaunchedEffect }
        if (!active) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                // Hold on the first frame (the resting mark) until the cards are drawn, so a slow device
                // never plays the bloom without them.
                if (art != null) elapsed.doubleValue += (now - previous) / 1e9
                previous = now
                val age = elapsed.doubleValue - released.doubleValue
                var mix = 0.0
                frameTime.doubleValue = when {
                    touching.value -> held.doubleValue
                    released.doubleValue >= 0 && age < 2 -> held.doubleValue
                    released.doubleValue >= 0 && age < 2.6 -> { mix = GlowSearchSplashScene.ease((age - 2) / 0.6); held.doubleValue }
                    released.doubleValue >= 0 -> if (autoPlay) (age - 2.6) % LOOP else 0.0
                    autoPlay -> elapsed.doubleValue % LOOP
                    else -> 0.0
                }
                blend.doubleValue = mix
            }
        }
    }

    val scene = remember { GlowSearchSplashScene() }
    Canvas(
        Modifier.fillMaxSize()
            .semantics { contentDescription = "Trove. Search anything: illustration, typography, 3D shapes. Tap to skip ahead, drag to scrub." }
            .pointerInput(reduced, previewTime) {
                if (reduced || previewTime != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = frameTime.doubleValue
                    touching.value = true
                    held.doubleValue = start
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    var last = down.position
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = change.position
                            held.doubleValue = (start + (last.x - down.position.x) / size.width * 8).coerceIn(0.0, LOOP - 0.01)
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        if (abs(last.x - down.position.x) < 8 * density && abs(last.y - down.position.y) < 8 * density) {
                            held.doubleValue = BEATS.firstOrNull { it > start + 0.05 } ?: BEATS[0]
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    } finally { touching.value = false; released.doubleValue = elapsed.doubleValue }
                }
            },
    ) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawColor(android.graphics.Color.BLACK)
        val scale = size.width / 393f
        val height = size.height / scale
        val t = frameTime.doubleValue
        val mix = blend.doubleValue
        canvas.save()
        canvas.scale(scale, scale)
        if (mix <= 0) {
            scene.draw(canvas, t, height.toDouble(), art)
        } else {
            canvas.saveLayerAlpha(0f, 0f, 393f, height, ((1 - mix) * 255).roundToInt())
            scene.draw(canvas, t, height.toDouble(), art)
            canvas.restore()
            canvas.saveLayerAlpha(0f, 0f, 393f, height, (mix * 255).roundToInt())
            scene.draw(canvas, 0.0, height.toDouble(), art)
            canvas.restore()
        }
        canvas.restore()
    }
}

/** Draws one frame from the loop time alone. */
internal class GlowSearchSplashScene {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SANS_SERIF; textSize = 17f }
    private val rect = RectF()
    /** The text starts where "Search anything" is centred, like the pin; longer words run on to the right. */
    private val textX = CENTER_X - textPaint.measureText("Search anything") / 2.0

    companion object {
        val WORDS = listOf("anything", "illustration", "typography", "3D shapes")
        /** When each word after the first starts to be erased. */
        val ERASES = doubleArrayOf(5.5, 7.2, 8.9)
        const val LETTER = 0.045
        const val CENTER_X = 196.5
        const val PILL_WIDTH = 296.0
        const val PILL_HEIGHT = 54.0
        const val PINK = 0xFF4FD8
        const val VIOLET = 0xA855F7
        const val CYAN = 0x22D3EE

        fun ease(value: Double): Double { val u = value.coerceIn(0.0, 1.0); return u * u * (3 - 2 * u) }
        fun clamp(value: Double) = value.coerceIn(0.0, 1.0)
        fun easeOut(value: Double): Double { val u = clamp(value); return 1 - (1 - u).pow(3) }
        fun easeInOut(value: Double): Double { val u = clamp(value); return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2 }
        fun easeOutBack(value: Double): Double { val u = clamp(value) - 1; return 1 + 2.70158 * u * u * u + 1.70158 * u * u }
        fun lerp(a: Double, b: Double, u: Double) = a + (b - a) * u
        fun argb(hex: Int, alpha: Double = 1.0): Int = ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or (hex and 0xFFFFFF)
        private fun prefix(text: String, count: Int) = text.take(max(0, count))

        class Typed(val text: String, val active: Boolean, val hold: Double)

        /** The placeholder at time t, whether a letter is being typed or erased, and when the current pause began. */
        fun typed(t: Double): Typed {
            if (t < 2.4) return Typed("", false, 0.0)
            if (t < 3.0) return Typed(prefix("Search", ((t - 2.4) / 0.1).toInt() + 1), true, 0.0)
            if (t < 3.75) return Typed("Search" + prefix(" anything", ((t - 3.0) / (0.75 / 9)).toInt() + 1), true, 0.0)
            var hold = 3.75
            for (k in 1..3) {
                val e = ERASES[k - 1]; val previous = WORDS[k - 1]; val next = WORDS[k]
                if (t < e) return Typed("Search $previous", false, hold)
                if (t < e + 0.3) return Typed("Search " + prefix(previous, previous.length - ((t - e) / (0.3 / previous.length)).toInt() - 1), true, 0.0)
                val typing = LETTER * next.length
                if (t < e + 0.3 + typing) return Typed("Search " + prefix(next, ((t - e - 0.3) / LETTER).toInt() + 1), true, 0.0)
                hold = e + 0.3 + typing
            }
            if (t < 10.2) return Typed("Search 3D shapes", false, hold)
            if (t < 10.7) return Typed(prefix("Search 3D shapes", 16 - ((t - 10.2) / (0.5 / 16)).toInt() - 1), true, 0.0)
            return Typed("", false, 0.0)
        }

        fun caretAlpha(t: Double, typing: Typed): Double {
            if (t < 1.1 || t >= 11.1) return 0.0
            if (t < 2.0) return 1 - (ease((t - 1.40) / 0.05) - ease((t - 1.70) / 0.05))
            if (t < 2.4 || t >= 10.2 || typing.active || typing.hold == 0.0) return 1.0
            val phase = (t - typing.hold) % 1.0
            return if (phase < 0.5) 1 - ease((phase - 0.45) / 0.05) else ease((phase - 0.95) / 0.05)
        }

        /** 0 = caret, 1 = the open pill. */
        fun pillOpen(t: Double): Double = when {
            t < 2.0 || t >= 11.05 -> 0.0
            t < 2.45 -> easeOut((t - 2.0) / 0.45)
            t < 10.7 -> 1.0
            else -> 1 - easeInOut((t - 10.7) / 0.35)
        }

        fun streak(t: Double) = ease((t - 2.0) / 0.18) * (1 - ease((t - 2.3) / 0.6)) + 0.8 * ease((t - 10.7) / 0.15) * (1 - ease((t - 10.9) / 0.3))

        fun prism(t: Double): Double {
            var g = ease((t - 2.3) / 0.3) * (1 - ease((t - 3.6) / 0.45))
            for (k in 1..3) {
                val start = ERASES[k - 1] + 0.3; val typing = LETTER * WORDS[k].length
                g += 0.55 * ease((t - start) / 0.15) * (1 - ease((t - start - typing) / 0.3))
            }
            return g
        }

        /** 0 = the tag mark, 1 = the caret; the second value is the swell before the squeeze. */
        fun mark(t: Double): Pair<Double, Double> = when {
            t < 0.6 -> 0.0 to 1.0
            t < 0.75 -> 0.0 to 1 + 0.07 * ease((t - 0.6) / 0.15)
            t < 1.1 -> { val u = (t - 0.75) / 0.35; easeInOut(u) to 1 + 0.07 * (1 - ease(u)) }
            t < 11.1 -> 1.0 to 1.0
            else -> (1 - easeOutBack((t - 11.1) / 0.4)) to 1.0
        }

        /**
         * The Trove tag: a rounded rectangle whose top corners are cut at 45° by `cut`, with a punched hole.
         * Once `cut` is no larger than `r` it is a plain rounded rectangle (the caret).
         */
        fun markPath(cx: Double, cy: Double, w: Double, h: Double, r: Double, cut: Double, hole: Double): Path {
            val x = (cx - w / 2).toFloat(); val y = (cy - h / 2).toFloat()
            val wf = w.toFloat(); val hf = h.toFloat(); val rf = r.toFloat(); val cf = cut.toFloat()
            val p = Path()
            p.fillType = Path.FillType.EVEN_ODD
            if (cut > r) {
                p.moveTo(x + cf, y); p.lineTo(x + wf - cf, y); p.lineTo(x + wf, y + cf)
            } else {
                p.moveTo(x + rf, y); p.lineTo(x + wf - rf, y); p.quadTo(x + wf, y, x + wf, y + rf)
            }
            p.lineTo(x + wf, y + hf - rf)
            p.quadTo(x + wf, y + hf, x + wf - rf, y + hf)
            p.lineTo(x + rf, y + hf)
            p.quadTo(x, y + hf, x, y + hf - rf)
            if (cut > r) {
                p.lineTo(x, y + cf)
            } else {
                p.lineTo(x, y + rf); p.quadTo(x, y, x + rf, y)
            }
            p.close()
            if (hole > 0.2) p.addCircle(cx.toFloat(), y + 12, hole.toFloat(), Path.Direction.CW)
            return p
        }
    }

    private fun fresh(alpha: Double = 1.0): Paint {
        paint.reset(); paint.isAntiAlias = true; paint.isDither = true
        paint.color = android.graphics.Color.WHITE
        paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        return paint
    }

    private fun radial(c: NativeCanvas, x: Double, y: Double, r: Double, hex: Int, alpha: Double) {
        if (alpha <= 0.002) return
        val p = fresh()
        p.shader = RadialGradient(x.toFloat(), y.toFloat(), r.toFloat(), intArrayOf(argb(hex, alpha), argb(hex, 0.0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), p)
    }

    private fun roundRect(x: Double, y: Double, w: Double, h: Double, r: Double): Path = Path().apply {
        addRoundRect(RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()), r.toFloat(), r.toFloat(), Path.Direction.CW)
    }

    fun draw(c: NativeCanvas, t: Double, height: Double, art: GlowSearchSplashArt?) {
        val fy = Math.round(height * 0.44).toDouble()
        val fyf = fy.toFloat()
        drawCards(c, t, height, fy, art)
        // A black band keeps the field clear of the cards (it comes in as they bloom); a scrim keeps the status bar legible.
        val band = fresh(ease((t - 3.7) / 0.7))
        band.shader = LinearGradient(0f, fyf - 170, 0f, fyf + 170, intArrayOf(argb(0, 0.0), argb(0), argb(0), argb(0, 0.0)), floatArrayOf(0f, 0.18f, 0.82f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(-20f, fyf - 170, 413f, fyf + 170, band)
        val scrim = fresh()
        scrim.shader = LinearGradient(0f, 0f, 0f, 90f, argb(0, 0.75), argb(0, 0.0), Shader.TileMode.CLAMP)
        c.drawRect(-20f, -20f, 413f, 90f, scrim)

        val typing = typed(t)
        val open = pillOpen(t); val streak = streak(t); val prism = prism(t)
        val typedWidth = if (typing.text.isEmpty()) 0.0 else textPaint.measureText(typing.text).toDouble()
        val caretX = lerp(CENTER_X, textX + typedWidth + 2.5, open)

        // Haze and pill glow.
        radial(c, caretX + 60, fy - 70, 240.0, 0xFFFFFF, 0.10 * prism)
        radial(c, caretX + 30, fy, 120.0, PINK, 0.12 * prism)
        radial(c, CENTER_X - 70, fy, 150.0, PINK, 0.22 * streak)
        radial(c, CENTER_X + 70, fy, 150.0, VIOLET, 0.22 * streak)

        // The field: the caret spreads into a pill that is only a faint glass hint while the glow is on, and nearly invisible at rest.
        if (open > 0) {
            val w = lerp(2.6, PILL_WIDTH, open); val h = lerp(22.0, PILL_HEIGHT, open)
            val pill = roundRect(CENTER_X - w / 2, fy - h / 2, w, h, h / 2)
            val glow = min(1.0, streak + prism)
            c.drawPath(pill, fresh().apply { color = argb(0xFFFFFF, lerp(1.0, 0.01 + 0.035 * glow, ease(open / 0.6))) })
            val rim = fresh(min(1.0, 0.7 * streak + 0.3 * prism) * open)
            rim.style = Paint.Style.STROKE; rim.strokeWidth = 1.2f
            rim.shader = LinearGradient((CENTER_X - w / 2).toFloat(), fyf, (CENTER_X + w / 2).toFloat(), fyf, intArrayOf(argb(PINK), argb(VIOLET), argb(CYAN)), null, Shader.TileMode.CLAMP)
            c.drawPath(pill, rim)
        }

        // The streak flaring across the field.
        if (streak > 0.002) {
            c.save()
            c.translate(CENTER_X.toFloat(), fyf)
            c.scale(1f, 0.09f)
            val flare = fresh()
            flare.shader = RadialGradient(0f, 0f, 170f, intArrayOf(argb(PINK, 0.9 * streak), argb(VIOLET, 0.5 * streak), argb(VIOLET, 0.0)), null, Shader.TileMode.CLAMP)
            c.drawCircle(0f, 0f, 170f, flare)
            c.restore()
            val half = (PILL_WIDTH / 2 * max(open, 0.25)).toFloat()
            val core = fresh()
            core.shader = LinearGradient(CENTER_X.toFloat() - half, fyf, CENTER_X.toFloat() + half, fyf,
                intArrayOf(argb(PINK, 0.0), argb(PINK, streak), argb(0xFFFFFF, streak), argb(VIOLET, streak), argb(VIOLET, 0.0)), null, Shader.TileMode.CLAMP)
            c.drawRect(CENTER_X.toFloat() - half, fyf - 0.75f, CENTER_X.toFloat() + half, fyf + 0.75f, core)
        }

        // The placeholder.
        if (typing.text.isNotEmpty()) {
            textPaint.color = argb(0xFFFFFF, 0.92)
            val metrics = textPaint.fontMetrics
            c.drawText(typing.text, textX.toFloat(), fyf - (metrics.ascent + metrics.descent) / 2, textPaint)
        }

        // The prismatic fan to the right of the caret.
        if (prism > 0.002) {
            val cx = caretX.toFloat()
            c.saveLayerAlpha(cx - 2, fyf - 32, cx + 42, fyf + 32, (min(1.0, prism) * 0.95 * 255).roundToInt())
            val quad = Path().apply {
                moveTo(cx + 2, fyf - 12); lineTo(cx + 34, fyf - 24); lineTo(cx + 34, fyf + 20); lineTo(cx + 2, fyf + 12); close()
            }
            val fan = fresh()
            fan.shader = LinearGradient(0f, fyf - 24, 0f, fyf + 20, intArrayOf(argb(PINK), argb(VIOLET), argb(CYAN)), null, Shader.TileMode.CLAMP)
            c.drawPath(quad, fan)
            val fade = fresh()
            fade.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            fade.shader = LinearGradient(cx + 2, 0f, cx + 34, 0f, argb(0xFFFFFF), argb(0xFFFFFF, 0.0), Shader.TileMode.CLAMP)
            c.drawRect(cx, fyf - 30, cx + 40, fyf + 30, fade)
            c.restore()
            radial(c, caretX + 12, fy - 8, 26.0, PINK, 0.5 * prism)
            radial(c, caretX + 12, fy + 10, 18.0, CYAN, 0.35 * prism)
            radial(c, caretX, fy, 14.0, 0xFFFFFF, 0.35 * prism)
        }

        // The caret, or the mark it comes from.
        if (t < 1.1 || t >= 11.1) {
            val (m, s) = mark(t)
            val w = lerp(38.0, 2.6, m) * s; val h = lerp(50.0, 22.0, m) * s; val r = lerp(8.0, 1.3, m) * s
            val cut = min(13.0, max(0.0, 12 * (1 - m * 1.7))) * s
            val hole = 4.2 * clamp(1 - m * 2) * s
            c.drawPath(markPath(CENTER_X, fy, w, h, r, cut, hole), fresh())
        } else {
            val alpha = caretAlpha(t, typing)
            if (alpha > 0) c.drawPath(roundRect(caretX - 1.3, fy - 11, 2.6, 22.0, 1.3), fresh(alpha))
        }
    }

    private fun drawCards(c: NativeCanvas, t: Double, height: Double, fy: Double, art: GlowSearchSplashArt?) {
        if (art == null || t < 3.45 || t >= 10.95) return
        val flow = t - 3.45
        GlowSearchSplashCard.all.forEachIndexed { index, card ->
            val y = card.baseY - GlowSearchSplashCard.SPEEDS[card.column] * flow
            val cx = card.x + card.width / 2; val cy = y + card.height / 2
            // Bloom out of the field.
            val delay = min(1.0, abs(cy - fy) / 500) * 0.45 + card.column * 0.04
            val p = clamp((t - 3.45 - delay) / 0.8)
            if (p <= 0) return@forEachIndexed
            val grow = easeOut(p)
            val reach = lerp(0.3, 1.0, grow)
            var x = CENTER_X + (cx - CENTER_X) * reach
            var yy = fy + (cy - fy) * reach
            val size = lerp(0.4, 1.0, grow)
            var alpha = ease(p * 1.4)
            val sharp = ease((p - 0.3) / 0.6)
            // Fly away at the end.
            val exitDelay = card.column * 0.05 + clamp(cy / height) * 0.1
            val away = clamp((t - 10.2 - exitDelay) / 0.5)
            yy -= 1100 * away * away * away
            alpha *= 1 - ease((t - 10.55 - exitDelay) / 0.2)
            // Category swaps, top to bottom.
            val swapDelay = clamp(cy / height) * 0.4 + card.column * 0.05
            var from = 0; var swap = 0.0
            for (k in 1..3) {
                val u = clamp((t - ERASES[k - 1] - 0.25 - swapDelay) / 0.4)
                if (u >= 1) from = k else { swap = ease(u); break }
            }
            // The card turns edge-on and back, and shows the next category's artwork once past edge-on.
            val flip = abs(cos(PI * swap))
            val shown = if (swap < 0.5) from else from + 1
            alpha *= 0.55 + 0.45 * flip
            val w = card.width * size * flip; val h = card.height * size
            x -= w / 2; yy -= h / 2
            if (yy > height + 20 || yy + h < -20 || alpha <= 0.002 || w < 0.5) return@forEachIndexed
            rect.set(x.toFloat(), yy.toFloat(), (x + w).toFloat(), (yy + h).toFloat())
            if (sharp < 1 && shown == 0) {
                bitmapPaint.alpha = (alpha * 255).roundToInt()
                c.drawBitmap(art.soft[index], null, rect, bitmapPaint)
                bitmapPaint.alpha = (alpha * sharp * 255).roundToInt()
            } else {
                bitmapPaint.alpha = (alpha * 255).roundToInt()
            }
            c.drawBitmap(art.sharp[shown][index], null, rect, bitmapPaint)
        }
    }
}
