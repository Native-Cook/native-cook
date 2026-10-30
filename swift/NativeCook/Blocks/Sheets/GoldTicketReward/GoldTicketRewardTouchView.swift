import SwiftUI
import UIKit

struct GoldTicketRewardTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
  func makeUIView(context: Context) -> GoldTicketRewardTouchSurface {
    let view = GoldTicketRewardTouchSurface()
    view.onEvent = onEvent
    return view
  }
  func updateUIView(_ view: GoldTicketRewardTouchSurface, context: Context) {
    view.onEvent = onEvent
  }
}
