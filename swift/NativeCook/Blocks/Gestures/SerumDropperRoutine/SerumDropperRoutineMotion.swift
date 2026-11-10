import Foundation

// Closed-form translation of sdr-motion.ts. The pinned hook samples this directly.
enum SerumDropperRoutineMotion {
    static let loop = 10.0
    static let grow = 0.55
    static let neck = 0.16
    struct Frame {
        var lift = 0.0, squeeze = 0.0, soak = 0.0, done = 1.0
        var drops = [-1.0, -1.0, -1.0]
        var finger = 0.0, fingerAt = 0, press = 0.0
    }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func ease(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
    static func out(_ x: Double) -> Double { 1-pow(1-x,3) }
    static func at(_ t: Double) -> Frame {
        var f = Frame()
        f.lift = ease(seg(t,1.3,1.9)) * (1-ease(seg(t,5.2,5.8)))
        let sq = seg(t,2,2.25) * (1-seg(t,4.4,4.7))
        var pulse = 0.0
        for (i, start) in [2.1,2.9,3.7].enumerated() {
            let a = t-start
            f.drops[i] = a >= 0 && t < 9.9 ? a : -1
            if a >= 0 && a < grow+neck { pulse = max(pulse,sin(.pi*a/(grow+neck))) }
        }
        f.squeeze = clamp(sq*(0.55+0.45*pulse))
        f.soak = seg(t,5.8,9.6)
        f.done = t < 0.5 ? 1 : t < 4.6 ? 1-out(seg(t,0.5,0.75)) : out(seg(t,4.6,4.95))
        let f0 = out(seg(t,0.32,0.47)) * (1-out(seg(t,0.58,0.74)))
        f.finger = f0; f.press = f0
        let f1 = out(seg(t,1.02,1.17)) * (1-out(seg(t,5.2,5.36)))
        if f1 > f.finger { f.finger = f1; f.fingerAt = 1; f.press = 0.6+0.4*f.squeeze }
        return f
    }
    // Reanimated withTiming's default inOut(quad), or Easing.bezier(.77,0,.175,1).
    static func timing(_ x: Double, custom: Bool) -> Double {
        let p = clamp(x)
        if !custom { return p < 0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2 }
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 {
            let u = (lo+hi)/2
            let bx = 3*(1-u)*(1-u)*u*0.77+3*(1-u)*u*u*0.175+u*u*u
            if bx < p { lo = u } else { hi = u }
        }
        let u = (lo+hi)/2
        return 3*(1-u)*u*u+u*u*u
    }
}

struct SerumDropperRoutineLayout: Equatable {
    let w: Double, h: Double, top: Double, bottom: Double
    init(w: Double, h: Double, top: Double, bottom: Double) {
        self.w = w; self.h = h
        self.top = max(top,44); self.bottom = max(bottom,20)
    }
    var sx: Double { w/402 }
    var k: Double { (h-top-bottom)/778 }
    func y(_ value: Double) -> Double { value < 62 ? value*top/62 : top+(value-62)*k }
    static let cards = [(26.0,308.0,165.0,210.0),(211.0,378.0,164.0,210.0),(26.0,610.0,165.0,200.0),(211.0,680.0,164.0,200.0)]
    static let labels = [(26.0,538.0),(210.0,314.0),(210.0,617.0),(26.0,840.0)]
    var bh: Double { 124*sx }
    var bw: Double { bh*700/1100 }
    var bx: Double { 110*sx }
    var btop: Double { y(384) }
    var dh: Double { 160*sx }
    var dw: Double { dh*300/1100 }
    var dtop: Double { btop-52*sx }
    var seatTipY: Double { dtop+dh*1034/1100 }
    var floor: Double { y(504) }
    var liftX: Double { 165*sx }
    func tip(_ lift: Double) -> CGPoint {
        let e1 = SerumDropperRoutineMotion.ease(min(1,lift/0.55))
        let e2 = SerumDropperRoutineMotion.ease(max(0,(lift-0.55)/0.45))
        return CGPoint(x: bx+(liftX-bx)*e2, y: seatTipY-100*sx*e1+(y(412)-(seatTipY-100*sx))*e2)
    }
}
