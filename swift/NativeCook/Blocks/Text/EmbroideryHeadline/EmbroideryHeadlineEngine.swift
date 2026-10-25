import UIKit

/// The loop clock and the user's sewing, stepped once a frame from the timeline. Not observed:
/// the timeline's date drives every redraw.
final class EmbroideryHeadlineEngine {
    typealias M = EmbroideryHeadlineMotion
    enum Mode { case auto, user, back, still }

    static let perPt = 1.6
    static let knotPop = 0.4

    var autoEnabled = false
    var reduced = false
    private(set) var satin: EmbroideryHeadlineSatin?
    private(set) var geo: EmbroideryHeadlineGeo?
    /// Where the needle rests between passes, and the hole its thread runs to (under the knot).
    private(set) var park: (Double, Double, Double, Double) = (0, 0, 0, 0)

    private(set) var now = 0.0
    private(set) var loopT = 0.0
    private(set) var mode: Mode = .still
    private var modeAt = 0.0
    private var last: Date?
    private(set) var lastTouch = -100.0
    private(set) var touching = false
    private var userP: [Double] = []
    private var returnFrom: [Double] = []
    private var knotAt = -100.0
    private(set) var lean = 0.0
    private var needle: (Double, Double, Double, Double, Double) = (0, 0, 0, 0, 0)

    func setMode(auto: Bool, reduced: Bool) {
        autoEnabled = auto
        self.reduced = reduced
        if mode != .user { mode = auto ? .auto : .still }
    }

    func configure(_ g: EmbroideryHeadlineGeo, _ s: EmbroideryHeadlineSatin) {
        geo = g
        satin = s
        park = (g.cx + g.fabricR * 0.5, g.cy + g.fabricR * 0.52, s.knot.0 - 2, s.knot.1 + s.knot.2 + 2)
        if mode == .user || mode == .back { mode = autoEnabled ? .auto : .still }
        loopT = 0
    }

    private static func sum(_ a: [Double]) -> Double { a.reduce(0, +) }

    /// Adds d threads in stitching order (d > 0) or takes them out from the end (d < 0).
    private static func sew(_ p: [Double], _ counts: [Double], _ d: Double) -> [Double] {
        var out = p
        var left = d
        if left > 0 {
            for i in out.indices where left > 0 {
                let take = min(counts[i] - out[i], left)
                out[i] += take
                left -= take
            }
        } else {
            for i in out.indices.reversed() where left < 0 {
                let take = min(out[i], -left)
                out[i] -= take
                left += take
            }
        }
        return out
    }

    /// The letter the needle is working: the first unfinished one, or the last when all are done.
    private static func activeGlyph(_ p: [Double], _ counts: [Double]) -> Int {
        for i in p.indices where p[i] < counts[i] - 0.001 { return i }
        return max(0, p.count - 1)
    }

    private static func finished(_ p: [Double], _ counts: [Double]) -> Int {
        p.indices.filter { p[$0] >= counts[$0] - 0.001 }.count
    }

    /// The needle's spot in the loop: (x, y, fromX, fromY, out 0..1).
    private func loopNeedle(_ t: Double) -> (Double, Double, Double, Double, Double) {
        guard let s = satin, !s.glyphs.isEmpty else { return (0, 0, 0, 0, 0) }
        let p = M.glyphPresent(t, s.counts)
        // Which letter is being worked: while unpicking, the one being unpicked; then the front.
        var gi = 0
        if t < 1.75 {
            for i in p.indices where p[i] < s.counts[i] - 0.001 { gi = i }
            for i in p.indices where p[i] > 0.001 && p[i] < s.counts[i] - 0.001 {
                gi = i
                break
            }
        } else {
            gi = Self.activeGlyph(p, s.counts)
        }
        let f = EmbroideryHeadlineArt.front(s.glyphs[gi], min(s.counts[gi] - 0.001, p[gi]))
        let out = M.needleOut(t)
        return (park.0 + (f.0 - park.0) * out, park.1 + (f.1 - park.1) * out, out > 0.5 ? f.2 : park.2, out > 0.5 ? f.3 : park.3, out)
    }

    func step(_ date: Date) {
        let dt = min(0.05, max(0, date.timeIntervalSince(last ?? date)))
        last = date
        guard let s = satin else { return }
        let t = now + dt
        now = t
        if mode == .auto { loopT = (loopT + dt).truncatingRemainder(dividingBy: EmbroideryHeadline.loop) }
        if mode == .user && autoEnabled && !touching && t - lastTouch > 2 {
            returnFrom = userP
            mode = .back
            modeAt = t
        }
        if mode == .back {
            let missing = 1 - Self.sum(returnFrom) / max(1, Self.sum(s.counts))
            let length = 0.3 + 0.6 * missing
            let k = M.easeInOut(M.seg(t, modeAt, modeAt + length))
            userP = returnFrom.indices.map { returnFrom[$0] + (s.counts[$0] - returnFrom[$0]) * k }
            if k >= 1 {
                // The knot pops last (unless it is already there), then the loop takes over at t = 0.
                if knotAt == -100 {
                    knotAt = t
                } else if knotAt == -50 || t - knotAt > Self.knotPop {
                    mode = .auto
                    loopT = 0
                }
            }
        }
        if mode == .user || mode == .back {
            // The needle chases the stitching front while sewing and goes back to rest when idle.
            let gi = Self.activeGlyph(userP, s.counts)
            let f = EmbroideryHeadlineArt.front(s.glyphs[gi], min(s.counts[gi] - 0.001, userP[gi]))
            let target = touching ? f : park
            let k = 1 - exp(-dt * (touching ? 22 : 7))
            let out = needle.4 + ((touching ? 1 : 0) - needle.4) * (1 - exp(-dt * 8))
            needle = (needle.0 + (target.0 - needle.0) * k, needle.1 + (target.1 - needle.1) * k, touching ? f.2 : park.2, touching ? f.3 : park.3, out)
            lean *= exp(-dt * 6)
        }
    }

    var wantsFrames: Bool {
        switch mode {
        case .auto, .back: return true
        case .still: return false
        case .user: return autoEnabled || touching || now - lastTouch < 1.5
        }
    }

    /// What is on the cloth: per letter threads laid, knot (scale, alpha), subheading (alpha, rise), needle.
    struct State {
        var p: [Double]
        var knot: (Double, Double)
        var sub: (Double, Double)
        var needle: (Double, Double, Double, Double, Double)
    }

    var state: State {
        guard let s = satin else { return State(p: [], knot: (0, 0), sub: (1, 0), needle: (0, 0, 0, 0, 0)) }
        switch mode {
        case .auto:
            let t = loopT
            return State(p: M.glyphPresent(t, s.counts), knot: M.knotState(t), sub: M.subheading(t), needle: loopNeedle(t))
        case .still:
            return State(p: s.counts, knot: (1, 1), sub: (1, 0), needle: (park.0, park.1, park.2, park.3, 0))
        case .user, .back:
            let total = Self.sum(s.counts)
            let knot: (Double, Double) = knotAt == -50 ? (1, 1) : knotAt < 0 ? (0.55, 0) : (M.pop(M.seg(now, knotAt, knotAt + Self.knotPop)), min(1, (now - knotAt) * 10))
            let a = min(1, max(0, (Self.sum(userP) - total + 40) / 40))
            return State(p: userP, knot: knot, sub: (a, (1 - a) * 10), needle: needle)
        }
    }

    /// The thread colour on the needle: the letter being worked, or the knot's when it rests.
    func needleRGB(_ st: State) -> EmbroideryHeadlineRGB {
        guard let s = satin, !s.glyphs.isEmpty else { return EmbroideryHeadlineArt.indigo }
        return st.needle.4 > 0.5 ? s.glyphs[Self.activeGlyph(st.p, s.counts)].rgb : s.knotRGB
    }

    var bob: Double {
        mode == .auto && !reduced ? 0.22 * state.needle.4 * (1 + sin(loopT / EmbroideryHeadline.loop * 2 * .pi * 54)) : 0
    }

    // MARK: Touch

    func touchDown() {
        guard let s = satin else { return }
        touching = true
        lastTouch = now
        if mode == .auto || mode == .still {
            userP = mode == .auto ? M.glyphPresent(loopT, s.counts) : s.counts
            needle = mode == .auto ? loopNeedle(loopT) : (park.0, park.1, park.2, park.3, 0)
            let ks = mode == .auto ? M.knotState(loopT) : (1, 1)
            knotAt = ks.1 >= 0.99 ? -50 : -100
        }
        mode = .user
    }

    /// Sews by the finger's sideways move; returns 1 for a finished letter, 2 for the knot, else 0.
    func drag(by dx: Double) -> Int {
        guard let s = satin, mode == .user else { return 0 }
        let before = userP
        let after = Self.sew(before, s.counts, dx * Self.perPt)
        userP = after
        lean = max(-0.35, min(0.35, lean + dx * 0.004))
        lastTouch = now
        let total = Self.sum(s.counts)
        var haptic = Self.finished(after, s.counts) != Self.finished(before, s.counts) ? 1 : 0
        if Self.sum(after) >= total - 0.001 && Self.sum(before) < total - 0.001 {
            knotAt = now
            haptic = 2
        }
        if Self.sum(after) < total - 0.001 { knotAt = -100 }
        return haptic
    }

    func touchUp() {
        touching = false
        lastTouch = now
    }
}
