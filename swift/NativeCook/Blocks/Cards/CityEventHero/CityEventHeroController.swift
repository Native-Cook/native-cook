import CoreMotion
import Foundation
import Observation

@MainActor
@Observable
final class CityEventHeroController {
    enum Hit { case none, sky, guests, like, view }

    private enum Mode { case rest, automatic, manual, returning }
    private enum Key: Hashable {
        case fan(Int), spot(Int), nameProgress, like, pop, ring, count
        case press, chevron, sweep, tod, day, night, mist, show, camX, camY, camZ, title, still
    }
    private enum Curve {
        case linear(Double)
        case easeOut(Double)
        case easeInOut(Double)
        case spring(CityEventHeroMotion.Spring)
        case velocitySpring(CityEventHeroMotion.Spring, velocity: Double)
        case sine(Double)
        case defaultTiming(Double)
        case showCamera(riseStart: Double, riseEnd: Double)
        case nightTap(Double)
        case heartPop
        case chevronNudge
        case show
        case titleFlash(hold: Double)
    }
    private struct Track {
        let from: Double
        let to: Double
        var start: Double
        let delay: Double
        let curve: Curve

        func value(at time: Double) -> Double {
            let elapsed = time - start - delay
            switch curve {
            case let .linear(duration):
                return from + (to - from) * CityEventHeroMotion.clamp(elapsed / duration)
            case let .easeOut(duration):
                return from + (to - from) * CityEventHeroMotion.easeOut(CityEventHeroMotion.clamp(elapsed / duration))
            case let .easeInOut(duration):
                return from + (to - from) * CityEventHeroMotion.easeInOut(CityEventHeroMotion.clamp(elapsed / duration))
            case let .spring(spring):
                return from + (to - from) * CityEventHeroMotion.spring(elapsed, spring)
            case let .velocitySpring(spring, velocity):
                guard elapsed > 0 else { return from }
                let omega = 2 * Double.pi / spring.duration
                let zeta = spring.dampingRatio
                let displacement = from - to
                let envelope = exp(-zeta * omega * elapsed)
                if envelope < 0.0001 { return to }
                if zeta == 1 {
                    return to + (displacement + (velocity + omega * displacement) * elapsed) * envelope
                }
                let damped = omega * sqrt(1 - zeta * zeta)
                return to + envelope * (displacement * cos(damped * elapsed)
                    + (velocity + zeta * omega * displacement) / damped * sin(damped * elapsed))
            case let .sine(duration):
                return from + (to - from) * CityEventHeroMotion.bezier(CityEventHeroMotion.clamp(elapsed / duration), 0.37, 0, 0.63, 1)
            case let .defaultTiming(duration):
                let p = CityEventHeroMotion.clamp(elapsed / duration)
                let eased = p < 0.5 ? 2 * p * p : 1 - 2 * (1 - p) * (1 - p)
                return from + (to - from) * eased
            case let .showCamera(riseStart, riseEnd):
                if elapsed < riseStart { return from }
                if elapsed < riseEnd {
                    return from + (1 - from) * CityEventHeroMotion.bezier((elapsed - riseStart) / (riseEnd - riseStart), 0.37, 0, 0.63, 1)
                }
                return 1 - CityEventHeroMotion.bezier(
                    CityEventHeroMotion.segment(elapsed, CityEventHeroMotion.SHOW_END, CityEventHeroMotion.SHOW_END + 0.9), 0.37, 0, 0.63, 1
                )
            case let .nightTap(duration):
                return from + (to - from) * CityEventHeroMotion.easeNightTap(CityEventHeroMotion.clamp(elapsed / duration))
            case .heartPop:
                return elapsed <= 0 ? from : CityEventHeroMotion.pop(at: elapsed)
            case .chevronNudge:
                return elapsed <= 0 ? from : CityEventHeroMotion.nudge(at: elapsed)
            case .show:
                return elapsed < 0 ? -1 : min(CityEventHeroMotion.SHOW_END, elapsed)
            case let .titleFlash(hold):
                if elapsed <= 0 { return from }
                if elapsed < 0.16 {
                    return from + (1 - from) * CityEventHeroMotion.easeOut(elapsed / 0.16)
                }
                if elapsed < 0.16 + hold { return 1 }
                return 1 - CityEventHeroMotion.easeInOut(
                    CityEventHeroMotion.clamp((elapsed - 0.16 - hold) / CityEventHeroMotion.Title.fade)
                )
            }
        }
    }

    @ObservationIgnored private let motionManager = CMMotionManager()
    @ObservationIgnored private var gravityReady = false
    @ObservationIgnored private var gravityX = 0.0
    @ObservationIgnored private var gravityY = 0.0
    @ObservationIgnored private var tiltX = 0.0
    @ObservationIgnored private var tiltY = 0.0
    @ObservationIgnored private var lastFrameTime = 0.0

    private var mode = Mode.rest
    private var base = CityEventHeroMotion.Frame()
    private var tracks: [Key: Track] = [:]
    private var autoStart = 0.0
    private var pausedAt: Double?
    private var resumeTask: Task<Void, Never>?
    private var showHapticTask: Task<Void, Never>?
    private var pressing = Hit.none
    private var pressStarted = 0.0
    private var isFanned = false
    private var spotlight = -1
    private var isLiked = false
    private var reducedSwap: (from: [Double], to: Double, start: Double)?
    private var panTODFrom = 0.0
    private var panCamXFrom = 0.0
    private var panCamYFrom = 0.0
    private var showEndsAt: Double?
    private var stillOn = false
    private var reducedOffAt: Double?
    private var returnStarted: Double?

    private(set) var autoPlay = false
    private(set) var reduceMotion = false
    private(set) var screenReader = false
    private(set) var active = true
    private(set) var isTicking = false
    private(set) var lightHaptic = 0
    private(set) var selectionHaptic = 0
    private(set) var burstHaptic = 0

    private var looping: Bool { autoPlay && !reduceMotion && !screenReader }

    func configure(autoPlay: Bool, reduceMotion: Bool, screenReader: Bool, at time: Double) {
        resumeTask?.cancel()
        showHapticTask?.cancel()
        self.autoPlay = autoPlay
        self.reduceMotion = reduceMotion
        self.screenReader = screenReader
        base = .init()
        tracks.removeAll()
        reducedSwap = nil
        showEndsAt = nil
        reducedOffAt = nil
        stillOn = false
        isFanned = false
        spotlight = -1
        isLiked = false
        pausedAt = active ? nil : time
        returnStarted = nil
        resetTilt()
        updateMotionUpdates()
        if looping {
            mode = .automatic
            autoStart = time
            isTicking = active
        } else {
            mode = .rest
            isTicking = false
        }
    }

    func setActive(_ value: Bool, at time: Double) {
        guard value != active else { return }
        active = value
        if !value {
            pausedAt = time
            isTicking = false
            motionManager.stopDeviceMotionUpdates()
            resumeTask?.cancel()
            showHapticTask?.cancel()
        } else if let pausedAt {
            let pause = max(0, time - pausedAt)
            autoStart += pause
            tracks = tracks.mapValues { track in
                var shifted = track
                shifted.start += pause
                return shifted
            }
            if var swap = reducedSwap { swap.start += pause; reducedSwap = swap }
            if let showEndsAt { self.showEndsAt = showEndsAt + pause }
            if let reducedOffAt { self.reducedOffAt = reducedOffAt + pause }
            if let returnStarted { self.returnStarted = returnStarted + pause }
            self.pausedAt = nil
            isTicking = mode == .automatic ? looping : mode != .rest
            updateMotionUpdates()
            if mode == .manual {
                if looping { scheduleReturn() } else { scheduleFreeze() }
            } else if mode == .returning {
                let remaining = max(0, CityEventHeroMotion.returnDuration - (time - (returnStarted ?? time)))
                resumeTask = Task { @MainActor [weak self] in
                    do { try await Task.sleep(for: .seconds(remaining)) } catch { return }
                    self?.finishReturn(at: Date.timeIntervalSinceReferenceDate)
                }
            }
        }
    }

    func frame(at time: Double, includeTilt: Bool = true) -> CityEventHeroMotion.Frame {
        let sampleTime = pausedAt ?? time
        var output: CityEventHeroMotion.Frame
        if mode == .automatic {
            output = CityEventHeroMotion.frame(at: sampleTime - autoStart)
        } else {
            output = base
            for (key, track) in tracks { set(track.value(at: sampleTime), for: key, in: &output) }
            if let swap = reducedSwap {
                let elapsed = CityEventHeroMotion.clamp((sampleTime - swap.start) / CityEventHeroMotion.reducedFade)
                let p = elapsed < 0.5 ? elapsed * 2 : (elapsed - 0.5) * 2
                let eased = p < 0.5 ? 2 * p * p : 1 - 2 * (1 - p) * (1 - p)
                output.guestFade = elapsed < 0.5 ? 1 - eased : eased
                output.fan = elapsed >= 0.5 ? Array(repeating: swap.to, count: output.fan.count) : swap.from
            }
            if let reducedOffAt, sampleTime >= reducedOffAt {
                output.show = -1
                output.day = 0
                output.night = 0
                output.still = 1
            }
            output.fire = CityEventHeroMotion.fire(of: output.show)
        }
        if includeTilt, !reduceMotion {
            output.camX += tiltX / CityEventHeroMotion.Space.ampX
            output.camY += tiltY / CityEventHeroMotion.Space.ampY
        }
        return output
    }

    func begin(_ hit: Hit, at time: Double) {
        resumeTask?.cancel()
        takeOver(at: time)
        pressing = hit
        pressStarted = time
        if hit == .view {
            track(.press, to: 1, at: time, curve: .easeOut(CityEventHeroMotion.View.pressIn))
        }
    }

    func end(_ hit: Hit, guestSlot: Int?, succeeded: Bool, at time: Double) {
        defer {
            pressing = .none
            if looping { scheduleReturn() } else { scheduleFreeze() }
        }
        guard succeeded, hit == pressing, time - pressStarted <= 0.8 else {
            if pressing == .view { track(.press, to: 0, at: time, curve: .spring(CityEventHeroMotion.Springs.ui)) }
            return
        }
        switch hit {
        case .sky: toggleEvening(at: time)
        case .guests: activateGuests(slot: guestSlot, at: time)
        case .like: activateLike(at: time)
        case .view: releaseView(commit: true, at: time)
        case .none: break
        }
    }

    func beginSkyDrag(at time: Double) {
        guard pressing == .sky else { return }
        let current = frame(at: time, includeTilt: false)
        panTODFrom = tod(of: current)
        panCamXFrom = current.camX
        panCamYFrom = current.camY
        tracks.removeValue(forKey: .tod)
        tracks.removeValue(forKey: .camX)
        tracks.removeValue(forKey: .camY)
        setTOD(panTODFrom, in: &base)
        base.camX = panCamXFrom
        base.camY = panCamYFrom
        if !reduceMotion { track(.title, to: 1, at: time, curve: .easeOut(0.16)) }
    }

    func scrubSky(
        translationX: Double,
        translationY: Double,
        cardWidth: Double,
        cardHeight: Double,
        at time: Double
    ) {
        guard pressing == .sky, cardWidth > 0, cardHeight > 0 else { return }
        let value = clampTOD(
            panTODFrom
                - translationX / (CityEventHeroMotion.Night.scrubX * cardWidth)
                + translationY / (CityEventHeroMotion.Night.scrub * cardHeight)
        )
        setTOD(value, in: &base)
        if !reduceMotion {
            base.camX = rubber(
                panCamXFrom + translationX * CityEventHeroMotion.Space.follow / CityEventHeroMotion.Space.ampX,
                limit: CityEventHeroMotion.Space.reach
            )
            base.camY = rubber(
                panCamYFrom + translationY * CityEventHeroMotion.Space.follow / CityEventHeroMotion.Space.ampY,
                limit: CityEventHeroMotion.Space.reach
            )
        }
    }

    func endSkyDrag(velocity: Double, velocityX: Double, velocityY: Double, at time: Double) {
        guard pressing == .sky else { return }
        let current = tod(of: frame(at: time, includeTilt: false))
        let target = velocity > CityEventHeroMotion.Night.flick
            || (velocity > -CityEventHeroMotion.Night.flick && current > 1) ? 2.0 : 0.0
        track(.tod, to: target, at: time, curve: .velocitySpring(.init(duration: 0.9, dampingRatio: 1), velocity: velocity))
        track(.camX, to: 0, at: time, curve: .velocitySpring(CityEventHeroMotion.CAM_SPRING,
            velocity: velocityX * CityEventHeroMotion.Space.follow / CityEventHeroMotion.Space.ampX))
        track(.camY, to: 0, at: time, curve: .velocitySpring(CityEventHeroMotion.CAM_SPRING,
            velocity: velocityY * CityEventHeroMotion.Space.follow / CityEventHeroMotion.Space.ampY))
        if !reduceMotion {
            track(.title, to: 0, at: time, delay: 0.9 + CityEventHeroMotion.Title.hold, curve: .easeInOut(CityEventHeroMotion.Title.fade))
        }
        pressing = .none
        if looping { scheduleReturn() } else { scheduleFreeze() }
    }

    func accessibilityGuests(at time: Double) { begin(.guests, at: time); end(.guests, guestSlot: nil, succeeded: true, at: time) }
    func accessibilitySky(at time: Double) { begin(.sky, at: time); end(.sky, guestSlot: nil, succeeded: true, at: time) }
    func accessibilityLike(at time: Double) { begin(.like, at: time); end(.like, guestSlot: nil, succeeded: true, at: time) }
    func accessibilityView(at time: Double) { begin(.view, at: time); end(.view, guestSlot: nil, succeeded: true, at: time) }

    private func takeOver(at time: Double) {
        guard mode == .automatic || mode == .returning else {
            if mode == .rest { mode = .manual; isTicking = active }
            return
        }
        base = frame(at: time, includeTilt: false)
        tracks.removeAll()
        mode = .manual
        isTicking = active
        isFanned = base.fan.contains { $0 > 0.5 }
        for slot in base.fan.indices {
            track(.fan(slot), to: isFanned ? 1 : 0, at: time, curve: .spring(CityEventHeroMotion.Springs.fan))
        }
        spotlight = base.spot.lastIndex { $0 > 0.5 } ?? -1
        for slot in base.spot.indices {
            track(.spot(slot), to: slot == spotlight ? 1 : 0, at: time, curve: .spring(CityEventHeroMotion.Springs.spot))
        }
        let shown = base.nameProgress > 0.5 ? base.nameTo : base.nameFrom
        base.nameFrom = shown
        base.nameTo = shown
        base.nameProgress = 0
        isLiked = base.like > 0.5
        track(.like, to: isLiked ? 1 : 0, at: time, curve: .defaultTiming(0.12))
        track(.count, to: isLiked ? 1 : 0, at: time, curve: .defaultTiming(0.12))
        track(.pop, to: 1, at: time, curve: .spring(CityEventHeroMotion.Springs.ui))
        if base.ring > 0, base.ring < 1 { track(.ring, to: 1, at: time, curve: .easeOut(0.2)) }
        if base.sweep > 0, base.sweep < 1 { track(.sweep, to: 1, at: time, curve: .easeInOut(0.2)) }
        track(.press, to: 0, at: time, curve: .spring(CityEventHeroMotion.Springs.ui))
        track(.chevron, to: 0, at: time, curve: .spring(CityEventHeroMotion.Springs.nudge))
        if base.title > 0 { track(.title, to: 0, at: time, curve: .defaultTiming(CityEventHeroMotion.Title.fade)) }
        if base.show >= 0, base.show < CityEventHeroMotion.SHOW_END {
            let remaining = CityEventHeroMotion.SHOW_END - base.show
            track(.show, to: CityEventHeroMotion.SHOW_END, at: time, curve: .linear(remaining))
            showEndsAt = time + remaining
        }
    }

    private func activateGuests(slot: Int?, at time: Double) {
        if !isFanned {
            moveGuests(to: 1, at: time)
            lightHaptic += 1
        } else if let slot, slot != spotlight {
            setSpot(slot, at: time)
            selectionHaptic += 1
        } else {
            setSpot(-1, at: time)
            moveGuests(to: 0, at: time)
            lightHaptic += 1
        }
    }

    private func moveGuests(to target: Double, at time: Double) {
        let current = frame(at: time, includeTilt: false)
        if reduceMotion {
            reducedSwap = (current.fan, target, time)
        } else {
            for slot in current.fan.indices {
                let delay = target > 0.5
                    ? Double(3 - CityEventHeroMotion.rank(slot)) * CityEventHeroMotion.FAN_STAGGER
                    : Double(CityEventHeroMotion.rank(slot)) * CityEventHeroMotion.FAN_STAGGER
                track(.fan(slot), to: target, at: time, delay: delay, curve: .spring(CityEventHeroMotion.Springs.fan))
            }
        }
        isFanned = target > 0.5
    }

    private func setSpot(_ slot: Int, at time: Double) {
        for index in CityEventHeroMotion.slots.indices {
            track(.spot(index), to: !reduceMotion && index == slot ? 1 : 0, at: time, curve: .spring(CityEventHeroMotion.Springs.spot))
        }
        spotlight = slot
        rollName(to: slot >= 0 ? CityEventHeroMotion.slots[slot].image : CityEventHeroMotion.restName, at: time)
    }

    private func rollName(to image: Int, at time: Double) {
        let current = frame(at: time, includeTilt: false)
        let shown = current.nameProgress > 0.5 ? current.nameTo : current.nameFrom
        base.nameFrom = shown
        base.nameTo = image
        base.nameProgress = 0
        tracks.removeValue(forKey: .nameProgress)
        track(.nameProgress, to: 1, at: time, curve: .easeInOut(reduceMotion ? CityEventHeroMotion.reducedFade : CityEventHeroMotion.Roll.name))
    }

    private func activateLike(at time: Double) {
        isLiked.toggle()
        if isLiked, !reduceMotion {
            track(.pop, to: 1, at: time, curve: .heartPop)
            track(.ring, from: 0, to: 1, at: time, delay: CityEventHeroMotion.Like.dip, curve: .easeOut(CityEventHeroMotion.Like.ring))
            track(.like, to: 1, at: time, delay: CityEventHeroMotion.Like.dip, curve: .easeOut(CityEventHeroMotion.Like.fill))
            track(.count, to: 1, at: time, delay: CityEventHeroMotion.Like.dip, curve: .easeInOut(CityEventHeroMotion.Roll.count))
        } else {
            let duration = reduceMotion ? CityEventHeroMotion.reducedFade : CityEventHeroMotion.Like.unfill
            track(.like, to: isLiked ? 1 : 0, at: time, curve: .easeOut(duration))
            track(.count, to: isLiked ? 1 : 0, at: time, curve: .easeInOut(reduceMotion ? CityEventHeroMotion.reducedFade : CityEventHeroMotion.Roll.count))
        }
        if isLiked { lightHaptic += 1 }
    }

    private func toggleEvening(at time: Double) {
        guard !showRunning(at: time) else { return }
        let current = tod(of: frame(at: time, includeTilt: false))
        let target = current > 1 ? 0.0 : 2.0
        let duration = reduceMotion ? CityEventHeroMotion.reducedFade : CityEventHeroMotion.Night.toggle
        track(.tod, to: target, at: time, curve: reduceMotion ? .easeInOut(duration) : .nightTap(duration))
        if !reduceMotion {
            track(.title, to: 0, at: time, curve: .titleFlash(hold: duration))
        }
    }

    private func startShow(at time: Double) {
        if reduceMotion {
            reducedOffAt = nil
            stillOn.toggle()
            if !stillOn {
                track(.still, to: 0, at: time, curve: .easeInOut(0.4))
                reducedOffAt = time + 0.4
            } else {
                base.show = CityEventHeroMotion.LED_STILL
                base.still = 0
                tracks.removeValue(forKey: .show)
                tracks.removeValue(forKey: .still)
                tracks.removeValue(forKey: .tod)
                setTOD(2, in: &base)
                track(.still, to: 1, at: time, curve: .easeInOut(0.4))
            }
            return
        }
        guard !showRunning(at: time) else { return }
        let currentTOD = tod(of: frame(at: time, includeTilt: false))
        let wait = currentTOD >= 1.98 ? 0 : CityEventHeroMotion.CTA.dusk
        if wait > 0 { track(.tod, to: 2, at: time, curve: .easeInOut(wait)) }
        tracks[.show] = Track(from: -1, to: CityEventHeroMotion.SHOW_END, start: time, delay: wait, curve: .show)
        track(.camX, to: 0, at: time, curve: .sine(0.6))
        track(.camZ, to: 1, at: time, delay: wait, curve: .showCamera(riseStart: 0, riseEnd: 2.55))
        track(.camY, to: 1, at: time, delay: wait, curve: .showCamera(
            riseStart: CityEventHeroMotion.LED.avatarIn - 0.05, riseEnd: CityEventHeroMotion.LED.climbEnd))
        showEndsAt = time + wait + CityEventHeroMotion.SHOW_END
        showHapticTask?.cancel()
        showHapticTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(
                    for: .seconds(wait + CityEventHeroMotion.LED.fire + CityEventHeroMotion.Fire.bursts[0].at)
                )
            } catch { return }
            self?.burstHaptic += 1
        }
    }

    private func releaseView(commit: Bool, at time: Double) {
        track(.press, to: 0, at: time, curve: .spring(CityEventHeroMotion.Springs.ui))
        guard commit else { return }
        if !reduceMotion {
            track(.chevron, from: 0, to: 0, at: time, curve: .chevronNudge)
            track(.sweep, from: 0, to: 1, at: time, curve: .easeInOut(CityEventHeroMotion.View.sweep))
        }
        lightHaptic += 1
        startShow(at: time)
    }

    private func showRunning(at time: Double) -> Bool {
        let show = value(for: .show, in: frame(at: time, includeTilt: false))
        return show >= 0 && show < CityEventHeroMotion.SHOW_END
    }

    private func scheduleReturn() {
        resumeTask?.cancel()
        resumeTask = Task { @MainActor [weak self] in
            guard let self else { return }
            let now = Date.timeIntervalSinceReferenceDate
            let delay = max(CityEventHeroMotion.resumeAfter, (self.showEndsAt ?? now) - now)
            do { try await Task.sleep(for: .seconds(delay)) } catch { return }
            let start = Date.timeIntervalSinceReferenceDate
            self.beginReturn(at: start)
            do { try await Task.sleep(for: .seconds(CityEventHeroMotion.returnDuration)) } catch { return }
            self.finishReturn(at: Date.timeIntervalSinceReferenceDate)
        }
    }

    private func scheduleFreeze() {
        resumeTask?.cancel()
        resumeTask = Task { @MainActor [weak self] in
            guard let self else { return }
            let now = Date.timeIntervalSinceReferenceDate
            let duration = max(self.reduceMotion ? 0.8 : 2.0, (self.showEndsAt ?? now) - now + 0.9)
            do { try await Task.sleep(for: .seconds(duration)) } catch { return }
            self.base = self.frame(at: Date.timeIntervalSinceReferenceDate, includeTilt: false)
            self.tracks.removeAll()
            self.reducedSwap = nil
            self.reducedOffAt = nil
            self.mode = .rest
            self.isTicking = false
        }
    }

    private func beginReturn(at time: Double) {
        base = frame(at: time, includeTilt: false)
        tracks.removeAll()
        mode = .returning
        returnStarted = time
        isTicking = active
        reducedSwap = nil
        moveGuests(to: 0, at: time)
        setSpot(-1, at: time)
        isLiked = false
        track(.like, to: 0, at: time, curve: .easeOut(CityEventHeroMotion.Like.unfill))
        track(.count, to: 0, at: time, curve: .easeInOut(CityEventHeroMotion.Roll.count))
        track(.pop, to: 1, at: time, curve: .spring(CityEventHeroMotion.Springs.ui))
        track(.press, to: 0, at: time, curve: .spring(CityEventHeroMotion.Springs.ui))
        track(.chevron, to: 0, at: time, curve: .spring(CityEventHeroMotion.Springs.nudge))
        // RN clears day immediately at night; the grade lifts straight to the reference photo.
        if base.night > 0 { base.day = 0 }
        track(.day, to: 0, at: time, curve: .easeInOut(CityEventHeroMotion.returnDuration - 0.1))
        track(.night, to: 0, at: time, curve: .easeInOut(CityEventHeroMotion.returnDuration - 0.1))
        track(.mist, to: 0, at: time, curve: .easeInOut(CityEventHeroMotion.returnDuration - 0.1))
        track(.camX, to: 0, at: time, curve: .easeInOut(CityEventHeroMotion.returnDuration - 0.1))
        track(.camY, to: 0, at: time, curve: .easeInOut(CityEventHeroMotion.returnDuration - 0.1))
        track(.camZ, to: 0, at: time, curve: .easeInOut(CityEventHeroMotion.returnDuration - 0.1))
        track(.title, to: 0, at: time, curve: .easeInOut(CityEventHeroMotion.returnDuration - 0.1))
    }

    private func finishReturn(at time: Double) {
        returnStarted = nil
        base = .init()
        tracks.removeAll()
        reducedSwap = nil
        showEndsAt = nil
        showHapticTask?.cancel()
        if looping {
            autoStart = time
            mode = .automatic
            isTicking = active
        } else {
            mode = .rest
            isTicking = false
        }
    }

    private func track(_ key: Key, to target: Double, at time: Double, delay: Double = 0, curve: Curve) {
        let current = value(for: key, in: frame(at: time, includeTilt: false))
        tracks[key] = Track(from: current, to: target, start: time, delay: delay, curve: curve)
    }

    private func track(_ key: Key, from source: Double, to target: Double, at time: Double, delay: Double = 0, curve: Curve) {
        tracks[key] = Track(from: source, to: target, start: time, delay: delay, curve: curve)
    }

    private func value(for key: Key, in frame: CityEventHeroMotion.Frame) -> Double {
        switch key {
        case let .fan(index): frame.fan[index]
        case let .spot(index): frame.spot[index]
        case .nameProgress: frame.nameProgress
        case .like: frame.like
        case .pop: frame.pop
        case .ring: frame.ring
        case .count: frame.count
        case .press: frame.press
        case .chevron: frame.chevron
        case .sweep: frame.sweep
        case .day: frame.day
        case .night: frame.night
        case .mist: frame.mist
        case .tod: tod(of: frame)
        case .show: frame.show
        case .camX: frame.camX
        case .camY: frame.camY
        case .camZ: frame.camZ
        case .title: frame.title
        case .still: frame.still
        }
    }

    private func set(_ value: Double, for key: Key, in frame: inout CityEventHeroMotion.Frame) {
        switch key {
        case let .fan(index): frame.fan[index] = value
        case let .spot(index): frame.spot[index] = value
        case .nameProgress: frame.nameProgress = value
        case .like: frame.like = value
        case .pop: frame.pop = value
        case .ring: frame.ring = value
        case .count: frame.count = value
        case .press: frame.press = value
        case .chevron: frame.chevron = value
        case .sweep: frame.sweep = value
        case .day: frame.day = value
        case .night: frame.night = value
        case .mist: frame.mist = value
        case .tod: setTOD(value, in: &frame)
        case .show: frame.show = value
        case .camX: frame.camX = value
        case .camY: frame.camY = value
        case .camZ: frame.camZ = value
        case .title: frame.title = value
        case .still: frame.still = value
        }
    }

    private func tod(of frame: CityEventHeroMotion.Frame) -> Double {
        frame.night > 0 ? 1 + frame.night : frame.day
    }

    private func setTOD(_ value: Double, in frame: inout CityEventHeroMotion.Frame) {
        let clamped = clampTOD(value)
        frame.day = clamped < 1 ? clamped : 1
        frame.night = clamped > 1 ? clamped - 1 : 0
    }

    private func clampTOD(_ value: Double) -> Double { min(max(value, 0), 2) }

    private func rubber(_ value: Double, limit: Double) -> Double {
        let magnitude = abs(value)
        guard magnitude > limit else { return value }
        let over = magnitude - limit
        return (value < 0 ? -1 : 1) * (limit + over / (1 + over / limit))
    }

    private func updateMotionUpdates() {
        guard active, !reduceMotion else {
            motionManager.stopDeviceMotionUpdates()
            return
        }
        guard motionManager.isDeviceMotionAvailable, !motionManager.isDeviceMotionActive else { return }
        motionManager.deviceMotionUpdateInterval = 1.0 / 60
        motionManager.startDeviceMotionUpdates(using: .xArbitraryZVertical, to: .main) { [weak self] motion, _ in
            guard let motion else { return }
            let x = motion.gravity.x
            let y = motion.gravity.y
            let time = motion.timestamp
            // Core Motion's callback is not actor-annotated. Normalize once at the API boundary.
            DispatchQueue.main.async { [weak self] in self?.receiveGravity(x: x, y: y, at: time) }
        }
    }

    private func resetTilt() {
        gravityReady = false
        gravityX = 0
        gravityY = 0
        tiltX = 0
        tiltY = 0
        lastFrameTime = 0
    }

    private func receiveGravity(x: Double, y: Double, at time: Double) {
        guard active, !reduceMotion else { return }
        let delta = lastFrameTime > 0 ? max(0, time - lastFrameTime) : 0
        lastFrameTime = time
        if !gravityReady {
            gravityReady = true
            gravityX = x
            gravityY = y
        }
        let amount = min(1, delta / CityEventHeroMotion.Space.tiltTau)
        gravityX += (x - gravityX) * amount
        gravityY += (y - gravityY) * amount
        tiltX = (gravityX - x) * CityEventHeroMotion.Space.tilt
        tiltY = (y - gravityY) * CityEventHeroMotion.Space.tilt
        // Wake an idle, non-autoplay block on tilt; stop its timeline again below a subpixel
        // threshold. Sensor samples stay observation-ignored, so no per-frame view-state writes.
        if mode == .rest {
            let moving = abs(tiltX) + abs(tiltY) > 0.001
            if isTicking != moving { isTicking = moving }
        }
    }
}
