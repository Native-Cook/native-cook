import SwiftUI

/// A streak screen: a gold coin spins, pops into a flame mascot, and the day count rolls up by one.
/// The flame lifts, a week bar fills and the buttons slide up; after a pause everything slides away
/// and the coin comes back. One clock drives all of it, so a touch and autoPlay run the same motion.
struct StreakFlameTakeover: View {
    /// The streak before today; the count rolls up to the next number.
    var days = 1824
    /// How many days of the week the bar fills, 0 to 7.
    var weekDays = 2
    var streakLabel = "day streak"
    /// The heading inside the week card.
    var weekLabel = "This week"
    var shareLabel = "SHARE MILESTONE"
    var continueLabel = "CONTINUE"
    /// Plays by itself every `StreakFlameTakeoverMotion.loop` seconds and ends on the coin again.
    var autoPlay = false
    var onShare: (() -> Void)? = nil
    /// Called when Continue is pressed by a touch.
    var onContinue: (() -> Void)? = nil

    private typealias M = StreakFlameTakeoverMotion

    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var clock = StreakFlameTakeoverClock()
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private static let page = Color(hex: 0x0b0d10)
    private static let card = Color(hex: 0x14171c)
    private static let cardLine = Color(hex: 0x2a2e36)
    private static let day = Color(hex: 0x5c6270)
    private static let orange = Color(hex: 0xff7a18)
    private static let blue = Color(hex: 0x1cb0f6)
    private static let gold = (r: 255.0, g: 216.0, b: 74.0, a: 1.0)
    private static let ghost = (r: 255.0, g: 255.0, b: 255.0, a: 0.1)

    private static let coinWidth = 84.0
    private static let coinHeight = 58.0
    private static let countSize = 88.0
    private static let countLine = 98.0
    private static let margin = 20.0
    private static let cardHeight = 112.0
    private static let track = 12.0
    private static let button = 56.0
    private static let glow = 460.0
    private static let dayLabels = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"]

    /// Fourteen sparks leave the flame in a ring, 80 to 130 points out.
    private static let sparks: [(dx: Double, dy: Double, size: Double, color: Color)] = (0..<14).map { i in
        let angle = Double(i) / 14 * tau + 0.3
        let reach = 80 + Double((i * 37) % 50)
        return (cos(angle) * reach, sin(angle) * reach, 5 + Double(i % 3) * 3, Color(hex: i % 3 == 1 ? 0xff7a18 : 0xffd23a))
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: !clock.isPlaying)) { timeline in
                screen(t: clock.time(at: timeline.date), insets: insets, size: size)
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .onTapGesture { touch(from: M.tapAt, force: false) }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: touches) { await resume() }
        .accessibilityLabel("\(days + 1) \(streakLabel)")
    }

    // MARK: Clock

    /// Runs the loop from `from` to its end. A run in progress is left alone unless `force`.
    private func touch(from: Double, force: Bool) {
        guard force || !clock.busy(at: .now) else { return }
        clock.auto = nil
        clock.touch = .init(start: .now, from: from)
        touches += 1
    }

    private func dismiss() {
        haptic.impactOccurred()
        touch(from: M.outAt, force: true)
        onContinue?()
    }

    /// Frees the touch once its run is over and, with autoPlay, starts the loop again from rest.
    private func resume() async {
        if let touch = clock.touch {
            try? await Task.sleep(for: .seconds(touch.remaining), tolerance: .zero)
            guard !Task.isCancelled else { return }
            clock.touch = nil
            try? await Task.sleep(for: .seconds(M.resumeAfter), tolerance: .zero)
            guard !Task.isCancelled else { return }
        }
        if autoPlay { clock.auto = .now }
    }

    // MARK: Screen

    /// How far the flame, count and label have lifted, and how far they have slid away at the end.
    private func lift(_ t: Double) -> Double {
        if t >= M.backAt || reduced { return 0 }
        let up = M.easeInOut(M.seg(t, M.liftAt, M.liftAt + M.liftIn)) * -M.lift
        return up + M.easeInOut(M.seg(t, M.outAt, M.outAt + M.outIn)) * (M.outSlide + M.lift)
    }

    /// 1 while the screen is up, fading to 0 at the end; 0 again until the resting count returns.
    private func present(_ t: Double) -> Double {
        t >= M.backAt ? 0 : 1 - M.seg(t, M.outAt, M.outAt + M.outIn)
    }

    /// How far a button has slid in, and then away.
    private func buttonSlide(_ t: Double, _ index: Int) -> (opacity: Double, offset: Double) {
        let at = M.buttonsAt + Double(index) * M.buttonStagger
        let p = M.easeOut(M.seg(t, at, at + M.buttonIn))
        let away = t >= M.backAt ? 0 : M.easeInOut(M.seg(t, M.outAt, M.outAt + M.outIn)) * M.outSlide
        return (p * present(t), reduced ? 0 : (1 - p) * M.buttonSlide + away)
    }

    private static func rounded(_ size: Double, _ weight: Font.Weight) -> Font {
        .system(size: size, weight: weight, design: .rounded)
    }

    private func screen(t: Double, insets: EdgeInsets, size: CGSize) -> some View {
        let flameY = max(insets.top + 150, size.height * 0.33)
        let countTop = flameY + StreakFlameTakeoverFlame.size / 2 + 6
        let labelTop = countTop + Self.countLine - 6
        let cardTop = labelTop + 52 - M.lift
        let cardWidth = size.width - Self.margin * 2
        let bottom = insets.bottom + 8
        let share = buttonSlide(t, 0)
        let next = buttonSlide(t, 1)
        let sparksP = M.seg(t, M.popAt + 0.1, M.popAt + 0.1 + M.sparksFor)
        let haloP = M.seg(t, M.popAt, M.popAt + 0.5)

        return ZStack(alignment: .topLeading) {
            Self.page
            coin(t: t).offset(x: size.width / 2 - Self.coinWidth / 2, y: flameY - Self.coinHeight / 2)

            // A warm glow comes up behind the flame as it pops, and follows it up.
            Circle()
                .fill(RadialGradient(
                    stops: [
                        .init(color: .rgba(255, 170, 0, 0.34), location: 0),
                        .init(color: .rgba(255, 120, 0, 0.1), location: 0.45),
                        .init(color: .rgba(255, 120, 0, 0), location: 1),
                    ],
                    center: .center, startRadius: 0, endRadius: Self.glow / 2
                ))
                .frame(width: Self.glow, height: Self.glow)
                .scaleEffect(reduced ? 1 : 0.6 + 0.4 * M.easeOut(haloP))
                .opacity(haloP * present(t))
                .offset(x: size.width / 2 - Self.glow / 2, y: flameY - Self.glow / 2 + lift(t))

            wings(t: t).offset(x: size.width / 2 - StreakFlameTakeoverWings.width / 2, y: flameY - StreakFlameTakeoverWings.height / 2 - 20)
            flame(t: t).offset(x: size.width / 2 - StreakFlameTakeoverFlame.size / 2, y: flameY - StreakFlameTakeoverFlame.size / 2)
            ForEach(Array(Self.sparks.enumerated()), id: \.offset) { _, spark in
                Circle().fill(spark.color).frame(width: spark.size, height: spark.size)
                    .scaleEffect(1 - 0.6 * sparksP)
                    .opacity(sparksP == 0 || sparksP == 1 ? 0 : 1 - sparksP * sparksP)
                    .offset(
                        x: size.width / 2 - spark.size / 2 + spark.dx * M.easeOut(sparksP),
                        y: flameY - spark.size / 2 + spark.dy * M.easeOut(sparksP)
                    )
            }

            count(t: t)
                .frame(width: size.width)
                .offset(y: countTop + lift(t))
            Text(streakLabel).font(Self.rounded(22, .heavy)).foregroundStyle(.white)
                .frame(width: size.width, height: 28)
                .opacity(M.seg(t, M.labelAt, M.labelAt + M.labelIn) * present(t))
                .offset(y: labelTop + lift(t))

            card(t: t, width: cardWidth)
                .offset(x: Self.margin, y: cardTop)

            Button { onShare?() } label: {
                HStack(spacing: 10) {
                    Image(systemName: "square.and.arrow.up").font(.system(size: 17, weight: .medium))
                    Text(shareLabel).font(Self.rounded(16, .black)).kerning(0.8).frame(height: 20)
                }
                .foregroundStyle(.white)
                .padding(.bottom, 4)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background {
                    ZStack(alignment: .top) {
                        RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color(hex: 0x1593d0))
                            .shadow(color: .rgba(28, 176, 246, 0.25), radius: 12, y: 10)
                        RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Self.blue).padding(.bottom, 4)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .frame(width: cardWidth, height: Self.button)
            .opacity(share.opacity)
            .offset(x: Self.margin, y: size.height - bottom - 52 - Self.button + share.offset)

            Button(action: dismiss) {
                Text(continueLabel).font(Self.rounded(16, .black)).kerning(0.8).foregroundStyle(Self.blue)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .frame(width: cardWidth, height: 44)
            .opacity(next.opacity)
            .offset(x: Self.margin, y: size.height - bottom - 44 + next.offset)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func coin(t: Double) -> some View {
        let gone = M.seg(t, M.popAt, M.popAt + M.coinOutIn)
        let back = M.seg(t, M.backAt, M.backAt + M.backIn)
        let spin = reduced ? 1 : max(0.08, abs(cos(t / M.coinPeriod * tau)))
        return ZStack(alignment: .top) {
            Capsule().fill(Color.rgba(255, 200, 0, 0.1))
                .frame(width: Self.coinWidth + 20, height: 18)
                .offset(y: Self.coinHeight - 6)
            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0xffe066), location: 0),
                    .init(color: Color(hex: 0xffbf00), location: 0.55),
                    .init(color: Color(hex: 0xe08a00), location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
            .overlay(
                Capsule().fill(Color.rgba(255, 244, 190, 0.55)).frame(width: 46, height: 32)
                    .overlay(Image(systemName: "flame.fill").font(.system(size: 19)).foregroundStyle(Color.rgba(214, 120, 0, 0.9)))
            )
            .clipShape(Capsule())
            .overlay(Capsule().strokeBorder(Color(hex: 0xf2a100), lineWidth: 3))
            .frame(width: Self.coinWidth, height: Self.coinHeight)
            .scaleEffect(x: spin * (1 - 0.4 * gone), y: 1 - 0.4 * gone)
            .opacity(t < M.popAt ? 1 : t >= M.backAt ? back : 1 - gone)
        }
        .frame(width: Self.coinWidth, height: Self.coinHeight, alignment: .top)
        .allowsHitTesting(false)
    }

    private func wings(t: Double) -> some View {
        let p = M.seg(t, M.popAt + 0.05, M.popAt + 0.05 + M.wingsFor)
        let flap = sin(p * .pi * 3)
        return StreakFlameTakeoverWings()
            .scaleEffect(x: 0.7 + 0.3 * M.easeOut(p), y: 0.8 + 0.2 * flap)
            .opacity(reduced || p == 0 || p == 1 ? 0 : 1 - p * 0.3)
            .allowsHitTesting(false)
    }

    private func flame(t: Double) -> some View {
        let shown = present(t)
        let glint = M.seg(t, M.glintAt, M.glintAt + M.glintFor)
        // The sparkle by the shades flashes with each glint pass.
        let sparkle = max(sin(.pi * M.seg(t, M.popAt + 0.3, M.popAt + 0.9)), sin(.pi * M.seg(t, M.glintAt + 0.2, M.glintAt + 0.8)))
        let pop = M.popFrom + (1 - M.popFrom) * M.spring(t - M.popAt, stiffness: M.popStiffness, damping: M.popDamping)
        let breathe = M.breathe * (1 - cos(t / M.breathePeriod * tau)) / 2 * M.seg(t, M.liftAt, M.liftAt + M.liftIn)
        let opacity = t < M.popAt || shown == 0 ? 0 : reduced ? min(M.seg(t, M.popAt, M.popAt + M.reducedFade), shown) : shown
        return StreakFlameTakeoverFlame(glint: glint, sparkle: sparkle)
            .scaleEffect(reduced ? 1 : pop * (1 + breathe))
            .offset(y: reduced ? 0 : lift(t))
            .opacity(opacity)
            .allowsHitTesting(false)
    }

    private func count(t: Double) -> some View {
        let bright = t >= M.backAt ? 0 : M.seg(t, M.brightenAt, M.brightenAt + M.brightenFor)
        // A straight mix, alpha included, like React Native's `interpolateColor`.
        let color = Color.rgba(
            Self.ghost.r + (Self.gold.r - Self.ghost.r) * bright,
            Self.ghost.g + (Self.gold.g - Self.ghost.g) * bright,
            Self.ghost.b + (Self.gold.b - Self.ghost.b) * bright,
            Self.ghost.a + (Self.gold.a - Self.ghost.a) * bright
        )
        let roll = t >= M.backAt ? 0 : reduced ? M.seg(t, M.rollAt, M.rollAt + M.reducedFade) : M.easeInOut(M.seg(t, M.rollAt, M.rollAt + M.rollFor))
        let opacity = t >= M.backAt ? M.seg(t, M.backAt, M.backAt + M.backIn) : present(t)
        let head = String(String(days + 1).dropLast())
        let from = String(String(days).suffix(1))
        let to = String((days + 1) % 10)
        func digits(_ text: String) -> some View {
            Text(text)
                .font(Self.rounded(Self.countSize, .black)).kerning(-2).monospacedDigit()
                .foregroundStyle(color)
                .shadow(color: .rgba(255, 150, 0, 0.45), radius: 9)
                .frame(height: Self.countLine)
        }
        return HStack(spacing: 0) {
            digits(head)
            VStack(spacing: 0) {
                digits(from)
                digits(to)
            }
            .offset(y: -Self.countLine * roll)
            .frame(height: Self.countLine, alignment: .top)
            .clipped()
        }
        .frame(height: Self.countLine)
        .opacity(opacity)
        .allowsHitTesting(false)
    }

    private func card(t: Double, width: Double) -> some View {
        let p = M.easeOut(M.seg(t, M.cardAt, M.cardAt + M.cardIn))
        let out = M.easeInOut(M.seg(t, M.outAt, M.outAt + M.outIn))
        let trackWidth = width - 32
        let full = trackWidth * Double(weekDays) / 7
        let fill = M.easeInOut(M.seg(t, M.fillAt, M.fillAt + M.fillFor))
        let glowP = M.seg(t, M.fillAt, M.fillAt + M.fillFor + 0.4)
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(weekLabel).font(Self.rounded(16, .heavy)).foregroundStyle(.white).frame(height: 20)
                Spacer(minLength: 0)
                HStack(spacing: 4) {
                    Image(systemName: "flame.fill").font(.system(size: 12)).foregroundStyle(Self.orange)
                    Text("\(weekDays) of 7").font(Self.rounded(14, .bold)).foregroundStyle(Color(hex: 0x9aa1ae)).frame(height: 18)
                }
            }
            HStack(spacing: 0) {
                ForEach(Array(Self.dayLabels.enumerated()), id: \.offset) { i, label in
                    if i > 0 { Spacer(minLength: 0) }
                    Text(label).font(Self.rounded(13, .heavy)).foregroundStyle(i == weekDays - 1 ? Self.orange : Self.day)
                        .frame(width: 30, height: 16)
                }
            }
            Capsule().fill(Color(hex: 0x1f232a))
                .frame(width: trackWidth, height: Self.track)
                .overlay(alignment: .leading) {
                    LinearGradient(colors: [Self.orange, Color(hex: 0xffc800)], startPoint: .leading, endPoint: .trailing)
                        .frame(width: full * fill)
                        .clipShape(Capsule())
                }
                .overlay(alignment: .topLeading) {
                    Circle().fill(Color(hex: 0xffc23a))
                        .background(Circle().fill(Color.rgba(255, 170, 0, 0.8)).padding(-4).blur(radius: 8))
                        .frame(width: 22, height: 22)
                        .opacity(0.9 * sin(.pi * glowP))
                        .offset(x: -9 + full * M.easeInOut(min(1, glowP * 1.7)), y: -5)
                }
        }
        .padding(.horizontal, 16)
        .frame(width: width, height: Self.cardHeight)
        .background(shape.fill(Self.card).shadow(color: .black.opacity(0.45), radius: 20, y: 18))
        // The inner top highlight, then the hairline edge.
        .overlay(shape.inset(by: 1).stroke(Color.white.opacity(0.05), lineWidth: 1).mask(alignment: .top) { Rectangle().frame(height: 3) })
        .overlay(shape.strokeBorder(Self.cardLine, lineWidth: 1))
        .opacity(p * present(t))
        .offset(y: reduced ? 0 : (1 - p) * M.cardSlide + (t >= M.backAt ? 0 : out * M.outSlide))
        .allowsHitTesting(false)
    }
}

#Preview {
    StreakFlameTakeover(autoPlay: true)
}
