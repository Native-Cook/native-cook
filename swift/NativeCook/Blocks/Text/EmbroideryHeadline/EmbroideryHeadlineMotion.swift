import Foundation

/*
 Embroidery Headline — the autoPlay script, a pure function of the loop time t.

   0.0 – 1.2   rest: "Slow made." satin-stitched on linen, the subheading below.
   1.2 – 2.2   the needle passes left to right and unpicks the letters (each in reverse, ~0.12 s a
               letter); the subheading fades out (1.2 – 1.6).
   1.75 – 6.6  close behind it the letters are stitched again, one after another, each taking time
               in proportion to its stitches: parallel satin threads laid across the letter at its
               own angle.
   6.6 – 7.0   the full stop is a French knot: it pops up with a spring.
   6.9 – 7.5   the subheading rises in (ease-out); hold to 9.0 = rest.
 */

enum EmbroideryHeadlineMotion {
    private static let unpickFrom = 1.2
    private static let unpickTo = 2.2
    private static let stitchFrom = 1.75
    private static let stitchTo = 6.6
    static let knotFrom = 6.6
    static let knotTo = 7.0

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    static func easeInOut(_ u: Double) -> Double { u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2 }
    static func easeOut(_ u: Double) -> Double { 1 - pow(1 - u, 3) }

    /// A spring-like pop for the knot: 0.55 → 1 with one soft overshoot.
    static func pop(_ u: Double) -> Double {
        if u <= 0 { return 0.55 }
        if u >= 1 { return 1 }
        return 1 - 0.45 * exp(-6 * u) * cos(u * 9.5)
    }

    /// Per glyph, how many of its satin threads are laid at t. Unpicking runs letter by letter left
    /// to right, each letter's threads coming out last-laid first; stitching follows, each letter's
    /// share of the time in proportion to its threads.
    static func glyphPresent(_ t: Double, _ counts: [Double]) -> [Double] {
        let n = Double(counts.count)
        let total = max(1, counts.reduce(0, +))
        var out: [Double] = []
        var acc = 0.0
        for (i, c) in counts.enumerated() {
            let di = Double(i)
            let u0 = unpickFrom + (unpickTo - unpickFrom) * di / n
            let u1 = unpickFrom + (unpickTo - unpickFrom) * (di + 1) / n
            let s0 = stitchFrom + (stitchTo - stitchFrom) * acc / total
            let s1 = stitchFrom + (stitchTo - stitchFrom) * (acc + c) / total
            acc += c
            if t < u0 { out.append(c) } else if t < s0 { out.append(c * (1 - easeInOut(seg(t, u0, u1)))) } else { out.append(c * seg(t, s0, s1)) }
        }
        return out
    }

    /// The knot: (scale, alpha).
    static func knotState(_ t: Double) -> (Double, Double) {
        if t >= unpickFrom && t < knotFrom {
            let out = seg(t, unpickTo - 0.15, unpickTo)
            return (1 - 0.3 * out, 1 - out)
        }
        let u = seg(t, knotFrom, knotTo)
        return (t < unpickFrom ? 1 : pop(u), t < unpickFrom ? 1 : min(1, u * 4))
    }

    /// The subheading: (alpha, rise in pt).
    static func subheading(_ t: Double) -> (Double, Double) {
        let out = easeInOut(seg(t, 1.2, 1.6))
        let back = easeOut(seg(t, 6.9, 7.5))
        return (t < 6.9 ? 1 - out : back, t < 6.9 ? 0 : (1 - back) * 10)
    }

    /// Whether the needle is out (1) or resting off the work (0), with smooth hand-offs.
    static func needleOut(_ t: Double) -> Double {
        easeInOut(seg(t, 1.0, 1.3)) * (1 - easeInOut(seg(t, 7.0, 7.5)))
    }
}
