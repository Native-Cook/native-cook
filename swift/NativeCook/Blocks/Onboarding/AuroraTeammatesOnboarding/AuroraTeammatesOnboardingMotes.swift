import MetalKit
import SwiftUI

struct AuroraTeammatesOnboardingMotes: UIViewRepresentable {
    let layout:AuroraTeammatesOnboardingTheme.Layout
    let scene:[Double]
    func makeCoordinator()->AuroraTeammatesOnboardingMotesRenderer { AuroraTeammatesOnboardingMotesRenderer() }
    func makeUIView(context:Context)->MTKView {
        let v=MTKView(frame:.zero,device:context.coordinator.device)
        v.colorPixelFormat = .bgra8Unorm;v.isOpaque=false;v.backgroundColor = .clear;v.clearColor=MTLClearColorMake(0,0,0,0)
        (v.layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.sRGB)
        v.isPaused=true;v.enableSetNeedsDisplay=true;v.autoResizeDrawable=false;v.isUserInteractionEnabled=false;v.delegate=context.coordinator
        return v
    }
    func updateUIView(_ v:MTKView,context:Context) {
        context.coordinator.layout=layout;context.coordinator.scene=scene
        let ratio=min(UIScreen.main.scale,2)
        v.drawableSize=CGSize(width:(layout.W*ratio).rounded(),height:(layout.H*ratio).rounded());v.setNeedsDisplay()
    }
}
