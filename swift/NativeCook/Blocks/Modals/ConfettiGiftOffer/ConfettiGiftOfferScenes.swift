import SwiftUI

/// What every scene needs: the controller (touch), the window metrics and Reduce Motion.
struct ConfettiGiftOfferContext {
    let controller: ConfettiGiftOfferController
    let metrics: ConfettiGiftOfferMetrics
    let reduced: Bool
}

extension View {
    /// `place(top, height)` of ConfettiGiftOffer.tsx:213: a full-width box `top` points under the screen's top edge.
    /// Horizontal content is centred, as an absolutely placed `left: 0, right: 0` column with centred items is.
    fileprivate func confettiGiftOfferPlace(top: CGFloat, width: CGFloat, height: CGFloat? = nil) -> some View {
        frame(width: width, height: height, alignment: .top)
            .padding(.top, top)
    }
}

/// The drag callbacks of a scene's scroll view, wired to the controller (`readingEvents`, tsx:212).
private func confettiGiftOfferScroller<Content: View>(
    _ context: ConfettiGiftOfferContext,
    enabled: Bool,
    @ViewBuilder content: @escaping () -> Content
) -> some View {
    ConfettiGiftOfferScroller(
        enabled: enabled,
        onDragBegan: context.controller.readingDragBegan,
        onDragEnded: context.controller.readingDragEnded,
        onMomentumBegan: context.controller.readingMomentumBegan,
        onMomentumEnded: context.controller.readingMomentumEnded,
        content: content
    )
}

private func confettiGiftOfferHeadline(_ lines: [[ConfettiGiftOfferRun]], label: String) -> some View {
    ConfettiGiftOfferText(lines: lines, style: ConfettiGiftOfferType.headline)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isHeader)
}

// MARK: - Gift

/// "We have a gift! Just for you!" over the checked box and an Open now button (tsx:219-225).
struct ConfettiGiftOfferGiftScene: View {
    let context: ConfettiGiftOfferContext
    let openProgress: Double
    let active: Bool

    var body: some View {
        let m = context.metrics
        confettiGiftOfferScroller(context, enabled: active) {
            if m.largeText {
                flow(m)
            } else {
                fixed(m)
            }
        }
    }

    private var headline: some View {
        confettiGiftOfferHeadline(
            [[ConfettiGiftOfferRun("We have a gift!")], [ConfettiGiftOfferRun("Just for you!")]],
            label: "We have a gift! Just for you!"
        )
    }

    private var openButton: some View {
        ConfettiGiftOfferButton(
            title: "Open now",
            width: 163,
            reduced: context.reduced,
            controller: context.controller,
            action: context.controller.open
        )
    }

    private func fixed(_ m: ConfettiGiftOfferMetrics) -> some View {
        ZStack(alignment: .topLeading) {
            headline.confettiGiftOfferPlace(top: 133 * m.u, width: m.width)
            // place(338, 310): the gift is centred in a 310u box and scaled by w about its centre.
            ConfettiGiftOfferGift(open: openProgress)
                .scaleEffect(m.w)
                .frame(width: m.width, height: 310 * m.u, alignment: .center)
                .padding(.top, 338 * m.u)
            openButton.confettiGiftOfferPlace(top: 765 * m.u, width: m.width)
        }
        .frame(width: m.width, height: m.height, alignment: .topLeading)
    }

    private func flow(_ m: ConfettiGiftOfferMetrics) -> some View {
        VStack(spacing: 0) {
            headline
            ConfettiGiftOfferGift(open: openProgress)
                .scaleEffect(0.9)
            openButton
        }
        .padding(.horizontal, 24)
        .padding(.top, m.top + 64)
        .padding(.bottom, m.bottom + 24)
        .frame(width: m.width)
    }
}

// MARK: - Offer

/// The "Limited time -70% offer" screen: headline, the ticket, Miso over it and the fine print (tsx:226-238).
struct ConfettiGiftOfferOfferScene: View {
    let context: ConfettiGiftOfferContext
    let countdown: ConfettiGiftOfferCountdown
    let active: Bool

    private static let percents: [(left: CGFloat, top: CGFloat, turn: Double)] = [
        (6, 6, -18), (84, 5, 14), (20, 22, 10), (74, 24, -12), (6, 44, 16), (88, 42, -20), (30, 9, -8), (62, 40, 18),
    ]

    var body: some View {
        let m = context.metrics
        confettiGiftOfferScroller(context, enabled: active) {
            if m.largeText {
                flow(m)
            } else {
                fixed(m)
            }
        }
    }

    private var headline: some View {
        confettiGiftOfferHeadline(
            [
                [ConfettiGiftOfferRun("Limited time")],
                [ConfettiGiftOfferRun("-70%", color: ConfettiGiftOfferPalette.green), ConfettiGiftOfferRun(" offer")],
            ],
            label: "Limited time -70% offer"
        )
    }

    private var finePrint: some View {
        ConfettiGiftOfferText("First year $17.99, then $59.99/year.\nCancel anytime.", style: ConfettiGiftOfferType.finePrint)
    }

    private var mascot: some View {
        ConfettiGiftOfferMascot(pose: .hearts, size: 128)
            .allowsHitTesting(false)
    }

    private func fixed(_ m: ConfettiGiftOfferMetrics) -> some View {
        let ticketWidth = m.width - 16 * m.w - 17 * m.w
        return ZStack(alignment: .topLeading) {
            headline.confettiGiftOfferPlace(top: 86 * m.u, width: m.width)
            ticket(width: ticketWidth, height: 463 * m.u, bodyHeight: 313 * m.u)
                .padding(.leading, 16 * m.w)
                .padding(.top, 262 * m.u)
            // offerMascot: zIndex 2, over the ticket.
            mascot.confettiGiftOfferPlace(top: 210 * m.u, width: m.width)
            // finePrint carries marginTop 16, which an absolutely placed box adds to its top.
            finePrint
                .frame(width: m.width - 48, alignment: .top)
                .padding(.leading, 24)
                .padding(.top, 734 * m.u + 16)
        }
        .frame(width: m.width, height: m.height, alignment: .topLeading)
    }

    private func flow(_ m: ConfettiGiftOfferMetrics) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(spacing: 0) {
                headline
                ticket(width: m.width - 48, height: nil, bodyHeight: nil)
                    .padding(.top, 92)
                finePrint
                    .padding(.top, 16)
                    .padding(.bottom, 8)
            }
            .padding(.horizontal, 24)
            .padding(.top, m.top + 64)
            .padding(.bottom, m.bottom + 24)
            .frame(width: m.width)
            mascot.confettiGiftOfferPlace(top: m.top + 64 + 98 - 6, width: m.width)
        }
        .frame(width: m.width, alignment: .topLeading)
    }

    /// `ticket` (tsx:259): a dark card of radius 44 with a "%" watermark, the price block, a dashed perforation
    /// with two notches and the Claim button. `height` and `bodyHeight` are nil when the card flows (large text).
    private func ticket(width: CGFloat, height: CGFloat?, bodyHeight: CGFloat?) -> some View {
        VStack(spacing: 0) {
            ticketBody(bodyHeight: bodyHeight)
            perforation(width: width)
            ticketFoot
        }
        .frame(width: width, height: height)
        .background(alignment: .topLeading) {
            watermark
        }
        .background(ConfettiGiftOfferPalette.hex(0x222222))
        .clipShape(RoundedRectangle(cornerRadius: 44, style: .circular))
    }

    private var watermark: some View {
        GeometryReader { box in
            ForEach(Self.percents.indices, id: \.self) { index in
                let mark = Self.percents[index]
                ConfettiGiftOfferText("%", style: ConfettiGiftOfferType.percent)
                    .rotationEffect(.degrees(mark.turn))
                    .offset(x: box.size.width * mark.left / 100, y: box.size.height * mark.top / 100)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func ticketBody(bodyHeight: CGFloat?) -> some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: 103)
            ConfettiGiftOfferCountdownPill(countdown: countdown)
            ConfettiGiftOfferText("$4.99", style: ConfettiGiftOfferType.oldPrice)
                .padding(.top, 27)
            ConfettiGiftOfferText("$1.49", style: ConfettiGiftOfferType.price)
                .padding(.top, 2)
            ConfettiGiftOfferText("per month", style: ConfettiGiftOfferType.perMonth)
                .padding(.top, 2)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity)
        .frame(height: bodyHeight)
        .frame(minHeight: bodyHeight == nil ? 300 : nil, alignment: .top)
    }

    /// A 1.5 pt dashed line with a white notch cut into each end. Its box is only 1.5 pt tall; the notches
    /// (22 pt, `top: -11`) hang over it and the ticket clips them.
    private func perforation(width: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            Path { path in
                path.move(to: CGPoint(x: 0, y: 0.75))
                path.addLine(to: CGPoint(x: width, y: 0.75))
            }
            .stroke(ConfettiGiftOfferPalette.hex(0x4A4A4A), style: StrokeStyle(lineWidth: 1.5, dash: [4.5, 4.5]))
            Circle().fill(Color.white).frame(width: 22, height: 22).offset(x: -11, y: -11)
            Circle().fill(Color.white).frame(width: 22, height: 22).offset(x: width - 11, y: -11)
        }
        .frame(width: width, height: 1.5, alignment: .topLeading)
    }

    private var ticketFoot: some View {
        ConfettiGiftOfferButton(
            title: "Claim your offer",
            kind: .mint,
            minHeight: 65,
            radius: 33,
            reduced: context.reduced,
            controller: context.controller,
            action: context.controller.claim
        )
        .padding(.horizontal, 37)
        .padding(.top, 10)
        .frame(maxHeight: .infinity, alignment: .center)
    }
}

/// "Offer expire in: 59:59" in its dark red pill (tsx:46-58).
struct ConfettiGiftOfferCountdownPill: View {
    let countdown: ConfettiGiftOfferCountdown

    var body: some View {
        Group {
            switch countdown {
            case let .loop(label):
                text(label)
            case let .wall(start):
                // One tick a second from the moment a touch took over.
                TimelineView(.periodic(from: start, by: 1)) { timeline in
                    let elapsed = max(0, Int((timeline.date.timeIntervalSince(start) + 0.02).rounded(.down)))
                    let seconds = max(0, 3599 - elapsed)
                    text(String(format: "%02d:%02d", seconds / 60, seconds % 60))
                }
            }
        }
        .padding(.horizontal, 15)
        .frame(height: 32)
        .background(ConfettiGiftOfferPalette.hex(0x6E3C36), in: RoundedRectangle(cornerRadius: 9, style: .circular))
    }

    private func text(_ label: String) -> some View {
        ConfettiGiftOfferText("Offer expire in: \(label)", style: ConfettiGiftOfferType.countdown)
            .accessibilityLabel("Offer expires in \(label)")
    }
}

// MARK: - Account

/// "Now let's create account" with the raccoon making a heart and the two sign-in buttons (tsx:239-250).
struct ConfettiGiftOfferSignupScene: View {
    let context: ConfettiGiftOfferContext
    let time: Double
    let active: Bool

    var body: some View {
        let m = context.metrics
        confettiGiftOfferScroller(context, enabled: active) {
            if m.largeText {
                flow(m)
            } else {
                fixed(m)
            }
        }
    }

    private var headline: some View {
        confettiGiftOfferHeadline(
            [[ConfettiGiftOfferRun("Now let’s")], [ConfettiGiftOfferRun("create account")]],
            label: "Now let’s create account"
        )
    }

    private var copy: some View {
        ConfettiGiftOfferText("Save your progress & reach your goals", style: ConfettiGiftOfferType.signupCopy)
    }

    private var hero: some View {
        ZStack {
            ConfettiGiftOfferSparkles(time: time, reduced: context.reduced)
            ConfettiGiftOfferMascot(pose: .love, size: 312)
        }
    }

    private var buttons: some View {
        VStack(spacing: 14) {
            ConfettiGiftOfferButton(
                title: "Continue with Apple",
                reduced: context.reduced,
                controller: context.controller,
                action: { context.controller.finish(.apple) },
                icon: { ConfettiGiftOfferAppleLogo() }
            )
            ConfettiGiftOfferButton(
                title: "Continue with Google",
                kind: .secondary,
                reduced: context.reduced,
                controller: context.controller,
                action: { context.controller.finish(.google) },
                icon: { ConfettiGiftOfferGoogleLogo() }
            )
        }
    }

    private func fixed(_ m: ConfettiGiftOfferMetrics) -> some View {
        ZStack(alignment: .topLeading) {
            headline.confettiGiftOfferPlace(top: 122 * m.u, width: m.width)
            // signupCopy carries marginTop 10.
            copy.confettiGiftOfferPlace(top: 218 * m.u + 10, width: m.width)
            // place(352, 300): the mascot (312) is centred in a 300u box; the sparkles fill the box.
            hero
                .frame(width: m.width, height: 300 * m.u)
                .padding(.top, 352 * m.u)
            buttons
                .frame(width: m.width - 66 * m.w)
                .padding(.leading, 33 * m.w)
                .padding(.top, 695 * m.u)
        }
        .frame(width: m.width, height: m.height, alignment: .topLeading)
    }

    private func flow(_ m: ConfettiGiftOfferMetrics) -> some View {
        VStack(spacing: 0) {
            headline
            copy.padding(.top, 10)
            hero.frame(maxWidth: .infinity).frame(height: 312)
            buttons.padding(.top, 24)
        }
        .padding(.horizontal, 24)
        .padding(.top, m.top + 64)
        .padding(.bottom, m.bottom + 24)
        .frame(width: m.width)
    }
}

// MARK: - The end

/// "You’re in." (or "All good."): the story's last screen and its Replay button (tsx:251).
struct ConfettiGiftOfferFinishScene: View {
    let context: ConfettiGiftOfferContext
    let stage: ConfettiGiftOfferStage

    var body: some View {
        let m = context.metrics
        let complete = stage == .complete
        ZStack {
            ConfettiGiftOfferPalette.screen
            ConfettiGiftOfferScroller(
                enabled: true,
                onDragBegan: {}
            ) {
                VStack(spacing: 20) {
                    ConfettiGiftOfferMascot(pose: .love, size: 200)
                    ConfettiGiftOfferText(complete ? "You’re in." : "All good.", style: ConfettiGiftOfferType.headline)
                        .accessibilityAddTraits(.isHeader)
                    ConfettiGiftOfferParagraph(
                        text: complete
                            ? "This is a demo: no account was created and no payment was made."
                            : "The offer is closed. You can come back whenever you like.",
                        style: ConfettiGiftOfferType.finishCopy
                    )
                    .padding(.bottom, 16)
                    ConfettiGiftOfferButton(
                        title: "Replay the gift",
                        stretch: false,
                        reduced: context.reduced,
                        controller: context.controller,
                        action: context.controller.reset
                    )
                }
                .padding(.horizontal, 32)
                .padding(.top, m.top + 90)
                .padding(.bottom, m.bottom + 24)
                .frame(width: m.width)
                .frame(minHeight: m.height)
            }
        }
    }
}
