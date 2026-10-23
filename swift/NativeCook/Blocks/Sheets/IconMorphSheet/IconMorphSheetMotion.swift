import SwiftUI
import UIKit

@MainActor
final class IconMorphSheetMotion {
    typealias T = IconMorphSheetTimeline
    typealias Frame = T.Frame

    enum Action { case idle, pan, step, settle, open, close, iconHold, iconRelease, buttonHold, puckHold, returning }
    struct Touch {
        var action: Action
        var snapshot: Frame
        var started: Double
        var touched: Double
        var target: Double
        var duration = T.stepDuration
        var speed = 0.0
        var amplitude = 1.0
        var axis = 0
        var x = 0.0
        var y = 0.0
        var lastX = 0.0
        var lastTime: Double
        var lean: Double
        var icon = false
    }

    var frame = Frame()
    var times = [Double](repeating: 0, count: 4)
    var clock = 0.0
    var epoch = 0.0
    var autoPlay = true
    var reduced = false
    var layout = IconMorphSheetLayout(width: 402, height: 874, top: 62, bottom: 34)
    private(set) var manual = false
    private(set) var touching = false
    private(set) var touch: Touch?
    private var last: Date?
    private var delay = 0.0
    var visible: Bool { delay >= 0.3 }

    func resetDate() { last = nil }

    func advance(to date: Date) {
        guard let previousDate = last else {
            last = date
            manual = !autoPlay
            return
        }
        let dt = max(0, date.timeIntervalSince(previousDate))
        last = date
        if delay < 0.3 {
            delay += dt
            return
        }
        advance(delta: dt)
    }

    func advance(delta: Double) {
        let previous = frame
        clock += delta
        if !manual {
            frame = T.loop(clock - epoch, reduced: reduced)
        } else if let current = touch {
            frame = manualFrame(current, now: clock)
            let held = touching || [.pan, .buttonHold, .iconHold, .puckHold].contains(current.action)
            if current.action == .returning && clock - current.started >= 0.6 {
                manual = false
                epoch = clock
                frame = Frame()
            } else if autoPlay && !held && current.action != .returning && clock - current.touched >= 3.5 {
                touch = state(.returning)
            }
        }
        let rest = frame.position.rounded()
        let atRest = abs(frame.position - rest) < 0.00001
        for i in 0..<4 {
            if reduced { times[i] = IconMorphSheetIcon.heroes[i] }
            else if frame.running < 0.5 || (atRest && Double(i) != rest) { times[i] = 0 }
            else if atRest && abs(previous.position - rest) < 0.00001 && previous.running > 0.5 {
                times[i] = (times[i] + delta * IconMorphSheetIcon.paces[i]).truncatingRemainder(dividingBy: IconMorphSheetIcon.loops[i])
            }
        }
    }

    func state(_ action: Action) -> Touch {
        Touch(action: action, snapshot: frame, started: clock, touched: clock, target: frame.position.rounded(), lastTime: clock, lean: frame.lean)
    }

    func catchTouch(_ action: Action) {
        touch = state(action)
        manual = true
    }

    func ambientDown() {
        touching = true
        if !manual || touch?.action == .returning { catchTouch(.idle) }
    }

    func ambientUp() {
        touching = false
        touch?.touched = clock
    }

    func commitSheet(_ opening: Bool, velocity: Double = 0) {
        var s = state(opening ? .open : .close)
        let distance = max(1, layout.sheetHeight(frame.position) * frame.open - frame.drag)
        s.duration = reduced ? 0.2 : opening ? 0.56 : T.clamp(distance / max(velocity, distance / T.closeDuration), 0.22, T.closeDuration)
        s.speed = !opening && velocity > 0 ? velocity / distance : 0
        touch = s
        manual = true
        UIImpactFeedbackGenerator(style: opening ? .light : .medium).impactOccurred()
    }

    func iconDown() { catchTouch(.iconHold) }

    func iconUp() {
        guard let s = touch else { return }
        if s.action == .iconHold || (s.action == .pan && s.icon && s.axis == 0) {
            frame = manualFrame(s, now: clock)
            touch = state(.iconRelease)
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        }
    }

    func panBegin(x: Double, y: Double) {
        let h = layout.sheetHeight(frame.position)
        let travel = layout.travel(frame, reduced: reduced)
        guard frame.open > 0, x >= 0, x <= layout.width,
              y >= layout.height - h + 56 + travel, y < layout.buttonTop else { return }
        catchTouch(.pan)
        let cy = layout.stageTop + layout.stageHeight / 2 + travel + (reduced ? 0 : 24 * (1 - frame.stage))
        touch?.icon = abs(x - layout.width / 2) <= 90 && abs(y - cy) <= 90
    }

    func panChange(x: Double, y: Double) {
        guard var s = touch, s.action == .pan else { return }
        let axis = s.axis != 0 ? s.axis : hypot(x, y) < 8 ? 0 : abs(x) > abs(y) ? 1 : 2
        let dt = max(1 / 240, clock - s.lastTime)
        let velocity = (x - s.lastX) / dt
        s.axis = axis
        s.x = x
        s.y = y
        s.lastX = x
        s.lastTime = clock
        s.lean = T.mix(s.lean, T.clamp(velocity / 1000, -1, 1), 1 - exp(-dt / 0.065))
        s.touched = clock
        touch = s
    }

    func panFinalize(velocityX: Double, velocityY: Double) {
        guard let s = touch, s.action == .pan else { return }
        frame = manualFrame(s, now: clock)
        if s.axis == 2 && (frame.drag > 120 || velocityY > 900) {
            commitSheet(false, velocity: max(0, velocityY))
            return
        }
        var next = state(.settle)
        next.axis = s.axis
        if s.axis == 1 {
            let base = s.snapshot.position.rounded()
            let direction = abs(velocityX) > 500 ? -(velocityX == 0 ? 0 : velocityX > 0 ? 1.0 : -1.0) : -(s.x == 0 ? 0 : s.x > 0 ? 1.0 : -1.0)
            let commits = abs(s.x / 280) > 0.35 || abs(velocityX) > 500
            next.target = T.clamp(base + (commits ? direction : 0), 0, 3)
            next.speed = -velocityX / 280
            next.duration = reduced ? 0.45 : T.clamp(abs(next.target - frame.position) * 280 / max(abs(velocityX), 420), 0.28, 0.52)
            next.amplitude = T.clamp(abs(velocityX) / 1200, 0.25, 1)
            if next.target != base { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        } else {
            next.speed = velocityY
            next.duration = reduced ? 0.45 : 0.52
            next.amplitude = 0
        }
        touch = next
    }

    func panCancel() {
        guard let s = touch, s.action == .pan else { return }
        frame = manualFrame(s, now: clock)
        var next = state(.settle)
        next.duration = reduced ? 0.45 : 0.4
        next.amplitude = 0
        touch = next
    }

    func buttonDown() { catchTouch(.buttonHold) }

    func buttonUp(inside: Bool) {
        guard let s = touch, s.action == .buttonHold else { return }
        frame = manualFrame(s, now: clock)
        if !inside {
            touch = state(.idle)
        } else if frame.open < 0.5 {
            commitSheet(true)
        } else if frame.position >= 2.5 {
            commitSheet(false)
        } else {
            var next = state(.step)
            next.target = min(3, frame.position.rounded() + 1)
            next.duration = reduced ? 0.45 : T.stepDuration
            touch = next
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }

    func closeTap() { commitSheet(false) }
    func infoTap() { commitSheet(true) }

    func puckDown(_ puck: Int) {
        catchTouch(.puckHold)
        touch?.snapshot.puck = puck
    }

    func puckUp(inside: Bool, puck: Int) {
        guard let s = touch, s.action == .puckHold else { return }
        frame = manualFrame(s, now: clock)
        if inside && puck == 2 { infoTap() }
        else if inside && frame.open > 0 { closeTap() }
        else { touch = state(.idle) }
    }

    func manualFrame(_ s: Touch, now: Double) -> Frame {
        let t = max(0, now - s.started)
        let a = s.snapshot
        var f = a
        f.markerOpacity = a.markerOpacity * (1 - T.progress(t, 0, 0.2))
        f.markerPress = a.markerPress * (1 - T.progress(t, 0, 0.12))
        f.buttonPress = T.spring(t, a.buttonPress, 0)
        f.puckPress = T.spring(t, a.puckPress, 0)
        f.press = T.clamp(T.spring(t, a.press, 0))
        f.bounce = reduced ? 0 : a.bounce * (1 - T.bezier(t / 0.2))
        f.lean = reduced ? 0 : T.spring(t, a.lean, 0, 260, 26)
        f.edge = T.spring(t, a.edge, 0, 260, 26)
        switch s.action {
        case .pan:
            if s.axis == 1 {
                let raw = a.position - s.x / 280
                f.position = T.clamp(raw, 0, 3)
                f.edge = (raw - f.position) * 280 * 0.2
                f.lean = reduced ? 0 : s.lean
                if !reduced && f.edge != 0 { f.bounce = T.clamp(f.edge / 56, -1, 1) * 0.2 }
            } else if s.axis == 2 { f.drag = a.drag + (s.y > 0 ? s.y : s.y * 0.15) }
            if s.icon && s.axis == 0 { f.press = T.mix(a.press, 1, T.bezier(t / 0.11)) }
        case .step, .settle:
            let p = T.progress(t, 0, s.duration)
            let e = s.action == .step ? T.stepEase(p) : T.releaseEase(p, s.target - a.position, s.speed, s.duration)
            f.position = T.mix(a.position, s.target, e)
            f.drag = T.spring(t, a.drag, 0, 260, 26, s.axis == 2 ? s.speed : 0)
            if t >= s.duration { f.bounce = reduced ? 0 : T.bounceAt(t - s.duration, s.amplitude) }
        case .open, .close:
            let opening = s.action == .open
            let target = opening ? 1.0 : 0.0
            let p = T.progress(t, 0, s.duration)
            let e = reduced ? T.progress(t, 0, 0.2) : s.speed > 0 ? T.releaseEase(p, 1, s.speed, s.duration) : T.sheetEase(p)
            f.open = T.mix(a.open, target, e)
            f.drag = T.mix(a.drag, 0, e)
            f.contents = reduced ? T.mix(a.contents, target, e) : T.contentFade(f.open)
            f.stage = reduced ? f.open : opening ? T.mix(a.stage, 1, T.openingStage(t)) : a.stage
            T.rollSheetLabels(&f, a, T.progress(t, 0, 0.26), opening)
            f.running = opening && t >= (reduced ? 0.2 : 0.6) ? 1 : 0
            if !opening && e == 1 {
                f.position = 0
                f.stage = 0
            }
        case .buttonHold: f.buttonPress = T.mix(a.buttonPress, 1, T.bezier(t / 0.12))
        case .puckHold: f.puckPress = T.mix(a.puckPress, 1, T.bezier(t / 0.12))
        case .iconHold: f.press = T.mix(a.press, 1, T.bezier(t / 0.11))
        case .iconRelease: f.bounce = reduced ? 0 : T.bounceAt(t, 0.6)
        case .returning:
            let p = T.bezier(t / 0.6)
            f.position = T.mix(a.position, 0, p)
            f.open = T.mix(a.open, 1, p)
            f.contents = reduced ? T.mix(a.contents, 1, p) : T.contentFade(f.open)
            f.stage = reduced ? f.open : T.mix(a.stage, 1, T.openingStage(t))
            T.rollSheetLabels(&f, a, T.progress(t, 0, 0.6), true)
            f.drag = T.mix(a.drag, 0, p)
            f.edge = T.mix(a.edge, 0, p)
            f.running = t >= 0.6 ? 1 : 0
        case .idle: break
        }
        return f
    }
}
