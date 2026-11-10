import CoreText
import SwiftUI
import UIKit

enum GoldEmblemMembershipResources {
    private static let register: Void = {
        for weight in ["Regular", "Medium", "SemiBold", "Bold"] {
            if let url = Bundle.main.url(forResource: "GoldEmblemMembership-Outfit-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    private static let fonts: [String: [Double: Font]] = {
        _ = register
        return Dictionary(uniqueKeysWithValues: ["Regular", "Medium", "SemiBold", "Bold"].map { weight in
            (weight, Dictionary(uniqueKeysWithValues: [11.5, 12.5, 13, 14, 15, 17, 23, 26, 30].map { size in
                (size, Font.custom(weight == "Bold" ? "Outfit-Bold" : "OutfitThin-\(weight)", fixedSize: size))
            }))
        })
    }()
    static func font(_ weight: String, _ size: Double) -> Font { fonts[weight]![size]! }
}
