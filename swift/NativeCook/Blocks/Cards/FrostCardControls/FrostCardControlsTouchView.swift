import SwiftUI
import UIKit

struct FrostCardControlsTouchView: UIViewRepresentable {
  var began: (CGPoint) -> Void
  var changed: (CGSize) -> Void
  var ended: () -> Void
  func makeUIView(context: Context) -> FrostCardControlsTouchSurface {
    let view = FrostCardControlsTouchSurface()
    updateUIView(view, context: context)
    return view
  }
  func updateUIView(_ view: FrostCardControlsTouchSurface, context: Context) {
    view.began = began
    view.changed = changed
    view.ended = ended
  }
}
