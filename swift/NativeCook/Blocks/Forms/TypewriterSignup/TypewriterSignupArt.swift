import SwiftUI
import UIKit

/*
 Typewriter Sign Up — static artwork, rendered once into images, and the geometry they share.

 Front view of a desk typewriter: a cream sheet stands up out of the carriage, the black rubber
 platen and its chrome knobs move with the carriage, a chrome paper bail rides over the sheet,
 and under the platen the typebar basket shows its fan of nine bars around the chrome segment.
 The deck's enamel front hides their pivots. Everything is drawn in points.
*/
enum TypewriterSignupArt {
    // Sheet, in its own coordinates (origin at its top-left corner).
    static let paperW = 300.0
    static let paperH = 340.0
    /// Baseline of the first typed line, and the line feed.
    static let topPad = 76.0
    static let lineH = 34.0
    /// Where typed text starts on the sheet (after the printed labels).
    static let textX = 108.0
    static let labels = ["NAME", "EMAIL", "PASSWORD"]

    static let platenR = 22.0
    /// How far the platen reaches past each side of the sheet.
    static let platenOver = 44.0

    /// Fan geometry: pivot radius, rest tip radius.
    static let segR0 = 30.0
    static let segR1 = 66.0

    /// Height of the fields and the button under the machine, by layout (screen points).
    static let formRegular = 3.0 * 56 + 2 * 8 + 16 + 52
    static let formCompact = 3.0 * 52 + 2 * 8 + 16 + 48
    /// Largest scale of the machine on tall screens.
    static let maxScale = 1.25

    // Colours.
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255).opacity(a)
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(red: r / 255, green: g / 255, blue: b / 255).opacity(a)
    }

    static let desk = [hex(0x2B211B), hex(0x1C1613), hex(0x110D0B)]
    static let paper = [hex(0xF6EFE0), hex(0xF0E6D1), hex(0xE7DBC2)]
    static let ink = hex(0x2B2420)
    static let cream = hex(0xF1E8D8)
    static let creamSoft = rgba(241, 232, 216, 0.62)
    static let enamel = [hex(0x24302A), hex(0x17201C), hex(0x0D1310)]
    static let chrome = [hex(0x5C6166), hex(0xE9EDF0), hex(0x9AA1A8), hex(0x3D4247)]
    static let brass = hex(0xCFAE6D)

    static let inkUI = UIColor(red: 0x2B / 255, green: 0x24 / 255, blue: 0x20 / 255, alpha: 1)

    // Fonts (Menlo stands in for the typewriter face, as in React Native).
    static let typedFont = UIFont(name: "Menlo-Regular", size: 15) ?? .monospacedSystemFont(ofSize: 15, weight: .medium)
    static let labelFont = UIFont(name: "Menlo-Bold", size: 8.5) ?? .monospacedSystemFont(ofSize: 8.5, weight: .semibold)
    static let headerFont = UIFont(name: "Menlo-Regular", size: 8) ?? .monospacedSystemFont(ofSize: 8, weight: .medium)
    static let plateFont = UIFont(name: "Menlo-Bold", size: 9) ?? .monospacedSystemFont(ofSize: 9, weight: .bold)
    /// One glyph's advance in the typed face.
    static let adv: Double = {
        let w = ("M" as NSString).size(withAttributes: [.font: typedFont]).width
        return w > 0 ? Double(w) : 9
    }()

    static func width(_ s: String, _ font: UIFont) -> Double {
        Double((s as NSString).size(withAttributes: [.font: font]).width)
    }

    /// Draws `s` with its baseline at `y`, as Skia's `drawText` does.
    static func text(_ c: inout GraphicsContext, _ s: String, x: Double, y: Double, font: UIFont, color: Color) {
        let t = Text(s).font(Font(font as CTFont)).foregroundColor(color)
        c.draw(t, at: CGPoint(x: x, y: y - Double(font.ascender)), anchor: .topLeading)
    }

    static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ colors: [Color], _ pos: [Double]? = nil) -> GraphicsContext.Shading {
        let stops = colors.enumerated().map { i, c in
            Gradient.Stop(color: c, location: pos?[i] ?? (colors.count > 1 ? Double(i) / Double(colors.count - 1) : 0))
        }
        return .linearGradient(Gradient(stops: stops), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    static func shadow(_ c: inout GraphicsContext, _ r: CGRect, radius: Double, blur: Double, dy: Double, alpha: Double) {
        c.drawLayer { l in
            l.addFilter(.blur(radius: blur))
            l.fill(Path(roundedRect: r.offsetBy(dx: 0, dy: dy), cornerRadius: radius), with: .color(.black.opacity(alpha)))
        }
    }

    /// Renders `draw` (in the coordinates of `rect`) into an image, sharp at `scale` × the screen.
    @MainActor
    static func render(_ rect: CGRect, scale: Double, _ draw: @escaping (inout GraphicsContext) -> Void) -> Image {
        let view = Canvas { ctx, _ in
            ctx.translateBy(x: -rect.minX, y: -rect.minY)
            draw(&ctx)
        }
        .frame(width: rect.width, height: rect.height)
        let r = ImageRenderer(content: view)
        r.scale = scale
        r.isOpaque = false
        guard let ui = r.uiImage else { return Image(uiImage: UIImage()) }
        return Image(uiImage: ui)
    }
}

/**
 The machine is laid out in its own points and drawn scaled by `scale` about the screen's
 top-left corner: w, h, cx and every y below are machine points (screen = machine × scale).
 `screenW`, `screenH`, `headerTop` and `formTop` are screen points.
*/
struct TypewriterSignupGeo: Equatable {
    var w: Double, h: Double, cx: Double, scale: Double
    var screenW: Double, screenH: Double
    /// Where the fields start (screen points).
    var formTop: Double
    /// The typing line's baseline (the strike point's y), the platen's centre, the rail.
    var sy: Double, platenY: Double, railY: Double
    /// Centre of the typebar segment, and the top of the deck's front panel.
    var segX: Double, segY: Double, deckY: Double
    /// Bottom of the typewriter (the form starts below).
    var machineBottom: Double
    var headerTop: Double
    var compact: Bool

    init(w W: Double, h H: Double, insetTop: Double, insetBottom: Double) {
        typealias A = TypewriterSignupArt
        let belowLine = 8 + A.platenR * 2 + 4 + 82 + 58
        let sheetRise = A.topPad + 2 * A.lineH
        compact = H < 740
        var scale = 1.0
        let sy: Double
        if compact {
            // Small screens: the machine at its own size right under the header, the fields after it.
            headerTop = insetTop + 18
            sy = headerTop + 64 + 10 + 96
        } else {
            // The button sits just above the home indicator; the machine takes all the room between
            // the fields and the header, scaled so even the fully typed sheet stays below the subtitle.
            headerTop = insetTop + 12
            let sheetLimit = headerTop + 84 + 10
            let mb = H - insetBottom - 16 - A.formRegular - 16
            scale = max(1, min(A.maxScale, (mb - sheetLimit) / (sheetRise + belowLine)))
            sy = mb / scale - belowLine
        }
        self.scale = scale
        self.sy = sy
        platenY = sy + 8 + A.platenR
        railY = platenY + A.platenR + 4
        segY = railY + 82
        deckY = segY - 6
        machineBottom = segY + 58
        let mw = W / scale
        w = mw
        h = H / scale
        cx = mw / 2
        segX = mw / 2
        screenW = W
        screenH = H
        formTop = machineBottom * scale + 16
    }

    /// Bar i's rest angle (screen radians, up = −π/2).
    func barAngle(_ i: Int) -> Double {
        .pi + ((Double(i) + 0.5) / Double(TypewriterSignupMotion.bars)) * .pi
    }

    /// Typebar i at extension e (0 rest … 1 at the platen): pivot and tip.
    func barTip(_ i: Int, _ e: Double) -> (CGPoint, CGPoint) {
        typealias A = TypewriterSignupArt
        let a0 = barAngle(i)
        let px = segX + cos(a0) * A.segR0
        let py = segY + sin(a0) * A.segR0
        let l0 = A.segR1 - A.segR0
        let tx = cx - px
        let ty = sy - 5 - py
        var a1 = atan2(ty, tx)
        while a1 - a0 > .pi { a1 -= 2 * .pi }
        while a1 - a0 < -.pi { a1 += 2 * .pi }
        let a = a0 + (a1 - a0) * e
        let len = l0 + (hypot(tx, ty) - l0) * e
        return (CGPoint(x: px, y: py), CGPoint(x: px + cos(a) * len, y: py + sin(a) * len))
    }
}

/// The five static pictures, each with the rectangle (in its own coordinates) it covers.
struct TypewriterSignupImages {
    var background: Image
    var paper: Image
    var paperRect: CGRect
    var carriage: Image
    var carriageRect: CGRect
    var body: Image
    var bodyRect: CGRect
    var deck: Image
    var deckRect: CGRect

    @MainActor private static var cache: [String: TypewriterSignupImages] = [:]

    /// The pictures for one layout, rendered once per process.
    @MainActor
    static func cached(g: TypewriterSignupGeo, displayScale: Double) -> TypewriterSignupImages {
        let key = "\(g.screenW)x\(g.screenH)@\(displayScale)|\(g.sy)|\(g.headerTop)"
        if let hit = cache[key] { return hit }
        let made = TypewriterSignupImages(g: g, displayScale: displayScale)
        cache[key] = made
        return made
    }

    @MainActor
    init(g: TypewriterSignupGeo, displayScale: Double) {
        typealias A = TypewriterSignupArt
        let hash = TypewriterSignupMotion.hash
        let ms = displayScale * g.scale

        // Desk, lamp glow, vignette, grain and the header copy (the sheet may fly over it).
        background = A.render(CGRect(x: 0, y: 0, width: g.screenW, height: g.screenH), scale: displayScale) { c in
            let full = Path(CGRect(x: 0, y: 0, width: g.screenW, height: g.screenH))
            c.fill(full, with: A.linear(0, 0, 0, g.screenH, A.desk, [0, 0.55, 1]))
            c.fill(full, with: .radialGradient(
                Gradient(stops: [
                    .init(color: A.rgba(255, 214, 160, 0.13), location: 0),
                    .init(color: A.rgba(255, 214, 160, 0.04), location: 0.5),
                    .init(color: .clear, location: 1),
                ]),
                center: CGPoint(x: g.screenW / 2, y: g.sy * g.scale - 40), startRadius: 0, endRadius: g.screenW * 0.9))
            c.fill(full, with: .radialGradient(
                Gradient(stops: [
                    .init(color: .black.opacity(0), location: 0.45),
                    .init(color: .black.opacity(0.18), location: 0.75),
                    .init(color: .black.opacity(0.55), location: 1),
                ]),
                center: CGPoint(x: g.screenW / 2, y: g.screenH * 0.45), startRadius: 0, endRadius: g.screenH * 0.75))
            // Wood grain: long faint strokes, seeded.
            var grain = Path()
            for i in 0..<46 {
                let d = Double(i)
                let y = hash(d * 3.1) * g.screenH
                grain.move(to: CGPoint(x: -10, y: y))
                grain.addCurve(
                    to: CGPoint(x: g.screenW + 10, y: y + (hash(d + 13) - 0.5) * 20),
                    control1: CGPoint(x: g.screenW * 0.3, y: y + (hash(d + 7) - 0.5) * 30),
                    control2: CGPoint(x: g.screenW * 0.7, y: y + (hash(d + 11) - 0.5) * 30))
            }
            c.stroke(grain, with: .color(A.hex(0xF3D9B8, 0.035)), lineWidth: 1)
            var dots = Path()
            for i in 0..<700 {
                let d = Double(i)
                dots.addEllipse(in: CGRect(x: hash(d * 1.7) * g.screenW - 0.6, y: hash(d * 2.3 + 1) * g.screenH - 0.6, width: 1.2, height: 1.2))
            }
            c.fill(dots, with: .color(.white.opacity(0.025)))

            let kicker = UIFont.systemFont(ofSize: 11, weight: .bold)
            let title = UIFont.systemFont(ofSize: g.compact ? 25 : 28, weight: .bold)
            let small = UIFont.systemFont(ofSize: 15, weight: .regular)
            A.text(&c, "INKWELL", x: 24, y: g.headerTop + 12, font: kicker, color: A.brass)
            A.text(&c, "Start your first draft", x: 24, y: g.headerTop + 46, font: title, color: A.cream)
            if !g.compact {
                A.text(&c, "Three lines and the page is yours.", x: 24, y: g.headerTop + 74, font: small, color: A.creamSoft)
            }
        }

        // The sheet with its shadow, in sheet coordinates: cream stock, fibres, faint rules, a
        // margin and the printed labels.
        paperRect = CGRect(x: -60, y: -60, width: A.paperW + 120, height: A.paperH + 120)
        paper = A.render(paperRect, scale: ms) { c in
            A.shadow(&c, CGRect(x: 4, y: 10, width: A.paperW - 8, height: A.paperH), radius: 2, blur: 14, dy: 0, alpha: 0.42)
            let r = Path(CGRect(x: 0, y: 0, width: A.paperW, height: A.paperH))
            c.fill(r, with: A.linear(0, 0, A.paperW, A.paperH, A.paper, [0, 0.5, 1]))
            // Edge darkening and a cooler lower half where the platen shades it.
            c.fill(r, with: A.linear(0, 0, A.paperW, 0, [A.rgba(120, 96, 60, 0.10), A.rgba(120, 96, 60, 0), A.rgba(120, 96, 60, 0), A.rgba(120, 96, 60, 0.12)], [0, 0.08, 0.92, 1]))
            c.fill(r, with: A.linear(0, 0, 0, A.paperH, [.white.opacity(0.35), .white.opacity(0), A.rgba(90, 70, 40, 0.10)], [0, 0.2, 1]))
            var fibre = Path()
            for i in 0..<260 {
                let d = Double(i)
                let x = hash(d * 5.3) * A.paperW
                let y = hash(d * 7.9 + 2) * A.paperH
                let a = hash(d * 1.3) * .pi
                let l = 2 + hash(d * 3.7) * 6
                fibre.move(to: CGPoint(x: x, y: y))
                fibre.addLine(to: CGPoint(x: x + cos(a) * l, y: y + sin(a) * l))
            }
            c.stroke(fibre, with: .color(A.hex(0x8A6F4A, 0.06)), style: StrokeStyle(lineWidth: 0.6, lineCap: .round))
            var speck = Path()
            for i in 0..<500 {
                let d = Double(i)
                speck.addEllipse(in: CGRect(x: hash(d * 2.1 + 9) * A.paperW - 0.45, y: hash(d * 4.4 + 3) * A.paperH - 0.45, width: 0.9, height: 0.9))
            }
            c.fill(speck, with: .color(A.hex(0x6B5536, 0.08)))

            // Printed head of the form.
            let printed = A.hex(0x7C6A55, 0.9)
            A.text(&c, "INKWELL  ·  NEW WRITER", x: 24, y: 38, font: A.headerFont, color: printed)
            A.text(&c, "DRAFT 0001", x: A.paperW - 24 - A.width("DRAFT 0001", A.headerFont), y: 38, font: A.headerFont, color: printed)
            c.fill(Path(CGRect(x: 24, y: 48, width: A.paperW - 48, height: 0.8)), with: .color(A.hex(0x7C6A55, 0.5)))
            // Margin and rules.
            c.fill(Path(CGRect(x: A.textX - 10, y: 56, width: 0.8, height: A.paperH - 70)), with: .color(A.hex(0xC0504A, 0.28)))
            var rules = Path()
            for l in 0..<7 {
                rules.addRect(CGRect(x: 20, y: A.topPad + Double(l) * A.lineH + 6, width: A.paperW - 40, height: 0.7))
            }
            c.fill(rules, with: .color(A.hex(0x5B7A9A, 0.13)))
            for (l, t) in A.labels.enumerated() {
                A.text(&c, t, x: 24, y: A.topPad + Double(l) * A.lineH - 1, font: A.labelFont, color: A.hex(0x8D7B66))
            }
        }

        // The carriage, in carriage coordinates: x = 0 at the sheet's left edge, y as on the
        // canvas. Black rubber platen with a soft highlight, chrome end knobs, and the paper bail
        // with two small rubber wheels over the sheet.
        carriageRect = CGRect(x: -A.platenOver - 40, y: g.sy - 80, width: A.paperW + A.platenOver * 2 + 80, height: g.platenY + A.platenR + 50 - (g.sy - 80))
        carriage = A.render(carriageRect, scale: ms) { c in
            let x0 = -A.platenOver
            let w = A.paperW + A.platenOver * 2
            let top = g.platenY - A.platenR
            A.shadow(&c, CGRect(x: x0, y: top, width: w, height: A.platenR * 2), radius: A.platenR, blur: 10, dy: 10, alpha: 0.45)
            c.fill(
                Path(roundedRect: CGRect(x: x0, y: top, width: w, height: A.platenR * 2), cornerRadius: 6),
                with: A.linear(0, top, 0, top + A.platenR * 2, [A.hex(0x3D3833), A.hex(0x57514B), A.hex(0x25211E), A.hex(0x110F0D), A.hex(0x070605)], [0, 0.22, 0.5, 0.82, 1]))
            c.fill(Path(CGRect(x: x0 + 6, y: top + 6, width: w - 12, height: 1.2)), with: .color(.white.opacity(0.14)))
            // Knobs.
            for kx in [x0 - 16, x0 + w + 2] {
                let kr = CGRect(x: kx, y: top - 8, width: 14, height: A.platenR * 2 + 16)
                A.shadow(&c, kr, radius: 5, blur: 4, dy: 3, alpha: 0.4)
                c.fill(Path(roundedRect: kr, cornerRadius: 5), with: A.linear(kx, 0, kx + 14, 0, A.chrome, [0, 0.35, 0.65, 1]))
                var knurl = Path()
                for i in 0..<9 { knurl.addRect(CGRect(x: kx + 1, y: top - 4 + Double(i) * 5.6, width: 12, height: 0.8)) }
                c.fill(knurl, with: .color(A.hex(0x202326, 0.35)))
            }
            // Paper bail.
            let by = g.sy - 52
            c.fill(Path(CGRect(x: x0 + 8, y: by + 3, width: w - 16, height: 3)), with: .color(.black.opacity(0.18)))
            c.fill(
                Path(roundedRect: CGRect(x: x0 + 8, y: by, width: w - 16, height: 3.2), cornerRadius: 1.6),
                with: A.linear(0, by, 0, by + 3.2, [A.hex(0xF2F4F6), A.hex(0x9AA0A6), A.hex(0x4A4F54)]))
            for wx in [70.0, A.paperW - 70] {
                let wr = CGRect(x: wx - 7, y: by - 3, width: 14, height: 10)
                A.shadow(&c, wr, radius: 4, blur: 2, dy: 2, alpha: 0.35)
                c.fill(Path(roundedRect: wr, cornerRadius: 4), with: A.linear(0, by - 3, 0, by + 7, [A.hex(0x4A443F), A.hex(0x1D1A18), A.hex(0x0B0A09)]))
            }
        }

        // The machine behind the bars: rail, enamel body with the basket opening, the chrome
        // segment, the type guide.
        bodyRect = CGRect(x: -40, y: g.railY - 20, width: g.w + 80, height: g.machineBottom - g.railY + 120)
        body = A.render(bodyRect, scale: ms) { c in
            let left = 10.0
            let w = g.w - 20
            let top = g.railY + 7
            let br = CGRect(x: left, y: top, width: w, height: g.machineBottom - top)
            A.shadow(&c, br, radius: 26, blur: 26, dy: 18, alpha: 0.5)
            A.shadow(&c, br, radius: 26, blur: 3, dy: 2, alpha: 0.4)
            c.fill(Path(roundedRect: br, cornerRadius: 26), with: A.linear(0, top, 0, g.machineBottom, A.enamel, [0, 0.5, 1]))
            // Basket opening.
            let open = Path(ellipseIn: CGRect(x: g.segX - 120, y: g.segY - 72, width: 240, height: 144))
            c.drawLayer { l in
                l.clip(to: Path(CGRect(x: 0, y: 0, width: g.w, height: g.segY)))
                l.fill(open, with: A.linear(0, g.segY - 72, 0, g.segY, [A.hex(0x050404), A.hex(0x0D0B0A), A.hex(0x171311)]))
                l.stroke(open, with: .color(A.hex(0x9AA1A8, 0.35)), lineWidth: 1)
            }
            // Chrome segment the bars pivot on.
            var arc = Path()
            arc.addArc(center: CGPoint(x: g.segX, y: g.segY), radius: A.segR0, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
            c.stroke(arc, with: A.linear(g.segX - 30, 0, g.segX + 30, 0, A.chrome, [0, 0.4, 0.7, 1]), lineWidth: 6)
            // Rail.
            c.fill(Path(CGRect(x: 0, y: g.railY + 2, width: g.w, height: 7)), with: .color(.black.opacity(0.35)))
            c.fill(Path(CGRect(x: 0, y: g.railY, width: g.w, height: 6)), with: A.linear(0, g.railY, 0, g.railY + 6, [A.hex(0xF0F2F4), A.hex(0xA3A9AF), A.hex(0x50555A)]))
            // Type guide: a small chrome plate with a V notch under the typing point.
            let gy = g.sy + 3
            var guide = Path()
            guide.move(to: CGPoint(x: g.cx - 15, y: gy + 2))
            guide.addLine(to: CGPoint(x: g.cx - 3, y: gy + 2))
            guide.addLine(to: CGPoint(x: g.cx, y: gy + 7))
            guide.addLine(to: CGPoint(x: g.cx + 3, y: gy + 2))
            guide.addLine(to: CGPoint(x: g.cx + 15, y: gy + 2))
            guide.addLine(to: CGPoint(x: g.cx + 12, y: gy + 14))
            guide.addLine(to: CGPoint(x: g.cx - 12, y: gy + 14))
            guide.closeSubpath()
            c.fill(guide, with: A.linear(0, gy, 0, gy + 14, [A.hex(0xF5F7F8), A.hex(0xA9AFB5), A.hex(0x5A5F64)]))
        }

        // The deck's front panel over the bar pivots, with its nameplate.
        deckRect = CGRect(x: 0, y: g.deckY - 4, width: g.w, height: g.machineBottom - g.deckY + 8)
        deck = A.render(deckRect, scale: ms) { c in
            let left = 10.0
            let w = g.w - 20
            let top = g.deckY
            let h = g.machineBottom - top
            c.fill(Path(roundedRect: CGRect(x: left, y: top, width: w, height: h), cornerRadius: 22), with: A.linear(0, top, 0, top + h, [A.hex(0x2C3A33), A.hex(0x1B2621), A.hex(0x101713)], [0, 0.4, 1]))
            c.fill(Path(CGRect(x: left + 22, y: top + 0.5, width: w - 44, height: 1)), with: .color(.white.opacity(0.16)))
            c.fill(Path(CGRect(x: left + 18, y: top + 7, width: w - 36, height: 1.6)), with: A.linear(0, 0, g.w, 0, [A.rgba(207, 174, 109, 0), A.rgba(207, 174, 109, 0.7), A.rgba(207, 174, 109, 0)]))
            let label = "INKWELL  No. 4"
            let tw = A.width(label, A.plateFont)
            let py = top + 24
            c.fill(Path(roundedRect: CGRect(x: g.cx - tw / 2 - 12, y: py - 4, width: tw + 24, height: 20), cornerRadius: 4), with: A.linear(0, py - 4, 0, py + 16, [A.hex(0xE6CF98), A.hex(0xB48A45), A.hex(0x7D5B26)]))
            A.text(&c, label, x: g.cx - tw / 2, y: py + 10, font: A.plateFont, color: A.hex(0x3A2A14))
        }
    }
}

/// Bitmaps of single typed glyphs (one per character and ink colour), so a frame draws images
/// instead of laying out text.
@MainActor
final class TypewriterSignupGlyphs {
    static let pad = 3.0
    private var cache: [String: Image] = [:]
    let size: CGSize
    let ascender: Double

    init() {
        let f = TypewriterSignupArt.typedFont
        ascender = Double(f.ascender)
        size = CGSize(width: TypewriterSignupArt.adv + Self.pad * 2, height: Double(f.ascender - f.descender) + Self.pad * 2)
    }

    /// The glyph `ch` in `color`; the image's baseline sits `pad + ascender` from its top.
    func image(_ ch: Character, ink: Int, color: UIColor) -> Image {
        let key = "\(ink)|\(ch)"
        if let img = cache[key] { return img }
        let r = UIGraphicsImageRenderer(size: size)
        let ui = r.image { _ in
            (String(ch) as NSString).draw(at: CGPoint(x: Self.pad, y: Self.pad), withAttributes: [
                .font: TypewriterSignupArt.typedFont,
                .foregroundColor: color,
            ])
        }
        let img = Image(uiImage: ui)
        cache[key] = img
        return img
    }
}
