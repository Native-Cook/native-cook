import SwiftUI

/*
 Icing Name Form — the autoPlay script; every value is a pure function of the loop time t.

   0.0 – 1.4   rest: "Happy birthday" piped in chocolate, the nozzle resting at the upper right.
   1.4 – 2.2   a palette knife sweeps left to right and lifts the writing off; the field empties.
   2.0 – 2.35  the nozzle comes down to the cake; "Noor" is typed (2.25 – 2.55).
   2.35 – 5.4  "Noor" is piped stroke by stroke, the nozzle lifting between strokes.
   5.5 – 5.95  the nozzle goes back to rest.
   5.6 – 6.4   the Rose chip is chosen: rose icing flows through the letters (5.65 – 6.35).
   6.4 – 7.4   hold.
   7.4 – 8.5   the turntable hands over to the next cake: this one slides out to the left with a
               quarter turn, a fresh cake with "Happy birthday" slides in from the right, the
               field, chip and price go back (7.9).
   8.5 – 9.0   rest.
 */

enum IcingNameFormMotion {
    static let restText = "Happy birthday"
    static let secondText = "Noor"
    static let pipeFrom = 2.35
    static let pipeTo = 5.4
    static let priceRoll = 0.45

    static let textScript: [(Double, String)] = [
        (0, restText), (1.5, "Happy birth"), (1.65, "Happy"), (1.8, "Hap"), (1.95, ""),
        (2.25, "N"), (2.35, "No"), (2.45, "Noo"), (2.55, secondText), (7.9, restText),
    ]

    /// Price in cents over the loop: base cake $38, 50 c a letter, $1 for a coloured icing.
    private static let priceScript: [(Double, Int)] = [(0, 4450), (1.95, 3800), (2.6, 4000), (5.7, 4100), (7.9, 4450)]

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }

    static func easeInOut(_ u: Double) -> Double {
        u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    static func easeOutBack(_ u: Double) -> Double {
        let c1 = 0.9
        let v = u - 1
        return 1 + (c1 + 1) * v * v * v + c1 * v * v
    }

    static func scriptIndex(_ t: Double) -> Int {
        var i = 0
        for (k, e) in textScript.enumerated() where t >= e.0 { i = k }
        return i
    }

    /// The price: (from cents, to cents, progress 0..1).
    static func priceAt(_ t: Double) -> (Double, Double, Double) {
        var i = 0
        for (k, e) in priceScript.enumerated() where t >= e.0 { i = k }
        let from = i == 0 ? priceScript[priceScript.count - 1].1 : priceScript[i - 1].1
        let p = i == 0 ? 1 : easeInOut(seg(t, priceScript[i].0, priceScript[i].0 + priceRoll))
        return (Double(from), Double(priceScript[i].1), p)
    }

    /// Knife position across the cake, -1 (left edge) → 1 (right edge); -2 when away.
    static func knifeAt(_ t: Double) -> Double {
        if t < 1.4 || t > 2.2 { return -2 }
        return -1.35 + 2.7 * easeInOut(seg(t, 1.4, 2.2))
    }

    static func pipeProgress(_ t: Double) -> Double { seg(t, pipeFrom, pipeTo) }
    static func roseFlow(_ t: Double) -> Double { easeInOut(seg(t, 5.65, 6.35)) }
    /// Chip ring position (0 Chocolate, 1 Rose).
    static func chipPosition(_ t: Double) -> Double {
        easeOutBack(seg(t, 5.6, 5.9)) * (1 - easeOutBack(seg(t, 7.9, 8.2)))
    }
    /// The cake hand-over: 0 → the Noor cake is centred, 1 → the next cake is.
    static func slideAt(_ t: Double) -> Double { easeInOut(seg(t, 7.4, 8.5)) }
    /// 0 = nozzle at rest, 1 = nozzle at work on the cake.
    static func nozzleWork(_ t: Double) -> Double {
        easeInOut(seg(t, 2.0, 2.35)) * (1 - easeInOut(seg(t, 5.5, 5.95)))
    }

    static func letterCount(_ text: String) -> Int { text.filter { $0 != " " }.count }
    static func priceFor(_ text: String, _ colour: Int) -> Double {
        Double(3800 + 50 * letterCount(text) + (colour == 0 ? 0 : 100))
    }
}

/// The user's lettering while they type.
struct IcingNameFormUserWriting {
    var L: IcingNameFormLettering
    var typedAt: [Double]
    var fromX: [Double]
    var fromY: [Double]
    var movedAt: Double
    var colorFrom: Int
    var colorTo: Int
    var colorAt: Double

    static let glide = 0.35

    static func pipeDuration(_ len: Double) -> Double { 0.22 + len / 200 }

    /// Per stroke, how much is piped (0..1) at `now`.
    func fracs(_ now: Double, instant: Bool) -> [Double] {
        var out: [Double] = []
        for g in L.firstStroke.indices {
            let len = L.glyphLength[g]
            let p = instant ? 1 : min(1, max(0, (now - typedAt[g]) / Self.pipeDuration(len)))
            var ink = p * len
            for k in 0..<L.strokeCount[g] {
                let full = max(0.5, L.strokes[L.firstStroke[g] + k].full)
                out.append(min(1, max(0, ink / full)))
                ink -= full
            }
        }
        return out
    }

    func shift(_ now: Double, axis: Int) -> [Double] {
        let k = 1 - IcingNameFormMotion.easeInOut(IcingNameFormMotion.seg(now, movedAt, movedAt + Self.glide))
        return (axis == 0 ? fromX : fromY).map { $0 * k }
    }

    /// The nozzle's target while the user types: (x, y cake-local, busy).
    func pen(_ fr: [Double], _ sx: [Double], _ sy: [Double], park: (Double, Double)) -> (Double, Double, Double) {
        for i in stride(from: L.strokes.count - 1, through: 0, by: -1) where fr[i] > 0 && fr[i] < 1 {
            let s = L.strokes[i]
            let q = IcingNameFormArt.strokePoint(s, fr[i] * s.full)
            return (q.0 + sx[s.glyph], q.1 + sy[s.glyph], 1)
        }
        return (park.0, park.1, 0)
    }
}

/// One frame of the loop, and the nozzle's path along a piping schedule.
enum IcingNameFormScene {
    /// Toward the light (upper left), as a unit vector in screen space.
    static let lightX = -0.6
    static let lightY = -0.8

    /// Where the nozzle rests, relative to the cake's centre (pt).
    static func park(_ g: IcingNameFormGeo) -> (Double, Double) { (g.R * 0.98, -g.R * 0.72) }

    /// Per-stroke progress of a scripted piping at u (0..1 of its schedule).
    static func scheduleFracs(_ t0: [Double], _ t1: [Double], _ u: Double) -> [Double] {
        t0.indices.map { i in
            let f = IcingNameFormMotion.seg(u, t0[i], t1[i])
            return 0.75 * f + 0.25 * IcingNameFormMotion.easeInOut(f)
        }
    }

    /// The nozzle on a scripted piping at u: (x, y cake-local, lift 0..1).
    static func schedulePen(_ L: IcingNameFormLettering, _ t0: [Double], _ t1: [Double], _ u: Double) -> (Double, Double, Double) {
        let n = L.strokes.count
        if n == 0 { return (0, 0, 1) }
        typealias M = IcingNameFormMotion
        for i in 0..<n {
            let s = L.strokes[i]
            if u <= t0[i] {
                if i == 0 { return (s.pts[0], s.pts[1], 0) }
                // Travelling, lifted, from the end of the last stroke to the start of this one.
                let p = L.strokes[i - 1]
                let ex = p.pts[p.pts.count - 2]
                let ey = p.pts[p.pts.count - 1]
                let v = M.easeInOut(M.seg(u, t1[i - 1], t0[i]))
                return (ex + (s.pts[0] - ex) * v, ey + (s.pts[1] - ey) * v, sin(.pi * v))
            }
            if u <= t1[i] {
                let f = M.seg(u, t0[i], t1[i])
                let q = IcingNameFormArt.strokePoint(s, s.full * (0.75 * f + 0.25 * M.easeInOut(f)))
                return (q.0, q.1, 0)
            }
        }
        let last = L.strokes[n - 1]
        return (last.pts[last.pts.count - 2], last.pts[last.pts.count - 1], 0)
    }

    /// Draws a lettering on a cake at (x, y) turned by `turn`, keeping its gloss lit from the upper left.
    static func drawWriting(
        _ ctx: GraphicsContext, _ L: IcingNameFormLettering, x: Double, y: Double, turn: Double,
        frac: [Double]?, sx: [Double]?, sy: [Double]?, a: Int, b: Int, flow: Double, alpha: Double, clipLeft: Double?
    ) {
        var c = ctx
        c.translateBy(x: x, y: y)
        c.rotate(by: .radians(turn))
        if let clipLeft { c.clip(to: Path(CGRect(x: clipLeft, y: -1e4, width: 2e4, height: 2e4))) }
        let co = cos(-turn)
        let si = sin(-turn)
        IcingNameFormArt.drawLettering(
            c, L, frac: frac, shiftX: sx, shiftY: sy,
            a: IcingNameFormArt.icings[a].rgb, b: IcingNameFormArt.icings[b].rgb, flow: flow,
            lx: lightX * co - lightY * si, ly: lightX * si + lightY * co, alpha: alpha
        )
    }

    /// One frame of the loop at t.
    static func drawLoopFrame(
        _ ctx: GraphicsContext, _ g: IcingNameFormGeo, cake: GraphicsContext.ResolvedImage, t: Double,
        rest: IcingNameFormLettering, second: IcingNameFormLettering, t0: [Double], t1: [Double]
    ) {
        typealias M = IcingNameFormMotion
        let s = M.slideAt(t)
        let away = g.w / 2 + g.boardR + 40
        // The cake now on the turntable (it slides out from 7.4), and the next one sliding in.
        let x0 = g.cx - s * away
        let turn0 = -s * 0.7
        IcingNameFormArt.drawCake(ctx, g, cake: cake, x: x0, y: g.cy, turn: turn0)
        if t < 1.4 {
            drawWriting(ctx, rest, x: x0, y: g.cy, turn: turn0, frac: nil, sx: nil, sy: nil, a: 0, b: 0, flow: 0, alpha: 1, clipLeft: nil)
        } else if t < 2.2 {
            let k = M.knifeAt(t)
            drawWriting(ctx, rest, x: x0, y: g.cy, turn: turn0, frac: nil, sx: nil, sy: nil, a: 0, b: 0, flow: 0, alpha: 1, clipLeft: k < -1.9 ? nil : k * g.R * 0.75)
        } else {
            drawWriting(ctx, second, x: x0, y: g.cy, turn: turn0, frac: scheduleFracs(t0, t1, M.pipeProgress(t)), sx: nil, sy: nil, a: 0, b: 1, flow: M.roseFlow(t), alpha: 1, clipLeft: nil)
        }
        if s > 0 {
            let x1 = g.cx + (1 - s) * away
            let turn1 = (1 - s) * 0.7
            IcingNameFormArt.drawCake(ctx, g, cake: cake, x: x1, y: g.cy, turn: turn1)
            drawWriting(ctx, rest, x: x1, y: g.cy, turn: turn1, frac: nil, sx: nil, sy: nil, a: 0, b: 0, flow: 0, alpha: 1, clipLeft: nil)
        }
        let k = M.knifeAt(t)
        if k > -1.9 { IcingNameFormArt.drawKnife(ctx, g, x: g.cx + k * g.R * 0.75, y: g.cy) }
        let w = M.nozzleWork(t)
        let pk = park(g)
        let pen = schedulePen(second, t0, t1, M.pipeProgress(t))
        let px = pk.0 + (pen.0 - pk.0) * w
        let py = pk.1 + (pen.1 - pk.1) * w
        let lift = 1 - w + w * pen.2
        let flow = M.roseFlow(t)
        let colour = flow > 0.5 || t > 7.9 ? (t > 7.9 ? 0 : 1) : 0
        IcingNameFormArt.drawNozzle(ctx, x: g.cx + px, y: g.cy + py, lift: lift, rgb: IcingNameFormArt.icings[colour].rgb)
    }
}
