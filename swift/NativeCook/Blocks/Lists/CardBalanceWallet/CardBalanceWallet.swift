import CoreText
import SwiftUI
import UIKit

enum CardBalanceWalletAction {
    case notifications, addMoney, transfer, scanToPay, addCard, overview, insights, messages
}

struct CardBalanceWallet: View {
    static let loop: Double = CardBalanceWalletMotion.loop

    var autoPlay = false
    var onAction: ((CardBalanceWalletAction) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var timelineActive = false
    @State private var autoStart = Date.now
    @State private var heldPose = CardBalanceWalletMotion.Pose()
    @State private var contact: Contact?
    @State private var release: Release?
    @State private var pressedTarget: PressedTarget?
    @State private var interrupted = false
    @State private var scriptGeneration = 0
    @State private var releaseGeneration = 0
    @State private var resumeGeneration = 0
    @State private var resumePending = false

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let windowTop = UIApplication.shared.connectedScenes
                .compactMap { ($0 as? UIWindowScene)?.keyWindow?.safeAreaInsets.top }
                .first ?? 0
            let safeTop = max(insets.top, windowTop)
            let offsetY = safeTop > 0 ? safeTop - Metrics.safeTop : -40

            TimelineView(.animation(minimumInterval: 1 / 120, paused: !needsTimeline)) { timeline in
                let pose = pose(at: timeline.date)
                Surface(
                    width: width,
                    height: height,
                    offsetY: offsetY,
                    pose: pose,
                    reduced: reduceMotion,
                    pressedTarget: pressedTarget,
                    onAction: onAction
                )
                .overlay(alignment: .topLeading) {
                    CardBalanceWalletTouch(
                        onBegan: { point in touchBegan(point, width: width, offsetY: offsetY, date: .now) },
                        onMoved: { point in touchMoved(point, width: width, offsetY: offsetY) },
                        onEnded: { point, velocity in
                            touchEnded(point, velocity: velocity, width: width, offsetY: offsetY, date: .now)
                        }
                    )
                    .frame(width: width, height: height)
                    .accessibilityHidden(true)
                }
                .frame(width: width, height: height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(hex: 0x030303).ignoresSafeArea())
        .environment(\.colorScheme, .dark)
        .onAppear(perform: restartScript)
        .onChange(of: autoPlay) { _, _ in restartScript() }
        .onChange(of: reduceMotion) { _, _ in restartScript() }
        .task(id: LoopKey(autoPlay: autoPlay, reduced: reduceMotion, generation: scriptGeneration)) {
            await autoplayLoop()
        }
        .task(id: releaseGeneration) {
            guard release != nil else { return }
            try? await Task.sleep(for: .milliseconds(1_500))
            guard !Task.isCancelled, contact == nil else { return }
            release = nil
            heldPose = CardBalanceWalletMotion.Pose()
        }
        .task(id: resumeGeneration) {
            guard resumePending, autoPlay, !reduceMotion else { return }
            try? await Task.sleep(for: .milliseconds(Int64(CardBalanceWalletMotion.resumeAfterMS)))
            guard !Task.isCancelled, resumePending, autoPlay, !reduceMotion, contact == nil else { return }
            resumePending = false
            interrupted = false
            release = nil
            heldPose = CardBalanceWalletMotion.Pose()
            scriptGeneration += 1
        }
    }

    private var needsTimeline: Bool {
        !reduceMotion && (timelineActive || contact != nil || release != nil)
    }

    private func pose(at date: Date) -> CardBalanceWalletMotion.Pose {
        if let contact {
            if reduceMotion { return CardBalanceWalletMotion.Pose() }
            let elapsed = max(0, date.timeIntervalSince(contact.started) * 1_000)
            let follow = contact.moved
                ? 1
                : CardBalanceWalletMotion.easeOut(min(1, elapsed / CardBalanceWalletMotion.followInMS))
            let lift: Double
            if elapsed < CardBalanceWalletMotion.pressMS {
                let progress = CardBalanceWalletMotion.easeOut(elapsed / CardBalanceWalletMotion.pressMS)
                lift = contact.initial.lift
                    + (CardBalanceWalletMotion.pressLift - contact.initial.lift) * progress
            } else {
                lift = CardBalanceWalletMotion.pickupLift(milliseconds: elapsed)
            }
            return CardBalanceWalletMotion.Pose(
                rx: contact.initial.rx + (contact.rx - contact.initial.rx) * follow,
                ry: contact.initial.ry + (contact.ry - contact.initial.ry) * follow,
                lift: lift
            )
        }
        if let release {
            if reduceMotion { return CardBalanceWalletMotion.Pose() }
            return CardBalanceWalletMotion.releasedPose(
                milliseconds: date.timeIntervalSince(release.started) * 1_000,
                initial: release.initial,
                velocityX: release.velocityX,
                velocityY: release.velocityY
            )
        }
        if timelineActive {
            return CardBalanceWalletMotion.pose(
                milliseconds: date.timeIntervalSince(autoStart) * 1_000,
                reduced: reduceMotion
            )
        }
        return heldPose
    }

    private func restartScript() {
        timelineActive = false
        heldPose = CardBalanceWalletMotion.Pose()
        contact = nil
        release = nil
        interrupted = false
        resumePending = false
        scriptGeneration += 1
        releaseGeneration += 1
        resumeGeneration += 1
    }

    private func autoplayLoop() async {
        guard autoPlay, !reduceMotion, !interrupted else {
            timelineActive = false
            return
        }
        while !Task.isCancelled {
            timelineActive = false
            heldPose = CardBalanceWalletMotion.Pose()
            let restIn = reduceMotion ? 1_000 : 600
            let active = reduceMotion ? 2_600 : 5_600
            let restOut = reduceMotion ? 3_400 : 800
            try? await Task.sleep(for: .milliseconds(restIn))
            guard !Task.isCancelled else { return }
            autoStart = Date.now.addingTimeInterval(-Double(restIn) / 1_000)
            timelineActive = true
            try? await Task.sleep(for: .milliseconds(active))
            guard !Task.isCancelled else { return }
            timelineActive = false
            heldPose = CardBalanceWalletMotion.Pose()
            try? await Task.sleep(for: .milliseconds(restOut))
        }
    }

    private func touchBegan(_ point: CGPoint, width: Double, offsetY: Double, date: Date) {
        heldPose = pose(at: date)
        heldPose.glow = 0
        timelineActive = false
        interrupted = true
        resumePending = false
        scriptGeneration += 1
        resumeGeneration += 1
        release = nil
        releaseGeneration += 1
        pressedTarget = onAction == nil ? nil : PressedTarget.hit(point, width: width, offsetY: offsetY)

        let cardWidth = width - Metrics.inset * 2
        let cardFrame = CGRect(x: Metrics.inset, y: offsetY + Metrics.cardTop, width: cardWidth, height: Metrics.cardHeight)
        guard cardFrame.contains(point), !reduceMotion else {
            contact = nil
            return
        }
        let x = min(1, max(0, (point.x - cardFrame.minX) / cardFrame.width)) - 0.5
        let y = min(1, max(0, (point.y - cardFrame.minY) / cardFrame.height)) - 0.5
        let tilt = CardBalanceWalletMotion.tilt(x: x, y: y)
        contact = Contact(started: date, initial: heldPose, rx: tilt.rx, ry: tilt.ry, moved: false)
    }

    private func touchMoved(_ point: CGPoint, width: Double, offsetY: Double) {
        if let pressedTarget,
           !pressedTarget.contains(point, width: width, offsetY: offsetY, retention: 16) {
            self.pressedTarget = nil
        }
        guard var contact, !reduceMotion else { return }
        let cardWidth = width - Metrics.inset * 2
        let x = min(1, max(0, (point.x - Metrics.inset) / cardWidth)) - 0.5
        let y = min(1, max(0, (point.y - offsetY - Metrics.cardTop) / Metrics.cardHeight)) - 0.5
        let tilt = CardBalanceWalletMotion.tilt(x: x, y: y)
        contact.rx = tilt.rx
        contact.ry = tilt.ry
        contact.moved = true
        self.contact = contact
    }

    private func touchEnded(_ point: CGPoint, velocity: CGVector, width: Double, offsetY: Double, date: Date) {
        if let pressedTarget,
           pressedTarget.contains(point, width: width, offsetY: offsetY, retention: 16) {
            onAction?(pressedTarget.action)
        }
        pressedTarget = nil
        if let contact, !reduceMotion {
            let initial = pose(at: date)
            let cardWidth = width - Metrics.inset * 2
            release = Release(
                started: date,
                initial: initial,
                velocityX: -velocity.dy / Metrics.cardHeight * 2 * CardBalanceWalletMotion.tiltMax,
                velocityY: velocity.dx / cardWidth * 2 * CardBalanceWalletMotion.tiltMax
            )
            self.contact = nil
            releaseGeneration += 1
        } else {
            contact = nil
        }
        if autoPlay && !reduceMotion {
            resumePending = true
            resumeGeneration += 1
        }
    }
}

private extension View {
    @ViewBuilder
    func cardBalanceWalletAction(
        _ action: CardBalanceWalletAction,
        onAction: ((CardBalanceWalletAction) -> Void)?
    ) -> some View {
        if let onAction {
            accessibilityAddTraits(.isButton)
                .accessibilityAction { onAction(action) }
                .disabled(false)
        } else {
            accessibilityAddTraits(.isButton)
                .disabled(true)
        }
    }
}

private extension CardBalanceWallet {
    struct Contact {
        let started: Date
        let initial: CardBalanceWalletMotion.Pose
        var rx: Double
        var ry: Double
        var moved: Bool
    }

    struct Release {
        let started: Date
        let initial: CardBalanceWalletMotion.Pose
        let velocityX: Double
        let velocityY: Double
    }

    struct LoopKey: Hashable {
        let autoPlay: Bool
        let reduced: Bool
        let generation: Int
    }

    enum PressedTarget: Equatable {
        case bell
        case addMoney
        case transfer
        case scan
        case addCard
        case overview
        case insights
        case messages

        static func hit(_ point: CGPoint, width: Double, offsetY: Double) -> Self? {
            all.first(where: { $0.contains(point, width: width, offsetY: offsetY, retention: 4) })
        }

        func contains(_ point: CGPoint, width: Double, offsetY: Double, retention: Double) -> Bool {
            frame(width: width, offsetY: offsetY).insetBy(dx: -retention, dy: -retention).contains(point)
        }

        private static let all: [Self] = [
            .bell, .addMoney, .transfer, .scan, .addCard, .overview, .insights, .messages,
        ]

        var action: CardBalanceWalletAction {
            switch self {
            case .bell: .notifications
            case .addMoney: .addMoney
            case .transfer: .transfer
            case .scan: .scanToPay
            case .addCard: .addCard
            case .overview: .overview
            case .insights: .insights
            case .messages: .messages
            }
        }

        private func frame(width: Double, offsetY: Double) -> CGRect {
            let actionWidth = (width - Metrics.inset * 2 - Metrics.actionGap) / 2
            return switch self {
            case .bell:
                CGRect(x: width - 28.8 - 22, y: offsetY + Metrics.headerY - 22, width: 44, height: 44)
            case .addMoney:
                CGRect(x: Metrics.inset, y: offsetY + Metrics.actionsTop, width: actionWidth, height: Metrics.actionsHeight)
            case .transfer:
                CGRect(x: Metrics.inset + actionWidth + Metrics.actionGap, y: offsetY + Metrics.actionsTop, width: actionWidth, height: Metrics.actionsHeight)
            case .scan:
                CGRect(x: Metrics.inset, y: offsetY + Metrics.tilesTop, width: Metrics.tileSize, height: Metrics.tileSize)
            case .addCard:
                CGRect(x: Metrics.inset + Metrics.tileSize + Metrics.tileGap, y: offsetY + Metrics.tilesTop, width: Metrics.tileSize, height: Metrics.tileSize)
            case .overview:
                Self.tabFrame(index: 0, offsetY: offsetY)
            case .insights:
                Self.tabFrame(index: 1, offsetY: offsetY)
            case .messages:
                Self.tabFrame(index: 2, offsetY: offsetY)
            }
        }

        private static func tabFrame(index: Int, offsetY: Double) -> CGRect {
            let center = Metrics.tabCircleXs[index]
            let y = offsetY + Metrics.tabTrackTop + (Metrics.tabTrackHeight - Metrics.tabCircle) / 2
            return CGRect(x: center - Metrics.tabCircle / 2, y: y, width: Metrics.tabCircle, height: Metrics.tabCircle)
        }
    }

    enum Metrics {
        static let safeTop = 62.0
        static let inset = 15.8
        static let headerY = 109.2
        static let avatar = 47.0
        static let nameX = 74.5
        static let bell = 24.0
        static let cardTop = 166.3
        static let cardHeight = 239.7
        static let cardRadius = 24.0
        static let cardPadX = 17.0
        static let cardLabelY = 25.8
        static let badgeRight = 15.9
        static let badgeTop = 45.7
        static let badgeWidth = 33.8
        static let badgeHeight = 23.8
        static let badgeRadius = 5.0
        static let actionsTop = 419.0
        static let actionsHeight = 82.4
        static let actionRadius = 24.0
        static let actionGap = 12.9
        static let actionPadX = 17.0
        static let dotsX = 183.7
        static let dotsY = 528.0
        static let activeDot = 22.0
        static let dot = 3.4
        static let dotGap = 2.6
        static let dotHeight = 3.6
        static let tilesTop = 563.3
        static let tileSize = 88.4
        static let tileGap = 5.0
        static let tileRadius = 20.0
        static let spentTop = 685.3
        static let spentRadius = 24.0
        static let spentTitleY = 28.8
        static let barY = 82.5
        static let barHeight = 16.3
        static let barGap = 3.0
        static let tabBand = 780.0
        static let tabTrackTop = 785.2
        static let tabTrackHeight = 62.6
        static let tabTrackX = 32.8
        static let tabPillWidth = 117.0
        static let tabPillHeight = 57.0
        static let tabCircle = 57.3
        static let tabCircleXs = [195.6, 266.0, 335.7]
    }

    struct Surface: View {
        let width: Double
        let height: Double
        let offsetY: Double
        let pose: CardBalanceWalletMotion.Pose
        let reduced: Bool
        let pressedTarget: PressedTarget?
        let onAction: ((CardBalanceWalletAction) -> Void)?

        var body: some View {
            ZStack(alignment: .topLeading) {
                Color(hex: 0x030303)
                Header(width: width, offsetY: offsetY, pressedTarget: pressedTarget, onAction: onAction)
                CardView(width: width, offsetY: offsetY, pose: pose, reduced: reduced)
                Actions(width: width, offsetY: offsetY, pressedTarget: pressedTarget, onAction: onAction)
                CardShadow(width: width, offsetY: offsetY, lift: pose.lift)
                Dots(offsetY: offsetY, ry: pose.ry)
                Tiles(offsetY: offsetY, pressedTarget: pressedTarget, onAction: onAction)
                Spent(width: width, offsetY: offsetY)
                TabBar(width: width, height: height, offsetY: offsetY, pressedTarget: pressedTarget, onAction: onAction)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .clipped()
        }
    }

    struct Header: View {
        let width: Double
        let offsetY: Double
        let pressedTarget: PressedTarget?
        let onAction: ((CardBalanceWalletAction) -> Void)?

        var body: some View {
            Image(uiImage: CardBalanceWalletAssets.profile)
                .resizable()
                .scaledToFill()
                .frame(width: Metrics.avatar, height: Metrics.avatar)
                .clipShape(.circle)
                .background(Color(hex: 0xDEDEDE), in: .circle)
                .offset(x: Metrics.inset, y: offsetY + Metrics.headerY - Metrics.avatar / 2)
                .accessibilityLabel("Carmen")
            Text("Carmen")
                .font(CardBalanceWalletAssets.font(CardBalanceWalletAssets.poppinsMedium, size: 12.5))
                .foregroundStyle(.white)
                .frame(height: 20)
                .fixedSize()
                .offset(x: Metrics.nameX, y: offsetY + Metrics.headerY - 10)
            ZStack(alignment: .topLeading) {
                CardBalanceWalletGlyph(kind: .bell, color: .white, size: Metrics.bell + 2)
                    .position(x: 22, y: 22)
                Circle()
                    .fill(Color(hex: 0x6E5BFF))
                    .frame(width: 8, height: 8)
                    .offset(x: 27, y: 7)
            }
            .frame(width: 44, height: 44)
            .scaleEffect(pressedTarget == .bell ? CardBalanceWalletMotion.pressScale : 1)
            .offset(x: width - 28.8 - 22, y: offsetY + Metrics.headerY - 22)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Notifications, 1 new")
            .cardBalanceWalletAction(.notifications, onAction: onAction)
        }
    }

    struct CardView: View {
        let width: Double
        let offsetY: Double
        let pose: CardBalanceWalletMotion.Pose
        let reduced: Bool

        var body: some View {
            let cardWidth = width - Metrics.inset * 2
            let shift = reduced ? 0 : (pose.ry / CardBalanceWalletMotion.tiltMax) * CardBalanceWalletMotion.refraction
            let specular = reduced
                ? pose.glow
                : min(1, max(0, pose.lift)) * CardBalanceWalletMotion.specular + pose.glow
            let light = reduced
                ? 0.5
                : 0.5 - (pose.ry / CardBalanceWalletMotion.tiltMax) * CardBalanceWalletMotion.specularTravel
            ZStack(alignment: .topLeading) {
                Image(uiImage: CardBalanceWalletAssets.field)
                    .resizable()
                    .interpolation(.high)
                    .frame(width: cardWidth, height: Metrics.cardHeight)
                    .layerEffect(
                        ShaderLibrary.cardBalanceWalletFluted(
                            .float2(Float(cardWidth), Float(Metrics.cardHeight)),
                            .float(Float(shift)),
                            .float(Float(specular)),
                            .float(Float(light))
                        ),
                        maxSampleOffset: CGSize(width: 16, height: 0)
                    )
                Text("card balance")
                    .font(CardBalanceWalletAssets.font(CardBalanceWalletAssets.poppinsRegular, size: 15.6))
                    .kerning(0.1)
                    .foregroundStyle(.white)
                    .frame(height: 24)
                    .fixedSize()
                    .offset(x: Metrics.cardPadX, y: Metrics.cardLabelY - 12)
                Text("*6745")
                    .font(CardBalanceWalletAssets.font(CardBalanceWalletAssets.poppinsRegular, size: 15.6))
                    .kerning(0.1)
                    .foregroundStyle(.white)
                    .frame(width: cardWidth - Metrics.cardPadX * 2, height: 24, alignment: .trailing)
                    .offset(x: Metrics.cardPadX, y: Metrics.cardLabelY - 12)
                ZStack {
                    RoundedRectangle(cornerRadius: Metrics.badgeRadius)
                        .fill(.white)
                    CardBalanceWalletGlyph(kind: .network, color: .clear, size: 26)
                }
                .frame(width: Metrics.badgeWidth, height: Metrics.badgeHeight)
                .offset(x: cardWidth - Metrics.badgeRight - Metrics.badgeWidth, y: Metrics.badgeTop)
                Balance()
            }
            .frame(width: cardWidth, height: Metrics.cardHeight, alignment: .topLeading)
            .clipShape(.rect(cornerRadius: Metrics.cardRadius, style: .continuous))
            .modifier(CardProjection(rx: reduced ? 0 : pose.rx, ry: reduced ? 0 : pose.ry))
            .scaleEffect(reduced ? 1 : 1 + CardBalanceWalletMotion.liftScale * pose.lift)
            .offset(x: Metrics.inset, y: offsetY + Metrics.cardTop)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Card ending 6745, balance $12,567")
            .accessibilityAddTraits(.isSummaryElement)
        }
    }

    struct Balance: View {
        private static let inkLeft = [18.5, 57.2, 88.9, 130.5, 150.5, 191.5, 233.9]
        private static let text = Array("$12,567")

        var body: some View {
            Canvas { context, _ in
                let font = UIFont.systemFont(ofSize: 65.5, weight: .heavy) as CTFont
                for (index, character) in Self.text.enumerated() {
                    let units = Array(String(character).utf16)
                    guard units.count == 1 else { continue }
                    var source = units[0]
                    var glyph = CGGlyph()
                    guard CTFontGetGlyphsForCharacters(font, &source, &glyph, 1) else { continue }
                    var bounds = CGRect.zero
                    CTFontGetBoundingRectsForGlyphs(font, .horizontal, &glyph, &bounds, 1)
                    var transform = CGAffineTransform(
                        a: 1,
                        b: 0,
                        c: 0,
                        d: -1,
                        tx: Self.inkLeft[index] - bounds.minX,
                        ty: 207.4
                    )
                    guard let glyphPath = CTFontCreatePathForGlyph(font, glyph, &transform) else { continue }
                    context.fill(Path(glyphPath), with: .color(.white))
                }
            }
            .allowsHitTesting(false)
        }
    }

    struct CardProjection: GeometryEffect {
        var rx: Double
        var ry: Double

        var animatableData: AnimatablePair<Double, Double> {
            get { AnimatablePair(rx, ry) }
            set {
                rx = newValue.first
                ry = newValue.second
            }
        }

        func effectValue(size: CGSize) -> ProjectionTransform {
            var transform = CATransform3DIdentity
            transform.m34 = -1 / 900
            transform = CATransform3DTranslate(transform, size.width / 2, size.height / 2, 0)
            transform = CATransform3DRotate(transform, rx * .pi / 180, 1, 0, 0)
            transform = CATransform3DRotate(transform, ry * .pi / 180, 0, 1, 0)
            transform = CATransform3DTranslate(transform, -size.width / 2, -size.height / 2, 0)
            return ProjectionTransform(transform)
        }
    }

    struct Actions: View {
        let width: Double
        let offsetY: Double
        let pressedTarget: PressedTarget?
        let onAction: ((CardBalanceWalletAction) -> Void)?

        var body: some View {
            let total = width - Metrics.inset * 2
            let itemWidth = (total - Metrics.actionGap) / 2
            action("add money", glyph: .plus, target: .addMoney, width: itemWidth)
                .offset(x: Metrics.inset, y: offsetY + Metrics.actionsTop)
            action("transfer", glyph: .arrow, target: .transfer, width: itemWidth)
                .offset(x: Metrics.inset + itemWidth + Metrics.actionGap, y: offsetY + Metrics.actionsTop)
        }

        private func action(_ title: String, glyph: CardBalanceWalletGlyph.Kind, target: PressedTarget, width: Double) -> some View {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: Metrics.actionRadius, style: .continuous).fill(.white)
                Text(title)
                    .font(CardBalanceWalletAssets.font(CardBalanceWalletAssets.poppinsMedium, size: 16.4))
                    .kerning(-0.1)
                    .foregroundStyle(Color(hex: 0x0B0B0C))
                    .frame(height: 22)
                    .fixedSize()
                    .offset(x: Metrics.actionPadX, y: (Metrics.actionsHeight - 22) / 2 - 3)
                CardBalanceWalletGlyph(kind: glyph, color: Color(hex: 0x0B0B0C), size: 24)
                    .offset(x: width - Metrics.actionPadX - 24, y: (Metrics.actionsHeight - 24) / 2)
            }
            .frame(width: width, height: Metrics.actionsHeight)
            .scaleEffect(pressedTarget == target ? CardBalanceWalletMotion.pressScale : 1)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(title == "add money" ? "Add money" : "Transfer")
            .cardBalanceWalletAction(target.action, onAction: onAction)
        }
    }

    struct CardShadow: View {
        let width: Double
        let offsetY: Double
        let lift: Double

        var body: some View {
            let progress = min(1, max(0, lift))
            LinearGradient(
                colors: [Color.black.opacity(0.55), .clear],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: width - Metrics.inset * 2, height: 34)
            .clipShape(UnevenRoundedRectangle(
                topLeadingRadius: Metrics.actionRadius,
                topTrailingRadius: Metrics.actionRadius,
                style: .continuous
            ))
            .opacity(CardBalanceWalletMotion.shadow * progress)
            .offset(
                x: Metrics.inset,
                y: offsetY + Metrics.actionsTop + CardBalanceWalletMotion.shadowDrop * progress
            )
            .allowsHitTesting(false)
        }
    }

    struct Dots: View {
        let offsetY: Double
        let ry: Double

        var body: some View {
            let extra = CardBalanceWalletMotion.dotStretch
                * min(1, abs(ry) / CardBalanceWalletMotion.tiltMax)
            ZStack(alignment: .topLeading) {
                Capsule()
                    .fill(.white)
                    .frame(width: Metrics.activeDot + extra, height: Metrics.dotHeight)
                    .offset(x: ry < 0 ? -extra : 0)
                Capsule()
                    .fill(Color(hex: 0x1F1F21))
                    .frame(width: Metrics.dot, height: Metrics.dotHeight)
                    .offset(x: Metrics.activeDot + Metrics.dotGap)
                Capsule()
                    .fill(Color(hex: 0x1F1F21))
                    .frame(width: Metrics.dot, height: Metrics.dotHeight)
                    .offset(x: Metrics.activeDot + Metrics.dot + Metrics.dotGap * 2)
            }
            .frame(width: 60, height: Metrics.dotHeight, alignment: .topLeading)
            .offset(x: Metrics.dotsX, y: offsetY + Metrics.dotsY - Metrics.dotHeight / 2)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Card 1 of 3")
        }
    }

    struct Tiles: View {
        let offsetY: Double
        let pressedTarget: PressedTarget?
        let onAction: ((CardBalanceWalletAction) -> Void)?

        var body: some View {
            tile(glyph: .scan, size: 28, label: "Scan to pay", target: .scan)
                .offset(x: Metrics.inset, y: offsetY + Metrics.tilesTop)
            tile(glyph: .plus, size: 24, label: "Add card", target: .addCard)
                .offset(x: Metrics.inset + Metrics.tileSize + Metrics.tileGap, y: offsetY + Metrics.tilesTop)
        }

        private func tile(
            glyph: CardBalanceWalletGlyph.Kind,
            size: Double,
            label: String,
            target: PressedTarget
        ) -> some View {
            ZStack {
                RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous)
                    .fill(Color(hex: 0x1F1F1F))
                CardBalanceWalletGlyph(kind: glyph, color: .white, size: size)
            }
            .frame(width: Metrics.tileSize, height: Metrics.tileSize)
            .scaleEffect(pressedTarget == target ? CardBalanceWalletMotion.pressScale : 1)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(label)
            .cardBalanceWalletAction(target.action, onAction: onAction)
        }
    }

    struct Spent: View {
        let width: Double
        let offsetY: Double
        private let split = [0.3, 0.15, 0.22, 0.33]
        private let colors = [0xEAB4BB, 0xB4E2EC, 0xC9B6EF, 0xE8EAB3]

        var body: some View {
            let spentWidth = width - Metrics.inset * 2
            let barWidth = spentWidth - Metrics.cardPadX * 2
            let room = barWidth - Metrics.barGap * 3
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: Metrics.spentRadius, style: .continuous)
                    .fill(Color(hex: 0x1F1F1F))
                HStack(spacing: 0) {
                    Text("Spent in ").foregroundStyle(Color(hex: 0x8B8B8F))
                    Text("May").foregroundStyle(.white)
                }
                .font(CardBalanceWalletAssets.font(CardBalanceWalletAssets.poppinsMedium, size: 16.8))
                .frame(height: 24)
                .fixedSize()
                .offset(x: Metrics.cardPadX, y: Metrics.spentTitleY - 12)
                Text("$1,789")
                    .font(CardBalanceWalletAssets.font(CardBalanceWalletAssets.poppinsSemibold, size: 21))
                    .kerning(-0.3)
                    .foregroundStyle(.white)
                    .frame(width: spentWidth - Metrics.cardPadX * 2, height: 30, alignment: .trailing)
                    .offset(x: Metrics.cardPadX, y: Metrics.spentTitleY - 15)
                ForEach(split.indices, id: \.self) { index in
                    Capsule()
                        .fill(Color(hex: UInt32(colors[index])))
                        .frame(width: split[index] * room, height: Metrics.barHeight)
                        .offset(x: barX(index: index, room: room), y: Metrics.barY - Metrics.barHeight / 2)
                }
            }
            .frame(width: spentWidth, height: 140, alignment: .topLeading)
            .offset(x: Metrics.inset, y: offsetY + Metrics.spentTop)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Spent in May, $1,789")
        }

        private func barX(index: Int, room: Double) -> Double {
            Metrics.cardPadX + split.prefix(index).reduce(0) { $0 + $1 * room + Metrics.barGap }
        }
    }

    struct TabBar: View {
        let width: Double
        let height: Double
        let offsetY: Double
        let pressedTarget: PressedTarget?
        let onAction: ((CardBalanceWalletAction) -> Void)?

        var body: some View {
            let bandTop = offsetY + Metrics.tabBand
            ZStack(alignment: .topLeading) {
                LinearGradient(
                    stops: [
                        .init(color: Color(hex: 0x1E1D1D).opacity(0), location: 0),
                        .init(color: Color(hex: 0x1E1D1D).opacity(0.92), location: 0.12),
                        .init(color: Color(hex: 0x1E1D1D).opacity(0.92), location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                ZStack(alignment: .topLeading) {
                    Capsule().fill(Color(hex: 0x2B2B2D))
                    HStack(spacing: 6) {
                        CardBalanceWalletGlyph(kind: .wallet, color: Color(hex: 0x6E5BFF), size: 26)
                        Text("All cards")
                            .font(CardBalanceWalletAssets.font(CardBalanceWalletAssets.poppinsSemibold, size: 15))
                            .foregroundStyle(Color(hex: 0x6E5BFF))
                            .frame(height: 20)
                    }
                    .frame(width: Metrics.tabPillWidth, height: Metrics.tabPillHeight)
                    .background(.white, in: .capsule)
                    .offset(x: 5, y: (Metrics.tabTrackHeight - Metrics.tabPillHeight) / 2)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("All cards")
                    .accessibilityAddTraits([.isButton, .isSelected])
                    tabCircle(kind: .grid, index: 0, label: "Overview", target: .overview)
                    tabCircle(kind: .chart, index: 1, label: "Insights", target: .insights)
                    tabCircle(kind: .chat, index: 2, label: "Messages", target: .messages)
                }
                .frame(width: width - Metrics.tabTrackX * 2, height: Metrics.tabTrackHeight)
                .offset(x: Metrics.tabTrackX, y: Metrics.tabTrackTop - Metrics.tabBand)
            }
            .frame(width: width, height: max(0, height - bandTop), alignment: .topLeading)
            .offset(y: bandTop)
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Tab bar")
        }

        private func tabCircle(
            kind: CardBalanceWalletGlyph.Kind,
            index: Int,
            label: String,
            target: PressedTarget
        ) -> some View {
            ZStack {
                Circle().fill(Color(hex: 0x8E8E93))
                CardBalanceWalletGlyph(kind: kind, color: Color(hex: 0x2A2A2C), size: 26)
            }
            .frame(width: Metrics.tabCircle, height: Metrics.tabCircle)
            .scaleEffect(pressedTarget == target ? CardBalanceWalletMotion.pressScale : 1)
            .offset(
                x: Metrics.tabCircleXs[index] - Metrics.tabTrackX - Metrics.tabCircle / 2,
                y: (Metrics.tabTrackHeight - Metrics.tabCircle) / 2
            )
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(label)
            .cardBalanceWalletAction(target.action, onAction: onAction)
        }
    }
}

#Preview {
    CardBalanceWallet(autoPlay: true)
}
