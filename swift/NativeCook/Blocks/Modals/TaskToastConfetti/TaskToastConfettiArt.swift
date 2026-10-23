import SwiftUI

/// The screen's drawings, each in the coordinates of the React Native SVG's viewBox.
enum TaskToastConfettiArt {
    static let blue = Color(hex: 0x1a5cff)
    static let scribble = Color(hex: 0xb9bdc9)

    private static let round = StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round)

    /// A round tomato-red creature with a sprout, looking right at the bubble.
    static var mascot: some View {
        Canvas { context, _ in
            context.fill(ellipse(32, 60, 17, 3), with: .color(.black.opacity(0.12)))
            // The SVG's gradient box is the circle's bounds: centre at 38 % / 34 %, radius 70 %.
            context.fill(
                ellipse(32, 35, 24, 24),
                with: .radialGradient(
                    Gradient(stops: [
                        .init(color: Color(hex: 0xff7b6e), location: 0),
                        .init(color: Color(hex: 0xef3340), location: 0.55),
                        .init(color: Color(hex: 0xb8172a), location: 1),
                    ]),
                    center: CGPoint(x: 8 + 0.38 * 48, y: 11 + 0.34 * 48), startRadius: 0, endRadius: 0.7 * 48
                )
            )
            var shine = context
            shine.translateBy(x: 22, y: 23)
            shine.rotate(by: .degrees(-30))
            shine.fill(ellipse(0, 0, 7, 4.5), with: .color(.white.opacity(0.35)))
            context.fill(path("M32 13 C26 4 18 6 17 9 C22 9 27 11 32 15 Z"), with: .color(Color(hex: 0x2ea95a)))
            context.fill(path("M32 13 C37 3 46 5 47 8 C42 8 37 11 32 15 Z"), with: .color(Color(hex: 0x3cc46a)))
            context.stroke(path("M31 15 C30 9 31 5 33 2"), with: .color(Color(hex: 0x24874a)), style: round)
            for x in [27.0, 41] { context.fill(ellipse(x, 33, 6.2, 7.4), with: .color(.white)) }
            for x in [29.5, 43.5] { context.fill(ellipse(x, 34, 3.4, 3.4), with: .color(Color(hex: 0x15131c))) }
            for x in [30.6, 44.6] { context.fill(ellipse(x, 32.6, 1.1, 1.1), with: .color(.white)) }
            let brow = StrokeStyle(lineWidth: 2.6, lineCap: .round)
            let dark = GraphicsContext.Shading.color(Color(hex: 0x7a0f1d))
            context.stroke(path("M20 24.5 L30 27"), with: dark, style: brow)
            context.stroke(path("M48 24.5 L38 27"), with: dark, style: brow)
            context.stroke(path("M30 45 Q35 49 40 45"), with: dark, style: round)
            for x in [20.0, 47] { context.fill(ellipse(x, 43, 3.4, 2), with: .color(Color(hex: 0xff9aa2, opacity: 0.7))) }
        }
        .frame(width: 64, height: 64)
    }

    /// The profile picture: a fuzzy blue face with big eyes.
    static var avatar: some View {
        Canvas { context, _ in
            let face = ellipse(22, 22, 21, 21)
            context.fill(
                face,
                with: .radialGradient(
                    Gradient(colors: [Color(hex: 0x3f7bff), Color(hex: 0x0a2a8f)]),
                    center: CGPoint(x: 1 + 0.4 * 42, y: 1 + 0.35 * 42), startRadius: 0, endRadius: 0.7 * 42
                )
            )
            context.stroke(face, with: .color(Color(hex: 0x5d8fff)), lineWidth: 1.5)
            context.fill(ellipse(16, 21, 6, 7), with: .color(.white))
            context.fill(ellipse(28, 20, 6, 7), with: .color(.white))
            let ink = GraphicsContext.Shading.color(Color(hex: 0x0d0f1a))
            context.fill(ellipse(17.5, 22, 3, 3), with: ink)
            context.fill(ellipse(26.5, 21.5, 3, 3), with: ink)
            context.stroke(path("M18 31 Q22 34 26 31"), with: ink, style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
        }
        .frame(width: 44, height: 44)
    }

    /// The app's mark: a white drop with a dot.
    static var logo: some View {
        Canvas { context, _ in
            context.fill(path("M20 4 C27 14 36 21 36 30 C36 39 29 45 20 45 C11 45 4 39 4 30 C4 21 13 14 20 4 Z"), with: .color(.white))
            context.fill(ellipse(27, 5, 4, 4), with: .color(.white))
            context.stroke(path("M20 26 L20 36"), with: .color(blue), style: StrokeStyle(lineWidth: 5, lineCap: .round))
            context.fill(ellipse(20, 20, 3, 3), with: .color(blue))
        }
        .frame(width: 40, height: 46)
    }

    /// The scribbled arrow that points from the inspiration note to the tab bar.
    static var scribbleArrow: some View {
        Canvas { context, _ in
            context.stroke(path("M13 3 C10 15 17 27 14 47"), with: .color(scribble), style: round)
            context.stroke(path("M5 37 L14 48 L23 38"), with: .color(scribble), style: round)
        }
        .frame(width: 30, height: 52)
    }

    private static func ellipse(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2))
    }

    /// Builds a path from SVG path data with the absolute M, L, Q, C and Z commands the drawings use.
    private static func path(_ data: String) -> Path {
        var tokens = data.replacingOccurrences(of: ",", with: " ").split(separator: " ").map(String.init)
        var path = Path()
        func point() -> CGPoint {
            let x = Double(tokens.removeFirst()) ?? 0
            return CGPoint(x: x, y: Double(tokens.removeFirst()) ?? 0)
        }
        while !tokens.isEmpty {
            let token = tokens.removeFirst()
            let command = token.prefix(1)
            // The number may be glued to its command ("M32"): put it back.
            if token.count > 1 { tokens.insert(String(token.dropFirst()), at: 0) }
            switch command {
            case "M": path.move(to: point())
            case "L": path.addLine(to: point())
            case "Q":
                let control = point()
                path.addQuadCurve(to: point(), control: control)
            case "C":
                let first = point(), second = point()
                path.addCurve(to: point(), control1: first, control2: second)
            default: path.closeSubpath()
            }
        }
        return path
    }
}
