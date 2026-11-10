import MetalKit
import SwiftUI

// Match Skia's Display P3 render target. The shader already converts sRGB to P3.
struct MessageBottleShareSea: UIViewRepresentable {
    let layout: MessageBottleShareMotion.Layout
    let frame: MessageBottleShareMotion.Frame
    let time: Double
    struct MessageBottleShareUniforms {
        var res: SIMD2<Float>
        var horizon: Float
        var time: Float
        var position: SIMD2<Float>
        var angle: Float
        var scale: Float
        var floating: Float
        var alpha: Float
        var splash: SIMD3<Float>
        var loop: Float
        var pixel: Float
    }
    final class MessageBottleShareRenderer: NSObject, MTKViewDelegate {
        let device = MTLCreateSystemDefaultDevice()
        var queue: MTLCommandQueue?
        var pipeline: MTLRenderPipelineState?
        var sample: (MessageBottleShareMotion.Layout, MessageBottleShareMotion.Frame, Double)?
        override init() {
            super.init()
            guard let device, let library = device.makeDefaultLibrary() else {
                assertionFailure("Message Bottle Share: Metal library unavailable")
                return
            }
            queue = device.makeCommandQueue()
            let descriptor = MTLRenderPipelineDescriptor()
            descriptor.vertexFunction = library.makeFunction(name: "messageBottleShareVertex")
            descriptor.fragmentFunction = library.makeFunction(name: "messageBottleShareFragment")
            descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
            do { pipeline = try device.makeRenderPipelineState(descriptor: descriptor) }
            catch { assertionFailure("Message Bottle Share: Metal pipeline failed: \(error)") }
        }
        func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) { }
        func draw(in view: MTKView) {
            guard let sample else { return }
            draw(view, sample.0, sample.1, sample.2)
        }
        func draw(_ view: MTKView, _ layout: MessageBottleShareMotion.Layout,
                  _ frame: MessageBottleShareMotion.Frame, _ time: Double) {
            guard let pipeline, let drawable = view.currentDrawable,
                  let descriptor = view.currentRenderPassDescriptor,
                  let command = queue?.makeCommandBuffer(),
                  let encoder = command.makeRenderCommandEncoder(descriptor: descriptor) else { return }
            let b = frame.bottle
            var uniforms = MessageBottleShareUniforms(
                res: SIMD2(Float(layout.w), Float(layout.h)), horizon: Float(layout.horizon), time: Float(time),
                position: SIMD2(Float(b.x), Float(b.y)), angle: Float(b.angle), scale: Float(b.scale),
                floating: Float(b.floating), alpha: Float(b.alpha),
                splash: SIMD3(Float(layout.landX), Float(layout.landY), Float(frame.splash)),
                loop: 10, pixel: Float(layout.w / view.drawableSize.width))
            encoder.setRenderPipelineState(pipeline)
            encoder.setFragmentBytes(&uniforms, length: MemoryLayout<MessageBottleShareUniforms>.stride, index: 0)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
            encoder.endEncoding()
            command.present(drawable)
            command.commit()
        }
    }
    func makeCoordinator() -> MessageBottleShareRenderer { MessageBottleShareRenderer() }
    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: context.coordinator.device)
        view.colorPixelFormat = .bgra8Unorm
        (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.displayP3)
        view.isPaused = true
        view.enableSetNeedsDisplay = true
        view.delegate = context.coordinator
        view.isOpaque = true
        view.isUserInteractionEnabled = false
        view.autoResizeDrawable = false
        return view
    }
    func updateUIView(_ view: MTKView, context: Context) {
        let scale = view.window?.screen.scale ?? UIScreen.main.scale
        let drawableSize = CGSize(width: layout.w*scale, height: layout.h*scale)
        if view.drawableSize != drawableSize { view.drawableSize = drawableSize }
        context.coordinator.sample = (layout, frame, time)
        view.setNeedsDisplay()
    }
}
