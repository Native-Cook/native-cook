import UIKit

/// What the screen shows at one moment.
struct TasteProfileFrame {
    var change: TasteProfileMotion.Change
    var sx: Double
    var sk: Double
    var hold: TasteProfileMotion.HoldView
    var followProgress: Double
    var followPress: Double
    var following: Bool
}

/// A value that is constant, eased over time, or a duration-based spring: a closed-form function of time.
struct TasteProfileAnim {
    enum Kind {
        case constant
        case timing(duration: Double, inOut: Bool)
        case spring(omega: Double, zeta: Double, v0: Double, settle: Double)
    }

    var from = 0.0
    var to = 0.0
    var t0 = 0.0
    var kind = Kind.constant

    static func constant(_ value: Double) -> TasteProfileAnim { TasteProfileAnim(from: value, to: value) }

    func value(_ now: Double) -> Double {
        switch kind {
        case .constant:
            return to
        case let .timing(duration, inOut):
            let p = TasteProfileMotion.segment(now, t0, t0 + duration)
            return TasteProfileMotion.lerp(from, to, inOut ? TasteProfileMotion.easeInOut(p) : TasteProfileMotion.easeOut(p))
        case let .spring(omega, zeta, v0, settle):
            return TasteProfileMotion.durationSpring(start: from, target: to, velocity: v0, elapsed: now - t0,
                                                     omega: omega, zeta: zeta, settleTime: settle).value
        }
    }
}

/// The clock and the touch state of the screen. It mirrors the React Native frame callback: in autoPlay the
/// screen is a function of the loop time; after a touch it is a function of the time since that touch, and
/// two seconds after the last touch the loop takes over again.
final class TasteProfileEngine {
    typealias M = TasteProfileMotion

    struct Rec {
        var from = 0
        var to = 0
        var start = -1e3
        var l0 = TasteProfileTheme.Box.chips[0].x
        var r0 = TasteProfileTheme.Box.chips[0].x + TasteProfileTheme.Box.chips[0].w
        var a0 = 0.0
        var back0 = 0
        var x0 = 0.0
    }

    struct PressRec {
        var chip = -1
        var t0 = 0.0
        var t1 = 0.0
    }

    private struct FollowRecord {
        var start = -1000.0
        var from = 0.0
        var velocity = 0.0
        var target = 0.0
    }

    private(set) var looping = false
    private var configured = false
    private var reduced = false
    private var auto = false
    private var active = true
    private var loopBase = 0.0
    private var loopSince = 0.0
    private var rec = Rec()
    private var press = PressRec()
    private var sx = TasteProfileAnim.constant(0)
    private var sk = TasteProfileAnim.constant(0)
    private var hl = TasteProfileAnim.constant(5)
    private var flatAt = -1.0
    private var dragFrom = 0.0
    private var holdRec = M.Hold.none
    private var pendingHold = false
    private(set) var holding = false
    private var touchedAt = -1e3
    private var follow = FollowRecord()
    private var followPressRec = PressRec()
    private var busyUntil = 0.0
    private var cellPoints = 1.0
    private var cardX = [0.0, 0.0, 0.0, 0.0]
    private var cardUnit = 1.0

    private var selection: UISelectionFeedbackGenerator?
    private var impact: UIImpactFeedbackGenerator?

    // MARK: Configuration

    func configure(looping newLooping: Bool, reduced newReduced: Bool, now: Double) {
        reduced = newReduced
        if !configured {
            configured = true
            looping = newLooping
            auto = newLooping
            loopBase = 0
            loopSince = now
            return
        }
        if newLooping == looping { return }
        looping = newLooping
        busyUntil = now + 2
        guard !newLooping, auto else { return }
        let t = loopTime(now)
        let c = M.loopChange(t)
        let st = M.stripAt(t)
        let shown = c.tau >= 0 ? c.to : c.from
        rec = changeFrom(c, to: shown, start: now - M.settle, x0: st.x.rounded())
        sx = .constant(st.x.rounded())
        sk = .constant(0)
        auto = false
    }

    func setGeometry(cell: Double, unit: Double, tileCardX: [Double]) {
        cellPoints = cell
        cardUnit = unit
        cardX = tileCardX
    }

    func setActive(_ isActive: Bool, now: Double) {
        if isActive == active { return }
        if !isActive {
            loopBase = loopTime(now)
            active = false
        } else {
            loopSince = now
            active = true
        }
    }

    private func loopTime(_ now: Double) -> Double {
        guard active, auto else { return loopBase }
        let t = (loopBase + now - loopSince).truncatingRemainder(dividingBy: M.loop)
        return t < 0 ? t + M.loop : t
    }

    private func setLoop(_ t: Double, now: Double) {
        loopBase = t
        loopSince = now
    }

    func busy(at now: Double) -> Bool {
        if looping { return true }
        if holding || pendingHold { return true }
        return now < busyUntil
    }

    // MARK: Frame

    private func changeFrom(_ c: M.Change, to: Int, start: Double, x0: Double) -> Rec {
        let edges = M.pill(c)
        let coin = M.coin(c)
        return Rec(from: c.tau >= 0 ? c.to : c.from, to: to, start: start, l0: edges.0, r0: edges.1,
                   a0: coin.angle, back0: coin.back, x0: x0)
    }

    private func manualChange(_ now: Double) -> M.Change {
        M.Change(from: rec.from, to: rec.to, tau: now - rec.start, l0: rec.l0, r0: rec.r0, a0: rec.a0, back0: rec.back0,
                 x0: rec.x0, pressChip: press.chip,
                 press: press.chip >= 0 ? M.press(at: now, down: press.t0, up: press.t1) : 0, reduced: reduced)
    }

    func frame(at now: Double) -> TasteProfileFrame {
        var c: M.Change
        var x: Double
        var k: Double
        var hv: M.HoldView
        if auto {
            let t = loopTime(now)
            c = M.loopChange(t)
            let st = M.stripAt(t)
            x = st.x
            k = st.k
            let hs = M.holdAt(t, .script)
            hv = M.holdView(t, .script, hl: M.scriptHl(hs.since), reduced: false)
        } else {
            if pendingHold, holding, holdRec.tile >= 0, now >= holdRec.t0 + M.Expand.hold {
                holdRec.t1 = holdRec.t0 + M.Expand.hold
                pendingHold = false
                lightImpact()
            }
            c = manualChange(now)
            hv = M.holdView(now, holdRec, hl: hl.value(now), reduced: reduced)
            if flatAt >= 0, now >= flatAt, !holding {
                flatAt = -1
                sk = TasteProfileAnim(from: sk.value(now), to: 0, t0: now, kind: .timing(duration: M.Cover.outDuration, inOut: true))
            }
            x = sx.value(now)
            k = sk.value(now)
            handBack(now: now, change: c, x: x, k: k, hold: hv)
        }
        let follow = followState(at: now)
        let pressAmount = followPressRec.chip >= 0 ? M.press(at: now, down: followPressRec.t0, up: followPressRec.t1) : 0
        return TasteProfileFrame(change: c, sx: x, sk: k, hold: hv, followProgress: follow.value, followPress: pressAmount,
                                 following: self.follow.target > 0.5)
    }

    /// Two seconds after the last touch, once the screen has settled, the loop takes over again.
    private func handBack(now: Double, change c: M.Change, x: Double, k: Double, hold hv: M.HoldView) {
        let still = k == 0 && abs(x - x.rounded()) < 1e-3 && hv.tile < 0
        guard looping, still, !holding, now - touchedAt >= M.resumeAfter, c.tau >= M.settle, c.press == 0 else { return }
        if follow.target > 0.5 { setFollow(false, now: now) }
        holdRec = .none
        hl = .constant(5)
        let rounded = x.rounded()
        if rec.to == 0, rounded == 0 {
            auto = true
            setLoop(0, now: now)
        } else if rec.to == M.script[0].city, rounded == 0 {
            auto = true
            setLoop(M.strip.down - 0.1, now: now)
        } else if rec.to == M.script[0].city, rounded == Double(M.strip.to) {
            auto = true
            setLoop(M.Hold.script.t0 - 0.1, now: now)
        } else {
            press = PressRec(chip: 0, t0: now, t1: now + M.Press.inside)
            rec = changeFrom(c, to: 0, start: now + M.Press.inside, x0: rounded)
            sx = .constant(0)
        }
    }

    // MARK: Taking over from the loop

    private func takeOver(_ now: Double) {
        guard auto else { return }
        let t = loopTime(now)
        let c = M.loopChange(t)
        let st = M.stripAt(t)
        rec = Rec(from: c.from, to: c.to, start: now - c.tau, l0: c.l0, r0: c.r0, a0: c.a0, back0: c.back0, x0: c.x0)
        press = PressRec()
        let h = M.Hold.script
        if t >= h.t0, t < h.t2 + M.Expand.out {
            let hs = M.holdAt(t, h)
            holdRec = M.Hold(tile: h.tile, t0: now - (t - h.t0), t1: t >= h.t1 ? now - (t - h.t1) : -1,
                             t2: t < h.t2 ? now : now - (t - h.t2))
            hl = .constant(M.scriptHl(hs.since))
        } else {
            holdRec = .none
        }
        sk = .constant(st.k)
        if st.k > 0 {
            let omega = M.durationOmega(duration: M.Cover.takeoverDuration, zeta: M.Cover.snapZeta)
            sx = TasteProfileAnim(from: st.x, to: st.x.rounded(), t0: now,
                                  kind: .spring(omega: omega, zeta: M.Cover.snapZeta, v0: 0, settle: M.Cover.takeoverDuration * 1.5))
            flatAt = now + 0.3
        } else {
            sx = .constant(st.x)
        }
        auto = false
    }

    private func touched(_ now: Double) {
        touchedAt = now
        busyUntil = max(busyUntil, now + 2)
    }

    // MARK: Chips

    func chipDown(_ index: Int, now: Double) {
        takeOver(now)
        holding = true
        touched(now)
        if !reduced { press = PressRec(chip: index, t0: now, t1: 1e9) }
    }

    func chipUp(now: Double) {
        holding = false
        touched(now)
        if press.chip >= 0, press.t1 > now { press.t1 = now }
    }

    @discardableResult
    func chipTap(_ index: Int, now: Double) -> Bool {
        takeOver(now)
        touched(now)
        let c = manualChange(now)
        let shown = c.tau >= 0 ? c.to : c.from
        if index == shown, index == c.to { return false }
        let x0 = sx.value(now).rounded()
        sx = .constant(0)
        sk = .constant(0)
        flatAt = -1
        rec = changeFrom(c, to: index, start: now, x0: x0)
        selectionHaptic()
        return true
    }

    // MARK: Photo strip

    func stripBegin(now: Double) {
        takeOver(now)
        holding = true
        touched(now)
    }

    func stripStart(now: Double) {
        let current = sx.value(now)
        sx = .constant(current)
        dragFrom = current
        if !reduced {
            sk = TasteProfileAnim(from: sk.value(now), to: 1, t0: now, kind: .timing(duration: M.Cover.inDuration, inOut: false))
        }
        touched(now)
    }

    private func stripMax() -> Double { Double(M.stripPhotos[rec.to].count - 4) }

    func stripMove(translation: Double, now: Double) {
        let max = stripMax()
        let raw = dragFrom - translation / cellPoints
        let x = raw < 0 ? raw * M.Cover.rubber : raw > max ? max + (raw - max) * M.Cover.rubber : raw
        sx = .constant(x)
        touched(now)
    }

    func stripEnd(velocity: Double, now: Double) {
        let max = stripMax()
        let v = -velocity / cellPoints
        let from = dragFrom.rounded()
        let current = sx.value(now)
        let target = min(max, Swift.max(0, (current + v * M.Cover.project).rounded()))
        let omega = M.durationOmega(duration: M.Cover.snapDuration, zeta: M.Cover.snapZeta)
        sx = TasteProfileAnim(from: current, to: target, t0: now,
                              kind: .spring(omega: omega, zeta: M.Cover.snapZeta, v0: v, settle: M.Cover.snapDuration * 1.5))
        flatAt = now + 0.42
        touched(now)
        if target != from { selectionHaptic() }
    }

    func stripFinalize(now: Double) {
        holding = false
        touched(now)
        if sk.value(now) > 0, flatAt < 0 { flatAt = now }
    }

    // MARK: Tiles

    func tileDown(_ tile: Int, now: Double) {
        takeOver(now)
        holding = true
        touched(now)
        hl = .constant(5)
        holdRec = M.Hold(tile: tile, t0: now, t1: -1, t2: 1e9)
        pendingHold = true
    }

    var tileGrown: Bool { holdRec.tile >= 0 && holdRec.t1 >= 0 }

    func tileMove(x pointX: Double, tile: Int, now: Double) {
        guard tileGrown else { return }
        let xRef = pointX / cardUnit - cardX[tile]
        let index = Double(TasteProfileEngine.barUnder(xRef))
        let current = hl.value(now)
        if current != index {
            if current.rounded() != index { selectionHaptic() }
            hl = TasteProfileAnim(from: current, to: index, t0: now, kind: .timing(duration: M.Expand.hlDuration, inOut: false))
        }
        touched(now)
    }

    func tileEnd(now: Double) {
        holdRec.t2 = now
        pendingHold = false
        holding = false
        touched(now)
    }

    static func barUnder(_ xRef: Double) -> Int {
        let chart = TasteProfileTheme.Chart.self
        let gap = (chart.w - 2 * chart.pad - 6 * chart.barW) / 5
        let i = ((xRef - chart.pad - chart.barW / 2) / (chart.barW + gap)).rounded()
        return Int(max(0, min(5, i)))
    }

    // MARK: Follow

    private func followState(at now: Double) -> (value: Double, velocity: Double) {
        M.followSpring(start: follow.from, target: follow.target, velocity: follow.velocity, elapsed: now - follow.start)
    }

    private func setFollow(_ on: Bool, now: Double) {
        let state = followState(at: now)
        let target = on ? 1.0 : 0.0
        if reduced {
            follow = FollowRecord(start: now - M.Follow.settleTime, from: target, velocity: 0, target: target)
        } else {
            var velocity = state.velocity
            if (target > state.value && velocity < 0) || (target < state.value && velocity > 0) { velocity = 0 }
            follow = FollowRecord(start: now, from: state.value, velocity: velocity, target: target)
        }
        busyUntil = max(busyUntil, now + 2)
    }

    func followDown(now: Double) {
        if !reduced { followPressRec = PressRec(chip: 0, t0: now, t1: 1e9) }
        busyUntil = max(busyUntil, now + 2)
    }

    func followUp(now: Double) {
        if followPressRec.chip >= 0, followPressRec.t1 > now { followPressRec.t1 = now }
    }

    func followTap(now: Double) {
        setFollow(follow.target <= 0.5, now: now)
        lightImpact()
    }

    // MARK: Haptics

    private func selectionHaptic() {
        if selection == nil { selection = UISelectionFeedbackGenerator() }
        selection?.selectionChanged()
    }

    private func lightImpact() {
        if impact == nil { impact = UIImpactFeedbackGenerator(style: .light) }
        impact?.impactOccurred()
    }
}
