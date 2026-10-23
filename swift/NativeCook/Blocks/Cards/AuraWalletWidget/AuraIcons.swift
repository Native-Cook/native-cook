// The source's paths, expressed in Core Graphics rather than an SVG view.
import UIKit

enum AuraIcons {
  static func path(_ name: String) -> CGPath {
    let path = CGMutablePath()
    func move(_ x: Double, _ y: Double) { path.move(to: CGPoint(x: x, y: y)) }
    func line(_ x: Double, _ y: Double) { path.addLine(to: CGPoint(x: x, y: y)) }
    func quad(_ cx: Double, _ cy: Double, _ x: Double, _ y: Double) {
      path.addQuadCurve(to: CGPoint(x: x, y: y), control: CGPoint(x: cx, y: cy))
    }
    switch name {
    case "settings":
      move(10, 3.2)
      quad(12, 2.1, 14, 3.2)
      line(19, 6.1)
      quad(21, 7.2, 21, 9.5)
      line(21, 14.5)
      quad(21, 16.8, 19, 17.9)
      line(14, 20.8)
      quad(12, 21.9, 10, 20.8)
      line(5, 17.9)
      quad(3, 16.8, 3, 14.5)
      line(3, 9.5)
      quad(3, 7.2, 5, 6.1)
      path.closeSubpath()
      path.addEllipse(in: CGRect(x: 8.6, y: 8.6, width: 6.8, height: 6.8))
    case "swap":
      move(8, 17)
      line(8, 3)
      move(3, 8)
      line(8, 3)
      line(13, 8)
      move(16, 7)
      line(16, 21)
      move(11, 16)
      line(16, 21)
      line(21, 16)
    case "receive":
      move(12, 4)
      line(12, 20)
      move(6, 14)
      line(12, 20)
      line(18, 14)
    case "send":
      move(12, 20)
      line(12, 4)
      move(6, 10)
      line(12, 4)
      line(18, 10)
    case "info":
      move(12, 11)
      line(12, 17)
      move(12, 7)
      line(12, 7.1)
    case "orbit":
      path.addEllipse(in: CGRect(x: 7, y: 7, width: 10, height: 10))
      move(8, 9.1)
      path.addCurve(
        to: CGPoint(x: 3.2, y: 14.8), control1: CGPoint(x: 3.5, y: 10.8),
        control2: CGPoint(x: 1.9, y: 13.4))
      path.addCurve(
        to: CGPoint(x: 16.2, y: 13.3), control1: CGPoint(x: 4.9, y: 16.8),
        control2: CGPoint(x: 11, y: 16))
      path.addCurve(
        to: CGPoint(x: 20.6, y: 7.6), control1: CGPoint(x: 20.6, y: 11),
        control2: CGPoint(x: 22, y: 8.8))
      path.addCurve(
        to: CGPoint(x: 15.6, y: 7.6), control1: CGPoint(x: 19.8, y: 6.9),
        control2: CGPoint(x: 17.7, y: 7))
    default:
      break
    }
    return path
  }
}
