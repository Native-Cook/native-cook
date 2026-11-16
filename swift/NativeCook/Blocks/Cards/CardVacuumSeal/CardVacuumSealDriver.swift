import Combine
import SwiftUI
import UIKit

/// The RN controller's autonomous and finger modes share the same scalar p and film uniforms.
@MainActor
final class CardVacuumSealDriver: NSObject, ObservableObject {
    typealias M = CardVacuumSealMotion
    typealias A = CardVacuumSealAnimation
    struct Frame {
        var look = M.Look(), label = 1.0, ambient = 0.0
        var dimple = CGPoint.zero, dimpleK = 0.0, finger = CGPoint.zero, fingerK = 0.0
        var stretch = 0.0, fade = 1.0, seed = 0.0, amp = 1.0, extra = 0.0, shown = 0.0
        var sealed = true
    }
    @Published private(set) var frame = Frame()
    private var link: CADisplayLink?
    private let feedback = CardVacuumSealFeedback()
    private var autoPlay = false, reduced = false, autoMode = false, resuming = false, userDriven = false
    private var pin: Double?, previous: Double?, clock = 0.0, loopTime = 0.0, ambient = 0.0, ambientOffset = 0.0
    private var lastTouch = -1e9, started = 0.0, direction = -1.0, p0 = 1.0, onTile = true, ready = true
    private var offset = 0.0, sqTarget = 1.0, lastP = 1.0, velocity = 0.0, v1 = 0.0, v2 = 0.0, peakSpeed = 0.0
    private var target = 1.0, labelOn = true, lockArmed = false, lastAir = -1.0
    private(set) var fingerDown = false
    private var kind = 0, seed = 0.0, amp = 1.0, extra = 0.0, finger = CGPoint.zero, dimple = CGPoint.zero
    private var p = A(1), press = A(0), flash = A(0), settle = A(1), glint = A(0), discGlint = A(0)
    private var tug = A(0), tugSide = A(0), label = A(1), dimpleK = A(0), fingerK = A(0), stretch = A(0), fade = A(1)

    func configure(autoPlay: Bool,reduced: Bool,pin: Double?) {
        stop()
        self.autoPlay = autoPlay; self.reduced = reduced; self.pin = pin
        autoMode = autoPlay && !reduced; resuming = false; userDriven = false
        previous = nil; clock = 0; loopTime = 0; ambient = 0; ambientOffset = 0
        fingerDown = false; lastTouch = -1e9; lastP = 1; target = 1; labelOn = true; lockArmed = false
        seed = 0; amp = 1; extra = 0; peakSpeed = 0; lastAir = -1
        p = A(1); press = A(0); flash = A(0); settle = A(1); glint = A(0); discGlint = A(0)
        tug = A(0); tugSide = A(0); label = A(1); dimpleK = A(0); fingerK = A(0); stretch = A(0); fade = A(1)
        frame = Frame()
        if let pin {
            let t = M.wrap(pin)
            frame.look = M.lookAt(t); frame.label = M.labelAt(t); frame.ambient = t; frame.shown = 1
            return
        }
        let link = CADisplayLink(target: self,selector: #selector(tick(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60,maximum: 120,preferred: 120)
        link.add(to: .main,forMode: .common); self.link = link
    }
    func stop() { link?.invalidate(); link = nil; previous = nil; feedback.stop() }
    func setActive(_ active: Bool) {
        previous = nil
        if !active { link?.isPaused = true; feedback.stop() }
        else { wake() }
    }
    private func wake() { previous = nil; link?.isPaused = false }

    @objc private func tick(_ sender: CADisplayLink) {
        let dt = min(max(0,sender.timestamp-(previous ?? (sender.timestamp-1/60))),0.05)
        previous = sender.timestamp; clock += dt
        let shown = reduced ? 1 : M.easeOut(M.clamp(clock/0.42))
        if autoMode && !reduced {
            loopTime = M.wrap(loopTime+dt); ambient = loopTime+ambientOffset
            let look = M.lookAt(loopTime)
            p.set(look.p); lastP = look.p; label.set(M.labelAt(loopTime)); labelOn = label.value > 0.5
            frame = Frame(look: look,label: label.value,ambient: ambient,shown: shown,sealed: frame.sealed)
            return
        }
        let prev = lastP
        _ = p.sample(clock); _ = press.sample(clock); _ = flash.sample(clock); _ = settle.sample(clock)
        _ = glint.sample(clock); _ = discGlint.sample(clock); _ = tug.sample(clock); _ = tugSide.sample(clock)
        _ = label.sample(clock); _ = dimpleK.sample(clock); _ = fingerK.sample(clock); _ = stretch.sample(clock); _ = fade.sample(clock)
        if !reduced {
            if userDriven || resuming || autoPlay { ambient += dt }
            if fingerDown && kind == 0 && onTile { p.set(M.clamp(p.value+direction*(direction > 0 ? 0.8 : 1/0.9)*dt)) }
            else if fingerDown && kind == 1 {
                if !ready && p.value >= 0.295 {
                    ready = true; offset = p.value-M.fingerP(finger.x-177,finger.y-111.5); sqTarget = p.value
                } else if ready { p.set(min(1,max(0.3,sqTarget))) }
            }
            let v = dt > 0 ? (p.value-prev)/dt : 0
            velocity = (v+v1+v2)/3; v2 = v1; v1 = v
            if fingerDown && kind != 2 && p.value > 0.3 && p.value < 0.95 && abs(velocity) > peakSpeed {
                peakSpeed = abs(velocity); amp = M.creaseAmp(peakSpeed); extra = peakSpeed > 2.2 ? 1 : 0
            }
            if fingerDown && kind != 2 {
                for (i,k) in [0.45,0.6,0.75,0.9].enumerated() where (prev < k && p.value >= k) || (prev > k && p.value <= k) { feedback.detent(hard: i == 3) }
                if p.value > 0.02 && p.value < 0.985 && abs(velocity) > 0.05 {
                    if ambient-lastAir > 0.07 { lastAir = ambient; feedback.air(0.8+0.6*M.clamp(p.value)) }
                } else if lastAir > 0 { lastAir = -1; feedback.air(nil) }
            } else if lastAir > 0 { lastAir = -1; feedback.air(nil) }
            syncLabel()
            if p.value < 0.9 { lockArmed = true }
            if lockArmed && p.value >= 0.999 {
                lockArmed = false
                if userDriven { feedback.lock(sound: true) }
                announce(true)
                settle.animate([.timing(1.03,0.06,.standard),.spring(1,12,120,0)],at: clock)
                glint.animate([.timing(1+0.6*extra,0.07,.standard),.timing(0,0.26,.standard)],at: clock)
                discGlint.animate([.delay(0.06),.timing(1,0.04,.standard),.timing(0,0.08,.standard)],at: clock)
                tug.animate([.timing(1,0.07,.standard),.spring(0,14,260,0)],at: clock)
                tugSide.animate([.delay(0.04),.timing(1,0.07,.standard),.spring(0,14,260,0)],at: clock)
            }
            if prev > 0.001 && p.value <= 0.001 {
                if userDriven { feedback.airBack(sound: true) }
                announce(false); peakSpeed = 0; amp = 1; extra = 0; seed = (seed+1).truncatingRemainder(dividingBy: 3)
            }
        }
        lastP = p.value
        var look = M.Look()
        look.p = reduced ? 1 : p.value; look.press = reduced ? 0 : press.value; look.flash = reduced ? 0 : flash.value
        look.settle = settle.value; look.glint = glint.value; look.discGlint = discGlint.value
        look.tug = tug.value; look.tugSide = tugSide.value
        frame = Frame(look: look,label: label.value,ambient: ambient,dimple: dimple,dimpleK: dimpleK.value,
                      finger: finger,fingerK: fingerK.value,stretch: stretch.value,fade: fade.value,seed: seed,amp: amp,extra: extra,
                      shown: shown,sealed: frame.sealed)
        if reduced { link?.isPaused = true; return }
        if !reduced && autoPlay && !fingerDown && ambient-lastTouch > 2 && dimpleK.value < 0.01 && fingerK.value < 0.01 {
            if !resuming {
                resuming = true; userDriven = false; target = 1
                if p.value < 0.999 { p.animate([.spring(1,22.1,157.9,0)],at: clock) }
            } else if abs(p.value-1) < 0.002 {
                resuming = false; loopTime = 0; ambientOffset = M.wrap(ambient); seed = 0; autoMode = true
            }
        }
        let moving = [p,press,flash,settle,glint,discGlint,tug,tugSide,label,dimpleK,fingerK,stretch,fade].contains { $0.moving }
        if !fingerDown && !moving && shown >= 1 && !(autoPlay && !reduced) { link?.isPaused = true }
    }
    private func syncLabel() {
        if !labelOn && p.value >= 0.999 { labelOn = true; label.animate([.timing(1,0.38,.label)],at: clock) }
        else if labelOn && p.value < 0.97 { labelOn = false; label.animate([.timing(0,0.38,.label)],at: clock) }
    }
    private func announce(_ sealed: Bool) {
        guard frame.sealed != sealed else { return }
        frame.sealed = sealed
        UIAccessibility.post(notification: .announcement,argument: sealed ? "Card protected" : "Card protection off")
    }
    private func takeOver() {
        wake(); userDriven = true; velocity = 0; v1 = 0; v2 = 0
        if autoMode { target = frame.look.p >= 0.5 ? 1 : 0; lockArmed = frame.look.p < 0.9; autoMode = false }
        resuming = false
    }
    func beginHold() {
        guard pin == nil, !fingerDown else { return }
        takeOver(); fingerDown = true; kind = 0
        press.animate([.spring(1,18,400,0)],at: clock)
        flash.animate([.timing(1,0.09,.standard),.timing(0,0.32,.standard)],at: clock)
        feedback.onset()
        started = ambient; p0 = p.value; direction = target > 0.5 ? -1 : 1; onTile = true; p.set(p.value)
        if reduced { press.set(1); flash.set(0) }
    }
    func moveHold(_ point: CGPoint,scale: Double) {
        guard fingerDown && kind == 0 else { return }
        let on = point.x > -16 && point.y > -16 && point.x < 112*scale+16 && point.y < 112*scale+16
        if on != onTile { onTile = on; press.animate([.spring(on ? 1 : 0.45,18,400,0)],at: clock) }
    }
    func beginFilm(_ point: CGPoint) {
        guard pin == nil, !reduced, !fingerDown else { return }
        takeOver(); fingerDown = true; lastTouch = ambient; p0 = p.value; p.set(p.value)
        let qx = point.x-177, qy = point.y-111.5
        if p0 >= 0.95 && point.y <= 223-28 && point.x <= 354-28 {
            kind = 2; dimple = point; dimpleK.animate([.spring(1,14,260,0)],at: clock); feedback.dimple(); return
        }
        kind = 1; started = ambient; finger = point; fingerK.animate([.timing(1,0.09,.standard)],at: clock)
        if p0 >= 0.95 { ready = true; offset = 1-M.fingerP(qx,qy); sqTarget = 1 }
        else {
            peakSpeed = 0; amp = 1; extra = 0; ready = p0 >= 0.295
            if !ready { p.animate([.spring(0.3,22,210,0)],at: clock) }
            target = 1; offset = ready ? p0-M.fingerP(qx,qy) : 0
            sqTarget = ready ? p0 : M.fingerP(qx,qy)
        }
        feedback.onset()
    }
    func moveFilm(_ point: CGPoint) {
        guard fingerDown else { return }
        if kind == 2 { dimple = point; return }
        guard kind == 1 else { return }
        let qx = point.x-177, qy = point.y-111.5
        sqTarget = min(1,max(0.3,M.fingerP(qx,qy)+offset))
        if ready { p.set(sqTarget) }
        finger = CGPoint(x: min(point.x,354),y: min(point.y,223))
        stretch.set(min(8,max(0,qy-111.5)*0.3))
    }
    func end() {
        guard fingerDown else { return }
        fingerDown = false; lastTouch = ambient
        if kind == 0 {
            press.animate([.spring(0,18,400,0)],at: clock)
            if reduced {
                press.set(0); target = target > 0.5 ? 0 : 1; p.set(target); fade.set(target)
                labelOn = target == 1; label.set(target)
                if target == 1 { feedback.lock(sound: false) } else { feedback.airBack(sound: false) }
                announce(target == 1); wake(); return
            }
            let sealing = direction > 0
            if ambient-started < 0.2 && abs(p.value-p0) < 0.2 {
                target = sealing ? 1 : 0
                p.animate([.timing(target,abs(target-p.value)*(sealing ? 1.25 : 0.9),.toggle)],at: clock)
            } else {
                let commit = sealing ? p.value >= 0.62 : p.value < 0.62
                target = commit == sealing ? 1 : 0
                p.animate([target == 1 ? .spring(1,26,180,sealing ? 0.8 : 0) : .spring(0,20,140,sealing ? 0 : -1/0.9)],at: clock)
            }
        } else if kind == 2 { dimpleK.animate([.spring(0,14,260,0)],at: clock) }
        else {
            fingerK.animate([.timing(0,0.16,.standard)],at: clock); stretch.animate([.spring(0,14,260,0)],at: clock)
            target = p.value+0.12*velocity >= 0.62 ? 1 : 0
            p.animate([target == 1 ? .spring(1,26,180,velocity) : .spring(0,20,140,velocity)],at: clock)
        }
        wake()
    }
    func toggleAccessible() {
        guard pin == nil else { return }
        // The reference's accessibility action is a 220 ms timing and does not mark the action userDriven.
        wake(); autoMode = false; target = frame.sealed ? 0 : 1
        if reduced { p.set(target); fade.set(target); labelOn = target == 1; label.set(target) }
        else { p.animate([.timing(target,0.22,.standard)],at: clock) }
        lastTouch = ambient; announce(target == 1)
    }
}
