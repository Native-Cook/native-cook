package com.ngocdevv.nativecook.blocks.animations.orbringmorph

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** autoPlay: one run every LOOP seconds; a run by hand lasts as long. */
const val LOOP = 8f

// After a touch autoPlay waits this long, and for the run to finish, before it starts over.
private const val USER_HOLD = 3f
// Holding a finger down this long freezes the sequence until it is lifted.
private const val HOLD_AFTER_MS = 300L

// The timeline, in seconds from the start of a run. The orb rests still until PULSE_AT.
private const val PULSE_AT = 0.8f
private const val PULSE_FOR = 0.8f
private const val TEXT_IN_AT = 1.0f
private const val TEXT_IN_FOR = 0.5f
private const val SHRINK_AT = 1.6f
private const val SHRINK_FOR = 0.6f
private const val SPIN_AT = 1.6f
private const val SPIN_FOR = 0.9f
private const val HALO_AT = 2.0f
private const val HALO_FOR = 0.8f
private const val GROW_AT = 2.2f
private const val GLASS_FOR = 0.6f
private const val DOTS_AT = 2.8f
private const val DOT_STAGGER = 0.07f
private const val DOT_FLOAT = 1.4f
private const val RING_AT = 3.0f
private const val RING_STAGGER = 0.08f
private const val RING_DRAW = 0.7f
// Seconds for one turn of the ring, and for one breath of its arcs.
private const val RING_TURN = 4f
private const val BREATH = 1.6f
private const val CONTRACT_AT = 4.6f
private const val CONTRACT_FOR = 0.7f
private const val TEXT_OUT_AT = 4.6f
private const val TEXT_OUT_FOR = 0.4f
private const val DOTS_OUT_AT = 4.6f
private const val DOTS_OUT_FOR = 0.4f
// The pill that the ring becomes hands over to the card over SWAP seconds before CARD_AT.
private const val SWAP = 0.15f
private const val CARD_AT = 5.3f
private const val TONE_FOR = 0.5f
private const val ICON_AT = 5.55f
private const val ICON_FOR = 0.5f
private const val ICON_DOT_AT = 5.95f
private const val CARD_OUT_AT = 6.4f
private const val CARD_OUT_FOR = 0.6f
private const val ORB_IN_AT = 7.2f
private const val ORB_IN_FOR = 0.8f

// One spring for the sphere's growth, the card's opening and the icon's dot.
private const val SPRING_MASS = 1f
private const val SPRING_STIFFNESS = 170f
private const val SPRING_DAMPING = 16f

// Sizes in dp. The orb's centre sits at ORB_Y of the screen's height, the text's at TEXT_Y.
private const val ORB_Y = 0.45f
private const val TEXT_Y = 0.78f
private const val ORB = 56f
private const val ORB_SMALL = 28f
private const val SPHERE = 120f
private const val PULSE = 0.15f
// The orb is two half discs, slid along their seam by this share of the radius.
private const val SPLIT = 0.1f
private const val SEAM = 80f
private const val HALO = 110f
private const val RING_RADIUS = 84f
private const val RING_WIDTH = 6f
private const val ARC = 70f
private const val BREATH_AMOUNT = 0.15f
// The ring flattens into a vertical pill PILL_WIDTH wide and 2 × PILL_HALF + PILL_WIDTH tall.
private const val PILL_HALF = 45f
private const val PILL_WIDTH = 8f
private const val CARD = 140f
private const val CARD_RADIUS = 28f
private const val ICON_RADIUS = 26f
private const val ICON_WIDTH = 4f
private const val ICON_DOT = 5f
private const val DOT_START = 62f
private const val TEXT_RISE = 8f
private const val LINE = 20f
// Blur sigmas of the glows.
private const val ORB_GLOW = 14f
private const val RING_GLOW = 6f
private const val CARD_GLOW = 18f
private const val DOT_GLOW = 3f
private const val BOUNCE_GLOW = 6f
private const val SHINE_GLOW = 3f
private const val ICON_GLOW = 5f

// Each dot: angle in degrees, distance from the centre, diameter, and orbit speed in radians a second.
private val DOTS = listOf(
    floatArrayOf(-70f, 100f, 3.5f, 0.35f),
    floatArrayOf(-5f, 112f, 2.5f, -0.3f),
    floatArrayOf(60f, 96f, 3f, 0.32f),
    floatArrayOf(125f, 116f, 2f, -0.34f),
    floatArrayOf(190f, 104f, 4f, 0.28f),
    floatArrayOf(250f, 120f, 2.5f, -0.3f),
)

// Under reduced motion the run jumps between still moments, fading through black at each jump.
private val STILLS = listOf(0f to 0f, 1.6f to 4.2f, 4.6f to 6.1f, 6.6f to 0f)
private const val DIP = 0.2f

private const val HALO_INNER = 0x0D1020
private const val HALO_OUTER = 0x141A36
private const val GLOW = 0x2F5BFF
private val ORB_LIGHT = intArrayOf(0x8FB3FF, 0x2F5BFF, 0x1A3FE6)
private val ORB_DARK = intArrayOf(0x5A86FF, 0x1F4BFF, 0x0B2BC9)
private val GLASS = intArrayOf(0x26305C, 0x0C1024, 0x05060C)
private val RIM = intArrayOf(0x8FB0FF, 0x5F78FF, 0x9B6BFF)
private const val DOT = 0x7F9BFF
private const val CARD_START = 0x4D7BFF
private const val CARD_FILL = 0x1A1D2B
private const val ICON_DOT_FILL = 0x7FA8FF
private const val BOUNCE = 0x3D6BFF
private const val WHITE = 0xFFFFFF

private val Background = Color(0xFF05060C)
private val Ink = Color.White.copy(alpha = 0.9f)
private val Ring = listOf(Color(0xFF3D6BFF), Color(0xFF9B6BFF), Color(0xFFFFE3F4))

private val TitleStyle = TextStyle(
    color = Ink,
    fontSize = 15.sp,
    lineHeight = LINE.sp,
    fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.Center,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun clamp(x: Float) = x.coerceIn(0f, 1f)
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
private fun easeIn(x: Float) = x * x * x
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

/** 0 at both ends, 1 in the middle, flat where it starts and ends. */
private fun bump(x: Float) = if (x <= 0f || x >= 1f) 0f else 0.5f - 0.5f * cos(2f * Math.PI.toFloat() * x)

/** A spring from 0 to 1 released at rest, [t] seconds after it starts. */
private fun spring(t: Float): Float {
    if (t <= 0f) return 0f
    val w0 = sqrt(SPRING_STIFFNESS / SPRING_MASS)
    val zeta = SPRING_DAMPING / (2f * sqrt(SPRING_STIFFNESS * SPRING_MASS))
    val wd = w0 * sqrt(1f - zeta * zeta)
    return 1f - exp(-zeta * w0 * t) * (cos(wd * t) + (zeta * w0 / wd) * sin(wd * t))
}

/** The moment of the run shown under reduced motion. */
private fun stillAt(seq: Float): Float {
    var shown = 0f
    for ((at, moment) in STILLS) if (seq >= at) shown = moment
    return shown
}

/** The screen's opacity under reduced motion: it fades through black around each jump. */
private fun dipAt(seq: Float): Float {
    var alpha = 1f
    for (i in 1 until STILLS.size) alpha = min(alpha, clamp(abs(seq - STILLS[i].first) / DIP))
    return alpha
}

private fun argb(rgb: Int, alpha: Float): Int = ((clamp(alpha) * 255f).toInt() shl 24) or (rgb and 0xFFFFFF)

private fun mix(a: Int, b: Int, t: Float): Int {
    fun channel(c: Int, shift: Int) = ((c shr shift) and 0xFF).toFloat()
    val r = lerp(channel(a, 16), channel(b, 16), t).toInt()
    val g = lerp(channel(a, 8), channel(b, 8), t).toInt()
    val bl = lerp(channel(a, 0), channel(b, 0), t).toInt()
    return (r shl 16) or (g shl 8) or bl
}

private fun Color.withAlpha(alpha: Float) = copy(alpha = alpha * this.alpha).toArgb()

/** A path through 33 points of an ellipse's arc, angles in degrees. */
private fun arcPath(cx: Float, cy: Float, rx: Float, ry: Float, from: Float, sweep: Float, close: Boolean): Path {
    val path = Path()
    val steps = 32
    for (i in 0..steps) {
        val a = Math.toRadians((from + sweep * i / steps).toDouble())
        val x = cx + rx * cos(a).toFloat()
        val y = cy + ry * sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (close) path.close()
    return path
}

/** The run's clock and what drives it, kept out of composition so touches and frames share it. */
private class OrbRingMorphClock {
    /** Seconds into the run; read only while drawing. */
    val time = mutableFloatStateOf(0f)
    /** Bumped by a touch so the frame loop starts when nothing else is running it. */
    val wake = mutableIntStateOf(0)
    // 0 at rest, 1 for one run by hand, 2 for autoPlay.
    var mode = 0
    var last = -1L
    var held = false
    // A touch is noted here and stamped with the frame's time by the clock: 1 a tap, 2 a hold.
    var request = 0
    var touchedAt = Long.MIN_VALUE / 2
}

/** Paints reused from frame to frame. Mask-filter blurs need API 28 on hardware canvases. */
private class OrbRingMorphPaints {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val blurs = HashMap<Float, Paint>()

    // A paint's alpha scales its shader, so a shaded paint is made opaque first.
    fun fill(shader: Shader) = fill.apply { color = android.graphics.Color.BLACK; this.shader = shader }
    fun fill(color: Int) = fill.apply { shader = null; this.color = color }
    fun stroke(width: Float, shader: Shader) = line.apply { strokeWidth = width; color = android.graphics.Color.BLACK; this.shader = shader }
    fun stroke(width: Float, color: Int) = line.apply { strokeWidth = width; shader = null; this.color = color }

    /** A paint blurred by [sigma] dp (the canvas is in dp, and the blur follows its scale). */
    fun blurred(sigma: Float, stroke: Float = 0f): Paint = blurs.getOrPut(sigma) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) }
    }.apply {
        shader = null
        color = android.graphics.Color.BLACK
        if (stroke > 0f) {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        } else {
            style = Paint.Style.FILL
        }
    }
}

private fun radial(x: Float, y: Float, r: Float, colors: IntArray, stops: FloatArray) =
    RadialGradient(x, y, max(r, 0.01f), colors, stops, Shader.TileMode.CLAMP)

private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray) =
    LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)

/** The orb: two half discs, slid apart along their seam, that merge into a dark glass sphere. */
private fun OrbRingMorphPaints.drawOrb(
    canvas: android.graphics.Canvas,
    cx: Float,
    cy: Float,
    d: Float,
    rot: Float,
    glass: Float,
    alpha: Float,
    glow: Float,
) {
    if (alpha <= 0.001f || d <= 0.5f) return
    val r = d / 2f
    if (canBlur) {
        val aura = blurred(ORB_GLOW)
        aura.color = argb(GLOW, alpha * (glow * (1f - glass) + 0.3f * glass))
        canvas.drawCircle(cx, cy, r * 1.15f, aura)
    }
    if (glass < 1f) {
        val seam = SEAM + rot
        val ux = cos(Math.toRadians(seam.toDouble())).toFloat()
        val uy = sin(Math.toRadians(seam.toDouble())).toFloat()
        val slide = SPLIT * r * (1f - glass)
        val a = alpha * (1f - glass)
        listOf(ORB_LIGHT, ORB_DARK).forEachIndexed { k, c ->
            val sign = if (k == 0) -1f else 1f
            val hx = cx + ux * slide * sign
            val hy = cy + uy * slide * sign
            canvas.drawPath(arcPath(hx, hy, r, r, seam + (if (k == 0) 0f else 180f), 180f, true), fill(radial(hx - 0.35f * r, hy - 0.4f * r, 1.3f * r, intArrayOf(argb(c[0], a), argb(c[1], a), argb(c[2], a)), floatArrayOf(0f, 0.6f, 1f))))
        }
    }
    if (glass > 0f) {
        val a = alpha * glass
        canvas.drawCircle(cx, cy, r, fill(radial(cx - 0.3f * r, cy - 0.35f * r, 1.25f * r, intArrayOf(argb(GLASS[0], a), argb(GLASS[1], a), argb(GLASS[2], a)), floatArrayOf(0f, 0.55f, 1f))))
        // Light bouncing up from below, and a small highlight up on the left.
        if (canBlur) {
            val bounce = blurred(BOUNCE_GLOW)
            bounce.color = argb(BOUNCE, 0.25f * a)
            canvas.drawOval(RectF(cx - 0.6f * r, cy + 0.55f * r - 0.18f * r, cx + 0.6f * r, cy + 0.55f * r + 0.18f * r), bounce)
            val shine = blurred(SHINE_GLOW)
            shine.color = argb(WHITE, 0.22f * a)
            canvas.save()
            canvas.translate(cx - 0.38f * r, cy - 0.48f * r)
            canvas.rotate(-35f)
            canvas.drawOval(RectF(-0.22f * r, -0.12f * r, 0.22f * r, 0.12f * r), shine)
            canvas.restore()
        }
        canvas.drawCircle(cx, cy, r - 0.75f, stroke(1.5f, linear(cx - 0.7f * r, cy - 0.7f * r, cx + 0.7f * r, cy + 0.7f * r, intArrayOf(argb(RIM[0], 0.95f * a), argb(RIM[1], 0.15f * a), argb(RIM[2], 0.6f * a)))))
    }
}

/** Everything on screen at [t] seconds into the run, in dp. */
private fun OrbRingMorphPaints.drawScene(canvas: android.graphics.Canvas, width: Float, height: Float, t: Float, ring: List<Color>) {
    val cx = width / 2f
    val cy = height * ORB_Y
    val contract = easeInOut(clamp((t - CONTRACT_AT) / CONTRACT_FOR))

    // The halo the sphere sits in.
    val halo = easeOut(clamp((t - HALO_AT) / HALO_FOR))
    val haloAlpha = 0.6f * halo * (1f - contract)
    if (haloAlpha > 0.001f) {
        val r = HALO * (0.85f + 0.15f * halo) * (1f - 0.1f * contract)
        canvas.drawCircle(cx, cy, r, fill(radial(cx, cy, r, intArrayOf(argb(HALO_INNER, haloAlpha), argb(HALO_OUTER, haloAlpha)), floatArrayOf(0.4f, 1f))))
        canvas.drawCircle(cx, cy, r - 0.5f, stroke(1f, argb(GLOW, 0.2f * haloAlpha)))
    }

    // The orb, then the sphere it becomes.
    if (t >= ORB_IN_AT) {
        val back = easeOut(clamp((t - ORB_IN_AT) / ORB_IN_FOR))
        drawOrb(canvas, cx, cy, ORB * (0.6f + 0.4f * back), 0f, 0f, back, 0.5f)
    } else {
        val pulse = bump((t - PULSE_AT) / PULSE_FOR)
        val shrink = easeInOut(clamp((t - SHRINK_AT) / SHRINK_FOR))
        val grow = spring(t - GROW_AT)
        val glass = easeInOut(clamp((t - GROW_AT) / GLASS_FOR))
        val rot = 360f * easeInOut(clamp((t - SPIN_AT) / SPIN_FOR))
        var d = if (t < GROW_AT) lerp(ORB, ORB_SMALL, shrink) else lerp(ORB_SMALL, SPHERE, grow)
        d *= (1f - PULSE * pulse) * (1f - 0.35f * contract)
        drawOrb(canvas, cx, cy, d, rot, glass, 1f - contract, 0.5f + 0.3f * pulse)
    }

    // Dots that drift out from the sphere on slow orbits.
    val dotsOut = 1f - easeInOut(clamp((t - DOTS_OUT_AT) / DOTS_OUT_FOR))
    if (t >= DOTS_AT && dotsOut > 0f) {
        DOTS.forEachIndexed { i, (angle, distance, diameter, speed) ->
            val f = easeOut(clamp((t - DOTS_AT - i * DOT_STAGGER) / DOT_FLOAT))
            if (f <= 0f) return@forEachIndexed
            val a = Math.toRadians(angle.toDouble()).toFloat() + speed * (t - DOTS_AT)
            val r = lerp(DOT_START, distance, f)
            val alpha = clamp(f * 3f) * dotsOut
            val x = cx + cos(a) * r
            val y = cy + sin(a) * r
            if (canBlur) {
                val glow = blurred(DOT_GLOW)
                glow.color = argb(DOT, 0.6f * alpha)
                canvas.drawCircle(x, y, diameter * 0.75f, glow)
            }
            canvas.drawCircle(x, y, diameter / 2f, fill(argb(DOT, alpha)))
        }
    }

    // The ring: four arcs on an ellipse that flattens into a vertical pill.
    val ringAlpha = 1f - clamp((t - (CARD_AT - SWAP)) / SWAP)
    if (t >= RING_AT && ringAlpha > 0f) {
        val rx = RING_RADIUS * (1f - contract)
        val ry = lerp(RING_RADIUS, PILL_HALF, contract)
        val turn = 360f * (t - RING_AT) / RING_TURN
        val thickness = lerp(RING_WIDTH, PILL_WIDTH, contract)
        for (i in 0 until 4) {
            val drawn = easeOut(clamp((t - RING_AT - i * RING_STAGGER) / RING_DRAW))
            val breath = 1f + BREATH_AMOUNT * sin(2f * Math.PI.toFloat() * (t - RING_AT) / BREATH + i * Math.PI.toFloat() / 2f) * (1f - contract)
            val span = lerp(ARC * drawn * breath, 90f, contract)
            if (span < 0.5f) continue
            val from = -90f + i * 90f + turn - span / 2f
            val a0 = Math.toRadians(from.toDouble())
            val a1 = Math.toRadians((from + span).toDouble())
            val x0 = cx + rx * cos(a0).toFloat()
            val y0 = cy + ry * sin(a0).toFloat()
            val x1 = cx + rx * cos(a1).toFloat()
            val y1 = cy + ry * sin(a1).toFloat()
            val path = arcPath(cx, cy, rx, ry, from, span, false)
            if (canBlur) {
                val glow = blurred(RING_GLOW, thickness + 4f)
                glow.shader = linear(x0, y0, x1, y1, IntArray(3) { ring[it].withAlpha(0.55f * ringAlpha) })
                canvas.drawPath(path, glow)
                glow.shader = null
            }
            canvas.drawPath(path, stroke(thickness, linear(x0, y0, x1, y1, IntArray(3) { ring[it].withAlpha(ringAlpha) })))
        }
    }

    // The card the pill opens into, with a ring icon drawn in it.
    val cardIn = clamp((t - (CARD_AT - SWAP)) / SWAP)
    val cardOut = easeIn(clamp((t - CARD_OUT_AT) / CARD_OUT_FOR))
    val cardAlpha = cardIn * (1f - cardOut)
    if (cardAlpha <= 0.001f || t >= ORB_IN_AT) return
    val s = spring(t - CARD_AT)
    val scale = 1f - 0.06f * cardOut
    val w = lerp(PILL_WIDTH, CARD, s) * scale
    val h = lerp(PILL_HALF * 2f + PILL_WIDTH, CARD, s) * scale
    val r = minOf(lerp(PILL_WIDTH / 2f, CARD_RADIUS, clamp(s)) * scale, w / 2f, h / 2f)
    val tone = easeInOut(clamp((t - CARD_AT) / TONE_FOR))
    val box = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    if (canBlur) {
        val glow = blurred(CARD_GLOW)
        glow.color = argb(GLOW, cardAlpha * lerp(0.7f, 0.22f, tone))
        canvas.drawRoundRect(box, r, r, glow)
    }
    canvas.drawRoundRect(box, r, r, fill(argb(mix(CARD_START, CARD_FILL, tone), cardAlpha)))
    canvas.drawRoundRect(box, r, r, fill(linear(cx, cy - h / 2f, cx, cy, intArrayOf(argb(WHITE, 0.08f * cardAlpha), argb(WHITE, 0f)))))
    canvas.drawRoundRect(RectF(box.left + 0.5f, box.top + 0.5f, box.right - 0.5f, box.bottom - 0.5f), r, r, stroke(1f, argb(WHITE, 0.08f * cardAlpha * tone)))

    val icon = easeInOut(clamp((t - ICON_AT) / ICON_FOR))
    if (icon > 0f) {
        val ir = ICON_RADIUS * scale
        val path = arcPath(cx, cy, ir, ir, -90f, 360f * icon, false)
        if (canBlur) {
            val glow = blurred(ICON_GLOW, ICON_WIDTH + 3f)
            glow.shader = linear(cx - ir, cy - ir, cx + ir, cy + ir, IntArray(3) { ring[it].withAlpha(0.6f * cardAlpha) })
            canvas.drawPath(path, glow)
            glow.shader = null
        }
        canvas.drawPath(path, stroke(ICON_WIDTH, linear(cx - ir, cy - ir, cx + ir, cy + ir, IntArray(3) { ring[it].withAlpha(cardAlpha) })))
    }
    val pop = spring(t - ICON_DOT_AT)
    if (pop > 0f) {
        canvas.drawCircle(cx, cy, ICON_DOT * pop * scale, fill(argb(ICON_DOT_FILL, cardAlpha)))
    }
}

/**
 * A splash sequence: a split blue orb pulses, spins, and grows into a dark glass sphere; a ring of
 * four gradient arcs draws around it, flattens into a vertical pill, and the pill opens into a card.
 *
 * @param title The two lines under the orb.
 * @param ringColors The ring's gradient, from where each arc starts to where it ends.
 * @param autoPlay Plays the sequence by itself every [LOOP] seconds.
 * @param onReveal Called when the card shows during a run started by hand.
 * @param onDone Called when a run started by hand is back at rest.
 */
@Composable
fun OrbRingMorph(
    modifier: Modifier = Modifier,
    title: String = "Your face,\nis your wallet",
    background: Color = Background,
    ringColors: List<Color> = Ring,
    autoPlay: Boolean = false,
    onReveal: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val clock = remember { OrbRingMorphClock() }
    val paints = remember { OrbRingMorphPaints() }
    val reveal by rememberUpdatedState(onReveal)
    val done by rememberUpdatedState(onDone)
    val wake by clock.wake

    // The frame loop runs while autoPlay is on or a run is going, and stops at rest otherwise.
    LaunchedEffect(autoPlay, wake) {
        while (autoPlay || clock.mode != 0 || clock.request != 0) {
            withFrameNanos { now ->
                val before = clock.last
                clock.last = now
                var dt = if (before < 0 || clock.held) 0f else (now - before) / 1e9f
                val asked = clock.request
                if (asked != 0) {
                    clock.request = 0
                    clock.touchedAt = now
                    // A tap starts a run unless one by hand is going; a hold during autoPlay finishes its run once.
                    if (asked == 1 && clock.mode != 1) {
                        clock.mode = 1
                        clock.time.floatValue = 0f
                        dt = 0f
                    } else if (asked == 2 && clock.mode == 2) {
                        clock.mode = 1
                    }
                }
                when (clock.mode) {
                    0 -> if (autoPlay && !clock.held && now - clock.touchedAt >= (USER_HOLD * 1e9f).toLong()) clock.mode = 2
                    else -> {
                        val previous = clock.time.floatValue
                        var next = previous + dt
                        if (clock.mode == 1 && previous < CARD_AT && next >= CARD_AT) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            reveal()
                        }
                        if (clock.mode == 2 && next >= LOOP) next -= LOOP
                        if (clock.mode == 1 && next >= LOOP) {
                            next = 0f
                            clock.mode = 0
                            done()
                        }
                        clock.time.floatValue = next
                    }
                }
            }
        }
        clock.last = -1L
    }

    fun shownTime(): Float = if (reduced) stillAt(clock.time.floatValue) else clock.time.floatValue

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(background)
            .semantics {
                role = Role.Button
                contentDescription = "Play the animation. Hold to pause it."
                onClick {
                    clock.request = 1
                    clock.wake.intValue++
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = withTimeoutOrNull(HOLD_AFTER_MS) { waitForUpOrCancellation() }
                    if (up != null) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        clock.request = 1
                        clock.wake.intValue++
                    } else {
                        clock.held = true
                        clock.request = 2
                        clock.wake.intValue++
                        waitForUpOrCancellation()
                        clock.held = false
                        clock.request = 2
                        clock.wake.intValue++
                    }
                }
            },
    ) {
        val textTop = maxHeight * TEXT_Y - LINE.dp
        val density = LocalDensity.current.density
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (reduced) dipAt(clock.time.floatValue) else 1f },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val t = shownTime()
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.scale(density, density)
                    paints.drawScene(native, size.width / density, size.height / density, t, ringColors)
                    native.restore()
                }
            }
            Text(
                text = title,
                style = TitleStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = textTop)
                    .padding(horizontal = 32.dp)
                    .graphicsLayer {
                        val t = shownTime()
                        val shown = easeOut(clamp((t - TEXT_IN_AT) / TEXT_IN_FOR))
                        val gone = easeInOut(clamp((t - TEXT_OUT_AT) / TEXT_OUT_FOR))
                        alpha = shown * (1f - gone)
                        translationY = TEXT_RISE * (1f - shown) * density
                    },
            )
        }
    }
}

@Preview
@Composable
private fun OrbRingMorphPreview() {
    OrbRingMorph(autoPlay = true)
}
