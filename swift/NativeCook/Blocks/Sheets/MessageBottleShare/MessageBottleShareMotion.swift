import Foundation

// These functions translate mbs-theme.ts and mbs-motion.ts value for value.
enum MessageBottleShareMotion {
    static let loop = 10.0
    static let restAngle = -0.42
    static let flight = 1.1
    static let drift = 2.4
    struct Layout {
        let w, h, top, titleTop, subTop, horizon, homeX, homeY, scale, landX, landY, ctaTop, hintTop: Double
        init(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) {
            self.w = w; self.h = h; self.top = top
            hintTop = h - max(bottom, 16) - 20
            ctaTop = hintTop - 8 - 56
            titleTop = top + 44 + 8
            subTop = titleTop + 48
            horizon = h * 0.42
            scale = w * 0.3
            homeX = w * 0.47
            homeY = ctaTop - 32 - scale * 0.55
            landX = w * 0.66
            landY = horizon + 18
        }
    }
    struct Bottle {
        var x = 0.0, y = 0.0, angle = restAngle, scale = 1.0, floating = 1.0, alpha = 1.0
    }
    struct Frame {
        var bottle: Bottle
        var splash = -1.0
        var sent = false
        var finger = 0.0, fingerX = 0.0, fingerY = 0.0, fingerPress = 0.0
    }
    static func clamp(_ v: Double) -> Double { max(0, min(1, v)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1 - pow(-2*x+2, 3)/2 }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1-x, 3) }
    static func bob(_ t: Double) -> (dy: Double, da: Double) {
        let ph = t / 2.5 * Double.pi * 2
        return (4 * sin(ph), 0.05 * sin(ph + 0.9))
    }
    static func flightAt(_ l: Layout, _ x: Double, _ y: Double, _ a: Double, _ p: Double) -> Bottle {
        let k = easeOut(p), u = 1-k
        let apex = l.horizon - l.scale * 1.2
        let mx = (x + l.landX) / 2
        return Bottle(x: u*u*x + 2*u*k*mx + k*k*l.landX,
                      y: u*u*y + 2*u*k*apex + k*k*l.landY,
                      angle: a - 1.4 * Double.pi * k,
                      scale: l.scale * (1 - 0.9 * pow(k, 0.8)), floating: 0, alpha: 1)
    }
    static func driftAt(_ l: Layout, _ tau: Double) -> Bottle {
        let k = seg(tau, 0, drift)
        return Bottle(x: l.landX + 18*k, y: l.landY - 3*k, angle: restAngle*0.3,
                      scale: l.scale*0.1*(1-0.4*k), floating: 0, alpha: 1-easeInOut(k))
    }
    static func riseAt(_ l: Layout, _ t: Double, _ k: Double) -> Bottle {
        let b = bob(t), e = easeOut(k)
        return Bottle(x: l.homeX, y: l.homeY+b.dy+(1-e)*l.scale*0.6,
                      angle: restAngle+b.da+(1-e)*0.3, scale: l.scale, floating: 1, alpha: e)
    }
    static func frameAt(_ t: Double, _ l: Layout) -> Frame {
        let b = bob(t)
        let pull = easeInOut(seg(t, 1.25, 1.7))
        let x = l.homeX - 24*pull, y = l.homeY+b.dy-l.scale*0.35*pull
        let a = restAngle+b.da-0.25*pull
        var bottle: Bottle
        var splash = -1.0
        if t < 1.7 {
            bottle = Bottle(x: x, y: y, angle: a, scale: l.scale, floating: 1-pull, alpha: 1)
        } else if t < 1.7+flight {
            // The reference deliberately recalculates the release position's bob at t.
            bottle = flightAt(l, x, y, a, (t-1.7)/flight)
        } else if t < 8 {
            bottle = driftAt(l, t-1.7-flight)
            splash = t-1.7-flight
        } else { bottle = riseAt(l, t, seg(t, 8, 9.4)) }
        let onBottle = easeOut(seg(t, 1.05, 1.2)) * (1-easeOut(seg(t, 1.7, 1.82)))
        let onCta = easeOut(seg(t, 7.6, 7.75)) * (1-easeOut(seg(t, 7.95, 8.12)))
        return Frame(bottle: bottle, splash: splash, sent: t >= 2.4 && t < 7.8,
                     finger: max(onBottle, onCta),
                     fingerX: onCta > 0 ? l.w/2 : bottle.x-l.scale*0.2,
                     fingerY: onCta > 0 ? l.ctaTop+28 : bottle.y,
                     fingerPress: onCta > 0 ? easeOut(seg(t, 7.75, 7.84)) * (1-easeOut(seg(t, 7.9, 8.02))) : onBottle)
    }
    // CSS cubic-bezier(.23, 1, .32, 1), also used when a short drag floats home.
    static func cssEase(_ x: Double) -> Double {
        let x = clamp(x)
        var low = 0.0, high = 1.0
        for _ in 0..<24 {
            let u = (low+high)/2, v = 1-u
            if 3*v*v*u*0.23 + 3*v*u*u*0.32 + u*u*u < x { low = u } else { high = u }
        }
        return 1 - pow(1-(low+high)/2, 3)
    }
}
