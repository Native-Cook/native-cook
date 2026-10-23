import Metal
import QuartzCore
import SceneKit
import UIKit

/// Uniforms of LiftCompositor.metal (layout must match).
private struct CompositeUniforms {
  var sizePx: SIMD2<Float>
  var pixelRatio: Float
  var mirror: Float
  var squash: Float
  var depth: Float
  var mirrored: Float
  var glow: Float
  var shadowDy: Float
  var shadowAlpha: Float
  var shown: Float
  var shadowScale: Float
  var fog: (SIMD4<Float>, SIMD4<Float>, SIMD4<Float>, SIMD4<Float>)
  var fogAt: SIMD4<Float>
}

/// The on-demand renderer of the lifted console (use-on-demand-three-scene.ts
/// + LiftLayer's Skia compositing). SceneKit renders the object offscreen
/// (SCNRenderer into a 4× MSAA texture, only when the pose or face changed);
/// a small Metal pass then composites the mirrored reflection with fog, the
/// silhouette shadow and the object into a CAMetalLayer, on the same display
/// link frame. Two SCNViews would render the object twice per frame and could
/// not derive a blurred silhouette shadow from the render; one offscreen
/// render reused three times is what RN does too.
@MainActor
final class GlowknobLiftRenderer {
  let layer = CAMetalLayer()
  private let device: MTLDevice
  private let queue: MTLCommandQueue
  private let renderer: SCNRenderer
  let scene: GlowknobLiftScene
  private let link: GlowknobLiftLink
  private let sizePx: (w: Int, h: Int)
  private let pixelRatio: Double
  private let frame: GlowknobBox
  private let housing: GlowknobBox
  private var msaaColor: MTLTexture!
  private var msaaDepth: MTLTexture!
  private var resolved: MTLTexture!
  private var resolvedView: MTLTexture!
  private var shadowA: MTLTexture!
  private var shadowB: MTLTexture!
  private var compositePipeline: MTLRenderPipelineState!
  private var downsamplePipeline: MTLComputePipelineState!
  private var blurPipeline: MTLComputePipelineState!
  private let shadowFactor: UInt32 = 4
  private var sampleCount = 4

  private var dirty = true
  private var reveal = false
  private var first = true
  private var lastPose = ""
  private var lastComposite = ""
  private var envReady = false
  private(set) var active = false

  init?(frame: GlowknobBox, housing: GlowknobBox, pixelRatio: Double, link: GlowknobLiftLink) {
    guard let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue() else { return nil }
    self.device = device
    self.queue = queue
    self.frame = frame
    self.housing = housing
    self.pixelRatio = pixelRatio
    self.link = link
    sizePx = (Int((frame.width * pixelRatio).rounded(.down)), Int((frame.height * pixelRatio).rounded(.down)))
    scene = GlowknobLiftScene(canvas: CGSize(width: frame.width, height: frame.height), housing: housing, pixelRatio: pixelRatio)
    renderer = SCNRenderer(device: device, options: nil)
    renderer.scene = scene.scene
    renderer.pointOfView = scene.cameraNode
    renderer.autoenablesDefaultLighting = false
    renderer.isJitteringEnabled = false

    layer.device = device
    layer.pixelFormat = .bgra8Unorm
    layer.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
    layer.framebufferOnly = true
    layer.isOpaque = false
    layer.contentsScale = pixelRatio
    layer.drawableSize = CGSize(width: sizePx.w, height: sizePx.h)
    layer.frame = CGRect(x: frame.x, y: frame.y, width: frame.width, height: frame.height)
    makeResources()
  }

  private func makeResources() {
    let w = sizePx.w, h = sizePx.h
    let colorFormat = renderer.colorPixelFormat == .invalid ? MTLPixelFormat.bgra8Unorm_srgb : renderer.colorPixelFormat
    let depthFormat = renderer.depthPixelFormat == .invalid ? MTLPixelFormat.depth32Float : renderer.depthPixelFormat
    let color = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: colorFormat, width: w, height: h, mipmapped: false)
    color.textureType = .type2DMultisample
    color.sampleCount = sampleCount
    color.usage = .renderTarget
    color.storageMode = .private
    msaaColor = device.makeTexture(descriptor: color)
    let depth = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: depthFormat, width: w, height: h, mipmapped: false)
    depth.textureType = .type2DMultisample
    depth.sampleCount = sampleCount
    depth.usage = .renderTarget
    depth.storageMode = .private
    msaaDepth = device.makeTexture(descriptor: depth)
    let res = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: colorFormat, width: w, height: h, mipmapped: false)
    res.usage = [.renderTarget, .shaderRead, .pixelFormatView]
    res.storageMode = .private
    resolved = device.makeTexture(descriptor: res)
    // Sample the encoded values: compositing happens in sRGB-encoded space.
    let encoded: MTLPixelFormat = colorFormat == .bgra8Unorm_srgb ? .bgra8Unorm
      : colorFormat == .rgba8Unorm_srgb ? .rgba8Unorm : colorFormat
    resolvedView = resolved.makeTextureView(pixelFormat: encoded)
    let sw = (w + Int(shadowFactor) - 1) / Int(shadowFactor), sh = (h + Int(shadowFactor) - 1) / Int(shadowFactor)
    let sd = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .r16Float, width: sw, height: sh, mipmapped: false)
    sd.usage = [.shaderRead, .shaderWrite]
    sd.storageMode = .private
    shadowA = device.makeTexture(descriptor: sd)
    shadowB = device.makeTexture(descriptor: sd)

    guard let library = try? device.makeLibrary(source: glowknobLiftCompositorSource, options: nil) else { return }
    let p = MTLRenderPipelineDescriptor()
    p.vertexFunction = library.makeFunction(name: "compositeVertex")
    p.fragmentFunction = library.makeFunction(name: "compositeFragment")
    p.colorAttachments[0].pixelFormat = .bgra8Unorm
    compositePipeline = try? device.makeRenderPipelineState(descriptor: p)
    downsamplePipeline = try? device.makeComputePipelineState(function: library.makeFunction(name: "shadowDownsample")!)
    blurPipeline = try? device.makeComputePipelineState(function: library.makeFunction(name: "gaussianBlur")!)
  }

  func setEnvironment(_ url: URL) {
    scene.setEnvironment(url)
    envReady = true
    dirty = true
  }

  func setActive(_ on: Bool) { active = on }

  struct Composite: Equatable {
    var shown: Double
    var lift: Double
    var tick: Double
    var fogTone: Double
    var glow: Bool
  }

  /// One frame of the layer: paint a new face, follow the pose, render the
  /// object if anything changed, then composite.
  func update(_ params: () -> Composite) {
    guard active, envReady else { return }
    if let picture = link.takeFace() {
      GlowknobPerf.canvas("paintFace") { scene.paintFace(picture, glow: link.light) }
      reveal = true
      dirty = true
    }
    if link.live {
      let p = link.latest
      let key = String(format: "%.4f %.4f %.4f %.3f %.3f", p[0], p[1], p[2], p[3], p[4])
      if key != lastPose {
        lastPose = key
        scene.setPose(lift: p[0], yaw: p[1], pitch: p[2], tiltX: p[3], tiltY: p[4])
        dirty = true
      }
    }
    guard let commandBuffer = queue.makeCommandBuffer() else { return }
    var rendered = false
    if dirty || first {
      first = false
      dirty = false
      GlowknobPerf.measure("render3D", \.render3D) { renderObject(commandBuffer) }
      rendered = true
    }
    if rendered {
      // afterRender: the face is revealed by the frame that carries it, so the
      // composite below already draws the object at shown = 1.
      let revealed = reveal
      reveal = false
      link.rendered(revealed)
    }
    var now = params()
    now.shown = link.ready ? now.shown : 0
    let key = "\(now.shown) \(now.lift) \(now.fogTone) \(now.glow)"
    // While the object is up, composite every frame even when nothing moved:
    // the layer's drawables then keep cycling, and the first frames of a
    // set-down or turn don't stall in `nextDrawable()` waiting for the
    // display to hand one back (40–160 ms on the simulator after a still
    // hold).
    if rendered || key != lastComposite || link.live {
      lastComposite = key
      GlowknobPerf.measure("composite3D", \.composite3D) { composite(commandBuffer, now) }
    }
    if GlowknobPerf.isOn {
      let seq = GlowknobPerf.frameSeq
      commandBuffer.addCompletedHandler { cb in
        GlowknobPerf.gpu((cb.gpuEndTime - cb.gpuStartTime) * 1000, frame: seq)
      }
    }
    commandBuffer.commit()
  }

  private func renderObject(_ commandBuffer: MTLCommandBuffer) {
    let pass = MTLRenderPassDescriptor()
    pass.colorAttachments[0].texture = msaaColor
    pass.colorAttachments[0].resolveTexture = resolved
    pass.colorAttachments[0].loadAction = .clear
    pass.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
    pass.colorAttachments[0].storeAction = .multisampleResolve
    pass.depthAttachment.texture = msaaDepth
    pass.depthAttachment.loadAction = .clear
    pass.depthAttachment.clearDepth = 1
    pass.depthAttachment.storeAction = .dontCare
    if msaaDepth.pixelFormat == .depth32Float_stencil8 {
      pass.stencilAttachment.texture = msaaDepth
      pass.stencilAttachment.loadAction = .clear
      pass.stencilAttachment.storeAction = .dontCare
    }
    renderer.render(atTime: CACurrentMediaTime(), viewport: CGRect(x: 0, y: 0, width: sizePx.w, height: sizePx.h),
                    commandBuffer: commandBuffer, passDescriptor: pass)
  }

  private func composite(_ commandBuffer: MTLCommandBuffer, _ c: Composite) {
    guard let drawable = GlowknobPerf.measure("nextDrawable", \.drawableWait, { layer.nextDrawable() }), let compositePipeline, let downsamplePipeline, let blurPipeline
    else { return }
    let lift = c.lift
    let shadowBlur = 5 + 17 * lift
    let sigmaSmall = Float(shadowBlur * pixelRatio / Double(shadowFactor))
    if let enc = commandBuffer.makeComputeCommandEncoder() {
      var factor = shadowFactor
      enc.setComputePipelineState(downsamplePipeline)
      enc.setTexture(resolvedView, index: 0)
      enc.setTexture(shadowA, index: 1)
      enc.setBytes(&factor, length: 4, index: 0)
      dispatch(enc, downsamplePipeline, shadowA)
      var sigma = sigmaSmall
      for (src, dst, horizontal) in [(shadowA!, shadowB!, UInt32(1)), (shadowB!, shadowA!, UInt32(0))] {
        var hz = horizontal
        enc.setComputePipelineState(blurPipeline)
        enc.setTexture(src, index: 0)
        enc.setTexture(dst, index: 1)
        enc.setBytes(&sigma, length: 4, index: 0)
        enc.setBytes(&hz, length: 4, index: 1)
        dispatch(enc, blurPipeline, dst)
      }
      enc.endEncoding()
    }
    // The glass floor under the lifted object: the same mirror line, squash
    // and fog as the 2D floor, which hands over to it as the object rises.
    let s = housing.width / 1000
    let mirror = housing.y + housing.height + (GLOWKNOB_GAP / 2) * s
    let depth = housing.height * GLOWKNOB_SQUASH * GLOWKNOB_DEPTH
    let fog = glowknobFogColors(c.fogTone).map { SIMD4<Float>(Float($0.r), Float($0.g), Float($0.b), Float($0.a)) }
    var u = CompositeUniforms(
      sizePx: SIMD2(Float(sizePx.w), Float(sizePx.h)), pixelRatio: Float(pixelRatio), mirror: Float(mirror),
      squash: Float(GLOWKNOB_SQUASH), depth: Float(depth), mirrored: Float(min(1, lift * 1.6)), glow: c.glow ? 1 : 0,
      shadowDy: Float(4 + 34 * lift + c.tick * 1e-3),
      shadowAlpha: Float(((0.34 - 0.14 * lift) * 1000).rounded() / 1000), shown: Float(c.shown),
      shadowScale: 1 / Float(shadowFactor), fog: (fog[0], fog[1], fog[2], fog[3]),
      fogAt: SIMD4(Float(GLOWKNOB_VISIBLE_AT[0]), Float(GLOWKNOB_VISIBLE_AT[1]), Float(GLOWKNOB_VISIBLE_AT[2]), Float(GLOWKNOB_VISIBLE_AT[3])))
    let pass = MTLRenderPassDescriptor()
    pass.colorAttachments[0].texture = drawable.texture
    pass.colorAttachments[0].loadAction = .clear
    pass.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
    pass.colorAttachments[0].storeAction = .store
    if let enc = commandBuffer.makeRenderCommandEncoder(descriptor: pass) {
      enc.setRenderPipelineState(compositePipeline)
      enc.setFragmentBytes(&u, length: MemoryLayout<CompositeUniforms>.stride, index: 0)
      enc.setFragmentTexture(resolvedView, index: 0)
      enc.setFragmentTexture(shadowA, index: 1)
      enc.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
      enc.endEncoding()
    }
    commandBuffer.present(drawable)
  }

  private func dispatch(_ enc: MTLComputeCommandEncoder, _ p: MTLComputePipelineState, _ t: MTLTexture) {
    let tw = p.threadExecutionWidth, th = max(1, p.maxTotalThreadsPerThreadgroup / tw)
    enc.dispatchThreadgroups(MTLSize(width: (t.width + tw - 1) / tw, height: (t.height + th - 1) / th, depth: 1),
                             threadsPerThreadgroup: MTLSize(width: tw, height: th, depth: 1))
  }
}
