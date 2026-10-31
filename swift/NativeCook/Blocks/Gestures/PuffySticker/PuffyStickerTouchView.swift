import SwiftUI
import UIKit

struct PuffyStickerTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
  func makeUIView(context: Context) -> PuffyStickerTouchSurface {
    let view = PuffyStickerTouchSurface()
    view.onEvent = onEvent
    return view
  }
  func updateUIView(_ view: PuffyStickerTouchSurface, context: Context) {
    view.onEvent = onEvent
  }
}

// A zero-delay long press survives the gallery's simultaneous navigation back swipe.
final class PuffyStickerTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onEvent: ((UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void)?
  private var start = CGPoint.zero
  private var last = CGPoint.zero
  private var lastTime = 0.0
  private var velocity = CGVector.zero
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
  @available(*, unavailable) required init?(coder: NSCoder) { nil }
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
  @objc private func handle(_ gesture: UILongPressGestureRecognizer) {
    let point = gesture.location(in: self)
    let now = ProcessInfo.processInfo.systemUptime
    if gesture.state == .began {
      start = point
      last = point
      lastTime = now
      velocity = .zero
    } else if gesture.state == .changed {
      let dt = max(0.001, now - lastTime)
      velocity = CGVector(dx: (point.x - last.x) / dt, dy: (point.y - last.y) / dt)
      last = point
      lastTime = now
    }
    onEvent?(
      gesture.state, point, CGSize(width: point.x - start.x, height: point.y - start.y), velocity)
  }
}
