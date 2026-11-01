import CoreText
import SwiftUI
import UIKit

// Fonts and the unchanging product page are registered/rasterised once per screen size.
final class BundlePackBoxResources {
    static let shared = BundlePackBoxResources()
    private var names: [String: String] = [:]
    private var cache: [String: Page] = [:]
    struct Page {
        let base: UIImage
        let buttons: [UIImage]
        let stamp: UIImage
        let stampSize: CGSize
        let cta: CGRect
        let sheet: CGRect
        let labels: [(String,CGRect)]
    }
    init() {
        for name in ["Inter-Regular", "Inter-SemiBold", "Inter-Bold", "Ionicons"] {
            if let url = Bundle.main.url(forResource: "BundlePackBox-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
                if let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider), let ps = font.postScriptName {
                    names[name] = ps as String
                }
            }
        }
    }
    func font(_ weight: String,_ size: Double) -> UIFont {
        let name = weight == "Ionicons" ? weight : "Inter-\(weight)"
        return UIFont(name: names[name] ?? name, size: size) ?? .systemFont(ofSize:size)
    }
    static func color(_ h: Int,_ a: Double = 1) -> UIColor {
        UIColor(red:Double((h>>16)&255)/255,green:Double((h>>8)&255)/255,blue:Double(h&255)/255,alpha:a)
    }
    private func layout(_ string: String,_ weight: String,_ size: Double,_ hex: Int,_ width: Double,_ tracking: Double,_ lineHeight: Double?,_ center: Bool,_ strike: Bool = false) -> (NSTextStorage, NSLayoutManager, NSTextContainer) {
        let face = font(weight,size)
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = center ? .center : .left
        var attributes: [NSAttributedString.Key:Any] = [.font:face,.foregroundColor:Self.color(hex),.paragraphStyle:paragraph]
        if tracking != 0 { attributes[.kern] = tracking }
        if strike { attributes[.strikethroughStyle] = NSUnderlineStyle.single.rawValue }
        if let lineHeight {
            paragraph.minimumLineHeight = lineHeight; paragraph.maximumLineHeight = lineHeight
            if lineHeight >= face.lineHeight { attributes[.baselineOffset] = (lineHeight-face.lineHeight)/2 }
        }
        let storage = NSTextStorage(string:string,attributes:attributes)
        let manager = NSLayoutManager(); manager.usesFontLeading = false
        let container = NSTextContainer(size:CGSize(width:width,height:1000)); container.lineFragmentPadding = 0
        manager.addTextContainer(container); storage.addLayoutManager(manager)
        manager.ensureLayout(for:container)
        return (storage,manager,container)
    }
    private func pixelCeil(_ value: Double) -> Double { ceil(value*UIScreen.main.scale)/UIScreen.main.scale }
    private func pixelFloor(_ value: Double) -> Double { floor(value*UIScreen.main.scale)/UIScreen.main.scale }
    private func pixelRound(_ value: Double) -> Double { (value*UIScreen.main.scale).rounded()/UIScreen.main.scale }
    func text(_ text: String,_ weight: String,_ size: Double,_ hex: Int,_ x: Double,_ y: Double,_ width: Double = 1000,_ tracking: Double = 0,_ lineHeight: Double? = nil,_ center: Bool = false,_ strike: Bool = false) -> CGSize {
        let (storage,manager,container) = layout(text,weight,size,hex,width,tracking,lineHeight,center,strike)
        let range = manager.glyphRange(for:container)
        manager.drawBackground(forGlyphRange:range,at:CGPoint(x:pixelRound(x),y:pixelRound(y)))
        manager.drawGlyphs(forGlyphRange:range,at:CGPoint(x:pixelRound(x),y:pixelRound(y)))
        let box = manager.usedRect(for:container)
        withExtendedLifetime(storage) {}
        return CGSize(width:pixelCeil(box.width),height:pixelCeil(box.height))
    }
    func width(_ text: String,_ weight: String,_ size: Double,_ track: Double = 0) -> Double {
        let (storage,manager,container) = layout(text,weight,size,0,1000,track,nil,false)
        let width = pixelCeil(manager.usedRect(for:container).width)
        withExtendedLifetime(storage) {}
        return width
    }
    func height(_ weight: String,_ size: Double) -> Double { pixelCeil(font(weight,size).lineHeight) }
    func page(_ size: CGSize,_ top: Double,_ bottom: Double) -> Page {
        let key = "\(size.width):\(size.height):\(top):\(bottom)"
        if let old = cache[key] { return old }
        let W = size.width,H = size.height, contentW = W-44
        let desc = "Kettlebell 8 kg, adjustable dumbbell, travel mat, 750 ml bottle and a light band."
        let (descStorage,descManager,descContainer) = layout(desc,"Regular",15,0,contentW,0,21,false)
        let descH = pixelCeil(descManager.usedRect(for:descContainer).height)
        withExtendedLifetime(descStorage) {}
        let kickH = height("SemiBold",12), titleH = height("Bold",24), priceH = height("Bold",26), hintH = height("Regular",13)
        let sheetH = 22+kickH+6+titleH+6+descH+14+priceH+16+56+10+hintH+bottom+14
        let sy = H-sheetH, priceY = sy+22+kickH+6+titleH+6+descH+14, ctaY = priceY+priceH+16
        let cta = CGRect(x:22,y:ctaY,width:contentW,height:56)
        let renderer = UIGraphicsImageRenderer(size:size)
        let base = renderer.image { _ in
            Self.color(0xFFFFFF).setFill()
            let sheet = UIBezierPath(roundedRect:CGRect(x:0,y:sy,width:W,height:sheetH),byRoundingCorners:[.topLeft,.topRight],cornerRadii:CGSize(width:30,height:30)); sheet.fill()
            let hy = top+6
            for x in [16.0,W-60] { Self.color(0xffffff,0.8).setFill(); UIBezierPath(ovalIn:CGRect(x:x,y:hy,width:44,height:44)).fill() }
            _ = text(String(UnicodeScalar(61993)!),"Ionicons",22,0x2A2420,16,hy+(44-height("Ionicons",22))/2,44,0,nil,true)
            _ = text(String(UnicodeScalar(61791)!),"Ionicons",21,0x2A2420,W-60,pixelFloor(hy+(44-height("Ionicons",21))/2),44,0,nil,true)
            _ = text("Kitform","Bold",18,0x2A2420,0,hy+(44-height("Bold",18))/2,W,-0.3,nil,true)
            _ = text("BUNDLE · 5 PIECES","SemiBold",12,0xB07A45,22,sy+22,contentW,1.2)
            _ = text("Home Gym Starter","Bold",24,0x2A2420,22,sy+22+kickH+6,contentW)
            _ = text(desc,"Regular",15,0x7A706A,22,sy+22+kickH+6+titleH+6,contentW,0,21)
            _ = text("$129","Bold",26,0x2A2420,22,priceY)
            let oldX = 22+width("$129","Bold",26)+10
            _ = text("$166","Regular",16,0xA79D96,oldX,priceY+(priceH-height("Regular",16))/2,1000,0,nil,false,true)
            let saveX = oldX+width("$166","Regular",16)+10, saveW = width("Save 22%","SemiBold",13)+20
            Self.color(0xFFE7DE).setFill(); UIBezierPath(roundedRect:CGRect(x:saveX,y:priceY+(priceH-26)/2,width:saveW,height:26),cornerRadius:13).fill()
            _ = text("Save 22%","SemiBold",13,0xE2574C,saveX+10,priceY+(priceH-height("SemiBold",13))/2)
            _ = text("Tip: pinch the kit together to pack it yourself","Regular",13,0xA79D96,22,ctaY+56+10,contentW,0,nil,true)
        }
        let buttons = [false,true].map { packed in
            UIGraphicsImageRenderer(size:cta.size).image { _ in
                Self.color(packed ? 0x2F9E62 : 0x2A2420).setFill(); UIBezierPath(roundedRect:CGRect(origin:.zero,size:cta.size),cornerRadius:28).fill()
                let label = packed ? "Packed · Add bundle to bag" : "Pack bundle"
                // Yoga floors the origins of these intrinsic text nodes on the physical pixel grid.
                let tw = width(label,"SemiBold",17), ix = pixelFloor((contentW-(20+8+tw))/2)
                _ = text(String(UnicodeScalar(packed ? 61982 : 62105)!),"Ionicons",20,0xffffff,ix,(56-height("Ionicons",20))/2,20,0,nil,true)
                _ = text(label,"SemiBold",17,0xffffff,ix+28,(56-height("SemiBold",17))/2,tw+1)
            }
        }
        let stampSize = CGSize(width:width("TO SHIP","Bold",13,1.2)+26,height:44)
        let stamp = UIGraphicsImageRenderer(size:stampSize).image { _ in
            Self.color(0xE2574C).setStroke(); let path = UIBezierPath(roundedRect:CGRect(origin:.zero,size:stampSize).insetBy(dx:1.5,dy:1.5),cornerRadius:8.5); path.lineWidth = 3; path.stroke()
            _ = text("READY\nTO SHIP","Bold",13,0xE2574C,13,7,stampSize.width-26,1.2,15,true)
        }
        let oldX = 22+width("$129","Bold",26)+10
        let saveX = oldX+width("$166","Regular",16)+10
        let labels: [(String,CGRect)] = [
            ("BUNDLE · 5 PIECES",CGRect(x:22,y:sy+22,width:contentW,height:kickH)),
            ("Home Gym Starter",CGRect(x:22,y:sy+22+kickH+6,width:contentW,height:titleH)),
            (desc,CGRect(x:22,y:sy+22+kickH+6+titleH+6,width:contentW,height:descH)),
            ("$129",CGRect(x:22,y:priceY,width:oldX-32,height:priceH)),
            ("$166",CGRect(x:oldX,y:priceY,width:saveX-oldX-10,height:priceH)),
            ("Save 22%",CGRect(x:saveX,y:priceY,width:width("Save 22%","SemiBold",13)+20,height:priceH)),
            ("Tip: pinch the kit together to pack it yourself",CGRect(x:22,y:ctaY+66,width:contentW,height:hintH))
        ]
        let page = Page(base:base,buttons:buttons,stamp:stamp,stampSize:stampSize,cta:cta,sheet:CGRect(x:0,y:sy,width:W,height:sheetH),labels:labels)
        cache[key] = page
        return page
    }
}
