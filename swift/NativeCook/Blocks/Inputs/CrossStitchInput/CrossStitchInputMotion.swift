import Foundation

/*
 Cross Stitch Input — the autoPlay script. Every value is a pure function of the loop time t, so
 the frame at t = loop is the frame at 0.

   0.0 – 1.2   rest: "Hi" worked in indigo, the needle parked in the cloth at the lower right.
   1.0 – 1.3   the needle comes up to the last stitch; 1.2 – 2.2 it unpicks "Hi" in reverse,
               legs pulling out one after the other. The field is erased (1.35, 1.5).
   1.95        the Madder chip is chosen; "June" is typed (1.95 – 2.45).
   2.0 – 5.6   the needle works "June", stitch by stitch: bottom-left to top-right, then bottom-right
               to top-left, rows worked back and forth.
   5.6 – 5.85  a knot: the needle loops once round the last hole; 5.85 – 6.35 it goes back to rest.
   6.2 – 7.2   hold.
   7.2 – 8.5   back to the start: "June" pulls out (7.2 – 7.9) while "Hi" is worked again in
               indigo in a quick time-lapse (7.6 – 8.5), the field reads "Hi" again.
   8.5 – 9.0   rest.
 */

enum CrossStitchInputMotion {
    static let restText = "Hi"
    static let secondText = "June"
    static let restThread = 1
    static let secondThread = 0

    /// The field's text over the loop: (from t, text).
    static let textScript: [(Double, String)] = [
        (0, "Hi"), (1.35, "H"), (1.5, ""), (1.95, "J"), (2.12, "Ju"), (2.29, "Jun"), (2.46, "June"),
        (7.2, "Jun"), (7.3, "Ju"), (7.4, "J"), (7.5, ""), (7.62, "H"), (7.76, "Hi"),
    ]

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    static func easeInOut(_ u: Double) -> Double { u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2 }
    static func easeOut(_ u: Double) -> Double { 1 - pow(1 - u, 3) }

    /// Ease-out with a small overshoot (about 4 %), for the chip ring.
    static func easeOutBack(_ u: Double) -> Double {
        let c1 = 0.9
        let v = u - 1
        return 1 + (c1 + 1) * v * v * v + c1 * v * v
    }

    /// Near-constant stitching pace that still starts and ends softly.
    private static func pace(_ u: Double) -> Double { 0.6 * u + 0.4 * easeInOut(u) }

    static func scriptIndex(_ t: Double) -> Int {
        var i = 0
        for (k, e) in textScript.enumerated() where t >= e.0 { i = k }
        return i
    }

    /// How many of "Hi"'s n stitches are on the cloth (fractional: the one being worked or pulled).
    static func restPresent(_ t: Double, _ n: Double) -> Double {
        if t < 1.2 { return n }
        if t < 2.2 { return n * (1 - easeInOut(seg(t, 1.2, 2.2))) }
        if t < 7.6 { return 0 }
        if t < 8.5 { return n * pace(seg(t, 7.6, 8.5)) }
        return n
    }

    static func secondPresent(_ t: Double, _ n: Double) -> Double {
        if t < 2.0 { return 0 }
        if t < 5.6 { return n * pace(seg(t, 2.0, 5.6)) }
        if t < 7.2 { return n }
        if t < 7.9 { return n * (1 - easeInOut(seg(t, 7.2, 7.9))) }
        return 0
    }

    /// The chip ring's position (thread index, fractional while it moves).
    static func chipPosition(_ t: Double) -> Double {
        let a = easeOutBack(seg(t, 1.95, 2.3))
        let b = easeOutBack(seg(t, 7.45, 7.8))
        return Double(restThread) + Double(secondThread - restThread) * a + Double(restThread - secondThread) * b
    }

    /// 0 → the counter's total is "Hi"'s, 1 → "June"'s.
    static func totalBlend(_ t: Double) -> Double { easeInOut(seg(t, 1.95, 2.35)) * (1 - easeInOut(seg(t, 7.45, 7.85))) }

    /// 0 → indigo, 1 → madder (the name's thread in the loop's two halves).
    static func threadBlend(_ t: Double) -> Double { easeInOut(seg(t, 1.9, 2.1)) * (1 - easeInOut(seg(t, 7.4, 7.6))) }

    /// The needle's script as blend weights: (park, follow "Hi", follow "June", knot phase 0..1, lift).
    static func needleScript(_ t: Double) -> (Double, Double, Double, Double, Double) {
        let toWork = easeInOut(seg(t, 1.0, 1.3))
        let handOff = easeInOut(seg(t, 1.95, 2.2))
        let home = easeInOut(seg(t, 5.85, 6.35))
        let knot = seg(t, 5.6, 5.85)
        let park = 1 - toWork + toWork * home
        let work = toWork * (1 - home)
        // Lifted while it travels to and from the work; resting stuck in the cloth otherwise.
        let lift = sin(.pi * seg(t, 1.0, 1.3)) * 0.6 + sin(.pi * seg(t, 5.85, 6.35)) * 0.8
        return (park, work * (1 - handOff), work * handOff, knot, lift)
    }
}

/// The name being typed by the user.
struct CrossStitchInputUserName {
    var layout: CrossStitchInputLayout
    var typedAt: [Double]
    /// Per glyph: where it was, in cells, relative to where it is now, and since when it glides.
    var fromX: [Double]
    var fromY: [Double]
    var movedAt: Double
    var thread: Int
    var threadFrom: CrossStitchInputRGB
    var threadAt: Double

    static let glide = 0.35

    static func stitchDuration(_ count: Int) -> Double { 0.15 + 0.055 * Double(count) }

    /// Per glyph, how many of its stitches are worked at `now`.
    func glyphPresent(_ now: Double) -> [Double] {
        layout.count.indices.map { i in
            let c = Double(layout.count[i])
            return c * min(1, max(0, (now - typedAt[i]) / Self.stitchDuration(layout.count[i])))
        }
    }

    func shift(_ now: Double, axis: Int) -> [Double] {
        let k = 1 - CrossStitchInputMotion.easeInOut(CrossStitchInputMotion.seg(now, movedAt, movedAt + Self.glide))
        return (axis == 0 ? fromX : fromY).map { $0 * k }
    }

    func colour(_ now: Double) -> CrossStitchInputRGB {
        CrossStitchInputArt.mix(threadFrom, CrossStitchInputArt.threads[thread].rgb,
                                CrossStitchInputMotion.easeInOut(CrossStitchInputMotion.seg(now, threadAt, threadAt + 0.3)))
    }

    /// Total stitches on the cloth.
    func worked(_ now: Double) -> Double { glyphPresent(now).reduce(0, +) }

    /// The needle while the user types: (x, y, fromX, fromY, busy). It follows the newest letter
    /// that is still being worked; otherwise it rests.
    func needleTarget(_ g: CrossStitchInputGeo, _ now: Double, _ sx: [Double], _ sy: [Double]) -> (Double, Double, Double, Double, Double) {
        let L = layout
        let present = glyphPresent(now)
        let h = g.cell * 0.4
        for i in stride(from: L.count.count - 1, through: 0, by: -1) where L.count[i] > 0 && present[i] < Double(L.count[i]) {
            let k = min(L.count[i] - 1, Int(floor(present[i])))
            let s = L.first[i] + k
            let x = g.cx + (L.cx[s] + sx[i]) * g.cell
            let y = g.cy + (L.cy[s] + sy[i]) * g.cell
            let p = CrossStitchInputArt.needlePoint(x, y, h, present[i] - Double(k))
            return (p.0, p.1, x - h, y + h, 1)
        }
        if L.total == 0 { return (g.parkX, g.parkY, g.parkX - 30, g.parkY + 6, 0) }
        let last = L.total - 1
        let gi = L.glyphOf[last]
        return (g.parkX, g.parkY, g.cx + (L.cx[last] + sx[gi]) * g.cell - h, g.cy + (L.cy[last] + sy[gi]) * g.cell - h, 0)
    }
}
