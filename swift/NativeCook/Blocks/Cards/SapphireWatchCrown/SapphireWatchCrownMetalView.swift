import MetalKit
import SwiftUI

struct SapphireWatchCrownMetalView: UIViewRepresentable {
  var layout: SapphireWatchCrownLayout
  var sample: SapphireWatchCrownClock.Sample
  func makeCoordinator() -> SapphireWatchCrownRenderer { SapphireWatchCrownRenderer() }
  func makeUIView(context: Context) -> MTKView {
    let view=SapphireWatchCrownRenderer.Surface(frame:.zero,device:context.coordinator.device)
    view.isOpaque=false; view.backgroundColor = .clear
    view.clearColor=MTLClearColorMake(0,0,0,0)
    view.colorPixelFormat = .bgra8Unorm
    view.isPaused=true; view.enableSetNeedsDisplay=false
    view.isUserInteractionEnabled=false; view.isAccessibilityElement=false
    view.autoResizeDrawable=true
    (view.layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.sRGB)
    context.coordinator.view=view; view.renderer=context.coordinator
    return view
  }
  func updateUIView(_ view: MTKView,context: Context) { context.coordinator.draw(layout,sample) }
}
