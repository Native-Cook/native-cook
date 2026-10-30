import SwiftUI
import UIKit

struct SpilledCoffeeErrorTouchView: UIViewRepresentable {
  var action: (UIGestureRecognizer.State, CGPoint) -> Void
  func makeUIView(context: Context) -> SpilledCoffeeErrorTouchSurface {
    let view = SpilledCoffeeErrorTouchSurface()
    view.action = action
    return view
  }
  func updateUIView(_ view: SpilledCoffeeErrorTouchSurface, context: Context) {
    view.action = action
  }
}
