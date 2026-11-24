package com.ngocdevv.nativecook.blocks.pickers.mixingdeskfader

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * mixerScene.ts and mixerMotion.ts: the scene's numbers, the film as pure functions of t, the sound and the projection.
 * The faders, knobs and mutes are a pure function of t and the meters are periodic signals of t, so t = LOOP gives the
 * same frame as t = 0.
 *
 *  0.00 – 0.95  rest: the Intro preset (music up), meters moving
 *  0.95 – 1.95  a finger rides Music down to 0.40
 *  2.35 – 3.25  a finger brings Host up to 0 dB, where it settles exactly
 *  3.90         SFX is muted: its button sinks, its light and its meter go out (0.16 s)
 *  5.00         the Interview preset is tapped: all four faders and knobs glide there (1.1 s each, 70 ms apart)
 *  7.40         Intro is tapped: everything glides back, SFX comes on again
 *  8.71 – 10.0  rest; last frame = first frame. The camera leans in a little (0.6 – 2.4) and back (8.0 – 9.8).
 *
 * A pose is 12 numbers: faders 0..3 (0..1 of their travel), knobs 4..7 (0..1 of their sweep), mutes 8..11 (1 muted).
 */
internal object MixerMotion {
    const val LOOP = 10.0
    const val POSE = 12
    const val CHANNELS = 4

    // ------------------------------------------------------------ scene (glTF space: y up, metres, faces +z)

    /** The Deck node: its origin (the plate's front edge, centre) and its tilt back about x (rad). */
    private val DECK_ORIGIN = doubleArrayOf(0.0, 0.032, 0.099288)
    private const val DECK_SLOPE = 0.119429

    /** Each fader's cap at v = 0 (deck frame); it slides towards the back (-z) by v x TRAVEL. Its grip is CAP_Y up. */
    val FADER_BASE = arrayOf(
        floatArrayOf(-0.096f, 0f, -0.034f),
        floatArrayOf(-0.036f, 0f, -0.034f),
        floatArrayOf(0.024f, 0f, -0.034f),
        floatArrayOf(0.084f, 0f, -0.034f),
    )
    const val TRAVEL = 0.082f
    private const val CAP_Y = 0.011

    /** Mute buttons (deck frame); a pressed one sinks MUTE_TRAVEL. */
    val MUTE_POS = arrayOf(
        floatArrayOf(-0.09f, 0f, -0.142f),
        floatArrayOf(-0.03f, 0f, -0.142f),
        floatArrayOf(0.03f, 0f, -0.142f),
        floatArrayOf(0.09f, 0f, -0.142f),
    )
    const val MUTE_TRAVEL = 0.0022f

    /** Gain knobs (deck frame); they turn about y. */
    val KNOB_POS = arrayOf(
        floatArrayOf(-0.09f, 0f, -0.173f),
        floatArrayOf(-0.03f, 0f, -0.173f),
        floatArrayOf(0.03f, 0f, -0.173f),
        floatArrayOf(0.09f, 0f, -0.173f),
    )
    private const val KNOB_ZERO_DEG = 135.0
    private const val KNOB_SPAN_DEG = 270.0

    /** Each meter has LEDS lenses, bottom up; the last two are amber and red. Emissive colours are linear. */
    const val LEDS = 8
    val LED_GREEN = floatArrayOf(0.1046f, 0.8228f, 0.2384f)
    val LED_AMBER = floatArrayOf(1.0f, 0.4564f, 0.0452f)
    val LED_RED = floatArrayOf(1.0f, 0.0802f, 0.0513f)
    const val LED_ON = 5f
    val LAMP_RGB = floatArrayOf(0.5776f, 1.0f, 0.3515f)
    const val LAMP_ON = 3.2f

    /** Camera: it orbits the target at DIST (az + = to the viewer's right). FRAME_W x FRAME_H metres stay in frame. */
    val TARGET = doubleArrayOf(0.0, 0.09, 0.0)
    private const val DIST = 0.78
    private const val AZ_REST = -8.0
    private const val EL_REST = 68.0
    private const val AZ_LEAN = -3.0
    private const val EL_LEAN = 64.0
    private const val ZOOM_LEAN = 0.02
    private const val FRAME_W = 0.35
    private const val FRAME_H = 0.2
    const val CAMERA_NEAR = 0.04
    const val CAMERA_FAR = 30.0

    /** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
    const val IBL_INTENSITY = 24_000f
    val KEY_DIRECTION = floatArrayOf(-0.35f, -0.85f, -0.4f)
    const val KEY_LUX = 60_000f
    const val KEY_KELVIN = 5000f
    val FILL_DIRECTION = floatArrayOf(0.7f, -0.4f, -0.55f)
    const val FILL_LUX = 12_000f
    const val FILL_KELVIN = 7200f
    const val EXPOSURE = 0f
    const val BLOOM_STRENGTH = 0.1f

    /** Palette (sRGB): a dark studio. */
    const val BACKDROP = 0x24211E
    const val PANEL = 0x1B1A19
    const val CARD = 0x262523
    const val INK = 0xF3F0EA
    const val MUTE = 0x9D9890
    const val REC = 0xFF5A4A
    val CHANNEL_COLOURS = intArrayOf(0xFF7A59, 0xF5C04A, 0x4FC3B0, 0xA68BFF)

    /** Copy: an invented podcast recording app. */
    const val APP = "Booth"
    const val STUDIO = "Studio B · Live"
    const val EPISODE = "Episode 42"
    val NAMES = listOf("Host", "Guest", "Music", "SFX")
    val PRESET_NAMES = listOf("Intro", "Interview", "Outro")

    // ------------------------------------------------------------ the film's numbers

    /** Intro, Interview, Outro: faders, knobs, mutes. */
    val PRESETS = arrayOf(
        doubleArrayOf(0.62, 0.62, 0.82, 0.5, 0.55, 0.5, 0.45, 0.4, 0.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.75, 0.72, 0.24, 0.36, 0.62, 0.6, 0.35, 0.42, 0.0, 0.0, 0.0, 1.0),
        doubleArrayOf(0.55, 0.55, 0.88, 0.6, 0.5, 0.5, 0.6, 0.5, 0.0, 0.0, 0.0, 0.0),
    )

    /** The film's rides: channel, to, start, end. */
    private val RIDE_CHANNEL = intArrayOf(2, 0)
    private val RIDE_TO = doubleArrayOf(0.4, 0.75)
    private val RIDE_START = doubleArrayOf(0.95, 2.35)
    private val RIDE_END = doubleArrayOf(1.95, 3.25)

    /** The film's mute: channel and time. */
    private const val MUTE_TAP_CHANNEL = 3
    private const val MUTE_TAP_AT = 3.9

    /** The film's preset recalls: preset and time. */
    private val RECALL_PRESET = intArrayOf(1, 0)
    private val RECALL_AT = doubleArrayOf(5.0, 7.4)

    /** A recall: each fader glides this long (s), this much after the one on its left. A mute moves in MUTE_MOVE. */
    const val GLIDE = 1.1
    const val STAGGER = 0.07
    const val MUTE_MOVE = 0.16

    /** The camera's lean. */
    private val LEAN = doubleArrayOf(0.6, 2.4, 8.0, 9.8)

    /** The fader law: travel (0..1) to dB, piecewise linear; 0 dB sits at SNAP_V. */
    private val V_TAB = doubleArrayOf(0.0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0)
    private val DB_TAB = doubleArrayOf(-60.0, -40.0, -30.0, -20.0, -10.0, -5.0, 0.0, 5.0, 10.0)
    const val SNAP_V = 0.75
    const val SNAP_ZONE = 0.03

    /** How far a fader can be pulled past an end (of its travel) before it stops. */
    private const val OVERPULL = 0.035

    /** The meters: a channel's signal offset (dB), and the dB the meter's bottom and top stand for. */
    private val SIGNAL_DB = doubleArrayOf(0.0, -2.0, -6.0, -4.0)
    private const val METER_FLOOR = -42.0
    private const val METER_SPAN = 48.0

    /** SFX hits (s) and how fast they decay (s). */
    private val HITS = doubleArrayOf(1.4, 4.6, 6.9, 8.3)
    private const val HIT_DECAY = 0.35

    // ------------------------------------------------------------ helpers

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x

    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))

    fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k

    fun smooth(x: Double): Double = x * x * (3 - 2 * x)

    private fun easeInOut(x: Double): Double = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    // ------------------------------------------------------------ the film

    /** The film's pose at t into out (12 numbers). */
    fun filmPose(t: Double, out: DoubleArray) {
        val start = PRESETS[0]
        for (i in 0 until POSE) out[i] = start[i]
        for (r in RIDE_CHANNEL.indices) {
            val c = RIDE_CHANNEL[r]
            out[c] = lerp(out[c], RIDE_TO[r], easeInOut(seg(t, RIDE_START[r], RIDE_END[r])))
        }
        val mc = MUTE_TAP_CHANNEL
        out[8 + mc] = lerp(out[8 + mc], 1.0, smooth(seg(t, MUTE_TAP_AT, MUTE_TAP_AT + MUTE_MOVE)))
        for (r in RECALL_PRESET.indices) {
            val p = PRESETS[RECALL_PRESET[r]]
            val at = RECALL_AT[r]
            for (c in 0 until CHANNELS) {
                val k = easeInOut(seg(t, at + c * STAGGER, at + c * STAGGER + GLIDE))
                out[c] = lerp(out[c], p[c], k)
                out[4 + c] = lerp(out[4 + c], p[4 + c], k)
                out[8 + c] = lerp(out[8 + c], p[8 + c], smooth(seg(t, at + 0.1, at + 0.1 + MUTE_MOVE)))
            }
        }
    }

    /** The preset the film's segmented control shows. */
    fun filmPreset(t: Double): Int {
        var p = 0
        for (r in RECALL_PRESET.indices) {
            if (t >= RECALL_AT[r]) p = RECALL_PRESET[r]
        }
        return p
    }

    /** The film's finger on the desk: out = [opacity, pressed, channel, kind (0 fader, 1 mute)]. */
    fun deskGhost(t: Double, out: DoubleArray) {
        out[0] = 0.0
        out[1] = 0.0
        out[2] = 0.0
        out[3] = 0.0
        for (r in RIDE_CHANNEL.indices) {
            val a = RIDE_START[r]
            val b = RIDE_END[r]
            if (t < a - 0.35 || t > b + 0.35) continue
            out[0] = seg(t, a - 0.32, a - 0.08) * (1 - seg(t, b + 0.1, b + 0.32))
            out[1] = seg(t, a - 0.08, a) * (1 - seg(t, b, b + 0.12))
            out[2] = RIDE_CHANNEL[r].toDouble()
            return
        }
        val at = MUTE_TAP_AT
        if (t > at - 0.35 && t < at + 0.35) {
            out[0] = seg(t, at - 0.32, at - 0.1) * (1 - seg(t, at + 0.1, at + 0.32))
            out[1] = seg(t, at - 0.1, at) * (1 - seg(t, at + 0.04, at + 0.18))
            out[2] = MUTE_TAP_CHANNEL.toDouble()
            out[3] = 1.0
        }
    }

    /** The film's finger on the presets: out = [opacity, pressed, preset]. */
    fun presetGhost(t: Double, out: DoubleArray) {
        var best = 0
        var dist = 1e9
        for (r in RECALL_AT.indices) {
            val d = abs(t - RECALL_AT[r])
            if (d < dist) {
                dist = d
                best = r
            }
        }
        val at = RECALL_AT[best]
        out[0] = seg(t, at - 0.32, at - 0.1) * (1 - seg(t, at + 0.1, at + 0.32))
        out[1] = seg(t, at - 0.1, at) * (1 - seg(t, at + 0.04, at + 0.18))
        out[2] = RECALL_PRESET[best].toDouble()
    }

    // ------------------------------------------------------------ sound

    /** A fader's travel to dB. */
    private fun faderDb(v: Double): Double {
        if (v <= 0) return DB_TAB[0]
        for (i in 0 until V_TAB.size - 1) {
            if (v <= V_TAB[i + 1]) return lerp(DB_TAB[i], DB_TAB[i + 1], (v - V_TAB[i]) / (V_TAB[i + 1] - V_TAB[i]))
        }
        return DB_TAB[DB_TAB.size - 1]
    }

    /** What the panel says for a channel. */
    fun dbLabel(v: Double, muted: Double): String {
        if (muted > 0.5) return "Muted"
        if (v < 0.01) return "−∞ dB"
        val db = faderDb(v)
        // JavaScript's Math.round: halves go up
        val r = jsRound(db * 10) / 10
        if (r == 0.0) return "0.0 dB"
        return (if (r > 0) "+" else "−") + String.format(java.util.Locale.US, "%.1f", abs(r)) + " dB"
    }

    /** Channel c's signal (0..1) at signal time s: two people taking turns, music on the beat, effects as hits. */
    private fun signalAt(c: Int, s: Double): Double {
        val w = 2 * PI / LOOP
        if (c <= 1) {
            val turn = 0.5 + 0.5 * sin(w * 2 * s)
            val mine = if (c == 0) turn else 1 - turn
            val syll = 0.5 + 0.3 * sin(w * (37 + 6 * c) * s + c * 1.7) + 0.2 * sin(w * (61 + 4 * c) * s + c * 0.6)
            return (0.25 + 0.75 * mine) * syll
        }
        if (c == 2) {
            val beat = 0.5 + 0.5 * cos(w * 20 * s)
            return 0.55 + 0.33 * beat * beat * beat * beat + 0.1 * sin(w * 7 * s)
        }
        var e = 0.08
        for (h in HITS) {
            // JavaScript's % keeps the sign of the dividend; s is never far below 0 here
            val d = (s - h + LOOP) % LOOP
            e = max(e, exp(-d / HIT_DECAY))
        }
        return e
    }

    /** Channel c's meter (0..1 of its LEDs) for its fader, knob and mute. */
    fun meterAt(c: Int, s: Double, fader: Double, knob: Double, mute: Double): Double {
        val db = -40 + 40 * signalAt(c, s) + SIGNAL_DB[c] + faderDb(fader) + (knob - 0.5) * 12
        return clamp01((db - METER_FLOOR) / METER_SPAN) * (1 - mute)
    }

    /** LED i of a meter, 0..1 (the top one partly lit as the level crosses it). */
    fun ledAt(level: Double, i: Int): Double = clamp01(level * LEDS - i)

    /** A knob's turn (deg, about the deck's up axis) for its value. */
    fun knobDeg(k: Double): Double = KNOB_ZERO_DEG - k * KNOB_SPAN_DEG

    // ------------------------------------------------------------ a finger on a fader

    /** The 0 dB detent: inside SNAP_ZONE the cap is drawn towards SNAP_V. */
    private fun magnet(v: Double): Double {
        val d = v - SNAP_V
        val m = abs(d)
        if (m >= SNAP_ZONE) return v
        return SNAP_V + d * (m / SNAP_ZONE) * (m / SNAP_ZONE)
    }

    /** A dragged fader's value: rubber-banded past either end, then the detent. */
    fun faderFromDrag(raw: Double): Double {
        if (raw > 1) {
            val o = raw - 1
            return 1 + o * OVERPULL / (o + OVERPULL)
        }
        if (raw < 0) {
            val o = -raw
            return -(o * OVERPULL) / (o + OVERPULL)
        }
        return magnet(raw)
    }

    // ------------------------------------------------------------ camera and projection

    /** How far the camera leans in, 0..1. */
    fun leanAt(t: Double): Double = smooth(seg(t, LEAN[0], LEAN[1])) * (1 - smooth(seg(t, LEAN[2], LEAN[3])))

    /** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H in a stage of this aspect. */
    fun tanHalfFovY(aspect: Double): Double = max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST

    /** The camera's eye for a lean k: out = [x, y, z]. It looks at TARGET. */
    fun cameraAt(k: Double, out: DoubleArray) {
        val az = lerp(AZ_REST, AZ_LEAN, k) * PI / 180
        val el = lerp(EL_REST, EL_LEAN, k) * PI / 180
        val d = DIST * (1 - ZOOM_LEAN * k)
        out[0] = TARGET[0] + d * cos(el) * sin(az)
        out[1] = TARGET[1] + d * sin(el)
        out[2] = TARGET[2] + d * cos(el) * cos(az)
    }

    /** A point in the deck's frame to world space: out = [x, y, z]. */
    private fun deckToWorld(x: Double, y: Double, z: Double, out: DoubleArray) {
        val c = cos(DECK_SLOPE)
        val s = sin(DECK_SLOPE)
        out[0] = DECK_ORIGIN[0] + x
        out[1] = DECK_ORIGIN[1] + y * c - z * s
        out[2] = DECK_ORIGIN[2] + y * s + z * c
    }

    private val eye = DoubleArray(3)
    private val world = DoubleArray(3)

    /** A world point to stage points (dp) for a lean k: out = [x, y]. Main thread only (shared scratch arrays). */
    private fun project(x: Double, y: Double, z: Double, k: Double, width: Double, height: Double, out: DoubleArray) {
        cameraAt(k, eye)
        var fx = TARGET[0] - eye[0]
        var fy = TARGET[1] - eye[1]
        var fz = TARGET[2] - eye[2]
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fl
        fy /= fl
        fz /= fl
        var rx = -fz
        var rz = fx
        val rl = sqrt(rx * rx + rz * rz)
        rx /= rl
        rz /= rl
        val ux = -rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy
        val px = x - eye[0]
        val py = y - eye[1]
        val pz = z - eye[2]
        val depth = px * fx + py * fy + pz * fz
        val aspect = width / height
        val tanY = tanHalfFovY(aspect)
        val ndcX = (px * rx + pz * rz) / (depth * tanY * aspect)
        val ndcY = (px * ux + py * uy + pz * uz) / (depth * tanY)
        out[0] = (ndcX * 0.5 + 0.5) * width
        out[1] = (0.5 - ndcY * 0.5) * height
    }

    /** Fader c's grip on screen at travel v: out = [x, y]. */
    fun capOnScreen(c: Int, v: Double, k: Double, width: Double, height: Double, out: DoubleArray) {
        val b = FADER_BASE[c]
        deckToWorld(b[0].toDouble(), CAP_Y, b[2] - v * TRAVEL, world)
        project(world[0], world[1], world[2], k, width, height, out)
    }

    /** Mute button c on screen: out = [x, y]. */
    fun muteOnScreen(c: Int, k: Double, width: Double, height: Double, out: DoubleArray) {
        val m = MUTE_POS[c]
        deckToWorld(m[0].toDouble(), 0.005, m[2].toDouble(), world)
        project(world[0], world[1], world[2], k, width, height, out)
    }

    /** Rounds as JavaScript's Math.round does. */
    fun jsRound(x: Double): Double = kotlin.math.floor(x + 0.5)
}
