import SwiftUI

/// The photo being edited (a lake at dusk, drawn in code) and the panel's line icons.
enum WaveScrubSliderArt {
    static func rgb(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: a)
    }

    /// A ridge line: a few sines over the width; y grows downwards.
    static func ridge(_ x: Double, w: Double, h: Double, base: Double, waves: [(Double, Double, Double)]) -> Double {
        var y = base * h
        for (a, f, p) in waves { y -= a * h * sin(x / w * 2 * .pi * f + p) }
        return y
    }

    static let far: [(Double, Double, Double)] = [(0.05, 1.3, 0.4), (0.03, 3.1, 1.2), (0.012, 7.7, 0.3)]
    static let mid: [(Double, Double, Double)] = [(0.06, 0.9, 2.2), (0.025, 2.3, 0.6), (0.01, 5.9, 1.9)]

    /// A pine: a stack of five tiers that widen downwards, on a thin trunk.
    static func pine(_ x: Double, _ base: Double, _ height: Double) -> Path {
        var p = Path()
        let top = base - height
        p.move(to: CGPoint(x: x, y: top))
        var right: [CGPoint] = []
        for i in 1...5 {
            let y = top + height * 0.86 * Double(i) / 5
            let wi = height * 0.2 * Double(i) / 5 + 1.5
            right.append(CGPoint(x: x + wi, y: y))
            if i < 5 { right.append(CGPoint(x: x + wi * 0.35, y: y - height * 0.02)) }
        }
        for pt in right { p.addLine(to: pt) }
        p.addLine(to: CGPoint(x: x + 1.2, y: top + height * 0.86))
        p.addLine(to: CGPoint(x: x + 1.2, y: base))
        p.addLine(to: CGPoint(x: x - 1.2, y: base))
        p.addLine(to: CGPoint(x: x - 1.2, y: top + height * 0.86))
        for pt in right.reversed() { p.addLine(to: CGPoint(x: 2 * x - pt.x, y: pt.y)) }
        p.closeSubpath()
        return p
    }

    /// Draws the photo into a w × h rectangle at the origin.
    static func photo(_ ctx: inout GraphicsContext, w: Double, h: Double) {
        let full = Path(CGRect(x: 0, y: 0, width: w, height: h))
        let horizon = 0.74 * h
        // Sky.
        ctx.fill(full, with: .linearGradient(Gradient(stops: [
            .init(color: rgb(0x16213F), location: 0),
            .init(color: rgb(0x4B3A6B), location: 0.38),
            .init(color: rgb(0xC4607A), location: 0.56),
            .init(color: rgb(0xF4A261), location: 0.68),
            .init(color: rgb(0xFFD59E), location: 0.74),
        ]), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
        // Stars in the upper sky, fixed places (a hash of the index).
        for i in 0..<28 {
            let fx = (Double(i) * 0.618034).truncatingRemainder(dividingBy: 1)
            let fy = (Double(i) * 0.381966 + 0.13 * Double(i % 3)).truncatingRemainder(dividingBy: 1)
            let r = i % 5 == 0 ? 1.3 : 0.8
            ctx.fill(Path(ellipseIn: CGRect(x: fx * w - r, y: (0.08 + 0.3 * fy) * h - r, width: 2 * r, height: 2 * r)),
                     with: .color(.white.opacity(0.35 + 0.4 * Double(i % 4) / 3 - 0.3 * fy)))
        }
        // Sun and its glow.
        let sun = CGPoint(x: 0.64 * w, y: 0.665 * h)
        ctx.fill(full, with: .radialGradient(Gradient(colors: [rgb(0xFFC27A, 0.85), rgb(0xFFC27A, 0)]),
                                             center: sun, startRadius: 0, endRadius: 0.55 * w))
        ctx.fill(Path(ellipseIn: CGRect(x: sun.x - 0.075 * w, y: sun.y - 0.075 * w, width: 0.15 * w, height: 0.15 * w)),
                 with: .color(rgb(0xFFF3D6)))
        // Streaks of cloud.
        for (x, y, cw, a) in [(0.06, 0.47, 0.42, 0.35), (0.3, 0.52, 0.5, 0.28), (0.62, 0.43, 0.3, 0.22), (0.7, 0.58, 0.26, 0.3)] {
            ctx.fill(Path(roundedRect: CGRect(x: x * w, y: y * h, width: cw * w, height: 5), cornerRadius: 2.5),
                     with: .color(rgb(0xF7B48A, a)))
        }
        // Far and middle ridges.
        func ridgePath(_ waves: [(Double, Double, Double)], base: Double) -> Path {
            var p = Path()
            p.move(to: CGPoint(x: 0, y: horizon))
            var x = 0.0
            while x <= w + 4 {
                p.addLine(to: CGPoint(x: x, y: ridge(x, w: w, h: h, base: base, waves: waves)))
                x += 4
            }
            p.addLine(to: CGPoint(x: w + 4, y: horizon))
            p.closeSubpath()
            return p
        }
        ctx.fill(ridgePath(far, base: 0.66), with: .color(rgb(0xA45A7C)))
        let midRidge = ridgePath(mid, base: 0.70)
        ctx.fill(midRidge, with: .color(rgb(0x5D3160)))
        // Lake.
        let lake = Path(CGRect(x: 0, y: horizon, width: w, height: h - horizon))
        ctx.fill(lake, with: .linearGradient(Gradient(stops: [
            .init(color: rgb(0xE59A6F), location: 0),
            .init(color: rgb(0x3D2447), location: 0.55),
            .init(color: rgb(0x1A1022), location: 1),
        ]), startPoint: CGPoint(x: 0, y: horizon), endPoint: CGPoint(x: 0, y: h)))
        // The middle ridge mirrored in the water.
        var mirrored = ctx
        mirrored.clip(to: lake)
        mirrored.translateBy(x: 0, y: 2 * horizon)
        mirrored.scaleBy(x: 1, y: -1)
        mirrored.fill(midRidge, with: .color(rgb(0x5D3160, 0.45)))
        // The sun's path on the water.
        var y = horizon + 5
        var i = 0.0
        while y < 0.93 * h {
            let k = (y - horizon) / (0.93 * h - horizon)
            let lw = (0.16 - 0.12 * k) * w * (i.truncatingRemainder(dividingBy: 2) == 0 ? 1 : 0.7)
            ctx.fill(Path(roundedRect: CGRect(x: sun.x - lw / 2, y: y, width: lw, height: 2.4), cornerRadius: 1.2),
                     with: .color(rgb(0xFFE2B0, 0.8 - 0.6 * k)))
            y += 7
            i += 1
        }
        // The near shore with its pines.
        let ink = rgb(0x120A18)
        var shore = Path()
        shore.move(to: CGPoint(x: 0, y: 0.83 * h))
        shore.addCurve(to: CGPoint(x: 0.46 * w, y: h), control1: CGPoint(x: 0.22 * w, y: 0.84 * h), control2: CGPoint(x: 0.36 * w, y: 0.9 * h))
        shore.addLine(to: CGPoint(x: 0, y: h))
        shore.closeSubpath()
        ctx.fill(shore, with: .color(ink))
        var right = Path()
        right.move(to: CGPoint(x: w, y: 0.86 * h))
        right.addCurve(to: CGPoint(x: 0.78 * w, y: h), control1: CGPoint(x: 0.9 * w, y: 0.87 * h), control2: CGPoint(x: 0.82 * w, y: 0.93 * h))
        right.addLine(to: CGPoint(x: w, y: h))
        right.closeSubpath()
        ctx.fill(right, with: .color(ink))
        for (x, b, ph) in [(0.04, 0.84, 0.3), (0.1, 0.845, 0.4), (0.165, 0.85, 0.24), (0.235, 0.86, 0.33), (0.3, 0.875, 0.2),
                           (0.885, 0.875, 0.26), (0.95, 0.87, 0.34)] {
            ctx.fill(pine(x * w, b * h + 6, ph * h), with: .color(ink))
        }
        // Vignette.
        ctx.fill(full, with: .radialGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.45)]),
                                             center: CGPoint(x: w / 2, y: h * 0.55), startRadius: 0.3 * h, endRadius: 0.85 * h))
    }

    enum Icon { case back, undo, redo, more, auto, light, contrast, color, vignette, grain, sharpen, crop, brush, dial, layers, sky }

    /// An icon drawn in a 24 × 24 box: what to stroke (1.6 wide, round) and what to fill.
    static func icon(_ icon: Icon) -> (stroke: Path, fill: Path) {
        var s = Path(), f = Path()
        func line(_ pts: [(Double, Double)], close: Bool = false) {
            s.move(to: CGPoint(x: pts[0].0, y: pts[0].1))
            for p in pts.dropFirst() { s.addLine(to: CGPoint(x: p.0, y: p.1)) }
            if close { s.closeSubpath() }
        }
        func circle(_ x: Double, _ y: Double, _ r: Double, fill: Bool = false) {
            let p = Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
            if fill { f.addPath(p) } else { s.addPath(p) }
        }
        func star(_ x: Double, _ y: Double, _ r: Double) {
            let k = r * 0.24
            f.move(to: CGPoint(x: x, y: y - r))
            f.addQuadCurve(to: CGPoint(x: x + r, y: y), control: CGPoint(x: x + k, y: y - k))
            f.addQuadCurve(to: CGPoint(x: x, y: y + r), control: CGPoint(x: x + k, y: y + k))
            f.addQuadCurve(to: CGPoint(x: x - r, y: y), control: CGPoint(x: x - k, y: y + k))
            f.addQuadCurve(to: CGPoint(x: x, y: y - r), control: CGPoint(x: x - k, y: y - k))
            f.closeSubpath()
        }
        switch icon {
        case .back: line([(15, 5), (8, 12), (15, 19)])
        case .undo, .redo:
            line([(9, 5), (5, 9), (9, 13)])
            s.move(to: CGPoint(x: 5, y: 9))
            s.addLine(to: CGPoint(x: 14, y: 9))
            s.addArc(center: CGPoint(x: 14, y: 14), radius: 5, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false)
            s.addLine(to: CGPoint(x: 10, y: 19))
            if icon == .redo { s = s.applying(CGAffineTransform(a: -1, b: 0, c: 0, d: 1, tx: 24, ty: 0)) }
        case .more:
            circle(12, 12, 9)
            for x in [8.0, 12, 16] { circle(x, 12, 1.3, fill: true) }
        case .auto:
            star(10.5, 13.5, 7)
            star(18, 5.5, 3)
        case .light:
            circle(12, 12, 4)
            for k in 0..<8 {
                let a = Double(k) * .pi / 4
                line([(12 + 7 * cos(a), 12 + 7 * sin(a)), (12 + 9.5 * cos(a), 12 + 9.5 * sin(a))])
            }
        case .contrast:
            circle(12, 12, 8)
            f.move(to: CGPoint(x: 12, y: 4))
            f.addArc(center: CGPoint(x: 12, y: 12), radius: 8, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: true)
            f.closeSubpath()
        case .color:
            circle(9, 10, 5); circle(15, 10, 5); circle(12, 15, 5)
        case .vignette:
            s.addPath(Path(roundedRect: CGRect(x: 4, y: 4, width: 16, height: 16), cornerRadius: 4))
            s.addPath(Path(ellipseIn: CGRect(x: 8, y: 8.5, width: 8, height: 7)))
        case .grain:
            for x in [7.0, 12, 17] { for y in [7.0, 12, 17] { circle(x, y, 1.4, fill: true) } }
        case .sharpen: line([(12, 4), (20, 19), (4, 19)], close: true)
        case .crop:
            line([(7, 3), (7, 17), (21, 17)])
            line([(3, 7), (17, 7), (17, 21)])
        case .brush:
            line([(5, 15), (14, 6), (19, 11), (10, 20)], close: true)
            line([(9.5, 10.5), (14.5, 15.5)])
            line([(10, 20), (20, 20)])
        case .dial:
            circle(12, 13, 8)
            line([(12, 13), (16, 9)])
            circle(12, 13, 1.6, fill: true)
            line([(12, 2.5), (12, 4)])
        case .layers:
            s.addPath(Path(roundedRect: CGRect(x: 4, y: 8, width: 12, height: 12), cornerRadius: 2))
            s.addPath(Path(roundedRect: CGRect(x: 8, y: 4, width: 12, height: 12), cornerRadius: 2))
        case .sky:
            s.addArc(center: CGPoint(x: 12, y: 15), radius: 5, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
            line([(3, 15), (21, 15)])
            line([(12, 5), (12, 7)]); line([(5.6, 8.6), (7, 10)]); line([(18.4, 8.6), (17, 10)])
            line([(6, 19), (18, 19)])
        }
        return (s, f)
    }

    /// Draws an icon centred at a point, 24 pt square.
    static func draw(_ icon: Icon, in ctx: inout GraphicsContext, at c: CGPoint, color: Color, size: Double = 24) {
        let (s, f) = WaveScrubSliderArt.icon(icon)
        var g = ctx
        g.translateBy(x: c.x - size / 2, y: c.y - size / 2)
        g.scaleBy(x: size / 24, y: size / 24)
        g.stroke(s, with: .color(color), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
        g.fill(f, with: .color(color))
    }
}
