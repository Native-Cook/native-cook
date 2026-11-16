package com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice

import com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice.ParticleRibbonVoiceTheme as Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** Everything the shader reads, in the shader's own uniform shapes (RN `Frame`). */
internal class PrvFrame(
    var phase: Double, // loop phase 0 .. 1 (whole cycles only)
    var line: DoubleArray, // y0, amplitude, wavelength, wave phase (cycles)
    var sheet: DoubleArray, // Wd, level, gain, bloom
    var twA: DoubleArray, // idle twist: amplitude, length, phase, slope
    var twB: DoubleArray, // tails: left, right, width, 0
    var think: DoubleArray, // amount, centre x, pitch, 0
    var pul: DoubleArray, // 8 x [x, amplitude, half width, velocity]
    var win: DoubleArray, // 2 x [x, strength, face-on angle, half width]
    var winB: DoubleArray, // 2 x [finger y, opening, bulge, 0]
    var bump: DoubleArray, // 2 x [x, height (pt, signed), half width, velocity]
    var opt: DoubleArray, // dust, loosening, p3, lift
    var extra: DoubleArray, // direction cue (reduced motion), 0, 0, 0
)

/** The loop, the live state machine and the numbers the shader reads (RN `prv-motion.ts`). Every autoplay frame is a pure function of the loop time. */
internal object ParticleRibbonVoiceMotion {
    const val LOOP = 9.0

    const val T_PRESS = 0.55
    const val T_RELEASE = 3.0
    const val T_SPEAK = 4.4
    const val T_IDLE_BACK = 6.5
    const val T_CAPTION_BACK = 7.1
    const val T_GRAB = 6.6
    const val T_LET_GO = 7.4
    const val T_THINK_IN = 3.05
    const val T_THINK_OUT = 4.4

    // spring presets: response s, damping ratio
    const val WIDTH_RESPONSE = 0.574
    const val WIDTH_ZETA = 0.913
    const val THREAD_RESPONSE = 0.5
    const val THREAD_ZETA = 1.0
    const val WINDOW_RESPONSE = 0.28
    const val WINDOW_ZETA = 0.9
    const val WINDOW_OUT_RESPONSE = 0.5
    const val WINDOW_OUT_ZETA = 1.0

    const val HIT = 70.0 // pt either side of the centre line that grabs the sheet
    const val WINDOW = 70.0 // half width of the finger's window
    const val OPEN_MAX = 26.0
    const val OPEN_DY = 90.0
    const val LEVEL_PER_PT = 220.0
    const val LEVEL_BASE = 0.25
    const val CANCEL_BELOW = 0.25
    const val RESUME_AFTER = 2.0
    const val RESUME_EASE = 0.6
    const val SPEED = 260.0
    const val BUMP_PER_VEL = 40.0
    const val BUMP_MAX = 22.0
    const val BUMP_WIDTH = 80.0
    const val BUMP_LIFE = 1.4
    const val THINK = 1.4
    const val REPLY_END = 2.1

    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t

    fun smooth(a: Double, b: Double, x: Double): Double {
        val t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }

    fun springAt(t: Double, response: Double, zeta: Double): Double {
        if (t <= 0) return 0.0
        val w = (2 * PI) / response
        if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    /** a level swell: a fast gaussian attack and an exponential release */
    private fun swell(tau: Double, rise: Double, fall: Double) = if (tau < 0) exp(-(tau / rise) * (tau / rise)) else exp(-tau / fall)

    // ---- the voice ----
    private val USER = arrayOf(
        doubleArrayOf(0.78, 0.34), doubleArrayOf(1.02, 0.42), doubleArrayOf(1.3, 0.55), doubleArrayOf(1.62, 0.74),
        doubleArrayOf(1.92, 0.9), doubleArrayOf(2.2, 1.0), doubleArrayOf(2.5, 0.8), doubleArrayOf(2.78, 0.58),
    )
    private val REPLY = arrayOf(doubleArrayOf(4.42, 0.55), doubleArrayOf(4.82, 0.7), doubleArrayOf(5.17, 0.6), doubleArrayOf(5.52, 0.5))
    const val X_WORD = 201.0
    const val X_RIGHT = 444.0
    const val X_LEFT = -42.0

    private fun userLevel(t: Double): Double {
        var keep = 1.0
        for (u in USER) keep *= 1 - u[1] * swell(t - u[0], 0.07, 0.3)
        return 1 - keep
    }

    private fun replyLevel(t: Double): Double {
        var keep = 1.0
        for (r in REPLY) keep *= 1 - r[1] * swell(t - (r[0] + 0.05), 0.07, 0.3)
        return 1 - keep
    }

    private const val W_IDLE = Theme.WD_IDLE
    private const val W_THREAD = Theme.WD_THREAD
    private const val W_SPEAK = Theme.WD_SPEAK
    private const val WD_LEVEL = Theme.WD_LEVEL
    private const val THINK_F = 7 / LOOP
    private val THINK_PITCH = (6 * PI) / 402

    fun emptyPulses() = doubleArrayOf(0.0, 0.0, 40.0, 0.0, 0.0, 0.0, 40.0, 0.0, 0.0, 0.0, 40.0, 0.0, 0.0, 0.0, 40.0, 0.0, 0.0, 0.0, 40.0, 0.0, 0.0, 0.0, 40.0, 0.0, 0.0, 0.0, 40.0, 0.0, 0.0, 0.0, 40.0, 0.0)
    fun emptyWin() = doubleArrayOf(0.0, 0.0, 0.0, WINDOW, 0.0, 0.0, 0.0, WINDOW)
    fun emptyBump() = doubleArrayOf(0.0, 0.0, BUMP_WIDTH, 0.0, 0.0, 0.0, BUMP_WIDTH, 0.0)

    class Params(
        val phase: Double, val waveP: Double, val amp: Double, val wd: Double, val level: Double, val idleW: Double, val idleP: Double, val think: Double,
        val thinkU0: Double, val pul: DoubleArray, val win: DoubleArray, val winB: DoubleArray, val bump: DoubleArray, val dir: Double, val loose: Double, val gain: Double,
    )

    fun buildFrame(p: Params): PrvFrame {
        val iw = p.idleW
        return PrvFrame(
            p.phase, doubleArrayOf(Theme.Y0, p.amp, Theme.WAVELENGTH, p.waveP), doubleArrayOf(p.wd, p.level, p.gain, 0.7),
            doubleArrayOf(Theme.TWIST_AMP * iw, Theme.TWIST_LENGTH, p.idleP, Theme.TWIST_SLOPE * iw),
            doubleArrayOf(Theme.TAIL_LEFT * iw, Theme.TAIL_RIGHT * iw, Theme.TAIL_WIDTH, 0.0), doubleArrayOf(p.think, p.thinkU0, THINK_PITCH, 0.0),
            p.pul, p.win, p.winB, p.bump, doubleArrayOf(1.0, p.loose, 0.0, 0.0), doubleArrayOf(p.dir, 0.0, 0.0, 0.0),
        )
    }

    /** the centre line at x (design pt) for a frame: the twin of lineAt() in the shader, without the finger's bulge */
    fun lineY(x: Double, f: PrvFrame): Double {
        var y = f.line[0] + f.line[1] * cos(2 * PI * (x / f.line[2] - f.line[3]))
        for (k in 0 until 2) {
            val d = (x - f.bump[k * 4]) / max(f.bump[k * 4 + 2], 1.0)
            y += f.bump[k * 4 + 1] * exp(-d * d)
        }
        return y
    }

    /** the twist angle without the finger's windows: the twin of twistAt() in the shader */
    fun twistBase(u: Double, f: PrvFrame): Double {
        var a = f.twA[0] * sin((2 * PI * u) / f.twA[1] + f.twA[2]) + f.twA[3] * ((u - 200) / 402)
        a += f.twB[0] * exp(-((u / f.twB[2]) * (u / f.twB[2]))) + f.twB[1] * exp(-(((402 - u) / f.twB[2]) * ((402 - u) / f.twB[2])))
        a += f.think[0] * (u - f.think[1]) * f.think[2]
        for (i in 0 until 8) {
            val x = (u - f.pul[i * 4]) / max(f.pul[i * 4 + 2], 1.0)
            a += f.pul[i * 4 + 1] * exp(-x * x)
        }
        return a
    }

    private fun faceOn(angle: Double) = round(angle / PI) * PI

    // ---- autoplay ----
    private class Pul(val t0: Double, val x0: Double, val v: Double, val a0: Double, val w: Double, val tau: Double, val life: Double, val rise: Double)

    private const val C = SPEED
    private const val FINGER_X0 = 300.0
    private const val FINGER_X1 = 175.0

    private val AUTO: List<Pul> = buildList {
        add(Pul(T_PRESS, X_LEFT, C, 0.9 * PI, 64.0, 0.9, 1.5, 0.0))
        for (u in USER) add(Pul(u[0] - 0.18, X_LEFT, C, PI * (0.3 + 0.55 * u[1]), 64.0, 0.9, 1.5, 0.0))
        for (r in REPLY) add(Pul(r[0], X_RIGHT, -C, PI * (0.3 + 0.55 * r[1]), 64.0, 0.9, 1.3, 0.0))
        add(Pul(T_LET_GO, FINGER_X1 - WINDOW, -C, 0.7 * PI, 40.0, 0.45, 1.2, 0.12))
        add(Pul(T_LET_GO, FINGER_X1 + WINDOW, C, 0.7 * PI, 40.0, 0.45, 1.2, 0.12))
    }

    /** the loop's flicks: t0, x0, velocity, signed height, half width, life */
    private val AUTO_BUMP = arrayOf(doubleArrayOf(T_RELEASE, 200.0, C, -14.0, BUMP_WIDTH, BUMP_LIFE))

    /** a bump rises in 80 ms and leaves over its last 0.5 s */
    private fun bumpGate(age: Double, life: Double) = smooth(0.0, 0.08, age) * (1 - smooth(life - 0.5, life, age))

    private fun bumpsAt(t: Double): DoubleArray {
        val out = emptyBump()
        var slot = 0
        var i = AUTO_BUMP.size - 1
        while (i >= 0 && slot < 2) {
            val b = AUTO_BUMP[i]
            i--
            val age = t - b[0]
            if (age < 0 || age > b[5]) continue
            out[slot * 4] = b[1] + b[2] * age
            out[slot * 4 + 1] = b[3] * bumpGate(age, b[5])
            out[slot * 4 + 2] = b[4]
            out[slot * 4 + 3] = b[2]
            slot++
        }
        return out
    }

    /** when each reply word appears (the loop's table; the same numbers drive the live engine) */
    val WORD_AT: DoubleArray = DoubleArray(REPLY.size) { REPLY[it][0] + (X_RIGHT - X_WORD) / C }

    private fun pulsesAt(t: Double): DoubleArray {
        val out = emptyPulses()
        var slot = 0
        var i = AUTO.size - 1
        while (i >= 0 && slot < 8) {
            val p = AUTO[i]
            i--
            val age = t - p.t0
            if (age < 0 || age > p.life) continue
            val gate = smooth(0.0, max(p.rise, 1e-3), age) * (1 - smooth(p.life - 0.5, p.life, age))
            out[slot * 4] = p.x0 + p.v * age
            out[slot * 4 + 1] = p.a0 * exp(-age / p.tau) * gate
            out[slot * 4 + 2] = p.w
            out[slot * 4 + 3] = p.v
            slot++
        }
        return out
    }

    private fun baseWidth(t: Double): Double =
        W_IDLE + (W_THREAD - W_IDLE) * springAt(t - T_RELEASE, WIDTH_RESPONSE, WIDTH_ZETA) + (W_SPEAK - W_THREAD) * springAt(t - T_SPEAK, WIDTH_RESPONSE, WIDTH_ZETA) +
            (W_IDLE - W_SPEAK) * springAt(t - T_IDLE_BACK, WIDTH_RESPONSE, WIDTH_ZETA)

    /** the finger of the parting beat: x, y (design pt) and the window's strength */
    private class Finger(val x: Double, val dy: Double, val s: Double)

    private fun fingerAt(t: Double): Finger {
        val k = smooth(6.75, 7.3, t)
        val e = k * k * (3 - 2 * k)
        val x = FINGER_X0 + (FINGER_X1 - FINGER_X0) * e
        val dy = -70 * smooth(6.7, 7.05, t) + 50 * smooth(7.1, 7.38, t)
        val sIn = springAt(t - T_GRAB, WINDOW_RESPONSE, WINDOW_ZETA)
        val sOut = springAt(t - T_LET_GO, WINDOW_OUT_RESPONSE, WINDOW_OUT_ZETA)
        return Finger(x, dy, clamp01(sIn - sOut))
    }

    /** the loop at time t without the finger: the sheet, the twists, the thread */
    private fun frameNoWindow(tIn: Double): PrvFrame {
        val t = ((tIn % LOOP) + LOOP) % LOOP
        val lu = userLevel(t)
        val lr = replyLevel(t)
        val level = 1 - (1 - lu) * (1 - lr)
        val base = baseWidth(t)
        val idleW = clamp01((base - W_THREAD) / (W_IDLE - W_THREAD))
        val wd = base + 4 * sin((2 * PI * 2 * t) / LOOP) * idleW + WD_LEVEL * (lu + lr)
        val levelLag = 1 - (1 - userLevel(t - 0.2)) * (1 - replyLevel(t - 0.2))
        val think = springAt(t - T_THINK_IN, THREAD_RESPONSE, THREAD_ZETA) - springAt(t - T_THINK_OUT, THREAD_RESPONSE, THREAD_ZETA)
        return buildFrame(
            Params(
                t / LOOP, Theme.WAVE_P0 + (3 * t) / LOOP, Theme.AMP + Theme.AMP_LEVEL * levelLag, wd, level, idleW, Theme.TWIST_PHASE + (2 * PI * t) / LOOP, clamp01(think),
                201 + 60 * sin(2 * PI * THINK_F * (t - T_RELEASE)), pulsesAt(t), emptyWin(), DoubleArray(8), bumpsAt(t), 0.0, 0.3 + 0.7 * level, 1.0,
            ),
        )
    }

    /** the parting beat's face-on angle: the field at the grab, a constant of the loop */
    private val GRAB_FACE_ON: Double = faceOn(twistBase(FINGER_X0, frameNoWindow(T_GRAB)))

    /** the loop at time t (any real; it wraps) */
    fun frameAt(tIn: Double): PrvFrame {
        val t = ((tIn % LOOP) + LOOP) % LOOP
        val f = frameNoWindow(t)
        val fg = fingerAt(t)
        if (fg.s > 0.001) {
            val cf = lineY(fg.x, f)
            f.win = doubleArrayOf(fg.x, fg.s, GRAB_FACE_ON, WINDOW, 0.0, 0.0, 0.0, WINDOW)
            f.winB = doubleArrayOf(cf + fg.dy, OPEN_MAX * clamp01(abs(fg.dy) / OPEN_DY), 0.3, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        return f
    }

    // ---- the caption ----
    /** line 2's layers, 0 .. 1 each: [question, listening, dot 1..3, word 1..7, tint of word 1..7] */
    const val CAPTION_LAYERS = 19
    const val MAX_WORDS = 7
    const val TINT_FOR = 0.24
    const val TINT = 0.35
    private const val OUT = 0.1
    private const val IN = 0.14
    private fun outAt(t: Double, a: Double) = smooth(a, a + OUT, t)
    private fun inAt(t: Double, a: Double) = smooth(a + 0.7 * OUT, a + 0.7 * OUT + IN, t)

    fun captionAt(tIn: Double): DoubleArray {
        val t = ((tIn % LOOP) + LOOP) % LOOP
        val out = DoubleArray(CAPTION_LAYERS)
        out[0] = clamp01(1 - outAt(t, T_PRESS) + inAt(t, T_CAPTION_BACK))
        out[1] = inAt(t, T_PRESS) * (1 - outAt(t, T_RELEASE))
        val dots = inAt(t, T_RELEASE) * (1 - outAt(t, T_SPEAK))
        out[2] = dots * smooth(T_RELEASE + 0.12, T_RELEASE + 0.2, t)
        out[3] = dots * smooth(T_RELEASE + 0.25 / THINK_F, T_RELEASE + 0.25 / THINK_F + 0.08, t)
        out[4] = dots * smooth(T_RELEASE + 0.75 / THINK_F, T_RELEASE + 0.75 / THINK_F + 0.08, t)
        val words = 1 - outAt(t, T_CAPTION_BACK)
        for (i in WORD_AT.indices) {
            out[5 + i] = words * smooth(WORD_AT[i], WORD_AT[i] + 0.06, t)
            out[5 + MAX_WORDS + i] = if (t >= WORD_AT[i]) out[5 + i] * (1 - smooth(WORD_AT[i], WORD_AT[i] + TINT_FOR, t)) else 0.0
        }
        return out
    }

    // ---- the request ----
    /** the request heard in the loop, one entry per word: its syllables (8 in all, the voice's 0.78 .. 3.00 s) */
    private val ASK_SYLLABLES = intArrayOf(1, 1, 2, 1, 1, 1, 1)
    private const val ASK_FROM = 0.78
    private const val ASK_STEP = (T_RELEASE - ASK_FROM) / 8
    const val ASK_LAYERS = MAX_WORDS + 1

    fun askAt(tIn: Double): DoubleArray {
        val t = ((tIn % LOOP) + LOOP) % LOOP
        val out = DoubleArray(8)
        val gone = 1 - smooth(T_CAPTION_BACK, T_CAPTION_BACK + 0.2, t)
        var heard = 0
        var i = 0
        while (i < ASK_SYLLABLES.size && i < MAX_WORDS) {
            val at = ASK_FROM + heard * ASK_STEP
            out[i] = smooth(at, at + 0.12, t) * gone
            heard += ASK_SYLLABLES[i]
            i++
        }
        out[MAX_WORDS] = smooth(T_PRESS - 0.06, T_PRESS + 0.06, t) * (1 - smooth(T_RELEASE - 0.06, T_RELEASE + 0.06, t))
        return out
    }

    /** what the ribbon's state is at loop time t: 0 idle, 1 listening, 2 thinking, 3 speaking */
    fun stateAt(tIn: Double): Int {
        val t = ((tIn % LOOP) + LOOP) % LOOP
        if (t >= T_PRESS && t < T_RELEASE) return 1
        if (t >= T_RELEASE && t < T_SPEAK) return 2
        if (t >= T_SPEAK && t < T_IDLE_BACK) return 3
        return 0
    }

    // ---- live ----
    private const val PL = 8
    private const val WL = 11
    private const val BL = 6

    class Live {
        var t = 0.0
        var mode = 0 // idle, listening, thinking, speaking
        var modeT = 0.0
        var talk = 0 // 1 + the finger's id while a finger holds the talk
        var talkY0 = 0.0
        var talkLevel = 0.0
        var hold = 0.0
        var level = 0.0
        var base = W_IDLE
        var baseV = 0.0
        var think = 0.0
        var thinkV = 0.0
        var waveP = 0.0
        var idleP = 0.0
        var acc = 0.0
        val pul = DoubleArray(PL * 8)
        val win = DoubleArray(2 * WL)
        val bump = doubleArrayOf(0.0, 0.0, 0.0, BUMP_WIDTH, 99.0, 1.0, 0.0, 0.0, 0.0, BUMP_WIDTH, 99.0, 1.0)
        var talkX = 0.0
        var talkY = 0.0
        var talkT = 0.0
        var talkVy = 0.0
        val sg = IntArray(4)
        var cross = 0
        var words = 0
        val cap = captionAt(0.0)
        var dots = 0
        var quiet = 0.0
        var gainIn = 0.0
        var gain = 0.0
        var fromMode = 0
        val wordT = DoubleArray(MAX_WORDS) { -1.0 }
    }

    fun liveRest(): Live {
        val f0 = frameAt(0.0)
        val s = Live()
        s.waveP = f0.line[3]
        s.idleP = f0.twA[2]
        return s
    }

    /** take the live state over from the loop at time t (a touch during autoplay): nothing jumps */
    fun liveFromLoop(s: Live, tIn: Double) {
        val t = ((tIn % LOOP) + LOOP) % LOOP
        val f = frameAt(t)
        s.t = t
        s.mode = stateAt(t)
        s.modeT = 0.0
        s.level = f.sheet[1]
        s.base = baseWidth(t)
        s.baseV = 0.0
        s.think = f.think[0]
        s.thinkV = 0.0
        s.waveP = f.line[3]
        s.idleP = f.twA[2]
        s.acc = 0.0
        s.talk = 0
        s.hold = 0.0
        s.words = 0
        s.wordT.fill(-1.0)
        s.fromMode = s.mode
        s.gain = 0.0
        for (i in 0 until PL) {
            val a = f.pul[i * 4 + 1]
            val on = abs(a) > 0.01
            s.pul[i * 8] = f.pul[i * 4]
            s.pul[i * 8 + 1] = f.pul[i * 4 + 3]
            s.pul[i * 8 + 2] = a
            s.pul[i * 8 + 3] = f.pul[i * 4 + 2]
            s.pul[i * 8 + 4] = if (on) 0.2 else 9.0
            s.pul[i * 8 + 5] = 0.9
            s.pul[i * 8 + 6] = 1.5
            s.pul[i * 8 + 7] = if (f.pul[i * 4 + 3] < 0) 1.0 else 0.0
        }
        s.win.fill(0.0)
        for (k in 0 until 2) {
            val h = f.bump[k * 4 + 1]
            val on = abs(h) > 0.05
            s.bump[k * BL] = f.bump[k * 4]
            s.bump[k * BL + 1] = f.bump[k * 4 + 3]
            s.bump[k * BL + 2] = h
            s.bump[k * BL + 3] = f.bump[k * 4 + 2]
            s.bump[k * BL + 4] = if (on) 0.3 else 99.0
            s.bump[k * BL + 5] = BUMP_LIFE
        }
        s.talkVy = 0.0
        s.sg.fill(0)
        val cap = captionAt(t)
        for (i in 0 until CAPTION_LAYERS) s.cap[i] = cap[i]
        s.dots = 0
        s.quiet = 0.0
        s.gainIn = 0.0
    }

    private fun emit(s: Live, x0: Double, v: Double, a: Double, w: Double, tau: Double, life: Double, speaking: Double) {
        // reuse a dead slot, else the oldest
        var k = 0
        var oldest = -1.0
        for (i in 0 until PL) {
            val age = s.pul[i * 8 + 4]
            if (age >= s.pul[i * 8 + 6]) {
                k = i
                oldest = 1e9
                break
            }
            if (age > oldest) {
                oldest = age
                k = i
            }
        }
        val o = k * 8
        s.pul[o] = x0
        s.pul[o + 1] = v
        s.pul[o + 2] = a
        s.pul[o + 3] = w
        s.pul[o + 4] = 0.0
        s.pul[o + 5] = tau
        s.pul[o + 6] = life
        s.pul[o + 7] = speaking
    }

    /** launch a transverse bump from x (design pt) toward the speaking side */
    private fun emitBump(s: Live, x0: Double, v: Double, h: Double, w: Double) {
        var k = 0
        var oldest = -1.0
        for (i in 0 until 2) {
            val age = s.bump[i * BL + 4]
            if (age >= s.bump[i * BL + 5]) {
                k = i
                break
            }
            if (age > oldest) {
                oldest = age
                k = i
            }
        }
        val o = k * BL
        s.bump[o] = x0
        s.bump[o + 1] = v
        s.bump[o + 2] = h
        s.bump[o + 3] = w
        s.bump[o + 4] = 0.0
        s.bump[o + 5] = BUMP_LIFE
    }

    /** set the mode (a prop, or the demo's flow) */
    fun liveMode(s: Live, mode: Int) {
        if (s.mode == mode) return
        s.fromMode = s.mode
        s.mode = mode
        s.modeT = 0.0
        s.acc = 0.55 // the first syllable of a phrase leaves almost at once
        if (mode == 3) {
            s.words = 0
            for (i in 0 until MAX_WORDS) s.wordT[i] = -1.0
        }
        if (mode == 2) s.dots = 0
    }

    /** a finger comes down (design pt). `parts` allows the sheet to be grabbed. Returns 1 for talk, 2 for part, 0 for nothing. */
    fun liveDown(s: Live, id: Int, x: Double, y: Double, c: Double, parts: Boolean, twist: Double): Int {
        s.quiet = 0.0
        if (parts && abs(y - c) <= HIT && x > 4 && x < 398) {
            for (k in 0 until 2) {
                if (s.win[k * WL] == 0.0) {
                    val o = k * WL
                    s.win[o] = 1.0
                    s.win[o + 1] = id.toDouble()
                    s.win[o + 2] = x
                    s.win[o + 3] = x
                    s.win[o + 4] = 0.0
                    s.win[o + 5] = 0.0
                    s.win[o + 6] = faceOn(twist)
                    s.win[o + 7] = y
                    s.win[o + 8] = 0.0
                    s.win[o + 9] = 0.0
                    s.win[o + 10] = s.t
                    return 2
                }
            }
        }
        if (s.talk == 0) {
            s.talk = id + 1
            s.talkY0 = y
            s.talkX = x
            s.talkY = y
            s.talkT = s.t
            s.talkVy = 0.0
            s.talkLevel = clamp01(LEVEL_BASE)
            s.hold = 0.0
            s.gainIn = 1.0
            emit(s, X_LEFT, C, 0.9 * PI, 64.0, 0.9, 1.5, 0.0)
            liveMode(s, 1)
            return 1
        }
        return 0
    }

    fun liveMove(s: Live, id: Int, x: Double, y: Double, c: Double, vx: Double) {
        s.quiet = 0.0
        for (k in 0 until 2) {
            val o = k * WL
            if (s.win[o] == 1.0 && s.win[o + 1] == id.toDouble()) {
                val nx = min(max(x, 20.0), 382.0)
                // the finger's horizontal speed (design pt/s), smoothed: the crossings it lets go of leave with it
                val dtv = s.t - s.win[o + 10]
                if (dtv > 0.004) {
                    s.win[o + 9] += ((nx - s.win[o + 3]) / dtv - s.win[o + 9]) * 0.35
                    s.win[o + 10] = s.t
                } else if (vx != 0.0) {
                    s.win[o + 9] = vx
                }
                s.win[o + 3] = nx
                s.win[o + 7] = y
                s.win[o + 8] = OPEN_MAX * clamp01(abs(y - c) / OPEN_DY)
                return
            }
        }
        if (s.talk == id + 1) {
            s.talkLevel = clamp01((s.talkY0 - y) / LEVEL_PER_PT + LEVEL_BASE)
            s.talkX = x
            // the finger's vertical speed, smoothed over a few events (the flick on release reads it)
            val dtv = s.t - s.talkT
            if (dtv > 0.004) {
                s.talkVy += ((y - s.talkY) / dtv - s.talkVy) * 0.35
                s.talkY = y
                s.talkT = s.t
            }
        }
    }

    /** a finger leaves. Returns 1 when a talk ended long enough to count (the demo then thinks). */
    fun liveUp(s: Live, id: Int, demoFlow: Boolean): Int {
        s.quiet = 0.0
        for (k in 0 until 2) {
            val o = k * WL
            if (s.win[o] == 1.0 && s.win[o + 1] == id.toDouble()) {
                // the window goes: the crossings leave in opposite directions at 260 pt/s plus the finger's vx, and decay
                s.win[o] = 2.0
                val xc = s.win[o + 2]
                val vx = s.win[o + 9]
                emit(s, xc - WINDOW, -C + 0.4 * vx, 0.7 * PI, 40.0, 0.45, 1.2, 0.0)
                emit(s, xc + WINDOW, C + 0.4 * vx, 0.7 * PI, 40.0, 0.45, 1.2, 0.0)
                return 0
            }
        }
        if (s.talk == id + 1) {
            s.talk = 0
            s.gainIn = 0.0
            if (s.hold < CANCEL_BELOW) {
                liveMode(s, 0)
                return 0
            }
            // the release's flick: the finger's vertical speed launches one bump toward the speaking side while the width falls
            val h = max(-BUMP_MAX, min(BUMP_MAX, s.talkVy / BUMP_PER_VEL))
            if (abs(h) >= 1.5) emitBump(s, s.talkX, C, h, BUMP_WIDTH)
            s.talkVy = 0.0
            if (demoFlow) liveMode(s, 2)
            return 1
        }
        return 0
    }

    private val SPEAK_AT = DoubleArray(REPLY.size) { REPLY[it][0] - REPLY[0][0] }

    /** advance the live state by dt seconds. `ext` >= 0 overrides the level (a microphone or the player). */
    fun liveStep(s: Live, dtIn: Double, demoFlow: Boolean, ext: Double, speed: Double) {
        val dt = min(max(dtIn, 0.0), 0.05)
        val prevT = s.modeT
        s.t += dt
        s.modeT += dt
        s.quiet += if (s.talk == 0 && s.win[0] != 1.0 && s.win[WL] != 1.0) dt else 0.0
        if (s.talk > 0) {
            s.hold += dt
            s.talkVy *= exp(-dt / 0.12) // a finger that stops is not flicking
        }
        // the demo's flow: thinking -> speaking -> idle
        if (demoFlow) {
            if (s.mode == 2 && s.modeT >= THINK) liveMode(s, 3)
            if (s.mode == 3 && s.modeT >= REPLY_END + 0.9) liveMode(s, 0)
        }
        // level: the finger's (or the microphone's) while listening, the reply's script while speaking
        var target = 0.0
        if (s.mode == 1) target = if (ext >= 0) ext else s.talkLevel
        else if (s.mode == 3) target = if (ext >= 0) ext else replyLevel(s.modeT + REPLY[0][0]) * 1
        val tau = if (target > s.level) 0.06 else 0.32
        s.level += (target - s.level) * (1 - exp(-dt / tau))
        // twists: one per syllable while listening (faster when louder), the reply's script while speaking
        if (s.mode == 1) {
            s.acc += (0.7 + 2.6 * s.level) * dt
            if (s.acc >= 1) {
                s.acc -= 1
                emit(s, X_LEFT, speed, PI * (0.3 + 0.55 * s.level), 64.0, 0.9, 1.5, 0.0)
            }
        } else if (s.mode == 3) {
            if (ext >= 0) {
                // a real reply: one twist per syllable of its level, from the right edge
                s.acc += (0.7 + 2.6 * s.level) * dt
                if (s.acc >= 1) {
                    s.acc -= 1
                    emit(s, X_RIGHT, -speed, PI * (0.3 + 0.55 * s.level), 64.0, 0.9, 1.3, 1.0)
                }
            } else {
                for (i in SPEAK_AT.indices) {
                    if (prevT <= SPEAK_AT[i] && s.modeT > SPEAK_AT[i]) emit(s, X_RIGHT, -speed, PI * (0.3 + 0.55 * REPLY[i][1]), 64.0, 0.9, 1.3, 1.0)
                }
            }
        }
        // the base width and the thread: springs, sub-stepped
        val bTarget = if (s.mode == 2) W_THREAD else if (s.mode == 3) W_SPEAK else W_IDLE
        val kB = 120.0
        val cB = 20.0
        val kT = 157.9
        val cT = 25.1
        val tTarget = if (s.mode == 2) 1.0 else 0.0
        val n = max(1, ceil(dt / 0.004).toInt())
        val h = dt / n
        for (i in 0 until n) {
            s.baseV += (kB * (bTarget - s.base) - cB * s.baseV) * h
            s.base += s.baseV * h
            s.thinkV += (kT * (tTarget - s.think) - cT * s.thinkV) * h
            s.think += s.thinkV * h
        }
        s.waveP += (3 * dt) / LOOP
        s.idleP += (2 * PI * dt) / LOOP
        // pulses
        for (i in 0 until PL) {
            val o = i * 8
            if (s.pul[o + 4] >= s.pul[o + 6]) {
                s.pul[o + 2] = 0.0
                continue
            }
            val x0 = s.pul[o]
            s.pul[o] += s.pul[o + 1] * dt
            s.pul[o + 4] += dt
            s.pul[o + 2] *= exp(-dt / s.pul[o + 5])
            // a reply word appears as its twist crosses the middle
            if (s.pul[o + 7] == 1.0 && x0 > X_WORD && s.pul[o] <= X_WORD) {
                if (s.words < MAX_WORDS) s.wordT[s.words] = s.t
                s.words += 1
            }
        }
        // windows
        for (k in 0 until 2) {
            val o = k * WL
            val st = s.win[o]
            if (st == 0.0) continue
            s.win[o + 2] += (s.win[o + 3] - s.win[o + 2]) * (1 - exp(-dt / 0.03))
            val held = st == 1.0
            if (held) s.win[o + 9] *= exp(-dt / 0.12) // a finger that stops is not carrying the crossings
            val response = if (held) WINDOW_RESPONSE else WINDOW_OUT_RESPONSE
            val zeta = if (held) WINDOW_ZETA else WINDOW_OUT_ZETA
            val kk = ((2 * PI) / response).pow(2)
            val cc = 2 * zeta * sqrt(kk)
            for (i in 0 until n) {
                s.win[o + 5] += (kk * ((if (held) 1.0 else 0.0) - s.win[o + 4]) - cc * s.win[o + 5]) * h
                s.win[o + 4] += s.win[o + 5] * h
            }
            if (!held && s.win[o + 4] < 0.002) {
                s.win[o] = 0.0
                s.win[o + 4] = 0.0
            }
        }
        // bumps ride along the centre line at 260 pt/s
        for (k in 0 until 2) {
            val o = k * BL
            if (s.bump[o + 4] >= s.bump[o + 5]) continue
            s.bump[o] += s.bump[o + 1] * dt
            s.bump[o + 4] += dt
        }
        // two fingers: a crossing (the edge of one window) passing under the other finger flips one of four signs
        if (s.win[0] == 1.0 && s.win[WL] == 1.0) {
            val xa = s.win[2]
            val xb = s.win[WL + 2]
            val d = WINDOW
            for (i in 0 until 4) {
                val v = if (i == 0) xb - (xa - d) else if (i == 1) xb - (xa + d) else if (i == 2) xa - (xb - d) else xa - (xb + d)
                val sgn = if (v > 0) 1 else -1
                if (s.sg[i] != 0 && s.sg[i] != sgn) s.cross += 1
                s.sg[i] = sgn
            }
        } else {
            s.sg.fill(0)
        }
        // the caption's layers follow the mode: a layer arrives only once the others are (almost) gone, so no two lines overlap
        val kOut = 1 - exp(-dt / 0.035)
        val kIn = 1 - exp(-dt / 0.05)
        val cap = s.cap
        // one dot per reversal of the thread (the shuttle's extremes) and the first at once
        s.dots = if (s.mode == 2) 1 + (if (s.modeT >= 0.25 / THINK_F) 1 else 0) + (if (s.modeT >= 0.75 / THINK_F) 1 else 0) else 0
        // after a reply the sentence stays 600 ms, then the question comes back
        val holdReply = s.mode == 0 && s.fromMode == 3 && s.modeT < 0.6
        val want0 = if (s.mode == 0 && !holdReply) 1.0 else 0.0
        val want1 = if (s.mode == 1) 1.0 else 0.0
        val want2 = if (s.mode == 2) 1.0 else 0.0
        val want3 = if (s.mode == 3 || holdReply) 1.0 else 0.0
        val g1 = cap[1]
        val g2 = max(cap[2], max(cap[3], cap[4]))
        var g3 = 0.0
        for (i in 5 until 5 + MAX_WORDS) g3 = max(g3, cap[i])
        val g0 = cap[0]
        val open0 = if (g1 < 0.25 && g2 < 0.25 && g3 < 0.25) 1.0 else 0.0
        val open1 = if (g0 < 0.25 && g2 < 0.25 && g3 < 0.25) 1.0 else 0.0
        val open2 = if (g0 < 0.25 && g1 < 0.25 && g3 < 0.25) 1.0 else 0.0
        val open3 = if (g0 < 0.25 && g1 < 0.25 && g2 < 0.25) 1.0 else 0.0
        cap[0] += (want0 * open0 - cap[0]) * (if (want0 * open0 > cap[0]) kIn else kOut)
        cap[1] += (want1 * open1 - cap[1]) * (if (want1 * open1 > cap[1]) kIn else kOut)
        for (i in 0 until 3) {
            val tg = want2 * open2 * (if (s.dots > i) 1.0 else 0.0)
            cap[2 + i] += (tg - cap[2 + i]) * (if (tg > cap[2 + i]) kIn else kOut)
        }
        for (i in 0 until MAX_WORDS) {
            val tg = want3 * open3 * (if (s.words > i) 1.0 else 0.0)
            cap[5 + i] += (tg - cap[5 + i]) * (if (tg > cap[5 + i]) 1 - exp(-dt / 0.02) else kOut)
            val ageW = if (s.wordT[i] < 0) 9.0 else s.t - s.wordT[i]
            cap[5 + MAX_WORDS + i] = if (ageW < TINT_FOR) cap[5 + i] * (1 - smooth(0.0, TINT_FOR, ageW)) else 0.0
        }
        // the finger's brightness lift: 60 ms in, 320 ms out (nothing pops)
        s.gain += ((if (s.gainIn > 0) 1.0 else 0.0) - s.gain) * (1 - exp(-dt / (if (s.gainIn > 0) 0.06 else 0.32)))
    }

    /** the frame the live state draws */
    fun liveFrame(s: Live): PrvFrame {
        val idleW = clamp01((s.base - W_THREAD) / (W_IDLE - W_THREAD))
        val pul = emptyPulses()
        var slot = 0
        for (i in 0 until PL) {
            if (slot >= 8) break
            val o = i * 8
            if (s.pul[o + 4] >= s.pul[o + 6]) continue
            val age = s.pul[o + 4]
            val life = s.pul[o + 6]
            val gate = 1 - smooth(life - 0.5, life, age)
            pul[slot * 4] = s.pul[o]
            pul[slot * 4 + 1] = s.pul[o + 2] * gate
            pul[slot * 4 + 2] = s.pul[o + 3]
            pul[slot * 4 + 3] = s.pul[o + 1]
            slot++
        }
        val win = emptyWin()
        val winB = DoubleArray(8)
        for (k in 0 until 2) {
            val o = k * WL
            if (s.win[o] == 0.0) continue
            win[k * 4] = s.win[o + 2]
            win[k * 4 + 1] = clamp01(s.win[o + 4])
            win[k * 4 + 2] = s.win[o + 6]
            win[k * 4 + 3] = WINDOW
            winB[k * 4] = s.win[o + 7]
            winB[k * 4 + 1] = s.win[o + 8]
            winB[k * 4 + 2] = 0.3
        }
        val bump = emptyBump()
        for (k in 0 until 2) {
            val o = k * BL
            if (s.bump[o + 4] >= s.bump[o + 5]) continue
            bump[k * 4] = s.bump[o]
            bump[k * 4 + 1] = s.bump[o + 2] * bumpGate(s.bump[o + 4], s.bump[o + 5])
            bump[k * 4 + 2] = s.bump[o + 3]
            bump[k * 4 + 3] = s.bump[o + 1]
        }
        val wd = s.base + 4 * sin(s.idleP * 2) * idleW + WD_LEVEL * s.level
        return buildFrame(
            Params(
                (s.t / LOOP) % 1, s.waveP, Theme.AMP + Theme.AMP_LEVEL * s.level, wd, s.level, idleW, s.idleP, clamp01(s.think), 201 + 60 * sin(2 * PI * THINK_F * s.modeT),
                pul, win, winB, bump, 0.0, 0.3 + 0.7 * s.level, 1 + 0.2 * s.gain,
            ),
        )
    }

    /** the resting frame under Reduce Motion: a still band whose width follows the level; thinking is a thread */
    fun reducedFrame(mode: Int, level: Double, pulse: Double): PrvFrame {
        val wd = (if (mode == 2) W_THREAD + 2 else if (mode == 3) W_SPEAK else W_IDLE) + WD_LEVEL * level
        val idleW = if (mode == 2) 0.0 else 1.0
        return buildFrame(
            Params(
                0.0, Theme.WAVE_P0, Theme.AMP, wd, if (mode == 2) 0.0 else level, idleW, Theme.TWIST_PHASE, 0.0, 201.0, emptyPulses(), emptyWin(), DoubleArray(8), emptyBump(),
                if (mode == 1) 1.0 else if (mode == 3) -1.0 else 0.0, 0.0, if (mode == 2) 0.75 + 0.25 * pulse else 1.0,
            ),
        )
    }

    private fun lerpArr(x: DoubleArray, y: DoubleArray, w: Double) = DoubleArray(x.size) { x[it] + (y[it] - x[it]) * w }

    /** blend two frames (the 600 ms ease back to the loop's rest); b's phases are brought to a's whole cycle first */
    fun blendFrame(a: PrvFrame, b: PrvFrame, w: Double): PrvFrame {
        val waveB = b.line[3] + round(a.line[3] - b.line[3])
        val pul = a.pul.copyOf()
        for (i in 0 until 8) pul[i * 4 + 1] *= 1 - w
        val win = a.win.copyOf()
        for (k in 0 until 2) win[k * 4 + 1] *= 1 - w
        val bump = a.bump.copyOf()
        for (k in 0 until 2) bump[k * 4 + 1] *= 1 - w
        val idleB = b.twA[2] + 2 * PI * round((a.twA[2] - b.twA[2]) / (2 * PI))
        return PrvFrame(
            b.phase, doubleArrayOf(mix(a.line[0], b.line[0], w), mix(a.line[1], b.line[1], w), b.line[2], mix(a.line[3], waveB, w)), lerpArr(a.sheet, b.sheet, w),
            doubleArrayOf(mix(a.twA[0], b.twA[0], w), b.twA[1], mix(a.twA[2], idleB, w), mix(a.twA[3], b.twA[3], w)), lerpArr(a.twB, b.twB, w),
            doubleArrayOf(mix(a.think[0], b.think[0], w), mix(a.think[1], b.think[1], w), a.think[2], 0.0), pul, win, a.winB, bump, lerpArr(a.opt, b.opt, w), lerpArr(a.extra, b.extra, w),
        )
    }

    /** the colour ramp at u (0 .. 1 along the ribbon): the twin of rampAt() in the shader */
    fun rampMix(ramp: Array<FloatArray>, u: Double): FloatArray {
        val a = smooth(0.3, 0.47, u)
        val b = smooth(0.49, 0.66, u)
        fun ch(i: Int): Float {
            val x = ramp[0][i] + (ramp[1][i] - ramp[0][i]) * a
            return (x + (ramp[2][i] - x) * b).toFloat()
        }
        return floatArrayOf(ch(0), ch(1), ch(2))
    }
}
