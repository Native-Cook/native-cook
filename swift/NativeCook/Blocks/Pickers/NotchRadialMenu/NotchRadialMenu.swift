import SwiftUI

/// One action of the menu.
struct NotchRadialMenuItem: Equatable {
    /// SF Symbol drawn on the chip.
    var symbol: String
    /// What VoiceOver calls it.
    var label: String

    static let samples = [
        NotchRadialMenuItem(symbol: "phone.connection.fill", label: "Call"),
        NotchRadialMenuItem(symbol: "envelope.badge.fill", label: "Message"),
        NotchRadialMenuItem(symbol: "mic.fill", label: "Voice note"),
    ]
}

/// One of the two tabs beside the notch.
struct NotchRadialMenuTab: Equatable {
    /// SF Symbol while the tab is not selected, and while it is.
    var symbol: String
    var selectedSymbol: String
    var label: String

    static let samples = [
        NotchRadialMenuTab(symbol: "house", selectedSymbol: "house.fill", label: "Home"),
        NotchRadialMenuTab(symbol: "person", selectedSymbol: "person.fill", label: "Profile"),
    ]
}

/// A tab bar whose top edge dips into a notch that cradles a round button. Tapping the button turns
/// its plus into a cross and fans the actions out of it: the chips leave from behind the button on
/// the left and sweep clockwise onto an arc above it, the furthest one leading. Tapping a chip picks
/// it and folds the fan away; tapping the cross folds it without a pick.
struct NotchRadialMenu: View {
    /// The actions, left to right along the arc. Three sit best.
    var items = NotchRadialMenuItem.samples
    /// The tab left of the notch and the one right of it.
    var tabs = NotchRadialMenuTab.samples
    /// The tab selected at first.
    var initialTab = 0
    /// Opens the menu, picks an action and closes it by itself, in a loop.
    var autoPlay = false
    /// Called with the index of the action that was picked.
    var onSelect: ((Int) -> Void)? = nil
    /// Called when the menu opens or closes.
    var onOpenChange: ((Bool) -> Void)? = nil
    /// Called with the index of the tab that was selected.
    var onTabChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    @State private var tab = 0
    /// Counts taps: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The button: 0 a plus on violet … 1 a cross on black.
        var open = NotchRadialMenuTrack(0)
        /// Each chip: 0 behind the button … 1 in its place on the arc.
        var chips: [NotchRadialMenuTrack] = []
        /// Tap feedback of each chip and of the button.
        var pulse: [NotchRadialMenuTrack] = []
        var press = NotchRadialMenuTrack(0)
        /// The tabs' colours: 0 the left one is lit … 1 the right one.
        var tab = NotchRadialMenuTrack(0)
        var isOpen = false
        /// Things to do at a later frame; opening or closing drops what is still waiting.
        var later: [(at: Date, run: () -> Void)] = []

        func runDue(_ now: Date) {
            guard later.contains(where: { $0.at <= now }) else { return }
            let due = later.filter { $0.at <= now }
            later.removeAll { $0.at <= now }
            for step in due { step.run() }
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion && !items.isEmpty }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let _ = motion.runDue(now)
            let open = motion.open.value(at: now)
            let lit = motion.tab.value(at: now)
            let center = CGPoint(x: Metrics.width / 2, y: Metrics.barTop)

            ZStack {
                RadialGradient(
                    colors: [Color(hex: 0x3d2a70), Color(hex: 0x120b24)],
                    center: .center, startRadius: 0, endRadius: Metrics.glow
                )
                .frame(width: Metrics.glow * 2, height: Metrics.glow * 2)
                .position(center)
                .allowsHitTesting(false)

                NotchRadialMenuBar()
                    .fill(Color(hex: 0x050505))
                    .frame(width: Metrics.width, height: Metrics.barHeight)
                    .position(x: Metrics.width / 2, y: Metrics.barTop + Metrics.barHeight / 2)

                // The light the open menu throws on the bottom of the cradle.
                Circle()
                    .trim(from: 0.06, to: 0.44)
                    .stroke(
                        LinearGradient(colors: [Color(hex: 0xc77dff, opacity: 0), Color(hex: 0xc77dff)], startPoint: .top, endPoint: .bottom),
                        style: StrokeStyle(lineWidth: 2, lineCap: .round)
                    )
                    .frame(width: Metrics.notch * 2 - 3, height: Metrics.notch * 2 - 3)
                    .opacity(0.55 * clamp01(open))
                    .position(center)
                    .allowsHitTesting(false)

                ForEach(0..<min(2, tabs.count), id: \.self) { i in
                    tabView(i, lit: i == 0 ? 1 - lit : lit)
                }

                ForEach(items.indices, id: \.self) { i in
                    if i < motion.chips.count {
                        chip(i, now: now)
                    }
                }

                button(open: open, press: motion.press.value(at: now))
                    .position(center)
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: touches)
        .onAppear { reset() }
        .onChange(of: items.count) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Parts

    private func button(open: Double, press: Double) -> some View {
        ZStack {
            Circle().fill(LinearGradient(colors: [Color(hex: 0xb06af5), Color(hex: 0x7c3aed)], startPoint: .top, endPoint: .bottom))
            Circle().fill(Color(hex: 0x0a0a0a)).opacity(clamp01(open))
            ZStack {
                Capsule().frame(width: Metrics.plus, height: Metrics.plusLine)
                Capsule().frame(width: Metrics.plusLine, height: Metrics.plus)
            }
            .foregroundStyle(.white)
            .rotationEffect(.degrees(45 * open))
        }
        .frame(width: Metrics.button, height: Metrics.button)
        .contentShape(Circle())
        .onTapGesture {
            touch()
            toggle()
        }
        .scaleEffect(1 - 0.06 * press)
        .accessibilityLabel(motion.isOpen ? "Close menu" : "Open menu")
        .accessibilityAddTraits(.isButton)
    }

    private func chip(_ i: Int, now: Date) -> some View {
        let p = motion.chips[i].value(at: now)
        // Under reduced motion a chip does not travel: it fades in where it belongs.
        let place = notchRadialMenuPlace(i, of: items.count, progress: reduceMotion ? 1 : p)
        return Image(systemName: items[i].symbol)
            .font(.system(size: Metrics.glyph, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: Metrics.chip, height: Metrics.chip)
            .background(
                Circle().fill(LinearGradient(colors: [Color(hex: 0xe2a8ff), Color(hex: 0xb266f0)], startPoint: .top, endPoint: .bottom))
            )
            .contentShape(Circle())
            .onTapGesture {
                touch()
                select(i)
            }
            .rotationEffect(.degrees(place.turn))
            .scaleEffect(place.scale * (1 + 0.12 * motion.pulse[i].value(at: now)))
            .opacity(clamp01(p / 0.3))
            .position(x: place.x, y: place.y)
            .allowsHitTesting(motion.isOpen)
            .accessibilityLabel(items[i].label)
            .accessibilityAddTraits(.isButton)
            .accessibilityHidden(!motion.isOpen)
    }

    /// A tab: its grey and its lilac look lie on top of each other and cross-fade.
    private func tabView(_ i: Int, lit: Double) -> some View {
        let item = tabs[i]
        return ZStack {
            tabLook(item.symbol, item.label)
                .foregroundStyle(Color(hex: 0x9a9a9a))
                .opacity(1 - lit)
            tabLook(item.selectedSymbol, item.label)
                .foregroundStyle(Color(hex: 0xc77dff))
                .opacity(lit)
        }
        .frame(width: Metrics.tabWidth, height: Metrics.barHeight)
        .contentShape(Rectangle())
        .onTapGesture {
            touch()
            pick(tab: i)
        }
        .position(x: i == 0 ? Metrics.tabInset : Metrics.width - Metrics.tabInset, y: Metrics.barTop + Metrics.barHeight / 2)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(item.label)
        .accessibilityAddTraits(tab == i ? [.isButton, .isSelected] : .isButton)
    }

    private func tabLook(_ symbol: String, _ label: String) -> some View {
        VStack(spacing: 5) {
            Image(systemName: symbol)
                .font(.system(size: 19, weight: .medium))
                .frame(height: 22)
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .lineLimit(1)
        }
    }

    // MARK: The menu's steps

    private func reset() {
        motion.chips = items.map { _ in NotchRadialMenuTrack(0) }
        motion.pulse = items.map { _ in NotchRadialMenuTrack(0) }
        motion.open = NotchRadialMenuTrack(0)
        motion.isOpen = false
        motion.later = []
        tab = min(max(initialTab, 0), 1)
        motion.tab = NotchRadialMenuTrack(Double(tab))
    }

    private func touch() {
        touches += 1
    }

    private func toggle() {
        motion.press.retarget(1, .pulse)
        setOpen(!motion.isOpen)
    }

    /// Fans the chips out or folds them away, the last one leading by `stagger` per place.
    private func setOpen(_ open: Bool) {
        guard open != motion.isOpen else { return }
        motion.isOpen = open
        motion.later = []
        let spring = open ? openSpring : closeSpring
        motion.open.retarget(open ? 1 : 0, reduceMotion ? .jump : .spring(spring))
        for i in motion.chips.indices {
            motion.chips[i].retarget(
                open ? 1 : 0,
                reduceMotion ? .tween(Metrics.fade, .linear) : .spring(spring),
                delay: reduceMotion ? 0 : Double(motion.chips.count - 1 - i) * Metrics.stagger
            )
        }
        onOpenChange?(open)
    }

    /// Picks action `i`: its chip swells for a moment, then the menu closes.
    private func select(_ i: Int) {
        guard motion.isOpen, motion.pulse.indices.contains(i) else { return }
        motion.pulse[i].retarget(1, .pulse)
        motion.later = [(Date.now.addingTimeInterval(Metrics.closeAfter), { setOpen(false) })]
        onSelect?(i)
    }

    private func pick(tab i: Int) {
        guard i != tab else { return }
        tab = i
        motion.tab.retarget(Double(i), reduceMotion ? .jump : .tween(Metrics.tabTime, .easeInOut))
        onTabChange?(i)
    }

    /// The script a finger would follow: open, pick the middle action, open again, pick the last one.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, go back to the closed menu and the first tab, and start over.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                setOpen(false)
                pick(tab: min(max(initialTab, 0), 1))
                try await Task.sleep(for: .seconds(Metrics.settle))
            }
            let clock = ContinuousClock()
            var start = clock.now
            let picks = [items.count / 2, items.count - 1]
            while true {
                for (round, item) in picks.enumerated() {
                    try await clock.sleep(until: start + .seconds(Metrics.openAt[round]))
                    toggle()
                    try await clock.sleep(until: start + .seconds(Metrics.pickAt[round]))
                    select(item)
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// The bar: square shoulders at the top, round corners at the bottom, and in the middle of the top
/// edge a cradle whose rim curves into it on each side.
private struct NotchRadialMenuBar: Shape {
    func path(in rect: CGRect) -> Path {
        let mid = rect.midX
        let r = Metrics.notch
        let f = Metrics.shoulder
        // The shoulder's circle touches the top edge and the cradle's circle: its centre is this far out.
        let reach = (r * r + 2 * r * f).squareRoot()
        // Where the two circles meet, as an angle below the top edge.
        let join = Angle(radians: asin(f / (r + f)))

        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY + Metrics.topCorner))
        path.addArc(tangent1End: CGPoint(x: rect.minX, y: rect.minY), tangent2End: CGPoint(x: mid, y: rect.minY), radius: Metrics.topCorner)
        path.addLine(to: CGPoint(x: mid - reach, y: rect.minY))
        path.addArc(center: CGPoint(x: mid - reach, y: rect.minY + f), radius: f, startAngle: .degrees(-90), endAngle: -join, clockwise: false)
        path.addArc(center: CGPoint(x: mid, y: rect.minY), radius: r, startAngle: .degrees(180) - join, endAngle: join, clockwise: true)
        path.addArc(center: CGPoint(x: mid + reach, y: rect.minY + f), radius: f, startAngle: .degrees(180) + join, endAngle: .degrees(270), clockwise: false)
        path.addArc(tangent1End: CGPoint(x: rect.maxX, y: rect.minY), tangent2End: CGPoint(x: rect.maxX, y: rect.maxY), radius: Metrics.topCorner)
        path.addArc(tangent1End: CGPoint(x: rect.maxX, y: rect.maxY), tangent2End: CGPoint(x: mid, y: rect.maxY), radius: Metrics.bottomCorner)
        path.addArc(tangent1End: CGPoint(x: rect.minX, y: rect.maxY), tangent2End: CGPoint(x: rect.minX, y: rect.minY), radius: Metrics.bottomCorner)
        path.closeSubpath()
        return path
    }
}

/// A number heading for a target, sampled against the `TimelineView` clock.
private struct NotchRadialMenuTrack {
    enum Kind {
        case jump
        case tween(Double, UnitCurve)
        case spring(Spring)
        /// Up to the target in 80 ms, a 40 ms hold, back to 0 in 180 ms: a tap.
        case pulse
    }

    private struct Move {
        var from: Double
        var to: Double
        var speed = 0.0
        var start = Date.distantPast
        var kind = Kind.jump

        func value(at date: Date) -> Double {
            let t = date.timeIntervalSince(start)
            guard t > 0 else { return from }
            switch kind {
            case .jump: return to
            case .tween(let duration, let curve): return t >= duration ? to : from + (to - from) * curve.value(at: t / duration)
            case .spring(let spring): return from + spring.value(target: to - from, initialVelocity: speed, time: t)
            case .pulse: return to * (t < 0.08 ? t / 0.08 : t < 0.12 ? 1 : max(0, 1 - (t - 0.12) / 0.18))
            }
        }

        func velocity(at date: Date) -> Double {
            let t = date.timeIntervalSince(start)
            guard t > 0, case .spring(let spring) = kind else { return 0 }
            return spring.velocity(target: to - from, initialVelocity: speed, time: t)
        }
    }

    private var move: Move
    /// The move a delayed one replaces: it goes on until the new one begins.
    private var before: Move?

    init(_ value: Double) {
        move = Move(from: value, to: value)
    }

    func value(at date: Date) -> Double {
        if let before, date < move.start { return before.value(at: date) }
        return move.value(at: date)
    }

    /// Heads for `target` from wherever it will be `delay` seconds from now, when the move begins.
    mutating func retarget(_ target: Double, _ kind: Kind, delay: Double = 0) {
        let begin = Date.now.addingTimeInterval(delay)
        let old = (before != nil && begin < move.start) ? before! : move
        var next = Move(from: old.value(at: begin), to: target, speed: old.velocity(at: begin), start: begin, kind: kind)
        if case .pulse = kind { next.from = 0 }
        before = delay > 0 ? old : nil
        move = next
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 204.0

    /// The bar's top edge, which is also the height of the button's centre.
    static let barTop = 124.0
    static let barHeight = 80.0
    static let topCorner = 6.0
    static let bottomCorner = 26.0
    static let notch = 34.0
    static let shoulder = 18.0
    static let button = 58.0
    static let plus = 16.0
    static let plusLine = 1.75
    static let tabInset = 90.0
    static let tabWidth = 110.0
    static let glow = 300.0

    // The chips: on an arc `spread` degrees to each side of 12 o'clock, the middle ones further out.
    static let chip = 46.0
    static let glyph = 19.0
    static let spread = 51.0
    static let sideReach = 70.0
    static let topReach = 84.0
    /// Where a folded chip waits, in degrees clockwise from 12 o'clock: left of the button, a little low.
    static let fold = -115.0
    static let foldReach = 0.2
    static let foldScale = 0.6

    static let stagger = 0.05
    /// A picked chip swells, and the menu closes this long after the tap.
    static let closeAfter = 0.45
    static let tabTime = 0.2
    static let fade = 0.15

    // autoPlay (seconds): the button is tapped at `openAt`, a chip at `pickAt`, twice per loop.
    static let openAt = [1.0, 4.2]
    static let pickAt = [2.45, 5.45]
    static let loop = 7.0
    /// After a touch the loop waits this long, closes the menu, and starts over once it has settled.
    static let resumeAfter = 2.0
    static let settle = 0.6
}

private let openSpring = Spring(mass: 1, stiffness: 260, damping: 23)
private let closeSpring = Spring(mass: 1, stiffness: 300, damping: 34)

/// Where chip `i` of `count` is at `progress` (0 folded behind the button … 1 on the arc; a spring may
/// carry it a little past either end): it swings clockwise about the button while moving outward and
/// growing, and its glyph turns with it. `turn` is in degrees.
private func notchRadialMenuPlace(_ i: Int, of count: Int, progress p: Double) -> (x: Double, y: Double, scale: Double, turn: Double) {
    let along = count > 1 ? Double(i) / Double(count - 1) * 2 - 1 : 0
    let final = along * Metrics.spread
    let reach = mix(Metrics.topReach, Metrics.sideReach, abs(along))
    let angle = mix(Metrics.fold, final, p)
    let distance = reach * mix(Metrics.foldReach, 1, p)
    let radians = angle * .pi / 180
    return (
        x: Metrics.width / 2 + distance * sin(radians),
        y: Metrics.barTop - distance * cos(radians),
        scale: max(0, mix(Metrics.foldScale, 1, p)),
        turn: angle - final
    )
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

#Preview {
    ZStack {
        Color(hex: 0x120b24).ignoresSafeArea()
        NotchRadialMenu(autoPlay: true)
    }
}
