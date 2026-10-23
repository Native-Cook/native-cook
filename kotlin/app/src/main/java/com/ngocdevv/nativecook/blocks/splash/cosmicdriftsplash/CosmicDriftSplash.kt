package com.ngocdevv.nativecook.blocks.splash.cosmicdriftsplash

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Cosmic Drift Splash: the launch of Orrery, an invented calm web browser. A port of the Swift
 reference (CosmicDriftSplash.swift); the timeline is written out at the top of that file.
 LOOP = 11 s: end card rest 0–1, card out 1–2.1 (the camera flies into the mark), fly-through of
 56 glossy spheres 1.9–4.1, recede into a spiral 4.1–5.5, dot 5.7, blob 5.85–6.35, morph into the
 mark 6.35–7.15, backdrop back to graphite 7.5–8.2, brand in 7.9–9.2, rest to 11.
*/
private const val LOOP = 11.0
private const val REST_TIME = 9.6

private fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
private fun easeInOut(v: Double): Double {
    val u = v.coerceIn(0.0, 1.0); return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
}
private fun easeOut(v: Double): Double { val u = v.coerceIn(0.0, 1.0); return 1 - (1 - u).pow(3) }
private fun easeIn(v: Double): Double { val u = v.coerceIn(0.0, 1.0); return u * u * u }
private fun easeOutBack(v: Double, s: Double = 1.6): Double { val u = v.coerceIn(0.0, 1.0) - 1; return 1 + u * u * ((s + 1) * u + s) }
private fun smooth(u: Double) = u * u * (3 - 2 * u)
private fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k

/** 1 on the end card, 0 while it is away: out over [a, b], back in over [c, d]. */
private fun card(t: Double, a: Double, b: Double, c: Double, d: Double, out: (Double) -> Double = ::easeInOut, back: (Double) -> Double = ::easeInOut): Double =
    if (t < c) 1 - out(seg(t, a, b)) else back(seg(t, c, d))

@Composable
fun CosmicDriftSplash(autoPlay: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val owner = LocalLifecycleOwner.current
    val reduced = rememberReducedMotion()
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
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

    // Sprites are built once, off the main thread. The film holds on its first frame (the still end
    // card) until they are ready, so the first fly-through never plays without its spheres.
    var sprites by remember { mutableStateOf(CosmicDriftSplashArt.cachedSprites) }
    LaunchedEffect(Unit) { if (sprites == null) sprites = CosmicDriftSplashArt.sprites() }
    val painter = remember { CosmicDriftSplashPainter(context.assets) }

    val frameTime = remember { mutableDoubleStateOf(0.0) }
    val steerX = remember { mutableDoubleStateOf(0.0) }
    val steerY = remember { mutableDoubleStateOf(0.0) }
    val clock = remember { CosmicDriftSplashClock() }
    LaunchedEffect(active, reduced, autoPlay, previewTime) {
        if (previewTime != null || reduced) { frameTime.doubleValue = previewTime?.rem(LOOP) ?: REST_TIME; return@LaunchedEffect }
        if (!active) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                if (sprites != null) clock.elapsed += (now - previous) / 1e9
                previous = now
                frameTime.doubleValue = clock.time(autoPlay)
                val k = clock.steerFactor()
                steerX.doubleValue = clock.steerX * k
                steerY.doubleValue = clock.steerY * k
            }
        }
    }

    val insets = WindowInsets.safeDrawing
    val localDensity = LocalDensity.current
    val top = insets.getTop(localDensity) / density
    val bottom = insets.getBottom(localDensity) / density

    Canvas(
        Modifier.fillMaxSize()
            .semantics { contentDescription = "Orrery. A quieter way to explore the web. Get started. Drag to fly through the planets, tap to skip." }
            .pointerInput(reduced, previewTime) {
                if (reduced || previewTime != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val current = frameTime.doubleValue
                    clock.scrubFrom = if (current < 9.2) current else 0.0
                    clock.held = current
                    clock.touching = true
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    var dx = 0.0; var dy = 0.0
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            dx = ((change.position.x - down.position.x) / density).toDouble()
                            dy = ((change.position.y - down.position.y) / density).toDouble()
                            val width = max(1.0, size.width / density.toDouble())
                            clock.held = (clock.scrubFrom + dx / width * 5).coerceIn(0.0, LOOP - 0.01)
                            clock.steerX = (dx * 0.15).coerceIn(-60.0, 60.0)
                            clock.steerY = (dy * 0.4).coerceIn(-140.0, 140.0)
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        if (abs(dx) < 8 && abs(dy) < 8) {
                            clock.held = 9.3; clock.steerX = 0.0; clock.steerY = 0.0
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    } finally {
                        clock.touching = false
                        clock.released = clock.elapsed
                    }
                }
            },
    ) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(density, density)
        painter.draw(canvas, size.width / density.toDouble(), size.height / density.toDouble(), top.toDouble(), bottom.toDouble(),
            frameTime.doubleValue, steerX.doubleValue, steerY.doubleValue, sprites)
        canvas.restore()
    }
}

/** The film's clock: autoPlay loops, a finger holds and scrubs, a release eases to the nearest rest. */
private class CosmicDriftSplashClock {
    var elapsed = 0.0
    var held = 0.0
    var scrubFrom = 0.0
    var released = -1.0
    var touching = false
    var steerX = 0.0
    var steerY = 0.0

    fun time(autoPlay: Boolean): Double {
        if (touching) return held
        if (released >= 0) {
            val age = elapsed - released
            if (age < 2) return held
            if (age < 2.8) {
                val target = if (held >= 6) LOOP else 0.0
                return held + (target - held) * easeInOut((age - 2) / 0.8)
            }
            return if (autoPlay) (age - 2.8) % LOOP else 0.0
        }
        return if (autoPlay) elapsed % LOOP else 0.0
    }

    fun steerFactor(): Double {
        if (touching) return 1.0
        if (released < 0) return 0.0
        val age = elapsed - released
        return if (age < 2) 1.0 else 1 - easeInOut((age - 2) / 0.8)
    }
}

private class CosmicDriftSplashPainter(assets: android.content.res.AssetManager) {
    private val art = CosmicDriftSplashArt
    private val questrial = Typeface.createFromAsset(assets, "cosmicdriftsplash/cosmicdriftsplash_Questrial-Regular.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()

    private val graphite = doubleArrayOf(0.075, 0.094, 0.102)
    private val night = doubleArrayOf(0.012, 0.016, 0.018)
    private val ink = argb(1.0, 0.945, 0.949, 0.937)
    private val white = argb(1.0, 0.96, 0.96, 0.95)

    private fun argb(a: Double, r: Double, g: Double, b: Double) = android.graphics.Color.argb(
        (a.coerceIn(0.0, 1.0) * 255).roundToInt(), (r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt())

    private fun fresh(color: Int): Paint {
        paint.reset(); paint.isAntiAlias = true; paint.color = color; return paint
    }

    fun draw(c: NativeCanvas, w: Double, h: Double, top: Double, bottom: Double, t: Double, sx: Double, sy: Double, sprites: List<List<Bitmap>>?) {
        val lit = card(t, 1.4, 2.0, 7.5, 8.2)
        c.drawColor(argb(1.0, mix(night[0], graphite[0], lit), mix(night[1], graphite[1], lit), mix(night[2], graphite[2], lit)))
        // A soft pool of light behind the mark on the end card.
        if (lit > 0.01) {
            val p = fresh(android.graphics.Color.WHITE)
            p.shader = RadialGradient((w / 2).toFloat(), (h * 0.42).toFloat(), (w * 0.9).toFloat(),
                intArrayOf(argb(0.035 * lit, 1.0, 1.0, 1.0), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle((w / 2).toFloat(), (h * 0.42).toFloat(), (w * 0.9).toFloat(), p)
        }
        drawStars(c, w, h, t, 0.42 + 0.58 * (1 - lit))
        if (sprites != null) drawField(c, w, h, t, sx, sy, sprites)
        drawMark(c, w, h, t)
        drawCard(c, w, h, top, bottom, t)
    }

    private fun drawStars(c: NativeCanvas, w: Double, h: Double, t: Double, visibility: Double) {
        for (s in art.stars) {
            var b = s.brightness
            if (s.twinkles) b *= 0.55 + 0.45 * sin(2 * PI * s.rate * t / LOOP + s.phase)
            val k = (b * 8).toInt().coerceIn(0, 7)
            val a = (k + 0.5) / 8 * visibility
            val p = if (s.blue) fresh(argb(a, 0.62, 0.74, 1.0)) else fresh(argb(a, 0.93, 0.95, 1.0))
            c.drawCircle((s.u * w).toFloat(), (s.v * h).toFloat(), s.size.toFloat(), p)
        }
    }

    // Fly-through

    private class Projected(val x: Double, val y: Double, val radius: Double, val depth: Double, val alpha: Double,
                            val blur: Double, val palette: Int, val angle: Double, val distance: Double)

    private fun camera(t: Double): Double {
        val tf = t - 1.9
        if (tf < 2.2) return 4.2 * tf
        val s = min(tf - 2.2, 1.4)
        return 9.24 + 4.2 * (s - s * s / 2.8)
    }
    private fun recede(t: Double) = easeInOut(seg(t, 4.1, 5.5))
    private fun roll(t: Double) = 0.32 * (t - 1.9) + 4.2 * recede(t)

    /** Continuous index into blurLevels for a blur given as a fraction of the diameter. */
    private fun blurLevel(blur: Double): Double {
        val levels = art.blurLevels
        for (i in 0 until levels.size - 1) if (blur <= levels[i + 1]) return i + (blur - levels[i]) / (levels[i + 1] - levels[i])
        return (levels.size - 1).toDouble()
    }

    private fun drawField(c: NativeCanvas, w: Double, h: Double, t: Double, sx: Double, sy: Double, sprites: List<List<Bitmap>>) {
        if (t <= 1.85 || t >= 5.75) return
        val show = smooth(seg(t, 1.9, 2.6)) * (1 - smooth(seg(t, 5.05, 5.7)))
        val cam = camera(t); val e = recede(t); val phi = roll(t)
        val spin = (roll(t + 0.01) - phi) / 0.01
        val centring = easeInOut(seg(t, 4.0, 5.4))
        val vx = w / 2 + w * 0.10 * (1 - centring) + sx
        val vy = h / 2 - h * 0.08 * (1 - centring) + sy
        val f = 0.85 * w

        val items = ArrayList<Projected>(art.spheres.size)
        for (s in art.spheres) {
            val d0 = s.z - cam + 1.2
            if (d0 < 0.25) continue // passed the camera: it never comes back
            val d = d0 * (1 + 6 * e) + 6 * e
            if (d < 0.25 || d > 16) continue
            val a = s.angle + phi
            val x = vx + f * s.distance * cos(a) / d; val y = vy + f * s.distance * sin(a) / d
            val r = f * s.radius / d
            val reach = r * CosmicDriftSplashArt.SPRITE_SPAN / 2
            if (x + reach < 0 || x - reach > w || y + reach < 0 || y - reach > h) continue
            val fog = 1 - art.smoothstep(6.0, 14.0, d); val near = art.smoothstep(0.25, 0.7, d0)
            val focus = 2.2
            var blur = if (d < focus) 0.12 * (focus / d - 1) else 0.05 * (d / focus - 1)
            blur = min(0.17, blur + 0.06 * e)
            items.add(Projected(x, y, r, d, show * fog * near, blur, s.palette, atan2(y - vy, x - vx), hypot(x - vx, y - vy)))
        }
        items.sortByDescending { it.depth }

        // Arc trails while the field spirals away.
        if (e > 0.02) {
            val sweep = min(1.6, spin * 0.35)
            for (p in items) {
                if (p.distance <= p.radius) continue
                val pt = fresh(art.palettes[p.palette].trail)
                pt.alpha = (0.18 * p.alpha * min(1.0, e * 3) * 255).roundToInt().coerceIn(0, 255)
                pt.style = Paint.Style.STROKE; pt.strokeCap = Paint.Cap.ROUND
                pt.strokeWidth = max(1.0, min(p.radius * 0.9, 10.0)).toFloat()
                rect.set((vx - p.distance).toFloat(), (vy - p.distance).toFloat(), (vx + p.distance).toFloat(), (vy + p.distance).toFloat())
                c.drawArc(rect, Math.toDegrees(p.angle - sweep).toFloat(), Math.toDegrees(sweep).toFloat(), false, pt)
            }
        }

        for (p in items) {
            if (p.alpha <= 0.005) continue
            val level = blurLevel(p.blur)
            val lo = level.toInt(); val hi = min(art.blurLevels.size - 1, lo + 1); val k = level - lo
            val half = p.radius * CosmicDriftSplashArt.SPRITE_SPAN
            rect.set((p.x - half).toFloat(), (p.y - half).toFloat(), (p.x + half).toFloat(), (p.y + half).toFloat())
            bitmapPaint.alpha = (p.alpha * 255).roundToInt().coerceIn(0, 255)
            c.drawBitmap(sprites[p.palette][lo], null, rect, bitmapPaint)
            if (k > 0.01 && hi != lo) {
                bitmapPaint.alpha = (p.alpha * k * 255).roundToInt().coerceIn(0, 255)
                c.drawBitmap(sprites[p.palette][hi], null, rect, bitmapPaint)
            }
        }

        // Dust glints, with a faint halo on the brightest.
        val cr = cos(phi); val sr = sin(phi)
        for (s in art.specks) {
            val d0 = s.z - cam + 1.2
            if (d0 < 0.3) continue
            val d = d0 * (1 + 6 * e) + 6 * e
            if (d < 0.3 || d > 16) continue
            val rx = s.x * cr - s.y * sr; val ry = s.x * sr + s.y * cr
            val x = vx + f * rx / d; val y = vy + f * ry / d
            if (x < -4 || x > w + 4 || y < -4 || y > h + 4) continue
            val tw = (0.5 + 0.5 * sin(2 * PI * s.rate * t / LOOP + s.phase)).pow(3)
            val a = (0.35 + 0.65 * tw) * (1 - art.smoothstep(6.0, 14.0, d)) * art.smoothstep(0.3, 0.8, d0)
            val r = min(2.6, max(0.5, f * 0.009 * s.size / d))
            val k = min(5, (a * 6).toInt())
            if (a > 0.8) c.drawCircle(x.toFloat(), y.toFloat(), (r * 3).toFloat(), fresh(argb(0.12 * show, 1.0, 1.0, 1.0)))
            c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), fresh(argb((k + 0.5) / 6 * show, 1.0, 1.0, 1.0)))
        }
    }

    // Mark

    private fun drawMark(c: NativeCanvas, w: Double, h: Double, t: Double) {
        val unit = 0.92 // points per mark unit: a 92 pt mark
        val rise = card(t, 1.05, 1.6, 7.9, 8.6)
        val cy = mix(h * 0.5, h * 0.405, rise)
        val px = w / 2 + CosmicDriftSplashArt.VISUAL_OFFSET_X * unit
        val py = cy + CosmicDriftSplashArt.VISUAL_OFFSET_Y * unit

        var scale = 1.0; var alpha = 1.0; var turn = 0.0
        var blob = 0.0; var wobble = 0.0
        var outer = 1.0; var inner = 1.0; var moon = 1.0
        if (t < 5.7) {
            if (t > 2.1) return
            scale = 1 + 3 * easeIn(seg(t, 1.6, 2.1))
            alpha = 1 - seg(t, 1.75, 2.1)
        } else {
            val dot = easeOut(seg(t, 5.7, 5.85)) * 5
            val swell = easeOutBack(seg(t, 5.85, 6.35), 1.2)
            val shrink = easeInOut(seg(t, 6.35, 6.95))
            blob = if (t < 5.85) dot else mix(mix(5.0, 44.0, swell), CosmicDriftSplashArt.PLANET_RADIUS * unit, shrink)
            wobble = 0.07 * sin(PI * seg(t, 5.85, 6.7))
            outer = easeOut(seg(t, 6.38, 6.90)); inner = easeOut(seg(t, 6.50, 7.00))
            moon = easeOutBack(seg(t, 6.85, 7.15), 2.2)
            turn = -0.35 * (1 - easeOut(seg(t, 6.35, 7.2)))
        }
        if (alpha <= 0.002) return

        c.saveLayerAlpha(null, (alpha * 255).roundToInt())
        c.translate(px.toFloat(), py.toFloat())
        c.scale(scale.toFloat(), scale.toFloat())
        c.rotate(Math.toDegrees(turn).toFloat())
        c.save()
        c.scale(unit.toFloat(), unit.toFloat())
        val stroke = fresh(white); stroke.style = Paint.Style.STROKE; stroke.strokeWidth = CosmicDriftSplashArt.STROKE.toFloat()
        stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeJoin = Paint.Join.ROUND
        c.drawPath(art.orbitPath(art.orbits[1], outer), stroke)
        c.drawPath(art.orbitPath(art.orbits[0], inner), stroke)
        if (moon > 0.001) {
            val a = CosmicDriftSplashArt.MOON_ANGLE * PI / 180
            val r = art.orbits[1][0]
            c.drawCircle((r * cos(a)).toFloat(), (r * sin(a)).toFloat(), (CosmicDriftSplashArt.MOON_RADIUS * moon).toFloat(), fresh(white))
        }
        if (t < 5.7) c.drawCircle(0f, 0f, CosmicDriftSplashArt.PLANET_RADIUS.toFloat(), fresh(white))
        c.restore()
        if (t >= 5.7 && blob > 0.01) {
            // The blob: a circle whose radius wobbles in three and five lobes while it swells.
            val path = Path()
            for (i in 0..64) {
                val a = i / 64.0 * 2 * PI
                val rr = blob * (1 + wobble * (0.6 * sin(3 * a + 9 * t) + 0.4 * sin(5 * a - 7 * t)))
                val x = (rr * cos(a)).toFloat(); val y = (rr * sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            c.drawPath(path, fresh(white))
        }
        c.restore()
    }

    // End card

    private fun text(c: NativeCanvas, s: String, x: Double, y: Double, size: Double, color: Int, alpha: Double, face: Typeface, blur: Double = 0.0, tracking: Double = 0.0) {
        val p = fresh(color)
        p.alpha = (alpha * android.graphics.Color.alpha(color)).roundToInt().coerceIn(0, 255)
        p.typeface = face; p.textSize = size.toFloat(); p.textAlign = Paint.Align.CENTER
        p.letterSpacing = (tracking / size).toFloat()
        if (blur > 0.2) p.maskFilter = BlurMaskFilter(blur.toFloat(), BlurMaskFilter.Blur.NORMAL)
        c.drawText(s, x.toFloat(), (y - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2).toFloat(), p)
    }

    private val system = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun drawCard(c: NativeCanvas, w: Double, h: Double, top: Double, bottom: Double, t: Double) {
        val word = card(t, 1.0, 1.45, 8.1, 8.9, ::easeIn, ::easeOut)
        val tag = card(t, 1.0, 1.4, 8.4, 9.1, ::easeIn, ::easeOut)
        val cta = card(t, 1.0, 1.35, 8.5, 9.2, ::easeIn, ::easeOut)
        val close = card(t, 1.0, 1.3, 8.7, 9.2, ::easeIn, ::easeOut)
        val markY = h * 0.405

        if (word > 0.002) {
            c.save()
            c.translate((w / 2).toFloat(), (markY + 76).toFloat())
            val s = (1 + 0.06 * (1 - word)).toFloat(); c.scale(s, s)
            text(c, "orrery", 0.0, 0.0, 46.0, ink, word, questrial, blur = 14 * (1 - word), tracking = 0.5)
            c.restore()
        }
        if (tag > 0.002) {
            text(c, "A quieter way to explore the web.", w / 2, markY + 120 + 6 * (1 - tag), 15.0,
                argb(1.0, 0.55, 0.59, 0.60), tag, questrial, blur = 6 * (1 - tag))
        }
        if (cta > 0.002) {
            val height = 54.0; val side = 24.0
            val y = h - max(bottom, 16.0) - 10 - height + 10 * (1 - cta)
            rect.set(side.toFloat(), y.toFloat(), (w - side).toFloat(), (y + height).toFloat())
            val r = (height / 2).toFloat()
            // The pill comes up from a dim teal to its full colour.
            val p = fresh(argb(cta, mix(0.11, 0.149, cta), mix(0.29, 0.651, cta), mix(0.31, 0.718, cta)))
            c.drawRoundRect(rect, r, r, p)
            val gloss = fresh(android.graphics.Color.WHITE)
            gloss.shader = LinearGradient(0f, rect.top, 0f, rect.centerY(), argb(0.14 * cta, 1.0, 1.0, 1.0), 0x00FFFFFF, Shader.TileMode.CLAMP)
            val inner = RectF(rect.left + 1, rect.top + 1, rect.right - 1, rect.bottom - 1)
            c.drawRoundRect(inner, r - 1, r - 1, gloss)
            text(c, "Get started", w / 2, y + height / 2, 17.0, argb(1.0, 0.03, 0.10, 0.11), cta, system)
        }
        if (close > 0.002) {
            val r = 17.0
            val cx = w - 20 - r; val cy = top + 10 + r
            c.drawCircle(cx.toFloat(), cy.toFloat(), r.toFloat(), fresh(argb(0.07 * close, 1.0, 1.0, 1.0)))
            val p = fresh(argb(0.75 * close, 1.0, 1.0, 1.0)); p.style = Paint.Style.STROKE; p.strokeWidth = 1.7f; p.strokeCap = Paint.Cap.ROUND
            val s = 5.5f
            c.drawLine(cx.toFloat() - s, cy.toFloat() - s, cx.toFloat() + s, cy.toFloat() + s, p)
            c.drawLine(cx.toFloat() + s, cy.toFloat() - s, cx.toFloat() - s, cy.toFloat() + s, p)
        }
    }
}
