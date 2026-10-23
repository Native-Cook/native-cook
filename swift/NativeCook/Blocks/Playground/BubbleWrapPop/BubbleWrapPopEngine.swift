import SwiftUI
import UIKit

enum BubbleWrapPopMetrics {
    // Layout: the sheet sits `side` pt from the screen's sides, between the counter and the hint.
    static let side = 18.0
    static let header = 118.0
    static let footer = 64.0
    static let sheetRadius = 26.0

    // autoPlay (seconds): eight presses pop a smiley, a finger sweeps every row, and once the sheet
    // is empty a fresh one slides in before the loop ends.
    static let loop = 12.0
    static let smileAt = 0.5
    static let smileStep = 0.6
    static let pressHold = 0.56
    static let sweepAt = 5.75
    static let sweepRow = 0.3
    static let scriptID = 1000
    static let userHold = 3.0

    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }

    static func easeInOut(_ t: Double) -> Double {
        let x = clamp01(t)
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    struct Stroke {
        var at: Double
        var dur: Double
        var pts: [Double]
    }

    /// Where autoPlay's fingers go on a sheet at rest: a smiley of presses, then a sweep.
    static func script(_ s: BubbleWrapPopSheet) -> [Stroke] {
        let w = s.right - s.left
        let h = s.bottom - s.top
        var targets: [(Double, Double)] = [(0.33, 0.3), (0.67, 0.3)]
        for k in 0..<6 {
            let a = Double(20 + k * 28) * Double.pi / 180
            targets.append((0.5 - cos(a) * 0.27, 0.52 + sin(a) * 0.2))
        }
        var used: [Int] = []
        var out: [Stroke] = []
        for (k, target) in targets.enumerated() {
            var best = -1
            var bestD = Double.infinity
            for i in 0..<s.count where !used.contains(i) {
                let d = hypot(s.bx[i] - target.0 * w, s.by[i] - target.1 * h)
                if d < bestD {
                    bestD = d
                    best = i
                }
            }
            used.append(best)
            out.append(Stroke(at: smileAt + Double(k) * smileStep, dur: pressHold, pts: [s.left + s.bx[best], s.top + s.by[best]]))
        }
        // The sweep: from the first bubble of the top row, across each row and down, side to side.
        let rows = s.rowOf[s.count - 1] + 1
        var pts: [Double] = []
        let x0 = s.left + s.bx[0] - s.radius * 0.6
        let x1 = s.left + s.bx[s.count - 1] + s.radius * 0.6
        for r in 0..<rows {
            let y = s.top + s.by[r * 6]
            let leftToRight = r % 2 == 0
            pts.append(contentsOf: [leftToRight ? x0 : x1, y, leftToRight ? x1 : x0, y])
        }
        out.append(Stroke(at: sweepAt, dur: Double(rows) * sweepRow, pts: pts))
        return out
    }

    /// A point along a stroke's polyline, `u` of the way by length.
    static func along(_ pts: [Double], _ u: Double) -> CGPoint {
        let n = pts.count / 2
        if n == 1 { return CGPoint(x: pts[0], y: pts[1]) }
        var total = 0.0
        for i in 1..<n { total += hypot(pts[2 * i] - pts[2 * i - 2], pts[2 * i + 1] - pts[2 * i - 1]) }
        var d = clamp01(u) * total
        for i in 1..<n {
            let l = hypot(pts[2 * i] - pts[2 * i - 2], pts[2 * i + 1] - pts[2 * i - 1])
            if d <= l || i == n - 1 {
                let t = l > 0 ? min(1, d / l) : 0
                return CGPoint(x: pts[2 * i - 2] + (pts[2 * i] - pts[2 * i - 2]) * t, y: pts[2 * i - 1] + (pts[2 * i + 1] - pts[2 * i - 1]) * t)
            }
            d -= l
        }
        return CGPoint(x: pts[pts.count - 2], y: pts[pts.count - 1])
    }
}

struct BubbleWrapPopSettings {
    var autoPlay: Bool
    var reducedMotion: Bool
    var width: Double
    var top: Double
    var bottom: Double
    var onPop: ((Int, Int) -> Void)?
}

/// Steps the sheet once a frame like the RN frame callback, plays the script, and takes touches.
@MainActor
final class BubbleWrapPopEngine {
    private typealias M = BubbleWrapPopMetrics
    private typealias P = BubbleWrapPopPhysics

    private(set) var sheet: BubbleWrapPopSheet?
    private var script: [M.Stroke] = []
    private var strokeDown: [Bool] = []
    private(set) var elapsed = 0.0
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var lastAuto: Bool?
    private var lastDate: Date?
    private(set) var frameCount = 0

    private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private let notice = UINotificationFeedbackGenerator()

    func step(to date: Date, settings: BubbleWrapPopSettings) {
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
        }
        // The script and the simulation share one clock, capped so a slow frame cannot skip ahead.
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(max(raw, 0), 1.0 / 30)
        if settings.width <= 0 || dt <= 0 { return }
        elapsed += dt
        frameCount += 1
        let s: BubbleWrapPopSheet
        if let current = sheet, current.width == settings.width, current.top == settings.top, current.bottom == settings.bottom {
            s = current
        } else {
            s = BubbleWrapPopSheet(
                width: settings.width,
                left: M.side,
                top: settings.top,
                right: settings.width - M.side,
                bottom: settings.bottom,
                calm: settings.reducedMotion
            )
            sheet = s
            script = M.script(s)
            strokeDown = [Bool](repeating: false, count: script.count)
        }
        if hold > 0 {
            hold -= dt
            if hold <= 0 { clock = 0 }
        } else if looping {
            let t = (clock + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            for (i, k) in script.enumerated() {
                let u = (t - k.at) / k.dur
                let p = M.along(k.pts, u)
                if u >= 0 && u < 1 {
                    if !strokeDown[i] {
                        s.down(id: M.scriptID + i, x: p.x, y: p.y, scripted: true)
                        strokeDown[i] = true
                    } else {
                        s.move(id: M.scriptID + i, x: p.x, y: p.y)
                    }
                } else if strokeDown[i] {
                    s.up(id: M.scriptID + i)
                    strokeDown[i] = false
                }
            }
        }
        s.step(dt)
        if s.events != 0 {
            if s.eventsByUser && s.events & P.eventPop != 0 {
                rigid.impactOccurred()
                settings.onPop?(s.pops, s.count)
            }
            if s.events & P.eventSwap != 0 && s.userFingers() == 0 && hold > 0 { notice.notificationOccurred(.success) }
            s.events = 0
            s.eventsByUser = false
        }
    }

    /// A real touch pauses the loop and lifts autoPlay's fingers.
    private func touched() {
        hold = M.userHold
        guard let s = sheet else { return }
        s.cancelScripted()
        for i in strokeDown.indices { strokeDown[i] = false }
    }

    func touchesDown(_ touches: [(id: Int, point: CGPoint)]) {
        touched()
        guard let s = sheet else { return }
        for t in touches { s.down(id: t.id, x: t.point.x, y: t.point.y, scripted: false) }
    }

    func touchesMoved(_ touches: [(id: Int, point: CGPoint)]) {
        guard let s = sheet else { return }
        hold = M.userHold
        for t in touches { s.move(id: t.id, x: t.point.x, y: t.point.y) }
    }

    func touchesUp(_ ids: [Int]) {
        guard let s = sheet else { return }
        for id in ids { s.up(id: id) }
    }
}

/// Every finger on the sheet, with its own id, from a recogniser that only listens: it never claims
/// the touches, so nothing else (a back swipe) can cancel them out from under a press.
struct BubbleWrapPopTouches: UIViewRepresentable {
    let engine: BubbleWrapPopEngine

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isMultipleTouchEnabled = true
        let listener = Listener(engine: engine)
        listener.cancelsTouchesInView = false
        listener.delaysTouchesBegan = false
        listener.delaysTouchesEnded = false
        listener.delegate = context.coordinator
        view.addGestureRecognizer(listener)
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            true
        }
    }

    final class Listener: UIGestureRecognizer {
        private let engine: BubbleWrapPopEngine
        private var ids: [ObjectIdentifier: Int] = [:]
        private var nextID = 1

        init(engine: BubbleWrapPopEngine) {
            self.engine = engine
            super.init(target: nil, action: nil)
        }

        private func points(_ touches: Set<UITouch>) -> [(id: Int, point: CGPoint)] {
            touches.compactMap { t in
                guard let id = ids[ObjectIdentifier(t)] else { return nil }
                return (id, t.location(in: view))
            }
        }

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
            for t in touches {
                ids[ObjectIdentifier(t)] = nextID
                nextID += 1
            }
            engine.touchesDown(points(touches))
        }

        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
            engine.touchesMoved(points(touches))
        }

        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
            finish(touches)
        }

        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
            finish(touches)
        }

        private func finish(_ touches: Set<UITouch>) {
            let pts = points(touches)
            for t in touches { ids[ObjectIdentifier(t)] = nil }
            engine.touchesUp(pts.map(\.id))
            // Once the last finger lifts, the listener resets for the next touch.
            if ids.isEmpty { state = .failed }
        }

        override func reset() {
            super.reset()
            if !ids.isEmpty { engine.touchesUp(Array(ids.values)) }
            ids.removeAll()
        }
    }
}
