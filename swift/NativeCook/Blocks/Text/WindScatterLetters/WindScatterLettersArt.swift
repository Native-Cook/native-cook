import CoreText
import SwiftUI
import UIKit

/*
 Wind Scatter Letters — static artwork: an autumn dusk sky over two ridges, drawn by a Canvas
 that does not depend on time (so SwiftUI draws it once), the leaf shape, the wind streaks and
 the headline's glyph outlines.

 Sky: a four-stop gradient from plum-grey to ember near the horizon, a low sun glow, a few long
 cloud bands, grain and a vignette. Two ridge silhouettes, the near one with a lit rim. The leaf
 is one path (blade + stem) centred on its middle, 24 pt long, drawn three times per frame in
 three colours with per-leaf transforms.
*/
enum WindScatterLettersArt {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255).opacity(a)
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(red: r / 255, green: g / 255, blue: b / 255).opacity(a)
    }

    static let sky: [UInt32] = [0x16151B, 0x25202A, 0x4A2D2C, 0x9A5333]
    static let cream = hex(0xF4E7D4)
    static let creamSoft = rgba(244, 231, 212, 0.72)
    static let ember = hex(0xE8A86C)
    static let leaves = [hex(0xC4572E), hex(0xD99A3A), hex(0x8F2F23)]

    private static let hash = WindScatterLettersMotion.hash

    /// y of the near ridge's crest at x (a few slow sines), shared by the art and the resting leaves.
    static func ridgeY(_ x: Double, w W: Double, h H: Double) -> Double {
        let base = H * 0.8
        return base - 18 * sin((x / W) * .pi * 1.3 + 0.4) - 7 * sin((x / W) * .pi * 4.1 + 1.2)
    }

    static func drawBackground(_ c: inout GraphicsContext, w W: Double, h H: Double) {
        let full = Path(CGRect(x: 0, y: 0, width: W, height: H))
        c.fill(full, with: .linearGradient(
            Gradient(stops: zip(sky, [0, 0.38, 0.72, 1]).map { Gradient.Stop(color: hex($0), location: $1) }),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: H * 0.84)))
        c.fill(full, with: .radialGradient(
            Gradient(stops: [
                .init(color: rgba(255, 170, 96, 0.42), location: 0),
                .init(color: rgba(232, 120, 70, 0.14), location: 0.4),
                .init(color: .clear, location: 1),
            ]),
            center: CGPoint(x: W * 0.72, y: H * 0.79), startRadius: 0, endRadius: W * 0.75))
        // Cloud bands.
        for i in 0..<5 {
            let d = Double(i)
            let y = H * (0.5 + 0.05 * d) + (hash(d) - 0.5) * 20
            let rect = CGRect(x: -40 + hash(d + 3) * W * 0.4, y: y, width: W * (0.6 + 0.5 * hash(d + 7)), height: 8 + 10 * hash(d + 9))
            c.drawLayer { l in
                l.addFilter(.blur(radius: 10))
                l.fill(Path(roundedRect: rect, cornerRadius: 10), with: .color(i % 2 == 1 ? rgba(255, 190, 140, 0.07) : rgba(30, 20, 30, 0.18)))
            }
        }
        // Ridges.
        var far = Path()
        far.move(to: CGPoint(x: 0, y: H))
        var x = 0.0
        while x <= W {
            far.addLine(to: CGPoint(x: x, y: H * 0.75 - 26 * sin((x / W) * .pi * 2.2 + 2.1) - 10 * sin((x / W) * 9.1)))
            x += 8
        }
        far.addLine(to: CGPoint(x: W, y: H))
        far.closeSubpath()
        c.fill(far, with: .linearGradient(Gradient(colors: [hex(0x3A2526), hex(0x2A1D20)]), startPoint: CGPoint(x: 0, y: H * 0.7), endPoint: CGPoint(x: 0, y: H)))
        var near = Path()
        near.move(to: CGPoint(x: 0, y: H))
        var rim = Path()
        rim.move(to: CGPoint(x: 0, y: ridgeY(0, w: W, h: H)))
        x = 0
        while x <= W {
            near.addLine(to: CGPoint(x: x, y: ridgeY(x, w: W, h: H)))
            if x > 0 { rim.addLine(to: CGPoint(x: x, y: ridgeY(x, w: W, h: H))) }
            x += 6
        }
        near.addLine(to: CGPoint(x: W, y: H))
        near.closeSubpath()
        c.fill(near, with: .linearGradient(
            Gradient(stops: [.init(color: hex(0x241619), location: 0), .init(color: hex(0x170F12), location: 0.4), .init(color: hex(0x0E0A0C), location: 1)]),
            startPoint: CGPoint(x: 0, y: H * 0.76), endPoint: CGPoint(x: 0, y: H)))
        c.stroke(rim, with: .linearGradient(
            Gradient(stops: [.init(color: rgba(255, 170, 100, 0.05), location: 0), .init(color: rgba(255, 170, 100, 0.45), location: 0.72), .init(color: rgba(255, 170, 100, 0.2), location: 1)]),
            startPoint: .zero, endPoint: CGPoint(x: W, y: 0)), lineWidth: 1.2)
        // Grain and vignette.
        var dots = Path()
        for i in 0..<1400 {
            let d = Double(i)
            dots.addEllipse(in: CGRect(x: hash(d * 1.37) * W - 0.6, y: hash(d * 2.71 + 5) * H - 0.6, width: 1.2, height: 1.2))
        }
        c.fill(dots, with: .color(rgba(255, 240, 220, 0.035)))
        c.fill(full, with: .radialGradient(
            Gradient(stops: [
                .init(color: .black.opacity(0), location: 0.45),
                .init(color: .black.opacity(0.22), location: 0.78),
                .init(color: .black.opacity(0.6), location: 1),
            ]),
            center: CGPoint(x: W / 2, y: H * 0.42), startRadius: 0, endRadius: H * 0.78))
    }

    /// The leaf blade, centred on (0, 0), pointing along +x.
    static let blade: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -12, y: 0))
        p.addCurve(to: CGPoint(x: 12, y: 0), control1: CGPoint(x: -7, y: -7.5), control2: CGPoint(x: 4, y: -8))
        p.addCurve(to: CGPoint(x: -12, y: 0), control1: CGPoint(x: 4, y: 7.2), control2: CGPoint(x: -7, y: 6.6))
        p.closeSubpath()
        return p
    }()

    /// The stem and veins.
    static let rib: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -16, y: 0.8))
        p.addQuadCurve(to: CGPoint(x: -10, y: 0), control: CGPoint(x: -12, y: 0.3))
        p.addLine(to: CGPoint(x: 10, y: 0))
        for x in [-5.0, 0, 5] {
            p.move(to: CGPoint(x: x, y: 0))
            p.addLine(to: CGPoint(x: x + 4, y: -3.6))
            p.move(to: CGPoint(x: x, y: 0))
            p.addLine(to: CGPoint(x: x + 4, y: 3.4))
        }
        return p
    }()

    /// Faint wind streaks: long, slightly curved strokes across the upper half (trimmed as they blow).
    static func streaks(w W: Double, h H: Double) -> [Path] {
        (0..<6).map { i in
            let d = Double(i)
            let y = H * (0.2 + 0.1 * d) + (hash(d * 4.1) - 0.5) * 30
            var p = Path()
            p.move(to: CGPoint(x: -20, y: y))
            p.addCurve(to: CGPoint(x: W + 20, y: y - 12), control1: CGPoint(x: W * 0.3, y: y - 30 * hash(d + 2)), control2: CGPoint(x: W * 0.6, y: y + 26 * hash(d + 5)))
            return p
        }
    }
}

/// The headline laid out once: each glyph's outline (origin on its baseline at its left edge),
/// its pivot, and every body's home.
struct WindScatterLettersLayout {
    static let lines = ["Let it", "drift"]
    static let maxSize = 96.0

    var paths: [Path] = []
    /// Pivot of each glyph relative to its origin (centre of the glyph box).
    var px: [Double] = []
    var py: [Double] = []
    var homes: [WindScatterLettersHome] = []
    var glyphs = 0
    var size = maxSize
    var b1 = 0.0
    var b2 = 0.0

    init(w W: Double, h H: Double, insetTop: Double) {
        func font(_ s: Double) -> CTFont {
            (UIFont(name: "Georgia-BoldItalic", size: s) ?? .italicSystemFont(ofSize: s)) as CTFont
        }
        func glyphsOf(_ f: CTFont, _ s: String) -> ([CGGlyph], [Double]) {
            var gs: [CGGlyph] = []
            for ch in s {
                let units = Array(String(ch).utf16)
                var g = [CGGlyph](repeating: 0, count: units.count)
                CTFontGetGlyphsForCharacters(f, units, &g, units.count)
                gs.append(g[0])
            }
            var adv = [CGSize](repeating: .zero, count: gs.count)
            CTFontGetAdvancesForGlyphs(f, .horizontal, gs, &adv, gs.count)
            return (gs, adv.map { Double($0.width) })
        }
        var f = font(Self.maxSize)
        let widest = Self.lines.map { glyphsOf(f, $0).1.reduce(0, +) }.max() ?? 0
        if widest > W - 48 {
            size = floor((Self.maxSize * (W - 48)) / widest)
            f = font(size)
        }
        b1 = insetTop + 92 + size * 0.95
        b2 = b1 + size * 0.98
        for (li, line) in Self.lines.enumerated() {
            let (gs, ws) = glyphsOf(f, line)
            var x = 24.0
            for (k, ch) in line.enumerated() {
                if ch != " " {
                    let outline = CTFontCreatePathForGlyph(f, gs[k], nil).map { Path($0) } ?? Path()
                    // Core Text draws y up.
                    paths.append(outline.applying(CGAffineTransform(scaleX: 1, y: -1)))
                    px.append(ws[k] / 2)
                    py.append(-size * 0.34)
                    homes.append(WindScatterLettersHome(x: x + ws[k] / 2, y: (li == 0 ? b1 : b2) - size * 0.34, reach: 1))
                }
                x += ws[k]
            }
        }
        glyphs = paths.count
        let at = [0.07, 0.2, 0.35, 0.6, 0.79, 0.93]
        for j in 0..<WindScatterLettersMotion.groundLeaves {
            let x = W * at[j]
            homes.append(WindScatterLettersHome(x: x, y: WindScatterLettersArt.ridgeY(x, w: W, h: H) - 3, reach: 0.5))
        }
    }
}
