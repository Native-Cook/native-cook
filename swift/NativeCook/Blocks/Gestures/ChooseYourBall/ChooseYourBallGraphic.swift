import SwiftUI

// The ball as flat vector art, for the thumbnails of the strip. The upper shell follows the
// ball's `art` recipe from the catalog. Mirrors PokeBallGraphic.tsx (without the glow and the
// swinging highlight, which this screen does not use).

struct ChooseYourBallGraphic: View {
    let ball: ChooseYourBallID
    let size: CGFloat
    /// The release button on the seam. The strip leaves it out: at 26 pt it is noise.
    var showButton = true

    /// Height of the seam band, as a fraction of the diameter.
    private static let bandRatio: CGFloat = 0.115

    var body: some View {
        Canvas { context, _ in
            draw(in: &context)
        }
        .frame(width: size, height: size)
    }

    private func draw(in context: inout GraphicsContext) {
        let spec = ball.spec
        let s = size
        let centre = CGPoint(x: s / 2, y: s / 2)
        let radius = s / 2
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: s * x, y: s * y) }

        // Lower shell: always the pale housing.
        let housing = Path(ellipseIn: CGRect(x: 2, y: 2, width: s - 4, height: s - 4))
        context.fill(
            housing,
            with: .linearGradient(
                Gradient(colors: [
                    Color(chooseYourBallHex: 0xFFFFFF), Color(chooseYourBallHex: 0xF7F7F7), Color(chooseYourBallHex: 0xC8CBD0),
                ]),
                startPoint: point(0.25, 0.12),
                endPoint: point(0.82, 1)
            )
        )

        // Upper shell and its markings, clipped to the dome.
        var dome = Path()
        dome.move(to: CGPoint(x: 0, y: centre.y))
        dome.addArc(center: centre, radius: radius, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
        dome.closeSubpath()

        var shell = context
        shell.clip(to: dome)
        shell.fill(
            dome,
            with: .linearGradient(
                Gradient(colors: [Color(chooseYourBallHex: spec.shell.0), Color(chooseYourBallHex: spec.shell.1)]),
                startPoint: point(0.22, 0.08),
                endPoint: point(0.82, 0.58)
            )
        )
        drawMarkings(in: &shell, spec: spec)

        let band = s * Self.bandRatio
        context.fill(
            Path(CGRect(x: 0, y: centre.y - band / 2, width: s, height: band)),
            with: .color(.black)
        )
        if showButton {
            let outer = s * 0.31
            let inner = s * 0.16
            context.fill(
                Path(ellipseIn: CGRect(x: centre.x - outer / 2, y: centre.y - outer / 2, width: outer, height: outer)),
                with: .color(.black)
            )
            context.fill(
                Path(ellipseIn: CGRect(x: centre.x - inner / 2, y: centre.y - inner / 2, width: inner, height: inner)),
                with: .color(Color(chooseYourBallHex: spec.button))
            )
        }
        context.stroke(housing, with: .color(.white.opacity(0.72)), lineWidth: 2)

        // Specular sweep across the top left.
        var sweep = Path()
        sweep.move(to: point(0.22, 0.24))
        sweep.addCurve(to: point(0.66, 0.13), control1: point(0.34, 0.09), control2: point(0.53, 0.07))
        context.stroke(
            sweep,
            with: .color(.white.opacity(0.30)),
            style: StrokeStyle(lineWidth: s * 0.028, lineCap: .round)
        )
    }

    private func drawMarkings(in context: inout GraphicsContext, spec: ChooseYourBallSpec) {
        let s = size
        let centre = s / 2
        let accent = Color(chooseYourBallHex: spec.accent)

        switch spec.art {
        case .solid:
            break

        case .stripes:
            let width = s * 0.11
            context.fill(Path(CGRect(x: s * 0.2, y: 0, width: width, height: s)), with: .color(accent))
            context.fill(Path(CGRect(x: s * 0.69 - width / 2, y: 0, width: width, height: s)), with: .color(accent))

        case .wedge:
            var left = Path()
            left.move(to: CGPoint(x: 0, y: s * 0.06))
            left.addLine(to: CGPoint(x: s * 0.36, y: centre))
            left.addLine(to: CGPoint(x: 0, y: centre))
            left.closeSubpath()
            var right = Path()
            right.move(to: CGPoint(x: s, y: s * 0.06))
            right.addLine(to: CGPoint(x: s * 0.64, y: centre))
            right.addLine(to: CGPoint(x: s, y: centre))
            right.closeSubpath()
            context.fill(left, with: .color(accent))
            context.fill(right, with: .color(accent))

        case .band:
            context.fill(
                Path(CGRect(x: 0, y: s * 0.26 - s * 0.0425, width: s, height: s * 0.085)),
                with: .color(accent)
            )

        case .emblem:
            // The "M" with a dot on each side.
            let w = s * 0.24
            let h = s * 0.13
            let cy = s * 0.26
            var letter = Path()
            letter.move(to: CGPoint(x: centre - w / 2, y: cy + h / 2))
            letter.addLine(to: CGPoint(x: centre - w / 2, y: cy - h / 2))
            letter.addLine(to: CGPoint(x: centre, y: cy + h * 0.22))
            letter.addLine(to: CGPoint(x: centre + w / 2, y: cy - h / 2))
            letter.addLine(to: CGPoint(x: centre + w / 2, y: cy + h / 2))
            context.stroke(
                letter,
                with: .color(accent),
                style: StrokeStyle(lineWidth: s * 0.045, lineCap: .round, lineJoin: .round)
            )
            for side in [-1.0, 1.0] {
                let dot = CGRect(x: centre + side * s * 0.21 - s * 0.034, y: cy - s * 0.034, width: s * 0.068, height: s * 0.068)
                context.fill(Path(ellipseIn: dot), with: .color(accent))
            }

        case .crest:
            let middle = CGPoint(x: centre, y: s * 0.25)
            let glow = CGRect(x: middle.x - s * 0.1, y: middle.y - s * 0.1, width: s * 0.2, height: s * 0.2)
            context.fill(
                Path(ellipseIn: glow),
                with: .radialGradient(
                    Gradient(colors: [accent, Color(chooseYourBallHex: spec.accent, opacity: 0)]),
                    center: CGPoint(x: centre, y: s * 0.22),
                    startRadius: 0,
                    endRadius: s * 0.13
                )
            )
            let ring = CGRect(x: middle.x - s * 0.075, y: middle.y - s * 0.075, width: s * 0.15, height: s * 0.15)
            context.stroke(Path(ellipseIn: ring), with: .color(accent), lineWidth: s * 0.022)
        }
    }
}
