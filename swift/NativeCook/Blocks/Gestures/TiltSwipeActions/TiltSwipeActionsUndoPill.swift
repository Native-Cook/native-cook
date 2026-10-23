import SwiftUI

/// "Groceries deleted · Undo", above the tab panel. Hero morph: the pill grows out of the deleted row's
/// Delete tile (it starts at the row's height, tile-sized at the right, and travels down while it widens).
struct TiltSwipeActionsUndoPill: View {
    let model: TiltSwipeActionsModel
    let reduceMotion: Bool

    private typealias Layout = TiltSwipeActionsLayout
    private typealias Colour = TiltSwipeActionsColor

    var body: some View {
        let shown = model.pill.value
        let p = shown.tiltClamped(0, 1.06)
        let width = TiltSwipeActionsFlat.planeWidth - Layout.gutter - (Layout.gutter + Layout.fab + TiltSwipeActionsTokens.Undo.trailingFabGap)
        content
            .padding(.leading, TiltSwipeActionsTokens.Undo.leftPadding)
            .padding(.trailing, TiltSwipeActionsTokens.Undo.rightPadding)
            .frame(width: width, height: Layout.pillHeight)
            .background(
                Capsule(style: .circular)
                    .fill(Colour.plum)
                    .shadow(color: Colour.slabShadow.opacity(TiltSwipeActionsTokens.Undo.shadowOpacity), radius: TiltSwipeActionsTokens.Undo.shadowRadius, x: 0, y: TiltSwipeActionsTokens.Undo.shadowY)
            )
            .scaleEffect(
                x: reduceMotion ? 1 : TiltSwipeActionsTokens.Undo.startScaleX + (1 - TiltSwipeActionsTokens.Undo.startScaleX) * p,
                y: reduceMotion ? 1 : 1 + (TiltSwipeActionsTokens.Undo.startScaleY - 1) * (1 - p),
                anchor: .trailing
            )
            .offset(y: reduceMotion ? 0 : (1 - p) * model.pillFrom)
            .opacity(reduceMotion ? min(shown, 1) : min(p * TiltSwipeActionsTokens.Undo.opacityGain, 1))
            .offset(x: Layout.gutter, y: TiltSwipeActionsFlat.planeHeight - Layout.pillBottom - Layout.pillHeight)
            .accessibilityHidden(shown < 0.05)
            .accessibilityElement(children: .contain)
    }

    private var content: some View {
        HStack(spacing: 0) {
            TiltSwipeActionsLabel(
                text: "\(model.pillTitle) deleted",
                style: TiltSwipeActionsType.pill,
                color: Colour.white,
                fitsWidth: true
            )
            .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: TiltSwipeActionsTokens.Undo.actionGap) {
                TiltSwipeActionsFeatherIcon(icon: .rotateCcw, size: TiltSwipeActionsTokens.Undo.icon, color: Colour.pink)
                TiltSwipeActionsLabel(text: "Undo", style: TiltSwipeActionsType.pillAction, color: Colour.pink)
            }
            .padding(.horizontal, TiltSwipeActionsTokens.Undo.actionPadding)
            .frame(height: TiltSwipeActionsTokens.Undo.actionHeight)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Undo, restore \(model.pillTitle)")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { model.undoPressed() }
        }
    }
}
