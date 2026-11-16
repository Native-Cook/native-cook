import Foundation

/// Exact analytic timeline from tfs-motion.ts. All geometry is in 402 × 874 reference points.
enum TokenFusionSplashMotion {
    static let loop = 7.5
    static let captures = [2.0, 2.55, 2.95, 3.28]
    static let speeds = [6.0, 9, 13, 19, 19, 19, 19]
    static let firstRingPeak = atan(2 * Double.pi * 3.2 * 0.26) / (2 * Double.pi * 3.2)
    struct TokenFusionSplashCoin {
        var x: Double, y: Double, r: Double, reach: Double, dye: Double, lean: Double, heat: Double
    }
    struct TokenFusionSplashScene {
        var x: Double, y: Double, r: Double, sphere: Double, stretch: Double, axis: Double
        var ringScale: Double, mark: Double, markSize: Double, cut: Double, word: Double, wordY: Double, flood: Double
        var coins: [TokenFusionSplashCoin]
    }
    static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi, max(lo, x)) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func smooth(_ x: Double) -> Double { let t = clamp(x); return t * t * (3 - 2 * t) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { smooth((t - a) / max(b - a, 0.0001)) }
    static func spring(_ t: Double, _ damping: Double, _ stiffness: Double, _ mass: Double = 1) -> Double {
        if t <= 0 { return 0 }
        let w = sqrt(stiffness / mass), z = damping / (2 * sqrt(stiffness * mass))
        let wd = w * sqrt(max(0.0001, 1 - z * z))
        return 1 - exp(-z * w * t) * (cos(wd * t) + z * w / wd * sin(wd * t))
    }
    static func ring(_ t: Double, _ speed: Double = 0) -> Double {
        if t < 0 { return 0 }
        return (0.09 + 0.09 * clamp(speed / 1500)) * sin(2 * .pi * 3.2 * t) * exp(-t / 0.26)
    }
    static func orbit(_ i: Int, _ count: Int, _ angle: Double, _ gather: Double, _ mass: Double) -> (x: Double, y: Double) {
        let a = ((count == 4 ? [-125.0, -35, 65, 155][i] : -125 + Double(i) * 360 / Double(count)) + angle) * .pi / 180
        let tilt = -18.0 * .pi / 180, gain = pow(0.93, mass)
        let x = mix(176, 150, gather) * gain * cos(a), y = mix(150, 118, gather) * gain * sin(a)
        return (201 + x * cos(tilt) - y * sin(tilt), 400 + x * sin(tilt) + y * cos(tilt))
    }
    static func reach(_ x: Double, _ y: Double, _ r: Double, _ cx: Double, _ cy: Double, _ cr: Double) -> Double {
        1 - seg(hypot(x - cx, y - cy) - r - cr, 30, 96)
    }
    static func sceneAt(_ time: Double, count: Int = 4) -> TokenFusionSplashScene {
        let t = (time.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        let returning = t > 5.7, back = seg(t, 5.7, 7)
        let gather = returning ? 1 - back : clamp(spring(t - 0.4, 14, 120))
        var area = 0.0, mass = 0.0, angle = 6 * min(t, 5.7), ringing = 0.0
        for i in 0..<count {
            let cap = count == 4 ? captures[i] : 2 + Double(i) * (1.28 / Double(max(1, count - 1)))
            let p = returning ? 1 - seg(t, 5.84 + Double(i) * 0.07, 6.85 + Double(i) * 0.04) : seg(t, cap, cap + 0.32)
            area += 22 * 22 * p
            mass += clamp(spring(t - cap - 0.18 - firstRingPeak - 0.12, 18, 90)) * (1 - back)
            if t > cap + 0.2 { angle += max(0, t - cap - 0.32) * (speeds[min(i + 1, 6)] - speeds[min(i, 6)]) }
            ringing += ring(t - cap - 0.18) * (1 - back)
        }
        angle *= 1 - back
        let base = sqrt(mix(120 * 120, 55 * 55, gather) + area)
        let flood = seg(t, 3.75, 4.6) * (1 - back), r = mix(base, 980, flood)
        var coins: [TokenFusionSplashCoin] = []
        for i in 0..<count {
            let cap = count == 4 ? captures[i] : 2 + Double(i) * (1.28 / Double(max(1, count - 1)))
            let slot = orbit(i, count, angle, gather, mass)
            let approach = i == 0 ? seg(t, 1, 1.7) : seg(t, cap - 0.7, cap - 0.08)
            let swallowed = seg(t, cap, cap + 0.32), release = seg(t, 5.84 + Double(i) * 0.07, 6.85 + Double(i) * 0.04)
            let travel = returning ? 1 - release : approach, progress = returning ? 1 - release : swallowed
            let dx = slot.x - 201, dy = slot.y - 400, len = max(1, hypot(dx, dy))
            let rr = 22 * sqrt(max(0, 1 - progress)), destination = 55.0 + 44 + 22
            let radial = mix(len, mix(destination, 20, swallowed), travel)
            let x = 201 + dx / len * radial, y = 400 + dy / len * radial
            coins.append(TokenFusionSplashCoin(x: x, y: y, r: rr, reach: reach(x, y, rr, 201, 400, base) * travel,
                              dye: max(approach * (1 - swallowed), 1 - seg(t, cap, cap + 2.4)) * (approach > 0 ? 1 : 0) * (1 - back),
                              lean: sign(201 - x) * min(1, mass) * 6 * .pi / 180, heat: approach * (1 - progress) * (1 - back)))
        }
        let mark = returning ? back : 1 - seg(t, 1.12, 1.35) + seg(t, 4.18, 4.92)
        return TokenFusionSplashScene(x: 201, y: 400, r: r, sphere: 1 - gather * 0.6, stretch: 1 + ringing, axis: -2.1,
                     ringScale: 1 - gather * 0.6 * 0.54, mark: clamp(mark),
                     markSize: returning ? mix(60, 60, back) : mix(mix(60, 40, gather), 60, seg(t, 4.18, 4.92)),
                     cut: seg(t, 4.18, 4.42) * (1 - seg(t, 4.42, 4.92)) * (1 - back),
                     word: seg(t, 4.96, 5.2) * (1 - seg(t, 5.7, 5.94)),
                     wordY: 470 + 8 * clamp(1 - seg(t, 4.96, 5.2) + back), flood: flood, coins: coins)
    }
    static func blend(_ a: TokenFusionSplashScene, _ b: TokenFusionSplashScene, _ p: Double) -> TokenFusionSplashScene {
        var s = b
        s.x = mix(a.x, b.x, p); s.y = mix(a.y, b.y, p); s.r = mix(a.r, b.r, p)
        s.sphere = mix(a.sphere, b.sphere, p); s.stretch = mix(a.stretch, b.stretch, p); s.axis = mix(a.axis, b.axis, p)
        s.ringScale = mix(a.ringScale, b.ringScale, p); s.mark = mix(a.mark, b.mark, p); s.markSize = mix(a.markSize, b.markSize, p)
        s.cut = mix(a.cut, b.cut, p); s.word = mix(a.word, b.word, p); s.wordY = mix(a.wordY, b.wordY, p); s.flood = mix(a.flood, b.flood, p)
        for i in b.coins.indices {
            s.coins[i].x = mix(a.coins[i].x, b.coins[i].x, p); s.coins[i].y = mix(a.coins[i].y, b.coins[i].y, p)
            s.coins[i].r = mix(a.coins[i].r, b.coins[i].r, p); s.coins[i].reach = mix(a.coins[i].reach, b.coins[i].reach, p)
            s.coins[i].dye = mix(a.coins[i].dye, b.coins[i].dye, p)
            // The RN blend deliberately takes lean and heat from b immediately.
        }
        return s
    }
    static func sign(_ x: Double) -> Double { x == 0 ? 0 : (x < 0 ? -1 : 1) }
}
