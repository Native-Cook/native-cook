import UIKit

// Simultaneous zero-delay recognition survives the gallery's interactive back swipe.
final class FrostCardControlsTouchSurface: UIView, UIGestureRecognizerDelegate {
  var began: ((CGPoint) -> Void)?
  var changed: ((CGSize) -> Void)?
  var ended: (() -> Void)?
  private var start = CGPoint.zero
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
    isAccessibilityElement = false
    isMultipleTouchEnabled = false
    let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
    recognizer.minimumPressDuration = 0
    recognizer.allowableMovement = .greatestFiniteMagnitude
    recognizer.cancelsTouchesInView = false
    recognizer.delegate = self
    addGestureRecognizer(recognizer)
  }
  @available(*, unavailable)
  required init?(coder: NSCoder) { nil }
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
  @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
    let point = recognizer.location(in: self)
    switch recognizer.state {
    case .began:
      start = point
      began?(point)
    case .changed: changed?(CGSize(width: point.x - start.x, height: point.y - start.y))
    case .ended, .cancelled, .failed: ended?()
    default: break
    }
  }
}
