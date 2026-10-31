import Foundation

enum GlassOrbShelfMotion {
    static let loop = 9.6
    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2
    }
    static func snap(_ x: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        let e = easeInOut(min(1, x/0.82))
        return e + (x > 0.82 ? sin((x-0.82)/0.18 * .pi)*0.025 : 0)
    }
    static func offsetAt(_ t: Double) -> Double {
        let moves: [(Double, Double, Double, Double)] = [(1.2,2,0,1),(3.4,4.2,1,2),(5.6,6.4,2,3),(7.6,8.9,3,0)]
        var o = 0.0
        for (i,m) in moves.enumerated() where t >= m.0 {
            let x = seg(t,m.0,m.1)
            o = m.2+(m.3-m.2)*(i == 3 ? easeInOut(x) : snap(x))
        }
        return o
    }
    static func rubber(_ value: Double) -> Double {
        if value < 0 { return -(-value/(1-value*1.6))*0.6 }
        if value > 3 { return 3+(value-3)/(1+(value-3)*1.6)*0.6 }
        return value
    }
    // Solve the same cubic Bezier used by Reanimated, including its non-linear time axis.
    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        func b(_ t: Double, _ a: Double, _ c: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*c+t*t*t }
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 { let mid = (lo+hi)/2; if b(mid,x1,x2)<x { lo=mid } else { hi=mid } }
        return b((lo+hi)/2,y1,y2)
    }
    static func spring(_ t: Double, from: Double, to: Double, velocity: Double = 0, duration: Double, damping: Double) -> Double {
        if t <= 0 { return from }
        let delta = from-to
        let v = (to > from && velocity < 0) || (to < from && velocity > 0) ? 0 : velocity
        if delta == 0 && v == 0 { return to }
        // Reanimated 4 duration spring: mass 4, relative energy 6e-9, bisection
        // of stiffness against the critically damped envelope at duration * 1.5.
        let mass = 4.0, threshold = 6e-9, settling = duration*1.5
        func energyFraction(_ stiffness: Double) -> Double {
            let omega = sqrt(stiffness/mass)*damping
            let envelope = exp(-omega*settling)
            let x = (delta+(v+delta*omega)*settling)*envelope
            let vel = x * -omega+(v+delta*omega)*envelope
            return (stiffness*x*x+mass*vel*vel)/(stiffness*delta*delta+mass*v*v)-threshold
        }
        var lo = Double.ulpOfOne, hi = 8000.0, stiffness = 4000.0
        for _ in 0..<100 {
            let f = energyFraction(stiffness)
            if abs(f) <= threshold*1e-3 { break }
            if f > 0 { lo=stiffness } else { hi=stiffness }
            stiffness=(lo+hi)/2
        }
        let omega=sqrt(stiffness/mass), wd=omega*sqrt(1-damping*damping)
        let e=exp(-damping*omega*t), a=(v+damping*omega*delta)/wd
        let fragment=e*(delta*cos(wd*t)+a*sin(wd*t))
        let vel = -damping*omega*fragment+e*((v+damping*omega*delta)*cos(wd*t)-wd*delta*sin(wd*t))
        let ratio=(stiffness*fragment*fragment+mass*vel*vel)/(stiffness*delta*delta+mass*velocity*velocity)
        return ratio <= threshold ? to : to+fragment
    }
}
