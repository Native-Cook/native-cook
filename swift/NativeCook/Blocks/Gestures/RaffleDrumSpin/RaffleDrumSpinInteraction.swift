import Foundation
import UIKit

@MainActor
final class RaffleDrumSpinInteraction: ObservableObject {
    struct State {
        let time: Double
        let story: RaffleDrumSpinMotion.Story
        let manualSpin: Double
        let manualSpeed: Double
        let idle: Double
    }

    struct Snapshot {
        let current: State
        let previous: State?
        let fade: Double
        var time: Double { current.time }
        var story: RaffleDrumSpinMotion.Story { current.story }
    }

    @Published private(set) var reducedTransitionActive = false
    @Published private(set) var isInteracting = false

    private var driver = RaffleDrumSpinMotion.makeDriver()
    private var clock = 0.0
    private var lastTimestamp: TimeInterval?
    private var stripGrab = 0.0
    private var crankLastY = 0.0
    private var reducedPrevious: State?
    private var reducedFadeStarted = 0.0
    private var reducedFadeTask: Task<Void, Never>?
    private var lastRatchetAt = -100.0
    private var scriptStarted = false
    private var scriptStep = 0
    private var scriptStepAt = 0.0
    private var scriptCrankStarted = 0.0
    private let audio = RaffleDrumSpinAudio()
#if DEBUG
    private let performance = RaffleDrumSpinPerformance()
#endif

    init() {
#if DEBUG
        isInteracting = ProcessInfo.processInfo.arguments.contains("-raffleScript")
#endif
    }

    func snapshot(at timestamp: TimeInterval, autoPlay: Bool, reducedMotion: Bool) -> Snapshot {
        let dt = lastTimestamp.map { max(0, timestamp - $0) } ?? 1.0 / 60.0
        lastTimestamp = timestamp
        clock += dt
        let before = driver.t
        let tickBefore = Int(floor((RaffleDrumSpinMotion.cageAngle(driver.t) + driver.spin) / (2 * .pi / 7)))
        if !reducedMotion { RaffleDrumSpinMotion.stepDriver(&driver, dt, clock, autoPlay) }
        runScriptIfNeeded()
#if DEBUG
        performance.record(at: timestamp, storyTime: driver.t, mode: driver.mode)
#endif
        let tickNow = Int(floor((RaffleDrumSpinMotion.cageAngle(driver.t) + driver.spin) / (2 * .pi / 7)))
        // The site's recording plays the sounds a touch would make (DemoSound).
        let recordingSound = DemoSound.inAutoPlay && autoPlay && !reducedMotion
        if driver.mode != 0 || (recordingSound && driver.t >= before), tickNow != tickBefore,
            clock - lastRatchetAt > 0.06
        {
            lastRatchetAt = clock
            if driver.mode != 0 { UISelectionFeedbackGenerator().selectionChanged() }
            audio.play(.ratchet)
        }
        playCues(from: before, to: driver.t, recordingSound: recordingSound)
        let current = state(of: driver)
        let rawFade = reducedMotion ? min(1, max(0, (timestamp - reducedFadeStarted) / 0.2)) : 1
        let fade = RaffleDrumSpinMotion.easeOut(rawFade)
        if fade >= 1 { reducedPrevious = nil }
        return Snapshot(current: current, previous: reducedPrevious, fade: fade)
    }

    func pinned(_ time: Double) -> Snapshot {
        let state = State(time: time, story: .init(), manualSpin: 0, manualSpeed: 0, idle: 0)
        return Snapshot(current: state, previous: nil, fade: 1)
    }

    private func state(of value: RaffleDrumSpinMotion.Driver) -> State {
        State(time: value.t, story: RaffleDrumSpinMotion.storyOf(value), manualSpin: value.spin,
              manualSpeed: min(1, abs(value.spinV) / 14), idle: value.idle)
    }

    private func beginReducedTransition(_ update: (inout RaffleDrumSpinMotion.Driver) -> Void) {
        reducedPrevious = state(of: driver)
        lastTimestamp = nil
        reducedFadeStarted = Date().timeIntervalSinceReferenceDate
        reducedTransitionActive = true
        reducedFadeTask?.cancel()
        reducedFadeTask = Task { [weak self] in
            do { try await Task.sleep(for: .milliseconds(220)) } catch { return }
            self?.reducedTransitionActive = false
        }
        update(&driver)
        driver.mode = 2
        driver.last = clock
    }

    func cardPressBegan(_ card: Int, reducedMotion: Bool) {
        if !isInteracting { isInteracting = true }
        guard !reducedMotion else { return }
        RaffleDrumSpinMotion.pressIn(&driver, card)
        driver.last = clock
    }

    func setSceneActive(_ value: Bool) { audio.setActive(value) }

    func cancelPress() { RaffleDrumSpinMotion.pressOut(&driver) }

    func cardPressEnded(_ card: Int, reducedMotion: Bool) {
        if reducedMotion {
            beginReducedTransition { value in value.from = card; value.to = card; value.t = 0 }
            return
        }
        RaffleDrumSpinMotion.pressOut(&driver)
        if RaffleDrumSpinMotion.selectCard(&driver, card, clock) {
            UISelectionFeedbackGenerator().selectionChanged()
            audio.play(.select)
        }
    }

    func playPressBegan(x: Double, reducedMotion: Bool) {
        if !isInteracting { isInteracting = true }
        guard !reducedMotion else { return }
        RaffleDrumSpinMotion.pressIn(&driver, 3, x)
        driver.last = clock
    }

    func playPressEnded(x: Double, reducedMotion: Bool) {
        if reducedMotion {
            beginReducedTransition { value in
                let waiting = value.t >= RaffleDrumSpinMotion.T.hold - 0.01 && value.t < RaffleDrumSpinMotion.T.claimPress.lowerBound
                value.t = waiting ? RaffleDrumSpinMotion.T.reselect.lowerBound - 0.05 : RaffleDrumSpinMotion.T.hold
            }
            return
        }
        RaffleDrumSpinMotion.pressOut(&driver)
        driver.pressX = x
        if RaffleDrumSpinMotion.pressPlay(&driver, clock) {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }
    }

    func stripBegan() {
        if !isInteracting { isInteracting = true }
        RaffleDrumSpinMotion.grabStrip(&driver, clock)
        stripGrab = driver.strip
    }

    func stripChanged(translation: Double) {
        driver.strip = RaffleDrumSpinMotion.rubberBand(stripGrab + translation)
        driver.last = clock
    }

    func stripEnded(velocity: Double) { RaffleDrumSpinMotion.releaseStrip(&driver, velocity, clock) }

    func crankBegan(y: Double) {
        if !isInteracting { isInteracting = true }
        RaffleDrumSpinMotion.touched(&driver, clock)
        driver.spinV = 0
        crankLastY = y
    }

    func crankChanged(y: Double, sx: Double) {
        let dy = y - crankLastY
        crankLastY = y
        driver.spin += (dy / (RaffleDrumSpinMotion.Layout.drumRadius * sx)) * 1.2
        driver.last = clock
    }

    func crankEnded(velocityY: Double, sx: Double) {
        driver.spinV = max(-24, min(24, (velocityY / (RaffleDrumSpinMotion.Layout.drumRadius * sx)) * 1.2))
        driver.last = clock
        if abs(driver.spinV) > 3 { UISelectionFeedbackGenerator().selectionChanged() }
    }

    func ticketBegan() -> Bool {
        if !isInteracting { isInteracting = true }
        guard RaffleDrumSpinMotion.canTilt(driver.t) else { return false }
        RaffleDrumSpinMotion.grabTicket(&driver, clock)
        UISelectionFeedbackGenerator().selectionChanged()
        return true
    }

    func ticketChanged(dx: Double, dy: Double) {
        guard driver.tilting else { return }
        RaffleDrumSpinMotion.tiltTicket(&driver, dx, dy, clock)
    }

    func ticketEnded(vx: Double, vy: Double) {
        guard driver.tilting else { return }
        RaffleDrumSpinMotion.releaseTicket(&driver, vx, vy, clock)
    }

    private func playCues(from before: Double, to time: Double, recordingSound: Bool) {
        guard driver.mode != 0 || recordingSound, time >= before else { return }
        // In the self-playing loop (mode 0) the cues sound but the haptics stay off.
        let haptics = driver.mode != 0
        func crossed(_ mark: Double) -> Bool { before < mark && time >= mark }
        for index in 0...driver.to where crossed(RaffleDrumSpinMotion.T.fly.upperBound + Double(index) * RaffleDrumSpinMotion.T.flyStagger) {
            if haptics { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            audio.play(.land)
        }
        if crossed(RaffleDrumSpinMotion.T.spin.lowerBound) { audio.play(.spin) }
        if crossed(RaffleDrumSpinMotion.T.eject.lowerBound) { audio.play(.pop) }
        if crossed(RaffleDrumSpinMotion.T.stitches.lowerBound) { audio.play(.stitches) }
        if crossed(RaffleDrumSpinMotion.T.morph.lowerBound) { audio.play(.morph) }
        if crossed(RaffleDrumSpinMotion.T.burst) {
            if haptics { UINotificationFeedbackGenerator().notificationOccurred(.success) }
            audio.play(.burst)
        }
        for index in 0..<12 {
            let k = Double(index) / 12
            let mark = RaffleDrumSpinMotion.T.number.lowerBound + (RaffleDrumSpinMotion.T.number.upperBound - RaffleDrumSpinMotion.T.number.lowerBound) * (1 - (1-k) * (1-k))
            if crossed(mark) { audio.play(.tick) }
        }
        if crossed(RaffleDrumSpinMotion.T.tear.lowerBound) { audio.play(.tear) }
        if crossed(RaffleDrumSpinMotion.T.burn.lowerBound + 0.08) { audio.play(.dust) }
        if crossed(RaffleDrumSpinMotion.T.tear.upperBound) {
            if haptics { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            audio.play(.landStub)
        }
    }

    private func runScriptIfNeeded() {
#if DEBUG
        guard ProcessInfo.processInfo.arguments.contains("-raffleScript") else { return }
        if !scriptStarted { scriptStarted = true; scriptStepAt = clock + 0.45 }
        guard clock >= scriptStepAt else { return }
        switch scriptStep {
        case 0:
            cardPressBegan(1, reducedMotion: false)
            cardPressEnded(1, reducedMotion: false)
            scriptStepAt = clock + 0.6
        case 1:
            stripBegan()
            stripChanged(translation: -92)
            stripEnded(velocity: -520)
            scriptStepAt = clock + 0.7
        case 2:
            playPressBegan(x: 184, reducedMotion: false)
            playPressEnded(x: 184, reducedMotion: false)
            scriptStepAt = clock + 0.7
        case 3:
            crankBegan(y: 0)
            scriptCrankStarted = clock
            scriptStepAt = clock + 1.0 / 60
        case 4:
            let progress = min(1, (clock - scriptCrankStarted) / 0.6)
            let y = progress * 2 * Double.pi * RaffleDrumSpinMotion.Layout.drumRadius / 1.2
            crankChanged(y: y, sx: 1)
            if progress < 1 { scriptStepAt = clock + 1.0 / 60; return }
            crankEnded(velocityY: 7.5 * RaffleDrumSpinMotion.Layout.drumRadius / 1.2, sx: 1)
            scriptStepAt = clock + 3.7
        case 5:
            if ticketBegan() {
                ticketChanged(dx: 70, dy: -35)
                ticketEnded(vx: -120, vy: 80)
                scriptStepAt = clock + 0.8
            } else { scriptStepAt = clock + 0.1; return }
        case 6:
            guard driver.t >= RaffleDrumSpinMotion.T.hold - 0.01 else {
                scriptStepAt = clock + 0.1
                return
            }
            playPressBegan(x: 184, reducedMotion: false)
            playPressEnded(x: 184, reducedMotion: false)
            scriptStepAt = .infinity
        default: break
        }
        scriptStep += 1
#endif
    }
}
