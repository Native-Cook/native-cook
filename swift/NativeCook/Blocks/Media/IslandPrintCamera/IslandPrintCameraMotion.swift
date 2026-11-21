import Foundation

/// A direct translation of ipc-motion.ts. Time is seconds; geometry is the 402-point RN canvas.
enum IslandPrintCameraMotion {
    static let loop = 11.0
    static let glide = (d: 22.1, k: 157.9)
    static let pop = (d: 19.0, k: 195.0)
    static let snap = (d: 30.5, k: 322.3)
    static func clamp(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a+(b-a)*k }
    static func easeOut(_ k: Double) -> Double { 1-pow(1-k, 3) }
    static func easeIn(_ k: Double) -> Double { k*k*k }
    static func easeInOut(_ k: Double) -> Double { k < 0.5 ? 4*k*k*k : 1-pow(-2*k+2, 3)/2 }
    static func springAt(_ t: Double, _ from: Double, _ to: Double, _ v0: Double, _ d: Double, _ k: Double) -> Double {
        if t <= 0 { return from }
        let x0 = from-to, w0 = sqrt(k), z = d/(2*w0)
        if z < 1 {
            let wd = w0*sqrt(1-z*z), e = exp(-z*w0*t)
            return to+e*(x0*cos(wd*t)+(v0+z*w0*x0)/wd*sin(wd*t))
        }
        if z == 1 { return to+exp(-w0*t)*(x0+(v0+w0*x0)*t) }
        let s = sqrt(z*z-1), r1 = -w0*(z-s), r2 = -w0*(z+s)
        let c2 = (v0-r1*x0)/(r2-r1), c1 = x0-c2
        return to+c1*exp(r1*t)+c2*exp(r2*t)
    }
    struct Frame {
        var p = 0.0, slot = 0.0, antic = 0.0, slotDip = 0.0
        var rise = [0.0, 0, 0], flood = [0.0, 0, 0]
        var depth = 0.0, exposure = 0.0, blur = 0.0, focus = 1.0, glint = 0.0
        var black = 0.0, flash = 0.0, bloom = 0.0, dim = 1.0, led = 0, ledHalo = 0.0
        var f = 0.0, printOut = false, printTop = 0.0, printRot = 0.0, printScaleY = 1.0
        var dragX = 0.0, dragY = 0.0
        var printTilt = 0.0, d = 0.0, sheen = 0.0, empty = 1.0, printShadow = 0.0
    }
    static func domeRise(_ p: Double, _ a: Double, _ b: Double) -> Double {
        let k = seg(p, a, b)
        return easeOut(k)+(k > 0.85 ? sin((k-0.85)/0.15 * .pi)*0.05 : 0)
    }
    static func feedAt(_ t: Double) -> Double {
        let u = seg(t, 4.85, 6.65)
        return u+0.12/(2 * .pi * 14)*sin(2 * .pi * 14*u)
    }
    static func pAt(_ t: Double) -> Double {
        if t < 0.7 { return 0 }
        if t < 1.45 { return 0.46*easeInOut(seg(t, 0.7, 1.45)) }
        if t < 1.7 { return 0.46 }
        if t < 4 { return min(springAt(t-1.7, 0.46, 1, 2.4, glide.d, glide.k), 1.02) }
        if t < 4.7 { return 1-easeInOut(seg(t, 4, 4.7)) }
        return 0
    }
    static func frameAt(_ input: Double) -> Frame {
        let t = (input.truncatingRemainder(dividingBy: loop)+loop).truncatingRemainder(dividingBy: loop)
        var f = Frame()
        f.p = pAt(t)
        let slotOn = t >= 4 && t < 6.7
        f.slot = t < 4 ? 0 : t < 6.7 ? easeInOut(seg(t, 4, 4.7)) : max(0, springAt(t-6.7, 1, 0, 0, snap.d, snap.k))
        f.antic = easeOut(seg(t, 0.6, 0.7))*(1-seg(f.p, 0, 0.15))*(t < 2 ? 1 : 0)
        f.slotDip = slotOn ? 2*easeOut(seg(t, 4.7, 4.85))*(1-seg(t, 6.6, 6.7))+(t > 4.7 && t < 4.85 ? 0.5*sin(t*2 * .pi * 30) : 0) : 0
        let pd = t >= 4 && t < 4.7 ? max(0, (f.p-0.45)/0.55) : f.p
        f.rise = [domeRise(pd, 0.45, 0.85), domeRise(pd, 0.5, 0.9), domeRise(pd, 0.55, 0.95)]
        f.flood = [seg(pd, 0.69, 0.85), seg(pd, 0.74, 0.9), seg(pd, 0.79, 0.95)]
        if t >= 2.9 && t < 3.5 { f.depth = min(springAt(t-2.9, 0, 0.45, 0, snap.d, snap.k), 0.5) }
        else if t >= 3.5 && t < 3.62 { f.depth = mix(0.45, 1, easeIn(seg(t, 3.5, 3.58))) }
        else if t >= 3.62 && t < 4 { f.depth = max(0, springAt(t-3.62, 1, 0, 0, pop.d, pop.k)) }
        f.exposure = t < 0.9 ? 0 : t < 1.9 ? mix(0, 1.15, easeOut(seg(t, 0.9, 1.9))) : t < 2.3 ? mix(1.15, 1, easeInOut(seg(t, 1.9, 2.3))) : 1
        f.blur = t < 1.5 ? 12 : t < 2.3 ? mix(12, 1.5, easeOut(seg(t, 1.5, 2.3))) : t < 2.9 ? 1.5 : t < 3.4 ? mix(1.5, 0, easeInOut(seg(t, 2.9, 3.4))) : 0
        f.focus = 1+0.03*sin(.pi*easeInOut(seg(t, 2.9, 3.4)))
        f.glint = t >= 1.9 && t < 2.5 ? easeInOut(seg(t, 1.9, 2.5)) : 0
        f.black = t >= 3.5 && t < 3.56 ? 1 : 0
        f.flash = t >= 3.56 && t < 3.8 ? 0.9*(1-easeOut(seg(t, 3.56, 3.8))) : 0
        f.bloom = t >= 3.56 && t < 3.8 ? 0.25*(1-easeOut(seg(t, 3.56, 3.8))) : 0
        f.dim = t >= 3.56 && t < 4.1 ? 1-0.18*sin(.pi*seg(t, 3.56, 4.1)) : 1
        f.led = t >= 3.5 && t < 7.1 ? 2 : t >= 3.4 && t < 3.5 ? 1 : 0
        f.ledHalo = f.led == 1 ? 0.18 : f.led == 2 ? 0.18*seg(t, 3.5, 3.65)*(1-seg(t, 7, 7.1)) : 0
        f.f = t < 4.85 ? 0 : t < 6.65 ? feedAt(t) : 1
        f.printTop = 48-247+247*f.f
        if t >= 6.65 && t < 6.91 {
            let q = seg(t, 6.65, 6.91)
            f.printTop = mix(48, 152, q*q); f.printOut = true; f.printShadow = 1
        } else if t >= 6.91 && t < 9.2 {
            f.printTop = 152; f.printOut = true
            f.printScaleY = min(1, springAt(t-6.91, 0.985, 1, 0, pop.d, pop.k))
            f.printRot = springAt(t-6.91, 2.5, 0, -40, pop.d, pop.k)
            f.printShadow = 1-seg(t, 6.91, 7.3)
        } else if t >= 9.2 && t < 9.8 {
            let k = easeIn(seg(t, 9.2, 9.8))
            f.printTop = mix(152, 890, k); f.printRot = 6*k; f.printOut = true
        } else if t >= 9.8 { f.printTop = 890; f.printOut = true }
        f.printTilt = t < 6.65 ? 38*(1-f.f) : 0
        f.d = t < 5.9 ? 0 : t < 8.8 ? seg(t, 5.9, 8.8) : t < 9.8 ? 1 : 0
        f.sheen = t >= 8.6 && t < 9.1 ? easeInOut(seg(t, 8.6, 9.1)) : 0
        f.empty = t < 6.65 ? 1 : t < 6.9 ? 1-seg(t, 6.65, 6.9) : t < 9.5 ? 0 : easeOut(seg(t, 9.5, 10))
        return f
    }
    static func liveFrameAt(_ p: Double, _ depth: Double, _ focusLock: Double) -> Frame {
        var f = frameAt(10.5)
        let q = min(max(p, 0), 1.02), open = seg(q, 0.35, 1)
        f.p = q
        f.rise = [domeRise(q, 0.45, 0.85), domeRise(q, 0.5, 0.9), domeRise(q, 0.55, 0.95)]
        f.flood = [seg(q, 0.69, 0.85), seg(q, 0.74, 0.9), seg(q, 0.79, 0.95)]
        f.depth = depth; f.exposure = open; f.blur = mix(mix(12, 1.5, open), 0, focusLock)
        f.focus = 1+0.03*sin(.pi*min(focusLock, 1))
        f.led = focusLock > 0.95 ? 1 : 0; f.ledHalo = focusLock > 0.95 ? 0.18 : 0
        return f
    }
}
