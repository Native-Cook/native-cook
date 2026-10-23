import Metal
import MetalKit
import SwiftUI

struct BeamCheckoutStepperMetalView: UIViewRepresentable {
    let frame: [Double]
    let clock: Double
    let still: Bool

    func makeCoordinator() -> BeamCheckoutStepperRenderer { BeamCheckoutStepperRenderer() }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: CGRect(x: 0, y: 0, width: 402, height: 874))
        view.device = MTLCreateSystemDefaultDevice()
        view.colorPixelFormat = .bgra8Unorm
        view.clearColor = MTLClearColorMake(0, 0, 0, 1)
        view.framebufferOnly = true
        view.isPaused = true
        view.enableSetNeedsDisplay = true
        view.preferredFramesPerSecond = 60
        view.contentScaleFactor = UIScreen.main.scale
        context.coordinator.configure(view)
        return view
    }

    func updateUIView(_ view: MTKView, context: Context) {
        context.coordinator.set(frame: frame, clock: clock, still: still)
        view.draw()
    }
}

final class BeamCheckoutStepperRenderer: NSObject, MTKViewDelegate {
    private var device: MTLDevice?
    private var queue: MTLCommandQueue?
    private var pipeline: MTLRenderPipelineState?
    private var uniforms = [Float](repeating: 0, count: 99)

    func configure(_ view: MTKView) {
        guard let device = view.device,
              let library = device.makeDefaultLibrary(),
              let vertex = library.makeFunction(name: "beamCheckoutStepperVertex"),
              let fragment = library.makeFunction(name: "beamCheckoutStepperFragment") else { return }
        self.device = device
        queue = device.makeCommandQueue()
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = vertex
        descriptor.fragmentFunction = fragment
        descriptor.colorAttachments[0].pixelFormat = view.colorPixelFormat
        pipeline = try? device.makeRenderPipelineState(descriptor: descriptor)
        view.delegate = self
    }

    func set(frame: [Double], clock: Double, still: Bool) {
        for i in 0..<min(97, frame.count) { uniforms[i] = Float(frame[i]) }
        uniforms[97] = Float(clock)
        uniforms[98] = still ? 1 : 0
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard let queue, let pipeline, let drawable = view.currentDrawable,
              let pass = view.currentRenderPassDescriptor,
              let command = queue.makeCommandBuffer(),
              let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        encoder.setRenderPipelineState(pipeline)
        uniforms.withUnsafeBytes { bytes in
            encoder.setFragmentBytes(bytes.baseAddress!, length: bytes.count, index: 0)
        }
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }
}
