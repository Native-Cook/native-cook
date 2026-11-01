import CoreGraphics
import Foundation

struct StepParadeFriend {
  let name: String
  let total: Double
  let coat, hat, skin: UInt32
  let peaks: [(Double, Double, Double)]
}

enum StepParadeMotion {
  static let loop = 9.0
  static let now = 21.5
  static let friends: [StepParadeFriend] = [
    .init(
      name: "Leo", total: 11240, coat: 0x3D6BFF, hat: 0xFF5A4E, skin: 0xF2C3A0,
      peaks: [(7, 1, 0.6), (18, 0.6, 1.2)]),
    .init(
      name: "You", total: 9560, coat: 0xFF7A45, hat: 0xFFD23F, skin: 0xE8B48E,
      peaks: [(8.5, 0.5, 0.8), (12.5, 0.7, 0.7), (19, 0.8, 1)]),
    .init(
      name: "Ana", total: 8310, coat: 0xFFC83D, hat: 0x3D6BFF, skin: 0xC98E66,
      peaks: [(10, 0.8, 1.5), (16, 0.6, 1)]),
    .init(
      name: "Kofi", total: 7120, coat: 0x22B573, hat: 0x1F2A44, skin: 0x8D5A3B,
      peaks: [(13, 1, 2.5)]),
    .init(
      name: "Juno", total: 5480, coat: 0xA764FF, hat: 0xFF8FC8, skin: 0xF5D1B5,
      peaks: [(17.5, 1, 1), (20.5, 0.6, 0.7)]),
    .init(
      name: "Sam", total: 3940, coat: 0x2A3142, hat: 0xFF9F2E, skin: 0xE3A77F,
      peaks: [(9, 0.4, 2), (15, 0.6, 2)]),
  ]
  static let curves: [[Double]] = friends.map { f in
    let rate = (0..<65).map { i -> Double in
      let h = 6 + 16 * Double(i) / 64
      return f.peaks.reduce(0.04) { r, p in r + p.1 * exp(-pow(h - p.0, 2) / (2 * p.2 * p.2)) }
    }
    var cum = [0.0]
    for i in 1..<65 { cum.append(cum[i - 1] + (rate[i - 1] + rate[i]) / 2) }
    return cum.map { $0 / cum[64] * f.total }
  }
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func smoother(_ x: Double) -> Double {
    let x = clamp(x)
    return x * x * x * (x * (x * 6 - 15) + 10)
  }
  static func hour(_ t: Double) -> Double {
    let t = t.truncatingRemainder(dividingBy: loop)
    if t < 6.5 { return 6 + 15.5 * smoother(t / 6.5) }
    if t < 7.8 { return now }
    return now - 15.5 * smoother((t - 7.8) / 1.2)
  }
  static func steps(_ i: Int, _ h: Double) -> Double {
    let u = clamp((h - 6) / 16) * 64
    let k = Int(floor(u))
    return k >= 64 ? curves[i][64] : mix(curves[i][k], curves[i][k + 1], u - Double(k))
  }
  static func place(_ g: CGRect, _ i: Int, _ steps: Double) -> (x: Double, y: Double, s: Double) {
    let lane = Double(i)
    return (
      g.minX + 24 + lane * 7 + steps / 12000 * (g.width - 84),
      g.minY + g.height * 0.5 + lane * g.height * 0.086, 0.86 + lane * 0.09
    )
  }
  static func number(_ n: Double) -> String {
    let s = String(Int(floor(n + 0.5)))
    return s.count > 3 ? String(s.dropLast(3)) + "," + String(s.suffix(3)) : s
  }
  static func time(_ h: Double) -> String {
    let m = Int(floor(h * 4)) * 15
    let hh = m / 60
    return "\(hh%12 == 0 ? 12 : hh%12):\(String(format: "%02d",m%60)) \(hh >= 12 ? "PM" : "AM")"
  }
  static func rank(_ h: Double) -> String {
    let mine = steps(1, h)
    var rank = 1
    var lead = 0.0
    var leader = 1
    for i in 0..<6 {
      let v = steps(i, h)
      if i != 1 && v > mine { rank += 1 }
      if v > lead {
        lead = v
        leader = i
      }
    }
    let ord = rank == 1 ? "1st" : rank == 2 ? "2nd" : rank == 3 ? "3rd" : "\(rank)th"
    return leader == 1
      ? "\(ord) of 6 friends · leading"
      : "\(ord) of 6 friends · \(number(lead-mine)) behind \(friends[leader].name)"
  }
}
