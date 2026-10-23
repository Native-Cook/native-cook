// SwiftUI receives only discrete content changes; geometry stays on the display clock.
import Observation

@MainActor @Observable final class AuraWords {
  var balance = "$ 52,002.50"
  var amount = "$60"
  var after = false
  var reduced = false
  var arrived = false
  var scale = 1.0
  var hint = AuraL10n.t("rest")
  var hintKind = "rest"
}
