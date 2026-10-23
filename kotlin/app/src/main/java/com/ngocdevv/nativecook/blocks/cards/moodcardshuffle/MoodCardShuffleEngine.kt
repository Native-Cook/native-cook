package com.ngocdevv.nativecook.blocks.cards.moodcardshuffle

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal const val COUNT = 4

// Card photos are drawn about 270 dp wide.
internal const val CARD_PHOTO_PX = 800
// The backdrop is the photo blurred once into a small BACKDROP_W × BACKDROP_H px image (sigma
// BACKDROP_BLUR px there), drawn stretched over the screen with cubic sampling.
internal const val BACKDROP_W = 120
internal const val BACKDROP_H = 260
internal const val BACKDROP_BLUR = 9.0
internal const val BACKDROP_SATURATION = 0.72f
// Seconds the backdrop takes to cross-fade when a reshuffle changes the front photo.
internal const val BACKDROP_FADE = 0.52
// Seconds a photo takes to fade in once it has loaded.
internal const val IMAGE_FADE = 0.35

// The front card is CARD_SHARE of the screen's width (at most CARD_MAX dp), CARD_ASPECT as tall as
// it is wide, with CARD_RADIUS dp corners. Each card has its own vanishing point PERSPECTIVE dp away.
internal const val CARD_SHARE = 0.68
internal const val CARD_MAX = 300.0
internal const val CARD_ASPECT = 0.84
internal const val CARD_RADIUS = 26f
internal const val PERSPECTIVE = 900.0

// The deck is a drum seen from the front. A card's place is its signed distance s from the front
// (in cards, −2 … 2): s 0 in front, 1 peeking out on the right, −1 on the left, ±2 at the back.
private const val PEEK_X = 0.25
private const val DEPTH_SCALE = 0.3
private const val DEPTH_DROP = 18.0
private const val PEEK_TURN = 16.0
private const val PEEK_TILT = 1.6
private const val DEPTH_DIM = 0.62
// The card leaving the front swings out the way the deck turns, then tucks in as the peek.
private const val SWING_X = 0.36
private const val SWING_TURN = 26.0
private const val SWING_LIFT = 14.0
private const val SWING_DIP = 0.08
private const val HEADING_EASE = 0.12
// When two cards trade places in the drawing order, the one going under is drawn once more on top,
// fading out over GHOST_BAND of their depth difference.
private const val GHOST_BAND = 0.3
// The chip, bookmark and text show only on the front card: gone by |s| = 1 / TEXT_FADE.
private const val TEXT_FADE = 3.0
// The region line's letters sit on their glyph advances plus this much.
internal const val REGION_TRACKING = 0.7f
internal const val SHADOW_BLUR = 16f
internal const val SHADOW_DROP = 16f
internal const val SHADOW_ALPHA = 0.34f

// Reshuffling to a new mood: the cards gather into a riffled pile, turn edge-on together (their
// faces change while nobody can see them), turn back and deal out.
private const val SHUFFLE_TIME = 1.15
private const val REDUCED_SHUFFLE_TIME = 0.6
private val GatherX = doubleArrayOf(0.0, 0.12, -0.12, 0.06)
private val GatherY = doubleArrayOf(0.0, -10.0, -20.0, -30.0)
private val GatherScale = doubleArrayOf(0.9, 0.87, 0.84, 0.81)
private val GatherTilt = doubleArrayOf(0.0, 7.0, -7.0, 3.5)
// Before a reshuffle the deck settles on the nearest card over this long.
private const val SETTLE_TIME = 0.2

// Finger travel that turns the deck by one card, as a share of the screen's width.
private const val DRAG_SPAN = 0.72
// A release commits when the position projected THROW s ahead passes half a card.
private const val THROW = 0.18
// Past one card in either direction the deck gives only RUBBER of the finger's travel.
private const val RUBBER = 0.2
// After a drag or a tap the deck lands on this spring, carrying the finger's speed.
private const val DECK_MASS = 1.0
private const val DECK_STIFFNESS = 90.0
private const val DECK_DAMPING = 17.0
// The pan starts after 12 dp of sideways travel and fails after 16 dp of vertical travel; a tap is
// a touch shorter than 320 ms.
internal const val PAN_SLOP = 12f
internal const val PAN_FAIL = 16f
internal const val TAP_MS = 320L
// autoPlay turns the deck over ADVANCE_TIME s on an ease-in-out curve.
private const val ADVANCE_TIME = 1.15
private const val REDUCED_ADVANCE_TIME = 0.32
private val AdvanceEase = CubicBezierEasing(0.5f, 0f, 0.25f, 1f)
// The bookmark pops to BOOKMARK_POP × over 0.11 s, then settles on a damped wobble.
private const val BOOKMARK_POP = 1.28
internal const val PILL_DURATION = 0.42
internal const val PILL_DAMPING_RATIO = 0.82f
internal const val ENERGY_THRESHOLD = 6e-9

// autoPlay (seconds): two cards, a reshuffle to Nature, a card, a reshuffle to Wild, two cards,
// back to Quiet and a rest. The last reshuffle deals the first place onto the front card, so one
// period ends looking exactly as it started.
private const val LOOP = 14.0

private class Cue(val at: Double, val next: Boolean = false, val mood: Int? = null)

private val Cues = listOf(
    Cue(1.0, next = true),
    Cue(2.4, next = true),
    Cue(3.8, mood = 1),
    Cue(5.6, next = true),
    Cue(7.0, mood = 3),
    Cue(8.8, next = true),
    Cue(10.2, next = true),
    Cue(11.6, mood = 0),
)
// A touch pauses autoPlay for this long; then the loop starts over.
private const val USER_HOLD = 4.0

/** JavaScript's Math.round: halves go up. */
internal fun jsRound(x: Double) = floor(x + 0.5)

internal fun clamp01(x: Double) = if (x.isNaN()) 0.0 else x.coerceIn(0.0, 1.0)

internal fun smooth(x: Double): Double {
    val t = clamp01(x)
    return t * t * (3 - 2 * t)
}

private fun inOutQuad(t: Double) = if (t < 0.5) 2 * t * t else 1 - 2 * (1 - t) * (1 - t)

/** A whole number of cards wrapped into 0 until COUNT. */
private fun wrapSlot(k: Double): Int {
    if (!k.isFinite()) return 0
    val r = k % COUNT
    return ((if (r < 0) r + COUNT else r).toInt()) % COUNT
}

/** The front card's size (width, height) in dp for a screen `width` dp wide. */
internal fun cardSizeFor(width: Double): Pair<Double, Double> {
    if (!width.isFinite() || width <= 0) return 0.0 to 0.0
    val w = min(CARD_MAX, jsRound(width * CARD_SHARE))
    return w to jsRound(w * CARD_ASPECT)
}

/** Linear interpolation through `values` at whole steps 0, 1, 2 … of `x`, wrapping at the end. */
private fun around(values: DoubleArray, x: Double): Double {
    val n = values.size
    if (n == 0 || !x.isFinite()) return 0.0
    val at = ((x % n) + n) % n
    val i = floor(at).toInt().coerceIn(0, n - 1)
    val f = at - i
    return values[i] + (values[(i + 1) % n] - values[i]) * f
}

/** A card's signed distance from the front, in (−2, 2]. */
private fun signedOf(index: Int, progress: Double): Double {
    var s = (index - progress) % COUNT
    if (s <= -2) s += COUNT
    if (s > 2) s -= COUNT
    return s
}

/** 0 at rest, 1 for the riffled pile in the middle of a reshuffle. */
private fun gatherOf(shuffle: Double) = smooth(shuffle / 0.36) * (1 - smooth((shuffle - 0.64) / 0.36))

/** Extra turn of the whole pile during a reshuffle: edge-on from 44% to 56%. */
private fun shuffleTurnOf(shuffle: Double) = 90 * smooth((shuffle - 0.2) / 0.24) * (1 - smooth((shuffle - 0.56) / 0.24))

internal class Pose(
    val x: Double,
    val y: Double,
    val scale: Double,
    val turn: Double,
    val tilt: Double,
    val dim: Double,
    val text: Double,
    val alpha: Double,
    val key: Double,
)

/** Where card `index` is drawn, from the deck's progress and the reshuffle. */
private fun poseOf(index: Int, progress: Double, heading: Double, shuffle: Double, reduced: Boolean, cardW: Double): Pose {
    val s = signedOf(index, progress)
    val a = abs(s)
    val side = if (s < 0) -1.0 else 1.0
    val angle = s * PI / 2
    val depth = (1 - cos(angle)) / 2
    // Only the card going out on the side the deck turns towards swings (heading +1: to the left).
    val leaving = clamp01((1 - side * heading) / 2)
    val swing = if (!reduced && a < 1) sin(PI * a) * leaving else 0.0
    var x = sin(angle) * PEEK_X * cardW + side * SWING_X * cardW * swing
    var y = DEPTH_DROP * depth - SWING_LIFT * swing
    var scale = (1 - DEPTH_SCALE * depth) * (1 - SWING_DIP * swing)
    var turn = sin(angle) * PEEK_TURN + side * SWING_TURN * swing
    var tilt = sin(angle) * PEEK_TILT
    var alpha = 1.0
    // Upper cards draw later: the closer to the front, the higher.
    var key = -a
    val g = if (reduced) 0.0 else gatherOf(shuffle)
    if (g > 0) {
        // The pile, by each card's rank from the front: 0 front, 1 right, 2 back, 3 left.
        val d = if (s < 0) s + COUNT else s
        x += (around(GatherX, d) * cardW - x) * g
        y += (around(GatherY, d) - y) * g
        scale += (around(GatherScale, d) - scale) * g
        tilt += (around(GatherTilt, d) - tilt) * g
        turn -= turn * g
        key += (-d - key) * g
    }
    turn += if (reduced) 0.0 else shuffleTurnOf(shuffle)
    // Reduced motion: the deck dips out and back in while a reshuffle changes its faces.
    if (reduced && shuffle > 0) alpha = 1 - sin(PI * clamp01(shuffle))
    return Pose(
        x = x,
        y = y,
        scale = scale,
        turn = turn,
        tilt = tilt,
        dim = DEPTH_DIM * depth + 0.1 * swing,
        text = clamp01(1 - a * TEXT_FADE) * (1 - g),
        alpha = alpha,
        key = key,
    )
}

/**
 * The 3 × 3 matrix (Skia / android.graphics.Matrix order) that draws a card, in its own coordinates
 * centred on its middle, at a pose: scaled, tilted in the picture plane, turned about its vertical
 * axis and seen in perspective.
 */
internal fun cardMatrix(p: Pose, cx: Double, cy: Double, out: FloatArray) {
    val t = p.turn * PI / 180
    val r = p.tilt * PI / 180
    val ct = cos(t)
    val st = sin(t)
    val a = p.scale * cos(r)
    val b = p.scale * sin(r)
    val tx = cx + p.x
    val ty = cy + p.y
    // Local (u, v) → (a·u − b·v, b·u + a·v); then x = X·cos t, depth = X·sin t (away from the eye).
    val q0 = ct + tx * st / PERSPECTIVE
    val q1 = ty * st / PERSPECTIVE
    val q2 = st / PERSPECTIVE
    out[0] = (q0 * a).toFloat()
    out[1] = (-q0 * b).toFloat()
    out[2] = tx.toFloat()
    out[3] = (q1 * a + b).toFloat()
    out[4] = (-q1 * b + a).toFloat()
    out[5] = ty.toFloat()
    out[6] = (q2 * a).toFloat()
    out[7] = (-q2 * b).toFloat()
    out[8] = 1f
}

/** The bookmark's size `age` s after a tap: up to 1.28× over 0.11 s, then a damped wobble. */
private fun popAt(age: Double): Double {
    val extra = BOOKMARK_POP - 1
    if (age < 0.11) return 1 + extra * smooth(age / 0.11)
    return 1 + extra * exp(-(age - 0.11) * 7) * cos((age - 0.11) * 18)
}

/** The stiffness Reanimated solves for a spring of mass 1 from rest, given a duration and a damping ratio. */
internal fun stiffnessFor(durationS: Double, zeta: Double): Float {
    val x0 = 1.0
    val settling = durationS * 1.5
    fun diff(k: Double): Double {
        val omega = sqrt(k) * zeta
        val a = x0 + x0 * omega * settling
        val e = exp(-omega * settling)
        val xt = a * e
        val vt = a * e * -omega + x0 * omega * e
        return (0.5 * k * xt * xt + 0.5 * vt * vt) / (0.5 * k * x0 * x0) - ENERGY_THRESHOLD
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
    return if (current.isFinite() && current > 0) current.toFloat() else 170f
}

/** Reanimated's spring, moved on by `t` s along its exact solution: (displacement, speed). */
private fun springStep(x0: Double, v0: Double, k: Double, c: Double, m: Double, t: Double): Pair<Double, Double> {
    val omega0 = sqrt(k / m)
    val zeta = c / (2 * sqrt(k * m))
    if (zeta < 1) {
        val omega1 = omega0 * sqrt(1 - zeta * zeta)
        val sin1 = sin(omega1 * t)
        val cos1 = cos(omega1 * t)
        val envelope = exp(-zeta * omega0 * t)
        val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
        val v = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        return frag to v
    }
    val envelope = exp(-omega0 * t)
    val x = envelope * (x0 + (v0 + omega0 * x0) * t)
    val v = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
    return x to v
}

/** A place as the canvas draws it: the blurb broken into at most two lines, the region spaced out. */
internal class Face(
    val name: String,
    val region: String,
    val photo: String,
    val lines: List<String>,
    /** Each letter of the region and its x from the line's start. */
    val regionLetters: List<Pair<String, Float>>,
    val chipWidth: Float,
)

/** One card to draw. */
internal class Item(
    val face: Face,
    val pose: Pose,
    val alpha: Double,
    val shadow: Boolean,
    val saved: Boolean,
    val pop: Double,
)

/** The deck's state, stepped once a frame like the RN frame callback, and the gestures that move it. */
internal class MoodCardShuffleEngine {
    private enum class Curve { InOutQuad, Advance }

    private sealed interface Motion

    private class Timing(
        val from: Double,
        val to: Double,
        val start: Double,
        val duration: Double,
        val curve: Curve,
        val wrapOnDone: Boolean,
    ) : Motion

    private class Spring(
        val target: Double,
        val velocity: Double,
        val startX0: Double,
        val initialEnergy: Double,
        val wrapOnDone: Boolean,
    ) : Motion

    // Deck position in cards (grows by one per card turned to the left).
    var progress = 0.0
        private set
    private var motion: Motion? = null
    // +1 while the deck turns on (front card out to the left), −1 while it turns back; eased.
    private var heading = 1.0
    private var lastProgress = 0.0
    private var grab = 0.0
    private var dragging = false
    // 0 … 1 through a reshuffle; `pending` is the mood it deals. The deal: the mood on the cards and
    // the slot that shows its first place, so a reshuffle keeps the front card in front.
    private var shuffle = 0.0
    private var shuffleStart = 0.0
    private var shuffleDuration = SHUFFLE_TIME
    private var shuffling = false
    private var pending = 0
    private var current = 0
    private var dealMood = 0
    private var dealOffset = 0
    private var lastShuffle = 0.0
    // The backdrop that fades out after a reshuffle, and when it began to.
    private var fadePhoto = ""
    private var fadeAt = -10.0
    // Saved places by name, and the last bookmark pop.
    private val saved = HashSet<String>()
    private var popName = ""
    private var popAtTime = -10.0
    var elapsed = 0.0
        private set
    private var clock = 0.0
    private var hold = 0.0
    private var lastAuto: Boolean? = null

    // What the composable tells the engine.
    var faces: List<List<Face>> = emptyList()
    var facesKey = ""
    var reduced = false
    var autoPlay = false
    var stageWidth = 0.0
    var stageHeight = 0.0
    var deckTop = 0.0
    var deckHeight = 0.0

    /** Called when a mood is chosen (index, by the user). */
    var onAnnounce: (Int, Boolean) -> Unit = { _, _ -> }
    var onLight: () -> Unit = {}
    var onSelect: () -> Unit = {}

    // The frame's output.
    val items = arrayOfNulls<Item>(7)
    /** Backdrop photos, bottom first, and their alpha. */
    val backdrops = ArrayList<Pair<String, Double>>(3)

    private val cardW get() = cardSizeFor(stageWidth).first
    private val cardH get() = cardSizeFor(stageWidth).second
    private val span get() = max(1.0, stageWidth * DRAG_SPAN)
    val cx get() = stageWidth / 2
    val cy get() = deckTop + deckHeight / 2

    // MARK: frame

    /** Steps the deck by `dt` s and lays out what to draw. `loaded` is when each photo arrived. */
    fun step(rawDt: Double, loaded: Map<String, Double>) {
        if (lastAuto != autoPlay) {
            lastAuto = autoPlay
            clock = 0.0
            hold = 0.0
        }
        val dt = min(max(rawDt, 0.0), 0.064)
        if (dt > 0) {
            elapsed += dt
            stepMotion(dt)
            stepShuffle()
            frameCallback(dt)
        }
        layout(loaded)
    }

    private fun stepMotion(dt: Double) {
        when (val m = motion) {
            null -> return
            is Timing -> {
                val runtime = elapsed - m.start
                if (runtime >= m.duration) {
                    progress = m.to
                    motion = null
                    if (m.wrapOnDone) wrapProgress()
                } else {
                    val f = clamp01(runtime / m.duration)
                    val e = if (m.curve == Curve.Advance) AdvanceEase.transform(f.toFloat()).toDouble() else inOutQuad(f)
                    progress = m.from + (m.to - m.from) * e
                }
            }
            is Spring -> {
                val (x, v) = springStep(progress - m.target, m.velocity, DECK_STIFFNESS, DECK_DAMPING, DECK_MASS, dt)
                progress = m.target + x
                val energy = 0.5 * DECK_STIFFNESS * x * x + 0.5 * DECK_MASS * v * v
                if (m.initialEnergy == 0.0 || energy / m.initialEnergy <= ENERGY_THRESHOLD || !progress.isFinite()) {
                    progress = m.target
                    motion = null
                    if (m.wrapOnDone) wrapProgress()
                } else {
                    motion = Spring(m.target, v, m.startX0, m.initialEnergy, m.wrapOnDone)
                }
            }
        }
    }

    /** Whole turns of the deck change nothing on screen, so the numbers are kept small once it lands. */
    private fun wrapProgress() {
        progress = ((progress % COUNT) + COUNT) % COUNT
    }

    private fun stepShuffle() {
        if (!shuffling) return
        if (elapsed - shuffleStart >= shuffleDuration) {
            shuffle = 0.0
            shuffling = false
        } else {
            shuffle = clamp01((elapsed - shuffleStart) / shuffleDuration)
        }
    }

    /** The RN frame callback. */
    private fun frameCallback(dt: Double) {
        val speed = (progress - lastProgress) / dt
        lastProgress = progress
        // A wrap of the numbers (a whole turn) is not a motion.
        if (abs(speed) > 0.05 && abs(speed) < 40) {
            heading += ((if (speed > 0) 1.0 else -1.0) - heading) * (1 - exp(-dt / HEADING_EASE))
        }

        // Half way through a reshuffle, edge-on, the cards take the new mood's faces.
        val sh = shuffle
        if (lastShuffle < 0.5 && sh >= 0.5) {
            val front = wrapSlot(jsRound(progress))
            face(dealMood, dealOffset, front)?.let { fadePhoto = it.photo }
            fadeAt = elapsed
            dealMood = pending
            dealOffset = front
        }
        lastShuffle = sh

        if (dragging) return
        if (hold > 0) {
            hold -= dt
            if (hold <= 0) clock = 0.0
            return
        }
        if (!autoPlay) return
        val before = clock
        val t = (before + dt) % LOOP
        clock = t
        for (cue in Cues) {
            val at = cue.at
            val passed = (before < at && t >= at) || (t < before && (at > before || at <= t))
            if (!passed) continue
            if (cue.next) advance(1.0, byUser = false)
            cue.mood?.let { choose(it, user = false) }
        }
    }

    // MARK: animations

    private fun startTiming(target: Double, duration: Double, curve: Curve, wrapOnDone: Boolean) {
        // Like Reanimated: a timing started over a running timing to the same value keeps its timeline.
        val m = motion
        motion = if (m is Timing && m.to == target) {
            Timing(m.from, target, m.start, duration, curve, wrapOnDone)
        } else {
            Timing(progress, target, elapsed, duration, curve, wrapOnDone)
        }
    }

    private fun startSpring(target: Double, configVelocity: Double, wrapOnDone: Boolean) {
        var startX0 = progress - target
        var velocity = when (val m = motion) {
            is Spring -> if (m.target == target) {
                // Triggered twice: the running spring carries on.
                startX0 = m.startX0
                m.velocity
            } else {
                m.velocity + configVelocity
            }
            // A timing animation has no speed to hand on (Reanimated reads NaN, then 0).
            is Timing -> 0.0
            null -> configVelocity
        }
        if (!velocity.isFinite()) velocity = 0.0
        if ((target > progress && velocity < 0) || (target < progress && velocity > 0)) velocity = 0.0
        val initialEnergy = 0.5 * DECK_STIFFNESS * startX0 * startX0 + 0.5 * DECK_MASS * configVelocity * configVelocity
        motion = Spring(target, velocity, startX0, initialEnergy, wrapOnDone)
    }

    /** Turns the deck one card on: `step` 1 brings the right card forward, −1 the left one. */
    fun advance(step: Double, byUser: Boolean) {
        if (shuffling) return
        val target = jsRound(progress) + step
        if (byUser) {
            startSpring(target, 0.0, wrapOnDone = true)
        } else {
            startTiming(target, if (reduced) REDUCED_ADVANCE_TIME else ADVANCE_TIME, Curve.Advance, wrapOnDone = true)
        }
    }

    /** Picks a mood: the chip pill moves at once and the deck riffles and deals it. */
    fun choose(next: Int, user: Boolean) {
        if (shuffling || next == current || next < 0 || next >= faces.size) return
        current = next
        shuffling = true
        pending = next
        if (user) onSelect()
        onAnnounce(next, user)
        // The deck settles on the nearest card first, so the pile gathers from rest. Whatever moves it
        // is stopped first, so the settle always starts from where the deck is.
        motion = null
        startTiming(jsRound(progress), SETTLE_TIME, Curve.InOutQuad, wrapOnDone = false)
        shuffle = 0.0
        shuffleStart = elapsed
        shuffleDuration = if (reduced) REDUCED_SHUFFLE_TIME else SHUFFLE_TIME
    }

    /** The chips and "Go Unexpected" were touched: autoPlay waits. */
    fun touched() {
        hold = USER_HOLD
    }

    /** Picks a random mood other than the chosen one. */
    fun surprise(chosen: Int) {
        val count = faces.size
        if (count < 2) return
        var next = (0 until count - 1).random()
        if (next >= chosen) next += 1
        choose(next, user = true)
    }

    // MARK: gestures on the deck

    fun panStart() {
        if (shuffling) return
        touched()
        motion = null
        grab = progress
        dragging = true
    }

    fun panUpdate(translationX: Double) {
        if (!dragging || !translationX.isFinite()) return
        // Dragging left turns the deck on, dragging right turns it back.
        val g = grab
        val base = jsRound(g)
        val raw = g - translationX / span
        val hi = base + 1
        val lo = base - 1
        progress = if (raw > hi) hi + (raw - hi) * RUBBER else if (raw < lo) lo + (raw - lo) * RUBBER else raw
    }

    fun panEnd(velocityX: Double) {
        if (!dragging) return
        dragging = false
        touched()
        val base = jsRound(grab)
        val velocity = if (velocityX.isFinite()) -velocityX / span else 0.0
        val projected = progress + velocity * THROW
        val target = jsRound(projected).coerceIn(base - 1, base + 1)
        if (target != base) onLight()
        startSpring(target, velocity, wrapOnDone = true)
    }

    /** Taps on the deck (stage coordinates, dp): the bookmark on the front card, or a peeking card. */
    fun tap(x: Double, y: Double) {
        if (shuffling) return
        touched()
        val w = cardW
        val h = cardH
        if (w <= 0) return
        val right = cx + w / 2
        val topEdge = cy - h / 2
        val settled = abs(progress - jsRound(progress)) < 0.05
        if (settled && x > right - 56 && x < right && y > topEdge && y < topEdge + 56) {
            val front = wrapSlot(jsRound(progress))
            face(dealMood, dealOffset, front)?.let {
                if (!saved.remove(it.name)) saved.add(it.name)
                popName = it.name
                popAtTime = elapsed
            }
            onLight()
            return
        }
        if (x > right) {
            onLight()
            advance(1.0, byUser = true)
        } else if (x < cx - w / 2) {
            onLight()
            advance(-1.0, byUser = true)
        }
    }

    // MARK: layout of a frame

    /** The place card slot `slot` shows for a deal (mood, offset). */
    private fun face(mood: Int, offset: Int, slot: Int): Face? {
        if (faces.isEmpty()) return null
        val list = faces[mood.coerceIn(0, faces.size - 1)]
        if (list.isEmpty()) return null
        val i = ((slot - offset) % COUNT + COUNT) % COUNT
        return list[i % list.size]
    }

    private val poses = Array(COUNT) { Pose(0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0) }
    private val order = IntArray(COUNT)

    private fun layout(loaded: Map<String, Double>) {
        items.fill(null)
        backdrops.clear()
        val w = cardW
        if (w <= 0 || stageHeight <= 0 || faces.isEmpty()) return
        val now = elapsed
        val p = progress
        val sh = shuffle

        // The backdrop: the front card's photo, blurred, cross-fading with the card arriving.
        var near = 0
        var second = 1
        var nearA = 9.0
        var secondA = 9.0
        for (i in 0 until COUNT) {
            val a = abs(signedOf(i, p))
            if (a < nearA) {
                second = near
                secondA = nearA
                near = i
                nearA = a
            } else if (a < secondA) {
                second = i
                secondA = a
            }
        }
        face(dealMood, dealOffset, near)?.let { f ->
            loaded[f.photo]?.let { at ->
                val alpha = clamp01((now - at) / IMAGE_FADE)
                if (alpha > 0.002) backdrops.add(f.photo to alpha)
            }
        }
        val mix = if (nearA + secondA > 0) nearA / (nearA + secondA) else 0.0
        if (mix > 0.002) face(dealMood, dealOffset, second)?.let { f -> if (loaded.containsKey(f.photo)) backdrops.add(f.photo to clamp01(mix)) }
        val fade = 1 - (now - fadeAt) / BACKDROP_FADE
        if (fade > 0 && loaded.containsKey(fadePhoto)) {
            val alpha = smooth(fade)
            if (alpha > 0.002) backdrops.add(fadePhoto to alpha)
        }

        // The deck, back to front. Where two neighbours in the order are about to trade places, the
        // lower one is drawn again on top, fading, so the trade is a cross-fade.
        for (i in 0 until COUNT) {
            poses[i] = poseOf(i, p, heading, sh, reduced, w)
            order[i] = i
        }
        for (i in 1 until COUNT) {
            val v = order[i]
            var j = i - 1
            while (j >= 0 && poses[order[j]].key > poses[v].key) {
                order[j + 1] = order[j]
                j -= 1
            }
            order[j + 1] = v
        }
        val popAge = now - popAtTime
        var slot = 0
        for (k in 0 until COUNT) {
            for (pass in 0 until 2) {
                if (pass == 1 && k == 0) continue
                val thisSlot = slot
                slot += 1
                val index = if (pass == 0) order[k] else order[k - 1]
                val pose = poses[index]
                var ghost = 1.0
                if (pass == 1) {
                    val gap = poses[order[k]].key - pose.key
                    if (gap >= GHOST_BAND) continue
                    ghost = 0.5 * (1 - gap / GHOST_BAND)
                }
                val alpha = pose.alpha * ghost
                if (alpha <= 0.004) continue
                val f = face(dealMood, dealOffset, index) ?: continue
                val pop = if (f.name == popName && popAge < 0.8 && !reduced) popAt(popAge) else 1.0
                if (thisSlot < items.size) items[thisSlot] = Item(f, pose, alpha, pass == 0, saved.contains(f.name), pop)
            }
        }
    }
}
