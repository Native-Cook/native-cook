import SwiftUI

private typealias Chart = StepsBarsToSpikesChart
private typealias Figures = StepsBarsToSpikesData

private enum S {
    static let fieldTop = Color(hex: 0x2b1bff)
    static let fieldBottom = Color(hex: 0x3a2bff)
    static let sheet = Color(hex: 0x101012)
    static let ink = Color.white
    static let fieldMute = Color.white.opacity(0.45)
    static let fieldFaint = Color.white.opacity(0.3)
    static let mute = Color(hex: 0x8a8a8e)
    static let down = Color(hex: 0xff5a4e)
    static let up = Color(hex: 0x3ddc97)
    static let switchTrack = Color(hex: 0x1e1e22)
    static let buttonDim = Color(hex: 0x1e1e22)
    static let navTrack = Color(hex: 0x18181b)
    // The big number is white text tinted by this gradient laid over it, which leaves the sheet
    // around the digits as it is.
    static let numberTint = [Color(hex: 0x7c8cff), Color(hex: 0xc6ccff)]

    // Layout, in points.
    static let margin = 20.0
    // The blue field takes this share of the screen; the sheet over the rest has rounded top corners.
    static let fieldShare = 0.56
    static let sheetRadius = 28.0
    static let axisWidth = 34.0
    static let xLabels = 22.0
    static let ticks = 9
    static let key = 32.0
    static let navHeight = 48.0

    // Motion.
    static let thumb = Animation.interpolatingSpring(mass: 0.7, stiffness: 260, damping: 20)
    // Labels fade out over 0.15 s and the new ones in over 0.25 s, the chart's after 0.15 s.
    static let fadeOut = 0.15
    static let fadeIn = 0.25
    static let fadeInDelay = 0.15

    // The autoPlay timeline, in seconds from the start of each loop.
    static let loop = 8.0
    static let switches: [(at: Double, view: Int)] = [(1.0, 1), (2.8, 2), (5.8, 0)]
    // A finger drawn over the month's spikes, by bar.
    static let drag = (down: 4.0, from: 1, to: 11, start: 4.1, end: 5.0, up: 5.25)
    // Seconds after the last touch before the chart returns to the days and the loop starts again.
    static let userHold = 3.0

    // SF Symbols draw larger than the icon font at the same size.
    static let symbolScale = 0.82

    /// Reanimated's default timing curve, which its fades use.
    static func quad(_ t: Double) -> Double {
        let x = min(1, max(0, t))
        return x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
    }

    /// The times within a loop at which the drawn finger moves onto each next bar.
    static let fingerSteps: [(at: Double, bar: Int)] = {
        var steps = [(drag.down, drag.from)]
        let span = Double(drag.to - drag.from)
        for bar in (drag.from + 1)...drag.to {
            // Where the smoothstep glide rounds up to this bar: its inverse, in closed form.
            let y = (Double(bar - drag.from) - 0.5) / span
            let x = 0.5 - sin(asin(1 - 2 * y) / 3)
            steps.append((drag.start + (drag.end - drag.start) * x, bar))
        }
        steps.append((drag.up, -1))
        return steps
    }()
}

/// A steps screen: bars on an electric-blue field over a black sheet with the total and three
/// stats. The D / W / M switch sinks the bars and grows the next set in a stagger, from seven soft
/// bars to a month of hairline spikes, while every number rolls to its new value. Dragging over the
/// bars lights the one under the finger and shows its steps.
struct StepsBarsToSpikes: View {
    var views = StepsBarsToSpikesData.views
    var unitLabel = "Steps"
    /// Switches day, week and month by itself every LOOP seconds, with a drag over the month.
    var autoPlay = false
    var onViewChange: ((StepsBarsToSpikesView, Int) -> Void)? = nil
    /// Called with a bar's index and steps while a finger is on it.
    var onSelect: ((Int, Double) -> Void)? = nil

    static let loop = S.loop

    @Environment(\.accessibilityReduceMotion) private var still
    @State private var viewIndex = 0
    @State private var fromIndex = 0
    @State private var switchAt = Date.distantPast
    @State private var switching = false
    @State private var picked = -1
    @State private var holding = false
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var stepHaptic = UISelectionFeedbackGenerator()
    @State private var pressHaptic = UIImpactFeedbackGenerator(style: .light)

    private var view: StepsBarsToSpikesView { views[viewIndex] }
    private var shownSteps: Double { picked >= 0 && picked < view.values.count ? view.values[picked] : view.total }

    var body: some View {
        GeometryReader { outer in
            let insets = outer.safeAreaInsets
            GeometryReader { proxy in
                let size = proxy.size
                let fieldHeight = size.height * S.fieldShare
                ZStack(alignment: .topLeading) {
                    LinearGradient(colors: [S.fieldTop, S.fieldBottom], startPoint: .top, endPoint: .bottom)
                        .frame(width: size.width, height: fieldHeight + S.sheetRadius)
                    TimelineView(.animation(paused: !switching)) { timeline in
                        field(top: insets.top, since: since(switching ? timeline.date : .distantFuture))
                    }
                    .frame(width: size.width, height: fieldHeight)
                    sheet(width: size.width, bottom: insets.bottom)
                        .frame(width: size.width, height: size.height - fieldHeight)
                        .offset(y: fieldHeight)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .background(S.sheet.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            stepHaptic.prepare()
            pressHaptic.prepare()
        }
        .task(id: switchAt) {
            do {
                try await wait(switchAt.addingTimeInterval(max(Chart.switchFor, S.fadeInDelay + S.fadeIn)))
                switching = false
            } catch {}
        }
        .task(id: touches) { await autoplay() }
    }

    private func since(_ date: Date) -> Double {
        min(Chart.switchFor, date.timeIntervalSince(switchAt))
    }

    // MARK: Field

    private func field(top: Double, since: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                ZStack(alignment: .topLeading) {
                    if fromIndex != viewIndex { note(views[fromIndex]).opacity(1 - S.quad(since / S.fadeOut)) }
                    note(view).opacity(fromIndex != viewIndex ? S.quad(since / S.fadeIn) : 1)
                }
                Spacer(minLength: 8)
                chip
            }
            .frame(height: 44, alignment: .top)
            chart(since: since)
                .padding(.top, 8)
                .padding(.bottom, 12)
        }
        .padding(.horizontal, S.margin)
        .padding(.top, top + 8)
    }

    private func note(_ view: StepsBarsToSpikesView) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 5) {
                Image(systemName: "minus.circle.fill")
                    .font(.system(size: 15 * S.symbolScale))
                    .frame(width: 15, height: 15)
                Text(Figures.grouped(view.note))
                    .font(.system(size: 17, weight: .semibold))
                    .monospacedDigit()
                    .frame(height: 22)
            }
            .foregroundStyle(S.fieldMute)
            .padding(.leading, 10)
            Text(view.noteLabel)
                .font(.system(size: 11))
                .foregroundStyle(S.fieldFaint)
                .frame(height: 14)
                .padding(.leading, 30)
        }
        .fixedSize()
    }

    private var chip: some View {
        let change = Int(Figures.round((view.total - view.previous) / max(1, view.previous) * 100))
        let color = change < 0 ? S.down : S.up
        return HStack(spacing: 4) {
            Image(systemName: change < 0 ? "arrow.down.circle.fill" : "arrow.up.circle.fill")
                .font(.system(size: 13 * S.symbolScale))
                .foregroundStyle(color)
                .frame(width: 13, height: 13)
            Text("\(abs(change))%")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(color)
            Text("\(change < 0 ? "lower" : "higher") \(view.changeLabel)")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(S.ink)
        }
        .lineLimit(1)
        .padding(.horizontal, 12)
        .frame(height: 30)
        .background(Capsule().fill(Color.white.opacity(0.12)))
    }

    private func makeBox(_ size: CGSize) -> Chart.Box {
        Chart.Box(left: S.axisWidth, width: max(0, size.width - S.axisWidth), base: size.height - S.xLabels, height: size.height - S.xLabels - 12)
    }

    private func chart(since: Double) -> some View {
        GeometryReader { proxy in
            let box = makeBox(proxy.size)
            Canvas { c, size in
                Chart.draw(c, box: box, views: views, from: fromIndex, to: viewIndex, since: since, picked: picked)
                // Labels of the view switched from fade out, the new ones fade in after them.
                if fromIndex != viewIndex {
                    var old = c
                    old.opacity = 1 - S.quad(since / S.fadeOut)
                    if old.opacity > 0 { labels(old, views[fromIndex], box) }
                }
                var new = c
                new.opacity = fromIndex != viewIndex ? S.quad((since - S.fadeInDelay) / S.fadeIn) : 1
                if new.opacity > 0 { labels(new, view, box) }
                if let badge = view.badge {
                    var faded = c
                    faded.opacity = viewIndex == 2 ? min(1, since / 0.3) : (fromIndex == 2 ? max(0, 1 - since / 0.2) : 0)
                    let text = Text(badge).font(.system(size: 12, weight: .semibold)).tracking(0.6).foregroundStyle(Color.white.opacity(0.7))
                    faded.draw(text, at: CGPoint(x: size.width, y: 8), anchor: .trailing)
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let bar = barAt(value.location.x, box)
                        if !holding {
                            holding = true
                            setFinger(bar, hand: true)
                            touched()
                        } else {
                            setFinger(bar, hand: true)
                        }
                    }
                    .onEnded { _ in
                        holding = false
                        setFinger(-1, hand: false)
                        touched()
                    }
            )
        }
        .accessibilityElement()
        .accessibilityLabel("\(unitLabel) by \(view.key == "D" ? "day" : view.key == "W" ? "week" : "day of the month")")
    }

    private func labels(_ c: GraphicsContext, _ view: StepsBarsToSpikesView, _ box: Chart.Box) {
        for i in 0..<S.ticks {
            let share = Double(S.ticks - i) / Double(S.ticks)
            let text = Text(Figures.axisLabel(view.max * share)).font(.system(size: 10)).monospacedDigit().foregroundStyle(S.fieldMute)
            c.draw(text, at: CGPoint(x: 0, y: box.base - box.height * share), anchor: .leading)
        }
        let step = box.width / Double(view.values.count)
        for (i, label) in view.labels.enumerated() where !label.isEmpty {
            let text = Text(label).font(.system(size: 11)).foregroundStyle(S.fieldMute)
            c.draw(text, at: CGPoint(x: box.left + step * (Double(i) + 0.5), y: box.base + 6 + 7), anchor: .center)
        }
    }

    private func barAt(_ x: Double, _ box: Chart.Box) -> Int {
        if box.width <= 0 { return -1 }
        let count = view.values.count
        return min(count - 1, max(0, Int(floor((x - box.left) / box.width * Double(count)))))
    }

    // MARK: Sheet

    private func sheet(width: Double, bottom: Double) -> some View {
        let stats = Figures.stats(shownSteps)
        let columns = width - S.margin * 2
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                monogram
                Spacer()
                viewSwitch
            }
            VStack(alignment: .leading, spacing: 0) {
                StepsBarsToSpikesRoller(text: Figures.grouped(shownSteps), size: 60, lineHeight: 68, weight: .heavy, tracking: -1.5, still: still)
                    // Rolling digits draw in layers of their own, which a blend over the sheet misses:
                    // the gradient is kept where the digits are inside one flattened layer instead.
                    .overlay {
                        LinearGradient(colors: S.numberTint, startPoint: .topLeading, endPoint: UnitPoint(x: 0.4, y: 1))
                            .blendMode(.sourceAtop)
                            .allowsHitTesting(false)
                    }
                    .drawingGroup()
                Text(picked >= 0 && picked < view.names.count ? "\(unitLabel) · \(view.names[picked])" : unitLabel)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(S.ink)
                    .frame(height: 18)
            }
            .padding(.top, 16)
            HStack(alignment: .top, spacing: 0) {
                stat(stats.distance, "km", "Distance").frame(width: columns * 1 / 3, alignment: .leading)
                stat(stats.calories, "kcal", "Calories").frame(width: columns * 1.35 / 3, alignment: .leading)
                stat(stats.floors, "", "Floors").frame(width: columns * 0.65 / 3, alignment: .leading)
            }
            .padding(.top, 22)
            Spacer(minLength: 0)
            nav.frame(maxWidth: .infinity)
        }
        .padding(.horizontal, S.margin)
        .padding(.top, 20)
        .padding(.bottom, max(bottom, 12))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(UnevenRoundedRectangle(topLeadingRadius: S.sheetRadius, topTrailingRadius: S.sheetRadius, style: .continuous).fill(S.sheet))
    }

    /// A generic round monogram standing in for the app's mark.
    private var monogram: some View {
        Text("GO")
            .font(.system(size: 14, weight: .black))
            .tracking(-0.5)
            .foregroundStyle(S.ink)
            .frame(width: 48, height: 30)
            .overlay(Capsule().strokeBorder(S.ink, lineWidth: 2.5))
    }

    private var viewSwitch: some View {
        ZStack(alignment: .leading) {
            Circle()
                .fill(S.ink)
                .frame(width: S.key, height: S.key)
                .offset(x: Double(viewIndex) * S.key)
                .animation(still ? nil : S.thumb, value: viewIndex)
            HStack(spacing: 0) {
                ForEach(Array(views.enumerated()), id: \.offset) { index, item in
                    Button {
                        touched()
                        choose(index, auto: false)
                    } label: {
                        Text(item.key)
                            .font(.system(size: 12, weight: index == viewIndex ? .heavy : .semibold))
                            .foregroundStyle(index == viewIndex ? S.sheet : S.mute)
                            .frame(width: S.key, height: S.key)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(index == viewIndex ? [.isSelected] : [])
                }
            }
        }
        .padding(3)
        .background(Capsule().fill(S.switchTrack))
    }

    private func stat(_ value: String, _ unit: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .bottom, spacing: 0) {
                StepsBarsToSpikesRoller(text: value, size: 22, lineHeight: 26, weight: .bold, tracking: -0.5, still: still)
                if !unit.isEmpty {
                    Text(unit)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(S.ink)
                        .frame(height: 18)
                        .padding(.leading, 2)
                        .padding(.bottom, 2)
                }
            }
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(S.mute)
                .frame(height: 14)
                .padding(.top, 2)
        }
    }

    private var nav: some View {
        HStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "shoeprints.fill")
                    .font(.system(size: 18 * S.symbolScale))
                    .frame(width: 18, height: 18)
                Text(unitLabel).font(.system(size: 14, weight: .semibold))
            }
            .foregroundStyle(S.sheet)
            .padding(.horizontal, 24)
            .frame(height: S.navHeight)
            .background(Capsule().fill(S.ink))
            roundButton("sparkles")
            roundButton("gearshape.fill")
        }
        .padding(6)
        .background(Capsule().fill(S.navTrack))
        .accessibilityHidden(true)
    }

    private func roundButton(_ symbol: String) -> some View {
        Image(systemName: symbol)
            .font(.system(size: 17 * S.symbolScale))
            .foregroundStyle(S.mute)
            .frame(width: S.navHeight, height: S.navHeight)
            .background(Circle().fill(S.buttonDim))
    }

    // MARK: Behaviour

    private func choose(_ index: Int, auto: Bool) {
        if index == viewIndex { return }
        if !auto {
            pressHaptic.impactOccurred()
            pressHaptic.prepare()
        }
        fromIndex = viewIndex
        viewIndex = index
        onViewChange?(views[index], index)
        // Under reduced motion the bars and numbers jump to the new view.
        switchAt = still ? .distantPast : .now
        switching = !still
    }

    private func setFinger(_ index: Int, hand: Bool) {
        if index == picked { return }
        picked = index
        if index >= 0 {
            if hand {
                stepHaptic.selectionChanged()
                stepHaptic.prepare()
            }
            onSelect?(index, view.values[index])
        }
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        if !holding { setFinger(-1, hand: false) }
        lastTouch = .now
        touches += 1
    }

    private func wait(_ date: Date) async throws {
        while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }
        try Task.checkCancellation()
    }

    /// The autoPlay loop presses the switch and drags a finger that is not there.
    private func autoplay() async {
        guard autoPlay else { return }
        do {
            if touches > 0 {
                let resume = lastTouch.addingTimeInterval(S.userHold)
                try await wait(resume)
                if holding { return }
                setFinger(-1, hand: false)
                choose(0, auto: true)
                try await wait(resume.addingTimeInterval(Chart.switchFor + 0.3))
            }
            var events: [(at: Double, run: () -> Void)] = S.switches.map { item in (item.at, { choose(item.view, auto: true) }) }
            events += S.fingerSteps.map { step in (step.at, { setFinger(step.bar, hand: false) }) }
            events.sort { $0.at < $1.at }
            var start = Date.now
            while true {
                for event in events {
                    try await wait(start.addingTimeInterval(event.at))
                    event.run()
                }
                start = start.addingTimeInterval(S.loop)
                try await wait(start)
            }
        } catch {}
    }
}
