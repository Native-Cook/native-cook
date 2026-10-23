import Metal
import MetalPerformanceShaders

/// Reused local 380×152 filter targets; no whole-card blur allocation.
@MainActor final class EmberBloomCache {
  /// Premultiplied light shared by both blur passes.
  var source: MTLTexture?

  /// Wide halo retained on medium and high tiers.
  var broad: MTLTexture?

  /// Tight core retained only on high.
  var tight: MTLTexture?
  /// Constant-sigma filters reused across frames at the same pixel scale.
  var broadFilter: MPSImageGaussianBlur?
  var tightFilter: MPSImageGaussianBlur?

  /// Last allocation scale; changes recreate the local targets once.
  private var scale: Float = 0

  func prepare(device: MTLDevice, scale: Float) {
    guard self.scale != scale else { return }
    self.scale = scale
    let descriptor = MTLTextureDescriptor.texture2DDescriptor(
      pixelFormat: EmberRenderer.format, width: Int(Float(EmberTheme.tube.w) * scale),
      height: Int(Float(EmberTheme.tube.h) * scale),
      mipmapped: false)
    descriptor.usage = [.renderTarget, .shaderRead, .shaderWrite]
    descriptor.storageMode = .private
    source = device.makeTexture(descriptor: descriptor)
    broad = device.makeTexture(descriptor: descriptor)
    tight = device.makeTexture(descriptor: descriptor)
    broadFilter = MPSImageGaussianBlur(device: device, sigma: Float(EmberTheme.E.bar.bloom) * scale)
    tightFilter = MPSImageGaussianBlur(device: device, sigma: Float(EmberTheme.E.bar.core) * scale)
    broadFilter?.edgeMode = .zero
    tightFilter?.edgeMode = .zero
  }
}
