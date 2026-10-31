import SwiftUI
import CoreText

enum IconEraScrubberTheme {
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB,red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,
              blue:Double(hex&255)/255,opacity:1)
    }
    static let ink = color(0xF3F4F8), mute = color(0x8C8FA0)
    static let faint = color(0x3A3C4A), tick = color(0x5A5D6E), page = color(0x09090F)
    static let step = 28.0, iconHalf = 110.0
    static let eras = [
        ("Glossy", "Gloss, bevels and airmail paper"),
        ("Flat", "Thin lines and clean colour"),
        ("Soft", "Rounded light and gentle depth"),
        ("Glass", "Clear glass that bends the light")
    ]
    static let names: [String:String] = {
        var names = [String:String]()
        for weight in ["Regular","Medium","SemiBold"] {
            guard let url = Bundle.main.url(forResource:"IconEraScrubber-Inter-\(weight)",withExtension:"ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
            if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],
               let descriptor = descriptors.first,
               let name = CTFontDescriptorCopyAttribute(descriptor,kCTFontNameAttribute) as? String {
                names[weight] = name
            }
        }
        return names
    }()
    static func font(_ weight: String, _ size: Double) -> Font {
        .custom(names[weight] ?? (weight == "Regular" ? "Inter24pt-Regular" : "Inter-\(weight)"),fixedSize:size)
    }
    struct Layout {
        var w: Double, h: Double, top: Double, bottom: Double
        var titleTop: Double { top+12 }
        var iconX: Double { w/2 }
        var iconY: Double { floor(top+(h-top-bottom)*0.36+0.5) }
        var yearTop: Double { iconY+iconHalf+44 }
        var rulerTop: Double { h-max(bottom,16)-96 }
    }
}
