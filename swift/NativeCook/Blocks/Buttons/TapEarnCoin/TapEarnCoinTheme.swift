import SwiftUI
import CoreText
import UIKit

enum TapEarnCoinTheme {
    /// The bundled Inter cuts name themselves "Inter24pt-Regular"/"Inter24pt-Bold" and "Inter-Medium"/"Inter-SemiBold".
    static func postScript(_ weight: String) -> String {
        weight == "Regular" || weight == "Bold" ? "Inter24pt-\(weight)" : "Inter-\(weight)"
    }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16)&255)/255, green: Double((hex >> 8)&255)/255, blue: Double(hex&255)/255, opacity: 1)
    }
    static let pageTop = color(0xFFF3DF), pageMid = color(0xFFF9F0)
    static let title = color(0xEC6E0C), ink = color(0x191B21), unit = color(0x3F444C)
    static let digit = color(0xF28D0E), digitBox = color(0xFEF2DC), labelNum = color(0xBF5700)
    static let cta = color(0xF56D01), ctaPressed = color(0xDE6200), blue = color(0x3182F6), bluePressed = color(0x2272EB)
    static let sheetTitle = color(0x191F28), sheetBody = color(0x6B7684), sheetRow = color(0x4E5968), chevron = color(0xB0B8C1)
    static let dim = color(0x14100A).opacity(0.62), pill = color(0x343840).opacity(0.88)
    static let register: Void = {
        for weight in ["Regular","Medium","SemiBold","Bold"] {
            if let url = Bundle.main.url(forResource: "TapEarnCoin-Inter-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static func fontScale(_ cap: Double = .infinity) -> Double {
        min(UIFont.preferredFont(forTextStyle:.body).pointSize/17,cap)
    }
    static func font(_ size: Double, _ weight: String = "Regular", cap: Double = .infinity) -> Font {
        _ = register; return .custom(postScript(weight), fixedSize:size*fontScale(cap))
    }
    static func uiFont(_ size: Double, _ weight: String = "Regular") -> UIFont {
        _ = register; return UIFont(name: postScript(weight),size:size) ?? .systemFont(ofSize:size)
    }
    struct Layout {
        var w: Double, h: Double, s: Double, titleTop: Double, subtitleTop: Double
        var coinX: Double, coinY: Double, coinR: Double, digitsTop: Double, digitW: Double, digitH: Double, digitGap: Double
        var labelTop: Double, ctaTop: Double, ctaH: Double, side: Double, sheetH: Double, sheetTop: Double
        var bellX: Double, bellY: Double, bellSize: Double
        var boxesLeft: Double { w/2-(3*digitW+3*digitGap+18)/2 }
        func boxX(_ i: Int) -> Double { boxesLeft+digitW/2+Double(i)*(digitW+digitGap) }
    }
    static func makeLayout(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) -> Layout {
        let s = w/720, titleTop = top+52, ctaBottom = h-bottom-12, sheetH = 398+bottom, bellSize = 99*s
        func at(_ y: Double) -> Double { titleTop+(y-70)*s }
        return Layout(w:w,h:h,s:s,titleTop:titleTop,subtitleTop:at(212)-5,coinX:w/2,coinY:at(560),coinR:220*s,
                      digitsTop:at(777),digitW:84*s,digitH:100*s,digitGap:15*s,labelTop:ctaBottom-56-20-20,
                      ctaTop:ctaBottom-56,ctaH:56,side:36*s,sheetH:sheetH,sheetTop:h-sheetH,
                      bellX:50*s+bellSize/2,bellY:h-sheetH+64*s+bellSize/2,bellSize:bellSize)
    }
}
