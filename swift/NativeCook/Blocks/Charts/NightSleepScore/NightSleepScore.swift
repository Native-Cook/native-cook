import SwiftUI
import UIKit

private struct NightSleepScoreChartFrameKey: PreferenceKey {
    static var defaultValue = CGRect.zero
    static func reduce(value: inout CGRect, nextValue: () -> CGRect) { value = nextValue() }
}

struct NightSleepScore: View {
    static let loop = NightSleepScoreMotion.loop
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @State private var driver = NightSleepScoreDriver()
    @State private var running = true
    @State private var revision = 0
    @State private var activeTab: NightSleepScoreTab = .insights
    @State private var chartVisible = true
    @State private var hapticTick = 0

    private typealias Theme = NightSleepScoreTheme
    private var controller: NightSleepScoreController { driver.controller }
    private var quiet: Bool { reduceMotion || voiceOver }
    private var now: TimeInterval { Date().timeIntervalSinceReferenceDate }

    var body: some View {
        GeometryReader { geometry in
            let scale = geometry.size.width / 402
            let top = NightSleepScoreSafeArea.insets.top
            ZStack(alignment: .topLeading) {
                Theme.page
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 0) {
                        Color.clear.frame(height: top)
                        NightSleepScoreMetricRail(scale: scale)
                        if activeTab == .insights {
                            dashboard(scale: scale)
                        } else {
                            destination(scale: scale)
                        }
                    }
                    .frame(width: geometry.size.width, alignment: .topLeading)
                    .padding(.bottom, 112 * scale)
                    .frame(minHeight: geometry.size.height + 180 * scale, alignment: .top)
                }
                .id(activeTab)
                Theme.page.frame(height: top).allowsHitTesting(false)
                NightSleepScoreScrollEdgeBlur(scale: scale)
                    .frame(maxHeight: .infinity, alignment: .bottom)
                NightSleepScoreGlassTabBar(active: activeTab, scale: scale, still: quiet, onSelect: selectTab)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .padding(.bottom, Theme.Tab.bottom * scale)
            }
            .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
            .onPreferenceChange(NightSleepScoreChartFrameKey.self) { frame in
                // A zero frame means the chart has not been laid out yet, not that it is off screen.
                guard frame != .zero else { return }
                let visible = frame.maxY > top && frame.minY < geometry.size.height - 105 * scale
                guard visible != chartVisible else { return }
                chartVisible = visible
                let time = now
                controller.setVisible(activeTab == .insights && visible, at: time)
                wake(at: time)
            }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .sensoryFeedback(.selection, trigger: hapticTick)
        .onAppear {
            let time = now
            controller.appear(autoPlay: autoPlay, quiet: quiet, at: time)
            // A foreground launch briefly reports `.inactive`; the following scene change owns suspension.
            if scenePhase == .background { controller.setActive(false, at: time) }
            controller.setVisible(activeTab == .insights && chartVisible, at: time)
            wake(at: time)
        }
        .onChange(of: scenePhase) { _, phase in
            let time = now
            controller.setActive(phase == .active, at: time)
            wake(at: time)
        }
        .onChange(of: quiet) { _, enabled in
            let time = now
            controller.setQuiet(enabled, at: time)
            wake(at: time)
        }
        .onChange(of: autoPlay) { _, enabled in
            let time = now
            controller.setAutoPlay(enabled, at: time)
            wake(at: time)
        }
    }

    // MARK: Insights

    private func dashboard(scale: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("A Good Night’s Sleep")
                .font(.system(size: 34 * scale, weight: .bold)).tracking(-0.45 * scale)
                .foregroundStyle(Theme.Day.ink.color).lineLimit(1).fixedSize(horizontal: true, vertical: false)
                .frame(height: 41 * scale, alignment: .leading)
                .padding(.top, 27.6 * scale).padding(.horizontal, 32 * scale)
                .frame(height: 75 * scale, alignment: .topLeading)
                .accessibilityAddTraits(.isHeader)
            Text("You got about an hour more sleep last night than the night before, which helped lift your sleep score.")
                .font(.system(size: 22 * scale)).foregroundStyle(Theme.Day.secondary.color)
                .lineSpacing(28 * scale - 26.3 * scale)
                .frame(width: 338 * scale, height: 84 * scale, alignment: .topLeading)
                .padding(.top, -3 * scale).padding(.horizontal, 32 * scale)
            sleepCard(scale: scale)
                .padding(.top, 15 * scale).padding(.horizontal, 16 * scale)
            readinessCard(scale: scale)
                .padding(.top, 11 * scale).padding(.horizontal, 16 * scale)
        }
    }

    private func sleepCard(scale: Double) -> some View {
        TimelineView(.animation(minimumInterval: nil, paused: !running)) { timeline in
            let _ = revision
            let sample = driver.sample(at: timeline.date.timeIntervalSinceReferenceDate)
            let selected = controller.selectedMinute
            NightSleepScoreCard(
                scale: scale, sample: sample,
                selectedMinute: Int((selected ?? 0).rounded(.down)),
                manual: controller.hasManualSelection && sample.frame.selectionPresence > 0.001,
                onDone: resetSelection,
                onLegendTap: { selectStage($0) },
                onAdjust: { adjustStage(forward: $0) },
                onChartBegan: { x in chartTouch(x, scale: scale, began: true) },
                onChartMoved: { x in chartTouch(x, scale: scale, began: false) },
                onChartEnded: chartEnded,
                onChartCancelled: chartCancelled)
            .onChange(of: sample.running) { _, next in if running != next { running = next } }
            .background {
                GeometryReader { proxy in
                    let c = NightSleepScoreTheme.Card.Chart.self
                    Color.clear.preference(key: NightSleepScoreChartFrameKey.self, value: CGRect(
                        x: 0, y: proxy.frame(in: .global).minY + c.top * scale, width: 1, height: c.height * scale))
                }
            }
        }
        .id(running)
    }

    private func readinessCard(scale s: Double) -> some View {
        let G = Theme.Gauge.self
        let shape = RoundedRectangle(cornerRadius: 28 * s, style: .continuous)
        return ZStack(alignment: .topLeading) {
            shape.fill(Theme.card).shadow(color: Theme.shadow, radius: 18 * s, x: 0, y: 4 * s)
            shape.strokeBorder(Color.white.opacity(0.75), lineWidth: 1).allowsHitTesting(false)
            Text("Readiness").font(.system(size: 15 * s)).foregroundStyle(Theme.Day.secondary.color).offset(x: 16 * s, y: 16.3 * s)
            Text("Ready").font(.system(size: 19 * s, weight: .bold)).foregroundStyle(Theme.Day.ink.color).offset(x: 16 * s, y: 38 * s)
            gauge(scale: s)
                .frame(width: 110 * s, height: 80 * s)
                .offset(x: (G.cx - 55) * s, y: (G.cy - 57) * s)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Readiness 7")
                .accessibilityAddTraits(.isImage)
            Rectangle().fill(Theme.Day.divider.color).frame(width: 338 * s, height: max(1, s)).offset(x: 16 * s, y: 88 * s)
            Text("Your training load has been lower than your normal level for the past few days and all your other metrics are in good shape. It looks like you’re ready for a demanding day.")
                .font(.system(size: 15 * s)).lineSpacing(20 * s - 17.9 * s)
                .foregroundStyle(Theme.Day.ink.color)
                .frame(width: 338 * s, alignment: .topLeading)
                .offset(x: 16 * s, y: 103 * s)
        }
        .frame(width: 370 * s, height: 290 * s, alignment: .topLeading)
    }

    private func gauge(scale s: Double) -> some View {
        let G = Theme.Gauge.self
        return ZStack {
            Canvas { c, size in
                let k = s
                let origin = CGPoint(x: 55 * k, y: 57 * k)
                func arc(_ from: Double, _ to: Double) -> Path {
                    nightSleepScoreArc(center: origin, radius: G.r * k, from: from, to: to)
                }
                let style = StrokeStyle(lineWidth: G.stroke * k, lineCap: .round)
                c.stroke(arc(G.from, G.to), with: .color(G.track), style: style)
                c.stroke(arc(G.lightFrom, G.lightTo), with: .color(G.light), style: style)
                let a = G.dot * 2 * Double.pi
                let d = CGPoint(x: origin.x + G.r * k * cos(a), y: origin.y + G.r * k * sin(a))
                c.fill(Path(ellipseIn: CGRect(x: d.x - G.dotR * k, y: d.y - G.dotR * k, width: 2 * G.dotR * k, height: 2 * G.dotR * k)), with: .color(G.blue))
            }
            Text("7").font(.system(size: G.numberSize * s, weight: .bold)).foregroundStyle(G.blue)
                .position(x: 55 * s, y: (57 + G.numberBase - G.numberSize * 0.36) * s)
        }
    }

    // MARK: Other tabs

    private func destination(scale s: Double) -> some View {
        let isLongevity = activeTab == .longevity
        let title = isLongevity ? "A gentler evening" : "Sleep stages"
        let text = isLongevity
            ? ["Small routines can make room for a calmer night.", "Keep your wake time steady when it feels comfortable.", "Dim bright lights during your wind-down.", "Leave a little quiet before bed."]
            : ["A quick guide to the colors in your night.", "Awake: 14 minutes marked in coral.", "REM: 1 hour 3 minutes marked in turquoise.", "Core: 5 hours 33 minutes marked in blue.", "Deep: 36 minutes marked in violet."]
        let shape = RoundedRectangle(cornerRadius: 28 * s, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) {
            Text(title).font(.system(size: 28 * s, weight: .bold)).tracking(-0.7 * s).foregroundStyle(Theme.Day.ink.color)
                .padding(.bottom, 18 * s)
            ForEach(0..<text.count, id: \.self) { i in
                Text(text[i]).font(.system(size: 17 * s)).lineSpacing(24 * s - 20.3 * s)
                    .foregroundStyle(Theme.Day.secondary.color)
                    .padding(i > 0 ? 16 * s : 0)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(i > 0 ? Color.white.opacity(0.72) : .clear, in: RoundedRectangle(cornerRadius: 18 * s, style: .continuous))
                    .padding(.bottom, i == text.count - 1 ? 0 : 18 * s)
            }
        }
        .padding(24 * s)
        .background(shape.fill(Theme.card).shadow(color: Theme.shadow, radius: 18 * s, x: 0, y: 4 * s))
        .overlay(shape.strokeBorder(Color.white.opacity(0.75), lineWidth: 1))
        .padding(.horizontal, 16 * s).padding(.top, 46 * s)
    }

    // MARK: Interaction

    private func chartTouch(_ x: CGFloat, scale: Double, began: Bool) {
        let C = NightSleepScoreTheme.Card.Chart.self
        let local = min(C.width * scale, max(0, Double(x) - C.touchInset * scale))
        let minute = local / (C.width * scale) * Double(NightSleepScoreData.windowMinutes)
        let time = now
        if began { controller.beginScrub(minute: minute, at: time) } else { controller.updateScrub(minute: minute) }
        wake(at: time)
    }

    private func chartEnded() {
        let time = now
        controller.endScrub(at: time)
        hapticTick += 1
        wake(at: time)
    }

    private func chartCancelled() {
        let time = now
        controller.cancelHeldTouch(at: time)
        wake(at: time)
    }

    private func selectStage(_ stage: NightSleepScoreStage) {
        let time = now
        controller.select(stage: stage, at: time)
        hapticTick += 1
        wake(at: time)
    }

    private func adjustStage(forward: Bool) {
        let time = now
        controller.adjustStage(forward: forward, at: time)
        hapticTick += 1
        wake(at: time)
    }

    private func resetSelection() {
        let time = now
        controller.reset(at: time)
        hapticTick += 1
        wake(at: time)
    }

    private func selectTab(_ next: NightSleepScoreTab) {
        guard next != activeTab else { return }
        activeTab = next
        hapticTick += 1
        let time = now
        controller.setVisible(next == .insights && chartVisible, at: time, clearSelection: true)
        wake(at: time)
    }

    /// Samples once and re-renders the (possibly paused) card.
    private func wake(at time: TimeInterval) {
        let sample = driver.sample(at: time)
        running = sample.running
        revision += 1
    }
}

/// The window's safe-area insets. The block lays out edge to edge (`.ignoresSafeArea()`), so a
/// GeometryReader inside it reports zero insets; read them from the key window instead.
enum NightSleepScoreSafeArea {
    @MainActor static var insets: EdgeInsets {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        let i = window?.safeAreaInsets ?? .zero
        return EdgeInsets(top: i.top, leading: i.left, bottom: i.bottom, trailing: i.right)
    }
}
