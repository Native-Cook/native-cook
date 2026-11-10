import MetalKit
import SwiftUI

struct EyewearLensTintMetalView: UIViewRepresentable {
  let layout: EyewearLensTintTheme.Layout
  let frame: EyewearLensTintMotion.Frame
  let scale: Double
  let reduced: Bool
  func makeCoordinator() -> EyewearLensTintRenderer { EyewearLensTintRenderer() }
  func makeUIView(context: Context) -> EyewearLensTintMetalSurface {
    let view = EyewearLensTintMetalSurface(frame: .zero, device: context.coordinator.device)
    view.colorPixelFormat = .bgra8Unorm
    (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
    view.clearColor = MTLClearColorMake(0, 0, 0, 0)
    view.isOpaque = false; view.backgroundColor = .clear
    view.framebufferOnly = true; view.isPaused = true; view.enableSetNeedsDisplay = true
    view.isUserInteractionEnabled = false; view.delegate = context.coordinator
    return view
  }
  func updateUIView(_ view: EyewearLensTintMetalSurface, context: Context) {
    context.coordinator.update(layout, frame, scale, reduced)
    view.contentScaleFactor = scale
    view.setNeedsDisplay()
  }
}
final class EyewearLensTintMetalSurface: MTKView {
  override func layoutSubviews() { super.layoutSubviews(); setNeedsDisplay() }
}
final class EyewearLensTintRenderer: NSObject, MTKViewDelegate {
  struct Uniforms { var view, lensA, lensB, tint, glare, photo: SIMD4<Float> }
  let device = MTLCreateSystemDefaultDevice()
  private var queue: MTLCommandQueue?
  private var pipeline: MTLRenderPipelineState?
  private var photo: MTLTexture?
  private var uniforms: Uniforms?
  override init() {
    super.init()
    guard let device, let library = device.makeDefaultLibrary() else { assertionFailure("EyewearLensTint: Metal library unavailable"); return }
    queue = device.makeCommandQueue()
    let d = MTLRenderPipelineDescriptor()
    d.vertexFunction = library.makeFunction(name: "eyewearLensTintVertex")
    d.fragmentFunction = library.makeFunction(name: "eyewearLensTintFragment")
    let a = d.colorAttachments[0]!
    a.pixelFormat = .bgra8Unorm; a.isBlendingEnabled = true
    a.sourceRGBBlendFactor = .one; a.destinationRGBBlendFactor = .oneMinusSourceAlpha
    a.sourceAlphaBlendFactor = .one; a.destinationAlphaBlendFactor = .oneMinusSourceAlpha
    do {
      pipeline = try device.makeRenderPipelineState(descriptor: d)
      if let image = EyewearLensTintResources.shared.portrait.cgImage {
        photo = try MTKTextureLoader(device: device).newTexture(cgImage: image, options: [.SRGB: false, .origin: MTKTextureLoader.Origin.topLeft.rawValue, .generateMipmaps: false])
      }
    } catch { assertionFailure("EyewearLensTint Metal: \(error)") }
  }
  func update(_ l: EyewearLensTintTheme.Layout, _ f: EyewearLensTintMotion.Frame, _ scale: Double, _ reduced: Bool) {
    uniforms = Uniforms(view: .init(Float(l.w), Float(l.h), Float(scale), Float(f.time)),
      lensA: .init(Float(l.lens(0).x), Float(l.lensY), Float(l.lensR), 0),
      lensB: .init(Float(l.lens(1).x), Float(l.lensY), Float(l.lensR), 0),
      tint: .init(Float(f.from), Float(f.to), Float(reduced ? (f.pour > 0 ? 1 : 0) : f.pour), Float(f.lens)),
      glare: .init(Float(f.glare), 0, 0, 0),
      photo: .init(Float(l.photo.minX), Float(l.photo.minY), Float(l.photo.width), Float(l.photo.height)))
  }
  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) { view.setNeedsDisplay() }
  func draw(in view: MTKView) {
    guard var uniforms, let pipeline, let photo, let pass = view.currentRenderPassDescriptor,
      let drawable = view.currentDrawable, let command = queue?.makeCommandBuffer(),
      let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
    encoder.setRenderPipelineState(pipeline)
    encoder.setFragmentBytes(&uniforms, length: MemoryLayout<Uniforms>.stride, index: 0)
    encoder.setFragmentTexture(photo, index: 0)
    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    encoder.endEncoding(); command.present(drawable); command.commit()
  }
}
