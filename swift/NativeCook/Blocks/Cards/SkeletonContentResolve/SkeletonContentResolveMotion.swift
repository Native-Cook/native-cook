import Foundation
import UIKit

/// The skeleton's timing, the pull and the 10 s film, as pure functions of two clocks: `sinceHide`,
/// the seconds since the content last turned into its skeleton, and `sinceShow`, the seconds since
/// the new content started to resolve (negative while loading). k = 0 is a bar, k = 1 the content.
enum SkeletonContentResolveMotion {
    static let loop: Double = 10

    static let stagger = 0.06
    static let resolveS = 0.62
    static let hideStagger = 0.02
    static let hideS = 0.3
    static let maxOrder = 11.0
    static let resolveTotal = maxOrder * stagger + resolveS
    static let hideTotal = maxOrder * hideStagger + hideS
    static let settled = resolveTotal + 0.3

    static let morphTo = 0.62
    static let textFrom = 0.3
    static let textTo = 0.85
    static let dissolveFrom = 0.5
    static let bloom = 0.22
    static let wipeFrom = 0.05
    static let wipeTo = 0.92
    static let wipeSoft = 0.35

    static let shimmerS = 1.15
    static let band = 150.0
    static let slant = 0.32

    static let threshold = 62.0
    static let hold = 46.0
    static let pullGain = 0.55
    static let pullSoft = 480.0
    static let loadS = 1.6
    static let spinS = 0.9
    static let ticks = 8
    static let spinFade = 0.3
    static let returnLoadS = 0.9
    static let idleS = 2.0

    static let offsetResponse = 0.42
    static let offsetDamping = 0.8
    private static let omega = 2 * Double.pi / offsetResponse
    private static let omegaD = omega * (1 - offsetDamping * offsetDamping).squareRoot()
    private static let decay = offsetDamping * omega

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func smooth(_ x: Double) -> Double { let c = clamp01(x); return c * c * (3 - 2 * c) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }
    static func easeInOutCubic(_ x: Double) -> Double {
        let c = clamp01(x)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }
    static func easeInOutQuad(_ x: Double) -> Double {
        let c = clamp01(x)
        return c < 0.5 ? 2 * c * c : 1 - pow(-2 * c + 2, 2) / 2
    }

    private static let settleA = 5.2
    private static let settleB = 7.5
    private static let settleEnd = 1 - exp(-settleA) * (cos(settleB) + (settleA / settleB) * sin(settleB))

    static func settle(_ u: Double) -> Double {
        let c = clamp01(u)
        let s = 1 - exp(-settleA * c) * (cos(settleB * c) + (settleA / settleB) * sin(settleB * c))
        return s + (1 - settleEnd) * c
    }

    static func kAt(_ order: Double, _ sinceHide: Double, _ sinceShow: Double) -> Double {
        if sinceShow >= 0 { return seg(sinceShow, order * stagger, order * stagger + resolveS) }
        if sinceHide >= 0 { return 1 - seg(sinceHide, order * hideStagger, order * hideStagger + hideS) }
        return 1
    }

    static func barMorph(_ k: Double) -> Double { settle(k / morphTo) }
    static func contentAlpha(_ k: Double) -> Double { smooth(seg(k, textFrom, textTo)) }
    static func barAlpha(_ k: Double) -> Double { 1 - smooth(seg(k, dissolveFrom, 1)) }
    static func barBloom(_ k: Double) -> Double { 1 + bloom * smooth(seg(k, dissolveFrom, 1)) }

    static func shimmerAlpha(_ k: Double, _ sinceHide: Double) -> Double {
        if sinceHide < 0 { return 0 }
        return smooth(seg(sinceHide, 0.12, 0.42)) * (1 - seg(k, 0, 0.25))
    }

    static func wipe(_ k: Double) -> Double { easeInOutCubic(seg(k, wipeFrom, wipeTo)) }

    static func shimmerX(_ sinceHide: Double, _ span: Double) -> Double {
        if sinceHide < 0 { return -band }
        let lap = sinceHide / shimmerS
        return -band + (lap - floor(lap)) * (span + 2 * band)
    }

    static func pullFor(_ dy: Double) -> Double {
        dy <= 0 ? 0 : (dy * pullGain) / (1 + dy / pullSoft)
    }

    /// The content's spring `s` seconds after leaving `from` with `velocity`, heading for `to`.
    static func offsetSpring(_ from: Double, _ to: Double, _ s: Double, velocity: Double = 0) -> Double {
        if s <= 0 { return from }
        let a = from - to
        let b = (velocity + decay * a) / omegaD
        return to + exp(-decay * s) * (a * cos(omegaD * s) + b * sin(omegaD * s))
    }

    static func tickArming(_ index: Int, _ reveal: Double) -> Double {
        0.85 * smooth(seg(reveal * Double(ticks), Double(index), Double(index + 1)))
    }

    static func tickSpinning(_ index: Int, _ sinceHide: Double) -> Double {
        let n = Double(ticks)
        let head = (max(0, sinceHide) / spinS) * n
        let r = (head - Double(index)).truncatingRemainder(dividingBy: n)
        let behind = (r + n).truncatingRemainder(dividingBy: n)
        return 1 - 0.78 * (behind / n)
    }

    // MARK: The film

    static let pullS = 0.9
    static let fingerDy = 170.0
    static let release1 = 1.7
    static let resolve1 = 3.3
    static let release2 = 6.4
    static let resolve2 = 7.75
    static let swapAfter = hideTotal + 0.04
    static let filmPull = pullFor(fingerDy)

    static func filmRelease(_ t: Double) -> Double { t >= release2 ? release2 : t >= release1 ? release1 : -1 }
    static func filmResolve(_ t: Double) -> Double { t >= release2 ? resolve2 : resolve1 }
    static func filmTarget(_ t: Double) -> Int { t >= release2 ? 0 : t >= release1 ? 1 : 0 }

    static func filmSinceHide(_ t: Double) -> Double {
        let r = filmRelease(t)
        return r < 0 ? -1 : t - r
    }

    static func filmSinceShow(_ t: Double) -> Double {
        if filmRelease(t) < 0 { return settled }
        let s = filmResolve(t)
        return t >= s ? min(settled, t - s) : -1
    }

    static func filmData(_ t: Double) -> Int {
        let r = filmRelease(t)
        if r < 0 { return 0 }
        let target = filmTarget(t)
        return t - r >= swapAfter ? target : 1 - target
    }

    static func filmFinger(_ t: Double) -> Double {
        let a = t < release1 + 0.5 ? release1 - pullS : release2 - pullS
        return fingerDy * easeOutCubic(seg(t, a, a + pullS))
    }

    static func filmGhost(_ t: Double) -> Double {
        let a1 = release1 - pullS
        let a2 = release2 - pullS
        let first = smooth(seg(t, a1 - 0.22, a1)) * (1 - smooth(seg(t, release1, release1 + 0.22)))
        let second = smooth(seg(t, a2 - 0.22, a2)) * (1 - smooth(seg(t, release2, release2 + 0.22)))
        return max(first, second)
    }

    private static func cycleOffset(_ t: Double, _ release: Double, _ resolve: Double) -> Double {
        if t < release - pullS { return 0 }
        if t < release { return pullFor(fingerDy * easeOutCubic(seg(t, release - pullS, release))) }
        if t < resolve { return offsetSpring(filmPull, hold, t - release) }
        return offsetSpring(hold, 0, t - resolve) * (1 - smooth(seg(t, resolve + 0.7, resolve + 0.95)))
    }

    static func filmOffset(_ t: Double) -> Double {
        t < release2 - pullS ? cycleOffset(t, release1, resolve1) : cycleOffset(t, release2, resolve2)
    }

    static func filmReveal(_ t: Double) -> Double {
        let pulling = (t >= release1 - pullS && t < release1) || (t >= release2 - pullS && t < release2)
        return pulling ? clamp01(filmOffset(t) / threshold) : 0
    }
}

/// The screen's state, advanced from the timeline's date. A plain class on purpose: the drawing reads
/// it inside `TimelineView`, so nothing writes SwiftUI state per frame.
final class SkeletonContentResolveClock {
    private typealias M = SkeletonContentResolveMotion

    struct Frame: Equatable {
        var sinceHide = -1.0
        var sinceShow = SkeletonContentResolveMotion.settled
        var offset = 0.0
        var reveal = 0.0
        var ghost = 0.0
        var fingerDy = 0.0
        var data = 0
    }

    private enum Mode { case auto, manual, returning }

    private struct Spring {
        var from = 0.0, to = 0.0, at = 0.0
        func value(_ now: Double) -> Double { SkeletonContentResolveMotion.offsetSpring(from, to, now - at) }
    }

    private struct Tween {
        var from = 0.0, to = 0.0, at = 0.0, duration = 0.0
        func value(_ now: Double) -> Double {
            duration <= 0 ? to : from + (to - from) * SkeletonContentResolveMotion.easeInOutQuad((now - at) / duration)
        }
    }

    var film = true
    private(set) var holding = false
    private(set) var lastTouch = 0.0
    private var mode: Mode = .auto
    private var loopStart = -1.0
    private var tau = 0.0
    private var hideAt = -1.0
    private var showAt = -1.0
    private var target = 0
    private var landed = true
    private var sinceHide = -1.0
    private var sinceShow = SkeletonContentResolveMotion.settled
    private var data = 0
    private var fingerDy = 0.0
    private var offset = Spring()
    private var reveal = Tween()
    private var ghost = Tween()

    func frame(_ now: Double) -> Frame {
        if mode == .auto && !film { mode = .manual }
        if mode == .auto {
            if loopStart < 0 { loopStart = now }
            let t = max(0, now - loopStart).truncatingRemainder(dividingBy: M.loop)
            tau = t
            sinceHide = M.filmSinceHide(t)
            sinceShow = M.filmSinceShow(t)
            let o = M.filmOffset(t)
            offset = Spring(from: o, to: o, at: now)
            reveal = Tween(to: M.filmReveal(t))
            ghost = Tween(to: M.filmGhost(t))
            fingerDy = M.filmFinger(t)
            data = M.filmData(t)
        } else {
            advanceManual(now)
        }
        return Frame(sinceHide: sinceHide, sinceShow: sinceShow, offset: offset.value(now),
                     reveal: reveal.value(now), ghost: ghost.value(now), fingerDy: fingerDy, data: data)
    }

    private func advanceManual(_ now: Double) {
        let ss = now >= showAt ? min(M.settled, now - showAt) : -1
        if ss < 0 && hideAt >= 0 { sinceHide = now - hideAt }
        sinceShow = ss
        if hideAt >= 0 && now - hideAt >= M.swapAfter { data = target }
        if ss >= 0 && !landed {
            landed = true
            springOffset(to: 0, now)
            DispatchQueue.main.async { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        }
        guard film, !holding else { return }
        let idle = ss >= M.settled
        if mode == .manual && idle && now - lastTouch > M.idleS {
            mode = .returning
            springOffset(to: 0, now)
            reveal = Tween(from: reveal.value(now), to: 0, at: now, duration: 0.16)
            if target != 0 { begin(now, next: 0, load: M.returnLoadS) }
        } else if mode == .returning && idle && target == 0 && data == 0 {
            loopStart = now
            tau = 0
            mode = .auto
        }
    }

    private func springOffset(to: Double, _ now: Double) {
        offset = Spring(from: offset.value(now), to: to, at: now)
    }

    private func begin(_ now: Double, next: Int, load: Double) {
        hideAt = now
        showAt = now + load
        target = next
        landed = false
        sinceHide = 0
        sinceShow = -1
    }

    /// A finger anywhere: the film stops, and whatever it was loading goes on loading.
    func take(_ now: Double) {
        lastTouch = now
        if mode == .manual { return }
        if mode == .auto {
            let t = tau
            let r = M.filmRelease(t)
            if r < 0 {
                hideAt = -1
                showAt = -1
                target = 0
                landed = true
                springOffset(to: 0, now)
            } else {
                let s = M.filmResolve(t)
                hideAt = now - (t - r)
                showAt = now + (s - t)
                target = M.filmTarget(t)
                landed = t >= s
                springOffset(to: t < s ? M.hold : 0, now)
            }
            reveal = Tween(from: reveal.value(now), to: 0, at: now, duration: 0.16)
            ghost = Tween(from: ghost.value(now), to: 0, at: now, duration: 0.16)
        }
        mode = .manual
    }

    func startPull() { holding = true }

    /// Returns true on the frame the pull arms the refresh.
    func pull(dy: Double, _ now: Double) -> Bool {
        lastTouch = now
        guard sinceShow >= M.resolveTotal else { return false }
        let wasArmed = reveal.value(now) >= 1
        let o = M.pullFor(dy)
        offset = Spring(from: o, to: o, at: now)
        let r = M.clamp01(o / M.threshold)
        reveal = Tween(to: r)
        return r >= 1 && !wasArmed
    }

    func endPull(_ now: Double) {
        guard holding else { return }
        holding = false
        lastTouch = now
        guard sinceShow >= M.resolveTotal else { return }
        if offset.value(now) >= M.threshold {
            begin(now, next: 1 - target, load: M.loadS)
            springOffset(to: M.hold, now)
        } else {
            springOffset(to: 0, now)
            reveal = Tween(from: reveal.value(now), to: 0, at: now, duration: 0.18)
        }
    }

    /// The accessibility action.
    func refresh(_ now: Double) {
        take(now)
        guard sinceShow >= M.resolveTotal else { return }
        begin(now, next: 1 - target, load: M.loadS)
        springOffset(to: M.hold, now)
    }
}
