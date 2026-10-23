import SwiftUI
import CoreGraphics
import Foundation

final class VelvetNapStudioResources: @unchecked Sendable {
    static let shared = VelvetNapStudioResources()
    static let colors: [UInt32] = [0xC7BDA0,0xA88444,0x7C253E,0x073C68,0xD3A5AD,0x438D97,0x41485C,0x737A43]
    static let names = ["Oat", "Ochre", "Claret", "Midnight", "Blush", "Lagoon", "Slate", "Moss"]
    let atlas: CGImage
    let shadow: CGImage
    // Raw float4(s, distance, tangent angle, relaxation delay), not a color image.
    let pathData: Data
    let emptyData = VelvetNapStudioField.empty.bytes
    private init() {
        atlas = Self.makeAtlas()
        shadow = Self.makeShadow(atlas)
        var values:[Float]=[];values.reserveCapacity(4096*4)
        for y in 0..<64 { for x in 0..<64 {
            let nearest=VelvetNapStudioMath.grid[y*64+x]
            values += [Float(nearest.s),Float(nearest.distance),Float(nearest.angle),Float(nearest.delay)]
        }}
        pathData=values.withUnsafeBytes { Data($0) }
    }
    static func color(_ rgb: UInt32, alpha:Double=1)->Color {
        Color(.sRGB,red:Double((rgb>>16)&255)/255,green:Double((rgb>>8)&255)/255,blue:Double(rgb&255)/255,opacity:alpha)
    }
    static func ui(_ rgb:UInt32,alpha:CGFloat=1)->CGColor {
        CGColor(srgbRed:CGFloat((rgb>>16)&255)/255,green:CGFloat((rgb>>8)&255)/255,blue:CGFloat(rgb&255)/255,alpha:alpha)
    }
    private static func render(size:CGSize,draw:(CGContext)->Void)->CGImage {
        let c=CGContext(data:nil,width:Int(size.width*2),height:Int(size.height*2),bitsPerComponent:8,bytesPerRow:0,
                        space:CGColorSpace(name:CGColorSpace.sRGB)!,bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue)!
        c.scaleBy(x:2,y:2);c.translateBy(x:0,y:size.height);c.scaleBy(x:1,y:-1)
        draw(c);return c.makeImage()!
    }
    private static func drawImage(_ image:CGImage,in rect:CGRect,context c:CGContext) {
        c.saveGState();c.translateBy(x:rect.minX,y:rect.maxY);c.scaleBy(x:1,y:-1)
        c.draw(image,in:CGRect(x:0,y:0,width:rect.width,height:rect.height));c.restoreGState()
    }
    static var front: CGPath {
        let p=CGMutablePath();p.move(to:CGPoint(x:30,y:45))
        p.addCurve(to:CGPoint(x:244,y:22),control1:CGPoint(x:105,y:42),control2:CGPoint(x:196,y:30))
        p.addQuadCurve(to:CGPoint(x:271,y:223),control:CGPoint(x:260,y:116))
        p.addQuadCurve(to:CGPoint(x:61,y:258),control:CGPoint(x:159,y:244))
        p.addQuadCurve(to:CGPoint(x:20,y:217),control:CGPoint(x:28,y:237))
        p.addQuadCurve(to:CGPoint(x:30,y:45),control:CGPoint(x:22,y:112));p.closeSubpath();return p
    }
    private static let noiseGrid=(0..<4096).map {VelvetNapStudioMath.hash($0+90000)}
    private static func noise(_ x:Double,_ y:Double)->Double {
        let ix=Int(floor(x)),iy=Int(floor(y)),fx=VelvetNapStudioMath.smooth(x-floor(x)),fy=VelvetNapStudioMath.smooth(y-floor(y))
        func at(_ x:Int,_ y:Int)->Double {noiseGrid[((y%64+64)%64)*64+(x%64+64)%64]}
        return (at(ix,iy)*(1-fx)+at(ix+1,iy)*fx)*(1-fy)+(at(ix,iy+1)*(1-fx)+at(ix+1,iy+1)*fx)*fy
    }
    private static func makePileRaster()->CGImage {
        let w=614,h=548
        var bytes=Data(count:w*h*4)
        let fans=(0..<21).map {k -> (x:Double,y:Double,l:Double,w:Double,c:Double,s:Double) in
            let angle = -0.7+0.9*VelvetNapStudioMath.hash(k+30)
            return (22+263*VelvetNapStudioMath.hash(2*k),36+194*VelvetNapStudioMath.hash(2*k+1),18+21*VelvetNapStudioMath.hash(k+60),7+9*VelvetNapStudioMath.hash(k+80),cos(angle),sin(angle))
        }
        let stops=[SIMD3(16.0,77.0,121.0),SIMD3(7.0,60.0,104.0),SIMD3(9.0,45.0,81.0)]
        for iy in 0..<h {for ix in 0..<w {
            let x=(Double(ix)+0.5)/2,y=(Double(iy)+0.5)/2
            let coarse=noise(x*0.055,y*0.055),warp=noise(x*0.044+13,y*0.044-9)
            let xx=x+14*(warp-0.5),yy=y+12*(coarse-0.5)
            let medium=noise(xx*0.14+3,yy*0.14+29),fine=noise(x*0.61,y*0.61)
            var theta=0.6+0.52*sin(0.043*x+0.019*y)+0.35*sin(0.028*y-0.016*x)
            var fanTone=0.0
            for fan in fans {
                let dx=x-fan.x,dy=y-fan.y,u=(dx*fan.c+dy*fan.s)/fan.l,v=(-dx*fan.s+dy*fan.c)/fan.w
                let r=u*u+v*v
                if r<4 {
                    let envelope=exp(-r*1.3)
                    theta += 0.47*envelope*sin(2.4*atan2(v,u+0.2)+coarse*3)
                    // Seven bent tonal ridges merge into the pile, without icon outlines.
                    fanTone += 0.095*envelope*sin(v*6.2+u*2.3+medium*2)
                }
            }
            let light=0.8*cos(theta)-0.6*sin(theta)
            let folds=sin(xx*0.23+yy*0.18+4*coarse+2*medium)
            let tonal=0.98+0.40*(coarse-0.5)+0.30*(medium-0.5)+0.08*folds+fanTone+0.14*(light-0.35)
            let grain=1+0.08*(fine-0.5)
            let t=VelvetNapStudioMath.clamp(((x-25)*239+(y-38)*207)/(239*239+207*207))
            let rgb=t<0.42 ? stops[0]+(stops[1]-stops[0])*(t/0.42):stops[1]+(stops[2]-stops[1])*((t-0.42)/0.58)
            let i=(iy*w+ix)*4
            bytes[i]=UInt8(min(255,max(0,(rgb.x*tonal*grain).rounded())))
            bytes[i+1]=UInt8(min(255,max(0,(rgb.y*tonal*grain).rounded())))
            bytes[i+2]=UInt8(min(255,max(0,(rgb.z*tonal*grain).rounded())))
            bytes[i+3]=255
        }}
        return CGImage(width:w,height:h,bitsPerComponent:8,bitsPerPixel:32,bytesPerRow:w*4,
                       space:CGColorSpace(name:CGColorSpace.sRGB)!,bitmapInfo:CGBitmapInfo(rawValue:CGImageAlphaInfo.premultipliedLast.rawValue),
                       provider:CGDataProvider(data:bytes as CFData)!,decode:nil,shouldInterpolate:true,intent:.defaultIntent)!
    }
    static func makeAtlas()->CGImage {
        return render(size:CGSize(width:307,height:274)) { c in
            c.saveGState();c.translateBy(x:165,y:113);c.rotate(by:-4 * .pi/180);c.translateBy(x:-165,y:-113)
            let rear=CGMutablePath();rear.move(to:CGPoint(x:58,y:28));rear.addLines(between:[CGPoint(x:253,y:8),CGPoint(x:273,y:194),CGPoint(x:80,y:218)]);rear.closeSubpath()
            c.addPath(rear);c.setFillColor(ui(0x062E50));c.fillPath();c.restoreGState()
            c.saveGState();c.addPath(front);c.clip()
            drawImage(makePileRaster(),in:CGRect(x:0,y:0,width:307,height:274),context:c)
            for i in 0..<9000 {
                let x=307*VelvetNapStudioMath.hash(i*5+700),y=274*VelvetNapStudioMath.hash(i*5+701)
                let theta=0.6+0.52*sin(0.043*x+0.019*y)+0.35*sin(0.028*y-0.016*x)
                let length=0.65+1.15*VelvetNapStudioMath.hash(i*5+702)
                let light=0.8*cos(theta)-0.6*sin(theta)
                c.setStrokeColor(ui(light>0.35 ? 0x3D91B0:0x092F51,alpha:0.08+0.17*VelvetNapStudioMath.hash(i*5+703)))
                c.setLineWidth(0.18+0.2*VelvetNapStudioMath.hash(i*5+704))
                c.move(to:CGPoint(x:x,y:y));c.addLine(to:CGPoint(x:x+cos(theta)*length,y:y+sin(theta)*length));c.strokePath()
            }
            c.restoreGState()
            let lip=CGMutablePath();lip.move(to:CGPoint(x:30,y:45));lip.addCurve(to:CGPoint(x:244,y:22),control1:CGPoint(x:119,y:72),control2:CGPoint(x:193,y:43))
            c.saveGState();c.setShadow(offset:CGSize(width:0,height:2),blur:3,color:ui(0x000000,alpha:0.2))
            c.addPath(lip);c.setStrokeColor(ui(0x082C4C));c.setLineWidth(3.5);c.strokePath();c.restoreGState()
            c.addPath(lip);c.setStrokeColor(ui(0x4A86A3,alpha:0.4));c.setLineWidth(0.65);c.strokePath()
            let underside=CGMutablePath();underside.move(to:CGPoint(x:61,y:258));underside.addLines(between:[CGPoint(x:111,y:248),CGPoint(x:78,y:214),CGPoint(x:20,y:217)]);underside.closeSubpath()
            c.addPath(underside);c.setFillColor(ui(0x052A49));c.fillPath()
            c.move(to:CGPoint(x:20,y:217));c.addLine(to:CGPoint(x:78,y:214));c.addLine(to:CGPoint(x:111,y:248));c.setLineWidth(0.6);c.setStrokeColor(ui(0x47738D,alpha:0.6));c.strokePath()
        }
    }
    static func makeShadow(_ image:CGImage)->CGImage {
        return render(size:CGSize(width:355,height:322)) { c in
            for (dy,blur,alpha) in [(9.0,11.0,0.12),(2.0,2.2,0.17)] {
                c.saveGState();c.setShadow(offset:CGSize(width:0,height:dy),blur:blur,color:ui(0x072743,alpha:alpha))
                drawImage(image,in:CGRect(x:24,y:24,width:307,height:274),context:c);c.restoreGState()
            }
            c.setBlendMode(.destinationOut);drawImage(image,in:CGRect(x:24,y:24,width:307,height:274),context:c)
        }
    }
}
