import SwiftUI

private enum S {
    static let skyTop = Color(hex: 0x2a0f3a)
    static let skyBottom = Color(hex: 0x120818)
    static let panel = Color(hex: 0x1d1027)
    static let ink = Color.white
    static let mute = Color(hex: 0x9c8ba8)
    static let axis = Color(hex: 0x8a8a8e)
    static let weekend = Color(hex: 0x5e5068)
    static let button = Color.white.opacity(0.08)
    static let track = Color(hex: 0x2d1b3c)
    static let thumb = Color(hex: 0x4a3560)
    static let good = Color(hex: 0x3ddc97)
    static let badgeInk = Color(hex: 0x111111)
    static let tabBar = Color(hex: 0x1a1220)
    static let tabActive = Color(hex: 0xff2d55)

    // Layout, in points.
    static let margin = 20.0
    static let nav = 56.0
    static let roundButton = 40.0
    static let segment = 36.0
    static let column = 14.0
    // The chart: room above the columns for the floating label, right for the time axis, below for days.
    static let labelSpace = 44.0
    static let axisWidth = 50.0
    static let days = 46.0
    static let dayBadge = CGSize(width: 28, height: 38)
    static let timeLabel = CGSize(width: 104, height: 22)
    static let timeLabelGap = 7.0
    static let tabHeight = 60.0
    // The time axis: 23:30 to 09:00, labelled every two hours.
    static let axisFrom = -30.0
    static let axisTo = 540.0
    static let grid = [0.0, 120, 240, 360, 480]
    static let idealAsleep = (-15.0, 45.0)
    static let usualWake = (420.0, 510.0)
    static let modes = ["Duration", "Consistency", "Stages"]
}

private enum M {
    static let follow = SleepColumnScrubberSpring.Params(damping: 22, stiffness: 300, mass: 0.6)
    static let press = SleepColumnScrubberSpring.Params(damping: 18, stiffness: 260, mass: 0.6)
    // Seconds for a column to take or give up the lift.
    static let liftFor = 0.16

    // The autoPlay timeline, in seconds from the start of each loop; the finger's place is in columns.
    static let loop = 9.0
    struct Move { var from: Double, to: Double, start: Double, end: Double }
    struct Sweep { var down: Double, up: Double, moves: [Move] }
    static let sweeps = [
        Sweep(down: 0.8, up: 4.4, moves: [Move(from: 13, to: 0, start: 0.95, end: 4.0)]),
        Sweep(down: 4.9, up: 8.1, moves: [Move(from: 11, to: 3, start: 5.0, end: 6.6), Move(from: 3, to: 13, start: 6.8, end: 7.9)]),
    ]
    // Seconds after the last touch before the chart returns to tonight and the loop starts again.
    static let userHold = 3.0
    static let resumeAfter = 0.4

    /// Smooth start and stop over 0…1.
    static func glide(_ t: Double) -> Double {
        let x = min(1, max(0, t))
        return x * x * (3 - 2 * x)
    }
}

/// A spring sampled against the clock; retargeting keeps its velocity, as Reanimated's does.
private struct SleepColumnScrubberSpring {
    struct Params { var damping: Double, stiffness: Double, mass: Double }

    private var from: Double
    private(set) var to: Double
    private var velocity = 0.0
    private var start = Date.distantPast
    private var params: Params?

    init(_ value: Double) {
        from = value
        to = value
    }

    func sample(_ date: Date) -> (value: Double, velocity: Double) {
        guard let params else { return (to, 0) }
        // Both springs here are under-damped (damping ratio below 1).
        let t = max(0, date.timeIntervalSince(start))
        let z = params.damping / (2 * params.mass)
        let w = sqrt(params.stiffness / params.mass - z * z)
        let a = from - to
        let b = (velocity + z * a) / w
        let decay = exp(-z * t)
        return (to + decay * (a * cos(w * t) + b * sin(w * t)), decay * (velocity * cos(w * t) - (z * b + w * a) * sin(w * t)))
    }

    func value(_ date: Date) -> Double { sample(date).value }

    /// When it is within a thousandth of a point of its target, in the units it moves in.
    var end: Date {
        guard let params else { return start }
        let z = params.damping / (2 * params.mass)
        let w = sqrt(params.stiffness / params.mass - z * z)
        let a = from - to
        let amplitude = hypot(a, (velocity + z * a) / w)
        return amplitude > 1e-3 ? start.addingTimeInterval(log(amplitude / 1e-3) / z) : start
    }

    mutating func target(_ value: Double, _ date: Date, _ params: Params?) {
        guard let params else {
            self = SleepColumnScrubberSpring(value)
            return
        }
        let now = sample(date)
        from = now.value
        velocity = now.velocity
        to = value
        start = date
        self.params = params
    }
}

/// A 0…1 lift on Reanimated's default `withTiming` curve (ease in-out, quadratic).
private struct SleepColumnScrubberLift {
    var from = 0.0
    var to = 0.0
    var start = Date.distantPast
    var duration = 0.0

    func value(_ date: Date) -> Double {
        guard duration > 0 else { return to }
        let t = min(1, max(0, date.timeIntervalSince(start) / duration))
        let eased = t < 0.5 ? 2 * t * t : 1 - pow(2 - 2 * t, 2) / 2
        return from + (to - from) * eased
    }

    mutating func target(_ value: Double, _ date: Date, _ duration: Double) {
        from = self.value(date)
        to = value
        start = date
        self.duration = duration
    }
}

/// The finger on the chart, real or played by autoPlay, and everything that follows it. A
/// reference type read by the timeline each frame, so following it never writes view state.
private final class SleepColumnScrubberFinger {
    /// The finger's place in columns, and whether it is down.
    var scrubAt: Double
    private(set) var touching = false
    var holding = false
    var byHand = false
    /// When the autoPlay loop started, nil when it is not running; `seen` is the loop time it last acted on.
    var loopOrigin: Date?
    private var seen = -1.0
    private(set) var selected: Int
    private var press = SleepColumnScrubberSpring(0)
    private var columnX: SleepColumnScrubberSpring
    private var columnTop: SleepColumnScrubberSpring
    private var lifts: [SleepColumnScrubberLift]
    private(set) var settled = Date.distantPast

    init(home: Int, asleep: Double, count: Int) {
        scrubAt = Double(home)
        selected = home
        columnX = SleepColumnScrubberSpring(Double(home))
        columnTop = SleepColumnScrubberSpring(asleep)
        lifts = (0..<count).map { SleepColumnScrubberLift(from: $0 == home ? 1 : 0, to: $0 == home ? 1 : 0) }
    }

    func setTouching(_ down: Bool, _ date: Date, still: Bool) {
        guard down != touching else { return }
        touching = down
        press.target(down ? 1 : 0, date, still ? nil : M.press)
        settled = max(settled, press.end)
    }

    /// Follows `scrubAt` to its column; returns the new column when it changed.
    func follow(_ date: Date, nights: [SleepColumnScrubberNight], still: Bool) -> Int? {
        let next = min(nights.count - 1, max(0, Int((scrubAt + 0.5).rounded(.down))))
        guard next != selected else { return nil }
        let before = selected
        selected = next
        columnX.target(Double(next), date, still ? nil : M.follow)
        columnTop.target(nights[next].asleep, date, still ? nil : M.follow)
        lifts[before].target(0, date, still ? 0 : M.liftFor)
        lifts[next].target(1, date, still ? 0 : M.liftFor)
        settled = max(settled, columnX.end, columnTop.end, date.addingTimeInterval(M.liftFor))
        return next
    }

    /// The autoPlay loop moves a finger that is not there, through the same values a touch sets.
    func play(_ date: Date, nights: [SleepColumnScrubberNight], still: Bool) -> Int? {
        guard let loopOrigin else { return nil }
        let elapsed = max(0, date.timeIntervalSince(loopOrigin))
        let t = elapsed.truncatingRemainder(dividingBy: M.loop)
        let was = seen > t ? -1 : seen
        seen = t
        for sweep in M.sweeps {
            if was < sweep.down && t >= sweep.down {
                scrubAt = sweep.moves[0].from
                setTouching(true, date, still: still)
            }
            for (j, move) in sweep.moves.enumerated() where t >= move.start && t <= sweep.up && (j + 1 == sweep.moves.count || t < sweep.moves[j + 1].start) {
                scrubAt = move.from + (move.to - move.from) * M.glide((t - move.start) / (move.end - move.start))
            }
            if was < sweep.up && t >= sweep.up { setTouching(false, date, still: still) }
        }
        return follow(date, nights: nights, still: still)
    }

    func begin(_ date: Date) {
        byHand = false
        seen = -1
        loopOrigin = date
    }

    func pressValue(_ date: Date) -> Double { press.value(date) }
    func columnXValue(_ date: Date) -> Double { columnX.value(date) }
    func columnTopValue(_ date: Date) -> Double { columnTop.value(date) }
    func lift(_ index: Int, _ date: Date) -> Double { lifts[index].value(date) }
}

/// A sleep consistency chart: one column per night from falling asleep to waking, striped with its
/// sleep stages. Dragging across the chart widens and rings the night under the finger, dims the
/// others, slides the white day badge to it and moves a label above it whose times roll.
struct SleepColumnScrubber: View {
    var title: String
    var nights: [SleepColumnScrubberNight]
    var modes: [String]
    var asleepStatus: String
    var variabilityStatus: String
    /// Moves a finger over the nights by itself every `loop` seconds and comes back to the last one.
    var autoPlay: Bool
    var onSelect: ((SleepColumnScrubberNight, Int) -> Void)?
    var onModeChange: ((String, Int) -> Void)?
    var onBack: (() -> Void)?

    /// The night shown when the screen opens, by its place in `nights`.
    private let home: Int
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @State private var mode: Int
    @State private var finger: SleepColumnScrubberFinger
    @State private var drawing = SleepColumnScrubberSleepColumns()
    /// The timeline runs while the loop plays or a finger is down, and until what they moved settles.
    @State private var live = false
    @State private var restAt = Date.distantPast
    @State private var touches = 0
    @State private var haptic = UISelectionFeedbackGenerator()

    init(
        title: String = "Sleep",
        nights: [SleepColumnScrubberNight] = SleepColumnScrubberData.nights,
        initialNight: Int? = nil,
        modes: [String] = S.modes,
        initialMode: Int = 1,
        asleepStatus: String = "STABLE",
        variabilityStatus: String = "NORMAL",
        autoPlay: Bool = false,
        onSelect: ((SleepColumnScrubberNight, Int) -> Void)? = nil,
        onModeChange: ((String, Int) -> Void)? = nil,
        onBack: (() -> Void)? = nil
    ) {
        self.title = title
        self.nights = nights
        self.modes = modes
        self.asleepStatus = asleepStatus
        self.variabilityStatus = variabilityStatus
        self.autoPlay = autoPlay
        self.onSelect = onSelect
        self.onModeChange = onModeChange
        self.onBack = onBack
        let home = initialNight ?? nights.count - 1
        self.home = home
        _mode = State(initialValue: initialMode)
        _finger = State(initialValue: SleepColumnScrubberFinger(home: home, asleep: nights[home].asleep, count: nights.count))
    }

    private var averageAsleep: Double { SleepColumnScrubberData.mean(nights.map(\.asleep)) }
    private var averageAwake: Double { SleepColumnScrubberData.mean(nights.map(\.awake)) }

    var body: some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                nav
                panel(bottomInset: proxy.safeAreaInsets.bottom)
                    .padding(.top, 12)
            }
        }
        .background(LinearGradient(colors: [S.skyTop, S.skyBottom], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: restAt) {
            guard (try? await wait(until: restAt)) != nil else { return }
            if finger.loopOrigin == nil && !finger.holding { live = false }
        }
        .task(id: touches) { await play() }
        .onChange(of: autoPlay) { touches += 1 }
    }

    // MARK: Parts

    private var nav: some View {
        ZStack {
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(S.ink)
            HStack {
                Button { onBack?() } label: { round(.back, 20) }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Back")
                Spacer()
                HStack(spacing: 8) {
                    round(.calendar, 18)
                    round(.options, 20)
                }
                .accessibilityHidden(true)
            }
            .padding(.horizontal, 16)
        }
        .frame(height: S.nav)
    }

    private func round(_ icon: SleepColumnScrubberIcon, _ size: Double) -> some View {
        SleepColumnScrubberIconView(icon: icon, size: size, color: S.ink)
            .frame(width: S.roundButton, height: S.roundButton)
            .background(S.button, in: Circle())
    }

    private func panel(bottomInset: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Overview")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(S.ink)
                .frame(height: 22)
            segments
                .padding(.top, 14)
            stats
                .padding(.top, 18)
            TimelineView(.animation(paused: !live)) { timeline in
                chart(live ? timeline.date : .distantFuture, playing: live)
            }
            .padding(.top, 4)
            legend
                .padding(.top, 8)
            HStack(spacing: 16) {
                ForEach(SleepColumnScrubberStage.allCases, id: \.self) { stage in
                    HStack(spacing: 6) {
                        Circle().fill(stage.color).frame(width: 8, height: 8)
                        legendText(stage.label)
                    }
                }
            }
            .padding(.top, 8)
            tabBar
                .frame(maxWidth: .infinity)
                .padding(.top, 16)
                .padding(.bottom, max(bottomInset, 12) - bottomInset)
        }
        .padding(.top, 20)
        .padding(.horizontal, S.margin)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28, style: .continuous)
                .fill(S.panel)
                .ignoresSafeArea(edges: .bottom)
        )
    }

    private var segments: some View {
        GeometryReader { proxy in
            let width = (proxy.size.width - 6) / Double(modes.count)
            ZStack(alignment: .topLeading) {
                Capsule()
                    .fill(S.thumb)
                    .frame(width: width, height: S.segment - 6)
                    .offset(x: 3 + Double(mode) * width, y: 3)
                    .animation(reducedMotion ? nil : .interpolatingSpring(mass: 0.6, stiffness: 260, damping: 18), value: mode)
                HStack(spacing: 0) {
                    ForEach(Array(modes.enumerated()), id: \.offset) { index, label in
                        Button {
                            mode = index
                            onModeChange?(label, index)
                        } label: {
                            Text(label)
                                .font(.system(size: 13, weight: index == mode ? .semibold : .medium))
                                .foregroundStyle(index == mode ? S.ink : S.mute)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(index == mode ? [.isSelected] : [])
                    }
                }
                .padding(3)
            }
            .frame(width: proxy.size.width, height: S.segment, alignment: .topLeading)
            .background(S.track, in: Capsule())
        }
        .frame(height: S.segment)
    }

    private var stats: some View {
        let variability = Int((SleepColumnScrubberData.spread(nights.map(\.asleep)) + 0.5).rounded(.down))
        return HStack(alignment: .top, spacing: 0) {
            stat("Avg. fall asleep", Text(SleepColumnScrubberData.clock(averageAsleep)), asleepStatus)
            stat(
                "Fall asleep variability",
                Text("\(variability)") + Text("m").font(.custom("Menlo", size: 13).weight(.medium)).foregroundStyle(S.mute),
                variabilityStatus
            )
        }
    }

    private func stat(_ caption: String, _ value: Text, _ status: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(caption)
                .font(.system(size: 12))
                .foregroundStyle(S.mute)
                .frame(height: 16)
            value
                .font(.custom("Menlo", size: 28).bold())
                .foregroundStyle(S.ink)
                .frame(height: 34)
                .padding(.top, 2)
            HStack(spacing: 5) {
                SleepColumnScrubberIconView(icon: .check, size: 11, color: S.badgeInk)
                    .frame(width: 14, height: 14)
                    .background(S.good, in: RoundedRectangle(cornerRadius: 4))
                Text(status)
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(0.4)
                    .foregroundStyle(S.ink)
                    .frame(height: 14)
            }
            .padding(.top, 2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func chart(_ date: Date, playing: Bool) -> some View {
        GeometryReader { proxy in
            let size = proxy.size
            let plotWidth = max(0, size.width - S.axisWidth)
            let step = nights.isEmpty ? 0 : plotWidth / Double(nights.count)
            let frame = SleepColumnScrubberChartFrame(
                width: size.width, top: S.labelSpace, bottom: max(S.labelSpace, size.height - S.days - 8),
                from: S.axisFrom, to: S.axisTo, first: step / 2, step: step, column: min(S.column, step * 0.65), plotWidth: plotWidth
            )
            let selection = playing ? advance(date) : finger.selected
            let night = nights[selection]
            let press = finger.pressValue(date)
            let columnX = frame.first + frame.step * finger.columnXValue(date)
            let columnTop = frame.y(finger.columnTopValue(date))
            let lifts = nights.indices.map { finger.lift($0, date) }
            let labelX = min(size.width + S.margin - 8 - S.timeLabel.width, max(8 - S.margin, columnX - S.timeLabel.width / 2))

            ZStack(alignment: .topLeading) {
                Canvas { context, _ in
                    drawing.draw(context, frame: frame, nights: nights, bands: bands, on: lifts, press: press, still: reducedMotion)
                }
                .frame(width: frame.width, height: frame.bottom + 24)
                ForEach(axisLabels, id: \.minutes) { label in
                    Text(SleepColumnScrubberData.clock(label.minutes))
                        .font(.system(size: 10, weight: label.strong ? .semibold : .regular).monospacedDigit())
                        .foregroundStyle(label.strong ? S.ink : S.axis)
                        .fixedSize()
                        .frame(height: 14)
                        .offset(x: plotWidth + 6, y: frame.y(label.minutes) - 7)
                }
                dayRow(step: step, color: nil)
                    .offset(y: frame.bottom + 10)
                dayRow(step: step, color: S.badgeInk)
                    .offset(x: S.dayBadge.width / 2 - columnX, y: 3)
                    .frame(width: S.dayBadge.width, height: S.dayBadge.height, alignment: .topLeading)
                    .background(S.ink)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                    .offset(x: columnX - S.dayBadge.width / 2, y: frame.bottom + 10 - 3)
                S.ink
                    .frame(width: 1, height: S.timeLabelGap)
                    .offset(x: columnX - 0.5, y: columnTop - S.timeLabelGap)
                SleepColumnScrubberRoller(
                    text: "\(SleepColumnScrubberData.clock(night.asleep))—\(SleepColumnScrubberData.clock(night.awake))",
                    font: .system(size: 12, weight: .bold).monospacedDigit(),
                    color: S.badgeInk,
                    lineHeight: 16,
                    still: reducedMotion
                )
                .frame(width: S.timeLabel.width, height: S.timeLabel.height)
                .background(S.ink, in: Capsule())
                .shadow(color: .black.opacity(0.35), radius: 6, x: 0, y: 4)
                .offset(x: labelX, y: columnTop - S.timeLabelGap - S.timeLabel.height)
                .allowsHitTesting(false)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(scrub(frame))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(night.weekday) \(night.day), \(SleepColumnScrubberData.clock(night.asleep)) to \(SleepColumnScrubberData.clock(night.awake))")
            .accessibilityAddTraits(.allowsDirectInteraction)
        }
    }

    private var bands: SleepColumnScrubberBands {
        SleepColumnScrubberBands(ideal: S.idealAsleep, wake: S.usualWake, grid: S.grid, averageAsleep: averageAsleep, averageAwake: averageAwake)
    }

    private var axisLabels: [(minutes: Double, strong: Bool)] {
        S.grid.filter { abs($0 - averageAsleep) > 22 && abs($0 - averageAwake) > 22 }.map { ($0, false) }
            + [(averageAsleep, true), (averageAwake, true)]
    }

    /// Two lines under each column, weekday and day of the month; `color` nil keeps the axis colours.
    private func dayRow(step: Double, color: Color?) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(nights.enumerated()), id: \.offset) { _, night in
                VStack(spacing: 0) {
                    Text(night.weekday)
                    Text("\(night.day)")
                }
                .font(.system(size: 11, weight: .medium).monospacedDigit())
                .foregroundStyle(color ?? (night.weekday == "Su" ? S.weekend : S.axis))
                .frame(width: step)
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .fixedSize()
    }

    private var legend: some View {
        HStack {
            HStack(spacing: 6) {
                RoundedRectangle(cornerRadius: 3).fill(Color.white.opacity(0.12)).frame(width: 12, height: 12)
                legendText("Ideal fall asleep")
            }
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                RoundedRectangle(cornerRadius: 3)
                    .strokeBorder(Color.white.opacity(0.35), style: StrokeStyle(lineWidth: 1, dash: [2, 2]))
                    .frame(width: 12, height: 12)
                legendText("Usual wake-up")
            }
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                S.ink.frame(width: 12, height: 1.5)
                legendText("Avg. fall asleep")
            }
        }
    }

    private func legendText(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11))
            .foregroundStyle(S.mute)
            .frame(height: 14)
    }

    private var tabBar: some View {
        HStack(spacing: 0) {
            tab(.today, "Today", S.tabActive, on: true)
            tab(.trending, "Progress", S.ink, on: false)
            tab(.barbell, "Workouts", S.ink, on: false)
        }
        .padding(4)
        .background(S.tabBar, in: Capsule())
        .overlay(Capsule().strokeBorder(Color.white.opacity(0.08), lineWidth: 1 / 3))
        .accessibilityHidden(true)
    }

    private func tab(_ icon: SleepColumnScrubberIcon, _ label: String, _ color: Color, on: Bool) -> some View {
        VStack(spacing: 3) {
            SleepColumnScrubberIconView(icon: icon, size: 20, color: color)
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(color)
                .frame(height: 14)
        }
        .frame(width: 92, height: S.tabHeight)
        .background(on ? Color.white.opacity(0.07) : .clear, in: Capsule())
    }

    // MARK: Behaviour

    /// Moves the autoPlay finger to `date`; a new night is reported after the frame is drawn.
    private func advance(_ date: Date) -> Int {
        if let index = finger.play(date, nights: nights, still: reducedMotion) {
            let night = nights[index]
            DispatchQueue.main.async { onSelect?(night, index) }
        }
        return finger.selected
    }

    private func picked(_ index: Int?) {
        guard let index else { return }
        if finger.byHand && finger.touching {
            haptic.selectionChanged()
            haptic.prepare()
        }
        onSelect?(nights[index], index)
    }

    private func scrub(_ frame: SleepColumnScrubberChartFrame) -> some Gesture {
        func columnAt(_ x: Double) -> Double { frame.step > 0 ? (x - frame.first) / frame.step : 0 }
        return DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if !finger.holding {
                    // The loop stops here, before the finger's first move.
                    finger.loopOrigin = nil
                    finger.byHand = true
                    finger.holding = true
                    finger.setTouching(true, now, still: reducedMotion)
                    live = true
                    touches += 1
                }
                finger.scrubAt = columnAt(value.location.x)
                picked(finger.follow(now, nights: nights, still: reducedMotion))
            }
            .onEnded { _ in
                let now = Date.now
                finger.holding = false
                finger.setTouching(false, now, still: reducedMotion)
                restAt = max(now, finger.settled)
                touches += 1
            }
    }

    private func wait(until date: Date) async throws {
        while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }
        try Task.checkCancellation()
    }

    private func play() async {
        guard autoPlay else { return }
        do {
            if touches > 0 {
                try await wait(until: .now.addingTimeInterval(M.userHold))
                // A finger still on the chart puts the loop off again when it lifts.
                guard !finger.holding else { return }
                let now = Date.now
                finger.setTouching(false, now, still: reducedMotion)
                finger.scrubAt = Double(home)
                picked(finger.follow(now, nights: nights, still: reducedMotion))
                live = true
                restAt = max(now, finger.settled)
                try await wait(until: now.addingTimeInterval(M.resumeAfter))
            }
            finger.begin(.now)
            live = true
        } catch {}
    }
}
