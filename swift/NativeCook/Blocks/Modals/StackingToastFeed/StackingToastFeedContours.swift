import SwiftUI

/// The faint topographic lines behind the page.
struct StackingToastFeedContours: View {
    private static let line = Color(hex: 0xdedfe3)
    private static let rings = 7
    private static let steps = 72

    // Where the contour maps sit, as fractions of the screen, and how wide their outer ring is.
    private static let hills: [(x: Double, y: Double, reach: Double, phase: Double)] = [
        (0.05, 0.04, 0.34, 0.4),
        (0.98, 0.62, 0.4, 2.1),
        (0.1, 0.97, 0.3, 4.2),
    ]

    let size: CGSize

    var body: some View {
        Canvas { context, size in
            for hill in Self.hills {
                for r in 0..<Self.rings {
                    let radius = Double(r + 1) / Double(Self.rings) * hill.reach * size.width
                    let path = Self.ring(cx: hill.x * size.width, cy: hill.y * size.height, radius: radius, phase: hill.phase + Double(r) * 0.15)
                    context.stroke(path, with: .color(Self.line), lineWidth: 1)
                }
            }
        }
        .frame(width: size.width, height: size.height)
        .allowsHitTesting(false)
    }

    /// A ring around (cx, cy) whose radius wobbles with a few fixed waves, as a contour line does.
    private static func ring(cx: Double, cy: Double, radius: Double, phase: Double) -> Path {
        var path = Path()
        for i in 0...steps {
            let angle = Double(i) / Double(steps) * Double.pi * 2
            let wobble = 1 + 0.12 * sin(angle * 3 + phase) + 0.07 * sin(angle * 5 + phase * 2) + 0.04 * sin(angle * 8 + phase * 3)
            let point = CGPoint(x: cx + cos(angle) * radius * wobble, y: cy + sin(angle) * radius * wobble)
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}
