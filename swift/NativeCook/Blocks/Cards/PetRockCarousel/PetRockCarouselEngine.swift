import SwiftUI
import UIKit

struct PetRockCarouselRock {
    let name: String
    /// Unsplash photo id: the stone's surface, laid over its base colour.
    let photo: String
    /// Base colour of the stone.
    let tone: UInt32
    /// Shape seed, vertical squash and resting tilt (rad).
    let seed: Int
    let aspect: Double
    let tilt: Double
    /// The face it wears when the carousel opens.
    let face: Int

    static let samples: [PetRockCarouselRock] = [
        .init(name: "Gus", photo: "photo-1604951832436-90867d74f4de", tone: 0x8b8680, seed: 7, aspect: 0.9, tilt: -0.1, face: 0),
        .init(name: "Marlo", photo: "photo-1650755743867-6ad2037a2934", tone: 0x998a78, seed: 21, aspect: 0.95, tilt: 0.06, face: 1),
        .init(name: "Dot", photo: "photo-1597188702867-0a052cddba09", tone: 0x948a80, seed: 33, aspect: 0.92, tilt: 0.04, face: 2),
        .init(name: "Sven", photo: "photo-1665706782455-f8e800c8aeaf", tone: 0x86898b, seed: 48, aspect: 0.82, tilt: -0.05, face: 3),
        .init(name: "Ruby", photo: "photo-1640832956551-eaeab27afbdc", tone: 0x9e8e7e, seed: 59, aspect: 0.96, tilt: 0.08, face: 4),
        .init(name: "Otis", photo: "photo-1725959523676-556f1fadaf2f", tone: 0x8e8a84, seed: 71, aspect: 0.88, tilt: -0.04, face: 5),
    ]
}

enum PetRockCarouselMetrics {
    // Sizes (pt). The focused rock is `bigMax` across at most, or this share of the screen's width;
    // the rocks next to it are `small` of that, and each one further away a little smaller again.
    static let bigMax = 250.0
    static let bigShare = 0.62
    static let small = 0.46
    static let gap = 22.0
    static let gapFar = 16.0
    // The carousel's centre line, as a share of the screen's height.
    static let centreAt = 0.5
    // The intro: the little rock that stands for the gap between the two words, and the title.
    static let titleRock = 50.0
    static let titleSize: CGFloat = 38
    static let titleAt = 0.46
    // Each letter of the title leaves this far (pt), the one nearest the rock first.
    static let split = 110.0
    static let splitLag = 0.07
    // A rock's photo is cropped to this much of its middle, so the stone's surface fills it.
    static let photoZoom = 1.7
    static let photoWidth = 520
    // How strongly the grain texture is laid over the stone and its paint, in overlay.
    static let grainOpacity = 1.0

    // Dragging: finger travel per rock is the distance between the focused rock and the next.
    static let dragSlop = 8.0
    static let tapMaxDuration = 0.4
    // Past the ends the carousel gives at most this much of a rock, in a rubber band.
    static let rubber = 0.55
    // A release throws the carousel this many seconds ahead before it picks a rock.
    static let throwTime = 0.16
    // The snap onto a rock: a spring with a little give.
    static let snapStiffness = 170.0
    static let snapDamping = 21.0
    // A rock has arrived when it is this close to the front (in rocks) and slower than this (rocks/s).
    static let arrivedAt = 0.02
    static let arrivedSpeed = 0.25
    // Intro ⇄ carousel: a spring that lets the rock grow a touch past its size and settle.
    static let introStiffness = 120.0
    static let introDamping = 17.0
    static let fadeTime = 0.28

    // The face morph: after a rock comes to rest it waits `morphDelay`, then the old paint blurs out
    // while the new one sharpens in over `morphTime`, and the rock swells by `morphPop`.
    static let morphDelay = 0.22
    static let morphTime = 0.72
    static let morphBlur = 9.0
    static let morphPop = 0.035
    static let morphEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1))

    // A tap squashes the rock (wider, shorter, standing on its base), then it springs back past
    // round and wobbles about its base.
    static let squashX = 0.12
    static let squashY = 0.15
    static let wobble = 0.14

    // Googly pupils: a loose ball in each eye, hanging a little low, thrown about by the rock's motion.
    static let pupilRest = 0.35
    static let pupilStiffness = 70.0
    static let pupilDamping = 4.5
    static let pupilBounce = 1.6
    // How hard the rock's acceleration (pt/s²), its wobble and its squash shake the pupils.
    static let pupilFromScroll = 0.0024
    static let pupilFromWobble = 0.035
    static let pupilFromSquash = 0.02

    // autoPlay loop (seconds): Enter, three rocks down with a face morph on each, a tap on the
    // third, back up to the first, and back to the title.
    static let loop = 14.0
    static let pressAt = 0.95
    static let enterAt = 1.1
    static let steps: [(at: Double, to: Int)] = [(3.0, 1), (5.0, 2), (8.0, 3), (10.0, 0)]
    static let wiggleAt = 6.9
    static let exitAt = 12.0
    // A touch pauses the loop this long; then it goes back to the title and starts over.
    static let userHold = 6.0

    // The caption's line fades in and out as it changes (s).
    static let captionIn = 0.24
    static let captionOut = 0.14

    static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, x)) }

    /// JavaScript's Math.round: halves go up.
    static func round(_ x: Double) -> Double { floor(x + 0.5) }

    /// Reanimated's default timing curve, ease in and out (quadratic).
    static func inOutQuad(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2 }
    static func outQuad(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) }

    /// Resistance past an end: grows with the overshoot but never past `rubber`.
    static func band(_ x: Double) -> Double { rubber * (1 - 1 / (1 + (x * 1.8) / rubber)) }

    static func rubberBand(_ raw: Double, last: Double) -> Double {
        if raw < 0 { return -band(-raw) }
        if raw > last { return last + band(raw - last) }
        return raw
    }
}

/// Where everything sits on this screen.
struct PetRockCarouselLayout: Equatable {
    var width: Double
    var height: Double
    var big: Double
    var centreY: Double
    /// Distance from the focused rock's centre to its neighbour's, and between further rocks.
    var near: Double
    var far: Double
    var titleX: Double
    var titleY: Double

    init(width: Double, height: Double, titleX: Double) {
        typealias M = PetRockCarouselMetrics
        self.width = width
        self.height = height
        big = min(M.bigMax, width * M.bigShare)
        let small = big * M.small
        centreY = height * M.centreAt
        near = big / 2 + M.gap + small / 2
        far = small + M.gapFar
        self.titleX = titleX
        titleY = height * M.titleAt
    }

    /// Where a rock sits for a signed distance `p` (in rocks) from the front, and how big.
    func slot(_ p: Double) -> (y: Double, scale: Double) {
        // Int(c) below traps on NaN; a non-finite scroll puts the rock in front.
        guard p.isFinite else { return (centreY, 1) }
        let a = abs(p)
        // Offsets at 0, 1, 2, 3, 4 rocks, extended past the last in a straight line.
        let offset = a <= 1 ? a * near : near + (a - 1) * far
        let scales = [1, PetRockCarouselMetrics.small, PetRockCarouselMetrics.small * 0.93, PetRockCarouselMetrics.small * 0.86]
        let c = min(a, 3)
        let k = min(Int(c), 2)
        let scale = scales[k] + (scales[k + 1] - scales[k]) * (c - Double(k))
        return (centreY + (p > 0 ? 1 : p < 0 ? -1 : 0) * offset, scale)
    }
}

/// A number driven like a Reanimated shared value: a spring, a timing (with a delay), or a
/// sequence of them, stepped once a frame.
struct PetRockCarouselAnim {
    enum Step {
        case spring(to: Double, stiffness: Double, damping: Double)
        case timing(to: Double, duration: Double, curve: (Double) -> Double, delay: Double)
    }

    private(set) var value: Double
    private(set) var velocity = 0.0
    private var steps: [Step] = []
    private var from = 0.0
    private var t = 0.0

    init(_ value: Double) { self.value = value }

    var running: Bool { !steps.isEmpty }

    mutating func jump(to value: Double) {
        self.value = value
        velocity = 0
        steps = []
    }

    /// Starts a new animation from where the value is; a spring keeps the speed it has unless given one.
    mutating func run(_ steps: [Step], velocity: Double? = nil) {
        if let velocity { self.velocity = velocity }
        self.steps = steps
        begin()
    }

    private mutating func begin() {
        from = value
        t = 0
        // A timing has no speed of its own: a spring after it starts from rest.
        if case .timing = steps.first { velocity = 0 }
    }

    /// Advances by `dt` s; true on the frame the whole sequence finishes.
    mutating func step(_ dt: Double) -> Bool {
        guard let step = steps.first else { return false }
        var done = false
        switch step {
        case let .spring(to, stiffness, damping):
            var left = dt
            while left > 0 {
                let h = min(left, 1.0 / 240)
                velocity += (-stiffness * (value - to) - damping * velocity) * h
                value += velocity * h
                left -= h
            }
            if abs(velocity) < 0.002 && abs(value - to) < 0.0005 {
                value = to
                velocity = 0
                done = true
            }
        case let .timing(to, duration, curve, delay):
            t += dt
            let p = min(1, max(0, (t - delay) / duration))
            value = from + (to - from) * curve(p)
            if p >= 1 {
                velocity = 0
                done = true
            }
        }
        guard done else { return false }
        steps.removeFirst()
        if steps.isEmpty { return true }
        begin()
        return false
    }
}

/// A rock's face lives in two slots: when it changes, the hidden slot takes the new one and the
/// two crossfade through a blur, so neither is ever seen changing.
struct PetRockCarouselMorph {
    var a: Int
    var b: Int
    var showB = false
    var morphing = false
    // 0: slot A shows, 1: slot B shows.
    var mix = PetRockCarouselAnim(0)

    init(face: Int) {
        a = face
        b = face
    }

    mutating func show(_ face: Int) {
        let shown = showB ? b : a
        guard shown != face else { return }
        if showB {
            a = face
            showB = false
        } else {
            b = face
            showB = true
        }
        morphing = true
        typealias M = PetRockCarouselMetrics
        mix.run([.timing(to: showB ? 1 : 0, duration: M.morphTime, curve: { M.morphEase.value(at: $0) }, delay: M.morphDelay)])
    }

    mutating func step(_ dt: Double) {
        if mix.step(dt) { morphing = false }
    }
}

struct PetRockCarouselSettings {
    var layout: PetRockCarouselLayout
    var autoPlay: Bool
    var reducedMotion: Bool
    var onFocus: ((String) -> Void)?
}

/// The carousel's state, stepped once a frame like the RN frame callback, and the gestures that
/// drive it.
@MainActor
final class PetRockCarouselEngine {
    private typealias M = PetRockCarouselMetrics

    let rocks = PetRockCarouselRock.samples
    let art = PetRockCarouselArt(count: PetRockCarouselRock.samples.count)
    private var last: Double { Double(rocks.count - 1) }

    private(set) var scroll = PetRockCarouselAnim(0)
    private(set) var intro = PetRockCarouselAnim(0)
    private(set) var squash = PetRockCarouselAnim(0)
    private(set) var wobble = PetRockCarouselAnim(0)
    private(set) var press = PetRockCarouselAnim(1)
    private(set) var morphs: [PetRockCarouselMorph]
    private(set) var inCarousel = false
    private(set) var faces: [Int]
    private(set) var focus = 0
    // Pupil position in its eye (−1…1 each way) and its velocity.
    private(set) var px = 0.0
    private(set) var py = PetRockCarouselMetrics.pupilRest
    private var pvx = 0.0
    private var pvy = 0.0
    private var jolts = 0
    private var joltAt: Double?
    private var softAt: Double?
    // What the pupils last saw: the carousel's position and speed, the wobble and the squash.
    private var memory = (s: 0.0, v: 0.0, w: 0.0, wv: 0.0, q: 0.0, qv: 0.0)
    private var entered = false
    private var dragging = false
    private var dragFrom = 0.0
    // The rock the carousel is heading for (nil once it has arrived), and whether a finger sent it.
    private var arriving: Int?
    private var arrivingByUser = false
    private var byUser = false
    // The rock that last came to rest in front.
    private var settledAt = 0
    private var lastFront = 0
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var elapsed = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    // The caption's line, and the one fading out under it.
    private(set) var caption = (key: "0-0", focus: 0, face: 0, at: -1.0)
    private(set) var oldCaption: (key: String, focus: Int, face: Int, at: Double)?
    // The touch under way.
    private var touchStart: Date?
    private var panning = false

    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let soft = UIImpactFeedbackGenerator(style: .soft)

    init() {
        let defaults = PetRockCarouselRock.samples.map(\.face)
        faces = defaults
        morphs = defaults.map { PetRockCarouselMorph(face: $0) }
        caption = (key: "0-\(defaults[0])", focus: 0, face: defaults[0], at: -1)
    }

    var now: Double { elapsed }

    func step(to date: Date, settings: PetRockCarouselSettings) {
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

        _ = scroll.step(dt)
        _ = intro.step(dt)
        _ = squash.step(dt)
        _ = wobble.step(dt)
        _ = press.step(dt)
        for k in morphs.indices { morphs[k].step(dt) }
        if let at = softAt, elapsed >= at {
            softAt = nil
            // step runs inside the TimelineView's body; feedback goes out after it.
            DispatchQueue.main.async { self.soft.impactOccurred() }
        }

        // autoPlay: a fixed script on a clock, paused for a while by any touch.
        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                // Back to the title, then the loop starts over from its rest.
                if entered {
                    exit(settings)
                    clock = M.exitAt
                } else {
                    clock = 0
                }
            }
        } else if looping {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            func passed(_ at: Double) -> Bool { (before < at && t >= at) || (t < before && (at > before || at <= t)) }
            if passed(M.pressAt) {
                press.run([
                    .timing(to: 0.96, duration: 0.11, curve: M.inOutQuad, delay: 0),
                    .timing(to: 1, duration: 0.18, curve: M.inOutQuad, delay: 0),
                ])
            }
            if passed(M.enterAt) { enter(user: false, settings) }
            for step in M.steps where passed(step.at) && entered { goTo(step.to, velocity: 0, user: false, settings) }
            if passed(M.wiggleAt) && entered { wiggle(user: false, settings) }
            if passed(M.exitAt) { exit(settings) }
        }

        // Googly pupils: a damped spring towards hanging low, pushed by how the rock accelerates.
        let s = scroll.value
        let v = (s - memory.s) / dt
        let smooth = memory.v + (v - memory.v) * 0.5
        let accel = (smooth - memory.v) / dt
        let w = wobble.value
        let wv = (w - memory.w) / dt
        let wa = (wv - memory.wv) / dt
        let q = squash.value
        let qv = (q - memory.q) / dt
        let qa = (qv - memory.qv) / dt
        memory = (s, smooth, w, wv, q, qv)
        if let goal = arriving, !dragging, abs(s - Double(goal)) < M.arrivedAt, abs(smooth) < M.arrivedSpeed {
            arriving = nil
            settled(goal, user: arrivingByUser, settings)
        }

        // A selection tick as each rock passes the front under the user's finger, and the caption follows.
        let front = Int(M.clamp(M.round(s), 0, last))
        if front != lastFront {
            lastFront = front
            if entered {
                setFocus(front)
                if byUser {
                    DispatchQueue.main.async { self.selection.selectionChanged() }
                }
            }
        }

        guard !settings.reducedMotion else { return }
        if let at = joltAt, elapsed >= at {
            joltAt = nil
            let flip = jolts % 2 == 0 ? 1.0 : -1.0
            jolts += 1
            pvx += 7 * flip
            pvy -= 6
        }
        let fx = -wa * M.pupilFromWobble
        let fy = accel * settings.layout.near * M.pupilFromScroll + qa * M.pupilFromSquash
        pvx += (-M.pupilStiffness * px - M.pupilDamping * pvx + M.clamp(fx, -400, 400)) * dt
        pvy += (-M.pupilStiffness * (py - M.pupilRest) - M.pupilDamping * pvy + M.clamp(fy, -400, 400)) * dt
        var x = px + pvx * dt
        var y = py + pvy * dt
        let r = hypot(x, y)
        if r > 1 {
            // The pupil hits the rim of its eye and bounces off it.
            x /= r
            y /= r
            let along = pvx * x + pvy * y
            if along > 0 {
                pvx -= M.pupilBounce * along * x
                pvy -= M.pupilBounce * along * y
            }
        }
        px = x
        py = y
    }

    private func setFocus(_ index: Int) {
        focus = index
        refreshCaption()
    }

    private func refreshCaption() {
        let key = "\(focus)-\(faces[focus])"
        guard key != caption.key else { return }
        oldCaption = (caption.key, caption.focus, caption.face, elapsed)
        caption = (key, focus, faces[focus], elapsed)
    }

    /// A rock came to rest in front: unless it is the one that was already there, it changes face.
    private func settled(_ index: Int, user: Bool, _ settings: PetRockCarouselSettings) {
        setFocus(index)
        // Reached from step, inside the TimelineView's body: the callback runs after it.
        if let onFocus = settings.onFocus {
            let name = rocks[index].name
            DispatchQueue.main.async { onFocus(name) }
        }
        guard index != settledAt else { return }
        settledAt = index
        // The next expression, skipping any worn by the rocks in view around it (two each way).
        let nearby = [index - 2, index - 1, index + 1, index + 2].compactMap { faces.indices.contains($0) ? faces[$0] : nil }
        var next = faces[index]
        repeat { next = (next + 1) % PetRockCarouselFaces.count } while nearby.contains(next)
        setFace(index, next)
        refreshCaption()
        joltAt = elapsed + M.morphDelay + 0.016
        if user { softAt = elapsed + M.morphDelay }
    }

    private func setFace(_ index: Int, _ face: Int) {
        faces[index] = face
        morphs[index].show(face)
    }

    private func goTo(_ target: Int, velocity: Double, user: Bool, _ settings: PetRockCarouselSettings) {
        byUser = user
        let to = Int(M.clamp(Double(target), 0, last))
        // The rock counts as arrived as soon as it is still in front, before the spring's last wobble.
        arriving = to
        arrivingByUser = user
        if settings.reducedMotion {
            scroll.run([.timing(to: Double(to), duration: 0.2, curve: M.inOutQuad, delay: 0)])
        } else {
            scroll.run([.spring(to: Double(to), stiffness: M.snapStiffness, damping: M.snapDamping)], velocity: velocity == 0 ? nil : velocity)
        }
    }

    private func enter(user: Bool, _ settings: PetRockCarouselSettings) {
        guard !entered else { return }
        entered = true
        scroll.jump(to: 0)
        if settings.reducedMotion {
            intro.run([.timing(to: 1, duration: M.fadeTime, curve: M.inOutQuad, delay: 0)])
        } else {
            intro.run([.spring(to: 1, stiffness: M.introStiffness, damping: M.introDamping)])
        }
        inCarousel = true
        if user { light.impactOccurred() }
    }

    private func exit(_ settings: PetRockCarouselSettings) {
        guard entered else { return }
        entered = false
        dragging = false
        arriving = nil
        if settings.reducedMotion {
            scroll.run([.timing(to: 0, duration: M.fadeTime, curve: M.inOutQuad, delay: 0)])
            intro.run([.timing(to: 0, duration: M.fadeTime, curve: M.inOutQuad, delay: 0)])
        } else {
            scroll.run([.spring(to: 0, stiffness: M.snapStiffness, damping: M.snapDamping)])
            intro.run([.spring(to: 0, stiffness: M.introStiffness, damping: M.introDamping)])
        }
        // Back on the title: the first rock in front, and every rock in its first face.
        inCarousel = false
        focus = 0
        settledAt = 0
        for k in rocks.indices { setFace(k, rocks[k].face) }
        refreshCaption()
    }

    private func wiggle(user: Bool, _ settings: PetRockCarouselSettings) {
        if user { medium.impactOccurred() }
        if settings.reducedMotion { return }
        squash.run([
            .timing(to: 1, duration: 0.09, curve: M.outQuad, delay: 0),
            .spring(to: 0, stiffness: 380, damping: 8),
        ])
        wobble.run([
            .timing(to: 1, duration: 0.08, curve: M.outQuad, delay: 0),
            .spring(to: 0, stiffness: 240, damping: 6),
        ])
    }

    private func touched() {
        hold = M.userHold
    }

    // MARK: Buttons

    func enterPressed(_ settings: PetRockCarouselSettings) {
        touched()
        enter(user: true, settings)
    }

    func titlePressed(_ settings: PetRockCarouselSettings) {
        touched()
        exit(settings)
    }

    // MARK: Gestures

    func touchMoved(_ value: DragGesture.Value, settings: PetRockCarouselSettings) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
            touched()
        }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.dragSlop else { return }
            panning = true
            guard entered else { return }
            byUser = true
            arriving = nil
            dragFrom = scroll.value
            dragging = true
        }
        guard dragging else { return }
        scroll.jump(to: M.rubberBand(dragFrom - value.translation.height / settings.layout.near, last: last))
    }

    func touchEnded(_ value: DragGesture.Value, settings: PetRockCarouselSettings) {
        defer {
            touchStart = nil
            panning = false
        }
        let layout = settings.layout
        if panning {
            guard dragging else { return }
            dragging = false
            // The finger's speed at release, in rocks per second, carries into the snap.
            let raw = -value.velocity.height / layout.near
            let velocity = raw.isFinite ? raw : 0
            let s = scroll.value
            let outside = s < 0 || s > last
            // Clamped before Int(), which traps on NaN or a huge throw.
            let target = M.clamp(M.round(s + (outside ? 0 : velocity * M.throwTime)), 0, last)
            goTo(Int(target), velocity: outside ? velocity * 0.3 : velocity, user: true, settings)
            return
        }
        guard let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration else { return }
        let point = value.location
        if !entered {
            // The little rock in the title can be poked too.
            if abs(point.y - layout.titleY) < M.titleRock && abs(point.x - layout.titleX) < M.titleRock { wiggle(user: true, settings) }
            return
        }
        let dy = point.y - layout.centreY
        if abs(dy) < layout.big * 0.45 {
            wiggle(user: true, settings)
        } else {
            goTo(Int(M.clamp(M.round(scroll.value), 0, last)) + (dy > 0 ? 1 : -1), velocity: 0, user: true, settings)
        }
    }

    /// The system took the touch away (no onEnded): a drag snaps to the nearest rock, and the next
    /// touch starts afresh.
    func touchCancelled(_ settings: PetRockCarouselSettings) {
        guard touchStart != nil else { return }
        touchStart = nil
        panning = false
        if dragging {
            dragging = false
            goTo(Int(M.clamp(M.round(scroll.value), 0, last)), velocity: 0, user: true, settings)
        }
    }
}
