import SwiftUI

/// A Skia color: unpremultiplied sRGB-encoded floats, as `Skia.Color(...)`
/// returns them. Gradients and blends here work on these encoded values,
/// like Skia does on an untagged surface.
struct GlowknobRGBA: Equatable, Sendable {
  var r: Double
  var g: Double
  var b: Double
  var a: Double

  init(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) {
    self.r = r
    self.g = g
    self.b = b
    self.a = a
  }

  /// "#RRGGBB" or "#RRGGBBAA" (CSS order: alpha last, as RN Skia parses it).
  init(_ hex: String) {
    var s = hex
    if s.hasPrefix("#") { s.removeFirst() }
    let v = UInt64(s, radix: 16) ?? 0
    if s.count == 8 {
      self.init(Double((v >> 24) & 0xFF) / 255, Double((v >> 16) & 0xFF) / 255,
                Double((v >> 8) & 0xFF) / 255, Double(v & 0xFF) / 255)
    } else {
      self.init(Double((v >> 16) & 0xFF) / 255, Double((v >> 8) & 0xFF) / 255, Double(v & 0xFF) / 255, 1)
    }
  }

  /// CSS `rgba(r,g,b,a)` with 0…255 channels.
  static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> GlowknobRGBA {
    GlowknobRGBA(r / 255, g / 255, b / 255, a)
  }

  var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }
  var cgColor: CGColor {
    CGColor(colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!, components: [r, g, b, a])!
  }
  var uiColor: UIColor { UIColor(cgColor: cgColor) }

  func withAlpha(_ alpha: Double) -> GlowknobRGBA { GlowknobRGBA(r, g, b, alpha) }

  static func mix(_ t: Double, _ x: GlowknobRGBA, _ y: GlowknobRGBA) -> GlowknobRGBA {
    GlowknobRGBA(x.r + (y.r - x.r) * t, x.g + (y.g - x.g) * t, x.b + (y.b - x.b) * t, x.a + (y.a - x.a) * t)
  }
}

/// RN Skia `interpolate(x, input, output, "clamp")`, piecewise linear.
func glowknobInterpolate(_ x: Double, _ input: [Double], _ output: [Double]) -> Double {
  if x <= input[0] { return output[0] }
  if x >= input[input.count - 1] { return output[output.count - 1] }
  var i = 1
  while i < input.count - 1 && x > input[i] { i += 1 }
  let a = input[i - 1], b = input[i]
  let t = b == a ? 0 : (x - a) / (b - a)
  return output[i - 1] + (output[i] - output[i - 1]) * t
}

/// RN Skia `interpolateColors`: each GlowknobRGBA channel interpolated linearly in
/// encoded sRGB between the stops, clamped at the ends.
func glowknobInterpolateColors(_ value: Double, _ input: [Double], _ colors: [GlowknobRGBA]) -> GlowknobRGBA {
  GlowknobRGBA(glowknobInterpolate(value, input, colors.map(\.r)), glowknobInterpolate(value, input, colors.map(\.g)),
       glowknobInterpolate(value, input, colors.map(\.b)), glowknobInterpolate(value, input, colors.map(\.a)))
}

/// Gradient stops interpolated the way Skia does by default: per channel on
/// unpremultiplied colors. SwiftUI blends stops in premultiplied space, which
/// only differs when a stop's alpha changes together with its color (a color
/// fading to "#00000000"); those runs are pre-sampled densely so both agree.
func glowknobSkiaGradient(_ colors: [GlowknobRGBA], positions: [Double]? = nil) -> Gradient {
  let pos = positions ?? colors.indices.map { colors.count == 1 ? 0 : Double($0) / Double(colors.count - 1) }
  var stops: [Gradient.Stop] = []
  for i in colors.indices {
    if i > 0 {
      let a = colors[i - 1], b = colors[i]
      let rgbDiffers = a.r != b.r || a.g != b.g || a.b != b.b
      if a.a != b.a && rgbDiffers {
        let n = 24
        for k in 1..<n {
          let t = Double(k) / Double(n)
          stops.append(.init(color: GlowknobRGBA.mix(t, a, b).color, location: pos[i - 1] + (pos[i] - pos[i - 1]) * t))
        }
      }
    }
    stops.append(.init(color: colors[i].color, location: pos[i]))
  }
  return Gradient(stops: stops)
}
