import UIKit

final class QrJoyPostcardTouchSurface: UIView, UIGestureRecognizerDelegate {
  var action: ((QrJoyPostcardTouchView.Phase, CGPoint) -> Void)?
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
    isAccessibilityElement = false
    let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
    gesture.minimumPressDuration = 0
    gesture.allowableMovement = .greatestFiniteMagnitude
    gesture.cancelsTouchesInView = false
    gesture.delegate = self
    addGestureRecognizer(gesture)
  }
  @available(*, unavailable)
  required init?(coder: NSCoder) { nil }
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
  @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
    let point = gesture.location(in: self)
    switch gesture.state {
    case .began: action?(.began, point)
    case .changed: action?(.changed, point)
    case .ended: action?(.ended, point)
    case .cancelled, .failed: action?(.cancelled, point)
    default: break
    }
  }
}
