import SwiftUI

/*
 Light Table Loupe — the data and the timeline.

 Twelve 35 mm slides, each a transparency drawn as gradient art: a sky gradient, an optional sun (a disc
 with a glow three times its radius) and up to two ridges, each a band filled from
 y = base + amp · sin(2π f u + phase) (u = 0 … 1 across the window, y as a fraction of its height) down
 to the bottom. All numbers are fractions of the slide's window.

 autoPlay timeline (loop = 9 s); the loupe parks at the lower right of the table:
   0.0–0.6   rest: the loupe parked, "Drag the loupe over a slide".
   0.6–1.6   the loupe lifts and glides (ease in-out, on an arc bowed 18 % of the way sideways) to slide 4;
             the hint fades 0.6–0.75.
   1.6       it lands: the view under the glass pulls focus (blur 6 → 0 pt in 0.25 s) and the caption
             "Frame 04 · …" fades in.
   2.4–3.2   on to slide 6 (focus at 3.2). 4.0–4.8 on to slide 8 (focus at 4.8).
   5.0       a tap: the loupe dips 4 % (0.25 s) and a red grease-pencil circle is drawn round slide 8 in
             0.4 s (two rough turns, 3 pt), the slide shifting 3 pt and tilting 1.5° (ease-out-back 0.3 s);
             "1 marked" fades in at the top.
   6.0–7.0   the loupe glides back to its place; the caption fades and the hint returns 7.0–7.25.
   7.6–8.2   the mark is wiped off (fades out) and the slide settles back.
   8.2–9.0   rest, the same frame as t = 0.
 All the while the table light breathes ±1.5 % (two cycles a loop) and a soft sheen crosses the glass once
 a loop (left to right, 1.6–4.6 s). The viewer projects the slide under the loupe with the caption's
 fade, its lamp flickering ±1 % eighteen times a loop.
 */

struct LightTableLoupeScene {
    var title: String
    var top: UInt32, bottom: UInt32
    /// Sun: x, y, radius (0 = none), colour, glow opacity.
    var sun: (x: Double, y: Double, r: Double, color: UInt32, glow: Double)
    var ridges: [(base: Double, amp: Double, freq: Double, phase: Double, color: UInt32)]
}

enum LightTableLoupeModel {
    static let loop = 9.0

    static let slides: [LightTableLoupeScene] = [
        .init(title: "Harbor at dusk", top: 0x1B2A6B, bottom: 0xF7814A, sun: (0.70, 0.60, 0.10, 0xFFD27A, 0.55),
              ridges: [(0.70, 0.03, 1.5, 0.4, 0x2A1E4A), (0.86, 0.01, 4.0, 1.0, 0x182040)]),
        .init(title: "Wheat in June", top: 0x3F9BEA, bottom: 0xCFE9FF, sun: (0.20, 0.25, 0.08, 0xFFF6C8, 0.6),
              ridges: [(0.62, 0.05, 0.8, 0.2, 0xE9B949), (0.80, 0.03, 1.6, 2.0, 0xC98A2B)]),
        .init(title: "Open sea", top: 0x0E4C92, bottom: 0x7FD1F0, sun: (0.50, 0.30, 0.06, 0xFFFFFF, 0.5),
              ridges: [(0.55, 0.006, 6.0, 0.0, 0x0A6FA8), (0.78, 0.02, 3.0, 1.2, 0x04507F)]),
        .init(title: "Night street", top: 0x0B0820, bottom: 0x40206A, sun: (0.78, 0.22, 0.06, 0xF5E9C8, 0.45),
              ridges: [(0.60, 0.08, 3.2, 0.6, 0x120C2E), (0.84, 0.03, 5.0, 2.2, 0xE0457B)]),
        .init(title: "Desert road", top: 0xF2A65A, bottom: 0xFFE1A8, sun: (0.50, 0.50, 0.14, 0xFFF1D0, 0.6),
              ridges: [(0.64, 0.04, 1.1, 1.4, 0xC4622D), (0.84, 0.02, 2.0, 0.3, 0x8A3A1C)]),
        .init(title: "Glacier lake", top: 0x5FB2E6, bottom: 0xE6F6FF, sun: (0.25, 0.20, 0.05, 0xFFFFFF, 0.5),
              ridges: [(0.48, 0.12, 1.8, 0.9, 0x6F8FB0), (0.72, 0.0, 1.0, 0.0, 0x2BB3B8)]),
        .init(title: "Northern lights", top: 0x031426, bottom: 0x0E3B4A, sun: (0.40, 0.32, 0.0, 0x38F2A0, 0.0),
              ridges: [(0.78, 0.05, 2.4, 0.7, 0x020A12)]),
        .init(title: "Pink beach", top: 0xFF8FA8, bottom: 0xFFE0C2, sun: (0.70, 0.38, 0.09, 0xFFFFFF, 0.55),
              ridges: [(0.64, 0.01, 3.0, 0.0, 0x4FC6D8), (0.80, 0.02, 1.2, 0.8, 0xFFD6B0)]),
        .init(title: "Alpine snow", top: 0x2F6FD6, bottom: 0xA9D2FF, sun: (0.80, 0.20, 0.05, 0xFFFFFF, 0.5),
              ridges: [(0.55, 0.15, 2.2, 0.3, 0xF4F8FF), (0.78, 0.06, 3.0, 1.9, 0x1F5A3A)]),
        .init(title: "Tulip rows", top: 0x6EC3FF, bottom: 0xE8F7FF, sun: (0.15, 0.20, 0.06, 0xFFFBE0, 0.55),
              ridges: [(0.60, 0.02, 1.0, 0.5, 0xFF3D6E), (0.78, 0.02, 1.3, 2.4, 0xFFB703)]),
        .init(title: "City at dusk", top: 0x2E1A55, bottom: 0xFF6F61, sun: (0.30, 0.66, 0.11, 0xFFC27A, 0.6),
              ridges: [(0.70, 0.10, 4.5, 0.2, 0x1D1236), (0.90, 0.01, 1.0, 0.0, 0x120A24)]),
        .init(title: "Fern forest", top: 0x0F3D2E, bottom: 0x7BC47F, sun: (0.60, 0.15, 0.08, 0xE6FFB0, 0.7),
              ridges: [(0.50, 0.10, 3.0, 1.1, 0x0B2E21), (0.78, 0.06, 5.0, 0.4, 0x145C3A)]),
    ]

    /// The aurora of "Northern lights": two soft green and violet bands across its sky.
    static let auroraIndex = 6

    static func color(_ hex: UInt32, _ opacity: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: opacity)
    }

    // MARK: Timeline

    struct Glide { var from: Int, to: Int, start: Double, end: Double, bow: Double }
    /// Stops: −1 is the parking place, otherwise a slide index.
    static let glides = [Glide(from: -1, to: 3, start: 0.6, end: 1.6, bow: 0.18),
                         Glide(from: 3, to: 5, start: 2.4, end: 3.2, bow: -0.18),
                         Glide(from: 5, to: 7, start: 4.0, end: 4.8, bow: 0.18),
                         Glide(from: 7, to: -1, start: 6.0, end: 7.0, bow: -0.18)]
    static let tapAt = 5.0
    static let marked = 7

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    static func inOut(_ x: Double) -> Double {
        let t = clamp(x)
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }
    static func outBack(_ x: Double) -> Double {
        let t = clamp(x), c1 = 1.70158, c3 = c1 + 1
        return 1 + c3 * pow(t - 1, 3) + c1 * pow(t - 1, 2)
    }

    /// Where along an arc from a to b, bowed sideways by `bow` of its length.
    static func arc(_ a: CGPoint, _ b: CGPoint, _ u: Double, bow: Double) -> CGPoint {
        let e = inOut(u)
        let mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2
        let dx = b.x - a.x, dy = b.y - a.y
        let c = CGPoint(x: mx - dy * bow, y: my + dx * bow)
        let x = (1 - e) * (1 - e) * a.x + 2 * (1 - e) * e * c.x + e * e * b.x
        let y = (1 - e) * (1 - e) * a.y + 2 * (1 - e) * e * c.y + e * e * b.y
        return CGPoint(x: x, y: y)
    }

    /// What plays at loop time t, given where the parking place and the slides' centres are.
    static func frame(_ t: Double, park: CGPoint, center: (Int) -> CGPoint) -> LightTableLoupeFrame {
        var f = LightTableLoupeFrame()
        func at(_ stop: Int) -> CGPoint { stop < 0 ? park : center(stop) }
        f.loupe = park
        for (i, g) in glides.enumerated() {
            if t < g.start {
                f.loupe = at(g.from)
                break
            }
            if t < g.end {
                let u = (t - g.start) / (g.end - g.start)
                f.loupe = arc(at(g.from), at(g.to), u, bow: g.bow)
                f.lift = sin(.pi * u)
                break
            }
            f.loupe = at(g.to)
            if g.to >= 0 {
                f.focus = g.to
                f.focusAt = g.end
                let leave = i + 1 < glides.count ? glides[i + 1].start : loop
                f.caption = smoothstep(g.end, g.end + 0.25, t) * (1 - smoothstep(leave, leave + 0.15, t))
            } else {
                f.focus = -1
            }
        }
        if f.focus < 0 { f.focusAt = -10 }
        if f.focus >= 0 && t >= f.focusAt { f.blur = 6 * (1 - smoothstep(f.focusAt, f.focusAt + 0.25, t)) }
        f.hint = 1 - smoothstep(0.6, 0.75, t) + smoothstep(7.0, 7.25, t)
        if t >= tapAt && t < tapAt + 0.25 { f.press = sin(.pi * (t - tapAt) / 0.25) }
        if t >= tapAt {
            f.markDraw = clamp((t - tapAt) / 0.4)
            f.markTilt = outBack((t - tapAt) / 0.3) * (1 - inOut((t - 7.6) / 0.6))
            f.markAlpha = 1 - smoothstep(7.6, 8.2, t)
        }
        f.counter = smoothstep(tapAt, tapAt + 0.3, t) * (1 - smoothstep(7.6, 8.2, t))
        f.pulse = sin(2 * .pi * 2 * t / loop)
        f.sheen = t >= 1.6 && t < 4.6 ? (t - 1.6) / 3.0 : -1
        f.flicker = sin(2 * .pi * 18 * t / loop)
        return f
    }
}

/// What the screen shows at one moment.
struct LightTableLoupeFrame {
    var loupe = CGPoint.zero
    /// 0 resting on the table, 1 lifted.
    var lift = 0.0
    var press = 0.0
    /// The slide whose focus was last pulled, and when (loop time or seconds since).
    var focus = -1
    var focusAt = -10.0
    /// Blur under the glass, in points.
    var blur = 0.0
    var caption = 0.0
    var hint = 1.0
    /// The grease mark on `marked`: how much is drawn, how visible, how far the slide has shifted.
    var markDraw = 0.0
    var markAlpha = 0.0
    var markTilt = 0.0
    /// Every mark on the table, by slide: drawn (0 … 1), opacity, shift and tilt (0 … 1).
    var marks: [Int: (draw: Double, alpha: Double, tilt: Double)] = [:]
    var markCount = 0
    var counter = 0.0
    var pulse = 0.0
    var sheen = -1.0
    /// The viewer's lamp flicker, −1 … 1 (18 cycles a loop).
    var flicker = 0.0
}
