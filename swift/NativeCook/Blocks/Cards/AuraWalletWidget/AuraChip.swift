// An animatable drawing keeps the chip's blur at the midpoint, not at its endpoints.
import SwiftUI
import UIKit

struct AuraChip: View {
  let words: AuraWords

  private func width(_ text: String) -> Double {
    max(
      90 * words.scale,
      (text as NSString).size(withAttributes: [
        .font: UIFont.systemFont(ofSize: 10 * words.scale, weight: .medium),
        .kern: 0.2 * words.scale,
      ]).width + 20 * words.scale)
  }

  var body: some View {
    AuraChipDrawing(
      blend: words.after ? 1 : 0, scale: words.scale, reduced: words.reduced,
      before: width(AuraL10n.t("balance")), after: width(AuraL10n.t("after"))
    )
    .animation(
      .timingCurve(0.77, 0, 0.175, 1, duration: AuraFlow.views.chip.fade / 1000), value: words.after
    )
    .accessibilityHidden(true)
  }
}
