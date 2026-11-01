import CoreText
import ImageIO
import UIKit
import SwiftUI

// All decoding, font creation, glyph paths, and blur happen once, outside the timeline.
final class QuestMapUnrollResources {
    static let shared = QuestMapUnrollResources()
    let page: UIImage
    let edge: UIImage
    let glow: UIImage
    let numberFont: UIFont
    let star: Path
    let unitCircle = Path(ellipseIn:CGRect(x:-1,y:-1,width:2,height:2))
    var labels: [String: UIImage] = [:]
    var widths: [String: Double] = [:]
    init() {
        for name in ["TitanOne-Regular", "Nunito-Black", "Nunito-ExtraBold", "Ionicons"] {
            if let url = Bundle.main.url(forResource: "QuestMapUnroll-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        func image(_ name: String, _ ext: String) -> UIImage {
            guard let url = Bundle.main.url(forResource: "QuestMapUnroll-\(name)", withExtension: ext),
                  let src = CGImageSourceCreateWithURL(url as CFURL, nil),
                  let cg = CGImageSourceCreateImageAtIndex(src, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
            else { return UIImage() }
            return UIImage(cgImage: cg)
        }
        page = image("map-page", "webp")
        edge = image("paper-edge", "png")
        numberFont = UIFont(name: "Nunito-Black", size: 18)!
        let path = CGMutablePath()
        for q in 0..<10 {
            let a = -.pi / 2 + Double(q) * .pi / 5
            let r = q % 2 == 1 ? 3.0 : 7.0
            let p = CGPoint(x: cos(a) * r, y: sin(a) * r)
            if q == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        path.closeSubpath()
        star = Path(path)
        glow = Self.makeGlow()
        label("powerNum", "334", "TitanOne", 20, 0xFFFFFF)
        label("powerText", "MAGIC POWER", "Nunito-Black", 11, 0xE8FFD8, 0.8)
        for (i, name) in ["Daily puzzle", "Gift bag", "Free hints", "Hint shop"].enumerated() {
            label("shop\(i)", name, "Nunito-Black", 11, 0xFFE3EC)
        }
        for i in 0..<6 {
            label("title\(i)", QuestMapUnrollMotion.names[i], "Nunito-Black", 17, 0x5A2E18)
            let n = QuestMapUnrollMotion.stars[i]
            label("sub\(i)", n > 0 ? "\(n) of 3 stars" : "New level · 4 spells to learn", "Nunito-ExtraBold", 13, 0x9A7050)
            label("card\(i)", "\(i + 1)", "TitanOne", 18, 0xB4532B)
            // Canvas drawText uses a baseline of 7, unlike RN Text.
            label("number\(i)", "\(i + 1)", "Nunito-Black", 18, i <= 2 ? 0xB4532B : 0xA58F70, baseline: true)
            var character=Array("\(i+1)".utf16)[0], glyph=CGGlyph()
            CTFontGetGlyphsForCharacters(numberFont as CTFont,&character,&glyph,1)
            let bounds=CTFontGetBoundingRectsForGlyphs(numberFont as CTFont,.horizontal,&glyph,nil,1)
            // SkFont.measureText includes the one-unit AA mask outset on each side.
            widths["number\(i)"]=ceil(bounds.maxX)-floor(bounds.minX)+2
        }
        let icons: [(String, Int, Double, UInt32)] = [("back",61735,22,0x5A2E18),("settings",62827,20,0x5A2E18),("flash",62230,16,0xFFFFFF),("puzzle",62179,22,0xFFFFFF),("gift",62263,22,0xFFFFFF),("bulb",61888,22,0xFFFFFF),("store",62887,22,0xFFFFFF),("play",62662,20,0xFFFFFF)]
        for (key, code, size, color) in icons { label(key, String(UnicodeScalar(code)!), "Ionicons", size, color) }
    }
    private static func makeGlow() -> UIImage {
        // Skia's mask blur uses sigma=12, not a radius-12 box shadow. Convolve once at 3x.
        let w=324, sigma=36.0, radius=108
        var kernel = (-radius...radius).map { exp(-Double($0*$0)/(2*sigma*sigma)) }
        let sum=kernel.reduce(0,+)
        kernel=kernel.map { $0/sum }
        var prefix=[0.0]
        for k in kernel { prefix.append(prefix.last!+k) }
        func integral(_ lower: Int, _ upper: Int) -> Double {
            let a=max(-radius,lower), b=min(radius,upper)
            return a <= b ? prefix[b+radius+1]-prefix[a+radius] : 0
        }
        var temp=[Double](repeating:0,count:w*w)
        for y in 0..<w {
            let dy=Double(y)+0.5-162
            if abs(dy) >= 42 { continue }
            let half=sqrt(42*42-dy*dy)
            let left=Int(ceil(161.5-half)),right=Int(floor(161.5+half))
            for x in 0..<w { temp[y*w+x]=integral(left-x,right-x) }
        }
        var output=[Double](repeating:0,count:w*w)
        for y in 0..<w/2 { for x in 0..<w/2 {
            var v=0.0
            for k in -radius...radius where y+k >= 0 && y+k < w { v += temp[(y+k)*w+x]*kernel[k+radius] }
            output[y*w+x]=v;output[y*w+w-1-x]=v
            output[(w-1-y)*w+x]=v;output[(w-1-y)*w+w-1-x]=v
        } }
        var rgba=[UInt8](repeating:0,count:w*w*4)
        for i in 0..<w*w {
            let a=output[i]*0.5
            rgba[i*4]=UInt8((255*a).rounded());rgba[i*4+1]=UInt8((227*a).rounded())
            rgba[i*4+2]=UInt8((138*a).rounded());rgba[i*4+3]=UInt8((255*a).rounded())
        }
        let data=Data(rgba) as CFData
        let cg=CGImage(width:w,height:w,bitsPerComponent:8,bitsPerPixel:32,bytesPerRow:w*4,space:CGColorSpace(name:CGColorSpace.sRGB)!,bitmapInfo:CGBitmapInfo(rawValue:CGImageAlphaInfo.premultipliedLast.rawValue),provider:CGDataProvider(data:data)!,decode:nil,shouldInterpolate:true,intent:.defaultIntent)!
        return UIImage(cgImage:cg,scale:3,orientation:.up)
    }
    static func color(_ rgb: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double((rgb >> 16) & 255) / 255, green: Double((rgb >> 8) & 255) / 255, blue: Double(rgb & 255) / 255, alpha: alpha)
    }
    private func label(_ key: String, _ text: String, _ face: String, _ size: Double, _ color: UInt32, _ tracking: Double = 0, baseline: Bool = false) {
        let font = UIFont(name: face, size: size)!
        var attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: Self.color(color)]
        if tracking != 0 { attrs[.kern] = tracking }
        let str = NSAttributedString(string: text, attributes: attrs)
        let width = CTLineGetTypographicBounds(CTLineCreateWithAttributedString(str), nil, nil, nil)
        widths[key] = baseline ? width : ceil(width*3)/3
        let format = UIGraphicsImageRendererFormat(); format.scale = 3
        let h = ceil(font.lineHeight*3)/3
        labels[key] = UIGraphicsImageRenderer(size: CGSize(width: ceil(width) + 8, height: h + 8), format: format).image { r in
            if baseline {
                let line = CTLineCreateWithAttributedString(str)
                r.cgContext.translateBy(x: 4, y: 4 + font.ascender)
                r.cgContext.scaleBy(x: 1, y: -1)
                CTLineDraw(line, r.cgContext)
            } else {
                str.draw(at: CGPoint(x: 4, y: 4))
            }
        }
    }
}
