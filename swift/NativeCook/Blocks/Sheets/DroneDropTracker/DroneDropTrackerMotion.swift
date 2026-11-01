import SwiftUI

struct DroneDropTrackerFrame {
    var f = 0.0, drop = 0.0, carried = 1.0, back = 0.0, sheet = 0.0, t = 0.0
    var delivered: Bool { drop > 0.98 }
    var eta: String {
        delivered ? "Delivered" : f >= 1 ? "Lowering your order" : "Arriving in \(max(1, Int(ceil((1 - f) * 6)))) min"
    }
}

enum DroneDropTrackerMotion {
    static let loop = 9.0
    static let route: [CGPoint] = [.init(x: 0.16, y: 0.2), .init(x: 0.16, y: 0.42), .init(x: 0.5, y: 0.42), .init(x: 0.5, y: 0.66), .init(x: 0.82, y: 0.66), .init(x: 0.82, y: 0.8)]
    static let lengths = zip(route, route.dropFirst()).map { hypot($1.x - $0.x, $1.y - $0.y) }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func smoother(_ k: Double) -> Double {
        let x = clamp(k)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    static func routeAt(_ f: Double) -> CGPoint {
        var want = clamp(f) * lengths.reduce(0, +)
        for i in lengths.indices {
            if want <= lengths[i] || i == lengths.count - 1 {
                let k = lengths[i] > 0 ? min(1, want / lengths[i]) : 0
                return CGPoint(x: mix(route[i].x, route[i + 1].x, k), y: mix(route[i].y, route[i + 1].y, k))
            }
            want -= lengths[i]
        }
        return route[0]
    }
    static func at(_ time: Double) -> DroneDropTrackerFrame {
        let t = time.truncatingRemainder(dividingBy: loop)
        var o = DroneDropTrackerFrame(t: time)
        if t < 5.4 { o.f = smoother(t / 5.4) }
        else if t < 6.6 {
            o.f = 1; o.drop = smoother((t - 5.4) / 1.1)
            if t > 6.5 { o.carried = 0 }
        } else if t < 8.6 {
            o.f = 1; o.drop = 1; o.carried = 0; o.back = smoother((t - 6.6) / 2)
        } else { o.f = 0; o.drop = 0; o.carried = clamp((t - 8.6) / 0.4); o.back = 1 }
        o.sheet = t < 2.4 ? 0 : t < 3.1 ? smoother((t - 2.4) / 0.7) : t < 7.8 ? 1 : 1 - smoother((t - 7.8) / 0.7)
        return o
    }
}

struct DroneDropTrackerGeometry: Equatable {
    let width: Double, height: Double, safeTop: Double, safeBottom: Double
    var minSheet: Double { 150 + safeBottom }
    var maxSheet: Double { (height * 0.55).rounded() }
    var travel: Double { maxSheet - minSheet }
    var top: Double { safeTop + 56 }
    var mapH: Double { height - top - minSheet + 30 }
    var focusY: Double { top + (height - maxSheet - top) * 0.42 }
    func sheetY(_ o: DroneDropTrackerFrame) -> Double { height - maxSheet + (1 - o.sheet) * travel }
}
