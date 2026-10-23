import SwiftUI

/// An onboarding screen whose notification preview is drawn inside a phone outline. The outline
/// draws on, a notification slides in and warms, and then it grows into a full photo card that
/// the outline clips. The clock drives all of it, so a touch and autoPlay run the same motion.
struct BannerGrowCard: View {
    var wordmark = "amber"
    var skipLabel = "Skip"
    var title = "Build your practice"
    var subtitle = "Get daily motivation and gentle reminders to sit, breathe and slow down."
    var buttonLabel = "Enable notifications"
    /// The notification the banner previews.
    var notificationTitle = "Time for your morning sit"
    var notificationBody = "Ten quiet minutes to begin."
    /// The caption of the grown card.
    var cardKicker = "TODAY'S PRACTICE"
    var cardTitle = "Morning sit"
    var cardMeta = "10 min · Breathwork"
    /// Grows the banner into the card by itself every `BannerGrowCardMotion.loop` seconds and shrinks it back.
    var autoPlay = false
    /// Called when the button is pressed by a touch.
    var onEnable: (() -> Void)? = nil

    private typealias M = BannerGrowCardMotion

    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var clock = BannerGrowCardClock()
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private static let ink = Color.white
    private static let mute = Color.white.opacity(0.55)
    private static let line = Color.white.opacity(0.32)
    private static let cream = Color.rgba(255, 240, 225, 0.78)
    private static let banner = 0x1c1c1e as UInt32
    private static let warm = 0x6b4224 as UInt32

    private static let outlineWidth = 272.0
    private static let outlineHeight = 424.0
    private static let outlineRadius = 46.0
    private static let outlineStroke = 1.5
    /// Room around the outline for the side buttons.
    private static let pad = 4.0
    private static let notchWidth = 64.0
    private static let notchHeight = 9.0
    /// The banner starts this far below the top of the outline, and the card ends at its bottom edge.
    private static let inset = (outlineWidth - BannerGrowCardScene.width) / 2
    private static let bannerTop = 44.0
    private static let bannerWidth = BannerGrowCardScene.width
    private static let bannerHeight = 72.0
    private static let bannerRadius = 18.0
    private static let cardRadius = outlineRadius - inset
    private static let cardHeight = BannerGrowCardScene.height
    private static let icon = 40.0
    private static let buttonHeight = 56.0
    private static let copyHeight = 112.0

    /// The phone's outline, open at the bottom: up the left side, over the top, down the right.
    private static let outlinePath: Path = {
        let o = pad + outlineStroke / 2, r = outlineRadius, w = outlineWidth, h = outlineHeight
        var p = Path()
        p.move(to: CGPoint(x: o, y: h))
        p.addLine(to: CGPoint(x: o, y: r + o))
        p.addRelativeArc(center: CGPoint(x: r + o, y: r + o), radius: r, startAngle: .degrees(180), delta: .degrees(90))
        p.addLine(to: CGPoint(x: w - r + o, y: o))
        p.addRelativeArc(center: CGPoint(x: w - r + o, y: r + o), radius: r, startAngle: .degrees(270), delta: .degrees(90))
        p.addLine(to: CGPoint(x: w + o, y: h))
        return p
    }()

    /// The phone's buttons: action and volume on the left, power on the right.
    private static let buttonsPath: Path = {
        let o = pad + outlineStroke / 2
        var p = Path()
        for (x, from, to) in [(o - 3, 92.0, 112.0), (o - 3, 132, 168), (o - 3, 180, 216), (outlineWidth + o + 3, 150, 214)] {
            p.move(to: CGPoint(x: x, y: from))
            p.addLine(to: CGPoint(x: x, y: to))
        }
        return p
    }()

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: !clock.isPlaying)) { timeline in
                screen(t: clock.time(at: timeline.date), insets: insets, size: size)
            }
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: touches) { await resume() }
    }

    // MARK: Clock

    private func press() {
        guard !clock.busy(at: .now) else { return }
        clock.auto = nil
        clock.touch = .init(start: .now, from: M.tapAt)
        touches += 1
        haptic.impactOccurred()
        onEnable?()
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

    /// 0 = banner, 1 = full card.
    private func grown(_ t: Double) -> Double {
        if reduced { return t >= M.growAt && t < M.collapseAt ? 1 : 0 }
        if t < M.collapseAt { return max(0, M.spring(t - M.growAt, stiffness: M.growStiffness, damping: M.growDamping)) }
        return 1 - M.easeInOut(M.seg(t, M.collapseAt, M.collapseAt + M.collapseIn))
    }

    /// 1 while the banner is on screen, with its slide-in and fade.
    private func bannerIn(_ t: Double) -> Double {
        M.seg(t, M.bannerAt, M.bannerAt + (reduced ? M.reducedFade : M.bannerIn)) * (1 - M.seg(t, M.bannerOutAt, M.bannerOutAt + M.bannerOutIn))
    }

    private func warmth(_ t: Double) -> Double {
        M.seg(t, M.warmAt, M.warmAt + M.warmIn) * (1 - M.seg(t, M.collapseAt, M.collapseAt + M.collapseIn))
    }

    /// A straight mix of two colours, with no gamma, like React Native's `interpolateColor`.
    private static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let from = Double((a >> shift) & 0xff), to = Double((b >> shift) & 0xff)
            return (from + (to - from) * t) / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
    }

    private func screen(t: Double, insets: EdgeInsets, size: CGSize) -> some View {
        // From the bottom up: the button, the copy, then the outline whose open foot sits above the copy.
        let buttonBottom = insets.bottom + 12
        let copyBottom = buttonBottom + Self.buttonHeight + 36
        let outlineTop = max(insets.top + 64, size.height - copyBottom - Self.copyHeight - 28 - Self.outlineHeight)
        let outlineLeft = (size.width - Self.outlineWidth) / 2 - Self.pad
        let cardLeft = (size.width - Self.bannerWidth) / 2
        let cardTop = outlineTop + Self.pad + Self.bannerTop
        return ZStack(alignment: .topLeading) {
            Color.black
            topRow(width: size.width).offset(y: insets.top + 14)
            glow(t: t).offset(x: cardLeft, y: cardTop)
            outline(t: t).offset(x: outlineLeft, y: outlineTop)
            card(t: t).offset(x: cardLeft, y: cardTop)
            VStack(spacing: 12) {
                Text(title).font(.system(size: 32, weight: .medium)).kerning(-0.8).foregroundStyle(Self.ink)
                    .multilineTextAlignment(.center)
                    .frame(minHeight: 38)
                Text(subtitle).font(.system(size: 16)).lineSpacing(22 - UIFont.systemFont(ofSize: 16).lineHeight).foregroundStyle(Self.mute)
                    .multilineTextAlignment(.center)
            }
            .frame(width: size.width - 64, height: Self.copyHeight, alignment: .top)
            .offset(x: 32, y: size.height - copyBottom - Self.copyHeight)
            Button(action: press) {
                HStack(spacing: 8) {
                    Image(systemName: "bell").font(.system(size: 16))
                    Text(buttonLabel).font(.system(size: 17, weight: .medium)).kerning(-0.2)
                }
                .foregroundStyle(Self.ink)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.white.opacity(0.04), in: Capsule())
                .overlay(Capsule().strokeBorder(Color.white.opacity(0.85), lineWidth: 1.5))
                .contentShape(Capsule())
            }
            .buttonStyle(BannerGrowCardPressStyle())
            .frame(width: size.width - 40, height: Self.buttonHeight)
            .offset(x: 20, y: size.height - buttonBottom - Self.buttonHeight)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func topRow(width: Double) -> some View {
        ZStack {
            Text(wordmark).font(.custom("Menlo", size: 16)).kerning(8).foregroundStyle(Self.ink)
                .padding(.leading, 8)
            HStack(spacing: 4) {
                Text(skipLabel).font(.system(size: 15, weight: .medium))
                Image(systemName: "arrow.right").font(.system(size: 13, weight: .medium))
            }
            .foregroundStyle(Color.white.opacity(0.72))
            .frame(maxWidth: .infinity, alignment: .trailing)
            .padding(.trailing, 20)
        }
        .frame(width: width, height: 24)
    }

    private func outline(t: Double) -> some View {
        let end = reduced ? 1
            : M.easeInOut(M.seg(t, M.outlineAt, M.outlineAt + M.outlineIn)) * (1 - M.easeInOut(M.seg(t, M.outlineOutAt, M.outlineOutAt + M.outlineOutIn)))
        let opacity = reduced ? M.seg(t, M.outlineAt, M.outlineAt + M.reducedFade) * (1 - M.seg(t, M.outlineOutAt, M.outlineOutAt + M.reducedFade)) : 1
        return ZStack(alignment: .top) {
            if end > 0 {
                Self.outlinePath.trimmedPath(from: 0, to: end)
                    .stroke(Self.line, style: StrokeStyle(lineWidth: Self.outlineStroke, lineCap: .round))
            }
            Self.buttonsPath.stroke(Self.line, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                .opacity(M.seg(end, 0.75, 1))
            Capsule().strokeBorder(Self.line, lineWidth: 1.5)
                .frame(width: Self.notchWidth, height: Self.notchHeight)
                .padding(.top, Self.pad + 14)
                .opacity(M.seg(end, 0.4, 0.7))
        }
        .frame(width: Self.outlineWidth + Self.pad * 2, height: Self.outlineHeight, alignment: .top)
        .opacity(opacity)
    }

    /// Warm light spilling round the card once it is a photo.
    private func glow(t: Double) -> some View {
        let g = min(1, grown(t))
        return RoundedRectangle(cornerRadius: Self.cardRadius, style: .circular)
            .fill(Color(hex: 0x8a4c22))
            .shadow(color: .rgba(240, 150, 70, 0.22), radius: 14)
            .shadow(color: .rgba(226, 132, 62, 0.34), radius: 35, y: 16)
            .frame(width: Self.bannerWidth, height: Self.bannerHeight + (Self.cardHeight - Self.bannerHeight) * g)
            .opacity(g * bannerIn(t))
            .allowsHitTesting(false)
    }

    private func card(t: Double) -> some View {
        let g = grown(t)
        let warm = warmth(t)
        let slide = reduced ? 0 : (1 - M.easeOut(M.seg(t, M.bannerAt, M.bannerAt + M.bannerIn))) * -14
        let topRadius = Self.bannerRadius + (Self.cardRadius - Self.bannerRadius) * min(1, g)
        let bottomRadius = Self.bannerRadius * (1 - M.seg(g, 0.6, 1))
        let height = Self.bannerHeight + (Self.cardHeight - Self.bannerHeight) * g
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: topRadius, bottomLeadingRadius: bottomRadius,
            bottomTrailingRadius: bottomRadius, topTrailingRadius: topRadius, style: .circular
        )
        let breathe = reduced ? 0 : sin(.pi * M.seg(t, M.breatheAt, M.breatheAt + M.breatheFor)) * M.breathe
        // The notification leaves as the card grows and comes back as it shrinks.
        let out = t < M.collapseAt
            ? M.seg(t, M.linesOutAt, M.linesOutAt + M.linesOutIn)
            : 1 - M.seg(t, M.collapseAt + M.collapseIn * 0.5, M.collapseAt + M.collapseIn)
        let caption = M.easeOut(M.seg(t, M.captionAt, M.captionAt + M.captionIn)) * (1 - M.seg(t, M.collapseAt, M.collapseAt + 0.25))
        // Children sit inside the 1 pt border, as React Native lays them out.
        return ZStack(alignment: .topLeading) {
            BannerGrowCardScene()
                .scaleEffect(1.04 + breathe - 0.04 * min(g, 1))
                .opacity(max(M.peek * warm, min(g, 1)))
            notification(t: t)
                .opacity(1 - out)
            captionView
                .opacity(caption)
                .offset(x: 20, y: Self.cardHeight - 116 + (reduced ? 0 : (1 - caption) * 10))
        }
        .offset(x: 1, y: 1)
        .frame(width: Self.bannerWidth, height: height, alignment: .topLeading)
        .background(Self.mix(Self.banner, Self.warm, max(warm, min(g, 1))))
        .clipShape(shape)
        .overlay(shape.strokeBorder(Color.white.opacity(0.14 * (1 - M.seg(g, 0, 0.3))), lineWidth: 1))
        .opacity(bannerIn(t))
        .offset(y: slide)
        .allowsHitTesting(false)
    }

    private func notification(t: Double) -> some View {
        HStack(spacing: 11) {
            ZStack(alignment: .bottom) {
                LinearGradient(colors: [Color(hex: 0xf6b25c), Color(hex: 0xc8692a)], startPoint: .top, endPoint: .bottom)
                Circle().fill(Color(hex: 0xfff4e2)).frame(width: 16, height: 16)
                    .frame(maxHeight: .infinity, alignment: .top).padding(.top, 11)
                Color.rgba(80, 32, 8, 0.55).frame(height: 13)
            }
            .frame(width: Self.icon, height: Self.icon)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .circular))
            .scaleEffect(reduced ? 1 : 0.6 + 0.4 * M.spring(t - M.bannerAt - 0.1, stiffness: 300, damping: 18))
            VStack(alignment: .leading, spacing: 1) {
                line(0, t: t) {
                    HStack {
                        Text("AMBER").font(.system(size: 11, weight: .semibold)).kerning(0.8)
                        Spacer(minLength: 0)
                        Text("now").font(.system(size: 11))
                    }
                    .foregroundStyle(Self.mute)
                    .frame(height: 14)
                }
                line(1, t: t) {
                    Text(notificationTitle).font(.system(size: 14, weight: .semibold)).kerning(-0.2).foregroundStyle(Self.ink)
                        .lineLimit(1).frame(height: 18)
                }
                line(2, t: t) {
                    Text(notificationBody).font(.system(size: 13)).foregroundStyle(Color.white.opacity(0.72))
                        .lineLimit(1).frame(height: 17)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 12)
        .frame(width: Self.bannerWidth, height: Self.bannerHeight)
    }

    /// One line of the notification, sliding in a beat after the one above it.
    private func line(_ index: Int, t: Double, @ViewBuilder content: () -> some View) -> some View {
        let at = M.bannerAt + 0.22 + Double(index) * 0.06
        let p = reduced ? 1 : M.easeOut(M.seg(t, at, at + 0.35))
        return content().opacity(p).offset(x: (1 - p) * -8)
    }

    private var captionView: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(cardKicker).font(.system(size: 11, weight: .bold)).kerning(1.4).foregroundStyle(Self.cream).frame(height: 14)
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(cardTitle).font(.system(size: 28, weight: .semibold)).kerning(-0.6).foregroundStyle(Self.ink).frame(height: 32)
                    Text(cardMeta).font(.system(size: 14, weight: .medium)).foregroundStyle(Self.cream).frame(height: 18)
                }
                Spacer(minLength: 0)
                Circle().fill(Color(hex: 0xfff6ec))
                    .shadow(color: .rgba(30, 10, 0, 0.35), radius: 8, y: 6)
                    .frame(width: 48, height: 48)
                    .overlay(Image(systemName: "play.fill").font(.system(size: 17)).foregroundStyle(Color(hex: 0x3a1f0e)).padding(.leading, 3))
            }
        }
        .frame(width: Self.bannerWidth - 2 - 20 - 18)
    }
}

/// The button dims and shrinks a little while it is held, as React Native's `Pressable` does here.
private struct BannerGrowCardPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.6 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

#Preview {
    BannerGrowCard(autoPlay: true)
}
