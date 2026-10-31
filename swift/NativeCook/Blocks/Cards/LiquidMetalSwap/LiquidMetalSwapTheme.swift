import SwiftUI
import CoreText

struct LiquidMetalSwapToken {
    let symbol, name, amount, fiat, balance: String
    let tint: SIMD3<Float>
    let colour: Color
}
enum LiquidMetalSwapTheme {
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255, opacity: 1)
    }
    static let page = color(0x0B0C10), card = color(0x15171D), ink = color(0xF2F3F5)
    static let mute = color(0x8A8F9C), faint = color(0x5C616D), silver = color(0xE6E9EF), gold = color(0xE8C27A)
    static let cardEdge = Color.white.opacity(0.06)
    static let tokens = [
        LiquidMetalSwapToken(symbol: "PRSM", name: "Prism", amount: "250", fiat: "$412.50", balance: "Balance 1,820", tint: SIMD3(0.8,0.8,0.79), colour: silver),
        LiquidMetalSwapToken(symbol: "SPRK", name: "Spark", amount: "1,204.83", fiat: "$412.17", balance: "Balance 96.4", tint: SIMD3(1,0.74,0.36), colour: gold)
    ]
    static let rates = ["1 PRSM = 4.8193 SPRK", "1 SPRK = 0.2075 PRSM"]
    static let cardH = 112.0, cardGap = 8.0, rowH = 36.0, buttonH = 56.0
    static let fonts: [String: String] = {
        var names: [String:String] = [:]
        for weight in ["Regular","Medium","SemiBold"] {
            guard let url = Bundle.main.url(forResource: "LiquidMetalSwap-Inter-\(weight)", withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor], let descriptor = descriptors.first {
                names[weight] = CTFontDescriptorCopyAttribute(descriptor, kCTFontNameAttribute) as? String
            }
        }
        return names
    }()
    static func font(_ size: Double, _ weight: String = "Regular") -> Font {
        .custom(fonts[weight] ?? (weight == "Regular" ? "Inter24pt-Regular" : "Inter-\(weight)"), fixedSize: size)
    }
}
struct LiquidMetalSwapLayout {
    var w, h, barTop, stageTop, stageH, cardsTop, rowsTop, buttonTop: Double
    let side = 16.0
    var cardW: Double { w-2*side }
    init(w: Double, h: Double, top: Double, bottom: Double) {
        self.w=w; self.h=h; barTop=top+4; stageTop=barTop+44
        buttonTop=h-max(bottom,16)-8-56
        rowsTop=buttonTop-16-3*36
        cardsTop=rowsTop-16-(2*112+8)
        stageH=max(200,cardsTop-8-stageTop)
    }
}
