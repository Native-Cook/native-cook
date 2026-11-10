import MetalKit
import SwiftUI

struct FilamentBulbDimmerMetal: UIViewRepresentable {
    let layout:FilamentBulbDimmerTheme.Layout
    let frame:FilamentBulbDimmerMotion.Frame
    let scale:CGFloat
    var reduced=false
    var pinned:Double?=nil
    func makeCoordinator()->FilamentBulbDimmerRenderer { FilamentBulbDimmerRenderer() }
    func makeUIView(context:Context)->MTKView {
        let v=MTKView(frame:.zero,device:context.coordinator.device)
        v.colorPixelFormat = .bgra8Unorm
        (v.layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.sRGB)
        v.isOpaque=true; v.isPaused=true; v.enableSetNeedsDisplay=true; v.isUserInteractionEnabled=false
        v.contentScaleFactor=scale; v.delegate=context.coordinator; return v
    }
    func updateUIView(_ view:MTKView,context:Context) {
        context.coordinator.layout=layout; context.coordinator.frame=frame; context.coordinator.reduced=reduced; context.coordinator.pinned=pinned
        view.contentScaleFactor=scale; view.setNeedsDisplay()
    }
}
