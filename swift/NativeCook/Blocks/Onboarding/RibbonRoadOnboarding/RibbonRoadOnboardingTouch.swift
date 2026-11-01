import SwiftUI
import UIKit

struct RibbonRoadOnboardingTouch: UIViewRepresentable {
  var event: (UIGestureRecognizer.State,CGPoint) -> Void
  func makeUIView(context: Context) -> RibbonRoadOnboardingTouchSurface {
    let view=RibbonRoadOnboardingTouchSurface(); view.event=event; return view
  }
  func updateUIView(_ view: RibbonRoadOnboardingTouchSurface,context: Context) { view.event=event }
}
final class RibbonRoadOnboardingTouchSurface: UIView, UIGestureRecognizerDelegate {
  var event: ((UIGestureRecognizer.State,CGPoint) -> Void)?
  override init(frame: CGRect) {
    super.init(frame:frame)
    backgroundColor = .clear; isOpaque=false; isAccessibilityElement=false
    let gesture=UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
    gesture.minimumPressDuration=0; gesture.allowableMovement = .greatestFiniteMagnitude
    gesture.delegate=self; addGestureRecognizer(gesture)
  }
  @available(*,unavailable) required init?(coder: NSCoder) { nil }
  func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
  @objc private func handle(_ gesture: UILongPressGestureRecognizer) { event?(gesture.state,gesture.location(in:self)) }
}
