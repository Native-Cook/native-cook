import SwiftUI

// Row-major, column-vector matrices, matching Skia processTransform3d's multiplication order.
struct CoinCardOnboardingMatrix {
  var a: [Double] = [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]
  func times(_ b: Self) -> Self {
    var out = Self(a: Array(repeating: 0, count: 16))
    for r in 0..<4 { for c in 0..<4 { for k in 0..<4 { out.a[r * 4 + c] += a[r * 4 + k] * b.a[k * 4 + c] } } }
    return out
  }
  static func translate(_ x: Double, _ y: Double, _ z: Double = 0) -> Self {
    var m = Self(); m.a[3] = x; m.a[7] = y; m.a[11] = z; return m
  }
  static func perspective(_ distance: Double) -> Self { var m = Self(); m.a[14] = -1 / distance; return m }
  static func rotate(_ axis: Int, _ degrees: Double) -> Self {
    let t = degrees * .pi / 180, c = cos(t), s = sin(t)
    if axis == 0 { return Self(a: [1,0,0,0, 0,c,-s,0, 0,s,c,0, 0,0,0,1]) }
    if axis == 1 { return Self(a: [c,0,s,0, 0,1,0,0, -s,0,c,0, 0,0,0,1]) }
    return Self(a: [c,-s,0,0, s,c,0,0, 0,0,1,0, 0,0,0,1])
  }
  var projection: ProjectionTransform {
    var p = ProjectionTransform()
    p.m11 = a[0]; p.m12 = a[4]; p.m13 = a[12]
    p.m21 = a[1]; p.m22 = a[5]; p.m23 = a[13]
    p.m31 = a[3]; p.m32 = a[7]; p.m33 = a[15]
    return p
  }
}

struct CoinCardOnboardingCard: View {
  typealias M = CoinCardOnboardingMotion
  typealias X = CoinCardOnboardingMatrix
  let front: Bool
  let p: Double
  let cx: Double
  let cy: Double
  private let r = CoinCardOnboardingResources.shared
  var body: some View {
    let keys = front ? M.front : M.back
    let ry = M.key(keys[3], p)
    let k = M.clamp(0.5 + ry / 60)
    let matrix = X.translate(cx + M.key(keys[0], p), cy + M.key(keys[1], p))
      .times(.perspective(640)).times(.rotate(2, M.key(keys[2], p)))
      .times(.rotate(0, M.key(keys[4], p))).times(.rotate(1, ry)).times(.translate(-126, -80))
    Image(uiImage: r.card(front, k)).resizable()
      .frame(width: 252, height: 160).projectionEffect(matrix.projection)
  }
}

struct CoinCardOnboardingCoin: View {
  typealias M = CoinCardOnboardingMotion
  typealias X = CoinCardOnboardingMatrix
  let euro: Bool
  let p: Double
  let cx: Double
  let cy: Double
  let reduced: Bool
  var body: some View {
    let keys = euro ? M.euro : M.dollar
    let r = M.key(keys[2], p)
    let flip = reduced ? 0 : M.key(keys[3], p)
    let rx = euro ? flip + 14 : 0, ry = euro ? -16 : flip + 18
    let base = X.translate(cx + M.key(keys[0], p), cy + M.key(keys[1], p))
      .times(.perspective(520)).times(.rotate(0, rx)).times(.rotate(1, ry))
    let facing = cos(rx * .pi / 180) * cos(ry * .pi / 180) >= 0
    // The edge order deliberately remains 1...8 even when the coin turns away, as in RN.
    let depths = facing ? [-9, -1, -2, -3, -4, -5, -6, -7, -8, 0] : [0, -1, -2, -3, -4, -5, -6, -7, -8, -9]
    ZStack(alignment: .topLeading) {
      ForEach(depths, id: \.self) { z in
        CoinCardOnboardingCoinPlane(radius: r, depth: z, euro: euro)
          // SkCanvas.concat converts each Matrix4 separately to SkMatrix (3x3).
          // The subsequent translateZ therefore becomes identity; retain RN's actual layering.
          .projectionEffect(base.times(.translate(-40, -40)).projection)
      }
    }.frame(width: 80, height: 80, alignment: .topLeading)
  }
}

struct CoinCardOnboardingCoinPlane: View {
  typealias R = CoinCardOnboardingResources
  let radius: Double
  let depth: Int
  let euro: Bool
  var body: some View {
    Canvas { context, _ in
      var c = context
      c.translateBy(x: 40, y: 40)
      var disc = c
      disc.scaleBy(x: radius, y: radius)
      if depth == 0 || depth == -9 {
        disc.fill(R.unitCircle, with: .linearGradient(Gradient(stops: [
          .init(color: R.color(0xffffff), location: 0), .init(color: R.color(0xc9ccd4), location: 0.4),
          .init(color: R.color(0x8e939e), location: 0.75), .init(color: R.color(0xe9ebf0), location: 1)
        ]), startPoint: CGPoint(x: -1, y: -1), endPoint: CGPoint(x: 1, y: 1)))
        var ring = c
        ring.scaleBy(x: radius * 0.8, y: radius * 0.8)
        ring.stroke(R.unitCircle, with: .color(R.color(0x787c88, 0.55)), lineWidth: 2 / (radius * 0.8))
        if depth == 0 {
          c.draw(Image(uiImage: R.shared.symbols[euro ? 1 : 0]), in: CGRect(x: -32, y: radius * 0.36 - 32, width: 64, height: 64))
        }
      } else {
        disc.fill(R.unitCircle, with: .color(R.color(depth % 2 == 0 ? 0x9ca1ab : 0x868b96)))
      }
    }.frame(width: 80, height: 80)
  }
}
