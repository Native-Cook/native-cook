import CoreText
import SwiftUI
import UIKit

enum StormCloudWeatherTheme {
    static let registered: Void = {
        for name in ["Outfit-Medium", "Inter-Regular", "Inter-Medium", "Inter-SemiBold"] {
            if let url = Bundle.main.url(forResource: "StormCloudWeather-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static func color(_ value: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((value>>16)&255)/255, green: Double((value>>8)&255)/255, blue: Double(value&255)/255, opacity: alpha)
    }
    static func font(_ name: String, _ size: Double) -> Font {
        _ = registered
        return .custom(name == "Inter-Regular" ? "Inter24pt-Regular" : name, fixedSize: size)
    }
}
