import SwiftUI

/// A level-up screen: a gold badge bursts out of a dot with confetti, the heading and two counted
/// figures follow, and Continue slides up. After a pause the whole screen fades back to the dot.
/// One clock drives all of it, so a touch and the autoPlay loop run the same motion.
struct LevelUpTakeover: View {
    var level = 3
    /// The figures counted up under the heading.
    var points = 596
    var xp = 1750
    var title = "LEVEL UP!"
    /// The line under the heading.
    var subtitle = "You reached level 3. Keep the streak going!"
    var pointsLabel = "Coins earned"
    var xpLabel = "XP earned"
    var continueLabel = "Continue"
    var shareLabel = "Share the News"
    /// The coin chip next to the share link.
    var shareReward = "+10"
    /// Plays by itself every `LevelUpTakeoverMotion.loop` seconds and ends where it began.
    var autoPlay = false
    /// Called when Continue is pressed by a touch.
    var onContinue: (() -> Void)? = nil
    var onShare: (() -> Void)? = nil

    private typealias M = LevelUpTakeoverMotion

    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var clock = LevelUpTakeoverClock()
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private static let cream = Color(hex: 0xfff0b8)
    private static let edge = Color(hex: 0xffe08a)
    private static let ink = Color(hex: 0x1a1a2e)
    private static let mute = Color(hex: 0x8a7a50)
    private static let orange = Color(hex: 0xff6a1a)

    private static let glow = 420.0
    private static let dot = 12.0
    private static let coin = 34.0
    private static let ctaHeight = 56.0
    private static let shareHeight = 56.0
    private static let margin = 24.0
    /// The sunburst behind the badge: rays, its size, and how fast it turns (radians a second).
    private static let rays = 14
    private static let raySize = 460.0
    private static let rayTurn = 0.12

    /// Wedges from the centre of the sunburst, alternating with gaps.
    private static let rayPath: Path = {
        var path = Path()
        let c = raySize / 2
        let half = Double.pi / Double(rays) / 1.6
        for i in 0..<rays {
            let a = Double(i) / Double(rays) * tau
            path.move(to: CGPoint(x: c, y: c))
            path.addLine(to: CGPoint(x: c + cos(a - half) * c, y: c + sin(a - half) * c))
            path.addLine(to: CGPoint(x: c + cos(a + half) * c, y: c + sin(a + half) * c))
            path.closeSubpath()
        }
        return path
    }()

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
        .preferredColorScheme(.light)
        .onAppear { haptic.prepare() }
        .task(id: touches) { await resume() }
        .accessibilityLabel(title)
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
        // A press on Continue goes straight to the end of the screen.
        touch(from: M.pressAt, force: true)
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

    private static func grouped(_ value: Int) -> String {
        if value < 1000 { return String(value) }
        let rest = value % 1000
        return "\(value / 1000),\(rest < 10 ? "00" : rest < 100 ? "0" : "")\(rest)"
    }

    private func screen(t: Double, insets: EdgeInsets, size: CGSize) -> some View {
        let badgeY = max(insets.top + 150, size.height * 0.32)
        let titleTop = badgeY + LevelUpTakeoverBadge.height / 2 + 26
        let statsTop = titleTop + 100
        let linkBottom = insets.bottom + 12
        let ctaBottom = linkBottom + Self.shareHeight + 12
        let center = CGPoint(x: size.width / 2, y: badgeY)

        let gone = M.seg(t, M.burstAt, M.burstAt + M.dotOutIn)
        let back = M.seg(t, M.dotBackAt, M.dotBackAt + M.dotBackIn)
        let out = M.seg(t, M.outAt, M.outAt + M.outIn)
        let glowP = M.seg(t, M.glowAt, M.glowAt + M.glowFor)
        let raysP = reduced ? M.seg(t, M.burstAt, M.burstAt + M.reducedFade) : M.easeOut(M.seg(t, M.burstAt, M.burstAt + 0.6))
        // The sunburst turns slowly while it shows and holds still otherwise.
        let turn = reduced ? 0 : (min(max(t, M.burstAt), M.outAt + M.outIn) - M.burstAt) * Self.rayTurn
        let badgePop = t < M.burstAt ? 0 : M.badgeFrom + (1 - M.badgeFrom) * M.spring(t - M.burstAt, stiffness: M.badgeStiffness, damping: M.badgeDamping)
        // The sparkles blink on after the burst and twice more while the screen holds.
        let twinkle = t < M.burstAt + 0.3 ? 0 : 0.35 + 0.65 * abs(sin((t - M.burstAt - 0.3) * 1.6))
        let titleP = M.easeOut(M.seg(t, M.titleAt, M.titleAt + M.titleIn))
        let subtitleP = M.easeOut(M.seg(t, M.titleAt + 0.08, M.titleAt + 0.08 + M.titleIn))
        let cardP = M.easeOut(M.seg(t, M.statsAt - 0.1, M.statsAt + 0.3))
        let ctaInto = reduced ? M.seg(t, M.ctaAt, M.ctaAt + M.reducedFade) : min(1, M.spring(t - M.ctaAt, stiffness: M.ctaStiffness, damping: M.ctaDamping))
        let press = M.seg(t, M.pressAt, M.pressAt + M.pressDown) - M.seg(t, M.pressAt + M.pressDown, M.pressAt + M.pressDown + M.pressUp)
        let linkP = M.easeOut(M.seg(t, M.linkAt, M.linkAt + M.linkIn))

        return ZStack(alignment: .topLeading) {
            Canvas { context, canvas in
                context.fill(
                    Path(CGRect(origin: .zero, size: canvas)),
                    with: .radialGradient(Gradient(colors: [Self.cream, Self.edge]), center: center, startRadius: 0, endRadius: max(size.width, size.height) * 0.75)
                )
            }

            Circle().fill(Color(hex: 0xffd84a))
                .background(Circle().fill(Color.rgba(255, 184, 0, 0.7)).padding(-4).blur(radius: 7))
                .frame(width: Self.dot, height: Self.dot)
                .scaleEffect(1 + 0.15 * sin(t * 4) * (1 - gone))
                .opacity(t < M.dotBackAt ? 1 - gone : back)
                .offset(x: center.x - Self.dot / 2, y: badgeY - Self.dot / 2)
                .allowsHitTesting(false)

            // Everything but the dot fades and shrinks a little at the end.
            ZStack(alignment: .topLeading) {
                Circle()
                    .fill(RadialGradient(colors: [.white.opacity(0.9), .white.opacity(0)], center: .center, startRadius: 0, endRadius: Self.glow / 2))
                    .frame(width: Self.glow, height: Self.glow)
                    // A resting haze, and a pulse that spreads out of it once.
                    .scaleEffect(reduced ? 1 : 1 + 0.3 * M.easeOut(glowP))
                    .opacity(reduced ? 0.4 : 0.4 + 0.6 * sin(.pi * glowP) * (1 - glowP) * 2)
                    .offset(x: center.x - Self.glow / 2, y: badgeY - Self.glow / 2)

                Canvas { context, _ in
                    let c = Self.raySize / 2
                    context.translateBy(x: c, y: c)
                    context.rotate(by: .radians(turn))
                    context.translateBy(x: -c, y: -c)
                    context.fill(
                        Self.rayPath,
                        with: .radialGradient(
                            Gradient(stops: [
                                .init(color: .white.opacity(0.85), location: 0),
                                .init(color: .white.opacity(0.35), location: 0.45),
                                .init(color: .white.opacity(0), location: 1),
                            ]),
                            center: CGPoint(x: c, y: c), startRadius: 0, endRadius: c
                        )
                    )
                }
                .frame(width: Self.raySize, height: Self.raySize)
                .scaleEffect(reduced ? 1 : 0.6 + 0.4 * raysP)
                .opacity(raysP)
                .offset(x: center.x - Self.raySize / 2, y: badgeY - Self.raySize / 2)

                LevelUpTakeoverBadge(level: level, sweep: M.seg(t, M.shineAt, M.shineAt + M.shineFor), twinkle: twinkle)
                    .scaleEffect(reduced ? 1 : badgePop)
                    .opacity(reduced ? M.seg(t, M.burstAt, M.burstAt + M.reducedFade) : t < M.burstAt ? 0 : 1)
                    .offset(x: (size.width - LevelUpTakeoverBadge.boxWidth) / 2, y: badgeY - LevelUpTakeoverBadge.boxHeight / 2)

                Text(title)
                    .font(.system(size: 36, weight: .black)).kerning(0.5).foregroundStyle(Self.ink)
                    .frame(width: size.width, height: 42)
                    .opacity(titleP)
                    .offset(y: titleTop + (reduced ? 0 : (1 - titleP) * M.slide))

                Text(subtitle)
                    .font(.system(size: 16, weight: .medium)).foregroundStyle(Color(hex: 0x6f6140))
                    .lineSpacing(22 - UIFont.systemFont(ofSize: 16).lineHeight)
                    .multilineTextAlignment(.center)
                    .frame(width: size.width - 80)
                    .opacity(subtitleP)
                    .offset(x: 40, y: titleTop + 50 + (reduced ? 0 : (1 - subtitleP) * M.slide))

                stats(t: t)
                    .frame(width: size.width - Self.margin * 2, height: 128)
                    .opacity(cardP)
                    .offset(x: Self.margin, y: statsTop + (reduced ? 0 : (1 - cardP) * M.slide))

                Button(action: dismiss) {
                    Text(continueLabel).font(.system(size: 18, weight: .heavy)).kerning(-0.2).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(
                            Capsule()
                                .fill(LinearGradient(stops: [
                                    .init(color: Color(hex: 0xff8a3d), location: 0),
                                    .init(color: Self.orange, location: 0.55),
                                    .init(color: Color(hex: 0xf45400), location: 1),
                                ], startPoint: .top, endPoint: .bottom))
                                .shadow(color: .rgba(200, 70, 0, 0.25), radius: 2, y: 2)
                                .shadow(color: .rgba(255, 106, 26, 0.38), radius: 12, y: 12)
                        )
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .frame(width: size.width - Self.margin * 2, height: Self.ctaHeight)
                .scaleEffect(1 - (1 - M.pressScale) * press)
                .opacity(min(1, ctaInto * 3))
                .offset(x: Self.margin, y: size.height - ctaBottom - Self.ctaHeight + (reduced ? 0 : (1 - ctaInto) * M.ctaSlide))

                Button(action: { onShare?() }) {
                    HStack(spacing: 10) {
                        Image(systemName: "square.and.arrow.up").font(.system(size: 17)).foregroundStyle(Self.ink)
                            .frame(width: 20, height: 20)
                        Text(shareLabel).font(.system(size: 16, weight: .bold)).foregroundStyle(Self.ink)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        HStack(spacing: 5) {
                            Self.coinFace(size: 20, star: 9)
                            Text(shareReward).font(.system(size: 14, weight: .heavy)).foregroundStyle(Self.ink)
                        }
                        .padding(.leading, 5).padding(.trailing, 10).frame(height: 30)
                        .background(Color(hex: 0xfff7d6), in: Capsule())
                        .overlay(Capsule().strokeBorder(Color(hex: 0xffe28a), lineWidth: 1))
                    }
                    .padding(.horizontal, 18)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Color.white.opacity(0.9), lineWidth: 1))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .frame(width: size.width - Self.margin * 2, height: Self.shareHeight)
                .opacity(linkP)
                .offset(x: Self.margin, y: size.height - linkBottom - Self.shareHeight + (reduced ? 0 : (1 - linkP) * 12))
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .scaleEffect(reduced ? 1 : 1 - (1 - M.outScale) * out)
            .opacity(1 - out)

            if !reduced {
                LevelUpTakeoverConfetti(time: t, at: M.burstAt, origin: center)
                LevelUpTakeoverConfetti(time: t, at: M.secondBurstAt, origin: center, count: M.secondBurstCount)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    /// A gold coin with a white star, as on the coin figure and the share chip.
    private static func coinFace(size: Double, star: Double) -> some View {
        Circle().fill(LinearGradient(colors: [Color(hex: 0xffe066), Color(hex: 0xffb000)], startPoint: .top, endPoint: .bottom))
            .frame(width: size, height: size)
            .overlay(Image(systemName: "star.fill").font(.system(size: star)).foregroundStyle(.white))
    }

    private func stats(t: Double) -> some View {
        HStack(spacing: 0) {
            stat(t: t, to: points, at: M.statsAt, caption: pointsLabel, xp: false)
            Rectangle().fill(Color.rgba(138, 122, 80, 0.2)).frame(width: 1, height: 72)
            stat(t: t, to: xp, at: M.statsAt + M.statsStagger, caption: xpLabel, xp: true)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Color.white.opacity(0.62))
                .shadow(color: .rgba(150, 100, 0, 0.1), radius: 1.5, y: 1)
                .shadow(color: .rgba(196, 140, 20, 0.16), radius: 15, y: 12)
        )
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(Color.white.opacity(0.9), lineWidth: 1))
        .allowsHitTesting(false)
    }

    private func stat(t: Double, to: Int, at: Double, caption: String, xp: Bool) -> some View {
        let count = Int((Double(to) * (reduced ? M.seg(t, at, at + M.reducedFade) : M.easeOut(M.seg(t, at, at + M.statsCount)))).rounded())
        let p = M.easeOut(M.seg(t, at, at + 0.35))
        let colors = xp ? [Color(hex: 0x5aa2ff), Color(hex: 0x2a62ff)] : [Color(hex: 0xffe066), Color(hex: 0xffb000)]
        return VStack(spacing: 0) {
            Circle().fill(LinearGradient(colors: colors, startPoint: .top, endPoint: .bottom))
                .overlay(Circle().strokeBorder(Color(hex: xp ? 0x7ab4ff : 0xffd23a), lineWidth: 2))
                .overlay(Image(systemName: xp ? "bolt.fill" : "star.fill").font(.system(size: xp ? 15 : 14)).foregroundStyle(.white))
                .frame(width: Self.coin, height: Self.coin)
                .shadow(color: xp ? .rgba(40, 100, 255, 0.3) : .rgba(230, 150, 0, 0.35), radius: 4, y: 3)
                .padding(.bottom, 8)
            Text("+\(Self.grouped(count))")
                .font(.system(size: 26, weight: .heavy)).kerning(-0.4).monospacedDigit().foregroundStyle(Self.ink)
                .frame(height: 30)
            Text(caption).font(.system(size: 13, weight: .semibold)).foregroundStyle(Self.mute)
                .frame(height: 16)
                .padding(.top, 2)
        }
        .frame(maxWidth: .infinity)
        .opacity(p)
        .offset(y: reduced ? 0 : (1 - p) * 10)
    }
}

#Preview {
    LevelUpTakeover(autoPlay: true)
}
