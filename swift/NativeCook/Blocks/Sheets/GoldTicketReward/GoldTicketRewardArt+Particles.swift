import SwiftUI

extension GoldTicketRewardArt {
  func bez(_ a: Double, _ b: Double, _ c: Double, _ end: Double, _ q: Double) -> Double {
    let u = 1 - q
    return u * u * u * a + 3 * u * u * q * b + 3 * u * q * q * c + q * q * q * end
  }
  func drawDust(_ context: GraphicsContext, _ t: Double) {
    let out = t >= M.T.carsOut[0] && t < M.T.gold[1] + 0.2
    let home = t >= M.T.goldOut[0] && t < M.T.carsIn[1]
    if !out && !home { return }
    var c = context
    c.blendMode = .plusLighter
    for mote in resources.dust {
      let k: Double
      if out {
        if t < mote.rel || t > mote.arr + 0.06 { continue }
        k = min(1, (t - mote.rel) / (mote.arr - mote.rel))
      } else {
        if t < mote.back0 || t > mote.back0 + 0.5 { continue }
        k = 1 - (t - mote.back0) / 0.5
      }
      let q = k * k * (3 - 2 * k)
      let fadeIn = min(1, (out ? k : 1 - k) / 0.12)
      let alpha = fadeIn * (k > 0.86 && out ? 1 - (k - 0.86) / 0.14 : 1) * 0.95
      for index in 0..<2 {
        let qq = index == 0 ? q : max(0, q - (out ? 0.05 : -0.05))
        let x = bez(mote.x0, mote.c1x, mote.c2x, mote.tx, qq)
        let y = bez(mote.y0, mote.c1y, mote.c2y, mote.ty, qq)
        c.fill(
          circle(x, y, mote.size * (1 - 0.35 * q) * (index == 0 ? 1 : 0.75)),
          with: .color(
            Self.color(mote.warm > 0.5 ? 0xffd774 : 0xfff0b8, alpha * (index == 0 ? 1 : 0.4))))
      }
    }
  }
  func drawSparks(_ context: GraphicsContext, _ t: Double) {
    let tau = t - M.T.land
    if tau <= 0 || tau > 0.75 { return }
    var c = context
    c.blendMode = .plusLighter
    for index in 0..<18 {
      let i = Double(index)
      let h1 = sin((i + 1) * 78.233) * 43758.5453
      let r1 = h1 - floor(h1)
      let h2 = sin((i + 1) * 39.425) * 24634.6345
      let r2 = h2 - floor(h2)
      let side = index % 2 == 0 ? -1.0 : 1.0
      let angle = -Double.pi / 2 + side * (0.35 + r1 * 1.05)
      let speed = 150 + r2 * 190
      let life = 0.38 + r1 * 0.3
      if tau > life { continue }
      let vx = cos(angle) * speed
      let vy = sin(angle) * speed
      let ox = B.coin.x + side * B.coin.r * (0.45 + 0.4 * r2)
      let oy = B.ticket.y + 1
      let x = ox + vx * tau
      let y = oy + vy * tau + 0.5 * 900 * tau * tau
      let currentVY = vy + 900 * tau
      let fade = 1 - tau / life
      line(
        c, [CGPoint(x: x, y: y), CGPoint(x: x - vx * 0.02, y: y - currentVY * 0.02)],
        Self.color(0xffe7a0, fade), 0.7 + 1.3 * fade)
    }
  }
}
