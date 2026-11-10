import SwiftUI
import MetalKit

struct PasskeyGlassSealMetalView: UIViewRepresentable {
    var layout: PasskeyGlassSealTheme.Layout
    var sample: PasskeyGlassSealClock.Sample
    var reduced = false
    func makeUIView(context: Context) -> MTKView {
        let v = MTKView(frame:.zero,device:context.coordinator.device)
        v.colorPixelFormat = .bgra8Unorm
        (v.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name:CGColorSpace.sRGB)
        v.isOpaque = false; v.backgroundColor = .clear; v.clearColor = MTLClearColorMake(0,0,0,0)
        v.isPaused = true; v.enableSetNeedsDisplay = false; v.isUserInteractionEnabled = false
        v.delegate = context.coordinator
        return v
    }
    func updateUIView(_ view: MTKView, context: Context) {
        let pr = view.window?.screen.scale ?? view.traitCollection.displayScale
        let target = CGSize(width:(layout.cw*pr).rounded(),height:(layout.ch*pr).rounded())
        if view.drawableSize != target { view.drawableSize = target }
        let f = sample.frame
        let scanY = layout.py+60*sin(2 * .pi*sample.scanTime/2.5+asin(26.0/60))*layout.s
        let values = [layout.cw,layout.ch,pr,sample.time,layout.px,layout.py,layout.s,scanY,
                      f.light,f.scan,reduced ? 1 : 0,0,f.unwind,f.key,PasskeyGlassSealMotion.turnAt(f.turn),0]
        context.coordinator.uniforms = values.map(Float.init)
        if reduced { context.coordinator.prepareMask(layout,pr) }
        view.draw()
    }
    func makeCoordinator() -> PasskeyGlassSealRenderer { PasskeyGlassSealRenderer() }
}
