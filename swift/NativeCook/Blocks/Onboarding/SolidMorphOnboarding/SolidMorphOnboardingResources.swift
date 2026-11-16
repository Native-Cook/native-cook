import CoreText
import SwiftUI
import UIKit

/// One packed texture keeps the UI and the field in the same color-effect pass.
@MainActor
final class SolidMorphOnboardingResources {
    static let register: Void = {
        for weight in ["Regular", "SemiBold"] {
            if let url = Bundle.main.url(forResource: "SolidMorphOnboarding-Outfit-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    struct Atlas {
        let image: Image
        let density: Double
        let glyphX: Double
    }
    private var key: SolidMorphOnboardingLayout?
    private var pixelScale = 0.0
    private var cached: Atlas?
    private var fonts: [String: CGFont] = [:]

    init() { _ = Self.register }

    func atlas(_ l: SolidMorphOnboardingLayout, displayScale: Double) -> Atlas {
        if key == l, pixelScale == displayScale, let cached { return cached }
        let density = min(3,displayScale,8190/max(l.width,l.height*3))
        let uiWidth = Int(ceil(l.width*density)), h = Int(ceil(l.height*3*density))
        let glyphX = uiWidth+2, w = glyphX+256
        let context = CGContext(data: nil, width: w, height: max(h,768), bitsPerComponent: 8, bytesPerRow: 0,
                                space: CGColorSpace(name: CGColorSpace.sRGB)!,
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.translateBy(x: 0,y: Double(max(h,768))); context.scaleBy(x: 1,y: -1)
        context.saveGState(); context.scaleBy(x: density,y: density)
        for (i,page) in SolidMorphOnboardingPages.all.enumerated() {
            context.saveGState(); context.translateBy(x: 0,y: Double(i)*l.height)
            drawPage(context,page,i,l)
            context.restoreGState()
        }
        context.restoreGState()
        let url = Bundle.main.url(forResource: "SolidMorphOnboarding-glyphs",withExtension: "png")!
        let glyphs = CGImage(pngDataProviderSource: CGDataProvider(url: url as CFURL)!,decode: nil,shouldInterpolate: false,intent: .defaultIntent)!
        // CGContext image drawing uses an upward image axis even in the flipped page context.
        context.saveGState(); context.translateBy(x: Double(glyphX),y: 768); context.scaleBy(x: 1,y: -1)
        context.interpolationQuality = .none
        context.draw(glyphs,in: CGRect(x: 0,y: 0,width: 256,height: 768)); context.restoreGState()
        let result = Atlas(image: Image(decorative: context.makeImage()!,scale: 1),density: density,glyphX: Double(glyphX))
        key = l; pixelScale = displayScale; cached = result
        return result
    }

    private func color(_ hex: UInt32) -> CGColor {
        CGColor(srgbRed: Double(hex >> 16 & 255)/255,green: Double(hex >> 8 & 255)/255,blue: Double(hex & 255)/255,alpha: 1)
    }
    private func font(_ size: Double, semi: Bool) -> CTFont {
        let weight = semi ? "SemiBold" : "Regular"
        if fonts[weight] == nil {
            let url = Bundle.main.url(forResource: "SolidMorphOnboarding-Outfit-\(weight)",withExtension: "ttf")!
            fonts[weight] = CGFont(CGDataProvider(url: url as CFURL)!)!
        }
        return CTFontCreateWithGraphicsFont(fonts[weight]!,size,nil,nil)
    }

    /// SkParagraph rounds the line height, retains fractional descent, and puts half
    /// of letterSpacing before the first glyph. Outfit has ascender:descender = 1000:260.
    private func text(_ c: CGContext, _ text: String, _ x: Double, _ y: Double, _ width: Double, _ height: Double,
                      _ size: Double, _ hex: UInt32, semi: Bool = false, tracking: Double = 0,
                      lineHeight: Double = 1.2, center: Bool = false, middle: Bool = false) {
        let font = font(size,semi: semi)
        var attributes: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): color(hex)
        ]
        // Explicit zero disables CoreText's natural pair kerning; SkParagraph keeps it.
        if tracking != 0 { attributes[.kern] = tracking }
        let attributed = NSAttributedString(string: text,attributes: attributes)
        let typesetter = CTTypesetterCreateWithAttributedString(attributed)
        let str = text as NSString
        var lines: [CTLine] = [], start = 0
        while start < str.length {
            let suggested = max(1,CTTypesetterSuggestLineBreak(typesetter,start,width))
            var length = suggested
            // Paragraph painting removes trailing spaces/newlines from alignment width.
            while length > 0, CharacterSet.whitespacesAndNewlines.contains(UnicodeScalar(str.character(at: start+length-1))!) { length -= 1 }
            lines.append(CTTypesetterCreateLine(typesetter,CFRange(location: start,length: length)))
            start += suggested
        }
        let pitch = (size*lineHeight).rounded()
        let paragraphHeight = Double(lines.count)*pitch
        let top = y+(middle ? max(0,(height-paragraphHeight)/2) : 0)
        let descent = size*lineHeight*(260.0/1260.0)
        for (i,line) in lines.enumerated() {
            let advance = CTLineGetTypographicBounds(line,nil,nil,nil)
            let left = center ? (width-advance)/2 : tracking/2
            c.saveGState(); c.translateBy(x: x+left,y: top+Double(i+1)*pitch-descent)
            c.scaleBy(x: 1,y: -1); c.textMatrix = .identity; c.textPosition = .zero
            CTLineDraw(line,c); c.restoreGState()
        }
    }

    private func drawPage(_ c: CGContext, _ page: SolidMorphOnboardingPages.Page, _ index: Int, _ l: SolidMorphOnboardingLayout) {
        let s = l.scale, f = l.fontScale, x = 17*s, dark = page.light == 0
        let fg: UInt32 = dark ? 0xFCFCFB : 0x0A0A0B
        func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, _ hex: UInt32, stroke: Double = 0) {
            let path = CGPath(roundedRect: CGRect(x: x,y: y,width: w,height: h),cornerWidth: r,cornerHeight: r,transform: nil)
            c.addPath(path)
            if stroke > 0 { c.setStrokeColor(color(hex)); c.setLineWidth(stroke); c.strokePath() }
            else { c.setFillColor(color(hex)); c.fillPath() }
        }
        let segW = min(91*s,(l.width-l.skipWidth-2*x)/3-4*s)
        for i in 0..<3 { rect(x+Double(i)*(segW+4*s),l.progressY,segW,4*s,0,i <= index ? fg : (dark ? 0x515154 : 0xD0D0D2)) }
        text(c,"Skip",l.width-l.skipWidth,l.progressY-8*s,l.skipWidth,32*s*f,16.5*s*f,fg)
        let chipH = 32*s*f, chipW = min(l.width-2*x,(((Double(page.chip.count)*6.8+24)*s*f)/4).rounded()*4)
        rect(x,l.chipY,chipW,chipH,8*s,dark ? 0x262627 : 0xECECEE)
        text(c,page.chip,x+12*s,l.chipY,chipW-12*s,chipH,13.5*s*f,fg,middle: true)
        text(c,page.title,x,l.titleY,l.width-2*x,l.titleHeight,l.titleSize,fg,semi: true,tracking: -0.6*s,lineHeight: 48/40)
        text(c,page.body,x,l.bodyY,l.width-2*x,l.bodyHeight,l.bodySize,dark ? 0xB6B6BB : 0x424246,lineHeight: 22/16)
        let w = l.buttonWidth, sx = l.largeText ? x : x+w+8*s, sy = l.largeText ? l.footerY+l.buttonHeight+8*s : l.footerY
        rect(x,l.footerY,w,l.buttonHeight,28*s,fg)
        rect(sx,sy,w,l.buttonHeight,28*s,dark ? 0x1D1D1F : 0xF2F2F3)
        if dark { rect(sx,sy,w,l.buttonHeight,28*s,0x555558,stroke: 0.65*s) }
        text(c,"Create account",x,l.footerY,w,l.buttonHeight,17*f*s,dark ? 0x0A0A0B : 0xFCFCFB,center: true,middle: true)
        text(c,"Log in",sx,sy,w,l.buttonHeight,17*f*s,fg,center: true,middle: true)
    }
}
