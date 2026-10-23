import CoreText
import SwiftUI
import UIKit

enum SendMoneyCardsAssets {
    private static let registered: Void = {
        for name in ["Regular", "Medium", "SemiBold"] {
            guard let url = Bundle.main.url(
                forResource: "SendMoneyCards-Poppins-\(name)",
                withExtension: "ttf"
            ) else {
                assertionFailure("Missing Send Money Cards font: \(name)")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func font(_ name: String, size: Double) -> Font {
        _ = registered
        return .custom(name, fixedSize: size)
    }

    static func image(_ name: String) -> Image {
        guard let image = UIImage(named: name) else {
            assertionFailure("Missing Send Money Cards image: \(name)")
            return Image(systemName: "person.crop.circle.fill")
        }
        return Image(uiImage: image)
    }
}
