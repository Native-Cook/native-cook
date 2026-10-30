import SwiftUI
import UIKit

// A zero-delay recognizer survives the gallery's simultaneous navigation back swipe.
struct CashbackPickFiveTouchView: UIViewRepresentable {
  var onBegan: () -> Void
  var onChanged: (Double) -> Void
  var onEnded: (CGPoint, Double) -> Void

  func makeUIView(context: Context) -> CashbackPickFiveTouchSurface {
    let view = CashbackPickFiveTouchSurface()
    updateUIView(view, context: context)
    return view
  }
  func updateUIView(_ view: CashbackPickFiveTouchSurface, context: Context) {
    view.onBegan = onBegan
    view.onChanged = onChanged
    view.onEnded = onEnded
  }
}

final class CashbackPickFiveTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onBegan: (() -> Void)?
  var onChanged: ((Double) -> Void)?
  var onEnded: ((CGPoint, Double) -> Void)?
  private var startY = 0.0
  private var lastY = 0.0
  private var lastTime = 0.0
  private var velocityY = 0.0
  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
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
    let point = recognizer.location(in: self)
    let time = ProcessInfo.processInfo.systemUptime
    switch recognizer.state {
    case .began:
      startY = point.y
      lastY = point.y
      lastTime = time
      velocityY = 0
      onBegan?()
    case .changed:
      velocityY = (point.y - lastY) / max(0.001, time - lastTime)
      lastY = point.y
      lastTime = time
      onChanged?(point.y - startY)
    case .ended, .cancelled, .failed:
      onEnded?(point, velocityY)
    default: break
    }
  }
}
