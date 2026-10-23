package com.ngocdevv.nativecook.blocks.onboarding.liquidswipeonboarding

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/**
 * @param photo Unsplash photo id, shown in the arch.
 * @param color The page's colour; [ink] is the colour of its type.
 * @param title The headline, one string per line.
 */
data class LiquidPage(val photo: String, val color: Color, val ink: Color, val title: List<String>, val body: String)

private val Pages = listOf(
    LiquidPage("photo-1582721478779-0ae163c05a60", Color(0xFFFF5B1F), Color(0xFF1D0B04), listOf("Find the", "fire."), "Guided treks to forty active craters, from your first ridge to the rim."),
    LiquidPage("photo-1628632577468-645afac21c82", Color(0xFF17120F), Color(0xFFFFD9C1), listOf("Read the", "ground."), "Live heat maps and vent alerts on every trail you walk."),
    LiquidPage("photo-1663640397122-d50df280b1db", Color(0xFFF4C9A4), Color(0xFF2B1109), listOf("Climb at", "first light."), "Small groups, local guides and hot coffee waiting at the crater."),
    LiquidPage("photo-1623093386041-a0915e5a1ca4", Color(0xFF7C1C0F), Color(0xFFFFE4D4), listOf("Stand at", "the edge."), "Your first trek is on us. Pick a volcano and we will do the rest."),
)

private const val BRAND = "CALDERA"

// The arch holds a photo about 360 dp wide on a phone; 1000 px covers that at 3x.
private fun photoUri(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=1000&q=80"

// Layout, dp.
private const val SIDE = 24f
private const val PHOTO_TOP = 60f // below the top inset
private const val PHOTO_HEIGHT = 0.4f // share of the screen's height
private const val TITLE_SIZE = 56f
private const val TITLE_LEADING = 53f
private const val BODY_SIZE = 16f
private const val BODY_LEADING = 23f
private const val DOTS_LEFT = 46f
private const val BUTTON_HEIGHT = 52f

// The tab: a smooth bump of the next page sticking out of the right edge (the previous page's on
// the left), REST_REACH deep and TAB_RADIUS tall either side of its centre, which rests TAB_ABOVE
// dp above the bottom inset.
private const val REST_REACH = 26f
private const val TAB_RADIUS = 52f
private const val TAB_ABOVE = 40f
// While dragged, the bump's tip follows the finger and it widens as it goes.
private const val PULL_REACH = 0.96f // dp of reach per dp dragged
private const val PULL_RADIUS = 0.78f // dp of half-height per dp dragged
private const val FOLLOW = 16f // 1/s, how quickly the bump's centre follows the finger up and down
// Past COMMIT_SHARE of the width, or flicked faster than COMMIT_SPEED, the liquid sweeps across.
private const val COMMIT_SHARE = 0.34f
private const val COMMIT_SPEED = 500f // dp/s
private const val RELEASE_FADE = 70f // ms; a finger held still this long before letting go has lost most of its speed
private const val SAMPLES = 44
// The sweep: a spring with a little overshoot, carried by the release speed; the new page's
// content rides in PARALLAX of the width behind the liquid and bounces into place.
private const val SWEEP_MS = 1000f
private const val SWEEP_RATIO = 0.7f
private const val RETURN_MS = 520f
private const val RETURN_RATIO = 0.55f
private const val TAB_MS = 420f
private const val TAB_RATIO = 0.6f
private const val PARALLAX = 0.55f
// Share of the release speed (in widths per second) the sweep carries on with.
private const val SWEEP_CARRY = 0.5f
private const val PUSH = 0.18f // how far the covered page is pushed away, share of the width
private const val REDUCED_SWEEP_MS = 280f

// autoPlay (seconds): three swipes forward and three back, then a rest. Each scripted drag runs
// DRAG_TIME s and speeds up as it goes, so it lets go with a flick.
private const val LOOP = 15f
private val Forward = floatArrayOf(1.0f, 3.2f, 5.4f)
private val Back = floatArrayOf(8.6f, 10.8f, 13.0f)
private val Starts = Forward + Back
private const val DRAG_TIME = 0.62f
private const val DRAG_SHARE = 0.5f // of the width
private const val DRAG_RISE = 110f // dp the scripted finger rises over a drag
private const val USER_HOLD = 4f
// A touch is a tap when it lifts within this long without dragging sideways past PAN_SLOP.
private const val TAP_MS = 400L
private const val PAN_SLOP = 8f

/** The bump that shapes the liquid's edge: 1 at its centre, 0 at ±1, smooth throughout. */
private fun bump(u: Float): Float {
    if (u <= -1f || u >= 1f) return 0f
    return 0.5f + 0.5f * cos(PI.toFloat() * u)
}

/** Whether a clock that went from `before` to `now`, wrapping at LOOP, passed `at`. */
private fun crossed(before: Float, now: Float, at: Float) =
    (before < at && now >= at) || (now < before && (at > before || at <= now))

/** Diminishing pull past a limit. */
private fun rubber(x: Float, limit: Float): Float {
    if (x <= limit) return x
    val over = x - limit
    return limit + over / (1f + over / 120f)
}

/** An alpha for Color.copy, which throws outside 0…1 (NaN included). */
private fun safeAlpha(alpha: Float) = if (alpha.isNaN()) 0f else alpha.coerceIn(0f, 1f)

/** `inset` dp in from the right edge (side 1) or the left edge (side -1). */
private fun fromSide(side: Int, width: Float, inset: Float) = if (side > 0) width - inset else inset

/**
 * A value driven like a Reanimated shared value: set directly, by `withSpring({ duration,
 * dampingRatio })` (the stiffness solved so the spring's energy falls to the threshold after
 * 1.5 × the duration) or by a timing on the default ease-in-out quad. The callback runs when the
 * animation ends or is replaced, as Reanimated's does.
 */
private class Driven(var value: Float) {
    private var mode = 0 // 0 still, 1 spring, 2 timing
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
    private var done: (() -> Unit)? = null

    /** Puts the value somewhere at once, stopping whatever was moving it. */
    fun set(next: Float) {
        value = next
        mode = 0
        velocity = 0f
        take()?.invoke()
    }

    fun springTo(target: Float, speed: Float, durationMs: Float, dampingRatio: Float, then: (() -> Unit)? = null) {
        val replaced = take()
        var v = speed
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
        initialEnergy = energy(x0, speed)
        done = then
        replaced?.invoke()
    }

    fun timeTo(target: Float, durationMs: Float, then: (() -> Unit)? = null) {
        val replaced = take()
        mode = 2
        from = value
        to = target
        time = 0f
        duration = durationMs / 1000f
        velocity = 0f
        done = then
        replaced?.invoke()
    }

    fun step(dt: Float) {
        when (mode) {
            1 -> {
                val x0 = value - to
                val v0 = velocity
                val t = dt
                if (zeta < 1f) {
                    val sin1 = sin(omega1 * t)
                    val cos1 = cos(omega1 * t)
                    val envelope = exp(-zeta * omega0 * t)
                    val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
                    value = to + frag
                    velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
                } else {
                    val envelope = exp(-omega0 * t)
                    value = to + envelope * (x0 + (v0 + omega0 * x0) * t)
                    velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
                }
                if (initialEnergy == 0f || energy(to - value, velocity) / initialEnergy <= ENERGY_THRESHOLD) {
                    value = to
                    velocity = 0f
                    mode = 0
                    take()?.invoke()
                }
            }
            2 -> {
                time += dt
                val p = min(1f, time / max(duration, 0.0001f))
                val eased = if (p < 0.5f) 2f * p * p else 1f - (-2f * p + 2f) * (-2f * p + 2f) / 2f
                value = from + (to - from) * eased
                if (p >= 1f) {
                    mode = 0
                    take()?.invoke()
                }
            }
        }
    }

    private fun take(): (() -> Unit)? {
        val callback = done
        done = null
        return callback
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

/** The liquid's shape on one side: its edge from that side, the bump's depth, half-height and centre. */
private class Shape(val edge: Float, val reach: Float, val radius: Float, val cy: Float)

/** Everything the frame loop, the touch and the picture share, kept off Compose state. */
private class LiquidMotion(val count: Int) {
    var current = 0
    // Which side is moving: 1 the next page from the right, -1 the previous one from the left, 0 none.
    var side = 0
    val drag = Driven(0f) // dp dragged toward the middle
    val sweep = Driven(0f) // 0 … 1 as the liquid fills the screen
    var busy = false
    val tab = Driven(1f) // the tabs grow back in after a page lands
    var fingerY = 0f
    var tabY = 0f
    var dragging = false
    val pressed = Driven(0f)
    // Where the finger was last seen moving, when (ms), and how fast (dp/s).
    var trackX = 0f
    var trackT = 0f
    var trackV = 0f
    // The frame clock, ms, for timing the finger.
    var clockMs = 0f

    // The scripted finger.
    var clock = 0f
    var looping = false
    var hold = 0f
    var scripted = -1f

    val photos = arrayOfNulls<Bitmap>(count)
    // The screen, in dp, and the safe area at its top and bottom.
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f
    val restY get() = height - bottom - TAB_ABOVE
    // Seconds since the block appeared. The picture reads it, so it is drawn again every frame.
    var elapsed by mutableFloatStateOf(0f)
}

/**
 * Onboarding pages in bold colours that pour over each other like liquid. A tab of the next
 * page sticks out of the right edge; dragging it pulls a smooth wave of that page after the
 * finger, and past a third of the width the wave sweeps across and fills the screen with a
 * springy overshoot. The previous page waits as a tab on the left.
 *
 * @param action The label of the button on the last page.
 * @param autoPlay Swipes through every page and back again, then rests and starts over.
 * @param onChange Called with a page's index once it fills the screen.
 * @param onDone Called when the button on the last page is tapped.
 */
@Composable
fun LiquidSwipeOnboarding(
    modifier: Modifier = Modifier,
    pages: List<LiquidPage> = Pages,
    action: String = "Pick a volcano",
    autoPlay: Boolean = false,
    onChange: ((Int) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val count = pages.size
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val changing by rememberUpdatedState(onChange)
    val finishing by rememberUpdatedState(onDone)
    val motion = remember(pages) { LiquidMotion(count) }
    val paints = remember { LiquidPaints() }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect {
        motion.top = safeTop
        motion.bottom = safeBottom
    }

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.clock = 0f
        motion.hold = 0f
    }

    // Each photo arrives on its own; until then its arch shows a faint wash.
    LaunchedEffect(motion, context) {
        val loader = SingletonImageLoader.get(context)
        pages.forEachIndexed { i, page ->
            launch {
                val request = ImageRequest.Builder(context)
                    .data(photoUri(page.photo))
                    .size(Size.ORIGINAL)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                motion.photos[i] = result.image.toBitmap()
            }
        }
    }

    fun grab(direction: Int): Boolean {
        val m = motion
        val next = m.current + direction
        if (m.busy || next < 0 || next >= count) return false
        m.side = direction
        m.dragging = true
        return true
    }

    /** The page under the liquid has filled the screen: it becomes the current page. */
    fun settle() {
        val m = motion
        m.current += m.side
        m.side = 0
        m.drag.set(0f)
        m.sweep.set(0f)
        m.busy = false
        m.tab.set(0f)
        m.tab.springTo(1f, 0f, TAB_MS, TAB_RATIO)
        changing?.invoke(m.current)
    }

    /** Lets go of the liquid: it sweeps across, or falls back into its tab. */
    fun letGo(velocity: Float, user: Boolean) {
        val m = motion
        val d = m.drag.value
        m.dragging = false
        if (m.side == 0) return
        if (d > m.width * COMMIT_SHARE || (velocity > COMMIT_SPEED && d > 24f)) {
            // Nothing else moves the sweep while it runs, so it always ends in `settle`.
            m.busy = true
            if (user) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (reducedMotion) {
                m.sweep.timeTo(1f, REDUCED_SWEEP_MS) { settle() }
            } else {
                m.sweep.springTo(1f, max(0f, velocity) / m.width * SWEEP_CARRY, SWEEP_MS, SWEEP_RATIO) { settle() }
            }
        } else {
            m.drag.springTo(0f, -velocity, RETURN_MS, RETURN_RATIO) { if (!m.dragging) m.side = 0 }
        }
    }

    fun touched() {
        motion.hold = USER_HOLD
        motion.scripted = -1f
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        val m = motion
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt <= 0f) return@withFrameNanos
                m.clockMs += dt * 1000f
                m.drag.step(dt)
                m.sweep.step(dt)
                m.tab.step(dt)
                m.pressed.step(dt)
                if (m.width > 0f) {
                    if (m.hold > 0f) {
                        m.hold -= dt
                        if (m.hold <= 0f) m.clock = 0f
                    } else if (m.looping) {
                        val before = m.clock
                        val t = (before + dt) % LOOP
                        m.clock = t
                        for (k in Starts.indices) {
                            if (crossed(before, t, Starts[k]) && grab(if (k < Forward.size) 1 else -1)) m.scripted = Starts[k]
                        }
                        val at = m.scripted
                        if (at >= 0f) {
                            // The finger speeds up as it drags, rising a little like a thumb's arc.
                            val u = ((t - at) / DRAG_TIME).coerceIn(0f, 1f)
                            m.drag.set(m.width * DRAG_SHARE * u * u)
                            m.fingerY = m.restY - DRAG_RISE * sin(u * PI.toFloat() / 2f)
                            if (u >= 1f) {
                                m.scripted = -1f
                                letGo(2f * m.width * DRAG_SHARE / DRAG_TIME, false)
                            }
                        }
                    }
                    // The bump's centre follows the finger, and drifts home when let go.
                    val target = if (m.dragging) m.fingerY else m.restY
                    m.tabY += (target - m.tabY) * (1f - exp(-dt * FOLLOW))
                }
                m.elapsed += dt
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(pages.firstOrNull()?.color ?: Color.Black)
            .clearAndSetSemantics {
                contentDescription = pages.joinToString(". ") { it.title.joinToString(" ") }
                customActions = listOf(
                    CustomAccessibilityAction("Next page") {
                        if (!grab(1)) return@CustomAccessibilityAction false
                        motion.drag.set(motion.width * 0.12f)
                        letGo(COMMIT_SPEED * 2f, true)
                        true
                    },
                    CustomAccessibilityAction("Previous page") {
                        if (!grab(-1)) return@CustomAccessibilityAction false
                        motion.drag.set(motion.width * 0.12f)
                        letGo(COMMIT_SPEED * 2f, true)
                        true
                    },
                )
            }
            .pointerInput(motion, reducedMotion) {
                // One recogniser stands in for the race of a pan (after 8 dp of sideways travel)
                // and a tap (a touch lifted within 400 ms that never turned into a pan).
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val m = motion
                    val d = density.density
                    val sx = down.position.x / d
                    val sy = down.position.y / d
                    val button = buttonRect(m, paints, action)
                    var tapAlive = true
                    // The button darkens as soon as it is pressed.
                    if (m.current == count - 1 && sx >= button.left && sx <= button.right && abs(sy - button.centerY()) < button.height()) {
                        m.pressed.timeTo(1f, 100f)
                    }
                    var panning = false
                    var x = sx
                    var y = sy
                    var lifted = false
                    var liftedAt = down.uptimeMillis
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        x = change.position.x / d
                        y = change.position.y / d
                        if (!change.pressed) {
                            lifted = true
                            liftedAt = change.uptimeMillis
                            break
                        }
                        val tx = x - sx
                        if (!panning && abs(tx) > PAN_SLOP) {
                            panning = true
                            tapAlive = false
                            m.pressed.timeTo(0f, 180f)
                            // Pan start.
                            touched()
                            val direction = if (tx < 0f) 1 else -1
                            // A drag can start anywhere: to the left brings the next page, to the
                            // right the previous; a tab still falling back can be caught.
                            if (!m.busy && (m.side == 0 || m.side == direction) && grab(direction)) {
                                // Setting the drag stops a spring that was taking it back.
                                m.drag.set(m.drag.value)
                                m.fingerY = y
                                m.trackX = tx
                                m.trackT = m.clockMs
                                m.trackV = 0f
                            }
                        }
                        if (panning) {
                            change.consume()
                            if (!m.dragging) continue
                            val raw = m.side * -tx
                            // Pulled the wrong way, the tab gives a little; far past the screen it resists.
                            m.drag.set(if (raw < 0f) -rubber(-raw, 0f) * 0.3f else rubber(raw, m.width * 0.85f))
                            // min/max rather than coerceIn, which throws while the height is under 62 dp.
                            m.fingerY = min(m.height - TAB_RADIUS * 0.6f, max(TAB_RADIUS * 0.6f, y))
                            // The finger's own speed, smoothed, so a drag that stopped before letting
                            // go counts as still. Measured across whole frames.
                            val elapsedS = (m.clockMs - m.trackT) / 1000f
                            if (elapsedS > 0.004f && tx != m.trackX) {
                                m.trackV += ((tx - m.trackX) / elapsedS - m.trackV) * 0.6f
                                m.trackX = tx
                                m.trackT = m.clockMs
                            }
                        }
                    }
                    if (panning) {
                        if (m.dragging) {
                            m.hold = USER_HOLD
                            val still = if (lifted) exp(-(m.clockMs - m.trackT) / RELEASE_FADE) else 0f
                            letGo(m.side * -m.trackV * still, true)
                        }
                        return@awaitEachGesture
                    }
                    if (!tapAlive) return@awaitEachGesture
                    m.pressed.timeTo(0f, 180f)
                    if (!lifted || liftedAt - down.uptimeMillis > TAP_MS) return@awaitEachGesture
                    touched()
                    val near = abs(y - m.restY) < TAB_RADIUS + 24f
                    if (m.current == count - 1 && x >= button.left && near) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        finishing?.invoke()
                        return@awaitEachGesture
                    }
                    // Tapping a tab pours its page in.
                    val direction = if (x > m.width - 64f) 1 else if (x < 64f) -1 else 0
                    if (direction == 0 || !near || !grab(direction)) return@awaitEachGesture
                    m.fingerY = m.restY
                    m.drag.set(m.width * 0.12f)
                    letGo(COMMIT_SPEED * 2f, true)
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        if (motion.width != width || motion.height != height) {
            motion.width = width
            motion.height = height
            if (!motion.dragging) {
                motion.fingerY = motion.restY
                motion.tabY = motion.restY
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            scale(density.density, density.density, pivot = Offset.Zero) {
                // Read so the picture is drawn again every frame.
                motion.elapsed
                drawIntoCanvas { drawLiquid(it.nativeCanvas, motion, pages, paints, action, reducedMotion) }
            }
        }
    }
}

private class LiquidPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private fun face(family: String, weight: Int): Typeface {
        val base = Typeface.create(family, Typeface.NORMAL)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, weight, false) else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face("sans-serif-condensed", 800)
        textSize = TITLE_SIZE
    }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face("sans-serif", 500)
        textSize = BODY_SIZE
    }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face("sans-serif", 700)
        textSize = 12f
    }
    val button = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face("sans-serif", 600)
        textSize = 16f
    }
    val arch = Path()
    val liquid = Path()
    val chevron = Path()
    val arrow = Path()
    val rect = RectF()
    val src = android.graphics.Rect()
    val dst = RectF()
    // Each page's copy, broken into lines that fit, for one width.
    var bodiesFor = Float.NaN
    var bodies: List<List<String>> = emptyList()
}

/** Where the button on the last page sits. */
private fun buttonRect(m: LiquidMotion, paints: LiquidPaints, action: String): RectF {
    val w = paints.button.measureText(action) + 66f
    val cy = m.height - m.bottom - TAB_ABOVE
    return RectF(m.width - SIDE - w, cy - BUTTON_HEIGHT / 2f, m.width - SIDE, cy + BUTTON_HEIGHT / 2f)
}

/** Breaks `text` into lines no wider than `max`. */
private fun wrap(text: String, paint: Paint, max: Float): List<String> {
    val lines = ArrayList<String>()
    var line = ""
    for (word in text.split(" ")) {
        val next = if (line.isEmpty()) word else "$line $word"
        if (line.isNotEmpty() && paint.measureText(next) > max) {
            lines.add(line)
            line = word
        } else {
            line = next
        }
    }
    if (line.isNotEmpty()) lines.add(line)
    return lines
}

private fun drawTracked(canvas: android.graphics.Canvas, text: String, paint: Paint, x: Float, y: Float, tracking: Float) {
    var at = x
    val space = paint.measureText("a a") - paint.measureText("aa")
    for (letter in text) {
        val s = letter.toString()
        canvas.drawText(s, at, y, paint)
        at += (if (letter == ' ') space else paint.measureText(s)) + tracking
    }
}

private fun trackedWidth(text: String, paint: Paint, tracking: Float): Float {
    var w = 0f
    for (letter in text) w += paint.measureText(letter.toString()) + tracking
    return w - tracking
}

/** One page: its colour, the photo in an arch, the headline and copy, and the dots. */
private fun drawPage(canvas: android.graphics.Canvas, m: LiquidMotion, pages: List<LiquidPage>, index: Int, paints: LiquidPaints, action: String) {
    val page = pages[index]
    val width = m.width
    val height = m.height
    val count = pages.size
    paints.fill.color = page.color.toArgb()
    canvas.drawRect(-width, -40f, width * 2f, height + 40f, paints.fill)
    val ink = page.ink.toArgb()

    // Header: the brand and the page number.
    val headerY = m.top + 28f
    paints.label.color = ink
    drawTracked(canvas, BRAND, paints.label, SIDE, headerY, 3f)
    val number = "%02d / %02d".format(index + 1, count)
    drawTracked(canvas, number, paints.label, width - SIDE - trackedWidth(number, paints.label, 1.5f), headerY, 1.5f)

    // The photo in an arch: a semicircle on top of a rectangle.
    val ax = SIDE
    val ay = m.top + PHOTO_TOP
    val aw = width - SIDE * 2f
    val ah = height * PHOTO_HEIGHT
    val r = aw / 2f
    val arch = paints.arch
    arch.reset()
    arch.moveTo(ax, ay + ah)
    arch.lineTo(ax, ay + r)
    paints.rect.set(ax, ay, ax + aw, ay + r * 2f)
    arch.arcTo(paints.rect, 180f, 180f, false)
    arch.lineTo(ax + aw, ay + ah)
    arch.close()
    val image = m.photos[index]
    if (image != null) {
        canvas.save()
        canvas.clipPath(arch)
        val iw = image.width.toFloat()
        val ih = image.height.toFloat()
        val scale = max(aw / iw, ah / ih)
        val sw = aw / scale
        val sh = ah / scale
        paints.src.set(((iw - sw) / 2f).toInt(), ((ih - sh) / 2f).toInt(), ((iw + sw) / 2f).toInt(), ((ih + sh) / 2f).toInt())
        paints.dst.set(ax, ay, ax + aw, ay + ah)
        canvas.drawBitmap(image, paints.src, paints.dst, paints.photo)
        canvas.restore()
    } else {
        paints.fill.color = page.ink.copy(alpha = 0.12f).toArgb()
        canvas.drawPath(arch, paints.fill)
    }

    // Headline and copy.
    var y = ay + ah + TITLE_SIZE + 14f
    paints.title.color = ink
    for (line in page.title) {
        canvas.drawText(line, SIDE - 2f, y, paints.title)
        y += TITLE_LEADING
    }
    y += BODY_LEADING - TITLE_LEADING + 14f
    if (paints.bodiesFor != width) {
        paints.bodiesFor = width
        paints.bodies = pages.map { wrap(it.body, paints.body, width - SIDE * 2f - 30f) }
    }
    paints.body.color = page.ink.copy(alpha = 0.78f).toArgb()
    for (line in paints.bodies[index]) {
        canvas.drawText(line, SIDE, y, paints.body)
        y += BODY_LEADING
    }

    // Dots: the current page is a longer bar.
    val dotsY = height - m.bottom - TAB_ABOVE
    var x = DOTS_LEFT
    for (i in 0 until count) {
        val w = if (i == index) 22f else 7f
        paints.fill.color = page.ink.copy(alpha = if (i == index) 1f else 0.35f).toArgb()
        paints.rect.set(x, dotsY - 3.5f, x + w, dotsY + 3.5f)
        canvas.drawRoundRect(paints.rect, 3.5f, 3.5f, paints.fill)
        x += w + 7f
    }

    // The last page has a button where the tab would be.
    if (index == count - 1) {
        val b = buttonRect(m, paints, action)
        paints.fill.color = ink
        canvas.drawRoundRect(b, b.height() / 2f, b.height() / 2f, paints.fill)
        paints.button.color = page.color.toArgb()
        canvas.drawText(action, b.left + 24f, b.centerY() + 5.5f, paints.button)
        val arrowX = b.right - 30f
        val arrowY = b.centerY()
        val arrow = paints.arrow
        arrow.reset()
        arrow.moveTo(arrowX - 7f, arrowY)
        arrow.lineTo(arrowX + 7f, arrowY)
        arrow.moveTo(arrowX + 1f, arrowY - 6f)
        arrow.lineTo(arrowX + 7f, arrowY)
        arrow.lineTo(arrowX + 1f, arrowY + 6f)
        paints.stroke.strokeWidth = 2f
        paints.stroke.color = page.color.toArgb()
        canvas.drawPath(arrow, paints.stroke)
    }
}

/** How much of a resting tab shows: it grows back after a page lands, and the tab on the far
 * side tucks away while the other side's liquid sweeps in, since it will bring another page. */
private fun tabShown(m: LiquidMotion, direction: Int): Float {
    val other = m.side != 0 && m.side != direction
    return m.tab.value * (if (other) max(0f, 1f - m.sweep.value * 2.5f) else 1f)
}

/** The liquid's shape on one side: its tab at rest, pulled after the finger, then sweeping across. */
private fun shapeOf(m: LiquidMotion, direction: Int, reduced: Boolean): Shape? {
    val next = m.current + direction
    if (next < 0 || next >= m.count || m.width <= 0f) return null
    val active = m.side == direction
    val d = if (active) m.drag.value else 0f
    val f = if (active) m.sweep.value else 0f
    val cy = if (active) m.tabY else m.restY
    val reach = max(if (active) 3f else 0f, REST_REACH * (if (active) 1f else tabShown(m, direction)) + d * PULL_REACH)
    val radius = TAB_RADIUS + max(0f, d) * PULL_RADIUS
    if (reduced && f > 0f) return Shape(m.width + 4f, 0f, m.height, cy)
    return Shape((m.width + 60f) * f, reach * (1f - f), radius + m.height * 1.3f * f, cy + (m.height / 2f - cy) * f)
}

/** The liquid page coming in from one side, with its edge straight but for a bump around `cy`. */
private fun liquidPath(path: Path, side: Int, width: Float, height: Float, g: Shape) {
    path.reset()
    path.moveTo(fromSide(side, width, -2f), -2f)
    path.lineTo(fromSide(side, width, g.edge), -2f)
    val r = max(1f, g.radius)
    val top = g.cy - r
    if (top > 0f) path.lineTo(fromSide(side, width, g.edge), top)
    for (k in 0..SAMPLES) {
        val y = top + 2f * r * k / SAMPLES
        path.lineTo(fromSide(side, width, g.edge + g.reach * bump((y - g.cy) / r)), y)
    }
    val end = max(height + 2f, g.cy + r)
    path.lineTo(fromSide(side, width, g.edge), end)
    path.lineTo(fromSide(side, width, -2f), end)
    path.close()
}

/** The chevron fades as its tab is pulled, and with the tab. */
private fun chevronShown(m: LiquidMotion, direction: Int): Float {
    val active = m.side == direction
    return max(0f, 1f - (if (active) m.drag.value / 60f + m.sweep.value * 4f else 0f)) * tabShown(m, direction)
}

private fun drawLiquid(canvas: android.graphics.Canvas, m: LiquidMotion, pages: List<LiquidPage>, paints: LiquidPaints, action: String, reduced: Boolean) {
    val width = m.width
    val height = m.height
    if (width <= 0f) return
    val current = m.current
    val side = m.side
    val d = m.drag.value
    val f = m.sweep.value

    // The covered page is pushed away from the liquid; the incoming page rides in behind its edge.
    canvas.save()
    if (!reduced) canvas.translate(-side * (d * 0.1f + width * PUSH * min(1f, f)), 0f)
    drawPage(canvas, m, pages, current, paints, action)
    val shade = if (current == pages.size - 1 && side == 0) m.pressed.value * 0.22f else 0f
    if (shade > 0f) {
        val b = buttonRect(m, paints, action)
        paints.fill.color = Color.Black.copy(alpha = safeAlpha(shade)).toArgb()
        canvas.drawRoundRect(b, b.height() / 2f, b.height() / 2f, paints.fill)
    }
    canvas.restore()

    for (direction in intArrayOf(-1, 1)) {
        val g = shapeOf(m, direction, reduced) ?: continue
        val opacity = if (reduced && side == direction && f > 0f) f else 1f
        liquidPath(paints.liquid, direction, width, height, g)
        if (opacity < 1f) canvas.saveLayerAlpha(0f, 0f, width, height, (opacity * 255f).toInt()) else canvas.save()
        canvas.clipPath(paints.liquid)
        if (!reduced) canvas.translate(direction * width * PARALLAX * (1f - (if (side == direction) f else 0f)), 0f)
        drawPage(canvas, m, pages, current + direction, paints, action)
        canvas.restore()
    }

    // A chevron on each tab, in the colour of the page it brings, fading as the tab is pulled.
    paints.stroke.strokeWidth = 2.2f
    for (direction in intArrayOf(-1, 1)) {
        val g = shapeOf(m, direction, reduced) ?: continue
        val alpha = chevronShown(m, direction)
        if (alpha <= 0f) continue
        val tip = g.edge + g.reach
        val cx = if (direction > 0) width - tip + 11f else tip - 11f
        val size = 5.5f
        val path = paints.chevron
        path.reset()
        path.moveTo(cx + direction * size * 0.45f, g.cy - size)
        path.lineTo(cx - direction * size * 0.45f, g.cy)
        path.lineTo(cx + direction * size * 0.45f, g.cy + size)
        paints.stroke.color = pages[current + direction].ink.copy(alpha = safeAlpha(alpha)).toArgb()
        canvas.drawPath(path, paints.stroke)
    }
}
