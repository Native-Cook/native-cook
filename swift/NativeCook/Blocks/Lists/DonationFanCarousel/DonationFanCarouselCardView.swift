import SwiftUI

/// `CardSlot` in the React Native block: a square portrait with rounded corners
/// at the fan's centre, moved, tilted and shrunk by its distance from the active page.
struct DonationFanCarouselCardView: View {
    let card: DonationFanCarouselCard
    let pose: DonationFanCarouselPose
    let geometry: DonationFanCarouselGeometry

    var body: some View {
        let size = geometry.cardSize
        Image(uiImage: DonationFanCarouselAssets.image(named: card.portrait))
            .resizable()
            .scaledToFill()
            .frame(width: size, height: size)
            .clipped()
            .background(DonationFanCarouselTheme.Colors.cardFill)
            .clipShape(RoundedRectangle(cornerRadius: geometry.cardRadius, style: .circular))
            // RN clips this card's own shadow with overflow: hidden. Keep the same silhouette.
            // `transform: [translateX, translateY, rotate, scale]` reads outermost first, so the
            // scale is applied first and the translation last.
            .scaleEffect(CGFloat(pose.scale))
            .rotationEffect(.degrees(pose.rotation))
            .opacity(pose.opacity)
            .offset(
                x: geometry.deckLeft + CGFloat(pose.x),
                y: geometry.deckTop + CGFloat(pose.y)
            )
            .allowsHitTesting(false)
    }
}
