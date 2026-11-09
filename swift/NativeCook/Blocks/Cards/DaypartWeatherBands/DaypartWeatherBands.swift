import SwiftUI
import UIKit

/// Four overlapping weather bands, driven by the same day under autoplay and the finger.
@MainActor
struct DaypartWeatherBands: View {
    var autoPlay = false
    static let loop: TimeInterval = 8

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @State private var iconOrigin = Date.now
    @State private var loopOrigin = Date.now
    @State private var playing = true
    @State private var weights = DaypartWeatherBandsWeights.morning
    @State private var alpha = 1.0
    @State private var presentation = DaypartWeatherBandsPresentation()
    @State private var touch: DaypartWeatherBandsTouch?
    @State private var generation = 0
    @State private var fade: DaypartWeatherBandsFade?
    @State private var fadeTask: Task<Void, Never>?
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)
    @GestureState private var fingerDown = false

    var body: some View {
        GeometryReader { safeArea in
            GeometryReader { viewport in
                let width = viewport.size.width
                let height = viewport.size.height
                let headerBottom = safeArea.safeAreaInsets.top + 64
                let fullHeight = height - headerBottom
                let openHeight = fullHeight - safeArea.safeAreaInsets.bottom - 3 * 72
                let span = max(1, openHeight - 72)
                TimelineView(.animation(paused: (reduceMotion && fade == nil) || scenePhase != .active)) { timeline in
                    let sample = sample(at: timeline.date)
                    let clock = reduceMotion ? 0.45 : DaypartWeatherBandsMotion.wrap(timeline.date.timeIntervalSince(iconOrigin))
                    ZStack(alignment: .topLeading) {
                        DaypartWeatherBandsPalette.background
                        DaypartWeatherBandsStack(
                            weights: sample.weights, alpha: sample.alpha, clock: clock,
                            width: width, fullHeight: fullHeight, openHeight: openHeight,
                            displayScale: displayScale, presentation: presentation,
                            onSelect: accessibilitySelect
                        )
                        .contentShape(Rectangle())
                        .gesture(gesture(span: span, openHeight: openHeight))
                        .offset(y: headerBottom)
                        header(width: width, top: safeArea.safeAreaInsets.top)
                            .allowsHitTesting(false)
                        LinearGradient(
                            colors: [DaypartWeatherBandsPalette.color(0x28221A).opacity(0.16),
                                     DaypartWeatherBandsPalette.color(0x28221A).opacity(0)],
                            startPoint: .top, endPoint: .bottom
                        )
                        .frame(width: width, height: 10)
                        .offset(y: headerBottom)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                    }
                    .frame(width: width, height: height, alignment: .topLeading)
                    .clipped()
                }
            }
            .ignoresSafeArea()
        }
        .background(DaypartWeatherBandsPalette.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .statusBarHidden(false)
        .onAppear { restart(); haptic.prepare() }
        .onDisappear { generation += 1; fadeTask?.cancel() }
        .onChange(of: autoPlay) { restart() }
        .onChange(of: reduceMotion) { restart() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { restart() }
        }
        .onChange(of: fingerDown) { _, down in
            if !down { cancelTouch() }
        }
        .task(id: generation) { await resumeAfterIdle(generation) }
    }

    // MARK: Clocks and presentation

    private func sample(at date: Date) -> DaypartWeatherBandsSample {
        if let fade {
            let elapsed = max(0, date.timeIntervalSince(fade.start))
            let fadingOut = elapsed < 0.11
            let opacity = fadingOut
                ? fade.fromAlpha * (1 - DaypartWeatherBandsMotion.inOutQuad(DaypartWeatherBandsMotion.seg(elapsed, 0, 0.11)))
                : DaypartWeatherBandsMotion.inOutQuad(DaypartWeatherBandsMotion.seg(elapsed, 0.11, 0.3))
            return DaypartWeatherBandsSample(weights: fadingOut ? fade.fromWeights : .open(fade.target), alpha: opacity)
        }
        if playing && autoPlay && !reduceMotion {
            return DaypartWeatherBandsSample(
                weights: DaypartWeatherBandsMotion.autoWeights(DaypartWeatherBandsMotion.wrap(date.timeIntervalSince(loopOrigin))),
                alpha: 1
            )
        }
        return DaypartWeatherBandsSample(weights: weights, alpha: alpha)
    }

    private func visibleSample(at date: Date) -> DaypartWeatherBandsSample {
        playing || fade != nil ? sample(at: date) : presentation.sample
    }

    private func freeze(at date: Date) {
        // State contains a spring's destination. The non-observed cache contains its visible frame.
        let visible = visibleSample(at: date)
        var transaction = Transaction(animation: nil)
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            playing = false
            weights = visible.weights
            alpha = visible.alpha
            presentation.sample = visible
        }
    }

    private func restart() {
        generation += 1
        fadeTask?.cancel()
        fadeTask = nil
        var transaction = Transaction(animation: nil)
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            touch = nil
            fade = nil
            weights = .morning
            alpha = 1
            presentation.sample = DaypartWeatherBandsSample(weights: .morning, alpha: 1)
            let date = Date.now
            iconOrigin = date
            loopOrigin = date
            playing = true
        }
    }

    private func resumeAfterIdle(_ token: Int) async {
        guard !playing, touch == nil, autoPlay, !reduceMotion else { return }
        do { try await Task.sleep(for: .seconds(2)) } catch { return }
        guard generation == token, !playing, touch == nil, autoPlay, !reduceMotion,
              scenePhase == .active else { return }
        let band = presentation.sample.weights.dominant
        var transaction = Transaction(animation: nil)
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            weights = .open(band)
            alpha = 1
            loopOrigin = Date.now.addingTimeInterval(-2 * Double(band))
            playing = true
        }
    }

    private func springTo(_ target: Int) {
        withAnimation(.spring(response: 0.45, dampingFraction: 0.78)) {
            weights = .open(target)
        }
    }

    private func fadeTo(_ target: Int) {
        fadeTask?.cancel()
        let visible = visibleSample(at: .now)
        var transaction = Transaction(animation: nil)
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            fade = DaypartWeatherBandsFade(fromWeights: visible.weights, fromAlpha: visible.alpha, target: target, start: .now)
        }
        fadeTask = Task { @MainActor in
            do { try await Task.sleep(for: .seconds(0.3)) } catch { return }
            guard !Task.isCancelled else { return }
            withTransaction(transaction) {
                weights = .open(target)
                alpha = 1
                fade = nil
                presentation.sample = DaypartWeatherBandsSample(weights: weights, alpha: 1)
            }
        }
    }

    private func select(_ target: Int) {
        if target != visibleSample(at: .now).weights.dominant { impact() }
        if reduceMotion { fadeTo(target) } else { springTo(target) }
    }

    private func accessibilitySelect(_ index: Int) {
        generation += 1
        freeze(at: .now)
        select(index)
        generation += 1
    }

    private func impact() {
        haptic.impactOccurred()
        haptic.prepare()
    }

    // MARK: One gesture for both taps and drags

    private func gesture(span: Double, openHeight: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($fingerDown) { _, down, _ in down = true }
            .onChanged { value in
                if touch == nil {
                    generation += 1
                    freeze(at: value.time)
                    touch = DaypartWeatherBandsTouch(y: value.startLocation.y, at: value.time)
                }
                guard var tracking = touch else { return }
                let y = value.location.y
                let dy = y - tracking.startY
                let dt = value.time.timeIntervalSince(tracking.lastAt)
                if dt > 0.001 {
                    tracking.velocity = 0.7 * ((y - tracking.lastY) / dt) + 0.3 * tracking.velocity
                    tracking.lastAt = value.time
                }
                tracking.lastY = y
                if !tracking.dragging {
                    guard abs(dy) >= 10 else { touch = tracking; return }
                    tracking.dragging = true
                    let ws = visibleSample(at: value.time).weights
                    let adjacent = ws[0] < 0.01 || ws[3] < 0.01
                    tracking.startFocus = adjacent ? ws[1] + 2 * ws[2] + 3 * ws[3] : Double(ws.dominant)
                    tracking.startBand = ws.dominant
                }
                touch = tracking
                guard !reduceMotion else { return }
                let next = DaypartWeatherBandsMotion.tentWeights(
                    DaypartWeatherBandsMotion.rubber(tracking.startFocus - dy / span)
                )
                var transaction = Transaction(animation: nil)
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    weights = next
                    presentation.sample = DaypartWeatherBandsSample(weights: next, alpha: alpha)
                }
            }
            .onEnded { value in
                guard let tracking = touch else { return }
                let dy = value.location.y - tracking.startY
                if tracking.dragging {
                    if reduceMotion {
                        let step = dy < 0 ? 1 : -1
                        select(min(3, max(0, tracking.startBand + step)))
                    } else {
                        let focus = tracking.startFocus - dy / span
                        // floor(x + .5) matches JavaScript Math.round, including negative ties.
                        let projected = focus - 0.18 * tracking.velocity / span
                        let target = Int(min(3, max(0, floor(projected + 0.5))))
                        if target != tracking.startBand { impact() }
                        springTo(target)
                    }
                } else if value.time.timeIntervalSince(tracking.startAt) < 0.5 {
                    let tops = visibleSample(at: value.time).weights.tops(openHeight: openHeight)
                    let band = (1...3).reversed().first { value.location.y >= tops[$0] } ?? 0
                    select(band)
                }
                touch = nil
                generation += 1
            }
    }

    private func cancelTouch() {
        guard let tracking = touch else { return }
        if tracking.dragging && !reduceMotion { springTo(presentation.sample.weights.dominant) }
        touch = nil
        generation += 1
    }

    // MARK: Header

    private func header(width: Double, top: Double) -> some View {
        ZStack(alignment: .topLeading) {
            DaypartWeatherBandsPalette.gradient([0x94B19B, 0x8BA892, 0x83A08A], middle: 0.5)
            Canvas { context, _ in
                context.fill(DaypartWeatherBandsArtwork.pin,
                             with: .color(DaypartWeatherBandsPalette.headerInk), style: FillStyle(eoFill: true))
            }
            .frame(width: 14, height: 18)
            .offset(x: 24, y: top + 15)
            DaypartWeatherBandsText(text: "Larkhaven", size: 17, weight: .semibold,
                                    ink: DaypartWeatherBandsPalette.headerInk, lineHeight: 22)
                .offset(x: 46, y: top + 11)
            DaypartWeatherBandsText(text: "Tuesday, 10 November", size: 12, weight: .medium,
                                    ink: DaypartWeatherBandsPalette.headerInk, lineHeight: 16)
                .opacity(0.78)
                .offset(x: 46, y: top + 34)
            Canvas { context, _ in
                let ink = DaypartWeatherBandsPalette.headerInk
                for i in 0..<4 {
                    let rect = CGRect(x: Double(i % 2) * 10, y: 0.5 + Double(i / 2) * 10, width: 7, height: 7)
                    context.fill(Path(roundedRect: rect, cornerRadius: 1.5), with: .color(ink.opacity(0.92)))
                }
                for i in 0..<3 {
                    let y = 1.5 + Double(i) * 6
                    context.fill(Path(ellipseIn: CGRect(x: 33, y: y, width: 3, height: 3)), with: .color(ink))
                    context.fill(Path(roundedRect: CGRect(x: 39, y: y + 0.25, width: 12, height: 2.5), cornerRadius: 1.25),
                                 with: .color(ink.opacity(0.92)))
                }
            }
            .frame(width: 51, height: 18)
            .offset(x: width - 75, y: top + 23)
        }
        .frame(width: width, height: top + 64, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Larkhaven, Tuesday, 10 November")
    }
}

// MARK: Animatable presentation (no observable state is written on animation frames)

private struct DaypartWeatherBandsSample {
    var weights: DaypartWeatherBandsWeights
    var alpha: Double
}

private struct DaypartWeatherBandsFade {
    var fromWeights: DaypartWeatherBandsWeights
    var fromAlpha: Double
    var target: Int
    var start: Date
}

private final class DaypartWeatherBandsPresentation {
    var sample = DaypartWeatherBandsSample(weights: .morning, alpha: 1)
}

private struct DaypartWeatherBandsWeights: VectorArithmetic {
    var morning = 0.0
    var day = 0.0
    var evening = 0.0
    var night = 0.0
    static let zero = Self()
    static let morning = Self(morning: 1)

    subscript(_ index: Int) -> Double {
        get {
            switch index { case 0: morning; case 1: day; case 2: evening; default: night }
        }
        set {
            switch index { case 0: morning = newValue; case 1: day = newValue; case 2: evening = newValue; default: night = newValue }
        }
    }

    static func open(_ index: Int) -> Self {
        var result = Self.zero
        result[index] = 1
        return result
    }

    var dominant: Int {
        var best = 0
        for i in 1..<4 where self[i] > self[best] { best = i }
        return best
    }

    func tops(openHeight: Double) -> [Double] {
        var y = 0.0
        return (0..<4).map { i in
            let top = y
            y += 72 + (openHeight - 72) * self[i]
            return top
        }
    }

    static func + (lhs: Self, rhs: Self) -> Self {
        Self(morning: lhs.morning + rhs.morning, day: lhs.day + rhs.day,
             evening: lhs.evening + rhs.evening, night: lhs.night + rhs.night)
    }
    static func - (lhs: Self, rhs: Self) -> Self {
        Self(morning: lhs.morning - rhs.morning, day: lhs.day - rhs.day,
             evening: lhs.evening - rhs.evening, night: lhs.night - rhs.night)
    }
    mutating func scale(by rhs: Double) {
        morning *= rhs; day *= rhs; evening *= rhs; night *= rhs
    }
    var magnitudeSquared: Double { morning * morning + day * day + evening * evening + night * night }
}

private struct DaypartWeatherBandsTouch {
    var startY: Double
    var startAt: Date
    var lastY: Double
    var lastAt: Date
    var velocity = 0.0
    var dragging = false
    var startFocus = 0.0
    var startBand = 0

    init(y: Double, at date: Date) {
        startY = y; lastY = y; startAt = date; lastAt = date
    }
}

private struct DaypartWeatherBandsStack: View, Animatable {
    var weights: DaypartWeatherBandsWeights
    var alpha: Double
    var clock: Double
    var width: Double
    var fullHeight: Double
    var openHeight: Double
    var displayScale: Double
    var presentation: DaypartWeatherBandsPresentation
    var onSelect: (Int) -> Void

    var animatableData: AnimatablePair<DaypartWeatherBandsWeights, Double> {
        get { AnimatablePair(weights, alpha) }
        set {
            weights = newValue.first
            alpha = newValue.second
            presentation.sample = DaypartWeatherBandsSample(weights: weights, alpha: alpha)
        }
    }

    var body: some View {
        let tops = weights.tops(openHeight: openHeight)
        ZStack(alignment: .topLeading) {
            ForEach(0..<4, id: \.self) { index in
                DaypartWeatherBandsBand(
                    band: DaypartWeatherBandsContent.bands[index], index: index,
                    openness: weights[index], alpha: alpha, clock: clock,
                    width: width, fullHeight: fullHeight, openHeight: openHeight, displayScale: displayScale
                )
                .accessibilityHidden(true)
                .offset(y: tops[index])
                .zIndex(Double(index))
            }
            // Expose each visible band, rather than four overlapping full-height buttons.
            ForEach(0..<4, id: \.self) { index in
                let bottom = index < 3 ? tops[index + 1] : fullHeight
                Color.clear
                    .frame(width: width, height: max(0, bottom - tops[index]))
                    .contentShape(Rectangle())
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(DaypartWeatherBandsContent.bands[index].spoken)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { onSelect(index) }
                    .offset(y: tops[index])
                    .zIndex(4)
            }
        }
        .frame(width: width, height: fullHeight, alignment: .topLeading)
        // The stack interpolates its weights; children draw those exact intermediate values.
        .transaction { $0.animation = nil }
    }
}

// MARK: Product content

private struct DaypartWeatherBandsBand: View {
    let band: DaypartWeatherBandsContent.Band
    let index: Int
    let openness: Double
    let alpha: Double
    let clock: Double
    let width: Double
    let fullHeight: Double
    let openHeight: Double
    let displayScale: Double

    var body: some View {
        let hairTop = max(72 + 200, openHeight - 100)
        let detailTop = 72 + max(4, floor((hairTop - 72 - 160) / 2 + 0.5))
        let columnWidth = (width - 48) / 3
        let ink = DaypartWeatherBandsPalette.color(band.ink)
        ZStack(alignment: .topLeading) {
            DaypartWeatherBandsPalette.color(band.colors[2])
            if index > 0 {
                LinearGradient(
                    colors: [DaypartWeatherBandsPalette.color(0x2E140E).opacity(0),
                             DaypartWeatherBandsPalette.color(0x2E140E).opacity(0.16)],
                    startPoint: .top, endPoint: .bottom
                )
                .frame(width: width, height: 14)
                .offset(y: -14)
            }
            DaypartWeatherBandsPalette.gradient(band.colors, middle: 0.45)
                .frame(width: width, height: min(openHeight, fullHeight))
            if index > 0 {
                Color.white.opacity(0.14).frame(width: width, height: 1)
            }

            DaypartWeatherBandsText(text: band.label, size: 11, weight: .bold, ink: ink, tracking: 2.2)
                .offset(x: 24, y: 16)
            DaypartWeatherBandsText(text: band.temp, size: 30, weight: .light, ink: ink, lineHeight: 36)
                .offset(x: 23, y: 30)
            DaypartWeatherBandsText(text: band.hours, size: 12, weight: .semibold, ink: ink, alignment: .trailing)
                .frame(width: width - 48, alignment: .trailing)
                .opacity(0.74)
                .offset(x: 24, y: 16)
            DaypartWeatherBandsText(text: band.feels, size: 13, ink: ink, alignment: .trailing)
                .frame(width: width - 48, alignment: .trailing)
                .opacity(0.84)
                .offset(x: 24, y: 38)

            Canvas { context, _ in
                DaypartWeatherBandsArtwork.icon(context, index: index, clock: clock)
            }
            .frame(width: 160, height: 160)
            .modifier(DaypartWeatherBandsReveal(openness: openness, alpha: alpha, from: 0.15, to: 0.8, rise: 18, grow: 0.28))
            .offset(x: 14, y: detailTop)
            DaypartWeatherBandsText(text: band.condition, size: 24, ink: ink, lineHeight: 30, fixedWidth: width - 204)
                .frame(width: width - 184 - 20, alignment: .leading)
                .modifier(DaypartWeatherBandsReveal(openness: openness, alpha: alpha, from: 0.35, to: 0.9, rise: 10))
                .offset(x: 184, y: detailTop + 42)
            // The reference's animated style overrides the note's base opacity of 0.78.
            DaypartWeatherBandsText(text: band.note, size: 14, ink: ink, lineHeight: 19, lines: 2, fixedWidth: width - 204)
                .frame(width: width - 184 - 20, alignment: .leading)
                .modifier(DaypartWeatherBandsReveal(openness: openness, alpha: alpha, from: 0.42, to: 0.95, rise: 10))
                .offset(x: 184, y: detailTop + 78)

            VStack(alignment: .leading, spacing: 16) {
                ink.opacity(0.2).frame(width: width - 48, height: 2 / displayScale)
                HStack(alignment: .top, spacing: 0) {
                    ForEach(0..<3, id: \.self) { i in
                        VStack(alignment: .leading, spacing: 4) {
                            DaypartWeatherBandsText(text: band.stats[i].0, size: 10.5, weight: .bold, ink: ink, tracking: 1.6)
                                .opacity(0.66)
                            DaypartWeatherBandsText(text: band.stats[i].1, size: 17, weight: .medium, ink: ink, lineHeight: 22)
                        }
                        .frame(width: columnWidth, alignment: .leading)
                    }
                }
            }
            .frame(width: width - 48, alignment: .leading)
            .modifier(DaypartWeatherBandsReveal(openness: openness, alpha: alpha, from: 0.5, to: 1, rise: 8))
            .offset(x: 24, y: hairTop)
        }
        // Deliberately not clipped: the next band's cast lies above its own bounds.
        .frame(width: width, height: fullHeight, alignment: .topLeading)
    }
}

private struct DaypartWeatherBandsReveal: ViewModifier {
    var openness: Double
    var alpha: Double
    var from: Double
    var to: Double
    var rise: Double
    var grow = 0.0

    func body(content: Content) -> some View {
        let e = DaypartWeatherBandsMotion.easeOutCubic(DaypartWeatherBandsMotion.seg(openness, from, to))
        content
            .opacity(e * alpha)
            .scaleEffect(1 - grow + grow * e)
            .offset(y: rise * (1 - e))
    }
}

/// Use the reference's iOS TextKit metrics, including its line-height baseline adjustment.
private struct DaypartWeatherBandsText: UIViewRepresentable {
    @Environment(\.displayScale) private var displayScale
    var text: String
    var size: Double
    var weight = UIFont.Weight.regular
    var ink: Color
    var tracking = 0.0
    var lineHeight: Double?
    var lines: Int? = nil
    var alignment = Alignment.leading
    var fixedWidth: Double?

    private var attributedText: NSAttributedString {
        let font = UIFont.systemFont(ofSize: size, weight: weight)
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor(ink)]
        if tracking != 0 { attributes[.kern] = tracking }
        if lineHeight != nil || alignment == .trailing {
            let paragraph = NSMutableParagraphStyle()
            if alignment == .trailing { paragraph.alignment = .right }
            if let lineHeight {
                paragraph.minimumLineHeight = lineHeight
                paragraph.maximumLineHeight = lineHeight
                if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
            }
            attributes[.paragraphStyle] = paragraph
        }
        return NSAttributedString(string: text, attributes: attributes)
    }

    func makeUIView(context: Context) -> DaypartWeatherBandsTextNative {
        let view = DaypartWeatherBandsTextNative()
        view.configure(attributedText, lines: lines ?? 0)
        return view
    }

    func updateUIView(_ view: DaypartWeatherBandsTextNative, context: Context) {
        view.configure(attributedText, lines: lines ?? 0)
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: DaypartWeatherBandsTextNative, context: Context) -> CGSize? {
        let width = fixedWidth ?? proposal.width ?? 10_000
        var size = uiView.measure(width: max(0, width), scale: displayScale)
        if let fixedWidth { size.width = fixedWidth }
        return size
    }
}

private final class DaypartWeatherBandsTextNative: UIView {
    private let storage = NSTextStorage()
    private let manager = NSLayoutManager()
    private let container = NSTextContainer(size: .zero)

    init() {
        super.init(frame: .zero)
        isOpaque = false
        backgroundColor = .clear
        isUserInteractionEnabled = false
        isAccessibilityElement = false
        container.lineFragmentPadding = 0
        manager.usesFontLeading = false
        manager.addTextContainer(container)
        storage.addLayoutManager(manager)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func configure(_ text: NSAttributedString, lines: Int) {
        guard !storage.isEqual(to: text) || container.maximumNumberOfLines != lines else { return }
        container.maximumNumberOfLines = lines
        container.lineBreakMode = lines > 0 ? .byTruncatingTail : .byClipping
        storage.setAttributedString(text)
        invalidateIntrinsicContentSize()
        setNeedsDisplay()
    }

    private func layout(width: CGFloat) {
        let size = CGSize(width: width, height: .greatestFiniteMagnitude)
        if container.size != size { container.size = size }
        manager.ensureLayout(for: container)
    }

    func measure(width: CGFloat, scale: CGFloat) -> CGSize {
        layout(width: width)
        var size = manager.usedRect(for: container).size
        var didWrap = false
        var lineCount = 0
        var linesHeight = 0.0
        let glyphs = manager.glyphRange(for: container)
        manager.enumerateLineFragments(forGlyphRange: glyphs) { _, used, _, range, stop in
            let characters = self.manager.characterRange(forGlyphRange: range, actualGlyphRange: nil)
            if NSMaxRange(characters) < self.storage.length { didWrap = true }
            if lineCount < self.container.maximumNumberOfLines { linesHeight = used.maxY }
            lineCount += 1
            if didWrap && (self.container.maximumNumberOfLines == 0 || lineCount >= self.container.maximumNumberOfLines) {
                stop.pointee = true
            }
        }
        if didWrap { size.width = width }
        if container.maximumNumberOfLines != 0 {
            if lineCount < container.maximumNumberOfLines { linesHeight += manager.extraLineFragmentUsedRect.height }
            size.height = linesHeight
        }
        return CGSize(width: ceil(size.width * scale) / scale, height: ceil(size.height * scale) / scale)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        setNeedsDisplay()
    }

    override func draw(_ rect: CGRect) {
        layout(width: bounds.width)
        let glyphs = manager.glyphRange(for: container)
        manager.drawBackground(forGlyphRange: glyphs, at: .zero)
        manager.drawGlyphs(forGlyphRange: glyphs, at: .zero)
    }
}

private enum DaypartWeatherBandsContent {
    struct Band {
        var label: String
        var spoken: String
        var hours: String
        var temp: String
        var feels: String
        var condition: String
        var note: String
        var stats: [(String, String)]
        var colors: [UInt32]
        var ink: UInt32
    }

    static let bands = [
        Band(label: "MORNING", spoken: "Morning, minus 1 degree, sunny", hours: "6–11 AM", temp: "−1°", feels: "Feels −4°",
             condition: "Sunny", note: "Clear and crisp until noon",
             stats: [("WIND", "E 7 mph"), ("HUMIDITY", "91%"), ("PRECIP", "0%")],
             colors: [0xEDC897, 0xE3BB88, 0xD9AB79], ink: 0x5A2F1F),
        Band(label: "DAY", spoken: "Day, plus 3 degrees, partly cloudy", hours: "11 AM–5 PM", temp: "+3°", feels: "Feels +1°",
             condition: "Partly Cloudy", note: "Clouds drift in after lunch",
             stats: [("WIND", "N 5 mph"), ("HUMIDITY", "45%"), ("PRECIP", "10%")],
             colors: [0xE2A472, 0xDB9864, 0xCE8858], ink: 0x47210F),
        Band(label: "EVENING", spoken: "Evening, plus 1 degree, light rain", hours: "5–10 PM", temp: "+1°", feels: "Feels −2°",
             condition: "Light Rain", note: "Showers ease off by nine",
             stats: [("WIND", "W 12 mph"), ("HUMIDITY", "88%"), ("PRECIP", "70%")],
             colors: [0xBA7465, 0xB1695A, 0xA15D52], ink: 0xFFF2E6),
        Band(label: "NIGHT", spoken: "Night, minus 2 degrees, light snow", hours: "10 PM–6 AM", temp: "−2°", feels: "Feels −6°",
             condition: "Light Snow", note: "Flurries settle before dawn",
             stats: [("WIND", "NW 9 mph"), ("HUMIDITY", "84%"), ("PRECIP", "60%")],
             colors: [0x6B4D51, 0x5F4346, 0x4E363B], ink: 0xF7E9DD)
    ]
}

private enum DaypartWeatherBandsPalette {
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }
    static let background = color(0x4E363B)
    static let headerInk = color(0xFBF6EE)

    static func gradient(_ colors: [UInt32], middle: Double) -> LinearGradient {
        LinearGradient(stops: [.init(color: color(colors[0]), location: 0),
                               .init(color: color(colors[1]), location: middle),
                               .init(color: color(colors[2]), location: 1)], startPoint: .top, endPoint: .bottom)
    }
}

// MARK: Pure motion, including the un-clamped spring overshoot

private enum DaypartWeatherBandsMotion {
    static let tau = 2 * Double.pi
    static let omega = tau / 0.45
    static let damping = 0.78
    static let move = 0.65

    static func seg(_ value: Double, _ a: Double, _ b: Double) -> Double {
        min(1, max(0, (value - a) / (b - a)))
    }
    static func easeOutCubic(_ value: Double) -> Double { 1 - pow(1 - value, 3) }
    // withTiming's default Easing.inOut(Easing.quad), used by the RN content cross-fade.
    static func inOutQuad(_ value: Double) -> Double {
        value < 0.5 ? 2 * value * value : 1 - pow(-2 * value + 2, 2) / 2
    }
    static func wrap(_ t: Double) -> Double {
        let remainder = t.truncatingRemainder(dividingBy: 8)
        return remainder < 0 ? remainder + 8 : remainder
    }
    static func fract(_ value: Double) -> Double { value - floor(value) }
    static func hash(_ index: Int) -> Double { fract(sin(Double(index) * 12.9898 + 1.7) * 43758.5453) }

    static func springStep(_ seconds: Double) -> Double {
        let wd = omega * sqrt(1 - damping * damping)
        return 1 - exp(-damping * omega * seconds) *
            (cos(wd * seconds) + ((damping * omega) / wd) * sin(wd * seconds))
    }
    static func handoff(_ seconds: Double) -> Double {
        if seconds <= 0 { return 0 }
        if seconds >= move { return 1 }
        return springStep(seconds) + (1 - springStep(move)) * (seconds / move)
    }
    static func autoWeights(_ t: Double) -> DaypartWeatherBandsWeights {
        let k = Int(floor(t / 2)) % 4
        let progress = handoff(t - Double(k) * 2 - 1.35)
        var result = DaypartWeatherBandsWeights.zero
        result[k] = 1 - progress
        result[(k + 1) % 4] += progress
        return result
    }
    static func tentWeights(_ focus: Double) -> DaypartWeatherBandsWeights {
        var result = DaypartWeatherBandsWeights.zero
        for i in 0..<4 { result[i] = max(0, 1 - abs(focus - Double(i))) }
        return result
    }
    static func rubber(_ focus: Double) -> Double {
        if focus < 0 {
            let over = -focus
            return -(over * 0.55) / (1 + over * 1.2)
        }
        if focus > 3 {
            let over = focus - 3
            return 3 + (over * 0.55) / (1 + over * 1.2)
        }
        return focus
    }
}

// MARK: Canvas artwork, in the reference's 160 × 160 coordinate space

private enum DaypartWeatherBandsArtwork {
    private typealias M = DaypartWeatherBandsMotion
    private typealias P = DaypartWeatherBandsPalette

    static let cloud: Path = {
        var path = Path(roundedRect: CGRect(x: -48, y: 2, width: 96, height: 26), cornerRadius: 13)
        path.addEllipse(in: CGRect(x: -44, y: -14, width: 40, height: 40))
        path.addEllipse(in: CGRect(x: -22, y: -32, width: 52, height: 52))
        path.addEllipse(in: CGRect(x: 13, y: -9, width: 34, height: 34))
        return path
    }()
    static let moon = Path(ellipseIn: CGRect(x: 36, y: 32, width: 52, height: 52))
        .subtracting(Path(ellipseIn: CGRect(x: 53, y: 25, width: 44, height: 44)))
    static let drop = line(0, 0, -2, 8)
    static let flake: Path = {
        var path = Path()
        for i in 0..<3 {
            let angle = Double(i) * .pi / 3 + .pi / 2
            path.addPath(line(cos(angle) * 4.5, sin(angle) * 4.5, -cos(angle) * 4.5, -sin(angle) * 4.5))
        }
        return path
    }()
    static let pin: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 7, y: 18))
        path.addCurve(to: CGPoint(x: 0, y: 7), control1: CGPoint(x: 7, y: 18), control2: CGPoint(x: 0, y: 10.5))
        path.addCurve(to: CGPoint(x: 7, y: 0), control1: CGPoint(x: 0, y: 3.1), control2: CGPoint(x: 3.1, y: 0))
        path.addCurve(to: CGPoint(x: 14, y: 7), control1: CGPoint(x: 10.9, y: 0), control2: CGPoint(x: 14, y: 3.1))
        path.addCurve(to: CGPoint(x: 7, y: 18), control1: CGPoint(x: 14, y: 10.5), control2: CGPoint(x: 7, y: 18))
        path.closeSubpath()
        path.addEllipse(in: CGRect(x: 4.4, y: 4.4, width: 5.2, height: 5.2))
        return path
    }()

    static func icon(_ context: GraphicsContext, index: Int, clock c: Double) {
        switch index {
        case 0:
            circle(context, x: 80, y: 80, radius: 52 + 3 * sin(M.tau * 2 * c / 8), color: P.color(0xFFF3DE).opacity(0.45))
            context.stroke(circlePath(80, 80, 29), with: .color(P.color(0xE57A3A)), lineWidth: 4.5)
            circle(context, x: 80, y: 80, radius: 21, color: P.color(0xE57A3A))
            rays(context, x: 80, y: 80, count: 10, radius: 38, length: 8, wobble: 3,
                 turn: c / 8 * (2 * M.tau / 10), clock: c, color: 0xE57A3A, width: 6)
        case 1:
            let glow = 0.55 + 0.45 * (0.5 + 0.5 * sin(M.tau * c / 8))
            circle(context, x: 60, y: 56, radius: 38, color: P.color(0xFFECC8).opacity(0.32 * glow))
            rays(context, x: 60, y: 56, count: 8, radius: 26, length: 6, wobble: 2,
                 turn: c / 8 * (M.tau / 8), clock: c, color: 0xFFE3B3, width: 4.5)
            circle(context, x: 60, y: 56, radius: 18, color: P.color(0xFFE3B3))
            drawCloud(context, x: 90 + 12 * sin(M.tau * c / 8), y: 98 + 2 * sin(M.tau * 2 * c / 8),
                      body: 0x5C3534, rim: 0x704543)
        case 2:
            rays(context, x: 50, y: 50, count: 7, radius: 22, length: 5, wobble: 1.5,
                 turn: c / 8 * (M.tau / 7), clock: c, color: 0x7A3A30, width: 4)
            circle(context, x: 50, y: 50, radius: 15, color: P.color(0x7A3A30))
            for (i, x) in [58.0, 73, 88, 103, 118, 133].enumerated() {
                particle(context, index: i, clock: c, x: x - 4, y: 112, fall: 38, period: 1,
                         drift: -6, sway: 0, spin: 0, path: drop, color: 0xF6D5AE, width: 4)
            }
            drawCloud(context, x: 88, y: 80 + 2 * sin(M.tau * 2 * c / 8), body: 0xF6D5AE, rim: 0xFFE8CB)
        default:
            circle(context, x: 62, y: 58, radius: 40, color: P.color(0xF2A86C).opacity(0.18))
            var moonContext = context
            moonContext.translateBy(x: 62, y: 58)
            moonContext.rotate(by: .radians(8 * .pi / 180 * sin(M.tau * c / 8)))
            moonContext.translateBy(x: -62, y: -58)
            moonContext.fill(moon, with: .color(P.color(0xF2A86C)))
            for (i, x) in [54.0, 68, 82, 96, 110, 124, 140].enumerated() {
                particle(context, index: i + 11, clock: c, x: x, y: 106, fall: 46, period: 2,
                         drift: 0, sway: 4, spin: .pi / 3, path: flake, color: 0xF6DEC4, width: 1.8)
            }
            drawCloud(context, x: 102 + 6 * sin(M.tau * c / 8 + .pi / 2), y: 88, scale: 0.78,
                      body: 0xEBD0B0, rim: 0xF7E3CB)
        }
    }

    private static func rays(_ context: GraphicsContext, x: Double, y: Double, count: Int,
                             radius: Double, length: Double, wobble: Double, turn: Double,
                             clock: Double, color: UInt32, width: Double) {
        var path = Path()
        for i in 0..<count {
            let angle = turn + Double(i) * M.tau / Double(count) - .pi / 2
            let rayLength = length + wobble * sin(M.tau * 2 * clock / 8 + Double(i) * M.tau * 2 / Double(count))
            path.addPath(line(x + cos(angle) * radius, y + sin(angle) * radius,
                              x + cos(angle) * (radius + rayLength), y + sin(angle) * (radius + rayLength)))
        }
        context.stroke(path, with: .color(P.color(color)), style: StrokeStyle(lineWidth: width, lineCap: .round))
    }

    private static func drawCloud(_ context: GraphicsContext, x: Double, y: Double, scale: Double = 1,
                                  body: UInt32, rim: UInt32) {
        var layer = context
        layer.translateBy(x: x, y: y)
        layer.scaleBy(x: scale, y: scale)
        layer.fill(cloud, with: .color(P.color(rim)))
        layer.translateBy(x: 0, y: 3)
        layer.fill(cloud, with: .color(P.color(body)))
    }

    private static func particle(_ context: GraphicsContext, index: Int, clock: Double,
                                 x: Double, y: Double, fall: Double, period: Double, drift: Double,
                                 sway: Double, spin: Double, path: Path, color: UInt32, width: Double) {
        let h = M.hash(index)
        let q = M.fract(clock / period + h)
        var layer = context
        layer.opacity = M.seg(q, 0, 0.15) * (1 - M.seg(q, 0.6, 1))
        layer.translateBy(x: x + drift * q + sway * sin(M.tau * (q + h)), y: y + fall * q)
        layer.rotate(by: .radians(spin * q))
        layer.stroke(path, with: .color(P.color(color)), style: StrokeStyle(lineWidth: width, lineCap: .round))
    }

    private static func circle(_ context: GraphicsContext, x: Double, y: Double, radius: Double, color: Color) {
        context.fill(circlePath(x, y, radius), with: .color(color))
    }
    private static func circlePath(_ x: Double, _ y: Double, _ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
    }
    private static func line(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: x1, y: y1))
        path.addLine(to: CGPoint(x: x2, y: y2))
        return path
    }
}
