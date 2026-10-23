import SwiftUI

/// `styles.screen` in the React Native block: the white screen (`overflow: hidden`) with the stage
/// centred in it. It draws whatever `clock` and `page` say and holds no state of its own.
struct DonationFanCarouselScreen: View {
    let geometry: DonationFanCarouselGeometry
    let size: CGSize
    let clock: Double
    let page: Double
    let accessibilityDisabled: Bool
    let onAccessibilityAdjust: (AccessibilityAdjustmentDirection) -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            DonationFanCarouselTheme.Colors.background
            DonationFanCarouselStage(geometry: geometry, clock: clock, page: page)
                .offset(x: geometry.stageLeft, y: geometry.stageTop)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Donation portraits")
        .accessibilityValue(DonationFanCarouselAccessibility.value(for: page))
        .accessibilityAdjustableAction { direction in
            guard !accessibilityDisabled else { return }
            onAccessibilityAdjust(direction)
        }
    }
}
