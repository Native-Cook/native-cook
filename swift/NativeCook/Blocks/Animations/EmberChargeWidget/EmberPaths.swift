import SwiftUI

/// Original RN paths, retained once; no SF Symbol substitutions.
/// Literal points are the authored Bézier control points from ember-theme.ts,
/// not layout positions. Keeping them beside each curve makes the silhouettes inspectable.
enum EmberPaths {
  static let walkFill0: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 11.55, y: 3.75))
    p.addCurve(
      to: CGPoint(x: 14.1, y: 1.2), control1: CGPoint(x: 11.55, y: 2.34),
      control2: CGPoint(x: 12.69, y: 1.2))
    p.addCurve(
      to: CGPoint(x: 16.65, y: 3.75), control1: CGPoint(x: 15.51, y: 1.2),
      control2: CGPoint(x: 16.65, y: 2.34))
    p.addCurve(
      to: CGPoint(x: 14.1, y: 6.3), control1: CGPoint(x: 16.65, y: 5.16),
      control2: CGPoint(x: 15.51, y: 6.3))
    p.addCurve(
      to: CGPoint(x: 11.55, y: 3.75), control1: CGPoint(x: 12.69, y: 6.3),
      control2: CGPoint(x: 11.55, y: 5.16))
    p.closeSubpath()
    return p
  }()
  static let walkFill1: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 10.9, y: 7.05))
    p.addCurve(
      to: CGPoint(x: 14.95, y: 8.43), control1: CGPoint(x: 12.45, y: 6.82),
      control2: CGPoint(x: 13.7, y: 7.32))
    p.addCurve(
      to: CGPoint(x: 18.48, y: 9.12), control1: CGPoint(x: 16.18, y: 9.52),
      control2: CGPoint(x: 16.98, y: 9.89))
    p.addCurve(
      to: CGPoint(x: 20.28, y: 9.72), control1: CGPoint(x: 19.22, y: 8.73),
      control2: CGPoint(x: 19.95, y: 9.02))
    p.addCurve(
      to: CGPoint(x: 19.62, y: 11.52), control1: CGPoint(x: 20.62, y: 10.45),
      control2: CGPoint(x: 20.3, y: 11.12))
    p.addCurve(
      to: CGPoint(x: 14.42, y: 11.35), control1: CGPoint(x: 17.66, y: 12.65),
      control2: CGPoint(x: 16.13, y: 12.25))
    p.addLine(to: CGPoint(x: 13.82, y: 14.9))
    p.addCurve(
      to: CGPoint(x: 17.66, y: 19.16), control1: CGPoint(x: 15.67, y: 15.98),
      control2: CGPoint(x: 17.05, y: 17.23))
    p.addLine(to: CGPoint(x: 18.42, y: 21.79))
    p.addCurve(
      to: CGPoint(x: 17.32, y: 23.5), control1: CGPoint(x: 18.69, y: 22.72),
      control2: CGPoint(x: 18.18, y: 23.38))
    p.addCurve(
      to: CGPoint(x: 15.61, y: 22.3), control1: CGPoint(x: 16.46, y: 23.62),
      control2: CGPoint(x: 15.86, y: 23.17))
    p.addLine(to: CGPoint(x: 14.93, y: 20.05))
    p.addCurve(
      to: CGPoint(x: 12.13, y: 17.38), control1: CGPoint(x: 14.5, y: 18.67),
      control2: CGPoint(x: 13.49, y: 18))
    p.addLine(to: CGPoint(x: 10.54, y: 19.93))
    p.addCurve(
      to: CGPoint(x: 5.14, y: 22.9), control1: CGPoint(x: 9.53, y: 21.47),
      control2: CGPoint(x: 7.28, y: 22.49))
    p.addCurve(
      to: CGPoint(x: 3.35, y: 21.79), control1: CGPoint(x: 4.17, y: 23.09),
      control2: CGPoint(x: 3.46, y: 22.61))
    p.addCurve(
      to: CGPoint(x: 4.58, y: 20.09), control1: CGPoint(x: 3.23, y: 20.95),
      control2: CGPoint(x: 3.69, y: 20.31))
    p.addCurve(
      to: CGPoint(x: 8.12, y: 18.12), control1: CGPoint(x: 6.24, y: 19.69),
      control2: CGPoint(x: 7.6, y: 19.05))
    p.addLine(to: CGPoint(x: 9.26, y: 15.28))
    p.addLine(to: CGPoint(x: 9.79, y: 10.52))
    p.addCurve(
      to: CGPoint(x: 7.75, y: 13.88), control1: CGPoint(x: 8.49, y: 11.1),
      control2: CGPoint(x: 8.14, y: 12.19))
    p.addCurve(
      to: CGPoint(x: 6.31, y: 14.95), control1: CGPoint(x: 7.57, y: 14.7),
      control2: CGPoint(x: 7.02, y: 15.08))
    p.addCurve(
      to: CGPoint(x: 5.37, y: 13.4), control1: CGPoint(x: 5.56, y: 14.82),
      control2: CGPoint(x: 5.25, y: 14.18))
    p.addCurve(
      to: CGPoint(x: 10.9, y: 7.05), control1: CGPoint(x: 5.89, y: 9.99),
      control2: CGPoint(x: 7.64, y: 7.65))
    p.closeSubpath()
    return p
  }()
  static let routeFill0: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 16.95, y: 4.15))
    p.addCurve(
      to: CGPoint(x: 19.6, y: 1.5), control1: CGPoint(x: 16.95, y: 2.69),
      control2: CGPoint(x: 18.14, y: 1.5))
    p.addCurve(
      to: CGPoint(x: 22.25, y: 4.15), control1: CGPoint(x: 21.06, y: 1.5),
      control2: CGPoint(x: 22.25, y: 2.69))
    p.addCurve(
      to: CGPoint(x: 19.6, y: 6.8), control1: CGPoint(x: 22.25, y: 5.61),
      control2: CGPoint(x: 21.06, y: 6.8))
    p.addCurve(
      to: CGPoint(x: 16.95, y: 4.15), control1: CGPoint(x: 18.14, y: 6.8),
      control2: CGPoint(x: 16.95, y: 5.61))
    p.closeSubpath()
    return p
  }()
  static let routeFill1: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 2.29, y: 18.38))
    p.addCurve(
      to: CGPoint(x: 2.6, y: 17.7), control1: CGPoint(x: 2.05, y: 18.11),
      control2: CGPoint(x: 2.23, y: 17.7))
    p.addLine(to: CGPoint(x: 4.05, y: 17.7))
    p.addLine(to: CGPoint(x: 4.05, y: 16.75))
    p.addLine(to: CGPoint(x: 5.85, y: 16.75))
    p.addLine(to: CGPoint(x: 5.85, y: 17.7))
    p.addLine(to: CGPoint(x: 7.3, y: 17.7))
    p.addCurve(
      to: CGPoint(x: 7.61, y: 18.38), control1: CGPoint(x: 7.67, y: 17.7),
      control2: CGPoint(x: 7.85, y: 18.11))
    p.addLine(to: CGPoint(x: 5.28, y: 21.02))
    p.addCurve(
      to: CGPoint(x: 4.62, y: 21.02), control1: CGPoint(x: 5.1, y: 21.22),
      control2: CGPoint(x: 4.8, y: 21.22))
    p.closeSubpath()
    return p
  }()
  static let routeStroke0: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 4.95, y: 18.1))
    p.addLine(to: CGPoint(x: 4.95, y: 6.2))
    p.addCurve(
      to: CGPoint(x: 8.55, y: 2.2), control1: CGPoint(x: 4.95, y: 3.9),
      control2: CGPoint(x: 6.46, y: 2.2))
    p.addCurve(
      to: CGPoint(x: 12.15, y: 6.2), control1: CGPoint(x: 10.64, y: 2.2),
      control2: CGPoint(x: 12.15, y: 3.9))
    p.addLine(to: CGPoint(x: 12.15, y: 17.5))
    p.addCurve(
      to: CGPoint(x: 15.87, y: 21.5), control1: CGPoint(x: 12.15, y: 19.84),
      control2: CGPoint(x: 13.77, y: 21.5))
    p.addCurve(
      to: CGPoint(x: 19.6, y: 17.5), control1: CGPoint(x: 17.97, y: 21.5),
      control2: CGPoint(x: 19.6, y: 19.84))
    p.addLine(to: CGPoint(x: 19.6, y: 5.5))
    return p
  }()
  static let boltFill0: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 13.61, y: 1.25))
    p.addCurve(
      to: CGPoint(x: 13.92, y: 1.36), control1: CGPoint(x: 13.72, y: 1.08),
      control2: CGPoint(x: 13.94, y: 1.16))
    p.addLine(to: CGPoint(x: 13.05, y: 10.32))
    p.addLine(to: CGPoint(x: 19.3, y: 9.81))
    p.addCurve(
      to: CGPoint(x: 19.46, y: 10.13), control1: CGPoint(x: 19.49, y: 9.79),
      control2: CGPoint(x: 19.58, y: 9.96))
    p.addLine(to: CGPoint(x: 10.4, y: 22.75))
    p.addCurve(
      to: CGPoint(x: 10.1, y: 22.62), control1: CGPoint(x: 10.28, y: 22.91),
      control2: CGPoint(x: 10.07, y: 22.83))
    p.addLine(to: CGPoint(x: 10.75, y: 13.65))
    p.addLine(to: CGPoint(x: 4.7, y: 14.05))
    p.addCurve(
      to: CGPoint(x: 4.54, y: 13.73), control1: CGPoint(x: 4.51, y: 14.07),
      control2: CGPoint(x: 4.42, y: 13.89))
    p.closeSubpath()
    return p
  }()
  static let heart: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 28, y: 9.5))
    p.addCurve(
      to: CGPoint(x: 24, y: 6.8), control1: CGPoint(x: 26.1, y: 9.5),
      control2: CGPoint(x: 25.65, y: 8.15))
    p.addCurve(
      to: CGPoint(x: 14, y: 3), control1: CGPoint(x: 21.36, y: 4.64),
      control2: CGPoint(x: 18.15, y: 3))
    p.addCurve(
      to: CGPoint(x: 0.25, y: 16.75), control1: CGPoint(x: 6.41, y: 3),
      control2: CGPoint(x: 0.25, y: 9.16))
    p.addCurve(
      to: CGPoint(x: 25.6, y: 49.2), control1: CGPoint(x: 0.25, y: 29),
      control2: CGPoint(x: 14, y: 40.5))
    p.addCurve(
      to: CGPoint(x: 28, y: 50), control1: CGPoint(x: 26.32, y: 49.74),
      control2: CGPoint(x: 27.17, y: 50))
    p.addCurve(
      to: CGPoint(x: 30.4, y: 49.2), control1: CGPoint(x: 28.83, y: 50),
      control2: CGPoint(x: 29.68, y: 49.74))
    p.addCurve(
      to: CGPoint(x: 55.75, y: 16.75), control1: CGPoint(x: 42, y: 40.5),
      control2: CGPoint(x: 55.75, y: 29))
    p.addCurve(
      to: CGPoint(x: 42, y: 3), control1: CGPoint(x: 55.75, y: 9.16),
      control2: CGPoint(x: 49.59, y: 3))
    p.addCurve(
      to: CGPoint(x: 32, y: 6.8), control1: CGPoint(x: 37.85, y: 3),
      control2: CGPoint(x: 34.64, y: 4.64))
    p.addCurve(
      to: CGPoint(x: 28, y: 9.5), control1: CGPoint(x: 30.35, y: 8.15),
      control2: CGPoint(x: 29.9, y: 9.5))
    p.closeSubpath()
    return p
  }()
  static let clockCircle = Path(ellipseIn: CGRect(x: 2.35, y: 2.35, width: 19.3, height: 19.3))
  static let clockHands: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 12, y: 5.35))
    p.addLine(to: CGPoint(x: 12, y: 12.05))
    p.addLine(to: CGPoint(x: 15.35, y: 15.35))
    return p
  }()
  static let crescent: Path = {
    var p = Path()
    p.addArc(
      center: EmberTheme.Heart.crescentCenter, radius: EmberTheme.Heart.crescentRadius,
      startAngle: .radians(EmberTheme.emberHeartKeyAngle - EmberTheme.Heart.crescentHalfAngle),
      endAngle: .radians(EmberTheme.emberHeartKeyAngle + EmberTheme.Heart.crescentHalfAngle),
      clockwise: false)
    return p
  }()
}
