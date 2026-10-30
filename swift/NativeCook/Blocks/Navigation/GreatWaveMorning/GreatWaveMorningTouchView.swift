import SwiftUI
import UIKit

// A zero-delay recognizer keeps the gallery's back-swipe from cancelling vertical scrolls.
struct GreatWaveMorningTouchView: UIViewRepresentable {
  enum Phase { case began, changed, ended }
  var onTouch: (Phase, CGPoint, CGSize, CGSize) -> Void
  func makeUIView(context: Context) -> GreatWaveMorningTouchSurface {
    let surface = GreatWaveMorningTouchSurface()
    surface.onTouch = onTouch
    return surface
  }
  func updateUIView(_ uiView: GreatWaveMorningTouchSurface, context: Context) {
    uiView.onTouch = onTouch
  }
}
