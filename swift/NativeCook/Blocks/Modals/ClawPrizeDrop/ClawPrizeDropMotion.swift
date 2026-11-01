import Foundation
import CoreGraphics

enum ClawPrizeDropMotion {
    static let loop = 9.0
    static let names = ["Plush bear", "Bunny ball", "Sleepy star", "Mint capsule", "Coin sack", "Gift coupon"]
    static let rewards = ["15% off toys", "Free delivery", "20% off snacks", "Mystery gift", "+200 coins", "10% off your basket"]
    static func clamp(_ v: Double) -> Double { max(0, min(1, v)) }
    static func smoother(_ v: Double) -> Double {
        let x = clamp(v)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    struct Geo {
        let x = 28.0
        let y, w, h, railY, floorY, chuteX, homeX, ps: Double
        let px, py: [Double]
        init(_ size: CGSize, _ top: Double) {
            y = top + 150; w = size.width - 56; h = (size.height * 0.42).rounded()
            railY = y + 22; floorY = y + h - 40; chuteX = 78; homeX = 28 + w * 0.5
            ps = (w * 0.24).rounded()
            let width = w, floor = floorY, prizeSize = ps
            px = [0.36, 0.58, 0.78, 0.47, 0.68, 0.88].map { 28 + width * $0 }
            py = [0.35, 0.3, 0.42, 0.85, 0.82, 0.7].map { floor - prizeSize * $0 }
        }
    }
    struct Grab { var at = -100.0; var fromX = 0.0; var prize = -1 }
    struct Frame {
        var x: Double
        var drop = 0.0, close = 0.0, carry = 0.0, fall = -1.0
        var prize = -1
        var coupon = 0.0, respawn = 1.0, swing = 0.0
    }
    static func grabAt(_ g: Geo, _ gr: Grab, _ u: Double) -> Frame {
        var r = Frame(x: gr.fromX, prize: gr.prize)
        if u >= 0 {
            let down = smoother(u / 0.7), shut = smoother((u - 0.7) / 0.25)
            let up = smoother((u - 0.95) / 0.7), move = smoother((u - 1.65) / 0.65)
            let open = u >= 2.3 ? smoother((u - 2.3) / 0.2) : 0
            r.drop = down * (1 - up); r.close = shut * (1 - open)
            if gr.prize >= 0 {
                r.x = lerp(gr.fromX, g.chuteX, move)
                r.carry = u >= 0.9 && u < 2.3 ? 1 : 0
                r.fall = u >= 2.3 ? u - 2.3 : -1
            }
        }
        return r
    }
    static func scriptAt(_ g: Geo, _ t: Double) -> Frame {
        let target = g.px[2]
        if t < 1.2 { return Frame(x: lerp(g.homeX, target, smoother((t - 0.4) / 0.8)), prize: 2) }
        var r = grabAt(g, Grab(at: 1.2, fromX: target, prize: 2), t - 1.2)
        if t >= 5.6 { r.x = lerp(g.chuteX, g.homeX, smoother((t - 5.6) / 1)) }
        r.coupon = t >= 4 && t < 5.6 ? smoother((t - 4) / 0.3) * (1 - smoother((t - 5.2) / 0.4)) : 0
        r.respawn = t >= 7.6 ? smoother((t - 7.6) / 0.8) : 0
        return r
    }
}
