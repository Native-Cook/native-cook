// The stationary page canvas only follows layout, never the animation drive.
import SwiftUI

struct AuraPageSurface: UIViewRepresentable {
  let width: Double
  let origin: CGPoint
  let onDrawn: () -> Void

  func makeUIView(context: Context) -> AuraSurfaceView {
    let view = AuraSurfaceView()
    view.isPage = true
    view.cardWidth = width
    view.cardOrigin = origin
    view.onShown = { _ in onDrawn() }
    return view
  }

  func updateUIView(_ view: AuraSurfaceView, context: Context) {
    guard view.cardWidth != width || view.cardOrigin != origin else { return }
    view.cardWidth = width
    view.cardOrigin = origin
    view.setNeedsLayout()
  }
}
