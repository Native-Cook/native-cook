import SwiftUI

/// `Heart` in `ReactionMarks.tsx`: one of three hearts that rise and fade in over the active
/// portrait. It is laid out centred on the active portrait's centre and moved from there.
struct DonationFanCarouselHeart: View {
    private typealias Hearts = DonationFanCarouselTheme.Hearts

    let index: Int
    /// `heartsAt(clock)`.
    let amount: Double
    let scale: CGFloat

    var body: some View {
        let placement = Hearts.placements[index]
        // `Math.max(0, Math.min(1, amount * 1.35 - index * 0.08))`.
        let delayed = max(0, min(1, amount * Hearts.staggerGain - Double(index) * Hearts.staggerStep))
        let side = CGFloat(placement.size) * scale
        DonationFanCarouselHeartArtwork()
            .frame(width: side, height: side)
            .shadow(
                color: DonationFanCarouselTheme.Colors.heartShadow,
                radius: Hearts.shadowRadius,
                x: 0,
                y: Hearts.shadowOffsetY
            )
            .scaleEffect(
                CGFloat(DonationFanCarouselMath.interpolate(delayed, input: [0, 1], output: [Hearts.scaleFrom, 1]))
            )
            .rotationEffect(.degrees(placement.rotate))
            .offset(
                x: CGFloat(placement.x) * scale,
                y: CGFloat(placement.y + placement.rise * (1 - delayed)) * scale
            )
            .opacity(delayed)
            .allowsHitTesting(false)
    }
}
