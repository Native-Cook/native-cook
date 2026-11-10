import MetalKit
import SwiftUI

struct VitaminCapsuleDoseMetal: UIViewRepresentable {
  let layout:VitaminCapsuleDoseLayout
  let frame:VitaminCapsuleDoseMotion.Frame
  var pinned:Double?
  var scripted:Bool
  var reduced:Bool
  func makeUIView(context:Context)->MTKView {
    let v=MTKView(frame:.zero,device:MTLCreateSystemDefaultDevice())
    v.isOpaque=false;v.backgroundColor = .clear;v.clearColor=MTLClearColorMake(0,0,0,0)
    v.isPaused=true;v.enableSetNeedsDisplay=true;v.colorPixelFormat = .bgra8Unorm
    (v.layer as? CAMetalLayer)?.colorspace=CGColorSpace(name:CGColorSpace.sRGB)
    context.coordinator.prepare(v);return v
  }
  func updateUIView(_ v:MTKView,context:Context) {
    context.coordinator.layout=layout;context.coordinator.frame=frame;context.coordinator.pinned=pinned;context.coordinator.scripted=scripted;context.coordinator.reduced=reduced
    v.setNeedsDisplay()
  }
  func makeCoordinator()->VitaminCapsuleDoseRenderer {VitaminCapsuleDoseRenderer()}
}
final class VitaminCapsuleDoseRenderer:NSObject,MTKViewDelegate {
  typealias M=VitaminCapsuleDoseMotion
  var layout=VitaminCapsuleDoseLayout(w:402,h:874,top:62,bottom:34)
  var frame=M.Frame(),pinned:Double?,scripted=false,reduced=false
  private var hero:MTLRenderPipelineState?,bead:MTLRenderPipelineState?
  private var pearls:MTLComputePipelineState?,dissolve:MTLComputePipelineState?
  private var beadsBuffer:MTLBuffer?,pearlsBuffer:MTLBuffer?
  private var queue:MTLCommandQueue?
  private var prevT = -1.0,stir=CGPoint(x:-999,y:-999)
  private var replayKey=""
  func prepare(_ v:MTKView) {
    guard let device=v.device,let library=device.makeDefaultLibrary() else {return}
    do {
      func pipeline(_ vs:String,_ fs:String)throws->MTLRenderPipelineState {
        let d=MTLRenderPipelineDescriptor()
        d.vertexFunction=library.makeFunction(name:vs);d.fragmentFunction=library.makeFunction(name:fs)
        let a=d.colorAttachments[0]!
        a.pixelFormat=v.colorPixelFormat;a.isBlendingEnabled=true
        a.sourceRGBBlendFactor = .one;a.destinationRGBBlendFactor = .oneMinusSourceAlpha
        a.sourceAlphaBlendFactor = .one;a.destinationAlphaBlendFactor = .oneMinusSourceAlpha
        return try device.makeRenderPipelineState(descriptor:d)
      }
      hero=try pipeline("vitaminCapsuleDoseFullVs","vitaminCapsuleDoseHeroFs")
      bead=try pipeline("vitaminCapsuleDoseBeadVs","vitaminCapsuleDoseBeadFs")
      pearls=try device.makeComputePipelineState(function:library.makeFunction(name:"vitaminCapsuleDosePearlsCs")!)
      dissolve=try device.makeComputePipelineState(function:library.makeFunction(name:"vitaminCapsuleDoseBeadsCs")!)
      beadsBuffer=device.makeBuffer(length:900*32,options:.storageModeShared)
      pearlsBuffer=device.makeBuffer(length:80*16,options:.storageModeShared)
      if let b=beadsBuffer {memset(b.contents(),0,b.length)}
      if let b=pearlsBuffer {memset(b.contents(),0,b.length)}
      queue=device.makeCommandQueue();v.delegate=self
    } catch {NSLog("VitaminCapsuleDose Metal pipeline: %@",String(describing:error))}
  }
  private func uniforms(_ f:M.Frame,_ pr:Double)->[SIMD4<Float>] {
    let l=layout,yaw=f.yaw+(reduced && pinned==nil ? 0:2*Double.pi*f.k),cy=cos(yaw),sy=sin(yaw),cp=cos(f.pitch),sp=sin(f.pitch)
    func v(_ a:Double,_ b:Double,_ c:Double,_ d:Double)->SIMD4<Float>{SIMD4(Float(a),Float(b),Float(c),Float(d))}
    return [v(l.w,l.h,pr,f.t),v(l.cx,l.cy+f.bob,l.unit,l.y(294)-l.cy-f.bob),v(0,f.a,f.b,f.k),v(cy,sp*sy,-cp*sy,0),v(0,cp,sp,0),v(sy,-sp*cy,cp*cy,0),v(f.take,f.reform,f.hold,0),v(l.checkX,l.rowsY[f.targetRow],0,0),v(f.sep,f.twist,f.hidden,f.tilt),v(f.dt,f.mode,f.fx,f.fy),v(l.floor,14*l.sx,l.w-14*l.sx,5.5),v(f.fdx,f.fdy,f.down,0)]
  }
  private func compute(_ command:MTLCommandBuffer,_ u:[SIMD4<Float>],_ taking:Bool) {
    guard let pearls,let dissolve,let c=command.makeComputeCommandEncoder() else{return}
    u.withUnsafeBytes{c.setBytes($0.baseAddress!,length:$0.count,index:0)}
    c.setBuffer(beadsBuffer,offset:0,index:1);c.setBuffer(pearlsBuffer,offset:0,index:3)
    if taking {c.setComputePipelineState(dissolve);c.dispatchThreadgroups(MTLSize(width:15,height:1,depth:1),threadsPerThreadgroup:MTLSize(width:64,height:1,depth:1))}
    c.setComputePipelineState(pearls);c.dispatchThreadgroups(MTLSize(width:1,height:1,depth:1),threadsPerThreadgroup:MTLSize(width:64,height:1,depth:1));c.endEncoding()
  }
  private func replay(_ t:Double,_ pr:Double) {
    let key="\(t)|\(layout)"
    guard replayKey != key,let command=queue?.makeCommandBuffer() else{return}
    replayKey=key
    if let b=beadsBuffer {memset(b.contents(),0,b.length)}
    if let b=pearlsBuffer {memset(b.contents(),0,b.length)}
    var previous = -1.0,point=CGPoint(x:-999,y:-999)
    // A pinned frame is a replay, never an endlessly running simulation at that pose.
    let count=Int(ceil(max(0,t)*60))
    for i in 0...max(0,count) {
      let at=min(t,Double(i)/60)
      var f=M.at(at);f.dt=i==0 ? 0:at-max(0,Double(i-1)/60)
      if previous<1.3 && at>=1.3 {f.mode=1}
      let old=point
      if f.fingerAt==3 {point=M.finger(f,layout)}
      f.fx=point.x;f.fy=point.y;f.fdx=point.x-old.x;f.fdy=point.y-old.y;f.down=f.fingerAt==3 && f.finger>0.5 ? 1:0
      compute(command,uniforms(f,pr),f.take>0);previous=at
    }
    command.commit();command.waitUntilCompleted()
  }
  func mtkView(_ view:MTKView,drawableSizeWillChange size:CGSize){view.setNeedsDisplay()}
  func draw(in v:MTKView) {
    guard let hero,let bead,let pass=v.currentRenderPassDescriptor,let drawable=v.currentDrawable else{return}
    let pr=Double(v.drawableSize.width)/max(1,layout.w)
    if let pinned {replay(pinned,pr)}
    guard let command=queue?.makeCommandBuffer() else{return}
    var f=frame
    if pinned==nil {
      if scripted && prevT<1.3 && f.t>=1.3 {f.mode=1}
      compute(command,uniforms(f,pr),f.take>0)
      prevT=f.t
    }
    let u=uniforms(f,pr)
    guard let e=command.makeRenderCommandEncoder(descriptor:pass) else{return}
    e.setRenderPipelineState(hero)
    u.withUnsafeBytes{e.setFragmentBytes($0.baseAddress!,length:$0.count,index:0)}
    e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3)
    e.setRenderPipelineState(bead)
    u.withUnsafeBytes{e.setVertexBytes($0.baseAddress!,length:$0.count,index:0)}
    e.setVertexBuffer(beadsBuffer,offset:0,index:2)
    e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:6,instanceCount:900)
    e.endEncoding();command.present(drawable);command.commit()
  }
}
