import CoreText
import Foundation

enum TiltPosterParallaxAssets {
    private static let fontFiles = [
        "TiltPosterParallax-InstrumentSerif-Regular",
        "TiltPosterParallax-Inter-Regular",
        "TiltPosterParallax-Inter-Medium",
        "TiltPosterParallax-Inter-SemiBold",
    ]

    private static let registered: Void = {
        for file in fontFiles {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else {
                assertionFailure("Missing Tilt Poster Parallax font: \(file).ttf")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func registerFonts() {
        _ = registered
    }
}
