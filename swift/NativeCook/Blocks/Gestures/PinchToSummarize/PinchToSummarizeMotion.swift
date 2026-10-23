import Observation
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

private struct PinchToSummarizeAnimatedScalar {
    private enum Motion {
        case still
        case spring(target: Double, damping: Double, stiffness: Double, mass: Double, initialEnergy: Double)
        case timing(from: Double, target: Double, elapsed: Double, duration: Double, linear: Bool)
    }

    private(set) var value: Double
    private(set) var velocity: Double = 0
    private var motion: Motion = .still

    init(_ value: Double) {
        self.value = value
    }

    var isMoving: Bool {
        if case .still = motion { return false }
        return true
    }

    mutating func set(_ next: Double) {
        value = next
        velocity = 0
        motion = .still
    }

    mutating func cancel() {
        motion = .still
    }

    mutating func spring(
        to target: Double,
        parameters: PinchToSummarizeConstants.Springs.Spring,
        velocity suppliedVelocity: Double = 0,
        carriesCurrentVelocity: Bool = true
    ) {
        let carried = carriesCurrentVelocity ? velocity : 0
        velocity = carried + suppliedVelocity
        let displacement = value - target
        let energy = 0.5 * parameters.stiffness * displacement * displacement
            + 0.5 * parameters.mass * velocity * velocity
        guard energy > 1e-12 else {
            set(target)
            return
        }
        motion = .spring(
            target: target,
            damping: parameters.damping,
            stiffness: parameters.stiffness,
            mass: parameters.mass,
            initialEnergy: energy
        )
    }

    mutating func timing(to target: Double, durationMS: Double, linear: Bool = false) {
        guard durationMS > 0 else {
            set(target)
            return
        }
        velocity = 0
        motion = .timing(from: value, target: target, elapsed: 0, duration: durationMS / 1000, linear: linear)
    }

    /// Reanimated's physical spring equation, integrated in small fixed steps. Starting a new spring
    /// can carry this scalar's current velocity, so an interrupted collapse does not visibly restart.
    mutating func step(_ seconds: Double) -> Bool {
        switch motion {
        case .still:
            return false

        case let .timing(from, target, elapsed, duration, linear):
            let nextElapsed = elapsed + seconds
            let u = min(1, max(0, nextElapsed / duration))
            let eased = linear ? u : (u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u))
            value = from + (target - from) * eased
            if u >= 1 {
                value = target
                velocity = 0
                motion = .still
                return true
            }
            motion = .timing(from: from, target: target, elapsed: nextElapsed, duration: duration, linear: linear)
            return false

        case let .spring(target, damping, stiffness, mass, initialEnergy):
            let x0 = value - target
            let v0 = velocity
            let omega0 = sqrt(stiffness / mass)
            let zeta = damping / (2 * sqrt(stiffness * mass))
            if zeta < 1 {
                let omegaD = omega0 * sqrt(1 - zeta * zeta)
                let sine = sin(omegaD * seconds)
                let cosine = cos(omegaD * seconds)
                let envelope = exp(-zeta * omega0 * seconds)
                let coefficient = (v0 + zeta * omega0 * x0) / omegaD
                let displacement = envelope * (x0 * cosine + coefficient * sine)
                value = target + displacement
                velocity = envelope * (
                    v0 * cosine
                        - ((zeta * omega0 * v0 + omega0 * omega0 * x0) / omegaD) * sine
                )
            } else {
                // All shipped springs are underdamped; this keeps the scalar well-defined if a future
                // constant reaches critical damping.
                let envelope = exp(-omega0 * seconds)
                let coefficient = v0 + omega0 * x0
                let displacement = envelope * (x0 + coefficient * seconds)
                value = target + displacement
                velocity = envelope * (v0 - omega0 * coefficient * seconds)
            }
            let displacement = value - target
            let energy = 0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
            if energy / initialEnergy <= 6e-9 || (abs(displacement) < 0.0001 && abs(velocity) < 0.001) {
                value = target
                velocity = 0
                motion = .still
                return true
            }
            return false
        }
    }
}

private struct PinchToSummarizePinchState {
    var active = false
    var out = false
    var startP = 0.0
    var focalX = 0.0
    var focalY = 0.0
    var track = 0.0
    var armed = false
    var scripted = false
    var lastScale = 1.0
}

private struct PinchToSummarizeScriptState {
    var time = 0.0
    var paused = false
    var realDown = false
    var quiet = 0.0
    var segment = 0
    var returningToRest = false
}

private struct PinchToSummarizeScheduledEvent {
    enum Action {
        case arrive
        case reducedCollapseSwap
        case reducedRestoreSwap
        case rimFlashOut
    }

    var due: Double
    var job: Int
    var action: Action
    var scripted: Bool
}

@Observable
final class PinchToSummarizeMotion {
    private(set) var p: CGFloat = 0
    private(set) var lift: CGFloat = 0
    private(set) var driftX: CGFloat = 0
    private(set) var driftY: CGFloat = 0
    private(set) var think: CGFloat = 0
    private(set) var rim: CGFloat = 0
    private(set) var reveal: CGFloat = 0
    private(set) var summaryAlpha: CGFloat = 0
    private(set) var pageAlpha: CGFloat = 1
    private(set) var clock: CGFloat = 0
    private(set) var bloomAt: CGFloat = -1
    private(set) var fingerAX: CGFloat = 0
    private(set) var fingerAY: CGFloat = 0
    private(set) var fingerBX: CGFloat = 0
    private(set) var fingerBY: CGFloat = 0
    private(set) var fingerAlpha: CGFloat = 0
    private(set) var mode: PinchToSummarizeMode = .reading
    private(set) var scrollLocked = false

    var autoPlay: Bool

    @ObservationIgnored private var stage = PinchToSummarizeStage(width: 0, height: 0, insetTop: 0)
    private var reducedMotion = false
    @ObservationIgnored private var revealTotal: Double = 1_470
    @ObservationIgnored private var lastDate: Date?
    @ObservationIgnored private var elapsedMS = 0.0
    @ObservationIgnored private var job = 0
    @ObservationIgnored private var pinch = PinchToSummarizePinchState()
    @ObservationIgnored private var script = PinchToSummarizeScriptState()
    @ObservationIgnored private var events: [PinchToSummarizeScheduledEvent] = []
    @ObservationIgnored private var pendingLandHaptic = false
    @ObservationIgnored private var pendingRestoreSettle = false

    @ObservationIgnored private var pValue = PinchToSummarizeAnimatedScalar(0)
    @ObservationIgnored private var liftValue = PinchToSummarizeAnimatedScalar(0)
    @ObservationIgnored private var driftXValue = PinchToSummarizeAnimatedScalar(0)
    @ObservationIgnored private var driftYValue = PinchToSummarizeAnimatedScalar(0)
    @ObservationIgnored private var thinkValue = PinchToSummarizeAnimatedScalar(0)
    @ObservationIgnored private var rimValue = PinchToSummarizeAnimatedScalar(0)
    @ObservationIgnored private var revealValue = PinchToSummarizeAnimatedScalar(0)
    @ObservationIgnored private var summaryAlphaValue = PinchToSummarizeAnimatedScalar(0)
    @ObservationIgnored private var pageAlphaValue = PinchToSummarizeAnimatedScalar(1)
    @ObservationIgnored private var fingerAlphaValue = PinchToSummarizeAnimatedScalar(0)

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
    }

    var snapshot: PinchToSummarizeSnapshot {
        PinchToSummarizeSnapshot(
            p: p,
            lift: lift,
            driftX: driftX,
            driftY: driftY,
            think: think,
            rim: rim,
            reveal: reveal,
            summaryAlpha: summaryAlpha,
            pageAlpha: pageAlpha,
            clock: clock,
            bloomAt: bloomAt,
            fingerAX: fingerAX,
            fingerAY: fingerAY,
            fingerBX: fingerBX,
            fingerBY: fingerBY,
            fingerAlpha: fingerAlpha,
            mode: mode
        )
    }

    var needsFrames: Bool {
        if reducedMotion {
            return mode == .thinking || valuesAreMoving || !events.isEmpty
        }
        if autoPlay { return true }
        return valuesAreMoving || mode == .thinking || pValue.value > 0.001 || !events.isEmpty
    }

    func configure(
        stage: PinchToSummarizeStage,
        reduceMotion: Bool,
        revealTotal: CGFloat
    ) {
        self.stage = stage
        self.revealTotal = max(1, Double(revealTotal))
        guard reduceMotion != self.reducedMotion else { return }
        self.reducedMotion = reduceMotion
        lastDate = nil
        if reduceMotion {
            script.paused = true
            script.segment = 0
            pinch.active = false
            fingerAlphaValue.set(0)
            rimValue.set(0)
            driftXValue.set(0)
            driftYValue.set(0)
            if mode == .reading {
                pValue.set(0)
                liftValue.set(0)
            } else {
                pValue.set(1)
                liftValue.set(1)
            }
            publishValues()
        } else if autoPlay {
            resetToRest(restartScript: true)
        }
    }

    @discardableResult
    func step(to date: Date) -> Bool {
        guard stage.width > 0, stage.height > 0 else {
            lastDate = date
            return false
        }
        guard let previous = lastDate else {
            lastDate = date
            publishValues()
            return true
        }

        let wallMS = max(date.timeIntervalSince(previous) * 1_000, 0)
        let dtMS = min(wallMS, PinchToSummarizeConstants.Timing.maxFrameMs)
        lastDate = date
        elapsedMS += dtMS
        let dt = dtMS / 1_000

        let pFinished = pValue.step(dt)
        _ = liftValue.step(dt)
        _ = driftXValue.step(dt)
        _ = driftYValue.step(dt)
        _ = thinkValue.step(dt)
        _ = rimValue.step(dt)
        _ = revealValue.step(dt)
        _ = summaryAlphaValue.step(dt)
        _ = pageAlphaValue.step(dt)
        _ = fingerAlphaValue.step(dt)

        if pFinished, pendingLandHaptic {
            pendingLandHaptic = false
            playHaptic(.land)
        }
        if pFinished, pendingRestoreSettle {
            pendingRestoreSettle = false
            finishRestore()
        }

        runDueEvents()

        if !reducedMotion, pValue.value > 0.001 || thinkValue.value > 0 {
            clock += CGFloat(wallMS)
        }

        if autoPlay, !reducedMotion {
            if script.paused {
                stepPausedScript(dtMS: wallMS)
            } else {
                runScript(dtMS: wallMS)
            }
        }

        publishValues()
        return true
    }

    func pinchBegin(focal: CGPoint, scripted: Bool) {
        guard stage.width > 0, stage.height > 0 else { return }
        if !scripted { lastDate = nil }
        let out = mode != .reading
        pValue.cancel()
        driftXValue.cancel()
        driftYValue.cancel()
        pinch = PinchToSummarizePinchState(
            active: true,
            out: out,
            startP: pValue.value,
            focalX: focal.x,
            focalY: focal.y,
            track: pValue.value,
            armed: false,
            scripted: scripted,
            lastScale: 1
        )
        if !out { scrollLocked = true }
    }

    func pinchMove(scale: CGFloat, focal: CGPoint) {
        guard pinch.active else { return }
        let g = pinch
        let scale = Double(scale)
        let raw: Double
        if g.out {
            raw = g.startP - (scale - 1) / (PinchToSummarizeConstants.Pinch.fullOut - 1)
        } else {
            raw = g.startP + (1 - scale) / (1 - PinchToSummarizeConstants.Pinch.fullIn)
        }
        let track = rubber(raw)
        let armed = g.out
            ? track < PinchToSummarizeConstants.Pinch.restoreAt
            : track > PinchToSummarizeConstants.Pinch.commitAt
        if armed != g.armed {
            buzz(armed ? .armed : .disarmed, scripted: g.scripted)
        }
        pinch.track = track
        pinch.armed = armed
        pinch.lastScale = scale

        guard !reducedMotion else { return }
        pValue.set(track)
        driftXValue.set((Double(focal.x) - g.focalX) * PinchToSummarizeConstants.Pinch.follow)
        driftYValue.set((Double(focal.y) - g.focalY) * PinchToSummarizeConstants.Pinch.follow)
        if g.out {
            summaryAlphaValue.set(PinchToSummarizeGeometry.clamp01(CGFloat(
                (track - PinchToSummarizeConstants.Pinch.restoreAt)
                    / (PinchToSummarizeConstants.Timing.summaryOutStart - PinchToSummarizeConstants.Pinch.restoreAt)
            )).double)
            rimValue.set(armed
                ? PinchToSummarizeConstants.Rim.pinchMax + PinchToSummarizeConstants.Rim.armedBoost
                : 0)
        } else {
            let progress = min(1, max(0, track / PinchToSummarizeConstants.Pinch.commitAt))
            rimValue.set(
                progress * PinchToSummarizeConstants.Rim.pinchMax
                    + (armed ? PinchToSummarizeConstants.Rim.armedBoost : 0)
            )
        }
        publishValues()
    }

    func pinchEnd(velocity: CGFloat, cancelled: Bool) {
        guard pinch.active else { return }
        let g = pinch
        pinch.active = false
        let velocity = velocity.isFinite ? Double(velocity) : 0
        if !g.out {
            let projected = g.track + project(-velocity / (1 - PinchToSummarizeConstants.Pinch.fullIn))
            if !cancelled, projected > PinchToSummarizeConstants.Pinch.commitAt {
                collapse(velocity: velocity, scripted: g.scripted)
            } else {
                scrollLocked = false
                rimValue.timing(to: 0, durationMS: PinchToSummarizeConstants.Timing.rimLetGo)
                pValue.spring(
                    to: 0,
                    parameters: PinchToSummarizeConstants.Springs.restore,
                    velocity: -velocity * PinchToSummarizeConstants.Pinch.velocityToProgress,
                    carriesCurrentVelocity: false
                )
                driftXValue.spring(to: 0, parameters: PinchToSummarizeConstants.Springs.drift)
                driftYValue.spring(to: 0, parameters: PinchToSummarizeConstants.Springs.drift)
            }
            return
        }

        let projected = g.track + project(-velocity / (PinchToSummarizeConstants.Pinch.fullOut - 1))
        if !cancelled, projected < PinchToSummarizeConstants.Pinch.restoreAt {
            restore(velocity: velocity, scripted: g.scripted)
        } else {
            rimValue.timing(to: 0, durationMS: PinchToSummarizeConstants.Timing.rimLetGo)
            summaryAlphaValue.timing(
                to: mode == .summary ? 1 : 0,
                durationMS: PinchToSummarizeConstants.Timing.reducedFade
            )
            pValue.spring(
                to: 1,
                parameters: PinchToSummarizeConstants.Springs.collapse,
                velocity: -velocity * PinchToSummarizeConstants.Pinch.velocityToProgress,
                carriesCurrentVelocity: false
            )
            driftXValue.spring(to: 0, parameters: PinchToSummarizeConstants.Springs.drift)
            driftYValue.spring(to: 0, parameters: PinchToSummarizeConstants.Springs.drift)
        }
    }

    /// A real touch stops the script immediately. This Swift port intentionally waits two seconds,
    /// returns to rest, then restarts; RN keeps its shipped 3.5 s state-relative resume constant.
    func touch(_ down: Bool) {
        lastDate = nil
        guard autoPlay, !reducedMotion else { return }
        if down, !script.paused, pinch.active, pinch.scripted {
            pinchEnd(velocity: 0, cancelled: true)
        }
        if down {
            fingerAlphaValue.timing(to: 0, durationMS: PinchToSummarizeConstants.Timing.fingerFade)
        }
        script.paused = true
        script.realDown = down
        script.quiet = 0
        script.segment = 0
        script.returningToRest = false
    }

    func summarize() {
        touch(false)
        if mode == .reading {
            collapse(velocity: 0, scripted: false)
        }
    }

    func showArticle() {
        touch(false)
        if mode != .reading {
            restore(velocity: 0, scripted: false)
        }
    }

    func cancelGesture() {
        if pinch.active {
            pinchEnd(velocity: 0, cancelled: true)
        }
        touch(false)
    }

    private var valuesAreMoving: Bool {
        pValue.isMoving || liftValue.isMoving || driftXValue.isMoving || driftYValue.isMoving
            || thinkValue.isMoving || rimValue.isMoving || revealValue.isMoving
            || summaryAlphaValue.isMoving || pageAlphaValue.isMoving || fingerAlphaValue.isMoving
    }

    private func collapse(velocity: Double, scripted: Bool) {
        job += 1
        let token = job
        mode = .thinking
        scrollLocked = true
        driftXValue.spring(to: 0, parameters: PinchToSummarizeConstants.Springs.drift)
        driftYValue.spring(to: 0, parameters: PinchToSummarizeConstants.Springs.drift)

        if reducedMotion {
            liftValue.set(1)
            pageAlphaValue.timing(to: 0, durationMS: PinchToSummarizeConstants.Timing.reducedFade)
            schedule(.reducedCollapseSwap, after: PinchToSummarizeConstants.Timing.reducedFade, job: token, scripted: scripted)
        } else {
            liftValue.set(0)
            pValue.spring(
                to: 1,
                parameters: PinchToSummarizeConstants.Springs.collapse,
                velocity: -velocity * PinchToSummarizeConstants.Pinch.velocityToProgress,
                carriesCurrentVelocity: false
            )
            pendingLandHaptic = !scripted
            rimValue.timing(to: PinchToSummarizeConstants.Rim.think, durationMS: PinchToSummarizeConstants.Timing.thinkIn)
        }
        thinkValue.timing(to: 1, durationMS: PinchToSummarizeConstants.Timing.thinkIn)
        if PinchToSummarizeConstants.qaHold != "a" {
            schedule(
                .arrive,
                after: PinchToSummarizeConstants.Timing.thinkMs + PinchToSummarizeConstants.Timing.tick,
                job: token,
                scripted: scripted
            )
        }
    }

    private func arrive(token: Int, scripted: Bool) {
        guard job == token, mode == .thinking else { return }
        mode = .summary
        buzz(.ready, scripted: scripted)
        if !reducedMotion { bloomAt = clock }
        rimValue.timing(
            to: 0,
            durationMS: reducedMotion ? 0 : PinchToSummarizeConstants.Timing.rimOut
        )
        thinkValue.timing(
            to: 0,
            durationMS: reducedMotion
                ? PinchToSummarizeConstants.Timing.reducedFade
                : PinchToSummarizeConstants.Timing.rimOut
        )
        if reducedMotion {
            liftValue.set(1)
        } else {
            liftValue.spring(to: 1, parameters: PinchToSummarizeConstants.Springs.lift)
        }
        summaryAlphaValue.set(1)
        revealValue.set(0)
        revealValue.timing(
            to: revealTotal,
            durationMS: reducedMotion ? PinchToSummarizeConstants.Timing.reducedFade : revealTotal,
            linear: true
        )
        #if canImport(UIKit)
        UIAccessibility.post(notification: .announcement, argument: "Summary ready")
        #endif
    }

    private func restore(velocity: Double, scripted: Bool) {
        job += 1
        let token = job
        mode = .reading
        pendingLandHaptic = false
        buzz(.restore, scripted: scripted)
        thinkValue.timing(to: 0, durationMS: PinchToSummarizeConstants.Timing.reducedFade)
        summaryAlphaValue.timing(to: 0, durationMS: PinchToSummarizeConstants.Timing.reducedFade)
        driftXValue.spring(to: 0, parameters: PinchToSummarizeConstants.Springs.drift)
        driftYValue.spring(to: 0, parameters: PinchToSummarizeConstants.Springs.drift)
        if reducedMotion {
            rimValue.set(0)
            pageAlphaValue.timing(to: 0, durationMS: PinchToSummarizeConstants.Timing.reducedFade)
            schedule(.reducedRestoreSwap, after: PinchToSummarizeConstants.Timing.reducedFade, job: token, scripted: scripted)
            return
        }

        rimValue.timing(
            to: PinchToSummarizeConstants.Rim.pinchMax + PinchToSummarizeConstants.Rim.armedBoost,
            durationMS: PinchToSummarizeConstants.Timing.rimFlashIn
        )
        schedule(.rimFlashOut, after: PinchToSummarizeConstants.Timing.rimFlashIn, job: token, scripted: scripted)
        pValue.spring(
            to: 0,
            parameters: PinchToSummarizeConstants.Springs.restore,
            velocity: -velocity * PinchToSummarizeConstants.Pinch.velocityToProgress,
            carriesCurrentVelocity: false
        )
        pendingRestoreSettle = true
    }

    private func finishRestore() {
        liftValue.set(0)
        revealValue.set(0)
        scrollLocked = false
    }

    private func schedule(
        _ action: PinchToSummarizeScheduledEvent.Action,
        after delay: Double,
        job: Int,
        scripted: Bool
    ) {
        events.append(PinchToSummarizeScheduledEvent(
            due: elapsedMS + delay,
            job: job,
            action: action,
            scripted: scripted
        ))
    }

    private func runDueEvents() {
        guard !events.isEmpty else { return }
        let ready = events.filter { $0.due <= elapsedMS }
        events.removeAll { $0.due <= elapsedMS || $0.job != job }
        for event in ready where event.job == job {
            switch event.action {
            case .arrive:
                arrive(token: event.job, scripted: event.scripted)
            case .reducedCollapseSwap:
                pValue.set(1)
                pageAlphaValue.timing(to: 1, durationMS: PinchToSummarizeConstants.Timing.reducedFade)
            case .reducedRestoreSwap:
                pValue.set(0)
                finishRestore()
                pageAlphaValue.timing(to: 1, durationMS: PinchToSummarizeConstants.Timing.reducedFade)
            case .rimFlashOut:
                rimValue.timing(to: 0, durationMS: PinchToSummarizeConstants.Timing.rimFlashOut)
            }
        }
    }

    private func runScript(dtMS: Double) {
        let autoplay = PinchToSummarizeConstants.Autoplay.self
        let time = script.time
        let inPinch = time >= autoplay.pinchInStart && time < autoplay.pinchInEnd
        let outPinch = time >= autoplay.pinchOutStart && time < autoplay.pinchOutEnd
        let segment = inPinch ? 1 : outPinch ? 2 : 0
        let centerX = Double(stage.width / 2)
        let centerInY = Double(stage.height * 0.46)
        let centerOutY = Double(PinchToSummarizeTheme.Layout.summaryTop + PinchToSummarizeTheme.Layout.cardHeight / 2)

        if segment != script.segment {
            if script.segment != 0 {
                if script.segment == 1 {
                    placeFingers(x: centerX, y: centerInY, spread: autoplay.spreadIn * autoplay.pinchInScale)
                    pinchMove(
                        scale: autoplay.pinchInScale.cgFloat,
                        focal: CGPoint(x: centerX.cgFloat, y: centerInY.cgFloat)
                    )
                } else {
                    placeFingers(x: centerX, y: centerOutY, spread: autoplay.spreadOut * autoplay.pinchOutScale)
                    pinchMove(
                        scale: autoplay.pinchOutScale.cgFloat,
                        focal: CGPoint(x: centerX.cgFloat, y: centerOutY.cgFloat)
                    )
                }
                pinchEnd(velocity: 0, cancelled: false)
                fingerAlphaValue.timing(to: 0, durationMS: PinchToSummarizeConstants.Timing.fingerFade)
            }
            if segment != 0 {
                let centerY = segment == 1 ? centerInY : centerOutY
                placeFingers(
                    x: centerX,
                    y: centerY,
                    spread: segment == 1 ? autoplay.spreadIn : autoplay.spreadOut
                )
                fingerAlphaValue.timing(to: 1, durationMS: PinchToSummarizeConstants.Timing.fingerFade)
                pinchBegin(focal: CGPoint(x: centerX.cgFloat, y: centerY.cgFloat), scripted: true)
            }
        }

        if segment == 1 {
            let u = ease((time - autoplay.pinchInStart) / (autoplay.pinchInEnd - autoplay.pinchInStart))
            let scale = mix(1, autoplay.pinchInScale, u)
            placeFingers(x: centerX, y: centerInY, spread: autoplay.spreadIn * scale)
            pinchMove(scale: scale.cgFloat, focal: CGPoint(x: centerX.cgFloat, y: centerInY.cgFloat))
        } else if segment == 2 {
            let u = ease((time - autoplay.pinchOutStart) / (autoplay.pinchOutEnd - autoplay.pinchOutStart))
            let scale = mix(1, autoplay.pinchOutScale, u)
            placeFingers(x: centerX, y: centerOutY, spread: autoplay.spreadOut * scale)
            pinchMove(scale: scale.cgFloat, focal: CGPoint(x: centerX.cgFloat, y: centerOutY.cgFloat))
        }

        var next = time + dtMS
        if PinchToSummarizeConstants.qaHold == "rest" {
            next = 0
        } else if PinchToSummarizeConstants.qaHold != nil, time >= autoplay.qaHoldAt {
            next = time
        }
        if next >= PinchToSummarizeConstants.loopMS {
            next -= PinchToSummarizeConstants.loopMS
            clock = 0
            // The RN source retains its old bloom timestamp here, which redraws the arrival ring at
            // the seam. Clear it so the required final frame is exactly the initial resting frame.
            bloomAt = -1
        }
        script.time = next
        script.segment = segment
    }

    private func stepPausedScript(dtMS: Double) {
        if script.realDown {
            script.quiet = 0
            return
        }
        script.quiet += dtMS
        let resumeDelayMS = 2_000.0
        guard script.quiet >= resumeDelayMS else { return }

        if !script.returningToRest {
            script.returningToRest = true
            job += 1
            events.removeAll()
            pendingLandHaptic = false
            fingerAlphaValue.timing(to: 0, durationMS: PinchToSummarizeConstants.Timing.fingerFade)
            if mode != .reading {
                restore(velocity: 0, scripted: true)
            } else {
                rimValue.timing(to: 0, durationMS: PinchToSummarizeConstants.Timing.rimLetGo)
                pValue.spring(to: 0, parameters: PinchToSummarizeConstants.Springs.restore)
                driftXValue.spring(to: 0, parameters: PinchToSummarizeConstants.Springs.drift)
                driftYValue.spring(to: 0, parameters: PinchToSummarizeConstants.Springs.drift)
            }
        }

        let atRest = mode == .reading && !pinch.active && abs(pValue.value) < 0.002
            && !pValue.isMoving && !driftXValue.isMoving && !driftYValue.isMoving
        if atRest {
            finishRestore()
            script = PinchToSummarizeScriptState(time: 0, paused: false, realDown: false, quiet: 0, segment: 0, returningToRest: false)
            clock = 0
            bloomAt = -1
        }
    }

    private func resetToRest(restartScript: Bool) {
        job += 1
        events.removeAll()
        mode = .reading
        scrollLocked = false
        pendingLandHaptic = false
        pendingRestoreSettle = false
        pinch = PinchToSummarizePinchState()
        pValue.set(0)
        liftValue.set(0)
        driftXValue.set(0)
        driftYValue.set(0)
        thinkValue.set(0)
        rimValue.set(0)
        revealValue.set(0)
        summaryAlphaValue.set(0)
        pageAlphaValue.set(1)
        fingerAlphaValue.set(0)
        bloomAt = -1
        clock = 0
        script = PinchToSummarizeScriptState(paused: !restartScript)
        publishValues()
    }

    private func publishValues() {
        p = pValue.value.cgFloat
        lift = liftValue.value.cgFloat
        driftX = driftXValue.value.cgFloat
        driftY = driftYValue.value.cgFloat
        think = thinkValue.value.cgFloat
        rim = rimValue.value.cgFloat
        reveal = revealValue.value.cgFloat
        summaryAlpha = summaryAlphaValue.value.cgFloat
        pageAlpha = pageAlphaValue.value.cgFloat
        fingerAlpha = fingerAlphaValue.value.cgFloat
    }

    private func placeFingers(x: Double, y: Double, spread: Double) {
        let dx = 0.36 * spread / 2
        let dy = 0.93 * spread / 2
        fingerAX = (x + dx).cgFloat
        fingerAY = (y - dy).cgFloat
        fingerBX = (x - dx).cgFloat
        fingerBY = (y + dy).cgFloat
    }

    private func band(_ over: Double) -> Double {
        (over * PinchToSummarizeConstants.Pinch.rubber)
            / (1 + PinchToSummarizeConstants.Pinch.rubber * abs(over))
    }

    private func rubber(_ raw: Double) -> Double {
        if raw > 1 { return 1 + band(raw - 1) }
        if raw < 0 { return -band(-raw) }
        return raw
    }

    private func project(_ velocity: Double) -> Double {
        ((velocity / 1_000) * PinchToSummarizeConstants.Pinch.deceleration)
            / (1 - PinchToSummarizeConstants.Pinch.deceleration)
    }

    private func ease(_ value: Double) -> Double {
        let t = min(1, max(0, value))
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    private func mix(_ from: Double, _ to: Double, _ progress: Double) -> Double {
        from + (to - from) * progress
    }

    private enum Haptic {
        case armed, disarmed, land, ready, restore
    }

    private func buzz(_ kind: Haptic, scripted: Bool) {
        if !scripted { playHaptic(kind) }
    }

    private func playHaptic(_ kind: Haptic) {
        #if canImport(UIKit)
        switch kind {
        case .armed, .disarmed:
            UISelectionFeedbackGenerator().selectionChanged()
        case .ready:
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        case .land:
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        case .restore:
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
        #endif
    }
}

private extension Double {
    var cgFloat: CGFloat { CGFloat(self) }
}

private extension CGFloat {
    var double: Double { Double(self) }
}
