import UIKit

/// The loop clock, the wheel and the user's tape, stepped once a frame from the timeline. Not
/// observed: the timeline's date drives every redraw.
final class DymoLabelInputEngine {
    typealias M = DymoLabelInputMotion
    enum Mode { case auto, user, back, still }

    static let returnFor = 0.8
    static let maxLetters = 12

    /// The wheel's angle as a function of time: held, a timed spin, or a spring after a flick.
    private enum Wheel {
        case fixed(Double)
        case tween(from: Double, to: Double, start: Double, duration: Double)
        case spring(from: Double, to: Double, v0: Double, start: Double)
    }

    var autoEnabled = false
    var reduced = false
    var artReady = false
    private(set) var now = 0.0
    private(set) var loopT = 0.0
    private(set) var mode: Mode = .still
    private var modeAt = 0.0
    private var last: Date?
    private(set) var lastTouch = -100.0
    var focused = false
    private(set) var touching = false
    private var wheelAnim: Wheel = .fixed(0)
    private var lastSlot = 0
    private(set) var user = DymoLabelInputUserTape()
    private var lastLoopText = -1

    // Threshold events for the view.
    var pendingText: String?
    var pendingTape: Int?

    func setMode(auto: Bool, reduced: Bool) {
        autoEnabled = auto
        self.reduced = reduced
        if mode != .user { mode = auto ? .auto : .still }
    }

    // MARK: Wheel

    func wheel(at t: Double) -> Double {
        switch wheelAnim {
        case let .fixed(v): return v
        case let .tween(from, to, start, duration):
            return from + (to - from) * M.spinEase(M.seg(t, start, start + duration))
        case let .spring(from, to, v0, start):
            // Damping ratio 0.8, settling in about 0.4 s, starting with the finger's speed.
            let u = max(0, t - start)
            let zeta = 0.8, w0 = 14.4
            let wd = w0 * sqrt(1 - zeta * zeta)
            let x0 = from - to
            let e = exp(-zeta * w0 * u)
            return to + e * (x0 * cos(wd * u) + (v0 + zeta * w0 * x0) / wd * sin(wd * u))
        }
    }

    private var wheelSettled: Bool {
        switch wheelAnim {
        case .fixed: return true
        case let .tween(_, _, start, duration): return now > start + duration
        case let .spring(_, _, _, start): return now > start + 1
        }
    }

    // MARK: Frame

    func step(_ date: Date) {
        let dt = min(0.05, max(0, date.timeIntervalSince(last ?? date)))
        last = date
        guard artReady else { return }
        let t = now + dt
        now = t
        if mode == .auto { loopT = (loopT + dt).truncatingRemainder(dividingBy: DymoLabelInput.loop) }
        let busy = user.cutAt > 0 && t < user.cutAt + 3.6
        let lastE = user.e.last ?? -100
        if mode == .user && autoEnabled && !focused && !touching && !busy && t - max(lastTouch, lastE) > 2 {
            mode = .back
            modeAt = t
            let w = wheel(at: t)
            wheelAnim = .tween(from: w, to: M.angleFor(0, from: w), start: t, duration: 0.7)
            pendingText = ""
            pendingTape = 0
        }
        if mode == .back && t - modeAt >= Self.returnFor {
            mode = .auto
            loopT = 0
            user = DymoLabelInputUserTape()
            wheelAnim = .fixed(0)
            lastLoopText = -1
        }
        if mode == .auto {
            let n = M.loopText(loopT)
            if n != lastLoopText {
                lastLoopText = n
                pendingText = String(M.word.prefix(n))
            }
        }
    }

    var wantsFrames: Bool {
        switch mode {
        case .auto, .back: return true
        case .still: return false
        case .user:
            if autoEnabled || touching || !wheelSettled { return true }
            if user.cutAt > 0 && now < user.cutAt + 3.7 { return true }
            return now - max(lastTouch, user.e.last ?? -100) < 1
        }
    }

    var returnK: Double { mode == .back ? M.easeInOut(M.seg(now, modeAt, modeAt + Self.returnFor)) : 0 }

    func pose() -> DymoLabelInputPose {
        switch mode {
        case .auto: return M.loopPose(loopT)
        case .still: return M.loopPose(0)
        case .user, .back: return user.pose(now, wheel: wheel(at: now), reduced: reduced)
        }
    }

    // MARK: Actions

    /// Hands the machine to the user as the loop left it.
    func takeOver() {
        let t = now
        lastTouch = t
        switch mode {
        case .user: return
        case .auto:
            let p = M.loopPose(loopT)
            let cut = loopT >= 4.7 && loopT < 8.4
            var u = DymoLabelInputUserTape()
            u.text = p.tape
            u.e = p.tape.map { _ in t - 1 }
            u.strip = cut ? M.word : []
            u.cutAt = cut ? t - (loopT - 4.75) : -100
            user = u
            wheelAnim = .fixed(p.wheel)
            lastSlot = M.slotAt(p.wheel)
            pendingText = String(p.tape)
        case .still, .back:
            user = DymoLabelInputUserTape()
            wheelAnim = .fixed(mode == .back ? wheel(at: t) : 0)
        }
        mode = .user
    }

    /// Presses `added` letters after the ones on the tape, spinning the wheel to each unless `spun`.
    private func press(_ current: [Character], _ added: [Character], spun: Bool) {
        let t = now
        var e = Array(user.e.prefix(current.count))
        var lastAt = e.last ?? t - 1
        var angle = wheel(at: t)
        for ch in added {
            let target = M.angleFor(M.slotOf(ch), from: angle)
            let needsSpin = !spun && abs(target - angle) > 0.5
            let at = max(t + (needsSpin ? 0.36 : 0.04), lastAt + 0.42)
            if needsSpin && !reduced {
                wheelAnim = .tween(from: wheel(at: t), to: target, start: at - 0.36, duration: 0.32)
            } else if needsSpin {
                wheelAnim = .fixed(target)
            }
            angle = target
            e.append(at)
            lastAt = at
            let delay = max(0, at - t)
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
        }
        user.text = current + added
        user.e = e
        lastTouch = t
    }

    /// The field's new text; returns what the field should show.
    func type(_ value: String) -> String {
        let clean = Array(value.uppercased().filter { M.wheelChars.contains($0) }.prefix(Self.maxLetters))
        if mode != .user { takeOver() }
        let prev = user.text
        if clean.starts(with: prev) {
            press(prev, Array(clean[prev.count...]), spun: false)
        } else {
            // Edited in the middle or deleted: the tape shows the new text, already pressed.
            user.text = clean
            user.e = clean.map { _ in now - 1 }
            lastTouch = now
        }
        return String(clean)
    }

    /// Cuts the strip; false when there is nothing to cut.
    @discardableResult
    func cut() -> Bool {
        if mode != .user { takeOver() }
        guard !user.text.isEmpty else { return false }
        let t = now
        user.strip = user.text
        user.stripColour = user.colour
        user.text = []
        user.e = []
        user.cutAt = t
        lastTouch = t
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        DispatchQueue.main.asyncAfter(deadline: .now() + (reduced ? 0 : 1.2)) {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
        return true
    }

    func pickTape(_ i: Int) {
        if mode != .user { takeOver() }
        let t = now
        let shown = M.seg(t, user.colourAt, user.colourAt + 0.25) >= 1 ? user.colour : user.colourFrom
        user.colourFrom = shown
        user.colour = i
        user.colourAt = t
        lastTouch = t
    }

    /// Presses the letter under the head; returns the new tape text, or nil.
    func pressTrigger() -> String? {
        let ch = M.wheelChars[M.slotAt(wheel(at: now))]
        if ch == " " { return nil }
        let prev = user.text
        if prev.count >= Self.maxLetters { return nil }
        press(prev, [ch], spun: true)
        return String(user.text)
    }

    // MARK: Touch on the machine

    func touchDown() {
        touching = true
        lastTouch = now
    }

    /// Tangential finger motion turns the wheel about its centre; true when a detent was crossed.
    func turn(at p: CGPoint, by d: CGSize, geo g: DymoLabelInputGeo) -> Bool {
        if mode != .user { takeOver() }
        let x = p.x - g.wheelX
        let y = p.y - g.wheelY
        let r2 = max(200, x * x + y * y)
        let deg = (x * d.height - y * d.width) / r2 * 180 / .pi
        let w = wheel(at: now) + deg
        wheelAnim = .fixed(w)
        lastTouch = now
        let slot = M.slotAt(w)
        if slot != lastSlot {
            lastSlot = slot
            return true
        }
        return false
    }

    func release(at p: CGPoint, velocity v: CGSize, geo g: DymoLabelInputGeo, turned: Bool) {
        if turned {
            let x = p.x - g.wheelX
            let y = p.y - g.wheelY
            let r2 = max(200, x * x + y * y)
            let vd = (x * v.height - y * v.width) / r2 * 180 / .pi
            let w = wheel(at: now)
            let target = M.angleFor(M.slotAt(w + vd * 0.12), from: w)
            wheelAnim = reduced ? .fixed(target) : .spring(from: w, to: target, v0: vd, start: now)
        }
        touching = false
        lastTouch = now
    }
}
