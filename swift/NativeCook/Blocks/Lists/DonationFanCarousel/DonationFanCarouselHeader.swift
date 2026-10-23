import SwiftUI

/// `styles.header` in the React Native block: "Latest donations" on the left and the counter
/// on the right, in one row that is inset 59 pt on both sides.
struct DonationFanCarouselHeader: View {
    private typealias Header = DonationFanCarouselTheme.Layout.Header
    private typealias Fonts = DonationFanCarouselTheme.Fonts

    let geometry: DonationFanCarouselGeometry

    /// React Native's `allowFontScaling` (on by default) multiplies `fontSize` and `lineHeight` by the
    /// user's text size relative to the default; 1 at the default size.
    @ScaledMetric(relativeTo: .body) private var textScale: CGFloat = 1

    var body: some View {
        let scale = geometry.scale
        let lineHeight = Header.lineHeight * scale * textScale
        HStack(spacing: 0) {
            Text("Latest donations")
                .font(DonationFanCarouselAssets.font(Fonts.medium, size: Header.fontSize * scale * textScale))
                .tracking(Header.headingTracking)
                .foregroundStyle(DonationFanCarouselTheme.Colors.ink)
                .lineLimit(1)
                .fixedSize()
                .frame(height: lineHeight)
            Spacer(minLength: 0)
            Text("04/24")
                .font(DonationFanCarouselAssets.font(Fonts.medium, size: Header.counterSize * scale * textScale))
                .tracking(Header.counterTracking)
                .foregroundStyle(DonationFanCarouselTheme.Colors.ink)
                .lineLimit(1)
                .fixedSize()
                .frame(height: lineHeight)
                // `top: -1.3` moves the counter up without changing the row.
                .offset(y: Header.counterTop)
        }
        .frame(width: geometry.stageWidth - Header.side * scale * 2, height: lineHeight)
        .offset(x: Header.side * scale, y: Header.top * scale)
    }
}
