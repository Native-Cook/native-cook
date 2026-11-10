import SwiftUI
import MetalKit

// The source shader already encodes sRGB into P3. Present those values directly,
// without an additional transfer or gamut conversion in the fragment program.
struct SunriseCocktailPourRenderer: UIViewRepresentable {
    let layout: SunriseCocktailPourLayout
    let frame: SunriseCocktailPourMotion.Frame
    let scale: CGFloat
    func makeCoordinator() -> SunriseCocktailPourPipeline { SunriseCocktailPourPipeline() }
    func makeUIView(context: Context) -> MTKView {
        let view=MTKView(frame:.zero,device:context.coordinator.device)
        view.colorPixelFormat = .bgra8Unorm
        view.framebufferOnly=true
        view.isPaused=true
        view.enableSetNeedsDisplay=true
        view.isUserInteractionEnabled=false
        view.isOpaque=true
        view.contentScaleFactor=scale
        (view.layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.displayP3)
        view.delegate=context.coordinator
        return view
    }
    func updateUIView(_ view: MTKView, context: Context) {
        context.coordinator.uniforms = [
            SIMD4(Float(layout.w),Float(layout.h),Float(layout.heroX),Float(layout.heroY)),
            SIMD4(Float(layout.heroS),Float(frame.shift),Float(frame.level),Float(frame.red)),
            SIMD4(Float(frame.rise),Float(frame.mix),Float(frame.stream),Float(frame.swirl)),
            SIMD4(Float(frame.phase),Float(1/scale),0,0)
        ]
        view.setNeedsDisplay()
    }
}
final class SunriseCocktailPourPipeline: NSObject, MTKViewDelegate {
    let device=MTLCreateSystemDefaultDevice()
    private var queue: MTLCommandQueue?
    private var pipeline: MTLRenderPipelineState?
    var uniforms=[SIMD4<Float>](repeating:.zero,count:4)
    override init() {
        super.init()
        guard let device else { assertionFailure("SunriseCocktailPour: Metal unavailable"); return }
        queue=device.makeCommandQueue()
        let descriptor=MTLRenderPipelineDescriptor()
        let library=device.makeDefaultLibrary()
        descriptor.vertexFunction=library?.makeFunction(name:"sunriseCocktailPourVertex")
        descriptor.fragmentFunction=library?.makeFunction(name:"sunriseCocktailPourFragment")
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        do { pipeline=try device.makeRenderPipelineState(descriptor:descriptor) }
        catch { assertionFailure("SunriseCocktailPour: \(error)") }
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard let pipeline, let pass=view.currentRenderPassDescriptor,
              let drawable=view.currentDrawable, let command=queue?.makeCommandBuffer(),
              let encoder=command.makeRenderCommandEncoder(descriptor:pass) else { return }
        encoder.setRenderPipelineState(pipeline)
        uniforms.withUnsafeBytes { encoder.setFragmentBytes($0.baseAddress!,length:$0.count,index:0) }
        encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }
}
