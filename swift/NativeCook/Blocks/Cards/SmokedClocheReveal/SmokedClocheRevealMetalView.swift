import MetalKit

final class SmokedClocheRevealMetalView: MTKView {
  struct Uniforms {
    var view: SIMD4<Float>, bowl: SIMD4<Float>, pose: SIMD4<Float>, finger: SIMD4<Float>, aux: SIMD4<Float>
  }
  private let r=SmokedClocheRevealResources.shared
  private var puffs: MTLBuffer!
  private var layout: SmokedClocheRevealLayout?
  private var sample=SmokedClocheRevealMotion.Frame()
  private var replayed: Double?
  private var previousPinnedLayout: SmokedClocheRevealLayout?
  init() {
    super.init(frame:.zero,device:SmokedClocheRevealResources.shared.device)
    colorPixelFormat = .bgra8Unorm
    (layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.sRGB)
    isOpaque=false;backgroundColor = .clear;clearColor=MTLClearColorMake(0,0,0,0)
    isPaused=true;enableSetNeedsDisplay=true;isUserInteractionEnabled=false
    puffs=r.device.makeBuffer(length:3072*32,options:.storageModeShared)!
    memset(puffs.contents(),0,puffs.length)
  }
  required init(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
  func render(_ l: SmokedClocheRevealLayout, _ f: SmokedClocheRevealMotion.Frame) {
    if let pin=f.pinned, replayed == pin, previousPinnedLayout == l, sample.reduced == f.reduced { return }
    layout=l;sample=f;setNeedsDisplay()
  }
  private func uniforms(_ f: SmokedClocheRevealMotion.Frame, _ l: SmokedClocheRevealLayout) -> Uniforms {
    Uniforms(view:SIMD4(Float(l.w),Float(l.h),Float(contentScaleFactor),Float(f.time)),
      bowl:SIMD4(Float(l.cx),Float(l.cy),Float(l.half),Float(l.radius)),
      pose:SIMD4(Float(f.lift),Float(f.cart),Float(f.mode),Float(f.dt)),
      finger:SIMD4(Float(f.fx),Float(f.fy),Float(f.dx),Float(f.dy)),aux:SIMD4(Float(f.down),f.reduced ? 1 : 0,0,0))
  }
  private func compute(_ command: MTLCommandBuffer, _ f: SmokedClocheRevealMotion.Frame, _ l: SmokedClocheRevealLayout) {
    guard let e=command.makeComputeCommandEncoder() else { return }
    var u=uniforms(f,l)
    e.setComputePipelineState(r.compute);e.setBytes(&u,length:80,index:0);e.setBuffer(puffs,offset:0,index:1)
    e.dispatchThreadgroups(MTLSize(width:48,height:1,depth:1),threadsPerThreadgroup:MTLSize(width:64,height:1,depth:1))
    e.endEncoding()
  }
  override func draw(_ rect: CGRect) {
    guard let l=layout, let drawable=currentDrawable,let pass=currentRenderPassDescriptor,let command=r.queue.makeCommandBuffer() else { return }
    var f=sample
    if f.reduced { replayed=nil;previousPinnedLayout=nil }
    if let pin=f.pinned, !f.reduced && (replayed != pin || previousPinnedLayout != l) {
      let blit=command.makeBlitCommandEncoder()!;blit.fill(buffer:puffs,range:0..<puffs.length,value:0);blit.endEncoding()
      let target=max(0,pin.truncatingRemainder(dividingBy:10))
      var t=0.0,previous = SmokedClocheRevealMotion.at(0),oldMode=0.0
      var previousPoint=CGPoint(x:-999,y:-999)
      while t < target {
        let dt=min(1.0/60,target-t);t += dt
        var step=SmokedClocheRevealMotion.at(t)
        step.dt=dt
        if oldMode < 2 && step.mode == 2 { step.mode=3 }
        oldMode=SmokedClocheRevealMotion.at(t).mode
        let p=SmokedClocheRevealMotion.fingerPoint(step,l)
        if step.fingerAt == 1 && step.finger > 0.5 {
          step.fx=p.x;step.fy=p.y;step.down=1
          if previousPoint.x > -900 { step.dx=p.x-previousPoint.x;step.dy=p.y-previousPoint.y }
          previousPoint=p
        } else { previousPoint=CGPoint(x:-999,y:-999) }
        compute(command,step,l);previous=step
      }
      if target == 0 { compute(command,previous,l) }
      replayed=pin;previousPinnedLayout=l
      f.dt=0
    } else if f.pinned == nil { compute(command,f,l);replayed=nil }
    guard let e=command.makeRenderCommandEncoder(descriptor:pass) else { return }
    var u=uniforms(f,l)
    e.setRenderPipelineState(r.glass)
    e.setFragmentBytes(&u,length:80,index:0);e.setFragmentTexture(r.texture,index:0);e.setFragmentSamplerState(r.sampler,index:0)
    e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
    if !f.reduced {
    e.setRenderPipelineState(r.smoke);e.setVertexBytes(&u,length:80,index:0);e.setVertexBuffer(puffs,offset:0,index:1)
    e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:6,instanceCount:3072)
    }
    e.endEncoding();command.present(drawable);command.commit()
  }
}
