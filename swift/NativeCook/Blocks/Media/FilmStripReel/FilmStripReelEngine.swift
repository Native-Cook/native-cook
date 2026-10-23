import SwiftUI
import UIKit

/// One frame of the roll: an Unsplash photo, how it is framed, and what its print says.
struct FilmStripReelFrame {
    enum Shape { case portrait, landscape }
    var id: String
    var shape: Shape
    var title: String
    var line: String

    // A portrait original loses a third of its height to the 3:2 frame, so it needs less width than
    // a landscape one for the same sharpness; both leave enough for the print, ~320 pt wide at 3x.
    var uri: String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(shape == .portrait ? 720 : 900)&q=80"
    }

    private static func p(_ id: String, _ title: String, _ line: String) -> Self { Self(id: "photo-\(id)", shape: .portrait, title: title, line: line) }
    private static func l(_ id: String, _ title: String, _ line: String) -> Self { Self(id: "photo-\(id)", shape: .landscape, title: title, line: line) }

    // A roll shot between the skatepark and the beach, three frames at a time.
    static let samples: [Self] = [
        l("1520045892732-304bc3ac5d8e", "Rail Grind", "Rooftop ledge, golden hour"),
        p("1723236900134-63561e5832b3", "Kickflip", "Blue sky over Marlow Park"),
        p("1597019558926-3eef445fdf60", "Bowl at Dusk", "Concrete bowls, Dune Lane"),
        l("1498330177096-689e3fb901ca", "The Barrel", "Reef break, six feet"),
        l("1529410804717-979530809e26", "Spray", "Offshore wind, Kelp Point"),
        l("1468931467769-06a09c69aad3", "Long Line", "North swell at noon"),
        p("1547447134-cd3f5c716030", "Yellow Wall", "A deck painted by hand"),
        p("1517124792960-0f4701b9bd20", "Deep End", "Pool session, Harlan Street"),
        p("1569135579442-d37b7a0ea74e", "Silhouette", "Last light, Brightwater"),
        l("1454123253751-1fe2b9e0c10d", "The Lip", "Shorebreak at low tide"),
        l("1461503312594-019be44dd599", "Green Room", "Cloud break, early"),
        l("1471079688237-3ac9a55f1d6f", "Glass", "Inside the tube"),
        p("1499083773823-5000fa2b23e4", "Drop In", "Indoor ramp, Unit 9"),
        p("1597769555495-c54a15cd8c3f", "Studio Air", "Kickflip on white"),
        p("1601247309037-a3e434b07906", "Teal Deck", "Flip on the boardwalk"),
        l("1550071510-35e941e4888e", "The Wall", "One rider, Kelp Point"),
        l("1486869801134-25b6bc85fc0d", "Gold Water", "Backlit, at dusk"),
        l("1556316782-1ec9b261c156", "Blue Peak", "Point break, west side"),
        p("1591311337241-cecfd26f1da1", "Crosswalk", "Ollie, midtown"),
        l("1536318431364-5cc762cfc8ec", "Wheels Up", "Rooftop, after the rain"),
        p("1503417680882-163c1609fd2f", "Handrail", "Noon at Pier 4"),
        l("1506988038666-496173f39dea", "Grey Morning", "Overcast, four feet"),
        l("1613450861177-c281f178b920", "Three Up", "Paddling out at sunset"),
        l("1457234068269-b58092525794", "Inside", "Barrel, Little Harbor"),
    ]
}

enum FilmStripReelMetrics {
    static let night: UInt32 = 0x0b0a09
    static let base: UInt32 = 0x1a1512
    static let cream: UInt32 = 0xefe6d8
    static let orange: UInt32 = 0xff7a1f
    static let paper: UInt32 = 0xf5f2eb
    static let printInk: UInt32 = 0x1d1a17
    static let window: UInt32 = 0x0e0b09
    static let emptyLine: UInt32 = 0x2a221c
    // The light table under the strip, brightest under the loupe.
    static let glow: UInt32 = 0xffe6c4

    // The strip crosses the screen at `angle` rad, its middle `stripY` of the way down. A frame is
    // `frameW` of the screen wide at 3:2, frames are `frameGap` pt apart, and the rails above and
    // below are `rail` pt deep: `edge` pt of film, a perforation `perfH` pt tall, then the edge printing.
    static let angle = -0.13
    static let stripY = 0.5
    static let frameW = 0.58
    static let frameGap = 14.0
    static let rail = 34.0
    static let edge = 4.0
    static let perfH = 11.0
    static let perfs = 8
    // The frame under the loupe stands `grow` larger, its neighbours pushed apart to make room.
    static let grow = 0.06
    static let loupe = 0.66
    // Far frames darken by `fadeStep` per frame from the middle, up to `fadeMax`.
    static let fadeStep = 0.26
    static let fadeMax = 0.62

    // Speed, in frames per second, smoothed over ~1/`smooth` s: it shears the strip by `shear` per
    // frame/s, bows its ends up by `bow` pt per frame/s, and blurs the frames along it by `blur` pt per frame/s.
    static let smooth = 12.0
    static let shear = 0.012
    static let shearMax = 0.16
    static let bow = 1.4
    static let bowMax = 18.0
    static let blur = 0.95
    static let blurMax = 11.0

    // Dragging winds the strip with the finger; on release it is thrown `project` s ahead at its
    // speed (never more than `throwMax` frames) and springs onto the nearest frame.
    static let project = 0.32
    static let throwMax = 9.0
    static let snapSpring = (duration: 0.7, dampingRatio: 0.86)

    // The print: `printMargin` pt from the screen's sides, a `border` pt white border and `caption`
    // pt of paper under the photo for its caption, turned `printTilt` rad, lifted on `liftSpring`;
    // the strip behind dims by `dim` and shrinks by `shrink`.
    static let printMargin = 26.0
    static let border = 12.0
    static let caption = 66.0
    static let printTilt = 0.03
    static let printY = 0.47
    static let liftSpring = (duration: 0.62, dampingRatio: 0.74)
    static let dropSpring = (duration: 0.52, dampingRatio: 0.92)
    static let dim = 0.66
    static let shrink = 0.04
    static let shadowSigma = 16.0

    // autoPlay: two runs of `wind` frames, each taking `windTime` s on an ease-in-out, each followed
    // by a print that rises and drops back; `loop` s in all, so the roll comes round to where it began.
    static let loop = 14.0
    static let wind = 12.0
    static let windTime = 2.2
    static let runs = [0.5, 7.0]
    static let lifts = [3.2, 9.7]
    static let drops = [5.6, 12.1]
    static let userHold = 3.0
    static let imageFade = 0.4
    static let panSlop: CGFloat = 6
    static let tapMaxDuration = 0.32

    static func clamp(_ value: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, value)) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// `value` wrapped into -n/2 … n/2, so the roll has no ends.
    static func wrap(_ value: Double, _ n: Int) -> Double {
        let n = Double(n)
        return value - n * floor(value / n + 0.5)
    }

    static func easeInOut(_ t: Double) -> Double {
        let c = clamp(t, 0, 1)
        return c < 0.5 ? 16 * c * c * c * c * c : 1 - pow(-2 * c + 2, 5) / 2
    }

    /// Reanimated's default timing curve, ease in and out (quadratic).
    static func quadInOut(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2 }

    /// How far the script has wound the strip at `t`, in frames.
    static func scriptWound(_ t: Double) -> Double {
        runs.reduce(0) { $0 + wind * easeInOut((t - $1) / windTime) }
    }
}

/// Where a frame stands on screen: its middle, turn, scale and shear.
struct FilmStripReelPose {
    var x: Double
    var y: Double
    var angle: Double
    var scale: Double
    var shear: Double

    private typealias M = FilmStripReelMetrics

    /// A frame `d` frames from the middle of the strip: where it stands, along a strip that bows with speed.
    init(d: Double, pitch: Double, speed: Double, cx: Double, cy: Double, motion: Double) {
        let near = max(0, 1 - abs(d))
        scale = 1 + M.grow * near
        let push = M.grow * pitch / 2
        let sign: Double = d > 0 ? 1 : d < 0 ? -1 : 0
        let along = d * pitch + sign * push * min(abs(d), 1)
        // The ends lift as the strip runs, bending it into a shallow smile.
        let bow = M.clamp(abs(speed) * M.bow, 0, M.bowMax) * motion
        let reach = pitch * 1.6
        let across = -bow * (along / reach) * (along / reach)
        let tangent = atan(-2 * bow * along / (reach * reach))
        let c = cos(M.angle)
        let s = sin(M.angle)
        x = cx + along * c - across * s
        y = cy + along * s + across * c
        angle = M.angle + tangent
        shear = M.clamp(-speed * M.shear, -M.shearMax, M.shearMax) * motion
    }
}

struct FilmStripReelPhoto {
    var image: UIImage
    /// The block's clock when it arrived, for its fade.
    var at: Double
}

/// One frame to draw.
struct FilmStripReelScene {
    var width: Double
    var height: Double
    var now: Double
    var photos: [FilmStripReelPhoto?]
    var offset: Double
    var speed: Double
    var lift: Double
    var lifted: Int
}

struct FilmStripReelSettings {
    var count: Int
    var autoPlay: Bool
    var reducedMotion: Bool
    var onPrint: ((Int) -> Void)?
}

/// The roll's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class FilmStripReelEngine {
    private typealias M = FilmStripReelMetrics

    private var width = 0.0
    private var height = 0.0
    private var count = 0
    private var now = 0.0
    private var photos: [FilmStripReelPhoto?] = []
    // How far the roll is wound, in frames (frame i sits in the middle when it equals i), and its
    // smoothed speed in frames per second.
    private var offset = FilmStripReelValue(0)
    private var lastOffset = 0.0
    private var speed = 0.0
    // The print: how far it is lifted (0 in the strip, 1 a print), and which frame it is.
    private var lift = FilmStripReelValue(0)
    private var lifted = -1
    private var printing = false
    private var dragFrom = 0.0
    // The self-playing loop: its clock, and the offset it winds from.
    private var looping = false
    private var clock = 0.0
    private var base = 0.0
    private var hold = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    // The touch under way.
    private var touchStart: Date?
    private var touchOrigin = CGPoint.zero
    private var panning = false

    private let selection = UISelectionFeedbackGenerator()
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let light = UIImpactFeedbackGenerator(style: .light)

    private var pitch: Double { width * M.frameW + M.frameGap }
    private var frameH: Double { width * M.frameW * 2 / 3 }

    // MARK: Photos

    func resetPhotos(count: Int) {
        photos = Array(repeating: nil, count: count)
    }

    func photoArrived(_ image: UIImage, at index: Int) {
        guard photos.indices.contains(index) else { return }
        photos[index] = FilmStripReelPhoto(image: image, at: now)
    }

    // MARK: Frame

    func step(to date: Date, width: Double, height: Double, settings: FilmStripReelSettings) -> FilmStripReelScene {
        self.width = width
        self.height = height
        count = settings.count
        if photos.count != count { photos = Array(repeating: nil, count: count) }
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 && width > 0 { advance(dt: dt, settings: settings) }
        return FilmStripReelScene(width: width, height: height, now: now, photos: photos, offset: offset.value, speed: speed, lift: lift.value, lifted: lifted)
    }

    private func advance(dt: Double, settings: FilmStripReelSettings) {
        now += dt
        offset.step(dt: dt, now: now)
        lift.step(dt: dt, now: now)

        if hold > 0 {
            hold -= dt
            if hold <= 0 {
                // Pick the loop up where the strip stands: at rest, or holding a print.
                let at = offset.value.rounded(.toNearestOrAwayFromZero)
                clock = printing ? 4.4 : 0
                base = printing ? at - M.wind : at
            }
        } else if looping {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            // The loop winds two dozen frames in all, so it ends where it began, a whole roll on.
            if t < before { base += M.wind * Double(M.runs.count) }
            offset.set(base + M.scriptWound(t))
            func crossed(_ at: Double) -> Bool {
                (before < at && t >= at) || (t < before && (at > before || at <= t))
            }
            for at in M.lifts where crossed(at) { raise(user: false, settings: settings) }
            for at in M.drops where crossed(at) { drop(user: false, settings: settings) }
        }

        // Speed, wrapped so the roll coming round does not count as a jump.
        let o = offset.value
        let moved = M.wrap(o - lastOffset, count) / dt
        lastOffset = o
        speed = M.mix(speed, moved, 1 - exp(-M.smooth * dt))
    }

    /// The frame in the middle of the strip.
    private var centred: Int {
        // `% 0` and Int(NaN) both trap.
        guard count > 0, offset.value.isFinite else { return 0 }
        let r = Int(offset.value.rounded(.toNearestOrAwayFromZero)) % count
        return (r + count) % count
    }

    private func raise(user: Bool, settings: FilmStripReelSettings) {
        let index = centred
        lifted = index
        printing = true
        if settings.reducedMotion {
            lift.timing(to: 1, duration: 0.24, curve: M.quadInOut, now: now)
        } else {
            lift.spring(to: 1, duration: M.liftSpring.duration, dampingRatio: M.liftSpring.dampingRatio)
        }
        if user { medium.impactOccurred() }
        settings.onPrint?(index)
    }

    private func drop(user: Bool, settings: FilmStripReelSettings) {
        printing = false
        if settings.reducedMotion {
            lift.timing(to: 0, duration: 0.2, curve: M.quadInOut, now: now)
        } else {
            lift.spring(to: 0, duration: M.dropSpring.duration, dampingRatio: M.dropSpring.dampingRatio)
        }
        if user { light.impactOccurred() }
    }

    private func touched() {
        hold = M.userHold
    }

    // MARK: Gestures

    /// One recogniser stands in for the race of a pan (after 6 pt of travel) and a tap (a touch
    /// lifted within 320 ms that never travelled that far).
    func touchMoved(_ value: DragGesture.Value, settings: FilmStripReelSettings) {
        if touchStart == nil || value.startLocation != touchOrigin {
            touchStart = value.time
            touchOrigin = value.startLocation
            panning = false
        }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.panSlop else { return }
            panning = true
            touched()
            if printing { drop(user: true, settings: settings) }
            offset.stop()
            dragFrom = offset.value
        }
        // Only the drag along the strip winds it.
        let along = value.translation.width * cos(M.angle) + value.translation.height * sin(M.angle)
        offset.set(dragFrom - along / pitch)
    }

    func touchEnded(_ value: DragGesture.Value, settings: FilmStripReelSettings) {
        defer {
            touchStart = nil
            panning = false
        }
        if panning {
            touched()
            let velocity = -(value.velocity.width * cos(M.angle) + value.velocity.height * sin(M.angle)) / pitch
            let to = (offset.value + M.clamp(velocity * M.project, -M.throwMax, M.throwMax)).rounded(.toNearestOrAwayFromZero)
            offset.spring(to: to, duration: M.snapSpring.duration, dampingRatio: M.snapSpring.dampingRatio, velocity: velocity)
            selection.selectionChanged()
            return
        }
        guard let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration, width > 0 else { return }
        tap(at: value.location, settings: settings)
    }

    private func tap(at point: CGPoint, settings: FilmStripReelSettings) {
        touched()
        if printing {
            drop(user: true, settings: settings)
            return
        }
        // Where the tap falls along and across the strip.
        let dx = point.x - width / 2
        let dy = point.y - height * M.stripY
        let along = dx * cos(M.angle) + dy * sin(M.angle)
        let across = -dx * sin(M.angle) + dy * cos(M.angle)
        if abs(across) > frameH / 2 + M.rail { return }
        let steps = (along / pitch).rounded(.toNearestOrAwayFromZero)
        let o = offset.value
        if steps == 0 && abs(o - o.rounded(.toNearestOrAwayFromZero)) < 0.08 {
            raise(user: true, settings: settings)
        } else {
            // A frame off to the side winds to the middle.
            offset.stop()
            offset.spring(to: o.rounded(.toNearestOrAwayFromZero) + steps, duration: M.snapSpring.duration, dampingRatio: M.snapSpring.dampingRatio)
            selection.selectionChanged()
        }
    }
}

// MARK: - Photos

/// Downloads a photo once and decodes it off the main thread, so the first frame that draws it does not stall.
enum FilmStripReelPhotos {
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

// MARK: - Motion

/// Reanimated's `withSpring` with a duration and a damping ratio, stepped a frame at a time: the
/// stiffness is solved so the spring's energy falls to the threshold after 1.5 × the duration, and
/// each frame moves along the exact solution from where the last one left it.
struct FilmStripReelSpring {
    let target: Double
    private(set) var velocity: Double
    private let dampingRatio: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, carried: Double, duration: Double, dampingRatio: Double) {
        self.target = target
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
struct FilmStripReelValue {
    private enum Motion {
        case spring(FilmStripReelSpring)
        case timing(from: Double, to: Double, start: Double, duration: Double, curve: (Double) -> Double)
    }

    private(set) var value: Double
    private var motion: Motion?

    init(_ value: Double) {
        self.value = value
    }

    mutating func set(_ value: Double) {
        self.value = value
        motion = nil
    }

    /// Stops whatever is moving the value, where it is.
    mutating func stop() {
        motion = nil
    }

    mutating func spring(to target: Double, duration: Double, dampingRatio: Double, velocity: Double = 0) {
        var carried = 0.0
        if case let .spring(running) = motion { carried = running.velocity }
        motion = .spring(FilmStripReelSpring(
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
