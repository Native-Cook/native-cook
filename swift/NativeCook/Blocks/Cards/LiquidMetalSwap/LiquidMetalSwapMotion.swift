import Foundation

// A direct transcription of lms-motion.ts. All scripted phases use one clock.
enum LiquidMetalSwapMotion {
    static let loop = 10.0
    static let hold = 1.5
    static let poolRadius = 118.0
    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let u = clamp01((x-a)/(b-a)); return u*u*(3-2*u)
    }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
    static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
    static func springStep(_ x: Double) -> Double {
        x <= 0 ? 0 : 1-exp(-6*x)*(cos(10*x)+0.6*sin(10*x))
    }
    static func settle(_ d0: Double, _ v0: Double, _ tau: Double, _ omega: Double) -> Double {
        (d0+(v0+omega*d0)*tau)*exp(-omega*tau)
    }
    struct Look {
        var kind, sink, soft, poolR, poolH, wobble, drops, gold: Double
    }
    static func look(_ m: Double) -> Look {
        let x = clamp01(m)
        let sink = x < 0.5 ? easeInOut(seg(x,0.03,0.5)) : 1-easeInOut(seg(x,0.5,0.97))
        return Look(kind: x < 0.5 ? 0 : 1, sink: sink, soft: smooth(0.1,0.55,sink),
                    poolR: poolRadius*pow(sink,0.65), poolH: 5+12*sink, wobble: sink,
                    drops: x > 0.36 && x < 0.64 ? seg(x,0.36,0.64) : 0, gold: smooth(0.4,0.6,x))
    }
    static func sway(_ t: Double) -> Double { 0.3*sin(2*Double.pi*2*t/loop) }
    enum T {
        static let press1 = 1.0, drag0 = 3.3, drag1 = 3.9, press2 = 5.2, release2 = 5.8
        static let press3 = 6.6, flip = 0.35, swapped = 1.1
    }
    struct Frame {
        var fill = 0.0, start = 0.0, m = 0.0, flip = 0.0, swapped = 0.0, doneText = 0.0
        var press = 0.0, yaw = 0.0, finger = 0.0, fingerAt = 0.0, fingerX = 0.0
    }
    static func frameAt(_ t: Double) -> Frame {
        let done1 = T.press1+hold, done3 = T.press3+hold
        var fill = 0.0, start = 0.0
        if t >= T.press1 && t < done1 { fill = (t-T.press1)/hold }
        else if t >= done1 && t < T.press2 { fill = 0 }
        if t >= done1 { start = 1 }
        if t >= T.press2 && t < T.release2 { fill = (t-T.press2)/hold }
        else if t >= T.release2 && t < T.press3 { fill = settle((T.release2-T.press2)/hold,1/hold,t-T.release2,9) }
        if t >= T.press3 && t < done3 { fill = (t-T.press3)/hold }
        if t >= done3 { start = 0; fill = 0 }
        let m = start == 0 ? fill : 1-fill
        let flip = easeInOut(seg(t,done1,done1+T.flip))*(1-easeInOut(seg(t,done3,done3+T.flip)))
        let swapped = (t >= done1 && t < done1+T.swapped+0.3 ? 1-seg(t,done1+T.swapped,done1+T.swapped+0.3) : 0)
            + (t >= done3 && t < done3+T.swapped+0.3 ? 1-seg(t,done3+T.swapped,done3+T.swapped+0.3) : 0)
        let doneText = clamp01(seg(t,done1,done1+0.2)-seg(t,done1+T.swapped,done1+T.swapped+0.3))
            + clamp01(seg(t,done3,done3+0.2)-seg(t,done3+T.swapped,done3+T.swapped+0.3))
        func pressOf(_ a: Double,_ b: Double) -> Double { easeOut(seg(t,a,a+0.1))*(1-easeOut(seg(t,b,b+0.15))) }
        var yaw = 0.0
        if t >= T.drag0 && t < T.drag1 { yaw = 1.6*easeOut(seg(t,T.drag0,T.drag1)) }
        else if t >= T.drag1 { yaw = 1.6*(1-springStep((t-T.drag1)/1.1)) }
        func near(_ a: Double,_ b: Double) -> Double { easeOut(seg(t,a-0.22,a))*(1-easeOut(seg(t,b,b+0.22))) }
        let onButton = near(T.press1,done1)+near(T.press2,T.release2)+near(T.press3,done3)
        let onCoin = near(T.drag0,T.drag1)
        return Frame(fill: fill, start: start, m: m, flip: flip, swapped: clamp01(swapped), doneText: clamp01(doneText),
                     press: clamp01(pressOf(T.press1,done1)+pressOf(T.press2,T.release2)+pressOf(T.press3,done3)),
                     yaw: yaw, finger: clamp01(onButton+onCoin), fingerAt: onCoin > 0.001 ? 1 : 0,
                     fingerX: easeOut(seg(t,T.drag0,T.drag1)))
    }
    // Native touch timings use the source's cubic Bezier control points.
    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let p = clamp01(x)
        func value(_ t: Double,_ a: Double,_ b: Double) -> Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 { let t = (lo+hi)/2; if value(t,x1,x2) < p { lo=t } else { hi=t } }
        return value((lo+hi)/2,y1,y2)
    }
}
