import SwiftUI
import UIKit

/// A timer dial drawn as a burst of 28 rays around a dark hub. The face counts 60 units: rays light
/// clockwise from 12 o'clock as the value grows, the two-digit value sits above the hub and a caption
/// below it. Dragging round the dial sets the value to the angle under the finger, without wrapping
/// past the top; each ray flicks outward as it lights, and the hub's dot leans towards the newest ray.
struct SunburstTimerDial: View {
    /// The value the dial starts on, 0…59.
    var initial = 0
    /// The caption under the hub.
    var label = "Timer"
    /// Sweeps the dial through a short script by itself, in a loop.
    var autoPlay = false
    /// Called with the value the dial settles on.
    var onChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the script and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The value, 0…59, continuous while it sweeps.
        var value = SunburstTimerDialTrack(0)
        /// How far each ray has turned from hairline to bold, 0…1, and its flick outward.
        var lit: [SunburstTimerDialTrack] = []
        var flick: [SunburstTimerDialTrack] = []
        /// 1 while a finger (or the script) is turning the dial: the hub dips and its dot leans.
        var press = SunburstTimerDialTrack(0)
        /// How many rays were lit when the last frame was drawn.
        var count = 1
        /// The finger's last angle and the value it has dragged to; nil while nothing touches the dial.
        var angle: Double?
        var dragged = 0.0
        /// The dial is turning under a finger, not under the script: only then it ticks.
        var byHand = false
        let haptic = UISelectionFeedbackGenerator()
    }

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let value = motion.value.value(at: now)
            let _ = notice(value)

            Canvas { context, size in
                draw(context, center: CGPoint(x: size.width / 2, y: size.height / 2), value: value, now: now)
            }
        }
        .frame(width: Metrics.size, height: Metrics.size)
        .contentShape(Circle())
        .gesture(drag)
        .onAppear { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
        .accessibilityElement()
        .accessibilityLabel(label)
        .accessibilityValue("\(Int(motion.value.value(at: .now).rounded()))")
        .accessibilityAdjustableAction { direction in
            touch()
            let next = (motion.value.value(at: .now) + (direction == .increment ? 1 : -1)).rounded()
            settle(next)
        }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Drawing

    private func draw(_ context: GraphicsContext, center: CGPoint, value: Double, now: Date) {
        let press = motion.press.value(at: now)

        for index in 0..<Metrics.rays where index < motion.lit.count {
            let lit = motion.lit[index].value(at: now)
            let turn = tau * Double(index) / Double(Metrics.rays)
            let outer = Metrics.outer + Metrics.flick * motion.flick[index].value(at: now)
            var ray = Path()
            ray.move(to: CGPoint(x: center.x + sin(turn) * Metrics.inner, y: center.y - cos(turn) * Metrics.inner))
            ray.addLine(to: CGPoint(x: center.x + sin(turn) * outer, y: center.y - cos(turn) * outer))
            let ink = Color(red: mix(0xA3, 0x11, lit) / 255, green: mix(0xA3, 0x11, lit) / 255, blue: mix(0xA0, 0x11, lit) / 255)
            context.stroke(ray, with: .color(ink), lineWidth: 1 + lit)
        }

        for index in 0..<8 {
            let turn = tau * Double(index) / 8
            let dot = CGRect(x: center.x + sin(turn) * Metrics.marker - 1.5, y: center.y - cos(turn) * Metrics.marker - 1.5, width: 3, height: 3)
            context.fill(Path(ellipseIn: dot), with: .color(Color(hex: 0x111111)))
        }

        // The hub dips under the finger, and its dot leans towards the newest ray.
        let hub = Metrics.hub * (1 - 0.05 * press)
        context.fill(Path(ellipseIn: CGRect(x: center.x - hub, y: center.y - hub, width: hub * 2, height: hub * 2)), with: .color(Color(hex: 0x333333)))
        let turn = tau * value / Metrics.units
        let lean = Metrics.lean * press
        let dot = CGRect(x: center.x + sin(turn) * lean - 1.5, y: center.y - cos(turn) * lean - 1.5, width: 3, height: 3)
        context.fill(Path(ellipseIn: dot), with: .color(.white))

        let shown = Int(min(max(value.rounded(), 0), Metrics.units - 1))
        let digits = Text(shown < 10 ? "0\(shown)" : "\(shown)")
            .font(.system(size: 22, weight: .semibold))
            .monospacedDigit()
            .foregroundStyle(Color(hex: 0x111111))
        context.draw(digits, at: CGPoint(x: center.x, y: center.y - Metrics.textY))
        let caption = Text(label)
            .font(.system(size: 14))
            .foregroundStyle(Color(hex: 0x111111))
        context.draw(caption, at: CGPoint(x: center.x, y: center.y + Metrics.textY - 4))
    }

    // MARK: The dial

    private func reset() {
        let start = min(max(Double(initial), 0), Metrics.units - 1)
        motion.value = SunburstTimerDialTrack(start)
        motion.count = lit(start)
        motion.lit = (0..<Metrics.rays).map { SunburstTimerDialTrack($0 < motion.count ? 1 : 0) }
        motion.flick = (0..<Metrics.rays).map { _ in SunburstTimerDialTrack(0) }
        motion.press = SunburstTimerDialTrack(0)
        motion.angle = nil
    }

    /// How many rays are lit at `value`, taken to the unit the digits show: the one at 12 o'clock, and
    /// one more every 60/28 units.
    private func lit(_ value: Double) -> Int {
        min(max(Int((value.rounded() * Double(Metrics.rays) / Metrics.units + 0.0001).rounded(.down)) + 1, 1), Metrics.rays)
    }

    /// Runs every frame: the rays the value has passed switch on or off, one tick each.
    private func notice(_ value: Double) {
        let count = lit(value)
        guard count != motion.count, motion.lit.count == Metrics.rays else { return }
        // Under reduced motion nothing eases: rays switch at once and none flicks.
        let change = reduceMotion ? SunburstTimerDialTrack.Kind.jump : .tween(Metrics.lightTime, .linear)
        if count > motion.count {
            for index in motion.count..<count {
                motion.lit[index].retarget(1, change)
                if !reduceMotion { motion.flick[index].retarget(1, .pulse) }
            }
        } else {
            for index in count..<motion.count { motion.lit[index].retarget(0, change) }
        }
        motion.count = count
        if motion.byHand { motion.haptic.selectionChanged() }
    }

    /// Sweeps to `target` in `time` seconds; no time means the snap on release.
    private func settle(_ target: Double, time: Double? = nil, curve: UnitCurve = .easeInOut) {
        let target = min(max(target, 0), Metrics.units - 1)
        var kind = SunburstTimerDialTrack.Kind.spring(snapSpring)
        if let time { kind = .tween(time, curve) }
        if reduceMotion { kind = .jump }
        motion.value.retarget(target, kind)
        onChange?(Int(target.rounded()))
    }

    private func hold(_ down: Bool) {
        motion.press.retarget(down ? 1 : 0, reduceMotion ? .jump : .spring(pressSpring))
    }

    private func touch() {
        motion.byHand = true
        touches += 1
    }

    /// Dragging anywhere on the dial: the value goes to the angle under the finger, then follows it
    /// round, stopping at 0 and 59 instead of wrapping past the top.
    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let x = value.location.x - Metrics.size / 2
                let y = value.location.y - Metrics.size / 2
                // Right over the centre the angle means nothing.
                guard hypot(x, y) > Metrics.dead || motion.angle == nil else { return }
                let turn = atan2(x, -y) < 0 ? atan2(x, -y) + tau : atan2(x, -y)
                if let before = motion.angle {
                    var step = turn - before
                    if step > .pi { step -= tau } else if step < -.pi { step += tau }
                    motion.dragged = min(max(motion.dragged + step / tau * Metrics.units, 0), Metrics.units - 1)
                    motion.value.retarget(motion.dragged, reduceMotion ? .jump : .tween(Metrics.followTime, Ease.out))
                } else {
                    motion.haptic.prepare()
                    touch()
                    hold(true)
                    motion.dragged = hypot(x, y) > Metrics.dead ? min(turn / tau * Metrics.units, Metrics.units - 1) : motion.value.value(at: .now)
                    motion.value.retarget(motion.dragged, reduceMotion ? .jump : .tween(Metrics.reachTime, Ease.out))
                }
                motion.angle = turn
            }
            .onEnded { _ in
                motion.angle = nil
                hold(false)
                settle(motion.dragged.rounded())
                touch()
            }
    }

    /// The script a finger would follow: four sweeps, the last one back to where it began.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, sweep back to the first value, and start over from rest.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                if motion.angle != nil { return }
                motion.byHand = false
                settle(Double(initial), time: Metrics.returnTime)
                try await Task.sleep(for: .seconds(Metrics.returnTime))
            }
            motion.byHand = false
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for step in Metrics.script {
                    try await clock.sleep(until: start + .seconds(step.at))
                    hold(true)
                    settle(Double(initial) + step.to, time: step.time, curve: step.curve)
                    try await clock.sleep(until: start + .seconds(step.at + step.time))
                    hold(false)
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// A number heading for a target, sampled against the `TimelineView` clock.
struct SunburstTimerDialTrack {
    enum Kind {
        case jump
        case tween(Double, UnitCurve)
        case spring(Spring)
        /// Up to 1 in 30 ms and back to 0 in 120 ms: a flick.
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
        case .spring(let spring): return t > 3 ? to : from + spring.value(target: to - from, initialVelocity: speed, time: t)
        case .pulse: return to * (t < 0.03 ? t / 0.03 : max(0, 1 - (t - 0.03) / 0.12))
        }
    }

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0, t <= 3, case .spring(let spring) = kind else { return 0 }
        return spring.velocity(target: to - from, initialVelocity: speed, time: t)
    }

    /// Heads for `target` from where it is now.
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
    /// The square the dial is drawn in.
    static let size = 320.0
    static let rays = 28
    static let units = 60.0

    // Radii: a ray's two ends, the eight markers and the hub.
    static let inner = 83.0
    static let outer = 140.0
    static let marker = 151.0
    static let hub = 24.0
    /// How far a ray shoots out as it lights, and how far the hub's dot leans.
    static let flick = 4.0
    static let lean = 3.0
    /// The value's and the caption's distance from the centre.
    static let textY = 54.0
    /// Touches closer to the centre than this do not turn the dial.
    static let dead = 12.0

    // Seconds: a ray's change of weight, the sweep to a finger that just landed, and the chase after it.
    static let lightTime = 0.08
    static let reachTime = 0.3
    static let followTime = 0.1

    // autoPlay (seconds): when each sweep starts, where it ends, how long it takes and on which curve.
    static let script: [(at: Double, to: Double, time: Double, curve: UnitCurve)] = [
        (0.8, 15, 1.0, Ease.out),
        (2.5, 40, 1.2, .easeInOut),
        (4.4, 25, 0.7, .easeInOut),
        (5.7, 0, 1.0, .easeInOut),
    ]
    static let loop = 8.0
    /// After a touch the loop waits this long, then takes `returnTime` to sweep back.
    static let resumeAfter = 2.0
    static let returnTime = 0.6
}

private let snapSpring = Spring(mass: 1, stiffness: 400, damping: 26)
private let pressSpring = Spring(mass: 1, stiffness: 320, damping: 22)

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

#Preview {
    ZStack {
        Color(hex: 0xF5F4F1).ignoresSafeArea()
        SunburstTimerDial(autoPlay: true)
    }
}
