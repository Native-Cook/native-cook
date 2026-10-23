import SwiftUI
import UIKit

enum FlipbookScrubMetrics {
    // The table, the book's paper and its binding.
    static let table: UInt32 = 0x17130f
    static let tableLit: UInt32 = 0x2c241d
    static let paper: UInt32 = 0xf2ece0
    static let paperBack: UInt32 = 0xf8f3ea
    static let paperEdge: UInt32 = 0xd9d0c0
    static let blank: UInt32 = 0xe4dccd
    static let ink: UInt32 = 0x2b2620
    static let tape: UInt32 = 0x2a1f19
    static let tapeSheen: UInt32 = 0x4a382c
    static let tapeDeep: UInt32 = 0x1c1511
    static let chrome: UInt32 = 0xefe6d8

    // The book, in pt: a leaf is the screen's width less `side` a side, `pageAspect` times as tall.
    // It is bound along its top edge under a cloth tape `tapeH` deep, and the leaves under the top
    // one show as a block `thick` deep along its bottom edge. The photo is printed `margin` in from
    // the edges, with `captionH` under it for its caption.
    static let side: CGFloat = 32
    static let maxPageW: CGFloat = 360
    static let pageAspect: CGFloat = 1.16
    static let tapeH: CGFloat = 26
    static let thick: CGFloat = 18
    static let margin: CGFloat = 14
    static let captionH: CGFloat = 34
    static let corner: CGFloat = 3
    // The header above the book: the label line and the counter, from the top safe edge.
    static let labelY: CGFloat = 24
    static let counterY: CGFloat = 74
    static let counterSize: CGFloat = 50
    // A leaf turning over the binding rises above it; this share of a leaf's height is kept clear
    // between the header and the binding for it.
    static let riseRoom: CGFloat = 0.36

    // The turn of one leaf, t = 0 (flat) … 1 (gone over the top). The thumb draws the leaf's
    // bottom-right corner up along a path that bows inwards by `bow` of the most the binding
    // allows, and the leaf folds along the line halfway between the corner and the thumb. Past
    // `squashFrom` the part above the binding folds down behind the book until it is gone at t = 1.
    static let bow = 0.52
    static let squashFrom = 0.55
    // The roll of paper along the fold, and the shadow the lifted part casts on the leaf under it.
    static let roll = 30.0
    static let foldShadow = 34.0
    static let showThrough = 0.09

    // A slow drag turns one leaf every `dragStep` pt; dragging up turns forwards. Faster, more
    // leaves are in the air at once: `spreadBase` leaves at rest, up to `spreadMax`, `spreadRate`
    // more per leaf per second. Past either end the book gives `rubber` leaves at most.
    static let dragStep = 46.0
    static let dragSlop = 4.0
    static let tapMaxDuration = 0.3
    static let spreadBase = 1.0
    static let spreadMax = 3.6
    static let spreadRate = 0.16
    static let speedSmooth = 9.0
    static let rubber = 1.2
    // After a release the book coasts, losing `friction` of its speed per second, until it is
    // slower than `settleSpeed` leaves/s; then a spring (stiffness, damping) lays the nearest leaf down.
    static let friction = 1.7
    static let settleSpeed = 3.2
    // Safety rails: a release is never faster than `maxSpeed` leaves/s, a coast that reaches an
    // end hands at most `endSpeed` leaves/s to the spring there, and the book's place never leaves
    // the band `slack` leaves past either end, so one long frame cannot carry it off the book.
    static let maxSpeed = 60.0
    static let endSpeed = 10.0
    static let slack = 1.6
    static let stiffness = 230.0
    static let damping = 23.0
    // A tick under the thumb every `tickEvery` leaves.
    static let tickEvery = 3.0
    static let imageFade = 0.4

    // autoPlay (seconds): the thumb turns `leaves` leaves over one at a time, rests, and turns them
    // back one at a time. Each turn takes `turn` s on a cubic ease-in-out, and a new one starts
    // every `step` s, so the book lies still on every leaf for `step − turn` s before the next.
    static let leaves = 5
    static let turn = 1.0
    static let stepEvery = 1.7
    static let rest = stepEvery - turn
    // The rest on the first leaf before the first turn, and the longer rest on the last leaf
    // before the way back.
    static let lead = 0.8
    static let pause = 0.8
    static let backAt = lead + Double(leaves) * stepEvery + pause
    static let loop = backAt + Double(leaves) * stepEvery - rest + lead
    // Each segment moves the book from wherever it is to `aim` leaves past the leaf the loop
    // started on; between segments the thumb lets go and the spring lays the leaf down.
    static let script: [FlipbookScrubSegment] =
        (0..<leaves).map { k in
            let from = lead + Double(k) * stepEvery
            return FlipbookScrubSegment(from: from, to: from + turn, aim: Double(k + 1), easeOut: false)
        }
        + (0..<leaves).map { k in
            let from = backAt + Double(k) * stepEvery
            return FlipbookScrubSegment(from: from, to: from + turn, aim: Double(leaves - k - 1), easeOut: false)
        }
    static let userHold = 3.5

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }

    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }

    static func ease(_ out: Bool, _ u: Double) -> Double {
        if out { return 1 - pow(1 - u, 3) }
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    /// iOS-style resistance past an end: `x` leaves past it give at most `limit`.
    static func rubberBand(_ x: Double, _ limit: Double) -> Double {
        let sign: Double = x < 0 ? -1 : 1
        return sign * (1 - 1 / ((abs(x) * 0.55) / limit + 1)) * limit
    }

    /// JavaScript's Math.round: halves go up.
    static func round(_ x: Double) -> Double { floor(x + 0.5) }

    /// How far leaf `k` has turned with the book at `p` and `spread` leaves in the air.
    static func turnOf(_ k: Int, _ p: Double, _ spread: Double, _ last: Int) -> Double {
        // The back leaf never turns; pressed past the end, its corner only lifts a little.
        if k >= last { return min(0.12, max(0, (p - Double(last)) * 0.3)) }
        let t = clamp01((p - Double(k) - 1 + spread) / spread)
        // Pulled back past the front, the first leaf's corner lifts as if to come back.
        if k == 0 && p < 0 { return max(t, min(0.12, -p * 0.3)) }
        return t
    }

    /// The fold of a leaf w × h turned by t: the line's normal (towards the corner) and offset.
    static func foldOf(_ t: Double, _ w: Double, _ h: Double) -> FlipbookScrubFold {
        let lift = 2 * h * t
        // The thumb stays within a leaf's height of the binding's far corner, so the fold never
        // crosses the binding.
        let bow = self.bow * sqrt(max(0, (2 * h - lift) * lift))
        let px = w - bow
        let py = h - lift
        let dx = w - px
        let dy = h - py
        let len = max(0.0001, hypot(dx, dy))
        let nx = dx / len
        let ny = dy / len
        let mx = (w + px) / 2
        let my = (h + py) / 2
        return FlipbookScrubFold(nx: nx, ny: ny, c: mx * nx + my * ny, mx: mx, my: my, len: len)
    }

    /// The part of a polygon where (p·n − c) has the sign `keep`.
    static func clip(_ points: [CGPoint], _ nx: Double, _ ny: Double, _ c: Double, keep: Double) -> [CGPoint] {
        var out: [CGPoint] = []
        let count = points.count
        for i in 0..<count {
            let a = points[i]
            let b = points[(i + 1) % count]
            let sa = (a.x * nx + a.y * ny - c) * keep
            let sb = (b.x * nx + b.y * ny - c) * keep
            if sa >= 0 { out.append(a) }
            if (sa >= 0) != (sb >= 0) {
                let k = sa / (sa - sb)
                out.append(CGPoint(x: a.x + (b.x - a.x) * k, y: a.y + (b.y - a.y) * k))
            }
        }
        return out
    }

    static func polygon(_ points: [CGPoint]) -> Path {
        var path = Path()
        path.addLines(points)
        path.closeSubpath()
        return path
    }
}

struct FlipbookScrubSegment {
    let from: Double
    let to: Double
    let aim: Double
    let easeOut: Bool
}

struct FlipbookScrubFold {
    let nx: Double
    let ny: Double
    let c: Double
    let mx: Double
    let my: Double
    let len: Double
}

/// One frame to draw.
struct FlipbookScrubFrame {
    var page: Double
    var speed: Double
    var elapsed: Double
}

struct FlipbookScrubSettings {
    var count: Int
    var autoPlay: Bool
    var reducedMotion: Bool
    /// Where the middle of the book is on screen: a tap below it turns forwards.
    var bookMiddle: Double
    var onPage: ((Int) -> Void)?
}

/// The book's state, stepped once a frame like the RN frame callback, and the gestures that turn it.
@MainActor
final class FlipbookScrubEngine {
    private typealias M = FlipbookScrubMetrics

    private enum Mode { case drag, coast, settle, script }

    // The book's place in leaves: 0 has the first leaf on top, 0.5 has it half over.
    private var page = 0.0
    private var velocity = 0.0
    // |velocity|, smoothed: how many leaves are in the air.
    private var speed = 0.0
    private var target = 0.0
    private var mode = Mode.settle
    private var dragFrom = 0.0
    private var byUser = false
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var segStart = 0.0
    private var segIndex = -1
    // The leaf the loop starts from and comes back to.
    private var base = 0.0
    private(set) var elapsed = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var lastTick: Double?
    private var wasResting = true
    // The touch under way.
    private var touchStart: Date?
    private var panning = false

    /// Each print as it arrives, with the time it arrived so it can fade in.
    var loaded: FlipbookScrubPainter.Loaded = [:]

    private let selection = UISelectionFeedbackGenerator()

    func step(to date: Date, settings: FlipbookScrubSettings) -> FlipbookScrubFrame {
        let last = Double(max(settings.count - 1, 0))
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
            base = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 {
            advance(dt: dt, last: last, settings: settings)
        }

        // A tick every few leaves while a hand turns them.
        let tick = floor(page / M.tickEvery)
        if let before = lastTick, before != tick, byUser { selection.selectionChanged() }
        lastTick = tick
        let resting = mode == .settle && abs(velocity) < 0.05 && abs(page - target) < 0.01
        if resting && !wasResting && byUser { settings.onPage?(Int(target)) }
        wasResting = resting

        return FlipbookScrubFrame(page: page, speed: speed, elapsed: elapsed)
    }

    private func advance(dt: Double, last: Double, settings: FlipbookScrubSettings) {
        elapsed += dt
        let before = page

        if mode != .drag {
            if hold > 0 {
                hold -= dt
                let resting = mode == .settle && abs(velocity) < 0.05 && abs(page - target) < 0.01
                // A book still coasting or settling keeps the loop waiting until it lies still.
                if hold <= 0 && looping && !resting { hold = 0.1 }
                if hold <= 0 && looping {
                    // Back into the loop from the leaf the hand left the book on: it becomes one of
                    // the loop's leaves (as near the start as the book allows) and the script picks
                    // up in the rest on that leaf, so the book only ever turns one leaf at a time.
                    let at = min(last, max(0, M.round(page)))
                    let from = min(at, max(0, last - Double(M.leaves)))
                    let j = at - from
                    base = from
                    clock = j == 0 ? 0 : M.backAt + (Double(M.leaves) - j) * M.stepEvery - M.rest
                    segIndex = -1
                }
            } else if looping {
                let t = (clock + dt).truncatingRemainder(dividingBy: M.loop)
                clock = t
                var active = -1
                for (i, s) in M.script.enumerated() where t >= s.from && t < s.to { active = i }
                if active != segIndex {
                    if active >= 0 {
                        segStart = page
                        byUser = false
                        mode = .script
                    } else if segIndex >= 0 {
                        // A segment ended: the thumb lets go and the nearest leaf settles.
                        target = min(last, max(0, M.round(page)))
                        mode = .settle
                    }
                    segIndex = active
                }
                if active >= 0 {
                    let s = M.script[active]
                    let u = (t - s.from) / (s.to - s.from)
                    let aim = min(last, max(0, base + s.aim))
                    let p = segStart + (aim - segStart) * M.ease(s.easeOut, u)
                    velocity = (p - page) / dt
                    page = p
                }
            }

            let p = page
            if mode == .coast {
                if abs(velocity) < M.settleSpeed {
                    let v = velocity
                    target = min(last, max(0, v > 0.4 ? ceil(p) : v < -0.4 ? floor(p) : M.round(p)))
                    mode = .settle
                } else {
                    velocity *= exp(-M.friction * dt)
                    let next = p + velocity * dt
                    if next < 0 || next > last {
                        // The coast reaches an end: it stops on the end leaf, and the spring there
                        // takes what is left of its speed (capped), so the leaf presses in a little.
                        let end = next < 0 ? 0 : last
                        page = end
                        velocity = min(M.endSpeed, max(-M.endSpeed, velocity))
                        target = end
                        mode = .settle
                    } else {
                        page = next
                    }
                }
            }
            if mode == .settle {
                if settings.reducedMotion {
                    page = target
                    velocity = 0
                } else {
                    let v = velocity + (M.stiffness * (target - p) - M.damping * velocity) * dt
                    velocity = v
                    page = p + v * dt
                }
            }
        }

        // Whatever happened above, the place stays a finite number near the book.
        if !page.isFinite || !velocity.isFinite {
            page = min(last, max(0, target.isFinite ? M.round(target) : 0))
            velocity = 0
            mode = .settle
        } else if page < -M.slack || page > last + M.slack {
            page = min(last + M.slack, max(-M.slack, page))
        }

        // The script turns one leaf at a time, so only a hand puts several leaves in the air.
        let fast = settings.reducedMotion || !byUser ? 0 : abs(velocity)
        speed += (fast - speed) * (1 - exp(-dt * M.speedSmooth))
        if mode == .drag {
            // The finger's speed, read from where it moved the book since the last frame.
            velocity += ((page - before) / dt - velocity) * 0.4
        }
    }

    // MARK: Gestures

    /// One recogniser stands in for the race of a pan (after 4 pt of travel) and a tap (a touch
    /// lifted within 300 ms that never travelled that far).
    func touchMoved(_ value: DragGesture.Value, settings: FlipbookScrubSettings) {
        let last = Double(max(settings.count - 1, 0))
        if touchStart == nil {
            touchStart = value.time
            panning = false
        }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.dragSlop else { return }
            panning = true
            hold = M.userHold
            byUser = true
            segIndex = -1
            // Caught wherever it is, mid-turn or mid-flight.
            dragFrom = min(last, max(0, page))
            mode = .drag
        }
        guard mode == .drag else { return }
        hold = M.userHold
        let raw = dragFrom - value.translation.height / M.dragStep
        if raw < 0 {
            page = M.rubberBand(raw, M.rubber)
        } else if raw > last {
            page = last + M.rubberBand(raw - last, M.rubber)
        } else {
            page = raw
        }
    }

    func touchEnded(_ value: DragGesture.Value, settings: FlipbookScrubSettings) {
        let last = Double(max(settings.count - 1, 0))
        defer {
            touchStart = nil
            panning = false
        }
        if panning {
            guard mode == .drag else { return }
            hold = M.userHold
            let thrown = -value.velocity.height / M.dragStep
            velocity = thrown.isFinite ? min(M.maxSpeed, max(-M.maxSpeed, thrown)) : 0
            mode = .coast
            return
        }
        guard let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration else { return }
        // A tap on the book turns one leaf forwards, or back when tapped above the middle.
        hold = M.userHold
        byUser = true
        segIndex = -1
        let forward = value.location.y > settings.bookMiddle
        let from = mode == .settle ? target : M.round(page)
        let next = min(last, max(0, from + (forward ? 1 : -1)))
        // A tap caught mid-coast stops the coast; the spring keeps the speed it had.
        velocity = min(M.endSpeed, max(-M.endSpeed, velocity))
        target = next
        mode = .settle
        if next != from { selection.selectionChanged() }
    }
}

/// Downloads a print once and keeps it decoded.
enum FlipbookScrubPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        // Decoded off the main thread, so the first frame that draws it does not stall.
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}
