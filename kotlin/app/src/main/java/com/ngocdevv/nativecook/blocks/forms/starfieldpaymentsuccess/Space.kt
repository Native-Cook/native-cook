package com.ngocdevv.nativecook.blocks.forms.starfieldpaymentsuccess

import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal object SpaceMotion {
    const val LOOP = 11.0
    const val FIRST_KEY_AT = 0.8
    const val KEY_EVERY = 0.35
    const val KEY_FOR = 0.12
    const val PRESS_AT = 2.6
    const val PRESS_FOR = 0.15
    const val LEAVE_AFTER = 3.6
    const val USER_HOLD = 3.0
    const val DROP = 0.28
    const val DISSOLVE_AT = 0.06
    const val DISSOLVE_FOR = 0.45
    const val STARS_AT = 0.4
    const val STARS_IN = 0.6
    const val TEXT_AT = 0.5
    const val TEXT_IN = 0.35
    const val WAIT_OUT = 0.3
    const val CHECK_AFTER = 0.35
    const val BAR_AFTER = 0.55
    const val BAR_IN = 0.45
    const val RESULT_AFTER = 0.85 + 0.3 + 0.3
    const val RESULT_IN = 0.35
    const val RETURN = 0.6
    const val MARK_Y = 0.36
}

internal object SpaceMath {
    fun clamp01(value: Double) = min(1.0, max(0.0, value))
    fun smooth(value: Double, from: Double, to: Double): Double {
        val t = clamp01((value - from) / (to - from))
        return t * t * (3 - 2 * t)
    }
    fun backOut(value: Double): Double {
        val t = clamp01(value) - 1
        return 1 + 2.70158 * t * t * t + 1.70158 * t * t
    }
}

private object SpaceSpec {
    const val GLOW_AT = 0.75
    const val GLOW_IN = 0.4
    const val PLANE_AT = 1.0
    const val PLANE_IN = 0.25
    const val CHECK_IN = 0.5
    const val RING_FOR = 0.8
    const val SWELL_AFTER = 0.85
    const val SWELL_FOR = 0.3
    const val TURN_AFTER = SWELL_AFTER + SWELL_FOR
    const val TURN_EASE = 0.3
    const val SPIN_PERIOD = 1.3
    const val PLANE_Y = 0.398
    const val STAR_COUNT = 240
    const val SEED = 20261012L
    const val STAR_SPEED = 0.05
    const val STAR_RUSH = 0.3
    const val NEAR = 0.18
    const val STAR_HALO = 2.2
    const val REACH = 0.5
    const val GLOW = 27.0
    const val GLOW_REACH = 11.0
    const val CHECK = 87.0
    const val SWELL = 1.3
    const val COIN = 112.0
    const val COIN_EDGE = 14.0
    const val COIN_TILT = -0.14
    const val R = COIN / 2
    const val PLANE_SCALE = 1.45f
    const val DOLLAR_SCALE = 2.5f
    val LOBES = arrayOf(
        doubleArrayOf(0.4, 2.3, 0.0, 1.05), doubleArrayOf(2.0, 1.7, 1.9, 0.86),
        doubleArrayOf(3.5, 2.9, 3.4, 1.0), doubleArrayOf(5.2, 2.1, 5.0, 0.8),
    )
    val GREEN = doubleArrayOf(30.0, 224.0, 122.0)
    val GREEN_DARK = doubleArrayOf(18.0, 150.0, 79.0)
    val RING = 0xffe5432a.toInt()
    val PLANE_INK = 0xff1b1206.toInt()
    val CHECK_INK = 0xff06140c.toInt()
    val BLOOM = rgba(255, 78, 10, 0.9f).toArgb()
    val YELLOW = 0xffffc61c.toInt()
    val CORE = 0xfffff4ae.toInt()
    val COIN_GLOW = rgba(255, 178, 58, 0.14f).toArgb()
    val SHADE = 0xff1a0e00.toInt()
    val EDGE = intArrayOf(0xff6b4a12.toInt(), 0xfff6dc92.toInt(), 0xff8a6119.toInt(), 0xff2e1f06.toInt(), 0xff4a3209.toInt(), 0xffc9a046.toInt(), 0xff3a2707.toInt())
    val EDGE_AT = floatArrayOf(0f, 0.12f, 0.26f, 0.5f, 0.7f, 0.86f, 1f)
    val FACE = intArrayOf(0xfffcefc9.toInt(), 0xffe8c57f.toInt(), 0xffb98a35.toInt())
    val RIM = intArrayOf(0xffc7963c.toInt(), 0xfff3d894.toInt(), 0xfffbeaba.toInt())
    val BACK = intArrayOf(0xfff1d596.toInt(), 0xffd2a551.toInt())
    val GROOVE = rgba(150, 104, 30, 0.75f).toArgb()
    val INNER_RIM = rgba(255, 246, 216, 0.7f).toArgb()
    val DOLLAR_SHADOW = 0xff8f6420.toInt()
    val DOLLAR_LIGHT = 0xfffff7dc.toInt()
    val DOLLAR = 0xffd9b266.toInt()
    val SHEEN = intArrayOf(rgba(255, 255, 255, 0f).toArgb(), rgba(255, 252, 235, 0.6f).toArgb(), rgba(255, 255, 255, 0f).toArgb())
    val BLUR_SAMPLES = doubleArrayOf(-2.4, -1.6, -0.8, 0.0, 0.8, 1.6, 2.4)
    val BLUR_WEIGHTS = BLUR_SAMPLES.map { exp(-it * it / 2) }.let { weights -> weights.map { it / weights.sum() } }
}

private data class SpaceStar(val x: Double, val y: Double, val depth: Double, val bright: Double)
private data class SpaceBlurKey(val color: Int, val sigma: Float, val stroke: Float)

private class SpaceArt {
    val stars: List<SpaceStar> = run {
        var seed = SpaceSpec.SEED
        fun next(): Double {
            seed = (seed * 1664525L + 1013904223L) and 0xffffffffL
            return seed / 4294967296.0
        }
        buildList {
            while (size < SpaceSpec.STAR_COUNT) {
                val x = next() * 2 - 1
                val y = next() * 2 - 1
                val star = SpaceStar(x, y, next(), 0.55 + 0.45 * next())
                if (hypot(x, y) > 0.08) add(star)
            }
        }
    }
    private val plane = Path().apply {
        moveTo(22f, 2f); lineTo(14.6f, 21.6f); lineTo(11.2f, 12.8f); lineTo(2.4f, 9.4f); close()
    }
    private val streaks = Path().apply {
        moveTo(2.2f, 17.4f); lineTo(6.4f, 13.2f)
        moveTo(4.8f, 21f); lineTo(9.6f, 16.2f)
        moveTo(9f, 22.4f); lineTo(11.8f, 19.6f)
    }
    private val tick = Path().apply { moveTo(-15.2f, 1f); lineTo(-5.7f, 10.5f); lineTo(15.2f, -10.5f) }
    private val dollar = Path().apply {
        moveTo(12f, 1f); lineTo(12f, 23f); moveTo(17f, 5f); lineTo(9.5f, 5f)
        arcTo(RectF(6f, 5f, 13f, 12f), -90f, -180f, false)
        lineTo(14.5f, 12f); arcTo(RectF(11f, 12f, 18f, 19f), -90f, 180f, false); lineTo(6f, 19f)
    }
    private val faceClip = Path().apply { addCircle(0f, 0f, SpaceSpec.R.toFloat(), Path.Direction.CW) }
    private val edge = LinearGradient(0f, -56f, 0f, 56f, SpaceSpec.EDGE, SpaceSpec.EDGE_AT, Shader.TileMode.CLAMP)
    private val face = diagonal(SpaceSpec.FACE)
    private val rim = diagonal(SpaceSpec.RIM)
    private val back = diagonal(SpaceSpec.BACK)
    private val sheen = LinearGradient(-28f, 0f, 28f, 0f, SpaceSpec.SHEEN, null, Shader.TileMode.CLAMP)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val blurPaints = mutableMapOf<SpaceBlurKey, Paint>()
    private val layerPaint = Paint()
    private val samplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }

    private fun diagonal(colors: IntArray) = LinearGradient(-56f, -56f, 56f, 56f, colors, null, Shader.TileMode.CLAMP)
    private fun solid(color: Int, opacity: Double = 1.0, stroke: Float = 0f): Paint {
        paint.shader = null; paint.color = color
        paint.alpha = (NativeColor.alpha(color) * opacity).roundToInt().coerceIn(0, 255)
        paint.style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL; paint.strokeWidth = stroke
        return paint
    }
    private fun gradient(shader: Shader, opacity: Double, stroke: Float = 0f): Paint {
        paint.color = NativeColor.WHITE; paint.shader = shader
        paint.alpha = (255 * opacity).roundToInt().coerceIn(0, 255)
        paint.style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL; paint.strokeWidth = stroke
        return paint
    }
    private fun blurred(canvas: NativeCanvas, radius: Double, color: Int, sigma: Double, x: Double = 0.0, y: Double = 0.0, opacity: Double = 1.0, stroke: Float = 0f) {
        if (opacity <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val p = blurPaints.getOrPut(SpaceBlurKey(color, sigma.toFloat(), stroke)) {
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color; style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL; strokeWidth = stroke
                    maskFilter = BlurMaskFilter(sigmaToRadius(sigma.toFloat()), BlurMaskFilter.Blur.NORMAL)
                }
            }
            p.alpha = (NativeColor.alpha(color) * opacity).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), p)
        } else {
            // Old hardware canvases ignore mask filters; integrate a bounded Gaussian coverage.
            layerPaint.alpha = (NativeColor.alpha(color) * opacity).roundToInt().coerceIn(0, 255)
            val reach = radius + stroke / 2 + SpaceSpec.BLUR_SAMPLES.last() * sigma + 2
            val saved = canvas.saveLayer((x - reach).toFloat(), (y - reach).toFloat(), (x + reach).toFloat(), (y + reach).toFloat(), layerPaint)
            samplePaint.color = color or 0xff000000.toInt()
            samplePaint.style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL; samplePaint.strokeWidth = stroke
            SpaceSpec.BLUR_SAMPLES.forEachIndexed { ix, dx ->
                SpaceSpec.BLUR_SAMPLES.forEachIndexed { iy, dy ->
                    samplePaint.alpha = (255 * SpaceSpec.BLUR_WEIGHTS[ix] * SpaceSpec.BLUR_WEIGHTS[iy]).roundToInt()
                    canvas.drawCircle((x + dx * sigma).toFloat(), (y + dy * sigma).toFloat(), radius.toFloat(), samplePaint)
                }
            }
            canvas.restoreToCount(saved)
        }
    }

    fun draw(canvas: NativeCanvas, elapsed: Double, duration: Double, still: Boolean, width: Float, height: Float) {
        val check = duration + SpaceMotion.CHECK_AFTER
        val turn = check + SpaceSpec.TURN_AFTER
        stars(canvas, elapsed, check, still, width, height)
        glow(canvas, elapsed, duration, still, width, height)
        rings(canvas, elapsed, check, still, width, height)
        val angle = if (still) 0.0 else turned(elapsed - turn)
        val swap = if (still) SpaceMath.smooth(elapsed, turn, turn + 0.3) else if (angle >= PI / 2) 1.0 else 0.0
        disc(canvas, elapsed, check, turn, angle, swap, still, width, height)
        if (swap > 0) coin(canvas, if (still) 0.0 else angle - PI, swap, width, height)
    }
    private fun turned(time: Double): Double {
        val speed = 2 * PI / SpaceSpec.SPIN_PERIOD
        if (time <= 0) return 0.0
        return if (time < SpaceSpec.TURN_EASE) speed * time * time / (2 * SpaceSpec.TURN_EASE) else speed * (time - SpaceSpec.TURN_EASE / 2)
    }
    private fun stars(canvas: NativeCanvas, elapsed: Double, check: Double, still: Boolean, width: Float, height: Float) {
        val shown = SpaceMath.smooth(elapsed, SpaceMotion.STARS_AT, SpaceMotion.STARS_AT + SpaceMotion.STARS_IN)
        if (shown <= 0) return
        val travel = if (still) 0.0 else SpaceSpec.STAR_SPEED * elapsed + SpaceSpec.STAR_RUSH * SpaceMath.smooth(elapsed, check - 0.1, check + 0.9)
        val reach = SpaceSpec.REACH * height
        stars.forEach { star ->
            var z = (star.depth - travel) % 1
            if (z <= 0) z += 1
            val scale = 1 / (SpaceSpec.NEAR + (1 - SpaceSpec.NEAR) * z)
            val x = width / 2 + star.x * reach * scale
            val y = height * SpaceMotion.MARK_Y + star.y * reach * scale
            if (x < -6 || x > width + 6 || y < -6 || y > height + 6) return@forEach
            val near = 1 - z
            val radius = 0.65 + 1.7 * near * near * near
            val alpha = shown * star.bright * (0.5 + 0.5 * near) * SpaceMath.smooth(z, 1.0, 0.9) * SpaceMath.smooth(z, 0.0, 0.06)
            if (radius > 1.2) blurred(canvas, radius * 1.8, NativeColor.WHITE, SpaceSpec.STAR_HALO, x, y, alpha * 0.5)
            canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), solid(NativeColor.WHITE, alpha))
        }
    }
    private fun glow(canvas: NativeCanvas, elapsed: Double, duration: Double, still: Boolean, width: Float, height: Float) {
        val grown = 0.5 + 0.5 * SpaceMath.smooth(elapsed, SpaceSpec.GLOW_AT, SpaceSpec.GLOW_AT + SpaceSpec.GLOW_IN)
        val opacity = SpaceMath.smooth(elapsed, SpaceSpec.GLOW_AT, SpaceSpec.GLOW_AT + SpaceSpec.GLOW_IN) * (1 - SpaceMath.smooth(elapsed, duration, duration + SpaceMotion.WAIT_OUT))
        if (opacity <= 0) return
        val breath = if (still) 1.0 else 1 + 0.05 * sin(elapsed * 3.3)
        val bob = if (still) 0.0 else 2.5 * sin(elapsed * 2.1)
        val saved = canvas.save()
        canvas.translate(width / 2, (height * SpaceSpec.PLANE_Y + bob).toFloat())
        canvas.scale((grown * breath).toFloat(), (grown * breath).toFloat())
        val time = if (still) 0.0 else elapsed
        for (bloom in listOf(true, false)) {
            SpaceSpec.LOBES.forEach { lobe ->
                val angle = lobe[0] + 0.4 * sin(time * lobe[1] + lobe[2])
                val distance = SpaceSpec.GLOW_REACH * (1 + 0.35 * sin(time * lobe[1] * 1.4 + lobe[2] * 2))
                blurred(canvas, (if (bloom) SpaceSpec.GLOW + 4 else SpaceSpec.GLOW) * lobe[3], if (bloom) SpaceSpec.BLOOM else SpaceSpec.YELLOW, if (bloom) 7.0 else 2.5, cos(angle) * distance * 1.25, sin(angle) * distance, opacity)
            }
        }
        blurred(canvas, 20.0, SpaceSpec.CORE, 9.0, opacity = opacity)
        val planeOpacity = opacity * SpaceMath.smooth(elapsed, SpaceSpec.PLANE_AT, SpaceSpec.PLANE_AT + SpaceSpec.PLANE_IN)
        canvas.rotate((if (still) 0.0 else 0.05 * sin(elapsed * 4.6) * 180 / PI).toFloat())
        canvas.scale(SpaceSpec.PLANE_SCALE, SpaceSpec.PLANE_SCALE); canvas.translate(-12f, -12f)
        canvas.drawPath(plane, solid(SpaceSpec.PLANE_INK, planeOpacity))
        canvas.drawPath(streaks, solid(SpaceSpec.PLANE_INK, planeOpacity, 1.3f))
        canvas.restoreToCount(saved)
    }
    private fun rings(canvas: NativeCanvas, elapsed: Double, check: Double, still: Boolean, width: Float, height: Float) {
        val p = SpaceMath.clamp01((elapsed - check) / SpaceSpec.RING_FOR)
        if (still || p <= 0 || p >= 1) return
        val u = 1 - p
        val radius = 16 + 0.42 * height * (1 - u * u * u)
        val opacity = 0.6 * (1 - p)
        blurred(canvas, radius * 2.2, SpaceSpec.RING, 1.5, width / 2.0, height * SpaceMotion.MARK_Y, opacity * 0.45, 1.5f)
        blurred(canvas, radius, SpaceSpec.RING, 1.5, width / 2.0, height * SpaceMotion.MARK_Y, opacity, 1.5f)
    }
    private fun disc(canvas: NativeCanvas, elapsed: Double, check: Double, turn: Double, angle: Double, swap: Double, still: Boolean, width: Float, height: Float) {
        val opacity = SpaceMath.smooth(elapsed, check, check + 0.2) * (1 - swap)
        if (opacity <= 0) return
        val dark = SpaceMath.smooth(elapsed, check + SpaceSpec.SWELL_AFTER, turn)
        val scale = (if (still) 1.0 else SpaceMath.backOut((elapsed - check) / SpaceSpec.CHECK_IN)) * (1 + (SpaceSpec.SWELL - 1) * dark)
        fun channel(i: Int) = (SpaceSpec.GREEN[i] + (SpaceSpec.GREEN_DARK[i] - SpaceSpec.GREEN[i]) * dark).roundToInt()
        val saved = canvas.save()
        canvas.translate(width / 2, (height * SpaceMotion.MARK_Y).toFloat())
        canvas.scale(max(0.001, cos(min(angle, PI / 2))).toFloat(), 1f); canvas.scale(scale.toFloat(), scale.toFloat())
        canvas.drawCircle(0f, 0f, (SpaceSpec.CHECK / 2).toFloat(), solid(NativeColor.rgb(channel(0), channel(1), channel(2)), opacity))
        canvas.drawPath(tick, solid(SpaceSpec.CHECK_INK, opacity, 5f))
        canvas.restoreToCount(saved)
    }
    private fun face(canvas: NativeCanvas, front: Boolean, opacity: Double) {
        val r = SpaceSpec.R.toFloat()
        canvas.drawCircle(0f, 0f, r, gradient(face, opacity))
        canvas.drawCircle(0f, 0f, r - 2.5f, gradient(rim, opacity, 5f))
        canvas.drawCircle(0f, 0f, r - 9f, solid(SpaceSpec.GROOVE, opacity, 1.4f))
        canvas.drawCircle(0f, 0f, r - 10.3f, solid(SpaceSpec.INNER_RIM, opacity, 1f))
        if (front) {
            val sign = canvas.save()
            canvas.scale(SpaceSpec.DOLLAR_SCALE, SpaceSpec.DOLLAR_SCALE); canvas.translate(-12f, -12f)
            val shadow = canvas.save(); canvas.translate(0.45f, 0.5f)
            canvas.drawPath(dollar, solid(SpaceSpec.DOLLAR_SHADOW, opacity, 2.5f)); canvas.restoreToCount(shadow)
            val light = canvas.save(); canvas.translate(-0.35f, -0.35f)
            canvas.drawPath(dollar, solid(SpaceSpec.DOLLAR_LIGHT, opacity, 2.5f)); canvas.restoreToCount(light)
            canvas.drawPath(dollar, solid(SpaceSpec.DOLLAR, opacity, 2.4f))
            canvas.restoreToCount(sign)
        } else canvas.drawCircle(0f, 0f, r - 19f, gradient(back, opacity))
    }
    private fun coin(canvas: NativeCanvas, angle: Double, opacity: Double, width: Float, height: Float) {
        val r = SpaceSpec.R.toFloat()
        val c = cos(angle)
        val sine = sin(angle)
        val scale = max(0.001, abs(c)).toFloat()
        val saved = canvas.save()
        canvas.translate(width / 2, (height * SpaceMotion.MARK_Y).toFloat()); canvas.rotate((SpaceSpec.COIN_TILT * 180 / PI).toFloat())
        blurred(canvas, SpaceSpec.R * 1.5, SpaceSpec.COIN_GLOW, 30.0, opacity = opacity)
        val behind = canvas.save()
        canvas.translate(((if (c >= 0) -1 else 1) * SpaceSpec.COIN_EDGE / 2 * sine).toFloat(), 0f); canvas.scale(scale, 1f)
        canvas.drawCircle(0f, 0f, r, gradient(edge, opacity)); canvas.restoreToCount(behind)
        val edgeWidth = (SpaceSpec.COIN_EDGE * abs(sine)).toFloat()
        canvas.drawRect(-edgeWidth / 2, -r, edgeWidth / 2, r, gradient(edge, opacity))
        canvas.translate(((if (c >= 0) 1 else -1) * SpaceSpec.COIN_EDGE / 2 * sine).toFloat(), 0f); canvas.scale(scale, 1f)
        canvas.clipPath(faceClip); face(canvas, c >= 0, opacity)
        canvas.drawCircle(0f, 0f, r, solid(SpaceSpec.SHADE, opacity * 0.66 * (1 - abs(c)).pow(0.8)))
        canvas.translate((1.5 * SpaceSpec.R * sine).toFloat(), 0f); canvas.rotate((0.35 * 180 / PI).toFloat())
        canvas.drawRect(-0.5f * r, -1.5f * r, 0.5f * r, 1.5f * r, gradient(sheen, opacity))
        canvas.restoreToCount(saved)
    }
}

/** The seeded sky and projected coin follow the same payment clock as the form. */
@Composable
fun Space(elapsed: Double, duration: Double, still: Boolean, width: Float, height: Float, modifier: Modifier = Modifier) {
    val art = remember { SpaceArt() }
    Canvas(modifier.size(width.coerceAtLeast(0f).dp, height.coerceAtLeast(0f).dp).clipToBounds().clearAndSetSemantics {}) {
        if (width > 0 && height > 0 && width.isFinite() && height.isFinite()) {
            surface(0.dp) { _, _, _, _ -> art.draw(drawContext.canvas.nativeCanvas, elapsed, duration, still, width, height) }
        }
    }
}

