import SwiftUI
import UIKit

enum JellySheetMetrics {
    // The sheet's three resting places, as the y of its top edge: the peek leaves `peekShow` pt of
    // the sheet above the footer; half sits at `half` of the screen; full stops `fullGap` below
    // the top inset.
    static let footer = 84.0
    static let peekShow = 124.0
    static let half = 0.47
    static let fullGap = 100.0
    static let corner = 28.0

    // The jelly edge is a membrane: `nodes` points across the width, pinned at both corners, each
    // lifted d pt off the sheet's straight edge. Each node is tied to its rest place (`anchor`,
    // 1/s²) and to its neighbours (`tension`), with damping of its own (`damp`) and between
    // neighbours (`shearDamp`, which calms the short ripples first), so the edge rings at about
    // 3 Hz in one soft arch, never in a zig-zag. It is stepped in fixed substeps of `substep` s.
    static let nodes = 21
    static let anchor = 60.0
    static let tension = 12000.0
    static let damp = 2.0
    static let shearDamp = 170.0
    static let substep = 1.0 / 720
    // What moves it: the air the sheet moves through holds every node back by `lag` × the sheet's
    // speed (pt/s²), so the edge trails a rising sheet; the nodes' own weight carries `inertia` ×
    // the sheet's acceleration (smoothed over `accelSmooth` s), so the edge swells as it stops; a
    // finger holds the nodes under it (a bell `grip` of the width wide) with a spring (`hold`,
    // `holdDamp`), so the edge near the finger leads and the rest trails; dragged past an end
    // detent, the finger lifts them by `stretchPull` of the stretch. A tap on the head kicks the
    // nodes under it up at `kick` pt/s.
    static let lag = 4.5
    static let inertia = 0.45
    static let accelSmooth = 0.04
    static let grip = 0.22
    static let hold = 900.0
    static let holdDamp = 40.0
    static let stretchPull = 0.55
    static let kick = 320.0
    // Drawn through a soft limit, so the edge never lifts or sags more than `liftMax` pt.
    static let liftMax = 34.0
    static let samples = 64
    // The content under the edge gives with the middle of it by this share, so a sag never covers it.
    static let contentGive = 0.5
    // The grabber stretches with the edge's local slope, up to `grabStretch` pt, and tilts with it.
    static let grabW = 40.0
    static let grabStretch = 14.0

    // The sheet itself: a spring to its detent (ζ ≈ 0.75, a small overshoot that sets the jelly
    // going), with the release velocity handed in. A release is thrown `throwTime` seconds ahead to
    // pick the detent; anything faster than `flick` pt/s moves one detent in its direction at least.
    static let stiffness = 230.0
    static let damping = 23.0
    static let throwTime = 0.18
    static let flick = 900.0
    static let rubber = 0.35
    static let panSlop = 4.0
    static let tapSlop = 10.0
    // A tap this far into the sheet's head opens it a detent.
    static let tapHead = 90.0
    // The photo behind, when the sheet is fully open: scaled back and blurred.
    static let photoScale = 0.9
    static let photoRadius = 26.0
    static let imageFade = 0.35

    // autoPlay (seconds). Each move is a finger dragging from one detent towards the next for
    // `time` seconds at x (share of the width), letting go with the speed it had.
    static let loop = 11.0
    static let moves: [(at: Double, time: Double, to: Int, x: Double)] = [
        (0.8, 0.55, 1, 0.66),
        (3.0, 0.5, 2, 0.36),
        (5.6, 0.45, 1, 0.58),
        (7.6, 0.42, 0, 0.44),
    ]
    // Where the script picks up again after a touch, by the detent the sheet rests on.
    static let resumeAt = [0, 2.2, 4.8]
    static let userHold = 4.0
    // Share of a scripted drag the finger covers before letting go; the spring does the rest.
    static let dragShare = 0.8

    /// The share of a finger's hold on node i, for a finger at fx on a sheet `width` wide.
    static func gripOf(_ i: Int, _ fx: Double, _ width: Double) -> Double {
        let x = width * Double(i) / Double(nodes - 1)
        let k = (x - fx) / max(1, width * grip)
        return exp(-k * k)
    }

    /// The edge's lift at x (pt): a Catmull-Rom curve through the nodes, through a soft limit.
    static func liftAt(_ d: [Double], _ x: Double, _ width: Double) -> Double {
        let n = nodes
        guard d.count == n else { return 0 }
        let f = min(max(x / max(1, width) * Double(n - 1), 0), Double(n - 1))
        let i = min(n - 2, Int(f.rounded(.down)))
        let t = f - Double(i)
        let p0 = d[max(0, i - 1)]
        let p1 = d[i]
        let p2 = d[i + 1]
        let p3 = d[min(n - 1, i + 2)]
        let t2 = t * t
        let t3 = t2 * t
        let raw = 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
        return liftMax * tanh(raw / liftMax)
    }
}

/// The jelly edge's nodes, pinned at both ends.
struct JellySheetMembrane {
    private typealias M = JellySheetMetrics
    var d = [Double](repeating: 0, count: JellySheetMetrics.nodes)
    var u = [Double](repeating: 0, count: JellySheetMetrics.nodes)
    var acc = 0.0
    var speed = 0.0
    var accel = 0.0

    mutating func flatten() {
        for i in 0..<M.nodes {
            d[i] = 0
            u[i] = 0
        }
    }

    /// Advances it by `dt` s. `speed` is the sheet's speed (pt/s, down positive), `accel` its
    /// acceleration; `held` says a finger holds it at fx, lifting the nodes under it to `lift`.
    mutating func step(_ dt: Double, speed: Double, accel: Double, held: Bool, fx: Double, lift: Double, width: Double) {
        let n = M.nodes
        acc = min(acc + dt, 0.05)
        while acc >= M.substep {
            acc -= M.substep
            // Velocities first (from this substep's places), then places: semi-implicit Euler.
            for i in 1..<(n - 1) {
                var a = M.tension * (d[i - 1] + d[i + 1] - 2 * d[i])
                    + M.shearDamp * (u[i - 1] + u[i + 1] - 2 * u[i])
                    - M.anchor * d[i]
                    - M.damp * u[i]
                    + M.lag * speed
                    + M.inertia * accel
                if held {
                    let w = M.gripOf(i, fx, width)
                    a += w * (M.hold * (lift - d[i]) - M.holdDamp * u[i])
                }
                u[i] += a * M.substep
            }
            for i in 1..<(n - 1) { d[i] += u[i] * M.substep }
        }
        // A guard: anything not finite (a bad frame time) puts the edge back flat.
        if d.contains(where: { !$0.isFinite }) || u.contains(where: { !$0.isFinite }) { flatten() }
    }
}

struct JellySheetStage: Equatable {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
}

/// One frame to draw.
struct JellySheetFrame {
    var y: Double
    /// The membrane's node lifts, pt.
    var lift: [Double]
    var elapsed: Double
    var detents: [Double]
}

/// The sheet's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class JellySheetEngine {
    private typealias M = JellySheetMetrics

    private var y = 0.0
    private var v = 0.0
    private var target = 0.0
    private var detent = 0
    private var dragging = false
    private var dragFrom = 0.0
    private var dragSpeed = 0.0
    private var fingerX = 0.0
    private var membrane = JellySheetMembrane()
    // A kick waiting for the next frame: where (x) and how hard (pt/s).
    private var kickX = 0.0
    private var kickV = 0.0
    private var ready = false
    // autoPlay's clock and the scripted finger.
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var scripted = -1
    private var scriptFrom = 0.0
    private var lastAuto: Bool?
    private var lastDate: Date?
    private(set) var elapsed = 0.0
    private var stage = JellySheetStage(width: 0, height: 0, top: 0, bottom: 0)
    private var detents: [Double] = [0, 0, 0]

    // The touch under way: whether it belongs to the sheet, and whether it became a drag.
    private var tracking: Bool?
    private var panning = false

    private var photos: [String: (image: UIImage, at: Double)] = [:]
    private let impact = UIImpactFeedbackGenerator(style: .light)

    var onDetentChange: ((Int) -> Void)?

    func photo(_ address: String) -> (image: UIImage, at: Double)? { photos[address] }

    func photoArrived(_ image: UIImage, for address: String) {
        if photos[address] == nil { photos[address] = (image, elapsed) }
    }

    func step(to date: Date, stage: JellySheetStage, autoPlay: Bool, reducedMotion: Bool) -> JellySheetFrame {
        if stage != self.stage {
            self.stage = stage
            // The detents follow the layout; the sheet starts at the peek.
            detents = [
                stage.height - (M.footer + stage.bottom) - M.peekShow,
                stage.height * M.half,
                stage.top + M.fullGap,
            ]
            if stage.height > 0 {
                if !ready {
                    y = detents[detent]
                    ready = true
                }
                target = detents[detent]
            }
        }
        if lastAuto != autoPlay {
            lastAuto = autoPlay
            looping = autoPlay
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0, ready {
            advance(dt: dt, reducedMotion: reducedMotion)
        }
        return JellySheetFrame(y: y, lift: membrane.d, elapsed: elapsed, detents: detents)
    }

    // MARK: Helpers

    private static func rubber(_ over: Double) -> Double {
        over * M.rubber / (1 + abs(over) / 400)
    }

    /// Lets go of the sheet with speed `speed` towards detent `to`.
    private func release(_ to: Int, speed: Double, byUser: Bool) {
        dragging = false
        v = speed
        if to != detent {
            if byUser { impact.impactOccurred() }
            onDetentChange?(to)
        }
        detent = to
        target = detents[to]
    }

    /// The detent a release at y with speed `speed` lands on.
    private func pick(_ at: Double, _ speed: Double) -> Int {
        let thrown = at + speed * M.throwTime
        var best = 0
        for k in 1..<detents.count where abs(detents[k] - thrown) < abs(detents[best] - thrown) {
            best = k
        }
        // A flick always moves at least one detent its way from where the sheet is now.
        if abs(speed) > M.flick {
            var near = 0
            for k in 1..<detents.count where abs(detents[k] - at) < abs(detents[near] - at) {
                near = k
            }
            let step = speed < 0 ? 1 : -1
            let further = min(max(near + step, 0), detents.count - 1)
            if speed < 0 ? best < further : best > further { best = further }
        }
        return best
    }

    /// Where a drag puts the sheet: with the finger, stretching past the end detents.
    private func follow(_ raw: Double) -> Double {
        let top = detents[2]
        let bottom = detents[0]
        if raw < top { return top - Self.rubber(top - raw) }
        if raw > bottom { return bottom + Self.rubber(raw - bottom) }
        return raw
    }

    // MARK: The frame

    private func advance(dt: Double, reducedMotion: Bool) {
        elapsed += dt
        let width = stage.width

        // The script: a finger drags the sheet towards the next detent and lets go.
        if hold > 0 {
            hold -= dt
            if hold <= 0 { clock = M.resumeAt[detent] }
        } else if looping {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            for (k, move) in M.moves.enumerated() {
                let end = move.at + move.time
                if scripted == k {
                    let u = min(max((t - move.at) / move.time, 0), 1)
                    let span = (detents[move.to] - scriptFrom) * M.dragShare
                    y = scriptFrom + span * (0.4 * u + 0.6 * u * u)
                    dragSpeed = span * (0.4 + 1.2 * u) / move.time
                    fingerX = move.x * width
                    if t >= end || t < before {
                        scripted = -1
                        release(move.to, speed: dragSpeed, byUser: false)
                    }
                } else if scripted < 0 && before < move.at && t >= move.at {
                    scripted = k
                    scriptFrom = y
                    dragging = true
                    fingerX = move.x * width
                }
            }
        }

        // The sheet: with the finger, or on its spring.
        var speed = v
        if dragging {
            speed = dragSpeed
        } else if reducedMotion {
            let next = y + (target - y) * (1 - exp(-dt * 14))
            speed = (next - y) / dt
            y = next
            v = speed
        } else {
            speed = v + (M.stiffness * (target - y) - M.damping * v) * dt
            v = speed
            y += speed * dt
        }

        // The jelly edge.
        if reducedMotion {
            membrane.flatten()
        } else {
            if kickV != 0 {
                for i in 1..<(M.nodes - 1) { membrane.u[i] += kickV * M.gripOf(i, kickX, width) }
                kickV = 0
            }
            let held = dragging
            let stretch = !held ? 0 : y < detents[2] ? detents[2] - y : y > detents[0] ? detents[0] - y : 0
            let v = speed.isFinite ? min(max(speed, -6000), 6000) : 0
            // The sheet's acceleration, smoothed so a jittery finger does not shake the edge.
            let raw = (v - membrane.speed) / dt
            membrane.speed = v
            membrane.accel += (min(max(raw, -40000), 40000) - membrane.accel) * (1 - exp(-dt / M.accelSmooth))
            if !membrane.accel.isFinite { membrane.accel = 0 }
            membrane.step(dt, speed: v, accel: membrane.accel, held: held, fx: fingerX, lift: stretch * M.stretchPull, width: width)
        }
    }

    // MARK: Gestures

    private func touched() {
        hold = M.userHold
        // A scripted drag under way is let go of where it is, and springs back to its detent.
        if scripted >= 0 {
            scripted = -1
            dragging = false
            v = dragSpeed
        }
    }

    /// The top of the sheet's content, which the gestures belong to (the footer sits over the rest).
    private var contentTop: Double { y - min(0, M.liftAt(membrane.d, stage.width / 2, stage.width)) * M.contentGive }

    // One recogniser stands in for the exclusive pan (after 4 pt) and tap (within 10 pt) on the
    // sheet. Touches on the photo above the sheet or on the footer belong to neither.
    func touchChanged(_ value: DragGesture.Value) {
        if tracking == nil {
            let p = value.startLocation
            let inside = p.y >= contentTop && p.y < stage.height - (M.footer + stage.bottom)
            tracking = inside
            panning = false
            if inside {
                touched()
                fingerX = p.x
            }
        }
        guard tracking == true else { return }
        let distance = hypot(value.translation.width, value.translation.height)
        if !panning && distance >= M.panSlop {
            panning = true
            // The sheet is caught wherever it is, mid-spring included.
            dragFrom = y
            dragSpeed = 0
            dragging = true
        }
        if panning {
            y = follow(dragFrom + value.translation.height)
            dragSpeed = value.velocity.height
            fingerX = value.location.x
        }
    }

    func touchEnded(_ value: DragGesture.Value) {
        let inside: Bool
        if let tracking {
            inside = tracking
        } else {
            // A touch lifted on the frame it went down reports no change first.
            let p = value.startLocation
            inside = p.y >= contentTop && p.y < stage.height - (M.footer + stage.bottom)
            if inside { touched() }
        }
        defer {
            tracking = nil
            panning = false
        }
        guard inside else { return }
        if panning {
            release(pick(y, value.velocity.height), speed: value.velocity.height, byUser: true)
            return
        }
        // A tap on the sheet's head opens it a detent; from full it goes back to half.
        let distance = hypot(value.translation.width, value.translation.height)
        guard distance <= M.tapSlop, value.location.y - contentTop <= M.tapHead else { return }
        fingerX = value.location.x
        // The tap sends a ripple out from under the finger as the sheet moves off.
        kickX = value.location.x
        kickV = M.kick
        release(detent == 2 ? 1 : detent + 1, speed: 0, byUser: true)
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum JellySheetPhotos {
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
