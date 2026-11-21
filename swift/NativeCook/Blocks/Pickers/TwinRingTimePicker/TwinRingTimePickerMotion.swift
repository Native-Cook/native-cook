import Foundation

/// A direct port of tr-motion.ts. All positions are in the reference's 402 × 874 points.
enum TwinRingTimePickerMotion {
    static let loop = 9.0
    struct Spring {
        let damping: Double
        let stiffness: Double
        static let detent = Self(damping: 40.4, stiffness: 503.5)
        static let pop = Self(damping: 19, stiffness: 195)
        static let weighty = Self(damping: 15.1, stiffness: 93.4)
        static let snap = Self(damping: 30.5, stiffness: 322.3)
        static let glide = Self(damping: 22.1, stiffness: 157.9)
    }
    struct Frame {
        var h = 8.0, m = 6.0, open = 0.0, fill = 0.0, cta = 1.0
        var pm = 0.0, kick = 0.0, flash = 0.0, hSpeed = 0.0, mSpeed = 0.0
    }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t-a)/(b-a), 0), 1) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a+(b-a)*k }
    static func easeIn(_ k: Double) -> Double { k*k*k }
    static func easeOut(_ k: Double) -> Double { 1-pow(1-k, 3) }
    static func easeInOut(_ k: Double) -> Double { k < 0.5 ? 4*k*k*k : 1-pow(-2*k+2, 3)/2 }
    static func springAt(_ t: Double, _ from: Double, _ to: Double, _ v0: Double, _ s: Spring, mass:Double = 1) -> Double {
        if t <= 0 { return from }
        let x0=from-to, w0=sqrt(s.stiffness/mass), z=s.damping/(2*sqrt(s.stiffness*mass))
        if z < 1 {
            let wd=w0*sqrt(1-z*z), e=exp(-z*w0*t)
            return to+e*(x0*cos(wd*t)+(v0+z*w0*x0)/wd*sin(wd*t))
        }
        let e=exp(-w0*t)
        return to+e*(x0+(v0+w0*x0)*t)
    }
    static func hour(_ t: Double) -> Double {
        if t < 1.85 { return 8 }
        if t < 3 { return springAt(t-1.85, 8, 9, 0, .weighty) }
        if t < 3.7 { return mix(9, 11, easeInOut(seg(t, 3, 3.7))) }
        if t < 3.85 { return 11 }
        if t < 4.55 { return mix(11, 13, easeInOut(seg(t, 3.85, 4.55))) }
        if t < 7.15 { return 13 }
        if t < 8.45 { return mix(13, 8, easeInOut(seg(t, 7.15, 8.45))) }
        return 8
    }
    static func minute(_ t: Double) -> Double {
        if t < 0.6 { return 6 }
        if t < 0.7 { return mix(6, 5.85, easeIn(seg(t, 0.6, 0.7))) }
        if t < 2.1 {
            let k=min(t-0.7, 1.4)
            let raw=5.85+6.15*(1-exp(-3*k))/(1-exp(-3*1.4))
            return raw-0.22/(2*Double.pi)*sin(2*Double.pi*raw)*exp(-3*k)
        }
        if t < 7 { return 12 }
        if t < 8.3 { return mix(12, 6, easeInOut(seg(t, 7, 8.3))) }
        return 6
    }
    static func frame(_ input: Double) -> Frame {
        let t=(input.truncatingRemainder(dividingBy: loop)+loop).truncatingRemainder(dividingBy: loop)
        let h=hour(t), m=minute(t), dt=1.0/120
        var f=Frame(h: h, m: m)
        f.hSpeed=abs(hour(min(t+dt, loop-0.001))-h)/dt
        f.mSpeed=abs(minute(min(t+dt, loop-0.001))-m)/dt
        if t >= 5.4 && t < 7 { f.open=min(springAt(t-5.4, 0, 1, 0, .glide), 1.04) }
        else if t >= 7 && t < 8.3 { f.open=1-easeInOut(seg(t, 7, 8.3)) }
        f.fill=t < 5.6 ? 0 : t < 6.3 ? easeOut(seg(t, 5.6, 6.3)) : t < 7 ? 1 : t < 8.3 ? 1-easeInOut(seg(t, 7, 8.3)) : 0
        f.cta=t >= 5.3 && t < 5.4 ? 1-0.03*easeIn(seg(t, 5.3, 5.4)) : t >= 5.4 && t < 5.8 ? springAt(t-5.4, 0.97, 1, 0, .pop) : 1
        f.kick=t >= 1.85 && t < 2.3 ? sin(Double.pi*seg(t, 1.85, 2.25)) : 0
        f.flash=t >= 1.85 && t < 1.97 ? 1-seg(t,1.85,1.97) : 0
        return f
    }
    static func rounded(_ x: Double) -> Double { floor(x+0.5) } // JavaScript Math.round, including negative values.
    static func hourLabel(_ v: Int) -> String { let n=((v-1)%12+12)%12+1; return n < 10 ? "0\(n)" : "\(n)" }
    static func minuteLabel(_ v: Int) -> String { let n=((v*5)%60+60)%60; return n < 10 ? "0\(n)" : "\(n)" }
}
