import SwiftUI

private enum M {
    /// autoPlay: 1D rests, then every range in turn, a hold on the last, and a slower morph home.
    static let loop = 9.0
    static let firstStepAt = 0.9
    static let lastStepAt = 6.15
    static let homeAt = 7.5
    /// After a touch autoPlay waits this long, then morphs home and plays again.
    static let userHold = 3.0

    // One morph: every point moves in y while x stays put; going home is a little slower.
    static let morph = 0.45
    static let homeMorph = 0.6
    static let slide = 0.2
    static let colorFor = 0.3
    // The old change fades out before the new one fades in, so the two never read over each other.
    static let deltaOut = 0.12
    static let deltaIn = 0.18
    static let swipe = 40.0
}

private enum S {
    static let background = Color(hex: 0x000000)
    static let ink: UInt32 = 0xffffff
    static let mute: UInt32 = 0x8e8e93
    static let red: UInt32 = 0xe0102b
    static let green: UInt32 = 0x5be02b
    static let redText = Color(hex: 0xff2d3d)
    // The area under the line: the line's hue, dark, deepening to black at the base.
    static let fillRed: [UInt32] = [0x3d0008, 0x1a0004]
    static let fillGreen: [UInt32] = [0x1f3d0a, 0x0c1804]
    static let badge = Color(hex: 0x4cd91f)
    static let button = Color(hex: 0x3fd62b)
    static let selector = Color(hex: 0x1c1c1e)
    static let selected = Color(hex: 0x3a3a3c)

    static let margin = 24.0
    static let selectorSide = 26.0
    static let selectorH = 30.0
    static let selectorInset = 2.0
    static let separatorH = 14.0
    static let line = 2.0
    // The line keeps to this band of the chart's height (from the top); the area runs to its base.
    static let bandTop = 0.16
    static let bandBottom = 0.8
    static let buttonH = 56.0
    static let badgeSize = 40.0
}

/// `Easing.inOut` of a power curve, as Reanimated's `Easing.inOut(Easing.cubic)` (3) or its default (2).
private func rangeMorphPriceLineInOut(_ t: Double, _ power: Double) -> Double {
    t < 0.5 ? pow(2 * t, power) / 2 : 1 - pow(2 - 2 * t, power) / 2
}

/// A straight RGB mix, as `interpolateColor(…, 'RGB', { gamma: 1 })`.
private func rangeMorphPriceLineMix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
    func channel(_ c: UInt32, _ shift: UInt32) -> Double { Double((c >> shift) & 0xff) }
    func mix(_ shift: UInt32) -> Double { channel(a, shift) + (channel(b, shift) - channel(a, shift)) * t }
    return .rgba(mix(16), mix(8), mix(0), 1)
}

/// A number timed against the `TimelineView` clock, so the canvas can sample it at any date.
private struct RangeMorphPriceLineTween {
    var from: Double
    var to: Double
    var start = Date.distantPast
    var duration = 0.0
    var power = 2.0

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        guard duration > 0 else { return to }
        let t = min(max(date.timeIntervalSince(start) / duration, 0), 1)
        return from + (to - from) * rangeMorphPriceLineInOut(t, power)
    }

    mutating func retarget(_ target: Double, at date: Date, duration: Double, power: Double) {
        from = value(at: date)
        to = target
        start = date
        self.duration = duration
        self.power = power
    }
}

/// Everything that moves: the line morphs from `shown` to `target` by `progress`.
private struct RangeMorphPriceLineMotion {
    var shown: [Double]
    var target: [Double]
    var progress = RangeMorphPriceLineTween(1)
    var slot = RangeMorphPriceLineTween(0)
    var up: RangeMorphPriceLineTween

    func line(at date: Date) -> [Double] {
        let p = progress.value(at: date)
        return zip(shown, target).map { a, b in a + (b - a) * p }
    }
}

/// An exchange-rate screen: a jagged price line over a dark area, and a range selector under it.
/// Choosing a range morphs the line into the next series point by point while the area follows,
/// the highlight slides, and the line turns red or green with the sign of the change.
struct RangeMorphPriceLine: View {
    var title: String
    /// The rate now, as shown under the title.
    var price: String
    /// The ranges of the selector, each with its own series; every one is drawn with the same point count.
    var ranges: [RangeMorphPriceLineRange]
    /// The two buttons at the bottom.
    var from: String
    var to: String
    /// Steps through the ranges by itself, every `loop` seconds.
    var autoPlay: Bool
    var onRangeChange: ((RangeMorphPriceLineRange, Int) -> Void)?
    var onCurrency: ((String) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var still
    @Environment(\.displayScale) private var displayScale
    @State private var motion: RangeMorphPriceLineMotion
    @State private var range = 0
    /// The timeline runs only while something moves; at rest every tween is read at its end.
    @State private var moving = false
    @State private var restAt = Date.distantPast
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var dragging = false
    @State private var haptic = UISelectionFeedbackGenerator()

    init(
        title: String = "USD per 1 EUR",
        price: String = "$1.0835",
        ranges: [RangeMorphPriceLineRange] = RangeMorphPriceLineSeries.ranges,
        from: String = "EUR",
        to: String = "USD",
        autoPlay: Bool = false,
        onRangeChange: ((RangeMorphPriceLineRange, Int) -> Void)? = nil,
        onCurrency: ((String) -> Void)? = nil
    ) {
        self.title = title
        self.price = price
        self.ranges = ranges
        self.from = from
        self.to = to
        self.autoPlay = autoPlay
        self.onRangeChange = onRangeChange
        self.onCurrency = onCurrency
        let first = RangeMorphPriceLineSeries.normalized(ranges[0].series)
        _motion = State(initialValue: RangeMorphPriceLineMotion(
            shown: first, target: first, up: RangeMorphPriceLineTween(ranges[0].change >= 0 ? 1 : 0)
        ))
    }

    /// autoPlay's steps: when, which range, and how long its morph takes.
    private var steps: [(at: Double, index: Int, time: Double)] {
        let gap = ranges.count > 2 ? (M.lastStepAt - M.firstStepAt) / Double(ranges.count - 2) : 0
        var out = [(at: 0.0, index: 0, time: M.homeMorph)]
        for i in 1..<max(1, ranges.count) { out.append((M.firstStepAt + Double(i - 1) * gap, i, M.morph)) }
        out.append((M.homeAt, 0, M.homeMorph))
        return out
    }

    var body: some View {
        GeometryReader { proxy in
            VStack(alignment: .leading, spacing: 0) {
                header
                    .padding(.top, 20)
                    .padding(.horizontal, S.margin)

                TimelineView(.animation(paused: !moving)) { timeline in
                    let date = moving ? timeline.date : .distantFuture
                    VStack(spacing: 0) {
                        chart(date)
                            .padding(.top, 24)
                        selector(date)
                            .frame(height: S.selectorH)
                            .padding(.top, 28)
                            .padding(.horizontal, S.selectorSide)
                    }
                }

                buttons
                    .padding(.top, 28)
                    .padding(.horizontal, S.selectorSide)
                    .padding(.bottom, max(proxy.safeAreaInsets.bottom, 16) + 8 - proxy.safeAreaInsets.bottom)
            }
        }
        .background(S.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: restAt) {
            guard (try? await wait(until: restAt)) != nil else { return }
            moving = false
        }
        .task(id: touches) { await play() }
        .onChange(of: autoPlay) { touches += 1 }
    }

    // MARK: Parts

    private var header: some View {
        let current = ranges[range]
        let rose = current.change >= 0
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 0) {
                Text(title)
                    .font(.system(size: 28, weight: .bold))
                    .tracking(-0.3)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, minHeight: 34, alignment: .leading)
                ZStack {
                    Circle().fill(S.badge)
                    RangeMorphPriceLineSineWave()
                        .fill(.white)
                        .frame(width: 24, height: 24)
                }
                .frame(width: S.badgeSize, height: S.badgeSize)
                .accessibilityHidden(true)
            }
            HStack(spacing: 16) {
                Text(price)
                    .font(.system(size: 20).monospacedDigit())
                    .foregroundStyle(Color(hex: S.mute))
                    .frame(height: 25)
                ZStack(alignment: .topLeading) {
                    Text("\(rose ? "↑" : "↓") \(String(format: "%.2f", abs(current.change)))%")
                        .font(.system(size: 20, weight: .medium).monospacedDigit())
                        .foregroundStyle(rose ? Color(hex: S.green) : S.redText)
                        .fixedSize()
                        .frame(height: 25)
                        .id(current.label)
                        .transition(.asymmetric(
                            insertion: .opacity.animation(still ? nil : .easeInOut(duration: M.deltaIn).delay(M.deltaOut)),
                            removal: .opacity.animation(still ? nil : .easeInOut(duration: M.deltaOut))
                        ))
                }
                .frame(minWidth: 100, minHeight: 25, maxHeight: 25, alignment: .topLeading)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(rose ? "Up" : "Down") \(abs(current.change).formatted()) percent over \(current.label)")
            }
        }
    }

    private func chart(_ date: Date) -> some View {
        Canvas { context, size in
            let values = motion.line(at: date)
            let up = motion.up.value(at: date)
            let width = size.width
            let height = size.height
            let dx = width / Double(values.count - 1)
            let top = height * S.bandTop
            let span = height * (S.bandBottom - S.bandTop)
            var line = Path()
            for (i, v) in values.enumerated() {
                let point = CGPoint(x: Double(i) * dx, y: top + (1 - v) * span)
                if i == 0 { line.move(to: point) } else { line.addLine(to: point) }
            }
            var area = line
            area.addLine(to: CGPoint(x: width, y: height))
            area.addLine(to: CGPoint(x: 0, y: height))
            area.closeSubpath()
            let fill = Gradient(colors: [
                rangeMorphPriceLineMix(S.fillRed[0], S.fillGreen[0], up),
                rangeMorphPriceLineMix(S.fillRed[1], S.fillGreen[1], up),
            ])
            context.fill(area, with: .linearGradient(fill, startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: height)))
            context.stroke(
                line,
                with: .color(rangeMorphPriceLineMix(S.red, S.green, up)),
                style: StrokeStyle(lineWidth: S.line, lineCap: .round, lineJoin: .round)
            )
        }
        .contentShape(Rectangle())
        .gesture(swipe)
        .accessibilityElement()
        .accessibilityLabel("\(title), \(ranges[range].label)")
    }

    private func selector(_ date: Date) -> some View {
        GeometryReader { proxy in
            let slot = motion.slot.value(at: date)
            let segment = (proxy.size.width - S.selectorInset * 2) / Double(ranges.count)
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(S.selected)
                    .frame(width: segment, height: S.selectorH - S.selectorInset * 2)
                    .offset(x: S.selectorInset + slot * segment, y: S.selectorInset)
                // Hidden next to the highlight, as in the system's segmented control.
                ForEach(0..<max(0, ranges.count - 1), id: \.self) { i in
                    Rectangle()
                        .fill(S.selected)
                        .frame(width: 2 / displayScale, height: S.separatorH)
                        .opacity(min(1, abs(slot - Double(i)), abs(slot - Double(i) - 1)))
                        .offset(x: S.selectorInset + Double(i + 1) * segment, y: (S.selectorH - S.separatorH) / 2)
                }
                HStack(spacing: 0) {
                    ForEach(Array(ranges.enumerated()), id: \.offset) { index, r in
                        Button { tap(index) } label: {
                            Text(r.label)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(rangeMorphPriceLineMix(S.mute, S.ink, max(0, 1 - abs(slot - Double(index)))))
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(r.label)
                        .accessibilityAddTraits(index == range ? [.isSelected] : [])
                    }
                }
                .padding(.horizontal, S.selectorInset)
                .frame(width: proxy.size.width, height: S.selectorH)
            }
            .frame(width: proxy.size.width, height: S.selectorH, alignment: .topLeading)
            .background(S.selector, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
    }

    private var buttons: some View {
        HStack(spacing: 20) {
            ForEach([from, to], id: \.self) { code in
                Button {
                    touched()
                    onCurrency?(code)
                } label: {
                    HStack(spacing: 10) {
                        RangeMorphPriceLineCurrencyDisc(kind: code == "USD" ? .usd : .eur)
                        Text(code)
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: S.buttonH)
                    .background(S.button, in: Capsule())
                }
                .buttonStyle(RangeMorphPriceLinePressStyle())
            }
        }
    }

    // MARK: Behaviour

    private var swipe: some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { value in
                // A drag that starts mostly vertical is not a swipe between ranges.
                guard !dragging, abs(value.translation.width) >= abs(value.translation.height) else { return }
                dragging = true
                touched()
            }
            .onEnded { value in
                guard dragging else { return }
                dragging = false
                // Swiping left goes on to a longer range, as the selector reads left to right.
                let dx = value.translation.width
                let step = dx < -M.swipe ? 1 : dx > M.swipe ? -1 : 0
                let next = min(ranges.count - 1, max(0, range + step))
                if next != range {
                    select(next, duration: M.morph, at: .now)
                    selectionHaptic()
                }
                touched()
            }
    }

    private func tap(_ index: Int) {
        touched()
        selectionHaptic()
        select(index, duration: M.morph, at: .now)
    }

    private func selectionHaptic() {
        haptic.selectionChanged()
        haptic.prepare()
    }

    /// A new range starts from wherever the line is, so a morph can be interrupted by another.
    private func select(_ index: Int, duration: Double, at date: Date) {
        motion.shown = motion.line(at: date)
        motion.target = RangeMorphPriceLineSeries.normalized(ranges[index].series)
        motion.progress = RangeMorphPriceLineTween(0)
        motion.progress.retarget(1, at: date, duration: still ? 0 : duration, power: 3)
        motion.slot.retarget(Double(index), at: date, duration: still ? 0 : M.slide, power: 3)
        motion.up.retarget(ranges[index].change >= 0 ? 1 : 0, at: date, duration: still ? 0 : M.colorFor, power: 2)
        withAnimation(still ? nil : .default) { range = index }
        onRangeChange?(ranges[index], index)
        if !still {
            moving = true
            restAt = max(restAt, date.addingTimeInterval(max(duration, M.slide, M.colorFor)))
        }
    }

    /// A touch takes over at once; some seconds after the last one the loop starts again, and its
    /// first step morphs the line home to the first range.
    private func touched() {
        lastTouch = .now
        touches += 1
    }

    private func wait(until date: Date) async throws {
        while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }
        try Task.checkCancellation()
    }

    private func play() async {
        guard autoPlay else { return }
        do {
            if touches > 0 {
                try await wait(until: lastTouch.addingTimeInterval(M.userHold))
                guard !dragging else { return }
            }
            var origin = Date.now
            while true {
                // Each step at its own time from the start of the loop, so every loop is the same.
                for step in steps {
                    let at = origin.addingTimeInterval(step.at)
                    try await wait(until: at)
                    if step.index != range { select(step.index, duration: step.time, at: at) }
                }
                origin = origin.addingTimeInterval(M.loop)
            }
        } catch {}
    }
}

private struct RangeMorphPriceLinePressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// Material Design's "sine-wave" glyph, on its 24-point grid.
private struct RangeMorphPriceLineSineWave: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 16.5, y: 21))
        p.addCurve(to: CGPoint(x: 11.05, y: 12.28), control1: CGPoint(x: 13.5, y: 21), control2: CGPoint(x: 12.31, y: 16.76))
        p.addCurve(to: CGPoint(x: 7.5, y: 5), control1: CGPoint(x: 10.14, y: 9.04), control2: CGPoint(x: 9, y: 5))
        p.addCurve(to: CGPoint(x: 4, y: 12), control1: CGPoint(x: 4.11, y: 5), control2: CGPoint(x: 4, y: 11.93))
        p.addLine(to: CGPoint(x: 2, y: 12))
        p.addCurve(to: CGPoint(x: 7.5, y: 3), control1: CGPoint(x: 2, y: 11.63), control2: CGPoint(x: 2.06, y: 3))
        p.addCurve(to: CGPoint(x: 12.97, y: 11.74), control1: CGPoint(x: 10.5, y: 3), control2: CGPoint(x: 11.71, y: 7.25))
        p.addCurve(to: CGPoint(x: 16.5, y: 19), control1: CGPoint(x: 13.83, y: 14.8), control2: CGPoint(x: 15, y: 19))
        p.addCurve(to: CGPoint(x: 20.07, y: 12), control1: CGPoint(x: 19.94, y: 19), control2: CGPoint(x: 20.07, y: 12.07))
        p.addLine(to: CGPoint(x: 22.07, y: 12))
        p.addCurve(to: CGPoint(x: 16.5, y: 21), control1: CGPoint(x: 22.07, y: 12.37), control2: CGPoint(x: 22, y: 21))
        p.closeSubpath()
        return p.applying(CGAffineTransform(scaleX: rect.width / 24, y: rect.height / 24).translatedBy(x: rect.minX, y: rect.minY))
    }
}
