import SwiftUI

/*
 Cross Stitch Input — what is drawn. The cloth, the hoop and the printed border are baked once; every
 frame draws only the name's stitches, the needle and its thread.
 */

typealias CrossStitchInputRGB = (Double, Double, Double)

/// The screen's geometry (pt): canvas size, hoop centre, outer hoop and fabric radius, grid cell.
struct CrossStitchInputGeo: Equatable {
    var w: Double
    var h: Double
    var cx: Double
    var cy: Double
    var hoopR: Double
    var fabricR: Double
    var cell: Double
    /// Where the needle rests, stuck in the cloth.
    var parkX: Double
    var parkY: Double

    /// Cells across the fabric.
    static let cells = 30.0

    init(w: Double, h: Double, hero: CGRect) {
        self.w = w
        self.h = h
        hoopR = min(hero.width - 36, hero.height - 20, 380) / 2
        fabricR = hoopR - 15
        cell = 2 * fabricR / Self.cells
        cx = hero.midX
        cy = hero.midY
        parkX = cx + fabricR * 0.55
        parkY = cy + fabricR * 0.36
    }
}

struct CrossStitchInputThread {
    let name: String
    let rgb: CrossStitchInputRGB
}

enum CrossStitchInputArt {
    static let threads: [CrossStitchInputThread] = [
        .init(name: "Madder", rgb: (0.66, 0.23, 0.2)),
        .init(name: "Indigo", rgb: (0.2, 0.28, 0.5)),
        .init(name: "Marigold", rgb: (0.83, 0.55, 0.15)),
        .init(name: "Moss", rgb: (0.36, 0.48, 0.28)),
        .init(name: "Plum", rgb: (0.46, 0.26, 0.43)),
    ]
    /// The printed border's thread: a soft sage.
    static let borderRGB: CrossStitchInputRGB = (0.5, 0.6, 0.47)

    static func col(_ c: CrossStitchInputRGB, _ a: Double = 1) -> Color { Color(red: c.0, green: c.1, blue: c.2, opacity: a) }
    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color { Color(red: r, green: g, blue: b, opacity: a) }

    /// k < 0 darkens towards a warm brown-black, k > 0 lightens towards warm cream.
    static func shade(_ c: CrossStitchInputRGB, _ k: Double) -> CrossStitchInputRGB {
        if k < 0 { return (c.0 * (1 + k) + 0.08 * -k, c.1 * (1 + k) + 0.05 * -k, c.2 * (1 + k) + 0.04 * -k) }
        return (c.0 + (0.99 - c.0) * k, c.1 + (0.96 - c.1) * k, c.2 + (0.9 - c.2) * k)
    }

    static func mix(_ a: CrossStitchInputRGB, _ b: CrossStitchInputRGB, _ t: Double) -> CrossStitchInputRGB {
        (a.0 + (b.0 - a.0) * t, a.1 + (b.1 - a.1) * t, a.2 + (b.2 - a.2) * t)
    }

    /// Adds one X stitch at cell centre (x, y); f = 0..1 how far it is worked. First leg
    /// bottom-left → top-right (f 0–0.42), second bottom-right → top-left (0.5–0.92): every top
    /// leg lies the same way, as on a well-worked sampler.
    private static func addStitch(_ p: inout Path, _ x: Double, _ y: Double, _ h: Double, _ f: Double) {
        let a = min(1, max(0, f / 0.42))
        if a > 0 {
            p.move(to: CGPoint(x: x - h, y: y + h))
            p.addLine(to: CGPoint(x: x - h + 2 * h * a, y: y + h - 2 * h * a))
        }
        let c = min(1, max(0, (f - 0.5) / 0.42))
        if c > 0 {
            p.move(to: CGPoint(x: x + h, y: y + h))
            p.addLine(to: CGPoint(x: x + h - 2 * h * c, y: y + h - 2 * h * c))
        }
    }

    /// Where the needle's point is while a stitch is at fraction f (pt).
    static func needlePoint(_ x: Double, _ y: Double, _ h: Double, _ f: Double) -> (Double, Double) {
        if f < 0.42 {
            let a = f / 0.42
            return (x - h + 2 * h * a, y + h - 2 * h * a)
        }
        if f < 0.5 {
            let a = (f - 0.42) / 0.08
            return (x + h, y - h + 2 * h * a)
        }
        if f < 0.92 {
            let a = (f - 0.5) / 0.42
            return (x + h - 2 * h * a, y + h - 2 * h * a)
        }
        return (x - h, y - h)
    }

    /// Per-glyph data for stitches worked letter by letter (the user's name).
    struct Glyphwise {
        var shiftX: [Double]
        var shiftY: [Double]
        var glyphOf: [Int]
        var present: [Double]
        var first: [Int]
    }

    /// Draws a set of stitches: cells (xs, ys) in grid units around the fabric centre, the first
    /// `present` of them worked (fractional = the one in progress), or per glyph through `glyphs`.
    /// Thread: a soft shadow, the floss, and two dashed plies on top so it reads as twisted strands.
    static func drawStitches(
        _ ctx: GraphicsContext, _ g: CrossStitchInputGeo, _ xs: [Double], _ ys: [Double], present: Double,
        rgb: CrossStitchInputRGB, alpha: Double, glyphs: Glyphwise? = nil
    ) {
        if present <= 0 || alpha <= 0.002 { return }
        var path = Path()
        let h = g.cell * 0.4
        let n = min(xs.count, Int(ceil(present)))
        for k in 0..<n {
            var f: Double
            var x = xs[k]
            var y = ys[k]
            if let gw = glyphs {
                let gi = gw.glyphOf[k]
                f = min(1, max(0, gw.present[gi] - Double(k - gw.first[gi])))
                x += gw.shiftX[gi]
                y += gw.shiftY[gi]
            } else {
                f = min(1, present - Double(k))
            }
            if f <= 0 { continue }
            addStitch(&path, g.cx + x * g.cell, g.cy + y * g.cell, h, f)
        }
        let width = g.cell * 0.33
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 0.9))
            l.translateBy(x: 0.45, y: 0.85)
            l.stroke(path, with: .color(rgba(0.24, 0.16, 0.1, 0.32 * alpha)), style: StrokeStyle(lineWidth: width * 1.15, lineCap: .round))
        }
        ctx.stroke(path, with: .color(col(rgb, alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
        var c = ctx
        c.translateBy(x: -width * 0.16, y: -width * 0.16)
        c.stroke(path, with: .color(col(shade(rgb, 0.45), 0.7 * alpha)), style: StrokeStyle(lineWidth: width * 0.34, lineCap: .butt, dash: [1.5, 1.3], dashPhase: 0))
        c = ctx
        c.translateBy(x: width * 0.16, y: width * 0.16)
        c.stroke(path, with: .color(col(shade(rgb, -0.35), 0.55 * alpha)), style: StrokeStyle(lineWidth: width * 0.34, lineCap: .butt, dash: [1.5, 1.3], dashPhase: 1.4))
    }

    private static let needleL = 58.0

    private static let needlePath: Path = {
        let L = needleL
        var p = Path()
        p.move(to: .zero)
        p.addLine(to: CGPoint(x: L - 8, y: -1.8))
        p.addQuadCurve(to: CGPoint(x: L + 1.5, y: 0), control: CGPoint(x: L + 1.5, y: -2.1))
        p.addQuadCurve(to: CGPoint(x: L - 8, y: 1.8), control: CGPoint(x: L + 1.5, y: 2.1))
        p.closeSubpath()
        return p
    }()

    private static let eyePath = Path(ellipseIn: CGRect(x: needleL - 8, y: -0.7, width: 6.5, height: 1.4))

    /// The needle (point at x, y, lying along `angle`, eye up-right) and its thread, which runs from
    /// the eye in a soft sag down to (fromX, fromY), where it last went into the cloth.
    static func drawNeedle(_ ctx: GraphicsContext, x: Double, y: Double, angle: Double, lift: Double, fromX: Double, fromY: Double, rgb: CrossStitchInputRGB, alpha: Double) {
        if alpha <= 0.002 { return }
        let L = needleL
        let eyeX = x + cos(angle) * (L - 4)
        let eyeY = y + sin(angle) * (L - 4)
        // Thread: from the eye, sagging under its weight, to the hole it came out of.
        var tp = Path()
        let midX = (eyeX + fromX) / 2
        let midY = max(eyeY, fromY) + 10 + hypot(eyeX - fromX, eyeY - fromY) * 0.18
        tp.move(to: CGPoint(x: fromX, y: fromY))
        tp.addQuadCurve(to: CGPoint(x: eyeX, y: eyeY), control: CGPoint(x: midX, y: midY))
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 1.4))
            l.translateBy(x: 1, y: 3)
            l.stroke(tp, with: .color(rgba(0.2, 0.13, 0.08, 0.18 * alpha)), style: StrokeStyle(lineWidth: 2.6, lineCap: .round))
        }
        ctx.stroke(tp, with: .color(col(rgb, alpha)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
        var hc = ctx
        hc.translateBy(x: -0.5, y: -0.5)
        hc.stroke(tp, with: .color(col(shade(rgb, 0.4), 0.6 * alpha)), style: StrokeStyle(lineWidth: 0.8, lineCap: .round))

        // Needle: a tapered steel shaft. Its shadow falls further away the higher it is lifted.
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 1.2 + lift * 2))
            l.translateBy(x: x + 1.5 + lift * 5, y: y + 2.5 + lift * 7)
            l.rotate(by: .radians(angle))
            l.fill(needlePath, with: .color(rgba(0.22, 0.15, 0.08, 0.3 * alpha)))
        }
        var c = ctx
        c.translateBy(x: x, y: y)
        c.rotate(by: .radians(angle))
        c.fill(needlePath, with: .linearGradient(
            Gradient(stops: [
                .init(color: rgba(0.97, 0.97, 0.96, alpha), location: 0),
                .init(color: rgba(0.62, 0.65, 0.68, alpha), location: 0.35),
                .init(color: rgba(0.4, 0.42, 0.45, alpha), location: 0.7),
                .init(color: rgba(0.8, 0.82, 0.83, alpha), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: -2.1), endPoint: CGPoint(x: 0, y: 2.1)
        ))
        c.fill(eyePath, with: .color(rgba(0.3, 0.28, 0.27, alpha)))
    }

    /// The needle's point on stitch `present` of a layout, and the hole its thread comes from.
    private static func workPoint(_ g: CrossStitchInputGeo, _ L: CrossStitchInputLayout, _ present: Double) -> (Double, Double, Double, Double) {
        if L.total == 0 { return (g.parkX, g.parkY, g.parkX, g.parkY) }
        let h = g.cell * 0.4
        let k = min(L.total - 1, max(0, Int(floor(present))))
        let f = min(1, max(0, present - Double(k)))
        let x = g.cx + L.cx[k] * g.cell
        let y = g.cy + L.cy[k] * g.cell
        let p = needlePoint(x, y, h, f)
        return (p.0, p.1, x - h, y + h)
    }

    /// The needle in the loop: (x, y, angle, lift, fromX, fromY).
    static func autoNeedle(_ t: Double, _ g: CrossStitchInputGeo, _ rest: CrossStitchInputLayout, _ second: CrossStitchInputLayout) -> (Double, Double, Double, Double, Double, Double) {
        typealias M = CrossStitchInputMotion
        let w = M.needleScript(t)
        let angle = -0.95
        let rn = Double(rest.total), sn = Double(second.total)
        let a = workPoint(g, rest, min(rn - 0.001, M.restPresent(t, rn)))
        let b = workPoint(g, second, min(sn - 0.001, M.secondPresent(t, sn)))
        // The knot: one small loop round the last hole.
        let kr = g.cell * 0.9 * sin(.pi * w.3)
        let ka = w.3 * .pi * 2
        let bx = b.0 + kr * cos(ka)
        let by = b.1 + kr * sin(ka)
        let work = w.1 + w.2
        let x = g.parkX * w.0 + a.0 * w.1 + bx * w.2
        let y = g.parkY * w.0 + a.1 * w.1 + by * w.2
        // The thread comes out of the last stitch of whichever name is on the cloth.
        let restOn = M.restPresent(t, rn) > 0.5
        let tail = restOn ? a : b
        let fromX = work > 0.5 ? (w.1 > w.2 ? a.2 : b.2) : tail.2
        let fromY = work > 0.5 ? (w.1 > w.2 ? a.3 : b.3) : tail.3
        // A small bob per stitch while working, a lift while travelling.
        let bob = work * 0.25 * (1 + sin(t * 2 * .pi * 9))
        let wob = 0.06 * sin(t / CrossStitchInput.loop * 2 * .pi * 4) * work
        return (x, y, angle + wob, w.4 + bob, fromX, fromY)
    }

    /// One frame of the loop's cloth: both names at their stitch counts, and the needle.
    static func drawLoopFrame(_ ctx: GraphicsContext, _ g: CrossStitchInputGeo, t: Double, alpha: Double, withNeedle: Bool, rest: CrossStitchInputLayout, second: CrossStitchInputLayout) {
        typealias M = CrossStitchInputMotion
        let restRGB = threads[M.restThread].rgb
        let secondRGB = threads[M.secondThread].rgb
        drawStitches(ctx, g, rest.cx, rest.cy, present: M.restPresent(t, Double(rest.total)), rgb: restRGB, alpha: alpha)
        drawStitches(ctx, g, second.cx, second.cy, present: M.secondPresent(t, Double(second.total)), rgb: secondRGB, alpha: alpha)
        if withNeedle {
            let n = autoNeedle(t, g, rest, second)
            drawNeedle(ctx, x: n.0, y: n.1, angle: n.2, lift: n.3, fromX: n.4, fromY: n.5, rgb: mix(restRGB, secondRGB, M.threadBlend(t)), alpha: alpha)
        }
    }
}
