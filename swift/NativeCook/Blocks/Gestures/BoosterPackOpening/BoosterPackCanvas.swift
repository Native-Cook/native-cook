import CoreGraphics
import Metal

/// A colour with straight alpha, as the paints are written.
struct BoosterPackColor {
  var r: Float
  var g: Float
  var b: Float
  var a: Float

  init(_ hex: UInt32, _ alpha: Double = 1) {
    r = Float((hex >> 16) & 0xFF) / 255
    g = Float((hex >> 8) & 0xFF) / 255
    b = Float(hex & 0xFF) / 255
    a = Float(alpha)
  }

  init(_ red: Double, _ green: Double, _ blue: Double, _ alpha: Double) {
    r = Float(red / 255)
    g = Float(green / 255)
    b = Float(blue / 255)
    a = Float(alpha)
  }
}

/// How a draw meets what is already there.
enum BoosterPackBlend: Int {
  case over, plus, screen, sourceAtop
}

/// The fragment functions of `BoosterPackShaders`.
enum BoosterPackFragment: String, CaseIterable {
  case paint = "boosterPackPaintFragment"
  case fold = "boosterPackFoldFragment"
  case foil = "boosterPackFoilFragment"
  case body = "boosterPackBodyFragment"
  case mouth = "boosterPackMouthFragment"
  case riseLight = "boosterPackRiseLightFragment"
  case card = "boosterPackCardFragment"
}

/// What a shape is filled with.
enum BoosterPackPaint {
  case color(BoosterPackColor)
  /// Colours along the line from the first point to the second; evenly spaced without stops.
  case linear(CGPoint, CGPoint, [BoosterPackColor], [Double]?)
  case radial(CGPoint, Double, [BoosterPackColor], [Double]?)
  /// A texture stretched over a rectangle of the scene, sampled at the texture coordinate.
  case image(MTLTexture, BoosterPackRect)
  /// Two colours mixed by a single-channel texture.
  case masked(MTLTexture, BoosterPackRect, BoosterPackColor, BoosterPackColor)
}

/// The per-frame buffers one command buffer draws from.
final class BoosterPackFrameBuffers {
  static let vertexStride = 32
  static let uniformStride = 512
  static let vertexCapacity = 48_000
  static let uniformCapacity = 384
  static let arrayCapacity = 4_096

  let vertices: MTLBuffer
  let uniforms: MTLBuffer
  let arrays: MTLBuffer
  var vertexCount = 0
  var uniformCount = 0
  var arrayCount = 0

  init?(device: MTLDevice) {
    guard
      let vertices = device.makeBuffer(length: Self.vertexStride * Self.vertexCapacity, options: .storageModeShared),
      let uniforms = device.makeBuffer(length: Self.uniformStride * Self.uniformCapacity, options: .storageModeShared),
      let arrays = device.makeBuffer(length: 16 * Self.arrayCapacity, options: .storageModeShared)
    else { return nil }
    self.vertices = vertices
    self.uniforms = uniforms
    self.arrays = arrays
  }

  func rewind() {
    vertexCount = 0
    uniformCount = 0
    arrayCount = 0
  }
}

/// The drawing calls of one render pass: meshes with a material, filled and clipping paths,
/// blurred strokes and rounded rectangles, all through one transform. It plays the part the
/// Skia canvas plays in the React Native block.
///
/// Paths are filled and clipped through the stencil buffer (a fan that inverts a bit, then a
/// covering quad), so any contour the geometry builds is drawn as built; multisampling
/// antialiases their edges. Blur is analytic: a Gaussian profile across a stroke or around a
/// rounded rectangle.
final class BoosterPackCanvas {
  private let renderer: BoosterPackRenderer
  private let encoder: MTLRenderCommandEncoder
  private let buffers: BoosterPackFrameBuffers
  private let view: (width: Float, height: Float, punch: Float)
  private let target: BoosterPackRect

  /// Row-major homography from local points to scene points.
  var matrix: [Double] = BoosterPackCanvas.identity
  static let identity: [Double] = [1, 0, 0, 0, 1, 0, 0, 0, 1]

  private var clipMask: UInt8 = 0
  private var nextClipBit: UInt8 = 1
  private static let fillBit: UInt8 = 0x80

  init(
    renderer: BoosterPackRenderer, encoder: MTLRenderCommandEncoder, buffers: BoosterPackFrameBuffers,
    width: Double, height: Double, punch: Double, target: BoosterPackRect
  ) {
    self.renderer = renderer
    self.encoder = encoder
    self.buffers = buffers
    view = (Float(width), Float(height), Float(punch))
    self.target = target
    encoder.setVertexBuffer(buffers.vertices, offset: 0, index: 0)
    encoder.setFragmentSamplerState(renderer.sampler, index: 0)
    encoder.setCullMode(.none)
  }

  /// Translation and uniform scale about an origin, as the pack body is posed.
  func setPose(_ pose: BoosterPackLightingPose) {
    let s = pose.scale
    matrix = [
      s, 0, pose.originX - s * pose.originX + pose.translateX,
      0, s, pose.originY - s * pose.originY + pose.translateY,
      0, 0, 1,
    ]
  }

  func resetMatrix() { matrix = Self.identity }

  // MARK: Vertices

  private func push(_ x: Double, _ y: Double, _ u: Double, _ v: Double,
                    _ e0: Double = 0, _ e1: Double = 0, _ e2: Double = 0, _ e3: Double = 0) {
    guard buffers.vertexCount < BoosterPackFrameBuffers.vertexCapacity else { return }
    let pointer = buffers.vertices.contents()
      .advanced(by: buffers.vertexCount * BoosterPackFrameBuffers.vertexStride)
      .assumingMemoryBound(to: Float.self)
    pointer[0] = Float(x)
    pointer[1] = Float(y)
    pointer[2] = Float(u)
    pointer[3] = Float(v)
    pointer[4] = Float(e0)
    pointer[5] = Float(e1)
    pointer[6] = Float(e2)
    pointer[7] = Float(e3)
    buffers.vertexCount += 1
  }

  private func pushQuad(_ rect: BoosterPackRect) -> Range<Int> {
    let start = buffers.vertexCount
    let x0 = rect.x, y0 = rect.y, x1 = rect.x + rect.width, y1 = rect.y + rect.height
    push(x0, y0, x0, y0)
    push(x1, y0, x1, y0)
    push(x0, y1, x0, y1)
    push(x0, y1, x0, y1)
    push(x1, y0, x1, y0)
    push(x1, y1, x1, y1)
    return start..<buffers.vertexCount
  }

  // MARK: Draws

  private struct Parameters {
    var a: [Double] = [0, 0, 0, 0]
    var b: [Double] = [0, 0, 0, 0]
    var c: [Double] = [0, 0, 0, 0]
    var d: [Double] = [0, 0, 0, 0]
    var colors: [BoosterPackColor] = []
    var stops: [Double] = []
    var texture: MTLTexture?
    var arrays: [Double]?
  }

  private enum Stencil {
    /// Draw where the active clips allow.
    case test
    /// Toggle a bit under the fan of a path; nothing is drawn.
    case mark(UInt8)
    /// Draw where a marked path and the clips allow, clearing the mark.
    case cover
  }

  private func draw(
    _ fragment: BoosterPackFragment, _ range: Range<Int>, _ parameters: Parameters,
    opacity: Double, blend: BoosterPackBlend = .over, stencil: Stencil = .test
  ) {
    guard !range.isEmpty, buffers.uniformCount < BoosterPackFrameBuffers.uniformCapacity else { return }
    var writesColor = true
    switch stencil {
    case .test:
      encoder.setDepthStencilState(renderer.stencilState(
        equal: clipMask != 0, readMask: clipMask, writeMask: 0, pass: .keep))
      encoder.setStencilReferenceValue(UInt32(clipMask))
    case let .mark(bit):
      writesColor = false
      encoder.setDepthStencilState(renderer.stencilState(
        equal: clipMask != 0, readMask: clipMask, writeMask: bit, pass: .invert))
      encoder.setStencilReferenceValue(UInt32(clipMask))
    case .cover:
      let mask = clipMask | Self.fillBit
      encoder.setDepthStencilState(renderer.stencilState(
        equal: true, readMask: mask, writeMask: Self.fillBit, pass: .zero))
      encoder.setStencilReferenceValue(UInt32(mask))
    }
    guard let pipeline = renderer.pipeline(fragment, blend, writesColor: writesColor) else { return }
    encoder.setRenderPipelineState(pipeline)

    let offset = buffers.uniformCount * BoosterPackFrameBuffers.uniformStride
    let u = buffers.uniforms.contents().advanced(by: offset).assumingMemoryBound(to: Float.self)
    for row in 0..<3 {
      for column in 0..<3 { u[row * 4 + column] = Float(matrix[row * 3 + column]) }
      u[row * 4 + 3] = 0
    }
    u[12] = view.width
    u[13] = view.height
    u[14] = view.punch
    u[15] = Float(opacity)
    u[16] = Float(target.x)
    u[17] = Float(target.y)
    u[18] = Float(target.width)
    u[19] = Float(target.height)
    for (row, values) in [parameters.a, parameters.b, parameters.c, parameters.d].enumerated() {
      for index in 0..<4 { u[20 + row * 4 + index] = Float(values[index]) }
    }
    for index in 0..<8 {
      let color = index < parameters.colors.count ? parameters.colors[index] : BoosterPackColor(0, 0)
      u[36 + index * 4] = color.r
      u[37 + index * 4] = color.g
      u[38 + index * 4] = color.b
      u[39 + index * 4] = color.a
      u[68 + index] = index < parameters.stops.count ? Float(parameters.stops[index]) : 1
    }
    buffers.uniformCount += 1
    encoder.setVertexBuffer(buffers.uniforms, offset: offset, index: 1)
    encoder.setFragmentBuffer(buffers.uniforms, offset: offset, index: 1)

    if let arrays = parameters.arrays {
      let rows = (arrays.count + 3) / 4
      guard buffers.arrayCount + rows <= BoosterPackFrameBuffers.arrayCapacity else { return }
      let arrayOffset = buffers.arrayCount * 16
      let pointer = buffers.arrays.contents().advanced(by: arrayOffset).assumingMemoryBound(to: Float.self)
      for (index, value) in arrays.enumerated() { pointer[index] = Float(value) }
      buffers.arrayCount += rows
      encoder.setFragmentBuffer(buffers.arrays, offset: arrayOffset, index: 2)
    } else {
      encoder.setFragmentBuffer(buffers.arrays, offset: 0, index: 2)
    }
    encoder.setFragmentTexture(parameters.texture ?? renderer.blank, index: 0)
    encoder.drawPrimitives(type: .triangle, vertexStart: range.lowerBound, vertexCount: range.count)
  }

  private func parameters(for paint: BoosterPackPaint) -> Parameters {
    var p = Parameters()
    func spread(_ count: Int, _ stops: [Double]?) -> [Double] {
      stops ?? (0..<count).map { count > 1 ? Double($0) / Double(count - 1) : 0 }
    }
    switch paint {
    case let .color(color):
      p.colors = [color]
    case let .linear(from, to, colors, stops):
      p.c = [1, Double(colors.count), 0, 0]
      p.d = [from.x, from.y, to.x, to.y]
      p.colors = colors
      p.stops = spread(colors.count, stops)
    case let .radial(center, radius, colors, stops):
      p.c = [2, Double(colors.count), 0, 0]
      p.d = [center.x, center.y, radius, 0]
      p.colors = colors
      p.stops = spread(colors.count, stops)
    case let .image(texture, rect):
      p.c = [3, 0, 0, 0]
      p.d = [rect.x, rect.y, rect.width, rect.height]
      p.texture = texture
    case let .masked(texture, rect, low, high):
      p.c = [4, 0, 0, 0]
      p.d = [rect.x, rect.y, rect.width, rect.height]
      p.colors = [low, high]
      p.texture = texture
    }
    return p
  }

  // MARK: Paths

  /// The contours of a path as polylines.
  static func flatten(_ path: CGPath) -> [[CGPoint]] {
    var contours: [[CGPoint]] = []
    var current: [CGPoint] = []
    path.applyWithBlock { element in
      let points = element.pointee.points
      switch element.pointee.type {
      case .moveToPoint:
        if !current.isEmpty { contours.append(current) }
        current = [points[0]]
      case .addLineToPoint:
        current.append(points[0])
      case .addQuadCurveToPoint:
        guard let from = current.last else { return }
        for step in 1...6 {
          let t = Double(step) / 6
          let s = 1 - t
          current.append(CGPoint(
            x: s * s * from.x + 2 * s * t * points[0].x + t * t * points[1].x,
            y: s * s * from.y + 2 * s * t * points[0].y + t * t * points[1].y))
        }
      case .addCurveToPoint:
        guard let from = current.last else { return }
        for step in 1...6 {
          let t = Double(step) / 6
          let s = 1 - t
          current.append(CGPoint(
            x: s * s * s * from.x + 3 * s * s * t * points[0].x + 3 * s * t * t * points[1].x + t * t * t * points[2].x,
            y: s * s * s * from.y + 3 * s * s * t * points[0].y + 3 * s * t * t * points[1].y + t * t * t * points[2].y))
        }
      case .closeSubpath:
        break
      @unknown default:
        break
      }
    }
    if !current.isEmpty { contours.append(current) }
    return contours
  }

  private func pushFan(_ path: CGPath) -> (range: Range<Int>, bounds: BoosterPackRect)? {
    let start = buffers.vertexCount
    var minX = Double.infinity, minY = Double.infinity, maxX = -Double.infinity, maxY = -Double.infinity
    for contour in Self.flatten(path) where contour.count >= 3 {
      let anchor = contour[0]
      for point in contour {
        minX = min(minX, point.x)
        minY = min(minY, point.y)
        maxX = max(maxX, point.x)
        maxY = max(maxY, point.y)
      }
      for index in 1..<(contour.count - 1) {
        push(anchor.x, anchor.y, anchor.x, anchor.y)
        push(contour[index].x, contour[index].y, contour[index].x, contour[index].y)
        push(contour[index + 1].x, contour[index + 1].y, contour[index + 1].x, contour[index + 1].y)
      }
    }
    guard buffers.vertexCount > start else { return nil }
    return (start..<buffers.vertexCount, BoosterPackRect(x: minX, y: minY, width: maxX - minX, height: maxY - minY))
  }

  /// Restricts what follows to the inside of a path; returns the token `unclip` takes.
  func clip(_ path: CGPath) -> UInt8 {
    let bit = nextClipBit
    guard bit != Self.fillBit, let fan = pushFan(path) else { return 0 }
    nextClipBit <<= 1
    draw(.paint, fan.range, Parameters(), opacity: 1, stencil: .mark(bit))
    clipMask |= bit
    return bit
  }

  func unclip(_ bit: UInt8) { clipMask &= ~bit }

  func fill(_ path: CGPath, _ paint: BoosterPackPaint, opacity: Double = 1, blend: BoosterPackBlend = .over) {
    guard opacity > 0, let fan = pushFan(path) else { return }
    draw(.paint, fan.range, Parameters(), opacity: 1, stencil: .mark(Self.fillBit))
    draw(.paint, pushQuad(fan.bounds), parameters(for: paint), opacity: opacity, blend: blend, stencil: .cover)
  }

  // MARK: Shapes

  func rect(_ rect: BoosterPackRect, _ paint: BoosterPackPaint, opacity: Double = 1, blend: BoosterPackBlend = .over) {
    guard opacity > 0 else { return }
    draw(.paint, pushQuad(rect), parameters(for: paint), opacity: opacity, blend: blend)
  }

  /// A rounded rectangle, filled or stroked, optionally blurred with a Gaussian of `sigma`.
  func roundedRect(
    _ rect: BoosterPackRect, radius: Double, _ paint: BoosterPackPaint, sigma: Double = 0,
    strokeWidth: Double = 0, opacity: Double = 1, blend: BoosterPackBlend = .over
  ) {
    guard opacity > 0 else { return }
    let margin = sigma * 3 + strokeWidth + 2
    var p = parameters(for: paint)
    p.a = [1, min(radius, min(rect.width, rect.height) / 2), sigma, strokeWidth]
    p.b = [rect.x, rect.y, rect.width, rect.height]
    let quad = pushQuad(BoosterPackRect(
      x: rect.x - margin, y: rect.y - margin, width: rect.width + margin * 2, height: rect.height + margin * 2))
    draw(.paint, quad, p, opacity: opacity, blend: blend)
  }

  /// A stroked polyline, optionally blurred. The profile across the stroke is exact for a
  /// straight run; round caps use the same profile around the end points.
  func stroke(
    _ source: [CGPoint], width: Double, _ paint: BoosterPackPaint, sigma: Double = 0, round: Bool = true,
    opacity: Double = 1, blend: BoosterPackBlend = .over
  ) {
    guard opacity > 0, width > 0 else { return }
    var points: [CGPoint] = []
    for point in source where points.last.map({ hypot($0.x - point.x, $0.y - point.y) > 0.01 }) ?? true {
      points.append(point)
    }
    guard points.count >= 2 else { return }
    let half = width / 2
    let reach = half + sigma * 3 + 1.5

    var lengths = [0.0]
    for index in 1..<points.count {
      lengths.append(lengths[index - 1] + hypot(points[index].x - points[index - 1].x, points[index].y - points[index - 1].y))
    }
    let total = lengths[points.count - 1]
    func direction(_ from: CGPoint, _ to: CGPoint) -> CGPoint {
      let length = max(hypot(to.x - from.x, to.y - from.y), 0.0001)
      return CGPoint(x: (to.x - from.x) / length, y: (to.y - from.y) / length)
    }

    // One cross-section per point, plus one beyond each end for the cap.
    var sections: [(left: CGPoint, right: CGPoint, along: Double)] = []
    let first = direction(points[0], points[1])
    let last = direction(points[points.count - 2], points[points.count - 1])
    func section(_ point: CGPoint, _ tangent: CGPoint, _ scale: Double, _ along: Double) {
      let nx = -tangent.y * reach * scale
      let ny = tangent.x * reach * scale
      sections.append((CGPoint(x: point.x + nx, y: point.y + ny), CGPoint(x: point.x - nx, y: point.y - ny), along))
    }
    section(CGPoint(x: points[0].x - first.x * reach, y: points[0].y - first.y * reach), first, 1, -reach)
    for index in 0..<points.count {
      let before = index > 0 ? direction(points[index - 1], points[index]) : first
      let after = index < points.count - 1 ? direction(points[index], points[index + 1]) : last
      var tangent = CGPoint(x: before.x + after.x, y: before.y + after.y)
      let length = hypot(tangent.x, tangent.y)
      tangent = length > 0.0001 ? CGPoint(x: tangent.x / length, y: tangent.y / length) : after
      // Widen the section at a bend so both edges stay parallel to their segments.
      let cosine = max(tangent.x * after.x + tangent.y * after.y, 0.35)
      section(points[index], tangent, 1 / cosine, lengths[index])
    }
    let end = points[points.count - 1]
    section(CGPoint(x: end.x + last.x * reach, y: end.y + last.y * reach), last, 1, total + reach)

    let start = buffers.vertexCount
    for index in 0..<(sections.count - 1) {
      let a = sections[index]
      let b = sections[index + 1]
      push(a.left.x, a.left.y, a.left.x, a.left.y, reach, a.along, total - a.along)
      push(a.right.x, a.right.y, a.right.x, a.right.y, -reach, a.along, total - a.along)
      push(b.left.x, b.left.y, b.left.x, b.left.y, reach, b.along, total - b.along)
      push(b.left.x, b.left.y, b.left.x, b.left.y, reach, b.along, total - b.along)
      push(a.right.x, a.right.y, a.right.x, a.right.y, -reach, a.along, total - a.along)
      push(b.right.x, b.right.y, b.right.x, b.right.y, -reach, b.along, total - b.along)
    }
    var p = parameters(for: paint)
    p.a = [2, half, sigma, round ? 1 : 0]
    draw(.paint, start..<buffers.vertexCount, p, opacity: opacity, blend: blend)
  }

  /// A stroked path: its contours flattened, then stroked as polylines.
  func stroke(
    _ path: CGPath, width: Double, _ paint: BoosterPackPaint, sigma: Double = 0, round: Bool = true,
    opacity: Double = 1, blend: BoosterPackBlend = .over
  ) {
    for contour in Self.flatten(path) {
      stroke(contour, width: width, paint, sigma: sigma, round: round, opacity: opacity, blend: blend)
    }
  }

  // MARK: Materials

  /// A textured triangle mesh whose fragment shader is evaluated at the interpolated
  /// texture coordinate.
  func mesh(
    _ fragment: BoosterPackFragment, vertices: [CGPoint], textures: [CGPoint], indices: [Int],
    a: [Double] = [0, 0, 0, 0], b: [Double] = [0, 0, 0, 0], c: [Double] = [0, 0, 0, 0],
    d: [Double] = [0, 0, 0, 0], arrays: [Double]? = nil, texture: MTLTexture? = nil,
    opacity: Double, blend: BoosterPackBlend = .over
  ) {
    guard opacity > 0, !vertices.isEmpty, vertices.count == textures.count else { return }
    let start = buffers.vertexCount
    for index in indices where index < vertices.count {
      push(vertices[index].x, vertices[index].y, textures[index].x, textures[index].y)
    }
    var p = Parameters()
    p.a = a
    p.b = b
    p.c = c
    p.d = d
    p.arrays = arrays
    p.texture = texture
    draw(fragment, start..<buffers.vertexCount, p, opacity: opacity, blend: blend)
  }

  /// A rectangle drawn by one of the material shaders.
  func quad(
    _ fragment: BoosterPackFragment, _ rect: BoosterPackRect,
    a: [Double] = [0, 0, 0, 0], b: [Double] = [0, 0, 0, 0], c: [Double] = [0, 0, 0, 0],
    texture: MTLTexture? = nil, opacity: Double, blend: BoosterPackBlend = .over
  ) {
    guard opacity > 0 else { return }
    var p = Parameters()
    p.a = a
    p.b = b
    p.c = c
    p.texture = texture
    draw(fragment, pushQuad(rect), p, opacity: opacity, blend: blend)
  }

  /// Solid triangles, three points each.
  func triangles(_ points: [CGPoint], _ paint: BoosterPackPaint, opacity: Double = 1) {
    guard opacity > 0 else { return }
    let start = buffers.vertexCount
    for point in points { push(point.x, point.y, point.x, point.y) }
    draw(.paint, start..<buffers.vertexCount, parameters(for: paint), opacity: opacity)
  }
}
