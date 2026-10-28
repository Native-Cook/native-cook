import Foundation
import CoreGraphics

/// Timing, layout, copy and colours of the Exploded Chip Shield, and the frame it draws. Every value
/// is a function of the loop time (autoPlay) or of the open amount q (touch), so the stacks match.
enum ExplodedChipShieldModel {
    static let loop: Double = 7.0

    // MARK: Copy

    static let brand = "Ferrin"
    static let skip = "Skip"
    static let eyebrow = "VAULT-GRADE PROTECTION"
    static let title = "Every layer,\nencrypted."
    static let body = "Your vault is sealed on this device\nand opens only for you."
    static let cta = "Continue"
    /// Callouts, top to bottom: (layer index, title, caption).
    static let callouts: [(layer: Int, title: String, caption: String)] = [
        (5, "Alloy shell", "Tamper-sealed"),
        (4, "Cipher mesh", "256-bit cipher"),
        (2, "Key enclave", "On-device keys"),
    ]

    // MARK: Colours

    static let ink: UInt32 = 0x14171B
    static let grey: UInt32 = 0x6B7280
    static let caption: UInt32 = 0x7A818C
    static let teal: UInt32 = 0x1E9C8A
    static let glow: UInt32 = 0x5FD3C2
    static let line: UInt32 = 0x9AA3AD
    static let dotOff: UInt32 = 0xC9CED5
    static let bgTop: UInt32 = 0xFFFFFF
    static let bgBottom: UInt32 = 0xEEF1F4

    // MARK: Layout (pt)

    static let unit: CGFloat = 150          // side of the lid
    static let squash: CGFloat = 0.58       // isometric: y scale after the 45° turn
    static let restYaw: Double = .pi / 4
    static let shift: CGFloat = 40          // the stack slides left this much as it opens
    static let textBlock: CGFloat = 280     // from the top of the eyebrow to the bottom safe edge
    static let closedLift: CGFloat = 206    // anchor of the closed stack above the text block
    static let openDrop: CGFloat = 106      // the anchor moves down this much when open
    static let dragSpan: CGFloat = 240      // a drag of 240 pt opens the stack fully
    static let closedZoom: CGFloat = 0.16   // the closed chip is drawn 16 % larger

    struct Layer {
        let side: CGFloat      // fraction of unit
        let radius: CGFloat    // fraction of unit
        let thick: CGFloat
        let zClosed: CGFloat
        let zOpen: CGFloat
        let rank: Double       // 0 leads when opening (lid) … 1 trails (tray)
        let fall: Double       // start of the fall in the autoPlay collapse
    }

    /// Bottom to top: base, tray, core, frosted ring, glass sheet, lid.
    static let layers: [Layer] = [
        Layer(side: 1.00, radius: 0.22, thick: 9, zClosed: 0, zOpen: 0, rank: 0, fall: 0),
        Layer(side: 0.90, radius: 0.18, thick: 7, zClosed: 9, zOpen: 50, rank: 1.00, fall: 4.70),
        Layer(side: 0.74, radius: 0.15, thick: 5, zClosed: 9, zOpen: 76, rank: 0.75, fall: 4.76),
        Layer(side: 0.84, radius: 0.17, thick: 8, zClosed: 9, zOpen: 128, rank: 0.50, fall: 4.82),
        Layer(side: 0.92, radius: 0.20, thick: 2.5, zClosed: 9, zOpen: 184, rank: 0.25, fall: 4.88),
        Layer(side: 1.00, radius: 0.22, thick: 10, zClosed: 9, zOpen: 240, rank: 0, fall: 4.94),
    ]
    static let discSide: CGFloat = 0.44     // the raised disc on the core, fraction of unit
    static let discThick: CGFloat = 7
    static let holeSide: CGFloat = 0.50     // round hole of the frosted ring
    static let trayHole: CGFloat = 0.76     // square hole of the tray
    static let landing: Double = 5.56       // the lid lands (fall 4.94 + 0.62)

    // MARK: Curves

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeIn(_ x: Double) -> Double { x * x * x }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func lerp(_ a: CGFloat, _ b: CGFloat, _ t: Double) -> CGFloat { a + (b - a) * CGFloat(t) }

    /// Damped spring (response 0.55 s, damping ratio 0.72) stepping 0 → 1 at τ = 0.
    static let springW: Double = 2 * .pi / 0.55
    static let springZ: Double = 0.72
    static func springStep(_ tau: Double) -> Double {
        if tau <= 0 { return 0 }
        if tau >= 1.6 { return 1 }
        let a = springZ * springW
        let wd = springW * sqrt(1 - springZ * springZ)
        return 1 - exp(-a * tau) * (cos(wd * tau) + a / wd * sin(wd * tau))
    }

    /// The landing bump: a fast damped wobble that starts and ends at 0.
    static func bump(_ tau: Double) -> Double {
        if tau <= 0 || tau >= 0.7 { return 0 }
        return exp(-9 * tau) * sin(18 * tau)
    }

    // MARK: Frames

    /// The autoPlay timeline (seconds in 0 ..< loop). Frames 0 and `loop` are both the closed icon.
    static func timeline(_ t: Double) -> ExplodedChipShieldFrame {
        var f = ExplodedChipShieldFrame()
        f.t = t
        let close = easeInOut(seg(t, 4.60, 5.70))
        f.open = easeInOut(seg(t, 1.00, 2.20)) * (1 - close)
        for i in 1 ..< layers.count {
            let l = layers[i]
            let rise = springStep(t - 1.0 - 0.07 * 4 * l.rank)
            let fall = easeIn(seg(t, l.fall, l.fall + 0.62))
            f.p[i] = rise * (1 - fall)
        }
        for k in 0 ..< callouts.count {
            let d = 0.08 * Double(k)
            f.callout[k] = easeOut(seg(t, 1.90 + d, 2.50 + d)) * (1 - easeIn(seg(t, 4.50, 4.80)))
        }
        // The view sways a few degrees while open, so the highlights travel.
        f.yaw = restYaw + 5 * .pi / 180 * sin(2 * .pi * t / loop - 1.2) * f.open
        let b = bump(t - landing)
        f.lidBump = 5 * max(0, b)
        f.dip = 1.5 * b
        return f
    }

    /// The same stack driven by the open amount q (0 closed … 1 open) under the finger.
    static func manual(_ q: Double, yawOffset: Double, t: Double) -> ExplodedChipShieldFrame {
        var f = ExplodedChipShieldFrame()
        f.t = t
        f.open = min(max(q, 0), 1.06)
        for i in 1 ..< layers.count {
            let r = layers[i].rank
            f.p[i] = min(max((q - 0.3 * r) / 0.7, 0), 1) + max(0, q - 1)
        }
        for k in 0 ..< callouts.count {
            let d = 0.05 * Double(k)
            f.callout[k] = easeOut(seg(q, 0.70 + d, 0.92 + d))
        }
        f.yaw = restYaw + yawOffset
        return f
    }

    /// Height of layer i's bottom face, with a slow bob while it floats.
    static func z(_ i: Int, _ f: ExplodedChipShieldFrame) -> CGFloat {
        let l = layers[i]
        guard i > 0 else { return 0 }
        let p = f.p[i]
        let bob = 3.5 * sin(2 * .pi * 2 * f.t / loop + 0.6 * Double(i)) * min(p, 1)
        var z = l.zClosed + (l.zOpen - l.zClosed) * CGFloat(p) + CGFloat(bob)
        if i == layers.count - 1 { z += CGFloat(f.lidBump) }
        return z
    }
}

struct ExplodedChipShieldFrame {
    var t: Double = 0                       // loop time (bob)
    var open: Double = 0                    // the whole stack: slide, glow, halo rings
    var p: [Double] = [0, 0, 0, 0, 0, 0]    // per layer: closed 0 … open 1
    var callout: [Double] = [0, 0, 0]
    var yaw: Double = ExplodedChipShieldModel.restYaw
    var lidBump: Double = 0
    var dip: Double = 0
}

/// Plays the timeline, hands over to the finger, springs home 2 s after the last touch.
final class ExplodedChipShieldDriver {
    typealias M = ExplodedChipShieldModel

    /// A damped spring from (x0, v0) towards a target, as a pure function of time.
    struct Spring {
        var from: Double
        var velocity: Double
        var to: Double
        var start: Double
        var zeta: Double = M.springZ

        func value(_ now: Double) -> Double {
            let tau = max(0, now - start)
            let w = M.springW
            let a = zeta * w
            let wd = w * sqrt(1 - zeta * zeta)
            let A = from - to
            let B = (velocity + a * A) / wd
            return to + exp(-a * tau) * (A * cos(wd * tau) + B * sin(wd * tau))
        }

        func done(_ now: Double) -> Bool { now - start > 1.4 }
    }

    private var epoch: Double?
    private(set) var manual = false
    private(set) var dragging = false
    private var q: Spring = Spring(from: 0, velocity: 0, to: 0, start: 0)
    private var tilt: Spring = Spring(from: 0, velocity: 0, to: 0, start: 0, zeta: 0.6)
    private var q0: Double = 0
    private var yaw0: Double = 0
    private var lastTouch: Double = -100
    private var returning = false
    // Reduced motion: 1 shows the open stack, 0 the closed icon; a tap crossfades in 0.25 s.
    private var shown: Double = 1
    private var fadeFrom: Double = 1
    private var fadeStart: Double = -100

    func reset() {
        epoch = nil
        manual = false
        dragging = false
        returning = false
        q = Spring(from: 0, velocity: 0, to: 0, start: 0)
        tilt = Spring(from: 0, velocity: 0, to: 0, start: 0, zeta: 0.6)
        shown = 1
        fadeFrom = 1
        fadeStart = -100
    }

    private func loopTime(_ now: Double) -> Double {
        if epoch == nil { epoch = now }
        return (now - (epoch ?? now)).truncatingRemainder(dividingBy: M.loop)
    }

    func frame(at now: Double, auto: Bool, reduced: Bool) -> ExplodedChipShieldFrame {
        let t = loopTime(now)
        if reduced { return M.manual(1, yawOffset: 0, t: 0) }
        if manual {
            if auto && !dragging && now - lastTouch > 2 {
                if !returning {
                    returning = true
                    q = Spring(from: q.value(now), velocity: 0, to: 0, start: now)
                } else if q.done(now) && tilt.done(now) {
                    manual = false
                    returning = false
                    epoch = now
                    return M.timeline(0)
                }
            }
            return M.manual(q.value(now), yawOffset: tilt.value(now), t: t)
        }
        return auto ? M.timeline(t) : M.manual(0, yawOffset: 0, t: t)
    }

    /// Whether the stack is still moving without the loop (Compose and RN stop their frame loop).
    func busy(at now: Double) -> Bool {
        manual && (dragging || !q.done(now) || !tilt.done(now) || now - lastTouch < 2.2)
    }

    func shownAmount(at now: Double) -> Double {
        let k = M.seg(now, fadeStart, fadeStart + 0.25)
        return fadeFrom + (shown - fadeFrom) * k
    }

    func toggleReduced(at now: Double) {
        fadeFrom = shownAmount(at: now)
        shown = shown > 0.5 ? 0 : 1
        fadeStart = now
    }

    func begin(at now: Double, auto: Bool) {
        let f = frame(at: now, auto: auto, reduced: false)
        let current = manual ? q.value(now) : f.open
        yaw0 = f.yaw - M.restYaw
        q = Spring(from: current, velocity: 0, to: current, start: now)
        tilt = Spring(from: yaw0, velocity: 0, to: yaw0, start: now)
        q0 = current
        manual = true
        dragging = true
        returning = false
        lastTouch = now
    }

    /// dx, dy: the finger's travel since the touch began (pt). Up opens, sideways tilts.
    func drag(dx: CGFloat, dy: CGFloat, at now: Double) {
        var v = q0 + Double(-dy / M.dragSpan)
        if v > 1 { let over = v - 1; v = 1 + over / (1 + over * 3) * 0.35 }
        if v < 0 { let over = -v; v = -(over / (1 + over * 3) * 0.35) }
        q = Spring(from: v, velocity: 0, to: v, start: now)
        let tiltDeg = min(max(Double(dx) * 0.08, -10), 10)
        tilt = Spring(from: yaw0 + tiltDeg * .pi / 180, velocity: 0, to: yaw0 + tiltDeg * .pi / 180, start: now, zeta: 0.6)
        lastTouch = now
    }

    /// Ends the drag or tap; returns true when the stack commits to the other state.
    func end(vy: CGFloat, tap: Bool, at now: Double) -> Bool {
        dragging = false
        lastTouch = now
        let current = q.value(now)
        let wasOpen = q0 >= 0.5
        let target: Double
        let velocity = Double(-vy / M.dragSpan)
        if tap {
            target = wasOpen ? 0 : 1
        } else {
            target = current + 0.15 * velocity > 0.5 ? 1 : 0
        }
        q = Spring(from: current, velocity: tap ? 0 : velocity, to: target, start: now)
        tilt = Spring(from: tilt.value(now), velocity: 0, to: 0, start: now, zeta: 0.6)
        return (target > 0.5) != wasOpen
    }
}
