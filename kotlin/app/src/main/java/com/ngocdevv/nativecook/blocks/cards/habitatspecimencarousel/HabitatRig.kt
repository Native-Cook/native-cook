package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A rig for flat cut-out art: no extra drawings, only regions of the one PNG that bend around
 * their own pivot (CREATURE_LIFE inverts the warp per pixel). Coordinates are pixels of the
 * 512x512 source art. Swapping in another mascot means another PNG and another one of these.
 */
internal class Sway(val degrees: Float, val cyclesPerLoop: Float, val phase: Float)

/** A twitch: degrees and the loop seconds where it fires. */
internal class Twitch(val degrees: Float, val at: FloatArray)

internal class Joint(
    val pivot: FloatArray,
    val center: FloatArray,
    val radius: Float,
    val sway: List<Sway>,
    val twitch: Twitch? = null,
)

/** A pose key for `flow`: seconds, value. */
internal class FlowKey(val seconds: Float, val value: Float)

/**
 * A joint's accent during the move: degrees, switched on and off over a window of the effect clock
 * (rise from, rise to, fall from, fall to), as a held bend (hz 0) or a beat (hz, phase).
 */
internal class Accent(val joint: Int, val degrees: Float, val window: FloatArray, val hz: Float, val phase: Float)

/** Acting around the signature move, in seconds of its effect clock (FX), read with `flow`. */
internal class Acting(val yaw: List<FlowKey>, val tilt: List<FlowKey>, val blinks: FloatArray, val accents: List<Accent>)

internal class Glow(val at: FloatArray, val radius: Float, val color: FloatArray)

/** The face, for a 2.5D head turn: its centre and radius, and how far its middle slides (art px) at a full turn. */
internal class Face(val at: FloatArray, val radius: Float, val shift: Float)

/** A drawn flame that is always drawn live (CREATURE_LIFE): its base, its height, and the ellipse that holds it. */
internal class Flame(val base: FloatArray, val height: Float, val at: FloatArray, val radius: FloatArray)

internal class Rig(
    /** Where the feet meet the ground; squash, lean and hops pivot here. */
    val feet: Float,
    val chest: FloatArray,
    /** Where the legs meet the body: above it the weight shifts sideways, below it the legs hang in the air. */
    val hips: Float,
    /** Joint 0 is always the head: it also tilts towards the finger and rights itself after a lean. */
    val joints: List<Joint>,
    /** Eye ellipses (cx, cy, rx, ry); a second eye with rx 0 is absent. */
    val eyes: List<FloatArray>,
    /** Loop seconds of every blink; two close together make a double blink. */
    val blinks: FloatArray,
    /** Where the signature move lights the body from, its reach and colour. */
    val glow: Glow,
    val face: Face,
    val flame: Flame?,
    val acting: Acting,
    /** Phase offset, so three creatures never breathe in step. */
    val phase: Float,
)

private fun f(vararg v: Float) = floatArrayOf(*v)
private fun keys(vararg v: Float): List<FlowKey> = List(v.size / 2) { FlowKey(v[it * 2], v[it * 2 + 1]) }

internal val RIGS: List<Rig> = listOf(
    // Charizard: wings beat out of step, the tail flame flickers on two periods.
    Rig(
        feet = 495f, chest = f(256f, 392f, 96f), hips = 440f, phase = 0f,
        joints = listOf(
            Joint(f(268f, 300f), f(245f, 150f), 88f, listOf(Sway(1.6f, 3f, 0.1f))),
            Joint(f(226f, 276f), f(95f, 245f), 122f, listOf(Sway(2.6f, 4f, 0f), Sway(0.9f, 7f, 0.3f))),
            Joint(f(292f, 280f), f(420f, 250f), 122f, listOf(Sway(-2.4f, 4f, 0.37f), Sway(-0.8f, 7f, 0.71f))),
            Joint(f(396f, 446f), f(452f, 372f), 62f, listOf(Sway(3.4f, 18f, 0.2f), Sway(2.0f, 12f, 0.55f))),
        ),
        eyes = listOf(f(239f, 136f, 9f, 5.5f), f(0f, 0f, 0f, 0f)),
        blinks = f(1.2f, 4.6f, 8.1f, 8.42f, 11.6f),
        glow = Glow(f(452f, 380f), 150f, f(1f, 0.55f, 0.2f)),
        face = Face(f(250f, 140f), 52f, 5f),
        flame = Flame(f(452f, 402f), 110f, f(451f, 345f), f(38f, 68f)),
        // Turns to its tail as the flame swells, watches the embers rise up and away, settles.
        acting = Acting(
            yaw = keys(0f, 0f, 0.18f, 0f, 0.55f, 0.85f, 1.15f, 0.85f, 1.7f, 0.2f, 2.3f, -0.25f, 3.2f, -0.1f, 3.9f, 0f),
            tilt = keys(0f, 0f, 0.5f, -2f, 1.2f, -3f, 2f, -4f, 2.8f, -1f, 3.9f, 0f),
            blinks = f(0.2f, 1.72f),
            // The wings beat as the flame swells, the far one a beat behind.
            accents = listOf(
                Accent(1, 4f, f(0.2f, 0.45f, 1.1f, 1.6f), 2.2f, 0f),
                Accent(2, -3.5f, f(0.2f, 0.45f, 1.1f, 1.6f), 2.2f, 0.12f),
            ),
        ),
    ),
    // Blastoise: a heavy body, so its head and arm move least and slowest.
    Rig(
        feet = 500f, chest = f(300f, 330f, 112f), hips = 405f, phase = 0.31f,
        joints = listOf(
            Joint(f(300f, 166f), f(320f, 104f), 78f, listOf(Sway(1.8f, 3f, 0.4f))),
            Joint(f(372f, 252f), f(432f, 292f), 62f, listOf(Sway(3.0f, 3f, 0.6f), Sway(0.8f, 6f, 0.1f))),
            Joint(f(112f, 262f), f(88f, 266f), 26f, listOf(Sway(-3.0f, 3f, 0.85f))),
            Joint(f(0f, 0f), f(0f, 0f), 0f, emptyList()),
        ),
        eyes = listOf(f(309f, 94f, 11f, 7f), f(0f, 0f, 0f, 0f)),
        blinks = f(2.1f, 5.6f, 5.95f, 9.4f, 12.6f),
        glow = Glow(f(409f, 196f), 140f, f(0.5f, 0.85f, 1f)),
        face = Face(f(318f, 100f), 58f, 6f),
        flame = null,
        // Braces towards the higher jet while both fire, then looks down at the spray landing.
        acting = Acting(
            yaw = keys(0f, 0f, 0.15f, 0f, 0.4f, -0.45f, 1.05f, -0.45f, 1.5f, 0.15f, 2.1f, 0.4f, 3f, 0.3f, 3.9f, 0f),
            tilt = keys(0f, 0f, 0.3f, 1.5f, 1f, 1.5f, 1.6f, 3f, 2.6f, 3f, 3.9f, 0f),
            blinks = f(0.4f, 1.5f),
            // Both arms brace against the recoil while the cannons fire.
            accents = listOf(
                Accent(1, 3f, f(0.2f, 0.32f, 1f, 1.4f), 0f, 0f),
                Accent(2, -3f, f(0.24f, 0.36f, 1f, 1.45f), 0f, 0f),
            ),
        ),
    ),
    // Bulbasaur: the bulb sways and its tip follows a beat later; one ear twitches now and then.
    Rig(
        feet = 492f, chest = f(292f, 420f, 122f), hips = 432f, phase = 0.62f,
        joints = listOf(
            Joint(f(258f, 432f), f(190f, 300f), 152f, listOf(Sway(1.3f, 3f, 0.15f))),
            Joint(f(330f, 236f), f(345f, 168f), 96f, listOf(Sway(2.6f, 3f, 0.3f))),
            Joint(f(350f, 162f), f(366f, 146f), 42f, listOf(Sway(3.2f, 3f, 0.45f))),
            Joint(f(116f, 240f), f(106f, 214f), 27f, listOf(Sway(1.5f, 6f, 0.2f)), Twitch(-9f, f(2.6f, 9.9f))),
        ),
        eyes = listOf(f(86f, 300f, 20f, 32f), f(251f, 308f, 40f, 29f)),
        blinks = f(0.4f, 3.9f, 7.3f, 10.6f, 10.94f, 12.9f),
        glow = Glow(f(377f, 152f), 190f, f(0.8f, 1f, 0.45f)),
        face = Face(f(172f, 300f), 132f, 9f),
        flame = null,
        // Looks up the beam as it fires, then down at the leaves as they fall and settle.
        acting = Acting(
            yaw = keys(0f, 0f, 0.3f, 0f, 0.6f, 0.5f, 1.3f, 0.5f, 1.8f, -0.3f, 2.7f, -0.45f, 3.4f, -0.2f, 3.9f, 0f),
            tilt = keys(0f, 0f, 0.5f, 1.5f, 1.3f, 2f, 2f, -1.5f, 2.8f, -2f, 3.9f, 0f),
            blinks = f(0.62f, 1.85f),
            // The bulb leans back as the beam pushes off it, and its crown flutters after.
            accents = listOf(
                Accent(1, -2f, f(0.5f, 0.62f, 1.2f, 1.6f), 0f, 0f),
                Accent(2, 3f, f(0.55f, 0.68f, 1.3f, 1.9f), 3f, 0.2f),
            ),
        ),
    ),
)

private const val TAU = (PI * 2).toFloat()
private const val RAD = (PI / 180).toFloat()

/**
 * Acting curve from rossark-motion-skill's engine: passes through every key with a continuous speed
 * and never overshoots one (monotone cubic), so a chain of poses never stops dead at a key.
 */
internal fun flow(t: Float, k: List<FlowKey>): Float {
    val n = k.size
    if (t <= k[0].seconds) return k[0].value
    if (t >= k[n - 1].seconds) return k[n - 1].value
    var i = 0
    while (k[i + 1].seconds < t) i++
    fun slope(j: Int) = (k[j + 1].value - k[j].value) / (k[j + 1].seconds - k[j].seconds)
    fun tangent(j: Int): Float {
        if (j == 0 || j == n - 1) return 0f
        val a = slope(j - 1)
        val b = slope(j)
        if (a * b <= 0) return 0f
        val w0 = 2 * (k[j + 1].seconds - k[j].seconds) + (k[j].seconds - k[j - 1].seconds)
        val w1 = (k[j + 1].seconds - k[j].seconds) + 2 * (k[j].seconds - k[j - 1].seconds)
        return (w0 + w1) / (w0 / a + w1 / b)
    }
    val h = k[i + 1].seconds - k[i].seconds
    val s = (t - k[i].seconds) / h
    val s2 = s * s
    val s3 = s2 * s
    return (2 * s3 - 3 * s2 + 1) * k[i].value + (s3 - 2 * s2 + s) * h * tangent(i) +
        (-2 * s3 + 3 * s2) * k[i + 1].value + (s3 - s2) * h * tangent(i + 1)
}

/** 0 -> 1 over [a, b], held, then back to 0 over [c, d] (SIGNATURE_EFFECT's `window`). */
private fun rises(x: Float, a: Float, b: Float, c: Float, d: Float): Float {
    val rise = max(0f, min(1f, (x - a) / (b - a)))
    val fall = max(0f, min(1f, (x - c) / (d - c)))
    return rise * rise * (3 - 2 * rise) * (1 - fall * fall * (3 - 2 * fall))
}

/** Seconds into a hold of the contented double blink. */
private val PET_BLINKS = f(0.22f, 0.56f)

/** Eyelid closure (0 open, 1 shut): close 110 ms, hold 40 ms, reopen 190 ms with an ease-out. */
internal fun lidAt(seconds: Float, blinks: FloatArray): Float {
    var lid = 0f
    for (blink in blinks) {
        var d = seconds - blink
        if (d < -LOOP / 2) d += LOOP
        if (d > LOOP / 2) d -= LOOP
        var v = 0f
        if (d >= 0 && d < 0.11f) { val u = d / 0.11f; v = u * u * (3 - 2 * u) }
        else if (d >= 0.11f && d < 0.15f) v = 1f
        else if (d >= 0.15f && d < 0.34f) { val u = 1 - (d - 0.15f) / 0.19f; v = u * u * u }
        lid = max(lid, v)
    }
    return lid
}

/** A twitch: a quick turn out (90 ms) and a slower return (260 ms). */
private fun twitchAt(seconds: Float, at: FloatArray): Float {
    var v = 0f
    for (time in at) {
        val d = seconds - time
        if (d >= 0 && d < 0.09f) v = max(v, sin((d / 0.09f) * PI.toFloat() / 2))
        else if (d >= 0.09f && d < 0.35f) { val u = 1 - (d - 0.09f) / 0.26f; v = max(v, u * u * (3 - 2 * u)) }
    }
    return v
}

/** Height of the incoming specimen's hop across a crossing, as a fraction of its size; it lands at f = 0.92. */
internal const val HOP_HEIGHT = 0.11f

internal fun hopLift(f: Float): Float {
    val u = max(0f, min(1f, (f - 0.3f) / 0.62f))
    return 4 * u * (1 - u)
}

/** Contact squash after a landing (0 -> 1 over LAND_MS): a fast squash, one overshoot, then rest. */
internal fun landEnvelope(progress: Float): Float {
    if (progress <= 0f || progress >= 1f) return 0f
    val s = progress * (LAND_MS / 1000f)
    if (s < 0.05f) { val u = s / 0.05f; return u * u * (3 - 2 * u) }
    return exp(-(s - 0.05f) * 7.5f) * cos((s - 0.05f) * 15f)
}

internal class LifeInput(
    /** Ambient loop time 0..1 (frozen under Reduced Motion). */
    val t: Float,
    val reduced: Boolean,
    val role: Int,
    val f: Float,
    val press: Float,
    val look: Float,
    val pet: Float,
    /** Seconds since a hold began. */
    val petClock: Float,
    /** Landing squash envelope for this creature, -1..1. */
    val land: Float,
    /** Signature progress for this creature, 0 outside its move. */
    val signature: Float,
    /** Seconds of this creature's move's effect clock (it runs on while the particles land); -1 when idle. */
    val fx: Float,
    val index: Int,
)

internal class LifePose(
    val sy: Float, val sx: Float, val lean: Float, val tx: Float, val ty: Float, val lift: Float,
    val angles: FloatArray, val headShift: Float, val yaw: Float, val breath: Float, val shift: Float,
    val hang: Float, val lid: Float, val squint: Float, val flameGrow: Float, val flameStretch: Float,
)

/**
 * Every channel of one creature for one frame: key poses come from the exchange, the press and
 * the signature move; an idle layer (breath, sway, weight, head) keeps even a held pose alive;
 * the head follows the body's lean 110 ms late and the bulb tip follows its bulb.
 */
internal fun lifePose(input: LifeInput): LifePose {
    val rig = RIGS[input.index]
    val live = if (input.reduced) 0f else 1f
    val seconds = input.t * LOOP
    fun wave(cycles: Float, phase: Float, t: Float = input.t) = sin(TAU * (t * cycles + phase + rig.phase))
    fun leanAt(t: Float) = 0.7f * wave(3f, 0.2f, t)
    val lean = live * (leanAt(input.t) + input.pet * 1.6f * sin(input.petClock * TAU * 1.6f)) * RAD
    val acting = input.fx >= 0
    val angles = FloatArray(4)
    for (j in 0 until 4) {
        val joint = rig.joints[j]
        var degrees = 0f
        for (sway in joint.sway) degrees += sway.degrees * wave(sway.cyclesPerLoop, sway.phase)
        if (joint.twitch != null) degrees += joint.twitch.degrees * twitchAt(seconds, joint.twitch.at)
        if (acting) {
            for (accent in rig.acting.accents) {
                if (accent.joint != j) continue
                val w = accent.window
                degrees += accent.degrees * rises(input.fx, w[0], w[1], w[2], w[3]) *
                    (if (accent.hz > 0) sin(TAU * (input.fx * accent.hz + accent.phase)) else 1f)
            }
        }
        if (j == 0) {
            // The head rights itself a beat after the body leans, looks towards the finger, tilts while
            // petted, and tilts to watch what its move throws.
            degrees += -0.45f * leanAt(input.t - 0.11f / LOOP) + input.look * 5 + input.pet * 4 +
                (if (acting) flow(input.fx, rig.acting.tilt) else 0f)
        }
        angles[j] = live * degrees * RAD
    }
    val breath = live * 0.012f * (0.5f + 0.5f * wave(4f, 0f))
    val lift = live * (if (input.role == 1) hopLift(input.f) else 0f)
    val signature = signaturePose(input.signature, input.index)
    // Height changes keep the volume: width scales by 1 / sy^0.6.
    val wiggle = input.pet * (0.5f + 0.5f * sin(input.petClock * TAU * 3.2f))
    val air = live * 0.05f * sin(PI.toFloat() * min(1f, lift))
    val squash = (1 - 0.035f * input.press) * (1 - 0.15f * input.land) * (1 - 0.03f * live * wiggle) * (1 + air) * (1 + 0.005f * live * wave(4f, 0.1f))
    return LifePose(
        sy = squash * signature.sy,
        sx = signature.sx / squash.pow(0.6f),
        lean = lean,
        tx = live * signature.tx,
        ty = live * signature.ty,
        lift = lift,
        angles = angles,
        headShift = live * input.look * 4,
        // The head turns (2.5D): a slow glance that never quite stops, and the move's acting turns.
        yaw = live * (0.2f * wave(2f, 0.15f) + (if (acting) flow(input.fx, rig.acting.yaw) else 0f) + input.look * 0.35f),
        breath = breath,
        // The weight drifts from one foot to the other (art px, the feet stay planted), and in the air
        // the legs hang a little longer, as a body held up by nothing does.
        shift = live * 4 * wave(3f, 0.35f),
        hang = 0.07f * lift,
        // A petted creature answers with a slow, contented double blink; it also blinks on each acting turn.
        lid = if (input.reduced) 0f else max(
            lidAt(seconds, rig.blinks),
            max(if (input.pet > 0) lidAt(input.petClock, PET_BLINKS) else 0f, if (acting) lidAt(input.fx, rig.acting.blinks) else 0f),
        ),
        squint = 0f,
        // The live tail flame: it swells into a plume during the move, and stretches 3-6% on two waves
        // (about 0.7 s and 1.1 s) the rest of the time.
        flameGrow = if (acting) rises(input.fx / (SIGNATURE_MS / 1000f), 0.1f, 0.28f, 0.6f, 0.96f) else 0f,
        flameStretch = live * (0.03f * wave(19f, 0.1f) + 0.02f * wave(12f, 0.4f)),
    )
}

/** Where one creature stands this frame: its layout, its place in the exchange and its pose. */
internal class BodyFrame(
    val index: Int,
    /** Art square and the transparent margin around it, in points. */
    val size: Float,
    val pad: Float,
    /** Art square's top-left inside the hero, the hero's top, and the screen. */
    val left: Float,
    val top: Float,
    val heroTop: Float,
    val width: Float,
    val screenHeight: Float,
    val scale: Float,
    val exchange: ExchangePose,
    val driftPx: Float,
    val pose: LifePose,
)

internal class LifeFrame(
    val body: BodyFrame,
    val phase: Float,
    /** Element of the incoming habitat. */
    val element: Int,
    /** Key light direction and how much of the baked volume shows. */
    val light: FloatArray,
    val tint: FloatArray,
    val glow: Float,
    /** Ambient loop seconds (the live flame's flicker loops with it) and Reduced Motion. */
    val seconds: Float,
    val reduced: Boolean,
)

/** The body's pose as one affine about the feet, and where its canvas sits on screen. */
internal class Placement(
    val k: Float, val fx: Float, val fy: Float, val z: Float, val ox: Float, val oy: Float,
    val c: Float, val s: Float, val originX: Float, val originY: Float,
)

internal fun placement(frame: BodyFrame): Placement {
    val size = frame.size
    val pad = frame.pad
    val pose = frame.pose
    val exchange = frame.exchange
    val k = size / 512f
    val fx = size / 2
    val fy = RIGS[frame.index].feet * k
    val view = size + pad * 2
    val z = exchange.scale
    return Placement(
        k, fx, fy, z,
        ox = pad + fx + pose.tx * frame.scale,
        oy = pad + fy + pose.ty * frame.scale - pose.lift * HOP_HEIGHT * size,
        c = cos(pose.lean),
        s = sin(pose.lean),
        originX = frame.left - pad + (view / 2) * (1 - z) + exchange.drift * frame.driftPx,
        originY = frame.heroTop + frame.top - pad + (view / 2) * (1 - z),
    )
}

private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = max(0f, min(1f, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)
}

/**
 * Where a point of the source art is on screen this frame: CREATURE_LIFE's warp run forwards
 * (the rig's bends, the breath and the head's shift, then the body's pose). Effects that leave the
 * body (the tail flame, the cannons, the bulb) start here, so they stay attached as it moves.
 */
internal fun bodyPoint(frame: BodyFrame, x: Float, y: Float): FloatArray {
    val rig = RIGS[frame.index]
    val p = placement(frame)
    val k = p.k
    val pose = frame.pose
    val ax = x * k
    val ay = y * k
    var dx = 0f
    var dy = 0f
    for (j in 0 until 4) {
        val joint = rig.joints[j]
        if (joint.radius <= 0) continue
        val r = joint.radius * k
        val w = 1 - smooth(r * 0.45f, r * 1.1f, hypot(ax - joint.center[0] * k, ay - joint.center[1] * k))
        if (w <= 0) continue
        val t = pose.angles[j] * w
        val px = ax - joint.pivot[0] * k
        val py = ay - joint.pivot[1] * k
        dx += cos(t) * px - sin(t) * py - px
        dy += sin(t) * px + cos(t) * py - py
        if (j == 0) dx += pose.headShift * frame.scale * w
    }
    val face = rig.face
    dx += pose.yaw * face.shift * k * (1 - smooth(face.radius * k * 0.15f, face.radius * k * 0.95f, hypot(ax - face.at[0] * k, ay - face.at[1] * k)))
    val chest = 1 - smooth(rig.chest[2] * k * 0.3f, rig.chest[2] * k * 1.25f, hypot(ax - rig.chest[0] * k, ay - rig.chest[1] * k))
    dx += (ax - rig.chest[0] * k) * 0.5f * pose.breath * chest
    dy += (ay - p.fy) * pose.breath * chest
    val hips = rig.hips * k
    dx += pose.shift * k * (1 - smooth(hips - 10 * k, p.fy, ay))
    dy += max(ay - hips, 0f) * pose.hang
    val u = (ax + dx - p.fx) * pose.sx
    val v = (ay + dy - p.fy) * pose.sy
    return floatArrayOf(p.originX + (p.ox + p.c * u - p.s * v) * p.z, p.originY + (p.oy + p.s * u + p.c * v) * p.z)
}
