import SwiftUI

// A small immediate-mode adapter keeps the source's save/restore and pin-pixel path coordinates.
final class ListeningBuddyPaywallCanvas {
  var context: GraphicsContext
  private var stack: [GraphicsContext] = []
  init(_ context: GraphicsContext) { self.context = context }
  func save() { stack.append(context) }
  func restore() { context = stack.removeLast() }
  func translate(_ x: Double, _ y: Double) { context.translateBy(x: x, y: y) }
  func scale(_ x: Double, _ y: Double) { context.scaleBy(x: x, y: y) }
  func rotate(_ degrees: Double, _ x: Double, _ y: Double) {
    translate(x, y)
    context.rotate(by: .degrees(degrees))
    translate(-x, -y)
  }
  func clipPath(_ path: ListeningBuddyPaywallPath) { context.clip(to: Path(path.path)) }
  func clipRect(_ rect: CGRect) { context.clip(to: Path(rect)) }
  func clipRRect(_ path: ListeningBuddyPaywallPath) { clipPath(path) }
  func drawPath(_ path: ListeningBuddyPaywallPath, _ paint: ListeningBuddyPaywallPaint) {
    var layer = context
    if paint.blur > 0 { layer.addFilter(.blur(radius: paint.blur)) }
    if let width = paint.width {
      layer.stroke(
        Path(path.path), with: .color(paint.color),
        style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    } else {
      layer.fill(Path(path.path), with: .color(paint.color))
    }
  }
  func drawRect(_ rect: CGRect, _ paint: ListeningBuddyPaywallPaint) {
    drawPath(ListeningBuddyPaywallPath(CGPath(rect: rect, transform: nil)), paint)
  }
  func drawOval(_ rect: CGRect, _ paint: ListeningBuddyPaywallPaint) {
    drawPath(ListeningBuddyPaywallPath(CGPath(ellipseIn: rect, transform: nil)), paint)
  }
  func drawCircle(_ x: Double, _ y: Double, _ r: Double, _ paint: ListeningBuddyPaywallPaint) {
    drawOval(CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2), paint)
  }
  func drawLine(
    _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ paint: ListeningBuddyPaywallPaint
  ) {
    let path = ListeningBuddyPaywallPath()
    path.moveTo(x1, y1)
    path.lineTo(x2, y2)
    drawPath(path, paint)
  }
  func drawRRect(_ path: ListeningBuddyPaywallPath, _ paint: ListeningBuddyPaywallPaint) {
    drawPath(path, paint)
  }
  func drawText(
    _ text: String, _ x: Double, _ baseline: Double, _ paint: ListeningBuddyPaywallPaint,
    _ font: ListeningBuddyPaywallFont
  ) {
    let glyphs = font.outline(text)
    var transform = CGAffineTransform(translationX: x, y: baseline).scaledBy(x: 1, y: -1)
    if let path = glyphs.copy(using: &transform) {
      drawPath(ListeningBuddyPaywallPath(path), paint)
    }
  }
}
