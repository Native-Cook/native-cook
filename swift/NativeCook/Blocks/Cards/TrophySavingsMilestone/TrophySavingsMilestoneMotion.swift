import Foundation

/// Direct numerical port of the approved RN controller. Pose 0→1 feeds; 1→2 releases right.
struct TrophySavingsMilestoneMotion: Sendable {
    enum Mode: Sendable { case idle, loop, drag, event, waiting, paused }
    var mode: Mode = .idle
    var generation = 0
    var progress = 0.0
    var pose = 0.0
    var epoch = 0.0
    var direction = 0.0
    var contactOrigin = 0.0
    var contactPoseOrigin = 0.0
    var contactDirection = 0.0
    var contactVelocity = 0.0
    var translationOrigin = 0.0
    var eventOrigin = 0.0
    var eventPoseOrigin = 0.0
    var eventStarted = 0.0
    var eventDemo = false
    var eventUserPeak = false
    static let peakHold = 0.22
    static func clamp(_ x: Double, _ low: Double = 0, _ high: Double = 1) -> Double { min(high, max(low, x)) }
    static func smooth(_ x: Double) -> Double { let u = clamp(x); return u*u*u*(10-15*u+6*u*u) }
    static func openDuration(_ p: Double) -> Double { max(0.08, 0.58*(1-clamp(p))) }
    static func closeDuration(_ p: Double) -> Double { 0.18+0.30*clamp(p) }
    static func closeProgress(_ p: Double, age: Double, velocity: Double = 0) -> Double {
        let p = clamp(p), duration = closeDuration(p)
        if age >= duration { return 0 }
        let u = clamp(age/duration), v = clamp(velocity, -3*p/duration, 0)
        return (2*u*u*u-3*u*u+1)*p+(u*u*u-2*u*u+u)*duration*v
    }
    static func loopProgress(_ time: Double) -> Double {
        let t = (time.truncatingRemainder(dividingBy: 5)+5).truncatingRemainder(dividingBy: 5)
        if t < 0.6 { return 0 }; if t < 1.7 { return smooth((t-0.6)/1.1) }
        if t < 2.6 { return 1 }; if t < 4.4 { return 1-smooth((t-2.6)/1.8) }; return 0
    }
    static func loopPose(_ time: Double) -> Double {
        let t = (time.truncatingRemainder(dividingBy: 5)+5).truncatingRemainder(dividingBy: 5)
        let p = loopProgress(t); return t < 2.6 ? p : 2-p
    }
    func presentedPose(at now: Double) -> Double {
        if mode == .loop { return Self.loopPose(now-epoch) }
        guard mode == .event else { return pose }
        let age = max(0, now-eventStarted)
        if eventDemo {
            let opening = Self.openDuration(eventOrigin)
            if age < opening { return eventPoseOrigin+(1-eventPoseOrigin)*Self.smooth(age/opening) }
            if age < opening+Self.peakHold { return 1 }
            return 2-Self.closeProgress(1, age: age-opening-Self.peakHold)
        }
        let p = Self.closeProgress(eventOrigin, age: age, velocity: contactVelocity)
        return eventPoseOrigin >= 1 ? 2-p : p
    }
    func presentedProgress(at now: Double) -> Double { let q = presentedPose(at: now); return q <= 1 ? q : 2-q }
    var peakTime: Double { eventDemo ? Self.openDuration(eventOrigin) : 0 }
    var endTime: Double { eventDemo ? peakTime+Self.peakHold+Self.closeDuration(1) : Self.closeDuration(eventOrigin) }
    mutating func begin(progress p: Double, demo: Bool, userPeak: Bool, now: Double, velocity: Double = 0) {
        let oldPose = presentedPose(at: now), p = Self.clamp(p)
        pose = p <= 0.000001 && oldPose >= 1.999999 ? 0 : oldPose
        mode = .event; generation += 1; progress = p; eventOrigin = p; eventPoseOrigin = pose
        eventStarted = now; eventDemo = demo; contactVelocity = demo ? 0 : velocity
        eventUserPeak = userPeak && (demo || p >= 0.985)
    }
}
