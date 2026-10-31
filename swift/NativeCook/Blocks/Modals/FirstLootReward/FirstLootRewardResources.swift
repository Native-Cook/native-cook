import SwiftUI
import UIKit

final class FirstLootRewardResources {
    static let shared = FirstLootRewardResources()
    static let ro = 155.0 / sqrt(3), ri = 102.0 / sqrt(3)
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red:Double((hex>>16)&255)/255, green:Double((hex>>8)&255)/255, blue:Double(hex&255)/255, opacity:alpha)
    }
    static func ui(_ hex: Int, _ alpha: Double = 1) -> UIColor {
        UIColor(red:Double((hex>>16)&255)/255, green:Double((hex>>8)&255)/255, blue:Double(hex&255)/255, alpha:alpha)
    }
    // Skia interpolates straight sRGB and alpha separately. SwiftUI interpolates
    // premultiplied stops; sampling each segment preserves Skia's translucent colours.
    private static func straightGradient(_ colors:[(Int,Double)],_ positions:[Double]) -> Gradient {
        var stops:[Gradient.Stop]=[]
        for segment in 0..<(colors.count-1) {
            let (a,aa)=colors[segment], (b,ba)=colors[segment+1]
            for i in 0...64 {
                let k=Double(i)/64
                func channel(_ shift:Int) -> Double {
                    let from=Double((a>>shift)&255), to=Double((b>>shift)&255)
                    return (from+(to-from)*k)/255
                }
                stops.append(.init(color:Color(.sRGB,red:channel(16),green:channel(8),blue:channel(0),opacity:aa+(ba-aa)*k),
                    location:positions[segment]+(positions[segment+1]-positions[segment])*k))
            }
        }
        return Gradient(stops:stops)
    }
    static let auraGradient=straightGradient([(0x3a206e,0.5),(0x221242,0.2),(0x140a28,0)],[0,0.4,1])
    static let bloomGradient=straightGradient([(0xebd7ff,1),(0xaf78fa,0.82),(0x7846dc,0)],[0,0.5,1])
    static func hex(_ r: Double, _ rc: Double) -> CGPath {
        let p = CGMutablePath(), ri = (r*cos(.pi/6)-rc)/cos(.pi/6)
        for i in 0..<6 {
            let a = Double.pi/6+Double(i)*Double.pi/3
            for j in 0...10 {
                let n = a-Double.pi/6+Double(j)/10*Double.pi/3
                let pt = CGPoint(x:ri*cos(a)+rc*cos(n), y:ri*sin(a)+rc*sin(n))
                if i == 0 && j == 0 { p.move(to:pt) } else { p.addLine(to:pt) }
            }
        }
        p.closeSubpath(); return p
    }
    static func star(_ x: Double, _ y: Double, _ s: Double) -> Path {
        let w = s*0.28*0.35
        var p = Path(); p.move(to:CGPoint(x:x,y:y-s))
        p.addQuadCurve(to:CGPoint(x:x+s,y:y),control:CGPoint(x:x+w,y:y-w))
        p.addQuadCurve(to:CGPoint(x:x,y:y+s),control:CGPoint(x:x+w,y:y+w))
        p.addQuadCurve(to:CGPoint(x:x-s,y:y),control:CGPoint(x:x-w,y:y+w))
        p.addQuadCurve(to:CGPoint(x:x,y:y-s),control:CGPoint(x:x-w,y:y-w)); p.closeSubpath(); return p
    }
    let outer = Path(hex(ro,15)), face = Path(hex(ro-9,11)), inner = Path(hex(ri,11))
    let unitCircle=Path(ellipseIn:CGRect(x:-1,y:-1,width:2,height:2))
    let ring=Path(ellipseIn:CGRect(x:-33,y:-33,width:66,height:66))
    let innerGlow=Path(ellipseIn:CGRect(x:-26,y:-22,width:52,height:52))
    let chestHalo=Path(ellipseIn:CGRect(x:-30,y:-30,width:60,height:60))
    let chestLight=Path(ellipseIn:CGRect(x:-30,y:-10,width:60,height:26))
    let chestStrip=Path(ellipseIn:CGRect(x:-22,y:-1,width:44,height:11))
    let rim: Image, faceRecess: Image, body: Image, lid: Image
    let swirl:Path = {
        var p=Path(); p.move(to:CGPoint(x:250,y:160))
        p.addCurve(to:CGPoint(x:370,y:40),control1:CGPoint(x:250,y:90),control2:CGPoint(x:330,y:40))
        p.addCurve(to:CGPoint(x:360,y:120),control1:CGPoint(x:420,y:40),control2:CGPoint(x:420,y:120))
        p.addCurve(to:CGPoint(x:360,y:-10),control1:CGPoint(x:300,y:120),control2:CGPoint(x:300,y:20)); return p
    }()
    // Exact notifications glyph from the RN Ionicons font, at 12 pt (MIT).
    let bell:Path = {
        var p=Path()
        p.move(to:CGPoint(x:5.671875,y:9.703125))
        p.addQuadCurve(to:CGPoint(x:5.98828125,y:9.73828125),control:CGPoint(x:5.7890625,y:9.7265625))
        p.addQuadCurve(to:CGPoint(x:6.3046875,y:9.703125),control:CGPoint(x:6.1875,y:9.75))
        p.addQuadCurve(to:CGPoint(x:7.3359375,y:8.765625),control:CGPoint(x:7.03125,y:9.515625))
        p.addLine(to:CGPoint(x:7.3828125,y:8.6015625))
        p.addLine(to:CGPoint(x:7.640625,y:8.484375))
        p.addQuadCurve(to:CGPoint(x:8.3203125,y:8.0390625),control:CGPoint(x:8.0390625,y:8.296875))
        p.addQuadCurve(to:CGPoint(x:9.3515625,y:6.0),control:CGPoint(x:9.1640625,y:7.3359375))
        p.addQuadCurve(to:CGPoint(x:9.375,y:5.203125),control:CGPoint(x:9.3515625,y:5.8359375))
        p.addQuadCurve(to:CGPoint(x:9.515625,y:3.890625),control:CGPoint(x:9.3984375,y:4.3359375))
        p.addQuadCurve(to:CGPoint(x:10.265625,y:2.5546875),control:CGPoint(x:9.65625,y:3.3046875))
        p.addQuadCurve(to:CGPoint(x:10.5,y:1.96875),control:CGPoint(x:10.4765625,y:2.2734375))
        p.addQuadCurve(to:CGPoint(x:10.4296875,y:1.640625),control:CGPoint(x:10.5,y:1.78125))
        p.addQuadCurve(to:CGPoint(x:9.890625,y:1.171875),control:CGPoint(x:10.265625,y:1.2890625))
        p.addQuadCurve(to:CGPoint(x:6.0,y:1.125),control:CGPoint(x:9.796875,y:1.125))
        p.addLine(to:CGPoint(x:2.203125,y:1.125))
        p.addLine(to:CGPoint(x:2.0859375,y:1.171875))
        p.addQuadCurve(to:CGPoint(x:1.5703125,y:1.640625),control:CGPoint(x:1.734375,y:1.3125))
        p.addQuadCurve(to:CGPoint(x:1.5,y:1.96875),control:CGPoint(x:1.5,y:1.78125))
        p.addQuadCurve(to:CGPoint(x:1.734375,y:2.5546875),control:CGPoint(x:1.5234375,y:2.2734375))
        p.addQuadCurve(to:CGPoint(x:2.484375,y:3.890625),control:CGPoint(x:2.34375,y:3.3046875))
        p.addQuadCurve(to:CGPoint(x:2.625,y:5.203125),control:CGPoint(x:2.6015625,y:4.3125))
        p.addQuadCurve(to:CGPoint(x:2.765625,y:6.515625),control:CGPoint(x:2.6484375,y:6.09375))
        p.addQuadCurve(to:CGPoint(x:4.359375,y:8.484375),control:CGPoint(x:3.09375,y:7.8515625))
        p.addLine(to:CGPoint(x:4.6171875,y:8.6015625))
        p.addLine(to:CGPoint(x:4.6640625,y:8.765625))
        p.addQuadCurve(to:CGPoint(x:5.671875,y:9.703125),control:CGPoint(x:4.96875,y:9.515625))
        p.closeSubpath()
        p.move(to:CGPoint(x:4.3828125,y:0.3515625))
        p.addQuadCurve(to:CGPoint(x:4.3828125,y:0.3515625),control:CGPoint(x:4.3828125,y:0.3515625))
        p.addQuadCurve(to:CGPoint(x:6.0,y:0.375),control:CGPoint(x:4.40625,y:0.375))
        p.addQuadCurve(to:CGPoint(x:7.6171875,y:0.3515625),control:CGPoint(x:7.59375,y:0.375))
        p.addQuadCurve(to:CGPoint(x:7.5703125,y:0.1171875),control:CGPoint(x:7.6875,y:0.28125))
        p.addQuadCurve(to:CGPoint(x:6.4453125,y:-0.703125),control:CGPoint(x:7.171875,y:-0.4921875))
        p.addQuadCurve(to:CGPoint(x:5.5546875,y:-0.703125),control:CGPoint(x:6.0,y:-0.796875))
        p.addQuadCurve(to:CGPoint(x:4.4296875,y:0.1171875),control:CGPoint(x:4.828125,y:-0.4921875))
        p.addQuadCurve(to:CGPoint(x:4.3828125,y:0.3515625),control:CGPoint(x:4.3125,y:0.28125))
        p.closeSubpath()
        return p
    }()
    private init() {
        rim = Self.image { c in
            Self.gradient(c, Self.hex(Self.ro,15), [0xe3c9ff,0xc697f8,0xad80ea,0x7f5aca], [0,0.12,0.55,1], CGPoint(x:0,y:-Self.ro),CGPoint(x:0,y:Self.ro))
        }
        faceRecess = Self.image { c in
            Self.gradient(c,Self.hex(Self.ro-9,11),[0xb386ef,0x8a62d2],[0,1],CGPoint(x:-Self.ro*0.4,y:-Self.ro),CGPoint(x:Self.ro*0.4,y:Self.ro))
            c.saveGState(); c.addPath(Self.hex(Self.ri,11)); c.clip()
            let g = CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB)!,colors:[Self.ui(0x6f4cc4).cgColor,Self.ui(0x3f2a88).cgColor] as CFArray,locations:[0,1])!
            c.drawRadialGradient(g,startCenter:CGPoint(x:0,y:6),startRadius:0,endCenter:CGPoint(x:0,y:6),endRadius:Self.ri*1.05,options:.drawsAfterEndLocation)
            c.addPath(Self.hex(Self.ri,11)); c.setLineWidth(9); c.replacePathWithStrokedPath(); c.clip()
            let shadow = CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB)!,colors:[Self.ui(0x1e0c50,0.85).cgColor,Self.ui(0x1e0c50,0.15).cgColor,Self.ui(0xaa8cf0,0.55).cgColor] as CFArray,locations:[0,0.55,1])!
            c.drawLinearGradient(shadow,start:CGPoint(x:0,y:-Self.ri),end:CGPoint(x:0,y:Self.ri),options:[.drawsBeforeStartLocation,.drawsAfterEndLocation]); c.restoreGState()
        }
        body = Self.image { Self.block($0,-27,4,54,19,3,7,0xc8b0fc,0xa587ee) }
        lid = Self.image { c in
            Self.block(c,-31,-25,62,28,15,4,0xeadfff,0xc7aefb)
            let band = CGPath(roundedRect:CGRect(x:-32,y:-1,width:64,height:11),cornerWidth:5,cornerHeight:5,transform:nil)
            Self.stroke(c,band,0x4e2aa0,2.6)
            Self.gradient(c,band,[0xf1e9ff,0xddcdff,0xbfa4f6],[0,0.5,1],CGPoint(x:0,y:-1),CGPoint(x:0,y:10))
            c.saveGState(); c.translateBy(x:0,y:4.5); c.rotate(by:.pi/4)
            let lock = CGPath(roundedRect:CGRect(x:-10,y:-10,width:20,height:20),cornerWidth:6,cornerHeight:6,transform:nil)
            Self.stroke(c,lock,0x4e2aa0,2.8); c.addPath(lock); c.setFillColor(Self.ui(0xf3ecff).cgColor); c.fillPath()
            Self.stroke(c,CGPath(roundedRect:CGRect(x:-8,y:-8,width:16,height:16),cornerWidth:5,cornerHeight:5,transform:nil),0xd5c3fb,1.2); c.restoreGState()
            c.addPath(Self.star(0,4.5,6.4).cgPath); c.setFillColor(Self.ui(0x7b4fe0).cgColor); c.fillPath()
        }
    }
    private static func image(_ draw: (CGContext)->Void) -> Image {
        let f = UIGraphicsImageRendererFormat(); f.scale=3
        return Image(uiImage:UIGraphicsImageRenderer(size:CGSize(width:300,height:300),format:f).image { r in
            r.cgContext.translateBy(x:150,y:150); draw(r.cgContext)
        })
    }
    private static func gradient(_ c: CGContext, _ path: CGPath, _ colors: [Int], _ stops:[CGFloat], _ a: CGPoint, _ b: CGPoint) {
        c.saveGState(); c.addPath(path); c.clip()
        let g = CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB)!,colors:colors.map { ui($0).cgColor } as CFArray,locations:stops)!
        c.drawLinearGradient(g,start:a,end:b,options:[.drawsBeforeStartLocation,.drawsAfterEndLocation]); c.restoreGState()
    }
    private static func stroke(_ c: CGContext, _ p: CGPath, _ color:Int, _ w:Double) {
        c.addPath(p); c.setStrokeColor(ui(color).cgColor); c.setLineWidth(w); c.setLineCap(.round); c.setLineJoin(.round); c.strokePath()
    }
    private static func block(_ c:CGContext,_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ rt:Double,_ rb:Double,_ top:Int,_ bot:Int) {
        let p = CGMutablePath()
        // Skia clamps adjacent corner radii to the block's dimensions.
        let factor = min(1,h/(rt+rb)), a=rt*factor, b=rb*factor
        p.move(to:CGPoint(x:x+a,y:y)); p.addLine(to:CGPoint(x:x+w-a,y:y))
        p.addArc(tangent1End:CGPoint(x:x+w,y:y),tangent2End:CGPoint(x:x+w,y:y+a),radius:a)
        p.addLine(to:CGPoint(x:x+w,y:y+h-b)); p.addArc(tangent1End:CGPoint(x:x+w,y:y+h),tangent2End:CGPoint(x:x+w-b,y:y+h),radius:b)
        p.addLine(to:CGPoint(x:x+b,y:y+h)); p.addArc(tangent1End:CGPoint(x:x,y:y+h),tangent2End:CGPoint(x:x,y:y+h-b),radius:b)
        p.addLine(to:CGPoint(x:x,y:y+a)); p.addArc(tangent1End:CGPoint(x:x,y:y),tangent2End:CGPoint(x:x+a,y:y),radius:a); p.closeSubpath()
        stroke(c,p,0x4e2aa0,2.6); gradient(c,p,[top,bot],[0,1],CGPoint(x:x,y:y),CGPoint(x:x+w*0.25,y:y+h))
        c.saveGState(); c.addPath(p); c.clip()
        for sx in [-12.5,6.5] {
            gradient(c,CGPath(rect:CGRect(x:sx,y:y,width:6,height:h),transform:nil),[0x9a6ff0,0x7b4fd8],[0,1],CGPoint(x:0,y:y),CGPoint(x:0,y:y+h))
            c.setFillColor(ui(0x4e2aa0,0.35).cgColor); c.fill(CGRect(x:sx,y:y,width:1.2,height:h))
        }
        for (sx,ww,al) in [(x+3,3.5,0.4),(-4.5,2.5,0.3),(15.0,2.5,0.25)] {
            c.setFillColor(ui(0xffffff,al).cgColor); c.fill(CGRect(x:sx,y:y+2,width:ww,height:h-4))
        }
        c.setFillColor(ui(0x7b4fd8,0.3).cgColor); c.fill(CGRect(x:x+w-4,y:y,width:4,height:h)); c.restoreGState()
    }
}
