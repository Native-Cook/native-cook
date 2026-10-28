import SwiftUI

/*
 Omelette Topping Builder — the food, drawn in code.

 Shelf ingredients are drawn in a unit box (-0.5 … 0.5, y down) that the caller scales: a red bell
 pepper with three lobes and a curled stem, a bunch of four spinach leaves, two wavy bacon strips,
 three cherry tomatoes, a button mushroom. Their light comes from the top left; soft spots are radial
 gradients (no blur), so they scale cleanly.

 Pieces are drawn in their own points around (0, 0), already rotated by the caller; `lx, ly` is the
 direction of the light in the piece's frame and `sx, sy` the offset of its shadow, so the cubes,
 leaves and strips keep one light whatever their angle.

 The omelette is drawn in its own frame: a half disc of radius R with its fold edge on y = 0 and the
 bulge towards +y.
*/
enum OmeletteToppingBuilderArt {
    typealias M = OmeletteToppingBuilderMotion

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((hex >> 16) & 0xFF) / 255,
              green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255,
              opacity: a)
    }

    static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    static func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }

    static func linear(_ stops: [(UInt32, Double, Double)], _ x0: Double, _ y0: Double,
                       _ x1: Double, _ y1: Double) -> GraphicsContext.Shading {
        .linearGradient(Gradient(stops: stops.map { .init(color: color($0.0, $0.1), location: $0.2) }),
                        startPoint: pt(x0, y0), endPoint: pt(x1, y1))
    }

    static func radial(_ stops: [(UInt32, Double, Double)], _ x: Double, _ y: Double, _ r: Double) -> GraphicsContext.Shading {
        .radialGradient(Gradient(stops: stops.map { .init(color: color($0.0, $0.1), location: $0.2) }),
                        center: pt(x, y), startRadius: 0, endRadius: r)
    }

    /// A soft elliptical spot fading from `alpha` at its centre to 0 at its edge.
    static func blob(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ rx: Double, _ ry: Double,
                     _ hex: UInt32, _ alpha: Double) {
        var l = c
        l.translateBy(x: x, y: y)
        l.scaleBy(x: 1, y: ry / rx)
        l.fill(circle(0, 0, rx), with: radial([(hex, alpha, 0), (hex, 0, 1)], 0, 0, rx))
    }

    /// A soft ring shadow under a disc: `alpha` up to radius r0, fading to 0 at r1.
    static func ringShadow(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ r0: Double, _ r1: Double, _ alpha: Double) {
        c.fill(circle(x, y, r1), with: radial([(0x000000, alpha, 0), (0x000000, alpha, r0 / r1), (0x000000, 0, 1)], x, y, r1))
    }

    /// A closed smooth curve through the points (Catmull-Rom as cubic Béziers).
    static func smoothClosed(_ p: [(Double, Double)]) -> Path {
        var path = Path()
        let n = p.count
        path.move(to: pt(p[0].0, p[0].1))
        for i in 0..<n {
            let p0 = p[(i - 1 + n) % n], p1 = p[i], p2 = p[(i + 1) % n], p3 = p[(i + 2) % n]
            path.addCurve(to: pt(p2.0, p2.1),
                          control1: pt(p1.0 + (p2.0 - p0.0) / 6, p1.1 + (p2.1 - p0.1) / 6),
                          control2: pt(p2.0 - (p3.0 - p1.0) / 6, p2.1 - (p3.1 - p1.1) / 6))
        }
        path.closeSubpath()
        return path
    }

    /// A band following a wavy centre line y = amp·sin(2πx/period + phase), between offsets a and b.
    static func band(_ x0: Double, _ x1: Double, _ a: Double, _ b: Double, _ amp: Double,
                     _ period: Double, _ phase: Double, _ n: Int) -> Path {
        var path = Path()
        func cy(_ x: Double) -> Double { amp * sin(2 * Double.pi * x / period + phase) }
        for i in 0...n {
            let x = x0 + (x1 - x0) * Double(i) / Double(n)
            let p = pt(x, cy(x) + a)
            if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        for i in stride(from: n, through: 0, by: -1) {
            let x = x0 + (x1 - x0) * Double(i) / Double(n)
            path.addLine(to: pt(x, cy(x) + b))
        }
        path.closeSubpath()
        return path
    }

    // MARK: Shelf ingredients (unit box)

    static func ingredient(_ c: inout GraphicsContext, _ kind: Int, ground: Bool) {
        if ground { blob(&c, 0.02, 0.44, 0.32, 0.065, 0x000000, 0.16) }
        switch kind {
        case 0: pepper(&c)
        case 1: spinach(&c)
        case 2: bacon(&c)
        case 3: tomatoes(&c)
        default: mushroom(&c)
        }
    }

    static func pepper(_ c: inout GraphicsContext) {
        var body = Path()
        body.move(to: pt(-0.32, -0.16))
        body.addCurve(to: pt(0, -0.27), control1: pt(-0.32, -0.30), control2: pt(-0.14, -0.33))
        body.addCurve(to: pt(0.32, -0.16), control1: pt(0.14, -0.33), control2: pt(0.32, -0.30))
        body.addCurve(to: pt(0.24, 0.33), control1: pt(0.36, 0.02), control2: pt(0.34, 0.22))
        body.addCurve(to: pt(0.06, 0.36), control1: pt(0.20, 0.39), control2: pt(0.10, 0.41))
        body.addCurve(to: pt(-0.06, 0.36), control1: pt(0.03, 0.42), control2: pt(-0.03, 0.42))
        body.addCurve(to: pt(-0.24, 0.33), control1: pt(-0.10, 0.41), control2: pt(-0.20, 0.39))
        body.addCurve(to: pt(-0.32, -0.16), control1: pt(-0.34, 0.22), control2: pt(-0.36, 0.02))
        body.closeSubpath()
        c.fill(body, with: radial([(0xFF6A48, 1, 0), (0xE42C1F, 1, 0.42), (0xB8171A, 1, 0.78), (0x7E0E12, 1, 1)],
                                  -0.12, -0.12, 0.62))
        var l = c
        l.clip(to: body)
        // Lobe creases, the shoulder's shade and the glossy highlights.
        blob(&l, -0.085, 0.14, 0.04, 0.24, 0x6E0A0E, 0.42)
        blob(&l, 0.10, 0.14, 0.04, 0.24, 0x6E0A0E, 0.36)
        blob(&l, 0.0, -0.25, 0.20, 0.06, 0x5C080B, 0.45)
        blob(&l, 0.26, 0.10, 0.08, 0.26, 0x5C080B, 0.35)
        blob(&l, -0.19, -0.04, 0.05, 0.15, 0xFFFFFF, 0.62)
        blob(&l, -0.04, 0.10, 0.025, 0.12, 0xFFFFFF, 0.30)
        blob(&l, 0.17, -0.10, 0.03, 0.08, 0xFFFFFF, 0.38)
        // Calyx and curled stem.
        blob(&c, 0, -0.27, 0.13, 0.05, 0x2B5A1C, 0.9)
        var stem = Path()
        stem.move(to: pt(-0.035, -0.26))
        stem.addCurve(to: pt(0.05, -0.43), control1: pt(-0.04, -0.35), control2: pt(-0.005, -0.41))
        stem.addLine(to: pt(0.085, -0.40))
        stem.addCurve(to: pt(0.035, -0.26), control1: pt(0.04, -0.37), control2: pt(0.035, -0.33))
        stem.closeSubpath()
        c.fill(stem, with: linear([(0x3A6E25, 1, 0), (0x74B24A, 1, 0.45), (0x3E7A2A, 1, 1)], -0.04, 0, 0.06, 0))
        c.fill(Path(ellipseIn: CGRect(x: 0.045, y: -0.437, width: 0.045, height: 0.035)), with: .color(color(0xB5D98A)))
    }

    static func leaf(_ L: Double, _ W: Double) -> Path {
        var p = Path()
        p.move(to: pt(0, 0))
        p.addCurve(to: pt(0, -L), control1: pt(W * 0.9, -L * 0.15), control2: pt(W * 1.05, -L * 0.7))
        p.addCurve(to: pt(0, 0), control1: pt(-W * 1.05, -L * 0.7), control2: pt(-W * 0.9, -L * 0.15))
        p.closeSubpath()
        return p
    }

    static func spinach(_ c: inout GraphicsContext) {
        let leaves: [(Double, Double, Double)] = [(-36, 0.46, 0.19), (38, 0.45, 0.18), (-12, 0.62, 0.23), (15, 0.56, 0.21)]
        for (deg, L, W) in leaves {
            var l = c
            l.translateBy(x: 0, y: 0.40)
            l.rotate(by: .degrees(deg))
            var stalk = Path()
            stalk.move(to: pt(0, 0))
            stalk.addLine(to: pt(0, -0.16))
            l.stroke(stalk, with: .color(color(0x8DBA5E)), style: StrokeStyle(lineWidth: 0.03, lineCap: .round))
            l.translateBy(x: 0, y: -0.12)
            let blade = leaf(L, W)
            l.fill(blade, with: linear([(0x3E8E3B, 1, 0), (0x2A7230, 1, 0.5), (0x1C5520, 1, 1)], 0, 0, 0, -L))
            var half = l
            half.clip(to: blade)
            half.fill(Path(CGRect(x: 0, y: -L, width: W * 1.2, height: L)), with: .color(color(0x0A2E0E, 0.18)))
            blob(&half, -W * 0.38, -L * 0.55, W * 0.24, L * 0.2, 0xFFFFFF, 0.16)
            var vein = Path()
            vein.move(to: pt(0, -0.02))
            vein.addQuadCurve(to: pt(0, -L * 0.92), control: pt(W * 0.08, -L * 0.5))
            for f in [0.28, 0.48, 0.68] {
                vein.move(to: pt(0, -L * f))
                vein.addLine(to: pt(W * 0.62, -L * (f + 0.13)))
                vein.move(to: pt(0, -L * f))
                vein.addLine(to: pt(-W * 0.62, -L * (f + 0.13)))
            }
            half.stroke(vein, with: .color(color(0x9CCB6E, 0.55)), lineWidth: 0.011)
            l.stroke(blade, with: .color(color(0x123E16, 0.35)), lineWidth: 0.01)
        }
    }

    static func baconStrip(_ c: inout GraphicsContext, len: Double, w: Double, phase: Double) {
        let amp = 0.032, period = 0.34, h = len / 2
        let meat = band(-h, h, -w / 2, w / 2, amp, period, phase, 24)
        c.fill(meat, with: linear([(0xC84E3C, 1, 0), (0xA2352A, 1, 0.5), (0x7A2119, 1, 1)], 0, -w / 2, 0, w / 2))
        var l = c
        l.clip(to: meat)
        l.fill(band(-h, h, -w * 0.12, w * 0.12, amp, period, phase, 24),
               with: linear([(0xF8E2CF, 1, 0), (0xEBC3A6, 1, 1)], 0, -w * 0.12, 0, w * 0.12))
        l.fill(band(-h, h, w * 0.30, w * 0.38, amp, period, phase, 24), with: .color(color(0xF1D0BA, 0.8)))
        l.fill(band(-h, h, -w * 0.42, -w * 0.36, amp, period, phase, 24), with: .color(color(0xF1D0BA, 0.45)))
        blob(&l, -h * 0.55, 0.0, 0.07, 0.05, 0x4A120C, 0.35)
        blob(&l, h * 0.45, w * 0.2, 0.08, 0.05, 0x4A120C, 0.3)
        l.fill(band(-h, h, -w / 2, -w * 0.42, amp, period, phase, 24), with: .color(color(0xFFFFFF, 0.22)))
        c.stroke(meat, with: .color(color(0x5E1A12, 0.45)), lineWidth: 0.012)
    }

    static func bacon(_ c: inout GraphicsContext) {
        var a = c
        a.translateBy(x: -0.08, y: 0.0)
        a.rotate(by: .degrees(-66))
        baconStrip(&a, len: 0.84, w: 0.17, phase: 0.4)
        var b = c
        b.translateBy(x: 0.09, y: 0.03)
        b.rotate(by: .degrees(-58))
        baconStrip(&b, len: 0.80, w: 0.16, phase: 2.2)
    }

    static func tomato(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ r: Double) {
        c.fill(circle(x, y, r), with: radial([(0xFF8A68, 1, 0), (0xEA3B2B, 1, 0.38), (0xB91D17, 1, 0.82), (0x86110F, 1, 1)],
                                             x - 0.35 * r, y - 0.38 * r, 1.35 * r))
        blob(&c, x - 0.34 * r, y - 0.38 * r, 0.24 * r, 0.15 * r, 0xFFFFFF, 0.8)
        blob(&c, x + 0.42 * r, y + 0.30 * r, 0.12 * r, 0.22 * r, 0xFF9A7A, 0.35)
    }

    static func tomatoes(_ c: inout GraphicsContext) {
        tomato(&c, 0.0, -0.11, 0.20)
        tomato(&c, -0.17, 0.12, 0.19)
        tomato(&c, 0.18, 0.13, 0.18)
        // Calyx on the back tomato, a stem scar on the front ones.
        var star = Path()
        for i in 0..<10 {
            let a = Double(i) * Double.pi / 5 - Double.pi / 2
            let r = i % 2 == 0 ? 0.085 : 0.028
            let p = pt(0.0 + r * cos(a), -0.27 + r * sin(a) * 0.7)
            if i == 0 { star.move(to: p) } else { star.addLine(to: p) }
        }
        star.closeSubpath()
        c.fill(star, with: .color(color(0x3E7B2B)))
        var stem = Path()
        stem.move(to: pt(0.0, -0.27))
        stem.addQuadCurve(to: pt(0.04, -0.36), control: pt(0.0, -0.33))
        c.stroke(stem, with: .color(color(0x4C8A33)), style: StrokeStyle(lineWidth: 0.022, lineCap: .round))
        blob(&c, -0.17, -0.05, 0.03, 0.02, 0x5E2A10, 0.5)
        blob(&c, 0.18, -0.03, 0.03, 0.02, 0x5E2A10, 0.5)
    }

    static func mushroom(_ c: inout GraphicsContext) {
        var stem = Path()
        stem.move(to: pt(-0.12, 0.02))
        stem.addCurve(to: pt(-0.12, 0.36), control1: pt(-0.13, 0.18), control2: pt(-0.16, 0.30))
        stem.addCurve(to: pt(0.12, 0.36), control1: pt(-0.04, 0.40), control2: pt(0.04, 0.40))
        stem.addCurve(to: pt(0.12, 0.02), control1: pt(0.16, 0.30), control2: pt(0.13, 0.18))
        stem.closeSubpath()
        c.fill(stem, with: linear([(0xCDBDA8, 1, 0), (0xF7F0E7, 1, 0.45), (0xD4C4AF, 1, 1)], -0.13, 0, 0.13, 0))
        blob(&c, 0, 0.36, 0.11, 0.03, 0xA88C6E, 0.4)
        // Gills under the cap.
        c.fill(Path(ellipseIn: CGRect(x: -0.34, y: -0.03, width: 0.68, height: 0.13)),
               with: linear([(0x6E543F, 1, 0), (0x9C8066, 1, 1)], 0, -0.03, 0, 0.10))
        var gills = Path()
        for i in 0..<9 {
            let x = -0.28 + 0.07 * Double(i)
            gills.move(to: pt(x, 0.035))
            gills.addLine(to: pt(x * 0.35, 0.03))
        }
        c.stroke(gills, with: .color(color(0x4E3A2A, 0.45)), lineWidth: 0.008)
        var cap = Path()
        cap.move(to: pt(-0.39, 0.03))
        cap.addCurve(to: pt(0.39, 0.03), control1: pt(-0.40, -0.34), control2: pt(0.40, -0.34))
        cap.addCurve(to: pt(-0.39, 0.03), control1: pt(0.30, 0.07), control2: pt(-0.30, 0.07))
        cap.closeSubpath()
        c.fill(cap, with: radial([(0xFBF5EE, 1, 0), (0xE8DACA, 1, 0.5), (0xC4AA8B, 1, 0.9), (0xA98D6D, 1, 1)],
                                 -0.10, -0.18, 0.52))
        var l = c
        l.clip(to: cap)
        blob(&l, -0.15, -0.16, 0.10, 0.05, 0xFFFFFF, 0.7)
        for (x, y, r) in [(0.10, -0.15, 0.018), (0.20, -0.06, 0.014), (-0.02, -0.20, 0.012), (0.24, -0.14, 0.01)] {
            blob(&l, x, y, r, r, 0x8A6B4E, 0.45)
        }
        l.stroke(cap.offsetBy(dx: 0, dy: -0.01), with: .color(color(0x8E7356, 0.25)), lineWidth: 0.012)
    }

    // MARK: Pieces (points)

    /// One piece of kind k at the origin, with the light along (lx, ly) and its shadow at (sx, sy).
    static func piece(_ c: inout GraphicsContext, _ k: Int, _ p: OmeletteToppingBuilderPiece,
                      lx: Double, ly: Double, sx: Double, sy: Double, shadow: Double) {
        switch k {
        case 0:
            let r = Path(roundedRect: CGRect(x: -4.6, y: -4.1, width: 9.2, height: 8.2), cornerRadius: 2.3)
            c.fill(r.offsetBy(dx: sx, dy: sy), with: .color(color(0x8A3A00, 0.30 * shadow)))
            c.fill(r, with: .color(color(0xA3141A)))
            c.fill(Path(roundedRect: CGRect(x: -4.1 + lx * 0.9, y: -3.6 + ly * 0.9, width: 8.0, height: 7.0), cornerRadius: 2.0),
                   with: .color(color(0xE3321F)))
            blob(&c, lx * 2.2, ly * 2.2, 2.2, 1.4, 0xFFFFFF, 0.55)
        case 1:
            let blade = Self.pieceLeaf()
            c.fill(blade.offsetBy(dx: sx, dy: sy), with: .color(color(0x6A5000, 0.26 * shadow)))
            c.fill(blade, with: .color(color(0x2B732D)))
            var l = c
            l.clip(to: blade)
            blob(&l, lx * 1.6, ly * 1.6, 4.6, 2.2, 0x6DB24E, 0.65)
            var vein = Path()
            vein.move(to: pt(-6.2, 0))
            vein.addQuadCurve(to: pt(6.4, 0), control: pt(0, 0.8))
            l.stroke(vein, with: .color(color(0x9CCB6E, 0.75)), lineWidth: 0.7)
            c.stroke(blade, with: .color(color(0x174A1A, 0.45)), lineWidth: 0.6)
        case 2:
            let meat = band(-11, 11, -3, 3, 1.1, 11, p.phase, 10)
            c.fill(meat.offsetBy(dx: sx, dy: sy), with: .color(color(0x6A3000, 0.28 * shadow)))
            c.fill(meat, with: .color(color(0xA8372B)))
            var l = c
            l.clip(to: meat)
            l.fill(band(-11, 11, -0.9, 0.7, 1.1, 11, p.phase, 10), with: .color(color(0xF3D3BC)))
            l.fill(band(-11, 11, 1.7, 2.2, 1.1, 11, p.phase, 10), with: .color(color(0xEFC9AE, 0.8)))
            blob(&l, lx * 2, ly * 2, 6, 2, 0xFFFFFF, 0.22)
            c.stroke(meat, with: .color(color(0x5A170F, 0.5)), lineWidth: 0.7)
        case 3:
            c.fill(circle(sx, sy, 5.2), with: .color(color(0x8A3A00, 0.28 * shadow)))
            c.fill(circle(0, 0, 5.2), with: .color(color(0xC4261C)))
            c.fill(circle(0, 0, 4.2), with: .color(color(0xE8452F)))
            for i in 0..<3 {
                let a = Double(i) * 2 * Double.pi / 3 + 0.4
                blob(&c, 2.3 * cos(a), 2.3 * sin(a), 1.5, 1.5, 0xF9C066, 0.95)
            }
            c.fill(circle(0, 0, 1.1), with: .color(color(0xF26A4A)))
            blob(&c, lx * 2.6, ly * 2.6, 1.6, 1.0, 0xFFFFFF, 0.6)
        default:
            var s = Path()
            s.move(to: pt(-6, 0.5))
            s.addCurve(to: pt(6, 0.5), control1: pt(-6.2, -5.6), control2: pt(6.2, -5.6))
            s.addLine(to: pt(1.9, 0.9))
            s.addLine(to: pt(2.2, 4.6))
            s.addQuadCurve(to: pt(-2.2, 4.6), control: pt(0, 5.6))
            s.addLine(to: pt(-1.9, 0.9))
            s.closeSubpath()
            c.fill(s.offsetBy(dx: sx, dy: sy), with: .color(color(0x6A4000, 0.26 * shadow)))
            c.fill(s, with: .color(color(0xEDE3D4)))
            var l = c
            l.clip(to: s)
            var gill = Path()
            gill.move(to: pt(-5.2, 0.2))
            gill.addQuadCurve(to: pt(5.2, 0.2), control: pt(0, -2.4))
            l.stroke(gill, with: .color(color(0xA58665, 0.6)), lineWidth: 1.1)
            blob(&l, lx * 2, ly * 2, 3, 1.6, 0xFFFFFF, 0.6)
            c.stroke(s, with: .color(color(0x96765A, 0.85)), lineWidth: 0.8)
        }
    }

    static func pieceLeaf() -> Path {
        var p = Path()
        p.move(to: pt(-7, 0))
        p.addCurve(to: pt(7, 0), control1: pt(-3, -4.6), control2: pt(3, -4.8))
        p.addCurve(to: pt(-7, 0), control1: pt(3, 4.3), control2: pt(-3, 4.5))
        p.closeSubpath()
        return p
    }

    // MARK: Omelette (its own frame)

    static let omeletteOutline: [(Double, Double)] = {
        let R = M.R
        var p: [(Double, Double)] = []
        for i in 0..<9 {
            let x = -R * 0.96 + R * 1.92 * Double(i) / 8
            p.append((x, -3.5 * (1 - (x / R) * (x / R))))
        }
        for i in 0...20 {
            let th = Double.pi * (0.025 + 0.95 * Double(i) / 20)
            let r = R * (1 + 0.012 * sin(3 * th + 0.5) + 0.008 * sin(7 * th + 1.3))
            p.append((r * cos(th), r * sin(th)))
        }
        return p
    }()

    static let omelettePath: Path = smoothClosed(omeletteOutline)

    /// Seeded speckles of the omelette's surface: x, y, radius, kind (0 light, 1 golden, 2 brown).
    static let speckles: [(Double, Double, Double, Int)] = {
        var out: [(Double, Double, Double, Int)] = []
        for i in 0..<72 {
            let th = Double.pi * (0.03 + 0.94 * M.hash(70000 + i * 4))
            let rho = (M.R - 6) * M.hash(70001 + i * 4).squareRoot()
            let k = Int(M.hash(70002 + i * 4) * 3)
            let r = k == 2 ? 1.0 + 0.8 * M.hash(70003 + i * 4) : 2.2 + 3.6 * M.hash(70003 + i * 4)
            out.append((cos(th) * rho, max(3, sin(th) * rho), r, k))
        }
        return out
    }()

    static func omelette(_ c: inout GraphicsContext, shadowLocal: (Double, Double), contactLocal: (Double, Double)) {
        let R = M.R
        let path = omelettePath
        var sh = c
        sh.addFilter(.blur(radius: 8))
        sh.fill(path.offsetBy(dx: shadowLocal.0, dy: shadowLocal.1), with: .color(color(0x4A3200, 0.20)))
        var ct = c
        ct.addFilter(.blur(radius: 2))
        ct.fill(path.offsetBy(dx: contactLocal.0, dy: contactLocal.1), with: .color(color(0x3A2600, 0.22)))

        c.fill(path, with: radial([(0xFFDF5C, 1, 0), (0xF9CA36, 1, 0.5), (0xF0B328, 1, 0.82), (0xE09A1A, 1, 1)], 0, R * 0.3, R * 1.05))
        var l = c
        l.clip(to: path)
        // Puffs, speckles, the browned rim and the sheen along the fold.
        blob(&l, -34, 44, 36, 20, 0xFFF2B8, 0.42)
        blob(&l, 34, 66, 26, 14, 0xFFF2B8, 0.36)
        blob(&l, -66, 22, 20, 11, 0xFFF2B8, 0.32)
        blob(&l, 8, 92, 22, 9, 0xD8901C, 0.28)
        for s in speckles {
            switch s.3 {
            case 0: blob(&l, s.0, s.1, s.2, s.2 * 0.8, 0xFFF4C2, 0.6)
            case 1: blob(&l, s.0, s.1, s.2, s.2 * 0.75, 0xE0A038, 0.42)
            default: l.fill(circle(s.0, s.1, s.2), with: .color(color(0xB97A1C, 0.32)))
            }
        }
        l.fill(circle(0, 0, R * 1.03), with: radial([(0xC97E12, 0, 0), (0xC97E12, 0, 0.78), (0xB86E0E, 0.5, 1)], 0, 0, R * 1.03))
        l.fill(Path(CGRect(x: -R, y: -8, width: 2 * R, height: 34)),
               with: linear([(0xFFF3B0, 0.85, 0), (0xFFE58A, 0, 1)], 0, -4, 0, 26))
        var fold = Path()
        let o = omeletteOutline
        fold.move(to: pt(o[0].0, o[0].1))
        for i in 1..<9 { fold.addLine(to: pt(o[i].0, o[i].1)) }
        l.stroke(fold.offsetBy(dx: 0, dy: 1.2), with: .color(color(0xFFFFFF, 0.45)), lineWidth: 1.4)
    }

    // MARK: Plate (points, centred at x, y)

    static func plate(_ c: inout GraphicsContext, _ x: Double, _ y: Double) {
        // The placemat halo.
        ringShadow(&c, x, y + 8, 160, 188, 0.07)
        c.fill(circle(x, y, 168), with: radial([(0xF8F8F7, 1, 0), (0xEFEFED, 1, 1)], x - 40, y - 50, 230))
        c.stroke(circle(x, y, 167.5), with: linear([(0xFFFFFF, 0.9, 0), (0xFFFFFF, 0, 0.6)], x - 120, y - 120, x + 60, y + 60), lineWidth: 1)
        // The plate: two shadows, the rim, the well.
        ringShadow(&c, x, y + 12, 136, 178, 0.12)
        ringShadow(&c, x, y + 2, 142, 150, 0.16)
        c.fill(circle(x, y, 144), with: linear([(0xFFFFFF, 1, 0), (0xF6F4F1, 1, 0.5), (0xE4E0DA, 1, 1)], x - 100, y - 100, x + 100, y + 100))
        c.stroke(circle(x, y, 143.3), with: linear([(0xFFFFFF, 1, 0), (0xFFFFFF, 0, 0.55)], x - 100, y - 100, x + 60, y + 60), lineWidth: 1.2)
        c.fill(circle(x, y, 112), with: radial([(0xFDFCFA, 1, 0), (0xF2EFEA, 1, 1)], x - 20, y - 24, 130))
        c.fill(circle(x, y, 112), with: radial([(0x000000, 0, 0), (0x000000, 0, 0.9), (0x5A4A30, 0.08, 1)], x, y, 112))
        c.stroke(circle(x, y, 112.6), with: linear([(0xFFFFFF, 0, 0.4), (0xFFFFFF, 0.95, 1)], x - 80, y - 80, x + 80, y + 80), lineWidth: 1.2)
    }
}
