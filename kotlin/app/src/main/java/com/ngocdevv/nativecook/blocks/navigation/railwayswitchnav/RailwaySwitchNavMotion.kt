package com.ngocdevv.nativecook.blocks.navigation.railwayswitchnav

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Railway Switch Nav — the trips and the loop, as in railwayMotion.ts. A "trip" is everything one
// lever pull does: the lever travels, two switches throw, the signals change, the route lights up,
// the train runs from one platform to another and the page changes. Every value is a closed-form
// function of the time since the trip began, so the loop (three trips) is exact and a finger's
// trip uses the same code.

internal const val LOOP = 9f
internal val RailSections = listOf("Home", "Trips", "Tickets", "Account")
internal const val COUNT = 4

/** A trip: start (s), lever pull (s), run (s), from, to. */
internal class RailTrip(val start: Float, val pull: Float, val run: Float, val from: Int, val to: Int)

internal val RailTrips = listOf(
    RailTrip(1.2f, 0.8f, 1.4f, 0, 2),
    RailTrip(4.4f, 0.6f, 1.2f, 2, 1),
    RailTrip(6.8f, 0.6f, 1.0f, 1, 0),
)
/** A finger's trip: the same shape, a little quicker. */
internal const val TOUCH_PULL = 0.5f
internal const val TOUCH_RUN = 1.1f
/** The page change starts this far into the run and lasts PAGE_FOR. */
private const val PAGE_AT = 0.71f
private const val PAGE_FOR = 0.6f

internal fun railClamp01(x: Float) = min(max(x, 0f), 1f)

private fun seg(t: Float, a: Float, b: Float) = railClamp01((t - a) / (b - a))

private fun easeInOut(x: Float): Float {
    val t = railClamp01(x)
    return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
}

private fun easeOut(x: Float): Float {
    val t = railClamp01(x)
    return 1f - (1f - t).pow(3)
}

/** A lever's travel: a short anticipation back (4 %), then the pull with a 5 % overshoot into the notch. */
private fun leverCurve(x: Float): Float {
    val t = railClamp01(x)
    if (t < 0.15f) return -0.04f * sin(t / 0.15f * PI.toFloat())
    val u = (t - 0.15f) / 0.85f
    val c = 1.7f
    return 1f + (c + 1f) * (u - 1f).pow(3) + c * (u - 1f).pow(2)
}

internal class RailPose {
    val levers = FloatArray(COUNT)
    /** Switch blades, -1 (set left) … 0 (closed) … 1 (set right). */
    val blades = FloatArray(COUNT)
    /** Green share of each section's signal. */
    val signals = FloatArray(COUNT)
    var from = 0
    var to = 0
    /** The route's light (0..1) and the train's progress along it (0..1). */
    var route = 0f
    var run = 1f
    /** The page change (0 = from's page, 1 = to's page). */
    var page = 1f
}

internal fun railTripPose(tau: Float, pull: Float, run: Float, from: Int, to: Int, pullFrom: Float, out: RailPose): RailPose {
    val dir = if (to > from) 1f else -1f
    val depart = pull
    val arrive = pull + run
    val throwIn = easeInOut(seg(tau, pull * 0.55f, pull * 0.55f + 0.3f))
    val release = easeInOut(seg(tau, arrive + 0.15f, arrive + 0.5f))
    val green = easeInOut(seg(tau, pull * 0.8f, pull * 0.8f + 0.18f))
    for (k in 0 until COUNT) {
        var lever = 0f
        var blade = 0f
        var signal = 0f
        if (k == to) {
            lever = pullFrom + (1f - pullFrom) * leverCurve(tau / pull)
            blade = -dir * throwIn * (1f - release)
            signal = green
        } else if (k == from) {
            lever = 1f - easeInOut(seg(tau, 0.1f, 0.1f + pull * 0.8f))
            blade = dir * throwIn * (1f - release)
            signal = 1f - green
        }
        out.levers[k] = lever
        out.blades[k] = blade
        out.signals[k] = signal
    }
    out.from = from
    out.to = to
    out.route = easeOut(seg(tau, pull * 0.55f, pull)) * (1f - easeInOut(seg(tau, arrive, arrive + 0.45f)))
    out.run = easeInOut(seg(tau, depart, arrive))
    out.page = easeInOut(seg(tau, depart + run * PAGE_AT, depart + run * PAGE_AT + PAGE_FOR))
    return out
}

/** The loop's current trip and the time since it began. */
internal fun railLoopTrip(t: Float): Pair<Int, Float> {
    // The latest trip that has begun; before the first one the last trip is long finished (Home).
    var k = RailTrips.size - 1
    var tau = t + LOOP - RailTrips[k].start
    for (i in RailTrips.indices) {
        if (t >= RailTrips[i].start) {
            k = i
            tau = t - RailTrips[i].start
        }
    }
    return k to tau
}

internal fun railLoopPose(t: Float, out: RailPose): RailPose {
    val (k, tau) = railLoopTrip(t)
    val trip = RailTrips[k]
    return railTripPose(tau, trip.pull, trip.run, trip.from, trip.to, 0f, out)
}

/** How long a trip lasts until everything has settled. */
internal fun railTripLength(pull: Float, run: Float) = max(pull + run + 0.6f, pull + run * PAGE_AT + PAGE_FOR)
