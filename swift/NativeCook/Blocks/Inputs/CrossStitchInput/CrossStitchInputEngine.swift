import Foundation

/// The loop clock, the user's name and the needle, stepped once a frame from the timeline. Not
/// observed: the timeline's date drives every redraw.
final class CrossStitchInputEngine {
    typealias M = CrossStitchInputMotion
    enum Mode { case auto, user, back, still }

    static let returnFor = 0.8

    let restL = CrossStitchInputFont.layout(M.restText)
    let secondL = CrossStitchInputFont.layout(M.secondText)
    let borderCount = Double(CrossStitchInputFont.border().cx.count)

    var geo: CrossStitchInputGeo?
    var autoEnabled = false
    var reduced = false
    var artReady = false
    private(set) var now = 0.0
    private(set) var loopT = 0.0
    private(set) var mode: Mode = .still
    private var modeAt = 0.0
    private var last: Date?
    private(set) var lastTouch = -100.0
    var focused = false
    private var chipFrom = Double(M.restThread)
    private var chipTo = Double(M.restThread)
    private var chipAt = -100.0
    private(set) var needle: (Double, Double, Double, Double, Double) = (0, 0, 0, 0, 0)
    private(set) var user: CrossStitchInputUserName
    private var lastScript = -1
    private var lastChip = -1

    // Threshold events for the view.
    var pendingText: String?
    var pendingThread: Int?

    init() {
        user = CrossStitchInputUserName(
            layout: restL, typedAt: restL.count.map { _ in -100 }, fromX: restL.count.map { _ in 0 },
            fromY: restL.count.map { _ in 0 }, movedAt: -100, thread: M.restThread,
            threadFrom: CrossStitchInputArt.threads[M.restThread].rgb, threadAt: -100
        )
    }

    func setMode(auto: Bool, reduced: Bool) {
        autoEnabled = auto
        self.reduced = reduced
        if mode != .user { mode = auto ? .auto : .still }
    }

    func step(_ date: Date) {
        let dt = min(0.05, max(0, date.timeIntervalSince(last ?? date)))
        last = date
        guard artReady, let g = geo else { return }
        let t = now + dt
        now = t
        if mode == .auto { loopT = (loopT + dt).truncatingRemainder(dividingBy: CrossStitchInput.loop) }
        if mode == .user && autoEnabled && !focused && t - lastTouch > 2 {
            mode = .back
            modeAt = t
            pendingText = M.restText
            pendingThread = M.restThread
        }
        if mode == .back && t - modeAt >= Self.returnFor {
            mode = .auto
            loopT = 0
            lastScript = -1
            lastChip = -1
        }
        // The needle under typing chases its target on a critically damped follow.
        if mode == .user || mode == .back {
            let target = user.needleTarget(g, t, user.shift(t, axis: 0), user.shift(t, axis: 1))
            let k = 1 - exp(-dt * (target.4 > 0.5 ? 26 : 9))
            needle = (needle.0 + (target.0 - needle.0) * k, needle.1 + (target.1 - needle.1) * k, target.2, target.3, target.4)
        }
        if mode == .auto {
            let i = M.scriptIndex(loopT)
            if i != lastScript {
                lastScript = i
                pendingText = M.textScript[i].1
            }
            let c = Int(M.chipPosition(loopT).rounded())
            if c != lastChip {
                lastChip = c
                pendingThread = c
            }
        }
    }

    var wantsFrames: Bool {
        switch mode {
        case .auto, .back: return true
        case .still: return false
        case .user:
            if autoEnabled { return true }
            if now - lastTouch < 1 { return true }
            // Still stitching a letter?
            let p = user.glyphPresent(now)
            for (i, c) in user.layout.count.enumerated() where p[i] < Double(c) { return true }
            return abs(needle.0 - (geo?.parkX ?? needle.0)) > 0.3 || abs(needle.1 - (geo?.parkY ?? needle.1)) > 0.3
        }
    }

    var returnK: Double { mode == .back ? M.easeInOut(M.seg(now, modeAt, modeAt + Self.returnFor)) : 0 }

    private func chipSpring(_ t: Double) -> Double {
        if chipAt < -50 { return chipTo }
        let zeta = 0.75, w0 = 15.3
        let wd = w0 * sqrt(1 - zeta * zeta)
        let u = max(0, t - chipAt)
        return chipTo + (chipFrom - chipTo) * exp(-zeta * w0 * u) * (cos(wd * u) + zeta * w0 / wd * sin(wd * u))
    }

    var chipPos: Double {
        switch mode {
        case .auto: return M.chipPosition(loopT)
        case .still, .user: return chipSpring(now)
        case .back:
            let c = chipSpring(now)
            return c + (Double(M.restThread) - c) * returnK
        }
    }

    /// The stitch counter: (done, total).
    var counts: (Double, Double) {
        let B = borderCount
        let rn = Double(restL.total), sn = Double(secondL.total)
        switch mode {
        case .auto:
            let t = loopT
            return (B + M.restPresent(t, rn) + M.secondPresent(t, sn), B + rn + (sn - rn) * M.totalBlend(t))
        case .still:
            return (B + rn, B + rn)
        case .user, .back:
            let done = B + user.worked(now)
            let total = B + Double(user.layout.total)
            let k = returnK
            return (done + (B + rn - done) * k, total + (B + rn - total) * k)
        }
    }

    /// Hands the screen to the user: the name on the cloth becomes theirs, mid-stitch if it was.
    func takeOver(shown: String, thread current: Int) {
        let t = now
        lastTouch = t
        if mode == .user { return }
        let L = CrossStitchInputFont.layout(shown)
        let which = shown == M.secondText ? 1 : shown == M.restText ? 0 : -1
        let lt = loopT
        let total = Double(L.total)
        let present = mode == .auto ? (which == 1 ? M.secondPresent(lt, total) : which == 0 ? M.restPresent(lt, total) : total) : total
        var typedAt: [Double] = []
        for i in L.count.indices {
            let frac = L.count[i] == 0 ? 1 : min(1, max(0, (present - Double(L.first[i])) / Double(L.count[i])))
            typedAt.append(t - frac * CrossStitchInputUserName.stitchDuration(L.count[i]) - (frac >= 1 ? 1 : 0))
        }
        user = CrossStitchInputUserName(
            layout: L, typedAt: typedAt, fromX: L.count.map { _ in 0 }, fromY: L.count.map { _ in 0 },
            movedAt: -100, thread: current, threadFrom: CrossStitchInputArt.threads[current].rgb, threadAt: -100
        )
        chipFrom = Double(current)
        chipTo = Double(current)
        chipAt = -100
        if let g = geo {
            if mode == .auto {
                let n = CrossStitchInputArt.autoNeedle(lt, g, restL, secondL)
                needle = (n.0, n.1, n.4, n.5, 0)
            } else if mode != .back {
                needle = (g.parkX, g.parkY, g.parkX - 30, g.parkY, 0)
            }
        }
        mode = .user
    }

    func type(_ next: String, prev prevText: String) {
        let t = now
        let prev = user
        let L = CrossStitchInputFont.layout(next)
        let k = 1 - M.easeInOut(M.seg(t, prev.movedAt, prev.movedAt + CrossStitchInputUserName.glide))
        let prevChars = Array(prevText)
        let chars = Array(next)
        var typedAt: [Double] = []
        var fromX: [Double] = []
        var fromY: [Double] = []
        for i in L.count.indices {
            let same = i < prev.layout.count.count && i < prevChars.count && i < chars.count && prevChars[i] == chars[i]
            typedAt.append(same ? prev.typedAt[i] : t)
            fromX.append(same ? prev.layout.col[i] + prev.fromX[i] * k - L.col[i] : 0)
            fromY.append(same ? prev.layout.row[i] + prev.fromY[i] * k - L.row[i] : 0)
        }
        var u = prev
        u.layout = L
        u.typedAt = typedAt
        u.fromX = fromX
        u.fromY = fromY
        u.movedAt = t
        user = u
        lastTouch = t
    }

    func pickThread(_ i: Int) {
        let t = now
        user.threadFrom = user.colour(t)
        user.thread = i
        user.threadAt = t
        chipFrom = chipSpring(t)
        chipTo = Double(i)
        chipAt = t
        lastTouch = t
    }

    func focusChanged(_ on: Bool) {
        focused = on
        lastTouch = now
    }
}
