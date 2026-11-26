import Foundation

/// The RN clock in seconds. Autoplay is analytic; interaction carries the current flow phase through the return.
final class HoneyDrizzleStepperMotion {
    struct Frame { var amount = 2.0, flow = 0.0, pop = 1.0; var number = 2; var still = false }
    var out = Frame()
    var mode = 0, last = -1.0, start = -1.0, flowFrom = 0.0, carry = 1000.0
    var amount = 2.0, flow = 0.0, popAge = 1000.0, dragFrom = 2.0
    var e0 = 2.0, e1 = 2.0, age = -1.0, idle = 0.0
    var dragging = false, homing = false, touches = 0, shown = 2, ticks = 0
    static func amountAt(_ t: Double) -> Double {
        func io(_ x: Double) -> Double { x < 0.5 ? 2*x*x : 1-pow(-2*x+2, 2)/2 }
        if t < 0.6 { return 2 }; if t < 2.4 { return 2+3*io((t-0.6)/1.8) }
        if t < 3.4 { return 5 }; if t < 5 { return 5-3*io((t-3.4)/1.6) }; return 2
    }
    static func inverse(_ y: Double) -> Double { y < 0.5 ? sqrt(y/2) : 1-sqrt((1-y)/2) }
    static let thresholds: [Double] = [1.0/6, 0.5, 5.0/6]
    static let changes: [Double] = thresholds.map { 0.6+1.8*inverse($0) } + thresholds.map { 3.4+1.6*inverse($0) }
    static func changedAgo(_ t: Double) -> Double { changes.filter { $0 <= t }.map { t-$0 }.min() ?? 1000 }
    static func pop(_ age: Double) -> Double { age < 0.25 ? 1+0.12*pow(1-age/0.25, 3) : 1 }
    static func loopFrame(_ t: Double, _ offset: Double = 0, _ carried: Double = 1000) -> Frame {
        let t = t.truncatingRemainder(dividingBy: 6), a = amountAt(t)
        return Frame(amount: a, flow: (t+offset).truncatingRemainder(dividingBy: 6), pop: pop(min(changedAgo(t), carried)), number: Int(a.rounded()))
    }
    func takeOver() {
        guard mode == 0 else { return }
        let since = start < 0 ? 0 : last-start, t = since.truncatingRemainder(dividingBy: 6)
        mode = 1; amount = Self.amountAt(t); flow = (t+flowFrom).truncatingRemainder(dividingBy: 6)
        popAge = min(Self.changedAgo(t), carry+since); shown = Int(amount.rounded()); age = -1
    }
    func easeTo(_ to: Double) { e0 = amount; e1 = to; age = 0 }
    func touchDown() { takeOver(); touches += 1; idle = 0; homing = false }
    func touchUp() { touches = max(0, touches-1); idle = 0 }
    func dragStart() { takeOver(); dragging = true; homing = false; age = -1; dragFrom = amount }
    func dragTo(_ dy: Double) { if dragging { amount = min(5, max(1, dragFrom-dy/60)) } }
    func dragEnd() { guard dragging else { return }; dragging = false; idle = 0; easeTo(amount.rounded()) }
    func stepBy(_ dir: Int) {
        takeOver(); idle = 0; homing = false
        let to = min(5, max(1, (age >= 0 ? e1 : amount.rounded())+Double(dir)))
        if to != amount { easeTo(to) }
    }
    func setStill(_ still: Bool) {
        if still && mode != 2 {
            amount = mode == 0 ? 2 : amount.rounded(); mode = 2; age = -1; dragging = false
            shown = Int(amount.rounded()); out = Frame(amount: amount, number: shown, still: true)
        } else if !still && mode == 2 { mode = 0; start = -1; flowFrom = 0; carry = 1000; amount = 2; age = -1 }
    }
    func step(_ now: Double, autoPlay: Bool) {
        let dt = last < 0 ? 0 : min(max(now-last, 0), 0.05)
        if mode == 0 {
            if autoPlay {
                last = now; if start < 0 { start = now }
                let since = now-start; out = Self.loopFrame(since, flowFrom, carry+since); return
            }
            // Pause from the frame on screen before advancing the live clock by dt.
            takeOver()
        }
        last = now
        if mode != 2 { flow = (flow+dt).truncatingRemainder(dividingBy: 6) }
        if !dragging && age >= 0 {
            age = min(0.3, age+dt); amount = e0+(e1-e0)*(1-pow(1-age/0.3, 3))
            if age >= 0.3 { amount = e1; age = -1 }
        }
        let moving = dragging || age >= 0
        popAge += dt; let n = Int(amount.rounded())
        if n != shown { shown = n; popAge = 0; if !homing { ticks += 1 } }
        out = Frame(amount: amount, flow: mode == 2 ? 0 : flow, pop: mode == 2 ? 1 : Self.pop(popAge), number: n, still: mode == 2)
        if touches > 0 || moving { if !homing { idle = 0 }; if moving { return } }
        if touches > 0 { return }; idle += dt
        if !autoPlay || mode == 2 || idle < 2 { return }
        if amount != 2 { homing = true; easeTo(2); return }
        mode = 0; homing = false; start = now; flowFrom = flow.truncatingRemainder(dividingBy: 1.5)
        carry = popAge; out = Self.loopFrame(0, flowFrom, carry)
    }
}
