import Foundation

// Values from qjp-theme.ts, on the same 402 × 874 reference grid.
enum QrJoyPostcardTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  static let S = 0.34096692111959287
  enum COPY {
    static let orders = "Orders"
    static let purchases = "Purchases"
    static let promo = "Decorate QR code"
    static let pick1 = "Pick up"
    static let pick2 = "by Mar 17, Tue"
    static let point = "Pickup point · 3A Quiet Lane, Riverside"
    static let title = "Share the joy"
    static let sub = "Turn any order into a reason to smile!"
    static let steps = [
      [
        "Decorate a plain QR code: add a", "backdrop and a few warm words to",
        "turn it into a lovely postcard.",
      ],
      [
        "Send it to whoever picks it up: even", "the most ordinary parcel becomes a",
        "little nicer to collect.",
      ],
    ]
    static let ctaA = "Decorate QR code"
    static let ctaB = "Send postcard"
    static let bubble = "for you"
    static let qrText = "example.com/joy/7Q2K"
  }
  enum C {
    static let page = "#F6F6FA"
    static let card = "#FFFFFF"
    static let ink = "#252527"
    static let grey = "#8E8E93"
    static let violet = "#C252FF"
    static let dim = "rgba(0, 0, 0, 0.5)"
    static let promo = "#FEE6FF"
    static let chip = "#F2F2F7"
    static let heroTop = "#FFC2F7"
    static let heroMid = "#FFC9F9"
    static let heroLight = "#FFE0FB"
    static let closeX = "#FFAEF2"
    static let cta = "#B62DFF"
    static let ctaPress = "#A01FEA"
    static let stepChip = "#F6F5FA"
    static let qrInk = "#252429"
    static let postTop = "#FFE6F7"
    static let postBottom = "#FFC4EC"
    static let dotA = "#C21FD6"
    static let dotB = "#5B21D9"
    static let finder = "#D61FC4"
    static let finderCore = "#5B1A8E"
    static let petal = "#FF6FD8"
    static let heart = "#F0219F"
    static let bubbleInk = "#7A1FA8"
    static let sparkle = "#FFFFFF"
  }
  enum PAGE {
    enum header {
      static let y0 = 0.0
      static let y1 = 102.85814249363868
      static let r = 26.0
    }
    enum back {
      static let x = 25.572519083969464
      static let y = 78.99045801526718
    }
    static let ordersY = 79.67239185750635
    enum row {
      static let x = 16.366412213740457
      static let y = 114.79198473282443
      static let w = 368.9262086513995
      static let h = 59.66921119592874
      static let r = 18.0
    }
    enum bag {
      static let x = 44.32569974554707
      static let y = 143.09223918575063
    }
    static let purchasesX = 69.89821882951654
    enum chevron {
      static let x = 357.3333333333333
      static let y = 144.456106870229
    }
    enum promo {
      static let x = 0.0
      static let y = 186.39503816793894
      static let w = 402.0
      static let h = 59.66921119592874
      static let r = 26.0
    }
    enum tulips {
      static let x = 99.56234096692111
      static let y = 170.36959287531806
      static let w = 61.374045801526734
      static let h = 44.32569974554707
    }
    enum promoText {
      static let x = 159.57251908396947
      static let y = 200.03371501272264
    }
    enum promoChevron {
      static let x = 295.95928753180664
      static let y = 200.03371501272264
    }
    enum order {
      static let x = 0.0
      static let y = 216.0591603053435
      static let w = 402.0
      static let h = 152.7531806615776
      static let r = 26.0
    }
    static let pick1Y = 242.65458015267174
    static let pick2Y = 268.2270992366412
    enum qrButton {
      static let x = 332.44274809160305
      static let y = 229.01590330788804
      static let w = 52.8498727735369
      static let h = 52.8498727735369
      static let r = 18.0
    }
    static let pointY = 299.5960559796438
    enum grabber {
      static let x = 183.09923664122138
      static let y = 315.9624681933842
      static let w = 36.142493638676825
      static let h = 4.091603053435108
    }
  }
  enum SHEET {
    static let top = 326.5324427480916
    static let r = 24.0
    static let heroBottom = 541.341603053435
    enum close {
      static let x = 373.3587786259542
      static let y = 352.4459287531807
      static let r = 11.251908396946565
    }
    static let titleX = 17.048346055979643
    static let titleY = 568.9599236641221
    static let subY = 603.397582697201
    enum stepChip {
      static let x = 30.005089058524174
      static let r = 12.615776081424936
    }
    static let stepTextX = 51.82697201017812
    static let stepLineY = [
      [635.1075063613231, 655.5655216284987, 676.0235368956743],
      [711.484096692112, 731.9421119592876, 752.400127226463],
    ]
    static let stepChipY = [637.1533078880407, 713.1889312977099]
    enum cta {
      static let x = 8.183206106870228
      static let y = 779.3365139949109
      static let w = 385.63358778625957
      static let h = 49.78117048346064
      static let r = 17.0
    }
  }
  enum SIZE {
    static let orders = 17.4
    static let row = 17.4
    static let promo = 17.4
    static let pick = 23.4
    static let point = 16.4
    static let title = 23.4
    static let body = 16.2
    static let stepNo = 15.0
    static let cta = 16.8
    static let bubble = 22.0
  }
  enum CARD {
    static let cx = 179.5033477590864
    static let cy = 554.2914152145662
    static let w = 190.0
    static let h = 232.0
    static let angle = -0.16755160819145562
    static let r = 19.0
  }
  static let QR_N = 29.0
  enum QR {
    static let x = 27.0
    static let y = 23.0
    static let size = 136.0
    static let m = 4.689655172413793
  }
  enum BEAR {
    static let x = 83.05642498697493
    static let y = 30.30099520803877
    static let w = 141.0
    static let h = 135.125
    static let frames = 9.0
    static let cols = 3.0
    static let fw = 360.0
    static let fh = 345.0
    static let rest = 4.0
  }
  enum GIFT {
    static let x = 285.73027989821884
    static let y = 449.9624681933842
    static let w = 128.0
    static let h = 161.33333333333334
    static let rot = -7.0
    static let blur = 4.2
  }
  enum STICKER {
    static let x = 196.0
    static let y = 64.0
    static let w = 58.0
    static let h = 69.04761904761905
    static let rot = 13.0
    static let border = 4.5
  }
  enum BUBBLE {
    static let x = 238.676844783715
    static let y = 357.90139949109414
    static let w = 92.0
    static let h = 40.0
    static let r = 20.0
    enum tail {
      static let x = 199.46564885496184
      static let y = 379.0413486005089
    }
  }
  static let QR_ROWS = [
    "11111110010110000111001111111", "10000010111010011000101000001",
    "10111010001101011111101011101", "10111010011110110001001011101",
    "10111010010111011010001011101", "10000010111000001011001000001",
    "11111110101010101010101111111", "00000000111011001111100000000",
    "00001111011001010010001100010", "11011101101101001101111010011",
    "10111011110110011000100001101", "01001101100010001010011101001",
    "01011011000101111011110000010", "01111100101000110010001111001",
    "11110010011110000100111100001", "00101101000010111101010101010",
    "10011011111001110101110111001", "10001101001011000011000111001",
    "00001010101000011011001011001", "00101100100100000101101000011",
    "11001111100001101010111111010", "00000000101111111001100011101",
    "11111110111110101011101011001", "10000010111110011101100011000",
    "10111010100000101111111111011", "10111010011010000001000100010",
    "10111010011001001011110001111", "10000010000110000010010000011",
    "11111110001101111101111100010",
  ]
  struct Module {
    let r, c, d, h: Double
    let heart: Bool
  }
  static let MODULES: [Module] = {
    let mid = (QR_N - 1) / 2
    let maxD = hypot(mid, mid)
    var result: [Module] = []
    for (row, line) in QR_ROWS.enumerated() {
      for (column, bit) in line.enumerated() where bit == "1" {
        let r = Double(row)
        let c = Double(column)
        let inFinder = (r < 7 && c < 7) || (r < 7 && c >= QR_N - 7) || (r >= QR_N - 7 && c < 7)
        if inFinder { continue }
        let seed = sin((r * 29 + c) * 12.9898) * 43758.5453
        result.append(
          Module(
            r: r, c: c, d: hypot(r - mid, c - mid) / maxD,
            h: seed - floor(seed), heart: abs(r - mid) <= 3 && abs(c - mid) <= 3))
      }
    }
    return result
  }()
  struct Sparkle { let x, y, s, ph: Double }
  static let SPARKLES = [
    Sparkle(x: 91.37913486005088, y: 368.8123409669211, s: 26.0, ph: 0.0),
    Sparkle(x: 263.2264631043257, y: 340.8530534351145, s: 13.0, ph: 0.35),
    Sparkle(x: 223.33333333333334, y: 355.85559796437656, s: 6.0, ph: 0.7),
    Sparkle(x: 19.776081424936386, y: 458.1456743002544, s: 8.0, ph: 0.15),
    Sparkle(x: 383.587786259542, y: 449.9624681933842, s: 7.0, ph: 0.55),
    Sparkle(x: 51.14503816793893, y: 520.54262086514, s: 6.0, ph: 0.85),
  ]
  struct Finder { let r, c: Double }
  static let FINDERS = [
    Finder(r: 0.0, c: 0.0),
    Finder(r: 0.0, c: 22.0),
    Finder(r: 22.0, c: 0.0),
  ]
}
