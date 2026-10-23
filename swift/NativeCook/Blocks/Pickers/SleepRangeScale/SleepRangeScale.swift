import SwiftUI

/// The fixed words on the sheet.
struct SleepRangeScaleLabels: Equatable {
    var title = "sleep"
    var subtitle = "timings"
    var question = "when did you sleep last night?"
    var sleep = "Sleep time"
    var wake = "Wake time"
    var to24 = "Switch to 24h"
    var to12 = "Switch to 12h"
    var submit = "Update sleep"
}

/// A sheet that asks when you slept. A scale of 24 hours, one tick an hour, carries two tall
/// handles with a band of colour between them: dragging a handle moves the sleep or the wake time
/// hour by hour, dragging the band moves both, and the two large read-outs above roll like an
/// odometer with every step. A chip switches the read-outs and the axis between 12 and 24 hours.
struct SleepRangeScale: View {
    /// The hour of the day at the left end of the scale, 0…23.
    var startHour = 20
    /// The first sleep and wake times, as hours of the day.
    var sleep = 23
    var wake = 7
    var labels = SleepRangeScaleLabels()
    /// Shows 24-hour times at first.
    var use24Hours = false
    /// Moves the handles and the band by itself, in a loop.
    var autoPlay = false
    /// Called with the sleep and the wake hour (0…23) after every step.
    var onChange: ((Int, Int) -> Void)? = nil
    /// Called with both hours when the button is tapped.
    var onSubmit: ((Int, Int) -> Void)? = nil
    var onClose: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0
    /// Counts the hours a finger passes, for the haptic.
    @State private var ticks = 0

    private enum Part {
        case sleep, wake, band
    }

    private enum Step {
        case grab(Part), shift(Int), release, format
    }

    /// What moves, sampled against the `TimelineView` clock. Positions count hours from the scale's left end.
    private final class Motion {
        var ready = false
        var sleep = 3
        var wake = 11
        var is24 = false
        /// Where the two handles are drawn.
        var sleepAt = SleepRangeScaleTrack(3)
        var wakeAt = SleepRangeScaleTrack(11)
        /// What the two read-outs show: between two hours they are part-way through a roll.
        var sleepShown = SleepRangeScaleTrack(3)
        var wakeShown = SleepRangeScaleTrack(11)
        /// 0 at rest … 1 held, for each handle.
        var sleepHeld = SleepRangeScaleTrack(0)
        var wakeHeld = SleepRangeScaleTrack(0)
        /// 0 twelve-hour times … 1 twenty-four-hour times.
        var format = SleepRangeScaleTrack(0)
        var chipPress = SleepRangeScaleTrack(0)
        var submitPress = SleepRangeScaleTrack(0)

        var held: Part?
        /// The hours when the drag began, and whether the finger has moved a step since.
        var from = (sleep: 0, wake: 0)
        var moved = false
    }

    private var auto: Bool { autoPlay && !reduceMotion }

    /// The first positions on the scale: sleep 0…23, wake after it and at most 24.
    private var first: (sleep: Int, wake: Int) {
        let s = ((sleep - startHour) % 24 + 24) % 24
        var w = ((wake - startHour) % 24 + 24) % 24
        if w <= s { w = min(24, max(s + 1, w == 0 ? 24 : w)) }
        return (s, w)
    }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let _ = prepare()
            let sleepAt = motion.sleepAt.value(at: now)
            let wakeAt = motion.wakeAt.value(at: now)
            let format = motion.format.value(at: now)
            let hours = motion.wake - motion.sleep

            ZStack {
                Capsule()
                    .fill(Color(hex: 0xb9bcc6))
                    .frame(width: 36, height: 4)
                    .position(x: Metrics.width / 2, y: 11)

                header

                readout(icon: "bed.double.fill", label: labels.sleep, value: motion.sleepShown.value(at: now), format: format)
                    .position(x: Metrics.width / 2 - Metrics.column, y: Metrics.readoutY)
                readout(icon: "alarm.fill", label: labels.wake, value: motion.wakeShown.value(at: now), format: format)
                    .position(x: Metrics.width / 2 + Metrics.column, y: Metrics.readoutY)
                Rectangle()
                    .fill(Metrics.hairline)
                    .frame(width: 1, height: 44)
                    .position(x: Metrics.width / 2, y: Metrics.readoutY + 8)
                Text("~\(hours) \(hours == 1 ? "hour" : "hours") of sleep")
                    .font(.system(size: 11, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(Color(hex: 0x8a8f98))
                    .position(x: Metrics.width / 2, y: Metrics.readoutY + 57)

                Canvas { context, _ in
                    drawSleepRangeScale(
                        context, sleep: sleepAt, wake: wakeAt,
                        sleepHeld: motion.sleepHeld.value(at: now), wakeHeld: motion.wakeHeld.value(at: now)
                    )
                }
                .allowsHitTesting(false)

                ForEach(0..<7, id: \.self) { mark in
                    let hour = (startHour + mark * 4) % 24
                    ZStack {
                        Text(sleepRangeScaleTwelve(hour).joined(separator: " ")).opacity(1 - format)
                        Text(sleepRangeScaleTwentyFour(hour)).opacity(format)
                    }
                    .font(.system(size: 9, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(Color(hex: 0x9a9fa8))
                    .fixedSize()
                    .position(x: Metrics.scaleLeft + Double(mark * 4) * Metrics.pitch, y: Metrics.tickY + 74)
                }

                Color.clear
                    .frame(width: Metrics.pitch * 24 + 48, height: 96)
                    .contentShape(Rectangle())
                    .gesture(scaleDrag)
                    .position(x: Metrics.width / 2, y: Metrics.tickY + 26)
                    .accessibilityElement()
                    .accessibilityLabel("\(labels.sleep) \(sleepRangeScaleTwentyFour(hour(motion.sleep))), \(labels.wake) \(sleepRangeScaleTwentyFour(hour(motion.wake)))")

                Rectangle()
                    .fill(Metrics.hairline)
                    .frame(width: Metrics.width - 40, height: 1)
                    .position(x: Metrics.width / 2, y: Metrics.footerY - 34)

                Text(motion.is24 ? labels.to12 : labels.to24)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x3a4048))
                    .frame(width: 104, height: 30)
                    .background(RoundedRectangle(cornerRadius: 8, style: .circular).fill(Color(hex: 0xecedef)))
                    .contentShape(Rectangle())
                    .onTapGesture {
                        touch()
                        toggleFormat()
                    }
                    .scaleEffect(1 - 0.05 * motion.chipPress.value(at: now))
                    .position(x: 20 + 52, y: Metrics.footerY)
                    .accessibilityAddTraits(.isButton)

                HStack(spacing: 8) {
                    Image(systemName: "arrow.up.circle")
                        .font(.system(size: 18, weight: .medium))
                    Text(labels.submit)
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundStyle(.white)
                .frame(width: 140, height: 42)
                .background(RoundedRectangle(cornerRadius: 10, style: .circular).fill(Color(hex: 0x3a4048)))
                .contentShape(Rectangle())
                .onTapGesture {
                    touch()
                    motion.submitPress.retarget(1, .pulse)
                    onSubmit?(hour(motion.sleep), hour(motion.wake))
                }
                .scaleEffect(1 - 0.04 * motion.submitPress.value(at: now))
                .position(x: Metrics.width - 20 - 70, y: Metrics.footerY)
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isButton)
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .background(
                RoundedRectangle(cornerRadius: Metrics.corner, style: .continuous)
                    .fill(
                        LinearGradient(
                            stops: [
                                .init(color: Color(hex: 0xf1edfb), location: 0),
                                .init(color: Color(hex: 0xfbfbfe), location: 0.35),
                                .init(color: Color(hex: 0xe9f2fb), location: 0.7),
                                .init(color: Color(hex: 0xf7eaf2), location: 1),
                            ],
                            startPoint: .topLeading, endPoint: .bottomTrailing
                        )
                    )
                    .shadow(color: Color(hex: 0x1c1b33, opacity: 0.18), radius: 22, x: 0, y: 12)
            )
        }
        .sensoryFeedback(.selection, trigger: ticks)
        .sensoryFeedback(.impact(weight: .light), trigger: touches)
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Parts

    private var header: some View {
        ZStack {
            VStack(alignment: .leading, spacing: -1) {
                Text(labels.title)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Metrics.ink)
                Text(labels.subtitle)
                    .font(.system(size: 9))
                    .foregroundStyle(Color(hex: 0x8a8f98))
                    .padding(.leading, 7)
            }
            .fixedSize()
            .frame(width: 60, height: 40, alignment: .leading)
            .position(x: 20 + 30, y: Metrics.headerY)
            Rectangle()
                .fill(Color(hex: 0x2b2f36, opacity: 0.45))
                .frame(width: 1, height: 22)
                .position(x: 84, y: Metrics.headerY)
            Text(labels.question)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Color(hex: 0x6b7078))
                .lineLimit(1)
                .frame(width: 190, height: 20, alignment: .leading)
                .position(x: 98 + 95, y: Metrics.headerY)
            Image(systemName: "xmark.circle")
                .font(.system(size: 21, weight: .medium))
                .foregroundStyle(Metrics.ink)
                .frame(width: 40, height: 40)
                .contentShape(Rectangle())
                .onTapGesture {
                    touch()
                    onClose?()
                }
                .position(x: Metrics.width - 31, y: Metrics.headerY)
                .accessibilityLabel("Close")
                .accessibilityAddTraits(.isButton)
            Rectangle()
                .fill(Metrics.hairline)
                .frame(width: Metrics.width - 40, height: 1)
                .position(x: Metrics.width / 2, y: Metrics.headerY + 29)
        }
    }

    /// An icon, a label and the time. The time is a drum: between two hours the earlier one has slid
    /// up and faded while the later one arrives from below.
    private func readout(icon: String, label: String, value: Double, format: Double) -> some View {
        let lower = value.rounded(.down)
        let turn = value - lower
        return ZStack {
            Image(systemName: icon)
                .font(.system(size: 19))
                .foregroundStyle(Color(hex: 0x9a9fa8))
                .offset(y: -40)
            Text(label)
                .font(.system(size: 9, weight: .medium))
                .foregroundStyle(Color(hex: 0x8a8f98))
                .offset(y: -20)
            ForEach(0..<2, id: \.self) { row in
                let shown = hour(min(24, max(0, Int(lower) + row)))
                ZStack {
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text(sleepRangeScaleTwelve(shown)[0])
                            .font(.system(size: 34, weight: .heavy))
                        Text(sleepRangeScaleTwelve(shown)[1])
                            .font(.system(size: 14, weight: .medium))
                    }
                    .opacity(1 - format)
                    Text(sleepRangeScaleTwentyFour(shown))
                        .font(.system(size: 30, weight: .heavy))
                        .opacity(format)
                }
                .monospacedDigit()
                .fixedSize()
                .offset(y: 10 + (row == 0 ? -turn : 1 - turn) * Metrics.roll)
                .opacity(row == 0 ? 1 - turn : turn)
            }
            .foregroundStyle(Metrics.ink)
        }
        .frame(width: 130, height: 100)
    }

    /// One drag over the scale. It takes the handle it starts near, or the band when it starts
    /// between the handles; a tap moves the nearer handle to the tapped hour.
    private var scaleDrag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { drag in
                // The drag's box starts 24 pt left of the scale.
                let at = (drag.startLocation.x - 24) / Metrics.pitch
                if motion.held == nil {
                    let toSleep = abs(at - Double(motion.sleep))
                    let toWake = abs(at - Double(motion.wake))
                    let near = min(toSleep, toWake) * Metrics.pitch <= Metrics.catchDistance
                    let inside = at > Double(motion.sleep) && at < Double(motion.wake)
                    motion.moved = false
                    hold(inside && !near ? .band : (toSleep <= toWake ? .sleep : .wake))
                    touch()
                }
                let steps = Int((drag.translation.width / Metrics.pitch).rounded())
                if steps != 0 { motion.moved = true }
                shift(steps, tick: true)
            }
            .onEnded { drag in
                if !motion.moved, motion.held != .band, abs(drag.translation.width) < 4 {
                    let hour = Int(((drag.startLocation.x - 24) / Metrics.pitch).rounded())
                    shift(hour - (motion.held == .sleep ? motion.from.sleep : motion.from.wake), tick: true)
                }
                letGo()
                touch()
            }
    }

    // MARK: The scale's steps

    private func hour(_ position: Int) -> Int {
        ((startHour + position) % 24 + 24) % 24
    }

    /// Sets the first values before the first frame is drawn.
    private func prepare() {
        guard !motion.ready else { return }
        motion.ready = true
        reset()
    }

    private func reset() {
        motion.sleep = first.sleep
        motion.wake = first.wake
        motion.is24 = use24Hours
        motion.sleepAt = SleepRangeScaleTrack(Double(first.sleep))
        motion.wakeAt = SleepRangeScaleTrack(Double(first.wake))
        motion.sleepShown = SleepRangeScaleTrack(Double(first.sleep))
        motion.wakeShown = SleepRangeScaleTrack(Double(first.wake))
        motion.format = SleepRangeScaleTrack(use24Hours ? 1 : 0)
        motion.held = nil
    }

    private func touch() {
        touches += 1
    }

    /// Takes hold of a handle or of the band: what is held thickens.
    private func hold(_ part: Part?) {
        if part != nil { motion.from = (motion.sleep, motion.wake) }
        motion.held = part
        let kind: SleepRangeScaleTrack.Kind = reduceMotion ? .jump : .tween(Metrics.holdTime, Ease.out)
        motion.sleepHeld.retarget(part == .sleep || part == .band ? 1 : 0, kind)
        motion.wakeHeld.retarget(part == .wake || part == .band ? 1 : 0, kind)
    }

    private func letGo() {
        hold(nil)
    }

    /// Moves what is held to `steps` hours from where it was taken, as far as the scale and the other handle allow.
    private func shift(_ steps: Int, tick: Bool) {
        guard let held = motion.held else { return }
        var sleep = motion.from.sleep
        var wake = motion.from.wake
        switch held {
        case .sleep: sleep = min(max(sleep + steps, 0), wake - 1)
        case .wake: wake = min(max(wake + steps, sleep + 1), 24)
        case .band:
            let by = min(max(steps, -sleep), 24 - wake)
            sleep += by
            wake += by
        }
        set(sleep: sleep, wake: wake, tick: tick)
    }

    private func set(sleep: Int, wake: Int, tick: Bool) {
        guard sleep != motion.sleep || wake != motion.wake else { return }
        let slide: SleepRangeScaleTrack.Kind = reduceMotion ? .jump : .spring(stepSpring)
        let roll: SleepRangeScaleTrack.Kind = reduceMotion ? .jump : .tween(Metrics.rollTime, Ease.out)
        if sleep != motion.sleep {
            motion.sleep = sleep
            motion.sleepAt.retarget(Double(sleep), slide)
            motion.sleepShown.retarget(Double(sleep), roll)
        }
        if wake != motion.wake {
            motion.wake = wake
            motion.wakeAt.retarget(Double(wake), slide)
            motion.wakeShown.retarget(Double(wake), roll)
        }
        if tick { ticks += 1 }
        onChange?(hour(sleep), hour(wake))
    }

    private func toggleFormat() {
        motion.is24.toggle()
        motion.chipPress.retarget(1, .pulse)
        motion.format.retarget(motion.is24 ? 1 : 0, reduceMotion ? .jump : .tween(Metrics.formatTime, .easeInOut))
    }

    /// The script a finger would follow: the wake handle in, the sleep handle in, the band along, both
    /// handles back, and the chip there and back.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, go back to the first times, and start over once they have settled.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                guard motion.held == nil else { return }
                set(sleep: first.sleep, wake: first.wake, tick: false)
                if motion.is24 != use24Hours { toggleFormat() }
                try await Task.sleep(for: .seconds(Metrics.settle))
            }
            let clock = ContinuousClock()
            var begin = clock.now
            while true {
                for (at, step) in Self.script {
                    try await clock.sleep(until: begin + .seconds(at))
                    switch step {
                    case .grab(let part): hold(part)
                    case .shift(let steps): shift(steps, tick: false)
                    case .release: letGo()
                    case .format: toggleFormat()
                    }
                }
                begin += .seconds(Metrics.loop)
            }
        } catch {}
    }

    /// autoPlay: what happens when, in seconds from the start of a loop. A shift counts hours from
    /// where the held part was taken.
    private static let script: [(Double, Step)] = [
        (1.0, .grab(.wake)), (1.15, .shift(-1)), (1.4, .shift(-2)), (1.7, .release),
        (2.3, .grab(.sleep)), (2.45, .shift(1)), (2.7, .shift(2)), (3.0, .release),
        (3.6, .grab(.band)), (3.75, .shift(1)), (4.0, .shift(2)), (4.25, .shift(3)), (4.55, .release),
        (5.15, .grab(.sleep)), (5.3, .shift(-1)), (5.45, .shift(-2)), (5.6, .shift(-3)), (5.75, .shift(-4)), (5.9, .shift(-5)), (6.15, .release),
        (6.5, .grab(.wake)), (6.65, .shift(-1)), (6.9, .release),
        (7.4, .format), (8.3, .format),
    ]
}

/// Draws the ticks, the band and the two handles. `sleep` and `wake` count hours from the left end.
private func drawSleepRangeScale(_ context: GraphicsContext, sleep: Double, wake: Double, sleepHeld: Double, wakeHeld: Double) {
    let tick = GraphicsContext.Shading.color(Color(hex: 0xb8bcc6))
    for hour in 0...24 {
        let x = Metrics.scaleLeft + Double(hour) * Metrics.pitch
        let half = hour % 4 == 0 ? Metrics.majorTick / 2 : Metrics.minorTick / 2
        context.fill(Path(CGRect(x: x - 0.5, y: Metrics.tickY - half, width: 1, height: half * 2)), with: tick)
    }

    let left = Metrics.scaleLeft + sleep * Metrics.pitch
    let right = Metrics.scaleLeft + wake * Metrics.pitch
    let band = CGRect(x: left, y: Metrics.tickY + Metrics.bandTop, width: max(0, right - left), height: Metrics.bandHeight)
    context.fill(
        Path(band),
        with: .linearGradient(
            Gradient(colors: [Color(hex: 0xa9b8ff), Color(hex: 0xc9a5f5)]),
            startPoint: CGPoint(x: band.minX, y: band.midY), endPoint: CGPoint(x: band.maxX, y: band.midY)
        )
    )

    let ink = GraphicsContext.Shading.color(Metrics.ink)
    for (x, held) in [(left, sleepHeld), (right, wakeHeld)] {
        let width = Metrics.handleWidth + 2 * held
        let grow = 3 * held
        let bar = CGRect(x: x - width / 2, y: Metrics.tickY + Metrics.handleTop - grow, width: width, height: Metrics.handleHeight + grow * 2)
        context.fill(Path(roundedRect: bar, cornerRadius: width / 2), with: ink)
    }
}

/// An hour of the day as a number and "AM" or "PM".
private func sleepRangeScaleTwelve(_ hour: Int) -> [String] {
    ["\(hour % 12 == 0 ? 12 : hour % 12)", hour < 12 ? "AM" : "PM"]
}

private func sleepRangeScaleTwentyFour(_ hour: Int) -> String {
    (hour < 10 ? "0" : "") + "\(hour):00"
}

/// A number heading for a target, sampled against the `TimelineView` clock.
private struct SleepRangeScaleTrack {
    enum Kind {
        case jump
        case tween(Double, UnitCurve)
        case spring(Spring)
        /// Up to the target in 80 ms, a 40 ms hold, back to 0 in 180 ms: a tap.
        case pulse
    }

    private var from: Double
    private var to: Double
    private var speed = 0.0
    private var start = Date.distantPast
    private var kind = Kind.jump

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0 else { return from }
        switch kind {
        case .jump: return to
        case .tween(let duration, let curve): return t >= duration ? to : from + (to - from) * curve.value(at: t / duration)
        case .spring(let spring): return from + spring.value(target: to - from, initialVelocity: speed, time: t)
        case .pulse: return to * (t < 0.08 ? t / 0.08 : t < 0.12 ? 1 : max(0, 1 - (t - 0.12) / 0.18))
        }
    }

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0, case .spring(let spring) = kind else { return 0 }
        return spring.velocity(target: to - from, initialVelocity: speed, time: t)
    }

    /// Heads for `target` from where it is now, keeping a spring's speed.
    mutating func retarget(_ target: Double, _ kind: Kind) {
        let now = Date.now
        let value = value(at: now)
        speed = velocity(at: now)
        from = value
        if case .pulse = kind { from = 0 }
        to = target
        start = now
        self.kind = kind
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 384.0
    static let corner = 32.0
    static let ink = Color(hex: 0x2b2f36)
    static let hairline = Color(hex: 0x2b2f36, opacity: 0.1)

    static let headerY = 42.0
    /// The read-outs' centre, and how far each is from the middle of the sheet.
    static let readoutY = 128.0
    static let column = 76.0
    static let roll = 26.0
    static let footerY = 351.0

    // The scale: 24 hours of `pitch` points from `scaleLeft`, the ticks centred on `tickY`.
    static let scaleLeft = 32.0
    static let pitch = 11.5
    static let tickY = 222.0
    static let minorTick = 12.0
    static let majorTick = 20.0
    /// The handles and the band, measured from the ticks' centre.
    static let handleTop = -13.0
    static let handleHeight = 72.0
    static let handleWidth = 4.0
    static let bandTop = 10.0
    static let bandHeight = 30.0
    /// A drag that starts within this many points of a handle takes it.
    static let catchDistance = 14.0

    static let holdTime = 0.12
    static let rollTime = 0.15
    static let formatTime = 0.25

    static let loop = 9.0
    /// After a touch the loop waits this long, returns to the first times, and starts over once they have settled.
    static let resumeAfter = 2.0
    static let settle = 0.8
}

private let stepSpring = Spring(mass: 1, stiffness: 600, damping: 34)

#Preview {
    ZStack {
        Color(hex: 0xcfcdd8).ignoresSafeArea()
        SleepRangeScale(autoPlay: true)
    }
}
