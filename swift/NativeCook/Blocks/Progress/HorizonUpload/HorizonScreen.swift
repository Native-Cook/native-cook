import SwiftUI

/// The lifecycle bridge leaves frame work in the native owner, outside SwiftUI invalidation.
struct HorizonScreen: UIViewRepresentable {
  let reduced: Bool
  let opaque: Bool
  let fontScale: Double
  func makeUIView(context: Context) -> HorizonHost { HorizonHost() }
  func updateUIView(_ view: HorizonHost, context: Context) {
    view.fontScale = fontScale
    view.upload.preferences(reduced: reduced, opaque: opaque)
  }
  static func dismantleUIView(_ view: HorizonHost, coordinator: ()) { view.stopClock() }
}
