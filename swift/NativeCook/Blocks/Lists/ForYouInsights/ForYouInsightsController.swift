import Foundation

/// One animated scalar (a Reanimated shared value): held, timed or sprung from where it was.
struct ForYouInsightsTrack {
    enum Kind {
        case hold
        case timing(duration: Double, delay: Double, ease: (Double) -> Double)
        case spring(ForYouInsightsMotion.Spring)
    }

    private var from = 0.0
    private var to = 0.0
    private var start = 0.0
    private var kind = Kind.hold

    init(_ value: Double = 0) { from = value; to = value }

    func value(at t: Double) -> Double {
        switch kind {
        case .hold: return to
        case let .timing(duration, delay, ease):
            return from + (to - from) * ease(ForYouInsightsMotion.clamp01((t - start - delay) / duration))
        case let .spring(s):
            return from + (to - from) * ForYouInsightsMotion.spring(t - start, s)
        }
    }

    func active(at t: Double) -> Bool {
        switch kind {
        case .hold: return false
        case let .timing(duration, delay, _): return t - start < duration + delay + 0.02
        case let .spring(s): return t - start < s.settle + 0.02
        }
    }

    mutating func set(_ v: Double) { from = v; to = v; kind = .hold }

    mutating func animate(to target: Double, _ kind: Kind, at t: Double) {
        let current = value(at: t)
        from = current
        to = target
        start = t
        self.kind = kind
    }
}

struct ForYouInsightsTipFrame: Equatable {
    var ghost = 1.0, stream = 1.0, caret = 0.0, shimmer = -1.0
}

/// What one frame draws: the loop's `pose` in autoPlay, the touch-driven tracks otherwise.
struct ForYouInsightsFrame {
    var tip = ForYouInsightsTipFrame()
    var press = 0.0, g = 0.0, driftTime = 0.0, drift = 0.0
    var touchX = 0.0, touchY = 0.0, dragY = 0.0
    var actorIndex = 0
    var lens = 0.0
    var readerLive = false
    var scrollToken = 0
}

/// The one clock of the component (the RN `useFrameCallback`), on the main thread.
final class ForYouInsightsController {
    typealias M = ForYouInsightsMotion

    var autoPlay = false
    var quiet = false
    var onInsights = true
    var scrollY = 0.0
    private(set) var rowBase = ForYouInsightsTheme.Layout.rowTop
    private(set) var actorIndex = 0
    private(set) var scrollToken = 0
    private(set) var readerLive = false

    private var auto = false
    private var origin = -1.0
    private var lastTouch = 0.0
    private var touching = false
    private var closing = false
    private var scrolledBack = false
    private var streamStart = -100.0
    private var driftStart = 0.0
    private var press = ForYouInsightsTrack()
    private var gTrack = ForYouInsightsTrack()
    private var drift = ForYouInsightsTrack()
    private var touchX = ForYouInsightsTrack()
    private var touchY = ForYouInsightsTrack()
    private var dragY = ForYouInsightsTrack()
    private var lens = ForYouInsightsTrack()
    private var now = 0.0
    private var lastFrame = ForYouInsightsFrame()

    var isAuto: Bool { auto }
    var isTouching: Bool { touching }
    var currentG: Double { lastFrame.g }

    func configure(autoPlay: Bool, quiet: Bool, at t: Double) {
        self.quiet = quiet
        let on = autoPlay && !quiet
        let changed = self.autoPlay != on
        self.autoPlay = on
        if !on { auto = false }
        else if changed { auto = true; origin = t }
    }

    func setLens(_ index: Int, animated: Bool, at t: Double) {
        if animated { lens.animate(to: Double(index), .spring(M.Springs.lens), at: t) } else { lens.set(Double(index)) }
    }

    /// True while something moves or the resume timer needs frames.
    func wantsFrames(at t: Double) -> Bool {
        if auto || touching { return true }
        if autoPlay && onInsights { return true }
        let tracks = [press, gTrack, drift, touchX, touchY, dragY, lens]
        if tracks.contains(where: { $0.active(at: t) }) { return true }
        if M.tipPose(t - streamStart).active { return true }
        return false
    }

    func tick(at t: Double) -> ForYouInsightsFrame {
        now = t
        var f = ForYouInsightsFrame()
        f.actorIndex = actorIndex
        f.touchX = touchX.value(at: t)
        f.touchY = touchY.value(at: t)
        f.dragY = dragY.value(at: t)
        f.lens = lens.value(at: t)
        f.scrollToken = scrollToken
        if auto {
            if origin < 0 { origin = t }
            let p = M.pose(t - origin)
            f.tip = ForYouInsightsTipFrame(ghost: p.tip.ghost, stream: p.tip.stream, caret: p.tip.caret, shimmer: p.tip.shimmer)
            f.press = p.open.press; f.g = p.open.g; f.driftTime = p.open.driftTime; f.drift = p.open.drift
            f.readerLive = false
            readerLive = false
            lastFrame = f
            return f
        }
        let tp = M.tipPose(t - streamStart)
        f.tip = ForYouInsightsTipFrame(ghost: tp.ghost, stream: tp.stream, caret: tp.caret, shimmer: tp.shimmer)
        f.press = press.value(at: t)
        f.g = gTrack.value(at: t)
        f.driftTime = t - driftStart
        f.drift = drift.value(at: t)
        f.readerLive = f.g > 0.98
        readerLive = f.readerLive
        lastFrame = f
        resume(t, tp)
        f.actorIndex = actorIndex
        return f
    }

    private func resume(_ t: Double, _ tp: M.TipPose) {
        guard autoPlay, !touching, onInsights else { return }
        if t - lastTouch < M.resumeAfter { return }
        if gTrack.value(at: t) > 0.001 {
            if !closing {
                closing = true
                drift.animate(to: 0, .timing(duration: 0.26, delay: 0, ease: M.inOutQuad), at: t)
                touchX.animate(to: 0, .spring(M.Springs.tiltBack), at: t)
                touchY.animate(to: 0, .spring(M.Springs.tiltBack), at: t)
                dragY.animate(to: 0, .spring(M.Springs.dragBack), at: t)
                gTrack.animate(to: 0, .spring(M.Springs.touchClose), at: t)
            }
            return
        }
        if scrollY > 2 {
            if !scrolledBack { scrolledBack = true; scrollToken += 1 }
            return
        }
        if tp.active || press.value(at: t) > 0.001 { return }
        closing = false
        scrolledBack = false
        actorIndex = 0
        rowBase = ForYouInsightsTheme.Layout.rowTop
        origin = t
        auto = true
    }

    // MARK: touch

    func touchBegan(at t: Double) {
        touching = true
        takeOver(at: t)
    }

    func touchEnded(at t: Double) {
        touching = false
        lastTouch = t
    }

    /// A touch takes over from the loop at exactly the frame it shows.
    func takeOver(at t: Double) {
        lastTouch = t
        closing = false
        guard auto else { return }
        auto = false
        let loopT = ((t - origin).truncatingRemainder(dividingBy: M.loop) + M.loop).truncatingRemainder(dividingBy: M.loop)
        let p = M.pose(loopT)
        streamStart = p.tip.active ? t - (loopT - M.T.dimStart) : -100
        press.set(p.open.press)
        press.animate(to: 0, .timing(duration: 0.2, delay: 0, ease: M.inOutQuad), at: t)
        driftStart = t - p.open.driftTime
        gTrack.set(p.open.g)
        if p.open.g > 0.001 {
            gTrack.animate(to: p.open.opening ? 1 : 0, .spring(p.open.opening ? M.Springs.touchOpen : M.Springs.touchClose), at: t)
            drift.set(p.open.drift)
            if p.open.opening { drift.animate(to: 1, .timing(duration: 0.5, delay: 0, ease: M.inOutQuad), at: t) }
        } else {
            drift.set(p.open.drift)
        }
    }

    func pressRow(_ index: Int, down: Bool, at t: Double) {
        if down {
            takeOver(at: t)
            if gTrack.value(at: t) > 0.001 { return }
            actorIndex = index
            press.animate(to: 1, .timing(duration: M.pressInMs, delay: 0, ease: M.easeOut), at: t)
        } else {
            press.animate(to: 0, .timing(duration: 0.2, delay: 0, ease: M.easeOut), at: t)
        }
    }

    /// Returns true when the article opens (so the caller plays the haptic).
    func open(_ index: Int, base: Double, at t: Double) -> Bool {
        takeOver(at: t)
        if gTrack.value(at: t) > 0.001 { return false }
        actorIndex = index
        rowBase = base
        takeOver(at: t)
        if quiet {
            gTrack.animate(to: 1, .timing(duration: 0.2, delay: 0, ease: M.inOutQuad), at: t)
        } else {
            driftStart = t + 0.4
            drift.set(0)
            drift.animate(to: 1, .timing(duration: 0.55, delay: 0.4, ease: M.easeInOut), at: t)
            gTrack.animate(to: 1, .spring(M.Springs.touchOpen), at: t)
        }
        return true
    }

    func close(at t: Double) {
        takeOver(at: t)
        drift.animate(to: 0, .timing(duration: 0.26, delay: 0, ease: M.inOutQuad), at: t)
        touchX.animate(to: 0, .spring(M.Springs.tiltBack), at: t)
        touchY.animate(to: 0, .spring(M.Springs.tiltBack), at: t)
        dragY.animate(to: 0, .spring(M.Springs.dragBack), at: t)
        if quiet { gTrack.animate(to: 0, .timing(duration: 0.2, delay: 0, ease: M.inOutQuad), at: t) }
        else { gTrack.animate(to: 0, .spring(M.Springs.touchClose), at: t) }
    }

    func regenerate(at t: Double) {
        if quiet { return }
        takeOver(at: t)
        if !M.tipPose(t - streamStart).active { streamStart = t }
    }

    func selectSection(insights: Bool, at t: Double) {
        takeOver(at: t)
        onInsights = insights
    }

    // MARK: hero drag

    func dragChanged(translation: CGPoint, scale k: Double) {
        touchX.set(translation.x)
        touchY.set(translation.y)
        dragY.set(M.rubber(translation.y / k) * k)
    }

    /// Returns true when the drag dismissed the reader.
    func dragEnded(translationY: Double, velocityY: Double, scale k: Double, at t: Double) -> Bool {
        let dismiss = translationY > M.Dismiss.distance * k || velocityY > M.Dismiss.velocity
        if dismiss {
            close(at: t)
            return true
        }
        touchX.animate(to: 0, .spring(M.Springs.tiltBack), at: t)
        touchY.animate(to: 0, .spring(M.Springs.tiltBack), at: t)
        dragY.animate(to: 0, .spring(M.Springs.dragBack), at: t)
        return false
    }
}
