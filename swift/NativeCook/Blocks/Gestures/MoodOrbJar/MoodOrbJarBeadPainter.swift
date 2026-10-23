import SwiftUI

/// Draws one mood as the same code-built glass bead used by the approved React Native block.
enum MoodOrbJarBeadPainter {
    static func draw(in source: GraphicsContext, radius: Double, mood index: Int) {
        let moods = MoodOrbJarTokens.moods
        let mood = moods[max(0, min(moods.count - 1, index))]
        let circle = Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2))
        source.fill(
            circle,
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: mood.light), location: 0),
                    .init(color: Color(hex: mood.base), location: 0.48),
                    .init(color: Color(hex: mood.deep), location: 1)
                ]),
                center: CGPoint(x: -0.34 * radius, y: -0.4 * radius),
                startRadius: 0,
                endRadius: 1.45 * radius
            )
        )
        var glow = source
        glow.opacity = 0.55
        glow.fill(
            circle,
            with: .radialGradient(
                Gradient(colors: [Color(hex: mood.light), Color(hex: mood.light).opacity(0)]),
                center: CGPoint(x: 0.36 * radius, y: 0.46 * radius),
                startRadius: 0,
                endRadius: 0.62 * radius
            )
        )
        source.stroke(
            Path(ellipseIn: CGRect(x: -radius + 0.5, y: -radius + 0.5, width: radius * 2 - 1, height: radius * 2 - 1)),
            with: .color(Color.rgba(30, 24, 16, 0.14)),
            lineWidth: 1
        )
        var highlight = source
        highlight.translateBy(x: -0.36 * radius, y: -0.44 * radius)
        highlight.rotate(by: .radians(-0.6))
        highlight.fill(
            Path(ellipseIn: CGRect(x: -0.2 * radius, y: -0.11 * radius, width: 0.4 * radius, height: 0.22 * radius)),
            with: .color(Color.rgba(255, 255, 255, 0.92))
        )
        source.fill(
            Path(ellipseIn: CGRect(x: 0.10 * radius, y: -0.61 * radius, width: 0.12 * radius, height: 0.12 * radius)),
            with: .color(Color.rgba(255, 255, 255, 0.7))
        )
    }
}
