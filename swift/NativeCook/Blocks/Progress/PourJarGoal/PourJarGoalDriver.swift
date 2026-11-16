import Observation
import QuartzCore
import UIKit

struct PourJarGoalScene {
    var amount = 2240.0, displayAmount = 2240.0, statAmount = 2240.0, percentAmount = 2240.0
    var heights = Array(repeating: 0.0, count: 64)
    var shaderHeights = PourJarGoalMotion.shaderLoopFields[0]
    var toast = 0.0, delta = 0.0, grip = 0.0, fingerY = PourJarGoalMotion.vessel.volumeY(2240.0 / 3100)
    var stream = 0.0, impact = 0.0, bubbleTime = 0.0, dropY = -100.0, dropX = 111.0, dropRadius = 0.0
    var touching = 0, hasUndo = false, accessibleValue = 2240.0
}

@MainActor
private final class PourJarGoalClock: NSObject {
    weak var owner: PourJarGoalDriver?
    @objc func tick(_ link: CADisplayLink) { owner?.frame(link.timestamp) }
}

/// useLiquid's event and frame ordering, including its fixed 120 Hz wave accumulator.
@MainActor @Observable
final class PourJarGoalDriver {
    typealias M = PourJarGoalMotion
    var scene = PourJarGoalScene()
    @ObservationIgnored private var link: CADisplayLink?
    private let clock = PourJarGoalClock()
    private let feedback = PourJarGoalFeedback()
    @ObservationIgnored private var d = State()
    @ObservationIgnored private var autoPlay = false
    @ObservationIgnored private var reduced = false
    @ObservationIgnored private var active = false
    @ObservationIgnored private var pin: Double?
    @ObservationIgnored private var lastFrame: Double?
    @ObservationIgnored private var cycleStart: Double?

    private struct State {
        var mode = 1, s = PourJarGoalScene(), field = M.Field()
        var base = 2240.0, target = 2240.0, committed = 2240.0, undoValue = 2240.0, returnFrom = 2240.0
        var time = 0.0, age = 10.0, liveTime = 0.0, toastAge = 100.0, userCommitTime = -100.0
        var finger = 111.0, secondFinger = -1000.0, secondFingerY = 0.0
        var dropOriginY = 0.0, dropAge = 100.0, dropSpeed = 0.0
        var pouring = false, pourAge = 0.0, accumulator = 0.0
        var lastNotify = -10.0, lastStep = 224, lastTick = 2
        var limited = false, cancelled = false, undoUntil = 0.0
    }

    func configure(autoPlay: Bool, reduced: Bool, pin: Double?, active: Bool) {
        let changed = self.reduced != reduced || self.autoPlay != autoPlay
        self.autoPlay = autoPlay; self.reduced = reduced; self.pin = pin; self.active = active
        if pin != nil || changed {
            d.mode = pin != nil ? 2 : autoPlay && !reduced ? 0 : 1
            cycleStart = nil
            if reduced { d.s.heights = M.loopFields[0] }
        }
        if let pin {
            timeline(reduced ? 0 : M.clamp(pin, 0, M.loop), CACurrentMediaTime(), pinned: true)
            publish(); stopClock()
        } else if active {
            cycleStart = nil; lastFrame = nil
            if d.mode == 0 || !reduced || d.age < 4.5 { wake() }
            else { publish() }
        } else { stopClock(); feedback.stop() }
    }
    func stop() { active = false; stopClock(); feedback.stop() }
    private func stopClock() { link?.invalidate(); link = nil; lastFrame = nil }
    private func wake() {
        guard active, link == nil else { return }
        clock.owner = self
        let displayLink = CADisplayLink(target: clock, selector: #selector(PourJarGoalClock.tick(_:)))
        displayLink.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 120, preferred: 120)
        displayLink.add(to: .main, forMode: .common)
        link = displayLink
    }
    private func publish() {
        d.s.shaderHeights = d.mode == 1 && !reduced
            ? d.s.heights.map { M.quantize($0, -64, 64) }
            : M.shaderLoopFields[reduced ? 0 : min(960, Int(floor(d.time * 120 + 0.5)))]
        d.s.hasUndo = d.undoUntil > CACurrentMediaTime()
        scene = d.s
    }
    private func timeline(_ t: Double, _ now: Double, pinned: Bool = false) {
        let k = M.timeline(t), b = d.base, add = min(100, 3100 - b)
        d.time = t
        d.s.amount = b + add * k.lift
        d.s.displayAmount = b + add * M.timeline(t - 0.02).lift
        d.s.statAmount = b + add * M.timeline(t - 0.04).lift
        d.s.percentAmount = b + add * M.timeline(t - 0.08).lift
        d.s.heights = M.loopFields[reduced ? 0 : min(960, Int(floor(k.t * 120 + 0.5)))]
        d.s.grip = k.grip
        let userAge = pinned ? 100 : now - d.userCommitTime
        d.s.toast = userAge < 4 ? M.clamp(M.springAt(userAge - 0.35)) : k.toast
        if userAge >= 4 { d.s.delta = add }
        d.s.impact = k.impact; d.s.bubbleTime = k.bubbleTime
    }
    func frame(_ now: Double) {
        guard active else { return }
        let dt = min(max(0, lastFrame.map { now - $0 } ?? 0.016667), 1.0 / 30)
        lastFrame = now
        if d.mode != 1 {
            if cycleStart == nil { cycleStart = now - d.time }
            let t = reduced ? 0 : (now - (cycleStart ?? now)).truncatingRemainder(dividingBy: 8)
            timeline(t, now); publish()
            return
        }
        if d.s.touching == 3 {
            let previous = d.pourAge
            d.pourAge += dt
            let t = d.pourAge, initialTarget = min(3100, d.committed + 100)
            d.target = d.committed + (initialTarget - d.committed) * M.segment(t, 0.3, 1)
            d.finger = 111; d.s.dropX = 111
            d.s.fingerY = M.vessel.volumeY(d.s.amount / 3100)
            d.s.dropY = 10 + (d.s.fingerY - 10) * pow(M.clamp((t - 0.06) / 0.24), 2)
            d.s.dropRadius = t > 0.06 && t < 0.3 && !reduced ? 4 : 0
            if t >= 0.3 && previous < 0.3 {
                if !reduced { d.field.impulse(111, 620, 216) }
                d.s.impact = 1; feedback.cue("impact"); feedback.sound("land", d.s.amount / 3100)
            }
            if t >= (reduced ? 0.3 : 1.1) { d.s.amount = initialTarget; commit(now) }
        }
        let touched = d.s.touching > 0
        d.age = touched ? 0 : d.age + dt; d.liveTime += dt
        if d.pouring {
            d.pourAge += dt
            if d.pourAge > 0.3 {
                let rate = 40 + 360 * M.segment(d.pourAge - 0.3, 0, 1.5)
                d.target = M.clamp(d.target + rate * dt, 0, 3100)
                d.s.stream = reduced ? 0 : 3 + 6 * M.clamp(rate / 400)
            }
        } else { d.s.stream *= exp(-dt * 24) }
        let desired = d.target
        d.s.amount += (desired - d.s.amount) * (1 - exp(-dt / (reduced ? 0.07 : 0.09)))
        d.s.displayAmount = reduced ? d.s.amount : d.s.displayAmount + (d.s.amount - d.s.displayAmount) * (1 - exp(-dt / 0.02))
        d.s.statAmount += (d.s.amount - d.s.statAmount) * (1 - exp(-dt / 0.04))
        d.s.percentAmount += (d.s.amount - d.s.percentAmount) * (1 - exp(-dt / 0.08))
        d.s.grip += ((touched && d.s.touching == 1 ? 1.0 : 0.0) - d.s.grip) * (1 - exp(-dt * 32))
        d.accumulator = min(1.0 / 15, d.accumulator + dt)
        if !reduced {
            let y = M.vessel.volumeY(d.s.amount / 3100)
            let mound = d.s.touching == 1 ? M.clamp(y - d.s.fingerY, -48, 48) : 0
            let mound2 = d.s.touching == 1 && d.secondFinger > -100 ? M.clamp(y - d.secondFingerY, -48, 48) : 0
            let row = min(127, max(0, Int(floor(y / 422 * 128))))
            let left = M.vessel.left[row], width = max(8, M.vessel.right[row] - left - 6)
            let columns = Int(floor(32 + M.clamp(width / 216) * 32 + 0.5))
            let steps = Int(floor(d.accumulator * 120))
            for _ in 0..<steps { d.field.step(1.0 / 120, width, 3.2, d.finger - left, mound, d.secondFinger - left, columns, mound2) }
            d.accumulator = d.accumulator.truncatingRemainder(dividingBy: 1.0 / 120)
            d.s.heights = d.field.output
            if d.field.energy > 0.5 || touched || d.pouring { d.s.bubbleTime += dt }
        } else { d.s.heights = M.loopFields[0] }
        d.toastAge += dt
        let ta = d.toastAge, successTime = reduced ? 0.01 : 0.35
        if ta >= successTime && ta - dt < successTime && d.s.delta != 0 { feedback.cue("success") }
        d.s.toast = M.clamp(reduced ? ta / 0.15 : M.springAt(ta - 0.35)) * (1 - M.segment(ta, 4, 4.34))
        d.dropAge += dt
        let da = d.dropAge
        if d.s.touching == 3 { /* The tap path above owns the drop. */ }
        else if da < 0.7 && !reduced {
            let y = d.dropOriginY - d.dropSpeed * da + 1200 * da * da
            let col = min(63, max(0, Int(floor(d.s.dropX / 222 * 63 + 0.5))))
            let surface = M.vessel.volumeY(d.s.amount / 3100) - d.s.heights[col]
            d.s.dropY = M.clamp(y, 10, 412); d.s.dropRadius = y < surface ? 4 : 0
            if da > 0.05 && y >= surface && d.s.dropRadius == 0 && d.dropSpeed > 0 {
                d.field.impulse(d.s.dropX, 240, 216); d.s.impact = 1; d.dropSpeed = 0
                feedback.sound("land", d.s.amount / 3100)
            }
        } else { d.s.dropRadius = 0 }
        d.s.impact *= exp(-dt * 9)
        if (touched || d.pouring) && d.liveTime - d.lastNotify >= 0.1 {
            d.lastNotify = d.liveTime
            let snapped = M.clamp(floor(d.s.amount / 10 + 0.5) * 10, 0, 3100)
            if d.pouring { feedback.sound("flow", d.s.amount / 3100, M.clamp(d.s.stream / 9) * 0.6) }
            let newStep = Int(floor(snapped / 10 + 0.5))
            if newStep != d.lastStep { d.lastStep = newStep; feedback.cue("detent") }
            let fraction = snapped / 3100, ticks = [0.25, 0.5, 0.75, 1.0]
            for i in 0..<4 where fraction >= ticks[i] && d.lastTick < i { feedback.cue("detent") }
            d.lastTick = ticks.lastIndex(where: { fraction >= $0 }) ?? -1
            if desired >= 3100 && !d.limited { d.limited = true; feedback.cue("limit") }
        }
        if !touched && !d.pouring && d.age > 2 && autoPlay && !reduced {
            if d.age - dt <= 2 { d.returnFrom = d.s.amount }
            let r = M.segment(d.age, 2, 2.6)
            d.s.amount = d.returnFrom + (d.base - d.returnFrom) * r
            if r >= 1 { d.mode = 0; d.time = 0; cycleStart = nil; d.s.toast = 0; d.s.bubbleTime = 0 }
        }
        publish()
        if (!autoPlay || reduced) && !touched && !d.pouring && d.age > 4.5
            && (reduced || d.field.energy < 0.5) && abs(d.s.amount - d.target) < 0.05 { stopClock() }
    }

    private func begin() {
        if d.mode != 1 {
            d.field.n = 64
            for i in 0..<64 { d.field.h[i] = d.s.heights[i]; d.field.output[i] = d.s.heights[i]; d.field.v[i] = 0 }
        }
        d.mode = 1; pin = nil; d.target = d.s.amount; d.committed = d.base
        d.age = 0; d.cancelled = false; d.limited = false; d.toastAge = 100
        wake(); feedback.cue("onset")
    }
    func drag(_ points: [CGPoint], width: Double, height: Double, began: Bool) {
        if began { begin(); d.s.touching = 1 }
        guard !d.cancelled, let point = points.first else { return }
        if point.x < -24 || point.x > width + 24 { cancel(); return }
        let sx = width / 222, sy = height / 422
        d.finger = M.clamp(point.x / sx, 0, 222)
        d.s.fingerY = M.rubber(point.y / sy, 8, 414)
        let value = M.vessel.yVolume(d.s.fingerY) * 3100
        d.target = M.rubber(value, d.committed, 3100)
        d.secondFinger = points.count > 1 ? M.clamp(points[1].x / sx, 0, 222) : -1000
        if points.count > 1 { d.secondFingerY = points[1].y / sy }
        publish()
    }
    func endDrag(velocityY: Double, height: Double) {
        guard !d.cancelled else { return }
        let velocity = -velocityY / (height / 422)
        if !reduced { d.field.impulse(d.finger, velocity, 216) }
        if velocity > 1400 && !reduced {
            d.dropOriginY = d.s.fingerY; d.s.dropX = d.finger; d.dropAge = 0
            d.dropSpeed = min(440, sqrt(4 * 1200 * max(0, min(40, d.s.fingerY - 10)))) * M.clamp(velocity / 2200, 0.5, 1)
        }
        d.secondFinger = -1000; commit(CACurrentMediaTime()); publish()
    }
    func beginPour() {
        begin(); d.s.touching = 2; d.pouring = true; d.pourAge = 0
        feedback.sound("pour", M.clamp(d.s.amount / 3100)); publish()
    }
    func endPour() {
        let held = d.pourAge
        d.pouring = false
        if held < 0.3 {
            feedback.sound("tail", M.clamp(d.s.amount / 3100)); d.target = d.committed
            d.pourAge = -0.001; d.s.touching = 3
        } else { feedback.sound("stop", d.s.amount / 3100); commit(CACurrentMediaTime()) }
        publish()
    }
    func cancel() {
        d.target = d.committed; d.cancelled = true; d.s.touching = 0; d.pouring = false
        d.age = 0; d.secondFinger = -1000; feedback.sound("stop", 0); publish()
    }
    private func commit(_ now: Double) {
        let previous = d.committed, saved = M.clamp(floor(d.s.amount / 10 + 0.5) * 10, previous, 3100)
        d.userCommitTime = now; d.target = saved; d.s.delta = saved - previous; d.undoValue = previous
        d.s.touching = 0; d.pouring = false; d.age = 0; d.toastAge = saved != previous ? 0 : 100
        d.base = saved; d.s.accessibleValue = saved
        if saved != previous { d.undoUntil = now + 4 }
    }
    func undo() {
        guard d.undoUntil >= CACurrentMediaTime() else { return }
        d.mode = 1; d.userCommitTime = -100; d.undoUntil = 0
        d.target = d.undoValue; d.toastAge = 100; d.age = 0; d.base = d.undoValue
        d.s.accessibleValue = d.undoValue; feedback.cue("undo"); wake(); publish()
    }
    func adjust(_ direction: Double, confirm: Bool = false) {
        d.mode = 1; pin = nil; d.age = 0; d.committed = d.base
        let next = M.clamp(d.target + direction * 10, d.base, 3100)
        d.target = next; wake()
        if confirm {
            let delta = next - d.base
            d.base = next; d.s.delta = delta; d.undoValue = next - delta; d.toastAge = 0
            d.userCommitTime = CACurrentMediaTime(); d.s.accessibleValue = next
            if delta != 0 { d.undoUntil = CACurrentMediaTime() + 4 }
        } else { d.s.accessibleValue = next; feedback.cue("detent") }
        publish()
    }
}
