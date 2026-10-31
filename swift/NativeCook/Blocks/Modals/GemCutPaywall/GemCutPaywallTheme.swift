import SwiftUI
import CoreText

enum GemCutPaywallTheme {
    static let page = color(0x07060C), surface = color(0x15131F), line = color(0x262236)
    static let ink = color(0xF5F3FA), mute = color(0x8F8AA3), thumb = color(0xE9E4FF)
    static let thumbInk = color(0x140F26), ctaInk = color(0x0B0914)
    static let names = ["Free","Plus","Pro"], prices = ["0.00","4.99","9.99"]
    static let notes = ["Five edits a day, forever.","Then $4.99 a month. Cancel anytime.","Then $9.99 a month. Cancel anytime."]
    static let features = ["Unlimited edits","4K and RAW export","AI relight and retouch"]
    static func color(_ hex: UInt32) -> Color { Color(.sRGB,red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,opacity:1) }
    static let registerFonts: Void = {
        for weight in ["Regular","Medium","SemiBold"] {
            if let url = Bundle.main.url(forResource:"GemCutPaywall-Inter-\(weight)",withExtension:"ttf") { CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil) }
        }
    }()
    static func font(_ size: Double, _ weight: String = "Regular") -> Font { _ = registerFonts; return .custom(weight == "Regular" ? "Inter24pt-Regular" : "Inter-\(weight)",fixedSize:size) }
    struct Layout {
        var w: Double, h: Double, barTop: Double, gemX: Double, gemY: Double, gemR: Double, floorY: Double
        var titleTop: Double, priceTop: Double, planTop: Double, featTop: Double, noteTop: Double, ctaTop: Double
        var planX: Double { 16 }; var planW: Double { w-32 }
        var segW: Double { (planW-8)/3 }; var thumbRun: Double { planW-8-segW }
    }
    static func layout(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) -> Layout {
        let cta = h-bottom-8-56, note=cta-12-18, feat=note-24-96, plan=feat-24-48, price=plan-24-22, title=price-4-34
        let bar=top+8, radius=min(w*0.36,160), room=bar+44, gy=room+(title-room)*0.42
        return Layout(w:w,h:h,barTop:bar,gemX:w/2,gemY:gy,gemR:radius,floorY:gy+radius*0.86,titleTop:title,priceTop:price,planTop:plan,featTop:feat,noteTop:note,ctaTop:cta)
    }
}
