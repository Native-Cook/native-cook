import SwiftUI

// All drawing stays in the RN reference space; only the outer canvas scales with width.
struct SunUvBuddyArt {
  typealias M = SunUvBuddyMotion
  typealias Theme = SunUvBuddyTheme
  typealias C = SunUvBuddyTheme.C
  typealias UI = SunUvBuddyTheme.UI
  static let tau = Double.pi * 2

  func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(
      .sRGB, red: Double((hex >> 16) & 255) / 255,
      green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
  }
  func mixColor(_ a: Int, _ b: Int, _ k: Double) -> Color {
    let rgb = [16, 8, 0].map { shift in
      M.mix(Double((a >> shift) & 255), Double((b >> shift) & 255), k) / 255
    }
    return Color(.sRGB, red: rgb[0], green: rgb[1], blue: rgb[2])
  }
  func skyColor(_ cold: Int, _ warm: Int, _ hot: Int, _ uv: Double) -> Color {
    let rgb = [16, 8, 0].map { shift in
      M.mix(
        M.mix(Double((cold >> shift) & 255), Double((warm >> shift) & 255), M.smooth(uv / 3)),
        Double((hot >> shift) & 255), M.smooth(uv - 4)) / 255
    }
    return Color(.sRGB, red: rgb[0], green: rgb[1], blue: rgb[2])
  }
  func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double = 0) -> Path {
    Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: radius)
  }
  func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
  }
  func line(
    _ ctx: inout GraphicsContext, _ x: Double, _ y: Double, _ x1: Double, _ y1: Double,
    _ color: Color, _ width: Double, _ round: Bool = false
  ) {
    var path = Path()
    path.move(to: CGPoint(x: x, y: y))
    path.addLine(to: CGPoint(x: x1, y: y1))
    ctx.stroke(
      path, with: .color(color),
      style: StrokeStyle(lineWidth: width, lineCap: round ? .round : .butt))
  }
  func text(
    _ ctx: inout GraphicsContext, _ value: String, _ x: Double, _ cy: Double,
    _ size: Double, _ face: String, _ ink: Color, _ anchor: Double = 0.5,
    baseline: Bool = false
  ) {
    _ = Theme.registered
    let resolved = ctx.resolve(
      Text(value).font(.custom(face, fixedSize: size)).foregroundColor(ink))
    let measured = resolved.measure(in: CGSize(width: 10000, height: 1000))
    let y = cy + (baseline ? 0 : size * 0.36) - resolved.firstBaseline(in: measured)
    ctx.draw(resolved, at: CGPoint(x: x - measured.width * anchor, y: y), anchor: .topLeading)
  }
  func textWidth(_ ctx: GraphicsContext, _ value: String, _ size: Double, _ face: String) -> Double
  {
    _ = Theme.registered
    return ctx.resolve(Text(value).font(.custom(face, fixedSize: size))).measure(
      in: CGSize(width: 10000, height: 1000)
    ).width
  }
  func barX(_ i: Int) -> Double { UI.bars.x0 + Double(i) * (UI.bars.w + UI.bars.gap) }
  func heightFor(_ v: Double) -> Double {
    let h = UI.bars.h
    let i = max(0, min(h.count - 2, Int(floor(v))))
    return M.mix(h[i], h[i + 1], max(0, min(1, v - Double(i))))
  }
  func barColor(_ v: Double) -> Color {
    if v < 2.5 { return color(C.barLow) }
    if v < 3 { return mixColor(C.barLow, C.barMid, (v - 2.5) * 2) }
    if v < 5.5 { return color(C.barMid) }
    return mixColor(C.barMid, C.barHigh, min(1, (v - 5.5) * 2))
  }
  func drawSun(_ original: GraphicsContext, _ live: M.Live, _ uv: Double) {
    var ctx = original
    let sun = UI.sun
    let radius = sun.r * live.size
    let squash =
      0.06 * sin(Double.pi * min(1, live.squish / 0.5)) * exp(-live.squish * 3)
      * (live.squish >= 0 ? 1 : 0)
    let breathe = 0.012 * sin(Self.tau * 2 * live.amb / M.LOOP)
    let rays = M.smooth(uv - 3.4)
    if rays > 0.01 {
      let spin = 360 * live.amb / M.LOOP / 3
      for k in 0..<12 {
        let angle = (Double(k) * 30 + spin) * Double.pi / 180
        let inner = radius * 1.12
        let outer = radius * (1.3 + 0.06 * sin(Self.tau * 2 * live.amb / M.LOOP + Double(k)))
        line(
          &ctx, sun.x + cos(angle) * inner, sun.y + sin(angle) * inner,
          sun.x + cos(angle) * outer, sun.y + sin(angle) * outer, color(C.sun, 0.55 * rays), 7, true
        )
      }
    }
    let glowAlpha = 0.22 * M.smooth(uv / 3) + 0.4 * M.smooth(uv - 4)
    ctx.fill(
      circle(sun.x, sun.y, radius * 1.6),
      with: .radialGradient(
        Gradient(colors: [color(0xFFE97A, glowAlpha), color(0xFFE97A, 0)]),
        center: CGPoint(x: sun.x, y: sun.y), startRadius: 0, endRadius: radius * 1.6))
    ctx.translateBy(x: sun.x, y: sun.y + radius * 0.9)
    ctx.scaleBy(x: 1 + squash + breathe, y: 1 - squash - breathe * 0.6)
    ctx.translateBy(x: 0, y: -radius * 0.9)
    var body = Path()
    let phase = Self.tau * live.amb / M.LOOP
    for k in 0...120 {
      let angle = Double(k) / 120 * Self.tau
      let rr = radius * (1 + 0.05 * cos(14 * angle + phase) + 0.01 * sin(3 * angle - phase * 2))
      let point = CGPoint(x: cos(angle) * rr, y: sin(angle) * rr)
      if k == 0 { body.move(to: point) } else { body.addLine(to: point) }
    }
    body.closeSubpath()
    ctx.fill(body, with: .color(color(C.sun)))
    ctx.fill(
      rect(radius * 0.42, radius * 0.62, radius * 0.78, radius * 0.19, 3),
      with: .color(color(C.sun)))
    ctx.fill(
      rect(radius * 1.06, radius * 0.24, radius * 0.15, radius * 0.55, 3),
      with: .color(color(C.sun)))
    let fx = radius * 0.38
    let fy = -radius * 0.16
    let sleepy = 1 - M.smooth(uv / 0.8)
    let shades = M.smooth(uv - 4.2)
    let eyeHeight = M.mix(7.5, 1.6, sleepy)
    for eye in 0..<2 {
      let look = max(-1, min(1, (barX(0) + UI.bars.w / 2 + live.hour * (UI.bars.w + UI.bars.gap) - UI.sun.x) / 160))
      let ex = fx + (eye == 0 ? -6.5 : 6.5) + 2.6 * look
      ctx.fill(rect(ex - 2.4, fy - eyeHeight / 2, 4.8, eyeHeight, 1.6), with: .color(color(C.ink)))
    }
    let open = M.smooth((uv - 1) / 2)
    var mouth = Path()
    mouth.move(to: CGPoint(x: fx - 4, y: fy + 9))
    mouth.addQuadCurve(
      to: CGPoint(x: fx + 4, y: fy + 9), control: CGPoint(x: fx, y: fy + 13 + 3 * open))
    mouth.closeSubpath()
    ctx.fill(mouth, with: .color(color(C.ink)))
    if shades > 0.01 {
      var glasses = ctx
      glasses.translateBy(x: 0, y: (1 - shades) * -26)
      let ink = color(0x17181C, shades)
      glasses.fill(rect(fx - 15, fy - 6, 13, 10, 4), with: .color(ink))
      glasses.fill(rect(fx + 2, fy - 6, 13, 10, 4), with: .color(ink))
      line(&glasses, fx - 2, fy - 3, fx + 2, fy - 3, ink, 2)
      line(&glasses, fx - 15, fy - 3, fx - 24, fy - 6, ink, 2)
      glasses.fill(rect(fx - 12, fy - 4.5, 4, 2.2, 1), with: .color(color(0xFFFFFF, 0.35 * shades)))
    }
    if sleepy > 0.3 {
      text(
        &ctx, "z", fx + 18, fy - 14 - 4 * sin(Self.tau * 3 * live.amb / M.LOOP), 13,
        "Nunito-ExtraBold", color(C.ink, (sleepy - 0.3) * 0.8), 0, baseline: true)
    }
  }
  func drawScene(_ original: GraphicsContext, _ size: CGSize, _ live: M.Live) {
    var ctx = original
    ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(color(C.page)))
    let scale = size.width / Theme.REF_W
    ctx.scaleBy(x: scale, y: scale)
    let uv = M.uvAt(live.hour, live.from, live.to, live.tau)
    ctx.fill(rect(0, -20, 402, UI.sheet.y + 40), with: .color(color(C.map)))
    line(&ctx, 40, -10, 120, 70, color(0xFFFFFF, 0.6), 1.2)
    line(&ctx, 230, -10, 210, 70, color(0xFFFFFF, 0.6), 1.2)
    line(&ctx, 300, 20, 402, 0, color(0xFFFFFF, 0.6), 1.2)
    let top = skyColor(0xEEF2FA, C.cardTop, 0xFFF1C2, uv)
    let bottom = skyColor(0xDCE5F4, C.cardBottom, 0xFFD27A, uv)
    ctx.fill(
      rect(0, UI.sheet.y, 402, UI.sheet.bottom - UI.sheet.y, UI.sheet.r),
      with: .linearGradient(
        Gradient(colors: [top, bottom]), startPoint: CGPoint(x: 0, y: UI.sheet.y),
        endPoint: CGPoint(x: 0, y: UI.sheet.bottom)))
    text(&ctx, Theme.title, 201, UI.title.y, UI.title.size, "Inter-SemiBold", color(C.ink))
    text(&ctx, Theme.place, 201, UI.place.y, UI.place.size, "Inter-Regular", color(C.grey))
    ctx.fill(circle(UI.close.x, UI.close.y, UI.close.r), with: .color(color(C.ink, 0.06)))
    line(
      &ctx, UI.close.x - 6, UI.close.y - 6, UI.close.x + 6, UI.close.y + 6, color(C.ink), 1.9, true)
    line(
      &ctx, UI.close.x + 6, UI.close.y - 6, UI.close.x - 6, UI.close.y + 6, color(C.ink), 1.9, true)
    for i in 0..<5 {
      let ch = UI.chips
      let on = i == live.to
      let press = on ? 1 - 0.04 * live.dayPress : 1
      var chip = ctx
      chip.translateBy(x: ch.x[i] + ch.w[i] / 2, y: ch.y)
      chip.scaleBy(x: press, y: press)
      chip.fill(
        rect(-ch.w[i] / 2, -ch.h / 2, ch.w[i], ch.h, ch.h / 2),
        with: .color(color(on ? C.chipOn : C.chip)))
      let tw = textWidth(ctx, Theme.days[i], 15, "Inter-Medium")
      let x0 = -(tw + 12) / 2
      chip.fill(circle(x0 + 3.5, 0, 3.5), with: .color(color(i == 4 ? C.green : C.yellow)))
      text(&chip, Theme.days[i], x0 + 12, 0, 15, "Inter-Medium", color(on ? 0xFFFFFF : C.ink), 0)
    }
    drawSun(ctx, live, uv)
    let near = max(0, min(10, Int(live.hour.rounded())))
    let nuv = M.uvAt(Double(near), live.from, live.to, live.tau).rounded()
    let soft = M.smooth(abs(live.hour - Double(near)) * 2.2)
    let alpha = 1 - 0.75 * soft
    let words = Theme.words(nuv)
    let head = "\(near == 3 ? "From" : "At") \(Theme.hours[near]):00 it will be \(words.level)"
    ctx.drawLayer { wordsContext in
      if soft > 0.02 { wordsContext.addFilter(.blur(radius: 3.2 * soft)) }
      var headline = wordsContext
      let k = min(1, 366 / textWidth(ctx, head, UI.headline.size, "Nunito-ExtraBold"))
      headline.translateBy(x: 201, y: UI.headline.y)
      headline.scaleBy(x: k, y: k)
      text(&headline, head, 0, 0, UI.headline.size, "Nunito-ExtraBold", color(C.ink, alpha))
      var badge = wordsContext
      badge.opacity *= alpha
      badge.fill(circle(UI.badge.x, UI.badge.y, UI.badge.r), with: .color(barColor(nuv)))
      text(&badge, String(Int(nuv)), UI.badge.x, UI.badge.y, 14, "Inter-SemiBold", color(C.ink))
      for i in 0..<2 {
        text(
          &wordsContext, words.line[i], 201, UI.line.y[i], UI.line.size, "Inter-Regular",
          color(C.ink, alpha))
      }
    }
    let props = M.smooth((uv - 1.6) / 1.2)
    if props > 0.01 {
      let ink = color(C.ink, props)
      let scale = 1.25 * (0.7 + 0.3 * props)
      var bottle = ctx
      bottle.translateBy(x: 182, y: UI.iconsY)
      bottle.scaleBy(x: scale, y: scale)
      bottle.fill(rect(-6, -10, 12, 20, 2.5), with: .color(ink))
      bottle.fill(rect(-4, -12.5, 8, 3), with: .color(ink))
      bottle.fill(circle(0, -1, 2.6), with: .color(color(C.yellow, props)))
      var capContext = ctx
      capContext.translateBy(x: 222, y: UI.iconsY + 1)
      capContext.scaleBy(x: scale, y: scale)
      var cap = Path()
      cap.move(to: CGPoint(x: -12, y: 4))
      cap.addCurve(
        to: CGPoint(x: 8, y: 2), control1: CGPoint(x: -12, y: -8), control2: CGPoint(x: 6, y: -10))
      for point in [CGPoint(x: 14, y: 4), CGPoint(x: 12, y: 7), CGPoint(x: -12, y: 7)] {
        cap.addLine(to: point)
      }
      cap.closeSubpath()
      capContext.fill(cap, with: .color(ink))
    }
    let hx = barX(0) + UI.bars.w / 2 + live.hour * (UI.bars.w + UI.bars.gap)
    let vTop = M.barValue(near, live.from, live.to, live.tau)
    let by = UI.bars.bottom - heightFor(vTop)
    let bend = max(-26, min(26, -live.hourV * 9))
    var stem = Path()
    stem.move(to: CGPoint(x: hx, y: UI.sheet.bottom - 4))
    stem.addQuadCurve(
      to: CGPoint(x: hx, y: by), control: CGPoint(x: hx + bend, y: (UI.sheet.bottom + by) / 2))
    ctx.stroke(stem, with: .color(color(C.sunDeep)), lineWidth: 2)
    ctx.fill(circle(hx, UI.sheet.bottom - 2, 5), with: .color(color(C.cardBottom)))
    for i in 0..<11 {
      let value = M.barValue(i, live.from, live.to, live.tau)
      let near = exp(-pow(Double(i) - live.hour, 2) / 0.35)
      let height = heightFor(value) + 7 * near * min(1, abs(live.hourV) / 3)
      let x = barX(i)
      ctx.fill(
        rect(x, UI.bars.bottom - height, UI.bars.w, height + 6, 6), with: .color(barColor(value)))
      let label = Int(value.rounded())
      text(
        &ctx, String(label), x + UI.bars.w / 2, UI.bars.bottom - 17, 15, "Inter-Medium",
        color(label == 0 ? 0x9A9CA4 : C.ink))
      text(
        &ctx, Theme.hours[i], x + UI.bars.w / 2, UI.bars.labelY, 13.5, "Inter-Regular",
        color(C.grey))
    }
    ctx.fill(rect(0, UI.bars.bottom, 402, 8), with: .color(color(C.page)))
    ctx.fill(rect(0, 820, 402, 200, 26), with: .color(color(0xFFFFFF)))
    text(
      &ctx, Theme.learn, 22, UI.learnY + 8, 22, "Nunito-ExtraBold", color(C.ink), 0, baseline: true)
  }
}
