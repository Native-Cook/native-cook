import CoreGraphics
import SwiftUI
import UIKit

enum SpirographDrawMetrics {
    // One fixed clock drives rolling, the pen, the script and every transition: ticks of 1/60 s,
    // at most `maxTicks` a frame (a 50 ms cap).
    static let tick = 1.0 / 60
    static let maxTicks = 3

    // The pen samples the curve every `penStep` rad of rolling; a stroke is cut into chunks of
    // `chunk` points, each built once, so the drawing is never rebuilt from scratch.
    static let penStep = 0.016
    static let chunk = 96
    // Once the ink holds `flattenAt` chunks they are drawn once into an offscreen image and dropped,
    // so however long the drawing runs it costs one image and a few chunks.
    static let flattenAt = 24
    // A fine gel-pen line, in model units.
    static let inkWidth = 1.35
    // The faint pencil preview of the whole pattern.
    static let ghostStepsPerTurn = 150

    // A release keeps the gear rolling at the finger's speed (rad/s, capped), slowed by `friction` (1/s).
    static let maxSpin = 14.0
    static let friction = 1.1
    static let minSpin = 0.04
    // Gesture thresholds standing in for the React Native recognisers: a pan starts after 2 pt, a
    // tap is a touch lifted within 300 ms.
    static let panSlop = 2.0
    static let tapTime = 0.3
    // The pen moving to another hole: a critically damped spring (stiffness, damping).
    static let holeStiffness = 170.0
    static let holeDamping = 26.0
    // Changing gear: the old one fades and shrinks as the new one grows in, over `swapTime` s.
    static let swapTime = 0.45
    // Clear: the drawn sheet slides off the pad, turning, over `sheetTime` s; under it is a fresh one.
    static let sheetTime = 1.05
    static let sheetTurn = -9.0
    // The coach arrow (shown while the sheet is empty and still): it sweeps `coachSweep` rad round the
    // ring ahead of the gear, holds and fades, every `coachPeriod` s; it fades in/out at `coachRate` /s.
    static let coachPeriod = 2.0
    static let coachSweep = 1.2
    static let coachRate = 5.0
    // The preview fades once its pattern is fully traced, at `ghostRate` /s.
    static let ghostRate = 4.0
    // After a touch the loop waits this long before it starts again.
    static let userHold = 4.0

    // Layout (pt): the paper's side margin and the room it keeps inside round the ring; the header
    // and the controls take fixed heights.
    static let margin = 16.0
    static let paperPad = 12.0
    static let headerH = 66.0
    static let controlsH = 3 * 48.0
    static let hintH = 34.0

    static let bounds = SpirographDrawModel.bounds

    static let desk: UInt32 = 0x232428
    static let text: UInt32 = 0xF2EFE7
    static let muted: UInt32 = 0x9E9B93
    static let paper: UInt32 = 0xF6F2E8

    static func easeInOut(_ t: Double) -> Double {
        t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    static func easeOut(_ t: Double) -> Double { 1 - pow(1 - min(1, max(0, t)), 3) }

    /// Whether a clock that went from `before` to `t` passed the moment `at`.
    static func crossed(_ before: Double, _ t: Double, _ at: Double) -> Bool { before < at && t >= at }

    /// JavaScript's Math.round: halves go up.
    static func round(_ x: Double) -> Double { floor(x + 0.5) }
}

/// One stroke chunk: built once, kept until it is flattened or the sheet is torn off.
struct SpirographDrawChunk {
    let path: Path
    let ink: Int
}

/// The ink on the sheet: a flattened image of everything old, the chunks since, and the live stroke.
final class SpirographDrawInk {
    private typealias M = SpirographDrawMetrics

    var flat: CGImage?
    var chunks: [SpirographDrawChunk] = []
    /// The stroke being drawn, not yet built into a chunk.
    var live: [CGPoint] = []
    var gear: Int
    var ink: Int
    var down = false
    /// Device pixels per model unit of the flattened image.
    var scale: Double = 3

    init(gear: Int, ink: Int) {
        self.gear = gear
        self.ink = ink
        live.reserveCapacity(M.chunk + 1)
    }

    var isEmpty: Bool { flat == nil && chunks.isEmpty && live.count <= 1 }

    static func polyline(_ points: [CGPoint]) -> Path {
        var path = Path()
        path.addLines(points)
        return path
    }

    /// Builds the live stroke into a chunk and starts the next one from its last point.
    func commitLive() {
        if live.count >= 2 {
            chunks.append(SpirographDrawChunk(path: Self.polyline(live), ink: ink))
            if chunks.count >= M.flattenAt { flatten() }
        }
        let last = live.last
        live.removeAll(keepingCapacity: true)
        if let last { live.append(last) }
    }

    func addPoint(_ p: CGPoint) {
        live.append(p)
        if live.count >= M.chunk { commitLive() }
    }

    func liftPen() {
        commitLive()
        live.removeAll(keepingCapacity: true)
        down = false
    }

    func wipe() {
        flat = nil
        chunks.removeAll()
        live.removeAll(keepingCapacity: true)
        down = false
    }

    /// Draws the old image (if any) and then every chunk's stroke into a new image of the ink's bounds.
    private func flatten() {
        let side = Int(ceil(2 * M.bounds * scale))
        guard side > 0, let space = CGColorSpace(name: CGColorSpace.sRGB), let context = CGContext(
            data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: 0,
            space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return }
        if let flat { context.draw(flat, in: CGRect(x: 0, y: 0, width: side, height: side)) }
        // y down, in model units around the ring's centre.
        context.translateBy(x: 0, y: CGFloat(side))
        context.scaleBy(x: scale, y: -scale)
        context.translateBy(x: M.bounds, y: M.bounds)
        context.setLineCap(.round)
        context.setLineJoin(.round)
        context.setLineWidth(M.inkWidth)
        for chunk in chunks {
            context.setStrokeColor(UIColor(Color(hex: SpirographDrawModel.inks[chunk.ink].color)).cgColor)
            context.addPath(chunk.path.cgPath)
            context.strokePath()
        }
        if let image = context.makeImage() {
            flat = image
            chunks.removeAll()
        }
    }
}

/// One frame to draw.
struct SpirographDrawFrame {
    var number: Int
    var phi: Double
    var spin: Double
    var hole: Double
    var gear: Int
    var gearFrom: Int
    var swap: Double
    var ink: Int
    /// The sheet tearing off (0…1), or -1.
    var tear: Double
    var ghost: Double
    var coach: Double
    /// Where the coach arrow is in its sweep (s into its period).
    var coachPhase: Double
}

/// The gear's rolling, the pen, the script and every transition, stepped once a frame like the RN
/// frame callback, and the gestures and buttons that drive them.
@MainActor
final class SpirographDrawEngine {
    private typealias M = SpirographDrawMetrics
    private typealias G = SpirographDrawModel

    private(set) var ink: SpirographDrawInk?
    private var number = 0
    private var acc = 0.0
    private var lastDate: Date?
    // Rolling: the gear's angle round the ring, its own turn, and its coasting speed (rad/s).
    private var phi = 0.0
    private var spin = 0.0
    private var lastPhi = 0.0
    private var omega = 0.0
    private var dragging = false
    private var fingerLast = 0.0
    // Gear and swap progress (1 = settled); the pen's hole, its target and its speed; the ink.
    private var gear = G.startGear
    private var gearFrom = G.startGear
    private var swap = 1.0
    private var hole = Double(G.startHole)
    private var holeTarget = G.startHole
    private var holeSpeed = 0.0
    private var inkIndex = G.startInk
    // How much of the current pattern the pen has covered (the span of φ it has drawn over).
    private var spanLo = 0.0
    private var spanHi = 0.0
    private var spanSet = false
    private var complete = false
    private var ghost = 1.0
    // The sheet tearing off (0…1), or -1.
    private var tear = -1.0
    // The coach arrow's strength and its own clock.
    private var coach = 1.0
    private var coachClock = 0.0
    // Requests from the controls, handled on the next tick.
    private var pendingGear = -1
    private var pendingHole = -1
    private var pendingInk = -1
    private var pendingClear = false
    // The script: its clock, a pause after a touch, and a scripted roll in progress.
    private var clock = 0.0
    private var hold = 0.0
    private var looping = false
    private var autoPlay: Bool?
    private var rollFrom = 0.0
    private var rollBy = 0.0
    private var rollAt = 0.0
    private var rollTime = 0.0
    private var reducedMotion = false
    /// Tells the view which gear, hole and ink to show in the controls and the status line.
    var onShow: ((Int, Int, Int) -> Void)?

    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let notice = UINotificationFeedbackGenerator()

    func step(to date: Date, autoPlay: Bool, reducedMotion: Bool, scale: Double) -> SpirographDrawFrame {
        self.reducedMotion = reducedMotion
        if self.autoPlay != autoPlay {
            self.autoPlay = autoPlay
            looping = autoPlay
            clock = 0
            hold = 0
        }
        if ink == nil { ink = SpirographDrawInk(gear: gear, ink: inkIndex) }
        guard let ink else { return frame() }
        ink.scale = max(1, scale)
        let dt = min(lastDate.map { date.timeIntervalSince($0) } ?? 0.016, Double(M.maxTicks) * M.tick)
        lastDate = date
        var pending = acc + max(0, dt)
        var ticks = 0
        while pending >= M.tick && ticks < M.maxTicks {
            step(ink)
            pending -= M.tick
            ticks += 1
        }
        acc = min(pending, M.tick)
        if ticks > 0 { number += 1 }
        return frame()
    }

    private func frame() -> SpirographDrawFrame {
        SpirographDrawFrame(
            number: number, phi: phi, spin: spin, hole: hole, gear: gear, gearFrom: gearFrom, swap: swap, ink: inkIndex,
            tear: tear, ghost: ghost, coach: coach,
            coachPhase: looping && hold <= 0 ? clock.truncatingRemainder(dividingBy: M.coachPeriod) : coachClock
        )
    }

    private func show() {
        let report = onShow
        let g = gear
        let h = holeTarget
        let i = inkIndex
        // Reported after this frame's update, never during it.
        DispatchQueue.main.async { report?(g, h, i) }
    }

    /// A new pattern starts: forget how much of the old one was covered.
    private func resetSpan() {
        spanSet = false
        complete = false
    }

    private func changeGear(_ index: Int) {
        guard G.gears.indices.contains(index) else { return }
        if index == gear && swap >= 1 { return }
        gearFrom = gear
        gear = index
        swap = reducedMotion ? 1 : 0
        resetSpan()
        show()
    }

    private func changeHole(_ index: Int) {
        guard G.holes.indices.contains(index), index != holeTarget else { return }
        holeTarget = index
        if reducedMotion { hole = Double(index) }
        resetSpan()
        show()
    }

    private func changeInk(_ index: Int) {
        guard G.inks.indices.contains(index), index != inkIndex else { return }
        inkIndex = index
        show()
    }

    private func startTear() {
        if tear >= 0 { return }
        tear = 0
    }

    /// The autoPlay script, from `before` to `t` seconds.
    private func script(_ before: Double, _ t: Double) {
        for cue in G.script where M.crossed(before, t, cue.at) {
            if let i = cue.ink { changeInk(i) }
            if let g = cue.gear { changeGear(g) }
            if let h = cue.hole { changeHole(h) }
            if cue.clear { startTear() }
            if let roll = cue.roll {
                rollFrom = phi
                rollBy = roll * 2 * .pi
                rollAt = cue.at
                rollTime = cue.time ?? 1
                omega = 0
            }
        }
    }

    /// One tick: requests, the script, rolling, the pen's hole and the gear swap, then the pen.
    private func step(_ state: SpirographDrawInk) {
        if pendingGear >= 0 {
            changeGear(pendingGear)
            pendingGear = -1
        }
        if pendingHole >= 0 {
            changeHole(pendingHole)
            pendingHole = -1
        }
        if pendingInk >= 0 {
            changeInk(pendingInk)
            pendingInk = -1
        }
        if pendingClear {
            startTear()
            pendingClear = false
        }

        if !dragging {
            if hold > 0 {
                hold -= M.tick
                if hold <= 0 { clock = 0 }
            } else if looping {
                let before = clock
                var t = before + M.tick
                if t >= G.loop {
                    t -= G.loop
                    // A whole turn of either angle looks the same: keep the numbers small.
                    let turnsPhi = M.round(phi / (2 * .pi)) * 2 * .pi
                    phi -= turnsPhi
                    lastPhi -= turnsPhi
                    spin -= M.round(spin / (2 * .pi)) * 2 * .pi
                    resetSpan()
                }
                clock = t
                script(t < before ? 0 : before, t)
                if rollTime > 0 {
                    let k = min(1, (t - rollAt) / rollTime)
                    phi = rollFrom + rollBy * M.easeInOut(max(0, k))
                    if k >= 1 { rollTime = 0 }
                }
            }
            if rollTime <= 0 && abs(omega) > 0 {
                phi += omega * M.tick
                omega *= exp(-M.friction * M.tick)
                if abs(omega) < M.minSpin { omega = 0 }
            }
        }

        let h = hole
        let hv = holeSpeed + (M.holeStiffness * (Double(holeTarget) - h) - M.holeDamping * holeSpeed) * M.tick
        holeSpeed = hv
        hole = h + hv * M.tick
        if abs(Double(holeTarget) - hole) < 0.002 && abs(hv) < 0.01 {
            hole = Double(holeTarget)
            holeSpeed = 0
        }
        if swap < 1 { swap = min(1, swap + M.tick / M.swapTime) }
        if tear >= 0 {
            let w = tear + M.tick / (reducedMotion ? 0.35 : M.sheetTime)
            if w >= 1 {
                state.wipe()
                tear = -1
                resetSpan()
            } else {
                tear = w
            }
        }

        // Roll the gear by however far `phi` moved, and let the pen trace the way it went.
        let g = gear
        let sw = M.easeInOut(swap)
        let radius = G.gearRadius(G.gears[gearFrom].teeth) * (1 - sw) + G.gearRadius(G.gears[g].teeth) * sw
        let from = lastPhi
        let to = phi
        let delta = to - from
        let rate = G.spinRate(radius)
        let spinFrom = spin
        spin = spinFrom + delta * rate
        lastPhi = to

        // The coach shows while the sheet is empty and nothing moves; the preview fades once traced.
        let still = !dragging && omega == 0 && rollTime <= 0 && tear < 0
        let coachGoal = still && state.isEmpty ? 1.0 : 0.0
        coach += (coachGoal - coach) * (1 - exp(-M.tick * M.coachRate))
        coachClock = (coachClock + M.tick).truncatingRemainder(dividingBy: M.coachPeriod)
        let ghostGoal = complete ? 0.0 : 1.0
        ghost += (ghostGoal - ghost) * (1 - exp(-M.tick * M.ghostRate))

        let settled = swap >= 1 && hole == Double(holeTarget) && tear < 0
        if !settled {
            if state.down { state.liftPen() }
            return
        }
        let pen = G.holeAt(hole, radius)
        if !state.down || state.gear != g || state.ink != inkIndex {
            if state.down { state.liftPen() }
            state.gear = g
            state.ink = inkIndex
            state.down = true
            state.live.removeAll(keepingCapacity: true)
            state.live.append(G.penAt(to, spin, radius, pen.d, pen.psi))
            return
        }
        if delta == 0 { return }
        let n = min(400, Int(ceil(abs(delta) / M.penStep)))
        for k in 1...n {
            let f = Double(k) / Double(n)
            state.addPoint(G.penAt(from + delta * f, spinFrom + delta * rate * f, radius, pen.d, pen.psi))
        }
        // How much of the closed pattern the pen has now been over.
        if !spanSet {
            spanLo = from
            spanHi = from
            spanSet = true
        }
        spanLo = min(spanLo, to)
        spanHi = max(spanHi, to)
        let full = Double(G.pattern(G.gears[g].teeth).turns) * 2 * .pi
        if !complete && spanHi - spanLo >= full - 0.02 {
            complete = true
            if !looping || hold > 0 { notice.notificationOccurred(.success) }
        }
    }

    // MARK: - Gestures and buttons

    private var touchStart: Date?
    private var startPoint = CGPoint.zero
    private var startedOnPaper = false
    private var panning = false

    private func touched() {
        hold = M.userHold
        rollTime = 0
    }

    /// A finger on the canvas, at `location` (pt) relative to the ring's centre; `onPaper` says
    /// whether it came down on the sheet.
    func touchChanged(location: CGPoint, onPaper: Bool, time: Date) {
        if touchStart == nil {
            touchStart = time
            startPoint = location
            startedOnPaper = onPaper
            panning = false
            // A finger on the canvas stops a coasting gear.
            omega = 0
            touched()
        }
        if !panning {
            if hypot(location.x - startPoint.x, location.y - startPoint.y) < M.panSlop { return }
            panning = true
            if startedOnPaper {
                dragging = true
                fingerLast = atan2(location.y, location.x)
            }
        }
        guard dragging else { return }
        let a = atan2(location.y, location.x)
        var d = a - fingerLast
        if d > .pi { d -= 2 * .pi }
        if d < -.pi { d += 2 * .pi }
        fingerLast = a
        // Right over the centre the angle is meaningless: skip it rather than spin the gear.
        if hypot(location.x, location.y) < 18 { return }
        phi += d
    }

    func touchEnded(location: CGPoint, velocity: CGSize, time: Date) {
        defer {
            touchStart = nil
            panning = false
            dragging = false
            hold = M.userHold
        }
        if panning {
            guard dragging else { return }
            let rx = location.x
            let ry = location.y
            let w = (rx * velocity.height - ry * velocity.width) / max(rx * rx + ry * ry, 2500)
            omega = reducedMotion || !w.isFinite ? 0 : max(-M.maxSpin, min(M.maxSpin, w))
            return
        }
        guard let start = touchStart, time.timeIntervalSince(start) <= M.tapTime, startedOnPaper else { return }
        touched()
        changeHole((holeTarget + 1) % G.holes.count)
        selection.selectionChanged()
    }

    private func userPick() {
        hold = M.userHold
        rollTime = 0
    }

    func pickGear(_ index: Int) {
        userPick()
        if index != gear { light.impactOccurred() }
        pendingGear = index
    }

    func pickHole(_ index: Int) {
        userPick()
        if index != holeTarget { selection.selectionChanged() }
        pendingHole = index
    }

    func pickInk(_ index: Int) {
        userPick()
        if index != inkIndex { selection.selectionChanged() }
        pendingInk = index
    }

    /// Clear: the sheet is torn off the pad.
    func clear() {
        userPick()
        medium.impactOccurred()
        pendingClear = true
    }

    /// VoiceOver's activate: moves the pen to the next hole, as a tap on the paper does.
    func nextHole() {
        touched()
        changeHole((holeTarget + 1) % G.holes.count)
    }
}
