import SwiftUI
import UIKit

enum PerspectiveRolodexMetrics {
    // The camera: its focal length in screen widths, and the height (as a share of the screen) it
    // looks along, which is where the middle of the card in front sits.
    static let focal = 2.24
    static let eye = 0.42
    // A card is one unit wide; this is its height and the radius of its corners.
    static let aspect = 0.93
    static let radius = 0.06
    // The card in front stands this far from the camera.
    static let frontZ = 4.0

    // Where a card sits for `d` = its index minus the scroll. 0 is in front; the cards already seen
    // (negative d) have come towards the viewer and down. Y is the height of the card's middle (down
    // is positive), Z its distance, pitch its lean in degrees, positive tipping the top edge towards
    // the viewer. Seen cards either lie back face up, overlapping like cards dealt on a table in
    // front of the viewer (`back`), or tip forward with their top edges towards the lens (`forward`).
    static let nearFrom = -5
    static func near(_ fall: PerspectiveRolodexFall) -> (y: [Double], z: [Double], pitch: [Double]) {
        switch fall {
        case .back:
            return ([1.4, 1.3, 1.16, 1.0, 0.74, 0], [0.95, 1.4, 1.9, 2.45, 3.25, frontZ], [-78, -75, -72, -68, -62, -4])
        case .forward:
            return ([2.6, 1.85, 1.36, 1.04, 0.82, 0], [1.15, 1.55, 1.98, 2.32, 2.62, frontZ], [70, 62, 52, 46, 40, -4])
        }
    }
    // Cards already seen darken as they come nearer, up to a limit, and the bottom of the screen
    // sinks into the background over `bottomFade` pt so the labels stay clear.
    static let nearShade = 0.14
    static let nearShadeMax = 0.45
    static let bottomFade: CGFloat = 240
    // The cards still to come stand in a line that climbs away behind the one in front, each a step
    // higher and further, so only a sliver of each shows above the next.
    static let farY = -0.13
    static let farZ = 4.3
    static let farStepY = -0.19
    static let farStepZ = 0.31
    static let farPitch = -7.0
    // The card falling out of the front stays upright a little longer before it tips: the share of
    // the way, raised to this power, that its lean has turned.
    static let tipLag = 1.8
    // Cards further back darken a little, up to a limit.
    static let farShade = 0.035
    static let farShadeMax = 0.42
    // The range of `d` that cards wrap around in: a card leaving the bottom comes back at the far end.
    static let wrapFrom = -4.5
    // How far, in cards, the far end of the stack fades in.
    static let farFade = 1.5
    // A card is dropped once any corner comes this close to the lens.
    static let nearClip = 0.2

    // Finger travel that moves the stack by one card, as a share of the card in front's height.
    static let dragStep = 0.8
    static let dragSlop: CGFloat = 6
    // The snap after a release: a spring on the scroll position, in cards.
    static let mass = 1.0
    static let stiffness = 170.0
    static let damping = 24.0
    // Seconds of speed a release throws the stack ahead by.
    static let throwTime = 0.18
    static let tapMaxDuration = 0.4

    // The self-playing turn: a card comes forward every `stepEvery` seconds over `stepTime`.
    static let stepEvery = 1.4
    static let stepTime = 0.46
    // After a touch the loop waits this long before it starts again.
    static let userHold = 3.0

    // An open card: where its middle sits as a share of the screen, and its side margin.
    static let openAt = 0.4
    static let openMargin: CGFloat = 20
    static let openDuration = 0.56
    static let openDampingRatio = 0.86
    // While a card opens the rest of the stack parts: the far cards rise, the near ones drop.
    static let openRise = 1.4
    static let openDrop = 1.2
    // A drag down by this share of the screen closes an open card all the way.
    static let dismissShare = 0.45
    static let dismissSpeed = 600.0
    static let dismissKeep = 0.65
    static let openRubber = 0.2
    static let captionRise = 18.0

    // The labels: their size, and how far the middle word rolls per card.
    static let labelFont = Font.custom("HelveticaNeue-Light", fixedSize: 12)
    static let line: CGFloat = 16
    static let imageFade = 0.35

    /// The size, in points, a card's face is laid out at before the perspective maps it: the full
    /// width, so even the open card is drawn from a sharp layer.
    static func cardLayoutWidth(_ screenWidth: CGFloat) -> CGFloat { max(screenWidth, 1) }
}

struct PerspectiveRolodexStage: Equatable {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
}

struct PerspectiveRolodexSettings {
    var count: Int
    var fall: PerspectiveRolodexFall
    var autoPlay: Bool
    var reducedMotion: Bool
    var onOpen: ((Int) -> Void)?
}

/// A card the camera sees: the perspective matrix that maps its face (a hundred units wide, about
/// its middle) onto the screen, row by row for points (x, y, w); its corners there; how faded and
/// how dark it is; and its place in the drawing order.
struct PerspectiveRolodexShown {
    var index: Int
    var z: Double
    var matrix: [Double]
    var quad: [Double]
    var alpha: Double
    var shade: Double
    var order: Double = 0

    /// The matrix as SwiftUI wants it, for a face laid out `side` points wide from its top left.
    /// ProjectionTransform multiplies row vectors, so the column-vector matrix is transposed, after
    /// moving the face's points into the hundred-unit system about its middle.
    func projection(side: CGFloat) -> ProjectionTransform {
        let s = 100 / Double(side)
        let hw = 50.0
        let hh = 50 * PerspectiveRolodexMetrics.aspect
        let a = matrix
        var t = ProjectionTransform()
        t.m11 = a[0] * s
        t.m21 = a[1] * s
        t.m31 = a[2] - hw * a[0] - hh * a[1]
        t.m12 = a[3] * s
        t.m22 = a[4] * s
        t.m32 = a[5] - hw * a[3] - hh * a[4]
        t.m13 = a[6] * s
        t.m23 = a[7] * s
        t.m33 = a[8] - hw * a[6] - hh * a[7]
        return t
    }
}

struct PerspectiveRolodexPhoto {
    var image: UIImage
    /// The block's clock when it arrived, for its fade.
    var at: Double
}

/// One frame to draw.
struct PerspectiveRolodexFrame {
    var stage: PerspectiveRolodexStage
    var scroll: Double
    var open: Double
    var shown: Int
    var elapsed: Double
    /// By card index; nil for a card the camera does not see.
    var cards: [PerspectiveRolodexShown?]
    var photos: [PerspectiveRolodexPhoto?]
}

// MARK: - The path

enum PerspectiveRolodexPath {
    private typealias M = PerspectiveRolodexMetrics

    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }

    /// `x` moved by whole turns of `n` into [from, from + n).
    static func wrap(_ x: Double, _ n: Int, _ from: Double) -> Double {
        x - Double(n) * floor((x - from) / Double(n))
    }

    static func wrapIndex(_ k: Int, _ n: Int) -> Int { ((k % n) + n) % n }

    /// JavaScript's Math.round: halves go up.
    static func round(_ x: Double) -> Double { floor(x + 0.5) }

    static func easeInOut(_ p: Double) -> Double {
        p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
    }

    /// The card's place at a whole step `i` of the path: (y, z, pitch).
    private static func key(_ i: Int, _ fall: PerspectiveRolodexFall) -> (Double, Double, Double) {
        if i <= 0 {
            let k = max(0, i - M.nearFrom)
            let near = M.near(fall)
            return (near.y[k], near.z[k], near.pitch[k])
        }
        return (M.farY + M.farStepY * Double(i - 1), M.farZ + M.farStepZ * Double(i - 1), M.farPitch)
    }

    /// Catmull-Rom through four values, at `t` between the middle two.
    private static func spline(_ a: Double, _ b: Double, _ c: Double, _ d: Double, _ t: Double) -> Double {
        let t2 = t * t
        let t3 = t2 * t
        return 0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (3 * b - a - 3 * c + d) * t3)
    }

    /// Where a card sits for `d`, smoothly between the steps of the path.
    static func place(_ d: Double, _ fall: PerspectiveRolodexFall) -> (y: Double, z: Double, pitch: Double) {
        let i = Int(floor(d))
        let t = d - Double(i)
        let a = key(i - 1, fall)
        let b = key(i, fall)
        let c = key(i + 1, fall)
        let e = key(i + 2, fall)
        var pitch = spline(a.2, b.2, c.2, e.2, t)
        if d > -1 && d < 0 {
            // Falling out of the front: the lean follows the fall late.
            let s = pow(-d, M.tipLag)
            let lean = M.near(fall).pitch
            pitch = lean[-1 - M.nearFrom] * s + lean[-M.nearFrom] * (1 - s)
        }
        return (spline(a.0, b.0, c.0, e.0, t), spline(a.1, b.1, c.1, e.1, t), pitch)
    }

    /// Every card the camera sees, far to near.
    static func frame(
        count: Int, scroll: Double, open: Double, openIndex: Int,
        stage: PerspectiveRolodexStage, fall: PerspectiveRolodexFall
    ) -> [PerspectiveRolodexShown] {
        let width = stage.width
        let height = stage.height
        let f = width * M.focal
        let cx = width / 2
        let cy = height * M.eye
        var shown: [PerspectiveRolodexShown] = []
        let farEnd = M.wrapFrom + Double(count)
        for index in 0..<count {
            let d = wrap(Double(index) - scroll, count, M.wrapFrom)
            let p = place(d, fall)
            var y = p.y
            var z = p.z
            var pitch = p.pitch
            var alpha = clamp01((farEnd - d) / M.farFade)
            var shade = d > 0
                ? min(M.farShadeMax, max(0, d - 0.5) * M.farShade)
                : min(M.nearShadeMax, -d * M.nearShade)
            if open > 0 {
                let o = min(open, 1)
                if index == openIndex {
                    // Upright, nearly the width of the screen, a little above the middle.
                    let openZ = f / (width - Double(M.openMargin) * 2)
                    let openY = ((height * M.openAt - cy) * openZ) / f
                    y += (openY - y) * open
                    z += (openZ - z) * open
                    pitch *= 1 - o
                    shade *= 1 - o
                } else {
                    y += (d > 0 ? -M.openRise : M.openDrop) * o
                    alpha *= max(0, 1 - o * 1.4)
                }
            }
            if alpha <= 0.004 { continue }
            let rad = pitch * .pi / 180
            let s = sin(rad)
            let c = cos(rad)
            let k = 1.0 / 100
            let matrix = [f * k / z, cx * k * s / z, cx, 0, (cy * k * s + f * k * c) / z, cy + f * y / z, 0, k * s / z, 1]
            let hw = 50.0
            let hh = 50 * M.aspect
            var quad: [Double] = []
            var behind = false
            for (u, v) in [(-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh)] {
                let w = matrix[7] * v + 1
                if w * z <= M.nearClip { behind = true }
                quad.append((matrix[0] * u + matrix[1] * v + matrix[2]) / w)
                quad.append((matrix[4] * v + matrix[5]) / w)
            }
            if behind { continue }
            let top = min(quad[1], quad[3], quad[5], quad[7])
            let bottom = max(quad[1], quad[3], quad[5], quad[7])
            if bottom < 0 || top > height { continue }
            // The open card is drawn last, over everything.
            shown.append(PerspectiveRolodexShown(
                index: index,
                z: index == openIndex && open > 0.01 ? 0 : z,
                matrix: matrix,
                quad: quad,
                alpha: alpha,
                shade: shade
            ))
        }
        return shown.sorted { $0.z > $1.z }
    }

    /// Whether a point lies inside a convex quad given corner by corner.
    static func inside(_ quad: [Double], _ x: Double, _ y: Double) -> Bool {
        var sign = 0.0
        for i in 0..<4 {
            let j = (i + 1) % 4
            let cross = (quad[j * 2] - quad[i * 2]) * (y - quad[i * 2 + 1]) - (quad[j * 2 + 1] - quad[i * 2 + 1]) * (x - quad[i * 2])
            if cross == 0 { continue }
            if sign == 0 { sign = cross > 0 ? 1 : -1 } else if (cross > 0 ? 1.0 : -1.0) != sign { return false }
        }
        return true
    }
}

// MARK: - Open spring

/// Reanimated's `withSpring` with a duration and a damping ratio, stepped a frame at a time: the
/// stiffness is solved so the spring's energy falls to the threshold after 1.5 × the duration, and
/// each frame moves along the exact solution from where the last one left it.
struct PerspectiveRolodexSpring {
    let target: Double
    private(set) var velocity: Double
    private let zeta: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, carried: Double, duration: Double, dampingRatio: Double) {
        self.target = target
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
        self.zeta = zeta
        omega0 = sqrt(k)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        initialEnergy = energy(x0, velocity, k)
    }

    /// Moves `value` on by `dt` seconds; true once the spring has come to rest on its target.
    mutating func step(_ value: inout Double, dt: Double) -> Bool {
        if initialEnergy == 0 {
            value = target
            return true
        }
        let x0 = value - target
        let v0 = velocity
        let t = dt
        if zeta < 1 {
            let sin1 = sin(omega1 * t)
            let cos1 = cos(omega1 * t)
            let envelope = exp(-zeta * omega0 * t)
            let frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            value = target + frag
            velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            let envelope = exp(-omega0 * t)
            value = target + envelope * (x0 + (v0 + omega0 * x0) * t)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
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

// MARK: - The engine

/// The stack's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class PerspectiveRolodexEngine {
    private typealias M = PerspectiveRolodexMetrics
    private typealias P = PerspectiveRolodexPath

    private enum OpenMotion {
        case spring(PerspectiveRolodexSpring)
        case timing(from: Double, to: Double, start: Double)
    }

    // `scroll` counts cards that have come forward; it is kept within one turn of the stack.
    private var scroll = 0.0
    private var seen = 0.0
    private var velocity = 0.0
    private var target = 0.0
    private var dragging = false
    private var dragFrom = 0.0
    // 0 shut, 1 open; the card it applies to.
    private var open = 0.0
    private var openIndex = -1
    private var isOpen = false
    private var openFrom = 0.0
    private var openMotion: OpenMotion?
    // What a drag moves: the stack, or the open card.
    private var dragsOpenCard = false
    // A finger, not the loop, moved the stack last; only then does it tick.
    private var byUser = false
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    // The self-playing step under way: where it started, and how long ago (−1 for none).
    private var stepFrom = 0.0
    private var stepT = -1.0
    private var elapsed = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var lastFront: Int?
    // The card whose title is shown while it is open.
    private var shown = 0
    private var photos: [PerspectiveRolodexPhoto?] = []
    private var stage = PerspectiveRolodexStage(width: 0, height: 0, top: 0, bottom: 0)
    // The touch under way.
    private var touchStart: Date?
    private var panning = false

    private let selection = UISelectionFeedbackGenerator()
    private let impact = UIImpactFeedbackGenerator(style: .light)

    func resetPhotos(count: Int) {
        photos = Array(repeating: nil, count: count)
    }

    func photoArrived(_ image: UIImage, at index: Int) {
        guard photos.indices.contains(index) else { return }
        photos[index] = PerspectiveRolodexPhoto(image: image, at: elapsed)
    }

    func step(to date: Date, stage: PerspectiveRolodexStage, settings: PerspectiveRolodexSettings) -> PerspectiveRolodexFrame {
        self.stage = stage
        let count = settings.count
        if photos.count != count { photos = Array(repeating: nil, count: count) }
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
            stepT = -1
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 && count > 0 {
            advance(dt: dt, settings: settings)
        }

        // A selection tick each time another card reaches the front under the user's hand.
        let front = P.wrapIndex(Int(P.round(scroll)), max(count, 1))
        if let before = lastFront, before != front, byUser, open < 0.5 {
            selection.selectionChanged()
        }
        lastFront = front

        var cards: [PerspectiveRolodexShown?] = Array(repeating: nil, count: count)
        if stage.width > 0, count > 0 {
            for (order, card) in P.frame(count: count, scroll: scroll, open: open, openIndex: openIndex, stage: stage, fall: settings.fall).enumerated() {
                var placed = card
                placed.order = Double(order)
                cards[card.index] = placed
            }
        }
        return PerspectiveRolodexFrame(stage: stage, scroll: scroll, open: open, shown: shown, elapsed: elapsed, cards: cards, photos: photos)
    }

    private func advance(dt: Double, settings: PerspectiveRolodexSettings) {
        elapsed += dt
        stepOpen(dt: dt)
        if dragging {
            velocity += ((scroll - seen) / dt - velocity) * 0.5
            seen = scroll
            return
        }
        if hold > 0 {
            // An open card keeps the loop waiting until it is put back.
            if !isOpen { hold -= dt }
            if hold <= 0 { clock = 0 }
        } else if looping && stepT < 0 {
            clock += dt
            if clock >= M.stepEvery {
                clock = 0
                byUser = false
                stepFrom = P.round(target)
                stepT = 0
            }
        }
        if stepT >= 0 {
            let t = stepT + dt
            let p = min(1, t / M.stepTime)
            let next = stepFrom + P.easeInOut(p)
            velocity = (next - scroll) / dt
            scroll = next
            if p >= 1 {
                stepT = -1
                target = stepFrom + 1
                velocity = 0
            } else {
                stepT = t
            }
        } else if settings.reducedMotion {
            scroll = target
            velocity = 0
        } else {
            let v = velocity + ((M.stiffness * (target - scroll) - M.damping * velocity) / M.mass) * dt
            velocity = v
            scroll += v * dt
        }
        // Whole turns of the stack change nothing on screen, so the numbers are kept small.
        if stepT < 0 {
            let count = Double(settings.count)
            let turns = floor(target / count) * count
            if turns != 0 {
                scroll -= turns
                target -= turns
            }
        }
        seen = scroll
    }

    private func stepOpen(dt: Double) {
        guard let motion = openMotion else { return }
        switch motion {
        case .spring(var spring):
            var value = open
            let done = spring.step(&value, dt: dt)
            open = value
            openMotion = done ? nil : .spring(spring)
        case let .timing(from, to, start):
            let p = min(1, max(0, (elapsed - start) / 0.2))
            open = from + (to - from) * Ease.out.value(at: p)
            if p >= 1 { openMotion = nil }
        }
    }

    private func settleOpen(to: Double, speed: Double, settings: PerspectiveRolodexSettings) {
        isOpen = to == 1
        if settings.reducedMotion {
            openMotion = .timing(from: open, to: to, start: elapsed)
            return
        }
        var carried = 0.0
        if case let .spring(running) = openMotion {
            // The same spring asked for again carries on as it was.
            if running.target == to { return }
            carried = running.velocity
        }
        openMotion = .spring(PerspectiveRolodexSpring(
            from: open, to: to, velocity: speed, carried: carried,
            duration: M.openDuration, dampingRatio: M.openDampingRatio
        ))
    }

    private func openFront(_ front: Double, settings: PerspectiveRolodexSettings) {
        target = front
        let index = Int(P.wrap(front, settings.count, 0))
        openIndex = index
        settleOpen(to: 1, speed: 0, settings: settings)
        shown = index
        impact.impactOccurred()
        settings.onOpen?(index)
    }

    private func touched() {
        hold = M.userHold
        clock = 0
        byUser = true
        if stepT >= 0 {
            // A step under way hands its motion to the spring and lands where it was going.
            stepT = -1
            target = stepFrom + 1
        }
    }

    // MARK: Gestures

    func touchMoved(_ value: DragGesture.Value, settings: PerspectiveRolodexSettings) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
        }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.dragSlop else { return }
            panning = true
            panStart()
        }
        panUpdate(translationY: value.translation.height)
    }

    func touchEnded(_ value: DragGesture.Value, settings: PerspectiveRolodexSettings) {
        defer {
            touchStart = nil
            panning = false
        }
        if panning {
            panEnd(velocityY: value.velocity.height, settings: settings)
        } else if let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration {
            tap(at: value.location, settings: settings)
        }
    }

    private var frontHeight: Double { stage.width * M.focal / M.frontZ * M.aspect }
    private var step: Double { max(1, frontHeight * M.dragStep) }
    private var dismiss: Double { max(1, stage.height * M.dismissShare) }

    private func panStart() {
        touched()
        if isOpen || open > 0.5 {
            dragsOpenCard = true
            openFrom = open
            return
        }
        dragsOpenCard = false
        dragFrom = scroll
        seen = scroll
        dragging = true
    }

    private func panUpdate(translationY: Double) {
        if dragsOpenCard {
            let p = openFrom - translationY / dismiss
            openMotion = nil
            open = p > 1 ? 1 + (p - 1) * M.openRubber : max(0, p)
            return
        }
        scroll = dragFrom + translationY / step
    }

    private func panEnd(velocityY: Double, settings: PerspectiveRolodexSettings) {
        if dragsOpenCard {
            let shut = velocityY > M.dismissSpeed || open < M.dismissKeep
            settleOpen(to: shut ? 0 : 1, speed: -velocityY / dismiss, settings: settings)
            return
        }
        dragging = false
        target = P.round(scroll + velocity * M.throwTime)
    }

    private func tap(at point: CGPoint, settings: PerspectiveRolodexSettings) {
        touched()
        if isOpen {
            settleOpen(to: 0, speed: 0, settings: settings)
            return
        }
        let count = settings.count
        guard count > 0, stage.width > 0 else { return }
        // The card under the finger: the nearest one whose outline holds the point.
        let cards = P.frame(count: count, scroll: scroll, open: 0, openIndex: -1, stage: stage, fall: settings.fall)
        for card in cards.reversed() where P.inside(card.quad, point.x, point.y) {
            let d = P.wrap(Double(card.index) - scroll, count, M.wrapFrom)
            // The card that was tapped, even if the stack was still turning under the finger.
            let front = P.round(scroll + d)
            if abs(d) < 0.5 {
                openFront(front, settings: settings)
            } else {
                target = front
            }
            return
        }
    }
}
