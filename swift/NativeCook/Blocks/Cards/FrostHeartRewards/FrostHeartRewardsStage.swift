import SwiftUI

/// FhrAtelierStage.tsx: the two tgfx scenes around the gem (bear, frost skin, progress ring under
/// it; raised paw, mist, caustic, front paw, plaque and spotlight over it) and the turning tag.
struct FrostHeartRewardsStage {
  typealias M = FrostHeartRewardsMotion
  typealias R = FrostHeartRewardsResources
  private let res = R.shared

  struct Box { let x, y, width, height, rotation: Double }
  static let bear = Box(x: 63.5, y: 126.0, width: 230.0, height: 198.0, rotation: 2.0)
  static let heart = Box(x: 58.0, y: 179.0, width: 251.0, height: 201.0, rotation: -0.5)
  static let raisedPaw = Box(x: 249, y: 174, width: 68, height: 83, rotation: 168)
  static let frontPawBox = Box(x: 54.5, y: 288.5, width: 90, height: 94.5, rotation: 0)
  static let slab = (x: 116.0, y: 250.0, width: 143.0, height: 82.0, rotation: -0.36)

  static let AMETHYST: [Double] = [0.58, 0.42, 0, 0, 0, -0.16, 1.12, 0.04, 0, 0, 0.75, -0.25, 0.50, 0, 0, 0, 0, 0, 1, 0]
  static let BEAR: [Double] = [0.956408, 0, 0, 0, 0.019412, 0, 1.032831, 0, 0, 0.024, 0, 0, 1.030621, 0, -0.005808, 0, 0, 0, 1, 0]
  static let PAW: [Double] = [0.570302, 0.130380, 0.057030, 0, 0.073195, 0.012287, 0.323003, 0.008781, 0, 0.079719, 0.087032, 0.052223, 0.635386, 0, 0.045082, 0, 0, 0, 1, 0]
  static let IDENTITY: [Double] = [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0]
  /// a ∘ b for 4×5 colour matrices (b applied first); a nested matrix is folded, never replaced.
  static func compose(_ a: [Double], _ b: [Double]) -> [Double] {
    var out = [Double](repeating: 0, count: 20)
    for i in 0..<4 {
      for j in 0..<5 {
        var v = j == 4 ? a[i * 5 + 4] : 0
        for k in 0..<4 { v += a[i * 5 + k] * b[k * 5 + j] }
        out[i * 5 + j] = v
      }
    }
    return out
  }
  static func colorMatrix(_ m: [Double]) -> ColorMatrix {
    var c = ColorMatrix()
    c.r1 = Float(m[0]); c.r2 = Float(m[1]); c.r3 = Float(m[2]); c.r4 = Float(m[3]); c.r5 = Float(m[4])
    c.g1 = Float(m[5]); c.g2 = Float(m[6]); c.g3 = Float(m[7]); c.g4 = Float(m[8]); c.g5 = Float(m[9])
    c.b1 = Float(m[10]); c.b2 = Float(m[11]); c.b3 = Float(m[12]); c.b4 = Float(m[13]); c.b5 = Float(m[14])
    c.a1 = Float(m[15]); c.a2 = Float(m[16]); c.a3 = Float(m[17]); c.a4 = Float(m[18]); c.a5 = Float(m[19])
    return c
  }
  static let bearLevel = compose(AMETHYST, BEAR)
  static let pawLevel = compose(AMETHYST, IDENTITY)
  static let frontLevel = compose(AMETHYST, PAW)

  static let badge = FrostHeartRewardsSVG.path("M302 154 C326 165 348 191 352 218")
  static let badgeBaseline = FrostHeartRewardsCurve(FrostHeartRewardsSVG.path("M298 158 C322 169 344 195 348 222"))
  static let badgeHighlight = FrostHeartRewardsCurve(FrostHeartRewardsSVG.path("M298 158.65 C322 169.65 344 195.65 348 222.65"))
  static let progressPath = FrostHeartRewardsSVG.path("M175 175 A126 103 0 1 1 174.999 175")
  static let pawClip = FrostHeartRewardsSVG.path("M308 208.5 A21.5 21.5 0 1 0 265 208.5 A21.5 21.5 0 1 0 308 208.5")
  static let wink = FrostHeartRewardsSVG.path("M193 183 Q200 189 207 182")
  static let tagLabels: [Path] = {
    let res = R.shared
    let f = res.font(.semibold, 17.7)
    return [
      res.path("Level 3", f, along: badgeBaseline), res.path("Level 4", f, along: badgeBaseline),
      res.path("Level 3", f, along: badgeHighlight), res.path("Level 4", f, along: badgeHighlight),
    ]
  }()
  static let lumi: (Path, Path, Path, Double, Double) = {
    let res = R.shared
    let f = res.font(.bold, 47)
    let width = res.run("lumi", f).advances.reduce(0, +)
    let cx = slab.x + slab.width / 2
    let tx = cx - width / 2, ty = slab.y + slab.height * 0.70
    return (
      res.path("lumi", f, x: tx + 0.8, baseline: ty + 2), res.path("lumi", f, x: tx, baseline: ty + 0.8),
      res.path("lumi", f, x: tx - 0.4, baseline: ty - 0.5), cx, ty)
  }()

  /// The tgfx value slots, derived from the scene exactly as in useAnimatedReaction.
  struct Slots {
    var time = 0.0, hue = 0.0, wink = 0.0, open = 0.0, push = 1.0, light = 0.0, gem = 0.0, restBear = 1.0
    var tiltX = 0.0, tiltY = 0.0, jelly = 0.0, blur = 0.55, raised = 0.0, tagY = 1.0, first = 1.0, next = 0.0
    var whiteFirst = 1.0, whiteNext = 0.0, metalFirst = 0.0, metalNext = 0.0, mist = 0.62, heart = 1.0
    var polish = 0.0, pulse = 1.0, progress = 0.0, progressAlpha = 0.0, bearScaleX = 1.0, bearScaleY = 1.0, core = 0.0
    var restVisible = true, hugVisible = false, frostVisible = true, lightVisible = false, causticVisible = false
    var waveVisible = false, finalVisible = false, reduced = false, tagAngle = 0.0
    /// tgfx blurs are Gaussian sigmas in device pixels; SwiftUI's radius (≈ sigma) is in points.
    var px = 1.0 / 3
  }
  static func slots(_ sc: M.Scene, tiltX: Double, tiltY: Double, jelly j: Double, reduced: Bool, pixel: Double = 1.0 / 3) -> Slots {
    var s = Slots()
    s.px = pixel
    let keep = 1 - sc.reset
    let light = sc.spotlight * keep
    let open = M.clamp(sc.display) * keep
    s.restVisible = open < 1
    s.hugVisible = open > 0
    let gem = sc.gem * keep
    let frostHalo = 1 - M.ease(M.seg(sc.t, 3.8, 3.9))
    let heartAlpha = max(frostHalo, sc.reset)
    s.frostVisible = heartAlpha > 0
    s.lightVisible = light > 0
    s.causticVisible = gem > 0
    let hue = sc.hue * keep
    s.waveVisible = !reduced && hue > 0 && hue < 1
    s.finalVisible = reduced ? hue > 0 : hue >= 1
    let tagBlend = sc.flip >= 0.5 ? 1.0 : 0.0
    let first = reduced ? 1 - hue : (1 - tagBlend) * keep + sc.reset
    let next = reduced ? hue : tagBlend * keep
    s.time = light <= 0 && gem <= 0 ? 0 : sc.t
    s.hue = hue
    s.wink = (sc.wink + abs(j)) * keep
    s.open = open
    s.push = 1 + sc.push * keep
    s.light = light
    s.gem = gem
    s.restBear = 1 - open
    s.tiltX = reduced ? 0 : tiltX * keep
    s.tiltY = reduced ? 0 : tiltY * keep
    s.jelly = reduced ? 0 : j * keep
    s.blur = reduced ? 0.55 : 0.55 + sin(sc.reset * Double.pi) * 2.2
    s.raised = open * 0.13
    s.first = first
    s.next = next
    s.whiteFirst = first * (1 - light)
    s.whiteNext = next * (1 - light)
    s.metalFirst = first * light
    s.metalNext = next * light
    s.mist = 0.62 * (1 - sc.cut * keep)
    s.heart = heartAlpha
    s.polish = sc.polish * keep
    s.core = (reduced ? 0 : sc.core) * keep
    s.pulse = 1 + sc.pulse * keep
    let progress = M.clamp((sc.balance - 1650) / 75)
    s.progress = progress
    s.progressAlpha = 0.2 * progress * sin(min(1, progress * 3) * Double.pi / 2) * (1 - sc.cut) * keep
    let squash = reduced ? 0 : j * keep
    s.bearScaleX = 1 - 0.03 * squash
    s.bearScaleY = 1 + 0.015 * squash
    s.reduced = reduced
    let angle = sc.flip * 180
    let folded = angle > 90 ? angle - 180 : angle
    s.tagAngle = reduced ? 0 : folded * (1 - sc.reset)
    return s
  }

  // MARK: helpers

  private func about(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ body: (inout GraphicsContext) -> Void) {
    c.translateBy(x: x, y: y); body(&c); c.translateBy(x: -x, y: -y)
  }
  private func hero(_ c: inout GraphicsContext, _ s: Slots) {
    c.translateBy(x: M.HEART.x, y: M.HEART.y)
    c.translateBy(x: s.tiltX, y: s.tiltY)
    c.scaleBy(x: s.push, y: s.push)
    c.translateBy(x: -M.HEART.x, y: -M.HEART.y)
  }
  private func image(_ c: GraphicsContext, _ image: Image, _ box: Box) {
    var g = c
    let cx = box.x + box.width / 2, cy = box.y + box.height / 2
    g.translateBy(x: cx, y: cy)
    g.rotate(by: .radians(box.rotation * Double.pi / 180))
    g.translateBy(x: -cx, y: -cy)
    g.draw(image, in: CGRect(x: box.x, y: box.y, width: box.width, height: box.height))
  }
  /// 1 − smoothstep(a, b, x) sampled into gradient stops.
  private static func smoothStops(_ count: Int = 12) -> [Gradient.Stop] {
    (0...count).map { k in
      let u = Double(k) / Double(count)
      let s = u * u * (3 - 2 * u)
      return Gradient.Stop(color: Color.white.opacity(1 - s), location: u)
    }
  }
  private static let falloff = Gradient(stops: smoothStops())
  /// SPRITE_MASK_SKSL over a rect: alpha 1 − smoothstep(floorY, floorY + fade, y), zero outside the rect.
  private func floorMask(_ c: inout GraphicsContext, _ rect: CGRect, _ floorY: Double, _ fade: Double) {
    c.blendMode = .destinationIn
    c.fill(Path(rect), with: .linearGradient(Self.falloff, startPoint: CGPoint(x: 0, y: floorY), endPoint: CGPoint(x: 0, y: floorY + fade)))
  }

  /// sprite(): the plain sprite, the amethyst wave front, and the final level colour.
  private func sprite(_ c: GraphicsContext, _ img: Image, _ box: Box, _ matrix: [Double], _ level: [Double], _ s: Slots) {
    var base = c
    base.addFilter(.colorMatrix(Self.colorMatrix(matrix)))
    image(base, img, box)
    if s.waveVisible {
      let angle = box.rotation * Double.pi / 180
      let cs = abs(cos(angle)), sn = abs(sin(angle))
      let w = box.width * cs + box.height * sn + 24, h = box.width * sn + box.height * cs + 24
      let bounds = CGRect(x: box.x + box.width / 2 - w / 2, y: box.y + box.height / 2 - h / 2, width: w, height: h)
      let radius = -24 + (230 - -24) * s.hue
      let outer = radius + 18
      if outer > 0 {
        var wave = c
        wave.clip(to: Path(bounds))
        wave.drawLayer { l in
          var colored = l
          colored.addFilter(.colorMatrix(Self.colorMatrix(level)))
          image(colored, img, box)
          // WAVE_MASK_SKSL: 1 − smoothstep(r − 18, r + 18, distance(p, heart)).
          let inner = radius - 18
          let stops: [Gradient.Stop] = (0...16).map { k in
            let d = outer * Double(k) / 16
            let u = M.clamp((d - inner) / (outer - inner))
            return Gradient.Stop(color: Color.white.opacity(1 - u * u * (3 - 2 * u)), location: Double(k) / 16)
          }
          l.blendMode = .destinationIn
          l.fill(Path(bounds), with: .radialGradient(Gradient(stops: stops), center: CGPoint(x: M.HEART.x, y: M.HEART.y), startRadius: 0, endRadius: outer))
        }
      }
    }
    if s.finalVisible {
      var fin = c
      var m = level
      if s.reduced { m[18] = s.hue; m[19] = 0 }
      fin.addFilter(.colorMatrix(Self.colorMatrix(m)))
      image(fin, img, box)
    }
  }
  /// <Mask floor><Group opacity><Blur/>{sprite}</Group></Mask>
  private func maskedSprite(_ c: GraphicsContext, _ rect: CGRect, _ floorY: Double, _ fade: Double, _ opacity: Double, _ s: Slots, _ draw: @escaping (GraphicsContext) -> Void) {
    guard opacity > 0 else { return }
    c.drawLayer { layer in
      var l = layer
      l.clip(to: Path(rect))
      l.drawLayer { group in
        var g = group
        g.opacity = opacity
        g.addFilter(.blur(radius: s.blur * s.px))
        g.drawLayer { inner in draw(inner) }
      }
      floorMask(&l, rect, floorY, fade)
    }
  }

  // MARK: back scene (under the gem)

  func drawBack(_ context: GraphicsContext, _ s: Slots) {
    var c = context
    hero(&c, s)
    if s.hugVisible && s.open > 0 {
      var g = c
      g.opacity = s.open
      g.addFilter(.blur(radius: 22 * s.px))
      g.fill(Path(ellipseIn: CGRect(x: 58, y: 193, width: 238, height: 178)), with: .color(Color.rgba(235, 201, 221, 0.15)))
    }
    var b = c
    b.translateBy(x: 175, y: 245)
    b.scaleBy(x: s.bearScaleX, y: s.bearScaleY)
    b.translateBy(x: -175, y: -245)
    let maskRect = CGRect(x: 30, y: 100, width: 310, height: 310)
    if s.restVisible {
      maskedSprite(b, maskRect, 292, 40, s.restBear, s) { g in
        self.sprite(g, self.res.bearImage, Self.bear, Self.BEAR, Self.bearLevel, s)
      }
    }
    if s.hugVisible {
      maskedSprite(b, maskRect, 310, 40, s.open, s) { g in
        self.sprite(g, self.res.hugImage, Self.bear, Self.BEAR, Self.bearLevel, s)
      }
    }
    if s.wink > 0 {
      var w = b
      w.opacity = min(1, s.wink)
      w.stroke(Self.wink, with: .color(Color(hex: 0x6E3B73)), style: StrokeStyle(lineWidth: 2.6, lineCap: .round))
    }
    if s.frostVisible && s.heart > 0 {
      let rect = CGRect(x: 35, y: 160, width: 295, height: 245)
      c.drawLayer { layer in
        var l = layer
        l.clip(to: Path(rect))
        l.drawLayer { group in
          var g = group
          g.opacity = s.heart
          g.translateBy(x: M.HEART.x, y: M.HEART.y)
          g.scaleBy(x: s.pulse, y: s.pulse)
          g.translateBy(x: -M.HEART.x, y: -M.HEART.y)
          g.addFilter(.blur(radius: s.blur * s.px))
          g.drawLayer { inner in self.image(inner, self.res.heartImage, Self.heart) }
        }
        floorMask(&l, rect, 306, 62)
      }
    }
    if s.progressAlpha > 0 && s.progress > 0 {
      var p = c
      p.opacity = s.progressAlpha
      p.stroke(Self.progressPath.trimmedPath(from: 0, to: s.progress), with: .color(Color(hex: 0xE3E1F7)), style: StrokeStyle(lineWidth: 0.75, lineCap: .round))
    }
  }

  // MARK: front scene (over the gem)

  func drawFront(_ context: GraphicsContext, _ s: Slots) {
    var c = context
    if s.restVisible && s.restBear > 0 {
      var h = c
      hero(&h, s)
      h.translateBy(x: 257, y: 217)
      h.rotate(by: .radians(s.raised))
      h.translateBy(x: -257, y: -217)
      h.clip(to: Self.pawClip)
      h.opacity = s.restBear
      h.drawLayer { layer in
        var l = layer
        l.addFilter(.blur(radius: s.blur * s.px))
        l.drawLayer { inner in self.sprite(inner, self.res.pawImage, Self.raisedPaw, Self.IDENTITY, Self.pawLevel, s) }
      }
    }
    var h = c
    hero(&h, s)
    if s.mist > 0 {
      var m = h
      m.opacity = s.mist
      m.addFilter(.blur(radius: 16 * s.px))
      m.fill(Path(ellipseIn: CGRect(x: 87, y: 317, width: 190, height: 63)), with: .color(Color(hex: 0xB8BED4)))
    }
    if s.causticVisible {
      var k = h
      k.blendMode = .plusLighter
      k.fill(
        Path(CGRect(x: 42, y: 285, width: 286, height: 108)),
        with: .shader(ShaderLibrary.frostHeartRewardsCaustic(.float(s.time), .float(s.gem), .float(s.open), .float(s.polish))))
    }
    c.drawLayer { inner in self.sprite(inner, self.res.frontPawImage, Self.frontPawBox, Self.PAW, Self.frontLevel, s) }
    drawPlaque(c, s)
    if s.lightVisible {
      c.fill(Path(CGRect(x: 0, y: 96, width: 402, height: 390)), with: .shader(ShaderLibrary.frostHeartRewardsSpotlight(.float(s.light))))
    }
  }

  private func drawPlaque(_ context: GraphicsContext, _ s: Slots) {
    let slab = Self.slab
    let cx = slab.x + slab.width / 2, cy = slab.y + slab.height / 2
    context.drawLayer { layer in
      var l = layer
      l.addFilter(.blur(radius: 0.55 * s.px))
      l.drawLayer { p in
        var g = p
        g.translateBy(x: cx, y: cy)
        g.rotate(by: .radians(slab.rotation))
        g.translateBy(x: -cx, y: -cy)
        let lip = Path(roundedRect: CGRect(x: slab.x, y: slab.y + 5, width: slab.width, height: slab.height), cornerSize: CGSize(width: 39, height: 39), style: .circular)
        g.fill(lip, with: .linearGradient(Gradient(colors: [Color(hex: 0xF9B9ED), Color(hex: 0xDE2DC7), Color(hex: 0x76196F)]), startPoint: CGPoint(x: cx, y: slab.y), endPoint: CGPoint(x: cx, y: slab.y + slab.height + 6)))
        let top = Path(roundedRect: CGRect(x: slab.x, y: slab.y, width: slab.width, height: slab.height), cornerSize: CGSize(width: 38, height: 38), style: .circular)
        g.fill(top, with: .linearGradient(Gradient(stops: [.init(color: Color(hex: 0xFD2DDE), location: 0), .init(color: Color(hex: 0xE92ACD), location: 0.46), .init(color: Color(hex: 0xA10CA0), location: 1)]), startPoint: CGPoint(x: cx, y: slab.y), endPoint: CGPoint(x: cx, y: slab.y + slab.height)))
        g.fill(
          Path(CGRect(x: slab.x - 1, y: slab.y - 1, width: slab.width + 2, height: slab.height + 2)),
          with: .shader(ShaderLibrary.frostHeartRewardsSlab(.float(s.time), .float(s.light), .float(s.polish), .float(s.core))))
        let lumi = Self.lumi
        var shadow = g
        shadow.addFilter(.blur(radius: 1.5 * s.px))
        shadow.fill(lumi.0, with: .color(Color(hex: 0x6F256D)))
        g.fill(lumi.1, with: .color(Color(hex: 0xB77AAD)))
        g.fill(lumi.2, with: .linearGradient(Gradient(stops: [.init(color: .white, location: 0), .init(color: Color(hex: 0xFDF3FF), location: 0.55), .init(color: Color(hex: 0xD6BDD8), location: 1)]), startPoint: CGPoint(x: lumi.3, y: lumi.4 - 36), endPoint: CGPoint(x: lumi.3, y: lumi.4 + 1)))
      }
    }
  }

  // MARK: tag (its own view, turned by rotateX)

  func drawTag(_ context: GraphicsContext, _ s: Slots) {
    var c = context
    c.stroke(
      Self.badge,
      with: .shader(ShaderLibrary.frostHeartRewardsTag(.float(s.light), .float(s.time), .float(s.polish), .float(s.next))),
      style: StrokeStyle(lineWidth: 29, lineCap: .round))
    let labels = Self.tagLabels
    let passes: [(Path, Color, Double)] = [
      (labels[0], .white, s.whiteFirst), (labels[1], .white, s.whiteNext),
      (labels[2], Color(hex: 0xB9C8DE), s.metalFirst), (labels[3], .white, s.metalNext),
      (labels[0], Color(hex: 0x263143), s.metalFirst), (labels[1], Color(hex: 0x53535B), s.metalNext),
    ]
    for pass in passes where pass.2 > 0 {
      var g = c
      g.opacity = min(1, pass.2)
      g.fill(pass.0, with: .color(pass.1))
    }
    c.opacity = 1
  }
}
