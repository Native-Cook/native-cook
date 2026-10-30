import SwiftUI

enum CabinClimateCarTheme {
  static let referenceWidth = 402.0
  static let referenceHeight = 874.0
  static let pinScale = 402.0 / 720
  static let bgTop = 0xD6DEF3, bg = 0xE9ECF1, card = 0xFFFFFF, ink = 0x0C0E1A
  static let label = 0x4C5361, mute = 0x757479, pill = 0x0C0D21, teal = 0x22D3A0
  static let green = 0x00E09A, blue = 0x2563EB, chip = 0xE9F7C2, tabSel = 0xE6E8ED
  static let seat = 0x25272C, seatHi = 0x4A4D55, cold = 0x3B82F6, mild = 0x22C7A9, warm = 0xF97316
  static let temp = CGRect(x: 30, y: 580, width: 322, height: 490)
  static let legroom = CGRect(x: 369, y: 580, width: 321, height: 235)
  static let fan = CGRect(x: 369, y: 833, width: 321, height: 237)
  static func color(_ rgb: Int, _ alpha: Double = 1) -> Color {
    Color(
      .sRGB, red: Double((rgb >> 16) & 255) / 255, green: Double((rgb >> 8) & 255) / 255,
      blue: Double(rgb & 255) / 255, opacity: alpha)
  }
  static func air(_ heat: Double, _ alpha: Double = 1) -> Color {
    let k = (heat - 70) / 10
    let other = k < 0 ? cold : warm
    let amount = CabinClimateCarMotion.clamp01(abs(k) * 2.2)
    func channel(_ shift: Int) -> Double {
      CabinClimateCarMotion.mix(
        Double((mild >> shift) & 255), Double((other >> shift) & 255), amount) / 255
    }
    return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: alpha)
  }
}
