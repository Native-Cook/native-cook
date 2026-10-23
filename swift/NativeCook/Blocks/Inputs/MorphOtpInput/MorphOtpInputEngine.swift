import SwiftUI
import UIKit

// MARK: - Constants

enum MorphOtpInputMetrics {
    static let length = 6

    // Cells (pt).
    static let cellMaxW = 48.0
    static let cellH = 64.0
    static let cellGap = 9.0
    static let cellRadius = 15.0
    // Hairline borders are drawn half a stroke inside the cell so they land on whole device pixels.
    static let border = 1.0
    // The focused cell lights its border mint, and a mint copy of that outline collapses into this
    // caret, which glows softly and breathes. A cell that loses focus lets its caret fade and
    // shrink in caretLeave s; it never grows back into an outline.
    static let caretW = 2.5
    static let caretH = 28.0
    static let caretBreath = 1.1 // s per breath
    static let caretGlow = 6.0 // blur radius of the caret's glow, pt
    // Critically damped so the caret settles without a wobble; the outline only lights faintly on
    // its way, so each keystroke reads as a quiet hand-off rather than a flash.
    static let focusSpring = (duration: 0.42, ratio: 1.0)
    static let focusIn = 0.18 // s for the mint outline to light up
    static let caretLeave = 0.2 // s
    static let activeBorder = MorphOtpInputColor(0x7cf2c5, alpha: 0.34)

    // Digits: a digit settles into its cell, rising rollFrom pt while it fades in out of the faintest
    // blur, on a soft ease-out with no overshoot, so typing feels light. Cleared, it sinks rollOut pt
    // and fades. Every digit stays clipped to its own cell.
    static let digitSize = 30.0
    static let rollFrom = 10.0
    static let rollOut = 10.0
    static let rollBlur = 1.2 // device pixels
    static let rollInTime = 0.34
    static let rollOutTime = 0.22
    static let checkDelay = 0.28 // s after the sixth digit before the code is checked

    // Wrong code: a damped shake, red, then the digits drop out.
    static let shakeAmplitude = 16.0 // pt
    static let shakeFrequency = 6.5 // Hz
    static let shakeDecay = 0.16 // s, time constant of the envelope
    static let shakeLength = 0.75 // s
    static let errorHold = 0.75 // s red before the code clears
    static let clearStagger = 0.03 // s between digits dropping out, last first

    // Right code: merge into a pill, then a circle, then the check and its ring.
    static let mergeTime = 0.46
    static let circleTime = 0.38
    static let mergedSpacing = 0.62 // × the cell width between the merged cells' centres
    static let checkAt = mergeTime + circleTime - 0.04
    static let checkTime = 0.38
    // The burst: a crisp ring grows from the circle to ringReach × its radius, a second, fainter one
    // follows ringLag of the way behind, and `sparks` short dashes shoot out. Everything stays
    // inside burstMax pt of the centre so it never reaches the status line below.
    static let burstTime = 0.62
    static let ringReach = 1.8
    static let ringLag = 0.14
    static let sparks = 12
    static let burstMax = 64.0
    static let unmergeTime = 0.7

    // autoPlay (seconds): a wrong code, the shake, the right code, the check, back to empty.
    static let loop = 10.0
    static let wrongAt = 0.7
    static let rightAt = 4.0
    static let typeStep = 0.26 // s between typed digits
    static let resetAt = 8.6
    static let userHold = 4.0 // s a touch pauses the script (and longer while the keyboard is up)
    static let resendSeconds = 30.0

    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1))

    static let background = MorphOtpInputColor(0x0c0d10)
    static let accent = MorphOtpInputColor(0x7cf2c5)
    static let error = MorphOtpInputColor(0xff5a5f)
    static let cellFill = MorphOtpInputColor(0x16171c)
    static let cellFilled = MorphOtpInputColor(0x1d1e24)
    static let cellError = MorphOtpInputColor(0x35161a)
    static let ink = MorphOtpInputColor(0x0c0d10)
    static let digitInk = MorphOtpInputColor(0xf4f5f7)
}

private typealias M = MorphOtpInputMetrics

func morphOtpInputClamp01(_ x: Double) -> Double { min(1, max(0, x)) }

/// 1 until `from`, then down to 0 at 1 on a smoothstep: a fade that holds, then goes.
func morphOtpInputFadeAfter(_ t: Double, _ from: Double) -> Double {
    let k = morphOtpInputClamp01((t - from) / (1 - from))
    return 1 - k * k * (3 - 2 * k)
}

// MARK: - Colour

/// An sRGB colour that mixes the way Reanimated's `interpolateColor` does: channels in a 2.2 gamma
/// linear space, rounded back to whole values, alpha mixed straight.
struct MorphOtpInputColor {
    var r: Double
    var g: Double
    var b: Double
    var a: Double

    init(_ hex: UInt32, alpha: Double = 1) {
        r = Double((hex >> 16) & 0xff)
        g = Double((hex >> 8) & 0xff)
        b = Double(hex & 0xff)
        a = alpha
    }

    init(r: Double, g: Double, b: Double, a: Double) {
        self.r = r
        self.g = g
        self.b = b
        self.a = a
    }

    func mix(_ other: MorphOtpInputColor, _ t: Double) -> MorphOtpInputColor {
        let t = morphOtpInputClamp01(t)
        func channel(_ x: Double, _ y: Double) -> Double {
            let lx = pow(x / 255, 2.2)
            let ly = pow(y / 255, 2.2)
            return (pow(lx + (ly - lx) * t, 1 / 2.2) * 255).rounded()
        }
        return MorphOtpInputColor(r: channel(r, other.r), g: channel(g, other.g), b: channel(b, other.b), a: a + (other.a - a) * t)
    }

    var color: Color { Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a) }
}

// MARK: - Animated values

/// A number moved by springs and timings the way Reanimated moves a shared value: a delayed
/// animation lets the one before it run on during the delay, a sequence runs its steps one after
/// another, and a spring restarted towards the same target carries on.
final class MorphOtpInputValue {
    private(set) var value: Double

    private enum Plan {
        case spring(target: Double, duration: Double, ratio: Double)
        case timing(target: Double, duration: Double, ease: (Double) -> Double)
    }

    private indirect enum Running {
        case spring(Spring)
        case timing(target: Double, from: Double, elapsed: Double, duration: Double, ease: (Double) -> Double)
        case delay(remaining: Double, next: Plan, previous: Running?)
    }

    private var running: Running?
    private var queue: [Plan] = []

    init(_ value: Double) {
        self.value = value
    }

    static func quadInOut(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2 }

    func set(_ next: Double) {
        value = next
        running = nil
        queue = []
    }

    func spring(to target: Double, duration: Double, ratio: Double) {
        running = start(.spring(target: target, duration: duration, ratio: ratio), previous: running)
        queue = []
    }

    func timing(to target: Double, duration: Double, ease: @escaping (Double) -> Double = MorphOtpInputValue.quadInOut) {
        running = start(.timing(target: target, duration: duration, ease: ease), previous: running)
        queue = []
    }

    /// Timings run one after another, each from where the last one stopped.
    func sequence(_ steps: [(target: Double, duration: Double, ease: (Double) -> Double)]) {
        guard let first = steps.first else { return }
        running = start(.timing(target: first.target, duration: first.duration, ease: first.ease), previous: running)
        queue = steps.dropFirst().map { .timing(target: $0.target, duration: $0.duration, ease: $0.ease) }
    }

    /// A timing that starts after `delay`; until then whatever moved the value keeps moving it.
    func delayedTiming(_ delay: Double, to target: Double, duration: Double, ease: @escaping (Double) -> Double = MorphOtpInputValue.quadInOut) {
        running = .delay(remaining: delay, next: .timing(target: target, duration: duration, ease: ease), previous: running)
        queue = []
    }

    private func start(_ plan: Plan, previous: Running?) -> Running {
        switch plan {
        case let .spring(target, duration, ratio):
            if case let .spring(old)? = previous, old.target == target, old.duration == duration, old.ratio == ratio {
                return .spring(old)
            }
            var carried = 0.0
            if case let .spring(old)? = previous { carried = old.velocity }
            return .spring(Spring(from: value, to: target, velocity: carried, duration: duration, ratio: ratio))
        case let .timing(target, duration, ease):
            if case let .timing(oldTarget, from, elapsed, _, _)? = previous, oldTarget == target {
                return .timing(target: target, from: from, elapsed: elapsed, duration: duration, ease: ease)
            }
            return .timing(target: target, from: value, elapsed: 0, duration: duration, ease: ease)
        }
    }

    func step(_ dt: Double) {
        guard let current = running else { return }
        let (next, finished) = advance(current, dt)
        if finished {
            running = queue.isEmpty ? nil : start(queue.removeFirst(), previous: nil)
        } else {
            running = next
        }
    }

    private func advance(_ r: Running, _ dt: Double) -> (Running?, Bool) {
        switch r {
        case var .spring(spring):
            let done = spring.step(&value, dt: dt)
            return (.spring(spring), done)
        case let .timing(target, from, elapsed, duration, ease):
            let t = elapsed + dt
            if t >= duration || duration <= 0 {
                value = target
                return (nil, true)
            }
            value = from + (target - from) * ease(t / duration)
            return (.timing(target: target, from: from, elapsed: t, duration: duration, ease: ease), false)
        case let .delay(remaining, next, previous):
            let left = remaining - dt
            if left <= 0 { return (start(next, previous: previous), false) }
            var kept = previous
            if let previous {
                let (moved, done) = advance(previous, dt)
                kept = done ? nil : moved
            }
            return (.delay(remaining: left, next: next, previous: kept), false)
        }
    }

    /// Reanimated's `withSpring` with a duration and damping ratio.
    struct Spring {
        let target: Double
        let duration: Double
        let ratio: Double
        private(set) var velocity: Double
        private let omega0: Double
        private let omega1: Double
        private let stiffness: Double
        private let initialEnergy: Double
        private static let threshold = 6e-9

        init(from value: Double, to target: Double, velocity: Double, duration: Double, ratio: Double) {
            self.target = target
            self.duration = duration
            self.ratio = ratio
            var v = velocity
            if (target > value && v < 0) || (target < value && v > 0) { v = 0 }
            self.velocity = v
            let x0 = value - target
            let zeta = ratio
            let settling = duration * 1.5
            func energy(_ x: Double, _ v: Double, _ k: Double) -> Double { 0.5 * k * x * x + 0.5 * v * v }
            func difference(_ k: Double) -> Double {
                let w = sqrt(k) * zeta
                let xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
                let vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
                return energy(xt, vt, k) / energy(x0, v, k) - Self.threshold
            }
            // Bisection, as Reanimated solves it (its mass of 4 only scales the stiffness it finds).
            var low = Double.ulpOfOne
            var high = 2e3
            let rising = difference(high) >= difference(low)
            var k = (low + high) / 2
            var left = 100
            while abs(difference(k)) > Self.threshold * 1e-3 && left > 0 {
                left -= 1
                if (difference(k) < 0) == rising { low = k } else { high = k }
                k = (low + high) / 2
            }
            if !k.isFinite { k = 170 }
            stiffness = k
            omega0 = sqrt(k)
            omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
            // Reanimated measures the starting energy with no speed of its own (none is configured).
            initialEnergy = energy(x0, 0, k)
        }

        mutating func step(_ value: inout Double, dt: Double) -> Bool {
            if initialEnergy == 0 {
                value = target
                return true
            }
            let x0 = value - target
            let v0 = velocity
            let zeta = ratio
            if zeta < 1 {
                let sin1 = sin(omega1 * dt)
                let cos1 = cos(omega1 * dt)
                let envelope = exp(-zeta * omega0 * dt)
                let frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
                value = target + frag
                velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
            } else {
                let envelope = exp(-omega0 * dt)
                value = target + envelope * (x0 + (v0 + omega0 * x0) * dt)
                velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
            }
            let now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
            if now / initialEnergy <= Self.threshold {
                value = target
                velocity = 0
                return true
            }
            return false
        }
    }
}

// MARK: - Engine

enum MorphOtpInputPhase {
    case idle, error, success
}

/// One cell's moving parts: its focus outline collapsing into the caret (`focus`), the mint outline
/// lighting up (`lit`), the caret fading once the cell lets it go (`leave`), its fill, and its
/// digit rolling in or out (`roll`: 0 below, 1 in place, 2 dropped out).
final class MorphOtpInputCell {
    let focus = MorphOtpInputValue(0)
    let lit = MorphOtpInputValue(0)
    let leave = MorphOtpInputValue(1)
    let fill = MorphOtpInputValue(0)
    let roll = MorphOtpInputValue(2)
    /// The last digit shown, kept while it drops out of an emptied cell.
    var glyph = " "
    var active = false
    var filled = false
}

/// The controller: typing, checking, the error and success sequences and the script, stepped once a
/// frame. Nothing in it is observed: the timeline redraws every frame anyway.
final class MorphOtpInputEngine {
    /// The code as passed in, and the six digits that can actually match.
    let source: String
    let code: String
    var reduced = false
    var onVerified: (() -> Void)?

    private(set) var now = 0.0
    private(set) var value = ""
    private(set) var shown = [String](repeating: "", count: M.length)
    private(set) var phase = MorphOtpInputPhase.idle
    private(set) var resendFrom = 0.0
    /// Bumped to ask the hidden field to take or drop the keyboard.
    private(set) var focusRequest = (id: 0, focused: false)

    private(set) var shake = 0.0
    private var shakeAt = -1.0
    let merge = MorphOtpInputValue(0)
    let check = MorphOtpInputValue(0)
    let burst = MorphOtpInputValue(0)
    let errorAmt = MorphOtpInputValue(0)
    // How much each status line shows. Each fades on its own, so going from idle straight to
    // success (or back) never passes through the error line.
    let idleShown = MorphOtpInputValue(1)
    let errorShown = MorphOtpInputValue(0)
    let successShown = MorphOtpInputValue(0)
    let cells = (0..<M.length).map { _ in MorphOtpInputCell() }

    private var playing = false
    /// The digits in the row were typed by the demo, not by the user.
    private var byScript = false
    /// The hidden field holds the keyboard, as its delegate reports.
    var fieldFocused = false
    private var lastDate: Date?
    private var timers: [(at: Double, run: () -> Void)] = []
    private var script: [(at: Double, run: () -> Void)] = []

    private let selection = UISelectionFeedbackGenerator()
    private let notification = UINotificationFeedbackGenerator()

    init(code: String) {
        source = code
        self.code = String((code.filter(\.isNumber) + "000000").prefix(M.length))
    }

    func configure(reduced: Bool, onVerified: (() -> Void)?) {
        self.reduced = reduced
        self.onVerified = onVerified
    }

    func step(to date: Date, autoPlay: Bool) {
        if autoPlay != playing {
            playing = autoPlay
            if autoPlay { runScript() } else { script.removeAll() }
        }
        let elapsed = lastDate.map { date.timeIntervalSince($0) } ?? (1.0 / 60)
        lastDate = date
        let dt = min(max(elapsed, 0), 0.064)
        now += dt

        // A shake asked for between frames starts on this one.
        if shakeAt == -2 { shakeAt = now }
        if shakeAt >= 0 {
            let k = now - shakeAt
            if k > M.shakeLength {
                shakeAt = -1
                shake = 0
            } else {
                shake = M.shakeAmplitude * exp(-k / M.shakeDecay) * sin(2 * Double.pi * M.shakeFrequency * k)
            }
        }

        for value in [merge, check, burst, errorAmt, idleShown, errorShown, successShown] { value.step(dt) }
        for cell in cells {
            cell.focus.step(dt)
            cell.lit.step(dt)
            cell.leave.step(dt)
            cell.fill.step(dt)
            cell.roll.step(dt)
        }
        // Timers fire after the values moved, so what they start is first drawn at rest. What they
        // run may add more timers, so each is taken off its list before it runs.
        while let index = timers.indices.min(by: { timers[$0].at < timers[$1].at }), timers[index].at <= now {
            timers.remove(at: index).run()
        }
        while let index = script.indices.min(by: { script[$0].at < script[$1].at }), script[index].at <= now {
            script.remove(at: index).run()
        }
    }

    private func later(_ seconds: Double, _ run: @escaping () -> Void) {
        timers.append((now + seconds, run))
    }

    // MARK: State that renders

    /// Mirrors the cells to the code and phase, the way the React tree re-renders.
    private func sync() {
        let activeIndex = min(value.count, M.length - 1)
        for (i, cell) in cells.enumerated() {
            let digit = shown[i]
            let filled = !digit.isEmpty
            let active = phase == .idle && i == activeIndex && value.count < M.length
            if active != cell.active {
                cell.active = active
                if active {
                    // Always start from the full outline, so focus arrives the same way every time.
                    cell.focus.set(0)
                    cell.leave.set(0)
                    cell.lit.timing(to: 1, duration: reduced ? 0.16 : M.focusIn)
                    if reduced { cell.focus.set(1) } else {
                        cell.focus.spring(to: 1, duration: M.focusSpring.duration, ratio: M.focusSpring.ratio)
                    }
                } else {
                    cell.lit.timing(to: 0, duration: M.caretLeave)
                    cell.leave.timing(to: 1, duration: M.caretLeave, ease: { M.easeOut.value(at: $0) })
                }
            }
            if filled && digit != cell.glyph { cell.glyph = digit }
            if filled != cell.filled {
                cell.filled = filled
                cell.fill.timing(to: filled ? 1 : 0, duration: 0.18)
                if filled {
                    cell.roll.set(0)
                    cell.roll.timing(to: 1, duration: reduced ? 0.2 : M.rollInTime, ease: { M.easeOut.value(at: $0) })
                } else {
                    cell.roll.timing(to: 2, duration: M.rollOutTime, ease: { M.easeOut.value(at: $0) })
                }
            }
        }
    }

    private func setPhase(_ p: MorphOtpInputPhase) {
        phase = p
        idleShown.timing(to: p == .idle ? 1 : 0, duration: 0.22)
        errorShown.timing(to: p == .error ? 1 : 0, duration: 0.22)
        successShown.timing(to: p == .success ? 1 : 0, duration: 0.22)
        sync()
    }

    private func fail(user: Bool) {
        setPhase(.error)
        errorAmt.timing(to: 1, duration: 0.12)
        if !reduced { shakeAt = -2 }
        if user { notification.notificationOccurred(.error) }
        later(M.errorHold) { [self] in
            // Digits drop out last to first while the red drains away.
            value = ""
            errorAmt.timing(to: 0, duration: 0.32)
            sync()
            for i in stride(from: M.length - 1, through: 0, by: -1) {
                later(Double(M.length - 1 - i) * M.clearStagger) { [self] in
                    shown[i] = ""
                    sync()
                }
            }
            later(Double(M.length) * M.clearStagger + 0.12) { [self] in setPhase(.idle) }
        }
    }

    private func succeed(user: Bool) {
        setPhase(.success)
        focusRequest = (focusRequest.id + 1, false)
        if reduced {
            merge.timing(to: 2, duration: 0.32)
            check.delayedTiming(0.24, to: 1, duration: 0.26)
        } else {
            let inOut: (Double) -> Double = { M.easeInOut.value(at: $0) }
            merge.sequence([(1, M.mergeTime, inOut), (2, M.circleTime, inOut)])
            check.delayedTiming(M.checkAt, to: 1, duration: M.checkTime, ease: { M.easeOut.value(at: $0) })
            burst.set(0)
            burst.delayedTiming(M.checkAt, to: 1, duration: M.burstTime, ease: { $0 })
        }
        // The success haptic lands with the check, as the ring leaves.
        later(reduced ? 0.24 : M.checkAt) { [self] in
            if user { notification.notificationOccurred(.success) }
            onVerified?()
        }
    }

    private func verify(user: Bool) {
        if value == code { succeed(user: user) } else { fail(user: user) }
    }

    /// Takes the field's text (typed, pasted or filled from a message) as the new code.
    private func enter(_ text: String, user: Bool) {
        if phase != .idle { return }
        let next = String(text.filter(\.isNumber).prefix(M.length))
        if next == value { return }
        value = next
        byScript = !user
        let chars = Array(next)
        shown = (0..<M.length).map { $0 < chars.count ? String(chars[$0]) : "" }
        sync()
        if next.count == M.length { later(M.checkDelay) { [self] in verify(user: user) } }
    }

    /// Back to an empty row: the circle opens back out into six cells.
    private func reset() {
        timers.removeAll()
        value = ""
        shown = [String](repeating: "", count: M.length)
        errorAmt.timing(to: 0, duration: 0.2)
        check.timing(to: 0, duration: 0.16)
        burst.set(0)
        merge.timing(to: 0, duration: reduced ? 0.3 : M.unmergeTime, ease: { M.easeInOut.value(at: $0) })
        resendFrom = now
        setPhase(.idle)
    }

    // MARK: Script

    private func typeCode(_ digits: String, at: Double) {
        for (i, digit) in digits.enumerated() {
            script.append((now + at + Double(i) * M.typeStep, { [self] in enter(value + String(digit), user: false) }))
        }
    }

    private func runScript() {
        script.removeAll()
        // A wrong code that differs from the right one in two places.
        let c = Array(code)
        var wrong = code
        if c.count == M.length {
            wrong = String(c[0..<3]) + String(c[4]) + String(c[3]) + String(c[5])
        }
        typeCode(wrong == code ? String(code.reversed()) : wrong, at: M.wrongAt)
        typeCode(code, at: M.rightAt)
        script.append((now + M.resetAt, { [self] in reset() }))
        script.append((now + M.loop, { [self] in runScript() }))
    }

    /// A touch pauses the script; it returns whether it cleared a code the demo was half way through.
    @discardableResult
    private func touched() -> Bool {
        if !playing { return false }
        script.removeAll()
        // The demo's half-typed code is not the user's: clear it so their own digits start fresh.
        var cleared = false
        if byScript && phase == .idle && !value.isEmpty {
            timers.removeAll()
            value = ""
            byScript = false
            shown = [String](repeating: "", count: M.length)
            sync()
            cleared = true
        }
        script.append((now + M.userHold, { [self] in resume() }))
        return cleared
    }

    /// The demo waits while the keyboard is up, so someone reading a code off another screen keeps
    /// the digits they have typed so far.
    private func resume() {
        if fieldFocused {
            script.append((now + M.userHold, { [self] in resume() }))
            return
        }
        reset()
        script.append((now + M.unmergeTime + 0.3, { [self] in runScript() }))
    }

    // MARK: Input

    /// The hidden field's text changed; returns the text it should show.
    func type(_ text: String) -> String {
        let before = value
        // If the touch cleared the demo's digits, only what the user just typed counts.
        enter(touched() ? String(text.dropFirst(before.count)) : text, user: true)
        return value
    }

    func tapRow() {
        touched()
        if phase == .success {
            reset()
            return
        }
        selection.selectionChanged()
        focusRequest = (focusRequest.id + 1, true)
    }

    func resend() {
        touched()
        selection.selectionChanged()
        resendFrom = now
    }
}
