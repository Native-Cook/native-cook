import SwiftUI

/// The coin of `ReactionMarks.tsx`: a lime disc with a pale inner ring and three seeds. It follows
/// the fan slot of the donor it belongs to (the first donor before 2500 ms of the loop, the fourth
/// after) and scales in and out with `coinAt`.
struct DonationFanCarouselCoin: View {
    private typealias Coin = DonationFanCarouselTheme.Coin
    private typealias Colors = DonationFanCarouselTheme.Colors
    private typealias Fan = DonationFanCarouselTheme.Fan

    let clock: Double
    let page: Double
    let scale: CGFloat

    var body: some View {
        let amount = DonationFanCarouselMotion.coinAt(clock)
        let t = DonationFanCarouselMotion.wrapped(clock)
        let selected: Double = t < Coin.secondHalfStartMs ? 0 : 3
        let distance = selected - page
        let absolute = min(3, abs(distance))
        let direction: Double = distance < 0 ? -1 : 1
        let x = DonationFanCarouselMath.fan(absolute, Fan.x, clamp: false) * direction * Double(scale)
        let y = DonationFanCarouselMath.fan(absolute, Fan.y, clamp: false) * Double(scale)
        let cardScale = DonationFanCarouselMath.fan(absolute, Fan.scale, clamp: false)
        let appear = DonationFanCarouselMath.interpolate(amount, input: [0, 1], output: [Coin.scaleFrom, 1])

        disc
            .shadow(
                color: Colors.coinShadow,
                radius: Coin.shadowRadius,
                x: 0,
                y: Coin.shadowOffsetY
            )
            .scaleEffect(CGFloat(cardScale * appear))
            .offset(x: CGFloat(x), y: CGFloat(y))
            .opacity(amount)
            .allowsHitTesting(false)
    }

    /// `styles.coin` with `coinInner` and the seeds inside it.
    private var disc: some View {
        let side = Coin.size * scale
        // `width: '72%'` of the coin's content box, which is inside its 1 pt border.
        let inner = Coin.innerFraction * (side - Coin.border * 2)
        // A radius larger than half the side is clamped, as React Native does.
        let innerRadius = min(Coin.innerRadius * scale, inner / 2)
        return ZStack {
            Circle().fill(Colors.accent)
            Circle().strokeBorder(Colors.accentDark, lineWidth: Coin.border)
            ZStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: innerRadius, style: .circular)
                    .strokeBorder(Colors.coinInnerBorder, lineWidth: Coin.innerBorder)
                // `alignItems: 'flex-end'`, `gap: 2`, `paddingBottom: 3`, inside the 1 pt border.
                HStack(alignment: .bottom, spacing: Coin.innerGap) {
                    ForEach(Array(Coin.seedHeights.enumerated()), id: \.offset) { _, height in
                        RoundedRectangle(
                            cornerRadius: min(Coin.seedRadius, Coin.seedWidth * scale / 2),
                            style: .circular
                        )
                        .fill(Colors.coinSeed)
                        .frame(width: Coin.seedWidth * scale, height: height * scale)
                    }
                }
                .padding(.bottom, Coin.innerPaddingBottom + Coin.innerBorder)
            }
            .frame(width: inner, height: inner)
        }
        .frame(width: side, height: side)
    }
}
