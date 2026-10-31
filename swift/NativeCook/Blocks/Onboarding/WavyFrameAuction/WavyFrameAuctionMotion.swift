import SwiftUI

enum WavyFrameAuctionMotion {
  static let loop = 9.0
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static func smooth(_ x: Double) -> Double { let k = clamp(x); return k * k * (3 - 2 * k) }
  static func ease(_ x: Double) -> Double {
    let k = clamp(x)
    return k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2
  }
  static func script(_ t: Double) -> Double {
    if t < 1.2 { return 0 }
    if t < 2.2 { return 0.42 * ease(t - 1.2) }
    if t < 5.8 {
      let x = clamp((t - 2.2) / 0.7)
      return mix(0.42, 1, 1 - pow(1 - x, 3) + sin(x * .pi) * 0.06)
    }
    if t < 6.8 { return 1 - ease(t - 5.8) }
    return 0
  }
  static func lift(_ t: Double) -> Double {
    var l = 0.0
    for (a, b) in [(1.2, 2.2), (5.8, 6.8)] {
      if t >= a && t < b { l = max(l, 1 - exp(-(t - a) * 10)) }
      else if t >= b { l = max(l, (1 - exp(-(b - a) * 10)) * exp(-(t - b) * 10)) }
    }
    return l
  }
  static func frame(_ s: Double, _ size: CGSize, _ top: Double) -> (rect: CGRect, r: Double) {
    let k = smooth(s)
    let w = mix(250, size.width - 32, k)
    let h = mix(300, size.height * 0.56, k)
    let cy = mix(top + 230, top + 64 + h / 2, k)
    return (CGRect(x: size.width / 2 - w / 2, y: cy - h / 2, width: w, height: h), mix(30, 28, k))
  }
  static func wavy(_ f: CGRect, _ r: Double, _ a: Double, _ ph: Double) -> Path {
    let x = f.minX, y = f.minY, w = f.width, h = f.height
    let top = w - 2 * r, side = h - 2 * r, arc = Double.pi / 2 * r
    let per = 2 * (w + h - 4 * r) + 2 * .pi * r
    var path = Path()
    for i in 0...220 {
      var d = Double(i) / 220 * per
      var px = 0.0, py = 0.0, nx = 0.0, ny = 0.0, wave = -1.0
      if d < top { px = x + r + d; py = y; ny = -1; wave = d / top }
      else {
        d -= top
        if d < arc {
          let an = -.pi / 2 + d / r
          px = x + w - r + cos(an) * r; py = y + r + sin(an) * r; nx = cos(an); ny = sin(an)
        } else {
          d -= arc
          if d < side { px = x + w; py = y + r + d; nx = 1; wave = d / side }
          else {
            d -= side
            if d < arc {
              let an = d / r
              px = x + w - r + cos(an) * r; py = y + h - r + sin(an) * r; nx = cos(an); ny = sin(an)
            } else {
              d -= arc
              if d < top { px = x + w - r - d; py = y + h; ny = 1; wave = d / top }
              else {
                d -= top
                if d < arc {
                  let an = .pi / 2 + d / r
                  px = x + r + cos(an) * r; py = y + h - r + sin(an) * r; nx = cos(an); ny = sin(an)
                } else {
                  d -= arc
                  if d < side { px = x; py = y + h - r - d; nx = -1; wave = d / side }
                  else {
                    d -= side
                    let an = .pi + min(d, arc) / r
                    px = x + r + cos(an) * r; py = y + r + sin(an) * r; nx = cos(an); ny = sin(an)
                  }
                }
              }
            }
          }
        }
      }
      let offset = wave >= 0 ? a * sin(wave * 5 * 2 * .pi + ph) * sin(wave * .pi) : 0
      let p = CGPoint(x: px + nx * offset, y: py + ny * offset)
      if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
    }
    path.closeSubpath()
    return path
  }
  static func tab(_ size: CGSize, _ bottom: Double, _ s: Double, _ ph: Double) -> (Path, Double) {
    let k = smooth(s), w = size.width
    let tw = mix(150, w - 40, k), th = mix(76, 58, k)
    let tx = w / 2 - tw / 2, ty = size.height - bottom - mix(4, 22, k) - th
    let a = mix(10, 0, k), rr = mix(18, th / 2, k)
    var p = Path()
    p.move(to: CGPoint(x: tx, y: ty + rr))
    p.addQuadCurve(to: CGPoint(x: tx + rr, y: ty), control: CGPoint(x: tx, y: ty))
    for i in 0...30 {
      let u = Double(i) / 30
      p.addLine(to: CGPoint(x: tx + rr + u * (tw - 2 * rr), y: ty - a * sin(u * .pi) * (0.6 + 0.4 * cos(u * .pi * 4 + ph * 3))))
    }
    p.addQuadCurve(to: CGPoint(x: tx + tw, y: ty + rr), control: CGPoint(x: tx + tw, y: ty))
    p.addLine(to: CGPoint(x: tx + tw, y: ty + th - rr * k))
    if k > 0.01 { p.addQuadCurve(to: CGPoint(x: tx + tw - rr * k, y: ty + th), control: CGPoint(x: tx + tw, y: ty + th)) }
    else { p.addLine(to: CGPoint(x: tx + tw, y: ty + th + 40)) }
    p.addLine(to: CGPoint(x: tx + rr * k, y: ty + th + (k > 0.01 ? 0 : 40)))
    if k > 0.01 { p.addQuadCurve(to: CGPoint(x: tx, y: ty + th - rr * k), control: CGPoint(x: tx, y: ty + th)) }
    p.closeSubpath()
    return (p, ty)
  }
}
