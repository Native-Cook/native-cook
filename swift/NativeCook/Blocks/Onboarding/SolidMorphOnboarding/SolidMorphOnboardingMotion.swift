import Foundation

enum SolidMorphOnboardingMotion {
    static let loop = 9.0
    static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { max(a, min(b, x)) }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = clamp((x-a)/max(0.00001, b-a)); return u*u*(3-2*u)
    }
    static func ease(_ x: Double) -> Double {
        let u = clamp(x); return u*u*u*(u*(u*6-15)+10)
    }
    static func timing(_ x: Double) -> Double {
        let u = clamp(x); return u < 0.5 ? 2*u*u : 1-pow(-2*u+2,2)/2
    }
    static func wrap(_ x: Double) -> Double { ((x.truncatingRemainder(dividingBy: loop))+loop).truncatingRemainder(dividingBy: loop) }
    static func progress(_ t: Double) -> Double {
        let x = wrap(t)
        if x < 0.6 { return 0 }
        if x < 1 { return 0.5*ease((x-0.6)/0.4) }
        if x < 1.8 { return 0.5 }
        if x < 2.3 { return 0.5+0.5*ease((x-1.8)/0.5) }
        if x < 2.8 { return 1 }
        if x < 5.3 { return 1+ease((x-2.8)/2.5) }
        if x < 6 { return 2 }
        if x < 8.55 { return 2*(1-ease((x-6)/2.55)) }
        return 0
    }
    static func rate(_ t: Double) -> Double { (progress(t+0.001)-progress(t-0.001))/0.002 }
    static func damped(_ t: Double, _ damping: Double, _ stiffness: Double) -> Double {
        t <= 0 ? 0 : exp(-damping*t/2)*sin(sqrt(max(0.01,stiffness-damping*damping/4))*t)
    }
    static func bob(_ t: Double) -> Double { 4*(damped(t-2.3,10,180)+damped(t-5.3,10,180))*(1-smooth(6,8.55,t)) }
    static func base(_ t: Double) -> Double { 1.5*(damped(t-2.6,14,200)+damped(t-5.6,14,200))*(1-smooth(6,8.55,t)) }
    static func hover(_ t: Double) -> Double { sin(wrap(t)/loop * .pi*4) }
    static func pulse(_ t: Double, _ at: Double, _ duration: Double) -> Double { t < at ? 0 : 1-smooth(0,duration,t-at) }
    static func arrival(_ t: Double) -> Double { max(pulse(t,2.3,0.1),pulse(t,5.3,0.1)) }
    static func basePulse(_ t: Double) -> Double { max(pulse(t,2.6,0.2),pulse(t,5.6,0.2)) }
    static func rubber(_ x: Double) -> Double {
        if x < 0 { return x*0.25/(1+abs(x)) }
        if x > 2 { return 2+(x-2)*0.25/(1+x-2) }
        return x
    }
    static func press(_ distance: Double) -> Double {
        let down = max(0,distance); return clamp((down <= 20 ? down*0.5 : 10+(down-20)*0.2)/18)
    }
    static func spinRate(_ p: Double, _ rate: Double) -> Double {
        let u = clamp(p-floor(p)); return rate*1.5*30*u*u*(1-u)*(1-u)
    }
}

/// Reanimated's mass-one closed-form springs, including its relative energy stop.
struct SolidMorphOnboardingAnimation {
    var value = 0.0
    var velocity = 0.0
    private var from = 0.0
    private var to = 0.0
    private var at = 0.0
    private var damping = 20.0
    private var stiffness = 140.0
    private var duration = 0.0
    private var kind = 0
    private var initialEnergy = 0.0
    private var springElapsed = 0.0
    private var springSampleAt = 0.0
    private(set) var finished = false

    mutating func set(_ x: Double) { value = x; velocity = 0; kind = 0; finished = false }
    mutating func spring(_ target: Double, at now: Double, damping c: Double = 20, stiffness k: Double = 140, velocity v: Double? = nil) {
        from = value; to = target; at = now; damping = c; stiffness = k
        velocity = v ?? velocity; initialVelocity = velocity
        initialEnergy = 0.5*velocity*velocity + 0.5*k*pow(target-value,2)
        springElapsed = 0; springSampleAt = now
        kind = 1; finished = false
    }
    mutating func timing(_ target: Double, at now: Double, duration seconds: Double) {
        from = value; to = target; at = now; duration = seconds; kind = 2; finished = false
    }
    private var initialVelocity = 0.0
    mutating func sample(_ now: Double) {
        finished = false
        guard kind != 0, now >= at else { return }
        let t: Double
        if kind == 1 {
            // Reanimated advances springs by at most 64 ms after a stalled frame.
            springElapsed += min(max(now-springSampleAt,0),0.064); springSampleAt = now
            t = springElapsed
        } else { t = now-at }
        if kind == 2 {
            value = from+(to-from)*SolidMorphOnboardingMotion.timing(t/max(0.000001,duration))
            velocity = 0
            if t >= duration { value = to; kind = 0; finished = true }
            return
        }
        let a = damping/2, w2 = stiffness-a*a, x0 = from-to, v0 = initialVelocity
        let x: Double, v: Double
        if w2 > 0.000001 {
            let w = sqrt(w2), b = (v0+a*x0)/w, e = exp(-a*t)
            x = e*(x0*cos(w*t)+b*sin(w*t))
            v = -a*x+e*w*(-x0*sin(w*t)+b*cos(w*t))
        } else if w2 >= -0.000001 {
            let b = v0+a*x0, e = exp(-a*t)
            x = e*(x0+b*t); v = e*b-a*x
        } else {
            let w = sqrt(-w2), r1 = -a+w, r2 = -a-w, c1 = (v0-r2*x0)/(r1-r2), c2 = x0-c1
            x = c1*exp(r1*t)+c2*exp(r2*t); v = r1*c1*exp(r1*t)+r2*c2*exp(r2*t)
        }
        value = to+x; velocity = v
        let energy = 0.5*v*v+0.5*stiffness*x*x
        if initialEnergy == 0 || energy/initialEnergy <= 6e-9 {
            value = to; velocity = 0; kind = 0; finished = true
        }
    }
}
