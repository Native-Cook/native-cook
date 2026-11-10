import SwiftUI
import UIKit

final class SnowGlobeGiftClock: ObservableObject {
    typealias M = SnowGlobeGiftMotion
    @Published var running = false
    var frame = M.Frame()
    var added = false
    var opened = false
    private var date: Date?
    private var now = 0.0, autoStart = 0.0, lastTouch = -100.0
    private var automatic = true, resumeAsked = false, dragging = false
    private var velocity = 0.0, prevX = 0.0, fingerAt = -10.0, fingerFrom = 0.0
    private var addedUntil = -1.0
    private var lidAnimation: SnowGlobeGiftTween?
    private var amountAnimation: SnowGlobeGiftTween?
    private var calmFrom: M.Frame?
    private var calmAt = 0.0
    private var calmVelocity = 0.0
    private var reduced = false
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let success = UINotificationFeedbackGenerator()

    func reset(_ auto: Bool, _ reduced: Bool) {
        frame = M.Frame(); now = 0; autoStart = 0; date = nil
        self.reduced = reduced; automatic = auto && !reduced
        velocity = 0; dragging = false; resumeAsked = false
        lidAnimation = nil; amountAnimation = nil; calmFrom = nil; added = false; opened = false
        running = false
    }
    func active() { date = nil }
    func configure(_ auto: Bool, _ reduced: Bool) {
        self.reduced = reduced; autoStart = now
        if auto && !reduced { automatic = true; return }
        automatic = false
        let on = frame.lid > 0.5
        lidAnimation = .init(from:frame.lid,to:on ? 1 : 0,at:now,duration:0.2,curve:0)
        amountAnimation = .init(from:frame.amount,to:on ? 1 : 0,at:now,duration:0.2,curve:0)
        calmFrom = frame; calmAt = now; calmVelocity = velocity
        running = true; date = Date()
    }
    func sample(_ at: Date, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> M.Frame {
        if let pinned { return M.frameAt(pinned) }
        // Autoplay follows wall time on Xcode Cloud; only live physics uses RN's 50 ms cap.
        let elapsed = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0.016))
        date = at; now += elapsed
        self.reduced = reduced
        if added && now >= addedUntil { added = false }
        if automatic && auto && !reduced {
            frame = M.frameAt((now-autoStart).truncatingRemainder(dividingBy: M.loop))
            opened = frame.lid > 0.5
            return frame
        }
        let wasOpen = frame.lid > 0.5
        if let a = lidAnimation {
            frame.lid = a.value(now); frame.glow = frame.lid
            if a.done(now) { lidAnimation = nil }
        }
        if let a = amountAnimation {
            frame.amount = a.value(now)
            if a.done(now) { amountAnimation = nil }
        }
        if (frame.lid > 0.5) != wasOpen { opened = frame.lid > 0.5 }
        if let from = calmFrom {
            let tween = SnowGlobeGiftTween(from:1,to:0,at:calmAt,duration:0.2,curve:0)
            let k = tween.value(now)
            frame.wobble = from.wobble*k; frame.swirl = from.swirl*k
            frame.finger = from.finger*k; frame.fingerPress = from.fingerPress*k
            velocity = calmVelocity*k
            if tween.done(now) { calmFrom = nil }
        }
        if frame.finger > 0 && calmFrom == nil {
            let p = M.clamp((now-fingerAt)/0.15)
            let e = p < 0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2
            frame.finger = fingerFrom*(1-e)
        }
        if !reduced && calmFrom == nil {
            let dt = min(0.05,elapsed)
            if !dragging {
                let a = -90*frame.wobble-2*0.22*9.5*velocity
                velocity += a*dt
                frame.wobble += velocity*dt
            }
            frame.swirl *= exp(-dt/1.3)
            if frame.swirl < 0.002 { frame.swirl = 0 }
            frame.spin += dt*6*frame.swirl
        }
        if auto && !reduced && !dragging && now-lastTouch >= 2 {
            if !resumeAsked {
                resumeAsked = true
                if frame.lid > 0.001 { setOpen(false) }
            } else if frame.lid < 0.001 && frame.swirl == 0 && abs(frame.wobble) < 0.003 && abs(velocity) < 0.03 {
                resumeAsked = false; frame.wobble = 0; autoStart = now; automatic = true
            }
        }
        let moving = dragging || calmFrom != nil || lidAnimation != nil || amountAnimation != nil || frame.finger > 0 || added || (!reduced && (frame.swirl > 0 || abs(frame.wobble) > 0.00001 || abs(velocity) > 0.0001))
        if running && !moving && !(auto && !reduced) {
            DispatchQueue.main.async { [weak self] in self?.running = false }
        }
        return frame
    }
    func takeOver() {
        lastTouch = now; resumeAsked = false
        if automatic {
            automatic = false; velocity = 0
            fingerAt = now; fingerFrom = frame.finger; frame.fingerPress = 0
            if frame.lid > 0.001 && frame.lid < 0.999 { setOpen(frame.lid > 0.5) }
        }
        date = Date(); running = true
    }
    func setOpen(_ on: Bool) {
        let duration = reduced ? 0.25 : (on ? 0.5 : 0.6)
        lidAnimation = .init(from: frame.lid, to: on ? 1 : 0, at: now, duration: duration, curve: reduced ? 0 : (on ? 1 : 2))
        amountAnimation = .init(from: frame.amount, to: on ? 1 : 0, at: now, duration: reduced ? duration : (on ? 0.45 : 0.25), curve: reduced ? 0 : 1)
        opened = on
        running = true
    }
    func begin() { takeOver() }
    func dragStart(_ x: Double) { if !reduced { dragging = true; prevX = x } }
    func drag(_ x: Double, _ radius: Double) {
        lastTouch = now
        guard dragging else { return }
        let lean = min(0.3,max(-0.3,x/(radius*3)))
        velocity = (lean-frame.wobble)*60; frame.wobble = lean
        frame.swirl = min(1,frame.swirl+abs(x-prevX)*0.004); prevX = x
    }
    func end(_ speed: Double, _ radius: Double, _ wasDrag: Bool, _ cancelled: Bool) {
        lastTouch = now
        if dragging {
            dragging = false; velocity = min(3,max(-3,speed/(radius*3)))
            if frame.swirl > 0.3 { light.impactOccurred() }
        } else if !wasDrag && !cancelled && !reduced {
            takeOver(); velocity += 1.4; frame.swirl = min(1,frame.swirl+0.35)
            light.impactOccurred()
        }
    }
    func accessibilityShake() {
        takeOver()
        if !reduced { frame.swirl = min(1,frame.swirl+0.6) }
    }
    func cta() {
        if !opened { takeOver(); setOpen(true) }
        else { added = true; addedUntil = now+1.8; date = Date(); running = true }
        success.notificationOccurred(.success)
    }
}

struct SnowGlobeGiftTween {
    var from: Double, to: Double, at: Double, duration: Double, curve: Int
    func done(_ now: Double) -> Bool { now-at >= duration }
    func value(_ now: Double) -> Double {
        let p = SnowGlobeGiftMotion.clamp((now-at)/duration)
        let e: Double
        if curve == 1 { e = SnowGlobeGiftMotion.bezier(p,0.23,1,0.32,1) }
        else if curve == 2 { e = SnowGlobeGiftMotion.bezier(p,0.77,0,0.175,1) }
        else { e = p < 0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2 }
        return from+(to-from)*e
    }
}
