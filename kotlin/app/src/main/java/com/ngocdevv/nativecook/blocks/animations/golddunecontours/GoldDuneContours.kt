package com.ngocdevv.nativecook.blocks.animations.golddunecontours

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The hidden-line terrain and its travelling light repeat exactly every eight seconds. */
const val LOOP = 8.0

private const val NIGHT = 0x0A0806
private const val GROUND = 0x120D09
private const val CREAM = 0xFFE9C4
private const val GOLD = 0xE8B36A
private const val GLOW = 0xFFC978

/** A dune field whose contours rise under a finger; all frames derive from absolute loop time. */
@Composable
fun GoldDuneContours(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val screen = LocalDensity.current
    val view = LocalView.current
    val safeTop = WindowInsets.safeDrawing.getTop(screen) / screen.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(screen) / screen.density
    val top = max(0f, safeTop - 59f)
    val bottom = max(0f, safeBottom - 34f)
    val interaction = remember { GoldDuneContoursInteraction() }
    val renderer = remember { GoldDuneContoursRenderer() }
    val clock = remember { GoldDuneContoursClock() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }

    LaunchedEffect(autoPlay, reduced, wake.intValue) {
        do {
            withFrameNanos { nanos ->
                clock.now = nanos / 1e9
                val nextPlaying = interaction.isPlaying(clock.now)
                if (playing != nextPlaying) playing = nextPlaying
                tick.intValue++
            }
            val last = interaction.lastTouch
            val settling = interaction.dragging || (last != null && clock.now - last < 3.0)
        } while ((autoPlay && !reduced) || settling)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF0A0806)).clipToBounds()) {
        val sx = maxWidth.value / 393f
        val sy = maxHeight.value / 852f
        val cardTop = 716f - bottom / sy
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "Dune contours. Drag to raise a ridge in the sand." }
                .pointerInput(sx, sy, cardTop, autoPlay, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val x = down.position.x / (density * sx)
                        val y = down.position.y / (density * sy)
                        if (y <= 300f || (x in 20f..373f && y in cardTop..(cardTop + 84f))) {
                            return@awaitEachGesture
                        }
                        interaction.begin(goldDuneNow(), clock.epoch, autoPlay, reduced, x.toDouble(), y.toDouble())
                        wake.intValue++
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        down.consume()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val now = goldDuneNow()
                                val damping = if (reduced) 1.0 else 0.72
                                interaction.x.retarget(change.position.x / (density * sx).toDouble(), now, 0.30, damping)
                                interaction.y.retarget(change.position.y / (density * sy).toDouble(), now, 0.30, damping)
                                change.consume()
                            }
                        } finally {
                            interaction.release(goldDuneNow(), reduced)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                },
        ) {
            // The frame tick is read only in draw, never in the composition's layout or text.
            tick.intValue
            val now = clock.now
            val time = if (reduced) 0.0 else interaction.time(now, clock.epoch, autoPlay)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(size.width / 393f, size.height / 852f)
                renderer.draw(
                    native, time, reduced, interaction.ridge.value(now), interaction.x.value(now),
                    interaction.y.value(now), interaction.lightBlend(now), interaction.isPlaying(now),
                    top / sy, bottom / sy,
                )
                native.restore()
            }
        }
        BasicText(
            "TONIGHT · 23:40",
            Modifier.offset((28f * sx).dp, (76f * sy + top).dp),
            style = goldDuneText(12f, 0.62f, FontWeight.Medium).copy(letterSpacing = 1.6.sp),
        )
        BasicText(
            "Dune Wind",
            Modifier.offset((28f * sx).dp, (104f * sy + top).dp),
            style = goldDuneText(40f).copy(fontFamily = FontFamily.Serif),
        )
        BasicText(
            "Low wind over warm sand, recorded at dusk.",
            Modifier.offset((28f * sx).dp, (158f * sy + top).dp).width((250f * sx).dp),
            style = goldDuneText(15f, 0.62f),
        )
        BasicText(
            "Dune Wind",
            Modifier.offset((104f * sx).dp, (738f * sy - bottom).dp),
            style = goldDuneText(16f, weight = FontWeight.SemiBold),
        )
        BasicText(
            "45 min · fades out",
            Modifier.offset((104f * sx).dp, (760f * sy - bottom).dp),
            style = goldDuneText(13f, 0.62f),
        )
        Box(
            Modifier.offset((36f * sx).dp, (732f * sy - bottom).dp)
                .size((52f * sx).dp, (52f * sy).dp)
                .semantics { contentDescription = if (playing) "Pause Dune Wind" else "Play Dune Wind" }
                .clickable(role = Role.Button) {
                    interaction.toggle(goldDuneNow(), clock.epoch, autoPlay, reduced)
                    playing = interaction.isPlaying(goldDuneNow())
                    wake.intValue++
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                },
        )
    }
}

private fun goldDuneText(size: Float, alpha: Float = 1f, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = Color(0xFFFFF3DC).copy(alpha = alpha), fontSize = size.sp, fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun goldDuneNow() = System.nanoTime() / 1e9

private class GoldDuneContoursClock {
    val epoch = goldDuneNow()
    var now = epoch
}

/** Analytic springs preserve velocity when a touch changes their target. */
private class GoldDuneContoursSpring(
    var from: Double = 0.0,
    var velocity: Double = 0.0,
    var target: Double = 0.0,
    var stamp: Double = 0.0,
    var response: Double = 0.3,
    var damping: Double = 0.72,
) {
    fun sample(now: Double): Pair<Double, Double> {
        val t = max(0.0, now - stamp)
        val w = 2 * PI / response
        val d = from - target
        if (t > 3) return target to 0.0
        if (damping >= 1) {
            val b = velocity + w * d
            val decay = exp(-w * t)
            return target + (d + b * t) * decay to (b - w * (d + b * t)) * decay
        }
        val a = damping * w
        val wd = w * sqrt(1 - damping * damping)
        val b = (velocity + a * d) / wd
        val decay = exp(-a * t)
        val wave = d * cos(wd * t) + b * sin(wd * t)
        return target + decay * wave to decay * (-a * wave - d * wd * sin(wd * t) + b * wd * cos(wd * t))
    }

    fun value(now: Double) = sample(now).first

    fun retarget(value: Double, now: Double, response: Double, damping: Double) {
        val current = sample(now)
        from = current.first
        velocity = current.second
        target = value
        stamp = now
        this.response = response
        this.damping = damping
    }
}

private class GoldDuneContoursInteraction {
    var lastTouch: Double? = null
    var dragging = false
    var paused = false
    var heldByTerrain = false
    var held = 0.0
    var x = GoldDuneContoursSpring(from = 196.5, target = 196.5)
    var y = GoldDuneContoursSpring(from = 500.0, target = 500.0)
    val ridge = GoldDuneContoursSpring()

    fun isPlaying(now: Double) = !paused || (!dragging && now - (lastTouch ?: now) >= 2)

    fun lightBlend(now: Double): Double {
        if (dragging) return 1.0
        val last = lastTouch
        if (heldByTerrain && last != null) {
            val u = ((now - last) / 0.65).coerceIn(0.0, 1.0)
            return 1 - u * u * (3 - 2 * u)
        }
        return 0.0
    }

    fun time(now: Double, epoch: Double, autoPlay: Boolean): Double {
        val last = lastTouch ?: return if (autoPlay) max(0.0, now - epoch) % LOOP else 0.0
        if (dragging) return held
        val elapsed = max(0.0, now - last)
        if (elapsed < 2) return if (paused || heldByTerrain) held else (held + elapsed) % LOOP
        val start = if (paused || heldByTerrain) held else (held + 2) % LOOP
        if (elapsed < 2.8) {
            val u = (elapsed - 2) / 0.8
            val ease = u * u * (3 - 2 * u)
            return start + (LOOP - start) * ease
        }
        return if (autoPlay) (elapsed - 2.8) % LOOP else 0.0
    }

    fun begin(now: Double, epoch: Double, autoPlay: Boolean, reduced: Boolean, px: Double, py: Double) {
        held = if (reduced) 0.0 else time(now, epoch, autoPlay)
        paused = !isPlaying(now)
        dragging = true
        heldByTerrain = true
        lastTouch = now
        x = GoldDuneContoursSpring(from = px, target = px)
        y = GoldDuneContoursSpring(from = py, target = py)
        ridge.retarget(1.0, now, 0.35, if (reduced) 1.0 else 0.70)
    }

    fun release(now: Double, reduced: Boolean) {
        dragging = false
        lastTouch = now
        val px = x.value(now)
        val py = y.value(now)
        x = GoldDuneContoursSpring(from = px, target = px)
        y = GoldDuneContoursSpring(from = py, target = py)
        ridge.retarget(0.0, now, 0.65, if (reduced) 1.0 else 0.55)
        if (reduced) ridge.velocity = 0.0
    }

    fun toggle(now: Double, epoch: Double, autoPlay: Boolean, reduced: Boolean) {
        val wasPlaying = isPlaying(now)
        held = if (reduced) 0.0 else time(now, epoch, autoPlay)
        paused = wasPlaying
        heldByTerrain = false
        lastTouch = now
    }
}

/** Native paths and paints are reused; there is no per-line composable or per-frame layout. */
private class GoldDuneContoursRenderer {
    private val line = Path()
    private val fillPath = Path()
    private val glyph = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glow6 = Paint(stroke).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(6f), BlurMaskFilter.Blur.NORMAL)
    }
    private val glow12 = Paint(stroke).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(12f), BlurMaskFilter.Blur.NORMAL)
    }
    private val stops = FloatArray(6)
    private val colors = IntArray(6)
    private val background = LinearGradient(0f, 0f, 0f, 852f, goldDuneArgb(NIGHT), goldDuneArgb(GROUND), Shader.TileMode.CLAMP)
    private val fog = LinearGradient(0f, 702f, 0f, 852f, goldDuneArgb(GROUND, 0.0), goldDuneArgb(GROUND, 0.7), Shader.TileMode.CLAMP)
    private val moon = RadialGradient(268f, 318f, 34f, goldDuneArgb(0xFFF6E0, 0.92), goldDuneArgb(0xFFD9A0, 0.92), Shader.TileMode.CLAMP)

    private fun solid(rgb: Int, alpha: Double = 1.0): Paint = fill.apply {
        shader = null
        color = goldDuneArgb(rgb, alpha)
    }

    private fun shaded(shader: Shader): Paint = fill.apply {
        color = android.graphics.Color.BLACK
        this.shader = shader
    }

    fun draw(
        canvas: android.graphics.Canvas, time: Double, reduced: Boolean, ridge: Double,
        fingerX: Double, fingerY: Double, lightBlend: Double, playing: Boolean,
        topShift: Float, bottomShift: Float,
    ) {
        val t = time % LOOP
        val tau = 2 * PI * t / LOOP
        canvas.drawRect(0f, 0f, 393f, 852f, shaded(background))
        val radius = if (reduced) 190.0 else 190 + 16 * sin(tau / 2).pow(2)
        val halo = RadialGradient(268f, 318f, radius.toFloat(), goldDuneArgb(0xFFB45E, 0.34), goldDuneArgb(0xFFB45E, 0.0), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 393f, 852f, shaded(halo))
        canvas.drawCircle(268f, 318f, 34f, shaded(moon))
        for (k in 0 until 26) {
            val seed = goldDuneHash(k + 1.0)
            val age = (seed + if (reduced) 0.0 else t / LOOP) % 1
            val x = (goldDuneHash(k + 31.0) * 393 + age * 18) % 393
            val y = 90 + goldDuneHash(k + 71.0) * 330 - age * 8
            val moteRadius = 0.6 + 0.8 * goldDuneHash(k + 111.0)
            val opacity = (0.1 + 0.28 * seed) * (0.5 + 0.5 * sin(2 * tau + seed * 2 * PI)) * sin(PI * age).pow(2)
            canvas.drawCircle(x.toFloat(), y.toFloat(), moteRadius.toFloat(), solid(CREAM, opacity))
        }
        for (i in 0 until 46) {
            val v = i / 45.0
            val baseline = 352 + 548 * v.pow(1.55)
            val amplitude = 22 + 150 * v.pow(1.3)
            line.rewind()
            for (sample in 0..74) {
                val x = min(417.0, -24 + sample * 6.0)
                val u = x / 393
                // A soft-cusped crest gives the dunes a wind-cut ridge instead of a round swell.
                val crest = 1 - 2 * sqrt(sin(PI * (0.8 * u + 0.9 * v) + tau / 2).pow(2) + 0.02)
                val h = 0.52 * crest +
                    0.30 * sin(2 * PI * (1.6 * u - 1.05 * v) - tau + 1.3) +
                    0.18 * sin(2 * PI * (2.9 * u + 2.2 * v) + 2 * tau + 0.4)
                val lift = ridge * 78 * (0.35 + 0.65 * v) * exp(-((x - fingerX) / 86).pow(2)) * exp(-((baseline - fingerY) / 110).pow(2))
                val y = baseline - amplitude * (0.5 + 0.5 * h) - lift
                if (sample == 0) line.moveTo(x.toFloat(), y.toFloat()) else line.lineTo(x.toFloat(), y.toFloat())
            }
            fillPath.set(line)
            fillPath.lineTo(417f, 960f)
            fillPath.lineTo(-24f, 960f)
            fillPath.close()
            val ground = goldDuneRgb(10 + 8 * v, 8 + 5 * v, 6 + 3 * v)
            canvas.drawPath(fillPath, solid(ground))
            val automatic = if (reduced) 0.55 else max(0.0, sin(PI * t / LOOP)).pow(0.6)
            val mix = ridge.coerceIn(0.0, 1.0)
            val autoCenter = if (reduced) 0.62 else -0.45 + 1.9 * t / LOOP + 0.22 * v
            val center = autoCenter * (1 - lightBlend) + fingerX / 393 * lightBlend
            val strength = automatic * (1 - lightBlend) + mix * lightBlend
            if (i % 3 == 0) {
                val nearLight = max(0.0, 1 - abs(center - 0.5) / 0.76) * strength
                glow(canvas, line, glow6, 6f, (3 + 3 * v).toFloat(), (0.16 + 0.30 * v) * (0.6 + 0.4 * nearLight))
            }
            if (i >= 40) glow(canvas, line, glow12, 12f, 6f, 0.16)
            stops[0] = 0f
            stops[1] = 0.5f
            stops[2] = 1f
            stops[3] = (center - 0.26).coerceIn(0.0, 1.0).toFloat()
            stops[4] = center.coerceIn(0.0, 1.0).toFloat()
            stops[5] = (center + 0.26).coerceIn(0.0, 1.0).toFloat()
            stops.sort()
            for (j in stops.indices) {
                val u = stops[j].toDouble()
                val f = if (u < 0.5) u * 2 else (u - 0.5) * 2
                val a = if (u < 0.5) CREAM else GOLD
                val b = if (u < 0.5) GOLD else 0xC98A6B
                val light = max(0.0, 1 - abs(u - center) / 0.26) * strength
                val red = goldDuneChannel(a, b, f, 16)
                val green = goldDuneChannel(a, b, f, 8)
                val blue = goldDuneChannel(a, b, f, 0)
                colors[j] = goldDuneArgb(
                    goldDuneRgb(red + (255 - red) * light, green + (246 - green) * light, blue + (224 - blue) * light),
                    (0.30 + 0.65 * v) + (1 - (0.30 + 0.65 * v)) * light,
                )
            }
            stroke.color = android.graphics.Color.BLACK
            stroke.shader = LinearGradient(0f, 0f, 393f, 0f, colors, stops, Shader.TileMode.CLAMP)
            stroke.strokeWidth = (0.6 + 0.9 * v).toFloat()
            canvas.drawPath(line, stroke)
        }
        canvas.drawRect(0f, 702f, 393f, 852f, shaded(fog))
        drawChrome(canvas, t, reduced, playing, topShift, bottomShift)
    }

    private fun glow(canvas: android.graphics.Canvas, path: Path, paint: Paint, blur: Float, width: Float, alpha: Double) {
        paint.shader = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paint.strokeWidth = width
            paint.color = goldDuneArgb(GLOW, alpha)
            canvas.drawPath(path, paint)
        } else {
            // Hardware mask blur is unavailable on API 26–27: soft concentric strokes.
            for (j in 4 downTo 1) {
                paint.strokeWidth = width + blur * j / 2
                paint.color = goldDuneArgb(GLOW, alpha * (5 - j) / 30)
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun drawChrome(canvas: android.graphics.Canvas, time: Double, reduced: Boolean, playing: Boolean, top: Float, bottom: Float) {
        canvas.drawCircle(345f, 84f + top, 20f, solid(0xFFFFFF, 0.07))
        stroke.shader = null
        stroke.strokeWidth = 1f
        stroke.color = goldDuneArgb(CREAM, 0.22)
        canvas.drawCircle(345f, 84f + top, 20f, stroke)
        glyph.rewind()
        glyph.addCircle(345f, 84f + top, 8f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(glyph)
        glyph.addCircle(350f, 81f + top, 8f, Path.Direction.CW)
        glyph.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(glyph, solid(CREAM))
        canvas.restore()
        glyph.fillType = Path.FillType.WINDING

        val y = 716f - bottom
        canvas.drawRoundRect(20f, y, 373f, y + 84f, 28f, 28f, solid(GROUND, 0.78))
        canvas.drawRoundRect(20f, y, 373f, y + 84f, 28f, 28f, solid(0xFFFFFF, 0.07))
        stroke.color = goldDuneArgb(CREAM, 0.22)
        canvas.drawRoundRect(20f, y, 373f, y + 84f, 28f, 28f, stroke)
        stroke.color = android.graphics.Color.BLACK
        stroke.shader = LinearGradient(20f, y, 20f, y + 26f, goldDuneArgb(CREAM, 0.10), goldDuneArgb(CREAM, 0.0), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(20.5f, y + 0.5f, 372.5f, y + 83.5f, 27.5f, 27.5f, stroke)
        val button = LinearGradient(36f, y + 16f, 88f, y + 68f, goldDuneArgb(CREAM), goldDuneArgb(GOLD), Shader.TileMode.CLAMP)
        canvas.drawCircle(62f, y + 42f, 26f, shaded(button))
        if (playing) {
            canvas.drawRoundRect(56f, y + 34f, 60f, y + 50f, 2f, 2f, solid(0x1A120A))
            canvas.drawRoundRect(64f, y + 34f, 68f, y + 50f, 2f, 2f, solid(0x1A120A))
        } else {
            glyph.rewind()
            glyph.moveTo(57f, y + 33f)
            glyph.lineTo(71f, y + 42f)
            glyph.lineTo(57f, y + 51f)
            glyph.close()
            canvas.drawPath(glyph, solid(0x1A120A))
        }
        for (k in 0 until 5) {
            val h = if (playing && !reduced) 8 + 14 * (0.5 + 0.5 * sin(4 * PI * time / LOOP + k * 1.1)) else 8.0
            val x = 322f + k * 6
            canvas.drawRoundRect(x, y + 42f - h.toFloat() / 2, x + 3f, y + 42f + h.toFloat() / 2, 1.5f, 1.5f, solid(CREAM, 0.8))
        }
    }
}

private fun goldDuneArgb(rgb: Int, alpha: Double = 1.0) = ((alpha.coerceIn(0.0, 1.0) * 255).toInt() shl 24) or (rgb and 0xFFFFFF)
private fun goldDuneRgb(r: Double, g: Double, b: Double) = (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
private fun goldDuneChannel(a: Int, b: Int, f: Double, shift: Int): Double {
    val start = (a shr shift) and 0xFF
    return start + (((b shr shift) and 0xFF) - start) * f
}
private fun goldDuneHash(seed: Double): Double {
    val n = sin(seed * 12.9898) * 43758.5453
    return n - floor(n)
}
