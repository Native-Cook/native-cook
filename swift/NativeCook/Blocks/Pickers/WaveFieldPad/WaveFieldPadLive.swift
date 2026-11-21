import CoreGraphics
import Foundation

/// Same live state and substepped forces as the reviewed RN pad. No view or haptic work in the integrator.
struct WaveFieldPadLive {
    enum Motion { case idle, drag, release, home }
    var motion: Motion = .idle
    var u = 0.5, v = 0.5, vu = 0.0, vv = 0.0
    var du = 0.5, dv = 0.5, dvu = 0.0, dvv = 0.0
    var finger = CGPoint(x: 0.5, y: 0.5), offset = CGPoint.zero
    var press = 0.0, settle = 0.0, still = 0.0, beat = 0.0, lock = 0.0
    var ring = 0.0, flash = -1.0, blip = -1.0
    var time = 0.0, lastTouch = 0.0
    var velocity = CGVector.zero
    var ripples: [WaveFieldPadFrame.Ripple] = []
    var nextRippleSlot = 0
    var anchor = CGPoint(x: 133, y: 133)
    var returning = false, formed = false, insideWall = true
    var reduced = false
    var ticks = 0, selectionEvents = 0, lightEvents = 0, softEvents = 0, wallEvents = 0
    static let tickThresholds: [Double] = {
        var sum = 0.0
        return (0..<7).map { i in sum += 0.09 - 0.05 * Double(i)/6; return sum/0.9 }
    }()

    init() {}

    init(frame: WaveFieldPadFrame, previous: WaveFieldPadFrame, reduced: Bool) {
        u = Self.unband(frame.u); v = Self.unband(frame.v)
        du = frame.dentU; dv = frame.dentV
        dvu = (frame.dentU - previous.dentU) * 60
        dvv = (frame.dentV - previous.dentV) * 60
        beat = frame.beat; press = frame.press; lock = frame.lock
        settle = Self.inverseSettle(frame.settle)
        still = frame.settle > 0 ? 0.25 : 0
        anchor = CGPoint(x: frame.u * 266, y: frame.v * 266)
        formed = frame.settle > 0.99; ticks = frame.settle > 0 ? 7 : 0
        ring = frame.ring; flash = frame.ring > 0 ? frame.ring * 0.5 : -1
        velocity = frame.velocity
        ripples = frame.ripples.map { r in
            var ripple = r
            let decay = 1-WaveFieldPadFrame.bezier(0.2,0.8,0.2,1,min(r.age/0.6,0.999))
            ripple.amplitude /= max(decay,0.000001)
            return ripple
        }
        time = frame.time; lastTouch = time
        setReduced(reduced)
    }

    static func unband(_ value: Double) -> Double { WaveFieldPadMetrics.unband(value) }
    static func clamp(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func easing(_ x: Double) -> Double { WaveFieldPadFrame.bezier(0.3, 0.35, 0.25, 1, x) }
    static func inverseSettle(_ x: Double) -> Double {
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 { let mid = (lo + hi)/2; if easing(mid) < x { lo = mid } else { hi = mid } }
        return (lo + hi)/2
    }
    mutating func setReduced(_ enabled: Bool) {
        if enabled != reduced { settle = enabled ? Self.easing(settle) : Self.inverseSettle(settle) }
        reduced = enabled
        if enabled {
            ripples = []; ring = 0; flash = -1; blip = -1; press = 0
            if motion != .drag { if motion == .home { u = 0.5; v = 0.5 }; motion = .idle }
            u = Self.clamp(u); v = Self.clamp(v); vu = 0; vv = 0
            du = u; dv = v; dvu = 0; dvv = 0; velocity = .zero
        }
    }
    mutating func addRipple(_ point: CGPoint, amplitude: Double) {
        guard !reduced, amplitude >= 0.3 else { return }
        ripples.removeAll { $0.slot == nextRippleSlot }
        ripples.append(.init(origin: point, age: 0, amplitude: amplitude, slot: nextRippleSlot))
        nextRippleSlot = 1 - nextRippleSlot
    }
    mutating func down(at point: CGPoint) {
        offset = CGPoint(x: point.x - u, y: point.y - v); finger = point
        motion = .drag; returning = false; lastTouch = time
        softEvents += 1
        addRipple(CGPoint(x: 44 + point.x*266, y: 44 + (1-point.y)*266), amplitude: 8)
    }
    mutating func move(to point: CGPoint) { finger = point; lastTouch = time }
    mutating func release(velocity: CGVector) {
        vu = reduced ? 0 : velocity.dx; vv = reduced ? 0 : velocity.dy
        motion = reduced ? .idle : .release; lastTouch = time
        let p = frame.localPuck
        addRipple(p, amplitude: min(6, hypot(vu, vv)*266/250))
    }
    mutating func home() {
        motion = reduced ? .idle : .home; returning = false; lastTouch = time; blip = reduced ? -1 : 0
        addRipple(frame.localPuck, amplitude: 8)
        if reduced { u = 0.5; v = 0.5; du = u; dv = v; vu = 0; vv = 0 }
    }
    mutating func adjust(mix: Double, intensity: Double) {
        u = Self.clamp(u + mix); v = Self.clamp(v + intensity)
        vu = 0; vv = 0; motion = .idle; lastTouch = time; returning = false
        selectionEvents += 1
    }
    /// Four force substeps per display tick, as RN; dt is supplied by the display timeline.
    mutating func step(_ elapsed: Double, autoplayAllowed: Bool) {
        let dt = min(max(elapsed, 0), 0.05)
        guard dt > 0 else { return }
        let old = frame, oldU = old.u, oldV = old.v, oldHz = old.hz
        time += dt
        if autoplayAllowed && motion != .drag && time - lastTouch > 2 && !returning {
            motion = .home; returning = true
        }
        if reduced {
            if motion == .drag { u = finger.x; v = finger.y }
            u = Self.clamp(u); v = Self.clamp(v); vu = 0; vv = 0
        } else if motion == .drag {
            let k = exp(-dt/0.045)
            offset.x *= k; offset.y *= k
            let nu = finger.x - offset.x, nv = finger.y - offset.y
            vu = (vu + (nu-u)/dt)*0.5; vv = (vv + (nv-v)/dt)*0.5
            u = nu; v = nv
        } else if motion == .release || motion == .home {
            for _ in 0..<4 {
                let h = dt/4
                if motion == .home {
                    vu += (-195*(u-0.5)-19*vu)*h; vv += (-195*(v-0.5)-19*vv)*h
                    u += vu*h; v += vv*h
                } else {
                    let x = Self.throwAxis(u, vu, h), y = Self.throwAxis(v, vv, h)
                    u = x.0; vu = x.1; v = y.0; vv = y.1
                }
            }
            if motion == .home && abs(u-0.5)<0.0015 && abs(v-0.5)<0.0015 && abs(vu)<0.01 && abs(vv)<0.01 {
                u = 0.5; v = 0.5; vu = 0; vv = 0; motion = .idle
            } else if motion == .release && u>=0 && u<=1 && v>=0 && v<=1 && abs(vu)*266<8 && abs(vv)*266<8 { motion = .idle }
        }
        let outside = u<0 || u>1 || v<0 || v>1
        if outside && insideWall && motion != .drag {
            var p = frame.localPuck
            if u>1 { p.x = 354 }; if u<0 { p.x = 0 }
            if v>1 { p.y = 0 }; if v<0 { p.y = 354 }
            addRipple(p, amplitude: min(10, 3 + hypot(vu,vv)*266/200)); wallEvents += 1
        }
        insideWall = !outside
        let shown = frame
        for _ in 0..<4 {
            let h = dt/4
            dvu += (-110*(du-shown.u)-11*dvu)*h; dvv += (-110*(dv-shown.v)-11*dvv)*h
            du += dvu*h; dv += dvv*h
        }
        if reduced { du = shown.u; dv = shown.v; dvu = 0; dvv = 0 }
        press += ((motion == .drag && !reduced ? 1.0 : 0)-press)*(1-exp(-dt/0.03))
        if motion == .drag || motion == .release {
            if (oldU-0.5)*(shown.u-0.5)<0 || (oldV-0.5)*(shown.v-0.5)<0 { selectionEvents += 1 }
            if (oldHz-1.5)*(shown.hz-1.5)<0 { lightEvents += 1 }
        }
        let vx = (shown.u-oldU)*266/dt, vy = -(shown.v-oldV)*266/dt
        velocity.dx += (vx-velocity.dx)*0.35; velocity.dy += (vy-velocity.dy)*0.35
        if reduced { velocity = .zero }
        still = hypot(vx,vy)<12 ? still+dt : 0
        let s0 = settle, p = CGPoint(x: shown.u*266, y: shown.v*266)
        if !returning && still>=0.25 {
            anchor = p; settle = min(1,settle+dt/(reduced ? 0.2 : 0.9))
        } else if returning || hypot(p.x-anchor.x,p.y-anchor.y)>6 {
            settle = max(0,settle-dt/(reduced ? 0.2 : 0.3))
            if !returning && ticks>0 && s0>0 && settle<s0 { ticks = 0; lightEvents += 1 }
        }
        if settle>s0 && ticks<7 && settle>=Self.tickThresholds[ticks] { ticks += 1; selectionEvents += 1 }
        if settle>=1 && !formed {
            formed = true; softEvents += 1
            if !reduced { ring = 0.0001; flash = 0 }
        } else if settle<0.5 { formed = false }
        if ring>0 { ring += dt/0.5; if ring>=1 { ring = 0 } }
        if flash>=0 { flash += dt; if flash>0.5 { flash = -1 } }
        lock = formed ? min(1,lock+dt/0.08) : max(0,lock-dt/0.15)
        beat += shown.hz*dt
        for i in ripples.indices { ripples[i].age += dt }
        ripples.removeAll { $0.age>=0.6 }
        if blip>=0 { blip += dt; if blip>0.36 { blip = -1 } }
    }
    static func throwAxis(_ x: Double, _ velocity: Double, _ h: Double) -> (Double,Double) {
        let acceleration = x>1 ? -93.4*(x-1)-15.1*velocity : x<0 ? -93.4*x-15.1*velocity : 0
        var speed = velocity+acceleration*h
        if x>=0 && x<=1 { speed *= pow(0.994,h*1000) }
        return (x+speed*h,speed)
    }
    var frame: WaveFieldPadFrame {
        let shown = WaveFieldPadMetrics.banded(u: u, v: v)
        var result = WaveFieldPadFrame(u: shown.u, v: shown.v, dentU: du, dentV: dv, press: reduced ? 0 : press,
                                      settle: reduced ? settle : Self.easing(settle), ring: reduced ? 0 : ring,
                                      lock: lock, beat: beat, ripple: nil)
        result.ripples = reduced ? [] : ripples.map { r in
            var ripple = r
            ripple.amplitude *= 1-WaveFieldPadFrame.bezier(0.2,0.8,0.2,1,min(r.age/0.6,1))
            return ripple
        }
        result.time = time; result.velocity = velocity; result.reduced = reduced
        let hum: Double = 0.012 * result.settle * sin(2 * Double.pi * 3 * time)
        let pop: Double = blip >= 0 ? 0.08 * sin(Double.pi * blip / 0.36) : 0
        let breath: Double = reduced ? 0 : 0.06 * (1-cos(2 * Double.pi * 6 * time/9)) * (1-press)
        result.scaleOverride = reduced ? 1 : (1 - 0.06 * press + pop + hum)
        result.haloOverride = 0.38 + 0.17 * press + 0.07 * result.settle + breath
        result.flash = flash>=0 && !reduced ? sin(.pi*flash/0.5) : 0
        return result
    }
}
