import SwiftUI

/// The explainer that "Talk to an Expert" and the chat button open (the `<Modal>` of the React
/// Native file): a dim backdrop and a violet card, content-sized and centred, that scrolls when
/// the text is taller than the screen allows.
struct CipherSeamModal: View {
    /// The whole screen.
    let size: CGSize
    let top: CGFloat
    let bottom: CGFloat
    let onClose: () -> Void

    /// The natural height of the text, to know whether it overflows.
    @State private var contentHeight: CGFloat = 0

    var body: some View {
        // `maxHeight: height - insets.top - insets.bottom - 48` of the card.
        let maxHeight = size.height - top - bottom - 48
        // What is left for the text: the card's 24 pt padding twice, the 8 pt gap and the 48 pt button.
        let scrollMax = max(0, maxHeight - 48 - 8 - 48)
        let viewport = min(contentHeight, scrollMax)
        ZStack {
            CipherSeamPalette.rgb(0x000000, alpha: 0x99)
            VStack(spacing: 0) {
                scrollArea(scrollMax: scrollMax, viewport: viewport)
                closeButton
                    .padding(.top, 8)
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: 28, style: .circular).fill(CipherSeamPalette.rgb(0x201332)))
            .padding(24)
        }
        .frame(width: size.width, height: size.height)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, onClose)
    }

    /// The text sizes the card: an invisible copy takes its natural height (at most `scrollMax`)
    /// and the scroll view fills that box, so the card hugs short text and scrolls long text.
    private func scrollArea(scrollMax: CGFloat, viewport: CGFloat) -> some View {
        copy
            .opacity(0)
            .accessibilityHidden(true)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height in
                contentHeight = height
            }
            .frame(maxHeight: scrollMax, alignment: .top)
            .overlay {
                ScrollView {
                    copy
                }
                .cipherSeamBlurViewport(enabled: true, contentHeight: contentHeight, viewportHeight: viewport)
            }
    }

    private var copy: some View {
        VStack(alignment: .leading, spacing: 0) {
            CipherSeamLabel(
                text: "One boundary. Two representations.", size: 24, weight: .semibold,
                color: CipherSeamPalette.rgb(0xF4EAFE), lineHeight: 29
            )
            .padding(.bottom, 18)
            CipherSeamLabel(
                text: "The card and the encoded pattern move as one plane. A fixed violet seam clips each representation, so the transformation stays attached to the card.",
                size: 15, color: CipherSeamPalette.rgb(0xC5B7D5), lineHeight: 23
            )
            .padding(.bottom, 18)
            CipherSeamLabel(
                text: "This is an interactive visual example. It does not process, store, or encrypt payment information.",
                size: 15, color: CipherSeamPalette.rgb(0xC5B7D5), lineHeight: 23
            )
            .padding(.bottom, 18)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// `styles.modalClose`.
    private var closeButton: some View {
        Button(action: onClose) {
            CipherSeamLabel(
                text: "Got it", size: 14, weight: .medium, color: CipherSeamPalette.buttonInk, alignment: .center
            )
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(RoundedRectangle(cornerRadius: 24, style: .circular).fill(CipherSeamPalette.rgb(0xF0E5FF)))
        }
        .buttonStyle(.plain)
    }
}
