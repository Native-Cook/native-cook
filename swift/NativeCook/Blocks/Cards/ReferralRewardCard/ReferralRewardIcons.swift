import SwiftUI

enum ReferralRewardIcons {
  static let gift: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 6, y: 9))
    p.addLine(to: CGPoint(x: 18, y: 9))
    p.addQuadCurve(to: CGPoint(x: 20, y: 11), control: CGPoint(x: 20, y: 9))
    p.addLine(to: CGPoint(x: 20, y: 19))
    p.addQuadCurve(to: CGPoint(x: 18, y: 21), control: CGPoint(x: 20, y: 21))
    p.addLine(to: CGPoint(x: 6, y: 21))
    p.addQuadCurve(to: CGPoint(x: 4, y: 19), control: CGPoint(x: 4, y: 21))
    p.addLine(to: CGPoint(x: 4, y: 11))
    p.addQuadCurve(to: CGPoint(x: 6, y: 9), control: CGPoint(x: 4, y: 9))
    p.closeSubpath()
    p.move(to: CGPoint(x: 4, y: 13))
    p.addLine(to: CGPoint(x: 20, y: 13))
    p.move(to: CGPoint(x: 12, y: 9))
    p.addLine(to: CGPoint(x: 12, y: 21))
    p.move(to: CGPoint(x: 12, y: 9))
    p.addCurve(
      to: CGPoint(x: 8, y: 3), control1: CGPoint(x: 12, y: 5.5), control2: CGPoint(x: 10, y: 3))
    p.addCurve(
      to: CGPoint(x: 8, y: 8), control1: CGPoint(x: 5, y: 3), control2: CGPoint(x: 5, y: 7))
    p.addLine(to: CGPoint(x: 12, y: 9))
    p.addCurve(
      to: CGPoint(x: 16, y: 3), control1: CGPoint(x: 12, y: 5.5), control2: CGPoint(x: 14, y: 3))
    p.addCurve(
      to: CGPoint(x: 16, y: 8), control1: CGPoint(x: 19, y: 3), control2: CGPoint(x: 19, y: 7))
    p.addLine(to: CGPoint(x: 12, y: 9))
    return p
  }()
  static let ticket: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 5, y: 4.5))
    p.addLine(to: CGPoint(x: 19, y: 4.5))
    p.addQuadCurve(to: CGPoint(x: 21, y: 6.5), control: CGPoint(x: 21, y: 4.5))
    p.addLine(to: CGPoint(x: 21, y: 9))
    p.addCurve(
      to: CGPoint(x: 18, y: 12), control1: CGPoint(x: 19.5, y: 9), control2: CGPoint(x: 18, y: 10.5)
    )
    p.addCurve(
      to: CGPoint(x: 21, y: 15), control1: CGPoint(x: 18, y: 13.5),
      control2: CGPoint(x: 19.5, y: 15))
    p.addLine(to: CGPoint(x: 21, y: 17.5))
    p.addQuadCurve(to: CGPoint(x: 19, y: 19.5), control: CGPoint(x: 21, y: 19.5))
    p.addLine(to: CGPoint(x: 5, y: 19.5))
    p.addQuadCurve(to: CGPoint(x: 3, y: 17.5), control: CGPoint(x: 3, y: 19.5))
    p.addLine(to: CGPoint(x: 3, y: 15))
    p.addCurve(
      to: CGPoint(x: 6, y: 12), control1: CGPoint(x: 4.5, y: 15), control2: CGPoint(x: 6, y: 13.5))
    p.addCurve(
      to: CGPoint(x: 3, y: 9), control1: CGPoint(x: 6, y: 10.5), control2: CGPoint(x: 4.5, y: 9))
    p.addLine(to: CGPoint(x: 3, y: 6.5))
    p.addQuadCurve(to: CGPoint(x: 5, y: 4.5), control: CGPoint(x: 3, y: 4.5))
    p.closeSubpath()
    p.move(to: CGPoint(x: 14, y: 7.5))
    p.addLine(to: CGPoint(x: 14, y: 10))
    p.move(to: CGPoint(x: 14, y: 14))
    p.addLine(to: CGPoint(x: 14, y: 16.5))
    return p
  }()
  static let crown: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 3, y: 7))
    p.addLine(to: CGPoint(x: 7.5, y: 11))
    p.addLine(to: CGPoint(x: 12, y: 4))
    p.addLine(to: CGPoint(x: 16.5, y: 11))
    p.addLine(to: CGPoint(x: 21, y: 7))
    p.addLine(to: CGPoint(x: 18, y: 16.5))
    p.addLine(to: CGPoint(x: 18, y: 18))
    p.addQuadCurve(to: CGPoint(x: 16, y: 20), control: CGPoint(x: 18, y: 20))
    p.addLine(to: CGPoint(x: 8, y: 20))
    p.addQuadCurve(to: CGPoint(x: 6, y: 18), control: CGPoint(x: 6, y: 20))
    p.addLine(to: CGPoint(x: 6, y: 16.5))
    p.closeSubpath()
    p.move(to: CGPoint(x: 6, y: 16.5))
    p.addLine(to: CGPoint(x: 18, y: 16.5))
    return p
  }()
  static let spark: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 12, y: 7))
    p.addQuadCurve(to: CGPoint(x: 17, y: 12), control: CGPoint(x: 13, y: 11))
    p.addQuadCurve(to: CGPoint(x: 12, y: 17), control: CGPoint(x: 13, y: 13))
    p.addQuadCurve(to: CGPoint(x: 7, y: 12), control: CGPoint(x: 11, y: 13))
    p.addQuadCurve(to: CGPoint(x: 12, y: 7), control: CGPoint(x: 11, y: 11))
    p.closeSubpath()
    return p
  }()
  static let rewards = [gift, ticket, crown]
}
