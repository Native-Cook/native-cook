import SwiftUI
import UIKit

enum WordDrumMetrics {
    // A geometric sans close to the reference: Avenir Next.
    static let fontSize: CGFloat = 40
    static let wordFont = Font.custom("AvenirNext-Bold", fixedSize: fontSize)
    // The word's baseline sits this share of the size below the drum's axis, so the letters straddle it.
    static let baseline: CGFloat = 0.34

    // The words are the spokes of a large wheel lying flat on the screen, its hub far off to the
    // right. Each word sits on the rim, `spacing` apart, and turns with its spoke, so the words above
    // the front rise to the right and the words below fall away, each one a little further left.
    // Words are drawn up to `maxRows` away from the front.
    static let spacing = 60.0
    static let radius = 760.0
    static let angle = spacing / radius
    static let maxRows = 3.8
    // The word in front grows a little and steps right, away from the arrow.
    static let frontGrow = 0.07
    static let frontShift = 6.0
    // Left edge of the words and of the arrow, from the left of the screen.
    static let wordLeft = 72.0
    static let arrowLeft: CGFloat = 28
    static let arrowWidth: CGFloat = 26
    static let arrowHead: CGFloat = 9
    static let arrowStroke: CGFloat = 3.2
    // Each word that reaches the front kicks the arrow, and the word with it, to the right like a
    // ratchet's pawl: a push of `kickSpeed` pt/s into a critically damped spring that peaks about
    // 13 pt over 80 ms and settles back over 300 ms. A fast spin kicks it once per word, up to `kickMax`.
    static let kickSpeed = 430.0
    static let kickStiffness = 150.0
    static let kickDamping = 24.5
    static let kickMax = 18.0
    // Height of the screen, as a share, where the drum's axis sits.
    static let axisAt = 0.47

    // Blur grows with a word's distance from the front and with the drum's speed. The Skia mask
    // ignored the canvas's scale, so these are sigmas in device pixels, not points.
    static let blurPerRow = 1.4
    static let blurPerSpeed = 0.55
    static let blurMax = 9.0
    static let fadePerRow = 0.16
    static let speedRate = 10.0

    /// A Skia blur sigma in device pixels as a SwiftUI blur radius, which spreads about half as far
    /// per unit as a sigma does.
    static func blurRadius(_ sigma: Double, scale: CGFloat) -> CGFloat {
        sigma * 2 / max(scale, 1)
    }

    // Finger travel that turns the wheel by one word: the words follow the finger.
    static let dragStep = spacing
    static let dragSlop: CGFloat = 6
    static let tapMaxDuration = 0.4
    // The snap after a release: a spring on the drum's position, in words.
    static let mass = 1.0
    static let stiffness = 300.0
    static let damping = 32.0
    // Seconds of speed a release throws the drum ahead by: a hard flick spins it like a reel.
    static let throwTime = 0.3

    // Choosing a word: four stripes sweep across from the left, staggered, over `wipeTime`. The
    // drum and the chosen word swap while the first stripe covers the screen.
    static let wipeTime = 1.15
    static let stripeLag = 0.08
    static let stripeSpan = 0.56
    static let swapAt = stripeSpan / 2
    // Each stripe is this many screens wide, so neighbours always overlap and nothing shows between
    // them while they move fastest.
    static let stripeLength = 2.0
    static let easeInOut = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.77, y: 0),
        endControlPoint: UnitPoint(x: 0.175, y: 1)
    )
    static let revealRise = 26.0
    static let fadeTime = 0.22

    // autoPlay loop (seconds): five turns of one word, a word chosen, the drum again, a rest.
    static let loop = 10.0
    static let turns = [0.7, 1.55, 2.4, 3.25, 4.1]
    static let chooseAt = 5.0
    static let returnAt = 7.6
    // After a touch the loop waits this long before it starts over; longer after a word is chosen,
    // so it can be read.
    static let userHold = 3.0
    static let chosenHold = 5.0

    /// Where a stripe's left edge is, in screens: -`stripeLength` (all to the left) … 1 (all to the right).
    static func stripeAt(_ wipe: Double, _ k: Int) -> Double {
        let p = min(1, max(0, (wipe - Double(k) * stripeLag) / stripeSpan))
        return -stripeLength + (stripeLength + 1) * easeInOut.value(at: p)
    }

    /// `x` moved by whole turns of `n` into [-n/2, n/2).
    static func centred(_ x: Double, _ n: Int) -> Double {
        x - Double(n) * floor(x / Double(n) + 0.5)
    }

    /// JavaScript's Math.round: halves go up.
    static func round(_ x: Double) -> Double { floor(x + 0.5) }
}

/// One frame to draw.
struct WordDrumFrame {
    var scroll: Double
    var speed: Double
    var kick: Double
    var wipe: Double
    var showing: Double
    var chosen: Int
}

struct WordDrumSettings {
    var count: Int
    var autoPlay: Bool
    var reducedMotion: Bool
    var axisY: Double
    var onChoose: ((Int) -> Void)?
}

/// The drum's state, stepped once a frame like the RN frame callback, and the gestures that turn it.
@MainActor
final class WordDrumEngine {
    private typealias M = WordDrumMetrics

    private var scroll = 0.0
    private var seen = 0.0
    private var velocity = 0.0
    private var target = 0.0
    // |velocity| smoothed, in words per second.
    private var speed = 0.0
    // The arrow's kick to the right, in pt, and its speed.
    private var kick = 0.0
    private var kickVelocity = 0.0
    private var dragging = false
    private var dragFrom = 0.0
    // 0…1 through a sweep of the stripes; `showing` flips while they cover the screen.
    private var wipe = 0.0
    private var wipeStart: Double?
    private var showing = 0.0
    private var fade: (from: Double, to: Double, start: Double)?
    private var wiping = false
    private var byUser = false
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var chosen = 0
    private var elapsed = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var lastFront: Int?
    private var wasCovered = false
    // The touch under way.
    private var touchStart: Date?
    private var panning = false

    private let selection = UISelectionFeedbackGenerator()
    private let impact = UIImpactFeedbackGenerator(style: .light)

    func step(to date: Date, settings: WordDrumSettings) -> WordDrumFrame {
        let count = max(settings.count, 1)
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 {
            elapsed += dt
            stepTimings()
            advance(dt: dt, count: count, settings: settings)
        }

        // The swap happens under the first stripe, so neither side is seen changing.
        let covered = wipe >= M.swapAt
        if covered && !wasCovered { showing = showing > 0.5 ? 0 : 1 }
        wasCovered = covered

        // Each word that reaches the front kicks the arrow; under the user's hand it also ticks.
        let front = Int(M.round(M.centred(scroll, count) + Double(count))) % count
        if let before = lastFront, before != front {
            if !settings.reducedMotion { kickVelocity = min(M.kickSpeed, kickVelocity + M.kickSpeed) }
            if byUser { selection.selectionChanged() }
        }
        lastFront = front

        return WordDrumFrame(scroll: scroll, speed: speed, kick: kick, wipe: wipe, showing: showing, chosen: chosen)
    }

    private func stepTimings() {
        if let start = wipeStart {
            let p = min(1, (elapsed - start) / M.wipeTime)
            wipe = p
            if p >= 1 {
                wipeStart = nil
                wiping = false
            }
        }
        if let fade {
            // Reanimated's default timing curve, ease in and out (quadratic).
            let p = min(1, (elapsed - fade.start) / M.fadeTime)
            let eased = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
            showing = fade.from + (fade.to - fade.from) * eased
            if p >= 1 {
                self.fade = nil
                wiping = false
            }
        }
    }

    private func advance(dt: Double, count: Int, settings: WordDrumSettings) {
        if dragging {
            velocity += ((scroll - seen) / dt - velocity) * 0.5
        } else {
            if hold > 0 {
                hold -= dt
                if hold <= 0 {
                    clock = 0
                    // A word left chosen goes back to the wheel before the loop starts over.
                    if looping && showing > 0.5 { sweep(settings: settings) }
                }
            } else if looping {
                let before = clock
                let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
                clock = t
                func passed(_ at: Double) -> Bool {
                    (before < at && t >= at) || (t < before && (at > before || at <= t))
                }
                for turn in M.turns where passed(turn) {
                    byUser = false
                    target = M.round(target) + 1
                }
                if passed(M.chooseAt) && showing < 0.5 { choose(user: false, settings: settings) }
                if passed(M.returnAt) && showing > 0.5 { sweep(settings: settings) }
            }
            if settings.reducedMotion {
                scroll = target
                velocity = 0
            } else {
                let v = velocity + ((M.stiffness * (target - scroll) - M.damping * velocity) / M.mass) * dt
                velocity = v
                scroll += v * dt
            }
            // Whole turns of the drum change nothing on screen, so the numbers are kept small.
            let turns = floor(target / Double(count)) * Double(count)
            if turns != 0 {
                scroll -= turns
                target -= turns
            }
        }
        seen = scroll
        let fast = settings.reducedMotion ? 0 : abs(velocity)
        speed += (fast - speed) * (1 - exp(-dt * M.speedRate))
        let pull = -M.kickStiffness * kick - M.kickDamping * kickVelocity
        let kv = kickVelocity + pull * dt
        let next = kick + kv * dt
        // Held between rest and `kickMax`; pressed against either end, it stops there.
        if next >= M.kickMax {
            kick = M.kickMax
            kickVelocity = min(kv, 0)
        } else if next <= 0 {
            kick = 0
            kickVelocity = max(kv, 0)
        } else {
            kick = next
            kickVelocity = kv
        }
    }

    /// Sweeps the stripes across; while they cover the screen the drum and the word swap.
    private func sweep(settings: WordDrumSettings) {
        if wiping { return }
        wiping = true
        wipe = 0
        if settings.reducedMotion {
            fade = (showing, showing > 0.5 ? 0 : 1, elapsed)
            return
        }
        wipeStart = elapsed
    }

    private func choose(user: Bool, settings: WordDrumSettings) {
        let count = max(settings.count, 1)
        let front = M.round(scroll)
        target = front
        let index = Int(front) - count * Int(floor(front / Double(count)))
        chosen = index
        if user {
            impact.impactOccurred()
            settings.onChoose?(index)
        }
        sweep(settings: settings)
    }

    private func touched() {
        hold = M.userHold
        byUser = true
    }

    // MARK: Gestures

    func touchMoved(_ value: DragGesture.Value, settings: WordDrumSettings) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
        }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.dragSlop else { return }
            panning = true
            if showing > 0.5 || wiping { return }
            touched()
            dragFrom = scroll
            seen = scroll
            dragging = true
        }
        guard dragging else { return }
        scroll = dragFrom - value.translation.height / M.dragStep
    }

    func touchEnded(_ value: DragGesture.Value, settings: WordDrumSettings) {
        defer {
            touchStart = nil
            panning = false
        }
        if panning {
            guard dragging else { return }
            dragging = false
            // The finger's own speed at release, in words per second, carries into the spring.
            velocity = -value.velocity.height / M.dragStep
            target = M.round(scroll + velocity * M.throwTime)
            return
        }
        guard let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration, !wiping else { return }
        touched()
        if showing > 0.5 {
            sweep(settings: settings)
            return
        }
        // The word under the finger: rows sit radius·sin(d·angle) from the axis.
        var hit = 0
        var best = Double.infinity
        for d in -3...3 {
            let y = settings.axisY + M.radius * sin(Double(d) * M.angle)
            let off = abs(value.location.y - y)
            if off < best {
                best = off
                hit = d
            }
        }
        if hit == 0 {
            hold = M.chosenHold
            choose(user: true, settings: settings)
        } else {
            target = M.round(scroll) + Double(hit)
        }
    }
}
