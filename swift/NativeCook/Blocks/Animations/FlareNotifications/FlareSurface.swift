import Metal
import QuartzCore
import SwiftUI
import UIKit

/// The shader's uniforms for one pill (flare-notifications.md §3.3).
struct FlareUniforms: Equatable {
  /// Pill width × 84, in points (the shader's coordinate space).
  var size: SIMD2<Float>
  var stretch: Float
  var heat: Float
  var cool: Float
  var ember: Float
  var guardX: Float
  var body: SIMD3<Float>
  var blobs: FlareBlobFrame
}

/// Compiles the flame shader (`FlareShader.source`) once and draws a pill's
/// flame into a CAMetalLayer. This is the RN Skia `<Shader>` fill: one
/// fragment per device pixel, shaded at its position in points.
///
/// The shader ships as source and is compiled at launch, so building the app
/// does not need Xcode's optional Metal Toolchain component.
@MainActor
final class FlareRenderer {
  static let shared: FlareRenderer? = FlareRenderer()

  let device: MTLDevice
  private let queue: MTLCommandQueue
  private let pipeline: MTLRenderPipelineState

  static let pixelFormat = MTLPixelFormat.bgra8Unorm

  private init?() {
    guard let device = MTLCreateSystemDefaultDevice(),
          let queue = device.makeCommandQueue()
    else { return nil }
    do {
      let library = try device.makeLibrary(source: FlareShader.source, options: nil)
      let descriptor = MTLRenderPipelineDescriptor()
      descriptor.vertexFunction = library.makeFunction(name: "flareVertex")
      descriptor.fragmentFunction = library.makeFunction(name: "flareFragment")
      descriptor.colorAttachments[0].pixelFormat = Self.pixelFormat
      pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
    } catch {
      assertionFailure("The flame shader failed to compile: \(error)")
      return nil
    }
    self.device = device
    self.queue = queue
  }

  func draw(_ u: FlareUniforms, into layer: CAMetalLayer, scale: Float) {
    guard let drawable = layer.nextDrawable(), let buffer = queue.makeCommandBuffer() else { return }
    let pass = MTLRenderPassDescriptor()
    pass.colorAttachments[0].texture = drawable.texture
    pass.colorAttachments[0].loadAction = .dontCare
    pass.colorAttachments[0].storeAction = .store
    guard let encoder = buffer.makeRenderCommandEncoder(descriptor: pass) else { return }
    encoder.setRenderPipelineState(pipeline)
    let params: [Float] = [u.size.x, u.size.y, u.stretch, u.heat, u.cool, u.ember, u.guardX,
                           u.body.x, u.body.y, u.body.z, scale]
    params.withUnsafeBytes { encoder.setFragmentBytes($0.baseAddress!, length: $0.count, index: 0) }
    u.blobs.geo.withUnsafeBytes { encoder.setFragmentBytes($0.baseAddress!, length: $0.count, index: 1) }
    u.blobs.col.withUnsafeBytes { encoder.setFragmentBytes($0.baseAddress!, length: $0.count, index: 2) }
    u.blobs.rot.withUnsafeBytes { encoder.setFragmentBytes($0.baseAddress!, length: $0.count, index: 3) }
    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    encoder.endEncoding()
    buffer.present(drawable)
    buffer.commit()
  }
}

/// The pill's fill: a CAMetalLayer clipped to the capsule (radius h / 2, as
/// RN's `RoundedRect r={height / 2}`), redrawn once per frame by the page.
@MainActor
final class FlareSurfaceView: UIView {
  override class var layerClass: AnyClass { CAMetalLayer.self }
  private var metalLayer: CAMetalLayer { layer as! CAMetalLayer }

  /// The pill's uniforms for this frame.
  var uniforms: (() -> FlareUniforms?)?

  init(body: FlareRGBA) {
    super.init(frame: .zero)
    isUserInteractionEnabled = false
    isOpaque = true
    // Shown until the first drawable lands (and if Metal is unavailable).
    layer.backgroundColor = body.uiColor.cgColor
    layer.cornerCurve = .circular
    layer.masksToBounds = true
    let metal = metalLayer
    metal.device = FlareRenderer.shared?.device
    metal.pixelFormat = FlareRenderer.pixelFormat
    metal.framebufferOnly = true
    // The shader writes gamma-encoded sRGB, as Skia's SkSL does into an sRGB
    // surface; the layer is tagged so it is displayed as such.
    metal.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    if window != nil { layer.contentsScale = traitCollection.displayScale }
    setNeedsLayout()
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    layer.cornerRadius = bounds.height / 2
    let scale = layer.contentsScale
    let size = CGSize(width: (bounds.width * scale).rounded(), height: (bounds.height * scale).rounded())
    if metalLayer.drawableSize != size { metalLayer.drawableSize = size }
    render()
  }

  func render() {
    guard window != nil, bounds.width > 0, bounds.height > 0,
          let renderer = FlareRenderer.shared, let u = uniforms?() else { return }
    renderer.draw(u, into: metalLayer, scale: Float(layer.contentsScale))
  }
}

/// SwiftUI host for a pill's surface.
struct FlareSurface: UIViewRepresentable {
  let pill: FlarePillModel

  func makeUIView(context: Context) -> FlareSurfaceView {
    let view = FlareSurfaceView(body: pill.flare.body)
    view.uniforms = { [weak pill] in pill?.uniforms }
    pill.surface = view
    return view
  }

  func updateUIView(_ view: FlareSurfaceView, context: Context) {}
}
