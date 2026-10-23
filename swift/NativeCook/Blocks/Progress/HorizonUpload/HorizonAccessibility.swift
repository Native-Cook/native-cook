import UIKit

/// Native VoiceOver activation bypasses gesture slop but uses the same guarded commands.
@MainActor final class HorizonAccessibility: UIAccessibilityElement {
  var activate: (() -> Void)?
  override func accessibilityActivate() -> Bool { activate?(); return true }
}
