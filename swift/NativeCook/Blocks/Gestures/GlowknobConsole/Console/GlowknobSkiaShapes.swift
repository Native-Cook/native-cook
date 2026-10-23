import SwiftUI

/// Skia geometry helpers for GraphicsContext.
enum GlowknobSk {
  /// `rrect(rect, rx, ry)` as a path, with SkRRect's radius scaling: radii
  /// that do not fit are scaled down together, keeping their ratio.
  static func rrect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ rxIn: Double, _ ryIn: Double) -> Path {
    var rx = max(0, rxIn), ry = max(0, ryIn)
    if w <= 0 || h <= 0 { return Path() }
    if rx + rx > w || ry + ry > h {
      let scale = min(w / (rx + rx), h / (ry + ry))
      rx *= scale
      ry *= scale
    }
    var p = Path()
    if rx <= 0 || ry <= 0 {
      p.addRect(CGRect(x: x, y: y, width: w, height: h))
      return p
    }
    // Quarter ellipses as cubics (k = 4/3·(√2−1)); Skia draws conics, the
    // difference is below 0.03% of the radius.
    let k = 0.5522847498307936
    let l = x, t = y, r = x + w, b = y + h
    p.move(to: CGPoint(x: l + rx, y: t))
    p.addLine(to: CGPoint(x: r - rx, y: t))
    p.addCurve(to: CGPoint(x: r, y: t + ry), control1: CGPoint(x: r - rx + rx * k, y: t),
               control2: CGPoint(x: r, y: t + ry - ry * k))
    p.addLine(to: CGPoint(x: r, y: b - ry))
    p.addCurve(to: CGPoint(x: r - rx, y: b), control1: CGPoint(x: r, y: b - ry + ry * k),
               control2: CGPoint(x: r - rx + rx * k, y: b))
    p.addLine(to: CGPoint(x: l + rx, y: b))
    p.addCurve(to: CGPoint(x: l, y: b - ry), control1: CGPoint(x: l + rx - rx * k, y: b),
               control2: CGPoint(x: l, y: b - ry + ry * k))
    p.addLine(to: CGPoint(x: l, y: t + ry))
    p.addCurve(to: CGPoint(x: l + rx, y: t), control1: CGPoint(x: l, y: t + ry - ry * k),
               control2: CGPoint(x: l + rx - rx * k, y: t))
    p.closeSubpath()
    return p
  }

  static func rrect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
    rrect(x, y, w, h, r, r)
  }

  static func circle(_ cx: Double, _ cy: Double, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
  }

  static func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }

  static func linear(_ colors: [GlowknobRGBA], _ positions: [Double]? = nil, from: CGPoint, to: CGPoint) -> GraphicsContext.Shading {
    .linearGradient(glowknobSkiaGradient(colors, positions: positions), startPoint: from, endPoint: to)
  }

  static func radial(_ colors: [GlowknobRGBA], _ positions: [Double]? = nil, center: CGPoint, radius: Double) -> GraphicsContext.Shading {
    .radialGradient(glowknobSkiaGradient(colors, positions: positions), center: center, startRadius: 0, endRadius: radius)
  }

  static func sweep(_ colors: [GlowknobRGBA], _ positions: [Double], center: CGPoint) -> GraphicsContext.Shading {
    .conicGradient(glowknobSkiaGradient(colors, positions: positions), center: center, angle: .zero)
  }
}

/// Skia blur sigma → SwiftUI blur radius. SwiftUI's blur radius follows the
/// context transform like Skia's `respectCTM` sigma, and for small radii it is
/// the Gaussian sigma; from ~6 device pixels up its kernel is narrower than a
/// Gaussian of σ = radius (measured on edge profiles: σ ≈ 0.92·radius). The
/// radius is widened by that factor so halos keep Skia's reach.
@MainActor
enum GlowknobBlurCalibration {
  /// Device pixels per drawing unit of the context being drawn.
  static var pixelsPerUnit = 1.0

  static func radius(sigma: Double) -> Double {
    let px = sigma * pixelsPerUnit
    let k = px <= 4 ? 1 : px >= 6 ? 0.92 : 1 - 0.08 * (px - 4) / 2
    return sigma / k
  }
}

extension GraphicsContext {
  /// A shape with a Skia `BlurMask` (normal style, respectCTM): its coverage
  /// blurred with Gaussian sigma `sigma` in the current coordinate space.
  @MainActor
  func blurred(_ sigma: Double, opacity: Double = 1, blend: BlendMode = .normal,
               _ body: (inout GraphicsContext) -> Void) {
    var c = self
    c.opacity = opacity
    c.blendMode = blend
    c.drawLayer { layer in
      layer.addFilter(.blur(radius: GlowknobBlurCalibration.radius(sigma: sigma)))
      body(&layer)
    }
  }

  /// RN Skia `<GlowknobBox>` inner `BoxShadow`: clip to the box, then a blurred
  /// "frame" (the box inflated by 10+|d| minus the box offset by d).
  @MainActor
  func innerShadow(_ box: (x: Double, y: Double, w: Double, h: Double, rx: Double, ry: Double),
                   dx: Double, dy: Double, sigma: Double, color: GlowknobRGBA, opacity: Double = 1) {
    var c = self
    c.clip(to: GlowknobSk.rrect(box.x, box.y, box.w, box.h, box.rx, box.ry))
    let ix = 10 + abs(dx), iy = 10 + abs(dy)
    var ring = GlowknobSk.rrect(box.x - ix, box.y - iy, box.w + 2 * ix, box.h + 2 * iy, box.rx + ix, box.ry + iy)
    ring.addPath(GlowknobSk.rrect(box.x + dx, box.y + dy, box.w, box.h, box.rx, box.ry))
    c.blurred(sigma, opacity: opacity) { l in
      l.fill(ring, with: .color(color.color), style: FillStyle(eoFill: true))
    }
  }
}
