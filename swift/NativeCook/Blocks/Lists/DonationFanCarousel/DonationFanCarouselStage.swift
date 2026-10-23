import SwiftUI

/// `styles.stage` in the React Native block: the white 748 : 462 stage with the header, the fan
/// and the two metadata blocks, in that order (later siblings draw over earlier ones).
struct DonationFanCarouselStage: View {
    let geometry: DonationFanCarouselGeometry
    let clock: Double
    let page: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            DonationFanCarouselHeader(geometry: geometry)
            DonationFanCarouselDeck(geometry: geometry, clock: clock, page: page)
            // `interpolate(page, [0, 0.55, 1.1], [1, 0.35, 0], Extrapolation.CLAMP)`.
            DonationFanCarouselMetadata(
                name: "Dorian Petters",
                amount: "$100 donation • ",
                time: "10m ago",
                opacity: DonationFanCarouselMath.interpolate(
                    page,
                    input: [0, 0.55, 1.1],
                    output: [1, 0.35, 0],
                    clamp: true
                ),
                geometry: geometry
            )
            // `interpolate(page, [1.9, 2.45, 3], [0, 0.35, 1], Extrapolation.CLAMP)`.
            DonationFanCarouselMetadata(
                name: "Emmy Stockley",
                amount: "$200 donation • ",
                time: "11m ago",
                opacity: DonationFanCarouselMath.interpolate(
                    page,
                    input: [1.9, 2.45, 3],
                    output: [0, 0.35, 1],
                    clamp: true
                ),
                geometry: geometry
            )
        }
        .frame(width: geometry.stageWidth, height: geometry.stageHeight, alignment: .topLeading)
        .background(DonationFanCarouselTheme.Colors.background)
    }
}
