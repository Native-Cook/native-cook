import Foundation

// Reanimated 4 duration spring: same stiffness bisection, mass, energy threshold and velocity handoff.
struct TapEarnCoinSpring {
    let from: Double, to: Double, velocity: Double, damping: Double, stiffness: Double, initialEnergy: Double
    static let mass = 4.0, threshold = 6e-9
    init(from: Double, to: Double, duration: Double, damping: Double, velocity: Double = 0, configVelocity: Double = 0) {
        self.from = from; self.to = to; self.damping = damping
        let v = (to > from && velocity < 0) || (to < from && velocity > 0) ? 0 : velocity
        self.velocity = v
        let x = from-to, settle = duration*1.5
        func diff(_ k: Double) -> Double {
            let omega = sqrt(k/4)*damping, decay = exp(-omega*settle)
            let xt = (x+(v+x*omega)*settle)*decay
            let vt = (x+(v+x*omega)*settle)*decay * -omega+(v+x*omega)*decay
            let e0 = 0.5*k*x*x+2*v*v
            return e0 == 0 ? 0 : (0.5*k*xt*xt+2*vt*vt)/e0-Self.threshold
        }
        var lo = Double.ulpOfOne, hi = 8000.0, mid = (lo+hi)/2
        let direction = diff(hi) >= diff(lo) ? 1.0 : -1.0
        var iterations = 100
        while abs(diff(mid)) > Self.threshold*1e-3 && iterations > 0 {
            iterations -= 1
            if diff(mid)*direction < 0 { lo = mid } else { hi = mid }
            mid = (lo+hi)/2
        }
        stiffness = mid
        initialEnergy = 0.5*mid*x*x+2*configVelocity*configVelocity
    }
    func sample(_ t: Double) -> (value: Double, velocity: Double, finished: Bool) {
        if t <= 0 { return (from,velocity,initialEnergy == 0) }
        let x = from-to, omega = sqrt(stiffness/4), z = damping
        let displacement: Double, speed: Double
        if z < 1 {
            let wd = omega*sqrt(1-z*z), decay = exp(-z*omega*t)
            let sine = sin(wd*t), cosine = cos(wd*t)
            displacement = decay*(sine*((velocity+z*omega*x)/wd)+x*cosine)
            speed = -z*omega*displacement+decay*(cosine*(velocity+z*omega*x)-wd*x*sine)
        } else {
            let decay = exp(-omega*t)
            displacement = decay*(x+(velocity+omega*x)*t)
            speed = decay * -omega*(x+(velocity+omega*x)*t)+decay*(velocity+omega*x)
        }
        let energy = 0.5*stiffness*displacement*displacement+2*speed*speed
        let done = initialEnergy == 0 || energy/initialEnergy <= Self.threshold
        return (done ? to : to+displacement,done ? 0 : speed,done)
    }
}
