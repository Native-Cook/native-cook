import Foundation

enum PasskeyGlassSealMotion {
    static let loop = 10.0
    static let scan = 2.5
    static let hold = 1.4
    static let flipZeta = 0.68
    static let flipOmega = 2 * Double.pi * 1.25
    // JavaScript Math.round rounds a negative half toward +infinity.
    static func nearestFace(_ f: Double) -> Double { floor(f + 0.5) }
    static func faceOf(_ f: Double) -> Int { Int(abs(nearestFace(f))) % 2 }
    static func flipSpring(_ t: Double) -> Double {
        guard t > 0 else { return 0 }
        let z = flipZeta, w = flipOmega, wd = w * sqrt(1-z*z)
        return 1-exp(-z*w*t)*(cos(wd*t)+(z*w/wd)*sin(wd*t))
    }
    struct Frame {
        var light = 0.0, unwind = 0.0, key = 0.0, turn = 0.0, flip = 0.0
        var scan = 1.0, finger = 0.0, press = 0.0
        var status: String { PasskeyGlassSealMotion.faceOf(flip) == 1 ? "Unlocked" : unwind > 0.02 ? "Verified" : light > 0.02 ? "Hold still…" : "Scanning…" }
    }
    static func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2 }
    static func easeOut(_ x: Double) -> Double { 1-pow(1-x, 3) }
    static func turnAt(_ x: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        return 1-exp(-6*x)*cos(9*x)*(1-x)
    }
    static func at(_ t: Double) -> Frame {
        let finger = easeOut(seg(t, 0.8, 1))*(1-easeOut(seg(t, 2.75, 2.93)))
        return Frame(
            light: seg(t, 1.2, 2.6)*(1-easeInOut(seg(t, 8.6, 9.5))),
            unwind: easeInOut(seg(t, 2.7, 3.9))*(1-easeInOut(seg(t, 8.2, 9.4))),
            key: easeOut(seg(t, 3.7, 4.3))*(1-easeInOut(seg(t, 8.2, 8.6))),
            turn: turnAt(seg(t, 4.4, 5))*(1-easeInOut(seg(t, 8.1, 8.4))),
            flip: flipSpring(t-5.2)-flipSpring(t-7.6),
            scan: clamp(1-easeOut(seg(t, 1, 1.3))+easeOut(seg(t, 9.2, 9.5))),
            finger: finger, press: finger)
    }
    static func bezier(_ p: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if p <= 0 { return 0 }; if p >= 1 { return 1 }
        func cubic(_ t: Double, _ a: Double, _ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 { let m = (lo+hi)/2; if cubic(m,x1,x2) < p { lo = m } else { hi = m } }
        return cubic((lo+hi)/2,y1,y2)
    }
}
