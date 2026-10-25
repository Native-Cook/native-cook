import Foundation

/*
 Elevator Floor Nav — timing. A trip (press a floor, doors close, ride, ding, doors open) is a pure
 function of the time since its button was pressed; the self-playing loop is two trips, and a
 finger simply starts a trip of its own.

 A trip pressed at p0 from floor a to floor b (durations × `pace`):
   p0          the button lights (and dips 0.12 s)
   +0.3 – 0.9  the doors close (ease in-out, a 2 % bump as they meet)
   +0.9 – R    the car rides 0.4 s a floor (smootherstep); the store behind slides by, blurred
               with speed; the dial's needle follows the car with a little follow-through
   arrive      the bell: the lantern of the direction glows and fades over 1.4 s, the button goes out
   +0.6        the doors open on the new section; the header rolls to its name

 loop = 9 s: rest on floor 1 (0 – 1.3), floor 4 pressed at 1.3 (doors 1.6 – 2.2, ride 2.2 – 3.4,
 open 3.4 – 4.0), hold, floor 1 pressed at 5.4 (doors 5.7 – 6.3, ride 6.3 – 7.5, open 7.5 – 8.1),
 rest 8.1 – 9.
*/
enum ElevatorFloorNavMotion {
    static let loop = 9.0
    static let floors = 5
    static let perFloor = 0.4
    static let idleBeforeBack = 2.0
    /// Easing back after a touch is a quick trip home at this pace (0.9 s in all).
    static let backPace = 0.9 / (0.3 + 0.6 + 0.6 + perFloor * 3)

    struct Trip {
        var from: Int
        var to: Int
        var p0: Double
        var pace: Double
    }

    struct Pose {
        /// The car's floor, continuous (1 … 5).
        var car = 1.0
        /// Ride speed in floors per second (for blur).
        var speed = 0.0
        /// Doors: 0 open … 1 closed.
        var doors = 0.0
        /// The needle's floor (follows the car with a little overshoot).
        var needle = 1.0
        /// Lit amount per button, and the dip of a pressed button.
        var lit = [0.0, 0.0, 0.0, 0.0, 0.0]
        var dip = [0.0, 0.0, 0.0, 0.0, 0.0]
        /// Bell: glow 0 … 1 of the up lantern and of the down lantern.
        var ding = [0.0, 0.0]
        /// Header label: from floor, to floor, roll 0 … 1.
        var labelFrom = 1
        var labelTo = 1
        var labelK = 1.0
    }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double {
        let v = clamp01(x)
        if v < 0.5 { return 4 * v * v * v }
        let u = -2 * v + 2
        return 1 - u * u * u / 2
    }
    static func easeOut(_ x: Double) -> Double {
        let u = 1 - clamp01(x)
        return 1 - u * u * u
    }
    static func smoother(_ x: Double) -> Double {
        let v = clamp01(x)
        return v * v * v * (v * (v * 6 - 15) + 10)
    }
    static func smootherSlope(_ x: Double) -> Double {
        if x <= 0 || x >= 1 { return 0 }
        return 30 * x * x * (x - 1) * (x - 1)
    }

    /// Times inside a trip, in seconds after the press.
    static func tripTimes(_ trip: Trip) -> (close0: Double, close1: Double, ride: Double, arrive: Double, open1: Double) {
        let k = trip.pace
        let ride = Double(max(1, abs(trip.to - trip.from))) * perFloor * k
        let close0 = 0.3 * k
        let close1 = 0.9 * k
        // Calling the floor the car is on only lights the button for a moment.
        let arrive = trip.to == trip.from ? 0.35 * k : close1 + ride
        return (close0, close1, ride, arrive, arrive + 0.6 * k)
    }

    /// The pose during (and after) a trip, at time t on the same clock as trip.p0.
    static func tripPose(_ trip: Trip, _ t: Double) -> Pose {
        let e = t - trip.p0
        let tt = tripTimes(trip)
        let span = Double(trip.to - trip.from)
        var p = Pose()
        p.car = Double(trip.from)
        if e >= tt.close1 && span != 0 {
            let u = (e - tt.close1) / tt.ride
            p.car = Double(trip.from) + span * smoother(u)
            p.speed = abs(span) * smootherSlope(u) / tt.ride
        }
        var doors = 0.0
        if span == 0 {
            doors = 0
        } else if e >= tt.close0 && e < tt.arrive {
            let c = seg(e, tt.close0, tt.close1)
            // A 2 % bump as the two doors meet.
            doors = easeInOut(c) + 0.02 * sin(.pi * seg(e, tt.close1 - 0.06 * trip.pace, tt.close1 + 0.12 * trip.pace))
        } else if e >= tt.arrive {
            doors = 1 - easeInOut(seg(e, tt.arrive, tt.open1))
        }
        p.doors = clamp01(doors)
        // The needle trails the car by 60 ms and overshoots a touch on arrival.
        var needle = Double(trip.from)
        if e >= tt.close1 && span != 0 { needle = Double(trip.from) + span * smoother((e - tt.close1 - 0.06) / tt.ride) }
        if e > tt.arrive { needle += (span > 0 ? 1 : (span < 0 ? -1 : 0)) * 0.12 * exp(-(e - tt.arrive) * 5) * sin((e - tt.arrive) * 14) }
        p.needle = needle
        let b = trip.to - 1
        if e >= 0 {
            p.lit[b] = clamp01(e / 0.08) * (1 - seg(e, tt.arrive, tt.arrive + 0.3))
            p.dip[b] = e < 0.36 ? sin(.pi * clamp01(e / 0.36)) * exp(-e * 3) : 0
        }
        let glow = e >= tt.arrive && span != 0 ? exp(-(e - tt.arrive) * 2.2) * clamp01((e - tt.arrive) / 0.05) : 0
        p.ding = span >= 0 ? [glow, 0] : [0, glow]
        p.labelFrom = trip.from
        p.labelTo = e >= tt.arrive ? trip.to : trip.from
        p.labelK = e >= tt.arrive ? easeOut((e - tt.arrive) / 0.35) : 1
        return p
    }

    /// True once a trip has fully finished (doors open, bell faded).
    static func tripDone(_ trip: Trip, _ t: Double) -> Bool {
        t - trip.p0 > tripTimes(trip).open1 + 0.2
    }

    /// A pose with the bell of an earlier trip still fading in it.
    static func mergeBell(_ p: Pose, _ earlier: Pose) -> Pose {
        var q = p
        q.ding = [max(p.ding[0], earlier.ding[0]), max(p.ding[1], earlier.ding[1])]
        return q
    }

    static let tripUp = Trip(from: 1, to: 4, p0: 1.3, pace: 1)
    static let tripDown = Trip(from: 4, to: 1, p0: 5.4, pace: 1)

    /// The pose of the self-playing loop at loop time t.
    static func loopPose(_ t: Double) -> Pose {
        if t < tripUp.p0 {
            var p = tripPose(tripDown, loop + t)
            p.lit = [0, 0, 0, 0, 0]
            p.dip = [0, 0, 0, 0, 0]
            return p
        }
        if t < tripDown.p0 { return tripPose(tripUp, t) }
        // The first trip's bell is still fading when the second starts.
        return mergeBell(tripPose(tripDown, t), tripPose(tripUp, t))
    }

    /// Resting pose at a floor (doors open, nothing lit).
    static func restAt(_ floor: Int) -> Pose {
        var p = Pose()
        p.car = Double(floor)
        p.needle = Double(floor)
        p.labelFrom = floor
        p.labelTo = floor
        return p
    }
}

/// The mode of the block and the clock: the loop plays, a finger calls floors, or the car goes home.
final class ElevatorFloorNavEngine {
    typealias M = ElevatorFloorNavMotion

    enum Mode { case auto, touch, back }

    var mode = Mode.auto
    var loopStart: Double? = nil
    var lastFrame: Double? = nil
    var lastTouch = 0.0
    var trip = M.Trip(from: 1, to: 1, p0: -99, pace: 1)
    var prev = M.Trip(from: 1, to: 1, p0: -99, pace: 1)
    var pending = 0
    /// Under reduced motion the floor simply changes.
    var still = 1
    /// A trip the finger called just arrived (for a haptic tick).
    var arrived = false
    private var wasRinging = false

    private func startTrip(_ to: Int, _ at: Double, _ pace: Double) {
        prev = trip
        trip = M.Trip(from: trip.to, to: to, p0: at, pace: pace)
    }

    func pose(at now: Double, auto: Bool, reduced: Bool) -> M.Pose {
        if loopStart == nil { loopStart = now }
        let gap = lastFrame.map { now - $0 } ?? 0
        // A stalled frame (or a paused timeline) holds the loop where it was instead of skipping.
        if gap > 0.25, mode == .auto, let s = loopStart { loopStart = s + gap }
        lastFrame = now
        if mode != .auto && tripDone(now) {
            if pending > 0 {
                let to = pending
                pending = 0
                if to != trip.to { startTrip(to, now, 1) }
            } else {
                let bell = current(now).ding
                if mode == .touch && now - lastTouch > M.idleBeforeBack {
                    mode = .back
                    if trip.to != 1 { startTrip(1, now, M.backPace) }
                } else if mode == .back && max(bell[0], bell[1]) < 0.04 {
                    mode = .auto
                    loopStart = now
                }
            }
        }
        let p: M.Pose
        if reduced && mode == .auto {
            p = M.restAt(still)
        } else if mode != .auto {
            p = current(now)
        } else if !auto {
            p = M.restAt(1)
        } else {
            p = M.loopPose(max(0, (now - (loopStart ?? now)).truncatingRemainder(dividingBy: M.loop)))
        }
        let ringing = mode == .touch && max(p.ding[0], p.ding[1]) > 0.8
        if ringing && !wasRinging { arrived = true }
        wasRinging = ringing
        return p
    }

    private func tripDone(_ now: Double) -> Bool { M.tripDone(trip, now) }

    private func current(_ now: Double) -> M.Pose {
        M.mergeBell(M.tripPose(trip, now), M.tripPose(prev, now))
    }

    /// Hands the loop's trip to the finger's clock, so nothing jumps.
    private func takeOver(_ at: Double) {
        if mode == .touch { return }
        if mode == .auto {
            let start = loopStart ?? at
            let t = ((at - start).truncatingRemainder(dividingBy: M.loop) + M.loop).truncatingRemainder(dividingBy: M.loop)
            if t >= 1.3 && t < 5.4 {
                trip = M.Trip(from: 1, to: 4, p0: at - (t - 1.3), pace: 1)
                prev = M.Trip(from: 4, to: 1, p0: at - (t + M.loop - 5.4), pace: 1)
            } else {
                let back = t >= 5.4 ? t - 5.4 : t + M.loop - 5.4
                trip = M.Trip(from: 4, to: 1, p0: at - back, pace: 1)
                prev = M.Trip(from: 1, to: 4, p0: at - back - 4.1, pace: 1)
            }
        }
        mode = .touch
    }

    /// A floor button was pressed (autoPlay may or may not have been running).
    func call(_ floor: Int, at: Double, auto: Bool, reduced: Bool) {
        lastTouch = at
        if reduced {
            still = floor
            return
        }
        if mode == .auto && !auto {
            // Nothing played: the car rests on floor 1.
            trip = M.Trip(from: 1, to: 1, p0: -99, pace: 1)
            prev = trip
            mode = .touch
        }
        takeOver(at)
        // A floor called while the car is busy waits for this trip to finish.
        if tripDone(at) { startTrip(floor, at, 1) } else { pending = floor }
    }
}
