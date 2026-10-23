import Foundation

/// The block's clock: every value on screen is a function of time, in closed form, so the loop can
/// be drawn from the timeline date without writing state. Angles are degrees, clockwise positive;
/// times are seconds.
enum VinylSwapScratchMotion {
    static let loop = 10.0
    /// About 33⅓ rpm; the real speed is rounded so the platter turns a whole number of times a loop.
    static let nominalSpeed = 200.0

    // The scripted scratch.
    static let stopAt = 1.5
    static let stopFor = 0.12
    static let strokes: [(start: Double, duration: Double, amp: Double)] = [(1.62, 0.6, 55), (2.22, 0.5, 75), (2.72, 0.44, 95)]
    static let releaseAt = 3.2

    // The two record changes: A to B, then B back to A.
    static let swaps: [(at: Double, from: Int, to: Int)] = [(4.6, 0, 1), (7.7, 1, 0)]
    static let swapLength = 1.8

    /// Waveform bars that pass the playhead per turn of the platter.
    static let barsPerTurn = 18.0
    /// Every ninth bar is a kick (twice a turn).
    static let kickEvery = 9

    // The motor: a spring of ω 12, ζ 0.45 on the platter's speed (about 20 % overshoot).
    private static let motorA = 0.45 * 12
    private static let motorB = 12 * (1 - 0.45 * 0.45).squareRoot()

    /// e^(−at)(cos bt + a/b sin bt): 1 at 0, decaying to 0 with an overshoot.
    static func motorResponse(_ t: Double) -> Double {
        exp(-motorA * t) * (cos(motorB * t) + motorA / motorB * sin(motorB * t))
    }

    private static func motorF(_ x: Double) -> Double {
        let a = motorA, b = motorB
        return exp(-a * x) * ((b - a * a / b) * sin(b * x) - 2 * a * cos(b * x)) / (a * a + b * b)
    }

    /// ∫₀ᵗ motorResponse.
    static func motorIntegral(_ t: Double) -> Double { motorF(t) - motorF(0) }

    /// The motor pulling the platter from speed v0 to `target`: angle travelled and speed after t.
    static func approach(from v0: Double, to target: Double, _ t: Double) -> (angle: Double, speed: Double) {
        (target * t + (v0 - target) * motorIntegral(t), target + (v0 - target) * motorResponse(t))
    }

    /// The platter braking from v0 to rest over `duration` (speed falls as (1 − u)²).
    static func brake(from v0: Double, duration: Double, _ t: Double) -> (angle: Double, speed: Double) {
        let u = min(max(t / duration, 0), 1)
        return (v0 * duration * (1 - pow(1 - u, 3)) / 3, v0 * (1 - u) * (1 - u))
    }

    /// The loop's platter, in units of the motor speed: ∫ speed and speed.
    static func shape(_ t: Double) -> (integral: Double, speed: Double) {
        if t < stopAt { return (t, 1) }
        if t < releaseAt {
            let b = brake(from: 1, duration: stopFor, t - stopAt)
            return (stopAt + b.angle, b.speed)
        }
        let r = approach(from: 0, to: 1, t - releaseAt)
        return (stopAt + stopFor / 3 + r.angle, r.speed)
    }

    /// Whole turns of the platter per loop.
    static let turns: Int = Int((nominalSpeed * shape(loop).integral / 360).rounded())
    /// The motor speed, in degrees a second.
    static let omega: Double = Double(turns) * 360 / shape(loop).integral
    /// Bars before the waveform repeats: one loop's worth.
    static let period: Int = Int(barsPerTurn) * turns

    /// The scripted hand: three back-and-forth strokes, pulling the record back and pushing it on.
    static func scratch(_ t: Double) -> (angle: Double, speed: Double) {
        for s in strokes where t >= s.start && t < s.start + s.duration {
            let u = (t - s.start) / s.duration
            return (-s.amp * (1 - cos(2 * .pi * u)) / 2, -s.amp * .pi / s.duration * sin(2 * .pi * u))
        }
        return (0, 0)
    }

    // MARK: Curves

    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    static func easeInOut(_ x: Double) -> Double {
        let t = clamp01(x)
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }
    static func easeIn(_ x: Double) -> Double { pow(clamp01(x), 3) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }

    /// A spring step from 0 to 1 (ω 11, ζ 0.62): the new record lands with a small overshoot.
    static func landing(_ t: Double) -> Double {
        let w = 11.0, z = 0.62
        let a = z * w, b = w * (1 - z * z).squareRoot()
        return 1 - exp(-a * t) * (cos(b * t) + a / b * sin(b * t))
    }

    // MARK: Pose

    /// What the screen shows at a moment.
    struct Pose {
        /// The platter's angle and speed (degrees, degrees a second).
        var angle = 0.0
        var speed = 0.0
        /// Each record's place: 0 on the platter, −1 gone off the left edge, 1 waiting off the right.
        var discX = [0.0, 1.0]
        var discAlpha = [1.0, 0.0]
        /// 0 with the stylus in the groove, 1 swung out beside the record.
        var arm = 0.0
        /// 0 down, 1 raised (a bigger, softer shadow).
        var lift = 0.0
        /// Colours: 0 album A, 1 album B.
        var mix = 0.0
        var waveMix = 0.0
        /// The waveform's loudness: low while the stylus is out of the groove.
        var needle = 1.0
        var titleAlpha = [1.0, 0.0]
        var titleX = [0.0, 0.0]
        /// The scripted finger on the record: opacity, scale and how far it has turned the record.
        var fingerAlpha = 0.0
        var fingerScale = 1.0
        var fingerTurn = 0.0
        /// The pills pressed by the script: pause, previous, next.
        var press = [0.0, 0.0, 0.0]
        /// The time used for flickers.
        var clock = 0.0

        static func rest(_ album: Int) -> Pose {
            var p = Pose()
            p.discX = album == 0 ? [0, 1] : [1, 0]
            p.discAlpha = album == 0 ? [1, 0] : [0, 1]
            p.mix = Double(album)
            p.waveMix = Double(album)
            p.titleAlpha = album == 0 ? [1, 0] : [0, 1]
            return p
        }
    }

    /// A record change `e` seconds in. Reduced motion: a 0.3 s cross-fade, nothing moves.
    static func swap(_ p: inout Pose, _ e: Double, from: Int, to: Int, still: Bool) {
        let dir = to > from ? 1.0 : -1.0
        func toward(_ m: Double) -> Double { Double(from) + dir * m }
        if still {
            let a = smoothstep(0, 0.3, e)
            p.discX[from] = 0; p.discX[to] = 0
            p.discAlpha[from] = 1 - a; p.discAlpha[to] = a
            p.mix = toward(a); p.waveMix = toward(a)
            p.titleAlpha[from] = 1 - a; p.titleAlpha[to] = a
            p.titleX = [0, 0]
            return
        }
        p.arm = e < 1.3 ? easeInOut(e / 0.4) : 1 - easeOut((e - 1.3) / 0.5)
        p.lift = smoothstep(0, 0.25, e) * (1 - smoothstep(1.55, 1.8, e))
        p.needle = clamp01(1 - smoothstep(0.05, 0.3, e) + smoothstep(1.6, 1.8, e))
        p.discX[from] = e < 0.3 ? 0 : -easeIn((e - 0.3) / 0.5)
        p.discX[to] = e < 0.6 ? 1 : e >= 1.5 ? 0 : 1 - landing(e - 0.6)
        p.discAlpha[from] = 1; p.discAlpha[to] = 1
        p.mix = toward(smoothstep(0.4, 1.4, e))
        p.waveMix = toward(smoothstep(0.6, 1.3, e))
        let out = smoothstep(0.45, 0.75, e)
        p.titleAlpha[from] = 1 - out
        p.titleX[from] = -14 * out
        p.titleAlpha[to] = smoothstep(0.7, 1.05, e)
        p.titleX[to] = 14 * (1 - easeOut((e - 0.7) / 0.35))
    }

    /// A scripted press of a pill around time `at`.
    static func bump(_ t: Double, _ at: Double) -> Double {
        smoothstep(at - 0.18, at - 0.05, t) * (1 - smoothstep(at + 0.05, at + 0.25, t))
    }

    /// The loop at time t (0 ≤ t < loop).
    static func loopPose(_ t: Double) -> Pose {
        var p = Pose.rest(0)
        let s = shape(t), k = scratch(t)
        p.angle = omega * s.integral + k.angle
        p.speed = omega * s.speed + k.speed
        p.clock = t
        let a = swaps[0], b = swaps[1]
        if t >= a.at && t < a.at + swapLength {
            swap(&p, t - a.at, from: a.from, to: a.to, still: false)
        } else if t >= a.at + swapLength && t < b.at {
            let angle = p.angle, speed = p.speed
            p = Pose.rest(1)
            p.angle = angle; p.speed = speed; p.clock = t
        } else if t >= b.at && t < b.at + swapLength {
            swap(&p, t - b.at, from: b.from, to: b.to, still: false)
        }
        p.fingerAlpha = smoothstep(1.3, 1.5, t) * (1 - smoothstep(releaseAt, releaseAt + 0.2, t))
        p.fingerScale = 1.25 - 0.25 * smoothstep(1.3, 1.5, t) + 0.15 * smoothstep(releaseAt, releaseAt + 0.2, t)
        p.fingerTurn = k.angle
        p.press = [0, bump(t, b.at), bump(t, a.at)]
        return p
    }

    /// Which record is on the platter (or leaving it) at loop time t, and the change in progress.
    static func loopState(_ t: Double) -> (album: Int, swap: (start: Double, from: Int, to: Int)?) {
        for s in swaps where t >= s.at && t < s.at + swapLength { return (s.from, (s.at, s.from, s.to)) }
        return (t >= swaps[0].at && t < swaps[1].at ? 1 : 0, nil)
    }

    // MARK: Waveform

    /// A small integer hash (PCG), the same in every stack: 0 to 1.
    static func hash(_ x: Int, _ salt: Int) -> Double {
        var h = UInt32(truncatingIfNeeded: x) &* 747_796_405 &+ UInt32(truncatingIfNeeded: salt) &* 2_891_336_453 &+ 1
        h = ((h >> ((h >> 28) &+ 4)) ^ h) &* 277_803_737
        h = (h >> 22) ^ h
        return Double(h) / 4_294_967_295
    }

    /// Bar k's height (0 to 1) in album `album`'s waveform; repeats every `period` bars.
    static func bar(_ album: Int, _ k: Int) -> Double {
        let i = ((k % period) + period) % period
        if i % kickEvery == 0 { return 0.95 }
        let x = Double(i) / Double(period)
        let seed = album == 0 ? 0.0 : 2.1
        let env = 0.5 + 0.22 * sin(2 * .pi * 2 * x + seed) + 0.14 * sin(2 * .pi * 5 * x + seed * 1.7)
        let h = env * (0.45 + 0.55 * hash(i, album + 1))
        return min(max(h, 0.1), 0.88)
    }

    static func isKick(_ k: Int) -> Bool { ((k % period) + period) % period % kickEvery == 0 }
}
