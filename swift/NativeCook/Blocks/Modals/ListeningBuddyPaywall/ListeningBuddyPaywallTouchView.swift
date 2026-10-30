import SwiftUI
import UIKit

// A zero-delay recognizer survives the gallery's simultaneous navigation back swipe.
struct ListeningBuddyPaywallTouchView: UIViewRepresentable {
  var onBegin: (CGPoint) -> Void
  var onEnd: (CGPoint) -> Void
  func makeUIView(context: Context) -> ListeningBuddyPaywallTouchSurface {
    let view = ListeningBuddyPaywallTouchSurface()
    view.onBegin = onBegin
    view.onEnd = onEnd
    return view
  }
  func updateUIView(_ view: ListeningBuddyPaywallTouchSurface, context: Context) {
    view.onBegin = onBegin
    view.onEnd = onEnd
  }
}
