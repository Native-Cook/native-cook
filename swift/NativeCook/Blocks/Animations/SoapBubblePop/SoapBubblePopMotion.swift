import Foundation

/*
 Soap Bubble Pop — geometry, the autoPlay script and the touch simulation, a port of the React
 Native bubbleMotion.ts.

 loop = 8 s:
   0.0 – 1.2   rest: three bubbles hover (a 3–4 pt bob at 1 and 2 cycles per loop, so it repeats).
   1.2 – 3.0   they drift up (ease-in-out).
   3.0         a tap pops bubble A: a ragged hole tears across it in 0.2 s, droplets fly out and
               fall, a "+10" star floats up, the round score rolls 0 → 10.
   4.6         bubble B pops the same way (10 → 20).
   5.4 – 6.4   hold: only C hovers.
   6.4 – 6.95  a new A inflates from the wand's soap film, detaches and floats to its place
               (6.95 – 7.55); 6.95 – 7.5 a new B inflates, floats home 7.5 – 8.0. C sinks back
               to rest over 6.4 – 8.0; the score rolls back to 0 (a new round) 6.45 – 7.0.
   8.0         the frame at 0.
*/
enum SoapBubblePopMotion {
    static let loop = 8.0
    static let reducedAt = 0.4
    static let idleBeforeBack = 2.0
    static let backFor = 0.9

    /// Inflating at the wand, then floating to the bubble's place (s).
    static let inflate = 0.55
    static let travel = 0.6
    static let popAt = [3.0, 4.6]
    /// Where the scripted taps land, as a share of the bubble's radius.
    static let tapAt: [(Double, Double)] = [(0.42, -0.5), (-0.56, -0.18)]
    static let respawnAt = [6.4, 6.95]
    static let travelB = 0.5
    /// A popped bubble's star floats this long (s).
    static let starFor = 1.1

    static let headerH = 128.0
    static let cardH = 136.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }

    /// The hover: 3 pt sideways once a loop, 4 pt up and down twice (film = loop clock in loops).
    static func bobX(_ i: Int, _ film: Double) -> Double { 3 * sin(2 * .pi * (film + Double(i) * 0.31)) }
    static func bobY(_ i: Int, _ film: Double) -> Double { 4 * sin(2 * .pi * (2 * film + Double(i) * 0.17)) }
}

struct SoapBubblePopGeo: Equatable {
    var w = 1.0
    var h = 1.0
    /// Rest x, y and radius of A, B, C.
    var rest: [Double] = Array(repeating: 0, count: 9)
    /// Drift of each bubble (dx, dy) during 1.2 – 3.0.
    var drift: [Double] = Array(repeating: 0, count: 6)
    var wx = 0.0
    var wy = 0.0
    var wr = 24.0
    var cardTop = 0.0

    init() {}

    init(w: Double, h: Double, insetTop: Double, insetBottom: Double) {
        typealias M = SoapBubblePopMotion
        let cardTop = h - max(insetBottom, 16) - 12 - M.cardH
        let wy = cardTop - 56
        let fieldTop = insetTop + M.headerH + 8
        let fieldH = wy - 70 - fieldTop
        let lift = min(44, fieldH * 0.11)
        self.w = w
        self.h = h
        rest = [
            w * 0.3, fieldTop + fieldH * 0.3, min(w * 0.17, 72),
            w * 0.7, fieldTop + fieldH * 0.55, min(w * 0.14, 60),
            w * 0.36, fieldTop + fieldH * 0.84, min(w * 0.11, 46),
        ]
        drift = [8, -lift, -10, -lift * 0.8, 12, -lift * 0.7]
        wx = w * 0.5
        self.wy = wy
        wr = 24
        self.cardTop = cardTop
    }
}

/// Pose of one bubble: x, y, radius, alpha, pop age (< 0 whole), tap dx, tap dy, wobble.
typealias SoapBubblePopPose = [Double]

struct SoapBubblePopFrame {
    var poses: [SoapBubblePopPose] = [
        [0, 0, 0, 0, -1, 0, 0, 0.012],
        [0, 0, 0, 0, -1, 0, 0, 0.012],
        [0, 0, 0, 0, -1, 0, 0, 0.012],
    ]
    /// Wand film alpha.
    var wandFilm = 1.0
    /// Finger mark: x, y, age (< 0 none).
    var tap: [Double] = [0, 0, -1]
    /// Star age per bubble (< 0 none), and where it starts.
    var stars: [Double] = [-1, -1, -1]
    var starX: [Double] = [0, 0, 0]
    var starY: [Double] = [0, 0, 0]
    /// Round score in tens (continuous while it rolls).
    var score = 0.0
    var film = 0.0
}

extension SoapBubblePopMotion {
    /// Bubble i growing at the wand and floating home: s seconds after it started.
    static func inflatePose(_ g: SoapBubblePopGeo, _ i: Int, _ s: Double, _ travel: Double, _ film: Double,
                            _ out: inout SoapBubblePopPose) {
        let R = g.rest[i * 3 + 2]
        let k = easeOut(clamp01(s / inflate))
        let r = g.wr * 1.05 + (R - g.wr * 1.05) * k
        // Attached: the bubble balloons up out of the ring.
        let ax = g.wx
        let ay = g.wy - (r - g.wr * 0.92)
        let u = easeInOut(clamp01((s - inflate) / travel))
        let tx = g.rest[i * 3] + bobX(i, film) * u
        let ty = g.rest[i * 3 + 1] + bobY(i, film) * u
        let arc = sin(.pi * u) * (i == 0 ? -22 : 22)
        out[0] = ax + (tx - ax) * u + arc
        out[1] = ay + (ty - ay) * u
        out[2] = r
        out[3] = 1
        out[4] = -1
        out[5] = 0
        out[6] = 0
        // Wobbly while it grows and right after it lets go, calm by the time it lands.
        let since = max(0, s - inflate)
        out[7] = 0.012 + 0.03 * (1 - k) + 0.03 * exp(-since / 0.3) * (s > inflate ? 1 : 0) * (1 - u)
    }

    static func restPose(_ g: SoapBubblePopGeo, _ i: Int, _ dx: Double, _ dy: Double, _ film: Double,
                         _ out: inout SoapBubblePopPose) {
        out[0] = g.rest[i * 3] + dx + bobX(i, film)
        out[1] = g.rest[i * 3 + 1] + dy + bobY(i, film)
        out[2] = g.rest[i * 3 + 2]
        out[3] = 1
        out[4] = -1
        out[5] = 0
        out[6] = 0
        out[7] = 0.012
    }

    /// The autoPlay frame at loop time t (pure), so frame `loop` = frame 0.
    static func script(_ t: Double, _ g: SoapBubblePopGeo, _ f: inout SoapBubblePopFrame) {
        let film = t / loop
        f.film = film
        let up = easeInOut(seg(t, 1.2, 3.0))
        f.tap[2] = -1
        f.wandFilm = 1
        for i in 0..<2 {
            f.stars[i] = -1
            if t < popAt[i] {
                restPose(g, i, g.drift[i * 2] * up, g.drift[i * 2 + 1] * up, film, &f.poses[i])
            } else if t < respawnAt[i] {
                // Popped where it was when the finger came down.
                restPose(g, i, g.drift[i * 2], g.drift[i * 2 + 1], popAt[i] / loop, &f.poses[i])
                f.poses[i][4] = t - popAt[i]
                f.poses[i][5] = tapAt[i].0 * f.poses[i][2]
                f.poses[i][6] = tapAt[i].1 * f.poses[i][2]
                f.stars[i] = t - popAt[i] < starFor ? t - popAt[i] : -1
                f.starX[i] = f.poses[i][0]
                f.starY[i] = f.poses[i][1]
                let tapAge = t - popAt[i] + 0.08
                if tapAge < 0.45 {
                    f.tap[0] = f.poses[i][0] + f.poses[i][5]
                    f.tap[1] = f.poses[i][1] + f.poses[i][6]
                    f.tap[2] = tapAge
                }
            } else {
                inflatePose(g, i, t - respawnAt[i], i == 0 ? travel : travelB, film, &f.poses[i])
                if t - respawnAt[i] < inflate + 0.25 { f.wandFilm = 0 }
            }
            // The finger mark shows just before the pop too.
            let pre = t - popAt[i] + 0.08
            if pre >= 0 && pre < 0.08 {
                f.tap[0] = g.rest[i * 3] + g.drift[i * 2] + tapAt[i].0 * g.rest[i * 3 + 2]
                f.tap[1] = g.rest[i * 3 + 1] + g.drift[i * 2 + 1] + tapAt[i].1 * g.rest[i * 3 + 2]
                f.tap[2] = pre
            }
        }
        // The film comes back to the ring once the second bubble has left it.
        let back = seg(t, respawnAt[1] + inflate + 0.05, respawnAt[1] + inflate + 0.35)
        if t > respawnAt[1] + inflate { f.wandFilm = back }
        let settle = easeInOut(seg(t, 6.4, 8.0))
        restPose(g, 2, g.drift[4] * up * (1 - settle), g.drift[5] * up * (1 - settle), film, &f.poses[2])
        f.stars[2] = -1
        f.score = easeInOut(seg(t, 3.05, 3.4)) + easeInOut(seg(t, 4.65, 5.0)) - 2 * easeInOut(seg(t, 6.45, 7.0))
    }
}

/// The touch simulation and the autoPlay clock, stepped once a frame like the RN frame callback.
@MainActor
final class SoapBubblePopSim {
    private typealias M = SoapBubblePopMotion
    enum Mode { case auto, live, back }

    private(set) var mode = Mode.auto
    private var t = 0.0
    private var now = 0.0
    private var last = 0.0
    private var back = 0.0
    private(set) var frame = SoapBubblePopFrame()
    /// Base (un-bobbed) x, y of each whole bubble while live; spawn clock (< 0 none).
    private var base: [Double] = Array(repeating: 0, count: 6)
    private var spawn: [Double] = [-1, -1, -1]
    private var score = 0.0
    private var from: [Double] = Array(repeating: 0, count: 9)
    private var filmFrom = 0.0
    private var scoreFrom = 0.0
    private var lastDate: Date?
    private var wasAuto: Bool?

    func step(to date: Date, geo g: SoapBubblePopGeo, auto: Bool, reduced: Bool) -> SoapBubblePopFrame {
        if wasAuto != auto {
            wasAuto = auto
            mode = .auto
            t = 0
            now = 0
            last = 0
            frame = SoapBubblePopFrame()
            spawn = [-1, -1, -1]
            score = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        // A stalled frame pauses the clock instead of skipping ahead.
        let dt = raw > 0.25 ? 0 : min(max(raw, 0), 1.0 / 20)
        advance(dt, g, auto: auto, reduced: reduced)
        return frame
    }

    /// A finger lands: the script stops; whole bubbles keep hovering where they are.
    private func goLive() {
        if mode == .live { return }
        for i in 0..<3 {
            let p = frame.poses[i]
            base[i * 2] = p[0] - M.bobX(i, frame.film)
            base[i * 2 + 1] = p[1] - M.bobY(i, frame.film)
            spawn[i] = -1
            // A bubble still at the wand or on its way home finishes that trip under the finger's clock.
            if mode == .auto && p[4] < 0 && i < 2 && t >= M.respawnAt[i] {
                spawn[i] = t - M.respawnAt[i]
            }
        }
        score = frame.score.rounded()
        frame.score = score
        mode = .live
    }

    func touch() {
        goLive()
        last = now
    }

    /// A tap: pops the topmost whole bubble under the finger. Returns its index, or -1.
    func tap(_ x: Double, _ y: Double) -> Int {
        goLive()
        last = now
        frame.tap = [x, y, 0]
        for i in 0..<3 {
            let p = frame.poses[i]
            if p[4] >= 0 || p[3] < 0.5 { continue }
            let dx = x - p[0]
            let dy = y - p[1]
            if dx * dx + dy * dy > p[2] * p[2] * 1.12 { continue }
            base[i * 2] = p[0] - M.bobX(i, frame.film)
            base[i * 2 + 1] = p[1] - M.bobY(i, frame.film)
            spawn[i] = -1
            frame.poses[i][4] = 0
            frame.poses[i][5] = dx
            frame.poses[i][6] = dy
            frame.stars[i] = 0
            frame.starX[i] = p[0]
            frame.starY[i] = p[1]
            score = min(9, score + 1)
            return i
        }
        return -1
    }

    private func advance(_ dt: Double, _ g: SoapBubblePopGeo, auto: Bool, reduced: Bool) {
        now += dt
        if mode == .auto {
            if auto { t = (t + dt).truncatingRemainder(dividingBy: M.loop) }
            M.script(reduced ? M.reducedAt : t, g, &frame)
            return
        }
        if frame.tap[2] >= 0 { frame.tap[2] += dt }
        if mode == .live {
            frame.film += dt / M.loop
            frame.wandFilm = 1
            var busy = false
            for i in 0..<3 where spawn[i] >= 0 && spawn[i] < M.inflate + 0.25 { busy = true }
            for i in 0..<3 {
                if frame.stars[i] >= 0 { frame.stars[i] = frame.stars[i] + dt < M.starFor ? frame.stars[i] + dt : -1 }
                if spawn[i] >= 0 {
                    spawn[i] += dt
                    M.inflatePose(g, i, spawn[i], M.travel, frame.film, &frame.poses[i])
                    if spawn[i] < M.inflate + 0.25 { frame.wandFilm = 0 }
                    if spawn[i] >= M.inflate + M.travel {
                        spawn[i] = -1
                        base[i * 2] = g.rest[i * 3]
                        base[i * 2 + 1] = g.rest[i * 3 + 1]
                    }
                } else if frame.poses[i][4] >= 0 {
                    frame.poses[i][4] += dt
                    // Once its droplets are gone, a new one is blown at the wand (one at a time).
                    if frame.poses[i][4] > 1.3 && !busy {
                        spawn[i] = 0
                        busy = true
                        M.inflatePose(g, i, 0, M.travel, frame.film, &frame.poses[i])
                    }
                } else {
                    frame.poses[i][0] = base[i * 2] + M.bobX(i, frame.film)
                    frame.poses[i][1] = base[i * 2 + 1] + M.bobY(i, frame.film)
                    frame.poses[i][7] = 0.012
                }
            }
            // The score's digits roll to the new value and rest exactly on it.
            frame.score += (score - frame.score) * min(1, dt * 9)
            if abs(score - frame.score) < 0.002 { frame.score = score }
            if auto && now - last > M.idleBeforeBack {
                mode = .back
                back = 0
                for i in 0..<3 {
                    let p = frame.poses[i]
                    let whole = p[4] < 0 && spawn[i] < 0
                    from[i * 3] = whole ? p[0] - M.bobX(i, frame.film) : g.rest[i * 3]
                    from[i * 3 + 1] = whole ? p[1] - M.bobY(i, frame.film) : g.rest[i * 3 + 1]
                    from[i * 3 + 2] = whole ? 1 : 0
                }
                filmFrom = frame.film
                scoreFrom = frame.score
            }
            return
        }
        // Back to rest: whole bubbles glide home; missing ones fade in at their place.
        back = min(1, back + dt / M.backFor)
        let k = M.easeInOut(back)
        frame.film = filmFrom + (filmFrom.rounded(.up) - filmFrom) * k
        frame.wandFilm = max(frame.wandFilm, k)
        for i in 0..<3 {
            let bx = from[i * 3] + (g.rest[i * 3] - from[i * 3]) * k
            let by = from[i * 3 + 1] + (g.rest[i * 3 + 1] - from[i * 3 + 1]) * k
            M.restPose(g, i, bx - g.rest[i * 3], by - g.rest[i * 3 + 1], frame.film, &frame.poses[i])
            if from[i * 3 + 2] < 0.5 {
                frame.poses[i][3] = k
                frame.poses[i][2] = g.rest[i * 3 + 2] * (0.92 + 0.08 * k)
            }
            if frame.stars[i] >= 0 { frame.stars[i] = frame.stars[i] + dt < M.starFor ? frame.stars[i] + dt : -1 }
        }
        frame.score = scoreFrom * (1 - k)
        if back >= 1 {
            mode = .auto
            t = 0
            score = 0
            spawn = [-1, -1, -1]
            M.script(0, g, &frame)
        }
    }
}
