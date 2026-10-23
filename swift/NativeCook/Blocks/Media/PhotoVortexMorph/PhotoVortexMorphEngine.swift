import SwiftUI
import UIKit

enum PhotoVortexMorphMetrics {
    // The five layouts, in the order a tap walks through them.
    static let vortex = 0
    static let drift = 1
    static let stack = 2
    static let strip = 3
    static let grid = 4
    static let layouts = 5
    static let names = ["Vortex", "Drift", "Stack", "Strip", "Grid"]
    static let lines = ["Forty frames in orbit", "Scattered through depth", "Gathered into one", "Fanned along a line", "Laid edge to edge"]

    static let paper: UInt32 = 0xf3f2ee
    static let cardTone: UInt32 = 0xdcd9d2

    // Vortex: cards on a ring `ringRadius`·width from the middle, each turned `twist` rad past its
    // spoke so they overlap like petals. It turns by itself at one full turn per loop; a drag turns
    // it with the finger and a flick keeps it spinning, the extra speed fading by `spinDecay` per second.
    static let ringRadius = 0.25
    static let ringCardW = 0.27
    static let ringCardH = 0.355
    static let twist = -0.5
    static let spinDecay = 1.4
    static let maxSpin = 14.0
    // A fast spin opens the ring by up to `ringOpen` of its radius.
    static let ringOpen = 0.16

    // Drift: the cards float in a box `driftX` by `driftY` (shares of the screen) and `driftZ` deep
    // (shares of the width), seen from `camera` widths away. The box turns about the vertical axis at
    // `driftTurn` rad/s; a drag turns it (`driftDrag` rad per pt) and tips it (`driftTip` rad per pt).
    static let driftX = 0.7
    static let driftY = 0.42
    static let driftZ = 0.7
    static let camera = 1.6
    static let driftCardW = 0.135
    static let driftCardH = 0.175
    static let driftTurn = 0.11
    static let driftDrag = 0.005
    static let driftTip = 0.0035
    static let driftTipMax = 0.45
    static let driftDecay = 1.8
    // Each card bobs `bob` pt, three times per loop, so a recording of one loop repeats exactly.
    static let bob = 5.0

    // Stack: one pile in the middle, the cards under the top one off by up to `stackJitter` pt and
    // `stackTilt` rad. A drag pulls the pile, which follows with resistance and springs back.
    static let stackW = 0.42
    static let stackH = 0.56
    static let stackJitter = 4.0
    static let stackTilt = 0.05

    // Strip: the cards side by side, each showing a sliver `stripStep`·width wide, one open in full.
    // A drag slides which card is open, `stripDrag` pt per card.
    static let stripW = 0.34
    static let stripH = 0.45
    static let stripStep = 0.012
    static let stripDrag = 9.0

    // Grid: `gridCols` by `gridRows` tiles over the whole screen, `gridGap` pt apart.
    static let gridCols = 5
    static let gridRows = 8
    static let gridGap = 2.0

    // Each card travels on its own spring: stiffness `stiffness` times 0.8 to 1.2, damping ratio
    // `damping`. On the way it is spun `twirl` rad/s and tipped `tip` rad/s, so the cards tumble a little.
    static let stiffness = 150.0
    static let damping = 0.74
    static let twirl = 2.6
    static let tip = 1.6
    static let substep = 1.0 / 120
    // A drag on the pile or the grid follows the finger with this resistance, never past `pullMax` pt.
    static let pull = 0.55
    static let pullMax = 140.0
    static let releaseSpring = (duration: 0.65, dampingRatio: 0.62)
    static let snapSpring = (duration: 0.52, dampingRatio: 0.9)

    // autoPlay: the vortex turns for 2.4 s, then the layouts follow at these times (s) and the loop
    // starts over every `loop` s, back on a settled vortex. The strip slides open card `peekCard` and back.
    static let loop = 14.4
    static let script: [(at: Double, to: Int)] = [(2.4, drift), (5.0, stack), (7.0, strip), (9.4, grid), (11.8, vortex)]
    static let peekAt = 7.9
    static let peekBack = 8.6
    static let peekCard = 27.0
    // Where the loop stands just after each layout arrives, for picking it up after a touch.
    static let scriptStart = [0, 2.4, 5.0, 7.0, 9.4]
    static let userHold = 3.0

    // Captions: the new one rises `captionRise` pt into place over `captionIn` s.
    static let captionIn = 0.45
    static let captionRise = 10.0
    static let imageFade = 0.35
    static let panSlop: CGFloat = 6
    static let tapMaxDuration = 0.35

    // A card's pose: centre, size, turn in the plane, tip about the vertical and horizontal axes,
    // and its place in the drawing order. Positions and velocities are kept side by side.
    static let x = 0
    static let y = 1
    static let w = 2
    static let h = 3
    static let roll = 4
    static let yaw = 5
    static let pitch = 6
    static let layer = 7
    static let k = 8

    static func clamp(_ value: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, value)) }

    /// Resistance past an edge: the further it is pulled, the less it gives, never past `max`.
    static func rubber(_ offset: Double, _ max: Double) -> Double {
        let sign: Double = offset < 0 ? -1 : 1
        let d = abs(offset) * pull
        return sign * max * (1 - 1 / (d / max + 1))
    }

    static func wrapAngle(_ a: Double) -> Double {
        let t = Double.pi * 2
        return a - t * floor((a + Double.pi) / t)
    }

    static func easeOut(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) * (1 - t) }

    private static let strong = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    static func easeOutStrong(_ p: Double) -> Double { strong.value(at: p) }
}

/// One photo of the set; `shape` is how the original is framed, so it is fetched wide enough to crop.
struct PhotoVortexMorphPhotoSource {
    enum Shape { case portrait, square, landscape }
    var id: String
    var shape: Shape

    // A landscape original loses two thirds of its width to a portrait card, so it is fetched wider.
    var uri: String {
        let width = switch shape {
        case .portrait: 420
        case .square: 480
        case .landscape: 720
        }
        return "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(width)&q=80"
    }

    private static func p(_ id: String) -> Self { Self(id: "photo-\(id)", shape: .portrait) }
    private static func sq(_ id: String) -> Self { Self(id: "photo-\(id)", shape: .square) }
    private static func ls(_ id: String) -> Self { Self(id: "photo-\(id)", shape: .landscape) }

    // Fashion, film stills, galleries and rooms, dealt so neighbours differ. The last tops the stack.
    static let samples: [Self] = [
        ls("1515634928627-2a4e0dae3ddf"), p("1613915617430-8ab0fd7c6baf"), ls("1606819717115-9159c900370b"),
        ls("1618221195710-dd6b41faaea6"), ls("1577190651915-bf62d54d5b36"), p("1549298222-1c31e8915347"),
        ls("1518998053901-5348d3961a04"), p("1618220179428-22790b461013"), ls("1632187981988-40f3cbaeef5e"),
        p("1645996830739-8fe3df27c33f"), ls("1569783721854-33a99b4c0bae"), sq("1586023492125-27b2c045efd7"),
        p("1529798856831-427dfd0a1ab1"), p("1541130292430-a832637ddc0d"), ls("1554907984-15263bfd63bd"),
        ls("1664711942326-2c3351e215e6"), ls("1723723467478-eb4667116137"), sq("1553544260-f87e671974ee"),
        sq("1582555172866-f73bb12a2ab3"), p("1606744837616-56c9a5c6a6eb"), ls("1585951237318-9ea5e175b891"),
        ls("1538329972958-465d6d2144ed"), p("1565876427310-0695a4ff03b7"), sq("1616046229478-9901c5536a45"),
        ls("1486693128850-a77436e7ba3c"), ls("1545989253-02cc26577f88"), p("1583847268964-b28dc8f51f92"),
        ls("1585951237313-1979e4df7385"), p("1522878308970-972ec5eedc0d"), p("1564078516393-cf04bd966897"),
        ls("1532800783378-1bed60adaf58"), ls("1605429523419-d828acb941d9"), p("1599696848652-f0ff23bc911f"),
        ls("1496680392913-a0417ec1a0ad"), ls("1507643179773-3e975d7ac515"), ls("1606744888344-493238951221"),
        ls("1596972812018-6385b8bd3e13"), p("1535385793343-27dff1413c5a"), p("1696332415657-9fbe802a8875"),
        p("1580478491436-fd6a937acc9e"),
    ]
}

/// Where each card floats in the drift, in shares of the box, and how it is tipped there.
struct PhotoVortexMorphSeed {
    var x, y, z, yaw, pitch, roll, stiff, spin, phase: Double

    /// The drift is dealt on a jittered 5 by 8 grid, so the cards spread evenly but not in rows. The
    /// React Native version shuffles the cells with a random sort; its result (and the draws it
    /// spent) is kept here so every stack scatters the cards the same way.
    static func sow(_ count: Int) -> [Self] {
        let shuffled = [27, 31, 7, 14, 26, 39, 29, 2, 21, 37, 25, 22, 28, 33, 16, 3, 36, 19, 8, 24,
                        10, 13, 4, 0, 6, 11, 17, 23, 9, 34, 32, 1, 12, 18, 5, 35, 38, 15, 20, 30]
        var r = PhotoVortexMorphRandom(seed: 23)
        let order: [Int]
        if count == shuffled.count {
            for _ in 0..<170 { _ = r.unit() }
            order = shuffled
        } else {
            order = Array(0..<count).shuffled(using: &r)
        }
        typealias M = PhotoVortexMorphMetrics
        return (0..<count).map { i in
            let cell = order[i]
            let col = cell % M.gridCols
            let row = (cell / M.gridCols) % M.gridRows
            let x = ((Double(col) + 0.15 + r.unit() * 0.7) / Double(M.gridCols)) * 2 - 1
            let y = ((Double(row) + 0.15 + r.unit() * 0.7) / Double(M.gridRows)) * 2 - 1
            let z = r.unit() * 2 - 1
            let yaw = (r.unit() * 2 - 1) * 0.55
            let pitch = (r.unit() * 2 - 1) * 0.35
            let roll = (r.unit() * 2 - 1) * 0.3
            let stiff = 0.8 + r.unit() * 0.4
            let spin: Double = r.unit() < 0.5 ? -1 : 1
            let phase = r.unit() * Double.pi * 2
            return Self(x: x, y: y, z: z, yaw: yaw, pitch: pitch, roll: roll, stiff: stiff, spin: spin, phase: phase)
        }
    }
}

/// The same small seeded generator as the React Native version (mulberry32), so the drift matches.
struct PhotoVortexMorphRandom: RandomNumberGenerator {
    private var s: UInt32

    init(seed: UInt32) { s = seed }

    mutating func unit() -> Double {
        s = s &+ 0x6d2b_79f5
        var t = s
        t = (t ^ (t >> 15)) &* (t | 1)
        t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
        return Double(t ^ (t >> 14)) / 4_294_967_296
    }

    mutating func next() -> UInt64 {
        let a = UInt64(unit() * 4_294_967_296)
        let b = UInt64(unit() * 4_294_967_296)
        return a << 32 | b
    }
}

/// Everything a layout's poses depend on besides the card.
struct PhotoVortexMorphScene {
    var width, height, spin, spinSpeed, turn, tip, pullX, pullY, open, bob: Double

    private typealias M = PhotoVortexMorphMetrics

    /// Card `i`'s pose in `layout`, written into `out`.
    func pose(_ layout: Int, _ i: Int, _ n: Int, _ seed: PhotoVortexMorphSeed, into out: inout [Double]) {
        let cx = width / 2
        let cy = height / 2
        let sw = width
        switch layout {
        case M.vortex:
            let open = 1 + M.ringOpen * tanh(abs(spinSpeed) / 6)
            let a = Double(i) / Double(n) * Double.pi * 2 + spin
            out[M.x] = cx + cos(a) * M.ringRadius * sw * open
            out[M.y] = cy + sin(a) * M.ringRadius * sw * open
            out[M.w] = M.ringCardW * sw
            out[M.h] = M.ringCardH * sw
            out[M.roll] = a + Double.pi / 2 + M.twist
            out[M.yaw] = 0
            out[M.pitch] = 0
            out[M.layer] = Double(i)
        case M.drift:
            // The box turned and tipped, then seen through the lens.
            let bx = seed.x * M.driftX * sw
            let by = seed.y * M.driftY * height + sin(bob + seed.phase) * M.bob
            let bz = seed.z * M.driftZ * sw
            let cT = cos(turn)
            let sT = sin(turn)
            let x1 = bx * cT + bz * sT
            let z1 = -bx * sT + bz * cT
            let cP = cos(tip)
            let sP = sin(tip)
            let y2 = by * cP - z1 * sP
            let z2 = by * sP + z1 * cP
            let d = M.camera * sw
            let k = d / max(d + z2, d * 0.3)
            out[M.x] = cx + x1 * k
            out[M.y] = cy + y2 * k
            out[M.w] = M.driftCardW * sw * k
            out[M.h] = M.driftCardH * sw * k
            out[M.roll] = seed.roll
            out[M.yaw] = seed.yaw + turn * 0.8
            out[M.pitch] = seed.pitch - tip * 0.8
            out[M.layer] = -z2
        case M.stack:
            let top = i == n - 1
            out[M.x] = cx + pullX + (top ? 0 : sin(seed.phase) * M.stackJitter)
            out[M.y] = cy + pullY + (top ? 0 : cos(seed.phase * 1.3) * M.stackJitter)
            out[M.w] = M.stackW * sw
            out[M.h] = M.stackH * sw
            out[M.roll] = top ? 0 : seed.roll * (M.stackTilt / 0.3)
            out[M.yaw] = 0
            out[M.pitch] = 0
            out[M.layer] = Double(i)
        case M.strip:
            // Each card lies over the one before, showing only a sliver of it; the cards after the
            // open one move right by a card's width, so it shows in full. The last card always does.
            let step = M.stripStep * sw
            let w = M.stripW * sw
            let gap = (w - step) * M.clamp(Double(n - 1) - open, 0, 1)
            let total = Double(n - 1) * step + w + gap
            let left = cx - total / 2 + pullX
            out[M.x] = left + Double(i) * step + (w - step) * M.clamp(Double(i) - open, 0, 1) + w / 2
            out[M.y] = cy + pullY
            out[M.w] = w
            out[M.h] = M.stripH * sw
            out[M.roll] = 0
            out[M.yaw] = 0
            out[M.pitch] = 0
            out[M.layer] = Double(i)
        default:
            let col = i % M.gridCols
            let row = i / M.gridCols
            let tw = (width - M.gridGap * Double(M.gridCols - 1)) / Double(M.gridCols)
            let th = (height - M.gridGap * Double(M.gridRows - 1)) / Double(M.gridRows)
            out[M.x] = Double(col) * (tw + M.gridGap) + tw / 2 + pullX
            out[M.y] = Double(row) * (th + M.gridGap) + th / 2 + pullY
            out[M.w] = tw
            out[M.h] = th
            out[M.roll] = 0
            out[M.yaw] = 0
            out[M.pitch] = 0
            out[M.layer] = Double(i)
        }
    }

    /// How long card `i` waits before leaving for `layout`, in seconds, so the cards go in a wave.
    func delay(_ layout: Int, _ i: Int, _ n: Int, _ state: [Double]) -> Double {
        switch layout {
        case M.drift: return Double(i) / Double(n) * 0.45
        case M.stack:
            let dx = state[i * M.k + M.x] - width / 2
            let dy = state[i * M.k + M.y] - height / 2
            return min(0.3, hypot(dx, dy) / height * 0.5)
        case M.strip: return Double(n - 1 - i) / Double(n) * 0.28
        case M.grid: return Double(i % M.gridCols + i / M.gridCols) * 0.032
        default: return Double(i) / Double(n) * 0.32
        }
    }
}

struct PhotoVortexMorphPhoto {
    var image: UIImage
    /// The block's clock when it arrived, for its fade.
    var at: Double
}

/// One frame to draw.
struct PhotoVortexMorphFrame {
    var width: Double
    var height: Double
    /// Every card's pose (the first `n · k` values; velocities follow).
    var state: [Double]
    var count: Int
    /// The settled vortex is drawn twice, its overlap seam on opposite sides.
    var settled: Bool
    var spin: Double
    var photos: [PhotoVortexMorphPhoto?]
    var elapsed: Double
    var alpha: Double
    var layout: Int
    var previous: Int
    /// How far the current caption has come in, 0 … 1.
    var fresh: Double
}

struct PhotoVortexMorphSettings {
    var count: Int
    var autoPlay: Bool
    var reducedMotion: Bool
    var onLayoutChange: ((Int) -> Void)?
}

/// The cards' state, stepped once a frame like the RN frame callback, and the gestures that move them.
@MainActor
final class PhotoVortexMorphEngine {
    private typealias M = PhotoVortexMorphMetrics

    private var width = 0.0
    private var height = 0.0
    private var seeds: [PhotoVortexMorphSeed] = []
    // Seconds since the block appeared, and the poses and velocities of every card.
    private var elapsed = 0.0
    private var state: [Double] = []
    // Whether each card has left for the current layout yet.
    private var gone: [Bool] = []
    private var photos: [PhotoVortexMorphPhoto?] = []
    private var layout = M.vortex
    private var previous = M.vortex
    private var changedAt = 0.0
    // Scene values the layouts read.
    private var spin = 0.0
    private var spinSpeed = 0.0
    private var turn = 0.0
    private var turnSpeed = 0.0
    private var tip = PhotoVortexMorphValue(0)
    private var pullX = PhotoVortexMorphValue(0)
    private var pullY = PhotoVortexMorphValue(0)
    private var open = PhotoVortexMorphValue(39)
    // Gesture bookkeeping.
    private var dragging = false
    private var lastAngle = 0.0
    private var dragFrom = 0.0
    private var tipFrom = 0.0
    private var settled = true
    // Under reduced motion the cards fade out, jump and fade in.
    private var fade = PhotoVortexMorphValue(1)
    private var fadingTo: Int?
    private var jump = -1
    // The self-playing loop.
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    // The touch under way.
    private var touchStart: Date?
    private var touchOrigin = CGPoint.zero
    private var panning = false

    private let impact = UIImpactFeedbackGenerator(style: .light)
    private let selection = UISelectionFeedbackGenerator()

    // MARK: Photos

    func resetPhotos(count: Int) {
        photos = Array(repeating: nil, count: count)
    }

    func photoArrived(_ image: UIImage, at index: Int) {
        guard photos.indices.contains(index) else { return }
        photos[index] = PhotoVortexMorphPhoto(image: image, at: elapsed)
    }

    // MARK: Frame

    private var scene: PhotoVortexMorphScene {
        PhotoVortexMorphScene(
            width: width, height: height, spin: spin, spinSpeed: spinSpeed, turn: turn, tip: tip.value,
            pullX: pullX.value, pullY: pullY.value, open: open.value,
            bob: elapsed * 3 / M.loop * Double.pi * 2
        )
    }

    func step(to date: Date, width: Double, height: Double, settings: PhotoVortexMorphSettings) -> PhotoVortexMorphFrame {
        self.width = width
        self.height = height
        let n = settings.count
        if seeds.count != n {
            seeds = PhotoVortexMorphSeed.sow(n)
            state = []
            gone = []
            open.set(Double(n - 1))
        }
        if photos.count != n { photos = Array(repeating: nil, count: n) }
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 && width > 0 {
            advance(dt: dt, settings: settings)
        }
        let fresh = settings.reducedMotion ? 1 : M.easeOut(M.clamp((elapsed - changedAt) / M.captionIn, 0, 1))
        return PhotoVortexMorphFrame(
            width: width, height: height, state: state, count: n, settled: settled, spin: spin,
            photos: photos, elapsed: elapsed, alpha: fade.value, layout: layout, previous: previous, fresh: fresh
        )
    }

    private func advance(dt: Double, settings: PhotoVortexMorphSettings) {
        let n = settings.count
        let now = elapsed + dt
        elapsed = now
        tip.step(dt: dt, now: now)
        pullX.step(dt: dt, now: now)
        pullY.step(dt: dt, now: now)
        open.step(dt: dt, now: now)
        fade.step(dt: dt, now: now)
        if let to = fadingTo, !fade.isAnimating {
            // Faded out: jump, then fade back in.
            fadingTo = nil
            jump = to
            fade.timing(to: 1, duration: 0.22, curve: M.easeOutStrong, now: now)
        }

        // The script.
        if hold > 0 {
            hold -= dt
            if hold <= 0 { clock = M.scriptStart[layout] }
        } else if looping {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            func crossed(_ at: Double) -> Bool {
                (before < at && t >= at) || (t < before && (at > before || at <= t))
            }
            for step in M.script where crossed(step.at) { goTo(step.to, user: false, settings: settings) }
            if crossed(M.peekAt) && layout == M.strip {
                open.spring(to: M.peekCard, duration: M.snapSpring.duration, dampingRatio: M.snapSpring.dampingRatio)
            }
            if crossed(M.peekBack) && layout == M.strip {
                open.spring(to: Double(n - 1), duration: M.snapSpring.duration, dampingRatio: M.snapSpring.dampingRatio)
            }
        }

        // The vortex turns by itself, plus whatever a flick left it; the drift turns slowly.
        if !dragging {
            let base = settings.reducedMotion ? 0 : Double.pi * 2 / M.loop
            spin = M.wrapAngle(spin + (base + spinSpeed) * dt)
            spinSpeed *= exp(-M.spinDecay * dt)
            if layout == M.drift {
                turn += ((settings.reducedMotion ? 0 : M.driftTurn) + turnSpeed) * dt
                turnSpeed *= exp(-M.driftDecay * dt)
            }
        }

        let s = scene
        let current = layout
        let from = previous
        let since = now - changedAt
        var target = [Double](repeating: 0, count: M.k)
        var worst = 0.0
        let fresh = state.count != n * M.k * 2
        if fresh { state = [Double](repeating: 0, count: n * M.k * 2) }
        let reset = jump
        for i in 0..<n {
            let o = i * M.k
            let v = n * M.k + o
            let leaving = since >= s.delay(current, i, n, state)
            // A jump under reduced motion lands on the layout it jumps to.
            s.pose(reset >= 0 ? reset : leaving ? current : from, i, n, seeds[i], into: &target)
            if fresh || reset >= 0 {
                // First frame, or a jump under reduced motion: straight to the pose, at rest.
                for c in 0..<M.k {
                    state[o + c] = target[c]
                    state[v + c] = 0
                }
                continue
            }
            if leaving && gone.count == n && !gone[i] {
                // Leaving: a little spin and tip, a different way for every card.
                gone[i] = true
                let seed = seeds[i]
                state[v + M.roll] += seed.spin * M.twirl * seed.stiff
                state[v + M.yaw] += -seed.spin * M.tip * (2 - seed.stiff)
            }
            // Angles take the short way round.
            state[o + M.roll] = target[M.roll] + M.wrapAngle(state[o + M.roll] - target[M.roll])
            let k = M.stiffness * seeds[i].stiff * (current == M.stack ? 0.55 + 0.6 * Double(i) / Double(max(1, n - 1)) : 1)
            let c = 2 * M.damping * sqrt(k)
            var left = dt
            while left > 1e-6 {
                let h = min(M.substep, left)
                left -= h
                for q in 0..<M.k {
                    let a = k * (target[q] - state[o + q]) - c * state[v + q]
                    state[v + q] += a * h
                    state[o + q] += state[v + q] * h
                }
            }
            worst = max(worst, abs(state[o + M.x] - target[M.x]) + abs(state[o + M.y] - target[M.y]))
        }
        if reset >= 0 {
            layout = reset
            previous = reset
            jump = -1
        }
        settled = current == M.vortex && worst < 8
    }

    /// Moves on to `to`: every card waits its turn in the wave, then leaves with a little tumble.
    private func goTo(_ to: Int, user: Bool, settings: PhotoVortexMorphSettings) {
        if user { impact.impactOccurred() }
        settings.onLayoutChange?(to)
        if settings.reducedMotion {
            fade.timing(to: 0, duration: 0.16, curve: M.easeOutStrong, now: elapsed)
            fadingTo = to
            return
        }
        previous = layout
        layout = to
        changedAt = elapsed
        gone = Array(repeating: false, count: settings.count)
        // Each layout starts from rest: the drift square on, the stack and grid unpulled, the strip on its last card.
        pullX.set(0)
        pullY.set(0)
        if to == M.drift {
            turn = -0.2
            turnSpeed = 0
            tip.set(0)
        }
        if to == M.strip { open.set(Double(settings.count - 1)) }
    }

    private func touched() {
        hold = M.userHold
    }

    // MARK: Gestures

    /// One recogniser stands in for the race of a pan (after 6 pt of travel) and a tap (a touch
    /// lifted within 350 ms that never travelled that far).
    func touchMoved(_ value: DragGesture.Value, settings: PhotoVortexMorphSettings) {
        if touchStart == nil || value.startLocation != touchOrigin {
            touchStart = value.time
            touchOrigin = value.startLocation
            panning = false
            // A drag SwiftUI cancelled never reached onEnded; let the vortex turn by itself again.
            dragging = false
        }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.panSlop else { return }
            panning = true
            panStart(at: value.location)
        }
        panUpdate(value, settings: settings)
    }

    func touchEnded(_ value: DragGesture.Value, settings: PhotoVortexMorphSettings) {
        defer {
            touchStart = nil
            panning = false
        }
        if panning {
            panEnd(value, settings: settings)
            return
        }
        guard let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration, width > 0 else { return }
        touched()
        goTo((layout + 1) % M.layouts, user: true, settings: settings)
    }

    private func panStart(at point: CGPoint) {
        touched()
        dragging = true
        lastAngle = atan2(point.y - height / 2, point.x - width / 2)
        dragFrom = layout == M.strip ? open.value : turn
        tipFrom = tip.value
        spinSpeed = 0
        turnSpeed = 0
    }

    private func panUpdate(_ value: DragGesture.Value, settings: PhotoVortexMorphSettings) {
        let n = Double(settings.count)
        let tx = value.translation.width
        let ty = value.translation.height
        switch layout {
        case M.vortex:
            // The ring turns with the finger about its middle.
            let a = atan2(value.location.y - height / 2, value.location.x - width / 2)
            spin += M.wrapAngle(a - lastAngle)
            lastAngle = a
        case M.drift:
            turn = dragFrom + tx * M.driftDrag
            tip.set(M.clamp(tipFrom - ty * M.driftTip, -M.driftTipMax, M.driftTipMax))
        case M.strip:
            // Past either end the strip gives less and less.
            let raw = dragFrom - tx / M.stripDrag
            let over = raw < 0 ? raw : raw > n - 1 ? raw - (n - 1) : 0
            open.set(raw - over + M.rubber(over * M.stripDrag, 60) / M.stripDrag)
        default:
            pullX.set(M.rubber(tx, M.pullMax))
            pullY.set(M.rubber(ty, M.pullMax))
        }
    }

    private func panEnd(_ value: DragGesture.Value, settings: PhotoVortexMorphSettings) {
        touched()
        dragging = false
        let vx = value.velocity.width
        let vy = value.velocity.height
        let reduced = settings.reducedMotion
        let r = M.releaseSpring
        switch layout {
        case M.vortex:
            // The finger's speed about the middle carries on as spin.
            let rx = value.location.x - width / 2
            let ry = value.location.y - height / 2
            let r2 = max(rx * rx + ry * ry, 40 * 40)
            spinSpeed = reduced ? 0 : M.clamp((rx * vy - ry * vx) / r2, -M.maxSpin, M.maxSpin)
        case M.drift:
            turnSpeed = reduced ? 0 : vx * M.driftDrag
            tip.spring(to: 0, duration: r.duration, dampingRatio: r.dampingRatio, velocity: -vy * M.driftTip)
        case M.strip:
            // Lands on the card nearest where the throw was heading.
            let v = -vx / M.stripDrag
            let aim = (M.clamp(open.value + v * 0.2, 0, Double(settings.count - 1))).rounded(.toNearestOrAwayFromZero)
            open.spring(to: aim, duration: M.snapSpring.duration, dampingRatio: M.snapSpring.dampingRatio, velocity: v)
            selection.selectionChanged()
        default:
            pullX.spring(to: 0, duration: r.duration, dampingRatio: r.dampingRatio, velocity: vx * 0.3)
            pullY.spring(to: 0, duration: r.duration, dampingRatio: r.dampingRatio, velocity: vy * 0.3)
        }
    }
}

// MARK: - Photos

/// Downloads a photo once and decodes it off the main thread, so the first frame that draws it does not stall.
enum PhotoVortexMorphPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await Task.detached(priority: .userInitiated) { image.preparingForDisplay() ?? image }.value
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}

// MARK: - Springs

/// Reanimated's `withSpring` with a duration and a damping ratio, stepped a frame at a time: the
/// stiffness is solved so the spring's energy falls to the threshold after 1.5 × the duration, and
/// each frame moves along the exact solution from where the last one left it.
struct PhotoVortexMorphSpring {
    let target: Double
    let duration: Double
    let dampingRatio: Double
    private(set) var velocity: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, carried: Double, duration: Double, dampingRatio: Double) {
        self.target = target
        self.duration = duration
        self.dampingRatio = dampingRatio
        var v = carried + velocity
        // Velocity that would carry the first frame away from the target is dropped.
        if (target > value && v < 0) || (target < value && v > 0) { v = 0 }
        self.velocity = v
        let x0 = value - target
        let zeta = dampingRatio
        let settling = duration * 1.5
        func energy(_ x: Double, _ v: Double, _ k: Double) -> Double { 0.5 * k * x * x + 0.5 * v * v }
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * zeta
            let xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
            let vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
            return energy(xt, vt, k) / energy(x0, v, k) - Self.energyThreshold
        }
        // Bisection, as Reanimated solves it.
        var low = Double.ulpOfOne
        var high = 8e3
        let rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while abs(difference(k)) > Self.energyThreshold * 1e-3 && left > 0 {
            left -= 1
            if (difference(k) < 0) == rising { low = k } else { high = k }
            k = (low + high) / 2
        }
        if !k.isFinite { k = 170 }
        stiffness = k
        omega0 = sqrt(k)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        initialEnergy = energy(x0, v, k)
    }

    /// Moves `value` on by `dt` seconds; true once the spring has come to rest on its target.
    mutating func step(_ value: inout Double, dt: Double) -> Bool {
        if initialEnergy == 0 || !initialEnergy.isFinite {
            value = target
            return true
        }
        let zeta = dampingRatio
        let x0 = value - target
        let v0 = velocity
        if zeta < 1 {
            let sin1 = sin(omega1 * dt)
            let cos1 = cos(omega1 * dt)
            let envelope = exp(-zeta * omega0 * dt)
            let frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            value = target + frag
            velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            let envelope = exp(-omega0 * dt)
            value = target + envelope * (x0 + (v0 + omega0 * x0) * dt)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
        }
        let now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
        if now / initialEnergy <= Self.energyThreshold {
            value = target
            velocity = 0
            return true
        }
        return false
    }
}

/// A number driven like a Reanimated shared value: set outright, or animated by a spring or a
/// timing curve that is stepped by the block's frame clock.
struct PhotoVortexMorphValue {
    private enum Motion {
        case spring(PhotoVortexMorphSpring)
        case timing(from: Double, to: Double, start: Double, duration: Double, curve: (Double) -> Double)
    }

    private(set) var value: Double
    private var motion: Motion?

    init(_ value: Double) {
        self.value = value
    }

    var isAnimating: Bool { motion != nil }

    mutating func set(_ value: Double) {
        self.value = value
        motion = nil
    }

    mutating func spring(to target: Double, duration: Double, dampingRatio: Double, velocity: Double = 0) {
        var carried = 0.0
        if case let .spring(running) = motion { carried = running.velocity }
        motion = .spring(PhotoVortexMorphSpring(
            from: value, to: target, velocity: velocity, carried: carried,
            duration: duration, dampingRatio: dampingRatio
        ))
    }

    mutating func timing(to target: Double, duration: Double, curve: @escaping (Double) -> Double, now: Double) {
        motion = .timing(from: value, to: target, start: now, duration: duration, curve: curve)
    }

    mutating func step(dt: Double, now: Double) {
        guard let motion else { return }
        switch motion {
        case .spring(var spring):
            var next = value
            let done = spring.step(&next, dt: dt)
            value = next
            self.motion = done ? nil : .spring(spring)
        case let .timing(from, to, start, duration, curve):
            let p = min(1, max(0, (now - start) / duration))
            value = from + (to - from) * curve(p)
            if p >= 1 { self.motion = nil }
        }
    }
}
