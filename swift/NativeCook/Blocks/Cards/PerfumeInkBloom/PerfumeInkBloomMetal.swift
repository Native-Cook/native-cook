import MetalKit
import SwiftUI

struct PerfumeInkBloomMetal: UIViewRepresentable {
  var layout: PerfumeInkBloomTheme.Layout
  var frame: PerfumeInkBloomMotion.Frame
  func makeUIView(context: Context) -> PerfumeInkBloomMetalView { PerfumeInkBloomMetalView() }
  func updateUIView(_ view: PerfumeInkBloomMetalView, context: Context) { view.render(layout,frame) }
}

final class PerfumeInkBloomMetalView: MTKView {
  private static let gpu=MTLCreateSystemDefaultDevice()!
  private static let library=gpu.makeDefaultLibrary()!
  private static func pipeline(_ vertex: String,_ fragment: String) -> MTLRenderPipelineState {
    let d=MTLRenderPipelineDescriptor()
    d.vertexFunction=library.makeFunction(name:vertex)
    d.fragmentFunction=library.makeFunction(name:fragment)
    let c=d.colorAttachments[0]!
    c.pixelFormat = .bgra8Unorm; c.isBlendingEnabled=true
    c.sourceRGBBlendFactor = .one; c.destinationRGBBlendFactor = .oneMinusSourceAlpha
    c.sourceAlphaBlendFactor = .one; c.destinationAlphaBlendFactor = .oneMinusSourceAlpha
    return try! gpu.makeRenderPipelineState(descriptor:d)
  }
  private static let bottle=pipeline("perfumeInkBloomFullVs","perfumeInkBloomBottleFs")
  private static let drop=pipeline("perfumeInkBloomDropVs","perfumeInkBloomDropFs")
  private static let compute=try! gpu.makeComputePipelineState(function:library.makeFunction(name:"perfumeInkBloomMistCs")!)
  private let queue=gpu.makeCommandQueue()!
  private let drops=gpu.makeBuffer(length:6144*32,options:.storageModePrivate)!
  private var notes: MTLBuffer?
  private var oldLayout: PerfumeInkBloomTheme.Layout?
  private var uniforms=[SIMD4<Float>](repeating:.zero,count:4)
  init() {
    super.init(frame:.zero,device:Self.gpu)
    colorPixelFormat = .bgra8Unorm
    (layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.sRGB)
    clearColor=MTLClearColorMake(0,0,0,0)
    isOpaque=false; backgroundColor = .clear; isUserInteractionEnabled=false
    framebufferOnly=true; isPaused=true; enableSetNeedsDisplay=true
  }
  required init(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
  func render(_ l: PerfumeInkBloomTheme.Layout,_ s: PerfumeInkBloomMotion.Frame) {
    if l != oldLayout {
      oldLayout=l
      let points=PerfumeInkBloomResources.shared.notes(l)
      notes=Self.gpu.makeBuffer(bytes:points,length:points.count*MemoryLayout<SIMD2<Float>>.stride,options:.storageModeShared)
    }
    uniforms[0] = .init(Float(l.w),Float(l.h),Float(contentScaleFactor),0)
    uniforms[1] = .init(Float(l.cx),Float(l.cy),Float(l.sx),Float(s.tilt))
    uniforms[2] = .init(Float(s.slosh),Float(s.press),0.375,0)
    uniforms[3] = .init(Float(s.age),Float(s.condense),Float(s.evaporate),0)
    setNeedsDisplay()
  }
  override func draw(_ rect: CGRect) {
    guard let notes, let pass=currentRenderPassDescriptor, let drawable=currentDrawable, let command=queue.makeCommandBuffer() else { return }
    let bytes=MemoryLayout<SIMD4<Float>>.stride*4
    if let c=command.makeComputeCommandEncoder() {
      c.setComputePipelineState(Self.compute)
      c.setBytes(&uniforms,length:bytes,index:0)
      c.setBuffer(drops,offset:0,index:1); c.setBuffer(notes,offset:0,index:2)
      c.dispatchThreadgroups(MTLSize(width:96,height:1,depth:1),threadsPerThreadgroup:MTLSize(width:64,height:1,depth:1))
      c.endEncoding()
    }
    guard let e=command.makeRenderCommandEncoder(descriptor:pass) else { return }
    e.setRenderPipelineState(Self.bottle)
    e.setFragmentBytes(&uniforms,length:bytes,index:0)
    e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
    e.setRenderPipelineState(Self.drop)
    e.setVertexBytes(&uniforms,length:bytes,index:0); e.setVertexBuffer(drops,offset:0,index:1)
    e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:6,instanceCount:6144)
    e.endEncoding(); command.present(drawable); command.commit()
  }
}
