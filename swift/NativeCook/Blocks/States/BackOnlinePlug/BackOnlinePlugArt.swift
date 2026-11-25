import CoreText
import CoreImage
import UIKit

final class BackOnlinePlugArt {
    struct Layout {
        var ox: Double, fy: Double, sx: Double, sy: Double, height: Double
        init(size: CGSize) { ox = (size.width-393)/2; fy = size.height/852; sx = ox+196.5; sy = 560*fy; height = size.height }
        func geometry(_ f: BackOnlinePlugMotion.Frame) -> (x:Double,y:Double,face:Double) {
            let scale = 1+0.42*max(f.z,0)+0.3*min(f.z,0)
            return (sx-64*f.a+4*f.sway,sy+92*f.a+2*f.sway,138*scale)
        }
        func plugHit(_ p: CGPoint, frame: BackOnlinePlugMotion.Frame) -> Bool {
            let g = geometry(frame)
            return hypot(p.x-g.x,p.y-g.y) <= g.face/2+16
        }
        func buttonHit(_ p: CGPoint) -> Bool { p.x >= ox+32 && p.x <= ox+361 && p.y >= 772*fy-6 && p.y <= 772*fy+64 }
    }
    private static func url(_ name: String, _ ext: String) -> URL { Bundle.main.url(forResource:"BackOnlinePlug-"+name,withExtension:ext)! }
    private let plug = UIImage(contentsOfFile:url("plug-back","png").path)!
    private let socket = UIImage(contentsOfFile:url("socket-front","png").path)!
    private static func font(_ file: String, _ size: Double) -> CTFont {
        let url = url(file,"ttf")
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
        let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as! [CTFontDescriptor]
        return CTFontCreateWithFontDescriptor(descriptors[0],size,nil)
    }
    private let title = font("BarlowCondensed-ExtraBold",78)
    private let line = font("Inter-Medium",16)
    private let button = font("Inter-SemiBold",17)
    private let chip = font("Inter-SemiBold",15)
    private static let blurContext = CIContext()
    private var plugShadows = [Int: UIImage]()
    private static func blurred(_ size: CGSize, sigma: Double, draw: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let source = UIGraphicsImageRenderer(size:size,format:format).image { draw($0.cgContext) }
        let input = CIImage(image:source)!
        let blurred = input.applyingFilter("CIGaussianBlur",parameters:[kCIInputRadiusKey:sigma])
        return UIImage(cgImage:blurContext.createCGImage(blurred,from:input.extent)!)
    }
    private let plateShadow = blurred(CGSize(width:242,height:242),sigma:10.0/3) { c in
        c.setFillColor(UIColor.black.withAlphaComponent(0.28).cgColor)
        c.addPath(UIBezierPath(roundedRect:CGRect(x:16,y:16,width:210,height:210),cornerRadius:18).cgPath); c.fillPath()
    }
    // Cache normalized soft masks; geometry and opacity stay continuous at the actual depth.
    private func plugShadow(_ depth: Double) -> UIImage {
        let key = Int((depth*60).rounded())
        if let image = plugShadows[key] { return image }
        let z = Double(key)/60, radius = 138*(1+0.42*z)/2*0.98
        let image = Self.blurred(CGSize(width:256,height:256),sigma:(3+16*z)/3/radius*100) { c in
            c.setFillColor(UIColor.black.cgColor); c.fillEllipse(in:CGRect(x:28,y:28,width:200,height:200))
        }
        plugShadows[key] = image
        return image
    }
    private func color(_ rgb: [Double], _ k: Double = 1, _ alpha: Double = 1) -> CGColor {
        CGColor(colorSpace:CGColorSpace(name:CGColorSpace.sRGB)!,components:rgb.map { min(255,($0*k).rounded())/255 }+[alpha])!
    }
    private func gradient(_ colors: [CGColor], _ stops: [CGFloat]) -> CGGradient {
        CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB),colors:colors as CFArray,locations:stops)!
    }
    private func text(_ c: CGContext, _ font: CTFont, _ string: String, _ x: Double, _ y: Double,
                      _ color: CGColor, tracking: Double = 0) {
        let chars = Array(string.utf16); var glyphs = [CGGlyph](repeating:0,count:chars.count)
        CTFontGetGlyphsForCharacters(font,chars,&glyphs,chars.count)
        var advances = [CGSize](repeating:.zero,count:chars.count)
        CTFontGetAdvancesForGlyphs(font,.horizontal,glyphs,&advances,glyphs.count)
        var dx = 0.0
        let positions = advances.map { advance -> CGPoint in defer { dx += advance.width+tracking }; return CGPoint(x:dx,y:0) }
        c.saveGState(); c.translateBy(x:x,y:y); c.scaleBy(x:1,y:-1); c.setFillColor(color)
        CTFontDrawGlyphs(font,glyphs,positions,glyphs.count,c); c.restoreGState()
    }
    func draw(_ c: CGContext, size: CGSize, frame f: BackOnlinePlugMotion.Frame) {
        let l = Layout(size:size)
        c.drawRadialGradient(gradient([color(f.wall,1.18),color(f.wall,0.78)],[20/620,1]),
                             startCenter:CGPoint(x:l.sx,y:l.sy),startRadius:0,
                             endCenter:CGPoint(x:l.sx,y:l.sy),endRadius:620,
                             options:[.drawsBeforeStartLocation,.drawsAfterEndLocation])
        plateShadow.draw(in:CGRect(x:l.sx-105+6-16,y:l.sy-105+10-16,width:242,height:242))
        socket.draw(in:CGRect(x:l.sx-105,y:l.sy-105,width:210,height:210*639/640))
        let g = l.geometry(f), face = g.face, top = g.y-face/2, h = face*723/419, depth = max(f.z,0)
        let radius = face/2*0.98, maskScale = radius/100
        plugShadow(depth).draw(in:CGRect(x:g.x+4+18*depth-128*maskScale,y:g.y+6+28*depth-128*maskScale,width:256*maskScale,height:256*maskScale),
                               blendMode:.normal,alpha:0.45-0.2*depth)
        let cw = face*54/419, ccx = g.x-face/2+face*208.5/419, cableTop = top+h-4
        if cableTop < l.height {
            c.saveGState(); c.clip(to:CGRect(x:ccx-cw/2,y:cableTop,width:cw,height:l.height-cableTop))
            c.drawLinearGradient(gradient([color([217,217,215]),color([244,244,242]),color([189,189,187])],[0,0.35,1]),
                                 start:CGPoint(x:ccx-cw/2,y:0),end:CGPoint(x:ccx+cw/2,y:0),
                                 options:[.drawsBeforeStartLocation,.drawsAfterEndLocation]); c.restoreGState()
        }
        plug.draw(in:CGRect(x:g.x-face/2,y:top,width:face,height:h))
        if f.spark > 0 && f.spark < 1 {
            let s = f.spark
            c.saveGState(); c.setBlendMode(.plusLighter)
            c.setStrokeColor(color([255,246,176],1,0.9*(1-s))); c.setLineWidth(3*(1-s)+0.5)
            let r = face/2+4+22*s
            c.strokeEllipse(in:CGRect(x:g.x-r,y:g.y-r,width:2*r,height:2*r))
            c.setStrokeColor(color([255,246,176],1,1-s)); c.setLineWidth(2); c.setLineCap(.round)
            let r0 = face/2+6+16*s, r1 = r0+14*(1-s)+3
            for i in 0..<10 {
                let a = Double(i)/10*6.283+0.2
                c.move(to:CGPoint(x:g.x+cos(a)*r0,y:g.y+sin(a)*r0))
                c.addLine(to:CGPoint(x:g.x+cos(a)*r1,y:g.y+sin(a)*r1)); c.strokePath()
            }; c.restoreGState()
        }
        for i in 0..<3 {
            let u = BackOnlinePlugMotion.clamp(f.flip*1.6-Double(i)*0.3), alpha = abs(u-0.5)*2
            if alpha > 0 {
                c.saveGState(); c.translateBy(x:l.ox+30,y:[150.0,224,298][i]*l.fy); c.scaleBy(x:1,y:max(0.05,alpha))
                text(c,title,(u >= 0.5 ? ["BACK","ON","LINE."] : ["STILL","OFF","LINE."])[i],0,0,color([255,255,255],1,alpha),tracking:4); c.restoreGState()
            }
        }
        let on = f.flip >= 0.5
        // Designer correction: subtitle ink changes with its text, not at the earlier live beat.
        let ink = color(on ? [166,215,182] : [169,181,174])
        text(c,line,on ? "Your 3 drafts are syncing now." : "Plug back in to sync 3 drafts.",l.ox+32,350*l.fy,ink)
        let y = 772*l.fy
        c.setFillColor(color([255,255,255],1,on ? 0.14 : 1))
        c.addPath(UIBezierPath(roundedRect:CGRect(x:l.ox+32,y:y,width:on ? 160 : 329,height:on ? 44 : 58),cornerRadius:on ? 22 : 29).cgPath); c.fillPath()
        if on { text(c,chip,"Syncing \(f.chip) of 3",l.ox+54,y+28,color([255,255,255])) }
        else {
            let width = CTLineGetTypographicBounds(CTLineCreateWithAttributedString(NSAttributedString(string:"Try again",attributes:[.font:button]) as CFAttributedString),nil,nil,nil)
            text(c,button,"Try again",l.ox+196.5-width/2,y+35,color([31,58,42]))
        }
    }
}
