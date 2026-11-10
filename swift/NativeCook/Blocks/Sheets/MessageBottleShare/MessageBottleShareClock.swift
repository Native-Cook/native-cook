import SwiftUI
import UIKit

final class MessageBottleShareClock: ObservableObject {
    typealias M = MessageBottleShareMotion
    struct Sample {
        var frame: M.Frame
        var time: Double
        var unsentOpacity = 1.0, sentOpacity = 0.0
        var unsentY = 0.0, sentY = 6.0
    }
    @Published var fading = false
    @Published var revision = 0
    private var date: Date?
    private var now = 0.0, seaT = 0.0
    private var mode = 0, phase = 0
    private var phaseAt = 0.0, lastTouch = -100.0
    private var bottle = M.Bottle()
    private var splash = -1.0, sent = false
    private var from = M.Bottle()
    private var dragX = 0.0, dragY = 0.0
    private var held = false
    private var returnAt = -1.0, returnX = 0.0, returnY = 0.0
    private var fadeAt = -1.0, fadeFrom = 1.0, fadeTo = 1.0
    private var sentChanged = -100.0
    private var finger = 0.0, fingerX = 0.0, fingerY = 0.0, fingerPress = 0.0
    private var fingerAt = -100.0, fingerFrom = 0.0
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let success = UINotificationFeedbackGenerator()

    func reset(_ auto: Bool, _ reduced: Bool) {
        mode = auto && !reduced ? 0 : 1
        date = nil
        if !auto || reduced {
            finger = 0; fingerPress = 0; fingerFrom = 0
            if sent || bottle.floating < 0.5 { setSent(true); bottle.alpha = 0; go(4) }
            else { bottle.alpha = 1; go(0) }
        }
        fading = reduced && (now-sentChanged < 0.44 || fadeAt >= 0 || returnAt >= 0)
        revision += 1
    }
    func active() { date = nil }
    private func go(_ p: Int) { phase = p; phaseAt = now }
    private func setSent(_ value: Bool) {
        if sent != value { sent = value; sentChanged = now }
    }
    private func takeOver(_ l: M.Layout) {
        lastTouch = now
        if mode == 0 {
            mode = 1
            fingerAt = now; fingerFrom = finger; fingerPress = 0
            if sent || (bottle.scale < l.scale*0.5 && bottle.alpha < 0.99) {
                go(4); setSent(true); bottle.alpha = 0
            } else if bottle.floating > 0.5 { go(0) }
            else { go(4) }
        }
    }
    private func throwFrom(_ reduced: Bool) {
        if reduced { date = nil }
        from = bottle
        medium.impactOccurred()
        if reduced {
            fadeFrom = bottle.alpha; fadeTo = 0; fadeAt = now; fading = true
            go(4); setSent(true)
        } else { go(2) }
    }
    private func again(_ l: M.Layout, _ reduced: Bool) {
        if reduced { date = nil }
        setSent(false); splash = -1
        if reduced {
            bottle = M.Bottle(x: l.homeX, y: l.homeY, angle: M.restAngle, scale: l.scale, floating: 1, alpha: 0)
            fadeFrom = 0; fadeTo = 1; fadeAt = now; fading = true
            go(0)
        } else { go(5) }
    }
    func press(_ l: M.Layout, _ reduced: Bool) {
        takeOver(l)
        if phase == 0 { throwFrom(reduced) }
        else if phase == 4 { again(l, reduced) }
        revision += 1
    }
    func activateBottle(_ l: M.Layout, _ reduced: Bool) {
        takeOver(l)
        if phase == 0 { throwFrom(reduced) }
        revision += 1
    }
    func touch(_ state: UIGestureRecognizer.State, _ translation: CGSize, _ velocity: CGPoint,
               _ l: M.Layout, _ reduced: Bool) {
        switch state {
        case .began:
            takeOver(l)
            guard phase == 0 || phase == 1 else { return }
            if phase == 0 { dragX = 0; dragY = 0 }
            held = true; returnAt = -1; go(1)
            light.impactOccurred()
        case .changed:
            lastTouch = now
            guard held else { return }
            dragX = translation.width; dragY = min(translation.height, 30)
        case .ended, .cancelled, .failed:
            lastTouch = now
            guard held else { return }
            held = false
            if velocity.y < -500 || translation.height < -70 { throwFrom(reduced) }
            else {
                returnAt = now; returnX = dragX; returnY = dragY
                if reduced { date = nil; fading = true }
            }
        default: return
        }
        revision += 1
    }
    func sample(_ at: Date, _ l: M.Layout, _ auto: Bool, _ reduced: Bool, _ pinned: Double?) -> Sample {
        if let pinned {
            let f = M.frameAt(pinned, l)
            return Sample(frame: f, time: pinned, unsentOpacity: f.sent ? 0 : 1,
                          sentOpacity: f.sent ? 1 : 0, unsentY: f.sent ? 6 : 0, sentY: f.sent ? 0 : 6)
        }
        // The visual clock follows wall time on Xcode Cloud, including slow GPU-less frames.
        let dt = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0))
        date = at; now += dt
        let previous = seaT
        if !reduced { seaT = (seaT+dt).truncatingRemainder(dividingBy: M.loop) }
        if mode == 0 {
            let f = M.frameAt(seaT, l)
            bottle = f.bottle; splash = f.splash; setSent(f.sent)
            finger = f.finger; fingerX = f.fingerX; fingerY = f.fingerY; fingerPress = f.fingerPress
        } else {
            let tau = now-phaseAt, b = M.bob(seaT)
            if returnAt >= 0 {
                let e = M.cssEase(M.seg(now-returnAt, 0, 0.35))
                dragX = returnX*(1-e); dragY = returnY*(1-e)
                if abs(dragX) < 0.5 && abs(dragY) < 0.5 && !held { phase = 0; returnAt = -1 }
            }
            if fadeAt >= 0 {
                // withTiming's default in-out quadratic easing, 200 ms.
                let p = M.seg(now-fadeAt, 0, 0.2)
                let e = p < 0.5 ? 2*p*p : 1-pow(-2*p+2, 2)/2
                bottle.alpha = fadeFrom+(fadeTo-fadeFrom)*e
                if p >= 1 {
                    fadeAt = -1
                }
            }
            finger = fingerFrom * (1 - { (p: Double) in p < 0.5 ? 2*p*p : 1-pow(-2*p+2, 2)/2 }(M.seg(now-fingerAt, 0, 0.15)))
            switch phase {
            case 0:
                bottle = M.Bottle(x: l.homeX, y: l.homeY+b.dy, angle: M.restAngle+b.da,
                                  scale: l.scale, floating: 1, alpha: bottle.alpha)
            case 1:
                bottle = M.Bottle(x: l.homeX+dragX, y: l.homeY+b.dy+dragY,
                                  angle: M.restAngle+b.da+dragX*0.002, scale: l.scale,
                                  floating: max(0, 1+dragY/40), alpha: 1)
            case 2:
                bottle = M.flightAt(l, from.x, from.y, from.angle, min(1, tau/M.flight))
                if tau >= M.flight { go(3); success.notificationOccurred(.success); setSent(true) }
            case 3:
                bottle = M.driftAt(l, tau); splash = tau
                if tau >= M.drift+0.6 { go(4) }
            case 4:
                splash = -1
                if !reduced { bottle.alpha = 0 }
            case 5:
                bottle = M.riseAt(l, seaT, min(1, tau/1.4))
                if tau >= 1.4 { go(0) }
            default: break
            }
            if auto && phase == 0 && now-lastTouch > 2 && seaT < previous { mode = 0 }
        }
        if reduced && fading && fadeAt < 0 && now-sentChanged >= 0.44 && returnAt < 0 {
            DispatchQueue.main.async { [weak self] in self?.fading = false }
        }
        let elapsed = now-sentChanged
        let incoming = M.cssEase(M.seg(elapsed, 0.12, 0.44))
        let outgoing = 1-M.cssEase(M.seg(elapsed, 0, 0.32))
        return Sample(frame: M.Frame(bottle: bottle, splash: splash, sent: sent, finger: finger,
                                     fingerX: fingerX, fingerY: fingerY, fingerPress: fingerPress), time: seaT,
                      unsentOpacity: sent ? outgoing : incoming, sentOpacity: sent ? incoming : outgoing,
                      unsentY: 6*(1-(sent ? outgoing : incoming)), sentY: 6*(1-(sent ? incoming : outgoing)))
    }
}
