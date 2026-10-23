import CoreGraphics
import Foundation

/// The path calls the geometry uses, over a Core Graphics path.
final class BoosterPackPathBuilder {
  private let path = CGMutablePath()

  func moveTo(_ x: Double, _ y: Double) { path.move(to: CGPoint(x: x, y: y)) }
  func lineTo(_ x: Double, _ y: Double) { path.addLine(to: CGPoint(x: x, y: y)) }
  func quadTo(_ cx: Double, _ cy: Double, _ x: Double, _ y: Double) {
    path.addQuadCurve(to: CGPoint(x: x, y: y), control: CGPoint(x: cx, y: cy))
  }
  func cubicTo(_ ax: Double, _ ay: Double, _ bx: Double, _ by: Double, _ x: Double, _ y: Double) {
    path.addCurve(to: CGPoint(x: x, y: y), control1: CGPoint(x: ax, y: ay), control2: CGPoint(x: bx, y: by))
  }
  func close() { path.closeSubpath() }
  func build() -> CGPath { path }
}

/// The cut path, the masks and the meshes. Every surface is derived from the same sampled tear.
enum BoosterPackGeometry {
  private typealias M = BoosterPackModel

  static let MESH_COLUMNS = 28
  static let MOUTH_MESH_COLUMNS = 56
  static let MOUTH_MESH_INDICES: [Int] = (0..<(MOUTH_MESH_COLUMNS - 1)).flatMap { column -> [Int] in
    let a = column * 2
    return [a, a + 1, a + 2, a + 2, a + 1, a + 3]
  }
  static let MOUTH_WALL_ROWS = 5
  static let MOUTH_WALL_INDICES: [Int] = (0..<((MOUTH_MESH_COLUMNS - 1) * (MOUTH_WALL_ROWS - 1))).flatMap { index -> [Int] in
    let column = index / (MOUTH_WALL_ROWS - 1)
    let row = index % (MOUTH_WALL_ROWS - 1)
    let a = column * MOUTH_WALL_ROWS + row
    let b = a + MOUTH_WALL_ROWS
    return [a, a + 1, b, b, a + 1, b + 1]
  }
  static let PREVIEW_MESH_COLUMNS = 64
  static let PREVIEW_MESH_ROWS = 9

  // Concentrate vertices at both curling ends while retaining a symmetric mesh
  // for either swipe direction. The detached strip has its own coarser topology.
  static func previewColumnU(_ column: Int) -> Double {
    (1 - cos(Double.pi * Double(column) / Double(PREVIEW_MESH_COLUMNS - 1))) * 0.5
  }

  static func makeEmptyPath() -> CGPath { BoosterPackPathBuilder().build() }

  static func makeSmoothPath(_ points: [CGPoint]) -> CGPath {
    let builder = BoosterPackPathBuilder()
    if points.isEmpty { return builder.build() }

    builder.moveTo(points[0].x, points[0].y)
    if points.count == 1 { return builder.build() }

    if points.count > 2 {
      for index in 1..<(points.count - 1) {
        let current = points[index]
        let next = points[index + 1]
        builder.quadTo(current.x, current.y, (current.x + next.x) / 2, (current.y + next.y) / 2)
      }
    }

    let beforeLast = points[points.count - 2]
    let last = points[points.count - 1]
    builder.quadTo(beforeLast.x, beforeLast.y, last.x, last.y)
    return builder.build()
  }

  static func trimTraceEnd(_ points: [CGPoint], _ trimDistance: Double) -> [CGPoint] {
    if points.count < 2 || trimDistance <= 0 { return points }

    var trimmed = points
    var remaining = trimDistance

    while trimmed.count > 1 {
      let endIndex = trimmed.count - 1
      let end = trimmed[endIndex]
      let previous = trimmed[endIndex - 1]
      let dx = end.x - previous.x
      let dy = end.y - previous.y
      let length = sqrt(dx * dx + dy * dy)

      if length > remaining && length > 0.0001 {
        let retained = (length - remaining) / length
        trimmed[endIndex] = CGPoint(x: previous.x + dx * retained, y: previous.y + dy * retained)
        return trimmed
      }

      remaining -= length
      trimmed.removeLast()
    }

    // A just-started gesture can be shorter than the cap radius. Keep a tiny
    // segment so a stable clipped onset is drawn instead of a dot.
    let first = points[0]
    let last = points[points.count - 1]
    let dx = last.x - first.x
    let dy = last.y - first.y
    let length = sqrt(dx * dx + dy * dy)
    if length <= 0.0001 { return [first] }
    let retained = min(0.5 / length, 1)
    return [first, CGPoint(x: first.x + dx * retained, y: first.y + dy * retained)]
  }

  static func completeCutPoints(_ inputPoints: [CGPoint], _ pack: BoosterPackRect, _ direction: Double) -> [CGPoint] {
    if inputPoints.isEmpty { return [] }

    let ordered = direction == 1 ? inputPoints : Array(inputPoints.reversed())
    let first = ordered[0]
    let last = ordered[ordered.count - 1]
    let left = pack.x
    let right = pack.x + pack.width

    var completed: [CGPoint] = []

    // The gesture arms before the finger reaches the opposite edge. Preserve the
    // measured exit tangent through that final gap, then ease it flat at the pack
    // boundary. A single horizontal append made every rim layer kink apart here.
    if direction == -1 && first.x > left {
      let next = ordered[min(1, ordered.count - 1)]
      let gap = first.x - left
      let slope = M.clamp((next.y - first.y) / max(next.x - first.x, 0.0001), -0.35, 0.35)
      let endY = first.y - slope * gap * 0.35
      for t in [1.0, 2.0 / 3.0, 1.0 / 3.0] {
        let y = (2 * t * t * t - 3 * t * t + 1) * first.y
          + (t * t * t - 2 * t * t + t) * -gap * slope
          + (-2 * t * t * t + 3 * t * t) * endY
          + (t * t * t - t * t) * -gap * slope * 0.75
        completed.append(CGPoint(x: first.x - gap * t, y: y))
      }
    } else if first.x > left {
      completed.append(CGPoint(x: left, y: first.y))
    }

    completed.append(contentsOf: ordered)

    if direction == 1 && last.x < right {
      let previous = ordered[max(0, ordered.count - 2)]
      let gap = right - last.x
      let slope = M.clamp((last.y - previous.y) / max(last.x - previous.x, 0.0001), -0.35, 0.35)
      let endY = last.y + slope * gap * 0.35
      for t in [1.0 / 3.0, 2.0 / 3.0, 1.0] {
        let y = (2 * t * t * t - 3 * t * t + 1) * last.y
          + (t * t * t - 2 * t * t + t) * gap * slope
          + (-2 * t * t * t + 3 * t * t) * endY
          + (t * t * t - t * t) * gap * slope * 0.75
        completed.append(CGPoint(x: last.x + gap * t, y: y))
      }
    } else if last.x < right {
      completed.append(CGPoint(x: right, y: last.y))
    }
    return completed
  }

  static func completePreviewCutPoints(
    _ inputPoints: [CGPoint], _ pack: BoosterPackRect, _ direction: Double, _ seamY: Double
  ) -> [CGPoint] {
    if inputPoints.isEmpty { return [] }

    let ordered = direction == 1 ? inputPoints : Array(inputPoints.reversed())
    let left = pack.x
    let right = pack.x + pack.width
    let transitionWidth = pack.width * 0.045

    if direction == 1 {
      let first = ordered[0]
      let head = ordered[ordered.count - 1]
      var completed: [CGPoint] = []
      if first.x > left { completed.append(CGPoint(x: left, y: first.y)) }
      completed.append(contentsOf: ordered)
      if head.x < right {
        let transitionX = min(right, head.x + transitionWidth)
        if transitionX > head.x + 0.5 { completed.append(CGPoint(x: transitionX, y: seamY)) }
        if transitionX < right { completed.append(CGPoint(x: right, y: seamY)) }
      }
      return completed
    }

    let head = ordered[0]
    var completed = [CGPoint(x: left, y: seamY)]
    let transitionX = max(left, head.x - transitionWidth)
    if transitionX > left + 0.5 { completed.append(CGPoint(x: transitionX, y: seamY)) }
    completed.append(contentsOf: ordered)
    let last = ordered[ordered.count - 1]
    if last.x < right { completed.append(CGPoint(x: right, y: last.y)) }
    return completed
  }

  static func makeBottomMask(_ points: [CGPoint], _ pack: BoosterPackRect) -> CGPath {
    let builder = BoosterPackPathBuilder()
    if points.count < 2 { return builder.build() }

    builder.moveTo(points[0].x, points[0].y)
    if points.count > 2 {
      for index in 1..<(points.count - 1) {
        let current = points[index]
        let next = points[index + 1]
        builder.quadTo(current.x, current.y, (current.x + next.x) / 2, (current.y + next.y) / 2)
      }
    }
    let beforeLast = points[points.count - 2]
    let last = points[points.count - 1]
    builder.quadTo(beforeLast.x, beforeLast.y, last.x, last.y)
    builder.lineTo(pack.x + pack.width, pack.y + pack.height)
    builder.lineTo(pack.x, pack.y + pack.height)
    builder.close()
    return builder.build()
  }

  private static func yAtX(_ points: [CGPoint], _ x: Double) -> Double {
    if points.isEmpty { return 0 }
    if x <= points[0].x { return points[0].y }
    let last = points.count - 1
    if x >= points[last].x { return points[last].y }

    for index in 1..<points.count {
      let right = points[index]
      if x <= right.x {
        let left = points[index - 1]
        let width = max(0.0001, right.x - left.x)
        let progress = M.clamp((x - left.x) / width, 0, 1)
        return left.y + (right.y - left.y) * progress
      }
    }
    return points[last].y
  }

  static func makeFoilMesh(_ points: [CGPoint], _ pack: BoosterPackRect) -> BoosterPackMesh {
    var vertices: [CGPoint] = []
    var textures: [CGPoint] = []

    for column in 0..<MESH_COLUMNS {
      let u = Double(column) / Double(MESH_COLUMNS - 1)
      let x = pack.x + u * pack.width
      let bottomY = yAtX(points, x)
      let top = CGPoint(x: x, y: pack.y)
      let bottom = CGPoint(x: x, y: bottomY)
      vertices.append(contentsOf: [top, bottom])
      textures.append(contentsOf: [top, bottom])
    }
    return BoosterPackMesh(vertices: vertices, textures: textures)
  }

  static func makePreviewFoilMesh(_ points: [CGPoint], _ pack: BoosterPackRect) -> BoosterPackMesh {
    var vertices: [CGPoint] = []
    var textures: [CGPoint] = []

    for column in 0..<PREVIEW_MESH_COLUMNS {
      let u = previewColumnU(column)
      let x = pack.x + u * pack.width
      let bottomY = yAtX(points, x)

      for row in 0..<PREVIEW_MESH_ROWS {
        let v = Double(row) / Double(PREVIEW_MESH_ROWS - 1)
        let y = pack.y + (bottomY - pack.y) * v
        let point = CGPoint(x: x, y: y)
        vertices.append(point)
        textures.append(point)
      }
    }
    return BoosterPackMesh(vertices: vertices, textures: textures)
  }

  static func makeCollapsedFoilMesh(_ pack: BoosterPackRect) -> BoosterPackMesh {
    let points = [CGPoint](repeating: CGPoint(x: pack.x, y: pack.y), count: MESH_COLUMNS * 2)
    return BoosterPackMesh(vertices: points, textures: points)
  }

  static func makeCollapsedPreviewFoilMesh(_ pack: BoosterPackRect) -> BoosterPackMesh {
    let points = [CGPoint](
      repeating: CGPoint(x: pack.x, y: pack.y), count: PREVIEW_MESH_COLUMNS * PREVIEW_MESH_ROWS)
    return BoosterPackMesh(vertices: points, textures: points)
  }

  private static func cavityDepthAt(
    _ point: CGPoint, _ pack: BoosterPackRect, _ depth: Double, _ direction: Double, _ exponent: Double
  ) -> Double {
    let u = M.clamp((point.x - pack.x) / max(pack.width, 0.0001), 0, 1)
    let directionalU = direction == 1 ? u : 1 - u
    // The physical tear has a shallow entry cuff and opens continuously toward
    // the swipe exit. This profile fits the measured frame-150/162/170 section
    // depths and mirrors automatically for a right-to-left swipe.
    let entryBump = 0.09 * pow(1 - directionalU, 4)
    return depth * min(1, 0.18 + 0.82 * pow(directionalU, exponent) + entryBump)
  }

  private static func frontLipDepthAt(
    _ point: CGPoint, _ pack: BoosterPackRect, _ depth: Double, _ direction: Double
  ) -> Double {
    let u = M.clamp((point.x - pack.x) / max(pack.width, 0.0001), 0, 1)
    let directionalU = direction == 1 ? u : 1 - u
    // The laminated front lip stays narrow at the tear entry and fans out into
    // the exit cuff. Keeping this on the same directional coordinate makes the
    // full cross-section mirror cleanly for right-to-left gestures.
    return depth * (0.56 + 0.44 * directionalU)
  }

  private static func liftFrontLipPoints(
    _ points: [CGPoint], _ pack: BoosterPackRect, _ lift: Double, _ direction: Double, _ middleLift: Double = 0
  ) -> [CGPoint] {
    if lift <= 0 { return points }

    return points.map { point in
      let u = M.clamp((point.x - pack.x) / max(pack.width, 0.0001), 0, 1)
      let directionalU = direction == 1 ? u : 1 - u
      // The reference cuff rises as one laminate near the swipe exit. A
      // smoothstep keeps the measured center section untouched and gives every
      // front layer the same tangent instead of stacking disconnected patches.
      let t = M.clamp((directionalU - 0.89) / 0.11, 0, 1)
      let eased = t * t * (3 - 2 * t)
      // The last few percent curl more sharply than the long approach. Keeping
      // this as a high-order terminal term preserves the measured center line,
      // while the very end rises as one bonded laminate instead of closing as a
      // flat capsule.
      let exitCurl = 0.78 * pow(t, 6)
      var middleProfile = 0.0
      if directionalU >= 0.25 && directionalU < 0.43 {
        let middleT = M.clamp((directionalU - 0.25) / 0.18, 0, 1)
        middleProfile = middleT * middleT * (3 - 2 * middleT)
      } else if directionalU >= 0.43 && directionalU < 0.58 {
        let middleT = M.clamp((directionalU - 0.43) / 0.15, 0, 1)
        middleProfile = 1 - middleT * 0.25
      } else if directionalU >= 0.58 && directionalU < 0.66 {
        let middleT = M.clamp((directionalU - 0.58) / 0.08, 0, 1)
        let middleEased = middleT * middleT * (3 - 2 * middleT)
        middleProfile = 0.75 * (1 - middleEased)
      }
      return CGPoint(x: point.x, y: point.y - lift * (eased + exitCurl) - middleLift * middleProfile)
    }
  }

  private static func normalAt(_ points: [CGPoint], _ index: Int) -> CGPoint {
    let point = points[index]
    var previousIndex = max(0, index - 1)
    var nextIndex = min(points.count - 1, index + 1)
    while previousIndex > 0
      && abs(points[previousIndex].x - point.x) + abs(points[previousIndex].y - point.y) < 0.0001 {
      previousIndex -= 1
    }
    while nextIndex < points.count - 1
      && abs(points[nextIndex].x - point.x) + abs(points[nextIndex].y - point.y) < 0.0001 {
      nextIndex += 1
    }
    let previous = points[previousIndex]
    let next = points[nextIndex]
    let dx = next.x - previous.x
    let dy = next.y - previous.y
    let length = sqrt(dx * dx + dy * dy)
    if length < 0.0001 { return CGPoint(x: 0, y: -1) }
    return CGPoint(x: dy / length, y: -dx / length)
  }

  private static func monotoneSlopeAt(_ previous: CGPoint, _ point: CGPoint, _ next: CGPoint) -> Double {
    let previousDelta = point.x - previous.x
    let nextDelta = next.x - point.x
    let previousWidth = abs(previousDelta)
    let nextWidth = abs(nextDelta)
    if previousWidth < 0.0001 && nextWidth < 0.0001 { return 0 }
    if previousWidth < 0.0001 {
      return nextWidth < 0.0001 ? 0 : (next.y - point.y) / nextDelta
    }
    if nextWidth < 0.0001 {
      return (point.y - previous.y) / previousDelta
    }

    let previousSlope = (point.y - previous.y) / previousDelta
    let nextSlope = (next.y - point.y) / nextDelta
    if previousSlope * nextSlope <= 0 { return 0 }

    let previousWeight = 2 * nextWidth + previousWidth
    let nextWeight = nextWidth + 2 * previousWidth
    let denominator = previousWeight / previousSlope + nextWeight / nextSlope
    if abs(denominator) < 0.0001 { return 0 }
    return (previousWeight + nextWeight) / denominator
  }

  private static func appendCubicSpline(_ builder: BoosterPackPathBuilder, _ points: [CGPoint]) {
    if points.count < 2 { return }
    for index in 0..<(points.count - 1) {
      let p0 = points[max(0, index - 1)]
      let p1 = points[index]
      let p2 = points[index + 1]
      let p3 = points[min(points.count - 1, index + 2)]
      let segmentWidth = p2.x - p1.x
      if abs(segmentWidth) < 0.0001 {
        builder.lineTo(p2.x, p2.y)
        continue
      }
      let startSlope = monotoneSlopeAt(p0, p1, p2)
      let endSlope = monotoneSlopeAt(p1, p2, p3)
      builder.cubicTo(
        p1.x + segmentWidth / 3, p1.y + (startSlope * segmentWidth) / 3,
        p2.x - segmentWidth / 3, p2.y - (endSlope * segmentWidth) / 3,
        p2.x, p2.y)
    }
  }

  private static func makeCubicSplinePath(_ points: [CGPoint]) -> CGPath {
    let builder = BoosterPackPathBuilder()
    if points.isEmpty { return builder.build() }
    builder.moveTo(points[0].x, points[0].y)
    appendCubicSpline(builder, points)
    return builder.build()
  }

  static func relaxMouthPoints(
    _ points: [CGPoint], _ pack: BoosterPackRect, _ entryOffset: Double, _ exitOffset: Double,
    _ exponent: Double, _ centerLift: Double, _ direction: Double
  ) -> [CGPoint] {
    points.map { point in
      let u = M.clamp((point.x - pack.x) / max(pack.width, 0.0001), 0, 1)
      let directionalU = direction == 1 ? u : 1 - u
      let spatialOffset = entryOffset
        + (exitOffset - entryOffset) * pow(directionalU, exponent)
        + centerLift * pow(max(0, sin(Double.pi * directionalU)), 1.4)
      return CGPoint(x: point.x, y: point.y + spatialOffset)
    }
  }

  static func makeCavityFrontEdge(_ points: [CGPoint]) -> CGPath {
    makeCubicSplinePath(points)
  }

  private static func mouthYAtX(_ points: [CGPoint], _ x: Double) -> Double {
    if points.count < 2 || x <= points[0].x || x >= points[points.count - 1].x {
      return yAtX(points, x)
    }
    for i in 1..<points.count {
      if x > points[i].x { continue }
      let left = points[i - 1]
      let right = points[i]
      let dx = max(right.x - left.x, 0.0001)
      let t = M.clamp((x - left.x) / dx, 0, 1)
      let a = monotoneSlopeAt(points[max(0, i - 2)], left, right)
      let b = monotoneSlopeAt(left, right, points[min(points.count - 1, i + 1)])
      return (2 * t * t * t - 3 * t * t + 1) * left.y + (t * t * t - 2 * t * t + t) * dx * a
        + (-2 * t * t * t + 3 * t * t) * right.y + (t * t * t - t * t) * dx * b
    }
    return points[points.count - 1].y
  }

  // Resample before curling so even a sparse gesture has a smooth terminal
  // bend. This same front contour drives the body mask, laminate and wall.
  static func curlMouthPoints(
    _ points: [CGPoint], _ pack: BoosterPackRect, _ lift: Double, _ direction: Double
  ) -> [CGPoint] {
    if points.count < 2 { return points }
    return (0..<MOUTH_MESH_COLUMNS).map { index in
      let u = Double(index) / Double(MOUTH_MESH_COLUMNS - 1)
      let x = pack.x + u * pack.width
      // A broad turn ends in a rising crease, as the foil does in frames
      // 162–178. A smoothstep here flattens at the tip into a rounded lip.
      let t = M.clamp(((direction == 1 ? u : 1 - u) - 0.8) / 0.2, 0, 1)
      return CGPoint(x: x, y: mouthYAtX(points, x) - lift * pow(t, 1.7))
    }
  }

  private static func mouthColumnU(_ index: Int) -> Double {
    if index < 12 { return 0.06 * (1 - cos(Double(index) / 11 * Double.pi / 2)) }
    if index >= MOUTH_MESH_COLUMNS - 12 {
      return 1 - 0.06 * (1 - cos(Double(MOUTH_MESH_COLUMNS - 1 - index) / 11 * Double.pi / 2))
    }
    return 0.06 + Double(index - 11) / Double(MOUTH_MESH_COLUMNS - 23) * 0.88
  }

  // Material coordinates remain attached to the curved laminate as its opening
  // and the user's tear path change. Both surfaces use the foil strip topology.
  static func makeMouthMaterialMesh(
    _ points: [CGPoint], _ pack: BoosterPackRect, _ depth: Double, _ direction: Double,
    _ exponent: Double, _ frontLip: Bool = false, _ exitLift: Double = 0, _ backPoints: [CGPoint]? = nil
  ) -> BoosterPackMesh {
    if points.count < 2 {
      return BoosterPackMesh(
        vertices: [CGPoint](repeating: CGPoint(x: pack.x, y: pack.y), count: MOUTH_MESH_COLUMNS * 2),
        textures: [CGPoint](repeating: .zero, count: MOUTH_MESH_COLUMNS * 2))
    }
    let rear = backPoints ?? points
    let samples: [CGPoint] = (0..<MOUTH_MESH_COLUMNS).map { index in
      let u = frontLip ? mouthColumnU(index) : Double(index) / Double(MOUTH_MESH_COLUMNS - 1)
      let x = pack.x + u * pack.width
      return CGPoint(x: x, y: mouthYAtX(points, x))
    }
    let backSamples: [CGPoint] = frontLip ? [] : samples.map { point in
      CGPoint(x: point.x, y: mouthYAtX(rear, point.x))
    }
    let front: [CGPoint] = frontLip
      ? liftFrontLipPoints(samples, pack, exitLift, direction).map { point in
          CGPoint(x: point.x, y: point.y - depth * 0.15)
        }
      : samples
    let outer: [CGPoint] = frontLip
      ? front.enumerated().map { index, point in
          let normal = normalAt(samples, index)
          let d = frontLipDepthAt(point, pack, depth, direction)
          return CGPoint(x: point.x - normal.x * d, y: point.y - normal.y * d)
        }
      : backSamples.enumerated().map { index, point in
          let normal = normalAt(backSamples, index)
          let d = cavityDepthAt(point, pack, depth, direction, exponent)
          return CGPoint(x: point.x + normal.x * d, y: point.y + normal.y * d)
        }
    var vertices: [CGPoint] = []
    var textures: [CGPoint] = []
    for index in 0..<MOUTH_MESH_COLUMNS {
      let u = (samples[index].x - pack.x) / pack.width
      vertices.append(frontLip ? front[index] : outer[index])
      vertices.append(frontLip ? outer[index] : front[index])
      textures.append(CGPoint(x: direction == 1 ? u : 1 - u, y: 0))
      textures.append(CGPoint(x: direction == 1 ? u : 1 - u, y: 1))
    }
    return BoosterPackMesh(vertices: vertices, textures: textures)
  }

  static func makeMouthWallMesh(
    _ frontPoints: [CGPoint], _ backPoints: [CGPoint], _ pack: BoosterPackRect,
    _ depth: Double, _ direction: Double, _ exponent: Double
  ) -> BoosterPackMesh {
    var vertices: [CGPoint] = []
    var textures: [CGPoint] = []
    let valid = frontPoints.count > 1 && backPoints.count > 1
    for column in 0..<MOUTH_MESH_COLUMNS {
      let u = mouthColumnU(column)
      let directionalU = direction == 1 ? u : 1 - u
      let x = pack.x + u * pack.width
      let entry = pow(M.clamp(1 - directionalU / 0.07, 0, 1), 2)
      let exit = pow(M.clamp(1 - (1 - directionalU) / 0.07, 0, 1), 2)
      let inset = direction * (min(depth * 0.22, pack.width * 0.025) * entry
        - min(depth * 0.14, pack.width * 0.014) * exit)
      let rearX = x + inset
      let frontY = mouthYAtX(frontPoints, x)
      let rearPoint = CGPoint(x: x, y: mouthYAtX(backPoints, rearX))
      let rearY = rearPoint.y - cavityDepthAt(rearPoint, pack, depth, direction, exponent)
      for row in 0..<MOUTH_WALL_ROWS {
        let v = Double(row) / Double(MOUTH_WALL_ROWS - 1)
        // The rear sheet turns outward toward the front silhouette. A cubic
        // side return replaces the former vertical, cut-off end of the strip.
        let turn = 1 - pow(1 - v, 3)
        vertices.append(valid
          ? CGPoint(x: rearX + (x - rearX) * turn, y: rearY + (frontY - rearY) * v)
          : CGPoint(x: pack.x, y: pack.y))
        textures.append(CGPoint(x: directionalU, y: v))
      }
    }
    return BoosterPackMesh(vertices: vertices, textures: textures)
  }

  // A triangle silhouette is not antialiased like a path. Draw a small
  // overscan behind a shared cubic clip so metallic edges stay clean at 1x too.
  static func expandMouthMesh(_ vertices: [CGPoint], _ rows: Int, _ amount: Double) -> [CGPoint] {
    let columns = vertices.count / rows
    return vertices.enumerated().map { index, point in
      let column = index / rows
      let row = index % rows
      return CGPoint(
        x: point.x + (column == 0 ? -amount : column == columns - 1 ? amount : 0),
        y: point.y + (row == 0 ? -amount : row == rows - 1 ? amount : 0))
    }
  }

  static func makeMouthMeshOutline(_ vertices: [CGPoint], _ rows: Int, _ tipRadius: Double = 0) -> CGPath {
    let builder = BoosterPackPathBuilder()
    let columns = vertices.count / rows
    if columns < 2 { return builder.build() }
    let top = (0..<columns).map { vertices[$0 * rows] }
    let bottom = (0..<columns).map { vertices[$0 * rows + rows - 1] }
    builder.moveTo(top[0].x, top[0].y)
    appendCubicSpline(builder, top)
    let rightTop = top[columns - 1]
    let rightBottom = bottom[columns - 1]
    if tipRadius > 0 {
      builder.cubicTo(rightTop.x + tipRadius, rightTop.y,
        rightBottom.x + tipRadius, rightBottom.y, rightBottom.x, rightBottom.y)
    } else {
      builder.cubicTo(rightBottom.x, rightTop.y + (rightBottom.y - rightTop.y) / 3,
        rightBottom.x, rightTop.y + (rightBottom.y - rightTop.y) * 2 / 3,
        rightBottom.x, rightBottom.y)
    }
    appendCubicSpline(builder, bottom.reversed())
    let leftTop = top[0]
    let leftBottom = bottom[0]
    if tipRadius > 0 {
      builder.cubicTo(leftBottom.x - tipRadius, leftBottom.y,
        leftTop.x - tipRadius, leftTop.y, leftTop.x, leftTop.y)
    } else {
      builder.cubicTo(leftBottom.x, leftTop.y + (leftBottom.y - leftTop.y) * 2 / 3,
        leftBottom.x, leftTop.y + (leftBottom.y - leftTop.y) / 3, leftTop.x, leftTop.y)
    }
    builder.close()
    return builder.build()
  }

  private static let BODY_ROWS: [Double] = [0, 0.025, 0.06, 0.12, 0.22, 0.38, 0.6, 0.8, 1]
  static let BODY_MESH_INDICES: [Int] = (0..<((MESH_COLUMNS - 1) * (BODY_ROWS.count - 1))).flatMap { index -> [Int] in
    let column = index / (BODY_ROWS.count - 1)
    let row = index % (BODY_ROWS.count - 1)
    let a = column * BODY_ROWS.count + row
    let b = a + BODY_ROWS.count
    return [a, a + 1, b, b, a + 1, b + 1]
  }

  static func makeBodyMaterialMesh(
    _ points: [CGPoint], _ pack: BoosterPackRect, _ sourceTop: Double, _ warp: Double, _ textureOffset: Double
  ) -> BoosterPackMesh {
    var vertices: [CGPoint] = []
    var textures: [CGPoint] = []
    for col in 0..<MESH_COLUMNS {
      let x = pack.x + Double(col) / Double(MESH_COLUMNS - 1) * pack.width
      let top = points.count > 1 ? yAtX(points, x) : sourceTop
      for v in BODY_ROWS {
        let y = top + (pack.y + pack.height - top) * v
        // Only substrate below the manufactured seal belongs to the opened
        // front panel. Warp its UVs onto the user's curved cut; never expose the
        // baked straight seal as a second, intersecting mouth edge.
        let delta = sourceTop - top - textureOffset
        let bodyHeight = max(pack.y + pack.height - top, 0.0001)
        let reach = min(bodyHeight, max(pack.height * 0.11, max(0, delta) * 3.1))
        // Confine seal removal to the first few rows. Extending it over the
        // whole front panel bends the artwork as the mouth relaxes.
        // The reach/derivative bound also keeps texture rows in their order.
        let power: Double = delta > 0 && reach < delta * 3.1 ? 1 : 3
        let local = M.clamp(1 - (y - top) / reach, 0, 1)
        let offset = delta * pow(local, power) * warp
        vertices.append(CGPoint(x: x, y: y))
        textures.append(CGPoint(x: x, y: y + textureOffset + offset))
      }
    }
    return BoosterPackMesh(vertices: vertices, textures: textures)
  }

  static func makeCavityBottomMask(_ points: [CGPoint], _ pack: BoosterPackRect) -> CGPath {
    let builder = BoosterPackPathBuilder()
    if points.count < 2 { return builder.build() }
    builder.moveTo(points[0].x, points[0].y)
    appendCubicSpline(builder, points)
    builder.lineTo(pack.x + pack.width, pack.y + pack.height)
    builder.lineTo(pack.x, pack.y + pack.height)
    builder.close()
    return builder.build()
  }

  static func makeEntryBodyWedge(_ points: [CGPoint], _ pack: BoosterPackRect, _ direction: Double) -> CGPath {
    let builder = BoosterPackPathBuilder()
    if points.count < 2 { return builder.build() }

    let entry = direction == 1 ? points[0] : points[points.count - 1]
    let inward = direction
    builder.moveTo(entry.x + inward * pack.width * 0.003, entry.y + pack.width * 0.008)
    builder.cubicTo(
      entry.x + inward * pack.width * 0.007, entry.y + pack.width * 0.035,
      entry.x + inward * pack.width * 0.014, entry.y + pack.width * 0.085,
      entry.x + inward * pack.width * 0.022, entry.y + pack.width * 0.13)
    builder.lineTo(entry.x + inward * pack.width * 0.075, entry.y + pack.width * 0.13)
    builder.lineTo(entry.x + inward * pack.width * 0.075, entry.y + pack.width * 0.008)
    builder.close()
    return builder.build()
  }

  static let FOIL_MESH_INDICES: [Int] = (0..<((MESH_COLUMNS - 1) * 6)).map { index in
    let cell = index / 6
    let offset = index % 6
    let topLeft = cell * 2
    let bottomLeft = topLeft + 1
    let topRight = topLeft + 2
    let bottomRight = topLeft + 3
    return [topLeft, bottomLeft, topRight, topRight, bottomLeft, bottomRight][offset]
  }

  static let PREVIEW_FOIL_MESH_INDICES: [Int] = (0..<((PREVIEW_MESH_COLUMNS - 1) * (PREVIEW_MESH_ROWS - 1) * 6)).map { index in
    let triangleOffset = index % 6
    let cell = index / 6
    let column = cell / (PREVIEW_MESH_ROWS - 1)
    let row = cell % (PREVIEW_MESH_ROWS - 1)
    let topLeft = column * PREVIEW_MESH_ROWS + row
    let bottomLeft = topLeft + 1
    let topRight = topLeft + PREVIEW_MESH_ROWS
    let bottomRight = topRight + 1
    return [topLeft, bottomLeft, topRight, topRight, bottomLeft, bottomRight][triangleOffset]
  }

  /// The finger's path in the reference video at `progress` of its travel, as a cubic through
  /// the measured start, two shaping points and the end.
  static func referenceTracePoint(_ progress: Double, _ width: Double, _ height: Double) -> CGPoint {
    let p0 = CGPoint(x: 0.125 * width, y: 0.568 * height)
    let p1 = CGPoint(x: 0.31 * width, y: 0.576 * height)
    let p2 = CGPoint(x: 0.58 * width, y: 0.565 * height)
    let p3 = CGPoint(x: 0.765 * width, y: 0.544 * height)
    let t = M.clamp(progress, 0, 1)
    let inverse = 1 - t
    return CGPoint(
      x: inverse * inverse * inverse * p0.x + 3 * inverse * inverse * t * p1.x
        + 3 * inverse * t * t * p2.x + t * t * t * p3.x,
      y: inverse * inverse * inverse * p0.y + 3 * inverse * inverse * t * p1.y
        + 3 * inverse * t * t * p2.y + t * t * t * p3.y)
  }
}
