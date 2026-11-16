import Foundation
import UIKit

// Live values use the same timing curves and physical spring parameters as RN.
// The autoplay path is independent and remains a pure function of pinned time.
@MainActor
final class WovenSpeakerPlayerDriver {
    typealias M = WovenSpeakerPlayerMotion
    typealias Look = WovenSpeakerPlayerLook
    private enum Curve { case timing, out, inOut, spring(Double,Double,Double) }
    private struct Animation {
        var from: Double, to: Double, start: Double, duration: Double, curve: Curve, velocity: Double = 0
        func value(_ now: Double) -> Double {
            let t = now-start; if t <= 0 { return from }
            switch curve {
            case .spring(let c,let k,let mass):
                let w = sqrt(k/mass), z = c/(2*sqrt(k*mass)), x = from-to
                let result: Double
                if z < 1 {
                    let wd = w*sqrt(1-z*z)
                    result = to+exp(-z*w*t)*(x*cos(wd*t)+(velocity+z*w*x)/wd*sin(wd*t))
                } else if abs(z-1) < 1e-6 {
                    result = to+exp(-w*t)*(x+(velocity+w*x)*t)
                } else {
                    let r1 = -w*(z-sqrt(z*z-1)), r2 = -w*(z+sqrt(z*z-1))
                    let a = (velocity-r2*x)/(r1-r2), b = x-a
                    result = to+a*exp(r1*t)+b*exp(r2*t)
                }
                return abs(result-to) < 1e-7 && t > 1 ? to : result
            default:
                let f = M.clamp(t/duration)
                let e: Double
                switch curve {
                case .out: e = M.out(f)
                case .inOut: e = M.inOut(f)
                default: e = f < 0.5 ? 2*f*f : 1-pow(-2*f+2,2)/2
                }
                return M.mix(from,to,e)
            }
        }
    }
    private struct Scalar {
        var current: Double
        var animation: Animation?
        var pending: Animation?
        mutating func sample(_ now: Double) -> Double {
            if let p = pending, now >= p.start {
                var next = p; next.from = animation?.value(p.start) ?? current
                animation = next; pending = nil
            }
            current = animation?.value(now) ?? current; return current
        }
        mutating func set(_ x: Double) { current = x; animation = nil; pending = nil }
        mutating func animate(_ x: Double, _ now: Double, _ duration: Double, _ curve: Curve, _ delay: Double = 0) {
            let start = sample(now)
            let a = Animation(from: start,to: x,start: now+delay,duration: duration,curve: curve)
            if delay > 0 { pending = a } else { animation = a; pending = nil }
        }
    }
    private let paths: [WritableKeyPath<Look,Double>] = [\.pos,\.glyph,\.press,\.bloom,\.lamp,\.title,\.line,\.pill,\.refl,\.handle,\.turn,\.psiO,\.psiI,\.dent,\.pull,\.head]
    private var values: [WritableKeyPath<Look,Double>: Scalar] = [:]
    private var slideValue = Scalar(current: 0), pillValue = Scalar(current: 1)
    private var previous: Double?, epoch: Double?, elapsed = 0.0
    private var now = 0.0, touchedAt = -1e9, snapUntil = 0.0, resumeAt: Double?
    private var live = false, enabled = false, reduced = false
    private var playing = false, byTouchPlay = false, playClock = 0.0, nextBeat = 0.12, beatN = 0
    private var coastV = 0.0, coasting = false, x = 0.0
    private var slots = Array(repeating: (-9.0,0.0),count: 4), strongAt = -9.0, pauseAt = -1.0
    private var region = 0, sub = 0, angle = 0.0, acc = 0.0, velocity = 0.0, velocityAt = 0.0
    private var tickAt = 0.0, tickN = 0, atLimit = false, turning = false
    private var finger = 0.0, dentX = 0.0, dentY = 0.0, pullAt = 0.0
    private(set) var fingerDown = false
    private(set) var look = Look()
    private(set) var index = 0
    private(set) var slide = 0.0
    private(set) var pillPress = 1.0
    var onOpenQueue: (() -> Void)?
    var reducedFramesNeeded: Bool {
        if playing || fingerDown || coasting { return true }
        let channels = Array(values.values) + [slideValue,pillValue]
        return channels.contains { channel in
            if channel.pending != nil { return true }
            guard let a = channel.animation else { return false }
            switch a.curve {
            case .spring: return channel.current != a.to
            default: return now < a.start+a.duration
            }
        }
    }

    init() {
        for p in paths { values[p] = Scalar(current: look[keyPath: p]) }
        set(\.bloom,0); set(\.lamp,0)
    }
    private func get(_ p: WritableKeyPath<Look,Double>) -> Double { values[p]?.current ?? 0 }
    private func set(_ p: WritableKeyPath<Look,Double>, _ v: Double) { values[p]?.set(v) }
    private func animate(_ p: WritableKeyPath<Look,Double>, _ v: Double, _ duration: Double, _ curve: Curve, _ delay: Double = 0) {
        values[p]?.animate(v,now,duration,curve,delay)
    }
    private func spring(_ p: WritableKeyPath<Look,Double>, _ v: Double, _ c: Double, _ k: Double, _ mass: Double = 1) {
        animate(p,v,0,.spring(c,k,mass))
    }
    private func impact(_ style: UIImpactFeedbackGenerator.FeedbackStyle) { UIImpactFeedbackGenerator(style: style).impactOccurred() }
    func reset() { live = false; epoch = nil; previous = nil; elapsed = 0; fingerDown = false; resumeAt = nil }
    func suspend() { previous = nil }
    func frame(at date: Date, autoPlay: Bool, reduce: Bool, pin: Double?) -> Look {
        now = date.timeIntervalSinceReferenceDate; enabled = autoPlay; reduced = reduce
        let dt = min(max(0,now-(previous ?? now)),0.05); previous = now
        if let pin { look = M.look(pin); return look }
        if autoPlay && !reduce && !live {
            if epoch == nil { epoch = now }; elapsed += dt
            look = M.look(elapsed); return look
        }
        if !live && reduce { look = Look(); return look }
        let old = look
        for p in paths { _ = values[p]?.sample(now) }
        slide = slideValue.sample(now); pillPress = pillValue.sample(now)
        if coasting {
            coastV *= pow(0.996,dt*1000)
            let hit = turnBy(coastV*dt,false)
            if abs(coastV) < 0.05 || hit {
                coasting = false
                if hit { x = min(max(x,0),200); impact(.rigid); limitPulse() }
                snap()
            }
        }
        let busy = fingerDown || coasting || now < snapUntil
        if playing && !busy {
            let p = get(\.pos)+dt
            if p >= 200 { set(\.pos,200); x = 200; setPlaying(false,false); impact(.rigid) }
            else { set(\.pos,p); x = p }
        }
        if playing && !busy {
            playClock += dt
            while playClock >= nextBeat {
                let strong = beatN%4 == 0; launch(strong ? 4 : 2.2)
                if strong { strongAt = now }
                if beatN == 0 && byTouchPlay { impact(.medium) }
                beatN += 1; nextBeat += M.beat
            }
        }
        if pauseAt > 0 && now >= pauseAt { pauseAt = -1; impact(.soft) }
        var k = Look()
        for p in paths { k[keyPath: p] = get(p) }
        let delta = reduced ? 0 : k.psiI // this live scalar stores shearDL, not the inner layer's angle
        k.psiI = k.psiO-delta; k.shear = max(-1,min(1,delta/0.25))
        k.omO = abs(k.psiO-old.psiO)/max(dt,0.001)/60
        k.omI = abs(k.psiI-old.psiI)/max(dt,0.001)/60
        k.bloomR = M.mix(60,84,k.bloom); k.bloom = M.mix(0.65,1,k.bloom)
        k.reach = M.mix(18,46,k.lamp); k.lamp = M.mix(0.12,1,k.lamp)
        k.finger = finger; k.dentX = dentX; k.dentY = dentY; k.pullAt = pullAt
        if !reduce {
            for i in slots.indices { let (r,a) = M.ring(now,slots[i].0,slots[i].1); if a > 0 { k.ring(i,r,a) } }
            k.pulse = M.pulse(now,strongAt)
        }
        k.wash = get(\.bloom)*(0.05+0.03*min(k.pulse,1)); look = k
        if enabled && !reduce && !busy && now-touchedAt > 2 && touchedAt > -1e8 {
            if resumeAt == nil { rest() }
            else if abs(get(\.pos)) < 0.02 && abs(get(\.psiO)) < 0.002 && abs(get(\.psiI)) < 0.002 && abs(get(\.glyph)) < 0.01 && abs(get(\.bloom)) < 0.01 && abs(get(\.dent)) < 0.05 {
                live = false; resumeAt = nil; touchedAt = -1e9; epoch = now; elapsed = 0
            }
        }
        return look
    }
    private func takeOver(_ date: Date) {
        now = date.timeIntervalSinceReferenceDate
        resumeAt = nil
        if live { return }; live = true
        let k = look; playing = k.glyph > 0.5; x = k.pos
        for p in paths { set(p,k[keyPath: p]) }
        set(\.bloom,(k.bloom-0.65)/0.35); set(\.lamp,(k.lamp-0.12)/0.88)
        set(\.psiI,k.psiO-k.psiI)
        finger = k.finger; dentX = k.dentX; dentY = k.dentY; pullAt = k.pullAt
        slots = Array(repeating: (-9,0),count: 4); playClock = 0; nextBeat = 0.12; beatN = 0
    }
    private func setPlaying(_ next: Bool, _ touch: Bool) {
        if playing == next { return }; playing = next; byTouchPlay = touch && next
        if reduced {
            for p in [\Look.glyph,\.bloom,\.lamp,\.title,\.line,\.pill,\.refl,\.handle] { animate(p,next ? 1 : 0,0.2,.timing) }
        } else if next {
            animate(\.glyph,1,0.26,.inOut); animate(\.bloom,1,0.3,.out,0.06); animate(\.lamp,1,0.22,.out,0.12)
            animate(\.title,1,0.24,.inOut,0.16); animate(\.line,1,0.24,.inOut,0.2); animate(\.pill,1,0.24,.inOut,0.24)
            animate(\.refl,1,0.2,.out,0.33); animate(\.handle,1,0.25,.out,0.8); press()
        } else {
            animate(\.glyph,0,0.22,.inOut); animate(\.pill,0,0.24,.inOut); animate(\.line,0,0.24,.inOut,0.04); animate(\.title,0,0.24,.inOut,0.08)
            animate(\.bloom,0,0.4,.out); animate(\.lamp,0,0.4,.out); animate(\.refl,0,0.2,.out); animate(\.handle,0,0.3,.out); press(); pauseAt = now+0.55
        }
        if next { playClock = 0; nextBeat = 0.12; beatN = 0 }
    }
    private func press() {
        animate(\.press,0.9,0.08,.out)
        values[\.press]?.pending = Animation(from: 0.9,to: 1,start: now+0.08,duration: 0,curve: .spring(30.5,322.3,1))
    }
    private func launch(_ amp: Double) {
        let oldest = slots.indices.min { slots[$0].0 < slots[$1].0 } ?? 0; slots[oldest] = (now,amp)
    }
    private func turnBy(_ a: Double, _ shear: Bool) -> Bool {
        let dx = a/(2 * .pi)*200, x0 = x, x1 = x0+dx
        let p0 = min(max(x0,0),200), p1 = min(max(x1,0),200), inside = p1-p0
        let effective = (inside+0.2*(dx-inside))/200*2 * .pi
        x = x1; set(\.pos,p1); set(\.psiO,get(\.psiO)+0.22*effective)
        if shear && !reduced { set(\.psiI,get(\.psiI)+0.4*0.22*effective) }
        return x1 < 0 || x1 > 200
    }
    private func snap() {
        let target = min(max(get(\.pos),0),200).rounded(); x = target; snapUntil = now+0.45
        spring(\.pos,target,20,260); impact(.light)
    }
    private func limitPulse() {
        animate(\.head,1,0.12,.timing)
        values[\.head]?.pending = Animation(from: 1,to: 0,start: now+0.12,duration: 0.26,curve: .timing)
    }
    private func rest() {
        resumeAt = now; playing = false; x = 0
        for p in paths { animate(p,p == \Look.press ? 1 : 0,0.6,.inOut) }
        slideValue.animate(0,now,0.6,.inOut); index = 0
    }
    func begin(_ p: CGPoint, at date: Date) {
        let r = hypot(p.x,p.y)
        region = r < 104 ? 2 : r <= 170 ? 1 : 3; sub = 0
        if region == 3 { return }
        takeOver(date); fingerDown = true; touchedAt = now; coasting = false; turning = false
        angle = atan2(p.y,p.x); acc = 0; velocity = 0; velocityAt = now; tickAt = now; tickN = Int(floor(get(\.pos)/5)); atLimit = false
        if region == 1 {
            x = get(\.pos); let ratio = r > 0.001 ? min(r,159)/r : 1
            dentX = p.x*ratio; dentY = p.y*ratio; finger = angle
            animate(\.dent,5,0.08,.out); impact(.light)
        } else if abs(p.y) < 34 && abs(p.x) < 34 { sub = 1; spring(\.press,0.9,30.5,322.3) }
        else if abs(p.y) < 22 && abs(p.x+67) < 22 { sub = 2 }
        else if abs(p.y) < 22 && abs(p.x-67) < 22 { sub = 3 }
    }
    func move(_ p: CGPoint, translation: CGSize, at date: Date) {
        guard fingerDown && region == 1 else { return }; now = date.timeIntervalSinceReferenceDate
        let r = hypot(p.x,p.y), a = atan2(p.y,p.x), d = M.wrapPi(a-angle)
        angle = a; acc += d
        let ratio = r > 0.001 ? min(max(r,104),159)/r : 1
        dentX = p.x*ratio; dentY = p.y*ratio; finger = a
        velocity = velocity*0.6+0.4*d/max(now-velocityAt,0.004); velocityAt = now; touchedAt = now
        if !turning && hypot(translation.width,translation.height) > 6 {
            turning = true; spring(\.turn,1,18,240); atLimit = turnBy(acc,true)
        } else if turning {
            let hit = turnBy(d,true); if hit && !atLimit { impact(.rigid); limitPulse() }; atLimit = hit
            let n = Int(floor(get(\.pos)/5))
            if n != tickN && now-tickAt >= 0.1 { tickAt = now; UISelectionFeedbackGenerator().selectionChanged() }; tickN = n
        }
    }
    func end(_ translation: CGSize, at date: Date, cancelled: Bool = false) {
        guard fingerDown else { return }; now = date.timeIntervalSinceReferenceDate; fingerDown = false; touchedAt = now
        if region == 1 {
            spring(\.dent,0,9,150)
            if turning {
                animate(\.turn,0,0.4,.inOut); spring(\.psiI,0,14,60,1.4)
                pullAt = finger; set(\.pull,0.8); animate(\.pull,0,1.2,.out,0.06)
                let vMax = 1.5*2 * .pi*(-log(0.996)*1000)
                let v = now-velocityAt < 0.24 ? max(-abs(vMax),min(abs(vMax),velocity)) : 0
                if abs(v) > 0.6 && x >= 0 && x <= 200 { coastV = v; coasting = true }
                else { x = min(max(x,0),200); snap() }
            }
        } else {
            spring(\.press,1,30.5,322.3)
            if !cancelled && hypot(translation.width,translation.height) < 6 {
                if sub == 1 { setPlaying(!playing,true) }
                else if sub == 2 { commitTrack(-1,false) }
                else if sub == 3 { commitTrack(1,false) }
            }
        }; region = 0
        // RN onFinalize clears NO_GRIP after setPlaying; keep that first-note flag lifetime.
        byTouchPlay = false
    }
    private func commitTrack(_ dir: Int, _ swipe: Bool) {
        let j = index+dir
        if j < 0 || j >= 3 {
            slideValue.animate(Double(-dir)*14,now,0.09,.timing)
            slideValue.pending = Animation(from: Double(-dir)*14,to: 0,start: now+0.09,duration: 0,curve: .spring(22.1,157.9,1)); return
        }
        index = j; let currentSlide = slideValue.sample(now); slideValue.set(currentSlide+Double(dir)*140)
        slideValue.animate(0,now,0,swipe ? .spring(22.1,157.9,1) : .spring(30.5,322.3,1))
        launch(1.6); impact(.light)
    }
    func beginTitle(at date: Date) { takeOver(date); fingerDown = true; touchedAt = now }
    func moveTitle(_ translation: Double, at date: Date) {
        guard playing else { return }; now = date.timeIntervalSinceReferenceDate
        var s = translation; if (index == 0 && s > 0) || (index == 2 && s < 0) { s *= 0.3 }; slideValue.set(s)
    }
    func endTitle(_ velocity: Double, at date: Date) {
        now = date.timeIntervalSinceReferenceDate; fingerDown = false; touchedAt = now; guard playing else { return }
        let s = slideValue.current
        if s < -56 || velocity < -520 { commitTrack(1,true) }
        else if s > 56 || velocity > 520 { commitTrack(-1,true) }
        else { slideValue.animate(0,now,0,.spring(22.1,157.9,1)) }
    }
    func pill(_ down: Bool, success: Bool = false, at date: Date) {
        now = date.timeIntervalSinceReferenceDate
        pillValue.animate(down ? 0.97 : 1,now,0,.spring(30.5,322.3,1))
        if !down && success && playing { onOpenQueue?() }
    }
    func toggle(at date: Date) { takeOver(date); setPlaying(!playing,false) }
    func skip(_ dir: Int, at date: Date) { takeOver(date); commitTrack(dir,false) }
    func seek(_ seconds: Double, at date: Date) {
        takeOver(date); let target = min(max((get(\.pos)+seconds).rounded(),0),200); x = target; animate(\.pos,target,0.2,.timing)
    }
}
