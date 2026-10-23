import SwiftUI

/// Draws the light table and the loupe into a Canvas.
enum LightTableLoupePainter {
    typealias M = LightTableLoupeModel
    typealias L = LightTableLoupe.Layout

    static let red = Color(red: 0.898, green: 0.204, blue: 0.173)

    /// Seeded noise: the same numbers on every platform (32-bit integer hash).
    static func hash(_ i: Int, _ seed: Int) -> Double {
        var x = UInt32(truncatingIfNeeded: i &* 374_761_393 &+ seed &* 668_265_263)
        x = (x ^ (x >> 13)) &* 1_274_126_177
        x ^= x >> 16
        return Double(x) / 4_294_967_296
    }

    // MARK: Table

    static func table(_ ctx: inout GraphicsContext, layout: L, frame f: LightTableLoupeFrame, size: CGSize) {
        let panel = CGRect(x: 0, y: layout.header, width: size.width, height: size.height - layout.header)
        let mid = CGPoint(x: panel.midX, y: panel.midY)
        let reach = hypot(panel.width, panel.height) / 2
        ctx.fill(Path(panel), with: .radialGradient(
            Gradient(colors: [M.color(0xEEF1F4), M.color(0xCFD6DD)]), center: mid, startRadius: 0, endRadius: reach))
        // The light breathes ±1.5 %.
        ctx.fill(Path(panel), with: .color(.white.opacity(0.03 + 0.03 * f.pulse)))
        // Diffuser grain.
        var dark = Path(), bright = Path()
        for i in 0..<700 {
            let x = hash(i, 3) * size.width
            let y = layout.header + hash(i, 4) * panel.height
            let dot = CGRect(x: x - 0.6, y: y - 0.6, width: 1.2, height: 1.2)
            if hash(i, 5) > 0.5 { dark.addEllipse(in: dot) } else { bright.addEllipse(in: dot) }
        }
        ctx.fill(dark, with: .color(.black.opacity(0.05)))
        ctx.fill(bright, with: .color(.white.opacity(0.5)))
        // Colour spilling through each slide onto the table.
        for i in 0..<12 {
            let c = layout.center(i)
            let r = layout.mount.width * 0.85
            let col = M.slides[i].bottom
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .radialGradient(
                Gradient(colors: [M.color(col, 0.16), M.color(col, 0)]), center: c, startRadius: 0, endRadius: r))
        }
        // The parking ring.
        ctx.stroke(Path(ellipseIn: CGRect(x: layout.park.x - 68, y: layout.park.y - 68, width: 136, height: 136)),
                   with: .color(M.color(0x8E98A3, 0.7)), style: StrokeStyle(lineWidth: 1, dash: [4, 5]))
        viewer(&ctx, layout: layout, frame: f)
        for i in 0..<12 { slide(&ctx, layout: layout, index: i, mark: f.marks[i]) }
        for (i, m) in f.marks { mark(&ctx, layout: layout, index: i, mark: m) }
        // A soft sheen crossing the glass.
        if f.sheen >= 0 {
            let x = -0.5 * size.width + 2 * size.width * f.sheen
            let a = 0.2 * sin(.pi * f.sheen)
            ctx.fill(Path(panel), with: .linearGradient(
                Gradient(stops: [.init(color: .white.opacity(0), location: 0), .init(color: .white.opacity(a), location: 0.5),
                                 .init(color: .white.opacity(0), location: 1)]),
                startPoint: CGPoint(x: x - 90, y: panel.minY), endPoint: CGPoint(x: x + 90, y: panel.minY + 60)))
        }
    }

    /// The slide viewer: a dark body, its screen projecting the slide under the loupe.
    static func viewer(_ ctx: inout GraphicsContext, layout: L, frame f: LightTableLoupeFrame) {
        let v = layout.viewer, sc = layout.screen
        ctx.drawLayer { s in
            s.addFilter(.shadow(color: .black.opacity(0.25), radius: 10, x: 0, y: 6))
            s.fill(Path(roundedRect: v, cornerRadius: 12), with: .linearGradient(
                Gradient(colors: [M.color(0x24272D), M.color(0x121418)]), startPoint: CGPoint(x: v.midX, y: v.minY), endPoint: CGPoint(x: v.midX, y: v.maxY)))
        }
        ctx.stroke(Path(roundedRect: v.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 12), with: .color(.white.opacity(0.08)), lineWidth: 1)
        ctx.fill(Path(roundedRect: sc, cornerRadius: 4), with: .color(M.color(0x0A0B0D)))
        ctx.drawLayer { g in
            g.clip(to: Path(roundedRect: sc, cornerRadius: 4))
            // A dim lamp when the gate is empty.
            g.fill(Path(sc), with: .radialGradient(Gradient(colors: [.white.opacity(0.10), .white.opacity(0)]),
                                                   center: CGPoint(x: sc.midX, y: sc.midY), startRadius: 0, endRadius: sc.width * 0.6))
            let a = f.focus >= 0 ? f.caption : 0
            if a > 0.001 {
                g.drawLayer { p in
                    p.opacity = a
                    art(&p, rect: sc, index: f.focus)
                }
            }
            // Lamp flicker and the projector's falloff.
            g.fill(Path(sc), with: .color(.white.opacity(0.01 + 0.01 * f.flicker)))
            g.fill(Path(sc), with: .radialGradient(Gradient(stops: [.init(color: .black.opacity(0), location: 0.55), .init(color: .black.opacity(0.45), location: 1)]),
                                                   center: CGPoint(x: sc.midX, y: sc.midY), startRadius: 0, endRadius: hypot(sc.width, sc.height) / 2))
        }
        ctx.stroke(Path(roundedRect: sc.insetBy(dx: -0.5, dy: -0.5), cornerRadius: 4.5), with: .color(M.color(0x2E3238)), lineWidth: 1)
    }

    static func slide(_ ctx: inout GraphicsContext, layout: L, index i: Int, mark: (draw: Double, alpha: Double, tilt: Double)?) {
        let o = layout.origin(i)
        let m = CGRect(origin: o, size: layout.mount)
        let tilt = mark?.tilt ?? 0
        ctx.drawLayer { g in
            if tilt != 0 {
                g.translateBy(x: m.midX + 3 * tilt, y: m.midY - 2 * tilt)
                g.rotate(by: .degrees(1.5 * tilt))
                g.translateBy(x: -m.midX, y: -m.midY)
            }
            // The mount, with a fine shadow on the table.
            g.drawLayer { s in
                s.addFilter(.shadow(color: .black.opacity(0.14), radius: 2.5, x: 0, y: 1.5))
                s.fill(Path(roundedRect: m, cornerRadius: 3), with: .color(M.color(0xF7F6F2)))
            }
            g.stroke(Path(roundedRect: m.insetBy(dx: 0.25, dy: 0.25), cornerRadius: 3), with: .color(.black.opacity(0.08)), lineWidth: 0.5)
            let w = layout.window(i)
            g.stroke(Path(roundedRect: w.insetBy(dx: -1.5, dy: -1.5), cornerRadius: 2), with: .color(M.color(0xE2DED3)), lineWidth: 2)
            art(&g, rect: w, index: i)
            // Printed on the mount.
            let font = Font.system(size: 6.5, weight: .medium, design: .monospaced)
            let ink = M.color(0x8A867C)
            let y = m.maxY - (m.maxY - w.maxY) / 2
            g.draw(g.resolve(Text(String(format: "%02d", i + 1)).font(font).foregroundColor(ink)), at: CGPoint(x: w.minX, y: y), anchor: .leading)
            g.draw(g.resolve(Text("OCT 84").font(font).foregroundColor(ink)), at: CGPoint(x: w.maxX, y: y), anchor: .trailing)
        }
    }

    /// A transparency, lit from behind.
    static func art(_ ctx: inout GraphicsContext, rect r: CGRect, index: Int) {
        let s = M.slides[index]
        ctx.drawLayer { g in
            g.clip(to: Path(r))
            g.fill(Path(r), with: .linearGradient(Gradient(colors: [M.color(s.top), M.color(s.bottom)]),
                                                  startPoint: CGPoint(x: r.midX, y: r.minY), endPoint: CGPoint(x: r.midX, y: r.maxY)))
            if index == M.auroraIndex {
                g.fill(Path(r), with: .radialGradient(Gradient(colors: [M.color(0x38F2A0, 0.55), M.color(0x38F2A0, 0)]),
                                                      center: CGPoint(x: r.minX + 0.35 * r.width, y: r.minY + 0.40 * r.height), startRadius: 0, endRadius: 0.5 * r.width))
                g.fill(Path(r), with: .radialGradient(Gradient(colors: [M.color(0x8F5BFF, 0.40), M.color(0x8F5BFF, 0)]),
                                                      center: CGPoint(x: r.minX + 0.72 * r.width, y: r.minY + 0.22 * r.height), startRadius: 0, endRadius: 0.42 * r.width))
            }
            if s.sun.r > 0 {
                let c = CGPoint(x: r.minX + s.sun.x * r.width, y: r.minY + s.sun.y * r.height)
                let rad = s.sun.r * r.width
                g.fill(Path(r), with: .radialGradient(Gradient(colors: [M.color(s.sun.color, s.sun.glow), M.color(s.sun.color, 0)]),
                                                      center: c, startRadius: 0, endRadius: rad * 3))
                g.fill(Path(ellipseIn: CGRect(x: c.x - rad, y: c.y - rad, width: 2 * rad, height: 2 * rad)), with: .color(M.color(s.sun.color)))
            }
            for ridge in s.ridges {
                var p = Path()
                p.move(to: CGPoint(x: r.minX, y: r.maxY))
                for k in 0...24 {
                    let u = Double(k) / 24
                    let y = ridge.base + ridge.amp * sin(2 * .pi * ridge.freq * u + ridge.phase)
                    p.addLine(to: CGPoint(x: r.minX + u * r.width, y: r.minY + y * r.height))
                }
                p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
                p.closeSubpath()
                g.fill(p, with: .color(M.color(ridge.color)))
            }
            // Backlight: brighter in the middle, a soft dark edge where the film meets the mount.
            g.fill(Path(r), with: .radialGradient(Gradient(colors: [.white.opacity(0.22), .white.opacity(0)]),
                                                  center: CGPoint(x: r.midX, y: r.midY), startRadius: 0, endRadius: hypot(r.width, r.height) * 0.5))
            g.stroke(Path(r.insetBy(dx: 0.75, dy: 0.75)), with: .color(.black.opacity(0.22)), lineWidth: 1.5)
            g.stroke(Path(r.insetBy(dx: 1.5, dy: 1.5)), with: .color(.black.opacity(0.08)), lineWidth: 3)
        }
    }

    /// The grease-pencil circle: two rough turns starting at the upper left.
    static func markPath(layout: L, index i: Int, tilt: Double) -> Path {
        let c0 = layout.center(i)
        let c = CGPoint(x: c0.x + 3 * tilt, y: c0.y - 2 * tilt)
        let rx = layout.mount.width / 2 + 9, ry = layout.mount.height / 2 + 9
        var p = Path()
        let n = 72
        for k in 0...n {
            let th = 2 * .pi * 1.12 * Double(k) / Double(n)
            let a = .pi * 1.15 + th
            let j = 1 + 0.035 * sin(3 * th + 0.7) + 0.02 * sin(7 * th + 1.3) + th / (2 * .pi) * 0.05
            let pt = CGPoint(x: c.x + rx * j * cos(a), y: c.y + ry * j * sin(a))
            if k == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        return p
    }

    static func mark(_ ctx: inout GraphicsContext, layout: L, index i: Int, mark m: (draw: Double, alpha: Double, tilt: Double)) {
        guard m.draw > 0, m.alpha > 0 else { return }
        let path = markPath(layout: layout, index: i, tilt: m.tilt).trimmedPath(from: 0, to: m.draw)
        ctx.stroke(path, with: .color(red.opacity(0.9 * m.alpha)), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
        ctx.stroke(path.applying(CGAffineTransform(translationX: 0.8, y: 0.6)), with: .color(red.opacity(0.45 * m.alpha)),
                   style: StrokeStyle(lineWidth: 1.2, lineCap: .round, lineJoin: .round))
    }

    // MARK: Loupe

    static func loupe(_ ctx: inout GraphicsContext, layout: L, frame f: LightTableLoupeFrame, size: CGSize) {
        let c = f.loupe
        let s = 1 + 0.04 * f.lift - 0.04 * f.press
        let lens = LightTableLoupe.lens * s
        func circle(_ r: Double) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)) }
        // Shadow, larger and softer when lifted.
        ctx.drawLayer { g in
            g.addFilter(.blur(radius: 6 + 8 * f.lift))
            g.fill(Path(ellipseIn: CGRect(x: c.x - 64 * s + 4 + 6 * f.lift, y: c.y - 64 * s + 8 + 10 * f.lift, width: 128 * s, height: 128 * s)),
                   with: .color(.black.opacity(0.30 + 0.08 * f.lift)))
        }
        // The table, 3 × larger under the glass.
        ctx.drawLayer { g in
            g.clip(to: circle(lens))
            if f.blur > 0.1 { g.addFilter(.blur(radius: f.blur)) }
            g.translateBy(x: c.x, y: c.y)
            g.scaleBy(x: LightTableLoupe.zoom, y: LightTableLoupe.zoom)
            g.translateBy(x: -c.x, y: -c.y)
            table(&g, layout: layout, frame: f, size: size)
        }
        ctx.drawLayer { g in
            g.clip(to: circle(lens))
            // Film grain under the glass.
            var dark = Path(), bright = Path()
            for i in 0..<400 {
                let a = hash(i, 7) * 2 * .pi, d = sqrt(hash(i, 8)) * lens
                let dot = CGRect(x: c.x + d * cos(a) - 0.5, y: c.y + d * sin(a) - 0.5, width: 1, height: 1)
                if hash(i, 9) > 0.5 { dark.addEllipse(in: dot) } else { bright.addEllipse(in: dot) }
            }
            g.fill(dark, with: .color(.black.opacity(0.12)))
            g.fill(bright, with: .color(.white.opacity(0.18)))
            g.fill(circle(lens), with: .radialGradient(Gradient(stops: [.init(color: .black.opacity(0), location: 0.7), .init(color: .black.opacity(0.35), location: 1)]),
                                                       center: c, startRadius: 0, endRadius: lens))
            // A glass highlight.
            var hl = g
            hl.translateBy(x: c.x - 17 * s, y: c.y - 23 * s)
            hl.rotate(by: .degrees(-30))
            hl.fill(Path(ellipseIn: CGRect(x: -23 * s, y: -12 * s, width: 46 * s, height: 24 * s)),
                    with: .linearGradient(Gradient(colors: [.white.opacity(0.32), .white.opacity(0)]),
                                          startPoint: CGPoint(x: 0, y: -12 * s), endPoint: CGPoint(x: 0, y: 12 * s)))
        }
        // Colour fringes at the rim.
        ctx.stroke(Path(ellipseIn: CGRect(x: c.x - lens + 0.2, y: c.y - lens + 0.6, width: 2 * lens - 1.6, height: 2 * lens - 1.6)),
                   with: .color(Color(red: 1, green: 0.2, blue: 0.2).opacity(0.35)), lineWidth: 1.2)
        ctx.stroke(Path(ellipseIn: CGRect(x: c.x - lens + 1.4, y: c.y - lens + 0.6, width: 2 * lens - 1.6, height: 2 * lens - 1.6)),
                   with: .color(Color(red: 0.2, green: 0.4, blue: 1).opacity(0.35)), lineWidth: 1.2)
        // Chrome lip and black housing.
        ctx.stroke(circle(53.5 * s), with: .linearGradient(Gradient(colors: [M.color(0xF2F2F2), M.color(0x7A7A7A)]),
                                                           startPoint: CGPoint(x: c.x - 54, y: c.y - 54), endPoint: CGPoint(x: c.x + 54, y: c.y + 54)), lineWidth: 2.2)
        ctx.stroke(circle(59 * s), with: .color(M.color(0x141414)), lineWidth: 9)
        ctx.stroke(circle(63.6 * s), with: .linearGradient(Gradient(colors: [M.color(0x5A5A5A), M.color(0x1A1A1A)]),
                                                           startPoint: CGPoint(x: c.x, y: c.y - 64), endPoint: CGPoint(x: c.x, y: c.y + 64)), lineWidth: 0.8)
    }
}
