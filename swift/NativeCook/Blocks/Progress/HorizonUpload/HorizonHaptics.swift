import UIKit

/// Prepare the four platform equivalents ahead of the transfer; autoplay mutes chunk ticks only.
@MainActor final class HorizonHaptics {
  private let light = UIImpactFeedbackGenerator(style: .light)
  private let soft = UIImpactFeedbackGenerator(style: .soft)
  private let medium = UIImpactFeedbackGenerator(style: .medium)
  private let success = UINotificationFeedbackGenerator()
  init() { light.prepare(); soft.prepare(); medium.prepare(); success.prepare() }
  func fire(_ kind: String) {
    switch kind {
    case "soft": soft.impactOccurred(); soft.prepare()
    case "medium": medium.impactOccurred(); medium.prepare()
    case "success": success.notificationOccurred(.success); success.prepare()
    default: light.impactOccurred(); light.prepare()
    }
  }
}
