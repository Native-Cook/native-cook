import CoreText
import ImageIO
import SwiftUI
import UIKit
import simd

enum FrostChestOpeningTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  enum COPY {
    static let balance = "2,953"
    static let eyebrow = "A BIG SURPRISE"
    static let title = "AWAITS!"
    static let drop = "1X RARE DROP"
    static let hint1 = "HOLD TO OPEN"
    static let hint2 = "CHEST!"
    static let plate1 = "RARE"
    static let plate2 = "BOX"
    static let reward1 = "WINTER SPECIAL"
    static let reward2 = "COSTUME"
    static let rarity = "SUPER RARE"
    static let others = "Other Rewards"
    static let items = ["XP Boost", "Winter Boots", "Frozen Shrine"]
    static let claim = "CLAIM"
  }
  enum C {
    static let skyTop = 0x8cb4ef
    static let skyMid = 0xc3d7f6
    static let skyLow = 0xeaf1fb
    static let floor = 0xf6f8fd
    static let rayLight = 0xf4f8ff
    static let rayDeep = 0x6f9be6
    static let frostTop = 0xd9e5f8
    static let frostLow = 0xe9eff9
    static let ink = 0x3a4766
    static let inkSoft = 0x4c5a7a
    static let hint = 0x97a4bf
    static let title = 0xffffff
    static let titleGlow = 0x9ccaff
    static let deepBlue = 0x0e5c9e
    static let glass = 0xffffff
    static let cyan = 0x37d6ff
    static let cyanDeep = 0x1597ea
    static let orbDeep = 0x0b6fd6
    static let orbLight = 0x6fe3ff
    static let streak = 0x22bdf0
    static let claimTop = 0x55c4ff
    static let claimMid = 0x1a8cff
    static let claimLow = 0x0b62e6
    static let claimInk = 0xeaf7ff
    static let plus = 0x1d8bf7
    static let starEdge = 0x94eefd
    static let starEdgeSoft = 0xc2f1fb
    static let chip = 0x5fd2f4
  }
  enum L {
    static let backX = 44.5
    static let backY = 45.0
    static let backD = 56.0
    static let coinX = 237.7
    static let coinY = 21.0
    static let coinW = 149.0
    static let coinH = 47.0
    static let coinIcon = 31.0
    static let plusD = 38.0
    static let balanceBase = 52.0
    static let balanceW = 49.0
    static let eyebrowBase = 213.5
    static let eyebrowW = 262.0
    static let titleBase = 275.7
    static let titleW = 298.0
    static let chestCX = 203.5
    static let chestCY = 434.4
    static let chestW = 329.0
    static let chestSY = 1.03
    static let openCX = 198.5
    static let openCY = 408.0
    static let openW = 344.0
    static let mouthY = 400.0
    static let rayCX = 201.0
    static let rayCY = 420.0
    static let pillX = 92.0
    static let pillY = 583.7
    static let pillW = 219.0
    static let pillH = 76.0
    static let pillIcon = 27.0
    static let pillTextX = 152.0
    static let dropBase = 630.7
    static let dropW = 136.0
    static let handY = 708.0
    static let handSize = 28.0
    static let hintBase = 752.0
    static let hintBase2 = 774.5
    static let hintW = 126.0
    static let hintW2 = 62.0
    static let cardCX = 201.5
    static let cardCY = 226.0
    static let cardW = 219.0
    static let cardH = 300.0
    static let cardR = 24.0
    static let jacketCX = 206.0
    static let jacketCY = 185.4
    static let jacketW = 183.0
    static let rewardBase = 300.4
    static let rewardBase2 = 320.0
    static let rewardW = 159.0
    static let rewardW2 = 94.0
    static let rarityCY = 354.0
    static let rarityW = 102.0
    static let rarityH = 28.0
    static let rarityTextW = 68.0
    static let panelX = 12.0
    static let panelY = 568.0
    static let panelW = 378.0
    static let panelH = 171.0
    static let panelR = 22.0
    static let othersBase = 596.6
    static let othersW = 114.0
    static let tileCY = 655.0
    static let tileD = 81.0
    static let tileR = 22.0
    static let tileXs = [87, 201, 315]
    static let tileArt = 64.0
    static let itemBase = 716.4
    static let itemW = [48.5, 72, 78]
    static let claimX = 13.0
    static let claimY = 759.0
    static let claimW = 376.0
    static let claimH = 69.0
    static let claimBase = 802.0
    static let claimTextW = 56.0
  }
}

// Images and fitted glyph runs are decoded / rasterized once, never inside the per-frame loop.
final class FrostChestOpeningResources {
  static let shared = FrostChestOpeningResources()
  struct Label {
    let image: Image
    let width: Double
    let size: CGSize
    let baseline: Double
    let fontSize: Double
  }
  private var images: [String: UIImage] = [:]
  private var labels: [String: Label] = [:]
  private var cards: [String: UIImage] = [:]
  init() {
    for weight in ["Black", "ExtraBold", "Bold", "SemiBold", "Regular"] {
      if let url = Bundle.main.url(
        forResource: "FrostChestOpening-Poppins-\(weight)", withExtension: "ttf")
      {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
    for name in [
      "chest-closed", "chest-open", "card-front", "card-back", "floor", "backdrop", "jacket",
      "xp-boost", "winter-boots", "frozen-shrine", "coin", "crystal",
    ] {
      let ext = name == "backdrop" ? "jpg" : "webp"
      if let url = Bundle.main.url(forResource: "FrostChestOpening-\(name)", withExtension: ext),
        let source = CGImageSourceCreateWithURL(url as CFURL, nil),
        let cg = CGImageSourceCreateImageAtIndex(
          source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
      {
        images[name] = UIImage(cgImage: cg)
      }
    }
  }
  func uiImage(_ name: String) -> UIImage { images[name] ?? UIImage() }
  func image(_ name: String) -> Image { Image(uiImage: uiImage(name)) }
  func fit(_ string: String, _ weight: String, _ target: Double) -> Label {
    let key = "\(string)|\(weight)|\(target)"
    if let cached = labels[key] { return cached }
    let probe = UIFont(name: "Poppins-\(weight)", size: 100) ?? UIFont.systemFont(ofSize: 100)
    func width(_ font: UIFont) -> Double {
      let line = CTLineCreateWithAttributedString(
        NSAttributedString(string: string, attributes: [.font: font, .kern: 0])
          as CFAttributedString)
      return CTLineGetTypographicBounds(line, nil, nil, nil)
    }
    let font = probe.withSize(100 * target / max(0.001, width(probe)))
    let w = width(font)
    let pad = 12.0
    let sz = CGSize(
      width: ceil(w + pad * 2), height: ceil(font.ascender - font.descender + pad * 2))
    let format = UIGraphicsImageRendererFormat()
    format.scale = UIScreen.main.scale
    let baseline = pad + font.ascender
    let bitmap = UIGraphicsImageRenderer(size: sz, format: format).image { renderer in
      let cg = renderer.cgContext
      cg.textMatrix = .identity
      cg.translateBy(x: pad, y: baseline)
      cg.scaleBy(x: 1, y: -1)
      let line = CTLineCreateWithAttributedString(
        NSAttributedString(
          string: string, attributes: [.font: font, .kern: 0, .foregroundColor: UIColor.white])
          as CFAttributedString)
      cg.textPosition = .zero
      CTLineDraw(line, cg)
    }
    let result = Label(
      image: Image(uiImage: bitmap), width: w, size: sz, baseline: baseline,
      fontSize: font.pointSize)
    labels[key] = result
    return result
  }
  // The static card front is baked at device resolution, including its measured Poppins text.
  // Different screen aspect ratios keep the reference's independently scaled y positions.
  func card(_ front: Bool, _ sx: Double, _ sy: Double) -> UIImage {
    let key = "\(front)|\(sx)|\(sy)"
    if let cached = cards[key] { return cached }
    let w = 219 * sx
    let h = 300 * sx
    let pad = 48 * sx
    let format = UIGraphicsImageRendererFormat()
    format.scale = UIScreen.main.scale
    let bitmap = UIGraphicsImageRenderer(
      size: CGSize(width: w + pad * 2, height: h + pad * 2), format: format
    ).image { renderer in
      let c = renderer.cgContext
      c.translateBy(x: pad + w / 2, y: pad + h / 2)
      let rect = CGRect(x: -w / 2, y: -h / 2, width: w, height: h)
      c.saveGState()
      c.setShadow(
        offset: .zero, blur: (front ? 10 : 14) * sx,
        color: UIColor.white.withAlphaComponent(front ? 0.35 : 0.7).cgColor)
      c.setFillColor(UIColor.white.withAlphaComponent(front ? 0.35 : 0.7).cgColor)
      c.addPath(UIBezierPath(roundedRect: rect, cornerRadius: 24 * sx).cgPath)
      c.fillPath()
      c.restoreGState()
      uiImage(front ? "card-front" : "card-back").draw(
        in: rect, blendMode: .normal, alpha: front ? 0.82 : 1)
      guard front else { return }
      func oy(_ y: Double) -> Double { (y - 226) * sy }
      let jacket = uiImage("jacket")
      let jw = 183 * sx
      let jh = jw * jacket.size.height / max(1, jacket.size.width)
      jacket.draw(
        in: CGRect(x: (206 - 201.5) * sx - jw / 2, y: oy(185.4) - jh / 2, width: jw, height: jh))
      let star = FrostChestOpeningArt.starPath
      c.saveGState()
      c.translateBy(x: (135 - 201.5) * sx, y: oy(135))
      c.scaleBy(x: sx, y: sx)
      c.setFillColor(FrostChestOpeningArt.uiColor(0x94eefd, 1).cgColor)
      c.addPath(star.cgPath)
      c.fillPath()
      c.scaleBy(x: 0.58, y: 0.58)
      c.setFillColor(UIColor.white.cgColor)
      c.addPath(star.cgPath)
      c.fillPath()
      c.restoreGState()
      func label(_ string: String, _ weight: String, _ target: Double, _ base: Double, _ color: Int)
      {
        let probe = UIFont(name: "Poppins-\(weight)", size: 100)!
        let line = CTLineCreateWithAttributedString(
          NSAttributedString(string: string, attributes: [.font: probe, .kern: 0])
            as CFAttributedString)
        let fs = 100 * target / CTLineGetTypographicBounds(line, nil, nil, nil)
        let font = probe.withSize(fs)
        let attrs: [NSAttributedString.Key: Any] = [
          .font: font, .kern: 0, .foregroundColor: FrostChestOpeningArt.uiColor(color, 1),
        ]
        let fitted = CTLineCreateWithAttributedString(
          NSAttributedString(string: string, attributes: attrs) as CFAttributedString)
        let tw = CTLineGetTypographicBounds(fitted, nil, nil, nil)
        c.saveGState()
        c.translateBy(x: -tw / 2, y: base)
        c.scaleBy(x: 1, y: -1)
        c.textMatrix = .identity
        c.textPosition = .zero
        CTLineDraw(fitted, c)
        c.restoreGState()
      }
      label("WINTER SPECIAL", "ExtraBold", 159 * sx, oy(300.4), 0x0e5c9e)
      label("COSTUME", "ExtraBold", 94 * sx, oy(320), 0x0e5c9e)
      let cr = CGRect(x: -51 * sx, y: oy(354) - 14 * sx, width: 102 * sx, height: 28 * sx)
      c.saveGState()
      c.setShadow(
        offset: CGSize(width: 0, height: 2), blur: 4,
        color: FrostChestOpeningArt.uiColor(0x8aa6da, 0.25).cgColor)
      c.setFillColor(UIColor.white.withAlphaComponent(0.78).cgColor)
      c.addPath(UIBezierPath(roundedRect: cr, cornerRadius: 14 * sx).cgPath)
      c.fillPath()
      c.restoreGState()
      let rare = fit("SUPER RARE", "Bold", 68 * sx)
      let start = -(9 * sx + 3 * sx + rare.width) / 2
      let icon = uiImage("crystal")
      let iw = 9 * sx
      let ih = iw * icon.size.height / max(1, icon.size.width)
      icon.draw(in: CGRect(x: start, y: oy(354) - ih / 2, width: iw, height: ih))
      // Move the centred text to the right of the small crystal.
      c.saveGState()
      c.translateBy(x: start + 12 * sx + rare.width / 2, y: 0)
      label("SUPER RARE", "Bold", 68 * sx, oy(354) + rare.fontSize * 0.36, 0x4c5a7a)
      c.restoreGState()
    }
    cards[key] = bitmap
    return bitmap
  }
}

struct FrostChestOpeningArt {
  typealias M = FrostChestOpeningMotion
  typealias F = M.Frame
  typealias L = FrostChestOpeningTheme.L
  typealias C = FrostChestOpeningTheme.C
  typealias Copy = FrostChestOpeningTheme.COPY
  let size: CGSize
  let top: Double
  let resources = FrostChestOpeningResources.shared
  var sx: Double { size.width / 402 }
  var sy: Double { size.height / 874 }
  var bounds: CGRect { CGRect(origin: .zero, size: size) }
  func X(_ n: Double) -> Double { n * sx }
  func Y(_ n: Double) -> Double { n * sy }
  func S(_ n: Double) -> Double { n * sx }
  static func uiColor(_ n: Int, _ a: Double) -> UIColor {
    UIColor(
      red: Double((n >> 16) & 255) / 255, green: Double((n >> 8) & 255) / 255,
      blue: Double(n & 255) / 255, alpha: a)
  }
  func color(_ n: Int, _ a: Double = 1) -> Color { Color(Self.uiColor(n, a)) }
  func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double = 0) -> Path {
    Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r)
  }
  func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
  }
  func radial(_ stops: [(Int, Double, Double)], _ x: Double, _ y: Double, _ radius: Double)
    -> GraphicsContext.Shading
  {
    .radialGradient(
      Gradient(stops: stops.map { .init(color: color($0.0, $0.1), location: $0.2) }),
      center: CGPoint(x: x, y: y), startRadius: 0, endRadius: radius)
  }
  func linear(_ stops: [(Int, Double, Double)], _ x: Double, _ y: Double, _ endY: Double)
    -> GraphicsContext.Shading
  {
    .linearGradient(
      Gradient(stops: stops.map { .init(color: color($0.0, $0.1), location: $0.2) }),
      startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x, y: endY))
  }
  func matrix(
    _ r: Double, _ g: Double, _ b: Double, _ lr: Double = 0, _ lg: Double = 0, _ lb: Double = 0
  ) -> ColorMatrix {
    var m = ColorMatrix()
    m.r1 = Float(r)
    m.g2 = Float(g)
    m.b3 = Float(b)
    m.a4 = 1
    m.r5 = Float(lr)
    m.g5 = Float(lg)
    m.b5 = Float(lb)
    return m
  }
  func drawImage(
    _ c: GraphicsContext, _ name: String, _ cx: Double, _ cy: Double, _ w: Double, _ a: Double = 1,
    _ stretch: Double = 1
  ) {
    let img = resources.uiImage(name)
    let h = w * img.size.height / max(1, img.size.width) * stretch
    var c = c
    c.opacity *= a
    c.draw(resources.image(name), in: CGRect(x: cx - w / 2, y: cy - h / 2, width: w, height: h))
  }
  func text(
    _ c: GraphicsContext, _ str: String, _ weight: String, _ width: Double, _ cx: Double,
    _ base: Double, _ ink: Int, _ a: Double = 1
  ) {
    guard a > 0.003 else { return }
    let l = resources.fit(str, weight, width)
    var image = c.resolve(l.image)
    image.shading = .color(color(ink))
    var c = c
    c.opacity *= a
    c.draw(
      image,
      in: CGRect(
        x: cx - l.width / 2 - 12, y: base - l.baseline, width: l.size.width, height: l.size.height))
  }
  func glass(
    _ c: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double,
    _ a: Double, _ fill: Double = 0.62
  ) {
    guard a > 0.003 else { return }
    var shadow = c
    shadow.addFilter(.blur(radius: 10))
    shadow.fill(rect(x, y + 6, w, h, r), with: .color(color(0x7d9bd6, 0.22 * a)))
    c.fill(
      rect(x, y, w, h, r),
      with: linear(
        [
          (0xffffff, (fill + 0.18) * a, 0), (0xf2f6fd, fill * a, 0.55),
          (0xe6eefb, (fill - 0.06) * a, 1),
        ], x, y, y + h))
    c.stroke(
      rect(x + 0.75, y + 0.75, w - 1.5, h - 1.5, r - 0.75), with: .color(color(0xffffff, 0.95 * a)),
      lineWidth: 1.5)
  }
  static let starPath: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 0, y: -10))
    p.addQuadCurve(to: CGPoint(x: 10, y: 0), control: CGPoint(x: 1.8, y: -1.8))
    p.addQuadCurve(to: CGPoint(x: 0, y: 10), control: CGPoint(x: 1.8, y: 1.8))
    p.addQuadCurve(to: CGPoint(x: -10, y: 0), control: CGPoint(x: -1.8, y: 1.8))
    p.addQuadCurve(to: CGPoint(x: 0, y: -10), control: CGPoint(x: -1.8, y: -1.8))
    p.closeSubpath()
    return p
  }()
  func star(
    _ c: GraphicsContext, _ x: Double, _ y: Double, _ size: Double, _ a: Double, _ kind: Int = 0
  ) {
    guard a > 0.01 && size > 0.3 else { return }
    var c = c
    c.translateBy(x: x, y: y)
    c.scaleBy(x: size / 20, y: size / 20)
    c.fill(
      Self.starPath,
      with: .color(color(kind == 2 ? C.starEdgeSoft : C.starEdge, kind == 1 ? 0.45 * a : a)))
    let k = kind == 1 ? 0.78 : 0.58
    c.scaleBy(x: k, y: k)
    c.fill(Self.starPath, with: .color(color(0xffffff, a)))
  }
  func drawSky(_ c: GraphicsContext, _ f: F, _ bright: Double? = nil) {
    let sky = bright ?? f.sky
    let args: [Shader.Argument] = [
      .float2(size), .float2(X(L.rayCX), Y(L.rayCY)), .float(0.4276), .float(sky),
    ]
    c.fill(
      Path(bounds),
      with: .shader(
        Shader(
          function: ShaderFunction(library: .default, name: "FrostChestOpeningSky"), arguments: args
        )))
    let floor = resources.uiImage("floor")
    let fh = size.width * floor.size.height / max(1, floor.size.width)
    var fl = c
    fl.opacity = 0.92
    fl.draw(
      resources.image("floor"), in: CGRect(x: 0, y: size.height - fh, width: size.width, height: fh)
    )
    c.fill(
      rect(0, size.height - fh, size.width, fh),
      with: linear([(0xf6f9ff, 0, 0), (0xf6f9ff, 0.45, 1)], 0, size.height - fh, size.height))
    if sky > 0.01 {
      c.fill(
        rect(0, Y(L.rayCY), size.width, size.height - Y(L.rayCY)),
        with: .shader(
          Shader(
            function: ShaderFunction(library: .default, name: "FrostChestOpeningFloorLight"),
            arguments: args)))
    }
  }
  func drawFrost(_ c: GraphicsContext, _ f: F) {
    guard f.frost > 0.003 else { return }
    var c = c
    c.opacity *= f.frost
    c.drawLayer { c in
      let breathe = 1.03 + 0.012 * sin(f.idle / M.LOOP * Double.pi * 4)
      let dw = size.width * breathe
      let dh = size.height * breathe
      c.draw(
        resources.image("backdrop"),
        in: CGRect(x: (size.width - dw) / 2, y: (size.height - dh) / 2, width: dw, height: dh))
      c.fill(
        Path(bounds),
        with: radial(
          [(0xffffff, 0.7, 0), (0xf0f6ff, 0.25, 0.55), (0xf0f6ff, 0, 1)], X(L.openCX),
          Y(L.mouthY - 40), S(330)))
    }
  }
  func drawChestGlow(_ c: GraphicsContext, _ f: F) {
    let a = (0.4 + 0.6 * f.charge) * (1 - f.reveal)
    let r = S(220 + 90 * f.charge)
    let x = X(L.chestCX + 3)
    let y = Y(375)
    guard a > 0.01 else { return }
    c.fill(
      circle(x, y, r),
      with: radial([(0xffffff, 0.85 * a, 0), (0xd2f4ff, 0.4 * a, 0.42), (0xd2f4ff, 0, 1)], x, y, r))
  }
  func drawClosedChest(_ c: GraphicsContext, _ f: F) {
    let back =
      1 + 0.04 * (1 - M.easeOut(M.seg(f.t, M.T.swapBack, M.T.restIn[1])))
      * (f.t >= M.T.swapBack ? 1 : 0)
    let cx = X(L.chestCX)
    let cy = Y(L.chestCY)
    let w = S(L.chestW) * back
    drawImage(c, "chest-closed", cx, cy, w, 1, L.chestSY)
    if f.charge > 0.01 {
      var lit = c
      lit.opacity *= f.charge
      lit.addFilter(.colorMatrix(matrix(0.95, 0.4, 0.2, 0, 0.6, 0.8)))
      drawImage(lit, "chest-closed", cx, cy, w, 1, L.chestSY)
      var bloom = c
      let by = Y(385)
      bloom.translateBy(x: cx, y: by)
      bloom.scaleBy(x: 1.25, y: 0.8)
      bloom.translateBy(x: -cx, y: -by)
      bloom.fill(
        circle(cx, by, S(175)),
        with: radial(
          [(0xffffff, 0.6 * f.charge, 0), (0xe6fbff, 0.32 * f.charge, 0.5), (0xe6fbff, 0, 1)], cx,
          by, S(175)))
    }
    if f.seam > 0.01 {
      var seam = c
      seam.opacity *= f.seam
      seam.blendMode = .screen
      seam.addFilter(.colorMatrix(matrix(0.3, 0.3, 0.3, 0.3, 0.66, 0.7)))
      drawImage(seam, "chest-closed", cx, cy, w, 1, L.chestSY)
    }
  }
  func drawOpenChest(_ c: GraphicsContext, _ f: F) {
    let k = 0.94 - 0.04 * f.pale
    let w = 0.1 * f.pale
    var pale = c
    pale.addFilter(.colorMatrix(matrix(k, k, k, w, w * 0.94, w * 0.9)))
    drawImage(pale, "chest-open", X(L.openCX), Y(L.openCY + f.openDY), S(L.openW))
    if f.glare > 0.01 {
      var glare = c
      glare.opacity *= 0.9 * f.glare
      glare.blendMode = .screen
      glare.addFilter(.colorMatrix(matrix(0.3, 0.3, 0.3, 0.3, 0.66, 0.7)))
      drawImage(glare, "chest-open", X(L.openCX), Y(L.openCY + f.openDY), S(L.openW))
    }
  }
  func drawMouthGlow(_ c: GraphicsContext, _ f: F) {
    let a = f.mouth * (1 - f.sink)
    guard a > 0.01 else { return }
    let x = X(L.openCX)
    let y = Y(L.mouthY + f.openDY)
    let r = S(170)
    var c = c
    c.translateBy(x: x, y: y)
    c.scaleBy(x: 1, y: 0.5)
    c.translateBy(x: -x, y: -y)
    c.fill(
      circle(x, y, r),
      with: radial(
        [
          (0xffffff, a, 0), (0xffffff, 0.9 * a, 0.35), (0xeefcff, 0.45 * a, 0.65), (0xeefcff, 0, 1),
        ], x, y, r))
  }
  func drawLight(_ context: GraphicsContext, _ f: F) {
    guard max(f.lCore, f.lRays, f.lStreak, f.lRingA, f.lPillar) > 0.004 else { return }
    var c = context
    c.blendMode = .plusLighter
    let args: [Shader.Argument] = [
      .float2(size), .float2(X(f.lX), Y(f.lY)), .float(f.t), .float(f.lCore), .float(f.lRays),
      .float(f.lReach), .float(f.lStreak), .float(S(f.lRingR)), .float(f.lRingA), .float(f.lPillar),
      .float(Y(L.mouthY + f.openDY)), .float(f.lBelow),
    ]
    c.fill(
      Path(bounds.insetBy(dx: -40, dy: -40)),
      with: .shader(
        Shader(
          function: ShaderFunction(library: .default, name: "FrostChestOpeningLight"),
          arguments: args)))
  }
  func drawVignette(_ c: GraphicsContext, _ f: F) {
    guard f.vignette > 0.004 else { return }
    c.fill(
      Path(bounds.insetBy(dx: -40, dy: -40)),
      with: radial(
        [(0x0a1e46, 0, 0), (0x0a1e46, 0, 0.45), (0x0a1e46, f.vignette, 1)], X(f.lX), Y(f.lY - 60),
        hypot(size.width, size.height) * 0.62))
  }
  func drawCard(_ context: GraphicsContext, _ f: F) {
    guard f.reveal > 0.5 && f.t >= M.T.card else { return }
    var cx = X(f.cardX)
    var cy = Y(f.cardY)
    var s = f.cardS
    var a = f.cardA
    if f.sink > 0 {
      cx = M.mix(cx, X(L.openCX), f.sink)
      cy = M.mix(cy, Y(L.mouthY) + S(10), f.sink)
      s *= M.mix(1, 0.3, f.sink)
      a *= 1 - M.seg(f.sink, 0.7, 1)
    }
    guard a > 0.01 else { return }
    let angle = f.spin * Double.pi / 180
    let cs = cos(angle)
    let sn = sin(angle)
    let w = S(L.cardW)
    let h = S(L.cardH)
    if f.cardHalo > 0.01 {
      let hw = w * s * max(0.25, abs(cs)) * 1.18
      let hh = h * s * 1.12
      var halo = context
      halo.addFilter(.blur(radius: S(22)))
      halo.fill(
        rect(cx - hw / 2, cy - hh / 2, hw, hh, S(30)),
        with: .color(color(0xffffff, 0.85 * f.cardHalo * a)))
    }
    var c = context
    c.opacity *= a
    let blur = S(f.cardBlur) + f.sink * 4
    if blur > 0.4 { c.addFilter(.blur(radius: blur)) }
    let card = resources.card(cs >= 0, sx, sy)
    let args: [Shader.Argument] = [
      .image(Image(uiImage: card)), .float2(cx, cy), .float2(card.size), .float(s), .float(cs),
      .float(sn), .float(S(700)), .float(f.cardHot),
    ]
    c.fill(
      Path(bounds),
      with: .shader(
        Shader(
          function: ShaderFunction(library: .default, name: "FrostChestOpeningCard"),
          arguments: args)))
  }
  func drawSparkles(_ c: GraphicsContext, _ f: F) {
    if f.reveal < 0.5 {
      let stars: [[Double]] = [
        [188, 338, 34, 0.84, 1.42], [268, 351, 60, 1.08, 1.78], [127, 291, 42, 1.4, 1.95],
        [288, 477, 54, 1.94, 2.6], [306, 481, 26, 2.4, 2.66],
      ]
      for v in stars {
        let u = M.seg(f.t, v[3], v[4])
        if u > 0 && u < 1 { star(c, X(v[0]), Y(v[1]), S(v[2]) * sin(pow(u, 0.7) * Double.pi), 1) }
      }
      let rest = f.header * (1 - M.seg(f.t, 0.84, 0.9))
      for v: [Double] in [[188, 338, 16], [45, 505, 12], [100, 525, 16]] {
        star(c, X(v[0]), Y(v[1]), S(v[2]), rest, 1)
      }
    } else if f.sparkle >= 0 {
      for index in 0..<64 {
        let i = Double(index)
        let born = 0.02 + 0.4 * pow(M.rand(i, 61), 1.3)
        let life = 0.26 + 0.24 * M.rand(i, 62)
        let age = f.sparkle - born
        if age < 0 || age > life { continue }
        let x0 = 40 + 300 * M.rand(i, 63)
        let y0 = 282 + 170 * pow(M.rand(i, 64), 1.25)
        let vy = 25 + 55 * M.rand(i, 65)
        let vx = (x0 - 196) * 0.22 + (M.rand(i, 66) - 0.5) * 24
        let k = age / life
        let pop = M.easeOut(M.seg(k, 0, 0.18))
        let out = 1 - M.seg(k, 0.6, 1)
        let sz = S(16 + 34 * pow(M.rand(i, 67), 1.4)) * M.mix(0.45, 1, pop) * M.mix(0.6, 1, out)
        star(c, X(x0 + vx * age), Y(y0 - vy * age), sz, pop * out, M.rand(i, 68) < 0.72 ? 2 : 1)
      }
      let spots: [[Double]] = [
        [120, 480, 26, 0.05], [152, 517, 32, 0.12], [245, 442, 26, 0], [297, 477, 22, 0.2],
        [322, 520, 22, 0.3], [230, 560, 30, 0.26],
      ]
      for v in spots {
        let u = M.seg(f.sparkle, v[3], v[3] + 0.36)
        if u > 0 && u < 1 {
          star(c, X(v[0]), Y(v[1] + f.openDY), S(v[2]) * sin(u * Double.pi), 1, 1)
        }
      }
    }
  }
  func drawTitle(_ c: GraphicsContext, _ f: F) {
    guard f.header > 0.003 else { return }
    let lift = S(8) * (1 - f.header)
    var glow = c
    glow.addFilter(.blur(radius: S(7)))
    text(
      glow, Copy.eyebrow, "Black", S(L.eyebrowW), X(201), Y(L.eyebrowBase) - lift, C.titleGlow,
      0.75 * f.header)
    text(
      glow, Copy.title, "Black", S(L.titleW), X(201), Y(L.titleBase) - lift, C.titleGlow,
      0.75 * f.header)
    text(
      c, Copy.eyebrow, "Black", S(L.eyebrowW), X(201), Y(L.eyebrowBase) - lift, C.title, f.header)
    text(c, Copy.title, "Black", S(L.titleW), X(201), Y(L.titleBase) - lift, C.title, f.header)
  }
  func line(
    _ c: GraphicsContext, _ points: [CGPoint], _ color: Int, _ alpha: Double, _ width: Double
  ) {
    guard let first = points.first else { return }
    var path = Path()
    path.move(to: first)
    for p in points.dropFirst() { path.addLine(to: p) }
    c.stroke(
      path, with: .color(self.color(color, alpha)),
      style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
  }
  func drawHeader(_ c: GraphicsContext, _ f: F) {
    let a = f.header
    guard a > 0.003 else { return }
    let bx = X(L.backX)
    let by = top + S(L.backY)
    let br = S(L.backD / 2)
    glass(c, bx - br, by - br, br * 2, br * 2, br, a, 0.58)
    var arrow = c
    arrow.translateBy(x: bx, y: by)
    arrow.scaleBy(x: S(22) / 24, y: S(22) / 24)
    arrow.translateBy(x: -12, y: -12)
    line(arrow, [CGPoint(x: 19, y: 12), CGPoint(x: 5, y: 12)], C.ink, 0.85 * a, 2)
    line(
      arrow, [CGPoint(x: 12, y: 19), CGPoint(x: 5, y: 12), CGPoint(x: 12, y: 5)], C.ink, 0.85 * a, 2
    )
    let px = X(L.coinX)
    let py = top + S(L.coinY)
    let pw = S(L.coinW)
    let ph = S(L.coinH)
    glass(c, px, py, pw, ph, ph / 2, a, 0.5)
    drawImage(c, "coin", px + S(26), py + ph / 2, S(L.coinIcon), a)
    let label = resources.fit(Copy.balance, "Bold", S(L.balanceW))
    text(
      c, Copy.balance, "Bold", S(L.balanceW), px + S(47) + label.width / 2, top + S(L.balanceBase),
      C.ink, a)
    let pr = S(L.plusD / 2)
    let pcx = px + pw - S(4.5) - pr
    let pcy = py + ph / 2
    c.fill(
      circle(pcx, pcy, pr),
      with: linear([(C.claimTop, a, 0), (C.claimLow, a, 1)], pcx, pcy - pr, pcy + pr))
    c.stroke(circle(pcx, pcy, pr - 0.6), with: .color(color(0xffffff, 0.7 * a)), lineWidth: 1.2)
    line(c, [CGPoint(x: pcx - S(8), y: pcy), CGPoint(x: pcx + S(8), y: pcy)], 0xffffff, a, S(2.6))
    line(c, [CGPoint(x: pcx, y: pcy - S(8)), CGPoint(x: pcx, y: pcy + S(8))], 0xffffff, a, S(2.6))
  }
  func drawShaft(_ c: GraphicsContext, _ f: F) {
    let u = f.shaft
    guard u > 0 && u < 1 else { return }
    let img = resources.uiImage("chest-closed")
    let cw = S(L.chestW)
    let ch = cw * img.size.height / max(1, img.size.width) * L.chestSY
    let chest = CGRect(x: X(L.chestCX) - cw / 2, y: Y(L.chestCY) - ch / 2, width: cw, height: ch)
    c.drawLayer { layer in
      layer.clip(to: Path(chest))
      layer.drawLayer { band in
        let env = M.seg(u, 0, 0.08) * (1 - M.seg(u, 0.9, 1))
        let x = X(-15 + 413 * u)
        let y = Y(445)
        let half = S(8 + 40 * sin(Double.pi * u))
        band.translateBy(x: x, y: y)
        band.rotate(by: .degrees(22))
        band.fill(
          rect(-half * 1.7, -S(300), half * 3.4, S(560)),
          with: .shader(ShaderLibrary.FrostChestOpeningShaft(.float(half), .float(sx), .float(env)))
        )
      }
      layer.blendMode = .destinationIn
      layer.draw(resources.image("chest-closed"), in: chest)
    }
  }
  func drawStreaks(_ c: GraphicsContext, _ f: F) {
    guard f.orb > 0 && f.t >= 0.72 else { return }
    let k = M.seg(f.t, 0.72, 0.85) * (1 - M.seg(f.fly, 0.5, 1))
    guard k > 0.01 else { return }
    for index in 0..<26 {
      let i = Double(index)
      let ang = M.rand(i, 21) * Double.pi * 2
      let life = 0.42 + 0.3 * M.rand(i, 22)
      let raw = (f.t - 0.72) / life + M.rand(i, 23)
      let u = raw - floor(raw)
      let rad = S(M.mix(165, 62, u * u))
      let len = S(8 + 11 * M.rand(i, 24))
      let a = sin(u * Double.pi) * k * (0.55 + 0.45 * M.rand(i, 25))
      let dx = cos(ang)
      let dy = sin(ang)
      let cx = X(f.orbX)
      let cy = Y(f.orbY)
      line(
        c,
        [
          CGPoint(x: cx + dx * rad, y: cy + dy * rad),
          CGPoint(x: cx + dx * (rad + len), y: cy + dy * (rad + len)),
        ], C.streak, a, S(2.2))
    }
  }
  static let hand: Path = {
    // Circular SVG arcs keep their original radii, flags and endpoints.
    var p = Path()
    func arc(_ end: CGPoint, _ radius: Double, _ large: Bool = false, _ sweep: Bool = false) {
      let start = p.currentPoint!
      let dx = (start.x - end.x) / 2
      let dy = (start.y - end.y) / 2
      let r = max(radius, hypot(dx, dy))
      let factor =
        (large == sweep ? -1.0 : 1.0)
        * sqrt(max(0, (r * r - dx * dx - dy * dy) / (dx * dx + dy * dy)))
      let cx = (start.x + end.x) / 2 + factor * dy
      let cy = (start.y + end.y) / 2 - factor * dx
      let from = atan2(start.y - cy, start.x - cx)
      let to = atan2(end.y - cy, end.x - cx)
      p.addArc(
        center: CGPoint(x: cx, y: cy), radius: r, startAngle: .radians(from),
        endAngle: .radians(to), clockwise: !sweep)
    }
    p.move(to: CGPoint(x: 10, y: 9.5))
    p.addLine(to: CGPoint(x: 10, y: 4))
    arc(CGPoint(x: 6, y: 4), 2)
    p.addLine(to: CGPoint(x: 6, y: 14))
    p.move(to: CGPoint(x: 14, y: 10))
    p.addLine(to: CGPoint(x: 14, y: 9))
    arc(CGPoint(x: 10, y: 9), 2)
    p.addLine(to: CGPoint(x: 10, y: 10))
    p.move(to: CGPoint(x: 18, y: 11))
    p.addLine(to: CGPoint(x: 18, y: 10))
    arc(CGPoint(x: 14, y: 10), 2)
    p.move(to: CGPoint(x: 18, y: 11))
    arc(CGPoint(x: 22, y: 11), 2, true, true)
    p.addLine(to: CGPoint(x: 22, y: 14))
    arc(CGPoint(x: 14, y: 22), 8, false, true)
    p.addLine(to: CGPoint(x: 12, y: 22))
    p.addCurve(
      to: CGPoint(x: 6.01, y: 19.66), control1: CGPoint(x: 9.2, y: 22),
      control2: CGPoint(x: 7.5, y: 21.14))
    p.addLine(to: CGPoint(x: 2.41, y: 16.06))
    arc(CGPoint(x: 5.24, y: 13.24), 2, false, true)
    p.addLine(to: CGPoint(x: 7, y: 15))
    return p
  }()
  func drawHold(_ c: GraphicsContext, _ f: F) {
    let a = f.pill
    if a > 0.003 {
      let pw = S(L.pillW)
      let ph = S(L.pillH)
      let px = X(L.pillX)
      let py = Y(L.pillY)
      let k = 1 - 0.03 * f.press
      var pill = c
      pill.translateBy(x: px + pw / 2, y: py + ph / 2)
      pill.scaleBy(x: k, y: k)
      pill.translateBy(x: -(px + pw / 2), y: -(py + ph / 2))
      glass(pill, px, py, pw, ph, ph / 2, a, 0.7)
      drawImage(pill, "crystal", px + S(37), py + ph / 2, S(L.pillIcon), a)
      let label = resources.fit(Copy.drop, "ExtraBold", S(L.dropW))
      text(
        pill, Copy.drop, "ExtraBold", S(L.dropW), X(L.pillTextX) + label.width / 2, Y(L.dropBase),
        C.ink, a)
    }
    if f.hint > 0.003 {
      var hand = c
      let hs = S(L.handSize) / 24
      hand.translateBy(x: X(201), y: Y(L.handY))
      hand.scaleBy(x: hs, y: hs)
      hand.translateBy(x: -12, y: -12)
      hand.stroke(
        Self.hand, with: .color(color(C.hint, f.hint)),
        style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
      text(c, Copy.hint1, "Bold", S(L.hintW), X(201), Y(L.hintBase), C.hint, f.hint)
      text(c, Copy.hint2, "Bold", S(L.hintW2), X(201), Y(L.hintBase2), C.hint, f.hint)
    }
    let show = f.orb > 0
    guard show || a > 0.003 else { return }
    let cx = X(show ? f.orbX : M.ORB.bead.x)
    let cy = Y(show ? f.orbY : M.ORB.bead.y)
    let r = S(show ? f.orbR : M.ORB.bead.r)
    let oa = show ? 1 : a
    if show {
      let pr = S(M.ORB.end.r) * 1.25
      let px = X(M.ORB.end.x)
      let py = Y(M.ORB.end.y + M.ORB.end.r * 0.8)
      c.fill(
        circle(px, py, pr), with: radial([(0xffffff, 0.85 * a, 0), (0xffffff, 0, 1)], px, py, pr))
    }
    c.fill(
      circle(cx, cy, r * 1.2),
      with: radial(
        [(0xffffff, 0.9 * oa, 0), (0xffffff, 0.9 * oa, 0.8), (0xffffff, 0, 1)], cx, cy, r * 1.2))
    c.fill(
      rect(cx - r - 2, cy - r - 2, r * 2 + 4, r * 2 + 4),
      with: .shader(
        ShaderLibrary.FrostChestOpeningOrb(
          .float2(cx, cy), .float(r), .float(f.fill), .float(f.t), .float(0), .float(oa))))
  }
  func drawRewards(_ c: GraphicsContext, _ f: F) {
    guard f.panel > 0.003 else { return }
    let x = X(L.panelX)
    let w = S(L.panelW)
    let h = S(L.panelH)
    let y = Y(L.panelY)
    let r = S(L.panelR)
    // Backdrop blur is rendered by sampling the same frost image under the glass panel.
    c.drawLayer { panel in
      panel.clip(to: rect(x, y, w, h, r))
      panel.addFilter(.blur(radius: 14 * f.panel))
      drawFrost(panel, f)
    }
    glass(c, x, y, w, h, r, f.panel, 0.2)
    text(c, Copy.others, "SemiBold", S(L.othersW), X(201), Y(L.othersBase), C.inkSoft, f.others)
    let names = ["xp-boost", "winter-boots", "frozen-shrine"]
    for i in 0..<3 {
      let k = f.tiles[i]
      if k <= 0.003 { continue }
      let d = S(L.tileD)
      let tx = X(Double(L.tileXs[i]))
      let ty = Y(L.tileCY)
      let sc = M.mix(0.92, 1, k)
      var tile = c
      if k < 0.98 { tile.addFilter(.blur(radius: 3 * (1 - k))) }
      tile.drawLayer { layer in
        var art = layer
        art.translateBy(x: tx, y: ty)
        art.scaleBy(x: sc, y: sc)
        glass(art, -d / 2, -d / 2, d, d, S(L.tileR), k, 0.6)
        let img = resources.uiImage(names[i])
        let aspect = img.size.width / max(1, img.size.height)
        drawImage(art, names[i], 0, 0, S(L.tileArt) * min(1, aspect), k)
        text(
          layer, Copy.items[i], "Regular", S(Double(L.itemW[i])), tx, Y(L.itemBase), C.inkSoft, k)
      }
    }
  }
  func drawClaim(_ context: GraphicsContext, _ f: F) {
    guard f.claim > 0.003 else { return }
    let x = X(L.claimX)
    let w = S(L.claimW)
    let h = S(L.claimH)
    let y = Y(L.claimY + f.claimY)
    let press = 1 - 0.03 * f.claimDown
    var c = context
    c.opacity *= f.claim
    let gain = M.mix(0.35, 1, f.claimInk)
    let lift = M.mix(0.65, 0, f.claimInk)
    c.addFilter(.colorMatrix(matrix(gain, gain, gain, lift, lift, lift)))
    c.drawLayer { c in
      c.translateBy(x: x + w / 2, y: y + h / 2)
      c.scaleBy(x: press, y: press)
      c.translateBy(x: -(x + w / 2), y: -(y + h / 2))
      var shadow = c
      shadow.addFilter(.blur(radius: 14))
      shadow.fill(rect(x + 10, y + 10, w - 20, h - 4, h / 2), with: .color(color(0x1a8cff, 0.45)))
      c.fill(rect(x - 2, y - 2, w + 4, h + 4, h / 2 + 2), with: .color(color(0xffffff, 0.85)))
      c.fill(
        rect(x, y, w, h, h / 2),
        with: linear([(C.claimTop, 1, 0), (C.claimMid, 1, 0.55), (C.claimLow, 1, 1)], x, y, y + h))
      c.fill(
        rect(x + 6, y + 3, w - 12, h * 0.5, h * 0.25),
        with: linear([(0xffffff, 0.5, 0), (0xffffff, 0, 1)], x, y, y + h * 0.5))
      var bottom = c
      bottom.clip(to: rect(x, y, w, h, h / 2))
      bottom.fill(
        rect(x, y, w, h),
        with: radial([(0x82dcff, 0.55, 0), (0x82dcff, 0, 1)], x + w / 2, y + h, w * 0.5))
      text(
        c, Copy.claim, "ExtraBold", S(L.claimTextW), x + w / 2, Y(L.claimBase) + y - Y(L.claimY),
        C.claimInk)
    }
  }
  func drawSnow(_ c: GraphicsContext, _ f: F) {
    let life = M.LOOP / 2
    for index in 0..<44 {
      let i = Double(index)
      let phase = f.idle / life + M.rand(i, 31)
      let cyc = floor(phase)
      let u = phase - cyc
      let seed = i * 7 + cyc.truncatingRemainder(dividingBy: 2)
      let x = M.rand(seed, 32) * size.width + S(14) * sin(u * Double.pi * 2 + i)
      let y = M.rand(seed, 33) * size.height * 0.96 + S(46) * u
      let big = M.rand(i, 34) > 0.86
      let r = big ? S(2.6 + 2 * M.rand(i, 35)) : S(0.9 + 1.3 * M.rand(i, 35))
      let a = sin(u * Double.pi) * (big ? 0.55 : 0.85)
      var snow = c
      if big { snow.addFilter(.blur(radius: r * 0.6)) }
      snow.fill(circle(x, y, r), with: .color(color(0xffffff, a)))
    }
  }
  func drawFlash(_ c: GraphicsContext, _ f: F) {
    guard f.flash > 0.003 else { return }
    let first = f.t < 6
    let cx = X(first ? 200 : L.openCX)
    let cy = Y(first ? 430 : L.mouthY)
    let r =
      first
      ? S(
        40 + 80 * M.seg(f.t, M.T.flashIn[0], 2.68) + 560
          * pow(M.seg(f.t, 2.68, M.T.flashIn[1]), 1.8)) : hypot(size.width, size.height) * 1.15
    c.fill(
      Path(bounds),
      with: radial(
        [
          (0xffffff, f.flash, 0), (0xfbfdff, f.flash * 0.92, 0.35), (0xeef8ff, f.flash * 0.55, 0.7),
          (0xeef8ff, 0, 1),
        ], cx, cy, r))
    if first {
      let u = M.seg(f.t, 2.61, 2.7)
      if u > 0 && u < 1 { star(c, cx, cy, S(90) * sin(Double.pi * u), 1, 1) }
    }
  }
  func graded(_ context: GraphicsContext, _ f: F, _ draw: (GraphicsContext) -> Void) {
    var c = context
    if f.exposure > 1.002 || f.dim > 0.002 {
      let e = f.exposure
      let lift = (e - 1) * 0.08
      c.addFilter(
        .colorMatrix(
          matrix(
            e * (1 - 0.62 * f.dim), e * (1 - 0.5 * f.dim), e * (1 - 0.22 * f.dim), lift, lift, lift)
        ))
    }
    c.drawLayer { draw($0) }
  }
  func drawScene(_ context: GraphicsContext, _ f: F) {
    var c = context
    c.translateBy(x: S(f.shakeX), y: S(f.shakeY))
    c.translateBy(x: X(200), y: Y(430))
    c.scaleBy(x: f.punch, y: f.punch)
    c.translateBy(x: -X(200), y: -Y(430))
    if f.reveal < 0.5 {
      graded(c, f) {
        drawSky($0, f)
        drawChestGlow($0, f)
      }
      if f.lBehind > 0.5 { drawLight(c, f) }
      graded(c, f) { drawClosedChest($0, f) }
      drawVignette(c, f)
      if f.lBehind < 0.5 { drawLight(c, f) }
    } else {
      let behind = f.sink > 0.55
      graded(c, f) {
        if f.frost < 1 { drawSky($0, f, 1) }
        drawFrost($0, f)
        if behind { drawCard($0, f) }
        drawOpenChest($0, f)
      }
      drawVignette(c, f)
      drawLight(c, f)
      drawMouthGlow(c, f)
      drawChips(c, f)
      drawMotes(c, f)
      if !behind { drawCard(c, f) }
    }
    drawSparkles(c, f)
    drawTitle(c, f)
    drawHeader(c, f)
    drawShaft(c, f)
    drawStreaks(c, f)
    drawHold(c, f)
    drawRewards(c, f)
    drawClaim(c, f)
    drawSnow(c, f)
    drawFlash(c, f)
  }
}

extension FrostChestOpeningArt {
  // WGSL uses float32 hashes, unlike the JS double-precision rand used by the stars/snow.
  static func gpuHash(_ i: Int, _ salt: Int) -> Double {
    let value = sin(Float(i) * 127.1 + Float(salt) * 311.7) * 43758.5453
    return Double(value - floor(value))
  }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let k = M.clamp01((x - a) / (b - a))
    return k * k * (3 - 2 * k)
  }
  static func rotate(_ axis: SIMD3<Float>, _ angle: Float, _ v: SIMD3<Float>) -> SIMD3<Float> {
    let c = cos(angle)
    let s = sin(angle)
    return v * c + simd_cross(axis, v) * s + axis * simd_dot(axis, v) * (1 - c)
  }
  static func corner(_ k: Int, _ w: Float) -> SIMD3<Float> {
    let tri = k / 3
    let j = k % 3
    let side = tri % 4
    let top = tri < 4
    if j == 0 { return SIMD3(0, top ? 1 : -0.55, 0) }
    var idx = side
    if j == 2 { idx = (side + 1) % 4 }
    if !top { idx = j == 1 ? (side + 1) % 4 : side }
    let ang = Float(idx) * 1.5707963 + 0.35
    return SIMD3(cos(ang) * w, 0.08, sin(ang) * w * 0.6)
  }
  func drawChips(_ context: GraphicsContext, _ f: F) {
    guard f.sparkle >= 0 && f.t < M.T.motes[1] else { return }
    for i in 0..<30 {
      func hash(_ s: Int) -> Double { Self.gpuHash(i, s) }
      let born = 0.3 * hash(12)
      let life = 0.35 + 0.22 * hash(13)
      let t = max(f.sparkle - born, 0)
      let k = t / life
      let alpha =
        (f.sparkle >= born ? 1.0 : 0) * Self.smooth(0, 0.12, k) * (1 - Self.smooth(0.6, 1, k))
        * (1 - f.flash)
      if alpha <= 0.001 { continue }
      let start = SIMD2<Double>((hash(1) - 0.5) * 250, (hash(2) - 0.6) * 90) * sx
      let away = start - SIMD2(0, 75 * sx) + SIMD2(0, 0.001)
      let dir = simd_normalize(away)
      let speed = (40 + 90 * hash(11)) * sx
      let travel = speed * (1 - exp(-2.5 * t)) / 2.5
      let grav = -18 * sx * t
      let depth = (hash(3) - 0.5) * 1.4
      let persp = 1 / (1 - depth * 0.35)
      let len = (3.4 + 3.2 * hash(5)) * sx
      let axis = simd_normalize(
        SIMD3<Float>(Float(hash(6) - 0.5), Float(hash(7) - 0.5), Float(hash(8) - 0.5 + 0.001)))
      let spin = Float(t * (6 + 10 * hash(9)) + hash(10) * 6.28)
      let lean = atan2(dir.x, -dir.y)
      let cl = Float(cos(lean))
      let sl = Float(sin(lean))
      let centre = SIMD2(X(L.openCX), Y(395)) + start + (dir * travel + SIMD2(0, grav)) * persp
      func rotate(_ v: SIMD3<Float>) -> SIMD3<Float> {
        let q = Self.rotate(axis, spin, v)
        return SIMD3(q.x * cl - q.y * sl, q.x * sl + q.y * cl, q.z)
      }
      let w = Float(0.5 + 0.2 * hash(4))
      // No culling and no depth test, as in the WebGPU pipeline: preserve triangle order.
      for tri in 0..<8 {
        let p0 = Self.corner(tri * 3, w)
        let p1 = Self.corner(tri * 3 + 1, w)
        let p2 = Self.corner(tri * 3 + 2, w)
        let n = simd_normalize(rotate(simd_normalize(simd_cross(p1 - p0, p2 - p0))))
        let facing = abs(n.z)
        let fres = pow(1 - facing, 2)
        let light = simd_normalize(SIMD3<Float>(-0.45, 0.65, 0.6))
        let incident = -light
        let reflection = incident - 2 * simd_dot(n, incident) * n
        let spec = pow(max(reflection.z, 0), 18)
        let diff = 0.55 + 0.45 * abs(simd_dot(n, light))
        let ice = simd_mix(
          SIMD3<Float>(0.33, 0.80, 0.96), SIMD3<Float>(0.50, 0.88, 0.99),
          SIMD3(repeating: Float(hash(14))))
        var rgb =
          simd_mix(ice * diff, SIMD3<Float>(0.92, 0.98, 1), SIMD3(repeating: fres * 0.7))
          + SIMD3(repeating: spec)
        rgb = simd_min(rgb, SIMD3(repeating: 1))
        let a = min(1, max(0, Double(0.7 + 0.3 * fres + spec) * alpha * 0.88))
        var path = Path()
        for (j, v) in [p0, p1, p2].enumerated() {
          let q = rotate(v)
          let p = CGPoint(
            x: centre.x + Double(q.x) * len * persp, y: centre.y - Double(q.y) * len * persp)
          if j == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        path.closeSubpath()
        context.fill(
          path,
          with: .color(
            Color(.sRGB, red: Double(rgb.x), green: Double(rgb.y), blue: Double(rgb.z), opacity: a))
        )
      }
    }
  }
  func drawMotes(_ c: GraphicsContext, _ f: F) {
    guard f.motes >= 0 else { return }
    for instance in 30..<210 {
      let i = instance + 100
      func hash(_ s: Int) -> Double { Self.gpuHash(i, s) }
      let born = 0.03 + 0.9 * pow(hash(21), 1.4)
      let life = 0.9 + 0.8 * hash(22)
      let t = f.motes - born
      let k = M.clamp01(t / life)
      let depth = hash(23)
      let side = hash(28) > 0.5 ? 1.0 : -1.0
      let x =
        side * (38 + (60 + 80 * depth) * hash(24)) + 16 * sin(t * (1.6 + hash(25)) + Double(i))
      let y = -20 * hash(26) - (60 + 140 * hash(27)) * max(t, 0)
      let r = M.mix(5.5, 1.3, depth) * sx * 2.2
      let cx = X(L.openCX) + x * sx
      let cy = Y(395) + y * sx
      let twinkle = 0.65 + 0.35 * sin(t * 9 + Double(i) * 3)
      let a =
        (t >= 0 ? 1.0 : 0) * sin(3.14159 * k) * twinkle * M.mix(0.55, 1, depth) * (1 - f.flash)
      if a <= 0.001 { continue }
      c.fill(
        rect(cx - r, cy - r, r * 2, r * 2),
        with: .shader(ShaderLibrary.FrostChestOpeningMote(.float2(cx, cy), .float(r), .float(a))))
    }
  }
}
