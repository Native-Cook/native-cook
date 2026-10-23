import CoreText
import SwiftUI

struct MoodStackColumnsMood: Equatable {
    var name: String
    /// Used in the caption under a selected column.
    var adjective: String
    var color: Color

    static let samples = [
        MoodStackColumnsMood(name: "Joy", adjective: "joyful", color: Color(hex: 0xffd04e)),
        MoodStackColumnsMood(name: "Calm", adjective: "calm", color: Color(hex: 0x4be29b)),
        MoodStackColumnsMood(name: "Low", adjective: "low", color: Color(hex: 0x7ba0ff)),
        MoodStackColumnsMood(name: "Stress", adjective: "stressed", color: Color(hex: 0xff5a4a)),
    ]
}

struct MoodStackColumnsColumn: Equatable {
    var name: String
    var checkIns: Int
    /// Percent per mood; a column is full height whatever the sum.
    var shares: [Double]

    static let samples = [
        MoodStackColumnsColumn(name: "Morning", checkIns: 5, shares: [80, 0, 0, 20]),
        MoodStackColumnsColumn(name: "Afternoon", checkIns: 7, shares: [28, 14, 14, 42]),
        MoodStackColumnsColumn(name: "Evening", checkIns: 6, shares: [0, 50, 33, 16]),
        MoodStackColumnsColumn(name: "Late Night", checkIns: 1, shares: [0, 100, 0, 0]),
    ]
}

enum MoodStackColumnsMotion {
    static let loop = 9.0
    fileprivate static let selectAt = [(0.8, 1), (2.2, 3)]
    fileprivate static let releaseAt = 3.6, sortAt = 4.2, unsortAt = 6.0, userHold = 3.0, settle = 1.0
}

private enum MoodStackColumnsStyle {
    static let background = Color(hex: 0x000000), ink = Color(hex: 0xffffff), soft = Color(hex: 0xc7c7cc), mute = Color(hex: 0x8e8e93), segmentInk = Color(hex: 0x111111), empty = Color(hex: 0x1c1c1e), toggle = Color(hex: 0x1c1c1e), thumb = Color(hex: 0x3a3a3c)
    static let margin = 20.0, gap = 6.0, columnRadius = 24.0, widen = 1.36, dim = 0.5, labelGrow = 1.25, pulse = 1.02, minChart = 260.0, maxChart = 420.0, toggleHeight = 30.0, toggleWidth = 70.0
    static let focusDamping = 18.0, focusStiffness = 190.0, focusMass = 1.0, springEnergy = 6e-9, pulseFor = 0.15, sortFor = 0.7, sortStagger = 0.04, toggleFor = 0.22, captionFor = 0.2
    static let headingSize = 34.0, headingHeight = 42.0, headingTracking = -0.6, headingInset = 12.0, chevronSize = 26.0, chevronGap = 6.0, chevronDrop = 4.0, datesGap = 6.0, datesSize = 12.0, datesHeight = 16.0, datesTracking = 0.6, toggleSize = 12.0, toggleLine = 16.0, toggleInset = 2.0, ruleGap = 8.0, ruleHeight = 1.0, ruleMark = 0.22, titleGap = 26.0, titleSize = 26.0, titleHeight = 32.0, titleTracking = -0.4, chartAllowance = 84.0, nameGap = 10.0, nameSize = 15.0, nameHeight = 20.0, checkSize = 13.0, checkHeight = 18.0, captionGap = 4.0, captionSlot = 22.0, captionSize = 13.0, captionHeight = 18.0, segmentSize = 16.0, segmentHeight = 20.0, minLabelHeight = 24.0, teaserHeight = 64.0, teaserOpacity = 0.55, teaserStops = [0.1, 0.9]
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func cubic(_ x: Double) -> Double { let t = clamp(x); return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }
    static func quad(_ x: Double) -> Double { let t = clamp(x); return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2 }
    static func outQuad(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 2) }
    static func outCubic(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 3) }
}
private typealias S = MoodStackColumnsStyle
private typealias M = MoodStackColumnsMotion

private struct MoodStackColumnsPiece {
    var mood: Int, share: Double, label: Double, byMood: Double, bySize: Double
}

private func MoodStackColumnsStack(_ column: MoodStackColumnsColumn) -> [MoodStackColumnsPiece] {
    let sum = column.shares.reduce(0, +), total = sum == 0 ? 1 : sum
    var pieces = column.shares.enumerated().compactMap { index, label in
        label / total > 0 ? MoodStackColumnsPiece(mood: index, share: label / total, label: label, byMood: 0, bySize: 0) : nil
    }
    var top = 0.0
    for i in pieces.indices { pieces[i].byMood = top; top += pieces[i].share }
    let ordered = pieces.indices.sorted { pieces[$0].share == pieces[$1].share ? pieces[$0].mood < pieces[$1].mood : pieces[$0].share < pieces[$1].share }
    top = 0
    for i in ordered { pieces[i].bySize = top; top += pieces[i].share }
    return pieces.sorted { $0.share == $1.share ? $0.mood < $1.mood : $0.share > $1.share }
}

/// Springs keep their velocity when interrupted; drawing samples the clock without publishing frames.
private struct MoodStackColumnsScalar {
    var from: Double, to: Double, at = Date.distantPast, duration = 0.0, curve = 0, velocity: Double? = nil
    init(_ value: Double) { from = value; to = value }
    func sample(_ date: Date) -> (value: Double, velocity: Double) {
        let t = max(0, date.timeIntervalSince(at))
        if let velocity {
            let z = S.focusDamping / (2 * S.focusMass), w = sqrt(S.focusStiffness / S.focusMass - z * z), a = from - to, b = (velocity + z * a) / w
            let delta = exp(-z * t) * (a * cos(w * t) + b * sin(w * t))
            let v = exp(-z * t) * (velocity * cos(w * t) + (-z * b - w * a) * sin(w * t))
            let initial = S.focusMass * velocity * velocity + S.focusStiffness * a * a
            if initial == 0 || (S.focusMass * v * v + S.focusStiffness * delta * delta) / initial < S.springEnergy { return (to, 0) }
            return (to + delta, v)
        }
        let u = duration == 0 ? 1 : S.clamp(t / duration)
        let p = curve == 1 ? S.outQuad(u) : curve == 2 ? S.quad(u) : curve == 3 ? S.outCubic(u) : u
        return (from + (to - from) * p, 0)
    }
    var end: Date {
        guard velocity != nil else { return at.addingTimeInterval(duration) }
        var lo = 0.0, hi = 1.0
        while sample(at.addingTimeInterval(hi)).velocity != 0 { hi *= 2 }
        for _ in 0..<48 { let mid = (lo + hi) / 2; if sample(at.addingTimeInterval(mid)).velocity == 0 { hi = mid } else { lo = mid } }
        return at.addingTimeInterval(hi)
    }
    mutating func target(_ value: Double, _ date: Date, _ duration: Double, _ curve: Int = 0) {
        from = sample(date).value; to = value; at = date; self.duration = duration; self.curve = curve; velocity = nil
    }
    mutating func spring(_ value: Double, _ date: Date, _ reduced: Bool) {
        if reduced { self = .init(value); return }
        if to == value { return }
        let now = sample(date); from = now.value; to = value; at = date; velocity = now.velocity
    }
}

private struct MoodStackColumnsPulse {
    var at = Date.distantPast, from = 1.0
    func value(_ date: Date) -> Double {
        let t = date.timeIntervalSince(at)
        if t < 0 || t >= S.pulseFor * 2 { return 1 }
        if t < S.pulseFor { return from + (S.pulse - from) * S.outQuad(t / S.pulseFor) }
        return S.pulse + (1 - S.pulse) * S.quad((t - S.pulseFor) / S.pulseFor)
    }
    mutating func start(_ date: Date) { from = value(date); at = date }
    var end: Date { at.addingTimeInterval(S.pulseFor * 2) }
}

private struct MoodStackColumnsGeometry {
    var width: Double, height: Double, top: Double, bottom: Double, count: Int
    var headingY: Double { top + S.headingInset }
    var datesY: Double { headingY + S.headingHeight + S.datesGap }
    var ruleY: Double { datesY + S.toggleHeight + S.ruleGap }
    var titleY: Double { ruleY + S.ruleHeight + S.titleGap }
    var headerHeight: Double { titleY + S.titleHeight }
    var teaserY: Double { height - S.teaserHeight - bottom }
    var chartSpace: Double { teaserY - headerHeight }
    var barHeight: Double { max(S.minChart, min(S.maxChart, chartSpace - S.chartAllowance)) }
    var rowHeight: Double { barHeight + S.nameGap + S.nameHeight + S.checkHeight + S.captionGap + S.captionSlot }
    var rowY: Double { headerHeight + (chartSpace - rowHeight) / 2 }
    var row: Double { width - S.margin * 2 - S.gap * Double(count - 1) }
    var base: Double { row / Double(count) }
    var wide: Double { count > 1 ? base * S.widen : base }
    var narrow: Double { count > 1 ? (row - wide) / Double(count - 1) : base }
    func columnAt(_ x: Double, _ selected: Int) -> Int {
        var left = S.margin
        for i in 0..<count {
            let width = selected < 0 ? base : i == selected ? wide : narrow
            if x < left + width + S.gap / 2 { return i }
            left += width + S.gap
        }
        return count - 1
    }
}
private struct MoodStackColumnsPlayback: Equatable { var autoPlay: Bool, touches: Int, count: Int }

/// A full-screen mood journal: tap again to release a column, drag to scrub, or re-sort its stack.
struct MoodStackColumns: View {
    var heading = "All time"
    var dateRange = "JUN 28 – OCT 12"
    var title = "Your mood during the day"
    var teaser = "Your emotions during the week"
    var moods = MoodStackColumnsMood.samples
    var columns = MoodStackColumnsColumn.samples
    var byMoodLabel = "By mood"
    var bySizeLabel = "By size"
    var autoPlay = false
    /// The column's place, or -1 when the selection is cleared. autoPlay also calls this.
    var onSelect: ((Int) -> Void)? = nil
    var onSortChange: ((Bool) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var selected = -1
    @State private var bySize = false
    @State private var any = MoodStackColumnsScalar(0)
    @State private var focuses = Array(repeating: MoodStackColumnsScalar(0), count: 7)
    @State private var pulses = Array(repeating: MoodStackColumnsPulse(), count: 7)
    @State private var captions = Array(repeating: MoodStackColumnsScalar(0), count: 7)
    @State private var sort = MoodStackColumnsScalar(0)
    @State private var toggle = MoodStackColumnsScalar(0)
    @State private var holding = false
    @State private var pressStart = -1
    @State private var pressedSelected = false
    @State private var moved = false
    @GestureState private var dragging = false
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var animation = 0
    @State private var busy = false
    @State private var idleAt = Date.now
    @State private var deadline = Date.distantPast
    @State private var haptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let geo = MoodStackColumnsGeometry(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom, top: insets.top, bottom: insets.bottom, count: columns.count)
            let pieces = columns.map(MoodStackColumnsStack)
            TimelineView(.animation(paused: !busy)) { timeline in
                let date = busy ? timeline.date : idleAt
                ZStack(alignment: .topLeading) {
                    Canvas { c, _ in draw(c, geo, pieces, date) }.accessibilityHidden(true)
                    ForEach(0..<2, id: \.self) { i in
                        Button { touched(); if bySize != (i == 1) { resort(i == 1, true, .now) } } label: {
                            Color.clear.frame(width: S.toggleWidth, height: S.toggleHeight - S.toggleInset * 2).contentShape(Rectangle())
                        }.buttonStyle(.plain).offset(x: geo.width - S.margin - S.toggleWidth * 2 - S.toggleInset + Double(i) * S.toggleWidth, y: geo.datesY + S.toggleInset)
                            .accessibilityLabel(i == 0 ? byMoodLabel : bySizeLabel).accessibilityAddTraits(bySize == (i == 1) ? .isSelected : [])
                    }
                    Color.clear.frame(width: geo.width, height: geo.rowHeight).contentShape(Rectangle()).offset(y: geo.rowY)
                        .gesture(DragGesture(minimumDistance: 0).updating($dragging) { _, active, _ in active = true }.onChanged { value in
                            let index = geo.columnAt(value.location.x, selected)
                            if !holding { holding = true; pressStart = index; pressedSelected = index == selected; moved = false; choose(index); touched() }
                            else { if index != pressStart { moved = true }; choose(index) }
                        }.onEnded { _ in finish() })
                        .accessibilityElement(children: .contain)
                        .overlay(alignment: .topLeading) {
                            // Accessibility follows the same settled widths the gesture uses.
                            HStack(spacing: S.gap) {
                                ForEach(columns.indices, id: \.self) { i in
                                    Color.clear.frame(width: selected < 0 ? geo.base : i == selected ? geo.wide : geo.narrow, height: geo.rowHeight)
                                        .accessibilityElement().accessibilityLabel(accessibility(i, pieces[i])).accessibilityAddTraits(.isButton)
                                        .accessibilityAction { touched(); select(i, true, .now) }
                                }
                            }.padding(.horizontal, S.margin).allowsHitTesting(false)
                        }
                    Color.clear.frame(width: geo.width - S.margin * 2, height: geo.headerHeight - geo.top)
                        .offset(x: S.margin, y: geo.top).accessibilityElement().accessibilityLabel(heading + ", " + dateRange + ", " + title).allowsHitTesting(false)
                }.frame(width: geo.width, height: geo.height, alignment: .topLeading).offset(x: -insets.leading, y: -insets.top)
            }
        }.background(S.background.ignoresSafeArea()).preferredColorScheme(.dark)
            .onAppear { haptic.prepare() }
            .onChange(of: dragging) { _, active in if !active && holding { finish() } }
            .onChange(of: reduced) { _, _ in updateFocus(.now); updateSort(.now); activate(.now) }
            .onChange(of: columns.count) { _, _ in updateSort(.now); activate(.now) }
            .task(id: animation) { do { try await wait(deadline); idleAt = .now; busy = false } catch {} }
            .task(id: MoodStackColumnsPlayback(autoPlay: autoPlay, touches: touches, count: columns.count)) { await playback() }
    }
    private func touched() { lastTouch = .now; touches += 1 }
    private func tick() { haptic.selectionChanged(); haptic.prepare() }
    private func choose(_ index: Int) { if index != selected { touched(); setSelection(index, .now); tick(); onSelect?(index) } }
    private func finish() { guard holding else { return }; holding = false; if pressedSelected && !moved { touched(); setSelection(-1, .now); tick(); onSelect?(-1) } else { touched() } }
    private func select(_ index: Int, _ hand: Bool, _ date: Date) { setSelection(index == selected ? -1 : index, date); if hand { tick() }; onSelect?(selected) }
    private func setSelection(_ index: Int, _ date: Date) {
        if index == selected { return }
        selected = index; updateFocus(date); activate(date)
    }
    private func updateFocus(_ date: Date) {
        any.spring(selected >= 0 ? 1 : 0, date, reduced)
        for i in focuses.indices {
            focuses[i].spring(i == selected ? 1 : 0, date, reduced)
            captions[i].target(i == selected ? 1 : 0, date, S.captionFor, 2)
            if reduced { pulses[i] = .init() }
        }
        if selected >= 0 && !reduced { pulses[selected].start(date) }
    }
    private func resort(_ next: Bool, _ hand: Bool, _ date: Date) {
        if next != bySize { bySize = next; updateSort(date); activate(date) }
        if hand { tick() }; onSortChange?(next)
    }
    private func updateSort(_ date: Date) {
        sort.target(bySize ? 1 : 0, date, reduced ? 0 : S.sortFor + Double(columns.count - 1) * S.sortStagger)
        toggle.target(bySize ? 1 : 0, date, reduced ? 0 : S.toggleFor, 3)
    }
    private func activate(_ date: Date) {
        let ends = [any.end, sort.end, toggle.end] + focuses.map(\.end) + captions.map(\.end) + pulses.map(\.end)
        deadline = ends.max() ?? date; idleAt = date; busy = deadline > date; animation += 1
    }
    private func wait(_ date: Date) async throws {
        while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }
        try Task.checkCancellation()
    }
    private func playback() async {
        guard autoPlay else { return }
        do {
            if touches > 0 {
                let reset = lastTouch.addingTimeInterval(M.userHold)
                try await wait(reset); setSelection(-1, reset)
                if bySize { bySize = false; updateSort(reset); activate(reset) }
                try await wait(reset.addingTimeInterval(M.settle))
            }
            var origin = Date.now
            while true {
                for (at, index) in M.selectAt { let date = origin.addingTimeInterval(at); try await wait(date); select(index % columns.count, false, date) }
                let release = origin.addingTimeInterval(M.releaseAt); try await wait(release); select(M.selectAt.last!.1 % columns.count, false, release)
                let size = origin.addingTimeInterval(M.sortAt); try await wait(size); resort(true, false, size)
                let mood = origin.addingTimeInterval(M.unsortAt); try await wait(mood); resort(false, false, mood)
                origin = origin.addingTimeInterval(M.loop); try await wait(origin)
            }
        } catch {}
    }
    private func number(_ value: Double) -> String { value == value.rounded() && abs(value) < 9e15 ? String(Int64(value)) : String(value) }
    private func caption(_ top: MoodStackColumnsPiece?) -> String {
        guard let top else { return "" }; return "\(number(top.label))% \(moods[top.mood].adjective)"
    }
    private func accessibility(_ i: Int, _ pieces: [MoodStackColumnsPiece]) -> String { "\(columns[i].name), \(columns[i].checkIns) check-ins, \(caption(pieces.first))" }
    private func font(_ size: Double, _ weight: UIFont.Weight, _ serif: Bool = false, _ tabular: Bool = false) -> UIFont {
        let font = tabular ? UIFont.monospacedDigitSystemFont(ofSize: size, weight: weight) : UIFont.systemFont(ofSize: size, weight: weight)
        return serif ? UIFont(descriptor: font.fontDescriptor.withDesign(.serif) ?? font.fontDescriptor, size: size) : font
    }
    private func width(_ value: String, _ size: Double, _ weight: UIFont.Weight, _ serif: Bool = false, _ tracking: Double = 0, _ tabular: Bool = false) -> Double {
        (value as NSString).size(withAttributes: [.font: font(size, weight, serif, tabular), .kern: tracking]).width
    }
    private func fitted(_ value: String, _ size: Double, _ max: Double, _ weight: UIFont.Weight, _ serif: Bool = false, _ tracking: Double = 0) -> Double {
        let measured = width(value, size, weight, serif, tracking)
        return measured <= max || measured == 0 ? size : size * max / measured
    }
    private func ellipsis(_ value: String, _ size: Double, _ max: Double, _ weight: UIFont.Weight, _ serif: Bool = false, _ tracking: Double = 0, _ tabular: Bool = false) -> String {
        if width(value, size, weight, serif, tracking, tabular) <= max { return value }
        var text = value
        while !text.isEmpty && width(text + "…", size, weight, serif, tracking, tabular) > max { text.removeLast() }
        return text + "…"
    }
    private func text(_ c: GraphicsContext, _ value: String, _ x: Double, _ y: Double, _ size: Double, _ height: Double, _ color: Color, _ weight: UIFont.Weight = .regular, _ centered: Bool = false, serif: Bool = false, tracking: Double = 0, tabular: Bool = false) {
        let f = font(size, weight, serif, tabular)
        let bottom = y + (height + CTFontGetAscent(f as CTFont) - CTFontGetDescent(f as CTFont)) / 2 + CTFontGetDescent(f as CTFont)
        c.draw(Text(value).font(Font(f)).tracking(tracking).foregroundStyle(color), at: CGPoint(x: x, y: bottom), anchor: centered ? .bottom : .bottomLeading)
    }
    private func draw(_ c: GraphicsContext, _ geo: MoodStackColumnsGeometry, _ pieces: [[MoodStackColumnsPiece]], _ date: Date) {
        let heading = ellipsis(heading, S.headingSize, geo.width - S.margin * 2 - S.chevronGap - S.chevronSize, .semibold, true, S.headingTracking)
        text(c, heading, S.margin, geo.headingY, S.headingSize, S.headingHeight, S.ink, .semibold, serif: true, tracking: S.headingTracking)
        let iconX = S.margin + width(heading, S.headingSize, .semibold, true, S.headingTracking) + S.chevronGap + S.chevronSize / 2
        if let image = UIImage(systemName: "chevron.down", withConfiguration: UIImage.SymbolConfiguration(pointSize: S.chevronSize * 0.6, weight: .regular)) {
            var icon = c.resolve(Image(uiImage: image).renderingMode(.template)); icon.shading = .color(S.ink)
            c.draw(icon, at: CGPoint(x: iconX, y: geo.headingY + S.headingHeight / 2 + S.chevronDrop / 2))
        }
        text(c, dateRange, S.margin, geo.datesY + (S.toggleHeight - S.datesHeight) / 2, S.datesSize, S.datesHeight, S.soft, .medium, tracking: S.datesTracking)
        let toggleX = geo.width - S.margin - S.toggleWidth * 2 - S.toggleInset * 2
        c.fill(capsule(CGRect(x: toggleX, y: geo.datesY, width: S.toggleWidth * 2 + S.toggleInset * 2, height: S.toggleHeight)), with: .color(S.toggle))
        c.fill(capsule(CGRect(x: toggleX + S.toggleInset + toggle.sample(date).value * S.toggleWidth, y: geo.datesY + S.toggleInset, width: S.toggleWidth, height: S.toggleHeight - S.toggleInset * 2)), with: .color(S.thumb))
        for (i, label) in [byMoodLabel, bySizeLabel].enumerated() {
            text(c, label, toggleX + S.toggleInset + S.toggleWidth * (Double(i) + 0.5), geo.datesY + (S.toggleHeight - S.toggleLine) / 2, S.toggleSize, S.toggleLine, bySize == (i == 1) ? S.ink : S.mute, .medium, true)
        }
        c.fill(Path(CGRect(x: S.margin, y: geo.ruleY, width: geo.width - S.margin * 2, height: S.ruleHeight)), with: .color(S.thumb))
        c.fill(Path(CGRect(x: S.margin, y: geo.ruleY, width: (geo.width - S.margin * 2) * S.ruleMark, height: S.ruleHeight)), with: .color(S.ink))
        text(c, title, S.margin, geo.titleY, fitted(title, S.titleSize, geo.width - S.margin * 2, .semibold, true, S.titleTracking), S.titleHeight, S.ink, .semibold, serif: true, tracking: S.titleTracking)
        var left = S.margin
        let a = any.sample(date).value, s = sort.sample(date).value
        for i in columns.indices {
            let f = focuses[i].sample(date).value
            let width = geo.base + (geo.wide - geo.base) * f + (geo.narrow - geo.base) * (a - f)
            var layer = c; layer.opacity = 1 - S.dim * S.clamp(a - f)
            layer.drawLayer { column in
                let top = pieces[i].first, bar = CGRect(x: left, y: geo.rowY, width: width, height: geo.barHeight)
                var segments = column.scaled(pulses[i].value(date), about: CGPoint(x: bar.midX, y: bar.midY))
                segments.clip(to: RoundedRectangle(cornerRadius: S.columnRadius, style: .continuous).path(in: bar))
                segments.fill(Path(bar), with: .color(top.map { moods[$0.mood].color } ?? S.empty))
                // Keep the RN progress formula in both directions, including its per-column timing.
                let stagger = Double(i) * S.sortStagger
                let p = reduced ? floor(s + 0.5) : S.cubic((s * (S.sortFor + stagger) - stagger) / S.sortFor)
                for piece in pieces[i] {
                    let y = geo.rowY + (piece.byMood + (piece.bySize - piece.byMood) * p) * geo.barHeight, height = piece.share * geo.barHeight
                    segments.fill(Path(CGRect(x: left, y: y, width: width, height: height)), with: .color(moods[piece.mood].color))
                    if height >= S.minLabelHeight {
                        let label = ellipsis("\(number(piece.label))%", S.segmentSize, width, .bold, false, 0, true)
                        let center = CGPoint(x: left + width / 2, y: y + height / 2), labels = segments.scaled(1 + (S.labelGrow - 1) * f, about: center)
                        text(labels, label, center.x, center.y - S.segmentHeight / 2, S.segmentSize, S.segmentHeight, S.segmentInk, .bold, true, tabular: true)
                    }
                }
                let nameY = geo.rowY + geo.barHeight + S.nameGap, center = left + width / 2
                text(column, columns[i].name, center, nameY, fitted(columns[i].name, S.nameSize, width, .medium), S.nameHeight, S.ink, .medium, true)
                let checks = "\(columns[i].checkIns) \(columns[i].checkIns == 1 ? "check-in" : "check-ins")"
                text(column, checks, center, nameY + S.nameHeight, fitted(checks, S.checkSize, width, .regular), S.checkHeight, S.mute, .regular, true)
                if let top, captions[i].sample(date).value > 0 {
                    text(column, ellipsis(caption(top), S.captionSize, width, .semibold), center, nameY + S.nameHeight + S.checkHeight + S.captionGap, S.captionSize, S.captionHeight, moods[top.mood].color.opacity(captions[i].sample(date).value), .semibold, true)
                }
            }
            left += width + S.gap
        }
        var teaserContext = c
        let teaserBox = CGRect(x: 0, y: geo.teaserY, width: geo.width, height: S.teaserHeight + geo.bottom)
        teaserContext.clip(to: Path(teaserBox))
        text(teaserContext, teaser, S.margin, geo.teaserY, fitted(teaser, S.titleSize, geo.width - S.margin * 2, .semibold, true, S.titleTracking), S.titleHeight, S.ink.opacity(S.teaserOpacity), .semibold, serif: true, tracking: S.titleTracking)
        teaserContext.fill(Path(teaserBox), with: .linearGradient(Gradient(stops: [.init(color: S.background.opacity(0), location: S.teaserStops[0]), .init(color: S.background, location: S.teaserStops[1])]), startPoint: CGPoint(x: 0, y: geo.teaserY), endPoint: CGPoint(x: 0, y: geo.height)))
    }
}
