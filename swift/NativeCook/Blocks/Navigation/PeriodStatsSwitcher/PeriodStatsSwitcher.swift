import SwiftUI

/// Everything the screen shows for one period.
struct PeriodStatsSwitcherStats: Equatable {
    /// A day or a month to remember: its figure and when it was.
    struct Mark: Equatable {
        var value: Double
        var when: String
        var caption: String
    }

    /// A label under the chart, and the slot it stands under.
    struct Tick: Equatable {
        var at: Int
        var text: String
        var strong = false
    }

    /// The switch's word for it.
    var name: String
    /// What the row under the switch calls this period, then the ones before it.
    var labels: [String]
    var total: Double
    var distance: Double
    var floors: Double
    var calories: Double
    /// The chart's top value.
    var scale: Double
    /// Bar heights as fractions of the chart, one to a slot; 0 leaves the slot empty.
    var bars: [Double]
    /// Slots whose goal was not met, drawn grey.
    var missed: [Int] = []
    var axis: [Tick]
    var average: Double
    var averageCaption: String
    /// This period against the last one: the difference and its percentage.
    var change: Double
    var percent: Int
    var changeCaption: String
    var best: Mark? = nil
    var worst: Mark? = nil

    static let samples = [
        PeriodStatsSwitcherStats(
            name: "Week", labels: ["Today, 28 Aug", "19 – 25 Aug", "12 – 18 Aug", "5 – 11 Aug"],
            total: 5052, distance: 3.4, floors: 19, calories: 278, scale: 5984,
            bars: [0.85, 0.75, 0.73, 0, 0, 0, 0],
            axis: ["26\nMon", "27\nTue", "28\nWed", "29\nThu", "30\nFri", "31\nSat", "1\nSun"].enumerated().map { Tick(at: $0.offset, text: $0.element, strong: $0.offset == 2) },
            average: 5408, averageCaption: "Daily avg.", change: -391, percent: -7, changeCaption: "vs last week avg."
        ),
        PeriodStatsSwitcherStats(
            name: "Month", labels: ["Aug 2024", "Jul 2024", "Jun 2024", "May 2024"],
            total: 162_779, distance: 110.5, floors: 332, calories: 3262, scale: 9525,
            bars: [
                0.52, 0.68, 0.61, 0.8, 0.47, 0.74, 0.9, 0.63, 0.95, 0.3, 0.58, 0.86, 0.71, 0.66, 0.92, 0.55, 0.78, 0.84, 0.6, 0.49, 0.73, 0.88, 0.64, 0.57,
                0.7, 0.81, 0.62, 0.76, 0, 0,
            ],
            missed: [8, 9],
            axis: [0, 4, 8, 12, 16, 20, 24, 28].map { Tick(at: $0, text: "\($0 + 1)") },
            average: 5813, averageCaption: "Daily avg.", change: -583, percent: -10, changeCaption: "vs last month avg.",
            best: Mark(value: 9525, when: "9 Aug", caption: "Best day"), worst: Mark(value: 1441, when: "10 Aug", caption: "Worst day")
        ),
        PeriodStatsSwitcherStats(
            name: "Year", labels: ["2024", "2023", "2022", "2021"],
            total: 1_172_691, distance: 811.3, floors: 2382, calories: 13_075, scale: 198_312,
            bars: [0.6, 0.64, 0.66, 0.74, 0.8, 0.86, 0.95, 0.7, 0, 0, 0, 0],
            axis: ["Jan", "Mar", "May", "Jul", "Sep", "Nov"].enumerated().map { Tick(at: $0.offset * 2, text: $0.element) },
            average: 146_586, averageCaption: "Monthly avg.", change: 17_766, percent: 12, changeCaption: "vs last year avg.",
            best: Mark(value: 198_312, when: "Jul", caption: "Best month"), worst: Mark(value: 110_127, when: "Jan", caption: "Worst month")
        ),
    ]

    /// The period as it was `back` steps ago: every figure times a factor, the bars turned round.
    func earlier(_ back: Int) -> PeriodStatsSwitcherStats {
        guard back > 0 else { return self }
        let by = Metrics.earlier[back % Metrics.earlier.count]
        let count = bars.count
        var copy = self
        copy.total *= by
        copy.distance *= by
        copy.floors *= by
        copy.calories *= by
        copy.average *= by
        copy.change *= -by
        copy.percent = -percent
        copy.bars = bars.indices.map { bars[($0 + back * Metrics.turn) % count] }
        copy.missed = missed.map { ($0 + count - (back * Metrics.turn) % count) % count }
        return copy
    }
}

/// A statistics screen under a three-way switch. Changing the period rebuilds what is under it:
/// the goal disc gives way to a large rolling total and the page closes up, every figure rolls to
/// its new digits, the old bars sink and a new set grows from the baseline, left to right.
struct PeriodStatsSwitcher: View {
    /// The periods of the switch, shortest first; the first one shows the goal disc.
    var periods = PeriodStatsSwitcherStats.samples
    /// The first period's goal, written in its disc.
    var goal = 3000.0
    var unit = "steps"
    var totalCaption = "Total Walk"
    /// Goes from period to period by itself every `PeriodStatsSwitcherMotion.loop` seconds.
    var autoPlay = false
    /// Called with the period's place in `periods` when the switch moves.
    var onSelect: ((Int) -> Void)? = nil
    var onClose: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var period = 0
    /// How many periods back the arrows have gone.
    @State private var back = 0
    /// The bars that are leaving the chart, and when the chart's clock started.
    @State private var left: PeriodStatsSwitcherStats? = nil
    @State private var chartStart = Date.distantPast
    @State private var chartRuns = false
    /// The disc's ring: 0 is empty, 1 full and solid.
    @State private var ring = 1.0
    @State private var held = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UISelectionFeedbackGenerator()

    private var stats: PeriodStatsSwitcherStats { periods[period].earlier(back) }
    private var first: Bool { period == 0 }
    private var week: PeriodStatsSwitcherStats { periods[0].earlier(first ? back : 0) }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)

            ZStack(alignment: .topLeading) {
                // One group: what comes and goes inside it rides with it while it moves.
                lower(width: size.width)
                    .frame(width: size.width, alignment: .topLeading)
                    .geometryGroup()
                    .offset(y: insets.top + Metrics.bodyTop - (first ? 0 : Metrics.bodyRise))

                headline(width: size.width)
                    .offset(y: insets.top)
                    .allowsHitTesting(false)

                topBar(width: size.width)
                    .offset(y: insets.top + Metrics.switchTop)

                periodRow
                    .frame(width: size.width - (Metrics.margin - 7) * 2, height: 22)
                    .offset(x: Metrics.margin - 7, y: insets.top + Metrics.periodTop)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .animation(reduceMotion ? nil : .easeInOut(duration: M.shiftFor), value: first)
            .contentShape(Rectangle())
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        if !held {
                            held = true
                            touches += 1
                        }
                    }
                    .onEnded { value in
                        held = false
                        let across = value.translation.width
                        if abs(across) > Metrics.swipe, abs(across) > abs(value.translation.height) * 2 { swiped(across < 0 ? 1 : -1) }
                    }
            )
            .ignoresSafeArea()
        }
        .background(Palette.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { haptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The top

    private func topBar(width: Double) -> some View {
        let segment = (width - Metrics.switchLeft - Metrics.margin - Metrics.switchInset * 2) / Double(periods.count)

        return HStack(spacing: 0) {
            Button {
                onClose?()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                    .frame(width: Metrics.switchLeft - Metrics.margin, height: Metrics.switchHeight, alignment: .leading)
                    .padding(.leading, Metrics.margin)
                    .contentShape(Rectangle())
            }
            .buttonStyle(DimStyle())
            .accessibilityLabel("Close")

            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 7)
                    .fill(.white)
                    .shadow(color: .black.opacity(0.1), radius: 1.5, y: 1)
                    .frame(width: segment)
                    .offset(x: Double(period) * segment)
                    .animation(reduceMotion ? nil : .interpolatingSpring(mass: 1, stiffness: M.capsuleStiffness, damping: M.capsuleDamping), value: period)
                HStack(spacing: 0) {
                    ForEach(Array(periods.enumerated()), id: \.offset) { index, item in
                        Button {
                            select(index)
                        } label: {
                            Text(item.name)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(index == period ? Palette.ink : Palette.segmentInk)
                                .frame(width: segment)
                                .frame(maxHeight: .infinity)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(index == period ? [.isSelected] : [])
                    }
                }
            }
            .padding(Metrics.switchInset)
            .frame(height: Metrics.switchHeight)
            .background(RoundedRectangle(cornerRadius: 8).fill(Palette.track))
        }
        .frame(width: width - Metrics.margin, alignment: .leading)
    }

    private var periodRow: some View {
        HStack(spacing: 0) {
            Button {
                step(1)
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(back < stats.labels.count - 1 ? Palette.ink : Palette.faint)
                    .frame(width: 22, height: 22)
                    .contentShape(Rectangle().inset(by: -12))
            }
            .accessibilityLabel("Earlier")
            Spacer(minLength: 0)
            Text(stats.labels[back])
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .id(stats.labels[back])
                .transition(.opacity)
            Spacer(minLength: 0)
            Button {
                step(-1)
            } label: {
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(back > 0 ? Palette.ink : Palette.faint)
                    .frame(width: 22, height: 22)
                    .contentShape(Rectangle().inset(by: -12))
            }
            .accessibilityLabel("Later")
        }
        .buttonStyle(DimStyle())
        .animation(.easeInOut(duration: M.swap), value: stats.labels[back])
    }

    /// The headline: a goal disc for the first period, a large total for the others.
    private func headline(width: Double) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 4) {
                PeriodStatsSwitcherRoll(
                    text: Self.grouped(first ? week.total : stats.total),
                    font: .system(size: Metrics.figure, weight: .heavy).italic(), tracking: -1, line: Metrics.figureLine, color: Palette.navy
                )
                Text(totalCaption)
                    .font(.system(size: 13))
                    .foregroundStyle(Palette.mute)
            }
            .opacity(first ? 0 : 1)
            .offset(x: Metrics.margin, y: Metrics.figureTop)

            PeriodStatsSwitcherDisc(
                fill: ring,
                // Away from the first period the disc holds zeros, so that its figure counts up when it returns.
                figure: first ? Self.grouped(week.total) : String(Self.grouped(week.total).map { $0.isNumber ? "0" : $0 }),
                caption: "of \(Self.grouped(goal)) \(unit)"
            )
            .frame(width: Metrics.disc, height: Metrics.disc)
            .geometryGroup()
            .scaleEffect(first ? 1 : 0.2)
            .opacity(first ? 1 : 0)
            .offset(x: (width - Metrics.disc) / 2 - (first ? 0 : width / 2 - Metrics.margin - Metrics.disc * 0.1), y: Metrics.discTop)
        }
        .frame(width: width, height: Metrics.bodyTop, alignment: .topLeading)
    }

    // MARK: Under the headline

    private func lower(width: Double) -> some View {
        let slot = (width - Metrics.margin * 2) / Double(stats.bars.count)
        let cardWidth = (width - Metrics.margin * 2 - Metrics.cardGap) / 2
        let up = stats.change >= 0

        return ZStack(alignment: .topLeading) {
            HStack(spacing: 0) {
                total(String(format: "%.1f", stats.distance), "KM", "Total distance")
                total(Self.grouped(stats.floors), "", "Total floors")
                total(Self.grouped(stats.calories), "kcal", "Total calories")
            }
            .padding(.horizontal, Metrics.margin)

            // The chart: one clock for the set that sinks and the set that grows.
            TimelineView(.animation(paused: !chartRuns)) { timeline in
                let t = chartRuns ? timeline.date.timeIntervalSince(chartStart) : M.chartFor
                Canvas { context, canvas in
                    if let left { Self.draw(left, leaving: true, at: t, still: reduceMotion, in: &context, size: canvas) }
                    Self.draw(stats, leaving: false, at: t, still: reduceMotion, in: &context, size: canvas)
                }
            }
            .frame(width: width, height: Metrics.chart)
            .background(Palette.panel)
            .overlay(alignment: .topLeading) {
                Text(Self.grouped(stats.scale))
                    .font(.system(size: 10))
                    .monospacedDigit()
                    .foregroundStyle(Palette.panelInk)
                    .id(stats.scale)
                    .transition(.opacity)
                    .padding(.leading, Metrics.margin)
                    .padding(.top, 6)
            }
            .offset(y: Metrics.chartTop)
            .accessibilityLabel("\(stats.name) chart")

            ZStack(alignment: .topLeading) {
                ForEach(stats.axis, id: \.at) { tick in
                    Text(tick.text)
                        .font(.system(size: 11, weight: tick.strong ? .bold : .regular))
                        .foregroundStyle(tick.strong ? Palette.ink : Palette.mute)
                        .multilineTextAlignment(.center)
                        .frame(width: 60, alignment: .top)
                        .offset(x: Metrics.margin + slot * Double(tick.at) + slot / 2 - 30)
                }
            }
            .frame(width: width, height: 28, alignment: .topLeading)
            .id(period)
            .transition(.opacity)
            .offset(y: Metrics.axisTop)

            HStack(spacing: 5) {
                Rectangle().fill(Palette.mute).frame(width: 10, height: 1.5)
                Text("Goal line")
                RoundedRectangle(cornerRadius: 1.5).fill(Palette.green).frame(width: 7, height: 7).padding(.leading, 9)
                Text("Goal meet")
                RoundedRectangle(cornerRadius: 1.5).fill(Palette.missed).frame(width: 7, height: 7).padding(.leading, 9)
                Text("Goal not meet")
            }
            .font(.system(size: 11))
            .foregroundStyle(Palette.mute)
            .frame(width: width - Metrics.margin * 2, height: 24)
            .background(RoundedRectangle(cornerRadius: 6).fill(Palette.strip))
            .offset(x: Metrics.margin, y: Metrics.legendTop)

            HStack(spacing: Metrics.cardGap) {
                card(width: cardWidth, height: Metrics.card) {
                    Image(systemName: "figure.run")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Palette.green)
                        .frame(width: 22, height: 22)
                    PeriodStatsSwitcherRoll(text: Self.grouped(stats.average), font: .system(size: 22, weight: .bold), tracking: 0, line: 28, color: Palette.navy)
                    Text(stats.averageCaption)
                }
                card(width: cardWidth, height: Metrics.card) {
                    Image(systemName: up ? "arrow.up.right" : "arrow.down.left")
                        .font(.system(size: 10, weight: .heavy))
                        .foregroundStyle(.white)
                        .frame(width: 22, height: 22)
                        .background(Circle().fill(up ? Palette.green : Palette.red))
                    HStack(spacing: 0) {
                        Text(up ? "+" : "-").font(.system(size: 22, weight: .bold)).frame(height: 28)
                        PeriodStatsSwitcherRoll(text: Self.grouped(stats.change), font: .system(size: 22, weight: .bold), tracking: 0, line: 28, color: up ? Palette.green : Palette.red)
                    }
                    .foregroundStyle(up ? Palette.green : Palette.red)
                    Text(stats.changeCaption)
                }
                .overlay(alignment: .topTrailing) {
                    Text("\(up ? "+" : "-")\(abs(stats.percent))%")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(up ? Palette.green : Palette.red)
                        .padding(14)
                }
            }
            .offset(x: Metrics.margin, y: Metrics.cardsTop)

            // The second row belongs to the longer periods; it follows the first in and out.
            if let best = stats.best, let worst = stats.worst {
                HStack(spacing: Metrics.cardGap) {
                    ForEach(Array([best, worst].enumerated()), id: \.offset) { index, mark in
                        card(width: cardWidth, height: Metrics.cardTall) {
                            Image(systemName: index == 0 ? "trophy.fill" : "face.dashed")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(index == 0 ? Palette.gold : Palette.mute)
                                .frame(width: 22, height: 22)
                            PeriodStatsSwitcherRoll(text: Self.grouped(mark.value), font: .system(size: 22, weight: .bold), tracking: 0, line: 28, color: Palette.navy)
                            Text(mark.when)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(Palette.ink)
                            Text(mark.caption)
                        }
                    }
                }
                .id(period)
                .transition(
                    .asymmetric(
                        insertion: .opacity.combined(with: .offset(y: M.rowRise)).animation(.easeOut(duration: M.swap).delay(M.rowAfter)),
                        removal: .opacity.animation(.linear(duration: M.sink))
                    )
                )
                .offset(x: Metrics.margin, y: Metrics.cardsTop + Metrics.card + Metrics.cardGap)
            }
        }
        .animation(.easeInOut(duration: M.swap), value: stats.scale)
        .animation(.easeInOut(duration: M.swap), value: period)
    }

    private func total(_ figure: String, _ unit: String, _ caption: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .bottom, spacing: 2) {
                PeriodStatsSwitcherRoll(text: figure, font: .system(size: 24, weight: .bold), tracking: 0, line: 28, color: Palette.navy)
                Text(unit)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Palette.navy)
                    .padding(.bottom, 4)
            }
            Text(caption)
                .font(.system(size: 11))
                .foregroundStyle(Palette.mute)
        }
        .lineLimit(1)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func card(width: Double, height: Double, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 0, content: content)
            .font(.system(size: 11))
            .foregroundStyle(Palette.mute)
            .lineLimit(1)
            .padding(.horizontal, 14)
            .padding(.top, 12)
            .frame(width: width, height: height, alignment: .topLeading)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(.white))
    }

    /// Draws one set of bars `t` seconds into the chart's clock.
    private static func draw(_ set: PeriodStatsSwitcherStats, leaving: Bool, at t: Double, still: Bool, in context: inout GraphicsContext, size: CGSize) {
        let slot = (size.width - Metrics.margin * 2) / Double(set.bars.count)
        let wide = min(Metrics.barWidest, slot * Metrics.barShare)
        for (index, share) in set.bars.enumerated() where share > 0 {
            // Reduced motion: the two sets cross-fade at full height.
            let fade = M.clamp01(t / M.cross)
            let grown = still ? 1 : leaving ? 1 - M.clamp01(t / M.sink) : M.grown(t - M.sink - Double(index) * M.barStep)
            let height = Metrics.chart * share * grown
            guard height > 0 else { continue }
            let bar = CGRect(x: Metrics.margin + slot * Double(index) + (slot - wide) / 2, y: size.height - height, width: wide, height: height)
            let shape = Path(roundedRect: bar, cornerRadii: RectangleCornerRadii(topLeading: 2, topTrailing: 2))
            context.fill(shape, with: .color((set.missed.contains(index) ? Palette.missed : Palette.green).opacity(still ? (leaving ? 1 - fade : fade) : 1)))
        }
    }

    // MARK: Behaviour

    /// Shows a period, `steps` back: the figures roll by themselves, the chart's clock starts over.
    private func show(_ index: Int, _ steps: Int) {
        guard index != period || steps != back else { return }
        let changed = index != period
        left = stats
        chartStart = .now
        chartRuns = true
        period = index
        back = steps
        let started = chartStart
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.chartFor))
            if chartStart == started { chartRuns = false }
        }
        guard changed else { return }
        if index == 0 {
            // The disc comes back as a ring that fills, then turns solid.
            // Empty first, and full from the next turn on, or the two would be one change.
            if !reduceMotion {
                ring = 0
                Task { @MainActor in
                    withAnimation(.easeOut(duration: M.ringFor).delay(M.ringAfter)) { ring = 1 }
                }
            }
        }
        onSelect?(index)
    }

    private func select(_ index: Int) {
        guard index != period else { return }
        haptic.selectionChanged()
        show(index, 0)
    }

    private func step(_ by: Int) {
        let next = back + by
        guard next >= 0, next < periods[period].labels.count else { return }
        haptic.selectionChanged()
        show(period, next)
    }

    private func swiped(_ by: Int) {
        let next = period + by
        if next >= 0, next < periods.count { select(next) }
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled, !held else { return }
            // The loop starts over from the first period, once it has settled there.
            show(0, 0)
            try? await clock.sleep(for: .seconds((M.ringAfter + M.ringFor) * 2))
            guard !Task.isCancelled else { return }
        }
        var origin = clock.now
        while !Task.isCancelled {
            for step in M.steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                show(step.to, 0)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private static func grouped(_ value: Double) -> String {
        var digits = String(Int(abs(value).rounded()))
        var out = ""
        while digits.count > 3 {
            out = "," + digits.suffix(3) + out
            digits = String(digits.dropLast(3))
        }
        return digits + out
    }

    private struct DimStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.opacity(configuration.isPressed ? 0.5 : 1)
        }
    }
}

/// The motion's numbers, shared by the screen and its parts.
enum PeriodStatsSwitcherMotion {
    /// autoPlay: Week, Month, Year and back to Week.
    static let loop = 10.0
    static let steps: [(at: Double, to: Int)] = [(2.4, 1), (4.8, 2), (7.4, 0)]
    /// After a touch autoPlay waits this long before it goes back to the first period and starts over.
    static let userHold = 3.0

    // The switch's capsule and the layout follow the period on a quick spring and a 350 ms ease.
    static let capsuleStiffness = 420.0
    static let capsuleDamping = 34.0
    static let shiftFor = 0.35
    // The disc comes back as a ring that fills, then turns solid.
    static let ringAfter = 0.15
    static let ringFor = 0.8
    static let solidFrom = 0.85
    // A digit column turns to its new digit; columns start one after another, left to right.
    static let rollFor = 0.65
    static let rollStep = 0.04
    // The chart's clock, in seconds: the old bars sink, then the new ones grow one after another on
    // a spring that passes their height by about 6%.
    static let sink = 0.15
    static let barStep = 0.012
    static let barOmega = 20.0
    static let barZeta = 0.667
    static let barRest = 0.9
    static let chartFor = sink + 30 * barStep + barRest
    static let cross = 0.25
    static let swap = 0.25
    // The second row of cards follows the first.
    static let rowAfter = 0.06
    static let rowRise = 10.0

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    /// How much of its height a bar has `t` seconds after it started to grow.
    static func grown(_ t: Double) -> Double {
        if t <= 0 { return 0 }
        if t >= barRest { return 1 }
        let damped = barOmega * (1 - barZeta * barZeta).squareRoot()
        return 1 - exp(-barZeta * barOmega * t) * (cos(damped * t) + barZeta * barOmega / damped * sin(damped * t))
    }
}

private typealias M = PeriodStatsSwitcherMotion

private enum Palette {
    static let page = Color(hex: 0xF1F0F7)
    static let navy = Color(hex: 0x212557)
    static let ink = Color(hex: 0x111111)
    static let mute = Color(hex: 0x8D8CA0)
    static let faint = Color(hex: 0xC8C7D4)
    static let track = Color(hex: 0xE4E3EC)
    static let segmentInk = Color(hex: 0x55546A)
    static let green = Color(hex: 0x26CE47)
    static let panel = Color(hex: 0xDFEDE8)
    static let panelInk = Color(hex: 0x7FA08F)
    static let missed = Color(hex: 0xC3D2CB)
    static let red = Color(hex: 0xE5484D)
    static let gold = Color(hex: 0xF2B022)
    static let strip = Color(hex: 0xE8E7EF)
}

private enum Metrics {
    /// Earlier periods are made from the given one: every figure times one of these, the bars turned round.
    static let earlier = [1, 0.86, 1.12, 0.93]
    static let turn = 3
    static let margin = 24.0
    // From the safe area's top.
    static let switchTop = 20.0
    static let switchHeight = 30.0
    static let switchLeft = 60.0
    static let switchInset = 2.0
    static let periodTop = 67.0
    static let discTop = 116.0
    static let disc = 226.0
    static let ring = 12.0
    static let figureTop = 100.0
    static let figure = 46.0
    static let figureLine = 52.0
    // Everything from the totals down: where it starts under the disc, and how far it rises without it.
    static let bodyTop = 360.0
    static let bodyRise = 170.0
    // Inside that body.
    static let chartTop = 62.0
    static let chart = 160.0
    static let barWidest = 30.0
    static let barShare = 0.62
    static let axisTop = chartTop + chart + 8
    static let legendTop = chartTop + chart + 44
    static let cardsTop = legendTop + 48
    static let card = 92.0
    static let cardTall = 106.0
    static let cardGap = 12.0
    static let swipe = 50.0
}

/// A figure whose digits roll to a new value, the leftmost first: each digit is a strip of the ten
/// digits behind a one-line window.
struct PeriodStatsSwitcherRoll: View {
    let text: String
    let font: Font
    let tracking: Double
    let line: Double
    let color: Color

    /// A character and its place counted from the right: a column keeps that place, so that a
    /// longer figure grows at the left.
    private struct Column: Identifiable {
        let id: String
        let order: Int
        let character: Character
    }

    private var columns: [Column] {
        let characters = Array(text)
        return characters.enumerated().map { index, character in
            Column(id: "\(characters.count - index)" + (character.isNumber ? "" : String(character)), order: index, character: character)
        }
    }

    var body: some View {
        HStack(spacing: 0) {
            ForEach(columns) { column in
                if let digit = column.character.wholeNumberValue {
                    VStack(spacing: 0) {
                        ForEach(0..<10, id: \.self) { Text("\($0)").frame(height: line) }
                    }
                    .offset(y: -Double(digit) * line)
                    .animation(.timingCurve(0.23, 1, 0.32, 1, duration: M.rollFor).delay(Double(column.order) * M.rollStep), value: digit)
                    .frame(height: line, alignment: .top)
                    // Wider than the digit by the padding, so that an italic is not cut at its sides.
                    .padding(.horizontal, 3)
                    .clipped()
                    .padding(.horizontal, -3)
                } else {
                    Text(String(column.character)).frame(height: line)
                }
            }
        }
        .font(font)
        .tracking(tracking)
        .monospacedDigit()
        .foregroundStyle(color)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }
}

/// The goal disc: a white disc whose green ring fills with `fill`, turning into a solid green disc
/// with a ring of white dots over the last part of it.
struct PeriodStatsSwitcherDisc: View, Animatable {
    var fill: Double
    let figure: String
    let caption: String

    var animatableData: Double {
        get { fill }
        set { fill = newValue }
    }

    var body: some View {
        let solid = M.clamp01((fill - M.solidFrom) / (1 - M.solidFrom))
        ZStack {
            Circle().fill(.white)
            Circle()
                .trim(from: 0, to: M.clamp01(fill))
                .stroke(Palette.green, style: StrokeStyle(lineWidth: Metrics.ring, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .padding(Metrics.ring / 2)
            face(ink: Palette.navy, note: Palette.mute)
            ZStack {
                Circle().fill(Palette.green)
                Circle()
                    .stroke(.white, style: StrokeStyle(lineWidth: 3, lineCap: .round, dash: [0.01, Double.pi * (Metrics.disc - 32) / 48 - 0.01]))
                    .padding(16)
                face(ink: .white, note: .white.opacity(0.8))
            }
            .opacity(solid)
        }
    }

    private func face(ink: Color, note: Color) -> some View {
        VStack(spacing: 0) {
            Image(systemName: "figure.walk")
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(ink)
                .frame(height: 30)
            PeriodStatsSwitcherRoll(text: figure, font: .system(size: 46, weight: .bold), tracking: -0.5, line: 52, color: ink)
            Text(caption)
                .font(.system(size: 12))
                .foregroundStyle(note)
                .frame(height: 16)
        }
    }
}

#Preview {
    PeriodStatsSwitcher(autoPlay: true)
}
