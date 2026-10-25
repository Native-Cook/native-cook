import Foundation

/*
 Thermostat Frost Dial — geometry, the autoPlay script and the touch simulation, a port of the
 React Native thermostatMotion.ts.

 loop = 9 s (set point T in °C):
   0.0 – 1.3   rest at 21.0°.
   1.3 – 3.4   turned colder to 16.0° (ease-in-out); frost follows 0.25 s behind, growing from
               the rim inward; the wall cools to blue.
   3.4 – 4.4   hold at 16°, frost at its fullest.
   4.4 – 6.8   turned warmer to 25.0°: the frost melts into beads (4.8 – 5.6), the wall glows
               amber and the air above the dial shimmers; the beads dry off 6.4 – 7.8.
   6.8 – 8.0   back to 21.0°.
   8.0 – 9.0   rest.
 The ring turns 13.5° per degree (270° for 10 – 30°). The big number shows T rounded to 0.5°,
 its digits rolling between steps.
*/
enum ThermostatFrostDialMotion {
    static let loop = 9.0
    static let rest = 21.0
    static let minT = 10.0
    static let maxT = 30.0
    /// The room's own temperature: ticks between it and the set point are lit.
    static let room = 20.5
    static let idleBeforeBack = 2.0
    static let backFor = 0.9

    static let start = 135 * Double.pi / 180
    static let arc = 270 * Double.pi / 180

    static let headerH = 112.0
    static let bottomH = 214.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// Set point angle on screen (rad, y down; 10° at 135°, 30° at 405°).
    static func angleOf(_ T: Double) -> Double { start + (T - minT) / (maxT - minT) * arc }

    /// The scripted set point at loop time t.
    static func scriptT(_ t: Double) -> Double {
        rest - 5 * easeInOut(seg(t, 1.3, 3.4)) + 9 * easeInOut(seg(t, 4.4, 6.8)) - 4 * easeInOut(seg(t, 6.8, 8.0))
    }

    static func coldOf(_ T: Double) -> Double { clamp01((rest - T) / 5) }
    static func warmOf(_ T: Double) -> Double { clamp01((T - rest) / 4) }

    /// cubic-bezier(0.77, 0, 0.175, 1), the mode label's roll.
    static func labelEase(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        let (x1, y1, x2, y2) = (0.77, 0.0, 0.175, 1.0)
        func bez(_ u: Double, _ a: Double, _ b: Double) -> Double {
            3 * (1 - u) * (1 - u) * u * a + 3 * (1 - u) * u * u * b + u * u * u
        }
        // Bisection on x(u), then y(u).
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 {
            let mid = (lo + hi) / 2
            if bez(mid, x1, x2) < x { lo = mid } else { hi = mid }
        }
        return bez((lo + hi) / 2, y1, y2)
    }

    /// Past the ends the ring resists: the overshoot is squashed toward ~4°.
    static func rubber(_ raw: Double) -> Double {
        if raw > maxT {
            let o = raw - maxT
            return maxT + o / (1 + o / 4)
        }
        if raw < minT {
            let o = minT - raw
            return minT - o / (1 + o / 4)
        }
        return raw
    }

    /// The rolling digits of T rounded to 0.5°: wheel positions for the tens, the ones and the half
    /// (0 = ".0", 1 = ".5"). Between steps a column rolls over the middle 60 % of the step, so it
    /// rests exactly on a whole digit whenever T holds.
    static func digitWheels(_ T: Double) -> (Double, Double, Double) {
        let k = min(maxT, max(minT, T)) * 2
        let base = floor(k)
        let fr = k - base
        let s = base + min(1, max(0, (fr - 0.2) / 0.6))
        let sm = base + (s - base) * (s - base) * (3 - 2 * (s - base))
        let half = sm - 2 * floor(sm / 2)
        let u = sm / 2
        let ones = floor(u) + min(1, max(0, (u - floor(u) - 0.5) * 2))
        let tens = floor(ones / 10) + min(1, max(0, ones - floor(ones / 10) * 10 - 9))
        return (tens, ones - floor(ones / 10) * 10, half)
    }
}

struct ThermostatFrostDialGeo: Equatable {
    var w = 1.0
    var h = 1.0
    var cx = 0.5
    var cy = 0.5
    var ro = 1.0
    var ri = 0.77

    init() {}

    init(w: Double, h: Double, insetTop: Double, insetBottom: Double) {
        typealias M = ThermostatFrostDialMotion
        let top = insetTop + M.headerH + 20
        let bottom = h - max(insetBottom, 12) - M.bottomH - 16
        let ro = max(1, min(w * 0.44, 176, (bottom - top) / 2))
        self.w = w
        self.h = h
        cx = w / 2
        cy = (top + bottom) / 2
        self.ro = ro
        ri = ro * 0.77
    }
}

struct ThermostatFrostDialFrame {
    var T = ThermostatFrostDialMotion.rest
    var frost = 0.0
    var wet = 0.0
    var phase = 0.0

    mutating func script(_ t: Double) {
        typealias M = ThermostatFrostDialMotion
        T = M.scriptT(t)
        // Frost grows a beat behind the dial and melts as it warms.
        frost = M.coldOf(M.scriptT(max(0, t - 0.25)))
        wet = M.easeInOut(M.seg(t, 4.8, 5.6)) * (1 - M.easeInOut(M.seg(t, 6.4, 7.8)))
        phase = t / M.loop
    }
}

/// The touch simulation and the autoPlay clock, stepped once a frame like the RN frame callback.
@MainActor
final class ThermostatFrostDialSim {
    private typealias M = ThermostatFrostDialMotion
    enum Mode { case auto, live, back }

    private(set) var mode = Mode.auto
    private var t = 0.0
    private var now = 0.0
    private var last = 0.0
    private var back = 0.0
    private(set) var frame = ThermostatFrostDialFrame()
    /// Set point velocity (°/s) for the release spring, and whether a finger holds the ring.
    private var v = 0.0
    private var held = false
    /// The finger's last angle around the centre.
    private var grab = 0.0
    /// The raw value the finger asks for (before rubber-banding).
    private var raw = ThermostatFrostDialMotion.rest
    private var turnAt = 0.0
    private var from: [Double] = [ThermostatFrostDialMotion.rest, 0, 0, 0]
    private var lastDate: Date?
    private var wasAuto: Bool?
    /// The half degree the set point last showed while a finger had the dial (for the detent haptic).
    private var detent: Int?
    /// "Heating to" (1) or "Cooling to" (0): eased over 0.32 s when the set point crosses the room.
    private var heatFrom = 1.0
    private var heatTo = 1.0
    private var heatAt = -1.0

    func step(to date: Date, auto: Bool, reduced: Bool) -> ThermostatFrostDialFrame {
        if wasAuto != auto {
            wasAuto = auto
            mode = .auto
            t = 0
            now = 0
            last = 0
            frame = ThermostatFrostDialFrame()
            heatFrom = 1
            heatTo = 1
            heatAt = -1
        }
        let rawDt = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        // A stalled frame pauses the clock instead of skipping ahead.
        let dt = rawDt > 0.25 ? 0 : min(max(rawDt, 0), 1.0 / 20)
        advance(dt, auto: auto, reduced: reduced)
        let target = frame.T >= M.room ? 1.0 : 0.0
        if target != heatTo {
            heatFrom = heating
            heatTo = target
            heatAt = now
        }
        return frame
    }

    /// The mode label's blend, 1 = "Heating to".
    var heating: Double {
        if heatAt < 0 { return heatTo }
        let k = M.labelEase(M.clamp01((now - heatAt) / 0.32))
        return heatFrom + (heatTo - heatFrom) * k
    }

    /// True once each time the set point crosses a half degree while a finger turns the dial or
    /// its release spring settles.
    func takeDetent() -> Bool {
        guard mode == .live else {
            detent = nil
            return false
        }
        let d = Int((frame.T * 2).rounded())
        defer { detent = d }
        return detent != nil && detent != d
    }

    func grab(_ angle: Double) {
        mode = .live
        held = true
        grab = angle
        raw = frame.T
        v = 0
        turnAt = now
        last = now
    }

    /// The finger turns the ring: the change of its angle around the centre turns the set point.
    func turn(_ angle: Double) {
        let dt = now - turnAt
        turnAt = now
        var d = angle - grab
        if d > .pi { d -= 2 * .pi }
        if d < -.pi { d += 2 * .pi }
        grab = angle
        raw += d / M.arc * (M.maxT - M.minT)
        let before = frame.T
        frame.T = M.rubber(raw)
        if dt > 0 { v = v * 0.6 + (frame.T - before) / dt * 0.4 }
        last = now
    }

    func release() {
        held = false
        last = now
    }

    private func advance(_ dt: Double, auto: Bool, reduced: Bool) {
        now += dt
        if mode == .auto {
            if auto { t = (t + dt).truncatingRemainder(dividingBy: M.loop) }
            frame.script(reduced ? 0 : t)
            return
        }
        frame.phase += dt / M.loop
        if mode == .live {
            if !held {
                // Release: a spring carries the finger's speed and settles on the nearest half degree.
                let target = min(M.maxT, max(M.minT, ((frame.T + v * 0.12) * 2).rounded() / 2))
                let w = 14.0
                let z = 0.8
                let a = w * w * (target - frame.T) - 2 * z * w * v
                v += a * dt
                frame.T += v * dt
                if abs(target - frame.T) < 0.002 && abs(v) < 0.02 {
                    frame.T = target
                    v = 0
                }
            }
            // Frost creeps in over ~0.6 s and melts faster; what melts becomes beads that dry off.
            let cold = M.coldOf(frame.T)
            let before = frame.frost
            frame.frost += (cold - frame.frost) * min(1, dt * (cold > frame.frost ? 2.2 : 3.5))
            let melted = max(0, before - frame.frost)
            frame.wet = min(1, frame.wet + melted * 1.6)
            frame.wet = max(0, frame.wet - dt * (0.12 + 0.45 * M.warmOf(frame.T)))
            if auto && !held && now - last > M.idleBeforeBack {
                mode = .back
                back = 0
                from = [frame.T, frame.frost, frame.wet, frame.phase]
            }
            return
        }
        back = min(1, back + dt / M.backFor)
        let k = M.easeInOut(back)
        frame.T = from[0] + (M.rest - from[0]) * k
        frame.frost = from[1] * (1 - k)
        frame.wet = from[2] * (1 - k)
        frame.phase = from[3] + (from[3].rounded(.up) - from[3]) * k
        if back >= 1 {
            mode = .auto
            t = 0
            frame.script(0)
        }
    }
}
