import SwiftUI

/// Top tabs set on an arc, like the rim of a large wheel whose hub is far below the screen. Paging
/// turns the wheel: the next tab climbs to the top centre and straightens while the page comes in
/// over the one that leaves, its picture trailing behind its text.
struct OrbitingTopTabs: View {
    /// The tabs' names, in the order of their pages.
    var tabs = ["NEW IN", "BROWSE", "ARCHIVE REVIVAL", "TRAIL RUNNER"]
    /// The tab the screen opens on.
    var initialTab = 1
    /// The names on the bottom bar; the first one is the current one.
    var barItems = ["HOME", "STYLE", "SEARCH", "SELL", "PROFILE"]
    /// Pages through the tabs by itself, back at the first page every `loop` seconds.
    var autoPlay = false
    var onSelect: ((Int) -> Void)? = nil

    private enum M {
        static let ink = Color(hex: 0x111111)

        /// The tabs sit on the rim of a wheel whose hub is `radius` points below their line;
        /// neighbours are `step` radians apart, which is `pitch` points along the rim.
        static let radius = 560.0
        static let step = 13.0 * Double.pi / 180
        static let pitch = radius * step
        /// The middle of the tabs' line and the top of the pages, below the top safe-area inset.
        static let tabsY = 41.0
        static let pagesY = 61.0
        static let tab = CGSize(width: 150, height: 32)
        /// A tab one step from the centre is this faint, and so is every tab beyond.
        static let dim = 0.55
        /// The page that leaves moves at this part of the finger's speed, under the one that comes in.
        static let out = 0.85
        /// Past the first and the last page a drag moves the pages by this part of the finger's way.
        static let rubber = 0.25
        /// The settle: about 0.4 s, passing the page by a few points.
        static let spring = Spring(mass: 1, stiffness: 170, damping: 21)
        /// A release goes to the page the drag would reach in this many seconds at its speed.
        static let project = 0.18
        /// Reduced motion: the pages cross-fade over this long.
        static let fade = 0.25
        static let bar = 58.0

        static let userHold = 3.0
        static let loop = 9.0
        /// The loop that plays by itself: the page that is due, in seconds from the start of a loop.
        static let script: [(at: Double, page: Int)] = [(1.0, 2), (3.0, 3), (5.0, 2), (5.5, 1)]
    }

    private struct Drag {
        var from: Double
        /// How many points of the drag make one page: the screen's width on the pages, the tabs'
        /// spacing on the wheel.
        var perPage: Double
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The page in the middle of the screen, as a number: 1.5 is half way between two pages. Nil
    /// until something moves it away from the first tab.
    @State private var position: OrbitingTopTabsTween? = nil
    @State private var current: Int? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var drag: Drag? = nil
    /// The timeline runs until this moment, and is paused while nothing moves.
    @State private var movingUntil = Date.distantPast
    @State private var moving = false
    @State private var haptic = UISelectionFeedbackGenerator()

    private var shown: Int { current ?? initialTab }
    private var last: Double { Double(tabs.count - 1) }

    private func place(at date: Date) -> Double {
        position?.value(at: date) ?? Double(initialTab)
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let pagesTop = insets.top + M.pagesY

            TimelineView(.animation(paused: !moving)) { timeline in
                stage(place(at: timeline.date), screen: screen, insets: insets)
            }
            .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            .ignoresSafeArea()
            .simultaneousGesture(dragging(width: screen.width, pagesTop: pagesTop))
        }
        .preferredColorScheme(.light)
        .onAppear { haptic.prepare() }
        .task(id: movingUntil) {
            let wait = movingUntil.timeIntervalSinceNow
            if wait > 0 { try? await Task.sleep(for: .seconds(wait)) }
            if !Task.isCancelled { moving = false }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The stage

    private func stage(_ p: Double, screen: CGSize, insets: EdgeInsets) -> some View {
        let pagesTop = insets.top + M.pagesY
        let barHeight = M.bar + insets.bottom
        let pageHeight = max(0, screen.height - pagesTop - barHeight)

        return ZStack(alignment: .topLeading) {
            Color.white

            // The page that comes in lies over the one that leaves.
            ZStack(alignment: .topLeading) {
                ForEach(tabs.indices, id: \.self) { index in
                    let away = Double(index) - p
                    OrbitingTopTabsPage(
                        index: index, width: screen.width, height: pageHeight,
                        drift: reduceMotion ? 0 : OrbitingTopTabsPage.parallax * min(1, max(0, away))
                    )
                    .offset(x: reduceMotion ? 0 : screen.width * (away >= 0 ? away : M.out * away))
                    .opacity(reduceMotion ? 1 - min(1, abs(away)) : 1)
                    .accessibilityHidden(index != shown)
                }
            }
            .frame(width: screen.width, height: pageHeight, alignment: .topLeading)
            .clipped()
            .offset(y: pagesTop)

            ZStack {
                Color.white
                ForEach(Array(tabs.enumerated()), id: \.offset) { index, label in
                    tab(label, index: index, away: Double(index) - p, center: screen.width / 2, y: insets.top + M.tabsY)
                }
            }
            .frame(width: screen.width, height: pagesTop)
            .clipped()

            HStack(spacing: 0) {
                ForEach(Array(barItems.enumerated()), id: \.offset) { index, label in
                    Text(label)
                        .font(.system(size: 7, weight: .semibold))
                        .tracking(0.8)
                        .underline(index == 0)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 8)
            .frame(width: screen.width, height: M.bar)
            .frame(height: barHeight, alignment: .top)
            .background(Color.black)
            .offset(y: screen.height - barHeight)
        }
    }

    /// A tab on the wheel's rim: upright at the top centre, tilted and lower as it turns away.
    private func tab(_ label: String, index: Int, away: Double, center: Double, y: Double) -> some View {
        let turn = away * M.step
        // A tap, not a button: a button rides the wheel under the finger and would fire at the end
        // of a drag.
        return Text(label)
            .font(.system(size: 9, weight: .semibold))
            .tracking(1)
            .foregroundStyle(M.ink)
            .lineLimit(1)
            .frame(width: M.tab.width, height: M.tab.height)
            .contentShape(Rectangle())
            .onTapGesture { press(index) }
            .accessibilityAddTraits(index == shown ? [.isButton, .isSelected] : .isButton)
        .opacity(1 - (1 - M.dim) * min(1, abs(away)))
        // Reduced motion: the tabs slide along a straight line.
        .rotationEffect(.radians(reduceMotion ? 0 : turn))
        .position(
            x: center + (reduceMotion ? away * M.pitch : M.radius * sin(turn)),
            y: y + (reduceMotion ? 0 : M.radius * (1 - cos(turn)))
        )
    }

    // MARK: Turning

    /// Keeps the timeline running for the next `seconds`.
    private func stir(_ seconds: Double) {
        movingUntil = max(movingUntil, Date.now.addingTimeInterval(seconds + 0.15))
        moving = true
    }

    private func turn(to index: Int, velocity: Double = 0) {
        let now = Date.now
        var tween = position ?? OrbitingTopTabsTween(Double(initialTab))
        if reduceMotion {
            tween.move(to: Double(index), at: now, duration: M.fade)
            stir(M.fade)
        } else {
            tween.move(to: Double(index), at: now, spring: M.spring, velocity: velocity)
            stir(M.spring.settlingDuration)
        }
        position = tween
        if index != shown {
            current = index
            onSelect?(index)
        }
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
    }

    private func press(_ index: Int) {
        touched()
        if index != shown { haptic.selectionChanged() }
        turn(to: index)
    }

    private func dragging(width: Double, pagesTop: Double) -> some Gesture {
        DragGesture(minimumDistance: 10, coordinateSpace: .global)
            .onChanged { value in
                if drag == nil {
                    guard abs(value.translation.width) > abs(value.translation.height) else { return }
                    drag = Drag(from: place(at: .now), perPage: value.startLocation.y < pagesTop ? M.pitch : width)
                    touched()
                }
                guard let drag else { return }
                let to = drag.from - value.translation.width / drag.perPage
                position = OrbitingTopTabsTween(to < 0 ? to * M.rubber : to > last ? last + (to - last) * M.rubber : to)
            }
            .onEnded { value in
                guard let drag else { return }
                self.drag = nil
                // Pages a second. A drag of the pages goes one page at most; the wheel may spin further.
                let velocity = -value.velocity.width / drag.perPage
                let start = drag.from.rounded()
                let reach = drag.perPage == M.pitch ? last : 1
                let aim = (place(at: .now) + velocity * M.project).rounded()
                let to = Int(min(min(last, start + reach), max(max(0, start - reach), aim)))
                if to != shown { haptic.selectionChanged() }
                turn(to: to, velocity: velocity)
            }
    }

    // MARK: The loop

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // Back to rest; the pages have settled by the time the loop first turns them.
            turn(to: initialTab)
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            // Each step turns to the page that is due at its time, whatever the steps before it did.
            for step in M.script {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                turn(to: step.page)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

/// A number on its way to a target, read against the timeline's date: on a spring that starts
/// with a speed, or evenly over a duration.
struct OrbitingTopTabsTween {
    private var from: Double
    private var to: Double
    private var start = Date.distantPast
    private var duration = 0.0
    private var spring: Spring?
    private var velocity = 0.0

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        if t <= 0 { return from }
        if let spring { return from + spring.value(target: to - from, initialVelocity: velocity, time: t) }
        return duration > 0 ? from + (to - from) * min(1, t / duration) : to
    }

    /// Sets off from where the number is at `date`, at `velocity` units a second.
    mutating func move(to target: Double, at date: Date, duration: Double = 0, spring: Spring? = nil, velocity: Double = 0) {
        from = value(at: date)
        to = target
        start = date
        self.duration = duration
        self.spring = spring
        self.velocity = velocity
    }
}

#Preview {
    OrbitingTopTabs(autoPlay: true)
}
