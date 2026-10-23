import CoreText
import SwiftUI
import UIKit

enum SavedPlacesMapAssets {
    private static let registration: Void = {
        let names = [
            "Archivo-Regular", "Archivo-Medium", "Archivo-SemiBold", "Archivo-ExtraBold",
            "Barlow-Regular", "Barlow-SemiBold", "Barlow-Italic",
        ]
        for name in names {
            guard let url = Bundle.main.url(forResource: "SavedPlacesMap-\(name)", withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    private static let images: [String: UIImage] = [
        "map": load("map"),
        "cafe": load("list-cafe"),
        "bar": load("list-bar"),
        "avatar": load("avatar-sam"),
    ]

    static func prepare() { _ = registration }

    static func image(_ name: String) -> Image {
        Image(uiImage: images[name] ?? UIImage()).interpolation(.high)
    }

    private static func load(_ name: String) -> UIImage {
        guard let url = Bundle.main.url(forResource: "SavedPlacesMap-\(name)", withExtension: "jpg"),
              let source = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return source.preparingForDisplay() ?? source
    }
}
