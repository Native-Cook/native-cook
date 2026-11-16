import Foundation

/// The approved 402 × 874 layout is anchored to the bottom safe area, including the photo.
struct ReededGlassRevealLayout: Hashable {
    let width: Double, height: Double, bottom: Double
    var scale: Double { width / 402 }
    func x(_ value: Double) -> Double { value * scale }
    func y(_ value: Double) -> Double { height - max(bottom, 0) - (874 - 34 - value) * scale }
    var image: CGRect { CGRect(x: 0, y: y(472) - 472 * scale, width: width, height: 874 * scale) }
    var capsule: CGRect { CGRect(x: x(16), y: y(472), width: x(370), height: x(99)) }
    var groundTop: Double { capsule.minY + capsule.height * 0.3 }
    var groundSolid: Double { capsule.maxY - 2 * scale }

    struct Random {
        var a: UInt32
        mutating func next() -> Double {
            a = a &+ 0x6d2b79f5
            var t = a
            t = (t ^ (t >> 15)) &* (t | 1)
            t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
            return Double(t ^ (t >> 14)) / 4294967296
        }
    }
    static let widths: [Double] = {
        var random = Random(a: 462604)
        let raw = (0..<22).map { _ in 0.87 + 0.25 * random.next() }, sum = raw.reduce(0, +)
        return raw.map { $0 / sum }
    }()
    static let seeds: [Double] = {
        var random = Random(a: 2644)
        return (0..<22).map { _ in 0.04 + 0.92 * random.next() }
    }()
    static func ribEdges(_ width: Double) -> [Double] {
        var edges = [0.0], acc = 0.0
        for i in widths.indices { acc += widths[i]; edges.append(i == 21 ? width : acc * width) }
        return edges
    }
}
