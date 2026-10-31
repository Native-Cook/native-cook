import SwiftUI
import UIKit

struct GemCutPaywallTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
  func makeUIView(context: Context) -> GemCutPaywallTouchSurface {
    let view = GemCutPaywallTouchSurface()
    view.onEvent = onEvent
    return view
  }
  func updateUIView(_ view: GemCutPaywallTouchSurface, context: Context) {
    view.onEvent = onEvent
  }
}
