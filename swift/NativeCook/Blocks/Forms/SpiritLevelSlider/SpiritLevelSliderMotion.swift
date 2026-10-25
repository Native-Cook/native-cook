import Foundation

/*
 Spirit Level Slider — the tilt timeline and the bubble's physics, a port of spiritMotion.ts.

 autoPlay (loop = 8 s), level angle θ (degrees, right end up is positive):
   0.0 – 1.2   rest at 0°.
   1.2 – 3.0   tilt to 28° (ease-in-out, a 3 % dip the other way first: anticipation).
   3.0 – 4.4   hold 28° (6/12, Standard).
   4.4 – 5.4   overshoot up to 40° (ease-in-out).
   5.4 – 6.4   back down to 18° (ease-in-out).
   6.4 – 7.6   return to 0° (ease-in-out).
   7.6 – 8.0   rest.
 The bubble runs to the higher end of the vial on a spring (ω 7 rad/s, ζ 0.42), integrated over
 four loops and the last kept, so the loop is exact.
 */
enum SpiritLevelSliderMotion {
    static let loop = 8.0
    static let minAngle = -6.0
    static let maxAngle = 48.0
    static let bubbleOmega = 7.0
    static let bubbleZeta = 0.42
    static let tableRate = 120.0

    static let pitchWords = ["Flat", "Low slope", "Standard", "Steep"]
    static let pitchBounds: [Double] = [5, 18, 37]

    static func clamp(_ v: Double, _ a: Double, _ b: Double) -> Double { min(max(v, a), b) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }

    static func easeInOut(_ x: Double) -> Double {
        if x < 0.5 { return 4 * x * x * x }
        let y = -2 * x + 2
        return 1 - y * y * y / 2
    }

    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp((x - a) / (b - a), 0, 1)
        return t * t * (3 - 2 * t)
    }

    /// The level's angle at loop time t.
    static func angleAt(_ t: Double) -> Double {
        if t < 1.2 { return 0 }
        if t < 3.0 {
            let u = seg(t, 1.2, 3.0)
            // A small dip before the lift (anticipation), gone by a quarter of the move.
            let dip = -0.9 * sin(Double.pi * clamp(u / 0.25, 0, 1)) * (1 - u)
            return 28 * easeInOut(u) + dip
        }
        if t < 4.4 { return 28 }
        if t < 5.4 { return mix(28, 40, easeInOut(seg(t, 4.4, 5.4))) }
        if t < 6.4 { return mix(40, 18, easeInOut(seg(t, 5.4, 6.4))) }
        if t < 7.6 { return mix(18, 0, easeInOut(seg(t, 6.4, 7.6))) }
        return 0
    }

    /// Where the bubble wants to sit for a tilt, as a share of its travel (−1 … 1, past 1 = pinned).
    static func bubbleGoal(_ angle: Double) -> Double {
        clamp(sin(angle * .pi / 180) / sin(42 * Double.pi / 180), -1.15, 1.15)
    }

    /// The bubble's [position, velocity] over one loop, periodic.
    static let bubbleTable: [(Double, Double)] = {
        let n = Int(loop * tableRate)
        let sub = 4
        let dt = 1 / (tableRate * Double(sub))
        var x = 0.0
        var v = 0.0
        var out = [(Double, Double)](repeating: (0, 0), count: n)
        for pass in 0..<4 {
            for i in 0..<n {
                for s in 0..<sub {
                    let target = bubbleGoal(angleAt(Double(i * sub + s) * dt))
                    let a = -bubbleOmega * bubbleOmega * (x - target) - 2 * bubbleZeta * bubbleOmega * v
                    v += a * dt
                    x += v * dt
                }
                if pass == 3 { out[i] = (x, v) }
            }
        }
        return out
    }()

    static func sampleBubble(_ t: Double) -> (Double, Double) {
        let table = bubbleTable
        let n = table.count
        let x = (t.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop) * tableRate
        let i = Int(floor(x))
        let f = x - Double(i)
        let a = table[i % n], b = table[(i + 1) % n]
        return (mix(a.0, b.0, f), mix(a.1, b.1, f))
    }

    static func stepBubble(_ x0: Double, _ v0: Double, target: Double, dt: Double) -> (Double, Double) {
        let steps = max(1, Int(ceil(dt * 240)))
        let h = dt / Double(steps)
        var x = x0
        var v = v0
        for _ in 0..<steps {
            let a = -bubbleOmega * bubbleOmega * (x - target) - 2 * bubbleZeta * bubbleOmega * v
            v += a * h
            x += v * h
        }
        return (x, v)
    }

    /// The roof's rise per 12 of run for a tilt (0 … 13).
    static func riseOf(_ angle: Double) -> Double {
        12 * tan(clamp(angle, 0, 50) * .pi / 180)
    }

    /// The pitch word as a continuous index, rolling over ±1.5° around each boundary.
    static func pitchIndex(_ angle: Double) -> Double {
        var k = 0.0
        for b in pitchBounds { k += smooth(b - 1.5, b + 1.5, angle) }
        return k
    }

    static func softRound(_ v: Double) -> Double {
        let f = floor(v)
        return f + smooth(0.35, 0.65, v - f)
    }

    /// Rubber band past the tilt limits.
    static func rubber(_ angle: Double) -> Double {
        if angle > maxAngle { return maxAngle + (angle - maxAngle) / (1 + (angle - maxAngle) / 8) }
        if angle < minAngle { return minAngle - (minAngle - angle) / (1 + (minAngle - angle) / 8) }
        return angle
    }
}
