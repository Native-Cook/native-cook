import SwiftUI
import UIKit

/// Zero-delay recognition survives the gallery's interactive back-swipe recognizer.
struct SetupChecklistAllSetTouchView: UIViewRepresentable {
  var scale: CGFloat
  var enabled: Bool
  var began: (CGPoint) -> Void
  var ended: (CGPoint) -> Void
  func makeUIView(context: Context) -> Surface {
    let view = Surface()
    updateUIView(view, context: context)
    return view
  }
  func updateUIView(_ view: Surface, context: Context) {
    view.scale = scale
    view.isUserInteractionEnabled = enabled
    view.began = began
    view.ended = ended
  }
  final class Surface: UIView, UIGestureRecognizerDelegate {
    var scale: CGFloat = 1
    var began: ((CGPoint) -> Void)?
    var ended: ((CGPoint) -> Void)?
    private var active = false
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
      let raw = gesture.location(in: self)
      let point = CGPoint(x: raw.x / max(0.001, scale), y: raw.y / max(0.001, scale))
      switch gesture.state {
      case .began:
        active = true
        began?(point)
      case .ended, .cancelled, .failed:
        guard active else { return }
        active = false
        ended?(point)
      default: break
      }
    }
  }
}
