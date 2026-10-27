import SwiftUI

/// `PressScale` (shared/PressScale.tsx): the whole surface settles to 0.96 in 150 ms on a strong ease-out
/// and returns on the same curve; under Reduce Motion it dims to 0.72 instead. The press is reported to the
/// controller (the `onPressIn` / `onPressOut` of the block's `pressOwnership`), which owns the loop while a
/// finger is down.
struct ConfettiGiftOfferPressStyle: ButtonStyle {
    var reduced: Bool
    var scale: CGFloat = 0.96
    var controller: ConfettiGiftOfferController

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(!reduced && configuration.isPressed ? scale : 1)
            .opacity(reduced && configuration.isPressed ? 0.72 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.15), value: configuration.isPressed)
            .onChange(of: configuration.isPressed) { _, pressed in
                if pressed { controller.pressIn() } else { controller.pressOut() }
            }
    }
}

/// The pill buttons of the block (`Button` in ConfettiGiftOffer.tsx:42, styles on lines 258-259).
struct ConfettiGiftOfferButton<Icon: View>: View {
    enum Kind {
        case primary
        case secondary
        case mint
        case danger
    }

    let title: String
    var kind: Kind = .primary
    var width: CGFloat?
    /// A column stretches its buttons (`alignItems: 'stretch'`); a centred one keeps its natural width.
    var stretch = true
    var minHeight: CGFloat = 57
    var radius: CGFloat = 29
    var reduced: Bool
    var controller: ConfettiGiftOfferController
    var action: () -> Void
    @ViewBuilder var icon: () -> Icon

    private var fill: Color {
        switch kind {
        case .primary: return ConfettiGiftOfferPalette.button
        case .secondary: return ConfettiGiftOfferPalette.hex(0xFFFFFF, alpha: Double(0x59) / 255)
        case .mint: return ConfettiGiftOfferPalette.green
        case .danger: return .white
        }
    }

    private var border: (width: CGFloat, color: Color) {
        switch kind {
        case .primary: return (0, .clear)
        case .secondary: return (1.5, ConfettiGiftOfferPalette.hex(0xD3D3DD))
        case .mint: return (5, ConfettiGiftOfferPalette.greenDark)
        case .danger: return (1.5, ConfettiGiftOfferPalette.hex(0xF7CFCB))
        }
    }

    private var labelColor: Color {
        switch kind {
        case .primary, .mint: return .white
        case .secondary: return ConfettiGiftOfferPalette.ink
        case .danger: return ConfettiGiftOfferPalette.hex(0xE5484D)
        }
    }

    var body: some View {
        Button(action: action) {
            surface
        }
        .buttonStyle(ConfettiGiftOfferPressStyle(reduced: reduced, controller: controller))
        .accessibilityLabel(title)
    }

    private var surface: some View {
        let edge = border
        return HStack(spacing: 9) {
            icon()
            ConfettiGiftOfferText(title, style: ConfettiGiftOfferType.buttonLabel(labelColor))
        }
        // paddingHorizontal 20, paddingVertical 14 inside the border; the border is part of the box.
        .padding(.horizontal, 20 + edge.width)
        .padding(.vertical, 14 + edge.width)
        .frame(width: width)
        .frame(maxWidth: width == nil && stretch ? .infinity : nil, minHeight: minHeight)
        .background(fill, in: RoundedRectangle(cornerRadius: radius, style: .circular))
        .overlay {
            if edge.width > 0 {
                RoundedRectangle(cornerRadius: radius, style: .circular)
                    .inset(by: edge.width / 2)
                    .stroke(edge.color, lineWidth: edge.width)
            }
        }
        .contentShape(RoundedRectangle(cornerRadius: radius, style: .circular))
    }
}

extension ConfettiGiftOfferButton where Icon == EmptyView {
    init(
        title: String,
        kind: Kind = .primary,
        width: CGFloat? = nil,
        stretch: Bool = true,
        minHeight: CGFloat = 57,
        radius: CGFloat = 29,
        reduced: Bool,
        controller: ConfettiGiftOfferController,
        action: @escaping () -> Void
    ) {
        self.init(
            title: title, kind: kind, width: width, stretch: stretch, minHeight: minHeight, radius: radius,
            reduced: reduced, controller: controller, action: action, icon: { EmptyView() }
        )
    }
}
