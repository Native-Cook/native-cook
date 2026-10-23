import SwiftUI
import UIKit

/// One clay a tap can recolour the blob to.
struct ClayPressBlobColour: Equatable {
    var name: String
    var hex: UInt32
}

enum ClayPressBlobMetrics {
    static let palette: [ClayPressBlobColour] = [
        ClayPressBlobColour(name: "Terracotta", hex: 0xD2623B),
        ClayPressBlobColour(name: "Cobalt", hex: 0x3954B8),
        ClayPressBlobColour(name: "Moss", hex: 0x6C9858),
        ClayPressBlobColour(name: "Marigold", hex: 0xE3A12F),
        ClayPressBlobColour(name: "Rose", hex: 0xDC7290),
        ClayPressBlobColour(name: "Lilac", hex: 0x8E74CC),
    ]
    // The stage the clay can be thrown around in, inside the header and the hint.
    static let header = 124.0
    static let footer = 66.0
    // The name's crossfade when the clay changes colour (Reanimated's FadeIn after a delay / FadeOut).
    static let labelIn = 0.3
    static let labelDelay = 0.12
    static let labelOut = 0.18

    // autoPlay (seconds). Each stroke is one finger, placed in units of the clay's radius from its
    // home: down at `at`, moving from `from` to `to` over `move` of its `dur`, up at the end. A
    // stroke with `tap` recolours the clay to that entry instead of cycling.
    static let loop = 14.0
    struct Stroke {
        var at: Double
        var dur: Double
        var from: (Double, Double)
        var to: (Double, Double)
        var move: Double
        var easeIn: Bool
        var tap: Int
    }
    static let script: [Stroke] = [
        // Two pokes that leave dents, then one on the right rim that notches it.
        Stroke(at: 0.6, dur: 0.6, from: (0.3, -0.34), to: (0.3, -0.34), move: 1, easeIn: false, tap: -1),
        Stroke(at: 1.7, dur: 0.5, from: (-0.36, 0.28), to: (-0.36, 0.28), move: 1, easeIn: false, tap: -1),
        Stroke(at: 2.7, dur: 0.6, from: (0.86, 0.14), to: (0.86, 0.14), move: 1, easeIn: false, tap: -1),
        // A pull from the upper right, held, then let go.
        Stroke(at: 4.0, dur: 1.05, from: (0.72, -0.62), to: (1.18, -0.98), move: 0.72, easeIn: false, tap: -1),
        // A tap to cobalt.
        Stroke(at: 5.8, dur: 0.09, from: (0.04, -0.06), to: (0.04, -0.06), move: 1, easeIn: false, tap: 1),
        // A flick to the right: it squashes against the wall and wobbles back.
        Stroke(at: 7.0, dur: 0.15, from: (-0.18, 0.1), to: (0.56, 0.02), move: 1, easeIn: true, tap: -1),
        // A pinch across the middle, held, then let go.
        Stroke(at: 9.2, dur: 0.95, from: (-0.9, 0), to: (-0.68, 0), move: 0.5, easeIn: false, tap: -1),
        Stroke(at: 9.2, dur: 0.95, from: (0.9, 0), to: (0.68, 0), move: 0.5, easeIn: false, tap: -1),
        // A tap back to terracotta.
        Stroke(at: 11.0, dur: 0.09, from: (-0.06, 0.05), to: (-0.06, 0.05), move: 1, easeIn: false, tap: 0),
    ]
    static let scriptID = 1000
    // After a touch the loop waits this long, then starts over.
    static let userHold = 3.0

    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }

    static func easeInOut(_ p: Double) -> Double {
        let t = clamp01(p)
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    /// Reanimated's default timing curve, ease in and out (quadratic).
    static func quadInOut(_ p: Double) -> Double {
        let t = clamp01(p)
        return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }
}

struct ClayPressBlobSettings {
    var autoPlay: Bool
    var reducedMotion: Bool
    var paletteSize: Int
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
    var onRecolour: ((Int) -> Void)?
}

/// The clay's state, stepped once a frame like the RN frame callback, the autoPlay script and the
/// touches that reach it.
@MainActor
final class ClayPressBlobEngine {
    private typealias M = ClayPressBlobMetrics
    private typealias P = ClayPressBlobPhysics

    private(set) var sim: ClayPressBlobSim?
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var lastAuto: Bool?
    private var lastDate: Date?
    private var strokeDown = [Bool](repeating: false, count: M.script.count)
    private var settings: ClayPressBlobSettings?
    // The colour label: the clay shown now, the one before it, and when it changed.
    private(set) var label = 0
    private(set) var previousLabel = -1
    private(set) var elapsed = 0.0
    private(set) var labelAt = -10.0
    private(set) var frameCount = 0

    private let soft = UIImpactFeedbackGenerator(style: .soft)
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let selection = UISelectionFeedbackGenerator()

    func step(to date: Date, settings: ClayPressBlobSettings) {
        self.settings = settings
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
        if settings.width <= 0 { return }
        var s: ClayPressBlobSim
        if let current = sim, current.right == settings.width - 20, current.bottom == settings.bottom, current.top == settings.top {
            s = current
        } else {
            // A new stage (first frame, a new layout) lays a fresh body down in the colour it had.
            let kept = min(max(sim?.colourTo ?? 0, 0), max(settings.paletteSize - 1, 0))
            s = ClayPressBlobSim(
                width: settings.width,
                height: settings.height,
                top: settings.top,
                bottom: settings.bottom,
                paletteSize: settings.paletteSize,
                calm: settings.reducedMotion
            )
            s.colourFrom = kept
            s.colourTo = kept
            sim = s
        }
        if dt <= 0 { return }
        elapsed += dt
        frameCount += 1
        // The script: each stroke's finger is down while the loop is inside it.
        if hold > 0 {
            hold -= dt
            if hold <= 0 { clock = 0 }
        } else if looping {
            let t = (clock + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            for (i, k) in M.script.enumerated() {
                let u = (t - k.at) / k.dur
                let m = M.clamp01(u / k.move)
                let e = k.easeIn ? m * m : M.easeInOut(m)
                let x = s.homeX + (k.from.0 + (k.to.0 - k.from.0) * e) * s.r0
                let y = s.homeY + (k.from.1 + (k.to.1 - k.from.1) * e) * s.r0
                if u >= 0 && u < 1 {
                    if !strokeDown[i] {
                        s.touchDown(id: M.scriptID + i, x: x, y: y, scripted: true, tapColour: k.tap)
                        strokeDown[i] = true
                    } else {
                        s.touchMove(id: M.scriptID + i, x: x, y: y)
                    }
                } else if strokeDown[i] {
                    s.touchMove(id: M.scriptID + i, x: x, y: y)
                    s.touchUp(id: M.scriptID + i, cancelled: false)
                    strokeDown[i] = false
                }
            }
        }
        s.step(dt)
        if s.events != 0 {
            if s.events & P.eventRecolour != 0 { recoloured(s.colourTo, byUser: s.eventsByUser) }
            if s.eventsByUser { feel(s.events) }
            s.events = 0
        }
    }

    private func recoloured(_ index: Int, byUser: Bool) {
        if index != label {
            previousLabel = label
            label = index
            labelAt = elapsed
        }
        if byUser { settings?.onRecolour?(index) }
    }

    private func feel(_ events: Int) {
        if events & P.eventThud != 0 { medium.impactOccurred() }
        else if events & P.eventRecolour != 0 { selection.selectionChanged() }
        else if events & P.eventPress != 0 { soft.impactOccurred() }
        else if events & P.eventRelease != 0 { light.impactOccurred() }
    }

    /// A real touch pauses the loop and lifts autoPlay's fingers.
    private func touched() {
        hold = M.userHold
        guard let s = sim else { return }
        s.cancelScripted()
        for i in strokeDown.indices { strokeDown[i] = false }
    }

    func touchesDown(_ touches: [(id: Int, point: CGPoint)]) {
        touched()
        guard let s = sim else { return }
        for t in touches { s.touchDown(id: t.id, x: t.point.x, y: t.point.y, scripted: false, tapColour: -1) }
    }

    func touchesMoved(_ touches: [(id: Int, point: CGPoint)]) {
        guard let s = sim else { return }
        hold = M.userHold
        for t in touches { s.touchMove(id: t.id, x: t.point.x, y: t.point.y) }
    }

    func touchesUp(_ ids: [Int], cancelled: Bool) {
        guard let s = sim else { return }
        for id in ids { s.touchUp(id: id, cancelled: cancelled) }
    }
}

/// Every finger on the clay, with its own id, from a recogniser that only listens: it never claims
/// the touches, so nothing else (a back swipe) can cancel them out from under the clay.
struct ClayPressBlobTouches: UIViewRepresentable {
    let engine: ClayPressBlobEngine

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
        private let engine: ClayPressBlobEngine
        private var ids: [ObjectIdentifier: Int] = [:]
        private var nextID = 1

        init(engine: ClayPressBlobEngine) {
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
            let pts = points(touches)
            engine.touchesDown(pts)
        }

        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
            let pts = points(touches)
            engine.touchesMoved(pts)
        }

        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
            finish(touches, cancelled: false)
        }

        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
            finish(touches, cancelled: true)
        }

        private func finish(_ touches: Set<UITouch>, cancelled: Bool) {
            let pts = points(touches)
            for t in touches { ids[ObjectIdentifier(t)] = nil }
            engine.touchesUp(pts.map(\.id), cancelled: cancelled)
            // Once the last finger lifts, the listener resets for the next touch.
            if ids.isEmpty { state = .failed }
        }

        override func reset() {
            super.reset()
            // Fingers still down when the system resets the listener are let go without a throw.
            if !ids.isEmpty { engine.touchesUp(Array(ids.values), cancelled: true) }
            ids.removeAll()
        }
    }
}
