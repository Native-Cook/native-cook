import SwiftUI

/// A wallet's intro: a fixed lower third (caption, small print, two buttons) under a stage where
/// one cast of gradient shapes keeps re-forming. A hero badge on a capsule with badges floating
/// round it becomes a grid, the grid a constellation, the constellation a striped wallet a diamond
/// drops into. Nothing cuts: each shape travels and turns into what it is in the next scene.
/// Tap the stage or swipe left for the next scene, swipe right for the one before.
struct MorphingBadgeTour: View {
    /// One caption a scene: the welcome, the grid, the constellation and the wallet.
    var captions = ["Welcome to\nPrism Wallet", "Your home for\neverything onchain", "Trade 2+ million coins", "Or just save\n(that's cool too)"]
    /// The small print, in turns plain and highlighted.
    var legal = ["By using Prism Wallet, you agree to the ", "terms", " and\n", "privacy policy"]
    var primaryLabel = "Create new wallet"
    var secondaryLabel = "I already have a wallet"
    /// Goes through the four scenes by itself, every `MorphingBadgeTourMotion.loop` seconds.
    var autoPlay = false
    /// Called with the scene's index when the artwork starts to turn into it.
    var onSceneChange: ((Int) -> Void)? = nil
    var onPrimary: (() -> Void)? = nil
    var onSecondary: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var calm
    /// The block opens on the first scene at rest.
    @State private var run = Run(from: S.all[0].flatMap { $0 }, to: 0, away: false, leaving: 0, start: .distantPast)
    /// The run's clock is going.
    @State private var active = false
    @State private var runTask: Task<Void, Never>? = nil
    /// When the block appeared, for the bobbing, the breathing and the sparkles.
    @State private var appeared = Date.now
    @State private var touching = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var sceneHaptic = UISelectionFeedbackGenerator()

    /// The pieces on their way from where they were to a scene.
    private struct Run {
        /// Where the pieces were when the run began: pieces × fields numbers.
        var from: [Double]
        /// The scene the run goes to, and whether everything leaves the stage first.
        var to: Int
        var away: Bool
        /// The scene whose caption leaves.
        var leaving: Int
        var start: Date

        /// Seconds into the run.
        func time(at date: Date) -> Double {
            min(M.runLength(away: away), max(0, date.timeIntervalSince(start)))
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let bottom = max(insets.bottom, 12)
            let center = CGPoint(x: size.width / 2, y: (size.height * Metrics.stageAt).rounded())
            let captionY = size.height - bottom - Metrics.captionUp
            let stageBottom = captionY - Metrics.stageGap

            TimelineView(.animation(paused: calm && !active)) { timeline in
                let r = run.time(at: timeline.date)
                let clock = timeline.date.timeIntervalSince(appeared)

                ZStack(alignment: .topLeading) {
                    Palette.night
                    Canvas { context, _ in
                        context.clip(to: Path(CGRect(x: 0, y: 0, width: size.width, height: stageBottom)))
                        draw(&context, r: r, clock: clock, center: center)
                    }
                    .accessibilityHidden(true)

                    stage
                        .frame(width: size.width, height: stageBottom)

                    ForEach(captions.prefix(S.count).indices, id: \.self) { index in
                        if index == run.to || index == run.leaving {
                            let shift = run.to == run.leaving ? (opacity: 1.0, y: 0.0) : M.captionMove(r, leaving: index == run.leaving, calm: calm)
                            firstLine(Text(captions[index]), font: .systemFont(ofSize: 22), pitch: 30, y: captionY)
                                .tracking(-0.4)
                                .foregroundStyle(.white)
                                .opacity(shift.opacity)
                                .offset(y: shift.y)
                                .accessibilityHidden(index != run.to)
                        }
                    }

                    firstLine(legalText, font: .systemFont(ofSize: 11), pitch: 16, y: size.height - bottom - Metrics.legalUp)

                    Button {
                        tapHaptic.impactOccurred()
                        onPrimary?()
                    } label: {
                        Text(primaryLabel)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Palette.night)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(Capsule().fill(.white))
                            .contentShape(Capsule())
                    }
                    .buttonStyle(DimStyle(dimmed: 0.8))
                    .frame(width: size.width - Metrics.buttonMargin * 2, height: Metrics.button)
                    .position(x: size.width / 2, y: size.height - bottom - Metrics.primaryUp)

                    Button {
                        tapHaptic.impactOccurred()
                        onSecondary?()
                    } label: {
                        Text(secondaryLabel)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(DimStyle(dimmed: 0.6))
                    .frame(width: size.width - Metrics.buttonMargin * 2, height: 44)
                    .position(x: size.width / 2, y: size.height - bottom - Metrics.secondaryUp)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    if !touching {
                        touching = true
                        touches += 1
                    }
                }
                .onEnded { _ in touching = false }
        )
        .onAppear { tapHaptic.prepare() }
        .onDisappear { runTask?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    /// The stage takes the taps and the swipes; the artwork is drawn under it.
    private var stage: some View {
        Color.clear
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onEnded { value in
                        let across = value.translation.width
                        if abs(across) > M.swipe {
                            step(by: across < 0 ? 1 : -1)
                        } else if abs(across) < 12, abs(value.translation.height) < 12 {
                            step(by: 1)
                        }
                    }
            )
            .accessibilityElement()
            .accessibilityLabel(captions[min(run.to, captions.count - 1)].replacingOccurrences(of: "\n", with: " "))
            .accessibilityHint("Shows the next scene")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { step(by: 1) }
    }

    private var legalText: Text {
        var whole = AttributedString()
        for (index, part) in legal.enumerated() {
            var piece = AttributedString(part)
            piece.foregroundColor = index % 2 == 1 ? Palette.link : Palette.mute
            whole += piece
        }
        return Text(whole)
    }

    /// Text whose first line is centred on `y`, with its lines `pitch` apart.
    private func firstLine(_ text: Text, font: UIFont, pitch: Double, y: Double) -> some View {
        text
            .font(Font(font))
            .lineSpacing(max(0, pitch - font.lineHeight))
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .padding(.top, y - font.lineHeight / 2)
            .allowsHitTesting(false)
    }

    private func draw(_ context: inout GraphicsContext, r: Double, clock: Double, center: CGPoint) {
        let now = run
        if calm {
            // Nothing travels: the scene the pieces come from fades into the one they go to.
            let u = M.ease(r / M.blend)
            MorphingBadgeTourStage.paint(&context, read: { now.from[$0 * S.fields + $1] }, veil: 1 - u, clock: nil, center: center)
            MorphingBadgeTourStage.paint(&context, read: { S.all[now.to][$0][$1] }, veil: u, clock: nil, center: center)
        } else {
            MorphingBadgeTourStage.paint(&context, read: { M.pieceValue(now.from, to: now.to, away: now.away, r, $0, $1) }, veil: 1, clock: clock, center: center)
        }
    }

    // MARK: Behaviour

    /// Starts the change to scene `to` from wherever the pieces are. The loop passes the moment
    /// the change was due, so a late wake-up does not shift it.
    private func go(to: Int, hand: Bool, at date: Date = .now) {
        let r = run.time(at: date)
        let now = (0..<S.pieces * S.fields).map { M.pieceValue(run.from, to: run.to, away: run.away, r, $0 / S.fields, $0 % S.fields) }
        // After the last scene everything leaves the stage, and the first one comes in from outside.
        let away = run.to == S.count - 1 && to == 0
        let changed = to != run.to
        run = Run(from: now, to: to, away: away, leaving: run.to, start: date)
        active = true
        if changed {
            if hand { sceneHaptic.selectionChanged() }
            onSceneChange?(to)
        }
        runTask?.cancel()
        let clock = ContinuousClock()
        let due = clock.now + .seconds(max(0, M.runLength(away: away) - Date.now.timeIntervalSince(date)))
        runTask = Task { @MainActor in
            try? await clock.sleep(until: due, tolerance: .zero)
            guard !Task.isCancelled else { return }
            active = false
        }
    }

    private func step(by: Int) {
        go(to: (run.to + by + S.count) % S.count, hand: true)
    }

    private func loop() async {
        guard autoPlay else { return }
        // Every step is due at a fixed time after the loop began, and the timers may not run late.
        let clock = ContinuousClock()
        var origin = clock.now
        var began = Date.now
        if touches > 0 {
            // Three seconds of rest, then back to the first scene if need be, and the loop starts
            // over once it has formed.
            var quiet = 0.0
            while quiet < M.userHold {
                try? await Task.sleep(for: .seconds(0.1))
                guard !Task.isCancelled else { return }
                quiet = active ? 0 : quiet + 0.1
            }
            origin = clock.now
            began = Date.now
            if run.to != 0 {
                let length = M.runLength(away: run.to == S.count - 1)
                go(to: 0, hand: false)
                origin += .seconds(length)
                began += length
            }
        }
        while !Task.isCancelled {
            for at in M.nextAt {
                try? await clock.sleep(until: origin + .seconds(at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                go(to: (run.to + 1) % S.count, hand: false, at: began + at)
            }
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

private typealias S = MorphingBadgeTourScenes
private typealias M = MorphingBadgeTourMotion

private enum Palette {
    static let night = Color(hex: 0x0E0D10)
    static let mute = Color(hex: 0x99999C)
    static let link = Color(hex: 0x3773F5)
}

private enum Metrics {
    /// The stage's centre, as a fraction of the screen's height.
    static let stageAt = 0.387
    // The lower third, as centres above the bottom safe area.
    static let secondaryUp = 9.0
    static let primaryUp = 83.0
    static let legalUp = 159.0
    static let captionUp = 251.0
    /// The artwork is cut off this far above the caption's first line.
    static let stageGap = 26.0
    static let button = 56.0
    static let buttonMargin = 26.5
}

#Preview {
    MorphingBadgeTour(autoPlay: true)
}
