import Foundation

/*
 Rocket Launch Progress — the deploy as pure functions of the loop time t (seconds).

 loop = 10 s: rest on the pad 0–1.4 · fuelling 1.4–4.4 (Build 0–25 %, Test –55 %, Upload –85 %,
 Launch prep –100 %, each stage easing in and out so the gauge breathes between steps) ·
 countdown 3-2-1 4.4–5.6 · ignition 5.6–6.2 (flame, smoke, the pad shakes) · lift-off 6.2–7.6
 (accelerating, pitching over to the right, a contrail behind) · deployed 7.6–8.4 · a new rocket
 rolls onto the pad 8.4–9.4 while the tank vents and the steps reset · rest 9.4–10.
*/
enum RocketLaunchProgressMotion {
    static let fuelAt = 1.4
    static let countAt = 4.4
    static let igniteAt = 5.6
    static let liftAt = 6.2
    static let doneAt = 7.6
    static let rollAt = 8.4
    static let rollFor = 1.0
    /// Fuel stages: start, end, from %, to %.
    static let stages: [(Double, Double, Double, Double)] = [
        (1.4, 2.1, 0, 25),
        (2.1, 3.0, 25, 55),
        (3.0, 3.8, 55, 85),
        (3.8, 4.4, 85, 100),
    ]
    /// Fuel % at which each step is done (Launch is done when the rocket is away).
    static let stepDone: [Double] = [25, 55, 85, 101]

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : (v > 1 ? 1 : v) }

    static func easeInOutCubic(_ v: Double) -> Double {
        let x = clamp01(v)
        if x < 0.5 { return 4 * x * x * x }
        let q = -2 * x + 2
        return 1 - q * q * q / 2
    }

    static func easeOutCubic(_ v: Double) -> Double {
        let q = 1 - clamp01(v)
        return 1 - q * q * q
    }

    /// easeOutBack: a 4 % overshoot for things that land (a rocket rolling to a stop, a badge).
    static func easeOutBack(_ v: Double) -> Double {
        let x = clamp01(v)
        let c1 = 0.9
        let c3 = c1 + 1
        let q = x - 1
        return 1 + c3 * q * q * q + c1 * q * q
    }

    /// The deploy's progress (0–100) in the loop: filled by 4.4, held, back to 0 while the tank vents.
    static func autoFuel(_ t: Double) -> Double {
        if t >= doneAt { return 100 * (1 - easeInOutCubic((t - doneAt) / 0.8)) }
        var f = 0.0
        for (a, b, p0, p1) in stages where t >= a {
            f = p0 + (p1 - p0) * easeInOutCubic((t - a) / (b - a))
        }
        return f
    }

    /// Engine: 0 off → 1 full. Lights at 5.6 with a stutter, full by 6.2, out of sight after 7.6.
    static func throttle(_ t: Double) -> Double {
        if t < igniteAt || t > rollAt { return 0 }
        let k = t - igniteAt
        let stutter = 0.12 * sin(k * 60) * exp(-k * 5)
        return clamp01(easeOutCubic(k / 0.6) + stutter)
    }

    /// How hard the camera shakes: building with the engine, peaking at lift-off, gone by 7.3.
    static func shake(_ t: Double) -> Double {
        easeOutCubic((t - igniteAt) / 0.6) * (1 - easeOutCubic((t - 6.4) / 0.9))
    }

    struct Pose {
        var x = 0.0
        var y = 0.0
        var rot = 0.0
        var on = 1.0
    }

    /// The rocket's base (nozzle exit) and pitch, relative to its place on the pad.
    static func rocketPose(_ t: Double) -> Pose {
        if t < liftAt { return Pose() }
        if t < rollAt {
            let k = t - liftAt
            // A slow first metre, then ~1100 pt/s²; it pitches over to the right as it climbs.
            let k2 = k * k
            return Pose(
                x: 70 * k2 * k,
                y: -(550 * k2 + 18 * k2 * k2 * k),
                rot: atan2(210 * k2, 1100 * k + 30) * 0.9,
                on: t < doneAt + 0.2 ? 1 : 0
            )
        }
        // The next rocket rolls in from the left on its transporter and stops with a small rock.
        let p = easeOutBack((t - rollAt) / rollFor)
        return Pose(x: -420 * (1 - p), y: 0, rot: -0.03 * sin(Double.pi * clamp01((t - rollAt) / rollFor)) * (1 - p), on: 1)
    }

    /// The countdown digit k (3, 2, 1 → 0, 1, 2): its offset (pt, + = below), opacity and scale.
    static func countDigit(_ k: Int, _ t: Double) -> (y: Double, o: Double, s: Double) {
        let a = countAt + Double(k) * 0.4
        let b = a + 0.4
        if t < a - 0.01 || t > b + 0.2 { return (30, 0, 1) }
        let inn = easeOutCubic((t - a) / 0.16)
        let out = easeOutCubic((t - b + 0.04) / 0.16)
        return (30 * (1 - inn) - 40 * out, inn * (1 - out), 0.92 + 0.08 * inn)
    }

    /// The "Deployed" badge: in at 7.6 with a little overshoot, out at 8.4.
    static func deployedBadge(_ t: Double) -> (s: Double, o: Double) {
        let inn = easeOutBack((t - doneAt) / 0.4)
        let out = easeOutCubic((t - rollAt) / 0.3)
        return (0.9 + 0.1 * inn, clamp01((t - doneAt) / 0.2) * (1 - out))
    }

    /// Step i's state: 0 waiting, 1 running, 2 done; from the fuel, and for Launch from the flight.
    /// All steps clear while the next rocket rolls in.
    static func stepState(_ i: Int, _ t: Double, _ fuel: Double) -> Int {
        if t >= rollAt + 0.2 { return 0 }
        if i == 3 { return t >= doneAt ? 2 : (fuel >= 85 || t >= countAt ? 1 : 0) }
        if fuel >= stepDone[i] || t >= doneAt { return 2 }
        let start = i == 0 ? 0.01 : stepDone[i - 1]
        return fuel >= start && (fuel > 0 || t >= fuelAt) ? 1 : 0
    }

    /// The status line's phases. The last repeats the first, so the frame at t = loop is the frame at 0.
    static let status = ["Ready on the pad", "Fuelling · build, test, upload", "T-minus", "Lift-off", "Live in production", "Next build rolling in", "Ready on the pad"]
    static let statusAt = [0, fuelAt, countAt, igniteAt, doneAt, rollAt, rollAt + rollFor]

    /// Status line i's offset (pt, + = below) and opacity at t: in from below over 0.3 s, out upward.
    static func statusLine(_ i: Int, _ t: Double) -> (y: Double, o: Double) {
        let a = statusAt[i]
        let b = i + 1 < statusAt.count ? statusAt[i + 1] : 1e9
        let inn = i == 0 ? 1 : easeOutCubic((t - a) / 0.3)
        let out = easeOutCubic((t - b) / 0.3)
        if t < a && i != 0 { return (14, 0) }
        return (14 * (1 - inn) - 14 * out, inn * (1 - out))
    }
}
