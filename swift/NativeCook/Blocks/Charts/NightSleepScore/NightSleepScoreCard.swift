import SwiftUI

/// The static day chart (grid, pale core track, connectors, rounded bars) in design points of the chart area.
enum NightSleepScoreChartGeometry {
    private typealias Chart = NightSleepScoreTheme.Card.Chart
    struct Bar { let index: Int; let x: Double; let y: Double; let width: Double; let stage: NightSleepScoreStage }

    static let bars: [Bar] = {
        var elapsed = 0
        return NightSleepScoreData.segments.enumerated().map { index, segment in
            let left = Chart.width * Double(elapsed) / Double(NightSleepScoreData.windowMinutes)
            elapsed += segment.minutes
            let right = Chart.width * Double(elapsed) / Double(NightSleepScoreData.windowMinutes)
            return Bar(index: index, x: left, y: Chart.level(segment.stage) - Chart.barHeight / 2,
                       width: max(1.5, right - left - Chart.barGap), stage: segment.stage)
        }
    }()

    static let connector: Path = {
        var path = Path()
        var elapsed = 0
        var previous: CGPoint?
        for segment in NightSleepScoreData.segments {
            let left = Chart.width * Double(elapsed) / Double(NightSleepScoreData.windowMinutes)
            let y = Chart.level(segment.stage)
            if let previous { path.move(to: previous); path.addLine(to: CGPoint(x: left, y: y)) }
            elapsed += segment.minutes
            previous = CGPoint(x: Chart.width * Double(elapsed) / Double(NightSleepScoreData.windowMinutes), y: y)
        }
        return path
    }()
}

/// The sleep card: day card, night sky, text that turns light when the sky reaches it, ring, legend, hypnogram, readout.
struct NightSleepScoreCard: View {
    let scale: Double
    let sample: NightSleepScoreSample
    let selectedMinute: Int
    let manual: Bool
    let onDone: () -> Void
    let onLegendTap: (NightSleepScoreStage) -> Void
    let onAdjust: (Bool) -> Void
    let onChartBegan: (CGFloat) -> Void
    let onChartMoved: (CGFloat) -> Void
    let onChartEnded: () -> Void
    let onChartCancelled: () -> Void

    private typealias Theme = NightSleepScoreTheme
    private typealias Chart = NightSleepScoreTheme.Card.Chart
    private typealias At = NightSleepScoreTheme.Card.At
    private typealias M = NightSleepScoreMotion
    private let stages = NightSleepScoreStage.allCases

    private var s: Double { scale }
    private var night: Double { sample.sky.night }
    private var presence: Double { sample.frame.selectionPresence }
    private func front(_ at: Double) -> Double { M.nightAt(night, at) }
    private func tint(_ day: NightSleepScoreRGBA, _ dark: NightSleepScoreRGBA, _ at: Double) -> Color { day.mixed(dark, front(at)) }

    private var visibleIndex: Int? { presence > 0.001 ? sample.frame.segmentIndex : nil }

    private func semantic() -> String {
        guard let i = visibleIndex else { return "No stage selected" }
        let label = NightSleepScoreData.segments[i].stage.title
        return manual ? "Selected \(label), \(selectedMinute) minutes into the overnight window" : "Night replay, \(label)"
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Theme.Card.radius * s, style: .continuous)
                .fill(Theme.card)
                .shadow(color: Theme.shadow, radius: 18 * s, x: 0, y: 4 * s)
            if night > 0.0005 { NightSleepScoreSkyBackdrop(scale: s, sky: sample.sky) }
            RoundedRectangle(cornerRadius: Theme.Card.radius * s, style: .continuous)
                .strokeBorder(Theme.Day.border.mixed(Theme.Night.border, night), lineWidth: 1)
                .allowsHitTesting(false)
            header
            ring
            divider(at: At.divider1, y: 134)
            legend
            chart
            NightSleepScoreOverlay(scale: s, sky: sample.sky)
            divider(at: At.divider2, y: 247)
            readout
        }
        .frame(width: Theme.Card.width * s, height: Theme.Card.height * s, alignment: .topLeading)
    }

    // MARK: Text column

    private func label(_ text: String, size: Double, weight: Font.Weight = .regular, line: Double,
                       day: NightSleepScoreRGBA, dark: NightSleepScoreRGBA, at: Double) -> some View {
        Text(text).font(.system(size: size * s, weight: weight)).foregroundStyle(tint(day, dark, at))
            .frame(height: line * s, alignment: .leading)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            label("Sleep", size: 15, line: 18, day: Theme.Day.secondary, dark: Theme.Night.secondary, at: At.label)
                .padding(.top, -0.7 * s)
            label("High Score", size: 19, weight: .bold, line: 24, day: Theme.Day.ink, dark: Theme.Night.ink, at: At.title)
                .padding(.top, 1.4 * s)
            label("Duration", size: 15, line: 18, day: Theme.Day.secondary, dark: Theme.Night.secondary, at: At.duration)
                .padding(.top, 13 * s)
            label("7h 12m", size: 15, weight: .semibold, line: 18, day: Theme.Day.ink, dark: Theme.Night.ink, at: At.durationValue)
                .padding(.top, 2 * s)
        }
        .offset(x: 16 * s, y: 18 * s)
        .accessibilityElement(children: .combine)
    }

    // MARK: Score ring

    private var ring: some View {
        let R = Theme.Card.Ring.self
        let dayOpacity = 1 - front(At.ring)
        return ZStack(alignment: .topLeading) {
            Canvas { c, size in
                let k = size.width / 112
                let center = CGPoint(x: 56 * k, y: 56 * k)
                let r = R.radius * k
                c.stroke(Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: 2 * r, height: 2 * r)),
                         with: .color(Theme.Day.divider.color), lineWidth: 1 * k)
                func seg(_ from: Double, _ to: Double, _ color: Color) {
                    c.stroke(nightSleepScoreArc(center: center, radius: r, from: from - 0.25, to: to - 0.25), with: .color(color),
                             style: StrokeStyle(lineWidth: 16 * k, lineCap: .round))
                }
                seg(0.75, 0.965, Theme.teal)
                seg(0.025, 0.19, Theme.orange)
                seg(0.25, 0.69, Theme.blue)
            }
            .opacity(dayOpacity)
            Text("84").font(.system(size: 28.5 * s, weight: .bold)).foregroundStyle(tint(Theme.Day.ink, Theme.Night.ink, At.ring))
                .position(x: 55 * s, y: 55.8 * s)
        }
        .frame(width: 112 * s, height: 112 * s)
        .offset(x: (R.cx - 56) * s, y: (R.cy - 56) * s)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Sleep score 84")
        .accessibilityAddTraits(.isImage)
    }

    private func divider(at: Double, y: Double) -> some View {
        Rectangle().fill(tint(Theme.Day.divider, Theme.Night.divider, at))
            .frame(width: 338 * s, height: max(1, s))
            .offset(x: 16 * s, y: y * s)
            .allowsHitTesting(false)
    }

    // MARK: Legend

    private func minutesText(_ stage: NightSleepScoreStage) -> String {
        let minutes = NightSleepScoreData.total(for: stage)
        return minutes < 60 ? "\(minutes)m" : "\(minutes / 60)h \(minutes % 60)m"
    }

    private var legendStageIndex: Int {
        guard let i = visibleIndex else { return 0 }
        let stage = NightSleepScoreData.segments[i].stage
        return stages.firstIndex(of: stage) ?? 0
    }

    private var legendHighlight: some View {
        let k = legendStageIndex
        let color: Color = Theme.Day.highlight.mixed(Theme.Night.highlight, front(At.legend[k]))
        return RoundedRectangle(cornerRadius: 11 * s, style: .continuous)
            .fill(color)
            .opacity(0.12 * presence)
            .frame(width: 145 * s, height: 22 * s)
            .offset(x: -6 * s, y: Double(k) * 22 * s)
    }

    private func legendRow(_ stage: NightSleepScoreStage) -> some View {
        let i = stages.firstIndex(of: stage) ?? 0
        let at = At.legend[i]
        let label: Color = tint(Theme.Day.stages.rgba(stage), Theme.Night.stages.rgba(stage), at)
        let value: Color = tint(Theme.Day.ink, Theme.Night.ink, at)
        return HStack {
            Text(stage.title).font(.system(size: 15 * s, weight: .semibold)).foregroundStyle(label)
            Spacer(minLength: 0)
            Text(minutesText(stage)).font(.system(size: 15 * s, weight: .bold)).foregroundStyle(value)
        }
        .frame(width: 135 * s, height: 22 * s)
    }

    private var legend: some View {
        ZStack(alignment: .topLeading) {
            legendHighlight
            VStack(spacing: 0) {
                legendRow(.awake)
                legendRow(.rem)
                legendRow(.core)
                legendRow(.deep)
            }
        }
        .frame(width: 135 * s, height: 88 * s, alignment: .topLeading)
        .contentShape(Rectangle())
        .onTapGesture { location in
            let row = min(3, max(0, Int(location.y / (22 * s))))
            onLegendTap(stages[row])
        }
        .offset(x: 16 * s, y: 149.6 * s)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Sleep stage legend")
        .accessibilityValue("Awake 14 minutes, REM 63 minutes, Core 333 minutes, Deep 36 minutes. \(semantic())")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: onAdjust(true)
            case .decrement: onAdjust(false)
            @unknown default: break
            }
        }
        .accessibilityAction(named: Text("Inspect Awake")) { onLegendTap(.awake) }
        .accessibilityAction(named: Text("Inspect REM")) { onLegendTap(.rem) }
        .accessibilityAction(named: Text("Inspect Core")) { onLegendTap(.core) }
        .accessibilityAction(named: Text("Inspect Deep")) { onLegendTap(.deep) }
    }

    // MARK: Hypnogram

    private var chart: some View {
        let staticOpacity = (1 - 0.35 * presence) * (1 - 0.86 * front(At.chart))
        let dayFade = 1 - front(At.chart)
        let bars = NightSleepScoreChartGeometry.bars
        let index = visibleIndex
        let cursorX = sample.frame.cursorProgress * Chart.width
        return ZStack(alignment: .topLeading) {
            Canvas { c, size in
                let k = s
                c.drawLayer { l in
                    l.opacity = staticOpacity
                    for y in Chart.grid {
                        var p = Path(); p.move(to: CGPoint(x: 0, y: y * k)); p.addLine(to: CGPoint(x: Chart.width * k, y: y * k))
                        l.stroke(p, with: .color(Chart.gridColor), style: StrokeStyle(lineWidth: 0.9 * k, dash: Chart.gridDash.map { $0 * k }))
                    }
                    l.fill(Path(roundedRect: CGRect(x: 0, y: (Chart.level(.core) - Chart.barHeight / 2) * k, width: Chart.width * k, height: Chart.barHeight * k),
                                cornerRadius: Chart.barRadius * k, style: .continuous), with: .color(Chart.coreTrack))
                    l.stroke(NightSleepScoreChartGeometry.connector.applying(CGAffineTransform(scaleX: k, y: k)),
                             with: .color(Chart.connectorColor), lineWidth: Chart.connectorWidth * k)
                    for bar in bars {
                        let rect = CGRect(x: bar.x * k, y: bar.y * k, width: bar.width * k, height: Chart.barHeight * k)
                        let shape = Path(roundedRect: rect, cornerRadius: min(Chart.barRadius, bar.width / 2) * k, style: .continuous)
                        if bar.stage == .awake {
                            let color = Theme.Day.stages.color(.awake)
                            l.fill(shape, with: .linearGradient(Gradient(colors: [color, color.opacity(0.35)]),
                                                                startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.maxY)))
                        } else {
                            l.fill(shape, with: .color(Theme.Day.stages.color(bar.stage)))
                        }
                    }
                }
                if let index, presence > 0 {
                    let bar = bars[index]
                    let rect = CGRect(x: bar.x * k, y: bar.y * k, width: bar.width * k, height: Chart.barHeight * k)
                    c.drawLayer { l in
                        l.opacity = dayFade
                        l.fill(Path(roundedRect: rect, cornerRadius: Chart.barRadius * k, style: .continuous), with: .color(Theme.Day.stages.color(bar.stage)))
                    }
                }
                c.drawLayer { l in
                    l.opacity = 0.12 * presence * dayFade
                    l.fill(Path(roundedRect: CGRect(x: cursorX * k, y: 1 * k, width: 7 * k, height: 80 * k), cornerRadius: 4 * k, style: .continuous),
                           with: .color(Theme.Day.accent.color))
                }
                c.drawLayer { l in
                    l.opacity = 0.9 * presence * dayFade
                    l.fill(Path(roundedRect: CGRect(x: cursorX * k, y: 1 * k, width: 1.5 * k, height: 80 * k), cornerRadius: 1 * k, style: .continuous),
                           with: .color(Theme.Day.ink.color))
                }
            }
            .frame(width: Chart.width * s, height: Chart.height * s)
            .clipped()
            .offset(x: Chart.left * s, y: Chart.top * s)
            .allowsHitTesting(false)
            .accessibilityHidden(true)

            NightSleepScoreChartTouch(onBegan: onChartBegan, onMoved: onChartMoved, onEnded: onChartEnded, onCancelled: onChartCancelled)
                .frame(width: (Chart.width + 2 * Chart.touchInset) * s, height: (Chart.height + 16) * s)
                .offset(x: (Chart.left - Chart.touchInset) * s, y: (Chart.top - 9) * s)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Sleep stage timeline, 23:18 to 06:44")
                .accessibilityValue(semantic())
                .accessibilityAdjustableAction { direction in
                    switch direction {
                    case .increment: onAdjust(true)
                    case .decrement: onAdjust(false)
                    @unknown default: break
                    }
                }
                .accessibilityAction(named: Text("Inspect Awake")) { onLegendTap(.awake) }
                .accessibilityAction(named: Text("Inspect REM")) { onLegendTap(.rem) }
                .accessibilityAction(named: Text("Inspect Core")) { onLegendTap(.core) }
                .accessibilityAction(named: Text("Inspect Deep")) { onLegendTap(.deep) }
        }
    }

    // MARK: Readout

    private var readout: some View {
        let swap = M.readoutSwap
        let summary = M.smootherStep((swap - presence) / swap)
        let detail = M.smootherStep((presence - (1 - swap)) / swap)
        let index = visibleIndex
        let stage = index.map { NightSleepScoreData.segments[$0].stage }
        return ZStack(alignment: .topLeading) {
            Text("You only had a few interruptions last night which helped you get a high sleep score.")
                .font(.system(size: 15 * s)).lineSpacing(0)
                .foregroundStyle(tint(Theme.Day.ink, Theme.Night.ink, At.readout))
                .frame(width: 338 * s, height: 52 * s, alignment: .topLeading)
                .opacity(summary)
                .allowsHitTesting(false)
            if let stage {
                VStack(alignment: .leading, spacing: 0) {
                    Text(manual ? "\(stage.title) · \(NightSleepScoreData.clockTime(at: Double(selectedMinute)))" : "Night replay · \(stage.title)")
                        .font(.system(size: 15 * s, weight: .semibold))
                        .foregroundStyle(tint(Theme.Day.ink, Theme.Night.ink, At.readout))
                        .frame(height: 20 * s, alignment: .leading)
                    Text("23:18–06:44 overnight window").font(.system(size: 13 * s))
                        .foregroundStyle(tint(Theme.Day.secondary, Theme.Night.secondary, At.readoutSub))
                        .frame(height: 17 * s, alignment: .leading)
                }
                .frame(width: 338 * s, height: 52 * s, alignment: .topLeading)
                .opacity(detail)
                .allowsHitTesting(false)
                if manual {
                    Button(action: onDone) {
                        Text("Done").font(.system(size: 14 * s, weight: .semibold))
                            .foregroundStyle(tint(Theme.Day.accent, Theme.Night.accent, At.readout))
                            .frame(width: 44 * s, height: 44 * s, alignment: .trailing)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Done")
                    .opacity(detail)
                    .offset(x: (338 - 44) * s)
                }
            }
        }
        .frame(width: 338 * s, height: 52 * s, alignment: .topLeading)
        .offset(x: 16 * s, y: 262 * s)
    }
}
