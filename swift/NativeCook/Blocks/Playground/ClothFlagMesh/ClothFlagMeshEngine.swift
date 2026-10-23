import Foundation
import UIKit

enum ClothFlagMeshMetrics {
    typealias Cloth = ClothFlagMeshCloth
    // One fixed clock drives the cloth and the autoPlay script: ticks of 1/60 s, at most `maxTicks`
    // a frame. Every frame draws the cloth between its last two ticks, so it moves evenly at any
    // refresh rate; if a frame runs late the cloth slows for a moment rather than jumping.
    static let tick = 1.0 / 60
    static let maxTicks = 2
    // Steps of still air run once at the start to hang the banner at rest; in the last
    // `stillSteps` of them every point is stopped after each step, so it comes to a dead stop.
    static let settleSteps = 420
    static let stillSteps = 180

    // Layout (pt): the pole's centre from the left, the banner's top below the safe area.
    static let poleX = 40.0
    static let poleW = 6.0
    // The pole fades over poleFade pt and is gone captionClear pt above the bottom inset.
    static let poleFade = 110.0
    static let captionClear = 196.0
    static let flagTop = 100.0
    // The cloth's hoist sits just right of the pole's centre, on its rings.
    static let hoistGap = 4.0
    // Perspective: depth (pt towards the viewer) seen from `focal` pt away.
    static let focal = 1100.0
    // The horizon, as a share of the screen's height.
    static let horizon = 0.6

    // Light from the upper left and in front; the sheen is a soft satin highlight.
    static let light = (x: -0.4, y: -0.5, z: 0.77)
    static let sheenPower = 40.0
    static let sheen = 0.5

    // The wind settings and their strength; the wind eases between them at `windRate` /s.
    static let modes = ["Calm", "Breeze", "Gale"]
    static let modeLevel = [0.0, 0.55, 1.0]
    static let windRate = 1.6
    // A gust fades with this time constant (s). The script gusts by `scriptGust`; a swipe this fast
    // (pt/s) that misses the cloth, while the wind is up, gusts by `gustPerSpeed` per pt/s; together
    // never past `gustMax`.
    static let gustFade = 1.1
    static let scriptGust = 0.85
    static let gustSwipe = 450.0
    static let gustPerSpeed = 1 / 2200.0
    static let gustMax = 1.5
    // A drag picks the nearest point of the cloth within this reach (pt). The cloth itself is the
    // feedback: the held point follows the finger and the folds pull out from it, with no marker.
    static let grabReach = 52.0
    // Gesture thresholds standing in for the React Native recognisers: a pan starts after 4 pt,
    // a tap is a touch lifted within 300 ms.
    static let panSlop = 4.0
    static let tapTime = 0.3

    // autoPlay (seconds): still at first; a breeze rises, then a gale, a gust, a hand pulls
    // the lower fly corner down and lets go, the wind drops and the banner falls still again.
    static let loop = 14.0
    static let breezeAt = 0.4
    static let galeAt = 2.4
    static let gustAt = 4.0
    static let grabAt = 5.6
    static let pullTime = 1.1
    static let releaseAt = 7.4
    static let calmAt = 8.6
    // For the last `landTime` of the loop the hanging banner is eased onto the exact resting state
    // it started from, so the loop joins without a jump.
    static let landTime = 1.8
    // Where the scripted hand pulls the corner, as a share of the banner from its hoist and top.
    static let pullTo = (x: 0.58, y: 1.55)
    // After a touch the loop waits this long before it starts again.
    static let userHold = 4.0

    static func easeInOut(_ t: Double) -> Double {
        t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }

    /// Whether a clock that went from `before` to `t` passed the moment `at`.
    static func crossed(_ before: Double, _ t: Double, _ at: Double) -> Bool { before < at && t >= at }
}

/// One frame to draw. The mesh itself lives in the engine's vertex buffer, read by the renderer.
struct ClothFlagMeshFrame {
    var number: Int
    var knots: Double
    /// The hoist's rings: the screen y of four points of the pinned column.
    var rings: [Double]
    /// The banner's outline on screen, for its shadow.
    var outline: [CGPoint]
    /// The wind setting: the readout says "CALM" at 0, "WIND" otherwise.
    var mode: Int
}

struct ClothFlagMeshLayout: Equatable {
    var top: Double
    var originX: Double { ClothFlagMeshMetrics.poleX + ClothFlagMeshMetrics.hoistGap }
    // Perspective centre: the middle of the banner at rest.
    var vpx: Double { originX + ClothFlagMeshCloth.flagW / 2 }
    var vpy: Double { top + ClothFlagMeshCloth.flagH / 2 }
}

/// The banner's cloth, the air and the autoPlay script, stepped once a frame like the RN frame
/// callback, and the gestures and the switch that pull, gust and set the wind.
@MainActor
final class ClothFlagMeshEngine {
    private typealias M = ClothFlagMeshMetrics
    private typealias C = ClothFlagMeshCloth

    /// Floats per vertex in `vertices`: position (2), flat banner point (2), shade (4), sheen (4).
    static let floatsPerVertex = 12
    /// The mesh, written in place every frame: never reallocated.
    let vertices = UnsafeMutablePointer<Float>.allocate(capacity: C.count * floatsPerVertex)
    /// The triangles, as indices into the points.
    let indices: [UInt16] = {
        var out: [UInt16] = []
        for r in 0..<(C.rows - 1) {
            for c in 0..<(C.cols - 1) {
                let i = UInt16(r * C.cols + c)
                let cols = UInt16(C.cols)
                out += [i, i + 1, i + cols, i + 1, i + cols + 1, i + cols]
            }
        }
        return out
    }()

    private var cloth: C?
    private var rest: C?
    private var layout = ClothFlagMeshLayout(top: 0)
    private static func buffer(_ n: Int) -> UnsafeMutablePointer<Double> {
        let p = UnsafeMutablePointer<Double>.allocate(capacity: n)
        p.initialize(repeating: 0, count: n)
        return p
    }
    // The cloth as drawn this frame (between its last two steps), its normals, and its points on
    // screen, for drawing and for picking the one under a finger.
    private let rx = buffer(C.count), ry = buffer(C.count), rz = buffer(C.count)
    private let nx = buffer(C.count), ny = buffer(C.count), nz = buffer(C.count)
    private let sx = buffer(C.count), sy = buffer(C.count)
    private var rings = [Double](repeating: 0, count: 4)
    private var outline = [CGPoint](repeating: .zero, count: 2 * (C.cols + C.rows) - 4)
    private var number = 0
    private var acc = 0.0
    private var lastDate: Date?
    // The air: 0…1 strength easing towards the chosen mode's, and a fading gust.
    private var level = 0.0
    private var gust = 0.0
    private(set) var mode = 0
    private var knots = 0.0
    // The point being dragged (-1 for none), where the finger holds it, and whether the script holds it.
    private var grab = -1
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var scripted = false
    // The wind a tap brings back after stilling it: the last setting that was not calm.
    private var lastWind = 1
    // The grabbed point's offset from the finger, so it does not jump to it.
    private var grabDX = 0.0
    private var grabDY = 0.0
    private var pullFromX = 0.0
    private var pullFromY = 0.0
    private var clock = 0.0
    private var hold = 0.0
    private var looping = false
    private var autoPlay: Bool?
    // The loop has run untouched since the cloth last hung at rest: at its end it may snap back exactly.
    private var pristine = true
    private var reducedMotion = false
    /// Tells the view which wind setting to show in the switch.
    var onMode: ((Int) -> Void)?

    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let soft = UIImpactFeedbackGenerator(style: .soft)

    init() {
        vertices.initialize(repeating: 0, count: C.count * Self.floatsPerVertex)
        // The flat banner coordinates each point shows never change.
        for r in 0..<C.rows {
            for c in 0..<C.cols {
                let v = (r * C.cols + c) * Self.floatsPerVertex
                vertices[v + 2] = Float(Double(c) * C.flagW / Double(C.cols - 1))
                vertices[v + 3] = Float(Double(r) * C.flagH / Double(C.rows - 1))
            }
        }
    }

    deinit {
        vertices.deallocate()
        for p in [rx, ry, rz, nx, ny, nz, sx, sy] { p.deallocate() }
    }

    func step(to date: Date, layout: ClothFlagMeshLayout, autoPlay: Bool, reducedMotion: Bool) -> ClothFlagMeshFrame {
        self.reducedMotion = reducedMotion
        let calm = reducedMotion ? 0.35 : 1
        if self.autoPlay != autoPlay {
            self.autoPlay = autoPlay
            looping = autoPlay
            clock = 0
            hold = 0
            if cloth == nil {
                mode = autoPlay ? 0 : 1
                lastWind = 1
            }
        }
        if cloth == nil || self.layout != layout {
            self.layout = layout
            let fresh = C(originX: layout.originX, originY: layout.top)
            let still = C(originX: layout.originX, originY: layout.top)
            for i in 0..<M.settleSteps {
                still.step(level: 0, gust: 0, dt: M.tick, grab: -1, grabX: 0, grabY: 0)
                if i >= M.settleSteps - M.stillSteps { still.still() }
            }
            fresh.copy(from: still)
            // A user starts in a breeze: let it fill out before the first frame.
            if !looping {
                for _ in 0..<150 { fresh.step(level: M.modeLevel[1] * calm, gust: 0, dt: M.tick, grab: -1, grabX: 0, grabY: 0) }
                level = M.modeLevel[1]
            }
            cloth = fresh
            rest = still
            grab = -1
            lastDate = nil
        }
        guard let cloth, let rest else {
            return ClothFlagMeshFrame(number: 0, knots: 0, rings: rings, outline: outline, mode: mode)
        }
        let dt = min(lastDate.map { date.timeIntervalSince($0) } ?? 0.016, Double(M.maxTicks) * M.tick)
        lastDate = date
        var pending = acc + max(0, dt)
        var ticks = 0
        while pending >= M.tick && ticks < M.maxTicks {
            tick(cloth, rest)
            pending -= M.tick
            ticks += 1
        }
        pending = min(pending, M.tick)
        acc = pending
        project(cloth, alpha: M.clamp01(pending / M.tick))
        if ticks > 0 {
            let target = C.knots(level: level, gust: gust, time: cloth.time)
            knots += (target - knots) * 0.08
        }
        number += 1
        return ClothFlagMeshFrame(number: number, knots: knots, rings: rings, outline: outline, mode: mode)
    }

    private func setWind(_ m: Int) {
        guard M.modeLevel.indices.contains(m), m != mode else { return }
        mode = m
        if m > 0 { lastWind = m }
        let report = onMode
        // Reported after this frame's update, never during it.
        DispatchQueue.main.async { report?(m) }
    }

    private func blow(_ amount: Double) {
        gust = min(M.gustMax, gust + amount)
    }

    /// The autoPlay script at time `t` after a tick from `before`.
    private func script(_ cloth: C, _ before: Double, _ t: Double) {
        let corner = C.count - 1
        if M.crossed(before, t, M.breezeAt) { setWind(1) }
        if M.crossed(before, t, M.galeAt) { setWind(2) }
        if M.crossed(before, t, M.gustAt) { blow(M.scriptGust) }
        if M.crossed(before, t, M.grabAt) {
            grab = corner
            scripted = true
            pullFromX = cloth.x[corner]
            pullFromY = cloth.y[corner]
        }
        if scripted && t >= M.grabAt && t < M.releaseAt {
            let k = M.easeInOut(M.clamp01((t - M.grabAt) / M.pullTime))
            fingerX = pullFromX + (layout.originX + C.flagW * M.pullTo.x - pullFromX) * k
            fingerY = pullFromY + (layout.top + C.flagH * M.pullTo.y - pullFromY) * k
        }
        if M.crossed(before, t, M.releaseAt) && scripted {
            grab = -1
            scripted = false
        }
        if M.crossed(before, t, M.calmAt) { setWind(0) }
    }

    /// One tick: the script, the air, then the cloth.
    private func tick(_ cloth: C, _ rest: C) {
        if grab < 0 || scripted {
            if hold > 0 {
                hold -= M.tick
                if hold <= 0 {
                    // Pick the loop up where the air already is, rather than dropping the cloth first.
                    clock = mode == 2 ? M.galeAt + 0.5 : mode == 1 ? M.breezeAt + 0.5 : 0
                }
            } else if looping {
                var before = clock
                var t = before + M.tick
                if t >= M.loop {
                    before = 0
                    t -= M.loop
                    // Untouched, the banner has hung still again: start the next loop from exactly the same state.
                    if pristine { cloth.copy(from: rest) }
                    pristine = true
                    level = 0
                    gust = 0
                    setWind(0)
                }
                clock = t
                script(cloth, before, t)
                if pristine && mode == 0 && t > M.loop - M.landTime {
                    let k = (t - (M.loop - M.landTime)) / M.landTime
                    cloth.blend(to: rest, k * k * k * 0.2)
                }
            }
        }
        let goal = M.modeLevel[mode]
        level += (goal - level) * (1 - exp(-M.tick * M.windRate))
        if abs(goal - level) < 1e-4 { level = goal }
        gust *= exp(-M.tick / M.gustFade)
        if gust < 1e-4 { gust = 0 }
        let calm = reducedMotion ? 0.35 : 1
        cloth.step(level: level * calm, gust: gust * calm, dt: M.tick, grab: grab, grabX: fingerX, grabY: fingerY)
    }

    /// The cloth as it stands between its last two steps, projected and shaded into the vertex buffer.
    private func project(_ cloth: C, alpha: Double) {
        for i in 0..<C.count {
            rx[i] = cloth.ox[i] + (cloth.x[i] - cloth.ox[i]) * alpha
            ry[i] = cloth.oy[i] + (cloth.y[i] - cloth.oy[i]) * alpha
            rz[i] = cloth.oz[i] + (cloth.z[i] - cloth.oz[i]) * alpha
        }
        C.computeNormals(rx, ry, rz, nx, ny, nz)
        let hx = M.light.x
        let hy = M.light.y
        let hz = M.light.z + 1
        let hl = (hx * hx + hy * hy + hz * hz).squareRoot()
        for i in 0..<C.count {
            let scale = M.focal / (M.focal - min(rz[i], M.focal * 0.6))
            let x = layout.vpx + (rx[i] - layout.vpx) * scale
            let y = layout.vpy + (ry[i] - layout.vpy) * scale
            sx[i] = x
            sy[i] = y
            let diffuse = max(0, nx[i] * M.light.x + ny[i] * M.light.y + nz[i] * M.light.z)
            let shade = min(1, 0.3 + 0.86 * diffuse)
            let h = max(0, (nx[i] * hx + ny[i] * hy + nz[i] * hz) / hl)
            let spec = pow(h, M.sheenPower) * M.sheen
            let v = i * Self.floatsPerVertex
            vertices[v] = Float(x)
            vertices[v + 1] = Float(y)
            vertices[v + 4] = Float(shade)
            vertices[v + 5] = Float(shade * 0.98)
            vertices[v + 6] = Float(shade * 0.95)
            vertices[v + 7] = 1
            vertices[v + 8] = Float(spec)
            vertices[v + 9] = Float(spec * 0.94)
            vertices[v + 10] = Float(spec * 0.82)
            vertices[v + 11] = 1
        }
        for (k, r) in [0, 4, 9, C.rows - 1].enumerated() {
            rings[k] = sy[r * C.cols]
        }
        // Round the edge: along the top, down the fly, back along the foot and up the hoist.
        var k = 0
        func add(_ i: Int) {
            outline[k] = CGPoint(x: sx[i], y: sy[i])
            k += 1
        }
        for c in 0..<C.cols { add(c) }
        for r in 1..<C.rows { add(r * C.cols + C.cols - 1) }
        for c in stride(from: C.cols - 2, through: 0, by: -1) { add((C.rows - 1) * C.cols + c) }
        for r in stride(from: C.rows - 2, through: 1, by: -1) { add(r * C.cols) }
    }

    // MARK: - Gestures and the switch

    private var touchStart: Date?
    private var startPoint = CGPoint.zero
    private var panning = false

    private func touched() {
        hold = M.userHold
        pristine = false
        if scripted {
            scripted = false
            grab = -1
        }
    }

    /// A screen point as a point at the cloth's depth `z`, undoing the perspective.
    private func toWorld(_ x: Double, _ y: Double, _ z: Double) -> (x: Double, y: Double) {
        let k = (M.focal - min(z, M.focal * 0.6)) / M.focal
        return (layout.vpx + (x - layout.vpx) * k, layout.vpy + (y - layout.vpy) * k)
    }

    func touchChanged(location: CGPoint, time: Date) {
        if touchStart == nil {
            touchStart = time
            startPoint = location
            panning = false
        }
        if !panning {
            if hypot(location.x - startPoint.x, location.y - startPoint.y) < M.panSlop { return }
            panning = true
            panStart(location)
        }
        guard let cloth, grab >= 0, !scripted else { return }
        let w = toWorld(location.x, location.y, cloth.z[grab])
        fingerX = w.x + grabDX
        fingerY = w.y + grabDY
    }

    private func panStart(_ p: CGPoint) {
        touched()
        guard let cloth else { return }
        var best = -1
        var bestD = M.grabReach
        for i in 0..<C.count where i % C.cols != 0 {
            let d = hypot(sx[i] - p.x, sy[i] - p.y)
            if d < bestD {
                bestD = d
                best = i
            }
        }
        if best < 0 { return }
        // Hold the point where it is and let the finger carry it from there.
        let w = toWorld(p.x, p.y, cloth.z[best])
        grabDX = cloth.x[best] - w.x
        grabDY = cloth.y[best] - w.y
        fingerX = cloth.x[best]
        fingerY = cloth.y[best]
        grab = best
        selection.selectionChanged()
    }

    func touchEnded(location: CGPoint, velocity: CGSize, time: Date) {
        defer {
            touchStart = nil
            panning = false
        }
        if panning {
            if grab >= 0 && !scripted {
                grab = -1
                hold = M.userHold
                return
            }
            // A swipe gusts the wind that is already blowing; in still air there is nothing to gust.
            let speed = hypot(velocity.width, velocity.height)
            if !(speed >= M.gustSwipe) || (mode == 0 && level < 0.05) { return }
            blow(speed * M.gustPerSpeed)
            hold = M.userHold
            light.impactOccurred()
            return
        }
        guard let start = touchStart, time.timeIntervalSince(start) <= M.tapTime else { return }
        touched()
        toggleWind()
    }

    /// A tap stills the wind, or brings back the last wind it stilled.
    private func toggleWind() {
        let on = mode == 0
        setWind(on ? lastWind : 0)
        (on ? medium : soft).impactOccurred()
    }

    /// The switch: sets the wind.
    func pick(_ m: Int) {
        guard M.modeLevel.indices.contains(m) else { return }
        hold = M.userHold
        pristine = false
        if m != mode { selection.selectionChanged() }
        mode = m
        if m > 0 { lastWind = m }
    }

    /// VoiceOver's activate: stills or raises the wind, as a tap does.
    func toggleNow() {
        touched()
        toggleWind()
    }
}
