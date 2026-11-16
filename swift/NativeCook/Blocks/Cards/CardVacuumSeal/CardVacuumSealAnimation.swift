import Foundation

/// Interruptible scalar channels with the reference's mass/stiffness/damping and timing curves.
struct CardVacuumSealAnimation {
    enum Curve { case standard, label, toggle }
    enum Segment {
        case timing(Double, Double, Curve)
        case spring(Double, Double, Double, Double) // target, damping, stiffness, initial velocity
        case delay(Double)
    }
    var value: Double
    private var startValue = 0.0, started = 0.0
    private var springSpeed = 0.0, inheritedSpeed = 0.0
    private var segments: [Segment] = []
    var moving: Bool { !segments.isEmpty }

    init(_ value: Double) { self.value = value }
    mutating func set(_ x: Double) { value = x; segments = []; springSpeed = 0; inheritedSpeed = 0 }
    mutating func animate(_ steps: [Segment], at time: Double) {
        value = sample(time)
        inheritedSpeed = springSpeed
        startValue = value; started = time; segments = steps
    }
    mutating func sample(_ time: Double) -> Double {
        while let segment = segments.first {
            let t = max(0,time-started)
            switch segment {
            case let .delay(duration):
                if t < duration { return value }
                started = time
            case let .timing(target,duration,curve):
                springSpeed = 0; inheritedSpeed = 0
                let x = CardVacuumSealMotion.clamp(t/max(duration,1e-9))
                let k: Double
                switch curve {
                case .standard: k = x < 0.5 ? 2*x*x : 1-pow(-2*x+2,2)/2
                case .label: k = CardVacuumSealMotion.easeInOut(x)
                case .toggle: k = CardVacuumSealMotion.bezier(0.4,0,0.3,1,x)
                }
                value = startValue+(target-startValue)*k
                if t < duration { return value }
                value = target; started = time
            case let .spring(target,damping,stiffness,velocity):
                // Closed form of the same mass=1 spring, including the release velocity.
                let suppliedSpeed = velocity+inheritedSpeed
                let initialSpeed = (target > startValue && suppliedSpeed < 0) || (target < startValue && suppliedSpeed > 0) ? 0 : suppliedSpeed
                let a = damping/2, w2 = stiffness-a*a, d = startValue-target
                let displacement: Double, speed: Double
                if w2 > 0 {
                    let w = sqrt(w2), b = (initialSpeed+a*d)/w
                    let wave = d*cos(w*t)+b*sin(w*t)
                    displacement = exp(-a*t)*wave
                    speed = exp(-a*t)*(-a*wave+w*(-d*sin(w*t)+b*cos(w*t)))
                } else if abs(w2) < 1e-9 {
                    let b = initialSpeed+a*d
                    displacement = exp(-a*t)*(d+b*t)
                    speed = exp(-a*t)*(b-a*(d+b*t))
                } else {
                    let w = sqrt(-w2), r1 = -a+w, r2 = -a-w, c1 = (initialSpeed-r2*d)/(r1-r2), c2 = d-c1
                    displacement = c1*exp(r1*t)+c2*exp(r2*t)
                    speed = r1*c1*exp(r1*t)+r2*c2*exp(r2*t)
                }
                value = target+displacement
                springSpeed = speed
                // Reanimated's initial energy uses the supplied config velocity (not inherited inertia).
                let energy = speed*speed+stiffness*displacement*displacement
                let initial = velocity*velocity+stiffness*d*d
                if initial > 0 && energy/initial > 6e-9 { return value }
                value = target; started = time
            }
            segments.removeFirst(); startValue = value; inheritedSpeed = 0; springSpeed = 0
        }
        return value
    }
}
