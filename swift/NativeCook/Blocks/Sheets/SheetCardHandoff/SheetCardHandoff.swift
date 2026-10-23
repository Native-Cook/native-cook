import SwiftUI

/// Calls `content` with the value as it animates; the closure's own mapping (ramps, lerps) runs
/// on every frame, which SwiftUI's modifiers would otherwise interpolate linearly end to end.
private struct SheetCardHandoffReader<Value: VectorArithmetic, Content: View>: View, Animatable {
    var value: Value
    @ViewBuilder let content: (Value) -> Content

    var animatableData: Value {
        get { value }
        set { value = newValue }
    }

    var body: some View { content(value) }
}

/// A task list with a bottom sheet over it. The sheet shows a fan of cards and a Done button; Done
/// drops the sheet and the front card flies out of it into the first slot of the list, growing from
/// 150 points to the width of the list. The card is drawn in an overlay above both, since it belongs
/// to neither, and its small and large layouts cross-fade so that text is never scaled.
struct SheetCardHandoff: View {
    /// The task the sheet hands to the list.
    var task = M.task
    /// The task already in the list.
    var existing = M.existing
    /// Later tasks under it, which make room too.
    var later = M.later
    /// Plays the whole interaction by itself, over and over, and gives way to a touch.
    var autoPlay = false
    var newLabel = "+ NEW TASK"
    var title = "ALL SET"
    var caption = "This is a preview. The full cards will be delivered later."
    var doneLabel = "DONE"
    /// Called when the card has arrived in the list.
    var onAdd: ((SheetCardHandoffTask) -> Void)? = nil

    private typealias M = SheetCardHandoffMotion

    private enum Stage { case idle, open, closing, added }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // 0 hidden, 1 resting; the fan, the flight and the list each have a run of their own.
    @State private var sheet = 0.0
    @State private var drag = 0.0
    @State private var fan = 0.0
    @State private var fly = 0.0
    @State private var flying = false
    @State private var flyFrom = 0.0
    @State private var shift = 0.0
    @State private var landed = 0.0
    @State private var badge = 0.0
    @State private var stage = Stage.idle
    @State private var added = false
    @State private var touches = 0
    @State private var autoNew = false
    @State private var autoDone = false
    @State private var dragging = false
    @State private var badgeTask: Task<Void, Never>? = nil

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            scene(screen: screen, insets: insets)
                .frame(width: screen.width, height: screen.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(M.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onDisappear { badgeTask?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private func scene(screen: CGSize, insets: EdgeInsets) -> some View {
        let sheetHeight = M.sheetHeight + insets.bottom
        let cardWidth = screen.width - M.margin * 2
        let listTop = insets.top + M.listTop
        let count = 2 + later.count + (added ? 1 : 0)

        return ZStack(alignment: .topLeading) {
            Color.clear

            // Header
            HStack {
                HStack(spacing: 6) {
                    Circle().fill(M.dot).frame(width: 7, height: 7)
                    Text("\(count) \(count == 1 ? "TASK" : "TASKS")")
                }
                .modifier(Pill())
                Spacer(minLength: 0)
                HStack(spacing: 8) {
                    HStack(spacing: 6) {
                        Image(systemName: "calendar")
                            .font(.system(size: 10.5, weight: .medium))
                        Text("27 Jan")
                    }
                    .modifier(Pill())
                    Image(systemName: "gearshape")
                        .font(.system(size: 13))
                        .foregroundStyle(M.ink)
                        .frame(width: M.pill, height: M.pill)
                        .background(M.page, in: Circle())
                        .overlay(Circle().strokeBorder(M.outline, lineWidth: M.hairline))
                }
            }
            .padding(.horizontal, M.margin)
            .frame(width: screen.width, height: M.pill)
            .offset(y: insets.top + 8)

            VStack(alignment: .leading, spacing: 2) {
                Text("Today")
                    .font(.system(size: 34, weight: .heavy))
                    .tracking(-1)
                    .foregroundStyle(M.ink)
                    .frame(height: 40)
                Text("Monday · Your focus plan")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(M.mute)
                    .frame(height: 20)
            }
            .offset(x: M.margin, y: insets.top + M.titleTop)

            // New task
            Button {
                touched()
                clear()
                open()
            } label: {
                Text(newLabel)
                    .font(.system(size: 13, weight: .heavy))
                    .tracking(1)
                    .foregroundStyle(M.wellInk)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(M.well, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .strokeBorder(M.dash, style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))
                    )
            }
            .buttonStyle(SheetCardHandoffPressStyle(forced: autoNew, onDown: touched))
            .accessibilityLabel("New task")
            .frame(width: cardWidth, height: M.newHeight)
            .offset(x: M.margin, y: insets.top + M.newTop)

            // The list: the new task lands in the first slot, the one that was there moves down.
            ZStack(alignment: .topTrailing) {
                SheetCardHandoffTaskCard(task: task, width: cardWidth)
                    .modifier(Lift(color: task.color))
                SheetCardHandoffReader(value: badge) { value in
                    Text("NEW")
                        .font(.system(size: 10, weight: .black))
                        .tracking(0.6)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8)
                        .frame(height: 20)
                        .background(M.badge, in: Capsule())
                        .compositingGroup()
                        .shadow(color: M.badge.opacity(0.4), radius: 6, y: 2)
                        .scaleEffect(value)
                        .opacity(min(1, value * 4))
                        .padding(.top, 16)
                        .padding(.trailing, 16)
                }
            }
            .opacity(landed)
            .offset(x: M.margin, y: listTop)

            VStack(spacing: M.gap) {
                ForEach([existing] + later, id: \.name) { item in
                    SheetCardHandoffTaskCard(task: item, width: cardWidth)
                        .modifier(Lift(color: item.color))
                }
            }
            .offset(x: M.margin, y: listTop + shift * M.slot)

            tabBar(screen: screen, insets: insets)

            SheetCardHandoffReader(value: AnimatablePair(sheet, drag)) { pair in
                let sheetValue = pair.first
                let dragValue = pair.second
                ZStack(alignment: .topLeading) {
                    Color.black
                        .opacity(M.scrim * min(1, sheetValue) * (1 - M.ramp(dragValue, 0, sheetHeight)))
                        .frame(width: screen.width, height: screen.height)
                        .allowsHitTesting(false)

                    sheetView(screen: screen, insets: insets, sheetHeight: sheetHeight)
                        .offset(y: screen.height - sheetHeight + (1 - sheetValue) * sheetHeight + dragValue)
                }
            }

            flier(screen: screen, sheetHeight: sheetHeight, cardWidth: cardWidth, listTop: listTop)
        }
    }

    /// The app's own tab bar, over a fade so the list slides under it.
    private func tabBar(screen: CGSize, insets: EdgeInsets) -> some View {
        let fadeHeight = M.tabHeight + insets.bottom + 72
        return ZStack(alignment: .bottom) {
            LinearGradient(
                stops: [
                    .init(color: .white.opacity(0), location: 0),
                    .init(color: .white.opacity(0.92), location: 0.55),
                    .init(color: M.page, location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: fadeHeight)
            .frame(maxHeight: .infinity, alignment: .bottom)
            .allowsHitTesting(false)

            HStack(spacing: 4) {
                HStack(spacing: 6) {
                    SheetCardHandoffDoubleCheck()
                        .stroke(.white, style: StrokeStyle(lineWidth: 1.9, lineCap: .round, lineJoin: .round))
                        .frame(width: 20, height: 20)
                    Text("Tasks")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 16)
                .frame(height: 44)
                .background(M.ink, in: Capsule())
                tab("rectangle.stack", size: 17)
                tab("chart.bar", size: 16)
                tab("person.crop.circle", size: 19)
            }
            .padding(.horizontal, 8)
            .frame(height: M.tabHeight)
            .background(.white, in: Capsule())
            .overlay(Capsule().strokeBorder(.black.opacity(0.06), lineWidth: M.hairline / 2))
            .compositingGroup()
            .shadow(color: .black.opacity(0.12), radius: 20, y: 10)
            .padding(.bottom, max(insets.bottom, 12) + 4)
        }
        .frame(width: screen.width, height: screen.height, alignment: .bottom)
    }

    private func tab(_ symbol: String, size: Double) -> some View {
        Image(systemName: symbol)
            .font(.system(size: size))
            .foregroundStyle(M.mute)
            .frame(minWidth: 52, minHeight: 44)
    }

    private func sheetView(screen: CGSize, insets: EdgeInsets, sheetHeight: Double) -> some View {
        let total = sheetHeight + M.overshoot
        return ZStack(alignment: .top) {
            UnevenRoundedRectangle(topLeadingRadius: M.sheetRadius, topTrailingRadius: M.sheetRadius)
                .fill(.white)
                .shadow(color: .black.opacity(0.18), radius: 30, y: -8)

            Capsule()
                .fill(M.grabber)
                .frame(width: 36, height: 5)
                .padding(.top, 8)
                .frame(width: screen.width, height: total, alignment: .top)

            SheetCardHandoffReader(value: fan) { value in
                let titleT = M.smooth(M.ramp(value, 0, 0.5))
                VStack(spacing: 6) {
                    Text(title)
                        .font(.system(size: 34, weight: .black))
                        .tracking(-0.5)
                        .foregroundStyle(M.blue)
                        .frame(height: 40)
                    Text(caption)
                        .font(.system(size: 15, weight: .medium))
                        .lineSpacing(2)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(M.blueSoft)
                }
                .padding(.horizontal, 40)
                .scaleEffect(0.8 + 0.2 * titleT)
                .opacity(titleT)
                // Under the grabber: 8 above it, 5 for it, 18 below.
                .padding(.top, 31)
                .frame(width: screen.width, height: total, alignment: .top)

                ZStack {
                    fanCard(index: 0, value: value, color: M.backLeft, tilt: -M.fanTilt, spread: -M.fanSpread)
                    fanCard(index: 1, value: value, color: M.backRight, tilt: M.fanTilt, spread: M.fanSpread)
                    SheetCardHandoffFace(task: task)
                        .background(task.color, in: RoundedRectangle(cornerRadius: 16))
                        .compositingGroup()
                        .shadow(color: M.frontShadow.opacity(0.3), radius: 16, y: 10)
                        .opacity(flying ? 0 : 1)
                }
                .frame(width: M.fanWidth, height: M.fanHeight)
                .padding(.top, M.fanTop)
                .frame(width: screen.width, height: total, alignment: .top)
            }

            Button {
                close()
            } label: {
                Text(doneLabel)
                    .font(.system(size: 16, weight: .black))
                    .tracking(1)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(M.blue, in: Capsule())
                    .compositingGroup()
                    .shadow(color: M.doneShadow.opacity(0.35), radius: 12, y: 6)
            }
            .buttonStyle(SheetCardHandoffPressStyle(forced: autoDone, onDown: touched))
            .frame(width: screen.width - M.margin * 2, height: M.buttonHeight)
            .padding(.bottom, M.overshoot + max(insets.bottom, 0) + 20)
            .frame(width: screen.width, height: total, alignment: .bottom)
        }
        .frame(width: screen.width, height: total, alignment: .top)
        .contentShape(Rectangle())
        .gesture(pan(sheetHeight: sheetHeight))
        .frame(width: screen.width, height: total, alignment: .top)
    }

    private func fanCard(index: Int, value: Double, color: Color, tilt: Double, spread: Double) -> some View {
        let span = M.fanDuration + M.fanStagger * 2
        let t = M.smooth(M.ramp(value, Double(index) * M.fanStagger / span, (Double(index) * M.fanStagger + M.fanDuration) / span))
        return SheetCardHandoffBackFace()
            .frame(width: M.fanWidth, height: M.fanHeight, alignment: .top)
            .background(color)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .rotationEffect(.degrees(tilt * t))
            .offset(x: spread * t, y: 8 * t)
            .opacity(t)
    }

    private func flier(screen: CGSize, sheetHeight: Double, cardWidth: Double, listTop: Double) -> some View {
        SheetCardHandoffReader(value: fly) { p in
            let width = M.lerp(M.fanWidth, cardWidth, p)
            let height = M.lerp(M.fanHeight, M.cardHeight, p)
            let small = 1 - M.ramp(p, 0.15, 0.55)
            ZStack(alignment: .topLeading) {
                SheetCardHandoffFace(task: task).opacity(small)
                SheetCardHandoffTaskCard(task: task, width: cardWidth).opacity(1 - small)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .background(task.color)
            .clipShape(RoundedRectangle(cornerRadius: M.lerp(16, 20, p)))
            .shadow(color: task.color.opacity(0.3), radius: 14, y: 8)
            .offset(
                x: M.lerp((screen.width - M.fanWidth) / 2, M.margin, p),
                y: M.lerp(screen.height - sheetHeight + M.fanTop + flyFrom, listTop, p)
            )
            .opacity(flying ? 1 : 0)
            .allowsHitTesting(false)
        }
    }

    private func pan(sheetHeight: Double) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                if !dragging {
                    dragging = true
                    touched()
                }
                var t = Transaction()
                t.disablesAnimations = true
                withTransaction(t) { drag = max(0, value.translation.height) }
            }
            .onEnded { value in
                dragging = false
                let far = drag > M.dismissDrag || value.velocity.height > M.dismissVelocity
                if far {
                    // From here the sheet is where the finger left it, and the card starts from there too.
                    instantly {
                        flyFrom = drag
                        sheet = 1 - drag / sheetHeight
                        drag = 0
                    }
                    close()
                } else {
                    withAnimation(M.sheetSpring) { drag = 0 }
                }
            }
    }

    // MARK: Stages

    private func instantly(_ change: () -> Void) {
        var t = Transaction()
        t.disablesAnimations = true
        withTransaction(t, change)
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        autoNew = false
        autoDone = false
        touches += 1
    }

    private func open() {
        guard stage == .idle else { return }
        stage = .open
        if reduceMotion {
            withAnimation(.easeInOut(duration: M.fade)) { sheet = 1 }
            instantly { fan = 1 }
            return
        }
        withAnimation(M.sheetSpring) { sheet = 1 }
        withAnimation(.linear(duration: M.fanDuration + M.fanStagger * 2).delay(M.fanAt)) { fan = 1 }
    }

    private func arrived() {
        stage = .added
        added = true
        onAdd?(task)
    }

    private func close() {
        guard stage == .open else { return }
        stage = .closing
        if reduceMotion {
            withAnimation(.easeInOut(duration: M.fade)) {
                sheet = 0
                landed = 1
                shift = 1
                badge = 1
            }
            arrived()
            return
        }
        withAnimation(M.closeCurve) { sheet = 0 }
        flying = true
        withAnimation(M.flightSpring, completionCriteria: .logicallyComplete) {
            fly = 1
        } completion: {
            // The overlay and the list's own card swap in the same frame.
            instantly {
                flying = false
                landed = 1
            }
            arrived()
            badgeTask = Task { @MainActor in
                try? await Task.sleep(for: .seconds(M.badgeAfter), tolerance: .zero)
                guard !Task.isCancelled else { return }
                withAnimation(M.quad(0.16)) { badge = 1.15 }
                try? await Task.sleep(for: .seconds(0.16), tolerance: .zero)
                guard !Task.isCancelled else { return }
                withAnimation(M.quad(0.12)) { badge = 1 }
            }
        }
        withAnimation(M.flightSpring.delay(M.listAfter)) { shift = 1 }
    }

    private func clear() {
        guard stage == .added else { return }
        stage = .idle
        badgeTask?.cancel()
        withAnimation(M.quad(M.clearFor * 0.7)) { landed = 0 }
        withAnimation(M.quad(M.clearFor * 0.5)) { badge = 0 }
        withAnimation(M.cubic(M.clearFor)) { shift = 0 }
        added = false
    }

    /// Everything back as it was at the start, with no animation.
    private func rest() {
        badgeTask?.cancel()
        instantly {
            sheet = 0
            drag = 0
            fan = 0
            fly = 0
            flying = false
            flyFrom = 0
            shift = 0
            landed = 0
            badge = 0
            stage = .idle
            added = false
        }
        autoNew = false
        autoDone = false
    }

    // MARK: autoPlay

    private var steps: [(at: Double, run: () -> Void)] {
        [
            (M.tapNewAt, { autoNew = true }),
            (M.tapNewAt + M.tapFor, { autoNew = false; open() }),
            (M.tapDoneAt, { autoDone = true }),
            (M.tapDoneAt + M.tapFor, { autoDone = false; close() }),
            (M.clearAt, clear),
            (M.restAt, rest),
        ]
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            rest()
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for step in steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                step.run()
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private struct Pill: ViewModifier {
        func body(content: Content) -> some View {
            content
                .font(.system(size: 12, weight: .bold))
                .tracking(0.3)
                .foregroundStyle(M.ink)
                .padding(.horizontal, 12)
                .frame(height: M.pill)
                .background(M.page, in: Capsule())
                .overlay(Capsule().strokeBorder(M.outline, lineWidth: M.hairline))
        }
    }

    /// A coloured glow under each card, large and soft.
    private struct Lift: ViewModifier {
        let color: Color

        func body(content: Content) -> some View {
            content.compositingGroup().shadow(color: color.opacity(0.28), radius: 14, y: 8)
        }
    }
}

/// Squashes to `SheetCardHandoffMotion.pressScale` while pressed, or while the autoPlay loop holds it.
private struct SheetCardHandoffPressStyle: ButtonStyle {
    let forced: Bool
    let onDown: () -> Void

    func makeBody(configuration: Configuration) -> some View {
        let down = configuration.isPressed || forced
        configuration.label
            .scaleEffect(down ? SheetCardHandoffMotion.pressScale : 1)
            .animation(.easeInOut(duration: down ? 0.08 : 0.14), value: down)
            .onChange(of: configuration.isPressed) { _, pressed in if pressed { onDown() } }
    }
}

/// The front card of the fan: a small printed card with a title, a lede and three dated lines.
private struct SheetCardHandoffFace: View {
    let task: SheetCardHandoffTask

    var body: some View {
        VStack(spacing: 0) {
            Text(task.name)
                .font(.system(size: 14, weight: .heavy))
                .tracking(-0.2)
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .frame(maxWidth: .infinity)
            Text("Lessons on building from scratch")
                .font(.system(size: 9, weight: .semibold))
                .foregroundStyle(.white.opacity(0.85))
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .frame(maxWidth: .infinity)
                .padding(.top, 3)
            Rectangle()
                .fill(.white.opacity(0.35))
                .frame(height: SheetCardHandoffMotion.hairline)
                .padding(.horizontal, -12)
                .padding(.top, 9)
                .padding(.bottom, 7)
            ForEach(SheetCardHandoffMotion.frontLines, id: \.line) { entry in
                VStack(alignment: .leading, spacing: 0) {
                    Text("– \(entry.line)")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(entry.note)
                        .font(.system(size: 7, weight: .medium))
                        .foregroundStyle(.white.opacity(0.72))
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.bottom, 5)
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 14)
        .frame(width: SheetCardHandoffMotion.fanWidth, height: SheetCardHandoffMotion.fanHeight, alignment: .top)
        .background(
            LinearGradient(colors: [.white.opacity(0.24), .white.opacity(0)], startPoint: .topLeading, endPoint: UnitPoint(x: 0.7, y: 0.8))
        )
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

/// A card behind the front one: only its top edge shows, so a ruled title is enough.
private struct SheetCardHandoffBackFace: View {
    var body: some View {
        VStack(spacing: 5) {
            Capsule().fill(.white.opacity(0.4)).frame(width: SheetCardHandoffMotion.fanWidth * 0.7, height: 5)
            Capsule().fill(.white.opacity(0.4)).frame(width: SheetCardHandoffMotion.fanWidth * 0.48, height: 5).opacity(0.6)
        }
        .padding(.top, 14)
    }
}

/// Two ticks, the second one offset to the right: the Tasks tab's icon.
private struct SheetCardHandoffDoubleCheck: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 20
        var path = Path()
        path.move(to: CGPoint(x: 1.5 * s, y: 10.5 * s))
        path.addLine(to: CGPoint(x: 5.5 * s, y: 14.5 * s))
        path.addLine(to: CGPoint(x: 13 * s, y: 6 * s))
        path.move(to: CGPoint(x: 9 * s, y: 14.5 * s))
        path.addLine(to: CGPoint(x: 18.5 * s, y: 6 * s))
        return path.offsetBy(dx: rect.minX, dy: rect.minY)
    }
}

enum SheetCardHandoffMotion {
    static let task = SheetCardHandoffTask(
        name: "Founder Playbook",
        caption: "Short lessons on building from scratch, read every morning before work.",
        time: "7:30AM", color: Color(hex: 0x4dd0cb)
    )
    static let existing = SheetCardHandoffTask(
        name: "Investor Mindset",
        caption: "Mental models for calm decisions, with one worked case study a day.",
        time: "9:00AM", color: Color(hex: 0xf58a4b), progress: 0.6
    )
    static let later = [
        SheetCardHandoffTask(
            name: "Deep Work Sprint",
            caption: "Ninety focused minutes on the one thing that moves the week forward.",
            time: "1:30PM", color: Color(hex: 0x8c7cf0), status: .later, progress: 0
        ),
        SheetCardHandoffTask(
            name: "Evening Reflection",
            caption: "Three questions to close the day, and one line to remember it by.",
            time: "9:00PM", color: Color(hex: 0xf2b63d), status: .later, progress: 0
        ),
    ]
    static let frontLines: [(line: String, note: String)] = [
        ("Ship the smallest thing that teaches you something.", "Day 1, on first versions"),
        ("Talk to ten users before you write a line of code.", "Day 2, on listening"),
        ("Say no to a hundred good ideas.", "Day 3, on focus"),
    ]

    /// One timeline for the autoPlay loop, in seconds from its start.
    static let loop = 9.0
    static let tapNewAt = 0.9
    static let tapFor = 0.15
    static let tapDoneAt = 3.9
    static let clearAt = 7.3
    static let restAt = 8.2
    /// Seconds a touch keeps the loop away.
    static let userHold = 3.0

    static let page = Color.white
    static let ink = Color(hex: 0x17181a)
    static let mute = Color(hex: 0x8d8f94)
    static let outline = Color(hex: 0xe3e4e6)
    static let well = Color(hex: 0xf5f5f6)
    static let wellInk = Color(hex: 0xa9abb0)
    static let dash = Color(hex: 0xd6d7da)
    static let dot = Color(hex: 0x34c759)
    static let grabber = Color(hex: 0xe2e3e6)
    static let blue = Color(hex: 0x4ab8f0)
    static let blueSoft = Color(hex: 0x8ccbf0)
    static let doneShadow = Color(hex: 0x1d8fd0)
    static let backLeft = Color(hex: 0x3fc2bc)
    static let backRight = Color(hex: 0x38b5af)
    static let frontShadow = Color(hex: 0x0b6f6b)
    static let badge = Color(hex: 0xf2364c)
    static let scrim = 0.55
    /// React Native's hairline width doubled, on a 3x screen.
    static let hairline = 2.0 / 3

    static let margin = 20.0
    static let pill = 28.0
    static let titleTop = 48.0
    static let newTop = 132.0
    static let newHeight = 52.0
    static let gap = 16.0
    static let listTop = newTop + newHeight + 16
    static let cardHeight = SheetCardHandoffMetrics.cardHeight
    static let slot = cardHeight + gap
    /// The sheet is built taller than it shows, so a spring that overshoots does not lift it off the bottom.
    static let sheetHeight = 432.0
    static let overshoot = 48.0
    static let sheetRadius = 28.0
    static let buttonHeight = 56.0
    static let fanTop = 132.0
    static let fanWidth = 150.0
    static let fanHeight = 186.0
    static let fanTilt = 7.0
    static let fanSpread = 22.0
    static let tabHeight = 60.0
    static let fanStagger = 0.08
    static let fanDuration = 0.5
    static let fanAt = 0.7
    static let pressScale = 0.95
    static let dismissDrag = 90.0
    static let dismissVelocity = 800.0

    static let sheetSpring = Animation.interpolatingSpring(mass: 1, stiffness: 190, damping: 17)
    static let flightSpring = Animation.spring(duration: 0.45, bounce: 0.2)
    static let closeCurve = Animation.timingCurve(0.5, 0, 1, 1, duration: 0.35)
    static let fade = 0.25
    /// The list starts to move this long after the card leaves the sheet.
    static let listAfter = 0.25
    static let badgeAfter = 1.2
    static let clearFor = 0.6

    /// Reanimated's default easing for `withTiming`.
    static func quad(_ duration: Double) -> Animation { .timingCurve(0.455, 0.03, 0.515, 0.955, duration: duration) }
    static func cubic(_ duration: Double) -> Animation { .timingCurve(0.645, 0.045, 0.355, 1, duration: duration) }

    static func lerp(_ from: Double, _ to: Double, _ t: Double) -> Double { from + (to - from) * t }
    static func ramp(_ value: Double, _ from: Double, _ to: Double) -> Double { min(1, max(0, (value - from) / (to - from))) }
    /// Soft start and end of a 0 → 1 run.
    static func smooth(_ t: Double) -> Double { t * t * (3 - 2 * t) }
}
