import CoreMotion
import Foundation
import UIKit

/// The same live session, gesture handoff, ring joint and exchange rules as NumeralBadge/Page.
@MainActor
final class FlipFocusBadgeDriver {
    typealias M = FlipFocusBadgeMotion
    typealias V = FlipFocusBadgeValue
    var yaw = V(0), pitch = V(0), depth = V(1), press = V(1), popPlus = V(1), popHr = V(1)
    var hint = V(0), labels = V(0), roll = V(1), fade = V(1), pill = V(0)
    private(set) var fingerDown = false
    private(set) var playing = false
    private(set) var running = false
    private(set) var range = 0
    private var autoEnabled = false, reduce = false, active = true, initialized = false
    private var lastFrame: Date?, epoch: Date?, rangeDate: Date?
    private var inactiveAt: Date?
    private var clockT = 0.0, lastTouch = -1e9, touchStart = 0.0, lastTx = 0.0, lastTy = 0.0
    private var maxTravel = 0.0, panActive = false, hold = false, holdFailed = false
    private var resuming = false, settlePending = false, settleTo = 0.0, flattenPending = false
    private var holdPopPending = false
    private var fadeTo: Double?, ring = 0.0, ringW = 0.0, value = 10.0, target = 10.0, risen = true
    private var lastK = 0, lastFace = false, lastCue = -1.0, lapped = 1e9, glintAt = -10.0
    private var glintOn = false
    private var total = 37440.0, elapsed = 0.0, clock = 0.0, orbit = 10.4, hoursSeen = 10
    private var labA = 10, labB = 10, rollFrom = "10h 24m", rollTo = "10h 24m"
    private var lastLook = M.lookAt(0)
    private let motion = CMMotionManager()
    private var zeroRoll: Double?, sensorSeen = 0
    private var deviceYaw = 0.0, deviceOn = false
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let soft = UIImpactFeedbackGenerator(style: .soft)
    private let selection = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()

    struct Frame {
        var look: M.Look
        var playing: Bool, running: Bool
        var fill: Double, hint: Double, labels: Double, roll: Double, sweep: Double, fade: Double, pill: Double
        var labA: Int, labB: Int, range: Int
        var rollFrom: String, rollTo: String
        var rangeFade: Double
    }
    func startSensors() {
        guard motion.isDeviceMotionAvailable, !motion.isDeviceMotionActive else { return }
        motion.deviceMotionUpdateInterval = 0.016
        motion.showsDeviceMovementDisplay = true
        // Reanimated's default iOSReferenceFrame is Auto, with the same fallback.
        motion.startDeviceMotionUpdates(using: motion.isMagnetometerAvailable ? .xArbitraryCorrectedZVertical : .xArbitraryZVertical)
    }
    func stopSensors() { motion.stopDeviceMotionUpdates() }
    func configure(auto: Bool, reduce: Bool, at date: Date) {
        autoEnabled = auto; self.reduce = reduce
        initialized = true; playing = auto && !reduce; epoch = date; lastFrame = date
        if playing { resetSession() }
    }
    func setActive(_ active: Bool, at date: Date) {
        if !active { inactiveAt = date; stopSensors() }
        else {
            if let inactiveAt, let epoch { self.epoch = epoch.addingTimeInterval(date.timeIntervalSince(inactiveAt)) }
            inactiveAt = nil; lastFrame = date; startSensors()
        }
        self.active = active
    }
    func frame(at date: Date, auto: Bool, reduce: Bool, pin: Double?) -> Frame {
        if !initialized { configure(auto: auto, reduce: reduce, at: date) }
        let dt = active ? min(max(date.timeIntervalSince(lastFrame ?? date), 0), 0.05) : 0
        lastFrame = date
        clockT += dt
        readDeviceTurn()
        pill.step(dt)
        if let pin {
            lastLook = M.lookAt(pin)
            return snapshot(at: date, loopPage: playing)
        }
        if playing {
            let t = max(0, date.timeIntervalSince(epoch ?? date)).truncatingRemainder(dividingBy: M.loop)
            lastLook = M.lookAt(t)
            return snapshot(at: date, loopPage: true)
        }
        if autoEnabled && !reduce && !fingerDown {
            let idle = clockT - lastTouch
            if idle > 2 && !resuming {
                resuming = true
                let home = floor(yaw.value / 360 + 0.5) * 360
                yaw.spring(home, .glide); pitch.spring(0, .glide); depth.spring(1, .glide); press.spring(1, .glide)
                popPlus.set(1); popHr.set(1); settlePending = false; flattenPending = false; holdPopPending = false
            }
            if idle > 3.1 && resuming {
                resuming = false; playing = true; epoch = date; resetSession(); lastLook = M.lookAt(0)
                return snapshot(at: date, loopPage: true)
            }
        }
        if fingerDown && !hold && !holdFailed && maxTravel <= 18 && clockT - touchStart >= 0.22 {
            hold = true; flattenPending = false; depth.cancel(); depth.spring(0.04, .snap, reduce: reduce); cue(.press)
        }
        let yawDone = yaw.step(dt)
        pitch.step(dt); press.step(dt)
        let plusDone = popPlus.step(dt)
        popHr.step(dt)
        if holdPopPending && plusDone {
            holdPopPending = false
            popPlus.spring(1, .pop, delay: 0.04); popHr.spring(1, .pop, delay: 0.08)
        }
        hint.step(dt); roll.step(dt)
        if labels.step(dt) { labA = labB }
        if depth.step(dt) && flattenPending {
            flattenPending = false; value = target; popPlus.set(0.6); popHr.set(0.6); startRise()
        }
        if fade.step(dt), let to = fadeTo {
            yaw.set(to); fadeTo = nil; fade.timing(1, duration: 0.1)
        }
        if yawDone && settlePending { settlePending = false; land(settleTo) }
        deviceTurn(dt)
        if running {
            elapsed += dt * 24; clock = elapsed
            orbit = (total + elapsed) / 3600
            let h = Int(floor(orbit))
            if h > hoursSeen {
                hoursSeen = h; target = Double(h); labA = labB; labB = h
                labels.set(0); labels.timing(1, duration: 0.3, reduce: reduce)
            }
        } else { orbit = total / 3600 }
        var ringYaw = yaw.value
        if !reduce {
            (ring, ringW) = M.jointStep(ring, ringW, yaw.value, dt)
            ringYaw = M.limitLag(yaw.value, ring)
        } else { ring = yaw.value }
        let front = cos(yaw.value * .pi / 180)
        if value.rounded() != target.rounded() && risen {
            if front < 0.02 {
                value = target; depth.set(0.04); popPlus.set(0.6); popHr.set(0.6); risen = false
            } else if front > 0.5 && !hold {
                risen = false; flattenPending = true
                depth.timing(0.04, duration: 0.16, reduce: reduce)
                if reduce { flattenPending = false; value = target; popPlus.set(0.6); popHr.set(0.6); startRise() }
            }
        }
        if !risen && front > 0.25 && value.rounded() == target.rounded() && !hold {
            startRise(); if !fingerDown { cue(.success) }
        }
        let kk = Int(floor(yaw.value / 180))
        if kk != lastK {
            lastK = kk
            if fingerDown && clockT - lastCue >= 0.06 { lastCue = clockT; cue(.selection) }
        }
        let lap = floor(orbit)
        if lap > lapped { lapped = lap; glintAt = clockT; glintOn = true; cue(.selection) }
        else if lap < lapped { lapped = lap }
        func pulse(_ c: Double) -> Double { glintOn && !reduce ? exp(-pow((clockT - glintAt - c) / 0.07, 2)) : 0 }
        var look = M.Look()
        look.yaw = yaw.value; look.pitch = pitch.value; look.ringYaw = ringYaw
        look.depth = depth.value; look.value = value; look.clock = clock; look.sat = orbit - floor(orbit)
        look.glintPlus = pulse(0.1); look.glintHead = pulse(0.16); look.glintSat = pulse(0.22)
        look.press = press.value; look.popPlus = popPlus.value; look.popHr = popHr.value
        look.env = reduce ? 0 : 5 * .pi / 180 * sin(2 * .pi * 0.123 * clockT); look.tPhase = clockT
        lastLook = look
        return snapshot(at: date, loopPage: false)
    }
    private func snapshot(at date: Date, loopPage: Bool) -> Frame {
        let frac = (total + elapsed) / 3600
        return Frame(look: lastLook, playing: loopPage, running: running,
                     fill: loopPage ? lastLook.fill : frac - floor(frac), hint: loopPage ? lastLook.hint : hint.value,
                     labels: loopPage ? lastLook.labelK : labels.value, roll: loopPage ? lastLook.sheetK : roll.value,
                     sweep: loopPage ? lastLook.sweep : -1, fade: fade.value, pill: pill.value,
                     labA: loopPage ? 10 : labA, labB: loopPage ? 11 : labB, range: range,
                     rollFrom: loopPage ? "10h 24m" : rollFrom, rollTo: loopPage ? "11h 00m" : rollTo,
                     rangeFade: rangeDate.map { M.timing(date.timeIntervalSince($0) / 0.2) } ?? 1)
    }
    func begin(at date: Date) {
        if !initialized { configure(auto: autoEnabled, reduce: reduce, at: date) }
        if playing {
            yaw.set(lastLook.yaw); pitch.set(lastLook.pitch); depth.set(lastLook.depth)
            ring = lastLook.ringYaw; ringW = 0; value = lastLook.value
            popPlus.set(lastLook.popPlus); popHr.set(lastLook.popHr); press.set(lastLook.press)
            lastK = Int(floor(lastLook.yaw / 180)); lastFace = M.isBack(lastLook.yaw); risen = true
            flattenPending = false; holdPopPending = false
        }
        playing = false; resuming = false; fingerDown = true; settlePending = false
        yaw.cancel(); pitch.cancel(); press.cancel()
        lastTouch = clockT; touchStart = clockT; lastTx = 0; lastTy = 0
        maxTravel = 0; panActive = false; holdFailed = false
        press.spring(1.03, .snap, reduce: reduce); cue(.onset)
    }
    func move(_ translation: CGSize) {
        let tx = translation.width, ty = translation.height
        maxTravel = max(maxTravel, hypot(tx, ty))
        if maxTravel >= 2 { panActive = true }
        if maxTravel > 18 {
            holdFailed = true
            if hold { releaseHold() }
        }
        guard panActive else { return }
        yaw.set(yaw.value + (tx - lastTx) * 0.6)
        pitch.set(M.rubber(pitch.value + (ty - lastTy) * 0.35))
        lastTx = tx; lastTy = ty
    }
    func end(velocity: CGSize) {
        guard fingerDown else { return }
        let v = velocity.width * 0.6, travel = max(-540, min(540, v * 0.18))
        settleTo = M.nearestFace(yaw.value + travel); settlePending = true
        yaw.spring(settleTo, .settle, velocity: v, reduce: reduce)
        pitch.spring(0, .tilt, velocity: -velocity.height * 0.35, reduce: reduce)
        if reduce { settlePending = false; land(settleTo) }
        fingerDown = false; lastTouch = clockT; press.spring(1, .snap, reduce: reduce)
        if hold { releaseHold() }
    }
    private func releaseHold() {
        hold = false; depth.spring(1, .rise, reduce: reduce)
        // RN's sequence first writes 0.6 over 1 ms, then delays the pop 40/80 ms.
        if reduce {
            popPlus.set(1); popHr.set(1)
        } else {
            holdPopPending = true
            popPlus.timing(0.6, duration: 0.001); popHr.timing(0.6, duration: 0.001)
        }
        cue(.press)
    }

    func activate() {
        playing = false
        settleTo = M.nearestFace(yaw.value) + 180
        if reduce {
            fadeTo = settleTo; fade.timing(0, duration: 0.1); emitFace(M.isBack(settleTo))
        } else { settlePending = true; yaw.spring(settleTo, .settle) }
        // RN accessibility activation deliberately leaves the last-touch clock unchanged.
    }
    func select(_ index: Int, at date: Date) {
        range = index; rangeDate = date
        pill.spring(Double(index), .snap, reduce: reduce)
    }
    private func resetSession() {
        running = false; elapsed = 0; total = 37440; orbit = 10.4; hint.set(0); hoursSeen = 10
        roll.set(1); labels.set(0); target = 10; labA = 10; labB = 10
        rollFrom = "10h 24m"; rollTo = "10h 24m"
    }
    private func startRise() {
        holdPopPending = false
        depth.spring(1, .rise, reduce: reduce)
        popPlus.spring(1, .pop, delay: 0.04, reduce: reduce); popHr.spring(1, .pop, delay: 0.08, reduce: reduce)
        risen = true
    }
    private func land(_ to: Double) {
        let back = M.isBack(to)
        if back != lastFace { lastFace = back; emitFace(back); cue(.land) }
    }
    private func emitFace(_ back: Bool) {
        if back {
            guard !running else { return }
            running = true; elapsed = 0; clock = 0; hint.timing(1, duration: 0.3, reduce: reduce)
        } else {
            guard running else { return }
            running = false; hint.timing(0, duration: 0.3, reduce: reduce)
            let el = elapsed; elapsed = 0
            if el < 60 { cue(.onset); return }
            let before = total; total += el
            rollFrom = Self.format(before); rollTo = Self.format(total)
            roll.set(0); roll.timing(1, duration: 0.7, delay: 0.2, reduce: reduce)
        }
    }
    private func readDeviceTurn() {
        guard let sample = motion.deviceMotion else { deviceOn = false; return }
        let r = sample.attitude.roll
        guard r.isFinite else { deviceOn = false; return }
        if zeroRoll == nil { zeroRoll = r }
        sensorSeen += 1
        deviceYaw = -(r - (zeroRoll ?? r)) * 180 / .pi
        deviceOn = sensorSeen > 5
    }
    private func deviceTurn(_ dt: Double) {
        guard deviceOn, !fingerDown, !resuming else { return }
        let diff = (deviceYaw - yaw.value + 540).truncatingRemainder(dividingBy: 360)
        let short = (diff + 360).truncatingRemainder(dividingBy: 360) - 180
        yaw.set(yaw.value + short * min(dt / 0.06, 1))
        let back = M.isBack(yaw.value)
        if back != lastFace { lastFace = back; emitFace(back); cue(.land) }
    }
    private enum Cue { case onset, land, press, selection, success }
    private func cue(_ cue: Cue) {
        guard !playing else { return }
        switch cue {
        case .onset: light.impactOccurred()
        case .land: medium.impactOccurred()
        case .press: soft.impactOccurred()
        case .selection: selection.selectionChanged()
        case .success: success.notificationOccurred(.success)
        }
    }
    private static func format(_ seconds: Double) -> String {
        let s = max(0, Int(floor(seconds + 0.5)))
        return "\(s / 3600)h \(String(format: "%02d", s % 3600 / 60))m"
    }
}
