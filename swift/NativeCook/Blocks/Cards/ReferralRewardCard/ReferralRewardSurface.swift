import Metal
import QuartzCore
import SwiftUI
import UIKit

/// Runtime compilation, sRGB tagging and point coordinates follow FlareSurface.
@MainActor final class ReferralRewardRenderer {
  static let shared = ReferralRewardRenderer()
  let device: MTLDevice?
  private let queue: MTLCommandQueue?
  private var pipeline: MTLRenderPipelineState?
  private let pass = MTLRenderPassDescriptor()
  private var uniforms = [Float](repeating: 0, count: 25)
  var available: Bool { pipeline != nil }
  private init() {
    device = MTLCreateSystemDefaultDevice()
    queue = device?.makeCommandQueue()
    guard let device else { return }
    do {
      let library = try device.makeLibrary(source: ReferralRewardShader.source, options: nil)
      let descriptor = MTLRenderPipelineDescriptor()
      descriptor.vertexFunction = library.makeFunction(name: "referralRewardVertex")
      descriptor.fragmentFunction = library.makeFunction(name: "referralRewardFragment")
      descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
      pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
      pass.colorAttachments[0].loadAction = .dontCare
      pass.colorAttachments[0].storeAction = .store
    } catch { print("ReferralReward shader unavailable; using plain surface: \(error)") }
  }
  func draw(_ model: ReferralRewardModel, layer: CAMetalLayer) {
    guard let pipeline, let drawable = layer.nextDrawable(),
      let command = queue?.makeCommandBuffer()
    else { return }
    let l = model.layout
    let b = ReferralRewardButtonBody(model: model)
    uniforms[0] = Float(l.width)
    uniforms[1] = Float(l.height)
    uniforms[2] = Float(l.lensY)
    for lamp in 0..<3 {
      let rgb = ReferralRewardFriend.blend(model.scroll.value, lamp: lamp)
      let at = 3 + lamp * 3
      uniforms[at] = rgb.x
      uniforms[at + 1] = rgb.y
      uniforms[at + 2] = rgb.z
    }
    uniforms[12] = Float(model.phase)
    uniforms[13] = Float(model.arrival)
    uniforms[14] = Float(model.glow.value)
    uniforms[15] = Float(model.flash.value)
    uniforms[16] = Float(layer.contentsScale)
    uniforms[17] = Float(l.width / 2)
    uniforms[18] = Float(l.origin.y + (model.reduced ? 0 : (1 - b.arrived) * 8))
    uniforms[19] = Float(b.width * b.scale / 2)
    uniforms[20] = Float(b.height * b.scale / 2)
    uniforms[21] = Float(b.radius * b.scale)
    uniforms[22] = Float(b.hidden ? 0 : b.arrived)
    uniforms[23] = Float(model.press.value)
    uniforms[24] = model.opaque ? 1 : 0
    pass.colorAttachments[0].texture = drawable.texture
    guard let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
    encoder.setRenderPipelineState(pipeline)
    uniforms.withUnsafeBytes {
      encoder.setFragmentBytes($0.baseAddress!, length: $0.count, index: 0)
    }
    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    encoder.endEncoding()
    command.present(drawable)
    command.commit()
  }
}

@MainActor final class ReferralRewardSurfaceView: UIView {
  override class var layerClass: AnyClass { CAMetalLayer.self }
  private var metal: CAMetalLayer { layer as! CAMetalLayer }
  weak var model: ReferralRewardModel?
  override init(frame: CGRect) {
    super.init(frame: frame)
    isUserInteractionEnabled = false
    isOpaque = false
    metal.device = ReferralRewardRenderer.shared.device
    metal.pixelFormat = .bgra8Unorm
    metal.framebufferOnly = true
    metal.isOpaque = false
    metal.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }
  override func didMoveToWindow() {
    super.didMoveToWindow()
    layer.contentsScale = traitCollection.displayScale
    setNeedsLayout()
  }
  override func layoutSubviews() {
    super.layoutSubviews()
    let size = CGSize(
      width: (bounds.width * layer.contentsScale).rounded(),
      height: (bounds.height * layer.contentsScale).rounded())
    if metal.drawableSize != size { metal.drawableSize = size }
    render()
  }
  func render() {
    guard window != nil, bounds.width > 0, let model else { return }
    ReferralRewardRenderer.shared.draw(model, layer: metal)
  }
}

struct ReferralRewardSurface: UIViewRepresentable {
  let model: ReferralRewardModel
  func makeUIView(context: Context) -> ReferralRewardSurfaceView {
    let v = ReferralRewardSurfaceView()
    v.model = model
    model.surface = v
    return v
  }
  // The owned display link and layoutSubviews already render every changed
  // frame. A SwiftUI update must not submit the same Metal frame a second time.
  func updateUIView(_ view: ReferralRewardSurfaceView, context: Context) {}
}

struct ReferralRewardButtonBody {
  let width: Double, height: Double, radius: Double, scale: Double, arrived: Double
  let hidden: Bool
  @MainActor init(model: ReferralRewardModel) {
    let h = model.reduced ? 0 : model.shape.value
    let l = model.layout
    let clock = model.fromCapsule ? 1.2 : model.comet.value
    let t = clock * (model.basePath?.total ?? 1)
    scale = 1 - 0.04 * model.press.value * (1 - h)
    width = l.buttonWidth + (12 - l.buttonWidth) * h
    height = l.buttonHeight + (12 - l.buttonHeight) * h
    let r = max(24, l.buttonHeight / 2)
    radius = r + (6 - r) * h
    hidden = !model.reduced && (h >= 1 || (clock < 1 && t >= 0 && t < 120))
    arrived = ReferralRewardMath.out((model.enter.value - 160) / 380)
  }
}

struct ReferralRewardPlainSurface: View {
  let model: ReferralRewardModel
  @Environment(\.displayScale) private var displayScale
  var body: some View {
    let _ = model.footerClock.frame
    let _ = model.arrivalClock.frame
    let b = ReferralRewardButtonBody(model: model)
    let l = model.layout
    let pressure = ReferralRewardMath.clamp(model.press.value)
    RoundedRectangle(cornerRadius: 32, style: .circular)
      .fill(Color(red: 12 / 255, green: 12 / 255, blue: 13 / 255))
      .overlay(
        RoundedRectangle(cornerRadius: 32, style: .circular).strokeBorder(
          .white.opacity(0.12), lineWidth: 1)
      )
      .overlay(alignment: .topLeading) {
        RoundedRectangle(cornerRadius: max(b.radius * b.scale, 0), style: .circular)
          .fill(
            Color(
              red: (28 + 10 * pressure) / 255, green: (28 + 10 * pressure) / 255,
              blue: (30 + 10 * pressure) / 255)
          )
          .overlay(
            RoundedRectangle(cornerRadius: max(b.radius * b.scale, 0), style: .circular)
              .strokeBorder(.white.opacity(0.1), lineWidth: 1 / displayScale)
          )
          .frame(width: max(b.width * b.scale, 0), height: max(b.height * b.scale, 0))
          .opacity(b.hidden ? 0 : b.arrived).position(
            x: l.width / 2, y: l.origin.y + (model.reduced ? 0 : (1 - b.arrived) * 8))
      }
  }
}
