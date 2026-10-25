import SwiftUI

/// The loop clock, the user's writing and the turntable, stepped once a frame from the timeline.
/// A plain reference type: nothing here is observed, the timeline's date drives every redraw.
final class IcingNameFormEngine {
    typealias M = IcingNameFormMotion

    enum Mode { case auto, user, back, still }

    static let returnFor = 0.8
    static let flowFor = 0.7

    // Layout, set once the hero is measured.
    private(set) var geo: IcingNameFormGeo?
    private(set) var restL = IcingNameFormLettering()
    private(set) var secondL = IcingNameFormLettering()
    private(set) var sched: (t0: [Double], t1: [Double]) = ([], [])
    private(set) var park: (Double, Double) = (0, 0)

    var artReady = false
    var autoEnabled = false
    var reduced = false

    private(set) var now = 0.0
    private(set) var loopT = 0.0
    private(set) var mode: Mode = .still
    private var modeAt = 0.0
    private var last: Date?
    private(set) var lastTouch = -100.0
    var focused = false
    var touching = false
    private(set) var turn = 0.0
    private var spin = 0.0
    private var turnFrom = 0.0
    private var grab = 0.0
    private(set) var pen: (Double, Double, Double) = (0, 0, 1)
    private var chipFrom = 0.0
    private var chipTo = 0.0
    private var chipAt = -100.0
    private var priceUser: (Double, Double, Double) = (4450, 4450, -100)
    private(set) var user: IcingNameFormUserWriting?

    /// What the cake shows right now, kept in step with the field and the chips.
    private var shown: (L: IcingNameFormLettering?, colour: Int, cents: Double) = (nil, 0, 4450)

    // Threshold events for the view (script text and chip, the reset on return).
    var pendingText: String?
    var pendingIcing: Int?
    private var lastScript = -1
    private var lastChip = -1

    func configure(_ g: IcingNameFormGeo) {
        guard g != geo else { return }
        geo = g
        restL = IcingNameFormFont.layout(M.restText, boxW: g.boxW, boxH: g.boxH)
        secondL = IcingNameFormFont.layout(M.secondText, boxW: g.boxW, boxH: g.boxH)
        sched = IcingNameFormFont.schedule(secondL)
        park = IcingNameFormScene.park(g)
        if user != nil {
            // A new size starts over from the loop's first frame.
            user = nil
            mode = autoEnabled ? .auto : .still
            loopT = 0
        }
    }

    private var shownText = M.restText

    func setMode(auto: Bool, reduced: Bool) {
        autoEnabled = auto
        self.reduced = reduced
        if mode != .user { mode = auto ? .auto : .still }
    }

    func updateShown(text: String, icing: Int) {
        shownText = text
        guard let g = geo else { return }
        shown = (IcingNameFormFont.layout(text, boxW: g.boxW, boxH: g.boxH), icing, M.priceFor(text, icing))
    }

    // MARK: Frame

    func step(_ date: Date) {
        let dt = min(0.05, max(0, date.timeIntervalSince(last ?? date)))
        last = date
        guard artReady, let g = geo else { return }
        let t = now + dt
        now = t
        if mode == .auto { loopT = (loopT + dt).truncatingRemainder(dividingBy: IcingNameForm.loop) }
        if mode == .user && autoEnabled && !focused && !touching && t - lastTouch > 2 {
            mode = .back
            modeAt = t
            // The turntable goes home by the shortest way.
            turnFrom = turn - (turn / (2 * .pi)).rounded() * 2 * .pi
            pendingText = M.restText
            pendingIcing = 0
        }
        if mode == .back {
            let k = M.easeInOut(M.seg(t, modeAt, modeAt + Self.returnFor))
            turn = turnFrom * (1 - k)
            spin = 0
            if k >= 1 {
                mode = .auto
                loopT = 0
                turn = 0
                lastScript = -1
                lastChip = -1
            }
        }
        if mode == .user && !touching {
            // The turntable coasts after a flick.
            turn += spin * dt
            spin *= exp(-dt * 2.4)
        }
        if mode == .user || mode == .back, let u = user {
            let fr = u.fracs(t, instant: reduced)
            let target = u.pen(fr, u.shift(t, axis: 0), u.shift(t, axis: 1), park: park)
            let k = 1 - exp(-dt * (target.2 > 0.5 ? 30 : 8))
            let kl = 1 - exp(-dt * 10)
            pen = (pen.0 + (target.0 - pen.0) * k, pen.1 + (target.1 - pen.1) * k, pen.2 + ((target.2 > 0.5 ? 0 : 1) - pen.2) * kl)
        }
        if mode == .auto {
            let i = M.scriptIndex(loopT)
            if i != lastScript {
                lastScript = i
                pendingText = M.textScript[i].1
            }
            let c = Int(M.chipPosition(loopT).rounded())
            if c != lastChip {
                lastChip = c
                pendingIcing = c
            }
        }
        _ = g
    }

    /// Whether the timeline must keep running.
    var wantsFrames: Bool {
        switch mode {
        case .auto, .back: return true
        case .still: return false
        case .user:
            if autoEnabled || touching || abs(spin) > 0.005 { return true }
            return now - lastTouch < 1.6 || abs(pen.2 - 1) > 0.01 && now - lastTouch < 4
        }
    }

    // MARK: Values for the controls

    private func chipSpring(_ t: Double) -> Double {
        if chipAt < -50 { return chipTo }
        // An underdamped spring (damping ratio 0.75) that settles in about 0.4 s.
        let zeta = 0.75, w0 = 15.3
        let wd = w0 * sqrt(1 - zeta * zeta)
        let u = max(0, t - chipAt)
        let e = exp(-zeta * w0 * u)
        return chipTo + (chipFrom - chipTo) * e * (cos(wd * u) + zeta * w0 / wd * sin(wd * u))
    }

    var chipPos: Double {
        switch mode {
        case .auto: return M.chipPosition(loopT)
        case .back: return chipSpring(now) * (1 - M.easeInOut(M.seg(now, modeAt, modeAt + Self.returnFor)))
        case .still: return 0
        case .user: return chipSpring(now)
        }
    }

    var price: (Double, Double, Double) {
        switch mode {
        case .auto: return M.priceAt(loopT)
        case .still: return (4450, 4450, 1)
        case .back: return (priceUser.1, 4450, M.easeInOut(M.seg(now, modeAt, modeAt + M.priceRoll)))
        case .user: return (priceUser.0, priceUser.1, M.easeInOut(M.seg(now, priceUser.2, priceUser.2 + M.priceRoll)))
        }
    }

    /// 0 while the user's writing shows, 1 once the return has cross-faded to the loop.
    var returnK: Double { mode == .back ? M.easeInOut(M.seg(now, modeAt, modeAt + Self.returnFor)) : 0 }

    // MARK: Touch

    /// Hands the screen to the user with whatever message the cake shows.
    func takeOver() {
        lastTouch = now
        if mode == .user && user != nil { return }
        guard let L = shown.L else { return }
        let n = L.firstStroke.count
        user = IcingNameFormUserWriting(
            L: L, typedAt: Array(repeating: -100, count: n), fromX: Array(repeating: 0, count: n),
            fromY: Array(repeating: 0, count: n), movedAt: -100, colorFrom: shown.colour, colorTo: shown.colour, colorAt: -100
        )
        chipFrom = Double(shown.colour)
        chipTo = Double(shown.colour)
        chipAt = -100
        priceUser = (shown.cents, shown.cents, -100)
        pen = (park.0, park.1, 1)
        if mode != .back { turn = 0 }
        mode = .user
    }

    func type(_ next: String, prev prevText: String, icing: Int) {
        guard let g = geo else { return }
        let t = now
        let prev = user
        let L = IcingNameFormFont.layout(next, boxW: g.boxW, boxH: g.boxH)
        let prevChars = Array(prevText)
        let chars = Array(next)
        let k = prev.map { 1 - M.easeInOut(M.seg(t, $0.movedAt, $0.movedAt + IcingNameFormUserWriting.glide)) } ?? 0
        var typedAt: [Double] = []
        var fromX: [Double] = []
        var fromY: [Double] = []
        for i in chars.indices {
            if let p = prev, i < p.L.originX.count, i < prevChars.count, prevChars[i] == chars[i], abs(p.L.size - L.size) < 0.5 {
                typedAt.append(p.typedAt[i])
                fromX.append(p.L.originX[i] + p.fromX[i] * k - L.originX[i])
                fromY.append(p.L.originY[i] + p.fromY[i] * k - L.originY[i])
            } else {
                typedAt.append(t)
                fromX.append(0)
                fromY.append(0)
            }
        }
        let colour = prev?.colorTo ?? icing
        user = IcingNameFormUserWriting(
            L: L, typedAt: typedAt, fromX: fromX, fromY: fromY, movedAt: t,
            colorFrom: prev?.colorFrom ?? colour, colorTo: colour, colorAt: prev?.colorAt ?? -100
        )
        priceUser = (M.priceFor(prevText, colour), M.priceFor(next, colour), t)
        lastTouch = t
    }

    func pick(_ i: Int, text: String, previous: Int) {
        if mode != .user { takeOver() }
        let t = now
        guard var u = user else { return }
        let flowDone = t - u.colorAt >= Self.flowFor
        u.colorFrom = flowDone ? u.colorTo : u.colorFrom
        u.colorTo = i
        u.colorAt = t
        user = u
        chipFrom = chipSpring(t)
        chipTo = Double(i)
        chipAt = t
        priceUser = (M.priceFor(text, previous), M.priceFor(text, i), t)
        lastTouch = t
    }

    func focusChanged(_ on: Bool) {
        focused = on
        if on, mode != .user { takeOver() }
        lastTouch = now
    }

    func dragBegan(at p: CGPoint) {
        guard let g = geo else { return }
        touching = true
        grab = atan2(p.y - g.cy, p.x - g.cx)
        spin = 0
        lastTouch = now
    }

    func dragMoved(to p: CGPoint, started: Bool) {
        guard let g = geo else { return }
        if started, mode != .user { takeOver() }
        let ang = atan2(p.y - g.cy, p.x - g.cx)
        var d = ang - grab
        if d > .pi { d -= 2 * .pi }
        if d < -.pi { d += 2 * .pi }
        grab = ang
        if mode == .user { turn += d }
        lastTouch = now
    }

    func dragEnded(at p: CGPoint, velocity v: CGSize) {
        if let g = geo, mode == .user {
            // Angular speed from the finger's velocity across the radius.
            let rx = p.x - g.cx
            let ry = p.y - g.cy
            let r2 = max(400, rx * rx + ry * ry)
            spin = max(-9, min(9, (rx * v.height - ry * v.width) / r2))
        }
        touching = false
        lastTouch = now
    }
}
