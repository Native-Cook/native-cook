import Foundation

/// Every measurement stays in the React Native 402 × 874 design space.
enum GhostFloatWelcomeTheme {
  static let width = 402.0
  static let height = 874.0
  static let scale = width / 610
  static func x(_ px: Double) -> Double { (px - 294) * scale }
  static func y(_ px: Double) -> Double { (px - 90) * scale }
  struct Body {
    let kind: String
    let x: Double, y: Double, r: Double, depth: Double
  }
  struct Star { let x: Double, y: Double, s: Double, depth: Double }
  struct Glint {
    let body: Int
    let dx: Double, dy: Double, s: Double
  }
  static let bodies: [Body] = [
    .init(kind: "ghost", x: x(600), y: y(520), r: 41, depth: 1),
    .init(kind: "lumen", x: x(600), y: y(355), r: x(656) - x(600), depth: 0.8),
    .init(kind: "dino", x: x(350), y: y(462), r: x(401) - x(350), depth: 0.6),
    .init(kind: "halo", x: x(852), y: y(504), r: x(894) - x(852), depth: 0.5),
    .init(kind: "ember", x: x(342), y: y(662), r: x(390) - x(342), depth: 0.7),
    .init(kind: "penguin", x: x(832), y: y(670), r: x(874) - x(832), depth: 0.55),
    .init(kind: "fern", x: x(595), y: y(725), r: x(655) - x(595), depth: 0.9),
  ]
  static let stars: [Star] = [
    .init(x: x(418), y: y(272), s: 17, depth: 0.25),
    .init(x: x(810), y: y(325), s: 25, depth: 0.35),
    .init(x: x(478), y: y(588), s: 15, depth: 0.2),
    .init(x: x(752), y: y(605), s: 12, depth: 0.15),
    .init(x: x(385), y: y(758), s: 20, depth: 0.3),
    .init(x: x(787), y: y(800), s: 16, depth: 0.25),
  ]
  static let glints: [Glint] = [
    .init(body: 1, dx: x(624) - x(600), dy: y(318) - y(355), s: 12),
    .init(body: 3, dx: x(876) - x(852), dy: y(477) - y(504), s: 9.5),
    .init(body: 4, dx: x(370) - x(342), dy: y(628) - y(662), s: 9.5),
    .init(body: 6, dx: x(552) - x(595), dy: y(688) - y(725), s: 10.5),
  ]
  enum C {
    static let bg = 0x1C1C1C, white = 0xFFFFFF, sub = 0xE8E8E8, grey = 0x9A9A9A
    static let lavender = 0xAB9FF2, pill = 0xAD9FF6, dot = 0x55516A, star = 0x392D5B
    static let ring = 0x8A8A8A, btnOff = 0x67608C, btnOffInk = 0x17122C
    static let btnOn = 0xAB9FF2, btnOnInk = 0x1C1C1C, have = 0x8E8E8E
    static let ghost = 0xFFFEFA, eye = 0x1D1C1A
  }
  static let tokens: [String: (face: Int, glyph: Int)] = [
    "lumen": (0xEAEAEA, 0x151515), "ember": (0xF2662B, 0xFFF2E7),
    "fern": (0x29C28C, 0xF4FFF9), "halo": (0xADA2F0, 0xFFFFFF),
  ]
  static let help = (x: x(862), y: y(196), r: 9.2)
  static let titleY = y(888), sub1Y = y(946), sub2Y = y(981), dotsY = y(1043)
  static let check = (x: x(379), y: y(1132), r: 12.5)
  static let agreeX = x(418)
  static let button = (x: x(321), y: y(1191), w: x(878) - x(321), h: y(1268) - y(1191))
  static let haveY = y(1321)
  static let fieldBottom = y(840)
}
