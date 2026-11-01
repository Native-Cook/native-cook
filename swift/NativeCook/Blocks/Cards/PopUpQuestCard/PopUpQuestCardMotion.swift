import SwiftUI

enum PopUpQuestCardMotion {
  static let loop = 9.0
  struct Pose { var o = 0.0; var yaw = 0.0 }
  static func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
  static func smooth(_ a: Double, _ b: Double, _ v: Double) -> Double {
    let x = clamp((v - a) / (b - a)); return x * x * (3 - 2 * x)
  }
  static func smoother(_ v: Double) -> Double {
    let x = clamp(v); return x * x * x * (x * (x * 6 - 15) + 10)
  }
  static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func at(_ time: Double) -> Pose {
    let t = time.truncatingRemainder(dividingBy: loop)
    var o = 0.0
    if t >= 1 && t < 2 { o = smoother(t - 1) }
    else if t >= 2 && t < 5.8 { o = 1 }
    else if t >= 5.8 && t < 6.8 { o = 1 - smoother(t - 5.8) }
    return Pose(o: o, yaw: t >= 2 && t < 5.8 ? 0.22 * sin((t - 2) / 3.8 * 2 * .pi) : 0)
  }
  struct Layout {
    let size: CGSize
    let top: Double
    let bottom: Double
    var w: Double { size.width }
    var h: Double { size.height }
    var sheetTop: Double { (h * 0.46).rounded() }
    var heroH: Double { (h * 0.5).rounded() }
    func card(_ i: Int) -> CGRect {
      let cw = (w - 44) / 2
      return CGRect(x: 16 + Double(i % 2) * (cw + 12), y: top + 176 + Double(i / 2) * 226, width: cw, height: 214)
    }
    func illustration(_ i: Int) -> CGRect {
      let c = card(i); return CGRect(x: c.minX + 8, y: c.minY + 8, width: c.width - 16, height: 138)
    }
    var page: CGRect { CGRect(x: 26, y: top + 70, width: w - 52, height: sheetTop - top - 86) }
    func frame(_ pose: Pose, _ active: Int, _ reduced: Bool) -> CGRect {
      let a = illustration(active), b = page, e = reduced ? 1 : pose.o
      return CGRect(x: mix(a.minX, b.minX, e), y: mix(a.minY, b.minY, e), width: mix(a.width, b.width, e), height: mix(a.height, b.height, e))
    }
    func clip(_ pose: Pose, _ active: Int, _ reduced: Bool) -> CGRect {
      let a = illustration(active), e = reduced ? 1 : pose.o
      return CGRect(x: mix(a.minX, 0, e), y: mix(a.minY, 0, e), width: mix(a.width, w, e), height: mix(a.height, sheetTop + 40, e))
    }
    // The same 4×4 transform as Skia, reduced to the plane's projective homography.
    func projection(_ pose: Pose, _ active: Int, _ reduced: Bool, _ layer: Int, _ hinge: Double) -> ProjectionTransform {
      let f = frame(pose, active, reduced), e = reduced ? 1 : pose.o
      let sc = mix(max(f.width / 1000, f.height / 700), min(f.width / 1000, f.height / 700), e)
      let phi = reduced ? 0 : 0.82 * smooth(0.15, 1, pose.o), yaw = reduced ? 0 : pose.yaw
      let cy = cos(yaw), sy = sin(yaw), cp = cos(phi), sp = sin(phi)
      let page = layer == 1, s = (700 - hinge) * sc
      let yV = page ? sc * cp : sc
      let yC = page ? -800 * sc * cp : (-100 - hinge) * sc - s * cp
      let zV = page ? sc * sp : 0
      let zC = page ? -800 * sc * sp : -s * sp
      let xa = cy * sc, xb = sy * zV, xc = -600 * sc * cy + sy * zC
      let za = -sy * sc, zb = cy * zV, zc = 600 * sc * sy + cy * zC
      let qa = -za / 760, qb = -zb / 760, qc = 1 - zc / 760
      var m = ProjectionTransform(CGAffineTransform.identity)
      m.m11 = xa + f.midX * qa; m.m21 = xb + f.midX * qb; m.m31 = xc + f.midX * qc
      m.m12 = f.maxY * qa; m.m22 = yV + f.maxY * qb; m.m32 = yC + f.maxY * qc
      m.m13 = qa; m.m23 = qb; m.m33 = qc
      return m
    }
  }
}
