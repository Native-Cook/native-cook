import UIKit

final class CabinClimateCarTouchSurface: UIView, UIGestureRecognizerDelegate {
  var begin: ((CGPoint) -> Void)?
  var move: ((CGFloat) -> Void)?
  var end: (() -> Void)?
  private var start = CGPoint.zero
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
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
      begin?(point)
    case .changed: move?(point.y - start.y)
    case .ended, .cancelled, .failed: end?()
    default: break
    }
  }
}
