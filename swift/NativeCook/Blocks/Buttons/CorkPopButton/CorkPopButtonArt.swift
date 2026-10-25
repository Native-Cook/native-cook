import SwiftUI
import UIKit

/*
 Cork Pop Button — the drawing. Static layers, drawn once into their own rasterised views: the room
 (emerald night, a warm spot behind the bottle, a diamond trellis, vignette, grain), the order card
 with its text, the bottle (green glass with specular strips, a crinkled gold foil with a scalloped
 tear edge, the cream "Celebrate" label on the shoulder). The cork and one gold bubble are sprites
 rendered once. Per frame: the card's lift, shadows and sheen, the bottle's drop, the cork's inch and
 rock, the wire cage (legs bow and the key spins as it loosens), the foam (a Metal shader), the CO2
 puff, the flying cork and the bubbles.
 */
enum CorkPopButtonArt {
    typealias Geo = CorkPopButtonMotion.Geo
    typealias Pose = CorkPopButtonMotion.Pose

    static func layout(w: Double, h: Double, top: Double) -> Geo {
        let cardX = 20.0
        let cardY = top + 108
        let cardW = w - 40
        let cardH = h < 740 ? 128.0 : 148.0
        let hintY = cardY + cardH + 18
        let lipY = cardY + cardH + (h < 740 ? 100 : 128)
        let avail = h - lipY
        let neckLen = max(110, min(180, avail * 0.42))
        let shoulder = max(80, min(120, avail * 0.3))
        return Geo(w: w, h: h, cx: w / 2, lipY: lipY, neck: 27, neckLen: neckLen, body: min(w * 0.43, 190),
                   corkW: 25, corkH: 30, cardX: cardX, cardY: cardY, cardW: cardW, cardH: cardH,
                   shoulder: shoulder, labelY: lipY + neckLen + shoulder * 0.86, hintY: hintY)
    }

    // MARK: Helpers

    /// "#RRGGBB" or "rgba(r,g,b,a)".
    static func col(_ s: String) -> Color {
        if s.hasPrefix("#") { return Color(hex: UInt32(s.dropFirst(), radix: 16) ?? 0) }
        let parts = s.drop { $0 != "(" }.dropFirst().prefix { $0 != ")" }.split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        guard parts.count == 4 else { return .clear }
        return Color.rgba(parts[0], parts[1], parts[2], parts[3])
    }

    static func gradient(_ list: [String], _ pos: [Double]? = nil) -> Gradient {
        let colors = list.map(col)
        guard let pos, pos.count == colors.count else { return Gradient(colors: colors) }
        return Gradient(stops: zip(colors, pos).map { Gradient.Stop(color: $0, location: $1) })
    }

    static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ list: [String], _ pos: [Double]? = nil) -> GraphicsContext.Shading {
        .linearGradient(gradient(list, pos), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    static func radial(_ x: Double, _ y: Double, _ r: Double, _ list: [String], _ pos: [Double]? = nil) -> GraphicsContext.Shading {
        .radialGradient(gradient(list, pos), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: max(0.1, r))
    }

    static func oval(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2))
    }

    static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
        Path(CGRect(x: x, y: y, width: w, height: h))
    }

    static func rrect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular)
    }

    static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    /// An arc of an ellipse, angles in degrees clockwise from +x (as Skia's drawArc).
    static func arc(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double, _ start: Double, _ sweep: Double) -> Path {
        var p = Path()
        p.addRelativeArc(center: .zero, radius: 1, startAngle: .degrees(start), delta: .degrees(sweep),
                         transform: CGAffineTransform(a: rx, b: 0, c: 0, d: ry, tx: cx, ty: cy))
        return p
    }

    static func stroke(_ c: GraphicsContext, _ path: Path, _ color: String, _ width: Double, _ alpha: Double = 1) {
        c.stroke(path, with: .color(col(color).opacity(alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round))
    }

    static func fill(_ c: GraphicsContext, _ path: Path, _ color: String, _ alpha: Double = 1) {
        c.fill(path, with: .color(col(color).opacity(alpha)))
    }

    static func blurred(_ c: GraphicsContext, _ path: Path, _ color: String, _ alpha: Double, _ sigma: Double) {
        var b = c
        b.addFilter(.blur(radius: sigma))
        b.fill(path, with: .color(col(color).opacity(alpha)))
    }

    /// Draws a line of text with its baseline at y; `anchor` 0 = left edge at x, 0.5 = centred, 1 = right edge.
    static func text(_ c: GraphicsContext, _ s: String, _ font: Font, _ color: String, _ x: Double, _ y: Double, anchor: Double = 0, alpha: Double = 1) {
        let t = c.resolve(Text(s).font(font).foregroundColor(col(color).opacity(alpha)))
        let big = CGSize(width: 1000, height: 200)
        let size = t.measure(in: big)
        let base = t.firstBaseline(in: big)
        c.draw(t, in: CGRect(x: x - size.width * anchor, y: y - base, width: size.width, height: size.height))
    }

    static let grainTile: Image = {
        let side = 96
        var bytes = [UInt8](repeating: 255, count: side * side * 4)
        var seed: UInt32 = 0x2545F491
        for i in 0..<(side * side) {
            seed = seed &* 1664525 &+ 1013904223
            let v = UInt8(truncatingIfNeeded: seed >> 24)
            bytes[i * 4] = v
            bytes[i * 4 + 1] = v
            bytes[i * 4 + 2] = v
        }
        let space = CGColorSpaceCreateDeviceRGB()
        let image = bytes.withUnsafeMutableBytes { raw -> CGImage? in
            CGContext(data: raw.baseAddress, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
                      space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)?.makeImage()
        }
        return image.map { Image(decorative: $0, scale: 3) } ?? Image(systemName: "circle")
    }()

    static func grain(_ c: GraphicsContext, _ r: CGRect, _ alpha: Double) {
        var g = c
        g.blendMode = .overlay
        g.opacity = alpha
        g.fill(Path(r), with: .tiledImage(grainTile, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: 1))
    }

    // Fonts (Helvetica Neue → SF, Menlo → SF Mono, Georgia italic as in React Native).
    static let kicker = Font.system(size: 11, weight: .bold)
    static let mono = Font.system(size: 22, weight: .bold, design: .monospaced)
    static let row = Font.system(size: 15, weight: .regular)
    static let rowBold = Font.system(size: 15, weight: .semibold)
    static let script = Font.custom("Georgia-Italic", size: 27)
    static let tiny = Font.system(size: 10, weight: .bold)

    // MARK: Static layers

    static func room(_ c: GraphicsContext, _ g: Geo) {
        let w = g.w, h = g.h
        let all = rect(0, 0, w, h)
        c.fill(all, with: linear(0, 0, 0, h, ["#0F201B", "#112A22", "#0C1E18", "#08130F"], [0, 0.4, 0.75, 1]))
        c.fill(all, with: radial(g.cx, g.lipY + 40, w * 0.95, ["rgba(214,170,96,0.32)", "rgba(120,110,70,0.12)", "rgba(20,40,32,0)"], [0, 0.5, 1]))
        // A faint diamond trellis, like the shop's wrapping paper.
        var trellis = Path()
        for i in -12..<24 {
            let x = Double(i) * 34
            trellis.move(to: CGPoint(x: x, y: 0))
            trellis.addLine(to: CGPoint(x: x + h * 0.5, y: h))
            trellis.move(to: CGPoint(x: x + h * 0.5, y: 0))
            trellis.addLine(to: CGPoint(x: x, y: h))
        }
        stroke(c, trellis, "#E8D3A0", 0.6, 0.03)
        c.fill(all, with: radial(w / 2, h * 0.5, hypot(w, h) * 0.6, ["rgba(4,10,8,0)", "rgba(4,10,8,0.3)", "rgba(3,8,6,0.7)"], [0, 0.6, 1]))
        grain(c, CGRect(x: 0, y: 0, width: w, height: h), 0.07)
    }

    static func card(_ c: GraphicsContext, _ g: Geo) {
        let x = g.cardX, y = g.cardY, w = g.cardW, h = g.cardH
        let rr = rrect(x, y, w, h, 22)
        c.fill(rr, with: linear(x, y, x + w * 0.4, y + h, ["#FBF5E8", "#F3EBD8", "#E9DEC6"], [0, 0.55, 1]))
        var gc = c
        gc.clip(to: rr)
        grain(gc, CGRect(x: x, y: y, width: w, height: h), 0.08)
        // A gold deckle line inside the edge.
        c.stroke(rrect(x + 6, y + 6, w - 12, h - 12, 17), with: .color(col("#C9A35A").opacity(0.55)), lineWidth: 0.8)
        c.stroke(rrect(x + 0.5, y + 0.5, w - 1, h - 1, 21.5), with: .color(col("#FFFFFF").opacity(0.6)), lineWidth: 1)
        let small = h < 140
        let left = x + 22
        let right = x + w - 22
        text(c, "ORDER", kicker, "#A87F33", left, y + (small ? 30 : 34))
        text(c, "MV-20481", mono, "#16302A", left, y + (small ? 54 : 60))
        // "Confirmed" pill.
        let label = "Confirmed"
        let tw = c.resolve(Text(label).font(tiny)).measure(in: CGSize(width: 1000, height: 100)).width
        let lw = tw + 26
        let py = y + (small ? 22 : 26)
        fill(c, rrect(right - lw, py, lw, 24, 12), "#1F5B45", 0.12)
        fill(c, oval(right - lw + 11, py + 12, 3, 3), "#2C7A5C")
        text(c, label, tiny, "#1F5B45", right - lw + 18, py + 16.5)
        let div = y + (small ? 68 : 78)
        stroke(c, line(left, div, right, div), "#C9B48A", 1, 0.5)
        let r1 = div + (small ? 24 : 28)
        let r2 = r1 + (small ? 24 : 28)
        text(c, "Cuvée Lumière Brut × 2", row, "#16302A", left, r1)
        text(c, "€96.00", rowBold, "#16302A", right, r1, anchor: 1)
        text(c, "Delivery · Thu 30 Oct", row, "#5E6E66", left, r2)
        text(c, "Free", row, "#5E6E66", right, r2, anchor: 1)
    }

    /// Half width of the bottle at height y (from the lip down).
    static func bottleHalf(_ g: Geo, _ y: Double) -> Double {
        let d = y - g.lipY
        if d < 10 { return g.neck + 3 }
        if d < g.neckLen { return g.neck + 6 * (d - 10) / (g.neckLen - 10) }
        let u = min(1, (d - g.neckLen) / g.shoulder)
        let s = u * u * (3 - 2 * u)
        return g.neck + 6 + (g.body - g.neck - 6) * s
    }

    static func bottlePath(_ g: Geo) -> Path {
        var b = Path()
        let top = g.lipY
        let bottom = g.h + 60
        let ht = bottleHalf(g, top)
        b.move(to: CGPoint(x: g.cx - ht, y: top + 4))
        b.addQuadCurve(to: CGPoint(x: g.cx - ht + 4, y: top), control: CGPoint(x: g.cx - ht, y: top))
        b.addLine(to: CGPoint(x: g.cx + ht - 4, y: top))
        b.addQuadCurve(to: CGPoint(x: g.cx + ht, y: top + 4), control: CGPoint(x: g.cx + ht, y: top))
        var y = top + 6
        while y <= bottom {
            b.addLine(to: CGPoint(x: g.cx + bottleHalf(g, y), y: y))
            y += 6
        }
        y = bottom
        while y >= top + 6 {
            b.addLine(to: CGPoint(x: g.cx - bottleHalf(g, y), y: y))
            y -= 6
        }
        b.closeSubpath()
        return b
    }

    static func bottle(_ c: GraphicsContext, _ g: Geo) {
        let cx = g.cx, lipY = g.lipY
        let glass = bottlePath(g)
        let B = g.body
        c.fill(glass, with: linear(cx - B, 0, cx + B, 0, ["#03100B", "#0B3325", "#1C5A40", "#0E3B2B", "#062015", "#020A07"], [0, 0.2, 0.36, 0.55, 0.82, 1]))
        var gc = c
        gc.clip(to: glass)
        // Light from the spot through the glass: a glow low in the body.
        blurred(gc, oval(cx + B * 0.15, g.labelY + 160, B * 0.9, B * 0.9), "#3E8F63", 0.35, 40)
        // Specular strips that follow the bottle's outline.
        for (k, a, wdt) in [(-0.55, 0.45, 5.0), (-0.42, 0.18, 2.0), (0.62, 0.14, 7.0)] {
            var s = Path()
            for y in stride(from: lipY + 8, to: g.h + 60, by: 6) {
                let pt = CGPoint(x: cx + bottleHalf(g, y) * k, y: y)
                if s.isEmpty { s.move(to: pt) } else { s.addLine(to: pt) }
            }
            var sc = gc
            sc.addFilter(.blur(radius: wdt * 0.5))
            stroke(sc, s, "#E9FFF2", wdt, a)
        }
        // The foil: from the lip down 85 % of the neck, with a scalloped tear edge.
        let foilEnd = lipY + g.neckLen * 0.85
        var foil = Path()
        foil.move(to: CGPoint(x: cx - bottleHalf(g, lipY) - 1.5, y: lipY - 1))
        foil.addLine(to: CGPoint(x: cx + bottleHalf(g, lipY) + 1.5, y: lipY - 1))
        var fy = lipY
        while fy <= foilEnd {
            foil.addLine(to: CGPoint(x: cx + bottleHalf(g, fy) + 1.5, y: fy))
            fy += 4
        }
        let half = bottleHalf(g, foilEnd) + 1.5
        let teeth = 9
        for i in stride(from: teeth, through: 0, by: -1) {
            let x = cx - half + 2 * half * Double(i) / Double(teeth)
            foil.addLine(to: CGPoint(x: x, y: foilEnd + (i % 2 == 0 ? 0 : 7)))
        }
        fy = foilEnd
        while fy >= lipY {
            foil.addLine(to: CGPoint(x: cx - bottleHalf(g, fy) - 1.5, y: fy))
            fy -= 4
        }
        foil.closeSubpath()
        c.fill(foil, with: linear(cx - half, 0, cx + half, 0, ["#5E4317", "#B89042", "#F7E3A6", "#D5B062", "#8E6A2A", "#4C3612"], [0, 0.22, 0.38, 0.55, 0.8, 1]))
        var fc = c
        fc.clip(to: foil)
        // Crinkles: short light and dark strokes, seeded.
        var light = Path()
        var dark = Path()
        let hash = CorkPopButtonMotion.hash
        for i in 0..<90 {
            let di = Double(i)
            let y = lipY + hash(di + 3) * (foilEnd - lipY + 6)
            let x = cx + (hash(di + 7) - 0.5) * 2 * bottleHalf(g, y)
            let a = (hash(di + 11) - 0.5) * 1.2
            let l = 4 + 9 * hash(di + 13)
            let seg = line(x, y, x + cos(a) * l, y + sin(a) * l)
            if i % 2 == 0 { light.addPath(seg) } else { dark.addPath(seg) }
        }
        stroke(fc, light, "#FFF3C8", 0.7, 0.32)
        stroke(fc, dark, "#3E2A0C", 0.7, 0.32)
        // The neck band: an embossed ring with the house's initials.
        let band = lipY + g.neckLen * 0.45
        fill(fc, rect(cx - 60, band - 9, 120, 18), "#2A1C06", 0.25)
        stroke(fc, line(cx - 60, band - 9, cx + 60, band - 9), "#FFF1C4", 0.8, 0.5)
        stroke(fc, line(cx - 60, band + 9, cx + 60, band + 9), "#2A1C06", 0.8, 0.5)
        text(fc, "M  V", tiny, "#3B2A0C", cx, band + 4.5, anchor: 0.5, alpha: 0.8)
        // The label on the shoulder: the call to action.
        let ly = g.labelY
        let lw = min(214, g.body * 1.16)
        let lh = 66.0
        blurred(c, rrect(cx - lw / 2, ly - lh / 2 + 4, lw, lh, lh / 2), "#010503", 0.5, 8)
        c.fill(rrect(cx - lw / 2, ly - lh / 2, lw, lh, lh / 2), with: linear(cx - lw / 2, 0, cx + lw / 2, 0, ["#CFC3A6", "#F7F0DE", "#FBF6EA", "#E8DDC2", "#BBAE8E"], [0, 0.25, 0.45, 0.75, 1]))
        c.stroke(rrect(cx - lw / 2 + 5, ly - lh / 2 + 5, lw - 10, lh - 10, lh / 2 - 5), with: .color(col("#B08A3E").opacity(0.9)), lineWidth: 1.1)
        text(c, "CUVÉE LUMIÈRE · BRUT", tiny, "#9A7631", cx, ly - 11, anchor: 0.5)
        text(c, "Celebrate", script, "#163A2D", cx, ly + 17, anchor: 0.5)
    }

    /// The cork, drawn at its place in the neck (lip at g.lipY).
    static func cork(_ c: GraphicsContext, _ g: Geo) {
        let cx = g.cx, lipY = g.lipY, corkW = g.corkW, corkH = g.corkH
        let top = lipY - corkH
        let hash = CorkPopButtonMotion.hash
        // The shaft, which shows as the cork inches out of the neck.
        c.fill(rect(cx - 17, lipY - 4, 34, 30), with: linear(cx - 17, 0, cx + 17, 0, ["#9C7646", "#E2C796", "#D2B07A", "#8E6939"]))
        var pores = Path()
        for i in 0..<14 {
            let di = Double(i)
            let r = 0.7 + hash(di + 98)
            pores.addPath(oval(cx + (hash(di + 90) - 0.5) * 30, lipY + hash(di + 94) * 26, r, r))
        }
        fill(c, pores, "#6C4B24", 0.5)
        // The mushroom.
        var m = Path()
        m.move(to: CGPoint(x: cx - corkW + 3, y: lipY + 2))
        m.addCurve(to: CGPoint(x: cx, y: top), control1: CGPoint(x: cx - corkW - 2, y: top + corkH * 0.55), control2: CGPoint(x: cx - corkW * 0.9, y: top))
        m.addCurve(to: CGPoint(x: cx + corkW - 3, y: lipY + 2), control1: CGPoint(x: cx + corkW * 0.9, y: top), control2: CGPoint(x: cx + corkW + 2, y: top + corkH * 0.55))
        m.closeSubpath()
        c.fill(m, with: linear(cx - corkW, 0, cx + corkW, 0, ["#7E5A2E", "#C9A471", "#E3C693", "#B48A54", "#6B4A22"], [0, 0.25, 0.42, 0.7, 1]))
        var mc = c
        mc.clip(to: m)
        var flecks = Path()
        for i in 0..<40 {
            let di = Double(i)
            let x = cx + (hash(di + 30) - 0.5) * corkW * 2
            let y = top + hash(di + 34) * corkH
            flecks.addEllipse(in: CGRect(x: x, y: y, width: 1 + 2 * hash(di + 38), height: 0.8 + hash(di + 39)))
        }
        fill(mc, flecks, "#5A3C18", 0.45)
        mc.fill(rect(cx - corkW - 4, top, corkW * 2 + 8, corkH + 4), with: linear(0, top, 0, lipY, ["rgba(255,240,210,0.25)", "rgba(0,0,0,0)", "rgba(40,20,5,0.35)"], [0, 0.4, 1]))
    }

    static func bubble(_ c: GraphicsContext, _ R: Double) {
        c.fill(oval(0, 0, R, R), with: radial(0, 0, R, ["rgba(255,232,170,0.04)", "rgba(255,220,140,0.12)", "rgba(232,184,92,0.55)", "rgba(255,236,186,0.95)"], [0, 0.6, 0.88, 1]))
        blurred(c, oval(-R * 0.36, -R * 0.4, R * 0.22, R * 0.22), "#FFFFFF", 0.85, R * 0.1)
        stroke(c, arc(0, 0, R * 0.7, R * 0.7, 20, 70), "#FFF6DA", R * 0.08, 0.6)
    }

    enum LayerKind { case room, card, bottle }

    static func layerRect(_ kind: LayerKind, _ g: Geo) -> CGRect {
        switch kind {
        case .room: return CGRect(x: 0, y: 0, width: g.w, height: g.h)
        case .card: return CGRect(x: g.cardX - 2, y: g.cardY - 2, width: g.cardW + 4, height: g.cardH + 4)
        case .bottle:
            let bx = g.cx - g.body - 30
            return CGRect(x: bx, y: g.lipY - 10, width: (g.body + 30) * 2, height: g.h + 80 - g.lipY)
        }
    }

    /// The cork sprite's rect around its place in the neck.
    static func corkRect(_ g: Geo) -> CGRect {
        CGRect(x: g.cx - g.corkW - 6, y: g.lipY - g.corkH - 4, width: g.corkW * 2 + 12, height: g.corkH + 34)
    }

    struct Sprites {
        var cork: Image
        var bubble: Image
    }

    @MainActor
    static func sprites(_ g: Geo) -> Sprites? {
        let r = corkRect(g)
        let corkView = Canvas { c, _ in
            var cc = c
            cc.translateBy(x: -r.minX, y: -r.minY)
            cork(cc, g)
        }
        .frame(width: r.width, height: r.height)
        let bubbleView = Canvas { c, _ in
            var cc = c
            cc.translateBy(x: 24, y: 24)
            bubble(cc, 22)
        }
        .frame(width: 48, height: 48)
        let a = ImageRenderer(content: corkView)
        a.scale = 3
        let b = ImageRenderer(content: bubbleView)
        b.scale = 3
        guard let ci = a.uiImage, let bi = b.uiImage else { return nil }
        return Sprites(cork: Image(uiImage: ci).interpolation(.high), bubble: Image(uiImage: bi).interpolation(.high))
    }

    // MARK: Per frame

    /// The card's two shadows under it, deepening as it lifts.
    static func cardShadows(_ c: GraphicsContext, _ g: Geo, _ lift: Double) {
        blurred(c, rrect(g.cardX, g.cardY + 2, g.cardW, g.cardH, 22), "#020604", 0.4 - 0.15 * lift, 3 + 2 * lift)
        blurred(c, rrect(g.cardX + 6, g.cardY + 16 + 10 * lift, g.cardW - 12, g.cardH, 22), "#020604", 0.35 + 0.2 * lift, 18 + 14 * lift)
    }

    /// The card's transform: lifted and scaled about its centre.
    static func cardTransform(_ c: inout GraphicsContext, _ g: Geo, _ lift: Double) {
        let s = 1 + 0.015 * lift
        c.translateBy(x: g.cx, y: g.cardY + g.cardH / 2 - 12 * lift)
        c.scaleBy(x: s, y: s)
        c.translateBy(x: -g.cx, y: -(g.cardY + g.cardH / 2))
    }

    /// The gold sheen across the lifted card, then the corked cork in the neck (under the bottle).
    static func underBottle(_ c: GraphicsContext, _ g: Geo, _ p: Pose, _ cork: GraphicsContext.ResolvedImage?) {
        if p.sheen > 0 && p.sheen < 1 {
            var sc = c
            cardTransform(&sc, g, p.lift)
            sc.clip(to: rrect(g.cardX, g.cardY, g.cardW, g.cardH, 22))
            let x = g.cardX - 120 + (g.cardW + 240) * p.sheen
            sc.blendMode = .plusLighter
            sc.fill(rect(g.cardX, g.cardY, g.cardW, g.cardH),
                    with: linear(x - 60, g.cardY, x + 60, g.cardY + g.cardH, ["rgba(255,230,160,0)", "rgba(255,226,150,0.5)", "rgba(255,255,255,0.35)", "rgba(255,230,160,0)"], [0, 0.45, 0.55, 1]))
        }
        if p.corked, let cork {
            var cc = c
            cc.translateBy(x: g.cx, y: g.lipY + p.drop)
            cc.rotate(by: .degrees(p.rock * 0.8))
            cc.translateBy(x: -g.cx, y: -g.lipY)
            cc.draw(cork, in: corkRect(g).offsetBy(dx: 0, dy: -p.inch))
        }
    }

    /// The wire cage: a cap plate on the cork, four legs to a ring under the lip, the twisted key.
    static func cage(_ c: GraphicsContext, _ g: Geo, inch: Double, loose: Double, rock: Double, dy: Double) {
        let cx = g.cx, corkW = g.corkW
        let top = g.lipY - g.corkH - inch + dy
        let ringY = g.lipY + 9 + loose * 2 + dy
        let ringR = g.neck + 3.5
        let plateR = corkW * 0.66
        let bow = -1 + loose * 4
        // Back legs first, then the ring, then the front legs.
        let legs = [-0.96, 0.96, -0.5, 0.5]
        for k in 0..<4 {
            let s = legs[k]
            var path = Path()
            path.move(to: CGPoint(x: cx + s * plateR, y: top + 3))
            path.addQuadCurve(to: CGPoint(x: cx + s * ringR, y: ringY), control: CGPoint(x: cx + s * (corkW + bow), y: top + g.corkH * 0.55))
            if k < 2 { stroke(c, path, "#7E878E", 0.9, 0.55) } else { stroke(c, path, "#DDE3E7", 1.1, 0.9) }
            if k == 1 { stroke(c, arc(cx, ringY, ringR, 3.5, 0, 180), "#C9D0D5", 1.6) }
        }
        // The key: a twisted loop hanging at the front of the ring, spinning as it loosens.
        let spin = loose * .pi * 6 + rock * 0.05
        let kw = 1 + 4 * abs(cos(spin))
        stroke(c, line(cx, ringY + 3, cx, ringY + 7), "#E9EDF0", 1.6)
        stroke(c, Path(ellipseIn: CGRect(x: cx - kw, y: ringY + 7, width: kw * 2, height: 9)), "#F2F5F7", 1.4)
        // The cap plate on top with its embossed ring.
        c.fill(Path(ellipseIn: CGRect(x: cx - plateR, y: top - 1.5, width: plateR * 2, height: 7)),
               with: linear(cx - plateR, 0, cx + plateR, 0, ["#8C6A2C", "#F3D88E", "#C49A48", "#7A5A22"]))
        stroke(c, Path(ellipseIn: CGRect(x: cx - plateR * 0.6, y: top + 0.2, width: plateR * 1.2, height: 3.6)), "#FFF1C2", 0.7, 0.7)
        let mark = rock * 0.12 + loose * 2.4
        fill(c, oval(cx + cos(mark) * plateR * 0.75, top + 2 + sin(mark) * 2, 1, 1), "#5A4012", 0.8)
    }

    /// The CO2 puff, the flying cork with its cage, the gold bubbles, the touch marker.
    static func over(_ c: GraphicsContext, _ g: Geo, _ p: Pose, _ cork: GraphicsContext.ResolvedImage?, _ bubble: GraphicsContext.ResolvedImage?) {
        for f in p.fog where f.alpha > 0.005 {
            blurred(c, oval(f.x, f.y, f.r, f.r), "#E6F1EC", f.alpha, f.r * 0.45)
        }
        if let flying = p.cork, let cork {
            let (x, y, rot) = flying
            var cc = c
            cc.translateBy(x: x, y: y)
            cc.rotate(by: .degrees(rot))
            cc.translateBy(x: -g.cx, y: -(g.lipY - g.corkH * 0.5))
            cc.draw(cork, in: corkRect(g))
            cage(cc, g, inch: 0, loose: 1, rock: 0, dy: 0)
        }
        if let bubble {
            for b in p.bubbles {
                let R = b.r
                var bc = c
                bc.opacity = b.alpha
                bc.draw(bubble, in: CGRect(x: b.x - R * 1.09, y: b.y - R * 1.09, width: R * 2.18, height: R * 2.18))
            }
        }
        // A soft touch marker while the loop twists the cork.
        if p.finger > 0.01 {
            let fy = g.lipY - g.corkH * 0.55 - p.inch
            let fx = g.cx + p.rock * 1.6
            blurred(c, oval(fx, fy, 24, 24), "#FFFFFF", 0.16 * p.finger, 6)
            stroke(c, oval(fx, fy, 22, 22), "#FFFFFF", 1.2, 0.35 * p.finger)
        }
    }
}

/// One static layer, drawn once in its own rect and rasterised; it only moves as a whole.
struct CorkPopButtonLayer: View, Equatable {
    let kind: CorkPopButtonArt.LayerKind
    let g: CorkPopButtonMotion.Geo

    var body: some View {
        let r = CorkPopButtonArt.layerRect(kind, g)
        Canvas { c, _ in
            var cc = c
            cc.translateBy(x: -r.minX, y: -r.minY)
            switch kind {
            case .room: CorkPopButtonArt.room(cc, g)
            case .card: CorkPopButtonArt.card(cc, g)
            case .bottle: CorkPopButtonArt.bottle(cc, g)
            }
        }
        .frame(width: r.width, height: r.height)
        .drawingGroup()
    }
}
