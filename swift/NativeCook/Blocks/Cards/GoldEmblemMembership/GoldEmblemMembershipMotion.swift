import Foundation

enum GoldEmblemMembershipMotion {
    static let LOOP = 10.0
    static let CAST = 1.7
    static let casts: [(at: Double, from: Int, to: Int, side: Double)] = [(0.8, 0, 1, 1), (3.8, 1, 2, 1), (7.3, 2, 0, -1)]
    struct Frame {
        var from = 0, to = 0
        var cast = 0.0, side = 1.0, drag = 0.0, finger = 0.0, fingerX = 0.0, press = 0.0
        var shown: Int { cast >= 0.5 ? to : from }
        var words: Double { cast <= 0 ? 1 : cast < 0.3 ? 1-cast/0.3 : cast < 0.82 ? 0 : (cast-0.82)/0.18 }
        var wordsY: Double { cast > 0 && cast < 0.5 ? cast/0.3*4 : 0 }
    }
    static func clamp01(_ v: Double) -> Double { max(0, min(1, v)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2 }
    static func easeOut(_ x: Double) -> Double { 1-pow(1-x, 3) }
    // Reanimated's default Easing.inOut(Easing.quad).
    static func timing(_ x: Double) -> Double { x < 0.5 ? 2*x*x : 1-pow(-2*x+2, 2)/2 }
    static func frameAt(_ t: Double) -> Frame {
        var s = Frame()
        for c in casts where t >= c.at-0.5 {
            s.from = c.from; s.to = c.to; s.side = c.side
            s.cast = seg(t, c.at, c.at+CAST)
            let sw = seg(t, c.at-0.45, c.at+0.05)
            let f = easeOut(seg(t, c.at-0.5, c.at-0.4))*(1-easeOut(seg(t, c.at+0.02, c.at+0.16)))
            if f > 0 { s.finger = f; s.fingerX = c.side*(90-200*easeInOut(sw)) }
            s.drag = -c.side*26*sin(.pi*easeInOut(sw))
        }
        if s.cast >= 1 { s.from = s.to; s.cast = 0 }
        s.press = s.finger
        return s
    }
}
