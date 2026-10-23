import Foundation
import Observation

/// Everything the scene needs for one frame, from the clock (port of `frameOf`).
struct EnvelopeGiftRevealFrame {
    var now = 0.0
    /// Autoplay blends non-divisible ambient periods to their t=0 values during the reset cross-fade.
    var ambientSeam = 0.0
    var p = 0.0
    var fly = 0.0
    var after = -1.0
    var reset = 0.0
    var page = 0.0
    var drop = 0.0
    var breath = 0.0
    var tilt0 = 0.0
}

/// A value that eases to a target after a delay: Reanimated's `withDelay(delay, withTiming(to, { duration }))`.
private struct EnvelopeGiftRevealFade {
    var from: Double
    var to: Double
    var start = 0.0
    var delay = 0.0
    var duration = 1.0

    func value(at now: Double) -> Double {
        let u = EnvelopeGiftRevealEase.clamp01((now - start - delay) / duration)
        return from + (to - from) * EnvelopeGiftRevealEase.polishOut(u)
    }

    func settled(at now: Double) -> Bool { now - start - delay >= duration }
}

/// The envelope's state (port of `use-envelope.ts`). Before the reveal commits, the finger owns `p`
/// (0 sealed to 1 card out). After it commits, every pose is a function of one clock and the commit time,
/// so the loop repeats.
@MainActor
@Observable
final class EnvelopeGiftRevealEngine {
    enum Phase: Int { case sealed, dragging, revealing, revealed, resetting }
    enum Haptic { case flap, commit, success, light, crack }

    private struct Commit {
        var at = -1.0
        var p0 = 0.0
        var tilt0 = 0.0
    }

    private struct Touch {
        var active = false
        var scripted = false
        var x0 = 0.0
        var y0 = 0.0
        var p0 = 0.0
        var flapped = false
        var cracked = false
    }

    private struct Script {
        var on: Bool
        var t = 0.0
        var quiet = 0.0
        var touching = false
        /// A manual reveal first cross-fades to rest, then restarts autoplay without another quiet period.
        var resumeAfterReset = false
    }

    private typealias Open = EnvelopeGiftRevealOpen
    private typealias Reveal = EnvelopeGiftRevealReveal
    private typealias Beat = EnvelopeGiftRevealScript
    private typealias Ease = EnvelopeGiftRevealEase

    /// The scripted finger starts on the envelope's lower half.
    let width: Double
    let reduced: Bool
    let autoPlay: Bool
    private let centreY: Double
    private let envTop: Double

    @ObservationIgnored private(set) var clock = 0.0
    @ObservationIgnored private(set) var p = 0.0
    @ObservationIgnored private(set) var tilt = 0.0
    private(set) var phase = Phase.sealed
    @ObservationIgnored private(set) var confetti = [Double](repeating: 0, count: EnvelopeGiftRevealReveal.confetti * 8)
    @ObservationIgnored private var pRun: EnvelopeGiftRevealSpringRun?
    @ObservationIgnored private var tiltRun: EnvelopeGiftRevealSpringRun?
    @ObservationIgnored private var commit = Commit()
    @ObservationIgnored private var resetAt = -1.0
    @ObservationIgnored private var breath = 0.0
    @ObservationIgnored private var touch = Touch()
    @ObservationIgnored private var script: Script
    @ObservationIgnored private var fired = false
    @ObservationIgnored private var lastWall: Double?

    @ObservationIgnored private var showFinal = false
    @ObservationIgnored private var finalOpacity: [EnvelopeGiftRevealFade]
    @ObservationIgnored private var finalShift: [EnvelopeGiftRevealFade]

    /// Called for each haptic the finger earns (never for the scripted finger).
    @ObservationIgnored var onHaptic: ((Haptic) -> Void)?
    /// Called with true when nothing moves any more (the host may pause its clock) and with false the moment something does.
    @ObservationIgnored var onIdleChange: ((Bool) -> Void)?
    @ObservationIgnored private var idleReported = false

    init(width: Double, centreY: Double, envTop: Double, autoPlay: Bool, reduced: Bool) {
        self.width = width
        self.centreY = centreY
        self.envTop = envTop
        self.autoPlay = autoPlay
        self.reduced = reduced
        script = Script(on: autoPlay)
        let hiddenShift = reduced ? 0.0 : EnvelopeGiftRevealMotion.Polish.textRise
        finalOpacity = Array(repeating: EnvelopeGiftRevealFade(from: 0, to: 0), count: 3)
        finalShift = Array(repeating: EnvelopeGiftRevealFade(from: hiddenShift, to: hiddenShift), count: 3)
    }

    // MARK: Frame

    /// How long the commit takes to finish opening from progress `p0`.
    static func finishMs(_ p0: Double, reduced: Bool) -> Double {
        reduced ? EnvelopeGiftRevealMotion.reducedMs : EnvelopeGiftRevealTokens.Reveal.finishBaseMs + EnvelopeGiftRevealReveal.finishMs * (1 - p0)
    }

    func frame() -> EnvelopeGiftRevealFrame {
        let now = clock
        // Source ambient periods (3.2 s, 9 s and 6 s) do not divide the fixed 8 s recording loop. Keep
        // those periods through the hero, blend them to their t=0 values only under the 420 ms reset,
        // then hold the canonical resting pose to make the visible loop seam exact.
        let ambientNow = autoPlay && script.on ? script.t : now
        let ambientSeam: Double
        if autoPlay && script.on {
            ambientSeam = Ease.smoothstep(Ease.clamp01(
                (ambientNow - Beat.reset) / EnvelopeGiftRevealReset.fadeMs
            ))
        } else {
            ambientSeam = 0
        }
        var current = p
        var fly = 0.0
        var after = -1.0
        if commit.at >= 0 {
            let finish = Self.finishMs(commit.p0, reduced: reduced)
            current = commit.p0 + (1 - commit.p0) * Ease.out((now - commit.at) / finish)
            after = now - commit.at - finish
            fly = reduced ? (after >= 0 ? 1 : 0) : Ease.fly(after / Reveal.flyMs)
        }
        let fade = reduced ? EnvelopeGiftRevealMotion.reducedMs : EnvelopeGiftRevealReset.fadeMs
        let reset = resetAt < 0 ? 0 : Ease.clamp01((now - resetAt) / fade)
        let page = after < 0 ? 0 : Ease.clamp01((after + EnvelopeGiftRevealTokens.Scene.pageLeadMs) / (reduced ? EnvelopeGiftRevealMotion.reducedMs : Reveal.pageMs))
        let drop = after < 0 || reduced ? (after >= 0 ? 1 : 0) : Ease.clamp01((after - EnvelopeGiftRevealTokens.Scene.envelopeDropDelayMs) / Reveal.envelopeDropMs)
        let breathWindow = EnvelopeGiftRevealTokens.Scene.breathWindowMs
        let lift = commit.at < 0 && !reduced && breath < breathWindow ? sin(Double.pi * breath / breathWindow) * Open.breathDeg : 0
        return EnvelopeGiftRevealFrame(
            now: ambientNow, ambientSeam: ambientSeam,
            p: current, fly: fly, after: after, reset: reset, page: page, drop: drop,
            breath: lift, tilt0: commit.at >= 0 ? commit.tilt0 : tilt
        )
    }

    /// Opacity and downward shift (pt) of the final screen's k-th element: title, message, button.
    func finalStyle(_ k: Int) -> (opacity: Double, shift: Double) {
        (finalOpacity[k].value(at: clock), finalShift[k].value(at: clock))
    }

    var sealedOrDragging: Bool { phase == .sealed || phase == .dragging }
    var revealed: Bool { phase == .revealed }

    // MARK: Clock

    /// Advances to the display link's time. Several calls for one frame move nothing after the first.
    func advance(to date: Date) {
        let ms = date.timeIntervalSinceReferenceDate * 1000
        guard let previous = lastWall else {
            lastWall = ms
            tick(dt: EnvelopeGiftRevealTokens.Clock.initialDeltaMs)
            return
        }
        let delta = ms - previous
        guard delta > 0 else { return }
        lastWall = ms
        tick(dt: min(delta, EnvelopeGiftRevealTokens.Clock.maximumDeltaMs))
    }

    private func tick(dt: Double) {
        clock += dt
        let now = clock
        stepSprings(now)
        if commit.at >= 0 {
            let finish = Self.finishMs(commit.p0, reduced: reduced)
            let done = finish + (reduced ? 0 : Reveal.flyMs)
            if !fired && now - commit.at >= finish + Reveal.flyMs * EnvelopeGiftRevealTokens.Scene.confettiTriggerProgress {
                fired = true
                spawn()
                haptic(.success)
            }
            if phase == .revealing && now - commit.at >= done { setPhase(.revealed) }
        }
        if resetAt >= 0 && now - resetAt >= (reduced ? EnvelopeGiftRevealMotion.reducedMs : EnvelopeGiftRevealReset.fadeMs) {
            resetAt = -1
            commit = Commit()
            setP(0)
            setTilt(0)
            setPhase(.sealed)
        }
        if !reduced { stepConfetti(dt: dt) }

        // The flap lifts a little now and then while sealed, to say it opens.
        breath = (script.on ? script.t : now).truncatingRemainder(dividingBy: Open.breathMs)
        if autoPlay { runScript(dt: dt) }
        reportIdle()
    }

    private func stepSprings(_ now: Double) {
        if var run = pRun {
            let done = run.step(to: now)
            p = run.value
            pRun = done ? nil : run
        }
        if var run = tiltRun {
            let done = run.step(to: now)
            tilt = run.value
            tiltRun = done ? nil : run
        }
    }

    private func setP(_ value: Double) {
        pRun = nil
        p = value
    }

    private func setTilt(_ value: Double) {
        tiltRun = nil
        tilt = value
    }

    private func springP(velocity: Double = 0) {
        pRun = EnvelopeGiftRevealSpringRun(from: p, to: 0, velocity: velocity, now: clock, config: EnvelopeGiftRevealSprings.close)
    }

    private func springTilt() {
        tiltRun = EnvelopeGiftRevealSpringRun(from: tilt, to: 0, velocity: 0, now: clock, config: EnvelopeGiftRevealSprings.close)
    }

    // MARK: Phase

    private func setPhase(_ next: Phase) {
        guard phase != next else { return }
        phase = next
        let show = next == .revealing || next == .revealed
        guard show != showFinal else { return }
        showFinal = show
        // The final screen fades in on the phase change: after a delay and in turn when it appears.
        for k in 0..<3 {
            let delay = show && !reduced ? EnvelopeGiftRevealMotion.Polish.finalBaseDelay + Reveal.textDelay + Double(k) * Reveal.stagger : 0
            let duration = show ? EnvelopeGiftRevealMotion.Polish.finalDuration : (reduced ? EnvelopeGiftRevealMotion.reducedMs : EnvelopeGiftRevealReset.fadeMs)
            let opacity = finalOpacity[k].value(at: clock)
            let shift = finalShift[k].value(at: clock)
            finalOpacity[k] = EnvelopeGiftRevealFade(from: opacity, to: show ? 1 : 0, start: clock, delay: delay, duration: duration)
            finalShift[k] = EnvelopeGiftRevealFade(
                from: shift,
                to: show || reduced ? 0 : EnvelopeGiftRevealMotion.Polish.textRise,
                start: clock,
                delay: delay,
                duration: duration
            )
        }
    }

    private func haptic(_ kind: Haptic) {
        if !touch.scripted { onHaptic?(kind) }
    }

    // MARK: Input

    private func doCommit() {
        commit = Commit(at: clock, p0: p, tilt0: tilt)
        fired = false
        setPhase(.revealing)
        haptic(.commit)
    }

    /// Interruption: a touch while the envelope is still opening (before the card leaves) reverses it.
    private func cancelReveal() -> Bool {
        let elapsed = clock - commit.at
        let finish = Self.finishMs(commit.p0, reduced: reduced)
        if commit.at < 0 || elapsed >= finish { return false }
        let u = Ease.clamp01(elapsed / finish)
        let now = commit.p0 + (1 - commit.p0) * (1 - pow(1 - u, 3))
        commit = Commit()
        setP(now)
        springP()
        springTilt()
        setPhase(.sealed)
        haptic(.light)
        return true
    }

    private func down(x: Double, y: Double, scripted: Bool) {
        if phase == .revealing && !scripted && cancelReveal() { return }
        if phase != .sealed { return }
        touch = Touch(active: true, scripted: scripted, x0: x, y0: y, p0: p, flapped: false, cracked: false)
        setP(p)
        setPhase(.dragging)
    }

    private func move(x: Double, y: Double) {
        guard touch.active, phase == .dragging else { return }
        var next = touch.p0 + (touch.y0 - y) / Open.travel
        if next < 0 { next = 0 }
        if next > 1 { next = 1 + (next - 1) * EnvelopeGiftRevealTokens.Interaction.overscrollResistance }
        setP(next)
        let out = max(0, (next - Open.flapShare) / (1 - Open.flapShare))
        let sway = max(-Open.tiltMax, min(Open.tiltMax, (x - touch.x0) * EnvelopeGiftRevealTokens.Interaction.tiltPerPoint))
        setTilt(reduced ? 0 : sway * min(1, out * EnvelopeGiftRevealTokens.Interaction.tiltRamp))
        // The seal cracks early in the lift (rigid tap), the flap clears at the fold (light tap).
        if !touch.cracked && next >= Open.flapShare * EnvelopeGiftRevealTokens.Interaction.crackShare {
            touch.cracked = true
            haptic(.crack)
        }
        if !touch.flapped && next >= Open.flapShare {
            touch.flapped = true
            haptic(.flap)
        }
    }

    private func up(velocityY vy: Double) {
        guard touch.active else { return }
        touch.active = false
        if phase != .dragging { return }
        if p >= Open.commit || (vy < EnvelopeGiftRevealTokens.Interaction.flickVelocity && p > EnvelopeGiftRevealTokens.Interaction.flickMinimumProgress) {
            doCommit()
            return
        }
        setPhase(.sealed)
        if p > EnvelopeGiftRevealTokens.Interaction.closeHapticProgress { haptic(.light) }
        springP(velocity: -vy / Open.travel)
        springTilt()
    }

    /// The button, and the accessible alternative: open without dragging.
    private func open() {
        if phase != .sealed { return }
        touch.scripted = false
        doCommit()
    }

    private func reset() {
        if phase != .revealed && phase != .revealing { return }
        resetAt = clock
        setPhase(.resetting)
    }

    private func takeOver() {
        if script.on && touch.scripted && touch.active {
            // The scripted finger lets go without opening: the flap falls closed.
            touch.active = false
            setPhase(.sealed)
            springP()
            springTilt()
        }
        script.on = false
        script.quiet = 0
        script.touching = true
        script.resumeAfterReset = false
    }

    private func letGo() {
        script.touching = false
        script.quiet = 0
    }

    // Entry points for the touch surface and the buttons. Each first brings the clock up to now.

    func touchBegan(x: Double, y: Double) {
        advance(to: .now)
        wake()
        takeOver()
        down(x: x, y: y, scripted: false)
    }

    func touchMoved(x: Double, y: Double) {
        advance(to: .now)
        move(x: x, y: y)
    }

    func touchEnded(velocityY: Double) {
        advance(to: .now)
        up(velocityY: velocityY)
        letGo()
    }

    func pressOpen() {
        advance(to: .now)
        wake()
        takeOver()
        open()
        letGo()
    }

    func pressShop() {
        advance(to: .now)
        wake()
        takeOver()
        reset()
        letGo()
    }

    // MARK: Autoplay

    private func runScript(dt: Double) {
        var s = script
        if !s.on {
            if s.resumeAfterReset {
                if phase == .sealed && p < EnvelopeGiftRevealTokens.Interaction.autoplayRestThreshold {
                    s.on = true
                    s.t = 0
                    s.resumeAfterReset = false
                }
                script = s
                return
            }
            let idle = !s.touching && !touch.active
            let quiet = idle ? s.quiet + dt : 0
            if quiet >= EnvelopeGiftRevealMotion.resumeAfterMs && phase == .revealed {
                reset()
                s.quiet = quiet
                s.resumeAfterReset = true
                script = s
                return
            }
            s.quiet = quiet
            s.on = quiet >= EnvelopeGiftRevealMotion.resumeAfterMs && phase == .sealed && p < EnvelopeGiftRevealTokens.Interaction.autoplayRestThreshold
            s.t = 0
            script = s
            return
        }
        let prev = s.t
        var t = prev + dt
        if let hold = EnvelopeGiftRevealMotion.qaHoldAt, prev >= hold { t = prev }
        let x0 = width / 2 + Beat.xOffset
        let y0 = envTop + EnvelopeGiftRevealScript.fingerOffset
        if prev < Beat.down && t >= Beat.down { down(x: x0, y: y0, scripted: true) }
        if t > Beat.down && prev < Beat.up {
            let u = Ease.clamp01((t - Beat.down - Beat.leadMs) / (Beat.up - Beat.down - Beat.tailMs))
            let e = Ease.inOutCubic(u)
            // Up with a slight drift to the right and back, so the card swings as it comes out.
            move(x: x0 + Beat.lateral * sin(Double.pi * e), y: y0 - Beat.rise * e)
        }
        if prev < Beat.up && t >= Beat.up { up(velocityY: 0) }
        if prev < Beat.reset && t >= Beat.reset { reset() }
        script.t = t >= EnvelopeGiftRevealMotion.loopMs ? t - EnvelopeGiftRevealMotion.loopMs : t
    }

    // MARK: Confetti

    /// A repeatable scatter: the same hash the source uses, so every loop throws the same petals.
    private static func hash(_ n: Int, _ salt: Double) -> Double {
        let x = sin(Double(n) * EnvelopeGiftRevealTokens.Confetti.hashN + salt * EnvelopeGiftRevealTokens.Confetti.hashSalt) * EnvelopeGiftRevealTokens.Confetti.hashScale
        return x - floor(x)
    }

    private func spawn() {
        if reduced { return }
        for n in 0..<Reveal.confetti {
            let o = n * 8
            let angle = -Double.pi / 2 + (Self.hash(n, 1) - 0.5) * Double.pi * EnvelopeGiftRevealTokens.Confetti.angleSpread
            let speed = EnvelopeGiftRevealTokens.Confetti.speedBase + Self.hash(n, 2) * EnvelopeGiftRevealTokens.Confetti.speedRange
            confetti[o] = width / 2 + (Self.hash(n, 3) - 0.5) * EnvelopeGiftRevealTokens.Confetti.originWidth
            confetti[o + 1] = centreY + (Self.hash(n, 4) - 0.5) * EnvelopeGiftRevealTokens.Confetti.originHeight
            confetti[o + 2] = cos(angle) * speed
            confetti[o + 3] = sin(angle) * speed
            confetti[o + 4] = Self.hash(n, 5) * Double.pi
            confetti[o + 5] = (Self.hash(n, 6) - 0.5) * EnvelopeGiftRevealTokens.Confetti.spinRange
            confetti[o + 6] = Reveal.confettiLife * (EnvelopeGiftRevealTokens.Confetti.minimumLife + EnvelopeGiftRevealTokens.Confetti.lifeRange * Self.hash(n, 7))
            confetti[o + 7] = floor(Self.hash(n, 8) * Double(EnvelopeGiftRevealTokens.Colors.confetti.count))
        }
    }

    /// Exact port of the source's per-frame update. `dt` affects integration, while drag and flutter apply once.
    private func stepConfetti(dt: Double) {
        let k = dt / 1000
        for n in 0..<Reveal.confetti {
            let o = n * 8
            if confetti[o + 6] <= 0 { continue }
            confetti[o + 2] *= Reveal.drag
            confetti[o + 3] = confetti[o + 3] * Reveal.drag + Reveal.gravity * k
            confetti[o] += confetti[o + 2] * k + sin(confetti[o + 4] * EnvelopeGiftRevealTokens.Confetti.flutterFrequency) * EnvelopeGiftRevealTokens.Confetti.flutter
            confetti[o + 1] += confetti[o + 3] * k
            confetti[o + 4] += confetti[o + 5] * k
            confetti[o + 6] -= k
        }
    }

    // MARK: Idle

    /// Nothing moves: reduced motion, no script, sealed, no spring, no fade, no petal.
    private var isIdle: Bool {
        guard reduced, !autoPlay, phase == .sealed, !touch.active, pRun == nil, tiltRun == nil, commit.at < 0, resetAt < 0 else { return false }
        return (0..<3).allSatisfy { finalOpacity[$0].settled(at: clock) && finalShift[$0].settled(at: clock) }
    }

    private func reportIdle() {
        let idle = isIdle
        guard idle != idleReported else { return }
        idleReported = idle
        onIdleChange?(idle)
    }

    private func wake() {
        guard idleReported else { return }
        idleReported = false
        onIdleChange?(false)
    }
}
