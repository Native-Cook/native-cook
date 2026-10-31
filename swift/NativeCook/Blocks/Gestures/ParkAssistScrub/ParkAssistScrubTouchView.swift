import SwiftUI
import UIKit

struct ParkAssistScrubTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State, CGPoint) -> Void
  func makeUIView(context: Context) -> ParkAssistScrubTouchSurface {
    let surface = ParkAssistScrubTouchSurface(); surface.onEvent = onEvent; return surface
  }
  func updateUIView(_ view: ParkAssistScrubTouchSurface, context: Context) { view.onEvent = onEvent }
}
final class ParkAssistScrubTouchSurface: UIView, UIGestureRecognizerDelegate {
  var onEvent: ((UIGestureRecognizer.State,CGPoint) -> Void)?
  override init(frame: CGRect) {
    super.init(frame:frame); backgroundColor = .clear; isOpaque = false; isAccessibilityElement = false
    let recognizer = UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
    recognizer.minimumPressDuration = 0; recognizer.allowableMovement = 600
    recognizer.cancelsTouchesInView = false; recognizer.delegate = self; addGestureRecognizer(recognizer)
  }
  @available(*,unavailable) required init?(coder: NSCoder) { nil }
  func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
  @objc private func handle(_ recognizer: UILongPressGestureRecognizer) { onEvent?(recognizer.state,recognizer.location(in:self)) }
}
