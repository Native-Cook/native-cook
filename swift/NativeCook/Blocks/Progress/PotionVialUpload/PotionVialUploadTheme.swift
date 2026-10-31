import Foundation
import CoreGraphics

enum PotionVialUploadTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  enum C {
    static let page = "#0a080d"
    static let star = "#f3ecd2"
    static let starGreen = "#5fe34f"
    static let glass = "#a29aaa"
    static let glassDark = "#525060"
    static let glare = "#e9e4f0"
    static let liquidTop = "#d2fb92"
    static let liquid = "#a2f863"
    static let liquidDeep = "#6fd23c"
    static let liquidBottom = "#3f9a24"
    static let meniscus = "#2c7c1f"
    static let glow = "#4fe02f"
    static let smoke = "#2f6a22"
    static let mist = "#7cf05a"
    static let brassHi = "#ffd277"
    static let brass = "#e9a944"
    static let brassLo = "#a35424"
    static let steelHi = "#d4d0dc"
    static let steel = "#918d9b"
    static let steelLo = "#55525e"
    static let gemHi = "#8ff58a"
    static let gemLight = "#63ea63"
    static let gem = "#33b24b"
    static let gemDark = "#1b7a3a"
    static let gemDeep = "#12592a"
    static let spider = "#16141c"
    static let spiderHi = "#4a4656"
    static let spiderLeg = "#5d5a68"
    static let spiderEye = "#9cf26a"
    static let hourglass = "#e8302f"
    static let ink = "#f2f1ec"
    static let mute = "#8b8a93"
    static let faint = "#5a5962"
    static let ready = "#c6f7a8"
    static let done = "#9be27a"
    static let card = "#141219"
    static let cardLine = "#2a2733"
    static let cardLineHot = "#4fae36"
    static let paper = "#f4f2ea"
    static let paperShade = "#c9c6bd"
    static let zip = "#3fbf4a"
  }
  enum V {
    static let x0 = 22.0, x1 = 282.0, shoulder = 302.0, neck = 311.0
    static let half = 23.0, neckHalf = 11.0, wall = 2.6
    static let band1 = [311.0, 320, 15], disc = [320.0, 327, 19], band2 = [327.0, 337, 15]
    static let chain = 80.0, gemEdge = 33.0
  }
  enum LAYOUT {
    static let vialY = 0.4, counterGap = 52.0, digit = 38.0, sub = 13.0
    static let cardW = 250.0, cardH = 68.0, cardBottom = 34.0
  }
  enum FILE { static let name = "spells.zip"; static let mb = 48.2 }
}
struct PotionVialUploadPoint { var x: Double; var y: Double }
struct PotionVialUploadGeo {
  var w, h, s, ox, cy, top, bottom, cardY: Double
  init(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) {
    self.w = w; self.h = h; self.top = top; self.bottom = bottom
    s = min(w / 402, 1.2); ox = (w - 402 * s) / 2
    let cardTop = h - bottom - 34 - 68 * s
    let headBottom = top + 52 + 76
    cy = headBottom + (cardTop - headBottom) * 0.4
    cardY = (cardTop - cy) / s
  }
  var icon: PotionVialUploadPoint { .init(x: 106, y: cardY + 34) }
}
