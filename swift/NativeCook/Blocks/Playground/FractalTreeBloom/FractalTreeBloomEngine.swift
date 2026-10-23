import Foundation

/// The numbers of the block, as in the React Native version.
enum FractalTreeBloomTuning {
    // Growth, in levels of branches per second (the tree has maxDepth + 1 levels), and the rate the
    // tree draws back into its seed when replanted.
    static let growRate = 1.9
    static let recedeRate = 6.0
    // Blossoms open over bloomTime s once the tree is grown, each a little after the last.
    static let bloomTime = 1.3
    // One season to the next, s.
    static let seasonTime = 1.8
    // Dragging this far across bends the wind fully; past it the drag gives less and less.
    static let windDrag = 150.0
    static let windMax = 1.6
    // The mound: its height and spread (share of the width), above the bottom inset.
    static let moundHeight = 26.0
    static let moundSpread = 0.42
    static let groundAbove = 64.0

    static let tick = 1.0 / 120
    static let maxTicks = 4

    // autoPlay, s.
    static let loop = 17.4
    static let growAt = 0.4
    static let gustFrom = 5.8
    static let gustTo = 7.0
    static let summerAt = 7.4
    static let autumnAt = 9.6
    static let winterAt = 12.0
    static let replantAt = 15.0
    static let userHold = 4.0

    // What happened during a frame, for haptics and the season's name.
    static let eventTurned = 1
    static let eventPlanted = 2
    static let eventSeason = 4

    /// Whether a time `at` was passed going from `before` to `t` on a looping clock.
    static func crossed(_ before: Double, _ t: Double, _ at: Double) -> Bool {
        (before < at && t >= at) || (t < before && (at > before || at <= t))
    }
}

/// The frame loop of the React Native block (its `useFrameCallback`, script and gestures), step for
/// step: growth, bloom, the season, the wind and what falls, on one fixed tick.
final class FractalTreeBloomEngine {
    private typealias T = FractalTreeBloomTuning
    private typealias S = FractalTreeBloomSimTuning

    let reduced: Bool
    let seed: Int
    private(set) var tree: FractalTreeBloomTree?
    private(set) var weather: FractalTreeBloomWeather
    let particles = FractalTreeBloomParticles()
    private(set) var ground = FractalTreeBloomGround(base: 0, cx: 0, bump: 0, spread: 1)
    private(set) var rootX = 0.0
    private(set) var rootY = 0.0
    private(set) var width = 0.0

    private var acc = 0.0
    /// Growth (0 … maxDepth + 1), whether it is growing (1), drawing back (−1) or still (0), and the bloom.
    private(set) var growth: Double
    private(set) var growing = 0
    private(set) var bloom: Double
    /// The season, and its move from one to the next.
    private(set) var season = 0.0
    private var seasonFrom = 0.0
    private var seasonTo = 0.0
    private var seasonT = 1.0
    private(set) var shownSeason = 0
    /// The wind's drag.
    private var dragging = false

    private var clock = 0.0
    private var hold = 0.0
    private var looping: Bool

    /// What happened since the events were last read (`FractalTreeBloomTuning.event…` bits).
    var events = 0

    init(auto: Bool, reduced: Bool, seed: Int) {
        looping = auto
        self.reduced = reduced
        self.seed = seed
        growth = auto ? 0 : Double(S.maxDepth + 1)
        bloom = auto ? 0 : 2
        // Wrapping arithmetic: a huge seed must not trap on overflow.
        weather = FractalTreeBloomWeather(seed: seed &* 7 &+ 1)
    }

    /// Restarts the autoPlay clock (autoPlay switched on or off).
    func setAuto(_ auto: Bool) {
        looping = auto
        clock = 0
        hold = 0
    }

    /// Moves on to the next season.
    private func nextSeason() {
        let to = (seasonTo + 0.5).rounded(.down) + 1
        seasonFrom = season
        seasonTo = to
        seasonT = 0
    }

    /// Draws the tree back into its seed (it grows again from there) and turns the year to spring.
    private func replant() {
        growing = -1
        let to = (seasonTo / 4 - 1e-6).rounded(.up) * 4
        if to > seasonTo {
            seasonFrom = season
            seasonTo = to
            seasonT = 0
        }
    }

    /// One step of the autoPlay script, from `before` to `t` s into the loop.
    private func script(_ before: Double, _ t: Double) {
        if T.crossed(before, t, T.growAt) {
            growth = 0
            growing = 1
            bloom = 0
        }
        if t >= T.gustFrom && t < T.gustTo {
            // A finger draws the wind to the right, then lets go.
            let k = (t - T.gustFrom) / (T.gustTo - T.gustFrom)
            weather.target = 0.95 * sin(min(1, k * 1.6) * Double.pi * 0.5)
        }
        if T.crossed(before, t, T.gustTo) { weather.target = 0 }
        if T.crossed(before, t, T.summerAt) || T.crossed(before, t, T.autumnAt) || T.crossed(before, t, T.winterAt) {
            nextSeason()
        }
        if T.crossed(before, t, T.replantAt) { replant() }
    }

    /// One frame `frameDt` s after the last one, on a screen `width` × `height` pt with `bottomInset`.
    func frame(_ frameDt: Double, width: Double, height: Double, bottomInset: Double) {
        if width == 0 { return }
        let tree: FractalTreeBloomTree
        if let existing = self.tree {
            tree = existing
        } else {
            tree = FractalTreeBloomTree(seed: seed)
            let base = height - bottomInset - T.groundAbove
            ground = FractalTreeBloomGround(base: base, cx: width / 2, bump: T.moundHeight, spread: width * T.moundSpread)
            rootX = width / 2
            rootY = ground.y(width / 2) + 2
            self.width = width
            self.tree = tree
        }
        let maxGrowth = Double(S.maxDepth + 1)
        let dt = min(frameDt, Double(T.maxTicks) * T.tick)
        var pending = acc + dt
        var ticks = 0
        while pending >= T.tick && ticks < T.maxTicks {
            pending -= T.tick
            ticks += 1
            if hold > 0 {
                if !dragging { hold -= T.tick }
                if hold <= 0 {
                    // Pick the loop up where the tree is: still growing from a replant, at the same
                    // point of the growth; otherwise from the replant, drawing it back to grow it again.
                    if growing > 0 {
                        clock = T.growAt + 0.01 + growth / T.growRate
                    } else {
                        clock = T.replantAt - 0.01
                    }
                }
            } else if looping {
                let before = clock
                let t = (before + T.tick).truncatingRemainder(dividingBy: T.loop)
                clock = t
                script(before, t)
            }
            // Growth, level by level, and the bloom once it is grown.
            let g = growth
            if growing > 0 {
                let next = reduced ? maxGrowth : min(maxGrowth, g + T.growRate * T.tick)
                growth = next
                if next >= maxGrowth { growing = 0 }
            } else if growing < 0 {
                let next = reduced ? 0 : max(0, g - T.recedeRate * T.tick)
                growth = next
                if next <= 0 {
                    // Back in the seed: it rests a moment, then grows again (unless the script grows it).
                    growing = looping && hold <= 0 ? 0 : 2
                    particles.count = 0
                    bloom = 0
                }
            } else if growing == 2 {
                growing = 1
            }
            if growth >= Double(S.maxDepth) + 0.6 && growing >= 0 { bloom = min(2, bloom + T.tick / T.bloomTime) }
            // The season's move.
            if seasonT < 1 {
                let k = min(1, seasonT + T.tick / (reduced ? 0.6 : T.seasonTime))
                seasonT = k
                season = seasonFrom + (seasonTo - seasonFrom) * FractalTreeBloomEase.inOut(k)
                if k >= 1 && seasonTo >= 4 {
                    // Whole years change nothing: keep the numbers small.
                    let years = (seasonTo / 4).rounded(.down) * 4
                    seasonTo -= years
                    seasonFrom -= years
                    season -= years
                    for i in 0..<tree.sites { tree.siteSeen[i] = -1 }
                }
            }
            weather.step(T.tick)
            tree.layout(weather, x: rootX, y: rootY, growth: growth, sway: reduced ? 0 : 1)
            particles.step(
                tree: tree, weather: weather, season: season, dt: T.tick, width: width, ground: ground,
                falling: !reduced && growing >= 0
            )
        }
        acc = min(pending, T.tick)
        // Int(_:) traps on NaN, and % keeps the sign: always a valid index into the season names.
        let rounded = (season + 0.5).rounded(.down)
        let name = rounded.isFinite ? ((Int(rounded) % 4) + 4) % 4 : 0
        if name != shownSeason {
            shownSeason = name
            events |= T.eventSeason
        }
    }

    // MARK: Gestures

    private func touched() {
        hold = T.userHold
    }

    func windStart() {
        touched()
        dragging = true
    }

    /// The drag is `translationX` pt across.
    func windMove(translationX: Double) {
        guard tree != nil else { return }
        // Rubber-banded: the further the drag, the less each point adds.
        let raw = translationX / T.windDrag
        let sign: Double = raw < 0 ? -1 : 1
        let a = Swift.abs(raw)
        weather.target = sign * (a < 1 ? a : 1 + (T.windMax - 1) * (1 - exp(-(a - 1))))
    }

    /// Let go at `velocityX` pt/s: the wind springs back, carrying the finger's speed. A cancelled
    /// drag springs back too (Gesture Handler's `onEnd` also runs on cancel), only without the fling.
    func windEnd(velocityX: Double, cancelled: Bool = false) {
        if tree != nil {
            weather.target = 0
            if !cancelled && velocityX.isFinite { weather.windSpeed += (velocityX / T.windDrag) * 0.35 }
        }
        dragging = false
    }

    func tap() {
        touched()
        nextSeason()
        events |= T.eventTurned
    }

    func longPress() {
        touched()
        replant()
        events |= T.eventPlanted
    }
}
