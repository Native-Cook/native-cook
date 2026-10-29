import Foundation

/// Timing and touch model of Petal Glass Scenes. Every value is a pure function of the loop time
/// (autoPlay) or of the clock since a touch event, so the loop's last frame equals its first.
enum PetalGlassScenesMotion {
    static let loop = 9.6
    static let scenes = 3
    /// Each scene owns 3.2 s of the loop: it rests, then the next one grows out of the glass.
    static let sceneLength = 3.2
    static let changeAt = 1.8
    static let changeLength = 1.4
    /// Reduced motion: the scenes cross-fade.
    static let fadeLength = 0.4

    /// What one frame shows: scene `from` lies under, scene `to` grows out of the card (p 0 … 1).
    struct Frame {
        var from = 0
        var to = 0
        var p = 0.0
        var theta = 0.0 // drift phase, 2π per loop
        var fade = -1.0 // cross-fade 0 … 1 instead of the front (negative: none)
        var offset = (x: 0.0, y: 0.0) // the card's drag offset, pt
    }

    // MARK: Curves

    static func mod(_ a: Double, _ b: Double) -> Double {
        let r = a.truncatingRemainder(dividingBy: b)
        return r < 0 ? r + b : r
    }

    static func clamp(_ v: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(max(v, lo), hi) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeInCubic(_ x: Double) -> Double { x * x * x }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    /// Wraps an angle to −π … π.
    static func wrap(_ a: Double) -> Double { mod(a + Double.pi, 2 * Double.pi) - Double.pi }

    // MARK: The scene change (p = 0 … 1 over 1.4 s)

    /// Where the front is, in pt from the card's edge: from the card's centre (−124) to its rim,
    /// a short hold there, then out past the farthest corner of the screen.
    static func front(_ p: Double, reach: Double) -> Double {
        if p < 0.26 { return -124 + 124 * easeOutCubic(p / 0.26) }
        if p < 0.36 { return 0 }
        let x = seg(p, 0.36, 1)
        return reach * x * x * (3 - 2 * x)
    }

    /// Strength of the lens bulge along the front.
    static func bulge(_ p: Double) -> Double { sin(Double.pi * clamp(p)) }

    /// Amplitude of the glass's liquid warp, pt.
    static func warp(_ p: Double) -> Double { 9 + 16 * sin(Double.pi * clamp(p)) }

    /// The card gives a little as the front reaches its rim, then swells as it leaves.
    static func cardScale(_ p: Double) -> Double {
        1 - 0.03 * sin(Double.pi * seg(p, 0.06, 0.44)) + 0.012 * sin(Double.pi * seg(p, 0.44, 1))
    }

    /// The text of the scene going out: (opacity, shift along the title, blur).
    static func textOut(_ p: Double, delay: Double = 0) -> (alpha: Double, shift: Double, blur: Double) {
        let e = easeInCubic(seg(p, 0.24 + delay, 0.40 + delay))
        return (1 - e, 18 * e, 6 * e)
    }

    /// The text of the scene coming in.
    static func textIn(_ p: Double, delay: Double = 0) -> (alpha: Double, shift: Double, blur: Double) {
        let e = easeOutCubic(seg(p, 0.40 + delay, 0.72 + delay))
        return (e, -18 * (1 - e), 6 * (1 - e))
    }

    /// The loop's frame at loop time t.
    static func auto(_ t: Double) -> Frame {
        let time = mod(t, loop)
        let k = min(Int(time / sceneLength), scenes - 1)
        let u = time - Double(k) * sceneLength
        return Frame(from: k, to: (k + 1) % scenes, p: seg(u, changeAt, changeAt + changeLength),
                     theta: 2 * Double.pi * time / loop)
    }

    // MARK: The card's drag

    /// Rubber band: the card follows the finger less and less.
    static func rubber(_ x: Double) -> Double { x / (1 + abs(x) / 180) }
    static func unrubber(_ o: Double) -> Double {
        let a = min(abs(o), 170.0)
        return (o < 0 ? -1 : 1) * a / (1 - a / 180)
    }
    static func rubberSlope(_ x: Double) -> Double { 1 / pow(1 + abs(x) / 180, 2) }

    /// A released spring (response 0.5 s, damping 0.72) from x0 moving at v0.
    static func spring(_ tau: Double, x0: Double, v0: Double) -> Double {
        if tau <= 0 { return x0 }
        let z = 0.72
        let w = 2 * Double.pi / 0.5
        let wd = w * sqrt(1 - z * z)
        return exp(-z * w * tau) * (x0 * cos(wd * tau) + (v0 + z * w * x0) / wd * sin(wd * tau))
    }
}

/// What the finger did. Auto → touch makes it manual; 2 s after the last touch (and after the
/// scene change it started) the loop resumes at the shown scene's rest, its drift eased back.
struct PetalGlassScenesTouch {
    typealias M = PetalGlassScenesMotion

    var manual = false
    var loopStart = 0.0 // clock at loop time 0
    var resumedAt = -100.0 // clock at which the loop last resumed
    var thetaLag = 0.0 // drift phase difference eased out after a resume

    var scene = 0 // the scene at rest in manual mode
    var changeFrom = 0
    var changeStart = -100.0 // clock at which a scene change began
    var fading = false

    var driftBase = 0.0 // drift time at the moment manual mode began
    var manualSince = 0.0

    var touching = false
    var lastTouch = -100.0
    var drag = (x: 0.0, y: 0.0) // finger translation while touching
    var dragBase = (x: 0.0, y: 0.0)
    var releasedAt = -100.0
    var release = (x: 0.0, y: 0.0, vx: 0.0, vy: 0.0)
    var haptic = -100.0 // clock of the last haptic asked for
    var hapticTaken = -100.0

    private var changeEnd: Double { changeStart + (fading ? M.fadeLength : M.changeLength) }

    /// The state with a pending resume applied.
    func resolved(_ clock: Double, autoPlay: Bool) -> PetalGlassScenesTouch {
        guard manual, autoPlay, !touching else { return self }
        let at = max(lastTouch, changeEnd) + 2
        guard clock >= at else { return self }
        var next = self
        next.manual = false
        next.loopStart = at - Double(scene) * M.sceneLength
        next.resumedAt = at
        let manualTheta = 2 * Double.pi * (driftBase + at - manualSince) / M.loop
        let loopTheta = 2 * Double.pi * Double(scene) * M.sceneLength / M.loop
        next.thetaLag = M.wrap(manualTheta - loopTheta)
        return next
    }

    func frame(_ clock: Double, autoPlay: Bool, reduced: Bool) -> M.Frame {
        let s = resolved(clock, autoPlay: autoPlay)
        var f: M.Frame
        if reduced {
            f = M.Frame(from: s.scene, to: s.scene)
            let p = M.seg(clock, s.changeStart, s.changeStart + M.fadeLength)
            if p < 1 { f.from = s.changeFrom; f.p = p; f.fade = M.easeInOutCubic(p) }
            return f
        }
        if !s.manual && autoPlay {
            f = M.auto(clock - s.loopStart)
            let ease = M.easeInOutCubic(M.seg(clock, s.resumedAt, s.resumedAt + 1.2))
            f.theta += s.thetaLag * (1 - ease)
        } else {
            f = M.Frame(from: s.scene, to: s.scene)
            let p = M.seg(clock, s.changeStart, s.changeStart + M.changeLength)
            if p < 1 { f.from = s.changeFrom; f.p = p }
            f.theta = 2 * Double.pi * (s.driftBase + clock - s.manualSince) / M.loop
        }
        f.offset = s.offset(clock)
        return f
    }

    func offset(_ clock: Double) -> (x: Double, y: Double) {
        if touching { return (M.rubber(drag.x), M.rubber(drag.y)) }
        let tau = clock - releasedAt
        return (M.spring(tau, x0: release.x, v0: release.vx), M.spring(tau, x0: release.y, v0: release.vy))
    }

    /// The scene on screen (the one the text names once a change is past its middle).
    func shown(_ clock: Double, autoPlay: Bool, reduced: Bool) -> Int {
        let f = frame(clock, autoPlay: autoPlay, reduced: reduced)
        return f.p >= 0.5 ? f.to : f.from
    }

    mutating func begin(_ clock: Double, autoPlay: Bool, reduced: Bool) {
        let f = frame(clock, autoPlay: autoPlay, reduced: reduced)
        let o = offset(clock)
        let wasManual = resolved(clock, autoPlay: autoPlay).manual || !autoPlay || reduced
        self = resolved(clock, autoPlay: autoPlay)
        if !wasManual {
            // Take over the loop where it is, a scene change in progress included.
            manual = true
            driftBase = f.theta / (2 * Double.pi) * M.loop
            manualSince = clock
            if f.p > 0 {
                changeFrom = f.from
                scene = f.to
                changeStart = clock - f.p * M.changeLength
                fading = false
            } else {
                scene = f.from
                changeStart = -100
            }
        } else if !manual {
            manual = true
            driftBase = clock
            manualSince = clock
        }
        touching = true
        // Pick the card up where the spring left it.
        dragBase = (M.unrubber(o.x), M.unrubber(o.y))
        drag = dragBase
        lastTouch = clock
    }

    mutating func move(_ clock: Double, dx: Double, dy: Double) {
        drag = (dragBase.x + dx, dragBase.y + dy)
        lastTouch = clock
    }

    /// Ends a drag; returns true when it changed the scene.
    @discardableResult
    mutating func end(_ clock: Double, dx: Double, dy: Double, vx: Double, vy: Double, reduced: Bool) -> Bool {
        touching = false
        lastTouch = clock
        releasedAt = clock
        release = (M.rubber(drag.x), M.rubber(drag.y),
                   max(-1500, min(1500, vx * M.rubberSlope(drag.x))),
                   max(-1500, min(1500, vy * M.rubberSlope(drag.y))))
        let horizontal = abs(dx) > abs(dy)
        let busy = clock < changeStart + (fading ? M.fadeLength : M.changeLength) * 0.6
        guard horizontal, !busy, abs(dx) > 80 || abs(vx) > 650 else { return false }
        let step = (abs(dx) > 80 ? dx : vx) < 0 ? 1 : M.scenes - 1
        changeFrom = scene
        scene = (scene + step) % M.scenes
        changeStart = clock
        fading = reduced
        haptic = clock
        return true
    }

    /// True once per haptic asked for.
    mutating func takeHaptic() -> Bool {
        guard haptic > hapticTaken else { return false }
        hapticTaken = haptic
        return true
    }

    var idleUntil: Double { max(lastTouch, changeEnd) + 2.2 }
}
