import SwiftUI

/// A tiny parser limited to the absolute M/L/C/Q/Z paths used by this block.
enum GhostFloatWelcomePaths {
  static func svg(_ source: String) -> Path {
    let expression = try! NSRegularExpression(pattern: "[MLCQZ]|[-+]?(?:[0-9]*\\.)?[0-9]+")
    let tokens = expression.matches(in: source, range: NSRange(source.startIndex..., in: source))
      .map {
        String(source[Range($0.range, in: source)!])
      }
    var path = Path()
    var index = 0
    func point() -> CGPoint {
      let p = CGPoint(x: Double(tokens[index])!, y: Double(tokens[index + 1])!)
      index += 2
      return p
    }
    while index < tokens.count {
      let command = tokens[index]
      index += 1
      switch command {
      case "M": path.move(to: point())
      case "L": path.addLine(to: point())
      case "C":
        let first = point()
        let second = point()
        let end = point()
        path.addCurve(to: end, control1: first, control2: second)
      case "Q":
        let control = point()
        let end = point()
        path.addQuadCurve(to: end, control: control)
      case "Z": path.closeSubpath()
      default: break
      }
    }
    return path
  }
  static let star = svg(
    "M0 -10 C1.2 -3.2 3.2 -1.2 10 0 C3.2 1.2 1.2 3.2 0 10 C-1.2 3.2 -3.2 1.2 -10 0 C-3.2 -1.2 -1.2 -3.2 0 -10 Z"
  )
  static let glint = svg("M0 -10 Q1.6 -1.6 10 0 Q1.6 1.6 0 10 Q-1.6 1.6 -10 0 Q-1.6 -1.6 0 -10 Z")
  static let check = svg("M-5.2 0.2 L-1.6 3.8 L5.4 -3.6")
  static let ember = svg(
    "M0.5 -11 C5.5 -5.5 9 -1.5 8.2 3.8 C7.5 8.6 4 11 0 11 C-4.2 11 -7.8 8.4 -8 4 C-8.2 0.4 -5.8 -2 -4.6 -5 C-3.4 -2.6 -1.8 -1.2 -0.8 -1.4 C-1.2 -4.6 -1.4 -7.8 0.5 -11 Z M0.2 3.4 C-1.6 5.4 -2.6 6.6 -2.4 8 C-2.2 9.2 -1 9.8 0.2 9.8 C1.6 9.8 2.8 9 2.8 7.6 C2.8 6 1.6 4.8 0.2 3.4 Z"
  )
  static let fern = svg("M-8.5 8.5 C-9.6 -2.8 -1.6 -9.4 9 -9 C9.6 1.6 2.6 9.6 -8.5 8.5 Z")
  static let fernRib = svg("M-8.5 8.5 L3.6 -3.6 M-2.6 2.6 L-2.4 -3.2 M0.8 -0.8 L5.2 -0.6")
  static let halo: Path = {
    var p = Path()
    p.addEllipse(in: CGRect(x: -9.6, y: -6, width: 12, height: 12))
    p.addEllipse(in: CGRect(x: -2.4, y: -6, width: 12, height: 12))
    return p
  }()
  static let lumen: Path = {
    let a = Path(ellipseIn: CGRect(x: -8.6, y: -8.6, width: 17.2, height: 17.2))
    let b = Path(ellipseIn: CGRect(x: 4.6 - 7.4, y: -3.4 - 7.4, width: 14.8, height: 14.8))
    return a.subtracting(b)
  }()
}
