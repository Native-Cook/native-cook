import CoreText
import SwiftUI
import UIKit

/// Core Graphics primitives preserve Skia's logical coordinates and text baseline.
struct SetupChecklistAllSetDrawing {
  struct Gradient {
    let x0: Double, x1: Double
    let colors: [String], alphas: [Double]
    init(_ x0: Double, _ x1: Double, _ colors: [String], _ alphas: [Double]) {
      self.x0 = x0
      self.x1 = x1
      self.colors = colors
      self.alphas = alphas
    }
  }
  final class Paint {
    var rgba = [0.0, 0.0, 0.0, 1.0]
    var lineWidth: Double?
    var gradient: Gradient?
    var alpha = 1.0
    func setAntiAlias(_ enabled: Bool) {}
    func setColor(_ value: [Double]) { rgba = value }
    func setAlphaf(_ value: Double) { alpha = value }
  }
  struct Font {
    let face: CTFont
    let size: Double
    init(_ weight: String, _ size: Double) {
      self.size = size
      let url = Bundle.main.url(
        forResource: "SetupChecklistAllSet-Inter-" + weight, withExtension: "ttf")!
      let descriptors =
        CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as! [CTFontDescriptor]
      face = CTFontCreateWithFontDescriptor(descriptors[0], size, nil)
    }
    func width(_ string: String) -> Double {
      let line = CTLineCreateWithAttributedString(
        NSAttributedString(string: string, attributes: [.font: face]))
      return CTLineGetTypographicBounds(line, nil, nil, nil)
    }
  }
  final class Path {
    let value = CGMutablePath()
    func moveTo(_ x: Double, _ y: Double) { value.move(to: CGPoint(x: x, y: y)) }
    func lineTo(_ x: Double, _ y: Double) { value.addLine(to: CGPoint(x: x, y: y)) }
    func quadTo(_ x: Double, _ y: Double, _ ex: Double, _ ey: Double) {
      value.addQuadCurve(to: CGPoint(x: ex, y: ey), control: CGPoint(x: x, y: y))
    }
    func cubicTo(_ x: Double, _ y: Double, _ x2: Double, _ y2: Double, _ ex: Double, _ ey: Double) {
      value.addCurve(
        to: CGPoint(x: ex, y: ey), control1: CGPoint(x: x, y: y), control2: CGPoint(x: x2, y: y2))
    }
    func close() { value.closeSubpath() }
    func addRRect(_ rounded: CGPath) { value.addPath(rounded) }
    func addCircle(_ x: Double, _ y: Double, _ radius: Double) {
      value.addEllipse(
        in: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
    }
    func addArc(_ rect: CGRect, _ start: Double, _ sweep: Double) {
      value.addArc(
        center: CGPoint(x: rect.midX, y: rect.midY), radius: rect.width / 2,
        startAngle: start * Double.pi / 180, endAngle: (start + sweep) * Double.pi / 180,
        clockwise: false)
    }
  }
  final class Surface {
    let cg: CGContext
    init(_ cg: CGContext) { self.cg = cg }
    func save() { cg.saveGState() }
    func restore() { cg.restoreGState() }
    func translate(_ x: Double, _ y: Double) { cg.translateBy(x: x, y: y) }
    func scale(_ x: Double, _ y: Double) { cg.scaleBy(x: x, y: y) }
    func rotate(_ degrees: Double) { cg.rotate(by: degrees * Double.pi / 180) }
    func clipRect(_ rect: CGRect) { cg.clip(to: rect) }
    func clipRRect(_ path: CGPath) {
      cg.addPath(path)
      cg.clip()
    }
    func drawColor(_ rgba: [Double]) {
      cg.setFillColor(Self.color(rgba))
      cg.fill(cg.boundingBoxOfClipPath)
    }
    static func color(_ rgba: [Double]) -> CGColor {
      CGColor(
        colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!, components: rgba.map { CGFloat($0) })!
    }
    func render(_ path: CGPath, _ paint: Paint) {
      cg.saveGState()
      cg.setAlpha(paint.alpha)
      cg.addPath(path)
      if let width = paint.lineWidth {
        cg.setStrokeColor(Self.color(paint.rgba))
        cg.setLineWidth(width)
        cg.setLineCap(.round)
        cg.setLineJoin(.round)
        cg.strokePath()
      } else if let gradient = paint.gradient {
        cg.clip()
        let colors = zip(gradient.colors, gradient.alphas).map { hex, alpha in
          Self.color(SetupChecklistAllSetArt.col(hex, alpha))
        }
        let locations: [CGFloat] = colors.count == 3 ? [0, 0.5, 1] : [0, 1]
        if let shader = CGGradient(
          colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors as CFArray,
          locations: locations)
        {
          cg.drawLinearGradient(
            shader, start: CGPoint(x: gradient.x0, y: 0), end: CGPoint(x: gradient.x1, y: 0),
            options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }
      } else {
        cg.setFillColor(Self.color(paint.rgba))
        cg.fillPath()
      }
      cg.restoreGState()
    }
    func drawCircle(_ x: Double, _ y: Double, _ radius: Double, _ paint: Paint) {
      render(
        CGPath(
          ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2),
          transform: nil), paint)
    }
    func drawOval(_ rect: CGRect, _ paint: Paint) {
      render(CGPath(ellipseIn: rect, transform: nil), paint)
    }
    func drawRect(_ rect: CGRect, _ paint: Paint) {
      render(CGPath(rect: rect, transform: nil), paint)
    }
    func drawRRect(_ path: CGPath, _ paint: Paint) { render(path, paint) }
    func drawPath(_ path: Path, _ paint: Paint) { render(path.value, paint) }
    func drawLine(_ x: Double, _ y: Double, _ ex: Double, _ ey: Double, _ paint: Paint) {
      let path = Path()
      path.moveTo(x, y)
      path.lineTo(ex, ey)
      drawPath(path, paint)
    }
  }
}
