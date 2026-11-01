import CoreText
import SwiftUI
import UIKit

final class FridgeDoorGroceryResources {
    static let shared = FridgeDoorGroceryResources()
    struct Label {
        var image: Image
        var size: CGSize
    }
    let black: String
    let bold: String
    let icons: String
    let lowPath: Path
    let lowWidth: Double
    var labels: [String: Label] = [:]
    private var encodedColors: [Int:[CGFloat]] = [:]
    func encodedColor(_ hex:Int,_ alpha:Double = 1) -> Color {
        let components: [CGFloat]
        if let cached = encodedColors[hex] { components = cached }
        else {
            let srgb = CGColor(srgbRed:Double(hex >> 16 & 255)/255,green:Double(hex >> 8 & 255)/255,blue:Double(hex & 255)/255,alpha:1)
            components = srgb.converted(to:CGColorSpace(name:CGColorSpace.extendedDisplayP3)!,intent:.relativeColorimetric,options:nil)!.components!
            encodedColors[hex] = components
        }
        return Color(.sRGB,red:components[0],green:components[1],blue:components[2],opacity:alpha)
    }
    private var screenCache: (size:CGSize,path:Path)?
    func screenPath(_ size:CGSize) -> Path {
        if let cached = screenCache, cached.size == size { return cached.path }
        let path = Path(CGRect(origin:.zero,size:size))
        screenCache = (size,path)
        return path
    }
    private var noteCache: (rect:CGRect,path:Path)?
    func notePath(_ rect:CGRect) -> Path {
        if let cache = noteCache, cache.rect == rect { return cache.path }
        let path = Path(roundedRect:rect,cornerRadius:20)
        noteCache = (rect,path)
        return path
    }
    private var bodyCache: (x:Double,y:Double,w:Double,h:Double,image:Image)?
    func body(_ g: FridgeDoorGroceryMotion.Geo) -> Image {
        if let cache = bodyCache, cache.x == g.x, cache.y == g.y, cache.w == g.w, cache.h == g.h { return cache.image }
        let size = CGSize(width:g.w+10,height:g.h+14)
        let format = UIGraphicsImageRendererFormat()
        format.preferredRange = .standard
        let bitmap = UIGraphicsImageRenderer(size:size,format:format).image { renderer in
            let c = renderer.cgContext
            func color(_ hex:Int,_ alpha:Double = 1) -> CGColor {
                CGColor(srgbRed:Double(hex >> 16 & 255)/255,green:Double(hex >> 8 & 255)/255,blue:Double(hex & 255)/255,alpha:alpha)
            }
            func rect(_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ radius:Double,_ hex:Int,_ alpha:Double = 1) {
                c.setFillColor(color(hex,alpha))
                c.addPath(CGPath(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerWidth:radius,cornerHeight:radius,transform:nil))
                c.fillPath()
            }
            rect(10,14,g.w,g.h,30,0x8A6A4A,0.12)
            rect(g.w*0.12,g.h-6,24,16,6,0x4A5560)
            rect(g.w*0.88-24,g.h-6,24,16,6,0x4A5560)
            rect(0,0,g.w,g.h,30,0x8FD3C1)
            c.saveGState()
            c.addPath(CGPath(roundedRect:CGRect(x:14,y:14,width:g.w-28,height:g.h-28),cornerWidth:20,cornerHeight:20,transform:nil))
            c.clip()
            let gradient = CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB),colors:[color(0xFFFDF4),color(0xEAF3F6)] as CFArray,locations:[0,1])!
            c.drawLinearGradient(gradient,start:CGPoint(x:0,y:14),end:CGPoint(x:0,y:g.h-14),options:[])
            c.restoreGState()
            for shelf in 0..<4 { rect(20,14+(g.h-28)/4*Double(shelf+1)-10,g.w-40,6,0,0xCFE3EA) }
        }
        // Convert the cached body to P3 coordinates without changing its numerical
        // storage on upload. Float pixels avoid an extra 8-bit quantization round trip.
        let cg = bitmap.cgImage!
        let storage = CGContext(data:nil,width:cg.width,height:cg.height,bitsPerComponent:32,bytesPerRow:0,
                                space:CGColorSpace(name:CGColorSpace.extendedDisplayP3)!,
                                bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.floatComponents.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)!
        storage.draw(cg,in:CGRect(x:0,y:0,width:cg.width,height:cg.height))
        let encoded = storage.makeImage()!.copy(colorSpace:CGColorSpace(name:CGColorSpace.extendedSRGB)!)!
        let image = Image(uiImage:UIImage(cgImage:encoded,scale:bitmap.scale,orientation:.up))
        bodyCache = (g.x,g.y,g.w,g.h,image)
        return image
    }
    init() {
        func register(_ name: String) -> String {
            guard let url = Bundle.main.url(forResource: "FridgeDoorGrocery-\(name)", withExtension: "ttf"),
                  let data = CGDataProvider(url: url as CFURL), let font = CGFont(data),
                  let ps = font.postScriptName else { return "Helvetica" }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            return ps as String
        }
        black = register("Nunito-Black")
        bold = register("Nunito-ExtraBold")
        icons = register("Ionicons")
        // Skia uses unshaped glyphs, integral advances and AA-padded measurement bounds.
        let face = UIFont(name:black,size:11)! as CTFont
        let chars = Array("Low".utf16)
        var glyphs = [CGGlyph](repeating:0,count:chars.count)
        CTFontGetGlyphsForCharacters(face,chars,&glyphs,chars.count)
        var advances = [CGSize](repeating:.zero,count:chars.count)
        CTFontGetAdvancesForGlyphs(face,.horizontal,glyphs,&advances,glyphs.count)
        var combined = Path()
        var measure = CGRect.null
        var x = 0.0
        for (i,glyph) in glyphs.enumerated() {
            if let outline = CTFontCreatePathForGlyph(face,glyph,nil) {
                let bounds = outline.boundingBoxOfPath
                let padded = CGRect(x:floor(bounds.minX)-1+x,y:floor(bounds.minY)-1,
                                    width:ceil(bounds.maxX)-floor(bounds.minX)+2,
                                    height:ceil(bounds.maxY)-floor(bounds.minY)+2)
                measure = measure.union(padded)
                combined.addPath(Path(outline),transform:CGAffineTransform(a:1,b:0,c:0,d:-1,tx:x,ty:0))
            }
            x += advances[i].width.rounded()
        }
        lowPath = combined
        lowWidth = measure.width
        for (key, text, font, size, color) in [
            ("small", "Pantry", bold, 14.0, 0xA08A72),
            ("title", "Your fridge", black, 28.0, 0x2A2A33),
            ("note", "2 items running low · tap to restock", bold, 15.0, 0x2A2A33),
            ("basket", String(UnicodeScalar(61819)!), icons, 22.0, 0x2A2A33),
            ("alert", String(UnicodeScalar(61715)!), icons, 20.0, 0xFF5A4E)
        ] { labels[key] = label(text, font, size, color) }
        for i in 1...8 { labels["bag\(i)"] = label("\(i)", black, 11, 0xFFFFFF) }
    }
    private func label(_ text: String, _ family: String, _ size: Double, _ hex: Int) -> Label {
        let font = UIFont(name: family, size: size)!
        let color = UIColor(red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, alpha: 1)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let width = (text as NSString).size(withAttributes: attributes).width
        let scale = UIScreen.main.scale
        let box = CGSize(width: ceil(width * scale) / scale, height: ceil(font.lineHeight * scale) / scale)
        let image = UIGraphicsImageRenderer(size: box).image { _ in
            (text as NSString).draw(at: .zero, withAttributes: attributes)
        }
        return Label(image: Image(uiImage: image), size: box)
    }
}
