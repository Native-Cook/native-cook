import SwiftUI

final class ListeningBuddyPaywallPaint {
  var color: Color
  var width: Double?
  var blur = 0.0
  init(_ hex: Int, _ alpha: Double = 1) {
    color = Color(
      .sRGB, red: Double((hex >> 16) & 255) / 255,
      green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
  }
  func setColor(_ color: Color) { self.color = color }
}
