import SwiftUI

enum SpilledCoffeeErrorTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  static let S = 402.0 / 658.0
  static let DY = 64.0
  static let PIN_W = 658.0
  static let floorY = 382.0
  static let elev = 15.0 * Double.pi / 180
  static let yaw = 28.0 * Double.pi / 180
  static let base = CGPoint(x: 503, y: 545)
  static let L = 215.0
  static let rb = 55.0
  static let rm = 75.0
  static let sleeve = [0.4, 0.74]
  static let lid = CGPoint(x: 502, y: 620)
  static let lidR = 62.0
  static let paper = [
    CGPoint(x: 52, y: 576), CGPoint(x: 322, y: 552), CGPoint(x: 432, y: 590),
    CGPoint(x: 156, y: 626),
  ]
  static let titleY = 728.0
  static let bodyY = [798.0, 840]
  static let button = CGRect(x: 222, y: 925, width: 214, height: 76)
  enum C {
    static let wallTop = 0xF9F9FB
    static let wallLow = 0xE3E6EB
    static let floor = 0xF0F1F5
    static let floorFar = 0xF3F4F7
    static let shadow = 0xC9CFD8
    static let pot = 0xF4F5F8
    static let potShade = 0xDCE0E6
    static let leafA = 0x7BAD70
    static let leafB = 0xA5D299
    static let leafC = 0x5E9A5A
    static let book = 0xE3E7EC
    static let bookTop = 0xFAFBFC
    static let bookSide = 0xC9D0D8
    static let paper = 0xFFFFFF
    static let cup = 0xFBFBFB
    static let cupShade = 0xDADDE2
    static let cupInside = 0xE2E6EA
    static let sleeve = 0xC7DAAC
    static let sleeveShade = 0xA9C38A
    static let bean = 0xEEF3E6
    static let coffee = 0x9C6352
    static let coffeeLight = 0xBB8374
    static let coffeeDeep = 0x7A4636
    static let lid = 0xF8F9FC
    static let lidShade = 0xDCE0E8
    static let title = 0x2E2B4A
    static let body = 0x6E6C84
    static let button = 0xFFFFFF
    static let buttonText = 0x423F66
    static let steam = 0xB9BECB
  }
  enum Copy {
    static let title = "Error!"
    static let body = ["Something went wrong,", "Please try again later"]
    static let retrying = "Trying again…"
    static let retryingBody = ["Brewing a fresh connection,", "just a moment"]
    static let button = "RETRY"
  }
  static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(
      .sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, opacity: alpha)
  }
}
