package com.ngocdevv.nativecook.blocks.cards.bentoexpandgrid

import android.graphics.Bitmap
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// The grid: margins and gaps in dp, the corner radius of a tile, the title block above it.
internal const val MARGIN = 16f
internal const val GAP = 10f
internal const val TILE_RADIUS = 22f
internal const val TITLE_BLOCK = 92f
// Tiles narrower than this set their caption smaller, on two lines.
internal const val NARROW = 130f
// Photos: the grid and the page's header share one copy; the pair inside the page is smaller.
internal const val HEADER_PX = 1200
internal const val PAIR_PX = 600

// The page: its header takes HEADER_SHARE of the screen; the sheet below overlaps it by OVERLAP
// and the header slides up at PARALLAX of the scroll, so the sheet glides over the photo.
private const val HEADER_SHARE = 0.48f
internal const val OVERLAP = 28f
internal const val PARALLAX = 0.45f
// A stat or quote face is drawn at its page size and scaled down into its tile, so its type
// stays sharp: up to FACE_GROW times the tile's size, never wider than the screen.
private const val FACE_GROW = 1.75f

// Opening: the tile's frame grows to the screen on a spring, its corners closing to 0. The other
// tiles are pushed away from it by PUSH dp, shrink by SHRINK and fade, nearest first, each
// starting STAGGER later. The page's sections rise REVEAL_RISE dp as they fade in, one by one.
private const val OPEN_MS = 800f
private const val OPEN_RATIO = 0.88f
private const val CLOSE_MS = 680f
private const val CLOSE_RATIO = 0.9f
private const val PUSH = 44f
private const val SHRINK = 0.1f
private const val STAGGER = 0.07f
private const val REVEAL_FROM = 0.42f
private const val REVEAL_STEP = 0.06f
private const val REVEAL_SPAN = 0.3f
internal const val REVEAL_RISE = 18f
// The sheet's paper comes in quickly, early in the flight, so it never sits half-seen over a dark tile.
internal const val SHEET_FROM = 0.22f
internal const val SHEET_TO = 0.42f
// The header fills the frame until HEADER_GIVE_FROM of the way, and is down to its own height by
// HEADER_GIVE_TO, the sheet sliding up under it; so a closing page never shows an empty card.
internal const val HEADER_GIVE_FROM = 0.2f
internal const val HEADER_GIVE_TO = 0.8f
private const val FADE_TIME = 220f

// Closing by hand: the page follows a drag down, shrinking by up to DISMISS_SHRINK over
// DISMISS_SPAN of the screen and rounding its corners to DISMISS_RADIUS, the grid peeking back in
// behind it. Past DISMISS_DISTANCE or DISMISS_SPEED it closes into its tile.
private const val DISMISS_SPAN = 0.5f
private const val DISMISS_SHRINK = 0.34f
private const val DISMISS_RADIUS = 36f
private const val DISMISS_DISTANCE = 110f
private const val DISMISS_SPEED = 800f
private const val FOLLOW_X = 0.6f
private const val FOLLOW_Y = 0.75f
private const val PEEK = 0.8f
private const val PEEK_SPAN = 0.25f
private const val DRAG_BACK_MS = 420f
private const val DRAG_BACK_RATIO = 0.82f
// The drag down starts after this much travel, and never once the finger has gone this far sideways.
private const val DISMISS_SLOP = 10f
private const val DISMISS_FAIL_X = 24f
// Press feedback on a tile.
private const val PRESS_SCALE = 0.97f
private const val PRESS_MS = 120f

// autoPlay loop (seconds): the big photo opens, its page scrolls down and back, it is dragged down
// and closes into its tile; the stat opens, scrolls, closes from its button; a rest.
private const val LOOP = 14f
private const val OPEN_A = 1.0f
private const val SCROLL_A = 2.3f
private const val BACK_A = 3.9f
private const val DRAG_A = 5.0f
private const val CLOSE_A = 5.5f
private const val OPEN_B = 7.1f
private const val SCROLL_B = 8.5f
private const val BACK_B = 10.0f
private const val CLOSE_B = 11.0f
private const val NOTE_A = 0
private const val NOTE_B = 1
private const val SCROLL_DEPTH = 240f
private const val SCROLL_MS = 1100f
private const val BACK_MS = 800f
private const val DRAG_PULL = 150f
// After a touch the loop waits this long; longer while a page is open, so it can be read.
private const val USER_HOLD = 3f
private const val PAGE_HOLD = 7f
// A scroll let go glides on and slows like an iOS scroll view: its speed keeps this share per ms.
private const val DECELERATION = 0.998f

private val EaseInOutStrong = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

internal fun clamp01(x: Float) = min(1f, max(0f, x))

internal fun smooth(from: Float, to: Float, x: Float): Float {
    val u = clamp01((x - from) / (to - from))
    return u * u * (3 - 2 * u)
}

internal fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** How much of a block of the page has risen in, by its place in the stagger. */
internal fun revealOf(open: Float, order: Int): Float {
    val from = REVEAL_FROM + order * REVEAL_STEP
    return smooth(from, from + REVEAL_SPAN, open)
}

/** A rectangle in dp. */
internal class Box4(val x: Float, val y: Float, val w: Float, val h: Float)

/** The page's frame, its corner radius, and how much a drag down has shrunk it. */
internal class PageFrame(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val s: Float)

internal class Push(val x: Float, val y: Float, val delay: Float)

internal class Loaded(val image: Bitmap, val at: Float)

/**
 * A number moved a frame at a time the way Reanimated moves a shared value: set at once, by
 * `withSpring({ duration, dampingRatio, velocity })`, or by `withTiming({ duration, easing })`.
 */
internal class Driven(initial: Float) {
    var value = initial
        private set
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
    private var curve: (Float) -> Float = ::quadInOut

    /** True while a spring or a timing is moving the value. */
    val moving: Boolean get() = mode != 0

    /** Puts the value somewhere at once, stopping whatever was moving it. */
    fun set(next: Float) {
        value = next
        mode = 0
        velocity = 0f
    }

    /** A spring under way hands its speed on to the new one. */
    fun springTo(target: Float, durationMs: Float, dampingRatio: Float, speed: Float = 0f) {
        val v = (if (mode == 1) velocity else 0f) + speed
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

    /** Reanimated's default easing is a quadratic ease-in-out. */
    fun timeTo(target: Float, durationMs: Float, easing: (Float) -> Float = ::quadInOut) {
        mode = 2
        from = value
        to = target
        time = 0f
        duration = max(durationMs / 1000f, 0.0001f)
        velocity = 0f
        curve = easing
    }

    /** Moves on by `dt` seconds; true on the frame the motion comes to rest on its target. */
    fun step(dt: Float): Boolean {
        when (mode) {
            1 -> {
                val x0 = value - to
                val v0 = velocity
                if (zeta < 1f) {
                    val s = sin(omega1 * dt)
                    val c = cos(omega1 * dt)
                    val envelope = exp(-zeta * omega0 * dt)
                    val frag = envelope * (s * ((v0 + zeta * omega0 * x0) / omega1) + x0 * c)
                    value = to + frag
                    velocity = -zeta * omega0 * frag + envelope * (c * (v0 + zeta * omega0 * x0) - omega1 * x0 * s)
                } else {
                    val envelope = exp(-omega0 * dt)
                    value = to + envelope * (x0 + (v0 + omega0 * x0) * dt)
                    velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
                }
                if (initialEnergy == 0f || energy(to - value, velocity) / initialEnergy <= ENERGY_THRESHOLD) {
                    value = to
                    velocity = 0f
                    mode = 0
                    return true
                }
            }
            2 -> {
                time += dt
                val p = clamp01(time / duration)
                value = from + (to - from) * curve(p)
                if (p >= 1f) {
                    mode = 0
                    return true
                }
            }
        }
        return false
    }

    private fun energy(x: Float, v: Float) = 0.5f * stiffness * x * x + 0.5f * v * v

    companion object {
        const val ENERGY_THRESHOLD = 6e-9f

        fun quadInOut(t: Float) = if (t < 0.5f) 2f * t * t else 1f - 2f * (1f - t) * (1f - t)

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

/**
 * Everything the frame loop, the gestures and the views share. The clock and the open note are
 * Compose state: the clock changes every frame, so whatever reads it is drawn again every frame.
 */
internal class BentoMotion(val notes: List<BentoNote>) {
    var width = 0f
        private set
    var height = 0f
        private set
    var top = 0f
        private set
    var bottom = 0f
        private set
    var rects: List<Box4> = emptyList()
        private set
    private var plan: List<List<Push>> = emptyList()
    // The page: 0 in its tile, 1 full screen; which tile is open (null for none).
    val open = Driven(0f)
    var active by mutableStateOf<Int?>(null)
        private set
    var closing = false
        private set
    val dragX = Driven(0f)
    val dragY = Driven(0f)
    var dragging = false
        private set
    // The page's scroll, its fling speed and how far it can go.
    var scrollY = 0f
        private set
    private var scrollSpeed = 0f
    private var maxScroll = 0f
    private val autoScroll = Driven(0f)
    private val pressed = HashMap<Int, Driven>()
    var elapsed by mutableFloatStateOf(0f)
    val images = HashMap<String, Loaded>()
    // autoPlay.
    private var clock = 0f
    private var looping = false
    private var hold = 0f
    var reduced = false
    // The touch on the page: 0 undecided, 1 scrolling, 2 dragging the page down.
    private var pageTouch = 0
    private var dismissFailed = false
    private var lastTranslation = 0f

    var haptic: () -> Unit = {}

    fun restartLoop(auto: Boolean) {
        looping = auto
        clock = 0f
        hold = 0f
    }

    fun layout(width: Float, height: Float, top: Float, bottom: Float) {
        if (width == this.width && height == this.height && top == this.top && bottom == this.bottom) return
        this.width = width
        this.height = height
        this.top = top
        this.bottom = bottom
        // The tiles on the grid, from the space between the title block and the bottom edge.
        val gridTop = top + TITLE_BLOCK
        val gridBottom = height - bottom - MARGIN
        val colW = (width - MARGIN * 2 - GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val rowH = (gridBottom - gridTop - GAP * (GRID_ROWS - 1)) / GRID_ROWS
        rects = notes.map { n ->
            Box4(
                MARGIN + n.col * (colW + GAP),
                gridTop + n.row * (rowH + GAP),
                n.cols * colW + (n.cols - 1) * GAP,
                n.rows * rowH + (n.rows - 1) * GAP,
            )
        }
        // For every pair of tiles: which way the second is pushed when the first opens and how
        // late it starts, nearest first.
        plan = rects.map { from ->
            val fx = from.x + from.w / 2
            val fy = from.y + from.h / 2
            val away = rects.map { to ->
                val dx = to.x + to.w / 2 - fx
                val dy = to.y + to.h / 2 - fy
                val d = hypot(dx, dy).let { if (it == 0f) 1f else it }
                Triple(dx / d, dy / d, d)
            }
            val delay = FloatArray(rects.size)
            away.indices.sortedBy { away[it].third }.forEachIndexed { rank, i -> delay[i] = max(0, rank - 1) * STAGGER }
            away.mapIndexed { i, a -> Push(a.first, a.second, delay[i]) }
        }
    }

    val headerHeight: Float get() = (height * HEADER_SHARE).roundToInt().toFloat()

    // MARK: Frame

    fun step(dt: Float) {
        if (dt <= 0f || width == 0f) return
        elapsed += dt
        loop(dt)
        if (open.step(dt) && closing && open.value == 0f) finishClose()
        dragX.step(dt)
        dragY.step(dt)
        for (value in pressed.values) value.step(dt)
        // The loop scrolls the open page itself.
        if (autoScroll.moving) {
            autoScroll.step(dt)
            if (hold <= 0f && active != null) {
                scrollY = min(max(autoScroll.value, 0f), maxScroll)
                scrollSpeed = 0f
            }
        }
        // A scroll let go glides on and slows the way a scroll view does.
        if (pageTouch != 1 && scrollSpeed != 0f) {
            scrollY += scrollSpeed * dt
            scrollSpeed *= DECELERATION.pow(dt * 1000f)
            if (scrollY <= 0f || scrollY >= maxScroll) {
                scrollY = min(max(scrollY, 0f), maxScroll)
                scrollSpeed = 0f
            }
            if (abs(scrollSpeed) < 4f) scrollSpeed = 0f
        }
    }

    private fun scrollTo(target: Float, ms: Float) {
        autoScroll.timeTo(target, ms, EaseInOutStrong::transform)
    }

    private fun loop(dt: Float) {
        if (hold > 0f) {
            if (!dragging) hold -= dt
            if (hold <= 0f) {
                // The loop starts over from the grid: a page left open closes first.
                clock = 0f
                if (looping) closeNote(false, 0f)
            }
            return
        }
        if (!looping) return
        val before = clock
        val t = (before + dt) % LOOP
        clock = t
        fun passed(at: Float) = (before < at && t >= at) || (t < before && (at > before || at <= t))
        if (passed(OPEN_A)) openNote(NOTE_A, false)
        if (passed(OPEN_B)) openNote(NOTE_B, false)
        if (passed(SCROLL_A) || passed(SCROLL_B)) scrollTo(SCROLL_DEPTH, SCROLL_MS)
        if (passed(BACK_A) || passed(BACK_B)) scrollTo(0f, BACK_MS)
        if (passed(DRAG_A) && active != null) {
            dragY.timeTo(DRAG_PULL, 460f, EaseInOutStrong::transform)
            dragX.timeTo(-14f, 460f, EaseInOutStrong::transform)
        }
        if (passed(CLOSE_A)) closeNote(false, 1.2f)
        if (passed(CLOSE_B)) closeNote(false, 0f)
    }

    // MARK: Page

    private fun openNote(index: Int, user: Boolean) {
        if (active != null || index < 0 || index >= notes.size) return
        active = index
        closing = false
        open.set(0f)
        dragX.set(0f)
        dragY.set(0f)
        scrollY = 0f
        scrollSpeed = 0f
        autoScroll.set(0f)
        if (user) {
            hold = PAGE_HOLD
            haptic()
        }
        if (reduced) open.timeTo(1f, FADE_TIME) else open.springTo(1f, OPEN_MS, OPEN_RATIO)
    }

    private fun closeNote(user: Boolean, velocity: Float) {
        if (active == null || closing) return
        closing = true
        if (reduced) open.timeTo(0f, FADE_TIME) else open.springTo(0f, CLOSE_MS, CLOSE_RATIO, -velocity)
        if (user) haptic()
    }

    private fun finishClose() {
        active = null
        closing = false
        dragX.set(0f)
        dragY.set(0f)
    }

    private fun touched() {
        hold = if (active != null) PAGE_HOLD else USER_HOLD
    }

    fun close() {
        touched()
        closeNote(true, 0f)
    }

    fun tapTile(index: Int) {
        touched()
        openNote(index, true)
    }

    fun press(index: Int, down: Boolean) {
        val value = pressed.getOrPut(index) { Driven(1f) }
        value.timeTo(if (down) PRESS_SCALE else 1f, if (down) PRESS_MS else PRESS_MS * 1.6f)
    }

    fun pressScale(index: Int) = pressed[index]?.value ?: 1f

    fun sheetMeasured(sheetHeight: Float) {
        // The scroll view is the screen tall, its content the sheet below the header's overlap.
        maxScroll = max(0f, headerHeight - OVERLAP + sheetHeight - height)
        scrollY = min(scrollY, maxScroll)
    }

    // One drag on the page both scrolls it and, from the top, pulls it down: the scroll follows the
    // finger; once the finger has gone 10 dp down with the page at its top, the page follows.
    fun pageDrag(tx: Float, ty: Float) {
        if (active == null || closing) return
        if (pageTouch == 0) {
            pageTouch = 1
            dismissFailed = false
            lastTranslation = 0f
            scrollSpeed = 0f
            touched()
        }
        if (abs(tx) > DISMISS_FAIL_X && pageTouch == 1) dismissFailed = true
        if (pageTouch == 1) {
            if (!dismissFailed && ty >= DISMISS_SLOP && scrollY <= 2f) {
                pageTouch = 2
                dragging = true
                touched()
            } else {
                scrollY = min(max(scrollY - (ty - lastTranslation), 0f), maxScroll)
                lastTranslation = ty
                return
            }
        }
        if (reduced) return
        dragX.set(tx)
        dragY.set(max(0f, ty))
    }

    fun pageRelease(ty: Float, rawVx: Float, rawVy: Float) {
        // A non-finite speed would carry NaN into the page's frame, and roundToPx throws on NaN.
        val vx = if (rawVx.isFinite()) rawVx else 0f
        val vy = if (rawVy.isFinite()) rawVy else 0f
        val touch = pageTouch
        pageTouch = 0
        dragging = false
        if (active == null || closing) return
        if (touch == 1) {
            scrollSpeed = -vy
            return
        }
        if (touch != 2) return
        if (ty > DISMISS_DISTANCE || vy > DISMISS_SPEED) {
            closeNote(true, max(0f, vy) / (height * 0.6f))
        } else {
            dragX.springTo(0f, DRAG_BACK_MS, DRAG_BACK_RATIO, vx)
            dragY.springTo(0f, DRAG_BACK_MS, DRAG_BACK_RATIO, vy)
        }
    }

    // MARK: Geometry

    fun pull() = clamp01(dragY.value / (height * DISMISS_SPAN))

    /** How far the grid has come back in behind a page dragged down, 0…1. */
    fun peek() = clamp01(dragY.value / (height * PEEK_SPAN))

    /** The page's frame for the tile at [index]: between its tile and the screen, shrunk by a drag down. */
    fun frame(index: Int): PageFrame {
        val tile = if (reduced) Box4(0f, 0f, width, height) else rects[index]
        val p = min(1f, open.value)
        val pull = pull()
        val s = 1f - DISMISS_SHRINK * pull
        val fw = width * s
        val fh = height * s
        val fx = (width - fw) / 2f + dragX.value * FOLLOW_X
        val fy = max(0f, dragY.value) * FOLLOW_Y
        val fr = DISMISS_RADIUS * clamp01(pull * 4f)
        return PageFrame(
            mix(tile.x, fx, p),
            mix(tile.y, fy, p),
            max(1f, mix(tile.w, fw, p)),
            max(1f, mix(tile.h, fh, p)),
            max(0f, mix(TILE_RADIUS, fr, p)),
            s,
        )
    }

    /** How big a stat or quote face is drawn, relative to its tile. */
    fun grow(index: Int) = min(FACE_GROW, width / rects[index].w)

    /** A tile's push while another is open, written into [out] as alpha, x, y, scale. */
    fun tileState(index: Int, out: FloatArray) {
        val k = active
        out[0] = 1f
        out[1] = 0f
        out[2] = 0f
        out[3] = 1f
        if (k == null) return
        if (k == index) {
            out[0] = 0f
            return
        }
        val move = plan[k][index]
        val p = clamp01(open.value) * (1f - PEEK * peek())
        val maxDelay = STAGGER * (plan.size - 2)
        val q = smooth(move.delay, move.delay + 1f - maxDelay, p)
        val push = if (reduced) 0f else PUSH * q
        out[0] = 1f - q
        out[1] = move.x * push
        out[2] = move.y * push
        out[3] = if (reduced) 1f else 1f - SHRINK * q
    }

    /** The title block fades as a page covers it, and back as the page is dragged down. */
    val titleAlpha: Float get() = if (active == null) 1f else 1f - clamp01(open.value) * (1f - PEEK * peek())
}
