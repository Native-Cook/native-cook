import Foundation

// The RN script is analytic; debug frames need no stateful replay.
enum StickerPeelProfileMotion {
    static let loop = 9.0
    static let half: [(Double, Double)] = [(52,48),(27,25),(46,22),(25,31)]
    struct Sticker {
        var x = 0.0, y = 0.0, rot = 0.0, f = 0.0, vf = 0.0
        var corner = 2
        var lift = 0.0, sq = 0.0, vsq = 0.0, vx = 0.0, k = 1.0
    }
    static func clamp(_ x: Double) -> Double { max(0,min(1,x)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b-a)*k }
    static func smooth(_ k: Double) -> Double { let x = clamp(k); return x*x*(3-2*x) }
    static func ease(_ k: Double) -> Double {
        let x = clamp(k)
        return x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2
    }
    static func home(_ width: Double, _ top: Double) -> [Sticker] {
        [Sticker(x:width/2,y:top+128), Sticker(x:width/2-56,y:top+86,rot:-14),
         Sticker(x:width/2-50,y:top+170,rot:-10), Sticker(x:width/2+52,y:top+168,rot:10)]
    }
    static func frame(_ time: Double, _ width: Double, _ top: Double) -> [Sticker] {
        var ss = home(width,top)
        let t = time.truncatingRemainder(dividingBy: loop)
        let a = t < 5.2 ? 0.8 : 5.2
        let f = t < a+0.9 ? 0.62*ease((t-a)/0.85) : 0
        let lift = t >= a+0.85 ? 1-smooth((t-(a+1.75))/0.08) : 0
        let k = ease((t-(a+0.9))/0.85)
        let land = t-(a+1.8)
        let sq = land >= 0 && land < 0.5 ? sin(land*22)*exp(-land*9)*0.1 : 0
        let h = ss[2]
        let ax = width-74, ay = top+324.0
        ss[2] = Sticker(x: mix(t < 5.2 ? h.x : ax,t < 5.2 ? ax : h.x,k),
                        y: mix(t < 5.2 ? h.y : ay,t < 5.2 ? ay : h.y,k)-sin(k * .pi)*40,
                        rot: mix(t < 5.2 ? h.rot : 14,t < 5.2 ? 14 : h.rot,k)+sin(k * .pi)*(t < 5.2 ? -12 : 12),
                        f:f, corner:t < 5.2 ? 2 : 0, lift:lift, sq:sq)
        return ss
    }
}
