import CoreText
import SwiftUI

enum SunUvBuddyTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  enum C {
    static let page = 0xF4F4F6, map = 0xC9CDD2, cardTop = 0xFFF8D9, cardBottom = 0xFFE9A6
    static let ink = 0x1E1F24, grey = 0x8E9099, chip = 0xFFFFFF, chipOn = 0x24252B
    static let sun = 0xFFD800, sunDeep = 0xFFC400, barLow = 0xEDEDF2
    static let barMid = 0xFFE14D, barHigh = 0xFFB21E, green = 0x3CC665, yellow = 0xF7CF39
  }
  enum UI {
    static let sheet = (y: 61.3, r: 34.0, bottom: 613.0, bottomR: 30.0)
    static let title = (y: 84.6, size: 19.0), place = (y: 105.0, size: 15.0)
    static let close = (x: 370.0, y: 95.2, r: 20.0)
    static let chips = (
      y: 148.0, h: 35.0, x: [-6.0, 74, 178, 262, 336], w: [74.0, 98, 78, 68, 72]
    )
    static let sun = (x: 198.8, y: 327.8, r: 84.0)
    static let headline = (y: 446.0, size: 24.0), badge = (x: 200.9, y: 476.6, r: 12.7)
    static let line = (y: [512.5, 534.4], size: 16.5)
    static let iconsY = 569.0
    static let bars = (
      x0: 20.4, w: 29.6, gap: 3.5, bottom: 765.6, h: [5.0, 26.8, 37.4, 48, 58, 68], labelY: 787.5
    )
    static let stemX = 135.4, learnY = 860.0
  }
  static let title = "UV index", place = "Riverside village", learn = "What the UV index means"
  static let days = ["Today", "Tomorrow", "Thu, 2", "Fri, 3", "Sat, 4"]
  static let hours = ["09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19"]
  static let forecast: [[Double]] = [
    [1, 2, 3, 4, 5, 5, 4, 3, 1, 0, 0], [1, 1, 2, 3, 4, 4, 3, 2, 1, 0, 0],
    [1, 1, 2, 3, 3, 3, 2, 1, 1, 0, 0], [0, 1, 1, 2, 3, 3, 2, 1, 1, 0, 0],
    [0, 1, 1, 1, 2, 2, 1, 1, 0, 0, 0],
  ]
  static func words(_ uv: Double) -> (level: String, line: [String]) {
    if uv <= 0 {
      return ("no UV at all", ["The sun is going to bed.", "Nothing to worry about now"])
    }
    if uv <= 2 { return ("low", ["Barely any sun. Enjoy the day", "without a second thought"]) }
    if uv <= 4 { return ("moderate", ["The sun is calm. But sunscreen", "and a cap won't hurt"]) }
    return ("high", ["Strong sun. Sunscreen, a cap", "and some shade, please"])
  }
  static let registered: Void = {
    for name in ["Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Nunito-ExtraBold"] {
      if let url = Bundle.main.url(forResource: "SunUvBuddy-" + name, withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
}
