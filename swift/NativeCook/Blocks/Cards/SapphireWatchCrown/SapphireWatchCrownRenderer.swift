import MetalKit

final class SapphireWatchCrownRenderer {
  struct Uniforms { var view, watch, hands, metal: SIMD4<Float> }
  final class Surface: MTKView {
    weak var renderer: SapphireWatchCrownRenderer?
    override func layoutSubviews() { super.layoutSubviews(); renderer?.redraw() }
    override func didMoveToWindow() { super.didMoveToWindow(); renderer?.redraw() }
  }
  let device=MTLCreateSystemDefaultDevice()!
  private let queue: MTLCommandQueue
  private let pipeline: MTLRenderPipelineState
  weak var view: MTKView?
  private var latest: (SapphireWatchCrownLayout,SapphireWatchCrownClock.Sample)?
  init() {
    queue=device.makeCommandQueue()!
    let library=device.makeDefaultLibrary()!
    let d=MTLRenderPipelineDescriptor()
    d.vertexFunction=library.makeFunction(name:"sapphireWatchCrownVertex")
    d.fragmentFunction=library.makeFunction(name:"sapphireWatchCrownFragment")
    d.colorAttachments[0].pixelFormat = .bgra8Unorm
    d.colorAttachments[0].isBlendingEnabled=true
    d.colorAttachments[0].sourceRGBBlendFactor = .one
    d.colorAttachments[0].destinationRGBBlendFactor = .oneMinusSourceAlpha
    d.colorAttachments[0].sourceAlphaBlendFactor = .one
    d.colorAttachments[0].destinationAlphaBlendFactor = .oneMinusSourceAlpha
    pipeline=try! device.makeRenderPipelineState(descriptor:d)
  }
  func redraw() { if let latest { draw(latest.0,latest.1) } }
  func draw(_ l: SapphireWatchCrownLayout,_ s: SapphireWatchCrownClock.Sample) {
    latest=(l,s)
    guard let view,view.bounds.width > 0,view.bounds.height > 0,
      let pass=view.currentRenderPassDescriptor,let drawable=view.currentDrawable,
      let buffer=queue.makeCommandBuffer(),let encoder=buffer.makeRenderCommandEncoder(descriptor:pass) else { return }
    let f=s.frame
    var u=Uniforms(view:.init(Float(l.w),Float(l.h),Float(view.drawableSize.width/l.w),Float(s.clock)),
      watch:.init(Float(l.cx),Float(l.cy),Float(l.r),Float(f.flip)),
      hands:.init(Float(SapphireWatchCrownMotion.modulo(f.hours,12)),Float(f.crown),Float(f.metalFrom),Float(f.metalTo)),
      metal:.init(Float(f.metalK),Float(s.time),0,0))
    encoder.setRenderPipelineState(pipeline)
    encoder.setFragmentBytes(&u,length:MemoryLayout<Uniforms>.stride,index:0)
    encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
    encoder.endEncoding(); buffer.present(drawable); buffer.commit()
  }
}
