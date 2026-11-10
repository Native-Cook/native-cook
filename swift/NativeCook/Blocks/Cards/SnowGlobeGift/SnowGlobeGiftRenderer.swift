import MetalKit
import SwiftUI

struct SnowGlobeGiftRenderer: UIViewRepresentable {
    var layout: SnowGlobeGiftLayout
    var frame: SnowGlobeGiftMotion.Frame
    var scale: Double

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: MTLCreateSystemDefaultDevice())
        view.isOpaque = true
        view.isPaused = true
        view.enableSetNeedsDisplay = true
        view.framebufferOnly = true
        view.colorPixelFormat = .bgra8Unorm
        (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.displayP3)
        view.contentScaleFactor = scale
        view.isUserInteractionEnabled = false
        context.coordinator.configure(view)
        view.delegate = context.coordinator
        return view
    }
    func makeCoordinator() -> SnowGlobeGiftMetalDriver { SnowGlobeGiftMetalDriver() }
    func updateUIView(_ view: MTKView, context: Context) {
        context.coordinator.uniforms = [
            SIMD4(Float(layout.w),Float(layout.h),Float(layout.heroX),Float(layout.heroY)),
            SIMD4(Float(layout.heroS),Float(frame.wobble),Float(frame.swirl),Float(frame.spin)),
            SIMD4(Float(frame.lid),Float(frame.glow),Float(1/scale),Float(scale))
        ]
        view.setNeedsDisplay()
    }
}

final class SnowGlobeGiftMetalDriver: NSObject, MTKViewDelegate {
    var uniforms = [SIMD4<Float>](repeating: .zero,count: 3)
    private var queue: MTLCommandQueue?
    private var pipeline: MTLRenderPipelineState?
    func configure(_ view: MTKView) {
        guard let device = view.device, let library = device.makeDefaultLibrary() else { return }
        queue = device.makeCommandQueue()
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: "snowGlobeGiftVertex")
        descriptor.fragmentFunction = library.makeFunction(name: "snowGlobeGiftFragment")
        descriptor.colorAttachments[0].pixelFormat = view.colorPixelFormat
        do { pipeline = try device.makeRenderPipelineState(descriptor: descriptor) }
        catch { assertionFailure("SnowGlobeGift: Metal pipeline failed: \(error)") }
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard let pipeline, let pass = view.currentRenderPassDescriptor,
              let drawable = view.currentDrawable, let buffer = queue?.makeCommandBuffer(),
              let encoder = buffer.makeRenderCommandEncoder(descriptor: pass) else { return }
        encoder.setRenderPipelineState(pipeline)
        uniforms.withUnsafeBytes { bytes in
            if let base = bytes.baseAddress { encoder.setFragmentBytes(base,length: bytes.count,index: 0) }
        }
        encoder.drawPrimitives(type: .triangle,vertexStart: 0,vertexCount: 3)
        encoder.endEncoding(); buffer.present(drawable); buffer.commit()
    }
}
