import CoreText
import SwiftUI
import UIKit

/// The bundled font and artwork. Files sit flat in the app bundle under `ConfettiGiftOffer-<name>`.
enum ConfettiGiftOfferAssets {
    /// Barlow Condensed ExtraBold (SIL OFL 1.1): the reference's heavy condensed headlines.
    static let headlineFontName = "BarlowCondensed-ExtraBold"

    /// Registers the font once for the process. Touch it before the first text is built.
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "ConfettiGiftOffer-BarlowCondensed-ExtraBold", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    /// Miso's four poses (art.tsx:7-12), decoded once.
    enum Pose: String {
        case hearts = "raccoon-hearts"
        case worried = "raccoon-worried"
        case love = "raccoon-love"
        case tail = "raccoon-tail"
    }

    private static let images: [Pose: UIImage] = {
        var loaded: [Pose: UIImage] = [:]
        for pose in [Pose.hearts, .worried, .love, .tail] {
            guard let url = Bundle.main.url(forResource: "ConfettiGiftOffer-\(pose.rawValue)", withExtension: "webp"),
                  let image = UIImage(contentsOfFile: url.path) else { continue }
            // Decode now, not on the first draw of the scene that shows the picture.
            loaded[pose] = image.preparingForDisplay() ?? image
        }
        return loaded
    }()

    static func image(_ pose: Pose) -> UIImage? {
        images[pose]
    }
}
