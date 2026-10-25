import SwiftUI

/*
 Everything drawn in the dock's canvases.
 Static (rendered once, under the panel): the graphite backdrop and the artboard with a harbour
 sketch in progress (gouache sun, ink water strokes, a boat, a selected pen path).
 Live (the canvas over the panel): the dock (rendered once), the plate under the active tool, six
 enamel tool tiles (each face rendered once), their shadows, the field lines and the fingertip.
 */
enum MagnetSnapDockArt {
    static let toolColors: [UInt32] = [0x5B606B, 0x2F7D76, 0xC4532F, 0xCF9A32, 0x57683C, 0x3F5F8A]
    private static let glyphColor: UInt32 = 0xF6F1E8
    private static let icon = MagnetSnapDockMotion.icon
    private static let R = MagnetSnapDockMotion.icon * 0.27

    struct Images {
        let backdrop: Image
        let dock: Image
        let faces: [Image]
    }

    private static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(hex: hex, opacity: a)
    }

    private static func shade(_ hex: UInt32, _ k: Double) -> Color {
        let c = (Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
        func f(_ v: Double) -> Double { k >= 0 ? v + (1 - v) * k : v * (1 + k) }
        return Color(.sRGB, red: f(c.0), green: f(c.1), blue: f(c.2), opacity: 1)
    }

    private static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ stops: [(Color, Double)]) -> GraphicsContext.Shading {
        .linearGradient(
            Gradient(stops: stops.map { .init(color: $0.0, location: $0.1) }),
            startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1)
        )
    }

    private static func blurred(_ c: GraphicsContext, _ sigma: Double) -> GraphicsContext {
        var copy = c
        copy.addFilter(.blur(radius: sigma))
        return copy
    }

    private static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: max(0, r), style: .circular)
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    private static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    private static func poly(_ pts: [Double], close: Bool = true) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: pts[0], y: pts[1]))
        for i in stride(from: 2, to: pts.count, by: 2) { p.addLine(to: CGPoint(x: pts[i], y: pts[i + 1])) }
        if close { p.closeSubpath() }
        return p
    }

    private static func round(_ width: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    private static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }

    @MainActor static func render(_ g: MagnetSnapDockGeo, scale: Double) -> Images {
        func image<V: View>(_ w: Double, _ h: Double, _ content: V) -> Image {
            let renderer = ImageRenderer(content: content.frame(width: w, height: h))
            renderer.scale = scale
            return renderer.uiImage.map { Image(uiImage: $0) } ?? Image(systemName: "circle")
        }
        let faces = (0..<MagnetSnapDockMotion.tools).map { i in
            image(icon * 2, icon * 2, Canvas { c, _ in
                var c = c
                c.translateBy(x: icon, y: icon)
                drawFace(c, i)
            })
        }
        return Images(
            backdrop: image(g.w, g.h, Canvas { c, _ in drawBackdrop(c, g) }),
            dock: image(g.w, g.h, Canvas { c, _ in drawDock(c, g) }),
            faces: faces
        )
    }

    /// One glyph, centred on the origin, about 28 pt across. `mono` paints every part one colour
    /// (the glyph's shadow).
    private static func glyph(_ c: GraphicsContext, _ tool: Int, mono: Color? = nil) {
        let ink = mono ?? color(glyphColor)
        let accent = mono ?? color(toolColors[tool])
        switch tool {
        case 0:
            c.fill(poly([-6, -12, 9, 3, 2.5, 4, 6.5, 11.5, 3.2, 13, -0.6, 5.6, -6, 10.5]), with: .color(ink))
        case 1:
            c.fill(poly([0, -12, 8.5, 1, 3.4, 11, -3.4, 11, -8.5, 1]), with: .color(ink))
            c.stroke(line(0, -11, 0, 2), with: .color(accent), style: round(1.4))
            c.fill(circle(0, 2.5, 2.2), with: .color(accent))
            c.fill(rr(-4.5, 12.5, 9, 2.6, 1.3), with: .color(ink))
        case 2:
            var b = c
            b.rotate(by: .degrees(-38))
            b.fill(rr(-2.4, -15, 4.8, 15, 2.4), with: .color(ink))
            if let mono {
                b.fill(rr(-3.6, -0.5, 7.2, 5, 1.2), with: .color(mono))
            } else {
                b.fill(rr(-3.6, -0.5, 7.2, 5, 1.2), with: linear(-3.6, 0, 3.6, 0, [(color(0xB9BEC6), 0), (color(0xFFFFFF), 0.45), (color(0x8D939B), 1)]))
            }
            var tip = Path()
            tip.move(to: CGPoint(x: -3.6, y: 4.5))
            tip.addCurve(to: CGPoint(x: 0.6, y: 16), control1: CGPoint(x: -4.2, y: 10), control2: CGPoint(x: -1.5, y: 13.5))
            tip.addCurve(to: CGPoint(x: 3.6, y: 4.5), control1: CGPoint(x: 2.4, y: 12.5), control2: CGPoint(x: 4.4, y: 9.5))
            tip.closeSubpath()
            b.fill(tip, with: .color(ink))
        case 3:
            c.fill(rr(-1, -1, 12, 12, 2.5), with: .color(mono ?? color(glyphColor, 0.62)))
            c.stroke(circle(-3.5, -3.5, 7.5), with: .color(ink), style: round(2))
        case 4:
            // drawText's baseline at y = 7: the line box's centre sits about 0.355 em above it.
            c.draw(Text("Aa").font(.system(size: 19, weight: .bold)).foregroundColor(ink), at: CGPoint(x: 0, y: 7 - 19 * 0.355))
        default:
            for k in stride(from: 2, through: 0, by: -1) {
                let y = Double(k - 1) * 6
                let p = poly([0, y - 6, 11, y, 0, y + 6, -11, y])
                if k == 0 {
                    c.fill(p, with: .color(ink))
                } else {
                    c.fill(p, with: .color(accent))
                    c.stroke(p, with: .color(mono ?? color(glyphColor, 0.75 - Double(k) * 0.15)), style: round(1.6))
                }
            }
        }
    }

    /// One enamel tool face, centred on the origin.
    private static func drawFace(_ c: GraphicsContext, _ i: Int) {
        let h = icon / 2
        let base = toolColors[i]
        c.fill(rr(-h, -h, icon, icon, R), with: linear(-h, -h, h * 0.4, h, [(shade(base, 0.28), 0), (shade(base, 0.05), 0.5), (shade(base, -0.22), 1)]))
        // Enamel gloss: a soft sheen across the top half.
        c.fill(rr(-h + 3, -h + 2, icon - 6, icon * 0.46, R - 3), with: linear(0, -h, 0, 0, [(.white.opacity(0.22), 0), (.white.opacity(0.02), 1)]))
        c.stroke(rr(-h + 0.6, -h + 0.6, icon - 1.2, icon - 1.2, R - 0.6), with: .color(.white.opacity(0.26)), lineWidth: 1)
        c.stroke(rr(-h + 1.2, -h + 2.4, icon - 2.4, icon - 3.2, R - 1), with: .color(.black.opacity(0.14)), lineWidth: 1)
        // Glyph shadow, then the glyph.
        c.drawLayer { layer in
            layer.translateBy(x: 0, y: 1.2)
            layer.addFilter(.blur(radius: 1.2))
            glyph(layer, i, mono: .black.opacity(0.35))
        }
        glyph(c, i)
    }

    /// Graphite backdrop and the artboard: a harbour study in progress.
    private static func drawBackdrop(_ c: GraphicsContext, _ g: MagnetSnapDockGeo) {
        let all = Path(CGRect(x: 0, y: 0, width: g.w, height: g.h))
        c.fill(all, with: linear(0, 0, 0, g.h, [(color(0x1D1F24), 0), (color(0x16181C), 0.5), (color(0x111215), 1)]))
        c.fill(all, with: .radialGradient(
            Gradient(stops: [.init(color: .black.opacity(0), location: 0.5), .init(color: .black.opacity(0.45), location: 1)]),
            center: CGPoint(x: g.w / 2, y: g.h * 0.38), startRadius: 0, endRadius: g.h * 0.75
        ))

        let bx = 20.0
        let by = g.boardTop
        let bw = g.w - 40
        let bh = max(120, g.panelTop - 16 - by)
        blurred(c, 16).fill(rr(bx, by + 14, bw, bh, 18), with: .color(.black.opacity(0.4)))
        blurred(c, 2).fill(rr(bx, by + 2, bw, bh, 18), with: .color(.black.opacity(0.4)))
        let board = rr(bx, by, bw, bh, 18)
        c.fill(board, with: linear(0, by, 0, by + bh, [(color(0xF5EFE4), 0), (color(0xEFE7D9), 0.6), (color(0xE7DDCC), 1)]))
        var a = c
        a.clip(to: board)
        // Paper tooth (Skia's fractal noise, multiplied, in RN), as one batch of faint specks.
        var specks = Path()
        for i in 0..<Int(bw * bh / 10) {
            let ii = Double(i)
            specks.addRect(CGRect(x: bx + hash(ii * 1.37 + 0.5) * bw, y: by + hash(ii * 2.11 + 7.3) * bh, width: 1, height: 1))
        }
        a.fill(specks, with: .color(color(0x6A5A40, 0.08)))
        // Sky wash, the gouache sun, the horizon.
        let horizon = by + bh * 0.56
        a.fill(Path(CGRect(x: bx, y: by, width: bw, height: horizon - by)), with: linear(0, by, 0, horizon, [(color(0xE9CFAE, 0), 0), (color(0xEAB894, 0.55), 0.7), (color(0xE7A27E, 0.6), 1)]))
        let sunR = min(30, bh * 0.13)
        a.fill(circle(bx + bw * 0.66, horizon - bh * 0.17, sunR), with: .color(color(0xD4572F, 0.88)))
        blurred(a, 6).fill(circle(bx + bw * 0.66 - 6, horizon - bh * 0.17 - 6, sunR * 0.5), with: .color(color(0xF08A5C, 0.6)))
        // Ink water: wavy dry-brush strokes, shorter toward the horizon.
        for k in 0..<9 {
            let kk = Double(k)
            let y = horizon + 8 + kk * (bh * 0.42) / 9
            let len = bw * (0.3 + 0.07 * kk)
            let x0 = bx + 14 + Double((k * 37) % 60)
            var p = Path()
            p.move(to: CGPoint(x: x0, y: y))
            var x = 0.0
            while x <= len {
                p.addLine(to: CGPoint(x: x0 + x, y: y + sin(x * 0.06 + kk) * 1.8))
                x += 12
            }
            a.stroke(p, with: .color(color(0x2A2F3A, 0.55 + 0.04 * kk)), style: round(1.6 + kk * 0.35))
        }
        a.stroke(line(bx + 10, horizon, bx + bw - 10, horizon), with: .color(color(0x2A2F3A, 0.8)), style: round(1.4))
        // A small sailing boat.
        let boatX = bx + bw * 0.3
        let ink = color(0x2A2F3A)
        a.fill(poly([boatX - 30, horizon + 4, boatX + 30, horizon + 4, boatX + 20, horizon + 14, boatX - 22, horizon + 14]), with: .color(ink))
        a.stroke(line(boatX, horizon + 4, boatX, horizon - 52), with: .color(ink), style: round(1.6))
        let sail = poly([boatX + 2, horizon - 50, boatX + 2, horizon - 2, boatX + 30, horizon - 2])
        a.fill(sail, with: .color(color(0xF7F2E8, 0.95)))
        a.stroke(sail, with: .color(ink), style: round(1.2))
        a.fill(poly([boatX - 2, horizon - 40, boatX - 2, horizon - 2, boatX - 22, horizon - 2]), with: .color(color(0x2F7D76, 0.85)))
        // The selected pen path, with anchors and a handle.
        let sel = color(0x3F8CFF)
        let p0 = CGPoint(x: bx + 24, y: by + bh * 0.3)
        let p1 = CGPoint(x: bx + bw * 0.42, y: by + bh * 0.14)
        let p2 = CGPoint(x: bx + bw - 30, y: by + bh * 0.26)
        var path = Path()
        path.move(to: p0)
        path.addCurve(to: p1, control1: CGPoint(x: p0.x + 40, y: p0.y - 30), control2: CGPoint(x: p1.x - 60, y: p1.y))
        path.addCurve(to: p2, control1: CGPoint(x: p1.x + 60, y: p1.y), control2: CGPoint(x: p2.x - 50, y: p2.y - 30))
        a.stroke(path, with: .color(sel), style: round(1.4))
        a.stroke(line(p1.x - 60, p1.y, p1.x + 60, p1.y), with: .color(color(0x3F8CFF, 0.7)), style: round(1))
        for x in [p1.x - 60, p1.x + 60] { a.fill(circle(x, p1.y, 3), with: .color(sel)) }
        for p in [p0, p1, p2] {
            let r = Path(CGRect(x: p.x - 4, y: p.y - 4, width: 8, height: 8))
            a.fill(r, with: .color(.white))
            a.stroke(r, with: .color(sel), style: round(1.4))
        }
    }

    /// The dock: an anodised bar with two shadows and a lit top edge, rendered once.
    private static func drawDock(_ c: GraphicsContext, _ g: MagnetSnapDockGeo) {
        let x = g.dockX, y = g.dockY, w = g.dockW, dh = MagnetSnapDockMotion.dockH
        blurred(c, 18).fill(rr(x + 6, y + 18, w - 12, dh - 4, 30), with: .color(.black.opacity(0.5)))
        blurred(c, 2.5).fill(rr(x, y + 2, w, dh, 30), with: .color(.black.opacity(0.45)))
        let bar = rr(x, y, w, dh, 30)
        c.fill(bar, with: linear(0, y, 0, y + dh, [(color(0x34373E), 0), (color(0x2A2D33), 0.3), (color(0x212328), 0.7), (color(0x1A1C20), 1)]))
        // Brushed grain (stretched fractal noise in RN): faint horizontal streaks.
        var inside = c
        inside.clip(to: bar)
        var light = Path()
        var dark = Path()
        for i in 0..<Int(w * dh / 40) {
            let ii = Double(i)
            let r = CGRect(x: x + hash(ii * 1.37 + 0.5) * w, y: y + hash(ii * 2.11 + 7.3) * dh, width: 4 + hash(ii * 0.37) * 8, height: 0.7)
            if hash(ii * 0.73 + 3.1) > 0.5 { light.addRect(r) } else { dark.addRect(r) }
        }
        inside.fill(light, with: .color(.white.opacity(0.035)))
        inside.fill(dark, with: .color(.black.opacity(0.06)))
        c.stroke(rr(x + 0.5, y + 0.5, w - 1, dh - 1, 29.5), with: .color(.white.opacity(0.12)), lineWidth: 1)
    }

    /// The recessed plate under the active tool, and its status light.
    static func drawPlate(_ c: GraphicsContext, _ g: MagnetSnapDockGeo, _ px: Double, _ hex: UInt32) {
        let s = icon + 10
        let y = g.cy - s / 2
        c.fill(rr(px - s / 2, y, s, s, 17), with: linear(0, y, 0, y + s, [(color(0x0E0F12), 0), (color(0x16181C), 0.4), (color(0x1C1E23), 1)]))
        c.stroke(rr(px - s / 2 + 0.5, y + 0.5, s - 1, s - 1, 16.5), with: .color(.black.opacity(0.5)), lineWidth: 1)
        c.stroke(line(px - s / 2 + 12, y + s - 0.5, px + s / 2 - 12, y + s - 0.5), with: .color(.white.opacity(0.12)), style: round(1))
        let ly = g.dockY + MagnetSnapDockMotion.dockH - 7
        blurred(c, 4).fill(circle(px, ly, 5), with: .color(color(hex, 0.6)))
        c.fill(rr(px - 7, ly - 1.5, 14, 3, 1.5), with: .color(color(hex)))
    }

    /// A tile's two shadows: tight contact and soft ambient, which grows as the tile lifts.
    static func drawTileShadow(_ c: GraphicsContext, _ x: Double, _ y: Double, _ scale: Double, _ lift: Double) {
        let s = icon * scale
        blurred(c, 2).fill(rr(x - s / 2 + 2, y - s / 2 + 2 + min(2, lift * 0.1), s - 4, s, R * scale), with: .color(.black.opacity(max(0.12, 0.38 - lift * 0.012))))
        blurred(c, 7 + lift * 0.35).fill(rr(x - s / 2 + 4, y - s / 2 + 8 + lift * 0.45, s - 8, s, R * scale), with: .color(.black.opacity(0.3)))
    }

    /// Field lines from each pulled tile to the finger: three bowed curves per tile, dashes flowing
    /// toward the finger (`phase` in pt), plus a faint glow.
    static func drawField(_ c: GraphicsContext, _ t: MagnetSnapDockTiles, _ fx: Double, _ fy: Double, _ phase: Double) {
        let glow = blurred(c, 3)
        for i in 0..<t.x.count where t.pull[i] >= 0.04 {
            let top = t.y[i] - icon * t.scale[i] / 2 + 2
            var outer = Path()
            var middle = Path()
            for k in -1...1 {
                let kk = Double(k)
                let sx = t.x[i] + kk * 9 * t.scale[i]
                let ex = fx + kk * 5
                let ey = fy + 12
                let mx = (sx + ex) / 2
                let my = (top + ey) / 2
                let dx = ex - sx
                let dy = ey - top
                let len = max(1, hypot(dx, dy))
                let bow = kk * 22 + (t.x[i] - fx) * 0.12
                var p = Path()
                p.move(to: CGPoint(x: sx, y: top))
                p.addQuadCurve(to: CGPoint(x: ex, y: ey), control: CGPoint(x: mx + (-dy / len) * bow, y: my + (dx / len) * bow))
                if k == 0 { middle.addPath(p) } else { outer.addPath(p) }
            }
            var all = outer
            all.addPath(middle)
            glow.stroke(all, with: .color(color(0x7FC8FF, 0.16 * t.pull[i])), lineWidth: 3)
            let dash = StrokeStyle(lineWidth: 1.1, lineCap: .round, dash: [5, 7], dashPhase: phase)
            c.stroke(outer, with: .color(color(0xA9DCFF, 0.45 * t.pull[i])), style: dash)
            c.stroke(middle, with: .color(color(0xA9DCFF, 0.75 * t.pull[i])), style: dash)
        }
    }

    /// The fingertip: a soft disc with a cool magnetic core.
    static func drawFinger(_ c: GraphicsContext, _ x: Double, _ y: Double, _ a: Double) {
        if a < 0.01 { return }
        let r = 22 * (1.12 - 0.12 * a)
        blurred(c, 10).fill(circle(x, y, r + 8), with: .color(color(0x7FC8FF, 0.14 * a)))
        c.fill(circle(x, y, r), with: .color(color(0xEAF4FF, 0.14 * a)))
        c.stroke(circle(x, y, r), with: .color(color(0xEAF4FF, 0.5 * a)), lineWidth: 1.4)
        blurred(c, 2).fill(circle(x, y, 4), with: .color(color(0xBFE4FF, 0.8 * a)))
    }
}
