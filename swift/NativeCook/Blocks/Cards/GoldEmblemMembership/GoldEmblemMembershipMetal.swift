import MetalKit
import SwiftUI

struct GoldEmblemMembershipMetal: UIViewRepresentable {
    let layout: GoldEmblemMembershipTheme.Layout
    let frame: GoldEmblemMembershipMotion.Frame
    let time, scale: Double
    let reduced: Bool
    func makeCoordinator() -> GoldEmblemMembershipRenderer { GoldEmblemMembershipRenderer() }
    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: context.coordinator.device)
        view.colorPixelFormat = .bgra8Unorm
        (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
        view.isOpaque = false; view.backgroundColor = .clear
        view.clearColor = MTLClearColorMake(0, 0, 0, 0)
        view.isPaused = true; view.enableSetNeedsDisplay = true
        view.autoResizeDrawable = true; view.isUserInteractionEnabled = false
        view.contentScaleFactor = scale; view.delegate = context.coordinator
        return view
    }
    func updateUIView(_ view: MTKView, context: Context) {
        let l = layout, s = frame
        context.coordinator.uniforms = [
            SIMD4(Float(l.w), Float(l.card.height+60), Float(scale), Float(time.truncatingRemainder(dividingBy: 600))),
            SIMD4(Float(l.card.minX), 10, Float(l.card.width), Float(l.card.height)),
            SIMD4(Float(s.from), Float(s.to), reduced ? 0 : Float(s.cast), Float(s.side)),
            SIMD4(Float(s.drag/60), 0, 0, 0)
        ]
        view.contentScaleFactor = scale
        view.setNeedsDisplay()
    }
}
