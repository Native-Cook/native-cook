import SwiftUI
import UIKit

/*
 Drawing for Paint Mixer Picker. Drawn once: the linen table with grain and the wall card's
 shadows (a Canvas outside the timeline), and the card's room (floor, skirting, a framed print, a
 sideboard, a lamp; an image rendered once and laid over the wall colour each frame). Drawn per
 frame: the five paint tins (lifted when picked), drops in the air, splashes, the stirring stick,
 and the colour's name, code and swatch. A port of paintMixerArt.ts.
 */
enum PaintMixerPickerArt {
    typealias Geo = PaintMixerPickerGeo
    private typealias M = PaintMixerPickerMotion

    static let cardR = 22.0

    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    static func rgb(_ c: PaintMixerPickerRGB, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: c.0, green: c.1, blue: c.2, opacity: alpha)
    }

    static let ink = rgb(0x2B2620)
    static let inkSoft = rgb(0x7A7064)
    static let background = rgb(0xEDE6DA)

    static let labelFont = Font.system(size: 11, weight: .semibold)
    static let nameFont = Font.system(size: 22, weight: .bold)
    static let codeFont = Font.system(size: 12, weight: .medium, design: .monospaced)
    static let chipFont = Font.system(size: 11, weight: .semibold)

    static func cardRect(_ g: Geo) -> CGRect {
        CGRect(x: 24, y: g.cardY, width: g.w - 48, height: g.cardH)
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    private static func blurred(_ c: GraphicsContext, _ radius: Double, opacity: Double = 1, _ draw: (inout GraphicsContext) -> Void) {
        c.drawLayer { l in
            l.opacity = opacity
            l.addFilter(.blur(radius: radius))
            draw(&l)
        }
    }

    /// A disc blurred with a Gaussian of `sigma`, drawn as one radial gradient (the blur's profile)
    /// instead of an offscreen blur: cheap enough for many shadows a frame.
    static func softCircle(_ c: GraphicsContext, _ x: Double, _ y: Double, _ r: Double, sigma: Double, _ color: Color, alpha: Double) {
        let s = max(0.01, sigma)
        let outer = r + 3 * s
        let profile: [(Double, Double)] = [(-3, 0.9987), (-2, 0.977), (-1, 0.841), (0, 0.5), (1, 0.159), (2, 0.023), (3, 0)]
        var stops: [Gradient.Stop] = []
        for (k, a) in profile {
            let d = r + k * s
            if d < 0 { continue }
            stops.append(.init(color: color.opacity(alpha * a), location: d / outer))
        }
        if let first = stops.first, first.location > 0 {
            stops.insert(.init(color: first.color, location: 0), at: 0)
        }
        c.fill(circle(x, y, outer), with: .radialGradient(Gradient(stops: stops), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: outer))
    }

    private static let unbounded = CGSize(width: 10_000, height: 1_000)

    private static func text(_ c: GraphicsContext, _ string: String, font: Font, color: Color, x: Double, baseline y: Double, centered: Bool = false) {
        let resolved = c.resolve(Text(string).font(font).foregroundStyle(color))
        let size = resolved.measure(in: unbounded)
        let base = resolved.firstBaseline(in: unbounded)
        let left = centered ? x - size.width / 2 : x
        c.draw(resolved, in: CGRect(x: left, y: y - base, width: size.width + 1, height: size.height))
    }

    // MARK: - Once

    /// A tile of fine grey noise, made once, for the table's grain.
    private static let grain: Image = {
        let side = 128
        var state: UInt32 = 0x9E3779B9
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: {
            let f = UIGraphicsImageRendererFormat()
            f.scale = 1
            f.opaque = false
            return f
        }())
        let image = renderer.image { ctx in
            for y in 0..<side {
                for x in 0..<side {
                    state ^= state << 13
                    state ^= state >> 17
                    state ^= state << 5
                    let v = CGFloat(state & 0xFF) / 255
                    ctx.cgContext.setFillColor(UIColor(white: v, alpha: 1).cgColor)
                    ctx.cgContext.fill(CGRect(x: x, y: y, width: 1, height: 1))
                }
            }
        }
        return Image(uiImage: image)
    }()

    /// Table, grain and the card's two shadows.
    static func drawTable(_ c: GraphicsContext, _ g: Geo) {
        let all = Path(CGRect(x: 0, y: 0, width: g.w, height: g.h))
        c.fill(all, with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(0xF3EDE3), location: 0),
                .init(color: rgb(0xEBE3D6), location: 0.55),
                .init(color: rgb(0xE2D8C8), location: 1),
            ]),
            startPoint: .zero,
            endPoint: CGPoint(x: 0, y: g.h)
        ))
        var grainLayer = c
        grainLayer.opacity = 0.05
        grainLayer.fill(all, with: .tiledImage(grain, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 0.5))
        // A faint linen weave.
        var weave = Path()
        var y = 0.0
        while y < g.h {
            weave.addRect(CGRect(x: 0, y: y, width: g.w, height: 1))
            y += 3
        }
        c.fill(weave, with: .color(rgb(0x786446, 0.035)))
        let card = Path(roundedRect: cardRect(g), cornerRadius: cardR)
        blurred(c, 18) { l in
            l.translateBy(x: 0, y: 14)
            l.fill(card, with: .color(rgb(0x46341E, 0.16)))
        }
        blurred(c, 2) { l in
            l.translateBy(x: 0, y: 1.5)
            l.fill(card, with: .color(rgb(0x46341E, 0.22)))
        }
    }

    /// The room in the card, drawn over the wall colour (which shows through where nothing is drawn).
    /// `c` is translated so the card's top-left corner is at (24, cardY) as on the screen.
    private static func drawRoom(_ c: GraphicsContext, _ g: Geo) {
        let x0 = 24.0
        let y0 = g.cardY
        let w = g.w - 48
        let h = g.cardH
        let u = g.u
        var c = c
        c.clip(to: Path(roundedRect: cardRect(g), cornerRadius: cardR))
        // Light from the lamp on the right, darker corners.
        c.fill(Path(CGRect(x: x0, y: y0, width: w, height: h)), with: .radialGradient(
            Gradient(stops: [
                .init(color: rgb(0xFFF8E8, 0.30), location: 0),
                .init(color: rgb(0xFFF8E8, 0.08), location: 0.45),
                .init(color: rgb(0x281E14, 0.16), location: 1),
            ]),
            center: CGPoint(x: x0 + w * 0.8, y: y0 + h * 0.35),
            startRadius: 0,
            endRadius: w * 0.7
        ))
        // Floor and skirting.
        let floorY = y0 + h * 0.76
        c.fill(Path(CGRect(x: x0, y: floorY, width: w, height: h * 0.24)), with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(0xC29466), location: 0),
                .init(color: rgb(0xA57A52), location: 0.5),
                .init(color: rgb(0x80593A), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: floorY),
            endPoint: CGPoint(x: 0, y: y0 + h)
        ))
        var planks = Path()
        for i in -6...6 {
            planks.move(to: CGPoint(x: x0 + w / 2 + Double(i) * 26 * u, y: floorY))
            planks.addLine(to: CGPoint(x: x0 + w / 2 + Double(i) * 44 * u, y: y0 + h))
        }
        c.stroke(planks, with: .color(rgb(0x462C18, 0.35)), lineWidth: 0.8)
        c.fill(Path(CGRect(x: x0, y: floorY - 7 * u, width: w, height: 7 * u)), with: .color(rgb(0xF4EFE6)))
        c.fill(Path(CGRect(x: x0, y: floorY, width: w, height: 3 * u)), with: .color(rgb(0x281A0E, 0.22)))
        // A framed print.
        let fx = x0 + 34 * u
        let fy = y0 + 26 * u
        let fw = 70 * u
        let fh = 52 * u
        blurred(c, 4) { l in l.fill(Path(CGRect(x: fx + 2, y: fy + 5, width: fw, height: fh)), with: .color(rgb(0x1E140A, 0.25))) }
        c.fill(Path(CGRect(x: fx, y: fy, width: fw, height: fh)), with: .color(rgb(0x3A322B)))
        c.fill(Path(CGRect(x: fx + 3, y: fy + 3, width: fw - 6, height: fh - 6)), with: .color(rgb(0xF7F3EA)))
        c.fill(circle(fx + fw * 0.4, fy + fh * 0.55, 11 * u), with: .color(rgb(0xC0704F)))
        c.fill(circle(fx + fw * 0.6, fy + fh * 0.45, 8 * u), with: .color(rgb(0x2F4858)))
        // A sideboard with a vase.
        let sx = x0 + w * 0.42
        let sw = w * 0.34
        let sy = floorY - 34 * u
        blurred(c, 5) { l in
            l.fill(Path(roundedRect: CGRect(x: sx + 3, y: sy + 6, width: sw, height: 30 * u), cornerRadius: 3), with: .color(rgb(0x1E140A, 0.25)))
        }
        c.fill(
            Path(roundedRect: CGRect(x: sx, y: sy, width: sw, height: 30 * u), cornerRadius: 3),
            with: .linearGradient(Gradient(colors: [rgb(0x836550), rgb(0x5E4636)]), startPoint: CGPoint(x: 0, y: sy), endPoint: CGPoint(x: 0, y: sy + 30 * u))
        )
        c.fill(Path(CGRect(x: sx + sw / 2 - 0.5, y: sy + 4 * u, width: 1, height: 22 * u)), with: .color(rgb(0x1E140A, 0.35)))
        for lx in [sx + 6 * u, sx + sw - 8 * u] {
            c.fill(Path(CGRect(x: lx, y: sy + 30 * u, width: 2 * u, height: 7 * u)), with: .color(rgb(0x4A3628)))
        }
        let vx = sx + sw * 0.25
        c.fill(Path(roundedRect: CGRect(x: vx - 7 * u, y: sy - 22 * u, width: 14 * u, height: 22 * u), cornerRadius: 6 * u), with: .color(rgb(0xE9E2D6)))
        for (dx, dy) in [(-12.0, -48.0), (4, -56), (14, -42)] {
            var p = Path()
            p.move(to: CGPoint(x: vx, y: sy - 20 * u))
            p.addQuadCurve(to: CGPoint(x: vx + dx * u, y: sy + dy * u), control: CGPoint(x: vx + dx * 0.3 * u, y: sy + dy * 0.6 * u))
            c.stroke(p, with: .color(rgb(0x5E6B48)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
            c.fill(Path(ellipseIn: CGRect(x: vx + dx * u - 3 * u, y: sy + dy * u - 2 * u, width: 6 * u, height: 4 * u)), with: .color(rgb(0x6F7F55)))
        }
        // A floor lamp on the right.
        let lx = x0 + w * 0.86
        var pole = Path()
        pole.move(to: CGPoint(x: lx, y: floorY + 4 * u))
        pole.addLine(to: CGPoint(x: lx, y: y0 + 40 * u))
        c.stroke(pole, with: .color(rgb(0x3A322B)), lineWidth: 1.6)
        var shade = Path()
        shade.move(to: CGPoint(x: lx - 9 * u, y: y0 + 24 * u))
        shade.addLine(to: CGPoint(x: lx + 9 * u, y: y0 + 24 * u))
        shade.addLine(to: CGPoint(x: lx + 14 * u, y: y0 + 42 * u))
        shade.addLine(to: CGPoint(x: lx - 14 * u, y: y0 + 42 * u))
        shade.closeSubpath()
        c.fill(shade, with: .color(rgb(0xF1E7D4)))
        blurred(c, 14) { l in
            l.fill(Path(ellipseIn: CGRect(x: lx - 30 * u, y: y0 + 36 * u, width: 60 * u, height: 40 * u)), with: .color(rgb(0xFFECBE, 0.35)))
        }
        // The room's tag.
        let tag = "Living room · North light"
        let tagWidth = c.resolve(Text(tag).font(chipFont)).measure(in: unbounded).width + 20
        c.fill(Path(roundedRect: CGRect(x: x0 + w - tagWidth - 12, y: y0 + 12, width: tagWidth, height: 22), cornerRadius: 11), with: .color(rgb(0xFFFCF6, 0.72)))
        text(c, tag, font: chipFont, color: ink, x: x0 + w - tagWidth - 2, baseline: y0 + 27)
    }

    private static func drawRoomEdge(_ c: GraphicsContext, _ g: Geo) {
        // Inner edge highlight.
        c.stroke(
            Path(roundedRect: CGRect(x: 24.5, y: g.cardY + 0.5, width: g.w - 49, height: g.cardH - 1), cornerRadius: cardR - 0.5),
            with: .color(Color.white.opacity(0.35)),
            lineWidth: 1
        )
    }

    @MainActor private static var roomCache: (geo: Geo, scale: Double, image: Image)?

    /// The room over the wall, rendered once per layout at the screen's scale (card-sized).
    @MainActor
    static func room(_ g: Geo, scale: Double) -> Image {
        if let roomCache, roomCache.geo == g, roomCache.scale == scale { return roomCache.image }
        let rect = cardRect(g)
        let renderer = ImageRenderer(content:
            Canvas { c, _ in
                c.translateBy(x: -rect.minX, y: -rect.minY)
                drawRoom(c, g)
                drawRoomEdge(c, g)
            }
            .frame(width: rect.width, height: rect.height)
        )
        renderer.scale = scale
        renderer.isOpaque = false
        let image: Image
        if let ui = renderer.uiImage {
            image = Image(uiImage: ui.preparingForDisplay() ?? ui)
        } else {
            image = Image(systemName: "square").resizable()
        }
        roomCache = (g, scale, image)
        return image
    }

    // MARK: - Per frame

    private static func lighten(_ hex: UInt32, _ k: Double) -> Color {
        let c = M.hexRGB(hex)
        func f(_ v: Double) -> Double {
            let b = v * 255
            return (k >= 0 ? b + (255 - b) * k : b * (1 + k)).rounded() / 255
        }
        return Color(.sRGB, red: f(c.0), green: f(c.1), blue: f(c.2), opacity: 1)
    }

    /// A glossy paint drop of pot i, h = height towards the camera (0 … 1).
    private static func drawDrop(_ c: GraphicsContext, _ g: Geo, _ x: Double, _ y: Double, _ h: Double, _ pot: Int) {
        let u = g.u
        let hex = M.pots[pot].hex
        let s = 1 + 0.45 * h
        softCircle(c, x + 10 * h * u, y + 16 * h * u, 8 * u, sigma: 2 + 4 * h, rgb(0x281E14), alpha: 0.28 * (1 - 0.45 * h))
        let r = 8 * u * s
        c.fill(circle(x, y, r), with: .radialGradient(
            Gradient(stops: [
                .init(color: lighten(hex, 0.35), location: 0),
                .init(color: rgb(hex), location: 0.5),
                .init(color: lighten(hex, -0.35), location: 1),
            ]),
            center: CGPoint(x: x - r * 0.35, y: y - r * 0.4),
            startRadius: 0,
            endRadius: r * 1.5
        ))
        c.fill(circle(x - r * 0.35, y - r * 0.4, r * 0.22), with: .color(Color.white.opacity(0.75)))
    }

    private static func rollEase(_ x: Double) -> Double {
        let k = x < 0 ? 0 : (x > 1 ? 1 : x)
        return 1 - (1 - k) * (1 - k) * (1 - k)
    }

    private static func codeLine(_ c: PaintMixerPickerRGB) -> String {
        "\(M.toHex(c))   ·   LRV \(M.lrv(c))"
    }

    /// The colour's label: current and previous name, seconds since it changed, the colour before.
    struct Label {
        var cur: Int
        var prev: Int
        var since: Double
        var prevRGB: PaintMixerPickerRGB
    }

    /// The wall colour, then the room over it.
    static func drawCard(_ c: GraphicsContext, _ g: Geo, shown: PaintMixerPickerRGB, room: Image) {
        let rect = cardRect(g)
        c.fill(Path(roundedRect: rect, cornerRadius: cardR), with: .color(rgb(shown)))
        c.draw(room, in: rect)
    }

    /// Tins, drops, splashes, the stick, and the colour's name, code and swatch.
    static func drawDynamic(_ c: GraphicsContext, _ g: Geo, _ m: PaintMixerPickerMix, shown: PaintMixerPickerRGB, label: Label) {
        let u = g.u
        for i in 0..<M.pots.count {
            let (px, py) = M.potCenter(g, i)
            let l = m.lift[i]
            let r = g.potR * (1 + 0.06 * l)
            softCircle(c, px + 2 * u, py + (4 + 6 * l) * u, r, sigma: 5 + 5 * l, rgb(0x3C2C1A), alpha: 0.20)
            softCircle(c, px, py + 1.5, r, sigma: 1.5, rgb(0x3C2C1A), alpha: 0.28)
            c.fill(circle(px, py, r), with: .linearGradient(
                Gradient(stops: [
                    .init(color: rgb(0xF6F4F0), location: 0),
                    .init(color: rgb(0xB9B3AA), location: 0.4),
                    .init(color: rgb(0xE8E5DF), location: 0.65),
                    .init(color: rgb(0x9C968D), location: 1),
                ]),
                startPoint: CGPoint(x: px - r, y: py - r),
                endPoint: CGPoint(x: px + r, y: py + r)
            ))
            c.fill(circle(px, py, r * 0.86), with: .color(rgb(0x3C3228, 0.35)))
            let hex = M.pots[i].hex
            c.fill(circle(px, py, r * 0.8), with: .radialGradient(
                Gradient(stops: [
                    .init(color: lighten(hex, 0.18), location: 0),
                    .init(color: rgb(hex), location: 0.6),
                    .init(color: lighten(hex, -0.2), location: 1),
                ]),
                center: CGPoint(x: px - r * 0.3, y: py - r * 0.3),
                startRadius: 0,
                endRadius: r
            ))
            c.fill(Path(ellipseIn: CGRect(x: px - r * 0.48, y: py - r * 0.55, width: r * 0.5, height: r * 0.26)), with: .color(Color.white.opacity(0.38)))
            // A drip over the rim.
            c.fill(circle(px + r * 0.62, py + r * 0.58, r * 0.16), with: .color(rgb(hex)))
            text(c, M.pots[i].name, font: labelFont, color: inkSoft, x: px, baseline: py + r + 16, centered: true)
        }

        for s in m.splashes {
            let k = min(1, s.age / 0.45)
            let e = 1 - (1 - k) * (1 - k) * (1 - k)
            var dots = Path()
            for j in 0..<7 {
                let a = Double(j) / 7 * Double.pi * 2 + Double(j) * 0.37
                let dist = (6 + (22 + Double(j % 3) * 7) * e) * u
                dots.addPath(circle(s.x + cos(a) * dist, s.y + sin(a) * dist, 2.8 * u * (1 - k)))
            }
            c.fill(dots, with: .color(rgb(M.pots[s.pot].hex)))
        }

        if m.stick.2 > 0.01 {
            let a = m.stick.2
            let sx = m.stick.0
            let sy = m.stick.1
            softCircle(c, sx + 7 * u, sy + 12 * u, 9 * u, sigma: 5, rgb(0x281C0E), alpha: 0.3 * a)
            var wood = c
            wood.opacity = a
            wood.fill(circle(sx, sy, 9 * u * (0.9 + 0.1 * a)), with: .radialGradient(
                Gradient(stops: [
                    .init(color: rgb(0xE6CBA0), location: 0),
                    .init(color: rgb(0xC9A577), location: 0.6),
                    .init(color: rgb(0x9A7448), location: 1),
                ]),
                center: CGPoint(x: sx - 3 * u, y: sy - 3 * u),
                startRadius: 0,
                endRadius: 12 * u
            ))
            var rings = circle(sx, sy, 5 * u)
            rings.addPath(circle(sx, sy, 2.4 * u))
            c.stroke(rings, with: .color(rgb(0x6E4E2C, 0.45 * a)), lineWidth: 0.8)
        }

        for f in m.flights { drawDrop(c, g, f.x, f.y, f.h, f.pot) }

        // The colour: swatch, name, code and light reflectance.
        let py = g.panelY
        softCircle(c, 46, py + 22, 22, sigma: 4, rgb(0x3C2C1A), alpha: 0.22)
        c.fill(circle(46, py + 22, 22), with: .color(rgb(shown)))
        c.stroke(circle(46, py + 22, 20.5), with: .color(Color.white.opacity(0.7)), lineWidth: 1.5)
        // A vertical roll on change: the old line slides up 10 pt and fades, the new one rises from
        // 10 pt below 60 ms later (ease-out, 260 ms each), both clipped to their line.
        let tx = 82.0
        let out = rollEase(label.since / 0.26)
        let inn = rollEase((label.since - 0.06) / 0.26)
        let lines: [(String, String, Font, Color, Double, Double)] = [
            (M.names[label.prev].0, M.names[label.cur].0, nameFont, ink, py + 22, 28),
            (codeLine(label.prevRGB), codeLine(shown), codeFont, inkSoft, py + 42, 16),
        ]
        for (before, after, font, color, base, lh) in lines {
            var lc = c
            lc.clip(to: Path(CGRect(x: tx - 2, y: base - lh * 0.82, width: g.w - tx - 20, height: lh)))
            if out < 1 {
                text(lc, before, font: font, color: color.opacity(1 - out), x: tx, baseline: base - 10 * out)
            }
            if inn > 0 {
                text(lc, after, font: font, color: color.opacity(inn), x: tx, baseline: base + 10 * (1 - inn))
            }
        }
    }
}
