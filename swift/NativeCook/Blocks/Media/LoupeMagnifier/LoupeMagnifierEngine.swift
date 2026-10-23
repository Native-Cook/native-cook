import QuartzCore
import SwiftUI
import UIKit

enum LoupeMagnifierMetrics {
    // The glass: its radius, the width of its metal rim (pt), and its zoom range.
    static let radius = 92.0
    static let rim = 13.0
    static let zoom = 2.5
    static let minZoom = 1.5
    static let maxZoom = 5.0
    // Barrel distortion: at the edge of the glass the photo is spread by this much more than in the
    // middle; the colours split by up to `fringe` there, red outwards and blue inwards.
    static let barrel = 0.32
    static let fringe = 0.04
    // The photo outside the glass is dimmed to this.
    static let dim = 0.66
    // The loupe trails the finger on a spring (unit mass), and a release throws it `throwAhead` s ahead.
    static let stiffness = 300.0
    static let damping = 25.0
    static let throwAhead = 0.1
    static let edgeBand = 60.0
    // The loupe's middle stays this share of its radius inside the screen's edges.
    static let edgeKeep = 0.62
    // Its highlight slides against its speed, up to `tiltMax` of the radius, and turns with it.
    static let tiltPerSpeed = 0.00012
    static let tiltMax = 0.16
    // A tap elsewhere jumps the loupe there on an arc, lifting it towards the viewer on the way.
    static let jumpTime = 0.52
    static let jumpArc = 0.35
    static let jumpLift = 0.08
    // A tap on the glass toggles between the resting zoom and a close look.
    static let closeZoom = 4.0
    static let zoomStiffness = 220.0
    static let zoomDamping = 22.0
    // The loupe's shadow: offset and softness at rest and lifted.
    static let shadow = [8.0, 14.0, 18.0]
    static let shadowLifted = [18.0, 30.0, 34.0]
    static let panSlop = 2.0
    static let tapMaxDuration = 0.3

    // The self-playing wander: the loupe glides from stop to stop on a smooth curve (`travel` s),
    // zooming out a little on the way, and rests on each one (`dwell` s). One of the moves is a jump.
    static let travel = 1.5
    static let dwell = 1.1
    static let travelZoom = 2.1
    static let jumpLeg = 2
    // After a touch the wander waits this long (s) before it starts again.
    static let userHold = 3.5
    static let imageFade = 0.6
}

enum LoupeMagnifierMath {
    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func easeInOut(_ p: Double) -> Double { p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2 }

    static func rubber(_ x: Double, _ lo: Double, _ hi: Double, _ band: Double) -> Double {
        if x < lo { return lo - band * (1 - 1 / ((lo - x) / band + 1)) }
        if x > hi { return hi + band * (1 - 1 / ((x - hi) / band + 1)) }
        return x
    }

    /// Catmull-Rom through four values, at `t` between the middle two.
    static func spline(_ a: Double, _ b: Double, _ c: Double, _ d: Double, _ t: Double) -> Double {
        let t2 = t * t
        let t3 = t2 * t
        return 0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (3 * b - a - 3 * c + d) * t3)
    }

    /// A point on a quadratic arc from a to b, bowed up by `bow` of their distance.
    static func arc(_ ax: Double, _ ay: Double, _ bx: Double, _ by: Double, _ bow: Double, _ t: Double) -> (Double, Double) {
        let cx = (ax + bx) / 2
        let cy = (ay + by) / 2 - hypot(bx - ax, by - ay) * bow
        let u = 1 - t
        return (u * u * ax + 2 * u * t * cx + t * t * bx, u * u * ay + 2 * u * t * cy + t * t * by)
    }

    /// Where the wander is at `t` seconds into the loop, in shares of the screen, with its zoom and
    /// how lifted it is. Each stop is a dwell then a move to the next stop.
    static func wander(_ stops: [LoupeMagnifierStop], _ t: Double) -> (x: Double, y: Double, zoom: Double, lift: Double, jump: Bool) {
        typealias M = LoupeMagnifierMetrics
        let n = stops.count
        guard n > 0, t.isFinite else { return (0.5, 0.5, M.zoom, 0, false) }
        let leg = M.travel + M.dwell
        let i = ((Int(floor(t / leg)) % n) + n) % n
        let local = t - floor(t / leg) * leg
        let a = stops[i]
        if local < M.dwell { return (a.x, a.y, a.zoom, 0, false) }
        let p = (local - M.dwell) / M.travel
        let e = easeInOut(p)
        let b = stops[(i + 1) % n]
        // Zoom eases out towards `travelZoom` half way, then in to the next stop's.
        let zoom = p < 0.5 ? a.zoom + (M.travelZoom - a.zoom) * easeInOut(p * 2) : M.travelZoom + (b.zoom - M.travelZoom) * easeInOut(p * 2 - 1)
        if i == M.jumpLeg {
            let q = arc(a.x, a.y, b.x, b.y, M.jumpArc, e)
            return (q.0, q.1, zoom, sin(Double.pi * p), true)
        }
        let z = stops[(i - 1 + n) % n]
        let c = stops[(i + 2) % n]
        return (spline(z.x, a.x, b.x, c.x, e), spline(z.y, a.y, b.y, c.y, e), zoom, 0, false)
    }
}

/// Where the wander stops, as shares of the screen, and the zoom at each stop.
struct LoupeMagnifierStop {
    var x: Double
    var y: Double
    var zoom: Double
}

struct LoupeMagnifierStage: Equatable {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
}

struct LoupeMagnifierSettings {
    var stops: [LoupeMagnifierStop]
    /// autoPlay, and not under reduced motion.
    var auto: Bool
    var reducedMotion: Bool
}

/// One frame to draw.
struct LoupeMagnifierFrame {
    var stage: LoupeMagnifierStage
    var x: Double
    var y: Double
    var zoom: Double
    var lift: Double
    var tiltX: Double
    var tiltY: Double
    /// How far the photo has faded in, 0 before it arrives.
    var shown: Double
    var image: UIImage?
}

/// The loupe's state, stepped once a frame like the RN frame callback, and a stand-in for the
/// React Native gestures: a tap racing a pan (on the fingers' average) and a pinch run together.
@MainActor
final class LoupeMagnifierEngine {
    private typealias M = LoupeMagnifierMetrics
    private typealias P = LoupeMagnifierMath

    // The loupe's middle, where it is heading, and its speed (pt, pt/s).
    private var x = 0.0
    private var y = 0.0
    private var vx = 0.0
    private var vy = 0.0
    private var tx = 0.0
    private var ty = 0.0
    private var placed = false
    private var grabX = 0.0
    private var grabY = 0.0
    private var dragging = false
    // Zoom on a spring, and the pinch's starting zoom.
    private var zoom = M.zoom
    private var zoomSpeed = 0.0
    private var zoomTo = M.zoom
    private var zoomFrom = M.zoom
    // A jump under way: from, to, seconds since it began (−1 for none); and how lifted the loupe is.
    private var jump = (0.0, 0.0, 0.0, 0.0)
    private var jumpT = -1.0
    private var lift = 0.0
    // The highlight's slide, smoothed.
    private var tiltX = 0.0
    private var tiltY = 0.0
    private var script = 0.0
    private var hold = 0.0
    private var elapsed = 0.0
    private var image: UIImage?
    private var imageAt = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var stage = LoupeMagnifierStage(width: 0, height: 0, top: 0, bottom: 0)
    private var settings: LoupeMagnifierSettings?

    private let selection = UISelectionFeedbackGenerator()
    private let impact = UIImpactFeedbackGenerator(style: .light)

    func photoArrived(_ image: UIImage) {
        self.image = image
        imageAt = elapsed
    }

    func step(to date: Date, stage room: LoupeMagnifierStage, settings: LoupeMagnifierSettings) -> LoupeMagnifierFrame {
        self.settings = settings
        stage = room
        if lastAuto != settings.auto {
            lastAuto = settings.auto
            script = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 0.016
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 { advance(dt: dt, settings: settings) }
        return LoupeMagnifierFrame(
            stage: stage, x: x, y: y, zoom: zoom, lift: lift, tiltX: tiltX, tiltY: tiltY,
            shown: image == nil ? 0 : P.clamp01((elapsed - imageAt) / M.imageFade),
            image: image
        )
    }

    /// Keeps the loupe's middle on the screen.
    private func inside(_ px: Double, _ py: Double) -> (Double, Double) {
        let m = M.radius * M.edgeKeep
        return (max(m, min(stage.width - m, px)), max(stage.top + m, min(stage.height - stage.bottom - m, py)))
    }

    private func touched() {
        hold = M.userHold
    }

    private func advance(dt: Double, settings: LoupeMagnifierSettings) {
        let reduced = settings.reducedMotion
        let auto = settings.auto
        // An empty route (never in the samples) falls back to the sample one.
        let stops = settings.stops.isEmpty ? LoupeMagnifierStop.samples : settings.stops
        elapsed += dt
        let room = stage
        if room.width <= 0 { return }
        if !placed {
            // Start resting on the first stop.
            x = stops[0].x * room.width
            y = stops[0].y * room.height
            tx = x
            ty = y
            zoom = auto ? stops[0].zoom : M.zoom
            zoomTo = zoom
            placed = true
        }

        var lifted = 0.0
        if auto && hold <= 0 && !dragging {
            let t = (script + dt).truncatingRemainder(dividingBy: Double(stops.count) * (M.travel + M.dwell))
            script = t
            let w = P.wander(stops, t)
            tx = w.x * room.width
            ty = w.y * room.height
            zoomTo = w.zoom
            if w.jump {
                // The jump leg flies on its own arc, not the spring.
                x = tx
                y = ty
                vx = 0
                vy = 0
                lifted = w.lift
            }
        } else if hold > 0 && !dragging {
            hold -= dt
            if hold <= 0 { script = 0 }
        }

        if jumpT >= 0 {
            let t = jumpT + dt
            let p = P.clamp01(t / M.jumpTime)
            let q = P.arc(jump.0, jump.1, jump.2, jump.3, M.jumpArc, P.easeInOut(p))
            x = q.0
            y = q.1
            lifted = sin(Double.pi * p)
            if p >= 1 {
                jumpT = -1
                tx = jump.2
                ty = jump.3
                vx = 0
                vy = 0
                // Stepped from the timeline's body: the haptic plays once the frame is done.
                DispatchQueue.main.async { [weak self] in self?.impact.impactOccurred() }
            } else {
                jumpT = t
            }
        } else if !(auto && hold <= 0 && lifted > 0) {
            if reduced {
                x = tx
                y = ty
                vx = 0
                vy = 0
            } else {
                // Long frames are split in two: this stiff spring is not stable over a 64 ms step
                // (it would oscillate out to infinity at a sustained 15 fps). At 60 fps nothing changes.
                let steps = dt > 0.034 ? 2 : 1
                let h = dt / Double(steps)
                for _ in 0..<steps {
                    vx += (M.stiffness * (tx - x) - M.damping * vx) * h
                    vy += (M.stiffness * (ty - y) - M.damping * vy) * h
                    x += vx * h
                    y += vy * h
                }
            }
        }
        lift = lifted

        // The zoom springs to its target; each whole step it passes under a pinch ticks.
        let before = zoom
        zoomSpeed += (M.zoomStiffness * (zoomTo - zoom) - M.zoomDamping * zoomSpeed) * dt
        zoom = reduced ? zoomTo : zoom + zoomSpeed * dt

        // The highlight slides against the loupe's speed.
        let ax = reduced ? 0 : max(-M.tiltMax, min(M.tiltMax, -vx * M.tiltPerSpeed * 10))
        let ay = reduced ? 0 : max(-M.tiltMax, min(M.tiltMax, -vy * M.tiltPerSpeed * 10))
        let ease = 1 - exp(-dt / 0.1)
        tiltX += (ax - tiltX) * ease
        tiltY += (ay - tiltY) * ease
        if dragging && floor(before) != floor(zoom) {
            DispatchQueue.main.async { [weak self] in self?.selection.selectionChanged() }
        }
    }

    // MARK: Touches

    private var fingers: [(id: Int, point: CGPoint)] = []
    private var downTime = 0.0
    private var origin = CGPoint.zero
    private var offset = CGPoint.zero
    private var samples: [(t: Double, x: Double, y: Double)] = []
    private var panActive = false
    private var pinchActive = false
    private var tapFailed = false
    private var pinchDistance = 1.0

    private var centroid: CGPoint {
        guard !fingers.isEmpty else { return .zero }
        var sx = 0.0, sy = 0.0
        for f in fingers {
            sx += f.point.x
            sy += f.point.y
        }
        return CGPoint(x: sx / Double(fingers.count), y: sy / Double(fingers.count))
    }

    /// The pan's position: the fingers' average, kept from jumping when a finger comes or goes.
    private var focus: CGPoint {
        let c = centroid
        return CGPoint(x: c.x + offset.x, y: c.y + offset.y)
    }

    private var velocity: (Double, Double) {
        guard let first = samples.first, let last = samples.last, last.t - first.t > 0.001 else { return (0, 0) }
        return ((last.x - first.x) / (last.t - first.t), (last.y - first.y) / (last.t - first.t))
    }

    private func sample() {
        let now = CACurrentMediaTime()
        let f = focus
        samples.append((now, f.x, f.y))
        samples.removeAll { now - $0.t > 0.1 }
    }

    private func rebase(_ change: () -> Void) {
        let before = focus
        change()
        let c = centroid
        offset = CGPoint(x: before.x - c.x, y: before.y - c.y)
        samples.removeAll()
        sample()
    }

    private var spread: Double {
        guard fingers.count >= 2 else { return 1 }
        return max(1, hypot(fingers[1].point.x - fingers[0].point.x, fingers[1].point.y - fingers[0].point.y))
    }

    func touchesBegan(_ touches: [(Int, CGPoint)]) {
        for (id, point) in touches {
            if fingers.isEmpty {
                fingers = [(id, point)]
                downTime = CACurrentMediaTime()
                origin = point
                offset = .zero
                samples.removeAll()
                sample()
                panActive = false
                pinchActive = false
                tapFailed = false
            } else {
                rebase { fingers.append((id, point)) }
            }
        }
        if fingers.count >= 2 && !pinchActive {
            // The pinch begins (and the tap can no longer win).
            tapFailed = true
            pinchActive = true
            touched()
            dragging = true
            zoomFrom = zoomTo
            pinchDistance = spread
        }
    }

    func touchesMoved(_ touches: [(Int, CGPoint)]) {
        for (id, point) in touches {
            if let at = fingers.firstIndex(where: { $0.id == id }) { fingers[at].point = point }
        }
        guard !fingers.isEmpty else { return }
        sample()
        let f = focus
        if !panActive && hypot(f.x - origin.x, f.y - origin.y) >= M.panSlop {
            panActive = true
            tapFailed = true
            touched()
            jumpT = -1
            dragging = true
            // The loupe keeps its distance from the finger, so a finger below it does not hide it.
            grabX = tx - f.x
            grabY = ty - f.y
            if hypot(x - tx, y - ty) > M.radius {
                grabX = x - f.x
                grabY = y - f.y
            }
        }
        if panActive {
            let m = M.radius * M.edgeKeep
            tx = P.rubber(f.x + grabX, m, stage.width - m, M.edgeBand)
            ty = P.rubber(f.y + grabY, stage.top + m, stage.height - stage.bottom - m, M.edgeBand)
        }
        if pinchActive && fingers.count >= 2 {
            zoomTo = P.rubber(zoomFrom * spread / pinchDistance, M.minZoom, M.maxZoom, 0.6)
        }
    }

    func touchesEnded(_ touches: [(Int, CGPoint)]) {
        let v = velocity
        for (id, point) in touches {
            if let at = fingers.firstIndex(where: { $0.id == id }) { fingers[at].point = point }
        }
        let ids = Set(touches.map(\.0))
        let last = fingers.allSatisfy { ids.contains($0.id) }
        if !last {
            rebase { fingers.removeAll { ids.contains($0.id) } }
        }
        if pinchActive && (last || fingers.count < 2) {
            pinchActive = false
            zoomTo = max(M.minZoom, min(M.maxZoom, zoomTo))
            dragging = false
            touched()
        }
        guard last else { return }
        let point = fingers.first?.point ?? .zero
        fingers.removeAll()
        if panActive {
            let reduced = settings?.reducedMotion ?? false
            let thrown = inside(tx + (reduced ? 0 : v.0 * M.throwAhead), ty + (reduced ? 0 : v.1 * M.throwAhead))
            tx = thrown.0
            ty = thrown.1
            dragging = false
            touched()
            panActive = false
            return
        }
        if !tapFailed && CACurrentMediaTime() - downTime <= M.tapMaxDuration {
            tap(at: point)
        }
    }

    /// Every finger is gone without an end (the recogniser was reset with touches down): the loupe
    /// is let go where it is heading and the zoom settles inside its range.
    func touchesLost() {
        guard !fingers.isEmpty || panActive || pinchActive || dragging else { return }
        fingers.removeAll()
        samples.removeAll()
        if pinchActive { zoomTo = max(M.minZoom, min(M.maxZoom, zoomTo)) }
        if panActive { (tx, ty) = inside(tx, ty) }
        panActive = false
        pinchActive = false
        tapFailed = true
        dragging = false
        touched()
    }

    private func tap(at point: CGPoint) {
        touched()
        if hypot(point.x - x, point.y - y) < M.radius + M.rim {
            // On the glass: a closer look, or back.
            zoomTo = zoomTo < (M.zoom + M.closeZoom) / 2 ? M.closeZoom : M.zoom
            selection.selectionChanged()
            return
        }
        let to = inside(point.x, point.y)
        if settings?.reducedMotion == true {
            x = to.0
            y = to.1
            tx = to.0
            ty = to.1
            return
        }
        jump = (x, y, to.0, to.1)
        jumpT = 0
    }
}
