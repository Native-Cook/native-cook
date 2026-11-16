import Foundation
import UIKit
import QuartzCore

/// Same gesture history filters, hysteresis and interruptible physical springs as the RN engine.
@MainActor
final class NeonContourMoodDriver {
    typealias M = NeonContourMoodMotion
    struct Spring {
        var from = 0.0
        var target = 0.0
        var velocity = 0.0
        var start = 0.0
        var stiffness = 220.0
        var damping = 18.0
        var initialEnergy = 0.0
        mutating func sample(_ now: Double) -> (value: Double, velocity: Double) {
            // Reanimated advances at most 64 ms on a delayed display frame.
            let t = min(0.064, max(0, now - start)), a = from - target, z = damping / 2
            start = now
            let w = sqrt(max(1e-9, stiffness - z * z))
            let b = (velocity + z * a) / w, decay = exp(-z * t)
            let c = cos(w * t), s = sin(w * t)
            let displacement = decay * (a * c + b * s)
            let v = decay * ((b * w - z * a) * c - (a * w + z * b) * s)
            let energy = 0.5 * stiffness * displacement * displacement + 0.5 * v * v
            if initialEnergy == 0 || energy / initialEnergy <= 6e-9 {
                from = target; velocity = 0
                return (target, 0)
            }
            from = target + displacement; velocity = v
            return (from, v)
        }
        mutating func move(to value: Double, at now: Double, stiffness: Double, damping: Double, velocity: Double? = nil) {
            let current = sample(now)
            from = current.value; target = value
            self.velocity = current.velocity + (velocity ?? 0)
            // Reanimated discards momentum pointing away from the newly requested target.
            if (target > from && self.velocity < 0) || (target < from && self.velocity > 0) { self.velocity = 0 }
            initialEnergy = 0.5 * stiffness * pow(from - target, 2) + 0.5 * pow(velocity ?? 0, 2)
            start = now; self.stiffness = stiffness; self.damping = damping
        }
        mutating func set(_ value: Double, at now: Double) { from = value; target = value; velocity = 0; start = now; initialEnergy = 0 }
    }
    struct Frame {
        var look = M.Look()
        var lab = Array(repeating: 0.0, count: 70)
        var leanX = 0.0
        var leanY = 0.0
        var squeeze = 0.0
        var pulse = -1.0
        var press = 0.0
        var buttonPress = 0.0
        var closePress = 0.0
        var edge = 0.0
        var fade = 1.0
    }
    private(set) var fingerDown = false
    private(set) var index = 1
    private var look = M.Look()
    private var events: [M.Event] = []
    private var history = Array(repeating: 1.0, count: 7)
    private var lab = Array(repeating: 0.0, count: 70)
    private var previous: Double?
    private var epoch: Double?
    private var playing = false
    private var lastLoopTime = 0.0
    private var haveHistory = false
    private var touched = -1e6
    private var returning = false
    private var direction = 0.0
    private var speed = 0.0
    private var heldPrevious = false
    private var holdFrom = 0.0
    private var holdTarget = 0.0
    private var holdAt = 0.0
    private var mood = Spring(from: 1, target: 1)
    private var press = Spring()
    private var leanX = Spring()
    private var leanY = Spring()
    private var squeeze = Spring()
    private var button = Spring()
    private var close = Spring()
    private var pulseAt = -1.0
    private var fadeAt = -1e6
    private var grab = 1.0
    private var gestureStart = 0.0
    private var heroPanning = false
    private static let clockOffset = Date().timeIntervalSinceReferenceDate - CACurrentMediaTime()
    private func clock(_ date: Date) -> Double { date.timeIntervalSinceReferenceDate - Self.clockOffset }
    private var endTarget = -1.0
    private var pinOverridden = false
    var pinIsOverridden: Bool { pinOverridden }
    private var initialized = false
    private var inactiveAt: Double?
    private let feedback = NeonContourMoodFeedback()

    func reset() {
        epoch = nil; previous = nil; playing = false; initialized = false
        fingerDown = false; returning = false; touched = -1e6; pinOverridden = false
        index = 1; look = M.Look(); events = []; haveHistory = false
        history = Array(repeating: 1, count: 7); mood = Spring(from: 1, target: 1)
        press = Spring(); leanX = Spring(); leanY = Spring(); squeeze = Spring()
        button = Spring(); close = Spring(); pulseAt = -1; fadeAt = -1e6; endTarget = -1
    }
    func setActive(_ active: Bool, at date: Date) {
        let now = clock(date)
        if !active { inactiveAt = now; return }
        guard let stopped = inactiveAt else { return }
        let delta = now - stopped
        epoch = epoch.map { $0 + delta }; touched += delta; holdAt += delta; pulseAt += delta; fadeAt += delta; gestureStart += delta
        mood.start += delta; press.start += delta; leanX.start += delta; leanY.start += delta
        squeeze.start += delta; button.start += delta; close.start += delta
        events = events.map { .init(time: $0.time + delta, from: $0.from, to: $0.to) }
        previous = now; inactiveAt = nil
    }
    func frame(at date: Date, auto: Bool, reduced: Bool, pin: Double?, widths: [Double], lengths: [Int], scale: Double) -> Frame {
        let now = inactiveAt ?? clock(date)
        let dt = min(max(0.001, now - (previous ?? (now - 1 / 60.0))), 0.05)
        previous = now
        if !initialized {
            lab = M.settled(1, widths, lengths); initialized = true; playing = auto && !reduced
        }
        let pinned = pinOverridden ? nil : pin
        if !reduced && (pinned != nil || playing) {
            if epoch == nil { epoch = now }
            let tt = pinned ?? (now - (epoch ?? now))
            look = M.timeline(tt)
            lab = M.label(M.crossings.filter { $0.time <= look.time }, now: look.time, idle: index, widths: widths, lengths: lengths)
            index = Int(lab[1]); lastLoopTime = look.time; haveHistory = false
            var result = snapshot(now, reduced: reduced, edge: 0)
            result.leanX = 0; result.leanY = 0; result.squeeze = 0; result.pulse = -1
            return result
        }
        if !haveHistory { handoff(now) }
        let raw = mood.sample(now).value, m = M.clamp(raw, 0, 6), prev = look.mood
        if fingerDown && !heldPrevious { direction = 0 }
        heldPrevious = fingerDown
        let inst = abs(m - prev) / dt
        speed = M.mix(speed, inst, 1 - exp(-dt / 0.05))
        if inst > 0.4 && m != prev { direction = m > prev ? 1 : -1 }
        let wasHolding = holdTarget == 1
        let wantHold = !reduced && fingerDown && direction != 0 && speed < (wasHolding ? 0.6 : 0.4)
        if wantHold != wasHolding {
            holdFrom = M.holdProfile(holdFrom, holdTarget, now - holdAt)
            holdTarget = wantHold ? 1 : 0; holdAt = now
        }
        let lo = max(0, m - 1.6), hi = min(6, m + 1.6)
        for k in 0..<7 {
            var h = m
            if k > 0 && !reduced {
                let tau = returning ? 0.08 : Double(k) * 0.096
                h = M.clamp(history[k] + (m - history[k]) * (1 - exp(-dt / max(0.001, tau))), lo, hi)
            }
            history[k] = h
            if k > 0 && !reduced {
                let nested = M.clamp(m - direction * Double(k) * 0.22, 0, 6)
                h = M.mix(h, nested, M.holdProfile(holdFrom, holdTarget, now - holdAt - Double(k) * 0.04))
            }
            look.echoes[k] = h
        }
        let holdNow = M.holdProfile(holdFrom, holdTarget, now - holdAt)
        look.mood = m; look.wake = reduced ? 0 : max(M.clamp(inst / 10), look.wake * exp(-dt / 0.18))
        look.press = press.sample(now).value
        look.breath = reduced ? 0 : 1.2 * sin(now * .pi) * holdNow
        look.radiate = 0; look.time = 0
        let next = M.settle(index, m)
        if next != index {
            if reduced { lab = M.settled(next, widths, lengths) }
            else {
                while events.count > 1 && now - events[1].time > 2 { events.removeFirst() }
                if events.count >= 10 { events.removeFirst() }
                events.append(.init(time: now, from: index, to: next))
            }
            index = next
            if !returning {
                feedback.cue("detent", weight: next == 0 || next == 6 ? 2 : next == 3 ? 0 : 1)
                feedback.strike(next)
            }
        }
        if !reduced { lab = M.label(events, now: now, idle: index, widths: widths, lengths: lengths) }
        if endTarget >= 0 && !fingerDown && abs(m - endTarget) < 0.01 && speed < 0.05 { endTarget = -1 }
        if auto && !fingerDown && now - touched >= 2 && pinned == nil {
            if !returning {
                returning = true
                if reduced { mood.set(1, at: now); fadeAt = now }
                else { mood.move(to: 1, at: now, stiffness: 157.9, damping: 22.1) }
            }
            if !reduced && now - touched >= 2.6 && abs(m - 1) < 0.002 && look.echoes.allSatisfy({ abs($0 - 1) < 0.004 }) {
                mood.set(1, at: now); epoch = nil; playing = true; returning = false
            }
        }
        return snapshot(now, reduced: reduced, edge: reduced ? 0 : (raw - m) * 45 * scale)
    }
    private func snapshot(_ now: Double, reduced: Bool, edge: Double) -> Frame {
        var f = Frame()
        f.look = look; f.lab = lab
        f.leanX = leanX.sample(now).value; f.leanY = leanY.sample(now).value; f.squeeze = squeeze.sample(now).value
        f.pulse = pulseAt >= 0 && now - pulseAt < 0.5 ? now - pulseAt : -1
        f.press = press.sample(now).value; f.buttonPress = button.sample(now).value; f.closePress = close.sample(now).value
        f.edge = edge
        if reduced {
            let t = M.clamp((now - fadeAt) / 0.12)
            f.fade = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
        }
        return f
    }
    private func handoff(_ now: Double) {
        history = look.echoes; holdFrom = 0; holdTarget = 0; holdAt = now; direction = 0; speed = 0
        haveHistory = true
        if playing {
            events = M.crossings.filter { $0.time <= lastLoopTime }.suffix(4).map {
                .init(time: now - (lastLoopTime - $0.time), from: $0.from, to: $0.to)
            }
        }
    }
    private func takeOver(_ now: Double) {
        if !haveHistory { handoff(now) }
        mood.set(look.mood, at: now)
        playing = false; pinOverridden = true; touched = now; returning = false
    }
    func begin(at date: Date, reduced: Bool, hero: Bool) {
        guard !fingerDown else { return }
        let now = clock(date)
        takeOver(now); grab = look.mood; fingerDown = true; endTarget = -1
        gestureStart = now; heroPanning = false
        press.move(to: reduced ? 0 : 1, at: now, stiffness: 322.3, damping: 30.5)
        feedback.cue("onset")
    }
    func move(_ translation: CGSize, location: CGPoint, layout l: NeonContourMoodLayout, hero: Bool, reduced: Bool, at date: Date) {
        if hero && !heroPanning {
            guard hypot(translation.width, translation.height) >= 6 else { return }
            heroPanning = true
        }
        guard !reduced else { return }
        let now = clock(date), perStop = (hero ? 56 : 45) * l.scale
        mood.set(M.rubber(grab + translation.width / perStop), at: now)
        if hero {
            squeeze.set(3.12 * tanh(translation.height / 6 / 3.12), at: now)
            let dx = (location.x - l.width / 2) / l.scale, dy = (location.y - l.heroY) / l.scale
            let r = hypot(dx, dy), k = r > 0.000001 ? 10 * tanh(r / 64) / r : 0
            leanX.set(dx * k, at: now); leanY.set(dy * k, at: now)
        }
    }
    func end(_ translation: CGSize, location: CGPoint, velocity: CGSize, layout l: NeonContourMoodLayout, hero: Bool, reduced: Bool, at date: Date) {
        let now = clock(date)
        let moved = abs(translation.width) > 5 * l.scale
        // Hero Tap wins the gesture race below the pan's 6 pt minimum distance.
        if hero && !heroPanning {
            if !reduced && now - gestureStart <= 0.5 { pulseAt = now; feedback.cue("pulse") }
        } else {
            let v = velocity.width / ((hero ? 56 : 45) * l.scale)
            let target = moved && !reduced ? M.projected(mood.sample(now).value, v)
                : hero ? M.clamp(mood.sample(now).value, 0, 6).rounded()
                : M.clamp((location.x - l.firstX) / (l.lastX - l.firstX) * 6, 0, 6).rounded()
            endTarget = target
            if reduced { mood.set(target, at: now); fadeAt = now }
            else {
                mood.move(to: target, at: now, stiffness: 220, damping: 18, velocity: moved ? v : 0)
                if hero && abs(squeeze.sample(now).value) > 3.12 * 0.3 { pulseAt = now }
            }
            feedback.cue(target == 0 || target == 6 ? "limit" : "release")
        }
        finalize(at: date)
    }
    func finalize(at date: Date) {
        let now = clock(date)
        fingerDown = false; touched = now
        press.move(to: 0, at: now, stiffness: 322.3, damping: 30.5)
        leanX.move(to: 0, at: now, stiffness: 200, damping: 14)
        leanY.move(to: 0, at: now, stiffness: 200, damping: 14)
        squeeze.move(to: 0, at: now, stiffness: 200, damping: 14)
    }
    func select(_ target: Double, at date: Date, reduced: Bool) {
        let now = clock(date)
        takeOver(now); endTarget = M.clamp(target, 0, 6)
        if reduced { mood.set(endTarget, at: now); fadeAt = now }
        else { mood.move(to: endTarget, at: now, stiffness: 220, damping: 18, velocity: 0) }
    }
    func buttonDown(_ isClose: Bool, at date: Date, reduced: Bool) {
        let now = clock(date)
        if isClose { close.set(reduced ? 0 : 1, at: now) } else { button.set(reduced ? 0 : 1, at: now) }
    }
    func buttonUp(_ isClose: Bool, success: Bool, at date: Date, reduced: Bool) {
        let now = clock(date)
        if success {
            feedback.cue(isClose ? "release" : "next")
            select(isClose ? 1 : M.clamp(look.mood, 0, 6).rounded(), at: date, reduced: reduced)
            if !isClose {
                let word = NeonContourMoodTheme.moods[Int(M.clamp(look.mood, 0, 6).rounded())].word.lowercased()
                UIAccessibility.post(notification: .announcement, argument: "\(word) selected")
            }
        }
        if isClose { close.move(to: 0, at: now, stiffness: 322.3, damping: 30.5) }
        else { button.move(to: 0, at: now, stiffness: 322.3, damping: 30.5) }
    }
}
