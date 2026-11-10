import Foundation

struct PasskeyGlassSealAnimation {
    enum Curve { case standard, out, inOut, linear }
    var from: Double, to: Double, start: Double, duration: Double
    var curve: Curve = .standard
    func value(_ now: Double) -> Double {
        let p = PasskeyGlassSealMotion.seg(now,start,start+max(0.000001,duration))
        let q: Double
        switch curve {
        case .linear: q = p
        case .out: q = PasskeyGlassSealMotion.easeOut(p)
        case .inOut: q = PasskeyGlassSealMotion.bezier(p,0.77,0,0.175,1)
        // Reanimated withTiming defaults to Easing.inOut(Easing.quad).
        case .standard: q = p < 0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2
        }
        return from+(to-from)*q
    }
}

// Closed-form mass-1 spring, retaining the finger's velocity on release.
struct PasskeyGlassSealSpring {
    var from: Double, to: Double, start: Double, velocity: Double = 0
    var normalise = false
    func state(_ now: Double) -> (position: Double, velocity: Double, finished: Bool) {
        let t = max(0,now-start), w = PasskeyGlassSealMotion.flipOmega
        let decay = PasskeyGlassSealMotion.flipZeta*w
        let wd = w*sqrt(1-pow(PasskeyGlassSealMotion.flipZeta,2))
        let a = from-to, b = (velocity+decay*a)/wd
        let wave = a*cos(wd*t)+b*sin(wd*t), e = exp(-decay*t)
        let x = e*wave
        let v = e*((-a*wd*sin(wd*t)+b*wd*cos(wd*t))-decay*wave)
        let initialEnergy = velocity*velocity+w*w*a*a
        let finished = now >= start && (initialEnergy == 0 || (v*v+w*w*x*x)/initialEnergy < 6e-9)
        return (finished ? to : to+x,finished ? 0 : v,finished)
    }
}
