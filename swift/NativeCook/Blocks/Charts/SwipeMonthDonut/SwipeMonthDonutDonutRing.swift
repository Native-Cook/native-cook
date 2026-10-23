import SwiftUI

/// The month's spending as a ring of round-capped segments with gaps, drawn clockwise from the top.
/// Each segment's start and sweep come from the shares, not from a stored path, so a segment that
/// leaves shrinks into its neighbour's end and one that arrives grows out of it; one shorter than
/// its round caps is drawn as a dot that shrinks to nothing instead.
struct SwipeMonthDonutDonutRing: View {
    var size: Double
    var stroke: Double
    /// The visible gap between two segments, in points along the ring.
    var gap: Double
    /// How far a tapped segment moves out.
    var pop: Double
    var colors: [Color]
    /// The share of each category, interpolated by the pager.
    var shares: [Double]
    /// The tapped category, or -1, and how far it is out (0 to 1).
    var focus: Int
    var out: Double

    var body: some View {
        Canvas { context, _ in
            // The canvas leaves room round the ring for a segment that pops out.
            let inset = stroke / 2 + pop + 1
            let radius = size / 2 - inset
            let centre = CGPoint(x: size / 2, y: size / 2)
            // Half a gap plus a round cap, as an angle: what each end of a segment gives up.
            let trim = (stroke / 2 + gap / 2) / radius * 180 / .pi
            var at = -90.0
            for (i, share) in shares.enumerated() where i < colors.count {
                let raw = share * 360
                let sweep = raw - trim * 2
                let mid = (at + raw / 2) * .pi / 180
                let shift = i == focus ? pop * out : 0
                // Shorter than its two caps: a dot, as wide as there is room for.
                let width = stroke * SwipeMonthDonutData.clamp01(raw / (trim * 2))
                let start = sweep > 0 ? at + trim : at + raw / 2
                at += raw
                guard width > 0.2 else { continue }
                var path = Path()
                path.addRelativeArc(center: centre, radius: radius, startAngle: .degrees(start), delta: .degrees(max(sweep, 0.01)))
                var segment = context
                segment.translateBy(x: cos(mid) * shift, y: sin(mid) * shift)
                segment.stroke(path, with: .color(colors[i]), style: StrokeStyle(lineWidth: width, lineCap: .round))
            }
        }
        .frame(width: size, height: size)
    }
}
