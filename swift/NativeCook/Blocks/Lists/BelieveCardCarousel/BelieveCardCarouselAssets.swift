import CoreText
import SwiftUI

enum BelieveCardCarouselAssets {
    static let regular = "Inter-Regular"
    static let medium = "Inter-Medium"
    static let semibold = "Inter-SemiBold"
    static let extraBold = "Inter-ExtraBold"
    static let displayBold = "InterDisplay-Bold"

    static let registered: Void = {
        let files = [
            "BelieveCardCarousel-Inter-Regular",
            "BelieveCardCarousel-Inter-Medium",
            "BelieveCardCarousel-Inter-SemiBold",
            "BelieveCardCarousel-Inter-ExtraBold",
            "BelieveCardCarousel-InterDisplay-Bold",
        ]
        for file in files {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else {
                assertionFailure("Missing Believe Card Carousel font: \(file).ttf")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static let avatar: UIImage = {
        guard let image = UIImage(named: "BelieveCardCarousel-avatar-anika.png") else {
            assertionFailure("Missing Believe Card Carousel avatar")
            return UIImage()
        }
        return image
    }()

    static func font(_ name: String, size: Double) -> Font {
        _ = registered
        return .custom(name, fixedSize: size)
    }

    static func uiFont(_ name: String, size: Double) -> UIFont {
        _ = registered
        guard let font = UIFont(name: name, size: size) else {
            assertionFailure("Missing registered Believe Card Carousel font: \(name)")
            return UIFont.systemFont(ofSize: size, weight: .bold)
        }
        return font
    }
}
