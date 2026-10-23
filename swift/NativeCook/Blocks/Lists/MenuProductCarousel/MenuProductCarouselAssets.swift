import CoreText
import SwiftUI
import UIKit

enum MenuProductCarouselAssets {
    static let titleFont = "LilitaOne"
    static let wordFont = "Anton-Regular"
    static let scriptFont = "KaushanScript-Regular"
    static let uiFont = "Montserrat-SemiBold"

    private static let registered: Void = {
        let files = [
            "MenuProductCarousel-LilitaOne-Regular",
            "MenuProductCarousel-Anton-Regular",
            "MenuProductCarousel-KaushanScript-Regular",
            "MenuProductCarousel-Montserrat-SemiBold",
        ]
        for file in files {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else {
                #if DEBUG
                assertionFailure("Missing Menu Product Carousel font: \(file).ttf")
                #endif
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func registerFonts() {
        _ = registered
    }

    static func image(_ name: String) -> Image {
        guard let image = UIImage(named: name) else {
            #if DEBUG
            assertionFailure("Missing Menu Product Carousel image: \(name)")
            #endif
            return Image(systemName: "photo")
        }
        return Image(uiImage: image)
    }
}
