import SwiftUI
import UIKit

/// One day of the strip, already in words.
struct DateLensStripDay: Equatable {
    /// "FRI"
    var weekday: String
    /// "06"
    var number: String
    /// "NOVEMBER": shown in place of the label for a moment when the lens crosses into this month.
    var month: String

    /// `count` days in a row, starting at the given date.
    static func run(year: Int, month: Int, day: Int, count: Int) -> [DateLensStripDay] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? calendar.timeZone
        guard let first = calendar.date(from: DateComponents(year: year, month: month, day: day)) else { return [] }
        let weekdays = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"]
        let months = ["JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"]
        return (0..<count).compactMap { offset in
            guard let date = calendar.date(byAdding: .day, value: offset, to: first) else { return nil }
            let parts = calendar.dateComponents([.weekday, .day, .month], from: date)
            let number = parts.day ?? 1
            return DateLensStripDay(
                weekday: weekdays[((parts.weekday ?? 1) - 1) % 7],
                number: number < 10 ? "0\(number)" : "\(number)",
                month: months[((parts.month ?? 1) - 1) % 12]
            )
        }
    }

    /// Four weeks from Monday 26 October 2026; Friday 6 November is index 11.
    static let samples = run(year: 2026, month: 10, day: 26, count: 28)
}

/// A date picker in a white capsule: a label on the left, and a strip of days that slides under a
/// fixed lens, a tall blue capsule that stands proud of the bar and shows the day beneath it in white.
/// Dragging moves the strip, and it settles on the nearest day; tapping a day brings it under the
/// lens. The lens is soft: it stretches and leans with the strip's speed and wobbles as it stops.
struct DateLensStrip: View {
    var days = DateLensStripDay.samples
    /// Index of the day that starts under the lens.
    var initial = 11
    var label = "PICK A DATE:"
    /// Slides the strip through a short script by itself, in a loop.
    var autoPlay = false
    /// Called with the index of the day the strip is settling on.
    var onSelect: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the script and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The strip: the index under the lens, continuous while it slides.
        var strip = DateLensStripTrack(0)
        /// The label's roll: 0 the label, 1 a month's name.
        var roll = DateLensStripTrack(0)
        var month = ""
        /// When the month's name rolls away again.
        var rollBack = Date.distantPast
        /// The lens's stretch, −1…1 and a little beyond: a spring that chases the strip's speed.
        var stretch = 0.0
        var stretchSpeed = 0.0
        /// The strip's position and the time when the last frame was drawn.
        var seen = 0.0
        var seenAt = Date.distantPast
        /// The day nearest the lens at that frame.
        var nearest = 0
        /// Where the strip was when a finger landed; nil while nothing touches it.
        var dragFrom: Double?
        /// The finger's travel when the touch turned into a drag; nil while it may still be a tap.
        var dragZero: Double?
        /// The strip is sliding under a finger, not under the script: only then it ticks.
        var byHand = false
        let haptic = UISelectionFeedbackGenerator()
    }

    private var auto: Bool { autoPlay && !reduceMotion && days.count > 1 }

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation) { timeline in
                let now = timeline.date
                let strip = motion.strip.value(at: now)
                let _ = notice(strip, now)

                Canvas { context, size in
                    draw(context, size: size, strip: strip, roll: motion.roll.value(at: now), stretch: reduceMotion ? 0 : motion.stretch)
                }
            }
            .contentShape(Rectangle())
            .gesture(drag(geometry.size))
        }
        .frame(height: Metrics.height)
        .onAppear { reset() }
        .onChange(of: days.count) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
        .accessibilityElement()
        .accessibilityLabel(label)
        .accessibilityValue(days.isEmpty ? "" : "\(days[bounded(motion.nearest)].weekday) \(days[bounded(motion.nearest)].number)")
        .accessibilityAdjustableAction { direction in
            touch()
            settle(Double(motion.nearest + (direction == .increment ? 1 : -1)))
        }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Drawing

    private func draw(_ context: GraphicsContext, size: CGSize, strip: Double, roll: Double, stretch: Double) {
        let bar = CGRect(x: Metrics.margin, y: (size.height - Metrics.barH) / 2, width: size.width - Metrics.margin * 2, height: Metrics.barH)
        let middle = bar.midY
        let capsule = Path(roundedRect: bar, cornerRadius: Metrics.barH / 2)

        var shade = context
        shade.addFilter(.shadow(color: .black.opacity(0.1), radius: 12, x: 0, y: 8))
        shade.fill(capsule, with: .color(.white))

        var inside = context
        inside.clip(to: capsule)
        // The label rolls up and a month's name rolls in from below, then back.
        let title = Text(label).font(.system(size: 13, weight: .bold)).foregroundStyle(Color(hex: 0x1414C8))
        var top = inside
        top.opacity = 1 - clamp01(roll)
        top.draw(title, at: CGPoint(x: bar.minX + Metrics.labelX, y: middle - Metrics.rollY * roll), anchor: .leading)
        if roll > 0 {
            let name = Text(motion.month + ":").font(.system(size: 13, weight: .bold)).foregroundStyle(Color(hex: 0x1414C8))
            var bottom = inside
            bottom.opacity = clamp01(roll)
            bottom.draw(name, at: CGPoint(x: bar.minX + Metrics.labelX, y: middle + Metrics.rollY * (1 - roll)), anchor: .leading)
        }
        drawDays(inside, bar: bar, strip: strip, lit: false)

        // The lens stretches sideways with the strip's speed, thins, and leans the way the days travel.
        let pull = min(abs(stretch), 1.15)
        let width = Metrics.lensW + Metrics.stretchW * pull
        let height = Metrics.lensH - Metrics.squashH * pull
        let lens = Path(
            roundedRect: CGRect(x: bar.minX + Metrics.lensX - Metrics.lean * stretch - width / 2, y: middle - height / 2, width: width, height: height),
            cornerRadius: width / 2
        )
        context.fill(lens, with: .color(Color(hex: 0x0000F5)))
        // The same days again in white, cut at the lens's edge: a number half-way in is half white.
        var glass = context
        glass.clip(to: lens)
        drawDays(glass, bar: bar, strip: strip, lit: true)
    }

    private func drawDays(_ context: GraphicsContext, bar: CGRect, strip: Double, lit: Bool) {
        for index in days.indices {
            let away = Double(index) - strip
            guard abs(away) < Metrics.reach else { continue }
            let x = bar.minX + Metrics.lensX + away * Metrics.pitch
            // Days fade as they slide under the label and into the bar's right end.
            let fade = lit ? 1 : min(clamp01((x - bar.minX - Metrics.fadeFrom) / Metrics.fadeOver), clamp01((bar.maxX - Metrics.endFade - x) / Metrics.endOver))
            guard fade > 0 else { continue }
            let day = days[index]
            var column = context
            column.opacity = fade
            column.translateBy(x: x, y: bar.midY)
            let grow = 1 + Metrics.grow * clamp01(1 - abs(away))
            column.scaleBy(x: grow, y: grow)
            column.draw(
                Text(day.weekday).font(.system(size: 9.5, weight: .medium)).tracking(0.3).foregroundStyle(lit ? Color.white : Color(hex: 0x8C8C8C)),
                at: CGPoint(x: 0, y: -Metrics.weekdayY)
            )
            column.draw(
                Text(day.number).font(.system(size: 21, weight: .bold)).monospacedDigit().foregroundStyle(lit ? Color.white : Color.black),
                at: CGPoint(x: 0, y: Metrics.numberY)
            )
        }
    }

    // MARK: The strip

    private func bounded(_ index: Int) -> Int {
        min(max(index, 0), max(days.count - 1, 0))
    }

    private func reset() {
        let start = bounded(initial)
        motion.strip = DateLensStripTrack(Double(start))
        motion.roll = DateLensStripTrack(0)
        motion.stretch = 0
        motion.stretchSpeed = 0
        motion.seen = Double(start)
        motion.seenAt = .distantPast
        motion.nearest = start
        motion.dragFrom = nil
        motion.dragZero = nil
    }

    /// Runs every frame: steps the lens's spring towards the strip's speed, and when another day comes
    /// nearest the lens it ticks, and rolls the label if the month changed.
    private func notice(_ strip: Double, _ now: Date) {
        let passed = now.timeIntervalSince(motion.seenAt)
        if passed > 0, passed < 0.25 {
            let pull = min(max((strip - motion.seen) / passed / Metrics.fullSpeed, -1), 1)
            // Small steps keep the spring steady when a frame is late.
            let steps = Int((passed / Metrics.tick).rounded(.up))
            let dt = passed / Double(steps)
            for _ in 0..<steps {
                motion.stretchSpeed += (Metrics.lensStiffness * (pull - motion.stretch) - Metrics.lensDamping * motion.stretchSpeed) * dt
                motion.stretch += motion.stretchSpeed * dt
            }
            if pull == 0, abs(motion.stretch) < 0.001, abs(motion.stretchSpeed) < 0.01 {
                motion.stretch = 0
                motion.stretchSpeed = 0
            }
        }
        motion.seen = strip
        motion.seenAt = now

        if motion.roll.value(at: now) > 0, now >= motion.rollBack {
            motion.rollBack = .distantFuture
            motion.roll.retarget(0, reduceMotion ? .jump : .spring(rollSpring))
        }

        let nearest = bounded(Int(strip.rounded()))
        guard nearest != motion.nearest, !days.isEmpty else { return }
        if days[nearest].month != days[bounded(motion.nearest)].month {
            motion.month = days[nearest].month
            motion.roll.retarget(1, reduceMotion ? .jump : .spring(rollSpring))
            motion.rollBack = now.addingTimeInterval(Metrics.monthTime)
        }
        motion.nearest = nearest
        if motion.byHand { motion.haptic.selectionChanged() }
    }

    /// Sends the strip to a day, carrying on at `velocity` days per second.
    private func settle(_ target: Double, velocity: Double = 0) {
        let day = Double(bounded(Int(target.rounded())))
        motion.strip.retarget(day, reduceMotion ? .jump : .spring(stripSpring), velocity: velocity)
        if !days.isEmpty { onSelect?(Int(day)) }
    }

    private func touch() {
        motion.byHand = true
        touches += 1
    }

    /// One gesture for the whole bar: a touch catches the strip, a drag slides it and lets go with
    /// momentum, and a touch that never moved brings the day under it to the lens.
    private func drag(_ size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if motion.dragFrom == nil {
                    let held = motion.strip.value(at: .now)
                    motion.strip.retarget(held, .jump)
                    motion.dragFrom = held
                    motion.dragZero = nil
                    motion.haptic.prepare()
                    touch()
                }
                let travel = value.translation.width
                if motion.dragZero == nil, abs(travel) > Metrics.slop { motion.dragZero = travel }
                guard let from = motion.dragFrom, let zero = motion.dragZero else { return }
                // Past either end the strip follows at a third of the finger's speed.
                let free = from - (travel - zero) / Metrics.pitch
                let last = Double(max(days.count - 1, 0))
                let held = free < 0 ? free * Metrics.rubber : free > last ? last + (free - last) * Metrics.rubber : free
                motion.strip.retarget(held, .jump)
            }
            .onEnded { value in
                let strip = motion.strip.value(at: .now)
                if motion.dragZero == nil {
                    let x = value.startLocation.x - Metrics.margin
                    let onDays = x > Metrics.fadeFrom && x < size.width - Metrics.margin * 2 && abs(value.startLocation.y - size.height / 2) < Metrics.lensH / 2
                    settle(onDays ? (strip + (x - Metrics.lensX) / Metrics.pitch).rounded() : strip.rounded())
                } else {
                    let speed = min(max(-value.velocity.width / Metrics.pitch, -Metrics.maxSpeed), Metrics.maxSpeed)
                    settle((strip + speed * Metrics.glide).rounded(), velocity: speed)
                }
                motion.dragFrom = nil
                motion.dragZero = nil
                touch()
            }
    }

    /// The script a finger would follow: four moves along the strip, the last one back to where it began.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, bring the first day back, and start over from rest.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                if motion.dragFrom != nil { return }
            }
            motion.byHand = false
            settle(Double(initial))
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for step in Metrics.script {
                    try await clock.sleep(until: start + .seconds(step.at))
                    settle(Double(initial + step.days))
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// A number heading for a target, sampled against the `TimelineView` clock.
struct DateLensStripTrack {
    enum Kind {
        case jump
        case spring(Spring)
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
        // Past three seconds a spring has long settled: the strip is then exactly on its day.
        case .spring(let spring): return t > 3 ? to : from + spring.value(target: to - from, initialVelocity: speed, time: t)
        }
    }

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0, t <= 3, case .spring(let spring) = kind else { return 0 }
        return spring.velocity(target: to - from, initialVelocity: speed, time: t)
    }

    /// Heads for `target` from where it is now, keeping its speed unless `velocity` gives another.
    mutating func retarget(_ target: Double, _ kind: Kind, velocity: Double? = nil) {
        let now = Date.now
        let value = value(at: now)
        speed = velocity ?? self.velocity(at: now)
        from = value
        to = target
        start = now
        self.kind = kind
    }
}

private enum Metrics {
    /// The component's height: the lens and room for it to wobble.
    static let height = 120.0
    static let margin = 16.0
    static let barH = 64.0

    // Measured from the bar's left end: where the label starts, the lens's centre, and the days' pitch.
    static let labelX = 22.0
    static let lensX = 214.0
    static let pitch = 62.0
    static let lensW = 66.0
    static let lensH = 90.0
    /// How much wider and lower the lens gets at full stretch, and how far it leans.
    static let stretchW = 12.0
    static let squashH = 6.0
    static let lean = 5.0
    /// The strip's speed, in days per second, that stretches the lens all the way.
    static let fullSpeed = 12.0
    // The lens's spring (response 0.4 s, damping 0.6), stepped at most `tick` seconds at a time.
    static let lensStiffness = 247.0
    static let lensDamping = 18.85
    static let tick = 1.0 / 240

    /// A day is 1.12× under the lens and back to 1× one day away.
    static let grow = 0.12
    static let weekdayY = 18.0
    static let numberY = 10.0
    /// Days further than this from the lens are not drawn.
    static let reach = 5.0
    // A day is gone at `fadeFrom` from the bar's left end and whole `fadeOver` further; the same near the right end.
    static let fadeFrom = 130.0
    static let fadeOver = 22.0
    static let endFade = 14.0
    static let endOver = 34.0
    /// How far the label travels as it rolls, and how long a month's name stays.
    static let rollY = 22.0
    static let monthTime = 1.2

    // Dragging: the travel that makes a touch a drag, how far a flick carries, and the pull past the ends.
    static let slop = 8.0
    static let glide = 0.16
    static let maxSpeed = 24.0
    static let rubber = 0.35

    // autoPlay (seconds): when the strip sets off, and to which day, counted from the resting one.
    static let script: [(at: Double, days: Int)] = [(1.0, 1), (2.25, 5), (4.05, 3), (5.35, 0)]
    static let loop = 7.0
    /// After a touch the loop waits this long before it starts over.
    static let resumeAfter = 2.0
}

private let stripSpring = Spring(mass: 1, stiffness: 180, damping: 17.5)
private let rollSpring = Spring(mass: 1, stiffness: 260, damping: 24)

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

#Preview {
    ZStack {
        Color(hex: 0xE6E6E8).ignoresSafeArea()
        DateLensStrip(autoPlay: true)
    }
}
