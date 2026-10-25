import CoreText
import SwiftUI
import UIKit

/*
 Chalkboard Search — the board's artwork and the chalk.

 The slate (dark green with old eraser swirls, grain and a vignette), the oak frame and ledge,
 the café's name and "What's cooking?" in chalk, the magnifier, the search line's underline and
 a small doodle are drawn by a Canvas that does not depend on time, so SwiftUI draws it once.
 Live chalk (the query and the suggestions) is a glyph outline filled with the chalk shader: an
 off-white whose coverage breaks up with two noises and a fine grain.
*/
struct ChalkboardSearchGeo: Equatable {
    static let frame = 14.0

    var w: Double, h: Double
    /// Outer board and the slate inside its frame.
    var boardX: Double, boardY: Double, boardW: Double, boardH: Double
    var slateX: Double, slateY: Double, slateW: Double, slateH: Double
    /// Query: left edge and baseline; suggestion lines: left edge and baselines.
    var qx: Double, qy: Double, sx: Double
    var sy: [Double]
    var doodle: Bool

    init(w: Double, h: Double, insetTop: Double) {
        self.w = w
        self.h = h
        boardX = 12
        boardY = insetTop + 4
        boardW = w - 24
        boardH = max(400, min(520, (h * 0.58).rounded()))
        slateX = boardX + Self.frame
        slateY = boardY + Self.frame
        slateW = boardW - Self.frame * 2
        slateH = boardH - Self.frame * 2 - 10
        qx = slateX + 52
        qy = slateY + 146
        sx = slateX + 36
        sy = [slateY + 212, slateY + 256, slateY + 300]
        doodle = boardH >= 470
    }

    /// Height of the area the board's canvas covers.
    var canvasH: Double { boardY + boardH + 24 }
}

struct ChalkboardSearchLine {
    var path: Path
    /// Each glyph's left and right edge (pt), for the pen's reveal and the dust.
    var lefts: [Double]
    var rights: [Double]
    var spaces: [Bool]
    /// Right edge of the whole line.
    var end: Double
}

enum ChalkboardSearchArt {
    static let cafeFont = font(16, bold: false)
    static let titleFont = font(32, bold: true)
    static let smallFont = font(14, bold: false)
    static let queryFont = font(30, bold: false)
    static let sugFont = font(21, bold: false)
    static let phFont = font(22, bold: false)

    private static func font(_ size: Double, bold: Bool) -> CTFont {
        let ui = UIFont(name: bold ? "ChalkboardSE-Bold" : "ChalkboardSE-Regular", size: size) ?? .systemFont(ofSize: size, weight: bold ? .bold : .regular)
        return ui as CTFont
    }

    static let hash = ChalkboardSearchMotion.hash

    /// A line of text as one chalk outline, each glyph nudged off the baseline and tilted a little.
    static func chalkLine(_ text: String, font: CTFont, x: Double, baseline: Double, seed: Double) -> ChalkboardSearchLine {
        var path = Path()
        var lefts: [Double] = []
        var rights: [Double] = []
        var spaces: [Bool] = []
        var pen = x
        for (i, ch) in text.enumerated() {
            let units = Array(String(ch).utf16)
            var glyphs = [CGGlyph](repeating: 0, count: units.count)
            CTFontGetGlyphsForCharacters(font, units, &glyphs, units.count)
            var adv = CGSize.zero
            let width = CTFontGetAdvancesForGlyphs(font, .horizontal, [glyphs[0]], &adv, 1)
            let w = width > 0 ? Double(adv.width) : Double(CTFontGetSize(font)) * 0.5
            lefts.append(pen)
            rights.append(pen + w)
            spaces.append(ch == " ")
            if ch != " ", let g = CTFontCreatePathForGlyph(font, glyphs[0], nil) {
                let di = Double(i)
                let dy = (hash(seed * 13 + di * 7.1) - 0.5) * 2.4
                let rot = (hash(seed * 7 + di * 3.3) - 0.5) * 6
                // Core Text draws y up; the board is y down with the baseline at 0.
                let t = CGAffineTransform(scaleX: 1, y: -1)
                    .concatenating(CGAffineTransform(translationX: -w / 2, y: 0))
                    .concatenating(CGAffineTransform(rotationAngle: rot * .pi / 180))
                    .concatenating(CGAffineTransform(translationX: pen + w / 2, y: baseline + dy))
                path.addPath(Path(g), transform: t)
            }
            pen += w * 1.02
        }
        return ChalkboardSearchLine(path: path, lefts: lefts, rights: rights, spaces: spaces, end: pen)
    }

    static func chalkShading(_ seed: Double) -> GraphicsContext.Shading {
        .shader(ShaderLibrary.chalkboardSearchChalk(.float(seed)))
    }

    /// Fills a path with chalk: a soft haze, the chalk itself, and a rough second pass along the
    /// edge. `haze` false leaves the blurred haze to the caller (it batches them into one layer).
    static func drawChalk(_ c: inout GraphicsContext, _ path: Path, seed: Double, alpha: Double = 1, haze: Bool = true) {
        if haze { drawHaze(&c, path, alpha: alpha) }
        var k = c
        k.opacity = alpha
        k.fill(path, with: chalkShading(seed))
        k.opacity = 0.45 * alpha
        k.stroke(path.offsetBy(dx: 0.6, dy: 0.5), with: chalkShading(seed + 5), lineWidth: 0.9)
    }

    static func drawHaze(_ c: inout GraphicsContext, _ path: Path, alpha: Double = 1) {
        c.drawLayer { l in
            l.addFilter(.blur(radius: 3))
            l.fill(path, with: .color(Color(red: 238 / 255, green: 240 / 255, blue: 232 / 255).opacity(0.07 * alpha)))
        }
    }

    /// A hand-drawn chalk stroke (for the underline, icons and the doodle).
    static func chalkStroke(_ c: inout GraphicsContext, _ path: Path, width: Double, seed: Double) {
        for k in 0..<2 {
            var l = c
            l.opacity = k == 0 ? 0.85 : 0.5
            l.translateBy(x: Double(k) * 0.7, y: Double(k) * -0.5)
            l.stroke(path, with: chalkShading(seed + Double(k) * 9), style: StrokeStyle(lineWidth: width * (k == 0 ? 1 : 0.6), lineCap: .round))
        }
    }

    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255).opacity(a)
    }

    static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ colors: [UInt32], _ pos: [Double]? = nil) -> GraphicsContext.Shading {
        let stops = colors.enumerated().map { i, c in
            Gradient.Stop(color: hex(c), location: pos?[i] ?? Double(i) / Double(max(1, colors.count - 1)))
        }
        return .linearGradient(Gradient(stops: stops), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    /// The board: frame, slate, ledge and the chalk that never changes.
    static func drawBoard(_ c: inout GraphicsContext, _ g: ChalkboardSearchGeo) {
        // Frame: two shadows on the wall, then oak with grain.
        let outer = Path(roundedRect: CGRect(x: g.boardX, y: g.boardY, width: g.boardW, height: g.boardH), cornerRadius: 10)
        c.drawLayer { l in
            l.addFilter(.blur(radius: 18))
            l.fill(outer.offsetBy(dx: 0, dy: 14), with: .color(.black.opacity(0.45)))
        }
        c.fill(outer, with: linear(g.boardX, 0, g.boardX + g.boardW, g.boardH, [0x7A4C2A, 0xA8723F, 0x8A5A33, 0x5E3A1F], [0, 0.3, 0.7, 1]))
        var grain = Path()
        for i in 0..<40 {
            let d = Double(i)
            let y = g.boardY + hash(d * 2.7) * g.boardH
            grain.move(to: CGPoint(x: g.boardX, y: y))
            grain.addCurve(
                to: CGPoint(x: g.boardX + g.boardW, y: y),
                control1: CGPoint(x: g.boardX + g.boardW * 0.3, y: y + (hash(d + 3) - 0.5) * 8),
                control2: CGPoint(x: g.boardX + g.boardW * 0.7, y: y + (hash(d + 5) - 0.5) * 8))
        }
        c.drawLayer { l in
            l.clip(to: outer)
            l.stroke(grain, with: .color(hex(0x2F1B0C, 0.18)), lineWidth: 0.8)
        }
        c.fill(Path(roundedRect: CGRect(x: g.boardX + 1, y: g.boardY + 1, width: g.boardW - 2, height: 1.2), cornerRadius: 1), with: .color(hex(0xFFE2B8, 0.35)))

        // Slate, inset into the frame.
        let slateRect = CGRect(x: g.slateX, y: g.slateY, width: g.slateW, height: g.slateH)
        c.drawLayer { l in
            l.clip(to: Path(roundedRect: slateRect, cornerRadius: 4))
            l.fill(Path(slateRect), with: .shader(ShaderLibrary.chalkboardSearchSlate(.float2(g.slateX, g.slateY), .float2(g.slateW, g.slateH))))
        }
        c.drawLayer { l in
            l.clip(to: Path(slateRect))
            l.addFilter(.blur(radius: 3))
            l.stroke(Path(slateRect), with: .color(.black.opacity(0.55)), lineWidth: 3)
        }

        // Ledge with a stick of chalk.
        let ly = g.slateY + g.slateH
        c.fill(Path(CGRect(x: g.slateX - 4, y: ly, width: g.slateW + 8, height: 12)), with: linear(0, ly, 0, ly + 12, [0x5A3519, 0xB37B45, 0x6B4325]))
        c.fill(Path(roundedRect: CGRect(x: g.slateX + g.slateW * 0.66, y: ly - 3, width: 34, height: 6), cornerRadius: 3), with: linear(0, ly - 3, 0, ly + 3, [0xFBFAF4, 0xE2E0D6, 0xBDBAB0]))

        // Chalk copy.
        let cafe = chalkLine("Corner Café", font: cafeFont, x: g.slateX + 24, baseline: g.slateY + 38, seed: 2)
        drawChalk(&c, cafe.path, seed: 3, alpha: 0.85)
        let title = chalkLine("What's cooking?", font: titleFont, x: g.slateX + 24, baseline: g.slateY + 84, seed: 4)
        drawChalk(&c, title.path, seed: 5)

        // Magnifier and the search line.
        let mx = g.slateX + 34
        let my = g.qy - 10
        var glass = Path(ellipseIn: CGRect(x: mx - 8.5, y: my - 8.5, width: 17, height: 17))
        glass.move(to: CGPoint(x: mx + 6.2, y: my + 6.2))
        glass.addLine(to: CGPoint(x: mx + 13, y: my + 13))
        chalkStroke(&c, glass, width: 2.4, seed: 11)
        var under = Path()
        under.move(to: CGPoint(x: g.slateX + 22, y: g.qy + 14))
        under.addCurve(
            to: CGPoint(x: g.slateX + g.slateW - 22, y: g.qy + 13),
            control1: CGPoint(x: g.slateX + g.slateW * 0.35, y: g.qy + 12),
            control2: CGPoint(x: g.slateX + g.slateW * 0.65, y: g.qy + 17))
        chalkStroke(&c, under, width: 1.6, seed: 13)

        let today = chalkLine("Today: rhubarb crumble · $6", font: smallFont, x: g.slateX + 24, baseline: g.slateY + g.slateH - 22, seed: 6)
        drawChalk(&c, today.path, seed: 7, alpha: 0.7)

        if g.doodle {
            // A cup of coffee with steam, bottom right.
            let dx = g.slateX + g.slateW - 76
            let dy = g.slateY + g.slateH - 82
            var cup = Path()
            cup.move(to: CGPoint(x: dx, y: dy))
            cup.addLine(to: CGPoint(x: dx + 4, y: dy + 30))
            cup.addCurve(to: CGPoint(x: dx + 32, y: dy + 30), control1: CGPoint(x: dx + 6, y: dy + 36), control2: CGPoint(x: dx + 30, y: dy + 36))
            cup.addLine(to: CGPoint(x: dx + 36, y: dy))
            cup.closeSubpath()
            cup.move(to: CGPoint(x: dx + 35, y: dy + 7))
            cup.addCurve(to: CGPoint(x: dx + 33, y: dy + 22), control1: CGPoint(x: dx + 48, y: dy + 6), control2: CGPoint(x: dx + 48, y: dy + 22))
            cup.move(to: CGPoint(x: dx - 8, y: dy + 40))
            cup.addCurve(to: CGPoint(x: dx + 46, y: dy + 40), control1: CGPoint(x: dx + 8, y: dy + 44), control2: CGPoint(x: dx + 30, y: dy + 44))
            for k in 0..<3 {
                let sxk = dx + 9 + Double(k) * 9
                cup.move(to: CGPoint(x: sxk, y: dy - 6))
                cup.addCurve(to: CGPoint(x: sxk, y: dy - 26), control1: CGPoint(x: sxk - 5, y: dy - 12), control2: CGPoint(x: sxk + 5, y: dy - 18))
            }
            chalkStroke(&c, cup, width: 2, seed: 17)
        }
    }

    /// The felt eraser, centred on (0, 0), its long side across the wipe; rendered once.
    static let eraserRect = CGRect(x: -40, y: -56, width: 90, height: 124)

    @MainActor
    static func renderEraser(scale: Double) -> Image {
        let r = eraserRect
        let view = Canvas { c, _ in
            c.translateBy(x: -r.minX, y: -r.minY)
            c.drawLayer { l in
                l.addFilter(.blur(radius: 8))
                l.fill(Path(roundedRect: CGRect(x: -12 + 6, y: -30 + 8, width: 34, height: 70), cornerRadius: 7), with: .color(.black.opacity(0.35)))
            }
            c.fill(Path(roundedRect: CGRect(x: -15, y: -32, width: 30, height: 64), cornerRadius: 6), with: .color(hex(0xD9D3C5)))
            c.fill(Path(roundedRect: CGRect(x: -11, y: -32, width: 22, height: 64), cornerRadius: 5), with: .color(hex(0x8E5C33)))
            c.fill(Path(roundedRect: CGRect(x: -9, y: -30, width: 4, height: 60), cornerRadius: 2), with: .color(Color(red: 1, green: 226 / 255, blue: 184 / 255).opacity(0.45)))
            c.fill(Path(roundedRect: CGRect(x: 5, y: -30, width: 3, height: 60), cornerRadius: 1.5), with: .color(Color(red: 40 / 255, green: 20 / 255, blue: 8 / 255).opacity(0.35)))
        }
        .frame(width: r.width, height: r.height)
        let renderer = ImageRenderer(content: view)
        renderer.scale = scale
        renderer.isOpaque = false
        return Image(uiImage: renderer.uiImage ?? UIImage())
    }

    /// The eraser's path in the loop: four passes over the query and the three menu lines, and
    /// 121 samples along it (x, y, heading).
    static func trail(_ g: ChalkboardSearchGeo) -> (Path, [(Double, Double, Double)]) {
        var b = Path()
        let l = g.slateX + 22
        let r = g.slateX + g.slateW - 22
        let ys = [g.qy - 12, g.sy[0] - 8, g.sy[1] - 8, g.sy[2] - 8]
        b.move(to: CGPoint(x: l, y: ys[0]))
        for k in 0..<ys.count {
            let from = k % 2 == 0 ? l : r
            let to = k % 2 == 0 ? r : l
            if k > 0 {
                let off = k % 2 == 0 ? -30.0 : 30.0
                b.addCurve(to: CGPoint(x: from, y: ys[k]), control1: CGPoint(x: from + off, y: ys[k - 1] + 6), control2: CGPoint(x: from + off, y: ys[k] - 6))
            }
            b.addLine(to: CGPoint(x: to, y: ys[k] + (hash(Double(k)) - 0.5) * 6))
        }
        var pts: [CGPoint] = []
        for i in 0...120 {
            let f = Double(i) / 120
            pts.append(b.trimmedPath(from: 0, to: max(f, 0.0001)).currentPoint ?? CGPoint(x: l, y: ys[0]))
        }
        var samples: [(Double, Double, Double)] = []
        for i in 0...120 {
            let a = pts[max(0, i - 1)]
            let c = pts[min(120, i + 1)]
            samples.append((pts[i].x, pts[i].y, atan2(c.y - a.y, c.x - a.x)))
        }
        return (b, samples)
    }
}
