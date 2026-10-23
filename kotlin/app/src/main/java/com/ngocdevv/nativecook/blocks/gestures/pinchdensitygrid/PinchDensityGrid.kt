package com.ngocdevv.nativecook.blocks.gestures.pinchdensitygrid

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/** One plate of the herbarium: an Unsplash photo id, its name and a line under it. */
data class Plate(val id: String, val name: String, val note: String)

private fun unsplash(id: String, width: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$width&q=80"

// Every plate is fetched small for the dense grids (a 5 column tile is ~80 dp, a 3 column one
// ~125 dp); the ones on screen at 2 or 1 columns are fetched again at SHARP_WIDTH px. Sharp
// copies are kept for the plates within SHARP_KEEP of the screen and dropped beyond.
private const val THUMB_WIDTH = 420
private const val SHARP_WIDTH = 1080
private const val SHARP_AHEAD = 2
private const val SHARP_KEEP = 6

private fun plate(id: String, name: String, note: String) = Plate("photo-$id", name, note)

// Leaves, bouquets and tulips, dealt so neighbours differ.
val HerbariumPlates: List<Plate> = listOf(
    plate("1503149779833-1de50ebe5f8a", "Shade Leaf", "Monstera deliciosa"),
    plate("1582794543139-8ac9cb0f7b11", "Blush", "Paeonia lactiflora"),
    plate("1554494583-c4e1649bfe71", "Open Sky", "Tulipa, red"),
    plate("1491147334573-44cbb4602074", "Pinstripe", "Calathea orbifolia"),
    plate("1572454591674-2739f30d8c40", "Apricot", "Garden roses"),
    plate("1586968295564-92fd7572718b", "On Cedar", "Tulipa, pink"),
    plate("1446071103084-c257b5f70672", "Rosette", "Echeveria"),
    plate("1561181286-d3fee7d55364", "Glass Vase", "Tulipa, mixed"),
    plate("1587316830148-c9b01df2da38", "Peach Light", "Tulipa, apricot"),
    plate("1598764557991-b9f211b73b81", "Split Leaf", "Monstera deliciosa"),
    plate("1457089328109-e5d9bd499191", "Ember Posy", "Ranunculus, anemone"),
    plate("1488928741225-2aaf732c96cc", "Field Rows", "Tulipa, mixed"),
    plate("1477554193778-9562c28588c0", "Rubber Fig", "Ficus elastica"),
    plate("1567696153798-9111f9cd3d0d", "Kraft Wrap", "Sunflower, eucalyptus"),
    plate("1614791199038-6869a104fe5f", "Single Stem", "Tulipa"),
    plate("1543459176-4426b37223ba", "Understory", "Monstera and fern"),
    plate("1533616688419-b7a585564566", "Marmalade", "Roses and stock"),
    plate("1525898939552-346875c89d92", "Soft Focus", "Tulipa, spring"),
    plate("1525498128493-380d1990a112", "White Room", "Monstera, potted"),
    plate("1589095181425-c038b3871b6a", "Handed Over", "Spray roses"),
    plate("1520763185298-1b434c919102", "Pink Cup", "Tulipa, pink"),
    plate("1530049478161-0780526964f4", "Night Frond", "Monstera deliciosa"),
    plate("1487530811176-3780de880c2d", "Dusk Bouquet", "Roses, wax flower"),
    plate("1559150180-2467f804f9bf", "Open Field", "Tulipa, mixed"),
    plate("1493552152660-f915ab47ae9d", "Window Fig", "Ficus elastica"),
    plate("1685613858397-64f79a0f3603", "Market Stall", "Carnations, daisies"),
    plate("1518701005037-d53b1f67bb1c", "Mint", "Tulipa, white"),
    plate("1525923838299-2312b60f6d69", "Palm Fan", "Chamaedorea"),
    plate("1531120364508-a6b656c3e78d", "Meadow", "Sunflower, cornflower"),
    plate("1561181226-e8a7edd504c6", "Water Glass", "Tulipa, pink"),
    plate("1605449670493-ca1d812d0488", "First Leaf", "Monstera, young"),
    plate("1615385639736-362b69696227", "Paper Cone", "Tulipa, red"),
    plate("1468327768560-75b778cbb551", "Flame", "Tulipa, flamed"),
    plate("1604866830513-d54766457f45", "Olive Pot", "Monstera, potted"),
    plate("1583228858294-6745cb25969e", "Pale Gift", "Roses, gypsophila"),
    plate("1586554978186-deffc54a0a5c", "Shadow", "Tulipa, pink"),
)

private val Paper = Color(0xFFEEECE4)
private val Ink = Color(0xFF1E2A20)
private val TileTone = Color(0xFFDCDCCF)

/**
 * A rung of the column ladder: the margin and gap (dp), the corner radius (dp), the tile's height
 * over its width, and the room left under it for the label.
 */
private class Rung(val cols: Int, val margin: Float, val gap: Float, val radius: Float, val aspect: Float, val label: Float)

// The column ladder a pinch walks along, densest first.
private val Ladder = arrayOf(
    Rung(5, 2f, 2f, 3f, 1.25f, 0f),
    Rung(3, 10f, 6f, 8f, 1.25f, 0f),
    Rung(2, 16f, 12f, 12f, 1.25f, 44f),
    Rung(1, 24f, 26f, 18f, 1.25f, 56f),
)
private val LAST = Ladder.size - 1
private val LogCols = FloatArray(Ladder.size) { ln(Ladder[it].cols.toFloat()) }
// The rung the grid opens on, and the two a double tap toggles between.
private const val START_RUNG = 1f
private const val ONE_COLUMN = 3
private const val THREE_COLUMNS = 1

// A pinch moves along the ladder in log columns: spreading the fingers by a factor s divides the
// column count by s^PINCH_GAIN, so 5 → 1 takes a spread of about 3.4×.
private const val PINCH_GAIN = 1.3f
// Past either end the pinch keeps following with resistance, never further than OVERPINCH rungs,
// which grow or shrink the whole grid about the fingers by OVERPINCH_SCALE per rung.
private const val OVERPINCH = 0.3f
private const val OVERPINCH_SCALE = 0.32f
// The tiles follow the fingers sideways during a pinch: a share of the anchor tile's drift, plus
// the fingers' own movement, with resistance, never past SIDEWAYS_MAX dp.
private const val SIDEWAYS_FOLLOW = 0.5f
private const val SIDEWAYS_MAX = 60f
// Release: the pinch is thrown PROJECT s ahead at its speed before the nearest rung is chosen,
// moving the landing rung by at most PROJECT_MAX.
private const val PROJECT = 0.14f
private const val PROJECT_MAX = 0.6f
private const val SETTLE_MS = 520f
private const val SETTLE_RATIO = 0.8f
private const val SCROLL_MS = 520f
private const val SCROLL_RATIO = 1f
// Scrolling past either end gives less the further it is pulled, never more than RUBBER_MAX dp.
private const val RUBBER_MAX = 90f

// Labels fade in between these rungs (2 columns reads them in full) and grow by LABEL_GROW at 1.
private const val LABEL_FROM = 1.35f
private const val LABEL_TO = 1.95f
private const val LABEL_GROW = 0.25f
private const val IMAGE_FADE = 0.35f

// autoPlay: a scripted pinch walks the ladder, each move taking `span` s on an ease-in-out, from
// the 3 column grid back to it, every LOOP s. Two touch marks show the fingers while it moves.
private const val LOOP = 12f
private val ScriptAt = floatArrayOf(1.4f, 3.8f, 6.6f, 9.8f)
private val ScriptSpan = floatArrayOf(1.2f, 1.2f, 1.7f, 1.2f)
private val ScriptTo = floatArrayOf(2f, 3f, 0f, 1f)
// Where the loop stands at rest on each rung, for picking it up after a touch.
private val ScriptRest = floatArrayOf(8.3f, 0f, 2.6f, 5.0f)
private const val USER_HOLD = 3f
// The anchored plate the script pinches over (its middle stays under the fingers).
private const val SCRIPT_ANCHOR = 7
private const val TOUCH_RADIUS = 21f
private const val TOUCH_FADE = 0.25f

// The header: the title band is HEADER dp below the safe area, then the grid begins.
private const val HEADER = 78f
private const val EDGE_FADE = 22f

// Gestures: a drag scrolls after PAN_SLOP dp, a pinch starts once the fingers' spread has changed
// by PINCH_SLOP, and a second tap within DOUBLE_TAP_MS of the first makes a double tap.
private const val PAN_SLOP = 4f
private const val PINCH_SLOP = 0.03f
private const val TAP_MS = 500L
private const val DOUBLE_TAP_MS = 260L
private const val DOUBLE_TAP_SLOP = 30f

private class Loaded(val image: Bitmap, val at: Float) {
    val shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
}

private fun clamp(value: Float, lo: Float, hi: Float) = min(hi, max(lo, value))

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Resistance past an edge: the further it is pulled, the less it gives, never past `max`. */
private fun rubber(offset: Float, max: Float): Float {
    val sign = if (offset < 0f) -1f else 1f
    return sign * max * (1f - 1f / (abs(offset) / max + 1f))
}

/** `value` kept within lo..hi, with the part beyond either edge given back with resistance. */
private fun rubberClamp(value: Float, lo: Float, hi: Float, max: Float): Float = when {
    value < lo -> lo + rubber(value - lo, max)
    value > hi -> hi + rubber(value - hi, max)
    else -> value
}

private fun easeInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

// Reanimated's default timing curve, ease in and out (quadratic).
private fun quadInOut(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f

/** A plate's frame: x, y, width, height and corner radius. */
private class Frame {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f
    var r = 0f
}

/** The screen the grid lies on: its size, and where the grid starts and how much room it leaves below. */
private class Stage {
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f
}

/** The nearest rung to `z`; a NaN z (which roundToInt throws on) counts as the opening rung. */
private fun nearestRung(z: Float): Int = if (z.isNaN()) START_RUNG.toInt() else clamp(z, 0f, LAST.toFloat()).roundToInt()

/** The lower rung for a place `z` on the ladder. */
private fun lower(z: Float) = min(floor(clamp(z, 0f, LAST.toFloat())).toInt(), LAST - 1)

/** Plate `i`'s frame on rung `k`, in content coordinates (before scrolling). */
private fun slot(k: Int, i: Int, width: Float, top: Float, out: Frame) {
    val r = Ladder[k]
    val w = (width - r.margin * 2f - r.gap * (r.cols - 1)) / r.cols
    val h = w * r.aspect
    out.x = r.margin + (i % r.cols) * (w + r.gap)
    out.y = top + (i / r.cols) * (h + r.label + r.gap)
    out.w = w
    out.h = h
    out.r = r.radius
}

private val ScratchA = Frame()
private val ScratchB = Frame()

/** Plate `i`'s frame at `z`, between the two rungs on either side, in content coordinates. */
private fun between(z: Float, i: Int, width: Float, top: Float, out: Frame) {
    val k = lower(z)
    val f = clamp(z, 0f, LAST.toFloat()) - k
    slot(k, i, width, top, ScratchA)
    slot(k + 1, i, width, top, ScratchB)
    out.x = mix(ScratchA.x, ScratchB.x, f)
    out.y = mix(ScratchA.y, ScratchB.y, f)
    out.w = mix(ScratchA.w, ScratchB.w, f)
    out.h = mix(ScratchA.h, ScratchB.h, f)
    out.r = mix(ScratchA.r, ScratchB.r, f)
}

/** The grid's full height on rung `k`, header and bottom margin included. */
private fun gridLength(k: Int, n: Int, s: Stage): Float {
    val r = Ladder[k]
    val w = (s.width - r.margin * 2f - r.gap * (r.cols - 1)) / r.cols
    return s.top + ceil(n.toFloat() / r.cols) * (w * r.aspect + r.label + r.gap) - r.gap + s.bottom
}

/** How far the grid can scroll at `z`. */
private fun maxScroll(z: Float, n: Int, s: Stage): Float {
    val k = lower(z)
    val f = clamp(z, 0f, LAST.toFloat()) - k
    return max(0f, mix(gridLength(k, n, s), gridLength(k + 1, n, s), f) - s.height)
}

/** Where on the ladder a pinch is, from its log column count, with resistance past either end. */
private fun rungForLog(logCols: Float): Float {
    if (logCols >= LogCols[0]) return -rubber((logCols - LogCols[0]) / (LogCols[0] - LogCols[1]), OVERPINCH)
    if (logCols <= LogCols[LAST]) return LAST + rubber((LogCols[LAST] - logCols) / (LogCols[LAST - 1] - LogCols[LAST]), OVERPINCH)
    for (k in 0 until LAST) {
        if (logCols <= LogCols[k] && logCols >= LogCols[k + 1]) return k + (LogCols[k] - logCols) / (LogCols[k] - LogCols[k + 1])
    }
    return LAST.toFloat()
}

private fun logForRung(z: Float): Float {
    val k = lower(z)
    return mix(LogCols[k], LogCols[k + 1], clamp(z, 0f, LAST.toFloat()) - k)
}

/** How many rungs one unit of log columns is worth around `z`. */
private fun rungsPerLog(z: Float): Float {
    val k = lower(z)
    return 1f / (LogCols[k] - LogCols[k + 1])
}

/** The column count at `z`, for the touch marks' spread. */
private fun columnsAt(z: Float) = exp(logForRung(z))

/**
 * A value driven per frame the way Reanimated drives `withSpring({ duration, dampingRatio })`,
 * `withTiming` and `withDecay` with a rubber band, so the grid moves on the same curves as in the
 * React Native version.
 */
private class DrivenValue(initial: Float = 0f) {
    var value = initial
        private set
    private var mode = 0 // 0 still, 1 spring, 2 timing, 3 decay
    private var to = 0f
    private var velocity = 0f
    private var zeta = 0f
    private var omega0 = 0f
    private var omega1 = 0f
    private var stiffness = 0f
    private var initialEnergy = 0f
    private var from = 0f
    private var time = 0f
    private var duration = 0f
    private var easing: (Float) -> Float = { it }
    private var lo = 0f
    private var hi = 0f
    private var factor = 0f
    private var springing = false

    val moving get() = mode != 0

    /** Puts the value somewhere at once, stopping whatever was moving it. */
    fun set(next: Float) {
        value = next
        stop()
    }

    /** Stops whatever is moving the value, where it is. */
    fun stop() {
        mode = 0
        velocity = 0f
    }

    fun springTo(target: Float, speed: Float, durationMs: Float, dampingRatio: Float) {
        var v = (if (mode == 1) velocity else 0f) + speed
        // Speed that would carry the first frame away from the target is dropped.
        if ((target > value && v < 0f) || (target < value && v > 0f)) v = 0f
        val x0 = value - target
        mode = 1
        to = target
        velocity = v
        zeta = dampingRatio
        stiffness = stiffnessFor(x0, v, durationMs, dampingRatio)
        omega0 = sqrt(stiffness)
        omega1 = if (zeta < 1f) omega0 * sqrt(1f - zeta * zeta) else 0f
        initialEnergy = energy(x0, v)
    }

    fun timeTo(target: Float, durationMs: Float, curve: (Float) -> Float) {
        mode = 2
        from = value
        to = target
        time = 0f
        duration = durationMs / 1000f
        velocity = 0f
        easing = curve
    }

    /** The speed fades (deceleration 0.998); past either bound it is pulled back by `factor` of the overshoot every frame. */
    fun decay(speed: Float, lo: Float, hi: Float, factor: Float) {
        mode = 3
        velocity = speed
        time = 0f
        this.lo = lo
        this.hi = hi
        this.factor = factor
        springing = false
    }

    fun step(dt: Float) {
        when (mode) {
            1 -> {
                val x0 = value - to
                val v0 = velocity
                if (zeta < 1f) {
                    val sin1 = kotlin.math.sin(omega1 * dt)
                    val cos1 = kotlin.math.cos(omega1 * dt)
                    val envelope = exp(-zeta * omega0 * dt)
                    val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
                    value = to + frag
                    velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
                } else {
                    val envelope = exp(-omega0 * dt)
                    value = to + envelope * (x0 + (v0 + omega0 * x0) * dt)
                    velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
                }
                if (initialEnergy == 0f || energy(to - value, velocity) / initialEnergy <= ENERGY_THRESHOLD) {
                    value = to
                    velocity = 0f
                    mode = 0
                }
            }
            2 -> {
                time += dt
                val p = clamp(time / duration, 0f, 1f)
                value = from + (to - from) * easing(p)
                if (p >= 1f) mode = 0
            }
            3 -> {
                val ms = dt * 1000f
                time += ms
                val nearest = if (abs(value - lo) < abs(value - hi)) lo else hi
                val derivative = if (value < lo || value > hi) value - nearest else 0f
                val v = velocity * exp(-(1f - 0.998f) * time * 0.1f) - derivative * factor
                velocity = v
                when {
                    abs(derivative) > 0.1f -> {
                        springing = true
                        value += v * ms / 1000f
                    }
                    springing -> {
                        value = nearest
                        mode = 0
                    }
                    abs(v) < 1f -> mode = 0
                    else -> value += v * ms / 1000f
                }
            }
        }
    }

    private fun energy(x: Float, v: Float) = 0.5f * stiffness * x * x + 0.5f * v * v

    companion object {
        const val ENERGY_THRESHOLD = 6e-9f

        /** The stiffness Reanimated solves for so a spring of mass 1 settles in 1.5 × the duration. */
        fun stiffnessFor(x0: Float, v0: Float, durationMs: Float, zeta: Float): Float {
            if (x0 == 0f && v0 == 0f) return 0f
            val settling = durationMs * 1.5 / 1000.0
            fun diff(k: Double): Double {
                val omega = sqrt(k) * zeta
                val a = x0 + (v0 + x0 * omega) * settling
                val e = exp(-omega * settling)
                val xt = a * e
                val vt = a * e * -omega + (v0 + x0 * omega) * e
                val e0 = 0.5 * k * x0 * x0 + 0.5 * v0 * v0
                val et = 0.5 * k * xt * xt + 0.5 * vt * vt
                return et / e0 - ENERGY_THRESHOLD
            }
            var lo = Math.ulp(1.0)
            var hi = 8e3
            val direction = if (diff(hi) >= diff(lo)) 1 else -1
            var current = (lo + hi) / 2
            var left = 100
            while (abs(diff(current)) > ENERGY_THRESHOLD * 1e-3 && left > 0) {
                left -= 1
                if (diff(current) * direction < 0) lo = current else hi = current
                current = (lo + hi) / 2
            }
            return current.toFloat()
        }
    }
}

/** Everything the frame loop, the gestures and the picture share. */
private class GridMotion(val count: Int) {
    // Bumped every frame so the picture is drawn again.
    var tick by mutableFloatStateOf(0f)
    var now = 0f
    val thumbs = arrayOfNulls<Loaded>(count)
    val sharp = arrayOfNulls<Loaded>(count)
    val requested = HashSet<Int>()
    val stage = Stage()
    var safeTop = 0f
    var safeBottom = 0f
    // Where the grid is on the ladder (0 = 5 columns … 3 = 1 column).
    val z = DrivenValue(START_RUNG)
    // The anchor: a point on one plate (in shares of its frame) that sits at screen height `focalY`.
    // Scrolling moves `focalY`; a pinch re-anchors under the fingers, so the plate stays under them.
    var anchor = SCRIPT_ANCHOR
    var anchorU = 0.5f
    var anchorV = 0.5f
    var focalX = 0f
    val focalY = DrivenValue()
    val sideways = DrivenValue()
    var ready = false
    // Gesture bookkeeping.
    var pinching = false
    var pinchLog = 0f
    var fromX = 0f
    var fromY = 0f
    var fromFocalY = 0f
    var fromAnchorX = 0f
    var fromSideways = 0f
    var panFrom = 0f
    var lastTapUp = 0L
    var lastTap = Offset.Zero
    // Under reduced motion the grid fades out, changes and fades in.
    val fade = DrivenValue(1f)
    var fadeTo = -1f
    var fadeFocal = 0f
    // The self-playing loop.
    var looping = false
    var clock = 0f
    var hold = 0f
    var scriptFocal = -1f
    var wantedFirst = -1
    var wantedLast = -1
    val frame = Frame()

    /** The scroll that puts the anchor at `focalY`, rubber-banded past either end. */
    fun scroll(): Float {
        between(z.value, anchor, stage.width, stage.top, frame)
        val raw = frame.y + frame.h * anchorV - focalY.value
        return rubberClamp(raw, 0f, maxScroll(z.value, count, stage), RUBBER_MAX)
    }

    /** Plate `i`'s frame on screen: between the rungs, scrolled, slid sideways, and grown past the ends. */
    fun onScreen(i: Int, scroll: Float, out: Frame) {
        val zNow = z.value
        between(zNow, i, stage.width, stage.top, out)
        out.x += sideways.value
        out.y -= scroll
        val over = if (zNow < 0f) zNow else if (zNow > LAST) zNow - LAST else 0f
        if (over != 0f) {
            val s = 1f + over * OVERPINCH_SCALE
            out.x = focalX + (out.x - focalX) * s
            out.y = focalY.value + (out.y - focalY.value) * s
            out.w *= s
            out.h *= s
        }
    }

    /** The `focalY` that keeps the anchor where it is at rung `to`, moved so the grid stays in range. */
    fun focalFor(to: Float, focal: Float): Float {
        between(to, anchor, stage.width, stage.top, frame)
        val y = frame.y + frame.h * anchorV
        return y - clamp(y - focal, 0f, maxScroll(to, count, stage))
    }

    /** Re-anchors on the plate under (or nearest to) a point on screen, without moving anything. */
    fun anchorAt(px: Float, py: Float) {
        val scroll = scroll()
        var best = 0
        var bestDistance = Float.MAX_VALUE
        var u = 0.5f
        var w = 0.5f
        val f = Frame()
        for (i in 0 until count) {
            onScreen(i, scroll, f)
            val dx = maxOf(f.x - px, 0f, px - f.x - f.w)
            val dy = maxOf(f.y - py, 0f, py - f.y - f.h)
            val d = dx * dx + dy * dy
            if (d < bestDistance) {
                bestDistance = d
                best = i
                u = (px - f.x) / f.w
                w = (py - f.y) / f.h
            }
        }
        // The new anchor's scroll has to match what is on screen now.
        between(z.value, best, stage.width, stage.top, f)
        anchor = best
        anchorU = u
        anchorV = w
        focalX = px
        focalY.set(f.y + f.h * w - scroll)
    }
}

/**
 * A herbarium of botanical plates whose column count follows a pinch continuously: spreading the
 * fingers walks 5 → 3 → 2 → 1 columns and pinching walks back, every tile sliding and growing
 * between the two neighbouring grids around the fingers rather than jumping. On release the grid
 * springs to the nearest column count with a tap of haptics. Names fade in under the plates at
 * two columns and one. A double tap toggles one column and three; a drag scrolls.
 *
 * @param autoPlay Pinches out to 2 and 1 columns, back in to 5 and out to 3 again, every 12 s.
 * @param background The paper.
 * @param ink The ink.
 * @param onColumnsChange Called with the column count each time the grid settles on a new one.
 */
@Composable
fun PinchDensityGrid(
    modifier: Modifier = Modifier,
    plates: List<Plate> = HerbariumPlates,
    autoPlay: Boolean = false,
    background: Color = Paper,
    ink: Color = Ink,
    onColumnsChange: ((Int) -> Unit)? = null,
) {
    val reducedMotion = com.ngocdevv.nativecook.shared.rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val n = plates.size
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val changing by rememberUpdatedState(onColumnsChange)
    val motion = remember(plates) { GridMotion(n) }
    val paints = remember { GridPaints() }
    val scope = rememberCoroutineScope()

    // Read during composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect {
        motion.safeTop = safeTop
        motion.safeBottom = safeBottom
    }

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.clock = 0f
        motion.hold = 0f
        motion.scriptFocal = -1f
    }

    val loader = remember(context) { SingletonImageLoader.get(context) }
    suspend fun fetch(uri: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(Size.ORIGINAL)
            // A software bitmap can carry mipmaps, which keep the small tiles from shimmering.
            .allowHardware(false)
            .build()
        val result = loader.execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap().also { it.setHasMipMap(true) }
    }

    // The small copies, all at once.
    LaunchedEffect(motion) {
        plates.forEachIndexed { i, item ->
            launch {
                val image = fetch(unsplash(item.id, THUMB_WIDTH)) ?: return@launch
                motion.thumbs[i] = Loaded(image, motion.now)
            }
        }
    }

    // The sharp copies, only for the plates near the screen at two columns or one.
    fun wantSharp(first: Int, last: Int) {
        val m = motion
        val drop = m.requested.filter { it < first - SHARP_KEEP || it > last + SHARP_KEEP }
        for (i in drop) {
            m.requested.remove(i)
            m.sharp[i] = null
        }
        for (i in max(0, first - SHARP_AHEAD)..min(n - 1, last + SHARP_AHEAD)) {
            if (!m.requested.add(i)) continue
            scope.launch {
                val image = fetch(unsplash(plates[i].id, SHARP_WIDTH))
                if (image == null) {
                    m.requested.remove(i)
                    return@launch
                }
                if (m.requested.contains(i)) m.sharp[i] = Loaded(image, m.now)
            }
        }
    }

    /** Springs to rung `to`, keeping the anchor in place and the grid in range. */
    fun settle(to: Int, velocity: Float, user: Boolean) {
        val m = motion
        val focal = m.focalFor(to.toFloat(), m.focalY.value)
        if (reducedMotion) {
            m.fade.timeTo(0f, 140f, ::quadInOut)
            m.fadeTo = to.toFloat()
            m.fadeFocal = focal
        } else {
            m.z.springTo(to.toFloat(), velocity, SETTLE_MS, SETTLE_RATIO)
            m.focalY.springTo(focal, 0f, SCROLL_MS, SCROLL_RATIO)
            m.sideways.springTo(0f, 0f, SCROLL_MS, SCROLL_RATIO)
        }
        if (user) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            changing?.invoke(Ladder[to].cols)
        }
    }

    fun touched() {
        motion.hold = USER_HOLD
        motion.scriptFocal = -1f
    }

    LaunchedEffect(motion) {
        var last = -1L
        val m = motion
        while (true) {
            withFrameNanos { frameTime ->
                val dt = if (last < 0) 1f / 60f else min((frameTime - last) / 1e9f, 0.064f)
                last = frameTime
                if (dt <= 0f || m.stage.width == 0f) return@withFrameNanos
                m.now += dt
                m.z.step(dt)
                m.focalY.step(dt)
                m.sideways.step(dt)
                m.fade.step(dt)
                if (m.fadeTo >= 0f && !m.fade.moving) {
                    // Faded out: change, then fade back in.
                    m.z.set(m.fadeTo)
                    m.focalY.set(m.fadeFocal)
                    m.sideways.set(0f)
                    m.fadeTo = -1f
                    m.fade.timeTo(1f, 200f, ::quadInOut)
                }
                if (!m.ready) {
                    // At rest on the first rung, scrolled to the top, anchored on the scripted plate.
                    between(START_RUNG, SCRIPT_ANCHOR, m.stage.width, m.stage.top, m.frame)
                    m.anchor = SCRIPT_ANCHOR
                    m.anchorU = 0.5f
                    m.anchorV = 0.5f
                    m.focalX = m.frame.x + m.frame.w / 2f
                    m.focalY.set(m.frame.y + m.frame.h / 2f)
                    m.ready = true
                }

                // The script: z follows the eased moves, and the anchor stays at the height it
                // started at, unless that would scroll the grid out of range.
                if (m.hold > 0f) {
                    m.hold -= dt
                    if (m.hold <= 0f) m.clock = ScriptRest[nearestRung(m.z.value)]
                } else if (m.looping && !m.pinching) {
                    val t = (m.clock + dt) % LOOP
                    m.clock = t
                    if (m.scriptFocal < 0f) m.scriptFocal = m.focalY.value
                    var rung = START_RUNG
                    for (k in ScriptAt.indices) {
                        if (t >= ScriptAt[k]) {
                            val from = rung
                            rung = ScriptTo[k]
                            if (t < ScriptAt[k] + ScriptSpan[k]) rung = mix(from, ScriptTo[k], easeInOut((t - ScriptAt[k]) / ScriptSpan[k]))
                        }
                    }
                    m.z.set(rung)
                    m.focalY.set(m.focalFor(rung, m.scriptFocal))
                }

                // Which plates are on screen, for the sharp copies, reported only when it changes.
                if (m.z.value > LABEL_FROM) {
                    val scroll = m.scroll()
                    var first = -1
                    var lastShown = -1
                    for (i in 0 until n) {
                        m.onScreen(i, scroll, m.frame)
                        if (m.frame.y + m.frame.h < 0f || m.frame.y > m.stage.height) continue
                        if (first < 0) first = i
                        lastShown = i
                    }
                    if (first != m.wantedFirst || lastShown != m.wantedLast) {
                        m.wantedFirst = first
                        m.wantedLast = lastShown
                        if (first >= 0) wantSharp(first, lastShown)
                    }
                }
                m.tick = m.now
            }
        }
    }

    fun pinchStart(x: Float, y: Float) {
        val m = motion
        touched()
        m.pinching = true
        m.z.stop()
        m.focalY.stop()
        m.sideways.stop()
        m.anchorAt(x, y)
        between(m.z.value, m.anchor, m.stage.width, m.stage.top, m.frame)
        m.pinchLog = logForRung(clamp(m.z.value, 0f, LAST.toFloat()))
        m.fromX = x
        m.fromY = y
        m.fromFocalY = m.focalY.value
        m.fromAnchorX = m.frame.x + m.frame.w * m.anchorU
        m.fromSideways = m.sideways.value
    }

    fun pinchUpdate(scale: Float, x: Float, y: Float) {
        val m = motion
        val rung = rungForLog(m.pinchLog - PINCH_GAIN * ln(max(scale, 0.01f)))
        m.z.set(rung)
        m.focalX = x
        m.focalY.set(m.fromFocalY + (y - m.fromY))
        // The plate under the fingers drifts sideways as the columns change; follow part of it.
        between(rung, m.anchor, m.stage.width, m.stage.top, m.frame)
        val drift = m.fromAnchorX - (m.frame.x + m.frame.w * m.anchorU)
        m.sideways.set(m.fromSideways + rubber(drift * SIDEWAYS_FOLLOW + (x - m.fromX), SIDEWAYS_MAX))
    }

    fun pinchEnd(scale: Float, velocity: Float) {
        val m = motion
        touched()
        m.pinching = false
        // Throwing the pinch carries on along the ladder; it lands on the rung it was heading for.
        val zNow = m.z.value
        // Spreading (scale rising) lowers the log column count, which moves up the ladder.
        // A velocity the tracker could not work out (NaN) is taken as no throw: roundToInt throws on NaN.
        val raw = PINCH_GAIN * (velocity / max(scale, 0.01f)) * rungsPerLog(zNow)
        val speed = if (raw.isFinite()) clamp(raw, -8f, 8f) else 0f
        val to = nearestRung(zNow + clamp(speed * PROJECT, -PROJECT_MAX, PROJECT_MAX))
        settle(to, speed, true)
    }

    fun panStart() {
        if (motion.pinching) return
        touched()
        motion.focalY.stop()
        motion.panFrom = motion.focalY.value
    }

    fun panUpdate(ty: Float) {
        if (motion.pinching) return
        motion.focalY.set(motion.panFrom + ty)
    }

    fun panEnd(vy: Float) {
        val m = motion
        if (m.pinching) return
        touched()
        // The anchor's height that scrolls the grid to its end, and to its top.
        val lo = m.focalFor(m.z.value, -1e6f)
        val hi = m.focalFor(m.z.value, 1e6f)
        m.focalY.decay(if (vy.isFinite()) vy else 0f, lo, hi, 0.7f)
    }

    fun doubleTap(x: Float, y: Float) {
        val m = motion
        touched()
        m.z.stop()
        m.focalY.stop()
        m.anchorAt(x, y)
        val rung = nearestRung(m.z.value)
        settle(if (rung == ONE_COLUMN) THREE_COLUMNS else ONE_COLUMN, 0f, true)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = "Herbarium, $n botanical plates. Pinch to change the number of columns; double tap to switch between one and three" }
            .pointerInput(motion, reducedMotion) {
                val d = density.density
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)
                    // 0 waiting, 1 dragging with one finger, 2 pinching, 3 nothing more to do.
                    var mode = 0
                    var many = false
                    var startSpan = 0f
                    var pinchActive = false
                    var scale = 1f
                    val scaleTracker = VelocityTracker1D(isDataDifferential = false)
                    try { while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            val lifted = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                            when {
                                mode == 1 -> {
                                    tracker.addPointerInputChange(lifted)
                                    panEnd(tracker.calculateVelocity().y / d)
                                }
                                mode == 2 && pinchActive -> pinchEnd(scale, scaleTracker.calculateVelocity())
                                mode == 0 && !many && lifted.uptimeMillis - down.uptimeMillis <= TAP_MS -> {
                                    // A tap: the second within DOUBLE_TAP_MS of the first, near it, is a double tap.
                                    val at = lifted.position / d
                                    val m = motion
                                    if (down.uptimeMillis - m.lastTapUp <= DOUBLE_TAP_MS && (at - m.lastTap).getDistance() < DOUBLE_TAP_SLOP) {
                                        m.lastTapUp = 0L
                                        doubleTap(at.x, at.y)
                                    } else {
                                        m.lastTapUp = lifted.uptimeMillis
                                        m.lastTap = at
                                    }
                                }
                            }
                            event.changes.forEach(PointerInputChange::consume)
                            break
                        }
                        if (pressed.size >= 2) {
                            many = true
                            if (mode == 1) {
                                // A second finger ends the drag; the pinch takes over from here.
                                panEnd(0f)
                            }
                            val a = pressed[0].position
                            val b = pressed[1].position
                            val span = hypot(a.x - b.x, a.y - b.y)
                            val cx = (a.x + b.x) / 2f / d
                            val cy = (a.y + b.y) / 2f / d
                            if (mode != 2 && mode != 3) {
                                mode = 2
                                startSpan = span
                            }
                            if (mode == 2) {
                                scale = if (startSpan > 0f) span / startSpan else 1f
                                if (!pinchActive && abs(scale - 1f) > PINCH_SLOP) {
                                    pinchActive = true
                                    pinchStart(cx, cy)
                                }
                                if (pinchActive) {
                                    scaleTracker.addDataPoint(pressed[0].uptimeMillis, scale)
                                    pinchUpdate(scale, cx, cy)
                                }
                            }
                            event.changes.forEach(PointerInputChange::consume)
                            continue
                        }
                        // One finger down.
                        if (mode == 2) {
                            if (pinchActive) pinchEnd(scale, scaleTracker.calculateVelocity())
                            mode = 3
                        }
                        val change = pressed[0]
                        if (change.id == down.id) tracker.addPointerInputChange(change)
                        val ty = (change.position.y - down.position.y) / d
                        val tx = (change.position.x - down.position.x) / d
                        if (mode == 0 && !many && sqrt(tx * tx + ty * ty) >= PAN_SLOP) {
                            mode = 1
                            panStart()
                        }
                        if (mode == 1) {
                            change.consume()
                            panUpdate(ty)
                        }
                    } } finally {
                        // A pinch cancelled mid-way (the gesture torn down) never reached pinchEnd; land
                        // it on the nearest rung so the drag and the script are not locked out.
                        if (pinchActive && motion.pinching) {
                            motion.pinching = false
                            settle(nearestRung(motion.z.value), 0f, false)
                        }
                    }
                }
            },
    ) {
        val m = motion
        m.stage.width = maxWidth.value
        m.stage.height = maxHeight.value
        m.stage.top = safeTop + HEADER
        m.stage.bottom = safeBottom + 28f
        Canvas(Modifier.fillMaxSize()) {
            m.tick
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { drawGrid(it.nativeCanvas, m, plates, paints, background.toArgb(), ink.toArgb()) }
            }
        }
    }
}

private fun sansSerif(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

/** The paints, fonts and scratch objects the picture reuses every frame. */
private class GridPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    val shade = Paint()
    val matrix = Matrix()
    val rect = RectF()
    val frame = Frame()
    val title = text(30f, 500)
    val small = text(10f, 500)
    val name = text(14f, 500)
    val note = text(12f, 400)
    val rung = text(12f, 500)
    private var gradientKey = Float.NaN
    private var gradientColor = 0
    var headGradient: LinearGradient? = null
    var footGradient: LinearGradient? = null

    private fun text(size: Float, weight: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = sansSerif(weight)
    }

    /** The fades under the header and over the home indicator, made again only when they move. */
    fun gradients(head: Float, foot: Float, height: Float, color: Int) {
        val key = head * 31f + foot * 7f + height
        if (key == gradientKey && color == gradientColor) return
        gradientKey = key
        gradientColor = color
        val clear = color and 0x00FFFFFF
        headGradient = LinearGradient(0f, head, 0f, head + EDGE_FADE, color, clear, Shader.TileMode.CLAMP)
        footGradient = LinearGradient(0f, foot, 0f, height, clear, color, Shader.TileMode.CLAMP)
    }
}

// Skia's `measureText`, which the React Native version spaces and cuts its labels by, gives a
// glyph's bounds rather than its advance, and on the iPhone it was made on those bounds stand clear
// of the ink: each run takes about two points more than its advance. This keeps the same spacing.
private const val GLYPH_PAD = 2f

private fun measure(paint: Paint, text: String) = paint.measureText(text) + GLYPH_PAD

private fun advance(paint: Paint, letter: String): Float =
    if (letter != " ") measure(paint, letter) else paint.measureText("a a") - paint.measureText("aa")

private fun trackedWidth(paint: Paint, text: String, tracking: Float): Float {
    var width = 0f
    for (letter in text) width += advance(paint, letter.toString()) + tracking
    return width - tracking
}

private fun drawTracked(canvas: android.graphics.Canvas, text: String, x: Float, y: Float, tracking: Float, paint: Paint) {
    var at = x
    for (letter in text) {
        val s = letter.toString()
        canvas.drawText(s, at, y, paint)
        at += advance(paint, s) + tracking
    }
}

/** A label cut to `max` dp, with an ellipsis when it does not fit. */
private fun fit(paint: Paint, text: String, max: Float): String {
    if (measure(paint, text) <= max) return text
    var cut = text
    while (cut.length > 1 && measure(paint, "$cut…") > max) cut = cut.dropLast(1)
    return "${cut.trimEnd()}…"
}

private fun alphaOf(value: Float) = if (value.isNaN()) 0 else (clamp(value, 0f, 1f) * 255f).roundToInt()

/** One plate: its photo cropped to fill a rounded frame, the sharp copy fading in over the small one. */
private fun drawPlate(canvas: android.graphics.Canvas, f: Frame, thumb: Loaded?, sharp: Loaded?, now: Float, paints: GridPaints, alpha: Float) {
    val r = max(0f, f.r)
    paints.rect.set(f.x, f.y, f.x + f.w, f.y + f.h)
    paints.fill.color = TileTone.toArgb()
    paints.fill.alpha = alphaOf(alpha)
    canvas.drawRoundRect(paints.rect, r, r, paints.fill)
    for (copy in arrayOf(thumb, sharp)) {
        if (copy == null) continue
        val shown = clamp((now - copy.at) / IMAGE_FADE, 0f, 1f)
        if (shown <= 0f) continue
        val iw = copy.image.width.toFloat()
        val ih = copy.image.height.toFloat()
        val scale = max(f.w / iw, f.h / ih)
        paints.matrix.setScale(scale, scale)
        paints.matrix.postTranslate(f.x + f.w / 2f - iw * scale / 2f, f.y + f.h / 2f - ih * scale / 2f)
        copy.shader.setLocalMatrix(paints.matrix)
        paints.art.shader = copy.shader
        paints.art.alpha = alphaOf(alpha * shown)
        canvas.drawRoundRect(paints.rect, r, r, paints.art)
    }
    paints.art.shader = null
}

/** The plate's name, its note and its number under it, set larger at one column. */
private fun drawLabel(canvas: android.graphics.Canvas, f: Frame, item: Plate, i: Int, paints: GridPaints, ink: Int, shown: Float, grow: Float) {
    val s = 1f + grow * LABEL_GROW
    canvas.save()
    canvas.translate(f.x, f.y + f.h)
    canvas.scale(s, s)
    val room = f.w / s
    val number = "No. %02d".format(i + 1)
    val numberWidth = trackedWidth(paints.small, number, 0.6f)
    paints.name.color = ink
    paints.name.alpha = alphaOf(shown)
    canvas.drawText(fit(paints.name, item.name, room - numberWidth - 8f), 1f, 19f, paints.name)
    paints.note.color = ink
    paints.note.alpha = alphaOf(shown * 0.55f)
    canvas.drawText(fit(paints.note, item.note, room - 2f), 1f, 35f, paints.note)
    paints.small.color = ink
    paints.small.alpha = alphaOf(shown * 0.55f)
    drawTracked(canvas, number, room - numberWidth, 18f, 0.6f, paints.small)
    canvas.restore()
}

private fun drawGrid(canvas: android.graphics.Canvas, m: GridMotion, plates: List<Plate>, paints: GridPaints, background: Int, ink: Int) {
    val width = m.stage.width
    val height = m.stage.height
    canvas.drawColor(background)
    if (!m.ready) return
    val zc = clamp(m.z.value, 0f, LAST.toFloat())
    val alpha = m.fade.value
    val labels = clamp((zc - LABEL_FROM) / (LABEL_TO - LABEL_FROM), 0f, 1f) * alpha
    val grow = clamp(zc - 2f, 0f, 1f)
    val scroll = m.scroll()
    val f = paints.frame
    for (i in plates.indices) {
        m.onScreen(i, scroll, f)
        if (f.y > height || f.y + f.h + 60f < 0f || f.x > width || f.x + f.w < 0f) continue
        drawPlate(canvas, f, m.thumbs[i], m.sharp[i], m.now, paints, alpha)
        if (labels > 0.01f) drawLabel(canvas, f, plates[i], i, paints, ink, labels, grow)
    }

    // The paper runs under the header and the home indicator, fading into the grid.
    val head = m.stage.top - EDGE_FADE + 8f
    val foot = height - m.safeBottom - EDGE_FADE
    paints.gradients(head, foot, height, background)
    paints.shade.shader = null
    paints.shade.color = background
    canvas.drawRect(0f, 0f, width, head, paints.shade)
    paints.shade.shader = paints.headGradient
    canvas.drawRect(0f, head, width, head + EDGE_FADE, paints.shade)
    paints.shade.shader = paints.footGradient
    canvas.drawRect(0f, foot, width, height, paints.shade)
    paints.shade.shader = null

    // Title, count and the ladder, the current rung marked by a pill that slides between them.
    val titleY = m.safeTop + 34f
    paints.title.color = ink
    canvas.drawText("Herbarium", 18f, titleY, paints.title)
    paints.small.color = ink
    paints.small.alpha = alphaOf(0.5f)
    drawTracked(canvas, "${plates.size} PLATES · PINCH", 20f, titleY + 20f, 1.1f, paints.small)
    val step = 28f
    val right = width - 18f
    val left = right - step * Ladder.size
    val pillX = left + step * (zc + 0.5f)
    paints.fill.color = ink
    paints.rect.set(pillX - 12f, titleY - 19f, pillX + 12f, titleY + 5f)
    canvas.drawRoundRect(paints.rect, 12f, 12f, paints.fill)
    for (k in Ladder.indices) {
        val label = Ladder[k].cols.toString()
        val near = clamp(1f - abs(zc - k), 0f, 1f)
        val x = left + step * (k + 0.5f) - measure(paints.rung, label) / 2f
        paints.rung.color = ink
        paints.rung.alpha = alphaOf(0.45f * (1f - near))
        canvas.drawText(label, x, titleY - 3f, paints.rung)
        paints.rung.color = background
        paints.rung.alpha = alphaOf(near)
        canvas.drawText(label, x, titleY - 3f, paints.rung)
    }
    paints.small.color = ink
    paints.small.alpha = alphaOf(0.5f)
    val cols = "COLUMNS"
    drawTracked(canvas, cols, right - trackedWidth(paints.small, cols, 1.1f) - 6f, titleY + 20f, 1.1f, paints.small)

    // The scripted pinch's two fingers, while it moves.
    if (m.looping && m.hold <= 0f) {
        for (k in ScriptAt.indices) {
            val into = m.clock - ScriptAt[k]
            val shown = clamp(min((into + TOUCH_FADE) / TOUCH_FADE, (ScriptSpan[k] + TOUCH_FADE - into) / TOUCH_FADE), 0f, 1f)
            if (shown <= 0f) continue
            val fromRung = ScriptTo[(k + ScriptAt.size - 1) % ScriptAt.size]
            val outward = ScriptTo[k] > fromRung
            val spread = (if (outward) 34f else 120f) * (columnsAt(fromRung) / columnsAt(zc)).pow(0.85f)
            val d = clamp(spread, 26f, 150f) * sqrt(0.5f)
            for (sign in intArrayOf(-1, 1)) {
                val fx = m.focalX + sign * d
                val fy = m.focalY.value + sign * d
                paints.fill.color = android.graphics.Color.WHITE
                paints.fill.alpha = alphaOf(0.5f * shown)
                canvas.drawCircle(fx, fy, TOUCH_RADIUS, paints.fill)
                paints.stroke.color = ink
                paints.stroke.alpha = alphaOf(0.45f * shown)
                canvas.drawCircle(fx, fy, TOUCH_RADIUS, paints.stroke)
            }
        }
    }
}
