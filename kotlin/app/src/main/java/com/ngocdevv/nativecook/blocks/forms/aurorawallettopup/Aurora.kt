package com.ngocdevv.nativecook.blocks.forms.aurorawallettopup

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// The clock of a top-up, in seconds since Continue was released. The wait (`duration`) is the
// time from Continue to the moment the light gathers and leaves.

/** autoPlay: one top-up every LOOP seconds, Continue pressed at PRESS_AT for PRESS_FOR. */
internal const val LOOP = 8.5f
internal const val PRESS_AT = 0.8f
internal const val PRESS_FOR = 0.15f

/** After a touch autoPlay waits this long, and for the form to be idle, before it starts over. */
internal const val USER_HOLD = 3f

internal const val BLUR_IN = 0.3f
internal const val SWEEP = 0.9f

// The result replaces the spinner this long after the sweep starts, and holds.
internal const val RESULT_AFTER = 0.3f
internal const val RESULT_IN = 0.25f
internal const val RESULT_HOLD = 1.8f
internal const val STATUS_OUT = 0.3f
internal const val FORM_BACK_AFTER = 0.1f
internal const val FORM_BACK_IN = 0.4f
internal const val COUNT_AFTER = 0.15f
internal const val COUNT_FOR = 0.7f

// The form takes touches again this long after it started to come back.
internal const val SETTLE = 0.9f

internal fun clamp01(t: Float) = t.coerceIn(0f, 1f)

internal fun smooth(t: Float, from: Float, to: Float): Float {
    val u = clamp01((t - from) / (to - from))
    return u * u * (3f - 2f * u)
}

internal fun easeOut(t: Float): Float {
    val u = 1f - clamp01(t)
    return 1f - u * u * u
}

/** When the result starts to leave and the form to come back. */
internal fun returnAt(duration: Float) = duration + RESULT_AFTER + RESULT_HOLD

internal fun endAt(duration: Float) = returnAt(duration) + SETTLE

/**
 * One soft ellipse of light. [x] and [rx] are fractions of the screen's width, [y] and [ry] of its
 * height, so the picture is the same on every phone.
 */
private class Light(var x: Float, var y: Float, var rx: Float, var ry: Float, var alpha: Float, var red: Float, var green: Float, var blue: Float) {
    constructor(x: Float, y: Float, rx: Float, ry: Float, alpha: Float, hex: Int) :
        this(x, y, rx, ry, alpha, ((hex shr 16) and 0xff) / 255f, ((hex shr 8) and 0xff) / 255f, (hex and 0xff) / 255f)

    fun set(other: Light) {
        x = other.x; y = other.y; rx = other.rx; ry = other.ry
        alpha = other.alpha; red = other.red; green = other.green; blue = other.blue
    }

    /** Moves this light the share [t] of the way to [other], which may sit at an offset. */
    fun mix(other: Light, t: Float, dx: Float = 0f, dy: Float = 0f) {
        x += (other.x + dx - x) * t
        y += (other.y + dy - y) * t
        rx += (other.rx - rx) * t
        ry += (other.ry - ry) * t
        alpha += (other.alpha - alpha) * t
        red += (other.red - red) * t
        green += (other.green - green) * t
        blue += (other.blue - blue) * t
    }
}

/** A light's own slow sine: reach in x and y, speed in radians a second, phase, and how much its size breathes. */
private class Wander(val x: Float, val y: Float, val speed: Float, val phase: Float, val pulse: Float)

/**
 * A light through the wait and the sweep: where it rests, at the hottest moment, as it pales
 * before the end, gathered round the rising centre (x and y are offsets from that centre), and
 * as the last trace along the top edge.
 */
private class Source(val rest: Light, val hot: Light, val pale: Light, val gathered: Light, val trace: Light, val wander: Wander)

private val Sources = listOf(
    // Deep violet in the lower left corner.
    Source(
        rest = Light(0.14f, 0.888f, 0.40f, 0.200f, 1.00f, 0x2a06b4),
        hot = Light(0.16f, 0.890f, 0.50f, 0.200f, 1.00f, 0x2204b6),
        pale = Light(0.20f, 0.890f, 0.48f, 0.195f, 1.00f, 0x4a0cd8),
        gathered = Light(-0.44f, 0.100f, 0.54f, 0.340f, 1.20f, 0x5c14dc),
        trace = Light(0.10f, -0.040f, 0.42f, 0.280f, 0.90f, 0x8f8af0),
        wander = Wander(0.03f, 0.01f, 2.1f, 0.0f, 0.05f),
    ),
    // Magenta through the middle, under the bar.
    Source(
        rest = Light(0.38f, 0.965f, 0.56f, 0.265f, 1.00f, 0xe060c5),
        hot = Light(0.39f, 0.975f, 0.57f, 0.245f, 1.00f, 0xea8ad8),
        pale = Light(0.30f, 0.990f, 0.42f, 0.250f, 1.00f, 0xffd6ff),
        gathered = Light(-0.15f, 0.180f, 0.60f, 0.200f, 0.85f, 0xa050e0),
        trace = Light(0.40f, -0.040f, 0.50f, 0.260f, 0.60f, 0xb9a8e0),
        wander = Wander(0.035f, 0.008f, 1.7f, 1.7f, 0.04f),
    ),
    // Peach across the right, turning orange in the middle of the wait.
    Source(
        rest = Light(0.70f, 0.900f, 0.84f, 0.255f, 0.85f, 0xffa890),
        hot = Light(0.70f, 0.912f, 0.85f, 0.266f, 0.85f, 0xf59b34),
        pale = Light(0.72f, 0.891f, 0.90f, 0.230f, 1.00f, 0xfdc88c),
        gathered = Light(0.46f, 0.000f, 0.50f, 0.320f, 1.00f, 0xffd49a),
        trace = Light(0.88f, -0.040f, 0.42f, 0.280f, 0.90f, 0xd2f6d0),
        wander = Wander(0.03f, 0.01f, 1.9f, 3.1f, 0.05f),
    ),
    // The crest of the hill, which breathes the most.
    Source(
        rest = Light(0.62f, 0.795f, 0.42f, 0.125f, 0.40f, 0xf39a48),
        hot = Light(0.56f, 0.860f, 0.62f, 0.205f, 0.53f, 0xff4a2a),
        pale = Light(0.57f, 0.868f, 0.51f, 0.230f, 0.70f, 0xffb2b1),
        gathered = Light(0.08f, -0.140f, 0.62f, 0.220f, 0.70f, 0xffd8cc),
        trace = Light(0.62f, -0.040f, 0.50f, 0.240f, 0.50f, 0xdfe6e0),
        wander = Wander(0.05f, 0.012f, 2.6f, 4.4f, 0.12f),
    ),
    // Ivory where the light meets the bar; it becomes the core of the blob that rises.
    Source(
        rest = Light(0.85f, 0.940f, 0.50f, 0.220f, 0.70f, 0xfff6e2),
        hot = Light(0.82f, 0.950f, 0.38f, 0.160f, 0.50f, 0xffd0f0),
        pale = Light(0.58f, 0.950f, 0.70f, 0.280f, 1.00f, 0xffc8e6),
        gathered = Light(0.00f, -0.020f, 0.76f, 0.380f, 1.30f, 0xfff4f8),
        trace = Light(0.36f, -0.040f, 0.40f, 0.200f, 0.70f, 0xd8e6fa),
        wander = Wander(0.025f, 0.006f, 1.5f, 2.3f, 0.04f),
    ),
)

// How a light fades from its centre to its edge: cos², so it has no rim and no bright point.
// A light stronger than 1 is solid in the middle and fades from further out.
private val FalloffAt = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
private val Falloff = floatArrayOf(1f, 0.905f, 0.655f, 0.345f, 0.095f, 0f)

// The gathered light starts at this height and leaves towards the top edge.
private const val RISE_FROM = 0.9f
private const val RISE_TO = 0.1f
private const val BAR_BLUR = 7f

/** What the light is drawn with, kept between frames. */
internal class AuroraPaint {
    internal val bar = SoftRoundRect(Color.White, sigma = BAR_BLUR)
    private val light = Light(0f, 0f, 0f, 0f, 0f, 0)

    /**
     * The light of a top-up at [e] seconds, in a canvas [width] × [height]: five soft ellipses
     * blended as screen so that where they overlap they glow, and the Continue button (its frame
     * is [barX], [barY], [barWidth], [barHeight]) as a blurred bar of light in front of them.
     * [still] is reduced motion: a still glow that fades in and out. The canvas needs a layer of
     * its own, or the lights would blend with the screen behind them instead of with each other.
     */
    fun draw(
        scope: DrawScope,
        e: Float,
        duration: Float,
        still: Boolean,
        width: Float,
        height: Float,
        barX: Float,
        barY: Float,
        barWidth: Float,
        barHeight: Float,
    ) = with(scope) {
        val grow = smooth(e, 0.1f, 0.75f)
        val heat = if (still) 0f else smooth(e, 0.34f * duration, 0.66f * duration) * (1f - smooth(e, 0.74f * duration, 0.92f * duration))
        val pale = if (still) 0f else smooth(e, 0.78f * duration, duration)
        val s = clamp01((e - duration) / SWEEP)
        val gather = if (still) 0f else smooth(s, 0f, 0.25f)
        val centre = RISE_FROM + (RISE_TO - RISE_FROM) * smooth(s, 0.05f, 0.5f)
        val settle = if (still) 0f else smooth(s, 0.4f, 0.62f)
        val fade = 1f - if (still) smooth(s, 0f, 0.35f) else smooth(s, 0.6f, 1f)
        val shown = smooth(grow, 0f, 0.6f)

        for (source in Sources) {
            val l = light
            l.set(source.rest)
            l.mix(source.hot, heat)
            l.mix(source.pale, pale)
            if (!still) {
                val w = source.wander
                l.x += w.x * sin(e * w.speed + w.phase)
                l.y += w.y * cos(e * w.speed * 1.3f + w.phase)
                val breath = 1f + w.pulse * sin(e * w.speed * 1.7f + w.phase * 2f)
                l.rx *= breath
                l.ry *= breath
                // It grows out of the bar: lower, narrower and flatter at first.
                l.x = 0.5f + (l.x - 0.5f) * (0.7f + 0.3f * grow)
                l.y += 0.07f * (1f - grow)
                l.rx *= 0.6f + 0.4f * grow
                l.ry *= 0.45f + 0.55f * grow
            }
            l.alpha *= shown
            l.mix(source.gathered, gather, dx = 0.5f, dy = centre)
            l.mix(source.trace, settle)
            l.alpha *= fade

            val brush = Brush.radialGradient(
                colorStops = Array(Falloff.size) { FalloffAt[it] to Color(l.red, l.green, l.blue, min(1f, l.alpha * Falloff[it])) },
                center = Offset.Zero,
                radius = 1f,
            )
            withTransform({
                translate(l.x * width, l.y * height)
                scale(l.rx * width, l.ry * height, pivot = Offset.Zero)
            }) {
                drawCircle(brush, radius = 1f, center = Offset.Zero, blendMode = BlendMode.Screen)
            }
        }

        val barAlpha = (0.6f - 0.2f * grow) * smooth(e, 0f, 0.18f) * (1f - gather) * (if (still) fade else 1f)
        if (barAlpha > 0.001f) {
            drawIntoCanvas { bar.draw(it.nativeCanvas, barX, barY, barWidth, barHeight, barHeight / 2f, barAlpha) }
        }
    }
}
