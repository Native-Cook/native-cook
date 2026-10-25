import Foundation
import UIKit

// The card's warmth and tilt, stepped once a frame from the view's TimelineView. A step-for-step
// port of thermalMotion.ts.
//
// Heat lives in a ring of heatSlots points (card points, and the frame-clock time each was laid
// down). A point's strength falls from heatPeak to 0 over heatLife seconds, so the reveal follows
// the thumb and closes behind it. In autoPlay the ring is rebuilt every frame from the loop time
// alone: a thumb sweeps an S-curve down the card (one point every emitEvery s), then the whole card
// warms and cools back. A touch keeps the same ring and goes on adding to it, so nothing jumps.

enum ThermalRevealCardTuning {
    static let heatSlots = 24
    static let heatLife = 1.4
    static let heatPeak = 0.42
    /// Gaussian radius of one warm spot (pt).
    static let heatRadius = 30.0
    /// A resting thumb keeps laying points down this often (s), so warmth builds under it.
    static let emitEvery = heatLife / Double(heatSlots - 1)
    // The thumb's sweep and the whole-card warmth (s).
    static let sweep = (1.5, 5.5)
    static let globUp = (4.6, 5.5)
    static let globDown = (6.4, 8.2)
    /// Card tilt limit (deg) and the drag that reaches it (deg per pt).
    static let tiltMax = 8.0
    static let tiltPerPt = 0.07
    static let idleBeforeBack = 2.0
    static let backFor = 0.8
    /// The three coves (fractions of the card), in route order. The autoPlay thumb passes over them.
    static let coves: [(Double, Double)] = [(0.28, 0.24), (0.71, 0.52), (0.33, 0.8)]
}

enum ThermalRevealCardMode {
    case auto, live, back
}

struct ThermalRevealCardFrame {
    /// x, y, strength, radius per slot.
    var heat: [Float] = Array(repeating: 0, count: ThermalRevealCardTuning.heatSlots * 4)
    var glob = 0.0
    var gloss = 0.3
    var rx = 0.0
    var ry = 0.0
}

@MainActor
final class ThermalRevealCardEngine {
    private typealias T = ThermalRevealCardTuning

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(max((t - a) / (b - a), 0), 1)
    }

    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func easeOut(_ x: Double) -> Double {
        1 - pow(1 - x, 3)
    }

    /// A point's warmth at age a (s): a quick rise, then a slow fall to nothing at heatLife.
    static func heatStrength(_ age: Double) -> Double {
        if age < 0 || age >= T.heatLife { return 0 }
        let rise = min(age / 0.08, 1)
        let fall = 1 - age / T.heatLife
        return T.heatPeak * rise * pow(fall, 1.5)
    }

    /// The thumb's S-curve over the card at sweep progress u (0…1), as fractions of the card.
    private static func thumbAt(_ u: Double) -> (Double, Double) {
        (0.5 - 0.22 * cos(2 * .pi * u), 0.14 + 0.74 * u)
    }

    private static func spring(_ x: Double, _ v: Double, _ x0: Double, _ w: Double, _ z: Double, _ dt: Double) -> (Double, Double) {
        let nv = v + (-w * w * (x - x0) - 2 * z * w * v) * dt
        return (x + nv * dt, nv)
    }

    private var configured: (auto: Bool, reduced: Bool)?
    private var auto = false
    private(set) var mode: ThermalRevealCardMode = .auto
    private var autoStart: Date?
    private var last: Date?
    private var t = 0.0
    private var now = 0.0
    private var lastTouch = -100.0
    private var hx = [Double](repeating: 0, count: ThermalRevealCardTuning.heatSlots)
    private var hy = [Double](repeating: 0, count: ThermalRevealCardTuning.heatSlots)
    private var ht = [Double](repeating: -100, count: ThermalRevealCardTuning.heatSlots)
    private var glob = 0.0
    private var warming = false
    private var fx = 0.0, fy = 0.0
    private var lastEmit = -100.0
    private var ex = 0.0, ey = 0.0
    private var steady = false
    private var rx = 0.0, ry = 0.0
    private var vrx = 0.0, vry = 0.0
    private var tiltX = 0.0, tiltY = 0.0
    private var tilting = false
    private var gloss = 0.3
    private var found = [false, false, false]
    private var backAt = 0.0
    private var backGlob = 0.0
    private var backGloss = 0.3
    private var fade = 1.0
    private var dragging = false
    private var dragStart = CGPoint.zero
    private let selection = UISelectionFeedbackGenerator()

    private func configure(auto: Bool, reduced: Bool) {
        configured = (auto, reduced)
        self.auto = auto
        mode = auto ? .auto : .live
        t = 0
        autoStart = nil
        lastTouch = -100
        hx = Array(repeating: 0, count: T.heatSlots)
        hy = Array(repeating: 0, count: T.heatSlots)
        ht = Array(repeating: -100, count: T.heatSlots)
        glob = reduced ? 1 : 0
        warming = false
        lastEmit = -100
        steady = reduced
        rx = 0; ry = 0; vrx = 0; vry = 0
        tiltX = 0; tiltY = 0
        tilting = false
        gloss = 0.3
        found = [false, false, false]
        fade = 1
    }

    /// True when nothing on screen can change until the next touch (the timeline may pause).
    var isIdle: Bool {
        guard mode == .live, !auto, !warming, !tilting, !dragging else { return false }
        let still = abs(rx) < 0.005 && abs(ry) < 0.005 && abs(vrx) < 0.01 && abs(vry) < 0.01
        let cold = ht.allSatisfy { Self.heatStrength(now - $0) == 0 }
        return still && cold && (steady || glob < 0.002)
    }

    func step(to date: Date, w: Double, h: Double, auto: Bool, reduced: Bool) -> ThermalRevealCardFrame {
        if configured?.auto != auto || configured?.reduced != reduced {
            configure(auto: auto, reduced: reduced)
        }
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 1.0 / 20)
        if h > 0 {
            now += dt
            switch mode {
            case .auto:
                if auto {
                    let start = autoStart ?? date
                    autoStart = start
                    t = date.timeIntervalSince(start).truncatingRemainder(dividingBy: ThermalRevealCard.loop)
                    scriptFrame(t, w, h)
                }
            case .live:
                liveFrame(dt)
                // One light tick per cove uncovered by a finger.
                for i in 0..<3 {
                    let heat = heatAt(T.coves[i].0 * w, T.coves[i].1 * h, w, h)
                    if !found[i] && heat > 0.85 && warming {
                        found[i] = true
                        selection.selectionChanged()
                    } else if found[i] && heat < 0.4 {
                        found[i] = false
                    }
                }
                let idle = !warming && !tilting && now - lastTouch > T.idleBeforeBack
                if auto && idle { beginBack() }
            case .back:
                if backFrame(dt) {
                    mode = .auto
                    t = 0
                    autoStart = date
                }
            }
        }
        var f = ThermalRevealCardFrame()
        for j in 0..<T.heatSlots {
            f.heat[j * 4] = Float(hx[j])
            f.heat[j * 4 + 1] = Float(hy[j])
            f.heat[j * 4 + 2] = Float(Self.heatStrength(now - ht[j]) * fade)
            f.heat[j * 4 + 3] = Float(T.heatRadius)
        }
        f.glob = glob
        f.gloss = gloss
        f.rx = rx
        f.ry = ry
        return f
    }

    /// Rebuilds the ring from loop time t: the points the scripted thumb laid in the last heatLife s.
    private func scriptFrame(_ t: Double, _ w: Double, _ h: Double) {
        let (a, b) = T.sweep
        let count = Int(floor((b - a) / T.emitEvery))
        let newest = Int(floor((t - a) / T.emitEvery))
        for j in 0..<T.heatSlots {
            let k = newest - (T.heatSlots - 1) + j
            ht[j] = -100
            if k < 0 || k > count { continue }
            let te = a + Double(k) * T.emitEvery
            let age = t - te
            if age < 0 || age >= T.heatLife { continue }
            // The thumb slows at the ends of its stroke (ease in-out over the sweep).
            let (ux, uy) = Self.thumbAt(Self.easeInOut(Self.seg(te, a, b)))
            hx[j] = ux * w
            hy[j] = uy * h
            // Stored as a time on the frame clock, so a touch can take over the ring as it is.
            ht[j] = now - age
        }
        glob = Self.easeOut(Self.seg(t, T.globUp.0, T.globUp.1)) * (1 - Self.easeInOut(Self.seg(t, T.globDown.0, T.globDown.1)))
        // Idle gloss and a gentle sway of whole-loop sines.
        let loop = ThermalRevealCard.loop
        gloss = 0.3 + 0.22 * sin(2 * .pi * t / loop)
        rx = 1.6 * sin(2 * .pi * t / loop)
        ry = -1.1 * sin(2 * .pi * 2 * t / loop)
        fade = 1
    }

    /// Lays one heat point at (x, y), over the coldest slot.
    private func emit(_ x: Double, _ y: Double) {
        var slot = 0
        var oldest = Double.infinity
        for j in 0..<T.heatSlots where ht[j] < oldest {
            oldest = ht[j]
            slot = j
        }
        hx[slot] = x
        hy[slot] = y
        ht[slot] = now
        lastEmit = now
        ex = x
        ey = y
    }

    private func liveFrame(_ dt: Double) {
        if warming && now - lastEmit >= T.emitEvery { emit(fx, fy) }
        if !steady { glob *= exp(-1.4 * dt) }
        let tx = tilting ? tiltX : 0
        let ty = tilting ? tiltY : 0
        let w = tilting ? 22.0 : 11.0
        let z = tilting ? 0.95 : 0.55
        (rx, vrx) = Self.spring(rx, vrx, tx, w, z, dt)
        (ry, vry) = Self.spring(ry, vry, ty, w, z, dt)
        gloss = 0.3 + (ry / T.tiltMax) * 0.35 - (rx / T.tiltMax) * 0.15
    }

    private func beginBack() {
        mode = .back
        backAt = now
        backGlob = glob
        backGloss = gloss
        warming = false
        tilting = false
    }

    /// Easing back to the first frame of the loop; true when done.
    private func backFrame(_ dt: Double) -> Bool {
        let k = Self.easeInOut(min((now - backAt) / T.backFor, 1))
        fade = 1 - k
        glob = backGlob * (1 - k)
        gloss = backGloss + (0.3 - backGloss) * k
        (rx, vrx) = Self.spring(rx, vrx, 0, 11, 0.9, dt)
        (ry, vry) = Self.spring(ry, vry, 0, 11, 0.9, dt)
        if k >= 1 {
            rx = 0; ry = 0; vrx = 0; vry = 0
            fade = 1
            for j in 0..<T.heatSlots { ht[j] = -100 }
            return true
        }
        return false
    }

    /// Warmth at a card point, as the shader sums it (without the noise).
    private func heatAt(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Double {
        var sum = 0.0
        for j in 0..<T.heatSlots {
            let st = Self.heatStrength(now - ht[j]) * fade
            if st <= 0 { continue }
            let dx = x - hx[j]
            let dy = y - hy[j]
            sum += st * exp(-(dx * dx + dy * dy) / (2 * T.heatRadius * T.heatRadius))
        }
        let cx = x / w - 0.5
        let cy = y / h - 0.5
        return sum + glob * (1.25 - 0.7 * hypot(cx, cy))
    }

    private static func soft(_ x: Double) -> Double {
        T.tiltMax * tanh(x / T.tiltMax)
    }

    // MARK: Touch (card points: the card's top-left corner is 0, 0)

    func dragChanged(_ p: CGPoint, translation: CGSize, w: Double, h: Double) {
        if !dragging {
            dragging = true
            dragStart = p
            lastTouch = now
            if mode != .live {
                mode = .live
                fade = 1
                vrx = 0
                vry = 0
            }
            if p.x >= 0 && p.x <= w && p.y >= 0 && p.y <= h {
                warming = true
                fx = p.x
                fy = p.y
                emit(p.x, p.y)
                selection.prepare()
            } else {
                tilting = true
                tiltX = rx
                tiltY = ry
            }
            return
        }
        lastTouch = now
        if warming {
            let x = min(max(p.x, 0), w)
            let y = min(max(p.y, 0), h)
            // Lay a point every 12 pt of travel as well as every emitEvery while resting.
            fx = x
            fy = y
            if hypot(x - ex, y - ey) > 12 { emit(x, y) }
        } else if tilting {
            tiltY = Self.soft(translation.width * T.tiltPerPt)
            tiltX = Self.soft(-translation.height * T.tiltPerPt)
        }
    }

    func dragEnded(velocity: CGSize) {
        if tilting {
            vry = velocity.width * T.tiltPerPt * 0.5
            vrx = -velocity.height * T.tiltPerPt * 0.5
        }
        warming = false
        tilting = false
        dragging = false
        lastTouch = now
    }
}
