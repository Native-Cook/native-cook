import Foundation

enum SnowGlobeGiftMotion {
    static let loop = 10.0
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2
    }
    static func easeOut(_ x: Double) -> Double { 1-pow(1-x, 3) }
    struct Frame {
        var wobble = 0.0, swirl = 0.0, spin = 0.0, lid = 0.0, glow = 0.0, amount = 0.0
        var finger = 0.0, fingerAt = 0.0, fingerX = 0.0, fingerPress = 0.0
    }
    static func frameAt(_ t: Double) -> Frame {
        var f = Frame()
        if t >= 0.9 && t < 3.3 {
            let tau = t-0.9
            let drive = 1-seg(t, 0.9, 1.6)
            f.wobble = 0.16*sin(tau*2*Double.pi*2.6)*(0.4+0.6*drive)*exp(-1.6*max(0,t-1.6))
            f.wobble *= 1-seg(t, 2.8, 3.3)
        }
        let up = easeOut(seg(t, 1, 1.6))
        let down = seg(t, 1.6, 5.2)
        f.swirl = up*(1-easeInOut(down))
        let k = seg(t, 0.9, 5.2)
        f.spin = 9*(1-(1-k)*(1-k))
        f.lid = easeOut(seg(t, 5.7, 6.2))*(1-easeInOut(seg(t, 8.3, 8.9)))
        f.glow = f.lid
        f.amount = seg(t, 5.9, 6.3)*(1-seg(t, 8.1, 8.4))
        if t < 3 {
            f.finger = easeOut(seg(t, 0.75, 0.9))*(1-easeOut(seg(t, 1.6, 1.78)))
            f.fingerX = 0.5*sin((t-0.9)*2*Double.pi*2.6)*(t > 0.9 ? 1 : 0)
            f.fingerPress = f.finger
        } else {
            f.fingerAt = 1
            f.finger = easeOut(seg(t, 5.45, 5.58))*(1-easeOut(seg(t, 5.74, 5.9)))
            f.fingerPress = easeOut(seg(t, 5.58, 5.68))*(1-easeOut(seg(t, 5.72, 5.86)))
        }
        return f
    }
    static func bezier(_ p: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        func b(_ t: Double, _ a: Double, _ c: Double) -> Double {
            3*(1-t)*(1-t)*t*a + 3*(1-t)*t*t*c + t*t*t
        }
        let p = clamp(p)
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 {
            let m = (lo+hi)/2
            if b(m,x1,x2) < p { lo = m } else { hi = m }
        }
        return b((lo+hi)/2,y1,y2)
    }
}

struct SnowGlobeGiftLayout {
    var w: Double, h: Double, barTop: Double
    var heroX: Double, heroY: Double, heroS: Double, amountY: Double
    var fromTop: Double, titleTop: Double, noteTop: Double, ctaTop: Double
    init(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) {
        self.w = w; self.h = h; barTop = top
        ctaTop = h-max(bottom,16)-8-56
        noteTop = ctaTop-32-44
        titleTop = noteTop-8-42
        fromTop = titleTop-4-18
        let room0 = top+44+16
        let room = fromTop-24-room0
        heroS = min(w*0.34,room/2.45,132)
        heroX = w/2
        heroY = room0+(room-heroS*2.35)/2+heroS
        amountY = heroY-heroS*0.64
    }
}
