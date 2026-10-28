package com.ngocdevv.nativecook.blocks.lists.emeraldtierleaderboard

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------
// motion.ts
// ---------------------------------------------------------------------------------------------

internal const val EMERALD_TIER_LEADERBOARD_LOOP_SECONDS = 10f
internal const val LOOP = EMERALD_TIER_LEADERBOARD_LOOP_SECONDS
internal const val TAKEOVER_MS = 1200f
internal const val CROWN_SEAT_PHASE = .98f
internal const val DEMO_TAKEOVER_START = 2f
internal const val DEMO_RETURN_START = 7.6f
private const val SNAP_DECELERATION = .995f

internal fun clamp(n: Float, a: Float = 0f, b: Float = 1f): Float = max(a, min(b, n))
internal fun range(t: Float, a: Float, b: Float): Float = clamp((t - a) / (b - a))

internal fun bezier(xIn: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val x = clamp(xIn)
    var u = x
    for (i in 0 until 7) {
        val a = 1 - u
        val f = 3 * a * a * u * x1 + 3 * a * u * u * x2 + u * u * u - x
        val d = 3 * a * a * x1 + 6 * a * u * (x2 - x1) + 3 * u * u * (1 - x2)
        if (abs(d) < .000001f) break
        u = clamp(u - f / d)
    }
    return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
}

internal fun out(x: Float): Float = bezier(x, .23f, 1f, .32f, 1f)
private fun mix(a: Float, b: Float, p: Float): Float = a + (b - a) * p
private fun smooth(p: Float): Float = p * p * (3 - 2 * p)

/** JavaScript's Math.round: halves round up. */
internal fun jsRound(x: Float): Int = floor(x + .5f).toInt()

/** The demo clock has readable holds; touch never enters its backwards return branch. */
internal fun emeraldDemoPhase(progress: Float): Float {
    val seconds = clamp(progress) * LOOP
    if (seconds < DEMO_TAKEOVER_START) return 0f
    if (seconds < DEMO_TAKEOVER_START + TAKEOVER_MS / 1000) return (seconds - DEMO_TAKEOVER_START) / (TAKEOVER_MS / 1000)
    if (seconds < DEMO_RETURN_START) return 1f
    if (seconds < DEMO_RETURN_START + TAKEOVER_MS / 1000) return 1 - (seconds - DEMO_RETURN_START) / (TAKEOVER_MS / 1000)
    return 0f
}

/** Continuous resistance at either endpoint, in the same units as the drag. */
internal fun emeraldRubberPhase(value: Float): Float {
    val edge = clamp(value)
    val over = value - edge
    return edge + over / (1 + abs(over) / .12f)
}

internal fun emeraldUnrubberPhase(value: Float): Float {
    val edge = clamp(value)
    val over = value - edge
    return edge + over / max(.00001f, 1 - abs(over) / .12f)
}

/** A deliberate flick commits in its direction; a slow release chooses the nearest projected rest. */
internal fun emeraldSnapTarget(phase: Float, velocity: Float): Int {
    if (velocity > .35f) return 1
    if (velocity < -.35f) return 0
    val projected = phase + (velocity / 1000) * SNAP_DECELERATION / (1 - SNAP_DECELERATION)
    return if (clamp(projected) >= .5f) 1 else 0
}

internal data class EmeraldPoint(val x: Float, val y: Float, val scale: Float)
internal data class EmeraldCrownPoint(val x: Float, val y: Float, val rotation: Float)

internal data class EmeraldFrame(
    val progress: Float, val rawProgress: Float, val cycle: Float, val rise: Float, val travel: Float, val arc: Float,
    val ari: EmeraldPoint, val milo: EmeraldPoint, val crown: EmeraldCrownPoint, val crownTravel: Float,
    val age: Float, val impact: Boolean, val burst: Float, val response: Float, val responsePhase: Float,
    val anticipation: Float, val settle: Float, val labelOpacity: Float, val score: Int, val hold: Float,
    val landing: Float, val reduced: Boolean, val allowResponse: Boolean,
)

/** One monotonic, interruptible rank journey. All coordinates remain local to the existing arena. */
internal fun emeraldRankFrame(rankPhase: Float, reduced: Boolean = false, allowResponse: Boolean = true): EmeraldFrame {
    val t = if (reduced) (if (rankPhase >= .5f) 1f else 0f) else clamp(rankPhase)
    val edgeOffset = if (reduced) 0f else (rankPhase - t) * 112
    val rise = out(range(t, 0f, .24f))
    val p = bezier(range(t, .28f, .7175f), .4f, 0f, .2f, 1f)
    val arc = sin(p * PI.toFloat())
    val anticipation = if (reduced) 0f else sin(range(t, .20f, .28f) * PI.toFloat())
    val landingAge = max(0f, (t - .7175f) * TAKEOVER_MS / 1000)
    val age = max(0f, (t - CROWN_SEAT_PHASE) * TAKEOVER_MS / 1000)
    val landed = !reduced && t > .7175f && t < 1
    val impact = allowResponse && !reduced && t >= CROWN_SEAT_PHASE && t < 1
    val landing = if (landed) sin(range(t, .7175f, .91f) * PI.toFloat()) else 0f
    val settle = if (landed) -1.1f * exp(-16 * landingAge) * sin(24 * landingAge) else 0f
    val responsePhase = range(t, CROWN_SEAT_PHASE, 1f)
    val response = if (impact) sin(responsePhase * PI.toFloat()).pow(2) else 0f
    val crownSeat = if (impact) -1.2f * exp(-20 * age) * sin(28 * age) else 0f
    // Eight extra points on Ari's lower route keep the two circular portraits separated.
    val ari = EmeraldPoint(mix(142f, 30f, p) + edgeOffset, mix(35f, 73f, p) + arc * 35, mix(1f, .8f, p))
    val milo = EmeraldPoint(
        mix(30f, 142f, p) + edgeOffset,
        mix(73f, 35f, p) - arc * 37 + anticipation * 3 + landing * 1.4f + settle,
        mix(.8f, 1f, p) * (1 - anticipation * .012f),
    )
    // The crown belongs to the first-place axis, not an interpolation of two moving heads.
    // Its top never rises above -10: the countdown ends at -13 in arena coordinates.
    val lift = out(range(t, .20f, .28f)) * (1 - smooth(range(t, .7175f, CROWN_SEAT_PHASE)))
    val crown = EmeraldCrownPoint(
        180 + edgeOffset,
        14 - lift * 24 + (if (p == 1f) landing * 1.4f + settle else 0f) + crownSeat,
        0f,
    )
    // Both text blocks are fully hidden before their boxes can meet the other portrait.
    val labelOpacity = if (reduced) 1f else 1 - smooth(range(p, .02f, .12f)) + smooth(range(p, .88f, .98f))
    return EmeraldFrame(
        progress = t, rawProgress = rankPhase, cycle = t, rise = rise, travel = p, arc = arc, ari = ari, milo = milo,
        crown = crown, crownTravel = range(t, .20f, CROWN_SEAT_PHASE), age = age, impact = impact,
        burst = response, response = response, responsePhase = responsePhase, anticipation = anticipation,
        settle = settle, labelOpacity = labelOpacity, score = 950 + jsRound(100 * rise),
        hold = smooth(range(t, CROWN_SEAT_PHASE, .94f)), landing = landing, reduced = reduced, allowResponse = allowResponse,
    )
}

/** Public deterministic preview: t=0 and t=10 have exactly the same frame. */
internal fun emeraldFrame(progress: Float, reduced: Boolean = false): EmeraldFrame {
    val wrapped = ((progress % 1) + 1) % 1
    return emeraldRankFrame(emeraldDemoPhase(wrapped), reduced, wrapped * LOOP < DEMO_RETURN_START)
}

// ---------------------------------------------------------------------------------------------
// recastMotion.ts
// ---------------------------------------------------------------------------------------------

internal const val RECAST_STAGE_WIDTH = 393f
internal const val RECAST_STAGE_HEIGHT = 335f
internal const val RECAST_DURATION_MS = 1200f

/** The crown lands on the new leader here; the haptic and the plinth response follow it. */
internal const val RECAST_SEAT_PHASE = 0.88f

/** Arena y of the promotion boundary between the podium (top 3) and row 4. */
internal const val PROMOTION_LINE_Y = 281.5f

/** Declaration order is the RN id order ['ari','milo','juno','theo']; the ordinal indexes the frame arrays. */
internal enum class RecastActorId { ari, milo, juno, theo }

/** Back-to-front order. Theo's source row is the foreground object throughout the recast. */
internal val RECAST_SURFACE_ORDER: List<RecastActorId> =
    listOf(RecastActorId.juno, RecastActorId.milo, RecastActorId.ari, RecastActorId.theo)
internal val RECAST_ACTOR_ORDER = RECAST_SURFACE_ORDER

internal data class RecastSurface(
    val x: Float, val y: Float, val width: Float, val height: Float, val radius: Float,
    /** The top plane extends UPWARD from y to y-depth. */
    val depth: Float, val rank: Int, val rankOpacity: Float, val glow: Float,
)

internal data class RecastActor(
    val cx: Float, val cy: Float, val size: Float,
    /** Leading edge of a 110pt name box; center-aligned on podium, left-aligned in row. */
    val nameX: Float,
    /** Top edge of the native 18pt name box. */
    val nameY: Float,
    /** Center of the native score box; scoreY is its 21pt score box's top edge. */
    val scoreX: Float, val scoreY: Float,
    val rowMix: Float, val labelOpacity: Float, val score: Int, val rank: Int,
)

internal data class RecastCrown(
    val x: Float, val y: Float, val opacity: Float, val rotation: Float, val squash: Float, val sparkle: Float,
)

internal data class RecastGain(val x: Float, val y: Float, val opacity: Float)
internal data class RecastLight(val response: Float, val phase: Float, val seat: Float)

internal class RecastFrame(
    val phase: Float, val reduced: Boolean,
    /** Indexed by [RecastActorId.ordinal]. */
    val actors: Array<RecastActor>,
    val surfaces: Array<RecastSurface>,
    /** Crown has the existing 34x30 footprint. It never leaves the first-place axis. */
    val crown: RecastCrown,
    /** 0 to 1 while every plinth numeral rolls from its old rank to its new one. */
    val roll: Float,
    /** Canonical 68x19 XP caption, above Theo with a 3pt portrait gap. */
    val gain: RecastGain,
    val light: RecastLight,
    /** Renderer aliases for the single docking reflection. */
    val response: Float, val responsePhase: Float,
) {
    fun actor(id: RecastActorId) = actors[id.ordinal]
    fun surface(id: RecastActorId) = surfaces[id.ordinal]

    /** Same frame with the docking response silenced (the scene does this while the response is not allowed). */
    fun withoutResponse() = RecastFrame(
        phase, reduced, actors, surfaces, crown, roll, gain, light.copy(response = 0f), 0f, responsePhase,
    )
}

private fun segment(t: Float, from: Float, to: Float): Float {
    val p = clamp((t - from) / (to - from))
    return p * p * (3 - 2 * p)
}

/**
 * The crown stays on the throne's axis while the kings change: it pops off Ari, hovers with a
 * slow wobble while the podium rearranges, then drops onto Theo with gravity and a damped seat.
 */
private fun crownPose(t: Float, reduced: Boolean): RecastCrown {
    if (reduced) return RecastCrown(180f, 14f, 1f, 0f, 0f, 0f)
    val pop = segment(t, 0.05f, 0.17f)
    val fall = clamp((t - 0.74f) / (RECAST_SEAT_PHASE - 0.74f))
    val hover = if (t > 0.17f && t < 0.74f) sin((t - 0.17f) / 0.57f * PI.toFloat()) else 0f
    val lifted = if (t < RECAST_SEAT_PHASE) pop * (1 - fall * fall) else 0f
    // Normalised seat time, so the settle reaches the exact rest pose at the end of the event.
    val u = if (t >= RECAST_SEAT_PHASE) clamp((t - RECAST_SEAT_PHASE) / (1 - RECAST_SEAT_PHASE)) else 0f
    val seat = if (t >= RECAST_SEAT_PHASE) exp(-u * 4) * sin(u * PI.toFloat() * 3) * (1 - u) else 0f
    return RecastCrown(
        x = 180f,
        y = 14 - 20 * lifted - 2.5f * hover + 2.2f * seat,
        opacity = 1f,
        rotation = lifted * (sin(t * 19) * 7 - 4) + seat * -6,
        squash = if (t >= RECAST_SEAT_PHASE) (1 - u) * (1 - u) else 0f,
        sparkle = u,
    )
}

private fun actor(
    cx: Float, cy: Float, size: Float, rowMix: Float, surface: RecastSurface, opacity: Float, score: Int,
    labelRowMix: Float = rowMix,
) = RecastActor(
    cx = cx, cy = cy, size = size, rowMix = rowMix, score = score, rank = surface.rank, labelOpacity = opacity,
    nameX = mix(cx - 55, surface.x + 82, labelRowMix),
    nameY = mix(cy + size / 2 + 7, surface.y + 15, labelRowMix),
    scoreX = mix(cx, surface.x + surface.width - 59, labelRowMix),
    scoreY = mix(cy + size / 2 + 25, surface.y + 14, labelRowMix),
)

/**
 * One deterministic and reversible event: lift 0-.18, space-yield .14-.5,
 * reshape/travel .18-.8, dock .8-1. No integration, random values, or renderer objects.
 * Reduced motion samples one complete endpoint at the semantic halfway point.
 */
internal fun recastFrame(phase: Float, reduced: Boolean = false): RecastFrame {
    val input = clamp(phase)
    val t = if (reduced) (if (input >= 0.5f) 1f else 0f) else input
    val lift = segment(t, 0f, 0.18f)
    val yieldSpace = segment(t, 0.14f, 0.50f)
    val reshape = segment(t, 0.18f, 0.80f)
    val junoShape = segment(t, 0.24f, 0.80f)
    val seated = segment(t, 0.80f, 1f)
    val responsePhase = clamp((t - 0.80f) / 0.20f)
    val response = if (reduced || t <= 0.80f || t >= 1) 0f else sin(responsePhase * PI.toFloat()).pow(2)
    val contact = if (reduced) 0f else response * 1.4f
    // Each name is hidden only while its own portrait crosses another lane, then returns at its new seat.
    val podiumLabel = 1 - segment(t, 0.12f, 0.18f) + segment(t, 0.50f, 0.58f)
    val junoLabelOpacity = 1 - segment(t, 0.12f, 0.18f) + segment(t, 0.66f, 0.74f)
    val theoLabelOpacity = 1 - segment(t, 0.06f, 0.12f) + segment(t, 0.68f, 0.76f)
    // The XP gain rides beside Theo during the ascent only, clear of the crown's axis.
    val gainOpacity = if (reduced) 0f else segment(t, 0.56f, 0.64f) * (1 - segment(t, 0.90f, 0.98f))
    // Plinth numerals stay with their plinths and roll to the new order as they settle; they only
    // dim while two plinths pass through each other, so two numerals never stack.
    val crossing = if (reduced) 0f else sin(PI.toFloat() * segment(t, 0.14f, 0.50f))
    val podiumRank = 1 - 0.9f * crossing
    val roll = if (reduced) (if (t >= 0.5f) 1f else 0f) else segment(t, 0.46f, 0.68f)
    // Rank identities change only while every rank/name label is fully hidden.
    val newOrder = t >= 0.5f
    val ariS = RecastSurface(
        x = mix(135f, 24f, yieldSpace), y = mix(175f, 202f, yieldSpace), width = 123f,
        height = mix(100f, 73f, yieldSpace), radius = 4f, depth = 15f,
        rank = if (newOrder) 2 else 1, rankOpacity = podiumRank, glow = mix(0.76f, 0.31f, yieldSpace),
    )
    val miloS = RecastSurface(
        x = mix(24f, 246f, yieldSpace), y = mix(202f, 214f, yieldSpace), width = 123f,
        height = mix(73f, 61f, yieldSpace), radius = 4f, depth = 15f,
        rank = if (newOrder) 3 else 2, rankOpacity = podiumRank, glow = 0.31f,
    )
    val junoS = RecastSurface(
        x = mix(246f, 19f, junoShape), y = mix(214f, 288f, junoShape),
        width = mix(123f, 355f, junoShape), height = mix(61f, 47f, junoShape),
        radius = mix(4f, 13f, junoShape), depth = mix(15f, 0f, junoShape),
        rank = if (newOrder) 4 else 3, rankOpacity = if (reduced) 1f else 1 - segment(t, 0.18f, 0.32f),
        glow = mix(0.31f, 0.13f, junoShape),
    )
    val theoS = RecastSurface(
        x = mix(19f, 135f, reshape), y = mix(288 - 18 * lift, 175f, reshape) + contact,
        width = mix(355f, 123f, reshape), height = mix(47f, 100f, reshape) - contact,
        radius = mix(13f, 4f, reshape), depth = mix(0f, 15f, reshape),
        rank = if (newOrder) 1 else 4, rankOpacity = if (reduced) 1f else segment(t, 0.56f, 0.74f),
        glow = mix(0.13f, 0.76f, reshape),
    )

    // Ari uses an upper lane; Milo vacates left downward before crossing beneath that lane.
    val ariUp = segment(t, 0.14f, 0.30f)
    val ariLeft = segment(t, 0.30f, 0.50f)
    val ariX = mix(mix(197f, 150f, ariUp), 85f, ariLeft)
    val ariY = mix(mix(70f, 40f, ariUp), 101f, ariLeft)
    val ariSize = mix(70f, 56f, ariUp)
    val miloDown = segment(t, 0.14f, 0.26f)
    val miloAcross = segment(t, 0.26f, 0.43f)
    val miloUp = segment(t, 0.43f, 0.50f)
    val miloX = mix(85f, 321f, miloAcross)
    val miloY = mix(mix(101f, 172f, miloDown), 111f, miloUp)

    // Juno takes the outer-right lane, then travels along the vacated row baseline.
    val junoDown = segment(t, 0.14f, 0.28f)
    val junoRow = segment(t, 0.28f, 0.43f)
    val junoAcross = segment(t, 0.43f, 0.68f)
    val junoX = mix(mix(321f, 350f, junoDown), 74f, junoAcross)
    val junoY = mix(mix(111f, 210f, junoDown), 311.5f, junoRow)
    val junoSize = mix(mix(56f, 42f, junoDown), 30f, junoRow)

    // Theo waits below the crossing, then owns the now-clear center ascent.
    val theoCenter = segment(t, 0.34f, 0.56f)
    val theoRise = segment(t, 0.56f, 0.80f)
    val theoX = mix(74f, 197f, theoCenter)
    val theoY = mix(mix(mix(311.5f, 278f, lift), 225f, theoCenter), 70f, theoRise) + contact
    val theoSize = mix(mix(mix(30f, 38f, lift), 52f, theoCenter), 70f, theoRise)
    val actors = arrayOf(
        actor(ariX, ariY, ariSize, 0f, ariS, podiumLabel, 1000),
        actor(miloX, miloY, 56f, 0f, miloS, podiumLabel, 950),
        actor(junoX, junoY, junoSize, junoShape, junoS, junoLabelOpacity, 900),
        // Finish the hidden label-layout transfer before revealing Theo during the ascent.
        // Physical rowMix and all portrait/surface trajectories remain unchanged.
        // The score counts up while Theo climbs, so the gain is read as it happens.
        actor(
            theoX, theoY, theoSize, 1 - reshape, theoS, theoLabelOpacity,
            850 + jsRound(200 * (1 - (1 - segment(t, 0.60f, 0.88f)).pow(2))), 1 - segment(t, 0.18f, 0.60f),
        ),
    )
    return RecastFrame(
        phase = t, reduced = reduced, actors = actors, surfaces = arrayOf(ariS, miloS, junoS, theoS),
        crown = crownPose(t, reduced),
        gain = RecastGain(theoX + theoSize / 2 + 6, theoY - 10, gainOpacity),
        roll = roll,
        light = RecastLight(response, responsePhase, seated), response = response, responsePhase = responsePhase,
    )
}

// ---------------------------------------------------------------------------------------------
// Reanimated withSpring({ duration, dampingRatio }) stand-in
// ---------------------------------------------------------------------------------------------

/**
 * Duration-based spring in the Reanimated / SwiftUI sense: `duration` is the perceptual period
 * (omega0 = 2 pi / duration, unit mass), `dampingRatio` the damping ratio.
 */
internal class EmeraldSpring(
    val from: Float, val to: Float, val velocity: Float, durationMs: Float, val dampingRatio: Float,
    val overshootClamping: Boolean,
) {
    private val omega0 = (2 * PI / (durationMs / 1000.0)).toFloat()

    /** Position at [seconds] after the start. */
    fun valueAt(seconds: Float): Float {
        val a = from - to
        val zeta = dampingRatio
        val w = omega0
        val x = if (zeta < 1f) {
            val wd = w * sqrt(1 - zeta * zeta)
            val b = (velocity + zeta * w * a) / wd
            exp(-zeta * w * seconds) * (a * cos(wd * seconds) + b * sin(wd * seconds))
        } else if (zeta == 1f) {
            (a + (velocity + w * a) * seconds) * exp(-w * seconds)
        } else {
            val s = sqrt(zeta * zeta - 1)
            val r1 = -w * (zeta - s)
            val r2 = -w * (zeta + s)
            val c2 = (velocity - r1 * a) / (r2 - r1)
            val c1 = a - c2
            c1 * exp(r1 * seconds) + c2 * exp(r2 * seconds)
        }
        return to + x
    }

    /** Time after which the envelope is under the rest threshold; a guard against a spring that never settles. */
    val maxSeconds: Float
        get() {
            val decay = max(0.0001f, dampingRatio.coerceAtMost(1f) * omega0)
            return max(0.2f, ln(1000f * (abs(from - to) + abs(velocity) / omega0 + 0.01f)) / decay)
        }
}
