import Foundation

enum IconEraScrubberMotion {
    static let loop = 10.0
    static let first = 2008.0, last = 2026.0
    static let keys = [first, 2011.5, 2013, 2018, 2019.5, 2023.5, 2025, last]
    static let eras = [0.0, 0, 1, 1, 2, 2, 3, 3]
    static let moves: [(Double, Double, Double)] = [
        (1, 2, 2012.3), (2.5, 3.3, 2016), (3.6, 4.6, 2020),
        (4.9, 5.8, 2024.3), (6.3, 6.9, last), (7.8, 9.4, first)
    ]
    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = clamp01((x-a)/(b-a)); return u*u*(3-2*u)
    }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2 }
    static func easeOut(_ x: Double) -> Double { 1-pow(1-x, 3) }
    static func eraOf(_ year: Double) -> Double {
        if year <= keys[0] { return 0 }
        for i in 1..<keys.count where year <= keys[i] {
            let u = (year-keys[i-1])/(keys[i]-keys[i-1])
            return eras[i-1] + (eras[i]-eras[i-1])*u
        }
        return 3
    }
    static func yearAt(_ t: Double) -> Double {
        var y = first
        for (i, m) in moves.enumerated() where t >= m.0 {
            let k = i == moves.count-1 ? easeOut(seg(t,m.0,m.1)) : easeInOut(seg(t,m.0,m.1))
            y += (m.2-y)*k
        }
        return y
    }
    struct Frame {
        var year = first, lean = 0.0, finger = 0.0, fingerX = 0.0
    }
    static func frameAt(_ t: Double) -> Frame {
        let year = yearAt(t)
        let v = (yearAt(t+0.03)-yearAt(t-0.03))/0.06
        var f = Frame(year: year, lean: max(-1,min(1,v*0.18)))
        for (i, m) in moves.enumerated() {
            let b = i == moves.count-1 ? m.0+0.25 : m.1
            let p = easeOut(seg(t,m.0-0.12,m.0))*(1-easeOut(seg(t,b,b+0.12)))
            if p > 0 {
                f.finger = p
                let from = i == 0 ? first : moves[i-1].2
                f.fingerX = i == moves.count-1 ? -2.5*easeOut(seg(t,m.0,m.0+0.25)) : year-from
            }
        }
        return f
    }

    // Easing.bezier(0.77, 0, 0.175, 1), used by reduced motion and the return glide.
    static func timingEase(_ x: Double) -> Double {
        let x = clamp01(x)
        var lo = 0.0, hi = 1.0
        for _ in 0..<30 {
            let u = (lo+hi)/2, v = 1-u
            let bx = 3*v*v*u*0.77+3*v*u*u*0.175+u*u*u
            if bx < x { lo = u } else { hi = u }
        }
        let u = (lo+hi)/2
        return u*u*(3-2*u)
    }
}

/// Reanimated's duration spring: mass 4, energy threshold 6e-9 and 1.5 perceptual duration.
/// Analytic sampling avoids frame-dependent integration and carries the release velocity.
struct IconEraScrubberAnimation {
    var from: Double, to: Double, start: Double, duration: Double
    var damping = 1.0, velocity = 0.0, omega = 0.0
    var spring = false
    var quadratic = false
    static func constant(_ value: Double) -> Self {
        .init(from:value,to:value,start:0,duration:0)
    }
    static func glide(_ from: Double, _ to: Double, at: Double, duration: Double,
                      damping: Double, velocity: Double = 0) -> Self {
        let v = (to-from)*velocity < 0 ? 0 : velocity
        let x = from-to, settle = duration*1.5
        if x == 0 && v == 0 { return .constant(to) }
        func energyFraction(_ stiffness: Double) -> Double {
            let w = sqrt(stiffness/4)*damping
            let ex = exp(-w*settle)
            let xt = (x+(v+x*w)*settle)*ex
            let vt = -w*xt+(v+x*w)*ex
            return (stiffness*xt*xt+4*vt*vt)/(stiffness*x*x+4*v*v)-6e-9
        }
        var lo = Double.ulpOfOne, hi = 8000.0
        for _ in 0..<100 {
            let mid = (lo+hi)/2
            if abs(energyFraction(mid)) <= 6e-12 { lo = mid; hi = mid; break }
            if energyFraction(mid) > 0 { lo = mid } else { hi = mid }
        }
        var a = Self(from:from,to:to,start:at,duration:settle,damping:damping,
                     velocity:v,omega:sqrt((lo+hi)/8),spring:true)
        // A callback fires on the first display frame below the energy threshold.
        for i in 1...max(1,Int(ceil(settle*240))) {
            let t = Double(i)/240
            let state = a.raw(t)
            let energy = state.0*state.0 + pow(state.1/a.omega,2)
            if energy/(x*x+pow(v/a.omega,2)) <= 6e-9 { a.duration = t; break }
        }
        return a
    }
    private func raw(_ t: Double) -> (Double, Double) {
        let x = from-to, w = omega, z = damping
        let ex = exp(-z*w*t), wd = w*sqrt(max(0,1-z*z))
        if wd > 0 {
            let c = cos(wd*t), s = sin(wd*t), b = (velocity+z*w*x)/wd
            let displacement = ex*(x*c+b*s)
            return (displacement,-z*w*displacement+ex*(-x*wd*s+b*wd*c))
        }
        let displacement = ex*(x+(velocity+w*x)*t)
        return (displacement,-w*displacement+ex*(velocity+w*x))
    }
    func value(_ now: Double) -> Double {
        let t = max(0,now-start)
        if duration <= 0 || t >= duration { return to }
        if spring { return to+raw(t).0 }
        let u = t/duration
        let k = quadratic ? (u<0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2) : IconEraScrubberMotion.timingEase(u)
        return from+(to-from)*k
    }
    func speed(_ now: Double) -> Double {
        spring && now-start < duration ? raw(max(0,now-start)).1 : 0
    }
    var end: Double { start+duration }
}
