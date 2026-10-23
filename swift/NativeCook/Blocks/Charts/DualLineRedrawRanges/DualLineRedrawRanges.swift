import CoreText
import SwiftUI

let DualLineRedrawRangesLoop = 9.0

private enum S {
    static let background = Color(hex: 0x101010), ink = Color(hex: 0xf2f2f2), mute = Color(hex: 0x6b6b70), grid = Color(hex: 0x2a2a2e), track = Color(hex: 0x232326), thumb = Color(hex: 0xf2f2f2), thumbInk = Color(hex: 0x111111), tile = Color(hex: 0x18181b)
    static let margin = 20.0, axis = 40.0, rightPad = 10.0, ticksHeight = 28.0, gridSteps = 5, lineWidth = 2.0, head = 3.5, headGlow = 6.0, glowBlur = 5.0, segmented = 34.0, labelSize = 12.0, labelRise = 10.0, labelDrop = 20.0
    static let wipe = 0.35, draw = 0.9, stagger = 0.12, labelIn = 0.2, total = wipe + stagger + draw + labelIn, fade = 0.25, thumbSlide = 0.2, swipe = 48.0
    static let loop = DualLineRedrawRangesLoop, switches = [0.9, 3.9, 6.9], userHold = 3.0
    static let captionIn = 0.2, ticksIn = 0.22, ticksOutStill = 0.12, valueIn = 0.24, valueOut = 0.18, valueTravel = 25.0
    static let titleSize = 22.0, titleLine = 28.0, titleSpacing = -0.3, captionSize = 14.0, captionLine = 19.0, captionTop = 4.0, infoSize = 14.0, infoGap = 6.0, infoMark = 9.0
    static let segmentedTop = 24.0, thumbInset = 3.0, segmentSize = 13.0, legendTop = 20.0, chipGap = 8.0, chipHeight = 28.0, chipPad = 12.0, sampleWidth = 14.0, sampleHeight = 2.0, chipSize = 12.0
    static let chartTop = 24.0, chartBottom = 8.0, axisSize = 10.0, axisLine = 14.0, tickSize = 10.0, tickLine = 13.0, tickTop = 10.0, tickWidth = 60.0
    static let statsTop = 12.0, tileGap = 12.0, tilePad = 16.0, tileRadius = 20.0, tileLabel = 12.0, tileLabelLine = 16.0, valueTop = 6.0, valueSize = 26.0, valueLine = 32.0
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double { let u = clamp((t - from) / (to - from)); return u * u * (3 - 2 * u) }
    static func quadratic(_ x: Double) -> Double { let u = clamp(x); return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2 }
    static func out(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 3) }
    static func color(_ hex: String) -> Color { Color(hex: UInt32(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0) }
    static func average(_ values: [Double]) -> String { "\(Int(floor(values.reduce(0, +) / Double(values.count) + 0.5))) %" }
}

private struct DualLineRedrawRangesWindow {
    var new: Bool, from: Double, to: Double, opacity: Double
    init(_ line: Int, _ phase: Double, _ end: Double, _ still: Bool) {
        if still {
            if phase < S.fade { new = false; from = 0; to = end; opacity = 1 - phase / S.fade }
            else { new = true; from = 0; to = 1; opacity = S.clamp((phase - S.fade) / S.fade) }
        } else if phase < S.wipe {
            new = false; from = end * pow(phase / S.wipe, 2); to = end; opacity = 1
        } else {
            new = true; from = 0; to = S.smooth(phase, S.wipe + Double(line) * S.stagger, S.wipe + Double(line) * S.stagger + S.draw); opacity = 1
        }
    }
}

private struct DualLineRedrawRangesGhost { let index: Int; let at: Date }

struct DualLineRedrawRanges: View {
    var title = "Average % move of high / low"
    var ranges = DualLineRedrawRangesLineRange.samples
    var initialRange = 1
    var highLabel = "High"
    var lowLabel = "Low"
    var highColor = "#e9c46a"
    var lowColor = "#8b7cf6"
    var autoPlay = false
    var onRangeChange: ((DualLineRedrawRangesLineRange) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var still
    @State private var fromIndex: Int? = nil
    @State private var toIndex: Int? = nil
    @State private var ghosts: [DualLineRedrawRangesGhost] = []
    @State private var textAt = Date.now
    @State private var ends = [1.0, 1.0]
    @State private var switchedAt = Date.distantPast
    @State private var thumb = Tween(0)
    @State private var moving = false
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var dragging = false
    @State private var swipeFailed = false
    @State private var haptic = UISelectionFeedbackGenerator()

    private var index: Int { toIndex ?? initialRange }
    private var previous: Int { fromIndex ?? initialRange }
    private var labels: [String] { [highLabel, lowLabel] }
    private var colors: [Color] { [S.color(highColor), S.color(lowColor)] }

    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(paused: !moving)) { timeline in
                let date = moving ? timeline.date : Date.now
                let phase = min(S.total, max(0, date.timeIntervalSince(switchedAt)))
                let textPhase = max(0, date.timeIntervalSince(textAt))
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).font(.system(size: S.titleSize, weight: .semibold)).tracking(S.titleSpacing).foregroundStyle(S.ink)
                        .frame(minHeight: S.titleLine, alignment: .leading)
                    HStack(spacing: S.infoGap) {
                        Text(ranges[index].caption).font(.system(size: S.captionSize)).foregroundStyle(S.mute)
                            .opacity(still ? 1 : S.quadratic(textPhase / S.captionIn))
                        Text("i").font(.system(size: S.infoMark, weight: .bold)).foregroundStyle(S.mute)
                            .frame(width: S.infoSize, height: S.infoSize).overlay(Circle().stroke(S.mute, lineWidth: 1))
                    }.frame(height: S.captionLine).padding(.top, S.captionTop)
                    segmented(date)
                    legend
                    Canvas { context, size in drawChart(context, size, phase, date) }
                        .contentShape(Rectangle()).accessibilityLabel("\(title), \(ranges[index].caption)")
                        .gesture(DragGesture(minimumDistance: 16).onChanged { value in
                            if !dragging {
                                if abs(value.translation.height) > 24 { swipeFailed = true }
                                if !swipeFailed && abs(value.translation.width) > 16 { dragging = true; touch() }
                            }
                        }.onEnded { value in
                            if dragging && abs(value.translation.width) >= S.swipe { select(index + (value.translation.width < 0 ? 1 : -1), hand: true) }
                            if dragging { dragging = false; touch() }
                            swipeFailed = false
                        }).padding(.top, S.chartTop).padding(.bottom, S.chartBottom)
                    stats(date)
                }.padding(.horizontal, S.margin).padding(.top, 16)
                    .padding(.bottom, max(0, S.margin - proxy.safeAreaInsets.bottom))
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }.background(S.background.ignoresSafeArea()).preferredColorScheme(.dark)
            .onAppear { thumb = Tween(Double(initialRange)); textAt = .now; moving = !still; haptic.prepare() }
            .task(id: textAt) {
                guard moving else { return }
                let duration = switchedAt == .distantPast ? S.wipe + S.valueIn : (still ? 2 * S.fade : S.total)
                do { try await wait(until: textAt.addingTimeInterval(duration)); moving = false } catch {}
            }
            .task(id: touches) { await autoplay() }
            .onChange(of: autoPlay) { _, _ in touches += 1 }
    }

    private func segmented(_ date: Date) -> some View {
        GeometryReader { segment in
            let width = segment.size.width / Double(ranges.count)
            ZStack(alignment: .topLeading) {
                Capsule().fill(S.track)
                Capsule().fill(S.thumb).frame(width: width - 6, height: S.segmented - 6)
                    .offset(x: S.thumbInset + thumb.value(at: date) * width, y: S.thumbInset)
                HStack(spacing: 0) {
                    ForEach(ranges.indices, id: \.self) { i in
                        Button { touch(); select(i, hand: true) } label: {
                            Text(ranges[i].name).font(.system(size: S.segmentSize, weight: i == index ? .semibold : .medium))
                                .foregroundStyle(i == index ? S.thumbInk : S.mute).frame(maxWidth: .infinity, maxHeight: .infinity)
                        }.buttonStyle(.plain).accessibilityAddTraits(i == index ? [.isSelected] : [])
                    }
                }
            }.frame(width: segment.size.width, height: S.segmented, alignment: .topLeading)
        }.frame(height: S.segmented).padding(.top, S.segmentedTop)
    }
    private var legend: some View {
        HStack(spacing: S.chipGap) {
            ForEach(0..<2, id: \.self) { line in
                HStack(spacing: S.chipGap) {
                    Capsule().fill(colors[line]).frame(width: S.sampleWidth, height: S.sampleHeight)
                    Text(labels[line]).font(.system(size: S.chipSize, weight: .medium)).foregroundStyle(S.ink)
                }.padding(.horizontal, S.chipPad).frame(height: S.chipHeight).background(S.tile, in: Capsule())
            }
        }.padding(.top, S.legendTop)
    }
    private func stats(_ date: Date) -> some View {
        HStack(spacing: S.tileGap) {
            ForEach(0..<2, id: \.self) { line in
                VStack(alignment: .leading, spacing: 0) {
                    Text("\(labels[line]) · average").font(.system(size: S.tileLabel)).foregroundStyle(S.mute).frame(height: S.tileLabelLine)
                    Canvas { context, size in drawAverage(context, size, date, line) }
                        .frame(height: S.valueLine).clipped().padding(.top, S.valueTop)
                        .accessibilityLabel(S.average(line == 0 ? ranges[index].high : ranges[index].low))
                }.padding(S.tilePad).frame(maxWidth: .infinity, alignment: .leading)
                    .background(S.tile, in: RoundedRectangle(cornerRadius: S.tileRadius, style: .continuous))
            }
        }.padding(.top, S.statsTop)
    }

    private func touch() { touches += 1; lastTouch = .now }
    private func select(_ next: Int, hand: Bool) {
        guard ranges.indices.contains(next), next != index else { return }
        let now = Date.now, phase = min(S.total, max(0, now.timeIntervalSince(switchedAt)))
        let windows = (0..<2).map { DualLineRedrawRangesWindow($0, phase, ends[$0], still) }
        fromIndex = windows[0].new ? index : previous
        ghosts = ghosts.filter { now.timeIntervalSince($0.at) < S.wipe } + [DualLineRedrawRangesGhost(index: index, at: now)]
        ends = windows.map(\.to); toIndex = next; switchedAt = now; textAt = now
        thumb.retarget(Double(next), at: now, duration: still ? 0 : S.thumbSlide, curve: .bezier(startControlPoint: .init(x: 1.0 / 3, y: 1), endControlPoint: .init(x: 2.0 / 3, y: 1)))
        moving = true
        if hand { haptic.selectionChanged(); haptic.prepare() }
        onRangeChange?(ranges[next])
    }
    private func wait(until date: Date) async throws {
        while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }
        try Task.checkCancellation()
    }
    private func autoplay() async {
        guard autoPlay, !dragging else { return }
        do {
            if touches > 0 { try await wait(until: lastTouch.addingTimeInterval(S.userHold)) }
            let reset = index != initialRange
            if reset { select(initialRange, hand: false) }
            var origin = Date.now.addingTimeInterval(reset ? S.total : 0)
            let order = (1..<ranges.count).map { (initialRange + $0) % ranges.count } + [initialRange]
            while true {
                for k in 0..<min(S.switches.count, order.count) { try await wait(until: origin.addingTimeInterval(S.switches[k])); select(order[k], hand: false) }
                origin = origin.addingTimeInterval(S.loop); try await wait(until: origin)
            }
        } catch {}
    }
    private func drawChart(_ context: GraphicsContext, _ size: CGSize, _ phase: Double, _ date: Date) {
        let textPhase = max(0, date.timeIntervalSince(textAt))
        let height = max(1, size.height - S.ticksHeight), width = size.width - S.axis - S.rightPad
        var graph = context; graph.translateBy(x: S.axis, y: 0)
        graph.clip(to: Path(CGRect(x: 0, y: 0, width: size.width - S.axis, height: height)))
        var grid = Path()
        for k in 0...S.gridSteps {
            let y = height * Double(k) / Double(S.gridSteps)
            drawText(context, "\(100 - k * 20) %", 0, y - 7, S.axisSize, S.axisLine, S.mute, .regular)
            let gy = floor(y + 0.5) + 0.5; grid.move(to: CGPoint(x: 0, y: gy)); grid.addLine(to: CGPoint(x: size.width - S.axis, y: gy))
        }
        graph.stroke(grid, with: .color(S.grid), lineWidth: 1)
        for line in 0..<2 {
            let win = DualLineRedrawRangesWindow(line, phase, ends[line], still), range = ranges[win.new ? index : previous]
            let values = line == 0 ? range.high : range.low, tip = DualLineRedrawRangesPointAt(values, win.to, width, height)
            graph.stroke(DualLineRedrawRangesLinePart(values, win.from, win.to, width, height), with: .color(colors[line].opacity(win.opacity)), style: StrokeStyle(lineWidth: S.lineWidth, lineCap: .round, lineJoin: .round))
            let tipAlpha = win.opacity * (win.new ? (win.to <= 0 ? 0 : 1) : (still ? 1 : 1 - S.smooth(phase, S.wipe * 0.75, S.wipe)))
            graph.drawLayer { glow in
                glow.addFilter(.blur(radius: S.glowBlur)); glow.fill(Path(ellipseIn: CGRect(x: tip.x - S.headGlow, y: tip.y - S.headGlow, width: S.headGlow * 2, height: S.headGlow * 2)), with: .color(colors[line].opacity(tipAlpha * 0.45)))
            }
            graph.fill(Path(ellipseIn: CGRect(x: tip.x - S.head, y: tip.y - S.head, width: S.head * 2, height: S.head * 2)), with: .color(colors[line].opacity(tipAlpha)))
            let drawn = S.wipe + Double(line) * S.stagger + S.draw
            let alpha = still ? win.opacity : (win.new ? S.smooth(phase, drawn, drawn + S.labelIn) : 1 - S.smooth(phase, 0, S.wipe * 0.5))
            let text = "\(Int(floor(values.last! + 0.5))) %", font = UIFont.systemFont(ofSize: S.labelSize, weight: .semibold)
            let textWidth = (text as NSString).size(withAttributes: [.font: font]).width
            graph.draw(Text(text).font(Font(font)).foregroundStyle(colors[line].opacity(alpha)), at: CGPoint(x: width - textWidth - 6, y: tip.y + (line == 0 ? -S.labelRise : S.labelDrop) + CTFontGetDescent(font as CTFont)), anchor: .bottomLeading)
        }
        func ticks(_ range: DualLineRedrawRangesLineRange, _ alpha: Double) {
            for (k, label) in range.ticks.enumerated() {
                let x = S.axis + width * Double(k) / Double(range.ticks.count - 1)
                let anchor = k == 0 ? UnitPoint.topLeading : (k == range.ticks.count - 1 ? .topTrailing : .top)
                context.draw(Text(label).font(.system(size: S.tickSize)).foregroundStyle(S.mute.opacity(alpha)), at: CGPoint(x: x, y: height + S.tickTop), anchor: anchor)
            }
        }
        if !still { for ghost in ghosts { ticks(ranges[ghost.index], 1 - S.quadratic(date.timeIntervalSince(ghost.at) / S.wipe)) } }
        ticks(ranges[index], still ? 1 : S.quadratic((textPhase - S.wipe) / S.ticksIn))
    }
    private func drawAverage(_ context: GraphicsContext, _ size: CGSize, _ date: Date, _ line: Int) {
        let phase = max(0, date.timeIntervalSince(textAt))
        func label(_ range: DualLineRedrawRangesLineRange, _ alpha: Double, _ y: Double) {
            let values = line == 0 ? range.high : range.low
            drawText(context, S.average(values), 0, y, S.valueSize, S.valueLine, colors[line].opacity(alpha), .semibold, tabular: true)
        }
        if !still { for ghost in ghosts { let u = S.quadratic(date.timeIntervalSince(ghost.at) / S.valueOut); label(ranges[ghost.index], 1 - u, -S.valueTravel * u) } }
        let u = still ? 1 : S.quadratic((phase - S.wipe) / S.valueIn)
        label(ranges[index], u, S.valueTravel * (1 - u))
    }
    private func drawText(_ context: GraphicsContext, _ value: String, _ x: Double, _ top: Double, _ size: Double, _ height: Double, _ color: Color, _ weight: UIFont.Weight, tabular: Bool = false) {
        let font = tabular ? UIFont.monospacedDigitSystemFont(ofSize: size, weight: weight) : UIFont.systemFont(ofSize: size, weight: weight)
        let baseline = top + (height + CTFontGetAscent(font as CTFont) - CTFontGetDescent(font as CTFont)) / 2
        context.draw(Text(value).font(Font(font)).foregroundStyle(color), at: CGPoint(x: x, y: baseline + CTFontGetDescent(font as CTFont)), anchor: .bottomLeading)
    }
}
