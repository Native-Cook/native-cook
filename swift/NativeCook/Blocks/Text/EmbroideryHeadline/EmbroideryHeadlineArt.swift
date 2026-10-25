import SwiftUI

/*
 Embroidery Headline — what is drawn. The table, hoop and linen are baked once (2 px per pt). The
 headline is turned into satin stitch once, off the main thread: every letter is rasterised, then cut
 by parallel lines at the letter's own angle into thread segments that run edge to edge. Each frame
 builds paths of the laid threads only (a shadow, two alternating thread shades, a sheen).
 */

typealias EmbroideryHeadlineRGB = (Double, Double, Double)

struct EmbroideryHeadlineGeo: Equatable {
    var w: Double
    var h: Double
    var cx: Double
    var cy: Double
    var hoopR: Double
    var fabricR: Double

    init(w: Double, h: Double, hero: CGRect) {
        self.w = w
        self.h = h
        hoopR = min(hero.width - 8, hero.height - 12, 400) / 2
        cx = hero.midX
        cy = hero.midY
        fabricR = hoopR - 15
    }
}

/// One letter in satin stitch: segments (x1, y1, x2, y2 flat, pt), thread colour, sheen 0..1.
struct EmbroideryHeadlineGlyph {
    var segs: [Double]
    var count: Int
    var rgb: EmbroideryHeadlineRGB
    var sheen: Double
}

struct EmbroideryHeadlineSatin {
    var glyphs: [EmbroideryHeadlineGlyph]
    var counts: [Double]
    var spacing: Double
    /// The knot: x, y, radius.
    var knot: (Double, Double, Double)
    var knotRGB: EmbroideryHeadlineRGB
}

enum EmbroideryHeadlineArt {
    static let madder: EmbroideryHeadlineRGB = (0.64, 0.22, 0.17)
    static let indigo: EmbroideryHeadlineRGB = (0.17, 0.3, 0.42)
    /// Thread angle per letter (degrees from horizontal), set across each letter's main strokes.
    static let angles: [Character: Double] = ["S": 58, "l": 12, "o": 64, "w": 72, "m": 16, "a": 56, "d": 20, "e": 62]
    /// Where the light comes from for the sheen (degrees).
    static let light = 38.0
    static let spacing = 1.15

    static func col(_ c: EmbroideryHeadlineRGB, _ a: Double = 1) -> Color { Color(red: c.0, green: c.1, blue: c.2, opacity: a) }
    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color { Color(red: r, green: g, blue: b, opacity: a) }

    static func tone(_ c: EmbroideryHeadlineRGB, _ k: Double) -> EmbroideryHeadlineRGB {
        if k < 0 { return (c.0 * (1 + k), c.1 * (1 + k), c.2 * (1 + k)) }
        return (c.0 + (1 - c.0) * k, c.1 + (0.97 - c.1) * k, c.2 + (0.92 - c.2) * k)
    }

    /// Lays the first `present` threads of a glyph (the one being laid is cut short). `even`/`odd`
    /// alternate so neighbouring threads differ a little in shade; `sheen` gets the middle 70 % of
    /// each thread, so the ends, where it dives into the cloth, stay darker.
    private static func lay(_ g: EmbroideryHeadlineGlyph, _ present: Double, _ even: inout Path, _ odd: inout Path, _ sheen: inout Path, _ all: inout Path) {
        let n = min(g.count, Int(ceil(present)))
        guard n > 0 else { return }
        for k in 0..<n {
            let f = min(1, present - Double(k))
            let x1 = g.segs[k * 4], y1 = g.segs[k * 4 + 1]
            let x2 = g.segs[k * 4 + 2], y2 = g.segs[k * 4 + 3]
            // Satin goes back and forth: odd threads are laid from the far edge.
            let fromEnd = k % 2 == 1
            let ax = fromEnd ? x2 : x1, ay = fromEnd ? y2 : y1
            let bx = fromEnd ? x1 : x2, by = fromEnd ? y1 : y2
            let ex = ax + (bx - ax) * f, ey = ay + (by - ay) * f
            let a = CGPoint(x: ax, y: ay), e = CGPoint(x: ex, y: ey)
            if k % 2 == 0 {
                even.move(to: a)
                even.addLine(to: e)
            } else {
                odd.move(to: a)
                odd.addLine(to: e)
            }
            all.move(to: a)
            all.addLine(to: e)
            if f > 0.3 {
                sheen.move(to: CGPoint(x: ax + (ex - ax) * 0.15, y: ay + (ey - ay) * 0.15))
                sheen.addLine(to: CGPoint(x: ax + (ex - ax) * 0.85, y: ay + (ey - ay) * 0.85))
            }
        }
    }

    /// Where the needle is on a glyph at `present`: (x, y, thread-from x, y).
    static func front(_ g: EmbroideryHeadlineGlyph, _ present: Double) -> (Double, Double, Double, Double) {
        if g.count == 0 { return (0, 0, 0, 0) }
        let k = min(g.count - 1, max(0, Int(floor(present))))
        let f = min(1, max(0, present - Double(k)))
        let x1 = g.segs[k * 4], y1 = g.segs[k * 4 + 1]
        let x2 = g.segs[k * 4 + 2], y2 = g.segs[k * 4 + 3]
        let fromEnd = k % 2 == 1
        let ax = fromEnd ? x2 : x1, ay = fromEnd ? y2 : y1
        let bx = fromEnd ? x1 : x2, by = fromEnd ? y1 : y2
        // The needle rides near the middle of the thread being laid (it swings edge to edge too
        // fast to follow at 160 threads a second), leaning toward the end it is heading for.
        let mx = (x1 + x2) / 2, my = (y1 + y2) / 2
        let lean = 0.35 * (f - 0.5) * 2
        return (mx + (bx - mx) * lean * 0.5, my + (by - my) * lean * 0.5, ax, ay)
    }

    static func drawSatin(_ ctx: GraphicsContext, _ s: EmbroideryHeadlineSatin, _ present: [Double]) {
        var all = Path()
        var evens: [Path] = []
        var odds: [Path] = []
        var sheens: [Path] = []
        for (i, g) in s.glyphs.enumerated() {
            var e = Path(), o = Path(), sh = Path()
            lay(g, i < present.count ? present[i] : 0, &e, &o, &sh, &all)
            evens.append(e)
            odds.append(o)
            sheens.append(sh)
        }
        if all.isEmpty { return }
        let w = s.spacing * 1.25
        // The cloth puckers a little around the stitching: a wide, soft darkening, then the
        // threads' own shadow.
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 3.2))
            l.translateBy(x: 0.5, y: 1.6)
            l.stroke(all, with: .color(rgba(0.3, 0.22, 0.13, 0.12)), style: StrokeStyle(lineWidth: w + 5, lineCap: .round))
        }
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 0.8))
            l.translateBy(x: 0.45, y: 0.9)
            l.stroke(all, with: .color(rgba(0.18, 0.1, 0.06, 0.4)), style: StrokeStyle(lineWidth: w + 0.6, lineCap: .round))
        }
        var lit = ctx
        lit.translateBy(x: -0.25, y: -0.3)
        for (i, g) in s.glyphs.enumerated() {
            ctx.stroke(evens[i], with: .color(col(tone(g.rgb, -0.24))), style: StrokeStyle(lineWidth: w, lineCap: .round))
            ctx.stroke(odds[i], with: .color(col(tone(g.rgb, -0.04))), style: StrokeStyle(lineWidth: w, lineCap: .round))
            lit.stroke(sheens[i], with: .color(col(tone(g.rgb, 0.5 + 0.2 * g.sheen), 0.28 + 0.55 * g.sheen)), style: StrokeStyle(lineWidth: w * 0.5, lineCap: .round))
        }
    }

    /// The French knot: a tight bead of thread wraps.
    static func drawKnot(_ ctx: GraphicsContext, _ s: EmbroideryHeadlineSatin, scale: Double, alpha: Double) {
        if alpha <= 0.002 { return }
        let (x, y, r0) = s.knot
        let r = r0 * scale
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: max(0.1, 1.6 + (scale - 1) * 4)))
            let rr = r * 1.02
            l.fill(Path(ellipseIn: CGRect(x: x + 0.8 - rr, y: y + 1.6 - rr, width: rr * 2, height: rr * 2)), with: .color(rgba(0.2, 0.12, 0.06, 0.38 * alpha)))
        }
        ctx.fill(
            Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: col(tone(s.knotRGB, 0.35), alpha), location: 0),
                    .init(color: col(s.knotRGB, alpha), location: 0.45),
                    .init(color: col(tone(s.knotRGB, -0.45), alpha), location: 1),
                ]),
                center: CGPoint(x: x - r * 0.35, y: y - r * 0.4), startRadius: 0, endRadius: r * 1.5
            )
        )
        for k in 0..<5 {
            let a0 = Double(k) * 72 + 20
            var p = Path()
            p.addArc(center: CGPoint(x: x, y: y), radius: r * 0.72, startAngle: .degrees(a0), endAngle: .degrees(a0 + 110), clockwise: false)
            ctx.stroke(p, with: .color(col(tone(s.knotRGB, k % 2 == 0 ? -0.3 : 0.3), 0.75 * alpha)), style: StrokeStyle(lineWidth: r * 0.22, lineCap: .round))
        }
        var hi = Path()
        hi.addArc(center: CGPoint(x: x, y: y - r * 0.05), radius: r * 0.55, startAngle: .degrees(200), endAngle: .degrees(270), clockwise: false)
        ctx.stroke(hi, with: .color(rgba(1, 0.96, 0.9, 0.45 * alpha)), style: StrokeStyle(lineWidth: r * 0.16, lineCap: .round))
    }

    private static let needleL = 62.0

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

    /// Needle and thread (thread from the eye, sagging, to where it last went into the cloth).
    static func drawNeedle(_ ctx: GraphicsContext, x: Double, y: Double, angle: Double, lift: Double, fromX: Double, fromY: Double, rgb: EmbroideryHeadlineRGB) {
        let L = needleL
        let eyeX = x + cos(angle) * (L - 5)
        let eyeY = y + sin(angle) * (L - 5)
        var tp = Path()
        tp.move(to: CGPoint(x: fromX, y: fromY))
        tp.addQuadCurve(
            to: CGPoint(x: eyeX, y: eyeY),
            control: CGPoint(x: (eyeX + fromX) / 2, y: max(eyeY, fromY) + 14 + hypot(eyeX - fromX, eyeY - fromY) * 0.2)
        )
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 1.4))
            l.translateBy(x: 1, y: 3)
            l.stroke(tp, with: .color(rgba(0.12, 0.08, 0.05, 0.22)), style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
        }
        ctx.stroke(tp, with: .color(col(rgb)), style: StrokeStyle(lineWidth: 1.9, lineCap: .round))
        ctx.stroke(tp, with: .color(col(tone(rgb, 0.45), 0.6)), style: StrokeStyle(lineWidth: 0.7, lineCap: .round))

        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 1.2 + lift * 2.5))
            l.translateBy(x: x + 1.5 + lift * 6, y: y + 2.5 + lift * 8)
            l.rotate(by: .radians(angle))
            l.fill(needlePath, with: .color(rgba(0.14, 0.1, 0.06, 0.32)))
        }
        var c = ctx
        c.translateBy(x: x, y: y)
        c.rotate(by: .radians(angle))
        c.fill(needlePath, with: .linearGradient(
            Gradient(stops: [
                .init(color: rgba(0.97, 0.97, 0.96, 1), location: 0),
                .init(color: rgba(0.64, 0.67, 0.7, 1), location: 0.35),
                .init(color: rgba(0.38, 0.4, 0.43, 1), location: 0.7),
                .init(color: rgba(0.8, 0.82, 0.83, 1), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: -2.1), endPoint: CGPoint(x: 0, y: 2.1)
        ))
        c.fill(Path(ellipseIn: CGRect(x: L - 8, y: -0.7, width: 6.5, height: 1.4)), with: .color(rgba(0.28, 0.26, 0.25, 1)))
    }

    private static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255, blue: Double(v & 255) / 255, opacity: a)
    }

    private static func disc(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    /// The table, hoop and linen in plain vectors (no grain or weave), drawn until the baked scene is ready.
    static func drawPlainScene(_ ctx: GraphicsContext, _ g: EmbroideryHeadlineGeo) {
        let bc = CGPoint(x: g.cx, y: g.cy - g.hoopR * 0.2)
        ctx.fill(Path(CGRect(x: 0, y: 0, width: g.w, height: g.h)), with: .radialGradient(
            Gradient(stops: [.init(color: hex(0x3A4A3F), location: 0), .init(color: hex(0x2C3A31), location: 0.5), .init(color: hex(0x1B241F), location: 1)]),
            center: bc, startRadius: 0, endRadius: max(g.w, g.h) * 0.75
        ))
        guard g.hoopR > 20 else { return }
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 22))
            l.fill(disc(g.cx, g.cy + 18, g.hoopR + 4), with: .color(hex(0x0B100D, 0.45)))
        }
        let fr = g.fabricR + 3
        ctx.fill(disc(g.cx, g.cy, fr), with: .radialGradient(
            Gradient(stops: [.init(color: hex(0xF1E8D8), location: 0), .init(color: hex(0xE6DAC4), location: 0.55), .init(color: hex(0xD4C5AC), location: 1)]),
            center: CGPoint(x: g.cx - fr * 0.3, y: g.cy - fr * 0.5), startRadius: 0, endRadius: fr * 1.7
        ))
        ctx.fill(disc(g.cx, g.cy, fr), with: .radialGradient(
            Gradient(stops: [.init(color: hex(0x4A3A26, 0), location: 0), .init(color: hex(0x4A3A26, 0), location: 0.8), .init(color: hex(0x4A3A26, 0.3), location: 1)]),
            center: CGPoint(x: g.cx, y: g.cy), startRadius: 0, endRadius: fr
        ))
        func ring(_ r0: Double, _ r1: Double, _ stops: [UInt32]) {
            var p = disc(g.cx, g.cy, r1)
            p.addPath(disc(g.cx, g.cy, r0))
            let locs = [r0 / r1, (r0 + (r1 - r0) * 0.3) / r1, (r0 + (r1 - r0) * 0.7) / r1, 1]
            ctx.fill(p, with: .radialGradient(
                Gradient(stops: zip(stops, locs).map { .init(color: hex($0.0), location: $0.1) }),
                center: CGPoint(x: g.cx, y: g.cy), startRadius: 0, endRadius: r1
            ), style: FillStyle(eoFill: true))
        }
        ring(g.fabricR - 1, g.fabricR + 4, [0x8F6235, 0xC29257, 0xD3A970, 0xA87540])
        ring(g.fabricR + 3, g.hoopR, [0x7F5329, 0xD2A46A, 0xE3BC86, 0x9E6A37])
        ctx.stroke(disc(g.cx, g.cy - 0.5, g.hoopR - 1.2), with: .color(hex(0xFFF1DA, 0.4)), lineWidth: 1)
        ctx.stroke(disc(g.cx, g.cy + 0.5, g.fabricR + 3.2), with: .color(hex(0x4E3218, 0.5)), lineWidth: 1)
        let top = g.cy - g.hoopR
        for dx in [-11.0, 3] {
            ctx.fill(Path(roundedRect: CGRect(x: g.cx + dx, y: top - 13, width: 8, height: 22), cornerRadius: 2.5), with: .linearGradient(
                Gradient(stops: [.init(color: hex(0xE2B880), location: 0), .init(color: hex(0xBD8A52), location: 0.55), .init(color: hex(0x936234), location: 1)]),
                startPoint: CGPoint(x: 0, y: top - 12), endPoint: CGPoint(x: 0, y: top + 12)
            ))
        }
        let brass = GraphicsContext.Shading.linearGradient(
            Gradient(stops: [.init(color: hex(0xFFF0B8), location: 0), .init(color: hex(0xD9A93E), location: 0.35), .init(color: hex(0x8D6418), location: 0.75), .init(color: hex(0xC99733), location: 1)]),
            startPoint: CGPoint(x: 0, y: top - 9), endPoint: CGPoint(x: 0, y: top - 1)
        )
        ctx.fill(Path(roundedRect: CGRect(x: g.cx - 22, y: top - 9, width: 44, height: 8), cornerRadius: 3), with: brass)
        ctx.fill(Path(roundedRect: CGRect(x: g.cx + 20, y: top - 12, width: 9, height: 14), cornerRadius: 3), with: brass)
    }
}
