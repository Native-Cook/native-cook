import SwiftUI
import UIKit

struct GlassTilesFuseTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State, CGPoint, CGSize, CGVector) -> Void
  func makeUIView(context: Context) -> GlassTilesFuseTouchSurface {
    let view = GlassTilesFuseTouchSurface()
    view.onEvent = onEvent
    return view
  }
  func updateUIView(_ view: GlassTilesFuseTouchSurface, context: Context) {
    view.onEvent = onEvent
  }
}
