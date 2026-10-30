import SwiftUI

enum IsometricScoreGaugeTheme {
    static let width = 402.0
    static let height = 874.0
    enum C {
        static let bg = 0x050506, disc = 0x111114, track = 0x1D1D22
        static let ink = 0xF0F0F2, grey = 0x5E5E66, caption = 0xCFCFD4
        static let card = 0x17161B, cardEdge = 0x2A2930, cardText = 0xD9D9DE
        static let glow = 0x3B2A5E, up = 0x3CCB6E, down = 0xF0663A
    }
    enum Gauge {
        static let cx = 200.0, cy = 278.0, radius = 121.0, width = 6.0
        static let start = 156.0, sweep = 228.0, low = 300.0, high = 900.0, score = 765.0
        static let numberY = 262.0, numberSize = 74.0
        static let labelY = 335.0, labelX = [68.8, 327.5]
    }
    enum UI {
        static let back = CGPoint(x: 45.6, y: 93.8)
        static let logo = CGPoint(x: 201, y: 93.8), logoSize = 27.0
        static let captionY = 392.5
        static let rows = [425.0, 623.0], cardWidth = 135.0, cardHeight = 183.0
        static let pitch = 151.2, x0 = 0.7
    }
    struct Scenario {
        let lines: [String]
        let icon: Int
        let delta: Double
    }
    static let columns: [[Scenario]] = [
        [.init(lines: ["pay off my", "credit card", "outstanding"], icon: 4, delta: 26),
         .init(lines: ["close oldest", "credit card"], icon: 5, delta: -18)],
        [.init(lines: ["take a new", "credit card"], icon: 0, delta: -12),
         .init(lines: ["take a new", "home loan"], icon: 2, delta: -22)],
        [.init(lines: ["take a new car", "loan"], icon: 1, delta: -37),
         .init(lines: ["take a new", "loan"], icon: 3, delta: -29)],
        [.init(lines: ["raise my", "credit limit"], icon: 0, delta: 9),
         .init(lines: ["save up a", "cushion"], icon: 3, delta: 14)]
    ]
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255,
              green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
}
