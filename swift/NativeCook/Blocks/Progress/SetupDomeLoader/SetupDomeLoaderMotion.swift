import Foundation

// Double precision throughout, matching the RN worklets.
enum SetupDomeLoaderMotion {
    static let loop = 9.0
    static let orbitW = 4 * Double.pi / loop
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func ramp(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b-a)*k }
    static func easeOut(_ k: Double) -> Double { 1-pow(1-clamp(k),3) }
    static func easeIn(_ k: Double) -> Double { pow(clamp(k),3) }
    static func easeInOut(_ k: Double) -> Double {
        let x = clamp(k)
        return x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2
    }
    static func backOut(_ k: Double) -> Double {
        let x = clamp(k)-1
        return 1+2.9*pow(x,3)+1.9*x*x
    }
    struct Phase {
        var c, b, k, burst, chk, tick, swap, prog: Double
    }
    static func phase(_ t: Double) -> Phase {
        Phase(
            c: t < 8.75 ? easeInOut(ramp(t,6.4,7)) : 1-easeOut(ramp(t,8.75,9)),
            b: t < 8.6 ? easeIn(ramp(t,7,7.35)) : 1-easeOut(ramp(t,8.6,8.8)),
            k: t < 8.5 ? ramp(t,7.3,7.42) : 1-ramp(t,8.5,8.65),
            burst: t >= 7.35 && t < 7.95 ? (t-7.35)/0.6 : 0,
            chk: t < 8.45 ? backOut(ramp(t,7.45,7.95)) : 1-easeIn(ramp(t,8.45,8.65)),
            tick: t < 8.45 ? easeOut(ramp(t,7.75,8.05)) : 1-ramp(t,8.45,8.55),
            swap: t < 8.5 ? ramp(t,7.4,7.9) : 1-ramp(t,8.5,8.9),
            prog: t < 7 ? clamp(t/7) : t < 8.5 ? 1 : 1-ramp(t,8.5,8.9))
    }
    struct Item {
        var kind: Int
        var a, h, s, spin, delay: Double
    }
    static let items: [Item] = [
        .init(kind:0,a:0,h:0.62,s:1,spin:1.0,delay:0),
        .init(kind:1,a:1.3,h:0.45,s:1,spin:-0.5,delay:0.06),
        .init(kind:2,a:2.6,h:0.78,s:1,spin:1.5,delay:0.12),
        .init(kind:3,a:3.7,h:0.3,s:1,spin:2.0,delay:0.03),
        .init(kind:3,a:5,h:0.56,s:0.82,spin:-2.5,delay:0.09)]
    struct Output {
        var t = 0.0
        var off = 0.0
        var hop = [Double](repeating:0,count:5)
        var clock = 0.0
    }
}
