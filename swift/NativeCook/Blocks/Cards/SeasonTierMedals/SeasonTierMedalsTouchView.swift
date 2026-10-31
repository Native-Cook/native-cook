import SwiftUI
import UIKit

struct SeasonTierMedalsTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
  func makeUIView(context: Context) -> SeasonTierMedalsTouchSurface {
    let view = SeasonTierMedalsTouchSurface()
    view.onEvent = onEvent
    return view
  }
  func updateUIView(_ view: SeasonTierMedalsTouchSurface, context: Context) {
    view.onEvent = onEvent
  }
}
