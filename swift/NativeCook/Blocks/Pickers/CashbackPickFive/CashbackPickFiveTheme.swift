import SwiftUI

// Coordinates are the original 736 px drawing, scaled by 402 / 736 at the screen edge.
enum CashbackPickFiveTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  static let S = 402.0 / 736.0
  static let PIN_W = 736.0
  static let MAX_PICKS = 5
  enum C {
    static let bg = 0xEFF2FB, ink = 0x10131C, grey = 0x8A8E9C, card = 0xFFFFFF
    static let bannerA = 0xDCCEFF, bannerB = 0xF6F0FF, chip = 0xDCD3FA
    static let box = 0xE6E8EF, boxOn = 0x3E414D, pay = 0xEF6155, purple = 0x8528FF
    static let pink = 0xF795C6, button = 0x1F222B, disabled = 0xC9CCD6
    static let disabledText = 0xF2F3F7, slot = 0xC9CDD8, fine = 0x9DA1AE
    static let link = 0x7A4BE0, panel = 0xFFFFFF, star = 0xFFFFFF
  }
  struct Category {
    var pct: String
    var name: String
    var sub: String? = nil
    var icon: Int
    var color: Int
  }
  static let cats: [Category] = [
    .init(pct: "2%", name: "All purchases", icon: 0, color: 0xE9B8F2),
    .init(pct: "10%", name: "Cafés, bars and restaurants", icon: 1, color: 0xF9AE1F),
    .init(pct: "3%", name: "Supermarkets", icon: 2, color: 0xF2B232),
    .init(pct: "10%", name: "Clothes and shoes", icon: 3, color: 0xCC7ACE),
    .init(
      pct: "10%", name: "Rideo Taxi", sub: "Cashback on rides in every fare", icon: 4,
      color: 0xFFD60A),
    .init(
      pct: "30%", name: "Reelhouse", sub: "Tickets in the app or on the site", icon: 5,
      color: 0x16161A),
    .init(pct: "5%", name: "Pharmacies", icon: 6, color: 0x3CC48A),
    .init(pct: "7%", name: "Beauty", icon: 7, color: 0xF7849E),
    .init(pct: "4%", name: "Fuel", icon: 8, color: 0x4E8CF5),
  ]
  enum Copy {
    static let brand = "ORBIT PLUS"
    static let title = ["Choose 5 of 9 categories", "for June"]
    static let banner = "100 000 Plus points", bannerSub = "Raffle on June 30. Join in?"
    static let section = "With your card", pay = "Pay"
    static let connect = ["Connect Orbit Plus to start", "spending your points"]
    static let choose = "Choose"
    static let fine = [
      "14 days free, then $4.99 a month. By tapping the button you agree",
      "to the promotion terms and the Orbit Plus subscription terms",
    ]
    static let picks = "Your five"
  }
  enum UI {
    static let back = (x: 64.0, y: 172.0), logo = (x: 364.0, y: 168.0)
    static let title = (x: 44.0, y: [288.0, 346.0])
    static let banner = (x: 30.0, y: 456.0, w: 676.0, h: 134.0)
    static let sectionY = 660.0
    static let list = (
      x: 29.0, y: 708.0, w: 677.0, top: 774.0, pitch: 102.0, sub: 32.0, iconX: 91.0, iconR: 33.0, textX: 156.0,
      boxX: 654.0
    )
    static let disabled = (x: 44.0, y: 1394.0, w: 648.0, h: 102.0)
    static let panelY = 1190.0, panelTray = 1172.0, toggle = 1262.0
    static let button = (x: 44.0, y: 1326.0, w: 648.0, h: 102.0)
    static let fine = [1462.0, 1486.0]
    static let tray = (x0: 214.0, pitch: 84.0, r: 32.0)
  }
}
