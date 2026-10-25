import SwiftUI

/*
 The abacus, drawn in one canvas. Rendered once into bitmaps: the screen's backdrop, the walnut
 frame with its grain and bevels, the lacquered back panel, the brass rods with their rivets, one
 bead per category colour (a barrel-shaped lacquer cylinder: dark edges, a highlight band, a
 specular glint, darker rounded ends, contact shadows on the rod either side) and one soft drop
 shadow. Per frame: 50 shadows + 50 beads as translated images, and the scripted fingertip.
 */
enum AbacusExpenseCounterArt {
    /// Lacquer colours: vermilion, teal, ochre, pine, plum.
    static let beadColors: [UInt32] = [0xB8432E, 0x2E6F73, 0xC98F2A, 0x4D6B3F, 0x6A3B55]

    struct Images {
        let board: Image
        let beads: [Image]
        let shadow: Image
    }

    private static func rgb(_ hex: UInt32) -> (Double, Double, Double) {
        (Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
    }

    private static func shade(_ hex: UInt32, _ k: Double, _ a: Double = 1) -> Color {
        let c = rgb(hex)
        func f(_ v: Double) -> Double { k >= 0 ? v + (1 - v) * k : v * (1 + k) }
        return Color(.sRGB, red: f(c.0), green: f(c.1), blue: f(c.2), opacity: a)
    }

    private static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular)
    }

    private static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ stops: [(UInt32, Double, Double)]) -> GraphicsContext.Shading {
        .linearGradient(
            Gradient(stops: stops.map { .init(color: Color(hex: $0.0, opacity: $0.1), location: $0.2) }),
            startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1)
        )
    }

    private static func blurred(_ c: GraphicsContext, _ sigma: Double) -> GraphicsContext {
        var copy = c
        copy.addFilter(.blur(radius: sigma))
        return copy
    }

    private static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }

    @MainActor static func render(_ g: AbacusExpenseCounterGeo, scale: Double) -> Images {
        func image<V: View>(_ w: Double, _ h: Double, _ content: V) -> Image {
            let renderer = ImageRenderer(content: content.frame(width: w, height: h))
            renderer.scale = scale
            return renderer.uiImage.map { Image(uiImage: $0) } ?? Image(systemName: "circle")
        }
        let board = image(g.w, g.h, Canvas { c, _ in drawBoard(c, g) })
        let beads = beadColors.map { color in
            image(g.bw * 2, g.bh * 2, Canvas { c, _ in
                var c = c
                c.translateBy(x: g.bw, y: g.bh)
                drawBead(c, g, color)
            })
        }
        let shadow = image(g.bw * 3, g.bh * 3, Canvas { c, _ in
            blurred(c, 4).fill(rr(g.bw * 1.5 - g.bw / 2 + 1, g.bh * 1.5 - g.bh / 2 + 6, g.bw - 2, g.bh, 9), with: .color(.black.opacity(0.5)))
        })
        return Images(board: board, beads: beads, shadow: shadow)
    }

    /// Backdrop, frame, back panel, rods: everything that never moves.
    private static func drawBoard(_ c: GraphicsContext, _ g: AbacusExpenseCounterGeo) {
        let all = Path(CGRect(x: 0, y: 0, width: g.w, height: g.h))
        c.fill(all, with: linear(0, 0, 0, g.h, [(0x1D1712, 1, 0), (0x16110D, 1, 0.5), (0x100C09, 1, 1)]))
        c.fill(all, with: .radialGradient(
            Gradient(colors: [Color(hex: 0xD9A56A, opacity: 0.12), Color(hex: 0xD9A56A, opacity: 0)]),
            center: CGPoint(x: g.w * 0.5, y: g.boardY + g.boardH * 0.4), startRadius: 0, endRadius: g.w * 0.95
        ))

        let x = g.boardX, y = g.boardY, w = g.boardW, h = g.boardH, f = g.frame
        // Two shadows under the abacus.
        blurred(c, 18).fill(rr(x + 8, y + 20, w - 16, h - 6, 20), with: .color(.black.opacity(0.55)))
        blurred(c, 3).fill(rr(x + 1, y + 3, w - 2, h, 18), with: .color(.black.opacity(0.5)))
        // Walnut frame with grain running along it.
        let outer = rr(x, y, w, h, 18)
        c.fill(outer, with: linear(0, y, 0, y + h, [(0x7A4B2A, 1, 0), (0x6A3F22, 1, 0.3), (0x5A341C, 1, 0.7), (0x4A2A16, 1, 1)]))
        var grainCtx = c
        grainCtx.clip(to: outer)
        for k in 0..<46 {
            let kk = Double(k)
            let yy = y + (kk / 46) * h + hash(kk) * 4
            var path = Path()
            var xx = x - 4
            while xx <= x + w + 4 {
                let py = yy + sin(xx * 0.02 + hash(kk + 9) * 6) * 2.2 + sin(xx * 0.07 + kk) * 0.6
                if xx == x - 4 { path.move(to: CGPoint(x: xx, y: py)) } else { path.addLine(to: CGPoint(x: xx, y: py)) }
                xx += 10
            }
            let color = k % 4 == 0 ? Color(hex: 0xC28A5A, opacity: 0.16) : Color(hex: 0x2A160A, opacity: 0.28)
            grainCtx.stroke(path, with: .color(color), lineWidth: 0.5 + hash(kk + 3) * 1.1)
        }
        // Fine grain noise (Skia's fractal noise in RN), as two batches of tiny specks.
        var light = Path()
        var dark = Path()
        let specks = Int(w * h / 14)
        for i in 0..<specks {
            let ii = Double(i)
            let px = x + hash(ii * 1.37 + 0.5) * w
            let py = y + hash(ii * 2.11 + 7.3) * h
            let r = CGRect(x: px, y: py, width: 1.1, height: 1.1)
            if hash(ii * 0.73 + 3.1) > 0.5 { light.addRect(r) } else { dark.addRect(r) }
        }
        grainCtx.fill(light, with: .color(.white.opacity(0.07)))
        grainCtx.fill(dark, with: .color(.black.opacity(0.09)))
        c.stroke(rr(x + 0.6, y + 0.6, w - 1.2, h - 1.2, 17.4), with: .color(Color(hex: 0xF0C18E, opacity: 0.28)), lineWidth: 1.2)

        // The back panel: dark lacquer, sunk into the frame (shadow on its top edge, light at the bottom).
        let ix = x + f, iy = y + f, iw = w - 2 * f, ih = h - 2 * f
        let panel = rr(ix, iy, iw, ih, 8)
        c.fill(panel, with: linear(0, iy, 0, iy + ih, [(0x120C09, 1, 0), (0x1D1410, 1, 0.3), (0x221812, 1, 1)]))
        var inside = c
        inside.clip(to: panel)
        blurred(inside, 5).fill(Path(CGRect(x: ix, y: iy - 6, width: iw, height: 10)), with: .color(.black.opacity(0.7)))
        blurred(inside, 4).fill(Path(CGRect(x: ix - 6, y: iy, width: 8, height: ih)), with: .color(.black.opacity(0.4)))
        // Dividers between the rods' bands.
        var dividers = Path()
        for r in 1..<AbacusExpenseCounterMotion.rods {
            dividers.move(to: CGPoint(x: ix + 10, y: iy + Double(r) * g.band))
            dividers.addLine(to: CGPoint(x: ix + iw - 10, y: iy + Double(r) * g.band))
        }
        inside.stroke(dividers, with: .color(Color(hex: 0xF0C18E, opacity: 0.05)), lineWidth: 1)
        var lip = Path()
        lip.move(to: CGPoint(x: ix + 8, y: iy + ih + 0.5))
        lip.addLine(to: CGPoint(x: ix + iw - 8, y: iy + ih + 0.5))
        c.stroke(lip, with: .color(Color(hex: 0xF0C18E, opacity: 0.22)), lineWidth: 1)

        // Brass rods with a rivet at each end.
        for ry in g.rodY {
            blurred(c, 2).fill(rr(ix - 4, ry - 1.6 + 3, iw + 8, 3.2, 1.6), with: .color(.black.opacity(0.5)))
            c.fill(rr(ix - 4, ry - 1.7, iw + 8, 3.4, 1.7), with: linear(0, ry - 1.7, 0, ry + 1.7, [(0xFBE3A8, 1, 0), (0xC79A4C, 1, 0.45), (0x7A5A24, 1, 1)]))
            for rx in [x + f / 2, x + w - f / 2] {
                blurred(c, 1).fill(Path(ellipseIn: CGRect(x: rx - 3.6, y: ry + 0.6 - 3.6, width: 7.2, height: 7.2)), with: .color(.black.opacity(0.5)))
                c.fill(Path(ellipseIn: CGRect(x: rx - 3.4, y: ry - 3.4, width: 6.8, height: 6.8)), with: .radialGradient(
                    Gradient(stops: [
                        .init(color: Color(hex: 0xFFF1C8), location: 0),
                        .init(color: Color(hex: 0xC79A4C), location: 0.5),
                        .init(color: Color(hex: 0x5E4219), location: 1),
                    ]),
                    center: CGPoint(x: rx - 1.2, y: ry - 1.4), startRadius: 0, endRadius: 5
                ))
            }
        }
    }

    /// One lacquered bead centred on the origin.
    private static func drawBead(_ c: GraphicsContext, _ g: AbacusExpenseCounterGeo, _ color: UInt32) {
        let bw = g.bw, bh = g.bh
        let x = -bw / 2 + 0.4
        let w = bw - 0.8
        let y = -bh / 2
        let r = min(w, bh) * 0.42
        // Contact shadows on the rod, either side.
        let contact = blurred(c, 1.4)
        contact.fill(Path(ellipseIn: CGRect(x: -bw / 2 - 2.5, y: -2.5, width: 5, height: 6)), with: .color(.black.opacity(0.45)))
        contact.fill(Path(ellipseIn: CGRect(x: bw / 2 - 2.5, y: -2.5, width: 5, height: 6)), with: .color(.black.opacity(0.45)))
        let body = rr(x, y, w, bh, r)
        c.fill(body, with: .linearGradient(
            Gradient(stops: [
                .init(color: shade(color, -0.55), location: 0),
                .init(color: shade(color, -0.05), location: 0.14),
                .init(color: shade(color, 0.32), location: 0.3),
                .init(color: shade(color, 0.05), location: 0.5),
                .init(color: shade(color, -0.25), location: 0.8),
                .init(color: shade(color, -0.62), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: y), endPoint: CGPoint(x: 0, y: y + bh)
        ))
        var inner = c
        inner.clip(to: body)
        // Rounded ends: the barrel turns away from the light at both sides.
        inner.fill(Path(CGRect(x: x, y: y, width: w, height: bh)), with: linear(x, 0, x + w, 0, [(0, 0.4, 0), (0, 0, 0.28), (0, 0, 0.7), (0, 0.45, 1)]))
        // The highlight band and a glint, toward the light at the upper left.
        blurred(inner, 1.5).fill(Path(CGRect(x: x, y: y + bh * 0.2, width: w, height: bh * 0.12)), with: .color(.white.opacity(0.22)))
        blurred(inner, 0.8).fill(Path(ellipseIn: CGRect(x: x + w * 0.2, y: y + bh * 0.17, width: w * 0.28, height: bh * 0.12)), with: .color(.white.opacity(0.75)))
        // Bounce light from the panel along the bottom.
        blurred(inner, 1.2).fill(Path(CGRect(x: x, y: y + bh * 0.84, width: w, height: bh * 0.08)), with: .color(shade(color, 0.2, 0.35)))
        c.stroke(rr(x + 0.4, y + 0.4, w - 0.8, bh - 0.8, r - 0.4), with: .color(.black.opacity(0.35)), lineWidth: 0.8)
    }

    /// One frame: the board, then every bead's shadow, then every bead, then the fingertip.
    static func draw(_ c: GraphicsContext, _ g: AbacusExpenseCounterGeo, _ art: Images, _ x: [Double], _ finger: (Double, Double, Double)) {
        c.draw(art.board, in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
        let beads = AbacusExpenseCounterMotion.beads
        guard x.count == AbacusExpenseCounterMotion.rods * beads else { return }
        let shadow = c.resolve(art.shadow)
        for r in 0..<AbacusExpenseCounterMotion.rods {
            for j in 0..<beads {
                c.draw(shadow, in: CGRect(x: x[r * beads + j] - g.bw * 1.5, y: g.rodY[r] - g.bh * 1.5, width: g.bw * 3, height: g.bh * 3))
            }
        }
        for r in 0..<AbacusExpenseCounterMotion.rods {
            let bead = c.resolve(art.beads[r])
            for j in 0..<beads {
                c.draw(bead, in: CGRect(x: x[r * beads + j] - g.bw, y: g.rodY[r] - g.bh, width: g.bw * 2, height: g.bh * 2))
            }
        }
        let a = finger.2
        if a > 0.01 {
            let rad = 20 * (1.1 - 0.1 * a)
            let p = CGPoint(x: finger.0, y: finger.1)
            func circle(_ r: Double) -> Path { Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)) }
            blurred(c, 8).fill(circle(rad + 6), with: .color(Color(hex: 0xFFE2BF, opacity: 0.1 * a)))
            c.fill(circle(rad), with: .color(Color(hex: 0xFFF1E0, opacity: 0.16 * a)))
            c.stroke(circle(rad), with: .color(Color(hex: 0xFFF1E0, opacity: 0.55 * a)), lineWidth: 1.4)
        }
    }
}
