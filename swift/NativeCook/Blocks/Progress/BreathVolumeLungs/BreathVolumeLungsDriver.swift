import Observation
import UIKit

@MainActor @Observable
final class BreathVolumeLungsDriver {
    typealias M = BreathVolumeLungsMotion
    var active = false
    @ObservationIgnored private var state = BreathVolumeLungsDriverState()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let success = UINotificationFeedbackGenerator()

    func frame(at date: Date, autoPlay: Bool, reduced: Bool) -> BreathVolumeLungsFrameState {
        let stamp = date.timeIntervalSinceReferenceDate
        if state.epoch == nil { state.epoch = stamp - state.clock }
        if state.fontStart == nil { state.fontStart = stamp }
        let idle = state.phase == 0 && (!autoPlay || reduced)
        let now = idle ? state.clock : stamp - (state.epoch ?? stamp)
        let dt = min(0.05, max(0, now - state.clock))
        let old = M.clamp(state.p), ph = state.phase
        state.clock = now
        synchronizeAdjustment()
        if ph == 0 && autoPlay && !reduced {
            let f = BreathVolumeLungsFrameState.pinned(now.truncatingRemainder(dividingBy: M.loop), reduced: false)
            state.p = f.progress; state.flow = f.flow; state.elapsed = f.elapsed
        } else if ph == 1 || ph == 7 {
            state.p = M.held(state.initial, now - state.anchor)
            state.elapsed += dt
            if ph == 7 && state.p >= 1 { phase(4); state.anchor = now; announce(5.5) }
        } else if ph == 3 {
            let age = now - state.anchor
            state.p = M.coast(state.initial, state.releaseVelocity, age) + state.releaseOver * (1 - M.springAt(age, 0.35, 0.85))
            if age > 0.6 { state.p = M.clamp(state.p); phase(4) }
        } else if ph == 4 && now - state.anchor > 2 {
            startRefill(reduced: reduced)
        } else if ph == 5 {
            let sample = state.refill.sample(now)
            state.p = reduced ? 0 : sample.value
            if sample.done || reduced {
                state.phase = 0; state.clock = 0; state.epoch = nil
                state.elapsed = 0; state.flow = 0; state.p = 0; state.touched = false
                active = false
            }
        }
        if ph == 1 || ph == 3 || ph == 5 || ph == 7 {
            state.flow = dt > 0 ? M.clamp((M.clamp(state.p) - old) / dt * 5.5, -1.13, 1.13) : 0
        } else if ph == 4 { state.flow = 0 }
        if state.touched && floor(M.clamp(state.p) * 5.5) > floor(old * 5.5) {
            state.lastPack = now; cue(1)
        }
        if M.clamp(state.p) >= 0.98 && old < 0.98 { state.airPeak = now }
        var f = BreathVolumeLungsFrameState()
        f.progress = state.p; f.flow = reduced ? 0 : state.flow
        f.time = state.clock.truncatingRemainder(dividingBy: M.loop); f.elapsed = state.elapsed
        f.pressed = reduced ? state.press.target : state.press.sample(now).value
        if state.reboundActive {
            let s = state.rebound.sample(now)
            if s.done && state.rebound.target != 0 { state.rebound.retarget(0, at: now) }
            else if s.done { state.reboundActive = false }
            f.rebound = reduced ? 0 : s.value
        }
        if state.phase == 0 {
            f.result = M.result(state.clock)
            f.lastAir = reduced ? 0 : M.lastAir(state.clock)
            for t in M.crossings where f.time >= t && f.time - t < 0.24 { f.packAge = f.time - t }
        } else {
            f.result = reduced ? state.resultTo : resultAlpha(now)
            f.packAge = now - state.lastPack
            f.lastAir = reduced ? 0 : 1 - M.smooth((now - state.airPeak) / 0.4)
        }
        let fontT = M.clamp((stamp - (state.fontStart ?? stamp)) / 0.18)
        f.fontAlpha = reduced ? 1 : (fontT < 0.5 ? 2 * fontT * fontT : 1 - pow(-2 * fontT + 2, 2) / 2)
        return f
    }
    func begin(scrub: Bool) {
        state.epoch = nil
        state.adjusting = false
        state.initial = scrub ? state.p : M.clamp(state.p); state.anchor = state.clock; state.dragOffset = 0
        state.touched = true; state.scrubRegion = scrub; state.fingerDown = true
        phase(1); state.flow = 0; state.press.retarget(1, at: state.clock); active = true; cue(0)
    }
    func move(translation: Double, velocity: Double, travel: Double) {
        guard state.fingerDown, state.scrubRegion else { return }
        if state.phase == 1 {
            if abs(translation) < 1 { return }
            state.initial = state.p; state.dragOffset = translation; phase(2)
        }
        let old = M.clamp(state.p)
        state.p = M.rubber(state.initial + (translation - state.dragOffset) / travel, travel)
        state.flow = M.clamp(velocity / travel * 5.5, -1.13, 1.13)
        if floor(M.clamp(state.p) * 5.5) > floor(old * 5.5) { state.lastPack = state.clock; cue(1) }
        if M.clamp(state.p) >= 0.98 && old < 0.98 { state.airPeak = state.clock }
    }
    func end(velocity: Double, travel: Double, reduced: Bool) {
        guard state.fingerDown else { return }
        let v = state.phase == 1 ? state.flow / 5.5 : velocity / travel
        let value = M.clamp(state.p)
        state.releaseOver = state.p - value; state.initial = value; state.releaseVelocity = v
        state.anchor = state.clock; state.fingerDown = false
        phase(reduced ? 4 : 3); state.press.retarget(0, at: state.clock)
        if reduced { state.p = value }
        if state.scrubRegion && v < -1.2 && !reduced {
            state.rebound = BreathVolumeLungsSpring(from: 0, target: 0.03, start: state.clock, stiffness: 180, damping: 10)
            state.reboundActive = true
        }
        announce(value * 5.5)
    }
    func cancel(reduced: Bool) {
        guard state.fingerDown else { return }
        if !state.scrubRegion { end(velocity: 0, travel: 1, reduced: reduced); return }
        state.fingerDown = false; phase(4); state.anchor = state.clock
        state.press.retarget(0, at: state.clock)
    }
    func finish() {
        state.epoch = nil
        state.touched = true; phase(4); state.anchor = state.clock; state.flow = 0
        state.press = BreathVolumeLungsSpring(); cue(2); announce(M.clamp(state.p) * 5.5); active = true
    }
    func reset(reduced: Bool) { state.epoch = nil; state.touched = true; cue(0); startRefill(reduced: reduced); active = true }
    func accessibleTest(reduced: Bool) {
        state.epoch = nil
        state.adjusting = false
        state.touched = true; cue(0); state.initial = M.clamp(state.p); state.anchor = state.clock
        if reduced { state.p = 1; phase(4); announce(5.5) } else { phase(7) }
        active = true
    }
    func adjust(_ up: Bool, reduced: Bool) {
        state.epoch = nil
        state.touched = true; phase(4); state.anchor = state.clock
        let target = M.clamp(state.p + (up ? 0.5 : -0.5) / 5.5)
        var velocity = state.adjusting ? state.refill.sample(state.clock).velocity : 0
        if (target > state.p && velocity < 0) || (target < state.p && velocity > 0) { velocity = 0 }
        state.refill = BreathVolumeLungsSpring(from: state.p, target: target, velocity: velocity,
                                             start: state.clock, stiffness: 322.3, damping: 30.5)
        state.adjusting = !reduced
        if reduced { state.p = state.refill.target }
        cue(0); announce(state.refill.target * 5.5); active = true
    }
    private func synchronizeAdjustment() {
        if state.adjusting {
            let sample = state.refill.sample(state.clock); state.p = sample.value
            state.adjusting = !sample.done
        }
    }
    func suspend() { state.epoch = nil }
    private func startRefill(reduced: Bool) {
        state.adjusting = false
        phase(5)
        state.refill = BreathVolumeLungsSpring(from: state.p, target: 0, start: state.clock, stiffness: 157.9, damping: 22.1)
        if reduced { state.p = 0 }
    }
    private func phase(_ value: Int) {
        guard state.phase != value else { return }
        let alpha = resultAlpha(state.clock)
        state.phase = value; state.resultFrom = alpha; state.resultTo = value == 4 ? 1 : 0; state.resultAt = state.clock
        if value == 4 && state.touched { cue(2) }
    }
    private func resultAlpha(_ now: Double) -> Double {
        let t = M.clamp((now - state.resultAt) / 0.2)
        let e = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
        return state.resultFrom + (state.resultTo - state.resultFrom) * e
    }
    private func cue(_ kind: Int) {
        let now = Date.timeIntervalSinceReferenceDate
        guard now - state.lastCue >= 0.1 else { return }
        state.lastCue = now
        if kind == 2 { success.notificationOccurred(.success) }
        else if kind == 1 { medium.impactOccurred() }
        else { light.impactOccurred() }
    }
    private func announce(_ value: Double) { UIAccessibility.post(notification: .announcement, argument: String(format: "%.1f Liter", value)) }
}
