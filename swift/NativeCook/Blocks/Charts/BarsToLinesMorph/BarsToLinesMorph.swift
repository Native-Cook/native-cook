import CoreText
import SwiftUI

private enum Palette {
    static let background = Color(hex: 0x0f0f10)
    static let card = Color(hex: 0x181819)
    static let cardEdge = Color(hex: 0x242426)
    static let selected = Color.rgba(255, 255, 255, 0.04)
    static let selectedEdge = Color(hex: 0x3a3a3c)
    static let ink = Color.white
    static let soft = Color(hex: 0xa1a1a6)
    static let mute = Color(hex: 0x6b6b70)
    static let grid = Color(hex: 0x2c2c30)
    static let red = Color(hex: 0xe5384b)
    static let redInk = Color(hex: 0xff4d5e)
}

private enum Metrics {
    static let margin = 16.0
    static let gap = 8.0
    static let tileHeight = 76.0
    static let tileRadius = 16.0
    static let axis = 32.0
    static let hourLabels = 30.0
    static let look = M.BarsToLinesMorphLook(barMax: 8, barShare: 0.6, barRadius: 2, dot: 3, gap: 1)
    static let lineWidth = 1.75
    static let labelSize = 11.0
    static let peakRise = 8.0
    static let lowDrop = 16.0
    static let tickHeights = [0.1, 0.5, 0.9]
    static let selectMove = 0.2
    static let cubicOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 1.0 / 3, y: 1), endControlPoint: UnitPoint(x: 2.0 / 3, y: 1))
    static let loop = 9.0
    static let switchAt = 1.0
    static let returnAt = 5.2
    static let userHold = 3.0
    static let headerIn = 0.2
    static let titleOut = 0.12
    static let axisOut = 0.15
    static let axisDelay = 0.3
    static let chartHeader = 42.0
    static let chartMargin = 16.0
    static let canvasTail = 20.0
    static let hoursTop = 8.0
    static let hourWidth = 60.0
    static let titleSize = 30.0
    static let titleHeight = 36.0
    static let subtitleSize = 13.0
    static let subtitleHeight = 18.0
    static let subtitleMargin = 2.0
    static let tilesMargin = 20.0
    static let top = 12.0
    static let tilePad = 12.0
    static let iconSize = 15.0
    static let iconGap = 6.0
    static let warningSize = 14.0
    static let nameSize = 12.0
    static let nameHeight = 16.0
    static let valueSize = 22.0
    static let valueHeight = 27.0
    static let unitSize = 11.0
    static let timeSize = 10.0
    static let timeHeight = 13.0
    static let cardTitleSize = 16.0
    static let cardTitleHeight = 21.0
    static let captionSize = 12.0
    static let captionHeight = 16.0
    static let axisSize = 10.0
    static let axisHeight = 14.0
    static let hourTickWidth = 2.0
    static let hourTickHeight = 4.0
    static let hourTickRadius = 1.0
    static let footnoteMargin = 4.0
}

private typealias M = BarsToLinesMorphMotion

/// Six vital tiles and a chart that folds into dots before changing its readings and shape.
struct BarsToLinesMorph: View {
    var title = "Vitals"
    var subtitle = "Today · updated 9:48 AM"
    var vitals = BarsToLinesMorphVital.samples
    var initialVital = 0
    var hours = ["12 AM", "4 AM", "8 AM", "12 PM", "4 PM", "8 PM"]
    var footnote = "Sample data"
    var autoPlay = false
    var onSelect: ((BarsToLinesMorphVital) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var selected: Int? = nil
    @State private var fromIndex: Int? = nil
    @State private var previous: Int? = nil
    @State private var runStart: Date? = nil
    @State private var drawingActive = false
    @State private var frameX = Tween(0)
    @State private var frameY = Tween(0)
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var haptic = UISelectionFeedbackGenerator()

    private var current: Int { selected ?? initialVital }
    private var plots: [BarsToLinesMorphPlot] { vitals.map(BarsToLinesMorphPlot.init) }
    private var other: Int {
        let two = plots.firstIndex { $0.lines == 2 }
        return max(0, two != nil && two != initialVital ? two! : plots.firstIndex { $0.bars != plots[initialVital].bars } ?? 0)
    }

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width - Metrics.margin * 2
            let tileWidth = (width - Metrics.gap) / 2
            TimelineView(.animation(paused: !drawingActive)) { timeline in
                let p = drawingActive ? phase(at: timeline.date) : M.total
                VStack(alignment: .leading, spacing: 0) {
                    Text(title)
                        .font(.system(size: Metrics.titleSize, weight: .bold))
                        .tracking(-0.6)
                        .foregroundStyle(Palette.ink)
                        .frame(height: Metrics.titleHeight, alignment: .leading)
                    Text(subtitle)
                        .font(.system(size: Metrics.subtitleSize))
                        .foregroundStyle(Palette.mute)
                        .frame(height: Metrics.subtitleHeight, alignment: .leading)
                        .padding(.top, Metrics.subtitleMargin)
                    tiles(width: tileWidth, at: timeline.date)
                        .padding(.top, Metrics.tilesMargin)
                    card(phase: p)
                        .padding(.top, Metrics.margin)
                }
                .padding(.horizontal, Metrics.margin)
                .padding(.top, Metrics.top)
                .padding(.bottom, max(proxy.safeAreaInsets.bottom, Metrics.margin) - proxy.safeAreaInsets.bottom)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: runStart) {
            guard let start = runStart else { return }
            let clock = ContinuousClock()
            let visualEnd = reduceMotion ? M.fade * 2 : M.total
            try? await clock.sleep(until: clock.now + .seconds(max(0, visualEnd - Date.now.timeIntervalSince(start))), tolerance: .zero)
            guard !Task.isCancelled, runStart == start else { return }
            drawingActive = false
            try? await clock.sleep(until: clock.now + .seconds(max(0, M.total - Date.now.timeIntervalSince(start))), tolerance: .zero)
            guard !Task.isCancelled, runStart == start else { return }
            runStart = nil
            previous = nil
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private func tiles(width: Double, at date: Date) -> some View {
        let rows = (vitals.count + 1) / 2
        return ZStack(alignment: .topLeading) {
            ForEach(vitals.indices, id: \.self) { index in
                Button {
                    lastTouch = .now
                    touches += 1
                    select(index, byHand: true)
                } label: {
                    tile(vitals[index], selected: current == index)
                        .frame(width: width, height: Metrics.tileHeight)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(current == index ? .isSelected : [])
                .offset(x: Double(index % 2) * (width + Metrics.gap), y: Double(index / 2) * (Metrics.tileHeight + Metrics.gap))
            }
        }
        .frame(width: width * 2 + Metrics.gap, height: Double(rows) * Metrics.tileHeight + Double(max(0, rows - 1)) * Metrics.gap, alignment: .topLeading)
        .overlay(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous)
                .fill(Palette.selected)
                .overlay(RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous).strokeBorder(Palette.selectedEdge, lineWidth: 1))
                .frame(width: width, height: Metrics.tileHeight)
                .offset(x: frameX.value(at: date) * (width + Metrics.gap), y: frameY.value(at: date) * (Metrics.tileHeight + Metrics.gap))
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .onAppear {
            frameX = Tween(Double(current % 2))
            frameY = Tween(Double(current / 2))
        }
    }

    private func tile(_ vital: BarsToLinesMorphVital, selected: Bool) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: Metrics.iconGap) {
                Image(systemName: symbol(vital.icon))
                    .font(.system(size: Metrics.iconSize))
                    .foregroundStyle(vital.iconColor)
                    .frame(width: Metrics.iconSize, height: Metrics.iconSize)
                Text(vital.name)
                    .font(.system(size: Metrics.nameSize))
                    .foregroundStyle(selected ? Palette.ink : Palette.soft)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if vital.warning {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: Metrics.warningSize))
                        .foregroundStyle(Palette.redInk)
                }
            }
            .frame(height: Metrics.nameHeight)
            Spacer(minLength: 0)
            HStack(alignment: .firstTextBaseline, spacing: Metrics.iconGap) {
                Text("\(Text(vital.value).font(.system(size: Metrics.valueSize, weight: .semibold)).foregroundColor(vital.warning ? Palette.redInk : Palette.ink))\(Text(" " + vital.unit).font(.system(size: Metrics.unitSize)).foregroundColor(vital.warning ? Palette.redInk : Palette.mute))")
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(vital.time)
                    .font(.system(size: Metrics.timeSize))
                    .foregroundStyle(Palette.mute)
                    .fixedSize()
            }
            .frame(height: Metrics.valueHeight)
        }
        .padding(Metrics.tilePad)
        .background(RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous).fill(Palette.card))
        .contentShape(RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous))
    }

    private func card(phase p: Double) -> some View {
        let vital = vitals[current]
        let entered = reduceMotion || runStart == nil ? 1 : M.layoutFade(p / Metrics.headerIn)
        return VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text(vital.name)
                    .font(.system(size: Metrics.cardTitleSize, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                    .opacity(entered)
                    .frame(height: Metrics.cardTitleHeight, alignment: .leading)
                    .overlay(alignment: .leading) {
                        if let previous {
                            Text(vitals[previous].name)
                                .font(.system(size: Metrics.cardTitleSize, weight: .semibold))
                                .foregroundStyle(Palette.ink)
                                .opacity(reduceMotion ? 0 : 1 - M.layoutFade(p / Metrics.titleOut))
                        }
                    }
                Text(caption(vital))
                    .font(.system(size: Metrics.captionSize))
                    .foregroundStyle(Palette.mute)
                    .frame(height: Metrics.captionHeight, alignment: .leading)
                    .opacity(entered)
            }
            .frame(height: Metrics.chartHeader, alignment: .topLeading)
            GeometryReader { proxy in
                let w = max(0, proxy.size.width - Metrics.axis)
                let h = max(0, proxy.size.height - Metrics.hourLabels)
                chart(phase: p, width: w, height: h)
                    .frame(width: w + Metrics.axis, height: h + Metrics.canvasTail, alignment: .topLeading)
                    .allowsHitTesting(false)
                hourLabels(width: w)
                    .offset(y: h + Metrics.hoursTop)
            }
            .padding(.top, Metrics.chartMargin)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(vital.name + ", " + caption(vital))
            Text(footnote)
                .font(.system(size: Metrics.timeSize))
                .foregroundStyle(Palette.mute)
                .frame(height: Metrics.timeHeight, alignment: .leading)
                .padding(.top, Metrics.footnoteMargin)
        }
        // Fixed padding and a top-leading frame keep the canvas's local origin unchanged.
        .padding(Metrics.margin)
        .padding(1)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous).fill(Palette.card))
        .overlay(RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous).strokeBorder(Palette.cardEdge, lineWidth: 1))
    }

    private func chart(phase p: Double, width: Double, height: Double) -> some View {
        let list = plots
        let from = list[fromIndex ?? initialVital]
        let to = list[current]
        return Canvas { context, _ in
            let axisIn = reduceMotion || runStart == nil ? 1 : M.layoutFade((p - Metrics.axisDelay) / Metrics.headerIn)
            drawAxis(vitals[current], opacity: axisIn, height: height, context: context)
            if let previous {
                drawAxis(vitals[previous], opacity: reduceMotion ? 0 : 1 - M.layoutFade(p / Metrics.axisOut), height: height, context: context)
            }
            var drawing = context
            drawing.translateBy(x: Metrics.axis, y: 0)
            drawing.clip(to: Path(CGRect(x: 0, y: 0, width: width, height: height + Metrics.canvasTail)))
            for at in Metrics.tickHeights {
                let y = (height * (1 - at)).rounded() + 0.5
                var path = Path()
                path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: width, y: y))
                drawing.stroke(path, with: .color(Palette.grid), style: StrokeStyle(lineWidth: 1, dash: [2, 5]))
            }
            let scene = M.scene(p, from: from, to: to, width: width, height: height, look: Metrics.look, still: reduceMotion)
            let c1 = M.mixColor(from.colors[0], to.colors[0], scene.mix)
            let c2 = M.mixColor(from.colors[1], to.colors[1], scene.mix)
            let bars = M.limitClips(above: scene.above, below: scene.below, width: width, height: height, gap: Metrics.look.gap)
            let lines = M.limitClips(above: scene.above, below: scene.below, width: width, height: height, gap: 0)
            var chart = drawing
            chart.opacity = scene.opacity
            for (clip, color) in [(bars.plain, c1), (bars.red, Palette.red)] {
                var layer = chart
                layer.clip(to: clip)
                layer.fill(scene.bars, with: .color(color))
            }
            for (clip, first, second) in [(lines.plain, c1, c2), (lines.red, Palette.redInk, Palette.redInk)] {
                var layer = chart
                layer.clip(to: clip)
                let stroke = StrokeStyle(lineWidth: Metrics.lineWidth, lineCap: .round, lineJoin: .round)
                layer.stroke(scene.line1.trimmedPath(from: 0, to: scene.trim), with: .color(first), style: stroke)
                if scene.lineCount == 2 { layer.stroke(scene.line2.trimmedPath(from: 0, to: scene.trim), with: .color(second), style: stroke) }
                layer.fill(scene.dots2, with: .color(second))
                layer.fill(scene.dots1, with: .color(first))
            }
            for which in 0...1 {
                drawExtreme(which, phase: p, from: from, to: to, width: width, height: height, context: drawing)
            }
        }
    }

    private func drawAxis(_ vital: BarsToLinesMorphVital, opacity: Double, height: Double, context: GraphicsContext) {
        var layer = context
        layer.opacity = opacity
        for k in 0..<3 {
            let text = Text(BarsToLinesMorphPlot.number(vital.ticks[k])).font(.system(size: Metrics.axisSize)).monospacedDigit().foregroundStyle(Palette.mute)
            layer.draw(text, at: CGPoint(x: 0, y: height * (1 - Metrics.tickHeights[k]) - Metrics.axisHeight / 2), anchor: .topLeading)
        }
    }

    private func drawExtreme(_ which: Int, phase p: Double, from: BarsToLinesMorphPlot, to: BarsToLinesMorphPlot, width: Double, height: Double, context: GraphicsContext) {
        let leaving = reduceMotion ? p < M.fade : p < M.bEnd
        let index = leaving ? fromIndex ?? initialVital : current
        let plot = leaving ? from : to
        let hour = which == 0 ? plot.peak : plot.trough
        let readings = which == 0 ? (plot.bars ? plot.b : plot.a) : (plot.bars ? plot.a : plot.b)
        let text = plot.extremes(unit: vitals[index].chartUnit)[which]
        // Bridge the system font directly; looking it up by name can silently select Times.
        let font = UIFont.systemFont(ofSize: Metrics.labelSize, weight: .semibold) as CTFont
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
        let textWidth = CTLineGetTypographicBounds(line, nil, nil, nil)
        let x = min(width - textWidth, max(0, (Double(hour) + 0.5) * width / Double(M.hours) - textWidth / 2))
        let y = M.yOf(readings[hour], plot, height) + (which == 0 ? -Metrics.peakRise : Metrics.lowDrop)
        let shown: Double
        if reduceMotion { shown = leaving ? 1 - p / M.fade : M.smooth(p, M.fade, M.fade * 2) }
        else { shown = leaving ? 1 - M.smooth(p, 0, M.labelOut) : M.smooth(p, M.cEnd, M.cEnd + M.labelIn) }
        let red = which == 0 ? plot.peakAbove : plot.troughBelow
        let color = red ? Palette.redInk : plot.colors[which == 0 ? 0 : plot.lines == 2 ? 1 : 0]
        var layer = context
        layer.opacity = shown
        layer.draw(Text(text).font(.system(size: Metrics.labelSize, weight: .semibold)).foregroundStyle(color), at: CGPoint(x: x, y: y + CTFontGetDescent(font)), anchor: .bottomLeading)
    }

    private func hourLabels(width: Double) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(hours.indices, id: \.self) { k in
                VStack(spacing: Metrics.footnoteMargin) {
                    RoundedRectangle(cornerRadius: Metrics.hourTickRadius).fill(Palette.grid).frame(width: Metrics.hourTickWidth, height: Metrics.hourTickHeight)
                    Text(hours[k]).font(.system(size: Metrics.timeSize)).foregroundStyle(Palette.mute).frame(height: Metrics.timeHeight)
                }
                .frame(width: Metrics.hourWidth, alignment: .top)
                .offset(x: Metrics.axis + (Double(k * 4) + 0.5) * width / Double(M.hours) - Metrics.hourWidth / 2)
            }
        }
        .frame(width: width + Metrics.axis, height: Metrics.hourLabels, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func phase(at date: Date) -> Double { runStart.map { min(M.total, max(0, date.timeIntervalSince($0))) } ?? M.total }

    /// Touch and autoplay use the same interrupted-switch rule as the source.
    private func select(_ index: Int, byHand: Bool) {
        guard index != current else { return }
        let now = Date.now
        if phase(at: now) >= M.bEnd { fromIndex = current }
        previous = current
        selected = index
        runStart = now
        drawingActive = true
        frameX.retarget(Double(index % 2), at: now, duration: reduceMotion ? 0 : Metrics.selectMove, curve: Metrics.cubicOut)
        frameY.retarget(Double(index / 2), at: now, duration: reduceMotion ? 0 : Metrics.selectMove, curve: Metrics.cubicOut)
        if byHand { haptic.selectionChanged() }
        onSelect?(vitals[index])
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(until: clock.now + .seconds(max(0, Metrics.userHold - Date.now.timeIntervalSince(lastTouch))), tolerance: .zero)
            guard !Task.isCancelled else { return }
            if current != initialVital {
                select(initialVital, byHand: false)
                try? await clock.sleep(until: clock.now + .seconds(M.total), tolerance: .zero)
            }
        }
        var origin = clock.now
        while !Task.isCancelled {
            try? await clock.sleep(until: origin + .seconds(Metrics.switchAt), tolerance: .zero)
            guard !Task.isCancelled else { return }
            select(other, byHand: false)
            try? await clock.sleep(until: origin + .seconds(Metrics.returnAt), tolerance: .zero)
            guard !Task.isCancelled else { return }
            select(initialVital, byHand: false)
            origin += .seconds(Metrics.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private func caption(_ vital: BarsToLinesMorphVital) -> String {
        switch vital.chart {
        case .bars: "Hourly low to high"
        case let .lines(_, second): second == nil ? "Hourly average" : "Systolic and diastolic, hourly"
        }
    }

    private func symbol(_ name: String) -> String {
        switch name {
        case "heart-pulse": "waveform.path.ecg"
        case "water-percent": "drop.halffull"
        case "thermometer": "thermometer.medium"
        case "gauge": "gauge.with.dots.needle.50percent"
        case "water": "drop.fill"
        case "lungs": "lungs.fill"
        default: "heart.fill"
        }
    }
}
