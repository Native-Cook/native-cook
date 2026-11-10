import MetalKit
import SwiftUI

struct BellPermissionPrimerGlass: UIViewRepresentable {
    let layout:BellPermissionPrimerLayout
    let frame:BellPermissionPrimerMotion.Frame
    let time:Double
    func makeCoordinator() -> BellPermissionPrimerRenderer { BellPermissionPrimerRenderer() }
    func makeUIView(context:Context) -> MTKView {
        let view=MTKView(frame:.zero,device:context.coordinator.device)
        view.colorPixelFormat = .bgra8Unorm
        view.isPaused=true; view.enableSetNeedsDisplay=true
        view.framebufferOnly=true; view.isOpaque=false; view.backgroundColor = .clear
        view.clearColor=MTLClearColorMake(0,0,0,0)
        view.isUserInteractionEnabled=false; view.autoResizeDrawable=true
        (view.layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.sRGB)
        view.delegate=context.coordinator
        return view
    }
    func updateUIView(_ view:MTKView,context:Context) {
        context.coordinator.values=layout.uniforms(frame,time,Double(view.contentScaleFactor))
        view.setNeedsDisplay()
    }
}
final class BellPermissionPrimerRenderer:NSObject,MTKViewDelegate {
    let device=MTLCreateSystemDefaultDevice()
    private var hero:MTLRenderPipelineState?, spark:MTLRenderPipelineState?, compute:MTLComputePipelineState?
    private var queue:MTLCommandQueue?, beads:MTLBuffer?
    var values=[SIMD4<Float>](repeating:.zero,count:8)
    override init() {
        super.init()
        guard let device else { return }
        queue=device.makeCommandQueue(); beads=device.makeBuffer(length:260*16,options:.storageModeShared)
        let library=device.makeDefaultLibrary()
        func render(_ vs:String,_ fs:String) -> MTLRenderPipelineState? {
            let d=MTLRenderPipelineDescriptor()
            d.vertexFunction=library?.makeFunction(name:vs); d.fragmentFunction=library?.makeFunction(name:fs)
            let a=d.colorAttachments[0]!
            a.pixelFormat = .bgra8Unorm; a.isBlendingEnabled=true
            a.sourceRGBBlendFactor = .one; a.destinationRGBBlendFactor = .oneMinusSourceAlpha
            a.sourceAlphaBlendFactor = .one; a.destinationAlphaBlendFactor = .oneMinusSourceAlpha
            do { return try device.makeRenderPipelineState(descriptor:d) }
            catch { assertionFailure("Bell Permission Primer Metal: \(error)"); return nil }
        }
        hero=render("bellPermissionPrimerFullVs","bellPermissionPrimerHeroFs")
        spark=render("bellPermissionPrimerSparkVs","bellPermissionPrimerSparkFs")
        if let fn=library?.makeFunction(name:"bellPermissionPrimerSparksCs") { compute=try? device.makeComputePipelineState(function:fn) }
    }
    func mtkView(_ view:MTKView,drawableSizeWillChange size:CGSize) {}
    func draw(in view:MTKView) {
        guard let hero,let pass=view.currentRenderPassDescriptor,let drawable=view.currentDrawable,let command=queue?.makeCommandBuffer() else { return }
        let bytes=MemoryLayout<SIMD4<Float>>.stride*values.count
        let taking=values[2].z>0.5 && values[2].w<0.999
        if taking,let compute,let beads,let encoder=command.makeComputeCommandEncoder() {
            encoder.setComputePipelineState(compute)
            encoder.setBytes(values,length:bytes,index:0); encoder.setBuffer(beads,offset:0,index:1)
            encoder.dispatchThreadgroups(MTLSize(width:5,height:1,depth:1),threadsPerThreadgroup:MTLSize(width:64,height:1,depth:1))
            encoder.endEncoding()
        }
        if let encoder=command.makeRenderCommandEncoder(descriptor:pass) {
            encoder.setRenderPipelineState(hero); encoder.setFragmentBytes(values,length:bytes,index:0)
            encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
            if taking,let spark,let beads {
                encoder.setRenderPipelineState(spark); encoder.setVertexBytes(values,length:bytes,index:0)
                encoder.setVertexBuffer(beads,offset:0,index:1)
                encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:6,instanceCount:260)
            }
            encoder.endEncoding()
        }
        command.present(drawable); command.commit()
    }
}
