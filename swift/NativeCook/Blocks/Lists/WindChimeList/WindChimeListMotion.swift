import Foundation

/*
 Wind Chime List — the sessions, the scripted scroll and every tube's swing, as pure functions
 of the loop time.

 loop = 9 s
   0.0 – 1.5   rest at the top of the list.
   1.5 – 4.0   a fling down to 420 pt (fast start, exponential slow-down). The tubes lag the
               scroll: each one is a damped pendulum (ζ 0.22, 1.0–2.4 Hz by its length) kicked
               when the scroll speeds up and slows down, 40 ms later per row, neighbours swinging
               opposite ways, so at the peaks they touch and ring.
   4.2         row 7 (mid-screen) is struck: it swings hard (ζ 0.12) and its waveform plays for 2.4 s.
   5.2 – 7.4   the list scrolls back up (ease-in-out), kicking the tubes again.
   7.4 – 9.0   the swings die out (a window closes the last 0.6 s so t = 9 equals t = 0).
*/
enum WindChimeListMotion {
    static let rowH = 92.0
    static let tubeH = 64.0
    static let stringH = rowH - tubeH
    static let rowDelay = 0.04
    private static let flingAt = 1.5
    private static let flingEnd = 4.0
    private static let flingTo = 420.0
    private static let backAt = 5.2
    private static let backEnd = 7.4
    /// The struck row: the seventh, which sits mid-screen at the end of the fling.
    static let strikeRow = 6
    static let strikeAt = 4.2
    static let playLen = 2.4
    /// A row turns by visualTurn of its swing angle and sways sideways `sway` pt per radian, so two
    /// neighbours swinging apart by `touch` close the string gap to within a few points: they touch.
    static let visualTurn = 0.35
    static let sway = 26.0
    static let touch = 0.37
    static let ringLen = 0.55

    struct Session {
        let title: String
        let meta: String
        let minutes: Int
        let brass: Bool
        let shrink: Double
        let seed: Double
    }

    static let sessions: [Session] = [
        Session(title: "Low Tide Bowls", meta: "Singing bowls", minutes: 24, brass: true, shrink: 0, seed: 1),
        Session(title: "Cedar Hollow", meta: "Bamboo chimes", minutes: 18, brass: false, shrink: 22, seed: 2),
        Session(title: "Slow Lantern", meta: "Gong & drone", minutes: 32, brass: true, shrink: 10, seed: 3),
        Session(title: "Morning Kelp", meta: "Crystal bowls", minutes: 15, brass: true, shrink: 30, seed: 4),
        Session(title: "Rain on Slate", meta: "Field recording", minutes: 40, brass: false, shrink: 6, seed: 5),
        Session(title: "Night Ferry", meta: "Drone", minutes: 28, brass: true, shrink: 18, seed: 6),
        Session(title: "Moss Garden", meta: "Bamboo chimes", minutes: 12, brass: false, shrink: 36, seed: 7),
        Session(title: "Ember Circle", meta: "Frame drum", minutes: 22, brass: true, shrink: 4, seed: 8),
        Session(title: "Still Harbour", meta: "Singing bowls", minutes: 35, brass: true, shrink: 26, seed: 9),
        Session(title: "Paper Lanterns", meta: "Kalimba", minutes: 16, brass: false, shrink: 14, seed: 10),
        Session(title: "Fog Bell", meta: "Bells & breath", minutes: 20, brass: true, shrink: 32, seed: 11),
        Session(title: "Riverstone", meta: "Water & bowls", minutes: 45, brass: false, shrink: 8, seed: 12),
        Session(title: "Quiet Orchard", meta: "Chimes", minutes: 14, brass: true, shrink: 20, seed: 13),
        Session(title: "Last Light", meta: "Gong bath", minutes: 50, brass: true, shrink: 2, seed: 14),
    ]

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// Row i's pendulum: angular frequency (rad/s, shorter tubes ring faster) and swing direction.
    static func rowOmega(_ i: Int) -> Double {
        let shrink = sessions[i % sessions.count].shrink
        return 2 * .pi * (1.0 + 1.4 * (shrink / 36) * 0.7 + 0.25 * hash(Double(i) + 7))
    }

    static func rowDir(_ i: Int) -> Double {
        (i % 2 == 0 ? 1 : -1) * (0.8 + 0.4 * hash(Double(i) * 3 + 1))
    }

    /// The scripted scroll offset (pt).
    static func scrollAt(_ t: Double) -> Double {
        if t < flingAt { return 0 }
        if t < backAt {
            let u = seg(t, flingAt, flingEnd)
            return (flingTo * (1 - exp(-4.2 * u))) / (1 - exp(-4.2))
        }
        return flingTo * (1 - easeInOutCubic(seg(t, backAt, backEnd)))
    }

    /// Damped impulse response, normalised to a unit first peak.
    private static func pulse(_ tau: Double, _ w: Double, _ zeta: Double) -> Double {
        if tau <= 0 { return 0 }
        let wd = w * sqrt(1 - zeta * zeta)
        return exp(-zeta * w * tau) * sin(wd * tau)
    }

    // Kicks the script gives every row: (time, strength) — the fling starting and braking, the
    // return starting and stopping.
    private static let kicks: [(Double, Double)] = [(1.5, 0.22), (2.15, -0.13), (3.3, -0.07), (5.2, -0.15), (6.3, 0.07), (7.25, 0.13)]

    /// Row i's swing angle (rad) at loop time t.
    static func swingAt(_ t: Double, _ i: Int) -> Double {
        let w = rowOmega(i)
        let d = rowDir(i)
        var a = 0.0
        for k in kicks { a += k.1 * d * pulse(t - k.0 - Double(i) * rowDelay, w, 0.22) }
        if i == strikeRow { a += 0.32 * pulse(t - strikeAt, w, 0.12) }
        // Tiny idle sway, whole cycles of the loop, so the rest still breathes.
        a += 0.012 * sin((2 * .pi * 2 * t) / WindChimeList.loop + Double(i) * 0.7)
        return a * (1 - easeInOutCubic(seg(t, 8.3, 8.95)))
    }

    /// Seconds since rows i and i + 1 last started touching (−1: not within ringLen), and the side (±1).
    static func ringAt(_ t: Double, _ i: Int) -> (Double, Double) {
        let step = 1.0 / 30
        var touching = false
        var side = 0.0
        var k = 0
        while Double(k) * step <= ringLen + step {
            let at = t - Double(k) * step
            if at < 0 { break }
            let delta = swingAt(at, i) - swingAt(at, i + 1)
            if abs(delta) > touch {
                touching = true
                side = delta > 0 ? 1 : -1
            } else if touching {
                return (Double(k - 1) * step, side)
            }
            k += 1
        }
        return (-1, 0)
    }

    /// The strike row's waveform level τ seconds after a strike (0…1).
    static func playEnvelope(_ tau: Double) -> Double {
        if tau < 0 || tau > playLen { return 0 }
        return clamp01(tau / 0.06) * pow(1 - tau / playLen, 1.6)
    }

    /// Bar k of a waveform (static height 0…1).
    static func barHeight(_ seed: Double, _ k: Int) -> Double {
        let d = Double(k)
        return 0.25 + 0.75 * abs(sin(d * 0.9 + seed * 1.7) * 0.6 + sin(d * 0.37 + seed) * 0.4)
    }
}
