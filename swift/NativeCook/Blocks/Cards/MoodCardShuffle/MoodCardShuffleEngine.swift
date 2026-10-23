import SwiftUI
import UIKit

enum MoodCardShuffleMetrics {
    static let count = 4

    // Card photos are drawn about 270 pt wide (810 px at 3×).
    static let cardPhotoPx = 800
    // The backdrop is the photo blurred once into a small `backdropW` × `backdropH` px image (sigma
    // `backdropBlur` px there), drawn stretched over the screen with cubic sampling.
    static let backdropW = 120
    static let backdropH = 260
    static let backdropBlur = 9.0
    static let backdropSaturation = 0.72
    // Seconds the backdrop takes to cross-fade when a reshuffle changes the front photo.
    static let backdropFade = 0.52
    // Seconds a photo takes to fade in once it has loaded.
    static let imageFade = 0.35

    // The front card is `cardShare` of the screen's width (at most `cardMax` pt), `cardAspect` as tall
    // as it is wide, with `cardRadius` pt corners. Each card has its own vanishing point
    // `perspective` pt away.
    static let cardShare = 0.68
    static let cardMax = 300.0
    static let cardAspect = 0.84
    static let cardRadius = 26.0
    static let perspective = 900.0

    // The deck is a drum seen from the front. A card's place is its signed distance s from the front
    // (in cards, −2 … 2): s 0 in front, 1 peeking out on the right, −1 on the left, ±2 at the back.
    static let peekX = 0.25
    static let depthScale = 0.3
    static let depthDrop = 18.0
    static let peekTurn = 16.0
    static let peekTilt = 1.6
    static let depthDim = 0.62
    // The card leaving the front swings out the way the deck turns, then tucks in as the peek.
    static let swingX = 0.36
    static let swingTurn = 26.0
    static let swingLift = 14.0
    static let swingDip = 0.08
    static let headingEase = 0.12
    // When two cards trade places in the drawing order, the one going under is drawn once more on
    // top, fading out over `ghostBand` of their depth difference.
    static let ghostBand = 0.3
    // The chip, bookmark and text show only on the front card: gone by |s| = 1 / `textFade`.
    static let textFade = 3.0
    // The region line's letters sit on their glyph advances plus this much.
    static let regionTracking = 0.7
    static let shadowBlur = 16.0
    static let shadowDrop = 16.0
    static let shadowAlpha = 0.34

    // Reshuffling to a new mood: the cards gather into a riffled pile, turn edge-on together (their
    // faces change while nobody can see them), turn back and deal out.
    static let shuffleTime = 1.15
    static let reducedShuffleTime = 0.6
    static let gatherX = [0, 0.12, -0.12, 0.06]
    static let gatherY = [0.0, -10, -20, -30]
    static let gatherScale = [0.9, 0.87, 0.84, 0.81]
    static let gatherTilt = [0, 7, -7, 3.5]
    // Before a reshuffle the deck settles on the nearest card over this long.
    static let settleTime = 0.2

    // Finger travel that turns the deck by one card, as a share of the screen's width.
    static let dragSpan = 0.72
    // A release commits when the position projected `throwTime` s ahead passes half a card.
    static let throwTime = 0.18
    // Past one card in either direction the deck gives only `rubber` of the finger's travel.
    static let rubber = 0.2
    // After a drag or a tap the deck lands on this spring, carrying the finger's speed.
    static let deckMass = 1.0
    static let deckStiffness = 90.0
    static let deckDamping = 17.0
    // The pan starts after 12 pt of sideways travel and fails after 16 pt of vertical travel; a tap
    // is a touch shorter than 0.32 s.
    static let panSlop = 12.0
    static let panFail = 16.0
    static let tapMaxDuration = 0.32
    // autoPlay turns the deck over `advanceTime` s on an ease-in-out curve.
    static let advanceTime = 1.15
    static let reducedAdvanceTime = 0.32
    static let advanceEase = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.5, y: 0),
        endControlPoint: UnitPoint(x: 0.25, y: 1)
    )
    // The bookmark pops to `bookmarkPop` × over 0.11 s, then settles on a damped wobble.
    static let bookmarkPop = 1.28
    static let pillDuration = 0.42
    static let pillDampingRatio = 0.82

    // autoPlay (seconds): two cards, a reshuffle to Nature, a card, a reshuffle to Wild, two cards,
    // back to Quiet and a rest. The last reshuffle deals the first place onto the front card, so one
    // period ends looking exactly as it started.
    static let loop = 14.0
    static let cues: [MoodCardShuffleCue] = [
        .init(at: 1.0, next: true),
        .init(at: 2.4, next: true),
        .init(at: 3.8, mood: 1),
        .init(at: 5.6, next: true),
        .init(at: 7.0, mood: 3),
        .init(at: 8.8, next: true),
        .init(at: 10.2, next: true),
        .init(at: 11.6, mood: 0),
    ]
    // A touch pauses autoPlay for this long; then the loop starts over.
    static let userHold = 4.0

    /// The front card's size for a screen `width` pt wide (whole points, like the RN layout).
    static func cardSize(_ width: Double) -> CGSize {
        guard width.isFinite, width > 0 else { return .zero }
        let w = min(cardMax, MoodCardShuffleMath.jsRound(width * cardShare))
        return CGSize(width: w, height: MoodCardShuffleMath.jsRound(w * cardAspect))
    }
}

struct MoodCardShuffleCue {
    var at: Double
    var next = false
    var mood: Int? = nil
}

/// Where and how a card is drawn.
struct MoodCardShufflePose {
    var x: Double
    var y: Double
    var scale: Double
    var turn: Double
    var tilt: Double
    var dim: Double
    var text: Double
    var alpha: Double
    var key: Double
}

// MARK: - The deck's geometry

enum MoodCardShuffleMath {
    private typealias M = MoodCardShuffleMetrics

    static func clamp01(_ x: Double) -> Double { x.isNaN ? 0 : min(1, max(0, x)) }

    static func smooth(_ x: Double) -> Double {
        let t = clamp01(x)
        return t * t * (3 - 2 * t)
    }

    /// JavaScript's Math.round: halves go up.
    static func jsRound(_ x: Double) -> Double { floor(x + 0.5) }

    /// A whole number of cards wrapped into 0 ..< count.
    static func wrap(_ k: Double) -> Int {
        guard k.isFinite else { return 0 }
        let n = Double(M.count)
        let r = k.truncatingRemainder(dividingBy: n)
        return Int(r < 0 ? r + n : r) % M.count
    }

    static func inOutQuad(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t)
    }

    /// Linear interpolation through `values` at whole steps 0, 1, 2 … of `x`, wrapping at the end.
    static func around(_ values: [Double], _ x: Double) -> Double {
        let n = values.count
        guard n > 0, x.isFinite else { return 0 }
        let nn = Double(n)
        let at = (x.truncatingRemainder(dividingBy: nn) + nn).truncatingRemainder(dividingBy: nn)
        let i = min(n - 1, max(0, Int(floor(at))))
        let f = at - Double(i)
        return values[i] + (values[(i + 1) % n] - values[i]) * f
    }

    /// A card's signed distance from the front, in (−2, 2].
    static func signedOf(_ index: Int, _ progress: Double) -> Double {
        let n = Double(M.count)
        var s = (Double(index) - progress).truncatingRemainder(dividingBy: n)
        if s <= -2 { s += n }
        if s > 2 { s -= n }
        return s
    }

    /// 0 at rest, 1 for the riffled pile in the middle of a reshuffle.
    static func gatherOf(_ shuffle: Double) -> Double {
        smooth(shuffle / 0.36) * (1 - smooth((shuffle - 0.64) / 0.36))
    }

    /// Extra turn of the whole pile during a reshuffle: edge-on from 44% to 56%.
    static func shuffleTurnOf(_ shuffle: Double) -> Double {
        90 * smooth((shuffle - 0.2) / 0.24) * (1 - smooth((shuffle - 0.56) / 0.24))
    }

    /// Where card `index` is drawn, from the deck's progress and the reshuffle.
    static func poseOf(_ index: Int, progress: Double, heading: Double, shuffle: Double, reduced: Bool, cardW: Double) -> MoodCardShufflePose {
        let s = signedOf(index, progress)
        let a = abs(s)
        let side: Double = s < 0 ? -1 : 1
        let angle = s * .pi / 2
        let depth = (1 - cos(angle)) / 2
        // Only the card going out on the side the deck turns towards swings (heading +1: to the left).
        let leaving = clamp01((1 - side * heading) / 2)
        let swing = !reduced && a < 1 ? sin(.pi * a) * leaving : 0
        var x = sin(angle) * M.peekX * cardW + side * M.swingX * cardW * swing
        var y = M.depthDrop * depth - M.swingLift * swing
        var scale = (1 - M.depthScale * depth) * (1 - M.swingDip * swing)
        var turn = sin(angle) * M.peekTurn + side * M.swingTurn * swing
        var tilt = sin(angle) * M.peekTilt
        var alpha = 1.0
        // Upper cards draw later: the closer to the front, the higher.
        var key = -a
        let g = reduced ? 0 : gatherOf(shuffle)
        if g > 0 {
            // The pile, by each card's rank from the front: 0 front, 1 right, 2 back, 3 left.
            let d = s < 0 ? s + Double(M.count) : s
            x += (around(M.gatherX, d) * cardW - x) * g
            y += (around(M.gatherY, d) - y) * g
            scale += (around(M.gatherScale, d) - scale) * g
            tilt += (around(M.gatherTilt, d) - tilt) * g
            turn -= turn * g
            key += (-d - key) * g
        }
        turn += reduced ? 0 : shuffleTurnOf(shuffle)
        // Reduced motion: the deck dips out and back in while a reshuffle changes its faces.
        if reduced && shuffle > 0 { alpha = 1 - sin(.pi * clamp01(shuffle)) }
        return MoodCardShufflePose(
            x: x, y: y, scale: scale, turn: turn, tilt: tilt,
            dim: M.depthDim * depth + 0.1 * swing,
            text: clamp01(1 - a * M.textFade) * (1 - g),
            alpha: alpha,
            key: key
        )
    }

    /// The 3 × 3 matrix (row-major, column vectors) that draws a card, in its own coordinates centred
    /// on its middle, at a pose: scaled, tilted in the picture plane, turned about its vertical axis
    /// and seen in perspective.
    static func cardMatrix(_ p: MoodCardShufflePose, cx: Double, cy: Double) -> [Double] {
        let t = p.turn * .pi / 180
        let r = p.tilt * .pi / 180
        let ct = cos(t)
        let st = sin(t)
        let a = p.scale * cos(r)
        let b = p.scale * sin(r)
        let tx = cx + p.x
        let ty = cy + p.y
        // Local (u, v) → (a·u − b·v, b·u + a·v); then x = X·cos t, depth = X·sin t (away from the eye).
        let q0 = ct + tx * st / M.perspective
        let q1 = ty * st / M.perspective
        let q2 = st / M.perspective
        return [q0 * a, -q0 * b, tx, q1 * a + b, -q1 * b + a, ty, q2 * a, -q2 * b, 1]
    }

    /// The same matrix for a view whose top-left corner sits at the stage's origin and whose middle
    /// is `(ox, oy)` in its own coordinates, as SwiftUI wants it (row vectors).
    static func projection(_ p: MoodCardShufflePose, cx: Double, cy: Double, ox: Double, oy: Double) -> ProjectionTransform {
        let m = cardMatrix(p, cx: cx, cy: cy)
        // M · T(−ox, −oy)
        let c = m[2] - m[0] * ox - m[1] * oy
        let f = m[5] - m[3] * ox - m[4] * oy
        let i = m[8] - m[6] * ox - m[7] * oy
        var out = ProjectionTransform()
        out.m11 = m[0]
        out.m21 = m[1]
        out.m31 = c
        out.m12 = m[3]
        out.m22 = m[4]
        out.m32 = f
        out.m13 = m[6]
        out.m23 = m[7]
        out.m33 = i
        return out
    }

    /// The bookmark's size `age` s after a tap: up to 1.28× over 0.11 s, then a damped wobble.
    static func pop(age: Double) -> Double {
        let extra = M.bookmarkPop - 1
        if age < 0.11 { return 1 + extra * smooth(age / 0.11) }
        return 1 + extra * exp(-(age - 0.11) * 7) * cos((age - 0.11) * 18)
    }
}

// MARK: - Springs

/// Reanimated's `withSpring` arithmetic.
enum MoodCardShuffleSpring {
    static let energyThreshold = 6e-9

    /// The stiffness Reanimated solves for a spring of mass 1 given a duration and a damping ratio,
    /// starting from rest: its energy falls to the threshold after 1.5 × the duration.
    static func stiffness(duration: Double, dampingRatio zeta: Double) -> Double {
        let x0 = 1.0
        let v = 0.0
        let settling = duration * 1.5
        func energy(_ x: Double, _ v: Double, _ k: Double) -> Double { 0.5 * k * x * x + 0.5 * v * v }
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * zeta
            let xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
            let vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
            return energy(xt, vt, k) / energy(x0, v, k) - energyThreshold
        }
        var low = Double.ulpOfOne
        var high = 8e3
        let rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while abs(difference(k)) > energyThreshold * 1e-3 && left > 0 {
            left -= 1
            if (difference(k) < 0) == rising { low = k } else { high = k }
            k = (low + high) / 2
        }
        return k.isFinite && k > 0 ? k : 170
    }

    /// Moves a spring on by `t` s along its exact solution: the displacement from its target and its
    /// speed.
    static func step(x0: Double, v0: Double, stiffness k: Double, damping c: Double, mass m: Double, dt t: Double) -> (x: Double, v: Double) {
        let omega0 = sqrt(k / m)
        let zeta = c / (2 * sqrt(k * m))
        if zeta < 1 {
            let omega1 = omega0 * sqrt(1 - zeta * zeta)
            let sin1 = sin(omega1 * t)
            let cos1 = cos(omega1 * t)
            let envelope = exp(-zeta * omega0 * t)
            let frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            let v = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
            return (frag, v)
        }
        let envelope = exp(-omega0 * t)
        let x = envelope * (x0 + (v0 + omega0 * x0) * t)
        let v = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
        return (x, v)
    }
}

// MARK: - The engine

/// A place as the deck draws it: the blurb already broken into at most two lines and the region's
/// letters already spaced.
struct MoodCardShuffleFace {
    var name: String
    var region: String
    var photo: String
    var lines: [String]
    /// Each letter of the region and its x from the line's start.
    var regionLetters: [(String, Double)]
    /// The "Curated" chip's width.
    var chipWidth: Double
}

/// A photo once it has arrived: the card's copy, its blurred backdrop and the engine's clock then.
struct MoodCardShuffleLoaded {
    var image: UIImage
    var blur: UIImage?
    var at: Double
}

/// One card to draw.
struct MoodCardShuffleItem {
    var face: MoodCardShuffleFace
    var loaded: MoodCardShuffleLoaded?
    var pose: MoodCardShufflePose
    var alpha: Double
    var shadow: Bool
    var saved: Bool
    var pop: Double
}

/// Everything one frame draws.
struct MoodCardShuffleFrame {
    var now = 0.0
    var cx = 0.0
    var cy = 0.0
    var cardSize = CGSize.zero
    /// Blurred photos for the backdrop, bottom first, and their alpha.
    var backdrops: [(UIImage, Double)] = []
    /// Seven draw slots in order: card, card, ghost, card, ghost, card, ghost.
    var items: [MoodCardShuffleItem?] = Array(repeating: nil, count: 7)
}

/// The deck's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class MoodCardShuffleEngine {
    private typealias M = MoodCardShuffleMetrics
    private typealias D = MoodCardShuffleMath

    private enum Curve {
        case inOutQuad
        case advance
        case linear

        func value(_ t: Double) -> Double {
            switch self {
            case .inOutQuad: D.inOutQuad(t)
            case .advance: M.advanceEase.value(at: t)
            case .linear: t
            }
        }
    }

    /// A running animation of a value, like a Reanimated animation object.
    private enum Motion {
        case timing(from: Double, to: Double, start: Double, duration: Double, curve: Curve, wrapOnDone: Bool)
        case spring(target: Double, velocity: Double, startX0: Double, initialEnergy: Double, wrapOnDone: Bool)
    }

    // Deck position in cards (grows by one per card turned to the left).
    private(set) var progress = 0.0
    private var motion: Motion?
    // +1 while the deck turns on (front card out to the left), −1 while it turns back; eased.
    private(set) var heading = 1.0
    private var lastProgress = 0.0
    private var grab = 0.0
    private var dragging = false
    // 0 … 1 through a reshuffle; `pending` is the mood it deals. The deal: the mood on the cards and
    // the slot that shows its first place, so a reshuffle keeps the front card in front.
    private(set) var shuffle = 0.0
    private var shuffleStart = 0.0
    private var shuffleDuration = M.shuffleTime
    private(set) var shuffling = false
    private var pending = 0
    private(set) var current = 0
    private(set) var dealMood = 0
    private(set) var dealOffset = 0
    private var lastShuffle = 0.0
    // The backdrop that fades out after a reshuffle, and when it began to.
    private var fadePhoto = ""
    private var fadeAt = -10.0
    // Saved places by name, and the last bookmark pop.
    private(set) var saved: Set<String> = []
    private var popName = ""
    private var popAt = -10.0
    private(set) var elapsed = 0.0
    private var clock = 0.0
    private var hold = 0.0
    private var lastAuto: Bool?
    private var lastDate: Date?

    // What the view tells the engine.
    var faces: [[MoodCardShuffleFace]] = []
    var facesKey = ""
    var reduced = false
    var autoPlay = false
    var stageSize = CGSize.zero
    var deckFrame = CGRect.zero

    /// Called on the next turn of the main loop when a mood is chosen (index, by the user).
    var onAnnounce: ((Int, Bool) -> Void)?

    // The touch under way.
    private var touchStart: Date?
    private var touchMode = 0 // 0 undecided, 1 pan, 2 pan failed (a tap may still land)

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let selection = UISelectionFeedbackGenerator()

    private var cardSize: CGSize { M.cardSize(Double(stageSize.width)) }
    private var span: Double { max(1, Double(stageSize.width) * M.dragSpan) }
    private var cx: Double { Double(stageSize.width) / 2 }
    private var cy: Double { Double(deckFrame.midY) }

    // MARK: Frame

    /// Steps the deck to `date` and returns what to draw.
    func frame(at date: Date, photos: [String: MoodCardShuffleLoaded]) -> MoodCardShuffleFrame {
        if lastAuto != autoPlay {
            lastAuto = autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(max(raw, 0), 0.064)
        if dt > 0 {
            elapsed += dt
            stepMotion(dt: dt)
            stepShuffle()
            frameCallback(dt: dt)
        }
        return draw(photos: photos)
    }

    private func stepMotion(dt: Double) {
        guard let m = motion else { return }
        switch m {
        case let .timing(from, to, start, duration, curve, wrapOnDone):
            let runtime = elapsed - start
            if runtime >= duration {
                progress = to
                motion = nil
                if wrapOnDone { wrapProgress() }
            } else {
                progress = from + (to - from) * curve.value(D.clamp01(runtime / duration))
            }
        case let .spring(target, velocity, startX0, initialEnergy, wrapOnDone):
            let next = MoodCardShuffleSpring.step(
                x0: progress - target, v0: velocity,
                stiffness: M.deckStiffness, damping: M.deckDamping, mass: M.deckMass, dt: dt
            )
            progress = target + next.x
            let energy = 0.5 * M.deckStiffness * next.x * next.x + 0.5 * M.deckMass * next.v * next.v
            if initialEnergy == 0 || energy / initialEnergy <= MoodCardShuffleSpring.energyThreshold || !progress.isFinite {
                progress = target
                motion = nil
                if wrapOnDone { wrapProgress() }
            } else {
                motion = .spring(target: target, velocity: next.v, startX0: startX0, initialEnergy: initialEnergy, wrapOnDone: wrapOnDone)
            }
        }
    }

    /// Whole turns of the deck change nothing on screen, so the numbers are kept small once it lands.
    private func wrapProgress() {
        let n = Double(M.count)
        progress = (progress.truncatingRemainder(dividingBy: n) + n).truncatingRemainder(dividingBy: n)
    }

    private func stepShuffle() {
        guard shuffling else { return }
        if elapsed - shuffleStart >= shuffleDuration {
            shuffle = 0
            shuffling = false
        } else {
            shuffle = D.clamp01((elapsed - shuffleStart) / shuffleDuration)
        }
    }

    /// The RN frame callback.
    private func frameCallback(dt: Double) {
        let speed = (progress - lastProgress) / dt
        lastProgress = progress
        // A wrap of the numbers (a whole turn) is not a motion.
        if abs(speed) > 0.05 && abs(speed) < 40 {
            heading += ((speed > 0 ? 1 : -1) - heading) * (1 - exp(-dt / M.headingEase))
        }

        // Half way through a reshuffle, edge-on, the cards take the new mood's faces.
        let sh = shuffle
        if lastShuffle < 0.5 && sh >= 0.5 {
            let front = D.wrap(D.jsRound(progress))
            if let face = face(mood: dealMood, offset: dealOffset, slot: front) { fadePhoto = face.photo }
            fadeAt = elapsed
            dealMood = pending
            dealOffset = front
        }
        lastShuffle = sh

        if dragging { return }
        if hold > 0 {
            hold -= dt
            if hold <= 0 { clock = 0 }
            return
        }
        if !autoPlay { return }
        let before = clock
        let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
        clock = t
        for cue in M.cues {
            let at = cue.at
            let passed = (before < at && t >= at) || (t < before && (at > before || at <= t))
            if !passed { continue }
            if cue.next { advance(1, byUser: false) }
            if let mood = cue.mood { choose(mood, user: false) }
        }
    }

    // MARK: Animations

    private func startTiming(to target: Double, duration: Double, curve: Curve, wrapOnDone: Bool) {
        // Like Reanimated: a timing started over a running timing to the same value keeps its timeline.
        if case let .timing(from, to, start, _, _, _) = motion, to == target {
            motion = .timing(from: from, to: target, start: start, duration: duration, curve: curve, wrapOnDone: wrapOnDone)
            return
        }
        motion = .timing(from: progress, to: target, start: elapsed, duration: duration, curve: curve, wrapOnDone: wrapOnDone)
    }

    private func startSpring(to target: Double, velocity configVelocity: Double, wrapOnDone: Bool) {
        let k = M.deckStiffness
        var startX0 = progress - target
        var velocity: Double
        switch motion {
        case let .spring(previousTarget, previousVelocity, previousX0, _, _):
            if previousTarget == target {
                // Triggered twice: the running spring carries on.
                startX0 = previousX0
                velocity = previousVelocity
            } else {
                velocity = previousVelocity + configVelocity
            }
        case .timing:
            // A timing animation has no speed to hand on (Reanimated reads NaN, then 0).
            velocity = 0
        case nil:
            velocity = configVelocity
        }
        if !velocity.isFinite { velocity = 0 }
        if (target > progress && velocity < 0) || (target < progress && velocity > 0) { velocity = 0 }
        let initialEnergy = 0.5 * k * startX0 * startX0 + 0.5 * M.deckMass * configVelocity * configVelocity
        motion = .spring(target: target, velocity: velocity, startX0: startX0, initialEnergy: initialEnergy, wrapOnDone: wrapOnDone)
    }

    /// Turns the deck one card on: `step` 1 brings the right card forward, −1 the left one.
    func advance(_ step: Double, byUser: Bool) {
        if shuffling { return }
        let target = D.jsRound(progress) + step
        if byUser {
            startSpring(to: target, velocity: 0, wrapOnDone: true)
        } else {
            startTiming(to: target, duration: reduced ? M.reducedAdvanceTime : M.advanceTime, curve: .advance, wrapOnDone: true)
        }
    }

    /// Picks a mood: the chip pill moves at once and the deck riffles and deals it.
    func choose(_ next: Int, user: Bool) {
        if shuffling || next == current || next < 0 || next >= faces.count { return }
        current = next
        shuffling = true
        pending = next
        if user { selection.selectionChanged() }
        DispatchQueue.main.async { [weak self] in self?.onAnnounce?(next, user) }
        // The deck settles on the nearest card first, so the pile gathers from rest. Whatever moves it
        // is stopped first, so the settle always starts from where the deck is.
        motion = nil
        startTiming(to: D.jsRound(progress), duration: M.settleTime, curve: .inOutQuad, wrapOnDone: false)
        shuffle = 0
        shuffleStart = elapsed
        shuffleDuration = reduced ? M.reducedShuffleTime : M.shuffleTime
    }

    /// The chips and "Go Unexpected" were touched: autoPlay waits.
    func touched() {
        hold = M.userHold
    }

    /// Picks a random mood other than the chosen one.
    func surprise(chosen: Int) {
        let count = faces.count
        guard count > 1 else { return }
        var next = Int.random(in: 0..<(count - 1))
        if next >= chosen { next += 1 }
        choose(next, user: true)
    }

    // MARK: Gestures on the deck

    /// A touch on the deck moved (or began): `location` and `translation` in the stage's space.
    func touchChanged(translation: CGSize, time: Date) {
        if touchStart == nil {
            touchStart = time
            touchMode = 0
        }
        let dx = Double(translation.width)
        let dy = Double(translation.height)
        if touchMode == 0 {
            if abs(dy) >= M.panFail {
                touchMode = 2
            } else if abs(dx) >= M.panSlop {
                touchMode = 1
                panStart()
            }
        }
        if touchMode == 1 { panUpdate(translationX: dx) }
    }

    func touchEnded(location: CGPoint, translation: CGSize, velocity: CGSize, time: Date) {
        defer {
            touchStart = nil
            touchMode = 0
        }
        if touchStart == nil { touchChanged(translation: translation, time: time) }
        if touchMode == 1 {
            panUpdate(translationX: Double(translation.width))
            panEnd(velocityX: Double(velocity.width))
        } else if let start = touchStart, time.timeIntervalSince(start) <= M.tapMaxDuration {
            tap(at: location)
        }
    }

    /// The system took the touch away.
    func touchCancelled() {
        if touchMode == 1 { panEnd(velocityX: 0) }
        touchStart = nil
        touchMode = 0
    }

    private func panStart() {
        if shuffling { return }
        touched()
        motion = nil
        grab = progress
        dragging = true
    }

    private func panUpdate(translationX: Double) {
        guard dragging, translationX.isFinite else { return }
        // Dragging left turns the deck on, dragging right turns it back.
        let g = grab
        let base = D.jsRound(g)
        let raw = g - translationX / span
        let hi = base + 1
        let lo = base - 1
        progress = raw > hi ? hi + (raw - hi) * M.rubber : raw < lo ? lo + (raw - lo) * M.rubber : raw
    }

    private func panEnd(velocityX: Double) {
        guard dragging else { return }
        dragging = false
        touched()
        let base = D.jsRound(grab)
        let velocity = velocityX.isFinite ? -velocityX / span : 0
        let projected = progress + velocity * M.throwTime
        let target = max(base - 1, min(base + 1, D.jsRound(projected)))
        if target != base { light.impactOccurred() }
        startSpring(to: target, velocity: velocity, wrapOnDone: true)
    }

    /// Taps on the deck: the bookmark on the front card, or a peeking card to bring it forward.
    private func tap(at point: CGPoint) {
        if shuffling { return }
        touched()
        let card = cardSize
        guard card.width > 0 else { return }
        let x = Double(point.x)
        let y = Double(point.y)
        let right = cx + card.width / 2
        let topEdge = cy - card.height / 2
        let settled = abs(progress - D.jsRound(progress)) < 0.05
        if settled && x > right - 56 && x < right && y > topEdge && y < topEdge + 56 {
            let front = D.wrap(D.jsRound(progress))
            if let name = face(mood: dealMood, offset: dealOffset, slot: front)?.name {
                if saved.contains(name) { saved.remove(name) } else { saved.insert(name) }
                popName = name
                popAt = elapsed
            }
            light.impactOccurred()
            return
        }
        if x > right {
            light.impactOccurred()
            advance(1, byUser: true)
        } else if x < cx - card.width / 2 {
            light.impactOccurred()
            advance(-1, byUser: true)
        }
    }

    // MARK: Drawing

    /// The place card slot `slot` shows for a deal (mood, offset).
    private func face(mood: Int, offset: Int, slot: Int) -> MoodCardShuffleFace? {
        guard !faces.isEmpty else { return nil }
        let list = faces[min(faces.count - 1, max(0, mood))]
        guard !list.isEmpty else { return nil }
        let i = ((slot - offset) % M.count + M.count) % M.count
        return list[i % list.count]
    }

    private func draw(photos: [String: MoodCardShuffleLoaded]) -> MoodCardShuffleFrame {
        var out = MoodCardShuffleFrame()
        let now = elapsed
        out.now = now
        let card = cardSize
        out.cardSize = card
        out.cx = cx
        out.cy = cy
        guard card.width > 0, stageSize.height > 0, !faces.isEmpty else { return out }
        let p = progress
        let sh = shuffle

        // The backdrop: the front card's photo, blurred, cross-fading with the card arriving.
        var near = 0
        var second = 1
        var nearA = 9.0
        var secondA = 9.0
        for i in 0..<M.count {
            let a = abs(D.signedOf(i, p))
            if a < nearA {
                second = near
                secondA = nearA
                near = i
                nearA = a
            } else if a < secondA {
                second = i
                secondA = a
            }
        }
        if let f = face(mood: dealMood, offset: dealOffset, slot: near), let photo = photos[f.photo], let blur = photo.blur {
            let alpha = D.clamp01((now - photo.at) / M.imageFade)
            if alpha > 0.002 { out.backdrops.append((blur, alpha)) }
        }
        let mix = nearA + secondA > 0 ? nearA / (nearA + secondA) : 0
        if mix > 0.002, let f = face(mood: dealMood, offset: dealOffset, slot: second), let blur = photos[f.photo]?.blur {
            out.backdrops.append((blur, D.clamp01(mix)))
        }
        let fade = 1 - (now - fadeAt) / M.backdropFade
        if fade > 0, let blur = photos[fadePhoto]?.blur {
            let alpha = D.smooth(fade)
            if alpha > 0.002 { out.backdrops.append((blur, alpha)) }
        }

        // The deck, back to front. Where two neighbours in the order are about to trade places, the
        // lower one is drawn again on top, fading, so the trade is a cross-fade.
        let poses = (0..<M.count).map { D.poseOf($0, progress: p, heading: heading, shuffle: sh, reduced: reduced, cardW: card.width) }
        var order = Array(0..<M.count)
        for i in 1..<M.count {
            let v = order[i]
            var j = i - 1
            while j >= 0 && poses[order[j]].key > poses[v].key {
                order[j + 1] = order[j]
                j -= 1
            }
            order[j + 1] = v
        }
        let popAge = now - popAt
        var slot = 0
        for k in 0..<M.count {
            for pass in 0..<2 {
                if pass == 1 && k == 0 { continue }
                defer { slot += 1 }
                let index = pass == 0 ? order[k] : order[k - 1]
                let pose = poses[index]
                var ghost = 1.0
                if pass == 1 {
                    let gap = poses[order[k]].key - pose.key
                    if gap >= M.ghostBand { continue }
                    ghost = 0.5 * (1 - gap / M.ghostBand)
                }
                let alpha = pose.alpha * ghost
                if alpha <= 0.004 { continue }
                // Edge-on the card is a line nobody sees, and SwiftUI would drop a projection that
                // flat (drawing the card unprojected), so it is left out.
                if abs(cos(pose.turn * .pi / 180)) < 0.002 { continue }
                guard let f = face(mood: dealMood, offset: dealOffset, slot: index) else { continue }
                var pop = 1.0
                if f.name == popName && popAge < 0.8 && !reduced { pop = D.pop(age: popAge) }
                if slot < out.items.count {
                    out.items[slot] = MoodCardShuffleItem(
                        face: f, loaded: photos[f.photo], pose: pose, alpha: alpha,
                        shadow: pass == 0, saved: saved.contains(f.name), pop: pop
                    )
                }
            }
        }
        return out
    }
}
