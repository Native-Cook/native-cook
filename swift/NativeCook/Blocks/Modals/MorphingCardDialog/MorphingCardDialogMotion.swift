import Foundation

/// Every number the morph uses, and the two ways its state is made: `autoState(at:)`, a pure
/// function of the loop time (closed-form damped springs sampled from the script's event times),
/// and `MorphingCardDialogLive`, the same springs stepped under a finger.
enum MorphingCardDialogMotion {
    /// Seconds. One autoPlay loop; rest at both ends.
    static let loop: Double = 9

    /// The frame's spring: response 0.42 s, damping ratio 0.82 (mass 1).
    static let springResponse = 0.42
    static let springDampingRatio = 0.82
    static let stiffness = pow(2 * Double.pi / springResponse, 2) // ≈ 223.8
    static let damping = 2 * springDampingRatio * stiffness.squareRoot() // ≈ 24.5
    /// The live spring is stepped in slices no longer than this.
    static let substep = 1.0 / 240

    /// Follow-through: title and "+" trail the frame by 45 ms, the subtitle by 75 ms (first-order lag).
    static let followTitle = 0.045
    static let followSubtitle = 0.075

    /// The detail content has its own clock: 0 → 1 in `contentIn` while open, back in `contentOut`.
    static let contentIn = 0.62
    static let contentOut = 0.16
    /// Where on that clock body, facts and button start, and how long each takes (share of the clock).
    static let contentStart = [0.1, 0.22, 0.34]
    static let contentSpan = 0.6
    static let contentRise = 14.0 // pt
    static let contentBlur = 8.0 // pt
    /// The content is also tied to the frame, so a drag that shrinks the dialog fades it.
    static let contentGate = (0.62, 0.92)

    static let radiusCard = 20.0
    static let radiusDialog = 28.0

    /// Behind the dialog.
    static let sceneScale = 0.96
    static let sceneDim = 0.34
    static let sceneBlur = 14.0 // light blur effect, per cent

    /// Drag: the finger's travel is rubber-banded, then shrinks the dialog toward its card.
    static let dragRubber = 260.0
    static let dragSpan = 360.0
    static let dragFollow = 0.55
    static let dragSideRubber = 120.0
    static let dragUpRubber = 90.0
    static let dragCloseDistance = 120.0
    static let dragCloseVelocity = 650.0
    static let dragSlop = 6.0

    static let pressScale = 0.97
    static let pressTime = 0.12

    /// autoPlay: 2 s after the last touch the dialog closes and the loop restarts at 0.
    static let resumeAfter = 2.0

    /// The loop's events, in seconds. A and B are the two cards the ghost finger opens.
    static let cardA = 1
    static let cardB = 4
    static let tapA = 1.3
    static let dragStart = 3.55
    static let dragEnd = 4.15
    static let tapB = 5.7
    static let tapClose = 7.5
    /// A ghost tap: the dot fades in, presses, lifts at `at`, fades out. Offsets from `at`.
    static let tapAppear = -0.4, tapShown = -0.22, tapDown = -0.15, tapPressed = -0.07, tapUp = 0.1, tapGone = 0.24
    /// The ghost drag: raw finger travel in pt, eased so it lets go while still moving.
    static let ghostPullDistance = 170.0
    static let ghostDrift = 14.0
    static let ghostAppear = 0.3

    // MARK: Helpers

    static func clamp(_ value: Double, _ low: Double, _ high: Double) -> Double { min(max(value, low), high) }
    static func mix(_ from: Double, _ to: Double, _ amount: Double) -> Double { from + (to - from) * amount }
    /// 0 before a, 1 after b.
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }
    static func easeOutCubic(_ u: Double) -> Double { let k = 1 - u; return 1 - k * k * k }
    static func easeInOutQuad(_ u: Double) -> Double { u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u) }
    /// Rubber band: follows at first, then resists.
    static func rubber(_ distance: Double, _ limit: Double) -> Double { distance / (1 + distance / limit) }
    static func rubberSlope(_ distance: Double, _ limit: Double) -> Double { let k = 1 + distance / limit; return 1 / (k * k) }
    /// The ghost drag's raw travel (0…1) at u = 0…1: it speeds up, so the release carries velocity.
    static func ghostPull(_ u: Double) -> Double { 0.35 * u + 0.65 * u * u }
    static func ghostPullSpeed(_ u: Double) -> Double { 0.35 + 1.3 * u }
    static func tapGhost(_ t: Double, _ at: Double) -> Double {
        seg(t, at + tapAppear, at + tapShown) * (1 - seg(t, at + 0.02, at + tapGone))
    }
    static func tapPress(_ t: Double, _ at: Double) -> Double {
        seg(t, at + tapDown, at + tapPressed) * (1 - seg(t, at, at + tapUp))
    }

    /// The damped spring in closed form: where it is and how fast, `t` seconds after it left `x0` at `v0`.
    static func spring(_ t: Double, from x0: Double, velocity v0: Double, to goal: Double) -> (x: Double, v: Double) {
        let a = damping / 2
        let w = (stiffness - a * a).squareRoot()
        let y0 = x0 - goal
        let b = (v0 + a * y0) / w
        let e = exp(-a * t), c = cos(w * t), s = sin(w * t)
        return (goal + e * (y0 * c + b * s), e * ((b * w - a * y0) * c - (a * b + y0 * w) * s))
    }

    // MARK: The autoPlay journey as a function of time

    struct Frame {
        var p = 0.0, v = 0.0, goal = 0.0
        var held = false
        var dragX = 0.0, dragY = 0.0, dragVX = 0.0, dragVY = 0.0
        var pulled = 0.0
    }

    /// The scripted drag's last instant: what the springs start from when the ghost lets go.
    private static let release: Frame = {
        let span = dragEnd - dragStart
        let banded = rubber(ghostPullDistance, dragRubber)
        let slope = rubberSlope(ghostPullDistance, dragRubber)
        let vy = ghostPullDistance * ghostPullSpeed(1) / span
        let x = rubber(ghostDrift, dragSideRubber) * dragFollow
        var f = Frame()
        f.p = 1 - banded / dragSpan
        f.v = -vy * slope / dragSpan
        f.dragY = banded * dragFollow
        f.dragVY = vy * slope * dragFollow
        f.dragX = x
        f.dragVX = ghostDrift / span * rubberSlope(x, dragSideRubber) * dragFollow
        return f
    }()

    private static func settled(_ s: (x: Double, v: Double), _ goal: Double) -> (Double, Double) {
        abs(goal - s.x) < 0.0004 && abs(s.v) < 0.004 ? (goal, 0) : (s.x, s.v)
    }

    private static func home(_ s: (x: Double, v: Double)) -> (Double, Double) {
        abs(s.x) < 0.05 && abs(s.v) < 0.5 ? (0, 0) : (s.x, s.v)
    }

    /// Frame progress, its speed and the drag offset at loop time `t`.
    static func autoFrame(_ t: Double) -> Frame {
        var f = Frame()
        if t < tapA { return f }
        if t < dragStart {
            f.goal = 1
            (f.p, f.v) = settled(spring(t - tapA, from: 0, velocity: 0, to: 1), 1)
        } else if t < dragEnd {
            let span = dragEnd - dragStart
            let u = (t - dragStart) / span
            let down = ghostPullDistance * ghostPull(u)
            let banded = rubber(down, dragRubber)
            let slope = rubberSlope(down, dragRubber)
            let vy = ghostPullDistance * ghostPullSpeed(u) / span
            f.goal = 1
            f.held = true
            f.pulled = down
            f.p = 1 - banded / dragSpan
            f.v = -vy * slope / dragSpan
            f.dragY = banded * dragFollow
            f.dragVY = vy * slope * dragFollow
            f.dragX = rubber(ghostDrift * u, dragSideRubber) * dragFollow
            f.dragVX = ghostDrift / span * rubberSlope(ghostDrift * u, dragSideRubber) * dragFollow
        } else if t < tapB {
            let r = release, dt = t - dragEnd
            (f.p, f.v) = settled(spring(dt, from: r.p, velocity: r.v, to: 0), 0)
            (f.dragX, f.dragVX) = home(spring(dt, from: r.dragX, velocity: r.dragVX, to: 0))
            (f.dragY, f.dragVY) = home(spring(dt, from: r.dragY, velocity: r.dragVY, to: 0))
        } else if t < tapClose {
            f.goal = 1
            (f.p, f.v) = settled(spring(t - tapB, from: 0, velocity: 0, to: 1), 1)
        } else {
            (f.p, f.v) = settled(spring(t - tapClose, from: 1, velocity: 0, to: 0), 0)
        }
        return f
    }

    /// The two follow-through values are a lag of the frame's progress: integrated once over the
    /// loop from the closed-form progress and read back by time, with the moments each dialog is
    /// home in its card again.
    private static let rate = 480.0
    private static let follow: (title: [Double], subtitle: [Double], homeA: Double, homeB: Double) = {
        let count = Int(loop * rate)
        let h = 1 / rate
        let kt = 1 - exp(-h / followTitle), ks = 1 - exp(-h / followSubtitle)
        var title = [Double](repeating: 0, count: count + 1)
        var subtitle = title
        var tf = 0.0, sf = 0.0
        var homeA = tapB, homeB = loop
        var foundA = false, foundB = false
        for i in 1...count {
            let t = Double(i) * h
            let f = autoFrame(t)
            tf += (f.p - tf) * kt
            sf += (f.p - sf) * ks
            if f.goal == 0, !f.held, f.p == 0, abs(tf) < 0.003, abs(sf) < 0.003, f.dragX == 0, f.dragY == 0 {
                tf = 0
                sf = 0
                if t > dragEnd, t < tapB, !foundA { homeA = t; foundA = true }
                if t > tapClose, !foundB { homeB = t; foundB = true }
            }
            title[i] = tf
            subtitle[i] = sf
        }
        return (title, subtitle, homeA, homeB)
    }()

    private static func sample(_ values: [Double], _ t: Double) -> Double {
        let x = clamp(t * rate, 0, Double(values.count - 1))
        let i = min(Int(x), values.count - 2)
        return mix(values[i], values[i + 1], x - Double(i))
    }

    /// The press on a card for a ghost tap that lifts at `at`: in over 120 ms from the finger's
    /// landing, out over 120 ms from its lift.
    private static func cardPress(_ t: Double, _ at: Double) -> Double {
        t < at ? easeInOutQuad(seg(t, at + tapDown, at + tapDown + pressTime)) : 1 - easeInOutQuad(seg(t, at, at + pressTime))
    }

    /// Everything the screen draws at loop time `t` (0 ..< loop).
    static func autoState(at t: Double) -> MorphingCardDialogState {
        let f = autoFrame(t)
        var s = MorphingCardDialogState()
        s.p = f.p
        s.dragX = f.dragX
        s.dragY = f.dragY
        s.titleFollow = sample(follow.title, t)
        s.subtitleFollow = sample(follow.subtitle, t)
        if t >= tapA, t < follow.homeA { s.active = cardA }
        if t >= tapB, t < follow.homeB { s.active = cardB }

        // The content's clock.
        if t >= tapA, t < dragStart {
            s.content = clamp((t - tapA) / contentIn, 0, 1)
        } else if t >= dragStart, t < tapB {
            s.content = clamp(1 - (t - dragStart) / contentOut, 0, 1)
        } else if t >= tapB, t < tapClose {
            s.content = clamp((t - tapB) / contentIn, 0, 1)
        } else if t >= tapClose {
            s.content = clamp(1 - (t - tapClose) / contentOut, 0, 1)
        }

        // Presses.
        if t >= tapB + tapDown {
            s.pressed = cardB
            s.pressAmount = cardPress(t, tapB)
        } else if t >= tapA + tapDown {
            s.pressed = cardA
            s.pressAmount = cardPress(t, tapA)
        }
        s.closePress = t >= tapClose + tapDown && t < tapClose ? 1 : 0

        // The ghost finger.
        var ghost = MorphingCardDialogGhost(spot: .card(cardA), opacity: tapGhost(t, tapA), press: tapPress(t, tapA))
        let landed = dragStart - ghostAppear
        let onDrag = seg(t, landed, landed + 0.18) * (1 - seg(t, dragEnd, dragEnd + 0.2))
        let onB = tapGhost(t, tapB)
        let onClose = tapGhost(t, tapClose)
        if onDrag > 0 {
            let u = seg(t, dragStart, dragEnd)
            ghost = MorphingCardDialogGhost(
                spot: .grab, dx: ghostDrift * u, dy: ghostPullDistance * ghostPull(u), opacity: onDrag,
                press: seg(t, dragStart - 0.1, dragStart) * (1 - seg(t, dragEnd, dragEnd + 0.1)))
        } else if onB > 0 {
            ghost = MorphingCardDialogGhost(spot: .card(cardB), opacity: onB, press: tapPress(t, tapB))
        } else if onClose > 0 {
            ghost = MorphingCardDialogGhost(spot: .close, opacity: onClose, press: tapPress(t, tapClose))
        }
        s.ghost = ghost
        return s
    }
}

/// What the screen draws from: the same values whether the loop or a finger made them.
struct MorphingCardDialogState {
    /// 0 = the card's rect, 1 = the dialog's. Overshoots a little on its spring.
    var p = 0.0
    /// `p`, 45 ms and 75 ms late.
    var titleFollow = 0.0
    var subtitleFollow = 0.0
    /// The detail content's own clock, 0…1.
    var content = 0.0
    /// The frame's offset under the finger.
    var dragX = 0.0
    var dragY = 0.0
    /// Index of the card the frame came from; -1 while nothing is open.
    var active = -1
    /// Which card is pressed and how far in (0…1); the same for the two buttons inside.
    var pressed = -1
    var pressAmount = 0.0
    var closePress = 0.0
    var actionPress = 0.0
    var ghost: MorphingCardDialogGhost?
}

struct MorphingCardDialogGhost {
    enum Spot { case card(Int), grab, close }
    var spot: Spot
    var dx = 0.0
    var dy = 0.0
    var opacity: Double
    var press: Double
}

/// The morph under a finger: the springs stepped in time, as the React Native controller does.
struct MorphingCardDialogLive {
    private typealias M = MorphingCardDialogMotion

    var state = MorphingCardDialogState()
    var velocity = 0.0
    var goal = 0.0
    var dragVX = 0.0, dragVY = 0.0
    var dragging = false
    var fingers = 0
    private var grabProgress = 1.0, grabX = 0.0, grabY = 0.0, pulled = 0.0
    private var pressTarget = 0.0, closeTarget = 0.0, actionTarget = 0.0

    /// Nothing open, nothing pressed, nothing moving.
    var resting: Bool {
        state.active < 0 && fingers == 0 && state.pressAmount == 0 && state.closePress == 0 && state.actionPress == 0
    }

    /// Carries on from wherever the loop was at `t`; a scripted drag in progress is let go.
    init(taking t: Double) {
        let f = M.autoFrame(t)
        state = M.autoState(at: t)
        state.ghost = nil
        state.closePress = 0
        velocity = f.held ? 0 : f.v
        goal = f.goal
        dragVX = f.held ? 0 : f.dragVX
        dragVY = f.held ? 0 : f.dragVY
        if f.held, f.pulled > M.dragCloseDistance { goal = 0 }
    }

    init() {}

    mutating func pressIn(_ index: Int) {
        state.pressed = index
        pressTarget = 1
    }

    mutating func pressOut() { pressTarget = 0 }
    mutating func pressClose(_ on: Bool) { closeTarget = on ? 1 : 0 }
    mutating func pressAction(_ on: Bool) { actionTarget = on ? 1 : 0 }

    /// Grows the frame out of card `index`. False while another card's dialog is still on screen.
    mutating func open(_ index: Int) -> Bool {
        let current = state.active
        if current >= 0, current != index { return false }
        if current < 0 {
            state.p = 0
            velocity = 0
            state.titleFollow = 0
            state.subtitleFollow = 0
            state.content = 0
            state.dragX = 0
            state.dragY = 0
            dragVX = 0
            dragVY = 0
        }
        state.active = index
        goal = 1
        return true
    }

    /// Sends the frame back to its card, keeping whatever speed it has plus `kick` (progress per second).
    mutating func close(_ kick: Double) {
        guard state.active >= 0 else { return }
        dragging = false
        velocity += kick
        goal = 0
    }

    /// Catches the frame wherever it is, mid-spring included.
    mutating func dragBegin() {
        dragging = true
        grabProgress = state.p
        grabX = state.dragX
        grabY = state.dragY
        pulled = 0
        velocity = 0
        dragVX = 0
        dragVY = 0
    }

    /// The finger has moved (x, y) since dragBegin: down shrinks toward the card, the rest stretches.
    mutating func dragMove(_ x: Double, _ y: Double) {
        let down = max(y, 0), up = max(-y, 0)
        let banded = M.rubber(down, M.dragRubber)
        pulled = down
        state.p = max(grabProgress - banded / M.dragSpan, 0)
        state.dragY = grabY + banded * M.dragFollow - M.rubber(up, M.dragUpRubber) * M.dragFollow
        state.dragX = grabX + (x < 0 ? -1 : 1) * M.rubber(abs(x), M.dragSideRubber) * M.dragFollow
    }

    /// Lets go with the finger's velocity (pt/s). True when the dialog closes.
    mutating func dragEnd(_ vx: Double, _ vy: Double) -> Bool {
        guard dragging else { return false }
        dragging = false
        let slope = M.rubberSlope(pulled, M.dragRubber)
        // The finger's speed becomes the springs' starting speed.
        velocity = vy > 0 || pulled > 0 ? -vy * slope / M.dragSpan : 0
        dragVY = vy * slope * M.dragFollow
        dragVX = vx * M.rubberSlope(abs(state.dragX - grabX), M.dragSideRubber) * M.dragFollow
        let closes = vy > M.dragCloseVelocity || pulled > M.dragCloseDistance
        goal = closes ? 0 : 1
        return closes
    }

    mutating func step(_ dt: Double, reducedMotion: Bool) {
        guard dt > 0 else { return }
        let rate = dt / M.pressTime
        state.pressAmount += M.clamp(pressTarget - state.pressAmount, -rate, rate)
        state.closePress += M.clamp(closeTarget - state.closePress, -rate, rate)
        state.actionPress += M.clamp(actionTarget - state.actionPress, -rate, rate)
        guard state.active >= 0 else { return }

        var x = state.p
        if !dragging {
            var v = velocity
            var ox = state.dragX, oy = state.dragY
            var vx = dragVX, vy = dragVY
            if reducedMotion {
                x += (goal - x) * (1 - exp(-dt * 14))
                ox = 0; oy = 0; v = 0; vx = 0; vy = 0
                if abs(goal - x) < 0.004 { x = goal }
            } else {
                let steps = max(1, Int((dt / M.substep).rounded(.up)))
                let h = dt / Double(steps)
                for _ in 0..<steps {
                    v += (M.stiffness * (goal - x) - M.damping * v) * h
                    x += v * h
                    vx += (-M.stiffness * ox - M.damping * vx) * h
                    ox += vx * h
                    vy += (-M.stiffness * oy - M.damping * vy) * h
                    oy += vy * h
                }
                if abs(goal - x) < 0.0004, abs(v) < 0.004 { x = goal; v = 0 }
                if abs(ox) < 0.05, abs(vx) < 0.5 { ox = 0; vx = 0 }
                if abs(oy) < 0.05, abs(vy) < 0.5 { oy = 0; vy = 0 }
            }
            state.p = x
            velocity = v
            state.dragX = ox
            state.dragY = oy
            dragVX = vx
            dragVY = vy
        }

        // Follow-through and the content's clock run whether a finger holds the frame or not.
        state.titleFollow += (x - state.titleFollow) * (1 - exp(-dt / M.followTitle))
        state.subtitleFollow += (x - state.subtitleFollow) * (1 - exp(-dt / M.followSubtitle))
        let showing = goal == 1 && !dragging
        state.content = M.clamp(state.content + (showing ? dt / M.contentIn : -dt / M.contentOut), 0, 1)

        // Home: the card takes over again.
        if goal == 0, !dragging, x == 0, abs(state.titleFollow) < 0.003, abs(state.subtitleFollow) < 0.003,
           state.dragX == 0, state.dragY == 0 {
            state.titleFollow = 0
            state.subtitleFollow = 0
            state.content = 0
            state.active = -1
        }
    }
}

/// Who is driving: the loop's clock, or a finger. Read once per frame by the screen's timeline;
/// nothing here is observed, so a frame never invalidates the view.
final class MorphingCardDialogModel {
    private typealias M = MorphingCardDialogMotion
    private enum Mode { case auto, manual, returning }

    private var mode = Mode.auto
    private var epoch = Date()
    private var last = Date()
    private var idle = 0.0
    private(set) var live = MorphingCardDialogLive()

    var resting: Bool { live.resting }
    var goal: Double { live.goal }
    var active: Int { live.state.active }

    func state(at date: Date, playing: Bool, reducedMotion: Bool) -> MorphingCardDialogState {
        let dt = min(max(date.timeIntervalSince(last), 0), 0.048)
        last = date
        if mode == .auto, !playing {
            live = MorphingCardDialogLive()
            mode = .manual
        }
        switch mode {
        case .auto:
            let t = max(0, date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: M.loop)
            return M.autoState(at: t)
        case .manual:
            live.step(dt, reducedMotion: reducedMotion)
            if playing {
                if live.fingers == 0, !live.dragging { idle += dt }
                if idle >= M.resumeAfter {
                    mode = .returning
                    live.close(0)
                }
            }
        case .returning:
            live.step(dt, reducedMotion: reducedMotion)
            // Back at rest: the loop starts again from 0.
            if live.state.active < 0, playing {
                epoch = date
                mode = .auto
                return M.autoState(at: 0)
            }
        }
        return live.state
    }

    /// A touch takes over: the scripted finger lets go and the clock stops.
    func touch(_ change: (inout MorphingCardDialogLive) -> Void) {
        idle = 0
        if mode == .auto {
            let now = Date()
            let t = max(0, now.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: M.loop)
            live = MorphingCardDialogLive(taking: t)
            last = now
        }
        mode = .manual
        change(&live)
    }
}
