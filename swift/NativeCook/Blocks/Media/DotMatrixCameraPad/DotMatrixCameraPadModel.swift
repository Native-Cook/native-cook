import Foundation
import CoreGraphics

/*
 The numbers behind Dot Matrix Camera Pad: the controls' state, the autoPlay timeline, the
 layout and the night street the viewfinder shows.
 */

/// Everything the screen shows at a moment.
struct DotMatrixCameraPadState {
    /// Exposure compensation in stops.
    var ev = 0.0
    /// White balance in kelvin.
    var warmth = 5200.0
    /// Left module: the A/M knob (0 = A, 1 = M) and the wheel-to-pad morph (0 = wheel, 1 = pad).
    var knob = 0.0
    var morph = 0.0
    /// Right module's A/M knob.
    var knobR = 0.0
    /// The pad's hot spot (0…1, x = shutter from slow to fast, y = ISO from low to high) and its glow.
    var px = DotMatrixCameraPadModel.restX
    var py = DotMatrixCameraPadModel.restY
    var glow = 0.0
    /// Viewfinder zoom (0.72, 1, 1.7 for the three lenses).
    var zoom = 1.0
    /// Shutter press (0…1), the capture blink (0…1) and the gallery thumbnail's scale.
    var press = 0.0
    var flash = 0.0
    var pop = 1.0

    static func mix(_ a: Self, _ b: Self, _ u: Double) -> Self {
        func l(_ x: Double, _ y: Double) -> Double { x + (y - x) * u }
        return Self(ev: l(a.ev, b.ev), warmth: l(a.warmth, b.warmth), knob: l(a.knob, b.knob), morph: l(a.morph, b.morph),
                    knobR: l(a.knobR, b.knobR), px: l(a.px, b.px), py: l(a.py, b.py), glow: l(a.glow, b.glow),
                    zoom: l(a.zoom, b.zoom), press: l(a.press, b.press), flash: l(a.flash, b.flash), pop: l(a.pop, b.pop))
    }
}

enum DotMatrixCameraPadModel {
    static let loop = 10.0
    static let shutterLabels = ["1/4", "1/8", "1/15", "1/30", "1/60", "1/125", "1/250", "1/500", "1/1000", "1/2000", "1/4000"]
    static let isoLabels = ["100", "200", "400", "800", "1600", "3200", "6400", "12800"]
    static let cols = 11, rows = 8
    /// 1/60 s and ISO 400.
    static let restX = 0.4, restY = 2.0 / 7
    static let lenses: [(label: String, zoom: Double)] = [("13", 0.72), ("24", 1.0), ("77", 1.7)]
    static let amber = (r: 0.96, g: 0.75, b: 0.16)

    /// Shutter time in seconds along the pad's x (log-spaced: 1/4 s at 0, 1/4000 s at 1).
    static func shutter(_ x: Double) -> Double { 0.25 * pow(2, -x * 10) }
    static func iso(_ y: Double) -> Double { 100 * pow(2, y * 7) }
    /// The picture's brightness in stops relative to the resting settings.
    static func exposure(_ s: DotMatrixCameraPadState) -> Double {
        s.ev + log2(iso(s.py) / 400) + log2(shutter(s.px) * 60)
    }

    static func evLabel(_ ev: Double) -> String {
        let r = (ev * 10).rounded() / 10
        return (r < 0 ? "-" : "+") + String(format: "%.1f", abs(r))
    }

    // MARK: Easing

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    static func easeInOut(_ x: Double) -> Double {
        let u = clamp01(x)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }
    static func easeOutBack(_ x: Double) -> Double {
        let u = clamp01(x), c1 = 1.4, c3 = c1 + 1
        return 1 + c3 * pow(u - 1, 3) + c1 * pow(u - 1, 2)
    }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { easeInOut((t - a) / (b - a)) }

    // MARK: autoPlay

    /// The finger's path across the pad: (time, x, y), smoothed between keys.
    static let path: [(Double, Double, Double)] = [(3.4, restX, restY), (4.6, 0.1, 0), (5.0, 0.1, 0), (6.4, 0.9, 6.0 / 7), (7.2, restX, restY)]

    static func state(at time: Double) -> DotMatrixCameraPadState {
        var t = time.truncatingRemainder(dividingBy: loop)
        if t < 0 { t += loop }
        var s = DotMatrixCameraPadState()
        s.ev = 0.6 * seg(t, 0.6, 1.8) - 0.6 * seg(t, 8.2, 9.0)
        s.knob = seg(t, 2.3, 2.55) - seg(t, 7.5, 7.75)
        s.morph = seg(t, 2.4, 3.0) - seg(t, 7.6, 8.2)
        s.glow = seg(t, 3.1, 3.4) - seg(t, 7.2, 7.6)
        for i in 0..<(path.count - 1) {
            let (t0, x0, y0) = path[i], (t1, x1, y1) = path[i + 1]
            if t >= t0 && t < t1 {
                let u = easeInOut((t - t0) / (t1 - t0))
                // The long diagonal bows a little, like a thumb's arc.
                let bow = i == 2 ? 0.12 * sin(Double.pi * u) : 0
                s.px = clamp01(x0 + (x1 - x0) * u + bow)
                s.py = clamp01(y0 + (y1 - y0) * u - bow)
            }
        }
        // The shot at 9.2 s.
        let d = t - 9.2
        if d >= 0 && d < 0.25 { s.press = sin(Double.pi * d / 0.25) }
        if d >= 0.04 && d < 0.4 { s.flash = 0.85 * pow(1 - (d - 0.04) / 0.36, 2) }
        if d >= 0.05 && d < 0.5 { s.pop = 0.82 + 0.18 * easeOutBack((d - 0.05) / 0.4) }
        return s
    }

    // MARK: Layout

    struct Layout {
        let w: Double, h: Double, top: Double, bottom: Double
        var viewfinder: CGRect { CGRect(x: 16, y: top + 44, width: w - 32, height: h * 0.62 - top - 44) }
        var modulesY: Double { viewfinder.maxY + 70 }
        static let moduleHeight = 86.0
        /// The left capsule grows from a 48 pt wheel to a 120 pt pad; each module (capsule, 10 pt gap,
        /// 26 pt switch) stays centred in its half of the screen.
        func leftCapsule(_ morph: Double) -> CGRect {
            let cw = 48 + 72 * DotMatrixCameraPadModel.clamp01(morph)
            return CGRect(x: w / 4 - (cw + 36) / 2, y: modulesY, width: cw, height: Self.moduleHeight)
        }
        func leftToggle(_ morph: Double) -> CGRect {
            CGRect(x: leftCapsule(morph).maxX + 10, y: modulesY + 14, width: 26, height: 58)
        }
        var rightCapsule: CGRect { CGRect(x: w * 0.75 - 42, y: modulesY, width: 48, height: Self.moduleHeight) }
        var rightToggle: CGRect { CGRect(x: rightCapsule.maxX + 10, y: modulesY + 14, width: 26, height: 58) }
        var readoutY: Double { viewfinder.maxY + 22 }
        var shutterCenter: CGPoint { CGPoint(x: w / 2, y: h - max(bottom, 16) - 24 - 32) }
        var gallery: CGRect { CGRect(x: 24, y: shutterCenter.y - 24, width: 48, height: 48) }
        var chevron: CGRect { CGRect(x: w - 24 - 44, y: shutterCenter.y - 22, width: 44, height: 44) }
        var chipsY: Double { viewfinder.maxY - 38 }
        func chipX(_ i: Int) -> Double { w / 2 + (Double(i) - 1.5) * 58 }
    }

    // MARK: The night street

    /// A small integer hash in 0…1, the same in every language.
    static func hash(_ a: Int, _ b: Int, _ c: Int) -> Double {
        var x = UInt32(truncatingIfNeeded: a &* 73_856_093) ^ UInt32(truncatingIfNeeded: b &* 19_349_663) ^ UInt32(truncatingIfNeeded: c &* 83_492_791)
        x ^= x >> 13
        x = x &* 1_274_126_177
        x ^= x >> 16
        return Double(x & 0xFFFF) / 65535
    }

    /// Building silhouettes: left edge, width, height above the horizon (fractions of the frame).
    static let buildings: [(Double, Double, Double)] = [(-0.02, 0.13, 0.30), (0.10, 0.10, 0.44), (0.19, 0.14, 0.26), (0.32, 0.09, 0.52),
                                                         (0.40, 0.16, 0.34), (0.55, 0.11, 0.47), (0.65, 0.15, 0.30), (0.79, 0.10, 0.41),
                                                         (0.88, 0.15, 0.28)]
    static let horizon = 0.66
    static let lamps: [(Double, Double)] = [(0.17, 0.56), (0.52, 0.53), (0.86, 0.57)]
    /// Cars: lane (0 = far, heading right with headlights; 1 = near, heading left), crossings per loop, phase.
    static let cars: [(Int, Double, Double)] = [(0, 2, 0.0), (0, 3, 0.5), (1, 2, 0.25), (1, 3, 0.8)]
    static let laneY = [0.765, 0.855]
}
