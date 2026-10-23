package com.ngocdevv.nativecook.blocks.media.filmboxunboxing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The screen as a function of time. Every moving part reads [sceneAt], which turns the clock and
 * the moments of the five actions (unbox, load, shoot, retake, reset) into opacities, angles and
 * scales; touches and the autoPlay loop only write those moments.
 */

/** Seconds of one autoPlay loop. */
internal const val LOOP = 10.0
/** Seconds without a touch, once the screen has settled, before it goes back to rest and plays again. */
internal const val USER_HOLD = 3.0

// The autoPlay loop, in seconds from its start: rest, drag the knob, open, read the card, load,
// shoot, look at the photo, then fade through black to the closed box.
private const val DRAG_AT = 1.4
private const val DRAG_FOR = 0.8
private const val UNBOX_AT = DRAG_AT + DRAG_FOR
private const val LOAD_AT = 6.6
private const val SHOOT_AT = 7.7
private const val RESET_AT = 9.6
/** Each half of the fade through black that puts the box back. */
internal const val RESET_HALF = 0.2

// The idle box bobs and sways on a 2.5 s period, a quarter of LOOP, so every loop sees the same sway.
private const val BOB = 2.5
private const val BOB_Y = 4.0
private const val BOB_ROLL = 1.5
private const val BOB_YAW = 3.0

// Opening, in seconds from the release of the knob.
private const val PILL_OUT = 0.2
private const val SPINNER_AT = 0.1
private const val SPINNER_IN = 0.2
private const val SPINNER_OUT = 0.2
/** Degrees the box tips towards the viewer as it opens, on a spring. */
private const val TILT = 16.0
private const val TILT_RESPONSE = 0.4
private const val TILT_DAMPING = 0.6
private const val FLAPS_AT = 0.08
private const val FLAPS_FOR = 0.5
/** Degrees the flaps swing open, past upright. */
private const val FLAP_OPEN = 110.0
private const val GLOW_AT = 0.35
private const val GLOW_FOR = 0.35
/** The streak of light that crosses the box. */
private const val SWEEP_AT = 0.3
private const val SWEEP_FOR = 0.45
/** The view dives into the open box. */
private const val DIVE_AT = 0.7
private const val DIVE_FOR = 0.5
internal const val DIVE_SCALE = 4f
private const val BOX_OUT_AT = 0.95
private const val BOX_OUT = 0.25
private const val HEADER_OUT_AT = 0.7
private const val HEADER_OUT = 0.25
private const val NAME_AT = 1.15
private const val NAME_IN = 0.25
private const val REVEAL_AT = 1.7

// The card, in seconds from REVEAL_AT.
private const val CANISTER_IN = 0.25
private const val CANISTER_FROM = 0.6
private const val CANISTER_RESPONSE = 0.5
private const val CANISTER_DAMPING = 0.7
/** The name's description, the four rows and the button come in one after the other. */
internal const val ROWS = 6
private const val ROWS_AT = 0.1
private const val ROW_STAGGER = 0.06
private const val ROW_IN = 0.3
internal const val ROW_LIFT = 8f
/** The canister turns once about its long axis, then rests, every SPIN_PERIOD. */
private const val SPIN_AT = 0.4
private const val SPIN_TURN = 2.2
private const val SPIN_PERIOD = 3.0
private const val OPEN_SETTLED = REVEAL_AT + 0.7

// Loading, in seconds from the tap on Load film.
private const val LOAD_GREY = 0.1
private const val CARD_OUT_AT = 0.05
private const val CARD_OUT = 0.25
private const val FLY = 0.55
/** The canister's size at the top of the camera. */
internal const val SMALL = 0.3f
private const val CAMERA_IN_AT = 0.25
private const val CAMERA_IN = 0.3
internal const val CAMERA_LIFT = 24f
private const val LOAD_SETTLED = 0.6

// Shooting, in seconds from the tap on the shutter, and retaking.
private const val PRESS = 0.15
private const val PRESS_SCALE = 0.9
private const val BUTTON_PRESS_SCALE = 0.94
private const val FLASH = 0.12
private const val FLASH_ALPHA = 0.3
private const val PHOTO_AT = 0.05
private const val PHOTO_IN = 0.4
private const val PHOTO_FROM = 1.1
private const val CAMERA_ROW_OUT_AT = 0.1
private const val CAMERA_ROW_OUT = 0.2
private const val PHOTO_ROW_IN_AT = 0.2
private const val PHOTO_ROW_IN = 0.25
private const val SHOT_SETTLED = 0.5
private const val PHOTO_OUT = 0.25
private const val RETAKE_SETTLED = 0.4

private const val TAU_D = PI * 2

/** When each action happened, in seconds of the screen's clock; -1 when it has not. */
internal data class FilmEvents(
    val unbox: Double = -1.0,
    val load: Double = -1.0,
    val shot: Double = -1.0,
    val retake: Double = -1.0,
    val reset: Double = -1.0,
)

/** What the controls on screen accept. */
internal enum class FilmStage { Busy, Box, Film, Camera, Photo }

/** Everything on screen at one moment. */
internal class FilmScene(
    val stage: FilmStage,
    val header: Float,
    /** The pill's ring, knob and label. */
    val pill: Float,
    val spinner: Float,
    /** Seconds the spinner has turned. */
    val spinnerTime: Float,
    val boxAlpha: Float,
    /** The box's sway, in degrees and dp. */
    val yaw: Float,
    val roll: Float,
    val lift: Float,
    val tilt: Float,
    val flap: Float,
    val glow: Float,
    /** Where the streak of light is across the box, 0 to 1; outside that range it is not drawn. */
    val sweep: Float,
    val dive: Float,
    val name: Float,
    val canister: Float,
    val canisterScale: Float,
    /** 0 on the card, 1 at the top of the camera. */
    val fly: Float,
    val spin: Float,
    val rows: FloatArray,
    val cardOut: Float,
    val grey: Float,
    val loadScale: Float,
    /** Seconds since the card came in, which the expiry counts down from. */
    val shown: Double,
    val camera: Float,
    val cameraRow: Float,
    val photoRow: Float,
    val shutter: Float,
    val flash: Float,
    val photo: Float,
    val photoScale: Float,
    val overlay: Float,
)

internal fun clamp01(x: Double) = min(max(x, 0.0), 1.0)

/** 0 before [from], 1 after [to], a smoothstep between. */
internal fun smooth(t: Double, from: Double, to: Double): Double {
    val x = clamp01((t - from) / (to - from))
    return x * x * (3 - 2 * x)
}

private fun easeOut(x: Double): Double {
    val k = 1 - clamp01(x)
    return 1 - k * k * k
}

private fun easeIn(x: Double): Double {
    val k = clamp01(x)
    return k * k * k
}

private fun easeInOut(x: Double): Double {
    val k = clamp01(x)
    return if (k < 0.5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2
}

/**
 * A spring from 0 to 1 after [t] seconds, as SwiftUI defines one: [response] is the period of the
 * undamped spring, [damping] the fraction of critical damping (below 1).
 */
internal fun springAt(t: Double, response: Double, damping: Double): Double {
    if (t <= 0) return 0.0
    val w0 = TAU_D / response
    val wd = w0 * sqrt(1 - damping * damping)
    val decay = exp(-damping * w0 * t)
    return 1 - decay * (cos(wd * t) + (damping * w0 / wd) * sin(wd * t))
}

/** Up and back down once over [length] seconds. */
private fun pulse(t: Double, length: Double) = if (t < 0 || t > length) 0.0 else sin(PI * t / length)

/** The canister's turn, [t] seconds after the card came in. */
private fun spinAt(t: Double, still: Boolean): Double {
    if (still || t < SPIN_AT) return 0.0
    return easeInOut(((t - SPIN_AT) % SPIN_PERIOD) / SPIN_TURN) * TAU_D
}

/** Everything on screen at [now], from the moments of the actions. */
internal fun sceneAt(now: Double, ev: FilmEvents, still: Boolean): FilmScene {
    val resetting = if (ev.reset >= 0) now - ev.reset else -1.0
    // Past the darkest moment of the fade the box is back, closed.
    val cleared = resetting >= RESET_HALF
    val overlay = when {
        resetting >= 0 && resetting < RESET_HALF -> smooth(resetting, 0.0, RESET_HALF)
        resetting >= RESET_HALF -> 1 - smooth(resetting, RESET_HALF, RESET_HALF * 2)
        else -> 0.0
    }

    val o = if (!cleared && ev.unbox >= 0) now - ev.unbox else -1.0
    val l = if (o >= 0 && ev.load > ev.unbox) now - ev.load else -1.0
    val shotOn = l >= 0 && ev.shot > ev.load && ev.shot > ev.retake
    val s = if (shotOn) now - ev.shot else -1.0
    val r = if (l >= 0 && ev.retake > ev.load && ev.retake > ev.shot) now - ev.retake else -1.0
    val v = o - REVEAL_AT

    val phase = now / BOB * TAU_D
    val sway = if (still) 0.0 else 1.0

    val rows = FloatArray(ROWS) { i ->
        val at = ROWS_AT + i * ROW_STAGGER
        (if (o < 0) 0.0 else if (still) smooth(v, ROWS_AT, ROWS_AT + ROW_IN) else smooth(v, at, at + ROW_IN)).toFloat()
    }

    var spin = spinAt(v, still)
    var fly = 0.0
    var canister = if (o < 0) 0.0 else smooth(v, 0.0, CANISTER_IN)
    if (l >= 0) {
        // The canister finishes its turn on the way up, the short way round.
        val from = spinAt(ev.load - ev.unbox - REVEAL_AT, still)
        val to = if (from > PI) TAU_D else 0.0
        if (still) {
            fly = if (l >= FLY / 2) 1.0 else 0.0
            canister = 1 - smooth(l, 0.0, FLY / 2) + smooth(l, FLY / 2, FLY)
        } else {
            fly = easeInOut(l / FLY)
        }
        spin = from + (to - from) * fly
    }

    val stage = when {
        resetting >= 0 && resetting < RESET_HALF * 2 -> FilmStage.Busy
        o < 0 -> FilmStage.Box
        l < 0 -> if (v >= ROWS_AT + ROW_IN) FilmStage.Film else FilmStage.Busy
        l < LOAD_SETTLED -> FilmStage.Busy
        shotOn -> if (s >= 0.3) FilmStage.Photo else FilmStage.Busy
        r >= 0 && r < 0.3 -> FilmStage.Busy
        else -> FilmStage.Camera
    }

    return FilmScene(
        stage = stage,
        header = (if (o < 0) 1.0 else 1 - smooth(o, HEADER_OUT_AT, HEADER_OUT_AT + HEADER_OUT)).toFloat(),
        pill = (if (o < 0) 1.0 else 1 - smooth(o, 0.0, PILL_OUT)).toFloat(),
        spinner = (if (o < 0) 0.0 else smooth(o, SPINNER_AT, SPINNER_AT + SPINNER_IN) * (1 - smooth(o, REVEAL_AT, REVEAL_AT + SPINNER_OUT))).toFloat(),
        spinnerTime = max(0.0, o).toFloat(),
        boxAlpha = (if (o < 0) 1.0 else if (still) 1 - smooth(o, 0.3, 0.6) else 1 - smooth(o, BOX_OUT_AT, BOX_OUT_AT + BOX_OUT)).toFloat(),
        yaw = (BOB_YAW * cos(phase) * sway).toFloat(),
        roll = (BOB_ROLL * sin(phase) * sway).toFloat(),
        lift = (-BOB_Y * sin(phase) * sway).toFloat(),
        tilt = (if (o < 0 || still) 0.0 else TILT * springAt(o, TILT_RESPONSE, TILT_DAMPING)).toFloat(),
        flap = (if (o < 0 || still) 0.0 else FLAP_OPEN * easeOut((o - FLAPS_AT) / FLAPS_FOR)).toFloat(),
        glow = (if (o < 0 || still) 0.0 else smooth(o, GLOW_AT, GLOW_AT + GLOW_FOR)).toFloat(),
        sweep = (if (o < 0 || still) -1.0 else (o - SWEEP_AT) / SWEEP_FOR).toFloat(),
        dive = (if (o < 0 || still) 0.0 else easeIn((o - DIVE_AT) / DIVE_FOR)).toFloat(),
        name = (if (o < 0) 0.0 else smooth(o, NAME_AT, NAME_AT + NAME_IN)).toFloat(),
        canister = canister.toFloat(),
        canisterScale = (if (still || o < 0) 1.0 else CANISTER_FROM + (1 - CANISTER_FROM) * springAt(v, CANISTER_RESPONSE, CANISTER_DAMPING)).toFloat(),
        fly = fly.toFloat(),
        spin = spin.toFloat(),
        rows = rows,
        cardOut = (if (l < 0) 0.0 else smooth(l, CARD_OUT_AT, CARD_OUT_AT + CARD_OUT)).toFloat(),
        grey = (if (l < 0) 0.0 else smooth(l, 0.0, LOAD_GREY)).toFloat(),
        loadScale = (if (l < 0 || still) 1.0 else 1 - (1 - BUTTON_PRESS_SCALE) * pulse(l, PRESS)).toFloat(),
        shown = max(0.0, v),
        camera = (if (l < 0) 0.0 else smooth(l, CAMERA_IN_AT, CAMERA_IN_AT + CAMERA_IN)).toFloat(),
        cameraRow = (if (shotOn) 1 - smooth(s, CAMERA_ROW_OUT_AT, CAMERA_ROW_OUT_AT + CAMERA_ROW_OUT) else if (r >= 0) smooth(r, 0.1, 0.35) else 1.0).toFloat(),
        photoRow = (if (shotOn) smooth(s, PHOTO_ROW_IN_AT, PHOTO_ROW_IN_AT + PHOTO_ROW_IN) else if (r >= 0) 1 - smooth(r, 0.0, 0.2) else 0.0).toFloat(),
        shutter = (if (shotOn && !still) 1 - (1 - PRESS_SCALE) * pulse(s, PRESS) else 1.0).toFloat(),
        flash = (if (shotOn && s < FLASH) FLASH_ALPHA * (1 - s / FLASH) else 0.0).toFloat(),
        photo = (if (shotOn) smooth(s, PHOTO_AT, PHOTO_AT + PHOTO_IN) else if (r >= 0) 1 - smooth(r, 0.0, PHOTO_OUT) else 0.0).toFloat(),
        photoScale = (if (shotOn && !still) PHOTO_FROM - (PHOTO_FROM - 1) * easeOut((s - PHOTO_AT) / PHOTO_IN) else 1.0).toFloat(),
        overlay = overlay.toFloat(),
    )
}

/** When the last action's motion ends; the screen counts its idle time from there. */
internal fun settledAt(ev: FilmEvents) = maxOf(
    if (ev.unbox < 0) -1.0 else ev.unbox + OPEN_SETTLED,
    if (ev.load < 0) -1.0 else ev.load + LOAD_SETTLED,
    if (ev.shot < 0) -1.0 else ev.shot + SHOT_SETTLED,
    if (ev.retake < 0) -1.0 else ev.retake + RETAKE_SETTLED,
    if (ev.reset < 0) -1.0 else ev.reset + RESET_HALF * 2,
)

/** The actions of the autoPlay loop [local] seconds into a loop that started at [base]. */
internal fun loopEvents(local: Double, base: Double) = FilmEvents(
    unbox = if (local >= UNBOX_AT) base + UNBOX_AT else -1.0,
    load = if (local >= LOAD_AT) base + LOAD_AT else -1.0,
    shot = if (local >= SHOOT_AT) base + SHOOT_AT else -1.0,
    retake = -1.0,
    reset = if (local >= RESET_AT) base + RESET_AT else -1.0,
)

/** Where the loop's finger holds the knob, 0 to 1; under reduced motion it jumps. */
internal fun loopDrag(local: Double, still: Boolean): Double {
    if (local < DRAG_AT || local >= RESET_AT + RESET_HALF) return 0.0
    if (still) return 1.0
    return easeInOut((local - DRAG_AT) / DRAG_FOR)
}

/** The loop's start for a clock reading of [now], when the loop began at [origin]. */
internal fun loopBase(now: Double, origin: Double) = origin + floor((now - origin) / LOOP) * LOOP

/** "17h 21m 58s". */
internal fun countdown(seconds: Int): String {
    val left = max(0, seconds)
    return "%dh %02dm %02ds".format(left / 3600, left % 3600 / 60, left % 60)
}
