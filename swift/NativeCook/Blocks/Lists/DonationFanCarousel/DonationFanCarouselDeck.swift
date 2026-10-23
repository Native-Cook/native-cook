import SwiftUI

/// The `StyleSheet.absoluteFill` view of the React Native block that holds the ten portraits and,
/// above them all (`zIndex: 130`), the active portrait's keyline and reactions. Portraits are
/// stacked by `zIndex` (100 minus 20 per step of distance); equal values keep the array's order.
struct DonationFanCarouselDeck: View {
    let geometry: DonationFanCarouselGeometry
    let clock: Double
    let page: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(DonationFanCarouselCard.all) { card in
                let pose = DonationFanCarouselPose(
                    distance: Double(card.index) - page,
                    layoutScale: Double(geometry.scale)
                )
                DonationFanCarouselCardView(card: card, pose: pose, geometry: geometry)
                    .zIndex(pose.zIndex)
            }
            DonationFanCarouselActiveBorder(geometry: geometry, clock: clock, page: page)
                .zIndex(DonationFanCarouselTheme.Layout.Deck.activeBorderZ)
        }
        .frame(width: geometry.stageWidth, height: geometry.stageHeight, alignment: .topLeading)
    }
}
