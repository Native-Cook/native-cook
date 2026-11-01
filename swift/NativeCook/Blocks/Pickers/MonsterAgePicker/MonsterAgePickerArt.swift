import SwiftUI
import UIKit
import CoreText

final class MonsterAgePickerArt {
    static let shared = MonsterAgePickerArt()
    struct Label { let path: CGPath; let width: Double }
    private var labels: [String: Label] = [:]
    private var staticImage: CGImage?
    private var staticSize = CGSize.zero
    private var staticTop = 0.0
    private var eyeArcs: [CGPath] = []
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> CGColor {
        CGColor(srgbRed: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255,
                blue: Double(hex&255)/255, alpha: alpha)
    }
    init() {
        for name in ["TitanOne-Regular", "Nunito-Black", "Nunito-ExtraBold"] {
            if let url = Bundle.main.url(forResource: "MonsterAgePicker-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        for n in 3...14 { for size in [22.0,28.0] { make("\(n)", "Nunito-Black", size) } }
    }
    private func make(_ text: String, _ name: String, _ size: Double) {
        let font = (UIFont(name:name,size:size) ?? UIFont.systemFont(ofSize:size)) as CTFont
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating:0,count:chars.count)
        CTFontGetGlyphsForCharacters(font,chars,&glyphs,chars.count)
        var advances = [CGSize](repeating:.zero,count:chars.count)
        CTFontGetAdvancesForGlyphs(font,.horizontal,glyphs,&advances,chars.count)
        let path = CGMutablePath()
        var x = 0.0
        for i in glyphs.indices {
            if let p = CTFontCreatePathForGlyph(font,glyphs[i],nil) {
                let transform = CGAffineTransform(a:1,b:0,c:0,d:-1,tx:x,ty:0)
                path.addPath(p,transform:transform)
            }
            // SkFont defaults to whole-point glyph advances, and measureText returns ink bounds.
            x += advances[i].width.rounded()
        }
        let ink = path.boundingBoxOfPath
        labels["\(text)|\(size)"] = Label(path:path,width:ceil(ink.maxX)-floor(ink.minX))
    }
    func text(_ c: CGContext, _ text: String, _ size: Double, _ x: Double, _ baseline: Double,
              _ color: UInt32, _ alpha: Double = 1) {
        guard let l = labels["\(text)|\(size)"] else { return }
        c.saveGState(); c.translateBy(x:x-l.width/2,y:baseline)
        c.setFillColor(Self.color(color,alpha)); c.addPath(l.path); c.fillPath(); c.restoreGState()
    }
    private func rect(_ c: CGContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double,
                      _ radius: Double, _ color: UInt32, _ alpha: Double = 1) {
        c.setFillColor(Self.color(color,alpha)); c.addPath(CGPath(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerWidth:radius,cornerHeight:radius,transform:nil)); c.fillPath()
    }
    private func oval(_ c: CGContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double,
                      _ color: UInt32, _ alpha: Double = 1) {
        c.setFillColor(Self.color(color,alpha)); c.fillEllipse(in:CGRect(x:x,y:y,width:w,height:h))
    }
    private func circle(_ c: CGContext, _ x: Double, _ y: Double, _ r: Double, _ color: UInt32) {
        oval(c,x-r,y-r,r*2,r*2,color)
    }
    func draw(_ c: CGContext, size: CGSize, top: Double, bottom: Double,
              frame o: MonsterAgePickerMotion.Frame, chosen: Int, pressed: Bool = false) {
        let W = size.width, cx = W/2, cy = (size.height*0.5).rounded(), R = W*0.5
        if staticSize != size || staticTop != top {
            staticSize = size; staticTop = top
            eyeArcs = [-1.0,1.0].map { k in
                let path = CGMutablePath()
                let transform = CGAffineTransform(a:R*0.13,b:0,c:0,d:R*0.1,tx:cx+k*R*0.34,ty:cy-R*0.5+R*0.08)
                path.addArc(center:.zero,radius:1,startAngle:200 * .pi/180,endAngle:340 * .pi/180,clockwise:false,transform:transform)
                return path
            }
            let format = UIGraphicsImageRendererFormat(); format.scale = UIScreen.main.scale
            staticImage = UIGraphicsImageRenderer(size:size,format:format).image { ctx in
                let c = ctx.cgContext
                c.setFillColor(Self.color(0x6E3DE8)); c.fill(CGRect(origin:.zero,size:size))
                oval(c,cx-R*0.8,cy+R*0.95,R*1.6,R*0.22,0x3A1680,0.25)
                circle(c,cx-R*0.72,cy-R*0.62,R*0.2,0x8E62F5)
                circle(c,cx+R*0.72,cy-R*0.62,R*0.2,0x8E62F5)
                c.saveGState()
                c.addPath(CGPath(roundedRect:CGRect(x:cx-R*0.92,y:cy-R*0.85,width:R*1.84,height:R*1.85),cornerWidth:R*0.8,cornerHeight:R*0.8,transform:nil)); c.clip()
                let gradient = CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB)!,colors:[Self.color(0xB996FF),Self.color(0x8C5CF6)] as CFArray,locations:[0,1])!
                c.drawRadialGradient(gradient,startCenter:CGPoint(x:cx-R*0.3,y:cy-R*0.5),startRadius:0,endCenter:CGPoint(x:cx-R*0.3,y:cy-R*0.5),endRadius:R*1.6,options:[.drawsAfterEndLocation]); c.restoreGState()
                for i in 0..<4 { rect(c,cx-89+Double(i)*46,top+10,40,6,3,i<2 ? 0xFFD84D : 0xFFFFFF,i<2 ? 1 : 0.25) }
            }.cgImage
        }
        if let image = staticImage {
            c.saveGState(); c.translateBy(x:0,y:size.height); c.scaleBy(x:1,y:-1)
            c.draw(image,in:CGRect(origin:.zero,size:size)); c.restoreGState()
        }
        let happy = MonsterAgePickerMotion.clamp((o.jaw-0.4)/0.4)
        for (index,k) in [-1.0,1.0].enumerated() {
            let ex = cx+k*R*0.34, ey = cy-R*0.5, lx = max(-1,min(1,o.look))*R*0.07
            circle(c,ex,ey,R*0.2,0xFFFFFF)
            circle(c,ex+lx,ey+R*0.03,R*0.095,0x24123F)
            circle(c,ex+lx+R*0.03,ey-R*0.01,R*0.03,0xFFFFFF)
            let lid = max(o.blink,happy)
            if lid > 0 {
                c.saveGState(); c.clip(to:CGRect(x:ex-R*0.22,y:ey-R*0.22,width:R*0.44,height:R*0.44*lid))
                circle(c,ex,ey,R*0.205,0x9D72FA); c.restoreGState()
                if happy > 0 {
                    c.addPath(eyeArcs[index]); c.setStrokeColor(Self.color(0x24123F,happy)); c.setLineWidth(5); c.setLineCap(.round); c.strokePath()
                }
            }
        }
        let stretch = min(1,abs(o.v)/12)*0.06, mw = W-76+W*stretch
        let mBot = cy+R*0.72, openTop = cy-R*0.14, closedTop = mBot-68-22
        let mTop = openTop+(closedTop-openTop)*o.jaw
        let mouth = CGPath(roundedRect:CGRect(x:cx-mw/2,y:mTop,width:mw,height:mBot-mTop),cornerWidth:min(46,(mBot-mTop)/2),cornerHeight:min(46,(mBot-mTop)/2),transform:nil)
        c.setFillColor(Self.color(0x3B1460)); c.addPath(mouth); c.fillPath()
        c.saveGState(); c.addPath(mouth); c.clip()
        oval(c,cx-mw*0.34,mBot-46,mw*0.68,90,0xFF7BA6)
        c.setFillColor(Self.color(0xF4EEFF))
        for i in -3...3 {
            let tx = cx+Double(i)*48
            c.move(to:CGPoint(x:tx-18,y:mTop-2)); c.addLine(to:CGPoint(x:tx+18,y:mTop-2)); c.addLine(to:CGPoint(x:tx,y:mTop+26)); c.closePath(); c.fillPath()
        }
        for i in 0..<12 {
            let dx = (Double(i)-o.off)*60
            if abs(dx)>mw/2+60 { continue }
            let near = MonsterAgePickerMotion.clamp(1-abs(Double(i)-o.off)), h = 54+14*near
            let ty = mBot-16-h+dx*dx/2600*0.2
            rect(c,cx+dx-25,ty,50,h+30,16,near>0.5 ? 0xFFFFFF : 0xE9DEFF)
            if near>0.01 { rect(c,cx+dx-25,ty,50,h+30,16,0xFFF7C2,0.35*near) }
            // Match the raster margin included by SkFont.measureText, absent from the glyph path.
            text(c,"\(i+3)",near>0.5 ? 28 : 22,cx+dx-1,ty+34+4*near,near>0.5 ? 0x3B1460 : 0x8B74B8)
        }
        c.restoreGState()
        let buttonY = size.height-bottom-20-58
        c.saveGState()
        if pressed { c.translateBy(x:cx,y:buttonY+29); c.scaleBy(x:0.96,y:0.96); c.translateBy(x:-cx,y:-buttonY-29) }
        rect(c,24,buttonY,W-48,58,29,0xE0AE1F)
        // RN's bottom border occupies the last four points inside the rounded box.
        c.saveGState(); c.clip(to:CGRect(x:24,y:buttonY,width:W-48,height:54))
        rect(c,24,buttonY,W-48,58,29,0xFFD84D); c.restoreGState()
        c.restoreGState()
    }
}
