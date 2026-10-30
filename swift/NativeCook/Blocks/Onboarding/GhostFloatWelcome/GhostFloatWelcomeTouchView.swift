import SwiftUI
import UIKit

/// A zero-delay recognizer shares recognition with the gallery's navigation gestures.
struct GhostFloatWelcomeTouchView: UIViewRepresentable {
  var scale: Double
  var dy: Double
  var enabled: Bool
  var began: (CGPoint) -> Void
  var changed: (CGPoint, CGVector) -> Void
  var ended: (CGPoint, CGVector) -> Void

  func makeUIView(context: Context) -> GhostFloatWelcomeTouchSurface {
    let view = GhostFloatWelcomeTouchSurface()
    updateUIView(view, context: context)
    return view
  }
  func updateUIView(_ view: GhostFloatWelcomeTouchSurface, context: Context) {
    view.scale = scale
    view.dy = dy
    view.isUserInteractionEnabled = enabled
    view.began = began
    view.changed = changed
    view.ended = ended
  }
}
