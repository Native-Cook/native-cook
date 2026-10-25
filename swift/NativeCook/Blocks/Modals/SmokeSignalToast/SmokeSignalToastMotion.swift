import Foundation

/*
 Smoke Signal Toast — geometry, the autoPlay script and the touch simulation, a port of the React
 Native smokeMotion.ts.

 loop = 9 s:
   0.0 – 1.3   rest: the fire crackles, a faint wisp rises.
   1.3 – 2.2   the fire flares and puffs a cloud (flare 1 → 1.35 → 1).
   2.2 – 3.8   the cloud rises to the middle of the sky (ease-in-out, 1.6 – 3.1) and condenses
               into the message: blurred words appear inside it (2.8 – 3.3), their scatter shrinks
               50 → 0 pt and their offset from below 50 → 0 pt (ease-in-out, 2.9 – 3.85) while
               the cloud thins away (3.0 – 3.7).
   3.8 – 5.6   readable hold.
   5.6 – 7.2   a gust from the left tears it to the right and it thins out (ease-in-out).
   7.2 – 9.0   the fire calms (flare dips to 0.85 and returns) and rests.
*/
enum SmokeSignalToastMotion {
    static let loop = 9.0
    static let reducedAt = 4.6
    static let idleBeforeBack = 2.0
    static let backFor = 0.9

    static let headerH = 60.0
    static let inputH = 52.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }

    /// cubic-bezier(0.23, 1, 0.32, 1), the message card's ease-out.
    static func cardEase(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        func bez(_ u: Double, _ a: Double, _ b: Double) -> Double {
            3 * (1 - u) * (1 - u) * u * a + 3 * (1 - u) * u * u * b + u * u * u
        }
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 {
            let mid = (lo + hi) / 2
            if bez(mid, 0.23, 0.32) < x { lo = mid } else { hi = mid }
        }
        return bez((lo + hi) / 2, 1, 1)
    }
}

struct SmokeSignalToastGeo: Equatable {
    var w = 1.0
    var h = 1.0
    var fireX = 0.0
    var fireY = 0.0
    var fireS = 1.0
    var treeY = 0.0
    var toastY = 0.0
    var inputTop = 0.0

    init() {}

    init(w: Double, h: Double, insetTop: Double, insetBottom: Double) {
        typealias M = SmokeSignalToastMotion
        let inputTop = h - max(insetBottom, 12) - 8 - M.inputH
        let fireS = min(1, h / 852)
        let fireY = inputTop - 40 * fireS
        let treeY = fireY - 70 * fireS
        let msgBottom = insetTop + M.headerH + 200
        self.w = w
        self.h = h
        fireX = w / 2
        self.fireY = fireY
        self.fireS = fireS
        self.treeY = treeY
        toastY = max(msgBottom + 70, min((msgBottom + treeY - 110) / 2 + 20, treeY - 170))
        self.inputTop = inputTop
    }
}

struct SmokeSignalToastFrame {
    var flare = 1.0
    /// Puff: x, y, radius, density.
    var puff: [Double] = [0, 0, 1, 0]
    /// Formed, scatter, rise, alpha.
    var text: [Double] = [0, 50, 50, 0]
    /// Wind direction and amount.
    var wind: [Double] = [1, 0, 0, 0]
    var phase = 0.0

    /// The autoPlay frame at loop time t (pure), so frame `loop` = frame 0.
    mutating func script(_ t: Double, _ g: SmokeSignalToastGeo) {
        typealias M = SmokeSignalToastMotion
        phase = t / M.loop
        // The flare when the puff leaves, the calm at the end.
        flare = 1 + 0.35 * sin(.pi * M.seg(t, 1.3, 2.2)) - 0.15 * sin(.pi * M.seg(t, 7.2, 8.6))
        // The puff: born at the fire, rising to the toast's line.
        let born = M.easeOut(M.seg(t, 1.3, 1.8))
        let up = M.easeInOut(M.seg(t, 1.6, 3.1))
        puff[0] = g.fireX + sin(up * .pi) * 18
        puff[1] = g.fireY - 40 * g.fireS - (g.fireY - 40 * g.fireS - g.toastY) * up
        puff[2] = 22 + 110 * up
        puff[3] = born * (1 - M.easeInOut(M.seg(t, 3.0, 3.7))) * 0.9
        // The message condenses out of it.
        let form = M.easeInOut(M.seg(t, 2.9, 3.85))
        text[0] = form
        text[1] = 50 * (1 - form)
        text[2] = 50 * (1 - form)
        text[3] = M.easeOut(M.seg(t, 2.8, 3.3))
        wind[0] = 1
        wind[1] = -0.18
        wind[2] = M.easeInOut(M.seg(t, 5.6, 7.2))
        if t >= 7.2 { text[3] = 0 }
    }
}

/// The touch simulation and the autoPlay clock, stepped once a frame like the RN frame callback.
@MainActor
final class SmokeSignalToastSim {
    private typealias M = SmokeSignalToastMotion
    enum Mode { case auto, live, back }

    private(set) var mode = Mode.auto
    private var t = 0.0
    private var now = 0.0
    private var last = 0.0
    private var back = 0.0
    private(set) var frame = SmokeSignalToastFrame()
    /// Swipe: whether a finger drags the message, how far, and the release animation.
    private(set) var dragging = false
    private var blow = 0.0
    private var blowV = 0.0
    private var blowTarget = 0.0
    /// Whether the message card is open.
    private(set) var open = false
    private var from: [Double] = [0, 0, 0, 0]
    private var lastDate: Date?
    private var wasAuto: Bool?
    private var blown = false
    /// The card's open/close animation: 0.32 s in, 0.22 s out, cubic-bezier(0.23, 1, 0.32, 1).
    private var cardFrom = 0.0
    private var cardAt = -10.0

    func step(to date: Date, geo g: SmokeSignalToastGeo, auto: Bool, reduced: Bool, ready: Bool) -> SmokeSignalToastFrame {
        if wasAuto != auto {
            wasAuto = auto
            mode = .auto
            t = 0
            now = 0
            last = 0
            frame = SmokeSignalToastFrame()
            blow = 0
            blowV = 0
            dragging = false
            open = false
            cardFrom = 0
            cardAt = -10
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        // The loop waits for the baked message; a stalled frame pauses the clock instead of
        // skipping ahead.
        let dt = !ready || raw > 0.25 ? 0 : min(max(raw, 0), 1.0 / 20)
        advance(dt, g, auto: auto, reduced: reduced)
        return frame
    }

    /// The card's progress, 0 closed – 1 open.
    var card: Double {
        let to = open ? 1.0 : 0.0
        let k = M.cardEase(M.clamp01((now - cardAt) / (open ? 0.32 : 0.22)))
        return cardFrom + (to - cardFrom) * k
    }

    /// True once when a swipe has blown the message away.
    func takeBlown() -> Bool {
        let b = mode == .live && !open && frame.wind[2] > 0.9
        defer { blown = b }
        return b && !blown
    }

    private func goLive() {
        if mode == .live { return }
        mode = .live
        blow = frame.wind[2]
        blowTarget = blow > 0.5 ? 1 : blow
        blowV = 0
        frame.puff[3] = 0
    }

    func touch() {
        goLive()
        last = now
    }

    /// Whether the message is up and readable enough to be tapped open or blown away.
    var readable: Bool {
        frame.text[3] > 0.5 && frame.text[0] > 0.6 && frame.wind[2] < 0.5
    }

    /// A swipe drags the wind: its direction and how far it has gone blow the words apart.
    func drag(_ dx: Double, _ dy: Double) {
        goLive()
        dragging = true
        last = now
        let d = hypot(dx, dy)
        if d > 4 {
            frame.wind[0] = dx / d
            frame.wind[1] = dy / d
        }
        blow = min(1, d / 260)
    }

    func dragEnd(_ vx: Double, _ vy: Double) {
        dragging = false
        last = now
        let fling = hypot(vx, vy)
        blowTarget = blow > 0.32 || fling > 650 ? 1 : 0
        blowV = min(4, fling / 400)
    }

    func setOpen(_ value: Bool) {
        goLive()
        if value != open {
            cardFrom = card
            cardAt = now
        }
        open = value
        last = now
        if value {
            // The words drift up out of the way as the card takes their place.
            frame.wind[0] = 0.15
            frame.wind[1] = -1
            blowTarget = 1
            blowV = 1.2
        }
    }

    private func advance(_ dt: Double, _ g: SmokeSignalToastGeo, auto: Bool, reduced: Bool) {
        now += dt
        if mode == .auto {
            if auto { t = (t + dt).truncatingRemainder(dividingBy: M.loop) }
            frame.script(reduced ? M.reducedAt : t, g)
            return
        }
        frame.phase += dt / M.loop
        if mode == .live {
            frame.flare += (1 - frame.flare) * min(1, dt * 3)
            if !dragging {
                // Released: a spring to blown away (1) or back together (0), carrying the fling.
                let w = 9.0
                let a = w * w * (blowTarget - blow) - 2 * 0.9 * w * blowV
                blowV += a * dt
                blow = min(1, max(0, blow + blowV * dt))
            }
            frame.wind[2] = blow
            if auto && !dragging && now - last > M.idleBeforeBack {
                mode = .back
                back = 0
                from = [frame.text[3], frame.wind[2], frame.phase, frame.flare]
                if open {
                    cardFrom = card
                    cardAt = now
                }
                open = false
            }
            return
        }
        back = min(1, back + dt / M.backFor)
        let k = M.easeInOut(back)
        frame.text[3] = from[0] * (1 - k)
        frame.wind[2] = from[1] + (1 - from[1]) * k * 0.5
        frame.phase = from[2] + (from[2].rounded(.up) - from[2]) * k
        frame.flare = from[3] + (1 - from[3]) * k
        if back >= 1 {
            mode = .auto
            t = 0
            blow = 0
            open = false
            frame.script(0, g)
        }
    }
}
