import Foundation
import Observation

struct HealthAgeDialRenderFrame {
    var scroll = 0.0
    var years = HealthAgeDialTheme.restYears
    var trail = 0.0
    var revealElapsed = -1.0
    var revealOpacity = 1.0
    var lineProgress = 0.0
    var guides = 0.0
    var count1 = 0.0
    var count2 = 0.0
    var badge1 = -1.0
    var badge2 = -1.0
    var bump = 0.0
    var lift = 0.0
    var chipPress = 0.0
}

@MainActor @Observable
final class HealthAgeDialPlayback {
    enum GestureTarget { case scroll, dial, chip }

    private enum Mode { case automatic, manual }
    private var mode = Mode.manual
    private var loopStart = 0.0
    private var autoPlay = false
    private var reduced = false
    private var pausedAt: Double?

    private var scroll = 0.0
    private var scrollReleaseAt: Double?
    private var scrollReleaseFrom = 0.0
    private var scrollReleaseVelocity = 0.0
    private var maxScroll = 0.0
    private var years = HealthAgeDialTheme.restYears
    private var yearsReleaseAt: Double?
    private var yearsReleaseFrom = HealthAgeDialTheme.restYears
    private var yearsReleaseVelocity = 0.0
    private var lastTouch = -100.0
    private var dragging = false
    private var forcedReturnAt: Double?
    private var forcedReturnFrom = 0.0

    private var revealAt: Double?
    private var fadeAt: Double?
    private var landAt = -100.0
    private var landed = false
    private var lastYearStep = Int(floor(HealthAgeDialTheme.restYears))

    private var liftFrom = 0.0
    private var liftTarget = 0.0
    private var liftAt = 0.0
    private var liftDuration = 0.3
    private var liftDamping = 0.8
    private var pressFrom = 0.0
    private var pressTarget = 0.0
    private var pressAt = 0.0
    private var pressDuration = HealthAgeDialMotion.Press.pressIn

    func start(autoPlay: Bool, reduced: Bool, at now: Double) {
        self.autoPlay = autoPlay
        self.reduced = reduced
        mode = autoPlay && !reduced ? .automatic : .manual
        loopStart = now
        scroll = 0
        scrollReleaseAt = nil
        years = HealthAgeDialTheme.restYears
        yearsReleaseAt = nil
        lastTouch = -100
        forcedReturnAt = nil
        revealAt = reduced ? now - 10 : nil
        fadeAt = nil
        landAt = -100
        landed = false
        liftFrom = 0
        liftTarget = 0
        pressFrom = 0
        pressTarget = 0
    }

    func setConfiguration(autoPlay: Bool, reduced: Bool, at now: Double) {
        let current = frame(at: now)
        self.autoPlay = autoPlay
        self.reduced = reduced
        if reduced || !autoPlay {
            mode = .manual
            scroll = current.scroll
            years = current.years
            scrollReleaseAt = nil
            yearsReleaseAt = nil
            forcedReturnAt = nil
            revealAt = reduced ? now - 10 : current.revealElapsed >= 0 ? now - current.revealElapsed : nil
        } else if mode == .manual {
            lastTouch = now
        }
    }

    func setActive(_ active: Bool, at now: Double) {
        if !active, pausedAt == nil {
            pausedAt = now
        } else if active, let pausedAt {
            let shift = max(0, now - pausedAt)
            loopStart += shift
            lastTouch += shift
            if let value = scrollReleaseAt { scrollReleaseAt = value + shift }
            if let value = yearsReleaseAt { yearsReleaseAt = value + shift }
            if let value = forcedReturnAt { forcedReturnAt = value + shift }
            if let value = revealAt { revealAt = value + shift }
            if let value = fadeAt { fadeAt = value + shift }
            landAt += shift
            liftAt += shift
            pressAt += shift
            self.pausedAt = nil
        }
    }

    func setMaximumScroll(_ value: Double) { maxScroll = max(0, value) }

    func accessibilityScroll(to value: Double, at now: Double, screenHeight: Double) {
        beginTouch(at: now)
        _ = updateScroll(value, at: now, screenHeight: screenHeight)
        _ = endScroll(velocity: 0, at: now, screenHeight: screenHeight)
    }

    func beginTouch(at now: Double) {
        let restartedAt = automaticRestartTime()
        let resumedAutomatic = mode == .manual && restartedAt.map { now >= $0 } == true
        let current = resumedAutomatic ? automaticFrame(at: now, start: restartedAt) : frame(at: now)
        if mode == .automatic || resumedAutomatic {
            mode = .manual
            scroll = current.scroll
            years = current.years
            let dt = 1 / 240.0
            let previous = automaticFrame(at: now - dt, start: resumedAutomatic ? restartedAt : nil)
            if abs(current.years - HealthAgeDialTheme.restYears) > 0.001 {
                yearsReleaseFrom = current.years
                yearsReleaseVelocity = (current.years - previous.years) / dt
                yearsReleaseAt = now
            }
            if current.revealElapsed >= 0 { revealAt = now - current.revealElapsed }
        } else {
            scroll = manualScroll(at: now)
            years = manualYears(at: now)
        }
        scrollReleaseAt = nil
        forcedReturnAt = nil
        if let value = revealAt, value > now { revealAt = nil }
        if landAt > now { landAt = -100; landed = false }
        lastTouch = now
    }

    func beginScroll(at now: Double) {
        beginTouch(at: now)
        dragging = true
    }

    @discardableResult
    func updateScroll(_ value: Double, at now: Double, screenHeight: Double) -> Bool {
        scroll = min(max(value, 0), maxScroll)
        lastTouch = now
        updateReveal(at: now, screenHeight: screenHeight)
        let progress = HealthAgeDialMotion.clamp(scroll / Self.landingOffset)
        if progress >= 0.999, !landed {
            landed = true
            if !reduced {
                landAt = now
                return true
            }
        } else if progress < 0.9, landed {
            landed = false
        }
        return false
    }

    func endScroll(velocity: Double, at now: Double, screenHeight: Double) -> Double? {
        dragging = false
        scrollReleaseFrom = scroll
        scrollReleaseVelocity = min(max(velocity, -2200), 2200)
        scrollReleaseAt = abs(scrollReleaseVelocity) > 20 ? now : nil
        lastTouch = now
        updateReveal(at: now, screenHeight: screenHeight)
        guard scrollReleaseAt != nil else { return nil }

        let revealThreshold = -HealthAgeDialTheme.pinSafeTop + HealthAgeDialTheme.Layout.Chart.top
            - (screenHeight - 150)
        if revealAt == nil,
           let delay = momentumCrossingTime(from: scrollReleaseFrom, velocity: scrollReleaseVelocity,
                                            target: revealThreshold) {
            revealAt = now + delay
        }
        let goneThreshold = -HealthAgeDialTheme.pinSafeTop + HealthAgeDialTheme.Layout.Chart.top
            - (screenHeight - 90)
        if revealAt != nil, fadeAt == nil,
           let delay = momentumCrossingTime(from: scrollReleaseFrom, velocity: scrollReleaseVelocity,
                                            target: goneThreshold), scrollReleaseVelocity < 0 {
            fadeAt = now + delay
        }
        if !reduced, !landed,
           let delay = momentumCrossingTime(from: scrollReleaseFrom, velocity: scrollReleaseVelocity,
                                            target: Self.landingOffset), scrollReleaseVelocity > 0 {
            landAt = now + delay
            landed = true
            return delay
        }
        return nil
    }

    func beginDial(at now: Double) {
        beginTouch(at: now)
        dragging = true
        yearsReleaseAt = nil
        setLift(1, duration: 0.3, damping: 0.8, at: now)
    }

    @discardableResult
    func updateDial(angle: Double, at now: Double) -> Bool {
        var next = angle / HealthAgeDialTheme.Layout.Dial.degreesPerYear
        if abs(angle) > 150, next.sign != years.sign { next = years }
        next = min(max(next, -HealthAgeDialTheme.Layout.Dial.maxYears), HealthAgeDialTheme.Layout.Dial.maxYears)
        years = next
        lastTouch = now
        let step = Int(floor(next))
        if step != lastYearStep {
            lastYearStep = step
            return true
        }
        return false
    }

    func endDial(velocity: Double, at now: Double) {
        dragging = false
        yearsReleaseFrom = years
        yearsReleaseVelocity = min(max(velocity, -40), 40)
        yearsReleaseAt = now
        lastTouch = now
        lastYearStep = Int(floor(HealthAgeDialTheme.restYears))
        setLift(0, duration: 0.4, damping: 1, at: now)
    }

    func chipPress(_ pressed: Bool, at now: Double) {
        pressFrom = pressValue(at: now)
        pressTarget = pressed ? 1 : 0
        pressAt = now
        pressDuration = pressed ? HealthAgeDialMotion.Press.pressIn : HealthAgeDialMotion.Press.release
        if pressed { beginTouch(at: now) }
    }

    func glideToDial(at now: Double) {
        let current = frame(at: now)
        guard current.scroll > 1 else { return }
        mode = .manual
        scroll = current.scroll
        years = current.years
        forcedReturnFrom = current.scroll
        forcedReturnAt = now
        scrollReleaseAt = nil
        lastTouch = now
    }

    func frame(at suppliedNow: Double) -> HealthAgeDialRenderFrame {
        let now = min(suppliedNow, pausedAt ?? suppliedNow)
        if mode == .automatic, !reduced { return automaticFrame(at: now) }

        var currentScroll = manualScroll(at: now)
        var currentYears = manualYears(at: now)
        let returnStart = forcedReturnAt ?? (!reduced && autoPlay && !dragging ? lastTouch + HealthAgeDialMotion.resumeAfter : nil)
        var restartAt: Double?
        if let returnStart, now >= returnStart {
            let origin = forcedReturnAt == nil ? manualScroll(at: returnStart) : forcedReturnFrom
            let progress = HealthAgeDialMotion.clamp((now - returnStart) / HealthAgeDialMotion.returnDuration)
            currentScroll = origin * (1 - HealthAgeDialMotion.easeInOut(progress))
            restartAt = automaticRestartTime()
        }
        if let restartAt, autoPlay, !reduced, now >= restartAt {
            return automaticFrame(at: now, start: restartAt)
        }

        let dt = 1 / 240.0
        let previousYears = manualYears(at: max(0, now - dt))
        let velocity = (currentYears - previousYears) / dt * HealthAgeDialTheme.Layout.Dial.degreesPerYear
        let fadeFinished = fadeAt.map { now - $0 >= HealthAgeDialMotion.Reveal.fade } ?? false
        let revealElapsed = fadeFinished ? -1 : revealAt.map { now - $0 } ?? -1
        let opacity = fadeAt.map { 1 - HealthAgeDialMotion.clamp((now - $0) / HealthAgeDialMotion.Reveal.fade) } ?? 1
        if reduced, currentScroll > 0, revealAt == nil { currentYears = HealthAgeDialTheme.restYears }
        return composedFrame(scroll: currentScroll, years: currentYears,
                             trail: reduced ? 0 : HealthAgeDialMotion.trail(for: velocity),
                             revealElapsed: reduced && currentScroll > 0 ? 10 : revealElapsed,
                             revealOpacity: opacity, bump: reduced ? 0 : HealthAgeDialMotion.landBump(now - landAt),
                             lift: liftValue(at: now), press: pressValue(at: now))
    }

    func tickEnd(at now: Double) -> Double? {
        if autoPlay, !reduced { return nil }
        if dragging { return nil }
        var end = now
        if let value = scrollReleaseAt { end = max(end, value + 3) }
        if let value = yearsReleaseAt { end = max(end, value + (reduced ? 0.2 : 1.6)) }
        if let value = revealAt { end = max(end, value + 1.3) }
        if let value = fadeAt { end = max(end, value + HealthAgeDialMotion.Reveal.fade) }
        if landAt > -90 { end = max(end, landAt + 1.2) }
        if let value = forcedReturnAt { end = max(end, value + HealthAgeDialMotion.returnDuration) }
        end = max(end, liftAt + liftDuration + 0.3, pressAt + pressDuration)
        return end
    }

    private func automaticFrame(at now: Double, start: Double? = nil) -> HealthAgeDialRenderFrame {
        let origin = start ?? loopStart
        let time = normalized(now - origin)
        let frame = HealthAgeDialMotion.loopFrame(
            at: time,
            rest: HealthAgeDialTheme.restYears,
            degreesPerYear: HealthAgeDialTheme.Layout.Dial.degreesPerYear,
            historyOffset: HealthAgeDialTheme.Layout.historyOffset
        )
        let landing = HealthAgeDialMotion.landingTime(offset: Self.landingOffset,
                                                       historyOffset: HealthAgeDialTheme.Layout.historyOffset)
        return composedFrame(scroll: frame.scroll, years: frame.years,
                             trail: HealthAgeDialMotion.trail(for: frame.velocity),
                             revealElapsed: frame.reveal, revealOpacity: frame.revealOpacity,
                             bump: HealthAgeDialMotion.landBump(time - landing), lift: 0, press: pressValue(at: now))
    }

    private func composedFrame(scroll: Double, years: Double, trail: Double, revealElapsed: Double,
                               revealOpacity: Double, bump: Double, lift: Double,
                               press: Double) -> HealthAgeDialRenderFrame {
        let elapsed = revealElapsed
        return HealthAgeDialRenderFrame(
            scroll: scroll,
            years: years,
            trail: trail,
            revealElapsed: elapsed,
            revealOpacity: revealOpacity,
            lineProgress: elapsed < 0 ? 0 : HealthAgeDialMotion.easeInOut(elapsed / HealthAgeDialMotion.Reveal.line),
            guides: elapsed < 0 ? 0 : HealthAgeDialMotion.easeOut((elapsed - HealthAgeDialMotion.Reveal.guidesAt) / HealthAgeDialMotion.Reveal.guides),
            count1: elapsed < 0 ? 0 : HealthAgeDialMotion.easeOut((elapsed - HealthAgeDialMotion.Reveal.countAt[0]) / HealthAgeDialMotion.Reveal.count),
            count2: elapsed < 0 ? 0 : HealthAgeDialMotion.easeOut((elapsed - HealthAgeDialMotion.Reveal.countAt[1]) / HealthAgeDialMotion.Reveal.count),
            badge1: elapsed < 0 ? -1 : min(elapsed - HealthAgeDialMotion.Reveal.badgeAt[0], 2),
            badge2: elapsed < 0 ? -1 : min(elapsed - HealthAgeDialMotion.Reveal.badgeAt[1], 2),
            bump: bump,
            lift: lift,
            chipPress: press
        )
    }

    private func manualScroll(at now: Double) -> Double {
        guard let released = scrollReleaseAt else { return scroll }
        let seconds = max(0, now - released)
        let decay = 2.0
        let projected = scrollReleaseFrom + scrollReleaseVelocity / decay * (1 - exp(-decay * seconds))
        return min(max(projected, 0), maxScroll)
    }

    private func manualYears(at now: Double) -> Double {
        guard let released = yearsReleaseAt else { return years }
        let seconds = max(0, now - released)
        if reduced {
            return HealthAgeDialMotion.lerp(yearsReleaseFrom, HealthAgeDialTheme.restYears,
                                            HealthAgeDialMotion.easeOut(seconds / 0.2))
        }
        if seconds > 1.6 { return HealthAgeDialTheme.restYears }
        return HealthAgeDialTheme.restYears + HealthAgeDialMotion.springDisplacement(
            seconds,
            displacement: yearsReleaseFrom - HealthAgeDialTheme.restYears,
            velocity: yearsReleaseVelocity,
            spring: HealthAgeDialMotion.Spring.dot
        )
    }

    private func automaticRestartTime() -> Double? {
        guard autoPlay, !reduced, !dragging else { return nil }
        let returnStart = forcedReturnAt ?? lastTouch + HealthAgeDialMotion.resumeAfter
        let extraDelay = forcedReturnAt == nil ? 0 : 2.8
        return returnStart + HealthAgeDialMotion.returnDuration + extraDelay
    }

    private func momentumCrossingTime(from: Double, velocity: Double, target: Double) -> Double? {
        let decay = 2.0
        guard abs(velocity) > 20 else { return nil }
        let projected = min(max(from + velocity / decay, 0), maxScroll)
        guard target >= min(from, projected), target <= max(from, projected), abs(target - from) > 0.001 else { return nil }
        let remainder = 1 - (target - from) * decay / velocity
        guard remainder > 0, remainder <= 1 else { return nil }
        return -log(remainder) / decay
    }

    private func updateReveal(at now: Double, screenHeight: Double) {
        if let value = fadeAt, now - value >= HealthAgeDialMotion.Reveal.fade {
            revealAt = nil
            fadeAt = nil
        }
        let top = -HealthAgeDialTheme.pinSafeTop + HealthAgeDialTheme.Layout.Chart.top - scroll
        let visible = top < screenHeight - 150
        let gone = top > screenHeight - 90
        if revealAt == nil, visible {
            revealAt = reduced ? now - 10 : now
            fadeAt = nil
        } else if revealAt != nil, gone {
            if fadeAt == nil { fadeAt = now }
        } else if revealAt != nil {
            fadeAt = nil
        }
    }

    private func setLift(_ target: Double, duration: Double, damping: Double, at now: Double) {
        liftFrom = liftValue(at: now)
        liftTarget = target
        liftAt = now
        liftDuration = duration
        liftDamping = damping
    }

    private func liftValue(at now: Double) -> Double {
        let elapsed = max(0, now - liftAt)
        let omega = 4.6 / max(0.001, liftDamping * liftDuration)
        let progress: Double
        if liftDamping >= 1 {
            let x = omega * elapsed
            progress = 1 - exp(-x) * (1 + x)
        } else {
            progress = HealthAgeDialMotion.spring(elapsed, spring: (omega, liftDamping))
        }
        return HealthAgeDialMotion.lerp(liftFrom, liftTarget, HealthAgeDialMotion.clamp(progress))
    }

    private func pressValue(at now: Double) -> Double {
        HealthAgeDialMotion.lerp(pressFrom, pressTarget,
                                 HealthAgeDialMotion.easeOut((now - pressAt) / max(0.001, pressDuration)))
    }

    private func normalized(_ value: Double) -> Double {
        (value.truncatingRemainder(dividingBy: HealthAgeDialMotion.loop) + HealthAgeDialMotion.loop)
            .truncatingRemainder(dividingBy: HealthAgeDialMotion.loop)
    }

    static let landingOffset = HealthAgeDialTheme.Layout.Dial.cy
        - (HealthAgeDialTheme.Layout.Chip.top + HealthAgeDialTheme.Layout.Chip.height / 2)
}
