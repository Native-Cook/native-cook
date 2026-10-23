import Foundation

enum DonationFanCarouselAccessibility {
    static func value(for page: Double) -> String {
        let index = Int(max(0, min(3, DonationFanCarouselMath.jsRound(page))))
        switch index {
        case 0:
            return "Dorian Petters, $100 donation, 10 minutes ago, portrait 1 of 4"
        case 3:
            return "Emmy Stockley, $200 donation, 11 minutes ago, portrait 4 of 4"
        default:
            return "Portrait \(index + 1) of 4"
        }
    }
}
