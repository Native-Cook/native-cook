import SwiftUI

/*
 Icing Name Form — what is drawn per frame: the cake image under its turn and slide, the lettering
 as layered strokes (shadow, body, a lighter core, a glossy highlight that stays lit from the upper
 left whatever the cake's angle), the knife and the piping bag. The counter and the cake are baked
 once (IcingNameFormBake).
 */

typealias IcingNameFormRGB = (Double, Double, Double)

struct IcingNameFormGeo: Equatable {
    var w: Double
    var h: Double
    var cx: Double
    var cy: Double
    var R: Double
    var boardR: Double
    var boxW: Double
    var boxH: Double

    init(w: Double, h: Double, hero: CGRect) {
        let R = min(hero.width, hero.height) / 2 * 0.86
        self.w = w
        self.h = h
        cx = hero.midX
        cy = hero.midY
        self.R = R
        boardR = R * 1.1
        boxW = R * 1.36
        boxH = R * 0.95
    }

    /// Side of the square cake image (pt).
    var side: Double { boardR * 2 + 48 }
}

struct IcingNameFormIcing {
    let name: String
    let rgb: IcingNameFormRGB
    var color: Color { Color(red: rgb.0, green: rgb.1, blue: rgb.2) }
}

enum IcingNameFormArt {
    static let icings: [IcingNameFormIcing] = [
        .init(name: "Chocolate", rgb: (0.36, 0.2, 0.13)),
        .init(name: "Rose", rgb: (0.84, 0.38, 0.49)),
        .init(name: "Pistachio", rgb: (0.53, 0.66, 0.39)),
        .init(name: "Blueberry", rgb: (0.3, 0.37, 0.66)),
        .init(name: "Lemon", rgb: (0.88, 0.71, 0.22)),
    ]

    static func col(_ c: IcingNameFormRGB, _ a: Double) -> Color {
        Color(red: c.0, green: c.1, blue: c.2, opacity: a)
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(red: r, green: g, blue: b, opacity: a)
    }

    static func tone(_ c: IcingNameFormRGB, _ k: Double) -> IcingNameFormRGB {
        if k < 0 { return (c.0 * (1 + k), c.1 * (1 + k), c.2 * (1 + k)) }
        return (c.0 + (1 - c.0) * k, c.1 + (0.97 - c.1) * k, c.2 + (0.94 - c.2) * k)
    }

    private static func round(_ w: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: w, lineCap: .round, lineJoin: .round)
    }

    /// Adds a stroke, from its start to `upto` pt of its length, shifted by (dx, dy).
    static func addStroke(_ p: inout Path, _ s: IcingNameFormStroke, upto: Double, dx: Double, dy: Double) {
        if upto <= 0 { return }
        let pts = s.pts
        let len = s.len
        let n = len.count
        guard n > 0 else { return }
        p.move(to: CGPoint(x: pts[0] + dx, y: pts[1] + dy))
        // A dot (zero-length stroke) still needs a tiny segment for the round cap.
        if len[n - 1] < 0.6 {
            p.addLine(to: CGPoint(x: pts[0] + dx + 0.3, y: pts[1] + dy + 0.2))
            return
        }
        for k in 1..<n {
            if len[k] <= upto {
                p.addLine(to: CGPoint(x: pts[k * 2] + dx, y: pts[k * 2 + 1] + dy))
            } else {
                let f = (upto - len[k - 1]) / max(0.0001, len[k] - len[k - 1])
                p.addLine(to: CGPoint(
                    x: pts[k * 2 - 2] + (pts[k * 2] - pts[k * 2 - 2]) * f + dx,
                    y: pts[k * 2 - 1] + (pts[k * 2 + 1] - pts[k * 2 - 1]) * f + dy
                ))
                return
            }
        }
    }

    /// The point `upto` pt along a stroke.
    static func strokePoint(_ s: IcingNameFormStroke, _ upto: Double) -> (Double, Double) {
        let pts = s.pts
        let len = s.len
        let n = len.count
        if n == 0 { return (0, 0) }
        for k in 1..<max(1, n) where len[k] >= upto {
            let f = (upto - len[k - 1]) / max(0.0001, len[k] - len[k - 1])
            return (pts[k * 2 - 2] + (pts[k * 2] - pts[k * 2 - 2]) * f, pts[k * 2 - 1] + (pts[k * 2 + 1] - pts[k * 2 - 1]) * f)
        }
        return (pts[n * 2 - 2], pts[n * 2 - 1])
    }

    /// One layered tube of icing along `body`, with the start bulges in `blobs`.
    private static func drawTube(_ ctx: GraphicsContext, body: Path, blobs: Path, rgb: IcingNameFormRGB, w: Double, lx: Double, ly: Double, alpha: Double, shadow: Bool) {
        if shadow {
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: w * 0.3))
                l.translateBy(x: -lx * w * 0.5, y: -ly * w * 0.75)
                let c = rgba(0.28, 0.15, 0.08, 0.34 * alpha)
                l.stroke(body, with: .color(c), style: round(w * 1.12))
                l.fill(blobs, with: .color(c))
            }
        }
        // Body: the darker rim of the tube, a lighter core shifted toward the light, then the gloss.
        let rim = col(tone(rgb, -0.28), alpha)
        ctx.stroke(body, with: .color(rim), style: round(w))
        ctx.fill(blobs, with: .color(rim))
        var c = ctx
        c.translateBy(x: lx * w * 0.1, y: ly * w * 0.1)
        c.stroke(body, with: .color(col(rgb, alpha)), style: round(w * 0.7))
        c = ctx
        c.translateBy(x: lx * w * 0.24, y: ly * w * 0.24)
        c.stroke(body, with: .color(col(tone(rgb, 0.55), 0.8 * alpha)), style: round(w * 0.26))
        c = ctx
        c.translateBy(x: lx * w * 0.3, y: ly * w * 0.3)
        c.stroke(body, with: .color(rgba(1, 0.99, 0.97, 0.85 * alpha)), style: round(w * 0.1))
    }

    /// Pipes a lettering in cake-local coordinates. `frac` per stroke (nil = all piped), `shift`
    /// per glyph (pt), colour A everywhere and colour B flowed in over the first `flow` of the ink.
    /// (lx, ly) is the direction toward the light in these local coordinates.
    static func drawLettering(
        _ ctx: GraphicsContext, _ L: IcingNameFormLettering, frac: [Double]?, shiftX: [Double]?, shiftY: [Double]?,
        a: IcingNameFormRGB, b: IcingNameFormRGB, flow: Double, lx: Double, ly: Double, alpha: Double
    ) {
        if alpha <= 0.002 || L.strokes.isEmpty { return }
        let w = max(4.2, min(9, L.size * 0.17))
        var body = Path()
        var blobs = Path()
        var bodyB = Path()
        var blobsB = Path()
        let flowLen = flow * L.total
        var acc = 0.0
        for (i, s) in L.strokes.enumerated() {
            let full = s.full
            let f = frac.map { i < $0.count ? $0[i] : 1 } ?? 1
            let upto = full * f
            let dx = shiftX.map { s.glyph < $0.count ? $0[s.glyph] : 0 } ?? 0
            let dy = shiftY.map { s.glyph < $0.count ? $0[s.glyph] : 0 } ?? 0
            if frac == nil || f > 0 {
                addStroke(&body, s, upto: max(upto, 0.01), dx: dx, dy: dy)
                // Pressure bulge where the nozzle first squeezes (grows over the first 5 pt).
                let r = w * (0.5 + 0.1 * min(1, upto / 5))
                let blob = CGRect(x: s.pts[0] + dx - r, y: s.pts[1] + dy - r, width: r * 2, height: r * 2)
                blobs.addEllipse(in: blob)
                let bUpto = min(upto, flowLen - acc)
                if bUpto > 0 {
                    addStroke(&bodyB, s, upto: bUpto, dx: dx, dy: dy)
                    blobsB.addEllipse(in: blob)
                }
            }
            acc += max(full, 0.5)
        }
        drawTube(ctx, body: body, blobs: blobs, rgb: a, w: w, lx: lx, ly: ly, alpha: alpha, shadow: true)
        if flowLen > 0 {
            drawTube(ctx, body: bodyB, blobs: blobsB, rgb: b, w: w, lx: lx, ly: ly, alpha: alpha, shadow: false)
        }
    }

    /// The cake image under its turn (rad) and slide (pt), plus the fondant's sheen, which stays put.
    static func drawCake(_ ctx: GraphicsContext, _ g: IcingNameFormGeo, cake: GraphicsContext.ResolvedImage, x: Double, y: Double, turn: Double) {
        let side = g.side
        var c = ctx
        c.translateBy(x: x, y: y)
        c.rotate(by: .radians(turn))
        c.draw(cake, in: CGRect(x: -side / 2, y: -side / 2, width: side, height: side))
        let r = g.R * 0.8
        ctx.fill(
            Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: rgba(1, 1, 1, 0.34), location: 0),
                    .init(color: rgba(1, 0.99, 0.97, 0.1), location: 0.45),
                    .init(color: rgba(1, 1, 1, 0), location: 1),
                ]),
                center: CGPoint(x: x - g.R * 0.34, y: y - g.R * 0.4), startRadius: 0, endRadius: g.R * 0.9
            )
        )
    }

    private static func rrect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular)
    }

    private static func hStops(_ stops: [(Color, Double)]) -> Gradient {
        Gradient(stops: stops.map { .init(color: $0.0, location: $0.1) })
    }

    /// The palette knife lifting the writing off, its blade at x (pt), slightly angled.
    static func drawKnife(_ ctx: GraphicsContext, _ g: IcingNameFormGeo, x: Double, y: Double) {
        var c = ctx
        c.translateBy(x: x, y: y)
        c.rotate(by: .degrees(7))
        let top = -g.R * 1.7
        let bottom = g.R * 1.05
        c.drawLayer { l in
            l.addFilter(.blur(radius: 6))
            l.fill(rrect(4, top + 10, 30, bottom - top, 15), with: .color(rgba(0.25, 0.15, 0.1, 0.25)))
        }
        c.fill(
            rrect(-14, top + g.R * 0.45, 28, bottom - top - g.R * 0.45, 14),
            with: .linearGradient(hStops([
                (rgba(0.62, 0.65, 0.68, 1), 0), (rgba(0.95, 0.96, 0.97, 1), 0.3),
                (rgba(0.74, 0.77, 0.8, 1), 0.65), (rgba(0.5, 0.53, 0.57, 1), 1),
            ]), startPoint: CGPoint(x: -14, y: 0), endPoint: CGPoint(x: 14, y: 0))
        )
        // A smear of the lifted icing along the leading edge.
        c.fill(rrect(8, -g.R * 0.25, 5, g.R * 0.6, 2.5), with: .color(rgba(0.4, 0.23, 0.15, 0.55)))
        c.fill(
            rrect(-9, top - 40, 18, g.R * 0.7, 8),
            with: .linearGradient(hStops([
                (rgba(0.5, 0.3, 0.18, 1), 0), (rgba(0.72, 0.48, 0.3, 1), 0.4), (rgba(0.42, 0.24, 0.14, 1), 1),
            ]), startPoint: CGPoint(x: -9, y: 0), endPoint: CGPoint(x: 9, y: 0))
        )
    }

    private static let bagPath: Path = {
        let L = 200.0
        var p = Path()
        p.move(to: CGPoint(x: 14, y: -7))
        p.addCurve(to: CGPoint(x: L, y: -36), control1: CGPoint(x: 70, y: -16), control2: CGPoint(x: 140, y: -38))
        p.addQuadCurve(to: CGPoint(x: L, y: 36), control: CGPoint(x: L + 14, y: 0))
        p.addCurve(to: CGPoint(x: 14, y: 7), control1: CGPoint(x: 140, y: 38), control2: CGPoint(x: 70, y: 16))
        p.closeSubpath()
        return p
    }()

    private static let pleats: Path = {
        var p = Path()
        for k in 0..<4 {
            let kd = Double(k)
            p.move(to: CGPoint(x: 60 + kd * 30, y: -12 - kd * 6))
            p.addQuadCurve(to: CGPoint(x: 192, y: -24 + kd * 14), control: CGPoint(x: 110 + kd * 22, y: -4 - kd * 3))
        }
        return p
    }()

    private static let tipPath: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: -3.5))
        p.addLine(to: CGPoint(x: 13, y: -7))
        p.addLine(to: CGPoint(x: 13, y: 7))
        p.addLine(to: CGPoint(x: 0, y: 3.5))
        p.closeSubpath()
        return p
    }()

    /// The piping bag: a metal star tip at (x, y), the bag rising to the upper right.
    static func drawNozzle(_ ctx: GraphicsContext, x: Double, y: Double, lift: Double, rgb: IcingNameFormRGB) {
        let L = 200.0
        let s = 1 + lift * 0.06
        // Shadow on the cake, further away when lifted.
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 6 + lift * 6))
            l.translateBy(x: x + 10 + lift * 14, y: y + 16 + lift * 18)
            l.rotate(by: .degrees(-48))
            l.fill(rrect(0, -14, L, 46, 23), with: .color(rgba(0.25, 0.14, 0.08, 0.22 - lift * 0.06)))
        }
        var c = ctx
        c.translateBy(x: x, y: y)
        c.scaleBy(x: s, y: s)
        c.rotate(by: .degrees(-48))
        // The bag: translucent plastic over icing, pleated at the top.
        c.fill(bagPath, with: .linearGradient(hStops([
            (col(tone(rgb, 0.35), 1), 0), (col(tone(rgb, 0.05), 1), 0.35),
            (col(tone(rgb, -0.2), 1), 0.7), (col(tone(rgb, -0.38), 1), 1),
        ]), startPoint: CGPoint(x: 0, y: -36), endPoint: CGPoint(x: 0, y: 36)))
        c.fill(bagPath, with: .linearGradient(hStops([
            (rgba(1, 1, 1, 0.55), 0), (rgba(1, 1, 1, 0.08), 0.3), (rgba(1, 1, 1, 0), 0.75), (rgba(1, 1, 1, 0.18), 1),
        ]), startPoint: CGPoint(x: 0, y: -36), endPoint: CGPoint(x: 0, y: 36)))
        c.stroke(pleats, with: .color(rgba(1, 1, 1, 0.35)), lineWidth: 1)
        // Coupler ring and the star tip.
        let metal = GraphicsContext.Shading.linearGradient(hStops([
            (rgba(0.96, 0.96, 0.95, 1), 0), (rgba(0.66, 0.68, 0.7, 1), 0.35),
            (rgba(0.42, 0.44, 0.47, 1), 0.7), (rgba(0.82, 0.83, 0.84, 1), 1),
        ]), startPoint: CGPoint(x: 0, y: -8), endPoint: CGPoint(x: 0, y: 8))
        c.fill(rrect(12, -9, 12, 18, 3), with: metal)
        c.fill(tipPath, with: metal)
        // The star opening, seen from above at the tip.
        let r = 2.6 * s
        ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(col(tone(rgb, -0.3), 1)))
    }
}
