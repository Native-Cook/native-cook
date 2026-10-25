import Foundation

/*
 The rope and its knot, as closed-form functions of time.

 The rope is pinned at both screen edges along the top of the tab bar. Its height at x (u = x / w):
   y = slack sag  S0 · sin(πu)
     + the knot's weight: a tent with its point at the knot, deepest when the knot is mid-span,
       K · 4·uk·(1 − uk) · (u / uk, or (1 − u) / (1 − uk) past the knot)
     + whip: the first four string modes sin(nπu), excited by "kicks" (a time, a place, a size)
       and ringing down: a / n · sin(nπ·u_kick) · sin(ω_n τ) · e^(−τ / 0.5), with ω_n = n · 2π · 1.8.
       A kick fades to exactly 0 between 1.4 and 1.9 s after it, so the loop closes on itself.

 autoPlay (loop = 8 s): the knot's place p (0…3, a tab index) is a sum of spring steps (ω 7.5,
 ζ 0.78, ~1 % overshoot, settled in ~1 s):
   0.0 – 1.3   rest on Explore.
   1.3 – 2.6   to Gear (p 0 → 2): the rope lifts where the knot left, dips and rings where it lands.
   2.6 – 3.6   hold.
   3.6 – 4.6   to Routes (2 → 1).
   4.6 – 5.6   hold.
   5.6 – 6.8   back to Explore (1 → 0), with a harder whip.
   6.8 – 8.0   rest (the last kick rings out by 7.95).
 A tap ripple marks each move's tab, as if a finger tapped it.
 */
enum RopeTabBarMotion {
    static let loop = 8.0
    static let tabs = 4
    static let sag = 4.0
    static let knotWeight = 7.0
    private static let whipHz = 1.8
    private static let whipDecay = 0.5
    static let knotW = 7.5
    static let knotZ = 0.78
    /// How long after the start of a move its landing kick comes (s).
    static let landAfter = 0.42

    /// (start, from, to)
    static let loopMoves: [(Double, Double, Double)] = [
        (1.3, 0, 2),
        (3.6, 2, 1),
        (5.6, 1, 0),
    ]

    static func tabU(_ i: Double) -> Double {
        (i + 0.5) / Double(tabs)
    }

    /// Kicks: (time, u, size in pt, + = down).
    static let loopKicks: [RopeTabBarKick] = {
        var out: [RopeTabBarKick] = []
        for (i, m) in loopMoves.enumerated() {
            let d = pow(abs(m.2 - m.1), 0.6)
            let hard = i == loopMoves.count - 1 ? 1.45 : 1
            out.append(RopeTabBarKick(time: m.0 + 0.05, u: tabU(m.1), size: -3.2 * d * hard))
            out.append(RopeTabBarKick(time: m.0 + landAfter, u: tabU(m.2), size: 6 * d * hard))
        }
        return out
    }()

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(max((t - a) / (b - a), 0), 1)
    }

    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// Step response of a spring from rest: position 0 → 1.
    static func springStep(_ tau: Double, _ w: Double, _ z: Double) -> Double {
        if tau <= 0 { return 0 }
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * tau) * (cos(wd * tau) + z * w / wd * sin(wd * tau))
    }

    /// The loop's knot place p at loop time t.
    static func loopKnot(_ t: Double) -> Double {
        var p = 0.0
        for m in loopMoves {
            p += (m.2 - m.1) * springStep(t - m.0, knotW, knotZ)
        }
        return t > 7.9 ? 0 : p
    }

    /// The rope's height below its pinned line at u, for a knot at uk, from kicks at time t.
    static func ropeAt(_ u: Double, _ uk: Double, _ kicks: [RopeTabBarKick], _ t: Double, _ gain: Double) -> Double {
        var y = sag * sin(Double.pi * u)
        let depth = knotWeight * 4 * uk * (1 - uk)
        y += depth * (u < uk ? u / max(uk, 0.001) : (1 - u) / max(1 - uk, 0.001))
        for k in kicks {
            let tau = t - k.time
            if tau <= 0 || tau >= 1.9 { continue }
            let env = exp(-tau / whipDecay) * (1 - easeInOut(seg(tau, 1.4, 1.9))) * k.size * gain
            for n in 1...4 {
                let nn = Double(n)
                y += env / nn * sin(nn * .pi * k.u) * sin(nn * .pi * u) * sin(nn * 2 * .pi * whipHz * tau)
            }
        }
        return y
    }

    /// A tap ripple: 0 → 1 over its life, or −1 when none is showing.
    static func rippleAt(_ t: Double, _ start: Double) -> Double {
        let tau = t - start
        if tau < -0.12 || tau > 0.55 { return -1 }
        return seg(tau, -0.12, 0.55)
    }
}

struct RopeTabBarKick {
    var time: Double
    var u: Double
    var size: Double
}
