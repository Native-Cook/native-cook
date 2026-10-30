import SwiftUI

extension GreatWaveMorningArt {
  final class Shape {
    var path = Path()
    init() {}
    init(_ path: Path) { self.path = path }
    func moveTo(_ x: Double, _ y: Double) { path.move(to: CGPoint(x: x, y: y)) }
    func lineTo(_ x: Double, _ y: Double) { path.addLine(to: CGPoint(x: x, y: y)) }
    func cubicTo(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double, _ y: Double) {
      path.addCurve(
        to: CGPoint(x: x, y: y), control1: CGPoint(x: x1, y: y1), control2: CGPoint(x: x2, y: y2))
    }
    func close() { path.closeSubpath() }
  }
  final class Paint {
    var rgba: [Double] = [0, 0, 0, 1]
    var shader: GraphicsContext.Shading?
    var width: Double?
    var blur = 0.0
    func setAntiAlias(_ enabled: Bool) {}  // Canvas antialiases paths by default.
    func setColor(_ rgba: [Double]) { self.rgba = rgba }
    func setShader(_ shader: GraphicsContext.Shading) { self.shader = shader }
    func setMaskFilter(_ sigma: Double) { blur = sigma }
    var color: Color { Color(.sRGB, red: rgba[0], green: rgba[1], blue: rgba[2], opacity: rgba[3]) }
    var shading: GraphicsContext.Shading { shader ?? .color(color) }
  }
  func col(_ hex: Int, _ alpha: Double = 1) -> [Double] {
    [
      Double((hex >> 16) & 255) / 255, Double((hex >> 8) & 255) / 255, Double(hex & 255) / 255,
      alpha,
    ]
  }
  func mixCol(_ a: Int, _ b: Int, _ k: Double, _ alpha: Double = 1) -> [Double] {
    let x = col(a)
    let y = col(b)
    return [mix(x[0], y[0], k), mix(x[1], y[1], k), mix(x[2], y[2], k), alpha]
  }
  func rgb(_ rgba: [Double]) -> Int {
    (Int((rgba[0] * 255).rounded()) << 16) | (Int((rgba[1] * 255).rounded()) << 8)
      | Int((rgba[2] * 255).rounded())
  }
  func paint(_ hex: Int, _ alpha: Double = 1) -> Paint {
    let result = Paint()
    result.setColor(col(hex, alpha))
    return result
  }
  func stroke(_ hex: Int, _ width: Double, _ alpha: Double = 1) -> Paint {
    let result = paint(hex, alpha)
    result.width = width
    return result
  }
  func shadowPaint(_ alpha: Double, _ blur: Double) -> Paint {
    let result = paint(0x06142B, alpha)
    result.blur = blur
    return result
  }
  func gradient(_ from: CGPoint, _ to: CGPoint, _ colors: [[Double]], _ stops: [Double]?)
    -> GraphicsContext.Shading
  {
    let positions = stops ?? colors.indices.map { Double($0) / Double(max(1, colors.count - 1)) }
    let stops = zip(colors, positions).map { rgba, position in
      Gradient.Stop(
        color: Color(.sRGB, red: rgba[0], green: rgba[1], blue: rgba[2], opacity: rgba[3]),
        location: position)
    }
    return .linearGradient(Gradient(stops: stops), startPoint: from, endPoint: to)
  }
  func rect(_ x: Double, _ y: Double, _ width: Double, _ height: Double) -> CGRect {
    CGRect(x: x, y: y, width: width, height: height)
  }
  func rrect(_ x: Double, _ y: Double, _ width: Double, _ height: Double, _ radius: Double) -> Shape
  {
    Shape(Path(roundedRect: rect(x, y, width, height), cornerRadius: radius))
  }
  func save() { stack.append(context) }
  func restore() { if let saved = stack.popLast() { context = saved } }
  func translate(_ x: Double, _ y: Double) { context.translateBy(x: x, y: y) }
  func scale(_ x: Double, _ y: Double) { context.scaleBy(x: x, y: y) }
  func rotate(_ degrees: Double, _ x: Double, _ y: Double) {
    translate(x, y)
    context.rotate(by: .degrees(degrees))
    translate(-x, -y)
  }
  func clipPath(_ shape: Shape) { context.clip(to: shape.path) }
  func clipRRect(_ shape: Shape) { clipPath(shape) }
  func drawPath(_ shape: Shape, _ paint: Paint) {
    // Blur only this primitive, keeping the fill and the next layer sharp.
    var draw = context
    if paint.blur > 0 { draw.addFilter(.blur(radius: paint.blur)) }
    if let width = paint.width {
      draw.stroke(
        shape.path, with: paint.shading,
        style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    } else {
      draw.fill(shape.path, with: paint.shading)
    }
  }
  func drawRRect(_ shape: Shape, _ paint: Paint) { drawPath(shape, paint) }
  func drawRect(_ rect: CGRect, _ paint: Paint) { drawPath(Shape(Path(rect)), paint) }
  func drawOval(_ rect: CGRect, _ paint: Paint) { drawPath(Shape(Path(ellipseIn: rect)), paint) }
  func drawCircle(_ x: Double, _ y: Double, _ radius: Double, _ paint: Paint) {
    drawOval(rect(x - radius, y - radius, radius * 2, radius * 2), paint)
  }
  func drawLine(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ paint: Paint) {
    let path = Shape()
    path.moveTo(x1, y1)
    path.lineTo(x2, y2)
    drawPath(path, paint)
  }
  @discardableResult
  func text(
    _ canvas: GreatWaveMorningArt, _ string: String, _ x: Double, _ cy: Double,
    _ font: GreatWaveMorningResources.Face, _ paint: Paint, _ anchor: Double = 0
  ) -> Double {
    let label = GreatWaveMorningResources.shared.label(string, font)
    var image = canvas.context.resolve(label.image)
    image.shading = .color(paint.color)
    canvas.context.draw(
      image,
      in: CGRect(
        x: x - label.width * anchor - 2,
        y: cy + font.size * 0.36 - label.baseline, width: label.imageSize.width,
        height: label.imageSize.height))
    return label.width
  }
}
