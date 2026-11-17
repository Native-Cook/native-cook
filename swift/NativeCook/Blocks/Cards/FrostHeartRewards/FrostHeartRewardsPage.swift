import SwiftUI

/// fhr-theme.ts and fhr-page.ts: the fixed page (a recorded picture in React Native) and the
/// page state that redraws (balance, claim chips, "65 more" / "Level up").
enum FrostHeartRewardsTheme {
  typealias R = FrostHeartRewardsResources
  static let background = Color(hex: 0x131313)
  static let panel = Color(hex: 0x1A1A1C)
  static let chip = Color(hex: 0x25242A)
  static let text = Color(hex: 0xFFFEFF)
  static let body = Color(hex: 0xC0BFC5)
  static let timer = Color(hex: 0xB7B6BC)
  static let muted = Color(hex: 0x6A6A6A)
  static let pink = Color(hex: 0xF924EE)
  static let violet = Color(hex: 0xBE48F8)
  static let orange = Color(hex: 0xFC7B20)
  static let tab = Color(hex: 0x5B5962)
  static let activeTab = Color(hex: 0xC575F3)
  static let badgeRed = Color(hex: 0xFF0050)
  static let black = Color(hex: 0x131313)
  static let white = Color.white

  static let nav = R.FontStyle(size: 18.3, weight: .semibold, tracking: -0.35)
  static let navBrand = R.FontStyle(size: 15.4, weight: .bold, tracking: -0.55)
  static let pill = R.FontStyle(size: 13.3, weight: .semibold, tracking: -0.35)
  static let balance = R.FontStyle(size: 47, weight: .bold, tracking: 1.5)
  static let nextTitle = R.FontStyle(size: 12, weight: .semibold, tracking: 0.05)
  static let remaining = R.FontStyle(size: 20, weight: .semibold, tracking: -0.35)
  static let fraction = R.FontStyle(size: 12.3, weight: .semibold, tracking: 0)
  static let amount = R.FontStyle(size: 19.3, weight: .semibold, tracking: -0.1)
  static let heading = R.FontStyle(size: 15.3, weight: .bold, tracking: 0.15)
  static let filter = R.FontStyle(size: 12, weight: .bold, tracking: 0.1)
  static let taskTitle = R.FontStyle(size: 15.3, weight: .bold, tracking: -0.1)
  static let taskBody = R.FontStyle(size: 13.3, weight: .regular, tracking: 0.05)
  static let taskReward = R.FontStyle(size: 12.7, weight: .bold, tracking: -0.5)
  static let taskTimer = R.FontStyle(size: 12.3, weight: .bold, tracking: 0.1)
  static let cartBadge = R.FontStyle(size: 13, weight: .regular, tracking: -1)

  struct Chip { let value: Int; let body: CGRect; let baseline: Double; let font: R.FontStyle }
  static let chips: [Chip] = [
    Chip(value: 5, body: CGRect(x: 15.33, y: 510.67, width: 48.33, height: 36.33), baseline: 535, font: .init(size: 15.7, weight: .bold, tracking: 0)),
    Chip(value: 8, body: CGRect(x: 69.33, y: 510.67, width: 48.33, height: 36.33), baseline: 535, font: .init(size: 16, weight: .bold, tracking: 0)),
    Chip(value: 10, body: CGRect(x: 123.33, y: 510.67, width: 48.33, height: 36.33), baseline: 535, font: .init(size: 16.3, weight: .semibold, tracking: 1)),
    Chip(value: 15, body: CGRect(x: 177.67, y: 510.67, width: 48, height: 36.33), baseline: 535, font: .init(size: 15.6, weight: .bold, tracking: 1.67)),
    Chip(value: 20, body: CGRect(x: 231.33, y: 510.67, width: 48.33, height: 36.33), baseline: 535, font: .init(size: 15.4, weight: .bold, tracking: 1)),
    Chip(value: 25, body: CGRect(x: 285.33, y: 510.67, width: 48.33, height: 36.33), baseline: 535, font: .init(size: 16, weight: .bold, tracking: 0.67)),
    Chip(value: 50, body: CGRect(x: 339.33, y: 510.67, width: 48.33, height: 36.33), baseline: 535.33, font: .init(size: 16, weight: .bold, tracking: 0)),
  ]
  static let icons = (
    back: "M29 79 L22 86 L29 93",
    chat: "M366 76 Q364 76 364 79 L364 90 Q364 92 367 92 L370 92 L370 97 L377 92 L383 92 Q386 92 386 89 L386 79 Q386 76 383 76 Z M369 81 L381 81 M369 85 L378 85",
    home: "M30 821 L30 818 L39 810 Q41 808 43 810 L49 815 M31 820 L31 830 Q31 832 34 832 L40 832 M46 819 A6 6 0 1 0 46 831 A6 6 0 1 0 46 819 M50 829 L54 833",
    list: "M111 812 L130 812 M111 820 L130 820 M111 828 L130 828",
    wallet: "M192 814 L198 809 L210 813 M192 813 L211 813 Q213 813 213 815 L213 829 Q213 831 211 831 L193 831 Q191 831 191 829 L191 816 Q191 813 194 813 M207 821 L213 821",
    cart: "M272 808 L276 822 L293 817 L289 811 L275 811 M278 827 A1 1 0 1 0 278 829 M288 827 A1 1 0 1 0 288 829",
    profile: "M375 820 A11 11 0 1 0 353 820 A11 11 0 1 0 375 820 M364 812 A3.5 3.5 0 1 0 364 819 A3.5 3.5 0 1 0 364 812 M357 827 Q364 819 371 827",
    clock: "M94 752 A4 4 0 1 0 94 760 A4 4 0 1 0 94 752 M94 753 L94 756 L97 757 M92 750 L96 750 M91 760 L90 762 M97 760 L98 762"
  )
}

/// The recorded pictures of makePage(faces) and makePage(faces, 'points').
struct FrostHeartRewardsStaticPage {
  typealias T = FrostHeartRewardsTheme
  typealias R = FrostHeartRewardsResources
  private let res = R.shared

  private func text(_ c: inout GraphicsContext, _ s: String, _ x: Double, _ y: Double, _ style: R.FontStyle, _ color: Color, center width: Double? = nil) {
    let f = res.font(style)
    let start = width == nil ? x - res.inkLeft(s, f) : x + (width! - res.width(s, style)) / 2
    c.fill(res.skiaPath(s, f, x: start, baseline: y, tracking: style.tracking), with: .color(color))
  }
  private func stroke(_ c: inout GraphicsContext, _ svg: String, _ color: Color, _ w: Double = 1.8) {
    c.stroke(FrostHeartRewardsSVG.path(svg), with: .color(color), style: StrokeStyle(lineWidth: w, lineCap: .round, lineJoin: .round))
  }
  private func rect(_ c: inout GraphicsContext, _ r: CGRect, _ radius: Double, _ color: Color) {
    c.fill(Path(roundedRect: r, cornerSize: CGSize(width: radius, height: radius), style: .circular), with: .color(color))
  }
  private func pill(_ c: inout GraphicsContext, _ r: CGRect, _ radius: Double, _ baseline: Double, _ s: String, _ color: Color, _ style: R.FontStyle = T.pill, _ ink: Color = .white) {
    rect(&c, r, radius, color)
    text(&c, s, r.minX, baseline, style, ink, center: r.width)
  }

  func draw(_ context: GraphicsContext) {
    var c = context
    // base layer
    stroke(&c, T.icons.back, T.pink, 1.8)
    stroke(&c, T.icons.chat, T.text, 1.6)
    text(&c, "my", 166, 91.67, T.nav, T.text)
    pill(&c, CGRect(x: 199.5, y: 75.33, width: 40.5, height: 19.33), 10, 90.67, "lumi", T.pink, T.navBrand)
    text(&c, "UPs every day!", 16.33, 491.67, T.heading, T.text)
    stroke(&c, "M176 480 L182 486 L176 492", T.text, 1.8)
    rect(&c, CGRect(x: 3, y: 579.33, width: 396, height: 294.67), 29.33, T.panel)
    let pills: [(CGRect, Double, String, Bool)] = [
      (CGRect(x: 15.33, y: 595, width: 59.33, height: 36.67), 18.33, "All", true),
      (CGRect(x: 82.33, y: 595, width: 122.67, height: 37), 18.5, "Deals", false),
      (CGRect(x: 212, y: 594.67, width: 79.67, height: 37.33), 18.67, "Pharmacy", false),
      (CGRect(x: 299, y: 595, width: 93, height: 37), 18.5, "Beauty", false),
    ]
    for p in pills { pill(&c, p.0, p.1, 619, p.2, p.3 ? T.white : T.chip, T.filter, p.3 ? T.black : T.body) }
    c.fill(
      Path(CGRect(x: 354, y: 590, width: 45, height: 46)),
      with: .linearGradient(Gradient(colors: [Color(hex: 0x1A1A1C, opacity: 0), Color(hex: 0x1A1A1C)]), startPoint: CGPoint(x: 354, y: 0), endPoint: CGPoint(x: 394, y: 0)))
    rect(&c, CGRect(x: 15, y: 647, width: 373, height: 127.33), 25, T.chip)
    rect(&c, CGRect(x: 18, y: 784, width: 367, height: 125), 26, T.chip)
    let b = CGRect(x: 27.67, y: 659, width: 49.33, height: 49.33)
    let disc = Path(ellipseIn: b)
    c.fill(
      disc,
      with: .linearGradient(
        Gradient(stops: [.init(color: Color(hex: 0xDFB5FF), location: 0), .init(color: Color(hex: 0x9A42E8), location: 0.28), .init(color: Color(hex: 0xF448F2), location: 0.65), .init(color: Color(hex: 0xDDCAFF), location: 1)]),
        startPoint: CGPoint(x: b.minX, y: b.minY), endPoint: CGPoint(x: b.maxX, y: b.maxY)))
    c.stroke(Path(ellipseIn: b.insetBy(dx: 1, dy: 1)), with: .color(Color(hex: 0xF0DDFF)), lineWidth: 1.3)
    text(&c, "lumi", b.minX, b.minY + 32, .init(size: 17.5, weight: .bold, tracking: -1.1), T.white, center: b.width)
    text(&c, "Discover more at lumi:", 88.67, 675.67, T.taskTitle, T.text)
    text(&c, "sportswear essentials", 88.67, 694.33, T.taskTitle, T.text)
    text(&c, "See what's in our latest collection", 88.67, 718.67, T.taskBody, T.body)
    text(&c, "and choose a new favourite", 88.67, 736.67, T.taskBody, T.body)
    pill(&c, CGRect(x: 311, y: 661, width: 36, height: 18.33), 8.5, 675, "+200", Color(hex: 0x49494B), T.taskReward)
    pill(&c, CGRect(x: 348.33, y: 661, width: 27.33, height: 17), 8.5, 674, "UP", T.orange, T.pill)
    text(&c, "2h 58m", 107, 760.33, T.taskTimer, T.timer)
    stroke(&c, T.icons.clock, T.timer, 1.3)
    c.fill(Path(ellipseIn: CGRect(x: 52 - 23, y: 815 - 23, width: 46, height: 46)), with: .color(T.violet))
    rect(&c, CGRect(x: 3, y: 797, width: 396, height: 77), 0, T.panel)
    stroke(&c, T.icons.home, T.activeTab, 1.9)
    stroke(&c, T.icons.list, T.tab, 2.2)
    stroke(&c, T.icons.wallet, T.tab, 2)
    stroke(&c, T.icons.cart, T.tab, 2)
    stroke(&c, T.icons.profile, T.tab, 1.8)
    pill(&c, CGRect(x: 281.33, y: 800.67, width: 22.67, height: 19.67), 10, 815.67, "15", T.badgeRed, T.cartBadge)
    // points layer
    let brands: [(CGRect, Double, String, Color, Double)] = [
      (CGRect(x: 16.33, y: 370.67, width: 27.67, height: 17.67), -5, "lumi", T.pink, 10.8),
      (CGRect(x: 44.33, y: 370.67, width: 35, height: 17.67), 4, "wow", T.violet, 12.8),
    ]
    for brand in brands {
      var r = c
      r.translateBy(x: brand.0.midX, y: brand.0.midY)
      r.rotate(by: .degrees(brand.1))
      r.translateBy(x: -brand.0.midX, y: -brand.0.midY)
      pill(&r, brand.0, 8.8, 383.67, brand.2, brand.3, .init(size: brand.4, weight: .semibold, tracking: -0.35))
    }
    text(&c, "To the next level", 190.67, 380.67, T.nextTitle, T.text)
    stroke(&c, "M347 372 L350 375.5 L347 379", T.body, 1.6)
    text(&c, "and $150", 193, 441.33, T.amount, T.text)
    text(&c, "of $150", 291, 442.33, T.fraction, T.muted)
  }
}

/// PageState: only counters and claim state redraw.
struct FrostHeartRewardsPageState {
  typealias T = FrostHeartRewardsTheme
  typealias R = FrostHeartRewardsResources
  typealias M = FrostHeartRewardsMotion
  private let res = R.shared
  static let check = FrostHeartRewardsSVG.path("M-3 0 L-0.5 2.5 L4 -3.5")
  static let digits: [[Path]] = {
    let res = R.shared
    let f = res.font(T.balance)
    let widths = res.run("1650", f).advances
    let xs = [17.67, 17.67 + widths[0] + 10.5, 17.67 + widths[0] + widths[1] + 12, 17.67 + widths[0] + widths[1] + widths[2] + 13.5]
    return xs.map { x in
      (0..<11).map { i in
        let d = String(i % 10)
        return res.skiaPath(d, f, x: x - res.inkLeft(d, f), baseline: 441.33 + Double(i) * 53)
      }
    }
  }()
  static let digitX: [Double] = {
    let res = R.shared
    let f = res.font(T.balance)
    let widths = res.run("1650", f).advances
    return [17.67, 17.67 + widths[0] + 10.5, 17.67 + widths[0] + widths[1] + 12, 17.67 + widths[0] + widths[1] + widths[2] + 13.5]
  }()

  /// Chip ages: claimed chips 0 and 1 are 1000 s old; in the demo 25 and 50 are claimed at 0.6/0.95 s.
  struct Input {
    var scene: M.Scene
    var chipAge: [Double]
    var reduced: Bool
  }

  func draw(_ context: GraphicsContext, _ input: Input) {
    var c = context
    let scene = input.scene
    if input.reduced { drawReducedBalance(&c, scene) } else { drawBalance(&c, scene) }
    let levelUp = M.ease(max(0, min(1, (scene.balance - 1714) / 6)))
    let more = 1 - levelUp
    let blur = input.reduced ? 0 : 4 * sin(Double.pi * levelUp)
    if more > 0 {
      var g = c
      g.opacity *= more
      g.fill(Path(roundedRect: CGRect(x: 273.67, y: 393.67, width: 27.67, height: 17), cornerSize: CGSize(width: 8.5, height: 8.5), style: .circular), with: .color(T.orange))
      g.fill(res.skiaPath("UP", res.font(T.pill), x: 279.5, baseline: 406.67), with: .color(.white))
      g.fill(res.skiaPath("of 500", res.font(T.fraction), x: 310.33, baseline: 410), with: .color(T.muted))
      var t = c
      t.opacity *= more
      t.drawLayer { l in
        if blur > 0.001 { l.addFilter(.blur(radius: blur)) }
        l.drawLayer { inner in
          inner.fill(res.skiaPath("\(Int((scene.remaining).rounded())) more", res.font(T.remaining), x: 192.33, baseline: 409.67), with: .color(T.text))
        }
      }
    }
    if levelUp > 0 {
      var t = c
      t.opacity *= levelUp
      t.drawLayer { l in
        if blur > 0.001 { l.addFilter(.blur(radius: blur)) }
        l.drawLayer { inner in
          inner.fill(res.skiaPath("Level up \u{203A}", res.font(T.remaining), x: 192.33, baseline: 409.67), with: .color(T.pink))
        }
      }
    }
    for i in T.chips.indices { drawChip(&c, i, input.chipAge[i], input.reduced) }
  }

  private func balanceText(_ v: Double) -> String {
    let r = Int(v.rounded())
    return "\(Int(floor(v / 1000))) \(String(format: "%03d", r % 1000))"
  }
  private func drawReducedBalance(_ c: inout GraphicsContext, _ scene: M.Scene) {
    let f = res.font(T.balance)
    let x = 17.67 - res.inkLeft("1", f)
    if scene.numberFade < 1 {
      var g = c
      g.opacity *= 1 - scene.numberFade
      g.fill(res.skiaPath(balanceText(scene.balanceFrom), f, x: x, baseline: 441.33), with: .color(T.text))
    }
    var g = c
    g.opacity *= scene.numberFade
    g.fill(res.skiaPath(balanceText(scene.balanceTo), f, x: x, baseline: 441.33), with: .color(T.text))
  }
  private func drawBalance(_ c: inout GraphicsContext, _ scene: M.Scene) {
    var clip = c
    clip.clip(to: Path(CGRect(x: 12, y: 396, width: 160, height: 53)))
    let value = scene.balance
    let integral = floor(value)
    let frac = value - integral
    let motion = abs(sin(frac * Double.pi)) * 0.75
    for k in 0..<4 {
      let power = pow(10, Double(3 - k))
      let carry = k == 3 ? frac : (integral.truncatingRemainder(dividingBy: power) == power - 1 ? frac : 0)
      let shift = (floor(value / power).truncatingRemainder(dividingBy: 10) + carry) * -53
      var d = clip
      d.clip(to: Path(CGRect(x: Self.digitX[k] - 1, y: 397, width: 34, height: 51)))
      d.translateBy(x: 0, y: shift)
      // A vertical-only Gaussian (Skia blur {x:0, y:σ}) as five weighted copies.
      let taps: [(Double, Double)] = motion < 0.05 ? [(0, 1)] : [(-2, 0.054), (-1, 0.244), (0, 0.403), (1, 0.244), (2, 0.054)]
      for tap in taps {
        var t = d
        t.opacity *= tap.1 / (motion < 0.05 ? 1 : 0.999)
        if tap.0 != 0 { t.translateBy(x: 0, y: tap.0 * motion) }
        if taps.count > 1 { t.blendMode = .plusLighter }
        for i in 0..<11 { t.fill(Self.digits[k][i], with: .color(T.text)) }
      }
    }
  }

  private func drawChip(_ c: inout GraphicsContext, _ i: Int, _ age: Double, _ reduced: Bool) {
    let chip = T.chips[i]
    let b = chip.body
    let cx = b.midX, cy = b.midY
    let fill = i < 2 || age < 0 ? 0 : (reduced ? 1 : M.ease(M.seg(age, 0, 0.18)))
    let fillOpacity = reduced ? M.ease(M.seg(age, 0, 0.16)) : 1
    var g = c
    if !reduced {
      let scale = age < 0 ? 1 : age < 0.12 ? 1 - 0.03 * M.out(M.seg(age, 0, 0.12)) : 0.97 + 0.03 * M.out(M.seg(age, 0.12, 0.36))
      g = g.scaled(scale, about: CGPoint(x: cx, y: cy))
    }
    let body = Path(roundedRect: b, cornerSize: CGSize(width: 18.17, height: 18.17), style: .circular)
    g.fill(body, with: .color(T.chip))
    if fill > 0 && fillOpacity > 0 {
      var f = g
      f.clip(to: Path(CGRect(x: b.minX, y: b.minY, width: b.width * fill, height: b.height)))
      f.opacity *= fillOpacity
      f.fill(body, with: .linearGradient(Gradient(colors: [Color(hex: 0xE123D9), Color(hex: 0xA63BEA)]), startPoint: CGPoint(x: b.minX, y: b.minY), endPoint: CGPoint(x: b.maxX, y: b.maxY)))
    }
    let font = res.font(chip.font)
    let label = String(chip.value)
    let tx = cx - res.width(label, chip.font) / 2
    g.fill(res.skiaPath(label, font, x: tx, baseline: chip.baseline), with: .color(T.text))
    let checkOpacity = i < 2 ? 1 : M.seg(age, 0.12, 0.18)
    if checkOpacity > 0 {
      let checkScale = i < 2 || reduced ? 1 : 0.95 + 0.05 * M.out(M.seg(age, 0.12, 0.28))
      let end = i < 2 || reduced ? 1 : M.seg(age, 0.16, 0.32)
      var k = g
      k.opacity *= checkOpacity
      k.translateBy(x: b.maxX - 8, y: b.minY + 8)
      k.scaleBy(x: checkScale, y: checkScale)
      k.fill(Path(ellipseIn: CGRect(x: -9, y: -9, width: 18, height: 18)), with: .color(Color(hex: 0x161619)))
      k.fill(Path(ellipseIn: CGRect(x: -7.4, y: -7.4, width: 14.8, height: 14.8)), with: .color(Color(hex: 0x28272D)))
      if end > 0 {
        k.stroke(Self.check.trimmedPath(from: 0, to: end), with: .color(.white), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
      }
    }
  }
}
