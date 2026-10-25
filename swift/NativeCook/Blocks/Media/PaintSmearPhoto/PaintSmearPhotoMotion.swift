import Foundation
import SwiftUI

// The paint's drag field: up to `trail` points (card points), each carrying a drag vector (pt),
// stepped once a frame from the view's TimelineView and read by the shader. A step-for-step port
// of paintSmearMotion.ts. In autoPlay the points are rebuilt every frame from the loop time (three
// curved brush strokes, then the paint flowing back); under a finger new points are added and,
// when the trail is full, the two oldest merge into one so nothing already painted pops away.

enum PaintSmearPhotoTuning {
    static let trail = 24
    /// Brush radius (Gaussian sigma, pt) for the S / M / L chips.
    static let brushes = [18.0, 26.0, 36.0]
    /// The script (s): three strokes, a hold, then the paint flows back.
    static let strokes: [(t: (Double, Double), from: (Double, Double), via: (Double, Double), to: (Double, Double))] = [
        ((1.4, 2.4), (0.12, 0.3), (0.5, 0.08), (0.9, 0.36)),
        ((2.6, 3.6), (0.88, 0.6), (0.46, 0.78), (0.1, 0.5)),
        ((3.8, 4.8), (0.28, 0.94), (0.56, 0.6), (0.86, 0.86)),
    ]
    static let pointsPerStroke = trail / strokes.count
    /// Drag per scripted point (pt); a point's drag builds over pickup s as the brush passes.
    static let scriptDrag = 48.0
    static let pickup = 0.3
    static let flowBack = (6.2, 8.2)
    /// A finger lays a point every step pt; its drag is the travel × dragPerPt, at most dragMax.
    static let step = 16.0
    static let dragPerPt = 1.1
    static let dragMax = 34.0
    static let mergedMax = 70.0
    static let idleBeforeBack = 2.0
    /// Reset / double-tap / back to rest: the paint flows back over this long (s).
    static let resetFor = 0.85
    /// The brush size change (s), on the strong ease-out.
    static let brushFor = 0.18
}

enum PaintSmearPhotoMode {
    case auto, live
}

struct PaintSmearPhotoFrame {
    /// x, y, drag x, drag y per point; unused points are zero.
    var trail: [Float] = Array(repeating: 0, count: PaintSmearPhotoTuning.trail * 4)
    var sigma = PaintSmearPhotoTuning.brushes[1]
    var flow = 1.0
}

@MainActor
final class PaintSmearPhotoEngine {
    private typealias T = PaintSmearPhotoTuning

    private static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(max((t - a) / (b - a), 0), 1)
    }

    private static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    private static func easeOut(_ x: Double) -> Double {
        1 - pow(1 - x, 3)
    }

    private static let brushCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))

    /// A point on a quadratic curve and its unit tangent.
    private static func quad(_ a: (Double, Double), _ b: (Double, Double), _ c: (Double, Double), _ u: Double, _ w: Double, _ h: Double) -> (Double, Double, Double, Double) {
        let v = 1 - u
        let x = (v * v * a.0 + 2 * v * u * b.0 + u * u * c.0) * w
        let y = (v * v * a.1 + 2 * v * u * b.1 + u * u * c.1) * h
        let tx = (2 * v * (b.0 - a.0) + 2 * u * (c.0 - b.0)) * w
        let ty = (2 * v * (b.1 - a.1) + 2 * u * (c.1 - b.1)) * h
        let l = max(hypot(tx, ty), 0.0001)
        return (x, y, tx / l, ty / l)
    }

    private var configured: Bool?
    private var auto = false
    private(set) var mode: PaintSmearPhotoMode = .auto
    private var autoStart: Date?
    private var last: Date?
    private var t = 0.0
    private var now = 0.0
    private var lastTouch = -100.0
    private var x: [Double] = []
    private var y: [Double] = []
    private var ax: [Double] = []
    private var ay: [Double] = []
    private var flow = 1.0
    private var resetAt = -1.0
    private var resume = false
    private var painting = false
    private var down = false
    private var lx = 0.0, ly = 0.0
    // The brush radius tween.
    private var sigmaFrom = PaintSmearPhotoTuning.brushes[1]
    private var sigmaTo = PaintSmearPhotoTuning.brushes[1]
    private var sigmaAt = -100.0

    private func configure(auto: Bool) {
        configured = auto
        self.auto = auto
        mode = auto ? .auto : .live
        t = 0
        autoStart = nil
        lastTouch = -100
        clearTrail()
        flow = 1
        resetAt = -1
        resume = false
        painting = false
    }

    private var sigma: Double {
        let k = min(max((now - sigmaAt) / T.brushFor, 0), 1)
        return sigmaFrom + (sigmaTo - sigmaFrom) * Self.brushCurve.value(at: k)
    }

    /// True when nothing on screen can change until the next touch (the timeline may pause).
    var isIdle: Bool {
        mode == .live && !auto && !down && resetAt < 0 && now - sigmaAt > T.brushFor
    }

    func step(to date: Date, w: Double, h: Double, auto: Bool) -> PaintSmearPhotoFrame {
        if configured != auto { configure(auto: auto) }
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 1.0 / 20)
        if h > 0 {
            now += dt
            if mode == .auto {
                if auto {
                    let start = autoStart ?? date
                    autoStart = start
                    t = date.timeIntervalSince(start).truncatingRemainder(dividingBy: PaintSmearPhoto.loop)
                    scriptFrame(t, w, h)
                }
            } else {
                if resetFrame() && resume {
                    resume = false
                    mode = .auto
                    t = 0
                    autoStart = date
                }
                if auto && !painting && !down && resetAt < 0 && now - lastTouch > T.idleBeforeBack {
                    if !x.isEmpty {
                        startReset(resume: true)
                    } else {
                        mode = .auto
                        t = 0
                        autoStart = date
                    }
                }
            }
        }
        var f = PaintSmearPhotoFrame()
        for i in 0..<min(x.count, T.trail) {
            f.trail[i * 4] = Float(x[i])
            f.trail[i * 4 + 1] = Float(y[i])
            f.trail[i * 4 + 2] = Float(ax[i])
            f.trail[i * 4 + 3] = Float(ay[i])
        }
        f.sigma = sigma
        f.flow = flow
        return f
    }

    private func clearTrail() {
        x.removeAll(keepingCapacity: true)
        y.removeAll(keepingCapacity: true)
        ax.removeAll(keepingCapacity: true)
        ay.removeAll(keepingCapacity: true)
    }

    private func scriptFrame(_ t: Double, _ w: Double, _ h: Double) {
        clearTrail()
        for st in T.strokes {
            for j in 0..<T.pointsPerStroke {
                // Points are laid at even times; the brush accelerates out of the press and slows
                // into the lift, so they bunch up at both ends of the stroke.
                let tau = Double(j) / Double(T.pointsPerStroke - 1)
                let born = st.t.0 + (st.t.1 - st.t.0) * tau
                if t < born { continue }
                let u = Self.easeInOut(tau)
                let (px, py, dx, dy) = Self.quad(st.from, st.via, st.to, u, w, h)
                let a = T.scriptDrag * Self.easeOut(Self.seg(t, born, born + T.pickup))
                x.append(px)
                y.append(py)
                ax.append(dx * a)
                ay.append(dy * a)
            }
        }
        flow = 1 - Self.easeInOut(Self.seg(t, T.flowBack.0, T.flowBack.1))
    }

    /// A finger took over: keep the field exactly as it looks, with the flow baked into the drags.
    private func takeOver() {
        for i in 0..<ax.count {
            ax[i] *= flow
            ay[i] *= flow
        }
        flow = 1
        resetAt = -1
        mode = .live
    }

    /// Lays a point between the last one and (px, py), dragging the paint along the travel.
    private func lay(_ px: Double, _ py: Double) {
        let dx = px - lx
        let dy = py - ly
        let d = hypot(dx, dy)
        if d < 0.5 { return }
        if x.count >= T.trail {
            // Merge the two oldest: a drag-weighted position, the summed drag (capped).
            let w0 = hypot(ax[0], ay[0]) + 0.001
            let w1 = hypot(ax[1], ay[1]) + 0.001
            let mx = (x[0] * w0 + x[1] * w1) / (w0 + w1)
            let my = (y[0] * w0 + y[1] * w1) / (w0 + w1)
            var sx = ax[0] + ax[1]
            var sy = ay[0] + ay[1]
            let m = hypot(sx, sy)
            if m > T.mergedMax {
                sx *= T.mergedMax / m
                sy *= T.mergedMax / m
            }
            x.replaceSubrange(0..<2, with: [mx])
            y.replaceSubrange(0..<2, with: [my])
            ax.replaceSubrange(0..<2, with: [sx])
            ay.replaceSubrange(0..<2, with: [sy])
        }
        let a = min(d * T.dragPerPt, T.dragMax)
        x.append((px + lx) / 2)
        y.append((py + ly) / 2)
        ax.append(dx / d * a)
        ay.append(dy / d * a)
        lx = px
        ly = py
    }

    private func startReset(resume: Bool) {
        if resetAt >= 0 { return }
        resetAt = now
        self.resume = resume
    }

    /// The flow-back of a reset; true when it has finished.
    private func resetFrame() -> Bool {
        if resetAt < 0 { return false }
        let k = Self.easeInOut(min((now - resetAt) / T.resetFor, 1))
        flow = 1 - k
        if k >= 1 {
            clearTrail()
            flow = 1
            resetAt = -1
            return true
        }
        return false
    }

    // MARK: Touch

    func touched() {
        lastTouch = now
        if mode != .live { takeOver() }
    }

    /// Reset or double-tap: the paint flows back.
    func reset() {
        touched()
        startReset(resume: false)
    }

    func setBrush(_ index: Int) {
        sigmaFrom = sigma
        sigmaTo = T.brushes[index]
        sigmaAt = now
    }

    /// A finger on the photo (card points). The stroke starts once it has moved 4 pt.
    func dragChanged(start: CGPoint, location: CGPoint) {
        if !down {
            down = true
            touched()
            lx = start.x
            ly = start.y
        }
        lastTouch = now
        if !painting {
            guard hypot(location.x - start.x, location.y - start.y) >= 4 else { return }
            painting = true
            // A stroke stops a flow-back that was under way where it is.
            if resetAt >= 0 { takeOver() }
        }
        if hypot(location.x - lx, location.y - ly) >= T.step { lay(location.x, location.y) }
    }

    func dragEnded() {
        down = false
        painting = false
        lastTouch = now
    }
}
