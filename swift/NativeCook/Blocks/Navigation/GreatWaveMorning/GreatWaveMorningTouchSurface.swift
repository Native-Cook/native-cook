import UIKit

final class GreatWaveMorningTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onTouch: ((GreatWaveMorningTouchView.Phase, CGPoint, CGSize, CGSize) -> Void)?
  private var start = CGPoint.zero
  private var last = CGPoint.zero
  private var lastTime = 0.0
  private var velocity = CGSize.zero
  override init(frame: CGRect) {
    super.init(frame: frame)
    isOpaque = false
    backgroundColor = .clear
    isMultipleTouchEnabled = false
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
    let now = ProcessInfo.processInfo.systemUptime
    switch gesture.state {
    case .began:
      start = point
      last = point
      lastTime = now
      velocity = .zero
      onTouch?(.began, point, .zero, .zero)
    case .changed:
      let dt = max(0.001, now - lastTime)
      velocity = CGSize(width: (point.x - last.x) / dt, height: (point.y - last.y) / dt)
      last = point
      lastTime = now
      onTouch?(
        .changed, point, CGSize(width: point.x - start.x, height: point.y - start.y), velocity)
    case .ended, .cancelled, .failed:
      onTouch?(.ended, point, CGSize(width: point.x - start.x, height: point.y - start.y), velocity)
    default: break
    }
  }
}
