import Foundation

/// The reference's analytic timeline; all values remain in its 402 × 874 design space.
enum CardVacuumSealMotion {
    static let loop = 6.0
    struct Look {
        var p = 1.0, press = 0.0, flash = 0.0, puff = 0.0, settle = 1.0
        var glint = 0.0, sweep = -1.0, sweepK = 0.0, tug = 0.0, tugSide = 0.0, discGlint = 0.0
    }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func wrap(_ t: Double) -> Double {
        let m = t.truncatingRemainder(dividingBy: loop)
        return m < 0 ? m+loop : m
    }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func smooth(_ v: Double) -> Double { let x = clamp(v); return x*x*(3-2*x) }
    static func band(_ a: Double, _ b: Double, _ x: Double) -> Double { smooth(seg(x, a, b)) }
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
        let v = clamp(x)
        var t = v
        for _ in 0..<6 {
            let it = 1-t
            let fx = 3*it*it*t*x1 + 3*it*t*t*x2 + t*t*t-v
            let dx = 3*it*it*x1 + 6*it*t*(x2-x1) + 3*t*t*(1-x2)
            if abs(dx) < 1e-6 { break }
            t = clamp(t-fx/dx)
        }
        let it = 1-t
        return 3*it*it*t*y1 + 3*it*t*t*y2 + t*t*t
    }
    static func easeOut(_ v: Double) -> Double { bezier(0.2, 0.8, 0.2, 1, v) }
    static func easeInOut(_ v: Double) -> Double { bezier(0.65, 0, 0.35, 1, v) }
    static func springAt(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        if t <= 0 { return 0 }
        let w = 2*Double.pi/response
        if zeta >= 1 { return 1-exp(-w*t)*(1+w*t) }
        let wd = w*sqrt(1-zeta*zeta)
        return 1-exp(-zeta*w*t)*(cos(wd*t)+zeta*w/wd*sin(wd*t))
    }
    static func bump(_ t: Double, _ a: Double, _ b: Double) -> Double { sin(.pi*seg(t,a,b)) }
    static func labelAt(_ t: Double) -> Double {
        t < 3.45 ? 1-easeInOut(seg(t,0.24,0.62)) : easeInOut(seg(t,3.45,3.83))
    }
    static func fingerP(_ x: Double, _ y: Double) -> Double {
        let ux = min(1.3,max(-1.3,x/177))*0.5+0.5
        let uy = min(1.3,max(-1.3,y/111.5))*0.5+0.5
        return 0.3+0.7*clamp(clamp(0.42*ux+0.58*uy)+0.1)
    }
    static func creaseAmp(_ speed: Double) -> Double { 0.8+0.45*smooth((speed-0.9)/(2.2-0.9)) }
    static func tugAt(_ t: Double) -> Double {
        t <= 0 ? 0 : smooth(t/0.07)*(1-springAt(max(t-0.07,0),0.39,0.434))
    }
    static func pAt(_ t: Double) -> Double {
        if t < 0.2 { return 1 }
        if t < 0.73 { return 1-0.7*bezier(0.45,0,0.55,1,seg(t,0.2,0.73)) }
        if t < 1 { return 0.3*(1-bezier(0.32,0,0.67,0,seg(t,0.73,1))) }
        if t < 1.25 { return 0 }
        if t < 1.6 { return 0.3*seg(t,1.25,1.6) }
        if t < 2.05 { return 0.3+0.36*seg(t,1.6,2.05) }
        if t < 3.05 {
            let tau = t-2.05, env = smooth(tau/0.12)*(1-smooth((tau-0.88)/0.12))
            return 0.66+0.02*env*sin(2 * .pi*1.2*tau)
        }
        if t < 3.45 { return 0.66+0.34*seg(t,3.05,3.45) }
        return 1
    }
    static func lookAt(_ t: Double) -> Look {
        let hold = smooth(seg(t,1.2,1.28))*(1-smooth(seg(t,3.45,3.57)))
        let off = smooth(seg(t,2.05,2.2))*(1-smooth(seg(t,2.85,3.05)))*0.55
        var l = Look()
        l.p = pAt(t)
        l.press = max(bump(t,0.12,0.4),hold*(1-off))
        l.flash = bump(t,1.2,1.52)+0.6*bump(t,0.12,0.42)
        l.puff = bump(t,0.58,0.88)
        l.settle = t >= 3.45 ? 1+0.03*(1-springAt(t-3.45,0.42,0.55)) : 1+0.03*smooth(seg(t,3.05,3.45))
        l.glint = bump(t,3.45,3.7)
        if t < 0.9 { l.sweep = -1+2*easeInOut(seg(t,0,0.9)); l.sweepK = 0.8*bump(t,0,0.9) }
        else if t >= 2.05 && t < 3.05 { l.sweep = -0.6+0.9*easeInOut(seg(t,2.05,3.05)); l.sweepK = 0.6*bump(t,2.05,3.05) }
        else if t >= 4 && t < 5 { l.sweep = -1+2*easeInOut(seg(t,4,5)); l.sweepK = 0.8*bump(t,4,5) }
        l.tug = tugAt(t-3.45); l.tugSide = tugAt(t-3.45-0.04)
        l.discGlint = bump(t,3.51,3.63)
        return l
    }
    static func lightDrift(_ t: Double) -> Double { 0.06*sin(2 * .pi*t/loop)+0.025*sin(4 * .pi*t/loop+1.3) }
}
