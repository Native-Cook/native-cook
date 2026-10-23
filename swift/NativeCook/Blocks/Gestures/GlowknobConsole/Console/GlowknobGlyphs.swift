import SwiftUI

/// Reference icon silhouettes for the two in-artwork mode selectors
/// (glyphs.tsx, viewBox 26 × 26).
enum GlowknobGlyphs {
  static func sunRing() -> Path { GlowknobSk.circle(13, 13, 6) }

  static func sunRays() -> Path {
    var p = Path()
    for i in 0..<8 {
      let a = Double(i) * Double.pi / 4
      p.move(to: GlowknobSk.pt(13 + sin(a) * 10, 13 + cos(a) * 10))
      p.addLine(to: GlowknobSk.pt(13 + sin(a) * 12, 13 + cos(a) * 12))
    }
    return p
  }

  /// "M3 9H9L17 3V23L9 17H3Z M22 10V16"
  static func speaker() -> Path {
    var p = Path()
    p.move(to: GlowknobSk.pt(3, 9))
    p.addLine(to: GlowknobSk.pt(9, 9))
    p.addLine(to: GlowknobSk.pt(17, 3))
    p.addLine(to: GlowknobSk.pt(17, 23))
    p.addLine(to: GlowknobSk.pt(9, 17))
    p.addLine(to: GlowknobSk.pt(3, 17))
    p.closeSubpath()
    p.move(to: GlowknobSk.pt(22, 10))
    p.addLine(to: GlowknobSk.pt(22, 16))
    return p
  }

  /// device-art.tsx's baked glyph paths (one path per glyph, stroke 1.6).
  static func bakedPath(_ mode: GlowknobChannel) -> Path {
    if mode == 1 { return speaker() }
    var p = sunRing()
    p.addPath(sunRays())
    return p
  }
}

/// `GlowknobReferenceGlyph`: the native (react-native-svg) selector icon.
struct GlowknobReferenceGlyph: View {
  let mode: GlowknobChannel
  let size: Double
  let tint: GlowknobRGBA

  var body: some View {
    let _ = GlowknobPerf.count("GlowknobReferenceGlyph")
    Canvas { ctx, bounds in
      // react-native-svg: viewBox 0 0 26 26 fitted to the view's laid-out
      // (pixel-rounded) bounds, preserveAspectRatio xMidYMid meet.
      let k = min(bounds.width, bounds.height) / 26
      ctx.translateBy(x: (bounds.width - 26 * k) / 2, y: (bounds.height - 26 * k) / 2)
      ctx.scaleBy(x: k, y: k)
      let color = GraphicsContext.Shading.color(tint.color)
      if mode == 0 {
        ctx.stroke(GlowknobGlyphs.sunRing(), with: color, lineWidth: 1.5)
        ctx.stroke(GlowknobGlyphs.sunRays(), with: color, style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
      } else {
        ctx.stroke(GlowknobGlyphs.speaker(), with: color,
                   style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
      }
    }
    .accessibilityHidden(true)
  }
}
