import Foundation

enum TinyWorldPickerMotion {
  static let loop = 9.0
  static let segment = 2.25
  static let hold = 1.35
  static let morph = 0.75
  static let idleSpin = 0.25
  static let spin = (4 * Double.pi - idleSpin * loop) / 4
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func smoother(_ x: Double) -> Double {
    let x = clamp(x)
    return x * x * x * (x * (x * 6 - 15) + 10)
  }
  static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let x = clamp((x - a) / (b - a))
    return x * x * (3 - 2 * x)
  }
  struct Frame {
    var cur = 0, nxt = 0
    var m = 0.0, yaw = 0.0, sq = 0.0, glow = 0.0, t = 0.0, tm = 0.0
  }
  static func at(_ time: Double) -> Frame {
    let t = time.truncatingRemainder(dividingBy: loop)
    let k = Int(floor(t / segment))
    let u = t - Double(k) * segment
    let mm = clamp((u - hold) / morph)
    let m = smoother(mm)
    let done = hold + morph
    let tau = u >= done ? u - done : u + segment - done
    let wobble = tau < 0 ? 0 : -0.07 * exp(-7 * tau) * sin(17 * tau)
    let sq = (mm > 0 && mm < 1 ? 0.12 * sin(Double.pi * mm) : 0) + wobble
    return Frame(cur: k % 4, nxt: (k + 1) % 4, m: m,
                 yaw: -(idleSpin * t + spin * (Double(k) + m)), sq: sq, t: t, tm: t)
  }
  static func visibility(_ i: Int, _ f: Frame) -> Double {
    let s = smooth(0.35, 0.65, f.m)
    return (i == f.cur ? (f.nxt == f.cur ? 1 : 1 - s) : 0)
      + (i == f.nxt && f.nxt != f.cur ? s : 0)
  }
  static func labelVisibility(_ i: Int, _ f: Frame) -> Double {
    if f.nxt == f.cur { return i == f.cur ? 1 : 0 }
    if i == f.cur { return 1 - smooth(0.14, 0.46, f.m) }
    if i == f.nxt { return smooth(0.48, 0.82, f.m) }
    return 0
  }
}

struct TinyWorldPickerWorld {
  let name, status, cta: String
  let halo: UInt32
  let glyph: Int
  let stats: [String]
  var done: Bool { status == "COMPLETED" }
  static let all: [Self] = [
    .init(name: "Cloud Nest", status: "COMPLETED", cta: "Replay world", halo: 0x2FB9D3, glyph: 984464, stats: ["2:41", "24/24", "3/3"]),
    .init(name: "Kart Loop", status: "COMPLETED", cta: "Replay world", halo: 0x7BD13A, glyph: 983612, stats: ["1:58", "18/18", "2/2"]),
    .init(name: "Bun Summit", status: "3 / 5 STARS", cta: "Continue", halo: 0xF29A3D, glyph: 984709, stats: ["4:12", "11/20", "1/3"]),
    .init(name: "Old Arena", status: "NEW WORLD", cta: "Start world", halo: 0xE8BE84, glyph: 984834, stats: ["--:--", "0/16", "0/4"])
  ]
}
