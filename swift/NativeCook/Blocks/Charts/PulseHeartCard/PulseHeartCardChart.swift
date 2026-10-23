import SwiftUI

private typealias Colors = PulseHeartCardTheme.Colors
private typealias Layout = PulseHeartCardTheme.Layout
private typealias M = PulseHeartCardMotion

/// The range chart: 20 soft capsules, a dotted baseline, and a cursor that follows the scrub.
struct PulseHeartCardChart: View {
    let scale: Double
    let engine: PulseHeartCardEngine
    let generation: Int
    let selectedIndex: Int?
    let onTouch: (Double) -> Void
    let onEnd: () -> Void
    let onAdjust: (Int) -> Void

    var body: some View {
        let s = scale
        let plotWidth = Layout.chartWidth * s
        let plotHeight = Layout.chartPlotHeight * s
        ZStack(alignment: .topLeading) {
            // Static: the bars (the pin's are soft-edged, a 0.8 pt blur matches them), the dotted baseline, the end ticks.
            Canvas { context, _ in
                context.opacity = 0.78
                context.drawLayer { layer in
                    layer.addFilter(.blur(radius: Layout.chartBarBlur * s))
                    for bar in M.barBounds {
                        for (y0, y1) in bar.segments {
                            let rect = CGRect(x: (bar.x - Layout.chartLeft) * s, y: (y0 - Layout.chartOrigin) * s,
                                              width: bar.width * s, height: (y1 - y0) * s)
                            layer.fill(Path(roundedRect: rect, cornerRadius: bar.width / 2 * s), with: .color(Colors.chartPink))
                        }
                    }
                }
            }
            .frame(width: plotWidth, height: plotHeight)
            .offset(y: Layout.chartPlotTop * s)
            .allowsHitTesting(false)

            Canvas { context, _ in
                for index in 0..<33 {
                    let dot = CGRect(x: (2 + Double(index) * 10.35 + 0.7 - 0.7) * s, y: (60.7 - 0.7) * s, width: 1.4 * s, height: 1.4 * s)
                    context.fill(Path(ellipseIn: dot), with: .color(Colors.faint.opacity(0.55)))
                }
                for x in [2.0, 334.0] {
                    var tick = Path()
                    tick.move(to: CGPoint(x: x * s, y: 57 * s))
                    tick.addLine(to: CGPoint(x: x * s, y: plotHeight))
                    context.stroke(tick, with: .color(Colors.faint.opacity(0.7)), lineWidth: s)
                }
            }
            .frame(width: plotWidth, height: plotHeight)
            .offset(y: Layout.chartPlotTop * s)
            .allowsHitTesting(false)

            dynamicLayer(plotWidth: plotWidth)

            HStack {
                Text("12AM")
                Spacer(minLength: 0)
                Text("12AM")
            }
            .font(.system(size: Layout.chartLabelSize * s, weight: .regular))
            .foregroundStyle(Colors.faint)
            .frame(width: plotWidth, height: Layout.chartLabelLine * s)
            .offset(y: Layout.chartLabelTop * s)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .frame(width: plotWidth, height: Layout.chartHeight * s, alignment: .topLeading)
        .overlay { PulseHeartCardTouch(onTouch: onTouch, onEnd: onEnd) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Heart rate history")
        .accessibilityValue(selectedIndex.map { "\(M.samples[$0]) BPM at \(M.sampleTime($0))" } ?? "43 to 120 BPM. Latest reading 62 BPM")
        .accessibilityHint("Swipe up or down to select a reading")
        .accessibilityAdjustableAction { direction in
            onAdjust(direction == .increment ? 1 : -1)
        }
    }

    /// The title and the cursor read the selection and its fade from the engine's frame.
    private func dynamicLayer(plotWidth: Double) -> some View {
        let s = scale
        return TimelineView(PulseHeartCardSchedule(engine: engine, generation: generation)) { context in
            let frame = engine.frame(at: context.date.timeIntervalSinceReferenceDate)
            let latest = M.barBounds[M.barBounds.count - 1]
            let index = frame.selected ?? M.barBounds.count - 1
            let bar = M.barBounds[index]
            let centreX = (bar.x - Layout.chartLeft + bar.width / 2) * s
            let centreY = (bar.y0 - Layout.chartOrigin - 3) * s
            let latestX = (latest.x - Layout.chartLeft + latest.width / 2) * s
            let latestY = (latest.y0 - Layout.chartOrigin - 3) * s
            let opacity = frame.cursorOpacity
            ZStack(alignment: .topLeading) {
                let title = frame.selected.map { "\(M.samples[$0]) BPM · \(M.sampleTime($0))" } ?? "43–120 BPM"
                Text(title)
                    .font(.system(size: Layout.chartTitleSize * s, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Colors.ink)
                    .lineLimit(1)
                    .frame(height: Layout.chartTitleLine * s)
                    .padding(.leading, Layout.chartTitleInset * s)

                ZStack(alignment: .topLeading) {
                    if let selected = frame.selected {
                        ForEach(Array(M.barBounds[selected].segments.enumerated()), id: \.offset) { _, segment in
                            RoundedRectangle(cornerRadius: bar.width / 2 * s, style: .continuous)
                                .fill(Colors.heart)
                                .frame(width: bar.width * s, height: (segment.1 - segment.0) * s)
                                .offset(x: (bar.x - Layout.chartLeft) * s, y: (segment.0 - Layout.chartOrigin) * s)
                                .opacity(opacity)
                        }
                    }
                    Rectangle()
                        .fill(Colors.heart)
                        .frame(width: s, height: 58 * s)
                        .offset(x: centreX, y: 2 * s)
                        .opacity(opacity * 0.25)
                    ring.offset(x: centreX - 4.5 * s, y: centreY - 4.5 * s).opacity(opacity)
                    ring.offset(x: latestX - 4.5 * s, y: latestY - 4.5 * s).opacity(1 - opacity)
                }
                .frame(width: plotWidth, height: Layout.chartPlotHeight * s, alignment: .topLeading)
                .offset(y: Layout.chartPlotTop * s)
            }
            .frame(width: plotWidth, height: Layout.chartHeight * s, alignment: .topLeading)
        }
        .allowsHitTesting(false)
    }

    private var ring: some View {
        Circle()
            .strokeBorder(Colors.heart, lineWidth: 2.4 * scale)
            .background(Circle().fill(Color.white))
            .frame(width: 9 * scale, height: 9 * scale)
    }
}
