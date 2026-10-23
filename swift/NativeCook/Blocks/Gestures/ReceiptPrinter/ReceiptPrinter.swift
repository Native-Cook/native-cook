import Observation
import SwiftUI
import UIKit

struct ReceiptPrinter: View {
    /// LOOP_MS / 1000; literal required by scripts/catalog.mjs.
    static let loop: Double = 10

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var model: ReceiptPrinterModel

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _model = State(initialValue: ReceiptPrinterModel(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )

            ReceiptPrinterStageView(
                model: model,
                size: size,
                safeTop: insets.top,
                autoPlay: autoPlay
            )
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(ReceiptPrinterTokens.COLOR.bg.ignoresSafeArea())
        .preferredColorScheme(.light)
        .statusBarHidden(model.state.stage != .grid && model.state.stage != .menu)
        .transaction { transaction in
            transaction.animation = nil
        }
        .onChange(of: reduceMotion) { _, reduced in
            model.configure(autoPlay: autoPlay, reducedMotion: reduced)
        }
        .onChange(of: autoPlay) { _, enabled in
            model.configure(autoPlay: enabled, reducedMotion: reduceMotion)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                model.startClockIfNeeded()
            } else {
                model.stopClock()
            }
        }
        .onDisappear { model.stopClock() }
    }
}

private enum ReceiptPrinterCoreMetrics {
    static let sourceTop = 62.0
    static let stageWidth = 402.0
    static let paperWidth = ReceiptPrinterTokens.SLOT.paperX1 - ReceiptPrinterTokens.SLOT.paperX0
    static let paperHeight = ReceiptPrinterTokens.RECEIPT.length + ReceiptPrinterTokens.RECEIPT.edge.depth
    static let paperShadowHeight = ReceiptPrinterTokens.RECEIPT.length - ReceiptPrinterTokens.RECEIPT.edge.depth
    static let paperShadowRadius = 14.0
    static let paperShadowY = 4.0
    static let slotShadowOpacity = 0.22
    static let morphForwardFade = 0.30
    static let morphBackStartFade = 0.15
    static let morphBackEndFade = 0.25
    static let morphColorStart = 0.55
    static let morphColorEnd = 0.95
    static let menuStartScale = 0.95
    static let menuShadowOpacity = 0.14
    static let menuShadowRadius = 22.0
    static let menuShadowY = 10.0
    static let menuVerticalPadding = 8.0
    static let menuIconSize = 15.0
    static let menuHighlightOpacity = 0.06
    static let menuRuleOpacity = 0.08
    static let ledPrintMinimum = 0.40
    static let ledPrintRange = 0.60
    static let ledGlowArmed = 0.35
    static let ledGlowPrinting = 0.35
    static let ledGlowIdle = 0.12
    static let ledGlowShadowOpacity = 0.90
    static let ledGlowShadowRadius = 6.0
    static let indicatorWidth = 3.0
    static let indicatorRight = 3.0
    static let indicatorMinimumHeight = 36.0
    static let indicatorContentPadding = 120.0
    static let buttonShadowOpacity = 0.08
    static let buttonShadowRadius = 10.0
    static let buttonShadowY = 3.0
    static let accessibilityScrollStep = 180.0
}

private struct ReceiptPrinterStageView: View {
    let model: ReceiptPrinterModel
    let size: CGSize
    let safeTop: Double
    let autoPlay: Bool

    private let t = ReceiptPrinterTokens.self

    private var state: ReceiptPrinterViewState { model.state }
    private var shift: Double { safeTop - ReceiptPrinterCoreMetrics.sourceTop }
    private var islandTop: Double { safeTop >= 51 ? t.ISLAND.top : safeTop + t.ISLAND.noIslandGap }
    private var paperTop: Double { islandTop + t.ISLAND.openH - t.ISLAND.tuck }

    var body: some View {
        ZStack(alignment: .topLeading) {
            t.COLOR.bg

            ReceiptPrinterGrid(
                top: shift,
                pressed: state.pressed,
                lift: state.lift,
                reducedMotion: state.reducedMotion,
                onPress: { model.printReceipt(scripted: false, tile: $0) },
                onLongPress: { model.openMenu($0, scripted: false) },
                onTouch: model.noteUserAction
            )
            .opacity(state.gridIn)
            .allowsHitTesting(state.stage == .grid)

            if state.stage == .menu {
                Button {
                    model.closeMenu()
                } label: {
                    Color.clear
                }
                .buttonStyle(.plain)
                .frame(width: size.width, height: size.height)
                .accessibilityLabel("Close menu")
            }

            ReceiptPrinterMenu(
                state: state,
                screenWidth: size.width,
                shift: shift,
                onChoose: { model.printReceipt(scripted: false) }
            )
            .allowsHitTesting(state.stage == .menu)

            ReceiptPrinterMorph(state: state, screenWidth: size.width, islandTop: islandTop, shift: shift)
            ReceiptPrinterPaperLayer(state: state, paperTop: paperTop, screenHeight: size.height)
            ReceiptPrinterPaperOverlay(state: state, paperTop: paperTop)
            ReceiptPrinterIsland(state: state, screenWidth: size.width, islandTop: islandTop)
            ReceiptPrinterIndicator(
                state: state,
                model: model,
                width: size.width,
                paperTop: paperTop,
                shift: shift
            )

            if state.stage == .printing || state.stage == .reading {
                ReceiptPrinterPaperTouchView(model: model)
                    .frame(
                        width: ReceiptPrinterCoreMetrics.paperWidth,
                        height: max(0, size.height - paperTop)
                    )
                    .offset(x: t.SLOT.paperX0, y: paperTop)
            }

            ReceiptPrinterActionBar(
                state: state,
                shift: shift,
                onSave: model.noteUserAction,
                onTear: { model.retract(scripted: false) },
                onSignature: model.noteUserAction
            )
            .allowsHitTesting(state.stage == .reading)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .contentShape(Rectangle())
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in model.touchBegan() }
                .onEnded { _ in model.touchEnded() }
        )
        .onAppear {
            ReceiptPrinterAssets.registerFonts()
            ReceiptPrinterAssets.assertAllLoaded()
            model.layout(height: size.height, safeTop: safeTop)
            model.configure(autoPlay: autoPlay, reducedMotion: reduceMotion)
            model.startClockIfNeeded()
        }
        .onChange(of: size) { _, newSize in
            model.layout(height: newSize.height, safeTop: safeTop)
        }
        .onChange(of: safeTop) { _, newSafeTop in
            model.layout(height: size.height, safeTop: newSafeTop)
        }
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

}

private struct ReceiptPrinterMenu: View {
    let state: ReceiptPrinterViewState
    let screenWidth: Double
    let shift: Double
    let onChoose: () -> Void

    @Environment(\.displayScale) private var displayScale
    private let t = ReceiptPrinterTokens.self

    var body: some View {
        let menu = t.GRID.menu
        let index = max(0, state.pressed)
        let cardX = t.GRID.gutter + Double(index % 3) * (t.GRID.cardW + t.GRID.gap)
        let x = min(cardX, screenWidth - t.GRID.gutter - menu.w)
        let anchorX = min(1, max(0, (cardX - x) / menu.w))
        let scale = state.reducedMotion
            ? 1
            : ReceiptPrinterCoreMetrics.menuStartScale
                + (1 - ReceiptPrinterCoreMetrics.menuStartScale) * state.menuIn

        VStack(spacing: 0) {
            ReceiptPrinterMenuButton(icon: "doc.plaintext", label: t.COPY.long, onChoose: onChoose)
                .overlay(alignment: .bottom) {
                    Rectangle()
                        .fill(t.COLOR.shadow.opacity(ReceiptPrinterCoreMetrics.menuRuleOpacity))
                        .frame(height: 1 / displayScale)
                }
            ReceiptPrinterMenuButton(icon: "text.alignleft", label: t.COPY.short, onChoose: onChoose)
        }
        .padding(.vertical, ReceiptPrinterCoreMetrics.menuVerticalPadding)
        .frame(width: menu.w, height: menu.h, alignment: .top)
        .background(t.COLOR.menu, in: RoundedRectangle(cornerRadius: menu.radius, style: .circular))
        .shadow(
            color: t.COLOR.shadow.opacity(ReceiptPrinterCoreMetrics.menuShadowOpacity),
            radius: ReceiptPrinterCoreMetrics.menuShadowRadius,
            x: 0,
            y: ReceiptPrinterCoreMetrics.menuShadowY
        )
        .scaleEffect(scale, anchor: UnitPoint(x: anchorX, y: 0))
        .opacity(state.menuIn)
        .offset(
            x: x,
            y: menu.y + shift + Double(index / 3) * t.GRID.rowPitch
        )
        .accessibilityElement(children: .contain)
    }
}

private struct ReceiptPrinterMenuButton: View {
    let icon: String
    let label: String
    let onChoose: () -> Void

    private let t = ReceiptPrinterTokens.self

    var body: some View {
        Button(action: onChoose) {
            ZStack(alignment: .topLeading) {
                Image(systemName: icon)
                    .font(.system(size: ReceiptPrinterCoreMetrics.menuIconSize, weight: .medium))
                    .frame(
                        width: ReceiptPrinterCoreMetrics.menuIconSize,
                        height: t.GRID.menu.rowH
                    )
                    .offset(x: t.GRID.menu.iconX - ReceiptPrinterCoreMetrics.menuIconSize / 2)
                Text(label)
                    .font(ReceiptPrinterAssets.font(size: t.TYPE.menu, weight: t.FONT.regular))
                    .foregroundStyle(t.COLOR.ink)
                    .frame(height: t.GRID.menu.rowH)
                    .offset(x: t.GRID.menu.textX)
            }
            .frame(width: t.GRID.menu.w, height: t.GRID.menu.rowH, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(ReceiptPrinterMenuButtonStyle())
        .accessibilityLabel(label)
    }
}

private struct ReceiptPrinterMenuButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                ReceiptPrinterTokens.COLOR.shadow.opacity(
                    configuration.isPressed ? ReceiptPrinterCoreMetrics.menuHighlightOpacity : 0
                )
            )
    }
}

private struct ReceiptPrinterPaperOverlay: View {
    let state: ReceiptPrinterViewState
    let paperTop: Double

    private let t = ReceiptPrinterTokens.self

    private var islandOpacity: Double { min(1, max(0, state.slotIn)) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(
                colors: [t.COLOR.develop, .clear],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: ReceiptPrinterCoreMetrics.paperWidth, height: ReceiptPrinterMotion.UPGRADE.developH)
            .offset(x: t.SLOT.paperX0, y: paperTop + t.ISLAND.tuck)
            .opacity(state.develop * ReceiptPrinterMotion.UPGRADE.developOpacity)

            LinearGradient(
                colors: [t.COLOR.shadow.opacity(ReceiptPrinterCoreMetrics.slotShadowOpacity), .clear],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: ReceiptPrinterCoreMetrics.paperWidth, height: t.SLOT.shadowH)
            .offset(x: t.SLOT.paperX0, y: paperTop + t.ISLAND.tuck)
            .opacity(state.fed > 1 ? islandOpacity : 0)
        }
        .opacity(islandOpacity)
        .allowsHitTesting(false)
    }
}

private struct ReceiptPrinterIsland: View {
    let state: ReceiptPrinterViewState
    let screenWidth: Double
    let islandTop: Double

    private let t = ReceiptPrinterTokens.self

    private var springAmount: Double { max(0, state.slotIn) }
    private var islandWidth: Double { t.ISLAND.w + (t.ISLAND.openW - t.ISLAND.w) * springAmount }
    private var islandHeight: Double { t.ISLAND.h + (t.ISLAND.openH - t.ISLAND.h) * springAmount }
    private var ledReveal: Double { min(1, max(0, (springAmount - 0.6) / 0.4)) }

    private var ledAmount: Double {
        if state.armed { return ReceiptPrinterMotion.POLISH.armedLed }
        guard state.stage == .printing else { return ReceiptPrinterMotion.UPGRADE.ledIdle }
        let blink = 0.5 + 0.5 * sin(state.clock * ReceiptPrinterMotion.UPGRADE.ledBlinkHz * 2 * .pi)
        return ReceiptPrinterCoreMetrics.ledPrintMinimum + ReceiptPrinterCoreMetrics.ledPrintRange * blink
    }

    private var glowAmount: Double {
        if state.armed { return ReceiptPrinterCoreMetrics.ledGlowArmed }
        if state.stage == .printing { return ReceiptPrinterCoreMetrics.ledGlowPrinting * ledAmount }
        return ReceiptPrinterCoreMetrics.ledGlowIdle
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            Capsule(style: .circular)
                .fill(t.ISLAND.color)
                .frame(width: islandWidth, height: islandHeight)
                .offset(x: screenWidth / 2 - islandWidth / 2, y: islandTop)

            Circle()
                .fill(t.COLOR.led)
                .frame(width: t.SLOT.led.glow * 2, height: t.SLOT.led.glow * 2)
                .shadow(
                    color: t.COLOR.led.opacity(ReceiptPrinterCoreMetrics.ledGlowShadowOpacity),
                    radius: ReceiptPrinterCoreMetrics.ledGlowShadowRadius
                )
                .opacity(glowAmount * ledReveal)
                .offset(
                    x: screenWidth / 2 + islandWidth / 2 - t.ISLAND.ledInset - t.SLOT.led.glow,
                    y: islandTop + islandHeight / 2 - t.SLOT.led.glow
                )

            Circle()
                .fill(t.COLOR.led)
                .frame(width: t.SLOT.led.r * 2, height: t.SLOT.led.r * 2)
                .opacity(ledAmount * ledReveal)
                .offset(
                    x: screenWidth / 2 + islandWidth / 2 - t.ISLAND.ledInset - t.SLOT.led.r,
                    y: islandTop + islandHeight / 2 - t.SLOT.led.r
                )
        }
        .allowsHitTesting(false)
    }
}

private struct ReceiptPrinterMorph: View {
    let state: ReceiptPrinterViewState
    let screenWidth: Double
    let islandTop: Double
    let shift: Double

    private let t = ReceiptPrinterTokens.self

    var body: some View {
        let index = max(0, state.morphFrom)
        let progress = state.morph
        let cardCenterX = t.GRID.gutter + Double(index % 3) * (t.GRID.cardW + t.GRID.gap) + t.GRID.cardW / 2
        let cardCenterY = shift + t.GRID.rowTop + Double(index / 3) * t.GRID.rowPitch + t.GRID.cardH / 2
        let slotCenterX = screenWidth / 2
        let slotCenterY = islandTop + t.ISLAND.openH / 2
        let scaleX = 1 + (t.ISLAND.openW / t.GRID.cardW - 1) * progress
        let scaleY = 1 + (t.ISLAND.openH / t.GRID.cardH - 1) * progress
        let centerX = cardCenterX + (slotCenterX - cardCenterX) * progress
        let centerY = cardCenterY + (slotCenterY - cardCenterY) * progress
        let opacity = morphOpacity(progress)
        let color = morphColor(progress)

        RoundedRectangle(
            cornerRadius: t.GRID.radius + (t.ISLAND.openH / 2 - t.GRID.radius) * progress,
            style: .circular
        )
        .fill(color)
        .frame(width: t.GRID.cardW, height: t.GRID.cardH)
        .scaleEffect(x: scaleX, y: scaleY, anchor: .center)
        .offset(x: centerX - t.GRID.cardW / 2, y: centerY - t.GRID.cardH / 2)
        .opacity(opacity)
        .allowsHitTesting(false)
    }

    private func morphOpacity(_ progress: Double) -> Double {
        guard progress > 0, progress < 1 else { return 0 }
        if state.morphBack {
            return min(
                1,
                (1 - progress) / ReceiptPrinterCoreMetrics.morphBackStartFade,
                progress / ReceiptPrinterCoreMetrics.morphBackEndFade
            )
        }
        return min(1, (1 - progress) / ReceiptPrinterCoreMetrics.morphForwardFade)
    }

    private func morphColor(_ progress: Double) -> Color {
        let unit = state.morphBack
            ? min(1, max(0, (progress - 0.85) / 0.15))
            : min(
                1,
                max(
                    0,
                    (progress - ReceiptPrinterCoreMetrics.morphColorStart)
                        / (ReceiptPrinterCoreMetrics.morphColorEnd - ReceiptPrinterCoreMetrics.morphColorStart)
                )
            )
        let start = state.morphBack ? 229.0 / 255.0 : 1.0
        let end = 0.0
        let gamma = 2.2
        let channel = pow((1 - unit) * pow(start, gamma) + unit * pow(end, gamma), 1 / gamma)
        return Color(.sRGB, red: channel, green: channel, blue: channel, opacity: 1)
    }
}

private struct ReceiptPrinterPaperLayer: View {
    let state: ReceiptPrinterViewState
    let paperTop: Double
    let screenHeight: Double

    private let t = ReceiptPrinterTokens.self

    private var paperRotation: Double {
        if state.stage == .printing, !state.reducedMotion {
            let steps = Double(ReceiptPrinterMotion.FEED.steps)
            let position = state.feed * steps
            let since = (position - floor(position)) * (ReceiptPrinterMotion.FEED.ms / steps) / 1_000
            return ReceiptPrinterMotion.FEED.swing
                * exp(-ReceiptPrinterMotion.FEED.swingDamping * since)
                * sin(ReceiptPrinterMotion.FEED.swingFreq * 2 * .pi * since)
        }
        if state.stage == .reading, !state.reducedMotion {
            return ReceiptPrinterMotion.UPGRADE.swayDeg
                * sin(state.clock / (ReceiptPrinterMotion.UPGRADE.swayMs / 1_000) * 2 * .pi)
        }
        return 0
    }

    var body: some View {
        let outerHeight = max(0, screenHeight - paperTop)
        let shadowY = min(state.fed, t.RECEIPT.length) - t.RECEIPT.length - state.scroll
        let shadowOpacity = ReceiptPrinterMotion.UPGRADE.liftShadow[0]
            + (ReceiptPrinterMotion.UPGRADE.liftShadow[1] - ReceiptPrinterMotion.UPGRADE.liftShadow[0]) * state.held
        let cutOffset = state.cut * ReceiptPrinterMotion.UPGRADE.cutDrop

        ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(t.COLOR.paper)
                .frame(
                    width: ReceiptPrinterCoreMetrics.paperWidth,
                    height: ReceiptPrinterCoreMetrics.paperShadowHeight
                )
                .shadow(
                    color: t.COLOR.shadow.opacity(shadowOpacity),
                    radius: ReceiptPrinterCoreMetrics.paperShadowRadius,
                    x: 0,
                    y: ReceiptPrinterCoreMetrics.paperShadowY
                )
                .opacity(state.fed > 1 ? 1 : 0)
                .offset(x: t.SLOT.paperX0, y: shadowY)

            ReceiptPrinterPaper()
                .rotationEffect(.degrees(paperRotation))
                .offset(y: -state.scroll + cutOffset)
                .frame(
                    width: ReceiptPrinterCoreMetrics.paperWidth,
                    height: max(0, state.fed),
                    alignment: .top
                )
                .clipped()
                .offset(x: t.SLOT.paperX0)
        }
        .frame(width: ReceiptPrinterCoreMetrics.stageWidth, height: outerHeight, alignment: .topLeading)
        .clipped()
        .offset(y: paperTop)
        .allowsHitTesting(false)
    }
}

private struct ReceiptPrinterIndicator: View {
    let state: ReceiptPrinterViewState
    let model: ReceiptPrinterModel
    let width: Double
    let paperTop: Double
    let shift: Double

    private let t = ReceiptPrinterTokens.self

    var body: some View {
        let available = t.BAR.top + shift - t.SPACE.sm - (paperTop + t.SPACE.sm)
        let height = max(
            ReceiptPrinterCoreMetrics.indicatorMinimumHeight,
            available * available / (t.RECEIPT.length + ReceiptPrinterCoreMetrics.indicatorContentPadding)
        )
        let progress = min(1, max(0, state.scroll / max(1, model.maxScroll)))
        let y = paperTop + t.SPACE.sm + progress * (available - height)

        Capsule()
            .fill(t.COLOR.indicator)
            .frame(width: ReceiptPrinterCoreMetrics.indicatorWidth, height: height)
            .offset(
                x: width - ReceiptPrinterCoreMetrics.indicatorRight - ReceiptPrinterCoreMetrics.indicatorWidth,
                y: y
            )
            .opacity(state.indicatorIn)
            .allowsHitTesting(false)
    }
}

private struct ReceiptPrinterActionBar: View {
    let state: ReceiptPrinterViewState
    let shift: Double
    let onSave: () -> Void
    let onTear: () -> Void
    let onSignature: () -> Void

    private let t = ReceiptPrinterTokens.self

    var body: some View {
        let eased = 1 - pow(1 - state.barIn, 3)
        HStack(spacing: t.BAR.gap) {
            ReceiptPrinterBarButton(
                icon: "tray.and.arrow.down",
                label: t.COPY.save,
                dark: true,
                action: onSave
            )
            ReceiptPrinterBarButton(
                icon: "list.bullet.rectangle",
                label: t.COPY.tear,
                dark: false,
                action: onTear
            )
            ReceiptPrinterBarButton(
                icon: "signature",
                label: t.COPY.sign,
                dark: false,
                action: onSignature
            )
        }
        .offset(
            x: t.BAR.x,
            y: t.BAR.top + shift + (1 - eased) * t.SPACE.lg
        )
        .opacity(eased)
    }
}

private struct ReceiptPrinterBarButton: View {
    let icon: String
    let label: String
    let dark: Bool
    let action: () -> Void

    private let t = ReceiptPrinterTokens.self

    var body: some View {
        let ink = dark ? t.COLOR.tagInk : t.COLOR.buttonInk
        Button(action: action) {
            ZStack(alignment: .top) {
                Image(systemName: icon)
                    .font(.system(size: t.BAR.icon, weight: .bold))
                    .foregroundStyle(ink)
                    .frame(width: t.BAR.icon, height: t.BAR.icon)
                    .offset(y: t.BAR.iconCap)
                Text(label)
                    .font(ReceiptPrinterAssets.font(size: t.TYPE.button, weight: t.FONT.bold))
                    .foregroundStyle(ink)
                    .offset(y: t.BAR.labelCap - t.TYPE.button * t.CAP_TOP)
            }
            .frame(width: t.BAR.w, height: t.BAR.h, alignment: .top)
            .background(
                dark ? AnyShapeStyle(t.COLOR.buttonDark) : AnyShapeStyle(t.COLOR.buttonLight),
                in: Capsule()
            )
            .shadow(
                color: t.COLOR.shadow.opacity(dark ? 0 : ReceiptPrinterCoreMetrics.buttonShadowOpacity),
                radius: dark ? 0 : ReceiptPrinterCoreMetrics.buttonShadowRadius,
                x: 0,
                y: dark ? 0 : ReceiptPrinterCoreMetrics.buttonShadowY
            )
        }
        .buttonStyle(ReceiptPrinterBarButtonStyle())
        .frame(width: t.BAR.w, height: t.BAR.h)
        .accessibilityLabel(label)
    }
}

private struct ReceiptPrinterBarButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? ReceiptPrinterMotion.POLISH.pressScale : 1)
            .animation(
                .timingCurve(
                    ReceiptPrinterMotion.POLISH.easeOut[0],
                    ReceiptPrinterMotion.POLISH.easeOut[1],
                    ReceiptPrinterMotion.POLISH.easeOut[2],
                    ReceiptPrinterMotion.POLISH.easeOut[3],
                    duration: (configuration.isPressed
                        ? ReceiptPrinterMotion.POLISH.pressInMs
                        : ReceiptPrinterMotion.POLISH.pressOutMs) / 1_000
                ),
                value: configuration.isPressed
            )
    }
}

private struct ReceiptPrinterPaperTouchView: UIViewRepresentable {
    let model: ReceiptPrinterModel

    func makeUIView(context: Context) -> ReceiptPrinterPaperTouchSurface {
        ReceiptPrinterPaperTouchSurface(model: model)
    }

    func updateUIView(_ view: ReceiptPrinterPaperTouchSurface, context: Context) {
        view.model = model
    }
}

@MainActor
private final class ReceiptPrinterPaperTouchSurface: UIView, UIGestureRecognizerDelegate {
    var model: ReceiptPrinterModel
    private var previousPoint = CGPoint.zero
    private var previousTime = 0.0
    private var velocityY = 0.0
    private var origin = CGPoint.zero
    private let velocityPan = UIPanGestureRecognizer()

    init(model: ReceiptPrinterModel) {
        self.model = model
        super.init(frame: .zero)
        backgroundColor = .clear
        isAccessibilityElement = true
        accessibilityTraits = .adjustable
        accessibilityLabel = "Run receipt"
        accessibilityHint = "Swipe vertically to read the receipt. Escape tears it off."

        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.numberOfTouchesRequired = 1
        recognizer.cancelsTouchesInView = false
        recognizer.delaysTouchesBegan = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)

        velocityPan.addTarget(self, action: #selector(trackPan(_:)))
        velocityPan.cancelsTouchesInView = false
        velocityPan.delaysTouchesBegan = false
        velocityPan.delegate = self
        addGestureRecognizer(velocityPan)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool { true }

    override func accessibilityIncrement() {
        model.accessibilityScroll(by: ReceiptPrinterCoreMetrics.accessibilityScrollStep)
    }

    override func accessibilityDecrement() {
        model.accessibilityScroll(by: -ReceiptPrinterCoreMetrics.accessibilityScrollStep)
    }

    override func accessibilityPerformEscape() -> Bool {
        model.retract(scripted: false)
        return true
    }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let time = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            origin = point
            previousPoint = point
            previousTime = time
            velocityY = 0
            model.touchBegan()
            model.dragBegan(scripted: false)
        case .changed:
            let delta = max(1.0 / 240.0, time - previousTime)
            let weight = 1 - exp(-delta / 0.035)
            velocityY += ((point.y - previousPoint.y) / delta - velocityY) * weight
            previousPoint = point
            previousTime = time
            model.touchMoved()
            model.dragMoved(translationY: point.y - origin.y)
        case .ended, .cancelled, .failed:
            if time - previousTime > 0.1 { velocityY = 0 }
            let systemVelocity = velocityPan.velocity(in: self).y
            let panRecognized = velocityPan.state == .began
                || velocityPan.state == .changed
                || velocityPan.state == .ended
            let releaseVelocity = panRecognized && systemVelocity.isFinite ? systemVelocity : velocityY
            model.dragEnded(velocityY: Double(releaseVelocity))
            model.touchEnded()
        default:
            break
        }
    }

    @objc private func trackPan(_ recognizer: UIPanGestureRecognizer) {
        // The zero-delay long press owns the drag so it survives the gallery's swipe recognizer.
        // UIPanGestureRecognizer is attached only for UIKit's release-velocity estimate.
    }
}
