import SwiftUI

// MARK: - The knot

/// The Loaven knot: two long loops (stadiums) crossed at ±tilt, woven over and under at their four
/// crossings like a braided loaf. Everything is in mark units around the knot's centre.
enum FoilMonogramSplashKnot {
    static let loopWidth: CGFloat = 46
    static let loopHeight: CGFloat = 136
    static let tilt = 30.0 // degrees each loop leans from upright
    static let stroke: CGFloat = 8 // foil band width
    static let gap: CGFloat = 2.6 // clear space on each side of a strand where it passes over the other
    static let overSpan = 0.06 // share of a loop's length redrawn on top around each crossing
    static let coreOverlap = 0.012 // share of a loop the gold core of a woven-over patch runs past its cut

    private static let r = loopWidth / 2
    private static let half = loopHeight / 2 - r
    private static let radians = tilt * .pi / 180

    /// The knot's size in mark units, stroke included.
    static let width = 2 * (half * sin(radians) + r + stroke / 2)
    static let height = 2 * (half * cos(radians) + r + stroke / 2)

    /// Where the loops cross, as a share of the length of the loop that passes over there (each
    /// loop starts at its top tip and runs clockwise): alternating over and under, a true weave.
    static let crossings: [(over: Int, at: Double)] = [(0, 0.2091), (1, 0.6272), (0, 0.7091), (1, 0.1272)]

    /// One loop, upright, then leaned by `sign` × tilt: it starts at its top tip and runs clockwise,
    /// so a trim of [0, u] matches the shares above.
    static func loop(_ sign: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: -half - r))
        p.addRelativeArc(center: CGPoint(x: 0, y: -half), radius: r, startAngle: .degrees(-90), delta: .degrees(90))
        p.addLine(to: CGPoint(x: r, y: half))
        p.addRelativeArc(center: CGPoint(x: 0, y: half), radius: r, startAngle: .degrees(0), delta: .degrees(180))
        p.addLine(to: CGPoint(x: -r, y: -half))
        p.addRelativeArc(center: CGPoint(x: 0, y: -half), radius: r, startAngle: .degrees(180), delta: .degrees(90))
        return p.applying(CGAffineTransform(rotationAngle: sign * radians))
    }

    /// An arched doorway, unit radius: a half circle over a body that runs 1.5 radii below its
    /// centre. Scaled up from the knot's centre, it opens the splash onto the app.
    static func arch(outline: Bool) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: -1, y: 1.5))
        p.addLine(to: CGPoint(x: -1, y: 0))
        p.addRelativeArc(center: .zero, radius: 1, startAngle: .degrees(180), delta: .degrees(180))
        p.addLine(to: CGPoint(x: 1, y: 1.5))
        if !outline { p.closeSubpath() }
        return p
    }
}

// MARK: - The script

/// The script, in seconds. It starts from a bare splash and comes back to it, so a recording of one
/// loop loops cleanly.
enum FoilMonogramSplashScript {
    static let loop = 9.0
    static let draw: [(at: Double, time: Double)] = [(0.3, 1.4), (0.55, 1.4)]
    static let sweepAt = 1.95 // a light sweeps across the foil
    static let sweepTime = 0.95
    static let sweepReach = 190.0 // pt each side of the centre the light travels
    static let lettersAt = 1.5 // the wordmark rises through its baseline, letter by letter
    static let letterStagger = 0.07
    static let letterIn = 0.6
    static let tagAt = 2.25
    static let tagIn = 0.5
    static let handoffAt = 3.9 // the letters drop out upwards, the knot flies to the header, the arch opens
    static let letterOut = 0.36
    static let letterOutStagger = 0.035
    static let moveAt = 3.98
    static let moveTime = 0.9
    static let archAt = 4.05
    static let archTime = 0.9
    static let appAt = 4.4 // the app's content rises in
    static let appStagger = 0.07
    static let appIn = 0.7
    static let appRise = 18.0
    static let appRest = 6.0 // without autoPlay the script holds here, on the app
    static let closeAt = 8.0 // the arch closes over the app and the header knot fades
    static let closeTime = 0.8
    static let knotOut = 0.3
    static let rimFade = 24.0 // pt of doorway radius over which its gold rim fades in
    static let skipSpeed = 3.5 // a tap on the splash plays the rest of it this much faster
    static let resumeAfter = 1.5 // s of rest after a touch before autoPlay moves on

    static let markScale = 1.15
    static let headerMark = 36.0
    static let headerLeft = 20.0

    static let easeOut = (0.23, 1.0, 0.32, 1.0)
    static let easeInOut = (0.65, 0.0, 0.35, 1.0)
    static let transition = (0.77, 0.0, 0.175, 1.0)

    /// Cubic bezier easing, as in CSS: solves x for `t` by Newton steps, then returns y.
    static func bezier(_ c: (Double, Double, Double, Double), _ t: Double) -> Double {
        let (x1, y1, x2, y2) = c
        if t <= 0 { return 0 }
        if t >= 1 { return 1 }
        var u = t
        for _ in 0..<8 {
            let x = 3 * (1 - u) * (1 - u) * u * x1 + 3 * (1 - u) * u * u * x2 + u * u * u - t
            let dx = 3 * (1 - u) * (1 - u) * x1 + 6 * (1 - u) * u * (x2 - x1) + 3 * u * u * (1 - x2)
            if abs(x) < 1e-5 || abs(dx) < 1e-6 { break }
            u = min(1, max(0, u - x / dx))
        }
        return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
    }

    /// How far `t` is through a step that starts `at` and lasts `time`, from 0 to 1.
    static func step(_ t: Double, _ at: Double, _ time: Double) -> Double {
        min(1, max(0, (t - at) / time))
    }

    /// How far a loop of the knot is drawn.
    static func drawn(_ t: Double, _ loop: Int, _ reduced: Bool) -> Double {
        if reduced { return t >= draw[0].at ? 1 : 0 }
        return bezier(easeInOut, step(t, draw[loop].at, draw[loop].time))
    }

    /// The knot's own fade: in under reduced motion, out at the end of the loop.
    static func knotOpacity(_ t: Double, _ reduced: Bool) -> Double {
        let enter = reduced ? step(t, draw[0].at, 0.5) : 1
        let leave = t >= closeAt ? 1 - step(t, closeAt, knotOut) : 1
        let swap = reduced && t >= handoffAt && t < closeAt ? abs(1 - 2 * step(t, handoffAt, 0.8)) : 1
        return enter * leave * swap
    }

    /// How far the knot has flown to the header: 0 on the splash, 1 in the app.
    static func moved(_ t: Double, _ reduced: Bool) -> Double {
        if reduced { return t >= handoffAt + 0.4 ? 1 : 0 }
        return bezier(transition, step(t, moveAt, moveTime))
    }

    /// How far the arch has opened onto the app.
    static func opened(_ t: Double) -> Double {
        if t >= closeAt { return 1 - bezier(transition, step(t, closeAt, closeTime)) }
        return bezier(transition, step(t, archAt, archTime))
    }

    /// Where the scripted light is, -0.25 (off to the left) to 1.25 (off to the right).
    static func sweep(_ t: Double) -> Double {
        -0.25 + 1.5 * bezier(easeInOut, step(t, sweepAt, sweepTime))
    }

    /// A letter's drop below (positive) or above (negative) its baseline, as a share of the reveal.
    static func letterShift(_ t: Double, _ i: Int) -> Double {
        let enter = bezier(easeOut, step(t, lettersAt + Double(i) * letterStagger, letterIn))
        let leave = bezier(transition, step(t, handoffAt + Double(i) * letterOutStagger, letterOut))
        return 1 - enter - leave
    }

    /// An app element's entrance, 0 to 1.
    static func appIn(_ t: Double, _ k: Int) -> Double {
        bezier(easeOut, step(t, appAt + Double(k) * appStagger, appIn))
    }
}

// MARK: - The clock

/// The script's clock, stepped once a frame like the React Native frame callback: a tap on the
/// splash speeds it up, a finger on the splash holds it, and without autoPlay it rests on the app.
final class FoilMonogramSplashClock {
    private typealias S = FoilMonogramSplashScript

    private(set) var t = 0.0
    private var speed = 1.0
    private var pause = 0.0
    private var last: Date?
    var looping = false
    /// A finger on the splash moving the light.
    private(set) var down = false
    private var lightAt = 0.5
    private var follow = Tween(0)

    /// Moves the clock to `date` and returns the script's time.
    func step(to date: Date, looping: Bool) -> Double {
        self.looping = looping
        advance(to: date)
        return t
    }

    private func advance(to date: Date) {
        defer { last = date }
        guard let last else { return }
        let dt = min(max(date.timeIntervalSince(last), 0), 0.05)
        if dt <= 0 || down { return }
        if pause > 0 {
            pause = max(0, pause - dt)
            return
        }
        let before = t
        // Without autoPlay the app stays once it is open; a replay jumps to the close.
        if !looping && before >= S.appRest && before < S.closeAt { return }
        var next = before + dt * speed
        if before < S.handoffAt && next >= S.handoffAt { speed = 1 }
        if !looping && before < S.appRest && next > S.appRest { next = S.appRest }
        if next >= S.loop { next -= S.loop }
        t = next.isFinite ? next : 0
    }

    /// The light's position on the screen's x axis, from the script or the finger.
    func shineX(centre: Double, at date: Date) -> Double {
        let f = follow.value(at: date)
        let at = S.sweep(t) * (1 - f) + min(1.25, max(-0.25, lightAt)) * f
        return centre + (at * 2 - 1) * S.sweepReach
    }

    /// A tap on the splash: plays the rest of it faster (or skips to the hand-off under reduced motion).
    func tap(reduced: Bool) -> Bool {
        guard t < S.handoffAt else { return false }
        if reduced { t = S.handoffAt } else { speed = S.skipSpeed }
        pause = 0
        return true
    }

    func dragChanged(x: Double, centre: Double, at date: Date) {
        if !down {
            guard t < S.handoffAt else { return }
            down = true
            follow.retarget(1, at: date, duration: 0.3, curve: .easeOut)
        }
        lightAt = 0.5 + (x - centre) / (2 * S.sweepReach)
    }

    func dragEnded(at date: Date) {
        guard down else { return }
        down = false
        follow.retarget(0, at: date, duration: 0.7, curve: .easeInOut)
        pause = looping ? S.resumeAfter : 0
    }

    func replay() {
        t = S.closeAt
        speed = 1
        pause = 0
    }

    /// A touch on the open app keeps it open a little longer under autoPlay.
    func holdApp() {
        if looping && t >= S.archAt + S.archTime * 0.7 && t < S.closeAt { pause = max(pause, 3) }
    }
}
