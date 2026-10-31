import CoreText
import SwiftUI
import UIKit

enum ChromeMatchRevealArt {
  typealias M = ChromeMatchRevealMotion
  static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
    Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
          green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
  }
  static let fonts: Void = {
    for name in ["GochiHand-Regular", "CaveatBrush-Regular", "Inter-SemiBold"] {
      if let url = Bundle.main.url(forResource: "ChromeMatchReveal-\(name)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
  static let hand: Font = { _ = fonts; return .custom("GochiHand-Regular", fixedSize: 34) }()
  static let brush: Font = { _ = fonts; return .custom("CaveatBrush-Regular", fixedSize: 26) }()
  static let semibold: Font = { _ = fonts; return .custom("Inter-SemiBold", fixedSize: 17) }()
  static let cardRect = Path(CGRect(x: 0, y: 0, width: 176, height: 236))
  static let star: Path = {
    var p = Path()
    for i in 0..<8 {
      let a = Double(i) * .pi / 4 - .pi / 2
      let r = i % 2 == 0 ? 1.0 : 0.22
      let point = CGPoint(x: cos(a) * r, y: sin(a) * r)
      if i == 0 { p.move(to: point) } else { p.addQuadCurve(to: point, control: .zero) }
    }
    p.addQuadCurve(to: CGPoint(x: cos(-Double.pi / 2), y: sin(-Double.pi / 2)), control: .zero)
    p.closeSubpath()
    return p
  }()
  static let starGradient = Gradient(stops: [
    .init(color: color(0xffffff), location: 0), .init(color: color(0xb9c7ff), location: 0.3),
    .init(color: color(0x7f8bb0), location: 0.5), .init(color: color(0xe8f0ff), location: 0.75),
    .init(color: color(0x9aa6d0), location: 1)])
  static let beamColors: [UInt32] = [0x8b6cf6, 0xff5ad8, 0x7fe3ff, 0xffffff, 0xb8ff6a, 0xff8a4a]
  // Skia's default gradient interpolates unpremultiplied RGBA. Explicit stops keep
  // that black transparent endpoint when SwiftUI interpolates premultiplied colors.
  static let beamGradients: [Gradient] = beamColors.map { hex in
    Gradient(stops: (0...32).map { index in
      let q = Double(index) / 32
      let k = 1 - q
      let c = Color(.sRGB, red: Double((hex >> 16) & 255) / 255 * k,
        green: Double((hex >> 8) & 255) / 255 * k,
        blue: Double(hex & 255) / 255 * k, opacity: k)
      return Gradient.Stop(color: c, location: q)
    })
  }
  static func beams(_ context: GraphicsContext, _ size: CGSize, _ cy: Double, _ f: M.Frame, _ reduced: Bool) {
    let g = f.glint
    if reduced || g < 0 || g > 1.6 { return }
    let fade = min(1, g / 0.12) * (1 - M.smooth((g - 0.9) / 0.7))
    let start = CGPoint(x: size.width / 2 + 4, y: cy + 236 * 0.62)
    for i in 0..<6 {
      let base = -Double.pi / 2 + (Double(i) - 2.5) * 0.32
      let a = base + sin(g * 3.4 + Double(i) * 1.3) * 0.38
      let end = CGPoint(x: start.x + cos(a) * size.height, y: start.y + sin(a) * size.height)
      var line = Path()
      line.move(to: start); line.addLine(to: end)
      // The transparent endpoint is black, as in the Skia gradient.
      let gradient = beamGradients[i]
      var glow = context
      glow.opacity = 0.35 * fade
      glow.addFilter(.blur(radius: 8))
      glow.stroke(line, with: .linearGradient(gradient, startPoint: start, endPoint: end), style: StrokeStyle(lineWidth: 10, lineCap: .round))
      var core = context
      core.opacity = 0.85 * fade
      core.stroke(line, with: .linearGradient(gradient, startPoint: start, endPoint: end), style: StrokeStyle(lineWidth: 2, lineCap: .round))
    }
  }
  static func overlay(_ context: GraphicsContext, _ size: CGSize, _ cy: Double, _ f: M.Frame, _ reduced: Bool) {
    let sx = size.width / 2 + 4
    let sy = cy + 6
    if f.star > 0.01 {
      var c = context
      c.translateBy(x: sx, y: sy)
      c.rotate(by: .degrees(-12 + f.star * 12))
      c.scaleBy(x: 46 * f.star, y: 46 * f.star)
      c.fill(star, with: .linearGradient(starGradient, startPoint: CGPoint(x: -1, y: -1), endPoint: CGPoint(x: 1, y: 1)))
    }
    let g = f.glint
    if g >= 0 && g < 0.9 && !reduced {
      for i in 0..<10 {
        let a = Double(i) / 10 * .pi * 2 + 0.3
        let d = (1 - exp(-g * 5)) * Double(70 + (i % 3) * 30)
        var c = context
        c.opacity = 1 - M.smooth((g - 0.35) / 0.55)
        c.translateBy(x: sx + cos(a) * d, y: sy + sin(a) * d)
        let r = Double(6 + (i % 2) * 4)
        c.scaleBy(x: r, y: r)
        c.fill(star, with: .color(color(i % 2 == 1 ? 0xd7c6ff : 0xffffff)))
      }
    }
  }
}

struct ChromeMatchRevealCard: View {
  typealias A = ChromeMatchRevealArt
  var side: Double
  var sample: ChromeMatchRevealClock.Sample
  var size: CGSize
  var cy: Double
  var reduced: Bool
  var body: some View {
    let f = sample.frame
    let cx = size.width / 2
    let fy = max(-0.8, min(0.8, (sample.gazeX - (cx + side * 62)) / 180))
    let fp = max(-0.35, min(0.35, (sample.gazeY - cy) / 420))
    let idle = 0.05 * sin(Double.pi * 0.5 * (f.glint + 0.95) + side)
    let meet = -side * 0.55 * f.look + idle
    let yaw = reduced ? 0 : meet + (fy - meet) * sample.gazeK
    let pitch = reduced ? 0 : fp * sample.gazeK
    let shape = RoundedRectangle(cornerRadius: 26, style: .circular)
    ZStack(alignment: .topLeading) {
      shape.fill(A.color(0x241a3a, 0.75))
      LinearGradient(colors: [.white.opacity(0.1), .white.opacity(0.02)], startPoint: .top, endPoint: .bottom)
        .padding(1.5)
      Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
        context.fill(A.cardRect, with: .shader(
          ShaderLibrary.chromeMatchRevealBust(.float2(88, 136), .float(92), .float(yaw), .float(pitch),
            .float3(side < 0 ? 0.52 : 1, side < 0 ? 0.42 : 0.36, side < 0 ? 1 : 0.86), .float(0))))
      }
      .frame(width: 176, height: 236)
      .offset(x: 1.5, y: 1.5)
      shape.strokeBorder(A.color(side < 0 ? 0x8b6cf6 : 0xff5ad8), lineWidth: 1.5)
    }
    .frame(width: 176, height: 236, alignment: .topLeading)
    .clipShape(shape)
    .scaleEffect(x: 1 - f.squash, y: 1 + f.squash)
    .rotationEffect(.degrees(side * 9 + (1 - f.k) * side * 18))
    .compositingGroup()
    .opacity(reduced ? f.k : 1)
    .position(x: cx + side * 62 + side * (reduced ? 0 : (1 - f.k) * size.width * 0.75),
              y: cy + (side > 0 ? 26 : -6))
    .accessibilityHidden(true)
  }
}
