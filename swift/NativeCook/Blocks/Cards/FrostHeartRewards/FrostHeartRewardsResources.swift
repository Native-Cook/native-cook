import AVFoundation
import CoreText
import ImageIO
import SwiftUI
import UIKit

/// Fonts, glyph runs, SVG paths, sprites and sounds, loaded once outside the frame loop.
final class FrostHeartRewardsResources {
  static let shared = FrostHeartRewardsResources()

  enum Weight: String { case regular = "Regular", medium = "Medium", semibold = "SemiBold", bold = "Bold" }
  struct FontStyle { let size: Double; let weight: Weight; let tracking: Double }

  private var descriptors: [Weight: CTFontDescriptor] = [:]
  private var fonts: [String: CTFont] = [:]
  let bear: CGImage?
  let hug: CGImage?
  let heart: CGImage?
  let paw: CGImage?
  let frontPaw: CGImage?
  let rounded: CGImage?
  let bearImage: Image
  let hugImage: Image
  let heartImage: Image
  let pawImage: Image
  let frontPawImage: Image

  init() {
    for weight in [Weight.regular, .medium, .semibold, .bold] {
      if let url = Bundle.main.url(forResource: "FrostHeartRewards-Inter-\(weight.rawValue)", withExtension: "ttf"),
         let list = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],
         let first = list.first
      {
        descriptors[weight] = first
      }
    }
    func load(_ name: String) -> CGImage? {
      guard let url = Bundle.main.url(forResource: "FrostHeartRewards-\(name)", withExtension: "png"),
            let source = CGImageSourceCreateWithURL(url as CFURL, nil)
      else { return nil }
      return CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
    }
    bear = load("bear")
    hug = load("bear-hug")
    heart = load("heart")
    paw = load("paw")
    frontPaw = load("front-paw")
    rounded = load("rounded-normal")
    func image(_ cg: CGImage?) -> Image {
      guard let cg else { return Image(uiImage: UIImage()) }
      return Image(decorative: cg, scale: 1).interpolation(.medium)
    }
    bearImage = image(bear)
    hugImage = image(hug)
    heartImage = image(heart)
    pawImage = image(paw)
    frontPawImage = image(frontPaw)
  }

  // MARK: Text

  func font(_ weight: Weight, _ size: Double) -> CTFont {
    let key = "\(weight.rawValue)-\(size)"
    if let cached = fonts[key] { return cached }
    let made: CTFont
    if let descriptor = descriptors[weight] {
      made = CTFontCreateWithFontDescriptor(descriptor, size, nil)
    } else {
      made = UIFont.systemFont(ofSize: size, weight: weight == .bold ? .bold : weight == .semibold ? .semibold : .regular) as CTFont
    }
    fonts[key] = made
    return made
  }
  func font(_ style: FontStyle) -> CTFont { font(style.weight, style.size) }

  struct Run {
    let glyphs: [CGGlyph]
    let advances: [Double]
    let bounds: [CGRect]
  }
  func run(_ text: String, _ font: CTFont) -> Run {
    let chars = Array(text.utf16)
    var glyphs = [CGGlyph](repeating: 0, count: chars.count)
    CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
    var sizes = [CGSize](repeating: .zero, count: glyphs.count)
    CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &sizes, glyphs.count)
    var rects = [CGRect](repeating: .zero, count: glyphs.count)
    CTFontGetBoundingRectsForGlyphs(font, .horizontal, glyphs, &rects, glyphs.count)
    return Run(glyphs: glyphs, advances: sizes.map { Double($0.width) }, bounds: rects)
  }
  /// Skia `getGlyphWidths` summed, with tracking between glyphs (fhr-page.ts `textWidth`).
  func width(_ text: String, _ style: FontStyle) -> Double {
    let r = run(text, font(style))
    return r.advances.reduce(0, +) + Double(max(0, r.advances.count - 1)) * style.tracking
  }
  /// Skia `measureText(str).x`. Skia unions its cached glyph bounds, which are whole pixels at the
  /// font size with a one-pixel outset (floor(left) − 1), so the text lands about a point right of
  /// its exact ink edge; measured on the React Native frames to within a third of a point.
  func inkLeft(_ text: String, _ font: CTFont) -> Double {
    let r = run(text, font)
    var pen = 0.0
    var left = Double.infinity
    for i in r.glyphs.indices {
      let b = r.bounds[i]
      if !b.isEmpty && b.width > 0 { left = min(left, pen + floor(Double(b.minX)) - 1) }
      pen += r.advances[i]
    }
    return left.isFinite ? left : 0
  }
  /// Glyph outlines with the pen at `x`, the baseline at `baseline`, each glyph placed by its
  /// own advance plus `tracking` (Skia draws these runs one character at a time, unkerned).
  func path(_ text: String, _ font: CTFont, x: Double, baseline: Double, tracking: Double = 0) -> Path {
    let r = run(text, font)
    let out = CGMutablePath()
    var pen = x
    for i in r.glyphs.indices {
      var t = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: pen, ty: baseline)
      if let glyph = CTFontCreatePathForGlyph(font, r.glyphs[i], &t) { out.addPath(glyph) }
      pen += r.advances[i] + tracking
    }
    return Path(out)
  }
  /// tgfx `TextPath`: each glyph sits on the curve at the middle of its advance, turned to its tangent.
  /// Skia's glyphs on iOS cover about 0.19 px more on every edge than CoreGraphics fills of the
  /// same outlines (measured on the React Native frames: ink/perimeter, all sizes and weights).
  static let skiaDilation = 0.38 / Double(UIScreen.main.scale)
  private var skiaCache: [String: Path] = [:]
  /// `path(...)` widened like Skia's text, cached (the strings are few and mostly static).
  func skiaPath(_ text: String, _ font: CTFont, x: Double, baseline: Double, tracking: Double = 0) -> Path {
    let key = "\(text)|\(CTFontCopyPostScriptName(font))|\(CTFontGetSize(font))|\(x)|\(baseline)|\(tracking)"
    if let cached = skiaCache[key] { return cached }
    let p = path(text, font, x: x, baseline: baseline, tracking: tracking)
    let wide = p.union(p.strokedPath(StrokeStyle(lineWidth: Self.skiaDilation, lineJoin: .round)))
    if skiaCache.count > 400 { skiaCache.removeAll() }
    skiaCache[key] = wide
    return wide
  }
  /// tgfx shapes its text (kerning applies, unlike Skia's one-character runs), so the advances
  /// come from a CoreText line.
  func path(_ text: String, _ font: CTFont, along curve: FrostHeartRewardsCurve) -> Path {
    let attributed = NSAttributedString(string: text, attributes: [NSAttributedString.Key(kCTFontAttributeName as String): font])
    let line = CTLineCreateWithAttributedString(attributed)
    let out = CGMutablePath()
    for ctRun in (CTLineGetGlyphRuns(line) as? [CTRun]) ?? [] {
      let count = CTRunGetGlyphCount(ctRun)
      var glyphs = [CGGlyph](repeating: 0, count: count)
      var positions = [CGPoint](repeating: .zero, count: count)
      var sizes = [CGSize](repeating: .zero, count: count)
      CTRunGetGlyphs(ctRun, CFRange(location: 0, length: 0), &glyphs)
      CTRunGetPositions(ctRun, CFRange(location: 0, length: 0), &positions)
      CTRunGetAdvances(ctRun, CFRange(location: 0, length: 0), &sizes)
      for i in 0..<count {
        let half = Double(sizes[i].width) / 2
        let mid = Double(positions[i].x) + half
        guard mid <= curve.length, let sample = curve.sample(at: mid) else { continue }
      let angle = atan2(sample.ty, sample.tx)
      var t = CGAffineTransform(translationX: sample.x, y: sample.y)
        .rotated(by: angle)
        .translatedBy(x: -half, y: 0)
        .scaledBy(x: 1, y: -1)
        if let glyph = CTFontCreatePathForGlyph(font, glyphs[i], &t) { out.addPath(glyph) }
      }
    }
    return Path(out)
  }

  // MARK: Sounds

  private var players: [String: AVAudioPlayer] = [:]
  private var audioReady = false
  static let volume: [String: Float] = [
    "claim0": 0.75, "claim1": 0.75, "claim2": 0.75, "claim3": 0.75,
    "frost": 0.36, "tinkle": 0.55, "level": 0.65, "cut-ting": 0.52,
  ]
  func prepareSounds() {
    guard !audioReady else { return }
    audioReady = true
    try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
    for (name, volume) in Self.volume {
      if let url = Bundle.main.url(forResource: "FrostHeartRewards-\(name)", withExtension: "wav"),
         let player = try? AVAudioPlayer(contentsOf: url)
      {
        player.volume = volume
        player.prepareToPlay()
        players[name] = player
      }
    }
  }
  func play(_ name: String) {
    prepareSounds()
    guard let player = players[name] else { return }
    player.currentTime = 0
    player.play()
  }
  func stopSounds() {
    for player in players.values {
      player.stop()
      player.currentTime = 0
    }
  }
}

/// A flattened path for `TextPath` placement (arc length, point and tangent).
struct FrostHeartRewardsCurve {
  private var points: [CGPoint] = []
  private var lengths: [Double] = []
  var length: Double { lengths.last ?? 0 }
  init(_ path: Path) {
    var last = CGPoint.zero
    var first = CGPoint.zero
    func add(_ p: CGPoint) {
      if points.isEmpty { points.append(p); lengths.append(0); return }
      let prev = points[points.count - 1]
      points.append(p)
      lengths.append(lengths[lengths.count - 1] + hypot(Double(p.x - prev.x), Double(p.y - prev.y)))
    }
    path.forEach { element in
      switch element {
      case .move(let p): last = p; first = p; if points.isEmpty { add(p) }
      case .line(let p): add(p); last = p
      case .quadCurve(let p, let c):
        for k in 1...32 {
          let t = CGFloat(k) / 32
          let u = 1 - t
          add(CGPoint(x: u * u * last.x + 2 * u * t * c.x + t * t * p.x, y: u * u * last.y + 2 * u * t * c.y + t * t * p.y))
        }
        last = p
      case .curve(let p, let c1, let c2):
        for k in 1...64 {
          let t = CGFloat(k) / 64
          let u = 1 - t
          let x = u * u * u * last.x + 3 * u * u * t * c1.x + 3 * u * t * t * c2.x + t * t * t * p.x
          let y = u * u * u * last.y + 3 * u * u * t * c1.y + 3 * u * t * t * c2.y + t * t * t * p.y
          add(CGPoint(x: x, y: y))
        }
        last = p
      case .closeSubpath: add(first); last = first
      }
    }
  }
  func sample(at distance: Double) -> (x: Double, y: Double, tx: Double, ty: Double)? {
    guard points.count > 1 else { return nil }
    var i = 1
    while i < lengths.count - 1 && lengths[i] < distance { i += 1 }
    let a = points[i - 1], b = points[i]
    let span = max(1e-9, lengths[i] - lengths[i - 1])
    let f = min(max((distance - lengths[i - 1]) / span, 0), 1)
    let dx = Double(b.x - a.x), dy = Double(b.y - a.y)
    let len = max(1e-9, hypot(dx, dy))
    return (Double(a.x) + dx * f, Double(a.y) + dy * f, dx / len, dy / len)
  }
}

/// Absolute SVG path commands M L Q C A Z, enough for the block's icons and curves.
enum FrostHeartRewardsSVG {
  static func path(_ string: String) -> Path {
    var tokens: [String] = []
    var current = ""
    for ch in string {
      if ch.isLetter {
        if !current.isEmpty { tokens.append(current); current = "" }
        tokens.append(String(ch))
      } else if ch == " " || ch == "," {
        if !current.isEmpty { tokens.append(current); current = "" }
      } else if ch == "-" && !current.isEmpty && !current.hasSuffix("e") {
        tokens.append(current); current = "-"
      } else {
        current.append(ch)
      }
    }
    if !current.isEmpty { tokens.append(current) }
    var path = Path()
    var i = 0
    var command = "M"
    var pen = CGPoint.zero
    var start = CGPoint.zero
    func number() -> Double { defer { i += 1 }; return Double(tokens[i]) ?? 0 }
    while i < tokens.count {
      if let first = tokens[i].first, first.isLetter { command = tokens[i]; i += 1; if command == "Z" || command == "z" { path.closeSubpath(); pen = start; continue } }
      guard i < tokens.count else { break }
      switch command {
      case "M":
        pen = CGPoint(x: number(), y: number()); start = pen
        path.move(to: pen)
        command = "L"
      case "L":
        pen = CGPoint(x: number(), y: number())
        path.addLine(to: pen)
      case "Q":
        let c = CGPoint(x: number(), y: number())
        pen = CGPoint(x: number(), y: number())
        path.addQuadCurve(to: pen, control: c)
      case "C":
        let c1 = CGPoint(x: number(), y: number())
        let c2 = CGPoint(x: number(), y: number())
        pen = CGPoint(x: number(), y: number())
        path.addCurve(to: pen, control1: c1, control2: c2)
      case "A":
        let rx = number(), ry = number(), phi = number() * .pi / 180
        let large = number() != 0, sweep = number() != 0
        let end = CGPoint(x: number(), y: number())
        arc(&path, from: pen, to: end, rx: rx, ry: ry, phi: phi, large: large, sweep: sweep)
        pen = end
      default:
        i += 1
      }
    }
    return path
  }

  /// SVG endpoint arc → centre parameterisation → cubic segments of at most 90°.
  private static func arc(_ path: inout Path, from p0: CGPoint, to p1: CGPoint, rx rxIn: Double, ry ryIn: Double, phi: Double, large: Bool, sweep: Bool) {
    var rx = abs(rxIn), ry = abs(ryIn)
    if rx == 0 || ry == 0 { path.addLine(to: p1); return }
    let cphi = cos(phi), sphi = sin(phi)
    let dx = (Double(p0.x) - Double(p1.x)) / 2, dy = (Double(p0.y) - Double(p1.y)) / 2
    let x1 = cphi * dx + sphi * dy, y1 = -sphi * dx + cphi * dy
    let lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
    if lambda > 1 { rx *= sqrt(lambda); ry *= sqrt(lambda) }
    let num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
    let den = rx * rx * y1 * y1 + ry * ry * x1 * x1
    var coef = sqrt(max(0, num / den))
    if large == sweep { coef = -coef }
    let cxp = coef * rx * y1 / ry, cyp = -coef * ry * x1 / rx
    let cx = cphi * cxp - sphi * cyp + (Double(p0.x) + Double(p1.x)) / 2
    let cy = sphi * cxp + cphi * cyp + (Double(p0.y) + Double(p1.y)) / 2
    func angle(_ ux: Double, _ uy: Double, _ vx: Double, _ vy: Double) -> Double {
      let a = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
      return a
    }
    let theta1 = angle(1, 0, (x1 - cxp) / rx, (y1 - cyp) / ry)
    var delta = angle((x1 - cxp) / rx, (y1 - cyp) / ry, (-x1 - cxp) / rx, (-y1 - cyp) / ry)
    if !sweep && delta > 0 { delta -= 2 * .pi }
    if sweep && delta < 0 { delta += 2 * .pi }
    let segments = max(1, Int(ceil(abs(delta) / (.pi / 2) - 1e-9)))
    let step = delta / Double(segments)
    let k = 4.0 / 3.0 * tan(step / 4)
    func point(_ t: Double) -> (Double, Double) {
      let x = rx * cos(t), y = ry * sin(t)
      return (cx + cphi * x - sphi * y, cy + sphi * x + cphi * y)
    }
    func derivative(_ t: Double) -> (Double, Double) {
      let x = -rx * sin(t), y = ry * cos(t)
      return (cphi * x - sphi * y, sphi * x + cphi * y)
    }
    var t = theta1
    for s in 0..<segments {
      let t2 = t + step
      let a = point(t), b = point(t2)
      let da = derivative(t), db = derivative(t2)
      let end = s == segments - 1 ? p1 : CGPoint(x: b.0, y: b.1)
      path.addCurve(
        to: end,
        control1: CGPoint(x: a.0 + k * da.0, y: a.1 + k * da.1),
        control2: CGPoint(x: b.0 - k * db.0, y: b.1 - k * db.1))
      t = t2
    }
  }
}
