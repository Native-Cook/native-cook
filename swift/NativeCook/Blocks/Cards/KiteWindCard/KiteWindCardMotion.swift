import Foundation

/// The committed RN clock and touch model, in seconds and screen points.
enum KiteWindMotion {
    static let winds = [10.0, 12, 15, 18, 21, 24, 26, 22, 17, 12]
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t-a)/(b-a))) }
    static func out(_ x: Double) -> Double { 1-pow(1-x, 3) }
    static func quad(_ x: Double) -> Double { x < 0.5 ? 2*x*x : 1-pow(-2*x+2, 2)/2 }
    static func phase(_ c: Double) -> Double { c-floor(c/8)*8 }
    static func wind(_ p: Double) -> Double {
        let q = min(9, max(0, p)), i = min(8, Int(q))
        return winds[i]+(winds[i+1]-winds[i])*(q-Double(i))
    }
    static func hour(_ p: Double) -> String {
        let h = min(9, max(0, Int(p.rounded())))+9
        return h < 12 ? "\(h) am" : "\(h == 12 ? 12 : h-12) pm"
    }
    static func loopP(_ t: Double) -> Double {
        if t < 1.2 { return 3 }
        if t < 3.2 { return 3+3*quad(seg(t, 1.2, 3.2)) }
        if t < 4.6 { return 6 }
        if t < 6.4 { return 6-3*quad(seg(t, 4.6, 6.4)) }
        return 3
    }
    struct Frame {
        var p = 3.0, t = 0.0, finger = 0.0, x = 0.0, y = 0.0
    }
    struct Touch {
        var manual = false, dragging = false, pulling = false
        var last = -100.0, p0 = 3.0, dx = 0.0, from = 3.0, to = 3.0, at = -100.0, duration = 0.35
        var kx = 0.0, ky = 0.0, kFromX = 0.0, kFromY = 0.0, kAt = -100.0
        func p(_ c: Double) -> Double {
            dragging ? min(9, max(0, p0+dx/31.4)) : from+(to-from)*out(seg(c, at, at+duration))
        }
        func pull(_ c: Double) -> (Double, Double) {
            if pulling { return (kx, ky) }
            let u = c-kAt
            if u < 0 || u > 1.2 { return (0, 0) }
            let k = exp(-7*u)*cos(12*u)
            return (kFromX*k, kFromY*k)
        }
        func frame(_ c: Double, _ reduced: Bool, _ auto: Bool) -> Frame {
            let t = reduced ? 0 : phase(c)
            if !manual {
                let f = seg(t, 1, 1.2)*(1-seg(t, 3.2, 3.4))+seg(t, 4.4, 4.6)*(1-seg(t, 6.4, 6.6))
                return Frame(p: reduced ? 3 : loopP(t), t: t, finger: reduced ? 0 : f)
            }
            let k = reduced ? (0.0, 0.0) : pull(c)
            return Frame(p: p(c), t: t, x: k.0, y: k.1)
        }
        mutating func takeOver(_ c: Double, _ reduced: Bool, _ auto: Bool) {
            last = c
            if manual { return }
            let current = frame(c, reduced, auto).p
            manual = true; from = current; to = current; at = c
        }
        mutating func go(_ target: Double, _ c: Double, _ reduced: Bool) {
            from = p(c); to = min(9, max(0, target)); at = c; duration = 0.35
            if reduced { from = to }
            dragging = false; last = c
        }
        mutating func step(_ c: Double, _ auto: Bool, _ reduced: Bool) {
            guard manual, auto, !reduced, !dragging, !pulling, c-last >= 2,
                  c >= at+duration, c >= kAt+1.2 else { return }
            if abs(p(c)-3) > 0.001 {
                let previous = last; go(3, c, false); last = previous; duration = 0.6
            } else if phase(c) >= 6.6 || phase(c) < 1 {
                self = Touch()
            }
        }
    }
}
