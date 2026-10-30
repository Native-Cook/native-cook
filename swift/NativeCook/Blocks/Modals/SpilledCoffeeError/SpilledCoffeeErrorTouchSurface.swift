import UIKit

// Zero-delay long press survives the gallery back-swipe and recognizes simultaneously.
final class SpilledCoffeeErrorTouchSurface: UIView, UIGestureRecognizerDelegate {
  var action: ((UIGestureRecognizer.State, CGPoint) -> Void)?
  override init(frame: CGRect) {
    super.init(frame: frame)
    isOpaque = false
    backgroundColor = .clear
    isAccessibilityElement = false
    let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
    recognizer.minimumPressDuration = 0
    recognizer.allowableMovement = .greatestFiniteMagnitude
    recognizer.cancelsTouchesInView = false
    recognizer.delegate = self
    addGestureRecognizer(recognizer)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { nil }
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
  @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
    action?(recognizer.state, recognizer.location(in: self))
  }
}
