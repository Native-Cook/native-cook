import SwiftUI

// The reference's worklet functions, including its distinct scripted/manual filing curves.
enum MailboxInboxMotion {
    static let loop = 9.0
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smoother(_ k: Double) -> Double {
        let x = clamp(k)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    struct Frame {
        var open = 0.0
        var filed = [0.0, 0, 0, 0]
    }
    static func at(_ time: Double) -> Frame {
        let t = time.truncatingRemainder(dividingBy: loop)
        var open = 0.0
        if t >= 1 && t < 2.5 { open = (t - 1) / 1.5 }
        else if t >= 2.5 && t < 6.2 { open = 1 }
        else if t >= 6.2 && t < 7.6 { open = 1 - (t - 6.2) / 1.4 }
        let filed = t >= 4.2 && t < 6.2 ? smoother((t - 4.2) / 0.8) : (t >= 6.2 && t < 7.6 ? 1.0 : 0.0)
        return Frame(open: open, filed: [0, 0, filed, 0])
    }
    static func letter(_ open: Double, _ i: Int) -> Double {
        smoother(open * 1.5 - Double(i) * 0.12)
    }
    struct Pose {
        var k, x, y, scale, angle: Double
    }
    static func pose(_ f: Frame, _ i: Int, _ width: Double, _ top: Double) -> Pose {
        let k = letter(f.open, i) * (1 - f.filed[i])
        let rowTop = top + 296 + Double(i) * 88
        let fromX = -88.0 * 0.68
        let fromY = top + 150 - 88 * 0.1 - rowTop - 39
        var shift = 0.0
        for j in 0..<i { shift += smoother(f.filed[j] * 1.4 - 0.3) * 88 }
        return Pose(k: k, x: fromX * (1-k), y: fromY * (1-k) - sin(.pi*k)*60 - shift,
                    scale: 0.22 + 0.78*k, angle: (1-k) * (i % 2 == 1 ? 24 : -18))
    }
}

final class MailboxInboxClock: ObservableObject {
    @Published var running = false
    private var frame = MailboxInboxMotion.Frame()
    private var target = 0.0
    private var fileT = [false, false, false, false]
    private var lastTouch = -100.0
    private var scripted = false
    private var loopT = 0.0
    private var now = 0.0
    private var date: Date?
    var holdingOpen: Bool { !scripted && frame.open == 1 && target == 1 }
    func sample(_ at: Date, auto: Bool, reduced: Bool, pinned: Double?) -> MailboxInboxMotion.Frame {
        if let pinned { return MailboxInboxMotion.at(pinned) }
        let wall = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0.016))
        date = at
        now += wall
        let dt = min(wall, 0.05)
        if auto && !reduced && now-lastTouch > 3.5 && (scripted || (frame.open < 0.001 && target == 0)) {
            if !scripted { scripted = true; loopT = 0 }
            loopT = (loopT + wall).truncatingRemainder(dividingBy: MailboxInboxMotion.loop)
            frame = MailboxInboxMotion.at(loopT)
            target = frame.open > 0.5 ? 1 : 0
            return frame
        }
        scripted = false
        let speed = reduced ? 4.0 : 1 / 1.4
        if frame.open < target { frame.open = min(target, frame.open + dt * speed) }
        if frame.open > target { frame.open = max(target, frame.open - dt * speed) }
        for i in 0..<4 {
            if fileT[i] { frame.filed[i] = min(1, frame.filed[i] + dt / 0.8) }
            if frame.open <= 0.001 { frame.filed[i] = 0; fileT[i] = false }
        }
        let moving = frame.open != target || (0..<4).contains { fileT[$0] && frame.filed[$0] < 1 }
        let waiting = auto && !reduced && frame.open < 0.001 && target == 0
        if running && !moving && !waiting {
            DispatchQueue.main.async { [weak self] in self?.running = false }
        }
        return frame
    }
    func toggle() {
        target = target > 0.5 ? 0 : 1
        touched()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
    func file(_ i: Int) { fileT[i] = true; touched() }
    private func touched() {
        scripted = false
        lastTouch = now
        date = Date()
        running = true
    }
    func resetDate() { date = nil }
}
