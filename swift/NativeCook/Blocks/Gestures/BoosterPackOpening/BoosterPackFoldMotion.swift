import CoreGraphics
import Foundation

/// The fold of the strip while it is still attached, driven by how far the tear has come.
enum BoosterPackFoldMotion {
  private typealias M = BoosterPackModel
  private typealias G = BoosterPackGeometry

  /// Gesture-driven projection of the measured curved fold; the cut stays fixed.
  static func deformPreviewFoil(
    _ base: [CGPoint], _ pack: BoosterPackRect, _ progress: Double, _ direction: Double
  ) -> [CGPoint] {
    if base.isEmpty { return [] }
    let p = M.clamp(progress, 0, 1)
    if p <= 0.06 { return base }
    let profiles = BoosterPackFoldProfile.FOLD_PROFILES
    let alphaTop = BoosterPackFoldProfile.FOLD_ALPHA_TOP
    var upper = 1
    while upper < profiles.count - 1 && profiles[upper].progress < p { upper += 1 }
    let a = profiles[upper - 1]
    let b = profiles[upper]
    let mix = M.clamp((p - a.progress) / (b.progress - a.progress), 0, 1)
    let tipShift = (a.tipShift + (b.tipShift - a.tipShift) * mix) * pack.width
    let shear = pack.width * M.interpolateKeyframes(p,
      [0.06, 0.29, 0.44, 0.52, 0.72, 0.95, 1],
      [0, 0.003, 0.0062, 0.0062, 0.0101, 0.0148, 0.0148])
    var positions: [CGPoint] = []
    positions.reserveCapacity(base.count)
    let rows = G.PREVIEW_MESH_ROWS
    let columns = G.PREVIEW_MESH_COLUMNS
    let exponent = 1.7
    let firstFalloff = pow(1 - 1 / Double(rows - 1), exponent)
    for columnStart in stride(from: 0, to: base.count, by: rows) {
      let column = columnStart / rows
      let columnEnd = min(columnStart + rows, base.count)
      let profileColumn = direction == 1 ? column : columns - 1 - column
      let bottomIndex = columnStart + rows - 1
      let bottom: CGPoint? = bottomIndex < base.count ? base[bottomIndex] : nil
      let height = bottom.map { max($0.y - pack.y, 0) } ?? 0
      guard let bottom, profileColumn >= 0, profileColumn < columns, height >= 0.001 else {
        positions.append(contentsOf: base[columnStart..<columnEnd])
        continue
      }

      // The depth solution depends on the column's cut height, not its row.
      let edge = (a.edge[profileColumn] + (b.edge[profileColumn] - a.edge[profileColumn]) * mix) * pack.width
      let firstHeight = height / Double(rows - 1)
      let alphaFraction = M.clamp(alphaTop[column] * pack.height / firstHeight, 0, 1)
      let minRowHeight = height * 0.0005
      // Solve for the underlying vertex: the artwork's rounded alpha corner
      // lies inside the first row, while measurements describe the visible edge.
      var depth = (edge - alphaFraction * firstHeight) / (1 + alphaFraction * (firstFalloff - 1))
      if firstHeight + depth * (firstFalloff - 1) < minRowHeight {
        depth = edge - alphaFraction * minRowHeight
      }
      depth = M.clamp(depth, 0, height * 0.92)
      let projectedHeight = max(height - depth, 0.001)

      for index in columnStart..<columnEnd {
        let point = base[index]
        let row = index - columnStart
        if row == rows - 1 {
          positions.append(point)
          continue
        }
        let u = M.clamp((point.x - pack.x) / pack.width, 0, 1)
        let fromStart = direction == 1 ? u : 1 - u
        let v = Double(row) / Double(rows - 1)
        let desiredY = point.y + depth * pow(1 - v, exponent)
        let previousY = row == 0 ? pack.y + depth : positions[index - 1].y + minRowHeight
        let y = min(bottom.y, max(previousY, desiredY))
        let projectedV = M.clamp((y - pack.y - depth) / projectedHeight, 0, 1)
        // The curled side stays almost vertical; return to the fixed tear edge
        // close to the seam instead of drawing the side inward along its full height.
        let curveShift = tipShift * pow(M.clamp(1 - fromStart / 0.6, 0, 1), 2) * (1 - pow(projectedV, 3))
        let printShear = -shear * exp(-pow((fromStart - 0.25) / 0.085, 2)) * sin(Double.pi * projectedV)
        positions.append(CGPoint(x: point.x + direction * (curveShift + printShear), y: y))
      }
    }
    return positions
  }
}
