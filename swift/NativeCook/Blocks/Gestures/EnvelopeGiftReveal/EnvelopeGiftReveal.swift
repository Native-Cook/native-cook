import SwiftUI

/// Envelope Gift Reveal: swipe up on the envelope (or press Open) and the flap folds back, the card follows the
/// finger out, flies into place and the page turns from lime to white under confetti.
struct EnvelopeGiftReveal: View {
    /// One autoplay period in seconds: the scripted finger's whole round, ending where it began (LOOP_MS 8000 / 1000;
    /// the catalog check reads this line, so it stays a plain number).
    static let loop: Double = 8

    var autoPlay = false
    var onShop: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduced

    var body: some View {
        // The block draws under the system bars and keeps its copy clear of them itself.
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            // Nothing to lay out until the screen is measured (a zero first pass gives negative sizes).
            if size.width > 0 && size.height > 0 {
                EnvelopeGiftRevealScreen(
                    size: size, safeTop: insets.top, safeBottom: insets.bottom,
                    autoPlay: autoPlay, reduced: reduced, onShop: onShop
                )
                // A new size or a change of Reduce Motion starts the screen over, as the source's `key` does.
                .id("\(Int(size.width))x\(Int(size.height))-\(reduced)")
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(EnvelopeGiftRevealColors.lime.ignoresSafeArea())
        // Dark status bar content on the lime and the white page, whatever the system's setting.
        .preferredColorScheme(.light)
    }
}

private struct EnvelopeGiftRevealScreen: View {
    private typealias M = EnvelopeGiftRevealMetrics
    private typealias C = EnvelopeGiftRevealColors

    let size: CGSize
    let autoPlay: Bool
    let reduced: Bool
    let onShop: (() -> Void)?
    private let lay: EnvelopeGiftRevealScreenLayout

    @State private var engine: EnvelopeGiftRevealEngine
    @State private var tilt = EnvelopeGiftRevealTilt()
    @State private var haptics = EnvelopeGiftRevealHaptics()
    /// True while nothing moves (Reduce Motion, no autoplay, resting): the clock stops until the next touch.
    @State private var asleep = false

    init(size: CGSize, safeTop: Double, safeBottom: Double, autoPlay: Bool, reduced: Bool, onShop: (() -> Void)?) {
        self.size = size
        self.autoPlay = autoPlay
        self.reduced = reduced
        self.onShop = onShop
        let layout = EnvelopeGiftRevealScreenLayout(height: size.height, top: safeTop, bottom: safeBottom)
        lay = layout
        _engine = State(initialValue: EnvelopeGiftRevealEngine(
            width: size.width, centreY: layout.cardTop + M.cardH / 2, envTop: layout.envTop, autoPlay: autoPlay, reduced: reduced
        ))
    }

    var body: some View {
        TimelineView(.animation(paused: asleep)) { timeline in
            content(engine.frame())
                // The display-link advances after the render pass, never while `body` is constructing its view.
                // Clock fields are deliberately not observed, so the next TimelineView tick consumes this update.
                .onChange(of: timeline.date, initial: true) { _, date in
                    engine.advance(to: date)
                }
        }
        .onAppear {
            engine.onHaptic = { kind in haptics.play(kind) }
            // Reported from inside a frame, so the state changes on the next turn of the run loop, not during the update.
            engine.onIdleChange = { idle in DispatchQueue.main.async { asleep = idle } }
            tilt.start()
        }
        .onDisappear { tilt.stop() }
    }

    private func content(_ frame: EnvelopeGiftRevealFrame) -> some View {
        let pose = EnvelopeGiftRevealPose(frame: frame, width: size.width, height: size.height, lay: lay, reduced: reduced, roll: tilt.roll)
        let painter = EnvelopeGiftRevealPainter(width: size.width, height: size.height, lay: lay, pose: pose, confetti: engine.confetti)
        return ZStack(alignment: .topLeading) {
            C.lime
            EnvelopeGiftRevealTouchSurface(engine: engine)
            EnvelopeGiftRevealScene(painter: painter)
            envelopeElement
            headline(frame)
            openButton(frame)
            title
            message
            shopButton
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    // MARK: Copy

    /// Headline letters leave one after another, sliding down as the flap starts to lift (wide type, staggered).
    private func headline(_ frame: EnvelopeGiftRevealFrame) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(EnvelopeGiftRevealCopy.headlineLines.enumerated()), id: \.offset) { row, line in
                HStack(spacing: 0) {
                    ForEach(Array(line.enumerated()), id: \.offset) { index, character in
                        letter(String(character), order: row * EnvelopeGiftRevealTokens.Headline.rowOrderStride + index, frame: frame)
                    }
                }
            }
        }
        .frame(width: size.width - M.finalSide - EnvelopeGiftRevealTokens.Scene.trailingInset, alignment: .leading)
        .offset(x: M.finalSide, y: lay.headlineTop)
        .allowsHitTesting(false)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(EnvelopeGiftRevealCopy.headlineLines.joined(separator: " "))
        .accessibilityAddTraits(.isHeader)
    }

    private func letter(_ character: String, order: Int, frame: EnvelopeGiftRevealFrame) -> some View {
        let opacity: Double
        let shift: Double
        if frame.reset > 0 {
            opacity = frame.reset
            shift = 0
        } else {
            let span = EnvelopeGiftRevealHeadline.span
            let u = reduced
                ? EnvelopeGiftRevealEase.clamp01(frame.p / EnvelopeGiftRevealTokens.Headline.reducedSpan)
                : EnvelopeGiftRevealEase.clamp01((frame.p - Double(order) * EnvelopeGiftRevealHeadline.stagger) / span)
            let e = EnvelopeGiftRevealEase.smoothstep(u)
            opacity = 1 - e
            shift = EnvelopeGiftRevealHeadline.drop * e
        }
        return Text(character)
            .font(EnvelopeGiftRevealAssets.displayFont(M.headline))
            .kerning(EnvelopeGiftRevealTokens.Text.headlineTracking)
            .foregroundStyle(C.ink)
            .fixedSize()
            // A line box shorter than the font's puts the baseline one descent above the box's bottom.
            .frame(height: M.headlineLine, alignment: .bottom)
            .opacity(opacity)
            .offset(y: shift)
    }

    private var title: some View {
        let style = engine.finalStyle(0)
        return VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(EnvelopeGiftRevealCopy.titleLines.enumerated()), id: \.offset) { _, line in
                Text(line)
                    .font(EnvelopeGiftRevealAssets.displayFont(M.title))
                    .kerning(EnvelopeGiftRevealTokens.Text.titleTracking)
                    .foregroundStyle(C.ink)
                    .fixedSize()
                    .frame(height: M.titleLine, alignment: .bottom)
            }
        }
        .frame(width: size.width - M.finalSide - EnvelopeGiftRevealTokens.Scene.trailingInset, alignment: .leading)
        .offset(x: M.finalSide, y: lay.titleTop + style.shift)
        .opacity(style.opacity)
        .allowsHitTesting(false)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(EnvelopeGiftRevealCopy.titleLines.joined(separator: " "))
        .accessibilityAddTraits(.isHeader)
    }

    private var message: some View {
        let style = engine.finalStyle(1)
        return Text(EnvelopeGiftRevealCopy.message)
            .font(.system(size: EnvelopeGiftRevealTokens.Text.bodySize))
            .foregroundStyle(C.messageInk)
            .multilineTextAlignment(.center)
            .padding(.horizontal, EnvelopeGiftRevealTokens.Text.messagePadding)
            .frame(width: size.width - M.finalSide * 2, height: M.messageH)
            .background(
                RoundedRectangle(cornerRadius: EnvelopeGiftRevealTokens.Text.messageCorner)
                    .fill(C.message)
                    .shadow(
                        color: .black.opacity(EnvelopeGiftRevealTokens.Text.messageShadowAlpha),
                        radius: EnvelopeGiftRevealTokens.Text.messageShadowRadius,
                        x: 0,
                        y: EnvelopeGiftRevealTokens.Text.messageShadowY
                    )
            )
            .offset(x: M.finalSide, y: lay.messageTop + style.shift)
            .opacity(style.opacity)
            .allowsHitTesting(false)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Gift card, \(EnvelopeGiftRevealCopy.amount). \(EnvelopeGiftRevealCopy.message)")
            .accessibilityHidden(!engine.revealed)
    }

    // MARK: Buttons

    /// The envelope for VoiceOver: swipe up is a drag, so the same thing is offered as a double tap.
    private var envelopeElement: some View {
        Color.clear
            .frame(width: M.envW, height: M.envH)
            .offset(x: (size.width - M.envW) / 2, y: lay.envTop)
            .allowsHitTesting(false)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Gift envelope")
            .accessibilityHint("Swipe up to open it, or double tap")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { engine.pressOpen() }
            .accessibilityHidden(!engine.sealedOrDragging)
    }

    private func openButton(_ frame: EnvelopeGiftRevealFrame) -> some View {
        let leaving = frame.reset > 0 ? 1 - frame.reset : EnvelopeGiftRevealEase.clamp01(frame.p / EnvelopeGiftRevealTokens.Text.openLeavingProgress)
        return Button { engine.pressOpen() } label: {
            Text("OPEN")
                .font(.system(size: EnvelopeGiftRevealTokens.Text.buttonSize, weight: .bold))
                .kerning(EnvelopeGiftRevealTokens.Text.openTracking)
                .foregroundStyle(C.white)
                .frame(width: M.buttonD, height: M.buttonD)
                .background(
                    Circle()
                        .fill(C.ink)
                        .shadow(
                            color: Color(
                                hex: EnvelopeGiftRevealTokens.Text.openShadow,
                                opacity: EnvelopeGiftRevealTokens.Text.openShadowAlpha
                            ),
                            radius: EnvelopeGiftRevealTokens.Text.openShadowRadius,
                            x: 0,
                            y: EnvelopeGiftRevealTokens.Text.openShadowY
                        )
                )
        }
        .buttonStyle(EnvelopeGiftRevealPressStyle())
        .accessibilityLabel("Open the gift")
        .opacity(1 - leaving)
        .scaleEffect(1 - EnvelopeGiftRevealTokens.Text.openLeavingScale * leaving)
        .offset(x: (size.width - M.buttonD) / 2, y: lay.buttonY - M.buttonD / 2)
        .allowsHitTesting(engine.sealedOrDragging)
    }

    private var shopButton: some View {
        let style = engine.finalStyle(2)
        return Button {
            if let onShop { onShop() } else { engine.pressShop() }
        } label: {
            Text(EnvelopeGiftRevealCopy.cta.uppercased())
                .font(.system(size: EnvelopeGiftRevealTokens.Text.buttonSize, weight: .bold))
                .kerning(EnvelopeGiftRevealTokens.Text.ctaTracking)
                .foregroundStyle(C.white)
                .frame(width: size.width - M.finalSide * 2, height: M.buttonH)
                .background(C.ink)
        }
        .buttonStyle(EnvelopeGiftRevealPressStyle())
        .accessibilityLabel(EnvelopeGiftRevealCopy.cta)
        .offset(x: M.finalSide, y: lay.ctaTop + style.shift)
        .opacity(style.opacity)
        .allowsHitTesting(engine.revealed)
    }
}

/// RN's `pressStyle`: 0.96 on touch down in 100 ms, then back in 160 ms. A SwiftUI button retains its
/// standard hit area while pressed; its action remains the button's normal accessibility action.
private struct EnvelopeGiftRevealPressStyle: ButtonStyle {
    private typealias Polish = EnvelopeGiftRevealMotion.Polish

    func makeBody(configuration: Configuration) -> some View {
        return configuration.label
            .scaleEffect(configuration.isPressed ? Polish.pressScale : 1)
            .animation(
                .timingCurve(
                    Polish.easeOut.x1, Polish.easeOut.y1, Polish.easeOut.x2, Polish.easeOut.y2,
                    duration: (configuration.isPressed ? Polish.pressInMs : Polish.pressOutMs) / 1000
                ),
                value: configuration.isPressed
            )
    }
}
