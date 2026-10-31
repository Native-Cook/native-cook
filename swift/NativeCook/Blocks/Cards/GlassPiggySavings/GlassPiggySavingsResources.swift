import AVFoundation
import CoreText
import CoreImage
import ImageIO
import SwiftUI
import UIKit

final class GlassPiggySavingsResources {
    static let shared=GlassPiggySavingsResources()
    let pig: Image
    let shadow: Image
    let coinHighlight: Image
    private var cachedLayout: GlassPiggySavingsMotion.Layout?
    private var cachedFull=Path()
    private var cachedLip=Path()
    private var faces: [String: CGFont]=[:]
    private var fonts: [String: CTFont]=[:]
    private init() {
        // Bake Gaussian masks once. Core Image radius is sigma, as in Skia MakeBlur.
        shadow=Self.blurredEllipse(CGSize(width:362,height:138),CGRect(x:56,y:56,width:250,height:26),14,.black)
        coinHighlight=Self.blurredEllipse(CGSize(width:90,height:90),CGRect(x:45-30*0.62,y:45-30*0.74,width:30*0.62,height:30*0.3),30*0.06)
        for weight in ["Black","ExtraBold","SemiBold"] {
            if let url=Bundle.main.url(forResource: "GlassPiggySavings-Nunito-\(weight)",withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
                if let data=CGDataProvider(url: url as CFURL), let font=CGFont(data) { faces[weight]=font }
            }
        }
        if let url=Bundle.main.url(forResource:"GlassPiggySavings-pig",withExtension:"png"),
           let source=CGImageSourceCreateWithURL(url as CFURL,nil),
           let image=CGImageSourceCreateImageAtIndex(source,0,[kCGImageSourceShouldCacheImmediately:true] as CFDictionary) {
            pig=Image(decorative:image,scale:1)
        } else { pig=Image(uiImage:UIImage()) }
        for (weight,sizes) in [("Black",[54.0,22,17]),("ExtraBold",[12.0,16,15]),("SemiBold",[14.0,13,12])] {
            for size in sizes { _=ctFont(weight,size) }
        }
    }
    private static func blurredEllipse(_ size: CGSize, _ ellipse: CGRect, _ sigma: Double, _ color: UIColor = .white) -> Image {
        let format=UIGraphicsImageRendererFormat(); format.scale=3; format.opaque=false
        let bitmap=UIGraphicsImageRenderer(size:size,format:format).image { renderer in
            renderer.cgContext.setFillColor(color.cgColor)
            renderer.cgContext.fillEllipse(in:ellipse)
        }
        guard let source=bitmap.cgImage else { return Image(uiImage:bitmap) }
        let input=CIImage(cgImage:source)
        let output=input.applyingFilter("CIGaussianBlur",parameters:[kCIInputRadiusKey:sigma*3])
        let srgb=CGColorSpace(name:CGColorSpace.sRGB)!
        let context=CIContext(options:[.workingColorSpace:srgb,.outputColorSpace:srgb])
        guard let image=context.createCGImage(output,from:input.extent) else { return Image(uiImage:bitmap) }
        return Image(decorative:image,scale:3)
    }
    func paths(_ l: GlassPiggySavingsMotion.Layout) -> (full: Path,lip: Path) {
        if let old=cachedLayout,old.w==l.w,old.h==l.h,old.top==l.top,old.bottom==l.bottom {
            return (cachedFull,cachedLip)
        }
        cachedLayout=l; cachedFull=Path(CGRect(x:0,y:0,width:l.w,height:l.h))
        let y0=l.slotY+6*GlassPiggySavingsMotion.scale*2.6
        var lip=Path()
        lip.move(to:CGPoint(x:l.slotX-80,y:-50)); lip.addLine(to:CGPoint(x:l.slotX+80,y:-50))
        lip.addLine(to:CGPoint(x:l.slotX+80,y:y0+16)); lip.addLine(to:CGPoint(x:l.slotX-80,y:y0-16)); lip.closeSubpath()
        cachedLip=lip
        return (cachedFull,cachedLip)
    }
    // All three source files share the PostScript name Nunito-ExtraLight despite containing
    // different static weights. Use the font from each file, as Expo's registered aliases do.
    func ctFont(_ weight: String, _ size: Double) -> CTFont {
        let key="\(weight)-\(size)"
        if let font=fonts[key] { return font }
        let font=faces[weight].map { CTFontCreateWithGraphicsFont($0,size,nil,nil) }
            ?? CTFontCreateWithName("Nunito-ExtraLight" as CFString,size,nil)
        fonts[key]=font; return font
    }
    func font(_ weight: String, _ size: Double) -> Font { Font(ctFont(weight,size)) }
    func height(_ weight: String, _ size: Double) -> Double {
        let f=ctFont(weight,size)
        return CTFontGetAscent(f)+CTFontGetDescent(f)+CTFontGetLeading(f)
    }
}

enum GlassPiggySavingsTheme {
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB,red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,opacity:alpha)
    }
    static let background=color(0x03110b)
    static let glow=Gradient(stops:[
        .init(color:color(0x20d780,0.78),location:0),
        .init(color:color(0x0e824e,0.42),location:0.38),
        .init(color:color(0x052818,0.12),location:0.72),
        .init(color:color(0x03110b,0),location:1)])
    static let circle=Path(ellipseIn:CGRect(x:-1,y:-1,width:2,height:2))
    static let rect=Path(CGRect(x:0,y:0,width:1,height:1))
}
