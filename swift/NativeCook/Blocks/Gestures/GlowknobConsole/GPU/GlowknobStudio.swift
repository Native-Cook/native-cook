import Foundation
import simd

struct GlowknobSoftBox {
  var size: SIMD2<Double>
  var at: SIMD3<Double>
  /// Radiance (linear); above 1 for highlights that read on glossy paint.
  var power: Double
  var tint = SIMD3<Double>(1, 1, 0.97)
}

struct GlowknobStudioOptions {
  /// Lower hemisphere (the floor bounce), linear RGB.
  var floor: SIMD3<Double>
  /// Upper hemisphere, linear RGB.
  var sky: SIMD3<Double>
  /// Where the floor fades into the sky, as [from, to] of the up vector.
  var horizon = SIMD2<Double>(-0.8, -0.3)
  var boxes: [GlowknobSoftBox]
}

/// A procedural studio environment (gpu/studio.ts): a two-tone dome and a few
/// emissive soft boxes, seen from the origin. three.js renders that scene into
/// a cube camera and PMREM-filters it; here it is ray-cast per texel into an
/// equirectangular HDR (Radiance RGBE) that SceneKit's `lightingEnvironment`
/// prefilters itself. The scene is the same: a dome of radius 20 colored
/// `mix(floor, sky, smoothstep(horizon, up))` and planes of `size` at `at`
/// facing the origin (Object3D.lookAt, up +Y), double-sided, `power · tint`.
enum GlowknobStudio {
  static func equirect(_ o: GlowknobStudioOptions, width: Int = 1024, blurSigma: Double = 0.035) -> [SIMD3<Float>] {
    let height = width / 2
    struct Plane { var center, x, y, n: SIMD3<Double>; var half: SIMD2<Double>; var color: SIMD3<Double> }
    let planes: [Plane] = o.boxes.map { b in
      // lookAt(0,0,0) for a non-camera object: +Z faces the target.
      let z = simd_normalize(-b.at)
      var x = simd_cross(SIMD3(0, 1, 0), z)
      if simd_length(x) < 1e-9 { x = SIMD3(1, 0, 0) }
      x = simd_normalize(x)
      let y = simd_cross(z, x)
      return Plane(center: b.at, x: x, y: y, n: z, half: b.size / 2, color: b.tint * b.power)
    }
    func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
      let t = max(0, min(1, (x - a) / (b - a)))
      return t * t * (3 - 2 * t)
    }
    func radiance(_ d: SIMD3<Double>) -> SIMD3<Double> {
      var best = Double.infinity
      var color: SIMD3<Double>?
      for p in planes {
        let denom = simd_dot(d, p.n)
        if abs(denom) < 1e-9 { continue }
        let t = simd_dot(p.center, p.n) / denom
        if t <= 0 || t >= best || t > 20 { continue }
        let hit = d * t - p.center
        if abs(simd_dot(hit, p.x)) <= p.half.x && abs(simd_dot(hit, p.y)) <= p.half.y {
          best = t
          color = p.color
        }
      }
      if let color { return color }
      return simd_mix(o.floor, o.sky, SIMD3(repeating: smoothstep(o.horizon.x, o.horizon.y, d.y)))
    }
    var pixels = [SIMD3<Float>](repeating: .zero, count: width * height)
    // 2×2 supersampling per texel.
    for py in 0..<height {
      for px in 0..<width {
        var sum = SIMD3<Double>.zero
        for sy in 0..<2 {
          for sx in 0..<2 {
            let u = (Double(px) + (Double(sx) + 0.5) / 2) / Double(width)
            let v = (Double(py) + (Double(sy) + 0.5) / 2) / Double(height)
            sum += radiance(direction(u: u, v: v))
          }
        }
        pixels[py * width + px] = SIMD3<Float>(sum / 4)
      }
    }
    return blur(pixels, width: width, height: height, sigma: blurSigma)
  }

  /// SceneKit's equirectangular convention: u = 0.5 looks down −Z, u grows
  /// toward +X; v = 0 is straight up.
  static func direction(u: Double, v: Double) -> SIMD3<Double> {
    let phi = (u - 0.5) * 2 * Double.pi
    let theta = v * Double.pi
    return SIMD3(sin(theta) * sin(phi), cos(theta), -sin(theta) * cos(phi))
  }

  /// PMREMGenerator.fromScene(scene, sigma 0.035): a small angular blur of the
  /// whole environment before prefiltering. Applied here as a separable
  /// Gaussian in equirect space (horizontal sigma widened by 1/sin θ).
  private static func blur(_ src: [SIMD3<Float>], width: Int, height: Int, sigma: Double) -> [SIMD3<Float>] {
    guard sigma > 0 else { return src }
    let radPerPx = Double.pi / Double(height)
    var tmp = src
    for y in 0..<height {
      let theta = (Double(y) + 0.5) / Double(height) * Double.pi
      let sx = min(Double(width) / 4, sigma / radPerPx / max(sin(theta), 0.05))
      let r = Int(ceil(sx * 3))
      let weights = (-r...r).map { Float(exp(-Double($0 * $0) / (2 * sx * sx))) }
      let total = weights.reduce(0, +)
      for x in 0..<width {
        var acc = SIMD3<Float>.zero
        for (i, k) in (-r...r).enumerated() {
          let xx = ((x + k) % width + width) % width
          acc += src[y * width + xx] * weights[i]
        }
        tmp[y * width + x] = acc / total
      }
    }
    var out = tmp
    let sy = sigma / radPerPx
    let r = Int(ceil(sy * 3))
    let weights = (-r...r).map { Float(exp(-Double($0 * $0) / (2 * sy * sy))) }
    for y in 0..<height {
      for x in 0..<width {
        var acc = SIMD3<Float>.zero, total: Float = 0
        for (i, k) in (-r...r).enumerated() {
          let yy = y + k
          if yy < 0 || yy >= height { continue }
          acc += tmp[yy * width + x] * weights[i]
          total += weights[i]
        }
        out[y * width + x] = acc / total
      }
    }
    return out
  }

  /// Radiance .hdr (RGBE, flat scanlines).
  static func writeHDR(_ pixels: [SIMD3<Float>], width: Int, height: Int, to url: URL) throws {
    var data = Data("#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y \(height) +X \(width)\n".utf8)
    data.reserveCapacity(data.count + pixels.count * 4)
    for p in pixels {
      let m = max(p.x, max(p.y, p.z))
      if m < 1e-32 {
        data.append(contentsOf: [0, 0, 0, 0])
      } else {
        let e = frexp(Double(m))
        let scale = e.0 * 256 / Double(m)
        data.append(contentsOf: [UInt8(Double(p.x) * scale), UInt8(Double(p.y) * scale), UInt8(Double(p.z) * scale),
                                 UInt8(e.1 + 128)])
      }
    }
    try data.write(to: url)
  }
}
