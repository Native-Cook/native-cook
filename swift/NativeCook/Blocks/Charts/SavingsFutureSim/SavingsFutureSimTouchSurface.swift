import UIKit

final class SavingsFutureSimTouchSurface: UIView, UIGestureRecognizerDelegate {
  var action: ((Int, CGPoint) -> Void)?
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
    isMultipleTouchEnabled = false
    let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
    gesture.minimumPressDuration = 0
    gesture.allowableMovement = .greatestFiniteMagnitude
    gesture.cancelsTouchesInView = false
    gesture.delegate = self
    addGestureRecognizer(gesture)
  }
  required init?(coder: NSCoder) { nil }
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
  @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
    switch gesture.state {
    case .began: action?(0, gesture.location(in: self))
    case .changed: action?(1, gesture.location(in: self))
    case .ended, .cancelled, .failed: action?(2, gesture.location(in: self))
    default: break
    }
  }
}
