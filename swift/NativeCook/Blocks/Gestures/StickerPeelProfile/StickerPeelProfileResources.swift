import CoreText
import SwiftUI
import UIKit

final class StickerPeelProfileResources {
    static let shared = StickerPeelProfileResources()
    let silhouettes: [CGPath]
    let paths: [Path]
    var faces: [Image] = []
    var shadows: [Image] = []
    var closeImage = UIImage()
    private var pageKey = ""
    private var pageImage = UIImage()
    static func ui(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,alpha:alpha)
    }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color { Color(uiColor:ui(hex,alpha)) }
    static func font(_ weight: String, _ size: Double) -> UIFont {
        UIFont(name:weight == "Bold" ? "Inter24pt-Bold" : "Inter-\(weight)",size:size) ?? .systemFont(ofSize:size)
    }
    static func gradient(_ c: CGContext, _ colors: [UInt32], _ start: CGPoint, _ end: CGPoint, _ alphas: [Double]? = nil) {
        let cs = colors.enumerated().map { ui($0.element,alphas?[$0.offset] ?? 1).cgColor } as CFArray
        let g = CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB)!,colors:cs,locations:nil)!
        c.drawLinearGradient(g,start:start,end:end,options:[.drawsBeforeStartLocation,.drawsAfterEndLocation])
    }
    static func render(_ size: CGSize, _ body: (CGContext)->Void) -> UIImage {
        let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 3; fmt.opaque = false
        return UIGraphicsImageRenderer(size:size,format:fmt).image { body($0.cgContext) }
    }
    init() {
        for name in ["Inter-Medium","Inter-SemiBold","Inter-Bold","Outfit-Medium"] {
            if let u = Bundle.main.url(forResource:"StickerPeelProfile-\(name)",withExtension:"ttf") {
                CTFontManagerRegisterFontsForURL(u as CFURL,.process,nil)
            }
        }
        silhouettes = (0..<4).map { StickerPeelProfileArt.silhouette($0) }
        paths = silhouettes.map { Path($0) }
        for k in 0..<4 {
            let image = Self.render(CGSize(width:160,height:160)) { c in
                c.translateBy(x:80,y:80)
                StickerPeelProfileArt.face(c,k,silhouettes[k])
            }
            faces.append(Image(uiImage:image))
            // Static mask blur (Skia sigma 5), cached outside the frame loop.
            let shadow = Self.render(CGSize(width:160,height:160)) { c in
                c.translateBy(x:80-1000,y:80)
                c.setShadow(offset:CGSize(width:1000,height:0),blur:10,color:Self.ui(0,0.28).cgColor)
                c.addPath(silhouettes[k]); c.setFillColor(UIColor.black.cgColor); c.fillPath()
            }
            shadows.append(Image(uiImage:shadow))
        }
        closeImage = Self.render(CGSize(width:56,height:56)) { c in
            StickerPeelProfilePage.round(c,0,0,56,56,18,0xffffff)
            StickerPeelProfilePage.text("✕",20,"Bold",28,(56-StickerPeelProfilePage.height(20,"Bold","✕"))/2,0x151517,1,true)
        }
    }
    func page(_ size: CGSize, _ top: Double, _ bottom: Double) -> UIImage {
        let key = "\(size)-\(top)-\(bottom)"
        if key != pageKey {
            pageKey = key
            pageImage = Self.render(size) { c in StickerPeelProfilePage.draw(c,size,top,bottom) }
        }
        return pageImage
    }
}
