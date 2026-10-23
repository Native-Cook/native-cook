import SwiftUI

/// A category of the gauge: the segment's colour, and the icon's glyph and circle.
struct SemicircleSpendGaugeCategory {
    var name: String
    var color: Color
    var ink: Color
    var tint: Color
    var icon: SemicircleSpendGaugeIcon
}

struct SemicircleSpendGaugePeriod {
    /// The dropdown's label: "Last Week".
    var label: String
    /// The word in the footer: "weekly".
    var span: String
    /// The whole half circle stands for the limit.
    var limit: Double
    /// One amount per category, in the same order.
    var amounts: [Double]
}

private enum S {
    static let categories = [
        SemicircleSpendGaugeCategory(name: "Shopping", color: Color(hex: 0x375efb), ink: Color(hex: 0x2748c8), tint: Color(hex: 0xe8eeff), icon: .shopping),
        SemicircleSpendGaugeCategory(name: "Utilities", color: Color(hex: 0x2cbdf2), ink: Color(hex: 0x1b93b8), tint: Color(hex: 0xe1f6fd), icon: .receipt),
        SemicircleSpendGaugeCategory(name: "Others", color: Color(hex: 0x9aa4b2), ink: Color(hex: 0x5f6368), tint: Color(hex: 0xf3f4f5), icon: .coins),
    ]
    static let periods = [
        SemicircleSpendGaugePeriod(label: "Last Week", span: "weekly", limit: 2000, amounts: [900, 600, 300]),
        SemicircleSpendGaugePeriod(label: "Last Month", span: "monthly", limit: 8000, amounts: [2880, 2150, 1390]),
    ]

    static let page = Color(hex: 0xdfe0e2)
    static let card = Color(hex: 0xffffff)
    static let ink = Color(hex: 0x111111)
    static let mute = Color(hex: 0x5f6368)
    static let caption = Color(hex: 0x6b6b6b)
    static let line = Color(hex: 0xe8e8e8)
    static let border = Color(hex: 0xe3e3e3)
    static let track = Color(hex: 0xe6e7e9)
    static let pressed = Color(hex: 0xf2f3f5)
    static let info = Color(hex: 0x8e9196)

    static let margin = 16.0
    static let maxCard = 420.0
    static let radius = 28.0
    static let pad = 18.0
    // The gauge: a half ring that pops a segment out radially, with a hairline gap between segments.
    static let gauge = 260.0
    static let thickness = 27.0
    static let gap = 2.0
    static let gaugeTop = 22.0
    static let pop = 8.0
    // Taps this far outside the ring still pick a segment.
    static let tapSlop = 14.0
    static let dimmed = 0.35
    static let icon = 40.0
    static let iconPop = 1.1
    static let gaugeWidth = gauge + pop * 2
    static let gaugeHeight = gauge / 2 + pop
}

private enum M {
    static let popSpring = SemicircleSpendGaugeSpringParams(damping: 13, stiffness: 240, mass: 0.8)
    static let flowSpring = SemicircleSpendGaugeSpringParams(damping: 17, stiffness: 120, mass: 1)
    static let dimFor = 0.22
    static let roll = 0.6
    static let menuFor = 0.2
    static let menuFrom = 0.9
    static let reducedFade = 0.16

    // The autoPlay timeline, in seconds from the start of each loop: each segment pops in turn, the
    // selection clears, then the dropdown switches to the next period and back.
    static let loop = 10.0
    static let popAt = [0.8, 1.9, 3.0]
    static let clearAt = 4.1
    static let openAt = [4.8, 7.4]
    static let pickAt = [5.5, 8.1]
    // The menu row lights this long before the autoPlay finger picks it.
    static let pressFor = 0.18
    static let userHold = 3.0
    // How long the card takes to come back to rest after a touch, before the loop restarts.
    static let settle = 1.2
}

private struct SemicircleSpendGaugeSpringParams {
    var damping: Double
    var stiffness: Double
    var mass: Double
}

private enum SemicircleSpendGaugeCurve {
    case outCubic, inOutQuad

    func callAsFunction(_ t: Double) -> Double {
        switch self {
        case .outCubic: 1 - pow(1 - t, 3)
        case .inOutQuad: t < 0.5 ? 2 * t * t : 1 - pow(2 - 2 * t, 2) / 2
        }
    }
}

/// A number moving on a timing curve or a spring, sampled against the `TimelineView` clock. A
/// spring that is retargeted keeps its velocity, as Reanimated's does.
private struct SemicircleSpendGaugeValue {
    private var from: Double
    private(set) var to: Double
    private var start = Date.distantPast
    private var duration = 0.0
    private var curve = SemicircleSpendGaugeCurve.outCubic
    private var spring: SemicircleSpendGaugeSpringParams?
    private var velocity = 0.0

    init(_ value: Double) {
        from = value
        to = value
    }

    func sample(_ date: Date) -> (value: Double, velocity: Double) {
        let t = max(0, date.timeIntervalSince(start))
        if let spring {
            // Every spring here is under-damped (damping ratio below 1).
            let z = spring.damping / (2 * spring.mass)
            let w = sqrt(spring.stiffness / spring.mass - z * z)
            let a = from - to
            let b = (velocity + z * a) / w
            let decay = exp(-z * t)
            let offset = decay * (a * cos(w * t) + b * sin(w * t))
            let speed = decay * (velocity * cos(w * t) - (z * b + w * a) * sin(w * t))
            return (to + offset, speed)
        }
        guard duration > 0 else { return (to, 0) }
        return (from + (to - from) * curve(min(t / duration, 1)), 0)
    }

    func value(_ date: Date) -> Double { sample(date).value }

    /// When the motion is over: a spring once it is within a thousandth of its travel.
    var end: Date {
        guard let spring else { return start.addingTimeInterval(duration) }
        let z = spring.damping / (2 * spring.mass)
        let w = sqrt(spring.stiffness / spring.mass - z * z)
        let a = from - to
        let amplitude = hypot(a, (velocity + z * a) / w)
        guard amplitude > 1e-4 else { return start }
        return start.addingTimeInterval(log(amplitude / 1e-4) / z)
    }

    mutating func time(_ target: Double, _ date: Date, _ duration: Double, _ curve: SemicircleSpendGaugeCurve) {
        from = value(date)
        to = target
        start = date
        self.duration = duration
        self.curve = curve
        spring = nil
        velocity = 0
    }

    mutating func spring(_ target: Double, _ date: Date, _ params: SemicircleSpendGaugeSpringParams) {
        let now = sample(date)
        from = now.value
        velocity = now.velocity
        to = target
        start = date
        spring = params
    }

    mutating func jump(_ target: Double) { self = SemicircleSpendGaugeValue(target) }
}

/// A spending summary card around a half-donut gauge: the half circle is the period's limit and
/// each category takes its share of it. Tapping a segment pops it out and dims the rest while the
/// centre rolls to its total; the dropdown switches the period, and the segments re-flow on a
/// spring as every amount rolls to its new value.
struct SemicircleSpendGauge: View {
    var title = "Spending Summary"
    /// Up to three categories, drawn left to right.
    var categories = S.categories
    var periods = S.periods
    /// The period shown at rest.
    var initialPeriod = 0
    var symbol = "$"
    var spentLabel = "SPENT"
    /// Pops each segment in turn and switches the period and back, every `loop` seconds.
    var autoPlay = false
    /// Called with the category's place, or -1 when the selection is cleared.
    var onSelect: ((Int) -> Void)? = nil
    var onPeriodChange: ((Int, SemicircleSpendGaugePeriod) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var period: Int?
    @State private var selected = -1
    @State private var menuOpen = false
    @State private var autoPressed = -1
    @State private var ends: [SemicircleSpendGaugeValue]?
    @State private var pops = Array(repeating: SemicircleSpendGaugeValue(0), count: 3)
    @State private var dims = Array(repeating: SemicircleSpendGaugeValue(0), count: 3)
    @State private var centre: SemicircleSpendGaugeValue?
    @State private var columns: [SemicircleSpendGaugeValue]?
    @State private var menu = SemicircleSpendGaugeValue(0)
    /// The timeline runs only while something moves; at rest every value is read at its end.
    @State private var moving = false
    @State private var restAt = Date.distantPast
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private var rest: Int { min(max(0, initialPeriod), periods.count - 1) }
    private var current: Int { period ?? rest }
    private var count: Int { min(3, categories.count) }

    private func fractions(_ index: Int) -> [Double] {
        let p = periods[index]
        var sum = 0.0
        return (0..<count).map { i in
            sum = min(1, sum + (i < p.amounts.count ? p.amounts[i] : 0) / p.limit)
            return sum
        }
    }

    private func total(_ index: Int) -> Double { periods[index].amounts.prefix(count).reduce(0, +) }

    var body: some View {
        GeometryReader { proxy in
            let cardWidth = min(S.maxCard, proxy.size.width - S.margin * 2)
            TimelineView(.animation(paused: !moving)) { timeline in
                card(cardWidth, moving ? timeline.date : .distantFuture)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .background(S.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { haptic.prepare() }
        .task(id: restAt) {
            guard (try? await wait(until: restAt)) != nil else { return }
            moving = false
        }
        .task(id: touches) { await play() }
        .onChange(of: autoPlay) { touches += 1 }
    }

    // MARK: Card

    private func card(_ width: Double, _ date: Date) -> some View {
        let p = periods[current]
        let ends = (self.ends ?? fractions(current).map(SemicircleSpendGaugeValue.init)).map { $0.value(date) }
        let centreValue = centre?.value(date) ?? total(current)
        let columnValues = (0..<count).map { columns?[$0].value(date) ?? p.amounts[$0] }
        let menuValue = menu.value(date)
        return VStack(spacing: 0) {
            header(p)
            divider
            gauge(width, ends, date, centreValue)
            divider
            HStack(spacing: 0) {
                ForEach(0..<count, id: \.self) { i in
                    column(i, columnValues[i], date)
                }
            }
            .padding(.vertical, 18)
            footer(p)
        }
        .padding(.horizontal, S.pad)
        .padding(.bottom, S.pad)
        .frame(width: width)
        .background(
            RoundedRectangle(cornerRadius: S.radius, style: .continuous)
                .fill(S.card)
                .shadow(color: .black.opacity(0.06), radius: 20, x: 0, y: 16)
        )
        .overlay {
            if menuOpen {
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture {
                        touched()
                        setMenu(false)
                    }
                    .accessibilityLabel("Close menu")
                    .accessibilityAddTraits(.isButton)
            }
        }
        .overlay(alignment: .topTrailing) {
            menuView(menuValue)
                .padding(.top, 62)
                .padding(.trailing, S.pad)
        }
    }

    private var divider: some View { S.line.frame(height: 1) }

    private func header(_ p: SemicircleSpendGaugePeriod) -> some View {
        HStack(spacing: 0) {
            HStack(spacing: 10) {
                SemicircleSpendGaugeIconView(icon: .pie, size: 22, color: S.mute)
                Text(title)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(S.ink)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                touched()
                setMenu(!menuOpen)
            } label: {
                HStack(spacing: 6) {
                    Text(p.label)
                        .font(.system(size: 15))
                        .foregroundStyle(S.ink)
                    SemicircleSpendGaugeIconView(icon: .chevron, size: 18, color: S.ink)
                }
                .padding(.leading, 14)
                .padding(.trailing, 10)
                .frame(height: 38)
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(S.border, lineWidth: 1))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Period: \(p.label)")
        }
        .frame(height: 72)
    }

    private func gauge(_ width: Double, _ ends: [Double], _ date: Date, _ centreValue: Double) -> some View {
        let label = selected < 0 ? spentLabel : categories[selected].name.uppercased()
        let pops = self.pops.map { reduced ? 0 : $0.value(date) }
        let dims = self.dims.map { $0.value(date) }
        return ZStack(alignment: .bottom) {
            Canvas { context, _ in
                let cx = S.gaugeWidth / 2
                let cy = S.gaugeHeight
                // The grey rest of the half ring: the part of the limit not spent.
                if let path = SemicircleSpendGauge.segment(cx, cy, ends[count - 1], 1, 0, true, false) {
                    context.fill(path, with: .color(S.track))
                }
                for i in 0..<count {
                    let start = i == 0 ? 0 : ends[i - 1]
                    guard let path = SemicircleSpendGauge.segment(cx, cy, start, ends[i], pops[i], i > 0, i < count - 1 || ends[i] < 0.999) else { continue }
                    var layer = context
                    layer.opacity = 1 - (1 - S.dimmed) * dims[i]
                    layer.fill(path, with: .color(categories[i].color))
                }
            }
            VStack(spacing: 6) {
                Text(label)
                    .font(.system(size: 14))
                    .tracking(1.6)
                    .foregroundStyle(S.caption)
                    .lineLimit(1)
                    .frame(height: 18)
                Text(cents(centreValue))
                    .font(.system(size: 32, weight: .semibold).monospacedDigit())
                    .tracking(-0.8)
                    .foregroundStyle(S.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .frame(height: 38)
            }
            .padding(.horizontal, S.pop + S.thickness + 8)
            .offset(y: 2)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(label) \(cents(centreValue))")
        }
        .frame(width: S.gaugeWidth, height: S.gaugeHeight)
        .padding(.top, S.gaugeTop)
        .padding(.bottom, 18)
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
        .onTapGesture { location in gaugeTap(location, areaWidth: width - S.pad * 2) }
    }

    private func column(_ i: Int, _ amount: Double, _ date: Date) -> some View {
        let category = categories[i]
        let pop = reduced ? 0 : pops[i].value(date)
        let dim = dims[i].value(date)
        return Button {
            touched()
            select(i, byHand: true)
        } label: {
            VStack(spacing: 0) {
                SemicircleSpendGaugeIconView(icon: category.icon, size: 20, color: category.ink)
                    .frame(width: S.icon, height: S.icon)
                    .background(category.tint, in: Circle())
                    .scaleEffect(1 + (S.iconPop - 1) * pop)
                    .padding(.bottom, 14)
                Text(category.name)
                    .font(.system(size: 15))
                    .foregroundStyle(S.mute)
                    .lineLimit(1)
                    .frame(height: 20)
                Text(cents(amount))
                    .font(.system(size: 17, weight: .semibold).monospacedDigit())
                    .foregroundStyle(S.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .frame(height: 22)
                    .padding(.top, 4)
            }
            .padding(.horizontal, 6)
            .opacity(1 - (1 - S.dimmed) * dim)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .overlay(alignment: .leading) {
            if i > 0 { S.line.frame(width: 1) }
        }
        .accessibilityLabel("\(category.name), \(cents(amount))")
    }

    private func footer(_ p: SemicircleSpendGaugePeriod) -> some View {
        HStack(spacing: 8) {
            (Text("Your \(p.span) spending limit is ")
                + Text("\(symbol)\(Int(p.limit)).").fontWeight(.semibold).foregroundStyle(S.ink))
                .font(.system(size: 14))
                .foregroundStyle(S.mute)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            SemicircleSpendGaugeIconView(icon: .info, size: 16, color: S.info)
        }
        .padding(.horizontal, 14)
        .frame(height: 44)
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(S.border, lineWidth: 1))
    }

    private func menuView(_ value: Double) -> some View {
        VStack(spacing: 0) {
            ForEach(Array(periods.enumerated()), id: \.offset) { i, p in
                Button {
                    touched()
                    pick(i, byHand: true)
                } label: {
                    HStack {
                        Text(p.label)
                            .font(.system(size: 15))
                            .foregroundStyle(S.ink)
                        Spacer(minLength: 0)
                        if i == current {
                            SemicircleSpendGaugeIconView(icon: .check, size: 18, color: categories.first?.color ?? S.ink)
                        }
                    }
                    .padding(.horizontal, 10)
                    .frame(height: 40)
                    .contentShape(Rectangle())
                }
                .buttonStyle(SemicircleSpendGaugeRowStyle(lit: autoPressed == i))
                .padding(.horizontal, 6)
                .accessibilityAddTraits(i == current ? [.isSelected] : [])
            }
        }
        .padding(.vertical, 6)
        .frame(width: 168)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(S.card)
                .shadow(color: .black.opacity(0.12), radius: 14, x: 0, y: 10)
        )
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(S.border, lineWidth: 1))
        .scaleEffect(reduced ? 1 : M.menuFrom + (1 - M.menuFrom) * value, anchor: .topTrailing)
        .opacity(value)
        .allowsHitTesting(menuOpen)
        .accessibilityHidden(!menuOpen)
    }

    // MARK: Geometry

    /// One segment of the half ring between two fractions of it, pushed out along its middle.
    private static func segment(_ cx: Double, _ cy: Double, _ start: Double, _ end: Double, _ pop: Double, _ gapStart: Bool, _ gapEnd: Bool) -> Path? {
        let outer = S.gauge / 2
        let inner = outer - S.thickness
        // Half the gap, as an angle at the ring's middle.
        let half = S.gap / 2 / (outer - S.thickness / 2) * 180 / .pi
        let a0 = 180 + start * 180 + (gapStart ? half : 0)
        let a1 = 180 + end * 180 - (gapEnd ? half : 0)
        let sweep = a1 - a0
        guard sweep > 0 else { return nil }
        let middle = (a0 + a1) / 2 * .pi / 180
        let center = CGPoint(x: cx + cos(middle) * S.pop * pop, y: cy + sin(middle) * S.pop * pop)
        var path = Path()
        path.addRelativeArc(center: center, radius: outer, startAngle: .degrees(a0), delta: .degrees(sweep))
        path.addRelativeArc(center: center, radius: inner, startAngle: .degrees(a0 + sweep), delta: .degrees(-sweep))
        path.closeSubpath()
        return path
    }

    private func cents(_ value: Double) -> String {
        let hundredths = Int((value * 100).rounded())
        let whole = (Double(hundredths) / 100).rounded(.down)
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 0
        let wholeText = formatter.string(from: NSNumber(value: whole)) ?? "\(Int(whole))"
        return "\(symbol)\(wholeText).\(String(format: "%02d", hundredths % 100))"
    }

    // MARK: Behaviour

    private func tick() {
        haptic.impactOccurred()
        haptic.prepare()
    }

    /// Keeps the timeline running until `date`.
    private func run(until date: Date) {
        moving = true
        restAt = max(restAt, date)
    }

    private func gaugeTap(_ location: CGPoint, areaWidth: Double) {
        touched()
        // The touch is measured in the padded area the gauge is centred in.
        let dx = location.x - areaWidth / 2
        let dy = location.y - (S.gaugeTop + S.gaugeHeight)
        let distance = hypot(dx, dy)
        if distance < S.gauge / 2 - S.thickness - S.tapSlop || distance > S.gauge / 2 + S.tapSlop || dy > S.tapSlop {
            if selected >= 0 { select(selected, byHand: true) }
            return
        }
        // The fraction along the half ring, 0 at the left end.
        let along = min(1, max(0, 1 - atan2(-dy, dx) / .pi))
        if let index = fractions(current).firstIndex(where: { along <= $0 }) {
            select(index, byHand: true)
        } else if selected >= 0 {
            select(selected, byHand: true)
        }
    }

    private func select(_ index: Int, byHand: Bool, at date: Date = .now) {
        let next = index == selected ? -1 : index
        selected = next
        if byHand { tick() }
        onSelect?(next)
        selectionChanged(date)
    }

    private func pick(_ index: Int, byHand: Bool, at date: Date = .now) {
        setMenu(false, at: date)
        autoPressed = -1
        guard index != current else { return }
        selected = -1
        setPeriod(index, at: date)
        if byHand { tick() }
        onPeriodChange?(index, periods[index])
    }

    private func setMenu(_ open: Bool, at date: Date = .now) {
        guard open != menuOpen else { return }
        menuOpen = open
        let duration = reduced ? M.reducedFade : M.menuFor
        menu.time(open ? 1 : 0, date, duration, .outCubic)
        run(until: date.addingTimeInterval(duration))
    }

    /// The segments re-flow on a spring and every amount rolls, as the period's effect does.
    private func setPeriod(_ index: Int, at date: Date) {
        let fromEnds = ends ?? fractions(current).map(SemicircleSpendGaugeValue.init)
        let fromColumns = columns ?? (0..<count).map { SemicircleSpendGaugeValue(periods[current].amounts[$0]) }
        period = index
        let next = fractions(index)
        ends = fromEnds.enumerated().map { i, value in
            var value = value
            if reduced { value.jump(next[i]) } else { value.spring(next[i], date, M.flowSpring) }
            return value
        }
        columns = fromColumns.enumerated().map { i, value in
            var value = value
            if reduced { value.jump(periods[index].amounts[i]) } else { value.time(periods[index].amounts[i], date, M.roll, .outCubic) }
            return value
        }
        selectionChanged(date)
        if let ends { run(until: ends.map(\.end).max() ?? date) }
        run(until: date.addingTimeInterval(M.roll))
    }

    /// The centre rolls to the selection's amount, the selected segment pops and the rest dim.
    private func selectionChanged(_ date: Date) {
        let amounts = periods[current].amounts
        var centre = self.centre ?? SemicircleSpendGaugeValue(total(current))
        let target = selected < 0 ? total(current) : amounts[selected]
        if reduced { centre.jump(target) } else { centre.time(target, date, M.roll, .outCubic) }
        self.centre = centre
        let fade = reduced ? M.reducedFade : M.dimFor
        for i in 0..<count {
            if !reduced { pops[i].spring(i == selected ? 1 : 0, date, M.popSpring) }
            dims[i].time(selected >= 0 && i != selected ? 1 : 0, date, fade, .inOutQuad)
        }
        let popsEnd = pops.prefix(count).map(\.end).max() ?? date
        run(until: max(popsEnd, date.addingTimeInterval(max(fade, reduced ? 0 : M.roll))))
    }

    private func touched() { touches += 1 }

    private func wait(until date: Date) async throws {
        while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }
        try Task.checkCancellation()
    }

    private func play() async {
        guard autoPlay else { return }
        do {
            if touches > 0 {
                try await wait(until: .now.addingTimeInterval(M.userHold))
                // Back to rest, then the loop starts once everything has settled.
                let now = Date.now
                setMenu(false, at: now)
                autoPressed = -1
                selected = -1
                if current != rest { setPeriod(rest, at: now) } else { selectionChanged(now) }
                try await wait(until: now.addingTimeInterval(M.settle))
            }
            let next = (rest + 1) % periods.count
            var events: [(at: Double, run: (Date) -> Void)] = M.popAt.enumerated().map { i, at in
                (at, { date in if selected != i { select(i, byHand: false, at: date) } })
            }
            events.append((M.clearAt, { date in if selected >= 0 { select(selected, byHand: false, at: date) } }))
            for (i, open) in M.openAt.enumerated() {
                let target = i == 0 ? next : rest
                events.append((open, { date in setMenu(true, at: date) }))
                events.append((M.pickAt[i] - M.pressFor, { _ in autoPressed = target }))
                events.append((M.pickAt[i], { date in pick(target, byHand: false, at: date) }))
            }
            var origin = Date.now
            while true {
                // Each step at its own time from the start of the loop, so every loop is the same.
                for event in events {
                    let at = origin.addingTimeInterval(event.at)
                    try await wait(until: at)
                    event.run(at)
                }
                origin = origin.addingTimeInterval(M.loop)
            }
        } catch {}
    }
}

private struct SemicircleSpendGaugeRowStyle: ButtonStyle {
    var lit: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed || lit ? S.pressed : .clear, in: RoundedRectangle(cornerRadius: 9))
    }
}
