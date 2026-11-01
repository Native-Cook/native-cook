import SwiftUI
import UIKit

struct StepParadeArt {
  typealias M = StepParadeMotion
  let width, height, top, bottom: Double
  let r = StepParadeResources.shared
  var g: CGRect { .init(x: 16, y: top + 268, width: width - 32, height: 252) }
  func color(_ hex: UInt32, _ a: Double = 1) -> Color { Color(hex: hex, opacity: a) }
  func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double = 0) -> Path {
    Path(
      roundedRect: .init(x: x, y: y, width: w, height: h), cornerRadius: radius, style: .circular)
  }
  func circle(_ x: Double, _ y: Double, _ rad: Double) -> Path {
    Path(ellipseIn: .init(x: x - rad, y: y - rad, width: rad * 2, height: rad * 2))
  }
  func fill(_ c: GraphicsContext, _ p: Path, _ hex: UInt32, _ a: Double = 1) {
    c.fill(p, with: .color(color(hex, a)))
  }
  func line(
    _ c: GraphicsContext, _ x: Double, _ y: Double, _ x2: Double, _ y2: Double, _ hex: UInt32,
    _ w: Double, _ a: Double = 1
  ) {
    var p = Path()
    p.move(to: .init(x: x, y: y))
    p.addLine(to: .init(x: x2, y: y2))
    c.stroke(p, with: .color(color(hex, a)), style: .init(lineWidth: w, lineCap: .round))
  }
  func text(
    _ c: GraphicsContext, _ str: String, _ x: Double, _ y: Double, _ size: Double,
    _ weight: String = "Regular", _ hex: UInt32 = 0x1B1E26, _ anchor: Double = 0,
    _ lineHeight: Double? = nil, _ tabular: Bool = false, _ pixelSnapX: Bool = false
  ) {
    let font = r.font(weight, size)
    let value = Text(str).font(Font(font)).foregroundColor(color(hex))
    let resolved = c.resolve(tabular ? value.monospacedDigit() : value)
    let measured = resolved.measure(in: .init(width: 2000, height: 200))
    let lh = lineHeight ?? r.pixelCeil(font.lineHeight)
    let left = x - measured.width * anchor
    let drawX = pixelSnapX ? floor(left * UIScreen.main.scale) / UIScreen.main.scale : left
    c.draw(
      resolved, at: .init(x: drawX, y: y + (lh - measured.height) / 2),
      anchor: .topLeading)
  }
  func icon(
    _ c: GraphicsContext, _ glyph: UInt32, _ x: Double, _ y: Double, _ size: Double,
    _ hex: UInt32 = 0x1B1E26
  ) {
    text(c, String(UnicodeScalar(glyph)!), x, y, size, "Ionicons", hex)
  }
  /// Yoga rounds native avatar subviews to physical pixels before the circular clip.
  func avatar(
    _ context: GraphicsContext, _ i: Int, _ x: Double, _ y: Double, _ size: Double,
    _ ring: Bool = false
  ) {
    let friend = M.friends[i]
    let scale = UIScreen.main.scale
    func frame(_ x: Double, _ y: Double, _ width: Double, _ height: Double) -> CGRect {
      let left = (x * scale).rounded() / scale
      let top = (y * scale).rounded() / scale
      let right = ((x + width) * scale).rounded() / scale
      let bottom = ((y + height) * scale).rounded() / scale
      return CGRect(x: left, y: top, width: right - left, height: bottom - top)
    }
    var c = context
    let bounds = frame(x, y, size, size)
    let outline = Path(ellipseIn: bounds)
    c.clip(to: outline)
    fill(c, outline, friend.coat, 0.2)
    let border = ring ? 2.5 : 0.0
    let coat = frame(x + size * 0.11, y + size * 0.68 - border, size * 0.78, size * 0.5)
    fill(c, Path(roundedRect: coat, cornerRadius: size * 0.25, style: .circular), friend.coat)
    let head = frame(x + size * 0.27, y + border + size * 0.26, size * 0.46, size * 0.46)
    fill(c, Path(roundedRect: head, cornerRadius: size * 0.23, style: .circular), friend.skin)
    let hatFrame = frame(x + size * 0.25, y + border + size * 0.14, size * 0.5, size * 0.24)
    let radius = min(hatFrame.height, hatFrame.width / 2)
    var hat = Path()
    hat.move(to: .init(x: hatFrame.minX, y: hatFrame.maxY))
    hat.addArc(
      center: .init(x: hatFrame.minX + radius, y: hatFrame.minY + radius), radius: radius,
      startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
    hat.addLine(to: .init(x: hatFrame.maxX - radius, y: hatFrame.minY))
    hat.addArc(
      center: .init(x: hatFrame.maxX - radius, y: hatFrame.minY + radius), radius: radius,
      startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false)
    hat.closeSubpath()
    fill(c, hat, friend.hat)
    if ring {
      c.stroke(
        circle(bounds.midX, bounds.midY, (bounds.width - 2.5) / 2),
        with: .color(color(0x1B1E26)), lineWidth: 2.5)
    }
  }
  func walker(
    _ c: GraphicsContext, _ x: Double, _ y: Double, _ s: Double, _ ph: Double, _ i: Int, _ sel: Int
  ) {
    let f = M.friends[i]
    let sw = sin(ph)
    let bob = abs(cos(ph)) * 2 * s
    fill(
      c, Path(ellipseIn: .init(x: x - 13 * s, y: y - 3 * s, width: 26 * s, height: 6 * s)),
      0x5C7A99, 0.18)
    let hip = y - 17 * s - bob
    line(c, x, hip, x + sin(sw * 0.55) * 14 * s, hip + cos(sw * 0.55) * 15 * s, 0x2B2F3A, 6.5 * s)
    line(c, x, hip, x - sin(sw * 0.55) * 14 * s, hip + cos(sw * 0.55) * 15 * s, 0x3A3F4C, 6.5 * s)
    let sh = hip - 17 * s
    line(
      c, x - 2 * s, sh + 3 * s, x - 2 * s + sin(sw * 0.6) * 11 * s,
      sh + 3 * s + cos(sw * 0.6) * 12 * s, f.coat, 6 * s, 0.8)
    fill(c, rect(x - 11 * s, sh - 4 * s, 22 * s, 24 * s, 10 * s), f.coat)
    line(c, x - 9 * s, sh + 8 * s, x + 9 * s, sh + 8 * s, 0xFFFFFF, 1.4 * s, 0.35)
    line(
      c, x + 2 * s, sh + 3 * s, x + 2 * s - sin(sw * 0.6) * 11 * s,
      sh + 3 * s + cos(sw * 0.6) * 12 * s, f.coat, 6 * s)
    let hy = sh - 12 * s
    fill(c, circle(x + s, hy, 10 * s), f.skin)
    fill(c, circle(x + 5 * s, hy + s, 1.4 * s), 0x1B1E26)
    fill(c, circle(x + 9 * s, hy + s, 1.4 * s), 0x1B1E26)
    var hat = Path()
    hat.addArc(
      center: .init(x: x + s, y: hy - 2 * s), radius: 10 * s, startAngle: .degrees(180),
      endAngle: .degrees(360), clockwise: false)
    hat.closeSubpath()
    fill(c, hat, f.hat)
    fill(c, rect(x - 10 * s, hy - 4 * s, 22 * s, 5 * s, 2.5 * s), f.hat)
    fill(c, circle(x + s, hy - 13 * s, 3.4 * s), 0xFFFFFF)
    if sel == i {
      c.stroke(circle(x, y - 1, 16 * s), with: .color(color(0x1B1E26, 0.25)), lineWidth: 1.5)
    }
  }
  func sky(_ h: Double, _ isTop: Bool) -> Color {
    let hs = [6.0, 9, 13, 17.5, 19.5, 22]
    let cols: [UInt32] =
      isTop
      ? [0xFFB9A6, 0xBFDBFF, 0xA9CEFF, 0x9EC2F7, 0xFF9F7A, 0x1F2A57]
      : [0xFFE9DD, 0xEFF6FF, 0xEAF4FF, 0xF2EADF, 0xFFD9BF, 0x4B5B95]
    var i = 0
    while i < 4 && h > hs[i + 1] { i += 1 }
    let k = M.clamp((h - hs[i]) / (hs[i + 1] - hs[i]))
    func ch(_ shift: UInt32) -> Double {
      M.mix(Double((cols[i] >> shift) & 255), Double((cols[i + 1] >> shift) & 255), k).rounded()
        / 255
    }
    return Color(.sRGB, red: ch(16), green: ch(8), blue: ch(0), opacity: 1)
  }
  func scene(_ context: GraphicsContext, _ h: Double, _ sel: Int) {
    var c = context
    c.clip(to: r.path("clip-\(g)") { rect(g.minX, g.minY, g.width, g.height, 28) })
    let sceneBounds = r.path("scene-\(g)") { Path(g) }
    c.fill(
      sceneBounds,
      with: .linearGradient(
        .init(colors: [sky(h, true), sky(h, false)]), startPoint: .init(x: 0, y: g.minY),
        endPoint: .init(x: 0, y: g.minY + g.height * 0.5)))
    let night = M.clamp((h - 19) / 1.5)
    let arc = M.clamp((h - 6) / 16)
    let sx = g.minX + 30 + arc * (g.width - 60)
    let sy = g.minY + g.height * 0.36 - sin(arc * .pi) * g.height * 0.22
    fill(c, circle(sx, sy, 15), 0xFFD36B, 1 - night)
    fill(c, circle(sx, sy, 24), 0xFFD36B, 0.18 * (1 - night))
    if night > 0 {
      fill(c, circle(sx, sy, 12), 0xF4F1E1, night)
      fill(c, circle(sx + 5, sy - 3, 10), 0x1F2A57, night)
      for k in 0..<14 {
        fill(
          c,
          circle(
            g.minX + Double(k * 97 % 360) * (g.width / 360), g.minY + 12 + Double(k * 53 % 60), 1.2),
          0xFFFFFF, 0.8 * night)
      }
    }
    let hor = g.minY + g.height * 0.4
    let hills = r.path("hills-\(width)-\(top)") {
      var path = Path()
      path.move(to: .init(x: g.minX, y: hor))
      for k in 0...24 {
        path.addLine(
          to: .init(
            x: g.minX + g.width * Double(k) / 24,
            y: hor - 16 - 12 * sin(Double(k) * 0.9) - 8 * sin(Double(k) * 2.3)))
      }
      path.addLine(to: .init(x: g.maxX, y: hor))
      path.closeSubpath()
      return path
    }
    fill(c, hills, night > 0.5 ? 0x9AA9CF : 0xD8E6F4)
    c.fill(
      r.path("snow-\(g)") { rect(g.minX, hor, g.width, g.height) },
      with: .linearGradient(
        .init(colors: [color(0xF3F8FD), color(0xDCE9F5)]), startPoint: .init(x: 0, y: hor),
        endPoint: .init(x: 0, y: g.maxY)))
    for k in 0..<5 {
      let px = g.minX + 40 + Double(k) * (g.width / 5) + Double(k % 2) * 18
      let py = hor + 2 + Double(k % 2) * 4
      let tree = r.path("tree-\(width)-\(top)-\(k)") {
        var path = Path()
        for (a, b, w) in [(34.0, -8.0, 11.0), (24.0, 2.0, 14.0)] {
          path.move(to: .init(x: px, y: py - a))
          path.addLine(to: .init(x: px + w, y: py + b))
          path.addLine(to: .init(x: px - w, y: py + b))
          path.closeSubpath()
        }
        return path
      }
      fill(c, tree, 0x2E6B5E)
      fill(
        c, r.path("tree-snow-\(width)-\(top)-\(k)") { circle(px - 4, py - 22, 2.2) }, 0xFFFFFF, 0.9)
    }
    let snx = g.maxX - 34
    fill(c, r.path("snowman-body-\(g)") { circle(snx, hor + 22, 13) }, 0xFFFFFF)
    fill(c, r.path("snowman-head-\(g)") { circle(snx, hor + 4, 9) }, 0xFFFFFF)
    fill(c, r.path("snowman-eye-\(g)") { circle(snx + 3, hor + 3, 1.3) }, 0x1B1E26)
    line(c, snx + 6, hor + 6, snx + 13, hor + 7, 0xFF8A3D, 2.4)
    for i in 0..<6 {
      let st = M.steps(i, h)
      let p = M.place(g, i, st)
      for (x, footprint) in r.footprints(g, i) {
        if x >= p.x - 10 * p.s { break }
        fill(c, footprint, 0xB8CCE0, 0.75)
      }
      walker(c, p.x, p.y, p.s, st * 0.006, i, sel)
    }
    if night > 0 { fill(c, sceneBounds, 0x1E2A57, 0.22 * night) }
  }
  func chrome(_ c: GraphicsContext) {
    fill(c, rect(0, 0, width, height), 0xF7F8FA)
    // Header and weekly winners.
    let switchWidth = 50 + r.switchLabelWidth
    fill(c, rect(16, top + 8, switchWidth, 36, 18), 0xFFFFFF)
    c.stroke(
      rect(16.5, top + 8.5, switchWidth - 1, 35, 17.5), with: .color(color(0xE6E8EE)), lineWidth: 1)
    text(
      c, "Friends", 31, top + 26 - r.lineHeight("SemiBold", 15) / 2 - 1 / UIScreen.main.scale, 15,
      "SemiBold")
    icon(c, 0xf232, 31 + r.switchLabelWidth + 4, top + 18 - 1 / UIScreen.main.scale, 16)
    avatar(c, 1, width - 54, top + 7, 38)
    let wins = [0, 1, 3, 0, 2, 4, 0]
    let days = ["M", "T", "W", "T", "F", "S", "S"]
    for i in 0..<7 {
      let x = 16 + Double(i) * (width - 72) / 6
      text(c, days[i], x + 20, top + 60, 12, "SemiBold", i == 6 ? 0x1B1E26 : 0x9AA0AE, 0.5)
      avatar(c, wins[i], x + 4, top + 66 + r.lineHeight("SemiBold", 12), 32, i == 6)
    }
    text(c, "Steps today", width / 2, top + 140, 15, "SemiBold", 0x6B7280, 0.5)
    let ty = g.maxY + 16
    fill(c, rect(36, ty + 9, g.width - 40, 4, 2), 0xE3E7EE)
    for (i, l) in ["6 AM", "10 AM", "2 PM", "6 PM", "10 PM"].enumerated() {
      text(c, l, 36 + Double(i) * (g.width - 40) / 4, ty + 24, 11, "Regular", 0x9AA0AE, 0.5)
    }
    let statsY = g.maxY + 70
    let cardW = (g.width - 12) / 2
    for i in 0..<2 {
      let x = 16 + Double(i) * (cardW + 12)
      let tint: UInt32 = i == 0 ? 0x3D6BFF : 0xFF7A45
      fill(c, rect(x, statsY, cardW, 64, 18), 0xFFFFFF)
      c.stroke(
        rect(x + 0.5, statsY + 0.5, cardW - 1, 63, 17.5), with: .color(color(0xECEEF2)),
        lineWidth: 1)
      fill(c, rect(x + 13, statsY + 14, 36, 36, 12), tint, 26.0 / 255)
      icon(c, i == 0 ? 0xf470 : 0xf314, x + 22, statsY + 23 - 1 / UIScreen.main.scale, 18, tint)
      text(
        c, i == 0 ? "Distance" : "Active time", x + 59,
        statsY + (65 + r.lineHeight("Bold", 17) - r.lineHeight("Regular", 12)) / 2, 12, "Regular",
        0x9AA0AE)
    }
    let tabY = height - bottom - 62 - 1 / UIScreen.main.scale
    fill(c, rect(0, tabY, width, height - tabY), 0xFFFFFF)
    fill(c, rect(0, tabY, width, 1 / UIScreen.main.scale), 0xE3E7EE)
    for (i, label) in ["Home", "Friends", "Challenges", "Me"].enumerated() {
      let x = width * (Double(i) + 0.5) / 4
      let hex: UInt32 = i == 1 ? 0x1B1E26 : 0x9AA0AE
      icon(
        c, [0xf383, 0xf49f, 0xf602, 0xf4ac][i], x - 11.5,
        tabY + 8 + (48 - 25 - r.lineHeight("SemiBold", 11)) / 2, 23, hex)
      text(
        c, label, x,
        tabY + 1 / UIScreen.main.scale + 33 + (48 - 25 - r.lineHeight("SemiBold", 11)) / 2, 11,
        "SemiBold", hex, 0.5)
    }
  }

  func draw(_ c: GraphicsContext, _ h: Double, _ sel: Int, _ bubbleSelected: Int) {
    text(
      // A single-line RN TextInput places the 64 pt line 4 pt above SwiftUI Text's centered line.
      c, M.number(M.steps(1, h)), width / 2 - 0.5 / UIScreen.main.scale,
      top + 136 + r.lineHeight("SemiBold", 15), 56, "Bold",
      0x1B1E26, 0.5, 64, true)
    text(
      c, M.rank(h), width / 2, top + 204 + r.lineHeight("SemiBold", 15), 14, "Regular", 0x6B7280,
      0.5)
    scene(c, h, sel)
    fill(c, r.path("time-pill-\(g)") { rect(30, g.minY + 14, 101, 28, 14) }, 0xFFFFFF, 0.86)
    icon(c, 0xf5de, 40, g.minY + 21, 14)
    text(c, M.time(h), 59, g.minY + 20, 13, "SemiBold", 0x1B1E26, 0, nil, true)
    for i in 0..<6 {
      let p = M.place(g, i, M.steps(i, h))
      let y = p.y - 60 * p.s - 28
      let f = M.friends[i]
      let label = f.name
      let labelWidth = r.nameWidths[i]
      let stepLabel = M.number(M.steps(i, h))
      let stepWidth = c.resolve(Text(stepLabel).font(Font(r.font("Regular", 11))).monospacedDigit())
        .measure(in: .init(width: 2000, height: 30)).width
      let bubbleW = labelWidth + 18 + (bubbleSelected == i ? 4 + max(36, stepWidth) : 0)
      let scale = UIScreen.main.scale
      let localLeft = (80 - bubbleW) / 2
      let x = p.x - 40 + (localLeft * scale).rounded() / scale
      let labelX = p.x - 40 + floor((localLeft + 9) * scale) / scale
      fill(c, rect(x, y, bubbleW, 22, 11), i == 1 ? 0x1B1E26 : 0xFFFFFF)
      c.stroke(
        rect(x + 0.5, y + 0.5, bubbleW - 1, 21, 10.5),
        with: .color(color(i == 1 ? 0x1B1E26 : 0xE3E7EE)), lineWidth: 1)
      text(
        c, label, labelX, y + (22 - r.lineHeight("SemiBold", 11)) / 2, 11, "SemiBold",
        i == 1 ? 0xFFFFFF : 0x1B1E26)
      if bubbleSelected == i {
        text(
          c, stepLabel, labelX + labelWidth + 4, y + (22 - r.lineHeight("SemiBold", 11)) / 2, 11,
          "Regular",
          i == 1 ? 0xFFFFFF : 0x1B1E26, 0, nil, true)
      }
    }
    let ty = g.maxY + 16
    let tx = 26 + M.clamp((h - 6) / 16) * (g.width - 40)
    fill(c, circle(tx + 10, ty + 11, 10), 0x1B1E26)
    fill(c, circle(tx + 10, ty + 11, 5), 0xFFFFFF)
    let statsY = g.maxY + 70
    let cardW = (g.width - 12) / 2
    let steps = M.steps(1, h)
    let m = Int(floor(steps / 110 + 0.5))
    for i in 0..<2 {
      let x = 16 + Double(i) * (cardW + 12)
      text(
        c, i == 0 ? String(format: "%.1f km", steps * 0.72 / 1000) : "\(m/60)h \(m%60)m", x + 59,
        statsY + (63 - r.lineHeight("Bold", 17) - r.lineHeight("Regular", 12)) / 2, 17, "Bold",
        0x1B1E26, 0, nil, true)
    }
  }
}
