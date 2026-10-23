// Chip material, labels and midpoint blur share the same interpolated fraction.
import SwiftUI

struct AuraChipDrawing: View, Animatable {
  var blend: Double
  let scale: Double
  let reduced: Bool
  let before: Double
  let after: Double
  var animatableData: Double {
    get { blend }
    set { blend = newValue }
  }

  var body: some View {
    let width = reduced ? (blend < 0.5 ? before : after) : before + (after - before) * blend
    ZStack {
      Capsule().fill(.white.opacity(0.16))
        .overlay(Capsule().strokeBorder(.white.opacity(0.18), lineWidth: scale))
        .frame(width: width, height: 22 * scale)
        .opacity(reduced ? abs(2 * blend - 1) : 1)
      Text(AuraL10n.t("balance")).opacity(1 - blend)
        .blur(radius: reduced ? 0 : 8 * blend * (1 - blend))
      Text(AuraL10n.t("after")).opacity(blend)
        .blur(radius: reduced ? 0 : 8 * blend * (1 - blend))
    }
    .font(.system(size: 10 * scale, weight: .medium))
    .tracking(0.2 * scale)
    .foregroundStyle(.white)
    .fixedSize()
    .frame(maxWidth: .infinity, maxHeight: .infinity)
  }
}
