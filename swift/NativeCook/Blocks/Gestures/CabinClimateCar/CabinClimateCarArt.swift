import CoreText
import SwiftUI
import UIKit

// All drawing coordinates are the original 720-pixel pin, scaled once at the canvas boundary.
final class CabinClimateCarArt {
  typealias C = CabinClimateCarTheme
  typealias M = CabinClimateCarMotion
  private var context: GraphicsContext
  private var stack: [GraphicsContext] = []
  private static let fontsRegistered: Void = {
    for weight in ["Regular", "Medium", "SemiBold", "Bold"] {
      if let url = Bundle.main.url(
        forResource: "CabinClimateCar-Inter-\(weight)", withExtension: "ttf")
      {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
  private static let car: UIImage? = {
    guard let url = Bundle.main.url(forResource: "CabinClimateCar-car", withExtension: "png") else {
      return nil
    }
    return UIImage(contentsOfFile: url.path)?.preparingForDisplay()
  }()
  init(_ context: GraphicsContext) {
    self.context = context
    _ = Self.fontsRegistered
  }
  private func save() { stack.append(context) }
  private func restore() { context = stack.removeLast() }
  private func translate(_ x: Double, _ y: Double) { context.translateBy(x: x, y: y) }
  private func scale(_ x: Double) { context.scaleBy(x: x, y: x) }
  private func rotate(_ degrees: Double) { context.rotate(by: .degrees(degrees)) }
  private func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double = 0) -> Path {
    Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r)
  }
  private func fill(_ p: Path, _ color: Color) { context.fill(p, with: .color(color)) }
  private func rr(
    _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, _ rgb: Int, _ a: Double = 1
  ) {
    fill(rect(x, y, w, h, r), C.color(rgb, a))
  }
  private func oval(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ color: Color) {
    fill(Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h)), color)
  }
  private func line(
    _ x: Double, _ y: Double, _ xx: Double, _ yy: Double, _ rgb: Int, _ width: Double
  ) {
    var p = Path()
    p.move(to: CGPoint(x: x, y: y))
    p.addLine(to: CGPoint(x: xx, y: yy))
    stroke(p, C.color(rgb), width)
  }
  private func stroke(_ p: Path, _ color: Color, _ width: Double) {
    context.stroke(
      p, with: .color(color),
      style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
  }
  private func font(_ size: Double, _ weight: String) -> UIFont {
    // The bundled Regular/Bold faces use Inter24pt PostScript names, unlike Medium/SemiBold.
    let family = weight == "Regular" || weight == "Bold" ? "Inter24pt" : "Inter"
    return UIFont(name: "\(family)-\(weight)", size: size) ?? UIFont.systemFont(ofSize: size)
  }
  private func textWidth(_ value: String, _ size: Double, _ weight: String) -> Double {
    let font = font(size, weight)
    return (value as NSString).size(withAttributes: [.font: font]).width
  }
  @discardableResult private func text(
    _ value: String, _ x: Double, _ cy: Double, _ size: Double,
    _ weight: String, _ rgb: Int, _ ax: Double = 0, _ alpha: Double = 1
  ) -> Double {
    let font = font(size, weight)
    let width = textWidth(value, size, weight)
    var resolved = context.resolve(
      Text(value).font(Font(font)).foregroundColor(C.color(rgb, alpha)))
    resolved.shading = .color(C.color(rgb, alpha))
    let bounds = CGSize(width: 10000, height: 10000)
    context.draw(
      resolved,
      at: CGPoint(x: x - width * ax, y: cy + size * 0.36 - resolved.firstBaseline(in: bounds)),
      anchor: .topLeading)
    return width
  }
  private func linear(
    _ path: Path, _ x: Double, _ y: Double, _ xx: Double, _ yy: Double, _ colors: [Color]
  ) {
    context.fill(
      path,
      with: .linearGradient(
        Gradient(colors: colors), startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: xx, y: yy)))
  }
  private func radial(_ path: Path, _ x: Double, _ y: Double, _ radius: Double, _ colors: [Color]) {
    context.fill(
      path,
      with: .radialGradient(
        Gradient(colors: colors), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: radius))
  }
  private func card(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ press: Double = 0) {
    save()
    translate(x + w / 2, y + h / 2)
    scale(1 - 0.04 * press)
    translate(-x - w / 2, -y - h / 2)
    context.drawLayer { layer in
      layer.addFilter(.blur(radius: 14))
      layer.fill(rect(x + 4, y + 10, w - 8, h, 40), with: .color(C.color(0x8A93A8, 0.12)))
    }
    rr(x, y, w, h, 40, C.card)
  }
  private let flows: [[[Double]]] = [
    [[455, 268], [400, 236], [320, 238], [265, 268], [235, 300]],
    [[470, 285], [410, 262], [340, 262], [280, 286], [250, 318]],
    [[445, 300], [395, 288], [330, 290], [285, 308], [262, 330]],
  ]
  private func catmull(_ points: [[Double]], _ u: Double) -> CGPoint {
    let n = points.count - 1
    let position = min(Double(n) - 1e-4, max(0, u * Double(n)))
    let i = Int(floor(position))
    let t = position - Double(i)
    let t2 = t * t
    let t3 = t * t * t
    let p0 = points[max(0, i - 1)]
    let p1 = points[i]
    let p2 = points[i + 1]
    let p3 = points[min(n, i + 2)]
    func q(_ d: Int) -> Double {
      let a = p0[d]
      let b = p1[d]
      let c = p2[d]
      let d = p3[d]
      return 0.5
        * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
    }
    return CGPoint(x: q(0), y: q(1))
  }
  private func drawCar(_ f: M.Frame) {
    if let image = Self.car {
      let height = 540 * image.size.height / image.size.width
      context.draw(
        Image(uiImage: image),
        in: CGRect(x: 362 - 270, y: 318 - height / 2, width: 540, height: height))
    }
    let gx = 362.0 + (350 - 365) * 540.0 / 660
    let gy = 318.0 + (270 - 318) * 540.0 / 660
    radial(
      Path(ellipseIn: CGRect(x: gx - 115, y: gy - 68, width: 230, height: 136)), gx, gy, 115,
      [C.air(f.heat, 0.32), C.air(f.heat, 0)])
    let strength = 0.55 + 0.45 * M.clamp01(abs(f.heat - f.temp) + abs(f.temp - 70) / 6)
    for k in 0..<3 {
      for j in 0..<4 {
        let u0 = (f.amb / M.LOOP * 6 + Double(j) / 4 + Double(k) * 0.13).truncatingRemainder(
          dividingBy: 1)
        var path = Path()
        var count = 0
        for sample in 0...8 {
          let u = u0 - 0.16 + Double(sample) / 8 * 0.16
          if u < 0 { continue }
          let p = catmull(flows[k], u)
          let q = CGPoint(x: 362 + (p.x - 365) * 540 / 660, y: 318 + (p.y - 318) * 540 / 660)
          if count == 0 { path.move(to: q) } else { path.addLine(to: q) }
          count += 1
        }
        if count > 1 { stroke(path, C.air(f.heat, sin(Double.pi * u0) * 0.75 * strength), 4) }
      }
    }
  }
  private func drawPill(_ f: M.Frame) {
    save()
    translate(360, 181)
    scale(f.card == 5 ? 1 - 0.04 * f.cardK : 1)
    rr(-85, -29, 170, 58, 29, C.pill)
    stroke(Path(ellipseIn: CGRect(x: -74, y: -23, width: 46, height: 46)), C.color(C.teal), 3)
    text("RL", -51, 0, 30, "SemiBold", C.teal, 0.5)
    text("Edit", -15, 0, 30, "SemiBold", 0xFFFFFF)
    line(55, -7, 62, 0, 0xFFFFFF, 2.8)
    line(55, 7, 62, 0, 0xFFFFFF, 2.8)
    restore()
  }
  private func drawTemp(_ f: M.Frame) {
    card(30, 580, 322, 490)
    save()
    context.clip(to: rect(30, 580, 322, 490, 40))
    linear(
      rect(30, 580, 322, 490), 0, 580 + 490 * 0.45, 0, 1070,
      [C.air(f.heat, 0), C.air(f.heat, 0.16)])
    restore()
    let cx = 191.0
    for (sign, y, k) in [("+", 670.0, f.plusK), ("−", 978.0, f.minusK)] {
      if k > 0.01 {
        let radius = 46 * (0.8 + 0.2 * k)
        oval(cx - radius, y - radius, radius * 2, radius * 2, C.color(0xEEF1F6, k))
      }
      save()
      translate(cx, y)
      scale(1 - 0.04 * k)
      text(sign, 0, -4, 54, "Regular", C.ink, 0.5)
      restore()
    }
    let roll = M.smooth(f.tempAt / M.ROLL)
    let a = Array(String(Int(f.from)))
    let b = Array(String(Int(f.temp)))
    let width =
      textWidth(String(Int(f.temp)), 116, "SemiBold") + textWidth("°", 116, "SemiBold") * 0.85
    var px = cx - width / 2
    let up = f.temp > f.from ? 1.0 : -1.0
    save()
    context.clip(to: rect(30, 745, 322, 160))
    for i in b.indices {
      let old = a.count == b.count ? String(a[i]) : ""
      let next = String(b[i])
      if roll >= 1 || old == next {
        text(next, px, 825, 116, "SemiBold", C.ink)
      } else {
        text(old, px, 825 - up * 120 * roll, 116, "SemiBold", C.ink, 0, 1 - roll)
        text(next, px, 825 + up * 120 * (1 - roll), 116, "SemiBold", C.ink, 0, roll)
      }
      px += textWidth(next, 116, "SemiBold")
    }
    text("°", px - 4, 825, 116, "SemiBold", C.ink)
    restore()
    restore()
  }
  private func seat(_ x: Double, _ y: Double) {
    save()
    translate(x, y + 16)
    scale(1.1)
    translate(-x, -y - 16)
    var p = Path()
    p.move(to: CGPoint(x: x - 30, y: y - 64))
    p.addCurve(
      to: CGPoint(x: x - 42, y: y - 44), control1: CGPoint(x: x - 40, y: y - 66),
      control2: CGPoint(x: x - 46, y: y - 56))
    p.addLine(to: CGPoint(x: x - 30, y: y + 4))
    p.addCurve(
      to: CGPoint(x: x - 8, y: y + 16), control1: CGPoint(x: x - 26, y: y + 14),
      control2: CGPoint(x: x - 18, y: y + 16))
    p.addLine(to: CGPoint(x: x + 36, y: y + 16))
    p.addCurve(
      to: CGPoint(x: x + 38, y: y - 2), control1: CGPoint(x: x + 46, y: y + 16),
      control2: CGPoint(x: x + 48, y: y + 2))
    p.addLine(to: CGPoint(x: x - 6, y: y - 6))
    p.addLine(to: CGPoint(x: x - 18, y: y - 56))
    p.addCurve(
      to: CGPoint(x: x - 30, y: y - 64), control1: CGPoint(x: x - 20, y: y - 62),
      control2: CGPoint(x: x - 24, y: y - 64))
    p.closeSubpath()
    linear(p, x - 40, y - 60, x + 40, y + 16, [C.color(C.seatHi), C.color(C.seat)])
    rr(x - 36, y - 80, 14, 18, 6, C.seat)
    oval(x - 46, y + 30, 92, 8, C.color(0xAEB4C2, 0.35))
    restore()
  }
  private func drawLegroom(_ f: M.Frame) {
    card(369, 580, 321, 235, f.card == 3 ? f.cardK : 0)
    let bars = [M.mix(1, 0.6, f.leg), M.mix(1, 1.45, f.leg)]
    for i in 0..<2 {
      let sx = [445.0, 559.0][i] + (i == 0 ? -16 : 16) * f.leg * 0.4
      seat(sx, 662)
      // Set warm: the seats heat up with a glow on the cushion and back.
      let warm = M.clamp01((f.heat - 71) / 3)
      if warm > 0.01 {
        radial(
          Path(ellipseIn: CGRect(x: sx - 26, y: 648, width: 80, height: 40)), sx + 14, 668, 40,
          [C.color(0xFF5A3C, 0.55 * warm), C.color(0xFF5A3C, 0)])
        radial(
          Path(ellipseIn: CGRect(x: sx - 54, y: 592, width: 48, height: 72)), sx - 30, 628, 30,
          [C.color(0xFF5A3C, 0.45 * warm), C.color(0xFF5A3C, 0)])
      }
      let bh = 40 * bars[i]
      rr(sx + 59, 682 - bh, 13, bh, 6.5, C.green)
    }
    let k = M.smooth((f.legAt - 0.05) / 0.28)
    let labels = ["Distributed legroom", "Extra rear legroom"]
    let on = f.legOn ? 1 : 0
    if k >= 1 {
      text(labels[on], 529.5, 765, 28, "Medium", C.mute, 0.5)
    } else {
      text(labels[1 - on], 529.5, 765 - 10 * k, 28, "Medium", C.mute, 0.5, 1 - k)
      text(labels[on], 529.5, 765 + 10 * (1 - k), 28, "Medium", C.mute, 0.5, k)
    }
    restore()
  }
  private func drawFan(_ f: M.Frame) {
    card(369, 833, 321, 237, f.card == 4 ? f.cardK : 0)
    save()
    translate(529.5, 918)
    rotate(360 * [6.0, 3, 12][f.fan] * f.amb / M.LOOP)
    for i in 0..<4 {
      save()
      rotate(Double(i) * 90)
      var p = Path()
      p.move(to: CGPoint(x: 0, y: -4))
      p.addCurve(
        to: CGPoint(x: 16, y: -30), control1: CGPoint(x: -4, y: -16),
        control2: CGPoint(x: 4, y: -32))
      p.addCurve(
        to: CGPoint(x: 8, y: -8), control1: CGPoint(x: 24, y: -28), control2: CGPoint(x: 22, y: -16)
      )
      stroke(p, C.color(C.ink), 3.6)
      restore()
    }
    restore()
    let k = M.smooth(f.fanAt / 0.25)
    text(["Auto", "Low", "High"][f.fan], 529.5, 1010 + 8 * (1 - k), 28, "Medium", C.mute, 0.5, k)
    restore()
  }
  private func drawBelow() {
    text("Preferences", 73, 1131, 30, "Medium", C.label)
    card(30, 1187, 660, 113)
    text("In-car announcements", 74, 1243.5, 29, "Regular", C.ink)
    text("All", 646, 1243.5, 29, "Regular", C.blue, 1)
    restore()
    card(30, 1312, 660, 230)
    let width = textWidth("Beta feature", 27, "Medium")
    rr(74, 1346, width + 26, 50, 10, C.chip)
    text("Beta feature", 87, 1371, 27, "Medium", C.ink)
    text("Quiet mode on longer rides", 74, 1440, 29, "Regular", C.ink, 0, 0.5)
    restore()
  }
  private func tabIcon(_ i: Int, _ x: Double, _ y: Double) {
    var p = Path()
    if i == 0 {
      stroke(
        Path(ellipseIn: CGRect(x: x - 16, y: y - 15, width: 12, height: 12)), C.color(C.ink), 3.2)
      stroke(
        Path(ellipseIn: CGRect(x: x + 4, y: y + 3, width: 12, height: 12)), C.color(C.ink), 3.2)
      p.move(to: CGPoint(x: x - 4, y: y - 9))
      p.addLine(to: CGPoint(x: x + 10, y: y - 9))
      p.addQuadCurve(to: CGPoint(x: x + 18, y: y - 1), control: CGPoint(x: x + 18, y: y - 9))
      p.addQuadCurve(to: CGPoint(x: x + 4, y: y + 4), control: CGPoint(x: x + 18, y: y + 4))
      p.addLine(to: CGPoint(x: x - 10, y: y + 4))
      stroke(p, C.color(C.ink), 3.2)
    } else if i == 1 {
      rr(x - 18, y - 8, 36, 20, 6, C.ink)
      p.move(to: CGPoint(x: x - 13, y: y - 8))
      p.addLine(to: CGPoint(x: x - 8, y: y - 18))
      p.addLine(to: CGPoint(x: x + 8, y: y - 18))
      p.addLine(to: CGPoint(x: x + 13, y: y - 8))
      p.closeSubpath()
      fill(p, C.color(C.ink))
      oval(x - 12.2, y - 1.2, 6.4, 6.4, .white)
      oval(x + 5.8, y - 1.2, 6.4, 6.4, .white)
      rr(x - 16, y + 10, 7, 7, 2, C.ink)
      rr(x + 9, y + 10, 7, 7, 2, C.ink)
    } else if i == 2 {
      stroke(
        Path(ellipseIn: CGRect(x: x - 16, y: y - 18, width: 32, height: 32)), C.color(C.ink), 3.2)
      line(x - 7, y - 6, x + 7, y - 6, C.ink, 2.6)
      line(x - 7, y + 1, x + 4, y + 1, C.ink, 2.6)
    } else {
      stroke(
        Path(ellipseIn: CGRect(x: x - 8, y: y - 17, width: 16, height: 16)), C.color(C.ink), 3.2)
      p.move(to: CGPoint(x: x - 16, y: y + 16))
      p.addCurve(
        to: CGPoint(x: x + 16, y: y + 16), control1: CGPoint(x: x - 14, y: y + 2),
        control2: CGPoint(x: x + 14, y: y + 2))
      stroke(p, C.color(C.ink), 3.2)
    }
  }
  private func drawTabs() {
    context.drawLayer { layer in
      layer.addFilter(.blur(radius: 18))
      layer.fill(rect(46, 1428, 628, 106, 53), with: .color(C.color(0x8A93A8, 0.16)))
    }
    rr(40, 1418, 640, 106, 53, 0xFFFFFF, 0.92)
    rr(199, 1426, 168, 90, 45, C.tabSel)
    for (i, x) in [130.0, 283, 436, 589].enumerated() {
      tabIcon(i, x, 1456)
      text(["My trip", "My car", "Help", "Account"][i], x, 1494, 21, "Medium", C.ink, 0.5)
    }
  }
  func draw(_ size: CGSize, _ f: M.Frame) {
    save()
    scale(size.width / 720)
    let height = size.height / (size.width / 720)
    linear(rect(0, 0, 720, height), 0, 0, 0, 560, [C.color(C.bgTop), C.color(C.bg)])
    radial(rect(0, 60, 720, 520), 362, 338, 300, [C.air(f.heat, 0.14), C.air(f.heat, 0)])
    drawPill(f)
    drawCar(f)
    text("Cabin controls", 73, 532, 30, "Medium", C.label)
    drawTemp(f)
    drawLegroom(f)
    drawFan(f)
    drawBelow()
    drawTabs()
    restore()
  }
}
