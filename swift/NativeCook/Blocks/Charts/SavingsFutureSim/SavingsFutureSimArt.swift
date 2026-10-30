import CoreText
import SwiftUI
import UIKit

// All coordinates are the source artwork's 736-pixel units, scaled by view width / 736.
final class SavingsFutureSimArt {
  typealias M = SavingsFutureSimMotion
  typealias F = M.Frame
  static let shared = SavingsFutureSimArt()
  private let fonts: [String: UIFont]
  private init() {
    var made: [String: UIFont] = [:]
    for weight in ["Regular", "Medium", "SemiBold", "Bold"] {
      if let url = Bundle.main.url(
        forResource: "SavingsFutureSim-Inter-\(weight)", withExtension: "ttf")
      {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        if let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider),
          let name = font.postScriptName
        {
          made[weight] = UIFont(name: name as String, size: 100)
        }
      }
    }
    fonts = made
  }
  func font(_ size: Double, _ weight: String) -> UIFont {
    (fonts[weight] ?? UIFont.systemFont(ofSize: 100)).withSize(size)
  }
  func width(_ text: String, _ size: Double, _ weight: String) -> Double {
    (text as NSString).size(withAttributes: [.font: font(size, weight)]).width
  }
  func color(_ hex: Int, _ alpha: Double = 1) -> UIColor {
    UIColor(
      red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, alpha: alpha)
  }
  func image(_ size: CGSize, _ frame: F) -> UIImage {
    let format = UIGraphicsImageRendererFormat()
    format.scale = UIScreen.main.scale
    format.opaque = true
    format.preferredRange = .standard
    return UIGraphicsImageRenderer(size: size, format: format).image { output in
      let c = output.cgContext
      let scale = size.width / 736
      c.scaleBy(x: scale, y: scale)
      draw(c, size.height / scale, frame)
    }
  }
  private func text(
    _ c: CGContext, _ value: String, _ x: Double, _ y: Double, _ size: Double, _ weight: String,
    _ hex: Int, _ alpha: Double = 1, _ anchor: Double = 0
  ) {
    let f = font(size, weight)
    let left = x - width(value, size, weight) * anchor
    c.saveGState()
    c.textMatrix = .identity
    c.translateBy(x: left, y: y + size * 0.36)
    c.scaleBy(x: 1, y: -1)
    c.textPosition = .zero
    let line = CTLineCreateWithAttributedString(
      NSAttributedString(
        string: value, attributes: [.font: f, .foregroundColor: color(hex, alpha), .kern: 0]
      ) as CFAttributedString)
    CTLineDraw(line, c)
    c.restoreGState()
  }
  private func rect(
    _ c: CGContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, _ hex: Int,
    _ alpha: Double = 1, _ stroke: Double = 0
  ) {
    let p = CGPath(
      roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: r, cornerHeight: r,
      transform: nil)
    c.addPath(p)
    if stroke > 0 {
      c.setStrokeColor(color(hex, alpha).cgColor)
      c.setLineWidth(stroke)
      c.strokePath()
    } else {
      c.setFillColor(color(hex, alpha).cgColor)
      c.fillPath()
    }
  }
  private func circle(
    _ c: CGContext, _ x: Double, _ y: Double, _ r: Double, _ hex: Int, _ alpha: Double = 1,
    _ stroke: Double = 0
  ) {
    c.addEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    if stroke > 0 {
      c.setStrokeColor(color(hex, alpha).cgColor)
      c.setLineWidth(stroke)
      c.strokePath()
    } else {
      c.setFillColor(color(hex, alpha).cgColor)
      c.fillPath()
    }
  }
  private func line(
    _ c: CGContext, _ x: Double, _ y: Double, _ xx: Double, _ yy: Double, _ hex: Int,
    _ width: Double, _ alpha: Double = 1
  ) {
    c.beginPath()
    c.move(to: CGPoint(x: x, y: y))
    c.addLine(to: CGPoint(x: xx, y: yy))
    c.setStrokeColor(color(hex, alpha).cgColor)
    c.setLineWidth(width)
    c.setLineCap(.round)
    c.strokePath()
  }
  private func gradient(
    _ c: CGContext, _ path: CGPath, _ colors: [UIColor], _ stops: [CGFloat], _ y0: Double,
    _ y1: Double, _ stroke: Double = 0
  ) {
    c.saveGState()
    c.addPath(path)
    if stroke > 0 {
      c.setLineWidth(stroke)
      c.replacePathWithStrokedPath()
    }
    c.clip()
    let gradient = CGGradient(
      colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray,
      locations: stops)!
    c.drawLinearGradient(
      gradient, start: CGPoint(x: 0, y: y0), end: CGPoint(x: 0, y: y1),
      options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    c.restoreGState()
  }
  static func rateFrac(_ rate: Double) -> Double { 0.1 + 0.125 * (rate - 4) }
  static func rateText(_ rate: Double) -> String {
    let r = (rate * 100).rounded() / 100
    let whole = floor(r)
    let decimal = Int(((r - whole) * 100).rounded())
    return "\(Int(whole)),\(decimal < 10 ? "0" : "")\(decimal)"
  }
  private func money(_ value: Double) -> String {
    let s = Array(String(Int(value.rounded())))
    return "$"
      + s.enumerated().map { index, char in
        String(char) + ((s.count - 1 - index) > 0 && (s.count - 1 - index) % 3 == 0 ? "," : "")
      }.joined()
  }
  private func point(_ u: Double, _ k: Double, _ rate: Double) -> CGPoint {
    let angle = Double.pi + Double.pi * u
    let v = (M.balance(u * 30, rate) - 10000) / (M.balance(30, 8) - 10000)
    let ku = M.smooth(k * 1.35 - u * 0.35)
    return CGPoint(
      x: M.mix(368 + 260 * cos(angle), 92 + u * 552, ku),
      y: M.mix(770 + 260 * sin(angle), 782 - 150 * v, ku))
  }
  private func shape(_ c: CGContext, _ f: F) -> CGPoint {
    let k = f.morph
    let split = M.mix(Self.rateFrac(f.rate), f.year / 30, M.smooth(k))
    let points = (0...72).map { point(Double($0) / 72, k, f.rate) }
    let width = M.mix(28, 7, M.smooth(k))
    let si = min(72, max(0, Int(floor(split * 72))))
    if k > 0.02 {
      let fill = CGMutablePath()
      fill.move(to: CGPoint(x: points[0].x, y: 782))
      for p in points { fill.addLine(to: p) }
      fill.addLine(to: CGPoint(x: points[72].x, y: 782))
      fill.closeSubpath()
      let sx = 92 + split * 552
      c.saveGState()
      c.clip(to: CGRect(x: 72, y: 572, width: sx - 72, height: 230))
      gradient(c, fill, [color(0xA39BF0, 0.38 * k), color(0xA39BF0, 0)], [0, 1], 632, 782)
      c.restoreGState()
      c.saveGState()
      c.clip(to: CGRect(x: sx, y: 572, width: 552, height: 230))
      c.addPath(fill)
      c.setFillColor(color(0xA39BF0, 0.06 * k).cgColor)
      c.fillPath()
      c.restoreGState()
    }
    let rest = CGMutablePath()
    rest.move(to: points[si])
    for i in si...72 { rest.addLine(to: points[i]) }
    c.setLineJoin(.round)
    c.setLineCap(.butt)
    if M.smooth(k) > 0.6 {
      c.saveGState()
      c.addPath(rest)
      c.setLineCap(.round)
      c.setLineDash(phase: 0, lengths: [12, 12])
      c.setLineWidth(width)
      c.setStrokeColor(color(0x6D6A95).cgColor)
      c.strokePath()
      c.restoreGState()
    } else {
      gradient(
        c, rest, [color(0x3A3A4A), color(0x3A3A4A, M.mix(0.15, 1, k))], [0.6, 1], 510, 780, width)
    }
    if k > 0.5 {
      let ma = M.smooth((k - 0.5) * 2)
      for year in [10.0, 20, 30] {
        let p = point(year / 30, k, f.rate)
        let pulse = exp(-pow(f.year - year, 2) / 1.2)
        circle(c, p.x, p.y, 6, f.year >= year ? 0xA39BF0 : 0x6D6A95, ma)
        if pulse > 0.02 { circle(c, p.x, p.y, 8 + 16 * pulse, 0xA39BF0, 0.7 * pulse * ma, 3) }
      }
    }
    let lead = CGMutablePath()
    lead.move(to: points[0])
    for i in 0...si { lead.addLine(to: points[i]) }
    let knob = point(split, k, f.rate)
    lead.addLine(to: knob)
    c.setLineCap(.butt)
    gradient(
      c, lead, [color(0x7A70E0, M.mix(0, 1, k)), color(0xA39BF0)], [0, 0.6], 780, 510, width)
    if k > 0.5 {
      var y = knob.y + 26
      while y < 782 {
        line(c, knob.x, y, knob.x, min(782, y + 7), 0xA39BF0, 2, 0.6 * M.smooth((k - 0.5) * 2))
        y += 14
      }
    }
    let kr = 22 * (1 + 0.06 * sin(2 * Double.pi * 3 * f.amb / M.LOOP)) * (1 + 0.2 * f.knob)
    circle(c, knob.x, knob.y, kr + 7, 0x232331)
    circle(c, knob.x, knob.y, kr, 0xA39BF0)
    return knob
  }
  private func value(_ c: CGContext, _ f: F, _ knob: CGPoint) {
    let k = M.smooth(f.morph * 1.3 - 0.15)
    if k < 0.99 {
      let rt = Self.rateText(f.rate)
      let vw = width(rt, 104, "Bold")
      let pw = width("%", 62, "Bold")
      let x = 368 - (vw + pw + 6) / 2
      text(c, rt, x, 680 - 12 * k, 104, "Bold", 0xFFFFFF, 1 - k)
      text(c, "%", x + vw + 6, 692 - 12 * k, 62, "Bold", 0xFFFFFF, 1 - k)
      text(c, "Your Savings Rate", 368, 760 - 12 * k, 30, "Regular", 0x9B9BAA, 1 - k, 0.5)
    }
    if k > 0.01 {
      let yi = max(1, Int(floor(f.year)))
      let roll = M.smooth((f.year - Double(yi)) / 0.35)
      let now = money(M.balance(Double(yi), f.rate))
      let prev = money(M.balance(Double(max(0, yi - 1)), f.rate))
      let y = 548 + 12 * (1 - k)
      let w = width(now, 104, "Bold") * 0.82
      var x = 368 - w / 2
      c.saveGState()
      c.translateBy(x: x, y: y)
      c.scaleBy(x: 0.82, y: 0.82)
      c.translateBy(x: -x, y: -y)
      c.clip(to: CGRect(x: x - 10, y: y - 70, width: w / 0.82 + 20, height: 140))
      if prev.count != now.count || f.year < 1.02 {
        text(c, now, x, y, 104, "Bold", 0xFFFFFF, k)
      } else {
        for (a, b) in zip(prev, now) {
          let char = String(b)
          if a == b || roll >= 1 {
            text(c, char, x, y, 104, "Bold", 0xFFFFFF, k)
          } else {
            text(c, String(a), x, y - 90 * roll, 104, "Bold", 0xFFFFFF, k * (1 - roll))
            text(c, char, x, y + 90 * (1 - roll), 104, "Bold", 0xFFFFFF, k * roll)
          }
          x += width(char, 104, "Bold")
        }
      }
      c.restoreGState()
      text(
        c, "in \(yi) \(yi == 1 ? "year" : "years")", 368, 604 + 12 * (1 - k), 30, "Regular",
        0x9B9BAA, k, 0.5)
      for year in [1, 10, 20, 30] {
        text(c, "\(year)y", 92 + Double(year) / 30 * 552, 808, 21, "Medium", 0x9B9BAA, k * 0.8, 0.5)
      }
      line(c, 92, 782, 644, 782, 0x383846, 2, k)
      rect(c, knob.x - 52, knob.y - 73, 104, 42, 21, 0x2B2C3D, k)
      text(c, "Year \(yi)", knob.x, knob.y - 52, 22, "SemiBold", 0xFFFFFF, k, 0.5)
    }
  }
  private func share(_ c: CGContext, _ index: Int, _ x: Double) {
    let y = 1490.0
    circle(c, x, y, 52, index == 0 ? 0x5AD27A : index == 2 ? 0x4DA8F0 : 0x2B2C3D)
    switch index {
    case 0:
      rect(c, x - 24, y - 20, 48, 34, 16, 0xFFFFFF)
      let p = CGMutablePath()
      p.move(to: CGPoint(x: x - 12, y: y + 12))
      p.addLine(to: CGPoint(x: x - 18, y: y + 24))
      p.addLine(to: CGPoint(x: x - 2, y: y + 14))
      p.closeSubpath()
      c.addPath(p)
      c.setFillColor(color(0xFFFFFF).cgColor)
      c.fillPath()
    case 1:
      line(c, x - 8, y + 8, x + 8, y - 8, 0xFFFFFF, 5)
      for (cx, cy, rx, ry) in [(x - 15, y + 7, x - 30, y - 1), (x + 15, y - 7, x, y - 15)] {
        c.saveGState()
        c.translateBy(x: cx, y: cy)
        c.rotate(by: -Double.pi / 4)
        c.translateBy(x: -cx, y: -cy)
        rect(c, rx, ry, 30, 16, 8, 0xFFFFFF, 1, 5)
        c.restoreGState()
      }
    case 2:
      let p = CGMutablePath()
      p.move(to: CGPoint(x: x - 24, y: y))
      p.addLine(to: CGPoint(x: x + 24, y: y - 20))
      p.addLine(to: CGPoint(x: x + 10, y: y + 22))
      p.addLine(to: CGPoint(x: x + 2, y: y + 6))
      p.closeSubpath()
      c.addPath(p)
      c.setFillColor(color(0xFFFFFF).cgColor)
      c.fillPath()
    default: for i in -1...1 { circle(c, x + Double(i) * 15, y, 5, 0xC9C9D6) }
    }
  }
  private func draw(_ c: CGContext, _ height: Double, _ f: F) {
    gradient(
      c, CGPath(rect: CGRect(x: 0, y: 0, width: 736, height: height), transform: nil),
      [color(0x17182C), color(0x0E0F1B)], [0, 1], 0, height)
    for x in [84.0, 652] { circle(c, x, 161, 40, 0x2B2C3D) }
    for i in -1...1 { circle(c, 84 + Double(i) * 12, 161, 4, 0xFFFFFF) }
    circle(c, 652, 161, 20, 0xFFFFFF, 1, 3.5)
    text(c, "?", 652, 161, 22, "SemiBold", 0xFFFFFF, 1, 0.5)
    let jx = 368 - (width("Joined Waitlist", 30, "SemiBold") + 54) / 2
    circle(c, jx + 18, 257, 18, 0xA39BF0)
    line(c, jx + 10, 257, jx + 16, 263, 0xFFFFFF, 3.4)
    line(c, jx + 16, 263, jx + 26, 251, 0xFFFFFF, 3.4)
    text(c, "Joined Waitlist", jx + 54, 257, 30, "SemiBold", 0xFFFFFF)
    text(c, "You’re on your way to the", 368, 328, 40, "SemiBold", 0xFFFFFF, 1, 0.5)
    text(c, "future of Savings.", 368, 383, 40, "SemiBold", 0xFFFFFF, 1, 0.5)
    rect(c, 38, 455, 660, 785, 56, 0x232331)
    let knob = shape(c, f)
    value(c, f, knob)
    line(c, 80, 833, 656, 833, 0x383846, 2)
    text(c, "See your savings grow.", 81, 900, 32, "SemiBold", 0xFFFFFF)
    for (i, s) in [
      "Explore what \(Self.rateText(f.rate))% could look like over",
      "time, and see how compounding can", "help your savings grow.",
    ].enumerated() {
      text(c, s, 81, [955.0, 999, 1043][i], 29, "Regular", 0x8E8E9E)
    }
    c.saveGState()
    c.translateBy(x: 368, y: 1150.5)
    let scale = 1 - 0.04 * f.press
    c.scaleBy(x: scale, y: scale)
    rect(c, -288, -45.5, 576, 91, 45.5, 0x3A3A4A)
    let ok = M.smooth(f.morph * 2 - 0.6)
    for (i, label) in ["Simulate My Future", "Back to My Rate"].enumerated() {
      let a = i == 0 ? 1 - ok : ok
      if a < 0.01 { continue }
      let x = -(width(label, 32, "SemiBold") + 46) / 2
      let dy = i == 0 ? -10 * ok : 10 * (1 - ok)
      for b in 0..<3 {
        rect(
          c, x + Double(b) * 10, dy + 14 - Double(b + 1) * 9, 5, Double(b + 1) * 9, 2.5, 0xFFFFFF, a
        )
      }
      text(c, label, x + 46, dy, 32, "SemiBold", 0xFFFFFF, a)
    }
    c.restoreGState()
    rect(c, 38, 1278, 660, 340, 56, 0x232331)
    text(c, "Skip the Waitlist", 74, 1338, 31, "Medium", 0xFFFFFF)
    text(c, "Learn More", 662, 1338, 31, "Medium", 0xA39BF0, 1, 1)
    line(c, 74, 1390, 662, 1390, 0x383846, 2)
    for (i, x) in [148.0, 295, 441, 588].enumerated() { share(c, i, x) }
  }
}
