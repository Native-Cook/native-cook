import MetalKit
import SwiftUI

struct SerumDropperRoutineMetal: UIViewRepresentable {
    let layout: SerumDropperRoutineLayout
    let frame: SerumDropperRoutineMotion.Frame
    let tip: CGPoint
    let time: Double
    func makeCoordinator() -> SerumDropperRoutineRenderer { SerumDropperRoutineRenderer() }
    func makeUIView(context: Context) -> MTKView {
        let view = SerumDropperRoutineMetalView(frame:.zero,device:context.coordinator.device)
        view.isOpaque = false; view.backgroundColor = .clear
        view.clearColor = MTLClearColorMake(0,0,0,0)
        view.colorPixelFormat = .bgra8Unorm
        view.isPaused = true; view.enableSetNeedsDisplay = false
        view.framebufferOnly = true; view.isUserInteractionEnabled = false
        view.delegate = context.coordinator
        return view
    }
    func updateUIView(_ view: MTKView,context: Context) {
        context.coordinator.uniforms = [
            SIMD4(Float(layout.w),Float(layout.h),Float(view.contentScaleFactor),Float(time.truncatingRemainder(dividingBy:600))),
            SIMD4(Float(tip.x),Float(tip.y),Float(layout.floor),Float(frame.soak)),
            SIMD4(Float(frame.drops[0]),Float(frame.drops[1]),Float(frame.drops[2]),Float(layout.sx)),
            SIMD4(Float(layout.liftX),0,0,0)
        ]
        view.draw()
    }
}
final class SerumDropperRoutineRenderer: NSObject,MTKViewDelegate {
    let device = MTLCreateSystemDefaultDevice()
    private var queue: MTLCommandQueue?
    private var pipeline: MTLRenderPipelineState?
    var uniforms = [SIMD4<Float>](repeating:.zero,count:4)
    override init() {
        super.init()
        guard let device else { return }
        queue = device.makeCommandQueue()
        let library = device.makeDefaultLibrary()
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library?.makeFunction(name:"serumDropperRoutineFullVs")
        descriptor.fragmentFunction = library?.makeFunction(name:"serumDropperRoutineOilFs")
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        let blend = descriptor.colorAttachments[0]!
        blend.isBlendingEnabled = true
        blend.sourceRGBBlendFactor = .one; blend.destinationRGBBlendFactor = .oneMinusSourceAlpha
        blend.sourceAlphaBlendFactor = .one; blend.destinationAlphaBlendFactor = .oneMinusSourceAlpha
        do { pipeline = try device.makeRenderPipelineState(descriptor:descriptor) }
        catch { assertionFailure("SerumDropperRoutine Metal pipeline: \(error)") }
    }
    func mtkView(_ view: MTKView,drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        guard let pipeline,let pass = view.currentRenderPassDescriptor,let drawable = view.currentDrawable,
              let command = queue?.makeCommandBuffer(),let encoder = command.makeRenderCommandEncoder(descriptor:pass) else { return }
        encoder.setRenderPipelineState(pipeline)
        uniforms.withUnsafeBytes { encoder.setFragmentBytes($0.baseAddress!,length:64,index:0) }
        encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
        encoder.endEncoding(); command.present(drawable); command.commit()
    }
}

// A pinned frame is submitted before SwiftUI gives the representable its bounds.
// Draw again after layout so that the first drawable receives the same uniforms.
final class SerumDropperRoutineMetalView: MTKView {
    override func layoutSubviews() {
        super.layoutSubviews()
        if bounds.width > 0 && bounds.height > 0 { draw() }
    }
}
