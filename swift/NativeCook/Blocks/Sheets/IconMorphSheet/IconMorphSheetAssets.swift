import CoreText
import SwiftUI
import UIKit

enum IconMorphSheetAssets {
    static let titles = ["Send with zero fees", "We keep every send safe", "Say it with an envelope", "Link your bank and go"]
    static let subtitles = [
        ["Friends or your own bank, always free."],
        ["We check the recipient for reported fraud", "before your money moves."],
        ["Wrap a gift or a thank-you in a note."],
        ["Running low? We top up from your account", "the moment you send."]
    ]
    static let descriptions = ["A zero with gold coins", "A siren with a shield", "An open envelope with a note", "A bank passbook with a check"]
    static let labels = ["Send $24.00", "Next", "Got it"]
    static let avatar: Image = {
        guard let url = Bundle.main.url(forResource: "IconMorphSheet-avatar", withExtension: "png"),
              let image = UIImage(contentsOfFile: url.path) else { return Image(systemName: "person.crop.square") }
        return Image(uiImage: image)
    }()
    static let registered: Void = {
        for weight in ["Regular", "Bold"] {
            if let url = Bundle.main.url(forResource: "IconMorphSheet-Inter-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }
}
