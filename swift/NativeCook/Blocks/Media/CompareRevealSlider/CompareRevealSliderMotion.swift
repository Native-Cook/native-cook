import Foundation
import UIKit

/// The divider's physics and the 8 s film, as pure functions. `pos` is 0 at the photo's left edge,
/// 1 at its right; left of the divider is the room before, right of it the room after.
enum CompareRevealSliderMotion {
    static let loop: Double = 8

    static let rest = 0.5
    static let stopMin = 0.07
    static let stopMax = 0.93
    static let rubberBand = 0.085
    static let project = 0.17

    static let springResponse = 0.5
    static let springDamping = 0.6
    private static let omega = 2 * Double.pi / springResponse
    private static let omegaD = omega * (1 - springDamping * springDamping).squareRoot()
    private static let decay = springDamping * omega

    static let handleW = 46.0
    static let handleH = 62.0
    static let handleY = 0.6
    static let pressSquashX = 0.12
    static let pressSquashY = 0.1
    static let stretchSpeed = 2.6
    static let stretchX = 0.2
    static let stretchY = 0.08
    static let lean = 5.0
    static let speedTau = 0.07

    static let glintLaps = 2.0
    static let glintLen = 150.0
    static let lineW = 2.0
    static let glowW = 26.0

    static let eveningS = 0.7
    static let idleS = 2.0
    static let returnS = 0.7
    static let grabRadius = 40.0
    static let glideS = 0.2

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { x < lo ? lo : x > hi ? hi : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func smooth(_ x: Double) -> Double { let c = clamp01(x); return c * c * (3 - 2 * c) }
    static func easeInOutCubic(_ x: Double) -> Double {
        let c = clamp01(x)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }
    static func easeInOutQuad(_ x: Double) -> Double {
        let c = clamp01(x)
        return c < 0.5 ? 2 * c * c : 1 - pow(-2 * c + 2, 2) / 2
    }

    static func rubber(_ p: Double) -> Double {
        if p < stopMin {
            let over = stopMin - p
            return stopMin - over / (1 + over / rubberBand)
        }
        if p > stopMax {
            let over = p - stopMax
            return stopMax + over / (1 + over / rubberBand)
        }
        return p
    }

    static func landing(_ p: Double, _ velocity: Double) -> Double {
        clamp(p + velocity * project, stopMin, stopMax)
    }

    static func springAt(_ from: Double, _ to: Double, _ velocity: Double, _ s: Double) -> Double {
        if s <= 0 { return from }
        let a = from - to
        let b = (velocity + decay * a) / omegaD
        return to + exp(-decay * s) * (a * cos(omegaD * s) + b * sin(omegaD * s))
    }

    static func stretchFor(_ velocity: Double) -> Double { clamp(velocity / stretchSpeed, -1, 1) }

    // MARK: The film

    static let press1 = 0.55
    static let drag1A = 0.7
    static let drag1B = 1.9
    static let left = 0.15
    static let flickA = 2.2
    static let flickB = 2.85
    static let flickTo = 0.6
    static let flickV = (2 * (flickTo - left)) / (flickB - flickA)
    static let right = landing(flickTo, flickV)
    static let eveningAt = 4.2
    static let press2 = 4.75
    static let drag2A = 4.9
    static let drag2B = 6.0
    static let back = 0.4
    static let wiggle = 0.05
    static let wiggleTurns = 2.5
    static let dayAt = 6.15
    static let settled = 7.05
    static let pressS = 0.15

    static func filmPos(_ t: Double) -> Double {
        if t <= drag1A { return rest }
        if t <= drag1B { return rest + (left - rest) * easeInOutCubic(seg(t, drag1A, drag1B)) }
        if t <= flickA { return left }
        if t <= flickB {
            let u = seg(t, flickA, flickB)
            return left + (flickTo - left) * u * u
        }
        if t <= drag2A { return springAt(flickTo, right, flickV, t - flickB) }
        if t <= drag2B {
            let u = seg(t, drag2A, drag2B)
            return right + (back - right) * easeInOutCubic(u) + wiggle * sin(2 * .pi * wiggleTurns * u) * sin(.pi * u)
        }
        let p = springAt(back, rest, 0, t - drag2B)
        return rest + (p - rest) * (1 - smooth(seg(t, settled - 0.35, settled)))
    }

    static func filmVelocity(_ t: Double) -> Double {
        let h = 1.0 / 240
        return (filmPos(min(loop, t + h)) - filmPos(max(0, t - h))) / (2 * h)
    }

    static func filmPress(_ t: Double) -> Double {
        let first = smooth(seg(t, press1, press1 + pressS)) * (1 - smooth(seg(t, flickB, flickB + pressS)))
        let second = smooth(seg(t, press2, press2 + pressS)) * (1 - smooth(seg(t, drag2B, drag2B + pressS)))
        return max(first, second)
    }

    static func filmGhost(_ t: Double) -> Double {
        let first = smooth(seg(t, press1 - 0.2, press1)) * (1 - smooth(seg(t, flickB, flickB + 0.22)))
        let second = smooth(seg(t, press2 - 0.2, press2)) * (1 - smooth(seg(t, drag2B, drag2B + 0.22)))
        return max(first, second)
    }

    static func filmEvening(_ t: Double) -> Double {
        easeInOutCubic(seg(t, eveningAt, eveningAt + eveningS)) * (1 - easeInOutCubic(seg(t, dayAt, dayAt + eveningS)))
    }

    static func glintAt(_ seconds: Double) -> Double {
        let lap = seconds * glintLaps / loop
        return lap - floor(lap)
    }

    static func glintGain(_ stretch: Double) -> Double { 0.55 + 0.45 * min(1, abs(stretch) * 1.6) }
    static func labelBefore(_ pos: Double) -> Double { smooth(seg(pos, 0.12, 0.3)) }
    static func labelAfter(_ pos: Double) -> Double { 1 - smooth(seg(pos, 0.7, 0.88)) }
}

/// The screen's state, advanced from the timeline's date. A plain class on purpose: the drawing reads
/// it inside `TimelineView`, so nothing writes SwiftUI state per frame.
final class CompareRevealSliderClock {
    private typealias M = CompareRevealSliderMotion

    struct Frame: Equatable {
        var pos = CompareRevealSliderMotion.rest
        var press = 0.0
        var stretch = 0.0
        var ghost = 0.0
        var evening = 0.0
        var glint = 0.0
    }

    private enum Mode { case auto, manual, returning }

    private struct Tween {
        var from = 0.0, to = 0.0, at = 0.0, duration = 0.0
        var cubic = false
        func value(_ now: Double) -> Double {
            guard duration > 0 else { return to }
            let u = (now - at) / duration
            return from + (to - from) * (cubic ? CompareRevealSliderMotion.easeInOutCubic(u) : CompareRevealSliderMotion.easeInOutQuad(u))
        }
    }

    private enum Motion {
        case fixed(Double)
        case spring(from: Double, to: Double, velocity: Double, at: Double)
        case tween(Tween)

        func value(_ now: Double) -> Double {
            switch self {
            case .fixed(let v): return v
            case .spring(let from, let to, let velocity, let at): return CompareRevealSliderMotion.springAt(from, to, velocity, now - at)
            case .tween(let t): return t.value(now)
            }
        }
    }

    var film = true
    var reduced = false
    var width = 361.0
    private(set) var holding = false
    private(set) var lastTouch = 0.0
    private(set) var selected = 0

    private var mode: Mode = .auto
    private var loopStart = -1.0
    private var returnAt = 0.0
    private var lastNow = -1.0
    private var cached = Frame()
    private var prevPos = CompareRevealSliderMotion.rest
    private var stretch = 0.0
    private var pos = Motion.fixed(CompareRevealSliderMotion.rest)
    private var press = Tween()
    private var evening = Tween()
    private var ghost = Tween()
    private var grab = Tween()
    private var fingerX = 0.0
    private var stopSide = 0

    func frame(_ now: Double) -> Frame {
        if now <= lastNow { return cached }
        let dt = M.clamp(now - lastNow, 1.0 / 240, 0.05)
        lastNow = now
        if mode == .auto && !film {
            mode = .manual
            pos = .fixed(M.rest)
            press = Tween()
            ghost = Tween()
            evening = Tween()
            stretch = 0
        }
        var glint = 0.0
        if mode == .auto {
            if loopStart < 0 { loopStart = now }
            let t = max(0, now - loopStart).truncatingRemainder(dividingBy: M.loop)
            pos = .fixed(M.filmPos(t))
            press = Tween(to: M.filmPress(t))
            stretch = M.stretchFor(M.filmVelocity(t))
            ghost = Tween(to: M.filmGhost(t))
            evening = Tween(to: M.filmEvening(t))
            glint = M.glintAt(t)
        } else {
            if holding {
                pos = .fixed(M.rubber((fingerX + grab.value(now)) / width))
            } else if film {
                if mode == .manual && now - lastTouch > M.idleS {
                    pos = .spring(from: pos.value(now), to: M.rest, velocity: 0, at: now)
                    evening = Tween(from: evening.value(now), to: 0, at: now, duration: M.returnS, cubic: true)
                    returnAt = now
                    mode = .returning
                } else if mode == .returning && now - returnAt > M.returnS + 0.5 {
                    loopStart = now
                    pos = .fixed(M.rest)
                    stretch = 0
                    mode = .auto
                }
            }
            let p = pos.value(now)
            let target = M.stretchFor((p - prevPos) / dt)
            let k = 1 - exp(-dt / M.speedTau)
            let s = stretch + (target - stretch) * k
            stretch = abs(s) < 0.002 ? 0 : s
            glint = M.glintAt(now)
        }
        let p = pos.value(now)
        prevPos = p
        let e = evening.value(now)
        let nextSelected = e > 0.5 ? 1 : 0
        if nextSelected != selected { selected = nextSelected }
        cached = Frame(pos: p, press: press.value(now), stretch: stretch, ghost: ghost.value(now), evening: e, glint: glint)
        return cached
    }

    /// A finger anywhere: the film stops where it is.
    private func take(_ now: Double) {
        lastTouch = now
        if mode == .manual { return }
        mode = .manual
        pos = .fixed(pos.value(now))
        press = Tween(from: press.value(now), to: 0, at: now, duration: 0.16)
        ghost = Tween(from: ghost.value(now), to: 0, at: now, duration: 0.16)
    }

    func touchBegan(x: Double, _ now: Double) {
        take(now)
        mode = .manual
        holding = true
        fingerX = x
        stopSide = 0
        let d = pos.value(now) * width - x
        grab = abs(d) > M.grabRadius
            ? Tween(from: d, to: 0, at: now, duration: reduced ? 0 : M.glideS)
            : Tween(to: d)
        press = Tween(from: press.value(now), to: 1, at: now, duration: 0.12)
    }

    /// Returns true when the divider has just been pulled past a stop.
    func touchMoved(x: Double, _ now: Double) -> Bool {
        fingerX = x
        lastTouch = now
        let p = M.rubber((x + grab.value(now)) / width)
        let side = p < M.stopMin ? -1 : p > M.stopMax ? 1 : 0
        defer { stopSide = side }
        return side != 0 && side != stopSide
    }

    func touchEnded(velocityX: Double, _ now: Double) {
        guard holding else { return }
        holding = false
        lastTouch = now
        press = Tween(from: press.value(now), to: 0, at: now, duration: 0.18)
        let p = pos.value(now)
        if reduced {
            pos = .tween(Tween(from: p, to: M.clamp(p, M.stopMin, M.stopMax), at: now, duration: 0.16))
        } else {
            let v = velocityX / width
            pos = .spring(from: p, to: M.landing(p, v), velocity: v, at: now)
        }
    }

    func choose(_ index: Int, _ now: Double) {
        selected = index
        lastTouch = now
        if mode != .manual {
            mode = .manual
            pos = .fixed(pos.value(now))
            press = Tween(from: press.value(now), to: 0, at: now, duration: 0.16)
            ghost = Tween(from: ghost.value(now), to: 0, at: now, duration: 0.16)
        }
        evening = Tween(from: evening.value(now), to: Double(index), at: now, duration: reduced ? 0.2 : M.eveningS, cubic: true)
    }

    func step(_ direction: Double, _ now: Double) {
        lastTouch = now
        mode = .manual
        ghost = Tween()
        press = Tween()
        let p = pos.value(now)
        pos = .tween(Tween(from: p, to: M.clamp(p + direction * 0.1, M.stopMin, M.stopMax), at: now, duration: 0.2))
    }
}
