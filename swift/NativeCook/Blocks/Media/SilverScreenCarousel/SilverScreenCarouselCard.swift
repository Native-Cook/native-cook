import SwiftUI

/// `ProfileCard` of the React Native file: a dark frame holding a photograph, the profile's code,
/// name and description, and a colour bar. Its position decides how far the photograph lags behind
/// its frame, how dark the frame is, and whether the shutter flashes over it.
struct SilverScreenCarouselCard: View {
    let profile: SilverScreenCarouselProfile
    /// `cardWidth`: the frame's outer width, border and padding included.
    let width: CGFloat
    /// `minHeight: width * 364 / 342`.
    let minHeight: CGFloat
    /// The row's position in cards.
    let position: Double
    /// `selected === index`.
    let active: Bool
    /// The shutter's opacity while this is the active card.
    let flash: Double
    let reduced: Bool
    let fontScale: CGFloat

    /// `PARALLAX`: the photograph is 22 pt wider than its box on each side and slides by up to that.
    private static let parallax: CGFloat = 22

    var body: some View {
        // `card` style: border 1 and padding 7, so the content starts 8 pt in.
        let contentWidth = width - 16
        // `imageWidth = width - 15` (one point wider than the content box, as in the React Native file).
        let imageWidth = width - 15
        let imageHeight = imageWidth * 257 / 327
        let distance = Double(profile.index) - position
        VStack(alignment: .leading, spacing: 0) {
            photoBox(imageWidth: imageWidth, imageHeight: imageHeight, distance: distance)
            SilverScreenCarouselCardDetails(profile: profile, contentWidth: contentWidth, fontScale: fontScale)
        }
        .frame(width: contentWidth, alignment: .topLeading)
        .padding(8)
        .frame(width: width, alignment: .topLeading)
        .frame(minHeight: minHeight, maxHeight: .infinity, alignment: .top)
        .background(
            RoundedRectangle(cornerRadius: 15)
                .fill(SilverScreenCarouselPalette.cardFill)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 15)
                .strokeBorder(SilverScreenCarouselPalette.cardBorder, lineWidth: 1)
        )
        .overlay(shade(distance: distance))
        // `Math.abs(distance) < 4 ? 1 : 0`
        .opacity(abs(distance) < 4 ? 1 : 0)
    }

    /// The rounded photograph with its shutter flash and hairline edge.
    private func photoBox(imageWidth: CGFloat, imageHeight: CGFloat, distance: Double) -> some View {
        // The photograph drifts a little slower than its frame, like a print sliding under glass.
        let lag = max(-1, min(1, distance))
        let shift = reduced ? 0 : CGFloat(lag) * -Self.parallax
        return Color.clear
            .frame(width: imageWidth, height: imageHeight)
            .overlay(
                Image(uiImage: SilverScreenCarouselAssets.photo(profile.image))
                    .resizable()
                    .scaledToFill()
                    .frame(width: imageWidth + Self.parallax * 2, height: imageHeight)
                    .clipped()
                    .offset(x: shift)
            )
            .overlay(
                Rectangle()
                    .fill(SilverScreenCarouselPalette.shutter)
                    .opacity(active ? flash : 0)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .strokeBorder(SilverScreenCarouselPalette.photoEdge, lineWidth: 1)
            )
            .background(SilverScreenCarouselPalette.photoBackdrop)
            .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    /// Frames waiting at the edges are a step darker than the one in the gate. The overlay sits
    /// inside the 1 pt border, like an absolutely filled view.
    private func shade(distance: Double) -> some View {
        return RoundedRectangle(cornerRadius: 15)
            .fill(Color.black)
            .opacity(min(1, abs(distance)) * 0.22)
            .padding(1)
            .allowsHitTesting(false)
    }
}

/// The part of a card that does not depend on the row's position: code, name, description and
/// the colour bar. Kept apart so a frame of the drift does not lay the text out again.
struct SilverScreenCarouselCardDetails: View {
    let profile: SilverScreenCarouselProfile
    let contentWidth: CGFloat
    let fontScale: CGFloat

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // `profileDetails`: a row with a 12 pt gap, centred, padded 5 / 22 / 20.
            HStack(alignment: .center, spacing: 12) {
                SilverScreenCarouselLabel(text: profile.code, style: .code, fontScale: fontScale)
                    .fixedSize()
                VStack(alignment: .leading, spacing: 4) {
                    SilverScreenCarouselLabel(text: profile.name, style: .profileName, fontScale: fontScale)
                    SilverScreenCarouselLabel(text: profile.description, style: .profileDescription, fontScale: fontScale)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 5)
            .padding(.top, 22)
            .padding(.bottom, 20)
            // `swatch`: 5 pt high, radius 3, margins 5 and 7, three evenly spaced stops left to right.
            LinearGradient(colors: profile.colors, startPoint: .leading, endPoint: .trailing)
                .frame(height: 5)
                .clipShape(RoundedRectangle(cornerRadius: 3))
                .padding(.horizontal, 5)
                .padding(.bottom, 7)
        }
        .frame(width: contentWidth, alignment: .topLeading)
    }
}
