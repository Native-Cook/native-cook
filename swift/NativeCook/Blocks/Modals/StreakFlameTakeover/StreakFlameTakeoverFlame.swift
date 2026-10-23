import SwiftUI

/// The flame mascot: a gold flame round a hot little character in shades, with a glint that sweeps
/// the lenses and a sparkle by them.
struct StreakFlameTakeoverFlame: View {
    static let size = 180.0

    /// 0 to 1: the glint's trip across the lenses.
    let glint: Double
    /// The sparkle's opacity.
    let sparkle: Double

    /// Everything is drawn in a 200-point box and scaled to the flame's size.
    private static let box = 200.0
    private static let frame = Color(hex: 0x4a1a10)
    private static let glintWidth = 9.0

    // The gold flame: a round body with three tips swept up and to the right.
    private static let outer = StreakFlameTakeoverSVG.path(
        "M100 190C58 190 28 160 28 120C28 92 40 70 58 54C62 70 70 76 78 74C76 54 84 34 104 18C104 38 112 50 122 52C124 36 134 22 152 12C146 32 148 48 156 58C162 50 170 44 182 42C172 58 168 72 170 88C174 100 172 110 172 120C172 160 142 190 100 190Z"
    )
    // The hot little character inside it, with two ears, a waving arm and a foot.
    private static let body = StreakFlameTakeoverSVG.path(
        "M62 150C60 120 63 96 72 82C76 70 90 66 98 78C100 80 104 80 106 78C114 66 128 70 132 82C142 98 144 124 140 150C136 172 120 182 100 182C80 182 64 172 62 150Z"
    )
    private static let arm = StreakFlameTakeoverSVG.path("M138 124C150 118 158 108 160 98C166 102 166 116 156 126C151 131 144 134 139 134Z")
    private static let lenses = StreakFlameTakeoverSVG.path(
        "M66 110L100 110L98 124C96 132 91 135 84 135L77 135C70 135 66 130 66 123Z" + "M106 110L146 108L144 122C142 131 136 135 128 135L117 135C110 135 106 130 106 123Z"
    )
    private static let brow = StreakFlameTakeoverSVG.path("M60 104L152 101L152 110L60 113Z")
    private static let bridge = StreakFlameTakeoverSVG.path("M98 112H108V117H98Z")
    private static let stripes = StreakFlameTakeoverSVG.path("M80 106L90 106L76 138L66 138ZM124 104L136 104L120 138L108 138Z")
    private static let sparklePath = StreakFlameTakeoverSVG.path("M158 86Q160 96 170 98Q160 100 158 110Q156 100 146 98Q156 96 158 86Z")

    private static func oval(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
        Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h))
    }

    private static func linear(_ colors: [Color], _ from: CGPoint, _ to: CGPoint, stops: [Double]? = nil) -> GraphicsContext.Shading {
        let gradient = stops.map { Gradient(stops: zip(colors, $0).map { .init(color: $0, location: $1) }) } ?? Gradient(colors: colors)
        return .linearGradient(gradient, startPoint: from, endPoint: to)
    }

    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: Self.size / Self.box, y: Self.size / Self.box)
            context.fill(Self.oval(52, 180, 96, 18), with: .color(.rgba(255, 200, 0, 0.22)))
            context.fill(
                Self.outer,
                with: .radialGradient(
                    Gradient(stops: [
                        .init(color: Color(hex: 0xfff6b0), location: 0),
                        .init(color: Color(hex: 0xffdb1f), location: 0.38),
                        .init(color: Color(hex: 0xffbd00), location: 0.75),
                        .init(color: Color(hex: 0xf59e00), location: 1),
                    ]),
                    center: CGPoint(x: 90, y: 112), startRadius: 0, endRadius: 104
                )
            )
            context.stroke(Self.outer, with: .color(.rgba(255, 250, 210, 0.5)), lineWidth: 2)
            var shine = context
            shine.opacity = 0.55
            shine.fill(
                Self.oval(58, 46, 84, 64),
                with: .radialGradient(Gradient(colors: [.white.opacity(0.9), .white.opacity(0)]), center: CGPoint(x: 100, y: 78), startRadius: 0, endRadius: 44)
            )

            context.fill(Self.arm, with: Self.linear([Color(hex: 0xff8a34), Color(hex: 0xe8471a)], CGPoint(x: 140, y: 98), CGPoint(x: 160, y: 134)))
            context.fill(Self.oval(110, 176, 22, 11), with: .color(Color(hex: 0xd93d14)))
            context.fill(
                Self.body,
                with: Self.linear([Color(hex: 0xffa040), Color(hex: 0xf45a1c), Color(hex: 0xd63a12)], CGPoint(x: 0, y: 70), CGPoint(x: 0, y: 182), stops: [0, 0.5, 1])
            )
            var cheek = context
            cheek.translateBy(x: 83, y: 92)
            cheek.rotate(by: .radians(-0.5))
            cheek.fill(Self.oval(-11, -6, 22, 12), with: .color(.white.opacity(0.32)))
            context.fill(Self.oval(68, 146, 64, 30), with: .color(.rgba(150, 30, 0, 0.18)))

            // The shades: a heavy brow bar, two cream lenses with a dark band, and a moving glint.
            context.fill(Self.lenses, with: Self.linear([Color(hex: 0xfff6e4), Color(hex: 0xf2cf98)], CGPoint(x: 0, y: 110), CGPoint(x: 0, y: 135)))
            var inside = context
            inside.clip(to: Self.lenses)
            inside.fill(Self.stripes, with: .color(Self.frame.opacity(0.88)))
            inside.translateBy(x: 56 + glint * 100, y: 0)
            inside.concatenate(CGAffineTransform(a: 1, b: 0, c: -0.5, d: 1, tx: 0, ty: 0))
            inside.fill(Path(CGRect(x: 0, y: 104, width: Self.glintWidth, height: 36)), with: .color(.white.opacity(0.9)))
            context.stroke(Self.lenses, with: .color(Self.frame), style: StrokeStyle(lineWidth: 4.5, lineJoin: .round))
            context.fill(Self.brow, with: .color(Self.frame))
            context.fill(Self.bridge, with: .color(Self.frame))
            var sparkling = context
            sparkling.opacity = sparkle
            sparkling.fill(Self.sparklePath, with: .color(.white))
        }
        .frame(width: Self.size, height: Self.size)
    }
}

/// A pair of gold wings that flap once behind the flame as it pops.
struct StreakFlameTakeoverWings: View {
    static let width = 300.0
    static let height = 140.0

    // One feathered wing; the other is the same, mirrored.
    private static let wing = StreakFlameTakeoverSVG.path(
        "M148 84C122 50 82 30 18 34C34 44 40 50 46 58C26 60 12 68 4 82C24 82 38 86 46 90C30 96 20 106 16 118C52 116 94 108 148 98Z"
    )
    private static let feathers = StreakFlameTakeoverSVG.path("M46 58C70 62 100 70 128 82M46 90C74 90 104 90 132 92")

    var body: some View {
        Canvas { context, _ in
            for side in [1.0, -1.0] {
                var half = context
                half.translateBy(x: Self.width / 2, y: 0)
                half.scaleBy(x: side, y: 1)
                half.translateBy(x: -Self.width / 2, y: 0)
                half.fill(
                    Self.wing,
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: Color(hex: 0xfff08a), location: 0),
                            .init(color: Color(hex: 0xffc800), location: 0.5),
                            .init(color: Color(hex: 0xf2a100), location: 1),
                        ]),
                        startPoint: CGPoint(x: 10, y: 40), endPoint: CGPoint(x: 150, y: 100)
                    )
                )
                half.stroke(Self.feathers, with: .color(.rgba(214, 130, 0, 0.55)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
            }
        }
        .frame(width: Self.width, height: Self.height)
    }
}

/// Paths from SVG data with the absolute M, L, H, V, Q, C and Z commands the drawings use.
enum StreakFlameTakeoverSVG {
    static func path(_ data: String) -> Path {
        var tokens: [String] = []
        var number = ""
        for character in data {
            if character.isLetter {
                if !number.isEmpty { tokens.append(number) }
                number = ""
                tokens.append(String(character))
            } else if character == " " || character == "," {
                if !number.isEmpty { tokens.append(number) }
                number = ""
            } else {
                number.append(character)
            }
        }
        if !number.isEmpty { tokens.append(number) }

        var path = Path()
        var index = 0
        var current = CGPoint.zero
        var command = "M"
        func next() -> Double {
            defer { index += 1 }
            return Double(tokens[index]) ?? 0
        }
        func point() -> CGPoint { CGPoint(x: next(), y: next()) }
        while index < tokens.count {
            if tokens[index].first?.isLetter == true {
                command = tokens[index]
                index += 1
            }
            switch command {
            case "M":
                current = point()
                path.move(to: current)
                command = "L"
            case "L":
                current = point()
                path.addLine(to: current)
            case "H":
                current.x = next()
                path.addLine(to: current)
            case "V":
                current.y = next()
                path.addLine(to: current)
            case "Q":
                let control = point()
                current = point()
                path.addQuadCurve(to: current, control: control)
            case "C":
                let first = point(), second = point()
                current = point()
                path.addCurve(to: current, control1: first, control2: second)
            default:
                path.closeSubpath()
            }
        }
        return path
    }
}
