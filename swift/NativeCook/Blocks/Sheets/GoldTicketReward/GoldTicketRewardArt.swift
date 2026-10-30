import QuartzCore
import SwiftUI

struct GoldTicketRewardArt {
  typealias H = GoldTicketRewardTheme
  typealias A = H.A
  typealias B = H.B
  typealias C = H.C
  typealias M = GoldTicketRewardMotion
  typealias F = M.Frame
  typealias R = GoldTicketRewardResources
  struct Live {
    var tiltX = 0.0
    var tiltY = 0.0
    var cta = 0.0
  }
  let resources = R.shared
  let size: CGSize
  static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(
      .sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, opacity: alpha)
  }
  func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double = 0) -> Path {
    Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: radius)
  }
  func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
  }
  func linear(
    _ stops: [(Int, Double, Double)], _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double
  ) -> GraphicsContext.Shading {
    .linearGradient(
      Gradient(stops: stops.map { .init(color: Self.color($0.0, $0.1), location: $0.2) }),
      startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
  }
  func radial(_ stops: [(Int, Double, Double)], _ x: Double, _ y: Double, _ radius: Double)
    -> GraphicsContext.Shading
  {
    .radialGradient(
      Gradient(stops: stops.map { .init(color: Self.color($0.0, $0.1), location: $0.2) }),
      center: CGPoint(x: x, y: y), startRadius: 0, endRadius: radius)
  }
  func text(
    _ context: GraphicsContext, _ key: String, _ x: Double, _ y: Double, _ ax: Double = 0.5,
    _ scale: Double = 1, _ shading: GraphicsContext.Shading, _ alpha: Double = 1
  ) {
    guard let label = resources.labels[key], alpha > 0.003 else { return }
    var c = context
    c.opacity *= alpha
    c.translateBy(x: x, y: y)
    c.scaleBy(x: scale, y: scale)
    var image = c.resolve(Image(uiImage: label.bitmap))
    image.shading = shading
    c.draw(
      image,
      in: CGRect(
        x: -label.width * ax - 12, y: label.size * 0.35 - label.baseline,
        width: label.bitmap.size.width, height: label.bitmap.size.height))
  }
  func line(_ c: GraphicsContext, _ points: [CGPoint], _ color: Color, _ width: Double) {
    var path = Path()
    if let point = points.first { path.move(to: point) }
    for point in points.dropFirst() { path.addLine(to: point) }
    c.stroke(
      path, with: .color(color),
      style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
  }
  static let star: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 0, y: -10))
    p.addQuadCurve(to: CGPoint(x: 10, y: 0), control: CGPoint(x: 1.6, y: -1.6))
    p.addQuadCurve(to: CGPoint(x: 0, y: 10), control: CGPoint(x: 1.6, y: 1.6))
    p.addQuadCurve(to: CGPoint(x: -10, y: 0), control: CGPoint(x: -1.6, y: 1.6))
    p.addQuadCurve(to: CGPoint(x: 0, y: -10), control: CGPoint(x: -1.6, y: -1.6))
    p.closeSubpath()
    return p
  }()
  func drawStar(
    _ context: GraphicsContext, _ x: Double, _ y: Double, _ half: Double, _ rotation: Double,
    _ alpha: Double, _ gold: Bool
  ) {
    var c = context
    c.translateBy(x: x, y: y)
    c.rotate(by: .radians(rotation))
    c.scaleBy(x: half / 10, y: half / 10)
    var glow = c
    glow.addFilter(.blur(radius: 3))
    glow.fill(Self.star, with: .color(Self.color(gold ? 0xffcf6a : 0xffffff, alpha * 0.55)))
    c.fill(Self.star, with: .color(Self.color(gold ? C.starGold : C.starWhite, alpha)))
    if gold {
      c.scaleBy(x: 0.45, y: 0.45)
      c.fill(Self.star, with: .color(Self.color(0xfff4cf, alpha)))
    }
  }
  func drawScene(_ context: GraphicsContext, _ f: F, _ live: Live) {
    let scale = size.width / 402
    let dy = size.height - 874 * scale
    context.draw(
      Image(uiImage: resources.page),
      in: CGRect(x: 0, y: 0, width: 402 * scale, height: 874 * scale))
    let da = C.dimA
    let db = C.dimB
    let dim = Color(
      .sRGB, red: M.mix(da[0], db[0], f.dim).rounded() / 255,
      green: M.mix(da[1], db[1], f.dim).rounded() / 255,
      blue: M.mix(da[2], db[2], f.dim).rounded() / 255, opacity: M.mix(da[3], db[3], f.dim))
    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(dim))
    var c = context
    c.translateBy(x: 0, y: dy)
    c.scaleBy(x: scale, y: scale)
    if f.glow > 0.003 {
      var glow = c
      glow.opacity *= f.glow
      let area = rect(0, A.glowTop, 402, A.stageEdge - A.glowTop)
      glow.fill(
        area,
        with: linear(
          [
            (C.glow, 0, 0), (C.glow, 0.12, 0.41), (C.glow, 0.29, 0.57), (C.glow, 0.48, 0.75),
            (C.glow, 0.6, 0.83), (C.glowHi, 0.9, 0.93), (C.glowHi, 1, 1),
          ], 0, A.glowTop, 0, A.stageEdge))
      glow.fill(
        area,
        with: radial(
          [(0xf0cf7a, 0.32, 0), (0xf0cf7a, 0, 1)], R.carsBox.x + R.carsBox.w / 2, A.stageTop - 6,
          R.carsBox.w * 0.62))
    }
    let inkR = 640 + f.ink * 1060
    let ink = radial(
      [(0x000000, 1, (inkR - 150) / inkR), (0x000000, 0, 1)], f.inkX, 874 + 700, inkR)
    if f.ink < 0.999 {
      var cream = c
      let p = M.clamp01((f.ink - 0.08) / 0.5)
      cream.opacity *= 1 - p * p * (3 - 2 * p)
      cream.drawLayer { layer in
        var stage = layer
        stage.translateBy(x: 0, y: f.ink * f.ink * 56)
        var path = Path()
        path.move(to: CGPoint(x: 0, y: A.stageEdge))
        path.addLine(to: CGPoint(x: A.stageInset, y: A.stageTop))
        path.addLine(to: CGPoint(x: 402 - A.stageInset, y: A.stageTop))
        path.addLine(to: CGPoint(x: 402, y: A.stageEdge))
        path.addLine(to: CGPoint(x: 402, y: 874))
        path.addLine(to: CGPoint(x: 0, y: 874))
        path.closeSubpath()
        stage.fill(
          path,
          with: linear(
            [(C.stageTop, 1, 0), (C.stageMid, 1, 0.42), (C.sheet, 1, 1)], 0, A.stageTop, 0, A.band.y
          ))
        if f.benefits < 0.999 {
          stage.fill(Path(A.band.rect), with: .color(Self.color(C.band, 1 - f.benefits)))
        }
        if f.ink > 0.001 {
          var erase = layer
          erase.blendMode = .destinationOut
          erase.fill(rect(0, A.glowTop, 402, 874 - A.glowTop), with: ink)
        }
      }
    }
    if f.ink > 0.001 {
      c.drawLayer { layer in
        let area = rect(0, B.fadeTop, 402, 874 - B.fadeTop)
        layer.fill(
          area,
          with: linear(
            [
              (0x5233ae, 0, 0), (0x5233ae, 0.3, 48.0 / 229), (0x5233ae, 0.66, 88.0 / 229),
              (C.purpleMid, 1, (B.full - B.fadeTop) / (874 - B.fadeTop)),
              (C.purpleLow, 1, 188.0 / 229), (C.purpleBottom, 1, 1),
            ], 0, B.fadeTop, 0, 874))
        layer.fill(
          area,
          with: radial(
            [(0x6a4fd6, 0.32, 0), (0x6a4fd6, 0, 1)], 201, B.benefits[0].y - 4, 402 * 0.55))
        if f.ink < 0.999 {
          var mask = layer
          mask.blendMode = .destinationIn
          mask.fill(rect(0, 0, 402, 874), with: ink)
        }
      }
      let rim = sin(Double.pi * min(1, f.ink * 1.15))
      if rim > 0.01 && f.ink < 0.999 {
        var light = c
        light.blendMode = .screen
        light.fill(
          rect(0, 0, 402, 874),
          with: radial(
            [
              (0x8b6cff, 0, (inkR - 150 * 1.3) / inkR),
              (0x9678ff, 0.55 * rim, (inkR - 150 * 0.5) / inkR), (0x8b6cff, 0, 1),
            ], f.inkX, 1574, inkR))
      }
    }
    let starAlpha = f.starsA * f.glow
    if starAlpha > 0.01 {
      for star in A.sparkles {
        let tw = 0.75 + 0.25 * (0.5 + 0.5 * sin(2 * Double.pi * (star.k * f.t / M.LOOP + star.ph)))
        drawStar(c, star.x, star.y, star.s * tw, 0, starAlpha, false)
      }
    }
    if f.cars < 0.999 {
      let box = R.carsBox
      c.fill(
        rect(box.x, box.y - 4, box.w, box.h + 8),
        with: .shader(
          ShaderLibrary.GoldTicketRewardDissolve(
            .image(Image(uiImage: resources.cars)), .float4(box.x, box.y, box.w, box.h),
            .float(f.cars), .float(f.carsGlint))))
    }
    text(
      c, "thanks", A.thanks.x, A.thanks.y + f.thanks.dy, 0.5, 1, .color(Self.color(C.ink)),
      f.thanks.a)
    text(
      c, "callback", A.callback.x, A.callback.y + f.callback.dy, 0.5, 1,
      .color(Self.color(0x55545f)), f.callback.a)
    if f.divider > 0.01 {
      var div = c
      div.opacity *= f.divider
      div.fill(
        rect(A.divider.x0, A.divider.y - 0.5, A.divider.x1 - A.divider.x0, 1),
        with: linear(
          [(0x968c6e, 0, 0), (0x968c6e, 0.35, 0.5), (0x968c6e, 0, 1)], A.divider.x0, 0,
          A.divider.x1, 0))
    }
    drawAura(c, f)
    drawTicket(c, f, live)
    drawDust(c, f.t)
    drawSparks(c, f.t)
    for (index, star) in f.stars.enumerated() where star.a > 0.01 {
      drawStar(
        c, star.x - live.tiltX * 7 * f.bAmount, star.y - live.tiltY * 5 * f.bAmount, star.s,
        star.rot, star.a, B.sparkles[index].gold)
    }
    drawBenefits(c, f)
    drawButton(c, f, live)
    if f.close.a > 0.01 {
      var close = c
      close.translateBy(x: B.close.x, y: B.close.y)
      close.rotate(by: .radians(f.close.rot))
      close.scaleBy(x: f.close.s, y: f.close.s)
      let half = B.close.s / 2
      line(
        close, [CGPoint(x: -half, y: -half), CGPoint(x: half, y: half)],
        Self.color(0xffffff, f.close.a), 2)
      line(
        close, [CGPoint(x: -half, y: half), CGPoint(x: half, y: -half)],
        Self.color(0xffffff, f.close.a), 2)
    }
  }
}
