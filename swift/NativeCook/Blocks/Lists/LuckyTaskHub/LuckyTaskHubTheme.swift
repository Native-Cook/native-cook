import Foundation

/// All geometry stays in the source artwork’s 720-pixel coordinate space.
enum LuckyTaskHubTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  static let S = 402.0 / 720
  static let DY = 96.0
  static let BALANCE = 12480.0
  static let BILL_VALUE = 100.0
  static let STEPS = 14.0
  static let FLIPS = 30.0
  enum C {
    static let bgTop = 0xE6F6C3
    static let bgMid = 0xF6FBEF
    static let page = 0xFFFFFF
    static let ink = 0x1B1C22
    static let ink2 = 0x2A2C33
    static let grey = 0x7B7F87
    static let line = 0xE2E8DA
    static let yellow = 0xFFD21F
    static let yellowDeep = 0xF6B800
    static let panel = 0xFFF6C2
    static let podium = 0xFFE15A
    static let redCard = 0xFF4A2E
    static let orange = 0xFF8A1F
    static let pink = 0xF07ED3
    static let pinkDeep = 0xD95BBE
    static let green = 0x7ED321
    static let greenDeep = 0x56B000
    static let wheelA = 0xB9EE6B
    static let wheelB = 0x8FDB3A
    static let cardBg = 0xF3F7EE
    static let cardEdge = 0xE1E8D6
    static let pill = 0xE7EDE0
    static let timerBg = 0xFFF1D8
    static let bolt = 0xFFB000
    static let white = 0xFFFFFF
    static let black = 0x17181C
    static let cash = 0x5FCB3A
  }
  static func fillAt(_ done: Double) -> Double {
    let nodeK = (316.0 - 168) / 462
    if done <= 5 { return done / 5 * nodeK }
    return nodeK + (done - 5) / (STEPS - 5) * (1 - nodeK)
  }
}
