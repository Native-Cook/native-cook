import QuartzCore
import SwiftUI
import UIKit

/// A welcome screen whose hero changes hands: the black card tips back and drops behind a green
/// card wallet, two notes rise out of the wallet's pocket and slide back in, and the card rises
/// out of it and lands where it started. A tap on the hero plays it.
struct WalletUnpackWelcome: View {
    var overline = "EXPECT MORE FROM YOUR"
    var title = "WALLET"
    var tagline = "a prepaid wallet designed for the\ncreditworthy"
    var subtitle = "A PAYMENT EXPERIENCE THAT'S"
    /// The large lines under the button, cut off by it and by the screen's edge.
    var teaser = "FAST,\nCONVENIENT &"
    var buttonLabel = "Get started"
    var cardLabel = "CREDIT CARD"
    var cardNumber = "1234 5678 9123 XXXX"
    var cardExpiry = "12/25"
    /// Plays the hero's sequence by itself every `loop` seconds, from the card back to the card.
    var autoPlay = false
    var onGetStarted: (() -> Void)?
    var onBack: (() -> Void)?

    private typealias T = WalletUnpackWelcomeTimeline
    private typealias Art = WalletUnpackWelcomeArt

    private enum Palette {
        static let ink = Color(hex: 0x111111)
        static let tagline = Color(hex: 0x333333)
        static let rule = Color(hex: 0x888888)
        static let teaser = Color(hex: 0x555555, opacity: 0.6)
        static let cardTop = Color(hex: 0x232323)
        static let cardBottom = Color(hex: 0x0E0E0E)
        static let chipTop = Color(hex: 0xE8D2A0)
        static let chipBottom = Color(hex: 0xB39459)
        static let chipLine = Color.rgba(122, 98, 56, 0.7)
        static let cardInk = Color(hex: 0xE5E5E5)
        static let cardMute = Color(hex: 0xA8A8A8)
    }

    // Layout, in points: the header from the safe area's top, the footer from its bottom, the hero
    // a little below the middle.
    private enum Metrics {
        static let backTop = 40.0
        static let overlineTop = 127.0
        static let titleTop = 144.0
        static let heroAt = 0.545
        static let taglineUp = 211.0
        static let ruleUp = 147.0
        static let ruleX = 48.0
        static let subtitleUp = 92.0
        static let buttonUp = 74.0
        static let buttonHeight = 44.0
        static let margin = 24.0
        static let teaserUp = 49.0
        static let teaserX = 72.0
        static let fade = 120.0
        static let cardW = 200.0
        static let cardH = 128.0
        static let cardRadius = 12.0
        /// The perspective the card tips back in, in points from the eye.
        static let perspective = 700.0
        static let pressed = 0.97
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The clock every motion reads counts from here.
    @State private var start = Date.now
    /// When the loop started, in seconds of the clock; nil while touches have the screen.
    @State private var origin: Double?
    /// When a tap started the sequence; nil when none is playing.
    @State private var playAt: Double?
    @GestureState private var touching = false
    @State private var lastTouch = 0.0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    private func clock(_ date: Date = .now) -> Double { date.timeIntervalSince(start) }

    /// Seconds into the sequence at `now`: the loop's while it plays, else a tap's.
    private func sequence(_ now: Double) -> Double {
        if let origin, now >= origin { return (now - origin).truncatingRemainder(dividingBy: T.loop) }
        if let playAt, now - playAt < T.sequenceEnd { return now - playAt }
        return 0
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            // The timeline rests while the card lies still by hand.
            TimelineView(.animation(paused: origin == nil && playAt == nil)) { timeline in
                let hero = T.hero(at: sequence(clock(timeline.date)), still: reduceMotion)
                content(hero, screen: screen, insets: insets)
                    .frame(width: screen.width, height: screen.height)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(Color.white.ignoresSafeArea())
        .preferredColorScheme(.light)
        .contentShape(Rectangle())
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, touching, _ in touching = true }
                .onChanged { _ in if origin != nil { takeOver() } }
        )
        .onChange(of: touching) { _, down in
            if !down { lastTouch = clock() }
        }
        .onAppear {
            start = .now
            origin = autoPlay ? 0 : nil
            tapHaptic.prepare()
        }
        .onChange(of: autoPlay) { _, on in origin = on ? clock() : nil }
        .task(id: IdleKey(lastTouch: lastTouch, touching: touching, playing: origin != nil, playAt: playAt, autoPlay: autoPlay)) {
            await idle()
        }
    }

    // MARK: The screen

    private func content(_ h: WalletUnpackWelcomeHero, screen: CGSize, insets: EdgeInsets) -> some View {
        let w = screen.width
        let heroY = screen.height * Metrics.heroAt
        let bottom = screen.height - insets.bottom

        return ZStack(alignment: .topLeading) {
            // A soft band of shade across the white, like light on brushed metal.
            LinearGradient(
                stops: [.init(color: .white, location: 0), .init(color: Color(hex: 0xDADADA), location: 0.42), .init(color: .white, location: 0.68), .init(color: Color(hex: 0xE9E9E9), location: 1)],
                startPoint: UnitPoint(x: 0, y: 0.15), endPoint: UnitPoint(x: 1, y: 0.85)
            )
            .frame(width: w, height: screen.height)
            LinearGradient(colors: [Color(hex: 0xA9A9A9), Color(hex: 0xA9A9A9, opacity: 0)], startPoint: .top, endPoint: .bottom)
                .frame(width: w, height: insets.top + 30)

            Button { onBack?() } label: {
                Canvas { context, _ in
                    var arrow = Path()
                    arrow.move(to: CGPoint(x: 0.75, y: 6))
                    arrow.addLine(to: CGPoint(x: 20, y: 6))
                    arrow.move(to: CGPoint(x: 5.5, y: 1))
                    arrow.addLine(to: CGPoint(x: 0.75, y: 6))
                    arrow.addLine(to: CGPoint(x: 5.5, y: 11))
                    context.stroke(arrow, with: .color(Palette.ink), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                }
                .frame(width: 20, height: 12)
                .frame(width: 44, height: 44, alignment: .leading)
                .padding(.leading, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            .offset(x: Metrics.margin - 12, y: insets.top + Metrics.backTop - 22)

            text(overline, size: 9, height: 12, weight: .semibold, spacing: 2, color: Palette.ink)
                .frame(width: w - Metrics.margin * 2)
                .offset(x: Metrics.margin, y: insets.top + Metrics.overlineTop)
            text(title, size: 76, height: 84, weight: .ultraLight, spacing: 1, color: Palette.ink)
                .frame(width: w)
                .offset(y: insets.top + Metrics.titleTop)
                .accessibilityAddTraits(.isHeader)

            hero(h)
                .position(x: w / 2, y: heroY)

            Text(tagline)
                .font(.system(size: 11))
                .lineSpacing(20 - UIFont.systemFont(ofSize: 11).lineHeight)
                .multilineTextAlignment(.center)
                .foregroundStyle(Palette.tagline)
                .frame(width: w - Metrics.margin * 2)
                .offset(x: Metrics.margin, y: bottom - Metrics.taglineUp + (20 - UIFont.systemFont(ofSize: 11).lineHeight) / 2)
            Path { path in
                path.move(to: CGPoint(x: 0, y: 0.5))
                path.addLine(to: CGPoint(x: w - Metrics.ruleX * 2, y: 0.5))
            }
            .stroke(Palette.rule, style: StrokeStyle(lineWidth: 1, dash: [1.5, 2.5]))
            .frame(width: w - Metrics.ruleX * 2, height: 1)
            .offset(x: Metrics.ruleX, y: bottom - Metrics.ruleUp)

            Text(teaser)
                .font(.system(size: 36, weight: .ultraLight))
                .lineSpacing(38 - UIFont.systemFont(ofSize: 36, weight: .ultraLight).lineHeight)
                .foregroundStyle(Palette.teaser)
                .lineLimit(2)
                .fixedSize()
                .offset(x: Metrics.teaserX, y: bottom - Metrics.teaserUp)
                .accessibilityHidden(true)
            LinearGradient(colors: [Color.rgba(189, 189, 189, 0), Color.rgba(189, 189, 189, 0.9)], startPoint: .top, endPoint: .bottom)
                .frame(width: w, height: Metrics.fade)
                .offset(y: screen.height - Metrics.fade)
                .allowsHitTesting(false)
            text(subtitle, size: 9, height: 12, weight: .semibold, spacing: 1.5, color: Palette.ink)
                .frame(width: w - Metrics.margin * 2)
                .offset(x: Metrics.margin, y: bottom - Metrics.subtitleUp)

            Button {
                tapHaptic.impactOccurred()
                onGetStarted?()
            } label: {
                text(buttonLabel, size: 11, height: 14, weight: .medium, color: .white)
                    .frame(width: w - Metrics.margin * 2, height: Metrics.buttonHeight)
                    .background(Color.black, in: RoundedRectangle(cornerRadius: 2))
            }
            .buttonStyle(WalletUnpackWelcomePress())
            .offset(x: Metrics.margin, y: bottom - Metrics.buttonUp)
        }
    }

    private func text(_ string: String, size: Double, height: Double, weight: Font.Weight, spacing: Double = 0, color: Color) -> some View {
        Text(string)
            .font(.system(size: size, weight: weight))
            .tracking(spacing)
            .foregroundStyle(color)
            .lineLimit(1)
            .frame(height: height)
    }

    /// The wallet's back, the notes, the card, the wallet's pocket: in that order, so that what
    /// slides in or out of the pocket passes behind its front.
    private func hero(_ h: WalletUnpackWelcomeHero) -> some View {
        ZStack {
            Color.clear
                .frame(width: Art.walletW + 40, height: Art.walletH + 40)
                .contentShape(Rectangle())
                .onTapGesture(perform: play)
                .accessibilityElement()
                .accessibilityLabel("Card and wallet")
                .accessibilityHint("Plays the animation")
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { play() }
            Group {
                Canvas { context, _ in Art.drawBack(context) }
                    .frame(width: Art.walletW, height: Art.walletH)
                if h.wallet > 0.001 {
                    Canvas { context, _ in Art.drawNotes(context, hero: h) }
                        .frame(width: Art.notesW, height: Art.notesH)
                }
            }
            .opacity(h.wallet)
            .scaleEffect(h.walletScale)
            card(face: h.face)
                .projectionEffect(Self.tip(h.tip))
                .scaleEffect(h.cardScale)
                .rotationEffect(.degrees(h.turn))
                .offset(y: h.cardY)
                .opacity(h.card)
            Canvas { context, _ in Art.drawPocket(context) }
                .frame(width: Art.walletW, height: Art.walletH)
                .opacity(h.wallet)
                .scaleEffect(h.walletScale)
        }
        .frame(width: Art.notesW, height: Art.notesH)
    }

    /// The card tipped `degrees` about its horizontal axis, top edge away, seen from `perspective`
    /// points: rotate about the card's centre, then project.
    private static func tip(_ degrees: Double) -> ProjectionTransform {
        var transform = CATransform3DMakeTranslation(-Metrics.cardW / 2, -Metrics.cardH / 2, 0)
        transform = CATransform3DConcat(transform, CATransform3DMakeRotation(degrees * .pi / 180, 1, 0, 0))
        var perspective = CATransform3DIdentity
        perspective.m34 = -1 / Metrics.perspective
        transform = CATransform3DConcat(transform, perspective)
        transform = CATransform3DConcat(transform, CATransform3DMakeTranslation(Metrics.cardW / 2, Metrics.cardH / 2, 0))
        return ProjectionTransform(transform)
    }

    private func card(face: Double) -> some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: [Palette.cardTop, Palette.cardBottom], startPoint: .topLeading, endPoint: .bottomTrailing)
            LinearGradient(colors: [Palette.chipTop, Palette.chipBottom], startPoint: .top, endPoint: .bottom)
                .overlay(alignment: .topLeading) {
                    ZStack(alignment: .topLeading) {
                        Palette.chipLine.frame(height: 1).offset(y: 7)
                        Palette.chipLine.frame(height: 1).offset(y: 14)
                        Palette.chipLine.frame(width: 1, height: 8).offset(x: 13.5, y: 7)
                    }
                }
                .frame(width: 28, height: 22)
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .offset(x: 20, y: 32)
            Canvas { context, _ in
                // The contactless mark: three arcs opening to the right.
                for r in [3.0, 6, 9] {
                    var arc = Path()
                    arc.addArc(center: CGPoint(x: 2, y: 7), radius: r, startAngle: .degrees(-45), endAngle: .degrees(45), clockwise: false)
                    context.stroke(arc, with: .color(Palette.cardInk), style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
                }
            }
            .frame(width: 14, height: 14)
            .offset(x: Metrics.cardW - 18 - 14, y: 36)
            Text(cardLabel)
                .font(.system(size: 6, weight: .medium))
                .tracking(1.2)
                .foregroundStyle(Color(hex: 0xCFCFCF))
                .lineLimit(1)
                .frame(width: Metrics.cardW - 32, height: 8, alignment: .trailing)
                .offset(x: 16, y: 14)
            Text(cardNumber)
                .font(.system(size: 12, design: .monospaced))
                .tracking(1)
                .foregroundStyle(Palette.cardInk)
                .lineLimit(1)
                .frame(height: 16)
                .offset(x: 20, y: 78)
            Text(cardExpiry)
                .font(.system(size: 7, design: .monospaced))
                .foregroundStyle(Palette.cardMute)
                .lineLimit(1)
                .frame(height: 10)
                .offset(x: 20, y: 98)
            // Past edge-on the card shows its plain back: the printing goes first.
            Palette.cardBottom.opacity(1 - face)
        }
        .frame(width: Metrics.cardW, height: Metrics.cardH)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: Actions

    /// A touch takes the screen from the loop; a sequence it was in the middle of plays on.
    private func takeOver() {
        let now = clock()
        lastTouch = now
        guard origin != nil else { return }
        let q = sequence(now)
        playAt = q > T.outAt && q < T.sequenceEnd ? now - q : nil
        origin = nil
    }

    private func play() {
        let now = clock()
        if let playAt, now - playAt < T.sequenceEnd { return }
        tapHaptic.impactOccurred()
        playAt = now - T.outAt
    }

    /// A sequence played by hand ends at rest; a while after it and the last touch, the loop
    /// starts again.
    private func idle() async {
        guard origin == nil, !touching else { return }
        let ends = playAt.map { $0 + T.sequenceEnd } ?? 0
        try? await Task.sleep(for: .seconds(max(0, ends - clock())))
        guard !Task.isCancelled else { return }
        playAt = nil
        guard autoPlay else { return }
        try? await Task.sleep(for: .seconds(max(0, max(lastTouch, ends) + T.userHold - clock())))
        guard !Task.isCancelled else { return }
        origin = clock()
    }

    private struct IdleKey: Equatable {
        let lastTouch: Double
        let touching: Bool
        let playing: Bool
        let playAt: Double?
        let autoPlay: Bool
    }
}

/// A button that shrinks a little while it is held.
private struct WalletUnpackWelcomePress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}
