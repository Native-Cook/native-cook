// Native numeric text transitions match the source's iOS NumericText view.
import SwiftUI

struct AuraNumber: View {
  let words: AuraWords
  let balance: Bool

  var body: some View {
    Text(balance ? words.balance : words.amount)
      .font(.system(size: (balance ? 34 : 15) * words.scale, weight: balance ? .semibold : .medium))
      .tracking(balance ? -0.6 * words.scale : -0.25)
      .foregroundStyle(.white)
      .contentTransition(.numericText())
      .animation(words.reduced ? nil : .default, value: balance ? words.balance : words.amount)
      .fixedSize()
      .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: balance ? .center : .leading)
      .accessibilityHidden(true)
  }
}
