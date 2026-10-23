package com.ngocdevv.nativecook.blocks.media.scattercollagehero

import android.graphics.Bitmap
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Photo widths in pixels: a tile is drawn at most ~200 dp wide, the hero fills the screen.
internal const val TILE_PX = 480
internal const val HERO_PX = 1000

// The collage is a field of photos flying slowly towards the viewer. Each tile lives on a cycle:
// it is born small near the vanishing point, grows by GROWTH times as it comes closer, moving
// out along its own direction, and leaves past the edges, while a new one is born behind it.
// Growth is exponential in time, so the zoom feels even rather than speeding up.
private const val GROWTH = 5.0
// Where the field opens from, as shares of the screen: a little below the middle.
private const val VANISH_X = 0.5
private const val VANISH_Y = 0.52
// How far a tile's centre is from the vanishing point at the end of its cycle, as shares of the
// screen's width and height (times a per-tile reach of 0.84…1.16).
private const val SPREAD_X = 0.92
private const val SPREAD_Y = 0.6
// A tile's longer side at the end of its cycle, in dp on a 402 dp wide screen (times 0.82…1.18).
private const val BASE_SIZE = 236.0
// A tile stays hidden for the first share of its cycle, fades in until FADE_IN, and fades out
// over the last share, so the far, tiny ones never clutter the middle.
private const val FADE_FROM = 0.14
private const val FADE_IN = 0.36
private const val FADE_OUT = 0.86
// Corner radius of a tile: a floor plus a share of its shorter side.
private const val RADIUS_MIN = 3.0
private const val RADIUS_SHARE = 0.05
// Each photo fades in over this many seconds once it has loaded.
internal const val PHOTO_FADE = 0.4f
// Successive tiles are a golden angle apart, so the field never lines up.
private const val GOLDEN = 2.399963

// Opening a tile: it flies out of the collage and grows to fill the screen on a spring, its
// corners closing to 0, while the other tiles fade out. Closing reverses it into the tile's slot.
private const val OPEN_MS = 700f
private const val OPEN_RATIO = 0.86f
private const val CLOSE_MS = 640f
private const val CLOSE_RATIO = 0.9f
private const val FADE_TIME = 240f
// The hero's copy rises in, line by line, over the last part of the flight.
private const val REVEAL_FROM = 0.66f
private const val REVEAL_STEP = 0.04f
private const val REVEAL_SPAN = 0.14f
internal const val REVEAL_RISE = 22f
// While a hero is dragged down, the collage fades back in behind it, up to PEEK, fully so once
// the finger has pulled PEEK_SPAN of the screen's height.
internal const val PEEK = 0.7f
internal const val PEEK_SPAN = 0.3f
// The full-size photo fades over the small one over this many seconds once it arrives.
internal const val SHARP_FADE = 0.3f
// Full-size photos kept in memory.
private const val SHARP_KEEP = 10
// At most this many heroes open at once: the visible tiles, read top to bottom.
private const val MAX_PAGES = 8

// Paging between heroes: one screen per hero, the photo lagging its frame by PARALLAX of the
// distance and the copy by COPY_PARALLAX, so the next hero pushes the last one out in layers.
internal const val PARALLAX = 0.42f
internal const val COPY_PARALLAX = 0.3f
private const val PAGE_MS = 520f
private const val PAGE_RATIO = 0.88f
private const val PAGE_THROW = 0.22f
private const val RUBBER = 0.28f

// Dragging a hero down shrinks it towards a card that follows the finger; past DISMISS_DISTANCE
// or DISMISS_SPEED it closes into its slot, otherwise it springs back.
private const val DISMISS_SPAN = 0.55f
private const val DISMISS_SHRINK = 0.3f
private const val DISMISS_DISTANCE = 120f
private const val DISMISS_SPEED = 750f
private const val DISMISS_RADIUS = 44f
private const val DRAG_BACK_MS = 420f
private const val DRAG_BACK_RATIO = 0.82f

// The chips under the collage: the active one's pill slides, the other photos dim.
private const val CHIP_MS = 420f
private const val CHIP_RATIO = 0.8f
private const val FILTER_TIME = 360f
private const val DIMMED = 0.16f

// autoPlay loop (seconds). The collage flies; a tile opens; the hero moves to the next one; it is
// dragged down and closes into that tile's slot; the collage flies on. The flight slows to a stop
// while a hero is open, so its slot waits for it. Each tile completes exactly one cycle per loop,
// so the last frame of the loop meets the first.
private const val LOOP = 15.0
private const val OPEN_AT = 3.2
private const val PAGE_AT = 6.3
private const val DRAG_AT = 8.6
private const val DISMISS_AT = 9.0
private const val STOP_AT = 3.2
private const val STOP_TIME = 0.6
private const val GO_AT = 9.15
private const val GO_TIME = 0.85
private const val DRAG_PULL = 150f
// After a touch the loop waits this long before it starts over; longer while a hero is open.
private const val USER_HOLD = 3.5f
private const val HERO_HOLD = 7f

private val EaseInOutStrong = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

internal fun clamp01(x: Float) = min(1f, max(0f, x))

/** roundToInt that never throws: NaN (a pager thrown by a bad velocity) gives 0. */
internal fun collageRound(x: Float) = if (x.isNaN()) 0 else x.roundToInt()

private fun smooth(from: Double, to: Double, x: Double): Double {
    val u = min(1.0, max(0.0, (x - from) / (to - from)))
    return u * u * (3 - 2 * u)
}

/** How much of a line of the hero's copy has risen in, by its place in the stagger. */
internal fun reveal(open: Float, order: Int): Float {
    val from = REVEAL_FROM + order * REVEAL_STEP
    return smooth(from.toDouble(), (from + REVEAL_SPAN).toDouble(), open.toDouble()).toFloat()
}

/** Integral of a smoothstep that rises from 0 at `at` to 1 over `span` seconds, up to time t. */
private fun rampArea(t: Double, at: Double, span: Double): Double {
    if (t <= at) return 0.0
    if (t >= at + span) return span / 2 + (t - at - span)
    val u = (t - at) / span
    return span * (u * u * u - (u * u * u * u) / 2)
}

/** Seconds of flight the loop has run by time t: the flight stops for the hero and starts again. */
private fun flown(t: Double) = t - rampArea(t, STOP_AT, STOP_TIME) + rampArea(t, GO_AT, GO_TIME)

// One full cycle of a tile takes the flight time of one loop.
private val CYCLE = flown(LOOP)

/** Where a tile is, in dp: its box, corner radius, place in its cycle and opacity. */
internal class Geo(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val u: Float, val alpha: Float)

/** The fixed part of each tile: when it is born, which way it travels, its size and shape. */
internal class Tile(val phase: Double, val angle: Double, val reach: Double, val width: Double, val height: Double) {
    /** Where the tile is on the screen for a given flight (0…1 is one cycle). */
    fun at(travel: Double, width: Float, height: Float): Geo {
        val raw = phase + travel
        val u = raw - floor(raw)
        val s = GROWTH.pow(u - 1)
        val base = BASE_SIZE * (width / 402.0) * s
        val w = base * this.width
        val h = base * this.height
        val cx = width * VANISH_X + cos(angle) * width * SPREAD_X * reach * s
        val cy = height * VANISH_Y + sin(angle) * height * SPREAD_Y * reach * s
        return Geo(
            (cx - w / 2).toFloat(),
            (cy - h / 2).toFloat(),
            w.toFloat(),
            h.toFloat(),
            (RADIUS_MIN + RADIUS_SHARE * min(w, h)).toFloat(),
            u.toFloat(),
            (smooth(FADE_FROM, FADE_IN, u) * (1 - smooth(FADE_OUT, 1.0, u))).toFloat(),
        )
    }

    companion object {
        private fun noise(i: Int, k: Int): Double {
            val x = sin(i * 127.1 + k * 311.7) * 43758.5453
            return x - floor(x)
        }

        fun make(sessions: List<CollageSession>) = sessions.mapIndexed { i, s ->
            val size = 0.82 + 0.36 * noise(i, 2)
            val (w, h) = when (s.shape) {
                CollageShape.Portrait -> 0.78 to 1.04
                CollageShape.Landscape -> 1.08 to 0.78
                CollageShape.Square -> 0.92 to 0.92
            }
            Tile(i.toDouble() / sessions.size, i * GOLDEN + 0.5, 0.84 + 0.32 * noise(i, 1), w * size, h * size)
        }
    }
}

/** A photo that has arrived, and when (on the motion's clock), so it can fade in. */
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

internal enum class CollageHaptic { Open, Tick, Start }

/**
 * Everything the frame loop, the gestures and the pictures share. Only the clock, the open list
 * and the chip are Compose state: the clock changes every frame, so whatever reads it is drawn
 * again every frame.
 */
internal class CollageMotion(val sessions: List<CollageSession>) {
    val tiles = Tile.make(sessions)
    val count = sessions.size
    // The screen, in dp, and the safe area at its top and bottom.
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f
    // How far the field has flown, in cycles (0…1); a clock for the photo fades.
    var travel = 0.0
    var elapsed by mutableFloatStateOf(0f)
    val low = arrayOfNulls<Loaded>(count)
    // Full-size copies for the heroes, fetched when a hero opens.
    val high = arrayOfNulls<Loaded>(count)
    // The hero: 0 in its slot, 1 full screen. `page` is the hero in front, `ids` the open list.
    val open = Driven(0f)
    val page = Driven(0f)
    val dragX = Driven(0f)
    val dragY = Driven(0f)
    var ids: List<Int> = emptyList()
    var heroOn = false
    var closing = false
    /** The heroes whose copy and controls are mounted, or null while the collage shows. */
    var hero by mutableStateOf<List<Int>?>(null)
    // The pan's direction once it is clear: 1 across (paging), 2 down (closing).
    var axis = 0
    var panFrom = 0f
    // The chips' filter, faded from one category to the next.
    var chip by mutableIntStateOf(0)
    private var catFrom = 0
    private var catTo = 0
    val catMix = Driven(1f)
    val pillX = Driven(0f)
    val pillW = Driven(0f)
    private val chipBoxes = HashMap<Int, Pair<Float, Float>>()
    // autoPlay.
    private var clock = 0.0
    var looping = false
    private var hold = 0f
    var reduced = false

    var haptic: (CollageHaptic) -> Unit = {}
    /** Asks for the full-size photos of a list of heroes, the first one first. */
    var sharpen: (List<Int>) -> Unit = {}
    var onStart: ((CollageSession) -> Unit)? = null

    fun restartLoop(auto: Boolean) {
        looping = auto
        clock = 0.0
        hold = 0f
    }

    fun sharpArrived(index: Int, image: Bitmap, keep: List<Int>) {
        high[index] = Loaded(image, elapsed)
        var held = high.count { it != null }
        for (k in high.indices) {
            if (held <= SHARP_KEEP) break
            if (high[k] != null && k !in keep) {
                high[k] = null
                held -= 1
            }
        }
    }

    // MARK: Frame

    fun step(dt: Float) {
        if (dt <= 0f || width == 0f) return
        elapsed += dt
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f) {
                clock = 0.0
                // A hero left open goes back to its slot before the loop starts over.
                if (looping && heroOn && axis == 0) closeHero(false, 0f)
            }
        }
        if (looping && hold <= 0f) {
            val before = clock
            val t = (before + dt) % LOOP
            clock = t
            val step = if (t >= before) flown(t) - flown(before) else flown(LOOP) - flown(before) + flown(t)
            travel = (travel + step / CYCLE) % 1.0
            fun passed(at: Double) = (before < at && t >= at) || (t < before && (at > before || at <= t))
            if (passed(OPEN_AT)) openTile(showcase(), false)
            if (passed(PAGE_AT) && heroOn && ids.size > 1) {
                val k = collageRound(page.value)
                goTo(if (k + 1 < ids.size) k + 1 else k - 1, false, 0f)
            }
            if (passed(DRAG_AT) && heroOn) {
                dragY.timeTo(DRAG_PULL, 380f, EaseInOutStrong::transform)
                dragX.timeTo(-12f, 380f, EaseInOutStrong::transform)
            }
            if (passed(DISMISS_AT)) closeHero(false, 1.4f)
        } else if (!reduced) {
            // By hand the field flies whenever no hero covers it.
            travel = (travel + dt * (1f - clamp01(open.value)) / CYCLE) % 1.0
        }

        if (open.step(dt) && closing && open.value == 0f) finishClose()
        page.step(dt)
        dragX.step(dt)
        dragY.step(dt)
        catMix.step(dt)
        pillX.step(dt)
        pillW.step(dt)
    }

    // MARK: Collage

    /** The category's weight on a tile: 1 when it is shown, DIMMED when filtered out. */
    private fun shown(index: Int, category: Int) =
        if (category == 0 || CollageCategories[category] == sessions[index].category) 1f else DIMMED

    /** How strongly each tile is drawn this frame, from the chips' filter. */
    fun weight(index: Int): Float {
        val a = shown(index, catFrom)
        return a + (shown(index, catTo) - a) * catMix.value
    }

    fun geo(index: Int) = tiles[index].at(travel, width, height)

    /** The tiles fully on screen, read top to bottom, that a hero can page through. */
    private fun heroList(must: Int): List<Int> {
        class Found(val i: Int, val y: Float, val area: Float)
        val found = ArrayList<Found>()
        for (i in tiles.indices) {
            val g = geo(i)
            val inside = g.x >= 6 && g.x + g.w <= width - 6 && g.y >= top + 6 && g.y + g.h <= height - bottom - 6
            if (i == must || (inside && g.alpha > 0.9f && shown(i, catTo) == 1f)) {
                found.add(Found(i, g.y + g.h / 2, g.w * g.h))
            }
        }
        // The largest few, always with the tapped one, then read top to bottom.
        found.sortByDescending { it.area }
        val kept = ArrayList(found.take(MAX_PAGES))
        if (kept.none { it.i == must }) found.firstOrNull { it.i == must }?.let { kept[kept.size - 1] = it }
        kept.sortBy { it.y }
        return kept.map { it.i }
    }

    /** The tile the loop opens: a mid-sized one near the middle, fully on screen. */
    private fun showcase(): Int {
        var best = 0
        var score = Float.POSITIVE_INFINITY
        for (i in tiles.indices) {
            val g = geo(i)
            val inside = g.x >= 12 && g.x + g.w <= width - 12 && g.y >= top + 40 && g.y + g.h <= height - bottom - 90
            if (!inside || g.alpha < 0.95f) continue
            val off = abs(g.x + g.w / 2 - width * 0.5f) + abs(g.y + g.h / 2 - height * 0.46f) - g.w * 0.8f
            if (off < score) {
                score = off
                best = i
            }
        }
        return best
    }

    // MARK: Hero

    /** The hero in front, as an index into `ids`. */
    val front: Int get() = max(0, min(ids.size - 1, collageRound(page.value)))

    private fun openTile(index: Int, user: Boolean) {
        if (heroOn) return
        val list = heroList(index)
        val first = max(0, list.indexOf(index))
        ids = list
        page.set(first.toFloat())
        dragX.set(0f)
        dragY.set(0f)
        closing = false
        heroOn = true
        open.set(0f)
        if (reduced) open.timeTo(1f, FADE_TIME) else open.springTo(1f, OPEN_MS, OPEN_RATIO)
        hero = list
        sharpen(listOf(list[first]) + list.filterIndexed { k, _ -> k != first })
        if (user) haptic(CollageHaptic.Open)
    }

    private fun closeHero(user: Boolean, velocity: Float) {
        if (!heroOn || closing) return
        closing = true
        if (reduced) open.timeTo(0f, FADE_TIME) else open.springTo(0f, CLOSE_MS, CLOSE_RATIO, -velocity)
        if (user) haptic(CollageHaptic.Open)
    }

    private fun finishClose() {
        heroOn = false
        closing = false
        dragX.set(0f)
        dragY.set(0f)
        hero = null
    }

    private fun goTo(target: Int, user: Boolean, rawVelocity: Float) {
        if (ids.isEmpty()) return
        val velocity = if (rawVelocity.isFinite()) rawVelocity else 0f
        val to = min(ids.size - 1, max(0, target))
        val before = collageRound(page.value)
        if (reduced) page.timeTo(to.toFloat(), FADE_TIME) else page.springTo(to.toFloat(), PAGE_MS, PAGE_RATIO, velocity)
        if (user && to != before) haptic(CollageHaptic.Tick)
    }

    fun touched() {
        hold = if (heroOn) HERO_HOLD else USER_HOLD
    }

    // MARK: Touch

    fun tap(x: Float, y: Float) {
        if (heroOn) return
        touched()
        // The nearest tile under the finger.
        var hit = -1
        var nearest = -1f
        for (i in tiles.indices) {
            val g = geo(i)
            if (g.alpha < 0.4f || shown(i, catTo) < 1f) continue
            if (x >= g.x && x <= g.x + g.w && y >= g.y && y <= g.y + g.h && g.u > nearest) {
                nearest = g.u
                hit = i
            }
        }
        if (hit >= 0) {
            openTile(hit, true)
            // Held longer now that a hero is open, so it can be read before the loop takes it back.
            touched()
        }
    }

    /** False when the hero cannot be dragged now, so the gesture is left alone. */
    fun panStart(): Boolean {
        if (!heroOn || closing) return false
        touched()
        axis = 0
        page.set(page.value)
        panFrom = page.value
        return true
    }

    fun panUpdate(tx: Float, ty: Float) {
        if (!heroOn || closing) return
        if (axis == 0) {
            axis = if (abs(tx) > abs(ty)) 1 else 2
            dragX.set(dragX.value)
            dragY.set(dragY.value)
        }
        if (axis == 1) {
            // Past the first or the last hero the pager gives way less and less.
            val n = ids.size
            var p = panFrom - if (width > 0f) tx / width else 0f
            if (p < 0f) p *= RUBBER
            if (p > n - 1) p = n - 1 + (p - (n - 1)) * RUBBER
            page.set(p)
        } else {
            dragX.set(tx * 0.7f)
            dragY.set(if (ty > 0f) ty else ty * 0.18f)
        }
    }

    fun panEnd(ty: Float, rawVx: Float, rawVy: Float) {
        // VelocityTracker can report NaN, and roundToInt throws on it.
        val vx = if (rawVx.isFinite()) rawVx else 0f
        val vy = if (rawVy.isFinite()) rawVy else 0f
        if (heroOn && !closing) {
            touched()
            if (axis == 1) {
                val v = if (width > 0f) -vx / width else 0f
                val from = collageRound(panFrom)
                val aim = collageRound(page.value + v * PAGE_THROW)
                goTo(min(from + 1, max(from - 1, aim)), true, v)
            } else if (ty > DISMISS_DISTANCE || vy > DISMISS_SPEED) {
                closeHero(true, max(0f, vy) / (height * 0.5f))
            } else {
                dragX.springTo(0f, DRAG_BACK_MS, DRAG_BACK_RATIO, vx * 0.7f)
                dragY.springTo(0f, DRAG_BACK_MS, DRAG_BACK_RATIO, vy)
            }
        }
        axis = 0
    }

    fun close() {
        touched()
        closeHero(true, 0f)
    }

    fun pick(k: Int) {
        touched()
        goTo(k, true, 0f)
    }

    fun start() {
        touched()
        haptic(CollageHaptic.Start)
        val list = hero ?: return
        if (list.isEmpty()) return
        onStart?.invoke(sessions[list[min(list.size - 1, max(0, collageRound(page.value)))]])
    }

    // MARK: Chips

    fun chipPlaced(index: Int, x: Float, w: Float) {
        chipBoxes[index] = x to w
        if (index == chip) {
            pillX.set(x)
            pillW.set(w)
        }
    }

    fun chooseChip(index: Int) {
        touched()
        if (index == chip) return
        chip = index
        haptic(CollageHaptic.Tick)
        catFrom = catTo
        catTo = index
        catMix.set(0f)
        catMix.timeTo(1f, FILTER_TIME, EaseInOutStrong::transform)
        val box = chipBoxes[index] ?: return
        if (reduced) {
            pillX.set(box.first)
            pillW.set(box.second)
        } else {
            pillX.springTo(box.first, CHIP_MS, CHIP_RATIO)
            pillW.springTo(box.second, CHIP_MS, CHIP_RATIO)
        }
    }

    // MARK: Hero frame

    /**
     * The hero's frame, written into [out] as x, y, w, h, radius: between the tile's slot
     * (open = 0) and the screen (open = 1). A drag down turns the screen into a card that shrinks
     * and follows the finger.
     */
    fun heroRect(slot: Geo, out: FloatArray) {
        val o = open.value
        val dx = dragX.value
        val dy = dragY.value
        val pull = clamp01(dy / (height * DISMISS_SPAN))
        val s = 1f - DISMISS_SHRINK * pull
        val fw = width * s
        val fh = height * s
        val fx = (width - fw) / 2f + dx
        val fy = (height - fh) / 2f + dy * 0.62f
        val fr = DISMISS_RADIUS * clamp01(pull * 3f)
        out[0] = slot.x + (fx - slot.x) * o
        out[1] = slot.y + (fy - slot.y) * o
        out[2] = max(1f, slot.w + (fw - slot.w) * o)
        out[3] = max(1f, slot.h + (fh - slot.h) * o)
        out[4] = max(0f, slot.r + (fr - slot.r) * clamp01(o))
    }
}
