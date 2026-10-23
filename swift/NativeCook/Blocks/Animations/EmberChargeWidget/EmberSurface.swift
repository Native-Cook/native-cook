import SwiftUI

/// SwiftUI adapter for one transparent, block-local Metal surface.
struct EmberSurface: UIViewRepresentable {
  let frame: EmberFrame
  var isTube = false

  func makeUIView(context: Context) -> EmberSurfaceView {
    let view = EmberSurfaceView(frame: .zero)
    view.frameValue = frame
    view.isTube = isTube
    return view
  }

  func updateUIView(_ view: EmberSurfaceView, context: Context) {
    guard view.frameValue != frame else { return }
    view.frameValue = frame
    view.render()
  }
}
