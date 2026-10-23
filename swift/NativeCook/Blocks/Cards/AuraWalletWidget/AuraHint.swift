// Sentence kinds crossfade. Amount steps within a sentence replace only the copy.
import SwiftUI

struct AuraHint: View {
  let words: AuraWords
  @ScaledMetric(relativeTo: .body) private var fontScale = 1.0

  var body: some View {
    Text(words.hint)
      .id(words.hintKind)
      .transition(.opacity)
      .font(.system(size: 13 * fontScale))
      .lineSpacing((18 - 13 * 1.193) * fontScale)
      .padding(.vertical, (18 - 13 * 1.193) / 2 * fontScale)
      .foregroundStyle(.white.opacity(0.6))
      .multilineTextAlignment(.center)
      .lineLimit(2)
      .frame(maxWidth: .infinity, minHeight: 36 * fontScale, alignment: .top)
      .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.16), value: words.hintKind)
      .accessibilityLabel(words.hint)
  }
}
