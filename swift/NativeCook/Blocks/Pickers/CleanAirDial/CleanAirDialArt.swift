import CoreText
import SwiftUI
import UIKit

// Font runs and the soft dust sprite are made once; Canvas only composites them each frame.
final class CleanAirDialResources {
  static let shared = CleanAirDialResources()
  struct Label {
    let image: Image
    let width: Double
    let size: CGSize
    let ascender: Double
    let fontSize: Double
  }
  private var labels: [String: Label] = [:]
  private let fonts: [String: UIFont]
  let dot: Image
  init() {
    let names = ["400Regular", "500Medium", "600SemiBold", "700Bold"]
    var faces: [String: String] = [:]
    for name in names {
      if let url = Bundle.main.url(
        forResource: "CleanAirDial-Manrope_\(name)", withExtension: "ttf")
      {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL)
          as? [CTFontDescriptor],
          let descriptor = descriptors.first
        {
          faces[name] = CTFontDescriptorCopyAttribute(descriptor, kCTFontNameAttribute) as? String
        }
      }
    }
    let specs: [(String, String, Double)] = [
      ("title", "700Bold", 34), ("room", "500Medium", 25), ("pm", "500Medium", 30),
      ("value", "700Bold", 70), ("stat", "600SemiBold", 34), ("unit", "400Regular", 34),
      ("cap", "600SemiBold", 27), ("label", "700Bold", 25), ("sub", "500Medium", 24),
      ("badge", "700Bold", 20),
    ]
    var made: [String: UIFont] = [:]
    for (key, face, size) in specs {
      made[key] =
        UIFont(name: faces[face] ?? "Manrope-Regular", size: size) ?? .systemFont(ofSize: size)
    }
    fonts = made
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    let sprite = UIGraphicsImageRenderer(size: CGSize(width: 32, height: 32), format: format).image
    { renderer in
      let colors =
        [
          UIColor.white.cgColor, UIColor.white.withAlphaComponent(0.8).cgColor,
          UIColor.white.withAlphaComponent(0).cgColor,
        ] as CFArray
      if let gradient = CGGradient(
        colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors, locations: [0, 0.45, 1])
      {
        renderer.cgContext.drawRadialGradient(
          gradient, startCenter: CGPoint(x: 16, y: 16), startRadius: 0,
          endCenter: CGPoint(x: 16, y: 16), endRadius: 16, options: [])
      }
    }
    dot = Image(uiImage: sprite).renderingMode(.template)
    let strings = [
      "Air Purifier", "Living room", "PM 2.5", "7", "64", "12", "°C", "%", "Good", "Moderate",
      "Poor",
      "Speed", "Timer", "Scenes", "Low", "Medium", "High", "Turbo", "Off", "On", "30 mins · On",
      "30 mins · Off", "A",
    ]
    for (key, _, _) in specs {
      for word in strings + (0...20).map({ String(format: "%03d", $0) })
        + (0...9).map({ String($0) })
      {
        labels[key + ":" + word] = makeLabel(word, font: made[key]!)
      }
    }
  }
  private func makeLabel(_ word: String, font: UIFont) -> Label {
    let width = (word as NSString).size(withAttributes: [.font: font]).width
    let size = CGSize(width: ceil(width) + 4, height: ceil(font.lineHeight) + 4)
    let format = UIGraphicsImageRendererFormat()
    format.scale = 2
    let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
      (word as NSString).draw(
        at: CGPoint(x: 2, y: 2), withAttributes: [.font: font, .foregroundColor: UIColor.white])
    }
    return Label(
      image: Image(uiImage: image).renderingMode(.template), width: width, size: size,
      ascender: font.ascender, fontSize: font.pointSize)
  }
  func label(_ word: String, _ key: String) -> Label {
    if let cached = labels[key + ":" + word] { return cached }
    let result = makeLabel(word, font: fonts[key]!)
    labels[key + ":" + word] = result
    return result
  }
}

// The canvas stays in the pin's pixel coordinates; scale/translation exactly match cad-draw.ts.
final class CleanAirDialArt {
  typealias M = CleanAirDialMotion
  typealias T = CleanAirDialTheme
  typealias C = CleanAirDialTheme.C
  typealias UI = CleanAirDialTheme.UI
  private var context: GraphicsContext
  private var stack: [GraphicsContext] = []
  private let resources = CleanAirDialResources.shared
  private static let particles:
    [(
      r: Double, th: Double, k: Double, wa: Double, wf: Double, wp: Double, size: Double, a: Double
    )] = {
      func hash(_ i: Int, _ k: Double) -> Double {
        let h = sin(Double(i + 1) * 12.9898 + k * 78.233) * 43758.5453
        return h - floor(h)
      }
      return (0..<T.DUST).map { i in
        let r = sqrt(hash(i, 1)) * (UI.dial.disk - 14)
        return (
          r, hash(i, 2) * M.TAU, r < UI.dial.disk * 0.55 ? 2 : 1,
          4 + 10 * hash(i, 3), 1 + floor(hash(i, 4) * 3), hash(i, 5) * M.TAU,
          4 + 6 * hash(i, 6) * hash(i, 6), 0.3 + 0.55 * hash(i, 7)
        )
      }
    }()
  init(_ context: GraphicsContext) { self.context = context }
  private func save() { stack.append(context) }
  private func restore() { context = stack.removeLast() }
  private func translate(_ x: Double, _ y: Double) { context.translateBy(x: x, y: y) }
  private func rotate(_ degrees: Double) { context.rotate(by: .degrees(degrees)) }
  private func scale(_ s: Double) { context.scaleBy(x: s, y: s) }
  private func circlePath(_ x: Double, _ y: Double, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
  }
  private func circle(_ x: Double, _ y: Double, _ r: Double, _ color: Color) {
    context.fill(circlePath(x, y, r), with: .color(color))
  }
  private func stroke(_ path: Path, _ color: Color, _ width: Double) {
    context.stroke(
      path, with: .color(color),
      style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
  }
  private func line(
    _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ color: Color, _ width: Double
  ) {
    var p = Path()
    p.move(to: CGPoint(x: x0, y: y0))
    p.addLine(to: CGPoint(x: x1, y: y1))
    stroke(p, color, width)
  }
  private func text(
    _ s: String, _ x: Double, _ cy: Double, _ font: String, _ color: Color, _ ax: Double = 0
  ) {
    let run = resources.label(s, font)
    var image = context.resolve(run.image)
    image.shading = .color(color)
    context.draw(
      image,
      in: CGRect(
        x: x - run.width * ax - 2, y: cy + run.fontSize * 0.36 - run.ascender - 2,
        width: run.size.width, height: run.size.height))
  }
  private func width(_ s: String, _ font: String) -> Double { resources.label(s, font).width }
  private func pill(_ color: Color) {
    context.fill(
      Path(
        roundedRect: CGRect(
          x: -UI.chips.w / 2, y: -UI.chips.h / 2, width: UI.chips.w, height: UI.chips.h),
        cornerRadius: UI.chips.h / 2), with: .color(color))
  }
  private func fan(_ x: Double, _ y: Double, _ s: Double, _ angle: Double, _ color: Color) {
    save()
    translate(x, y)
    rotate(angle)
    for i in 0..<4 {
      save()
      rotate(Double(i) * 90)
      var blade = Path()
      blade.move(to: .zero)
      blade.addCurve(
        to: CGPoint(x: 5 * s, y: -16 * s), control1: CGPoint(x: -3 * s, y: -6 * s),
        control2: CGPoint(x: -2 * s, y: -15 * s))
      blade.addCurve(
        to: CGPoint(x: 2 * s, y: -2 * s), control1: CGPoint(x: 10 * s, y: -16 * s),
        control2: CGPoint(x: 9 * s, y: -8 * s))
      blade.closeSubpath()
      context.fill(blade, with: .color(color))
      restore()
    }
    circle(0, 0, 3 * s, color)
    restore()
  }
  private func stopwatch(_ hand: Double) {
    stroke(circlePath(0, 2, 15), T.color(C.white), 3.4)
    line(-5, -19, 5, -19, T.color(C.white), 3.4)
    line(0, -19, 0, -13, T.color(C.white), 3)
    let angle = hand * .pi / 180 - .pi / 2
    line(0, 2, cos(angle) * 9, 2 + sin(angle) * 9, T.color(C.white), 3.2)
  }
  private func moon(_ x: Double, _ y: Double, _ wob: Double, _ background: Color) {
    save()
    translate(x, y)
    rotate(wob)
    circle(0, 0, 15, T.color(C.white))
    circle(8, -7, 13, background)
    circle(-6, 11, 6, T.color(C.white))
    circle(1, 13, 5, T.color(C.white))
    circle(-12, 14, 4.5, T.color(C.white))
    restore()
  }
  private func snowflake(_ x: Double, _ y: Double) {
    for i in 0..<3 {
      let angle = Double(i) * .pi / 3
      let dx = cos(angle) * 15
      let dy = sin(angle) * 15
      line(x - dx, y - dy, x + dx, y + dy, T.color(C.white), 3)
      for sign in [-1.0, 1.0] {
        let ex = x + sign * dx * 0.62
        let ey = y + sign * dy * 0.62
        for offset in [0.6, -0.6] {
          let branch = angle + (sign > 0 ? .pi : 0) + offset
          line(ex, ey, ex + cos(branch) * 6, ey + sin(branch) * 6, T.color(C.white), 3)
        }
      }
    }
  }
  private func drop(_ x: Double, _ y: Double) {
    var p = Path()
    p.move(to: CGPoint(x: x, y: y - 15))
    p.addCurve(
      to: CGPoint(x: x + 12, y: y + 5), control1: CGPoint(x: x + 4, y: y - 8),
      control2: CGPoint(x: x + 12, y: y - 2))
    p.addCurve(
      to: CGPoint(x: x, y: y + 16), control1: CGPoint(x: x + 12, y: y + 12),
      control2: CGPoint(x: x + 6, y: y + 16))
    p.addCurve(
      to: CGPoint(x: x - 12, y: y + 5), control1: CGPoint(x: x - 6, y: y + 16),
      control2: CGPoint(x: x - 12, y: y + 12))
    p.addCurve(
      to: CGPoint(x: x, y: y - 15), control1: CGPoint(x: x - 12, y: y - 2),
      control2: CGPoint(x: x - 4, y: y - 8))
    p.closeSubpath()
    stroke(p, T.color(C.white), 3)
    save()
    context.clip(to: p)
    context.fill(
      Path(CGRect(x: x - 14, y: y + 4, width: 28, height: 14)), with: .color(T.color(C.white)))
    restore()
  }
  private func dots(_ x: Double, _ y: Double) {
    for row in -2...2 {
      for column in -2...2 {
        if abs(row) + abs(column) > 2 && !(abs(row) == 1 && abs(column) == 1) { continue }
        if (row + column) % 2 != 0 { continue }
        circle(x + Double(column) * 6.5, y + Double(row) * 6.5, 3.6, T.color(C.white))
      }
    }
  }
  private func power(_ color: Color) {
    var arc = Path()
    arc.addArc(
      center: CGPoint(x: 0, y: 2), radius: 15, startAngle: .degrees(-50), endAngle: .degrees(230),
      clockwise: false)
    stroke(arc, color, 3.6)
    line(0, -18, 0, -2, color, 3.6)
  }
  private func gear() {
    var p = Path()
    for i in 0..<16 {
      let a0 = Double(i) / 16 * M.TAU
      let a1 = Double(i + 1) / 16 * M.TAU
      let radius = i % 2 == 0 ? 18.0 : 13.5
      if i == 0 { p.move(to: CGPoint(x: cos(a0) * radius, y: sin(a0) * radius)) }
      p.addLine(to: CGPoint(x: cos(a0 + 0.12) * radius, y: sin(a0 + 0.12) * radius))
      p.addLine(to: CGPoint(x: cos(a1 - 0.12) * radius, y: sin(a1 - 0.12) * radius))
    }
    p.closeSubpath()
    context.fill(p, with: .color(T.color(C.white)))
    circle(0, 0, 6, T.color(C.chip))
  }
  private func autoBadge(_ color: Color, _ ink: Color) {
    var p = Path()
    for i in 0...24 {
      let angle = Double(i) / 24 * M.TAU
      let radius = 17 + (i % 2 == 0 ? 1.6 : -1.2)
      let point = CGPoint(x: cos(angle) * radius, y: sin(angle) * radius)
      if i == 0 { p.move(to: point) } else { p.addLine(to: point) }
    }
    p.closeSubpath()
    context.fill(p, with: .color(color))
    text("A", 0, 0, "badge", ink, 0.5)
  }
  private func drawTicks(_ f: M.Frame) {
    let dial = UI.dial
    for k in 0..<dial.ticks {
      let tick = Double(k)
      let angle = tick / Double(dial.ticks) * M.TAU - .pi / 2
      let on = M.clamp01(f.g - tick + 0.5)
      let near = f.drag >= 0 ? max(0, 1 - abs(tick - f.drag) / 3.5) : 0
      let lead = on > 0 && on < 1 ? 1 : max(0, 1 - abs(tick - (f.g - 0.5)))
      let r0 = dial.r0 - 3 * near
      let r1 = dial.r1 + 12 * near + 3 * lead * (1 - f.hush)
      let ca = cos(angle)
      let sa = sin(angle)
      if lead > 0.3 && on > 0 {
        line(
          dial.x + ca * r0, dial.y + sa * r0, dial.x + ca * r1, dial.y + sa * r1,
          T.color(C.green, 0.18 * lead * (1 - f.hush)), dial.tickW * 3)
      }
      line(
        dial.x + ca * r0, dial.y + sa * r0, dial.x + ca * r1, dial.y + sa * r1,
        T.mixColor(C.tick, C.green, on * (1 - f.hush), 1 - 0.45 * f.hush), dial.tickW)
    }
  }
  private func drawDust(_ f: M.Frame) {
    let dial = UI.dial
    let reach = f.f * Double(T.BASE_DUST) * 1.15
    let band = Double(T.BASE_DUST) * 0.15
    let wob = M.TAU * f.amb / M.LOOP
    for i in 0..<T.DUST {
      var pull = 0.0
      if i < T.BASE_DUST {
        pull = M.clamp01((reach - Double(i)) / band)
      } else {
        let visibility = M.clamp01((-f.f * 80 - Double(i - T.BASE_DUST)) / 10)
        if visibility <= 0 { continue }
        pull = 1 - visibility
      }
      let particle = Self.particles[i]
      var radius = particle.r
      var angle = particle.th + particle.k * f.phi
      var alpha = particle.a
      var size = particle.size
      if pull > 0 {
        if f.ret == 1 || i >= T.BASE_DUST {
          radius = M.mix(particle.r, dial.disk + 10, pull)
          alpha *= 1 - pull
        } else {
          radius = particle.r * pow(1 - pull, 1.5)
          angle += pull * pull * M.TAU * 1.2
          alpha *= 1 - M.smooth((pull - 0.65) / 0.35)
          size *= 1 - 0.5 * pull
        }
      }
      if alpha < 0.01 { continue }
      let wx = particle.wa * sin(particle.wf * wob + particle.wp)
      let wy = particle.wa * cos(particle.wf * wob + particle.wp * 1.3)
      let x = dial.x + cos(angle) * radius + wx * (1 - pull)
      let y = dial.y + sin(angle) * radius + wy * (1 - pull)
      var dot = context.resolve(resources.dot)
      dot.shading = .color(T.color(C.dust, alpha * (1 - 0.6 * f.hush)))
      context.draw(dot, in: CGRect(x: x - size / 2, y: y - size / 2, width: size, height: size))
    }
  }
  private func drawSwirl(_ f: M.Frame) {
    let dial = UI.dial
    let speed = M.clamp01((f.g - T.LEVELS[1].ticks) / (T.LEVELS[3].ticks - T.LEVELS[1].ticks))
    let alpha = 0.05 + 0.18 * speed
    if alpha * (1 - f.hush) < 0.02 { return }
    for k in 0..<4 {
      let base = f.phi * 2 + Double(k) * M.TAU / 4
      var path = Path()
      for step in 0...24 {
        let u = Double(step) / 24
        let radius = M.mix(dial.disk * 0.9, dial.disk * 0.25, u)
        let angle = base + u * 2.2
        let point = CGPoint(x: dial.x + cos(angle) * radius, y: dial.y + sin(angle) * radius)
        if step == 0 { path.move(to: point) } else { path.addLine(to: point) }
      }
      let gradient = Gradient(stops: [
        .init(color: .white.opacity(0), location: 0),
        .init(color: T.color(0x9FE8C0, alpha * (1 - f.hush)), location: 0.08),
        .init(color: .white.opacity(0), location: 0.25),
        .init(color: .white.opacity(0), location: 1),
      ])
      context.stroke(
        path,
        with: .conicGradient(
          gradient, center: CGPoint(x: dial.x, y: dial.y), angle: .radians(base)),
        style: StrokeStyle(lineWidth: 3, lineCap: .round))
    }
  }
  private func drawDial(_ f: M.Frame) {
    let dial = UI.dial
    drawTicks(f)
    context.fill(
      circlePath(dial.x, dial.y, dial.disk),
      with: .linearGradient(
        Gradient(colors: [T.color(C.diskTop), T.color(C.diskBot)]),
        startPoint: CGPoint(x: 0, y: dial.y - dial.disk),
        endPoint: CGPoint(x: 0, y: dial.y + dial.disk)))
    stroke(circlePath(dial.x, dial.y, dial.disk), T.color(C.rim), 3)
    save()
    context.clip(to: circlePath(dial.x, dial.y, dial.disk - 2))
    drawSwirl(f)
    drawDust(f)
    let goodF = (12.0 - 6.5) / 13
    let wave = f.ret == 0 ? M.clamp01((f.f - goodF) / 0.14) : 0
    if wave > 0 && wave < 1 {
      let rr = M.mix(30, dial.disk, 1 - pow(1 - wave, 3))
      let green = T.QUALITY[0].color
      let gradient = Gradient(stops: [
        .init(color: T.color(green, 0), location: 0.6),
        .init(color: T.color(green, 0.35 * (1 - wave)), location: 0.9),
        .init(color: T.color(green, 0), location: 1),
      ])
      context.stroke(circlePath(dial.x, dial.y, rr),
        with: .radialGradient(gradient, center: CGPoint(x: dial.x, y: dial.y), startRadius: 0, endRadius: rr + 16),
        style: StrokeStyle(lineWidth: M.mix(26, 6, wave)))
    }
    let pm = M.pmOf(f.f)
    let qualities = T.QUALITY
    let q = qualities.firstIndex(where: { pm < $0.max }) ?? (qualities.count - 1)
    let lo = q > 0 ? qualities[q - 1].max : -10
    let hi = qualities[q].max
    let kLo = q > 0 ? M.smooth((pm - lo) / 0.8 + 0.5) : 1
    let kHi = q < qualities.count - 1 ? M.smooth((hi - pm) / 0.8 + 0.5) : 1
    let capAlpha = 1 - 0.55 * f.hush
    let capColor: Color
    if kLo < 1 {
      capColor = T.mixColor(qualities[q - 1].color, qualities[q].color, kLo, capAlpha)
    } else if kHi < 1 {
      capColor = T.mixColor(qualities[q + 1].color, qualities[q].color, kHi, capAlpha)
    } else {
      capColor = T.color(qualities[q].color, capAlpha)
    }
    circle(dial.x, UI.cap.cy, UI.cap.r, capColor)
    save()
    context.clip(to: circlePath(dial.x, UI.cap.cy, UI.cap.r))
    if kLo < 1 {
      text(
        qualities[q - 1].word, dial.x, UI.cap.text - 26 * kLo, "cap", T.color(C.capText, 1 - kLo),
        0.5)
      text(
        qualities[q].word, dial.x, UI.cap.text + 26 * (1 - kLo), "cap", T.color(C.capText, kLo), 0.5
      )
    } else if kHi < 1 {
      text(
        qualities[q + 1].word, dial.x, UI.cap.text + 26 * kHi, "cap", T.color(C.capText, 1 - kHi),
        0.5)
      text(
        qualities[q].word, dial.x, UI.cap.text - 26 * (1 - kHi), "cap", T.color(C.capText, kHi), 0.5
      )
    } else {
      text(qualities[q].word, dial.x, UI.cap.text, "cap", T.color(C.capText), 0.5)
    }
    restore()
    restore()
    let pulling =
      f.ret == 0 && f.f > 0.01 && f.f < M.F_HIGH + 0.3
      ? M.clamp01(f.f / 0.15) * M.clamp01((M.F_HIGH + 0.25 - f.f) / 0.25) : 0
    if pulling > 0.01 {
      context.fill(
        circlePath(dial.x, dial.y, 60),
        with: .radialGradient(
          Gradient(colors: [T.color(0x9FE8C0, 0.12 * pulling), T.color(0x9FE8C0, 0)]),
          center: CGPoint(x: dial.x, y: dial.y), startRadius: 0, endRadius: 60))
    }
    line(
      dial.x - dial.disk + 2, UI.divider, dial.x + dial.disk - 2, UI.divider, T.color(C.divider), 2)
    text(T.COPY.pm, dial.x, UI.pm.y, "pm", T.color(C.label))
    let n0 = Int(floor(pm))
    let fraction = M.smooth(pm - floor(pm))
    let digits = Array(String(format: "%03d", n0)).map(String.init)
    let next = Array(String(format: "%03d", n0 + 1)).map(String.init)
    var x = dial.x - width(String(format: "%03d", n0), "value") / 2
    save()
    context.clip(to: Path(CGRect(x: dial.x - 160, y: UI.pm.value - 40, width: 320, height: 80)))
    let ink = T.color(C.white, 1 - 0.5 * f.hush)
    for i in 0..<3 {
      if digits[i] == next[i] || fraction < 0.001 {
        text(digits[i], x, UI.pm.value, "value", ink)
      } else {
        text(digits[i], x, UI.pm.value + fraction * 74, "value", ink)
        text(next[i], x, UI.pm.value - (1 - fraction) * 74, "value", ink)
      }
      x += width(digits[i], "value")
    }
    restore()
    let stats = UI.stats
    for i in 0..<3 {
      let sx = stats.x[i]
      let value = T.COPY.stats[i].v
      let unit = T.COPY.stats[i].u
      let vw = width(value, "stat")
      let uw = width(unit, "unit")
      text(value, sx - (vw + uw) / 2, stats.v, "stat", T.color(C.white))
      text(unit, sx - (vw + uw) / 2 + vw, stats.v, "unit", T.color(C.unit))
      if i == 0 {
        snowflake(sx, stats.icon)
      } else if i == 1 {
        drop(sx, stats.icon)
      } else {
        dots(sx, stats.icon)
      }
    }
    for x in stats.div { line(x, stats.divY[0], x, stats.divY[1], T.color(C.divider), 2) }
  }
  private func drawChips(_ f: M.Frame) {
    let chips = UI.chips
    for i in 0..<3 {
      let x = chips.x[i]
      let press = f.press == i ? f.pressK : 0
      save()
      translate(x, chips.y)
      scale(1 - 0.04 * press)
      let bg = i == 0 ? C.blue : i == 2 ? C.purple : C.chip
      let on = i == 0 ? f.power : i == 1 ? f.timer : f.scenes
      let fill = i == 1 ? T.mixColor(C.chip, 0x2A2A2A, on) : T.mixColor(C.chip, bg, on)
      pill(fill)
      if i == 0 {
        fan(-32, 0, 1.25, f.phi * 4 * 180 / .pi, T.color(C.white))
        for sign in [-1.0, 1.0] {
          var triangle = Path()
          triangle.move(to: CGPoint(x: 28, y: sign * 9))
          triangle.addLine(to: CGPoint(x: 36, y: sign * 17))
          triangle.addLine(to: CGPoint(x: 44, y: sign * 9))
          triangle.closeSubpath()
          context.fill(triangle, with: .color(T.color(0xFFFFFF, 0.55)))
        }
      } else if i == 1 {
        stopwatch(f.timer > 0 ? min(360, f.timerAt * 240) * f.timer : 0)
      } else {
        moon(-25, 0, 6 * sin(M.TAU * 2 * f.amb / M.LOOP), T.mixColor(C.chip, C.purple, on))
        var chevron = Path()
        chevron.move(to: CGPoint(x: 38, y: -8))
        chevron.addLine(to: CGPoint(x: 45, y: 0))
        chevron.addLine(to: CGPoint(x: 38, y: 8))
        stroke(chevron, T.color(0xFFFFFF), 3.2)
      }
      restore()
      text(T.COPY.chips[i], x, chips.label, "label", T.color(C.white), 0.5)
    }
    var shown = false
    for level in 0..<T.LEVELS.count {
      let entry = T.LEVELS[level]
      let delta =
        (f.g - entry.ticks)
        / max(1, level < T.LEVELS.count - 1 ? T.LEVELS[level + 1].ticks - entry.ticks : 9)
      let alpha = M.clamp01(1 - abs(f.g - entry.ticks) / 4.5)
      if alpha < 0.02 { continue }
      shown = true
      text(
        f.power < 0.5 ? "Off" : entry.name, chips.x[0], chips.sub - delta * 30, "sub",
        T.color(C.sub, alpha), 0.5)
    }
    if !shown {
      text(T.LEVELS[M.levelAt(max(0, f.g))].name, chips.x[0], chips.sub, "sub", T.color(C.sub), 0.5)
    }
    text(
      f.timer > 0.5 ? T.COPY.timerOn : T.COPY.timerOff, chips.x[1], chips.sub, "sub",
      T.color(C.sub), 0.5)
    text(f.scenes > 0.5 ? "On" : "Off", chips.x[2], chips.sub, "sub", T.color(C.sub), 0.5)
  }
  private func drawBottom(_ f: M.Frame) {
    for i in 0..<3 {
      let press = f.press == 3 + i ? f.pressK : 0
      save()
      translate(UI.bottom.x[i], UI.bottom.y)
      scale(1 - 0.04 * press)
      if i == 1 {
        pill(T.mixColor(C.chip, C.power, f.power))
        power(T.color(f.power > 0.5 ? 0x151515 : C.white))
      } else if i == 0 {
        pill(T.mixColor(C.chip, 0xE9E9E9, f.auto))
        autoBadge(
          T.color(f.auto > 0.5 ? 0x151515 : C.white), T.color(f.auto > 0.5 ? 0xE9E9E9 : C.chip))
      } else {
        pill(T.color(C.chip))
        gear()
      }
      restore()
    }
  }
  func drawScene(_ size: CGSize, _ f: M.Frame) {
    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(T.color(C.bg)))
    save()
    scale(size.width / T.REF_W)
    translate(0, T.Y0)
    scale(T.S)
    translate(-T.X0, 0)
    var chevron = Path()
    chevron.move(to: CGPoint(x: T.X0 + 52, y: UI.header.y - 16))
    chevron.addLine(to: CGPoint(x: T.X0 + 36, y: UI.header.y))
    chevron.addLine(to: CGPoint(x: T.X0 + 52, y: UI.header.y + 16))
    stroke(chevron, T.color(C.white), 4)
    text(T.COPY.title, 600, UI.header.y - 8, "title", T.color(C.white), 0.5)
    text(T.COPY.room, 600, UI.header.sub + 2, "room", T.color(C.label), 0.5)
    for i in 0..<3 { circle(T.X0 + 670 - 64 + Double(i) * 12, UI.header.y, 3.6, T.color(C.white)) }
    drawDial(f)
    drawChips(f)
    drawBottom(f)
    restore()
  }
}
