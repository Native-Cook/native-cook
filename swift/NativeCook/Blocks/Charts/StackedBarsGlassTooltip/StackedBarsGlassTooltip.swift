import SwiftUI

private typealias Macros = StackedBarsGlassTooltipMacros

private enum S {
    static let sky = [Color(hex: 0xfde8f2), Color(hex: 0xe9e4ff), Color(hex: 0xddebff)]
    static let ink = Color(hex: 0x1b1b2f)
    static let mute = Color(hex: 0x8a8aa0)
    static let soft = Color(hex: 0x6b6b85)
    static let line = Color(hex: 0xefeff5)
    static let baseline = Color(hex: 0xe5e5f0)
    static let card = Color.white
    static let up = Color(hex: 0xff5c5c)
    static let upFill = Color(hex: 0xffecec)
    static let shadow = Color.rgba(60, 40, 120, 1)
    // Bottom to top, each a gradient from its top to its bottom.
    static let protein = [Color(hex: 0x2d8bff), Color(hex: 0x00b7ff)]
    static let fat = [Color(hex: 0xc77dff), Color(hex: 0xd99aff)]
    static let carbs = [Color(hex: 0xffd95a), Color(hex: 0xffc130)]
    static let swatch = (carbs: Color(hex: 0xffb800), fat: Color(hex: 0xc77dff), protein: Color(hex: 0x2d8bff))

    static let margin = 16.0
    static let cardPadding = 20.0
    static let cardRadius = 28.0
    static let cardMaxHeight = 620.0
    static let axis = 30.0
    static let labels = 22.0
    static let gridLines = 5
    static let column = 28.0
    // A column takes this share of its slot, up to `column` wide.
    static let columnShare = 0.62
    static let segmentGap = 2.0
    static let columnRadius = 10.0
    static let lift = 6.0
    static let dim = 0.45
    static let tipWidth = 140.0
    static let tipHeight = 96.0
    static let tipRadius = 16.0
    static let overhang = 8.0
    // The tooltip floats this far above the hovered column, and may rise this far over the legend.
    static let tipGap = 10.0
    static let tipRise = 44.0
    static let finger = 40.0
    static let segmented = 32.0
    static let segmentWidth = 64.0

    static let morph = 0.8
    static let stillMorph = 0.3
    static let focusIn = 0.15
    static let focusOut = 0.2
    // The tooltip glides to a new column (300 ms, damping ratio 0.82); the finger ring trails the
    // touch (380 ms, critically damped). Perceptual durations, as Reanimated reads them.
    static let glide = StackedBarsGlassTooltipSpring.Config(duration: 0.3, dampingRatio: 0.82)
    static let trail = StackedBarsGlassTooltipSpring.Config(duration: 0.38, dampingRatio: 1)
    // Both springs are at rest well within this.
    static let settle = 0.8
    static let pill = Animation.timingCurve(0.33, 1, 0.68, 1, duration: 0.26)
    // Fades of the labels keyed by the range.
    static let periodIn = 0.2
    static let axisIn = 0.22
    static let datesIn = 0.22
    static let datesDelay = morph / 2
    static let datesOut = 0.15

    /// autoPlay, in seconds from the start of a loop: the finger goes down on a column, drags
    /// right, rests, drags back and lifts; then the control switches to the next range and back.
    static let loop = 9.0
    // time, column (fractions glide between columns)
    static let scrub: [(at: Double, column: Double)] = [(0.8, 1), (1.2, 1), (2.9, 6), (3.3, 6), (4.5, 2), (5.0, 2)]
    static let liftAt = 5.0
    static let switchAt = 5.6
    static let switchBackAt = 7.6
    /// After the last touch autoPlay waits this long, then comes back to rest.
    static let userHold = 3.0
    // The scripted finger's height in the chart.
    static let fingerHeight = 0.55

    /// Reanimated's default timing curve.
    static func quad(_ t: Double) -> Double {
        let x = min(1, max(0, t))
        return x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
    }

    static func cubic(_ t: Double) -> Double {
        let x = min(1, max(0, t))
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// The scripted finger's column at a time of the loop, eased between the rows of `scrub`.
    static func scriptedColumn(_ time: Double) -> Double {
        if time <= scrub[0].at { return scrub[0].column }
        for k in 1..<scrub.count where time < scrub[k].at {
            let a = scrub[k - 1], b = scrub[k]
            let u = (time - a.at) / (b.at - a.at)
            return a.column + (b.column - a.column) * u * u * (3 - 2 * u)
        }
        return scrub[scrub.count - 1].column
    }

    /// The times at which the scripted finger moves onto another column, and the column.
    static let columnSteps: [(at: Double, column: Int?)] = {
        var steps: [(Double, Int?)] = [(scrub[0].at, Int(scrub[0].column))]
        for k in 1..<scrub.count {
            let a = scrub[k - 1], b = scrub[k]
            let from = Int(a.column), to = Int(b.column)
            if from == to { continue }
            // The column changes where the glide crosses a half: the smoothstep's inverse, in closed form.
            for next in stride(from: from + (to > from ? 1 : -1), through: to, by: to > from ? 1 : -1) {
                let edge = Double(min(next, next - (to > from ? 1 : -1))) + 0.5
                let y = (edge - a.column) / (b.column - a.column)
                let u = 0.5 - sin(asin(1 - 2 * y) / 3)
                steps.append((a.at + (b.at - a.at) * u, next))
            }
        }
        steps.append((liftAt, nil))
        return steps
    }()

    /// 2500 → "2.5k", 450 → "450".
    static func axisLabel(_ value: Double) -> String {
        if value < 1000 { return String(Int(Macros.round(value))) }
        var text = String(format: "%.1f", value / 1000)
        if text.hasSuffix(".0") { text.removeLast(2) }
        return text + "k"
    }

    static func grouped(_ value: Double) -> String {
        let digits = String(Int(Macros.round(value)))
        var out = ""
        for (i, character) in digits.enumerated() {
            if i > 0 && (digits.count - i) % 3 == 0 { out.append(",") }
            out.append(character)
        }
        return out
    }
}

/// A damped spring of unit mass, solved exactly: its offset from the target and velocity after `dt`.
struct StackedBarsGlassTooltipSpring {
    struct Config {
        var stiffness: Double
        var damping: Double

        init(duration: Double, dampingRatio: Double) {
            stiffness = pow(2 * .pi / duration, 2)
            damping = 4 * .pi * dampingRatio / duration
        }
    }

    var value: Double
    var velocity = 0.0
    var target: Double
    var start = Date.distantPast
    var config: Config? = nil

    init(_ value: Double) {
        self.value = value
        target = value
    }

    static func step(offset x: Double, velocity v: Double, dt: Double, _ config: Config) -> (offset: Double, velocity: Double) {
        let w0 = sqrt(config.stiffness)
        let zeta = config.damping / (2 * w0)
        let decay = exp(-zeta * w0 * dt)
        if abs(zeta - 1) < 1e-6 {
            let b = v + w0 * x
            return ((x + b * dt) * decay, (v - w0 * b * dt) * decay)
        }
        let wd = w0 * sqrt(1 - zeta * zeta)
        let c = cos(wd * dt), s = sin(wd * dt)
        return (
            decay * (x * c + (v + zeta * w0 * x) / wd * s),
            decay * (v * c - (zeta * w0 * v + w0 * w0 * x) / wd * s)
        )
    }

    func sample(_ date: Date) -> (value: Double, velocity: Double) {
        guard let config else { return (target, 0) }
        let dt = max(0, date.timeIntervalSince(start))
        let next = Self.step(offset: value - target, velocity: velocity, dt: dt, config)
        return (target + next.offset, next.velocity)
    }

    /// Heads for `target` from where the spring is at `date`, keeping its speed; nil jumps there.
    mutating func retarget(_ target: Double, at date: Date, _ config: Config?) {
        if let config {
            let now = sample(date)
            value = now.value
            velocity = now.velocity
            self.config = config
        } else {
            value = target
            velocity = 0
            self.config = nil
        }
        self.target = target
        start = date
    }
}

/// A number from one value to another over a time span, on Reanimated's default curve.
private struct StackedBarsGlassTooltipFade {
    var from = 0.0
    var to = 0.0
    var start = Date.distantPast
    var duration = 0.0

    func value(at date: Date) -> Double {
        guard duration > 0 else { return to }
        return from + (to - from) * S.quad(date.timeIntervalSince(start) / duration)
    }
}

private struct StackedBarsGlassTooltipLegend {
    var label: String
    var color: Color
    var grams: (StackedBarsGlassTooltipDay) -> Int
}

/// What the finger and the focus look like at a moment.
private struct StackedBarsGlassTooltipMotion {
    var focus: Double
    var focusX: Double
    var trailX: Double
    var trailY: Double
}

/// The hand's finger and the springs it drives.
private struct StackedBarsGlassTooltipHand {
    var on = false
    var x = 0.0
    var y = 0.0
    var column = -1
    var focus = StackedBarsGlassTooltipFade()
    var focusX = StackedBarsGlassTooltipSpring(0)
    var trailX = StackedBarsGlassTooltipSpring(0)
    var trailY = StackedBarsGlassTooltipSpring(0)

    func motion(_ date: Date) -> StackedBarsGlassTooltipMotion {
        StackedBarsGlassTooltipMotion(
            focus: focus.value(at: date), focusX: focusX.sample(date).value,
            trailX: trailX.sample(date).value, trailY: trailY.sample(date).value
        )
    }
}

/// A number that rolls up when it changes: the old one leaves upward as the new one comes in.
private struct StackedBarsGlassTooltipRolling: View {
    var value: String
    var size: Double
    var lineHeight: Double
    var weight: Font.Weight
    var color: Color
    var alignment: Alignment = .leading
    var still: Bool

    var body: some View {
        ZStack(alignment: alignment) {
            Text(value)
                .font(.system(size: size, weight: weight))
                .monospacedDigit()
                .foregroundStyle(color)
                .lineLimit(1)
                .fixedSize()
                .frame(height: lineHeight)
                .id(value)
                .transition(still ? .identity : .asymmetric(
                    insertion: .opacity.combined(with: .offset(y: 25)).animation(.easeInOut(duration: 0.22)),
                    removal: .opacity.combined(with: .offset(y: -25)).animation(.easeInOut(duration: 0.18))
                ))
        }
        .clipped()
    }
}

/// A macros screen: a week of stacked columns of protein, fat and carbs on a white card. Press and
/// drag across the chart: the column under the finger lifts while the others dim, and a frosted
/// glass tooltip glides above it with that day's grams. Day, Week and Month morph the columns into
/// each other.
struct StackedBarsGlassTooltip: View {
    var title = "Macros"
    var heading = "How your intake changed over time"
    var hint = "Press and drag across the chart"
    /// The periods of the segmented control, each with its columns.
    var ranges = StackedBarsGlassTooltipMacros.ranges
    /// The range shown at rest; autoPlay switches to the next one and back.
    var initialRange = 1
    var carbsLabel = "Carbs"
    var fatLabel = "Fat"
    var proteinLabel = "Protein"
    /// Plays a scrub across the week and a switch to the month by itself, every LOOP seconds.
    var autoPlay = false
    var onRangeChange: ((StackedBarsGlassTooltipRange) -> Void)? = nil
    /// Called with the column under the finger, or nil when it lifts.
    var onSelect: ((Int?) -> Void)? = nil

    static let loop = S.loop

    @Environment(\.accessibilityReduceMotion) private var still
    @State private var range: Int? = nil
    @State private var hovered: Int? = nil
    @State private var fromBars: Macros.Bars? = nil
    @State private var toBars: Macros.Bars? = nil
    @State private var fromRange = 0
    @State private var switchAt = Date.distantPast
    @State private var automatic = false
    @State private var started = false
    @State private var origin = Date.now
    @State private var hand = StackedBarsGlassTooltipHand()
    @State private var holding = false
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    // The frame clock runs only while something moves; otherwise the screen is drawn at `sampleAt`.
    @State private var autoMoving = false
    @State private var handMoving = false
    @State private var busyUntil = Date.distantPast
    @State private var sampleAt = Date.now
    @State private var chart = CGSize(width: 1, height: 1)
    @State private var haptic = UISelectionFeedbackGenerator()

    private var current: Int { range ?? initialRange }
    private var restRange: Int { initialRange }
    private var otherRange: Int { (initialRange + 1) % ranges.count }
    private var paints: Macros.Paints {
        Macros.Paints(
            colors: [S.protein, S.fat, S.carbs], gap: S.segmentGap, radius: S.columnRadius, lift: S.lift,
            dim: S.dim, card: S.card, widest: S.column, share: S.columnShare
        )
    }
    private var morphFor: Double { still ? S.stillMorph : S.morph }

    var body: some View {
        GeometryReader { outer in
            let bottom = outer.safeAreaInsets.bottom
            VStack(spacing: 0) {
                titleRow
                    .padding(.top, 8)
                    .padding(.bottom, 20)
                card
                    .frame(maxHeight: S.cardMaxHeight)
                    .layoutPriority(1)
                Text(hint)
                    .font(.system(size: 13))
                    .foregroundStyle(S.soft)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, minHeight: 18)
                    .padding(.top, 16)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, S.margin)
            .padding(.top, 8)
            .padding(.bottom, max(bottom, 16) - bottom)
        }
        .background(LinearGradient(colors: S.sky, startPoint: .topLeading, endPoint: .bottomTrailing).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            haptic.prepare()
            if range == nil {
                range = initialRange
                fromRange = initialRange
                fromBars = Macros.bars(ranges[initialRange])
                toBars = fromBars
            }
        }
        .task(id: busyUntil) {
            do {
                try await wait(busyUntil)
                sampleAt = busyUntil
                handMoving = false
            } catch {}
        }
        .task(id: touches) { await autoplay() }
    }

    // MARK: Header and card

    private var titleRow: some View {
        HStack(alignment: .center, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.system(size: 30, weight: .bold))
                    .tracking(-0.6)
                    .foregroundStyle(S.ink)
                    .frame(height: 36)
                Text(ranges[current].period)
                    .font(.system(size: 13))
                    .foregroundStyle(S.soft)
                    .lineLimit(1)
                    .frame(height: 18)
                    .id(current)
                    .transition(still ? .identity : .asymmetric(insertion: .opacity.animation(.easeInOut(duration: S.periodIn)), removal: .identity))
                    .padding(.top, 2)
            }
            .padding(.trailing, 12)
            Spacer(minLength: 0)
            segmented
        }
    }

    private var segmented: some View {
        ZStack(alignment: .topLeading) {
            Capsule()
                .fill(S.card)
                .frame(width: S.segmentWidth - 6, height: S.segmented - 6)
                .shadow(color: S.shadow.opacity(0.14), radius: 4, x: 0, y: 2)
                .offset(x: 3 + Double(current) * S.segmentWidth, y: 3)
                .animation(still ? nil : S.pill, value: current)
            HStack(spacing: 0) {
                ForEach(Array(ranges.enumerated()), id: \.offset) { index, item in
                    Button { pressRange(index) } label: {
                        Text(item.name)
                            .font(.system(size: 13, weight: index == current ? .semibold : .medium))
                            .foregroundStyle(index == current ? S.ink : S.soft)
                            .frame(width: S.segmentWidth, height: S.segmented)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(index == current ? [.isSelected] : [])
                }
            }
        }
        .frame(height: S.segmented)
        .background(Capsule().fill(Color.white.opacity(0.7)))
    }

    private var card: some View {
        let range = ranges[current]
        let columns = range.columns
        let n = Double(columns.count)
        let average = { (grams: (StackedBarsGlassTooltipDay) -> Int) in columns.reduce(0.0) { $0 + Double(grams($1)) } / n }
        let averageKcal = columns.reduce(0.0) { $0 + Macros.calories($1) } / n
        return VStack(alignment: .leading, spacing: 0) {
            Text(heading)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(S.ink)
                .frame(height: 21)
            HStack(spacing: 0) {
                HStack(alignment: .firstTextBaseline, spacing: 0) {
                    Text(S.grouped(averageKcal))
                        .font(.system(size: 30, weight: .semibold))
                        .tracking(-0.6)
                        .monospacedDigit()
                        .foregroundStyle(S.ink)
                    Text(" cal")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(S.mute)
                }
                .frame(height: 36)
                Text("↑ \(String(format: "%.1f", range.change))%")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(S.up)
                    .padding(.horizontal, 8)
                    .frame(height: 22)
                    .background(Capsule().fill(S.upFill))
                    .padding(.leading, 10)
            }
            .padding(.top, 12)
            HStack(alignment: .top, spacing: 24) {
                ForEach(legend, id: \.label) { item in
                    VStack(alignment: .leading, spacing: 0) {
                        StackedBarsGlassTooltipRolling(
                            value: "\(Int(Macros.round(average(item.grams)))) g", size: 20, lineHeight: 25, weight: .semibold,
                            color: item.color, still: still
                        )
                        Text("Avg. \(item.label.lowercased())")
                            .font(.system(size: 11))
                            .foregroundStyle(S.mute)
                            .frame(height: 14)
                    }
                    .frame(minWidth: 64, alignment: .leading)
                }
            }
            .padding(.top, 14)
            .padding(.bottom, 12)
            chartArea
                .padding(.top, 8)
        }
        .padding(S.cardPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous)
                .fill(S.card)
                .shadow(color: S.shadow.opacity(0.12), radius: 20, x: 0, y: 16)
        )
        .overlay(RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous).strokeBorder(Color.white, lineWidth: 1))
    }

    private var legend: [StackedBarsGlassTooltipLegend] {
        [
            StackedBarsGlassTooltipLegend(label: carbsLabel, color: S.swatch.carbs, grams: { $0.carbs }),
            StackedBarsGlassTooltipLegend(label: fatLabel, color: S.swatch.fat, grams: { $0.fat }),
            StackedBarsGlassTooltipLegend(label: proteinLabel, color: S.swatch.protein, grams: { $0.protein }),
        ]
    }

    // MARK: Chart

    private var chartArea: some View {
        GeometryReader { proxy in
            let size = CGSize(width: max(1, proxy.size.width - S.axis), height: max(1, proxy.size.height - S.labels))
            let moving = automatic ? autoMoving : handMoving
            TimelineView(.animation(paused: !moving)) { timeline in
                let date = moving ? timeline.date : sampleAt
                let motion = motion(date, size)
                let to = toBars ?? Macros.bars(ranges[current])
                let x = motion.focusX
                let top = size.height - Macros.stackTop(to, x: x, width: size.width, height: size.height) - S.lift
                let tipY = max(-S.tipRise, top - S.tipGap - S.tipHeight)
                ZStack(alignment: .topLeading) {
                    // The canvas reaches above the chart, where the top axis label overhangs it.
                    Canvas { c, _ in
                        var chart = c
                        chart.translateBy(x: 0, y: S.overhang)
                        drawChart(chart, size, date, motion)
                    }
                    .frame(width: proxy.size.width, height: proxy.size.height + S.overhang)
                    .offset(y: -S.overhang)
                    Circle()
                        .fill(Color.white.opacity(0.12))
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.8), lineWidth: 1))
                        .frame(width: S.finger, height: S.finger)
                        .shadow(color: S.shadow.opacity(0.18), radius: 5, x: 0, y: 2)
                        .opacity(motion.focus)
                        .offset(x: S.axis + motion.trailX - S.finger / 2, y: motion.trailY - S.finger / 2)
                    // A hairline from the tooltip down to the top of the column, when there is room for one.
                    Rectangle()
                        .fill(Color.white.opacity(0.9))
                        .frame(width: 1, height: max(0, top - (tipY + S.tipHeight)))
                        .shadow(color: S.shadow.opacity(0.25), radius: 1.5)
                        .opacity(motion.focus)
                        .offset(x: S.axis + x - 0.5, y: tipY + S.tipHeight)
                    tooltip
                        .scaleEffect(still ? 1 : 0.94 + 0.06 * motion.focus)
                        .opacity(motion.focus)
                        .offset(x: S.axis + min(size.width - S.tipWidth, max(-S.axis, x - S.tipWidth / 2)), y: tipY)
                }
                .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let x = min(size.width - 1, max(0, value.location.x - S.axis))
                        let y = min(size.height, max(0, value.location.y))
                        if !holding {
                            holding = true
                            takeover(.now)
                            touched()
                        }
                        setFinger(on: true, x: x, y: y, size: size, byHand: true)
                    }
                    .onEnded { _ in
                        holding = false
                        setFinger(on: false, x: hand.x, y: hand.y, size: size, byHand: true)
                        touched()
                    }
            )
            .onAppear { chart = size }
            .onChange(of: size) { _, value in chart = value }
        }
        .accessibilityElement()
        .accessibilityLabel("\(heading), \(ranges[current].period)")
    }

    private var tooltip: some View {
        let columns = ranges[current].columns
        let day = hovered.map { columns[min($0, columns.count - 1)] }
        return ZStack {
            Rectangle().fill(.ultraThinMaterial)
            Color.white.opacity(0.4)
            if let day {
                VStack(alignment: .leading, spacing: 4) {
                    StackedBarsGlassTooltipRolling(value: day.title, size: 12, lineHeight: 16, weight: .semibold, color: S.ink, still: still)
                    ForEach(legend, id: \.label) { item in
                        HStack(spacing: 0) {
                            Circle().fill(item.color).frame(width: 6, height: 6).padding(.trailing, 6)
                            Text(item.label)
                                .font(.system(size: 12))
                                .foregroundStyle(S.soft)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            StackedBarsGlassTooltipRolling(
                                value: "\(item.grams(day)) g", size: 12, lineHeight: 16, weight: .semibold, color: S.ink,
                                alignment: .trailing, still: still
                            )
                        }
                        .frame(height: 16)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
            }
        }
        .frame(width: S.tipWidth, height: S.tipHeight)
        .clipShape(RoundedRectangle(cornerRadius: S.tipRadius, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: S.tipRadius, style: .continuous).strokeBorder(Color.white.opacity(0.8), lineWidth: 1))
        .shadow(color: S.shadow.opacity(0.16), radius: 12, x: 0, y: 8)
        .allowsHitTesting(false)
    }

    private func drawChart(_ c: GraphicsContext, _ size: CGSize, _ date: Date, _ motion: StackedBarsGlassTooltipMotion) {
        let since = date.timeIntervalSince(switchAt)
        let switched = fromRange != current
        let range = ranges[current]
        for k in 0...S.gridLines {
            let y = size.height * (1 - Double(k) / Double(S.gridLines))
            c.fill(Path(CGRect(x: S.axis, y: y, width: size.width, height: 1)), with: .color(k == 0 ? S.baseline : S.line))
        }
        // The axis of the new range fades in; the old one is gone at once.
        var axis = c
        axis.opacity = switched && !still ? S.quad(since / S.axisIn) : 1
        for k in 0...S.gridLines {
            let y = size.height * (1 - Double(k) / Double(S.gridLines))
            let text = Text(S.axisLabel(range.max * Double(k) / Double(S.gridLines))).font(.system(size: 10)).monospacedDigit().foregroundStyle(S.mute)
            axis.draw(text, at: CGPoint(x: 0, y: y), anchor: .leading)
        }

        if let from = fromBars, let to = toBars {
            let m = S.cubic(since / morphFor)
            var bars = c
            bars.translateBy(x: S.axis, y: 0)
            // Reduced motion swaps the columns halfway through a short fade instead of moving them.
            if still { bars.opacity = 1 - sin(.pi * m) }
            Macros.draw(
                bars, from: from, to: to, morph: still ? (m < 0.5 ? 0 : 1) : m, focusX: motion.focusX, focus: motion.focus,
                width: size.width, height: size.height, paints: paints
            )
        }

        // Dates: the old ones fade out, the new ones in once the columns have half moved.
        if switched && !still {
            var old = c
            old.opacity = 1 - S.quad(since / S.datesOut)
            if old.opacity > 0 { dates(old, ranges[fromRange], size) }
        }
        var fresh = c
        fresh.opacity = switched && !still ? S.quad((since - S.datesDelay) / S.datesIn) : 1
        if fresh.opacity > 0 { dates(fresh, range, size) }
    }

    private func dates(_ c: GraphicsContext, _ range: StackedBarsGlassTooltipRange, _ size: CGSize) {
        let pitch = Macros.columnLayout(range.columns.count, size.width, S.column, S.columnShare).pitch
        for (i, column) in range.columns.enumerated() where !column.label.isEmpty {
            let text = Text(column.label).font(.system(size: 10)).foregroundStyle(S.mute)
            c.draw(text, at: CGPoint(x: S.axis + (Double(i) + 0.5) * pitch, y: size.height + 6 + 6.5), anchor: .center)
        }
    }

    // MARK: Motion

    private func motion(_ date: Date, _ size: CGSize) -> StackedBarsGlassTooltipMotion {
        guard automatic else { return hand.motion(date) }
        return autoMotion(max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: S.loop), size)
    }

    private func autoColumn(_ x: Double, _ n: Int, _ width: Double) -> Int {
        min(n - 1, max(0, Int(floor(x / (width / Double(n))))))
    }

    /// The scripted finger at a time of the loop, through the same springs a touch drives. The
    /// springs are stepped frame by frame from the landing, as they chase the finger.
    private func autoMotion(_ t: Double, _ size: CGSize) -> StackedBarsGlassTooltipMotion {
        let n = ranges[restRange].columns.count
        let pitch = Macros.columnLayout(n, size.width, S.column, S.columnShare).pitch
        let fingerX = { (time: Double) in (S.scriptedColumn(time) + 0.5) * pitch }
        let center = { (time: Double) in (Double(autoColumn(fingerX(time), n, size.width)) + 0.5) * pitch }
        let landing = S.scrub[0].at
        let focus = t < S.liftAt ? S.quad((t - landing) / S.focusIn) : 1 - S.quad((t - S.liftAt) / S.focusOut)
        let y = size.height * S.fingerHeight
        if t < landing { return StackedBarsGlassTooltipMotion(focus: focus, focusX: center(landing), trailX: fingerX(t), trailY: y) }
        if still {
            return StackedBarsGlassTooltipMotion(focus: focus, focusX: center(min(t, S.liftAt - 0.001)), trailX: fingerX(t), trailY: y)
        }
        // A fresh touch starts at its column and under the finger.
        var focusX = (value: center(landing), velocity: 0.0)
        var trail = (value: fingerX(landing), velocity: 0.0)
        let end = min(t, S.liftAt + S.settle)
        var frame = 0
        while true {
            let at = landing + Double(frame) / 60
            if at >= end { break }
            let dt = min(1 / 60, end - at)
            let goal = center(min(at, S.liftAt - 0.001))
            let f = StackedBarsGlassTooltipSpring.step(offset: focusX.value - goal, velocity: focusX.velocity, dt: dt, S.glide)
            focusX = (goal + f.offset, f.velocity)
            let target = fingerX(at)
            let r = StackedBarsGlassTooltipSpring.step(offset: trail.value - target, velocity: trail.velocity, dt: dt, S.trail)
            trail = (target + r.offset, r.velocity)
            frame += 1
        }
        // Once lifted, the ring stays under the finger as it fades.
        return StackedBarsGlassTooltipMotion(focus: focus, focusX: focusX.value, trailX: t < S.liftAt ? trail.value : fingerX(t), trailY: y)
    }

    /// The hand takes the finger, the focus and the springs from where the loop has them.
    private func takeover(_ now: Date) {
        guard automatic else { return }
        let t = max(0, now.timeIntervalSince(origin)).truncatingRemainder(dividingBy: S.loop)
        let size = chart
        let m = autoMotion(t, size)
        let n = ranges[restRange].columns.count
        let pitch = Macros.columnLayout(n, size.width, S.column, S.columnShare).pitch
        let on = t >= S.scrub[0].at && t < S.liftAt
        var next = StackedBarsGlassTooltipHand()
        next.on = on
        next.x = (S.scriptedColumn(t) + 0.5) * pitch
        next.y = size.height * S.fingerHeight
        next.column = on ? autoColumn(next.x, n, size.width) : -1
        next.focus = StackedBarsGlassTooltipFade(from: m.focus, to: m.focus)
        next.focusX = StackedBarsGlassTooltipSpring(m.focusX)
        next.trailX = StackedBarsGlassTooltipSpring(m.trailX)
        next.trailY = StackedBarsGlassTooltipSpring(m.trailY)
        // A spring caught mid-glide keeps going where it was heading.
        if on && !still {
            let later = autoMotion(min(t + 1 / 60, S.liftAt - 0.001), size)
            next.focusX.velocity = (later.focusX - m.focusX) * 60
            next.focusX.target = (Double(next.column) + 0.5) * pitch
            next.focusX.start = now
            next.focusX.config = S.glide
            next.focus = StackedBarsGlassTooltipFade(from: m.focus, to: 1, start: now, duration: S.focusIn * (1 - m.focus))
        }
        hand = next
        automatic = false
        autoMoving = false
        keepMoving(until: now.addingTimeInterval(S.settle))
    }

    /// Where the finger is, and whether it is down: picks the column, moves the focus and the ring.
    private func setFinger(on: Bool, x: Double, y: Double, size: CGSize, byHand: Bool) {
        let now = Date.now
        let wasOn = hand.on
        let n = toBars?.kcal.count ?? ranges[current].columns.count
        let column = on ? min(n - 1, max(0, Int(floor(x / (size.width / Double(n)))))) : -1
        if column != hand.column {
            if column >= 0 {
                let center = (Double(column) + 0.5) * (size.width / Double(n))
                // A fresh touch starts at its column; moving on, the focus glides.
                hand.focusX.retarget(center, at: now, hand.column < 0 || still ? nil : S.glide)
            }
            hand.column = column
            hover(column, byHand: byHand)
        }
        if on != wasOn {
            hand.focus = StackedBarsGlassTooltipFade(
                from: hand.focus.value(at: now), to: on ? 1 : 0, start: now, duration: on ? S.focusIn : S.focusOut)
        }
        let follow = !wasOn || still || !on ? nil : S.trail
        hand.trailX.retarget(x, at: now, follow)
        hand.trailY.retarget(y, at: now, follow)
        hand.on = on
        hand.x = x
        hand.y = y
        keepMoving(until: now.addingTimeInterval(S.settle))
    }

    private func hover(_ index: Int, byHand: Bool) {
        withAnimation(still ? nil : .default) { hovered = index < 0 ? nil : index }
        if byHand && index >= 0 {
            haptic.selectionChanged()
            haptic.prepare()
        }
        onSelect?(index < 0 ? nil : index)
    }

    private func select(_ index: Int, byHand: Bool) {
        if index == current { return }
        let now = Date.now
        // The switch starts from the range drawn now.
        fromBars = toBars
        toBars = Macros.bars(ranges[index])
        fromRange = current
        range = index
        switchAt = now
        if byHand {
            haptic.selectionChanged()
            haptic.prepare()
        }
        if !automatic { keepMoving(until: now.addingTimeInterval(max(morphFor, S.datesDelay + S.datesIn))) }
        onRangeChange?(ranges[index])
    }

    private func pressRange(_ index: Int) {
        takeover(.now)
        setFinger(on: false, x: hand.x, y: hand.y, size: chart, byHand: true)
        touched()
        select(index, byHand: true)
    }

    private func keepMoving(until date: Date) {
        if date > busyUntil { busyUntil = date }
        handMoving = true
    }

    /// A touch takes over from autoPlay; the loop comes back `userHold` seconds after the last one.
    private func touched() {
        lastTouch = .now
        touches += 1
    }

    private func wait(_ date: Date) async throws {
        while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }
        try Task.checkCancellation()
    }

    private func autoplay() async {
        guard autoPlay else { return }
        do {
            if !started {
                // The first run starts the loop from rest at once.
                started = true
                origin = .now
                sampleAt = origin
                automatic = true
            } else if !automatic {
                guard !holding else { return }
                // Back to rest: the resting range first, then the loop from its start.
                var resume = lastTouch.addingTimeInterval(S.userHold)
                try await wait(resume)
                if current != restRange {
                    select(restRange, byHand: false)
                    resume = resume.addingTimeInterval(S.morph)
                    try await wait(resume)
                }
                hand = StackedBarsGlassTooltipHand()
                origin = resume
                sampleAt = resume
                automatic = true
            }
            let shift = max(morphFor, S.datesDelay + S.datesIn)
            var events: [(at: Double, run: () -> Void)] = S.columnSteps.map { step in (step.at, { hover(step.column ?? -1, byHand: false) }) }
            events += [
                (S.scrub[0].at, { autoMoving = true }),
                (S.liftAt + S.focusOut, { pause() }),
                (S.switchAt, { select(otherRange, byHand: false); autoMoving = true }),
                (S.switchAt + shift, { pause() }),
                (S.switchBackAt, { select(restRange, byHand: false); autoMoving = true }),
                (S.switchBackAt + shift, { pause() }),
            ]
            events.sort { $0.at < $1.at }
            var start = origin
            // Joining a loop already under way, the events before now are skipped.
            let joined = Date.now
            let elapsed = joined.timeIntervalSince(start)
            if elapsed > 0 { start = start.addingTimeInterval(floor(elapsed / S.loop) * S.loop) }
            while true {
                for event in events where start.addingTimeInterval(event.at) >= joined {
                    try await wait(start.addingTimeInterval(event.at))
                    event.run()
                }
                start = start.addingTimeInterval(S.loop)
                try await wait(start)
            }
        } catch {}
    }

    private func pause() {
        sampleAt = .now
        autoMoving = false
    }
}
