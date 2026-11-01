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

  // Quartz writes directly into shared Metal storage. The private-texture fallback
  // uses an encoded blit, never replaceRegion's synchronous simulator IPC.
  private final class Bitmap {
    let storage: MTLBuffer
    let texture: MTLTexture
    let context: CGContext
    let linear: Bool
    var dirty = true
    var key: [Double] = []
    init?(_ device: MTLDevice, _ w: Int, _ h: Int) {
      let alignment = max(256,device.minimumLinearTextureAlignment(for:.rgba8Unorm))
      let row = (w*4+alignment-1)/alignment*alignment
      guard let storage = device.makeBuffer(length:row*h,options:.storageModeShared),
            let context = CGContext(data:storage.contents(),width:w,height:h,bitsPerComponent:8,
              bytesPerRow:row,space:CGColorSpace(name:CGColorSpace.sRGB)!,
              bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
      let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.rgba8Unorm,width:w,height:h,mipmapped:false)
      descriptor.storageMode = .shared; descriptor.usage = .shaderRead
      // The simulator's Metal driver aborts on a texture made over a buffer, so it uploads instead.
      #if targetEnvironment(simulator)
      let shared: MTLTexture? = nil
      #else
      let shared = storage.makeTexture(descriptor:descriptor,offset:0,bytesPerRow:row)
      #endif
      if let texture = shared {
        self.texture = texture; linear = true
      } else {
        descriptor.storageMode = .private
        guard let texture = device.makeTexture(descriptor:descriptor) else { return nil }
        self.texture = texture; linear = false
      }
      self.storage = storage; self.context = context
      memset(storage.contents(),0,row*h)
    }
    func draw(_ scale: Double, _ body: (CGContext)->Void) {
      let c = context
      c.clear(CGRect(x:0,y:0,width:c.width,height:c.height))
      c.saveGState(); c.setShouldAntialias(false)
      c.translateBy(x:0,y:Double(c.height)); c.scaleBy(x:scale,y:-scale)
      body(c); c.restoreGState(); dirty = true
    }
  }
  private final class Slot {
    // Each permit protects this exact storage, independently of completion order.
    let available = DispatchSemaphore(value:1)
    let back, solid, halo: Bitmap
    init?(_ device: MTLDevice, _ w: Int, _ h: Int) {
      guard let back = Bitmap(device,w,h), let solid = Bitmap(device,w,h), let halo = Bitmap(device,w,h) else { return nil }
      self.back = back; self.solid = solid; self.halo = halo
    }
  }
  private var slots: [Slot] = []
  private var nextSlot = 0
  private var geometryKey: [Double] = []
  private var starField: Bitmap?
  // Mask, two glows, and glass tint are immutable until resize.
  private var fixed: [Bitmap] = []
  private var pendingGeo: PotionVialUploadGeo?
  private var lastFrame: PotionVialUploadFrame?

  // nextDrawable can wait for the compositor even after GPU work completes.
  // Acquire one ahead on a worker; the display callback only takes a ready one.
  private let drawableQueue = DispatchQueue(label:"PotionVialUpload.drawable",qos:.userInteractive)
  private var readyDrawable: CAMetalDrawable?
  private var requestingDrawable = false
  private var wakeWhenReady = false
  private var waitingForSlot = false

  override init() {
    let device = MTLCreateSystemDefaultDevice(); self.device = device; queue = device?.makeCommandQueue()
    view = PotionVialUploadMetalSurface(frame:.zero,device:device); view.isPaused = true; view.enableSetNeedsDisplay = true
    view.framebufferOnly = true; view.isOpaque = true; view.backgroundColor = UIColor(cgColor:A.color(A.C.page)); view.colorPixelFormat = .bgra8Unorm
    if let layer = view.layer as? CAMetalLayer {
      layer.colorspace = CGColorSpace(name:CGColorSpace.displayP3)
      layer.maximumDrawableCount = 3; layer.allowsNextDrawableTimeout = true
    }
    view.isUserInteractionEnabled = false; view.autoResizeDrawable = false
    if let library = device?.makeDefaultLibrary() {
      let desc = MTLRenderPipelineDescriptor(); desc.vertexFunction = library.makeFunction(name:"potionVialUploadVertex"); desc.fragmentFunction = library.makeFunction(name:"potionVialUploadFragment"); desc.colorAttachments[0].pixelFormat = .bgra8Unorm
      do { pipeline = try device?.makeRenderPipelineState(descriptor:desc) }
      catch { NSLog("PotionVialUpload pipeline: %@",String(describing:error)) }
    }
    super.init()
    view.delegate = self
    view.onLayout = { [weak self] in self?.view.setNeedsDisplay() }
    _ = PotionVialUploadResources.shared
  }
  func draw(_ g: PotionVialUploadGeo,_ f: PotionVialUploadFrame) {
    pendingGeo = g; lastFrame = f; view.setNeedsDisplay()
  }
  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
  func draw(in view: MTKView) {
    guard view.window != nil, !view.bounds.isEmpty,
          let g = pendingGeo, let f = lastFrame else { return }
    render(g,f)
  }
  private func requestDrawable(wake: Bool) {
    wakeWhenReady = wakeWhenReady || wake
    guard !requestingDrawable, readyDrawable == nil, let layer = view.layer as? CAMetalLayer else { return }
    requestingDrawable = true
    drawableQueue.async { [weak self] in
      let drawable = autoreleasepool { layer.nextDrawable() }
      DispatchQueue.main.async { [weak self] in
        guard let self else { return }
        self.requestingDrawable = false; self.readyDrawable = drawable
        let wake = self.wakeWhenReady; self.wakeWhenReady = false
        if wake && drawable != nil { self.view.setNeedsDisplay() }
      }
    }
  }
  private func prepare(_ g: PotionVialUploadGeo, _ pd: Double, _ device: MTLDevice) -> Bool {
    let key = [g.w,g.h,g.top,g.bottom,pd]
    if geometryKey == key { return true }
    let w = max(1,Int(ceil(g.w*pd/4))), h = max(1,Int(ceil(g.h*pd/4)))
    var freshSlots: [Slot] = [], layers: [Bitmap] = []
    for _ in 0..<3 {
      guard let slot = Slot(device,w,h) else { return false }; freshSlots.append(slot)
    }
    for _ in 0..<4 {
      guard let layer = Bitmap(device,w,h) else { return false }; layers.append(layer)
    }
    guard let stars = Bitmap(device,w,h) else { return false }
    stars.draw(pd/4) { c in
      c.setFillColor(A.color(A.C.page)); c.fill(CGRect(x:0,y:0,width:g.w,height:g.h)); A.starField(c,g)
    }
    layers[0].draw(pd/4) { c in
      c.setShouldAntialias(true); A.local(c,g); c.setFillColor(A.color("#ffffff")); c.addPath(A.inner); c.fillPath()
    }
    // Keep Quartz's original resampling and the exact screen pixel phase.
    // Only opacity is animated in Metal; these images are never drawn again.
    layers[1].draw(pd/4) { c in A.local(c,g); A.glow(c,A.outer,9,1) }
    layers[2].draw(pd/4) { c in A.local(c,g); A.glow(c,A.outer,24,1) }
    layers[3].draw(pd/4) { c in A.local(c,g); A.fill(c,A.inner,"#c4bed2",0.05) }
    // In-flight command buffers retain old storage across a resize.
    slots = freshSlots; fixed = layers; starField = stars; nextSlot = 0; geometryKey = key
    return true
  }
  private func render(_ g: PotionVialUploadGeo,_ f: PotionVialUploadFrame) {
    guard let device, let pipeline, let queue else { return }
    let pd = Double(view.window?.screen.scale ?? UIScreen.main.scale)
    let drawableSize = CGSize(width:g.w*pd,height:g.h*pd)
    if view.drawableSize != drawableSize { view.drawableSize = drawableSize; readyDrawable = nil }
    guard let drawable = readyDrawable else { requestDrawable(wake:true); return }
    readyDrawable = nil
    guard drawable.texture.width == Int(drawableSize.width), drawable.texture.height == Int(drawableSize.height),
          prepare(g,pd,device), let stars = starField else { requestDrawable(wake:true); return }
    guard let slotIndex = (0..<slots.count).map({ (nextSlot+$0)%slots.count }).first(where:{ slots[$0].available.wait(timeout:.now()) == .success }) else {
      // Drop a presentation under GPU pressure without advancing or pausing the clock.
      waitingForSlot = true; requestDrawable(wake:false); return
    }
    let slot = slots[slotIndex]; nextSlot = (slotIndex+1)%slots.count; waitingForSlot = false
    var submitted = false
    defer { if !submitted { slot.available.signal() }; requestDrawable(wake:false) }
    guard let buffer = queue.makeCommandBuffer() else { return }
    let backKey = [f.t,f.glow]
    if slot.back.key != backKey {
      memcpy(slot.back.storage.contents(),stars.storage.contents(),stars.storage.length)
      let c = slot.back.context
      c.saveGState(); c.setShouldAntialias(false); c.translateBy(x:0,y:Double(c.height)); c.scaleBy(x:pd/4,y:-pd/4)
      A.twinkles(c,g,f); c.restoreGState(); slot.back.key = backKey; slot.back.dirty = true
    }
    let solidKey = [f.t,f.front,f.liquidOn,f.gone,f.cloud,f.cloudA,f.gemX,f.gemY,f.gemRot,f.gemVis,
      f.spX,f.spY,f.spHop,f.spHead,f.spWalk,f.spPhase,f.spIdle,f.burst,f.slotEmpty,f.cardDoneA,f.shake]
    if slot.solid.key != solidKey {
      slot.solid.draw(pd/4) { c in A.solid(c,g,f) }; slot.solid.key = solidKey
    }
    let haloKey = [f.gemX,f.gemY,f.glow]
    if slot.halo.key != haloKey {
      // This small, moving image needs Quartz's fractional-position filter for
      // byte parity. Redraw only its changed transform/alpha, never the two glows.
      slot.halo.draw(pd/4) { c in
        A.local(c,g); c.setAlpha(0.3+0.25*f.glow)
        if let image = PotionVialUploadResources.shared.halo {
          c.draw(image,in:CGRect(x:f.gemX-62.4,y:f.gemY-62.4,width:124.8,height:124.8))
        }
      }
      slot.halo.key = haloKey
    }
    let layers = [slot.back,slot.solid,slot.halo]+fixed
    let uploads = layers.filter { $0.dirty && !$0.linear }
    if !uploads.isEmpty {
      guard let blit = buffer.makeBlitCommandEncoder() else { return }
      for image in uploads {
        let c = image.context
        blit.copy(from:image.storage,sourceOffset:0,sourceBytesPerRow:c.bytesPerRow,sourceBytesPerImage:c.bytesPerRow*c.height,
          sourceSize:MTLSize(width:c.width,height:c.height,depth:1),to:image.texture,destinationSlice:0,destinationLevel:0,destinationOrigin:MTLOrigin(x:0,y:0,z:0))
      }
      blit.endEncoding()
    }
    let pass = MTLRenderPassDescriptor()
    pass.colorAttachments[0].texture = drawable.texture
    pass.colorAttachments[0].loadAction = .dontCare; pass.colorAttachments[0].storeAction = .store
    guard let encoder = buffer.makeRenderCommandEncoder(descriptor:pass) else { return }
    let uniforms: [SIMD4<Float>] = [
      .init(Float(g.w),Float(g.h),Float(g.s),Float(g.ox)),
      .init(Float(g.cy),Float(pd),Float(f.t),Float(f.p)),
      .init(Float(f.front),Float(f.bulge),Float(f.glow),Float(f.mist)),
      .init(Float(f.liquidOn),Float(f.shake),Float((0.95*f.glow*255).rounded()),Float((0.32*f.glow*255).rounded()))]
    encoder.setRenderPipelineState(pipeline)
    uniforms.withUnsafeBytes { encoder.setFragmentBytes($0.baseAddress!,length:$0.count,index:0) }
    for (index,image) in [slot.back,fixed[1],slot.solid,fixed[0],fixed[2],fixed[3],slot.halo].enumerated() {
      encoder.setFragmentTexture(image.texture,index:index)
    }
    encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3); encoder.endEncoding()
    buffer.addCompletedHandler { [weak self] command in
      // Retain every backing buffer, including immutable textures, through completion.
      _ = layers
      slot.available.signal()
      if let error = command.error { NSLog("PotionVialUpload GPU: %@",String(describing:error)) }
      DispatchQueue.main.async { [weak self] in
        if let self, self.waitingForSlot { self.waitingForSlot = false; self.view.setNeedsDisplay() }
      }
    }
    buffer.present(drawable); buffer.commit(); submitted = true
    for image in layers { image.dirty = false }
  }
}

final class PotionVialUploadMetalSurface: MTKView {
  var onLayout: (() -> Void)?
  override func layoutSubviews() { super.layoutSubviews(); onLayout?() }
  override func didMoveToWindow() { super.didMoveToWindow(); onLayout?() }
}
