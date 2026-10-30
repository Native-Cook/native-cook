import SwiftUI

enum CleanAirDialTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  static let S = 402.0 / 670.0
  static let X0 = 265.0
  static let Y0 = 220.0
  static let DUST = 300
  static let BASE_DUST = 260
  enum C {
    static let bg = 0x121212, tick = 0x4A4A4A, green = 0x29AB65
    static let diskTop = 0x070707, diskBot = 0x101010, rim = 0x1A1A1A
    static let divider = 0x202020, label = 0x868686, white = 0xF4F4F4
    static let unit = 0x9A9A9A, sub = 0x6B6B6B, chip = 0x1D1D1D
    static let blue = 0x3F6AF5, purple = 0x5E55F2, power = 0xF1F1F1
    static let dust = 0xD8C6A8, capText = 0x0B2E14
  }
  struct Quality {
    let max: Double
    let word: String
    let color: Int
  }
  static let QUALITY = [
    Quality(max: 6.5, word: "Good", color: 0x4FD6C8),
    Quality(max: 15.5, word: "Moderate", color: 0x18B063),
    Quality(max: 999, word: "Poor", color: 0xF2994A),
  ]
  struct Level {
    let name: String
    let ticks: Double
    let omega: Double
    let target: Double
  }
  static let LEVELS = [
    Level(name: "Low", ticks: 6, omega: 0.35, target: -0.5),
    Level(name: "Medium", ticks: 12, omega: 0.7, target: 0),
    Level(name: "High", ticks: 21, omega: 1.6, target: 0.62),
    Level(name: "Turbo", ticks: 30, omega: 2.5, target: 0.88),
  ]
  enum COPY {
    static let title = "Air Purifier", room = "Living room", pm = "PM 2.5"
    static let stats = [(v: "7", u: "°C"), (v: "64", u: "%"), (v: "12", u: "%")]
    static let chips = ["Speed", "Timer", "Scenes"]
    static let timerOn = "30 mins · On", timerOff = "30 mins · Off"
  }
  enum UI {
    static let header = (y: -200.0, sub: -160.0)
    static let dial = (x: 600.0, y: 282.0, r0: 247.0, r1: 270.0, ticks: 60, disk: 230.0, tickW: 4.6)
    static let pm = (y: 140.0, value: 212.0)
    static let divider = 283.0
    static let stats = (
      x: [470.0, 600.0, 728.0], v: 335.0, icon: 388.0, div: [537.0, 665.0], divY: [313.0, 407.0]
    )
    static let cap = (cy: 622.0, r: 185.0, text: 474.0)
    static let chips = (
      x: [400.0, 600.0, 800.0], y: 648.0, w: 142.0, h: 78.0, label: 716.0, sub: 748.0
    )
    static let bottom = (x: [400.0, 600.0, 800.0], y: 845.0)
  }
  static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(
      .sRGB, red: Double((hex >> 16) & 255) / 255,
      green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
  }
  static func mixColor(_ a: Int, _ b: Int, _ k: Double, _ alpha: Double = 1) -> Color {
    let mix = CleanAirDialMotion.mix
    return Color(
      .sRGB,
      red: mix(Double((a >> 16) & 255), Double((b >> 16) & 255), k) / 255,
      green: mix(Double((a >> 8) & 255), Double((b >> 8) & 255), k) / 255,
      blue: mix(Double(a & 255), Double(b & 255), k) / 255, opacity: alpha)
  }
}
