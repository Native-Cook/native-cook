package com.ngocdevv.nativecook.blocks.navigation.elevatorfloornav

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/*
 Elevator Floor Nav — timing, ported from elevatorMotion.ts. A trip (press a floor, doors close,
 ride, ding, doors open) is a pure function of the time since its button was pressed; the
 self-playing loop is two trips, and a finger simply starts a trip of its own.

 A trip pressed at p0 from floor a to floor b (durations × `pace`):
   p0          the button lights (and dips 0.12 s)
   +0.3 – 0.9  the doors close (ease in-out, a 2 % bump as they meet)
   +0.9 – R    the car rides 0.4 s a floor (smootherstep); the store behind slides by, blurred
               with speed; the dial's needle follows the car with a little follow-through
   arrive      the bell: the lantern of the direction glows and fades, the button goes out
   +0.6        the doors open on the new section; the header rolls to its name

 LOOP = 9 s: rest on floor 1 (0 – 1.3), floor 4 pressed at 1.3 (doors 1.6 – 2.2, ride 2.2 – 3.4,
 open 3.4 – 4.0), hold, floor 1 pressed at 5.4 (doors 5.7 – 6.3, ride 6.3 – 7.5, open 7.5 – 8.1),
 rest 8.1 – 9.
*/
internal object ElevatorMotion {
    const val LOOP = 9.0
    const val FLOORS = 5
    const val PER_FLOOR = 0.4
    const val IDLE_BEFORE_BACK = 2.0
    /** Easing back after a touch is a quick trip home at this pace (0.9 s in all). */
    const val BACK_PACE = 0.9 / (0.3 + 0.6 + 0.6 + PER_FLOOR * 3)

    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeInOut(x: Double): Double {
        val v = clamp01(x)
        return if (v < 0.5) 4 * v * v * v else 1 - (-2 * v + 2).pow(3) / 2
    }
    fun easeOut(x: Double): Double {
        val u = 1 - clamp01(x)
        return 1 - u * u * u
    }
    private fun smoother(x: Double): Double {
        val v = clamp01(x)
        return v * v * v * (v * (v * 6 - 15) + 10)
    }
    private fun smootherSlope(x: Double) = if (x <= 0 || x >= 1) 0.0 else 30 * x * x * (x - 1) * (x - 1)

    class Times(val close0: Double, val close1: Double, val ride: Double, val arrive: Double, val open1: Double)

    /** Times inside a trip, in seconds after the press. */
    fun tripTimes(trip: ElevatorTrip): Times {
        val k = trip.pace
        val ride = max(1, abs(trip.to - trip.from)) * PER_FLOOR * k
        val close0 = 0.3 * k
        val close1 = 0.9 * k
        // Calling the floor the car is on only lights the button for a moment.
        val arrive = if (trip.to == trip.from) 0.35 * k else close1 + ride
        return Times(close0, close1, ride, arrive, arrive + 0.6 * k)
    }

    /** The pose during (and after) a trip, at time t on the same clock as trip.p0. */
    fun tripPose(trip: ElevatorTrip, t: Double): ElevatorPose {
        val p = ElevatorPose()
        val e = t - trip.p0
        val tt = tripTimes(trip)
        val span = trip.to - trip.from
        var car = trip.from.toDouble()
        var speed = 0.0
        if (e >= tt.close1 && span != 0) {
            val u = (e - tt.close1) / tt.ride
            car = trip.from + span * smoother(u)
            speed = abs(span) * smootherSlope(u) / tt.ride
        }
        var doors = 0.0
        if (span == 0) {
            doors = 0.0
        } else if (e >= tt.close0 && e < tt.arrive) {
            val c = seg(e, tt.close0, tt.close1)
            // A 2 % bump as the two doors meet.
            doors = easeInOut(c) + 0.02 * sin(PI * seg(e, tt.close1 - 0.06 * trip.pace, tt.close1 + 0.12 * trip.pace))
        } else if (e >= tt.arrive) {
            doors = 1 - easeInOut(seg(e, tt.arrive, tt.open1))
        }
        // The needle trails the car by 60 ms and overshoots a touch on arrival.
        var needle = trip.from.toDouble()
        if (e >= tt.close1 && span != 0) needle = trip.from + span * smoother((e - tt.close1 - 0.06) / tt.ride)
        if (e > tt.arrive) needle += sign(span.toDouble()) * 0.12 * exp(-(e - tt.arrive) * 5) * sin((e - tt.arrive) * 14)
        val b = trip.to - 1
        if (e >= 0) {
            p.lit[b] = clamp01(e / 0.08) * (1 - seg(e, tt.arrive, tt.arrive + 0.3))
            p.dip[b] = if (e < 0.36) sin(PI * clamp01(e / 0.36)) * exp(-e * 3) else 0.0
        }
        val glow = if (e >= tt.arrive && span != 0) exp(-(e - tt.arrive) * 2.2) * clamp01((e - tt.arrive) / 0.05) else 0.0
        if (span >= 0) p.ding[0] = glow else p.ding[1] = glow
        p.car = car
        p.speed = speed
        p.doors = clamp01(doors)
        p.needle = needle
        p.labelFrom = trip.from
        p.labelTo = if (e >= tt.arrive) trip.to else trip.from
        p.labelK = if (e >= tt.arrive) easeOut((e - tt.arrive) / 0.35) else 1.0
        return p
    }

    /** True once a trip has fully finished (doors open, bell faded). */
    fun tripDone(trip: ElevatorTrip, t: Double) = t - trip.p0 > tripTimes(trip).open1 + 0.2

    /** A pose with the bell of an earlier trip still fading in it. */
    fun mergeBell(p: ElevatorPose, earlier: ElevatorPose): ElevatorPose {
        p.ding[0] = max(p.ding[0], earlier.ding[0])
        p.ding[1] = max(p.ding[1], earlier.ding[1])
        return p
    }

    val TRIP_UP = ElevatorTrip(1, 4, 1.3, 1.0)
    val TRIP_DOWN = ElevatorTrip(4, 1, 5.4, 1.0)

    /** The pose of the self-playing loop at loop time t. */
    fun loopPose(t: Double): ElevatorPose {
        if (t < TRIP_UP.p0) {
            val p = tripPose(TRIP_DOWN, LOOP + t)
            p.lit.fill(0.0)
            p.dip.fill(0.0)
            return p
        }
        if (t < TRIP_DOWN.p0) return tripPose(TRIP_UP, t)
        // The first trip's bell is still fading when the second starts.
        return mergeBell(tripPose(TRIP_DOWN, t), tripPose(TRIP_UP, t))
    }

    /** Resting pose at a floor (doors open, nothing lit). */
    fun restAt(floor: Int) = ElevatorPose().apply {
        car = floor.toDouble()
        needle = floor.toDouble()
        labelFrom = floor
        labelTo = floor
    }
}

internal class ElevatorTrip(val from: Int, val to: Int, val p0: Double, val pace: Double)

internal class ElevatorPose {
    /** The car's floor, continuous (1 … 5). */
    var car = 1.0
    /** Ride speed in floors per second (for blur). */
    var speed = 0.0
    /** Doors: 0 open … 1 closed. */
    var doors = 0.0
    /** The needle's floor (follows the car with a little overshoot). */
    var needle = 1.0
    /** Lit amount per button, and the dip of a pressed button. */
    val lit = DoubleArray(ElevatorMotion.FLOORS)
    val dip = DoubleArray(ElevatorMotion.FLOORS)
    /** Bell: glow 0 … 1 of the up lantern and of the down lantern. */
    val ding = DoubleArray(2)
    /** Header label: from floor, to floor, roll 0 … 1. */
    var labelFrom = 1
    var labelTo = 1
    var labelK = 1.0
}
