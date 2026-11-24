import Foundation
import Observation
import UIKit

/// Rotating Word Headline — the timeline (port of `rotatingWordMotion.ts`). Every frame comes from one number, the
/// word position `pos` (0 = first word, 1 = second, …), which only moves during four short transitions. The letters,
/// the width of the word, the underline, the plan card and the ghost tap all read it.
enum RotatingWordHeadlineMotion {
    static let loop: Double = 8
    static let words = 4
    /// When each word change starts (s); each lasts `transition`. The last ends exactly at `loop`.
    static let starts: [Double] = [1.3, 3.3, 5.3, 7.3]
    static let transition: Double = 0.7
    /// Delay between neighbouring letters, as a fraction of one transition.
    static let stagger: Double = 0.07
    static let maxLetters: Double = 7
    /// How far a letter travels on its wheel (× font size) and how far it turns.
    static let rollRise: Double = 0.62
    static let rollDeg: Double = 72
    static let widthLag: Double = 0.08
    static let lineLag: Double = 0.16
    static let ghostIn: Double = -0.42
    static let ghostPress: Double = -0.12
    static let ghostOut: Double = 0.34
    /// Touch: idle before easing back to rest, and the length of that return.
    static let idle: Double = 2
    static let returnLen: Double = 0.9
    /// A drag of this many font sizes moves one word.
    static let dragPerWord: Double = 1.15

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func mix(_ a: Double, _ b: Double, _ u: Double) -> Double { a + (b - a) * u }
    static func wrapIndex(_ i: Int, _ n: Int) -> Int { ((i % n) + n) % n }

    /// Word position at loop time t.
    static func posAt(_ t: Double) -> Double {
        starts.reduce(0) { $0 + seg(t, $1, $1 + transition) }
    }

    /// Signed distance from word k to the position, wrapped into (−words/2, words/2].
    static func wordDistance(_ pos: Double, _ k: Int) -> Double {
        let n = Double(words)
        var d = ((pos - Double(k)).truncatingRemainder(dividingBy: n) + n).truncatingRemainder(dividingBy: n)
        if d > n / 2 { d -= n }
        return d
    }

    static func letterProgress(_ u: Double, _ i: Int) -> Double {
        let span = 1 - stagger * (maxLetters - 1)
        return easeInOutCubic(clamp01((u - Double(i) * stagger) / span))
    }

    /// −1 rolled out above, 0 in place, +1 waiting below.
    static func letterWheel(_ pos: Double, _ k: Int, _ i: Int) -> Double {
        let d = wordDistance(pos, k)
        if d <= -1 || d >= 1 { return 1 }
        if d >= 0 { return -letterProgress(d, i) }
        return 1 - letterProgress(d + 1, i)
    }

    static func morphAmount(_ pos: Double, _ lag: Double) -> Double {
        let u = pos - floor(pos)
        return easeInOutCubic(clamp01((u - lag) / (1 - lag)))
    }

    /// Ghost finger over the word: (opacity, scale).
    static func ghostAt(_ t: Double) -> (Double, Double) {
        var o = 0.0
        var s = 1.0
        for a in starts {
            let show = easeOutCubic(seg(t, a + ghostIn, a + ghostIn + 0.2))
                * (1 - easeInOutCubic(seg(t, a + ghostOut - 0.2, a + ghostOut)))
            if show > o {
                o = show
                let press = seg(t, a + ghostPress, a) * (1 - seg(t, a, a + 0.18))
                s = 1 - 0.18 * easeInOutCubic(press)
            }
        }
        return (o, s)
    }
}

/// One frame of the headline: the word position, plus the ghost while the loop plays.
struct RotatingWordHeadlineFrame {
    var pos: Double
    var ghostOpacity: Double
    var ghostScale: Double
}

/// Drives the word position as a pure function of time. It is written only at touch events (never per frame), so
/// any number of views can read the same position from their own `TimelineView`: the headline and the plan card
/// below it stay in step.
@Observable
final class RotatingWordHeadlineDriver {
    typealias M = RotatingWordHeadlineMotion

    /// Spring of a tap and a release (damping 20, stiffness 190, mass 1).
    private static let damping = 20.0
    private static let stiffness = 190.0

    var autoPlay: Bool
    var reducedMotion: Bool

    /// Start of the autoplay clock (t = 0).
    private(set) var autoStart: Date
    /// Last touch event; nil while the loop has never been touched.
    private(set) var lastTouch: Date?
    private(set) var dragging = false
    private(set) var dragPos = 0.0
    private var dragFrom = 0.0
    /// The settle after a tap or a release.
    private var settleFrom = 0.0
    private var settleTo = 0.0
    private var settleV = 0.0
    private var settleAt = Date.distantPast

    @ObservationIgnored private var lastWord = 0
    @ObservationIgnored private let haptic = UIImpactFeedbackGenerator(style: .light)

    init(autoPlay: Bool = true, reducedMotion: Bool = false, start: Date = .now) {
        self.autoPlay = autoPlay
        self.reducedMotion = reducedMotion
        self.autoStart = start
    }

    private var auto: Bool { autoPlay && !reducedMotion }

    /// Whether anything can still move without a new touch (a paused timeline otherwise).
    func isMoving(at now: Date) -> Bool {
        if auto { return true }
        if dragging { return false }
        return now.timeIntervalSince(settleAt) < 2
    }

    func frame(at now: Date) -> RotatingWordHeadlineFrame {
        guard let last = lastTouch else {
            guard auto else { return .init(pos: 0, ghostOpacity: 0, ghostScale: 1) }
            return autoFrame(now.timeIntervalSince(autoStart))
        }
        if dragging { return .init(pos: dragPos, ghostOpacity: 0, ghostScale: 1) }
        let since = now.timeIntervalSince(last)
        if !auto || since <= M.idle { return .init(pos: settled(at: now), ghostOpacity: 0, ghostScale: 1) }
        // 2 s after the last touch: ease back to the nearest whole cycle (the first word), then the loop restarts.
        let returnAt = last.addingTimeInterval(M.idle)
        let from = settled(at: returnAt)
        let to = (from / Double(M.words)).rounded() * Double(M.words)
        let u = (since - M.idle) / M.returnLen
        if u < 1 { return .init(pos: M.mix(from, to, M.easeInOutCubic(u)), ghostOpacity: 0, ghostScale: 1) }
        return autoFrame(since - M.idle - M.returnLen)
    }

    private func autoFrame(_ elapsed: Double) -> RotatingWordHeadlineFrame {
        let t = elapsed.truncatingRemainder(dividingBy: M.loop)
        let g = M.ghostAt(t)
        return .init(pos: M.posAt(t), ghostOpacity: g.0, ghostScale: g.1)
    }

    /// Position of the settle (spring, or a short tween under Reduce Motion) at `now`.
    private func settled(at now: Date) -> Double {
        let t = max(0, now.timeIntervalSince(settleAt))
        let x0 = settleFrom - settleTo
        if reducedMotion {
            return settleTo + x0 * (1 - M.easeInOutCubic(min(1, t / 0.28)))
        }
        // Underdamped spring, solved in closed form.
        let w0 = Self.stiffness.squareRoot()
        let zeta = Self.damping / (2 * w0)
        let wd = w0 * (1 - zeta * zeta).squareRoot()
        let e = exp(-zeta * w0 * t)
        let x = e * (x0 * cos(wd * t) + (settleV + zeta * w0 * x0) / wd * sin(wd * t))
        return settleTo + x
    }

    // MARK: touch

    private func takeOver(_ now: Date) -> Double {
        let p = frame(at: now).pos
        lastTouch = now
        lastWord = Int(p.rounded())
        return p
    }

    func tap(fontSize: Double) {
        let now = Date.now
        let p = takeOver(now)
        settleFrom = p
        settleTo = p.rounded() + 1
        settleV = 0
        settleAt = now
        lastWord = Int(settleTo)
        haptic.impactOccurred()
    }

    func dragBegan() {
        let now = Date.now
        dragFrom = takeOver(now)
        dragPos = dragFrom
        dragging = true
    }

    func dragChanged(translationY: Double, fontSize: Double) {
        lastTouch = .now
        dragPos = dragFrom - translationY / (fontSize * M.dragPerWord)
        let w = Int(dragPos.rounded())
        if w != lastWord {
            lastWord = w
            haptic.impactOccurred()
        }
    }

    func dragEnded(velocityY: Double, fontSize: Double) {
        let now = Date.now
        let v = -velocityY / (fontSize * M.dragPerWord)
        settleFrom = dragPos
        settleTo = (dragPos + v * 0.12).rounded()
        settleV = reducedMotion ? 0 : v
        settleAt = now
        lastTouch = now
        dragging = false
        if Int(settleTo) != lastWord {
            lastWord = Int(settleTo)
            haptic.impactOccurred()
        }
    }
}
