import SwiftUI

/// "Are you sure to skip -70% offer?": the sheet that rises over a light scrim when the offer is closed
/// (`ExitSheet`, tsx:102-124). React Native shows it in a `Modal`; here it is the top layer of the block.
/// The loop positions it from measured source positions; a touch hands it to the spring in the controller.
struct ConfettiGiftOfferSheet: View {
    let context: ConfettiGiftOfferContext
    let sheet: ConfettiGiftOfferSheetFrame

    @Environment(\.confettiGiftOfferFontScale) private var fontScale
    /// The scroll content's measured height, which the panel hugs.
    @State private var contentHeight: CGFloat = 0

    var body: some View {
        let m = context.metrics
        let maxHeight = max(240, m.height - m.top - 20)
        ZStack(alignment: .bottom) {
            scrim
                .opacity(sheet.scrim)
            panel(m, maxHeight: maxHeight)
                .offset(y: sheet.translateY)
        }
        .frame(width: m.width, height: m.height, alignment: .bottom)
        .offset(x: sheet.outgoingX)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape) { context.controller.closeSheet() }
    }

    /// Pressing the scrim leaves the sheet like the drag does (`scrim`: rgba(0,0,0,.19)).
    private var scrim: some View {
        Button(action: context.controller.closeSheet) {
            Color.black.opacity(0.19)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .buttonStyle(ConfettiGiftOfferPressStyle(reduced: false, scale: 1, controller: context.controller))
        .accessibilityLabel("Continue without the sample offer")
    }

    /// The sheet hugs its content: `sheetTop` (30) + the scroll viewport, which is as tall as its content up to
    /// `maxHeight - 48`; the panel is at least `516/874` of the screen (unless the text is large) and at most
    /// `maxHeight`. All heights are explicit; a flexible `minHeight`/`maxHeight` frame would grow to the proposal.
    private func panel(_ m: ConfettiGiftOfferMetrics, maxHeight: CGFloat) -> some View {
        let viewport = contentHeight > 0 ? min(contentHeight, maxHeight - 48) : maxHeight - 48
        let minimum = fontScale <= 1.3 ? m.height * 516 / 874 : 0
        let height = min(max(30 + viewport, minimum), maxHeight)
        return VStack(spacing: 0) {
            handle
            ConfettiGiftOfferScroller(
                enabled: sheet.visible,
                showsIndicator: true,
                height: viewport,
                onContentHeight: { contentHeight = $0 },
                onDragBegan: context.controller.sheetReadingBegan,
                onMomentumBegan: context.controller.sheetReadingBegan
            ) {
                content(m)
            }
        }
        .padding(.horizontal, 33)
        .frame(maxWidth: .infinity)
        .frame(height: height, alignment: .top)
        .background(Color.white, in: UnevenRoundedRectangle(topLeadingRadius: 34, topTrailingRadius: 34, style: .circular))
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { context.controller.panelHeight = $0 }
        .accessibilityElement(children: .contain)
    }

    /// The 30 pt drag zone with the 22 x 4 handle (`sheetTop`, `dragZone`, `handle`).
    private var handle: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 2, style: .circular)
                .fill(ConfettiGiftOfferPalette.hex(0xC7C7CC))
                .frame(width: 22, height: 4)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 30)
        .contentShape(Rectangle())
        .overlay {
            ConfettiGiftOfferHandlePan(
                onBegan: context.controller.sheetPanBegan,
                onChanged: { context.controller.sheetPanChanged(translationY: $0) },
                onEnded: { context.controller.sheetPanEnded(velocityY: $0, success: $1) }
            )
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Drag down to skip the offer")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { context.controller.closeSheet() }
    }

    private func content(_ m: ConfettiGiftOfferMetrics) -> some View {
        VStack(spacing: 0) {
            // sheetMascot: a 178 pt box with the 190 pt picture centred in it.
            ConfettiGiftOfferMascot(pose: .worried, size: 190)
                .frame(maxWidth: .infinity)
                .frame(height: 178)
            ConfettiGiftOfferText(
                lines: [
                    [ConfettiGiftOfferRun("Are you sure to skip")],
                    [ConfettiGiftOfferRun("-70%", color: ConfettiGiftOfferPalette.hex(0xF0605A)), ConfettiGiftOfferRun(" offer?")],
                ],
                style: ConfettiGiftOfferType.sheetTitle
            )
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Are you sure to skip -70% offer?")
            .padding(.top, 7)
            VStack(spacing: 13) {
                ConfettiGiftOfferButton(
                    title: "Back to offer",
                    reduced: context.reduced,
                    controller: context.controller,
                    action: context.controller.stay
                )
                ConfettiGiftOfferButton(
                    title: "Skip this offer forever",
                    kind: .danger,
                    reduced: context.reduced,
                    controller: context.controller,
                    action: context.controller.skip
                )
            }
            .padding(.top, 34)
        }
        .frame(maxWidth: .infinity)
        .padding(.bottom, m.bottom + 18)
    }
}
