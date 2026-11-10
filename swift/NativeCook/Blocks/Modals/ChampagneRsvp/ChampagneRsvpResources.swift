import CoreText
import UIKit
import SwiftUI

// Registration happens once. There are no sound assets or sound calls in the RN block.
enum ChampagneRsvpResources {
    static let registered: Void = {
        for name in ["InstrumentSerif-Regular", "Inter-Regular", "Inter-Medium", "Inter-SemiBold"] {
            if let url = Bundle.main.url(forResource: "ChampagneRsvp-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    private static let fonts: [String: Font] = {
        _ = registered
        return [
            "Inter-Medium12": .custom("Inter-Medium", fixedSize: 12),
            "Inter-Medium15": .custom("Inter-Medium", fixedSize: 15),
            "Inter-Regular15": .custom("Inter24pt-Regular", fixedSize: 15),
            "Inter-SemiBold17": .custom("Inter-SemiBold", fixedSize: 17),
            "InstrumentSerif-Regular44": .custom("InstrumentSerif-Regular", fixedSize: 44)
        ]
    }()
    static func font(_ face: String, _ size: Double) -> Font { fonts["\(face)\(Int(size))"]! }
}
