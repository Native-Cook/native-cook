import Foundation
import CoreGraphics

enum FridgeDoorGroceryMotion {
    static let loop = 9.0
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smoother(_ k: Double) -> Double {
        let x = clamp(k)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    static func backOut(_ k: Double) -> Double {
        let x = clamp(k) - 1
        return 1 + 2.4 * pow(x, 3) + 1.4 * pow(x, 2)
    }
    struct Frame {
        var a = 0.0
        var fly = [Double](repeating: 0, count: 8)
        var t = 0.0
        var bag = 0
        var bagScale: Double {
            1 + 0.18 * fly.reduce(0) { max($0, $1 > 0.85 && $1 < 1 ? sin(($1 - 0.85) / 0.15 * .pi) : 0) }
        }
    }
    static func at(_ time: Double) -> Frame {
        let t = time.truncatingRemainder(dividingBy: loop)
        var a = 0.0
        if t >= 1 && t < 1.9 { a = backOut((t - 1) / 0.9) }
        else if t >= 1.9 && t < 6.2 { a = 1 }
        else if t >= 6.2 && t < 7 {
            let k = (t - 6.2) / 0.8
            a = 1 - smoother(k) + (k > 0.85 ? sin((k - 0.85) / 0.15 * .pi) * 0.03 : 0)
        }
        let f0 = t >= 2.5 && t < 8.2 ? clamp((t - 2.5) / 0.7) : 0
        let f2 = t >= 3.2 && t < 8.2 ? clamp((t - 3.2) / 0.7) : 0
        return Frame(a: a, fly: [f0, 0, f2, 0, 0, 0, 0, 0], t: time, bag: (f0 >= 1 ? 1 : 0) + (f2 >= 1 ? 1 : 0))
    }
    struct Item {
        var shelf: Int
        var x: Double
        var low: Bool
    }
    static let items: [Item] = [
        .init(shelf: 0, x: 0.18, low: true), .init(shelf: 0, x: 0.55, low: false),
        .init(shelf: 1, x: 0.22, low: true), .init(shelf: 1, x: 0.66, low: false),
        .init(shelf: 2, x: 0.2, low: false), .init(shelf: 2, x: 0.6, low: false),
        .init(shelf: 3, x: 0.3, low: false), .init(shelf: 3, x: 0.7, low: false)
    ]
    struct Geo {
        var x, y, w, h, bagX, bagY: Double
        init(_ size: CGSize, _ top: Double) {
            w = (size.width * 0.62).rounded()
            x = ((size.width - w) / 2).rounded() - 10
            y = top + 120
            h = (size.height * 0.56).rounded()
            bagX = size.width - 52
            bagY = top + 30
        }
    }
}
