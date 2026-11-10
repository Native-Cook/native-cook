import SwiftUI

// Direct translations of gac-theme.ts, gac-motion.ts and gac-depth.ts.
enum GlassApertureCameraTheme {
    struct Layout {
        let w, h, top, bottom: Double
        var sx: Double { w / 402 }
        var k: Double { (h - top - bottom) / 778 }
        init(_ size: CGSize, _ top: Double, _ bottom: Double) {
            w = size.width; h = size.height; self.top = max(top,44); self.bottom = max(bottom,20)
        }
        func Y(_ y: Double) -> Double { y < 62 ? y * top / 62 : top + (y - 62) * k }
        var viewH: Double { Y(770) }
        var photoH: Double { w * 2622 / 1206 }
        var irisX: Double { 201 * sx }
        var irisY: Double { Y(360) }
        var irisR: Double { 128 * sx }
        var shutterX: Double { 199 * sx }
        var shutterY: Double { Y(710) }
        var far: CGPoint { CGPoint(x: 0.36 * w, y: 0.6 * photoH) }
        var near: CGPoint { CGPoint(x: 0.75 * w, y: 0.64 * photoH) }
    }
}

enum GlassApertureCameraMotion {
    static let loop = 10.0
    enum Shot {
        static let go = 0.3, end = 1.5
        static let y = (v0: -3.5, zeta: 0.66, omega: 8.0)
        static let k = (to: 0.14, zeta: 0.82, omega: 11.0)
        static let rot = (to: -6.0, v0: 28.0, zeta: 0.5, omega: 11.0)
    }
    struct ShotPose {
        var y = 0.0, k = 1.0, rot = 0.0, stretch = 0.0, alpha = 0.0, bump = 0.0
    }
    struct Frame {
        var t = 0.0, fnum = 2.8, iris = 0.0, focus = 0.2, snap = 0.0, shot = -1.0
        var sent = 0.0, ring = -1.0, finger = 0.0, fingerAt = 0, spread = 80.0, press = 0.0
        var ringPoint: CGPoint? = nil
        var button = 0, buttonPress = 0.0
    }
    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
    static func io(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2 }
    static func out(_ x: Double) -> Double { 1-pow(1-x, 3) }
    static func fAt(_ u: Double) -> Double { 1.4 * pow(16/1.4, clamp01(u)) }
    static func uOf(_ f: Double) -> Double { log(f/1.4) / log(16/1.4) }
    static func springAt(_ t: Double, _ from: Double, _ to: Double, _ v0: Double, _ zeta: Double, _ omega: Double) -> Double {
        if t <= 0 { return from }
        let x0 = from-to, wd = omega*sqrt(1-zeta*zeta)
        let b = (v0+zeta*omega*x0)/wd
        return to+exp(-zeta*omega*t)*(x0*cos(wd*t)+b*sin(wd*t))
    }
    private static func shotY(_ f: Double) -> Double {
        springAt(f,0,1,Shot.y.v0,Shot.y.zeta,Shot.y.omega)
    }
    static let land: Double = {
        var f = 0.0
        while f < 2 && shotY(f) < 1 { f += 0.001 }
        return f
    }()
    static func shotAt(_ s: Double) -> ShotPose {
        let f = s-Shot.go
        if s < 0 || f <= 0 { return ShotPose() }
        let y = shotY(f)
        let vy = (shotY(f+0.004)-shotY(max(0,f-0.004)))/(f > 0.004 ? 0.008 : 0.004+f)
        let k = springAt(f,1,Shot.k.to,0,Shot.k.zeta,Shot.k.omega)
        let rot = springAt(f,0,Shot.rot.to,Shot.rot.v0,Shot.rot.zeta,Shot.rot.omega)
        let tau = f-land
        let bump = tau > 0 ? exp(-7*tau)*sin(26*tau) : 0
        return ShotPose(y:y,k:k,rot:rot,stretch:max(-1,min(1,vy/6)),alpha:1-seg(f,land+0.05,land+0.4),bump:bump)
    }
    static func frameAt(_ t: Double) -> Frame {
        let u0 = uOf(2.8), u11 = uOf(11)
        var u = u0
        if t >= 0.9 && t < 2.2 { u = u0 * (1-io(seg(t, 0.9, 1.8))) }
        else if t >= 2.2 && t < 3.8 { u = u11 * io(seg(t, 2.2, 3.4)) }
        else if t >= 3.8 { u = u11 + (u0-u11) * io(seg(t, 3.8, 4.4)) }
        var f = Frame()
        f.t = t; f.fnum = fAt(u)
        f.iris = out(seg(t, 0.6, 0.9)) * (1-io(seg(t, 4.5, 5)))
        f.focus = 0.2 + 0.72 * (io(seg(t, 5.4, 6))-io(seg(t, 7, 7.6)))
        let a = seg(t, 7.8, 8.15)
        f.snap = a < 0.35 ? io(a/0.35) : 1-out((a-0.35)/0.65)
        f.shot = t >= 7.8 && t < 7.8+Shot.end ? t-7.8 : -1
        f.sent = out(seg(t, 8.52, 8.77)) * (1-io(seg(t, 9.35, 9.6)))
        f.ring = t >= 5.4 && t < 6.6 ? t-5.4 : t >= 7 && t < 8.2 ? t-7 : -1
        f.finger = out(seg(t, 0.4, 0.6)) * (1-out(seg(t, 4.4, 4.6)))
        f.press = f.finger
        for (start, at) in [(5.4, 1), (7.0, 3), (7.8, 2)] {
            let p = out(seg(t, start-0.18, start-0.03)) * (1-out(seg(t, start+0.1, start+0.26)))
            if p > f.finger { f.finger = p; f.fingerAt = at; f.press = p }
        }
        f.spread = 60 + 90 * (1-u/u11)
        return f
    }
    static func label(_ f: Double) -> String {
        let v = f < 9.95 ? floor(f*10+0.5)/10 : floor(f+0.5)
        return v < 9.95 ? String(format: "%.1f", v) : String(Int(v))
    }
    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        func cubic(_ t: Double, _ a: Double, _ b: Double) -> Double {
            3*(1-t)*(1-t)*t*a + 3*(1-t)*t*t*b + t*t*t
        }
        var lo = 0.0, hi = 1.0
        for _ in 0..<32 { let m = (lo+hi)/2; if cubic(m,x1,x2) < x { lo=m } else { hi=m } }
        return cubic((lo+hi)/2,y1,y2)
    }
    static func depthAt(_ u: Double, _ v: Double) -> Double {
        func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
            let t = clamp01((x-a)/(b-a)); return t*t*(3-2*t)
        }
        var d = 1-clamp01(hypot((u-0.36)*1.5,v-0.64)*1.9)*0.8
        let sky = smooth(0.2,0.26,u)*(1-smooth(0.56,0.62,u+(0.5-v)*0.5))*(1-smooth(0.46,0.52,v))
        d = max(d, sky*0.97)
        let right = smooth(0.52,0.6,u)*(1-smooth(0.55,0.6,v))
        d += (0.55+0.35*(1-u)+0.15*(0.55-v)-d)*right*0.9
        let van = smooth(0.48,0.52,u)*smooth(0.565,0.59,v)*(1-smooth(0.89,0.92,v))
        d += (0.2-d)*van
        d += (0.04-d)*smooth(0.82,0.98,v)*(1-van)
        return clamp01(d)
    }
}
