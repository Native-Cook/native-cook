import SwiftUI

/// "Progress & Planning" panel: tabs, a "STEP 3 OF 5 · 40% Complete" row over a segmented bar, and
/// a checklist that keeps the current step on its middle row. Completing a step sweeps its segment
/// full, moves the outlined segment (with its circling light) on, ticks the row and scrolls the list.
struct StepPlannerProgress: View {
    /// Share of the steps completed, 0…1. Leave nil to show the reference's 2 of 5 (40%).
    var progress: Double? = nil
    /// Plays by itself: the card's parts arrive one after another, two steps complete, hold, start over.
    var autoPlay = false
    var title = "Progress & Planning"
    var steps = ["Create Account", "Business Details", "Payment Method", "Team Setup", "Final Review"]
    var tabs = ["Setup", "Timeline", "Goals"]
    var label = "Setup progress"
    var onTabChange: (Int) -> Void = { _ in }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var tab = 0
    @State private var pressedTab = 0
    /// Steps done while controlled, a fraction while one is completing.
    @State private var value = Tween(0)
    @State private var pillX = Tween(tabFrames[0].x)
    @State private var pillW = Tween(tabFrames[0].w)
    @State private var press = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0
    /// When the autoPlay run started; the circling light counts from here too.
    @State private var start = Date.now
    @State private var epoch = Date.now

    private var count: Int { steps.count }
    private var playing: Bool { autoPlay && progress == nil && !reduceMotion }
    private var rest: Int { min(Metrics.restDone, count) }
    private var exitAt: Double { Clock.stepFirst + Double(rest - 1) * Clock.stepEvery + Clock.stepLength + Clock.hold }
    private var loop: Double { exitAt + Clock.exit + Clock.gap }

    private func clock(at date: Date) -> Double {
        max(date.timeIntervalSince(start), 0).truncatingRemainder(dividingBy: loop)
    }

    /// Steps done at `date`.
    private func done(at date: Date) -> Double {
        if progress != nil { return value.value(at: date) }
        guard playing else { return Double(rest) }
        let t = clock(at: date)
        var d = 0.0
        for k in 0..<rest {
            let begin = Clock.stepFirst + Double(k) * Clock.stepEvery
            d += easeInOut(seg(t, begin, begin + Clock.stepLength))
        }
        return d
    }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            Canvas { context, _ in
                draw(context, date: date)
            }
            .padding(-Metrics.bleed)
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .overlay(alignment: .topLeading) {
            // One clear button per tab, over the labels the canvas draws.
            ForEach(tabFrames.indices, id: \.self) { index in
                Button {
                    select(index)
                } label: {
                    Color.clear
                }
                .buttonStyle(TabPress { pressed in
                    if pressed { pressedTab = index }
                    press.retarget(pressed ? 1 : 0, duration: 0.13, curve: Ease.out)
                    settle(0.13)
                })
                .frame(width: tabFrames[index].w, height: Metrics.pillH)
                .offset(x: tabFrames[index].x, y: Metrics.pillY)
                .accessibilityLabel(index < tabs.count ? tabs[index] : "")
                .accessibilityAddTraits(index == tab ? .isSelected : [])
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(label)
        .accessibilityValue("\(Int((min(max(progress ?? Double(rest) / Double(max(count, 1)), 0), 1) * 100).rounded())) percent")
        .onChange(of: progress, initial: true) { old, progress in
            guard let progress else { return }
            // The first call only reports the starting value: show it without a run-up.
            let first = old == progress
            value.retarget(
                min(max(progress, 0), 1) * Double(count),
                duration: first ? 0 : reduceMotion ? 0.2 : 0.7, curve: Ease.out
            )
            settle(0.7)
        }
        .task(id: settleToken) {
            try? await Task.sleep(for: .seconds(0.8))
            if !Task.isCancelled { settling = false }
        }
    }

    private func settle(_ seconds: Double) {
        settling = true
        settleToken += 1
    }

    private func select(_ index: Int) {
        let frame = tabFrames[index]
        let glide = reduceMotion ? 0.15 : 0.32
        pillX.retarget(frame.x, duration: glide, curve: Ease.out)
        pillW.retarget(frame.w, duration: glide, curve: Ease.out)
        tab = index
        onTabChange(index)
        // Choosing a tab also restarts the demo run.
        start = .now
        settle(glide)
    }

    /// Opacity and rise of the card's `part`-th block: they arrive one after another when a run starts.
    private func entrance(_ context: GraphicsContext, part: Int, t: Double) -> GraphicsContext {
        guard playing else { return context }
        let begin = Clock.enterFirst + Clock.enterStagger * Double(part)
        let arrived = easeOut(seg(t, begin, begin + Clock.enterLength))
        var copy = context
        copy.opacity = arrived * (1 - seg(t, exitAt, exitAt + Clock.exit))
        copy.translateBy(x: 0, y: 10 * (1 - arrived))
        return copy
    }

    private func draw(_ context: GraphicsContext, date: Date) {
        let o = Metrics.bleed
        let w = Metrics.width
        let h = Metrics.height
        let t = clock(at: date)
        let d = done(at: date)
        let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
        let current = min(Int(d + 0.001), count - 1)
        func at(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: o + x, y: o + y) }

        var shadow = context
        shadow.addFilter(.blur(radius: 14))
        shadow.fill(
            Path(roundedRect: CGRect(x: o + 8, y: o + 16, width: w - 16, height: h - 16), cornerRadius: Metrics.radius),
            with: .color(.black.opacity(0.55))
        )
        context.fill(
            Path(roundedRect: CGRect(x: o, y: o, width: w, height: h), cornerRadius: Metrics.radius),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x141416), Color(hex: 0x1c1d22)]),
                startPoint: CGPoint(x: 0, y: o), endPoint: CGPoint(x: 0, y: o + h)
            )
        )
        context.stroke(
            Path(roundedRect: CGRect(x: o + 0.5, y: o + 0.5, width: w - 1, height: h - 1), cornerRadius: Metrics.radius - 0.5),
            with: .color(.white.opacity(0.05)), lineWidth: 1
        )

        let head = entrance(context, part: 0, t: t)
        head.draw(
            Text(verbatim: title).font(.system(size: 17.5, weight: .semibold)).foregroundStyle(Color(hex: 0xdcd8d3)),
            at: at(20, 32.5), anchor: .leading
        )

        drawTabs(entrance(context, part: 1, t: t), date: date)

        let bar = entrance(context, part: 2, t: t)
        bar.draw(
            Text(verbatim: "STEP \(current + 1) OF \(count)")
                .font(.system(size: 11.5, weight: .medium).monospacedDigit()).kerning(1)
                .foregroundStyle(Color(hex: 0xe6e6ea)),
            at: at(21, Metrics.stepY), anchor: .leading
        )
        let complete = bar.resolve(
            Text(verbatim: "Complete").font(.system(size: 13.5)).foregroundStyle(Color(hex: 0xeceef2))
        )
        bar.draw(complete, at: at(w - Metrics.inset, Metrics.stepY), anchor: .trailing)
        let completeWidth = complete.measure(in: CGSize(width: 200, height: 40)).width
        // Computed here, inside the timeline, so the percentage counts with the sweep.
        bar.draw(
            Text(verbatim: "\(Int((d / Double(max(count, 1)) * 100).rounded()))%")
                .font(.system(size: 13.5, weight: .bold).monospacedDigit()).foregroundStyle(green),
            at: at(w - Metrics.inset - completeWidth - 4, Metrics.stepY), anchor: .trailing
        )
        for index in 0..<count {
            drawSegment(bar, index: index, done: d, t: t, time: time)
        }

        let list = entrance(context, part: 3, t: t)
        let listRect = CGRect(x: o + Metrics.inset, y: o + Metrics.listY, width: Metrics.innerW, height: Metrics.listH)
        let listShape = Path(roundedRect: listRect, cornerRadius: Metrics.listR)
        list.fill(
            listShape,
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x282a36), Color(hex: 0x22242e)]),
                startPoint: CGPoint(x: 0, y: listRect.minY), endPoint: CGPoint(x: 0, y: listRect.maxY)
            )
        )
        var rows = list
        rows.clip(to: listShape)
        for index in 0..<count {
            drawRow(rows, index: index, done: d)
        }

        let footer = entrance(context, part: 4, t: t)
        let name = steps.indices.contains(current) ? steps[current] : ""
        let footerName = footer.resolve(
            Text(verbatim: name.prefix(1) + name.dropFirst().lowercased())
                .font(.system(size: 15.5, weight: .semibold)).foregroundStyle(Color(hex: 0xe0dcd6))
        )
        footer.draw(footerName, at: at(20, Metrics.footerY), anchor: .leading)
        let nameWidth = footerName.measure(in: CGSize(width: 240, height: 40)).width
        let badgeLabel = footer.resolve(
            Text(verbatim: "CURRENT").font(.system(size: 9.5, weight: .semibold)).kerning(0.6)
                .foregroundStyle(Color(hex: 0x747884))
        )
        let badgeWidth = badgeLabel.measure(in: CGSize(width: 120, height: 40)).width + 16
        let badge = capsule(CGRect(x: o + 20 + nameWidth + 9, y: o + Metrics.footerY - 9, width: badgeWidth, height: 18))
        footer.fill(badge, with: .color(Color(hex: 0x1f2127)))
        footer.stroke(
            capsule(CGRect(x: o + 20 + nameWidth + 9.5, y: o + Metrics.footerY - 8.5, width: badgeWidth - 1, height: 17)),
            with: .color(Color(hex: 0x383a44)), lineWidth: 1
        )
        footer.draw(badgeLabel, at: at(20 + nameWidth + 9 + badgeWidth / 2, Metrics.footerY), anchor: .center)
    }

    /// The tab strip: its sliding pill, then a dashboard grid, a calendar and a target with their labels.
    private func drawTabs(_ context: GraphicsContext, date: Date) {
        let o = Metrics.bleed
        let cy = o + Metrics.tabsY + Metrics.tabsH / 2
        context.fill(
            capsule(CGRect(x: o + Metrics.inset, y: o + Metrics.tabsY, width: Metrics.innerW, height: Metrics.tabsH)),
            with: .color(Color(hex: 0x20222b))
        )
        // The selected tab's pill slides and resizes to the tab that was chosen.
        context.fill(
            capsule(CGRect(
                x: o + pillX.value(at: date), y: o + Metrics.pillY, width: pillW.value(at: date), height: Metrics.pillH
            )),
            with: .color(Color(hex: 0x3a3e4b))
        )

        for (index, frame) in tabFrames.enumerated() {
            let selected = index == tab
            let tint = Color(hex: selected ? 0xf0f2f6 : 0xb3b7c2)
            let pressed = index == pressedTab ? press.value(at: date) : 0
            let item = context.scaled(1 - 0.03 * pressed, about: CGPoint(x: o + frame.x + frame.w / 2, y: cy))
            var icon = item
            icon.translateBy(x: o + frame.icon, y: cy)
            switch index {
            case 0:
                for tile in [
                    CGRect(x: -5.8, y: -5.8, width: 5, height: 6.6), CGRect(x: 0.8, y: -5.8, width: 5, height: 4),
                    CGRect(x: -5.8, y: 2.4, width: 5, height: 3.4), CGRect(x: 0.8, y: -0.2, width: 5, height: 6),
                ] {
                    icon.fill(Path(roundedRect: tile, cornerRadius: 1.3), with: .color(tint))
                }
            case 1:
                icon.stroke(
                    Path(roundedRect: CGRect(x: -5.4, y: -4.6, width: 10.8, height: 10), cornerRadius: 2),
                    with: .color(tint), lineWidth: 1.3
                )
                icon.fill(
                    Path(
                        roundedRect: CGRect(x: -5.4, y: -4.6, width: 10.8, height: 3.2),
                        cornerRadii: RectangleCornerRadii(topLeading: 2, topTrailing: 2)
                    ),
                    with: .color(tint)
                )
                var pegs = Path()
                for x in [-2.6, 2.6] {
                    pegs.move(to: CGPoint(x: x, y: -6.4))
                    pegs.addLine(to: CGPoint(x: x, y: -4.4))
                }
                icon.stroke(pegs, with: .color(tint), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
            default:
                var target = Path()
                target.addArc(center: .zero, radius: 5.6, startAngle: .degrees(6), endAngle: .degrees(264), clockwise: false)
                target.move(to: CGPoint(x: 2.5, y: 0.4))
                target.addArc(center: .zero, radius: 2.5, startAngle: .degrees(9), endAngle: .degrees(261), clockwise: false)
                target.move(to: .zero)
                target.addLine(to: CGPoint(x: 5, y: -5))
                target.move(to: CGPoint(x: 3, y: -5.6))
                target.addLine(to: CGPoint(x: 5.6, y: -5.6))
                target.addLine(to: CGPoint(x: 5.6, y: -3))
                icon.stroke(target, with: .color(tint), style: StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round))
            }
            item.draw(
                Text(verbatim: index < tabs.count ? tabs[index] : "")
                    .font(.system(size: 14, weight: selected ? .bold : .regular))
                    .foregroundStyle(Color(hex: selected ? 0xf0f2f6 : 0xc6c9d2)),
                at: CGPoint(x: o + frame.text, y: cy), anchor: .leading
            )
        }
    }

    /// One segment of the step bar: grey, outlined with a circling light while current, or filled.
    private func drawSegment(_ context: GraphicsContext, index: Int, done: Double, t: Double, time: Double) {
        let h = Metrics.segH
        let w = (Metrics.innerW - Metrics.segGap * Double(count - 1)) / Double(count)
        let x = Metrics.bleed + Metrics.inset + (w + Metrics.segGap) * Double(index)
        let y = Metrics.bleed + Metrics.segY
        let shape = capsule(CGRect(x: x, y: y, width: w, height: h))
        let outline = capsule(CGRect(x: x + 0.6, y: y + 0.6, width: w - 1.2, height: h - 1.2))

        // The segments grow in from the left one after another when a run starts.
        var context = context
        if playing {
            let begin = Clock.waveFirst + Clock.waveStagger * Double(index)
            context.translateBy(x: x, y: y)
            context.scaleBy(x: max(0, easeOutBack(seg(t, begin, begin + Clock.waveLength))), y: 1)
            context.translateBy(x: -x, y: -y)
        }
        context.fill(shape, with: .color(Color(hex: 0x2c3039)))

        let current = seg(done - Double(index), -0.25, 0) * (1 - seg(done - Double(index), 0.8, 1))
        if current > 0 {
            var lit = context
            lit.opacity *= current
            lit.fill(shape, with: .color(.rgba(122, 250, 155, 0.14)))
            var glow = lit
            glow.addFilter(.blur(radius: 3))
            glow.stroke(outline, with: .color(.rgba(122, 250, 155, 0.4)), lineWidth: 3)
            lit.stroke(outline, with: .color(.rgba(122, 250, 155, 0.8)), lineWidth: 1.2)
            if !reduceMotion {
                let perimeter = 2 * (w - h) + .pi * h
                lit.stroke(
                    outline, with: .color(Color(hex: 0xeafff0)),
                    style: StrokeStyle(
                        lineWidth: 1.4, lineCap: .round, dash: [perimeter * 0.16, perimeter * 0.84], dashPhase: -time * 42
                    )
                )
            }
        }

        // 0…1 while this step completes: the fill sweeps across from the left.
        let filled = min(1, max(0, done - Double(index)))
        guard filled > 0 else { return }
        var fill = context
        fill.opacity *= min(1, filled * 8)
        let sweep = Path(CGRect(x: x, y: y - 20, width: w * filled, height: h + 40))
        var glow = fill
        glow.clip(to: sweep)
        glow.addFilter(.blur(radius: 5))
        glow.fill(shape, with: .color(.rgba(122, 250, 155, 0.55)))
        var inside = fill
        inside.clip(to: shape)
        var solid = inside
        solid.clip(to: sweep)
        solid.fill(shape, with: .color(green))
        // A glint rides the leading edge of the sweep.
        if filled < 1 {
            var glint = inside
            glint.opacity *= sin(.pi * filled)
            glint.addFilter(.blur(radius: 4))
            glint.fill(disc(CGPoint(x: x + w * filled, y: y + h / 2), 7), with: .color(.white))
        }
    }

    /// One step of the checklist: ticked, current, or still to come (scalloped).
    private func drawRow(_ context: GraphicsContext, index: Int, done: Double) {
        let o = Metrics.bleed
        let y = o + Metrics.listY + Metrics.rowFirst + Metrics.rowPitch * Double(index) - scrollOf(done, count: count)
        let c = CGPoint(x: o + Metrics.rowX, y: y)
        let r = Metrics.rowR
        // Rows scrolled out of the list need no drawing.
        guard y > o + Metrics.listY - Metrics.rowPitch, y < o + Metrics.listY + Metrics.listH + Metrics.rowPitch else { return }

        if index < count - 1 {
            var line = Path()
            line.move(to: CGPoint(x: c.x, y: y + r + 4))
            line.addLine(to: CGPoint(x: c.x, y: y + Metrics.rowPitch - r - 4))
            context.stroke(line, with: .color(Color(hex: 0x30323d)), lineWidth: 1)
        }
        let upcoming = seg(Double(index) - done, 0.3, 0.9)
        if upcoming > 0 {
            context.stroke(
                disc(c, r + 0.4), with: .color(Color(hex: 0x17181d).opacity(upcoming)),
                style: StrokeStyle(lineWidth: 2.4, dash: [2.6, 2.27])
            )
        }
        context.fill(disc(c, r), with: .color(Color(hex: 0x17181d)))
        context.stroke(disc(c, r - 0.5), with: .color(.white.opacity(0.06)), lineWidth: 1)

        // 0…1 while this step completes.
        let ticked = min(1, max(0, done - Double(index)))
        // A ring spreads from the circle as its tick lands.
        if ticked > 0.25, ticked < 1 {
            let spread = seg(ticked, 0.25, 1)
            context.stroke(disc(c, r + 10 * easeOut(spread)), with: .color(green.opacity(0.7 * (1 - spread))), lineWidth: 1.4)
        }
        let shown = seg(ticked, 0.25, 0.5)
        if shown > 0 {
            var tick = context
            tick.opacity *= shown
            tick.stroke(disc(c, r - 0.5), with: .color(.rgba(122, 250, 155, 0.28)), lineWidth: 1)
            tick = tick.scaled(0.4 + 0.6 * easeOutBack(seg(ticked, 0.25, 0.85)), about: c)
            var glow = tick
            glow.addFilter(.blur(radius: 4))
            glow.fill(disc(c, 8), with: .color(.rgba(122, 250, 155, 0.5)))
            tick.fill(disc(c, 6.7), with: .color(green))
            var check = Path()
            check.move(to: CGPoint(x: c.x - 3.1, y: c.y + 0.2))
            check.addLine(to: CGPoint(x: c.x - 1, y: c.y + 2.3))
            check.addLine(to: CGPoint(x: c.x + 3.2, y: c.y - 2.1))
            tick.stroke(
                check.trimmedPath(from: 0, to: seg(ticked, 0.55, 1)), with: .color(Color(hex: 0x10311a)),
                style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round)
            )
        }

        // The label: grey once done, white while current, dim while still to come.
        context.draw(
            Text(verbatim: steps[index]).font(.system(size: 14)).foregroundStyle(labelColor(done - Double(index))),
            at: CGPoint(x: o + Metrics.rowTextX, y: y), anchor: .leading
        )
    }
}

/// Reports presses of a tab's clear button; the canvas underneath draws the feedback.
private struct TabPress: ButtonStyle {
    let onPress: (Bool) -> Void

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Capsule())
            .onChange(of: configuration.isPressed) { _, pressed in onPress(pressed) }
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

private enum Metrics {
    static let width = 330.0
    static let height = 374.0
    static let radius = 26.0
    /// The canvas overflows the card by this much on every side to fit its shadow.
    static let bleed = 36.0
    static let inset = 20.5
    static let innerW = width - inset * 2
    static let tabsY = 61.0
    static let tabsH = 39.0
    static let pillY = 65.0
    static let pillH = 31.0
    static let stepY = 131.0
    static let segY = 152.0
    static let segH = 7.3
    static let segGap = 3.3
    static let listY = 181.5
    static let listH = 137.0
    static let listR = 20.0
    static let rowFirst = 23.0
    static let rowPitch = 45.0
    static let rowX = 43.7
    static let rowR = 12.0
    static let rowTextX = 70.0
    static let rowsShown = 3
    static let footerY = 350.0
    /// The reference rests with two of five steps done.
    static let restDone = 2
}

/// Left edge and width of each tab's pill, and where its icon and label sit.
private let tabFrames: [(x: Double, w: Double, icon: Double, text: Double)] = [
    (25, 86, 46.5, 59),
    (112, 98, 131, 144),
    (213, 91.5, 233, 246),
]

/// The autoPlay run is one clock in seconds: the parts arrive, each step completes, a hold, a fade.
private enum Clock {
    static let enterFirst = 0.1
    static let enterStagger = 0.09
    static let enterLength = 0.5
    static let waveFirst = 0.45
    static let waveStagger = 0.07
    static let waveLength = 0.4
    static let stepFirst = 1.5
    static let stepEvery = 1.5
    static let stepLength = 0.7
    static let hold = 2.6
    static let exit = 0.4
    static let gap = 0.3
}

private let green = Color(hex: 0x7afa9b)

private func seg(_ t: Double, _ from: Double, _ to: Double) -> Double {
    min(1, max(0, (t - from) / (to - from)))
}

private func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }

private func easeInOut(_ x: Double) -> Double {
    x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
}

private func easeOutBack(_ x: Double) -> Double {
    let c = 1.7
    return 1 + (c + 1) * pow(x - 1, 3) + c * pow(x - 1, 2)
}

private func disc(_ center: CGPoint, _ radius: Double) -> Path {
    Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
}

/// How far the checklist has scrolled: the current step stays on the middle row.
private func scrollOf(_ done: Double, count: Int) -> Double {
    min(max(done - 1, 0), Double(max(count - Metrics.rowsShown, 0))) * Metrics.rowPitch
}

/// Label colour for a row `ahead` steps behind (negative) or past (positive) the current one.
private func labelColor(_ ahead: Double) -> Color {
    let dim = (77.0, 80.0, 96.0)
    let white = (233.0, 234.0, 240.0)
    let grey = (138.0, 143.0, 162.0)
    func mix(_ a: (Double, Double, Double), _ b: (Double, Double, Double), _ f: Double) -> Color {
        .rgba(a.0 + (b.0 - a.0) * f, a.1 + (b.1 - a.1) * f, a.2 + (b.2 - a.2) * f, 1)
    }
    if ahead < 0 { return mix(dim, white, seg(ahead, -0.4, 0)) }
    return mix(white, grey, seg(ahead, 0.6, 1))
}

#Preview {
    ZStack {
        Color(hex: 0x0a0a0a).ignoresSafeArea()
        StepPlannerProgress(autoPlay: true)
    }
}
