import MetalKit
import SwiftUI

struct PotionVialUploadMetalView: UIViewRepresentable {
  let geo: PotionVialUploadGeo
  let frame: PotionVialUploadFrame
  func makeUIView(context: Context) -> MTKView { context.coordinator.view }
  func makeCoordinator() -> PotionVialUploadRenderer { PotionVialUploadRenderer() }
  func updateUIView(_ view: MTKView, context: Context) { context.coordinator.draw(geo,frame) }
}

final class PotionVialUploadRenderer: NSObject, MTKViewDelegate {
  typealias A = PotionVialUploadArt
  let view: PotionVialUploadMetalSurface
  private let device: MTLDevice?
  private let queue: MTLCommandQueue?
  private var pipeline: MTLRenderPipelineState?
  private var textures: [[MTLTexture]] = []
  private let inFlight = DispatchSemaphore(value:3)
  private var slot = 0
  private var contexts: [CGContext] = []
  private var lastGeo: PotionVialUploadGeo?
  private var lastFrame: PotionVialUploadFrame?
  private var width = 0, height = 0
  override init() {
    let device = MTLCreateSystemDefaultDevice(); self.device = device; queue = device?.makeCommandQueue()
    view = PotionVialUploadMetalSurface(frame:.zero,device:device); view.isPaused = true; view.enableSetNeedsDisplay = true
    view.framebufferOnly = true; view.isOpaque = true; view.backgroundColor = UIColor(cgColor:A.color(A.C.page)); view.colorPixelFormat = .bgra8Unorm
    (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name:CGColorSpace.displayP3)
    view.isUserInteractionEnabled = false; view.autoResizeDrawable = false
    if let library = device?.makeDefaultLibrary() {
      let desc = MTLRenderPipelineDescriptor(); desc.vertexFunction = library.makeFunction(name:"potionVialUploadVertex"); desc.fragmentFunction = library.makeFunction(name:"potionVialUploadFragment"); desc.colorAttachments[0].pixelFormat = .bgra8Unorm
      do {
        pipeline = try device?.makeRenderPipelineState(descriptor:desc)
      } catch { NSLog("PotionVialUpload pipeline: %@",String(describing:error)) }
    }
    super.init()
    view.delegate = self
    view.onLayout = { [weak self] in self?.view.setNeedsDisplay() }
    _ = PotionVialUploadResources.shared
  }
  func draw(_ g: PotionVialUploadGeo,_ f: PotionVialUploadFrame) {
    pendingGeo = g; lastFrame = f
    view.setNeedsDisplay()
  }
  private var pendingGeo: PotionVialUploadGeo?
  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
  func draw(in view: MTKView) {
    guard view.window != nil, !view.bounds.isEmpty,
          let g = pendingGeo, let f = lastFrame else { return }
    render(g,f)
  }
  private func render(_ g: PotionVialUploadGeo,_ f: PotionVialUploadFrame) {
    guard let device, let pipeline, let queue else { return }
    let pd = Double(view.window?.screen.scale ?? UIScreen.main.scale), w = max(1,Int(ceil(g.w*pd/4))), h = max(1,Int(ceil(g.h*pd/4)))
    let resized = width != w || height != h
    let changed = resized || (lastGeo.map { $0.w != g.w || $0.h != g.h || $0.top != g.top || $0.bottom != g.bottom } ?? true)
    if resized {
      width=w; height=h; contexts=[]; textures=[]
      for _ in 0..<4 {
        guard let c = CGContext(data:nil,width:w,height:h,bitsPerComponent:8,bytesPerRow:w*4,space:CGColorSpace(name:CGColorSpace.sRGB)!,bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue) else { return }
        contexts.append(c)
      }
      for _ in 0..<3 {
        var group: [MTLTexture] = []
        for _ in 0..<4 {
          let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.rgba8Unorm,width:w,height:h,mipmapped:false)
          descriptor.storageMode = .shared
          guard let texture = device.makeTexture(descriptor:descriptor) else { return }
          group.append(texture)
        }
        textures.append(group)
      }
    }
    func render(_ n: Int,_ draw:(CGContext)->Void) {
      let c = contexts[n]; c.clear(CGRect(x:0,y:0,width:w,height:h)); c.saveGState(); c.setShouldAntialias(false); c.translateBy(x:0,y:Double(h)); c.scaleBy(x:pd/4,y:-pd/4); draw(c); c.restoreGState()
    }
    render(0) { c in c.setFillColor(A.color(A.C.page)); c.fill(CGRect(x:0,y:0,width:g.w,height:g.h)); A.stars(c,g,f) }
    render(1) { c in A.softFront(c,g,f) }
    render(2) { c in A.solid(c,g,f) }
    if changed { render(3) { c in c.setShouldAntialias(true); A.local(c,g); c.setFillColor(A.color("#ffffff")); c.addPath(A.inner); c.fillPath() } }
    lastGeo=g
    let drawableSize = CGSize(width:g.w*pd,height:g.h*pd)
    if view.drawableSize != drawableSize { view.drawableSize = drawableSize }
    guard let drawable = view.currentDrawable, let pass = view.currentRenderPassDescriptor, let buffer = queue.makeCommandBuffer(), let encoder = buffer.makeRenderCommandEncoder(descriptor:pass) else { return }
    let uniforms: [SIMD4<Float>] = [
      .init(Float(g.w),Float(g.h),Float(g.s),Float(g.ox)),
      .init(Float(g.cy),Float(pd),Float(f.t),Float(f.p)),
      .init(Float(f.front),Float(f.bulge),Float(f.glow),Float(f.mist)),
      .init(Float(f.liquidOn),Float(f.shake),0,0)]
    // Never overwrite a texture that an earlier frame is still sampling.
    inFlight.wait()
    let semaphore = inFlight
    buffer.addCompletedHandler { command in
      semaphore.signal()
      if let error = command.error { NSLog("PotionVialUpload GPU: %@",String(describing:error)) }
    }
    slot = (slot+1)%3
    for n in 0..<4 {
      if let data = contexts[n].data { textures[slot][n].replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:data,bytesPerRow:w*4) }
    }
    encoder.setRenderPipelineState(pipeline)
    uniforms.withUnsafeBytes { encoder.setFragmentBytes($0.baseAddress!,length:$0.count,index:0) }
    for i in 0..<4 { encoder.setFragmentTexture(textures[slot][i],index:i) }
    encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3); encoder.endEncoding(); buffer.present(drawable); buffer.commit()
  }
}

final class PotionVialUploadMetalSurface: MTKView {
  var onLayout: (() -> Void)?
  override func layoutSubviews() { super.layoutSubviews(); onLayout?() }
  override func didMoveToWindow() { super.didMoveToWindow(); onLayout?() }
}
