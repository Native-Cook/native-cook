import Foundation
import SwiftUI

/*
 Ink Drop Bloom — geometry, the autoPlay script and the touch simulation, a port of the React
 Native inkDropMotion.ts.

 Units: points and seconds. loop = 9 s:
   0.0 – 1.4   rest: clear water, the swatches are empty outlines.
   1.15 – 1.4  drop 1 (indigo) falls into the glass; it blooms 1.4 – 4.
   2.55 – 2.8  drop 2 (orchid) falls; 3.95 – 4.2 drop 3 (lagoon).
   5.6 – 6.6   peak: the plumes fill the glass, every swatch is full.
   6.35 – 6.65 the press's mesh disc comes down to the water; 6.65 – 8.05 it pushes the ink to
               the floor and the water above it is clear (ease-in-out); 8.05 – 8.55 it lifts out.
   7.0 – 8.2   the swatches drain back to outlines.
   8.55 – 9.0  rest (the frame at 9 is the frame at 0).
*/
enum InkDropBloomMotion {
    static let loop = 9.0
    /// The poster under reduced motion: the palette at its fullest.
    static let reducedAt = 6.1
    /// After the last touch the screen waits this long, then clears the water and restarts.
    static let idleBeforeBack = 2.0
    /// How long clearing back to rest takes after a touch (s).
    static let backFor = 0.9

    /// The three inks (sRGB 0–1, at full strength).
    static let inks: [SIMD3<Float>] = [
        SIMD3(0.17, 0.2, 0.62),
        SIMD3(0.74, 0.12, 0.42),
        SIMD3(0.04, 0.5, 0.52),
    ]

    static let phases = [
        "Clear water · ready to mix",
        "Dropping indigo…",
        "Dropping orchid…",
        "Dropping lagoon…",
        "Palette ready · 5 colours",
        "Filtering the glass…",
        "Clear water · ready to mix",
    ]

    static let dropAt = [1.4, 2.8, 4.2]
    /// Where each scripted drop enters, as a share of the water's half width.
    static let dropX = [-0.32, 0.3, 0.02]
    static let fallFor = 0.25
    static let fallFrom = 92.0
    static let pressIn = (6.35, 6.65)
    static let pressPush = (6.65, 8.05)
    static let pressOut = (8.05, 8.55)
    static let labelAt = [1.25, 2.65, 4.05, 5.5, 6.45, 8.3]
    static let labelFor = 0.42

    // Layout: header, glass, palette card and button, top to bottom.
    static let headerH = 116.0
    static let paletteH = 140.0
    static let buttonH = 52.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
}

struct InkDropBloomGeo: Equatable {
    var w = 1.0
    var h = 1.0
    var cx = 0.5
    var top = 0.0
    var bottom = 1.0
    var hwTop = 0.0
    var hwBot = 0.0
    var wall = 6.0
    var surface = 0.0
    var floor = 1.0
    var paletteTop = 0.0
    var buttonTop = 0.0

    init() {}

    init(w: Double, h: Double, insetTop: Double, insetBottom: Double) {
        typealias M = InkDropBloomMotion
        let buttonTop = h - max(insetBottom, 12) - 12 - M.buttonH
        let paletteTop = buttonTop - 16 - M.paletteH
        let top = insetTop + M.headerH + 24
        let bottom = paletteTop - 30
        let tall = bottom - top
        let hwTop = min(w * 0.29, 116, tall * 0.31)
        self.w = w
        self.h = h
        cx = w / 2
        self.top = top
        self.bottom = bottom
        self.hwTop = hwTop
        hwBot = hwTop * 0.88
        wall = 6
        surface = top + max(30, tall * 0.11)
        floor = bottom - 22
        self.paletteTop = paletteTop
        self.buttonTop = buttonTop
    }

    /// Whether a point is inside the water (where a tap drops ink).
    func inWater(_ x: Double, _ y: Double) -> Bool {
        y > surface + 4 && y < floor - 4 && abs(x - cx) < hwBot - wall - 4
    }
}

/// Everything one frame needs, in the order the shader and the views read it.
struct InkDropBloomFrame {
    /// 4 drops × (x, y, age, seed); age < 0 = none.
    var drops: [Double] = [0, 0, -1, 0, 0, 0, -1, 0, 0, 0, -1, 0, 0, 0, -1, 0]
    /// Ink index of each drop slot.
    var inks: [Int] = [0, 1, 2, 0]
    var falling: [Double] = [0, 0, 0, 0]
    var fallInk = 0
    /// Disc y (< 0 none), rod alpha, squeeze, ink amount.
    var press: [Double] = [-1, 0, 0, 1]
    var stir: [Double] = [0, 0, 0, 60]
    var swatches: [Double] = [0, 0, 0, 0, 0]
    var label = 0.0
    var bar = 0.0

    /// The autoPlay frame at loop time t: a pure function, so frame `loop` = frame 0.
    mutating func script(_ t: Double, _ g: InkDropBloomGeo) {
        typealias M = InkDropBloomMotion
        let ihw = g.hwTop - g.wall
        let done = t >= M.pressPush.1
        for i in 0..<3 {
            let age = t - M.dropAt[i]
            drops[i * 4] = g.cx + M.dropX[i] * ihw
            drops[i * 4 + 1] = g.surface
            drops[i * 4 + 2] = age > 0 && !done ? age : -1
            drops[i * 4 + 3] = Double(i) * 0.37 + 0.11
            inks[i] = i
        }
        drops[14] = -1
        falling[3] = 0
        for i in 0..<3 {
            let s = M.seg(t, M.dropAt[i] - M.fallFor, M.dropAt[i])
            if s > 0 && s < 1 {
                let y0 = g.top - M.fallFrom
                falling[0] = g.cx + M.dropX[i] * ihw
                // Falling is gravity: the drop speeds up (y ∝ s²).
                falling[1] = y0 + (g.surface - y0) * s * s
                falling[2] = 5.2
                falling[3] = M.clamp01(s / 0.25)
                fallInk = i
            }
        }
        var disc = -1.0
        var rod = 0.0
        let above = g.top - 26
        if t >= M.pressIn.0 && t < M.pressIn.1 {
            disc = above + (g.surface - above) * M.easeOut(M.seg(t, M.pressIn.0, M.pressIn.1))
            rod = M.easeOut(M.seg(t, M.pressIn.0, M.pressIn.0 + 0.18))
        } else if t >= M.pressPush.0 && t < M.pressPush.1 {
            disc = g.surface + (g.floor - 4 - g.surface) * M.easeInOut(M.seg(t, M.pressPush.0, M.pressPush.1))
            rod = 1
        } else if t >= M.pressOut.0 && t < M.pressOut.1 {
            disc = g.floor - 4 + (above - (g.floor - 4)) * M.easeInOut(M.seg(t, M.pressOut.0, M.pressOut.1))
            rod = 1 - M.seg(t, M.pressOut.1 - 0.2, M.pressOut.1)
        }
        press[0] = disc
        press[1] = rod
        press[2] = M.seg(t, M.pressPush.0, M.pressPush.1)
        press[3] = 1
        stir[2] = 0
        let drain = 1 - M.easeInOut(M.seg(t, 7.0, 8.2))
        for i in 0..<3 {
            swatches[i] = M.easeOut(M.seg(t, M.dropAt[i] + 0.25, M.dropAt[i] + 1.35)) * drain
        }
        swatches[3] = M.easeOut(M.seg(t, 4.9, 5.8)) * drain
        swatches[4] = M.easeOut(M.seg(t, 5.15, 6.05)) * drain
        var l = 0.0
        for a in M.labelAt { l += M.easeInOut(M.seg(t, a, a + M.labelFor)) }
        label = l
        bar = M.easeInOut(M.seg(t, 1.3, 6.0)) * (1 - M.easeInOut(M.seg(t, 6.65, 8.05)))
    }

    /// The light the water throws on the table takes the mean colour of the ink in it.
    var tint: SIMD3<Float> {
        var r = 0.0, gg = 0.0, b = 0.0, w = 0.0
        for i in 0..<4 {
            let a = drops[i * 4 + 2]
            if a <= 0 { continue }
            let k = min(1, a / 1.5) * press[3] * (1 - press[2])
            let c = InkDropBloomMotion.inks[inks[i]]
            r += Double(c.x) * k
            gg += Double(c.y) * k
            b += Double(c.z) * k
            w += k
        }
        if w <= 0.0001 { return SIMD3(1, 1, 1) }
        let m = min(1, w)
        return SIMD3(Float(1 - m + r / w * m), Float(1 - m + gg / w * m), Float(1 - m + b / w * m))
    }
}

/// The touch simulation and the autoPlay clock, stepped once a frame like the RN frame callback.
@MainActor
final class InkDropBloomSim {
    private typealias M = InkDropBloomMotion
    enum Mode { case auto, live, back }

    var mode = Mode.auto
    var t = 0.0
    var now = 0.0
    var last = 0.0
    var back = 0.0
    var flow = 0.0
    var frame = InkDropBloomFrame()
    private var from = InkDropBloomFrame()
    private var slot = 3
    private var nextInk = 0
    private var touching = false
    private var lastX = 0.0
    private var lastDate: Date?
    private var wasAuto: Bool?

    /// Steps to `date` and returns the frame to draw.
    func step(to date: Date, geo g: InkDropBloomGeo, auto: Bool, reduced: Bool) -> InkDropBloomFrame {
        if wasAuto != auto {
            wasAuto = auto
            reset()
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        // A stalled frame pauses the clock instead of skipping ahead.
        let dt = raw > 0.25 ? 0 : min(max(raw, 0), 1.0 / 20)
        advance(dt, g, auto: auto, reduced: reduced)
        return frame
    }

    private func reset() {
        mode = .auto
        t = 0
        now = 0
        last = 0
        back = 0
        flow = 0
        frame = InkDropBloomFrame()
        slot = 3
        nextInk = 0
        touching = false
    }

    private func advance(_ dt: Double, _ g: InkDropBloomGeo, auto: Bool, reduced: Bool) {
        now += dt
        switch mode {
        case .auto:
            if auto { t = (t + dt).truncatingRemainder(dividingBy: M.loop) }
            flow = reduced ? M.reducedAt : t
            frame.script(reduced ? M.reducedAt : t, g)
        case .live:
            flow += dt
            for i in 0..<4 {
                let a = frame.drops[i * 4 + 2]
                if a >= 0 { frame.drops[i * 4 + 2] = min(a + dt, 9) }
            }
            for i in 0..<4 where frame.drops[i * 4 + 2] > 0.3 {
                let k = frame.inks[i]
                frame.swatches[k] = min(1, frame.swatches[k] + dt * 1.2)
            }
            if frame.swatches[0] > 0.6 && frame.swatches[1] > 0.6 {
                frame.swatches[3] = min(1, frame.swatches[3] + dt)
            }
            if frame.swatches[1] > 0.6 && frame.swatches[2] > 0.6 {
                frame.swatches[4] = min(1, frame.swatches[4] + dt)
            }
            let full = frame.swatches.reduce(0, +) / 5
            frame.bar += (full - frame.bar) * min(1, dt * 4)
            if !touching { frame.stir[2] *= exp(-dt / 3) }
            if auto && !touching && now - last > M.idleBeforeBack {
                mode = .back
                back = 0
                from = frame
            }
        case .back:
            // Back to rest: the ink fades out, the twist unwinds, the swatches drain, the label rolls home.
            flow += dt
            back = min(1, back + dt / M.backFor)
            let k = M.easeInOut(back)
            let a = from
            frame.press[3] = a.press[3] * (1 - k)
            frame.stir[2] = a.stir[2] * (1 - k)
            for i in 0..<5 { frame.swatches[i] = a.swatches[i] * (1 - k) }
            frame.bar = a.bar * (1 - k)
            let home = a.label <= 3 ? 0.0 : 6.0
            frame.label = a.label + (home - a.label) * k
            if back >= 1 {
                mode = .auto
                t = 0
                flow = 0
                frame.script(0, g)
            }
        }
    }

    /// A finger lands: the script stops where it is and the water is the finger's.
    func touchDown(_ x: Double, _ y: Double) {
        if mode != .live {
            if frame.press[0] > 0 { frame.press[3] *= (1 - frame.press[2] * frame.press[2]) }
            frame.press[0] = -1
            frame.press[1] = 0
            frame.falling[3] = 0
            frame.label = frame.label.rounded()
            if frame.label >= 5 { frame.label = 0 }
            mode = .live
        }
        touching = true
        last = now
        lastX = x
        frame.stir[0] = x
        frame.stir[1] = y
    }

    /// Drops ink where the finger tapped.
    func drop(_ x: Double, _ y: Double) {
        slot = (slot + 1) % 4
        let ink = nextInk
        nextInk = (nextInk + 1) % 3
        frame.drops[slot * 4] = x
        frame.drops[slot * 4 + 1] = y
        frame.drops[slot * 4 + 2] = 0.001
        frame.drops[slot * 4 + 3] = (now * 0.731).truncatingRemainder(dividingBy: 1)
        frame.inks[slot] = ink
        if frame.press[3] < 0.999 { frame.press[3] = 1 }
        frame.label = Double(1 + ink)
    }

    /// A drag stirs: the twist follows the finger and grows with how far it moves sideways.
    func stir(_ x: Double, _ y: Double) {
        let dx = max(-28, min(28, x - lastX))
        frame.stir[0] += (x - frame.stir[0]) * 0.35
        frame.stir[1] += (y - frame.stir[1]) * 0.35
        frame.stir[2] = max(-5, min(5, frame.stir[2] + dx * 0.0042))
        frame.stir[3] = 74
        lastX = x
        last = now
    }

    func touchUp() {
        touching = false
        last = now
    }
}
