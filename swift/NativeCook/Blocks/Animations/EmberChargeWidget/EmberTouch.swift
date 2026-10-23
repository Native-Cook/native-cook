import SwiftUI
import UIKit

/// Zero-distance touch ownership on the untransformed wrapper. A UIKit press recogniser
/// supplies cancellation as well as release, and keeps a finger that travels or leaves the card.
struct EmberTouch: UIViewRepresentable {
  let motion: EmberMotion
  let scale: Double

  func makeUIView(context: Context) -> EmberTouchView {
    let view = EmberTouchView()
    view.motion = motion
    view.cardScale = scale
    view.updateAccessibility()
    return view
  }

  func updateUIView(_ view: EmberTouchView, context: Context) {
    view.cardScale = scale
    view.updateAccessibility()
  }
}
