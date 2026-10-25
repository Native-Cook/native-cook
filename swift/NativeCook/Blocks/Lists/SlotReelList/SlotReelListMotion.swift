import Foundation

/*
 Slot Reel List — the loop as pure functions of t. A reel's position is an offset in rows (row k of
 its list sits on the payline when the offset is k, mod the list's length); the lever is an angle.

 loop = 9 s: rest 0–1.3 · lever pulled 1.3–1.9 (down 0.35 s, released at 1.9, springs back) ·
 spin 1.9–4.8 (reels stop at 3.6, 4.1, 4.6, each landing with a clunk) · win sweep 4.8–5.8 ·
 hold 5.8–7.0 · reels roll on to their resting rows 7.0–8.4 (1 s each, 0.15 s apart) · rest.
*/
enum SlotReelListMotion {
    /// Dishes per reel.
    static let rows = 8
    /// The rows on the payline at rest, and the loop's winning combination.
    static let restRows = [2, 5, 1]
    static let winRows = [0, 0, 0]
    /// Whole turns of the list each reel makes before stopping (later reels spin longer).
    static let turns = [3, 4, 5]
    static let pullAt = 1.3
    static let pullDown = 0.35
    static let spinAt = 1.9
    static let stops = [3.6, 4.1, 4.6]
    static let sweepAt = 4.8
    static let sweepFor = 1.0
    static let returnAt = 7.0
    static let returnFor = 1.0
    static let returnStagger = 0.15
    /// The lever's travel: from upright (0) to pointing down past the viewer.
    static let leverMax = Double.pi * 0.82

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

    static func hash(_ n: Double) -> Double {
        let v = sin(n * 127.1 + 311.7) * 43758.5453
        return v - floor(v)
    }

    /// Positive modulo.
    static func wrap(_ v: Double, _ n: Double) -> Double { v - n * floor(v / n) }

    /*
     One spin: rows travelled after `tau` seconds of a spin of `dur` seconds covering `dist` rows.
     The speed ramps up over the first 15 %, cruises, and falls over the last 40 % to 18 % of its
     peak, so the reel still carries speed when the stop catches it: then it overshoots and settles
     on a damped spring (ω 26 rad/s, ζ 0.3) — the clunk of a reel landing.
    */
    private static let a = 0.15
    private static let d = 0.4
    private static let e = 0.18
    private static let s1 = 1 - a / 2 - (1 - e) * d / 2
    private static let omega = 26.0
    private static let zeta = 0.3

    static func spinRows(_ tau: Double, _ dur: Double, _ dist: Double) -> Double {
        if tau <= 0 { return 0 }
        if tau < dur {
            let u = tau / dur
            let s: Double
            if u < a {
                s = u * u / (2 * a)
            } else if u < 1 - d {
                s = a / 2 + (u - a)
            } else {
                let w = u - (1 - d)
                s = a / 2 + (1 - d - a) + w - (1 - e) * w * w / (2 * d)
            }
            return dist * s / s1
        }
        let ve = dist * e / (s1 * dur)
        let k = tau - dur
        let wd = omega * (1 - zeta * zeta).squareRoot()
        return dist + ve / wd * exp(-zeta * omega * k) * sin(wd * k)
    }

    /// Rows a reel travels to go from row `from` to row `to`, after `turns` whole turns.
    static func spinDistance(_ from: Double, _ to: Double, _ turns: Int) -> Double {
        Double(turns * rows) + wrap(to - from, Double(rows))
    }

    /// Reel c's offset in the loop.
    static func autoOffset(_ c: Int, _ t: Double) -> Double {
        let start = spinAt + 0.06 * Double(c)
        let dist = spinDistance(Double(restRows[c]), Double(winRows[c]), turns[c])
        var o = Double(restRows[c]) + spinRows(t - start, stops[c] - start, dist)
        let back = returnAt + returnStagger * Double(c)
        if t > back {
            o += wrap(Double(restRows[c] - winRows[c]), Double(rows)) * easeInOutCubic((t - back) / returnFor)
        }
        return o
    }

    /// A released lever: a playful spring back to upright (ζ 0.42), from angle a0.
    static func leverSpring(_ k: Double, _ a0: Double) -> Double {
        let w = 15.0
        let z = 0.42
        let wd = w * (1 - z * z).squareRoot()
        let env = exp(-z * w * k)
        return a0 * env * (cos(wd * k) + z * w / wd * sin(wd * k))
    }

    /// The lever in the loop: pulled down (ease-in-out), let go, and springing back past upright.
    static func autoLever(_ t: Double) -> Double {
        if t < pullAt { return 0 }
        if t < pullAt + pullDown { return leverMax * easeInOutCubic((t - pullAt) / pullDown) }
        if t < spinAt { return leverMax }
        return leverSpring(t - spinAt, leverMax)
    }

    /// The win: payline glow (up at 4.8, down after 7.0) and the light band's progress (4.8–5.8).
    static func autoWin(_ t: Double) -> (glow: Double, sweep: Double) {
        let glow = easeOutCubic((t - sweepAt) / 0.2) * (1 - easeOutCubic((t - returnAt) / 0.3))
        let sweep = t >= sweepAt && t <= sweepAt + sweepFor ? easeInOutCubic((t - sweepAt) / sweepFor) : -1
        return (glow, sweep)
    }

    /// A damped spring (ζ 0.55, settling in ~0.65 s) from x0 with velocity v0 (rows/s) to `target`.
    static func snapSpring(_ k: Double, from x0: Double, velocity v0: Double, to target: Double) -> Double {
        let w0 = 11.0
        let z = 0.55
        let wd = w0 * (1 - z * z).squareRoot()
        let a = x0 - target
        let b = (v0 + z * w0 * a) / wd
        return target + exp(-z * w0 * k) * (a * cos(wd * k) + b * sin(wd * k))
    }
}
