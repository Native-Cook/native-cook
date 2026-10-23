import Foundation
import UIKit

/// Event-boundary state changes; presentation reads never mutate the clock or accumulate frames.
@MainActor
final class RaceResultStandingsController {
    typealias Motion = RaceResultStandingsMotion

    enum Channel: CaseIterable {
        case progress, lap, fastest, replay, press, back, track, stretch, fade
    }

    enum Curve { case linear, easeOut, spring }

    struct Animation {
        var start: Double
        let from: Double
        let to: Double
        let duration: Double
        let curve: Curve

        var end: Double { start + duration }

        func value(at time: Double) -> Double {
            let progress = Motion.clamp((time - start) / max(duration, 0.000_001))
            let eased: Double
            switch curve {
            case .linear:
                eased = progress
            case .easeOut:
                eased = Motion.easeOut(progress)
            case .spring:
                let elapsed = max(0, time - start)
                let omega = RaceResultStandingsController.stretchOmega
                let damped = omega * sqrt(1 - Motion.settleDamping * Motion.settleDamping)
                eased = progress >= 1 ? 1 : 1 - exp(-Motion.settleDamping * omega * elapsed) *
                    (cos(damped * elapsed) + Motion.settleDamping * omega / damped * sin(damped * elapsed))
            }
            return from + (to - from) * eased
        }
    }

    struct Snapshot {
        var frame: Motion.Frame
        var stretch: Double
        var backPress: Double
        var trackPress: Double
        var contentOpacity: Double

        var shown: Int { frame.u >= 0.5 ? 1 - frame.from : frame.from }
    }

    enum Target { case title, back, track, none }

    private var automatic: Bool
    private var anchor: Double
    private var manual = Motion.Frame()
    private var values: [Channel: Double] = [.back: 0, .track: 0, .stretch: 0, .fade: 1]
    private var animations: [Channel: Animation] = [:]
    private var wakeTask: Task<Void, Never>?
    private var pausedAt: Double?
    private var lastTouch = -Double.infinity
    private var touching = false
    private var dragging = false
    private var panFailed = false
    private var scrubBlocked = false
    private var pendingReplay = false
    private var target = Target.none
    private var beganAt = 0.0
    private var dragStartU = 0.0
    private var fadeTarget = 0
    private var lightsOutAt: Double?
    private(set) var reduced = false
    private(set) var foreground = true
    let autoPlay: Bool
    var onChange: (() -> Void)?

    private let selection = UISelectionFeedbackGenerator()
    private let lightImpact = UIImpactFeedbackGenerator(style: .light)

    init(autoPlay: Bool, anchor: Double = Date.timeIntervalSinceReferenceDate) {
        self.autoPlay = autoPlay
        self.automatic = autoPlay
        self.anchor = anchor
    }

    func start(at now: Double) {
        anchor = now
        changed(at: now)
    }

    func stop() {
        wakeTask?.cancel()
        wakeTask = nil
        onChange = nil
    }

    func snapshot(at date: Double) -> Snapshot {
        let now = pausedAt ?? date
        let frame = automatic ? Motion.frame(at: max(0, now - anchor)) : sampledManual(at: now)
        return Snapshot(
            frame: frame,
            stretch: value(.stretch, at: now),
            backPress: value(.back, at: now),
            trackPress: value(.track, at: now),
            contentOpacity: value(.fade, at: now)
        )
    }

    func isAnimating(at now: Double) -> Bool {
        guard foreground else { return false }
        if automatic {
            let time = max(0, now - anchor).truncatingRemainder(dividingBy: Motion.loop)
            return time >= Motion.replayAt && time < Motion.replayAt + Motion.replayEnd
        }
        return animations.values.contains { $0.end > now }
    }

    private func sampledManual(at time: Double) -> Motion.Frame {
        var frame = manual
        frame.u = value(.progress, at: time)
        frame.lap = value(.lap, at: time)
        frame.fastestU = value(.fastest, at: time)
        frame.replay = value(.replay, at: time)
        frame.press = value(.press, at: time)
        return frame
    }

    private func base(_ channel: Channel) -> Double {
        switch channel {
        case .progress: manual.u
        case .lap: manual.lap
        case .fastest: manual.fastestU
        case .replay: manual.replay
        case .press: manual.press
        default: values[channel] ?? 0
        }
    }

    private func set(_ channel: Channel, _ value: Double) {
        switch channel {
        case .progress: manual.u = value
        case .lap: manual.lap = value
        case .fastest: manual.fastestU = value
        case .replay: manual.replay = value
        case .press: manual.press = value
        default: values[channel] = value
        }
    }

    private func value(_ channel: Channel, at now: Double) -> Double {
        animations[channel]?.value(at: now) ?? base(channel)
    }

    private func animate(_ channel: Channel, to: Double, duration: Double, curve: Curve = .linear, at now: Double) {
        animations[channel] = Animation(
            start: now,
            from: value(channel, at: now),
            to: to,
            duration: duration,
            curve: curve
        )
    }

    private func cancel(_ channel: Channel, at now: Double) {
        let current = value(channel, at: now)
        animations[channel] = nil
        set(channel, current)
    }

    private func displayed(at now: Double) -> Int {
        let frame = sampledManual(at: now)
        return frame.u >= 0.5 ? 1 - frame.from : frame.from
    }

    private func settle(to: Double, at now: Double) {
        animate(.progress, to: to, duration: abs(to - value(.progress, at: now)) * Motion.switch, at: now)
    }

    private func runLap(at now: Double) {
        let current = value(.lap, at: now)
        set(.lap, current > 0 && current < Motion.lapEnd ? current : 0.0001)
        animations[.lap] = nil
        animate(.lap, to: Motion.lapEnd, duration: (Motion.lapEnd - manual.lap) * Motion.lap, at: now)
    }

    private func rollFastest(at now: Double) {
        if value(.fastest, at: now) >= 1 {
            animations[.fastest] = nil
            manual.fastestFrom = 1 - manual.fastestFrom
            manual.fastestU = 0
        }
        guard manual.fastestFrom != displayed(at: now), value(.lap, at: now) <= 0 else { return }
        animate(.fastest, to: 1, duration: (1 - value(.fastest, at: now)) * Motion.fastestRoll, at: now)
    }

    private func startReplay(at now: Double) {
        if value(.lap, at: now) > 0 {
            animations[.lap] = nil
            manual.lap = 0
            rollFastest(at: now)
        }
        let current = value(.replay, at: now)
        let start = current > 0 ? current : 0.0001
        animations[.replay] = nil
        manual.replay = start
        animate(.replay, to: Motion.replayEnd, duration: Motion.replayEnd - start, at: now)
        if start < Motion.replayGo {
            lightsOutAt = now + Motion.replayGo - start
            lightImpact.prepare()
        }
    }

    private func toggle(at now: Double, haptic: Bool) {
        if value(.progress, at: now) >= 1 {
            animations[.progress] = nil
            manual.from = 1 - manual.from
            manual.u = 0
        }
        let next = 1 - displayed(at: now)
        settle(to: next == 1 - manual.from ? 1 : 0, at: now)
        if haptic { selection.selectionChanged() }
    }

    private func takeover(at now: Double) {
        advance(to: now)
        lastTouch = now
        guard automatic else { return }
        manual = Motion.frame(at: max(0, now - anchor))
        automatic = false
        manual.press = 0
        if manual.replay > 0 { startReplay(at: now) }
    }

    private func fade(to session: Int, at now: Double) {
        fadeTarget = session
        animate(.fade, to: 0, duration: Motion.reducedFade, curve: .easeOut, at: now)
    }

    private func pressChannel(for target: Target) -> Channel? {
        switch target {
        case .title: .press
        case .back: .back
        case .track: .track
        case .none: nil
        }
    }

    private func queueReplay(at now: Double) {
        if animations[.progress] != nil || displayed(at: now) != 0 || value(.progress, at: now) > 0 {
            pendingReplay = true
            if animations[.progress] == nil, displayed(at: now) != 0 { toggle(at: now, haptic: false) }
            else if animations[.progress] == nil { settle(to: manual.from == 0 ? 0 : 1, at: now) }
        } else {
            startReplay(at: now)
        }
    }

    func begin(at now: Double, location: CGPoint, scale: Double, safeTop: Double) {
        guard foreground else { return }
        takeover(at: now)
        touching = true
        dragging = false
        panFailed = false
        scrubBlocked = false
        beganAt = now

        let x = location.x / scale
        let y = (location.y - safeTop) / scale + RaceResultStandingsTheme.REF_TOP
        let titleTop = RaceResultStandingsTheme.layout.title.baseline -
            RaceResultStandingsTheme.ASCENT * RaceResultStandingsTheme.layout.title.size - 8
        if x >= 111.92, x <= 292.33, y >= titleTop, y <= titleTop + 44 {
            target = .title
        } else if abs(x - RaceResultStandingsTheme.layout.back.cx) <= RaceResultStandingsTheme.layout.back.d / 2,
                  abs(y - RaceResultStandingsTheme.layout.back.cy) <= RaceResultStandingsTheme.layout.back.d / 2 {
            target = .back
        } else if !reduced,
                  x >= RaceResultStandingsTheme.layout.track.x,
                  x <= RaceResultStandingsTheme.layout.track.x + RaceResultStandingsTheme.layout.track.w,
                  y >= RaceResultStandingsTheme.layout.track.y,
                  y <= RaceResultStandingsTheme.layout.track.y + RaceResultStandingsTheme.layout.track.h {
            target = .track
        } else {
            target = .none
        }

        if !reduced, let channel = pressChannel(for: target), target != .track || value(.replay, at: now) <= 0 {
            animate(channel, to: 1, duration: Motion.pressDown, curve: .easeOut, at: now)
        }
        selection.prepare()
        changed(at: now)
    }

    func move(at now: Double, translation: CGPoint, width: Double) {
        guard touching, foreground else { return }
        advance(to: now)
        let dx = translation.x
        let dy = translation.y

        if hypot(dx, dy) > 10, let channel = pressChannel(for: target) {
            animate(channel, to: 0, duration: Motion.pressUp, curve: .easeOut, at: now)
            target = .none
        }
        if !dragging, dx > Motion.dragFailOffset || abs(dy) > Motion.dragFailOffset { panFailed = true }
        if !reduced, !panFailed, !dragging, dx < -Motion.dragActiveOffset {
            scrubBlocked = value(.replay, at: now) > 0 || pendingReplay
            if !scrubBlocked {
                cancel(.progress, at: now)
                if manual.u >= 1 {
                    manual.from = 1 - manual.from
                    manual.u = 0
                }
                dragStartU = manual.u
                dragging = true
            }
        }
        if dragging {
            let span = width * Motion.dragSpan
            let raw = dragStartU + max(0, -dx) / span
            manual.u = min(raw, 1)
            animations[.stretch] = nil
            let over = max(0, raw - 1) * span
            set(.stretch, -(over * Motion.rubber) / (1 + over / Motion.rubberFalloff))
        }
        lastTouch = now
        changed(at: now)
    }

    func end(at now: Double, translation: CGPoint, velocity: CGPoint, cancelled: Bool = false) {
        guard touching else { return }
        advance(to: now)

        if dragging {
            animate(.stretch, to: 0, duration: Self.stretchDuration, curve: .spring, at: now)
            if !cancelled {
                let commit = value(.progress, at: now) >= Motion.dragCommit || -velocity.x > Motion.dragVelocity
                if commit, value(.progress, at: now) < 1 { selection.selectionChanged() }
                settle(to: commit ? 1 : 0, at: now)
            }
        } else if !cancelled, hypot(translation.x, translation.y) <= 10 {
            if target == .title, now - beganAt <= 0.8, value(.replay, at: now) <= 0, !pendingReplay {
                if reduced {
                    fade(to: 1 - displayed(at: now), at: now)
                    selection.selectionChanged()
                } else {
                    toggle(at: now, haptic: true)
                }
            } else if target == .track, now - beganAt <= 0.5,
                      !reduced, value(.replay, at: now) <= 0, !pendingReplay {
                queueReplay(at: now)
            }
        }

        if let channel = pressChannel(for: target) {
            animate(channel, to: 0, duration: Motion.pressUp, curve: .easeOut, at: now)
        }
        target = .none
        touching = false
        dragging = false
        lastTouch = now
        changed(at: now)
    }

    func accessibilityToggle(at now: Double) {
        takeover(at: now)
        guard value(.replay, at: now) <= 0, !pendingReplay else { return }
        if reduced { fade(to: 1 - displayed(at: now), at: now) }
        else { toggle(at: now, haptic: false) }
        changed(at: now)
    }

    func accessibilityReplay(at now: Double) {
        takeover(at: now)
        guard !reduced, value(.replay, at: now) <= 0, !pendingReplay else { return }
        queueReplay(at: now)
        changed(at: now)
    }

    func accessibilityBack(at now: Double) {
        takeover(at: now)
        animate(.back, to: 0, duration: Motion.pressUp, curve: .easeOut, at: now)
        changed(at: now)
    }

    func setReduced(_ enabled: Bool, at now: Double) {
        guard reduced != enabled else { return }
        takeover(at: now)
        reduced = enabled
        if enabled {
            let session = displayed(at: now)
            animations.removeAll()
            manual = Motion.Frame(from: session, fastestFrom: session)
            values = [.back: 0, .track: 0, .stretch: 0, .fade: 1]
            pendingReplay = false
            lightsOutAt = nil
        } else if autoPlay {
            automatic = true
            anchor = now
        }
        changed(at: now)
    }

    func setForeground(_ active: Bool, at now: Double) {
        guard foreground != active else { return }
        if !active {
            advance(to: now)
            pausedAt = now
            wakeTask?.cancel()
        } else if let pausedAt {
            let delay = now - pausedAt
            anchor += delay
            lastTouch += delay
            beganAt += delay
            if lightsOutAt != nil { lightsOutAt! += delay }
            for channel in Array(animations.keys) { animations[channel]?.start += delay }
            self.pausedAt = nil
        }
        foreground = active
        changed(at: now)
    }

    /// Called only by input or the single scheduled boundary timer, never by a frame read.
    func advance(to now: Double) {
        guard foreground, !automatic else { return }

        if let fireAt = lightsOutAt, now >= fireAt {
            lightsOutAt = nil
            lightImpact.impactOccurred()
        }

        if touching, target != .none {
            let expiry = beganAt + (target == .title ? 0.8 : 0.5)
            if now >= expiry, let channel = pressChannel(for: target) {
                animate(channel, to: 0, duration: Motion.pressUp, curve: .easeOut, at: expiry)
                target = .none
            }
        }

        while let event = animations.min(by: { $0.value.end < $1.value.end }), event.value.end <= now {
            let channel = event.key
            let animation = event.value
            let time = animation.end
            animations[channel] = nil
            set(channel, animation.to)
            switch channel {
            case .progress:
                if animation.to == 1 {
                    manual.from = 1 - manual.from
                    manual.u = 0
                    runLap(at: time)
                }
            case .lap:
                manual.lap = 0
                rollFastest(at: time)
            case .fastest:
                manual.fastestFrom = 1 - manual.fastestFrom
                manual.fastestU = 0
            case .replay:
                manual.replay = 0
                lightsOutAt = nil
            case .fade:
                if animation.to == 0 {
                    manual = Motion.Frame(from: fadeTarget, fastestFrom: fadeTarget)
                    animate(.fade, to: 1, duration: Motion.reducedFade, curve: .easeOut, at: time)
                }
            default:
                break
            }
        }

        if pendingReplay, animations[.progress] == nil, displayed(at: now) == 0, manual.u == 0 {
            pendingReplay = false
            startReplay(at: now)
        }

        guard autoPlay, !reduced, !touching, !dragging,
              now - lastTouch >= Motion.resumeAfter,
              animations[.progress] == nil,
              animations[.lap] == nil,
              animations[.replay] == nil,
              animations[.fastest] == nil,
              !pendingReplay else { return }

        if displayed(at: now) != 0 {
            toggle(at: now, haptic: false)
        } else {
            let fastest = manual.fastestU >= 1 ? 1 - manual.fastestFrom : manual.fastestFrom
            if fastest != 0 {
                rollFastest(at: now)
            } else {
                manual = Motion.Frame()
                automatic = true
                anchor = now
            }
        }
    }

    private func changed(at now: Double) {
        advance(to: now)
        wakeTask?.cancel()
        onChange?()
        guard foreground else { return }

        var deadlines = animations.values.map(\.end).filter { $0 > now }
        if touching, target != .none { deadlines.append(beganAt + (target == .title ? 0.8 : 0.5)) }
        if let lightsOutAt, lightsOutAt > now { deadlines.append(lightsOutAt) }
        if automatic {
            let time = max(0, now - anchor).truncatingRemainder(dividingBy: Motion.loop)
            let boundaries = [Motion.replayAt, Motion.replayAt + Motion.replayEnd, Motion.loop]
            if let boundary = boundaries.first(where: { $0 > time + 0.000_001 }) {
                deadlines.append(now + boundary - time)
            }
        } else if autoPlay, !reduced, !touching, now < lastTouch + Motion.resumeAfter {
            deadlines.append(lastTouch + Motion.resumeAfter)
        }
        guard let next = deadlines.min() else { return }

        wakeTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: .seconds(max(0.001, next - Date.timeIntervalSinceReferenceDate)))
            } catch {
                return
            }
            guard let self, !Task.isCancelled else { return }
            let time = Date.timeIntervalSinceReferenceDate
            self.advance(to: time)
            self.changed(at: time)
        }
    }

    // Reanimated 4's duration-based spring: mass 4, energy threshold 6e-9,
    // perceptual duration 400ms, physical settling duration 1.5×, damping ratio 0.8.
    nonisolated static let stretchDuration: Double = {
        let omega = stretchOmega
        let damped = omega * sqrt(1 - Motion.settleDamping * Motion.settleDamping)
        func energy(_ time: Double) -> Double {
            let envelope = exp(-Motion.settleDamping * omega * time)
            let x = envelope * (cos(damped * time) + Motion.settleDamping * omega / damped * sin(damped * time))
            let velocity = -envelope * omega * omega / damped * sin(damped * time)
            return x * x + velocity * velocity / (omega * omega)
        }
        var low = 0.0
        var high = 1.0
        for _ in 0..<60 {
            let middle = (low + high) / 2
            if energy(middle) > 6e-9 { low = middle } else { high = middle }
        }
        return high
    }()

    nonisolated static let stretchOmega: Double = {
        func difference(_ stiffness: Double) -> Double {
            let omega = sqrt(stiffness / 4) * Motion.settleDamping
            let time = 0.6
            let x = (1 + omega * time) * exp(-omega * time)
            let velocity = (1 + omega * time) * exp(-omega * time) * -omega + omega * exp(-omega * time)
            return (stiffness * x * x + 4 * velocity * velocity) / stiffness - 6e-9
        }
        var low = Double.ulpOfOne
        var high = 8_000.0
        var current = 4_000.0
        for _ in 0..<100 {
            if abs(difference(current)) <= 6e-12 { break }
            if difference(current) > 0 { low = current } else { high = current }
            current = (low + high) / 2
        }
        return sqrt(current / 4)
    }()
}
