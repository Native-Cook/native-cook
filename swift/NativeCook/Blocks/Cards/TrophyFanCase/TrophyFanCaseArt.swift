import SwiftUI

@MainActor struct TrophyFanCaseArt {
  let resources = TrophyFanCaseResources.shared
  static let bounds = CGRect(x: -75, y: -114, width: 150, height: 228)
  static let shade = Path(roundedRect: bounds, cornerRadius: 20)

  static func transform(_ b: TrophyFanCaseMotion.Body) -> CGAffineTransform {
    let flip = b.f * .pi / 180
    let ky = tan(sin(b.f * .pi / 90) * -6 * .pi / 180)
    let kx = tan(max(-7, min(7, -b.vx * 0.006)) * .pi / 180)
    let sx = b.s * max(0.02, abs(cos(flip)))
    let sy = b.s * (1 + 0.06 * abs(sin(flip)))
    let cr = cos(b.r * .pi / 180), sr = sin(b.r * .pi / 180)
    // T · rotateZ · scaleX · scaleY · skewY · skewX, about the card's centre.
    return CGAffineTransform(
      a: cr * sx - sr * sy * ky, b: sr * sx + cr * sy * ky,
      c: cr * sx * kx - sr * sy * (1 + ky * kx),
      d: sr * sx * kx + cr * sy * (1 + ky * kx), tx: b.x, ty: b.y)
  }
  func draw(_ context: GraphicsContext, _ hd: TrophyFanCaseMotion.Hand) {
    let top = hd.top
    let order = (0..<4).filter { $0 != top } + (top >= 0 ? [top] : [])
    for i in order {
      let b = hd.cards[i]
      var c = context
      c.opacity = hd.sel >= 0 && hd.sel != i ? 0.55 : 1
      c.concatenate(Self.transform(b))
      c.addFilter(.shadow(color: .black.opacity(0.55), radius: 18, x: 0, y: 10))
      c.drawLayer { layer in
        layer.draw(b.f < 90 ? resources.fronts[i] : resources.backs[i], in: Self.bounds)
        layer.fill(Self.shade, with: .color(.black.opacity(abs(sin(b.f * .pi / 180)) * 0.55)))
      }
    }
  }
}
