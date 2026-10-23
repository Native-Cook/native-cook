import CoreText
import SwiftUI
import UIKit

enum PastePlacesImportAssets {
    private static let registration: Void = {
        ["PastePlacesImport-Anybody-Text", "PastePlacesImport-Anybody-ExtraBold"].forEach { name in
            if let asset = NSDataAsset(name: name),
               let provider = CGDataProvider(data: asset.data as CFData),
               let font = CGFont(provider) {
                CTFontManagerRegisterGraphicsFont(font, nil)
            } else if let url = Bundle.main.url(forResource: name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    private static let images: [String: UIImage] = [
        "notes": loadImage("PastePlacesImport-icon-notes"),
        "chat": loadImage("PastePlacesImport-icon-chat"),
        "doc": loadImage("PastePlacesImport-icon-doc"),
    ]

    static func prepare() { _ = registration }

    static func font(_ name: String, size: Double) -> UIFont {
        _ = registration
        return UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
    }

    static func image(_ name: String) -> UIImage { images[name] ?? UIImage() }

    static func textWidth(_ text: String, font: UIFont, tracking: Double = 0) -> Double {
        let natural = (text as NSString).size(withAttributes: [.font: font]).width
        return natural + max(0, Double(text.count - 1)) * tracking
    }

    private static func loadImage(_ name: String) -> UIImage {
        if let image = UIImage(named: name) { return image.preparingForDisplay() ?? image }
        guard let path = Bundle.main.path(forResource: name, ofType: "png"),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }
}
