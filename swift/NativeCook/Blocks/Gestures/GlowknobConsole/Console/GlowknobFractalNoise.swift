import CoreGraphics
import Foundation

/// Skia's `GlowknobFractalNoise` (SkPerlinNoiseShader, fractal sum) is the SVG 1.1
/// feTurbulence reference algorithm. This is that algorithm, evaluated once
/// into an image (the housing's grain is static), with the same seed handling
/// and lattice/gradient setup.
struct GlowknobFractalNoise {
  private static let bSize = 0x100
  private static let bm = 0xFF
  private static let perlinN = 0x1000

  private var lattice = [Int](repeating: 0, count: 0x100 + 0x100 + 2)
  private var gradient = [[[Double]]](
    repeating: [[Double]](repeating: [0, 0], count: 0x100 + 0x100 + 2), count: 4)

  init(seed: Int) {
    let randM = 2147483647, randA = 16807, randQ = 127773, randR = 2836
    func setupSeed(_ s: Int) -> Int {
      var l = s
      if l <= 0 { l = -(l % (randM - 1)) + 1 }
      if l > randM - 1 { l = randM - 1 }
      return l
    }
    func random(_ s: Int) -> Int {
      var result = randA * (s % randQ) - randR * (s / randQ)
      if result <= 0 { result += randM }
      return result
    }
    var lSeed = setupSeed(seed)
    let b = Self.bSize
    var i = 0
    for k in 0..<4 {
      i = 0
      while i < b {
        lattice[i] = i
        for j in 0..<2 {
          lSeed = random(lSeed)
          gradient[k][i][j] = Double((lSeed % (b + b)) - b) / Double(b)
        }
        let s = (gradient[k][i][0] * gradient[k][i][0] + gradient[k][i][1] * gradient[k][i][1]).squareRoot()
        gradient[k][i][0] /= s
        gradient[k][i][1] /= s
        i += 1
      }
    }
    i -= 1
    while i > 0 {
      let k = lattice[i]
      lSeed = random(lSeed)
      let j = lSeed % b
      lattice[i] = lattice[j]
      lattice[j] = k
      i -= 1
    }
    for i in 0..<(b + 2) {
      lattice[b + i] = lattice[i]
      for k in 0..<4 {
        for j in 0..<2 { gradient[k][b + i][j] = gradient[k][i][j] }
      }
    }
  }

  private func noise2(_ channel: Int, _ vx: Double, _ vy: Double) -> Double {
    func sCurve(_ t: Double) -> Double { t * t * (3 - 2 * t) }
    func lerp(_ t: Double, _ a: Double, _ b: Double) -> Double { a + t * (b - a) }
    var t = vx + Double(Self.perlinN)
    let bx0 = Int(t) & Self.bm, bx1 = (bx0 + 1) & Self.bm
    let rx0 = t - Double(Int(t)), rx1 = rx0 - 1
    t = vy + Double(Self.perlinN)
    let by0 = Int(t) & Self.bm, by1 = (by0 + 1) & Self.bm
    let ry0 = t - Double(Int(t)), ry1 = ry0 - 1
    let i = lattice[bx0], j = lattice[bx1]
    let b00 = lattice[i + by0], b10 = lattice[j + by0], b01 = lattice[i + by1], b11 = lattice[j + by1]
    let sx = sCurve(rx0), sy = sCurve(ry0)
    let g = gradient[channel]
    var u = rx0 * g[b00][0] + ry0 * g[b00][1]
    var v = rx1 * g[b10][0] + ry0 * g[b10][1]
    let a = lerp(sx, u, v)
    u = rx0 * g[b01][0] + ry1 * g[b01][1]
    v = rx1 * g[b11][0] + ry1 * g[b11][1]
    let bb = lerp(sx, u, v)
    return lerp(sy, a, bb)
  }

  /// Fractal sum over `octaves`, as feTurbulence type="fractalNoise".
  func turbulence(_ channel: Int, _ x: Double, _ y: Double, freqX: Double, freqY: Double, octaves: Int) -> Double {
    var sum = 0.0, ratio = 1.0
    for _ in 0..<octaves {
      sum += noise2(channel, x * freqX * ratio, y * freqY * ratio) / ratio
      ratio *= 2
    }
    return sum
  }

  /// The shader rendered over `units` (drawing units) at `pixelsPerUnit`,
  /// sampled at pixel centers; GlowknobRGBA = (turbulence·255 + 255)/2, premultiplied.
  static func image(units: CGSize, pixelsPerUnit: Double, freq: Double, octaves: Int, seed: Int) -> CGImage? {
    let noise = GlowknobFractalNoise(seed: seed)
    let w = max(1, Int((units.width * pixelsPerUnit).rounded(.up)))
    let h = max(1, Int((units.height * pixelsPerUnit).rounded(.up)))
    var data = [UInt8](repeating: 0, count: w * h * 4)
    for py in 0..<h {
      let y = (Double(py) + 0.5) / pixelsPerUnit
      for px in 0..<w {
        let x = (Double(px) + 0.5) / pixelsPerUnit
        var c = [Double](repeating: 0, count: 4)
        for ch in 0..<4 {
          let v = (noise.turbulence(ch, x, y, freqX: freq, freqY: freq, octaves: octaves) * 255 + 255) / 2
          c[ch] = max(0, min(255, v)) / 255
        }
        let o = (py * w + px) * 4
        data[o] = UInt8((c[0] * c[3] * 255).rounded())
        data[o + 1] = UInt8((c[1] * c[3] * 255).rounded())
        data[o + 2] = UInt8((c[2] * c[3] * 255).rounded())
        data[o + 3] = UInt8((c[3] * 255).rounded())
      }
    }
    let provider = CGDataProvider(data: Data(data) as CFData)!
    return CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
                   space: CGColorSpace(name: CGColorSpace.sRGB)!,
                   bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                   provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
  }
}
