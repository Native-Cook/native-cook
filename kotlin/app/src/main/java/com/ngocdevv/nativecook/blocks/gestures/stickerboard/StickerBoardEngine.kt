package com.ngocdevv.nativecook.blocks.gestures.stickerboard

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Length of the self-playing loop, in seconds. It starts and ends with every sticker at home.
internal const val LOOP = 15f

// Moves in the loop: which sticker, when it is picked up and dropped (s), and where to, as an
// offset from home in dp of a 402 dp wide screen, a turn in degrees and a scale.
private class Move(val index: Int, val at: Float, val end: Float, val dx: Float, val dy: Float, val turn: Float, val scale: Float)

private val MOVES = arrayOf(
    Move(0, 0.6f, 2.2f, 150f, 250f, 18f, 1f),
    Move(1, 3.0f, 4.6f, -170f, 165f, -26f, 1.28f),
    Move(0, 9.7f, 11.1f, 0f, 0f, 0f, 1f),
    Move(1, 11.4f, 12.8f, 0f, 0f, 0f, 1f),
)

// The peel in the loop: the sticker, when it is pressed, when the corner lifts, when the finger has
// pulled the corner past the middle, when it has carried it near the bin, and when it lets go.
private const val PEEL_INDEX = 5
private const val PEEL_PRESS = 5.0f
private const val PEEL_START = 5.45f
private const val PEEL_PULLED = 6.9f
private const val PEEL_CARRIED = 8.0f
private const val PEEL_DROP = 8.5f
// How far past the middle the scripted finger pulls the corner, as a share of the corner's distance.
private const val PEEL_REACH = 0.7f
// The scripted carry ends this far off the bin's hover point (dp), inside its reach, so the magnet
// visibly pulls the sticker in the last bit.
private const val CARRY_TO_X = 54f
private const val CARRY_TO_Y = -56f
// A carried sticker travels on an arc this far off the straight line (dp).
private const val ARC = 40f
// After a touch the loop waits this long (s), then tidies every sticker home over TIDY_TIME s.
private const val USER_HOLD = 3.5f
private const val TIDY_TIME = 0.9f
private const val TIDY_REST = 0.3f

internal const val DESIGN_WIDTH = 402f
// The white die-cut border around every sticker (dp).
internal const val BORDER = 7f
// Picked up: grows by LIFT_GROW and its shadow falls further and softer.
internal const val LIFT_GROW = 0.06f
private const val LIFT_TIME = 0.12f
// A sticker picked up from under others does not jump over them: a copy stays where it was in the
// pile while the one on top fades in over RISE_TIME s on an ease-in-out curve, so it surfaces
// through the stickers that covered it as it lifts.
private const val RISE_TIME = 0.3f
internal val SHADOW_NEAR = floatArrayOf(1f, 2f, 2.2f)
internal val SHADOW_FAR = floatArrayOf(6f, 16f, 11f)
// Dropped: it keeps a little of its speed (time constant in s) while it falls for FALL_TIME s, then
// slaps down with a squash that springs out.
private const val GLIDE = 0.09f
private const val FALL_TIME = 0.16f
private const val MAX_THROW = 1500f
private const val SQUASH = 0.085f
private const val SQUASH_STIFFNESS = 480f
private const val SQUASH_DAMPING = 19f
// While carried it swings with the finger's speed: degrees per dp/s, up to SWAY_MAX.
private const val SWAY_PER_SPEED = 0.012f
private const val SWAY_MAX = 10f
// Pinch limits, rubber-banded beyond.
private const val MIN_SCALE = 0.55f
private const val MAX_SCALE = 2.2f
// Holding this long (s) lifts a corner instead; the corner follows the finger, and pulled further
// than DETACH times its distance from the middle the sticker comes off.
private const val LONG_PRESS = 0.42f
private const val PAN_SLOP = 4f
// Frames a touch that ended is given for one of the gestures to start before it counts as a tap.
private const val PENDING_FRAMES = 3
private const val DETACH = 1.25f
internal const val FLATTEN_TIME = 0.22f
private const val DETACHED_LIFT = 1.4f
private const val FOLLOW = 0.05f
// The bin notices a loose sticker within APPROACH dp of the point over its mouth and opens its lid
// the nearer it comes; within REACH dp it pulls the sticker in (let go of beyond REACH_OUT times that).
private const val APPROACH = 300f
private const val REACH = 92f
private const val REACH_OUT = 1.3f
// Pulled in, the sticker moves this share of the way to the mouth and shrinks by MAGNET_SHRINK.
private const val MAGNET_PULL = 0.82f
internal const val MAGNET_SHRINK = 0.3f
private const val MAGNET_STIFFNESS = 360f
private const val MAGNET_DAMPING = 19f
// A flick towards the bin from within FLING_RANGE dp, faster than FLING_SPEED dp/s and within about
// 25° of it, throws the sticker away too.
private const val FLING_SPEED = 800f
private const val FLING_RANGE = 460f
private const val FLING_AIM = 0.9f
// Thrown away: it crumples into a ball of BALL dp over CRUMPLE s while it moves over the mouth, then
// drops in on a short arc over DROP s.
internal const val CRUMPLE = 0.34f
internal const val DROP = 0.3f
internal const val TOSS_END = CRUMPLE + DROP
internal const val BALL = 16f
internal const val BALL_HOP = 16f
internal const val BALL_SPIN = 170f
// The bin's springs (stiffness, damping): rising, the lid, its slam and bounce, leaning and growing
// towards a sticker, the squash and wobble when the lid slams, and how long it stays up after.
private val SHOW = floatArrayOf(300f, 21f)
private val LID = floatArrayOf(240f, 15f)
private val SLAM = floatArrayOf(1500f, 14f)
private const val LID_BOUNCE = 0.32f
private val LEAN = floatArrayOf(260f, 20f)
private const val LEAN_MAX = 9f
private val GROW = floatArrayOf(300f, 20f)
private const val GROW_NEAR = 0.07f
private const val GROW_MAGNET = 0.05f
private val BIN_SQUASH = floatArrayOf(520f, 12f)
private const val BIN_SQUASH_KICK = 2.6f
private val WOBBLE = floatArrayOf(220f, 5f)
private const val WOBBLE_KICK = 70f
private const val LINGER = 0.9f
// A sticker thrown away comes back home after RESPAWN s, slapped back on over SPAWN_TIME s.
private const val RESPAWN = 4f
internal const val SPAWN_TIME = 0.22f
internal const val IMAGE_FADE = 0.3f

internal fun clamp01(x: Float) = if (x.isNaN()) 0f else x.coerceIn(0f, 1f)
internal fun easeInOut(p: Float) = if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).pow(3) / 2f
internal fun easeOut(p: Float) = 1f - (1f - p).pow(3)

/** Ease-out that runs a little past 1 and comes back. */
internal fun easeOutBack(p: Float): Float {
    val c = 1.7f
    val q = p - 1f
    return 1f + (c + 1f) * q * q * q + c * q * q
}

/** One step of a damped spring, in small sub-steps so a long frame cannot blow it up: value, speed. */
private fun spring(x: Float, v: Float, target: Float, k: FloatArray, dt: Float, out: FloatArray) {
    val steps = max(1, kotlin.math.ceil(dt * 240f).toInt())
    val h = dt / steps
    var nx = x
    var nv = v
    repeat(steps) {
        nv += (-k[0] * (nx - target) - k[1] * nv) * h
        nx += nv * h
    }
    if (nx.isNaN() || nv.isNaN() || nx.isInfinite() || nv.isInfinite()) {
        out[0] = target
        out[1] = 0f
    } else {
        out[0] = nx
        out[1] = nv
    }
}

private fun rubber(x: Float, lo: Float, hi: Float, band: Float) = when {
    x < lo -> lo - band * (1f - 1f / ((lo - x) / band + 1f))
    x > hi -> hi + band * (1f - 1f / ((x - hi) / band + 1f))
    else -> x
}

/** One sticker's live state. Positions in screen dp, turns in degrees. */
internal class Live {
    var x = 0f
    var y = 0f
    var turn = 0f
    var scale = 1f
    var vx = 0f
    var vy = 0f
    var sway = 0f
    var lift = 0f
    var held = false
    var byUser = false
    var falling = false
    var squash = 0f
    var squashSpeed = 0f
    // Seconds since it was thrown away (−1 on the board), and where, how turned and how big it was.
    var gone = -1f
    var goneX = 0f
    var goneY = 0f
    var goneTurn = 0f
    var goneScale = 1f
    var goneByUser = false
    // Seconds since it was slapped back on (−1 for none).
    var spawn = -1f
    // Rising through the pile: 0…1 (1 settled on top), and the sticker its old copy is drawn under.
    var rise = 1f
    var riseUnder = -1
    // Where the tidy-up starts it from.
    var fromX = 0f
    var fromY = 0f
    var fromTurn = 0f
    var fromScale = 1f
}

/** The peeled corner: the sticker, its corner (sticker units), the finger (screen), and more. */
internal class Peel {
    var index = -1
    var cx = 0f
    var cy = 0f
    var fx = 0f
    var fy = 0f
    var detached = false
    // Seconds since the flap began to lie flat again (−1 for not), and the corner it starts from.
    var flatten = -1f
    var flatX = 0f
    var flatY = 0f
    var anchorX = 0f
    var anchorY = 0f
    var byUser = false
}

/** Everything about the bin that moves, kept as springs (value and speed) and a few flags. */
internal class BinState {
    var show = 0f
    var showV = 0f
    var lid = 0f
    var lidV = 0f
    var lean = 0f
    var leanV = 0f
    var grow = 1f
    var growV = 0f
    var squash = 0f
    var squashV = 0f
    var wobble = 0f
    var wobbleV = 0f
    var magnet = 0f
    var magnetV = 0f
    // A loose sticker is within reach and held over the mouth.
    var snapped = false
    // A ball has gone in and the lid is slamming; whether it has hit, and who threw the ball.
    var landed = false
    var slam = false
    var slamHit = false
    var byUser = false
    // Seconds the bin stays up after the slam.
    var linger = 0f
    // Stickers thrown away (shown modulo 100), the number before, the roll between them, and balls
    // that went in since the last slam.
    var count = 0
    var prev = 0
    var roll = 1f
    var pending = 0

    fun resetCount() {
        count = 0
        prev = 0
        roll = 1f
        pending = 0
    }

    fun reset() {
        show = 0f; showV = 0f; lid = 0f; lidV = 0f; lean = 0f; leanV = 0f; grow = 1f; growV = 0f
        squash = 0f; squashV = 0f; wobble = 0f; wobbleV = 0f; magnet = 0f; magnetV = 0f
        snapped = false; landed = false; slam = false; slamHit = false; byUser = false; linger = 0f
        resetCount()
    }
}

/** A screen point in a sticker's own units. */
internal fun toLocal(s: Live, k: Float, x: Float, y: Float): FloatArray {
    val a = -s.turn * PI.toFloat() / 180f
    val dx = x - s.x
    val dy = y - s.y
    val scale = max(0.01f, k * s.scale)
    return floatArrayOf((dx * cos(a) - dy * sin(a)) / scale, (dx * sin(a) + dy * cos(a)) / scale)
}

/** A point of a sticker's own units on the screen. */
private fun toScreen(x0: Float, y0: Float, turn: Float, scale0: Float, k: Float, x: Float, y: Float): FloatArray {
    val a = turn * PI.toFloat() / 180f
    val scale = k * scale0
    return floatArrayOf(x0 + (x * cos(a) - y * sin(a)) * scale, y0 + (x * sin(a) + y * cos(a)) * scale)
}

private fun toScreen(s: Live, k: Float, x: Float, y: Float) = toScreen(s.x, s.y, s.turn, s.scale, k, x, y)

/** Haptics the board asks for: one per commit. */
internal enum class Thud { Drop, Lift, Rip, Snap, Slam }

/**
 * The board's state, stepped once a frame like the RN frame callback, and a stand-in for the React
 * Native gesture set: a long-press pan that peels, racing a pan, a pinch and a rotation that run
 * together on the sticker under the fingers.
 */
internal class StickerBoardEngine(private val stickers: List<StickerDef>, private val shapes: List<StickerShape>, private val thud: (Thud) -> Unit) {
    val live = Array(stickers.size) { Live() }
    var order = IntArray(stickers.size) { it }
    val peel = Peel()
    val bin = BinState()
    private val sp = FloatArray(2)
    var elapsed = 0f
    val loaded = arrayOfNulls<LoadedPhoto>(stickers.size)
    var auto = false
    var reduced = false

    // The screen, in dp, and the safe area at its top and bottom.
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f

    // The sticker under the fingers, whether a drag, pinch, turn or peel began, and a release
    // waiting a few frames to see whether any of them did.
    private var active = -1
    private var started = false
    private var pending = 0
    // A peel the finger is still holding.
    private var peeling = false
    private var dragFromX = 0f
    private var dragFromY = 0f
    private var turnFrom = 0f
    private var scaleFrom = 1f
    private var script = 0f
    private var hold = 0f
    private var tidy = -1f
    private var fingerX = 0f
    private var fingerY = 0f
    private var laidOut = floatArrayOf(-1f, -1f, -1f, -1f)

    val k get() = width / DESIGN_WIDTH

    /** Every sticker at home once the board is laid out (again), like the React Native layout effect. */
    fun layout(w: Float, h: Float, t: Float, b: Float) {
        if (laidOut[0] == w && laidOut[1] == h && laidOut[2] == t && laidOut[3] == b) return
        laidOut = floatArrayOf(w, h, t, b)
        width = w
        height = h
        top = t
        bottom = b
        for (i in live.indices) {
            val l = Live()
            val home = homeOf(i)
            l.x = home[0]
            l.y = home[1]
            l.turn = stickers[i].turn
            live[i] = l
        }
        order = IntArray(stickers.size) { it }
        resetPeel()
        active = -1
        peeling = false
        bin.reset()
    }

    private fun resetPeel() {
        peel.index = -1
        peel.detached = false
        peel.flatten = -1f
        peel.byUser = false
    }

    /** The point over the bin's mouth where a sticker is pulled in, with the bin standing up straight. */
    private fun hoverX() = width / 2f
    private fun hoverY() = height - bottom - (BIN_FLOOR + BIN_BODY + BIN_HOVER) * k

    fun restartLoop() {
        script = 0f
        hold = 0f
    }

    /** Home of sticker `i` on the screen. */
    private fun homeOf(i: Int) = if (i in stickers.indices) floatArrayOf(stickers[i].homeX * width, top + stickers[i].homeY * (height - top - bottom)) else floatArrayOf(width / 2f, height / 2f)

    /** Puts sticker `i` on top of the pile, rising through the ones that covered it. */
    private fun raise(i: Int) {
        if (i !in live.indices) return
        val at = order.indexOf(i)
        if (at < 0 || at >= order.size - 1) return
        val under = order[at + 1]
        order = (order.filter { it != i } + i).toIntArray()
        val s = live[i]
        // Still rising from before: the copy stays where it was, under the same sticker.
        if (s.rise >= 1f) s.riseUnder = under
        s.rise = 0f
    }

    /** The top sticker under a point, or −1. */
    private fun hit(x: Float, y: Float): Int {
        for (j in order.indices.reversed()) {
            val i = order[j]
            if (i !in live.indices || i !in shapes.indices) continue
            val s = live[i]
            if (s.gone >= 0f) continue
            val p = toLocal(s, k, x, y)
            if (shapes[i].contains(p[0], p[1])) return i
        }
        return -1
    }

    /** Picks up sticker `i` and puts it on top of the pile. */
    private fun pickUp(i: Int, byUser: Boolean) {
        if (i !in live.indices) return
        active = i
        started = false
        raise(i)
        val s = live[i]
        s.held = true
        s.byUser = byUser
        s.falling = false
        s.vx = 0f
        s.vy = 0f
    }

    /** Picks up the sticker under a finger, once per touch. */
    private fun grab(x: Float, y: Float) {
        if (active >= 0) return
        pickUp(hit(x, y), true)
    }

    /** Lets go of a carried sticker: it keeps a little speed, falls, and slaps down. */
    private fun release(i: Int, vx: Float, vy: Float) {
        if (i !in live.indices) return
        val s = live[i]
        if (s.held) {
            s.held = false
            s.falling = true
            var tx = if (reduced || vx.isNaN()) 0f else vx.coerceIn(-MAX_THROW, MAX_THROW)
            var ty = if (reduced || vy.isNaN()) 0f else vy.coerceIn(-MAX_THROW, MAX_THROW)
            // Aim the glide so the sticker lands on the board.
            val endX = (s.x + tx * GLIDE).coerceIn(16f, max(16f, width - 16f))
            val endY = (s.y + ty * GLIDE).coerceIn(top + 16f, max(top + 16f, height - bottom - 16f))
            tx = (endX - s.x) / GLIDE
            ty = (endY - s.y) / GLIDE
            s.vx = tx
            s.vy = ty
        }
        if (active == i) active = -1
    }

    /** Throws sticker `i` away: from where it hangs now it crumples into a ball and drops in the bin. */
    private fun toss(i: Int, byUser: Boolean) {
        if (i !in live.indices) return
        val s = live[i]
        val grow = if (reduced) 1f else 1f + LIFT_GROW * min(s.lift, 1.4f)
        s.held = false
        s.falling = false
        s.gone = 0f
        s.goneX = s.x
        s.goneY = s.y
        s.goneTurn = s.turn + s.sway
        s.goneScale = s.scale * grow * (1f - MAGNET_SHRINK * clamp01(bin.magnet))
        s.goneByUser = byUser
        s.vx = 0f
        s.vy = 0f
    }

    private fun startPeel(i: Int, x: Float, y: Float, byUser: Boolean) {
        if (i !in live.indices || i !in shapes.indices) return
        val s = live[i]
        // The corner: the point of the sticker's reach in the direction of the finger.
        val p = toLocal(s, k, x, y)
        val len = max(1f, hypot(p[0], p[1]))
        val reach = shapes[i].extent * 0.9f
        val cx = p[0] / len * reach
        val cy = p[1] / len * reach
        val corner = toScreen(s, k, cx, cy)
        peel.index = i
        peel.cx = cx
        peel.cy = cy
        peel.fx = corner[0]
        peel.fy = corner[1]
        peel.detached = false
        peel.flatten = -1f
        peel.flatX = cx
        peel.flatY = cy
        peel.anchorX = 0f
        peel.anchorY = 0f
        peel.byUser = byUser
        peeling = true
        started = true
    }

    /**
     * Lets go of a peel. Off and held over the bin, or flicked at it, it is thrown away; off
     * elsewhere, it is dropped; still on, the flap lies back down.
     */
    private fun releasePeel(vx: Float, vy: Float) {
        val i = peel.index
        if (i !in live.indices || !peeling) return
        peeling = false
        if (peel.detached) {
            val tx = peel.fx + peel.anchorX
            val ty = peel.fy + peel.anchorY
            val dx = hoverX() - tx
            val dy = hoverY() - ty
            val dist = min(hypot(dx, dy), hypot(hoverX() - peel.fx, hoverY() - peel.fy))
            val speed = if (vx.isNaN() || vy.isNaN()) 0f else hypot(vx, vy)
            val aimed = speed > FLING_SPEED && dist < FLING_RANGE * k && dist > 1f && (vx * dx + vy * dy) / (speed * dist) > FLING_AIM
            if (bin.snapped || aimed) toss(i, peel.byUser) else release(i, vx, vy)
            resetPeel()
        } else {
            val f = toLocal(live[i], k, peel.fx, peel.fy)
            peel.flatten = 0f
            peel.flatX = f[0]
            peel.flatY = f[1]
        }
        if (active == i) active = -1
    }

    /** A touch takes over from the loop: anything it was carrying is put down. */
    private fun touched() {
        if (auto && hold <= 0f) {
            for (i in live.indices) if (live[i].held && !live[i].byUser && i != peel.index) release(i, 0f, 0f)
            if (peel.index >= 0 && !peel.byUser) releasePeel(0f, 0f)
            tidy = -1f
        }
        hold = USER_HOLD
    }

    // MARK: frame

    fun advance(dt: Float) {
        if (width <= 0f) return
        elapsed += dt
        val k = k

        checkLongPress()

        // A touch that ended without becoming a drag, pinch, turn or peel puts its sticker down. The
        // gestures settle in no fixed order, so this waits a few frames for one of them to start.
        if (pending > 0) {
            pending -= 1
            if (pending == 0 && !started && active >= 0 && peel.index < 0) release(active, 0f, 0f)
        }

        // The loop, or the tidy-up before it starts again.
        if (auto) {
            if (hold > 0f) {
                if (active < 0 && peel.index < 0) hold -= dt
                if (hold <= 0f) tidy = 0f
            } else if (tidy >= 0f) {
                val t = tidy + dt
                if (tidy == 0f) {
                    for (s in live) {
                        s.fromX = s.x
                        s.fromY = s.y
                        s.fromTurn = s.turn
                        s.fromScale = s.scale
                        if (s.gone >= 0f) s.gone = max(s.gone, RESPAWN)
                    }
                }
                val p = easeInOut(clamp01(t / TIDY_TIME))
                for (i in live.indices) {
                    val s = live[i]
                    if (s.gone >= 0f || s.spawn >= 0f) continue
                    val home = homeOf(i)
                    val moved = hypot(s.fromX - home[0], s.fromY - home[1]) > 1f || abs(s.fromTurn - stickers[i].turn) > 0.5f || abs(s.fromScale - 1f) > 0.01f
                    if (!moved) continue
                    // The tidy-up carries it now, so it is put down at the end like the loop's own moves.
                    if (t - dt <= 0f) {
                        s.held = true
                        s.byUser = false
                    }
                    s.x = s.fromX + (home[0] - s.fromX) * p
                    s.y = s.fromY + (home[1] - s.fromY) * p
                    s.turn = s.fromTurn + (stickers[i].turn - s.fromTurn) * p
                    s.scale = s.fromScale + (1f - s.fromScale) * p
                }
                if (t >= TIDY_TIME && tidy < TIDY_TIME) {
                    for (i in live.indices) if (live[i].held && !live[i].byUser) release(i, 0f, 0f)
                }
                if (t >= TIDY_TIME + TIDY_REST) {
                    tidy = -1f
                    script = 0f
                    // The loop starts again with an empty bin.
                    bin.resetCount()
                } else {
                    tidy = t
                }
            } else {
                runScript(dt)
            }
        }

        // The peel: a lifted corner comes off once pulled far enough; a released flap lies back down.
        if (peel.index >= live.size) resetPeel()
        if (peel.index in live.indices && peel.byUser && !peeling && peel.flatten < 0f) {
            // A user's peel whose finger has gone is let go of, so the board can never be left
            // holding a flap.
            val i = peel.index
            if (peel.detached) {
                resetPeel()
                release(i, 0f, 0f)
            } else {
                val f = toLocal(live[i], k, peel.fx, peel.fy)
                peel.flatten = 0f
                peel.flatX = f[0]
                peel.flatY = f[1]
            }
            if (active == i) active = -1
        }
        if (peel.index in live.indices && peel.index in shapes.indices) {
            val i = peel.index
            val s = live[i]
            if (peel.flatten >= 0f) {
                val f = peel.flatten + dt
                if (f >= FLATTEN_TIME) {
                    if (peel.detached) {
                        peel.flatten = -1f
                        peel.flatX = peel.cx
                        peel.flatY = peel.cy
                    } else {
                        resetPeel()
                        release(i, 0f, 0f)
                    }
                } else {
                    peel.flatten = f
                }
            } else if (!peel.detached) {
                val f = toLocal(s, k, peel.fx, peel.fy)
                if (hypot(f[0] - peel.cx, f[1] - peel.cy) > DETACH * shapes[i].extent) {
                    peel.detached = true
                    peel.flatten = 0f
                    peel.flatX = f[0]
                    peel.flatY = f[1]
                    peel.anchorX = s.x - peel.fx
                    peel.anchorY = s.y - peel.fy
                    if (peel.byUser) thud(Thud.Rip)
                }
            }
        }
        val q = peel

        // A loose sticker near the bin: how near, whether the bin has it, and its pull on it.
        val hx = hoverX()
        val hy = hoverY()
        val loose = q.index in live.indices && q.detached
        val tx = if (loose) q.fx + q.anchorX else hx
        val ty = if (loose) q.fy + q.anchorY else hy
        // Near when either the sticker or the finger holding its corner is near.
        val dist = if (loose) min(hypot(tx - hx, ty - hy), hypot(q.fx - hx, q.fy - hy)) else 0f
        val near = if (loose) clamp01((APPROACH * k - dist) / ((APPROACH - REACH) * k)) else 0f
        val wasSnapped = bin.snapped
        bin.snapped = when {
            !loose -> false
            dist < REACH * k -> true
            dist > REACH * REACH_OUT * k -> false
            else -> bin.snapped
        }
        if (bin.snapped && !wasSnapped && q.byUser) thud(Thud.Snap)
        if (reduced) {
            bin.magnet = if (bin.snapped) 1f else 0f
            bin.magnetV = 0f
        } else {
            spring(bin.magnet, bin.magnetV, if (bin.snapped) 1f else 0f, floatArrayOf(MAGNET_STIFFNESS, MAGNET_DAMPING), dt, sp)
            bin.magnet = sp[0]
            bin.magnetV = sp[1]
        }
        val magnet = if (loose) bin.magnet else 0f

        // Every sticker: carried ones lift and follow; dropped ones glide, fall and slap down; thrown
        // ones crumple, drop in and come back later.
        var landed = false
        var ballIn = 0
        var ballByUser = false
        var tossing = false
        for (i in live.indices) {
            val s = live[i]
            if (s.rise < 1f) {
                s.rise = min(1f, s.rise + dt / RISE_TIME)
                if (s.rise >= 1f) s.riseUnder = -1
            }
            if (s.gone >= 0f) {
                val before = s.gone
                s.gone += dt
                if (before < TOSS_END && s.gone >= TOSS_END) {
                    ballIn += 1
                    if (s.goneByUser) ballByUser = true
                } else if (s.gone < TOSS_END) {
                    tossing = true
                }
                if (s.gone >= RESPAWN) {
                    val home = homeOf(i)
                    s.gone = -1f
                    s.x = home[0]
                    s.y = home[1]
                    s.turn = if (i in stickers.indices) stickers[i].turn else 0f
                    s.scale = 1f
                    s.lift = 1f
                    s.sway = 0f
                    s.held = false
                    s.falling = false
                    s.spawn = 0f
                    s.squash = 0f
                    s.squashSpeed = 0f
                }
                continue
            }
            if (s.spawn >= 0f) {
                s.spawn += dt
                s.lift = 1f - clamp01(s.spawn / SPAWN_TIME)
                if (s.spawn >= SPAWN_TIME) {
                    s.spawn = -1f
                    s.lift = 0f
                    s.squash = if (reduced) 0f else SQUASH
                    s.squashSpeed = 0f
                }
                continue
            }
            val peeled = q.index == i
            if (peeled && q.detached) {
                // Off the board: it follows the finger, held by the point it was pulled from, and is
                // pulled towards the bin's mouth once the bin has it.
                val gx = tx + (hx - tx) * MAGNET_PULL * magnet
                val gy = ty + (hy - ty) * MAGNET_PULL * magnet
                val follow = 1f - exp(-dt / FOLLOW)
                s.vx = (gx - s.x) * follow / dt
                s.x += (gx - s.x) * follow
                s.y += (gy - s.y) * follow
                s.lift += (DETACHED_LIFT - s.lift) * (1f - exp(-dt / LIFT_TIME))
            } else if (s.held) {
                val target = if (peeled) 0.15f else 1f
                s.lift += (target - s.lift) * (1f - exp(-dt / LIFT_TIME))
            } else if (s.falling) {
                if (!reduced) {
                    s.x += s.vx * dt
                    s.y += s.vy * dt
                    val keep = exp(-dt / GLIDE)
                    s.vx *= keep
                    s.vy *= keep
                }
                s.lift = max(0f, s.lift - dt / FALL_TIME)
                if (s.lift <= 0f) {
                    s.falling = false
                    s.squash = if (reduced) 0f else SQUASH
                    s.squashSpeed = 0f
                    s.vx = 0f
                    s.vy = 0f
                    if (s.byUser) landed = true
                }
            }
            // It swings with its speed while carried, and settles when put down.
            val swayTo = if (s.held || (peeled && q.detached)) (s.vx * SWAY_PER_SPEED).coerceIn(-SWAY_MAX, SWAY_MAX) else 0f
            s.sway += (swayTo - s.sway) * (1f - exp(-dt / 0.12f))
            if (s.sway.isNaN()) s.sway = 0f
            if (s.squash != 0f || s.squashSpeed != 0f) {
                spring(s.squash, s.squashSpeed, 0f, floatArrayOf(SQUASH_STIFFNESS, SQUASH_DAMPING), dt, sp)
                s.squash = sp[0]
                s.squashSpeed = sp[1]
                if (abs(s.squash) < 0.0005f && abs(s.squashSpeed) < 0.01f) {
                    s.squash = 0f
                    s.squashSpeed = 0f
                }
            }
        }
        if (landed) thud(Thud.Drop)

        // The bin: up while a corner is lifted or a ball is on its way; it leans towards a loose
        // sticker, grows a little, opens its lid as the sticker nears, and slams it once the last
        // ball is in.
        val b = bin
        if (ballIn > 0) {
            b.landed = true
            b.pending += ballIn
            if (ballByUser) b.byUser = true
        }
        if (b.landed && !tossing) {
            b.landed = false
            b.slam = true
            b.slamHit = false
        }
        if (b.slam) {
            if (reduced) {
                b.lid = 0f
                b.lidV = -1f
            } else {
                spring(b.lid, b.lidV, 0f, SLAM, dt, sp)
                b.lid = sp[0]
                b.lidV = sp[1]
            }
            if (b.lid <= 0f && b.lidV <= 0f) {
                b.lid = 0f
                b.lidV = if (reduced) 0f else -b.lidV * LID_BOUNCE
                if (!b.slamHit) {
                    b.slamHit = true
                    if (b.byUser) thud(Thud.Slam)
                    b.byUser = false
                    b.prev = b.count
                    b.count = (b.count + max(1, b.pending)) % 100
                    b.pending = 0
                    b.roll = if (reduced) 1f else 0f
                    b.linger = LINGER
                    if (!reduced) {
                        b.squashV += BIN_SQUASH_KICK
                        b.wobbleV += if (b.count % 2 == 0) WOBBLE_KICK else -WOBBLE_KICK
                    }
                }
            }
            if (b.slamHit && b.lid < 0.004f && abs(b.lidV) < 0.06f) {
                b.slam = false
                b.lid = 0f
                b.lidV = 0f
            }
        } else {
            val open = if (tossing || b.landed || b.snapped) 1f else near.pow(1.3f) * 0.85f
            if (reduced) {
                b.lid = open
                b.lidV = 0f
            } else {
                spring(b.lid, b.lidV, open, LID, dt, sp)
                b.lid = sp[0]
                b.lidV = sp[1]
                if (b.lid < 0f) {
                    b.lid = 0f
                    b.lidV = 0f
                }
            }
        }
        if (!b.slam && b.linger > 0f) b.linger = max(0f, b.linger - dt)
        val up = if (q.index >= 0 || tossing || b.landed || b.slam || b.linger > 0f) 1f else 0f
        if (reduced) {
            b.show += (up - b.show) * (1f - exp(-dt / 0.12f))
            b.showV = 0f
        } else {
            spring(b.show, b.showV, up, SHOW, dt, sp)
            b.show = sp[0]
            b.showV = sp[1]
            val lean = near * (((tx + q.fx) / 2f - hx) / (120f * k)).coerceIn(-1f, 1f) * LEAN_MAX
            spring(b.lean, b.leanV, if (loose) lean else 0f, LEAN, dt, sp)
            b.lean = sp[0]
            b.leanV = sp[1]
            spring(b.grow, b.growV, 1f + GROW_NEAR * near + GROW_MAGNET * magnet, GROW, dt, sp)
            b.grow = sp[0]
            b.growV = sp[1]
            spring(b.squash, b.squashV, 0f, BIN_SQUASH, dt, sp)
            b.squash = sp[0].coerceIn(-0.2f, 0.25f)
            b.squashV = sp[1]
            spring(b.wobble, b.wobbleV, 0f, WOBBLE, dt, sp)
            b.wobble = sp[0].coerceIn(-12f, 12f)
            b.wobbleV = sp[1]
        }
        if (b.roll < 1f) b.roll = min(1f, b.roll + dt / ROLL_TIME)
    }

    private fun runScript(dt: Float) {
        val k = k
        val was = script
        var t = was + dt
        if (t >= LOOP) {
            t -= LOOP
            bin.resetCount()
        }
        fun crossed(at: Float) = if (t >= was) was < at && t >= at else was < at || t >= at
        for ((m, move) in MOVES.withIndex()) {
            if (move.index !in live.indices || move.index !in stickers.indices) continue
            if (crossed(move.at)) {
                raise(move.index)
                live[move.index].held = true
                live[move.index].byUser = false
                live[move.index].falling = false
            }
            if (t >= move.at && t <= move.end) {
                // Where the sticker was before this move: its last move's end, or home.
                var fx = 0f
                var fy = 0f
                var fTurn = 0f
                var fScale = 1f
                for (q in 0 until m) {
                    if (MOVES[q].index == move.index) {
                        fx = MOVES[q].dx
                        fy = MOVES[q].dy
                        fTurn = MOVES[q].turn
                        fScale = MOVES[q].scale
                    }
                }
                val p = easeInOut((t - move.at) / (move.end - move.at))
                val home = homeOf(move.index)
                val ex = (move.dx - fx) * k
                val ey = (move.dy - fy) * k
                val len = max(1f, hypot(ex, ey))
                val bulge = sin(PI.toFloat() * p) * ARC * k
                val s = live[move.index]
                s.x = home[0] + fx * k + ex * p + (-ey / len) * bulge
                s.y = home[1] + fy * k + ey * p + (ex / len) * bulge
                s.turn = stickers[move.index].turn + fTurn + (move.turn - fTurn) * p
                s.scale = fScale + (move.scale - fScale) * p
            }
            if (crossed(move.end)) release(move.index, 0f, 0f)
        }
        // The peel: press, lift the corner, pull it off, carry it over the bin, let go.
        if (PEEL_INDEX !in live.indices || PEEL_INDEX !in stickers.indices) {
            script = t
            return
        }
        if (crossed(PEEL_PRESS)) {
            if (live[PEEL_INDEX].gone < 0f && active < 0) pickUp(PEEL_INDEX, false)
        }
        if (crossed(PEEL_START) && active == PEEL_INDEX) {
            val s = live[PEEL_INDEX]
            val def = stickers[PEEL_INDEX]
            val corner = toScreen(s, k, def.a, def.b)
            startPeel(PEEL_INDEX, corner[0], corner[1], false)
        }
        val p = peel
        if (p.index == PEEL_INDEX && !p.byUser) {
            if (t >= PEEL_START && t < PEEL_PULLED) {
                // The finger pulls the corner back across the sticker, past its middle.
                val e = easeInOut((t - PEEL_START) / (PEEL_PULLED - PEEL_START))
                val lx = p.cx + (-PEEL_REACH * p.cx - p.cx) * e
                val ly = p.cy + (-PEEL_REACH * p.cy - p.cy) * e
                val f = if (!p.detached) {
                    toScreen(live[PEEL_INDEX], k, lx, ly)
                } else {
                    val home = homeOf(PEEL_INDEX)
                    toScreen(home[0], home[1], stickers[PEEL_INDEX].turn, 1f, k, lx, ly)
                }
                p.fx = f[0]
                p.fy = f[1]
            }
            if (crossed(PEEL_PULLED)) {
                fingerX = p.fx
                fingerY = p.fy
            }
            if (t >= PEEL_PULLED && t < PEEL_DROP && p.detached) {
                // Carried on a curve to just off the bin's mouth, where the magnet takes it.
                val e = easeInOut(clamp01((t - PEEL_PULLED) / (PEEL_CARRIED - PEEL_PULLED)))
                val bx = hoverX() + CARRY_TO_X * k - p.anchorX
                val by = hoverY() + CARRY_TO_Y * k - p.anchorY
                val cx = (fingerX + bx) / 2f + 40f * k
                val cy = min(fingerY, by) - 60f * k
                val u = 1f - e
                p.fx = u * u * fingerX + 2f * u * e * cx + e * e * bx
                p.fy = u * u * fingerY + 2f * u * e * cy + e * e * by
            }
            if (crossed(PEEL_DROP)) releasePeel(0f, 0f)
        }
        script = t
    }

    // MARK: touches

    // The fingers on the board, in the order they came down: id, x, y.
    private val fingers = ArrayList<FloatArray>()
    private val fingerIds = ArrayList<Long>()
    private var downTime = 0L
    // The averaged pan: where its centroid began, the offset that keeps it from jumping when a
    // finger is added or lifted, and recent samples for its speed.
    private var originX = 0f
    private var originY = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private val samples = ArrayDeque<FloatArray>()
    private var panActive = false
    private var twoActive = false
    private var liftActive = false
    private var liftFailed = false
    private var twoDistance = 1f
    private var twoAngle = 0f

    private fun centroid(): FloatArray {
        var x = 0f
        var y = 0f
        for (f in fingers) {
            x += f[0]
            y += f[1]
        }
        val n = max(1, fingers.size)
        return floatArrayOf(x / n, y / n)
    }

    private fun translation(): FloatArray {
        val c = centroid()
        return floatArrayOf(c[0] - originX + offsetX, c[1] - originY + offsetY)
    }

    private fun velocity(): FloatArray {
        val first = samples.firstOrNull() ?: return floatArrayOf(0f, 0f)
        val last = samples.last()
        val span = last[0] - first[0]
        if (span < 0.001f) return floatArrayOf(0f, 0f)
        return floatArrayOf((last[1] - first[1]) / span, (last[2] - first[2]) / span)
    }

    private fun now() = System.nanoTime() / 1e9f

    private fun sample() {
        val t = now()
        val tr = translation()
        samples.addLast(floatArrayOf(t, tr[0], tr[1]))
        while (samples.isNotEmpty() && t - samples.first()[0] > 0.1f) samples.removeFirst()
    }

    /** Keeps the averaged translation where it was across a change in the number of fingers. */
    private inline fun rebase(change: () -> Unit) {
        val before = translation()
        change()
        val c = centroid()
        offsetX = before[0] - (c[0] - originX)
        offsetY = before[1] - (c[1] - originY)
        samples.clear()
        sample()
    }

    fun touchesBegan(ids: List<Long>, points: List<FloatArray>) {
        for (n in ids.indices) {
            val p = points[n]
            if (fingers.isEmpty()) {
                // A new gesture: each of the gestures begins on the sticker under the finger.
                fingers.add(p)
                fingerIds.add(ids[n])
                downTime = System.nanoTime()
                originX = p[0]
                originY = p[1]
                offsetX = 0f
                offsetY = 0f
                samples.clear()
                sample()
                panActive = false
                twoActive = false
                liftActive = false
                liftFailed = false
                touched()
                grab(p[0], p[1])
            } else {
                rebase {
                    fingers.add(p)
                    fingerIds.add(ids[n])
                }
            }
        }
        if (fingers.size >= 2 && !twoActive && !liftActive) {
            // The pinch and the rotation begin; the peel can no longer win.
            liftFailed = true
            val a = fingers[0]
            val b = fingers[1]
            touched()
            grab((a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f)
            twoActive = true
            started = true
            twoDistance = max(1f, hypot(b[0] - a[0], b[1] - a[1]))
            twoAngle = atan2(b[1] - a[1], b[0] - a[0])
            if (active in live.indices) {
                scaleFrom = live[active].scale
                turnFrom = live[active].turn
            }
        }
    }

    fun touchesMoved(ids: List<Long>, points: List<FloatArray>) {
        for (n in ids.indices) {
            val at = fingerIds.indexOf(ids[n])
            if (at >= 0) fingers[at] = points[n]
        }
        if (fingers.isEmpty()) return
        sample()
        val t = translation()
        val v = velocity()
        if (liftActive) {
            val f = fingers[0]
            if (peel.index >= 0 && peel.byUser && (peel.flatten < 0f || peel.detached)) {
                peel.fx = f[0]
                peel.fy = f[1]
            }
            return
        }
        if (!panActive && hypot(t[0], t[1]) >= PAN_SLOP) {
            panActive = true
            liftFailed = true
            started = true
            if (active in live.indices && peel.index < 0) {
                dragFromX = live[active].x
                dragFromY = live[active].y
            }
        }
        if (panActive && active in live.indices && peel.index < 0) {
            val s = live[active]
            s.x = rubber(dragFromX + t[0], 12f, width - 12f, 40f)
            s.y = rubber(dragFromY + t[1], top + 12f, height - bottom - 12f, 40f)
            s.vx = v[0]
            s.vy = v[1]
        }
        if (twoActive && active in live.indices && peel.index < 0 && fingers.size >= 2) {
            val a = fingers[0]
            val b = fingers[1]
            val distance = max(1f, hypot(b[0] - a[0], b[1] - a[1]))
            val angle = atan2(b[1] - a[1], b[0] - a[0])
            live[active].scale = rubber(scaleFrom * distance / twoDistance, MIN_SCALE, MAX_SCALE, 0.25f)
            live[active].turn = turnFrom + (angle - twoAngle) * 180f / PI.toFloat()
        }
    }

    fun touchesEnded(ids: List<Long>, points: List<FloatArray>) {
        val v = velocity()
        for (n in ids.indices) {
            val at = fingerIds.indexOf(ids[n])
            if (at >= 0) fingers[at] = points[n]
        }
        val remaining = fingerIds.count { it !in ids }
        if (remaining > 0) {
            rebase {
                for (id in ids) {
                    val at = fingerIds.indexOf(id)
                    if (at >= 0) {
                        fingers.removeAt(at)
                        fingerIds.removeAt(at)
                    }
                }
            }
            if (fingers.size < 2 && twoActive) {
                // The pinch ends: past a limit, the scale springs back inside it as the sticker lands.
                twoActive = false
                if (active in live.indices) live[active].scale = live[active].scale.coerceIn(MIN_SCALE, MAX_SCALE)
            }
            return
        }
        if (fingers.isEmpty()) return
        fingers.clear()
        fingerIds.clear()
        if (twoActive) {
            twoActive = false
            if (active in live.indices) live[active].scale = live[active].scale.coerceIn(MIN_SCALE, MAX_SCALE)
        }
        if (liftActive) {
            liftActive = false
            if (peel.byUser) releasePeel(v[0], v[1])
            if (peel.byUser) releasePeel(0f, 0f)
            touched()
            return
        }
        // The pan ends (with its speed if it was moving), then every gesture is finalised.
        if (panActive && peel.index < 0) release(active, v[0], v[1])
        if (started && peel.index < 0) release(active, 0f, 0f)
        touched()
        pending = PENDING_FRAMES
        panActive = false
    }

    /** The long-press pan wins once the finger has been held still long enough. */
    private fun checkLongPress() {
        if (fingers.size != 1 || liftActive || liftFailed || panActive) return
        if ((System.nanoTime() - downTime) / 1e9f < LONG_PRESS) return
        liftActive = true
        // The other gestures are cancelled: the pan is finalised.
        touched()
        pending = PENDING_FRAMES
        val f = fingers[0]
        if (active in live.indices && peel.index < 0) {
            startPeel(active, f[0], f[1], true)
            thud(Thud.Lift)
        }
    }
}
