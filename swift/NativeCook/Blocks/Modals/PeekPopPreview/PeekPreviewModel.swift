import UIKit

/// One animated number: set directly, or moved by a spring, a timing or a decay.
struct PeekPopDrive {
    private enum Mode {
        case still
        case spring(to: Double, stiffness: Double, damping: Double)
        case timing(from: Double, to: Double, duration: Double, elapsed: Double, easeOut: Bool)
        case decay(low: Double, high: Double)
    }

    private(set) var value = 0.0
    private var velocity = 0.0
    private var delay = 0.0
    private var mode = Mode.still

    var moving: Bool { if case .still = mode { return false } else { return true } }

    mutating func set(_ next: Double) {
        value = next
        velocity = 0
        delay = 0
        mode = .still
    }

    mutating func stop() {
        velocity = 0
        delay = 0
        mode = .still
    }

    mutating func spring(_ to: Double, _ spring: PeekPopPreviewMotion.Spring, velocity start: Double = 0, delay wait: Double = 0) {
        // A delayed spring leaves from wherever the value is when it starts, at rest until then.
        velocity = wait > 0 ? 0 : start
        delay = wait
        mode = .spring(to: to, stiffness: spring.stiffness, damping: spring.damping)
    }

    mutating func timing(_ to: Double, duration: Double, easeOut: Bool = false, delay wait: Double = 0) {
        velocity = 0
        delay = wait
        mode = .timing(from: value, to: to, duration: duration, elapsed: 0, easeOut: easeOut)
    }

    mutating func decay(velocity start: Double, low: Double, high: Double) {
        velocity = start
        delay = 0
        mode = .decay(low: low, high: high)
    }

    mutating func step(_ dt: Double) {
        var left = dt
        if delay > 0 {
            let used = min(delay, left)
            delay -= used
            left -= used
            if left <= 0 { return }
        }
        switch mode {
        case .still:
            return
        case let .spring(to, stiffness, damping):
            // Semi-implicit Euler in steps short enough for the stiffest spring here.
            let count = max(1, Int(ceil(left / 0.002)))
            let h = left / Double(count)
            for _ in 0..<count {
                velocity += (-stiffness * (value - to) - damping * velocity) * h
                value += velocity * h
            }
            if abs(value - to) < 0.0004 && abs(velocity) < 0.004 {
                value = to
                velocity = 0
                mode = .still
            }
        case let .timing(from, to, duration, elapsed, easeOut):
            let now = elapsed + left
            let u = PeekPopPreviewMotion.clamp(now / duration, 0, 1)
            value = PeekPopPreviewMotion.mix(from, to, easeOut ? PeekPopPreviewMotion.easeOutCubic(u) : u)
            mode = u >= 1 ? .still : .timing(from: from, to: to, duration: duration, elapsed: now, easeOut: easeOut)
        case let .decay(low, high):
            // Reanimated's default deceleration: 0.998 per millisecond.
            let next = velocity * pow(0.998, left * 1000)
            value += (velocity + next) / 2 * left
            velocity = next
            if value <= low || value >= high {
                value = PeekPopPreviewMotion.clamp(value, low, high)
                velocity = 0
                mode = .still
            } else if abs(velocity) < 2 {
                velocity = 0
                mode = .still
            }
        }
    }
}

/// PeekPreview: long-press an item and it lifts into a preview card; swipe up and the card becomes the page.
/// Reusable: this model knows nothing about articles. It owns the state, the touch rules and the autoplay hand-over;
/// the view draws what is inside the frame from the same values. A plain reference type: the timeline steps it once
/// per frame, so nothing here is observed.
final class PeekPreviewModel {
    private typealias M = PeekPopPreviewMotion

    private enum Mode { case auto, manual, returning }
    private enum Touch { case none, trigger, frame, backdrop }

    private(set) var active = -1
    private(set) var press = PeekPopDrive()
    private(set) var peek = PeekPopDrive()
    private(set) var sink = PeekPopDrive()
    private(set) var pop = PeekPopDrive()
    private(set) var scroll = PeekPopDrive()
    private(set) var reveal = 0.0
    private(set) var clock = 0.0
    private(set) var scripted = false

    /// How far each item's open page can scroll; the caller sets it from its content.
    var maxScrolls: [Double] = []

    private var wants = false
    private var held = 0
    private var mode = Mode.returning
    private var idle = 0.0
    private var last: Date?
    private var reduced = false

    private var touch = Touch.none
    private var touchRow = -1
    private var downAt: Date?
    private var opened = false
    private var failed = false
    private var panning = false
    private var onPage = false
    private var scrollAtGrab = 0.0
    private var travelAtOpen = 0.0

    private var maxScroll: Double { active >= 0 && active < maxScrolls.count ? maxScrolls[active] : 0 }
    var card: Double { peek.value - sink.value }

    /// Nothing is moving and nothing is out: a paused timeline can stay paused.
    var settled: Bool {
        held == 0 && active < 0 && !press.moving && !peek.moving && !sink.moving && !pop.moving && !scroll.moving && reveal == 0
    }

    /// Whether the frame takes touches: it is up as a preview or a page.
    var frameIsUp: Bool { active >= 0 && (card > 0.3 || pop.value > 0.3) }

    func reset(playing: Bool, reduced: Bool) {
        self.reduced = reduced
        clock = 0
        scripted = false
        mode = playing ? .returning : .manual
        wants = false
        active = -1
        press.set(0)
        peek.set(0)
        sink.set(0)
        pop.set(0)
        scroll.set(0)
        reveal = 0
        last = nil
    }

    // MARK: The clock

    func step(_ date: Date, playing: Bool) {
        let dt = min(last.map { date.timeIntervalSince($0) } ?? 0, 0.048)
        last = date
        guard dt > 0 else { return }

        // The long press: the finger has stayed down for PRESS.
        if touch == .trigger, !opened, !failed, let downAt, date.timeIntervalSince(downAt) >= M.press {
            opened = true
            peekOpen()
            haptic()
        }

        if !scripted {
            press.step(dt)
            peek.step(dt)
            sink.step(dt)
            pop.step(dt)
            scroll.step(dt)
            let up = wants && sink.value < 0.08
            reveal = up ? min(1, reveal + dt / M.revealIn) : max(0, reveal - dt / M.revealOut)
            if active >= 0 && held == 0 && !wants && peek.value < 0.002 && pop.value < 0.002 && press.value < 0.002 {
                active = -1
                press.set(0)
                peek.set(0)
                sink.set(0)
                pop.set(0)
                scroll.set(0)
            }
        }

        switch mode {
        case .manual:
            guard playing else { return }
            if held == 0 { idle += dt }
            if idle >= M.resumeAfter {
                peekClose(velocity: 0)
                mode = .returning
            }
        case .returning:
            // Back at rest: the loop starts again from 0.
            if active < 0 && playing {
                clock = 0
                scripted = true
                mode = .auto
            }
        case .auto:
            guard playing else { return }
            var t = clock + dt
            if t >= M.loop { t -= M.loop }
            clock = t
            let row = min(M.scriptRow(t), max(maxScrolls.count - 1, 0))
            let pressed = M.scriptPress(t)
            let peeked = M.scriptPeek(t)
            let popped = M.scriptPop(t)
            press.set(pressed)
            peek.set(peeked)
            sink.set(0)
            pop.set(popped)
            reveal = M.scriptReveal(t)
            scroll.set(min(M.scriptScroll(t), maxScrolls.indices.contains(row) ? maxScrolls[row] : 0))
            wants = false
            active = pressed + abs(peeked) + abs(popped) > 0.0005 ? row : -1
        }
    }

    // MARK: Moves

    private func drive(_ value: inout PeekPopDrive, _ target: Double, _ spring: M.Spring, velocity: Double = 0) {
        if reduced { value.timing(target, duration: M.reducedDuration) }
        else { value.spring(target, spring, velocity: velocity) }
    }

    private func peekOpen() {
        wants = true
        sink.set(0)
        pop.set(0)
        drive(&peek, 1, M.peekSpring)
        press.timing(0, duration: M.pressRelease.1 - M.pressRelease.0, delay: M.pressRelease.0)
    }

    private func peekStay() {
        wants = true
        drive(&peek, 1, M.peekSpring)
        drive(&sink, 0, M.peekSpring)
        drive(&pop, 0, M.closeSpring)
    }

    private func peekPop(velocity: Double) {
        wants = true
        press.timing(0, duration: 0.12)
        drive(&sink, 0, M.peekSpring)
        drive(&peek, 1, M.peekSpring)
        drive(&pop, 1, M.popSpring, velocity: velocity)
    }

    /// Everything goes back into the row, along the path it came. `velocity` is per second, negative toward the row.
    private func peekClose(velocity: Double) {
        wants = false
        let fromPage = pop.value > 0.2
        press.timing(0, duration: 0.12)
        scroll.timing(0, duration: 0.26)
        drive(&sink, 0, M.closeSpring)
        drive(&pop, 0, M.closeSpring, velocity: fromPage ? velocity : 0)
        if fromPage && !reduced { peek.spring(0, M.closeSpring, delay: M.closeFollow) }
        else { drive(&peek, 0, M.closeSpring, velocity: fromPage ? 0 : velocity) }
    }

    /// A script was driving and a finger arrived: carry on from wherever it was to the nearest resting state.
    private func settle() {
        guard active >= 0 else { return }
        if pop.value > 0.5 { peekPop(velocity: 0) }
        else if card > 0.5 { peekStay() }
        else { peekClose(velocity: 0) }
    }

    /// Every touch starts here: the script lets go and the idle clock restarts.
    private func takeOver() {
        idle = 0
        if scripted {
            scripted = false
            settle()
        }
        mode = .manual
    }

    private func peekDrag(_ travel: Double) {
        if travel < 0 {
            sink.set(0)
            pop.set(min(-travel / M.popUpSpan, 1.02))
        } else {
            pop.set(0)
            sink.set(M.rubber(travel, M.sinkRubber) / M.sinkSpan)
        }
    }

    private func peekRelease(_ travel: Double, _ velocity: Double) {
        if pop.value > M.popCommit || (travel < -12 && velocity < -M.popVelocity) {
            peekPop(velocity: -velocity / M.popUpSpan)
            haptic()
            return
        }
        if travel > M.sinkClose || velocity > M.sinkVelocity {
            peekClose(velocity: -velocity * M.rubberSlope(max(travel, 0), M.sinkRubber) / M.sinkSpan)
            haptic()
            return
        }
        peekStay()
    }

    private func pageDrag(_ travel: Double) {
        let want = scrollAtGrab - travel
        if want >= 0 {
            scroll.set(want <= maxScroll ? want : maxScroll + M.rubber(want - maxScroll, M.scrollRubber))
            pop.set(1)
        } else {
            scroll.set(0)
            pop.set(1 - M.rubber(-want, M.popDownRubber) / M.popDownSpan)
        }
    }

    private func pageRelease(_ travel: Double, _ velocity: Double) {
        let want = scrollAtGrab - travel
        if want < 0 {
            if -want > M.dismissPull || velocity > M.dismissVelocity {
                peekClose(velocity: -velocity * M.rubberSlope(-want, M.popDownRubber) / M.popDownSpan)
                haptic()
                return
            }
            drive(&pop, 1, M.popSpring)
            return
        }
        if want > maxScroll { drive(&scroll, maxScroll, M.closeSpring) }
        else if maxScroll > 0 { scroll.decay(velocity: -velocity, low: 0, high: maxScroll) }
    }

    private func haptic() {
        DispatchQueue.main.async { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    }

    // MARK: Touch

    /// The finger came down. `frame` is where the preview or page is drawn right now; `rows` where the items rest.
    func down(at point: CGPoint, frame: CGRect, rows: [CGRect], date: Date) {
        takeOver()
        opened = false
        failed = false
        panning = false
        travelAtOpen = 0
        if frameIsUp {
            if frame.contains(point) {
                touch = .frame
                held += 1
                onPage = pop.value > 0.5
                scroll.stop()
                scrollAtGrab = scroll.value
            } else {
                touch = .backdrop
            }
            return
        }
        guard let index = rows.firstIndex(where: { $0.contains(point) }) else {
            touch = .none
            return
        }
        touch = .trigger
        touchRow = index
        downAt = date
        held += 1
        if active != index {
            // Another item was still on its way home: it is home now.
            peek.set(0)
            sink.set(0)
            pop.set(0)
            reveal = 0
        }
        active = index
        wants = false
        press.timing(1, duration: M.press, easeOut: true)
    }

    func moved(translation: CGSize) {
        let travel = translation.height
        switch touch {
        case .trigger:
            if failed { return }
            if !opened {
                if hypot(translation.width, translation.height) > 10 {
                    // It moved before the long press: this touch is not a peek.
                    failed = true
                    held = max(0, held - 1)
                    press.timing(0, duration: 0.14)
                }
                travelAtOpen = travel
                return
            }
            peekDrag(travel - travelAtOpen)
        case .frame:
            if !panning {
                if abs(travel) < 6 { return }
                panning = true
            }
            if onPage { pageDrag(travel) } else { peekDrag(travel) }
        case .backdrop, .none:
            return
        }
    }

    func up(translation: CGSize, velocity: CGSize, date: Date) {
        let travel = translation.height
        let shape = touch
        touch = .none
        idle = 0
        switch shape {
        case .trigger:
            if failed { return }
            held = max(0, held - 1)
            if opened {
                peekRelease(travel - travelAtOpen, velocity.height)
                return
            }
            // Lifted before the long press: a quick tap opens the page directly.
            if let downAt, date.timeIntervalSince(downAt) < M.press - 0.04 {
                active = touchRow
                peekPop(velocity: 0)
                haptic()
            } else {
                press.timing(0, duration: 0.14)
            }
        case .frame:
            held = max(0, held - 1)
            if panning {
                if onPage { pageRelease(travel, velocity.height) }
                else { peekRelease(travel, velocity.height) }
            } else if pop.value <= 0.5 && card >= 0.5 {
                // A tap on the preview opens it.
                peekPop(velocity: 0)
                haptic()
            }
        case .backdrop:
            if hypot(translation.width, translation.height) < 10 {
                peekClose(velocity: 0)
                haptic()
            }
        case .none:
            return
        }
    }

    /// A touch that closes (an action, the back button): it takes over from the script first.
    func closeFromTouch() {
        takeOver()
        peekClose(velocity: 0)
        haptic()
    }
}
