import SwiftUI

private enum Palette {
    static let background = Color.black
    static let ink = Color.white
    static let mute = Color(hex: 0x8E8E93)
    static let card = Color(hex: 0x131316)
    static let chip = Color(hex: 0x1C1C1F)
    static let track = Color(hex: 0x1C1C1F)
    static let thumbColor = Color(hex: 0x2E2E33)
    static let button = Color(hex: 0x141417)
    static let tag = Color(hex: 0xFF3B4E)
    static let tabBar = Color(hex: 0x1C1C1F)
    static let tabOn = Color(hex: 0x2A2A40)
    static let accent = StressDotGridDonutLevel.color(StressDotGridDonutLevel.normal)
}

// Layout, in points.
private let margin = 16.0
private let nav = 56.0
private let roundButton = 40.0
private let segment = 36.0
private let donutSize = 164.0
private let chipHeight = 62.0
private let tabHeight = 58.0
private let busiestShift = 14.0
// The share of the morph over which the old text leaves, and the new one arrives at the end.
private let handOverShare = 0.4

// Motion.
private let thumbFor = 0.25
private let donutSpring = (damping: 14.0, stiffness: 120.0, mass: 0.9)
private let jumpDip = 0.3
private let jumpFor = 0.22
// A swipe this long over the grid switches the view; it counts once it has gone 12 pt sideways.
private let swipe = 40.0
private let swipeStart = 12.0
private let morphFor = StressDotGridDonutDotGrid.morphFor

// The autoPlay timeline, in seconds from the start of each loop.
private let loop = 8.0
private let steps: [(at: Double, view: Int)] = [(1.0, 1), (5.4, 0)]
// Seconds after the last touch before the screen returns to the first view and the loop starts again.
private let userHold = 3.0

private let levels: [(label: String, color: Color)] = [
    ("Low", StressDotGridDonutLevel.color(StressDotGridDonutLevel.low)),
    ("Normal", StressDotGridDonutLevel.color(StressDotGridDonutLevel.normal)),
    ("High", StressDotGridDonutLevel.color(StressDotGridDonutLevel.high)),
]

/// How far the old text (side 0) has gone or the new one (side 1) has come, as the morph runs 0 → 1.
private func handOver(_ m: Double, _ side: Int) -> Double {
    let from = side == 0 ? 0 : 1 - handOverShare
    return min(1, max(0, (m - from) / handOverShare))
}

/// A number moving to a target on a timed curve or a spring, read from the timeline's date so no
/// state changes while it moves.
private struct Motion {
    private var from: Double
    private var to: Double
    private var start = Date.distantPast
    private var duration = 0.0
    private var curve: (Double) -> Double = { $0 }
    private var spring = false
    private var velocity = 0.0

    init(_ value: Double) {
        from = value
        to = value
    }

    /// When the value has arrived.
    var end: Date {
        guard spring else { return start.addingTimeInterval(duration) }
        let (z, _, a, b) = coefficients
        let amplitude = hypot(a, b)
        // The swing has shrunk below a ten-thousandth of the way: nothing on screen moves after that.
        return amplitude < 1e-4 ? start : start.addingTimeInterval(log(amplitude / 1e-4) / z)
    }

    func value(at date: Date) -> Double { sample(date).value }

    private var coefficients: (z: Double, w: Double, a: Double, b: Double) {
        let z = donutSpring.damping / (2 * donutSpring.mass)
        let w = sqrt(donutSpring.stiffness / donutSpring.mass - z * z)
        let a = from - to
        return (z, w, a, (velocity + z * a) / w)
    }

    private func sample(_ date: Date) -> (value: Double, velocity: Double) {
        let t = max(0, date.timeIntervalSince(start))
        if spring {
            if date >= end { return (to, 0) }
            // The spring is underdamped (damping ratio 0.67), so it overshoots once like Reanimated's.
            let (z, w, a, b) = coefficients
            let decay = exp(-z * t)
            let offset = decay * (a * cos(w * t) + b * sin(w * t))
            let speed = decay * (velocity * cos(w * t) - (a * w + z * b) * sin(w * t))
            return (to + offset, speed)
        }
        guard duration > 0 else { return (to, 0) }
        return (from + (to - from) * curve(min(1, t / duration)), 0)
    }

    /// Jumps to `value`.
    mutating func set(_ value: Double) { self = Motion(value) }

    mutating func time(to target: Double, at date: Date, duration: Double, curve: @escaping (Double) -> Double) {
        from = value(at: date)
        to = target
        start = date
        self.duration = duration
        self.curve = curve
        spring = false
    }

    mutating func spring(to target: Double, at date: Date) {
        let now = sample(date)
        from = now.value
        velocity = now.velocity
        to = target
        start = date
        spring = true
    }
}

/// A stress patterns screen: a heat map of dots, one per two-hour period of the week, and a donut of
/// how many were low, normal or high. Switching to four weeks sends every dot to its day in the new
/// grid in a stagger while the grid shrinks, the donut's segments spring to their new lengths and
/// the counts roll; switching back runs it in reverse.
struct StressDotGridDonut: View {
    var title = "Patterns"
    /// The two views of the heat map: two-hour periods of a week, and days of four weeks.
    var days = StressDotGridDonutPeriod.days
    var weeks = StressDotGridDonutPeriod.weeks
    var donutLabel = "Levels"
    var tagLabel = "Preview"
    /// Switches to the four weeks and back by itself every `loop` seconds.
    var autoPlay = false
    var onViewChange: ((StressDotGridDonutPeriod, Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @State private var view = 0
    // 0 shows the week's periods, 1 the four weeks; the donut follows on a spring of its own.
    @State private var morph = Motion(0)
    @State private var donut = Motion(0)
    @State private var fade = Motion(1)
    @State private var thumb = Motion(0)
    /// Until when something moves; the timeline runs only until then.
    @State private var movingUntil = Date.distantPast
    @State private var moving = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var swipeSideways: Bool? = nil
    @State private var haptic = UISelectionFeedbackGenerator()

    private var periods: [StressDotGridDonutPeriod] { [days, weeks] }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let bottom = max(insets.bottom, 12)
            TimelineView(.animation(paused: !moving)) { timeline in
                // At rest every motion has arrived, so any date after it reads the same.
                let date = moving ? timeline.date : Date.distantFuture
                ZStack(alignment: .bottom) {
                    content(width: proxy.size.width - margin * 2, date: date)
                        .padding(.horizontal, margin)
                        .padding(.top, insets.top)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                        .clipped()

                    LinearGradient(colors: [Palette.background.opacity(0), Palette.background], startPoint: .top, endPoint: .bottom)
                        .frame(height: tabHeight + bottom + 40)
                        .allowsHitTesting(false)
                    tabs.padding(.bottom, bottom)
                }
                .ignoresSafeArea()
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: movingUntil) { await settle() }
        .task(id: Restart(on: autoPlay, touches: touches)) { await autoplay() }
    }

    // MARK: The screen

    @ViewBuilder
    private func content(width: Double, date: Date) -> some View {
        let m = morph.value(at: date)
        let first = 1 - handOver(m, 0)
        let second = handOver(m, 1)
        let tally = periods[view].counts
        let shares = periods.map { period in
            let tally = period.counts
            return tally.map { Double($0) / Double(max(1, tally[0] + tally[1] + tally[2])) }
        }

        VStack(alignment: .leading, spacing: 0) {
            HStack {
                round("person.fill", 15)
                Spacer()
                HStack(spacing: 6) {
                    text(title, 17, 22, .semibold, Palette.ink)
                    Image(systemName: "lock.fill").font(.system(size: 12, weight: .semibold)).foregroundStyle(Palette.ink)
                }
                Spacer()
                round("info", 17)
            }
            .frame(height: nav)

            segments(thumb: thumb.value(at: date))
                .padding(.top, 4)
                .padding(.horizontal, 16)

            HStack(spacing: 0) {
                ZStack(alignment: .leading) {
                    text(days.range, 14, 18, .semibold, Palette.ink).opacity(first)
                    text(weeks.range, 14, 18, .semibold, Palette.ink).opacity(second)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                tagView
            }
            .padding(.top, 18)

            StressDotGridDonutDotGrid(width: width, days: days, weeks: weeks, morph: m, opacity: fade.value(at: date))
                .contentShape(Rectangle())
                .gesture(swipeGesture)
                .accessibilityElement()
                .accessibilityLabel("Stress by \(view == 0 ? "period, last 7 days" : "day, last 4 weeks")")
                .padding(.top, 12)

            section("Stress Levels")
            HStack(spacing: 16) {
                StressDotGridDonutLevelDonut(
                    size: donutSize, colors: levels.map(\.color), from: shares[0], to: shares[1],
                    progress: donut.value(at: date), label: donutLabel
                )
                VStack(spacing: 8) {
                    ForEach(levels.indices, id: \.self) { i in
                        chipView(i, count: tally[i], first: first, second: second)
                    }
                }
            }
            .padding(.top, 14)

            section("Stressful Periods")
            busiest(m)
                .padding(.top, 12)
        }
    }

    private func text(_ value: String, _ size: Double, _ lineHeight: Double, _ weight: Font.Weight, _ color: Color) -> some View {
        Text(value)
            .font(.system(size: size, weight: weight))
            .foregroundStyle(color)
            .lineLimit(1)
            .frame(height: lineHeight)
    }

    private func round(_ symbol: String, _ size: Double) -> some View {
        Image(systemName: symbol)
            .font(.system(size: size, weight: .semibold))
            .foregroundStyle(Palette.ink)
            .frame(width: roundButton, height: roundButton)
            .background(Circle().fill(Palette.button))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.12), lineWidth: 0.5))
    }

    private var tagView: some View {
        HStack(spacing: 5) {
            Image(systemName: "lock.fill").font(.system(size: 8.5, weight: .semibold)).foregroundStyle(Palette.tag)
            text(tagLabel, 11, 14, .semibold, Palette.ink)
        }
        .padding(.horizontal, 10)
        .frame(height: 24)
        .background(Capsule().fill(Palette.tag.opacity(0.1)))
        .overlay(Capsule().strokeBorder(Palette.tag.opacity(0.45), lineWidth: 1))
    }

    private func section(_ title: String) -> some View {
        HStack {
            text(title, 15, 20, .semibold, Palette.ink)
            Spacer()
            tagView
        }
        .padding(.top, 18)
    }

    private func segments(thumb: Double) -> some View {
        GeometryReader { proxy in
            let width = (proxy.size.width - 6) / 2
            ZStack(alignment: .topLeading) {
                Capsule().fill(Palette.thumbColor)
                    .frame(width: width, height: segment - 6)
                    .offset(x: 3 + thumb * width, y: 3)
                HStack(spacing: 0) {
                    ForEach(periods.indices, id: \.self) { index in
                        Button {
                            touched()
                            choose(index, auto: false)
                        } label: {
                            text(periods[index].label, 14, 18, .medium, index == view ? Palette.ink : Palette.mute)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(index == view ? [.isSelected] : [])
                    }
                }
                .padding(3)
            }
        }
        .frame(height: segment)
        .background(Capsule().fill(Palette.track))
    }

    private func chipView(_ i: Int, count: Int, first: Double, second: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                Circle().fill(levels[i].color).frame(width: 10, height: 10)
                text(levels[i].label, 12, 16, .medium, Palette.mute)
            }
            HStack(alignment: .bottom, spacing: 0) {
                StressDotGridDonutRoller(text: String(count), size: 22, weight: .semibold, color: Palette.ink, lineHeight: 26, still: reducedMotion)
                ZStack(alignment: .topLeading) {
                    text(days.unit, 12, 16, .regular, Palette.mute).opacity(first)
                    text(weeks.unit, 12, 16, .regular, Palette.mute).opacity(second)
                }
                .padding(.leading, 4)
                .padding(.bottom, 3)
            }
            .padding(.top, 2)
        }
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: chipHeight)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Palette.chip))
    }

    private func busiest(_ m: Double) -> some View {
        let gone = handOver(m, 0)
        let come = handOver(m, 1)
        return ZStack(alignment: .topLeading) {
            busiestText(days).opacity(1 - gone).offset(y: -busiestShift * gone)
            busiestText(weeks).opacity(come).offset(y: busiestShift * (1 - come))
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .frame(height: 84, alignment: .topLeading)
        .background(Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func busiestText(_ period: StressDotGridDonutPeriod) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            text(period.busiestTitle, 12, 16, .medium, Palette.mute)
            text(period.busiest, 22, 28, .semibold, Palette.ink).padding(.top, 4)
        }
    }

    private var tabs: some View {
        HStack(spacing: 0) {
            tab("clock.fill", 17, box: 20, "Today", on: false)
            tab("square.grid.2x2.fill", 16, box: 19, "Patterns", on: true)
            tab("book.fill", 15, box: 19, "Playbook", on: false)
        }
        .padding(4)
        .background(Capsule().fill(Palette.tabBar))
        .overlay(Capsule().strokeBorder(Color.white.opacity(0.1), lineWidth: 0.5))
    }

    /// `box` is the React Native icon's size, which sets the tab's line.
    private func tab(_ symbol: String, _ size: Double, box: Double, _ label: String, on: Bool) -> some View {
        VStack(spacing: 3) {
            Image(systemName: symbol).font(.system(size: size)).foregroundStyle(on ? Palette.accent : Palette.ink).frame(height: box)
            text(label, 11, 14, .semibold, on ? Palette.accent : Palette.ink)
        }
        .frame(width: 86, height: tabHeight)
        .background(Capsule().fill(on ? Palette.tabOn : .clear))
    }

    // MARK: Behaviour

    private var swipeGesture: some Gesture {
        DragGesture(minimumDistance: swipeStart)
            .onChanged { value in
                guard swipeSideways == nil else { return }
                // Like a pan that fails once it goes 12 pt up or down before going sideways.
                swipeSideways = abs(value.translation.width) >= abs(value.translation.height)
                touched()
            }
            .onEnded { value in
                defer { swipeSideways = nil }
                guard swipeSideways == true else { return }
                touched()
                let dx = value.translation.width
                if dx < -swipe { choose(1, auto: false) } else if dx > swipe { choose(0, auto: false) }
            }
    }

    private func choose(_ index: Int, auto: Bool, at date: Date = .now) {
        guard index != view else { return }
        view = index
        if !auto {
            haptic.selectionChanged()
            haptic.prepare()
        }
        onViewChange?(periods[index], index)
        let target = Double(index)
        if reducedMotion {
            morph.set(target)
            donut.set(target)
            thumb.set(target)
            fade.set(jumpDip)
            // Reanimated's default timing curve, ease-in-out quadratic.
            fade.time(to: 1, at: date, duration: jumpFor, curve: { $0 < 0.5 ? 2 * $0 * $0 : 1 - pow(-2 * $0 + 2, 2) / 2 })
            run(until: fade.end)
            return
        }
        thumb.time(to: target, at: date, duration: thumbFor, curve: { 1 - pow(1 - $0, 3) })
        morph.time(to: target, at: date, duration: morphFor, curve: { $0 })
        donut.spring(to: target, at: date)
        run(until: max(thumb.end, morph.end, donut.end))
    }

    private func run(until date: Date) {
        moving = true
        movingUntil = max(movingUntil, date)
    }

    private func settle() async {
        do {
            try await wait(until: movingUntil)
            moving = false
        } catch {}
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        lastTouch = .now
        touches += 1
    }

    private struct Restart: Equatable {
        var on: Bool
        var touches: Int
    }

    /// Taps the tabs a finger would, each at its time from the start of the loop.
    private func autoplay() async {
        guard autoPlay else { return }
        do {
            if touches > 0 {
                try await wait(until: lastTouch.addingTimeInterval(userHold))
                choose(0, auto: true)
                try await wait(until: Date.now.addingTimeInterval(morphFor + 0.3))
            }
            var start = Date.now
            while true {
                for step in steps {
                    let at = start.addingTimeInterval(step.at)
                    try await wait(until: at)
                    choose(step.view, auto: true, at: at)
                }
                start = start.addingTimeInterval(loop)
            }
        } catch {}
    }

    private func wait(until date: Date) async throws {
        while date > .now {
            try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero)
        }
        try Task.checkCancellation()
    }
}

#Preview {
    StressDotGridDonut(autoPlay: true)
}
