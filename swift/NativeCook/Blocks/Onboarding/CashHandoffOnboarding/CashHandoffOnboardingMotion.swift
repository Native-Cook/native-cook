import Foundation
import CoreGraphics

enum CashHandoffOnboardingMotion {
    static let LOOP = 8.0
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func smooth(_ k: Double) -> Double { let x = clamp(k); return x * x * (3 - 2 * x) }
    static func easeIn(_ k: Double) -> Double { pow(clamp(k), 3) }
    static func easeOut(_ k: Double) -> Double { 1 - pow(1 - clamp(k), 3) }
    static func easeInOut(_ k: Double) -> Double {
        let x = clamp(k)
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func closeSpring(_ s: Double) -> Double { s <= 0 ? 0 : 1 - exp(-s * 9) * cos(s * 14) }
    struct Layout {
        var width: Double
        var height: Double
        var top: Double
        var bottom: Double
        var panel: Double { (height * 0.64).rounded() }
        var hold: CGPoint { CGPoint(x: 142, y: top + 304) }
        var palm: CGPoint { CGPoint(x: width - 104, y: top + 342) }
    }
    struct Scene {
        var cx = 142.0, cy = 304.0, rot = 0.0, fan = 0.0
        var gCurl = 0.75, rCurl = 0.02, gOff = 0.0, rOff = 0.0, caughtAt = -10.0
    }
    static func at(_ t: Double, _ l: Layout) -> Scene {
        let k = easeInOut((t - 1) / 1.2)
        let fly = t >= 1 && t < 2.2
        let away = t >= 4.6 && t < 5.6 ? easeIn((t - 4.6) / 1) : t >= 5.6 && t < 6.8 ? 1 - easeOut((t - 5.6) / 1.2) : 0
        let fresh = t >= 5.6
        let caught = t >= 2.2 && !fresh
        let cx = fresh ? l.hold.x : mix(l.hold.x, l.palm.x, k)
        let cy = fresh ? l.hold.y : mix(l.hold.y, l.palm.y, k) - sin(k * .pi) * 80
        return Scene(cx: cx + (fresh ? -1 : caught ? 1 : 0) * away * 320, cy: cy,
                     rot: fly ? sin(k * .pi * 3) * 12 : caught ? -6 : 0,
                     fan: fly ? sin(k * .pi) * 0.7 : 0,
                     gCurl: t < 1 || fresh ? 0.75 : mix(0.75, 0.15, smooth((t - 1) / 0.25)),
                     rCurl: caught ? mix(0.02, 0.85, closeSpring(t - 2.2)) : 0.02,
                     gOff: -away * 320, rOff: away * 320, caughtAt: caught ? 2.2 : -10)
    }
}
