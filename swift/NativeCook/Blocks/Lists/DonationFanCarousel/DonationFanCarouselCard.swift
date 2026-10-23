import SwiftUI

/// One portrait slot of the fan: `cards` in the React Native block. `index` is the slot's position
/// along the fan (the page that makes it the active portrait); the three donors repeat at both ends.
struct DonationFanCarouselCard: Identifiable {
    let id: String
    let index: Int
    /// The portrait's file name without prefix and extension.
    let portrait: String

    static let all: [DonationFanCarouselCard] = [
        DonationFanCarouselCard(id: "mateo-left", index: -3, portrait: "mateo"),
        DonationFanCarouselCard(id: "andre-left", index: -2, portrait: "andre"),
        DonationFanCarouselCard(id: "ruth-left", index: -1, portrait: "ruth"),
        DonationFanCarouselCard(id: "noah", index: 0, portrait: "noah"),
        DonationFanCarouselCard(id: "mei", index: 1, portrait: "mei"),
        DonationFanCarouselCard(id: "theo", index: 2, portrait: "theo"),
        DonationFanCarouselCard(id: "clara", index: 3, portrait: "clara"),
        DonationFanCarouselCard(id: "andre-right", index: 4, portrait: "andre"),
        DonationFanCarouselCard(id: "mateo-right", index: 5, portrait: "mateo"),
        DonationFanCarouselCard(id: "ruth-right", index: 6, portrait: "ruth"),
    ]
}
