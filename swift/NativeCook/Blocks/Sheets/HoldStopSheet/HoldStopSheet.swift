import SwiftUI

/// A focus timer. "Start Focus" drops the tomatoes away and the day turns peach; "Hold To Stop
/// Focus" fills a line while it is held, and stopping early resets the timer, drops the tomatoes
/// back with bounces and warns in a sheet. The autoPlay loop presses and holds the same controls.
struct HoldStopSheet: View {
    /// The length of a focus session, in seconds.
    var duration = 300
    var startLabel = "Start Focus"
    var holdLabel = "Hold To Stop Focus"
    var sheetTitle = "Focus time is less than 5 minutes"
    var sheetBody = "Pomodoros lasting less than 5 minutes will not be recorded. Keep going! Every focus session will get better and better (^_^)"
    var gotItLabel = "Got it"
    /// Starts, holds Stop to the end and dismisses the sheet by itself every `HoldStopSheet.loop` seconds.
    var autoPlay = false
    var onStart: (() -> Void)? = nil
    /// Called when the hold completes, with the seconds that were left.
    var onStop: ((_ secondsLeft: Int) -> Void)? = nil

    /// Seconds for one pass of the autoPlay loop.
    static let loop = 10.0
    // The timeline, in seconds from the start of a loop.
    // The pile is on screen from the end of one loop to Start, and again from the stop on.
    private static let pressStartAt = 1.25
    private static let startAt = 1.4
    private static let holdAt = 2.6
    private static let pressGotItAt = 7.3
    private static let dismissAt = 7.5
    /// Seconds after the last touch before the loop starts again from rest.
    private static let userHold = 3.0

    private enum Pressed { case none, start, gotIt }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var running = false
    @State private var left = 300
    @State private var sheetOpen = false
    @State private var autoPressed = Pressed.none
    @State private var holding = false
    @State private var countdown: Task<Void, Never>? = nil
    @State private var ticking: Task<Void, Never>? = nil
    /// Ends the hold when the line is full; timed from the press, not from the animation's end.
    @State private var holdEnd: Task<Void, Never>? = nil
    @State private var touches = 0
    @State private var touching = false
    @State private var tick = UIImpactFeedbackGenerator(style: .light)
    @State private var run = 0.0
    @State private var footer = 0.0
    @State private var fill = 0.0
    @State private var fall = 0.0
    @State private var rise = 0.0
    @State private var dim = 0.0
    @State private var sheet = 0.0
    @State private var pulse = 1.0
    /// When the pile started to rain back in, until it is reset.
    @State private var riseFrom: Date? = nil

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            screen(width: width, height: height, insets: insets)
                .frame(width: width, height: height, alignment: .topLeading)
                .clipped()
                .ignoresSafeArea()
        }
        .environment(\.colorScheme, .light)
        .preferredColorScheme(.light)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !touching else { return }
                    touching = true
                    touched()
                }
                .onEnded { _ in touching = false }
        )
        .onAppear {
            left = duration
            tick.prepare()
        }
        .onDisappear { clearTimers() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await playLoop() }
    }

    // MARK: Layout

    private func screen(width: Double, height: Double, insets: EdgeInsets) -> some View {
        let bottom = max(insets.bottom, 12)
        let text = Self.clock(left)

        return ZStack(alignment: .topLeading) {
            LinearGradient(stops: [.init(color: M.idleTop, location: 0), .init(color: .white, location: 0.6)], startPoint: .top, endPoint: .bottom)
            LinearGradient(
                stops: [.init(color: M.peachTop, location: 0), .init(color: M.peachMid, location: 0.6), .init(color: .white, location: 1)],
                startPoint: .top, endPoint: .bottom
            )
            .modifier(Driven(run) { content, p in content.opacity(p) })

            Image(systemName: "bookmark.fill")
                .font(.system(size: 17))
                .foregroundStyle(M.bookmark)
                .frame(width: 20, height: 20)
                .offset(x: width - 24 - 20, y: insets.top + 6)

            VStack(spacing: 4) {
                HStack(spacing: 0) {
                    ForEach(Array(text.enumerated()), id: \.offset) { _, char in
                        ZStack {
                            Text(String(char))
                                .font(.system(size: M.timerSize, weight: .heavy).monospacedDigit())
                                .tracking(-1)
                                .foregroundStyle(M.ink)
                                .fixedSize()
                                .id(char)
                                .transition(reduceMotion ? .identity : .asymmetric(
                                    insertion: .offset(y: M.rollRise).combined(with: .opacity),
                                    removal: .offset(y: -M.rollRise).combined(with: .opacity)
                                ))
                        }
                        .frame(width: char == ":" ? M.colonWidth : M.digitWidth, height: M.timerHeight)
                    }
                }
                .animation(.linear(duration: M.roll), value: text)
                .accessibilityElement()
                .accessibilityLabel(text)
                .accessibilityAddTraits(.updatesFrequently)
                HStack(spacing: 2) {
                    Text("Focus").font(.system(size: 20, weight: .bold)).foregroundStyle(M.ink)
                    Image(systemName: "chevron.forward").font(.system(size: 13, weight: .semibold)).foregroundStyle(M.mute)
                }
                .frame(height: 26)
                .modifier(Driven(run) { content, p in content.opacity(1 - p) })
            }
            .frame(width: width)
            .modifier(Driven(run) { content, p in content.offset(y: height * (0.22 + 0.1 * p)) })
            .allowsHitTesting(false)

            // Two separate drivers, so the fall and the rise keep their own animations.
            Color.clear
                .modifier(Driven(fall) { content, f in
                    content.modifier(Driven(rise) { _, r in
                        HoldStopSheetTomatoes(width: width, drop: height, fall: f, rise: r, reduced: reduceMotion)
                    })
                })
                .frame(width: width, height: height)
                .allowsHitTesting(false)

            startButton
                .frame(width: width)
                .modifier(Driven(fall) { content, f in
                    content.modifier(Driven(rise) { content, r in
                        let down = min(1, f * 1.6)
                        let back = min(1, max(0, (r - 0.55) / 0.45))
                        content
                            .opacity(reduceMotion ? 1 - down * (1 - r) : 1)
                            .offset(y: reduceMotion ? 0 : r > 0 ? -height * (1 - holdStopSheetBounce(back)) : 480 * down * down)
                    })
                })
                .offset(y: height - holdStopSheetPileHeight * 0.5 - M.startHeight)
                .allowsHitTesting(!running && !sheetOpen)

            footerView
                .frame(width: width)
                .modifier(Driven(footer) { content, p in
                    content.modifier(Driven(fill) { content, f in content.opacity(p * (1 - 0.45 * f)) })
                })
                .offset(y: height - (bottom + 12) - M.footerHeight)
                .allowsHitTesting(running)

            Color.black
                .modifier(Driven(dim) { content, p in content.opacity(p) })
                .allowsHitTesting(sheetOpen)

            sheetView
                .frame(width: width - 2 * M.sheetMargin, height: M.sheetHeight)
                .modifier(Driven(sheet) { content, p in
                    content
                        .opacity(reduceMotion ? p : 1)
                        .offset(y: reduceMotion ? 0 : (1 - p) * (M.sheetHeight + bottom + M.sheetMargin + 24))
                })
                .offset(x: M.sheetMargin, y: height - max(M.sheetMargin, bottom - 8) - M.sheetHeight)
                .allowsHitTesting(sheetOpen)
        }
    }

    private var startButton: some View {
        Button(action: start) {
            Text(startLabel)
                .font(.system(size: 18, weight: .bold))
                .tracking(-0.2)
                .foregroundStyle(.white)
                .frame(width: M.startWidth, height: M.startHeight)
        }
        .buttonStyle(HoldStopSheetButtonStyle(normal: M.button, pressed: M.buttonPressed, held: autoPressed == .start, shadows: M.startShadows))
        .accessibilityLabel(startLabel)
        .frame(maxWidth: .infinity)
    }

    private var footerView: some View {
        VStack(spacing: 12) {
            ZStack(alignment: .leading) {
                Capsule().fill(M.track)
                Capsule().fill(M.ink)
                    .modifier(Driven(fill) { content, f in content.frame(width: M.holdLineWidth * f) })
            }
            .frame(width: M.holdLineWidth, height: M.holdLineHeight)
            .clipShape(Capsule())
            Text(holdLabel)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(M.ink)
                .frame(height: 20)
        }
        .padding(.horizontal, 40)
        .padding(.vertical, 8)
        // The hit area reaches 16 pt past the label, as `hitSlop` did.
        .padding(16)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in beginHold(felt: true) }
                .onEnded { _ in endHold() }
        )
        .padding(-16)
        .accessibilityElement()
        .accessibilityLabel(holdLabel)
        .accessibilityAddTraits(.isButton)
    }

    private var sheetView: some View {
        VStack(spacing: 0) {
            Capsule().fill(M.grabber).frame(width: 40, height: 5).padding(.bottom, 18)
            Text(sheetTitle)
                .font(.system(size: 20, weight: .heavy))
                .tracking(-0.3)
                .foregroundStyle(M.cardTitle)
                .multilineTextAlignment(.center)
                .frame(minHeight: 26)
            Text(sheetBody)
                .font(.system(size: 14))
                .lineSpacing(3)
                .foregroundStyle(M.cardBody)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
                .padding(.bottom, 20)
            Button { dismiss() } label: {
                Text(gotItLabel)
                    .font(.system(size: 17, weight: .heavy))
                    .foregroundStyle(.white)
                    .frame(width: M.gotItWidth, height: M.gotItHeight)
            }
            .buttonStyle(HoldStopSheetButtonStyle(normal: M.gotIt, pressed: M.gotItPressed, held: autoPressed == .gotIt, shadows: M.gotItShadows))
            .modifier(Driven(pulse) { content, p in content.scaleEffect(p) })
            .accessibilityLabel(gotItLabel)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 28)
        .padding(.top, 10)
        .frame(maxWidth: .infinity)
        .background {
            let shape = RoundedRectangle(cornerRadius: M.sheetRadius, style: .continuous)
            shape.fill(M.card)
                .shadow(color: .rgba(40, 20, 10, 0.35), radius: 24, y: 20)
                .shadow(color: .rgba(40, 20, 10, 0.15), radius: 3, y: 2)
        }
    }

    // MARK: Actions

    private func animate(_ value: Binding<Double>, to target: Double, _ animation: Animation?) {
        withAnimation(animation) { value.wrappedValue = target }
    }

    private func clearTimers() {
        countdown?.cancel()
        countdown = nil
        ticking?.cancel()
        ticking = nil
        holdEnd?.cancel()
        holdEnd = nil
    }

    private func haptic() {
        tick.impactOccurred()
        tick.prepare()
    }

    private func start() {
        guard !running else { return }
        autoPressed = .none
        running = true
        left = duration
        animate($rise, to: 0, nil)
        animate($fall, to: 1, M.timing(reduceMotion ? M.fade : M.fall, curve: M.quadIn))
        animate($run, to: 1, M.timing(M.background, curve: M.quadInOut))
        animate($footer, to: 1, M.timing(M.fade).delay(M.footerAfter / 1000))
        // One second off the timer, each at its own absolute time from the start.
        countdown = Task { @MainActor in
            let clock = ContinuousClock()
            let origin = clock.now
            for second in 1...max(1, duration) {
                try? await clock.sleep(until: origin + .seconds(second), tolerance: .zero)
                guard !Task.isCancelled else { return }
                left = duration - second
            }
        }
        onStart?()
    }

    private func stop() {
        holding = false
        clearTimers()
        onStop?(left)
        running = false
        left = duration
        sheetOpen = true
        animate($run, to: 0, M.timing(M.background, curve: M.quadInOut))
        animate($footer, to: 0, M.timing(M.fade))
        animate($fill, to: 0, nil)
        animate($rise, to: 0, nil)
        animate($rise, to: 1, .linear(duration: (reduceMotion ? M.fade : M.rise) / 1000))
        riseFrom = .now
        animate($dim, to: M.dimTo, M.timing(M.dimIn))
        if reduceMotion {
            animate($sheet, to: 1, M.timing(M.fade).delay(M.fade / 1000))
        } else {
            animate($sheet, to: 1, M.sheetSpring.delay(M.sheetAfter / 1000))
        }
        withAnimation(M.timing(150).delay((M.pulseAfter + M.sheetAfter) / 1000), completionCriteria: .logicallyComplete) {
            pulse = 1.08
        } completion: {
            animate($pulse, to: 1, M.timing(250))
        }
    }

    private func beginHold(felt: Bool) {
        guard running, !holding else { return }
        holding = true
        animate($fill, to: 1, .linear(duration: M.hold / 1000))
        holdEnd = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(M.hold), tolerance: .zero)
            guard !Task.isCancelled, holding else { return }
            stop()
        }
        if felt {
            haptic()
            ticking = Task { @MainActor in
                while !Task.isCancelled {
                    try? await Task.sleep(for: .milliseconds(M.tick), tolerance: .zero)
                    guard !Task.isCancelled else { return }
                    haptic()
                }
            }
        }
    }

    private func endHold() {
        guard holding else { return }
        holding = false
        ticking?.cancel()
        ticking = nil
        holdEnd?.cancel()
        holdEnd = nil
        animate($fill, to: 0, M.timing(250))
    }

    private func dismiss() {
        autoPressed = .none
        sheetOpen = false
        let ms = reduceMotion ? M.fade : 450
        animate($dim, to: 0, M.timing(ms))
        withAnimation(M.timing(ms, curve: M.cubicIn), completionCriteria: .logicallyComplete) {
            sheet = 0
        } completion: {
            // The pile is back in place behind the sheet; only the pieces of the animation reset.
            guard !sheetOpen else { return }
            animate($fall, to: 0, nil)
            animate($rise, to: 0, nil)
            riseFrom = nil
        }
    }

    private func rest() {
        clearTimers()
        holding = false
        running = false
        left = duration
        sheetOpen = false
        autoPressed = .none
        for value in [$run, $footer, $fill, $dim, $sheet] { animate(value, to: 0, M.timing(M.restFade)) }
        // A pile that is back in place stays; one that is away rises back from below.
        let landed = riseFrom.map { Date.now.timeIntervalSince($0) * 1000 >= 0.99 * (reduceMotion ? M.fade : M.rise) } ?? false
        animate($rise, to: 0, nil)
        animate($fall, to: 0, landed ? nil : M.timing(M.restFade))
        riseFrom = nil
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        autoPressed = .none
        touches += 1
    }

    private func playLoop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(for: .seconds(Self.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            rest()
        }
        let steps: [(Double, () -> Void)] = [
            (Self.pressStartAt, { autoPressed = .start }),
            (Self.startAt, start),
            (Self.holdAt, { beginHold(felt: false) }),
            (Self.pressGotItAt, { autoPressed = .gotIt }),
            (Self.dismissAt, dismiss),
        ]
        var origin = clock.now
        while !Task.isCancelled {
            for (at, run) in steps {
                try? await clock.sleep(until: origin + .seconds(at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                run()
            }
            origin += .seconds(Self.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private static func clock(_ seconds: Int) -> String {
        let left = max(0, seconds)
        return String(format: "%02d:%02d", left / 60, left % 60)
    }
}

// MARK: Support

/// Re-evaluates `transform` on every frame of an animation of `value`, so a number that moves
/// under a spring can drive several properties through non-linear maps.
private struct Driven: ViewModifier, Animatable {
    var value: Double
    private let transform: (Content, Double) -> AnyView

    init<Result: View>(_ value: Double, @ViewBuilder _ transform: @escaping (Content, Double) -> Result) {
        self.value = value
        self.transform = { AnyView(transform($0, $1)) }
    }

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    func body(content: Content) -> some View { transform(content, value) }
}

private struct HoldStopSheetButtonStyle: ButtonStyle {
    let normal: Color
    let pressed: Color
    let held: Bool
    /// Box shadows as colour, Gaussian radius and drop.
    let shadows: [(Color, Double, Double)]

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background {
                ZStack {
                    ForEach(shadows.indices, id: \.self) { index in
                        let (color, radius, y) = shadows[index]
                        Capsule().fill(color).offset(y: y).blur(radius: radius)
                    }
                    Capsule().fill(held || configuration.isPressed ? pressed : normal)
                }
            }
            .contentShape(Capsule())
    }
}

private typealias M = HoldStopSheetMetrics

private enum HoldStopSheetMetrics {
    static let idleTop = Color(hex: 0xfde3d2)
    static let peachTop = Color(hex: 0xf7ae88)
    static let peachMid = Color(hex: 0xfde3d1)
    static let ink = Color(hex: 0x4a3b35)
    static let mute = Color(hex: 0x8c7a71)
    static let bookmark = Color(hex: 0xd9a58b)
    static let track = Color.rgba(74, 59, 53, 0.16)
    static let button = Color(hex: 0x4a403b)
    static let buttonPressed = Color(hex: 0x2e2724)
    static let card = Color(hex: 0xf6ece4)
    static let cardTitle = Color(hex: 0x3e2f28)
    static let cardBody = Color(hex: 0x77675e)
    static let grabber = Color.rgba(62, 47, 40, 0.18)
    static let gotIt = Color(hex: 0xd0504a)
    static let gotItPressed = Color(hex: 0xab3c37)
    // Box shadows: a blur of B is a Gaussian of B / 2.
    static let startShadows: [(Color, Double, Double)] = [(.rgba(74, 40, 25, 0.35), 12, 10), (.rgba(74, 40, 25, 0.25), 2, 2)]
    static let gotItShadows: [(Color, Double, Double)] = [(.rgba(208, 80, 74, 0.35), 9, 8)]

    static let timerSize = 84.0
    static let digitWidth = 52.0
    static let colonWidth = 24.0
    static let timerHeight = 100.0
    static let startWidth = 156.0
    static let startHeight = 56.0
    static let holdLineWidth = 140.0
    static let holdLineHeight = 4.0
    static let footerHeight = 8 + 4 + 12 + 20 + 8.0
    static let sheetMargin = 10.0
    static let sheetHeight = 244.0
    static let sheetRadius = 36.0
    static let gotItWidth = 188.0
    static let gotItHeight = 52.0

    // The durations of the pieces, in milliseconds.
    static let hold = 2400.0
    static let background = 500.0
    static let fall = 650.0
    static let rise = 1500.0
    static let roll = 0.2
    static let rollRise = 12.0
    static let dimTo = 0.45
    static let dimIn = 300.0
    static let sheetAfter = 700.0
    static let pulseAfter = 700.0
    static let footerAfter = 700.0
    static let fade = 180.0
    static let restFade = 350.0
    static let sheetSpring = Animation.interpolatingSpring(mass: 1, stiffness: 180, damping: 15)
    /// Milliseconds between the light ticks while the hold fills.
    static let tick = 500

    static let cubicOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.33, y: 1), endControlPoint: UnitPoint(x: 0.68, y: 1))
    static let cubicIn = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.32, y: 0), endControlPoint: UnitPoint(x: 0.67, y: 0))
    static let quadIn = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.11, y: 0), endControlPoint: UnitPoint(x: 0.5, y: 0))
    static let quadInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.455, y: 0.03), endControlPoint: UnitPoint(x: 0.515, y: 0.955))

    /// A timed animation of `ms` milliseconds, cubic ease-out unless told otherwise.
    static func timing(_ ms: Double, curve: UnitCurve = cubicOut) -> Animation {
        .timingCurve(curve, duration: ms / 1000)
    }
}
