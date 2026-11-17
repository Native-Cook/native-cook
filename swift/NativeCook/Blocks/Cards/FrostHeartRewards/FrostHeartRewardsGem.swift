import MetalKit
import SwiftUI
import UIKit

/// gpu/FhrParticles.tsx + fhr-backdrop.ts: the gem ray-tracer over the Blender sectors, its
/// frost skin, laser, display-case pieces, glints, bloom, breath, core rays and claim pearls.
enum FrostHeartRewardsGemLayout {
  /// Contains the gem, its bloom/caustic and every claim trajectory at the original scale.
  static let viewport = CGRect(x: 20, y: 140, width: 375, height: 415)
  /// The optical target only covers the projected gem.
  static let crystal = CGRect(x: 27, y: 151, width: 298, height: 244)
  static let crystalResolution = 0.5
}

/// Everything the GPU layer reads in a frame (FhrParticles' frame callback inputs).
struct FrostHeartRewardsGemFrame: Equatable {
  var scene = FrostHeartRewardsMotion.Scene()
  var time = 0.0
  var ring = 0.0
  var tiltX = 0.0
  var tiltY = 0.0
  var jelly = 0.0
  var interactive = false
  var touchTime = 0.0
  var claims: [Double] = [-1000, -1000, -1000, -1000, -1000]
  var reduced = false
  var snapshot = false
  static func == (a: Self, b: Self) -> Bool {
    a.time == b.time && a.ring == b.ring && a.tiltX == b.tiltX && a.tiltY == b.tiltY && a.jelly == b.jelly
      && a.interactive == b.interactive && a.touchTime == b.touchTime && a.claims == b.claims && a.reduced == b.reduced
      && a.snapshot == b.snapshot && a.scene.t == b.scene.t && a.scene.reset == b.scene.reset && a.scene.polish == b.scene.polish
      && a.scene.polishX == b.scene.polishX && a.scene.gem == b.scene.gem && a.scene.breath == b.scene.breath
      && a.scene.breathX == b.scene.breathX && a.scene.breathY == b.scene.breathY && a.scene.display == b.scene.display
      && a.scene.hue == b.scene.hue && a.scene.spotlight == b.scene.spotlight && a.scene.core == b.scene.core
  }
}

/// Device, pipelines and the textures that never change: shared by every gem view.
final class FrostHeartRewardsGPU {
  let device: MTLDevice
  let queue: MTLCommandQueue
  let draw: MTLRenderPipelineState
  let overlay: MTLRenderPipelineState
  let composite: MTLRenderPipelineState
  let breath: MTLRenderPipelineState
  let coverage: MTLRenderPipelineState
  let glints: MTLComputePipelineState
  let sampler: MTLSamplerState
  let pieces: MTLBuffer
  let coverageVertices: MTLBuffer
  let coverageCount: Int
  let portals: MTLBuffer
  let sectors: Int
  let bear: MTLTexture
  let hug: MTLTexture
  let page: MTLTexture
  let skin: MTLTexture
  let rounded: MTLTexture
  let blank: MTLTexture

  static let available: FrostHeartRewardsGPU? = FrostHeartRewardsGPU.make()
  private static func make() -> FrostHeartRewardsGPU? { FrostHeartRewardsGPU() }

  private init?() {
    guard let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue(),
          let library = device.makeDefaultLibrary()
    else { return nil }
    self.device = device
    self.queue = queue
    func fn(_ name: String) -> MTLFunction? { library.makeFunction(name: name) }
    let blendOver: (MTLRenderPipelineColorAttachmentDescriptor) -> Void = { a in
      a.isBlendingEnabled = true
      a.rgbBlendOperation = .add; a.alphaBlendOperation = .add
      a.sourceRGBBlendFactor = .one; a.destinationRGBBlendFactor = .oneMinusSourceAlpha
      a.sourceAlphaBlendFactor = .one; a.destinationAlphaBlendFactor = .oneMinusSourceAlpha
    }
    do {
      let d = MTLRenderPipelineDescriptor()
      d.vertexFunction = fn("frostHeartRewardsVertex")
      d.fragmentFunction = fn("frostHeartRewardsFragment")
      d.colorAttachments[0].pixelFormat = .rgba16Float
      d.colorAttachments[1].pixelFormat = .rgba16Float
      draw = try device.makeRenderPipelineState(descriptor: d)
      let o = MTLRenderPipelineDescriptor()
      o.vertexFunction = fn("frostHeartRewardsVertex")
      o.fragmentFunction = fn("frostHeartRewardsOverlayFragment")
      o.colorAttachments[0].pixelFormat = .bgra8Unorm
      blendOver(o.colorAttachments[0])
      overlay = try device.makeRenderPipelineState(descriptor: o)
      let c = MTLRenderPipelineDescriptor()
      c.vertexFunction = fn("frostHeartRewardsCompositeVertex")
      c.fragmentFunction = fn("frostHeartRewardsCompositeFragment")
      c.colorAttachments[0].pixelFormat = .bgra8Unorm
      blendOver(c.colorAttachments[0])
      composite = try device.makeRenderPipelineState(descriptor: c)
      let b = MTLRenderPipelineDescriptor()
      b.vertexFunction = fn("frostHeartRewardsCompositeVertex")
      b.fragmentFunction = fn("frostHeartRewardsBreathFragment")
      b.colorAttachments[0].pixelFormat = .bgra8Unorm
      blendOver(b.colorAttachments[0])
      breath = try device.makeRenderPipelineState(descriptor: b)
      let m = MTLRenderPipelineDescriptor()
      m.vertexFunction = fn("frostHeartRewardsCoverageVertex")
      m.fragmentFunction = fn("frostHeartRewardsCoverageFragment")
      m.colorAttachments[0].pixelFormat = .rgba8Unorm
      m.rasterSampleCount = 4
      coverage = try device.makeRenderPipelineState(descriptor: m)
      guard let g = fn("frostHeartRewardsGlints") else { return nil }
      glints = try device.makeComputePipelineState(function: g)
    } catch {
      return nil
    }
    let sd = MTLSamplerDescriptor()
    sd.minFilter = .linear; sd.magFilter = .linear; sd.mipFilter = .notMipmapped
    sd.sAddressMode = .clampToEdge; sd.tAddressMode = .clampToEdge
    guard let sampler = device.makeSamplerState(descriptor: sd) else { return nil }
    self.sampler = sampler

    // Blender sectors (atelier-gem.json) → the Piece storage layout of fhr-particles.ts.
    guard let url = Bundle.main.url(forResource: "FrostHeartRewards-atelier-gem", withExtension: "json"),
          let data = try? Data(contentsOf: url),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let list = json["pieces"] as? [[String: Any]]
    else { return nil }
    struct Face { let indices: [Int]; let normal: [Double]; let offset: Double }
    struct Sector { let vertices: [[Double]]; let faces: [Face]; let centroid: [Double]; let pivot: [Double]; let exterior: Int }
    func doubles(_ v: Any?) -> [Double] { (v as? [NSNumber])?.map { $0.doubleValue } ?? [] }
    let sectors: [Sector] = list.map { p in
      let vertices = (p["vertices"] as? [[NSNumber]] ?? []).map { $0.map { $0.doubleValue } }
      let faces = (p["faces"] as? [[String: Any]] ?? []).map { f in
        Face(indices: (f["indices"] as? [NSNumber] ?? []).map { $0.intValue }, normal: doubles(f["normal"]), offset: (f["offset"] as? NSNumber)?.doubleValue ?? 0)
      }
      let centroid = doubles(p["centroid"])
      let display = doubles(p["displayCentroid"])
      return Sector(vertices: vertices, faces: faces, centroid: centroid, pivot: display.count == 3 ? display : centroid, exterior: (p["exteriorFaceCount"] as? NSNumber)?.intValue ?? 3)
    }
    self.sectors = sectors.count
    var pieceData = [UInt8]()
    func put(_ v: Float) { withUnsafeBytes(of: v) { pieceData.append(contentsOf: $0) } }
    func putU(_ v: UInt32) { withUnsafeBytes(of: v) { pieceData.append(contentsOf: $0) } }
    for (index, p) in sectors.enumerated() {
      let partner = sectors[index ^ 1]
      var mask: UInt32 = 0
      for (j, face) in p.faces.enumerated() {
        let shared = j >= p.exterior && partner.faces.enumerated().contains { k, other in
          k >= partner.exterior
            && sqrt(zip(face.normal, other.normal).map { ($0 + $1) * ($0 + $1) }.reduce(0, +)) < 1e-5
            && abs(face.offset + other.offset) < 1e-5
        }
        if !shared { mask |= 1 << UInt32(j) }
      }
      let radius = p.vertices.map { v in sqrt(pow(v[0] - p.centroid[0], 2) + pow(v[1] - p.centroid[1], 2) + pow(v[2] - p.centroid[2], 2)) }.max() ?? 0
      put(Float(p.centroid[0])); put(Float(p.centroid[1])); put(Float(p.centroid[2])); put(Float(radius))
      put(Float(p.pivot[0])); put(Float(p.pivot[1])); put(Float(p.pivot[2])); put(Float(p.exterior))
      putU(mask); putU(UInt32(p.faces.count)); putU(0); putU(0)
      for i in 0..<11 {
        if i < p.faces.count {
          let f = p.faces[i]
          put(Float(f.normal[0])); put(Float(f.normal[1])); put(Float(f.normal[2])); put(Float(f.offset))
        } else {
          put(0); put(0); put(0); put(100)
        }
      }
    }
    guard let pieces = device.makeBuffer(bytes: pieceData, length: pieceData.count) else { return nil }
    self.pieces = pieces
    var vertices = [Float]()
    for (id, p) in sectors.enumerated() {
      for face in p.faces where face.indices.count >= 3 {
        for j in 1..<(face.indices.count - 1) {
          for index in [face.indices[0], face.indices[j], face.indices[j + 1]] {
            let v = p.vertices[index]
            vertices += [Float(v[0]), Float(v[1]), Float(v[2]), Float(id)]
          }
        }
      }
    }
    coverageCount = vertices.count / 4
    guard let cv = device.makeBuffer(bytes: vertices, length: vertices.count * 4) else { return nil }
    coverageVertices = cv
    var portalValues = [Float]()
    for group in 0..<12 {
      let all = sectors[group * 2].vertices + sectors[group * 2 + 1].vertices
      var outer = all[0]
      for v in all where hypot(v[0] - 0.15, v[1] + 0.1) > hypot(outer[0] - 0.15, outer[1] + 0.1) { outer = v }
      portalValues += [Float(175 + outer[0] * 105), Float(278 - outer[1] * 105)]
    }
    guard let portals = device.makeBuffer(bytes: portalValues, length: portalValues.count * 4) else { return nil }
    self.portals = portals

    let res = FrostHeartRewardsResources.shared
    let tex = MTLTextureDescriptor()
    tex.pixelFormat = .rgba8Unorm; tex.width = 1; tex.height = 1; tex.usage = [.shaderRead]
    guard let blank = device.makeTexture(descriptor: tex) else { return nil }
    self.blank = blank
    guard let bearPremul = Self.texture(device, res.bear), let hugPremul = Self.texture(device, res.hug),
          let heartPremul = Self.texture(device, res.heart), let rounded = Self.texture(device, res.rounded),
          let library2 = device.makeDefaultLibrary()
    else { return nil }
    self.rounded = rounded
    // Straight-alpha sprites, as createImageBitmap(premultiplyAlpha: 'none') uploads them.
    guard let bear = Self.unpremultiply(device, queue, library2, bearPremul), let hug = Self.unpremultiply(device, queue, library2, hugPremul),
          let page = Self.renderPage(device, queue, library2), let skin = Self.renderSkin(device, queue, library2, heartPremul)
    else { return nil }
    self.bear = bear
    self.hug = hug
    self.page = page
    self.skin = skin
  }

  /// Premultiplied RGBA8 in sRGB, from a decoded PNG.
  private static func texture(_ device: MTLDevice, _ image: CGImage?) -> MTLTexture? {
    guard let image else { return nil }
    let w = image.width, h = image.height
    var bytes = [UInt8](repeating: 0, count: w * h * 4)
    let ok = bytes.withUnsafeMutableBytes { buffer -> Bool in
      guard let ctx = CGContext(data: buffer.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
      else { return false }
      ctx.interpolationQuality = .none
      ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
      return true
    }
    guard ok else { return nil }
    let d = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm, width: w, height: h, mipmapped: false)
    d.usage = [.shaderRead]
    guard let t = device.makeTexture(descriptor: d) else { return nil }
    t.replace(region: MTLRegionMake2D(0, 0, w, h), mipmapLevel: 0, withBytes: bytes, bytesPerRow: w * 4)
    return t
  }
  private static func target(_ device: MTLDevice, _ w: Int, _ h: Int) -> MTLTexture? {
    let d = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm, width: w, height: h, mipmapped: false)
    d.usage = [.shaderRead, .renderTarget]
    d.storageMode = .private
    return device.makeTexture(descriptor: d)
  }
  private struct SkinParams { var box: SIMD4<Float>; var rotation: Float; var scale: Float; var direction: SIMD2<Float>; var sigma: Float; var floorY: Float; var floorFade: Float; var pad: Float = 0 }
  private static func pass(_ device: MTLDevice, _ queue: MTLCommandQueue, _ library: MTLLibrary, _ fragment: String, _ out: MTLTexture, _ input: MTLTexture?, _ params: SkinParams?) -> Bool {
    let d = MTLRenderPipelineDescriptor()
    d.vertexFunction = library.makeFunction(name: "frostHeartRewardsCompositeVertex")
    d.fragmentFunction = library.makeFunction(name: fragment)
    d.colorAttachments[0].pixelFormat = .rgba8Unorm
    guard let pipeline = try? device.makeRenderPipelineState(descriptor: d), let command = queue.makeCommandBuffer() else { return false }
    let rp = MTLRenderPassDescriptor()
    rp.colorAttachments[0].texture = out
    rp.colorAttachments[0].loadAction = .clear
    rp.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0)
    rp.colorAttachments[0].storeAction = .store
    guard let e = command.makeRenderCommandEncoder(descriptor: rp) else { return false }
    e.setRenderPipelineState(pipeline)
    if let input { e.setFragmentTexture(input, index: 0) }
    if var params { e.setFragmentBytes(&params, length: MemoryLayout<SkinParams>.stride, index: 0) }
    e.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    e.endEncoding()
    command.commit()
    command.waitUntilCompleted()
    return true
  }
  private static func unpremultiply(_ device: MTLDevice, _ queue: MTLCommandQueue, _ library: MTLLibrary, _ source: MTLTexture) -> MTLTexture? {
    guard let out = target(device, source.width, source.height) else { return nil }
    let p = SkinParams(box: .zero, rotation: 0, scale: 1, direction: SIMD2(1, 0), sigma: 0.0001, floorY: 1e9, floorFade: 1)
    return pass(device, queue, library, "frostHeartRewardsSkinBlurFragment", out, source, p) ? out : nil
  }
  /// The static page rays at 402 × 440 (RAYS_SKSL, p3 = 0), sampled by refracted rays.
  private static func renderPage(_ device: MTLDevice, _ queue: MTLCommandQueue, _ library: MTLLibrary) -> MTLTexture? {
    guard let out = target(device, 402, 440) else { return nil }
    return pass(device, queue, library, "frostHeartRewardsPageFragment", out, nil, nil) ? out : nil
  }
  /// The registered frost skin at native 3× (1206 × 1320): the heart sprite, blur σ 0.55 pt, floor mask.
  private static func renderSkin(_ device: MTLDevice, _ queue: MTLCommandQueue, _ library: MTLLibrary, _ heart: MTLTexture) -> MTLTexture? {
    guard let a = target(device, 1206, 1320), let b = target(device, 1206, 1320), let c = target(device, 1206, 1320) else { return nil }
    let box = SIMD4<Float>(58, 179, 251, 201)
    let rotation = Float(-0.5 * Double.pi / 180)
    guard pass(device, queue, library, "frostHeartRewardsSkinDrawFragment", a, heart, SkinParams(box: box, rotation: rotation, scale: 3, direction: .zero, sigma: 1, floorY: 0, floorFade: 0)),
          pass(device, queue, library, "frostHeartRewardsSkinBlurFragment", b, a, SkinParams(box: box, rotation: 0, scale: 3, direction: SIMD2(1, 0), sigma: 1.65, floorY: 0, floorFade: 0)),
          pass(device, queue, library, "frostHeartRewardsSkinBlurFragment", c, b, SkinParams(box: box, rotation: 0, scale: 3, direction: SIMD2(0, 1), sigma: 1.65, floorY: 306, floorFade: 62))
    else { return nil }
    return c
  }
}

/// One gem surface: the per-size targets, the baked case front, the uniforms and the glints.
final class FrostHeartRewardsGemRenderer: NSObject, MTKViewDelegate {
  typealias L = FrostHeartRewardsGemLayout
  private let gpu = FrostHeartRewardsGPU.available
  private var values = [Float](repeating: 0, count: 48)
  private var crystal: MTLTexture?
  private var material: MTLTexture?
  private var frozen: MTLTexture?
  private var coverage: MTLTexture?
  private var coverageMSAA: MTLTexture?
  private var params: MTLBuffer?
  private var glintBuffer: MTLBuffer?
  private var sizeKey = CGSize.zero
  private var pending = FrostHeartRewardsGemFrame()
  private var last: FrostHeartRewardsGemFrame?
  private var lastRenderTime = -1.0
  private var lastFrameDate = Date.distantPast
  private var hasDrawn = false
  var available: Bool { gpu != nil }

  func configure(_ view: MTKView) {
    view.device = gpu?.device
    view.colorPixelFormat = .bgra8Unorm
    view.framebufferOnly = true
    view.isOpaque = false
    view.backgroundColor = .clear
    view.clearColor = MTLClearColorMake(0, 0, 0, 0)
    view.isPaused = true
    view.enableSetNeedsDisplay = false
    view.autoResizeDrawable = false
    view.presentsWithTransaction = true
    view.isUserInteractionEnabled = false
    view.isAccessibilityElement = false
    (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
    view.delegate = self
  }

  func render(_ view: MTKView, _ frame: FrostHeartRewardsGemFrame, width: Double, scale: Double) {
    guard let gpu else { return }
    let density = width / 402 * scale
    let w = Int((L.viewport.width * width / 402 * scale).rounded())
    let h = Int((L.viewport.height * width / 402 * scale).rounded())
    let key = CGSize(width: w, height: h)
    if key != sizeKey {
      sizeKey = key
      view.drawableSize = key
      setup(gpu, w, h, density)
      last = nil
    }
    if let last, last == frame, hasDrawn { return }
    pending = frame
    drawn = false
    view.draw()
    // A view not yet in a window has no drawable: try again once it is (a pinned frame draws only once).
    if !drawn { retry(view, 0) }
  }
  private var drawn = false
  private func retry(_ view: MTKView, _ attempt: Int) {
    guard attempt < 40 else { return }
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self, weak view] in
      guard let self, let view, !self.drawn else { return }
      view.draw()
      if !self.drawn { self.retry(view, attempt + 1) }
    }
  }

  private func setup(_ gpu: FrostHeartRewardsGPU, _ w: Int, _ h: Int, _ density: Double) {
    let displayScale = density
    let cw = max(1, Int((L.crystal.width * displayScale * L.crystalResolution).rounded()))
    let ch = max(1, Int((L.crystal.height * displayScale * L.crystalResolution).rounded()))
    func tex(_ format: MTLPixelFormat, _ tw: Int, _ th: Int, samples: Int = 1) -> MTLTexture? {
      let d = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: format, width: tw, height: th, mipmapped: false)
      d.usage = samples > 1 ? [.renderTarget] : [.renderTarget, .shaderRead]
      d.storageMode = .private
      if samples > 1 {
        d.textureType = .type2DMultisample
        d.sampleCount = samples
      }
      return gpu.device.makeTexture(descriptor: d)
    }
    crystal = tex(.rgba16Float, cw, ch)
    material = tex(.rgba16Float, cw, ch)
    frozen = tex(.rgba16Float, cw, ch)
    coverage = tex(.rgba8Unorm, w, h)
    coverageMSAA = tex(.rgba8Unorm, w, h, samples: 4)
    params = gpu.device.makeBuffer(length: 48 * 4)
    glintBuffer = gpu.device.makeBuffer(length: 48, options: .storageModeShared)
    if let g = glintBuffer { memset(g.contents(), 0, 48) }
    values = [Float](repeating: 0, count: 48)
    values[0] = Float(w); values[1] = Float(h); values[2] = Float(density); values[4] = 0
    values[32] = Float(L.viewport.minX); values[33] = Float(L.viewport.minY); values[34] = Float(L.viewport.width); values[35] = Float(L.viewport.height)
    values[36] = Float(L.crystal.minX); values[37] = Float(L.crystal.minY); values[38] = Float(L.crystal.width); values[39] = Float(L.crystal.height)
    values[40] = Float(cw); values[41] = Float(ch)
    values[42] = Float(Double(cw) / L.crystal.width); values[43] = Float(Double(ch) / L.crystal.height)
    // Bake the joined case front once (t = 3.9 s, gem 1, spotlight 1) into the carried texture.
    guard let frozen, let material, let command = gpu.queue.makeCommandBuffer() else { return }
    var bake = values
    bake[3] = 3.9; bake[8] = 1; bake[10] = 1
    let rp = MTLRenderPassDescriptor()
    rp.colorAttachments[0].texture = frozen
    rp.colorAttachments[0].loadAction = .clear; rp.colorAttachments[0].storeAction = .store
    rp.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0)
    rp.colorAttachments[1].texture = material
    rp.colorAttachments[1].loadAction = .clear; rp.colorAttachments[1].storeAction = .store
    rp.colorAttachments[1].clearColor = MTLClearColorMake(0, 0, 0, 0)
    if let e = command.makeRenderCommandEncoder(descriptor: rp) {
      e.setRenderPipelineState(gpu.draw)
      bindMaterial(e, gpu, bake, cut: gpu.blank)
      e.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: gpu.sectors)
      e.endEncoding()
    }
    command.commit()
    command.waitUntilCompleted()
  }

  private func bindMaterial(_ e: MTLRenderCommandEncoder, _ gpu: FrostHeartRewardsGPU, _ v: [Float], cut: MTLTexture) {
    v.withUnsafeBytes { bytes in
      e.setVertexBytes(bytes.baseAddress!, length: bytes.count, index: 0)
      e.setFragmentBytes(bytes.baseAddress!, length: bytes.count, index: 0)
    }
    e.setVertexBuffer(gpu.pieces, offset: 0, index: 1)
    e.setFragmentBuffer(gpu.pieces, offset: 0, index: 1)
    let textures: [(MTLTexture, Int)] = [(gpu.bear, 0), (gpu.hug, 1), (gpu.page, 2), (gpu.rounded, 4), (cut, 5)]
    for (t, i) in textures { e.setVertexTexture(t, index: i); e.setFragmentTexture(t, index: i) }
    e.setVertexSamplerState(gpu.sampler, index: 0)
    e.setFragmentSamplerState(gpu.sampler, index: 0)
  }

  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

  func draw(in view: MTKView) {
    guard let gpu, let crystal, let material, let frozen, let coverage, let coverageMSAA, let glintBuffer else { return }
    let f = pending
    let state = f.scene
    let t = f.time
    let rotation = f.ring
    let loopTime = t - floor(t / 10) * 10
    let renderTime = !f.interactive && (loopTime <= 0.6 || loopTime >= 9.999) ? 0 : t
    let now = Date()
    values[3] = Float(state.t)
    values[5] = Float(f.tiltY / 8 * 0.25)
    values[6] = Float(max(-0.61, min(0.61, rotation + f.tiltX / 8 * 0.18)))
    values[7] = f.interactive ? 1 : 0
    values[8] = Float(state.gem); values[9] = Float(state.display); values[10] = Float(state.spotlight); values[11] = Float(state.reset)
    values[12] = Float(state.push); values[13] = Float(state.polish); values[14] = Float(state.hue); values[15] = f.reduced ? 1 : 0
    if f.interactive {
      for i in 0..<5 { values[16 + i] = Float(f.claims[i] >= 0 ? f.touchTime - f.claims[i] : -1000) }
    }
    values[21] = f.interactive ? 1 : 0
    values[24] = Float(state.polishX)
    values[28] = Float(f.tiltX); values[29] = Float(f.tiltY); values[30] = Float(f.jelly)
    values[44] = Float(state.breathX); values[45] = Float(state.breathY); values[46] = Float(state.breath); values[47] = Float(state.core)
    let previous = last
    let jumped = previous == nil || abs(renderTime - lastRenderTime) > 0.1 || abs(f.tiltX - (previous?.tiltX ?? 0)) > 1 || abs(f.tiltY - (previous?.tiltY ?? 0)) > 1
    let frameDt = min(0.1, max(0, now.timeIntervalSince(lastFrameDate)))
    values[31] = jumped || f.snapshot ? 1 : Float(frameDt)
    guard let command = gpu.queue.makeCommandBuffer() else { return }
    let priorLoop = lastRenderTime - floor(lastRenderTime / 10) * 10
    let stableHold = !f.snapshot && previous != nil && !f.interactive && !(previous?.interactive ?? true) && !f.reduced && !(previous?.reduced ?? true)
      && state.t >= 7.4 && state.t < 8.9 && priorLoop >= 7.4 && priorLoop < 8.9 && rotation == previous?.ring
      && f.tiltX == previous?.tiltX && f.tiltY == previous?.tiltY && f.jelly == previous?.jelly
    if (state.t >= 2.1 || state.breath > 0.0001 || f.reduced && state.gem > 0) && !stableHold {
      let rp = MTLRenderPassDescriptor()
      for (i, target) in [crystal, material].enumerated() {
        rp.colorAttachments[i].texture = target
        rp.colorAttachments[i].loadAction = .clear
        rp.colorAttachments[i].storeAction = .store
        rp.colorAttachments[i].clearColor = MTLClearColorMake(0, 0, 0, 0)
      }
      if let e = command.makeRenderCommandEncoder(descriptor: rp) {
        e.setRenderPipelineState(gpu.draw)
        bindMaterial(e, gpu, values, cut: frozen)
        e.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: gpu.sectors)
        e.endEncoding()
      }
      // The silhouette is drawn every time: a reused mask while the pieces settle (until 6.9 s) would lag.
      let mp = MTLRenderPassDescriptor()
      mp.colorAttachments[0].texture = coverageMSAA
      mp.colorAttachments[0].resolveTexture = coverage
      mp.colorAttachments[0].loadAction = .clear
      mp.colorAttachments[0].storeAction = .multisampleResolve
      mp.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0)
      if let e = command.makeRenderCommandEncoder(descriptor: mp) {
        e.setRenderPipelineState(gpu.coverage)
        values.withUnsafeBytes { e.setVertexBytes($0.baseAddress!, length: $0.count, index: 0) }
        e.setVertexBuffer(gpu.pieces, offset: 0, index: 1)
        e.setVertexBuffer(gpu.coverageVertices, offset: 0, index: 3)
        e.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: gpu.coverageCount)
        e.endEncoding()
      }
    }
    if state.gem > 0.001 && !f.reduced, let e = command.makeComputeCommandEncoder() {
      e.setComputePipelineState(gpu.glints)
      values.withUnsafeBytes { e.setBytes($0.baseAddress!, length: $0.count, index: 0) }
      e.setBuffer(gpu.pieces, offset: 0, index: 1)
      e.setBuffer(glintBuffer, offset: 0, index: 2)
      e.setTexture(crystal, index: 6)
      e.dispatchThreadgroups(MTLSize(width: 1, height: 1, depth: 1), threadsPerThreadgroup: MTLSize(width: 256, height: 1, depth: 1))
      e.endEncoding()
    }
    guard let drawable = view.currentDrawable else { return }
    let cp = MTLRenderPassDescriptor()
    cp.colorAttachments[0].texture = drawable.texture
    cp.colorAttachments[0].loadAction = .clear
    cp.colorAttachments[0].storeAction = .store
    cp.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0)
    if let e = command.makeRenderCommandEncoder(descriptor: cp) {
      let density = Double(values[2])
      let W = Int(values[0]), H = Int(values[1])
      func scissor(_ x: Int, _ y: Int, _ w: Int, _ h: Int) {
        let x0 = max(0, min(W, x)), y0 = max(0, min(H, y))
        e.setScissorRect(MTLScissorRect(x: x0, y: y0, width: max(1, min(W - x0, w)), height: max(1, min(H - y0, h))))
      }
      values.withUnsafeBytes { bytes in
        e.setVertexBytes(bytes.baseAddress!, length: bytes.count, index: 0)
        e.setFragmentBytes(bytes.baseAddress!, length: bytes.count, index: 0)
      }
      e.setFragmentBuffer(gpu.pieces, offset: 0, index: 1)
      e.setVertexBuffer(gpu.pieces, offset: 0, index: 1)
      e.setFragmentBuffer(glintBuffer, offset: 0, index: 2)
      e.setFragmentBuffer(gpu.portals, offset: 0, index: 3)
      e.setFragmentSamplerState(gpu.sampler, index: 0)
      e.setVertexSamplerState(gpu.sampler, index: 0)
      let textures: [(MTLTexture, Int)] = [(gpu.bear, 0), (gpu.hug, 1), (gpu.page, 2), (gpu.skin, 3), (gpu.rounded, 4), (frozen, 5), (crystal, 6), (material, 7), (coverage, 8)]
      for (tx, i) in textures { e.setFragmentTexture(tx, index: i); e.setVertexTexture(tx, index: i) }
      if state.t >= 2.1 || f.reduced && state.gem > 0 {
        scissor(Int(floor((L.crystal.minX - L.viewport.minX) * density)), Int(floor((L.crystal.minY - L.viewport.minY) * density)),
                Int(ceil(L.crystal.width * density)), Int(ceil(L.crystal.height * density)))
        e.setRenderPipelineState(gpu.composite)
        e.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
      }
      if state.t < 2.4 && state.breath > 0.0001 {
        let cx0 = L.crystal.minX, cx1 = L.crystal.maxX, cy0 = L.crystal.minY, cy1 = L.crystal.maxY
        let left = Int(floor((min(cx1, max(cx0, state.breathX - 40)) - L.viewport.minX) * density))
        let top = Int(floor((min(cy1, max(cy0, state.breathY - 40)) - L.viewport.minY) * density))
        let right = Int(ceil((max(cx0, min(cx1, state.breathX + 40)) - L.viewport.minX) * density))
        let bottom = Int(ceil((max(cy0, min(cy1, state.breathY + 40)) - L.viewport.minY) * density))
        scissor(left, top, max(1, right - left), max(1, bottom - top))
        e.setRenderPipelineState(gpu.breath)
        e.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
      }
      scissor(0, 0, W, H)
      e.setRenderPipelineState(gpu.overlay)
      e.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6, instanceCount: 53, baseInstance: gpu.sectors)
      e.endEncoding()
    }
    command.commit()
    command.waitUntilScheduled()
    drawable.present()
    hasDrawn = true
    drawn = true
    last = f
    lastRenderTime = renderTime
    lastFrameDate = now
  }
}

/// The transparent native overlay, placed over the design viewport (20, 140, 375 × 415).
struct FrostHeartRewardsGemView: UIViewRepresentable {
  let frame: FrostHeartRewardsGemFrame
  let width: Double

  func makeCoordinator() -> FrostHeartRewardsGemRenderer { FrostHeartRewardsGemRenderer() }
  func makeUIView(context: Context) -> MTKView {
    let view = MTKView(frame: .zero, device: FrostHeartRewardsGPU.available?.device)
    context.coordinator.configure(view)
    return view
  }
  func updateUIView(_ view: MTKView, context: Context) {
    let scale = view.window?.screen.scale ?? UIScreen.main.scale
    context.coordinator.render(view, frame, width: width, scale: scale)
  }
}
