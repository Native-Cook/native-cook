import SwiftUI
import UIKit

private typealias Colors = PulseHeartCardTheme.Colors
private typealias Layout = PulseHeartCardTheme.Layout
private typealias Space = PulseHeartCardTheme.Space
private typealias M = PulseHeartCardMotion

/// A cardio summary built around a living heart: an ECG trace sweeps across it and each R-peak makes
/// the heart beat; scrubbing the range chart sets the heart to that half hour's BPM.
struct PulseHeartCard: View {
    static let loop = 8.0

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @State private var engine: PulseHeartCardEngine
    /// Bumped whenever the timelines have to start again (a touch, a reset, a scene change).
    @State private var generation = 0
    @State private var feedback = UISelectionFeedbackGenerator()

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _engine = State(initialValue: PulseHeartCardEngine(autoPlay: autoPlay))
    }

    private var quiet: Bool { reduceMotion || voiceOver }
    private var now: Double { Date.timeIntervalSinceReferenceDate }
    private var selectedBPM: Int { engine.selectedIndex.map { M.samples[$0] } ?? Int(M.restBPM) }

    var body: some View {
        GeometryReader { proxy in
            let scale = proxy.size.width / PulseHeartCardTheme.referenceWidth
            let insets = PulseHeartCardSafeArea.insets
            let contentHeight = max(proxy.size.height - insets.top, (PulseHeartCardTheme.referenceHeight - PulseHeartCardTheme.referenceSafeTop) * scale)
            ZStack(alignment: .topLeading) {
                Colors.background
                ScrollView(.vertical, showsIndicators: false) {
                    stage(scale: scale, width: proxy.size.width, height: contentHeight)
                        .padding(.top, insets.top)
                        .padding(.bottom, max(insets.bottom, 16))
                }
                PulseHeartCardTabBar(scale: scale, still: quiet)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .padding(.bottom, Layout.tabBottom * scale)
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
        .ignoresSafeArea()
        .background(Colors.background.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            let binding = $generation
            engine.onSelectionCleared = { binding.wrappedValue += 1 }
            engine.setQuiet(quiet, at: now)
            engine.setForeground(scenePhase == .active, at: now)
            generation += 1
        }
        .onChange(of: autoPlay) { _, value in
            engine.setAutoPlay(value, at: now)
            generation += 1
        }
        .onChange(of: quiet) { _, value in
            engine.setQuiet(value, at: now)
            generation += 1
        }
        .onChange(of: scenePhase) { _, phase in
            engine.setForeground(phase == .active, at: now)
            generation += 1
        }
    }

    // MARK: Stage

    private func top(_ referenceY: Double, _ scale: Double) -> Double {
        (referenceY - PulseHeartCardTheme.referenceSafeTop) * scale
    }

    private func stage(scale: Double, width: Double, height: Double) -> some View {
        let s = scale
        let copyLeft = (width - Layout.headlineWidth * s) / 2
        return ZStack(alignment: .topLeading) {
            heartLayer(scale: s, width: width, height: height)

            metricStrip(scale: s, width: width)
                .offset(y: 0)

            PulseHeartCardText.lines(
                "Cardio Fitness\nTrending Up",
                size: Layout.headlineSize * s, line: Layout.headlineLine * s, weight: .bold, uiWeight: .bold, color: Colors.ink,
                width: Layout.headlineWidth * s, height: Layout.headlineHeight * s
            )
            .offset(x: Layout.headlineLeft * s, y: top(Layout.headlineTop, s))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Cardio Fitness. Trending Up.")

            PulseHeartCardText.lines(
                "On average, your cardio fitness\nwent up over the last month. This is\ngreat for your long-term health.",
                size: Layout.bodySize * s, line: Layout.bodyLine * s, weight: .regular, uiWeight: .regular, color: Colors.muted,
                width: Layout.bodyWidth * s, height: Layout.bodyHeight * s
            )
            .offset(x: Layout.bodyLeft * s, y: top(Layout.bodyTop, s))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("On average, your cardio fitness went up over the last month. This is great for your long-term health.")

            PulseHeartCardChart(
                scale: s, engine: engine, generation: generation, selectedIndex: engine.selectedIndex,
                onTouch: { x in scrub(x: x, scale: s) },
                onEnd: { release() },
                onAdjust: { step in adjust(step) }
            )
            .offset(x: width / 2 - Layout.chartWidth / 2 * s, y: top(Layout.chartTop, s))

            supportRow(top: Layout.supportTop, label: "Supporting metrics", left: copyLeft, scale: s, width: width, items: [("Resting Heart Rate", "55 BPM"), ("Recovery HRV", "54 MS")])
            // The pin's next row, mostly under the glass tab bar.
            supportRow(top: Layout.supportNextTop, label: "More metrics", left: copyLeft, scale: s, width: width, items: [("Walking Heart Rate", "98 BPM"), ("Cardio Recovery", "28 BPM")])
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    // MARK: Heart, reading

    /// The heart canvas (one shader) and the reading printed on it, drawn from one engine frame.
    private func heartLayer(scale: Double, width: Double, height: Double) -> some View {
        let s = scale
        return TimelineView(PulseHeartCardSchedule(engine: engine, generation: generation)) { context in
            let frame = engine.frame(at: context.date.timeIntervalSinceReferenceDate)
            let bpm = frame.selected.map { M.samples[$0] } ?? Int(M.restBPM)
            let body = frame.beat.body
            let lift = frame.beat.lift
            let canvasHeight = (Layout.heartCanvasBottom - Layout.heartCanvasTop) * s
            ZStack(alignment: .topLeading) {
                Rectangle()
                    .fill(Color.white)
                    .frame(width: width, height: canvasHeight)
                    .colorEffect(ShaderLibrary.pulseHeartCardHeart(
                        .float(Float(s)),
                        .float4(Float(body), Float(frame.beat.lobe), Float(frame.beat.squash), Float(lift)),
                        .float4(Float(frame.beat.gloss), Float(frame.beat.light), Float(frame.beat.push), Float(frame.beat.flash)),
                        .float2(Float(frame.tau0), Float(frame.tau1)),
                        .float3(Float(frame.phi), Float(frame.traceStart), Float(frame.traceEnd))
                    ))
                    .offset(y: top(Layout.heartCanvasTop, s))
                    .allowsHitTesting(false)

                // The reading rides 60 % of the beat and flashes white on the R-peak.
                let ride = 1 + 0.6 * body
                let numberCentre = Layout.numberTop + Layout.numberLine / 2
                let unitCentre = Layout.unitTop + Layout.unitLine / 2
                let numberShift = ((numberCentre - Layout.heartCentreY) * (ride - 1) + 0.6 * lift) * s
                let unitShift = ((unitCentre - Layout.heartCentreY) * (ride - 1) + 0.6 * lift) * s

                Text(String(bpm))
                    .font(.system(size: Layout.numberSize * s, weight: .semibold))
                    .monospacedDigit()
                    .tracking(Layout.numberTracking * s)
                    .foregroundStyle(Colors.ink)
                    .lineLimit(1)
                    .frame(width: width, height: Layout.numberLine * s)
                    .scaleEffect(ride)
                    .offset(y: top(Layout.numberTop, s) + numberShift)
                    .accessibilityHidden(true)
                reading("BPM", color: Colors.bpmUnit, scale: s, width: width, ride: ride, shift: unitShift, opacity: 1)
                reading("BPM", color: Colors.flash, scale: s, width: width, ride: ride, shift: unitShift, opacity: 0.85 * frame.beat.flash)

                Color.clear
                    .frame(width: width / 2, height: (Layout.unitTop + Layout.unitLine - Layout.numberTop) * s)
                    .offset(x: width / 4, y: top(Layout.numberTop, s))
                    .accessibilityElement()
                    .accessibilityLabel("Heart rate")
                    .accessibilityValue("\(bpm) beats per minute")
            }
            .frame(width: width, height: height, alignment: .topLeading)
        }
    }

    private func reading(_ text: String, color: Color, scale s: Double, width: Double, ride: Double, shift: Double, opacity: Double) -> some View {
        Text(text)
            .font(.system(size: Layout.unitSize * s, weight: .medium))
            .foregroundStyle(color)
            .lineLimit(1)
            .frame(width: width, height: Layout.unitLine * s)
            .scaleEffect(ride)
            .offset(y: top(Layout.unitTop, s) + shift)
            .opacity(opacity)
            .accessibilityHidden(true)
    }

    // MARK: Metric strip

    private func metricStrip(scale s: Double, width: Double) -> some View {
        HStack(spacing: 0) {
            chip(title: "Fitness", value: "5,253 steps", width: Layout.chipFitness, scale: s, active: false) {
                PulseHeartCardFitnessIcon(scale: s)
            }
            Spacer().frame(width: (Space.chipGap + 1) * s)
            Button {
                resetFromHeart()
            } label: {
                TimelineView(PulseHeartCardSchedule(engine: engine, generation: generation)) { context in
                    let frame = engine.frame(at: context.date.timeIntervalSinceReferenceDate)
                    let bpm = frame.selected.map { M.samples[$0] } ?? Int(M.restBPM)
                    chip(title: "Heart", value: "\(bpm) BPM", width: Layout.chipHeart, scale: s, active: true) {
                        PulseHeartCardHeartIcon(size: Layout.chipIcon * s)
                            .scaleEffect(1 + 1.5 * frame.beat.body)
                    }
                }
            }
            .buttonStyle(PulseHeartCardPressStyle())
            .accessibilityLabel("Heart, \(selectedBPM) BPM")
            Spacer().frame(width: Space.chipGap * s)
            chip(title: "Vitals", value: "Typical", width: Layout.chipVitals, scale: s, active: false) {
                PulseHeartCardVitalsIcon(scale: s)
            }
        }
        .frame(width: width, height: Layout.stripHeight * s, alignment: .leading)
        .clipped()
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Cardio metrics")
    }

    private func chip<Icon: View>(title: String, value: String, width: Double, scale s: Double, active: Bool, @ViewBuilder icon: () -> Icon) -> some View {
        let shape = RoundedRectangle(cornerRadius: Layout.stripRadius * s, style: .continuous)
        let labelWidth = (width - 2 * Space.chipPadding - Layout.chipIcon - Space.chipIconGap) * s
        return HStack(spacing: 0) {
            icon().frame(width: Layout.chipIcon * s, height: Layout.chipIcon * s)
            VStack(alignment: .leading, spacing: 0) {
                PulseHeartCardText.line(title, size: 16 * s, line: 19 * s, weight: .regular, color: Colors.muted)
                    .frame(width: labelWidth, alignment: .leading)
                Text(value)
                    .font(.system(size: 17 * s, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Colors.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
                    .allowsTightening(true)
                    .frame(width: labelWidth, height: 21 * s, alignment: .leading)
            }
            .frame(width: labelWidth, alignment: .leading)
            .padding(.leading, Space.chipIconGap * s)
        }
        .padding(.horizontal, Space.chipPadding * s)
        .frame(width: width * s, height: Layout.stripHeight * s)
        .background(active ? Color.white : Colors.card, in: shape)
        .overlay {
            if !active { shape.strokeBorder(Colors.border, lineWidth: Space.hairline * s) }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title), \(value)")
    }

    // MARK: Supporting metrics

    private func supportRow(top y: Double, label: String, left: Double, scale s: Double, width: Double, items: [(String, String)]) -> some View {
        HStack(alignment: .top, spacing: 0) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                if index > 0 { Spacer().frame(width: Space.metricGap * s) }
                supportMetric(title: item.0, value: item.1, scale: s)
            }
        }
        .frame(width: Layout.headlineWidth * s, alignment: .topLeading)
        .offset(x: left, y: top(y, s))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(label)
    }

    private func supportMetric(title: String, value: String, scale s: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Text(title)
                    .font(.system(size: Layout.supportTitleSize * s, weight: .semibold))
                    .foregroundStyle(Colors.heartDark)
                    .lineLimit(1)
                PulseHeartCardChevron(scale: s)
            }
            .frame(height: Layout.supportTitleLine * s)
            PulseHeartCardText.line(value, size: Layout.supportValueSize * s, line: Layout.supportValueLine * s, weight: .semibold, color: Colors.supportValue)
                .padding(.top, Layout.supportValueGap * s)
            PulseHeartCardText.line("Typical", size: Layout.supportNoteSize * s, line: Layout.supportNoteLine * s, weight: .regular, color: Colors.muted)
        }
        .frame(width: Layout.supportWidth * s, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title), \(value)")
    }

    // MARK: Interaction

    private func scrub(x: Double, scale: Double) {
        let wasAsleep = engine.wakeAt != nil
        let result = engine.select(index: M.clampIndex((x / scale - 3.9) / 7.25), at: now)
        if result.haptic { feedback.selectionChanged() }
        if result.changed || wasAsleep { generation += 1 }
    }

    private func release() {
        engine.release(at: now)
        generation += 1
    }

    private func adjust(_ step: Int) {
        let current = engine.selectedIndex ?? M.barBounds.count - 1
        engine.select(index: current + step, at: now)
        engine.release(at: now)
        generation += 1
    }

    private func resetFromHeart() {
        engine.resetNow(at: now)
        generation += 1
    }
}

/// The window's safe-area insets. The block lays out edge to edge (`.ignoresSafeArea()`), so a
/// GeometryReader inside it reports zero insets; read them from the key window instead.
enum PulseHeartCardSafeArea {
    @MainActor static var insets: EdgeInsets {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        let i = window?.safeAreaInsets ?? .zero
        return EdgeInsets(top: i.top, leading: i.left, bottom: i.bottom, trailing: i.right)
    }
}
