import CoreText
import ImageIO
import SwiftUI
import UIKit

/// One film profile: `PROFILES` of the React Native file, copy character for character.
struct SilverScreenCarouselProfile: Identifiable {
    let index: Int
    let id: String
    let code: String
    let name: String
    let description: String
    /// The photograph's name without the `SilverScreenCarousel-` prefix and the extension.
    let image: String
    /// The three stops of the colour bar under the card.
    let colors: [Color]
}

enum SilverScreenCarouselProfiles {
    static let all: [SilverScreenCarouselProfile] = [
        SilverScreenCarouselProfile(
            index: 0, id: "ember", code: "EM", name: "Ember chrome",
            description: "Bold reds. Clear blue skies.", image: "red-geometry",
            colors: [
                SilverScreenCarouselPalette.rgb(0x3B79B9),
                SilverScreenCarouselPalette.rgb(0xF6BA72),
                SilverScreenCarouselPalette.rgb(0xC74728),
            ]),
        SilverScreenCarouselProfile(
            index: 1, id: "afternoon", code: "AF", name: "Afternoon film",
            description: "Warm light. Soft green shadows.", image: "coastal-afternoon",
            colors: [
                SilverScreenCarouselPalette.rgb(0x38543B),
                SilverScreenCarouselPalette.rgb(0xA6B092),
                SilverScreenCarouselPalette.rgb(0xEFB88A),
            ]),
        SilverScreenCarouselProfile(
            index: 2, id: "silver", code: "SV", name: "Silver midnight",
            description: "Deep blacks. Luminous grain.", image: "midnight-stage",
            colors: [
                SilverScreenCarouselPalette.rgb(0x252728),
                SilverScreenCarouselPalette.rgb(0x7E8383),
                SilverScreenCarouselPalette.rgb(0xE6E7E3),
            ]),
        SilverScreenCarouselProfile(
            index: 3, id: "salt", code: "SA", name: "Salt air",
            description: "Cool blues. An honest portrait.", image: "salt-air",
            colors: [
                SilverScreenCarouselPalette.rgb(0x343F65),
                SilverScreenCarouselPalette.rgb(0x8294A8),
                SilverScreenCarouselPalette.rgb(0xE9E3D6),
            ]),
    ]
}

/// The bundled photographs and the serif font, loaded once.
enum SilverScreenCarouselAssets {
    /// The PostScript name of Source Serif 4 Regular (`useFonts({ 'SourceSerif4-Regular': ... })`).
    static let serifName = "SourceSerif4-Regular"

    /// Registers the font file with Core Text for this process; touching it once is enough.
    static let registered: Bool = {
        if let url = Bundle.main.url(forResource: "SilverScreenCarousel-SourceSerif4-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        return true
    }()

    /// The four photographs, decoded at their own size so no frame has to decode one.
    private static let photos: [String: UIImage] = {
        var loaded: [String: UIImage] = [:]
        for profile in SilverScreenCarouselProfiles.all {
            if let image = load(profile.image) {
                loaded[profile.image] = image
            }
        }
        return loaded
    }()

    /// Loads the font and decodes the photographs before the first frame.
    static func prepare() {
        _ = registered
        _ = photos
    }

    static func photo(_ name: String) -> UIImage {
        return photos[name] ?? UIImage()
    }

    private static func load(_ name: String) -> UIImage? {
        guard let url = Bundle.main.url(forResource: "SilverScreenCarousel-\(name)", withExtension: "jpg"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil) else {
            return nil
        }
        let options = [
            kCGImageSourceShouldCache: true,
            kCGImageSourceShouldCacheImmediately: true,
        ] as CFDictionary
        guard let decoded = CGImageSourceCreateImageAtIndex(source, 0, options) else {
            return nil
        }
        return UIImage(cgImage: decoded, scale: 1, orientation: .up)
    }
}
