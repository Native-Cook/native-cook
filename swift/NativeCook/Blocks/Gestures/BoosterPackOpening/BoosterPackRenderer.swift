import CoreGraphics
import ImageIO
import Metal
import MetalKit

/// Owns the Metal state of the block: the compiled shaders, the artwork, the off-screen
/// layer the pack is summed on, and the per-frame buffers. `draw` turns the experience's
/// scene into one frame.
final class BoosterPackRenderer {
  static let colorFormat = MTLPixelFormat.bgra8Unorm
  static let stencilFormat = MTLPixelFormat.stencil8
  static let sampleCount = 4

  let device: MTLDevice
  private let queue: MTLCommandQueue
  private let library: MTLLibrary
  private let vertexFunction: MTLFunction
  let sampler: MTLSamplerState
  let blank: MTLTexture

  private(set) var pack: MTLTexture?
  private(set) var cardFaces: [MTLTexture] = []
  private(set) var cardBack: MTLTexture?
  private var pattern: MTLTexture?
  private var patternSize = 0.0

  private var pipelines: [Int: MTLRenderPipelineState] = [:]
  private var stencils: [UInt32: MTLDepthStencilState] = [:]
  private var idleBuffers: [BoosterPackFrameBuffers] = []

  // The pack's own layer: its weighted poses are summed here before they meet the scene.
  private var layerColor: MTLTexture?
  private var layerResolve: MTLTexture?
  private var layerStencil: MTLTexture?
  private var layerRegion = BoosterPackRect.zero

  /// True once the pack, the five faces and the back are decoded.
  var loaded: Bool { pack != nil && cardFaces.count == BoosterPackCardReveal.CARD_IMAGES.count && cardBack != nil }

  init?() {
    guard let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue() else { return nil }
    self.device = device
    self.queue = queue
    do {
      library = try device.makeLibrary(source: BoosterPackShaders.source, options: nil)
    } catch {
      print("Booster Pack shaders did not compile: \(error)")
      return nil
    }
    guard let vertexFunction = library.makeFunction(name: "boosterPackVertex") else { return nil }
    self.vertexFunction = vertexFunction

    let samplerDescriptor = MTLSamplerDescriptor()
    samplerDescriptor.minFilter = .linear
    samplerDescriptor.magFilter = .linear
    samplerDescriptor.mipFilter = .linear
    samplerDescriptor.sAddressMode = .clampToEdge
    samplerDescriptor.tAddressMode = .clampToEdge
    let blankDescriptor = MTLTextureDescriptor.texture2DDescriptor(
      pixelFormat: .rgba8Unorm, width: 1, height: 1, mipmapped: false)
    guard let sampler = device.makeSamplerState(descriptor: samplerDescriptor),
      let blank = device.makeTexture(descriptor: blankDescriptor)
    else { return nil }
    self.sampler = sampler
    self.blank = blank

    // Compile every pipeline the scene uses now, so the first tear and the first cut do not
    // wait for one.
    for fragment in BoosterPackFragment.allCases { _ = pipeline(fragment, .over, writesColor: true) }
    for blend in [BoosterPackBlend.plus, .screen, .sourceAtop] { _ = pipeline(.paint, blend, writesColor: true) }
    _ = pipeline(.paint, .over, writesColor: false)
    _ = pipeline(.fold, .plus, writesColor: true)
    _ = pipeline(.riseLight, .plus, writesColor: true)

    pack = texture(named: "booster-pack")
    cardFaces = BoosterPackCardReveal.CARD_IMAGES.compactMap { texture(named: $0) }
    cardBack = texture(named: BoosterPackCardReveal.CARD_BACK_IMAGE)
  }

  // MARK: Resources

  /// Decodes a bundled image into a premultiplied texture, top row first, with mipmaps.
  private func texture(named name: String) -> MTLTexture? {
    guard let url = Bundle.main.url(forResource: name, withExtension: "webp"),
      let source = CGImageSourceCreateWithURL(url as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil),
      let space = CGColorSpace(name: CGColorSpace.sRGB)
    else { return nil }
    let width = image.width
    let height = image.height
    var bytes = [UInt8](repeating: 0, count: width * height * 4)
    guard let context = CGContext(
      data: &bytes, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
      space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
    else { return nil }
    context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
    let descriptor = MTLTextureDescriptor.texture2DDescriptor(
      pixelFormat: .rgba8Unorm, width: width, height: height, mipmapped: true)
    descriptor.usage = [.shaderRead, .renderTarget]
    guard let texture = device.makeTexture(descriptor: descriptor) else { return nil }
    texture.replace(
      region: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0, withBytes: bytes, bytesPerRow: width * 4)
    if let commands = queue.makeCommandBuffer(), let blit = commands.makeBlitCommandEncoder() {
      blit.generateMipmaps(for: texture)
      blit.endEncoding()
      commands.commit()
    }
    return texture
  }

  /// Builds what depends on the scene's size ahead of the frame that first needs it.
  func prepare(width: Double, height: Double) {
    guard width > 0, height > 0 else { return }
    _ = patternTexture(
      planeSize: (hypot(width, height) * 1.08).rounded(.up), artSize: max(width * 1.55, height * 0.82))
  }

  private func patternTexture(planeSize: Double, artSize: Double) -> MTLTexture? {
    if let pattern, patternSize == planeSize { return pattern }
    let mask = BoosterPackBackdrop.blurredPattern(planeSize: planeSize, artSize: artSize)
    let descriptor = MTLTextureDescriptor.texture2DDescriptor(
      pixelFormat: .r8Unorm, width: mask.side, height: mask.side, mipmapped: false)
    guard let texture = device.makeTexture(descriptor: descriptor) else { return nil }
    texture.replace(
      region: MTLRegionMake2D(0, 0, mask.side, mask.side), mipmapLevel: 0, withBytes: mask.bytes,
      bytesPerRow: mask.side)
    pattern = texture
    patternSize = planeSize
    return texture
  }

  func pipeline(_ fragment: BoosterPackFragment, _ blend: BoosterPackBlend, writesColor: Bool) -> MTLRenderPipelineState? {
    let fragmentIndex = BoosterPackFragment.allCases.firstIndex(of: fragment) ?? 0
    let key = fragmentIndex * 16 + blend.rawValue * 2 + (writesColor ? 1 : 0)
    if let pipeline = pipelines[key] { return pipeline }

    let vertices = MTLVertexDescriptor()
    vertices.attributes[0].format = .float2
    vertices.attributes[0].offset = 0
    vertices.attributes[1].format = .float2
    vertices.attributes[1].offset = 8
    vertices.attributes[2].format = .float4
    vertices.attributes[2].offset = 16
    vertices.layouts[0].stride = BoosterPackFrameBuffers.vertexStride

    let descriptor = MTLRenderPipelineDescriptor()
    descriptor.vertexFunction = vertexFunction
    descriptor.fragmentFunction = library.makeFunction(name: fragment.rawValue)
    descriptor.vertexDescriptor = vertices
    descriptor.rasterSampleCount = Self.sampleCount
    descriptor.stencilAttachmentPixelFormat = Self.stencilFormat
    guard let color = descriptor.colorAttachments[0] else { return nil }
    color.pixelFormat = Self.colorFormat
    color.writeMask = writesColor ? .all : []
    color.isBlendingEnabled = true
    // Every fragment returns premultiplied colour.
    switch blend {
    case .over:
      color.sourceRGBBlendFactor = .one
      color.destinationRGBBlendFactor = .oneMinusSourceAlpha
      color.sourceAlphaBlendFactor = .one
      color.destinationAlphaBlendFactor = .oneMinusSourceAlpha
    case .plus:
      color.sourceRGBBlendFactor = .one
      color.destinationRGBBlendFactor = .one
      color.sourceAlphaBlendFactor = .one
      color.destinationAlphaBlendFactor = .one
    case .screen:
      color.sourceRGBBlendFactor = .one
      color.destinationRGBBlendFactor = .oneMinusSourceColor
      color.sourceAlphaBlendFactor = .one
      color.destinationAlphaBlendFactor = .oneMinusSourceAlpha
    case .sourceAtop:
      color.sourceRGBBlendFactor = .destinationAlpha
      color.destinationRGBBlendFactor = .oneMinusSourceAlpha
      color.sourceAlphaBlendFactor = .zero
      color.destinationAlphaBlendFactor = .one
    }
    do {
      let pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
      pipelines[key] = pipeline
      return pipeline
    } catch {
      print("Booster Pack pipeline \(fragment.rawValue) failed: \(error)")
      return nil
    }
  }

  func stencilState(equal: Bool, readMask: UInt8, writeMask: UInt8, pass: MTLStencilOperation) -> MTLDepthStencilState? {
    let key = UInt32(readMask) | UInt32(writeMask) << 8 | UInt32(pass.rawValue) << 16 | (equal ? 1 << 24 : 0)
    if let state = stencils[key] { return state }
    let face = MTLStencilDescriptor()
    face.stencilCompareFunction = equal ? .equal : .always
    face.readMask = UInt32(readMask)
    face.writeMask = UInt32(writeMask)
    face.stencilFailureOperation = .keep
    face.depthFailureOperation = .keep
    face.depthStencilPassOperation = pass
    let descriptor = MTLDepthStencilDescriptor()
    descriptor.frontFaceStencil = face
    descriptor.backFaceStencil = face
    let state = device.makeDepthStencilState(descriptor: descriptor)
    stencils[key] = state
    return state
  }

  /// Sizes the pack's layer to the pack and a small margin, at the drawable's scale.
  private func prepareLayer(for scene: BoosterPackScene, scale: Double) -> Bool {
    let margin = 4.0
    let region = BoosterPackRect(
      x: scene.pack.x - margin, y: scene.pack.y - margin,
      width: scene.pack.width + margin * 2, height: scene.pack.height + margin * 2)
    let width = max(Int((region.width * scale).rounded(.up)), 1)
    let height = max(Int((region.height * scale).rounded(.up)), 1)
    if let layerResolve, layerResolve.width == width, layerResolve.height == height, region == layerRegion {
      return true
    }
    let resolve = MTLTextureDescriptor.texture2DDescriptor(
      pixelFormat: Self.colorFormat, width: width, height: height, mipmapped: false)
    resolve.usage = [.shaderRead, .renderTarget]
    resolve.storageMode = .private
    let color = MTLTextureDescriptor.texture2DDescriptor(
      pixelFormat: Self.colorFormat, width: width, height: height, mipmapped: false)
    color.textureType = .type2DMultisample
    color.sampleCount = Self.sampleCount
    color.usage = .renderTarget
    color.storageMode = .private
    let stencil = MTLTextureDescriptor.texture2DDescriptor(
      pixelFormat: Self.stencilFormat, width: width, height: height, mipmapped: false)
    stencil.textureType = .type2DMultisample
    stencil.sampleCount = Self.sampleCount
    stencil.usage = .renderTarget
    stencil.storageMode = .private
    layerResolve = device.makeTexture(descriptor: resolve)
    layerColor = device.makeTexture(descriptor: color)
    layerStencil = device.makeTexture(descriptor: stencil)
    layerRegion = region
    return layerResolve != nil && layerColor != nil && layerStencil != nil
  }

  // MARK: The frame

  func draw(_ scene: BoosterPackScene, in view: MTKView) {
    guard scene.width > 0, let pack,
      let drawable = view.currentDrawable,
      let mainPass = view.currentRenderPassDescriptor,
      let commands = queue.makeCommandBuffer(),
      let buffers = idleBuffers.popLast() ?? BoosterPackFrameBuffers(device: device)
    else { return }
    buffers.rewind()
    let scale = Double(view.drawableSize.width) / max(scene.width, 1)
    let whole = BoosterPackRect(x: 0, y: 0, width: scene.width, height: scene.height)

    // The intact pack and the folding one are summed on their own layer first.
    var layer: MTLTexture?
    if scene.intactOpacity + scene.previewOpacity > 0, prepareLayer(for: scene, scale: scale) {
      let pass = MTLRenderPassDescriptor()
      pass.colorAttachments[0].texture = layerColor
      pass.colorAttachments[0].resolveTexture = layerResolve
      pass.colorAttachments[0].loadAction = .clear
      pass.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
      pass.colorAttachments[0].storeAction = .multisampleResolve
      pass.stencilAttachment.texture = layerStencil
      pass.stencilAttachment.loadAction = .clear
      pass.stencilAttachment.clearStencil = 0
      pass.stencilAttachment.storeAction = .dontCare
      if let encoder = commands.makeRenderCommandEncoder(descriptor: pass) {
        let canvas = BoosterPackCanvas(
          renderer: self, encoder: encoder, buffers: buffers, width: scene.width, height: scene.height,
          punch: 1, target: layerRegion)
        drawPackLayer(scene, canvas, pack)
        encoder.endEncoding()
        layer = layerResolve
      }
    }

    mainPass.colorAttachments[0].loadAction = .clear
    mainPass.colorAttachments[0].clearColor = MTLClearColor(red: 0.67, green: 0.76, blue: 0.9, alpha: 1)
    mainPass.stencilAttachment.loadAction = .clear
    mainPass.stencilAttachment.clearStencil = 0
    mainPass.stencilAttachment.storeAction = .dontCare
    if let encoder = commands.makeRenderCommandEncoder(descriptor: mainPass) {
      let canvas = BoosterPackCanvas(
        renderer: self, encoder: encoder, buffers: buffers, width: scene.width, height: scene.height,
        punch: scene.punch, target: whole)
      drawPackScene(scene, canvas, pack, layer)
      drawBackdrop(scene, canvas)
      drawCards(scene, canvas)
      encoder.endEncoding()
    }

    commands.addCompletedHandler { [weak self] _ in
      DispatchQueue.main.async { self?.idleBuffers.append(buffers) }
    }
    // Present inside the Core Animation transaction, so this frame lands together with
    // the SwiftUI layers drawn over it.
    commands.commit()
    commands.waitUntilScheduled()
    drawable.present()
  }

  private func drawPackLayer(_ s: BoosterPackScene, _ canvas: BoosterPackCanvas, _ pack: MTLTexture) {
    let artwork = BoosterPackPaint.image(pack, s.pack)
    canvas.rect(s.pack, artwork, opacity: s.intactOpacity)
    canvas.fill(s.previewBottomMask, artwork, opacity: s.previewOpacity, blend: .plus)
    drawFold(s, canvas, pack, opacity: s.previewOpacity, blend: .plus)
    // Inside the pack layer, so the sheen keeps the artwork's alpha.
    if s.sheenOpacity > 0.001 {
      canvas.rect(
        s.pack,
        .linear(s.sheenStart, s.sheenEnd, [
          BoosterPackColor(255, 255, 255, 0), BoosterPackColor(255, 255, 255, 0.1),
          BoosterPackColor(255, 255, 255, 0.3), BoosterPackColor(255, 255, 255, 0.62),
          BoosterPackColor(214, 250, 255, 0.28), BoosterPackColor(190, 246, 255, 0.1),
          BoosterPackColor(255, 255, 255, 0),
        ], [0, 0.3, 0.45, 0.5, 0.55, 0.7, 1]),
        opacity: s.sheenOpacity, blend: .sourceAtop)
    }
  }

  private func drawFold(
    _ s: BoosterPackScene, _ canvas: BoosterPackCanvas, _ pack: MTLTexture, opacity: Double, blend: BoosterPackBlend
  ) {
    canvas.mesh(
      .fold, vertices: s.previewFoilVertices, textures: s.previewMeshTextures,
      indices: BoosterPackGeometry.PREVIEW_FOIL_MESH_INDICES,
      a: [s.pack.x, s.pack.y, s.pack.width, s.pack.height], b: s.fold.fold,
      arrays: s.fold.foldSurface + paddedCross(s.fold.foldCross), texture: pack, opacity: opacity, blend: blend)
  }

  /// Three values per column become four, the width of a row in the array buffer.
  private func paddedCross(_ values: [Double]) -> [Double] {
    var padded: [Double] = []
    padded.reserveCapacity(values.count / 3 * 4)
    for index in stride(from: 0, to: values.count - 2, by: 3) {
      padded.append(contentsOf: [values[index], values[index + 1], values[index + 2], 0])
    }
    return padded
  }

  private func drawPackScene(
    _ s: BoosterPackScene, _ canvas: BoosterPackCanvas, _ pack: MTLTexture, _ layer: MTLTexture?
  ) {
    let width = s.width
    let height = s.height
    // Punch scaling must not uncover the edges, so the background is drawn a little larger.
    let whole = BoosterPackRect(x: -width * 0.05, y: -height * 0.05, width: width * 1.1, height: height * 1.1)
    let top = CGPoint(x: 0, y: 0)
    let bottom = CGPoint(x: 0, y: height)
    canvas.rect(whole, .linear(top, bottom, [
      BoosterPackColor(0xAAC2E5), BoosterPackColor(0xCADBF8), BoosterPackColor(0xE7FFFF), BoosterPackColor(0xE3F9FD),
    ], [0.08, 0.33, 0.69, 0.93]))
    canvas.rect(whole, .linear(top, bottom, [
      BoosterPackColor(0xC9D7F9), BoosterPackColor(0xC5D6F7), BoosterPackColor(0x9DB6DD), BoosterPackColor(0x7EA1CE),
    ], [0.08, 0.33, 0.69, 0.93]), opacity: s.backgroundMorph)
    // Dim the background while tearing, keeping the pack and card clear.
    canvas.rect(whole, .linear(top, bottom, [
      BoosterPackColor(0x050607), BoosterPackColor(0x07090D), BoosterPackColor(0x181827),
    ], [0, 0.46, 0.78]), opacity: s.dimOpacity)

    canvas.roundedRect(
      BoosterPackRect(
        x: s.pack.x - width * 0.018, y: s.pack.y - width * 0.012,
        width: s.pack.width + width * 0.036, height: s.pack.height + width * 0.02),
      radius: width * 0.035, .color(BoosterPackColor(0x63F8FF)), sigma: width * 0.055, opacity: s.packAuraOpacity)

    if let layer {
      let region = layerRegion
      canvas.rect(region, .image(layer, region))
    }

    canvas.mesh(
      .foil, vertices: s.foilVertices, textures: s.meshTextures, indices: BoosterPackGeometry.FOIL_MESH_INDICES,
      a: [s.pack.x, s.pack.y, s.pack.width, s.pack.height], b: s.foil.material, c: [s.foil.pixelScale, 0, 0, 0],
      arrays: s.foil.surface, texture: pack, opacity: s.meshOpacity)
    canvas.stroke(
      s.foilEdge, width: width * 0.006, .color(BoosterPackColor(0x48F3F6)), sigma: width * 0.009,
      opacity: s.meshOpacity)

    let light = s.mouthLighting
    func mouth(_ vertices: [CGPoint], _ textures: [CGPoint], _ indices: [Int], rim: Bool, opacity: Double) {
      canvas.mesh(
        .mouth, vertices: vertices, textures: textures, indices: indices,
        a: [light.center, light.secondCenter, light.strength, light.opening],
        b: [light.travel, rim ? 1 : 0, light.liningCenter, light.liningLight],
        c: [light.sideLight, light.liningTilt, 0, 0],
        d: [light.liningTint[0], light.liningTint[1], light.liningTint[2], 0],
        arrays: paddedSurface(light.liningSurface), opacity: opacity)
    }

    if s.cavityOpacity > 0 {
      canvas.setPose(s.bodyPose)
      let clip = canvas.clip(s.mouthSurfaceOutline)
      mouth(s.mouthSurfaceVertices, s.mouthSurfaceTextures, BoosterPackGeometry.MOUTH_WALL_INDICES,
        rim: false, opacity: s.cavityOpacity * s.cavityStrength)
      canvas.unclip(clip)
      canvas.resetMatrix()
    }

    let cardWidth = width * BoosterPackModel.REFERENCE_LAYOUT.card.width
    let cardHeight = height * BoosterPackModel.REFERENCE_LAYOUT.card.height
    let cardX = width * BoosterPackModel.REFERENCE_LAYOUT.card.centerX - cardWidth / 2
    if s.riseLightOpacity > 0.001 {
      canvas.quad(
        .riseLight, BoosterPackRect(x: 0, y: 0, width: width, height: height),
        a: [width * 0.5, s.cardTop + cardHeight * 0.46, width * 1.02, s.postCutMs * 0.0021], b: [1, 0, 0, 0],
        opacity: s.riseLightOpacity, blend: .plus)
    }

    // The first card rises out of the mouth before the stack takes over.
    if s.cardOpacity > 0, s.cardsOpacity == 0 || s.backdropOpacity < 1, let face = cardFaces.first {
      canvas.roundedRect(
        BoosterPackRect(
          x: cardX - width * 0.004, y: s.cardTop, width: cardWidth + width * 0.008,
          height: cardHeight + width * 0.008),
        radius: width * 0.02, .color(BoosterPackColor(0x4B5474)), sigma: width * 0.01, opacity: s.cardShadowOpacity)
      let rect = BoosterPackRect(x: cardX, y: s.cardTop, width: cardWidth, height: cardHeight)
      canvas.rect(rect, .image(face, rect), opacity: s.cardOpacity)
    }

    guard s.bodyOpacity > 0, !s.body.vertices.isEmpty else {
      drawFold(s, canvas, pack, opacity: s.flatFoilOpacity, blend: .over)
      return
    }

    canvas.setPose(s.bodyPose)
    if s.cardContactShadowOpacity > 0 {
      let builder = BoosterPackPathBuilder()
      builder.moveTo(s.pack.x - width * 0.02, s.pack.y - width * 0.01)
      builder.lineTo(s.pack.x + s.pack.width + width * 0.02, s.pack.y - width * 0.01)
      builder.lineTo(s.pack.x + s.pack.width + width * 0.02, s.pack.y + s.pack.height + width * 0.01)
      builder.lineTo(s.pack.x - width * 0.02, s.pack.y + s.pack.height + width * 0.01)
      builder.close()
      let clip = canvas.clip(builder.build())
      canvas.stroke(
        s.mouthFrontEdgePath, width: width * 0.012, .color(BoosterPackColor(0x05040B)), sigma: width * 0.006,
        opacity: s.cardContactShadowOpacity)
      canvas.unclip(clip)
    }

    let bodyClip = canvas.clip(s.postCutBottomMask)
    canvas.fill(
      s.entryBodyWedgePath,
      .linear(CGPoint(x: 0, y: s.seamY + width * 0.004), CGPoint(x: 0, y: s.seamY + width * 0.1), [
        BoosterPackColor(0xB96FE7), BoosterPackColor(0xB52CE8), BoosterPackColor(0xD052EE),
      ], [0, 0.42, 1]),
      opacity: s.bodyOpacity * s.cavityStrength)
    let rect = s.bodyTextureRect
    canvas.mesh(
      .body, vertices: s.body.vertices, textures: s.body.textures, indices: BoosterPackGeometry.BODY_MESH_INDICES,
      a: [rect.x, rect.y, rect.width, rect.height], b: [s.bodyDeglareStrength, 0, 0, 0], texture: pack,
      opacity: s.bodyOpacity)
    canvas.unclip(bodyClip)

    let rimClip = canvas.clip(s.rimSurfaceOutline)
    mouth(s.rimSurfaceVertices, s.rimSurfaceTextures, BoosterPackGeometry.MOUTH_MESH_INDICES,
      rim: true, opacity: s.bodyOpacity * s.edgeOpacity)
    canvas.unclip(rimClip)
    canvas.stroke(
      s.mouthFrontEdgePath, width: width * 0.023,
      .radial(s.mouthGlowCenter, s.pack.width * 0.30, [
        BoosterPackColor(150, 255, 255, 0.8), BoosterPackColor(20, 242, 255, 0.48), BoosterPackColor(0, 190, 255, 0),
      ], [0, 0.25, 1]),
      sigma: width * 0.013, opacity: s.bodyOpacity * s.cavitySpecularOpacity, blend: .screen)
    canvas.resetMatrix()

    drawFold(s, canvas, pack, opacity: s.flatFoilOpacity, blend: .over)
  }

  private func paddedSurface(_ values: [Double]) -> [Double] {
    var padded: [Double] = []
    for index in stride(from: 0, to: values.count - 1, by: 2) {
      padded.append(contentsOf: [values[index], values[index + 1], 0, 0])
    }
    return padded
  }

  private func drawBackdrop(_ s: BoosterPackScene, _ canvas: BoosterPackCanvas) {
    guard s.backdropOpacity > 0 else { return }
    let width = s.width
    let height = s.height
    let planeSize = (hypot(width, height) * 1.08).rounded(.up)
    let artSize = max(width * 1.55, height * 0.82)
    guard let pattern = patternTexture(planeSize: planeSize, artSize: artSize) else { return }
    let plane = BoosterPackRect(
      x: (width - planeSize) / 2, y: (height - planeSize) / 2, width: planeSize, height: planeSize)
    let zoom = s.reducedMotion
      ? BoosterPackBackdrop.RESTING_SCALE
      : BoosterPackBackdrop.ENTRY_SCALE
        + (BoosterPackBackdrop.RESTING_SCALE - BoosterPackBackdrop.ENTRY_SCALE) * BoosterPackModel.clamp(s.backdropZoom, 0, 1)
    let angle = s.backdropRotation * Double.pi / 180
    let cosine = cos(angle) * zoom
    let sine = sin(angle) * zoom
    let cx = width / 2
    let cy = height / 2
    canvas.matrix = [
      cosine, -sine, cx - cosine * cx + sine * cy,
      sine, cosine, cy - sine * cx - cosine * cy,
      0, 0, 1,
    ]
    canvas.rect(
      plane,
      .masked(pattern, plane, BoosterPackColor(BoosterPackBackdrop.background), BoosterPackColor(BoosterPackBackdrop.pattern)),
      opacity: s.backdropOpacity)
    canvas.resetMatrix()

    for particle in BoosterPackBackdrop.particles {
      let cycle = (particle.phase + s.particlePhase / particle.duration).truncatingRemainder(dividingBy: 1)
      let y = height + particle.size + (-particle.size - (height + particle.size)) * cycle
      let peak = s.reducedMotion ? particle.maxOpacity * 0.34 : particle.maxOpacity
      let later = s.reducedMotion ? particle.maxOpacity * 0.24 : particle.maxOpacity * 0.68
      let opacity = BoosterPackModel.interpolateKeyframes(cycle, [0, 0.12, 0.58, 1], [0, peak, later, 0]) * s.backdropOpacity
      let x = particle.left * width - particle.size / 2
        + (s.reducedMotion ? 0 : sin(cycle * 2 * Double.pi + particle.waveOffset) * particle.drift)
      let dot = BoosterPackRect(x: x, y: y, width: particle.size, height: particle.size)
      canvas.roundedRect(
        dot, radius: particle.size / 2, .color(BoosterPackColor(255, 255, 255, 0.58)),
        sigma: max(4, particle.size) / 2, opacity: opacity)
      canvas.roundedRect(dot, radius: particle.size / 2, .color(BoosterPackColor(255, 255, 255, 0.96)), opacity: opacity)
    }
  }

  private func drawCards(_ s: BoosterPackScene, _ canvas: BoosterPackCanvas) {
    guard s.cardsOpacity > 0, let cardBack, cardFaces.count == s.cards.count else { return }
    let width = s.width
    let height = s.height
    let whole = BoosterPackRect(x: 0, y: 0, width: width, height: height)

    // The coloured backdrop of an epic card's turn.
    if s.auraOpacity > 0.001 {
      canvas.rect(whole, .linear(.zero, CGPoint(x: width, y: height), [
        BoosterPackColor(0xF8AD72), BoosterPackColor(0xE055AB), BoosterPackColor(0xCF4BE3),
        BoosterPackColor(0x777AF1), BoosterPackColor(0x67D8E8),
      ], nil), opacity: s.auraOpacity)
      canvas.rect(whole, .radial(CGPoint(x: width * 0.5, y: height * 0.46), height * 0.58, [
        BoosterPackColor(255, 251, 233, 140.0 / 255.0), BoosterPackColor(255, 248, 239, 0),
      ], nil), opacity: s.auraOpacity)
    }

    for (id, opacity) in s.sparkleOpacity.enumerated() where opacity > 0.003 {
      let x = width * (0.06 + Double((id * 37 + 17) % 89) / 100)
      let y = height * (0.17 + Double((id * 29 + 3) % 63) / 100)
      let size = width * (id % 3 == 0 ? 0.015 : 0.007)
      let radius = size * 1.4
      canvas.roundedRect(
        BoosterPackRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2), radius: radius,
        .color(BoosterPackColor(0xFFF4BB, 0.55)), sigma: size * 1.5, opacity: opacity)
      let points = BoosterPackOverlayPaths.markPoints(x, y, size, star: false)
      canvas.triangles(
        [points[0], points[1], points[2], points[0], points[2], points[3]],
        .color(BoosterPackColor(0xFFFEF1)), opacity: opacity)
    }

    let cardWidth = width * BoosterPackModel.REFERENCE_LAYOUT.card.width
    let cardHeight = height * BoosterPackModel.REFERENCE_LAYOUT.card.height
    let radius = cardWidth * 0.025
    let face = BoosterPackRect(x: -cardWidth / 2, y: -cardHeight / 2, width: cardWidth, height: cardHeight)
    func grown(_ by: Double, _ dy: Double = 0) -> BoosterPackRect {
      BoosterPackRect(
        x: face.x - by, y: face.y - by + dy, width: face.width + by * 2, height: face.height + by * 2)
    }
    for cardIndex in stride(from: s.cards.count - 1, through: 0, by: -1) {
      let card = s.cards[cardIndex]
      if card.frontOpacity > 0 {
        canvas.matrix = card.frontMatrix
        canvas.roundedRect(
          grown(0.7, 1.7), radius: radius, .color(BoosterPackColor(0x43516D, 0.26)), sigma: width * 0.012,
          opacity: card.frontOpacity)
        canvas.roundedRect(grown(0.6), radius: radius, .color(BoosterPackColor(0xF6F7FC)), opacity: card.frontOpacity)
        canvas.quad(
          .card, face, a: [cardWidth, cardHeight, radius, 0],
          b: [card.tilt.x, card.tilt.y, card.holo, card.shine], c: [card.sweep, card.flash, 0, 0],
          texture: cardFaces[cardIndex], opacity: card.frontOpacity * card.artworkOpacity)
        canvas.roundedRect(
          face, radius: radius, .color(BoosterPackColor(0xF6FEFF)), sigma: 3, strokeWidth: 2.2,
          opacity: card.borderGlow * card.frontOpacity)
      }
      if card.backOpacity > 0 {
        canvas.matrix = card.backMatrix
        canvas.roundedRect(grown(0.9), radius: radius, .color(BoosterPackColor(0xE1E4EE)), opacity: card.backOpacity)
        canvas.quad(.card, face, a: [cardWidth, cardHeight, radius, 0], texture: cardBack, opacity: card.backOpacity)
      }
    }
    canvas.resetMatrix()
  }
}
