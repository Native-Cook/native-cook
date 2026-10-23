import CoreGraphics
import Foundation

/// What the fold and the flying strip tell their shaders about the surface: per mesh column,
/// how the projected sheet is compressed, bent and turned. Lighting is derived from the
/// current vertices, so holding the gesture holds the light.
enum BoosterPackMaterials {
  private typealias M = BoosterPackModel
  private typealias G = BoosterPackGeometry

  struct Fold {
    /// Maximum projected compression, bent span, swipe direction, sheen strength.
    var fold: [Double] = [0, 0, 1, 1]
    /// Per column: texture height, compression, first-row stretch, upper-edge slope.
    var foldSurface = [Double](repeating: 0, count: BoosterPackGeometry.PREVIEW_MESH_COLUMNS * 4)
    /// Per column: projected distance from the crest at quarter, half and three-quarter
    /// rows, divided by texture height.
    var foldCross = [Double](repeating: 0, count: BoosterPackGeometry.PREVIEW_MESH_COLUMNS * 3)
  }

  static func foldUniforms(
    _ positions: [CGPoint], _ uv: [CGPoint], _ pack: BoosterPackRect, _ swipeDirection: Double,
    _ reducedMotion: Bool
  ) -> Fold {
    let rows = G.PREVIEW_MESH_ROWS
    let columns = G.PREVIEW_MESH_COLUMNS
    var foldSurface: [Double] = []
    var foldCross: [Double] = []
    var compression = 0.0
    var bentArea = 0.0
    var previousU = 0.0
    var previousBend = 0.0
    func position(_ index: Int) -> CGPoint? { index >= 0 && index < positions.count ? positions[index] : nil }
    func texture(_ index: Int) -> CGPoint? { index >= 0 && index < uv.count ? uv[index] : nil }
    for column in 0..<columns {
      let start = column * rows
      let end = start + rows - 1
      let top = position(start)
      let bottom = position(end)
      let uvTop = texture(start)
      let uvBottom = texture(end)
      let textureHeight = uvTop != nil && uvBottom != nil ? max(uvBottom!.y - uvTop!.y, 0.001) : 1
      let local = top != nil && bottom != nil && textureHeight > 0.001
        ? M.clamp(1 - (bottom!.y - top!.y) / textureHeight, 0, 0.95) : 0
      let nextRow = position(start + 1)
      let nextUV = texture(start + 1)
      let uvRowHeight = uvTop != nil && nextUV != nil ? nextUV!.y - uvTop!.y : 0
      let rowStretch = top != nil && nextRow != nil && uvRowHeight > 0.001
        ? (nextRow!.y - top!.y) / uvRowHeight : 1
      let before = position(max(0, column - 1) * rows)
      let after = position(min(columns - 1, column + 1) * rows)
      let edgeSlope = before != nil && after != nil && abs(after!.x - before!.x) > 0.001
        ? (after!.y - before!.y) / (after!.x - before!.x) : 0
      let materialU = uvTop.map { ($0.x - pack.x) / pack.width } ?? previousU
      let bend = sqrt(local)
      if column > 0 { bentArea += (bend + previousBend) * 0.5 * abs(materialU - previousU) }
      previousU = materialU
      previousBend = bend
      compression = max(compression, local)
      foldSurface.append(contentsOf: [textureHeight, local, rowStretch, edgeSlope])
      for row in [2, 4, 6] {
        let point = position(start + row)
        foldCross.append(top != nil && point != nil ? max((point!.y - top!.y) / textureHeight, 0) : Double(row) / 8)
      }
    }
    // Integrate the sampled bend using its actual, nonuniform column spacing.
    // A last-bent-column cutoff would make the shadow jump as the fold widens.
    let extent = compression > 0 ? M.clamp(2 * bentArea / sqrt(compression), 0, 1) : 0
    return Fold(
      fold: [compression, extent, swipeDirection, reducedMotion ? 0.4 : 1],
      foldSurface: foldSurface, foldCross: foldCross)
  }

  // These angles describe the out-of-plane rotation that a projected 2D mesh
  // cannot retain. The light stays fixed; the turning, twisting foil reflects it.
  // In the reference the reflection reaches the right end at f133, then moves
  // toward the middle as that end turns away between f140 and f162.
  private static let MATERIAL_TIMES: [Double] = [0, 66.667, 116.667, 150, 233.333, 333.333, 433.333, 533.333, 633.333, 733.333, 900, 1000]
  private static let MATERIAL_ROLL: [Double] = [-1.32, -1.14, -0.88, -0.66, 0.2, 1.02, 1.75, 2.05, 2.3, 2.48, 2.65, 2.8]
  private static let MATERIAL_TWIST: [Double] = [0.12, 0.5, 1.08, 1.5, 1.85, 2.12, 2.35, 2.58, 2.8, 3.02, 3.25, 3.4]

  struct Foil {
    /// Roll, twist, how much baked light is removed, reflection strength.
    var material: [Double] = [0, 0, 0, 0]
    var pixelScale = 1.0
    /// Per column: signed facing, bending angle, texture height, projected height.
    var surface = [Double](repeating: 0, count: BoosterPackGeometry.MESH_COLUMNS * 4)
  }

  static func foilUniforms(
    _ positions: [CGPoint], _ uv: [CGPoint], _ pack: BoosterPackRect, _ time: Double,
    _ width: Double, _ height: Double, _ reducedMotion: Bool
  ) -> Foil {
    let columns = G.MESH_COLUMNS
    var surface: [Double] = []
    let last = min(columns, positions.count / 2) - 1
    var globalX = pack.width
    var globalY = 0.0
    if last >= 0 {
      let firstTop = positions[0]
      let firstBottom = positions[1]
      let lastTop = positions[last * 2]
      let lastBottom = positions[last * 2 + 1]
      globalX = (lastTop.x + lastBottom.x - firstTop.x - firstBottom.x) * 0.5
      globalY = (lastTop.y + lastBottom.y - firstTop.y - firstBottom.y) * 0.5
    }

    for column in 0..<columns {
      guard column * 2 + 1 < positions.count, column * 2 + 1 < uv.count else {
        surface.append(contentsOf: [1, 0, max(pack.height * 0.18, 1), 1])
        continue
      }
      let top = positions[column * 2]
      let bottom = positions[column * 2 + 1]
      let uvTop = uv[column * 2]
      let uvBottom = uv[column * 2 + 1]
      let before = max(0, column - 1)
      let after = min(last, column + 1)
      let beforeTop = positions[before * 2]
      let beforeBottom = positions[before * 2 + 1]
      let afterTop = positions[after * 2]
      let afterBottom = positions[after * 2 + 1]
      let tangentX = (afterTop.x + afterBottom.x - beforeTop.x - beforeBottom.x) * 0.5
      let tangentY = (afterTop.y + afterBottom.y - beforeTop.y - beforeBottom.y) * 0.5
      let tangentLength = max(hypot(tangentX, tangentY), 0.001)
      let edgeX = bottom.x - top.x
      let edgeY = bottom.y - top.y
      let textureHeight = max(uvBottom.y - uvTop.y, 0.001)
      let textureSpan = max(uv[after * 2].x - uv[before * 2].x, 0.001)
      let stretch = max(tangentLength / textureSpan, 0.001)
      let signedHeight = (tangentX * edgeY - tangentY * edgeX) / tangentLength
      let facing = M.clamp(signedHeight / (textureHeight * stretch), -1, 1)
      let bend = atan2(globalX * tangentY - globalY * tangentX, globalX * tangentX + globalY * tangentY)
      surface.append(contentsOf: [facing, bend, textureHeight, hypot(edgeX, edgeY)])
    }

    let t = reducedMotion ? 0 : M.clamp(time, 0, 1000)
    return Foil(
      material: [
        M.interpolateKeyframes(t, MATERIAL_TIMES, MATERIAL_ROLL),
        M.interpolateKeyframes(t, MATERIAL_TIMES, MATERIAL_TWIST),
        M.clamp((t - 66.667) / 75, 0, 1),
        reducedMotion ? 0.16 : M.clamp((t - 50) / 58.333, 0, 1),
      ],
      pixelScale: min(width / 393, height / 852),
      surface: surface)
  }
}
