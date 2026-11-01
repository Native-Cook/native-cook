import SwiftUI
import UIKit

enum TinyWorldPickerArt {
  typealias M = TinyWorldPickerMotion
  typealias R = TinyWorldPickerResources
  static let stars: [(x: Double, y: Double, r: Double, ph: Double, k: Double)] = {
    var seed: Int64 = 7
    func rnd() -> Double { seed = (seed * 16807) % 2147483647; return Double(seed) / 2147483647 }
    return (0..<70).map { _ in (rnd(), rnd(), 0.6 + rnd() * 1.1, rnd() * 6.283, 1 + floor(rnd() * 3)) }
  }()
  static func color(_ rgb: UInt32, _ alpha: Double = 1) -> Color { Color(uiColor: R.color(rgb, alpha)) }
  static func halo(_ a: UInt32, _ b: UInt32, _ m: Double) -> SIMD3<Float> {
    let p = M.smooth(0.2, 0.8, m)
    func channel(_ shift: UInt32) -> Double {
      let a = Double((a >> shift) & 255) / 255, b = Double((b >> shift) & 255) / 255
      return (pow(pow(a, 2.2) * (1 - p) + pow(b, 2.2) * p, 1 / 2.2) * 255).rounded() / 255
    }
    return SIMD3(Float(channel(16)), Float(channel(8)), Float(channel(0)))
  }
  static func sky(_ c: GraphicsContext, _ size: CGSize, _ f: M.Frame) {
    let h = size.height
    c.fill(R.shared.rect(CGRect(origin: .zero, size: size)), with: .linearGradient(
      Gradient(colors: [color(0x163C49), color(0x0E2A34), color(0x081A21)]),
      startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
  }
  static func dust(_ c: GraphicsContext, _ size: CGSize, _ f: M.Frame) {
    let w = size.width, h = size.height
    for s in stars {
      let opacity = 0.18 + 0.42 * (0.5 + 0.5 * sin(f.t * 2 * Double.pi * s.k / M.loop + s.ph))
      c.fill(R.shared.ellipse(CGRect(x: s.x * w - s.r, y: s.y * h - s.r, width: s.r * 2, height: s.r * 2)), with: .color(color(0xB6E4EC, opacity)))
    }
  }
  static func foreground(_ c: GraphicsContext, _ size: CGSize, _ top: Double, _ bottom: Double, _ scale: Double, _ f: M.Frame, _ pressed: Int) {
    let r = R.shared, w = size.width, h = size.height, cy = (h * 0.47).rounded()
    let image = Image(uiImage: r.staticLayer(size, top, bottom, scale))
    let by = h - bottom - 68
    let controls = [CGRect(x: w - 60, y: cy - 176, width: 44, height: 44),
                    CGRect(x: w - 60, y: cy - 120, width: 44, height: 44),
                    CGRect(x: w - 60, y: cy - 64, width: 44, height: 44),
                    CGRect(x: 16, y: by + 2, width: 52, height: 52),
                    CGRect(x: w - 68, y: by + 2, width: 52, height: 52),
                    CGRect(x: 80, y: by, width: w - 160, height: 56)]
    if controls.indices.contains(pressed) {
      let rect = controls[pressed]
      let crop = pressed == 4 ? rect.insetBy(dx: -6, dy: -6) : rect
      var base = c
      let mask = r.excluding(crop, size)
      base.clip(to: mask, style: FillStyle(eoFill: true))
      base.draw(image, in: CGRect(origin: .zero, size: size))
      var button = c
      button.translateBy(x: rect.midX, y: rect.midY); button.scaleBy(x: 0.96, y: 0.96)
      button.translateBy(x: -rect.midX, y: -rect.midY); button.clip(to: r.rect(crop))
      button.draw(image, in: CGRect(origin: .zero, size: size))
    } else {
      c.draw(image, in: CGRect(origin: .zero, size: size))
    }
    func text(_ label: R.Label, _ x: Double, _ y: Double, _ context: GraphicsContext) {
      context.draw(Image(uiImage: label.image), in: CGRect(x: x - label.width / 2, y: y - label.height / 2, width: label.width, height: label.height))
    }
    let s = sin(Double.pi * M.clamp(f.m))
    let bob = sin(f.t * 2 * Double.pi * 2 / M.loop) * 2.5 - s * 6
    var bubble = c
    bubble.translateBy(x: w / 2, y: cy - 156 + bob)
    bubble.scaleBy(x: 1 - 0.12 * s, y: 1 - 0.12 * s)
    bubble.fill(r.rounded(CGRect(x: -22, y: -22, width: 44, height: 44), 14), with: .color(.white.opacity(0.14)))
    bubble.stroke(r.rounded(CGRect(x: -22 + 1 / scale, y: -22 + 1 / scale, width: 44 - 2 / scale, height: 44 - 2 / scale), 14 - 1 / scale), with: .color(color(0xA0E6F0, 0.16)), lineWidth: 2 / scale)
    for i in 0..<4 {
      let wd = TinyWorldPickerWorld.all[i]
      let v = M.labelVisibility(i, f), vis = M.visibility(i, f)
      if vis > 0.0001 {
        var glyph = bubble; glyph.opacity = vis
        text(r.icon(wd.glyph, 24, 0xFFFFFF, material: true), 0, 0, glyph)
      }
      if v > 0.0001 {
        var labels = c; labels.opacity = v
        labels.drawLayer { layer in
        var labels = layer
        let incoming = i == f.nxt && f.nxt != f.cur
        // The transform origin is the centre of the 80-point label layer.
        labels.translateBy(x: w / 2, y: cy + 180 + (1 - v) * (incoming ? 10 : -8))
        labels.scaleBy(x: 0.96 + 0.04 * v, y: 0.96 + 0.04 * v)
        let title = r.label(wd.name, "TitanOne", 32, lineHeight: 38)
        var shadow = labels; shadow.addFilter(.shadow(color: .black.opacity(0.25), radius: 0, x: 0, y: 2))
        text(title, 0, -21, shadow)
        let tint: UInt32 = wd.done ? 0x6BDC74 : (i == 2 ? 0xFFC14D : 0x7FE6F5)
        let status = r.label(wd.status, "Nunito-Black", 12, tint, spacing: 1.4)
        let statusW = status.width + 24 + 3 + (wd.done ? 20 : 0)
        let rect = CGRect(x: -statusW / 2, y: 8, width: statusW, height: 28)
        labels.fill(r.rounded(rect, 14), with: .color(color(tint, 31.0 / 255)))
        labels.stroke(r.rounded(rect.insetBy(dx: 0.75, dy: 0.75), 13.25), with: .color(color(tint, 136.0 / 255)), lineWidth: 1.5)
        if wd.done { text(r.icon(61982, 14, tint), -statusW / 2 + 13.5 + 7, 22, labels) }
        text(status, wd.done ? 10 : 0, 22, labels)
        }
        var stats = c; stats.opacity = v
        let statLabels = wd.stats.map { r.label($0, "Nunito-ExtraBold", 13, 0xCFEFF4, alpha: 0.78) }
        let total = statLabels.reduce(0.0) { $0 + $1.width + 19 } + 36
        var x = (w - total) / 2
        for (index, label) in statLabels.enumerated() {
          text(r.icon([62942, 61985, 62264][index], 14, 0xCFEFF4, 0.62), x + 7, cy + 236 + (1 - v) * 6, stats)
          text(label, x + 19 + label.width / 2, cy + 236 + (1 - v) * 6, stats)
          x += 19 + label.width + 18
        }
        var cta = c; cta.opacity = v
        if pressed == 5 {
          cta.translateBy(x: w / 2, y: by + 28); cta.scaleBy(x: 0.96, y: 0.96)
          cta.translateBy(x: -w / 2, y: -by - 28)
        }
        text(r.label(wd.cta, "Nunito-Black", 18, 0x2A1E05, spacing: 0.2), w / 2, h - bottom - 12 - 30 + (1 - v) * 6, cta)
      }
      let dotW = 7 + 15 * vis
      let dotRect = CGRect(x: w / 2 - 45 + Double(i) * 30 - dotW / 2, y: cy + 274 - 3.5, width: dotW, height: 7)
      let dot = vis == 0 || vis == 1 ? r.rounded(dotRect, 3.5) : Path(roundedRect: dotRect, cornerRadius: 3.5)
      c.fill(dot, with: .color(color(0xCFEFF4, 0.35 + 0.65 * vis)))
    }
  }
}
