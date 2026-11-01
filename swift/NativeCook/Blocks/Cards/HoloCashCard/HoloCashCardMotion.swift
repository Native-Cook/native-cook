import Foundation

enum HoloCashCardMotion {
    static let loop = 8.0
    static let start = 502020313
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(1, max(0, (t - a) / (b - a)))
    }
    static func smooth(_ t: Double) -> Double {
        let x = min(1, max(0, t)); return x * x * (3 - 2 * x)
    }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func scriptTilt(_ t: Double) -> (Bool, Double, Double) {
        if t < 1 || t >= 2.4 { return (false, 0, 0) }
        let k = seg(t, 1, 2.4)
        let a = smooth(seg(k, 0, 0.35)), b = smooth(seg(k, 0.35, 0.9))
        return (true, mix(mix(0, 9, a), -7, b), mix(mix(0, -15, a), 15, b))
    }
    static func ticksAt(_ t: Double) -> Int {
        Int(floor(min(t, 3.2) / 0.3 + max(0, min(t, 6.4) - 3.2) / 0.11 + max(0, t - 6.4) / 0.3))
    }
    struct Odometer {
        var disp: [Double], tgt: [Double], vel: [Double], last: [Int]
        init(_ value: Int) {
            last = (0..<9).map { value / Int(pow(10, Double(8 - $0))) % 10 }
            disp = last.map(Double.init); tgt = disp; vel = Array(repeating: 0, count: 9)
        }
        mutating func step(_ value: Int, _ dt: Double) {
            let n = max(1, Int(ceil(dt / (1 / 240.0)))), h = dt / Double(n)
            for k in 0..<9 {
                let d = value / Int(pow(10, Double(8 - k))) % 10
                if d != last[k] { tgt[k] += Double((d - last[k] + 10) % 10); last[k] = d }
                for _ in 0..<n {
                    vel[k] += (420 * (tgt[k] - disp[k]) - 2 * sqrt(420) * 0.9 * vel[k]) * h
                    disp[k] += vel[k] * h
                }
                if tgt[k] > 1000 { tgt[k] -= 1000; disp[k] -= 1000 }
            }
        }
    }
    struct State {
        var time = 0.0, clock = 0.0, last = -10.0
        var rx = 0.0, ry = 0.0, vx = 0.0, vy = 0.0, tx = 0.0, ty = 0.0
        var on = false, son = false
        var value = start, extra = 0, acc = 0.0, boost = false, boostK = 0.0, dip = 0.0
        var odo = Odometer(start)
        var apy = 3.0, apyVelocity = 0.0, popAt = -10.0, hide = 0.0, hideTarget = 0.0
        mutating func setBoost(_ enabled: Bool) {
            if boost == enabled { return }; boost = enabled; popAt = clock
        }
        mutating func step(_ dt: Double, auto: Bool, reduced: Bool, scriptTime: Double? = nil) {
            clock += dt
            if auto && !reduced && !(on || clock - last < 2) {
                time = scriptTime ?? (time + dt)
                if time >= loop { time -= loop }
                let st = scriptTilt(time); son = st.0; tx = st.1; ty = st.2
                setBoost(time >= 3.2 && time < 6.4)
                let target = start + extra + ticksAt(time)
                dip = sin(seg(time, 7.25, 7.75) * .pi)
                if time >= 7.5 || time < 7.25 {
                    let v = time >= 7.5 ? start + extra : target
                    if time >= 7.5 && value != v { odo = Odometer(v) }; value = v
                } else { value = target }
            } else {
                dip = 0; acc += dt
                let tick = boost ? 0.11 : 0.3
                while acc >= tick { acc -= tick; value += 1 }
            }
            let held = on || son, k = held ? 320.0 : 170.0, z = held ? 0.9 : 0.32
            let c = 2 * sqrt(k) * z, n = max(1, Int(ceil(dt / (1 / 240.0)))), h = dt / Double(n)
            for _ in 0..<n {
                vx += (k * ((held ? tx : 0) - rx) - c * vx) * h; rx += vx * h
                vy += (k * ((held ? ty : 0) - ry) - c * vy) * h; ry += vy * h
            }
            boostK += ((boost ? 1 : 0) - boostK) * (1 - exp(-dt * 6))
            let ad = boost ? 8.0 : 3.0
            if reduced { apy = ad; odo = Odometer(value) }
            else {
                apyVelocity += (380 * (ad - apy) - 2 * sqrt(380) * 0.8 * apyVelocity) * dt
                apy += apyVelocity * dt; odo.step(value, dt)
            }
            hide += (hideTarget - hide) * (1 - exp(-dt * 14))
        }
        var stickerScale: Double {
            let s = clock - popAt
            return s >= 0 && s < 0.6 ? 1 + 0.16 * sin(s * 22) * exp(-s * 7) - 0.08 * exp(-s * 30) : 1
        }
    }
    // Replay the stateful springs at 60 Hz with the original 1/240 integrator substeps.
    static func at(_ seconds: Double) -> State {
        let t = max(0, seconds).truncatingRemainder(dividingBy: loop)
        var state = State()
        let frames = Int(floor(t * 60 + 1e-10))
        if frames > 0 {
            for i in 1...frames {
                state.step(1 / 60.0, auto: true, reduced: false, scriptTime: Double(i) / 60)
            }
        }
        let elapsed = Double(frames) / 60
        if t - elapsed > 1e-10 { state.step(t - elapsed, auto: true, reduced: false, scriptTime: t) }
        return state
    }
}
