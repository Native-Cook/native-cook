import SwiftUI

struct ActionGridTabBarTab: Equatable {
    var label: String
    /// The name of a system symbol.
    var icon: String

    static let samples = [
        ActionGridTabBarTab(label: "Home", icon: "house"),
        ActionGridTabBarTab(label: "Inbox", icon: "tray"),
        ActionGridTabBarTab(label: "Alerts", icon: "bell"),
        ActionGridTabBarTab(label: "Apps", icon: "square.stack.3d.up"),
    ]
}

/// An action of the grid: a system symbol, or a short text such as "Aa" in its place.
struct ActionGridTabBarAction: Equatable {
    var label: String
    var icon: String? = nil
    var glyph: String? = nil

    static let samples = [
        ActionGridTabBarAction(label: "Trim", icon: "scissors"),
        ActionGridTabBarAction(label: "Crop", icon: "crop"),
        ActionGridTabBarAction(label: "Enhance", icon: "wand.and.stars"),
        ActionGridTabBarAction(label: "Text", glyph: "Aa"),
        ActionGridTabBarAction(label: "Audio", icon: "music.note"),
        ActionGridTabBarAction(label: "Speed", icon: "gauge.with.dots.needle.33percent"),
        ActionGridTabBarAction(label: "Duplicate", icon: "plus.square.on.square"),
        ActionGridTabBarAction(label: "Undo", icon: "arrow.uturn.backward"),
        ActionGridTabBarAction(label: "Share", icon: "square.and.arrow.up"),
        ActionGridTabBarAction(label: "Save", icon: "bookmark"),
        ActionGridTabBarAction(label: "Delete", icon: "trash"),
    ]
}

struct ActionGridTabBarTheme {
    var dark: Bool
    var page: Color
    /// Titles and icons.
    var ink: Color
    var card: Color
    /// The placeholder shapes on a card.
    var shape: Color
    var bar: Color
    var highlight: Color
    var panel: Color
    var cell: Color
    var label: Color

    static let light = ActionGridTabBarTheme(
        dark: false, page: Color(hex: 0xF3F3F3), ink: Color(hex: 0x111111), card: Color(hex: 0xFBFBFB), shape: Color(hex: 0xE9E9E9),
        bar: Color(hex: 0xFBFBFB), highlight: Color(hex: 0xEDEDED), panel: Color(hex: 0xFAFAFA), cell: Color(hex: 0xEFEFEF), label: Color(hex: 0x8A8A8E)
    )
    static let dark = ActionGridTabBarTheme(
        dark: true, page: Color(hex: 0x0E0D10), ink: .white, card: Color(hex: 0x1A1A1D), shape: Color(hex: 0x2A2A2E),
        bar: Color(hex: 0x232425), highlight: Color(hex: 0x343437), panel: Color(hex: 0x1C1C1F), cell: Color(hex: 0x2A2A2D), label: Color(hex: 0x8A8A8E)
    )
}

/// A page with a floating tab bar and a round plus button next to it. The highlight behind the
/// selected tab's icon slides to a new tab stretched, its front end first. The plus turns into a
/// cross while the bar itself stretches upward into a panel holding a grid of actions; pressing
/// an action, the cross or the page shrinks it back.
struct ActionGridTabBar: View {
    var tabs = ActionGridTabBarTab.samples
    /// The actions of the grid, four to a row.
    var actions = ActionGridTabBarAction.samples
    var theme = ActionGridTabBarTheme.light
    /// The tab that is selected at first, and the one autoPlay comes back to.
    var initialTab = 0
    /// Goes through the tabs and opens the grid by itself every `ActionGridTabBarMotion.loop` seconds.
    var autoPlay = false
    var onSelect: ((Int) -> Void)? = nil
    /// Called when the grid opens (true) and closes (false).
    var onToggle: ((Bool) -> Void)? = nil
    var onAction: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The selected tab; nil until one is chosen, which means `initialTab`.
    @State private var selected: Int? = nil
    @State private var open = false
    /// Where the highlight's two ends are, in tabs; nil until the first change, when both are on `initialTab`.
    @State private var ends: Ends? = nil
    /// True while the highlight is still moving, which is when its timeline runs.
    @State private var moving = false
    /// Counts the changes of tab, so the timeline is paused after the last one has settled.
    @State private var moves = 0
    /// Counts the times the grid opened, each of which swells the plus button.
    @State private var swells = 0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var selectionHaptic = UISelectionFeedbackGenerator()
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    private struct Ends {
        var left: ActionGridTabBarSpring
        var right: ActionGridTabBarSpring
        var movedAt: Date
    }

    private var current: Int { selected ?? initialTab }
    private var rows: Int { (actions.count + Metrics.columns - 1) / Metrics.columns }
    private var panelHeight: Double {
        Metrics.pad + Double(rows) * Metrics.row + Double(rows - 1) * Metrics.rowGap + Metrics.padBottom
    }

    var body: some View {
        ZStack {
            theme.page.ignoresSafeArea()

            ForEach(tabs.indices, id: \.self) { index in
                page(index)
            }

            if open {
                Color.clear
                    .contentShape(Rectangle())
                    .ignoresSafeArea()
                    .onTapGesture {
                        touched()
                        toggle(byHand: true)
                    }
                    .accessibilityLabel("Close")
            }

            // The home indicator's inset is ignored: the row sits a fixed way above the screen's bottom.
            GeometryReader { proxy in
                HStack(alignment: .bottom, spacing: Metrics.gap) {
                    shape(width: proxy.size.width - Metrics.margin * 2 - Metrics.gap - Metrics.plus)
                    plusButton
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, Metrics.barBottom)
            }
            .ignoresSafeArea(.container, edges: .bottom)
        }
        .preferredColorScheme(theme.dark ? .dark : .light)
        .onAppear {
            selectionHaptic.prepare()
            tapHaptic.prepare()
        }
        .task(id: moves) {
            guard moves > 0 else { return }
            try? await Task.sleep(for: .seconds(M.settled))
            if !Task.isCancelled { moving = false }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The pages

    /// A tab's page: its name, which cross-fades, and placeholder cards, which also slide.
    private func page(_ index: Int) -> some View {
        let shown = index == current
        let aside = reduceMotion || shown ? 0 : index < current ? -M.pageSlide : M.pageSlide
        let fade = M.ease(reduceMotion ? M.stillFade : M.pageFade)
        let heights = Metrics.pages[index % Metrics.pages.count]

        return VStack(spacing: Metrics.titleGap) {
            Text(tabs[index].label)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(theme.ink)
                .lineLimit(1)
                .frame(height: Metrics.titleHeight)
                .opacity(shown ? 1 : 0)
                .accessibilityAddTraits(.isHeader)

            VStack(spacing: 12) {
                ForEach(heights.indices, id: \.self) { card in
                    HStack(spacing: 14) {
                        Circle().fill(theme.shape).frame(width: 36, height: 36)
                        VStack(alignment: .leading, spacing: 8) {
                            Capsule().fill(theme.shape).frame(width: 120, height: 8)
                            Capsule().fill(theme.shape).frame(width: 76, height: 6)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 18)
                    .frame(height: heights[card])
                    .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(theme.card))
                }
            }
            .padding(.horizontal, Metrics.pageMargin)
            .opacity(shown ? 1 : 0)
            .offset(x: aside)
        }
        .padding(.top, Metrics.titleTop)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .animation(fade, value: current)
        .allowsHitTesting(false)
        .accessibilityHidden(!shown)
    }

    // MARK: The bar and the panel

    /// The bar and the panel are one shape: its height, its corners and its fill change with `open`.
    private func shape(width: Double) -> some View {
        let radius = open ? Metrics.panelRadius : Metrics.barHeight / 2

        return ZStack(alignment: .bottom) {
            // The grid keeps its place: the shape's top edge uncovers it.
            grid
                .padding(.top, Metrics.pad)
                .padding(.horizontal, Metrics.pad)
                .frame(width: width, height: panelHeight, alignment: .top)
                .allowsHitTesting(open)
                .accessibilityHidden(!open)

            tabRow(width: width)
                .blur(radius: open && !reduceMotion ? Metrics.tabsBlur : 0)
                .opacity(open ? 0 : 1)
                .animation(open ? M.ease(M.tabsOut) : M.ease(M.tabsIn).delay(reduceMotion ? 0 : M.tabsInAfter), value: open)
                .allowsHitTesting(!open)
                .accessibilityHidden(open)
        }
        .frame(width: width, height: open ? panelHeight : Metrics.barHeight, alignment: .bottom)
        .clipShape(RoundedRectangle(cornerRadius: radius))
        .background(
            RoundedRectangle(cornerRadius: radius)
                .fill(open ? theme.panel : theme.bar)
                .shadow(color: .black.opacity(0.06), radius: 5, y: 2)
        )
        // Without motion the shape jumps and its contents cross-fade.
        .animation(reduceMotion ? nil : (open ? M.open : M.close), value: open)
    }

    private func tabRow(width: Double) -> some View {
        let first = Metrics.highlightInset + Metrics.highlightWidth / 2
        let pitch = (width - first * 2) / Double(max(1, tabs.count - 1))

        return ZStack {
            TimelineView(.animation(paused: !moving)) { timeline in
                // At rest both ends are on the selected tab, whatever date the paused timeline holds.
                let now = moving ? timeline.date : Date.distantFuture
                let left = ends?.left.value(at: now) ?? Double(current)
                let right = ends?.right.value(at: now) ?? Double(current)
                let lift = ends.map { M.lift(now.timeIntervalSince($0.movedAt)) } ?? 0

                Capsule()
                    .fill(theme.highlight)
                    .shadow(color: .black.opacity(0.12 * lift), radius: 8, y: 2)
                    .frame(width: max(Metrics.highlightMin, abs(right - left) * pitch + Metrics.highlightWidth), height: Metrics.highlightHeight)
                    .scaleEffect(1 + M.liftScale * lift)
                    .position(x: first + (left + right) / 2 * pitch, y: Metrics.barHeight / 2)
            }

            ForEach(tabs.indices, id: \.self) { index in
                Button {
                    touched()
                    select(index, byHand: true)
                } label: {
                    Image(systemName: tabs[index].icon)
                        .font(.system(size: Metrics.symbol, weight: .regular))
                        .foregroundStyle(theme.ink)
                        .frame(width: Metrics.highlightWidth, height: Metrics.barHeight)
                        .contentShape(Rectangle())
                }
                .buttonStyle(Flat())
                .position(x: first + Double(index) * pitch, y: Metrics.barHeight / 2)
                .accessibilityLabel(tabs[index].label)
                .accessibilityAddTraits(index == current ? [.isSelected] : [])
            }
        }
        .frame(width: width, height: Metrics.barHeight)
    }

    private var grid: some View {
        VStack(spacing: Metrics.rowGap) {
            ForEach(0..<rows, id: \.self) { row in
                HStack(alignment: .top, spacing: 0) {
                    ForEach(0..<Metrics.columns, id: \.self) { column in
                        let index = row * Metrics.columns + column
                        Group {
                            if index < actions.count {
                                cell(actions[index], index: index)
                            } else {
                                Color.clear
                            }
                        }
                        .frame(width: Metrics.cell, height: Metrics.row, alignment: .top)
                        if column < Metrics.columns - 1 { Spacer(minLength: 0) }
                    }
                }
                // The bottom row comes first.
                .modifier(ActionGridTabBarReveal(shown: open, after: M.cellsAfter + Double(rows - 1 - row) * M.stagger, still: reduceMotion))
            }
        }
    }

    private func cell(_ action: ActionGridTabBarAction, index: Int) -> some View {
        Button {
            touched()
            onAction?(index)
            toggle(byHand: true)
        } label: {
            VStack(spacing: 4) {
                Group {
                    if let icon = action.icon {
                        Image(systemName: icon).font(.system(size: Metrics.symbol, weight: .regular))
                    } else {
                        Text(action.glyph ?? "").font(.system(size: 17, weight: .medium))
                    }
                }
                .foregroundStyle(theme.ink)
                .frame(width: Metrics.cell, height: Metrics.cell)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(theme.cell))

                // Wider than its cell, so a long label is not cut.
                Text(action.label)
                    .font(.system(size: 10))
                    .foregroundStyle(theme.label)
                    .lineLimit(1)
                    .fixedSize()
                    .frame(width: Metrics.cell, height: 12)
            }
        }
        .buttonStyle(Press())
        .accessibilityLabel(action.label)
    }

    private var plusButton: some View {
        Button {
            touched()
            toggle(byHand: true)
        } label: {
            ZStack {
                Capsule().fill(theme.ink).frame(width: Metrics.plusArm, height: Metrics.plusStroke)
                Capsule().fill(theme.ink).frame(width: Metrics.plusStroke, height: Metrics.plusArm)
            }
            .rotationEffect(.degrees(open ? M.turnDegrees : 0))
            .animation(reduceMotion ? M.ease(M.stillFade) : M.turn, value: open)
            .frame(width: Metrics.plus, height: Metrics.plus)
            .background(Circle().fill(theme.bar).shadow(color: .black.opacity(0.06), radius: 5, y: 2))
            .contentShape(Circle())
        }
        .buttonStyle(Flat())
        .keyframeAnimator(initialValue: 1.0, trigger: swells) { content, scale in
            content.scaleEffect(scale)
        } keyframes: { _ in
            KeyframeTrack {
                CubicKeyframe(M.swell, duration: M.swellUp)
                CubicKeyframe(1.0, duration: M.swellDown)
            }
        }
        .accessibilityLabel(open ? "Close" : "Actions")
    }

    // MARK: Behaviour

    private func select(_ index: Int, byHand: Bool) {
        guard !open, index != current else { return }
        // The loop that plays by itself is silent.
        if byHand { selectionHaptic.selectionChanged() }
        let now = Date.now
        if reduceMotion {
            ends = nil
        } else {
            // The end in front takes the fast spring, so the capsule stretches on its way.
            let forward = index > current
            var next = ends ?? Ends(left: .init(Double(current)), right: .init(Double(current)), movedAt: now)
            next.left.retarget(Double(index), at: now, forward ? M.slow : M.fast)
            next.right.retarget(Double(index), at: now, forward ? M.fast : M.slow)
            next.movedAt = now
            ends = next
            moving = true
            moves += 1
        }
        selected = index
        onSelect?(index)
    }

    private func toggle(byHand: Bool) {
        if byHand { tapHaptic.impactOccurred() }
        open.toggle()
        if open && !reduceMotion { swells += 1 }
        onToggle?(open)
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(until: clock.now + .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            // Rest is the grid closed on the first tab.
            if open { toggle(byHand: false) }
            select(initialTab, byHand: false)
        }
        // Every step is timed from the loop's origin, so a late wake does not push the next one back.
        var origin = clock.now
        while !Task.isCancelled {
            for step in M.steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                if let tab = step.tab { select(tab, byHand: false) } else { toggle(byHand: false) }
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    // MARK: Styles

    /// A button that shows its label as it is.
    private struct Flat: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
        }
    }

    private struct Press: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? ActionGridTabBarMotion.pressScale : 1)
                .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
        }
    }
}

/// One row of the grid: it fades in, sharpens and grows when the grid opens, `after` seconds behind
/// the shape, and the rows leave together, faster.
struct ActionGridTabBarReveal: ViewModifier {
    let shown: Bool
    let after: Double
    let still: Bool

    func body(content: Content) -> some View {
        content
            .scaleEffect(shown || still ? 1 : ActionGridTabBarMotion.cellsScale)
            .blur(radius: shown || still ? 0 : ActionGridTabBarMotion.cellsBlur)
            .opacity(shown ? 1 : 0)
            .animation(animation, value: shown)
    }

    private var animation: Animation {
        typealias M = ActionGridTabBarMotion
        if still { return M.ease(M.stillFade) }
        return shown ? M.ease(M.cellsIn).delay(after) : M.ease(M.cellsOut)
    }
}

/// A spring of mass 1 between two numbers, sampled against a date; retargeting keeps its speed.
struct ActionGridTabBarSpring {
    private var from: Double
    private var to: Double
    private var speed = 0.0
    private var start = Date.distantPast
    private var stiffness = 1.0
    private var damping = 2.0

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        sample(date).value
    }

    mutating func retarget(_ target: Double, at date: Date, _ spring: (stiffness: Double, damping: Double)) {
        let now = sample(date)
        from = now.value
        speed = now.speed
        to = target
        start = date
        stiffness = spring.stiffness
        damping = spring.damping
    }

    /// The closed form of an underdamped spring released at `from` with `speed`.
    private func sample(_ date: Date) -> (value: Double, speed: Double) {
        let t = date.timeIntervalSince(start)
        guard t > 0 else { return (from, speed) }
        guard t < ActionGridTabBarMotion.settled else { return (to, 0) }
        let omega = stiffness.squareRoot()
        let decay = damping / 2
        let damped = (omega * omega - decay * decay).squareRoot()
        let a = from - to
        let b = (speed + decay * a) / damped
        let envelope = exp(-decay * t)
        let cosine = cos(damped * t)
        let sine = sin(damped * t)
        return (
            to + envelope * (a * cosine + b * sine),
            envelope * ((b * damped - decay * a) * cosine - (a * damped + decay * b) * sine)
        )
    }
}

/// The timeline of the block, shared by its three stacks.
enum ActionGridTabBarMotion {
    /// The period of the autoPlay loop, in seconds, and what it presses at each moment: a tab, or the plus (nil).
    static let loop = 10.0
    static let steps: [(at: Double, tab: Int?)] = [(1.0, 1), (2.0, 2), (3.0, 3), (4.2, 0), (5.4, nil), (8.4, nil)]
    /// After a touch the loop waits this long, returns to rest and starts over.
    static let userHold = 3.0

    // The highlight's two ends each follow a spring of mass 1 to the new tab. The end in front takes
    // the fast one, so the capsule is about a third wider in mid-flight between neighbours.
    static let fast = (stiffness: 520.0, damping: 38.0)
    static let slow = (stiffness: 230.0, damping: 26.0)
    /// Both springs are at rest by then.
    static let settled = 1.0
    // Seconds. It lifts while it moves: up in `liftUp`, back in `liftDown`.
    static let liftScale = 0.08
    static let liftUp = 0.14
    static let liftDown = 0.24
    // The page: the title cross-fades, the cards also slide.
    static let pageFade = 0.3
    static let pageSlide = 12.0
    // The bar stretches into the panel on one spring and shrinks back on a firmer one.
    static let open = Animation.interpolatingSpring(mass: 1, stiffness: 130, damping: 20)
    static let close = Animation.interpolatingSpring(mass: 1, stiffness: 200, damping: 24)
    // The tab icons blur out as the shape starts to grow and come back as it lands.
    static let tabsOut = 0.2
    static let tabsIn = 0.25
    static let tabsInAfter = 0.1
    // The plus turns to a cross on a spring that overshoots, and swells for a moment as the grid opens.
    static let turn = Animation.interpolatingSpring(mass: 1, stiffness: 300, damping: 18)
    static let turnDegrees = 45.0
    static let swell = 1.25
    static let swellUp = 0.16
    static let swellDown = 0.26
    // The rows of the grid come in from the bottom one and leave together.
    static let cellsAfter = 0.12
    static let stagger = 0.02
    static let cellsIn = 0.3
    static let cellsOut = 0.15
    static let cellsScale = 0.9
    static let cellsBlur = 6.0
    static let pressScale = 0.94
    /// Without motion everything cross-fades in this time and the shapes jump.
    static let stillFade = 0.2

    /// A cubic ease-out.
    static func ease(_ seconds: Double) -> Animation {
        .timingCurve(0.33, 1, 0.68, 1, duration: seconds)
    }

    /// How far the highlight is lifted `t` seconds after it set off, 0 to 1.
    static func lift(_ t: Double) -> Double {
        func eased(_ x: Double) -> Double { UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.33, y: 1), endControlPoint: UnitPoint(x: 0.68, y: 1)).value(at: min(1, max(0, x))) }
        if t <= 0 || t >= liftUp + liftDown { return 0 }
        return t < liftUp ? eased(t / liftUp) : 1 - eased((t - liftUp) / liftDown)
    }
}

private typealias M = ActionGridTabBarMotion

private enum Metrics {
    static let pageMargin = 20.0
    static let titleTop = 12.0
    static let titleHeight = 22.0
    static let titleGap = 20.0
    /// The heights of the placeholder cards on each tab's page, so that a new tab is a new page.
    static let pages: [[Double]] = [[120, 72, 72, 72], [72, 72, 72, 72, 72], [72, 120, 72, 72], [120, 120, 72]]
    /// The bottom row: the bar, a gap and the round plus button, between these side margins.
    static let margin = 24.0
    static let gap = 8.0
    static let barHeight = 48.0
    static let plus = 48.0
    static let plusArm = 16.0
    static let plusStroke = 1.8
    /// The row's bottom edge above the bottom of the screen.
    static let barBottom = 24.0
    /// System symbols draw larger than an icon font at the same size: this fills a 22 point box.
    static let symbol = 16.0
    /// The capsule behind the selected tab's icon, and how far the first and the last sit from the bar's ends.
    static let highlightWidth = 60.0
    static let highlightHeight = 40.0
    static let highlightInset = 4.0
    static let highlightMin = 36.0
    static let tabsBlur = 8.0
    /// The grid: four columns of 44 point buttons with a label under each.
    static let columns = 4
    static let pad = 16.0
    static let padBottom = 14.0
    static let cell = 44.0
    static let row = 60.0
    static let rowGap = 14.0
    static let panelRadius = 26.0
}

#Preview {
    ActionGridTabBar(autoPlay: true)
}
