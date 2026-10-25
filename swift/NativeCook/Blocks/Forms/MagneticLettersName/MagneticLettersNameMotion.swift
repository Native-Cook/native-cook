import Foundation

/*
 Magnetic Letters Name — the timeline and the flight of one magnet, as pure functions of time.

 A magnet's pose is where its centre is (pt), its turn (rad), how far it is lifted off the door
 (0 = flat on the steel, 1 = held a finger's width away) and a uniform scale (the snap's bounce).
 autoPlay asks `autoPose(t)` for every magnet; under touch each magnet keeps a flight record
 (from, to, start, kind) and the same curves are asked with the time since that flight began.
*/

struct MagneticLettersNamePose: Equatable {
    var x = 0.0
    var y = 0.0
    var rot = 0.0
    var lift = 0.0
    var scale = 1.0
}

enum MagneticLettersNameMotion {
    typealias Pose = MagneticLettersNamePose

    /// The name the loop spells.
    static let script = Array("NOAH")
    /// Typing: the first letter leaves the pile at 1.3 s, one more every 0.42 s, each flight 0.38 s.
    static let typeStart = 1.3
    static let typeStagger = 0.42
    static let fly = 0.38
    /// "Continue" lights up at 3.6 s (0.25 s) and goes out with the first backspace.
    static let readyAt = 3.6
    /// Backspace: the last letter pops off at 5.2 s, one more every 0.45 s, each flight 0.45 s.
    static let popStart = 5.2
    static let popStagger = 0.45
    static let pop = 0.45
    /// The longest name the line holds.
    static let maxLetters = 10
    /// A drop after a drag: 0.2 s to fall back onto the steel.
    static let drop = 0.2

    enum Kind { case rest, snap, land, drop, held }

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

    /// The tiny bounce after contact: the scale dips ~5 % within 40 ms, overshoots ~2 %, and settles.
    static func contactBounce(_ since: Double) -> Double {
        if since <= 0 || since > 0.7 { return 1 }
        return 1 - 0.06 * exp(-since * 11) * sin(since * 38)
    }

    /// How a magnet's turn approaches 0 so it never spins the long way round.
    static func shortAngle(_ a: Double) -> Double {
        var v = a
        while v > .pi { v -= .pi * 2 }
        while v < -.pi { v += .pi * 2 }
        return v
    }

    /// Magnetic snap from `from` onto `to`: lifts, glides in an arc, and for the last 20 pt the
    /// door's pull takes it and it accelerates into place, then bounces. `u` = seconds since start.
    static func snapPose(_ from: Pose, _ to: Pose, _ u: Double, _ dur: Double) -> Pose {
        let tau = u / dur
        if tau <= 0 { return from }
        if tau >= 1 {
            let b = contactBounce(u - dur)
            return Pose(x: to.x, y: to.y + (1 - b) * 20, rot: to.rot, lift: 0, scale: b)
        }
        let dx = to.x - from.x
        let dy = to.y - from.y
        let dist = max(1, (dx * dx + dy * dy).squareRoot())
        let pull = min(20, dist * 0.3) / dist
        let kA = 0.8
        let s: Double
        if tau < kA {
            let x = tau / kA
            // Mostly an ease-in-out, kept 30 % linear so it is still moving when the pull takes over.
            s = (1 - pull) * (0.7 * x * x * (3 - 2 * x) + 0.3 * x)
        } else {
            let q = (tau - kA) / (1 - kA)
            let vA = ((1 - pull) * 0.3) / kA
            let v0 = clamp01((vA * (1 - kA)) / max(pull, 0.0001))
            s = 1 - pull + pull * (v0 * q + (1 - v0) * q * q)
        }
        let arc = min(64, dist * 0.28)
        let lift = tau < 0.2 ? easeOutCubic(tau / 0.2) : (tau < kA ? 1 : 1 - (tau - kA) / (1 - kA))
        let e = easeOutCubic(tau / 0.85)
        var out = Pose()
        out.x = from.x + dx * s
        out.y = from.y + dy * s - arc * sin(.pi * s)
        out.rot = shortAngle(from.rot) * (1 - e) + to.rot * e
        out.lift = max(lift, from.lift * (1 - tau))
        out.scale = 1 + 0.06 * out.lift
        return out
    }

    /// Popping off: the magnet presses in (60 ms), jumps free of the line, flies back in an arc
    /// (ease-in-out) and lands on the pile with the same bounce.
    static func landPose(_ from: Pose, _ to: Pose, _ u: Double, _ dur: Double) -> Pose {
        let tau = u / dur
        if tau <= 0 { return from }
        if tau >= 1 {
            let b = contactBounce(u - dur)
            return Pose(x: to.x, y: to.y + (1 - b) * 20, rot: to.rot, lift: 0, scale: b)
        }
        let dx = to.x - from.x
        let dy = to.y - from.y
        let dist = (dx * dx + dy * dy).squareRoot()
        let press = 0.13
        if tau < press {
            let k = sin(.pi * tau / press)
            return Pose(x: from.x, y: from.y + k * 1.5, rot: from.rot, lift: from.lift, scale: 1 - 0.03 * k)
        }
        let x = (tau - press) / (1 - press)
        let s = easeInOutCubic(x)
        let arc = min(70, 26 + dist * 0.22)
        let lift = x < 0.25 ? easeOutCubic(x / 0.25) : (x < 0.8 ? 1 : 1 - (x - 0.8) / 0.2)
        var out = Pose()
        out.x = from.x + dx * s
        out.y = from.y + dy * s - arc * sin(.pi * min(1, s * 1.08))
        out.rot = from.rot + (to.rot - from.rot) * s + sin(.pi * s) * 0.35
        out.lift = max(lift, from.lift * (1 - x))
        out.scale = 1 + 0.07 * out.lift
        return out
    }

    /// Let go away from the line: the magnet falls back onto the steel (accelerating) and bounces.
    static func dropPose(_ from: Pose, _ u: Double) -> Pose {
        let tau = u / drop
        var out = Pose(x: from.x, y: from.y, rot: from.rot, lift: 0, scale: 1)
        if tau >= 1 {
            let b = contactBounce(u - drop)
            out.y = from.y + (1 - b) * 20
            out.scale = b
        } else {
            out.lift = from.lift * (1 - tau * tau)
            out.scale = 1 + 0.06 * out.lift
        }
        return out
    }

    /// Where script letter k is in the loop: 0 on the pile, 1 flying in / on the line, 2 popping off.
    static func autoPhase(_ k: Int, _ t: Double) -> (phase: Int, since: Double) {
        let typeAt = typeStart + typeStagger * Double(k)
        let popAt = popStart + popStagger * Double(script.count - 1 - k)
        if t < typeAt { return (0, 0) }
        if t < popAt { return (1, t - typeAt) }
        return (2, t - popAt)
    }

    /// "Continue" in the loop: lit from 3.6 s to the first backspace.
    static func autoReady(_ t: Double) -> Double {
        easeOutCubic((t - readyAt) / 0.25) * (1 - easeOutCubic((t - popStart) / 0.2))
    }

    /// How many letters the loop's line holds at t (counted when each lands).
    static func autoCount(_ t: Double) -> Int {
        var n = 0
        for k in 0..<script.count {
            let p = autoPhase(k, t)
            if p.phase == 1 && p.since >= fly { n += 1 }
        }
        return n
    }
}
