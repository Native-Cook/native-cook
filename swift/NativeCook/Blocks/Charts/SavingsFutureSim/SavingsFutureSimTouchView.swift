import SwiftUI
import UIKit

// Zero-delay long press survives the gallery's simultaneous interactive back gesture.
struct SavingsFutureSimTouchView: UIViewRepresentable {
  var action: (Int, CGPoint) -> Void
  func makeUIView(context: Context) -> SavingsFutureSimTouchSurface {
    let view = SavingsFutureSimTouchSurface()
    view.action = action
    return view
  }
  func updateUIView(_ view: SavingsFutureSimTouchSurface, context: Context) { view.action = action }
}
