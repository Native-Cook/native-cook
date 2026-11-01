import SwiftUI

enum TrophyFanCaseTheme {
  struct Trophy {
    let badge: String
    let title: String
    let time: String
    let when: String
    let pace: String
    let splits: [Double]
    let best: String
    let delta: String
  }
  static let trophies = [
    Trophy(badge: "pr", title: "Half marathon PR", time: "1:28:14", when: "Apr 12 · Riverside loop", pace: "4:11", splits: [0.82, 0.86, 0.9, 0.88, 0.95], best: "Previous best 1:31:02", delta: "−2:48"),
    Trophy(badge: "third", title: "3rd fastest 10 mile", time: "1:17:55", when: "May 3 · Harbour 10", pace: "4:50", splits: [0.88, 0.84, 0.86, 0.8, 0.9], best: "Your fastest 1:14:20", delta: "+3:35"),
    Trophy(badge: "streak", title: "2026 longest streak", time: "41 days", when: "Feb 2 – Mar 14", pace: "5:24", splits: [0.6, 0.72, 0.68, 0.8, 0.76], best: "Previous streak 27 days", delta: "+14"),
    Trophy(badge: "second", title: "2nd fastest 10K", time: "45:09", when: "Jun 21 · Night 10K", pace: "4:30", splits: [0.9, 0.92, 0.88, 0.94, 0.97], best: "Your fastest 44:41", delta: "+0:28")
  ]
  static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
          green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
  }
}
