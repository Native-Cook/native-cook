import Foundation

/// Timing and touch model of Pickle Flavour Wipe. Every value is a pure function of the loop time
/// (autoPlay) or of the clock since a touch event, so the loop's last frame equals its first.
enum PickleFlavourWipeMotion {
    static let loop = 10.0
    static let pages = 4

    /// Loop time at which each wipe starts: Mango → Lime, Lime → Garlic, Garlic → Trio, Trio → Mango.
    static let wipeAt = [1.0, 3.25, 5.5, 7.75]
    /// How long the edge takes to cross the screen.
    static let sweep = 0.8
    /// Loop time at which each page is on screen and settled (where the loop resumes after a touch).
    static let settledAt = [9.6, 3.1, 5.35, 7.6]
    /// Entrance age of a page that has fully arrived.
    static let settledAge = 4.0

    /// What the art needs for one frame: page `from` lies under, page `to` is revealed by the edge.
    struct Scene {
        var from = 0
        var fromAge = settledAge // entrance age of `from` (seconds since its own wipe began)
        var to = 0
        var toAge = settledAge
        var reveal = 0.0 // 0 … 1: how far the edge has crossed
        var dir = 1.0 // +1: the edge moves right → left (next page); −1: left → right (previous)
        var fade = -1.0 // reduced motion: cross-fade 0 → 1 instead of the edge (negative: none)

        static func still(_ page: Int, age: Double = settledAge) -> Scene {
            Scene(from: page, fromAge: age, to: page, toAge: age, reveal: 0)
        }
    }

    // MARK: Curves

    static func mod(_ a: Double, _ b: Double) -> Double {
        let r = a.truncatingRemainder(dividingBy: b)
        return r < 0 ? r + b : r
    }

    static func clamp(_ v: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(max(v, lo), hi) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    /// A released spring's remaining share of its start value (zero start velocity).
    static func springDecay(_ tau: Double, damping z: Double, response: Double) -> Double {
        if tau <= 0 { return 1 }
        let w = 2 * Double.pi / response
        let wd = w * sqrt(1 - z * z)
        return exp(-z * w * tau) * (cos(wd * tau) + z * w / wd * sin(wd * tau))
    }

    /// A spring stepping from 0 to 1.
    static func springStep(_ tau: Double, damping z: Double, response: Double) -> Double {
        tau <= 0 ? 0 : 1 - springDecay(tau, damping: z, response: response)
    }

    static func hash(_ x: Double) -> Double {
        let s = sin(x * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /// A sine that repeats a whole number of times per loop.
    static func wave(_ t: Double, _ k: Double, _ phase: Double) -> Double {
        sin(2 * Double.pi * k * t / loop + phase)
    }

    // MARK: Entrance of a page (age = seconds since its wipe began)

    /// The jar: rises 300 pt from below, turning from +14°, on a spring.
    static func jarIn(_ age: Double) -> Double { springStep(age - 0.22, damping: 0.62, response: 0.6) }
    /// Ingredient `i` pops out from behind the jar.
    static func ingredientIn(_ age: Double, _ i: Int) -> Double {
        springStep(age - 0.45 - 0.07 * Double(i), damping: 0.55, response: 0.5)
    }
    /// The title slides in from the left.
    static func titleIn(_ age: Double) -> Double { springStep(age - 0.5, damping: 0.75, response: 0.45) }
    /// The title's speed (share of its travel per second), for the smear.
    static func titleSpeed(_ age: Double) -> Double { (titleIn(age) - titleIn(age - 1.0 / 60)) * 60 }
    /// Subtitle and description fade up.
    static func copyIn(_ age: Double) -> Double { easeOutCubic(seg(age, 0.85, 1.2)) }
    /// Trio page: jar `j` of the line-up rises, staggered.
    static func trioJarIn(_ age: Double, _ j: Int) -> Double {
        springStep(age - 0.22 - 0.09 * Double(j), damping: 0.62, response: 0.6)
    }

    // MARK: The loop (P = 10 s)

    /// Rest on Mango 0–1 s → the edge wipes Lime in (1.0–1.8 s), the jar, ingredients and title
    /// arrive → hold → Garlic (3.25 s) → hold → the three jars line up on the Trio page (5.5 s) →
    /// hold → Mango wipes back in (7.75 s) and settles by 9.5 s → rest.
    static func loopScene(_ t: Double) -> Scene {
        var k = wipeAt.count - 1
        while k >= 0, t < wipeAt[k] { k -= 1 }
        // Before the first wipe: the last wipe (→ Mango) of the previous loop.
        let start = k >= 0 ? wipeAt[k] : wipeAt[wipeAt.count - 1] - loop
        let wipe = k >= 0 ? k : wipeAt.count - 1
        let age = t - start
        let from = wipe, to = (wipe + 1) % pages
        if age < sweep {
            return Scene(from: from, fromAge: settledAge, to: to, toAge: age,
                         reveal: easeInOutCubic(age / sweep), dir: 1)
        }
        return .still(to, age: age)
    }
}

/// The finger: swipe left for the next flavour, right for the previous one; the edge follows the
/// finger, release completes the wipe or springs it back. Two seconds after the last touch the loop
/// resumes from the page on screen.
final class PickleFlavourWipeTouch {
    typealias M = PickleFlavourWipeMotion

    static let idle = 2.0
    static let dragSpan = 300.0 // pt of horizontal drag for a full wipe

    private(set) var touching = false
    private(set) var page = 0 // the page that is (or is becoming) the settled one
    private var pageAgeBase = -100.0 // clock at which `page`'s entrance began
    private var base = 0.0 // clock at loop time 0
    private var lastClock = -100.0
    private var startS = 0.0
    private var s = 0.0 // signed wipe while dragging: + towards the next page, − towards the previous
    // The last release.
    private var releaseClock = -100.0
    private var releaseFrom = 0
    private var releaseTo = 0
    private var releaseReveal = 0.0
    private var releaseDir = 1.0
    private var releaseCommit = false
    private var releaseAge = 0.0
    private(set) var hapticDue: [Double] = []

    var restsAfter: Double { lastClock + Self.idle }
    var everTouched: Bool { lastClock > -50 }

    func loopTime(_ clock: Double, autoPlay: Bool) -> Double? {
        if touching || clock < lastClock + Self.idle { return nil }
        if !autoPlay { return M.settledAt[page] }
        if everTouched { base = lastClock + Self.idle - M.settledAt[page] }
        return M.mod(clock - base, M.loop)
    }

    func scene(_ clock: Double, autoPlay: Bool) -> M.Scene {
        if let t = loopTime(clock, autoPlay: autoPlay) { return M.loopScene(t) }
        if touching {
            let reveal = abs(s)
            if reveal < 1e-4 { return .still(page, age: clock - pageAgeBase) }
            let dir: Double = s > 0 ? 1 : -1
            let to = (page + (s > 0 ? 1 : M.pages - 1)) % M.pages
            return M.Scene(from: page, fromAge: clock - pageAgeBase, to: to, toAge: 0.9 * min(reveal, 1),
                           reveal: reveal, dir: dir)
        }
        let tau = clock - releaseClock
        if releaseCommit {
            let reveal = 1 + (releaseReveal - 1) * M.springDecay(tau, damping: 0.86, response: 0.42)
            if tau > 1.2 { return .still(releaseTo, age: releaseAge + tau) }
            return M.Scene(from: releaseFrom, fromAge: M.settledAge, to: releaseTo, toAge: releaseAge + tau,
                           reveal: reveal, dir: releaseDir)
        }
        let reveal = releaseReveal * M.springDecay(tau, damping: 0.86, response: 0.38)
        if tau > 1.2 || abs(reveal) < 1e-4 && tau > 0.3 { return .still(releaseFrom, age: clock - pageAgeBase) }
        return M.Scene(from: releaseFrom, fromAge: clock - pageAgeBase, to: releaseTo, toAge: 0.9 * max(0, reveal),
                       reveal: reveal, dir: releaseDir)
    }

    func begin(_ clock: Double, autoPlay: Bool) {
        // Carry the scene as it is on screen into the drag.
        let now = scene(clock, autoPlay: autoPlay)
        if now.from == now.to || now.reveal > 0.5 {
            page = now.to
            pageAgeBase = clock - now.toAge
            startS = now.from == now.to ? 0 : -now.dir * (1 - now.reveal)
        } else {
            page = now.from
            pageAgeBase = clock - now.fromAge
            startS = now.dir * now.reveal
        }
        s = startS
        touching = true
        lastClock = clock
    }

    /// `dx` is the finger's horizontal travel in pt (negative = left).
    func drag(_ clock: Double, dx: Double) {
        guard touching else { return }
        let raw = startS - dx / Self.dragSpan
        let mag = abs(raw)
        s = mag > 1 ? (raw > 0 ? 1 : -1) * (1 + (mag - 1) / (1 + (mag - 1) / 0.12)) : raw
        lastClock = clock
    }

    /// `vx` is the finger's horizontal speed in pt/s.
    func end(_ clock: Double, vx: Double) {
        guard touching else { return }
        touching = false
        let speed = -vx / Self.dragSpan // in wipes per second, + towards the next page
        let dir: Double = s > 0 || (s == 0 && speed > 0) ? 1 : -1
        let reveal = abs(s)
        let fling = speed * dir
        releaseClock = clock
        releaseFrom = page
        releaseTo = (page + (dir > 0 ? 1 : M.pages - 1)) % M.pages
        releaseDir = dir
        releaseReveal = reveal
        releaseCommit = reveal > 0.35 || (fling > 2 && reveal > 0.04)
        releaseAge = 0.9 * min(reveal, 1)
        if releaseCommit {
            page = releaseTo
            pageAgeBase = clock - releaseAge
            hapticDue.append(clock)
        }
        lastClock = clock
    }

    /// Reduced motion: a swipe changes the page with a cross-fade.
    private var fadeClock = -100.0
    private var fadeFrom = 0

    func fadeScene(_ clock: Double) -> M.Scene {
        let x = M.seg(clock - fadeClock, 0, 0.3)
        if x >= 1 { return .still(page) }
        var scene = M.Scene.still(fadeFrom)
        scene.to = page
        scene.fade = x
        return scene
    }

    func fadeSwipe(_ clock: Double, dx: Double) {
        guard abs(dx) > 50 else { return }
        fadeFrom = page
        page = (page + (dx < 0 ? 1 : M.pages - 1)) % M.pages
        fadeClock = clock
        lastClock = clock
        hapticDue.append(clock)
    }

    func fading(_ clock: Double) -> Bool { clock - fadeClock < 0.35 }

    /// True once per due haptic.
    func takeHaptic(_ clock: Double) -> Bool {
        guard hapticDue.contains(where: { $0 <= clock }) else { return false }
        hapticDue.removeAll { $0 <= clock }
        return true
    }
}
