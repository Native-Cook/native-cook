import SwiftUI
import MetalKit

struct WindChimeTonesMetal: UIViewRepresentable {
    var layout: WindChimeTonesLayout
    var frame: WindChimeTonesMotion.Frame
    var reduced: Bool
    func makeCoordinator() -> WindChimeTonesRenderer { WindChimeTonesRenderer() }
    func makeUIView(context: Context) -> MTKView {
        let view=MTKView(frame:.zero,device:context.coordinator.device)
        view.colorPixelFormat = .bgra8Unorm
        (view.layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.sRGB)
        view.isPaused=true; view.enableSetNeedsDisplay=true
        view.isOpaque=true; view.framebufferOnly=true
        view.delegate=context.coordinator
        return view
    }
    func updateUIView(_ view: MTKView, context: Context) {
        let ratio=view.window?.screen.scale ?? UIScreen.main.scale
        var rings=WindChimeTonesMotion.ringsAt(frame.music,frame.cut,frame.laps)
        if reduced { for i in stride(from:1,to:rings.count,by:2) { rings[i] *= 0.4 } }
        context.coordinator.uniforms=([layout.w,layout.h,ratio,frame.time,layout.cx,layout.cy,layout.radius,frame.kick,frame.power,frame.glow,0,0]+rings).map(Float.init)
        view.setNeedsDisplay()
    }
}

final class WindChimeTonesRenderer: NSObject, MTKViewDelegate {
    let device=MTLCreateSystemDefaultDevice()
    private var queue: MTLCommandQueue?
    private var pipeline: MTLRenderPipelineState?
    var uniforms=[Float](repeating:0,count:28)
    override init() {
        super.init()
        guard let device else { return }
        queue=device.makeCommandQueue()
        guard let library=device.makeDefaultLibrary() else { assertionFailure("WindChimeTones Metal library missing"); return }
        let descriptor=MTLRenderPipelineDescriptor()
        descriptor.vertexFunction=library.makeFunction(name:"windChimeTonesVertex")
        descriptor.fragmentFunction=library.makeFunction(name:"windChimeTonesFragment")
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        descriptor.colorAttachments[0].isBlendingEnabled=true
        descriptor.colorAttachments[0].sourceRGBBlendFactor = .one
        descriptor.colorAttachments[0].destinationRGBBlendFactor = .oneMinusSourceAlpha
        descriptor.colorAttachments[0].sourceAlphaBlendFactor = .one
        descriptor.colorAttachments[0].destinationAlphaBlendFactor = .oneMinusSourceAlpha
        do { pipeline=try device.makeRenderPipelineState(descriptor:descriptor) }
        catch { assertionFailure("WindChimeTones pipeline: \(error)") }
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) { view.setNeedsDisplay() }
    func draw(in view: MTKView) {
        guard let pipeline, let pass=view.currentRenderPassDescriptor,
              let drawable=view.currentDrawable, let command=queue?.makeCommandBuffer(),
              let encoder=command.makeRenderCommandEncoder(descriptor:pass) else { return }
        pass.colorAttachments[0].clearColor=MTLClearColorMake(0,0,0,0)
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(&uniforms,length:112,index:0)
        encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
        encoder.endEncoding(); command.present(drawable); command.commit()
    }
}
