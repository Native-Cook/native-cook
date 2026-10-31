import SwiftUI

enum SeasonTierMedalsTheme {
    static let names = ["BRONZE", "SILVER", "GOLD"]
    static let kickers = ["Your level in the spring season", "Your level in the summer season", "Your next level"]
    static let titles = ["Bronze reached in April", "Earn 1,500 more points", "Gold starts at 2,000 points"]
    static let nums = ["500 / 500", "500 / 2,000", "500 / 2,000"]
    static let feet = ["1% back as points on every purchase", "Get it by August 31 to reach Gold", "10% back on travel and dining"]
    static let fills = [1.0, 0.25, 0.25]
    static let bars = [0xc98d68, 0xe9eaee, 0xf2c56c]
    static let halos = [[1.0, 0.72, 0.55], [1, 1, 1], [1, 0.86, 0.55]]
    static let pages = [[0.42, 0.2, 0.1], [0.26, 0.28, 0.36], [0.46, 0.33, 0.08]]
    static let metals: [SIMD3<Float>] = [.init(0.86, 0.5, 0.33), .init(0.9, 0.91, 0.94), .init(1, 0.76, 0.34)]
    static let deeps: [SIMD3<Float>] = [.init(0.36, 0.15, 0.08), .init(0.3, 0.32, 0.38), .init(0.47, 0.28, 0.05)]
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func blended(_ colors: [[Double]], _ p: Double, _ alpha: Double) -> Color {
        let i = min(1, max(0, Int(floor(p))))
        let k = SeasonTierMedalsMotion.clamp(p - Double(i))
        let v = (0..<3).map { (SeasonTierMedalsMotion.mix(colors[i][$0], colors[i + 1][$0], k) * 255).rounded() / 255 }
        return Color(.sRGB, red: v[0], green: v[1], blue: v[2], opacity: alpha)
    }
}
