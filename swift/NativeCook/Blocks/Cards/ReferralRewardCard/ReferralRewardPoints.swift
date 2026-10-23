import SwiftUI
import UIKit

struct ReferralRewardPoints: View {
  let model: ReferralRewardModel
  private static let glyphs = (0...9).map(String.init)
  var body: some View {
    let _ = model.pointsClock.frame
    let scale = model.layout.fontScale
    let advance = model.layout.digitAdvance
    ZStack {
      ForEach(0..<3, id: \.self) { [frame = model.pointsClock.frame] order in
        let _ = frame
        let turn = model.turns[order].value
        let shown = model.shown[order].value
        ZStack {
          ForEach(0..<10, id: \.self) { digit in
            let away = ReferralRewardMath.wrap(Double(digit) - turn + 5, 10) - 5
            let far = min(abs(away), 1)
            let opacity = pow(max(1 - far / 0.75, 0), 1.4)
            Text(Self.glyphs[digit]).font(
              .system(size: 30 * scale, weight: .bold).monospacedDigit()
            )
            .foregroundStyle(.white).frame(width: advance + 16, height: 36)
            .scaleEffect(x: 1, y: model.reduced ? 1 : 1 - 0.3 * far)
            .blur(radius: model.reduced || opacity <= 0 ? 0 : 5 * far)
            .opacity(opacity)
            .offset(y: model.reduced ? 0 : away * 28.8)
          }
        }.opacity(shown)
          .offset(x: Double(1 - order) * advance, y: model.reduced ? 0 : (1 - shown) * 14.4)
      }
    }.frame(width: advance * 3, height: 36)
      .scaleEffect(1 + 0.1 * model.bump.value)
      .offset(x: -(3 - model.columns.value) * advance / 2)
      // The drums are drawing only; expose a single, fixed-size text element.
      .accessibilityRepresentation { Text(model.pointsLabel) }
      .contentShape(.accessibility, Rectangle())
      .accessibilitySortPriority(40)
  }
}
