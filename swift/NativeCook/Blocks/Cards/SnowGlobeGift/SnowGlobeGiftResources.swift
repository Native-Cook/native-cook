import CoreText
import SwiftUI
import UIKit

final class SnowGlobeGiftResources {
    static let shared = SnowGlobeGiftResources()
    static func color(_ hex: Int) -> UIColor {
        UIColor(red: Double((hex>>16)&255)/255,green: Double((hex>>8)&255)/255,blue: Double(hex&255)/255,alpha: 1)
    }
    struct Label {
        let image: UIImage
        let size: CGSize
        let pad: Double
    }
    var labels: [String: Label] = [:]
    private init() {
        for name in ["InstrumentSerif-Regular","Inter-Regular","Inter-Medium","Inter-SemiBold"] {
            if let url = Bundle.main.url(forResource: "SnowGlobeGift-\(name)",withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
            }
        }
        labels["from"] = label("FROM MIRA","Inter-Medium",12,18,1.4,0x8C7B78)
        labels["title"] = label("A gift for you","InstrumentSerif-Regular",36,42,0,0x2A1D1C)
        labels["note"] = label("“Happy birthday! The coffee is on me.”","Inter24pt-Regular",15,22,0,0x8C7B78)
        labels["amount"] = label("$50","InstrumentSerif-Regular",56,60,0,0xB98A3E,true)
        labels["amountNote"] = label("Kinto gift balance","Inter-SemiBold",11,nil,1,0xB98A3E)
        labels["open"] = label("Open Gift","Inter-SemiBold",17,nil,0,0xFCF8F6)
        labels["add"] = label("Add $50 to Wallet","Inter-SemiBold",17,nil,0,0xFCF8F6)
        labels["added"] = label("Added to Wallet","Inter-SemiBold",17,nil,0,0xFCF8F6)
    }
    private func label(_ text: String, _ name: String, _ size: Double, _ lineHeight: Double?, _ kern: Double, _ hex: Int, _ glow: Bool = false) -> Label {
        let font = UIFont(name: name,size: size) ?? UIFont.systemFont(ofSize: size)
        let paragraph = NSMutableParagraphStyle()
        let height = lineHeight ?? font.lineHeight
        paragraph.minimumLineHeight = height; paragraph.maximumLineHeight = height
        var attributes: [NSAttributedString.Key: Any] = [.font:font,.foregroundColor: Self.color(hex),.paragraphStyle:paragraph]
        // An absent letterSpacing preserves the font's pair kerning in RN.
        if kern != 0 { attributes[.kern] = kern }
        let string = NSAttributedString(string:text,attributes:attributes)
        let width = CTLineGetTypographicBounds(CTLineCreateWithAttributedString(string),nil,nil,nil)
        let pad = glow ? 30.0 : 4.0
        let dimensions = CGSize(width:width+pad*2,height:height+pad*2)
        let format = UIGraphicsImageRendererFormat(); format.scale = 3
        let image = UIGraphicsImageRenderer(size:dimensions,format:format).image { renderer in
            // RCTText clips glyphs to the requested line fragment, including the
            // tall dollar stem when the 60 pt line is shorter than the font.
            if lineHeight != nil && height < font.lineHeight {
                renderer.cgContext.clip(to:CGRect(x:0,y:pad,width:dimensions.width,height:height))
            }
            if glow { renderer.cgContext.setShadow(offset:.zero,blur:14,color:UIColor(red:1,green:240/255,blue:210/255,alpha:0.9).cgColor) }
            let baselineAdjustment = lineHeight == nil ? 0 : max(0,height-font.lineHeight)/2
            string.draw(in:CGRect(x:pad,y:pad-baselineAdjustment,width:width+1,height:height+baselineAdjustment))
        }
        return Label(image:image,size:CGSize(width:width,height:height),pad:pad)
    }
    func draw(_ key: String, _ context: GraphicsContext, _ x: Double, _ top: Double) {
        guard let label = labels[key] else { return }
        context.draw(Image(uiImage:label.image),in:CGRect(x:x-label.size.width/2-label.pad,y:top-label.pad,width:label.image.size.width,height:label.image.size.height))
    }
}
