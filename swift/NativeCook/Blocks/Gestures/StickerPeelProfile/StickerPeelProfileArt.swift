import CoreText
import SwiftUI
import UIKit

// Static die-cuts and face artwork, transcribed from spp-stickers.ts.
enum StickerPeelProfileArt {
    typealias R = StickerPeelProfileResources
    static func silhouette(_ k: Int) -> CGPath {
        let p = CGMutablePath()
        if k == 0 {
            p.addRoundedRect(in:CGRect(x:-48,y:-36,width:96,height:82),cornerWidth:30,cornerHeight:30)
            p.move(to:CGPoint(x:-40,y:-18)); p.addLine(to:CGPoint(x:-30,y:-46)); p.addLine(to:CGPoint(x:-12,y:-30)); p.closeSubpath()
            p.move(to:CGPoint(x:40,y:-18)); p.addLine(to:CGPoint(x:30,y:-46)); p.addLine(to:CGPoint(x:12,y:-30)); p.closeSubpath()
        } else if k == 1 {
            p.move(to:CGPoint(x:0,y:-10))
            curve(p,-9,-24,-28,-20,-26,-2); curve(p,-24,10,-10,18,0,24)
            curve(p,10,18,24,10,26,-2); curve(p,28,-20,9,-24,0,-10); p.closeSubpath()
        } else if k == 2 {
            p.addRoundedRect(in:CGRect(x:-42,y:-18,width:84,height:36),cornerWidth:18,cornerHeight:18)
        } else {
            p.move(to:CGPoint(x:2,y:-28))
            curve(p,10,-16,24,-10,22,8); curve(p,20,22,10,28,0,28)
            curve(p,-12,28,-22,20,-22,6); curve(p,-22,-6,-13,-10,-11,-20)
            curve(p,-6,-12,-4,-8,-2,-5); curve(p,2,-13,1,-20,2,-28); p.closeSubpath()
        }
        return p
    }
    static func curve(_ p: CGMutablePath, _ a: Double,_ b: Double,_ c: Double,_ d: Double,_ e: Double,_ f: Double) {
        p.addCurve(to:CGPoint(x:e,y:f),control1:CGPoint(x:a,y:b),control2:CGPoint(x:c,y:d))
    }
    static func face(_ c: CGContext, _ k: Int, _ sil: CGPath) {
        c.addPath(sil); c.setStrokeColor(UIColor.white.cgColor); c.setLineWidth(8); c.setLineJoin(.round); c.strokePath()
        c.saveGState(); c.addPath(sil); c.clip()
        switch k {
        case 0: R.gradient(c,[0xf8cf6a,0xefb24a,0xd9892a],CGPoint(x:0,y:-46),CGPoint(x:0,y:46))
        case 1: R.gradient(c,[0xff6f84,0xf2304d,0xc4122f],CGPoint(x:-20,y:-20),CGPoint(x:20,y:24))
        case 2: R.gradient(c,[0xff7ac8,0xf2489e,0xc93cd8],CGPoint(x:-42,y:0),CGPoint(x:42,y:0))
        default: R.gradient(c,[0xffe066,0xff9a2e,0xf2501f],CGPoint(x:0,y:-28),CGPoint(x:0,y:28))
        }
        c.restoreGState()
        if k == 0 {
            c.setFillColor(R.ui(0xb8661c,0.35).cgColor); c.fillEllipse(in:CGRect(x:-40,y:-34,width:80,height:26))
            c.setFillColor(R.ui(0x3a2412).cgColor)
            c.fillEllipse(in:CGRect(x:-22,y:-2,width:9,height:12)); c.fillEllipse(in:CGRect(x:13,y:-2,width:9,height:12))
            c.setStrokeColor(R.ui(0x3a2412).cgColor); c.setLineWidth(2.4); c.setLineCap(.round); c.setLineJoin(.miter); c.setMiterLimit(4)
            c.move(to:CGPoint(x:-7,y:14)); c.addQuadCurve(to:CGPoint(x:0,y:14),control:CGPoint(x:-3.5,y:19))
            c.addQuadCurve(to:CGPoint(x:7,y:14),control:CGPoint(x:3.5,y:19)); c.strokePath()
            c.setFillColor(R.ui(0xff8a8a,0.55).cgColor)
            c.fillEllipse(in:CGRect(x:-34,y:12,width:14,height:8)); c.fillEllipse(in:CGRect(x:20,y:12,width:14,height:8))
            for i in 0..<3 {
                c.move(to:CGPoint(x:-46,y:4+i*5)); c.addLine(to:CGPoint(x:-36,y:6+i*4)); c.strokePath()
                c.move(to:CGPoint(x:46,y:4+i*5)); c.addLine(to:CGPoint(x:36,y:6+i*4)); c.strokePath()
            }
        } else if k == 2 {
            let font = R.font("Bold",24) as CTFont
            let chars = Array("slay".utf16)
            var glyphs = [CGGlyph](repeating:0,count:chars.count)
            CTFontGetGlyphsForCharacters(font,chars,&glyphs,chars.count)
            var advances = [CGSize](repeating:.zero,count:chars.count)
            CTFontGetAdvancesForGlyphs(font,.horizontal,glyphs,&advances,chars.count)
            let width = advances.reduce(0) { $0+$1.width }
            var x = 0.0
            let positions = advances.map { advance -> CGPoint in
                defer { x += advance.width }; return CGPoint(x:x,y:0)
            }
            c.saveGState(); c.concatenate(CGAffineTransform(a:1,b:0,c:-0.2,d:1,tx:0,ty:0))
            c.translateBy(x:-width/2+2,y:8); c.scaleBy(x:1,y:-1)
            c.setFillColor(UIColor.white.cgColor)
            CTFontDrawGlyphs(font,glyphs,positions,glyphs.count,c)
            c.restoreGState()
        } else if k == 3 {
            let p = CGMutablePath(); p.move(to:CGPoint(x:1,y:-4))
            curve(p,6,4,12,8,11,16); curve(p,10,23,5,26,0,26); curve(p,-6,26,-11,22,-11,15)
            curve(p,-11,9,-6,6,-5,1); curve(p,-2,5,-1,7,0,8); curve(p,2,4,1,0,1,-4); p.closeSubpath()
            c.saveGState(); c.addPath(p); c.clip()
            R.gradient(c,[0xfff2a8,0xffc33a],CGPoint(x:0,y:-4),CGPoint(x:0,y:26)); c.restoreGState()
        }
        c.saveGState(); c.addPath(sil); c.clip()
        R.gradient(c,[0xffffff,0xffffff],CGPoint(x:-30,y:-30),CGPoint(x:10,y:10),[0.45,0])
        c.restoreGState()
    }
    static func halfPlane(_ px: Double,_ py: Double,_ nx: Double,_ ny: Double) -> Path {
        let tx = -ny, ty = nx, b = 400.0
        var p = Path(); p.move(to:CGPoint(x:px+tx*b,y:py+ty*b))
        p.addLine(to:CGPoint(x:px+tx*b+nx*b,y:py+ty*b+ny*b))
        p.addLine(to:CGPoint(x:px-tx*b+nx*b,y:py-ty*b+ny*b))
        p.addLine(to:CGPoint(x:px-tx*b,y:py-ty*b)); p.closeSubpath(); return p
    }
    static func draw(_ context: GraphicsContext, _ ss: [StickerPeelProfileMotion.Sticker], _ order: [Int]) {
        let res = R.shared
        let imageBox = CGRect(x:-80,y:-80,width:160,height:160)
        for i in order {
            let s = ss[i]; var c = context
            c.translateBy(x:s.x,y:s.y)
            c.rotate(by:.degrees(s.rot+max(-14,min(14,s.vx*0.015))))
            let sc = (1+0.1*s.lift)*s.k
            c.scaleBy(x:sc*(1+s.sq),y:sc*(1-s.sq))
            if s.lift > 0.01 {
                var sh = c
                sh.addFilter(.blur(radius:6+10*s.lift))
                let h = StickerPeelProfileMotion.half[i]
                sh.fill(Path(roundedRect:CGRect(x:-h.0+6,y:-h.1+14*s.lift,width:h.0*2-12,height:h.1*2),cornerRadius:16),with:.color(.black.opacity(0.4*s.lift)))
            }
            if s.f < 0.002 { c.draw(res.faces[i],in:imageBox); continue }
            let h = StickerPeelProfileMotion.half[i]
            let cx = (s.corner == 0 || s.corner == 3) ? -(h.0+4) : h.0+4
            let cy = (s.corner == 0 || s.corner == 1) ? -(h.1+4) : h.1+4
            let dl = hypot(cx,cy), nx = cx/dl, ny = cy/dl, d = s.f*dl
            let px = cx-nx*d, py = cy-ny*d
            var flat = c; flat.clip(to:halfPlane(px,py,-nx,-ny)); flat.draw(res.faces[i],in:imageBox)
            let dist = 2*(px*nx+py*ny)
            let reflection = CGAffineTransform(a:1-2*nx*nx,b:-2*nx*ny,c:-2*nx*ny,d:1-2*ny*ny,tx:dist*nx,ty:dist*ny)
            let clip = halfPlane(px,py,nx,ny)
            var shadow = c; shadow.translateBy(x:2,y:4); shadow.concatenate(reflection); shadow.clip(to:clip)
            shadow.draw(res.shadows[i],in:imageBox)
            var back = c; back.concatenate(reflection); back.clip(to:clip)
            let shading = GraphicsContext.Shading.linearGradient(Gradient(colors:[R.color(0xd9d6dd),R.color(0xf6f4f8),.white]),startPoint:CGPoint(x:px,y:py),endPoint:CGPoint(x:px+nx*d,y:py+ny*d))
            let sil = res.paths[i]
            back.stroke(sil,with:shading,style:StrokeStyle(lineWidth:8,lineJoin:.round))
            back.fill(sil,with:shading)
        }
    }
}
