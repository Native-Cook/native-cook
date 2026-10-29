import Foundation

// The jam's physics: every car is a spring to a shared ground, leans when it lags, stretches along
// its width when it moves fast (and grows tall in the air), and pushes its neighbours apart through
// contact springs. A hard bump makes the hit car honk, a harder one makes it hop. The simulation runs
// at a fixed step of 1/240 s in double precision, so the loop (stepped from rest at t = 0 with the
// scripted input of `TrafficJamShakeMotion`) is the same on every run and in every stack.

/// One car of the pile, in design points: anchor = the front face's bottom centre.
struct TrafficJamShakeCar {
    var x: Double
    var y: Double
    /// Front face width, body height, cabin height, length (depth of the extrusion).
    var w: Double
    var hb: Double
    var hc: Double
    var l: Double
    var color: Int
    /// Rest angle, radians (positive turns clockwise on screen).
    var angle: Double
}

enum TrafficJamShakeMotion {
    static let loop = 8.0
    static let dt = 1.0 / 240
    /// The scripted tap on the front car.
    static let tapTime = 0.95
    static let tapCar = 7
    static let shakeStart = 1.95
    static let shakeEnd = 5.0
    /// One left-right swing of the ghost finger, seconds.
    static let swing = 0.66
    static let amplitude = 10.0
    /// The residual is faded out over this span so the loop ends exactly at rest.
    static let fadeStart = 6.4
    static let fadeEnd = 7.2
    static let userHold = 2.0
    static let settle = 0.6
    static let honkLife = 0.5

    // Physics constants.
    static let groundOmega = 30.0
    static let groundZeta = 0.9
    static let carZeta = 0.2
    static let contactK = 1600.0
    static let contactC = 10.0
    static let honkSpeed = 70.0
    static let hopSpeed = 100.0
    static let gravity = 2600.0
    static let tapLift = 380.0
    static let leanPerPoint = 0.007
    static let leanOmega = 13.0
    static let leanZeta = 0.2
    static let stretchOmega = 18.0
    static let stretchZeta = 0.25

    /// The pile, back to front (paint order). Colours index `TrafficJamShakePalette.bodies`.
    static let cars: [TrafficJamShakeCar] = [
        TrafficJamShakeCar(x: 62, y: 150, w: 80, hb: 66, hc: 30, l: 54, color: 2, angle: 0),
        TrafficJamShakeCar(x: 150, y: 146, w: 78, hb: 74, hc: 28, l: 52, color: 1, angle: 0),
        TrafficJamShakeCar(x: 240, y: 156, w: 84, hb: 82, hc: 30, l: 58, color: 0, angle: 0),
        TrafficJamShakeCar(x: 70, y: 228, w: 104, hb: 36, hc: 28, l: 66, color: 3, angle: 0.2),
        TrafficJamShakeCar(x: 196, y: 220, w: 120, hb: 30, hc: 26, l: 72, color: 2, angle: 0),
        TrafficJamShakeCar(x: 92, y: 288, w: 132, hb: 34, hc: 26, l: 70, color: 0, angle: 0),
        TrafficJamShakeCar(x: 236, y: 290, w: 110, hb: 36, hc: 28, l: 66, color: 1, angle: -0.12),
        TrafficJamShakeCar(x: 196, y: 344, w: 112, hb: 28, hc: 24, l: 60, color: 4, angle: 0),
    ]
    /// Neighbours that touch in the pile.
    static let pairs: [(Int, Int)] = [
        (0, 1), (1, 2), (0, 3), (1, 3), (1, 4), (2, 4), (3, 4), (3, 5),
        (4, 5), (4, 6), (2, 6), (5, 6), (5, 7), (6, 7),
    ]

    /// Extrusion direction: 34° up and to the right.
    static let dirX = cos(34 * Double.pi / 180)
    static let dirY = -sin(34 * Double.pi / 180)

    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }
    static func omega(_ i: Int) -> Double { 2 * .pi * (1.4 + 1.1 * hash(Double(i) + 1)) }
    static func spinSign(_ i: Int) -> Double { hash(Double(i) + 7) < 0.5 ? -1 : 1 }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func smooth(_ t: Double, _ a: Double, _ b: Double) -> Double {
        let x = seg(t, a, b)
        return x * x * (3 - 2 * x)
    }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInCubic(_ x: Double) -> Double { x * x * x }

    /// The ground target of the ghost finger's shake, design points.
    static func target(at t: Double) -> (x: Double, y: Double) {
        guard t > shakeStart, t < shakeEnd else { return (0, 0) }
        let a = amplitude * smooth(t, shakeStart, 2.6) * (1 - smooth(t, shakeEnd - 0.55, shakeEnd))
        let phase = 2 * Double.pi * (t - shakeStart) / swing
        return (a * sin(phase), 0.25 * a * sin(2 * phase))
    }

    /// 1 for most of the loop, easing to 0 before it ends so the last frame is the rest frame.
    static func window(at t: Double) -> Double { 1 - smooth(t, fadeStart, fadeEnd) }

    /// The ghost finger: opacity, scale, and where it is (a car's roof, or the pile's centre + offset).
    static func finger(at t: Double) -> (opacity: Double, scale: Double, onTap: Bool) {
        if t < 1.5 {
            let i = easeOutCubic(seg(t, 0.75, 0.95))
            let o = easeInCubic(seg(t, 1.0, 1.25))
            return (i * (1 - o), 0.8 + 0.2 * i - 0.12 * seg(t, 0.92, 0.98) * (1 - seg(t, 0.98, 1.08)), true)
        }
        let i = easeOutCubic(seg(t, 1.7, 1.95))
        let o = easeInCubic(seg(t, shakeEnd, shakeEnd + 0.25))
        return (i * (1 - o), 0.8 + 0.2 * i, false)
    }

    /// Engine idle: a tiny vertical tremble that repeats 36 times per loop.
    static func tremble(_ i: Int, at t: Double) -> Double {
        0.35 * sin(2 * Double.pi * 36 * t / loop + 2 * Double.pi * hash(Double(i) + 3))
    }
}

final class TrafficJamShakeSim {
    typealias M = TrafficJamShakeMotion

    struct Body {
        var px = 0.0, py = 0.0, vx = 0.0, vy = 0.0
        var z = 0.0, vz = 0.0
        var a = 0.0, va = 0.0
        var e = 0.0, ve = 0.0
        /// Seconds since the last honk.
        var honk = 9.0
        var honkCool = 0.0
        var hopCool = 0.0
    }

    private struct Pair { var i: Int; var j: Int; var nx: Double; var ny: Double }

    private(set) var bodies: [Body]
    private(set) var gx = 0.0, gy = 0.0
    private var gvx = 0.0, gvy = 0.0
    /// Where the ground is pulled to: the script's shake, or the finger.
    var tx = 0.0, ty = 0.0
    /// Steps done since the last reset.
    private(set) var steps = 0
    /// True while the loop's script drives the ground (false under the finger).
    var scripted = true
    /// Counts hard bumps, for haptics.
    private(set) var bumps = 0
    private var tapped = false
    private let pairs: [Pair]
    private let omegas: [Double]
    private let neighbours: [[(Int, Double, Double)]]

    var time: Double { Double(steps) * M.dt }

    init() {
        let cars = M.cars
        bodies = Array(repeating: Body(), count: cars.count)
        omegas = cars.indices.map { M.omega($0) }
        var list: [Pair] = []
        var near = Array(repeating: [(Int, Double, Double)](), count: cars.count)
        for (i, j) in M.pairs {
            let dx = cars[j].x - cars[i].x
            let dy = cars[j].y - cars[i].y
            let len = max(hypot(dx, dy), 1)
            list.append(Pair(i: i, j: j, nx: dx / len, ny: dy / len))
            near[i].append((j, dx / len, dy / len))
            near[j].append((i, -dx / len, -dy / len))
        }
        pairs = list
        neighbours = near
    }

    func reset() {
        bodies = Array(repeating: Body(), count: bodies.count)
        gx = 0; gy = 0; gvx = 0; gvy = 0; tx = 0; ty = 0
        steps = 0
        tapped = false
        scripted = true
    }

    /// Multiplies every deviation from rest by `k` (used when a touch picks up a fading loop).
    func scale(by k: Double) {
        for i in bodies.indices {
            bodies[i].px *= k; bodies[i].py *= k; bodies[i].vx *= k; bodies[i].vy *= k
            bodies[i].z *= k; bodies[i].vz *= k; bodies[i].a *= k; bodies[i].va *= k
            bodies[i].e *= k; bodies[i].ve *= k
        }
        gx *= k; gy *= k; gvx *= k; gvy *= k
    }

    /// Steps until `t` seconds after the last reset; a gap longer than `maxGap` is skipped, not simulated.
    func run(until t: Double, maxGap: Double = .infinity) {
        let target = Int((t / M.dt).rounded(.down))
        if Double(target - steps) * M.dt > maxGap { steps = target - Int(maxGap / M.dt) }
        while steps < target { step() }
    }

    /// Makes car `i` hop and honk.
    func tap(_ i: Int) {
        if bodies[i].z < 6 { bodies[i].vz = M.tapLift }
        bodies[i].va += 2 * M.spinSign(i)
        bodies[i].honk = 0
        bodies[i].honkCool = 0.8
        bodies[i].hopCool = 0.6
    }

    /// Honk only (reduced motion).
    func honk(_ i: Int) {
        bodies[i].honk = 0
        bodies[i].honkCool = 0.8
    }

    private func bump(_ k: Int, _ speed: Double) {
        if speed > M.honkSpeed, bodies[k].honkCool <= 0 {
            bodies[k].honk = 0
            bodies[k].honkCool = 0.8
            bumps += 1
        }
        if speed > M.hopSpeed, bodies[k].hopCool <= 0, bodies[k].z < 1 {
            bodies[k].vz = min(300, 2.5 * speed)
            bodies[k].va += 1.2 * M.spinSign(k)
            bodies[k].hopCool = 0.6
        }
    }

    private func step() {
        let dt = M.dt
        let t = time
        if scripted {
            let g = M.target(at: t)
            tx = g.x
            ty = g.y
            if !tapped, t >= M.tapTime {
                tapped = true
                tap(M.tapCar)
            }
        }

        // The ground follows its target on a stiff spring.
        let w0 = M.groundOmega
        gvx += (w0 * w0 * (tx - gx) - 2 * M.groundZeta * w0 * gvx) * dt
        gvy += (w0 * w0 * (ty - gy) - 2 * M.groundZeta * w0 * gvy) * dt
        gx += gvx * dt
        gy += gvy * dt

        var ax = [Double](repeating: 0, count: bodies.count)
        var ay = [Double](repeating: 0, count: bodies.count)
        for i in bodies.indices {
            let w = omegas[i]
            let b = bodies[i]
            ax[i] = -w * w * (b.px - gx) - 2 * M.carZeta * w * (b.vx - gvx)
            ay[i] = -w * w * (b.py - gy) - 2 * M.carZeta * w * (b.vy - gvy)
        }
        for p in pairs {
            let bi = bodies[p.i]
            let bj = bodies[p.j]
            let d = (bj.px - bi.px) * p.nx + (bj.py - bi.py) * p.ny
            guard d < 0 else { continue }
            let vi = bi.vx * p.nx + bi.vy * p.ny
            let vj = bj.vx * p.nx + bj.vy * p.ny
            let approach = max(0, vi - vj)
            let f = M.contactK * -d + M.contactC * approach
            ax[p.i] -= f * p.nx
            ay[p.i] -= f * p.ny
            ax[p.j] += f * p.nx
            ay[p.j] += f * p.ny
            // The car being run into is the one that complains.
            bump(vi > -vj ? p.j : p.i, approach)
        }

        for i in bodies.indices {
            var b = bodies[i]
            b.vx += ax[i] * dt
            b.vy += ay[i] * dt
            b.px += b.vx * dt
            b.py += b.vy * dt

            if b.z > 0 || b.vz > 0 {
                b.vz -= M.gravity * dt
                b.z += b.vz * dt
                if b.z <= 0 {
                    let impact = -b.vz
                    b.z = 0
                    b.vz = impact > 160 ? impact * 0.28 : 0
                    b.ve += 0.0025 * impact
                    if impact > 220 {
                        for (j, nx, ny) in neighbours[i] where bodies[j].z < 1 && bodies[j].hopCool <= 0 {
                            bodies[j].vz += 0.32 * impact
                            bodies[j].vx += 0.12 * impact * nx
                            bodies[j].vy += 0.12 * impact * ny
                            bodies[j].hopCool = 0.6
                        }
                    }
                }
            }

            let lean = M.leanPerPoint * (b.px - gx)
            let wa = M.leanOmega
            b.va += (-wa * wa * (b.a - lean) - 2 * M.leanZeta * wa * b.va) * dt
            b.a += b.va * dt

            let stretch = min(0.4, 0.0024 * abs(b.vx - gvx)) - min(0.18, 0.0006 * abs(b.vz))
            let we = M.stretchOmega
            b.ve += (-we * we * (b.e - stretch) - 2 * M.stretchZeta * we * b.ve) * dt
            b.e = min(0.7, max(-0.35, b.e + b.ve * dt))

            b.honk += dt
            b.honkCool -= dt
            b.hopCool -= dt
            bodies[i] = b
        }
        steps += 1
    }
}
