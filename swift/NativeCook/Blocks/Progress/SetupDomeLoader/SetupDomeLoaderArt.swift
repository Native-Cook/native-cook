import CoreGraphics
import UIKit

struct SetupDomeLoaderGeo: Equatable {
    var cx, by, r, ry, h: Double
    init(_ size: CGSize) {
        cx = size.width/2; by = (size.height*0.47).rounded()
        r = (size.width*0.25).rounded(); ry = (size.width*0.25*0.3).rounded()
        h = (size.width*0.25*1.75).rounded()
    }
}

// The RN Skia drawing operations, with sRGB paint and round stroke caps.
struct SetupDomeLoaderArt {
    typealias M = SetupDomeLoaderMotion
    let cv: CGContext
    static let space = CGColorSpace(name: CGColorSpace.sRGB)!
    static let checkGlow = CGGradient(colorsSpace:space,colors:[color(0x3BE07A,0.32),color(0x3BE07A,0)] as CFArray,locations:[0,1])!
    static func color(_ hex: Int, _ alpha: Double = 1) -> CGColor {
        CGColor(colorSpace:space,components:[Double((hex>>16)&255)/255,Double((hex>>8)&255)/255,Double(hex&255)/255,alpha])!
    }
    func path(_ p: CGPath, _ hex: Int, _ alpha: Double = 1, _ stroke: Double = 0) {
        cv.addPath(p)
        if stroke > 0 {
            cv.setStrokeColor(Self.color(hex,alpha)); cv.setLineWidth(stroke)
            cv.setLineCap(.round); cv.setLineJoin(.miter); cv.strokePath()
        } else { cv.setFillColor(Self.color(hex,alpha)); cv.fillPath() }
    }
    func rect(_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ color: Int,_ a: Double = 1) {
        cv.setFillColor(Self.color(color,a)); cv.fill(CGRect(x:x,y:y,width:w,height:h))
    }
    func oval(_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ color: Int,_ a: Double = 1,_ stroke: Double = 0) {
        path(CGPath(ellipseIn:CGRect(x:x,y:y,width:w,height:h),transform:nil),color,a,stroke)
    }
    func circle(_ x: Double,_ y: Double,_ r: Double,_ color: Int,_ a: Double = 1,_ stroke: Double = 0) {
        oval(x-r,y-r,r*2,r*2,color,a,stroke)
    }
    func line(_ x: Double,_ y: Double,_ xx: Double,_ yy: Double,_ color: Int,_ a: Double = 1,_ stroke: Double = 1) {
        let p = CGMutablePath(); p.move(to:CGPoint(x:x,y:y)); p.addLine(to:CGPoint(x:xx,y:yy)); path(p,color,a,stroke)
    }
    func rr(_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ r: Double,_ color: Int,_ a: Double = 1,_ stroke: Double = 0) {
        path(CGPath(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerWidth:r,cornerHeight:r,transform:nil),color,a,stroke)
    }
    func panel(_ g: SetupDomeLoaderGeo,_ a: Double,_ b: Double) -> CGPath {
        var x0 = g.cx+g.r*cos(a), x1 = x0
        for i in 0...18 {
            let x = g.cx+g.r*cos(M.mix(a,b,Double(i)/18))
            x0 = min(x0,x); x1 = max(x1,x)
        }
        let mid = (x0+x1)/2, half = max(0.5,(x1-x0)/2), arch = half*0.95
        let p = CGMutablePath()
        for i in 0...18 {
            let th = M.mix(a,b,Double(i)/18)
            let point = CGPoint(x:g.cx+g.r*cos(th),y:g.by+g.ry*sin(th))
            if i == 0 { p.move(to:point) } else { p.addLine(to:point) }
        }
        for i in stride(from:18,through:0,by:-1) {
            let x = g.cx+g.r*cos(M.mix(a,b,Double(i)/18))
            let k = M.clamp(1-pow((x-mid)/half,2))
            p.addLine(to:CGPoint(x:x,y:g.by-g.h+arch-arch*sqrt(k)))
        }
        p.closeSubpath(); return p
    }
    func platform(_ g: SetupDomeLoaderGeo,_ lit: Double) {
        let w = g.r*1.42, h = g.ry*1.42
        oval(g.cx-w,g.by-h+16,w*2,h*2,0x4B4A9E)
        rect(g.cx-w,g.by,w*2,16,0x4B4A9E)
        cv.saveGState()
        cv.addEllipse(in:CGRect(x:g.cx-w,y:g.by-h,width:w*2,height:h*2)); cv.clip()
        let grad = CGGradient(colorsSpace:Self.space,colors:[Self.color(0xA9A7F5),Self.color(0x7C7AD9)] as CFArray,locations:[0,1])!
        cv.drawLinearGradient(grad,start:CGPoint(x:g.cx,y:g.by-h),end:CGPoint(x:g.cx,y:g.by+h),options:[.drawsBeforeStartLocation,.drawsAfterEndLocation])
        cv.restoreGState()
        oval(g.cx-w*0.78,g.by-h*0.78,w*1.56,h*1.56,0xC9C7FF,0.35,1.2)
        oval(g.cx-w*0.52,g.by-h*0.52,w*1.04,h*1.04,0xC9C7FF,0.22,1)
        let key = CGMutablePath()
        key.addEllipse(in:CGRect(x:g.cx-11,y:g.by-7,width:22,height:12))
        key.move(to:CGPoint(x:g.cx-5,y:g.by+1)); key.addLine(to:CGPoint(x:g.cx+5,y:g.by+1))
        key.addLine(to:CGPoint(x:g.cx+9,y:g.by+h*0.62)); key.addLine(to:CGPoint(x:g.cx-9,y:g.by+h*0.62)); key.closeSubpath()
        path(key,0x0B0B14)
        if lit > 0 {
            path(key,0x3BE07A,lit)
            cv.saveGState()
            // Cached Gaussian mask, sigma=10 in the RN mask filter.
            SetupDomeLoaderResources.shared.drawKeyGlow(cv,g,lit)
            cv.restoreGState()
        }
    }
    func item(_ kind: Int,_ u: Double,_ sx: Double,_ tilt: Double,_ alpha: Double) {
        cv.saveGState(); cv.rotate(by:tilt*Double.pi/180); cv.scaleBy(x:max(0.08,abs(sx)),y:1)
        let back = sx < 0
        switch kind {
        case 0:
            rr(-u*0.9,-u*0.6,u*1.8,u*1.2,u*0.18,back ? 0x4A2A1A : 0x6B3D24,alpha)
            rr(-u*0.9,-u*0.6,u*1.8,u*0.62,u*0.18,back ? 0x5A3320 : 0x80492B,alpha)
            line(-u*0.75,0,u*0.75,0,0xC08A5C,0.55*alpha,1.2)
            if !back { circle(u*0.55,u*0.02,u*0.11,0xE8C27A,alpha) }
        case 1:
            rr(-u*0.62,-u*0.85,u*1.24,u*1.7,u*0.14,back ? 0x6E5320 : 0x3A2A14,alpha)
            rr(-u*0.62,-u*0.85,u*1.24,u*1.7,u*0.14,0xE3B34E,alpha,2)
            if !back {
                let cell = u*0.17
                for i in 0..<5 { for j in 0..<5 {
                    if (i*7+j*3+i*j)%3 != 0 || (i<2 && j<2) || (i>2 && j<2) || (i<2 && j>2) {
                        rect(-u*0.43+Double(i)*cell,-u*0.43+Double(j)*cell,cell*0.82,cell*0.82,0xF2C55E,alpha)
                    }
                } }
            }
        case 2:
            let f = 0.12*sin(tilt*3), p = CGMutablePath()
            p.move(to:CGPoint(x:-u*0.9,y:-u*0.45+f*u)); p.addLine(to:CGPoint(x:u*0.9,y:-u*0.45-f*u))
            p.addLine(to:CGPoint(x:u*0.9,y:u*0.45-f*u)); p.addLine(to:CGPoint(x:-u*0.9,y:u*0.45+f*u)); p.closeSubpath()
            path(p,back ? 0x1E9E58 : 0x2FCB72,alpha); circle(0,0,u*0.22,0x1B8A4C,alpha,1.5)
        default:
            circle(0,0,u*0.48,0xC9581F,alpha); circle(0,0,u*0.44,back ? 0xE07030 : 0xF28A3A,alpha)
            circle(0,0,u*0.28,0xFFB070,0.7*alpha,1.6)
        }
        cv.restoreGState()
    }
    func glass(_ g: SetupDomeLoaderGeo,_ a: Double,_ b: Double,_ c: Double,_ d: Double) {
        let left = SetupDomeLoaderResources.shared.panel(0,g,a,b) { panel(g,a,b) }
        let right = SetupDomeLoaderResources.shared.panel(1,g,c,d) { panel(g,c,d) }
        path(left,0x9C9BFF,0.24); path(right,0x9C9BFF,0.24)
        path(left,0xC3C2FF,0.85,2.2); path(right,0xC3C2FF,0.85,2.2)
        for s in 0..<3 {
            let xl = g.cx+g.r*cos(M.mix(a,b,0.18+Double(s)*0.12))
            let xr = g.cx+g.r*cos(M.mix(c,d,0.7+Double(s)*0.1))
            line(xl,g.by-g.h*0.82,xl,g.by-g.h*0.18,0xE2E1FF,0.28,1.4)
            line(xr,g.by-g.h*0.82,xr,g.by-g.h*0.18,0xE2E1FF,0.28,1.4)
        }
    }
    func backdrop(_ g: SetupDomeLoaderGeo,_ check: Double) {
        if check > 0.01 {
            cv.saveGState()
            cv.setAlpha(M.clamp(check))
            let center = CGPoint(x:g.cx,y:g.by-g.h*0.62)
            cv.drawRadialGradient(Self.checkGlow,startCenter:center,startRadius:0,endCenter:center,endRadius:g.r*2.4,options:.drawsAfterEndLocation)
            cv.restoreGState()
        }
    }
    func burst(_ g: SetupDomeLoaderGeo,_ phase: Double) {
        if phase > 0 {
            let r = M.mix(0.3,1.7,M.easeOut(phase))
            oval(g.cx-g.r*r,g.by-g.ry*r,g.r*2*r,g.ry*2*r,0x3BE07A,0.8*(1-phase),3)
        }
    }
    func scene(_ g: SetupDomeLoaderGeo,_ o: M.Output,_ reduced: Bool) {
        let ph = M.phase(o.t)
        backdrop(g,ph.chk)
        let open = 1-ph.c
        if open > 0.01 {
            for i in 0..<6 { rect(g.cx-g.r*0.82*open,g.by-g.h+g.h*0.12+Double(i)*g.h*0.13,g.r*1.64*open,g.h*0.08,0x2B1830,0.85*open) }
        }
        SetupDomeLoaderResources.shared.drawBase(cv,g,ph)
        func drawItems(_ front: Bool) {
            for (i,it) in M.items.enumerated() {
                let r = 1-ph.c
                let phi = it.a+(reduced ? 0 : M.orbitW*o.t+o.off)+ph.c*2.4
                let z = sin(phi)
                if (z >= 0) != front { continue }
                let x = g.cx+g.r*0.58*r*cos(phi)
                let bob = reduced ? 0 : sin(o.clock*(6*Double.pi/M.loop)+it.a*2)*4
                let y = g.by-g.h*M.mix(it.h,0.5,ph.c)+g.ry*0.62*r*z-bob-o.hop[i]
                let sc = (0.86+0.16*z)*it.s*(1-0.75*ph.c)
                cv.saveGState(); cv.translateBy(x:x,y:y); cv.scaleBy(x:sc,y:sc)
                let sx = cos(o.clock*it.spin*(2*Double.pi/M.loop)*2+it.a)
                let tilt = sin(o.clock*(2*Double.pi/M.loop)+it.a)*0.25
                // Static item paths are cached as vector display lists; only the note flutters.
                if it.kind == 2 { item(it.kind,g.r*0.5,sx,tilt,1-ph.c*ph.c) }
                else { SetupDomeLoaderResources.shared.drawItem(cv,it.kind,g.r*0.5,sx,tilt,1-ph.c*ph.c) }
                cv.restoreGState()
            }
        }
        drawItems(false)
        if ph.c < 0.995 {
            glass(g,M.mix(Double.pi*0.61,Double.pi*0.5,ph.c),M.mix(Double.pi*1.39,Double.pi*0.5,ph.c),M.mix(-Double.pi*0.39,Double.pi*0.5,ph.c),M.mix(Double.pi*0.39,Double.pi*0.5,ph.c))
        }
        drawItems(true)
        if ph.c > 0.6 && ph.b < 0.999 {
            let top = M.mix(g.by-g.h-g.r*0.1,g.by-2,ph.b)
            line(g.cx,top,g.cx,g.by-2,0xC3C2FF,0.9*M.clamp((ph.c-0.6)/0.4),2.4)
            if ph.b > 0 {
                circle(g.cx,top,4+6*ph.b,0x3BE07A)
                SetupDomeLoaderResources.shared.drawBeadGlow(cv,g.cx,top,8+6*ph.b)
            }
        }
        if ph.chk > 0.005 {
            let cy = M.mix(g.by-4,g.by-g.h*0.62,min(1,ph.chk)), r = M.mix(8,g.r*0.42,ph.chk)
            circle(g.cx,cy,r,0x2DD06B)
            if ph.tick > 0 {
                let a = CGPoint(x:g.cx-r*0.38,y:cy+r*0.02), b = CGPoint(x:g.cx-r*0.1,y:cy+r*0.3), c = CGPoint(x:g.cx+r*0.42,y:cy-r*0.28)
                let l1 = hypot(b.x-a.x,b.y-a.y), l2 = hypot(c.x-b.x,c.y-b.y)
                let length = (l1+l2)*ph.tick, p = CGMutablePath()
                p.move(to:a)
                if length <= l1 { p.addLine(to:CGPoint(x:M.mix(a.x,b.x,length/l1),y:M.mix(a.y,b.y,length/l1))) }
                else { p.addLine(to:b); p.addLine(to:CGPoint(x:M.mix(b.x,c.x,(length-l1)/l2),y:M.mix(b.y,c.y,(length-l1)/l2))) }
                cv.addPath(p); cv.setStrokeColor(Self.color(0x0E2416)); cv.setLineCap(.round); cv.setLineJoin(.round); cv.setLineWidth(max(2,r*0.16)); cv.strokePath()
            }
        }
    }
}
