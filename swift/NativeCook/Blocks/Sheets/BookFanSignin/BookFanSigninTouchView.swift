import SwiftUI
import UIKit

struct BookFanSigninTouchView: UIViewRepresentable {
  var action: (Int, CGPoint, CGPoint, CGPoint) -> Void
  func makeUIView(context: Context) -> BookFanSigninTouchSurface {
    let view = BookFanSigninTouchSurface()
    view.action = action
    return view
  }
  func updateUIView(_ view: BookFanSigninTouchSurface, context: Context) { view.action = action }
}

// A zero-delay recognizer survives the gallery's simultaneous back-swipe recognizer.
final class BookFanSigninTouchSurface: UIView, UIGestureRecognizerDelegate {
  var action: ((Int, CGPoint, CGPoint, CGPoint) -> Void)?
  private var start = CGPoint.zero, last = CGPoint.zero, velocity = CGPoint.zero
  private var lastTime = 0.0
  override init(frame: CGRect) {
    super.init(frame: frame)
    isOpaque = false
    backgroundColor = .clear
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
    let point = recognizer.location(in: self)
    let now = Date.timeIntervalSinceReferenceDate
    let translation = CGPoint(x: point.x - start.x, y: point.y - start.y)
    switch recognizer.state {
    case .began:
      start = point
      last = point
      lastTime = now
      velocity = .zero
      action?(0, point, .zero, .zero)
    case .changed:
      let dt = max(0.001, now - lastTime)
      velocity = CGPoint(x: (point.x - last.x) / dt, y: (point.y - last.y) / dt)
      last = point
      lastTime = now
      action?(1, point, translation, velocity)
    case .ended, .cancelled, .failed: action?(2, point, translation, velocity)
    default: break
    }
  }
}
