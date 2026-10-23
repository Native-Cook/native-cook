import Foundation
import Observation
import UIKit

/// Main-actor state port of use-glass-disc.ts. The renderer reads values; the view calls update once per frame.
@Observable @MainActor final class GlassDiscOnboardingModel {
    enum Stage { case intro, community, moving }
    struct Finger {
        var x = 0.0
        var y = 0.0
        var alpha = 0.0
    }

    private enum Phase { case intro, pulling, landing, community, backdrag, rewinding }
    private enum Channel: CaseIterable {
        case discX, discY, discR, ring, reveal, introAlpha, hintAlpha, headIn, restIn, nudge
    }
    private enum Ease { case out, cubicOut, quadOut, cubicInOut, linear }
    private struct ScalarAnimation {
        var from: Double
        var to: Double
        var durationMS: Double
        var elapsedMS = 0.0
        var delayMS = 0.0
        var ease: Ease = .linear
        var velocity = 0.0
        var dampingRatio = 0.8
        var stiffness = 0.0
        var initialEnergy = 0.0
        var spring = false
    }
    private struct Drag {
        var y0 = 0.0
        var pull = 0.0
        var ticks = 0
        var scripted = false
    }

    let width: Double
    let height: Double
    let autoPlay: Bool
    let reduceMotion: Bool

    var stage: Stage = .intro
    var discX: Double
    var discY: Double
    var discR: Double
    var ring = 0.0
    var reveal = 0.0
    /// Zero samples the lone headline in the texture's lower half; one samples the top screen in place.
    var texMode = 0.0
    var introAlpha = 1.0
    var hintAlpha = 1.0
    var headIn = 0.0
    var restIn = 0.0
    var nudge = 0.0
    var stretch = 0.0
    var glint = 0.0
    var glintAng = 0.0
    var spreadBoost = 0.0
    var squash = 0.0
    var clock = 0.0
    var landAt = -1e9
    /// Changes when a sparse manual-mode wake starts a new six-second rim glint.
    var ambientWakeID = 0
    var finger = Finger()
    var stickers: GlassDiscOnboardingSimulation

    /// A manual intro can pause its display link after settling; autoplay and the floating cloud need frames.
    var isAnimating: Bool {
        if reduceMotion { return !animations.isEmpty || phase == .pulling || phase == .backdrag || phase == .landing || phase == .rewinding }
        return autoPlay || phase != .intro || !animations.isEmpty || stickers.phase != .hidden || ambientActive || glint > 0
    }

    private var phase: Phase = .intro
    @ObservationIgnored private var drag = Drag()
    private var animations: [Channel: ScalarAnimation] = [:]
    @ObservationIgnored private var glintAt = -1e9
    @ObservationIgnored private var lastY: Double
    @ObservationIgnored private var fingerAt = (x: 0.0, y: 0.0, t: 0.0)
    @ObservationIgnored private var scriptTime = 0.0
    @ObservationIgnored private var scriptSegment = 0
    @ObservationIgnored private var scriptEpochWallTimeMS: Double?
    @ObservationIgnored private var scriptCycle = 0
    @ObservationIgnored private var scriptPaused = false
    @ObservationIgnored private var realDown = false
    @ObservationIgnored private var quiet = 0.0
    @ObservationIgnored private var landingDue: Double?
    @ObservationIgnored private var landingScripted = false
    @ObservationIgnored private var communityDue: Double?
    @ObservationIgnored private var pendingRiseY = false
    @ObservationIgnored private var pendingRiseR = false
    @ObservationIgnored private var rewinding = false
    private var ambientActive = false
    @ObservationIgnored private var ambientTimer: Timer?

    private var domeR: Double { width * GlassDiscOnboardingTheme.Layout.domeRadius }
    private var domeY: Double { height * GlassDiscOnboardingTheme.Layout.domeTop + domeR }
    private var beadY: Double { height * GlassDiscOnboardingTheme.Layout.beadY }
    private var restY: Double { height * GlassDiscOnboardingTheme.Layout.beadRestY }
    private var restR: Double { GlassDiscOnboardingTheme.Layout.beadRestRadius }
    private var beadR: Double { GlassDiscOnboardingTheme.Layout.beadRadius }
    private var backR: Double { width * GlassDiscOnboardingTheme.Layout.backRadius }

    init(width: Double, height: Double, autoPlay: Bool, reduceMotion: Bool) {
        self.width = width
        self.height = height
        self.autoPlay = autoPlay
        self.reduceMotion = reduceMotion
        let initialR = width * GlassDiscOnboardingTheme.Layout.domeRadius
        let initialY = height * GlassDiscOnboardingTheme.Layout.domeTop + initialR
        discX = width / 2
        discY = initialY
        discR = initialR
        lastY = initialY
        stickers = GlassDiscOnboardingSimulation(width: width, height: height)
        ambientActive = !autoPlay && !reduceMotion
        if reduceMotion {
            // The repository contract asks Reduce Motion to show the final legible state and stop the loop.
            phase = .community
            stage = .community
            discY = height * GlassDiscOnboardingTheme.Layout.beadRestY
            discR = GlassDiscOnboardingTheme.Layout.beadRestRadius
            lastY = discY
            texMode = 1
            reveal = 1
            introAlpha = 0
            hintAlpha = 0
            headIn = 1
            restIn = 1
            stickers.launch(x: width / 2, y: height * GlassDiscOnboardingTheme.Layout.beadY, reducedMotion: true)
            stickers.step(ms: GlassDiscOnboardingMotion.Fade.reducedMs, height: height, reducedMotion: true)
        }
    }

    func touchBegin(x: Double, y: Double) {
        ambientTimer?.invalidate()
        ambientTimer = nil
        if !autoPlay && !reduceMotion { ambientActive = true }
        pauseScript(down: true)
        begin(x: x, y: y, scripted: false)
    }

    func touchMove(x: Double, y: Double) { move(x: x, y: y) }

    func touchEnd(velocityY: Double = 0, cancelled: Bool = false) {
        end(velocityY: velocityY, cancelled: cancelled)
        pauseScript(down: false)
    }

    func enter() {
        pauseScript(down: false)
        if phase == .intro { commit(scripted: false) }
    }

    func startOver() {
        pauseScript(down: false)
        if phase == .community { rewind(scripted: false, velocityY: 0) }
    }

    func update(wallTimeMS: Double, deltaMS rawMS: Double) {
        if autoPlay, scriptEpochWallTimeMS == nil { scriptEpochWallTimeMS = wallTimeMS }
        let ms = min(max(rawMS, 0), GlassDiscOnboardingMotion.maximumFrameMS)
        guard ms > 0 else { return }
        clock += ms
        stepAnimations(ms: ms)
        if let due = landingDue, clock >= due {
            landingDue = nil
            feedback(.land, scripted: landingScripted)
            land()
        }
        if let due = communityDue, clock >= due {
            communityDue = nil
            phase = .community
            stage = .community
            UIAccessibility.post(notification: .announcement, argument: "\(GlassDiscOnboardingTheme.Copy.headline) \(GlassDiscOnboardingTheme.Copy.sub.joined(separator: " "))")
        }
        let speed = (discY - lastY) / ms * 1000
        lastY = discY
        let targetStretch = reduceMotion ? 0 : min(GlassDiscOnboardingMotion.Upgrade.stretchMax, abs(speed) * GlassDiscOnboardingMotion.Upgrade.stretchPerSpeed)
        stretch += (targetStretch - stretch) * GlassDiscOnboardingMotion.Upgrade.stretchFollow
        spreadBoost = reduceMotion ? 0 : min(GlassDiscOnboardingMotion.Upgrade.dispersionMax, abs(speed) * GlassDiscOnboardingMotion.Upgrade.dispersionPerSpeed)
        updateGlint()
        updatePulse()
        stickers.step(ms: ms, height: height, reducedMotion: reduceMotion)
        if !autoPlay && !reduceMotion { updateAmbientWake() }
        guard autoPlay && !reduceMotion else { return }
        if scriptPaused {
            quiet = realDown ? 0 : quiet + max(rawMS, 0)
            // Owner override: two seconds after a touch, ease the entire composition back to rest.
            if quiet >= GlassDiscOnboardingMotion.Autoplay.restartAfterTouchMS {
                if phase == .community { rewind(scripted: true, velocityY: 0) }
                if phase == .intro && stickers.phase == .hidden && animations.isEmpty {
                    scriptTime = 0
                    scriptSegment = 0
                    scriptEpochWallTimeMS = wallTimeMS
                    scriptCycle = 0
                    scriptPaused = false
                    quiet = 0
                    resetRest(resetClock: true)
                }
            }
            return
        }
        runScript(wallTimeMS: wallTimeMS)
    }

    private func begin(x: Double, y: Double, scripted: Bool) {
        if phase == .community {
            if hypot(x - width / 2, y - restY) < restR + GlassDiscOnboardingTheme.Touch.beadPadding {
                phase = .backdrag
                drag = Drag(y0: y, scripted: scripted)
                animations.removeValue(forKey: .discY)
                animations.removeValue(forKey: .discR)
                return
            }
            fingerAt = (x, y, clock)
            stickers.setFinger(x: x, y: y)
            return
        }
        guard phase == .intro, y >= discY - discR - GlassDiscOnboardingTheme.Touch.domePadding else { return }
        phase = .pulling
        texMode = 0
        drag = Drag(y0: y, scripted: scripted)
        for channel in [Channel.discX, .discY, .discR, .ring, .reveal, .introAlpha, .hintAlpha] {
            animations.removeValue(forKey: channel)
        }
        animations.removeValue(forKey: .nudge)
        if !reduceMotion { spring(.nudge, to: GlassDiscOnboardingMotion.Upgrade.nudge, spec: GlassDiscOnboardingMotion.Upgrade.nudgeSpring) }
    }

    private func move(x: Double, y: Double) {
        if phase == .community {
            let delta = clock - fingerAt.t
            stickers.moveFinger(x: x, y: y, deltaMS: delta)
            fingerAt = (x, y, clock)
            return
        }
        if phase == .pulling {
            let p = max(0, drag.y0 - y)
            let ticks = Int(floor(min(1, p / GlassDiscOnboardingMotion.Pull.threshold) / GlassDiscOnboardingMotion.Pull.tickEvery))
            if ticks > drag.ticks { feedback(.tick, scripted: drag.scripted) }
            drag.pull = p
            drag.ticks = max(ticks, drag.ticks)
            place(pull: p)
        } else if phase == .backdrag {
            let p = max(0, y - drag.y0)
            drag.pull = p
            placeBack(pull: p)
        }
    }

    private func end(velocityY: Double, cancelled: Bool) {
        if phase == .community { stickers.endFinger(); return }
        if phase == .pulling {
            let fraction = drag.pull / GlassDiscOnboardingMotion.Pull.threshold
            if !cancelled && (fraction >= GlassDiscOnboardingMotion.Pull.commitAt ||
                (velocityY < GlassDiscOnboardingMotion.Pull.flickVelocity && fraction > GlassDiscOnboardingMotion.Pull.flickMin)) {
                commit(scripted: drag.scripted, velocityY: velocityY)
                return
            }
            phase = .intro
            spring(.nudge, to: 0, spec: GlassDiscOnboardingMotion.Upgrade.nudgeSpring, velocity: -velocityY)
            let spec = GlassDiscOnboardingMotion.Spring.back
            spring(.discX, to: width / 2, spec: spec)
            spring(.discY, to: domeY, spec: spec, velocity: velocityY)
            spring(.discR, to: domeR, spec: spec)
            for channel in [Channel.ring, .reveal, .introAlpha, .hintAlpha] {
                timing(channel, to: channel == .introAlpha || channel == .hintAlpha ? 1 : 0,
                       duration: GlassDiscOnboardingMotion.Fade.settle, ease: .out)
            }
            return
        }
        if phase == .backdrag {
            if !cancelled && (drag.pull >= GlassDiscOnboardingMotion.Back.commitBy ||
                (velocityY > GlassDiscOnboardingMotion.Back.flickVelocity && drag.pull > GlassDiscOnboardingMotion.Back.flickMin)) {
                rewind(scripted: drag.scripted, velocityY: velocityY)
                return
            }
            phase = .community
            spring(.discY, to: restY, spec: GlassDiscOnboardingMotion.Spring.bead, velocity: velocityY)
            spring(.discR, to: restR, spec: GlassDiscOnboardingMotion.Spring.bead)
        }
    }

    private func place(pull p: Double) {
        let P = GlassDiscOnboardingMotion.Pull.self
        let L = GlassDiscOnboardingTheme.Layout.self
        let t = min(1, p / P.threshold)
        let over = max(0, p - P.threshold)
        let extra = P.rubber * over / (over + P.rubber)
        discX = width * Self.curve(L.pullT, L.pullX, t)
        discR = width * Self.curve(L.pullT, L.pullR, t)
        discY = height * Self.curve(L.pullT, L.pullY, t) - extra
        ring = t < P.ringPeakAt ? Self.smooth(t / P.ringPeakAt) : 1 - Self.smooth((t - P.ringPeakAt) / (P.ringOutAt - P.ringPeakAt))
        reveal = Self.smooth((t - P.revealFrom) / (P.revealTo - P.revealFrom))
        introAlpha = 1 - Self.smooth(t / P.introOutBy)
        hintAlpha = 1 - Self.smooth(t / P.hintOutBy)
    }

    private func placeBack(pull p: Double) {
        discY = restY + p
        discR = restR + (backR - restR) * Self.smooth(p / GlassDiscOnboardingMotion.Back.growBy)
    }

    private func commit(scripted: Bool, velocityY: Double = 0) {
        phase = .landing
        stage = .moving
        feedback(.commit, scripted: scripted)
        spring(.nudge, to: 0, spec: GlassDiscOnboardingMotion.Upgrade.nudgeSpring)
        glintAt = clock
        timing(.ring, to: 0, duration: GlassDiscOnboardingMotion.Fade.settle, ease: .out)
        timing(.introAlpha, to: 0, duration: GlassDiscOnboardingMotion.Fade.settle, ease: .out)
        hintAlpha = 0
        if reduceMotion {
            reveal = 0
            discX = width / 2
            discY = beadY
            discR = beadR
            land()
            return
        }
        timing(.reveal, to: 0, duration: GlassDiscOnboardingMotion.Fade.settle, ease: .out)
        spring(.discX, to: width / 2, spec: GlassDiscOnboardingMotion.Spring.bead)
        spring(.discR, to: beadR, spec: GlassDiscOnboardingMotion.Spring.bead)
        spring(.discY, to: beadY, spec: GlassDiscOnboardingMotion.Spring.bead, velocity: velocityY)
        landingScripted = scripted
        landingDue = clock + GlassDiscOnboardingMotion.Fade.settle + GlassDiscOnboardingMotion.Fade.landingTail
    }

    private func land() {
        landAt = clock
        texMode = 1
        reveal = 1
        let fade = GlassDiscOnboardingMotion.Fade.self
        timing(.headIn, to: 1, duration: reduceMotion ? fade.reducedMs : fade.headlineMs, ease: .cubicOut)
        timing(.restIn, to: 1, duration: reduceMotion ? fade.reducedMs : fade.restMs,
               delay: reduceMotion ? 0 : fade.restDelay, ease: .quadOut)
        communityDue = clock + (reduceMotion ? fade.reducedMs : fade.restDelay + fade.restMs)
        stickers.launch(x: width / 2, y: beadY, reducedMotion: reduceMotion)
        if reduceMotion {
            discY = restY
            discR = restR
        } else {
            spring(.discY, to: beadY, spec: GlassDiscOnboardingMotion.Spring.bead)
            spring(.discR, to: beadR, spec: GlassDiscOnboardingMotion.Spring.bead)
            pendingRiseY = true
            pendingRiseR = true
        }
    }

    private func rewind(scripted: Bool, velocityY: Double) {
        phase = .rewinding
        stage = .moving
        rewinding = true
        feedback(.back, scripted: scripted)
        let fade = GlassDiscOnboardingMotion.Fade.self
        timing(.headIn, to: 0, duration: fade.outMs, ease: .out)
        timing(.restIn, to: 0, duration: fade.outMs, ease: .out)
        timing(.reveal, to: 0, duration: fade.settle, ease: .out)
        timing(.introAlpha, to: 1, duration: fade.introBack, delay: fade.outMs, ease: .out)
        timing(.hintAlpha, to: 1, duration: fade.introBack, delay: fade.outMs, ease: .out)
        stickers.leave()
        if reduceMotion {
            discY = domeY
            discR = domeR
            toIntro()
        } else {
            spring(.discR, to: domeR, spec: GlassDiscOnboardingMotion.Spring.dome)
            spring(.discY, to: domeY, spec: GlassDiscOnboardingMotion.Spring.dome, velocity: velocityY)
        }
    }

    private func toIntro() {
        phase = .intro
        stage = .intro
        texMode = 0
        rewinding = false
    }

    private func pauseScript(down: Bool) {
        if down && !scriptPaused && scriptSegment != 0 { end(velocityY: 0, cancelled: true) }
        if down { finger.alpha = 0 }
        scriptPaused = true
        realDown = down
        quiet = 0
        scriptSegment = 0
    }

    private func runScript(wallTimeMS: Double) {
        let A = GlassDiscOnboardingMotion.Autoplay.self
        guard let epoch = scriptEpochWallTimeMS else { return }
        let elapsed = max(0, wallTimeMS - epoch)
        let cycle = Int(floor(elapsed / GlassDiscOnboardingMotion.loopMS))
        var t = elapsed.truncatingRemainder(dividingBy: GlassDiscOnboardingMotion.loopMS)
        if GlassDiscOnboardingMotion.debugFreezeMS >= 0 {
            t = min(t, GlassDiscOnboardingMotion.debugFreezeMS)
        }
        if cycle != scriptCycle {
            scriptCycle = cycle
            scriptSegment = 0
            resetRest(resetClock: true)
        }
        let segment = t >= A.pullStart && t < A.pullEnd ? 1 : t >= A.sweepStart && t < A.sweepEnd ? 2 : t >= A.backStart && t < A.backEnd ? 3 : 0
        if segment != scriptSegment {
            if scriptSegment != 0 {
                let endTime = scriptSegment == 1 ? A.pullEnd : scriptSegment == 2 ? A.sweepEnd : A.backEnd
                let point = scriptPoint(segment: scriptSegment, time: endTime)
                move(x: point.x, y: point.y)
                end(velocityY: 0, cancelled: false)
            }
            if segment == 0 { finger.alpha = 0 }
            else {
                let point = scriptPoint(segment: segment, time: t)
                finger = Finger(x: point.x, y: point.y, alpha: 1)
                begin(x: point.x, y: point.y, scripted: true)
            }
        }
        if segment != 0 {
            let point = scriptPoint(segment: segment, time: t)
            finger = Finger(x: point.x, y: point.y, alpha: 1)
            move(x: point.x, y: point.y)
        }
        scriptTime = t
        scriptSegment = segment
    }

    private func scriptPoint(segment: Int, time: Double) -> (x: Double, y: Double) {
        let A = GlassDiscOnboardingMotion.Autoplay.self
        if segment == 1 {
            let u = (time - A.pullStart) / (A.pullEnd - A.pullStart)
            return (width * A.startX, height * A.startY - A.pullBy * Self.smooth(u))
        }
        if segment == 2 {
            let u = Self.smooth((time - A.sweepStart) / (A.sweepEnd - A.sweepStart))
            return (
                width * (A.sweepX + A.sweepWidth * u),
                height * (A.sweepY - A.sweepArc * sin(u * .pi))
            )
        }
        let u = (time - A.backStart) / (A.backEnd - A.backStart)
        return (width / 2 + A.backXOffset, restY + A.backBy * Self.smooth(u))
    }

    private func resetRest(resetClock: Bool) {
        animations.removeAll()
        landingDue = nil
        landingScripted = false
        communityDue = nil
        pendingRiseY = false
        pendingRiseR = false
        rewinding = false
        phase = .intro
        stage = .intro
        discX = width / 2
        discY = domeY
        discR = domeR
        lastY = domeY
        ring = 0; reveal = 0; texMode = 0
        introAlpha = 1; hintAlpha = 1; headIn = 0; restIn = 0
        nudge = 0; stretch = 0; spreadBoost = 0; squash = 0
        glint = 0; glintAng = 0; glintAt = -1e9; landAt = -1e9
        finger = Finger()
        stickers = GlassDiscOnboardingSimulation(width: width, height: height)
        if resetClock { clock = 0 }
    }

    private func updateGlint() {
        let U = GlassDiscOnboardingMotion.Upgrade.self
        var elapsed = clock - glintAt
        if (phase == .intro || phase == .community) && elapsed > U.glintMs { elapsed = clock.truncatingRemainder(dividingBy: U.glintEveryMs) }
        guard !reduceMotion, elapsed >= 0, elapsed < U.glintMs,
              phase == .intro || phase == .landing || phase == .community else { glint = 0; return }
        let u = elapsed / U.glintMs
        glint = sin(.pi * u)
        glintAng = U.glintFrom + (U.glintTo - U.glintFrom) * (1 - pow(1 - u, 3))
    }

    private func updatePulse() {
        guard !reduceMotion else { squash = 0; return }
        let U = GlassDiscOnboardingMotion.Upgrade.self
        var amount = 0.0
        for pulse in U.pulses {
            let u = (clock - landAt - GlassDiscOnboardingMotion.Burst.delayMs - pulse) / U.pulseMs
            if u > 0 && u < 1 {
                amount += sin(.pi * min(1, u * U.pulseWaveSpeed)) * (1 - u)
            }
        }
        squash = min(1, amount) * U.pulseSquash
    }

    /// The RN glint runs at the start of each six-second interval. Sleep between glints in manual mode.
    private func updateAmbientWake() {
        guard phase == .intro, animations.isEmpty, stickers.phase == .hidden, glint == 0 else {
            ambientTimer?.invalidate()
            ambientTimer = nil
            return
        }
        guard ambientTimer == nil else { return }
        let period = GlassDiscOnboardingMotion.Upgrade.glintEveryMs
        let next = (floor(clock / period) + 1) * period
        ambientActive = false
        let timer = Timer.scheduledTimer(withTimeInterval: max(0.001, (next - clock) / 1000), repeats: false) { [weak self] _ in
            DispatchQueue.main.async { [weak self] in
                self?.wakeAmbient(at: next)
            }
        }
        ambientTimer = timer
    }

    private func wakeAmbient(at time: Double) {
        ambientTimer = nil
        guard !autoPlay, !reduceMotion, phase == .intro, animations.isEmpty else { return }
        clock = time
        ambientActive = true
        ambientWakeID += 1
    }

    private func timing(_ channel: Channel, to target: Double, duration: Double, delay: Double = 0, ease: Ease) {
        animations[channel] = ScalarAnimation(from: value(channel), to: target, durationMS: duration,
                                               delayMS: delay, ease: ease)
    }

    private func spring(_ channel: Channel, to target: Double, spec: GlassDiscOnboardingMotion.SpringValue, velocity: Double = 0) {
        if let previous = animations[channel], previous.spring,
           previous.to == target, previous.durationMS == spec.duration,
           previous.dampingRatio == spec.dampingRatio, previous.elapsedMS > 0 {
            // Reanimated recognizes a second spring to the same value and continues its original
            // stiffness, velocity, start displacement and completion clock (land -> rise sequence).
            return
        }
        let current = value(channel)
        let initial = current - target
        // Reanimated hands the previous spring's velocity to a replacement, including the bead's rise sequence.
        let inherited = animations[channel]?.spring == true ? animations[channel]?.velocity ?? 0 : 0
        let combinedVelocity = inherited + velocity
        let effectiveVelocity = (target > current && combinedVelocity < 0) ||
            (target < current && combinedVelocity > 0) ? 0 : combinedVelocity
        let stiffness = Self.springStiffness(displacement: initial, velocity: effectiveVelocity, spec: spec)
        // RN's termination energy uses the configured release velocity, even if onStart clipped it
        // for pointing away from the target. The trajectory itself uses effectiveVelocity above.
        let energy = 0.5 * stiffness * initial * initial + 0.5 * velocity * velocity
        animations[channel] = ScalarAnimation(from: current, to: target, durationMS: spec.duration,
                                               velocity: effectiveVelocity, dampingRatio: spec.dampingRatio,
                                               stiffness: stiffness, initialEnergy: energy, spring: true)
    }

    private func stepAnimations(ms: Double) {
        for channel in Channel.allCases {
            guard var animation = animations[channel] else { continue }
            if animation.delayMS > 0 {
                animation.delayMS -= ms
                if animation.delayMS > 0 { animations[channel] = animation; continue }
                animation.elapsedMS = -animation.delayMS
                animation.delayMS = 0
            } else { animation.elapsedMS += ms }
            let done: Bool
            if animation.spring {
                let dt = min(ms, 64) / 1000
                let zeta = animation.dampingRatio
                let omega = sqrt(animation.stiffness)
                let omega1 = omega * sqrt(max(0, 1 - zeta * zeta))
                let displacement = value(channel) - animation.to
                let v0 = animation.velocity
                let envelope = exp(-zeta * omega * dt)
                let nextDisplacement: Double
                let nextVelocity: Double
                if zeta < 1, omega1 > 0 {
                    let oscillation = v0 + zeta * omega * displacement
                    let wave = sin(omega1 * dt) * oscillation / omega1 + displacement * cos(omega1 * dt)
                    nextDisplacement = envelope * wave
                    nextVelocity = -zeta * omega * nextDisplacement + envelope * (cos(omega1 * dt) * oscillation - omega1 * displacement * sin(omega1 * dt))
                } else {
                    let wave = displacement + (v0 + omega * displacement) * dt
                    nextDisplacement = envelope * wave
                    nextVelocity = envelope * (-omega * wave + v0 + omega * displacement)
                }
                let energy = 0.5 * animation.stiffness * nextDisplacement * nextDisplacement + 0.5 * nextVelocity * nextVelocity
                done = animation.initialEnergy == 0 || energy / animation.initialEnergy <= 6e-9
                setValue(channel, done ? animation.to : animation.to + nextDisplacement)
                animation.velocity = done ? 0 : nextVelocity
            } else {
                let u = min(1, animation.elapsedMS / max(1, animation.durationMS))
                setValue(channel, animation.from + (animation.to - animation.from) * Self.ease(u, animation.ease))
                done = u >= 1
            }
            if done { animations.removeValue(forKey: channel) }
            else { animations[channel] = animation }
        }
        if pendingRiseY, animations[.discY] == nil {
            pendingRiseY = false
            timing(.discY, to: restY, duration: GlassDiscOnboardingMotion.Rise.ms, ease: .cubicInOut)
        }
        if pendingRiseR, animations[.discR] == nil {
            pendingRiseR = false
            timing(.discR, to: restR, duration: GlassDiscOnboardingMotion.Rise.ms, ease: .cubicInOut)
        }
        if rewinding, animations[.discY] == nil { toIntro() }
    }

    private func value(_ channel: Channel) -> Double {
        switch channel {
        case .discX: discX
        case .discY: discY
        case .discR: discR
        case .ring: ring
        case .reveal: reveal
        case .introAlpha: introAlpha
        case .hintAlpha: hintAlpha
        case .headIn: headIn
        case .restIn: restIn
        case .nudge: nudge
        }
    }

    private func setValue(_ channel: Channel, _ next: Double) {
        switch channel {
        case .discX: discX = next
        case .discY: discY = next
        case .discR: discR = next
        case .ring: ring = next
        case .reveal: reveal = next
        case .introAlpha: introAlpha = next
        case .hintAlpha: hintAlpha = next
        case .headIn: headIn = next
        case .restIn: restIn = next
        case .nudge: nudge = next
        }
    }

    private enum Feedback { case tick, commit, land, back }
    private func feedback(_ kind: Feedback, scripted: Bool) {
        guard !scripted else { return }
        switch kind {
        case .tick: UISelectionFeedbackGenerator().selectionChanged()
        case .commit: UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        case .land, .back: UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }

    private static func smooth(_ input: Double) -> Double {
        let u = min(1, max(0, input))
        return u * u * (3 - 2 * u)
    }

    private static func curve(_ times: [Double], _ values: [Double], _ time: Double) -> Double {
        if time <= times[0] { return values[0] }
        if time >= times[times.count - 1] { return values[values.count - 1] }
        var i = 0
        while i < times.count - 2 && time > times[i + 1] { i += 1 }
        let u = (time - times[i]) / (times[i + 1] - times[i])
        let p0 = values[max(0, i - 1)]
        let p1 = values[i]
        let p2 = values[i + 1]
        let p3 = values[min(values.count - 1, i + 2)]
        return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u)
    }

    private static func ease(_ u: Double, _ style: Ease) -> Double {
        switch style {
        case .linear: return u
        case .cubicOut: return 1 - pow(1 - u, 3)
        case .quadOut: return 1 - pow(1 - u, 2)
        case .cubicInOut: return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
        case .out:
            let c = GlassDiscOnboardingMotion.easeOut
            var lo = 0.0, hi = 1.0
            for _ in 0..<22 {
                let t = (lo + hi) / 2
                let x = 3 * (1 - t) * (1 - t) * t * c[0] + 3 * (1 - t) * t * t * c[2] + t * t * t
                if x < u { lo = t } else { hi = t }
            }
            let t = (lo + hi) / 2
            return 3 * (1 - t) * (1 - t) * t * c[1] + 3 * (1 - t) * t * t * c[3] + t * t * t
        }
    }

    /// Reanimated duration springs choose stiffness by a relative energy threshold at 1.5× perceptual duration.
    private static func springStiffness(displacement x0: Double, velocity v0: Double,
                                        spec: GlassDiscOnboardingMotion.SpringValue) -> Double {
        if x0 == 0 && v0 == 0 { return 900 }
        let targetTime = spec.duration * 1.5 / 1000
        let threshold = 6e-9
        func difference(_ k: Double) -> Double {
            let omega = sqrt(k) * spec.dampingRatio
            let wave = x0 + (v0 + x0 * omega) * targetTime
            let decay = exp(-omega * targetTime)
            let xt = wave * decay
            let vt = (v0 + x0 * omega - omega * wave) * decay
            let initial = 0.5 * k * x0 * x0 + 0.5 * v0 * v0
            let final = 0.5 * k * xt * xt + 0.5 * vt * vt
            return final / initial - threshold
        }
        var lo = Double.ulpOfOne, hi = 8000.0
        let direction = difference(hi) >= difference(lo) ? 1.0 : -1.0
        for _ in 0..<100 {
            let mid = (lo + hi) / 2
            let result = difference(mid)
            if abs(result) <= threshold * 0.001 { return mid }
            if result * direction < 0 { lo = mid } else { hi = mid }
        }
        return (lo + hi) / 2
    }
}
