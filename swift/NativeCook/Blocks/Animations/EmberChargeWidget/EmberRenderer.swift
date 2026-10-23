import Metal
import QuartzCore

/// Gamma-encoded sRGB values, exactly as the RN Skia runtime shaders.
/// Unorm (NOT _srgb) avoids silently linearizing the shader arithmetic.
@MainActor final class EmberRenderer {
  private typealias E = EmberTheme.E
  private typealias M = EmberMotionConstants

  /// One runtime compiler and command queue shared by this block’s surfaces.
  static let shared = EmberRenderer()

  /// The GPU backing every texture and drawable.
  let device: MTLDevice

  /// Submits each frame’s bloom passes before the final composite.
  private let queue: MTLCommandQueue

  /// Full-canvas glass fragment pipeline.
  private let glass: MTLRenderPipelineState

  /// Premultiplied tube light used as the Gaussian source.
  private let bloom: MTLRenderPipelineState

  /// Sharp tube composed over its local blurred light.
  private let tube: MTLRenderPipelineState

  /// Unorm preserves gamma-encoded shader arithmetic; layer color space is tagged separately.
  static let format = MTLPixelFormat.bgra8Unorm
  private init?() {
    /// The GPU backing every texture and drawable.
    /// Submits each frame’s bloom passes before the final composite.
    guard let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue() else {
      return nil
    }
    self.device = device
    self.queue = queue
    do {
      let lib = try device.makeLibrary(
        source: Self.bridge + EmberGlassShader.source + EmberTubeShader.source + Self.fragments,
        options: nil)
      func pipeline(_ name: String) throws -> MTLRenderPipelineState {
        let d = MTLRenderPipelineDescriptor()
        d.vertexFunction = lib.makeFunction(name: "emberVertex")
        d.fragmentFunction = lib.makeFunction(name: name)
        d.colorAttachments[0].pixelFormat = Self.format
        return try device.makeRenderPipelineState(descriptor: d)
      }
      glass = try pipeline("emberGlass")
      bloom = try pipeline("emberBloom")
      tube = try pipeline("emberTube")
    } catch {
      assertionFailure("Ember Metal compilation: \(error)")
      return nil
    }
  }
  // Metal requires semicolons. These entry points only unpack uniforms and compose
  // premultiplied layers; all card dimensions interpolate the shared theme.
  private static let bridge = #"""
    #include <metal_stdlib>
    using namespace metal;
    struct EmberVertex { float4 position [[position]]; };
    vertex EmberVertex emberVertex(uint id [[vertex_id]]) {
      // Full-screen triangle in normalized device coordinates, not card geometry.
      float2 positions[3] = {float2(-1, -1), float2(3, -1), float2(-1, 3)};
      return {float4(positions[id], 0, 1)};
    }
    """#

  private static let fragments = #"""
    fragment half4 emberGlass(EmberVertex v [[stage_in]], constant float *u [[buffer(0)]]) {
      GlassProgram g;
      g.size = float2(\#(EmberTheme.E.size));
      g.bleed = \#(EmberTheme.E.bleed);
      g.radius = \#(EmberTheme.E.corner);
      g.time = u[1];
      g.rise = u[2];
      g.heat = u[3];
      g.slosh = u[4];
      g.ripple = u[5];
      g.origin = float2(u[6], \#(EmberTheme.barCenterY));
      g.flash = u[7];
      g.lightLean = u[8];
      g.sweep = u[9];
      g.aim = float2(u[10], u[11]);
      g.touchLight = u[12];
      g.touchRadius = u[13];
      g.materialise = u[14];
      g.quality = u[15];
      return g.main(v.position.xy / u[0]);
    }

    TubeProgram makeTube(constant float *u) {
      TubeProgram t;
      t.width = u[1];
      t.heat = u[2];
      t.drift = u[3];
      t.bend = u[4];
      t.press = u[5];
      t.fuse = u[6];
      t.ring = u[7];
      t.bloomOnly = 0;
      t.aimX = u[8];
      return t;
    }

    fragment half4 emberBloom(EmberVertex v [[stage_in]], constant float *u [[buffer(0)]]) {
      TubeProgram t = makeTube(u);
      t.bloomOnly = 1;
      return t.main(v.position.xy / u[0] - \#(EmberTheme.tubePad));
    }

    fragment half4 emberTube(EmberVertex v [[stage_in]], constant float *u [[buffer(0)]],
                            texture2d<half> broad [[texture(0)]], texture2d<half> tight [[texture(1)]]) {
      TubeProgram t = makeTube(u);
      float2 xy = v.position.xy / u[0];
      half4 col = half4(0);
      constexpr sampler s(coord::normalized, filter::linear, address::clamp_to_zero);
      float2 uv = xy / float2(\#(EmberTheme.tube.w), \#(EmberTheme.tube.h));
      if (u[9] > 0.5) col = broad.sample(s, uv) * half(u[10]);
      if (u[9] > 1.5) {
        half4 b = tight.sample(s, uv) * half(u[11]);
        col = b + col * (1 - b.a);
      }
      half4 sharp = t.main(xy - \#(EmberTheme.tubePad));
      col = sharp + col * (1 - sharp.a);
      // The bar's parent clip is circular radius 76, independent of the glass SDF.
      float d = t.roundedBox(xy + float2(\#(EmberTheme.tube.x), \#(EmberTheme.tube.y)),
                             float2(\#(EmberTheme.E.size)), \#(EmberTheme.E.radius));
      return col * half(1 - smoothstep(-0.5, 0.5, d));
    }
    """#

  func render(_ frame: EmberFrame, into layer: CAMetalLayer, isTube: Bool, cache: EmberBloomCache) {
    guard let drawable = layer.nextDrawable(), let command = queue.makeCommandBuffer() else {
      return
    }
    let scale = Float(layer.contentsScale)
    let reduced = frame.reduced
    let tier: Float = frame.quality == .high ? 2 : frame.quality == .medium ? 1 : 0
    // Uniform array order is the explicit ABI of the fragment entry points above.
    // Remaining coefficients are the reference's light-response curves, not layout.
    var params: [Float]
    if isTube {
      params = [
        scale, Float(max(E.bar.h / 2, frame.progress * E.bar.w)), Float(reduced ? 0 : frame.glow),
        Float(reduced ? 0 : frame.drift),
        Float(reduced ? 0 : (frame.meniscus + 3 * frame.slosh) * min(1, (1 - frame.progress) * 12)),
        Float(reduced ? 0 : (frame.press + 0.12 * frame.charge) * (1 - frame.fuse)),
        Float(frame.fuse),
        Float(
          reduced || frame.age <= 0
            ? 0 : exp(-M.bar.ringDecay * frame.age) * sin(M.bar.ringFrequency * frame.age)),
        Float(reduced ? 0 : frame.aimX), tier,
        Float(0.65 + (reduced ? 0 : 0.12 * frame.glow + 0.25 * frame.fuse)),
        Float(0.22 + (reduced ? 0 : 0.18 * frame.fuse)),
      ]
    } else {
      let flash =
        frame.ignition <= 0
        ? 0
        : frame.ignition < M.flash.peak
          ? frame.ignition / M.flash.peak
          : 1 - EmberMath.seg(frame.ignition, M.flash.peak, M.flash.end)
      params = [
        scale, Float(reduced ? 0 : frame.time),
        Float(M.rise.rest + (M.rise.hot - M.rise.rest) * frame.glow), Float(frame.glow),
        Float(reduced ? 0 : frame.slosh),
        Float(reduced ? 0 : EmberMath.seg(frame.ignition, 0, M.rippleEnd)),
        Float(E.bar.x + max(E.bar.h / 2, frame.progress * E.bar.w)), Float(reduced ? 0 : flash),
        Float(
          reduced || tier < 2
            ? 0 : EmberMath.clamp(frame.slosh * 2.4, -0.35, 0.35) + frame.press * 0.08),
        Float(reduced || tier < 2 ? 0 : EmberMath.seg(frame.age, 0, M.sweep)),
        Float(reduced ? 0 : frame.aimX),
        Float(reduced ? 0 : frame.aimY),
        Float(frame.touchLight),
        Float(
          reduced
            ? EmberTheme.touchRadius.rest
            : EmberTheme.touchRadius.rest + EmberTheme.touchRadius.growth * frame.touchSpread),
        Float(reduced ? 1 : frame.enter), tier,
      ]
    }
    func encode(
      _ target: MTLTexture, _ pipeline: MTLRenderPipelineState, textures: [MTLTexture] = []
    ) {
      let pass = MTLRenderPassDescriptor()
      pass.colorAttachments[0].texture = target
      pass.colorAttachments[0].loadAction = .dontCare
      pass.colorAttachments[0].storeAction = .store
      guard let e = command.makeRenderCommandEncoder(descriptor: pass) else { return }
      e.setRenderPipelineState(pipeline)
      params.withUnsafeBytes { e.setFragmentBytes($0.baseAddress!, length: $0.count, index: 0) }
      for (i, t) in textures.enumerated() { e.setFragmentTexture(t, index: i) }
      e.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
      e.endEncoding()
    }
    if isTube {
      cache.prepare(device: device, scale: scale)
      guard let source = cache.source, let broad = cache.broad, let tight = cache.tight else {
        return
      }
      if tier > 0 {
        encode(source, bloom)
        cache.broadFilter?.encode(
          commandBuffer: command, sourceTexture: source, destinationTexture: broad)
        if tier > 1 {
          cache.tightFilter?.encode(
            commandBuffer: command, sourceTexture: source, destinationTexture: tight)
        }
      }
      encode(drawable.texture, tube, textures: [broad, tight])
    } else {
      encode(drawable.texture, glass)
    }
    command.present(drawable)
    command.commit()
  }
}
