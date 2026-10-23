import SwiftUI
import UIKit

/// Event-owned state. Rendering only samples dates; no frame callback mutates observable state.
@MainActor @Observable
final class VitalsChipMorphPlayback {
    typealias M = VitalsChipMorphMotion
    enum Kind { case tap, release, idle, fade }
    struct Transition {
        var lo: Int
        var hi: Int
        var target: Int
        var start: Date
        var from: Double
        var velocity: Double
        var speedFrom: Double
        var kind: Kind
        var duration: Double
    }
    private(set) var auto = false
    private(set) var quiet = false
    private(set) var current = 0
    private(set) var dragging = false
    private(set) var foreground = true
    private var autoAwake = false
    private var autoPlay = false
    private var origin = Date()
    private var fxOrigin = Date()
    private var fxOriginValue = 0.0
    private var settledAt = Date()
    private var lastTouch = Date.distantPast
    private var pausedAt: Date?
    private var transition: Transition?
    private var pending: Int?
    private var restartAutoAt: Date?
    private var held = M.Blend(lo: 0, hi: 0, u: 0, beat: 0, press: 0, pressChip: -1, target: 0)
    private var dragStartState = -1
    private var dragBaseU = 0.0
    private var shift = 0.0
    private var shiftRelease: (Date, Double)?
    private var boundary: Task<Void, Never>?
    private let selection = UISelectionFeedbackGenerator()

    var moving: Bool {
        foreground && ((auto && autoAwake) || transition != nil || (!auto && !quiet && current == 2 && !dragging) || shiftRelease != nil)
    }

    func configure(autoPlay: Bool, quiet: Bool) {
        let wasAuto = auto
        self.autoPlay = autoPlay
        self.quiet = quiet
        if quiet || !autoPlay {
            auto = false
            restartAutoAt = nil
            if wasAuto { settle(0, at: Date()) }
        } else if transition == nil && !dragging && current == 0 {
            auto = true
            origin = Date()
        }
        scheduleBoundary()
    }

    func sample(at date: Date) -> M.Blend {
        let now = pausedAt ?? date
        if auto {
            let t = max(0, now.timeIntervalSince(origin))
            return M.loopBlend(t.truncatingRemainder(dividingBy: M.LOOP))
        }
        if let a = transition, !dragging {
            let tau = max(0, now.timeIntervalSince(a.start))
            let x: Double
            if a.kind == .fade { x = M.clamp01(a.from + tau / M.SPRINGS.reducedFade) }
            else {
                let sp = springFor(a.kind)
                x = M.clamp01(M.spring(tau, a.from, a.velocity, sp.omega, sp.zeta))
            }
            return M.Blend(
                lo: a.lo,
                hi: a.hi,
                u: a.target == a.hi ? x : 1 - x,
                beat: 0,
                press: 0,
                pressChip: -1,
                target: a.target,
                fxTime: effectTime(at: now),
                speed: quiet ? 0 : filteredSpeed(for: a, tau: tau),
                beatCount: -1
            )
        }
        var result = held
        result.fxTime = effectTime(at: now)
        result.speed = quiet ? 0 : result.speed
        result.beatCount = -1
        if !dragging && result.lo == result.hi && result.lo == 2 && !quiet {
            let t = max(0, now.timeIntervalSince(settledAt))
            result.beat = M.pulse((t / M.BEAT).truncatingRemainder(dividingBy: 1)) * M.clamp01(t / 0.3)
            result.beatCount = t / M.BEAT
        }
        return result
    }

    private func effectTime(at date: Date) -> Double {
        fxOriginValue + max(0, date.timeIntervalSince(fxOrigin))
    }

    /// Reanimated smooths measured shape speed once per display frame. Rebuild the
    /// same first-order filter from the transition event, keeping rendering pure.
    private func filteredSpeed(for transition: Transition, tau: Double) -> Double {
        guard transition.kind != .fade else { return 0 }
        let spring = springFor(transition.kind)
        let hz = 60.0
        let wholeSteps = min(Int(floor(tau * hz)), Int(transition.duration * hz) + 1)
        var filtered = transition.speedFrom
        if wholeSteps > 0 {
            for step in 1...wholeSteps {
                let time = Double(step) / hz
                let toward = M.springVelocity(time, transition.from, transition.velocity, spring.omega, spring.zeta)
                let raw = transition.target == transition.hi ? toward : -toward
                filtered += (raw - filtered) * M.LIQUID.speedSmoothing
            }
        }
        let partial = min(max(tau * hz - Double(wholeSteps), 0), 1)
        guard partial > 0 else { return filtered }
        let toward = M.springVelocity(tau, transition.from, transition.velocity, spring.omega, spring.zeta)
        let raw = transition.target == transition.hi ? toward : -toward
        let amount = 1 - pow(1 - M.LIQUID.speedSmoothing, partial)
        return filtered + (raw - filtered) * amount
    }

    func heroShift(at date: Date) -> Double {
        guard let (start, from) = shiftRelease else { return shift }
        // Reanimated duration spring: 400 ms perceptual duration, ζ=.8.
        return from * (1 - M.spring(max(0, date.timeIntervalSince(start)), 0, 0, Self.rubberOmega, 0.8))
    }

    private static let rubberOmega: Double = {
        // springUtils.calculateNewStiffnessToMatchDuration: default mass 4,
        // energyThreshold 6e-9, targetDuration × 1.5.
        var lo = Double.ulpOfOne, hi = 8000.0
        let zeta = 0.8, duration = 0.4 * 1.5, threshold = 6e-9, mass = 4.0
        for _ in 0..<100 {
            let mid = (lo + hi) / 2
            let omega = sqrt(mid / mass) * zeta
            let x = (1 + omega * duration) * exp(-omega * duration)
            let v = -x * omega + omega * exp(-omega * duration)
            let fraction = (mid * x * x + mass * v * v) / mid
            if abs(fraction - threshold) <= threshold * 1e-3 { return sqrt(mid / mass) }
            if fraction > threshold { lo = mid } else { hi = mid }
        }
        return sqrt((lo + hi) / 2 / mass)
    }()

    func select(chip: Int, at date: Date = Date()) {
        touch(at: date)
        guard VitalsChipMorphTheme.CHIP_SCREEN.indices.contains(chip) else {
            scheduleBoundary(); return
        }
        let screen = VitalsChipMorphTheme.CHIP_SCREEN[chip]
        let b = sample(at: date)
        if (b.lo == b.hi ? b.lo : b.target) != screen { selection.selectionChanged() }
        goTo(screen, kind: .tap, velocity: 0, at: date)
        scheduleBoundary()
    }

    func adjust(_ direction: AccessibilityAdjustmentDirection) {
        let step = direction == .increment ? 1 : -1
        select(chip: VitalsChipMorphTheme.SCREEN_CHIP[min(max(current + step, 0), 2)])
    }

    private func touch(at date: Date) {
        lastTouch = date
        restartAutoAt = nil
        boundary?.cancel()
        if auto {
            let b = sample(at: date)
            fxOriginValue = b.fxTime
            fxOrigin = date
            auto = false
            held = b
            held.beat = 0
            held.press = 0
            if b.lo != b.hi && (b.u < 0.001 || b.u > 0.999) {
                settle(b.u < 0.001 ? b.lo : b.hi, at: date)
            } else if b.lo != b.hi {
                beginTransition(lo: b.lo, hi: b.hi, to: b.target,
                                from: b.target == b.hi ? b.u : 1 - b.u, velocity: 0, kind: .tap, at: date)
            }
        }
    }

    private func goTo(_ to: Int, kind: Kind, velocity: Double, at date: Date) {
        let b = sample(at: date)
        var v0 = velocity
        if b.lo == b.hi {
            guard to != b.lo else { return }
            beginTransition(lo: min(b.lo, to), hi: max(b.lo, to), to: to,
                            from: 0, velocity: v0, speedFrom: b.speed, kind: kind, at: date)
        } else if to == b.lo || to == b.hi {
            if let a = transition, !dragging {
                let sp = springFor(a.kind)
                let v = M.springVelocity(date.timeIntervalSince(a.start), a.from, a.velocity, sp.omega, sp.zeta)
                v0 = a.target == to ? v : -v
            }
            beginTransition(lo: b.lo, hi: b.hi, to: to, from: to == b.hi ? b.u : 1 - b.u,
                            velocity: v0, speedFrom: b.speed, kind: kind, at: date)
        } else { pending = to }
    }

    private func beginTransition(
        lo: Int,
        hi: Int,
        to: Int,
        from: Double,
        velocity: Double,
        speedFrom: Double? = nil,
        kind: Kind,
        at date: Date
    ) {
        let actualKind: Kind = quiet ? .fade : kind
        let sp = springFor(actualKind)
        var duration = max(0, (1 - from) * M.SPRINGS.reducedFade)
        if actualKind != .fade {
            // Find the first frame satisfying RN's settle predicate. This is evaluated
            // once per event, never in the timeline's drawing closure.
            duration = 0.1 + 1 / 240.0
            while duration < 12 {
                let x = M.spring(duration, from, velocity, sp.omega, sp.zeta)
                let v = M.springVelocity(duration, from, velocity, sp.omega, sp.zeta)
                if abs(1 - x) < 0.0015 && abs(v) < 0.03 { break }
                duration += 1 / 240.0
            }
        }
        transition = Transition(lo: lo, hi: hi, target: to, start: date,
                                from: from, velocity: velocity, speedFrom: speedFrom ?? held.speed,
                                kind: actualKind, duration: duration)
        pending = nil
    }

    private func settle(_ screen: Int, at date: Date) {
        transition = nil
        current = screen
        settledAt = date
        held = M.Blend(lo: screen, hi: screen, u: 0, beat: 0, press: 0, pressChip: -1, target: screen)
    }

    func beginDrag(at date: Date = Date()) {
        touch(at: date)
        held = sample(at: date)
        transition = nil
        dragging = true
        dragStartState = held.lo == held.hi ? held.lo : -1
        dragBaseU = held.u
    }

    func updateDrag(translation: Double, velocity: Double, k: Double) {
        let d = translation / (M.DRAG.span * k)
        let progressVelocity = -velocity / (M.DRAG.span * k)
        if dragStartState >= 0 {
            shiftRelease = nil
            let start = dragStartState
            let next = d < 0 ? start + 1 : start - 1
            if next < 0 || next > 2 {
                held = M.Blend(lo: start, hi: start, u: 0, beat: 0, press: 0, pressChip: -1, target: start)
                shift = translation * M.DRAG.rubber / (1 + abs(translation) / (M.DRAG.rubberFalloff * k))
                return
            }
            shift = 0
            let p = min(abs(d), 1)
            held = M.Blend(lo: min(start, next), hi: max(start, next), u: next > start ? p : 1 - p,
                          beat: 0, press: 0, pressChip: -1, target: next)
        } else { held.u = M.clamp01(dragBaseU - d) }
        held.speed += (progressVelocity - held.speed) * M.LIQUID.speedSmoothing
    }

    func endDrag(velocity: Double, k: Double, at date: Date = Date()) {
        dragging = false
        lastTouch = date
        if shift != 0 {
            if !quiet { shiftRelease = (date, shift) }
            shift = 0
        }
        if held.lo != held.hi {
            let vu = -velocity / (M.DRAG.span * k)
            let forward = vu > M.DRAG.commitVelocity || (held.u > M.DRAG.commitDistance && vu > -M.DRAG.commitVelocity)
            let to = forward ? held.hi : held.lo
            if dragStartState >= 0 && to != dragStartState { selection.selectionChanged() }
            beginTransition(lo: held.lo, hi: held.hi, to: to, from: to == held.hi ? held.u : 1 - held.u,
                            velocity: to == held.hi ? vu : -vu, kind: .release, at: date)
        }
        scheduleBoundary()
    }

    func setForeground(_ active: Bool) {
        guard foreground != active else { return }
        foreground = active
        if !active { pausedAt = Date(); boundary?.cancel() }
        else if let pause = pausedAt {
            let delta = Date().timeIntervalSince(pause)
            origin = origin.addingTimeInterval(delta)
            fxOrigin = fxOrigin.addingTimeInterval(delta)
            settledAt = settledAt.addingTimeInterval(delta)
            lastTouch = lastTouch.addingTimeInterval(delta)
            if transition != nil { transition?.start = transition!.start.addingTimeInterval(delta) }
            if let (start, value) = shiftRelease { shiftRelease = (start.addingTimeInterval(delta), value) }
            if let restartAutoAt { self.restartAutoAt = restartAutoAt.addingTimeInterval(delta) }
            pausedAt = nil
            scheduleBoundary()
        }
    }

    func stop() { boundary?.cancel(); boundary = nil }

    private func springFor(_ kind: Kind) -> (omega: Double, zeta: Double) {
        switch kind {
        case .release: (M.SPRINGS.release.omega, M.SPRINGS.release.zeta)
        case .idle: (M.SPRINGS.idle.omega, M.SPRINGS.idle.zeta)
        default: (M.SPRINGS.tap.omega, M.SPRINGS.tap.zeta)
        }
    }

    private func scheduleBoundary() {
        boundary?.cancel()
        guard foreground, !dragging else { return }
        let now = Date()
        if auto {
            let t = max(0, now.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.LOOP)
            autoAwake = t >= M.T.pressFitness && t < M.T.restAt
            let delay: Double
            if t < M.T.pressFitness { delay = M.T.pressFitness - t }
            else if t < M.T.restAt { delay = M.T.restAt - t }
            else { delay = M.LOOP - t + M.T.pressFitness }
            boundary = Task { [weak self] in
                try? await Task.sleep(for: .seconds(delay))
                guard !Task.isCancelled else { return }
                self?.scheduleBoundary()
            }
            return
        }
        var next: Date?
        if let a = transition { next = a.start.addingTimeInterval(a.duration) }
        if let (start, _) = shiftRelease {
            let end = start.addingTimeInterval(0.6)
            next = next.map { min($0, end) } ?? end
        }
        if let restartAutoAt { next = next.map { min($0, restartAutoAt) } ?? restartAutoAt }
        if restartAutoAt == nil && transition == nil && autoPlay && !quiet {
            let idle = max(now, lastTouch.addingTimeInterval(M.RESUME_AFTER))
            next = next.map { min($0, idle) } ?? idle
        }
        guard let next else { return }
        boundary = Task { [weak self] in
            try? await Task.sleep(for: .seconds(max(0, next.timeIntervalSinceNow)))
            guard !Task.isCancelled, let self else { return }
            self.advanceBoundary(at: Date())
        }
    }

    private func advanceBoundary(at now: Date) {
        if let (start, _) = shiftRelease, now.timeIntervalSince(start) >= 0.6 { shiftRelease = nil }
        if let restart = restartAutoAt, now >= restart {
            restartAutoAt = nil
            auto = true
            origin = now.addingTimeInterval(-M.T.pressFitness)
            autoAwake = true
            scheduleBoundary()
            return
        }
        if let a = transition, now.timeIntervalSince(a.start) >= a.duration {
            let next = pending
            settle(a.target, at: now)
            pending = nil
            if let next, next != a.target { goTo(next, kind: .tap, velocity: 0, at: now) }
        }
        if transition == nil && autoPlay && !quiet && now.timeIntervalSince(lastTouch) >= M.RESUME_AFTER {
            if current != 0 { goTo(0, kind: .idle, velocity: 0, at: now) }
            else if restartAutoAt == nil {
                // Keep the reference rest frame in the static manual state, then
                // enter autoplay at the first moving beat. This avoids freezing
                // the preceding frame when TimelineView pauses during hand-off.
                restartAutoAt = now.addingTimeInterval(M.T.pressFitness)
            }
        }
        scheduleBoundary()
    }
}
