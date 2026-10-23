import UIKit
import simd

struct CrystalTrophyRevealLayout {
  var width: Double
  var height: Double
  var k: Double
  var originX: Double
  var originY: Double
  var unit: Double
  var titleY: Double
  var titleSize: Double
  var titleLine: Double
  var dateY: Double
  var dateSize: Double
  var bodyTop: Double
  var share: CGRect
  var close: CGRect
  let titleTilt = -8.0 * Double.pi / 180

  init(size: CGSize, insets: UIEdgeInsets) {
    width = size.width
    height = size.height
    k = min(1, max(0.72, (height - insets.top - insets.bottom) / 778))
    let shareWidth = min(354, width - 48)
    let shareY = height - insets.bottom - 16 - 56
    bodyTop = shareY - (40 * k).rounded() - 44
    originX = width / 2
    originY = bodyTop - (76 * k).rounded()
    unit = 108 * k
    titleY = insets.top + (104 * k).rounded()
    titleSize = (68 * k).rounded()
    titleLine = (62 * k).rounded()
    dateY = insets.top + (188 * k).rounded()
    dateSize = (20 * k).rounded()
    share = CGRect(x: (width - shareWidth) / 2, y: shareY, width: shareWidth, height: 56)
    close = CGRect(x: width - 56, y: insets.top + 4, width: 40, height: 40)
    let apex = project(.init(0, 2.9, 0), yaw: 0).y
    let dateBottom = dateY + dateSize * 0.5 + sin(-titleTilt) * dateSize * 4.8
    let lift = max(0, dateBottom + 16 - apex)
    titleY -= lift
    dateY -= lift
  }

  var hitBox: CGRect {
    CGRect(
      x: originX - unit * 1.3 - 24, y: originY - unit * 3.05 - 24, width: unit * 2.6 + 48,
      height: unit * 3.65 + 48)
  }

  func project(_ p: SIMD3<Double>, yaw: Double) -> SIMD2<Double> {
    let x = p.x * cos(yaw) + p.z * sin(yaw)
    let z = -p.x * sin(yaw) + p.z * cos(yaw)
    let y = p.y * cos(0.15) - z * sin(0.15)
    let depth = p.y * sin(0.15) + z * cos(0.15)
    let s = 11 / (11 - depth)
    return .init(originX + unit * x * s, originY - unit * y * s)
  }

  func stagePoint(_ p: CGPoint, motion: CrystalTrophyRevealMotion) -> CGPoint {
    let cy = originY - 1.45 * unit
    return CGPoint(
      x: originX + (p.x - originX) / motion.cameraScale,
      y: cy + (p.y - cy - motion.cameraY) / motion.cameraScale)
  }

  func rim(yaw: Double, rise: Double) -> [SIMD4<Float>] {
    let drop = (1 - rise) * 2.96
    var points: [SIMD2<Double>] = []
    for v in CrystalTrophyRevealCut.vertices where v.y >= drop {
      points.append(project(v - SIMD3(0, drop, 0), yaw: yaw))
    }
    for edge in CrystalTrophyRevealCut.edges {
      let a = CrystalTrophyRevealCut.vertices[edge.x]
      let b = CrystalTrophyRevealCut.vertices[edge.y]
      if (a.y - drop) * (b.y - drop) < 0 {
        let t = (drop - a.y) / (b.y - a.y)
        points.append(project(.init(a.x + (b.x - a.x) * t, 0, a.z + (b.z - a.z) * t), yaw: yaw))
      }
    }
    points.sort { $0.x == $1.x ? $0.y < $1.y : $0.x < $1.x }
    var hull: [SIMD2<Double>] = []
    for side in 0..<2 {
      let start = hull.count
      for i in points.indices {
        let p = points[side == 0 ? i : points.count - 1 - i]
        while hull.count >= start + 2 {
          let a = hull[hull.count - 2]
          let b = hull[hull.count - 1]
          if (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x) > 1e-7 { break }
          hull.removeLast()
        }
        hull.append(p)
      }
      if !hull.isEmpty { hull.removeLast() }
    }
    return (0..<32).map { i in
      guard i < hull.count else { return SIMD4<Float>(repeating: -10000) }
      let a = hull[i]
      let b = hull[(i + 1) % hull.count]
      return .init(Float(a.x), Float(a.y), Float(b.x), Float(b.y))
    }
  }

  func sparkles(yaw: Double, rise: Double, flash: Double) -> [SIMD4<Float>] {
    let sites: [(Double, Double, Double, Double)] = [
      (0.05, 2.90, 0.17, 0), (-0.74, 2.52, 0.15, -0.045), (0.86, 1.36, 0.04, 0.035),
      (-0.86, 1.34, 0.04, 0.075), (0.329, 0.967, 0.209, -0.025), (-0.329, 0.967, 0.209, 0.015),
      (0, 1.40, 0.285, -0.075), (0, 0.60, 0.1442, 0.055),
    ]
    let drop = (1 - rise) * 2.96
    return sites.enumerated().map { i, site in
      let (x, y, z, phase) = site
      let alignment = cos(yaw) * cos(phase) + sin(yaw) * sin(phase)
      var gain = pow(max(0, alignment), i == 0 ? 95 : 2600) * (i == 0 ? 1.1 : 3)
      if i == 0 { gain += flash * 1.8 }
      let p = project(.init(x, y - drop, z), yaw: yaw)
      let size = (i == 0 ? 17 * (1 + flash) : 5.2) * unit / 108
      return .init(Float(p.x), Float(p.y), Float(y - drop > 0.01 ? gain : 0), Float(size))
    }
  }

  func flecks(yaw: Double) -> [SIMD4<Float>] {
    let lamp = simd_normalize(SIMD3<Double>(0.16, 0.9, 0.4))
    return (0..<12).map { i in
      let floor = i >= 8
      let a = Double(i) * 2.399963 + 0.4
      let n0 = simd_normalize(
        SIMD3<Double>(
          sin(a) * (floor ? 0.91 : 0.56), floor ? -0.42 : 0.83, cos(a) * (floor ? 0.91 : 0.56)))
      let n = SIMD3<Double>(
        n0.x * cos(yaw) + n0.z * sin(yaw), n0.y, -n0.x * sin(yaw) + n0.z * cos(yaw))
      let incidence = simd_dot(lamp, n)
      var r = -lamp + 2 * incidence * n
      if floor {
        let eta = 1 / 1.46
        let c = abs(incidence)
        let bend = eta * c - sqrt(max(0, 1 - eta * eta * (1 - c * c)))
        r = -eta * lamp + bend * n
        r.y = -abs(r.y)
        r.z = abs(r.z)
      }
      let sourceX = sin(a) * 0.32
      let sourceY = floor ? 0.72 : 0.65 + Double(i % 3) * 0.21
      var px: Double
      var py: Double
      var energy: Double
      if floor {
        let t = sourceY / max(0.12, -r.y)
        let side = i % 2 == 0 ? -1.0 : 1.0
        px = originX + (side * (0.94 + 0.23) + r.x * t * 0.15) * unit
        py = originY + 32 * k + r.z * t * unit * 0.08
        energy = 0.48 + 0.18 * abs(incidence)
      } else {
        let t = 1.15 / max(0.12, -r.z)
        px = originX + (sourceX + r.x * t) * unit
        py = originY - (sourceY + r.y * t) * unit
        energy = max(0, min(1, (-r.z - 0.03) * 4)) * max(0, incidence)
        energy *= max(0, min(1, (py - height * 0.32) / 55))
        energy *= max(0, min(1, (originY - py) / 25))
        if i == 4 { px -= 10 * k }
        if i == 6 { px += 10 * k }
      }
      let turn = min(1, sin(yaw) * sin(yaw) * 12)
      if i != 4 && i != 6 && i != 9 && i != 10 { energy *= turn }
      energy *= 1 + 0.35 * turn
      let sideFade = max(0, min(1, min(px, width - px) / 38))
      return .init(
        Float(px), Float(py), Float(a + atan2(r.x, max(0.1, abs(r.y))) * 0.4),
        Float(energy * sideFade))
    }
  }
}
