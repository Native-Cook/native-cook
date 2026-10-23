import UIKit

final class GlassPeakChartAccessibilityElement: UIAccessibilityElement {
  var activate: (() -> Void)?
  var adjust: ((Double) -> Void)?

  override func accessibilityActivate() -> Bool {
    guard let activate else { return false }
    activate()
    return true
  }

  override func accessibilityIncrement() { adjust?(1) }
  override func accessibilityDecrement() { adjust?(-1) }
}
