// Runtime Metal, point coordinates and sRGB tagging follow ReferralReward's surface.
import Metal
import QuartzCore

@MainActor final class AuraRenderer {
  static let shared = AuraRenderer()
  var device: MTLDevice? { AuraPipelineStore.shared.resources?.device }
  private let pass = MTLRenderPassDescriptor()

  private init() {
    pass.colorAttachments[0].loadAction = .dontCare
    pass.colorAttachments[0].storeAction = .store
  }

  @discardableResult func draw(
    _ uniforms: [Float], on layer: CAMetalLayer, page isPage: Bool,
    onShown: (@MainActor @Sendable (Double) -> Void)? = nil
  )
    -> Bool
  {
    guard let resources = AuraPipelineStore.shared.resources,
      let drawable = layer.nextDrawable(), let command = resources.queue.makeCommandBuffer()
    else { return false }
    let pipeline = isPage ? resources.page : resources.shell
    pass.colorAttachments[0].texture = drawable.texture
    guard let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return false }
    encoder.setRenderPipelineState(pipeline)
    uniforms.withUnsafeBytes {
      encoder.setFragmentBytes($0.baseAddress!, length: $0.count, index: 0)
    }
    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    encoder.endEncoding()
    if let onShown {
      if isPage {
        // The page starts transparent, so use GPU completion rather than waiting
        // for an invisible layer to be composited before beginning its fade.
        command.addCompletedHandler { command in
          guard command.status == .completed else { return }
          let time = CACurrentMediaTime() * 1000
          DispatchQueue.main.async { onShown(time) }
        }
      } else {
        #if targetEnvironment(simulator)
        // The simulator SDK omits drawable presentation handlers. A successful
        // shell command completion is its explicit shown-frame fallback.
        command.addCompletedHandler { command in
          guard command.status == .completed else { return }
          let time = CACurrentMediaTime() * 1000
          DispatchQueue.main.async { onShown(time) }
        }
        #else
        drawable.addPresentedHandler { drawable in
          let time = drawable.presentedTime * 1000
          guard time > 0 else { return }
          DispatchQueue.main.async { onShown(time) }
        }
        #endif
      }
    }
    // presentsWithTransaction requires direct drawable presentation after
    // scheduling; command.present would bypass the current CA transaction.
    command.commit()
    command.waitUntilScheduled()
    drawable.present()
    return true
  }
}
