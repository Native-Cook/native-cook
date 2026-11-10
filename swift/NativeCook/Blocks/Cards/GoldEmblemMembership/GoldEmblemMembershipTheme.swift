import SwiftUI

enum GoldEmblemMembershipTheme {
    static let page = color(0x26272B)
    static let strip = color(0x37383D)
    struct Tier {
        let name, range, next: String
        let ink, sub: UInt32
        let coupon: String
        let sliver: UInt32
    }
    static let tiers = [
        Tier(name: "Gold member", range: "100,000 < assets < 300,000", next: "Platinum is $80,889.00 away", ink: 0x5A4426, sub: 0x6E5634, coupon: "1", sliver: 0xB8B9BC),
        Tier(name: "Platinum member", range: "300,000 < assets < 800,000", next: "Black is $412,560.00 away", ink: 0x33414A, sub: 0x4E5D66, coupon: "1.5", sliver: 0xC9B08A),
        Tier(name: "Black member", range: "assets > 800,000", next: "The highest tier", ink: 0xF0CC86, sub: 0xC9AE7C, coupon: "2", sliver: 0x9FB0B8),
    ]
    static let stats: [(String, String, Double)] = [("81.57", "Withdraw", 68), ("869.23", "Interest expense", 205), ("559.33", "Reward", 333)]
    static let privileges: [(String, String, Double)] = [("Gift package", "$60", 66), ("Welfare day", "$150", 200), ("Redeem", "7.6%", 333)]
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255)/255, green: Double((hex >> 8) & 255)/255, blue: Double(hex & 255)/255, opacity: 1)
    }
    struct Layout: Equatable {
        let w, h, sx, k, top: Double
        init(_ size: CGSize, _ safeTop: Double, _ safeBottom: Double) {
            w = size.width; h = size.height
            top = max(safeTop, 44)
            sx = w/402
            k = (h-top-max(safeBottom, 20))/(874-62-34)
        }
        func y(_ value: Double) -> Double { value < 62 ? value*top/62 : top+(value-62)*k }
        var card: CGRect { CGRect(x: 17*sx, y: y(103), width: 367*sx, height: 185*k) }
    }
}
