import SwiftUI
import UIKit

/*
 Parachute Drop Alert — the drawing. The app under the text (sky with clouds and rooftops, the
 sheet, its cards, route and tab-bar icons) is drawn once into a rasterised view; the alert card
 (with its two shadows) is a sprite rendered once. The alert layer over it (scrim, suspension lines,
 the canopies and the card) is one Canvas redrawn each frame from the pose. A canopy is seven gores
 between elliptical meridians with a scalloped hem, coral and cream, shaded across the dome;
 collapsing, its height falls, its hem sags in folds and its lines go slack.
 */
enum ParachuteDropAlertArt {
    typealias Geo = ParachuteDropAlertMotion.Geo
    typealias Pose = ParachuteDropAlertMotion.Pose
    typealias Canopy = ParachuteDropAlertMotion.Canopy

    static let coral = "#EE6B4D"
    static let ink = "#1D2B3D"

    static func layout(w: Double, h: Double, top: Double, bottom: Double) -> Geo {
        let skyH = top + 250
        let sheetY = skyH - 24
        let card1Y = sheetY + 22
        let card1H = 176.0
        let sectionY = card1Y + card1H + 22
        let rowsY = sectionY + 30
        let tabY = h - max(bottom, 8) - 56
        let rows = max(0, min(3, Int(floor((tabY - 12 - rowsY) / 78))))
        let cardW = min(w - 40, 360)
        let cardH = 172.0
        let canW = cardW * 0.74
        let pad = 20.0
        let bw = (cardW - pad * 2 - 10) / 2
        return Geo(w: w, h: h, cardW: cardW, cardH: cardH, landX: w / 2, landY: skyH + 44, canW: canW, canH: canW * 0.42,
                   lines: 104, top: top, skyH: skyH, sheetY: sheetY, card1Y: card1Y, card1H: card1H, sectionY: sectionY,
                   rowsY: rowsY, rows: rows, tabY: tabY,
                   later: CGRect(x: pad, y: cardH - 62, width: bw, height: 44),
                   track: CGRect(x: pad + bw + 10, y: cardH - 62, width: bw, height: 44))
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

    static func oval(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2))
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

    static func stroke(_ c: GraphicsContext, _ path: Path, _ color: String, _ width: Double, _ alpha: Double = 1) {
        c.stroke(path, with: .color(col(color).opacity(alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }

    static func fill(_ c: GraphicsContext, _ path: Path, _ color: String, _ alpha: Double = 1) {
        c.fill(path, with: .color(col(color).opacity(alpha)))
    }

    static func blurred(_ c: GraphicsContext, _ path: Path, _ color: String, _ alpha: Double, _ sigma: Double) {
        var b = c
        b.addFilter(.blur(radius: sigma))
        b.fill(path, with: .color(col(color).opacity(alpha)))
    }

    /// Draws a line of text with its baseline at y; `anchor` 0 = left edge at x, 0.5 = centred.
    static func text(_ c: GraphicsContext, _ s: String, _ font: Font, _ color: String, _ x: Double, _ y: Double, anchor: Double = 0) {
        let t = c.resolve(Text(s).font(font).foregroundColor(col(color)))
        let big = CGSize(width: 1000, height: 200)
        let size = t.measure(in: big)
        let base = t.firstBaseline(in: big)
        c.draw(t, in: CGRect(x: x - size.width * anchor, y: y - base, width: size.width, height: size.height))
    }

    static let grainTile: Image = {
        let side = 96
        var bytes = [UInt8](repeating: 255, count: side * side * 4)
        var seed: UInt32 = 0x1B873593
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

    static func cloud(_ c: GraphicsContext, _ x: Double, _ y: Double, _ s: Double, _ a: Double) {
        let puffs: [Double] = [-0.9, 0.1, 0.32, -0.3, -0.25, 0.42, 0.25, -0.38, 0.5, 0.8, 0.05, 0.3]
        for i in stride(from: 0, to: puffs.count, by: 3) {
            let r = puffs[i + 2] * 70 * s
            blurred(c, oval(x + puffs[i] * 60 * s, y + puffs[i + 1] * 30 * s, r, r), "#FFFFFF", a, 6 * s)
        }
        blurred(c, Path(ellipseIn: CGRect(x: x - 70 * s, y: y + 4 * s, width: 140 * s, height: 22 * s)), "#FFFFFF", a * 0.9, 6 * s)
    }

    /// A parcel: a little box in cream lines.
    static func parcel(_ c: GraphicsContext, _ x: Double, _ y: Double, _ s: Double, _ color: String) {
        var p = Path()
        p.move(to: CGPoint(x: x, y: y - 9 * s))
        p.addLine(to: CGPoint(x: x + 10 * s, y: y - 4 * s))
        p.addLine(to: CGPoint(x: x + 10 * s, y: y + 7 * s))
        p.addLine(to: CGPoint(x: x, y: y + 12 * s))
        p.addLine(to: CGPoint(x: x - 10 * s, y: y + 7 * s))
        p.addLine(to: CGPoint(x: x - 10 * s, y: y - 4 * s))
        p.closeSubpath()
        p.move(to: CGPoint(x: x - 10 * s, y: y - 4 * s))
        p.addLine(to: CGPoint(x: x, y: y + 1 * s))
        p.addLine(to: CGPoint(x: x + 10 * s, y: y - 4 * s))
        p.move(to: CGPoint(x: x, y: y + 1 * s))
        p.addLine(to: CGPoint(x: x, y: y + 12 * s))
        p.move(to: CGPoint(x: x - 5 * s, y: y - 6.5 * s))
        p.addLine(to: CGPoint(x: x + 5 * s, y: y - 1.5 * s))
        stroke(c, p, color, 1.6 * s)
    }

    static func tile(_ c: GraphicsContext, _ x: Double, _ y: Double, _ size: Double, _ from: String, _ to: String) {
        c.fill(rrect(x, y, size, size, size * 0.3), with: linear(x, y, x + size, y + size, [from, to]))
        stroke(c, rrect(x + 0.5, y + 0.5, size - 1, size - 1, size * 0.3), "#FFFFFF", 1, 0.25)
    }

    static func cardBox(_ c: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) {
        blurred(c, rrect(x, y + 6, w, h, r), "#5B4A3A", 0.12, 10)
        blurred(c, rrect(x, y + 1, w, h, r), "#5B4A3A", 0.12, 1.5)
        c.fill(rrect(x, y, w, h, r), with: linear(x, y, x, y + h, ["#FFFDFA", "#FBF8F3"]))
    }

    // MARK: Static layers

    /// The app under the alert (everything but its text).
    static func app(_ c: GraphicsContext, _ g: Geo) {
        let w = g.w, h = g.h
        c.fill(Path(CGRect(x: 0, y: 0, width: w, height: g.skyH + 10)), with: linear(0, 0, 0, g.skyH, ["#8FB8DD", "#B4CFE7", "#D8E2EC", "#F2DCCB"], [0, 0.4, 0.72, 1]))
        blurred(c, oval(w * 0.82, g.top + 40, 90, 90), "#FFF4E0", 0.45, 40)
        cloud(c, w * 0.18, g.top + 118, 0.8, 0.75)
        cloud(c, w * 0.86, g.top + 168, 0.6, 0.6)
        cloud(c, w * 0.52, g.top + 214, 0.45, 0.5)
        // Rooftops on the horizon, two layers.
        func roofs(_ yb: Double, _ seed: Double, _ color: String, _ hgt: Double) {
            var b = Path()
            b.move(to: CGPoint(x: 0, y: yb))
            var x = 0.0
            var i = 0
            while x < w {
                let di = Double(i)
                let bw = 26 + 30 * abs(sin(seed + di * 1.7))
                let bh = hgt * (0.45 + 0.55 * abs(sin(seed * 2 + di * 2.3)))
                b.addLine(to: CGPoint(x: x, y: yb - bh))
                if i % 3 == 1 {
                    b.addLine(to: CGPoint(x: x + bw * 0.5, y: yb - bh - 12))
                    b.addLine(to: CGPoint(x: x + bw, y: yb - bh))
                } else {
                    if i % 2 == 0 {
                        b.addLine(to: CGPoint(x: x + bw * 0.25, y: yb - bh))
                        b.addLine(to: CGPoint(x: x + bw * 0.25, y: yb - bh - 9))
                        b.addLine(to: CGPoint(x: x + bw * 0.35, y: yb - bh - 9))
                        b.addLine(to: CGPoint(x: x + bw * 0.35, y: yb - bh))
                    }
                    b.addLine(to: CGPoint(x: x + bw, y: yb - bh))
                }
                x += bw
                i += 1
            }
            b.addLine(to: CGPoint(x: w, y: yb))
            b.closeSubpath()
            fill(c, b, color)
        }
        roofs(g.skyH + 4, 1.3, "#BCCBDA", 54)
        roofs(g.skyH + 4, 4.1, "#A5B7C9", 34)
        grain(c, CGRect(x: 0, y: 0, width: w, height: g.skyH + 10), 0.05)
        // The sheet.
        c.fill(rrect(0, g.sheetY, w, h - g.sheetY + 40, 28), with: linear(0, g.sheetY, 0, h, ["#F7F3EE", "#F3EEE7", "#EEE8E0"]))
        fill(c, rrect(w / 2 - 18, g.sheetY + 8, 36, 5, 2.5), "#1D2B3D", 0.14)
        // Card 1: the parcel on its way, with its route.
        let x = 16.0
        let cw = w - 32
        cardBox(c, x, g.card1Y, cw, g.card1H, 22)
        tile(c, x + 16, g.card1Y + 16, 44, "#F58B66", "#E2553C")
        parcel(c, x + 38, g.card1Y + 37, 1, "#FFF3EA")
        let ry = g.card1Y + 104
        let x0 = x + 30
        let x1 = x + cw - 30
        stroke(c, line(x0, ry, x1, ry), "#1D2B3D", 3, 0.1)
        let done = x0 + (x1 - x0) * 0.78
        c.stroke(line(x0, ry, done, ry), with: linear(x0, 0, done, 0, ["#F6A07E", coral]), style: StrokeStyle(lineWidth: 3, lineCap: .round))
        for k in 0..<4 {
            let sx = x0 + (x1 - x0) * Double(k) / 3
            fill(c, oval(sx, ry, 7, 7), sx <= done ? coral : "#E4DDD3")
            fill(c, oval(sx, ry, 3, 3), "#FFFDFA")
        }
        // The van, just past "Out for delivery".
        blurred(c, oval(done, ry, 12, 12), coral, 0.35, 5)
        fill(c, oval(done, ry, 10, 10), "#FFFDFA")
        stroke(c, oval(done, ry, 10, 10), coral, 2)
        fill(c, rrect(done - 5.5, ry - 3.5, 8, 6, 1.2), coral)
        fill(c, rrect(done + 2, ry - 1.5, 3.5, 4, 1), coral)
        stroke(c, line(x + 16, g.card1Y + 140, x + cw - 16, g.card1Y + 140), "#1D2B3D", 1, 0.07)
        // Rows.
        for r in 0..<g.rows {
            let y = g.rowsY + Double(r) * 78
            cardBox(c, x, y, cw, 68, 20)
            tile(c, x + 14, y + 12, 44, r % 2 == 0 ? "#9EC3E3" : "#E9C37A", r % 2 == 0 ? "#6E9DC9" : "#D79E45")
            parcel(c, x + 36, y + 33, 0.9, "#FFFBF4")
        }
        // Tab bar.
        c.fill(Path(CGRect(x: 0, y: g.tabY, width: w, height: h - g.tabY)), with: linear(0, g.tabY, 0, h, ["#FBF8F4", "#F6F2EC"]))
        stroke(c, line(0, g.tabY, w, g.tabY), "#1D2B3D", 1, 0.08)
        for k in 0..<4 {
            let cx = w * (Double(k) + 0.5) / 4
            let cy = g.tabY + 18
            let color = k == 1 ? coral : "#8A93A0"
            if k == 0 {
                var p = Path()
                p.move(to: CGPoint(x: cx - 8, y: cy - 1))
                p.addLine(to: CGPoint(x: cx, y: cy - 8))
                p.addLine(to: CGPoint(x: cx + 8, y: cy - 1))
                p.move(to: CGPoint(x: cx - 6, y: cy - 3))
                p.addLine(to: CGPoint(x: cx - 6, y: cy + 7))
                p.addLine(to: CGPoint(x: cx + 6, y: cy + 7))
                p.addLine(to: CGPoint(x: cx + 6, y: cy - 3))
                stroke(c, p, color, 1.8)
            } else if k == 1 {
                var p = Path()
                p.move(to: CGPoint(x: cx, y: cy + 8))
                p.addCurve(to: CGPoint(x: cx, y: cy - 9), control1: CGPoint(x: cx - 11, y: cy - 2), control2: CGPoint(x: cx - 7, y: cy - 9))
                p.addCurve(to: CGPoint(x: cx, y: cy + 8), control1: CGPoint(x: cx + 7, y: cy - 9), control2: CGPoint(x: cx + 11, y: cy - 2))
                stroke(c, p, color, 1.8)
                stroke(c, oval(cx, cy - 2, 2.6, 2.6), color, 1.8)
            } else if k == 2 {
                parcel(c, cx, cy - 1, 0.75, color)
            } else {
                stroke(c, oval(cx, cy - 4, 4, 4), color, 1.8)
                var p = Path()
                p.move(to: CGPoint(x: cx - 8, y: cy + 8))
                p.addQuadCurve(to: CGPoint(x: cx + 8, y: cy + 8), control: CGPoint(x: cx, y: cy - 2))
                stroke(c, p, color, 1.8)
            }
        }
    }

    /// Margins around the card in its sprite, room for its two shadows.
    static let cardPad = CGSize(width: 40, height: 40)

    /// The alert card with its two shadows, drawn with its top left at the origin.
    static func card(_ c: GraphicsContext, _ g: Geo) {
        let w = g.cardW
        let h = g.cardH
        blurred(c, rrect(4, 22, w - 8, h, 26), "#0E1726", 0.3, 22)
        blurred(c, rrect(0, 2, w, h, 26), "#0E1726", 0.22, 2.5)
        c.fill(rrect(0, 0, w, h, 26), with: linear(0, 0, 0, h, ["#FFFEFB", "#FBF7F1", "#F5EFE6"]))
        stroke(c, rrect(0.5, 0.5, w - 1, h - 1, 25.5), "#FFFFFF", 1, 0.9)
        var gc = c
        gc.clip(to: rrect(0, 0, w, h, 26))
        grain(gc, CGRect(x: 0, y: 0, width: w, height: h), 0.05)
        tile(c, 20, 20, 48, "#F58B66", "#E2553C")
        parcel(c, 44, 43, 1.1, "#FFF3EA")
        text(c, "Your parcel is 4 min away", .system(size: 17, weight: .semibold), ink, 82, 40)
        text(c, "Leo is on Linden Street now.", .system(size: 14, weight: .regular), "#5C6776", 82, 62)
        text(c, "Door code 2417 is ready.", .system(size: 14, weight: .regular), "#5C6776", 82, 81)
        let l = g.later
        fill(c, Path(roundedRect: l, cornerRadius: l.height / 2), "#EEE7DD")
        text(c, "Later", .system(size: 16, weight: .semibold), ink, l.midX, l.minY + 28, anchor: 0.5)
        let t = g.track
        blurred(c, Path(roundedRect: t.offsetBy(dx: 0, dy: 2), cornerRadius: t.height / 2), "#C8452C", 0.35, 4)
        c.fill(Path(roundedRect: t, cornerRadius: t.height / 2), with: linear(0, t.minY, 0, t.maxY, ["#F48563", coral, "#DC5236"]))
        stroke(c, Path(roundedRect: t.insetBy(dx: 0.5, dy: 0.5), cornerRadius: t.height / 2), "#FFFFFF", 1, 0.25)
        text(c, "Track", .system(size: 16, weight: .semibold), "#FFF8F2", t.midX, t.minY + 28, anchor: 0.5)
    }

    @MainActor
    static func cardSprite(_ g: Geo) -> Image? {
        let pad = cardPad
        let view = Canvas { c, _ in
            var cc = c
            cc.translateBy(x: pad.width, y: pad.height)
            card(cc, g)
        }
        .frame(width: g.cardW + pad.width * 2, height: g.cardH + pad.height * 2)
        let r = ImageRenderer(content: view)
        r.scale = 3
        return r.uiImage.map { Image(uiImage: $0).interpolation(.high) }
    }

    // MARK: Per frame

    /// Meridian point s (0 apex .. 1 hem) of gore edge e of a canopy, in its own frame (hem centre at 0).
    static func meridian(_ c: Canopy, _ e: Double, _ s: Double) -> CGPoint {
        let phi = (e / Double(ParachuteDropAlertMotion.gores) - 0.5) * .pi
        let k = c.collapse
        let x = c.w / 2 * sin(phi) * sin(s * .pi / 2) * (1 + 0.1 * k * sin(e * 2.1 + s * 3))
        // Collapsing: the hem drops in folds and the crown sinks below it.
        let sag = k * (10 + 14 * abs(sin(e * 1.9 + 0.6))) * s
        return CGPoint(x: x, y: -c.h * cos(s * .pi / 2) + sag)
    }

    static func scallop(_ c: Canopy, _ a: CGPoint, _ z: CGPoint) -> CGPoint {
        CGPoint(x: (a.x + z.x) / 2, y: (a.y + z.y) / 2 - c.h * 0.09 * (1 - c.collapse * 0.5))
    }

    static func canopyPath(_ c: Canopy) -> Path {
        let G = ParachuteDropAlertMotion.gores
        var b = Path()
        for s in 0...8 {
            let m = meridian(c, 0, Double(s) / 8)
            if s == 0 { b.move(to: m) } else { b.addLine(to: m) }
        }
        for e in 1...G {
            let a = meridian(c, Double(e - 1), 1)
            let z = meridian(c, Double(e), 1)
            b.addQuadCurve(to: z, control: scallop(c, a, z))
        }
        for s in stride(from: 8, through: 0, by: -1) { b.addLine(to: meridian(c, Double(G), Double(s) / 8)) }
        b.closeSubpath()
        return b
    }

    static func gorePath(_ c: Canopy, _ e: Int) -> Path {
        var b = Path()
        var m = CGPoint.zero
        for s in 0...8 {
            m = meridian(c, Double(e), Double(s) / 8)
            if s == 0 { b.move(to: m) } else { b.addLine(to: m) }
        }
        let z = meridian(c, Double(e + 1), 1)
        b.addQuadCurve(to: z, control: scallop(c, m, z))
        for s in stride(from: 8, through: 0, by: -1) { b.addLine(to: meridian(c, Double(e + 1), Double(s) / 8)) }
        b.closeSubpath()
        return b
    }

    /// Where a canopy's lines meet the card (two clips at the card's top corners), in screen space.
    static func attach(_ g: Geo, _ p: Pose, _ side: Double) -> CGPoint {
        let lx = side * (g.cardW / 2 - 34)
        let ly = -g.cardH / 2
        let cs = cos(p.cardRot), sn = sin(p.cardRot)
        return CGPoint(x: p.cardX + lx * cs - ly * sn, y: p.cardY + lx * sn + ly * cs)
    }

    static func canopy(_ cv: GraphicsContext, _ g: Geo, _ p: Pose, _ c: Canopy) {
        if c.alpha <= 0.001 { return }
        let G = ParachuteDropAlertMotion.gores
        // Lines first: from each hem point to the nearer clip (or trailing free once released).
        let cs = cos(c.tilt), sn = sin(c.tilt)
        var lines = Path()
        for e in 0...G {
            let m = meridian(c, Double(e), 1)
            let hx = c.x + m.x * cs - m.y * sn
            let hy = c.y + m.x * sn + m.y * cs
            let fe = Double(e) / Double(G)
            let a = c.attached ? attach(g, p, Double(e) < Double(G) / 2 ? -1 : 1)
                : CGPoint(x: c.x + (fe - 0.5) * 30 - 40, y: c.y + g.lines * 0.55)
            let mx = (hx + a.x) / 2 + c.slack * 18 * (fe - 0.5)
            let my = (hy + a.y) / 2 + c.slack * 30
            lines.move(to: CGPoint(x: hx, y: hy))
            lines.addQuadCurve(to: a, control: CGPoint(x: mx, y: my))
        }
        stroke(cv, lines, "#2E3A4A", 1, 0.6 * c.alpha)
        var lc = cv
        lc.translateBy(x: c.x, y: c.y)
        lc.rotate(by: .radians(c.tilt))
        let outline = canopyPath(c)
        let alpha = c.alpha
        lc.drawLayer { l in
            l.opacity = alpha
            for e in 0..<G { fill(l, gorePath(c, e), e % 2 == 0 ? coral : "#FBEFE2") }
            // Shade across the dome: lit from the upper left, darker at the far edge and the hem.
            var sc = l
            sc.clip(to: outline)
            let box = Path(CGRect(x: -c.w, y: -c.h * 1.4, width: c.w * 2, height: c.h * 1.8))
            sc.fill(box, with: linear(-c.w / 2, 0, c.w / 2, 0, ["rgba(255,255,255,0.22)", "rgba(255,255,255,0)", "rgba(60,30,30,0.1)", "rgba(60,30,30,0.32)"], [0, 0.35, 0.7, 1]))
            sc.fill(box, with: linear(0, -c.h, 0, 10, ["rgba(255,255,255,0.1)", "rgba(0,0,0,0)", "rgba(70,30,20,0.25)"], [0, 0.6, 1]))
            var seams = Path()
            for e in 1..<G {
                for s in 0...8 {
                    let m = meridian(c, Double(e), Double(s) / 8)
                    if s == 0 { seams.move(to: m) } else { seams.addLine(to: m) }
                }
            }
            stroke(l, seams, "#7A3426", 0.8, 0.35)
            stroke(l, outline, "#7A3426", 1, 0.3)
            // The vent at the crown.
            let m = meridian(c, Double(G) / 2, 0.04)
            fill(l, Path(ellipseIn: CGRect(x: m.x - c.w * 0.05, y: m.y - 2, width: c.w * 0.1, height: 5)), "#3A2420", 0.55 * (1 - c.collapse))
        }
    }

    static func alert(_ c: GraphicsContext, _ g: Geo, _ card: GraphicsContext.ResolvedImage?, _ p: Pose, pressed: Int) {
        if p.scrim > 0.002 { fill(c, Path(CGRect(x: 0, y: 0, width: g.w, height: g.h)), "#1A2433", p.scrim) }
        if !p.shown { return }
        // Canopies behind the card first (a draped one lies over its top edge, so it comes after).
        for k in p.canopies where k.collapse < 0.35 { canopy(c, g, p, k) }
        var cc = c
        cc.translateBy(x: p.cardX, y: p.cardY)
        cc.rotate(by: .radians(p.cardRot))
        cc.translateBy(x: 0, y: g.cardH / 2)
        cc.scaleBy(x: p.sx, y: p.sy)
        cc.translateBy(x: -g.cardW / 2, y: -g.cardH)
        if let card {
            cc.draw(card, in: CGRect(x: -cardPad.width, y: -cardPad.height, width: g.cardW + cardPad.width * 2, height: g.cardH + cardPad.height * 2))
        }
        if pressed >= 0 {
            let r = pressed == 0 ? g.later : g.track
            fill(cc, Path(roundedRect: r, cornerRadius: r.height / 2), "#1D2B3D", 0.1)
        }
        // The two clips the lines hook into.
        for s in [-1.0, 1.0] {
            let x = g.cardW / 2 + s * (g.cardW / 2 - 34)
            fill(cc, oval(x, 0, 4.5, 4.5), "#C9CFD6")
            stroke(cc, oval(x, 0, 4.5, 4.5), "#6E7884", 1)
        }
        for k in p.canopies where k.collapse >= 0.35 { canopy(c, g, p, k) }
    }
}

/// The app under the alert, drawn once and rasterised.
struct ParachuteDropAlertApp: View, Equatable {
    let g: ParachuteDropAlertMotion.Geo

    var body: some View {
        Canvas { c, _ in ParachuteDropAlertArt.app(c, g) }
            .frame(width: g.w, height: g.h)
            .drawingGroup()
    }
}
