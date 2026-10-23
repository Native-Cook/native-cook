import SwiftUI
import UIKit

/// The block's portraits and fonts. The portraits are the React Native block's `assets/*.jpg`,
/// copied unmodified into this folder as `DonationFanCarousel-<name>.jpg` (the app bundles every
/// resource into one flat namespace). The type is the system's Avenir Next, which React Native
/// asks for by family name at weights 500, 600 and 400.
enum DonationFanCarouselAssets {
    /// The seven portraits the ten cards share, loaded once.
    private static let portraits: [String: UIImage] = Dictionary(
        uniqueKeysWithValues: ["andre", "clara", "mateo", "mei", "noah", "ruth", "theo"].map { name in
            (name, DonationFanCarouselAssets.load(name))
        }
    )

    /// A portrait by name. A missing file asserts in debug builds instead of drawing nothing.
    static func image(named name: String) -> UIImage {
        guard let image = portraits[name] else {
            assertionFailure("Unknown Donation Fan Carousel portrait: \(name)")
            return UIImage()
        }
        return image
    }

    private static func load(_ name: String) -> UIImage {
        let file = "DonationFanCarousel-\(name).jpg"
        guard let image = UIImage(named: file) else {
            assertionFailure("Missing Donation Fan Carousel portrait: \(file)")
            return UIImage()
        }
        return image
    }

    /// Checks once that the three Avenir Next faces exist; a missing one asserts in debug builds
    /// and falls back to the system font in release builds.
    private static let fontsChecked: Void = {
        for name in [
            DonationFanCarouselTheme.Fonts.medium,
            DonationFanCarouselTheme.Fonts.demiBold,
            DonationFanCarouselTheme.Fonts.regular,
        ] {
            assert(UIFont(name: name, size: 12) != nil, "Missing Donation Fan Carousel font: \(name)")
        }
    }()

    /// A font at a fixed size (`fontSize` in React Native is already multiplied by the stage scale).
    static func font(_ postScriptName: String, size: CGFloat) -> Font {
        _ = fontsChecked
        return .custom(postScriptName, fixedSize: size)
    }
}
