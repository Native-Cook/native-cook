import SwiftUI
import UIKit

struct MenuProductCarousel: View {
    static let loop: Double = 15.267

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let autoPlay: Bool
    let freezeAt: Double?

    @State private var autoplayStartedAt: Date?
    @State private var isManual = false
    @State private var isTouching = false
    @State private var heldTimeMilliseconds = 0.0
    @State private var manualIndex = MenuProductCarouselContent.first
    @State private var manualStartIndex = MenuProductCarouselContent.first
    @State private var manualDragActivated = false
    @State private var manualSpring: MenuProductCarouselSpring?
    @State private var springGeneration = 0
    @State private var resumeGeneration = 0

    init(autoPlay: Bool = false, freezeAt: Double? = nil) {
        self.autoPlay = autoPlay
        self.freezeAt = freezeAt
        MenuProductCarouselAssets.registerFonts()
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            TimelineView(.animation(paused: !timelineNeedsFrames)) { timeline in
                scene(size: size, date: timeline.date)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(MenuProductCarouselPalette.background.ignoresSafeArea())
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .onAppear {
            autoplayStartedAt = .now
        }
        .onChange(of: reduceMotion) { _, reduced in
            resumeGeneration += 1
            if let spring = manualSpring {
                manualIndex = spring.to
                manualSpring = nil
                springGeneration += 1
            }
            if !isManual {
                heldTimeMilliseconds = 0
                autoplayStartedAt = .now
            } else if !reduced, autoPlay {
                manualIndex = MenuProductCarouselContent.first
                isManual = false
                heldTimeMilliseconds = 0
                autoplayStartedAt = .now
            }
        }
        .task(id: resumeGeneration) {
            guard autoPlay, !reduceMotion, freezeAt == nil, resumeGeneration > 0 else { return }
            try? await Task.sleep(nanoseconds: MenuProductCarouselMotion.resumeNanoseconds)
            guard !Task.isCancelled, !reduceMotion, !isTouching else { return }
            manualIndex = MenuProductCarouselContent.first
            isManual = false
            heldTimeMilliseconds = 0
            autoplayStartedAt = .now
        }
        .task(id: springGeneration) {
            guard let spring = manualSpring else { return }
            try? await Task.sleep(nanoseconds: UInt64(spring.settlingDuration * 1_000_000_000))
            guard !Task.isCancelled else { return }
            manualIndex = spring.to
            manualSpring = nil
        }
    }

    private var timelineNeedsFrames: Bool {
        !reduceMotion && ((autoPlay && freezeAt == nil && !isManual) || manualSpring != nil)
    }

    private func scene(size: CGSize, date: Date) -> some View {
        let time = displayedTime(at: date)
        let continuousIndex = isManual ? (manualSpring?.value(at: date) ?? manualIndex) : MenuProductCarouselMotion.carousel(at: time)
        let outro = MenuProductCarouselMotion.outro(at: time)
        let endCenter = CGPoint(
            x: CGFloat(MenuProductCarouselMetrics.logoEndLeft) +
                (MenuProductCarouselLogo.width * CGFloat(MenuProductCarouselMetrics.logoEndScale)) / 2,
            y: CGFloat(MenuProductCarouselMetrics.logoEndTop) +
                (MenuProductCarouselLogo.height * CGFloat(MenuProductCarouselMetrics.logoEndScale)) / 2
        )

        return ZStack(alignment: .topLeading) {
            MenuProductCarouselAssets.image("MenuProductCarousel-topo-bg.png")
                .resizable()
                .scaledToFill()
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .clipped()

            if autoPlay && freezeAt == nil && !reduceMotion {
                MenuProductCarouselRecorderPulse(startedAt: autoplayStartedAt)
                    .frame(width: size.width, height: size.height, alignment: .bottomTrailing)
                    .allowsHitTesting(false)
            }

            MenuProductCarouselLogo()
                .opacity(outro.topLogo)
                .accessibilityHidden(outro.topLogo < 0.5)
                .position(x: CGFloat(MenuProductCarouselMetrics.logoLeft) + MenuProductCarouselLogo.width / 2,
                          y: CGFloat(MenuProductCarouselMetrics.logoTop) + MenuProductCarouselLogo.height / 2)

            ForEach(MenuProductCarouselContent.items) { item in
                MenuProductCarouselCard(item: item, index: item.index, continuousIndex: continuousIndex, outro: outro)
            }

            Text("Don’t wait")
                .font(.custom(MenuProductCarouselAssets.uiFont, fixedSize: CGFloat(MenuProductCarouselMetrics.dontSize)))
                .foregroundStyle(MenuProductCarouselPalette.ink)
                .frame(width: size.width, height: CGFloat(MenuProductCarouselMetrics.dontSize), alignment: .top)
                .offset(y: CGFloat(MenuProductCarouselMetrics.dontTop))
                .opacity(outro.dont)
                .accessibilityHidden(outro.dont < 0.5)
                .allowsHitTesting(false)

            MenuProductCarouselWords(size: size, offsetX: outro.wordsX, shown: outro.wordsShown)
                .accessibilityHidden(outro.wordsShown < 0.5)
                .allowsHitTesting(false)

            MenuProductCarouselLogo()
                .opacity(outro.endLogo > 0 ? 1 : 0)
                .accessibilityHidden(outro.endLogo < 0.5)
                .scaleEffect(CGFloat(max(outro.endLogo, 0.02) * MenuProductCarouselMetrics.logoEndScale))
                .position(x: endCenter.x, y: endCenter.y)
                .allowsHitTesting(false)

            MenuProductCarouselTouchView(
                onBegan: beginTouch,
                onChanged: updateTouch(translationX:),
                onEnded: endTouch(velocityX:)
            )
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .zIndex(100)
            .allowsHitTesting(freezeAt == nil)
            .accessibilityHidden(true)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Menu: swipe through the dishes")
    }

    private func displayedTime(at date: Date) -> Double {
        if let freezeAt { return freezeAt }
        if isManual { return heldTimeMilliseconds }
        guard autoPlay, !reduceMotion else { return 0 }
        let start = autoplayStartedAt ?? date
        return date.timeIntervalSince(start).truncatingRemainder(dividingBy: Self.loop) * 1_000
    }

    private func beginTouch() {
        guard freezeAt == nil else { return }
        resumeGeneration += 1
        isTouching = true
        let now = Date.now
        if let spring = manualSpring {
            manualIndex = spring.value(at: now)
            manualSpring = nil
            springGeneration += 1
        } else if !isManual {
            heldTimeMilliseconds = displayedTime(at: now)
            manualIndex = MenuProductCarouselMotion.carousel(at: heldTimeMilliseconds)
        }
        manualStartIndex = manualIndex
        manualDragActivated = false
        isManual = true
    }

    private func updateTouch(translationX: CGFloat) {
        guard manualDragActivated || abs(translationX) > MenuProductCarouselMotion.activeOffset else { return }
        manualDragActivated = true
        let candidate = manualStartIndex - Double(translationX) / MenuProductCarouselMetrics.pitch
        manualIndex = MenuProductCarouselMotion.rubberBand(candidate)
    }

    private func endTouch(velocityX: CGFloat) {
        guard freezeAt == nil else { return }
        if manualDragActivated {
            let velocity = -Double(velocityX) / MenuProductCarouselMetrics.pitch
            let target = MenuProductCarouselMotion.clamp(
                (manualIndex + velocity * MenuProductCarouselMotion.projectSeconds).rounded(),
                0,
                Double(MenuProductCarouselContent.last)
            )
            if reduceMotion {
                manualIndex = target
                manualSpring = nil
            } else {
                manualSpring = MenuProductCarouselSpring(
                    from: manualIndex,
                    to: target,
                    configuredVelocity: velocity,
                    startedAt: .now
                )
                springGeneration += 1
            }
        }
        isTouching = false
        resumeGeneration += 1
    }
}

private struct MenuProductCarouselCard: View {
    let item: MenuProductCarouselItem
    let index: Int
    let continuousIndex: Double
    let outro: MenuProductCarouselOutro

    var body: some View {
        let position = cardPosition
        MenuProductCarouselCardFace(item: item)
            .frame(width: CGFloat(MenuProductCarouselMetrics.cardWidth), height: CGFloat(MenuProductCarouselMetrics.cardHeight), alignment: .topLeading)
            .scaleEffect(CGFloat(position.scale * outro.groupScale))
            .opacity(position.visible ? outro.groupOpacity : 0)
            .position(x: CGFloat(position.x), y: CGFloat(MenuProductCarouselMetrics.cardCenterY))
            .zIndex(position.zIndex)
            .accessibilityLabel(item.title)
    }

    private var cardPosition: (x: Double, scale: Double, visible: Bool, zIndex: Double) {
        let p = Double(index) - continuousIndex
        let proximity = min(abs(p), 1)
        let scale = 1 - (1 - MenuProductCarouselMetrics.sideScale) * proximity
        let lower = min(max(Int(floor(continuousIndex)), 0), MenuProductCarouselContent.last)
        let upper = min(MenuProductCarouselContent.last, lower + 1)
        let fraction = continuousIndex - Double(lower)
        let leftGap = MenuProductCarouselMotion.interpolate(
            MenuProductCarouselContent.items[lower].leftGap,
            MenuProductCarouselContent.items[upper].leftGap,
            fraction
        )
        let rightGap = MenuProductCarouselMotion.interpolate(
            MenuProductCarouselContent.items[lower].rightGap,
            MenuProductCarouselContent.items[upper].rightGap,
            fraction
        )
        let restCenter = MenuProductCarouselMotion.interpolate(
            MenuProductCarouselContent.items[lower].x + MenuProductCarouselMetrics.cardWidth / 2,
            MenuProductCarouselContent.items[upper].x + MenuProductCarouselMetrics.cardWidth / 2,
            fraction
        )
        let center = restCenter + p * (p < 0 ? leftGap : rightGap)
        let collapsed = MenuProductCarouselMetrics.screenCenterX +
            (center - MenuProductCarouselMetrics.screenCenterX) * outro.groupScale
        return (collapsed, scale, abs(p) <= 2.2, (10 - abs(p) * 3).rounded())
    }
}

private struct MenuProductCarouselCardFace: View {
    let item: MenuProductCarouselItem

    var body: some View {
        ZStack(alignment: .topLeading) {
            MenuProductCarouselAssets.image(item.imageName)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: CGFloat(item.image.width), height: CGFloat(item.image.height))
                .position(x: CGFloat(item.image.left + item.image.width / 2), y: CGFloat(item.image.top + item.image.height / 2))

            Text(item.title)
                .font(.custom(MenuProductCarouselAssets.titleFont, fixedSize: CGFloat(item.titleMetrics.size)))
                .foregroundStyle(MenuProductCarouselPalette.title)
                .lineLimit(1)
                .shadow(color: .white.opacity(0.35), radius: 6)
                .frame(width: CGFloat(MenuProductCarouselMetrics.cardWidth), height: CGFloat(item.titleMetrics.size), alignment: .center)
                .scaleEffect(x: CGFloat(item.titleMetrics.scaleX), y: 1, anchor: .center)
                .position(
                    x: CGFloat(item.titleMetrics.centerX),
                    y: CGFloat(item.titleMetrics.top - item.titleMetrics.size * MenuProductCarouselMetrics.titleAscent + item.titleMetrics.size / 2)
                )
        }
        .background(MenuProductCarouselPalette.card)
        .clipShape(RoundedRectangle(cornerRadius: CGFloat(MenuProductCarouselMetrics.cardRadius), style: .continuous))
        .shadow(
            color: MenuProductCarouselPalette.shadow,
            radius: CGFloat(MenuProductCarouselMetrics.cardShadowBlur),
            x: CGFloat(MenuProductCarouselMetrics.cardShadowX),
            y: CGFloat(MenuProductCarouselMetrics.cardShadowY)
        )
    }
}

private struct MenuProductCarouselWords: View {
    let size: CGSize
    let offsetX: Double
    let shown: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            word("TAKE A", top: MenuProductCarouselMetrics.takeTop, right: MenuProductCarouselMetrics.takeRight)
            word("BITE", top: MenuProductCarouselMetrics.biteTop, right: MenuProductCarouselMetrics.biteRight)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .offset(x: CGFloat(offsetX))
        .opacity(shown)
    }

    private func word(_ value: String, top: Double, right: Double) -> some View {
        HStack(alignment: .top, spacing: 0) {
            Spacer(minLength: 0)
            Text(value)
                .font(.custom(MenuProductCarouselAssets.wordFont, fixedSize: CGFloat(MenuProductCarouselMetrics.wordFontSize)))
                .foregroundStyle(MenuProductCarouselPalette.bite)
                .lineLimit(1)
                // Scale only the glyph's intrinsic box; RN transforms the Text rather than its full-screen parent.
                .scaleEffect(x: CGFloat(MenuProductCarouselMetrics.wordScaleX), y: 1, anchor: .center)
                .frame(height: CGFloat(MenuProductCarouselMetrics.wordLineHeight), alignment: .top)
        }
        .frame(width: CGFloat(right), height: CGFloat(MenuProductCarouselMetrics.wordLineHeight), alignment: .topTrailing)
        .offset(y: CGFloat(top - MenuProductCarouselMetrics.wordTopSize * MenuProductCarouselMetrics.wordAscent))
    }
}

/// A one-point recorder keep-alive matching the React Native component's 50 ms opacity repeat.
private struct MenuProductCarouselRecorderPulse: View {
    let startedAt: Date?

    var body: some View {
        TimelineView(.animation) { timeline in
            let start = startedAt ?? timeline.date
            let phase = timeline.date.timeIntervalSince(start)
                .truncatingRemainder(dividingBy: 0.1) / 0.05
            let opacity = phase <= 1 ? phase : 2 - phase
            MenuProductCarouselPalette.recorderPulse
                .opacity(opacity)
                .frame(width: 1, height: 1)
        }
    }
}

private struct MenuProductCarouselLogo: View {
    static let width = CGFloat(MenuProductCarouselMetrics.px(157))
    static let height = CGFloat(MenuProductCarouselMetrics.px(114))

    var body: some View {
        ZStack(alignment: .topLeading) {
            Text("Cafe & Restaurant")
                .font(.custom(MenuProductCarouselAssets.scriptFont, fixedSize: 6.2))
                .foregroundStyle(MenuProductCarouselPalette.brand)
                .frame(width: Self.width, height: 8, alignment: .leading)
                .offset(y: CGFloat(MenuProductCarouselMetrics.px(48)))

            logoWord("COFFEE", top: MenuProductCarouselMetrics.px(60))
            logoWord("TALES", top: MenuProductCarouselMetrics.px(87))

            Canvas { context, _ in
                let p: (Double) -> CGFloat = { CGFloat(MenuProductCarouselMetrics.px($0)) }
                var firstSteam = Path()
                firstSteam.move(to: CGPoint(x: p(108), y: p(42)))
                firstSteam.addCurve(
                    to: CGPoint(x: p(106), y: p(14)),
                    control1: CGPoint(x: p(98), y: p(32)),
                    control2: CGPoint(x: p(116), y: p(24))
                )
                firstSteam.addCurve(
                    to: CGPoint(x: p(110), y: p(0)),
                    control1: CGPoint(x: p(100), y: p(8)),
                    control2: CGPoint(x: p(106), y: p(3))
                )
                context.stroke(firstSteam, with: .color(MenuProductCarouselPalette.brand), style: .init(lineWidth: 1.1, lineCap: .round))

                var secondSteam = Path()
                secondSteam.move(to: CGPoint(x: p(114), y: p(44)))
                secondSteam.addCurve(
                    to: CGPoint(x: p(112), y: p(20)),
                    control1: CGPoint(x: p(106), y: p(36)),
                    control2: CGPoint(x: p(120), y: p(28))
                )
                context.stroke(secondSteam, with: .color(MenuProductCarouselPalette.brand), style: .init(lineWidth: 1.1, lineCap: .round))

                var cup = Path()
                cup.move(to: CGPoint(x: p(108), y: p(52)))
                cup.addLine(to: CGPoint(x: p(142), y: p(52)))
                cup.addLine(to: CGPoint(x: p(132), y: p(88)))
                cup.addQuadCurve(to: CGPoint(x: p(118), y: p(88)), control: CGPoint(x: p(125), y: p(94)))
                cup.closeSubpath()
                context.fill(cup, with: .color(MenuProductCarouselPalette.brand))

                var handle = Path()
                handle.move(to: CGPoint(x: p(140), y: p(58)))
                handle.addCurve(
                    to: CGPoint(x: p(134), y: p(80)),
                    control1: CGPoint(x: p(156), y: p(56)),
                    control2: CGPoint(x: p(156), y: p(76))
                )
                context.stroke(handle, with: .color(MenuProductCarouselPalette.brand), style: .init(lineWidth: 2))
                context.stroke(
                    Path(ellipseIn: CGRect(x: p(100), y: p(93), width: p(52), height: p(8))),
                    with: .color(MenuProductCarouselPalette.brand),
                    style: .init(lineWidth: 1.4)
                )

                var sparkle = Path()
                sparkle.move(to: CGPoint(x: p(95), y: p(96)))
                sparkle.addLine(to: CGPoint(x: p(97), y: p(101)))
                sparkle.addLine(to: CGPoint(x: p(102), y: p(103)))
                sparkle.addLine(to: CGPoint(x: p(97), y: p(105)))
                sparkle.addLine(to: CGPoint(x: p(95), y: p(110)))
                sparkle.addLine(to: CGPoint(x: p(93), y: p(105)))
                sparkle.addLine(to: CGPoint(x: p(88), y: p(103)))
                sparkle.addLine(to: CGPoint(x: p(93), y: p(101)))
                sparkle.closeSubpath()
                context.fill(sparkle, with: .color(MenuProductCarouselPalette.brand))
            }
            .frame(width: Self.width, height: Self.height, alignment: .topLeading)
        }
        .frame(width: Self.width, height: Self.height, alignment: .topLeading)
    }

    private func logoWord(_ value: String, top: Double) -> some View {
        Text(value)
            .font(.custom(MenuProductCarouselAssets.titleFont, fixedSize: 24))
            .tracking(0.2)
            .foregroundStyle(MenuProductCarouselPalette.brand)
            .frame(width: Self.width, height: 26, alignment: .leading)
            .offset(y: CGFloat(top))
    }
}

private enum MenuProductCarouselPalette {
    static let background = color(0xFED5BC)
    static let card = color(0x7F4B27)
    static let brand = color(0x6E3C1E)
    static let ink = color(0x111111)
    static let title = Color.white
    static let bite = color(0x7A4524)
    static let shadow = Color(red: 74 / 255, green: 38 / 255, blue: 16 / 255, opacity: 0.38)
    static let recorderPulse = color(0xFDD4BB)

    private static func color(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

private enum MenuProductCarouselMetrics {
    static let pxScale = 0.6828
    static let crop = 44.8
    static let screenWidth = 402.0
    static let screenCenterX = 201.0

    static func px(_ value: Double) -> Double { value * pxScale }
    static func pxX(_ value: Double) -> Double { value * pxScale - crop }

    static let cardWidth = px(360)
    static let cardHeight = px(477)
    static let cardCenterY = px(563.5)
    static let cardRadius = px(22)
    static let sideScale = 0.778
    static let pitch = 266.0
    static let cardShadowX = 8.0
    static let cardShadowY = 10.0
    static let cardShadowBlur = 16.0

    static let logoLeft = pxX(280)
    static let logoTop = px(62)
    static let logoEndLeft = pxX(235)
    static let logoEndTop = px(494)
    static let logoEndScale = 1.56

    static let dontTop = px(424) - 8
    static let dontSize = 52.0
    static let takeRight = pxX(589)
    static let biteRight = pxX(588)
    static let takeTop = px(535)
    static let biteTop = px(668)
    static let wordFontSize = 98.5
    static let wordLineHeight = 110.0
    static let wordScaleX = 1.27
    static let wordTopSize = 92.0
    static let titleAscent = 0.16
    static let wordAscent = 0.12
}

private struct MenuProductCarouselImageFrame {
    let left: Double
    let top: Double
    let width: Double
    let height: Double
}

private struct MenuProductCarouselTitleMetrics {
    let size: Double
    let scaleX: Double
    let centerX: Double
    let top: Double
}

private struct MenuProductCarouselItem: Identifiable {
    let id: String
    let index: Int
    let title: String
    let imageName: String
    let x: Double
    let titleMetrics: MenuProductCarouselTitleMetrics
    let image: MenuProductCarouselImageFrame
    let leftGap: Double
    let rightGap: Double
}

private enum MenuProductCarouselContent {
    static let first = 1.0
    static let last = 9

    private static func item(
        _ index: Int,
        _ id: String,
        _ image: String,
        _ title: String,
        _ cardX: Double,
        _ titleSize: Double,
        _ titleScaleX: Double,
        _ titleCenterX: Double,
        _ titleTop: Double,
        _ imageFrame: (Double, Double, Double, Double),
        _ gaps: (Double, Double)
    ) -> MenuProductCarouselItem {
        MenuProductCarouselItem(
            id: id,
            index: index,
            title: title,
            imageName: "MenuProductCarousel-\(image).png",
            x: MenuProductCarouselMetrics.pxX(cardX),
            titleMetrics: .init(
                size: titleSize,
                scaleX: titleScaleX,
                centerX: MenuProductCarouselMetrics.px(titleCenterX),
                top: MenuProductCarouselMetrics.px(titleTop)
            ),
            image: .init(left: imageFrame.0, top: imageFrame.1, width: imageFrame.2, height: imageFrame.3),
            leftGap: gaps.0,
            rightGap: gaps.1
        )
    }

    static let items = [
        item(0, "coldrink-0", "coldrink", "Coldrink", 178, 56.5, 0.819, 176.5, 57.3, (1.5, 41.8, 220.8, 249.6), (292, 267.4)),
        item(1, "coffee", "coffee", "Coffee", 187, 65.9, 0.78, 170, 59.3, (42.2, 102.5, 163.1, 184.3), (258.9, 272.6)),
        item(2, "burger", "burger", "Burger", 167, 54, 0.890, 179.3, 62.8, (28.3, 86.7, 167.1, 188.9), (274.3, 281.8)),
        item(3, "fries", "fries", "French Fries", 167, 46.4, 0.856, 174.5, 77.3, (16.8, 55, 225.2, 254.6), (284.5, 283.1)),
        item(4, "pizza", "pizza", "Pizza", 189, 56.4, 0.823, 171, 72.3, (30.1, 78.9, 184.1, 208.1), (285.9, 290)),
        item(5, "coldrink", "coldrink", "Coldrink", 178, 56.5, 0.819, 176.5, 57.3, (1.5, 41.8, 220.8, 249.6), (292, 267.4)),
        item(6, "pasta", "pasta", "Pasta", 159, 46.9, 1.025, 166.7, 107.3, (19.2, 78, 180.2, 203.7), (269.5, 264.7)),
        item(7, "taco", "taco", "Taco", 191, 66.1, 0.956, 164.2, 85.8, (30.1, 83.7, 176.3, 199.3), (267.4, 257.9)),
        item(8, "sandwich", "sandwich", "Sandwich", 165, 54.3, 0.831, 170.2, 93.3, (21.9, 82.7, 186.4, 210.7), (260.6, 264.7)),
        item(9, "coldrink-2", "coldrink", "Coldrink", 178, 56.5, 0.819, 176.5, 57.3, (1.5, 41.8, 220.8, 249.6), (292, 267.4)),
    ]
}

private struct MenuProductCarouselOutro {
    let groupScale: Double
    let groupOpacity: Double
    let dont: Double
    let wordsX: Double
    let wordsShown: Double
    let topLogo: Double
    let endLogo: Double
}

/// Reanimated 4.5.1's duration-based spring, including velocity clipping and energy termination.
private struct MenuProductCarouselSpring {
    static let mass = 4.0
    static let dampingRatio = 0.8
    static let energyThreshold = 6e-9
    static let settlingSeconds = 0.6 // 400 ms perceptual duration × Reanimated's 1.5 coefficient

    let from: Double
    let to: Double
    let velocity: Double
    let stiffness: Double
    let initialEnergy: Double
    let startedAt: Date
    let settlingDuration: Double

    init(from: Double, to: Double, configuredVelocity: Double, startedAt: Date) {
        self.from = from
        self.to = to
        self.startedAt = startedAt

        var animationVelocity = configuredVelocity
        if (to > from && animationVelocity < 0) || (to < from && animationVelocity > 0) {
            animationVelocity = 0
        }
        let resolvedStiffness = Self.matchedStiffness(
            displacement: from - to,
            velocity: animationVelocity
        )
        let resolvedEnergy = Self.energy(
            displacement: from - to,
            velocity: configuredVelocity,
            stiffness: resolvedStiffness
        )
        let resolvedDuration = Self.terminationTime(
            from: from,
            to: to,
            velocity: animationVelocity,
            stiffness: resolvedStiffness,
            initialEnergy: resolvedEnergy
        )
        velocity = animationVelocity
        stiffness = resolvedStiffness
        initialEnergy = resolvedEnergy
        settlingDuration = resolvedDuration
    }

    func value(at date: Date) -> Double {
        let elapsed = max(0, date.timeIntervalSince(startedAt))
        let sample = Self.sample(from: from, to: to, velocity: velocity, stiffness: stiffness, at: elapsed)
        guard initialEnergy > 0, sample.energy / initialEnergy > Self.energyThreshold else { return to }
        return sample.position
    }

    private static func energy(displacement: Double, velocity: Double, stiffness: Double) -> Double {
        0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
    }

    // Direct port of calculateNewStiffnessToMatchDuration from Reanimated 4.5.1.
    private static func matchedStiffness(displacement: Double, velocity: Double) -> Double {
        func energyDifference(_ stiffness: Double) -> Double {
            let omega = sqrt(stiffness / mass) * dampingRatio
            let envelope = exp(-omega * settlingSeconds)
            let position = (
                displacement + (velocity + displacement * omega) * settlingSeconds
            ) * envelope
            let speed = position * -omega + (velocity + displacement * omega) * envelope
            let initial = energy(displacement: displacement, velocity: velocity, stiffness: stiffness)
            guard initial > 0 else { return -energyThreshold }
            return energy(displacement: position, velocity: speed, stiffness: stiffness) / initial - energyThreshold
        }

        var lower = Double.ulpOfOne
        var upper = 8_000.0
        let direction = energyDifference(upper) >= energyDifference(lower) ? 1.0 : -1.0
        var current = (lower + upper) / 2
        for _ in 0..<100 {
            let difference = energyDifference(current)
            if abs(difference) <= energyThreshold * 1e-3 { return current }
            if difference * direction < 0 {
                lower = current
            } else {
                upper = current
            }
            current = (lower + upper) / 2
        }
        return current
    }

    // Direct port of underDampedSpringCalculations.
    private static func sample(
        from: Double,
        to: Double,
        velocity: Double,
        stiffness: Double,
        at time: Double
    ) -> (position: Double, energy: Double) {
        let displacement = from - to
        let omega0 = sqrt(stiffness / mass)
        let omega1 = omega0 * sqrt(1 - dampingRatio * dampingRatio)
        let envelope = exp(-dampingRatio * omega0 * time)
        let sine = sin(omega1 * time)
        let cosine = cos(omega1 * time)
        let fragment = envelope * (
            sine * ((velocity + dampingRatio * omega0 * displacement) / omega1) +
                displacement * cosine
        )
        let currentVelocity = -dampingRatio * omega0 * fragment + envelope * (
            cosine * (velocity + dampingRatio * omega0 * displacement) -
                omega1 * displacement * sine
        )
        return (
            to + fragment,
            energy(displacement: fragment, velocity: currentVelocity, stiffness: stiffness)
        )
    }

    private static func terminationTime(
        from: Double,
        to: Double,
        velocity: Double,
        stiffness: Double,
        initialEnergy: Double
    ) -> Double {
        guard initialEnergy > 0 else { return 0 }
        var lower = 0.0
        var upper = 1.0 / 240.0
        while upper < 5,
              sample(from: from, to: to, velocity: velocity, stiffness: stiffness, at: upper).energy /
                initialEnergy > energyThreshold {
            lower = upper
            upper += 1.0 / 240.0
        }
        for _ in 0..<30 {
            let middle = (lower + upper) / 2
            let fraction = sample(
                from: from,
                to: to,
                velocity: velocity,
                stiffness: stiffness,
                at: middle
            ).energy / initialEnergy
            if fraction > energyThreshold {
                lower = middle
            } else {
                upper = middle
            }
        }
        return upper
    }
}

private enum MenuProductCarouselMotion {
    static let loopMilliseconds = 15_267.0
    static let resumeNanoseconds: UInt64 = 3_500_000_000
    static let holdMilliseconds = 9_300.0
    static let projectSeconds = 0.15
    static let snapDuration = 0.4
    static let snapDampingRatio = 0.8
    static let activeOffset = CGFloat(8)
    private static let rubber = 0.3
    private static let shifts = [367.0, 1_733, 2_900, 4_067, 5_300, 6_500, 7_900]
    private static let step = 1_000.0 / 30
    private static let shiftCurve = [0.0, 0.024, 0.09, 0.19, 0.32, 0.46, 0.6, 0.73, 0.85, 0.94, 0.99, 1]
    private static let groupScale = [(9_420.0, 1.0), (9_670.0, 0.83), (9_830.0, 0.95), (10_030.0, 0.04)]
    private static let endLogo = [(12_667.0, 0.02), (12_700.0, 0.16), (12_767.0, 0.34), (12_833.0, 0.59), (12_900.0, 0.91), (12_967.0, 0.9), (13_033.0, 0.85), (13_100.0, 0.85), (13_167.0, 0.92), (13_233.0, 0.98), (13_367.0, 1.0)]

    static func clamp(_ value: Double, _ lower: Double, _ upper: Double) -> Double {
        min(max(value, lower), upper)
    }

    static func interpolate(_ first: Double, _ second: Double, _ amount: Double) -> Double {
        first + (second - first) * amount
    }

    static func carousel(at time: Double) -> Double {
        let index = shifts.reduce(MenuProductCarouselContent.first) { partial, shift in
            partial + shiftProgress(time - shift)
        }
        return min(index, Double(MenuProductCarouselContent.last))
    }

    static func rubberBand(_ index: Double) -> Double {
        if index < 0 { return index * rubber }
        if index > Double(MenuProductCarouselContent.last) {
            return Double(MenuProductCarouselContent.last) + (index - Double(MenuProductCarouselContent.last)) * rubber
        }
        return index
    }

    static func outro(at time: Double) -> MenuProductCarouselOutro {
        let wordsIn = segment(time, 10_433, 11_033)
        let wordsOut = segment(time, 12_233, 12_566)
        return MenuProductCarouselOutro(
            groupScale: keys(time, groupScale),
            groupOpacity: 1 - segment(time, 9_930, 10_030),
            dont: segment(time, 10_170, 10_370) * (1 - segment(time, 12_370, 12_500)),
            wordsX: -477 * (1 - quadraticOut(wordsIn)) + -614 * quadraticIn(wordsOut),
            wordsShown: time >= 10_433 && time < 12_566 ? 1 : 0,
            topLogo: 1 - segment(time, 12_230, 12_370),
            endLogo: time >= 12_667 ? keys(time, endLogo) : 0
        )
    }

    private static func shiftProgress(_ delta: Double) -> Double {
        guard delta > 0 else { return 0 }
        let position = delta / step
        let lower = Int(floor(position))
        guard lower < shiftCurve.count - 1 else { return 1 }
        return interpolate(shiftCurve[lower], shiftCurve[lower + 1], position - Double(lower))
    }

    private static func segment(_ value: Double, _ start: Double, _ end: Double) -> Double {
        clamp((value - start) / (end - start), 0, 1)
    }

    private static func quadraticOut(_ value: Double) -> Double {
        let clamped = clamp(value, 0, 1)
        return 1 - (1 - clamped) * (1 - clamped)
    }

    private static func quadraticIn(_ value: Double) -> Double {
        let clamped = clamp(value, 0, 1)
        return clamped * clamped
    }

    private static func keys(_ time: Double, _ values: [(Double, Double)]) -> Double {
        guard let first = values.first, let last = values.last else { return 0 }
        guard time > first.0 else { return first.1 }
        for index in 1..<values.count where time <= values[index].0 {
            let previous = values[index - 1]
            let current = values[index]
            return interpolate(previous.1, current.1, (time - previous.0) / (current.0 - previous.0))
        }
        return last.1
    }
}
