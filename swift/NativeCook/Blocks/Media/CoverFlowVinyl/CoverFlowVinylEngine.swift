import SwiftUI
import UIKit

enum CoverFlowVinylMetrics {
    // The cover in front is `sizeShare` of the width (at most `maxSize` pt), centred at `centreAt`
    // of the height. Its neighbours stand `first` covers to each side, the rest `next` further on,
    // turned `tilt`° to face the middle, shrunk by `sideScale`, darkened by `sideDim`, and seen
    // through a lens `perspective` covers away. Covers further than `visible` from the front fade out.
    static let sizeShare = 0.56
    static let maxSize = 250.0
    static let centreAt = 0.4
    static let first = 0.62
    static let next = 0.16
    static let tilt = 70.0
    static let sideScale = 0.2
    static let sideDim = 0.5
    static let dimPerCover = 0.1
    static let perspective = 4.5
    static let visible = 3.6
    // The floor reflection: a flipped copy fading into the background over `reflection` of a cover.
    static let reflection = 0.42
    // How strong the reflection is just under the cover.
    static let reflectionStrength = 0.42
    // The caption sits this far under the covers, the player under it.
    static let captionGap = 100.0

    // Scrolling: one cover per `dragStep` of the cover's size under the finger; a release is thrown
    // `throwTime` seconds ahead and a spring (ζ ≈ 0.9) settles on the nearest cover.
    static let dragStep = 0.72
    static let throwTime = 0.16
    static let stiffness = 190.0
    static let damping = 25.0
    static let panSlop = 8.0
    static let tapSlop = 10.0
    static let tapMaxDuration = 0.5

    // Pulling the record out: a spring on 0…1 (ζ ≈ 0.78, so the record overshoots a touch as it
    // stops). The cover lifts `lift` pt and moves left by `shift` of its size; the record slides
    // out to `recordOut` of the size right of the cover's centre; the other covers spread `spread`
    // of a cover outwards and fade.
    static let openStiffness = 170.0
    static let openDamping = 20.0
    static let lift = 26.0
    static let shift = 0.24
    static let recordOut = 0.56
    static let recordSize = 0.95
    static let spread = 0.45
    // 33⅓ rpm, reached and lost at `spinRate` (1/s).
    static let rpm = 100.0 / 3
    static let spinRate = 2.4
    static let label = 0.36
    static let imageFade = 0.3

    // autoPlay (seconds): two covers on, the record out and playing, back in, a flick back home.
    static let loop = 12.0
    static let steps: [(at: Double, by: Int)] = [(0.7, 1), (1.8, 1), (9.0, -2)]
    static let openAt = 3.0
    static let closeAt = 7.6
    static let userHold = 4.0

    /// Where a cover `d` places from the front sits: sideways offset (in covers), turn and shade.
    static func placement(_ d: Double) -> (x: Double, turn: Double, scale: Double, dim: Double, alpha: Double) {
        let a = abs(d)
        let s: Double = d > 0 ? 1 : d < 0 ? -1 : 0
        let near = min(a, 1)
        return (
            x: s * (near * first + max(a - 1, 0) * next),
            turn: -s * near * tilt,
            scale: 1 - near * sideScale,
            dim: near * sideDim + max(a - 1, 0) * dimPerCover,
            alpha: min(max(visible + 0.6 - a, 0), 1)
        )
    }

    /// `x` moved by whole turns of `n` into [-n/2, n/2).
    static func centred(_ x: Double, _ n: Int) -> Double {
        x - Double(n) * floor(x / Double(n) + 0.5)
    }

    /// JavaScript's Math.round: halves go up.
    static func round(_ x: Double) -> Double { floor(x + 0.5) }

    /// The album in front when the flow is at `x`.
    static func indexAt(_ x: Double, _ n: Int) -> Int {
        // Int(NaN) and Int(±inf) trap; an index out of 0..<n would trap where it is used.
        guard n > 0, x.isFinite else { return 0 }
        let r = round(x)
        return min(max(Int(r - Double(n) * floor(r / Double(n))), 0), n - 1)
    }

    /// Whether the clock, going from `before` to `now` (wrapping at the loop), went past `at`.
    static func passed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        (before < at && now >= at) || (now < before && at <= now)
    }

    /// A cover's perspective, for a view `size` wide and twice as tall (the sleeve over its
    /// reflection), turned about its middle, which is the floor line: scaled, turned `turn`°
    /// about the vertical, moved `x` pt sideways and `lift` pt up, then seen through a lens
    /// `perspective` covers away, as the RN transform list does.
    static func projection(size: Double, scale s: Double, turn: Double, x: Double, lift: Double) -> ProjectionTransform {
        // A zero size (the first layout) would divide by zero below and hand Core Animation NaNs.
        guard size > 0 else { return ProjectionTransform() }
        let w = size
        let h = size * 2
        let theta = turn * .pi / 180
        let c = cos(theta)
        let g = sin(theta) * s / (size * perspective)
        let k0 = 1 - g * w / 2
        var t = ProjectionTransform()
        t.m11 = w / 2 * g + c * s
        t.m21 = 0
        t.m31 = w / 2 * k0 - c * s * w / 2 + x
        t.m12 = h / 2 * g
        t.m22 = s
        t.m32 = h / 2 * k0 - s * h / 2 - lift
        t.m13 = g
        t.m23 = 0
        t.m33 = k0
        return t
    }
}

/// One frame to draw.
struct CoverFlowVinylFrame {
    var scroll: Double
    var open: Double
    var spin: Double
    var track: Double
    var front: Int
    var playing: Bool
    var elapsed: Double
}

/// The flow's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class CoverFlowVinylEngine {
    private typealias M = CoverFlowVinylMetrics

    private var scroll = 0.0
    private var velocity = 0.0
    private var target = 0.0
    private var dragging = false
    private var dragFrom = 0.0
    private var seen = 0.0
    private var open = 0.0
    private var openV = 0.0
    private var openTarget = 0.0
    private var spin = 0.0
    private var spinSpeed = 0.0
    private var paused = false
    // Where the track is, in seconds.
    private var track: Double?
    private var byUser = false
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var front = 0
    private var lastAuto: Bool?
    private var lastDate: Date?
    private(set) var elapsed = 0.0

    // The layout the gestures read.
    private var width = 0.0
    private var size = 0.0
    private var centreY = 0.0

    // The touch under way.
    private var downAt: Date?
    private var touchOrigin = CGPoint.zero
    private var panning = false

    private var photos: [String: (image: UIImage, at: Double)] = [:]
    private let selection = UISelectionFeedbackGenerator()
    private let impact = UIImpactFeedbackGenerator(style: .light)

    var albums: [CoverFlowVinylAlbum] = []
    var onPlay: ((CoverFlowVinylAlbum) -> Void)?

    func photo(_ address: String) -> (image: UIImage, at: Double)? { photos[address] }

    func photoArrived(_ image: UIImage, for address: String) {
        if photos[address] == nil { photos[address] = (image, elapsed) }
    }

    func step(
        to date: Date,
        albums: [CoverFlowVinylAlbum],
        width: Double,
        height: Double,
        autoPlay: Bool,
        reducedMotion: Bool
    ) -> CoverFlowVinylFrame {
        self.albums = albums
        self.width = width
        size = min(width * M.sizeShare, M.maxSize)
        centreY = height * M.centreAt
        if track == nil { track = albums.first?.startAt ?? 0 }
        if lastAuto != autoPlay {
            lastAuto = autoPlay
            looping = autoPlay
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0, !albums.isEmpty {
            advance(dt: dt, reducedMotion: reducedMotion)
        }
        return CoverFlowVinylFrame(
            scroll: scroll,
            open: open,
            spin: spin,
            track: track ?? 0,
            front: front,
            playing: !paused,
            elapsed: elapsed
        )
    }

    /// Pulls the front record out, or slides it back in.
    private func toggle(user: Bool) {
        let out = openTarget < 0.5
        openTarget = out ? 1 : 0
        if out {
            let index = M.indexAt(scroll, albums.count)
            // The record left in the sleeve is wound back to where this album starts.
            if open < 0.02 {
                spin = 0
                track = albums[index].startAt
            }
            paused = false
            onPlay?(albums[index])
        }
        if user { impact.impactOccurred() }
    }

    private func advance(dt: Double, reducedMotion: Bool) {
        elapsed += dt
        let count = albums.count

        if hold > 0 {
            hold -= dt
            // The script starts over from a resting state: closed at its start, open just before it closes.
            if hold <= 0 { clock = openTarget > 0.5 ? M.closeAt - 0.1 : 0 }
        } else if looping && !dragging {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            for step in M.steps where M.passed(before, t, step.at) && openTarget < 0.5 {
                byUser = false
                target = M.round(target) + Double(step.by)
                // A flick back carries its speed into the spring, like a finger's would.
                if step.by < -1 { velocity = Double(step.by) * 3.2 }
            }
            if M.passed(before, t, M.openAt) && openTarget < 0.5 { toggle(user: false) }
            if M.passed(before, t, M.closeAt) && openTarget > 0.5 { toggle(user: false) }
        }

        // The flow.
        if dragging {
            velocity += ((scroll - seen) / dt - velocity) * 0.5
        } else if reducedMotion {
            scroll = target
            velocity = 0
        } else {
            let v = velocity + (M.stiffness * (target - scroll) - M.damping * velocity) * dt
            velocity = v
            scroll += v * dt
        }
        seen = scroll
        // Whole turns of the flow change nothing on screen, so the numbers are kept small.
        let turns = floor(target / Double(count)) * Double(count)
        if turns != 0 && !dragging {
            scroll -= turns
            seen -= turns
            target -= turns
        }

        // The record.
        if reducedMotion {
            open += (openTarget - open) * (1 - exp(-dt * 12))
        } else {
            let ov = openV + (M.openStiffness * (openTarget - open) - M.openDamping * openV) * dt
            openV = ov
            open += ov * dt
        }
        let turning = openTarget > 0.5 && !paused
        let goal = turning && !reducedMotion ? M.rpm / 60 * 360 : 0
        spinSpeed += (goal - spinSpeed) * (1 - exp(-dt * M.spinRate))
        spin = (spin + spinSpeed * dt).truncatingRemainder(dividingBy: 360)
        let index = M.indexAt(scroll, count)
        if turning {
            track = ((track ?? 0) + dt).truncatingRemainder(dividingBy: albums[index].length)
        }

        // The album in front names the record and the player; a tick when the user brought it there.
        if index != front {
            front = index
            if byUser { selection.selectionChanged() }
        }
    }

    // MARK: Gestures

    private func touched() {
        hold = M.userHold
        byUser = true
    }

    // One recogniser stands in for the exclusive pan (8 pt sideways) and tap (within 10 pt).
    func touchChanged(_ value: DragGesture.Value) {
        if downAt == nil || value.startLocation != touchOrigin {
            // A drag SwiftUI cancelled never reached touchEnded; settle it on a cover before the new touch.
            if dragging {
                dragging = false
                target = M.round(scroll)
            }
            // The gesture's own timestamps are not on the timeline's clock.
            downAt = Date()
            touchOrigin = value.startLocation
            panning = false
        }
        let step = size * M.dragStep
        if !panning && abs(value.translation.width) >= M.panSlop {
            panning = true
            touched()
            if openTarget <= 0.5 {
                dragFrom = scroll
                seen = scroll
                dragging = true
            }
        }
        if panning && dragging && step > 0 {
            scroll = dragFrom - value.translation.width / step
        }
    }

    func touchEnded(_ value: DragGesture.Value) {
        let start = downAt
        defer {
            downAt = nil
            panning = false
        }
        if panning {
            let step = size * M.dragStep
            guard dragging, step > 0 else { return }
            dragging = false
            let thrown = -value.velocity.width / step
            velocity = thrown.isFinite ? thrown : 0
            target = M.round(scroll + velocity * M.throwTime)
            return
        }
        // A touch lifted on the frame it went down reports no change first: it is a tap.
        let quick = start.map { Date().timeIntervalSince($0) <= M.tapMaxDuration } ?? true
        guard quick, hypot(value.translation.width, value.translation.height) <= M.tapSlop else { return }
        tap(at: value.location)
    }

    private func tap(at point: CGPoint) {
        touched()
        if openTarget > 0.5 {
            // The player's middle button pauses; anywhere else puts the record back.
            let controlsY = centreY + size / 2 + M.captionGap + 121
            if abs(point.y - controlsY) < 34 && abs(point.x - width / 2) < 34 {
                paused.toggle()
                return
            }
            toggle(user: true)
            return
        }
        // Before the first layout there are no covers to tap (and dx would divide by zero).
        guard size > 0 else { return }
        // A tap on a side cover brings it to the front; on the front cover, pulls its record.
        let dx = (point.x - width / 2) / size
        if abs(dx) < 0.5 && abs(point.y - centreY) < size / 2 {
            target = M.round(scroll)
            toggle(user: true)
            return
        }
        if abs(point.y - centreY) > size * 0.7 { return }
        let a = abs(dx)
        let places = a < M.first + 0.2 ? 1 : 1 + M.round((a - M.first) / M.next)
        target = M.round(scroll) + (dx < 0 ? -1 : 1) * places
    }
}

// MARK: - Photos

/// Downloads a cover once and keeps it decoded for the sleeve, its reflection and the label.
enum CoverFlowVinylPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}
