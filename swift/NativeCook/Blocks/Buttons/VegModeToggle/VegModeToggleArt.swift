import SwiftUI

/*
 Veg Mode Toggle — the two dishes, drawn in code.

 Units: the toggle's height H = 1, origin at the track's top-left; the track is 2.42 × 1. Each dish
 pops up about its pivot on the track's bottom line (y = 1), which also cuts it: the caller clips
 everything below y = 1.
 Broccoli: a cloud of seven lobes, a two-prong stem, a darker front floret and a garlic bulb.
 Drumstick: a red leg leaning right, a white bone on top, a halved boiled egg in front.
*/
enum VegModeToggleArt {
    static func rgb(_ hex: UInt32) -> (Double, Double, Double) {
        (Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
    }

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        let c = rgb(hex)
        return Color(.sRGB, red: c.0, green: c.1, blue: c.2, opacity: alpha)
    }

    static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    static func linear(_ stops: [(UInt32, Double)], _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double)
        -> GraphicsContext.Shading {
        .linearGradient(Gradient(stops: stops.map { .init(color: color($0.0), location: $0.1) }),
                        startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    static func radial(_ hex: UInt32, _ alpha: Double, _ x: Double, _ y: Double, _ r: Double) -> GraphicsContext.Shading {
        .radialGradient(Gradient(colors: [color(hex, alpha), color(hex, 0)]),
                        center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r)
    }

    // MARK: Geometry (shared by the art and its shadow)

    static let lobes: [(Double, Double, Double)] = [
        (0.27, -0.13, 0.24), (-0.16, 0.25, 0.30), (0.54, 0.06, 0.22), (0.19, 0.40, 0.40),
        (0.22, 0.74, 0.28), (0.58, 0.58, 0.20), (-0.06, 0.58, 0.26),
    ]
    static let floretLobes: [(Double, Double, Double)] = [
        (0.64, 0.28, 0.13), (0.80, 0.20, 0.14), (0.97, 0.29, 0.12),
        (0.70, 0.42, 0.11), (0.82, 0.40, 0.15), (0.94, 0.42, 0.10),
    ]

    static var cloud: Path {
        var p = Path()
        for l in lobes { p.addPath(circle(l.0, l.1, l.2)) }
        return p
    }

    static var floret: Path {
        var p = Path()
        for l in floretLobes { p.addPath(circle(l.0, l.1, l.2)) }
        return p
    }

    /// The floret's short stalk, under its crown.
    static var stalk: Path {
        var p = Path()
        p.move(to: CGPoint(x: 0.74, y: 0.46))
        p.addLine(to: CGPoint(x: 0.90, y: 0.46))
        p.addLine(to: CGPoint(x: 0.86, y: 0.74))
        p.addLine(to: CGPoint(x: 0.78, y: 0.74))
        p.closeSubpath()
        return p
    }

    static var bulb: Path { circle(0.93, 0.97, 0.25) }

    /// The bulb's papery tip, drawn as its own shape over the bulb.
    static var bulbTip: Path {
        var p = Path()
        p.move(to: CGPoint(x: 0.88, y: 0.76))
        p.addQuadCurve(to: CGPoint(x: 0.94, y: 0.63), control: CGPoint(x: 0.92, y: 0.72))
        p.addQuadCurve(to: CGPoint(x: 0.98, y: 0.76), control: CGPoint(x: 0.95, y: 0.72))
        p.closeSubpath()
        return p
    }

    static var stem: Path {
        var p = Path()
        p.move(to: CGPoint(x: 0.56, y: 1.05))
        p.addLine(to: CGPoint(x: 0.47, y: 0.78))
        p.move(to: CGPoint(x: 0.58, y: 1.05))
        p.addLine(to: CGPoint(x: 0.66, y: 0.80))
        return p
    }

    static var bone: Path {
        var p = Path()
        p.move(to: CGPoint(x: 2.03, y: 0.10))
        p.addLine(to: CGPoint(x: 2.13, y: -0.12))
        return p
    }

    static var boneEnds: Path {
        var p = circle(2.08, -0.19, 0.075)
        p.addPath(circle(2.19, -0.14, 0.075))
        return p
    }

    static var meat: Path {
        var p = Path()
        p.move(to: CGPoint(x: 1.33, y: 1.0))
        p.addCurve(to: CGPoint(x: 2.03, y: 0.05), control1: CGPoint(x: 1.30, y: 0.62), control2: CGPoint(x: 1.64, y: 0.26))
        p.addCurve(to: CGPoint(x: 2.22, y: 0.66), control1: CGPoint(x: 2.12, y: 0.14), control2: CGPoint(x: 2.23, y: 0.42))
        p.addLine(to: CGPoint(x: 2.10, y: 1.0))
        p.closeSubpath()
        return p
    }

    /// The left edge of the leg, where the gloss runs.
    static var meatGloss: Path {
        var p = Path()
        p.move(to: CGPoint(x: 1.45, y: 0.80))
        p.addCurve(to: CGPoint(x: 1.93, y: 0.17), control1: CGPoint(x: 1.47, y: 0.55), control2: CGPoint(x: 1.68, y: 0.32))
        return p
    }

    static var eggWhite: Path {
        Path(ellipseIn: CGRect(x: -0.30, y: -0.24, width: 0.60, height: 0.48))
            .applying(CGAffineTransform(rotationAngle: -30 * .pi / 180)
                .concatenating(CGAffineTransform(translationX: 2.02, y: 0.86)))
    }

    // MARK: Pop

    /// Places a dish for its amount a (0 hidden, 1 at rest) and velocity v: it rises from below the
    /// bottom line, grows, leans in and squashes with its speed. Reduced motion only fades it.
    static func pop(_ c: inout GraphicsContext, a: Double, v: Double, pivot: Double, lean: Double, reduced: Bool) {
        if reduced { return }
        let k = max(-1, min(1, v / 8))
        let s = max(0, 0.6 + 0.4 * a)
        c.translateBy(x: pivot, y: 1 + (1 - a) * 1.05)
        c.rotate(by: .degrees((1 - a) * lean))
        c.scaleBy(x: s * (1 - 0.05 * k), y: s * (1 + 0.07 * k))
        c.translateBy(x: -pivot, y: -1)
    }

    /// A part that lags its dish: scaled about its own pivot by how far behind it is.
    static func part(_ c: inout GraphicsContext, a: Double, a2: Double, x: Double, y: Double, reduced: Bool) {
        if reduced { return }
        let s = max(0, 1 + 0.6 * (a2 - a))
        c.translateBy(x: x, y: y)
        c.scaleBy(x: s, y: s)
        c.translateBy(x: -x, y: -y)
    }

    // MARK: Drawing

    /// The broccoli. With `silhouette`, every part is filled black (for the shadow layer).
    static func broccoli(_ c: inout GraphicsContext, a: Double, v: Double, a2: Double, reduced: Bool, silhouette: Bool) {
        var g = c
        pop(&g, a: a, v: v, pivot: 0.30, lean: -14, reduced: reduced)
        let ink = GraphicsContext.Shading.color(.black)

        g.stroke(stem, with: silhouette ? ink : .color(color(0x3BB455)),
                 style: StrokeStyle(lineWidth: 0.09, lineCap: .round))

        let cloudPath = cloud
        g.fill(cloudPath, with: silhouette ? ink
               : linear([(0x1FA244, 0), (0x4CC262, 0.5), (0xAEE483, 1)], -0.3, -0.3, 0.35, 1.0))
        if !silhouette {
            // One broad gloss on the crown.
            g.fill(cloudPath, with: radial(0xFFFFFF, 0.38, 0.05, 0.02, 0.34))
        }

        var f = g
        part(&f, a: a, a2: a2, x: 0.82, y: 0.74, reduced: reduced)
        f.fill(stalk, with: silhouette ? ink : linear([(0x5CC266, 0), (0x9EDC86, 1)], 0.82, 0.46, 0.82, 0.74))
        f.fill(floret, with: silhouette ? ink : linear([(0x1C9A40, 0), (0x5DCB6C, 1)], 0.6, 0.1, 0.95, 0.55))
        if !silhouette {
            f.fill(circle(0.80, 0.20, 0.14), with: radial(0xFFFFFF, 0.24, 0.76, 0.15, 0.12))
            f.fill(circle(0.64, 0.28, 0.13), with: radial(0xFFFFFF, 0.18, 0.60, 0.23, 0.11))
        }

        var w = g
        part(&w, a: a, a2: a2, x: 0.93, y: 1.0, reduced: reduced)
        w.fill(bulbTip, with: silhouette ? ink : linear([(0xF4F1EA, 0), (0xDCD9D2, 1)], 0.93, 0.63, 0.93, 0.76))
        w.fill(bulb, with: silhouette ? ink : linear([(0xFFFFFF, 0), (0xDCD9D2, 1)], 0.93, 0.70, 0.93, 1.05))
        if !silhouette {
            var cloves = Path()
            cloves.move(to: CGPoint(x: 0.86, y: 0.80))
            cloves.addQuadCurve(to: CGPoint(x: 0.84, y: 1.02), control: CGPoint(x: 0.81, y: 0.90))
            cloves.move(to: CGPoint(x: 1.00, y: 0.80))
            cloves.addQuadCurve(to: CGPoint(x: 1.02, y: 1.02), control: CGPoint(x: 1.05, y: 0.90))
            w.stroke(cloves, with: .color(color(0xCFCBC2)), lineWidth: 0.012)
            w.fill(circle(0.87, 0.84, 0.07), with: radial(0xFFFFFF, 0.9, 0.87, 0.84, 0.07))
        }
    }

    /// The drumstick and the egg. With `silhouette`, every part is filled black.
    static func drumstick(_ c: inout GraphicsContext, a: Double, v: Double, a2: Double, reduced: Bool, silhouette: Bool) {
        var g = c
        pop(&g, a: a, v: v, pivot: 1.76, lean: 14, reduced: reduced)
        let ink = GraphicsContext.Shading.color(.black)
        let boneShade = linear([(0xFFFFFF, 0), (0xDAD5CB, 1)], 2.05, -0.28, 2.18, 0.08)

        g.stroke(bone, with: silhouette ? ink : boneShade, style: StrokeStyle(lineWidth: 0.10, lineCap: .round))
        g.fill(boneEnds, with: silhouette ? ink : boneShade)

        g.fill(meat, with: silhouette ? ink : linear([(0xF0603A, 0), (0xE2412F, 0.45), (0xD2282B, 1)], 1.95, 0.1, 1.45, 0.95))
        if !silhouette {
            g.stroke(meatGloss, with: .color(color(0xFFFFFF, 0.28)), style: StrokeStyle(lineWidth: 0.05, lineCap: .round))
            g.fill(circle(1.98, 0.16, 0.10), with: radial(0xFFFFFF, 0.30, 1.98, 0.16, 0.10))
        }

        var e = g
        part(&e, a: a, a2: a2, x: 2.0, y: 1.0, reduced: reduced)
        e.fill(eggWhite, with: silhouette ? ink : linear([(0xFFFFFF, 0), (0xE3DFD6, 1)], 2.12, 0.62, 1.92, 1.08))
        if !silhouette {
            e.fill(circle(1.96, 0.92, 0.17), with: linear([(0xFFD64F, 0), (0xF0961E, 1)], 1.90, 0.76, 2.04, 1.08))
            e.fill(circle(1.91, 0.86, 0.04), with: .color(color(0xFFFFFF, 0.6)))
        }
    }
}
