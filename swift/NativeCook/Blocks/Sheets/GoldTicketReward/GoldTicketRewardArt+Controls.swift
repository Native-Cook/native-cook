import SwiftUI

extension GoldTicketRewardArt {
  func mixedColor(_ first: Int, _ second: Int, _ p: Double, _ alpha: Double = 1) -> Color {
    Color(
      .sRGB, red: M.mix(Double((first >> 16) & 255), Double((second >> 16) & 255), p) / 255,
      green: M.mix(Double((first >> 8) & 255), Double((second >> 8) & 255), p) / 255,
      blue: M.mix(Double(first & 255), Double(second & 255), p) / 255, opacity: alpha)
  }
  func drawBenefits(_ c: GraphicsContext, _ f: F) {
    let m = f.benefits
    for index in 0..<2 {
      let mi = index == 0 ? f.benefits : f.benefits2
      text(
        c, "benefit\(index)", M.mix(A.benefits[index].x, B.benefits[index].x, mi),
        M.mix(A.benefits[index].y, B.benefits[index].y, mi), 0.5, M.mix(1, H.BENEFIT_B_SCALE, mi),
        .color(mixedColor(C.bandInk, 0xffffff, mi, M.mix(1, 0.92, mi))))
    }
    let x = M.mix(A.seps[0], B.sep, m)
    let h = M.mix(A.sepH, B.sepH, m)
    let y = M.mix(A.benefits[0].y, B.benefits[0].y, m)
    c.fill(
      rect(x - 0.6, y - h / 2, 1.2, h),
      with: .color(mixedColor(C.bandInk, 0xffffff, m, M.mix(0.45, 0.6, m))))
    if f.third > 0.01 {
      text(
        c, "benefit2", A.benefits[2].x, A.benefits[2].y, 0.5, 1,
        .color(Self.color(C.bandInk, f.third)))
      c.fill(
        rect(A.seps[1] - 0.6, A.benefits[0].y - A.sepH / 2, 1.2, A.sepH),
        with: .color(Self.color(C.bandInk, 0.45 * f.third)))
    }
  }
  func drawButton(_ context: GraphicsContext, _ f: F, _ live: Live) {
    let m = f.button
    let box = M.boxMix(A.button, B.button, m)
    var c = context
    c.translateBy(x: box.x + box.w / 2, y: box.y + box.h / 2)
    let press = 1 - 0.04 * live.cta
    c.scaleBy(x: press, y: press)
    if f.ctaGlow > 0.01 {
      let grow = 3 + 5 * f.ctaGlow
      var glow = c
      glow.addFilter(.blur(radius: 6 + 8 * f.ctaGlow))
      glow.fill(
        rect(
          -box.w / 2 - grow, -box.h / 2 - grow, box.w + grow * 2, box.h + grow * 2,
          A.radius + 2 + grow), with: .color(Self.color(0xffffff, 0.42 * f.ctaGlow)))
    }
    let rounded = rect(-box.w / 2, -box.h / 2, box.w, box.h, A.radius + 2)
    c.clip(to: rounded)
    let edge = -box.w / 2 - 30 + (box.w + 60) * m
    var white = Path()
    white.move(to: CGPoint(x: -box.w, y: -box.h))
    white.addLine(to: CGPoint(x: edge + 12, y: -box.h))
    white.addLine(to: CGPoint(x: edge - 12, y: box.h))
    white.addLine(to: CGPoint(x: -box.w, y: box.h))
    white.closeSubpath()
    let scale = M.mix(1, H.WIDTH.ctaB / H.WIDTH.ctaA, m)
    if m < 0.999 {
      c.fill(rounded, with: .color(Self.color(C.ctaA)))
      var a = c
      if m > 0.001 { a.clip(to: white, options: .inverse) }
      text(a, "cta", 0, 0, 0.5, scale, .color(Self.color(C.ctaAText)))
    }
    if m > 0.001 {
      var b = c
      if m < 0.999 { b.clip(to: white) }
      b.fill(rounded, with: .color(Self.color(C.ctaB)))
      text(b, "cta", 0, 0, 0.5, scale, .color(Self.color(C.ctaBText)))
    }
  }
}
