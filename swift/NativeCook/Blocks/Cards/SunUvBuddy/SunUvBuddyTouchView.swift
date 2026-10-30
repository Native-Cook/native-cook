import SwiftUI
import UIKit

struct SunUvBuddyTouchView: UIViewRepresentable {
  var onTouch: (CGPoint, Int) -> Void
  func makeUIView(context: Context) -> SunUvBuddyTouchSurface {
    let view = SunUvBuddyTouchSurface()
    view.onTouch = onTouch
    return view
  }
  func updateUIView(_ view: SunUvBuddyTouchSurface, context: Context) { view.onTouch = onTouch }
}

final class SunUvBuddyTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onTouch: ((CGPoint, Int) -> Void)?
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
  @available(*, unavailable) required init?(coder: NSCoder) { nil }
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool { true }
  @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
    let point = recognizer.location(in: self)
    switch recognizer.state {
    case .began: onTouch?(point, 0)
    case .changed: onTouch?(point, 1)
    case .ended, .cancelled, .failed: onTouch?(point, 2)
    default: break
    }
  }
}
