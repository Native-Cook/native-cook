import UIKit

final class ListeningBuddyPaywallTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onBegin: ((CGPoint) -> Void)?
  var onEnd: ((CGPoint) -> Void)?
  private var accepted = false
  override init(frame: CGRect) {
    super.init(frame: frame)
    isOpaque = false
    backgroundColor = .clear
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
    case .began:
      accepted = true
      onBegin?(point)
    case .ended, .cancelled, .failed:
      guard accepted else { return }
      accepted = false
      // RN onFinalize handles cancellation through the same release path.
      onEnd?(point)
    default: break
    }
  }
}
