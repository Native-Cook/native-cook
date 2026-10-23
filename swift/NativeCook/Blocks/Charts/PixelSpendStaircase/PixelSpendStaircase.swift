import SwiftUI

private enum K {
    // May: steady days, rent on the 18th, and $123 today. April ran ahead of it from the 6th on.
    static let spending: [Double] = [620, 560, 70, 60, 250, 590, 90, 780, 70, 290, 320, 310, 70, 60, 480, 80, 320, 3030, 470, 613, 123]
    static let previous: [Double] = [
        400, 500, 100, 150, 250, 1160, 60, 1080, 60, 840, 50, 250, 300, 300, 700, 1500, 80, 820, 600, 60, 40, 40, 60, 50, 60, 57,
    ]

    static let background = Color(hex: 0x1a1a1a)
    static let ink = Color(hex: 0xf2f2f2)
    static let mute = Color(hex: 0x8a8a8a)
    static let faint = Color(hex: 0x6f6f6f)
    static let chevron = Color(hex: 0x4a4a4a)
    // Cells by height, bottom to top: the bottom rows sink into the screen.
    static let greyStops: [(Double, UInt32)] = [(0, 0x1f1f1f), (0.14, 0x2a2a2a), (1, 0x2c2c2c)]
    static let emberStops: [(Double, UInt32)] = [(0, 0x2e1d15), (0.1, 0x47291a), (0.4, 0x8e4321), (0.7, 0xd2552a), (0.86, 0xee6430), (1, 0xf56b32)]
    static let cursor = Color(hex: 0xff4d26)
    static let flash = Color(hex: 0xffd9c2)

    // The grid: cells fill the width with a hairline gap; rows are as many as fit, up to maxRows.
    static let side = 4.0
    static let gap = 1.5
    static let radius = 1.5
    static let maxRows = 31
    // Room above the tallest column for the floating labels. Last month's label sits this many rows
    // higher than the cursor's would, so the two never meet at the end of the month.
    static let labelSpace = 76.0
    static let previousRise = 2.0
    static let labelGap = 8.0
    static let labelWidth = 180.0
    static let labelLine = 20.0
    // The glow behind the cursor cell, in cells, and its blur (a Skia sigma) in points.
    static let glow = 1.7
    static let glowBlur = 5.0
    static let glowAlpha = 0.32
    static let glowRadius = 3.0

    // Seconds. A column lights from the bottom up, one row every rowOn, and goes dark from the top.
    static let rowOn = 0.012
    static let rowOff = 0.02
    static let cellIn = 0.09
    static let cellOut = 0.12
    // Columns passed in one jump (a tap far from the cursor) start one after the other.
    static let columnStagger = 0.035
    // The top cell of a column flashes when it lights.
    static let flashFor = 0.35
    static let flashPeak = 0.75
    // A brighter band runs along the tops of the lit columns once the cursor is back at today.
    static let shimmerAfter = 0.15
    static let shimmerFor = 1.2
    static let shimmerDepth = 4
    static let shimmerWidth = 1.4
    static let shimmerPeak = 0.22
    static let reducedFade = 0.16
    // The amounts roll to their new value, and the label glides to its column.
    static let roll = 0.32
    static let labelGlide = 0.18

    // The autoPlay timeline, in seconds from the start of each loop.
    static let loop = 9.0
    static let scrubTo = 4
    static let leftAt = 0.8
    static let leftFor = 1.8
    static let rightAt = 3.3
    static let rightFor = 2.4
    // After a touch the block waits this long, then glides back to today and loops again.
    static let userHold = 3.0
    static let returnFor = 0.9

    static let headerTop = 36.0
    static let amount = 72.0

    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }

    static func easeInOut(_ t: Double) -> Double {
        let x = clamp(t)
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - clamp(t), 3) }

    /// The colour at `t` along a list of stops, mixed per channel in sRGB.
    static func along(_ stops: [(Double, UInt32)], _ t: Double) -> Color {
        var i = 0
        while i < stops.count - 2 && t > stops[i + 1].0 { i += 1 }
        let (t0, c0) = stops[i]
        let (t1, c1) = stops[i + 1]
        let u = min(1, max(0, (t - t0) / (t1 - t0)))
        let channel = { (shift: UInt32) -> Double in
            let a = Double((c0 >> shift) & 0xff), b = Double((c1 >> shift) & 0xff)
            return (a + (b - a) * u).rounded()
        }
        return Color.rgba(channel(16), channel(8), channel(0), 1)
    }

    static func running(_ values: [Double]) -> [Double] {
        var sum = 0.0
        return values.map { sum += $0; return sum }
    }

    private static let grouping: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 0
        return formatter
    }()

    static func money(_ symbol: String, _ value: Double) -> String {
        symbol + (grouping.string(from: NSNumber(value: value.rounded())) ?? "")
    }

    /// The times at which an ease-in-out glide from column `a` to `b`, starting at `start`, reaches
    /// each column it passes (rounded as JavaScript rounds), with that column.
    static func glide(from a: Int, to b: Int, start: Double, duration: Double) -> [(Double, Int)] {
        guard a != b else { return [] }
        let step = b > a ? 1 : -1
        return stride(from: a + step, through: b, by: step).map { column in
            let edge = Double(column) - 0.5 * Double(step)
            let share = (edge - Double(a)) / Double(b - a)
            var lo = 0.0, hi = 1.0
            for _ in 0..<40 {
                let mid = (lo + hi) / 2
                if easeInOut(mid) < share { lo = mid } else { hi = mid }
            }
            return (start + hi * duration, column)
        }
    }
}

/// A value rolling to a new one with an ease-out cubic, sampled against the timeline's clock.
private struct PixelSpendStaircaseRoll {
    var from: Double
    var to: Double
    var at = 0.0
    var duration = 0.0

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(_ t: Double) -> Double {
        guard duration > 0 else { return to }
        return from + (to - from) * K.easeOutCubic((t - at) / duration)
    }

    mutating func target(_ value: Double, _ t: Double, _ duration: Double) {
        guard value != to else { return }
        from = self.value(t)
        to = value
        at = t
        self.duration = duration
    }
}

/// Where everything sits for one chart size.
private struct PixelSpendStaircaseLayout {
    var size = CGSize.zero
    var pitch = 0.0
    var rows = K.maxRows
    var left = 0.0
    var lit: [Int] = []
    var ghost: [Int] = []
    var grey: [Color] = []
    var ember: [Color] = []

    init() {}

    init(size: CGSize, totals: [Double], previousTotals: [Double], columns: Int) {
        self.size = size
        pitch = size.width > 0 ? (size.width - K.side * 2) / Double(columns) : 0
        rows = pitch > 0 ? max(4, min(K.maxRows, Int(((size.height - K.labelSpace) / pitch).rounded(.down)))) : K.maxRows
        left = (size.width - pitch * Double(columns)) / 2
        // Column heights in cells: the taller month sets the scale.
        let top = max(totals.last ?? 1, previousTotals.last ?? 1)
        let rows = rows
        let cells = { (value: Double) -> Int in value > 0 ? max(1, Int((value / top * Double(rows)).rounded())) : 0 }
        lit = totals.map(cells)
        ghost = (0..<columns).map { cells($0 < previousTotals.count ? previousTotals[$0] : 0) }
        grey = (0..<rows).map { K.along(K.greyStops, Double($0) / Double(rows - 1)) }
        ember = (0..<rows).map { K.along(K.emberStops, Double($0) / Double(rows - 1)) }
    }

    var floor: Double { size.height }

    func cell(_ c: Int, _ r: Int) -> CGRect {
        let side = pitch - K.gap
        return CGRect(x: left + Double(c) * pitch + K.gap / 2, y: floor - Double(r + 1) * pitch + K.gap / 2, width: side, height: side)
    }

    /// The top-left corner of the label over column `c`, clear of last month's staircase.
    func label(_ c: Int) -> CGPoint {
        let height = Double(max(c < lit.count ? lit[c] : 0, ghost[c]))
        let x = min(size.width - K.labelWidth / 2 - K.side, max(K.labelWidth / 2 + K.side, left + Double(c) * pitch + pitch / 2))
        return CGPoint(x: x - K.labelWidth / 2, y: floor - height * pitch - K.labelGap - K.labelLine)
    }
}

/// Which columns are lit, when each last changed, and the cursor.
private struct PixelSpendStaircaseGrid {
    var on: [Bool]
    var at: [Double]
    var selected: Int
    var shimmerAt = -100.0
    var busyUntil = 0.0
}

private final class PixelSpendStaircaseCache {
    var layout = PixelSpendStaircaseLayout()
}

/**
 A month's spending as a staircase of pixels: each column is a day, lit from the bottom up to the
 month's running total, over last month's staircase in grey. Dragging moves the cursor (the bright
 top cell): days after it go dark from the top, and moving forward lights them again in a rising
 pixel sweep, while the amount and the label above the cursor roll to the day.
 */
struct PixelSpendStaircase: View {
    /// What was spent on each day of this month so far; the last entry is today.
    var spending = K.spending
    /// What was spent on each day of last month, drawn as the grey staircase behind.
    var previousSpending = K.previous
    var month = "May"
    var previousMonth = "Apr"
    var symbol = "$"
    var todayLabel = "today"
    /// Shown under the chart while this month's total is below last month's on the same day.
    var lessLabel = "You’re spending less…"
    var moreLabel = "You’re spending more…"
    /// Scrubs back to an early day and lights the month up again every `loop` seconds.
    var autoPlay = false
    /// Called with the day (1-based), that day's spend and the month's total up to it.
    var onSelect: ((Int, Double, Double) -> Void)? = nil
    /// The chevron under the chart: where a detail sheet would open.
    var onOpenDetails: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var still
    @State private var grid: PixelSpendStaircaseGrid?
    @State private var amount: PixelSpendStaircaseRoll?
    @State private var total: PixelSpendStaircaseRoll?
    @State private var labelX = PixelSpendStaircaseRoll(0)
    @State private var labelY = PixelSpendStaircaseRoll(0)
    @State private var chartSize = CGSize.zero
    @State private var cache = PixelSpendStaircaseCache()
    @State private var animating = false
    @State private var dragging = false
    @State private var touches = 0
    @State private var lastTouch = 0.0
    @State private var haptic = UISelectionFeedbackGenerator()

    private var today: Int { spending.count - 1 }
    private var columns: Int { max(spending.count, previousSpending.count) }
    private var totals: [Double] { K.running(spending) }
    private var previousTotals: [Double] { K.running(previousSpending) }
    private var state: PixelSpendStaircaseGrid {
        grid ?? PixelSpendStaircaseGrid(on: (0..<columns).map { $0 <= today }, at: Array(repeating: -100, count: columns), selected: today)
    }

    private static func clock(_ date: Date = .now) -> Double { date.timeIntervalSinceReferenceDate }

    var body: some View {
        GeometryReader { screen in
        TimelineView(.animation(paused: !animating)) { timeline in
            let t = Self.clock(animating ? timeline.date : .now)
            let grid = state
            let day = grid.selected
            let spend = (amount?.value(t) ?? spending[today]).rounded()
            let sum = (total?.value(t) ?? totals[today]).rounded()
            let isToday = day == today
            let dayName = "\(month) \(day + 1)"
            let previousAtDay = previousTotals.isEmpty ? 0 : previousTotals[min(day, previousTotals.count - 1)]
            VStack(spacing: 0) {
                VStack(spacing: 0) {
                    Text(K.money(symbol, spend))
                        .font(.system(size: K.amount, weight: .medium))
                        .tracking(-1)
                        .monospacedDigit()
                        .foregroundStyle(K.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                        .frame(height: 84)
                    Text(isToday ? todayLabel : dayName)
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(K.mute)
                        .frame(height: 30)
                        .padding(.top, -6)
                }
                .padding(.horizontal, 24)
                .padding(.top, K.headerTop)

                GeometryReader { proxy in
                    let layout = layout(for: proxy.size)
                    ZStack(alignment: .topLeading) {
                        Canvas { c, _ in draw(c, layout, grid, t) }
                        if layout.pitch > 0 {
                            let lastGhost = Double(layout.ghost.last ?? 0)
                            Text("\(previousMonth): \(K.money(symbol, previousTotals.last ?? 0))")
                                .font(.system(size: 15, weight: .semibold))
                                .monospacedDigit()
                                .foregroundStyle(K.faint)
                                .lineLimit(1)
                                .frame(height: K.labelLine)
                                .frame(width: layout.size.width - K.side - 2, alignment: .trailing)
                                .offset(y: layout.floor - (lastGhost + K.previousRise) * layout.pitch - K.labelGap - K.labelLine)
                            Text("\(isToday ? month : dayName): \(K.money(symbol, sum))")
                                .font(.system(size: 15, weight: .semibold))
                                .monospacedDigit()
                                .foregroundStyle(K.mute)
                                .lineLimit(1)
                                .frame(width: K.labelWidth, height: K.labelLine)
                                .offset(x: labelX.value(t), y: labelY.value(t))
                        }
                    }
                    .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { drag in
                                dragging = true
                                touch()
                                scrub((drag.location.x - layout.left) / layout.pitch - 0.5, byHand: true, at: Self.clock())
                            }
                            .onEnded { _ in
                                dragging = false
                                touch()
                            }
                    )
                    .onAppear { place(proxy.size) }
                    .onChange(of: proxy.size) { _, size in place(size) }
                }
                .padding(.top, 8)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Spending in \(month)")
                .accessibilityValue("\(dayName): \(K.money(symbol, spend)), \(K.money(symbol, sum)) so far")
                .accessibilityAdjustableAction { direction in
                    touch()
                    scrub(Double(state.selected + (direction == .increment ? 1 : -1)), byHand: true, at: Self.clock())
                }

                VStack(spacing: 0) {
                    Text(sum > previousAtDay ? moreLabel : lessLabel)
                        .font(.system(size: 19, weight: .medium))
                        .foregroundStyle(K.mute)
                        .lineLimit(1)
                        .frame(height: 24)
                    Button { onOpenDetails?() } label: {
                        PixelSpendStaircaseChevron()
                            .stroke(K.chevron, style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
                            .frame(width: 56, height: 16)
                            .padding(12)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 22 - 12)
                    .padding(.bottom, 6 - 12)
                    .accessibilityLabel("Details")
                }
                .padding(.top, 14)
                // At least 16 pt below, more where the home indicator needs it.
                .padding(.bottom, max(0, 16 - screen.safeAreaInsets.bottom))
            }
        }
        }
        .background(K.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: grid?.busyUntil ?? 0) {
            let until = state.busyUntil
            guard until > Self.clock() else { return }
            animating = true
            do {
                try await wait(until: until)
                animating = false
            } catch {}
        }
        .task(id: LoopKey(autoPlay: autoPlay, touches: touches)) { await loop() }
    }

    private struct LoopKey: Equatable {
        var autoPlay: Bool
        var touches: Int
    }

    private func layout(for size: CGSize) -> PixelSpendStaircaseLayout {
        if cache.layout.size != size || cache.layout.lit.count != spending.count {
            cache.layout = PixelSpendStaircaseLayout(size: size, totals: totals, previousTotals: previousTotals, columns: columns)
        }
        return cache.layout
    }

    /// Puts the label over the cursor's column without a glide, as on the first frame.
    private func place(_ size: CGSize) {
        chartSize = size
        let point = layout(for: size).label(state.selected)
        labelX = PixelSpendStaircaseRoll(point.x)
        labelY = PixelSpendStaircaseRoll(point.y)
    }

    // MARK: Actions

    /// Moves the cursor to the column nearest `x`; columns passed light up or go dark.
    private func scrub(_ x: Double, byHand: Bool, at t: Double) {
        let layout = layout(for: chartSize)
        guard layout.pitch > 0 else { return }
        var next = state
        let target = min(today, max(0, Int(floor(x + 0.5))))
        let old = next.selected
        guard target != old else { return }
        let stagger = still ? 0 : K.columnStagger
        var last = t
        if target > old {
            for c in old + 1...target {
                next.on[c] = true
                next.at[c] = t + Double(c - old - 1) * stagger
                last = next.at[c]
            }
        } else {
            for c in stride(from: old, to: target, by: -1) {
                next.on[c] = false
                next.at[c] = t + Double(old - c) * stagger
                last = next.at[c]
            }
        }
        next.selected = target
        var busy = last + Double(layout.rows) * K.rowOff + K.cellOut + K.flashFor
        if target == today && !still {
            let start = next.at[today] + Double(layout.lit[today] - 1) * K.rowOn + K.shimmerAfter
            next.shimmerAt = start
            busy = max(busy, start + K.shimmerFor)
        }
        // The amounts roll and the label glides on the same clock.
        busy = max(busy, t + K.roll)
        next.busyUntil = max(next.busyUntil, busy)
        grid = next

        var spend = amount ?? PixelSpendStaircaseRoll(spending[today])
        var sum = total ?? PixelSpendStaircaseRoll(totals[today])
        spend.target(spending[target], t, still ? 0 : K.roll)
        sum.target(totals[target], t, still ? 0 : K.roll)
        amount = spend
        total = sum
        let point = layout.label(target)
        labelX.target(point.x, t, still ? 0 : K.labelGlide)
        labelY.target(point.y, t, still ? 0 : K.labelGlide)

        if byHand {
            haptic.selectionChanged()
            haptic.prepare()
            onSelect?(target + 1, spending[target], totals[target])
        }
    }

    private func touch() {
        lastTouch = Self.clock()
        touches += 1
    }

    private func wait(until t: Double) async throws {
        while t > Self.clock() { try await Task.sleep(for: .seconds(t - Self.clock()), tolerance: .zero) }
        try Task.checkCancellation()
    }

    /// The column the autoPlay finger reaches at each time of one loop.
    private func loopSteps() -> [(Double, Int)] {
        let low = min(K.scrubTo, today)
        // Under reduced motion the cursor jumps instead of travelling.
        if still { return [(K.leftAt, low), (K.rightAt, today)] }
        return K.glide(from: today, to: low, start: K.leftAt, duration: K.leftFor)
            + K.glide(from: low, to: today, start: K.rightAt, duration: K.rightFor)
    }

    /// Each step is stamped with its time on the loop, not the time the task woke, so every loop is identical.
    private func loop() async {
        guard autoPlay else { return }
        do {
            if touches > 0 {
                try await wait(until: lastTouch + K.userHold)
                guard !dragging else { return }
            }
            let now = Self.clock()
            let from = state.selected
            var start = now
            if from != today && !still {
                start = now + K.returnFor
                for (at, column) in K.glide(from: from, to: today, start: now, duration: K.returnFor) {
                    try await wait(until: at)
                    scrub(Double(column), byHand: false, at: at)
                }
            }
            let steps = loopSteps()
            while true {
                for (at, column) in steps {
                    try await wait(until: start + at)
                    scrub(Double(column), byHand: false, at: start + at)
                }
                start += K.loop
            }
        } catch {}
    }

    // MARK: Drawing

    private func draw(_ c: GraphicsContext, _ layout: PixelSpendStaircaseLayout, _ grid: PixelSpendStaircaseGrid, _ now: Double) {
        guard layout.pitch > 0 else { return }
        let rows = layout.rows
        let shimmer = (now - grid.shimmerAt) / K.shimmerFor
        let band: Double? = shimmer >= 0 && shimmer <= 1 ? -2 + shimmer * Double(today + 4) : nil
        let cursor = grid.selected
        var cursorLit = 0.0
        let rrect = { (rect: CGRect) in Path(roundedRect: rect, cornerRadius: K.radius) }

        // Cells of one colour at full strength go into one path per row; the rest are drawn one by one.
        var greys = Array(repeating: Path(), count: rows)
        var embers = Array(repeating: Path(), count: rows)
        var partial: [(CGRect, Int, Double, Double)] = []

        for col in 0..<columns {
            let own = col <= today ? layout.lit[col] : 0
            let height = min(rows, max(own, layout.ghost[col]))
            let since = now - grid.at[col]
            for r in 0..<height {
                let rect = layout.cell(col, r)
                greys[r].addRoundedRect(in: rect, cornerSize: CGSize(width: K.radius, height: K.radius))
                if r >= own { continue }
                let level: Double
                if still {
                    level = grid.on[col] ? K.clamp(since / K.reducedFade) : 1 - K.clamp(since / K.reducedFade)
                } else if grid.on[col] {
                    level = K.clamp((since - Double(r) * K.rowOn) / K.cellIn)
                } else {
                    level = 1 - K.clamp((since - Double(own - 1 - r) * K.rowOff) / K.cellOut)
                }
                if level <= 0 { continue }
                // The top cell flashes as it lights, and the shimmer brightens the top few rows.
                var glint = 0.0
                if r == own - 1 && grid.on[col] && !still {
                    let lighted = since - Double(r) * K.rowOn
                    if lighted >= 0 { glint = K.flashPeak * (1 - K.clamp(lighted / K.flashFor)) }
                }
                let depth = own - 1 - r
                if depth < K.shimmerDepth, let band {
                    let d = (Double(col) - band) / K.shimmerWidth
                    glint = max(glint, K.shimmerPeak * exp(-d * d) * (1 - Double(depth) / Double(K.shimmerDepth)))
                }
                if level >= 1 && glint <= 0.005 {
                    embers[r].addRoundedRect(in: rect, cornerSize: CGSize(width: K.radius, height: K.radius))
                } else {
                    partial.append((rect, r, level, glint))
                }
                if col == cursor && r == own - 1 { cursorLit = level }
            }
        }
        for r in 0..<rows {
            c.fill(greys[r], with: .color(layout.grey[r]))
            c.fill(embers[r], with: .color(layout.ember[r]))
        }
        for (rect, r, level, glint) in partial {
            c.fill(rrect(rect), with: .color(layout.ember[r].opacity(level)))
            if glint > 0.005 { c.fill(rrect(rect), with: .color(K.flash.opacity(glint * level))) }
        }

        // The cursor: the top cell of the selected day, red, in a soft square glow.
        let top = layout.lit[cursor] - 1
        guard cursorLit > 0, top >= 0 else { return }
        let halo = layout.pitch * K.glow
        let cx = layout.left + Double(cursor) * layout.pitch + layout.pitch / 2
        let cy = layout.floor - (Double(top) + 0.5) * layout.pitch
        c.drawLayer { glow in
            glow.addFilter(.blur(radius: K.glowBlur))
            glow.fill(
                Path(roundedRect: CGRect(x: cx - halo / 2, y: cy - halo / 2, width: halo, height: halo), cornerRadius: K.glowRadius),
                with: .color(K.cursor.opacity(K.glowAlpha * cursorLit))
            )
        }
        c.fill(rrect(layout.cell(cursor, top)), with: .color(K.cursor.opacity(cursorLit)))
    }
}

/// A wide, shallow chevron in a 56 × 16 box.
private struct PixelSpendStaircaseChevron: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 4, y: 4))
        path.addLine(to: CGPoint(x: 28, y: 12))
        path.addLine(to: CGPoint(x: 52, y: 4))
        return path
    }
}
