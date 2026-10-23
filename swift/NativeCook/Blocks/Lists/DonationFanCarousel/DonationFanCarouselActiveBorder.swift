import SwiftUI

/// `styles.activeBorder` in the React Native block: a box over the active portrait with the lime
/// keyline (hidden while the fan travels) and the hearts and coin on top of it.
struct DonationFanCarouselActiveBorder: View {
    let geometry: DonationFanCarouselGeometry
    let clock: Double
    let page: Double

    var body: some View {
        ZStack {
            // `borderWidth: layout.deck.border * scale` is drawn inside the box.
            RoundedRectangle(cornerRadius: geometry.cardRadius, style: .circular)
                .strokeBorder(
                    DonationFanCarouselTheme.Colors.accent,
                    lineWidth: DonationFanCarouselTheme.Layout.Deck.border * geometry.scale
                )
                .opacity(DonationFanCarouselMotion.borderAt(clock))
            DonationFanCarouselReactionMarks(clock: clock, page: page, scale: geometry.scale)
        }
        .frame(width: geometry.cardSize, height: geometry.cardSize)
        .offset(x: geometry.deckLeft, y: geometry.deckTop)
        .allowsHitTesting(false)
    }
}
