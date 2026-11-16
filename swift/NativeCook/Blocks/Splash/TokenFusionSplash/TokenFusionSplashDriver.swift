import Foundation
import UIKit

@MainActor
final class TokenFusionSplashDriver {
    typealias M = TokenFusionSplashMotion
    private(set) var live = TokenFusionSplashLive(M.sceneAt(0))
    private(set) var current = M.sceneAt(0)
    private(set) var lagOne = M.sceneAt(0)
    private(set) var lagTwo = M.sceneAt(0)
    private(set) var time = 0.0
    private var playing = true, complete = false, active = true, wasConfigured = false
    private var previousFrame: Date?
    private var resumeStart = -1.0, resumeScene = M.sceneAt(0)
    private var lastHaptic = -Double.infinity
    private var fadeFrom = 1.0
    private var fadeAt: Date?, fadeIndex = -1, fadeApplied = false
    var onMerge: ((String, [String]) -> Void)?
    var onRelease: ((String) -> Void)?
    var onComplete: (() -> Void)?

    func configure(auto: Bool) {
        guard !wasConfigured else { return }
        wasConfigured = true; playing = auto
    }
    func reset(auto: Bool) {
        live = TokenFusionSplashLive(M.sceneAt(0)); current = M.sceneAt(0); lagOne = current; lagTwo = current
        playing = auto; time = 0; complete = false; previousFrame = nil; resumeStart = -1; fadeAt = nil; pendingMerge = 0
    }
    func setActive(_ value: Bool) { active = value; previousFrame = nil }
    func frame(at date: Date, auto: Bool, reduced: Bool, idleMs: Double? = 5000) -> (scene: TokenFusionSplashMotion.TokenFusionSplashScene, lag: TokenFusionSplashMotion.TokenFusionSplashScene, opacity: Double) {
        configure(auto: auto)
        let dt = min(max(0, previousFrame.map { date.timeIntervalSince($0) } ?? (16.667 / 1000)), 0.05)
        if previousFrame == date { return (current, lagTwo, opacity(at: date)) }
        previousFrame = date
        guard active else { return (current, lagTwo, opacity(at: date)) }
        // Reduced Motion has no frame subscription except during its explicit 80/160 ms fade.
        if reduced { return (current, lagTwo, opacity(at: date)) }
        if complete { return (current, lagTwo, 1) }
        var next: M.TokenFusionSplashScene
        if playing {
            let t = time + dt
            time = t.truncatingRemainder(dividingBy: M.loop)
            next = M.sceneAt(t)
        } else {
            live.step(dt)
            applyCommand()
            if !live.held, let idle = idleMs, !auto, live.now - live.lastTouch >= idle / 1000 { live.flood = max(live.flood, 0.001) }
            next = live.scene()
            if !live.held && auto && live.now - live.lastTouch > 2 {
                if resumeStart < 0 { resumeStart = live.now; resumeScene = next }
                let p = M.seg(live.now - resumeStart, 0, 0.6)
                next = M.blend(resumeScene, M.sceneAt(0), p)
                if p >= 1 {
                    time = 0; playing = true; live = TokenFusionSplashLive(M.sceneAt(0)); resumeStart = -1
                }
            }
            let finished = live.flood > 0.995 && !live.completed
            if finished { if !auto { complete = true }; live.completed = true }
            dispatch(finished: finished)
        }
        lagTwo = lagOne; lagOne = current; current = next
        return (current, lagTwo, 1)
    }
    func begin(_ pointer: Int, at point: CGPoint, pin: Double?, auto: Bool) -> Bool {
        configure(auto: auto)
        let source = pin.map { M.sceneAt($0) } ?? current
        let m = playing || pin != nil ? TokenFusionSplashLive(source, next: M.sceneAt((pin ?? time) + 1 / 120.0)) : live
        guard m.grab(pointer, point.x, point.y) else { return false }
        playing = false; resumeStart = -1; live = m; current = live.scene()
        // RN emits the pending touch cue on the next frame, not during the touch callback.
        return true
    }
    func move(_ pointer: Int, at point: CGPoint, geometry: TokenFusionSplashFrame.TokenFusionSplashGeometry) {
        live.move(pointer, point.x, point.y, bounds: geometry.bounds); current = live.scene()
    }
    func release(_ pointer: Int, cancel: Bool) { live.release(pointer, cancel: cancel) }
    func merge(_ i: Int, pin: Double?, auto: Bool) {
        configure(auto: auto)
        if playing || pin != nil { live = TokenFusionSplashLive(pin.map { M.sceneAt($0) } ?? current, next: M.sceneAt((pin ?? time) + 1 / 120.0)) }
        complete = false; live.lastTouch = live.now; playing = false; resumeStart = -1
        // RN queues this command after one live integration step. See applyCommand().
        pendingMerge |= 1 << i
    }
    private var pendingMerge = 0
    private func applyCommand() {
        for i in live.coins.indices where pendingMerge & (1 << i) != 0 { live.capture(i) }
        pendingMerge = 0
    }
    func skip() {
        live.flood = 1; live.completed = true; current = live.scene(reduced: true); complete = true
        onComplete?()
    }
    func reducedAction(_ i: Int, at date: Date) { fadeFrom = opacity(at: date); fadeAt = date; fadeIndex = i; fadeApplied = false }
    func applyReduced() {
        guard !fadeApplied else { return }
        fadeApplied = true
        if fadeIndex >= 0 {
            let c = live.coins[fadeIndex]; c.state = 4; c.r = 0; live.mergedMask |= 1 << fadeIndex
            live.mergeEvents |= 1 << fadeIndex; live.cue = 3; dispatch(finished: false)
        }
        if fadeIndex < 0 || live.coins.allSatisfy({ $0.state == 4 }) {
            live.flood = 1; complete = true; haptic(5); onComplete?()
        }
        current = live.scene(reduced: true)
    }
    func finishFade() { fadeAt = nil }
    private func opacity(at date: Date) -> Double {
        guard let start = fadeAt else { return 1 }
        let t = date.timeIntervalSince(start)
        // Reanimated withTiming's default Easing.inOut(Easing.quad).
        func ease(_ t: Double) -> Double { let p = M.clamp(t); return p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2 }
        return t < 0.08 ? fadeFrom * (1 - ease(t / 0.08)) : ease((t - 0.08) / 0.16)
    }
    private func dispatch(finished: Bool) {
        let ids = TokenFusionSplashResources.ids.enumerated().filter { live.mergedMask & (1 << $0.offset) != 0 }.map(\.element)
        for i in live.coins.indices {
            if live.mergeEvents & (1 << i) != 0 { onMerge?(TokenFusionSplashResources.ids[i], ids) }
            if live.releaseEvents & (1 << i) != 0 { onRelease?(TokenFusionSplashResources.ids[i]) }
        }
        if live.cue != 0 || finished { haptic(finished ? 5 : live.cue) }
        if finished { onComplete?() }
        live.mergeEvents = 0; live.releaseEvents = 0; live.cue = 0
    }
    private func haptic(_ cue: Int) {
        let now = Date().timeIntervalSince1970
        guard now - lastHaptic >= 0.06 else { return }
        lastHaptic = now
        if cue == 5 { UINotificationFeedbackGenerator().notificationOccurred(.success) }
        else {
            let style: UIImpactFeedbackGenerator.FeedbackStyle = cue == 1 ? .light : (cue == 2 ? .soft : (cue == 4 ? .heavy : .medium))
            UIImpactFeedbackGenerator(style: style).impactOccurred()
        }
        // tfs-haptics.ts has no sound assets or playback. Autoplay never reaches this method.
    }
}
