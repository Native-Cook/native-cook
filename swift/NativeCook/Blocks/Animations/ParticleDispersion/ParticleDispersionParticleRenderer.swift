import Foundation
import Metal
import MetalKit
import CoreGraphics

/// All animation state comes from the caller's clock. Replaying the same seed
/// and time produces the same particles without resetting GPU simulation state.
public struct ParticleDispersionParticleRenderParameters {
  public var logicalSize: SIMD2<Float>
  public var drawableSize: SIMD2<Float>
  public var timeSeconds: Float
  public var progress: Float
  public var dustStartSeconds: Float
  public var dustDurationSeconds: Float
  public var seed: Float
  public var reduceMotion: Bool
  public var backgroundColor: SIMD4<Float>
  /// Normalized x, y, width and height in the logical display viewport.
  public var subjectBounds: SIMD4<Float>
  /// Optional inside-edge emission; zero preserves background-only sampling.
  public var edgeParticleStrength: Float

  public init(
    logicalSize: SIMD2<Float>,
    drawableSize: SIMD2<Float>,
    timeSeconds: Float,
    progress: Float,
    dustStartSeconds: Float = 0.417,
    dustDurationSeconds: Float = 2.6,
    seed: Float = 1,
    reduceMotion: Bool = false,
    backgroundColor: SIMD4<Float> = SIMD4(0.49, 0.38, 0.29, 1),
    subjectBounds: SIMD4<Float> = SIMD4(0.18, 0.08, 0.64, 0.84),
    edgeParticleStrength: Float = 0
  ) {
    self.logicalSize = logicalSize
    self.drawableSize = drawableSize
    self.timeSeconds = timeSeconds
    self.progress = progress
    self.dustStartSeconds = dustStartSeconds
    self.dustDurationSeconds = dustDurationSeconds
    self.seed = seed
    self.reduceMotion = reduceMotion
    self.backgroundColor = backgroundColor
    self.subjectBounds = subjectBounds
    self.edgeParticleStrength = edgeParticleStrength
  }
}

/// Retain this immutable pair across frames and replays. Metal command buffers
/// retain encoded resources; replacing a pair requires no manual GPU teardown.
public final class ParticleDispersionParticleTextures {
  public let frame: MTLTexture
  public let mask: MTLTexture

  public init(frame: MTLTexture, mask: MTLTexture) {
    self.frame = frame
    self.mask = mask
  }
}

public enum ParticleDispersionParticleRendererError: LocalizedError {
  case missingShaderFunction(String)
  case invalidParameters
  case incompatibleRenderTarget
  case encoderUnavailable
  case textureLoadFailed(URL, Error)

  public var errorDescription: String? {
    switch self {
    case .missingShaderFunction(let name):
      return "The particle shader function \(name) is unavailable."
    case .invalidParameters:
      return "Particle rendering requires finite values and positive viewport dimensions."
    case .incompatibleRenderTarget:
      return "The particle render target must match the renderer's pixel format and sample count."
    case .encoderUnavailable:
      return "Metal could not create the particle render encoder."
    case .textureLoadFailed(let url, let error):
      return "Could not load particle texture \(url.lastPathComponent): \(error.localizedDescription)"
    }
  }
}

/// A fullscreen backplate and dense, forward-advected point sprites. Positions,
/// random motion, source colour and source-mask lookup all stay on the GPU.
/// This core has no UIView/Expo dependency and also runs in a macOS test harness.
public final class ParticleDispersionParticleRenderer {
  public let device: MTLDevice
  public let colorPixelFormat: MTLPixelFormat
  public static let maximumParticleCount = 300_000

  private let backgroundPipeline: MTLRenderPipelineState
  private let particlePipeline: MTLRenderPipelineState
  private let subjectPipeline: MTLRenderPipelineState

  // Keep this layout identical to ParticleDispersionParticleUniforms in the embedded Metal code.
  // SIMD4 alignment makes the complete structure 96 bytes on Apple platforms.
  private struct ParticleDispersionUniforms {
    var logicalSize: SIMD2<Float>
    var drawableSize: SIMD2<Float>
    var backgroundColor: SIMD4<Float>
    var subjectBounds: SIMD4<Float>
    var timeSeconds: Float
    var progress: Float
    var dustStartSeconds: Float
    var dustDurationSeconds: Float
    var seed: UInt32
    var reduceMotion: UInt32
    var columns: UInt32
    var particleCount: UInt32
    var cellSize: Float
    var edgeParticleStrength: Float
    var padding: SIMD2<Float> = .zero
  }

  public init(
    device: MTLDevice,
    colorPixelFormat: MTLPixelFormat = .bgra8Unorm
  ) throws {
    self.device = device
    self.colorPixelFormat = colorPixelFormat
    let library = try device.makeLibrary(source: Self.metalSource + ParticleDispersionSubjectShader.source, options: nil)

    func pipeline(vertex: String, fragment: String, blend: Bool) throws -> MTLRenderPipelineState {
      guard let vertexFunction = library.makeFunction(name: vertex) else {
        throw ParticleDispersionParticleRendererError.missingShaderFunction(vertex)
      }
      guard let fragmentFunction = library.makeFunction(name: fragment) else {
        throw ParticleDispersionParticleRendererError.missingShaderFunction(fragment)
      }
      let descriptor = MTLRenderPipelineDescriptor()
      descriptor.label = blend ? "Independent image dust" : "Captured-frame backplate"
      descriptor.vertexFunction = vertexFunction
      descriptor.fragmentFunction = fragmentFunction
      descriptor.rasterSampleCount = 1
      let attachment = descriptor.colorAttachments[0]!
      attachment.pixelFormat = colorPixelFormat
      attachment.isBlendingEnabled = blend
      if blend {
        attachment.rgbBlendOperation = .add
        attachment.alphaBlendOperation = .add
        attachment.sourceRGBBlendFactor = .one
        attachment.destinationRGBBlendFactor = .oneMinusSourceAlpha
        attachment.sourceAlphaBlendFactor = .one
        attachment.destinationAlphaBlendFactor = .oneMinusSourceAlpha
      }
      return try device.makeRenderPipelineState(descriptor: descriptor)
    }

    backgroundPipeline = try pipeline(
      vertex: "particleBackgroundVertex", fragment: "particleBackgroundFragment", blend: false
    )
    particlePipeline = try pipeline(
      vertex: "imageDustVertex", fragment: "imageDustFragment", blend: true
    )
    subjectPipeline = try pipeline(
      vertex: "particleBackgroundVertex", fragment: "subjectFragment", blend: true
    )
    assert(MemoryLayout<ParticleDispersionUniforms>.stride == 96)
  }

  /// File decoding and pipeline creation may be called from a preparation queue.
  /// Captures and masks must already have the same upright orientation.
  public func loadTextures(frameURL: URL, maskURL: URL) throws -> ParticleDispersionParticleTextures {
    let loader = MTKTextureLoader(device: device)
    let options: [MTKTextureLoader.Option: Any] = [
      .SRGB: false,
      .origin: MTKTextureLoader.Origin.topLeft.rawValue,
      .generateMipmaps: false,
      .textureUsage: NSNumber(value: MTLTextureUsage.shaderRead.rawValue),
    ]
    func load(_ url: URL) throws -> MTLTexture {
      do {
        let texture = try loader.newTexture(URL: url, options: options)
        texture.label = "Particle source: \(url.lastPathComponent)"
        return texture
      } catch {
        throw ParticleDispersionParticleRendererError.textureLoadFailed(url, error)
      }
    }
    return try ParticleDispersionParticleTextures(frame: load(frameURL), mask: load(maskURL))
  }

  /// Encodes one complete backplate pass, but never commits or waits. The caller
  /// owns drawable presentation, command-buffer submission and completion errors.
  public func encode(
    commandBuffer: MTLCommandBuffer,
    renderPassDescriptor: MTLRenderPassDescriptor,
    parameters: ParticleDispersionParticleRenderParameters,
    textures: ParticleDispersionParticleTextures
  ) throws {
    let p = parameters
    let scalars = [
      p.logicalSize.x, p.logicalSize.y, p.drawableSize.x, p.drawableSize.y,
      p.timeSeconds, p.progress, p.dustStartSeconds, p.dustDurationSeconds, p.seed,
      p.backgroundColor.x, p.backgroundColor.y, p.backgroundColor.z, p.backgroundColor.w,
      p.subjectBounds.x, p.subjectBounds.y, p.subjectBounds.z, p.subjectBounds.w,
      p.edgeParticleStrength,
    ]
    guard scalars.allSatisfy({ $0.isFinite }),
      p.logicalSize.x > 0, p.logicalSize.y > 0,
      p.logicalSize.x <= 32_768, p.logicalSize.y <= 32_768,
      p.drawableSize.x > 0, p.drawableSize.y > 0,
      p.dustDurationSeconds > 0
    else {
      throw ParticleDispersionParticleRendererError.invalidParameters
    }
    guard let target = renderPassDescriptor.colorAttachments[0].texture,
      target.pixelFormat == colorPixelFormat, target.sampleCount == 1
    else {
      throw ParticleDispersionParticleRendererError.incompatibleRenderTarget
    }

    let viewportScale = min(max(p.logicalSize.x / 390, 0.82), 1.30)
    let limit = Float(Self.maximumParticleCount)
    var cellSize = max(1.1 * viewportScale, sqrt(p.logicalSize.x * p.logicalSize.y / limit))
    var columns = Int(ceil(p.logicalSize.x / cellSize))
    var rows = Int(ceil(p.logicalSize.y / cellSize))
    // Rounding the last row/column can exceed the area estimate; increase the
    // spacing slightly instead of dropping the bottom rows of the image.
    while columns * rows > Self.maximumParticleCount {
      cellSize *= max(1.001, sqrt(Float(columns * rows) / limit))
      columns = Int(ceil(p.logicalSize.x / cellSize))
      rows = Int(ceil(p.logicalSize.y / cellSize))
    }
    let count = columns * rows
    var uniforms = ParticleDispersionUniforms(
      logicalSize: p.logicalSize,
      drawableSize: p.drawableSize,
      backgroundColor: p.backgroundColor,
      subjectBounds: p.subjectBounds,
      timeSeconds: p.timeSeconds,
      progress: min(max(p.progress, 0), 1),
      dustStartSeconds: p.dustStartSeconds,
      dustDurationSeconds: p.dustDurationSeconds,
      seed: p.seed.bitPattern,
      reduceMotion: p.reduceMotion ? 1 : 0,
      columns: UInt32(columns),
      particleCount: UInt32(count),
      cellSize: cellSize,
      edgeParticleStrength: min(max(p.edgeParticleStrength, 0), 1)
    )

    let dustTime = p.timeSeconds - p.dustStartSeconds
    let drawsParticles = !p.reduceMotion && p.timeSeconds >= 0.400 &&
      dustTime >= 0 && dustTime < p.dustDurationSeconds
    let drawsBackplate = p.reduceMotion || p.timeSeconds < 0.400 || drawsParticles
    if !drawsBackplate {
      // Between the capture and dust, and after dust finishes, only the opaque
      // matte remains. Clear it without shading every physical screen pixel.
      // Keep the measured particle pass unchanged while dust is active.
      let attachment = renderPassDescriptor.colorAttachments[0]!
      attachment.loadAction = .clear
      // Match the original fragment's half4 output before target conversion.
      attachment.clearColor = MTLClearColor(
        red: Double(Float16(min(max(p.backgroundColor.x, 0), 1))),
        green: Double(Float16(min(max(p.backgroundColor.y, 0), 1))),
        blue: Double(Float16(min(max(p.backgroundColor.z, 0), 1))),
        alpha: 1
      )
    }

    guard let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: renderPassDescriptor) else {
      throw ParticleDispersionParticleRendererError.encoderUnavailable
    }
    defer { encoder.endEncoding() }
    encoder.label = "Stateless image-particle dispersion"
    encoder.setViewport(MTLViewport(
      originX: 0, originY: 0,
      width: Double(p.drawableSize.x), height: Double(p.drawableSize.y),
      znear: 0, zfar: 1
    ))
    encoder.setCullMode(.none)
    encoder.setVertexBytes(&uniforms, length: MemoryLayout<ParticleDispersionUniforms>.stride, index: 0)
    if drawsBackplate {
      encoder.setFragmentBytes(&uniforms, length: MemoryLayout<ParticleDispersionUniforms>.stride, index: 0)
      encoder.setFragmentTexture(textures.frame, index: 0)
      encoder.setRenderPipelineState(backgroundPipeline)
      encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    }

    if drawsParticles {
      encoder.setVertexTexture(textures.frame, index: 0)
      encoder.setVertexTexture(textures.mask, index: 1)
      encoder.setRenderPipelineState(particlePipeline)
      encoder.drawPrimitives(type: .point, vertexStart: 0, vertexCount: count)
    }
    encoder.setFragmentBytes(&uniforms, length: MemoryLayout<ParticleDispersionUniforms>.stride, index: 0)
    encoder.setFragmentTexture(textures.frame, index: 0)
    encoder.setFragmentTexture(textures.mask, index: 1)
    encoder.setRenderPipelineState(subjectPipeline)
    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
  }

  private static let metalSource = #"""
  #include <metal_stdlib>
  using namespace metal;

  struct ParticleDispersionParticleUniforms {
    float2 logicalSize;
    float2 drawableSize;
    float4 backgroundColor;
    float4 subjectBounds;
    float timeSeconds;
    float progress;
    float dustStartSeconds;
    float dustDurationSeconds;
    uint seed;
    uint reduceMotion;
    uint columns;
    uint particleCount;
    float cellSize;
    float edgeParticleStrength;
    float2 padding;
  };

  constexpr sampler sourceSampler(coord::normalized, address::clamp_to_edge, filter::linear);

  float2 coverUV(float2 screen, float2 imageSize, float2 viewport) {
    float scale = max(viewport.x / imageSize.x, viewport.y / imageSize.y);
    float2 offset = (viewport - imageSize * scale) * 0.5;
    return (screen - offset) / (imageSize * scale);
  }

  uint particleHash(uint value) {
    value ^= value >> 16;
    value *= 0x7feb352du;
    value ^= value >> 15;
    value *= 0x846ca68bu;
    value ^= value >> 16;
    return value;
  }

  float particleRandom(uint value) {
    return float(particleHash(value) >> 8) * (1.0 / 16777216.0);
  }

  struct ParticleDispersionBackgroundVertex {
    float4 position [[position]];
    float2 uv;
  };

  vertex ParticleDispersionBackgroundVertex particleBackgroundVertex(uint id [[vertex_id]]) {
    const float2 vertices[3] = { float2(-1, -1), float2(-1, 3), float2(3, -1) };
    ParticleDispersionBackgroundVertex out;
    out.position = float4(vertices[id], 0, 1);
    out.uv = float2((vertices[id].x + 1) * 0.5, (1 - vertices[id].y) * 0.5);
    return out;
  }

  fragment half4 particleBackgroundFragment(
    ParticleDispersionBackgroundVertex in [[stage_in]],
    constant ParticleDispersionParticleUniforms &u [[buffer(0)]],
    texture2d<float> frame [[texture(0)]]
  ) {
    float3 matte = clamp(u.backgroundColor.rgb, 0.0, 1.0);
    if (u.reduceMotion != 0 || u.timeSeconds < 0.400) {
      float2 size = float2(frame.get_width(), frame.get_height());
      float2 uv = coverUV(in.uv * u.logicalSize, size, u.logicalSize);
      float3 original = frame.sample(sourceSampler, uv).rgb;
      float reveal = u.reduceMotion != 0 ? smoothstep(0.08, 0.92, u.progress) : 0;
      return half4(half3(mix(original, matte, reveal)), 1.0h);
    }
    return half4(half3(matte), 1.0h);
  }

  struct ParticleDispersionDustVertex {
    float4 position [[position]];
    float pointSize [[point_size]];
    float4 color [[flat]];
    float coreRadius [[flat]];
    float edgeWidth [[flat]];
  };

  vertex ParticleDispersionDustVertex imageDustVertex(
    uint id [[vertex_id]],
    constant ParticleDispersionParticleUniforms &u [[buffer(0)]],
    texture2d<float> frame [[texture(0)]],
    texture2d<float> mask [[texture(1)]]
  ) {
    ParticleDispersionDustVertex out;
    out.position = float4(3, 3, 0, 1);
    out.pointSize = 1;
    out.color = float4(0);
    out.coreRadius = 0;
    out.edgeWidth = 0.5;
    if (id >= u.particleCount) { return out; }

    uint randomSeed = particleHash(id ^ u.seed);
    float2 jitter = float2(particleRandom(randomSeed + 11u), particleRandom(randomSeed + 23u));
    float2 cell = float2(id % u.columns, id / u.columns);
    float2 source = (cell + 0.12 + jitter * 0.76) * u.cellSize;
    if (any(source >= u.logicalSize)) { return out; }

    float2 frameSize = float2(frame.get_width(), frame.get_height());
    float2 maskSize = float2(mask.get_width(), mask.get_height());
    float2 frameUV = coverUV(source, frameSize, u.logicalSize);
    float2 maskUV = coverUV(source, maskSize, u.logicalSize);
    float sourceSubject = clamp(mask.sample(sourceSampler, maskUV, level(0)).r, 0.0, 1.0);
    float membership = 1 - smoothstep(0.08, 0.72, sourceSubject);
    if (u.edgeParticleStrength > 0) {
      // Opt-in only: a narrow inner edge uses the original edge colour. The
      // default path never samples or emits a solid subject interior.
      float2 probe = float2(3.0);
      float around = mask.sample(sourceSampler, coverUV(source + float2(probe.x, 0), maskSize, u.logicalSize), level(0)).r;
      around = min(around, mask.sample(sourceSampler, coverUV(source - float2(probe.x, 0), maskSize, u.logicalSize), level(0)).r);
      around = min(around, mask.sample(sourceSampler, coverUV(source + float2(0, probe.y), maskSize, u.logicalSize), level(0)).r);
      around = min(around, mask.sample(sourceSampler, coverUV(source - float2(0, probe.y), maskSize, u.logicalSize), level(0)).r);
      float innerEdge = smoothstep(0.08, 0.72, sourceSubject) * (1 - smoothstep(0.08, 0.72, around));
      membership = max(membership, innerEdge * u.edgeParticleStrength);
    }
    if (membership <= 0.001) { return out; }

    float age = clamp((u.timeSeconds - u.dustStartSeconds) / u.dustDurationSeconds, 0.0, 1.0);
    float birth = 0.06 * particleRandom(randomSeed + 37u);
    // Dense small fragments preserve the early image. Most disperse sooner;
    // the surviving fine dust has the same independent motion distribution.
    bool longLived = particleRandom(randomSeed + 43u) < 0.36;
    float deathRandom = particleRandom(randomSeed + 41u);
    float death = longLived ? mix(0.86, 1.0, deathRandom) : mix(0.32, 0.64, deathRandom);
    if (age < birth || age >= death) { return out; }
    float motionAge = max(age - birth, 0.0);
    float particleAge = motionAge / (death - birth);
    float fade = smoothstep(0.50, 1.0, particleAge);
    float opacity = smoothstep(birth, birth + 0.02, age) * (1 - fade) * membership * 0.94;
    if (opacity <= 0.001) { return out; }

    float viewportScale = clamp(u.logicalSize.x / 390.0, 0.82, 1.30);
    float angle = particleRandom(randomSeed + 53u) * 6.28318530718;
    float speed = mix(15.0, 48.0, particleRandom(randomSeed + 67u)) * viewportScale;
    float2 velocity = float2(cos(angle), sin(angle)) * speed;
    float2 subjectUV = u.subjectBounds.xy + u.subjectBounds.zw * 0.5;
    float2 center = subjectUV * u.logicalSize;
    float2 radial = source - center;
    radial /= max(length(radial), 1.0);
    velocity += radial * (8.0 * viewportScale * particleRandom(randomSeed + 71u));
    float scatter = 1 - exp(-3.6 * motionAge);
    float lift = mix(45.0, 130.0, particleRandom(randomSeed + 83u)) * viewportScale * pow(motionAge, 1.6);
    float2 phase = float2(particleRandom(randomSeed + 97u), particleRandom(randomSeed + 101u)) * 6.28318530718;
    float2 frequency = float2(4.0, 4.5) + float2(particleRandom(randomSeed + 107u), particleRandom(randomSeed + 109u)) * 5.0;
    float2 amplitude = (2.0 + float2(particleRandom(randomSeed + 127u), particleRandom(randomSeed + 131u)) * 5.0) * viewportScale;
    float2 curl = (sin(phase + frequency * motionAge) - sin(phase)) * amplitude;
    float2 destination = source + velocity * scatter + curl + float2(0, -lift);

    float sizeRandom = particleRandom(randomSeed + 149u);
    float diameter = sizeRandom < 0.94
      ? mix(0.8, 1.6, sizeRandom / 0.94)
      : mix(1.6, 2.0, (sizeRandom - 0.94) / 0.06);
    // Overlapping image fragments initially preserve the captured colour field;
    // their own forward motion and shrinking footprint reveal separate grains.
    // This contains no static image/noise layer behind the moving particles.
    float initialFootprint = mix(1.25, 1.0, smoothstep(0.03, 0.25, age));
    diameter *= viewportScale * initialFootprint * mix(1.0, 0.88, fade);
    float pixelRatio = min(u.drawableSize.x / u.logicalSize.x, u.drawableSize.y / u.logicalSize.y);
    float diameterPixels = max(0.5, diameter * pixelRatio);
    // Include one physical pixel for the soft perimeter instead of clipping
    // antialiasing at the point primitive's square bounds.
    out.pointSize = diameterPixels + 1.0;
    out.coreRadius = 0.5 * diameterPixels / out.pointSize;
    out.edgeWidth = 0.5 / out.pointSize;
    out.position = float4(destination.x / u.logicalSize.x * 2 - 1, 1 - destination.y / u.logicalSize.y * 2, 0, 1);
    float4 sample = frame.sample(sourceSampler, frameUV, level(0));
    out.color = float4(sample.rgb, opacity * sample.a);
    return out;
  }

  fragment half4 imageDustFragment(ParticleDispersionDustVertex in [[stage_in]], float2 point [[point_coord]]) {
    float distance = length(point - 0.5);
    float shape = 1 - smoothstep(in.coreRadius - in.edgeWidth, in.coreRadius + in.edgeWidth, distance);
    float alpha = in.color.a * shape;
    return half4(half3(in.color.rgb * alpha), half(alpha));
  }
  """#
}
