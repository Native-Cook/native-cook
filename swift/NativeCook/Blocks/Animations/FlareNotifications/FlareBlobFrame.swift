import Foundation

/// `flare-blobs.json`: the same numbers as `flare-blobs.ts` in the React Native
/// build, written by the fitting pipeline. Bundled as-is, never edited by hand.
///
/// Each pill's flame is `count` soft elliptical blobs keyframed every `dt`
/// seconds over a `period`-second loop, fitted by gradient descent against
/// every frame of the reference clip. Per key and blob, 9 numbers: u, v (pill
/// heights from the trailing edge / top), log a, log b (radii above the
/// minimums), angle, alpha logit, and OKLab L, a, b.
struct FlareBlobKeys: Decodable, Sendable {
  let dt: Double
  let period: Double
  let count: Int
  let minA: Double
  let minB: Double
  let kiln: [Double]
  let tide: [Double]
  let bloom: [Double]

  func keys(for id: Flare.ID) -> [Double] {
    switch id {
    case .kiln: kiln
    case .tide: tide
    case .bloom: bloom
    }
  }

  /// The bundled data. The JSON ships with the app; a missing or malformed
  /// file is a build error, not a runtime state.
  static let shared: FlareBlobKeys = {
    guard let url = Bundle.main.url(forResource: "flare-blobs", withExtension: "json")
      ?? Bundle(for: FlareBundleToken.self).url(forResource: "flare-blobs", withExtension: "json"),
      let data = try? Data(contentsOf: url),
      let keys = try? JSONDecoder().decode(FlareBlobKeys.self, from: data)
    else { fatalError("flare-blobs.json is missing from the bundle") }
    return keys
  }()
}

private final class FlareBundleToken {}

/// One frame of a pill's flame: the shader's uniform arrays, flattened
/// (`geo` and `col` are `count` float4s, `rot` is `count` float2s).
struct FlareBlobFrame: Equatable, Sendable {
  var geo: [Float]
  var col: [Float]
  var rot: [Float]
}

extension FlareBlobKeys {
  /// `blobFrame(keys, t)` from `flare-blob-frame.ts`: interpolates the fitted
  /// blob keys at flame time `t` (seconds) with the cyclic Catmull-Rom curve
  /// the fit used, then maps them to the shader's uniforms: radii are
  /// `min + exp(log r)`, alpha is the logistic of its logit, the angle is
  /// handed over as (cos, sin). Evaluated in double precision, as in JS, and
  /// rounded to Float only for the GPU.
  func frame(_ keys: [Double], at t: Double) -> FlareBlobFrame {
    let n = count
    let k = Int(flareJsRound(period / dt))
    // JS `%` is the truncated remainder, like `fmod`.
    var x = fmod(t / dt, Double(k))
    if x < 0 { x += Double(k) }
    let i1 = Int(x.rounded(.down))
    let f = x - Double(i1)
    let f2 = f * f
    let f3 = f2 * f
    let k0 = ((i1 - 1 + k) % k) * n * 9
    let k1 = (i1 % k) * n * 9
    let k2 = ((i1 + 1) % k) * n * 9
    let k3 = ((i1 + 2) % k) * n * 9

    var geo = [Float](repeating: 0, count: n * 4)
    var col = [Float](repeating: 0, count: n * 4)
    var rot = [Float](repeating: 0, count: n * 2)
    keys.withUnsafeBufferPointer { p in
      func at(_ b: Int, _ c: Int) -> Double {
        let p0 = p[k0 + b * 9 + c]
        let p1 = p[k1 + b * 9 + c]
        let p2 = p[k2 + b * 9 + c]
        let p3 = p[k3 + b * 9 + c]
        return 0.5 * (2 * p1 + (p2 - p0) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f2
          + (3 * p1 - p0 - 3 * p2 + p3) * f3)
      }
      for b in 0..<n {
        geo[b * 4] = Float(at(b, 0))
        geo[b * 4 + 1] = Float(at(b, 1))
        geo[b * 4 + 2] = Float(minA + exp(at(b, 2)))
        geo[b * 4 + 3] = Float(minB + exp(at(b, 3)))
        let ang = at(b, 4)
        rot[b * 2] = Float(cos(ang))
        rot[b * 2 + 1] = Float(sin(ang))
        col[b * 4] = Float(at(b, 6))
        col[b * 4 + 1] = Float(at(b, 7))
        col[b * 4 + 2] = Float(at(b, 8))
        col[b * 4 + 3] = Float(1 / (1 + exp(-at(b, 5))))
      }
    }
    return FlareBlobFrame(geo: geo, col: col, rot: rot)
  }
}
