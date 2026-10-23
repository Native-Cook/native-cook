import SwiftUI

/// A first launch that starts with a breath. The splash gives way to an orange tide with a
/// sleeping face on it; holding the screen makes it rise ("Breathe in"), and at the top it sinks
/// again by itself, slower ("Breathe out"). After that one breath the welcome fades in: a picture
/// on a yellow panel, a consent box and two buttons.
struct BreathingTideWelcome: View {
    /// The name next to the dot on the splash.
    var wordmark = "stillwater"
    var inhaleLabel = "Breathe in"
    var exhaleLabel = "Breathe out"
    var title = "Welcome to Stillwater"
    var subtitle = "Support for all of life\u{2019}s moments"
    /// The line next to the checkbox, in turns plain and as a link.
    var consent = ["I agree to Stillwater\u{2019}s ", "Terms & Conditions", " and acknowledge the ", "Privacy Policy", "."]
    var primaryLabel = "Create an account"
    var secondaryLabel = "Log in"
    /// The tide and the yellow panel.
    var tideColor = Color(hex: BreathingTideWelcomeArt.tideInk)
    var panelColor = Color(hex: BreathingTideWelcomeArt.panelInk)
    /// Takes one breath, shows the welcome and comes back to the splash by itself, every `BreathingTideWelcomeMotion.loop` seconds.
    var autoPlay = false
    /// Called when the breath is complete and the welcome shows.
    var onBreath: (() -> Void)? = nil
    var onAgree: ((Bool) -> Void)? = nil
    var onPrimary: (() -> Void)? = nil
    var onSecondary: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var calm
    /// The scene time while nothing moves: 0 on the splash, `atLow` at the low tide, `atC` on the welcome.
    @State private var rest = 0.0
    /// The scene on its way from one time to another.
    @State private var run: Run? = nil
    @State private var runTask: Task<Void, Never>? = nil
    /// The breath in progress: whose it is, for the haptic at its top and for a touch that interrupts.
    @State private var holder: Holder? = nil
    /// A finger is on the tide, and one is on the screen.
    @State private var pressing = false
    @State private var touching = false
    @State private var agreed = false
    /// When the block appeared, for the ornaments.
    @State private var appeared = Date.now
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var topHaptic = UIImpactFeedbackGenerator(style: .medium)
    @State private var tickHaptic = UISelectionFeedbackGenerator()

    private enum Holder { case hand, auto }

    /// The scene runs from one time to another at an even pace.
    private struct Run {
        let from: Double
        let to: Double
        let start: Date
        let duration: Double

        func scene(at date: Date) -> Double {
            guard duration > 0 else { return to }
            return from + (to - from) * min(max(date.timeIntervalSince(start) / duration, 0), 1)
        }
    }

    private var atWelcome: Bool { run == nil && rest == M.atC }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let cx = size.width / 2

            // The ornaments float while the welcome is at rest; nothing else moves by itself.
            TimelineView(.animation(paused: run == nil && (calm || rest != M.atC))) { timeline in
                let s = run?.scene(at: timeline.date) ?? rest
                let there = M.welcome(s)

                ZStack(alignment: .topLeading) {
                    Palette.paper

                    HStack(spacing: Metrics.logoGap) {
                        Circle().fill(Palette.dot).frame(width: Metrics.logoDot, height: Metrics.logoDot)
                        Text(wordmark)
                            .font(.system(size: 34, weight: .medium, design: .rounded))
                            .foregroundStyle(Palette.ink)
                            .lineLimit(1)
                    }
                    .opacity(M.logoAlpha(s))
                    .position(x: cx, y: size.height * Metrics.logoAt)

                    Canvas { context, _ in
                        BreathingTideWelcomeArt.tide(&context, s: s, calm: calm, size: size, color: tideColor)
                    }
                    .accessibilityHidden(true)

                    word(inhaleLabel).opacity(M.inAlpha(s)).position(x: cx, y: insets.top + Metrics.wordY)
                    word(exhaleLabel).opacity(M.outAlpha(s)).position(x: cx, y: insets.top + Metrics.wordY)

                    breathTarget

                    if there > 0 {
                        welcome(s: s, clock: calm ? nil : timeline.date.timeIntervalSince(appeared), size: size, top: insets.top, bottom: max(insets.bottom, 16))
                            .compositingGroup()
                            .opacity(there)
                            .allowsHitTesting(atWelcome)
                            .accessibilityHidden(!atWelcome)
                    }
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    if !touching {
                        touching = true
                        touched()
                    }
                }
                .onEnded { _ in touching = false }
        )
        .onAppear {
            tapHaptic.prepare()
            topHaptic.prepare()
        }
        .onDisappear { runTask?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private func word(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 26, weight: .semibold, design: .rounded))
            .foregroundStyle(Palette.text)
            .lineLimit(1)
            .allowsHitTesting(false)
    }

    /// Anywhere on the screen takes the breath.
    private var breathTarget: some View {
        Color.clear
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        if !pressing {
                            pressing = true
                            press(.hand)
                        }
                    }
                    .onEnded { _ in
                        pressing = false
                        release()
                    }
            )
            .allowsHitTesting(!atWelcome)
            .accessibilityElement()
            .accessibilityLabel("\(inhaleLabel). Press and hold")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { press(.hand) }
            .accessibilityHidden(atWelcome)
    }

    // MARK: The welcome

    private func welcome(s: Double, clock: Double?, size: CGSize, top: Double, bottom: Double) -> some View {
        let cx = size.width / 2
        let crest = top + Metrics.crestY
        // The yellow panel comes down as it fades in.
        let slide = calm || s > M.atC ? 0 : -M.panelSlide * (1 - M.welcome(s))
        return ZStack(alignment: .topLeading) {
            panelColor
                .frame(height: crest + Metrics.crestDrop + M.panelSlide * 2)
                .offset(y: slide - M.panelSlide)
            BreathingTideWelcomeSheet(crest: crest, drop: Metrics.crestDrop).fill(Palette.paper)
            Canvas { context, _ in
                BreathingTideWelcomeArt.picture(&context, clock: clock, center: CGPoint(x: cx, y: top + Metrics.pictureY))
            }
            .offset(y: slide)
            .accessibilityHidden(true)
            .allowsHitTesting(false)

            Text(title)
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(Palette.text)
                .lineLimit(1)
                .position(x: cx, y: crest + Metrics.titleBelow)
            Text(subtitle)
                .font(.system(size: 16))
                .foregroundStyle(Palette.soft)
                .lineLimit(1)
                .position(x: cx, y: crest + Metrics.subtitleBelow)

            Button {
                agree(.hand)
            } label: {
                HStack(spacing: 12) {
                    checkbox
                    Text(consentText)
                        .font(.system(size: 11))
                        .lineSpacing(max(0, 16 - UIFont.systemFont(ofSize: 11).lineHeight))
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(height: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(agreed ? .isSelected : [])
            .frame(width: size.width - Metrics.consentMargin * 2)
            .position(x: cx, y: size.height - bottom - Metrics.consentUp)

            Button {
                tapHaptic.impactOccurred()
                onPrimary?()
            } label: {
                label(primaryLabel, ink: .white, fill: Palette.blue)
            }
            .buttonStyle(DimStyle(dimmed: 0.8))
            .disabled(!agreed)
            .opacity(agreed ? 1 : 0.4)
            .animation(.easeOut(duration: 0.2), value: agreed)
            .frame(width: size.width - Metrics.buttonMargin * 2, height: Metrics.button)
            .position(x: cx, y: size.height - bottom - Metrics.primaryUp)

            Button {
                tapHaptic.impactOccurred()
                onSecondary?()
            } label: {
                label(secondaryLabel, ink: Palette.text, fill: Palette.blush)
                    .shadow(color: .black.opacity(0.08), radius: 6, y: 4)
            }
            .buttonStyle(DimStyle(dimmed: 0.7))
            .frame(width: size.width - Metrics.buttonMargin * 2, height: Metrics.button)
            .position(x: cx, y: size.height - bottom - Metrics.secondaryUp)
        }
    }

    private func label(_ text: String, ink: Color, fill: Color) -> some View {
        Text(text)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(ink)
            .lineLimit(1)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Capsule().fill(fill))
            .contentShape(Capsule())
    }

    /// The box fills and the tick lands on a spring.
    private var checkbox: some View {
        RoundedRectangle(cornerRadius: 4, style: .circular)
            .fill(Palette.text.opacity(agreed ? 1 : 0))
            .overlay(RoundedRectangle(cornerRadius: 4, style: .circular).strokeBorder(Palette.text, lineWidth: 2))
            .overlay(
                BreathingTideWelcomeTick()
                    .stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                    .scaleEffect(agreed ? 1 : 0)
            )
            .frame(width: Metrics.box, height: Metrics.box)
            .animation(calm ? nil : .interpolatingSpring(mass: 1, stiffness: M.tickStiffness, damping: M.tickDamping), value: agreed)
    }

    private var consentText: AttributedString {
        var whole = AttributedString()
        for (index, part) in consent.enumerated() {
            var piece = AttributedString(part)
            piece.foregroundColor = index % 2 == 1 ? Palette.blue : Palette.text
            if index % 2 == 1 { piece.underlineStyle = .single }
            whole += piece
        }
        return whole
    }

    // MARK: Behaviour

    private func scene(at date: Date) -> Double { run?.scene(at: date) ?? rest }

    /// Runs the scene from where it is to `to`, and leaves it at rest on `landing`. The loop passes
    /// the moment the run was due, so a late wake-up does not shift it.
    private func begin(to: Double, duration: Double, landing: Double, at date: Date) {
        let from = scene(at: date)
        runTask?.cancel()
        run = Run(from: from, to: to, start: date, duration: duration)
        let clock = ContinuousClock()
        let start = clock.now - .seconds(Date.now.timeIntervalSince(date))
        runTask = Task { @MainActor in
            if from < M.atTop, to > M.atTop {
                try? await clock.sleep(until: start + .seconds(M.atTop - from), tolerance: .zero)
                guard !Task.isCancelled else { return }
                if holder == .hand { topHaptic.impactOccurred() }
                holder = nil
            }
            try? await clock.sleep(until: start + .seconds(duration), tolerance: .zero)
            guard !Task.isCancelled else { return }
            rest = landing
            run = nil
            if landing == M.atC { onBreath?() }
            // The box is empty again by the time the splash is back.
            if landing == 0 { agreed = false }
        }
    }

    /// The splash has rested: the logo fades and the tide comes.
    private func enter(at date: Date = .now) {
        guard run == nil, rest == 0 else { return }
        begin(to: M.atLow, duration: M.atLow, landing: M.atLow, at: date)
    }

    /// A finger goes down: the breath runs to its end and on to the welcome, unless it is let go before the top.
    private func press(_ by: Holder, at date: Date = .now) {
        let s = scene(at: date)
        guard s > 0, s <= M.atTop else { return }
        holder = by
        begin(to: M.atC, duration: M.atC - s, landing: M.atC, at: date)
    }

    /// Let go before the top: the tide sinks back, at the pace of an exhale, and waits.
    private func release() {
        holder = nil
        let s = scene(at: .now)
        guard run != nil, s > 0, s < M.atTop else { return }
        begin(to: M.atLow, duration: s < M.atLow ? M.atLow - s : (s - M.atLow) * M.exhale / M.inhale, landing: M.atLow, at: .now)
    }

    /// From the welcome, or from the waiting tide, back to the splash.
    private func goBack(at date: Date = .now) {
        guard run == nil else { return }
        if rest == M.atLow { begin(to: 0, duration: M.atLow, landing: 0, at: date) }
        if rest == M.atC { begin(to: M.end, duration: M.back, landing: 0, at: date) }
    }

    private func agree(_ by: Holder) {
        if by == .hand { tickHaptic.selectionChanged() }
        agreed.toggle()
        onAgree?(agreed)
    }

    /// A touch takes over from the autoPlay loop, which starts again from the splash a while later.
    private func touched() {
        if holder == .auto { release() }
        touches += 1
    }

    private func loop() async {
        guard autoPlay else {
            // By hand the splash gives way by itself, and the tide waits for a finger.
            try? await Task.sleep(for: .seconds(M.splash))
            if !Task.isCancelled { enter() }
            return
        }
        if touches > 0 {
            // Three seconds of rest, then back to the splash, which the loop starts from.
            var quiet = 0.0
            while quiet < M.userHold {
                try? await Task.sleep(for: .seconds(0.1))
                guard !Task.isCancelled else { return }
                quiet = run == nil ? quiet + 0.1 : 0
            }
            while !Task.isCancelled, run != nil || rest != 0 {
                goBack()
                try? await Task.sleep(for: .seconds(0.1))
            }
        }
        // Every step is due at a fixed time after the loop began, and the timers may not run late.
        let clock = ContinuousClock()
        var origin = clock.now
        var began = Date.now
        while !Task.isCancelled {
            try? await clock.sleep(until: origin + .seconds(M.enterAt), tolerance: .zero)
            guard !Task.isCancelled else { return }
            enter(at: began + M.enterAt)
            try? await clock.sleep(until: origin + .seconds(M.pressAt), tolerance: .zero)
            guard !Task.isCancelled else { return }
            press(.auto, at: began + M.pressAt)
            try? await clock.sleep(until: origin + .seconds(M.tickAt), tolerance: .zero)
            guard !Task.isCancelled else { return }
            if !agreed { agree(.auto) }
            try? await clock.sleep(until: origin + .seconds(M.backAt), tolerance: .zero)
            guard !Task.isCancelled else { return }
            goBack(at: began + M.backAt)
            origin += .seconds(M.loop)
            began += M.loop
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private struct DimStyle: ButtonStyle {
        let dimmed: Double

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(configuration.isPressed ? dimmed : 1)
                .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
        }
    }
}

private typealias M = BreathingTideWelcomeMotion

private enum Palette {
    static let paper = Color(hex: 0xFEFEFE)
    static let dot = Color(hex: 0xFD6703)
    static let ink = Color(hex: 0x232323)
    static let text = Color(hex: 0x313131)
    static let soft = Color(hex: 0x585858)
    static let blue = Color(hex: 0x136EFB)
    static let blush = Color(hex: 0xFDF6F6)
}

private enum Metrics {
    /// The splash's logo, as a fraction of the screen's height.
    static let logoAt = 0.47
    static let logoDot = 58.0
    static let logoGap = 14.0
    // Where things are, in points under the top safe area…
    static let wordY = 17.0
    static let pictureY = 131.0
    static let crestY = 289.0
    /// The white area's edge is this much lower at the screen's edges than at its crest.
    static let crestDrop = 32.0
    static let titleBelow = 72.0
    static let subtitleBelow = 114.0
    // …and above the bottom safe area.
    static let secondaryUp = 43.0
    static let primaryUp = 110.0
    static let consentUp = 182.0
    static let button = 50.0
    static let buttonMargin = 26.5
    static let consentMargin = 48.0
    static let box = 18.0
}

/// The white area under the yellow panel: it has an arched top edge, like the tide.
struct BreathingTideWelcomeSheet: Shape {
    let crest: Double
    let drop: Double

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: crest + drop))
        path.addQuadCurve(to: CGPoint(x: rect.maxX, y: crest + drop), control: CGPoint(x: rect.midX, y: crest - drop))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

/// A tick in an 18 × 18 box.
struct BreathingTideWelcomeTick: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 18
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 4.5 * s, y: rect.minY + 9.3 * s))
        path.addLine(to: CGPoint(x: rect.minX + 7.8 * s, y: rect.minY + 12.5 * s))
        path.addLine(to: CGPoint(x: rect.minX + 13.6 * s, y: rect.minY + 5.9 * s))
        return path
    }
}

#Preview {
    BreathingTideWelcome(autoPlay: true)
}
