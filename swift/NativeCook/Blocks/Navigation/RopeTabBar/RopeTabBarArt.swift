import SwiftUI

/*
 The tab bar's canvas: a ripstop-nylon bar (rendered once), the climbing rope pinned to two steel
 eyelets at the screen edges, the knot that marks the active tab, and tap ripples.
 Canvas-local coordinates: the canvas starts topPad above the bar.
 */
enum RopeTabBarArt {
    static let topPad = 30.0
    /// The rope's pinned line, below the bar's top edge.
    static let ropeLine = 5.0
    static let samples = 48
    private static let ropeW = 5.2

    private static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(hex: hex, opacity: a)
    }

    private static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    private static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }

    private static func round(_ width: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    /// The bar: dark forest ripstop with a lit top edge, and the two eyelets the rope is tied to.
    @MainActor static func renderBar(w: Double, barH: Double, scale: Double) -> Image {
        let H = barH + topPad
        let renderer = ImageRenderer(content: Canvas { c, _ in drawBar(c, w: w, barH: barH) }.frame(width: w, height: H))
        renderer.scale = scale
        return renderer.uiImage.map { Image(uiImage: $0) } ?? Image(systemName: "circle")
    }

    private static func drawBar(_ c: GraphicsContext, w: Double, barH: Double) {
        let H = barH + topPad
        let y0 = topPad
        // Soft shadow the bar casts upward onto the content.
        c.fill(Path(CGRect(x: 0, y: 0, width: w, height: y0)), with: .linearGradient(
            Gradient(colors: [color(0x0B120E, 0), color(0x0B120E, 0.35)]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: y0)
        ))
        let body = Path(CGRect(x: 0, y: y0, width: w, height: barH))
        c.fill(body, with: .linearGradient(
            Gradient(stops: [
                .init(color: color(0x24372D), location: 0),
                .init(color: color(0x1B2B23), location: 0.25),
                .init(color: color(0x14211B), location: 0.7),
                .init(color: color(0x101A15), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: y0), endPoint: CGPoint(x: 0, y: H)
        ))
        // Ripstop: a fine grid, with a heavier thread every fourth line.
        var fine = Path()
        var heavy = Path()
        var k = 0
        var x = 3.0
        while x < w {
            if k % 4 == 0 { heavy.addPath(line(x, y0, x, H)) } else { fine.addPath(line(x, y0, x, H)) }
            x += 6
            k += 1
        }
        k = 0
        var y = y0 + 3
        while y < H {
            if k % 4 == 0 { heavy.addPath(line(0, y, w, y)) } else { fine.addPath(line(0, y, w, y)) }
            y += 6
            k += 1
        }
        c.stroke(fine, with: .color(color(0xD8E6DC, 0.035)), style: round(0.5))
        c.stroke(heavy, with: .color(color(0xD8E6DC, 0.06)), style: round(0.9))
        // Fibre noise (Skia's fractal noise in RN), as two batches of faint specks.
        var light = Path()
        var dark = Path()
        for i in 0..<Int(w * barH / 12) {
            let ii = Double(i)
            let r = CGRect(x: hash(ii * 1.37 + 0.5) * w, y: y0 + hash(ii * 2.11 + 7.3) * barH, width: 1, height: 1)
            if hash(ii * 0.73 + 3.1) > 0.5 { light.addRect(r) } else { dark.addRect(r) }
        }
        c.fill(light, with: .color(.white.opacity(0.04)))
        c.fill(dark, with: .color(.black.opacity(0.06)))
        c.stroke(line(0, y0 + 0.5, w, y0 + 0.5), with: .color(color(0xE9F2EA, 0.16)), lineWidth: 1)
        // Eyelets at both edges.
        for x in [7.0, w - 7] {
            let cy = y0 + ropeLine
            var shadow = c
            shadow.addFilter(.blur(radius: 2))
            shadow.fill(circle(x, cy + 1.5, 6.5), with: .color(.black.opacity(0.5)))
            c.stroke(circle(x, cy, 5), with: .linearGradient(
                Gradient(stops: [
                    .init(color: color(0xF2F4F6), location: 0),
                    .init(color: color(0x9AA1A9), location: 0.5),
                    .init(color: color(0x4B5158), location: 1),
                ]),
                startPoint: CGPoint(x: x, y: cy - 6), endPoint: CGPoint(x: x, y: cy + 6)
            ), lineWidth: 2.6)
        }
    }

    private static let knotGradient = Gradient(stops: [
        .init(color: color(0xFFA16A), location: 0),
        .init(color: color(0xE2672F), location: 0.35),
        .init(color: color(0xB3461C), location: 0.7),
        .init(color: color(0x6A240C), location: 1),
    ])

    /// Draws the rope from its heights `ys` (canvas y at samples + 1 evenly spaced x across the
    /// width), the knot at (kx, ky) leaning with the rope's slope, and a ripple (0–1, or −1) at
    /// (rx, ry).
    static func drawRope(_ c: GraphicsContext, w: Double, ys: [Double], kx: Double, ky: Double, slope: Double, ripple: Double, rx: Double, ry: Double) {
        let step = w / Double(samples)
        var path = Path()
        path.move(to: CGPoint(x: 0, y: ys[0]))
        for i in 1..<samples {
            let mx = (Double(i) + 0.5) * step
            let my = (ys[i] + ys[i + 1]) / 2
            path.addQuadCurve(to: CGPoint(x: mx, y: my), control: CGPoint(x: Double(i) * step, y: ys[i]))
        }
        path.addLine(to: CGPoint(x: w, y: ys[samples]))

        // Shadow on the bar, then the rope: dark core, dyed sheath, a lit ridge.
        var shadow = c
        shadow.translateBy(x: 0, y: 3.5)
        shadow.addFilter(.blur(radius: 2.4))
        shadow.stroke(path, with: .color(color(0x050A07, 0.55)), style: round(ropeW + 1.5))
        c.stroke(path, with: .color(color(0x6E2810)), style: round(ropeW + 1.2))
        c.stroke(path, with: .color(color(0xDC6430)), style: round(ropeW))
        var ridge = c
        ridge.translateBy(x: 0, y: -1.3)
        ridge.stroke(path, with: .color(color(0xFFB37E, 0.55)), style: round(1.2))

        // The braid: short diagonal strands every 4.5 pt, turned with the rope.
        var braid = Path()
        var navy = Path()
        var x = 4.0
        var k = 0
        while x < w - 2 {
            let i = min(samples - 1, Int(floor(x / step)))
            let f = x / step - Double(i)
            let y = ys[i] + (ys[i + 1] - ys[i]) * f
            let a = atan2(ys[i + 1] - ys[i], step)
            let ca = cos(a + 1.05)
            let sa = sin(a + 1.05)
            let strand = line(x - ca * 2.4, y - sa * 2.4, x + ca * 2.4, y + sa * 2.4)
            if k % 5 == 2 { navy.addPath(strand) } else { braid.addPath(strand) }
            x += 4.5
            k += 1
        }
        c.stroke(braid, with: .color(color(0x8F3414, 0.6)), style: round(1.1))
        c.stroke(navy, with: .color(color(0x22324F, 0.7)), style: round(1.2))

        // The knot: three wraps of a barrel knot around the rope, lit from above.
        var knot = c
        knot.translateBy(x: kx, y: ky)
        knot.rotate(by: .radians(atan(slope)))
        var knotShadow = knot
        knotShadow.addFilter(.blur(radius: 3.5))
        knotShadow.fill(Path(ellipseIn: CGRect(x: -15, y: -4, width: 30, height: 16)), with: .color(color(0x020503, 0.55)))
        for k in -1...1 {
            var wrap = knot
            wrap.translateBy(x: Double(k) * 6.2, y: 0)
            wrap.rotate(by: .degrees(-24))
            let rect = Path(roundedRect: CGRect(x: -4, y: -9, width: 8, height: 18), cornerRadius: 4, style: .circular)
            wrap.fill(rect, with: .linearGradient(knotGradient, startPoint: CGPoint(x: -4, y: -9), endPoint: CGPoint(x: 4, y: 9)))
            wrap.stroke(rect, with: .color(color(0x4F1A08, 0.7)), lineWidth: 0.8)
            wrap.stroke(line(-1.6, -6.5, -1.6, 4), with: .color(color(0xFFD1AD, 0.5)), style: round(1))
        }

        if ripple >= 0 {
            let r = 12 + 22 * (1 - pow(1 - ripple, 3))
            let a = ripple < 0.15 ? ripple / 0.15 : 1 - (ripple - 0.15) / 0.85
            let ring = circle(rx, ry, r)
            c.stroke(ring, with: .color(color(0xF4EAD8, 0.45 * a)), lineWidth: 1.5)
            c.fill(ring, with: .color(color(0xF4EAD8, 0.1 * a)))
        }
    }
}
