import CoreGraphics

final class ListeningBuddyPaywallPath {
  let path: CGMutablePath
  init(_ source: CGPath? = nil) {
    path = CGMutablePath()
    if let source { path.addPath(source) }
  }
  func moveTo(_ x: Double, _ y: Double) { path.move(to: CGPoint(x: x, y: y)) }
  func lineTo(_ x: Double, _ y: Double) { path.addLine(to: CGPoint(x: x, y: y)) }
  func cubicTo(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double, _ y: Double) {
    path.addCurve(
      to: CGPoint(x: x, y: y), control1: CGPoint(x: x1, y: y1), control2: CGPoint(x: x2, y: y2))
  }
  func quadTo(_ x1: Double, _ y1: Double, _ x: Double, _ y: Double) {
    path.addQuadCurve(to: CGPoint(x: x, y: y), control: CGPoint(x: x1, y: y1))
  }
  func close() { path.closeSubpath() }
  func addArc(_ rect: CGRect, _ start: Double, _ sweep: Double) {
    let arc = CGMutablePath()
    arc.addArc(
      center: .zero, radius: 1, startAngle: start * .pi / 180,
      endAngle: (start + sweep) * .pi / 180, clockwise: sweep < 0)
    let transform = CGAffineTransform(translationX: rect.midX, y: rect.midY)
      .scaledBy(x: rect.width / 2, y: rect.height / 2)
    path.addPath(arc, transform: transform)
  }
}
