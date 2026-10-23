import SwiftUI

/// `ReactionMarks` in the React Native block: the three hearts and the coin, centred on the active
/// portrait (a zero-size layer at its centre, as in `styles.layer`) and drawn above its keyline.
struct DonationFanCarouselReactionMarks: View {
    let clock: Double
    let page: Double
    let scale: CGFloat

    var body: some View {
        let hearts = DonationFanCarouselMotion.heartsAt(clock)
        ZStack {
            ForEach(0..<3, id: \.self) { index in
                DonationFanCarouselHeart(index: index, amount: hearts, scale: scale)
            }
            DonationFanCarouselCoin(clock: clock, page: page, scale: scale)
        }
        .frame(width: 0, height: 0)
        .allowsHitTesting(false)
    }
}
