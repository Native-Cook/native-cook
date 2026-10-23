import SwiftUI

/// A recipe page with a timer and a three-tab bar. "Start oven" turns into a box that counts down;
/// once that box has scrolled off the screen, the stopwatch in the tab bar swells into a pill that
/// carries the time, and when the box comes back the pill collapses and hands the time back to it.
struct LiveTimerTab: View {
    var title = "Baked Brie & Garlic Bread"
    var subtitle = "25 min · Serves 4"
    /// The text of the page, one string for each paragraph.
    var paragraphs = LiveTimerTab.sampleParagraphs
    var listTitle = "Ingredients"
    var list = ["1 wheel of brie, about 250 g", "1 baguette, sliced", "3 cloves of garlic", "2 sprigs of rosemary", "Olive oil and flaky salt"]
    /// The title of the page behind the third tab.
    var profileTitle = "Profile"
    var startLabel = "Start oven"
    /// What the timer counts down from, in seconds.
    var duration = LiveTimerTabMotion.timer
    /// Starts the timer, scrolls the page away and back and stops the timer by itself, every `LiveTimerTabMotion.loop` seconds.
    var autoPlay = false
    var onStart: (() -> Void)? = nil
    /// Called with the seconds that were left when the timer is stopped by hand.
    var onStop: ((Int) -> Void)? = nil
    /// Called when the time is up.
    var onDone: (() -> Void)? = nil
    /// Called with 0 for the recipe or 1 for the profile.
    var onTabChange: ((Int) -> Void)? = nil

    static let sampleParagraphs = [
        "Few things fill a kitchen the way warm bread and melting cheese do. The smell drifts out of the oven long before the timer ends, and everyone nearby finds a reason to come and look.",
        "Here a whole wheel of brie bakes on a ring of baguette slices rubbed with garlic. A little rosemary and a thread of olive oil are all it needs: the cheese turns soft and glossy while the bread crisps at its edges.",
        "It suits a quiet evening for two as well as a table full of friends. Bring it out in its dish while it still bubbles, and let people tear and dip as they talk.",
    ]

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// When the timer was started; nil while it is idle.
    @State private var startedAt: Date? = nil
    /// Whole seconds counted since then.
    @State private var counted = 0
    @State private var tab = 0
    /// The timer's control has scrolled out of what shows of the page.
    @State private var away = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// 0 the black button, 1 the light box.
    @State private var run = Tween(0)
    /// 0 the stopwatch, 1 the pill.
    @State private var pill = Tween(0)
    /// 0 the recipe, 1 the profile.
    @State private var turn = Tween(0)
    /// The pill is handing the time back to the box, and how far under the box it is.
    @State private var ghosting = false
    @State private var ghostDrop = 0.0
    /// How much of the cover behind the status bar shows: none over the picture, all of it over text.
    @State private var cover = 0.0
    /// How many of the values above are on their way; the timeline is paused at none.
    @State private var moving = 0
    @State private var pilot = LiveTimerTabPilot()
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var selectHaptic = UISelectionFeedbackGenerator()

    private var running: Bool { startedAt != nil }
    /// The time lives in the tab bar while its box is out of sight.
    private var live: Bool { running && (away || tab != 0) }
    private var time: String { M.clock(duration - 1 - counted) }

    var body: some View {
        GeometryReader { safe in
            let insets = safe.safeAreaInsets
            GeometryReader { proxy in
                let size = proxy.size
                let barHeight = M.tabBar + max(insets.bottom, M.tabBottom)
                // What shows of the page: from under the status bar to where the lines start to fade.
                let shown = insets.top...(size.height - barHeight)
                let pillY = size.height - barHeight + M.tabBar / 2

                TimelineView(.animation(paused: moving == 0)) { timeline in
                    let now = timeline.date
                    let turned = turn.value(at: now)
                    let swollen = pill.value(at: now)

                    ZStack(alignment: .topLeading) {
                        Palette.page
                        LiveTimerTabScroll(pilot: pilot) {
                            recipe(width: size.width, least: size.height + M.autoScroll, bottom: barHeight + M.fade, ran: run.value(at: now), swollen: swollen)
                        }
                        .opacity(1 - turned)
                        .allowsHitTesting(tab == 0)
                        .accessibilityHidden(tab != 0)

                        profile(top: insets.top)
                            .opacity(turned)
                            .allowsHitTesting(false)
                            .accessibilityHidden(tab != 1)

                        LinearGradient(
                            stops: [.init(color: Palette.page, location: 0), .init(color: Palette.page, location: insets.top / (insets.top + Metrics.coverFade)), .init(color: Palette.page.opacity(0), location: 1)],
                            startPoint: .top, endPoint: .bottom
                        )
                        .frame(height: insets.top + Metrics.coverFade)
                        .opacity(cover * (1 - turned))
                        .allowsHitTesting(false)

                        VStack(spacing: 0) {
                            Spacer(minLength: 0)
                            LinearGradient(colors: [Palette.bar.opacity(0), Palette.bar], startPoint: .top, endPoint: .bottom)
                                .frame(height: M.fade)
                                .allowsHitTesting(false)
                            bar(width: size.width, swollen: swollen)
                                .frame(height: barHeight, alignment: .top)
                                .background(Palette.bar)
                        }
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }
                .onAppear {
                    pilot.onDrag = { touched() }
                    pilot.onScroll = { y in scrolled(to: y, shown: shown, pillY: pillY) }
                }
                .onChange(of: size) { _, _ in
                    pilot.onScroll = { y in scrolled(to: y, shown: shown, pillY: pillY) }
                }
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear { tapHaptic.prepare() }
        .onDisappear { pilot.halt() }
        .onChange(of: live) { _, now in handOver(to: now) }
        .task(id: startedAt) { await count() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The pages

    private func recipe(width: Double, least: Double, bottom: Double, ran: Double, swollen: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                Palette.hero
                ForEach(Art.hero.indices, id: \.self) { index in
                    let dot = Art.hero[index]
                    Circle()
                        .fill(Color(hex: dot.3))
                        .frame(width: dot.2 * 2, height: dot.2 * 2)
                        .offset(x: width / 2 + dot.0 - dot.2, y: dot.1 - dot.2)
                }
            }
            .frame(width: width, height: M.hero, alignment: .topLeading)
            .clipped()
            .accessibilityHidden(true)

            Group {
                Text(title)
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(Palette.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .frame(height: M.titleLine)
                    .padding(.top, M.titleGap)
                    .accessibilityAddTraits(.isHeader)
                Text(subtitle)
                    .font(.system(size: 15))
                    .foregroundStyle(Palette.mute)
                    .lineLimit(1)
                    .frame(height: M.subtitleLine)
                    .padding(.top, M.subtitleGap)

                control(width: width - M.margin * 2, ran: ran, swollen: swollen)
                    .padding(.top, M.controlGap)
                    // The time that rises from the pill is drawn over the text below.
                    .zIndex(1)

                ForEach(paragraphs, id: \.self) { paragraph in
                    Text(paragraph)
                        .font(.system(size: 17))
                        .lineSpacing(Metrics.bodySpacing)
                        .foregroundStyle(Palette.body)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, M.bodyGap)
                }
                Text(listTitle)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                    .frame(height: 26)
                    .padding(.top, M.bodyGap + 8)
                    .padding(.bottom, 8)
                ForEach(list, id: \.self) { item in
                    Text(item)
                        .font(.system(size: 17))
                        .foregroundStyle(Palette.body)
                        .lineLimit(1)
                        .frame(height: M.bodyLine + 4)
                }
            }
            .padding(.horizontal, M.margin)

            Color.clear.frame(height: bottom)
        }
        .frame(width: width, alignment: .leading)
        .frame(minHeight: least, alignment: .top)
    }

    private func control(width: Double, ran: Double, swollen: Double) -> some View {
        let way = M.clamp01(swollen)
        let grey = M.mix(Metrics.buttonGrey, Metrics.boxGrey, ran)
        return ZStack {
            Button {
                touched()
                tapHaptic.impactOccurred()
                if running {
                    let left = duration - counted
                    stop()
                    onStop?(left)
                } else {
                    start()
                }
            } label: {
                Text(startLabel)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .fixedSize()
                    .opacity(1 - ran)
                    .frame(width: M.mix(width, M.boxWidth, ran), height: M.control)
                    .background(RoundedRectangle(cornerRadius: M.controlRadius, style: .continuous).fill(Color(white: grey)))
                    .clipShape(RoundedRectangle(cornerRadius: M.controlRadius, style: .continuous))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .opacity(1 - way)
            .accessibilityLabel(running ? "\(time), stop the timer" : startLabel)

            if running {
                // The box's time is not in the box: on its way back from the pill it rises to its
                // place from under the page, growing, and the box fades in around it.
                LiveTimerTabRollingTime(text: time, size: Metrics.controlSize, color: Palette.ink, weight: .medium, still: reduceMotion)
                    .scaleEffect(ghosting ? M.mix(1, M.ghostScale, way) : 1)
                    .offset(y: ghosting ? way * ghostDrop : 0)
                    .opacity(ran * (ghosting ? M.clamp01((1 - way) / M.ghostIn) * M.mix(1, 0.5, way) : 1 - way))
                    .transition(reduceMotion ? .identity : .asymmetric(insertion: LiveTimerTabRollingTime.rollIn, removal: .opacity))
                    .allowsHitTesting(false)
            }
        }
        .frame(width: width, height: M.control)
    }

    private func profile(top: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(profileTitle)
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .frame(height: M.titleLine)
                .accessibilityAddTraits(.isHeader)
            ForEach(Metrics.rows, id: \.self) { width in
                HStack(spacing: 16) {
                    RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Palette.box).frame(width: 56, height: 56)
                    VStack(alignment: .leading, spacing: 10) {
                        Capsule().fill(Palette.box).frame(width: width, height: 12)
                        Capsule().fill(Palette.row).frame(width: 90, height: 12)
                    }
                }
                .padding(.top, 20)
            }
        }
        .padding(.horizontal, M.margin)
        .padding(.top, top + M.titleGap)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Palette.page)
    }

    // MARK: The tab bar

    private func bar(width: Double, swollen: Double) -> some View {
        ZStack {
            tabButton(0, label: "Recipe") { LiveTimerTabHat() }
                .position(x: M.tabInset, y: M.tabBar / 2)
            timerTab(swollen: swollen)
                .position(x: width / 2, y: M.tabBar / 2)
            tabButton(1, label: profileTitle) { LiveTimerTabPerson() }
                .position(x: width - M.tabInset, y: M.tabBar / 2)
        }
        .frame(width: width, height: M.tabBar)
    }

    private func tabButton(_ index: Int, label: String, @ViewBuilder icon: () -> some Shape) -> some View {
        Button {
            touched()
            if tab != index { selectHaptic.selectionChanged() }
            choose(index)
        } label: {
            icon()
                .stroke(tab == index ? Palette.ink : Palette.inactive, style: StrokeStyle(lineWidth: M.tabStroke, lineCap: .round, lineJoin: .round))
                .frame(width: M.tabIcon, height: M.tabIcon)
                .frame(width: M.tabBar, height: M.tabBar)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(tab == index ? [.isSelected] : [])
    }

    /// The stopwatch whose ring is the pill: it widens, darkens and rises to the middle of the bar.
    private func timerTab(swollen: Double) -> some View {
        let way = M.clamp01(swollen)
        let size = reduceMotion ? 1 : swollen
        let height = M.mix(M.dial, M.pillHeight, size)
        let drop = M.mix(M.dialDrop, 0, size)
        let hand = Capsule().fill(Palette.ink).frame(width: M.tabStroke, height: M.hand).offset(y: -M.hand / 2).rotationEffect(.degrees(45))
        return Button {
            touched()
            selectHaptic.selectionChanged()
            choose(0)
            pilot.home()
        } label: {
            ZStack {
                if reduceMotion {
                    // Under reduced motion the ring stays as it is and fades out under the pill.
                    hand
                        .frame(width: M.dial, height: M.dial)
                        .overlay(Circle().strokeBorder(Palette.ink, lineWidth: M.tabStroke))
                        .offset(y: M.dialDrop)
                        .opacity(1 - way)
                }
                Capsule()
                    .fill(Palette.ink)
                    .frame(width: M.tick, height: M.tabStroke)
                    .offset(y: M.mix(M.dialDrop, 0, reduceMotion ? way : swollen) - M.mix(M.dial, M.pillHeight, reduceMotion ? way : swollen) / 2 - M.tickGap)
                ZStack {
                    if !reduceMotion { hand.opacity(1 - M.clamp01(swollen * 2.5)) }
                    if live {
                        LiveTimerTabRollingTime(text: time, size: Metrics.pillSize, color: .white, weight: .semibold, still: reduceMotion)
                            .transition(reduceMotion ? .opacity : .asymmetric(insertion: LiveTimerTabRollingTime.rollIn, removal: .opacity))
                    }
                }
                .frame(width: M.mix(M.dial, M.pillWidth, size), height: height)
                .background(Capsule().fill(Palette.ink.opacity(M.clamp01(size))))
                .overlay(Capsule().strokeBorder(Palette.ink, lineWidth: M.tabStroke))
                .clipShape(Capsule())
                .offset(y: drop)
                .opacity(reduceMotion ? way : 1)
            }
            .frame(width: M.pillWidth, height: M.tabBar)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(live ? "Timer, \(time)" : "Timer")
    }

    // MARK: Behaviour

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
        pilot.halt()
    }

    /// Keeps the timeline running for as long as a value is on its way.
    private func keepMoving(for seconds: Double, from date: Date) {
        moving += 1
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(max(0, date.timeIntervalSinceNow + seconds) + 0.1))
            moving -= 1
        }
    }

    private func start(at date: Date = .now) {
        guard startedAt == nil else { return }
        counted = 0
        withAnimation(reduceMotion ? nil : LiveTimerTabRollingTime.animation) { startedAt = date }
        run.retarget(1, at: date, duration: M.start, curve: M.easeOut)
        keepMoving(for: M.start, from: date)
        onStart?()
    }

    private func stop(at date: Date = .now) {
        guard startedAt != nil else { return }
        withAnimation(.easeOut(duration: M.timeOut)) { startedAt = nil }
        run.retarget(0, at: date, duration: M.stop, curve: M.easeOut)
        keepMoving(for: M.stop, from: date)
    }

    private func choose(_ index: Int) {
        guard index != tab else { return }
        withAnimation(reduceMotion ? nil : LiveTimerTabRollingTime.animation) { tab = index }
        onTabChange?(index)
        turn.retarget(Double(index), duration: M.tabFade, curve: .easeInOut)
        keepMoving(for: M.tabFade, from: .now)
    }

    /// The control leaves when its middle passes an edge of what shows, and returns a little further in.
    private func scrolled(to y: Double, shown: ClosedRange<Double>, pillY: Double) {
        // The cover comes in as the picture's last points pass under the status bar.
        let covered = M.clamp01((y - (M.hero - shown.lowerBound - Metrics.coverOver)) / Metrics.coverOver)
        if covered != cover { cover = covered }
        let middle = M.controlCentre - y
        let edge = away ? M.hysteresis : -M.hysteresis
        let now = middle < shown.lowerBound + edge || middle > shown.upperBound - edge
        guard now != away else { return }
        ghostDrop = pillY - middle
        withAnimation(reduceMotion ? nil : LiveTimerTabRollingTime.animation) { away = now }
    }

    /// The time moves to the tab bar, or back to its box.
    private func handOver(to live: Bool) {
        // The time rises back to its box only when the box is there to take it.
        ghosting = !live && running && tab == 0 && !reduceMotion
        let seconds = live ? M.form : M.collapse
        pill.retarget(live ? 1 : 0, duration: seconds, curve: live && !reduceMotion ? M.swell : M.easeOut)
        keepMoving(for: seconds, from: .now)
    }

    /// The count: one tick a second, each due at a whole number of seconds from the start.
    private func count() async {
        guard let startedAt else { return }
        let clock = ContinuousClock()
        let origin = clock.now.advanced(by: .seconds(startedAt.timeIntervalSinceNow))
        var n = 1
        while !Task.isCancelled {
            try? await clock.sleep(until: origin + .seconds(n), tolerance: .zero)
            guard !Task.isCancelled else { return }
            if n >= duration {
                stop()
                onDone?()
                return
            }
            counted = n
            n += 1
        }
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // Back to rest; the loop's first stretch covers the way there.
            stop()
            choose(0)
            pilot.glide(to: 0, seconds: M.scrollHome)
        }
        var origin = clock.now
        var date = Date.now
        // Each step is given the moment it was due, so a late wake-up does not make a loop differ.
        let steps: [(Double, (Date) -> Void)] = [
            (M.startAt, { start(at: $0) }),
            (M.scrollAt, { pilot.glide(to: M.autoScroll, seconds: M.scrollUp, from: $0) }),
            (M.backAt, { pilot.glide(to: 0, seconds: M.scrollBack, from: $0) }),
            (M.stopAt, { stop(at: $0) }),
        ]
        while !Task.isCancelled {
            for (at, step) in steps {
                try? await clock.sleep(until: origin + .seconds(at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                step(date.addingTimeInterval(at))
            }
            origin += .seconds(M.loop)
            date.addTimeInterval(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

private typealias M = LiveTimerTabMotion

private enum Palette {
    static let page = Color(hex: 0xFCFCFC)
    static let bar = Color.white
    static let ink = Color(hex: 0x111111)
    static let mute = Color(hex: 0x8A8A8A)
    static let body = Color(hex: 0x3A3A3A)
    static let inactive = Color(hex: 0x9A9A9A)
    static let box = Color(hex: 0xE6E6E6)
    static let row = Color(hex: 0xEDEDED)
    static let hero = Color(hex: 0xEFEFEF)
}

private enum Metrics {
    /// The greys of the black button and of the light box, 0 to 1.
    static let buttonGrey = Double(0x16) / 255
    static let boxGrey = Double(0xE6) / 255
    static let controlSize = 19.0
    static let pillSize = 17.0
    /// The cover behind the status bar fades out over this height under it, and comes in over this much scrolling.
    static let coverFade = 16.0
    static let coverOver = 40.0
    /// Added between the lines of 17-point text for a line height of 28.
    static let bodySpacing = 7.7
    /// The widths of the first line of the profile's rows.
    static let rows = [150.0, 190.0, 120.0]
}

private enum Art {
    /// The picture at the top of the page, seen from above: a board, a pan with a wheel of brie in a
    /// ring of bread, and two bowls. Each circle is its middle from the picture's own, its radius and its grey.
    static let hero: [(Double, Double, Double, UInt32)] = [
        (0, 132, 84, 0xDCDCDC),
        (0, 132, 66, 0x1C1C1C),
        (0, 132, 58, 0x2B2B2B),
        (49, 132, 11, 0xCFCFCF),
        (24.5, 174.4, 11, 0xCFCFCF),
        (-24.5, 174.4, 11, 0xCFCFCF),
        (-49, 132, 11, 0xCFCFCF),
        (-24.5, 89.6, 11, 0xCFCFCF),
        (24.5, 89.6, 11, 0xCFCFCF),
        (49, 132, 7.5, 0xE6E6E6),
        (24.5, 174.4, 7.5, 0xE6E6E6),
        (-24.5, 174.4, 7.5, 0xE6E6E6),
        (-49, 132, 7.5, 0xE6E6E6),
        (-24.5, 89.6, 7.5, 0xE6E6E6),
        (24.5, 89.6, 7.5, 0xE6E6E6),
        (0, 132, 34, 0xF4F4F4),
        (0, 132, 28, 0xFFFFFF),
        (-9, 125, 2.5, 0x8A8A8A),
        (8, 138, 2.5, 0x8A8A8A),
        (3, 120, 2, 0xB4B4B4),
        (-6, 141, 2, 0xB4B4B4),
        (-136, 66, 28, 0xDCDCDC),
        (-136, 66, 21, 0xF7F7F7),
        (134, 196, 32, 0xDCDCDC),
        (134, 196, 24, 0xC9C9C9),
    ]
}

/// A chef's hat in a 26-point box.
struct LiveTimerTabHat: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 7.5, y: 17.2))
        path.addCurve(to: CGPoint(x: 7.4, y: 9.6), control1: CGPoint(x: 3.2, y: 16.6), control2: CGPoint(x: 2.6, y: 10.2))
        path.addCurve(to: CGPoint(x: 18.6, y: 9.6), control1: CGPoint(x: 8, y: 5.6), control2: CGPoint(x: 18, y: 5.6))
        path.addCurve(to: CGPoint(x: 18.5, y: 17.2), control1: CGPoint(x: 23.4, y: 10.2), control2: CGPoint(x: 22.8, y: 16.6))
        path.addLine(to: CGPoint(x: 18.5, y: 21))
        path.addQuadCurve(to: CGPoint(x: 17, y: 22.5), control: CGPoint(x: 18.5, y: 22.5))
        path.addLine(to: CGPoint(x: 9, y: 22.5))
        path.addQuadCurve(to: CGPoint(x: 7.5, y: 21), control: CGPoint(x: 7.5, y: 22.5))
        path.closeSubpath()
        path.move(to: CGPoint(x: 7.5, y: 19))
        path.addLine(to: CGPoint(x: 18.5, y: 19))
        return path.offsetBy(dx: rect.minX, dy: rect.minY)
    }
}

/// A head and shoulders in a 26-point box.
struct LiveTimerTabPerson: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addEllipse(in: CGRect(x: 13 - 4.2, y: 8.6 - 4.2, width: 8.4, height: 8.4))
        path.move(to: CGPoint(x: 4.8, y: 22.5))
        path.addCurve(to: CGPoint(x: 13, y: 15.6), control1: CGPoint(x: 4.8, y: 17.5), control2: CGPoint(x: 8.5, y: 15.6))
        path.addCurve(to: CGPoint(x: 21.2, y: 22.5), control1: CGPoint(x: 17.5, y: 15.6), control2: CGPoint(x: 21.2, y: 17.5))
        return path.offsetBy(dx: rect.minX, dy: rect.minY)
    }
}

#Preview {
    LiveTimerTab(autoPlay: true)
}
