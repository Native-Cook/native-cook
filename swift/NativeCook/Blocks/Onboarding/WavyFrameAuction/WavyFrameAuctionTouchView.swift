import SwiftUI
import UIKit

struct WavyFrameAuctionTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State, Double, Double) -> Void
  func makeUIView(context: Context) -> WavyFrameAuctionTouchSurface {
    let view = WavyFrameAuctionTouchSurface(); view.onEvent = onEvent; return view
  }
  func updateUIView(_ view: WavyFrameAuctionTouchSurface, context: Context) { view.onEvent = onEvent }
}
