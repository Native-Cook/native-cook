import Foundation
import CoreGraphics

/// Frame state stays inside the timeline subtree. Autoplay is deterministic; feedback is touch-only.
@MainActor
final class SkyTimeScrubDriver {
    typealias M = SkyTimeScrubMotion
    let timeline: SkyTimeScrubMotion.Timeline
    let feedback = SkyTimeScrubFeedback()
    private(set) var pose: SkyTimeScrubMotion.Pose
    private(set) var fingerDown = false
    private var touchActive = false
    private var sunTouch = false
    private var touchAt = 0.0
    private var base = 0.0
    private var lastCue = -1.0
    private var mode = 0
    private var clock = 0.0
    private var loopTime = 0.0
    private var previousDate: Date?
    private var lastTouch = -1.0
    private var coastFrom = 0.0
    private var coastVelocity = 0.0
    private var coastAt = 0.0
    private var returnFrom = 0.0
    private var returnAt = 0.0
    private var previousMinutes = -1e9
    private var tickAt = [-100.0, -100, -100]
    private var spring: Spring?
    private var active = true
    private var pinCancelled = false
    private var lastProjection = 1.0
    private var lastScrubCallback = 0.0
    var onChange: ((Double) -> Void)?
    var onScrub: ((Double) -> Void)?
    var onScrubStart: (() -> Void)?
    var onScrubEnd: ((Double) -> Void)?
    private let detents: [Double]
    private let adapt: Bool
    private let up: Double
    private let down: Double

    init(day: SkyTimeScrubMotion.Day = .init(), now: Double = 1086, detents: [Double]? = nil,
         adapt: Bool = true, up: Double = 0.18, down: Double = 0.6) {
        timeline = M.Timeline(day: day, now: M.wrap(now), adapt: adapt, up: up, down: down)
        self.detents = detents ?? [day.sunrise, day.noon, day.sunset, M.wrap(now)]
        self.adapt = adapt; self.up = up; self.down = down
        pose = M.Pose(minutes: M.wrap(now), ev: M.target(now, day))
    }
    private struct Spring {
        var from: Double, target: Double, velocity: Double, at: Double, stiffness: Double, damping: Double
        var energyVelocity: Double
        var elapsed = 0.0
        mutating func value(_ time: Double) -> (Double, Double, Bool) {
            // Reanimated advances springs with a separate 64 ms cap (the driver uses 50 ms).
            elapsed += min(max(0, time - at), 0.064); at = time
            let t = elapsed, z = damping / 2, w = sqrt(stiffness - z * z)
            let a = from - target, b = (velocity + z * a) / w
            let wave = a * cos(w * t) + b * sin(w * t), decay = exp(-z * t)
            let x = wave * decay
            let v = decay * ((-a * w * sin(w * t) + b * w * cos(w * t)) - z * wave)
            let initialEnergy = energyVelocity * energyVelocity + stiffness * a * a
            // Reanimated's mass=1 analytic spring and default relative energy threshold.
            let done = initialEnergy == 0 || (v * v + stiffness * x * x) / initialEnergy <= 6e-9
            return (done ? target : target + x, done ? 0 : v, done)
        }
    }
    func setActive(_ value: Bool) { active = value; previousDate = nil }

    func frame(at date: Date, auto: Bool, reduce: Bool, pin: Double?, projection: Double) -> SkyTimeScrubMotion.Pose {
        guard active else { return pose }
        let dt = min(max(0, previousDate.map { date.timeIntervalSince($0) } ?? 0), 0.05)
        previousDate = date; clock = date.timeIntervalSinceReferenceDate; lastProjection = projection
        if let pin, !pinCancelled {
            pose = timeline.sample(reduce ? 0 : pin)
            tickAt = pose.ticks.map { clock - $0 }
            previousMinutes = -1e9
            return pose
        }
        if mode == 0 && auto && !reduce {
            loopTime = (loopTime + dt).truncatingRemainder(dividingBy: M.loop)
            let hint = pose.hint
            pose = timeline.sample(loopTime)
            pose.hint = min(M.hintAt(loopTime), hint + dt / 0.6)
            tickAt = pose.ticks.map { clock - $0 }
            previousMinutes = pose.minutes
            return pose
        }
        let previousStep = Int(floor(M.wrap(pose.minutes) / 15))
        let previousLimb = limb(pose.minutes, projection)
        if fingerDown {
            if !touchActive && clock - touchAt >= 0.28 {
                touchActive = true; lastCue = clock; feedback.cue("onset"); onScrubStart?()
            }
            let target = touchActive || clock - touchAt >= 0.28 ? 1.0 : 0
            pose.held = reduce ? target : M.mix(pose.held, target, 1 - exp(-dt / 0.08))
        } else { pose.held = reduce ? 0 : M.mix(pose.held, 0, 1 - exp(-dt / 0.12)) }
        if mode == 2 {
            let elapsed = max(0, clock - coastAt), speed = coastVelocity * exp(-6.018 * elapsed)
            pose.minutes = coastFrom + coastVelocity / 6.018 * (1 - exp(-6.018 * elapsed))
            let d = M.nearest(pose.minutes, detents)
            if abs(speed) < 200 && d.distance < 35 {
                startSpring(d.value, speed: speed, stiffness: 260, damping: 22)
                feedback.cue("landing", "clack")
            } else if abs(speed) < 2 { mode = 1; commit() }
        }
        if mode == 3, var spring {
            let (value, _, done) = spring.value(clock)
            pose.minutes = value
            self.spring = spring
            if done { self.spring = nil; mode = 1; commit() }
        }
        if !fingerDown && lastTouch >= 0 && clock - lastTouch >= 2 && mode != 4 {
            spring = nil; returnFrom = pose.minutes; returnAt = clock; mode = 4
        }
        if mode == 4 {
            let p = M.clamp((clock - returnAt) / 0.6)
            let target = timeline.now + M.round((returnFrom - timeline.now) / 1440) * 1440
            pose.minutes = M.mix(returnFrom, target, reduce ? 1 : M.smooth(p))
            if p >= 1 || reduce {
                pose.minutes = timeline.now; pose.ev = M.target(timeline.now, timeline.day)
                mode = auto && !reduce ? 0 : 1; loopTime = 0; lastTouch = -1; commit()
            }
        }
        let target = M.target(pose.minutes, timeline.day), tau = target < pose.ev ? up : down
        pose.ev = reduce || !adapt ? target : M.clamp(M.mix(pose.ev, target, 1 - exp(-dt / max(tau, 0.001))), target - 0.8, target + 1.2)
        let session = pose.held > 0.5 || (lastTouch >= 0 && mode != 4)
        pose.wake = reduce ? (session ? 0.6 : 0) : M.clamp(pose.wake + (session ? dt : -dt * 2), 0, 0.6)
        let away = pose.held > 0.02 || lastTouch >= 0
        pose.hint = reduce ? (away ? 0 : 1) : M.clamp(pose.hint + (away ? -dt / 0.35 : dt / 0.6))
        let times = [timeline.day.noon, timeline.day.sunset, timeline.day.night]
        if previousMinutes > -1e8 {
            for r in 0..<3 where floor((previousMinutes - times[r]) / 1440) != floor((pose.minutes - times[r]) / 1440) {
                tickAt[r] = clock
                if touchActive && clock - lastCue >= 0.1 { lastCue = clock; feedback.cue("step") }
            }
        }
        previousMinutes = pose.minutes
        pose.ticks = tickAt.map { clock - $0 }; pose.press = fingerDown ? 1 : 0
        emitCrossings(previousStep: previousStep, previousLimb: previousLimb)
        if (touchActive || mode == 2 || mode == 3) && clock - lastScrubCallback >= 0.05 {
            lastScrubCallback = clock; onScrub?(M.wrap(pose.minutes))
        }
        return pose
    }

    private func limb(_ minutes: Double, _ projection: Double) -> Int {
        let e = M.solar(minutes, timeline.day).elevation
        let h = M.sunHeight(e, timeline.day, projection), semi = M.semiHeight(e)
        return h - semi > 0 ? 2 : h + semi < 0 ? 0 : 1
    }
    private func emitCrossings(previousStep: Int, previousLimb: Int) {
        guard mode != 0 else { return }
        let step = Int(floor(M.wrap(pose.minutes) / 15))
        if step != previousStep && (touchActive || mode == 2) && clock - lastCue >= 0.1 {
            lastCue = clock
            feedback.cue(step % 4 == 0 ? "hour" : "step", M.solar(pose.minutes, timeline.day).elevation < 0 ? "tickNight" : "tick")
        }
        let next = limb(pose.minutes, lastProjection)
        if next != previousLimb {
            let lower = (next == 1 && previousLimb == 2) || (next == 2 && previousLimb == 1)
            feedback.cue(lower ? "lowerLimb" : "upperLimb", lower ? "swell" : "")
        }
    }
    func begin(sun: Bool) {
        pinCancelled = true; spring = nil; mode = 1; lastTouch = clock
        fingerDown = true; touchActive = sun; sunTouch = sun; touchAt = clock; base = pose.minutes
        lastCue = sun ? clock : -1
        if sun { feedback.cue("onset"); onScrubStart?() }
    }
    func move(translationX: Double) {
        guard fingerDown else { return }
        let oldStep = Int(floor(M.wrap(pose.minutes) / 15)), oldLimb = limb(pose.minutes, lastProjection)
        let moved = abs(translationX) > 8
        if !sunTouch && !touchActive && moved { touchActive = true; lastCue = clock; feedback.cue("onset"); onScrubStart?() }
        if sunTouch {
            // Preserve the original grab offset: translation alone drives the original sun x.
            let x = M.solar(base, timeline.day).x + translationX, d = (x - 86) / 230
            pose.minutes = base - M.wrap(base) + M.fromDay(d, timeline.day)
        } else if touchActive || moved { pose.minutes = base - translationX * 2.5 }
        lastTouch = clock
        emitCrossings(previousStep: oldStep, previousLimb: oldLimb)
    }
    func end(velocityX: Double, reduce: Bool) {
        guard fingerDown else { return }
        if touchActive {
            let d = M.dayParam(pose.minutes, timeline.day)
            let minutesPerDay = d <= 0.5 ? 2 * (timeline.day.noon - timeline.day.sunrise) : 2 * (timeline.day.sunset - timeline.day.noon)
            let speed = M.clamp(sunTouch ? velocityX * minutesPerDay / 230 : -velocityX * 2.5, -3600, 3600)
            onScrubEnd?(M.wrap(pose.minutes))
            if reduce {
                let detent = M.nearest(pose.minutes, detents)
                if detent.distance < 35 { pose.minutes = detent.value }
                commit()
            } else { coastFrom = pose.minutes; coastVelocity = speed; coastAt = clock; mode = 2 }
        }
        fingerDown = false; touchActive = false; sunTouch = false; lastTouch = clock
    }
    func travel(_ rawTarget: Double, reduce: Bool) {
        pinCancelled = true; spring = nil; mode = 3; lastTouch = clock
        let target = rawTarget + M.round((pose.minutes - rawTarget) / 1440) * 1440
        feedback.cue("onset")
        if reduce { pose.minutes = target; commit(); mode = 1 }
        else { startSpring(target, speed: 0, stiffness: 157.9, damping: 22.1) }
    }
    private func startSpring(_ target: Double, speed: Double, stiffness: Double, damping: Double) {
        // This RN version clips an initial velocity that points away from the new target.
        let away = (target > pose.minutes && speed < 0) || (target < pose.minutes && speed > 0)
        spring = Spring(from: pose.minutes, target: target, velocity: away ? 0 : speed, at: clock,
                        stiffness: stiffness, damping: damping, energyVelocity: speed); mode = 3
    }
    private func commit() { onChange?(M.wrap(pose.minutes)) }
}
