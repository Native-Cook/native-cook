import CoreText
import SwiftUI

struct DotMatrixRevenueGroup { var label: String; var total: Double; var heights: [Int] }
struct DotMatrixRevenueRange { var name: String; var change: Double; var groups: [DotMatrixRevenueGroup]
    static let samples = [
        DotMatrixRevenueRange(name: "DAILY", change: 33, groups: [
            DotMatrixRevenueGroup(label: "MON", total: 412, heights: [1, 1, 1, 2, 3, 5, 4, 3, 4, 6, 5, 3, 2, 2, 3, 4, 3, 2, 2, 1, 1, 1, 1]),
            DotMatrixRevenueGroup(label: "TUE", total: 548, heights: [1, 1, 2, 3, 4, 7, 6, 4, 5, 8, 9, 6, 4, 3, 4, 6, 5, 4, 3, 2, 2, 1, 1]),
        ]),
        DotMatrixRevenueRange(name: "WEEKLY", change: 84, groups: [
            DotMatrixRevenueGroup(label: "WK 24", total: 2910, heights: [2, 3, 5, 4, 6, 3, 2, 1, 3, 4, 7, 5, 3, 2, 2, 4, 5, 3, 2, 1, 2, 3, 2]),
            DotMatrixRevenueGroup(label: "WK 25", total: 5336, heights: [3, 5, 8, 6, 4, 9, 11, 7, 5, 3, 4, 6, 8, 10, 7, 5, 3, 2, 4, 6, 5, 3, 2]),
        ]),
        DotMatrixRevenueRange(name: "MONTHLY", change: 326, groups: [
            DotMatrixRevenueGroup(label: "MAY", total: 3250, heights: [1, 2, 4, 8, 3, 3, 4, 6, 3, 1, 2, 2, 3, 3, 4, 3, 3, 1, 2, 2, 2]),
            DotMatrixRevenueGroup(label: "JUN", total: 12392, heights: [6, 7, 12, 10, 8, 9, 6, 5, 4, 5, 2, 1, 3, 2, 1, 5, 2, 1, 1, 4, 3, 5, 3, 4, 2]),
        ]),
    ]
}

private enum S {
    static let page = Color(hex: 0xeeeeee), card = Color(hex: 0xf7f7f7), line = Color(hex: 0xe4e4e4), ink = Color(hex: 0x111111), mute = Color(hex: 0xa0a0a0)
    static let dark = 74.0, light = 201.0, shades = 16
    static let margin = 16.0, maxCard = 420.0, radius = 32.0, pad = 22.0, header = 98.0
    static let rows = 14, chartTop = 18.0, dot = 0.46, edgeColumns = 4, edgeAlpha = 0.2
    static let columnStagger = 0.018, dotStagger = 0.04, leaveFor = 0.26, leaveRise = 14.0, dropFor = 0.42, drop = 40.0, overshoot = 1.9
    static let waveStagger = 0.025, shadeFor = 0.14, lift = 3.0, liftFor = 0.34, rowLag = 0.008, rippleAfter = 0.95, rippleStagger = 0.016, reducedFade = 0.16
    static let count = 0.7, underline = 0.26, colorFor = 0.2
    static let loop = 9.0, highlightAt = 0.8, highlightColumn = 9.0, weeklyAt = 2.6, weekly = 1, backAt = 5.2, userHold = 3.0, settle = 2.0
    static let headerTop = 24.0, titleSize = 13.0, titleLine = 18.0, titleSpacing = 0.5, headlineTop = 4.0, changeSize = 40.0, changeLine = 46.0, changeSpacing = -0.5, percentSize = 20.0, percentGap = 4.0
    static let tabGap = 14.0, tabSize = 12.0, tabLine = 18.0, tabSpacing = 0.3, underlineTop = 21.0, captions = 52.0, captionTop = 10.0, captionSize = 13.0, captionLine = 18.0
    static let cssEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.25, y: 0.1), endControlPoint: UnitPoint(x: 0.25, y: 1))
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func out(_ x: Double) -> Double { let u = 1 - clamp(x); return 1 - u * u * u }
    static func backOut(_ t: Double) -> Double { let x = t - 1; return 1 + (overshoot + 1) * x * x * x + overshoot * x * x }
    static func rounded(_ x: Double) -> Int { Int(floor(x + 0.5)) }
    static func mono(_ size: Double) -> UIFont { UIFont(name: "Menlo", size: size) ?? .monospacedSystemFont(ofSize: size, weight: .regular) }
    static func width(_ text: String, _ font: UIFont, _ spacing: Double = 0) -> Double {
        CTLineGetTypographicBounds(CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font as CTFont, .kern: spacing])), nil, nil, nil)
    }
    static func money(_ symbol: String, _ value: Double) -> String {
        let formatter = NumberFormatter(); formatter.locale = Locale(identifier: "en_US"); formatter.numberStyle = .decimal; formatter.maximumFractionDigits = 0
        return symbol + (formatter.string(from: NSNumber(value: rounded(value))) ?? "0")
    }
}

private struct DotMatrixRevenueGeometry {
    let width: Double, columns: Int, pitch: Double, chartHeight: Double, height: Double
    let tabs: [CGRect]
    init(_ width: Double, _ ranges: [DotMatrixRevenueRange]) {
        self.width = width
        columns = ranges.map { $0.groups[0].heights.count + 1 + $0.groups[1].heights.count }.max()!
        pitch = (width - S.pad) / Double(columns)
        chartHeight = Double(S.rows) * pitch + S.chartTop
        height = S.header + 1 + chartHeight + S.captions + 2
        let widths = ranges.map { S.width($0.name, S.mono(S.tabSize), S.tabSpacing) }
        var x = width - 2 - S.pad - widths.reduce(0, +) - Double(ranges.count - 1) * S.tabGap
        tabs = widths.map { w in defer { x += w + S.tabGap }; return CGRect(x: x, y: S.headerTop, width: w, height: S.tabLine) }
    }
}

private struct DotMatrixRevenueState {
    var range: Int, active = 1
    var from: [Int], to: [Int], flowAt: [Double], shadeFrom: [Double], shadeTo: [Double], shadeAt: [Double], liftAt: [Double]
    var tintFrom: [Double], tintTo: [Double], tintAt = -100.0
    var countAt = -100.0, changeFrom: Double, changeTo: Double, busyUntil = -100.0
    var lineFromX: Double, lineFromW: Double, lineAt = -100.0
    init(_ ranges: [DotMatrixRevenueRange], _ rest: Int, _ geo: DotMatrixRevenueGeometry) {
        range = rest
        let laid = Self.layout(ranges[rest], geo.columns)
        from = laid.heights; to = laid.heights
        flowAt = Array(repeating: -100, count: geo.columns); shadeAt = flowAt; liftAt = flowAt
        shadeFrom = laid.groups.map { $0 == 1 ? 1 : 0 }; shadeTo = shadeFrom
        tintFrom = ranges.indices.map { $0 == rest ? 1 : 0 } + [0, 1]; tintTo = tintFrom
        changeFrom = ranges[rest].change; changeTo = changeFrom
        lineFromX = geo.tabs[rest].minX; lineFromW = geo.tabs[rest].width
    }
    static func layout(_ range: DotMatrixRevenueRange, _ columns: Int) -> (heights: [Int], groups: [Int], start: [Int]) {
        var h = Array(repeating: 0, count: columns), g = Array(repeating: -1, count: columns)
        let start = [0, range.groups[0].heights.count + 1]
        for group in 0...1 { for (c, value) in range.groups[group].heights.enumerated() { h[start[group] + c] = value; g[start[group] + c] = group } }
        return (h, g, start)
    }
    func shade(_ c: Int, _ t: Double, _ reduced: Bool) -> Double { shadeFrom[c] + (shadeTo[c] - shadeFrom[c]) * S.clamp((t - shadeAt[c]) / (reduced ? S.reducedFade : S.shadeFor)) }
    func tint(_ c: Int, _ t: Double, _ reduced: Bool) -> Double { tintFrom[c] + (tintTo[c] - tintFrom[c]) * (reduced ? 1 : S.cssEase.value(at: S.clamp((t - tintAt) / S.colorFor))) }
    func change(_ t: Double, _ reduced: Bool) -> Double { changeFrom + (changeTo - changeFrom) * (reduced ? 1 : S.out((t - countAt) / S.count)) }
    func share(_ t: Double, _ reduced: Bool) -> Double { reduced ? 1 : Double(S.rounded(S.out((t - countAt) / S.count) * 200)) / 200 }
    func line(_ geo: DotMatrixRevenueGeometry, _ t: Double, _ reduced: Bool) -> (x: Double, w: Double) {
        let u = reduced ? 1 : S.out((t - lineAt) / S.underline), tab = geo.tabs[range]
        return (lineFromX + (tab.minX - lineFromX) * u, lineFromW + (tab.width - lineFromW) * u)
    }
    mutating func tintChanged(_ t: Double, _ reduced: Bool, _ rangeCount: Int) {
        tintFrom = tintFrom.indices.map { tint($0, t, reduced) }
        tintTo = (0..<rangeCount).map { $0 == range ? 1 : 0 } + [active == 0 ? 1 : 0, active == 1 ? 1 : 0]
        tintAt = t
    }
    mutating func highlight(_ column: Double, _ t: Double, _ ranges: [DotMatrixRevenueRange], _ geo: DotMatrixRevenueGeometry, _ reduced: Bool) -> Bool {
        let c = min(geo.columns - 1, max(0, S.rounded(column)))
        let groups = Self.layout(ranges[range], geo.columns).groups, group = groups[c]
        guard group >= 0, group != active else { return false }
        var last = t
        for k in 0..<geo.columns {
            shadeFrom[k] = shade(k, t, reduced); shadeTo[k] = groups[k] == group ? 1 : 0
            shadeAt[k] = t + (reduced ? 0 : Double(abs(k - c)) * S.waveStagger)
            if !reduced { liftAt[k] = shadeAt[k] }; last = max(last, shadeAt[k])
        }
        active = group; tintChanged(t, reduced, ranges.count)
        busyUntil = max(busyUntil, reduced ? t + S.reducedFade : last + S.liftFor + Double(S.rows) * S.rowLag)
        return true
    }
    mutating func switchRange(_ index: Int, _ t: Double, _ ranges: [DotMatrixRevenueRange], _ geo: DotMatrixRevenueGeometry, _ reduced: Bool) -> Bool {
        guard index != range else { return false }
        let oldLine = line(geo, t, reduced), oldChange = change(t, reduced), laid = Self.layout(ranges[index], geo.columns)
        from = to; to = laid.heights
        var last = t
        for c in 0..<geo.columns {
            flowAt[c] = t + (reduced ? 0 : Double(c) * S.columnStagger)
            shadeFrom[c] = shade(c, t, reduced); shadeTo[c] = laid.groups[c] == 1 ? 1 : 0; shadeAt[c] = flowAt[c]
            if !reduced { liftAt[c] = flowAt[c] + S.rippleAfter + Double(c) * (S.rippleStagger - S.columnStagger) }
            last = max(last, reduced ? flowAt[c] + S.reducedFade : liftAt[c] + S.liftFor + Double(S.rows) * S.rowLag)
        }
        range = index; active = 1; tintChanged(t, reduced, ranges.count)
        changeFrom = oldChange; changeTo = ranges[index].change; countAt = t
        lineFromX = oldLine.x; lineFromW = oldLine.w; lineAt = t; busyUntil = max(busyUntil, last)
        return true
    }
}

struct DotMatrixRevenue: View {
    var title = "REVENUE"
    var ranges = DotMatrixRevenueRange.samples
    var initialRange: Int? = nil
    var symbol = "$"
    var autoPlay = false
    var onRangeChange: ((Int, DotMatrixRevenueRange) -> Void)? = nil
    var onSelectGroup: ((Int, DotMatrixRevenueRange) -> Void)? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var state: DotMatrixRevenueState? = nil
    @State private var origin = Date.now
    @State private var moving = false
    @State private var end = -100.0
    @State private var dragging = false
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var haptic = UISelectionFeedbackGenerator()
    private var rest: Int { min(ranges.count - 1, max(0, initialRange ?? ranges.count - 1)) }
    private func clock(_ date: Date = .now) -> Double { date.timeIntervalSince(origin) }
    var body: some View {
        GeometryReader { proxy in
            let geo = DotMatrixRevenueGeometry(min(S.maxCard, proxy.size.width - S.margin * 2), ranges)
            let now = state ?? DotMatrixRevenueState(ranges, rest, geo)
            TimelineView(.animation(paused: !moving)) { timeline in
                let t = clock(moving ? timeline.date : .now)
                Canvas { context, _ in draw(now, geo, t, context) }
                    .accessibilityHidden(true)
                    .overlay(alignment: .topLeading) {
                        ZStack(alignment: .topLeading) {
                            Color.clear.frame(width: geo.width - 2, height: S.header).accessibilityElement().accessibilityLabel("\(title) \(ranges[now.range].change >= 0 ? "up" : "down") \(S.rounded(abs(ranges[now.range].change))) percent").allowsHitTesting(false)
                            ForEach(ranges.indices, id: \.self) { index in
                                Button { touched(); switchRange(index, true, geo) } label: { Color.clear.frame(width: geo.tabs[index].width + 8, height: S.tabLine + 24).contentShape(Rectangle()) }
                                    .buttonStyle(.plain).accessibilityLabel(ranges[index].name).accessibilityAddTraits(index == now.range ? .isSelected : [])
                                    .offset(x: geo.tabs[index].minX - 4 + 1, y: S.headerTop - 12 + 1)
                            }
                            Color.clear.frame(width: geo.width - 2, height: geo.chartHeight).contentShape(Rectangle())
                                .gesture(DragGesture(minimumDistance: 0).onChanged { value in dragging = true; touched(); highlight((value.location.x - S.pad) / geo.pitch - 0.5, true, geo) }.onEnded { _ in dragging = false; touched() })
                                .accessibilityElement().accessibilityLabel("Revenue chart").accessibilityValue("\(ranges[now.range].groups[now.active].label) highlighted")
                                .accessibilityAdjustableAction { direction in touched(); highlight(Double(DotMatrixRevenueState.layout(ranges[now.range], geo.columns).start[direction == .increment ? 1 : 0]), true, geo) }
                                .offset(x: 1, y: S.header + 2)
                        }.frame(width: geo.width, height: geo.height, alignment: .topLeading)
                    }
                    .frame(width: geo.width, height: geo.height, alignment: .topLeading)
                    .background(S.card).clipShape(RoundedRectangle(cornerRadius: S.radius, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: S.radius, style: .continuous).strokeBorder(S.line, lineWidth: 1).allowsHitTesting(false))
                    .position(x: proxy.size.width / 2, y: proxy.size.height / 2)
            }
            .onAppear { if state == nil { state = now }; haptic.prepare() }
            .task(id: LoopKey(on: autoPlay, touches: touches, dragging: dragging)) { await loop(geo) }
        }
        .background(S.page).ignoresSafeArea().preferredColorScheme(.light)
        .task(id: end) {
            guard moving else { return }
            await until(origin.addingTimeInterval(end)); guard !Task.isCancelled else { return }; moving = false
        }
    }
    private func touched() { lastTouch = .now; touches += 1 }
    private func highlight(_ column: Double, _ hand: Bool, _ geo: DotMatrixRevenueGeometry) {
        var next = state ?? DotMatrixRevenueState(ranges, rest, geo)
        guard next.highlight(column, clock(), ranges, geo, reduced) else { return }
        state = next; end = next.busyUntil; moving = true
        if hand { haptic.selectionChanged(); onSelectGroup?(next.active, ranges[next.range]) }
    }
    private func switchRange(_ index: Int, _ hand: Bool, _ geo: DotMatrixRevenueGeometry) {
        var next = state ?? DotMatrixRevenueState(ranges, rest, geo)
        guard next.switchRange(index, clock(), ranges, geo, reduced) else { return }
        state = next; end = next.busyUntil; moving = true
        if hand { haptic.selectionChanged(); onRangeChange?(index, ranges[index]) }
    }
    private func until(_ date: Date) async { let c = ContinuousClock(); try? await c.sleep(until: c.now + .seconds(max(0, date.timeIntervalSinceNow)), tolerance: .zero) }
    private func loop(_ geo: DotMatrixRevenueGeometry) async {
        guard autoPlay, !dragging else { return }
        if touches > 0 { await until(lastTouch.addingTimeInterval(S.userHold)); guard !Task.isCancelled else { return } }
        let now = state ?? DotMatrixRevenueState(ranges, rest, geo), atRest = now.range == rest && now.active == 1
        if now.range != rest { switchRange(rest, false, geo) }
        else if now.active != 1 { highlight(Double(DotMatrixRevenueState.layout(ranges[rest], geo.columns).start[1]), false, geo) }
        var start = Date.now.addingTimeInterval(atRest ? 0 : S.settle)
        while !Task.isCancelled {
            await until(start.addingTimeInterval(S.highlightAt)); guard !Task.isCancelled else { return }; highlight(S.highlightColumn, false, geo)
            await until(start.addingTimeInterval(S.weeklyAt)); guard !Task.isCancelled else { return }; switchRange(min(S.weekly, ranges.count - 1), false, geo)
            await until(start.addingTimeInterval(S.backAt)); guard !Task.isCancelled else { return }; switchRange(rest, false, geo)
            start = start.addingTimeInterval(S.loop); await until(start)
        }
    }
    private struct LoopKey: Equatable { let on: Bool; let touches: Int; let dragging: Bool }
    private func draw(_ g: DotMatrixRevenueState, _ geo: DotMatrixRevenueGeometry, _ t: Double, _ context: GraphicsContext) {
        var c = context; c.translateBy(x: 1, y: 1)
        func text(_ value: String, _ x: Double, _ top: Double, _ font: UIFont, _ height: Double, _ color: Color, _ spacing: Double = 0, _ tabular: Bool = false) {
            let ct = font as CTFont, baseline = top + (height + CTFontGetAscent(ct) - CTFontGetDescent(ct)) / 2
            var label = Text(value).font(Font(font)).tracking(spacing).foregroundColor(color)
            if tabular { label = label.monospacedDigit() }
            c.draw(label, at: CGPoint(x: x, y: baseline + CTFontGetDescent(ct)), anchor: .bottomLeading)
        }
        func tint(_ k: Int) -> Color { let v = (160 + (17 - 160) * g.tint(k, t, reduced)) / 255; return Color(.sRGB, red: v, green: v, blue: v) }
        text(title, S.pad, S.headerTop, S.mono(S.titleSize), S.titleLine, S.ink, S.titleSpacing)
        let change = S.rounded(g.change(t, reduced)), value = (change >= 0 ? "+" : "−") + String(abs(change))
        let font = UIFont.monospacedDigitSystemFont(ofSize: S.changeSize, weight: .light), headline = S.headerTop + S.titleLine + S.headlineTop
        text(value, S.pad, headline, font, S.changeLine, S.ink, S.changeSpacing, true)
        let percent = UIFont.systemFont(ofSize: S.percentSize, weight: .light)
        let baseline = headline + (S.changeLine + CTFontGetAscent(font as CTFont) - CTFontGetDescent(font as CTFont)) / 2
        text("%", S.pad + S.width(value, font, S.changeSpacing) + S.percentGap, baseline - CTFontGetAscent(percent as CTFont), percent, Double(CTFontGetAscent(percent as CTFont) + CTFontGetDescent(percent as CTFont)), S.ink)
        for i in ranges.indices { text(ranges[i].name, geo.tabs[i].minX, S.headerTop, S.mono(S.tabSize), S.tabLine, tint(i), S.tabSpacing) }
        let line = g.line(geo, t, reduced)
        c.fill(Path(CGRect(x: line.x, y: S.headerTop + S.underlineTop, width: line.w, height: 1)), with: .color(S.ink))
        c.fill(Path(CGRect(x: 0, y: S.header, width: geo.width - 2, height: 1)), with: .color(S.line))
        // Dropping dots belong to the chart's viewport, even while they sit above its first row.
        var dots = c
        dots.clip(to: Path(CGRect(x: 0, y: S.header + 1, width: geo.width - 2, height: geo.chartHeight)))
        let radius = geo.pitch * S.dot / 2, floorY = geo.chartHeight - geo.pitch / 2
        for column in 0..<geo.columns {
            let shade = S.rounded(g.shade(column, t, reduced) * Double(S.shades)), byte = Double(S.rounded(S.light + (S.dark - S.light) * Double(shade) / Double(S.shades)))
            let color = Color.rgba(byte, byte, byte, 1)
            let edge = column >= geo.columns - S.edgeColumns ? 1 - Double(column - (geo.columns - S.edgeColumns) + 1) / Double(S.edgeColumns) * (1 - S.edgeAlpha) : 1
            let since = t - g.flowAt[column]
            for row in 0..<max(g.from[column], g.to[column]) {
                var alpha = 1.0, dy = 0.0
                if row >= g.to[column] {
                    let u = S.clamp((since - Double(g.from[column] - 1 - row) * (reduced ? 0 : S.dotStagger)) / (reduced ? S.reducedFade : S.leaveFor)); alpha = 1 - u
                    if !reduced { dy = -S.leaveRise * (1 - (1 - u) * (1 - u)) }
                } else if row >= g.from[column] {
                    if reduced { alpha = S.clamp(since / S.reducedFade) }
                    else { let u = S.clamp((since - Double(row - g.from[column]) * S.dotStagger) / S.dropFor); alpha = S.clamp(u * 3); dy = -S.drop * (1 - S.backOut(u)) }
                }
                if alpha <= 0.01 { continue }
                let lift = (t - g.liftAt[column] - Double(row) * S.rowLag) / S.liftFor
                if lift > 0 && lift < 1 { dy -= S.lift * sin(.pi * lift) }
                let x = S.pad + Double(column) * geo.pitch + geo.pitch / 2, y = S.header + 1 + floorY - Double(row) * geo.pitch + dy
                dots.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)), with: .color(color.opacity(alpha * edge)))
            }
        }
        let laid = DotMatrixRevenueState.layout(ranges[g.range], geo.columns), share = g.share(t, reduced)
        for i in 0...1 { let group = ranges[g.range].groups[i]; text(group.label + " " + S.money(symbol, group.total * share), S.pad + Double(laid.start[i]) * geo.pitch + geo.pitch / 2 - radius, S.header + 1 + geo.chartHeight + S.captionTop, S.mono(S.captionSize), S.captionLine, tint(ranges.count + i)) }
    }
}
