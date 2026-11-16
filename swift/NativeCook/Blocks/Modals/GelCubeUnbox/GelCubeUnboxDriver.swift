import Foundation

/// Gesture/playback ownership mirrors gcu-reveal.tsx and gcu-playback.ts.
/// Spring steps use Reanimated 4's analytic solution, 64 ms frame cap and 6e-9 energy gate.
@MainActor
final class GelCubeUnboxDriver {
    typealias M = GelCubeUnboxMotion
    typealias Spring = M.GelCubeUnboxSpring

    private struct GelCubeUnboxChannel {
        var value: Double
        var velocity = 0.0, target = 0.0, initialEnergy = 0.0, startValue = 0.0
        var spring: Spring?
        var clampOvershoot = false
        var timingStart: Double?, timingFrom = 0.0, duration = 0.0
        var nextSpringTarget: Double?
        init(_ value: Double) { self.value = value }
        var running: Bool { spring != nil || timingStart != nil }
        mutating func set(_ value: Double) {
            self.value = value; velocity = 0; spring = nil; timingStart = nil; nextSpringTarget = nil
        }
        mutating func animate(_ to: Double, _ config: Spring, velocity supplied: Double = 0,
                              clamp: Bool = false, then: Double? = nil) {
            let previousVelocity = spring != nil ? velocity : 0
            velocity = previousVelocity + supplied
            if (to > value && velocity < 0) || (to < value && velocity > 0) { velocity = 0 }
            target = to; startValue = value - to
            initialEnergy = 0.5 * config.mass * supplied * supplied + 0.5 * config.stiffness * startValue * startValue
            spring = config; timingStart = nil; clampOvershoot = clamp; nextSpringTarget = then
        }
        mutating func timing(_ to: Double, duration: Double, now: Double) {
            timingFrom = value; target = to; timingStart = now; self.duration = duration
            velocity = 0; spring = nil; nextSpringTarget = nil
        }
        mutating func step(_ dt: Double, _ now: Double) {
            if let start = timingStart {
                let k = M.clamp01((now - start) / max(0.00001, duration))
                value = timingFrom + (target - timingFrom) * M.timing(k)
                if k >= 1 { value = target; timingStart = nil }
                return
            }
            guard let config = spring else { return }
            let t = min(max(0, dt), 0.064), w = sqrt(config.stiffness / config.mass)
            let zeta = config.damping / (2 * sqrt(config.stiffness * config.mass))
            let x = value - target, v = velocity
            if zeta < 1 {
                let wd = w * sqrt(1 - zeta * zeta), sn = sin(wd * t), cs = cos(wd * t)
                let envelope = exp(-zeta * w * t)
                let fragment = envelope * (sn * ((v + zeta * w * x) / wd) + x * cs)
                value = target + fragment
                velocity = -zeta * w * fragment + envelope * (cs * (v + zeta * w * x) - wd * x * sn)
            } else {
                let envelope = exp(-w * t)
                value = target + envelope * (x + (v + w * x) * t)
                velocity = envelope * (v * (1 - t * w) - t * x * w * w)
            }
            let left = startValue >= 0 ? target : target + startValue, right = left + abs(startValue)
            let energy = 0.5 * config.mass * velocity * velocity + 0.5 * config.stiffness * pow(target - value, 2)
            if initialEnergy == 0 || energy / initialEnergy <= 6e-9 || (clampOvershoot && (value < left || value > right)) {
                value = target; velocity = 0; spring = nil
                if let next = nextSpringTarget { animate(next, config, clamp: clampOvershoot) }
            }
        }
    }

    private var p: GelCubeUnboxChannel
    private var sealOffset = GelCubeUnboxChannel(0), core = GelCubeUnboxChannel(1), closeLead = GelCubeUnboxChannel(0)
    private var mode = 1, t = 0.0, start = 0.0, epoch: Double?, lastFrame: Double?
    private var auto = false, reduced = false, sound = false, lidWeight = 1.4, controlled: Bool?
    private var resumeAt: Double?, resetting = false, flashUntil = -Double.infinity, lastHaptic = -Double.infinity
    private var dragging = false, sealBroken = false, crossed = false, closing = false
    private var active = true, inactiveAt: Double?, lastCallback = -Double.infinity, lastReported: Double?
    private var lastActivity = false
    private var external = false, lastExternal: Double?, lastPublished: Double?
    private var pendingWrites: [Double] = []
    private let feedback = GelCubeUnboxFeedback()
    private(set) var fingerDown = false
    private(set) var pressed = false
    var onOpenChange: ((Bool) -> Void)?
    var onProgress: ((Double) -> Void)?
    var onActivityChange: (() -> Void)?
    var onExternalProgress: ((Double) -> Void)?

    init(defaultOpen: Bool = false, progress: Double? = nil) {
        p = GelCubeUnboxChannel(progress ?? (defaultOpen ? 1 : 0))
        lastExternal = progress; lastPublished = progress
    }
    var semanticOpen: Bool { p.value + sealOffset.value >= 0.8 }
    var needsFrames: Bool {
        (mode == 0 && auto) || p.running || sealOffset.running || core.running || closeLead.running
            || resumeAt != nil || flashUntil > Date().timeIntervalSinceReferenceDate
    }

    func configure(auto: Bool, reduced: Bool, sound: Bool, lidWeight: Double, open: Bool?,
                   externalProgress: Double?, at date: Date) {
        let now = date.timeIntervalSinceReferenceDate
        external = externalProgress != nil
        if let value = externalProgress, value != lastExternal {
            if let echo = pendingWrites.firstIndex(of: value) { pendingWrites.removeFirst(echo + 1) }
            else { p.set(value); pendingWrites.removeAll() }
            lastExternal = value
        }
        let enabling = auto && !reduced && open == nil && !external
        if self.reduced != reduced {
            epoch = nil; mode = 1; p.set(p.value + sealOffset.value)
            core.set(1); sealOffset.set(0); closeLead.set(0); resumeAt = nil; closing = false
        }
        if enabling && !self.auto && !semanticOpen && !fingerDown { mode = 0; t = 0; epoch = now }
        if !enabling && mode == 0 { mode = 1; epoch = nil }
        self.auto = enabling; self.reduced = reduced; self.sound = sound; self.lidWeight = lidWeight
        if controlled != open {
            controlled = open
            if let open {
                mode = 1; epoch = nil; p.set(p.value)
                if reduced { p.set(open ? 1 : 0) }
                else { p.animate(open ? 1 : 0, weighted(M.lidSpring)) }
            }
        }
    }

    func frame(at date: Date) -> GelCubeUnboxMotion.GelCubeUnboxScene {
        let now = date.timeIntervalSinceReferenceDate
        guard active else { return scene(now) }
        let dt = max(0, now - (lastFrame ?? now)); lastFrame = now
        if mode == 0 {
            if epoch == nil { epoch = now - t }
            t = M.wrap(now - (epoch ?? now)); p.set(M.progressAt(t))
        } else {
            let previous = p.value + sealOffset.value
            if let resume = resumeAt, now >= resume, auto {
                resumeAt = nil; closing = true; closeLead.set(M.fanLead)
                p.timing(0, duration: M.reset, now: resume); resetting = true
            }
            p.step(dt, now); sealOffset.step(dt, now); core.step(dt, now); closeLead.step(dt, now)
            let current = p.value + sealOffset.value
            if closing && previous > M.hairline && current <= M.hairline { flashUntil = now + M.flashDuration }
            if closing && current <= M.contact && previous > M.contact {
                closing = false; cue("close", now: now, throttled: true)
            }
            if resetting && !p.running {
                resetting = false; closing = false; mode = 0; t = 0; epoch = now; p.set(0)
            }
        }
        let current = p.value + sealOffset.value
        if lastReported != current, now - lastCallback >= M.callbackInterval, let report = onProgress {
            lastCallback = now
            lastReported = current
            let value = M.clamp01(current)
            DispatchQueue.main.async { report(value) }
        }
        if external, p.value != lastPublished, let write = onExternalProgress {
            let value = p.value
            lastPublished = value; pendingWrites.append(value)
            // Binding writes must occur after view evaluation; echo tracking keeps an in-flight
            // gesture from being reset by the previous frame's acknowledged host value.
            DispatchQueue.main.async { write(value) }
        }
        let needsFrames = self.needsFrames
        if needsFrames != lastActivity {
            lastActivity = needsFrames
            if let notify = onActivityChange { DispatchQueue.main.async { notify() } }
        }
        return scene(now)
    }
    private func scene(_ now: Double) -> M.GelCubeUnboxScene {
        if mode != 1 { return .film(t, reduced: reduced) }
        let progress = p.value + sealOffset.value
        return M.GelCubeUnboxScene(progress: reduced ? 0 : progress,
                                  core: reduced ? 1 + M.clamp01(progress) * 0.25 : core.value,
                                  fan: reduced ? 0 : max(0, progress - closeLead.value), caustic: core.value,
                                  angle: reduced ? 0 : M.angleAt(progress), flash: reduced ? 0 : now < flashUntil ? M.flashGain : 0,
                                  content: reduced ? M.clamp01(p.value) : M.contentAt(progress),
                                  textProgress: p.value, actionScale: reduced ? 1 : pressed ? 0.97 : 1)
    }
    private func weighted(_ spring: Spring) -> Spring {
        var s = spring; s.mass = lidWeight; return s
    }
    private func takeOver(_ date: Date) {
        _ = frame(at: date)
        if mode != 1 { core.set(M.coreAt(t)) }
        p.set(p.value + sealOffset.value); sealOffset.set(0)
        mode = 1; epoch = nil; resumeAt = nil; resetting = false
    }
    private func afterTouch(_ now: Double) { if auto { resumeAt = now + M.resume } }
    private func cue(_ name: String, now: Double, throttled: Bool = false) {
        guard !throttled || now - lastHaptic >= M.cueInterval else { return }
        lastHaptic = now; feedback.cue(name, sound: sound)
    }

    func begin(at date: Date) {
        takeOver(date)
        start = p.value; dragging = false; closing = false; closeLead.set(0); flashUntil = -.infinity
        sealBroken = start > M.hairline; crossed = start > M.threshold
        core.animate(M.pressCore, M.coreSpring); fingerDown = true
        cue("grab", now: date.timeIntervalSinceReferenceDate)
    }
    func move(dy: Double, at date: Date) {
        guard fingerDown else { return }
        _ = frame(at: date)
        let distance = -dy, now = date.timeIntervalSinceReferenceDate
        dragging = abs(distance) >= M.seal
        if !sealBroken && distance < M.seal && start < M.hairline {
            p.set(M.rubber(start + distance * M.resistance / M.drag)); return
        }
        if !sealBroken && distance >= M.seal {
            sealBroken = true
            sealOffset.set(-M.seal * (1 - M.resistance) / M.drag); sealOffset.animate(0, M.sealSpring)
            cue("seal", now: now, throttled: true)
        }
        let next = M.rubber(start + distance / M.drag)
        p.set(next)
        if !crossed && next >= M.threshold { crossed = true; cue("threshold", now: now, throttled: true) }
    }
    func end(velocityY: Double, at date: Date, cancelled: Bool = false) {
        guard fingerDown else { return }
        _ = frame(at: date); fingerDown = false; core.animate(1, M.coreSpring)
        let now = date.timeIntervalSinceReferenceDate
        if cancelled || !dragging {
            p.animate(controlled.map { $0 ? 1 : 0 } ?? start, M.closeSpring)
            afterTouch(now); return
        }
        let velocity = -velocityY / M.drag, next = p.value + velocity * M.projection > M.threshold
        let target = controlled.map { $0 ? 1.0 : 0.0 } ?? (next ? 1.0 : 0.0)
        closing = target == 0
        closeLead.set(target == 0 ? M.fanLead * max(1, abs(velocity)) : 0)
        closeLead.timing(0, duration: M.reset, now: now)
        if target == 1 && -velocityY > M.flick {
            p.animate(M.overshoot, weighted(M.flickSpring), velocity: velocity, clamp: true, then: 1)
        } else { p.animate(target, weighted(target == 1 ? M.lidSpring : M.closeSpring), velocity: velocity) }
        onOpenChange?(next); afterTouch(now)
    }
    func toggle(at date: Date) {
        takeOver(date)
        let next = p.value < M.threshold, now = date.timeIntervalSinceReferenceDate
        core.set(1); closing = !next; closeLead.set(next ? 0 : M.fanLead)
        cue(next ? "seal" : "grab", now: now)
        let target = controlled.map { $0 ? 1.0 : 0.0 } ?? (next ? 1.0 : 0.0)
        if reduced { p.set(target) } else { p.animate(target, weighted(M.lidSpring)) }
        onOpenChange?(next); afterTouch(now)
    }
    func pressAction(_ down: Bool, at date: Date) {
        if down { takeOver(date) } else { afterTouch(date.timeIntervalSinceReferenceDate) }
        pressed = down
    }
    func action(at date: Date) {
        if !semanticOpen { toggle(at: date) } else { afterTouch(date.timeIntervalSinceReferenceDate) }
    }
    func setActive(_ value: Bool, at date: Date) {
        guard value != active else { return }
        let now = date.timeIntervalSinceReferenceDate
        if value {
            let pause = max(0, now - (inactiveAt ?? now))
            if let e = epoch { epoch = e + pause }
            // RN cancels its idle timeout when the app backgrounds.
            inactiveAt = nil
        } else { _ = frame(at: date); inactiveAt = now; resumeAt = nil }
        active = value; lastFrame = now
    }
}
