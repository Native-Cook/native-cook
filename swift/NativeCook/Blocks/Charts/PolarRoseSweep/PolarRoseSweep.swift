import CoreText
import SwiftUI

struct PolarRoseSweepRound {
    /// The toggle's label.
    var name: String
    /// Per idea, in percent of the people asked; what is left over did not answer.
    var likely: [Double]
    var unsure: [Double]
    var unlikely: [Double]

    static let samples = [
        PolarRoseSweepRound(
            name: "March",
            likely: [77, 71, 67, 56, 54, 46, 46, 46, 46, 31],
            unsure: [13, 14, 10, 16, 12, 18, 14, 20, 12, 22],
            unlikely: [8, 9, 15, 18, 22, 20, 26, 18, 30, 28]
        ),
        PolarRoseSweepRound(
            name: "June",
            likely: [82, 64, 72, 49, 61, 58, 40, 52, 38, 44],
            unsure: [10, 17, 11, 20, 13, 14, 18, 16, 15, 19],
            unlikely: [6, 12, 12, 22, 18, 20, 28, 22, 30, 26]
        ),
    ]
}

private enum K {
    static let ideas = [
        "Layout refiner", "Sketch converter", "Prompt builder", "Search assistant", "Anomaly detector",
        "Group finder", "Price predictor", "Diagram seeker", "Graph copilot", "Link analyst",
    ]

    static let centre = Color(hex: 0x3a1a57)
    static let edge = Color(hex: 0x0e0516)
    static let title = Color(hex: 0xf1d9ff)
    static let soft = Color(hex: 0xa57bc0)
    static let label: UInt32 = 0xb58ad1
    static let grid: UInt32 = 0x5c3380
    // The inner sector's colour follows its value: dark plum for the least likely, lavender for the most.
    static let likelyStops: [(Double, UInt32)] = [(30, 0x6f4687), (45, 0x9568ab), (65, 0xcf97e2), (80, 0xeeb3f7)]
    static let unsure: UInt32 = 0x6b2bbf
    static let unlikely: UInt32 = 0x2b1345
    static let number: UInt32 = 0x3b1c57
    static let numberSelected: UInt32 = 0x111111
    static let unsureNumber: UInt32 = 0xe3c4f7
    static let white: UInt32 = 0xffffff
    static let legend: [UInt32] = [0xeeb3f7, 0x7b2fd2, 0x2b1345]
    static let pill = Color.rgba(26, 10, 40, 0.8)
    static let thumb = Color.rgba(241, 217, 255, 0.16)

    // The chart. Radius is linear in percent (not area): 100 % is the outer ring.
    static let maxRadius = 170.0
    static let labelRoom = 32.0
    static let labelGap = 15.0
    static let rings = [0.2, 0.4, 0.6, 0.8, 1]
    static let gridWidth = 0.75
    static let gridAlpha = 0.55
    // The selected wedge pushes out and brightens; the others dim a little.
    static let push = 10.0
    static let brighten = 0.14
    static let dim = 0.78
    static let numberSize = 15.0
    static let numberSelectedSize = 30.0
    static let numberAt = 0.66
    static let numberOut = 0.1
    static let labelSize = 12.0
    static let tapRoom = 36.0
    static let numberFont = UIFont.systemFont(ofSize: numberSize, weight: .bold)
    static let unsureFont = UIFont.systemFont(ofSize: 14, weight: .semibold)
    static let labelFont = UIFont.systemFont(ofSize: labelSize, weight: .regular)

    // Seconds. A spring step (damping ratio and angular frequency) for pushes and re-flows.
    static let zeta = 0.62
    static let omega = 12.0
    static let settled = 0.9
    // A radar line sweeps clockwise; each wedge re-flows to the new round as it passes.
    static let sweepFor = 1.5
    static let trail = 32.0
    static let trailSteps = 12
    static let trailAlpha = 0.16
    static let reducedFade = 0.18
    static let toggleFor = 0.24

    // The autoPlay timeline, in seconds from the start of each loop.
    static let loop = 10.0
    static let tour: [(Double, Int)] = [(0.8, 1), (1.6, 2), (2.4, 3), (5.6, 0)]
    static let nextRoundAt = 3.4
    static let backAt = 6.4
    static let userHold = 3.0
    static let settle = 2.0

    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }

    /// A damped spring's step response at `t` seconds: 0 before, about 1 once settled.
    static func spring(_ t: Double) -> Double {
        if t <= 0 { return 0 }
        if t >= settled * 2 { return 1 }
        let decay = zeta * omega
        let damped = omega * sqrt(1 - zeta * zeta)
        return 1 - exp(-decay * t) * (cos(damped * t) + (decay / damped) * sin(damped * t))
    }

    static func channels(_ hex: UInt32) -> [Double] { [Double((hex >> 16) & 0xff), Double((hex >> 8) & 0xff), Double(hex & 0xff)] }

    /// A colour mixed toward white by `lighten`, with `opacity`; a spring overshoots past 1, so both stop at the ends.
    static func color(_ c: [Double], _ lighten: Double, _ opacity: Double) -> Color {
        let light = clamp(lighten)
        let mixed = c.map { ($0 + (255 - $0) * light).rounded() }
        return Color.rgba(mixed[0], mixed[1], mixed[2], (clamp(opacity) * 255).rounded() / 255)
    }

    static func mix(_ a: [Double], _ b: [Double], _ u: Double) -> [Double] { zip(a, b).map { $0 + ($1 - $0) * u } }

    /// An annular sector between two radii and two angles (degrees, clockwise from +x).
    static func sector(_ cx: Double, _ cy: Double, _ r0: Double, _ r1: Double, _ a0: Double, _ sweep: Double) -> Path {
        var path = Path()
        guard r1 > r0 else { return path }
        let centre = CGPoint(x: cx, y: cy)
        path.addArc(center: centre, radius: r1, startAngle: .degrees(a0), endAngle: .degrees(a0 + sweep), clockwise: false)
        if r0 < 0.5 {
            path.addLine(to: centre)
        } else {
            path.addArc(center: centre, radius: r0, startAngle: .degrees(a0 + sweep), endAngle: .degrees(a0), clockwise: true)
        }
        path.closeSubpath()
        return path
    }
}

/// What is selected and shown, and when each last changed, in seconds on the reference clock.
private struct PolarRoseSweepRun {
    var selected = 0
    var previous = -1
    var selectedAt = -100.0
    var round = 0
    var fromRound = 0
    var sweptAt = -100.0
    var toggleFrom = 0.0
    var toggleAt = -100.0
    var busyUntil = 0.0

    /// The toggle's thumb position, gliding between rounds with an ease-out cubic.
    func toggle(_ t: Double, still: Bool) -> Double {
        if still { return Double(round) }
        let u = 1 - pow(1 - K.clamp((t - toggleAt) / K.toggleFor), 3)
        return toggleFrom + (Double(round) - toggleFrom) * u
    }
}

/**
 A survey as a polar-area chart: one wedge per idea, each three stacked sectors (likely, hard to
 say, not likely) whose radii read in percent. A touch selects the wedge under it, which pushes out
 on a spring; the round toggle sends a radar line round the chart, each wedge re-flowing to the
 new answers as the line passes while its number rolls.
 */
struct PolarRoseSweep: View {
    var title = "How likely are you to use it?"
    var caption: String? = nil
    /// One wedge per idea, clockwise from the top.
    var ideas = K.ideas
    /// Two or more survey rounds over the same ideas; the toggle switches between them.
    var rounds = PolarRoseSweepRound.samples
    var legend = ["Likely", "Hard to say", "Not likely"]
    /// Tours the wedges and sweeps to the next round and back every `loop` seconds.
    var autoPlay = false
    var onSelect: ((Int) -> Void)? = nil
    var onRoundChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var still
    @State private var run = PolarRoseSweepRun()
    @State private var chartFrame = CGRect.zero
    @State private var toggleWidths: [Int: Double] = [:]
    @State private var animating = false
    @State private var dragging = false
    @State private var touches = 0
    @State private var lastTouch = 0.0
    @State private var haptic = UISelectionFeedbackGenerator()

    private var count: Int { ideas.count }
    private var step: Double { 360 / Double(count) }

    private static func clock(_ date: Date = .now) -> Double { date.timeIntervalSinceReferenceDate }

    private func radius(_ size: CGSize) -> Double {
        max(60, min(K.maxRadius, size.width / 2 - K.labelRoom, size.height / 2 - K.labelRoom))
    }

    var body: some View {
        GeometryReader { screen in
        TimelineView(.animation(paused: !animating)) { timeline in
            let t = Self.clock(animating ? timeline.date : .now)
            VStack(alignment: .leading, spacing: 0) {
                header(t)
                GeometryReader { proxy in
                    Canvas { c, size in draw(c, size, t) }
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { drag in
                                    dragging = true
                                    touch()
                                    select(wedge(at: drag.location, in: proxy.size), byHand: true, at: Self.clock())
                                }
                                .onEnded { _ in
                                    dragging = false
                                    touch()
                                }
                        )
                }
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { chartFrame = $0 }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Survey chart")
                .accessibilityValue("\(ideas[run.selected]): \(Int(rounds[run.round].likely[run.selected]))%")
                .accessibilityAdjustableAction { direction in
                    touch()
                    select((run.selected + (direction == .increment ? 1 : -1) + count) % count, byHand: true, at: Self.clock())
                }
                legendPill
                    .frame(maxWidth: .infinity)
                    // At least 16 pt below, more where the home indicator needs it, then 8 more.
                    .padding(.bottom, max(0, 16 - screen.safeAreaInsets.bottom) + 8)
            }
        }
        }
        .background {
            Canvas { c, size in
                let full = Path(CGRect(origin: .zero, size: size))
                c.fill(full, with: .color(K.edge))
                c.fill(full, with: .radialGradient(
                    Gradient(colors: [K.centre, K.edge]),
                    center: CGPoint(x: chartFrame.midX, y: chartFrame.midY), startRadius: 0,
                    endRadius: max(1, max(chartFrame.width, chartFrame.height) * 0.85)
                ))
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: run.busyUntil) {
            let until = run.busyUntil
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

    private func header(_ t: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(K.title)
                .lineSpacing(28 - UIFont.systemFont(ofSize: 22, weight: .semibold).lineHeight)
            Text(caption ?? "Survey, \(count) ideas")
                .font(.system(size: 13))
                .foregroundStyle(K.soft)
                .frame(height: 18)
                .padding(.top, 4)
            toggle(t).padding(.top, 16)
        }
        .padding(.horizontal, 24)
        .padding(.top, 16)
    }

    private func toggle(_ t: Double) -> some View {
        let v = run.toggle(t, still: still)
        let widths = (0..<rounds.count).map { toggleWidths[$0] ?? 0 }
        let i = min(Int(floor(v)), widths.count - 1)
        let j = min(i + 1, widths.count - 1)
        let u = v - floor(v)
        let left = widths.prefix(max(0, i)).reduce(0, +)
        let width = widths[i] + (widths[j] - widths[i]) * u
        return HStack(spacing: 0) {
            ForEach(Array(rounds.enumerated()), id: \.offset) { index, round in
                Button {
                    touch()
                    switchRound(index, byHand: true, at: Self.clock())
                } label: {
                    Text(round.name)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(index == run.round ? K.title : K.soft)
                        .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: K.toggleFor), value: run.round)
                        .frame(height: 30)
                        .padding(.horizontal, 16)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .onGeometryChange(for: Double.self) { $0.size.width } action: { toggleWidths[index] = $0 }
                .accessibilityAddTraits(index == run.round ? [.isSelected] : [])
            }
        }
        .background(alignment: .leading) {
            Capsule()
                .fill(K.thumb)
                .frame(width: width)
                .offset(x: left + widths[i] * u)
                .opacity(toggleWidths.isEmpty ? 0 : 1)
        }
        .padding(3)
        .background(K.pill, in: RoundedRectangle(cornerRadius: 18))
    }

    private var legendPill: some View {
        HStack(spacing: 16) {
            ForEach(Array(legend.enumerated()), id: \.offset) { i, name in
                HStack(spacing: 6) {
                    Circle()
                        .fill(K.color(K.channels(K.legend[i]), 0, 1))
                        .overlay { if i == 2 { Circle().strokeBorder(K.color(K.channels(K.grid), 0, 1), lineWidth: 1) } }
                        .frame(width: 10, height: 10)
                    Text(name)
                        .font(.system(size: 12))
                        .foregroundStyle(K.title)
                        .frame(height: 16)
                }
            }
        }
        .padding(.horizontal, 16)
        .frame(height: 36)
        .background(K.pill, in: Capsule())
    }

    // MARK: Actions

    private func select(_ index: Int, byHand: Bool, at t: Double) {
        guard index != run.selected, index >= 0, index < count else { return }
        run.previous = run.selected
        run.selected = index
        run.selectedAt = t
        run.busyUntil = max(run.busyUntil, t + K.settled * 2)
        if byHand {
            haptic.selectionChanged()
            haptic.prepare()
            onSelect?(index)
        }
    }

    private func switchRound(_ index: Int, byHand: Bool, at t: Double) {
        guard index != run.round else { return }
        run.toggleFrom = run.toggle(t, still: still)
        run.toggleAt = t
        run.fromRound = run.round
        run.round = index
        run.sweptAt = t
        run.busyUntil = max(run.busyUntil, t + K.sweepFor + K.settled * 2)
        if byHand {
            haptic.selectionChanged()
            haptic.prepare()
            onRoundChange?(index)
        }
    }

    private func touch() {
        lastTouch = Self.clock()
        touches += 1
    }

    /// The wedge under a point of the chart, or -1 away from it.
    private func wedge(at point: CGPoint, in size: CGSize) -> Int {
        let dx = point.x - size.width / 2
        let dy = point.y - size.height / 2
        if hypot(dx, dy) > radius(size) + K.tapRoom { return -1 }
        let degrees = atan2(dy, dx) * 180 / .pi + 90
        return Int(floor((degrees.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) / step)) % count
    }

    private func wait(until t: Double) async throws {
        while t > Self.clock() { try await Task.sleep(for: .seconds(t - Self.clock()), tolerance: .zero) }
        try Task.checkCancellation()
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
            let atRest = run.round == 0 && run.selected == 0
            switchRound(0, byHand: false, at: now)
            select(0, byHand: false, at: now)
            var start = now + (atRest ? 0 : K.settle)
            // Wedges first, then rounds, in the order of their times.
            let steps = (K.tour.map { ($0.0, $0.1 % count, false) } + [(K.nextRoundAt, 1 % rounds.count, true), (K.backAt, 0, true)])
                .sorted { $0.0 < $1.0 }
            while true {
                for (at, index, isRound) in steps {
                    try await wait(until: start + at)
                    if isRound { switchRound(index, byHand: false, at: start + at) } else { select(index, byHand: false, at: start + at) }
                }
                start += K.loop
            }
        } catch {}
    }

    // MARK: Drawing

    /// Draws `string` with its baseline at `point.y`, `anchor` of the way along its width at `point.x`.
    private func text(_ c: GraphicsContext, _ string: String, _ font: UIFont, _ color: Color, _ point: CGPoint, anchor: Double) {
        let resolved = c.resolve(Text(string).font(Font(font)).foregroundStyle(color))
        c.draw(resolved, at: CGPoint(x: point.x, y: point.y + CTFontGetDescent(font as CTFont)), anchor: UnitPoint(x: anchor, y: 1))
    }

    private func draw(_ c: GraphicsContext, _ size: CGSize, _ now: Double) {
        guard size.width > 0 else { return }
        let radius = radius(size)
        let cx = size.width / 2
        let cy = size.height / 2
        let unit = radius / 100
        let current = run.selected
        let before = run.previous
        let pushed = still ? K.clamp((now - run.selectedAt) / K.reducedFade) : K.spring(now - run.selectedAt)
        let table = rounds.map { r in
            (0..<count).map { i -> [Double] in
                let a = i < r.likely.count ? r.likely[i] : 0
                let b = a + (i < r.unsure.count ? r.unsure[i] : 0)
                return [a, b, b + (i < r.unlikely.count ? r.unlikely[i] : 0)]
            }
        }
        let target = table[run.round]
        let source = table[run.fromRound]
        let since = now - run.sweptAt
        let stops = K.likelyStops.map { ($0.0, K.channels($0.1)) }

        // Wedges, the selected one last so its push is drawn over its neighbours.
        func drawWedge(_ i: Int) {
            let flow = still ? K.clamp((since - K.sweepFor / 2) / K.reducedFade) : K.spring(since - Double(i) / Double(count) * K.sweepFor)
            let value = (0..<3).map { source[i][$0] + (target[i][$0] - source[i][$0]) * flow }
            let pop = i == current ? pushed : (i == before ? 1 - pushed : 0)
            let a0 = -90 + Double(i) * step
            let middle = (a0 + step / 2) * .pi / 180
            let push = still ? 0 : K.push * pop
            let x = cx + cos(middle) * push
            let y = cy + sin(middle) * push
            // The push may overshoot; colours and sizes follow the settled amount.
            let tone = K.clamp(pop)
            let light = K.brighten * tone
            let alpha = K.dim + (1 - K.dim) * tone

            // The inner sector's colour from its value along the stops.
            var s = 0
            while s < stops.count - 2 && value[0] > stops[s + 1].0 { s += 1 }
            let u = K.clamp((value[0] - stops[s].0) / (stops[s + 1].0 - stops[s].0))
            let inner = K.mix(stops[s].1, stops[s + 1].1, u)

            c.fill(K.sector(x, y, value[1] * unit, value[2] * unit, a0, step), with: .color(K.color(K.channels(K.unlikely), light, alpha)))
            c.fill(K.sector(x, y, value[0] * unit, value[1] * unit, a0, step), with: .color(K.color(K.channels(K.unsure), light, alpha)))
            c.fill(K.sector(x, y, 0, value[0] * unit, a0, step), with: .color(K.color(inner, light, alpha)))

            // The likely share inside the inner sector, larger on the selected wedge; the larger
            // number moves out to where the wedge is wide enough for it.
            let scale = 1 + (K.numberSelectedSize / K.numberSize - 1) * tone
            let at = value[0] * unit * (K.numberAt + K.numberOut * tone)
            let ink = K.mix(K.channels(K.number), K.channels(K.numberSelected), tone)
            var number = c
            number.translateBy(x: x + cos(middle) * at, y: y + sin(middle) * at)
            number.scaleBy(x: scale, y: scale)
            text(number, "\(Int(value[0].rounded()))%", K.numberFont, K.color(ink, 0, 0.82 + 0.18 * tone), CGPoint(x: 0, y: K.numberSize * 0.36), anchor: 0.5)

            // On the selected wedge, the "hard to say" share sits in its band.
            if tone > 0.02 && value[1] - value[0] >= 6 {
                let bandAt = (value[0] + value[1]) / 2 * unit
                text(c, "\(Int((value[1] - value[0]).rounded()))%", K.unsureFont, K.color(K.channels(K.unsureNumber), 0, tone),
                     CGPoint(x: x + cos(middle) * bandAt, y: y + sin(middle) * bandAt + 5), anchor: 0.5)
            }
        }
        for i in 0..<count where i != current && i != before { drawWedge(i) }
        if before >= 0 && before != current { drawWedge(before) }
        drawWedge(current)

        // Reference rings and spokes over the wedges, faint.
        var grid = Path()
        for ring in K.rings { grid.addEllipse(in: CGRect(x: cx - ring * radius, y: cy - ring * radius, width: ring * radius * 2, height: ring * radius * 2)) }
        for i in 0..<count {
            let a = (-90 + Double(i) * step) * .pi / 180
            grid.move(to: CGPoint(x: cx, y: cy))
            grid.addLine(to: CGPoint(x: cx + cos(a) * radius, y: cy + sin(a) * radius))
        }
        c.stroke(grid, with: .color(K.color(K.channels(K.grid), 0, K.gridAlpha)), lineWidth: K.gridWidth)

        // The radar line and its fading trail while a new round sweeps in.
        if !still && since >= 0 && since <= K.sweepFor {
            let p = since / K.sweepFor
            let fade = K.clamp(p / 0.08) * K.clamp((1 - p) / 0.08)
            let head = -90 + 360 * p
            let slice = K.trail / Double(K.trailSteps)
            let white = K.channels(K.white)
            for k in 0..<K.trailSteps {
                c.fill(K.sector(cx, cy, 0, radius, head - Double(k + 1) * slice, slice),
                       with: .color(K.color(white, 0, K.trailAlpha * fade * (1 - Double(k) / Double(K.trailSteps)))))
            }
            let a = head * .pi / 180
            var line = Path()
            line.move(to: CGPoint(x: cx, y: cy))
            line.addLine(to: CGPoint(x: cx + cos(a) * (radius + 6), y: cy + sin(a) * (radius + 6)))
            c.stroke(line, with: .color(K.color(white, 0, 0.9 * fade)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        }

        // Idea names along the circle, turned so that none reads upside down.
        for i in 0..<count {
            let pop = i == current ? pushed : (i == before ? 1 - pushed : 0)
            let middle = -90 + (Double(i) + 0.5) * step
            let a = middle * .pi / 180
            let r = radius + K.labelGap + (still ? 0 : K.push * pop)
            let lower = sin(a) > 0.01
            var label = c
            label.translateBy(x: cx + cos(a) * r, y: cy + sin(a) * r)
            label.rotate(by: .degrees(lower ? middle - 90 : middle + 90))
            text(label, ideas[i], K.labelFont, K.color(K.channels(K.label), pop, 1), CGPoint(x: 0, y: K.labelSize * 0.36), anchor: 0.5)
        }
    }
}
