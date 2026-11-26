import Foundation

/// The committed RN clock: closed-form autoplay and inverse-easing takeover from the visible speed.
final class TurbineBoostButtonMotion {
    struct Frame { var angle = 0.0, arc = 0.0, boost = 0.0, share = 0.0; var held = false }
    static let tau = Double.pi * 2, idle = 0.15 * tau, delta = 8.85 * tau
    static let correction = 20 / 19.99875
    var mode = 0, holding = 0, quarter = 0, ticks = 0
    var once = false, full = false
    var last = -1.0, start = -1.0, from = 0.0, angle = 0.0, share = 0.0, x = 0.0, y = 1.0, idleTime = 0.0
    var out = TurbineBoostButtonMotion.frame(0)
    static func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
    static func ease(_ v: Double) -> Double { v < 0.5 ? 2*v*v : 1-pow(2-2*v, 2)/2 }
    static func inverse(_ v: Double) -> Double { v < 0.5 ? sqrt(v/2) : 1-sqrt((1-v)/2) }
    static func integral(_ v: Double) -> Double {
        if v <= 0.5 { return 2*v*v*v/3 }
        return 1/12 + (v-0.5) - (1-pow(2-2*v, 3))/12
    }
    static func shareAt(_ t: Double) -> Double {
        if t < 0.5 { return 0 }; if t < 2.5 { return ease(clamp((t-0.5)/2)) }
        if t < 3.3 { return 1 }; if t < 4.8 { return pow(1-clamp((t-3.3)/1.5), 3) }; return 0
    }
    static func turned(_ t: Double) -> Double {
        var a = idle*t
        if t > 0.5 { a += delta*2*integral(clamp((t-0.5)/2)) }
        if t > 2.5 { a += delta*(min(t, 3.3)-2.5) }
        if t > 3.3 { a += delta*1.5*(1-pow(1-clamp((t-3.3)/1.5), 4))/4 }
        return correction*a
    }
    static func wrap(_ a: Double) -> Double {
        let value = a-tau*floor(a/tau)
        // Remove round-off at the exact 20-revolution seam before conversion to a float uniform.
        return value < 1e-10 || tau-value < 1e-10 ? 0 : value
    }
    static func look(_ a: Double, _ s: Double, _ held: Bool) -> Frame {
        Frame(angle: wrap(a), arc: min((idle+delta*s)*0.033, 2*tau/22), boost: s*s*(3-2*s), share: s, held: held)
    }
    static func frame(_ t: Double, _ from: Double = 0) -> Frame { look(from+turned(t), shareAt(t), t >= 0.5 && t < 3.3) }
    func takeover() {
        guard mode == 0 else { return }
        mode = 1; angle = out.angle; share = out.share
        x = Self.inverse(share); y = 1-cbrt(share)
    }
    func hold() {
        takeover(); holding += 1; idleTime = 0
        if mode == 2 { return }; x = Self.inverse(share); quarter = min(3, Int(floor(share*4+1e-9)))
    }
    func release() {
        guard holding > 0 else { return }; holding -= 1; idleTime = 0
        if holding == 0 && mode != 2 { y = 1-cbrt(share) }
    }
    func activate() {
        guard !once else { return }; hold(); once = true
        if share >= 1 { once = false; full = true; release() }
    }
    func setReduced(_ reduced: Bool) {
        if reduced && mode != 2 { mode = 2; share = 0; out = Frame(held: holding > 0) }
        else if !reduced && mode == 2 { mode = 0; start = -1; from = 0; share = 0; holding = 0; once = false }
    }
    func step(_ now: Double, autoPlay: Bool) {
        let dt = last < 0 ? 0 : min(max((now-last)/1000, 0), 0.05); last = now
        if mode == 2 {
            let before = share
            share = Self.clamp(share + (holding > 0 ? dt/0.4 : -dt/0.4))
            if before < 1 && share >= 1 { full = true; if once { holding = max(0, holding-1) }; once = false }
            out = Frame(angle: 0, arc: 0, boost: share*share*(3-2*share), share: share, held: holding > 0); return
        }
        if mode == 0 {
            if autoPlay {
                if start < 0 { start = now }; out = Self.frame(((now-start)/1000).truncatingRemainder(dividingBy: 5), from); return
            }; takeover()
        }
        if holding > 0 && share < 1 {
            x = min(1, x+dt/2); share = Self.ease(x)
            let q = min(3, Int(floor(share*4+1e-9)))
            if q > quarter { ticks += q-quarter; quarter = q }
            if x >= 1 { share = 1; full = true; if once { once = false; release() } }
        } else if holding == 0 && share > 0 {
            y = min(1, y+dt/1.5); share = y >= 1 ? 0 : pow(1-y, 3)
        }
        angle = Self.wrap(angle+(Self.idle+Self.delta*share)*dt)
        out = Self.look(angle, share, holding > 0)
        if holding > 0 || share > 0 { idleTime = 0; return }
        idleTime += dt
        if autoPlay && idleTime >= 2 { mode = 0; start = now; from = angle; out = Self.frame(0, from) }
    }
}
