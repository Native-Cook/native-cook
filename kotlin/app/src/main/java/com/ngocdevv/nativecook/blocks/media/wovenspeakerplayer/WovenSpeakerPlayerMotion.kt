package com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer

import com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer.WovenSpeakerPlayerTheme as Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

internal class WovenSpeakerPlayerSpring(val damping: Double, val stiffness: Double, val mass: Double)

/** Every number the canvas reads (RN `Look`). */
internal data class WovenSpeakerPlayerLook(
    var pos: Double = 0.0, // track position, seconds
    var glyph: Double = 0.0, // 0 paused .. 1 playing: the play -> pause path
    var press: Double = 1.0, // the centre control's press scale, 1 .. 0.9
    var bloom: Double = WovenSpeakerPlayerMotion.BLOOM_REST,
    var bloomR: Double = 60.0,
    var lamp: Double = 0.12, // the mesh's red rim light
    var reach: Double = 18.0,
    var title: Double = 0.0, // the three lines of the text block, 0 resting copy .. 1 playing copy
    var line: Double = 0.0,
    var pill: Double = 0.0,
    var refl: Double = 0.0, // the bezel's red reflection
    var turn: Double = 0.0, // the finger is on the mesh, 0 .. 1
    var psiO: Double = 0.0,
    var psiI: Double = 0.0,
    var omO: Double = 0.0,
    var omI: Double = 0.0,
    var shear: Double = 0.0,
    var finger: Double = 0.0,
    var dentX: Double = 0.0,
    var dentY: Double = 0.0,
    var dent: Double = 0.0,
    var r0: Double = 0.0,
    var a0: Double = 0.0,
    var r1: Double = 0.0,
    var a1: Double = 0.0,
    var r2: Double = 0.0,
    var a2: Double = 0.0,
    var r3: Double = 0.0,
    var a3: Double = 0.0,
    var wash: Double = 0.0, // the lamp's wash on the page, 0 .. 0.08
    var pulse: Double = 0.0, // an accented beat has reached the rim
    var pull: Double = 0.0,
    var pullAt: Double = 0.0,
    var handle: Double = 0.0, // the arc's handle dot 0 .. 1
    var head: Double = 0.0, // the arc head's limit pulse 0 .. 1
)

/**
 * The loop, the gesture constants and the physics of the RN block (`wsp-motion.ts`). Every frame of the autoplay is a pure function of the loop time
 * (LOOP = 8 s): the track plays from 0:00, the dome is turned to 1:40 and coasts to 1:46, turns back to 0:00 and the speaker pauses and rests.
 */
internal object WovenSpeakerPlayerMotion {
    const val LOOP = 8.0

    // phase table
    const val TAP = 0.4
    val GLYPH = doubleArrayOf(0.4, 0.66)
    val BLOOM = doubleArrayOf(0.46, 0.76)
    val RIM = doubleArrayOf(0.52, 0.74)
    val TITLE = doubleArrayOf(0.56, 0.8)
    val LINE = doubleArrayOf(0.6, 0.84)
    val PILL = doubleArrayOf(0.64, 0.88)
    val REFL = doubleArrayOf(0.73, 0.93)
    const val RING0 = 0.52
    const val TOUCH1 = 1.4
    val DENT1 = doubleArrayOf(1.4, 1.48)
    val MOVE1 = doubleArrayOf(1.5, 2.6)
    const val RELEASE1 = 2.9
    val SNAP = doubleArrayOf(3.4, 3.75)
    const val RELAX1_END = 4.3
    const val TOUCH2 = 5.6
    val DENT2 = doubleArrayOf(5.6, 5.68)
    val MOVE2 = doubleArrayOf(5.68, 6.4)
    const val RELEASE2 = 6.45
    const val PAUSE = 6.6
    val GLYPH_BACK = doubleArrayOf(6.6, 6.82)
    val PILL_BACK = doubleArrayOf(6.64, 6.88)
    val LINE_BACK = doubleArrayOf(6.68, 6.92)
    val TITLE_BACK = doubleArrayOf(6.72, 6.96)
    val LAMP_BACK = doubleArrayOf(6.6, 7.0)
    const val REST = 7.25

    const val BLOOM_REST = 0.65
    const val TRACK = 200.0
    const val TURN_SECONDS = 200.0
    const val BPM = 118.0
    const val BEAT = 60 / BPM
    const val ACCENT_EVERY = 4
    const val GAIN = 0.22
    const val INNER = 0.6
    const val SHEAR_FULL = 0.25
    const val RING_R0 = 104.0
    const val RING_RIM = 165.0
    const val RING_LIFE = 0.55
    const val RING_WEAK = 2.2
    const val RING_STRONG = 4.0
    const val FINGER_R = 135.0
    const val ANNULUS_INNER = 104.0
    const val ANNULUS_OUTER = 170.0
    const val DENT = 5.0
    const val PULL = 0.8

    // gesture mapping
    const val SLOP = 6.0
    const val COAST_DECAY = 0.996
    const val COAST_MAX_TURNS = 1.5
    const val COAST_STOP = 0.05
    const val RUBBER = 0.2
    const val RESUME_AFTER = 2.0
    const val TICK_EVERY = 5.0
    const val TICK_GAP = 0.1
    const val SWIPE = 56.0
    const val SWIPE_VELOCITY = 520.0
    const val TITLE_RUBBER = 0.3
    const val COVER_FOLLOW = 0.25
    const val SLIDE_SPAN = 140.0
    const val V_TAIL = 0.08

    val SNAP_SPRING = WovenSpeakerPlayerSpring(30.5, 322.3, 1.0)
    val GLIDE = WovenSpeakerPlayerSpring(22.1, 157.9, 1.0)
    val SNAP_SECOND = WovenSpeakerPlayerSpring(20.0, 260.0, 1.0)
    val RELAX = WovenSpeakerPlayerSpring(14.0, 60.0, 1.4)
    const val PRESS_RESPONSE = 0.35
    const val PRESS_ZETA = 0.85
    val TURN_SPRING = WovenSpeakerPlayerSpring(18.0, 240.0, 1.0)

    // pauses and haptic delays
    const val FIRST_NOTE_DELAY_MS = 120.0
    const val PAUSE_BEAT_DELAY_MS = 550.0

    fun clamp01(x: Double) = min(max(x, 0.0), 1.0)
    fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k

    fun smooth(a: Double, b: Double, x: Double): Double {
        val k = clamp01((x - a) / (b - a))
        return k * k * (3 - 2 * k)
    }

    /** 0 before a, 1 after b, linear in between */
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

    /** the y of a CSS cubic-bezier(x1, y1, x2, y2) at x (Newton) */
    fun bezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        val cx = 3 * x1
        val bx = 3 * (x2 - x1) - cx
        val ax = 1 - cx - bx
        val cy = 3 * y1
        val by = 3 * (y2 - y1) - cy
        val ay = 1 - cy - by
        var s = x
        for (i in 0 until 6) {
            val e = ((ax * s + bx) * s + cx) * s - x
            val d = (3 * ax * s + 2 * bx) * s + cx
            if (abs(e) < 1e-5 || abs(d) < 1e-6) break
            s -= e / d
        }
        s = clamp01(s)
        return ((ay * s + by) * s + cy) * s
    }

    fun easeOut(x: Double) = bezier(x, 0.2, 0.8, 0.2, 1.0)
    fun easeInOut(x: Double) = bezier(x, 0.65, 0.0, 0.35, 1.0)

    /** 0 -> 1 step response of a spring (response s, damping ratio) */
    fun springAt(t: Double, response: Double, zeta: Double): Double {
        if (t <= 0) return 0.0
        val w = (2 * PI) / response
        if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    /** the step response of a raw spring (damping c, stiffness k, mass m) */
    fun springRaw(t: Double, c: Double, k: Double, m: Double): Double {
        val w = sqrt(k / m)
        val zeta = c / (2 * sqrt(k * m))
        return springAt(t, (2 * PI) / w, zeta)
    }

    fun wrapPi(a: Double): Double {
        var x = a
        while (x > PI) x -= 2 * PI
        while (x < -PI) x += 2 * PI
        return x
    }

    /** "m:ss" of a position in seconds */
    fun timeLabel(sec: Double): String {
        val s = max(0, Math.floor(sec + 1e-6).toInt())
        val m = s / 60
        val r = s - m * 60
        return "$m:${if (r < 10) "0" else ""}$r"
    }

    // ---- the pulses ----

    class Ring(val r: Double, val a: Double)

    /** one pulse launched at t0: where it is at `now`, and how high. Radius 104 -> 165 pt in 0.55 s, gone at the rim. */
    fun ringAt(now: Double, t0: Double, amp: Double): Ring {
        val age = now - t0
        val u = age / RING_LIFE
        if (u < 0 || u >= 1) return Ring(0.0, 0.0)
        val env = smooth(0.0, 0.1, u) * (1 - smooth(0.72, 1.0, u))
        return Ring(RING_R0 + (RING_RIM - RING_R0) * u, amp * env)
    }

    /** the accented beat's arrival at the rim: 0 -> 1 -> 0 over about 0.3 s */
    fun pulseAt(now: Double, t0: Double): Double {
        val u = now - (t0 + RING_LIFE)
        if (u < 0 || u > 0.6) return 0.0
        return (1 - exp(-u / 0.03)) * exp(-u / 0.12) * 1.6
    }

    class Launch(val t: Double, val amp: Double)

    /** the beat grid of the demo: a pulse every beat while it plays and nobody touches the dome */
    private fun buildLaunches(): List<Launch> {
        val out = ArrayList<Launch>()
        for (k in 0 until 20) {
            val t = RING0 + k * BEAT
            if (t >= PAUSE) break
            val touching = (t >= TOUCH1 && t < RELEASE1 + 0.25) || (t >= TOUCH2 && t < RELEASE2 + 0.1)
            if (touching) continue
            out.add(Launch(t, if (k % ACCENT_EVERY == 0) RING_STRONG else RING_WEAK))
        }
        return out
    }

    val LAUNCHES = buildLaunches()

    /** fills the four ring slots of a Look from a list of launches (the newest four alive at `now`) */
    fun fillRings(look: WovenSpeakerPlayerLook, now: Double, launches: List<Launch>) {
        var n = 0
        var pulse = 0.0
        var i = launches.size - 1
        while (i >= 0 && n < 4) {
            val l = launches[i]
            if (l.amp >= RING_STRONG - 1e-6) pulse = max(pulse, pulseAt(now, l.t))
            val g = ringAt(now, l.t, l.amp)
            i--
            if (g.a <= 0) continue
            when (n) {
                0 -> { look.r0 = g.r; look.a0 = g.a }
                1 -> { look.r1 = g.r; look.a1 = g.a }
                2 -> { look.r2 = g.r; look.a2 = g.a }
                else -> { look.r3 = g.r; look.a3 = g.a }
            }
            n++
        }
        look.pulse = pulse
    }

    // finger path of the demo: forward from 3 o'clock to ~9 o'clock, then back faster
    private val FWD_TRAVEL = ((TRACK / 2 - 1) / TURN_SECONDS) * 2 * PI
    private val COAST_ANGLE = (12 * PI) / 180
    private const val COAST_TAU = 0.18
    private const val POS_TOUCH1 = TOUCH1 - TAP
    private val POS_SNAP_FROM = TRACK / 2 + (COAST_ANGLE / (2 * PI)) * TURN_SECONDS * (1 - exp(-(SNAP[0] - RELEASE1) / COAST_TAU))
    private val POS_SNAPPED = round(POS_SNAP_FROM)
    private val POS_TOUCH2 = POS_SNAPPED + (TOUCH2 - SNAP[1])
    private val PSI_TOUCH2 = GAIN * (FWD_TRAVEL + COAST_ANGLE)
    private val REV_START = PI + 0.2
    private val REV_TRAVEL = (POS_TOUCH2 / TURN_SECONDS) * 2 * PI

    /** position of the demo's track at loop time t */
    private fun demoPos(t: Double): Double {
        if (t < TAP) return 0.0
        if (t < MOVE1[0]) return min(t - TAP, POS_TOUCH1)
        if (t < RELEASE1) {
            val e = bezier(seg(t, MOVE1[0], MOVE1[1]), 0.45, 0.0, 0.25, 1.0)
            return POS_TOUCH1 + (TRACK / 2 - POS_TOUCH1) * e
        }
        if (t < SNAP[0]) return TRACK / 2 + (COAST_ANGLE / (2 * PI)) * TURN_SECONDS * (1 - exp(-(t - RELEASE1) / COAST_TAU))
        if (t < TOUCH2) {
            val sp = springAt(t - SNAP[0], 0.3, 1.0)
            val base = POS_SNAP_FROM + (POS_SNAPPED - POS_SNAP_FROM) * sp
            return base + max(0.0, t - SNAP[1])
        }
        if (t < MOVE2[0]) return POS_TOUCH2
        val e = bezier(seg(t, MOVE2[0], MOVE2[1]), 0.4, 0.0, 0.2, 1.0)
        return POS_TOUCH2 * (1 - e)
    }

    private class Turn(val psiO: Double, val shearD: Double, val finger: Double)

    /** the outer layer's angle, the layers' difference and the finger's bearing */
    private fun demoTurn(t: Double): Turn {
        if (t < TOUCH1) return Turn(0.0, 0.0, 0.0)
        if (t < RELEASE1) {
            val e = bezier(seg(t, MOVE1[0], MOVE1[1]), 0.45, 0.0, 0.25, 1.0)
            val psiO = GAIN * FWD_TRAVEL * e
            return Turn(psiO, (1 - INNER) * psiO, FWD_TRAVEL * e)
        }
        val since = t - RELEASE1
        val coast = COAST_ANGLE * (1 - exp(-since / COAST_TAU))
        val psiOc = GAIN * (FWD_TRAVEL + coast)
        val d0 = (1 - INNER) * GAIN * FWD_TRAVEL
        if (t < TOUCH2) {
            // the inner layer catches up (cloth easing), and is forced home by 4.3
            val shearD = d0 * (1 - springRaw(since, RELAX.damping, RELAX.stiffness, RELAX.mass)) * (1 - smooth(RELEASE1 + 1.1, RELAX1_END, t))
            return Turn(psiOc, shearD, FWD_TRAVEL)
        }
        if (t < MOVE2[0]) return Turn(PSI_TOUCH2, 0.0, REV_START)
        val e = bezier(seg(t, MOVE2[0], MOVE2[1]), 0.4, 0.0, 0.2, 1.0)
        val psiO = PSI_TOUCH2 * (1 - e)
        val dEnd = -(1 - INNER) * PSI_TOUCH2
        if (t < RELEASE2) return Turn(psiO, dEnd * e, REV_START - REV_TRAVEL * e)
        val s2 = t - RELEASE2
        val shearD = dEnd * (1 - springAt(s2, 0.55, 0.8)) * (1 - smooth(RELEASE2 + 0.5, REST, t))
        return Turn(0.0, shearD, REV_START - REV_TRAVEL)
    }

    /** every number of the demo's frame at loop time t (seconds, any value: it wraps) */
    fun lookAt(tIn: Double): WovenSpeakerPlayerLook {
        val t = ((tIn % LOOP) + LOOP) % LOOP
        val k = WovenSpeakerPlayerLook()
        // play and pause: the choreography of the concept, the pause in reverse order with the same steps
        fun up(a: Double, b: Double) = easeInOut(seg(t, a, b))
        val playing = t >= TAP && t < PAUSE
        fun rise(w: DoubleArray, fall: DoubleArray) = if (playing) up(w[0], w[1]) else if (t >= PAUSE) 1 - up(fall[0], fall[1]) else 0.0
        k.glyph = rise(GLYPH, GLYPH_BACK)
        val lampUp = if (playing) easeOut(seg(t, RIM[0], RIM[1])) else if (t >= PAUSE) 1 - easeOut(seg(t, LAMP_BACK[0], LAMP_BACK[1] - 0.1)) else 0.0
        k.lamp = mix(0.12, 1.0, lampUp)
        k.reach = mix(18.0, 46.0, lampUp)
        val bloomUp = if (playing) easeOut(seg(t, BLOOM[0], BLOOM[1])) else if (t >= PAUSE) 1 - easeOut(seg(t, LAMP_BACK[0], LAMP_BACK[1])) else 0.0
        k.bloom = mix(BLOOM_REST, 1.0, bloomUp)
        k.bloomR = mix(Theme.BLOOM_R0, Theme.BLOOM_R1, bloomUp)
        k.title = rise(TITLE, TITLE_BACK)
        k.line = rise(LINE, LINE_BACK)
        k.pill = rise(PILL, PILL_BACK)
        k.refl = if (playing) easeOut(seg(t, REFL[0], REFL[1])) else if (t >= PAUSE) 1 - easeOut(seg(t, GLYPH_BACK[0], GLYPH_BACK[0] + 0.2)) else 0.0
        // the centre control's press: down 80 ms, back on a spring (at the tap and at the pause)
        fun pressAt(t0: Double): Double {
            val d = seg(t, t0, t0 + 0.08)
            val u = t - (t0 + 0.08)
            return if (u < 0) 1 - 0.1 * easeOut(d) else 1 - 0.1 * (1 - springAt(u, PRESS_RESPONSE, PRESS_ZETA))
        }
        k.press = if (t >= PAUSE) pressAt(PAUSE) else if (t >= TAP) pressAt(TAP) else 1.0
        if (t >= PAUSE + 0.5) k.press = 1.0
        k.handle = if (playing) easeOut(seg(t, 0.9, 1.15)) else if (t >= PAUSE) 1 - easeOut(seg(t, GLYPH_BACK[0], GLYPH_BACK[1] + 0.3)) else 0.0

        // position, layers, finger
        k.pos = demoPos(t)
        val tr = demoTurn(t)
        k.psiO = tr.psiO
        k.psiI = tr.psiO - tr.shearD
        k.shear = max(-1.0, min(1.0, tr.shearD / SHEAR_FULL))
        k.finger = tr.finger
        // angular speeds for the weave's motion blur (rad of turn per frame at 60 fps), by a finite difference
        val dt = 1.0 / 120
        val ahead = demoTurn(t + dt)
        val behind = demoTurn(t - dt)
        k.omO = abs(ahead.psiO - behind.psiO) / (2 * dt) / 60
        k.omI = abs(ahead.psiO - ahead.shearD - (behind.psiO - behind.shearD)) / (2 * dt) / 60

        // the finger's dent and the engagement of the turn
        val on1 = t >= TOUCH1 && t < RELEASE1
        val on2 = t >= TOUCH2 && t < RELEASE2
        fun dentIn(a: DoubleArray) = easeOut(seg(t, a[0], a[1]))
        var dent = 0.0
        var turn = 0.0
        if (t >= TOUCH1 && t < TOUCH2) {
            dent = if (on1) dentIn(DENT1) else 1 - easeInOut(seg(t, RELEASE1, RELEASE1 + 0.3))
            turn = if (on1) springAt(t - TOUCH1, 0.35, 0.85) else 1 - easeInOut(seg(t, RELEASE1, RELEASE1 + 0.4))
        } else if (t >= TOUCH2) {
            dent = if (on2) dentIn(DENT2) else 1 - easeInOut(seg(t, RELEASE2, RELEASE2 + 0.3))
            turn = if (on2) springAt(t - TOUCH2, 0.35, 0.85) else 1 - easeInOut(seg(t, RELEASE2, RELEASE2 + 0.4))
        }
        k.dent = DENT * max(0.0, dent)
        k.turn = max(0.0, min(1.05, turn))
        k.dentX = FINGER_R * cos(tr.finger)
        k.dentY = FINGER_R * sin(tr.finger)
        // strands left out of line where the finger last was, relaxing after the release
        if (t >= RELEASE1 && t < TOUCH2) {
            k.pull = PULL * (1 - smooth(RELEASE1, RELEASE1 + 1.2, t))
            k.pullAt = FWD_TRAVEL
        } else if (t >= RELEASE2) {
            k.pull = PULL * (1 - smooth(RELEASE2, REST - 0.2, t))
            k.pullAt = REV_START - REV_TRAVEL
        }
        if (t >= REST) {
            // nothing of the finger is left: the same numbers as the first frame
            k.finger = 0.0
            k.pullAt = 0.0
            k.dentX = FINGER_R
            k.dentY = 0.0
        }
        fillRings(k, t, LAUNCHES)
        // the lamp's wash on the page: 5 % while it plays, breathing to 8 % on the accented beats
        val playE = if (playing) up(TAP, TAP + 0.45) else if (t >= PAUSE) 1 - up(PAUSE, PAUSE + 0.5) else 0.0
        k.wash = playE * (Theme.WASH_BASE + (Theme.WASH_BEAT - Theme.WASH_BASE) * min(k.pulse, 1.0))
        return k
    }

}
