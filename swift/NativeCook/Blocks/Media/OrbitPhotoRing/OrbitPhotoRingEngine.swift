import SwiftUI
import UIKit

enum OrbitPhotoRingMetrics {
    // Length of the self-playing loop, in seconds. It starts and ends on the first camera, with the
    // ring turned by exactly one whole turn, so one period records as a seamless loop.
    static let loop = 16.0
    // Script of the loop: two flicks (time in s, release strength in radians of throw) and three
    // camera changes (time in s, cameras forward from the one the loop started on).
    static let flicks: [(at: Double, by: Double)] = [(1.8, 2.2), (10.4, -1.3)]
    static let switches: [(at: Double, ahead: Int)] = [(4.6, 1), (8.2, 2), (12.6, 0)]
    // A scripted flick: the finger speeds up over `flickDrag` s, lets go, and the ring coasts down
    // with a time constant of `coast` s. A user's throw coasts the same way.
    static let flickDrag = 0.28
    static let coast = 0.7
    // The idle spin, in rad/s, chosen so that the loop adds up to exactly one turn.
    static let idleSpin: Double = {
        var thrown = 0.0
        for flick in flicks { thrown += flick.by + flick.by * flickDrag / (2 * coast) }
        return (Double.pi * 2 - thrown) / loop
    }()
    // After a touch the loop waits this long, in seconds, before it plays again.
    static let userHold = 3.5

    // The scene, designed on a 402 pt wide screen and scaled to the real width. A pinhole camera
    // with a focal length of `focal` pt looks at the body standing `depth` pt away.
    static let designWidth = 402.0
    static let focal = 700.0
    static let depth = 700.0
    // The middle of the body, as a share of the height between the safe areas.
    static let bodyAt = 0.41
    // The body rests turned a little to show its right side, and seen a little from above (degrees).
    static let restYaw = 28.0
    static let restPitch = 15.0
    static let restRoll = -2.0
    // How far the body turns with the ring's speed (degrees) and the speed that turns it ~76% of that.
    static let yawGain = 26.0
    static let yawSpeed = 2.4
    // Springs on the body's turn and tilt: stiffness and damping per second, unit mass.
    static let turnStiffness = 90.0
    static let turnDamping = 11.0
    // Vertical drags tilt the body and the ring, rubber-banded: degrees per point, and the travel
    // at which the band has taken half.
    static let tiltPerPt = 0.11
    static let tiltBand = 140.0
    // The body bobs by `bob` pt, four times a loop.
    static let bob = 3.0

    // The ring styles, each a vector of numbers so that one morphs into another: the ring's
    // radius, how far below the body's middle its centre sits, its tilt towards the viewer and its
    // roll in the picture plane (degrees); the prints' width and how far they turn towards the
    // outside at the sides (degrees); the body's scale; how much the body tumbles (0…1); how far
    // the prints split into two rings rolled ±`gyroRoll`° (0…1); which way the second ring spins; how
    // far the prints lean with the ring's roll instead of each at its own angle (0…1); and how far
    // the body and its ring sit above the usual place (pt).
    static let sRadius = 0
    static let sDrop = 1
    static let sTilt = 2
    static let sRoll = 3
    static let sPrint = 4
    static let sTurn = 5
    static let sBody = 6
    static let sTumble = 7
    static let sSplit = 8
    static let sDir = 9
    static let sLean = 10
    static let sRise = 11
    static let styleShapes: [[Double]] = [
        // Ring: a wide, gently tilted halo around a camera at rest.
        [206, 14, 21, -9, 74, 28, 1, 0, 0, 1, 0, 0],
        // Tumble: a glossy toy camera turning round with a wide ring of polaroids. The ring is sunk
        // below the body and its front edge raised, so the front prints sweep across the lens while
        // the back ones pass low under the body; it is rolled so its left side hangs lower than its
        // right, and every print leans with it. The whole scene sits higher to leave the ring room below.
        [200, 155, -20, -16, 76, 40, 1.18, 1, 0, 1, 1, 72],
        // Gyro: two rings of five crossing like a gyroscope, spinning against each other.
        [188, 6, 64, 0, 66, 18, 0.88, 0, 1, -1, 0, 0],
    ]
    static let tumbleStyle = 1
    static let gyroRoll = 34.0
    // A change of style morphs every number over `styleTime` s on an ease-in-out curve.
    static let styleTime = 0.9
    // Tumbling, the camera and its ring turn round together, the front of both travelling left to
    // right: whole turns over these spans of the loop (start s, end s) on an ease-in-out curve,
    // between the camera changes; between them the ring rests and the body only sways `tumbleSway`°
    // four times a loop. The body turns about an axis leaning back and to the side, so halfway round
    // it shows its base. The turns are locked to the clock, so a period records as a seamless loop.
    static let tumbleTurns: [(start: Double, end: Double)] = [(0.4, 4.2), (9.7, 12.5)]
    static let tumbleAxis = [0.1, 1, 0.24]
    // After a touch the body comes back round to face the viewer at this rate (1/s).
    static let tumbleHome = 2.6
    static let tumbleSway = 5.0
    // The toy faces the viewer more squarely than the box camera: its rest turn is this much less (°).
    static let toyYaw = 20.0
    // Each print floats by `printFloat` pt, twice a loop, and has its own roll.
    static let printFloat = 4.0
    static let printRolls: [Double] = [-8, 6, -12, 4, 10, -5, 8, -10, 3, -6, 9, -3]
    // Prints behind the body darken up to `backShade` and blur up to `backBlur` px.
    static let backShade = 0.62
    static let backBlur = 3.2
    // Finger travel follows the front prints. Throws are capped, in rad/s, and a throw faster than
    // `flickTick` rad/s ends with a light haptic.
    static let maxSpin = 12.0
    static let flickTick = 2.0
    static let dragSlop: CGFloat = 6
    static let tapMaxDuration = 0.4

    // A camera change: the body spins once (`spinTime` s), morphing into the next between 30% and
    // 70% of the turn while it dips; the prints fly off (outward and down, staggered) and the new
    // ones are shot out of the lens to their places, one after another.
    static let spinTime = 1.1
    static let spinDip = 0.14
    static let spinLift = 18.0
    static let outTime = 0.42
    static let outStagger = 0.025
    static let inDelay = 0.35
    static let inStagger = 0.07
    static let inTime = 0.75
    // A new change waits until the deal is done.
    static let dealTime = inDelay + inStagger * 9 + inTime
    // Tapping the body fires the shutter: the glass flashes for `flashTime` s and the body jumps back.
    static let flashTime = 0.45
    static let shutterPunch = 0.035

    // The bar of camera thumbnails: spacing, thumbnail width, and its middle above the safe area.
    static let barSpacing = 82.0
    static let thumbWidth = 46.0
    static let barAbove = 64.0
    static let barStiffness = 200.0
    static let barDamping = 24.0
    // A drag that starts this close to the bar's middle line scrolls the bar instead of spinning the ring (pt).
    static let barTouch = 44.0
    // The bar scrolls freely: a flick coasts down with a time constant of `barCoast` s, flicks are
    // capped at `barMaxSpeed` cameras per second, and it may be pulled `barRubber` cameras past either
    // end before it springs back.
    static let barCoast = 0.33
    static let barMaxSpeed = 30.0
    static let barRubber = 0.45

    static let leatherGrain = 0.42
    static let imageFade = 0.35
    // Light comes from the upper left, in front.
    static let light: [Double] = {
        let l = [-0.45, -0.75, -0.55]
        let n = (l[0] * l[0] + l[1] * l[1] + l[2] * l[2]).squareRoot()
        return [l[0] / n, l[1] / n, l[2] / n]
    }()
}

// MARK: - Cameras as numbers

/// A camera flattened to numbers, so two of them can be morphed into each other. Indices into it:
enum OrbitPhotoRingVector {
    static let w = 0
    static let h = 1
    static let d = 2
    static let humpW = 3
    static let humpH = 4
    static let humpD = 5
    static let lensR = 6
    static let lensL = 7
    static let lensX = 8
    static let lensY = 9
    static let windows = 10
    static let plate = 11
    static let leather = 12
    static let metal = 15
    static let barrel = 18
    static let accent = 21
    static let glass = 24
    static let shell = 27
    static let toyBody = 30

    static func make(_ c: OrbitPhotoRingCamera) -> [Double] {
        func rgb(_ hex: UInt32) -> [Double] {
            [Double((hex >> 16) & 255) / 255, Double((hex >> 8) & 255) / 255, Double(hex & 255) / 255]
        }
        return c.body + c.hump + c.lens + c.lensAt + [c.windows, c.plate]
            + rgb(c.leather) + rgb(c.metal) + rgb(c.barrel) + rgb(c.accent) + rgb(c.glass)
            + rgb(c.toyShell ?? c.metal) + rgb(c.toyBody ?? c.leather)
    }

    static func mix(_ a: [Double], _ b: [Double], _ t: Double) -> [Double] {
        var out = a
        for i in 0..<a.count { out[i] = a[i] + (b[i] - a[i]) * t }
        return out
    }
}

// MARK: - Geometry

/// The eye: focal length and the point on screen it looks through.
struct OrbitPhotoRingView3 {
    var f: Double
    var cx: Double
    var cy: Double
}

/// Where an object stands, its scale and its rotation (3×3, row-major).
struct OrbitPhotoRingPose {
    var x: Double
    var y: Double
    var z: Double
    var s: Double
    var r: [Double]
}

/// A flat face seen by the eye: the perspective matrix that draws it (row-major, a point (u, v)
/// of its drawing lands at ((m0 u + m1 v + m2) / w, (m3 u + m4 v + m5) / w), w = m6 u + m7 v + 1),
/// whether it faces the eye, and how much light it takes.
struct OrbitPhotoRingFace {
    var m: [Double]?
    var visible: Bool
    var light: Double
}

enum OrbitPhotoRingMath {
    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func easeInOut(_ p: Double) -> Double { p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2 }
    static func easeOut(_ p: Double) -> Double { 1 - pow(1 - p, 3) }
    static func easeIn(_ p: Double) -> Double { p * p * p }
    static func backOut(_ p: Double) -> Double {
        let c = 1.5
        let q = p - 1
        return 1 + (c + 1) * q * q * q + c * q * q
    }
    static func smooth(_ p: Double) -> Double { p * p * (3 - 2 * p) }
    /// JavaScript's Math.round: halves go up.
    static func round(_ x: Double) -> Double { floor(x + 0.5) }
    static func sign(_ x: Double) -> Double { x > 0 ? 1 : x < 0 ? -1 : 0 }

    /// Rubber band: follows `x` at first, then gives less and less, never passing `band`.
    static func rubber(_ x: Double, _ band: Double) -> Double {
        sign(x) * band * (1 - 1 / (abs(x) / band + 1))
    }

    /// The loop's turn of the ring at `t` seconds into it, beyond where it started.
    static func flickTurn(_ t: Double, at: Double, by throwBy: Double) -> Double {
        typealias M = OrbitPhotoRingMetrics
        if t <= at { return 0 }
        let drag = throwBy * M.flickDrag / (2 * M.coast)
        if t < at + M.flickDrag {
            let s = (t - at) / M.flickDrag
            return drag * s * s
        }
        return drag + throwBy * (1 - exp(-(t - at - M.flickDrag) / M.coast))
    }

    static func scriptTurn(_ t: Double, reduced: Bool, style: Int) -> Double {
        if reduced { return 0 }
        if style == OrbitPhotoRingMetrics.tumbleStyle {
            var turned = 0.0
            for turn in OrbitPhotoRingMetrics.tumbleTurns {
                turned += Double.pi * 2 * easeInOut(clamp01((t - turn.start) / (turn.end - turn.start)))
            }
            return turned
        }
        var turn = OrbitPhotoRingMetrics.idleSpin * t
        for flick in OrbitPhotoRingMetrics.flicks { turn += flickTurn(t, at: flick.at, by: flick.by) }
        return turn
    }

    /// 3×3 row-major product.
    static func mul3(_ a: [Double], _ b: [Double]) -> [Double] {
        var out = [Double](repeating: 0, count: 9)
        for r in 0..<3 {
            for c in 0..<3 {
                out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
            }
        }
        return out
    }

    /// Rotation of an object: rolled in the picture plane, turned about the vertical (positive
    /// shows its right side) and tipped about the horizontal (positive shows its top). Radians; the
    /// screen's y points down and z away from the viewer.
    static func rotation(_ yaw: Double, _ pitch: Double, _ roll: Double) -> [Double] {
        let cr = cos(roll), sr = sin(roll)
        let cy = cos(yaw), sy = sin(yaw)
        let cp = cos(pitch), sp = sin(pitch)
        let rz = [cr, -sr, 0, sr, cr, 0, 0, 0, 1]
        let ry = [cy, 0, sy, 0, 1, 0, -sy, 0, cy]
        let rx = [1, 0, 0, 0, cp, -sp, 0, sp, cp]
        return mul3(rx, mul3(ry, rz))
    }

    static func turn(_ m: [Double], _ x: Double, _ y: Double, _ z: Double) -> [Double] {
        [m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z]
    }

    /// A point of an object, from its own coordinates to the eye's.
    static func place(_ pose: OrbitPhotoRingPose, _ x: Double, _ y: Double, _ z: Double) -> [Double] {
        let p = turn(pose.r, x * pose.s, y * pose.s, z * pose.s)
        return [pose.x + p[0], pose.y + p[1], pose.z + p[2]]
    }

    /// The perspective matrix that draws a flat face: a point (u, v) of the face's own drawing
    /// lands where the eye sees O + u·U + v·V. nil when the face is behind the eye.
    static func faceMatrix(_ view: OrbitPhotoRingView3, _ o: [Double], _ u: [Double], _ v: [Double]) -> [Double]? {
        let z = o[2]
        if z <= 1 { return nil }
        let f = view.f, cx = view.cx, cy = view.cy
        return [
            (f * u[0] + cx * u[2]) / z,
            (f * v[0] + cx * v[2]) / z,
            (f * o[0] + cx * o[2]) / z,
            (f * u[1] + cy * u[2]) / z,
            (f * v[1] + cy * v[2]) / z,
            (f * o[1] + cy * o[2]) / z,
            u[2] / z,
            v[2] / z,
            1,
        ]
    }

    /// A face of an object: centre c, its drawing's axes u and v, its outward normal n.
    static func face(_ view: OrbitPhotoRingView3, _ pose: OrbitPhotoRingPose, _ c: [Double], _ u: [Double], _ v: [Double], _ n: [Double]) -> OrbitPhotoRingFace {
        let o = place(pose, c[0], c[1], c[2])
        let uu = turn(pose.r, u[0] * pose.s, u[1] * pose.s, u[2] * pose.s)
        let vv = turn(pose.r, v[0] * pose.s, v[1] * pose.s, v[2] * pose.s)
        let nn = turn(pose.r, n[0], n[1], n[2])
        let facing = nn[0] * o[0] + nn[1] * o[1] + nn[2] * o[2]
        let l = OrbitPhotoRingMetrics.light
        let lit = max(0, nn[0] * l[0] + nn[1] * l[1] + nn[2] * l[2])
        return OrbitPhotoRingFace(m: faceMatrix(view, o, uu, vv), visible: facing < 0, light: 0.3 + 0.7 * lit)
    }

    /// Where slot `theta` of a ring (radius, tilt and roll in degrees, drop) is, around the body's middle.
    static func ringPoint(_ theta: Double, _ radius: Double, _ tilt: Double, _ roll: Double, _ drop: Double) -> [Double] {
        let x = radius * sin(theta)
        let z = -radius * cos(theta)
        let t = tilt * .pi / 180
        let y1 = -z * sin(t)
        let z1 = z * cos(t)
        let r = roll * .pi / 180
        return [x * cos(r) - y1 * sin(r), x * sin(r) + y1 * cos(r) + drop, z1]
    }

    /// The style vector `t` of the way from `from` to style `to`.
    static func styleMix(_ from: [Double], _ to: Int, _ t: Double) -> [Double] {
        let shapes = OrbitPhotoRingMetrics.styleShapes
        let shape = shapes[min(shapes.count - 1, max(0, to))]
        let e = easeInOut(clamp01(t))
        var out = shape
        for i in shape.indices {
            let a = from.indices.contains(i) ? from[i] : shape[i]
            out[i] = a + (shape[i] - a) * e
        }
        return out
    }

    /// An angle brought into (−π, π].
    static func wrapPi(_ a: Double) -> Double {
        let t = Double.pi * 2
        return a - t * round(a / t)
    }

    /// Finger travel per radian of turn: the speed of the front prints on screen.
    static func travelPerRadian(_ radius: Double, _ width: Double) -> Double {
        typealias M = OrbitPhotoRingMetrics
        return max(1, radius * M.focal / max(1, M.depth - radius) * (width / M.designWidth))
    }
}

// MARK: - Frame

struct OrbitPhotoRingStage: Equatable {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
}

struct OrbitPhotoRingSettings {
    var cameras: [OrbitPhotoRingCamera]
    var vectors: [[Double]]
    var autoPlay: Bool
    /// The style the block opens with, an index into `styleShapes`.
    var initialStyle: Int
    var reducedMotion: Bool
    var onSelect: ((Int) -> Void)?
}

/// A print in the ring, or flying in or out of it, as the eye sees it.
struct OrbitPhotoRingPrint {
    var set: Int
    var index: Int
    var z: Double
    var matrix: [Double]
    var facing: Bool
    var alpha: Double
    var shade: Double
    var blur: Double
    var order: Double = 0
    var key: Int { self.set * 1000 + index }
}

struct OrbitPhotoRingPhoto {
    var image: UIImage
    /// The block's clock when it arrived, for its fade.
    var at: Double
}

/// One frame to draw.
struct OrbitPhotoRingFrame {
    var stage: OrbitPhotoRingStage
    var view: OrbitPhotoRingView3
    var pose: OrbitPhotoRingPose
    var body: [Double]
    var label: String
    var flash: Double
    /// How much of the toy camera shows (the Tumble style), 0…1.
    var toy: Double
    var prints: [OrbitPhotoRingPrint]
    /// Where the body sits in the drawing order among the prints.
    var bodyOrder: Double
    var from: Int
    var to: Int
    var since: Double
    var bar: Double
    /// The highlight in the bar: follows the chosen camera, never the scroll.
    var pick: Double
    var elapsed: Double
    var photos: [[OrbitPhotoRingPhoto?]]
}

// MARK: - The engine

/// The ring's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class OrbitPhotoRingEngine {
    private typealias M = OrbitPhotoRingMetrics
    private typealias P = OrbitPhotoRingMath

    // The ring's turn (rad) and spin (rad/s).
    private var angle = 0.0
    private var spin = 0.0
    private var dragging = false
    private var dragFrom = 0.0
    private var dragX = 0.0
    private var dragY = 0.0
    // A drag along the bar scrolls it: whether the drag is on the bar, and where the bar was when it began.
    private var barDrag = false
    private var barFrom = 0.0
    // The body's turn and tilt, on springs (degrees, degrees per second).
    private var yaw = M.restYaw
    private var yawSpeed = 0.0
    private var tilt = 0.0
    private var tiltSpeed = 0.0
    // The camera shown, the one before it, and seconds since the change.
    private var from = 0
    private var to = 0
    private var since = 100.0
    private var shutter = 100.0
    // The bar's scroll position, in cameras, and its speed. It coasts where a flick leaves it, or
    // glides to a chosen camera while `barFollow` is set; the highlight follows the chosen camera on
    // its own spring (scrolling never chooses).
    private var bar = 0.0
    private var barSpeed = 0.0
    private var barTarget = 0.0
    private var barFollow = true
    private var pick = 0.0
    private var pickSpeed = 0.0
    // The loop: its clock, the turn it started from, the camera it started on; and the pause a touch makes.
    private var clock = 0.0
    private var script = 0.0
    private var scriptFrom = 0.0
    private var scriptCamera = 0
    private var scriptStyle = 0
    private var hold = 0.0
    // Tumbling, the body turns round with the ring: by the ring's turn beyond `camHome` (rad).
    private var camHome = 0.0
    private var elapsed = 0.0
    // The ring style: the numbers it is morphing from, the style it is morphing to, and seconds since.
    private var styleReady = false
    private var styleFrom = M.styleShapes[0]
    private var styleTo = 0
    private var styleSince = M.styleTime
    // The second ring's own turn (rad).
    private var angleB = 0.0
    private var radiusNow = M.styleShapes[0][M.sRadius]
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var photos: [[OrbitPhotoRingPhoto?]] = []
    private var stage = OrbitPhotoRingStage(width: 0, height: 0, top: 0, bottom: 0)
    // The touch under way.
    private var touchStart: Date?
    private var panning = false

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private let selection = UISelectionFeedbackGenerator()

    func selectionTick() {
        selection.selectionChanged()
    }

    /// Takes the style the block opens with, once.
    private func prepareStyle(_ settings: OrbitPhotoRingSettings) {
        if styleReady { return }
        styleReady = true
        let first = min(M.styleShapes.count - 1, max(0, settings.initialStyle))
        styleFrom = M.styleShapes[first]
        styleTo = first
        scriptStyle = first
        styleSince = M.styleTime
        radiusNow = M.styleShapes[first][M.sRadius]
    }

    /// Morphs to style `index` from wherever the current morph is.
    func chooseStyle(_ index: Int, settings: OrbitPhotoRingSettings) {
        prepareStyle(settings)
        if index == styleTo || index < 0 || index >= M.styleShapes.count { return }
        styleFrom = P.styleMix(styleFrom, styleTo, styleSince / M.styleTime)
        styleTo = index
        styleSince = 0
    }

    func resetPhotos(_ cameras: [OrbitPhotoRingCamera]) {
        photos = cameras.map { Array(repeating: nil, count: $0.photos.count) }
    }

    func photoArrived(_ image: UIImage, camera: Int, index: Int) {
        guard photos.indices.contains(camera), photos[camera].indices.contains(index) else { return }
        photos[camera][index] = OrbitPhotoRingPhoto(image: image, at: elapsed)
    }


    func step(to date: Date, stage: OrbitPhotoRingStage, settings: OrbitPhotoRingSettings) -> OrbitPhotoRingFrame {
        self.stage = stage
        let count = settings.cameras.count
        prepareStyle(settings)
        if photos.count != count { resetPhotos(settings.cameras) }
        // A shorter camera list from the caller never leaves the body pointing past its end.
        if from >= count || to >= count {
            from = 0
            to = 0
            scriptCamera = 0
        }
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            hold = 0
            script = 0
            scriptFrom = angle
            scriptCamera = to
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 0.016
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 { advance(dt: dt, settings: settings) }
        return frame(settings: settings)
    }

    private func advance(dt: Double, settings: OrbitPhotoRingSettings) {
        let reduced = settings.reducedMotion
        elapsed += dt
        clock = (clock + dt).truncatingRemainder(dividingBy: M.loop)
        since += dt
        shutter += dt
        styleSince += dt
        let shape = P.styleMix(styleFrom, styleTo, styleSince / M.styleTime)
        radiusNow = shape[M.sRadius]
        let perRadian = P.travelPerRadian(shape[M.sRadius], stage.width)
        // Tumbling, the ring rests between its turns instead of drifting.
        let idle = reduced ? 0 : M.idleSpin * (1 - shape[M.sTumble])
        let before = angle

        if dragging && !barDrag {
            angle = dragFrom + dragX / perRadian
            spin += ((angle - before) / dt - spin) * 0.35
        } else {
            if hold > 0 {
                hold -= dt
                if hold <= 0 {
                    // The loop starts again from wherever the ring and the camera are.
                    script = 0
                    scriptFrom = angle
                    scriptCamera = to
                }
            }
            if scriptStyle != styleTo {
                // The loop's turn changes with the style; the ring carries on from where it is.
                scriptFrom += P.scriptTurn(script, reduced: reduced, style: scriptStyle) - P.scriptTurn(script, reduced: reduced, style: styleTo)
                scriptStyle = styleTo
            }
            if settings.autoPlay && hold <= 0 {
                let was = script
                var now = was + dt
                if now >= M.loop {
                    scriptFrom += P.scriptTurn(M.loop, reduced: reduced, style: scriptStyle)
                    now -= M.loop
                }
                for change in M.switches {
                    let at = change.at
                    let crossed = now >= was ? (was < at && now >= at) : (was < at || now >= at)
                    if crossed { choose((scriptCamera + change.ahead) % settings.cameras.count, byUser: false, settings: settings) }
                }
                script = now
                angle = scriptFrom + P.scriptTurn(now, reduced: reduced, style: scriptStyle)
                spin = (angle - before) / dt
            } else if reduced {
                spin = 0
            } else {
                // A throw coasts down to the idle spin.
                let s = idle + (spin - idle) * exp(-dt / M.coast)
                spin = s
                angle = before + s * dt
            }
        }

        // The second ring follows the first, the way its style spins it, and is pulled onto its place:
        // the first ring's turn when the prints make one ring, its mirror image when they make two
        // (mirrored, a print of one ring never meets a print of the other where the rings cross).
        let moved = angle - before
        // Tumbling, the body turns with the ring while the loop plays or a finger holds it; otherwise
        // it comes back round to face the viewer (in the other styles it is always home).
        let ringHeld = dragging && !barDrag
        let scripted = settings.autoPlay && hold <= 0
        if styleTo != M.tumbleStyle { camHome += moved }
        if styleTo != M.tumbleStyle || (!scripted && !ringHeld) {
            camHome += P.wrapPi(angle - camHome) * (1 - exp(-dt * M.tumbleHome))
        }
        let mirror = M.styleShapes[styleTo][M.sDir] < 0 ? -1.0 : 1.0
        var b = angleB + moved * shape[M.sDir]
        b += P.wrapPi(mirror * angle - b) * min(1, dt * 4)
        angleB = b

        // The body turns with the ring's spin and tilts with a vertical drag, both on springs.
        let turnTo = M.restYaw + (reduced ? 0 : (1 - shape[M.sTumble]) * M.yawGain * tanh((spin - idle) / M.yawSpeed))
        yawSpeed += (M.turnStiffness * (turnTo - yaw) - M.turnDamping * yawSpeed) * dt
        yaw += yawSpeed * dt
        let tiltTo = dragging && !barDrag ? P.rubber(dragY * M.tiltPerPt, M.tiltBand * M.tiltPerPt) : 0
        tiltSpeed += (M.turnStiffness * (tiltTo - tilt) - M.turnDamping * tiltSpeed) * dt
        tilt += tiltSpeed * dt
        if barDrag && dragging {
            // The bar follows the finger, rubber-banding past its first and last camera.
            let k = stage.width / M.designWidth
            let raw = barFrom - dragX / (M.barSpacing * k)
            let last = Double(settings.cameras.count - 1)
            let at = raw < 0 ? P.rubber(raw, M.barRubber) : raw > last ? last + P.rubber(raw - last, M.barRubber) : raw
            barSpeed = (at - bar) / dt
            bar = at
        } else if barFollow {
            barSpeed += (M.barStiffness * (barTarget - bar) - M.barDamping * barSpeed) * dt
            bar += barSpeed * dt
        } else {
            // Left alone, the bar coasts down; past an end it springs back to it.
            let last = Double(settings.cameras.count - 1)
            let edge = min(max(bar, 0), last)
            if edge != bar {
                barSpeed += (M.barStiffness * (edge - bar) - M.barDamping * barSpeed) * dt
            } else {
                barSpeed *= exp(-dt / M.barCoast)
            }
            bar += barSpeed * dt
        }
        pickSpeed += (M.barStiffness * (Double(to) - pick) - M.barDamping * pickSpeed) * dt
        pick += pickSpeed * dt
    }

    /// Starts a camera change, unless one is still being dealt.
    private func choose(_ index: Int, byUser: Bool, settings: OrbitPhotoRingSettings) {
        if index < 0 || index >= settings.cameras.count { return }
        if index == to {
            // Tapping the chosen camera only brings it back to the middle of the bar.
            barTarget = Double(index)
            barFollow = true
            return
        }
        if since < M.dealTime { return }
        from = to
        to = index
        barTarget = Double(index)
        barFollow = true
        since = 0
        if byUser {
            medium.impactOccurred()
            settings.onSelect?(index)
        }
    }

    private func touched() {
        hold = M.userHold
    }

    // MARK: Picture

    private func frame(settings: OrbitPhotoRingSettings) -> OrbitPhotoRingFrame {
        let reduced = settings.reducedMotion
        let room = stage
        let k = room.width / M.designWidth
        let shape = P.styleMix(styleFrom, styleTo, styleSince / M.styleTime)
        let cy = room.top + (room.height - room.top - room.bottom) * M.bodyAt - shape[M.sRise] * k
        let view = OrbitPhotoRingView3(f: M.focal * k, cx: room.width / 2, cy: cy)
        // How much of the toy camera shows, and how much it tumbles (none under reduced motion).
        let toy = shape[M.sTumble]
        let tumbling = reduced ? 0 : toy

        // The camera: the morph between the last body and this one, the spin and the dip.
        let t = since
        let change = P.clamp01(t / M.spinTime)
        let morph = P.smooth(P.clamp01((change - 0.3) / 0.4))
        let body = OrbitPhotoRingVector.mix(settings.vectors[from], settings.vectors[to], morph)
        let dip = sin(Double.pi * change)
        // Tumbling, the change spins the body the way its ring turns.
        let spinWay = M.styleShapes[styleTo][M.sTumble] > 0.5 ? -1.0 : 1.0
        let turning = reduced ? 0 : spinWay * 360 * P.easeInOut(change)
        let flashAge = shutter / M.flashTime
        let flash = flashAge < 1 ? 1 - flashAge : 0
        let punch = flashAge < 1 ? M.shutterPunch * sin(Double.pi * min(1, flashAge * 2)) : 0
        let bob = reduced ? 0 : M.bob * sin(clock / M.loop * .pi * 8)
        let pitch = M.restPitch + tilt
        let pose = OrbitPhotoRingPose(
            x: 0,
            y: bob - (reduced ? 0 : M.spinLift * dip),
            z: M.depth,
            s: shape[M.sBody] * (1 - (reduced ? 0 : M.spinDip) * dip) * (1 - punch),
            r: P.mul3(
                OrbitPhotoRingToy.tumbleMatrix(clock, turned: angle - camHome, weight: tumbling),
                P.rotation((yaw - M.toyYaw * toy + turning) * .pi / 180, pitch * .pi / 180, M.restRoll * .pi / 180)
            )
        )
        typealias V = OrbitPhotoRingVector
        let lensTip = P.place(pose, body[V.lensX], body[V.lensY], -body[V.d] / 2 - body[V.lensL])

        // The prints: the ones flying off and the ones being dealt, then the ring at rest.
        var prints: [OrbitPhotoRingPrint] = []
        let radius = shape[M.sRadius]
        let ringTilt = shape[M.sTilt] + tilt * 0.6
        let split = shape[M.sSplit]
        let turnNow = angle
        let turnB = angleB
        let sets = from != to && t < M.dealTime ? [from, to] : [to]
        for set in sets {
            let outgoing = set != to
            let n = settings.cameras[set].photos.count
            for i in 0..<n {
                // Odd prints make the second ring when the style splits them.
                let second = i % 2 == 1
                let theta = (second ? turnB : turnNow) + Double(i) / Double(n) * .pi * 2
                let ringRoll = shape[M.sRoll] + (second ? -1 : 1) * split * M.gyroRoll
                let slot = P.ringPoint(theta, radius, ringTilt, ringRoll, shape[M.sDrop])
                let float = reduced ? 0 : M.printFloat * sin(clock / M.loop * .pi * 4 + Double(i) * 1.7)
                var x = slot[0]
                var y = slot[1] + float + pose.y
                var z = slot[2] + M.depth
                var s = shape[M.sPrint]
                var alpha = 1.0
                let lean = shape[M.sLean]
                var roll = (M.printRolls[i % M.printRolls.count] + sin(theta) * 6) * (1 - 0.75 * lean) + ringRoll * (split * 0.5 + lean * 1.15)
                var yawP = -sin(theta) * shape[M.sTurn]
                if outgoing {
                    let q = P.clamp01((t - Double(i) * M.outStagger) / M.outTime)
                    if q >= 1 { continue }
                    let e = reduced ? 0 : P.easeIn(q)
                    x += slot[0] * 0.9 * e
                    y += 170 * e
                    z += (slot[2] > 0 ? 0.4 : 0.9) * slot[2] * e
                    roll += 40 * e * (i % 2 == 0 ? 1 : -1)
                    s *= 1 + 0.15 * e
                    alpha = 1 - (reduced ? q : P.smooth(q))
                } else if t < M.dealTime && sets.count == 2 {
                    let q = P.clamp01((t - M.inDelay - Double(i) * M.inStagger) / M.inTime)
                    if q <= 0 { continue }
                    if reduced {
                        alpha = P.smooth(q)
                    } else {
                        let e = P.easeOut(q)
                        x = lensTip[0] + (x - lensTip[0]) * e
                        y = lensTip[1] + (y - lensTip[1]) * e
                        z = lensTip[2] - 30 * (1 - e) + (z - lensTip[2]) * e
                        s *= 0.25 + 0.75 * P.backOut(q)
                        roll -= 160 * (1 - e)
                        yawP *= e
                        alpha = P.clamp01(q * 4)
                    }
                }
                let depth = P.clamp01((z - (M.depth - radius * 0.3)) / (radius * 1.3))
                let r = P.rotation(yawP * .pi / 180, (min(ringTilt, 34) * 0.5 + pitch * 0.2) * .pi / 180, roll * .pi / 180)
                let unit = s / 100
                let o = [x, y, z]
                let u = P.turn(r, unit, 0, 0)
                let v = P.turn(r, 0, unit, 0)
                let normal = P.turn(r, 0, 0, -1)
                guard let m = P.faceMatrix(view, o, u, v) else { continue }
                prints.append(OrbitPhotoRingPrint(
                    set: set,
                    index: i,
                    z: z,
                    matrix: m,
                    facing: normal[0] * o[0] + normal[1] * o[1] + normal[2] * o[2] < 0,
                    alpha: alpha,
                    // Leaning prints pass low under the body in sight, so they stay sharper there.
                    shade: depth * M.backShade * (1 - 0.3 * shape[M.sLean]),
                    blur: reduced ? 0 : depth * M.backBlur * (1 - 0.6 * shape[M.sLean])
                ))
            }
        }
        prints.sort { $0.z > $1.z }
        // The body is drawn just before the first print in front of it. Tumbling, a print passes behind
        // the body once it is beyond the body's front face rather than its middle, so the prints coming
        // round the sides slip behind its edges instead of over them.
        let bodyHalf = (body[V.d] + (max(body[V.d], body[V.w] * 0.42) - body[V.d]) * toy) / 2 * pose.s
        let bodyLine = M.depth - bodyHalf * 0.8 * toy
        var bodyOrder = Double(prints.count)
        for i in prints.indices {
            if bodyOrder == Double(prints.count) && prints[i].z < bodyLine { bodyOrder = Double(i) }
            prints[i].order = Double(i) + (Double(i) >= bodyOrder ? 1 : 0)
        }

        return OrbitPhotoRingFrame(
            stage: room,
            view: view,
            pose: pose,
            body: body,
            label: settings.cameras[morph < 0.5 ? from : to].name,
            flash: flash,
            toy: toy,
            prints: prints,
            bodyOrder: bodyOrder,
            from: from,
            to: to,
            since: t,
            bar: bar,
            pick: pick,
            elapsed: elapsed,
            photos: photos
        )
    }

    // MARK: Gestures

    // One recogniser stands in for the race of a pan (after 6 pt of travel) and a tap (a touch
    // lifted within 400 ms that never travelled that far).
    func touchMoved(_ value: DragGesture.Value, settings: OrbitPhotoRingSettings) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
            barDrag = abs(value.startLocation.y - (stage.height - stage.bottom - M.barAbove)) < M.barTouch
            if barDrag {
                // A finger on the bar catches it.
                barFollow = false
                barSpeed = 0
            }
        }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.dragSlop else { return }
            panning = true
            touched()
            dragFrom = angle
            barFrom = bar
            dragging = true
        }
        dragX = value.translation.width
        dragY = value.translation.height
    }

    func touchEnded(_ value: DragGesture.Value, settings: OrbitPhotoRingSettings) {
        defer {
            touchStart = nil
            panning = false
            dragging = false
            barDrag = false
        }
        if panning && barDrag {
            // The bar coasts on from the flick and stops wherever it runs out. Scrolling only moves
            // the bar: a camera is chosen by tapping it.
            touched()
            let k = stage.width / M.designWidth
            let speed = -value.velocity.width / (M.barSpacing * k)
            barSpeed = settings.reducedMotion ? 0 : max(-M.barMaxSpeed, min(M.barMaxSpeed, speed))
            barFollow = false
        } else if panning {
            touched()
            let perRadian = P.travelPerRadian(radiusNow, stage.width)
            let thrown = settings.reducedMotion ? 0 : max(-M.maxSpin, min(M.maxSpin, value.velocity.width / perRadian))
            spin = thrown
            if abs(thrown) > M.flickTick { light.impactOccurred() }
        } else if let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration {
            tap(at: value.location, settings: settings)
        }
    }

    private func tap(at point: CGPoint, settings: OrbitPhotoRingSettings) {
        touched()
        let room = stage
        let k = room.width / M.designWidth
        let barY = room.height - room.bottom - M.barAbove
        if abs(point.y - barY) < 44 {
            let index = Int(P.round(bar + (point.x - room.width / 2) / (M.barSpacing * k)))
            choose(index, byUser: true, settings: settings)
            return
        }
        let bodyY = room.top + (room.height - room.top - room.bottom) * M.bodyAt - M.styleShapes[styleTo][M.sRise] * k
        if hypot(point.x - room.width / 2, point.y - bodyY) < 120 * k * M.styleShapes[styleTo][M.sBody] {
            shutter = 0
            rigid.impactOccurred()
        }
    }
}
