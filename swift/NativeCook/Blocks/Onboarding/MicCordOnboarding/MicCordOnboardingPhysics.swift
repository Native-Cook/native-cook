import Foundation

// A value-for-value translation of mco-physics.ts. All integration uses Double at 120 Hz.
struct MicCordOnboardingRope {
    static let count = 18
    static let notesCount = 14
    static let step = 1.0 / 120.0
    var x, y, px, py: [Double]
    var held = false
    var hx = 0.0, hy = 0.0, speed = 0.0
    var notes = (0..<84).map { $0 % 6 == 4 ? 99.0 : 0.0 }
    var spawn = 0.0, burst = 0, was = false, seed = 0

    init(_ ax: Double, _ ay: Double) {
        x = Array(repeating: ax, count: Self.count)
        y = (0..<Self.count).map { ay + Double($0) * 10 }
        px = x; py = y
    }
    static func smooth(_ value: Double) -> Double {
        let k = min(1, max(0, value)); return k * k * (3 - 2 * k)
    }
    static func hash(_ i: Int) -> Double {
        let s = sin(Double(i) * 12.9898 + 4.1414) * 43758.5453
        return s - floor(s)
    }
    mutating func integrate(_ ax: Double, _ ay: Double, _ h: Double, _ damping: Double) {
        let ex = x[17], ey = y[17]
        for i in 1..<18 {
            let vx = (x[i] - px[i]) * damping, vy = (y[i] - py[i]) * damping
            px[i] = x[i]; py[i] = y[i]
            x[i] += vx
            y[i] += vy + (i == 17 ? 2400 : 1500) * h * h
        }
        x[0] = ax; y[0] = ay; px[0] = ax; py[0] = ay
        for _ in 0..<14 {
            for i in 0..<17 {
                let dx = x[i+1] - x[i], dy = y[i+1] - y[i]
                let d = max(0.0001, hypot(dx, dy)), diff = (d - 10) / d
                let k = d > 10 ? 0.5 : 0.25
                let mx = dx * diff * k, my = dy * diff * k
                if i > 0 { x[i] += mx; y[i] += my }
                x[i+1] -= mx; y[i+1] -= my
            }
            if held { x[17] = hx; y[17] = hy }
        }
        speed = hypot(x[17] - ex, y[17] - ey) / h
    }
    mutating func stepNotes(_ h: Double, _ quiet: Bool) {
        spawn += h
        if was && !held { burst = 4 }
        was = held
        if !quiet && !held && (speed > 300 || burst > 0) && spawn > 0.08 {
            if burst > 0 { burst -= 1 }; spawn = 0
            var best = 0
            for i in 1..<14 where notes[i*6+4] > notes[best*6+4] { best = i }
            seed += 1
            let o = best * 6, dx = x[17] - x[15], dy = y[17] - y[15]
            let dl = max(0.001, hypot(dx, dy))
            notes[o] = x[17] + dx / dl * 200 + (Self.hash(seed+11) - 0.5) * 40
            notes[o+1] = y[17] + dy / dl * 200
            notes[o+2] = (Self.hash(seed) - 0.5) * 160 + (x[17] - px[17]) / h * 0.15
            notes[o+3] = -120 - Self.hash(seed+7) * 120
            notes[o+4] = 0; notes[o+5] = floor(Self.hash(seed+3) * 3)
        }
        for i in 0..<14 {
            let o = i*6
            if notes[o+4] > 1.6 { continue }
            notes[o+4] += h; notes[o] += notes[o+2] * h; notes[o+1] += notes[o+3] * h
            notes[o+2] *= 1 - h * 1.5; notes[o+3] *= 1 - h * 0.6
        }
    }
    static func rest(_ ax: Double, _ ay: Double) -> Self {
        var r = Self(ax, ay)
        for _ in 0..<600 { r.integrate(ax, ay, step, 0.96) }
        r.px = r.x; r.py = r.y
        return r
    }
    mutating func script(_ t: Double, _ rest: Self, _ ax: Double, _ ay: Double, _ reduced: Bool) {
        if t >= 1 && t < 1.8 {
            let k = Self.smooth((t - 1) / (1.8 - 1 - 0.15))
            held = true; hx = rest.x[17] + 40*k; hy = rest.y[17] - 18*k
        } else { held = false }
        integrate(ax, ay, Self.step, 0.992)
        stepNotes(Self.step, reduced)
        if t > 7.4 {
            let k = Self.smooth((t - 7.4) / 0.6) * 0.08
            for i in 0..<18 {
                x[i] += (rest.x[i] - x[i]) * k; y[i] += (rest.y[i] - y[i]) * k
                px[i] += (rest.px[i] - px[i]) * k; py[i] += (rest.py[i] - py[i]) * k
            }
        }
    }
}
