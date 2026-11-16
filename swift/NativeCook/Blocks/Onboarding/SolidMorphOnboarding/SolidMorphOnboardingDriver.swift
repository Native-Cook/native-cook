import CoreMotion
import Observation
import UIKit

struct SolidMorphOnboardingFrame {
    var progress = 0.0, rate = 0.0, clock = 0.0, cushion = 0.0
    var tiltX = 0.0, tiltY = 0.0, base = 0.0, lift = 0.0
    var primaryPress = 0.0, secondaryPress = 0.0, contactPulse = 0.0, basePulse = 0.0
    var playing = false, visibility = 1.0
}

@MainActor @Observable
final class SolidMorphOnboardingDriver {
    var selected = 0
    var needsFrames = false
    var revision = 0
    private(set) var fingerDown = false
    @ObservationIgnored private var d = State()
    @ObservationIgnored private let sensor = CMMotionManager()
    @ObservationIgnored private var cueAt = -Double.infinity
    @ObservationIgnored private var noticeAt = -Double.infinity
    private typealias M = SolidMorphOnboardingMotion
    private struct State {
        var p = SolidMorphOnboardingAnimation(), cushion = SolidMorphOnboardingAnimation()
        var tiltX = SolidMorphOnboardingAnimation(), tiltY = SolidMorphOnboardingAnimation()
        var base = SolidMorphOnboardingAnimation(), lift = SolidMorphOnboardingAnimation()
        var primary = SolidMorphOnboardingAnimation(), secondary = SolidMorphOnboardingAnimation()
        var contactPulse = SolidMorphOnboardingAnimation(), basePulse = SolidMorphOnboardingAnimation()
        var rateFade = SolidMorphOnboardingAnimation()
        var clock = 0.0, rate = 0.0, playing = false, initialized = false, active = true
        var origin = 0.0, previousFrame: Double?, pauseAt: Double?
        var touchedAt = -1e9, target = 0.0, chain = 0.0, startP = 0.0, userActive = false
        var hitObject = false, horizontal = false, contact = false, limit = false
        var resetFrom: Double?, resetClock = 0.0, resetPhase = 0.0
        var arrivalPending = false, baseKickAt: Double?, baseKick = false, liftKick = false
        var contactFadeAt: Double?, baseFadeAt: Double?
        var fadeFrom = 1.0, reducedTo = 0.0, reducedAt: Double?
        var reduced = false, autoPlay = false
        var sampleTime = 0.0
    }

    func frame(at date: Date, autoPlay: Bool, reduced: Bool, pin: Double?) -> SolidMorphOnboardingFrame {
        let now = date.timeIntervalSinceReferenceDate
        if !d.initialized { d.initialized = true; d.origin = now; d.playing = autoPlay && !reduced }
        d.autoPlay = autoPlay
        if d.reduced != reduced {
            d.reduced = reduced; d.playing = autoPlay && !reduced
            d.p.set(M.clamp(d.p.value,0,2).rounded()); d.clock = 0; d.rate = 0
            d.cushion.set(0); d.tiltX.set(0); d.tiltY.set(0); d.reducedAt = nil
        }
        if let pin, !reduced {
            d.playing = true; d.clock = pin; d.p.set(M.progress(pin)); d.rate = M.rate(pin)
            updateSelected(now: now, silent: true)
            return snapshot()
        }
        guard d.active else { return snapshot() }
        let dt = max(0,now-(d.previousFrame ?? now)); d.previousFrame = now
        let previous = d.p.value
        sampleAnimations(now)
        if reduced {
            if let start = d.reducedAt {
                if now-start >= 0.11 { d.p.set(d.reducedTo) }
                if now-start >= 0.22 {
                    d.reducedAt = nil
                    Task { @MainActor [weak self] in self?.needsFrames = false }
                }
            }
        } else if d.playing {
            if let paused = d.pauseAt { d.origin += now-paused; d.pauseAt = nil }
            d.clock = M.wrap(now-d.origin); d.p.set(M.progress(d.clock)); d.rate = M.rate(d.clock)
        } else {
            d.clock = M.wrap(d.clock+dt)
            if d.pauseAt == nil { d.pauseAt = now }
            if !fingerDown && autoPlay && now-d.touchedAt > 2 {
                let reset = M.smooth(2,2.6,now-d.touchedAt)
                if reset < 1 {
                    if d.resetFrom == nil {
                        d.userActive = false; d.p.set(d.p.value); d.resetFrom = d.p.value
                        d.resetClock = d.clock; d.resetPhase = (d.clock/(9/4)).rounded()*(9/4)
                        d.tiltX.timing(0,at: now,duration: 0.6); d.tiltY.timing(0,at: now,duration: 0.6)
                        d.arrivalPending = false
                    }
                    let blend = M.ease(reset)
                    d.p.set((d.resetFrom ?? 0)*(1-blend)); d.clock = d.resetClock+(d.resetPhase-d.resetClock)*blend
                } else {
                    d.p.set(0); d.tiltX.set(0); d.tiltY.set(0); d.lift.set(0); d.base.set(0); d.cushion.set(0)
                    d.resetFrom = nil; d.playing = true; d.origin = now; d.pauseAt = nil; d.clock = 0
                }
            }
            if !d.playing { sampleRate(previous: previous,now: now) }
        }
        updateSelected(now: now,silent: d.playing || !d.userActive)
        return snapshot()
    }

    private func snapshot() -> SolidMorphOnboardingFrame {
        let sensorTilt = d.reduced || d.playing || fingerDown || !sensor.isDeviceMotionAvailable ? 0 : M.clamp((sensor.deviceMotion?.attitude.roll ?? 0)*4,-2,2)
        let visibility = visibility(at: d.previousFrame ?? 0)
        return SolidMorphOnboardingFrame(progress: d.p.value,rate: d.rate,clock: d.clock,cushion: d.cushion.value,
            tiltX: d.tiltX.value+sensorTilt,tiltY: d.tiltY.value,base: d.base.value,lift: d.lift.value,
            primaryPress: d.primary.value,secondaryPress: d.secondary.value,contactPulse: d.contactPulse.value,
            basePulse: d.basePulse.value,playing: d.playing,visibility: visibility)
    }

    private func visibility(at now: Double) -> Double {
        guard let start = d.reducedAt else { return 1 }
        let t = now-start
        return t < 0.11 ? d.fadeFrom*(1-M.timing(t/0.11)) : M.timing((t-0.11)/0.11)
    }

    private func sampleAnimations(_ now: Double) {
        d.p.sample(now); d.cushion.sample(now); d.tiltX.sample(now); d.tiltY.sample(now)
        d.base.sample(now); d.lift.sample(now); d.primary.sample(now); d.secondary.sample(now)
        d.contactPulse.sample(now); d.basePulse.sample(now); d.rateFade.sample(now)
        if d.p.finished && d.arrivalPending {
            d.arrivalPending = false; d.baseKickAt = now+0.3; d.liftKick = true
            d.lift.timing(4,at: now,duration: 0.07)
            buzz(2,now)
        }
        if let at = d.baseKickAt, now >= at {
            d.baseKickAt = nil; d.baseKick = true
            d.base.timing(-1.5,at: now,duration: 0.05)
            d.basePulse.timing(1,at: now,duration: 0.001); d.baseFadeAt = now+0.001
        }
        if d.lift.finished && d.liftKick { d.liftKick = false; d.lift.spring(0,at: now,damping: 8,stiffness: 170) }
        if d.base.finished && d.baseKick { d.baseKick = false; d.base.spring(0,at: now,damping: 14,stiffness: 200) }
        if let fade = d.contactFadeAt, now >= fade { d.contactFadeAt = nil; d.contactPulse.timing(0,at: now,duration: 0.12) }
        if let fade = d.baseFadeAt, now >= fade { d.baseFadeAt = nil; d.basePulse.timing(0,at: now,duration: 0.2) }
    }

    private func sampleRate(previous: Double, now: Double) {
        if d.p.value != previous {
            if d.userActive {
                let forward = d.p.value > previous && floor(d.p.value+0.1) > floor(previous+0.1)
                let reverse = d.p.value < previous && floor(d.p.value-0.1) < floor(previous-0.1)
                if forward || reverse { buzz(1,now) }
            }
            d.rate = (d.p.value-previous)/max(0.008,now-d.sampleTime)
            d.rateFade.set(d.rate); d.rateFade.timing(0,at: now,duration: 0.08)
            d.sampleTime = now
        } else { d.rate = d.rateFade.value }
    }

    func begin(_ point: CGPoint, layout l: SolidMorphOnboardingLayout, at date: Date) {
        guard point.x >= 20 else { return }
        let now = date.timeIntervalSinceReferenceDate, wasPlaying = d.playing
        sampleAnimations(now)
        if wasPlaying { d.lift.set(d.lift.value+M.bob(d.clock)); d.base.set(d.base.value+M.base(d.clock)) }
        d.playing = false; d.rateFade.set(d.rate); d.rateFade.timing(0,at: now,duration: 0.08)
        d.sampleTime = now
        fingerDown = true; d.touchedAt = now; d.p.set(d.p.value)
        d.cushion.set(d.cushion.value); d.lift.set(d.lift.value); d.base.set(d.base.value)
        d.tiltX.set(d.tiltX.value); d.tiltY.set(d.tiltY.value)
        d.startP = d.p.value; d.chain = wasPlaying || abs(d.target-d.p.value) > 0.8 ? d.p.value.rounded() : d.target
        d.horizontal = false; d.resetFrom = nil; d.contact = false; d.limit = false; d.userActive = true
        d.arrivalPending = false; d.baseKickAt = nil; d.liftKick = false; d.baseKick = false
        d.hitObject = abs(point.x-l.heroX) < l.heroRadius*1.3 && abs(point.y-l.heroY) < l.heroRadius
        if d.hitObject && !d.reduced { d.cushion.spring(0.23,at: now,damping: 22.1,stiffness: 157.9) }
    }

    func move(_ point: CGPoint, translation: CGSize, layout l: SolidMorphOnboardingLayout, at date: Date) {
        guard fingerDown else { return }
        let now = date.timeIntervalSinceReferenceDate
        d.touchedAt = now
        guard !d.reduced else { return }
        if abs(translation.width) >= 10 || d.horizontal || !d.hitObject {
            if !d.horizontal { d.cushion.spring(0,at: now,damping: 22.1,stiffness: 157.9) }
            d.horizontal = true
            let sign = translation.width == 0 ? 0.0 : (translation.width < 0 ? -1.0 : 1.0)
            let tracked = translation.width-(d.hitObject ? sign*10 : 0)
            let raw = d.startP-tracked/320/l.scale
            let previous = d.p.value; d.p.set(M.rubber(raw))
            sampleRate(previous: previous,now: now)
            if (raw < -0.04 || raw > 2.04) && !d.limit { d.limit = true; buzz(0,now) }
        } else {
            let compress = M.press(max(0,translation.height)/l.scale); d.cushion.set(max(0.23,compress))
            if compress >= 0.99 && !d.contact {
                d.contact = true; d.contactPulse.timing(1,at: now,duration: 0.001); d.contactFadeAt = now+0.001; buzz(0,now)
            }
        }
        if d.hitObject {
            d.tiltX.set(M.clamp((point.x-l.heroX)/max(1,l.heroRadius),-1,1)*12)
            d.tiltY.set(M.clamp((point.y-l.heroY)/max(1,l.heroRadius),-1,1)*12)
        }
    }

    func end(_ translation: CGSize, velocity: CGSize, x: Double, layout l: SolidMorphOnboardingLayout, at date: Date, cancelled: Bool = false) {
        guard fingerDown else { return }
        let now = date.timeIntervalSinceReferenceDate
        fingerDown = false; d.touchedAt = now
        if !cancelled {
            let v = -velocity.width/320/l.scale
            let value = d.reduced ? d.startP-translation.width/320/l.scale : d.p.value
            let fast = abs(velocity.width) > 400 && (d.horizontal || d.reduced)
            let to = M.clamp(fast ? d.chain.rounded()+(v == 0 ? 0 : (v > 0 ? 1 : -1)) : (value+v*0.12).rounded(),0,2)
            d.target = to
            if d.reduced { reducedPage(to,now) }
            else { d.p.spring(to,at: now,velocity: v); d.arrivalPending = to != d.startP.rounded() }
        }
        if !d.reduced {
            d.cushion.spring(0,at: now,damping: 8,stiffness: 170)
            d.lift.spring(0,at: now,damping: 8,stiffness: 170); d.base.spring(0,at: now,damping: 14,stiffness: 200)
            let damping = cancelled ? 8.0 : 2/1.2, stiffness = cancelled ? 170.0 : 1/(1.2*1.2)
            d.tiltX.spring(0,at: now,damping: damping,stiffness: stiffness,velocity: cancelled ? nil : velocity.width*0.02)
            d.tiltY.spring(0,at: now,damping: damping,stiffness: stiffness,velocity: cancelled ? nil : velocity.height*0.02)
        }
        if !cancelled && !d.hitObject && hypot(translation.width,translation.height) <= 8 {
            go(d.p.value.rounded()+(x > l.width/2 ? 1 : -1),at: date)
        }
    }

    func go(_ to: Double, at date: Date) {
        let now = date.timeIntervalSinceReferenceDate
        let velocity = d.rate
        d.playing = false; d.target = M.clamp(to,0,2); d.touchedAt = now; d.userActive = true
        d.sampleTime = now
        d.resetFrom = nil; d.arrivalPending = false
        if d.reduced { reducedPage(d.target,now) }
        else { d.p.spring(d.target,at: now,velocity: velocity) }
        d.cushion.spring(0,at: now,damping: 8,stiffness: 170)
    }
    private func reducedPage(_ to: Double, _ now: Double) {
        d.fadeFrom = visibility(at: now)
        d.p.set(d.p.value); d.reducedAt = now; d.reducedTo = to
        needsFrames = true
    }
    func button(_ primary: Bool, down: Bool, at date: Date) {
        let now = date.timeIntervalSinceReferenceDate
        if down {
            d.playing = false; d.touchedAt = now
            d.sampleTime = now
            d.rateFade.set(d.rate); d.rateFade.timing(0,at: now,duration: 0.08)
        }
        if d.reduced {
            if primary { d.primary.set(down ? 1 : 0) } else { d.secondary.set(down ? 1 : 0) }
        } else if primary { d.primary.spring(down ? 1 : 0,at: now,damping: 22.1,stiffness: 157.9) }
        else { d.secondary.spring(down ? 1 : 0,at: now,damping: 22.1,stiffness: 157.9) }
        revision += 1
    }
    func reset() { d = State(); selected = 0; needsFrames = false; revision += 1 }
    func setActive(_ active: Bool, at date: Date) {
        let now = date.timeIntervalSinceReferenceDate
        if !active && d.playing { d.pauseAt = d.previousFrame ?? now }
        d.active = active; d.previousFrame = now
        if active && sensor.isDeviceMotionAvailable { sensor.deviceMotionUpdateInterval = 0.04; sensor.startDeviceMotionUpdates() }
        else { sensor.stopDeviceMotionUpdates() }
    }
    private func updateSelected(now: Double, silent: Bool) {
        let value = Int(M.clamp(d.p.value,0,2).rounded())
        guard value != selected, now-noticeAt >= 0.1 else { return }
        noticeAt = now
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.selected = value
            if !silent { UIAccessibility.post(notification: .announcement,argument: "\(SolidMorphOnboardingPages.all[value].title.replacingOccurrences(of: "\n",with: " ")). Page \(value+1) of 3") }
        }
    }
    private func buzz(_ kind: Int, _ now: Double) {
        guard now-cueAt >= 0.1 else { return }; cueAt = now
        if kind == 1 { UISelectionFeedbackGenerator().selectionChanged() }
        else { UIImpactFeedbackGenerator(style: kind == 2 ? .light : .rigid).impactOccurred() }
    }
}
