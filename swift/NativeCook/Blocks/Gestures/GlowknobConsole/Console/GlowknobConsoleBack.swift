import CoreGraphics
import CoreText
import UIKit

// Surfaces of the lifted console that only exist in 3D, painted with Core
// Graphics into textures SceneKit samples (console-back.ts). Drawing units
// match DeviceArt: the housing is 1000 × 284.
private let W = 1000.0
private let H = 284.0

private func face(_ size: Double, _ weight: String = "400") -> CTFont {
  // RN Skia matchFont("Helvetica Neue", weight) on iOS.
  let name: String
  switch weight {
  case "300": name = "HelveticaNeue-Light"
  case "500": name = "HelveticaNeue-Medium"
  default: name = "HelveticaNeue"
  }
  return CTFontCreateWithName(name as CFString, size, nil)
}

// Deterministic noise so the brushing is the same on every lift.
private struct Random {
  var s: Int64
  mutating func next() -> Double {
    s = (s * 16807) % 2147483647
    return Double(s) / 2147483647
  }
}

private func color(_ c: GlowknobRGBA) -> CGColor { c.cgColor }

/// One glyph's advance (RN Skia `font.measureText(ch).width`; measured
/// against the RN capture: the mark's spacing matches advances exactly).
private func advance(_ ch: String, _ font: CTFont) -> Double {
  let line = CTLineCreateWithAttributedString(NSAttributedString(string: ch, attributes: [.font: font]))
  return CTLineGetTypographicBounds(line, nil, nil, nil)
}

private func spaced(_ c: CGContext, _ text: String, _ font: CTFont, _ cx: Double, _ y: Double,
                    _ tracking: Double, _ tint: GlowknobRGBA) {
  let chars = text.map(String.init)
  let widths = chars.map { advance($0, font) }
  let total = widths.reduce(0, +) + tracking * Double(chars.count - 1)
  var x = cx - total / 2
  for (i, ch) in chars.enumerated() {
    let line = CTLineCreateWithAttributedString(NSAttributedString(
      string: ch, attributes: [.font: font, .foregroundColor: color(tint)]))
    c.saveGState()
    // The context is y-down; CoreText draws y-up from the baseline.
    c.textMatrix = .identity
    c.translateBy(x: x, y: y)
    c.scaleBy(x: 1, y: -1)
    c.textPosition = .zero
    CTLineDraw(line, c)
    c.restoreGState()
    x += widths[i] + tracking
  }
}

// Laser-etched text: a dark lip above, a faint catch-light below, then the
// frosted fill, so it reads as cut into the metal.
private func engraved(_ c: CGContext, _ text: String, _ font: CTFont, _ cx: Double, _ y: Double,
                      _ tracking: Double, _ alpha: Double) {
  spaced(c, text, font, cx, y - 1, tracking, .rgba(0, 0, 0, 0.55 * alpha))
  spaced(c, text, font, cx, y + 1, tracking, .rgba(255, 255, 255, 0.14 * alpha))
  spaced(c, text, font, cx, y, tracking, .rgba(200, 200, 205, 0.75 * alpha))
}

private func linearGradient(_ colors: [GlowknobRGBA], _ locations: [CGFloat]) -> CGGradient {
  CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray,
             locations: locations)!
}

private func screw(_ c: CGContext, _ x: Double, _ y: Double) {
  c.saveGState()
  c.addEllipse(in: CGRect(x: x - 12, y: y - 12, width: 24, height: 24))
  c.clip()
  c.drawLinearGradient(linearGradient(["#5C5C60", "#2A2A2D", "#141416"].map(GlowknobRGBA.init), [0, 0.5, 1]),
                       start: CGPoint(x: x, y: y - 12), end: CGPoint(x: x, y: y + 12),
                       options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
  c.restoreGState()
  c.setStrokeColor(color(.rgba(0, 0, 0, 0.7)))
  c.setLineWidth(1.4)
  c.strokeEllipse(in: CGRect(x: x - 12.6, y: y - 12.6, width: 25.2, height: 25.2))
  // Six-lobe drive.
  let drive = CGMutablePath()
  for i in 0..<12 {
    let a = Double(i) * Double.pi / 6 - Double.pi / 2
    let r = i % 2 == 0 ? 5.6 : 3.2
    let p = CGPoint(x: x + cos(a) * r, y: y + sin(a) * r)
    if i == 0 { drive.move(to: p) } else { drive.addLine(to: p) }
  }
  drive.closeSubpath()
  c.setFillColor(color(GlowknobRGBA("#0C0C0D")))
  c.addPath(drive)
  c.fillPath()
}

/// The back plate: dark anodized aluminium, brushed, with an etched mark.
/// `c` is y-down, in drawing units.
func glowknobPaintConsoleBack(_ c: CGContext) {
  let rr = CGPath(roundedRect: CGRect(x: 0, y: 0, width: W, height: H), cornerWidth: H / 2, cornerHeight: H / 2,
                  transform: nil)
  c.saveGState()
  c.addPath(rr)
  c.clip()
  c.drawLinearGradient(linearGradient(["#5A5A5F", "#45454A", "#36363A"].map(GlowknobRGBA.init), [0, 0.5, 1]),
                       start: .zero, end: CGPoint(x: 0, y: H),
                       options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
  var r = Random(s: 11)
  c.setLineWidth(0.7)
  c.setLineCap(.butt)
  for _ in 0..<420 {
    let y = r.next() * H
    let light = r.next() < 0.5
    let tint: GlowknobRGBA = light ? .rgba(255, 255, 255, 0.012 + r.next() * 0.03) : .rgba(0, 0, 0, 0.04 + r.next() * 0.07)
    let x0 = r.next() * 300
    let x1 = x0 + 500 + r.next() * 500
    c.setStrokeColor(color(tint))
    c.move(to: CGPoint(x: x0, y: y))
    c.addLine(to: CGPoint(x: x1, y: y))
    c.strokePath()
  }
  c.restoreGState()

  engraved(c, "GLOWKNOB", face(40, "500"), W / 2, 136, 15, 1)
  engraved(c, "TACTILE CONSOLE  ·  GK-01", face(14, "500"), W / 2, 170, 4.5, 0.75)
  engraved(c, "Designed in Saigon  ·  Painted by SwiftUI  ·  Lit by SceneKit", face(11.5), W / 2, 226, 1.2, 0.55)

  // The two channels, as small inlaid dots beside the mark.
  c.setFillColor(color(GlowknobTactile.warm[1]))
  c.fillEllipse(in: CGRect(x: W / 2 - 218 - 4.5, y: 122 - 4.5, width: 9, height: 9))
  c.setFillColor(color(GlowknobTactile.cool[1]))
  c.fillEllipse(in: CGRect(x: W / 2 + 218 - 4.5, y: 122 - 4.5, width: 9, height: 9))

  for (x, y) in [(186.0, 78.0), (814, 78), (186, 206), (814, 206)] { screw(c, x, y) }

  // Rim: a soft dark edge where the plate meets the chamfer.
  c.addPath(rr)
  c.setStrokeColor(color(.rgba(0, 0, 0, 0.45)))
  c.setLineWidth(3)
  c.strokePath()
}

/// Knurled edge of the knob, tiled around the cylinder. Each ridge is a soft
/// gradient, not hard lines: the texture has no mipmaps, so hard edges would
/// shimmer as the knob turns.
func glowknobPaintKnurl(_ c: CGContext, width: Double, height: Double) {
  let ridges = 16
  let step = width / Double(ridges)
  let g = linearGradient(["#1A1A1C", "#2E2E31", "#4E4E52", "#2E2E31", "#1A1A1C"].map(GlowknobRGBA.init),
                         [0, 0.3, 0.55, 0.8, 1])
  // TileMode.Repeat: one gradient period per ridge.
  for i in 0..<ridges {
    let x = Double(i) * step
    c.saveGState()
    c.clip(to: CGRect(x: x, y: 0, width: step, height: height))
    c.drawLinearGradient(g, start: CGPoint(x: x, y: 0), end: CGPoint(x: x + step, y: 0), options: [])
    c.restoreGState()
  }
}

/// A y-down sRGB bitmap context of `pixels`, scaled so drawing units fill it.
func glowknobMakeTexture(pixels: CGSize, units: CGSize, _ draw: (CGContext) -> Void) -> CGImage? {
  guard let c = CGContext(data: nil, width: Int(pixels.width), height: Int(pixels.height), bitsPerComponent: 8,
                          bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
  c.translateBy(x: 0, y: pixels.height)
  c.scaleBy(x: 1, y: -1)
  c.scaleBy(x: pixels.width / units.width, y: pixels.height / units.height)
  c.setShouldAntialias(true)
  draw(c)
  return c.makeImage()
}
