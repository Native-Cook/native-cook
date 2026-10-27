import SwiftUI

/*
 Confetti Gift Offer: a paywall story with an original raccoon, Miso. "We have a gift! Just for you!" over a
 checked box whose striped tail knocks the lid askew and an Open now button; the box opens into a
 "Limited time -70% offer" ticket in a burst of capsule confetti; closing it raises an "Are you sure to skip
 -70% offer?" sheet, and either way the story ends on "Now let's create account" with Apple and Google buttons.
 Prices are illustrative: nothing is charged and no sign-in is made.

 autoPlay (loop = 9.5 s), t in seconds:
   0.00-0.33  the gift at rest.
   0.33-0.58  it rocks, squats and kicks its lid up.
   0.58-1.10  the ticket grows from 64% and fades in; the first wave of confetti falls (0.58-2.28).
   1.10-3.17  the offer; its countdown starts at 59:59 and ticks once a second.
   3.17-3.43  the skip sheet rises over a light scrim (measured positions).
   4.05-4.90  a second wave of confetti.
   4.50-4.90  the account screen pushes in from the right, carrying the offer and the sheet out together.
   4.90-8.40  the account screen, its sparkles twinkling.
   8.40-8.75  it fades; 8.75-9.10 the gift comes back; at rest again.

 Touch: Open now plays the opening, the close chip raises the sheet (drag its handle down, or press the scrim, to
 skip), Claim your offer and Skip push the account screen in, and a sign-in button ends on "You're in.". Touching
 takes over from the loop; 2 s after the last touch from the gift or the offer it resumes. Reduce Motion shows the
 settled offer: no travel, no confetti, no loop.

 Every frame is a function of the loop time (ConfettiGiftOfferController.frame), so `previewTime` freezes any
 frame. The numbers are those of the React Native block.
*/
struct ConfettiGiftOffer: View {
    /// The loop's period in seconds (`LOOP`).
    static let loop: Double = 9.5

    var autoPlay = false
    /// Freezes the loop at this time (seconds) and turns touch off; used to look at one frame.
    var previewTime: Double?
    /// Called when a sign-in button ends the story: which one, and whether the offer was claimed.
    var onContinue: ((ConfettiGiftOfferProvider, Bool) -> Void)?
    /// Part of the React Native props; the story never dismisses itself.
    var onDismiss: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var controller = ConfettiGiftOfferController()

    init(
        autoPlay: Bool = false,
        previewTime: Double? = nil,
        onContinue: ((ConfettiGiftOfferProvider, Bool) -> Void)? = nil,
        onDismiss: (() -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onContinue = onContinue
        self.onDismiss = onDismiss
        _ = ConfettiGiftOfferAssets.registered
    }

    var body: some View {
        GeometryReader { outer in
            let insets = outer.safeAreaInsets
            GeometryReader { full in
                let metrics = ConfettiGiftOfferMetrics(
                    width: full.size.width,
                    height: full.size.height,
                    top: insets.top,
                    bottom: insets.bottom,
                    fontScale: ConfettiGiftOfferMetrics.fontScale(for: dynamicTypeSize)
                )
                ConfettiGiftOfferScreen(
                    controller: controller,
                    config: ConfettiGiftOfferConfig(
                        autoPlay: autoPlay,
                        previewTime: previewTime.map { ConfettiGiftOfferMotion.normalize($0, period: Self.loop) },
                        reduced: reduceMotion,
                        metrics: metrics,
                        onContinue: onContinue
                    )
                )
                .environment(\.confettiGiftOfferFontScale, metrics.fontScale)
            }
            .ignoresSafeArea()
        }
        .background(ConfettiGiftOfferPalette.screen.ignoresSafeArea())
        // The reference's status bar is dark on every screen of the story.
        .preferredColorScheme(.light)
        .onAppear { controller.setForeground(scenePhase == .active) }
        .onChange(of: scenePhase) { _, phase in controller.setForeground(phase == .active) }
        .onDisappear { controller.stop() }
    }
}

/// The screen itself: one timeline, one frame of the controller, every layer of the story.
private struct ConfettiGiftOfferScreen: View {
    let controller: ConfettiGiftOfferController
    let config: ConfettiGiftOfferConfig

    var body: some View {
        let wake = controller.wake
        TimelineView(.animation(paused: !wake.needsFrames)) { timeline in
            let frame = controller.frame(at: timeline.date, config: config)
            layers(frame)
        }
    }

    private func layers(_ f: ConfettiGiftOfferFrame) -> some View {
        let m = config.metrics
        let context = ConfettiGiftOfferContext(controller: controller, metrics: m, reduced: config.reduced)
        return ZStack(alignment: .topLeading) {
            ConfettiGiftOfferPalette.screen

            // offerWashStyle: a white wash under the offer, moving with it.
            Color.white
                .opacity(f.washOpacity)
                .offset(x: f.shift)
                .allowsHitTesting(false)
                .accessibilityHidden(true)

            ConfettiGiftOfferGiftScene(context: context, openProgress: f.openProgress, active: f.stage == .gift)
                .opacity(f.giftOpacity)
                .allowsHitTesting(f.stage == .gift)
                .accessibilityHidden(f.stage != .gift)

            ConfettiGiftOfferOfferScene(context: context, countdown: f.countdown, active: f.stage == .offer)
                .opacity(f.offerOpacity)
                .scaleEffect(f.offerScale)
                .offset(x: f.shift)
                .allowsHitTesting(f.stage == .offer)
                .accessibilityHidden(f.stage != .offer)

            signup(f, context)

            if f.stage == .complete || f.stage == .dismissed {
                ConfettiGiftOfferFinishScene(context: context, stage: f.stage)
            }

            ConfettiGiftOfferConfetti(age: f.confettiAge, reduced: config.reduced)
                .offset(x: f.shift)

            closeChip(f, context)

            if f.sheet.visible {
                ConfettiGiftOfferSheet(context: context, sheet: f.sheet)
            }
        }
        .frame(width: m.width, height: m.height, alignment: .topLeading)
        .clipped()
        .allowsHitTesting(config.previewTime == nil)
    }

    private func signup(_ f: ConfettiGiftOfferFrame, _ context: ConfettiGiftOfferContext) -> some View {
        ZStack {
            // The account screen's own pale gradient (#D9E3FB, #E2E5F7, #EAE9F0).
            LinearGradient(
                stops: [
                    .init(color: ConfettiGiftOfferPalette.hex(0xD9E3FB), location: 0),
                    .init(color: ConfettiGiftOfferPalette.hex(0xE2E5F7), location: 0.5),
                    .init(color: ConfettiGiftOfferPalette.hex(0xEAE9F0), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .allowsHitTesting(false)
            ConfettiGiftOfferSignupScene(context: context, time: f.time, active: f.stage == .signup)
        }
        .frame(width: context.metrics.width, height: context.metrics.height)
        .opacity(f.signupOpacity)
        .offset(x: f.signupShift)
        .allowsHitTesting(f.stage == .signup)
        .accessibilityHidden(f.stage != .signup)
    }

    /// The offer's close chip (tsx:253): it shares the offer's opacity, scale and push.
    private func closeChip(_ f: ConfettiGiftOfferFrame, _ context: ConfettiGiftOfferContext) -> some View {
        let m = context.metrics
        return Button(action: context.controller.leave) {
            ZStack {
                Circle().fill(ConfettiGiftOfferPalette.hex(0xF3F3F3))
                ConfettiGiftOfferCloseIcon()
            }
            .frame(width: 44, height: 44)
            // hitSlop 4.
            .contentShape(Rectangle().inset(by: -4))
        }
        .buttonStyle(ConfettiGiftOfferPressStyle(reduced: config.reduced, controller: context.controller))
        .accessibilityLabel("Leave the offer")
        .opacity(f.offerOpacity)
        .scaleEffect(f.offerScale)
        .offset(x: f.shift)
        .allowsHitTesting(f.stage == .offer)
        .accessibilityHidden(f.stage != .offer)
        .padding(.leading, 18)
        .padding(.top, m.largeText ? m.top + 8 : 62 * m.u)
    }
}
