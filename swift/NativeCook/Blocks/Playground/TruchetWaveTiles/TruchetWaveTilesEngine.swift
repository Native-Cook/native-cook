import SwiftUI
import UIKit

/// A two-colour palette, as 0–255 sRGB components.
struct TruchetWaveTilesPalette {
    var name: String
    var ground: [Double]
    var ink: [Double]
}

enum TruchetWaveTilesMetrics {
    static let palettes = [
        TruchetWaveTilesPalette(name: "Ink", ground: [16, 26, 46], ink: [239, 228, 206]),
        TruchetWaveTilesPalette(name: "Kiln", ground: [190, 80, 46], ink: [248, 234, 210]),
        TruchetWaveTilesPalette(name: "Moss", ground: [28, 56, 45], ink: [222, 196, 122]),
        TruchetWaveTilesPalette(name: "Cobalt", ground: [240, 234, 222], ink: [38, 58, 182]),
    ]
    static let styles = ["Arcs", "Diagonals", "Triangles"]

    // The grid: `columns` square tiles across, as many rows as cover the screen, centred.
    static let columns = 8
    // Line widths as a share of a tile: thick ribbons for arcs, thinner for diagonals, none once
    // the triangles are filled in.
    static let arcWidth = 0.2
    static let diagonalWidth = 0.13
    // A quarter circle as a cubic Bézier: control points this share of the radius along the tangents.
    static let kappa = 0.5523

    // A flip turns a tile 90° on a spring: angular frequency `omega` rad/s, damping ratio `damping`,
    // stepped at `substep` s. Mid-turn a tile lifts by up to `lift` of its size, with a shadow.
    static let omega = 2 * Double.pi / 0.62
    static let damping = 0.52
    static let substep = 1.0 / 240
    static let lift = 0.12
    static let liftShadow = 7.0
    // A wave front leaves its origin at `waveSpeed` pt/s; each tile flips as the front reaches its
    // centre. A faint ring marks the front for `ringLife` s.
    static let waveSpeed = 520.0
    static let ringLife = 1.6
    static let maxWaves = 6

    // The style morphs on a spring of 1.1 s, the thumb on one of 0.42 s; a palette crossfades
    // over `paletteTime` s on an ease-in-out.
    static let styleSpring = (duration: 1.1, dampingRatio: 0.92)
    static let thumbSpring = (duration: 0.42, dampingRatio: 0.82)
    static let paletteTime = 1.2
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1))
    static let userHold = 4.0

    // autoPlay, one `loop` in seconds: four waves (so every tile turns a full 360° and the pattern
    // comes back), the styles from arcs through diagonals and triangles back to arcs, four palettes.
    static let loop = 16.0
    static let wavesAt = [0.6, 4.4, 8.2, 12.0]
    static let waveFrom = [CGPoint(x: 0.3, y: 0.34), CGPoint(x: 0.78, y: 0.68), CGPoint(x: 0.22, y: 0.8), CGPoint(x: 0.7, y: 0.18)]
    static let styleAt = [2.4, 6.2, 10.0]
    static let styleTo = [1, 2, 0]
    static let paletteAt = [3.3, 7.1, 10.9, 14.6]

    static let segmentWidth: CGFloat = 92
    static let segmentHeight: CGFloat = 36
    static let barPad: CGFloat = 4
    static let swatch: CGFloat = segmentHeight + barPad * 2
    static let controlsGap: CGFloat = 10
    static let controlsBottom: CGFloat = 18
    static let controlsWidth: CGFloat = segmentWidth * 3 + barPad * 2 + controlsGap + swatch

    static let grainStrength = 0.09

    /// The opening pattern: each tile turned 0–3 quarters, from a fixed seed.
    static let opening: [Int] = {
        var random = TruchetWaveTilesRandom(seed: 7)
        return (0..<512).map { _ in Int(floor(random.next() * 4)) }
    }()

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }

    static func smooth(_ t: Double) -> Double {
        let x = clamp01(t)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }

    /// The tile style at `s` (0 arcs, 1 diagonals, 2 triangles).
    static func style(_ s: Double) -> (reach: Double, round: Double, fill: Double, width: Double) {
        if s <= 1 {
            let e = smooth(s)
            return (0.5 + 0.5 * e, 1 - e, 0, arcWidth + (diagonalWidth - arcWidth) * e)
        }
        let e = smooth(s - 1)
        return (1, 0, e, diagonalWidth * (1 - e))
    }

    static func palette(_ i: Int) -> TruchetWaveTilesPalette {
        let n = palettes.count
        return palettes[((i % n) + n) % n]
    }

    static func color(_ a: [Double], _ b: [Double], _ t: Double, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: (a[0] + (b[0] - a[0]) * t) / 255, green: (a[1] + (b[1] - a[1]) * t) / 255,
              blue: (a[2] + (b[2] - a[2]) * t) / 255, opacity: alpha)
    }
}

/// The seeded generator the React Native version uses (mulberry32), so the opening pattern matches.
struct TruchetWaveTilesRandom {
    private var s: UInt32
    init(seed: UInt32) { s = seed }

    mutating func next() -> Double {
        s = s &+ 0x6d2b_79f5
        var t = s
        t = (t ^ (t >> 15)) &* (t | 1)
        t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
        return Double(t ^ (t >> 14)) / 4_294_967_296
    }
}

/// Reanimated's `withSpring` with a duration and a damping ratio: the stiffness is solved so the
/// spring's energy falls to the threshold after 1.5 × the duration (as Reanimated does), and each
/// frame moves along the exact solution from where the last one left it.
struct TruchetWaveTilesSpring {
    private(set) var value: Double
    private(set) var velocity = 0.0
    private(set) var target: Double
    private var zeta = 1.0
    private var omega0 = 0.0
    private var omega1 = 0.0
    private var stiffness = 0.0
    private var initialEnergy = 0.0
    private var moving = false
    private static let threshold = 6e-9

    init(_ value: Double) {
        self.value = value
        target = value
    }

    /// Springs from where it is, keeping its velocity, as a new `withSpring` does.
    mutating func spring(to next: Double, duration: Double, dampingRatio: Double) {
        target = next
        let v = moving ? velocity : 0
        let x0 = value - next
        zeta = dampingRatio
        let settling = duration * 1.5
        func energy(_ x: Double, _ v: Double, _ k: Double) -> Double { 0.5 * k * x * x + 0.5 * v * v }
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * dampingRatio
            let a = x0 + (v + x0 * w) * settling
            let xt = a * exp(-w * settling)
            let vt = a * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
            return energy(xt, vt, k) / energy(x0, v, k) - Self.threshold
        }
        var low = Double.ulpOfOne
        var high = 8e3
        let rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while abs(difference(k)) > Self.threshold * 1e-3 && left > 0 {
            left -= 1
            if (difference(k) < 0) == rising { low = k } else { high = k }
            k = (low + high) / 2
        }
        if !k.isFinite { k = 170 }
        stiffness = k
        omega0 = sqrt(k)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        velocity = v
        initialEnergy = energy(x0, v, k)
        moving = initialEnergy > 0
        if !moving { value = next }
    }

    mutating func step(_ dt: Double) {
        guard moving else { return }
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
        if now / initialEnergy <= Self.threshold {
            value = target
            velocity = 0
            moving = false
        }
    }
}

/// A value moving to a target over a fixed time on a curve, like `withTiming`.
struct TruchetWaveTilesTiming {
    private(set) var value: Double
    private var from: Double
    private var to: Double
    private var t = 1.0
    private var duration = 1.0
    private var curve: (Double) -> Double = { $0 }

    init(_ value: Double) {
        self.value = value
        from = value
        to = value
    }

    mutating func time(to next: Double, duration: Double, curve: @escaping (Double) -> Double) {
        from = value
        to = next
        t = 0
        self.duration = duration
        self.curve = curve
    }

    mutating func step(_ dt: Double) {
        guard t < 1 else { return }
        t = min(1, t + dt / duration)
        value = from + (to - from) * curve(t)
    }
}

struct TruchetWaveTilesWave {
    var x: Double
    var y: Double
    var at: Double
    var hit: [Bool]
}

/// The grid's state, stepped once a frame like the React Native frame callback.
final class TruchetWaveTilesEngine {
    private typealias M = TruchetWaveTilesMetrics

    // Per tile: its angle in degrees, its angular velocity, and the quarter turns it is heading to.
    private(set) var angle: [Double] = []
    private var velocity: [Double] = []
    private(set) var turns: [Int] = []
    private(set) var waves: [TruchetWaveTilesWave] = []
    private(set) var elapsed = 0.0
    private var lastDate: Date?

    private var morph = TruchetWaveTilesSpring(0)
    // Under reduced motion the style moves on a 0.4 s timing instead.
    private var morphTiming = TruchetWaveTilesTiming(0)
    private var morphTimed = false
    /// The style the tiles show: 0 arcs … 2 triangles.
    var morphValue: Double { morphTimed ? morphTiming.value : morph.value }
    private(set) var thumb = TruchetWaveTilesSpring(0)
    private(set) var palette = TruchetWaveTilesTiming(0)
    /// The style whose label is shown as chosen.
    var selected = 0

    private var clock = 0.0
    private var hold = 0.0
    private var lastAuto: Bool?

    // The grid's geometry.
    private(set) var side = 1.0
    private(set) var rows = 0
    private(set) var top = 0.0
    private(set) var size = CGSize.zero
    var count: Int { M.columns * rows }

    // The touch under way, and where it started (to tell a new drag from the one before).
    private var touchStart: Date?
    private var touchAt: CGPoint?
    private var panning = false
    private var lastTile = -1
    private var last = CGPoint.zero

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let selection = UISelectionFeedbackGenerator()

    func step(to date: Date, size: CGSize, autoPlay: Bool, reducedMotion: Bool) {
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.05)
        if lastAuto != autoPlay {
            lastAuto = autoPlay
            clock = 0
        }
        guard size.width > 0, dt > 0 else { return }
        if size != self.size {
            self.size = size
            side = size.width / Double(M.columns)
            rows = Int(ceil(size.height / side)) + 1
            top = (size.height - Double(rows) * side) / 2
        }
        if angle.count != count {
            // A fresh grid in the opening pattern.
            turns = (0..<count).map { M.opening[$0 % M.opening.count] }
            angle = turns.map { Double($0) * 90 }
            velocity = Array(repeating: 0, count: count)
            waves = []
            return
        }
        let now = elapsed + dt
        elapsed = now
        morph.step(dt)
        morphTiming.step(dt)
        thumb.step(dt)
        palette.step(dt)

        if hold > 0 {
            hold -= dt
            if hold <= 0 { clock = 0 }
        } else if autoPlay {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            func passed(_ at: Double) -> Bool { (before < at && t >= at) || (t < before && (at > before || at <= t)) }
            for k in M.wavesAt.indices where passed(M.wavesAt[k]) {
                sendWave(x: M.waveFrom[k].x * size.width, y: M.waveFrom[k].y * size.height)
            }
            for k in M.styleAt.indices where passed(M.styleAt[k]) { chooseStyle(M.styleTo[k], reducedMotion: reducedMotion) }
            for k in M.paletteAt.indices where passed(M.paletteAt[k]) { nextPalette() }
        }

        // Wave fronts: every tile the front has reached since last frame starts its turn.
        if !waves.isEmpty {
            let far = hypot(size.width, size.height) + side
            var keep: [TruchetWaveTilesWave] = []
            for var w in waves {
                let reach = (now - w.at) * M.waveSpeed
                for i in 0..<min(count, w.hit.count) where !w.hit[i] {
                    let cx = (Double(i % M.columns) + 0.5) * side
                    let cy = top + (Double(i / M.columns) + 0.5) * side
                    if hypot(cx - w.x, cy - w.y) <= reach {
                        w.hit[i] = true
                        flip(i, reducedMotion: reducedMotion)
                    }
                }
                if reach < far || now - w.at < M.ringLife { keep.append(w) }
            }
            waves = keep
        }

        // Springs towards each tile's quarter turn; a settled tile has its turns folded back to 0–3.
        if !reducedMotion {
            let steps = max(1, Int(ceil(dt / M.substep)))
            let h = dt / Double(steps)
            let w2 = M.omega * M.omega
            let c = 2 * M.damping * M.omega
            for i in 0..<count {
                let target = Double(turns[i]) * 90
                var a = angle[i]
                var v = velocity[i]
                if abs(target - a) < 0.01 && abs(v) < 0.05 {
                    if turns[i] >= 4 {
                        turns[i] -= 4
                        angle[i] = Double(turns[i]) * 90
                    }
                    continue
                }
                for _ in 0..<steps {
                    v += (-w2 * (a - target) - c * v) * h
                    a += v * h
                }
                if abs(target - a) < 0.01 && abs(v) < 0.05 {
                    a = target
                    v = 0
                }
                angle[i] = a
                velocity[i] = v
            }
        }
    }

    /// Starts a tile on its next quarter turn.
    private func flip(_ i: Int, reducedMotion: Bool) {
        guard i >= 0, i < angle.count, i < turns.count, i < velocity.count else { return }
        if reducedMotion {
            // No turning: it snaps a quarter on, its turns kept within 0–3.
            turns[i] = (turns[i] + 1) % 4
            angle[i] = Double(turns[i]) * 90
            velocity[i] = 0
            return
        }
        // At most a full turn queued: rapid taps or a scribble over one tile never wind it up further.
        if Double(turns[i]) * 90 - angle[i] >= 360 { return }
        turns[i] += 1
    }

    private func sendWave(x: Double, y: Double) {
        var list = Array(waves.suffix(M.maxWaves - 1))
        list.append(TruchetWaveTilesWave(x: x, y: y, at: elapsed, hit: Array(repeating: false, count: count)))
        waves = list
    }

    func chooseStyle(_ i: Int, reducedMotion: Bool) {
        thumb.spring(to: Double(i), duration: M.thumbSpring.duration, dampingRatio: M.thumbSpring.dampingRatio)
        if reducedMotion {
            // Reanimated's default timing curve, inOut(quad).
            morphTiming = TruchetWaveTilesTiming(morphValue)
            morphTiming.time(to: Double(i), duration: 0.4) { t in t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2 }
            morphTimed = true
        } else {
            if morphTimed {
                morph = TruchetWaveTilesSpring(morphValue)
                morphTimed = false
            }
            morph.spring(to: Double(i), duration: M.styleSpring.duration, dampingRatio: M.styleSpring.dampingRatio)
        }
        selected = i
    }

    func nextPalette() {
        palette.time(to: floor(palette.value + 0.5) + 1, duration: M.paletteTime) { M.easeInOut.value(at: $0) }
    }

    private func tileAt(_ x: Double, _ y: Double) -> Int {
        let c = Int(floor(x / side))
        let r = Int(floor((y - top) / side))
        if c < 0 || c >= M.columns || r < 0 || r >= rows { return -1 }
        return r * M.columns + c
    }

    // MARK: Touches

    /// The segmented control and the swatch: pressed in, they act at once.
    func pick(_ i: Int, reducedMotion: Bool) {
        guard i != selected else { return }
        hold = M.userHold
        chooseStyle(i, reducedMotion: reducedMotion)
        selection.selectionChanged()
    }

    func cycle() {
        hold = M.userHold
        nextPalette()
        selection.selectionChanged()
    }

    /// A pan after 4 pt paints a trail of flips; a touch lifted within 0.5 s that never went that
    /// far is a tap, which sends a ring wave from where it landed.
    func touchChanged(_ value: DragGesture.Value, reducedMotion: Bool) {
        // A new drag while the last one never reported its end: forget that one first.
        if let at = touchAt, at != value.startLocation { cancelNow() }
        if touchStart == nil {
            touchStart = value.time
            touchAt = value.startLocation
            panning = false
        }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= 4 else { return }
            panning = true
            hold = M.userHold
            last = value.startLocation
            lastTile = tileAt(last.x, last.y)
            flip(lastTile, reducedMotion: reducedMotion)
            light.impactOccurred()
        }
        hold = M.userHold
        // Walk from the last point to this one in half-tile steps, so a fast stroke leaves no gaps.
        let dx = value.location.x - last.x
        let dy = value.location.y - last.y
        let steps = max(1, Int(ceil(hypot(dx, dy) / (side * 0.5))))
        for k in 1...steps {
            let i = tileAt(last.x + dx * Double(k) / Double(steps), last.y + dy * Double(k) / Double(steps))
            if i != lastTile {
                lastTile = i
                flip(i, reducedMotion: reducedMotion)
            }
        }
        last = value.location
    }

    /// The drag may have been cancelled without an end. Checked a moment later, once a normal
    /// `onEnded` has surely run: if the same drag is still open, it is forgotten (no tap, no trail
    /// from where it was to the next touch).
    func touchCancelled() {
        guard let start = touchStart else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self, self.touchStart == start else { return }
            self.cancelNow()
        }
    }

    private func cancelNow() {
        touchStart = nil
        touchAt = nil
        panning = false
        lastTile = -1
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            touchStart = nil
            touchAt = nil
            panning = false
        }
        guard !panning, let start = touchStart, value.time.timeIntervalSince(start) <= 0.5,
              hypot(value.translation.width, value.translation.height) < 10 else { return }
        hold = M.userHold
        sendWave(x: value.location.x, y: value.location.y)
        light.impactOccurred()
    }
}
