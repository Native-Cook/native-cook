import CoreText
import SwiftUI
import UIKit

struct SpilledCoffeeErrorArt {
  typealias M = SpilledCoffeeErrorMotion
  typealias H = SpilledCoffeeErrorTheme
  typealias C = H.C
  typealias V3 = SIMD3<Double>
  struct CupFrame {
    var axis: V3
    var u: V3
    var v: V3
    var base: V3
  }
  struct Cup {
    var mouthC: CGPoint
    var low: CGPoint
    var fr: CupFrame
  }
  let size: CGSize
  static let tau = Double.pi * 2
  static let ce = cos(H.elev)
  static let se = sin(H.elev)
  static let fall = V3(-cos(H.yaw), 0, sin(H.yaw))
  static let side = V3(-fall.z, 0, fall.x)
  static let view = V3(0, se, ce)
  static let registered: Void = {
    for name in [
      "Manrope_400Regular", "Manrope_500Medium", "Manrope_600SemiBold", "Manrope_700Bold",
    ] {
      if let url = Bundle.main.url(forResource: "SpilledCoffeeError-" + name, withExtension: "ttf")
      {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
  func color(_ hex: Int, _ alpha: Double = 1) -> Color { H.color(hex, alpha) }
  func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double = 0) -> Path {
    Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r)
  }
  func oval(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
    Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h))
  }
  func poly(_ points: [CGPoint]) -> Path {
    Path { path in
      if let first = points.first { path.move(to: first) }
      for p in points.dropFirst() { path.addLine(to: p) }
      path.closeSubpath()
    }
  }
  func line(_ points: [CGPoint]) -> Path {
    Path { path in
      if let first = points.first { path.move(to: first) }
      for p in points.dropFirst() { path.addLine(to: p) }
    }
  }
  func fill(_ c: GraphicsContext, _ p: Path, _ hex: Int, _ a: Double = 1) {
    c.fill(p, with: .color(color(hex, a)))
  }
  func stroke(_ c: GraphicsContext, _ p: Path, _ hex: Int, _ width: Double, _ a: Double = 1) {
    c.stroke(
      p, with: .color(color(hex, a)),
      style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
  }
  func blur(_ c: GraphicsContext, _ p: Path, _ hex: Int, _ a: Double, _ sigma: Double) {
    var blurred = c
    blurred.addFilter(.blur(radius: sigma))
    blurred.drawLayer { fill($0, p, hex, a) }
  }
  func gradient(_ colors: [Int], _ stops: [Double]? = nil) -> Gradient {
    Gradient(
      stops: colors.enumerated().map {
        .init(
          color: color($0.element),
          location: stops?[$0.offset] ?? Double($0.offset) / Double(colors.count - 1))
      })
  }
  func linear(
    _ c: GraphicsContext, _ p: Path, _ colors: [Int], _ from: CGPoint, _ to: CGPoint,
    _ stops: [Double]? = nil
  ) {
    c.fill(p, with: .linearGradient(gradient(colors, stops), startPoint: from, endPoint: to))
  }
  func proj(_ p: V3) -> CGPoint {
    CGPoint(x: H.base.x + p.x, y: H.base.y - p.y * Self.ce + p.z * Self.se)
  }
  func dot(_ a: V3, _ b: V3) -> Double { a.x * b.x + a.y * b.y + a.z * b.z }
  func cross(_ a: V3, _ b: V3) -> V3 {
    V3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
  }
  func normalized(_ p: V3) -> V3 {
    let n = sqrt(dot(p, p))
    return p / (n == 0 ? 1 : n)
  }
  func cupFrame(_ theta: Double) -> CupFrame {
    let t = theta * Double.pi / 180
    let ct = cos(t)
    let st = sin(t)
    return CupFrame(
      axis: V3(st * Self.fall.x, ct, st * Self.fall.z), u: Self.side,
      v: V3(ct * Self.fall.x, -st, ct * Self.fall.z),
      base: V3(H.rb * (1 - ct) * Self.fall.x, H.rb * st, H.rb * (1 - ct) * Self.fall.z))
  }
  func ringPoint(_ fr: CupFrame, _ h: Double, _ r: Double, _ angle: Double) -> V3 {
    fr.base + fr.axis * h + (fr.u * cos(angle) + fr.v * sin(angle)) * r
  }
  func ring(_ fr: CupFrame, _ h: Double, _ r: Double) -> [CGPoint] {
    (0..<40).map { proj(ringPoint(fr, h, r, Double($0) / 40 * Self.tau)) }
  }
  func hull(_ points: [CGPoint]) -> [CGPoint] {
    let sorted = points.sorted { $0.x == $1.x ? $0.y < $1.y : $0.x < $1.x }
    func turn(_ o: CGPoint, _ a: CGPoint, _ b: CGPoint) -> Double {
      (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }
    var lower: [CGPoint] = []
    var upper: [CGPoint] = []
    for p in sorted {
      while lower.count >= 2 && turn(lower[lower.count - 2], lower[lower.count - 1], p) <= 0 {
        lower.removeLast()
      }
      lower.append(p)
    }
    for p in sorted.reversed() {
      while upper.count >= 2 && turn(upper[upper.count - 2], upper[upper.count - 1], p) <= 0 {
        upper.removeLast()
      }
      upper.append(p)
    }
    if !lower.isEmpty { lower.removeLast() }
    if !upper.isEmpty { upper.removeLast() }
    return lower + upper
  }
  // Walk the visible arc from its first sample to avoid a seam at angle zero.
  func band(_ fr: CupFrame, _ h0: Double, _ h1: Double) -> Path? {
    let r0 = M.mix(H.rb, H.rm, h0 / H.L)
    let r1 = M.mix(H.rb, H.rm, h1 / H.L)
    let visible = (0..<48).map { i in
      dot(fr.u * cos(Double(i) / 48 * Self.tau) + fr.v * sin(Double(i) / 48 * Self.tau), Self.view)
        > -0.02
    }
    var start = 0
    for i in 0..<48 where !visible[i] && visible[(i + 1) % 48] {
      start = (i + 1) % 48
      break
    }
    var front: [CGPoint] = []
    var back: [CGPoint] = []
    for k in 0...48 {
      let i = (start + k) % 48
      if !visible[i] && k > 0 { break }
      let angle = Double(i) / 48 * Self.tau
      front.append(proj(ringPoint(fr, h0, r0, angle)))
      back.append(proj(ringPoint(fr, h1, r1, angle)))
    }
    return front.count < 2 ? nil : poly(front + back.reversed())
  }
  func drawRoom(_ c: GraphicsContext, _ amb: Double, _ top: Double, _ bottom: Double) {
    linear(
      c, rect(-10, top, H.PIN_W + 20, H.floorY - top), [C.wallTop, C.wallLow],
      CGPoint(x: 0, y: top), CGPoint(x: 0, y: H.floorY))
    linear(
      c, rect(-10, H.floorY, H.PIN_W + 20, bottom - H.floorY), [C.floorFar, C.floor],
      CGPoint(x: 0, y: H.floorY), CGPoint(x: 0, y: H.floorY + 160))
    blur(c, rect(-10, H.floorY - 2, H.PIN_W + 20, 10), 0xD5DAE1, 0.6, 6)
    c.fill(
      rect(150, top, H.PIN_W, H.floorY - top),
      with: .radialGradient(
        Gradient(colors: [color(0xFFFFFF, 0.9), color(0xFFFFFF, 0)]),
        center: CGPoint(x: 560, y: 60), startRadius: 0, endRadius: 380))
    func book(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ d: Double, _ circleX: Double) {
      blur(c, rect(x - 6, y + h - 4, w + 30, 18), C.shadow, 0.5, 9)
      fill(
        c,
        poly([
          CGPoint(x: x, y: y), CGPoint(x: x + d, y: y - d * 0.55),
          CGPoint(x: x + w + d, y: y - d * 0.55), CGPoint(x: x + w, y: y),
        ]), C.bookTop)
      fill(c, rect(x, y, w, h, 6), C.book)
      fill(c, rect(x, y + h - 10, w, 10), C.bookSide, 0.7)
      let radius = h * 0.17
      let disc = oval(circleX - radius, y + h * 0.42 - radius, radius * 2, radius * 2)
      fill(c, disc, 0xFFFFFF)
      stroke(c, disc, 0xC6CDD6, 2)
      let label = rect(circleX + h * 0.3, y + h * 0.36, w - (circleX - x) - h * 0.4, h * 0.12, 3)
      fill(c, label, 0xFFFFFF)
      stroke(c, label, 0xC6CDD6, 1.5)
    }
    book(446, 386, 240, 112, 26, 474)
    book(516, 312, 170, 86, 22, 544)
    blur(c, oval(-40, 420, 170, 34), C.shadow, 0.6, 12)
    var pot = Path()
    pot.move(to: CGPoint(x: -34, y: 196))
    pot.addLine(to: CGPoint(x: 112, y: 196))
    pot.addLine(to: CGPoint(x: 98, y: 438))
    pot.addQuadCurve(to: CGPoint(x: -20, y: 438), control: CGPoint(x: 40, y: 450))
    pot.closeSubpath()
    linear(
      c, pot, [0xFFFFFF, C.pot, C.potShade], CGPoint(x: -34, y: 0), CGPoint(x: 112, y: 0),
      [0, 0.55, 1])
    fill(c, oval(-34, 184, 146, 26), 0xE3E7EC)
    fill(c, oval(-28, 188, 134, 18), 0xC9CED6)
    func leaf(
      _ bx: Double, _ by: Double, _ len: Double, _ wid: Double, _ angle: Double, _ ca: Int,
      _ cb: Int, _ k: Double
    ) {
      var leafContext = c
      leafContext.translateBy(x: bx, y: by)
      leafContext.rotate(by: .degrees(angle + 2.2 * sin(Self.tau * amb / M.LOOP + k)))
      var path = Path()
      path.move(to: .zero)
      path.addCurve(
        to: CGPoint(x: 0, y: -len), control1: CGPoint(x: -wid, y: -len * 0.3),
        control2: CGPoint(x: -wid * 0.6, y: -len * 0.85))
      path.addCurve(
        to: .zero, control1: CGPoint(x: wid * 0.7, y: -len * 0.8),
        control2: CGPoint(x: wid, y: -len * 0.3))
      path.closeSubpath()
      linear(leafContext, path, [ca, cb], CGPoint(x: -wid, y: 0), CGPoint(x: wid, y: 0))
      stroke(leafContext, line([.zero, CGPoint(x: 0, y: -len * 0.92)]), 0xFFFFFF, 1.4, 0.25)
    }
    leaf(20, 196, 280, 66, -14, C.leafC, C.leafA, 0)
    leaf(46, 196, 250, 58, 12, C.leafA, C.leafB, 1.3)
    leaf(8, 196, 200, 50, -38, C.leafA, C.leafB, 2.1)
    leaf(74, 196, 170, 44, 34, C.leafB, C.leafA, 3.4)
    leaf(-10, 196, 236, 54, -2, C.leafB, C.leafC, 4.6)
  }
  func drawPaper(_ c: GraphicsContext) {
    var shadow = c
    shadow.translateBy(x: 4, y: 7)
    blur(shadow, poly(H.paper), C.shadow, 0.55, 7)
    fill(c, poly(H.paper), C.paper)
  }
  static let blobs: [[Double]] = [
    [282, 566, 24], [258, 574, 30], [228, 581, 34], [197, 587, 30], [170, 590, 24], [212, 595, 20],
    [244, 590, 18], [148, 592, 16], [298, 576, 14],
  ]
  func drawPuddle(_ c: GraphicsContext, _ pose: M.Pose, _ mouth: CGPoint) {
    let s = pose.spill
    if s <= 0.002 { return }
    // RN sharpens the blurred blobs' alpha with a colour matrix (22·a − 9, clamped). SwiftUI's
    // colour matrix does not clamp, so alpha above 1 wiped the middle out and left only a ring;
    // the alpha threshold at the same crossing (a ≈ 0.43) gives the same merged puddle.
    var layer = c
    layer.addFilter(.alphaThreshold(min: 0.43, color: color(C.coffee)))
    layer.drawLayer { blobs in
      for i in 0..<Self.blobs.count {
        let k = M.smooth(s * Double(Self.blobs.count) * 1.25 - Double(i))
        if k <= 0.01 { continue }
        let b = Self.blobs[i]
        let x =
          M.mix(mouth.x, b[0], M.easeOut(k)) + 1.5
          * sin(Self.tau * pose.amb / M.LOOP * 2 + Double(i))
        let y = M.mix(mouth.y + 30, b[1], M.easeOut(k))
        let r = b[2] * k
        blur(blobs, oval(x - r * 1.35, y - r * 0.42, r * 2.7, r * 0.84), C.coffee, 1, 7)
      }
    }
    for i in 0..<4 {
      let k = M.smooth(s * Double(Self.blobs.count) * 1.25 - Double(i))
      if k <= 0.2 { continue }
      let b = Self.blobs[i]
      fill(
        c, oval(b[0] - b[2] * 0.6, b[1] - b[2] * 0.22, b[2] * 0.9, b[2] * 0.16), C.coffeeLight,
        0.55 * k)
    }
    func hit(_ t: Double, _ x: Double, _ y: Double) {
      if t < 0 || t > 1.1 { return }
      for k in 0..<2 {
        let q = M.clamp01((t - Double(k) * 0.18) / 0.9)
        if q <= 0 { continue }
        stroke(
          c, oval(x - 30 * q, y - 9 * q, 60 * q, 18 * q), C.coffeeLight, 2 * (1 - q), 0.8 * (1 - q))
      }
    }
    hit(pose.splash, 236, 584)
    // Droplets thrown up where the coffee lands, falling back on their arcs.
    if pose.splash >= 0 && pose.splash < 0.75 {
      let t = pose.splash
      for i in 0..<12 {
        let h1 = sin(Double(i + 1) * 12.9898) * 43758.5453
        let r1 = h1 - floor(h1)
        let angle = -Double.pi / 2 + (r1 - 0.5) * 2.2
        let velocity = 160 + 140 * (Double(i) * 0.37).truncatingRemainder(dividingBy: 1)
        let x = 236 + cos(angle) * velocity * t * 1.3
        let y = 580 + sin(angle) * velocity * t * 0.9 + 520 * t * t
        if y > 600 { continue }
        let alpha = 1 - M.clamp01((t - 0.45) / 0.3)
        fill(c, oval(x - 3, y - 2.2, 6, 4.4), C.coffee, alpha)
      }
    }
    hit(pose.drip - 0.35, 262, 576)
  }
  func drawCup(_ c: GraphicsContext, _ pose: M.Pose) -> Cup {
    let fr = cupFrame(pose.theta)
    let baseRing = ring(fr, 0, H.rb)
    let mouthRing = ring(fr, H.L, H.rm)
    let mouthC = proj(fr.base + fr.axis * H.L)
    let baseC = proj(fr.base)
    let shadow = hull(
      (baseRing + mouthRing).map {
        CGPoint(x: $0.x + 14, y: H.base.y + 6 + ($0.y - H.base.y) * 0.08)
      })
    blur(c, poly(shadow), C.shadow, 0.55, 10)
    let body = poly(hull(baseRing + mouthRing))
    let ax = CGPoint(x: mouthC.x - baseC.x, y: mouthC.y - baseC.y)
    let al = max(1, hypot(ax.x, ax.y))
    let nx = -ax.y / max(1, hypot(ax.x, ax.y))
    let ny = ax.x / max(1, hypot(ax.x, ax.y))
    let mid = CGPoint(x: (mouthC.x + baseC.x) / 2, y: (mouthC.y + baseC.y) / 2)
    let from = CGPoint(x: mid.x + nx * 70, y: mid.y + ny * 70)
    let to = CGPoint(x: mid.x - nx * 70, y: mid.y - ny * 70)
    linear(c, body, [C.cupShade, C.cup, 0xFFFFFF, C.cupShade], from, to, [0, 0.4, 0.6, 1])
    if dot(fr.axis, Self.view) < 0 { fill(c, poly(baseRing), 0xE9ECEF) }
    if let sleeve = band(fr, H.sleeve[0] * H.L, H.sleeve[1] * H.L) {
      var clipped = c
      clipped.clip(to: body)
      linear(
        clipped, sleeve, [C.sleeveShade, C.sleeve, 0xD6E6C0, C.sleeveShade], from, to,
        [0, 0.4, 0.6, 1])
    }
    var best = 0.0
    var bestDot = -2.0
    for i in 0..<24 {
      let angle = Double(i) / 24 * Self.tau
      let dd = dot(fr.u * cos(angle) + fr.v * sin(angle), Self.view)
      if dd > bestDot {
        bestDot = dd
        best = angle
      }
    }
    let hb = (H.sleeve[0] + H.sleeve[1]) * 0.5 * H.L
    let bc = proj(ringPoint(fr, hb, M.mix(H.rb, H.rm, hb / H.L), best))
    var bean = c
    bean.translateBy(x: bc.x, y: bc.y)
    bean.rotate(by: .radians(atan2(ax.y / al, ax.x / al)))
    fill(bean, oval(-14, -10, 28, 20), C.bean, 0.95)
    var crease = Path()
    crease.move(to: CGPoint(x: -10, y: 2))
    crease.addCurve(
      to: CGPoint(x: 10, y: -2), control1: CGPoint(x: -3, y: -6), control2: CGPoint(x: 3, y: 6))
    stroke(bean, crease, C.sleeve, 2.2)
    let low = mouthRing.max { $0.y < $1.y } ?? .zero
    if dot(fr.axis, Self.view) > 0 {
      let mouth = poly(mouthRing)
      fill(c, mouth, C.cupInside)
      let left = 1 - pose.spill
      if left > 0.02 && pose.theta > 30 {
        var clipped = c
        clipped.clip(to: mouth)
        fill(
          clipped,
          oval(
            low.x - H.rm * 0.9, low.y - H.rm * 0.5 * left - 10, H.rm * 1.8, H.rm * 1.1 * left + 14),
          C.coffee)
      }
      stroke(c, mouth, 0xFFFFFF, 4.5)
    }
    return Cup(mouthC: mouthC, low: low, fr: fr)
  }
  func drawLid(_ c: GraphicsContext, _ pose: M.Pose, _ cup: Cup) {
    let k = pose.lid
    let fr = cup.fr
    let on = proj(fr.base + fr.axis * (H.L + 4))
    let e = M.easeOut(k)
    let hop = pose.lidHop >= 0 ? 26 * abs(sin(pose.lidHop * 9)) * exp(-pose.lidHop * 5) : 0
    let x = M.mix(on.x, H.lid.x, e)
    let y = M.mix(on.y, H.lid.y, e) - sin(Double.pi * k) * 110 - hop
    let tumble = sin(Double.pi * k) * 1.4
    let n = normalized(
      V3(
        M.mix(fr.axis.x, 0, e) + sin(tumble) * 0.4, M.mix(fr.axis.y, 1, e),
        M.mix(fr.axis.z, 0, e) + cos(tumble) * 0.1 * sin(Double.pi * k)))
    let ref = abs(n.y) < 0.9 ? V3(0, 1, 0) : V3(1, 0, 0)
    let u = normalized(cross(n, ref))
    let v = cross(n, u)
    let r = M.mix(H.rm + 5, H.lidR, e)
    var points: [CGPoint] = []
    var top: [CGPoint] = []
    for i in 0..<40 {
      let angle = Double(i) / 40 * Self.tau
      let w = (u * cos(angle) + v * sin(angle)) * r
      points.append(CGPoint(x: x + w.x, y: y - w.y * Self.ce + w.z * Self.se))
      top.append(
        CGPoint(
          x: x + w.x * 0.8 - n.x * 6,
          y: y - (w.y * 0.8 + n.y * 6) * Self.ce + (w.z * 0.8 + n.z * 6) * Self.se))
    }
    if k > 0.98 {
      blur(c, oval(H.lid.x - H.lidR + 8, H.lid.y - 6, H.lidR * 2, 22), C.shadow, 0.5, 7)
    }
    fill(c, poly(points), C.lidShade)
    var lifted = c
    lifted.translateBy(x: 0, y: -5)
    fill(lifted, poly(points), C.lid)
    stroke(c, poly(top), 0xE6E9F0, 2)
  }
  func drawStream(_ c: GraphicsContext, _ pose: M.Pose, _ low: CGPoint) {
    if pose.pour <= 0.01 { return }
    var p = Path()
    p.move(to: CGPoint(x: low.x, y: low.y - 6))
    p.addQuadCurve(to: CGPoint(x: 236, y: 584), control: CGPoint(x: low.x - 30, y: low.y + 30))
    stroke(c, p, C.coffee, 12 * pose.pour, 0.95)
  }
  func drawSteam(_ c: GraphicsContext, _ pose: M.Pose, _ top: CGPoint) {
    if pose.steam <= 0.01 { return }
    for k in 0..<3 {
      let x0 = top.x - 22 + Double(k) * 22
      let phase = pose.amb * 4 + Double(k) * 1.7
      let points = (0...16).map { i -> CGPoint in
        let u = Double(i) / 16
        return CGPoint(x: x0 + 8 * sin(phase + u * 6) * u, y: top.y - 16 - u * 90)
      }
      stroke(c, line(points), C.steam, 4, 0.55 * pose.steam * (0.6 + 0.4 * sin(phase)))
    }
  }
  // Baselines and letter spacing follow the Skia text helper, using the bundled Manrope faces.
  func text(
    _ c: GraphicsContext, _ value: String, _ x: Double, _ cy: Double, _ size: Double,
    _ weight: String, _ hex: Int, _ alpha: Double = 1, _ track: Double = 0
  ) {
    _ = Self.registered
    let font = UIFont(name: "Manrope-" + weight, size: size) ?? UIFont.systemFont(ofSize: size)
    let attributes: [NSAttributedString.Key: Any] = [.font: font, .kern: track]
    let attributed = NSAttributedString(string: value, attributes: attributes)
    let line = CTLineCreateWithAttributedString(attributed)
    let width = CTLineGetTypographicBounds(line, nil, nil, nil) - (track == 0 ? 0 : track)
    // A vector glyph path keeps Canvas baselines identical to drawText rather than guessing anchors.
    let path = CGMutablePath()
    for run in CTLineGetGlyphRuns(line) as! [CTRun] {
      let count = CTRunGetGlyphCount(run)
      var glyphs = [CGGlyph](repeating: 0, count: count)
      var positions = [CGPoint](repeating: .zero, count: count)
      CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
      CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)
      for i in 0..<count {
        if let glyph = CTFontCreatePathForGlyph(font as CTFont, glyphs[i], nil) {
          let transform = CGAffineTransform(
            a: 1, b: 0, c: 0, d: -1, tx: x - width / 2 + positions[i].x,
            ty: cy + size * 0.36 - positions[i].y)
          path.addPath(glyph, transform: transform)
        }
      }
    }
    fill(c, Path(path), hex, alpha)
  }
  func drawText(_ c: GraphicsContext, _ pose: M.Pose) {
    let k = pose.text
    let cx = H.PIN_W / 2
    func fade(
      _ a: Double, _ dy: Double, _ value: String, _ y: Double, _ size: Double, _ weight: String,
      _ hex: Int
    ) {
      if a < 0.01 { return }
      text(c, value, cx, y + dy, size, weight, hex, a)
    }
    fade(1 - k, -10 * k, H.Copy.title, H.titleY, 34, "SemiBold", C.title)
    fade(k, 10 * (1 - k), H.Copy.retrying, H.titleY, 34, "SemiBold", C.title)
    for i in 0..<2 {
      fade(1 - k, -10 * k, H.Copy.body[i], H.bodyY[i], 25, "Medium", C.body)
      fade(k, 10 * (1 - k), H.Copy.retryingBody[i], H.bodyY[i], 25, "Medium", C.body)
    }
    let b = H.button
    let scale = 1 - 0.04 * pose.press
    var button = c
    button.translateBy(x: b.midX, y: b.midY)
    button.scaleBy(x: scale, y: scale)
    blur(
      button, rect(-b.width / 2 + 10, -b.height / 2 + 24, b.width - 20, b.height, b.height / 2),
      0xB8BEE0, 0.6, 16)
    fill(button, rect(-b.width / 2, -b.height / 2, b.width, b.height, b.height / 2), C.button)
    if pose.dots < 0.99 {
      text(button, H.Copy.button, 0, 0, 25, "SemiBold", C.buttonText, 1 - pose.dots, 2.5)
    }
    if pose.dots > 0.01 {
      for i in 0..<3 {
        let bounce = max(0, sin(pose.amb * 7 - Double(i) * 0.7))
        fill(
          button, oval(Double(i - 1) * 18 - 5.5, -6 * bounce - 5.5, 11, 11), C.buttonText,
          pose.dots * (0.5 + 0.5 * bounce))
      }
    }
  }
  func drawScene(_ context: GraphicsContext, _ pose: M.Pose) {
    let scale = size.width / H.REF_W
    var c = context
    c.scaleBy(x: scale, y: scale)
    c.translateBy(x: 0, y: H.DY)
    c.scaleBy(x: H.S, y: H.S)
    let top = -H.DY / H.S - 10
    let bottom = (size.height / scale - H.DY) / H.S + 10
    drawRoom(c, pose.amb, top, bottom)
    drawPaper(c)
    let fr = cupFrame(pose.theta)
    drawPuddle(c, pose, proj(fr.base + fr.axis * H.L))
    let cup = drawCup(c, pose)
    drawStream(c, pose, cup.low)
    if pose.drip >= 0 && pose.drip < 0.35 {
      let q = pose.drip / 0.35
      fill(c, oval(cup.low.x - 4, M.mix(cup.low.y, 576, q * q) - 5, 8, 10), C.coffee)
    }
    drawLid(c, pose, cup)
    drawSteam(c, pose, proj(fr.base + fr.axis * (H.L + 10)))
    drawText(c, pose)
    if pose.rewind > 0.01 {
      for i in 0..<6 {
        let y =
          (pose.amb * 900 + Double(i) * 260).truncatingRemainder(dividingBy: bottom - top) + top
        fill(c, rect(-10, y, H.PIN_W + 20, Double(3 + (i % 2) * 2)), 0x8FA2C8, 0.09 * pose.rewind)
      }
    }
    context.fill(
      Path(CGRect(origin: .zero, size: size)),
      with: .shader(ShaderLibrary.SpilledCoffeeErrorGrain()))
  }
}
