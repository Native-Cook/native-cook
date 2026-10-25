import UIKit

/// The loop clock and the user's pick, stepped once a frame from the timeline. Not observed: the
/// timeline's date drives every redraw.
final class CrayonBoxPickerEngine {
    typealias M = CrayonBoxPickerMotion
    enum Mode { case auto, user, back, still }

    static let returnFor = 0.8

    /// The pull's motion after release: held, a short timing to fully out, or a spring back.
    private enum Pull {
        case fixed(Double)
        case tween(from: Double, start: Double)
        case spring(from: Double, start: Double)
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
    private(set) var touching = false
    private(set) var active = -1
    private var pullAnim: Pull = .fixed(0)
    private(set) var wobble = 0.0
    private(set) var releaseAt = -100.0
    private var freed = false
    /// The chosen colour (from, to, since) and the swatch (old, new, since).
    private var colourState: (Int, Int, Double) = (M.restColour, M.restColour, -100)
    private var swatchState: (Int, Int, Double) = (M.restColour, M.restColour, -100)

    func setMode(auto: Bool, reduced: Bool) {
        autoEnabled = auto
        self.reduced = reduced
        if mode != .user { mode = auto ? .auto : .still }
    }

    func pull(at t: Double) -> Double {
        switch pullAnim {
        case let .fixed(v): return v
        case let .tween(from, start):
            return from + (1 - from) * M.easeInOut(M.seg(t, start, start + 0.12))
        case let .spring(from, start):
            // Damping ratio 0.7, settling in about 0.4 s.
            let u = max(0, t - start)
            let zeta = 0.7, w0 = 16.4
            let wd = w0 * sqrt(1 - zeta * zeta)
            return from * exp(-zeta * w0 * u) * (cos(wd * u) + zeta * w0 / wd * sin(wd * u))
        }
    }

    private func pullSettled(_ t: Double) -> Bool {
        switch pullAnim {
        case .fixed: return true
        case let .tween(_, start): return t > start + 0.12
        case let .spring(_, start): return t > start + 1
        }
    }

    func step(_ date: Date) {
        let dt = min(0.05, max(0, date.timeIntervalSince(last ?? date)))
        last = date
        guard artReady else { return }
        let t = now + dt
        now = t
        if mode == .auto { loopT = (loopT + dt).truncatingRemainder(dividingBy: CrayonBoxPicker.loop) }
        let r = releaseAt
        if r > 0 && t >= r + 2.25 {
            active = -1
            releaseAt = -100
            pullAnim = .fixed(0)
            lastTouch = t
        }
        if r < 0 && active >= 0 && !touching && pullSettled(t) && abs(pull(at: t)) < 0.004 { active = -1 }
        wobble *= exp(-dt * 5)
        if mode == .user && autoEnabled && !touching && active < 0 && t - lastTouch > 2 {
            mode = .back
            modeAt = t
        }
        if mode == .back && t - modeAt >= Self.returnFor {
            mode = .auto
            loopT = 0
        }
    }

    var wantsFrames: Bool {
        switch mode {
        case .auto, .back: return true
        case .still: return false
        case .user:
            return autoEnabled || touching || active >= 0 || !pullSettled(now) || now - lastTouch < 1 || now < colourState.2 + 0.5
        }
    }

    var returnK: Double { mode == .back ? M.easeInOut(M.seg(now, modeAt, modeAt + Self.returnFor)) : 0 }

    /// The colour's name: (from, to, roll 0..1).
    var colour: (Int, Int, Double) {
        switch mode {
        case .auto: return M.loopColour(loopT)
        case .still: return (M.restColour, M.restColour, 1)
        case .back:
            let c = colourState
            return c.1 == M.restColour ? (c.1, c.1, 1) : (c.1, M.restColour, returnK)
        case .user:
            let c = colourState
            return (c.0, c.1, M.easeInOut(M.seg(now, c.2, c.2 + 0.35)))
        }
    }

    /// The user's pick, its phase now, and the swatches.
    func userFrame() -> (Int, CrayonBoxPickerPhase, (Int, Double, Int, Double)) {
        let t = now
        let phase: CrayonBoxPickerPhase
        if releaseAt > 0 && t >= releaseAt {
            let r = releaseAt
            phase = M.pickPhase(CrayonBoxPickerPick(crayon: active, times: [r - 1, r, r + 0.35, r + 1.35, r + 1.85, r + 2.25]), t)
        } else {
            phase = CrayonBoxPickerPhase(phase: 1, amount: pull(at: t), wobble: wobble)
        }
        let sw = swatchState
        let prog = M.seg(t, sw.2, sw.2 + 1.0)
        return (active, phase, (sw.0, 1 - prog, sw.1, prog))
    }

    /// The loop hands over: the colour and swatch it shows become the user's.
    private func takeOver() {
        let t = now
        lastTouch = t
        if mode == .user { return }
        var shown = M.restColour
        if mode == .auto {
            let c = M.loopColour(loopT)
            shown = c.2 >= 0.5 ? c.1 : c.0
            let s = M.loopSwatch(loopT)
            let sw = s.3 >= 0.5 ? s.2 : s.0
            swatchState = (sw, sw, -100)
        } else {
            swatchState = (M.restColour, M.restColour, -100)
        }
        colourState = (shown, shown, -100)
        active = -1
        releaseAt = -100
        pullAnim = .fixed(0)
        mode = .user
    }

    // MARK: Touch

    /// A finger comes down at a point; true when it is on a crayon.
    func begin(at p: CGPoint, geo g: CrayonBoxPickerGeo) -> Bool {
        if releaseAt > 0 { return false }
        let i = g.crayonAt(p.x, p.y)
        if i < 0 { return false }
        takeOver()
        touching = true
        active = i
        freed = false
        return true
    }

    /// The finger has moved; true when the crayon has just come free (a tick).
    func move(translationY: Double, velocityX: Double, geo g: CrayonBoxPickerGeo) -> Bool {
        guard touching, active >= 0 else { return false }
        let s = g.slot(active)
        let out = g.cl - s.show + 6
        // Friction inside the box, rubber-band once it is fully out.
        let raw = max(0, -translationY * 0.85)
        let over = max(0, raw - out)
        let shown = raw <= out ? raw : out + over / (1 + over / 60)
        pullAnim = .fixed(shown / out)
        wobble = max(-1, min(1, wobble * 0.7 + velocityX * 0.0012))
        lastTouch = now
        if !freed && raw >= out {
            freed = true
            return true
        }
        return false
    }

    /// The finger lifts; true when the crayon is picked.
    func end() -> Bool {
        defer {
            touching = false
            lastTouch = now
        }
        guard active >= 0, touching else { return false }
        let t = now
        let cur = colourState
        let shown = M.seg(t, cur.2, cur.2 + 0.35) >= 0.5 ? cur.1 : cur.0
        let p = pull(at: t)
        if p >= 0.75 {
            if reduced {
                colourState = (shown, active, t - 1)
                swatchState = (shown, active, t - 2)
                pullAnim = .fixed(0)
                return true
            }
            // Finish coming out (0.12 s), then the pick runs from its release.
            let r = t + 0.12
            pullAnim = .tween(from: p, start: t)
            releaseAt = r
            swatchState = (swatchState.1, active, r + 0.35)
            colourState = (shown, active, r + 1.35)
            return true
        }
        pullAnim = .spring(from: p, start: t)
        return false
    }
}
