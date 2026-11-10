import MetalKit
import SwiftUI

// A full-screen fragment pass preserves the reference's ray marcher and P3 output.
struct ChampagneRsvpGlassView: UIViewRepresentable {
    let layout: ChampagneRsvpLayout
    let frame: ChampagneRsvpMotion.Frame
    struct ChampagneRsvpUniforms {
        var uRes: SIMD2<Float>, uL: SIMD2<Float>, uR: SIMD2<Float>
        var uS: Float
        var uRot: SIMD2<Float>
        var uClink: Float
        var uClinkAt: SIMD2<Float>
        var uBurst: Float, uPhase: Float, uPx: Float, scale: Float
    }
    final class ChampagneRsvpRenderer: NSObject, MTKViewDelegate {
        let queue: MTLCommandQueue
        let pipeline: MTLRenderPipelineState
        var uniforms: ChampagneRsvpUniforms?
        init(_ device: MTLDevice) {
            queue = device.makeCommandQueue()!
            let library = device.makeDefaultLibrary()!
            let desc = MTLRenderPipelineDescriptor()
            desc.vertexFunction = library.makeFunction(name: "champagneRsvpVertex")
            desc.fragmentFunction = library.makeFunction(name: "champagneRsvpFragment")
            desc.colorAttachments[0].pixelFormat = .bgra8Unorm
            do { pipeline = try device.makeRenderPipelineState(descriptor: desc) }
            catch { fatalError("Champagne RSVP shader pipeline: \(error)") }
            super.init()
        }
        func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
        func draw(in view: MTKView) {
            guard var u = uniforms, let pass = view.currentRenderPassDescriptor,
                  let drawable = view.currentDrawable, let command = queue.makeCommandBuffer(),
                  let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
            encoder.setRenderPipelineState(pipeline)
            encoder.setFragmentBytes(&u, length: MemoryLayout<ChampagneRsvpUniforms>.stride, index: 0)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
            encoder.endEncoding()
            command.present(drawable)
            command.commit()
        }
    }
    func makeCoordinator() -> ChampagneRsvpRenderer { ChampagneRsvpRenderer(MTLCreateSystemDefaultDevice()!) }
    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: context.coordinator.queue.device)
        view.colorPixelFormat = .bgra8Unorm
        (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.displayP3)
        view.isPaused = true
        view.enableSetNeedsDisplay = true
        view.isUserInteractionEnabled = false
        view.delegate = context.coordinator
        return view
    }
    func updateUIView(_ view: MTKView, context: Context) {
        let l = layout, f = frame
        let scale = view.window?.screen.scale ?? UIScreen.main.scale
        view.contentScaleFactor = scale
        let drawableSize = CGSize(width: l.w * scale, height: l.h * scale)
        if view.drawableSize != drawableSize { view.drawableSize = drawableSize }
        context.coordinator.uniforms = ChampagneRsvpUniforms(
            uRes: SIMD2(Float(l.w), Float(l.h)), uL: SIMD2(Float(l.left), Float(l.feetY)),
            uR: SIMD2(Float(l.right), Float(l.feetY)), uS: Float(l.heroS),
            uRot: SIMD2(Float(f.lean), Float(-f.lean)), uClink: Float(f.clink),
            uClinkAt: SIMD2(Float(l.w / 2), Float(l.feetY - cos(f.lean) * 2.62 * l.heroS)),
            uBurst: Float(f.burst), uPhase: Float(f.phase), uPx: Float(1 / scale), scale: Float(scale))
        view.setNeedsDisplay()
    }
}
