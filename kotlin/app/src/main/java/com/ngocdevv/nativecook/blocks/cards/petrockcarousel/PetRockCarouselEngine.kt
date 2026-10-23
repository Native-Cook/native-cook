package com.ngocdevv.nativecook.blocks.cards.petrockcarousel

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal class PetRockSpec(
    val name: String,
    /** Unsplash photo id: the stone's surface, laid over its base colour. */
    val photo: String,
    /** Base colour of the stone. */
    val tone: Int,
    /** Shape seed, vertical squash and resting tilt (rad). */
    val seed: Int,
    val aspect: Float,
    val tilt: Float,
    /** The face it wears when the carousel opens. */
    val face: Int,
)

internal val PetRocks = listOf(
    PetRockSpec("Gus", "photo-1604951832436-90867d74f4de", 0xFF8B8680.toInt(), 7, 0.9f, -0.1f, 0),
    PetRockSpec("Marlo", "photo-1650755743867-6ad2037a2934", 0xFF998A78.toInt(), 21, 0.95f, 0.06f, 1),
    PetRockSpec("Dot", "photo-1597188702867-0a052cddba09", 0xFF948A80.toInt(), 33, 0.92f, 0.04f, 2),
    PetRockSpec("Sven", "photo-1665706782455-f8e800c8aeaf", 0xFF86898B.toInt(), 48, 0.82f, -0.05f, 3),
    PetRockSpec("Ruby", "photo-1640832956551-eaeab27afbdc", 0xFF9E8E7E.toInt(), 59, 0.96f, 0.08f, 4),
    PetRockSpec("Otis", "photo-1725959523676-556f1fadaf2f", 0xFF8E8A84.toInt(), 71, 0.88f, -0.04f, 5),
)

// Sizes (dp). The focused rock is BIG_MAX across at most, or BIG_SHARE of the screen's width; the
// rocks next to it are SMALL of that, and each one further away a little smaller again.
internal const val BIG_MAX = 250f
internal const val BIG_SHARE = 0.62f
internal const val SMALL = 0.46f
private const val GAP = 22f
private const val GAP_FAR = 16f
// The carousel's centre line, as a share of the screen's height.
private const val CENTRE_AT = 0.5f
// The intro: the little rock that stands for the gap between the two words, and the title.
internal const val TITLE_ROCK = 50f
internal const val TITLE_SIZE = 38f
internal const val TITLE_AT = 0.46f
// Each letter of the title leaves this far (dp), the one nearest the rock first.
internal const val SPLIT = 110f
internal const val SPLIT_LAG = 0.07f
// A rock's photo is cropped to this much of its middle, so the stone's surface fills it.
internal const val PHOTO_ZOOM = 1.7f
internal const val PHOTO_WIDTH = 520
// How strongly the grain texture is laid over the stone and its paint, in overlay.
internal const val GRAIN_ALPHA = 1f

// Dragging: finger travel per rock is the distance between the focused rock and the next.
internal const val DRAG_SLOP = 8f
internal const val TAP_MS = 400L
// Past the ends the carousel gives at most this much of a rock, in a rubber band.
private const val RUBBER = 0.55f
// A release throws the carousel this many seconds ahead before it picks a rock.
private const val THROW_TIME = 0.16f
// The snap onto a rock: a spring with a little give (mass 1).
private const val SNAP_STIFFNESS = 170f
private const val SNAP_DAMPING = 21f
// A rock has arrived when it is this close to the front (in rocks) and slower than this (rocks/s).
private const val ARRIVED_AT = 0.02f
private const val ARRIVED_SPEED = 0.25f
// Intro ⇄ carousel: a spring that lets the rock grow a touch past its size and settle.
private const val INTRO_STIFFNESS = 120f
private const val INTRO_DAMPING = 17f
private const val FADE_TIME = 0.28f

// The face morph: after a rock comes to rest it waits MORPH_DELAY, then the old paint blurs out
// while the new one sharpens in over MORPH_TIME, and the rock swells by MORPH_POP.
private const val MORPH_DELAY = 0.22f
private const val MORPH_TIME = 0.72f
internal const val MORPH_BLUR = 9f
internal const val MORPH_POP = 0.035f
private val MorphEase = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

// A tap squashes the rock (wider, shorter, standing on its base), then it springs back past round
// and wobbles about its base.
internal const val SQUASH_X = 0.12f
internal const val SQUASH_Y = 0.15f
internal const val WOBBLE = 0.14f

// Googly pupils: a loose ball in each eye, hanging a little low, thrown about by the rock's motion.
private const val PUPIL_REST = 0.35f
private const val PUPIL_STIFFNESS = 70f
private const val PUPIL_DAMPING = 4.5f
private const val PUPIL_BOUNCE = 1.6f
// How hard the rock's acceleration (dp/s²), its wobble and its squash shake the pupils.
private const val PUPIL_FROM_SCROLL = 0.0024f
private const val PUPIL_FROM_WOBBLE = 0.035f
private const val PUPIL_FROM_SQUASH = 0.02f

// autoPlay loop (seconds): Enter, three rocks down with a face morph on each, a tap on the third,
// back up to the first, and back to the title.
private const val LOOP = 14f
private const val PRESS_AT = 0.95f
private const val ENTER_AT = 1.1f
private val Steps = listOf(3.0f to 1, 5.0f to 2, 8.0f to 3, 10.0f to 0)
private const val WIGGLE_AT = 6.9f
private const val EXIT_AT = 12.0f
// A touch pauses the loop this long; then it goes back to the title and starts over.
private const val USER_HOLD = 6f

// The caption's line fades in and out as it changes (s).
internal const val CAPTION_IN = 0.24f
internal const val CAPTION_OUT = 0.14f

internal fun clamp(x: Float, lo: Float, hi: Float) = min(hi, max(lo, x))

/** JavaScript's Math.round: halves go up. */
internal fun jsRound(x: Float) = floor(x + 0.5f)

/** Reanimated's default timing curve, ease in and out (quadratic). */
internal fun inOutQuad(t: Float) = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
private fun outQuad(t: Float) = 1 - (1 - t) * (1 - t)

/** Resistance past an end: grows with the overshoot but never past RUBBER. */
private fun band(x: Float) = RUBBER * (1 - 1 / (1 + (x * 1.8f) / RUBBER))

private fun rubber(raw: Float, last: Float) = when {
    raw < 0 -> -band(-raw)
    raw > last -> last + band(raw - last)
    else -> raw
}

/** Where everything sits on this screen (dp). */
internal class PetRockLayout(val width: Float, val height: Float, val titleX: Float) {
    val big = min(BIG_MAX, width * BIG_SHARE)
    val centreY = height * CENTRE_AT
    /** Distance from the focused rock's centre to its neighbour's, and between further rocks. */
    val near = big / 2 + GAP + big * SMALL / 2
    val far = big * SMALL + GAP_FAR
    val titleY = height * TITLE_AT

    /** Where a rock sits for a signed distance [p] (in rocks) from the front: its y and its scale. */
    fun slotY(p: Float): Float {
        val a = abs(p)
        val offset = if (a <= 1) a * near else near + (a - 1) * far
        return centreY + kotlin.math.sign(p) * offset
    }

    fun slotScale(p: Float): Float {
        val c = min(abs(p), 3f)
        val k = min(c.toInt(), 2)
        val from = Scales[k]
        return from + (Scales[k + 1] - from) * (c - k)
    }

    private companion object {
        val Scales = floatArrayOf(1f, SMALL, SMALL * 0.93f, SMALL * 0.86f)
    }
}

/**
 * A number driven like a Reanimated shared value: a spring, a timing (with a delay), or a sequence
 * of them, stepped once a frame.
 */
internal class PetRockAnim(var value: Float) {
    sealed interface Step
    class Spring(val to: Float, val stiffness: Float, val damping: Float) : Step
    class Timing(val to: Float, val duration: Float, val curve: (Float) -> Float = ::inOutQuad, val delay: Float = 0f) : Step

    var velocity = 0f
        private set
    private val steps = ArrayDeque<Step>()
    private var from = 0f
    private var t = 0f

    fun jump(to: Float) {
        value = to
        velocity = 0f
        steps.clear()
    }

    /** Starts a new animation from where the value is; a spring keeps the speed it has unless given one. */
    fun run(vararg next: Step, velocity: Float? = null) {
        if (velocity != null) this.velocity = velocity
        steps.clear()
        steps.addAll(next)
        begin()
    }

    private fun begin() {
        from = value
        t = 0f
        // A timing has no speed of its own: a spring after it starts from rest.
        if (steps.firstOrNull() is Timing) velocity = 0f
    }

    /** Advances by [dt] s; true on the frame the whole sequence finishes. */
    fun step(dt: Float): Boolean {
        val step = steps.firstOrNull() ?: return false
        var done = false
        when (step) {
            is Spring -> {
                var left = dt
                while (left > 0f) {
                    val h = min(left, 1f / 240f)
                    velocity += (-step.stiffness * (value - step.to) - step.damping * velocity) * h
                    value += velocity * h
                    left -= h
                }
                if (abs(velocity) < 0.002f && abs(value - step.to) < 0.0005f) {
                    value = step.to
                    velocity = 0f
                    done = true
                }
            }
            is Timing -> {
                t += dt
                val p = clamp((t - step.delay) / step.duration, 0f, 1f)
                value = from + (step.to - from) * step.curve(p)
                if (p >= 1f) {
                    velocity = 0f
                    done = true
                }
            }
        }
        if (!done) return false
        steps.removeFirst()
        if (steps.isEmpty()) return true
        begin()
        return false
    }
}

/**
 * A rock's face lives in two slots: when it changes, the hidden slot takes the new one and the two
 * crossfade through a blur, so neither is ever seen changing.
 */
internal class PetRockMorph(face: Int) {
    var a = face
    var b = face
    var showB = false
    var morphing = false
    // 0: slot A shows, 1: slot B shows.
    val mix = PetRockAnim(0f)

    fun show(face: Int) {
        val shown = if (showB) b else a
        if (shown == face) return
        if (showB) {
            a = face
            showB = false
        } else {
            b = face
            showB = true
        }
        morphing = true
        mix.run(PetRockAnim.Timing(if (showB) 1f else 0f, MORPH_TIME, { MorphEase.transform(it) }, MORPH_DELAY))
    }

    fun step(dt: Float) {
        if (mix.step(dt)) morphing = false
    }
}

/** What the carousel asks of the screen around it: haptics and a callback. */
internal interface PetRockHost {
    fun selection()
    fun light()
    fun medium()
    fun soft()
    fun focused(name: String)
}

/** The carousel's state, stepped once a frame, and the gestures that drive it. */
internal class PetRockEngine(private val host: PetRockHost) {
    val rocks = PetRocks
    private val last = (rocks.size - 1).toFloat()
    var reducedMotion = false
    var layout = PetRockLayout(400f, 800f, 200f)

    val scroll = PetRockAnim(0f)
    val intro = PetRockAnim(0f)
    val squash = PetRockAnim(0f)
    val wobble = PetRockAnim(0f)
    val press = PetRockAnim(1f)
    val morphs = rocks.map { PetRockMorph(it.face) }
    var inCarousel = false
        private set
    val faces = rocks.map { it.face }.toIntArray()
    var focus = 0
        private set
    // Pupil position in its eye (−1…1 each way) and its velocity.
    var px = 0f
        private set
    var py = PUPIL_REST
        private set
    private var pvx = 0f
    private var pvy = 0f
    private var jolts = 0
    private var joltAt = -1f
    private var softAt = -1f
    // What the pupils last saw: the carousel's position and speed, the wobble and the squash.
    private var mS = 0f
    private var mV = 0f
    private var mW = 0f
    private var mWv = 0f
    private var mQ = 0f
    private var mQv = 0f
    private var entered = false
    private var dragging = false
    private var dragFrom = 0f
    // The rock the carousel is heading for (−1 once it has arrived), and whether a finger sent it.
    private var arriving = -1
    private var arrivingByUser = false
    private var byUser = false
    // The rock that last came to rest in front.
    private var settledAt = 0
    private var lastFront = 0
    private var clock = 0f
    private var looping = false
    private var hold = 0f
    var elapsed = 0f
        private set

    // The caption's line, and the one fading out under it.
    class Caption(val focus: Int, val face: Int, val at: Float)
    var caption = Caption(0, rocks[0].face, -1f)
        private set
    var oldCaption: Caption? = null
        private set

    fun setAutoPlay(on: Boolean) {
        looping = on
        clock = 0f
        hold = 0f
    }

    fun step(dt: Float) {
        if (dt <= 0f) return
        elapsed += dt
        scroll.step(dt)
        intro.step(dt)
        squash.step(dt)
        wobble.step(dt)
        press.step(dt)
        morphs.forEach { it.step(dt) }
        if (softAt >= 0f && elapsed >= softAt) {
            softAt = -1f
            host.soft()
        }

        // autoPlay: a fixed script on a clock, paused for a while by any touch.
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping) {
                // Back to the title, then the loop starts over from its rest.
                if (entered) {
                    exit()
                    clock = EXIT_AT
                } else {
                    clock = 0f
                }
            }
        } else if (looping) {
            val before = clock
            val t = (before + dt) % LOOP
            clock = t
            fun passed(at: Float) = (before < at && t >= at) || (t < before && (at > before || at <= t))
            if (passed(PRESS_AT)) press.run(PetRockAnim.Timing(0.96f, 0.11f), PetRockAnim.Timing(1f, 0.18f))
            if (passed(ENTER_AT)) enter(false)
            for ((at, to) in Steps) if (passed(at) && entered) goTo(to, 0f, false)
            if (passed(WIGGLE_AT) && entered) wiggle(false)
            if (passed(EXIT_AT)) exit()
        }

        // Googly pupils: a damped spring towards hanging low, pushed by how the rock accelerates.
        val s = scroll.value
        val v = (s - mS) / dt
        val smooth = mV + (v - mV) * 0.5f
        val accel = (smooth - mV) / dt
        val w = wobble.value
        val wv = (w - mW) / dt
        val wa = (wv - mWv) / dt
        val q = squash.value
        val qv = (q - mQ) / dt
        val qa = (qv - mQv) / dt
        mS = s
        mV = smooth
        mW = w
        mWv = wv
        mQ = q
        mQv = qv
        val goal = arriving
        if (goal >= 0 && !dragging && abs(s - goal) < ARRIVED_AT && abs(smooth) < ARRIVED_SPEED) {
            arriving = -1
            settled(goal, arrivingByUser)
        }

        // A selection tick as each rock passes the front under the user's finger, and the caption follows.
        val front = clamp(jsRound(s), 0f, last).toInt()
        if (front != lastFront) {
            lastFront = front
            if (entered) {
                setFocus(front)
                if (byUser) host.selection()
            }
        }

        if (reducedMotion) return
        if (joltAt >= 0f && elapsed >= joltAt) {
            joltAt = -1f
            val flip = if (jolts % 2 == 0) 1f else -1f
            jolts += 1
            pvx += 7 * flip
            pvy -= 6
        }
        val fx = -wa * PUPIL_FROM_WOBBLE
        val fy = accel * layout.near * PUPIL_FROM_SCROLL + qa * PUPIL_FROM_SQUASH
        pvx += (-PUPIL_STIFFNESS * px - PUPIL_DAMPING * pvx + clamp(fx, -400f, 400f)) * dt
        pvy += (-PUPIL_STIFFNESS * (py - PUPIL_REST) - PUPIL_DAMPING * pvy + clamp(fy, -400f, 400f)) * dt
        var x = px + pvx * dt
        var y = py + pvy * dt
        val r = hypot(x, y)
        if (r > 1f) {
            // The pupil hits the rim of its eye and bounces off it.
            x /= r
            y /= r
            val along = pvx * x + pvy * y
            if (along > 0f) {
                pvx -= PUPIL_BOUNCE * along * x
                pvy -= PUPIL_BOUNCE * along * y
            }
        }
        px = x
        py = y
    }

    private fun setFocus(index: Int) {
        focus = index
        refreshCaption()
    }

    private fun refreshCaption() {
        val face = faces[focus]
        if (caption.focus == focus && caption.face == face) return
        oldCaption = Caption(caption.focus, caption.face, elapsed)
        caption = Caption(focus, face, elapsed)
    }

    /** A rock came to rest in front: unless it is the one that was already there, it changes face. */
    private fun settled(index: Int, user: Boolean) {
        setFocus(index)
        host.focused(rocks[index].name)
        if (index == settledAt) return
        settledAt = index
        // The next expression, skipping any worn by the rocks in view around it (two each way).
        val nearby = listOf(index - 2, index - 1, index + 1, index + 2).mapNotNull { faces.getOrNull(it) }
        var next = faces[index]
        do next = (next + 1) % PetRockFaces.COUNT while (next in nearby)
        setFace(index, next)
        refreshCaption()
        joltAt = elapsed + MORPH_DELAY + 0.016f
        if (user) softAt = elapsed + MORPH_DELAY
    }

    private fun setFace(index: Int, face: Int) {
        faces[index] = face
        morphs[index].show(face)
    }

    private fun goTo(target: Int, velocity: Float, user: Boolean) {
        byUser = user
        val to = clamp(target.toFloat(), 0f, last)
        // The rock counts as arrived as soon as it is still in front, before the spring's last wobble.
        arriving = to.toInt()
        arrivingByUser = user
        if (reducedMotion) {
            scroll.run(PetRockAnim.Timing(to, 0.2f))
        } else {
            scroll.run(PetRockAnim.Spring(to, SNAP_STIFFNESS, SNAP_DAMPING), velocity = if (velocity == 0f) null else velocity)
        }
    }

    private fun enter(user: Boolean) {
        if (entered) return
        entered = true
        scroll.jump(0f)
        if (reducedMotion) intro.run(PetRockAnim.Timing(1f, FADE_TIME)) else intro.run(PetRockAnim.Spring(1f, INTRO_STIFFNESS, INTRO_DAMPING))
        inCarousel = true
        if (user) host.light()
    }

    private fun exit() {
        if (!entered) return
        entered = false
        dragging = false
        arriving = -1
        if (reducedMotion) {
            scroll.run(PetRockAnim.Timing(0f, FADE_TIME))
            intro.run(PetRockAnim.Timing(0f, FADE_TIME))
        } else {
            scroll.run(PetRockAnim.Spring(0f, SNAP_STIFFNESS, SNAP_DAMPING))
            intro.run(PetRockAnim.Spring(0f, INTRO_STIFFNESS, INTRO_DAMPING))
        }
        // Back on the title: the first rock in front, and every rock in its first face.
        inCarousel = false
        focus = 0
        settledAt = 0
        rocks.forEachIndexed { k, rock -> setFace(k, rock.face) }
        refreshCaption()
    }

    private fun wiggle(user: Boolean) {
        if (user) host.medium()
        if (reducedMotion) return
        squash.run(PetRockAnim.Timing(1f, 0.09f, ::outQuad), PetRockAnim.Spring(0f, 380f, 8f))
        wobble.run(PetRockAnim.Timing(1f, 0.08f, ::outQuad), PetRockAnim.Spring(0f, 240f, 6f))
    }

    fun touched() {
        hold = USER_HOLD
    }

    // Buttons

    fun enterPressed() {
        touched()
        enter(true)
    }

    fun titlePressed() {
        touched()
        exit()
    }

    // Gestures (dp)

    fun panStarted() {
        if (!entered) return
        byUser = true
        arriving = -1
        dragFrom = scroll.value
        dragging = true
    }

    fun panMoved(dy: Float) {
        if (!dragging) return
        scroll.jump(rubber(dragFrom - dy / layout.near, last))
    }

    /** The finger let go at [vy] dp/s. */
    fun panEnded(vy: Float) {
        if (!dragging) return
        dragging = false
        // The finger's speed at release, in rocks per second, carries into the snap. VelocityTracker
        // can hand back NaN, which would poison the spring and every rock's alpha after it.
        val velocity = if (vy.isFinite()) -vy / layout.near else 0f
        val s = scroll.value
        val outside = s < 0f || s > last
        goTo(jsRound(s + if (outside) 0f else velocity * THROW_TIME).toInt(), if (outside) velocity * 0.3f else velocity, true)
    }

    fun tapped(x: Float, y: Float) {
        if (!entered) {
            // The little rock in the title can be poked too.
            if (abs(y - layout.titleY) < TITLE_ROCK && abs(x - layout.titleX) < TITLE_ROCK) wiggle(true)
            return
        }
        val dy = y - layout.centreY
        if (abs(dy) < layout.big * 0.45f) wiggle(true) else goTo(jsRound(scroll.value).toInt() + if (dy > 0) 1 else -1, 0f, true)
    }
}
