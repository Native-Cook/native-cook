import SwiftUI
import UIKit

/// The RN frame callback and simultaneous pan/tap state, with Reanimated 4's mass-4 touch springs.
final class IslandPrintCameraDriver {
    private let audio=IslandPrintCameraAudio()
    private typealias M = IslandPrintCameraMotion
    private var previous: Date?
    private var now = 0.0, time = 0.0, seq = -1.0, lastTouch = 0.0
    private var auto: Bool
    private var options: (Bool, Bool)?
    private var p = IslandPrintCameraSpring(), depth = IslandPrintCameraSpring(), focus = IslandPrintCameraSpring()
    private var dragX = IslandPrintCameraSpring(), dragY = IslandPrintCameraSpring()
    private var grab = 0, dragStartX = 0.0, dragStartY = 0.0, lastY = 0.0
    private var p0 = 0.0, touchStart: Date?, pan = false, maxDistance = 0.0
    private var tapActive = false
    private(set) var gestureActive = false
    private(set) var flashOn = false
    private(set) var touching = false
    private(set) var pinned = false
    private var pinnedTime: Double?
    var swayTime: Double { auto ? time : now }

    init(autoPlay: Bool) {
        auto = autoPlay
        #if DEBUG
        if let raw = UserDefaults.standard.object(forKey: "islandPrintCameraTime") {
            pinnedTime = (raw as? NSNumber)?.doubleValue ?? (raw as? String).flatMap(Double.init)
            pinned = pinnedTime != nil
        }
        #endif
    }

    func frame(at date: Date, autoPlay: Bool, reduced: Bool) -> IslandPrintCameraMotion.Frame {
        if options == nil || options!.0 != autoPlay || options!.1 != reduced {
            auto = autoPlay && !reduced
            options = (autoPlay, reduced)
        }
        let raw = max(previous.map { date.timeIntervalSince($0) } ?? 0, 0)
        let dt = min(raw, 0.05)
        previous = date
        now += dt
        if tapActive, let touchStart, date.timeIntervalSince(touchStart) > 8 { finalizeTap(reduced: reduced) }
        if pinned, let pinnedTime { time = pinnedTime; auto = true; return M.frameAt(time) }
        let springDt = min(raw, 0.064)
        p.step(springDt); depth.step(springDt); focus.step(springDt); dragX.step(springDt); dragY.step(springDt)
        if auto {
            time = (time+dt).truncatingRemainder(dividingBy: M.loop)
            return M.frameAt(time)
        }
        if seq >= 0 {
            let old=seq
            if !(grab == 2 && seq >= 5.6) { seq += dt }
            if old < 1.35 && seq >= 1.35 { audio.play("motor") }
            if old < 3.41 && seq >= 3.41 { audio.play("land"); buzz(.light) }
            if seq > 6.5 { seq = -1; p.set(0); dragX.set(0); dragY.set(0); lastTouch = now }
        } else if !reduced && autoPlay && !touching && now-lastTouch > 2 {
            if p.value > 0.004 {
                p.animate(to: 0, spring: M.glide, reduced: reduced)
                lastTouch = now-1.4
            } else { time = 0; auto = true }
        }
        if auto { return M.frameAt(time) }
        if seq >= 0 { var f = M.frameAt(3.5+seq); f.dragX = dragX.value; f.dragY = dragY.value; return f }
        return M.liveFrameAt(p.value, depth.value, focus.value)
    }

    private func takeOver() {
        if auto {
            if time > 3.5 && time < 10 { seq = time-3.5 }
            else { p.set(M.frameAt(time).p) }
            auto = false
        }
        pinned = false; touching = true; lastTouch = now
    }
    private func hit(_ q: CGPoint, _ x: Double, _ r: Double) -> Bool {
        pow(q.x-x, 2)+pow(q.y-362, 2) <= r*r
    }
    func change(_ v: DragGesture.Value, scale: Double, reduced: Bool) {
        if !gestureActive {
            takeOver(); touchStart = v.time; pan = false; maxDistance = 0
            gestureActive = true; tapActive = true
            let q = CGPoint(x: v.startLocation.x/scale, y: v.startLocation.y/scale)
            if p.value > 0.9 && seq < 0 && hit(q, 201, 47) {
                depth.animate(to: 0.45, spring: M.snap, reduced: reduced)
                focus.animate(to: 1, spring: M.glide, reduced: reduced)
                buzz(.soft)
            }
        }
        maxDistance = max(maxDistance, hypot(v.translation.width, v.translation.height))
        if tapActive && maxDistance > 12 { finalizeTap(reduced: reduced) }
        if !pan && abs(v.translation.height) > 6 {
            pan = true
            if seq < 0 { p0 = p.value; buzz(.soft) }
            else {
                let q = CGPoint(x: v.location.x/scale, y: v.location.y/scale), f = M.frameAt(3.5+seq)
                let onPrint = q.x > 88+dragX.value && q.x < 314+dragX.value && q.y > f.printTop-10+dragY.value && q.y < f.printTop+257+dragY.value
                if onPrint && seq >= 1.35 && seq < 3.15 { grab = 1 }
                else if onPrint && seq >= 3.41 && seq < 5.7 {
                    grab = 2; dragStartX = dragX.value; dragStartY = dragY.value
                    dragX.set(dragStartX); dragY.set(dragStartY)
                }
                lastY = 0; if grab > 0 { buzz(.soft) }
            }
        }
        if pan && seq >= 0 {
            if grab == 1 {
                let dy = (v.translation.height-lastY)/scale; lastY = v.translation.height
                if dy > 0 { seq = min(seq+0.55*dy/137,3.149) }
            } else if grab == 2 {
                let dy = v.translation.height/scale
                dragX.set(dragStartX+v.translation.width/scale)
                dragY.set(dragStartY+(dy > 0 ? dy : dy/(1-dy/80)))
            }
        }
        if pan && seq < 0 {
            var q = p0+v.translation.height/(346*scale)
            if q > 1 { let over = (q-1)*346; q = 1+over/(1+over/120)/346 }
            p.set(max(q, 0))
        }
    }
    func end(_ v: DragGesture.Value, scale: Double, reduced: Bool) {
        guard gestureActive else { return }
        if pan && seq < 0 {
            let velocity = v.velocity.height/(346*scale)
            let target = p.value+velocity*0.12 >= 0.5 ? 1.0 : 0.0
            p.animate(to: target, spring: M.glide, velocity: velocity, reduced: reduced)
            buzz(target == 1 ? .rigid : .light)
        }
        if pan && seq >= 0 && grab == 2 {
            let vx = v.velocity.width/scale, vy = v.velocity.height/scale
            if vy > 700 || v.translation.height/scale > 140 { seq = max(seq,5.7); buzz(.light) }
            else { dragX.animate(to: 0,spring: M.pop,velocity: vx,reduced: reduced); dragY.animate(to: 0,spring: M.pop,velocity: vy,reduced: reduced) }
        }
        let q = CGPoint(x: v.location.x/scale, y: v.location.y/scale)
        let ok = tapActive && max(maxDistance, hypot(v.translation.width, v.translation.height)) <= 12 && v.time.timeIntervalSince(touchStart ?? v.time) <= 8
        let open = p.value > 0.9 && seq < 0
        if ok && open && hit(q, 201, 53) {
            seq = 0; depth.set(0); focus.set(0); buzz(.medium); audio.play("shutter")
        } else if ok && open && hit(q, 73, 32) {
            flashOn.toggle(); UISelectionFeedbackGenerator().selectionChanged()
        } else if ok && seq < 0 && q.y > 83+max(p.value, 0)*371 && q.y < 143+max(p.value, 0)*371 && abs(q.x-201) < 60 {
            p.animate(to: p.value > 0.5 ? 0 : 1, spring: M.glide, reduced: reduced); buzz(.light)
        }
        if tapActive { finalizeTap(reduced: reduced) }
        grab = 0; gestureActive = false; touching = false; lastTouch = now; touchStart = nil
    }
    func stopAudio() { audio.stop() }
    private func buzz(_ style: UIImpactFeedbackGenerator.FeedbackStyle) { UIImpactFeedbackGenerator(style: style).impactOccurred() }
    func cancel(reduced: Bool) {
        guard gestureActive else { previous = nil; return }
        if pan && seq < 0 { p.animate(to: p0 >= 0.5 ? 1 : 0,spring: M.glide,reduced: reduced) }
        if grab == 2 { dragX.animate(to: 0,spring: M.pop,reduced: reduced); dragY.animate(to: 0,spring: M.pop,reduced: reduced) }
        if tapActive { finalizeTap(reduced: reduced) }
        grab = 0; gestureActive = false; touching = false; lastTouch = now; touchStart = nil
    }
    private func finalizeTap(reduced: Bool) {
        if depth.value > 0 && seq < 0 {
            depth.animate(to: 0, spring: M.pop, reduced: reduced)
            focus.animate(to: 0, spring: M.glide, reduced: reduced)
        }
        tapActive = false; touching = gestureActive; lastTouch = now
    }
}

private struct IslandPrintCameraSpring {
    private(set) var value = 0.0
    private var velocity = 0.0, from = 0.0, target = 0.0, v0 = 0.0, elapsed = 0.0
    private var damping = 22.1, stiffness = 157.9, energy = 0.0
    private var running = false
    mutating func set(_ x: Double) { value = x; velocity = 0; running = false }
    mutating func animate(to: Double, spring: (d: Double, k: Double), velocity added: Double = 0, reduced: Bool) {
        if reduced { set(to); return }
        if running && target == to { return }
        velocity += added
        if velocity*(to-value) < 0 { velocity = 0 }
        from = value; target = to; v0 = velocity; elapsed = 0
        damping = spring.d; stiffness = spring.k; running = true
        energy = 0.5*stiffness*pow(from-to, 2)+2*added*added
    }
    mutating func step(_ dt: Double) {
        guard running else { return }
        elapsed += dt
        value = IslandPrintCameraMotion.springAt(elapsed, from, target, v0, damping/4, stiffness/4)
        let a = damping/8, wd = sqrt(stiffness/4-a*a), x0 = from-target
        let b = (v0+a*x0)/wd
        velocity = exp(-a*elapsed)*((-a*x0+b*wd)*cos(wd*elapsed)+(-a*b-x0*wd)*sin(wd*elapsed))
        let currentEnergy = 0.5*stiffness*pow(value-target, 2)+2*velocity*velocity
        if energy == 0 || currentEnergy/energy <= 6e-9 { set(target) }
    }
}
