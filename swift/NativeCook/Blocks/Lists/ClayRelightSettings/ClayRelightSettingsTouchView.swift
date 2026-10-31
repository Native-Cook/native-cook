import SwiftUI
import UIKit

struct ClayRelightSettingsTouchView: UIViewRepresentable {
  var onEvent: (UIGestureRecognizer.State, CGSize, CGVector) -> Void
  func makeUIView(context: Context) -> ClayRelightSettingsTouchSurface {
    let view = ClayRelightSettingsTouchSurface(); view.onEvent = onEvent; return view
  }
  func updateUIView(_ view: ClayRelightSettingsTouchSurface, context: Context) { view.onEvent = onEvent }
}
