import CoreText
import SwiftUI
import UIKit

/// A direct Canvas port of ltk-draw.ts. Coordinates and baseline offsets are pin pixels.
enum LuckyTaskHubArt {
  typealias M = LuckyTaskHubMotion
  typealias C = LuckyTaskHubTheme.C
  static let tau = Double.pi * 2

  static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(
      .sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
      blue: Double(hex & 255) / 255, opacity: alpha)
  }

  static let registered: Void = {
    for name in ["Nunito-Black", "Inter-Bold", "Inter-SemiBold", "Inter-Medium"] {
      if let url = Bundle.main.url(forResource: "LuckyTaskHub-" + name, withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
  static let atlas: [Image] = {
    guard let path = Bundle.main.path(forResource: "LuckyTaskHub-atlas", ofType: "webp"),
      let source = UIImage(contentsOfFile: path)?.cgImage
    else { return [] }
    return (0..<12).compactMap { index in
      source.cropping(to: CGRect(x: index % 4 * 256, y: index / 4 * 256, width: 256, height: 256))
        .map { Image(decorative: $0, scale: 1) }
    }
  }()

  struct Face {
    let font: CTFont
    let size: Double
    init(_ name: String, _ size: Double) {
      _ = registered
      font = CTFontCreateWithName(name as CFString, size, nil)
      self.size = size
    }
  }
  enum F {
    static let header = Face("Nunito-Black", 46), title = Face("Nunito-Black", 40)
    static let go = Face("Nunito-Black", 27), small = Face("Inter-SemiBold", 20)
    // The bundled bold file's internal PostScript name differs from its filename.
    static let timer = Face("Inter-SemiBold", 21), label = Face("Inter24pt-Bold", 19)
    static let h2 = Face("Inter24pt-Bold", 31), count = Face("Nunito-Black", 62)
    static let countSmall = Face("Nunito-Black", 38), body = Face("Inter-Medium", 23)
    static let reward = Face("Inter24pt-Bold", 27), balance = Face("Inter24pt-Bold", 29)
    static let dollar = Face("Nunito-Black", 30)
  }

  /// Canvas values copy on save, including transforms, opacity and clipping.
  final class Drawing {
    var ctx: GraphicsContext
    var stack: [GraphicsContext] = []
    init(_ context: GraphicsContext) { ctx = context }
    func save() { stack.append(ctx) }
    func restore() { ctx = stack.removeLast() }
    func translate(_ x: Double, _ y: Double) { ctx.translateBy(x: x, y: y) }
    func scale(_ x: Double, _ y: Double) { ctx.scaleBy(x: x, y: y) }
    func rotate(_ degrees: Double) { ctx.rotate(by: .degrees(degrees)) }
    func skew(_ x: Double) {
      ctx.concatenate(CGAffineTransform(a: 1, b: 0, c: x, d: 1, tx: 0, ty: 0))
    }
    func clip(_ p: Path) { ctx.clip(to: p) }
    func fill(_ p: Path, _ hex: Int, _ alpha: Double = 1) {
      ctx.fill(p, with: .color(color(hex, alpha)))
    }
    func stroke(_ p: Path, _ hex: Int, _ width: Double, _ alpha: Double = 1) {
      ctx.stroke(
        p, with: .color(color(hex, alpha)),
        style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }
    func gradient(
      _ p: Path, _ colors: [Int], _ stops: [Double], _ x0: Double, _ y0: Double, _ x1: Double,
      _ y1: Double
    ) {
      ctx.fill(
        p,
        with: .linearGradient(
          Gradient(
            stops: zip(colors, stops).map {
              .init(color: color($0.0), location: $0.1)
            }), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1)))
    }
    func circle(_ x: Double, _ y: Double, _ radius: Double, _ hex: Int, _ alpha: Double = 1) {
      fill(oval(x - radius, y - radius, radius * 2, radius * 2), hex, alpha)
    }
    func ring(
      _ x: Double, _ y: Double, _ radius: Double, _ hex: Int, _ width: Double, _ alpha: Double = 1
    ) {
      stroke(oval(x - radius, y - radius, radius * 2, radius * 2), hex, width, alpha)
    }
    func line(
      _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ hex: Int, _ width: Double,
      _ alpha: Double = 1
    ) {
      stroke(path([(x0, y0), (x1, y1)]), hex, width, alpha)
    }
  }
  static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
    Path(CGRect(x: x, y: y, width: w, height: h))
  }
  static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
    Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r)
  }
  static func oval(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
    Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h))
  }
  static func path(_ points: [(Double, Double)], close: Bool = false) -> Path {
    var p = Path()
    for (i, point) in points.enumerated() {
      if i == 0 {
        p.move(to: CGPoint(x: point.0, y: point.1))
      } else {
        p.addLine(to: CGPoint(x: point.0, y: point.1))
      }
    }
    if close { p.closeSubpath() }
    return p
  }
  static func sprite(
    _ c: Drawing, _ cell: Int, _ x: Double, _ y: Double, _ size: Double, _ alpha: Double = 1
  ) {
    guard atlas.indices.contains(cell) else { return }
    c.save()
    c.ctx.opacity *= alpha
    c.ctx.draw(atlas[cell], in: CGRect(x: x - size / 2, y: y - size / 2, width: size, height: size))
    c.restore()
  }

  final class GlyphRun {
    let path: CGPath
    let width: Double
    init(_ string: String, _ face: Face) {
      let chars = Array(string.utf16)
      var glyphs = [CGGlyph](repeating: 0, count: chars.count)
      var advances = [CGSize](repeating: .zero, count: chars.count)
      CTFontGetGlyphsForCharacters(face.font, chars, &glyphs, chars.count)
      CTFontGetAdvancesForGlyphs(face.font, .horizontal, glyphs, &advances, chars.count)
      let result = CGMutablePath()
      var x = 0.0
      for i in glyphs.indices {
        if let glyph = CTFontCreatePathForGlyph(face.font, glyphs[i], nil) {
          result.addPath(glyph, transform: CGAffineTransform(translationX: x, y: 0))
        }
        // SkFont's default non-subpixel metrics round each glyph's advance.
        x += advances[i].width.rounded()
      }
      // Source measureText returns the ink bounds, including neither side bearings
      // nor trailing spaces; drawText still starts at the unadjusted baseline origin.
      path = result
      width = result.boundingBoxOfPath.width
    }
  }
  static let glyphCache = NSCache<NSString, GlyphRun>()
  static func glyphRun(_ string: String, _ face: Face) -> GlyphRun {
    let key = "\(CTFontCopyPostScriptName(face.font))-\(face.size)-\(string)" as NSString
    if let run = glyphCache.object(forKey: key) { return run }
    let run = GlyphRun(string, face)
    glyphCache.setObject(run, forKey: key)
    return run
  }
  @discardableResult static func text(
    _ c: Drawing, _ string: String, _ x: Double, _ cy: Double,
    _ font: Face, _ ink: Int, _ alpha: Double = 1, _ ax: Double = 0,
    outline: Double = 0
  ) -> Double {
    let run = glyphRun(string, font)
    let transform = CGAffineTransform(
      a: 1, b: 0, c: 0, d: -1, tx: x - run.width * ax, ty: cy + font.size * 0.36)
    let p = Path(run.path).applying(transform)
    if outline > 0 { c.stroke(p, ink, outline, alpha) } else { c.fill(p, ink, alpha) }
    return run.width
  }
  static func title(_ c: Drawing, _ string: String, _ x: Double, _ y: Double) {
    c.save()
    c.translate(x, y)
    c.skew(-0.16)
    text(c, string, 0, 0, F.title, C.white, outline: 7)
    text(c, string, 0, 0, F.title, C.ink2)
    c.restore()
  }
  static func commas(_ n: Int) -> String {
    let s = Array(String(n))
    var result = ""
    for i in s.indices {
      result.append(s[i])
      let left = s.count - 1 - i
      if left > 0 && left % 3 == 0 { result.append(",") }
    }
    return result
  }
  @discardableResult static func rolling(
    _ c: Drawing, _ value: Double, _ x: Double, _ cy: Double,
    _ font: Face, _ ink: Int, _ alpha: Double = 1, _ ax: Double = 0,
    commas useCommas: Bool = false
  ) -> Double {
    let n = Int(floor(value + 1e-6))
    let k = min(1, max(0, value - Double(n)))
    let a = useCommas ? commas(n) : String(n)
    let b = useCommas ? commas(n + 1) : String(n + 1)
    if k < 0.001 || a.count != b.count {
      return text(c, k > 0.5 ? b : a, x, cy, font, ink, alpha, ax)
    }
    let width = glyphRun(a, font).width
    let h = font.size * 1.05
    let e = M.smooth(k)
    var px = x - width * ax
    for (ca, cb) in zip(a, b) {
      let cw = glyphRun(String(ca), font).width
      if ca == cb {
        text(c, String(ca), px, cy, font, ink, alpha)
      } else {
        c.save()
        c.clip(rect(px - 2, cy - h * 0.55, cw + 4, h * 1.1))
        text(c, String(ca), px, cy - e * h, font, ink, alpha)
        text(c, String(cb), px, cy + (1 - e) * h, font, ink, alpha)
        c.restore()
      }
      px += cw
    }
    return width
  }
  static func go(_ c: Drawing, _ x: Double, _ y: Double, _ press: Double, _ amb: Double) {
    c.save()
    c.translate(x + 43, y + 18)
    c.scale(1 - 0.04 * press, 1 - 0.04 * press)
    c.fill(rr(-43, -18, 86, 36, 18), C.black)
    c.save()
    c.translate(-29, 0)
    c.skew(-0.16)
    text(c, "Go", 0, -1, F.go, C.white)
    c.restore()
    let nudge = 4 * pow(max(0, sin(tau * 3 * amb / M.LOOP)), 6)
    let ax = 9 + nudge
    c.fill(path([(ax, 5), (ax + 18, 5), (ax + 10, -4)], close: true), C.white)
    c.restore()
  }
  static func star(
    _ c: Drawing, _ x: Double, _ y: Double, _ radius: Double, _ ink: Int, _ alpha: Double = 1,
    outline: Double = 0
  ) {
    var p = Path()
    p.move(to: CGPoint(x: x, y: y - radius))
    p.addQuadCurve(to: CGPoint(x: x + radius, y: y), control: CGPoint(x: x, y: y))
    p.addQuadCurve(to: CGPoint(x: x, y: y + radius), control: CGPoint(x: x, y: y))
    p.addQuadCurve(to: CGPoint(x: x - radius, y: y), control: CGPoint(x: x, y: y))
    p.addQuadCurve(to: CGPoint(x: x, y: y - radius), control: CGPoint(x: x, y: y))
    p.closeSubpath()
    if outline > 0 { c.stroke(p, ink, outline, alpha) } else { c.fill(p, ink, alpha) }
  }

  static func drawRanking(_ c: Drawing, _ f: M.Frame) {
    c.save()
    c.translate(184, 243)
    c.rotate(-4)
    c.gradient(rr(-150, -205, 300, 410, 30), [C.orange, C.redCard], [0, 1], 0, -205, 0, 205)
    c.restore()
    let card = rr(40, 50, 310, 395, 30)
    c.gradient(card, [0xFFE13A, C.yellow], [0, 1], 40, 50, 40, 445)
    c.save()
    c.clip(card)
    for i in 1...3 {
      let d = Double(i) * 70
      c.save()
      c.translate(40 + 310 * 0.55, 50 + 395 * 0.55)
      c.rotate(12)
      c.stroke(rr(-d, -d, d * 2, d * 2, 22), 0xFFEA70, 14, 0.7)
      c.restore()
    }
    c.restore()
    title(c, "Ranking", 62, 92)
    c.fill(rr(62, 118, 124, 32, 16), 0xFFF6D2)
    c.ring(79, 134, 8, C.ink, 2.2)
    c.line(79, 134, 79, 129, C.ink, 2)
    c.line(79, 134, 83, 136, C.ink, 2)
    text(c, "05:06:26", 94, 134, F.timer, C.ink)
    let panel = rr(56, 196, 282, 170, 26)
    c.gradient(panel, [0xFFFBE6, 0xFFEFA0], [0, 1], 0, 196, 0, 366)
    c.stroke(panel, C.white, 5)
    c.save()
    c.clip(panel)
    c.fill(oval(99, 306, 192, 44), 0xF4C21C)
    c.fill(oval(99, 296, 192, 44), C.podium)
    c.stroke(oval(195 - 96 * 0.8, 318 - 22 * 0.7, 96 * 1.6, 22 * 1.4), 0xFFF2A6, 3, 0.8)
    c.restore()
    var ty = 0.0
    var sx = 1.0
    var sy = 1.0
    var tilt = 0.0
    if f.hop >= 0 {
      let h = f.hop
      let air = max(0, min(1, (h - 0.08) / 0.62))
      ty = -58 * sin(.pi * air)
      let crouch = max(0, sin(.pi * min(1, h / 0.12))) * (h < 0.12 ? 1 : 0)
      let land = h > 0.7 ? exp(-(h - 0.7) * 7) * sin((h - 0.7) * 22) : 0
      sy = 1 - 0.12 * crouch - 0.1 * land + 0.06 * sin(.pi * air)
      sx = 1 + 0.1 * crouch + 0.08 * land - 0.03 * sin(.pi * air)
      tilt = 10 * sin(tau * air) * (1 - air)
    }
    let shK = 1 + ty / 120
    c.fill(oval(195 - 44 * shK, 318 - 8 * shK, 88 * shK, 16 * shK), 0xC99A00, 0.35 * shK)
    c.save()
    c.translate(197, 320 + ty)
    c.rotate(tilt)
    c.scale(sx, sy)
    if f.tierK < 1 { sprite(c, Int(f.tierFrom), 0, -150 * 0.47, 150) }
    if f.tierK > 0 { sprite(c, Int(f.tierTo), 0, -150 * 0.47, 150, f.tierK) }
    c.restore()
    if f.hop >= 0.3 && f.hop < 1.2 {
      let q = (f.hop - 0.3) / 0.9
      for i in 0..<8 {
        let a = Double(i) / 8 * tau + 0.4
        let d = 30 + 90 * M.easeOut(q)
        star(
          c, 197 + cos(a) * d, 318 - 80 - 58 + 40 * q + sin(a) * d * 0.8,
          9 * (1 - q * 0.6), i % 2 == 0 ? C.white : 0xFFF07A, 1 - q)
      }
    }
    c.fill(rr(138, 322, 118, 30, 15), C.white)
    c.stroke(rr(138, 322, 118, 30, 15), 0xF4C21C, 3)
    for i in 0..<2 {
      let tier = Int(i == 0 ? f.tierFrom : f.tierTo)
      let same = f.tierTo == f.tierFrom
      let a = i == 0 ? (same ? 1 : 1 - f.tierK) : f.tierK
      if a < 0.01 || (i == 1 && same) { continue }
      c.save()
      c.translate(197, 337 + (same ? 0 : i == 0 ? -8 * f.tierK : 8 * (1 - f.tierK)))
      c.skew(-0.16)
      text(c, ["Bronze", "Silver", "Gold"][tier], 0, 0, F.label, C.ink, a, 0.5)
      c.restore()
    }
    let blink = max(0, 1 - abs(f.amb - 1.9) / 0.08) + max(0, 1 - abs(f.amb - 6.2) / 0.08)
    let look = f.hop >= 0 ? -sin(.pi * min(1, f.hop / 0.8)) * 5 : 0
    for e in 0..<2 {
      c.save()
      c.translate(112 + Double(e) * 32, 248)
      c.rotate(e == 0 ? -14 : 12)
      c.scale(1, max(0.08, 1 - blink))
      c.fill(oval(-17, -21, 34, 42), C.black)
      star(c, 2, -4 + look, 11, 0xFFE84A)
      c.restore()
    }
    c.circle(128, 270, 11, 0xFF3B2F)
    c.circle(125, 266, 3.5, C.white, 0.8)
    var mouth = Path()
    mouth.move(to: CGPoint(x: 80, y: 288))
    mouth.addQuadCurve(to: CGPoint(x: 124, y: 292), control: CGPoint(x: 102, y: 306))
    c.stroke(mouth, C.black, 4)
    let pop =
      f.hop >= 0 ? exp(-max(0, f.hop - 0.4) * 5) * sin(min(.pi, max(0, f.hop - 0.4) * 9)) : 0
    c.save()
    c.translate(262, 250 - 10 * pop)
    c.scale(1 + 0.2 * pop, 1 + 0.2 * pop)
    var arrow = Path()
    arrow.move(to: CGPoint(x: -26, y: 30))
    arrow.addQuadCurve(to: CGPoint(x: 8, y: -24), control: CGPoint(x: 4, y: 34))
    c.stroke(arrow, C.black, 9)
    c.fill(path([(8, -46), (26, -18), (-10, -20)], close: true), C.black)
    c.restore()
    go(c, 66, 388, f.press == 0 ? f.pressK : 0, f.amb)
  }

  static func drawFlip(_ c: Drawing, _ f: M.Frame) {
    let card = rr(368, 50, 320, 193, 30)
    c.gradient(card, [0xF59BE0, C.pink, C.pinkDeep], [0, 0.5, 1], 368, 50, 688, 243)
    c.save()
    c.clip(card)
    var swirl = Path()
    swirl.move(to: CGPoint(x: 488, y: 250))
    swirl.addCurve(
      to: CGPoint(x: 698, y: 70), control1: CGPoint(x: 528, y: 110),
      control2: CGPoint(x: 648, y: 200))
    c.stroke(swirl, 0xFFB4EC, 22, 0.35)
    c.ring(438, 60, 46, 0xFFB4EC, 22, 0.35)
    c.save()
    c.translate(600, 182)
    c.rotate(-16)
    sprite(c, 6, 0, 0, 96 * 1.2)
    c.restore()
    let ang = f.flipAng
    let turn = ang * .pi / 180
    let lift = sin(turn / 2)
    let face = ang < 90 ? 4 + Int(f.flipFrom) : ang > 270 ? 4 + Int(f.flipTo) : 6
    c.save()
    c.translate(634 - 6 * lift, 170 - 16 * lift)
    c.rotate(9 - 8 * lift)
    c.scale(max(0.02, abs(cos(turn))) * (1 + 0.12 * lift), 1 + 0.12 * lift)
    if lift > 0.02 {
      c.fill(
        rr(-104 * 0.42, -104 * 0.4 + 14 * lift, 104 * 0.84, 104 * 0.86, 16), 0x7A1F66, 0.25 * lift)
    }
    sprite(c, face, 0, 0, 104 * 1.3)
    c.restore()
    c.restore()
    title(c, "Lucky Flip", 390, 92)
    let w0 = text(c, "Remaining:", 392, 126, F.small, C.white, 0.92) + 6
    let w1 = rolling(c, f.remaining, 392 + w0, 126, F.small, C.white, 0.92)
    text(c, "/30", 392 + w0 + w1, 126, F.small, C.white, 0.92)
    go(c, 392, 180, f.press == 1 ? f.pressK : 0, f.amb)
    c.circle(664, 72, 23, C.white)
    let flicker = 1 + 0.07 * sin(tau * 6 * f.amb / M.LOOP) + 0.04 * sin(tau * 13 * f.amb / M.LOOP)
    c.save()
    c.translate(664, 81)
    c.scale(1, flicker)
    var flame = Path()
    flame.move(to: .zero)
    flame.addCurve(
      to: CGPoint(x: -8, y: -22), control1: CGPoint(x: -14, y: 0), control2: CGPoint(x: -15, y: -14)
    )
    flame.addCurve(
      to: CGPoint(x: -2, y: -18), control1: CGPoint(x: -7, y: -14), control2: CGPoint(x: -2, y: -14)
    )
    flame.addCurve(
      to: CGPoint(x: 5, y: -30), control1: CGPoint(x: -2, y: -24), control2: CGPoint(x: 2, y: -28))
    flame.addCurve(
      to: CGPoint(x: 13, y: -8), control1: CGPoint(x: 4, y: -22), control2: CGPoint(x: 14, y: -18))
    flame.addCurve(to: .zero, control1: CGPoint(x: 13, y: -3), control2: CGPoint(x: 8, y: 0))
    flame.closeSubpath()
    c.fill(flame, 0xFF3D2E)
    c.circle(1, -7, 4, C.white, 0.85)
    c.restore()
  }

  static func drawWheel(
    _ c: Drawing, _ x: Double, _ y: Double, _ radius: Double, _ angle: Double, _ blur: Double
  ) {
    c.save()
    c.translate(x, y)
    c.rotate(angle)
    for i in 0..<8 {
      let a = Double(i) * .pi / 4
      var p = Path()
      p.move(to: .zero)
      p.addArc(
        center: .zero, radius: radius, startAngle: .radians(a), endAngle: .radians(a + .pi / 4),
        clockwise: false)
      p.closeSubpath()
      let base = i % 2 == 0 ? C.wheelA : C.wheelB
      func ch(_ hex: Int, _ shift: Int) -> Double { Double(hex >> shift & 255) / 255 }
      let rgb = [16, 8, 0].map { shift in
        M.mix(ch(base, shift), (ch(C.wheelA, shift) + ch(C.wheelB, shift)) / 2, blur)
      }
      c.ctx.fill(
        p, with: .color(Color(.sRGB, red: rgb[0], green: rgb[1], blue: rgb[2], opacity: 1)))
    }
    for i in 0..<8 {
      let a = Double(i) * .pi / 4
      c.line(0, 0, cos(a) * radius, sin(a) * radius, C.white, 3, 0.7 * (1 - blur))
    }
    c.restore()
    c.ring(x, y, radius, C.white, 7)
    c.ring(x, y, radius + 4, C.greenDeep, 3)
    c.circle(x, y, radius * 0.32, C.white)
    c.ring(x, y, radius * 0.32, C.wheelB, 6)
    c.circle(x, y, radius * 0.12, C.wheelB)
    let phase = ((angle.truncatingRemainder(dividingBy: 45) + 45).truncatingRemainder(dividingBy: 45)) / 45
    let flick = 16 * pow(1 - phase, 3) * min(1, blur * 3)
    c.save()
    c.translate(x, y - radius - 8)
    c.rotate(-flick)
    c.translate(-x, -(y - radius - 8))
    let pointer = path(
      [(x - 9, y - radius - 8), (x + 9, y - radius - 8), (x, y - radius + 10)], close: true)
    c.fill(pointer, 0xFF3B2F)
    c.stroke(pointer, C.black, 2.5)
    c.restore()
  }
  static func drawSpin(_ c: Drawing, _ f: M.Frame) {
    let card = rr(368, 258, 320, 192, 30)
    c.gradient(card, [0x8EE33A, C.green, C.greenDeep], [0, 0.6, 1], 368, 258, 368, 450)
    c.save()
    c.clip(card)
    c.ring(428, 460, 90, 0xA6F05A, 20, 0.4)
    drawWheel(c, 626, 398, 62, f.wheel, min(1, f.wheelSpeed * 1.3))
    if f.pop > 0.01 { c.fill(card, C.white, 0.35 * f.pop) }
    c.restore()
    title(c, "Lucky Spin", 390, 298)
    text(c, "Remaining: 0/1", 392, 332, F.small, C.white, 0.92)
    go(c, 392, 387, f.press == 2 ? f.pressK : 0, f.amb)
    let wide = 1 + 0.25 * f.wheelSpeed + 0.25 * f.pop
    for e in 0..<2 {
      c.save()
      c.translate(610 + Double(e) * 32, 346)
      c.scale(wide, wide)
      c.fill(oval(-15, -19, 30, 38), C.white)
      c.stroke(oval(-15, -19, 30, 38), C.black, 3.5)
      c.save()
      c.rotate(e == 0 ? -8 : 8)
      text(c, "$", 4 * f.wheelSpeed, 0, F.dollar, C.black, 1, 0.5)
      c.restore()
      c.restore()
    }
    c.circle(626, 364, 9, 0xFF3B2F)
    c.circle(623.5, 361, 3, C.white, 0.8)
  }

  static func drawProgress(_ c: Drawing, _ f: M.Frame) {
    text(c, "Newbie Tasks", 32, 524, F.h2, C.ink)
    c.fill(rr(505, 507, 183, 34, 17), C.timerBg)
    c.fill(
      path(
        [(527, 503), (515, 525), (525, 525), (519, 545), (537, 519), (527, 519), (533, 503)],
        close: true), C.bolt)
    text(c, "6D:23:21:10", 676, 524, F.label, C.ink, 1, 1)
    c.fill(rr(32, 579, 656, 138, 32), C.cardEdge)
    c.fill(rr(32, 573, 656, 138, 32), C.cardBg)
    c.stroke(rr(32, 573, 656, 138, 32), C.cardEdge, 3)
    c.save()
    c.translate(62, 632)
    c.skew(-0.14)
    let w0 = rolling(c, f.prog, 0, 0, F.count, C.ink)
    text(c, "/14", w0 + 2, 6, F.countSmall, C.ink)
    c.restore()
    c.fill(rr(168, 614, 462, 28, 14), C.line)
    let fw = max(28, LuckyTaskHubTheme.fillAt(f.prog) * 462)
    c.save()
    c.clip(rr(168, 614, fw, 28, 14))
    c.fill(rect(168, 614, fw, 28), 0xFFC21F)
    let march = (f.amb / M.LOOP * 12 * 36).truncatingRemainder(dividingBy: 36)
    var i = -2
    while Double(i) < fw / 18 + 2 {
      let x = 168 + Double(i) * 36 + march
      c.fill(path([(x, 642), (x + 14, 614), (x + 28, 614), (x + 14, 642)], close: true), 0xFF4A2E)
      i += 1
    }
    c.restore()
    let sx = 168 + fw - 4
    let spark = 1 + 0.12 * sin(tau * 4 * f.amb / M.LOOP)
    star(c, sx, 628, 21 * spark, 0xFF3B2F)
    star(c, sx, 628, 21 * spark, C.white, outline: 2.5)
    let stepK = f.prog - floor(f.prog)
    if stepK > 0.6 && stepK < 0.999 {
      let q = (stepK - 0.6) / 0.4
      for i in 0..<6 {
        let a = Double(i) / 6 * tau
        star(
          c, sx + cos(a) * (14 + 30 * q), 628 + sin(a) * (14 + 30 * q), 7 * (1 - q), C.bolt, 1 - q)
      }
    }
    c.circle(316, 627, 30, C.cardEdge)
    c.circle(316, 627, 27, C.white)
    c.fill(rr(304, 621, 24, 18, 3), 0x20B15A)
    c.fill(rr(302, 616, 28, 7, 2), 0x20B15A)
    c.fill(rect(314, 616, 4, 23), C.white)
    text(c, "Rs27.00", 316, 675, F.label, C.ink, 1, 0.5)
    sprite(c, 3, 592, 622, 72)
    text(c, "500", 592, 675, F.label, C.ink, 1, 0.5)
    c.fill(rr(640, 607, 90, 48, 24), C.black)
    var arrow = path([(658, 631), (674, 631)])
    arrow.move(to: CGPoint(x: 668, y: 624))
    arrow.addLine(to: CGPoint(x: 675, y: 631))
    arrow.addLine(to: CGPoint(x: 668, y: 638))
    c.stroke(arrow, C.white, 3.5)
  }

  static func drawOffers(_ c: Drawing) {
    text(c, "Super Offers", 32, 786, F.h2, C.ink)
    text(c, "See All", 688, 786, F.body, C.ink, 1, 1)
    let xs = [33.0, 247, 461, 677]
    let names = ["Tiger Clash", "Zap Rush", "Fruit Tiles"]
    let rewards = ["1,605", "270", "14K"]
    for i in 0..<4 {
      let x = xs[i]
      c.fill(rr(x, 858, 190, 203, 30), C.cardEdge)
      c.fill(rr(x, 852, 190, 203, 30), C.cardBg)
      c.stroke(rr(x, 852, 190, 203, 30), C.cardEdge, 3)
      if i == 3 { continue }
      let ix = x + 95.0
      let iy = 884.0
      c.fill(rr(ix - 51, iy - 51, 102, 102, 24), C.white)
      c.save()
      c.clip(rr(ix - 46, iy - 46, 92, 92, 20))
      sprite(c, 8 + i, ix, iy, 92)
      c.restore()
      text(c, names[i], ix, 960, F.body, C.ink2, 1, 0.5)
      c.fill(rr(x + 14, 984, 162, 46, 23), C.pill)
      sprite(c, 3, x + 42, 1007, 46)
      text(c, rewards[i], x + 113, 1007, F.reward, C.ink, 1, 0.5)
    }
  }
  static func drawInProgress(_ c: Drawing, _ f: M.Frame) {
    c.fill(rr(118, 1090, 560, 64, 32), C.white)
    c.stroke(rr(118, 1090, 560, 64, 32), C.cardEdge, 3)
    let w0 = text(c, "You have ", 144, 1122, F.body, C.ink2)
    let w1 = text(c, "2", 150 + w0, 1122, F.reward, C.ink)
    text(c, " offer(s) in progress", 150 + w0 + w1, 1122, F.body, C.ink2)
    c.circle(62, 1122, 46, C.cardEdge)
    c.circle(62, 1122, 42, C.white)
    drawWheel(c, 62, 1122, 42 * 0.62, -f.wheel * 0.5, 0)
    c.gradient(rr(624, 1060, 126, 96, 24), [0xFFE46A, 0xFFC21F], [0, 1], 0, 1060, 0, 1156)
    c.stroke(rr(624, 1060, 126, 96, 24), 0xF2A500, 3)
    let wig = f.chest >= 0 ? sin(f.chest * 30) * exp(-f.chest * 4) * 12 : 0
    let hopY = f.chest >= 0 ? -10 * sin(.pi * min(1, f.chest / 0.35)) : 0
    c.save()
    c.translate(672, 1094 + hopY)
    c.rotate(wig)
    sprite(c, 7, 0, 0, 74)
    c.restore()
    text(c, "02:48", 672, 1138, F.label, C.ink, 1, 0.5)
  }
  static func drawCasual(_ c: Drawing) {
    text(c, "Casual Games Tasks", 32, 1241, F.h2, C.ink)
    c.fill(rr(32, 1288, 656, 120, 30), C.cardEdge)
    c.fill(rr(32, 1282, 656, 120, 30), C.cardBg)
    c.save()
    c.clip(rr(54, 1302, 80, 80, 18))
    sprite(c, 10, 94, 1342, 80)
    c.restore()
    text(c, "Play Fruit Tiles for 10 minutes", 156, 1328, F.body, C.ink2)
    c.fill(rr(156, 1352, 120, 34, 17), C.pill)
    sprite(c, 3, 178, 1369, 34)
    text(c, "120", 200, 1369, F.label, C.ink)
  }

  static func drawBills(_ c: Drawing, _ f: M.Frame) {
    guard f.billsT >= 0 else { return }
    for i in stride(from: M.BILLS - 1, through: 0, by: -1) {
      let q = (f.billsT - Double(i) * M.BILL_GAP) / M.BILL_FLIGHT
      if q <= 0 || q >= 1 { continue }
      let e = 1 - pow(1 - q, 2.2)
      let cx = 746 + Double(i) * 18
      let cy = 138 - Double(i) * 30
      func point(_ p: Double) -> CGPoint {
        CGPoint(
          x: (1 - p) * (1 - p) * 626 + 2 * (1 - p) * p * cx + p * p * 532,
          y: (1 - p) * (1 - p) * 378 + 2 * (1 - p) * p * cy + p * p * -48)
      }
      let pt = point(e)
      let size = M.mix(88, 44, e) * (1 + 0.25 * sin(.pi * min(1, q * 3)))
      for k in 1...3 {
        let trail = point(max(0, e - Double(k) * 0.05))
        c.circle(trail.x, trail.y, Double(9 - k * 2), C.white, 0.5 - Double(k) * 0.12)
      }
      c.save()
      c.translate(pt.x, pt.y)
      c.rotate(-25 + 340 * e * (i % 2 == 0 ? 1 : -1) * 0.35)
      sprite(c, 11, 0, 0, size)
      c.restore()
    }
  }
  static func drawHeader(_ c: Drawing, _ f: M.Frame) {
    text(c, "Rewards", 32, -48, F.header, C.ink)
    let s = 1 + 0.08 * f.bump
    c.save()
    c.translate(593, -48)
    c.scale(s, s)
    c.fill(rr(-95, -26, 190, 60, 30), C.cardEdge)
    c.fill(rr(-95, -30, 190, 60, 30), C.white)
    if f.bump > 0.01 { c.stroke(rr(-95, -30, 190, 60, 30), C.cash, 4, f.bump) }
    sprite(c, 3, -61, 0, 56)
    if f.balFade > 0.001 && f.balFade < 0.999 {
      c.save()
      c.ctx.opacity *= 1 - f.balFade
      rolling(c, f.balance, 75, 0, F.balance, C.ink, 1, 1, commas: true)
      c.restore()
      c.save()
      c.ctx.opacity *= f.balFade
      text(c, commas(Int(LuckyTaskHubTheme.BALANCE)), 75, 0, F.balance, C.ink, 1, 1)
      c.restore()
    } else if f.balFade >= 0.999 {
      text(c, commas(Int(LuckyTaskHubTheme.BALANCE)), 75, 0, F.balance, C.ink, 1, 1)
    } else {
      rolling(c, f.balance, 75, 0, F.balance, C.ink, 1, 1, commas: true)
    }
    c.restore()
  }

  static func drawScene(_ context: GraphicsContext, _ size: CGSize, _ f: M.Frame) {
    let c = Drawing(context)
    c.fill(rect(0, 0, size.width, size.height), C.page)
    let scale = size.width / LuckyTaskHubTheme.REF_W
    c.scale(scale, scale)
    c.translate(0, LuckyTaskHubTheme.DY)
    c.scale(LuckyTaskHubTheme.S, LuckyTaskHubTheme.S)
    let top = -LuckyTaskHubTheme.DY / LuckyTaskHubTheme.S
    c.gradient(
      rect(0, top, 720, 520 - top), [C.bgTop, C.bgMid, C.page], [0, 0.6, 1], 0, top, 0, 520)
    drawHeader(c, f)
    drawRanking(c, f)
    drawFlip(c, f)
    drawSpin(c, f)
    drawProgress(c, f)
    drawOffers(c)
    drawInProgress(c, f)
    drawCasual(c)
    drawBills(c, f)
  }
}
