import SwiftUI

enum ListeningBuddyPaywallTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  static let S = 402.0 / 736
  static let DY = 120.0
  static let PIN_W = 736.0
  enum C {
    static let sky = 0xFAF0E4
    static let cloud = 0xFDF8F2
    static let hillA = 0xF5E5D5
    static let hillB = 0xFDF4EB
    static let body = 0xF18E4B
    static let bodyLight = 0xF6A56E
    static let bodyShade = 0xE27A36
    static let cheek = 0xEE7B33
    static let leaf = 0x7DB36A
    static let leafDark = 0x5E9A55
    static let phones = 0x555B75
    static let phonesLight = 0x737A96
    static let face = 0x4D5268
    static let bushA = 0x828DAB
    static let bushB = 0xA9B0DA
    static let title = 0x5F6680
    static let sub = 0x504B45
    static let card = 0xF7F3EF
    static let border = 0xF0A25E
    static let header = 0x959DB8
    static let label = 0x5F6680
    static let per = 0x6C6E7B
    static let note = 0x5F6680
    static let fine = 0xB5B3B0
    static let bar = 0x6E7389
    static let close = 0x5F6377
    static let white = 0xFFFFFF
    static let dot = 0xCAC8C9
    static let dotOff = 0xE8DFD6
  }
  struct Plan {
    let name: String
    let price: String
    let per: String
    let lines: [String]
    let perMonth: Int
  }
  static let plans = [
    Plan(
      name: "MONTHLY", price: "12,99 €", per: "PER MONTH",
      lines: ["Recurring billing. Cancel anytime.", "*That's 12,99 € a month."], perMonth: 1299),
    Plan(
      name: "ANNUAL", price: "94,99 €*", per: "PER YEAR",
      lines: ["Recurring billing. Cancel anytime.", "*That's 7,92 € a month."], perMonth: 792),
    Plan(
      name: "LIFETIME", price: "449,99 €", per: "ONE PAYMENT",
      lines: ["No subscription. Pay once.", "*Yours for good, every update included."], perMonth: -1
    ),
  ]
  enum Copy {
    static let title = "Subscribe now"
    static let sub = ["Get access to hundreds of guided meditations in", "the Stillwater Library."]
    static let popular = ["MOST", "POPULAR"]
    static let links = ["Restore Purchase", "Terms & Conditions"]
    static let cta = "CONTINUE"
  }
  enum UI {
    static let close = (x: 668.0, y: 115.0, r: 44.0)
    static let titleY = 222.0
    static let sub = [296.0, 343.0]
    static let dots = (y: 593.0, x: [336.0, 368.0, 400.0])
    static let buddy = (x: 128.0, y: 640.0)
    static let white = 655.0
    static let cards = (y: 752.0, h: 253.0, x: [49.0, 275.0, 501.0], w: 186.0, r: 10.0)
    static let headerH = 66.0
    static let frame = (pad: 10.0, r: 20.0, w: 3.0)
    static let lines = [1063.0, 1101.0]
    static let links = 1152.0
    static let bar = (y: 1183.0, h: 126.0)
  }
}
