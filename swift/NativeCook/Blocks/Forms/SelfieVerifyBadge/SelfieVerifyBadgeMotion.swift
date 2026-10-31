import Foundation

enum SelfieVerifyBadgeMotion {
    static let loop = 9.6
    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2 }
    static func easeOut(_ x: Double) -> Double { 1-pow(1-x, 3) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a+(b-a)*t }
    static func springTo(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * Double.pi / response, wd = w * sqrt(1-zeta*zeta)
        return 1-exp(-zeta*w*t)*(cos(wd*t)+zeta*w/wd*sin(wd*t))
    }
    struct Layout {
        let w, h, titleTop, frameX, frameY, frame, hintTop, shutterY, shutter, cover: Double
        init(w: Double, h: Double, top: Double, bottom: Double) {
            let f = min(122, w*0.3), y = top+120+f+30
            self.w=w; self.h=h; titleTop=top+34; frameX=w/2; frameY=y; frame=f
            hintTop=y+f+40; shutterY=h-bottom-74; shutter=72; cover=hypot(w,h)*0.8
        }
    }
    struct Verified {
        let brackets, collapse, bust, shape, puff, size, badge, yaw, check, light: Double
    }
    static func verified(_ l: Layout, _ k: Double) -> Verified {
        let shape=easeInOut(seg(k,0.06,0.3)), puff=easeInOut(seg(k,0.2,0.55))
        let grow=easeInOut(seg(k,0.45,0.72)), flipIn=seg(k,0.55,0.9)
        return Verified(brackets: 1-easeOut(seg(k,0,0.12)), collapse: easeInOut(seg(k,0,0.3)),
                        bust: 1-easeInOut(seg(k,0.24,0.36)), shape: shape, puff: puff,
                        size: mix(mix(l.frame,l.frame*0.82,shape),l.cover,grow),
                        badge: 56*springTo(flipIn*0.44,0.42,0.55), yaw: (1-easeOut(flipIn))*Double.pi*3,
                        check: easeInOut(seg(k,0.86,1)), light: easeInOut(seg(k,0.55,0.7)))
    }
    static func scanAt(_ t: Double) -> Double {
        if t < 0.8 || t >= 1.7 { return -9 }
        let u=seg(t,0.8,1.7)
        return mix(-0.52,0.58,easeInOut(u < 0.5 ? u*2 : 2-u*2))
    }
    static func detectedAt(_ t: Double) -> Double { easeOut(seg(t,1.7,1.95))*(1-easeOut(seg(t,8.9,9.2))) }
    static func linkAt(_ t: Double) -> Double {
        if t < 2.35 { return 0 }
        if t < 7.6 { return seg(t,2.35,5.6) }
        return 1-easeInOut(seg(t,7.6,8.9))
    }
    static func flashAt(_ t: Double) -> Double {
        let u=seg(t,2.33,2.55)
        return u > 0 && u < 1 ? sin(u*Double.pi)*(1-u) : 0
    }
    static func shutterAt(_ t: Double) -> Double { easeOut(seg(t,2.2,2.3))*(1-easeOut(seg(t,2.34,2.54))) }
    // Cubic-bezier inversion, preserving the RN timing curve rather than substituting a native ease.
    static func bezier(_ x: Double, _ x1: Double = 0.77, _ y1: Double = 0, _ x2: Double = 0.175, _ y2: Double = 1) -> Double {
        func at(_ t: Double, _ a: Double, _ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
        var lo=0.0, hi=1.0
        for _ in 0..<24 { let m=(lo+hi)/2; if at(m,x1,x2)<x { lo=m } else { hi=m } }
        return at((lo+hi)/2,y1,y2)
    }
    // Reanimated 4 duration spring: mass 4, duration 700 ms, dampingRatio .45,
    // energyThreshold 6e-9, perceptual duration multiplier 1.5.
    static func releaseStiffness(_ x0: Double, _ velocity: Double) -> Double {
        if x0==0 && velocity==0 {return 900}
        func energyDiff(_ k: Double) -> Double {
            let omega=sqrt(k/4)*0.45, settling=0.7*1.5
            let xt=(x0+(velocity+x0*omega)*settling)*exp(-omega*settling)
            let vt=xt * -omega+(velocity+x0*omega)*exp(-omega*settling)
            let e0=0.5*k*x0*x0+2*velocity*velocity
            return (0.5*k*xt*xt+2*vt*vt)/e0-6e-9
        }
        var lo=Double.ulpOfOne, hi=8000.0, current=(lo+hi)/2
        let direction=energyDiff(hi)>=energyDiff(lo) ? 1.0 : -1.0
        for _ in 0..<100 {
            if abs(energyDiff(current))<=6e-12 {break}
            if energyDiff(current)*direction<0 {lo=current} else {hi=current}
            current=(lo+hi)/2
        }
        return current
    }
    struct Frame {
        var link=0.0, detected=0.0, scan = -9.0, flash=0.0, shutter=0.0, yaw=0.0, spin=0.0, fade=0.0
    }
    static func frameAt(_ t: Double) -> Frame {
        Frame(link: linkAt(t), detected: detectedAt(t), scan: scanAt(t), flash: flashAt(t),
              shutter: shutterAt(t), yaw: sin(t/loop*Double.pi*2)*0.32)
    }
}
