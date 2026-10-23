import SwiftUI

/// Decodes reference sRGB hex channels without changing their transfer function.
extension Color {
  init(ember hex: UInt32, alpha: Double = 1) {
    self.init(
      .sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, opacity: alpha)
  }
}
