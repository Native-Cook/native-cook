import SwiftUI

/// Black "BOOK A CALL" pill. While pressed (or by itself with `autoPlay`) the label slides away,
/// two avatars swing in as "host + you", then slide together and a short message wipes in beside
/// them. A tap plays the whole greeting once.
struct AvatarCallButton: View {
    var label = "BOOK A CALL"
    /// Shown next to the two avatars once they have met.
    var message = "Let's talk!"
    /// Initials on the host's avatar; the other one always reads "YOU".
    var initials = "JD"
    /// Plays the whole greeting by itself with a pause between runs.
    var autoPlay = false
    var action: () -> Void = {}

    @State private var taps = 0

    var body: some View {
        Button {
            taps += 1
            action()
        } label: {
            Text(label)
        }
        .buttonStyle(Style(label: label, message: message, initials: initials, autoPlay: autoPlay, taps: taps))
        .accessibilityLabel(label)
    }

    private struct Style: ButtonStyle {
        let label: String
        let message: String
        let initials: String
        let autoPlay: Bool
        let taps: Int

        func makeBody(configuration: Configuration) -> some View {
            Surface(
                label: label, message: message, initials: initials,
                autoPlay: autoPlay, taps: taps, isPressed: configuration.isPressed
            )
        }
    }
}

private enum Metrics {
    // The pill has a fixed size so the avatars have known places to travel between.
    static let width = 164.0
    static let height = 52.0
    static let avatar = 38.0
    /// The canvas overflows the button by this much on every side to fit the shadow.
    static let bleed = 26.0

    // Avatar centres, measured from the pill's left edge: where they fade in, where they pause as
    // "host + you", and where they end up overlapping.
    static let hostX = [26.0, 48.0, 27.0]
    static let guestX = [138.0, 116.0, 53.0]
    static let messageX = 80.0
    static let messageWidth = 80.0

    static let openSeconds = 0.38
    static let pairHold = 0.42
    static let joinSeconds = 0.52
    static let closeSeconds = 0.32
    // How long a tap keeps the greeting up, and the rhythm of the autoplay loop.
    static let tapHold = 1.3
    static let autoPlayPause = 1.5
    static let autoPlayHold = 1.7
}

private let easeInOut = UnitCurve.bezier(
    startControlPoint: UnitPoint(x: 0.65, y: 0),
    endControlPoint: UnitPoint(x: 0.35, y: 1)
)

private struct Surface: View {
    let label: String
    let message: String
    let initials: String
    let autoPlay: Bool
    let taps: Int
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// 0 → 1: the label makes way for the two avatars.
    @State private var open = Tween(0)
    /// 0 → 1: the avatars slide together and the message appears.
    @State private var join = Tween(0)
    @State private var held = false
    @State private var auto = false
    @State private var tapped = false
    @State private var tapToken = 0
    @State private var pressedAt = Date.distantPast
    @State private var greeting = false
    @State private var run = 0
    @State private var moving = false
    @State private var settleToken = 0

    var body: some View {
        TimelineView(.animation(paused: !moving)) { timeline in
            let o = open.value(at: timeline.date)
            let j = join.value(at: timeline.date)
            content(open: o, join: j)
        }
        .frame(width: Metrics.width, height: Metrics.height)
        .clipShape(Capsule())
        .background {
            Canvas { context, size in
                drawPill(context, size: size)
            }
            .padding(-Metrics.bleed)
            .allowsHitTesting(false)
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: isPressed) { _, pressed in
            held = pressed
            if pressed {
                pressedAt = .now
                sync()
            } else {
                // The button's action follows straight after and may want the greeting kept, so
                // decide after it.
                Task { sync() }
            }
        }
        .onChange(of: taps) {
            // A tap is too short to watch the greeting, so it keeps it up for a moment. A long
            // hold has already shown it.
            guard Date.now.timeIntervalSince(pressedAt) < Metrics.openSeconds + Metrics.pairHold else { return }
            tapped = true
            tapToken += 1
            sync()
        }
        .task(id: tapToken) {
            guard tapToken > 0 else { return }
            let total = Metrics.openSeconds + Metrics.pairHold + Metrics.joinSeconds + Metrics.tapHold
            try? await Task.sleep(for: .seconds(total))
            guard !Task.isCancelled else { return }
            tapped = false
            sync()
        }
        .task(id: autoPlay) {
            guard autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.autoPlayPause))
                if Task.isCancelled { break }
                auto = true
                sync()
                let shown = Metrics.openSeconds + Metrics.pairHold + Metrics.joinSeconds + Metrics.autoPlayHold
                try? await Task.sleep(for: .seconds(shown))
                auto = false
                sync()
                try? await Task.sleep(for: .seconds(Metrics.closeSeconds))
            }
            auto = false
            sync()
        }
        .task(id: settleToken) {
            guard settleToken > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.closeSeconds + 0.05))
            guard !Task.isCancelled, !greeting else { return }
            moving = false
        }
    }

    /// Starts or ends the greeting so it matches what press, tap and autoplay currently ask for.
    private func sync() {
        let wanted = held || auto || tapped
        guard wanted != greeting else { return }
        greeting = wanted
        run += 1
        let id = run
        moving = true
        guard wanted else {
            join.retarget(0, duration: Metrics.closeSeconds * 0.7, curve: Ease.out)
            open.retarget(0, duration: Metrics.closeSeconds, curve: Ease.out)
            settleToken += 1
            return
        }
        open.retarget(1, duration: Metrics.openSeconds, curve: Ease.out)
        Task {
            try? await Task.sleep(for: .seconds(Metrics.openSeconds + Metrics.pairHold))
            guard run == id else { return }
            join.retarget(1, duration: Metrics.joinSeconds, curve: easeInOut)
        }
    }

    private func content(open o: Double, join j: Double) -> some View {
        // Reduced motion keeps the fades but nothing travels: the avatars appear already joined.
        let hostX = reduceMotion
            ? Metrics.hostX[2]
            : Metrics.hostX[0] + (Metrics.hostX[1] - Metrics.hostX[0]) * o + (Metrics.hostX[2] - Metrics.hostX[1]) * j
        let guestX = reduceMotion
            ? Metrics.guestX[2]
            : Metrics.guestX[0] + (Metrics.guestX[1] - Metrics.guestX[0]) * o + (Metrics.guestX[2] - Metrics.guestX[1]) * j
        let swing = reduceMotion ? 0 : 40 * (1 - o)
        let grow = reduceMotion ? 1 : 0.8 + 0.2 * o
        let avatarOpacity = reduceMotion ? o : min(1, o * 1.5)
        // The message is wiped in from the left.
        let wipe = reduceMotion ? 1 : max(0, (j - 0.35) / 0.65)

        return ZStack(alignment: .leading) {
            Text(label)
                .font(.system(size: 14, weight: .semibold))
                .kerning(0.2)
                .lineLimit(1)
                .frame(width: Metrics.width)
                .opacity(1 - min(1, o * 1.8))
                .offset(y: reduceMotion ? 0 : -22 * o)
                // The texts in here are decoration; the button itself carries the label.
                .accessibilityHidden(true)

            Text("+")
                .font(.system(size: 15, weight: .medium))
                .frame(width: Metrics.width)
                .opacity(reduceMotion ? 0 : o * (1 - min(1, j * 2.5)))
                .accessibilityHidden(true)

            avatar(Color(hex: 0xff7a59)) {
                Text(initials).font(.system(size: 13, weight: .bold)).accessibilityHidden(true)
            }
            .scaleEffect(grow)
            .rotationEffect(.degrees(-swing))
            .opacity(avatarOpacity)
            .offset(x: hostX - Metrics.avatar / 2)

            avatar(.white) {
                Text("YOU").font(.system(size: 9.5, weight: .heavy)).foregroundStyle(.black).accessibilityHidden(true)
            }
            .scaleEffect(grow)
            .rotationEffect(.degrees(swing))
            .opacity(avatarOpacity)
            .offset(x: guestX - Metrics.avatar / 2)

            Text(message)
                .font(.system(size: 14, weight: .medium))
                .lineLimit(1)
                .frame(width: Metrics.messageWidth, alignment: .leading)
                .mask(alignment: .leading) {
                    Rectangle().frame(width: Metrics.messageWidth * wipe)
                }
                .opacity(reduceMotion ? o : min(1, wipe * 2))
                .offset(x: Metrics.messageX)
                .accessibilityHidden(true)
        }
        .foregroundStyle(.white)
        .frame(width: Metrics.width, height: Metrics.height, alignment: .leading)
    }

    private func avatar(_ color: Color, @ViewBuilder content: () -> some View) -> some View {
        Circle()
            .fill(color)
            .frame(width: Metrics.avatar, height: Metrics.avatar)
            .overlay { content() }
    }

    private func drawPill(_ context: GraphicsContext, size: CGSize) {
        let bleed = Metrics.bleed
        let rect = CGRect(x: bleed, y: bleed, width: size.width - bleed * 2, height: size.height - bleed * 2)
        let pill = capsule(rect)

        var shadow = context
        shadow.addFilter(.blur(radius: 9))
        shadow.fill(
            capsule(CGRect(x: rect.minX + 2, y: rect.minY + 8, width: rect.width - 4, height: rect.height - 2)),
            with: .color(.black.opacity(0.34))
        )

        context.fill(pill, with: .color(Color(hex: 0x030303)))

        // A soft light hugging the inside of the edge, as if the pill were slightly domed.
        var rim = context
        rim.clip(to: pill)
        rim.addFilter(.blur(radius: 3.5))
        rim.stroke(pill, with: .color(.white.opacity(0.46)), lineWidth: 5)
    }
}

#Preview {
    ZStack {
        Color(hex: 0xeeeeee).ignoresSafeArea()
        AvatarCallButton(autoPlay: true)
    }
}
