import UIKit

final class GhostFloatWelcomeTouchSurface: UIView, UIGestureRecognizerDelegate {
  var scale = 1.0
  var dy = 0.0
  var began: ((CGPoint) -> Void)?
  var changed: ((CGPoint, CGVector) -> Void)?
  var ended: ((CGPoint, CGVector) -> Void)?
  private var last = CGPoint.zero
  private var lastTime = 0.0
  private var velocity = CGVector.zero

  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
    isMultipleTouchEnabled = false
    isAccessibilityElement = false
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
    let now = Date().timeIntervalSinceReferenceDate
    let location = recognizer.location(in: self)
    let point = CGPoint(x: location.x / scale, y: (location.y - dy) / scale)
    switch recognizer.state {
    case .began:
      last = location
      lastTime = now
      velocity = .zero
      began?(point)
    case .changed:
      let dt = max(0.001, now - lastTime)
      velocity = CGVector(
        dx: (location.x - last.x) / dt / scale, dy: (location.y - last.y) / dt / scale)
      last = location
      lastTime = now
      changed?(point, velocity)
    case .ended, .cancelled, .failed:
      ended?(point, velocity)
    default: break
    }
  }
}
