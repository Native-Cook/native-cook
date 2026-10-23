package com.ngocdevv.nativecook.blocks.media.flipbookscrub

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/** One leaf of the book: a photo printed on it, its caption and the chapter it belongs to. */
data class FlipbookPage(val id: String, val title: String, val chapter: String)

private val Pages = listOf(
    FlipbookPage("photo-1547153760-18fc86324498", "Arabesque", "The Dancers"),
    FlipbookPage("photo-1511715282680-fbf93a50e721", "Grand jeté", "The Dancers"),
    FlipbookPage("photo-1529229504105-4ea795dcbf59", "Relevé", "The Dancers"),
    FlipbookPage("photo-1563775957383-f7b1abbe63ec", "Pirouette", "The Dancers"),
    FlipbookPage("photo-1525426250015-4e6473cdbf17", "Port de bras", "The Dancers"),
    FlipbookPage("photo-1531318701087-32c11653dd77", "Attitude", "The Dancers"),
    FlipbookPage("photo-1611879531844-24b7ddf40b26", "Développé", "The Dancers"),
    FlipbookPage("photo-1524330685423-3e1966445abe", "Tour en l’air", "The Dancers"),
    FlipbookPage("photo-1690267286998-bae912638f40", "Pas de chat", "The Dancers"),
    FlipbookPage("photo-1593078886626-aabc65c1d311", "Fouetté", "The Dancers"),
    FlipbookPage("photo-1681152322586-7ddde43866a4", "Plié", "The Dancers"),
    FlipbookPage("photo-1529154547048-4d1e71e6a760", "Révérence", "The Dancers"),
    FlipbookPage("photo-1571008887538-b36bb32f4571", "Set", "The Runners"),
    FlipbookPage("photo-1727094141271-9bea5bc8c757", "Drive", "The Runners"),
    FlipbookPage("photo-1744060204728-f68e434a3edf", "Stride", "The Runners"),
    FlipbookPage("photo-1480179087180-d9f0ec044897", "Kick", "The Runners"),
    FlipbookPage("photo-1759674861540-afed9f86f94a", "Cadence", "The Runners"),
    FlipbookPage("photo-1461896836934-ffe607ba8211", "Lean", "The Runners"),
    FlipbookPage("photo-1581889470536-467bdbe30cd0", "Pace", "The Runners"),
    FlipbookPage("photo-1594882645126-14020914d58d", "Surge", "The Runners"),
    FlipbookPage("photo-1698671823406-035c77ff6fcd", "Float", "The Runners"),
    FlipbookPage("photo-1728532483490-708f6562b738", "Reach", "The Runners"),
    FlipbookPage("photo-1758922769578-68c5ba000d87", "Push", "The Runners"),
    FlipbookPage("photo-1744706908605-ac30eb45f98c", "Finish", "The Runners"),
)

// Each photo is fetched at about three times the size it is printed, cropped to the print.
private fun photoUri(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=750&h=800&q=80"

// The table, the book's paper and its binding.
private val TABLE = Color(0xFF17130F)
private const val TABLE_LIT = 0xFF2C241D.toInt()
private const val TABLE_DARK = 0xFF17130F.toInt()
private const val PAPER = 0xFFF2ECE0.toInt()
private const val PAPER_BACK = 0xFFF8F3EA.toInt()
private const val PAPER_EDGE = 0xFFD9D0C0.toInt()
private const val BLANK = 0xFFE4DCCD.toInt()
private const val INK = 0xFF2B2620.toInt()
private const val TAPE = 0xFF2A1F19.toInt()
private const val TAPE_SHEEN = 0xFF4A382C.toInt()
private const val TAPE_DEEP = 0xFF1C1511.toInt()
private const val CHROME = 0xFFEFE6D8.toInt()

// The book, in dp: a leaf is the screen's width less SIDE a side, PAGE_ASPECT times as tall. It is
// bound along its top edge under a cloth tape TAPE_H deep, and the leaves under the top one show
// as a block THICK deep along its bottom edge. The photo is printed MARGIN in from the edges,
// with CAPTION_H under it for its caption.
private const val SIDE = 32f
private const val MAX_PAGE_W = 360f
private const val PAGE_ASPECT = 1.16f
private const val TAPE_H = 26f
private const val THICK = 18f
private const val MARGIN = 14f
private const val CAPTION_H = 34f
private const val CORNER = 3f
// The header above the book: the label line and the counter, from the top safe edge.
private const val LABEL_Y = 24f
private const val COUNTER_Y = 74f
private const val COUNTER_SIZE = 50f
// A leaf turning over the binding rises above it; this share of a leaf's height is kept clear
// between the header and the binding for it.
private const val RISE_ROOM = 0.36f

// The turn of one leaf, t = 0 (flat) … 1 (gone over the top). The thumb draws the leaf's
// bottom-right corner up along a path that bows inwards by BOW of the most the binding allows,
// and the leaf folds along the line halfway between the corner and the thumb. Past SQUASH_FROM
// the part above the binding folds down behind the book until it is gone at t = 1.
private const val BOW = 0.52
private const val SQUASH_FROM = 0.55
// The roll of paper along the fold, and the shadow the lifted part casts on the leaf under it.
private const val ROLL = 30.0
private const val FOLD_SHADOW = 34.0
private const val SHOW_THROUGH = 0.09

// A slow drag turns one leaf every DRAG_STEP dp; dragging up turns forwards. Faster, more leaves
// are in the air at once: SPREAD_BASE leaves at rest, up to SPREAD_MAX, SPREAD_RATE more per
// leaf per second. Past either end the book gives RUBBER leaves at most.
private const val DRAG_STEP = 46.0
private const val DRAG_SLOP = 4f
private const val TAP_MS = 300L
private const val SPREAD_BASE = 1.0
private const val SPREAD_MAX = 3.6
private const val SPREAD_RATE = 0.16
private const val SPEED_SMOOTH = 9.0
private const val RUBBER = 1.2
// After a release the book coasts, losing FRICTION of its speed per second, until it is slower
// than SETTLE_SPEED leaves/s; then a spring (stiffness, damping) lays the nearest leaf down.
private const val FRICTION = 1.7
private const val SETTLE_SPEED = 3.2
// Safety rails: a release is never faster than MAX_SPEED leaves/s, a coast that reaches an end
// hands at most END_SPEED leaves/s to the spring there, and the book's place never leaves the
// band SLACK leaves past either end, so one long frame cannot carry it off the book.
private const val MAX_SPEED = 60.0
private const val END_SPEED = 10.0
private const val SLACK = 1.6
private const val STIFFNESS = 230.0
private const val DAMPING = 23.0
// A tick under the thumb every TICK_EVERY leaves.
private const val TICK_EVERY = 3.0
private const val IMAGE_FADE = 0.4

// autoPlay (seconds): the thumb turns LEAVES leaves over one at a time, rests, and turns them back
// one at a time. Each turn takes TURN s on a cubic ease-in-out, and a new one starts every STEP s,
// so the book lies still on every leaf for STEP − TURN s before the next turn begins.
private const val LEAVES = 5
private const val TURN = 1.0
private const val STEP = 1.7
private const val REST = STEP - TURN
// The rest on the first leaf before the first turn, and the longer rest on the last leaf before the
// way back.
private const val LEAD = 0.8
private const val PAUSE = 0.8
private const val BACK_AT = LEAD + LEAVES * STEP + PAUSE
private const val LOOP = BACK_AT + LEAVES * STEP - REST + LEAD
// Each segment moves the book from wherever it is to `aim` leaves past the leaf the loop started
// on; between segments the thumb lets go and the spring lays the leaf down.
private class Segment(val from: Double, val to: Double, val aim: Double, val easeOut: Boolean)

private val Script =
    List(LEAVES) { k -> Segment(LEAD + k * STEP, LEAD + k * STEP + TURN, (k + 1).toDouble(), false) } +
        List(LEAVES) { k -> Segment(BACK_AT + k * STEP, BACK_AT + k * STEP + TURN, (LEAVES - k - 1).toDouble(), false) }
private const val USER_HOLD = 3.5

private fun clamp01(x: Double) = min(1.0, max(0.0, x))

private fun smoothstep(a: Double, b: Double, x: Double): Double {
    val t = clamp01((x - a) / (b - a))
    return t * t * (3 - 2 * t)
}

private fun ease(out: Boolean, u: Double): Double {
    if (out) return 1 - (1 - u).pow(3)
    return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
}

/** iOS-style resistance past an end: `x` leaves past it give at most `limit`. */
private fun rubber(x: Double, limit: Double): Double {
    val sign = if (x < 0) -1.0 else 1.0
    return sign * (1 - 1 / ((abs(x) * 0.55) / limit + 1)) * limit
}

/** JavaScript's Math.round: halves go up. */
private fun jsRound(x: Double) = floor(x + 0.5)

/** How far leaf `k` has turned with the book at `p` and `spread` leaves in the air. */
private fun turnOf(k: Int, p: Double, spread: Double, last: Int): Double {
    // The back leaf never turns; pressed past the end, its corner only lifts a little.
    if (k >= last) return min(0.12, max(0.0, (p - last) * 0.3))
    val t = clamp01((p - k - 1 + spread) / spread)
    // Pulled back past the front, the first leaf's corner lifts as if to come back.
    if (k == 0 && p < 0) return max(t, min(0.12, -p * 0.3))
    return t
}

private class Fold(val nx: Double, val ny: Double, val c: Double, val mx: Double, val my: Double, val len: Double)

/** The fold of a leaf w × h turned by t: the line's normal (towards the corner) and offset. */
private fun foldOf(t: Double, w: Double, h: Double): Fold {
    val lift = 2 * h * t
    // The thumb stays within a leaf's height of the binding's far corner, so the fold never
    // crosses the binding.
    val bow = BOW * sqrt(max(0.0, (2 * h - lift) * lift))
    val px = w - bow
    val py = h - lift
    val dx = w - px
    val dy = h - py
    val len = max(0.0001, hypot(dx, dy))
    val nx = dx / len
    val ny = dy / len
    val mx = (w + px) / 2
    val my = (h + py) / 2
    return Fold(nx, ny, mx * nx + my * ny, mx, my, len)
}

/** The part of a polygon (flat x,y list) where (p·n − c) has the sign `keep`. */
private fun clipPolygon(points: DoubleArray, nx: Double, ny: Double, c: Double, keep: Int): DoubleArray {
    val out = ArrayList<Double>(points.size + 4)
    val count = points.size / 2
    for (i in 0 until count) {
        val ax = points[i * 2]
        val ay = points[i * 2 + 1]
        val bx = points[((i + 1) % count) * 2]
        val by = points[((i + 1) % count) * 2 + 1]
        val sa = (ax * nx + ay * ny - c) * keep
        val sb = (bx * nx + by * ny - c) * keep
        if (sa >= 0) {
            out.add(ax)
            out.add(ay)
        }
        if ((sa >= 0) != (sb >= 0)) {
            val k = sa / (sa - sb)
            out.add(ax + (bx - ax) * k)
            out.add(ay + (by - ay) * k)
        }
    }
    return out.toDoubleArray()
}

private fun polygonPath(points: DoubleArray): Path {
    val path = Path()
    for (i in points.indices step 2) {
        if (i == 0) path.moveTo(points[i].toFloat(), points[i + 1].toFloat()) else path.lineTo(points[i].toFloat(), points[i + 1].toFloat())
    }
    path.close()
    return path
}

/** Paints with a shader at full strength, whatever colour the paint was left with. */
private fun Paint.shade(shader: Shader) {
    color = 0xFF000000.toInt()
    this.shader = shader
}

private fun pad(n: Int) = n.toString().padStart(2, '0')

private fun argb(r: Int, g: Int, b: Int, a: Double) = ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b

private class Loaded(val bitmap: Bitmap, val at: Double)

private enum class Mode { Drag, Coast, Settle, Script }

/** The book's motion, shared by the frame loop, the gestures and the drawing. */
private class FlipbookMotion(count: Int) {
    // The book's place in leaves: 0 has the first leaf on top, 0.5 has it half over.
    var page = 0.0
    var velocity = 0.0
    // |velocity|, smoothed: how many leaves are in the air.
    var speed = 0.0
    var target = 0.0
    var mode = Mode.Settle
    var dragFrom = 0.0
    var byUser = false
    var clock = 0.0
    var looping = false
    var hold = 0.0
    var segStart = 0.0
    var segIndex = -1
    // The leaf the loop starts from and comes back to.
    var base = 0.0
    var elapsed = 0.0
    var lastTick = 0.0
    var wasResting = true
    val loaded = arrayOfNulls<Loaded>(count)
    // Bumped once a frame so the canvas draws again.
    var frame by mutableLongStateOf(0L)
}

/** One frame of the book: the script, the coast after a flick and the spring that lays a leaf down. */
private fun advance(m: FlipbookMotion, dt: Double, last: Double, reducedMotion: Boolean) {
    m.elapsed += dt
    val before = m.page
    if (m.mode != Mode.Drag) {
        if (m.hold > 0) {
            m.hold -= dt
            val resting = m.mode == Mode.Settle && abs(m.velocity) < 0.05 && abs(m.page - m.target) < 0.01
            // A book still coasting or settling keeps the loop waiting until it lies still.
            if (m.hold <= 0 && m.looping && !resting) m.hold = 0.1
            if (m.hold <= 0 && m.looping) {
                // Back into the loop from the leaf the hand left the book on: it becomes one of the
                // loop's leaves (as near the start as the book allows) and the script picks up in the
                // rest on that leaf, so the book only ever turns one leaf at a time.
                val at = min(last, max(0.0, jsRound(m.page)))
                val from = min(at, max(0.0, last - LEAVES))
                val j = at - from
                m.base = from
                m.clock = if (j == 0.0) 0.0 else BACK_AT + (LEAVES - j) * STEP - REST
                m.segIndex = -1
            }
        } else if (m.looping) {
            val t = (m.clock + dt) % LOOP
            m.clock = t
            var active = -1
            Script.forEachIndexed { i, s -> if (t >= s.from && t < s.to) active = i }
            if (active != m.segIndex) {
                if (active >= 0) {
                    m.segStart = m.page
                    m.byUser = false
                    m.mode = Mode.Script
                } else if (m.segIndex >= 0) {
                    // A segment ended: the thumb lets go and the nearest leaf settles.
                    m.target = min(last, max(0.0, jsRound(m.page)))
                    m.mode = Mode.Settle
                }
                m.segIndex = active
            }
            if (active >= 0) {
                val s = Script[active]
                val u = (t - s.from) / (s.to - s.from)
                val aim = min(last, max(0.0, m.base + s.aim))
                val p = m.segStart + (aim - m.segStart) * ease(s.easeOut, u)
                m.velocity = (p - m.page) / dt
                m.page = p
            }
        }

        val p = m.page
        if (m.mode == Mode.Coast) {
            if (abs(m.velocity) < SETTLE_SPEED) {
                val v = m.velocity
                m.target = min(last, max(0.0, if (v > 0.4) ceil(p) else if (v < -0.4) floor(p) else jsRound(p)))
                m.mode = Mode.Settle
            } else {
                m.velocity *= exp(-FRICTION * dt)
                val next = p + m.velocity * dt
                if (next < 0 || next > last) {
                    // The coast reaches an end: it stops on the end leaf, and the spring there takes
                    // what is left of its speed (capped), so the leaf presses in a little and settles.
                    val end = if (next < 0) 0.0 else last
                    m.page = end
                    m.velocity = m.velocity.coerceIn(-END_SPEED, END_SPEED)
                    m.target = end
                    m.mode = Mode.Settle
                } else {
                    m.page = next
                }
            }
        }
        if (m.mode == Mode.Settle) {
            if (reducedMotion) {
                m.page = m.target
                m.velocity = 0.0
            } else {
                val v = m.velocity + (STIFFNESS * (m.target - p) - DAMPING * m.velocity) * dt
                m.velocity = v
                m.page = p + v * dt
            }
        }
    }
    // Whatever happened above, the place stays a finite number near the book.
    if (!m.page.isFinite() || !m.velocity.isFinite()) {
        m.page = min(last, max(0.0, if (m.target.isFinite()) jsRound(m.target) else 0.0))
        m.velocity = 0.0
        m.mode = Mode.Settle
    } else if (m.page < -SLACK || m.page > last + SLACK) {
        m.page = m.page.coerceIn(-SLACK, last + SLACK)
    }
    // The script turns one leaf at a time, so only a hand puts several leaves in the air.
    val fast = if (reducedMotion || !m.byUser) 0.0 else abs(m.velocity)
    m.speed += (fast - m.speed) * (1 - exp(-dt * SPEED_SMOOTH))
    if (m.mode == Mode.Drag) {
        // The finger's speed, read from where it moved the book since the last frame.
        m.velocity += ((m.page - before) / dt - m.velocity) * 0.4
    }
}

/**
 * A thick flipbook lying on a dark table, bound along its top edge under a cloth tape, a photo
 * printed on every leaf. Dragging up thumbs the leaves over: each one curls up from its
 * bottom-right corner, shows its lighter back as it rolls over the binding and falls behind the
 * book. A fast drag puts several leaves in the air at once, so the photos run like a film; the
 * book coasts on after a flick and lays the nearest leaf down on a spring. A counter at the top
 * rolls with the leaves.
 *
 * @param pages The leaves, front to back.
 * @param autoPlay Turns a few leaves over one at a time, rests, turns them back one at a time and starts over.
 * @param onPage Called with a leaf's index when the book comes to rest on it by hand.
 */
@Composable
fun FlipbookScrub(
    modifier: Modifier = Modifier,
    pages: List<FlipbookPage> = Pages,
    autoPlay: Boolean = false,
    onPage: ((Int) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val total = pages.size
    val last = total - 1
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val resting by rememberUpdatedState(onPage)
    val motion = remember(pages) { FlipbookMotion(total) }
    val painter = remember(pages, reducedMotion) { FlipbookPainter(pages, reducedMotion) }

    // Read during composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect {
        painter.top = safeTop
        painter.bottom = safeBottom
    }

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.clock = 0.0
        motion.hold = 0.0
        motion.base = 0.0
    }

    // Each print arrives on its own and fades in; until then its leaf shows blank paper.
    LaunchedEffect(motion, context) {
        val loader = SingletonImageLoader.get(context)
        pages.forEachIndexed { i, leaf ->
            launch {
                val request = ImageRequest.Builder(context).data(photoUri(leaf.id)).size(Size.ORIGINAL).build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                motion.loaded[i] = Loaded(result.image.toBitmap(), motion.elapsed)
            }
        }
    }

    LaunchedEffect(motion, reducedMotion) {
        var lastNanos = -1L
        val m = motion
        m.lastTick = floor(m.page / TICK_EVERY)
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNanos < 0) 1.0 / 60 else min((now - lastNanos) / 1e9, 0.064)
                lastNanos = now
                if (dt > 0) advance(m, dt, last.toDouble(), reducedMotion)
                // A tick every few leaves while a hand turns them.
                val tick = floor(m.page / TICK_EVERY)
                if (tick != m.lastTick && m.byUser) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                m.lastTick = tick
                val rest = m.mode == Mode.Settle && abs(m.velocity) < 0.05 && abs(m.page - m.target) < 0.01
                if (rest && !m.wasResting && m.byUser) resting?.invoke(m.target.toInt())
                m.wasResting = rest
                m.frame = now
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(TABLE)
            .semantics { contentDescription = "Flipbook of $total photos. Drag up to riffle through the leaves." },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        painter.layout(width, height)

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(motion, last) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        var panning = false
                        var released = false
                        val m = motion
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            val dx = (change.position.x - start.x) / density.density
                            val dy = (change.position.y - start.y) / density.density
                            if (!change.pressed) {
                                released = true
                                if (panning) {
                                    change.consume()
                                    if (m.mode == Mode.Drag) {
                                        m.hold = USER_HOLD
                                        val thrown = (-tracker.calculateVelocity().y / density.density / DRAG_STEP).toDouble()
                                        m.velocity = if (thrown.isFinite()) thrown.coerceIn(-MAX_SPEED, MAX_SPEED) else 0.0
                                        m.mode = Mode.Coast
                                    }
                                } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                    // A tap on the book turns one leaf forwards, or back when tapped above the middle.
                                    m.hold = USER_HOLD
                                    m.byUser = true
                                    m.segIndex = -1
                                    val forward = change.position.y / density.density > painter.bookY + painter.pageH / 2f
                                    val from = if (m.mode == Mode.Settle) m.target else jsRound(m.page)
                                    val next = min(last.toDouble(), max(0.0, from + if (forward) 1 else -1))
                                    // A tap caught mid-coast stops the coast; the spring keeps the speed it had.
                                    m.velocity = m.velocity.coerceIn(-END_SPEED, END_SPEED)
                                    m.target = next
                                    m.mode = Mode.Settle
                                    if (next != from) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                continue
                            }
                            if (!panning && sqrt(dx * dx + dy * dy) >= DRAG_SLOP) {
                                panning = true
                                m.hold = USER_HOLD
                                m.byUser = true
                                m.segIndex = -1
                                // Caught wherever it is, mid-turn or mid-flight.
                                m.dragFrom = min(last.toDouble(), max(0.0, m.page))
                                m.mode = Mode.Drag
                            }
                            if (panning) {
                                change.consume()
                                if (m.mode == Mode.Drag) {
                                    m.hold = USER_HOLD
                                    val raw = m.dragFrom - dy / DRAG_STEP
                                    m.page = when {
                                        raw < 0 -> rubber(raw, RUBBER)
                                        raw > last -> last + rubber(raw - last, RUBBER)
                                        else -> raw
                                    }
                                }
                            }
                        }
                        // A finger lost to the system lets go of the book where it is.
                        if (!released && m.mode == Mode.Drag) {
                            m.target = min(last.toDouble(), max(0.0, jsRound(m.page)))
                            m.mode = Mode.Settle
                        }
                    }
                },
        ) {
            motion.frame
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { painter.draw(it.nativeCanvas, motion) }
            }
        }
    }
}

/** Draws one frame of the book and its header, as the RN version records one Skia picture. */
private class FlipbookPainter(private val pages: List<FlipbookPage>, private val reducedMotion: Boolean) {
    var top = 0f
    var bottom = 0f
    var width = 0f
    var height = 0f
    var pageW = 0f
    var pageH = 0f
    var bookX = 0f
    var bookY = 0f

    // The chapters in order, and each leaf's chapter as a number, for the rolling chapter line.
    private val chapters = pages.map { it.chapter }.distinct()
    private val chapterOf = pages.map { chapters.indexOf(it.chapter) }

    private val sans = Typeface.SANS_SERIF
    private val serifItalic = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    private val serifBoldItalic =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SERIF, 600, true) else Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
    private fun weight(w: Int, fallback: String): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(sans, w, false) else Typeface.create(fallback, Typeface.NORMAL)

    private fun font(face: Typeface, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        isSubpixelText = true
    }

    private val label = font(weight(600, "sans-serif-medium"), 11f)
    private val caption = font(serifItalic, 15f)
    private val number = font(weight(600, "sans-serif-medium"), 10f)
    private val counter = font(weight(600, "sans-serif-medium"), COUNTER_SIZE)
    private val chapter = font(serifBoldItalic, 24f)
    private val small = font(weight(500, "sans-serif-medium"), 13f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // Hardware canvases only honour mask filters from API 28; below that the shadows are left out.
    private val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    private val cast = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb(0, 0, 0, 0.55)
        if (blurs) maskFilter = BlurMaskFilter(sigmaToRadius(18f), BlurMaskFilter.Blur.NORMAL)
    }
    private val flapShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb(18, 10, 4, 0.32)
        if (blurs) maskFilter = BlurMaskFilter(sigmaToRadius(7f), BlurMaskFilter.Blur.NORMAL)
    }
    private val rect = RectF()
    private val src = Rect()
    private val bounds = Rect()
    private val mirror = Matrix()
    private val widths = HashMap<String, Float>()

    fun layout(w: Float, h: Float) {
        width = w
        height = h
        pageW = min(MAX_PAGE_W, w - SIDE * 2)
        pageH = (pageW * PAGE_ASPECT).roundToInt().toFloat()
        bookX = (w - pageW) / 2
        val headerBottom = top + COUNTER_Y + 8
        bookY = max(headerBottom + pageH * RISE_ROOM, (h - pageH - THICK) / 2 + 24)
    }

    /**
     * Skia's measured width: the glyphs' ink bounds, rounded out and padded by one a side, not
     * their advance. The RN code aligns text and steps tracked letters by it.
     */
    private fun measure(text: String, paint: Paint): Float = widths.getOrPut("${paint.textSize}/${paint.typeface.hashCode()}/$text") {
        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.isEmpty) 0f else bounds.width() + 2f
    }

    /** Text set to its left (align 0), middle (0.5) or right (1). */
    private fun text(canvas: NativeCanvas, text: String, paint: Paint, x: Float, y: Float, align: Float, color: Int, alpha: Double) {
        paint.color = color
        paint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        canvas.drawText(text, x - measure(text, paint) * align, y, paint)
    }

    fun draw(canvas: NativeCanvas, m: FlipbookMotion) {
        val total = pages.size
        if (total == 0 || width <= 0f) return
        val last = total - 1
        // Drawn from a place kept on the book, whatever the physics did this frame.
        val p = if (m.page.isFinite()) m.page.coerceIn(-RUBBER, last + RUBBER) else 0.0
        val w = pageW
        val h = pageH
        val now = m.elapsed

        // The table, lit from above the book.
        fill.shade(RadialGradient(width * 0.42f, bookY + h * 0.35f, height * 0.62f, TABLE_LIT, TABLE_DARK, Shader.TileMode.CLAMP))
        canvas.drawRect(0f, 0f, width, height, fill)
        fill.shader = null

        // The header: the label, the chapter under it, and the counter on the right.
        var cursor = SIDE
        // A space measures as nothing on its own, so it is measured between two letters.
        val space = measure("a a", label) - measure("aa", label)
        for (letter in "MOTION STUDIES") {
            val s = letter.toString()
            if (letter != ' ') text(canvas, s, label, cursor, top + LABEL_Y, 0f, CHROME, 0.5)
            cursor += (if (letter == ' ') space else measure(s, label)) + 2.2f
        }
        val at = min(last.toDouble(), max(0.0, p))
        // The chapter rolls up a line, like the counter, while the leaf between two chapters turns.
        val whole = max(0, min(last - 1, floor(at).toInt()))
        val next = min(last, whole + 1)
        val chapterAt = chapterOf[whole] + (chapterOf[next] - chapterOf[whole]) * smoothstep(0.2, 0.8, at - whole)
        canvas.save()
        canvas.clipRect(0f, top + LABEL_Y + 8, width * 0.6f, top + LABEL_Y + 48)
        chapters.forEachIndexed { j, name ->
            val d = j - chapterAt
            val alpha = clamp01(1 - abs(d) * 1.4)
            if (alpha > 0.01) text(canvas, name, chapter, SIDE, (top + LABEL_Y + 34 + d * 30).toFloat(), 0f, CHROME, alpha)
        }
        canvas.restore()
        val of = "/ ${pad(total)}"
        text(canvas, of, small, width - SIDE, top + COUNTER_Y, 1f, CHROME, 0.5)
        drawCounter(canvas, at + 1, width - SIDE - measure(of, small) - 8, top + COUNTER_Y)
        text(canvas, "Drag up to riffle  ·  Tap to turn a leaf", small, width / 2, height - bottom - 22, 0.5f, CHROME, 0.42)

        canvas.save()
        canvas.translate(bookX, bookY)

        // The book's shadow on the table, and the block of leaves under the top one.
        if (blurs) canvas.drawRoundRect(8f, 14f, 8f + w, 14f + h + THICK, 8f, 8f, cast)
        fill.color = PAPER_EDGE
        canvas.drawRoundRect(0f, 0f, w, h + THICK, CORNER + 2, CORNER + 2, fill)
        // The edges of the leaves, a hairline each, darker towards the table.
        stroke.strokeWidth = 0.6f
        stroke.strokeCap = Paint.Cap.BUTT
        var y = h + 1.6f
        while (y < h + THICK - 1) {
            val depth = (y - h) / THICK
            stroke.color = argb(90, 72, 52, 0.18 + depth * 0.32)
            canvas.drawLine(2f, y, w - 2, y, stroke)
            y += 1.7f
        }
        fill.shade(LinearGradient(0f, h, 0f, h + THICK, argb(40, 28, 16, 0.05), argb(40, 28, 16, 0.38), Shader.TileMode.CLAMP))
        canvas.drawRoundRect(0f, h, w, h + THICK, CORNER + 2, CORNER + 2, fill)
        fill.shader = null

        if (reducedMotion) {
            // No curl: the next leaf fades in over the one on top.
            val k = min(last, max(0, floor(at).toInt()))
            drawLeaf(canvas, k, m, w, h, now)
            val f = at - k
            if (f > 0.01 && k < last) {
                canvas.saveLayerAlpha(-4f, -4f, w + 4, h + 4, (f * 255).roundToInt())
                drawLeaf(canvas, k + 1, m, w, h, now)
                canvas.restore()
            }
            drawTape(canvas, w)
            canvas.restore()
            return
        }

        val spread = min(SPREAD_MAX, SPREAD_BASE + m.speed * SPREAD_RATE)
        // The first leaf still lying flat, with the tape over it; then the leaves in the air above
        // it, the one furthest over last. The turn falls leaf by leaf, so the first leaf that has
        // not started is found by walking up from the last one gone over.
        var topLeaf = max(0, floor(p - 1).toInt())
        while (topLeaf < last && turnOf(topLeaf, p, spread, last) > 0) topLeaf += 1
        val flat = turnOf(topLeaf, p, spread, last) <= 0
        if (flat) drawLeaf(canvas, topLeaf, m, w, h, now)
        drawTape(canvas, w)
        val from = min(last, if (flat) topLeaf - 1 else topLeaf)
        val wd = w.toDouble()
        val hd = h.toDouble()
        val pageBox = doubleArrayOf(0.0, 0.0, wd, 0.0, wd, hd, 0.0, hd)
        val underTape = doubleArrayOf(-1.0, TAPE_H.toDouble(), wd + 1, TAPE_H.toDouble(), wd + 1, hd + 1, -1.0, hd + 1)
        var k = from
        while (k >= max(0, floor(p - 1).toInt())) {
            val t = turnOf(k, p, spread, last)
            if (t >= 1) break
            if (t > 0) drawTurning(canvas, k, t, m, pageBox, underTape, w, h, now)
            k -= 1
        }
        canvas.restore()
    }

    private fun drawTurning(canvas: NativeCanvas, k: Int, t: Double, m: FlipbookMotion, pageBox: DoubleArray, underTape: DoubleArray, w: Float, h: Float, now: Double) {
        val fold = foldOf(t, w.toDouble(), h.toDouble())
        // What still lies flat, clipped clear of the tape that holds it.
        val keep = clipPolygon(underTape, fold.nx, fold.ny, fold.c, -1)
        if (keep.size >= 6) {
            canvas.save()
            canvas.clipPath(polygonPath(keep))
            drawLeaf(canvas, k, m, w, h, now)
            canvas.restore()
        }
        val open = clipPolygon(pageBox, fold.nx, fold.ny, fold.c, 1)
        if (open.size < 6) return
        // The shadow the lifted part throws on the leaf it uncovers, deepest at the fold.
        fill.shade(LinearGradient(
            fold.mx.toFloat(), fold.my.toFloat(),
            (fold.mx + fold.nx * FOLD_SHADOW).toFloat(), (fold.my + fold.ny * FOLD_SHADOW).toFloat(),
            argb(30, 18, 8, 0.34 * (1 - smoothstep(0.75, 1.0, t))), argb(30, 18, 8, 0.0),
            Shader.TileMode.CLAMP,
        ))
        canvas.drawPath(polygonPath(open), fill)
        fill.shader = null
        // The lifted part, mirrored over the fold; what rises above the binding folds down behind
        // the book towards the end of the turn.
        val squash = smoothstep(SQUASH_FROM, 1.0, t)
        val n = open.size / 2
        val mirrored = DoubleArray(open.size)
        for (i in 0 until n) {
            val x = open[i * 2]
            val y = open[i * 2 + 1]
            val s = x * fold.nx + y * fold.ny - fold.c
            mirrored[i * 2] = x - 2 * s * fold.nx
            mirrored[i * 2 + 1] = y - 2 * s * fold.ny
        }
        // Split at the binding, so above it can fold away while below it stays put.
        val split = ArrayList<Double>(open.size + 8)
        for (i in 0 until n) {
            val ax = mirrored[i * 2]
            val ay = mirrored[i * 2 + 1]
            val bx = mirrored[((i + 1) % n) * 2]
            val by = mirrored[((i + 1) % n) * 2 + 1]
            split.add(ax)
            split.add(if (ay < 0) ay * (1 - squash) else ay)
            if ((ay < 0) != (by < 0)) {
                val mm = ay / (ay - by)
                split.add(ax + (bx - ax) * mm)
                split.add(0.0)
            }
        }
        if (1 - squash > 0.01) drawFlap(canvas, polygonPath(split.toDoubleArray()), fold, squash, k, m, w, h, now)
    }

    /** A leaf's face: paper, the photo printed on it, its caption and number. */
    private fun drawLeaf(canvas: NativeCanvas, index: Int, m: FlipbookMotion, w: Float, h: Float, now: Double) {
        // A leaf that is not there (an index off the book) draws nothing rather than throwing.
        if (index !in pages.indices) return
        fill.color = PAPER
        canvas.drawRoundRect(0f, 0f, w, h, CORNER, CORNER, fill)
        rect.set(MARGIN, TAPE_H + 10, w - MARGIN, h - CAPTION_H)
        fill.color = BLANK
        canvas.drawRect(rect, fill)
        val loaded = m.loaded.getOrNull(index)
        if (loaded != null) {
            val bitmap = loaded.bitmap
            val iw = bitmap.width.toFloat()
            val ih = bitmap.height.toFloat()
            // The middle of the photo, cropped to fill the print's frame.
            val fit = max(rect.width() / iw, rect.height() / ih)
            val sw = rect.width() / fit
            val sh = rect.height() / fit
            src.set(((iw - sw) / 2).roundToInt(), ((ih - sh) / 2).roundToInt(), ((iw + sw) / 2).roundToInt(), ((ih + sh) / 2).roundToInt())
            image.alpha = (clamp01((now - loaded.at) / IMAGE_FADE) * 255).roundToInt()
            canvas.drawBitmap(bitmap, src, rect, image)
        }
        val base = h - CAPTION_H / 2 + 5
        text(canvas, pages[index].title, caption, MARGIN, base, 0f, INK, 1.0)
        text(canvas, "${pad(index + 1)} / ${pad(pages.size)}", number, w - MARGIN, base, 1f, INK, 0.55)
        // The paper curves into the binding, a little darker there.
        fill.shade(LinearGradient(0f, TAPE_H, 0f, TAPE_H + 22, argb(60, 40, 20, 0.16), argb(60, 40, 20, 0.0), Shader.TileMode.CLAMP))
        canvas.drawRect(0f, TAPE_H, w, TAPE_H + 22, fill)
        fill.shader = null
    }

    /** The lifted part of a leaf: its back, lighter paper, rolled along the fold. */
    private fun drawFlap(canvas: NativeCanvas, flap: Path, fold: Fold, squash: Double, index: Int, m: FlipbookMotion, w: Float, h: Float, now: Double) {
        // Its shadow on whatever is under it, a little down and to the right.
        if (blurs) {
            canvas.save()
            canvas.translate(5f, 9f)
            canvas.drawPath(flap, flapShadow)
            canvas.restore()
        }
        fill.color = PAPER_BACK
        canvas.drawPath(flap, fill)

        canvas.save()
        canvas.clipPath(flap)
        // The print shows faintly through the paper, mirrored by the fold.
        val through = SHOW_THROUGH * (1 - squash)
        if (through > 0.005 && m.loaded.getOrNull(index) != null) {
            val nx = fold.nx
            val ny = fold.ny
            val c = fold.c
            canvas.save()
            mirror.setValues(
                floatArrayOf(
                    (1 - 2 * nx * nx).toFloat(), (-2 * nx * ny).toFloat(), (2 * c * nx).toFloat(),
                    (-2 * nx * ny).toFloat(), (1 - 2 * ny * ny).toFloat(), (2 * c * ny).toFloat(),
                    0f, 0f, 1f,
                ),
            )
            canvas.concat(mirror)
            drawLeaf(canvas, index, m, w, h, now)
            canvas.restore()
            fill.color = PAPER_BACK
            fill.alpha = ((1 - through) * 255).roundToInt()
            canvas.drawPath(flap, fill)
        }
        // The roll: a crease in shadow at the fold, a highlight on the curve, then the paper turning
        // away from the light towards the far edge.
        val mx = fold.mx.toFloat()
        val my = fold.my.toFloat()
        val rx = (fold.mx - fold.nx * ROLL).toFloat()
        val ry = (fold.my - fold.ny * ROLL).toFloat()
        fill.shade(LinearGradient(
            mx, my, rx, ry,
            intArrayOf(argb(70, 50, 30, 0.30), argb(255, 255, 255, 0.75), argb(255, 255, 255, 0.0)),
            floatArrayOf(0f, 0.32f, 1f),
            Shader.TileMode.CLAMP,
        ))
        canvas.drawPath(flap, fill)
        val reach = max(ROLL * 1.5, fold.len * 0.5)
        fill.shade(LinearGradient(
            rx, ry, (fold.mx - fold.nx * reach).toFloat(), (fold.my - fold.ny * reach).toFloat(),
            argb(80, 60, 40, 0.0), argb(80, 60, 40, 0.12 + 0.12 * squash),
            Shader.TileMode.CLAMP,
        ))
        canvas.drawPath(flap, fill)
        fill.shader = null
        canvas.restore()

        stroke.strokeWidth = 0.75f
        stroke.color = argb(90, 70, 50, 0.28)
        canvas.drawPath(flap, stroke)
    }

    /** The cloth tape that binds the leaves along the top, with a row of stitches. */
    private fun drawTape(canvas: NativeCanvas, w: Float) {
        fill.shade(LinearGradient(0f, -6f, 0f, TAPE_H, intArrayOf(TAPE_SHEEN, TAPE, TAPE_DEEP), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP))
        canvas.drawRoundRect(-3f, -6f, w + 3, TAPE_H, 5f, 5f, fill)
        fill.shader = null
        stroke.strokeWidth = 1.2f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = argb(239, 230, 216, 0.28)
        var x = 12f
        while (x < w - 8) {
            canvas.drawLine(x, TAPE_H - 7, x + 5, TAPE_H - 7, stroke)
            x += 11f
        }
        stroke.strokeCap = Paint.Cap.BUTT
    }

    /** The leaf number, each digit rolling up like an odometer as the leaves go by. */
    private fun drawCounter(canvas: NativeCanvas, value: Double, right: Float, baseline: Float) {
        val cell = measure("0", counter) * 0.86f
        val whole = floor(value).toInt()
        val frac = smoothstep(0.38, 0.62, value - whole)
        val line = COUNTER_SIZE * 1.05
        canvas.save()
        canvas.clipRect(right - cell * 2 - 4, baseline - COUNTER_SIZE * 0.92f, right + 4, baseline + COUNTER_SIZE * 0.2f)
        for (place in 0 until 2) {
            val unit = if (place == 0) 1 else 10
            val digit = (whole / unit) % 10
            // The tens roll only while the ones turn over from 9.
            val roll = if (place == 0) frac else if (whole % 10 == 9) frac else 0.0
            val x = right - cell * (place + 1)
            for (k in 0 until 2) {
                val shown = ((digit + k) % 10).toString()
                val y = (baseline + (k - roll) * line).toFloat()
                val alpha = clamp01(1 - abs(k - roll) * 1.4)
                if (alpha <= 0.01) continue
                text(canvas, shown, counter, x + (cell - measure(shown, counter)) / 2, y, 0f, CHROME, alpha)
            }
        }
        canvas.restore()
    }
}
