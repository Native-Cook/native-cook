import SwiftUI

// The rest of the belt, reduced to an index: small, low contrast, and the only place where more
// than one ball is visible at once. Every thumbnail places itself from its distance to the
// centre of the loop. Mirrors BallStrip.tsx.

enum ChooseYourBallStripMetrics {
    /// How many slots either side of the centre are drawn at all.
    static let reach: Double = 3
    /// Distance between two thumbnails.
    static let stride: CGFloat = 40
    /// Diameter of a thumbnail, and of the selected one.
    static let thumb: CGFloat = 26
    static let selected: CGFloat = 36
    /// Extra touch area around a thumbnail.
    static let hitSlop: CGFloat = 10

    static func scale(distance: Double) -> CGFloat {
        let t = min(max(distance, 0), 1)
        return selected / thumb + (1 - selected / thumb) * t
    }

    static func opacity(distance: Double) -> Double {
        if distance <= 1 { return 1 + (0.42 - 1) * max(distance, 0) }
        return max(0, 0.42 * (1 - (distance - 1) / (reach - 1)))
    }
}

struct ChooseYourBallStrip: View {
    let carousel: ChooseYourBallCarousel

    var body: some View {
        ZStack {
            ForEach(Array(carousel.order.enumerated()), id: \.element) { index, ball in
                ChooseYourBallStripThumb(carousel: carousel, ball: ball, index: index)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: ChooseYourBallStripMetrics.selected)
    }
}

private struct ChooseYourBallStripThumb: View {
    let carousel: ChooseYourBallCarousel
    let ball: ChooseYourBallID
    let index: Int

    var body: some View {
        let slot = ChooseYourBallGeometry.cyclicDelta(index: Double(index), offset: carousel.offset, total: carousel.total)
        let distance = abs(slot)
        let opacity = ChooseYourBallStripMetrics.opacity(distance: distance)

        // Further than `reach` from the centre a thumbnail is not drawn, and is no button either.
        if opacity >= 0.01 {
            ChooseYourBallGraphic(ball: ball, size: ChooseYourBallStripMetrics.thumb, showButton: false)
                .scaleEffect(ChooseYourBallStripMetrics.scale(distance: distance))
                .opacity(opacity)
                .offset(x: slot * ChooseYourBallStripMetrics.stride)
                .accessibilityElement()
                .accessibilityLabel(ball.spec.label)
                .accessibilityAddTraits(index == carousel.preview ? [.isButton, .isSelected] : .isButton)
                .accessibilityAction { carousel.select(slot: index) }
        }
    }
}
