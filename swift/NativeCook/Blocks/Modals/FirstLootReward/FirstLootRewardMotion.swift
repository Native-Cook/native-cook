import Foundation

enum FirstLootRewardMotion {
    static let loop = 4.0
    static func clamp(_ v: Double) -> Double { max(0, min(1, v)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a+(b-a)*k }
    static func smooth(_ k: Double) -> Double { let x = clamp(k); return x*x*(3-2*x) }
    static func out(_ k: Double) -> Double { let x = clamp(k); return 1-pow(1-x, 3) }
    static func presence(_ t: Double, _ a: Double, _ d: Double) -> Double { out(seg(t,a,a+d)) }
    static func hash(_ i: Double) -> Double { let s = sin(i*12.9898+78.233)*43758.5453; return s-floor(s) }
    static func resting(_ t: Double) -> Bool { t > 1.9 && t < 3.55 }
    struct Chest {
        var gather = 0.0, lid = 0.0, light = 0.0, since = -1.0, bump = 0.0
    }
    static func chest(_ a: Double) -> Chest {
        if a < 0 { return Chest() }
        let g = a < 0.25 ? smooth(a/0.25) : 1-smooth((a-0.25)/0.06)
        let o = a-0.25
        let lid = o >= 0 ? out(seg(o,0,0.07))*(1-smooth(seg(o,0.68,0.88))) : 0
        let land = o-0.88
        let bump = land >= 0 && land < 0.3 ? sin(min(land/0.1,1)*Double.pi)*exp(-land*8)*0.6 : 0
        let light = o >= 0 ? smooth(o/0.05)*(1-smooth(seg(o,0.55,0.88))) : 0
        return Chest(gather:g,lid:lid,light:light,since:o,bump:bump)
    }
    struct Frame {
        var t: Double
        var chest: Chest
        var tiltX = 0.0, tiltY = 0.0, press = 0.0, power = 1.0, charge = 0.0
        var keepPress = 0.0, viewPress = 0.0
        var ring: Double { 1-smooth(seg(t,0.12,0.24)) }
        var badgeA: Double { seg(t,0.03,0.21) }
        var badgeS: Double {
            if t < 0.233 { return mix(0.5,1,smooth(seg(t,0.04,0.233))) }
            if t < 0.3 { return mix(1,1.03,1-pow(1-seg(t,0.233,0.3),2)) }
            return mix(1.03,1,smooth(seg(t,0.3,0.42)))
        }
        var sceneA: Double { smooth(seg(t,0.25,0.8)) }
        var sparkA: Double { seg(t,0.45,1.55) }
        var fade: Double { smooth(seg(t,3.55,3.92)) }
    }
    static func at(_ t: Double) -> Frame { Frame(t:t,chest:chest(t-0.48)) }

    // Exact Reanimated 4 duration spring: mass 4, relative energy 6e-9,
    // stiffness solved by the same bounded bisection, then the analytic solution.
    struct Spring {
        var from = 0.0, target = 0.0, velocity = 0.0, start = -10.0
        var damping = 0.55, stiffness = 900.0
        func sample(_ now: Double) -> (Double, Double) {
            let t = max(0,now-start), w = sqrt(stiffness/4), x = from-target
            let position: Double, speed: Double
            if damping >= 1 {
                let b = velocity+w*x, e = exp(-w*t)
                position = target+(x+b*t)*e; speed = (b-w*(x+b*t))*e
            } else {
                let wd = w*sqrt(1-damping*damping), b = (velocity+damping*w*x)/wd
                let e = exp(-damping*w*t), q = x*cos(wd*t)+b*sin(wd*t)
                position = target+e*q
                speed = e*(-damping*w*q-x*wd*sin(wd*t)+b*wd*cos(wd*t))
            }
            let initial = 0.5*stiffness*x*x
            let energy = 0.5*stiffness*pow(position-target,2)+2*speed*speed
            return initial == 0 || energy/initial <= 6e-9 ? (target,0) : (position,speed)
        }
        mutating func set(_ value:Double) { from=value; target=value; velocity=0; start = -10 }
        mutating func aim(_ value: Double, _ now: Double, _ seconds: Double, _ ratio: Double) {
            let s = sample(now); from=s.0; velocity=s.1; target=value; start=now; damping=ratio
            if (value > from && velocity < 0) || (value < from && velocity > 0) { velocity=0 }
            let x=from-target, d=seconds*1.5
            if x == 0 && velocity == 0 { return }
            func diff(_ k:Double) -> Double {
                let w=sqrt(k/4)*ratio, b=velocity+x*w, e=exp(-w*d)
                let xt=(x+b*d)*e, vt=(x+b*d)*e*(-w)+b*e
                return (0.5*k*xt*xt+2*vt*vt)/(0.5*k*x*x+2*velocity*velocity)-6e-9
            }
            var lo=Double.ulpOfOne, hi=8000.0, mid=(lo+hi)/2
            let direction=diff(hi) >= diff(lo) ? 1.0 : -1.0
            for _ in 0..<100 {
                let f=diff(mid)
                if abs(f) <= 6e-12 { break }
                if f*direction < 0 { lo=mid } else { hi=mid }; mid=(lo+hi)/2
            }
            stiffness=mid
        }
    }
}
