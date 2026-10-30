import SwiftUI
import UIKit

// Zero-delay long press keeps card drags alive alongside the gallery's back swipe.
struct QrJoyPostcardTouchView: UIViewRepresentable {
  enum Phase { case began, changed, ended, cancelled }
  var action: (Phase, CGPoint) -> Void
  func makeUIView(context: Context) -> QrJoyPostcardTouchSurface {
    let surface = QrJoyPostcardTouchSurface()
    surface.action = action
    return surface
  }
  func updateUIView(_ view: QrJoyPostcardTouchSurface, context: Context) { view.action = action }
}
