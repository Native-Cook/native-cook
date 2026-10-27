import SwiftUI
import UIKit

/// The bundled artwork: the glossy split day-and-night icon (an invented app's icon).
enum DaylightIconRevealArt {
    static let name = "DaylightIconReveal-daylight-icon"

    /// Decoded once, before the first frame that needs it.
    static let image: UIImage = {
        guard
            let url = Bundle.main.url(forResource: name, withExtension: "webp"),
            let source = UIImage(contentsOfFile: url.path)
        else {
            assertionFailure("Missing Daylight Icon Reveal artwork")
            return UIImage()
        }
        return source.preparingForDisplay() ?? source
    }()
}

/// The top of the screen: the icon rising from below and settling, with the light that crosses it
/// and the faint day and night glow behind it (`hero` in the React Native file).
struct DaylightIconRevealHero: View {
    /// Loop time in seconds.
    let t: Double
    let reduced: Bool
    /// The window's size in points (`useWindowDimensions`).
    let width: CGFloat
    let height: CGFloat
    let heroHeight: CGFloat
    /// Large text sizes (`fontScale > 1.4`): a smaller icon centred in a shorter hero.
    let compact: Bool
    let safeTop: CGFloat

    private typealias S = DaylightIconRevealScript

    var body: some View {
        ZStack(alignment: .topLeading) {
            glow
            icon
        }
        .frame(width: width, height: heroHeight, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    // MARK: Icon

    private var icon: some View {
        let artWidth = compact ? width * 0.8 : width
        let artHeight = compact ? height * 0.8 : height
        let left = (width - artWidth) / 2
        // Compact: the icon is centred in the hero, below the status bar (0.35836 is where its
        // top edge sits in the plane's units).
        let top = compact ? (heroHeight + safeTop) / 2 - artHeight * 0.35836 : 0
        let plane = S.imagePlane(S.iconTime(t, reduced: reduced), width: artWidth, height: artHeight)
        let recede = S.recede(t, reduced: reduced)
        // CSS order: the filter works on the view in its own size, the scale comes after it.
        return artwork
            .compositingGroup()
            // React Native's CSS blur draws at about half SwiftUI's radius on iOS (matched on device).
            .blur(radius: recede.blur * 0.5)
            .scaleEffect(recede.scale)
            .opacity(recede.opacity)
            .frame(width: S.imageSize, height: S.imageSize)
            .opacity(S.iconOpacity(t, reduced: reduced))
            .projectionEffect(plane)
            .offset(x: left, y: top)
    }

    /// The image and the sweep of light, 256 x 256 pt (`IMAGE_SIZE`) before the plane transform.
    private var artwork: some View {
        ZStack(alignment: .topLeading) {
            Image(uiImage: DaylightIconRevealArt.image)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .frame(width: S.imageSize, height: S.imageSize)
            glint
        }
        .frame(width: S.imageSize, height: S.imageSize, alignment: .topLeading)
    }

    /// One stripe of light, 96 pt wide and tilted 18 degrees, sliding across the icon's body and
    /// clipped to its rounded outline (radius `0.235` of the body's width).
    private var glint: some View {
        let sweep = S.glint(t, reduced: reduced)
        let clipWidth = S.imageSize * S.bodyWidth
        let clipHeight = S.imageSize * S.bodyHeight
        let white = Color.white
        return LinearGradient(
            stops: [
                .init(color: white.opacity(0), location: 0),
                .init(color: white.opacity(Double(0x40) / 255), location: 0.3),
                .init(color: white.opacity(Double(0xB8) / 255), location: 0.5),
                .init(color: white.opacity(Double(0x40) / 255), location: 0.7),
                .init(color: white.opacity(0), location: 1),
            ],
            startPoint: .leading,
            endPoint: .trailing
        )
        .frame(width: 96, height: clipHeight + 120)
        .rotationEffect(.degrees(18))
        .offset(x: sweep.translateX, y: -60)
        .opacity(sweep.opacity)
        .frame(width: clipWidth, height: clipHeight, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: clipWidth * 0.235, style: .circular))
        .offset(x: S.imageSize * S.bodyX, y: S.imageSize * S.bodyY)
        .allowsHitTesting(false)
    }

    // MARK: Glow

    /// Day on the left, night on the right: the icon's two lights spill faintly onto the page.
    /// Two ellipses with a radial fade from the colour (42% and 34%) to nothing.
    private var glow: some View {
        // Where the settled icon's centre lands (top 0.242 + half its 0.232 height).
        let centerY = compact ? heroHeight / 2 : height * 0.358
        return ZStack(alignment: .topLeading) {
            ellipse(color: DaylightIconRevealPalette.rgb(0xFFD35A), opacity: 0.42, centerX: width * 0.36, centerY: centerY)
            ellipse(color: DaylightIconRevealPalette.rgb(0x5B4BE0), opacity: 0.34, centerX: width * 0.64, centerY: centerY)
        }
        .frame(width: width, height: heroHeight, alignment: .topLeading)
        .clipped()
        .compositingGroup()
        .opacity(S.glow(t, reduced: reduced))
        .allowsHitTesting(false)
    }

    private func ellipse(color: Color, opacity: Double, centerX: CGFloat, centerY: CGFloat) -> some View {
        let radiusX = width * 0.42
        let radiusY = width * 0.36
        return Circle()
            .fill(
                RadialGradient(
                    colors: [color.opacity(opacity), color.opacity(0)],
                    center: .center,
                    startRadius: 0,
                    endRadius: radiusX
                )
            )
            .frame(width: radiusX * 2, height: radiusX * 2)
            .scaleEffect(x: 1, y: radiusY / radiusX)
            .position(x: centerX, y: centerY)
    }
}
