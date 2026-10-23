import SwiftUI

private let ink = Color.white
private let stroke = 22.0
// The space between two segments, measured between their rounded ends.
private let gap = 6.0
private let glowBlur = 9.0
private let glowAlpha = 0.5

/// A ring split into rounded segments, one per level, whose lengths morph between two sets of shares.
struct StressDotGridDonutLevelDonut: View {
    var size: Double
    var colors: [Color]
    /// Each segment's share in the two views; the donut moves between them as `progress` goes 0 → 1.
    var from: [Double]
    var to: [Double]
    var progress: Double
    var label: String

    var body: some View {
        Canvas { context, _ in
            for (path, color) in zip(paths(), colors) {
                context.drawLayer { glow in
                    glow.addFilter(.blur(radius: glowBlur))
                    glow.stroke(path, with: .color(color.opacity(glowAlpha)), style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                }
                context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: stroke, lineCap: .round))
            }
        }
        .frame(width: size, height: size)
        // Skia draws into the view's own surface, so the glow stops at its edges.
        .clipped()
        .overlay {
            Text(label)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(ink)
                .frame(height: 20)
                .allowsHitTesting(false)
        }
    }

    private func paths() -> [Path] {
        let radius = (size - stroke) / 2 - glowBlur / 2
        let centre = CGPoint(x: size / 2, y: size / 2)
        // Round ends reach half a stroke past each segment, so the gap in degrees counts them in.
        let gapDegrees = (gap + stroke) / radius * 180 / .pi
        var shares: [Double] = []
        var total = 0.0
        for i in from.indices {
            let share = max(0, from[i] + (to[i] - from[i]) * progress)
            shares.append(share)
            total += share
        }
        let free = 360 - gapDegrees * Double(from.count)
        var start = -90 + gapDegrees / 2
        return shares.map { share in
            // A segment with almost nothing left is still a dot, as the round ends draw it.
            let sweep = max(0.01, total > 0 ? share / total * free : 0)
            var path = Path()
            path.addRelativeArc(center: centre, radius: radius, startAngle: .degrees(start), delta: .degrees(sweep))
            start += sweep + gapDegrees
            return path
        }
    }
}
