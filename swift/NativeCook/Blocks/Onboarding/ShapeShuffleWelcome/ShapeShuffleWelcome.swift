import SwiftUI

/// A welcome screen whose only picture is a small still life of blocks that keeps rebuilding
/// itself: the same eleven pieces tumble, stretch, round off and change colour into four different
/// buildings, each landing with a springy overshoot.
struct ShapeShuffleWelcome: View {
    var title = "Welcome home"
    var subtitle = "Control your content and devices from one place."
    var buttonLabel = "Get Started"
    /// The button's colour.
    var tint = Color(hex: 0x4f46e5)
    /// Rebuilds the still life by itself, one composition every `ShapeShuffleWelcomeScene.step` seconds, back at the first after `loop`.
    var autoPlay = false
    var onStart: (() -> Void)? = nil
    var onMore: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var still
    /// The screen's clock counts seconds from here.
    @State private var epoch: Date? = nil
    /// The first composition, as if it had just been arrived at: every loop begins like this one.
    @State private var stage = Stage(from: S.count - 1, to: 0, t0: -S.change)
    @State private var drag = Drag(index: -1, offset: .zero, released: 0)
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// Changes whenever something starts to move; the clock rests a while after the last one.
    @State private var wakes = 0
    @State private var idle = false
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var changeHaptic = UISelectionFeedbackGenerator()

    private typealias S = ShapeShuffleWelcomeScene

    /// What shows: the change from one composition to another that began at `t0`, on the screen's clock.
    private struct Stage {
        var from: Int
        var to: Int
        var t0: Double
    }

    /// A piece taken out of its place by a finger: by how much, and when it was let go (nil while held).
    private struct Drag {
        var index: Int
        var offset: CGSize
        var released: Double?
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private enum Palette {
        static let background = Color.white
        static let ink = Color(hex: 0x202124)
        static let glyph = Color(hex: 0x3c4043)
    }

    private enum Metrics {
        // Where the title's middle and the ground the pieces stand on are, as shares of the screen's height.
        static let titleY = 0.196
        static let groundY = 0.587
        static let subtitleGap = 20.0
        static let button = (width: 108.0, height: 32.0, radius: 4.0, right: 22.0, bottom: 9.0)
        static let more = (right: 19.0, top: 31.0, dot: 3.0, gap: 3.0)
        // The still life is drawn in a box this large round the scene's middle on the ground, with room for a throw.
        static let stage = (width: 340.0, above: 300.0, below: 60.0)
        // A touch within this of the scene counts as one on the still life.
        static let reach = (side: 130.0, above: 180.0, below: 24.0)
        static let press = 0.96
        /// Reduced motion: one composition fades out, the next in.
        static let fade = 0.3
        /// A finger that has moved less than this has tapped.
        static let slop = 8.0
        /// The clock rests this long after the last thing started to move.
        static let settle = 2.0
    }

    private var now: Double { epoch.map { Date.now.timeIntervalSince($0) } ?? 0 }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let ground = size.height * Metrics.groundY

            ZStack {
                Palette.background

                Text(title)
                    .font(.system(size: 19))
                    .lineLimit(1)
                    .position(x: size.width / 2, y: size.height * Metrics.titleY)
                Text(subtitle)
                    .font(.system(size: 13))
                    .lineSpacing(2.5)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .frame(width: size.width - 64, height: 36, alignment: .top)
                    .position(x: size.width / 2, y: size.height * Metrics.titleY + Metrics.subtitleGap + 18)

                TimelineView(.animation(paused: idle)) { timeline in
                    stillLife(at: epoch.map { timeline.date.timeIntervalSince($0) } ?? 0)
                }
                .frame(width: Metrics.stage.width, height: Metrics.stage.above + Metrics.stage.below)
                .position(x: size.width / 2, y: ground + (Metrics.stage.below - Metrics.stage.above) / 2)
                .accessibilityHidden(true)

                Button {
                    touched()
                    onMore?()
                } label: {
                    HStack(spacing: Metrics.more.gap) {
                        ForEach(0..<3, id: \.self) { _ in
                            Circle().fill(Palette.glyph).frame(width: Metrics.more.dot, height: Metrics.more.dot)
                        }
                    }
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("More")
                .position(x: size.width - Metrics.more.right - 7.5, y: insets.top + Metrics.more.top + 1.5)

                Button {
                    touched()
                    tapHaptic.impactOccurred()
                    onStart?()
                } label: {
                    Text(buttonLabel)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .frame(width: Metrics.button.width, height: Metrics.button.height)
                        .background(RoundedRectangle(cornerRadius: Metrics.button.radius).fill(tint))
                }
                .buttonStyle(PressStyle())
                .position(
                    x: size.width - Metrics.button.right - Metrics.button.width / 2,
                    y: size.height - max(insets.bottom, 16) - Metrics.button.bottom - Metrics.button.height / 2
                )
            }
            .foregroundStyle(Palette.ink)
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .simultaneousGesture(play(size: size, ground: ground))
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear { tapHaptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
        .task(id: wakes) {
            try? await Task.sleep(for: .seconds(Metrics.settle))
            if !Task.isCancelled { idle = true }
        }
    }

    // MARK: The still life

    private func stillLife(at now: Double) -> some View {
        let e = now - stage.t0
        // Reduced motion: no travel. The pieces change places while the still life is faded out.
        let shown = e < Metrics.fade ? stage.from : stage.to
        return Canvas { context, _ in
            for (index, piece) in S.pieces.enumerated() {
                let p = still ? S.piece(index, from: shown, to: shown, 0) : S.piece(index, from: stage.from, to: stage.to, e)
                let left = drag.index == index ? S.dragLeft(drag.released.map { now - $0 } ?? -1) : 0
                var layer = context
                layer.translateBy(x: Metrics.stage.width / 2 + p.x + drag.offset.width * left, y: Metrics.stage.above - p.y + drag.offset.height * left)
                layer.rotate(by: .degrees(p.rotation))
                layer.fill(S.outline(steps: piece.steps, p), with: .color(p.color))
            }
        }
        .opacity(still && e >= 0 && e < Metrics.fade * 2 ? abs(e - Metrics.fade) / Metrics.fade : 1)
    }

    // MARK: Touch

    /// A tap on the still life rebuilds it at once; a piece can be pulled out of its place, and springs back.
    private func play(size: CGSize, ground: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard abs(value.translation.width) > Metrics.slop || abs(value.translation.height) > Metrics.slop || drag.released == nil else { return }
                if drag.released != nil {
                    touched()
                    let x = value.startLocation.x - size.width / 2
                    let y = ground - value.startLocation.y
                    // The piece in front, among those the finger is on.
                    var hit = -1
                    for (index, piece) in S.pieces.enumerated() {
                        let spot = piece.spots[stage.to]
                        let reach = max(spot.w, spot.h) / 2 + 6
                        if spot.w > 0, abs(x - spot.x) < reach, abs(y - spot.y) < reach { hit = index }
                    }
                    drag = Drag(index: hit, offset: value.translation, released: nil)
                }
                drag.offset = value.translation
            }
            .onEnded { value in
                if drag.released == nil {
                    drag.released = now
                    wake()
                    return
                }
                let x = value.startLocation.x
                let y = value.startLocation.y
                guard abs(x - size.width / 2) < Metrics.reach.side, y > ground - Metrics.reach.above, y < ground + Metrics.reach.below else { return }
                touched()
                guard now >= stage.t0 + S.change else { return }
                stage = Stage(from: stage.to, to: (stage.to + 1) % S.count, t0: now)
                changeHaptic.selectionChanged()
            }
    }

    /// A touch takes over from the autoPlay loop, which starts again from the first composition a while later.
    private func touched() {
        touches += 1
        wake()
    }

    private func wake() {
        idle = false
        wakes += 1
    }

    private func loop() async {
        if epoch == nil { epoch = .now }
        guard autoPlay else { return }
        func sleep(until time: Double) async { try? await Task.sleep(for: .seconds(max(0, time - now))) }

        if touches > 0 {
            await sleep(until: max(now + S.userHold, stage.t0 + S.change))
            guard !Task.isCancelled else { return }
            // The touches are over: back to the first composition, and on with the loop from there.
            stage = stage.to == 0 ? Stage(from: 0, to: 0, t0: now - S.change) : Stage(from: stage.to, to: 0, t0: now)
            wake()
        }
        var due = stage.t0 + S.step
        while !Task.isCancelled {
            await sleep(until: due)
            guard !Task.isCancelled else { return }
            // Timed from when it was due, not from now: every loop is the same length.
            stage = Stage(from: stage.to, to: (stage.to + 1) % S.count, t0: due)
            wake()
            due += S.step
        }
    }

    private struct PressStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? Metrics.press : 1)
                .animation(.easeOut(duration: configuration.isPressed ? 0.1 : 0.16), value: configuration.isPressed)
        }
    }
}

#Preview {
    ShapeShuffleWelcome(autoPlay: true)
}
