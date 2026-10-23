import SwiftUI

/// The dish, drawn from vectors in units of the plate's diameter, centred on 0, 0.
enum CartFlyStepperPlate {
    private static let plateLight: UInt32 = 0xF6F2EA
    private static let plateDark: UInt32 = 0xE1D9CB
    private static let plateRim: UInt32 = 0xD3CABA
    private static let rice: UInt32 = 0xFBF8F1
    private static let salmonLight: UInt32 = 0xE2703F
    private static let salmonDark: UInt32 = 0xA1401C
    private static let glaze: UInt32 = 0x7A2E14
    private static let seed: UInt32 = 0xFFF8EA
    private static let cucumber: UInt32 = 0xD6E8B0
    private static let cucumberRind: UInt32 = 0x6F9A3E

    // Edamame and herbs: x, y, radius, and which of the two greens.
    private static let greens: [(Double, Double, Double, Int)] = [
        (-0.27, -0.12, 0.045, 0),
        (-0.2, 0.17, 0.04, 1),
        (-0.06, 0.27, 0.045, 0),
        (0.14, 0.24, 0.038, 1),
        (0.27, 0.08, 0.045, 0),
        (0.24, -0.17, 0.04, 1),
        (0.05, -0.28, 0.042, 0),
        (-0.14, -0.25, 0.036, 1),
        (-0.3, 0.05, 0.034, 1),
    ]
    private static let greenColors: [UInt32] = [0x8DB255, 0x5F8F34]
    // Cucumber slices: x, y, radius.
    private static let slices: [(Double, Double, Double)] = [(0.2, 0.15, 0.07), (-0.22, -0.02, 0.065)]
    // Sesame seeds on the fillet, in the fillet's own turned frame: x, y, angle in degrees.
    private static let seeds: [(Double, Double, Double)] = [
        (-0.13, -0.04, 20), (-0.07, 0.03, -30), (-0.02, -0.05, 60), (0.04, 0.04, 10),
        (0.09, -0.03, -50), (0.14, 0.02, 35), (-0.1, 0.06, 80), (0.01, 0.0, -10),
    ]
    private static let filletTurn = -32.0

    private static func color(_ hex: UInt32, _ alpha: Double) -> Color { Color(hex: hex, opacity: min(max(alpha, 0), 1)) }

    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    /// The plate of salmon, rice and greens, `d` points across, centred on `cx`, `cy`.
    static func draw(_ context: inout GraphicsContext, cx: Double, cy: Double, d: Double, alpha: Double) {
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: max(0.035 * d, 0.5)))
            layer.fill(circle(cx, cy + 0.04 * d, 0.48 * d), with: .color(.black.opacity(0.14 * alpha)))
        }
        context.fill(
            circle(cx, cy, 0.5 * d),
            with: .radialGradient(
                Gradient(colors: [color(plateLight, alpha), color(plateDark, alpha)]),
                center: CGPoint(x: cx - 0.12 * d, y: cy - 0.14 * d), startRadius: 0, endRadius: 0.62 * d
            )
        )
        context.stroke(circle(cx, cy, 0.42 * d), with: .color(color(plateRim, 0.8 * alpha)), lineWidth: max(0.012 * d, 0.5))
        context.fill(circle(cx, cy, 0.36 * d), with: .color(color(rice, alpha)))

        for (x, y, r) in slices {
            context.fill(circle(cx + x * d, cy + y * d, r * d), with: .color(color(cucumberRind, alpha)))
            context.fill(circle(cx + x * d, cy + y * d, r * d * 0.8), with: .color(color(cucumber, alpha)))
        }
        for (x, y, r, k) in greens {
            context.fill(circle(cx + x * d, cy + y * d, r * d), with: .color(color(greenColors[k], alpha)))
        }

        // The fillet, turned, with a glaze and sesame seeds.
        var fillet = context
        fillet.translateBy(x: cx + 0.02 * d, y: cy - 0.02 * d)
        fillet.rotate(by: .degrees(filletTurn))
        let w = 0.44 * d
        let h = 0.2 * d
        fillet.fill(
            Path(roundedRect: CGRect(x: -w / 2, y: -h / 2, width: w, height: h), cornerRadius: 0.09 * d, style: .circular),
            with: .linearGradient(Gradient(colors: [color(salmonLight, alpha), color(salmonDark, alpha)]), startPoint: CGPoint(x: 0, y: -h / 2), endPoint: CGPoint(x: 0, y: h / 2))
        )
        var lines = Path()
        for k in -2...2 {
            let x = Double(k) * 0.07 * d
            lines.move(to: CGPoint(x: x - 0.03 * d, y: -h * 0.38))
            lines.addLine(to: CGPoint(x: x + 0.03 * d, y: h * 0.38))
        }
        fillet.stroke(lines, with: .color(color(glaze, 0.45 * alpha)), style: StrokeStyle(lineWidth: max(0.012 * d, 0.4), lineCap: .round))
        for (x, y, a) in seeds {
            var grain = fillet
            grain.translateBy(x: x * d, y: y * d)
            grain.rotate(by: .degrees(a))
            grain.fill(Path(ellipseIn: CGRect(x: -0.014 * d, y: -0.007 * d, width: 0.028 * d, height: 0.014 * d)), with: .color(color(seed, alpha)))
        }
    }
}
