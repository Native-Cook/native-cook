import SwiftUI
import UIKit

enum CubeStoryViewerMetrics {
    // Each photo is up for `photo` seconds while its bar fills; then the next one shows.
    static let photo = 2.0
    // The cube turns a quarter turn to the next person in `turn` seconds on an ease-in-out curve.
    static let turn = 0.9
    static let turnReduced = 0.35
    static let easeTurn = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.65, y: 0),
        endControlPoint: UnitPoint(x: 0.35, y: 1)
    )
    // Lens distance as a share of the screen width: the faces swing through real perspective.
    static let perspective = 2.2
    // A face turned a full quarter away is this dark.
    static let dim = 0.72
    // The photo on show drifts in by this much over its time (Ken Burns), and sideways by `drift` pt.
    static let kenBurns = 0.06
    static let drift = 10.0

    // Dragging turns the cube with the finger, one face per screen width. A release is thrown
    // `throwTime` seconds ahead at its speed, then a spring carries it to the nearest face (no
    // further than one face from where the drag began). Past one face the drag only stretches.
    static let dragSlop = 12.0
    static let dragFail = 28.0
    static let throwTime = 0.22
    static let rubber = 0.22
    static let stiffness = 210.0
    static let damping = 26.0
    // Taps in the left `tapBack` of the screen go back a photo, anywhere else forward.
    static let tapBack = 0.3
    static let tapSlop = 12.0
    static let longPress = 0.26
    static let longPressSlop = 14.0
    // The header and the footer fade out with this time constant while a story is held.
    static let fadeTime = 0.09
    // Photos fade in over this long once they arrive.
    static let imageFade = 0.3

    // autoPlay. Everything else plays by itself; the script adds one press-and-hold, on the second
    // person's second photo, `holdAt` seconds in, for `hold` seconds. A touch hands the viewer to
    // the user for `userHold` seconds. One loop: 11 photos × 2 s + 3 turns × 0.9 s + 1.3 s = 26 s.
    static let holdUser = 1
    static let holdPhoto = 1
    static let holdAt = 0.8
    static let hold = 1.3
    static let userHold = 4.0
    static let loop = 26.0
}

/// What the viewer is doing between frames.
enum CubeStoryViewerMode {
    case playing, turning, dragging, settling
}

/// One frame to draw.
struct CubeStoryViewerFrame {
    var cube: Double
    /// Per person: the photo on show and how far its bar has filled.
    var marks: [(index: Int, fill: Double)]
    var chrome: Double
    var elapsed: Double
}

/// The viewer's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class CubeStoryViewerEngine {
    private typealias M = CubeStoryViewerMetrics

    // The cube's turn in faces (unbounded; face u is in front at cube = u + count·k).
    private var cube = 0.0
    private var cubeVelocity = 0.0
    private var mode = CubeStoryViewerMode.playing
    private var active = 0
    private var turnFrom = 0.0
    private var turnTo = 0.0
    private var turnTime = 0.0
    private var dragFrom = 0.0
    private var target = 0.0
    private var marks: [(index: Int, fill: Double)] = []
    // A finger is down (the bars wait), or a story is held (the bars wait and the chrome fades).
    private var touching = false
    private var pressed = false
    private var chrome = 1.0
    private var scriptHold = 0.0
    private var scriptDone = false
    private var userHold = 0.0
    private var looping = false
    private var lastAuto: Bool?
    private var lastDate: Date?
    private(set) var elapsed = 0.0
    private var width = 0.0

    // The touch under way: where and when it went down, and which recogniser has won it.
    private var downAt: Date?
    private var downPoint = CGPoint.zero
    private var lastTranslation = CGSize.zero
    private var panning = false
    private var panFailed = false
    private var pressFailed = false

    private var photos: [String: (image: UIImage, at: Double)] = [:]

    private let impact = UIImpactFeedbackGenerator(style: .light)
    private let selection = UISelectionFeedbackGenerator()

    var lengths: [Int] = []
    var onUserChange: ((Int) -> Void)?

    func photo(_ address: String) -> (image: UIImage, at: Double)? { photos[address] }

    func photoArrived(_ image: UIImage, for address: String) {
        if photos[address] == nil { photos[address] = (image, elapsed) }
    }

    func step(to date: Date, width: Double, lengths: [Int], autoPlay: Bool, reducedMotion: Bool) -> CubeStoryViewerFrame {
        self.width = width
        self.lengths = lengths
        let count = lengths.count
        if marks.count != count { marks = Array(repeating: (0, 0), count: count) }
        if lastAuto != autoPlay {
            lastAuto = autoPlay
            looping = autoPlay
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0, count > 0 {
            checkLongPress(at: date)
            advance(dt: dt, reducedMotion: reducedMotion)
        }
        return CubeStoryViewerFrame(cube: cube, marks: marks, chrome: chrome, elapsed: elapsed)
    }

    // MARK: Helpers

    /// `x` moved by whole turns of `n` into [-n/2, n/2).
    static func centred(_ x: Double, _ n: Int) -> Double {
        x - Double(n) * floor(x / Double(n) + 0.5)
    }

    private static func mod(_ i: Int, _ n: Int) -> Int { ((i % n) + n) % n }

    /// JavaScript's Math.round: halves go up.
    private static func round(_ x: Double) -> Double { floor(x + 0.5) }

    /// The rubber band past one face from where a drag began.
    private static func stretch(_ d: Double) -> Double {
        let a = abs(d)
        return a <= 1 ? d : (d < 0 ? -1 : 1) * (1 + (a - 1) * M.rubber)
    }

    private func setMark(_ user: Int, _ index: Int, _ fill: Double) {
        marks[user] = (index, fill)
    }

    /// Starts a quarter turn of the cube by `step` faces; the person it turns to starts over.
    private func turn(_ step: Int) {
        let from = Self.round(cube)
        turnFrom = cube
        turnTo = from + Double(step)
        turnTime = 0
        guard from.isFinite, !lengths.isEmpty else { return }
        setMark(Self.mod(Int(from) + step, lengths.count), 0, 0)
        mode = .turning
    }

    /// The cube has come to rest on face `at`.
    private func land(_ at: Double) {
        let count = Double(lengths.count)
        // Whole turns of the cube change nothing on screen, so the number is kept small.
        let wrapped = at - count * floor(at / count)
        cube -= at - wrapped
        cubeVelocity = 0
        mode = .playing
        // `at` is a whole number of faces, so `wrapped` is too; clamped all the same, as it indexes `marks`.
        guard wrapped.isFinite, lengths.count > 0 else { return }
        let face = min(max(Int(wrapped), 0), lengths.count - 1)
        if face != active {
            active = face
            scriptDone = false
            // Called from the timeline's body: the owner hears about it once the frame is done.
            let callback = onUserChange
            DispatchQueue.main.async { callback?(face) }
        }
    }

    // MARK: The frame

    private func advance(dt: Double, reducedMotion: Bool) {
        elapsed += dt
        let count = lengths.count
        if userHold > 0 { userHold = max(0, userHold - dt) }

        switch mode {
        case .turning:
            let duration = reducedMotion ? M.turnReduced : M.turn
            let t = turnTime + dt
            turnTime = t
            let e = M.easeTurn.value(at: min(1, t / duration))
            cube = turnFrom + (turnTo - turnFrom) * e
            if t >= duration {
                cube = turnTo
                land(turnTo)
                // The time left over from this frame already counts towards the new person's first photo.
                setMark(active, 0, (t - duration) / M.photo)
            }
        case .settling:
            // Long frames are split in two: this spring is not stable over a 64 ms step. At 60 fps
            // nothing changes.
            let steps = dt > 0.034 ? 2 : 1
            let h = dt / Double(steps)
            var x = cube
            var v = cubeVelocity
            for _ in 0..<steps {
                v += (M.stiffness * (target - x) - M.damping * v) * h
                x += v * h
            }
            cubeVelocity = v
            cube = x
            if reducedMotion || (abs(target - cube) < 0.0015 && abs(v) < 0.02) {
                cube = target
                land(target)
            }
        case .playing:
            let u = active
            let (index, fill) = marks[u]
            // The script's one press-and-hold, when its moment comes round.
            if looping, userHold <= 0, !scriptDone, u == M.holdUser % count, index == M.holdPhoto, fill * M.photo >= M.holdAt {
                scriptDone = true
                scriptHold = M.hold
            }
            if scriptHold > 0 {
                scriptHold = max(0, scriptHold - dt)
            } else if !touching && !pressed {
                let next = fill + dt / M.photo
                if next < 1 {
                    setMark(u, index, next)
                } else if index < lengths[u] - 1 {
                    setMark(u, index + 1, next - 1)
                } else {
                    setMark(u, index, 1)
                    // After the last person the viewer stops, unless it is playing by itself.
                    if u < count - 1 || looping { turn(1) }
                }
            }
        case .dragging:
            break
        }

        // The header and footer fade while a story is held.
        let show = pressed || scriptHold > 0 ? 0.0 : 1.0
        chrome += (show - chrome) * (1 - exp(-dt / M.fadeTime))
    }

    // MARK: Gestures

    // One recogniser stands in for the race of a pan (12 pt sideways before 28 pt up or down), a
    // long press (260 ms within 14 pt) and a tap (lifted within 240 ms, within 12 pt).

    private func touched() {
        userHold = M.userHold
        scriptHold = 0
    }

    func touchChanged(_ value: DragGesture.Value) {
        if downAt == nil {
            // The gesture's own timestamps are not on the timeline's clock.
            downAt = Date()
            downPoint = value.startLocation
            panning = false
            panFailed = false
            pressFailed = false
            // The pan begins: the bars wait while a finger is down.
            touched()
            touching = true
        }
        lastTranslation = value.translation
        let dx = value.translation.width
        let dy = value.translation.height
        let distance = hypot(dx, dy)
        if distance > M.longPressSlop {
            pressFailed = true
            // A hold that moves too far ends.
            pressed = false
        }
        if !panning && !panFailed && !pressed {
            if abs(dy) >= M.dragFail {
                panFailed = true
                touching = false
            } else if abs(dx) >= M.dragSlop {
                panning = true
                pressFailed = true
                panStart()
            }
        }
        if panning { panUpdate(translationX: dx) }
    }

    /// A touch the system took away (a DragGesture then never ends): the drag settles on the
    /// nearest face and the bars run again. Nothing happens when the touch already ended.
    func touchCancelled() {
        guard downAt != nil || panning || touching || pressed || mode == .dragging else { return }
        if mode == .dragging {
            target = Self.round(cube)
            mode = .settling
        }
        downAt = nil
        panning = false
        touching = false
        pressed = false
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            downAt = nil
            panning = false
            touching = false
            pressed = false
        }
        // A touch lifted on the frame it went down reports no change first: it is a tap.
        guard let start = downAt else {
            touched()
            tap(x: value.location.x)
            return
        }
        if panning {
            panEnd(velocityX: value.velocity.width)
            // A drag that could not be measured (no width yet) settles where it is.
            if mode == .dragging {
                target = Self.round(cube)
                mode = .settling
            }
            return
        }
        if pressed { return }
        let distance = hypot(value.translation.width, value.translation.height)
        if Date().timeIntervalSince(start) <= M.longPress - 0.02, distance <= M.tapSlop {
            tap(x: value.location.x)
        }
    }

    private func checkLongPress(at date: Date) {
        guard let start = downAt, !panning, !pressed, !pressFailed else { return }
        if date.timeIntervalSince(start) >= M.longPress {
            pressFailed = true
            touched()
            pressed = true
            // Checked from the timeline's body: the haptic plays once the frame is done.
            DispatchQueue.main.async { [weak self] in self?.selection.selectionChanged() }
        }
    }

    private func panStart() {
        // A drag can catch the cube mid-turn; it starts from wherever the cube is.
        dragFrom = Self.round(cube)
        target = cube
        mode = .dragging
    }

    private func panUpdate(translationX: Double) {
        guard width > 0 else { return }
        let d = (target - dragFrom) - translationX / width
        cube = dragFrom + Self.stretch(d)
    }

    private func panEnd(velocityX: Double) {
        let count = lengths.count
        guard width > 0, count > 0, marks.count == count else { return }
        let v = velocityX.isFinite ? -velocityX / width : 0
        let thrown = cube + v * M.throwTime
        let to = max(dragFrom - 1, min(dragFrom + 1, Self.round(thrown)))
        let next = Self.mod(Int(to), count)
        if next != active {
            // The person turned to picks up where they were left, or starts over once all was seen.
            let seen = marks[next]
            setMark(next, seen.index >= lengths[next] - 1 && seen.fill >= 1 ? 0 : seen.index, 0)
            impact.impactOccurred()
        }
        target = to
        cubeVelocity = v
        mode = .settling
    }

    private func tap(x: Double) {
        guard mode == .playing, !lengths.isEmpty, marks.count == lengths.count, marks.indices.contains(active) else { return }
        touched()
        let u = active
        let index = marks[u].index
        if x < width * M.tapBack {
            if index > 0 { setMark(u, index - 1, 0) } else { turn(-1) }
        } else if index < lengths[u] - 1 {
            setMark(u, index + 1, 0)
        } else {
            setMark(u, index, 1)
            turn(1)
        }
    }
}

// MARK: - The faces' perspective

enum CubeStoryViewerProjection {
    /// Where a face sits on the cube. `p` is its distance from the front in faces: 0 in front,
    /// -1 a quarter turn to the left, +1 to the right. The face swings about the edge it shares with
    /// the face in front, so the cube's corner stays at the screen while the far edges recede. The
    /// lens sits over the middle of the screen, `perspective` widths away, as in the RN version.
    static func transform(p: Double, width w: Double, height h: Double) -> ProjectionTransform {
        // No width yet (first layout): the face stays flat rather than taking a NaN matrix.
        guard w > 0, h > 0, p.isFinite else { return ProjectionTransform() }
        let half = w / 2
        let pivot = p > 0 ? -half : half
        let theta = p * .pi / 2
        let s = sin(theta) / (w * CubeStoryViewerMetrics.perspective)
        let c = cos(theta)
        let k = -half - pivot
        let cx = w / 2
        let cy = h / 2
        // A point (x, y) of the face, from its top left: turned about the pivot, moved by p widths,
        // then divided by the lens. Rows of the row-vector matrix SwiftUI takes.
        var t = ProjectionTransform()
        t.m11 = cx * s + c
        t.m21 = 0
        t.m31 = cx * (1 + s * k) + c * k + p * w + pivot
        t.m12 = cy * s
        t.m22 = 1
        t.m32 = cy * (1 + s * k) - h / 2
        t.m13 = s
        t.m23 = 0
        t.m33 = 1 + s * k
        return t
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded for every face that shows it.
enum CubeStoryViewerPhotos {
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
