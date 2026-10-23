import Metal
import QuartzCore
import UIKit

/// Compile once and reuse pipeline, texture and uniform storage. All four materials
/// receive presentation values from the same display-link tick, in point coordinates.
@MainActor final class HorizonRenderer {
  static let shared = HorizonRenderer()
  let device = MTLCreateSystemDefaultDevice()
  private var queue: MTLCommandQueue?
  private var pipelines: [String: MTLRenderPipelineState] = [:]
  private var textures: [String: MTLTexture] = [:]
  private let pass = MTLRenderPassDescriptor()
  private var u = [Float](repeating: 0, count: 34)
  private(set) var failure: String?
  private init() {
    guard let device else { failure = "Metal device unavailable"; return }
    queue = device.makeCommandQueue()
    do {
      let library = try device.makeLibrary(source: HorizonShader.source, options: nil)
      for name in ["horizonScene", "horizonBar", "horizonFile"] {
        let d = MTLRenderPipelineDescriptor()
        d.vertexFunction = library.makeFunction(name: "horizonVertex")
        d.fragmentFunction = library.makeFunction(name: name)
        d.colorAttachments[0].pixelFormat = .bgra8Unorm
        pipelines[name] = try device.makeRenderPipelineState(descriptor: d)
      }
      for name in ["file-sheet", "file-badge", "file-badge-done"] { loadTexture(name, device: device) }
      pass.colorAttachments[0].loadAction = .dontCare
      pass.colorAttachments[0].storeAction = .store
    } catch { failure = String(describing: error); print("Horizon Upload Metal: \(error)") }
  }
  private func loadTexture(_ name: String, device: MTLDevice) {
    guard let image = HorizonResources.image(name).cgImage else { return }
    let width = image.width, height = image.height
    // Skia ImageShader supplies premultiplied sRGB; do not let Metal decode to linear light.
    guard let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
      bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!,
      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue), let bytes = context.data else { return }
    context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
    let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm,
      width: width, height: height, mipmapped: false)
    let texture = device.makeTexture(descriptor: descriptor)
    texture?.replace(region: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0,
      withBytes: bytes, bytesPerRow: width * 4)
    textures[name] = texture
  }
  func draw(_ surface: HorizonSurface, upload m: HorizonUpload, layout l: HorizonLayout) {
    let kind = surface.kind
    let name = kind == .scene ? "horizonScene" : kind == .bar ? "horizonBar" : "horizonFile"
    guard let pipeline = pipelines[name], let drawable = surface.metal.nextDrawable(),
      let command = queue?.makeCommandBuffer() else { return }
    let scale = surface.layer.contentsScale
    if kind == .scene {
      let c = m.reduced ? 0 : m.crest.value
      let inhale = HorizonMath.part(c, 0, 0.12), flareAt = HorizonMath.part(c, 0.12, 0.3)
      let bell = flareAt > 0 && flareAt < 1 ? sin(.pi * flareAt) : 0
      let sweepAt = HorizonMath.part(c, 0.25, 0.7)
      let settled = HorizonMath.clamp(m.done.value) * (m.reduced ? 1 : HorizonMath.part(c, 0.7, 1))
      let rise = HorizonMath.smooth(HorizonMath.part(flareAt, 0, 0.5))
      let dawn = m.reduced ? HorizonMath.clamp(m.done.value)
        : ((c > 0 && c < 1) || m.done.value > 0 ? rise * (1 + 0.5 * (1 - HorizonMath.part(c, 0.21, 1))) : 0)
      u[0] = Float(l.x + l.width / 2); u[1] = Float(l.y + 120); u[2] = Float(l.width / 2); u[3] = 120
      u[4] = u[0] + Float(m.shiftX.value); u[5] = u[1] + Float(m.shiftY.value - m.lift * 46)
      u[6] = u[2]; u[7] = 120; u[8] = Float(l.size.width); u[9] = Float(l.size.height)
      u[10] = 0; u[11] = 67; u[12] = Float((l.width - 48) / 2); u[13] = 29
      u[14] = Float(l.width / 2 - 26); u[15] = -94; u[16] = 16; u[17] = Float(m.closePress.value)
      u[18] = Float(l.size.width / 2); u[19] = Float(l.y + 206 + 560)
      u[20] = Float(560 * asin(min(l.size.width / 1120, 1)) + 20); u[21] = Float(scale)
      u[22] = Float(m.cardScale); u[23] = Float(max(HorizonMath.clamp(m.progress), settled))
      u[24] = Float(max(0, m.power.value) * (1 - 0.1 * inhale * (1 - flareAt)))
      u[25] = Float(HorizonMath.clamp(m.pulse)); u[26] = m.reduced ? 0 : Float(m.time.truncatingRemainder(dividingBy: 9000) / 9000)
      u[27] = Float(bell * bell); u[28] = Float(1 - pow(1 - flareAt, 3)); u[29] = Float(settled)
      u[30] = Float(dawn); u[31] = Float(HorizonMath.smooth(sweepAt))
      u[32] = Float(HorizonMath.part(sweepAt, 0, 0.12) * (1 - HorizonMath.part(sweepAt, 0.84, 1)))
      u[33] = m.opaque ? 1 : 0
    } else if kind == .bar {
      u[0] = Float(l.barWidth); u[1] = m.label == .idle || m.label == .stopped ? 0 : Float(m.progress)
      u[2] = Float(m.power.value); u[3] = Float(m.pulse); u[4] = Float(m.appearance.paused.value)
      u[5] = Float(m.appearance.calm.value); u[6] = Float(m.appearance.shine.value)
      u[7] = m.reduced ? 0 : 1; u[8] = m.label == .done ? 1 : 0; u[9] = Float(scale)
    } else {
      u[0] = Float(m.power.value); u[1] = kind == .sheet ? Float(m.pulse) : 0
      u[2] = m.opaque ? 1 : 0
      u[3] = kind == .sheet && !m.reduced ? Float(HorizonMath.part(m.crest.value, 0.45, 0.62)) : 0
      u[4] = kind == .sheet ? 1 : 0
      u[5] = Float(surface.bounds.width); u[6] = Float(surface.bounds.height); u[7] = Float(scale)
    }
    pass.colorAttachments[0].texture = drawable.texture
    guard let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
    encoder.setRenderPipelineState(pipeline)
    u.withUnsafeBytes { encoder.setFragmentBytes($0.baseAddress!, length: $0.count, index: 0) }
    if kind == .sheet || kind == .front || kind == .back {
      encoder.setFragmentTexture(textures[kind == .sheet ? "file-sheet" : kind == .front ? "file-badge" : "file-badge-done"], index: 0)
    }
    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    encoder.endEncoding(); command.present(drawable); command.commit()
  }
}
