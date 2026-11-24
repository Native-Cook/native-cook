import Foundation

/// Every number the popover's motion uses, and the two ways its state is made: `autoState(at:)`,
/// a pure function of the loop time (closed-form damped springs sampled from the script's event
/// times), and `MorphingFeedbackPopoverLive`, the same springs stepped under a finger.
enum MorphingFeedbackPopoverMotion {
    /// Seconds. One autoPlay loop; rest at both ends.
    static let loop: Double = 8

    /// Two springs, one per axis (mass 1). Width is the bouncier one and leads; height follows.
    struct Spring {
        let stiffness: Double
        let damping: Double

        init(response: Double, dampingRatio: Double) {
            stiffness = pow(2 * Double.pi / response, 2)
            damping = 2 * dampingRatio * stiffness.squareRoot()
        }

        init(stiffness: Double, damping: Double) {
            self.stiffness = stiffness
            self.damping = damping
        }

        /// Closed form: where the spring is and how fast, `t` seconds after it left `x0` at `v0`.
        func at(_ t: Double, from x0: Double, velocity v0: Double, to goal: Double) -> (x: Double, v: Double) {
            let a = damping / 2
            let w = (stiffness - a * a).squareRoot()
            let y0 = x0 - goal
            let b = (v0 + a * y0) / w
            let e = exp(-a * t), c = cos(w * t), s = sin(w * t)
            return (goal + e * (y0 * c + b * s), e * ((b * w - a * y0) * c - (a * b + y0 * w) * s))
        }
    }

    static let widthSpring = Spring(response: 0.4, dampingRatio: 0.66) // ≈ 246.7, 20.7
    static let heightSpring = Spring(response: 0.44, dampingRatio: 0.78) // ≈ 203.9, 22.3
    /// Opening: width first, height 50 ms later. Closing: height first, width 50 ms later.
    static let axisStagger = 0.05
    static let substep = 1.0 / 240

    /// The label trails the frame by 60 ms (first-order lag) on its way to the placeholder's place.
    static let followLabel = 0.06
    /// As a placeholder the label is this opaque.
    static let placeholderAlpha = 0.42

    /// The popover's content: its own clock, 0 → 1 in `contentIn` while open, back in `contentOut`.
    static let contentIn = 0.5
    static let contentOut = 0.12
    /// Field, faces, Send: where each starts on that clock and how long it takes.
    static let contentStart = [0.06, 0.24, 0.38]
    static let contentSpan = 0.6
    static let contentRise = 10.0 // pt
    /// The content is tied to the height too, so a collapse or a drag fades it.
    static let contentGate = (0.5, 0.9)

    static let radiusOpen = 26.0
    static let scrim = 0.14

    /// Drag down: rubber-banded travel squashes the popover toward the pill.
    static let dragRubber = 220.0
    static let dragSpan = 260.0
    static let dragWidthShare = 0.5
    static let dragFollow = 0.3
    static let dragUpRubber = 70.0
    static let dragCloseDistance = 90.0
    static let dragCloseVelocity = 600.0
    static let dragSlop = 6.0

    /// "Thanks!": seconds after Send. The check is two strokes drawn one after the other.
    static let thanksLabelOut = 0.15
    static let thanksIn = (0.22, 0.42)
    static let thanksShort = (0.3, 0.42)
    static let thanksLong = (0.42, 0.62)
    static let thanksPulse = (0.62, 0.9)
    static let thanksOut = (1.5, 1.68)
    static let thanksLabelIn = (1.62, 1.85)
    static let thanksEnd = 1.9
    static let thanksSwell = 0.04

    static let pressScale = 0.96
    static let pressTime = 0.12
    /// A face that is picked pops to this and springs back.
    static let facePop = 1.28
    static let facePopTime = 0.11
    static let faceSpring = Spring(stiffness: 380, damping: 14)
    static let faceFill = 0.14
    static let sendFade = 0.16
    static let sendIdle = 0.4

    /// autoPlay: 2 s after the last touch the popover closes and the loop restarts at 0.
    static let resumeAfter = 2.0

    /// The loop's events, in seconds.
    static let tapPill = 0.8
    static let typeStart = 1.5
    static let typeEnd = 3.7
    static let tapFace = 4.05
    static let tapSend = 4.8
    /// The face the ghost finger picks (0 = worst … 3 = best).
    static let ghostFace = 3
    static let tapAppear = -0.4, tapShown = -0.22, tapDown = -0.15, tapPressed = -0.07, tapUp = 0.1, tapGone = 0.24

    /// What autoPlay types: a first sentence, a breath, then the rest.
    static let sample = "Love the tide view. Could notes open on launch?"
    static let samplePauseAt = 19
    static let typeFirst = 0.36
    static let typeBreath = 0.12

    // MARK: Helpers

    static func clamp(_ value: Double, _ low: Double, _ high: Double) -> Double { min(max(value, low), high) }
    static func mix(_ from: Double, _ to: Double, _ amount: Double) -> Double { from + (to - from) * amount }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a), 0, 1) }
    static func easeOutCubic(_ u: Double) -> Double { let k = 1 - u; return 1 - k * k * k }
    static func easeInOutCubic(_ u: Double) -> Double { u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2 }
    static func easeInOutQuad(_ u: Double) -> Double { u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u) }
    static func rubber(_ distance: Double, _ limit: Double) -> Double { distance / (1 + distance / limit) }
    static func rubberSlope(_ distance: Double, _ limit: Double) -> Double { let k = 1 + distance / limit; return 1 / (k * k) }
    static func tapGhost(_ t: Double, _ at: Double) -> Double {
        seg(t, at + tapAppear, at + tapShown) * (1 - seg(t, at + 0.02, at + tapGone))
    }
    static func tapPress(_ t: Double, _ at: Double) -> Double {
        seg(t, at + tapDown, at + tapPressed) * (1 - seg(t, at, at + tapUp))
    }

    /// How many characters of the sample are typed at u = 0…1 of the typing time.
    static func typedCount(_ u: Double, _ total: Int) -> Int {
        let first = min(samplePauseAt, total)
        if u <= typeFirst { return Int((Double(first) * (u / typeFirst)).rounded(.down)) }
        return first + Int((Double(total - first) * seg(u, typeFirst + typeBreath, 1)).rounded())
    }

    /// A picked face's scale, `age` seconds after the pick: up in 110 ms, then the spring back.
    static func facePop(_ age: Double) -> Double {
        if age <= 0 { return 1 }
        if age < facePopTime { return mix(1, facePop, easeInOutQuad(age / facePopTime)) }
        return faceSpring.at(age - facePopTime, from: facePop, velocity: 0, to: 1).x
    }

    // MARK: The autoPlay journey as a function of time

    struct Axes {
        var wide = 0.0, wideV = 0.0, wideGoal = 0.0
        var tall = 0.0, tallV = 0.0, tallGoal = 0.0
        var goal = 0.0
        /// Seconds until the second axis hears about the goal.
        var lag = 0.0
    }

    private static func settled(_ s: (x: Double, v: Double), _ goal: Double) -> (Double, Double) {
        abs(goal - s.x) < 0.0004 && abs(s.v) < 0.004 ? (goal, 0) : (s.x, s.v)
    }

    /// Width and height progress, with their speeds, at loop time `t`.
    static func autoAxes(_ t: Double) -> Axes {
        var a = Axes()
        let open = tapPill, close = tapSend
        if t >= open, t < close { a.goal = 1 }
        if t >= open, t < close + axisStagger {
            a.wideGoal = 1
            (a.wide, a.wideV) = settled(widthSpring.at(t - open, from: 0, velocity: 0, to: 1), 1)
        } else if t >= close + axisStagger {
            (a.wide, a.wideV) = settled(widthSpring.at(t - close - axisStagger, from: 1, velocity: 0, to: 0), 0)
        }
        if t >= open + axisStagger, t < close {
            a.tallGoal = 1
            (a.tall, a.tallV) = settled(heightSpring.at(t - open - axisStagger, from: 0, velocity: 0, to: 1), 1)
        } else if t >= close {
            (a.tall, a.tallV) = settled(heightSpring.at(t - close, from: 1, velocity: 0, to: 0), 0)
        }
        if t >= open, t < open + axisStagger { a.lag = open + axisStagger - t }
        if t >= close, t < close + axisStagger { a.lag = close + axisStagger - t }
        return a
    }

    /// The label's lag behind the height, integrated once over the loop from the closed-form
    /// height and read back by time; and the moment the collapse is small enough to count as closed.
    private static let rate = 480.0
    private static let follow: (label: [Double], closedAt: Double) = {
        let count = Int(loop * rate)
        let h = 1 / rate
        let k = 1 - exp(-h / followLabel)
        var label = [Double](repeating: 0, count: count + 1)
        var value = 0.0
        var closedAt = loop
        for i in 1...count {
            let t = Double(i) * h
            let tall = autoAxes(t).tall
            value += (tall - value) * k
            if abs(value) < 0.0005, tall == 0 { value = 0 }
            if t > tapSend, tall <= 0.02, closedAt == loop { closedAt = t }
            label[i] = value
        }
        return (label, closedAt)
    }()

    private static func sample(_ values: [Double], _ t: Double) -> Double {
        let x = clamp(t * rate, 0, Double(values.count - 1))
        let i = min(Int(x), values.count - 2)
        return mix(values[i], values[i + 1], x - Double(i))
    }

    /// A press that goes in 150 ms before the ghost's tap lifts at `at` and lets go there (120 ms each way).
    private static func press(_ t: Double, _ at: Double) -> Double {
        t < at ? easeInOutQuad(seg(t, at + tapDown, at + tapDown + pressTime)) : 1 - easeInOutQuad(seg(t, at, at + pressTime))
    }

    /// Everything the screen draws at loop time `t` (0 ..< loop).
    static func autoState(at t: Double) -> MorphingFeedbackPopoverState {
        let a = autoAxes(t)
        let closedAt = follow.closedAt
        var s = MorphingFeedbackPopoverState()
        s.wide = a.wide
        s.tall = a.tall
        s.goal = a.goal
        s.labelFollow = sample(follow.label, t)
        if t >= tapPill, t < tapSend {
            s.content = clamp((t - tapPill) / contentIn, 0, 1)
        } else if t >= tapSend {
            s.content = clamp(1 - (t - tapSend) / contentOut, 0, 1)
        }
        s.thanks = t >= tapSend && t < tapSend + thanksEnd ? t - tapSend : -1
        s.press = press(t, tapPill)
        s.sendPress = press(t, tapSend)

        // Typing: the placeholder goes with the first character.
        let total = sample.count
        if t >= typeStart, t < closedAt {
            s.typed = t <= typeEnd ? typedCount(seg(t, typeStart, typeEnd), total) : total
        }
        s.covered = s.typed > 0
        let firstKey = typeStart + (typeEnd - typeStart) * typeFirst / Double(min(samplePauseAt, total))
        s.sendOpacity = mix(sendIdle, 1, easeInOutQuad(seg(t, firstKey, firstKey + sendFade))
            * (1 - easeInOutQuad(seg(t, closedAt, closedAt + sendFade))))

        // The picked face.
        if t >= tapFace {
            if t < closedAt { s.mood = ghostFace }
            s.facePop[ghostFace] = facePop(t - tapFace)
            s.facePicked[ghostFace] = easeInOutQuad(seg(t, tapFace, tapFace + faceFill))
                * (1 - easeInOutQuad(seg(t, closedAt, closedAt + faceFill)))
        }

        // The ghost finger.
        var ghost = MorphingFeedbackPopoverGhost(spot: .pill, opacity: tapGhost(t, tapPill), press: tapPress(t, tapPill))
        let onFace = tapGhost(t, tapFace), onSend = tapGhost(t, tapSend)
        if onFace > 0 {
            ghost = MorphingFeedbackPopoverGhost(spot: .face, opacity: onFace, press: tapPress(t, tapFace))
        } else if onSend > 0 {
            ghost = MorphingFeedbackPopoverGhost(spot: .send, opacity: onSend, press: tapPress(t, tapSend))
        }
        s.ghost = ghost
        return s
    }
}

/// What the screen draws from: the same values whether the loop or a finger made them.
struct MorphingFeedbackPopoverState {
    /// 0 = the pill's width / height, 1 = the popover's. Each overshoots on its own spring.
    var wide = 0.0
    var tall = 0.0
    /// 1 while the popover is open or opening.
    var goal = 0.0
    /// `tall`, 60 ms late: the label rides on it.
    var labelFollow = 0.0
    /// The content's own clock, 0…1.
    var content = 0.0
    var dragY = 0.0
    /// Seconds since Send; -1 when no thanks is showing.
    var thanks = -1.0
    var press = 0.0
    var sendPress = 0.0
    /// Characters of the sample autoPlay has typed.
    var typed = 0
    /// True while something is typed, so the placeholder steps aside.
    var covered = false
    /// The face that is picked, -1 for none.
    var mood = -1
    var facePop = [1.0, 1.0, 1.0, 1.0]
    var facePicked = [0.0, 0.0, 0.0, 0.0]
    var sendOpacity = MorphingFeedbackPopoverMotion.sendIdle
    /// Points the popover is pushed up by the keyboard.
    var lift = 0.0
    /// A finger has taken over: the real field shows instead of autoPlay's text.
    var manual = false
    var ghost: MorphingFeedbackPopoverGhost?
}

struct MorphingFeedbackPopoverGhost {
    enum Spot { case pill, face, send }
    var spot: Spot
    var opacity: Double
    var press: Double
}

/// The popover under a finger: the springs stepped in time, as the React Native controller does.
struct MorphingFeedbackPopoverLive {
    private typealias M = MorphingFeedbackPopoverMotion

    var state = MorphingFeedbackPopoverState()
    var dragging = false
    var fingers = 0
    private var wideV = 0.0, tallV = 0.0, wideGoal = 0.0, tallGoal = 0.0, lag = 0.0
    private var dragV = 0.0
    private var grabWide = 1.0, grabTall = 1.0, grabY = 0.0, pulled = 0.0
    private var pressTarget = 0.0, sendTarget = 0.0
    private var popAge = [Double](repeating: 10, count: 4)
    private var wasClosed = true

    /// A pill again, nothing pressed, nothing showing.
    var resting: Bool {
        state.goal == 0 && state.tall == 0 && state.wide == 0 && state.thanks < 0 && state.press == 0
            && state.sendPress == 0 && fingers == 0 && state.lift == 0
    }

    init() { state.manual = true }

    /// Carries on from wherever the loop was at `t`.
    init(taking t: Double) {
        let a = M.autoAxes(t)
        state = M.autoState(at: t)
        state.ghost = nil
        state.manual = true
        state.typed = 0
        wideV = a.wideV
        tallV = a.tallV
        wideGoal = a.wideGoal
        tallGoal = a.tallGoal
        lag = a.lag
        if t >= M.tapFace { popAge[M.ghostFace] = t - M.tapFace }
        wasClosed = state.goal == 0 && state.tall <= 0.02
    }

    mutating func pressPill(_ on: Bool) { pressTarget = on ? 1 : 0 }
    mutating func pressSend(_ on: Bool) { sendTarget = on ? 1 : 0 }

    /// Stretches the pill open. False when it already is.
    mutating func open() -> Bool {
        guard state.goal != 1 else { return false }
        state.thanks = -1
        state.goal = 1
        wideGoal = 1
        lag = M.axisStagger
        return true
    }

    /// Closes back into the pill.
    mutating func dismiss() -> Bool {
        guard state.goal != 0 else { return false }
        dragging = false
        state.goal = 0
        tallGoal = 0
        lag = M.axisStagger
        return true
    }

    /// Closes, and the pill says thanks for a moment.
    mutating func confirm() -> Bool {
        guard dismiss() else { return false }
        state.thanks = 0
        return true
    }

    /// Picks a face, or lets go of the one that is picked.
    mutating func pick(_ index: Int, reducedMotion: Bool) {
        if state.mood == index {
            state.mood = -1
        } else {
            state.mood = index
            if !reducedMotion { popAge[index] = 0 }
        }
    }

    mutating func dragBegin() {
        dragging = true
        grabWide = state.wide
        grabTall = state.tall
        grabY = state.dragY
        pulled = 0
        wideV = 0
        tallV = 0
        dragV = 0
    }

    /// The finger has moved y since dragBegin: down squashes the popover toward the pill, up stretches a little.
    mutating func dragMove(_ y: Double) {
        let down = max(y, 0), up = max(-y, 0)
        let banded = M.rubber(down, M.dragRubber)
        pulled = down
        state.tall = max(grabTall - banded / M.dragSpan, 0)
        state.wide = max(grabWide - banded / M.dragSpan * M.dragWidthShare, 0)
        state.dragY = grabY + banded * M.dragFollow - M.rubber(up, M.dragUpRubber) * 0.5
    }

    /// Lets go with the finger's velocity (pt/s). True when the popover closes.
    mutating func dragEnd(_ vy: Double) -> Bool {
        guard dragging else { return false }
        dragging = false
        let slope = M.rubberSlope(pulled, M.dragRubber)
        let speed = pulled > 0 ? -vy * slope / M.dragSpan : 0
        tallV = speed
        wideV = speed * M.dragWidthShare
        dragV = vy * slope * M.dragFollow
        let closes = vy > M.dragCloseVelocity || pulled > M.dragCloseDistance
        if closes {
            state.goal = 0
            tallGoal = 0
            lag = M.axisStagger
        }
        return closes
    }

    mutating func step(_ dt: Double, reducedMotion: Bool, hasText: Bool, liftTarget: Double) {
        guard dt > 0 else { return }
        let to = state.goal

        // The second axis hears about a new goal 50 ms after the first.
        if lag > 0 {
            lag -= dt
            if lag <= 0 {
                wideGoal = to
                tallGoal = to
            }
        }

        var w = state.wide, h = state.tall
        if !dragging {
            var vw = wideV, vh = tallV
            var y = state.dragY, vy = dragV
            if reducedMotion {
                let k = 1 - exp(-dt * 14)
                w += (wideGoal - w) * k
                h += (tallGoal - h) * k
                if abs(wideGoal - w) < 0.004 { w = wideGoal }
                if abs(tallGoal - h) < 0.004 { h = tallGoal }
                vw = 0; vh = 0; y = 0; vy = 0
            } else {
                let steps = max(1, Int((dt / M.substep).rounded(.up)))
                let slice = dt / Double(steps)
                let ws = M.widthSpring, hs = M.heightSpring
                for _ in 0..<steps {
                    vw += (ws.stiffness * (wideGoal - w) - ws.damping * vw) * slice
                    w += vw * slice
                    vh += (hs.stiffness * (tallGoal - h) - hs.damping * vh) * slice
                    h += vh * slice
                    vy += (-hs.stiffness * y - hs.damping * vy) * slice
                    y += vy * slice
                }
                if abs(wideGoal - w) < 0.0004, abs(vw) < 0.004 { w = wideGoal; vw = 0 }
                if abs(tallGoal - h) < 0.0004, abs(vh) < 0.004 { h = tallGoal; vh = 0 }
                if abs(y) < 0.05, abs(vy) < 0.5 { y = 0; vy = 0 }
            }
            state.wide = w
            state.tall = h
            wideV = vw
            tallV = vh
            state.dragY = y
            dragV = vy
        }

        let follow = state.labelFollow + (h - state.labelFollow) * (1 - exp(-dt / M.followLabel))
        state.labelFollow = abs(follow) < 0.0005 && h == 0 ? 0 : follow
        let showing = to == 1 && !dragging
        state.content = M.clamp(state.content + (showing ? dt / M.contentIn : -dt / M.contentOut), 0, 1)
        if state.thanks >= 0 { state.thanks = state.thanks + dt >= M.thanksEnd ? -1 : state.thanks + dt }

        // Closed again: the next opening starts clean.
        let closed = to == 0 && h <= 0.02
        if closed, !wasClosed { state.mood = -1 }
        wasClosed = closed

        // Presses, the faces and Send.
        let rate = dt / M.pressTime
        state.press += M.clamp(pressTarget - state.press, -rate, rate)
        state.sendPress += M.clamp(sendTarget - state.sendPress, -rate, rate)
        state.covered = hasText && !closed
        for i in 0..<4 {
            popAge[i] += dt
            state.facePop[i] = M.facePop(popAge[i])
            let fill = dt / M.faceFill
            state.facePicked[i] += M.clamp((state.mood == i ? 1 : 0) - state.facePicked[i], -fill, fill)
        }
        let ready = state.covered || state.mood >= 0
        let fade = dt / M.sendFade * (1 - M.sendIdle)
        state.sendOpacity += M.clamp((ready ? 1 : M.sendIdle) - state.sendOpacity, -fade, fade)

        // The keyboard's push.
        let lift = state.lift + (liftTarget - state.lift) * (1 - exp(-dt * 14))
        state.lift = abs(liftTarget - lift) < 0.2 ? liftTarget : lift
    }
}

/// Who is driving: the loop's clock, or a finger. Read once per frame by the screen's timeline;
/// nothing here is observed, so a frame never invalidates the view.
final class MorphingFeedbackPopoverModel {
    private typealias M = MorphingFeedbackPopoverMotion
    private enum Mode { case auto, manual, returning }

    private var mode = Mode.auto
    private var epoch = Date()
    private var last = Date()
    private var idle = 0.0
    private(set) var live = MorphingFeedbackPopoverLive()

    var resting: Bool { live.resting }

    func state(at date: Date, playing: Bool, reducedMotion: Bool, hasText: Bool, focused: Bool,
               liftTarget: Double) -> MorphingFeedbackPopoverState {
        let dt = min(max(date.timeIntervalSince(last), 0), 0.048)
        last = date
        if mode == .auto, !playing {
            live = MorphingFeedbackPopoverLive()
            mode = .manual
        }
        switch mode {
        case .auto:
            let t = max(0, date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: M.loop)
            return M.autoState(at: t)
        case .manual:
            live.step(dt, reducedMotion: reducedMotion, hasText: hasText, liftTarget: liftTarget)
            if playing {
                if live.fingers == 0, !live.dragging, !focused { idle += dt }
                if idle >= M.resumeAfter {
                    mode = .returning
                    _ = live.dismiss()
                }
            }
        case .returning:
            live.step(dt, reducedMotion: reducedMotion, hasText: hasText, liftTarget: liftTarget)
            // Back at rest: the loop starts again from 0.
            if live.state.goal == 0, live.state.tall == 0, live.state.thanks < 0, playing {
                epoch = date
                mode = .auto
                return M.autoState(at: 0)
            }
        }
        return live.state
    }

    /// A touch takes over: the clock stops, and what was typed so far stays. Returns the number
    /// of characters autoPlay had typed when this touch is the one that took over, else nil.
    @discardableResult
    func touch(_ change: (inout MorphingFeedbackPopoverLive) -> Void) -> Int? {
        idle = 0
        var adopted: Int?
        if mode == .auto {
            let now = Date()
            let t = max(0, now.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: M.loop)
            let before = M.autoState(at: t)
            adopted = before.goal == 1 ? before.typed : 0
            live = MorphingFeedbackPopoverLive(taking: t)
            last = now
        }
        mode = .manual
        change(&live)
        return adopted
    }
}
