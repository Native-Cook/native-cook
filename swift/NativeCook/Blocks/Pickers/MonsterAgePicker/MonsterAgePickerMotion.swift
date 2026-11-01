import Foundation

// Direct translation of the RN worklet functions and its semi-implicit Euler driver.
enum MonsterAgePickerMotion {
    static let loop = 9.0
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smoother(_ k: Double) -> Double {
        let x = clamp(k)
        return x*x*x*(x*(x*6-15)+10)
    }
    static func chomp(_ u: Double) -> Double {
        if u < 0 || u > 0.7 { return 0 }
        if u < 0.16 { return smoother(u / 0.16) }
        if u < 0.28 { return 1 }
        let k = (u-0.28)/0.42
        return (1-k)*(1-k)*cos(k*4.2)
    }
    static func script(_ time: Double) -> (off: Double, jaw: Double) {
        let t = time.truncatingRemainder(dividingBy: loop)
        var off = 2.0
        if t >= 1 && t < 2.6 { off = 2+3*smoother((t-1)/1.6) }
        else if t >= 2.6 && t < 6.2 { off = 5 }
        else if t >= 6.2 && t < 7.8 { off = 5-3*smoother((t-6.2)/1.6) }
        return (off, chomp(t-3.4))
    }
    // The RN pin hook leaves React's chosen label dependent on earlier pinned times.
    // Replay its tick/reaction events so a fresh Swift launch has a deterministic label.
    static func pinnedChosen(_ time: Double) -> Int {
        let end = max(0,time.truncatingRemainder(dividingBy:loop))
        var t = 0.0, previousJaw = 0.0
        var tick = 2, chosen = -1
        while t < end {
            t = min(end,t+1/60)
            let f = script(t)
            let next = Int(f.off.rounded())
            if next != tick { tick = next; chosen = -1 }
            if f.jaw > 0.5 && previousJaw <= 0.5 { chosen = next }
            previousJaw = f.jaw
        }
        return chosen
    }
    struct Frame {
        var off = 2.0, v = 0.0, jaw = 0.0, look = 0.0, blink = 0.0
    }
}
