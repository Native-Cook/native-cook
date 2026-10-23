import SwiftUI

/// One of the two metadata blocks under the fan (`styles.metadata`): the donor's name over
/// "$ donation • time ago", centred across the stage. The two blocks sit on top of each other and
/// cross-fade with the page.
struct DonationFanCarouselMetadata: View {
    private typealias Metadata = DonationFanCarouselTheme.Layout.Metadata
    private typealias Fonts = DonationFanCarouselTheme.Fonts
    private typealias Colors = DonationFanCarouselTheme.Colors

    let name: String
    let amount: String
    let time: String
    let opacity: Double
    let geometry: DonationFanCarouselGeometry

    /// React Native's `allowFontScaling` (on by default) multiplies `fontSize` and `lineHeight` by the
    /// user's text size relative to the default; 1 at the default size.
    @ScaledMetric(relativeTo: .body) private var textScale: CGFloat = 1

    var body: some View {
        let scale = geometry.scale
        VStack(spacing: 0) {
            Text(name)
                .font(DonationFanCarouselAssets.font(Fonts.demiBold, size: Metadata.nameSize * scale * textScale))
                .tracking(Metadata.nameTracking)
                .foregroundStyle(Colors.ink)
                .lineLimit(1)
                .fixedSize()
                .frame(height: Metadata.nameLine * scale * textScale)
            Text(detail)
                .font(DonationFanCarouselAssets.font(Fonts.regular, size: Metadata.detailSize * scale * textScale))
                .tracking(Metadata.detailTracking)
                .lineLimit(1)
                .fixedSize()
                .frame(height: Metadata.detailLine * scale * textScale)
        }
        .frame(width: geometry.stageWidth)
        .offset(y: Metadata.top * scale)
        .opacity(opacity)
    }

    /// `<Text><Text style={detailAmount}>$100 donation • </Text><Text style={detailTime}>10m ago</Text></Text>`.
    private var detail: AttributedString {
        var amountRun = AttributedString(amount)
        amountRun.foregroundColor = Colors.detailAmount
        var timeRun = AttributedString(time)
        timeRun.foregroundColor = Colors.secondary
        amountRun.append(timeRun)
        return amountRun
    }
}
