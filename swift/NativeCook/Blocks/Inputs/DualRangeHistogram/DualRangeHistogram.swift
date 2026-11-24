import SwiftUI
import UIKit

enum DualRangeHistogramPalette {
    static let page = Color(hex: 0xf6f1e8)
    static let ink = Color(hex: 0x2a2420)
    static let mute = Color(hex: 0x7c7267)
    static let muted = Color(hex: 0xddd6ca)
    static let track = Color(hex: 0xe4ddd1)
    static let fillTop = Color(hex: 0xe8703f)
    static let fillMid = Color(hex: 0xd9542f)
    static let fillLow = Color(hex: 0xb83f2a)
    static let thumb = Color(hex: 0xfffdf9)
    static let thumbEdge = Color(hex: 0xe6dfd3)
    static let tagInk = Color(hex: 0xfbf6ee)
    static let ghost = Color.rgba(42, 36, 32, 0.2)
}

/// The filter sheet of Nook, an invented rental search app, around the histogram. A price histogram sits on a
/// two-thumb range: bars inside take the colour, bars outside drain and sink, the labels ride the thumbs and roll their
/// digits, and the thumbs squash when held and refuse to cross with a rubber band. Drag a thumb or tap a bar to take
/// over; the film returns 2 s after the last touch.
struct DualRangeHistogram: View {
    var autoPlay = true

    static let loop = DualRangeHistogramMotion.loop

    private typealias P = DualRangeHistogramPalette
    private typealias D = DualRangeHistogramData

    @State private var placeType = 0
    @State private var bedrooms = 0
    @State private var amenities = [false, false, false]
    @State private var resetKey = 0
    @State private var count = DualRangeHistogramCount()

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    private var bins: [Double] {
        var share = 1.0
        for (i, on) in amenities.enumerated() where on { share *= D.amenities[i].share }
        return D.makeBins(type: placeType, bedrooms: bedrooms, share: share)
    }

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let height = proxy.size.height + proxy.safeAreaInsets.top + proxy.safeAreaInsets.bottom
            let compact = height < 740
            let contentW = min(width, 520) - 40
            let amenityRows = compact ? 2 : 3
            let reserved = compact ? 370.0 : 420.0
            let chartHeight = max(96, min(compact ? 150 : 196, height - proxy.safeAreaInsets.top - proxy.safeAreaInsets.bottom - reserved - Double(amenityRows) * 40)).rounded()

            VStack(spacing: 0) {
                HStack {
                    Text("Filters")
                        .font(.system(size: 28, weight: .bold))
                        .kerning(-0.4)
                        .foregroundStyle(P.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button(action: clear) {
                        Text("Clear all")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(P.mute)
                            .padding(12)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .padding(-12)
                }
                .frame(width: contentW, height: 44)

                VStack(alignment: .leading, spacing: compact ? 12 : 20) {
                    Picker("Place type", selection: $placeType.animation()) {
                        ForEach(D.placeTypes.indices, id: \.self) { i in Text(D.placeTypes[i]).tag(i) }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()

                    VStack(alignment: .leading, spacing: 0) {
                        Text("Price per month")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(P.ink)
                        Text("Most homes in Harbour District are $1,900 to $2,800")
                            .font(.system(size: 13))
                            .foregroundStyle(P.mute)
                            .padding(.top, 2)
                        Color.clear.frame(height: compact ? 8 : 16)
                        DualRangeHistogramRange(
                            bins: bins,
                            min: D.priceMin, max: D.priceMax, step: D.priceStep, minGap: D.priceGap,
                            defaultValue: D.priceDefault,
                            width: contentW, chartHeight: chartHeight,
                            autoPlay: autoPlay, count: count, resetKey: resetKey
                        )
                    }

                    VStack(spacing: 12) {
                        HStack(spacing: 12) {
                            Text("Bedrooms")
                                .font(.system(size: 15, weight: .medium))
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Picker("Bedrooms", selection: $bedrooms) {
                                ForEach(D.bedrooms.indices, id: \.self) { i in Text(D.bedrooms[i]).tag(i) }
                            }
                            .pickerStyle(.segmented)
                            .labelsHidden()
                            .frame(width: 200)
                        }
                        ForEach(0..<amenityRows, id: \.self) { i in
                            Toggle(isOn: amenityBinding(i)) {
                                Text(D.amenities[i].label)
                                    .font(.system(size: 15))
                                    .foregroundStyle(P.ink)
                            }
                            .tint(P.fillMid)
                        }
                    }
                }
                .frame(width: contentW, alignment: .top)
                .padding(.top, 12)
                .frame(maxHeight: .infinity, alignment: .top)

                DualRangeHistogramButton(count: count)
                    .frame(width: contentW)
            }
            .padding(.top, compact ? 4 : 10)
            .padding(.bottom, max(proxy.safeAreaInsets.bottom, 12) + 4 - proxy.safeAreaInsets.bottom)
            .frame(width: width)
        }
        .background(P.page.ignoresSafeArea())
        .environment(\.colorScheme, .light)
        .preferredColorScheme(.light)
    }

    private func amenityBinding(_ i: Int) -> Binding<Bool> {
        Binding(
            get: { amenities[i] },
            set: { on in
                UISelectionFeedbackGenerator().selectionChanged()
                amenities[i] = on
            }
        )
    }

    private func clear() {
        UISelectionFeedbackGenerator().selectionChanged()
        placeType = 0
        bedrooms = 0
        amenities = [false, false, false]
        resetKey += 1
    }
}

/// "Show 724 homes": the count rolls on odometer columns.
struct DualRangeHistogramButton: View {
    let count: DualRangeHistogramCount
    @State private var awake = true
    @State private var pressed = false

    private typealias P = DualRangeHistogramPalette

    var body: some View {
        let font = Font.system(size: 17, weight: .semibold).monospacedDigit()
        TimelineView(.animation(paused: !awake)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let v = count.value(at: now)
            let _ = sleepIfDone(now)
            HStack(spacing: 0) {
                Text("Show ").font(font)
                DualRangeHistogramDigit(value: v, place: 100, lowest: false, hideLeading: true, width: 10.5, height: 22, font: font)
                DualRangeHistogramDigit(value: v, place: 10, lowest: false, hideLeading: true, width: 10.5, height: 22, font: font)
                DualRangeHistogramDigit(value: v, place: 1, lowest: true, hideLeading: false, width: 10.5, height: 22, font: font)
                Text(" homes").font(font)
            }
            .foregroundStyle(P.tagInk)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 52)
        .background(
            Capsule()
                .fill(P.ink)
                .shadow(color: Color.rgba(42, 36, 32, 0.3), radius: 1, y: 1)
                .shadow(color: Color.rgba(42, 36, 32, 0.18), radius: 12, y: 10)
        )
        .scaleEffect(pressed ? 0.98 : 1)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in if !pressed { pressed = true } }
                .onEnded { _ in pressed = false }
        )
        .onChange(of: count.to) { _, _ in awake = true }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Show \(Int(count.to)) homes")
        .accessibilityAddTraits(.isButton)
    }

    private func sleepIfDone(_ now: Double) -> Bool {
        if awake && !count.busy(at: now) {
            DispatchQueue.main.async { if !count.busy(at: Date().timeIntervalSinceReferenceDate) { awake = false } }
        }
        return true
    }
}

/// One odometer column: eleven rows, 0 … 9 and 0 again.
struct DualRangeHistogramDigit: View {
    let value: Double
    let place: Double
    let lowest: Bool
    let hideLeading: Bool
    let width: Double
    let height: Double
    let font: Font

    var body: some View {
        let v = value
        let opacity = hideLeading && v < place ? max(0, (v - place * 0.9) / (place * 0.1)) : 1
        VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)").font(font).frame(width: width, height: height)
            }
        }
        .offset(y: -DualRangeHistogramMotion.digitAt(v, place, lowest) * height)
        .frame(width: width, height: height, alignment: .top)
        .clipped()
        .opacity(opacity)
        .accessibilityHidden(true)
    }
}

/// A price histogram over a two-thumb range. Bars inside the range take the colour, bars outside drain and sink, the
/// labels ride the thumbs and roll their digits, and the thumbs squash when held and refuse to cross with a rubber band.
struct DualRangeHistogramRange: View {
    /// Listings per price bin, evenly spaced from `min` to `max`. A new array morphs the bars.
    let bins: [Double]
    let min: Double
    let max: Double
    let step: Double
    let minGap: Double
    let defaultValue: (Double, Double)
    let width: Double
    let chartHeight: Double
    var autoPlay = true
    /// Receives the number of listings in the range, rolled to whole numbers.
    var count: DualRangeHistogramCount?
    /// Change it to send the thumbs back to `defaultValue`.
    var resetKey = 0
    var onChange: ((Double, Double) -> Void)?

    private typealias M = DualRangeHistogramMotion
    private typealias P = DualRangeHistogramPalette

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine: DualRangeHistogramEngine
    @State private var awake = true
    @State private var touching = false
    @State private var settled: (Double, Double)

    init(bins: [Double], min: Double, max: Double, step: Double, minGap: Double, defaultValue: (Double, Double),
         width: Double, chartHeight: Double, autoPlay: Bool = true, count: DualRangeHistogramCount? = nil,
         resetKey: Int = 0, onChange: ((Double, Double) -> Void)? = nil) {
        self.bins = bins
        self.min = min
        self.max = max
        self.step = step
        self.minGap = minGap
        self.defaultValue = defaultValue
        self.width = width
        self.chartHeight = chartHeight
        self.autoPlay = autoPlay
        self.count = count
        self.resetKey = resetKey
        self.onChange = onChange
        _engine = State(initialValue: DualRangeHistogramEngine(bins: bins, min: min, max: max, step: step, minGap: minGap, defaultValue: defaultValue))
        _settled = State(initialValue: defaultValue)
    }

    private var inset: Double { M.thumb / 2 }
    private var span: Double { width - inset * 2 }
    private var range: Double { max - min }

    var body: some View {
        let totalH = chartHeight + M.trackArea + M.tagH + 4
        TimelineView(.animation(paused: !awake)) { timeline in
            let f = advance(timeline.date)
            ZStack(alignment: .topLeading) {
                chart(f)
                    .frame(width: width, height: chartHeight + M.trackArea)
                thumb(u: f.low, press: f.pressLow)
                thumb(u: f.high, press: f.pressHigh)
                Circle()
                    .fill(P.ghost)
                    .frame(width: 46, height: 46)
                    .scaleEffect(M.mix(1.12, 0.94, M.clamp(f.ghostSide == 0 ? f.pressLow : f.pressHigh, 0, 1)))
                    .offset(x: inset + (f.ghostSide == 0 ? f.low : f.high) * span - 23, y: chartHeight + (M.trackArea - 46) / 2)
                    .opacity(M.clamp(f.ghost, 0, 1) * 0.9)
                    .allowsHitTesting(false)
                let tags = tagX(f)
                tag(price: min + M.clamp(f.low, 0, 1) * range)
                    .offset(x: tags.0, y: chartHeight + M.trackArea + 2)
                tag(price: min + M.clamp(f.high, 0, 1) * range)
                    .offset(x: tags.1, y: chartHeight + M.trackArea + 2)
            }
            .frame(width: width, height: totalH, alignment: .topLeading)
        }
        .frame(width: width, height: totalH)
        .contentShape(Rectangle())
        .gesture(drag)
        .accessibilityElement(children: .contain)
        .overlay(alignment: .topLeading) { accessibilityThumbs }
        .onChange(of: bins) { _, next in
            engine.setBins(next, at: Date().timeIntervalSinceReferenceDate)
            awake = true
        }
        .onChange(of: resetKey) { _, _ in
            engine.reset()
            awake = true
        }
        .onChange(of: reduceMotion) { _, _ in awake = true }
    }

    private func advance(_ date: Date) -> DualRangeHistogramFrame {
        let e = engine
        if e.onTick == nil { wire() }
        e.span = span
        e.inset = inset
        e.playing = autoPlay && !reduceMotion
        let f = e.frame(at: date.timeIntervalSinceReferenceDate)
        if e.asleep && awake && !touching { DispatchQueue.main.async { if e.asleep { awake = false } } }
        return f
    }

    private func wire() {
        engine.counter = count
        engine.onTick = { UISelectionFeedbackGenerator().selectionChanged() }
        engine.onPress = { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        let lo = min, r = range, cb = onChange
        engine.onSettle = { a, b in
            settled = (lo + a * r, lo + b * r)
            cb?(lo + a * r, lo + b * r)
        }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { v in
                let now = Date().timeIntervalSinceReferenceDate
                if !touching {
                    touching = true
                    awake = true
                    engine.touchBegan(x: v.startLocation.x, at: now)
                }
                engine.touchMoved(x: v.location.x, at: now)
            }
            .onEnded { v in
                engine.touchEnded(x: v.location.x, velocityX: v.velocity.width, binCount: bins.count, at: Date().timeIntervalSinceReferenceDate)
                touching = false
                awake = true
            }
    }

    // MARK: - Drawing

    private func chart(_ f: DualRangeHistogramFrame) -> some View {
        Canvas { ctx, _ in
            let bars = barsPath(f)
            ctx.fill(bars, with: .color(P.muted))
            let lowX = inset + f.low * span
            let highX = inset + f.high * span
            var clipped = ctx
            clipped.clip(to: Path(CGRect(x: lowX, y: 0, width: Swift.max(0, highX - lowX), height: chartHeight)))
            clipped.fill(bars, with: .linearGradient(
                Gradient(stops: [.init(color: P.fillTop, location: 0), .init(color: P.fillMid, location: 0.55), .init(color: P.fillLow, location: 1)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: chartHeight)
            ))
            let trackY = chartHeight + M.trackArea / 2 - M.trackH / 2
            ctx.fill(Path(roundedRect: CGRect(x: inset, y: trackY, width: span, height: M.trackH), cornerRadius: M.trackH / 2), with: .color(P.track))
            let fillW = Swift.max(0, (f.high - f.low) * span)
            if fillW > 0 {
                ctx.fill(Path(roundedRect: CGRect(x: lowX, y: trackY, width: fillW, height: M.trackH), cornerRadius: Swift.min(M.trackH / 2, fillW / 2)), with: .color(P.fillMid))
            }
        }
    }

    private func barsPath(_ f: DualRangeHistogramFrame) -> Path {
        let values = f.bins
        let n = values.count
        guard n > 0 else { return Path() }
        let slot = span / Double(n)
        let barW = Swift.max(1, slot - M.barGap)
        let lowX = inset + f.low * span
        let highX = inset + f.high * span
        let peak = Swift.max(1, values.max() ?? 1)
        let room = chartHeight - M.liftPt - 2
        let r = Swift.min(M.barRadius, barW / 2)
        var path = Path()
        for i in 0..<n {
            let cx = inset + (Double(i) + 0.5) * slot
            let inside = M.smooth(M.seg(cx, lowX - slot, lowX + slot)) * (1 - M.smooth(M.seg(cx, highX - slot, highX + slot)))
            let dl = (cx - lowX) / M.liftSigma
            let dh = (cx - highX) / M.liftSigma
            let lift = M.liftPt * (f.pressLow * exp(-dl * dl) + f.pressHigh * exp(-dh * dh))
            let h = Swift.max(3, room * (values[i] / peak) * M.mix(M.sink, 1, inside) + lift)
            path.addRoundedRect(in: CGRect(x: cx - barW / 2, y: chartHeight - h, width: barW, height: h), cornerSize: CGSize(width: r, height: r), style: .circular)
        }
        return path
    }

    private func thumb(u: Double, press: Double) -> some View {
        let p = M.clamp(press, 0, 1)
        return Circle()
            .fill(P.thumb)
            .overlay(Circle().strokeBorder(P.thumbEdge, lineWidth: 1 / 3))
            .overlay(Circle().fill(P.fillMid).frame(width: 8, height: 8))
            .frame(width: M.thumb, height: M.thumb)
            .shadow(color: Color.rgba(60, 40, 25, 0.28), radius: 1.5, y: 1)
            .shadow(color: Color.rgba(60, 40, 25, 0.16), radius: 8, y: 6)
            .scaleEffect(x: M.mix(1, M.squashX, p), y: M.mix(1, M.squashY, p))
            .offset(x: inset + u * span - M.thumb / 2, y: chartHeight + (M.trackArea - M.thumb) / 2)
            .accessibilityHidden(true)
    }

    /// The labels ride their thumbs and move apart instead of overlapping.
    private func tagX(_ f: DualRangeHistogramFrame) -> (Double, Double) {
        let l = inset + f.low * span - M.tagW / 2
        let r = inset + f.high * span - M.tagW / 2
        let close = r - l < M.tagW + M.tagGap
        let left = M.clamp(close ? (l + r) / 2 - (M.tagW + M.tagGap) / 2 : l, 0, width - M.tagW * 2 - M.tagGap)
        let right = M.clamp(close ? (l + r) / 2 + (M.tagW + M.tagGap) / 2 : r, left + M.tagW + M.tagGap, width - M.tagW)
        return (left, right)
    }

    private func tag(price: Double) -> some View {
        let font = Font.system(size: 13.5, weight: .semibold).monospacedDigit()
        let h = 18.0, w = 8.6
        return HStack(spacing: 0) {
            Text("$").font(font)
            DualRangeHistogramDigit(value: price, place: 1000, lowest: false, hideLeading: false, width: w, height: h, font: font)
            Text(",").font(font)
            DualRangeHistogramDigit(value: price, place: 100, lowest: false, hideLeading: false, width: w, height: h, font: font)
            DualRangeHistogramDigit(value: price, place: 10, lowest: true, hideLeading: false, width: w, height: h, font: font)
            Text("0").font(font)
        }
        .foregroundStyle(P.tagInk)
        .frame(width: M.tagW, height: M.tagH)
        .background(Capsule().fill(P.ink))
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: - Accessibility

    private var accessibilityThumbs: some View {
        HStack(spacing: 0) {
            Color.clear
                .accessibilityElement()
                .accessibilityLabel("Minimum price")
                .accessibilityValue(price(settled.0))
                .accessibilityAdjustableAction { dir in adjust(0, dir) }
            Color.clear
                .accessibilityElement()
                .accessibilityLabel("Maximum price")
                .accessibilityValue(price(settled.1))
                .accessibilityAdjustableAction { dir in adjust(1, dir) }
        }
        .frame(width: width, height: chartHeight + M.trackArea + M.tagH)
        .allowsHitTesting(false)
    }

    private func price(_ v: Double) -> String {
        "$" + Int(v.rounded()).formatted(.number.locale(Locale(identifier: "en_US")))
    }

    private func adjust(_ side: Int, _ dir: AccessibilityAdjustmentDirection) {
        engine.nudge(side, dir == .increment ? 1 : -1)
        awake = true
    }
}
