import UIKit

/// One clock owns entrance, authored playback, gesture snapshots, and the return to rest.
final class ChromeZeroNumeralMotion {
    private typealias T = ChromeZeroNumeralTimeline
    enum Action { case idle, down, dragging, holding, coasting, tap, refusing, buttonDown, buttonTurn }
    private struct Touch {
        var action = Action.idle
        var started = 0.0
        var touched = 0.0
        var yaw = T.restYaw
        var pitch = T.restPitch
        var fuse = 1.0
        var scale = 1.0
        var point = CGPoint.zero
        var ripple = CGPoint.zero
        var velocity = 0.0
        var holdable = false
        var snapshot = T.rest()
        var caught = -1.0
    }
    var autoPlay = false
    var reduced = false
    var centre = CGPoint(x: 214, y: 286)
    var unit = 158.0
    private(set) var frame = T.entrance(0.9)
    private(set) var clock = 0.0
    private(set) var playhead = 0.9
    private(set) var mode = 0
    private var touch = Touch()
    private var lastTick = -1.0
    var action: Action { touch.action }

    func advance(_ dt: Double) {
        let previous = frame
        clock += dt
        if mode < 2 {
            if reduced {
                frame = T.rest(0, reduced: true)
                let lightTime = clock.truncatingRemainder(dividingBy: 6)
                if autoPlay && clock >= 6 && lightTime < 1.1 { frame.sweep = T.lerp(-1, 1, lightTime / 1.1) }
            } else {
                let before = playhead
                let next = before + dt
                frame = mode == 0 ? T.entrance(next) : T.loop(next)
                playhead = next.truncatingRemainder(dividingBy: 12)
                if mode == 0 {
                    if before < 3.86 && next >= 3.86 { haptic(.rigid) }
                    if before < 4.02 && next >= 4.02 { haptic(.light) }
                    if next >= 5.54 - 1e-9 {
                        mode = autoPlay ? 1 : 2
                        if !autoPlay {
                            frame = T.loop(5.54)
                            touch.yaw = frame.yaw
                            touch.started = clock - max(0, next - 5.54)
                            touch.touched = touch.started
                        }
                    }
                }
            }
        } else if mode == 3 {
            let t = clock - touch.started
            frame = T.rest(0, reduced: reduced)
            frame.yaw = T.landed(t, touch.yaw, T.nearestRest(touch.yaw), 100, 18, 0.7)
            frame.pitch = T.landed(t, touch.pitch, T.restPitch, 160, 22, 0.7)
            frame.fuse = T.lerp(touch.fuse, 1, T.bezier(t / 0.3))
            frame.scale = T.lerp(touch.scale, 1, T.bezier(t / 0.3))
            if t >= 0.7 {
                mode = 1
                playhead = 0
                frame = T.rest(0, reduced: reduced)
            }
        } else {
            if touch.action == .down && touch.holdable && clock - touch.started >= 0.18 {
                touch.action = .holding
                touch.started = clock
                haptic(.soft)
            }
            frame = touchFrame()
            let action = touch.action
            let elapsed = clock - touch.started
            let busy = [.down, .dragging, .holding, .buttonDown].contains(action)
            let finished = (action == .coasting && elapsed >= (reduced ? 0.3 : 1.88))
                || ([.tap, .refusing].contains(action) && elapsed >= 0.8)
                || (action == .buttonTurn && elapsed >= 1.1)
            if finished {
                touch.action = .idle
                touch.started = clock
                snapshotPose()
                touch.fuse = 1
                touch.scale = 1
            }
            if autoPlay && !busy && clock - touch.touched >= 3.5 {
                mode = 3
                touch.started = clock
                snapshotPose()
            }
            let fast = dt > 0 && abs(frame.yaw - previous.yaw) / dt > 1.4
            if !reduced && [.dragging, .coasting].contains(action) && fast
                && floor(frame.yaw / (.pi / 2)) != floor(previous.yaw / (.pi / 2))
                && clock - lastTick >= 1 / 12 {
                lastTick = clock
                haptic(.light)
            }
        }
    }

    private func touchFrame() -> ChromeZeroNumeralFrame {
        let s = touch
        let t = max(0, clock - s.started)
        var f = T.rest(clock, reduced: reduced)
        f.yaw = s.yaw
        f.pitch = s.pitch
        switch s.action {
        case .idle:
            f.yaw += reduced ? 0 : 0.17 * sin(T.tau * t / 6)
        case .down, .dragging:
            f.fuse = s.fuse
            f.scale = s.scale
        case .holding:
            f.fuse = max(0.22, reduced ? T.lerp(s.fuse, 0.22, T.clamp(t / 0.2)) : s.fuse - 1 + T.loosen(t))
            f.scale = reduced ? 1 : T.lerp(s.scale, 0.97, T.bezier(t / 0.22))
        case .coasting:
            f.yaw = reduced ? T.lerp(s.yaw, T.nearestRest(s.yaw), T.bezier(t / 0.3)) : T.coast(s.yaw, s.velocity, t)
            f.pitch = reduced ? T.lerp(s.pitch, T.restPitch, T.bezier(t / 0.3)) : T.landed(t, s.pitch, T.restPitch, 160, 22, 0.8)
            f.fuse = T.lerp(s.fuse, 1, T.bezier(t / 0.3))
            f.scale = T.lerp(s.scale, 1, T.bezier(t / 0.3))
        case .tap, .refusing:
            let dip = s.action == .tap ? 0.1 : 0
            let from = s.action == .tap ? 0.55 : s.fuse
            let release = max(0, t - dip)
            f.fuse = t < dip ? T.lerp(s.fuse, 0.55, T.bezier(t / dip))
                : reduced ? T.lerp(from, 1, T.progress(release, 0, 0.2))
                : min(1, T.landed(release, from, 1, 220, 18, 0.7))
            f.scale = reduced ? 1 : T.landed(t, s.scale, 1, 260, 14, 0.7)
            f.rippleX = s.ripple.x
            f.rippleY = s.ripple.y
            f.ripple = release > 0 && release < 0.7 ? T.cubic(release / 0.7) : 0
            f.flash = (s.action == .tap ? 0.3 : 0.35) * (1 - T.cubic(release / 0.6))
            f.glint = T.glint(release)
        case .buttonDown:
            f.buttonPress = T.bezier(t / 0.12)
        case .buttonTurn:
            let p = T.clamp(t / 1.1)
            f.yaw = s.yaw + (reduced ? 0 : T.tau * T.bezier(p, 0.77, 0, 0.175, 1))
            f.buttonPress = reduced ? 0 : T.landed(t, 1, 0, 240, 20, 0.65)
            f.arrow = reduced ? 0 : 4 * sin(.pi * T.clamp(t / 0.5))
            f.sweep = p < 1 ? T.lerp(-1, 1, p) : -1
            f.glint = T.glint(t - 0.37)
            for i in 0..<3 { f.twinkle[i] += reduced ? 0 : 0.12 * sin(.pi * p) }
        }
        let catchUp = T.bezier((clock - s.caught) / 0.3)
        f.build = T.lerp(s.snapshot.build, 1, catchUp)
        f.drain = T.lerp(s.snapshot.drain, 0, catchUp)
        f.percent = T.lerp(s.snapshot.percent, 1, catchUp)
        f.dim = T.lerp(s.snapshot.dim, 0, catchUp)
        f.card = T.lerp(s.snapshot.card, 1, catchUp)
        f.cardOpacity = T.lerp(s.snapshot.cardOpacity, 1, catchUp)
        for i in 0..<3 {
            f.sparkles[i] = T.lerp(s.snapshot.sparkles[i], 1, catchUp)
            f.sparkleRotation[i] = T.lerp(s.snapshot.sparkleRotation[i], 0, catchUp)
            f.sparkleOpacity[i] = T.lerp(s.snapshot.sparkleOpacity[i], 1, catchUp)
        }
        for i in 0..<5 {
            f.copy[i] = T.lerp(s.snapshot.copy[i], 1, catchUp)
            f.copyY[i] = T.lerp(s.snapshot.copyY[i], 0, catchUp)
        }
        return f
    }

    private func snapshotPose() {
        touch.yaw = frame.yaw
        touch.pitch = frame.pitch
        touch.fuse = frame.fuse
        touch.scale = frame.scale
    }
    private func catchTouch(_ action: Action, holdable: Bool = true, point: CGPoint? = nil) {
        touch = Touch(action: action, started: clock, touched: clock, yaw: frame.yaw,
                      pitch: frame.pitch, fuse: frame.fuse, scale: frame.scale,
                      point: point ?? centre, holdable: holdable, snapshot: frame, caught: clock)
        mode = 2
    }
    func panBegin(_ point: CGPoint) {
        let dx = (point.x - centre.x) / unit
        let dy = (point.y - centre.y) / unit
        catchTouch(.down, holdable: dx * dx / 0.9 + dy * dy / 1.6 < 1, point: point)
    }
    func panChange(_ point: CGPoint, translation: CGPoint) {
        touch.point = point
        if hypot(translation.x, translation.y) < 6 && touch.action != .dragging { return }
        touch.action = .dragging
        touch.touched = clock
        touch.holdable = false
        touch.yaw = touch.snapshot.yaw + translation.x / 110
        touch.pitch = T.rubberPitch(touch.snapshot.pitch - translation.y / 330)
        touch.fuse = 1
        touch.scale = 1
    }
    func panFinalize(velocityX: Double, success: Bool, point: CGPoint) {
        var action = Action.coasting
        if success && touch.holdable && touch.action == .down {
            action = .tap
            haptic(.light)
        }
        if touch.action == .holding {
            action = .refusing
            if success { haptic(.rigid) }
        }
        touch.action = action
        touch.started = clock
        touch.touched = clock
        snapshotPose()
        touch.ripple = CGPoint(x: max(-0.64, min(0.64, (point.x - centre.x) / (unit * max(cos(frame.yaw), 0.3)))),
                               y: max(-1, min(1, -(point.y - centre.y) / unit)))
        touch.velocity = success ? max(-35, min(35, velocityX / 110)) : 0
    }
    func activateHero() {
        catchTouch(.tap)
        haptic(.light)
    }
    func pressButton() { catchTouch(.buttonDown, holdable: false) }
    func releaseButton(success: Bool) {
        touch.action = success ? .buttonTurn : .coasting
        touch.started = clock
        touch.touched = clock
        snapshotPose()
        touch.velocity = 0
        if success { haptic(.medium) }
    }
    private func haptic(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        UIImpactFeedbackGenerator(style: style).impactOccurred()
    }
}
