import Foundation

/// Where one portrait sits in the fan: the animated style of `CardSlot` in the React Native block.
/// `distance` is the card's index minus the page; the transform is a translation, then a rotation,
/// then a scale about the card's centre, applied innermost first.
struct DonationFanCarouselPose {
    let x: Double
    let y: Double
    let scale: Double
    let rotation: Double
    let opacity: Double
    let zIndex: Double

    init(distance: Double, layoutScale: Double) {
        typealias Fan = DonationFanCarouselTheme.Fan
        let absolute = min(3, abs(distance))
        let direction: Double = distance < 0 ? -1 : 1
        x = DonationFanCarouselMath.fan(absolute, Fan.x, clamp: true) * direction * layoutScale
        y = DonationFanCarouselMath.fan(absolute, Fan.y, clamp: true) * layoutScale
        scale = DonationFanCarouselMath.fan(absolute, Fan.scale, clamp: true)
        rotation = DonationFanCarouselMath.fan(absolute, Fan.rotate, clamp: true) * direction
        opacity = absolute >= 3 ? max(0, 1 - (absolute - 3) * 4) : 1
        zIndex = DonationFanCarouselMath.jsRound(100 - absolute * 20)
    }
}
