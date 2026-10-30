import SwiftUI

// Reference coordinates are width-scaled, including Y; there is no height fitting or safe-area offset.
enum FrostCardControlsTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  enum C {
    static let bg = 0xF2F2F7
    static let ink = 0x111114
    static let grey = 0x8C8C93
    static let blue = 0x2B3BE0
    static let tile = 0xFFFFFF
    static let button = 0x1C1C1F
    static let brandA = 0xFF4E1A
    static let brandB = 0xFF6E2B
    static let ice = 0xDDEFFF
  }
  enum Copy {
    static let balance = "−$3,116.58"
    static let noLimit = "No spending limit"
    static let setLimit = "Set limit"
    static let brand = "ARIVA"
    static let physical = "Physical card"
    static let last4 = "5371"
    static let hint = "Swipe or tap to see details"
    static let wallet = "Add to Wallet"
    static let tiles = ["Freeze", "Reissue", "Discard"]
    static let unfreeze = "Unfreeze"
    static let frozen = "Frozen"
    static let transactions = "Transactions"
    static let nothing = "Nothing spent in April"
  }
  enum UI {
    static let balance = CGPoint(x: 41.5, y: 91.5)
    static let balanceSize = 25.0
    static let noLimit = CGPoint(x: 41.5, y: 122)
    static let setLimit = CGPoint(x: 362.7, y: 122)
    static let card = CGRect(x: 41.5, y: 182.2, width: 321.2, height: 207.6)
    static let cardRadius = 14.0
    static let hint = CGPoint(x: 202, y: 427)
    static let wallet = CGRect(x: 20.8, y: 487.5, width: 363.2, height: 57.9)
    static let tileXs = [20.8, 149.1, 277.5]
    static let tileY = 567.2
    static let tileW = 106.5
    static let tileH = 89.0
    static let tileRadius = 16.0
    static let iconY = 599.0
    static let labelY = 631.7
    static let tx = CGRect(x: 20.8, y: 678, width: 363.2, height: 220)
  }
  enum Card {
    static let physical = CGPoint(x: 21.9, y: 157.3)
    static let last4 = CGPoint(x: 21.9, y: 177.5)
    static let mark = CGPoint(x: 264.4, y: 162.8)
  }
  static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(
      .sRGB, red: Double((hex >> 16) & 255) / 255,
      green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
  }
}
