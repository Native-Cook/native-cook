import SwiftUI
import UIKit

enum ShatterGlassTransitionMetrics {
    static let photoPx = 1000

    // The fracture: `rays` cracks run out from the point of impact, crossed by rings at `rings` pt
    // (jittered), so the glass breaks into small shards near the impact and large ones far from it.
    static let rays = 13
    static let rings: [Double] = [0, 20, 52, 104, 180, 285, 425, 610, 850, 1150]
    static let rayJitter = 0.55
    static let ringJitter = 0.3
    // Rings from this one outwards split their shards in two along a diagonal.
    static let splitFrom = 5

    // Timeline of a break, in seconds: the cracks race out over `crackTime`, the glass holds until
    // `fallAt`, then the shards fall; by `end` every shard has left the screen or faded.
    static let crackTime = 0.14
    static let fallAt = 0.26
    static let end = 1.5
    static let fadeTail = 0.3
    // Shards: gravity in pt/s², the outward push (strongest at the impact, falling off over
    // `pushReach` pt), and each shard's start delayed by its distance from the impact.
    static let gravity = 2600.0
    static let pushNear = 560.0
    static let pushFar = 140.0
    static let pushReach = 260.0
    static let delayPerPt = 0.00032
    // The next photo waits behind: it starts this much larger and darker and settles as it shows.
    static let revealZoom = 0.06
    static let revealDim = 0.5
    // The whole pane shakes on impact: a damped oscillation of `shake` pt.
    static let shake = 5.0

    // Mending (swiping back): the previous photo's shards rise from where they would be
    // `reverseFrom` seconds into a fall and fit back together, following the finger across
    // `mendSpan` of the width.
    static let reverseFrom = 0.78
    static let mendSpan = 0.75
    static let mendTime = 1.0
    static let mendEase = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.45, y: 0),
        endControlPoint: UnitPoint(x: 0.2, y: 1)
    )
    // The pan starts after 14 pt of sideways travel; a tap lifts within 300 ms.
    static let panSlop: CGFloat = 14
    static let tapMaxDuration = 0.3

    // autoPlay (seconds): three breaks, one mend back, two more breaks: four photos forward, so
    // the loop ends on the photo it started from.
    static let loop = 13.2
    static let cues: [ShatterGlassTransitionCue] = [
        .init(at: 0.6, mend: false, x: 0.62, y: 0.38),
        .init(at: 2.8, mend: false, x: 0.3, y: 0.56),
        .init(at: 5.0, mend: false, x: 0.55, y: 0.3),
        .init(at: 7.2, mend: true, x: 0.5, y: 0.45),
        .init(at: 8.9, mend: false, x: 0.42, y: 0.42),
        .init(at: 11.0, mend: false, x: 0.68, y: 0.6),
    ]
    static let userHold = 4.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }

    static func smooth(_ x: Double) -> Double {
        let t = clamp01(x)
        return t * t * (3 - 2 * t)
    }
}

struct ShatterGlassTransitionCue {
    var at: Double
    var mend: Bool
    var x: Double
    var y: Double
}

/// A broken pane: its cracks, and its shards with how each one flies.
struct ShatterGlassTransitionShatter {
    var x: Double
    var y: Double
    /// Each crack as points from the impact outwards, and their distances.
    var rays: [[CGPoint]] = []
    var reach: [[Double]] = []
    var polys: [Path] = []
    var cx: [Double] = []
    var cy: [Double] = []
    var vx: [Double] = []
    var vy: [Double] = []
    var delay: [Double] = []
    var spin: [Double] = []
    var flip: [Double] = []
    var grow: [Double] = []

    private typealias M = ShatterGlassTransitionMetrics

    /// A polygon cut to the rectangle 0…w × 0…h (Sutherland–Hodgman).
    private static func clip(_ points: [CGPoint], _ w: Double, _ h: Double) -> [CGPoint] {
        var out = points
        for edge in 0..<4 {
            let input = out
            out = []
            let n = input.count
            if n == 0 { break }
            for i in 0..<n {
                let a = input[i]
                let b = input[(i + 1) % n]
                // Signed distance inside the edge: left x ≥ 0, right x ≤ w, top y ≥ 0, bottom y ≤ h.
                let da = edge == 0 ? a.x : edge == 1 ? w - a.x : edge == 2 ? a.y : h - a.y
                let db = edge == 0 ? b.x : edge == 1 ? w - b.x : edge == 2 ? b.y : h - b.y
                if da >= 0 { out.append(a) }
                if (da >= 0) != (db >= 0) {
                    let t = da / (da - db)
                    out.append(CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t))
                }
            }
        }
        return out
    }

    /// Breaks a w × h pane from (px, py): its cracks and its shards, the same for the same seed,
    /// drawn from the same generator as the React Native version so every break matches it.
    init(px: Double, py: Double, w: Double, h: Double, seed: Int) {
        x = px
        y = py
        var state = (UInt64(seed) &* 2_654_435_761) % 4_294_967_296
        func random() -> Double {
            state = (state &* 1_664_525 &+ 1_013_904_223) % 4_294_967_296
            return Double(state) / 4_294_967_296
        }
        let turn = random() * .pi * 2
        var verts: [[CGPoint]] = []
        for i in 0..<M.rays {
            let angle = turn + (Double(i) + (random() - 0.5) * M.rayJitter) * .pi * 2 / Double(M.rays)
            var ring: [CGPoint] = []
            var dist: [Double] = []
            for j in 0..<M.rings.count {
                let r = j == 0 ? 0 : M.rings[j] * (1 + (random() - 0.5) * M.ringJitter)
                let a = angle + (random() - 0.5) * 0.14
                ring.append(CGPoint(x: px + cos(a) * r, y: py + sin(a) * r))
                dist.append(r)
            }
            verts.append(ring)
            reach.append(dist)
        }
        rays = verts
        func add(_ points: [CGPoint]) {
            let poly = Self.clip(points, w, h)
            let n = poly.count
            if n < 3 { return }
            var sx = 0.0
            var sy = 0.0
            for p in poly {
                sx += p.x
                sy += p.y
            }
            let cx = sx / Double(n)
            let cy = sy / Double(n)
            let dx = cx - px
            let dy = cy - py
            let d = max(1, hypot(dx, dy))
            let near = exp(-d / M.pushReach)
            let push = (M.pushFar + M.pushNear * near) * (0.7 + 0.6 * random())
            var path = Path()
            path.addLines(poly)
            path.closeSubpath()
            polys.append(path)
            self.cx.append(cx)
            self.cy.append(cy)
            vx.append(dx / d * push + (random() - 0.5) * 90)
            vy.append(dy / d * push * 0.6 - 180 * random())
            delay.append(d * M.delayPerPt + random() * 0.05)
            spin.append((random() - 0.5) * 2 * (2.5 + 5 * near))
            flip.append((random() - 0.5) * 2 * (3 + 7 * near))
            grow.append(0.08 + 0.3 * near * random())
        }
        for j in 0..<(M.rings.count - 1) {
            for i in 0..<M.rays {
                let a = verts[i]
                let b = verts[(i + 1) % M.rays]
                if j == 0 {
                    add([CGPoint(x: px, y: py), a[1], b[1]])
                } else if j >= M.splitFrom {
                    let p = [a[j], a[j + 1], b[j + 1], b[j]]
                    if random() > 0.5 {
                        add([p[0], p[1], p[2]])
                        add([p[0], p[2], p[3]])
                    } else {
                        add([p[0], p[1], p[3]])
                        add([p[1], p[2], p[3]])
                    }
                } else {
                    add([a[j], a[j + 1], b[j + 1], b[j]])
                }
            }
        }
    }
}

struct ShatterGlassTransitionSettings {
    var count: Int
    var autoPlay: Bool
    var reduced: Bool
    var size: CGSize
}

/// The pane's state, stepped once a frame like the RN frame callback, and the gestures that drive it.
@MainActor
final class ShatterGlassTransitionEngine {
    private typealias M = ShatterGlassTransitionMetrics

    private struct Timing {
        var from: Double
        var to: Double
        var start: Double
        var duration: Double
        var curve: (Double) -> Double
        var done: (ShatterGlassTransitionEngine) -> Void
    }

    /// 0 at rest, 1 breaking the front photo, 2 mending the previous one over it.
    private(set) var mode = 0
    private(set) var current = 0
    /// Seconds into a break; and 0 … 1 through a mend.
    private(set) var time = 0.0
    private(set) var mend = 0.0
    private(set) var shatter: ShatterGlassTransitionShatter?
    private var timeMotion: Timing?
    private var mendMotion: Timing?
    private var seed = 1
    private var scrubbing = false
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var lastAuto: Bool?
    private(set) var elapsed = 0.0
    private var lastDate: Date?
    private var settings = ShatterGlassTransitionSettings(count: 1, autoPlay: false, reduced: false, size: .zero)
    // The touch under way.
    private var touchStart: Date?
    private var panning = false

    /// Called (on the next turn of the main loop) with the photo that ends up in front.
    var onAnnounce: ((Int) -> Void)?
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let light = UIImpactFeedbackGenerator(style: .light)

    func step(to date: Date, settings: ShatterGlassTransitionSettings) {
        self.settings = settings
        // Every index below wraps by the count: never by zero.
        self.settings.count = max(1, settings.count)
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        guard dt > 0 else { return }
        elapsed += dt
        runLoop(dt: dt)
        if let m = timeMotion {
            let (value, finished) = evaluate(m)
            time = value
            if finished {
                timeMotion = nil
                m.done(self)
            }
        }
        if let m = mendMotion {
            let (value, finished) = evaluate(m)
            mend = value
            if finished {
                mendMotion = nil
                m.done(self)
            }
        }
    }

    /// An animation's value on the engine's clock, and whether it has landed.
    private func evaluate(_ m: Timing) -> (Double, Bool) {
        let p = m.duration > 0 ? M.clamp01((elapsed - m.start) / m.duration) : 1
        return (m.from + (m.to - m.from) * m.curve(p), p >= 1)
    }

    private func runLoop(dt: Double) {
        if scrubbing { return }
        if hold > 0 {
            hold -= dt
            if hold <= 0 { clock = 0 }
            return
        }
        if !looping { return }
        let before = clock
        let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
        clock = t
        let w = Double(settings.size.width)
        let h = Double(settings.size.height)
        for cue in M.cues {
            let passed = (before < cue.at && t >= cue.at) || (t < before && (cue.at > before || cue.at <= t))
            if !passed { continue }
            if !cue.mend {
                strike(x: cue.x * w, y: cue.y * h, user: false)
            } else if beginMend(x: cue.x * w, y: cue.y * h) {
                settleMend(to: 1, user: false, duration: M.mendTime)
            }
        }
    }

    private func announce(_ index: Int) {
        DispatchQueue.main.async { [weak self] in self?.onAnnounce?(index) }
    }

    /// Breaks the front photo from (x, y).
    private func strike(x: Double, y: Double, user: Bool) {
        let size = settings.size
        if mode != 0 || size.width == 0 { return }
        seed += 1
        shatter = ShatterGlassTransitionShatter(px: x, py: y, w: size.width, h: size.height, seed: seed)
        mode = 1
        time = 0
        let next = (current + 1) % settings.count
        announce(next)
        if user { medium.impactOccurred() }
        timeMotion = Timing(from: 0, to: M.end, start: elapsed, duration: M.end, curve: { $0 }) { engine in
            engine.current = next
            engine.mode = 0
            engine.time = 0
        }
    }

    /// Starts mending the previous photo from shards broken at (x, y); `mend` drives it.
    private func beginMend(x: Double, y: Double) -> Bool {
        let size = settings.size
        if mode != 0 || size.width == 0 { return false }
        seed += 1
        shatter = ShatterGlassTransitionShatter(px: x, py: y, w: size.width, h: size.height, seed: seed)
        mend = 0
        mode = 2
        return true
    }

    private func finishMend(user: Bool) {
        let back = (current - 1 + settings.count) % settings.count
        current = back
        mode = 0
        mend = 0
        mendMotion = nil
        announce(back)
        if user { light.impactOccurred() }
    }

    private func settleMend(to: Double, user: Bool, duration: Double) {
        mendMotion = Timing(from: mend, to: to, start: elapsed, duration: duration, curve: { M.mendEase.value(at: $0) }) { engine in
            if to == 1 {
                engine.finishMend(user: user)
            } else {
                engine.mode = 0
                engine.mend = 0
            }
        }
    }

    /// Ends a break or a mend in flight at once, so a new touch always gets a response.
    private func settleNow() {
        if mode == 1 {
            timeMotion = nil
            current = (current + 1) % settings.count
            mode = 0
            time = 0
        } else if mode == 2 && !scrubbing {
            mendMotion = nil
            if mend > 0.5 {
                finishMend(user: false)
            } else {
                mode = 0
                mend = 0
            }
        }
    }

    // MARK: Gestures

    func touchChanged(_ value: DragGesture.Value) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
            hold = M.userHold
        }
        let dx = value.translation.width
        if !panning && abs(dx) >= M.panSlop {
            panning = true
            panStart(translationX: dx, start: value.startLocation)
        }
        if panning && scrubbing {
            mend = M.clamp01(dx / (Double(settings.size.width) * M.mendSpan))
        }
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            touchStart = nil
            panning = false
        }
        if panning {
            panEnd(translationX: value.translation.width, velocityX: value.velocity.width, start: value.startLocation)
        } else if let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration {
            hold = M.userHold
            settleNow()
            strike(x: value.location.x, y: value.location.y, user: true)
        }
    }

    /// The system took the touch away (onEnded never fires for that): a mend under the finger
    /// settles back, as the RN pan's onFinalize does.
    func touchCancelled() {
        guard touchStart != nil else { return }
        touchStart = nil
        panning = false
        if scrubbing {
            scrubbing = false
            settleMend(to: 0, user: true, duration: 0.26)
        }
    }

    private func panStart(translationX: Double, start: CGPoint) {
        hold = M.userHold
        settleNow()
        // Swiping back (to the right) mends the previous photo under the finger.
        if translationX > 0 && beginMend(x: start.x, y: start.y) {
            scrubbing = true
            mendMotion = nil
        }
    }

    private func panEnd(translationX: Double, velocityX: Double, start: CGPoint) {
        let width = Double(settings.size.width)
        if scrubbing {
            scrubbing = false
            let projected = mend + velocityX / (width * M.mendSpan) * 0.2
            let to: Double = projected > 0.5 ? 1 : 0
            let left = to == 1 ? 1 - mend : mend
            settleMend(to: to, user: true, duration: max(0.18, left * 0.7))
            return
        }
        // A hard swipe forward breaks the glass where the finger first landed.
        if velocityX < -700 || translationX < -width * 0.3 {
            strike(x: start.x, y: start.y, user: true)
        }
    }
}
