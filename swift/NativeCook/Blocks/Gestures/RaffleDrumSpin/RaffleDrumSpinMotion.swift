import CoreGraphics
import Foundation

// Direct Double-precision port of rds-motion.ts. Keep operation order aligned with the reference.
enum RaffleDrumSpinMotion {
    static let loop = 10.0
    static let referenceWidth = 402.0
    static let referenceHeight = 874.0

    enum T {
        static let select = 0.6...0.92
        static let fly = 0.75...1.35
        static let flyStagger = 0.1
        static let landBounce = 0.3
        static let press = 1.6...1.75
        static let spin = 1.7...3.6
        static let eject = 3.45...4.0
        static let handoff = 0.18
        static let focus = 3.5...3.9
        static let antic = 4.0...4.75
        static let stitches = 4.05...4.55
        static let morph = 4.75...5.7
        static let burst = 5.5
        static let number = 5.6...6.2
        static let sweep = 6.2...6.9
        static let claim = 6.35...6.95
        static let hold = 7.0
        static let claimPress = 7.85...8.0
        static let drain = 0.45
        static let tear = 8.0...8.6
        static let burn = 8.15...8.6
        static let dust = 0.42
        static let focusOut = 8.6...9.0
        static let back = 8.75...9.25
        static let backStagger = 0.1
        static let entered = 8.6...8.8
        static let iconBack = 8.55...8.85
        static let reselect = 9.4...9.72
    }

    enum Layout {
        static let close = CGPoint(x: 362, y: 88)
        static let closeD = 44.0
        static let drum = CGPoint(x: 205, y: 178)
        static let drumRadius = 70.0
        static let titleBase = 386.0
        static let cardY = 561.0
        static let cardWidth = 167.0
        static let cardHeight = 202.0
        static let cardRadius = 18.0
        static let cardGap = 11.0
        static let cardX = 17.0
        static let play = CGRect(x: 17, y: 789, width: 368, height: 48)
        static let ticket = CGRect(x: 51, y: 376.5, width: 300, height: 111)
        static let ticketBody = 0.729
        static let reveal = CGPoint(x: 201, y: 430)
        static let revealRadius = 60.0
    }

    static let ticket = (x: 201.0, y: 432.0, w: 300.0, h: 111.0, body: 0.729)
    static let idleRate = 0.35
    static let turns = 3.0
    static let spinExtra = Double.pi * 2 * turns - idleRate * loop
    static let camera = (yaw: 0.2, pitch: -0.27, dist: 6.0)
    static let ballRadius = 0.15
    private static let inner = 1 - 0.03 - ballRadius
    static let stripMinimum = -(Layout.cardX * 2 + 3 * Layout.cardWidth + 2 * Layout.cardGap - referenceWidth)
    static let resume = 2.0

    static let iconBalls: [[(dx: Double, radius: Double)]] = [
        [(84, 28)],
        [(55, 24), (107, 24)],
        [(42, 20), (83, 20), (124, 20)],
    ]

    struct Ball: Identifiable {
        var id: Int
        var x: Double
        var y: Double
        var r: Double
        var z: Double
        var rx: Double
        var ry: Double
        var rz: Double
        var a: Double
        var glow: Double
        var sq: Double

        var radius: Double { r }
        var rotation: Double { rx }
        var alpha: Double { a }
        var squash: Double { sq }
        var depth: Double { z }
    }

    struct Frame {
        var t: Double
        var theta: Double
        var omega: Double
        var speed: Double
        var from: Int
        var to: Int
        var selK: Double
        var sweep: Double
        var check: Double
        var strip: Double
        var iconA: [[Double]]
        var balls: [Ball]
        var hero: Ball?
        var press: Double
        var cardPress: [Double]
        var shimmer: Double
        var dim: Double
        var focus: Double
        var stitches: Double
        var stitchT: Double
        var morph: Double
        var ticketA: Double
        var number: Double
        var foil: Double
        var claimK: Double
        var claimGlint: Double
        var claimPress: Double
        var tear: Double
        var burn: Double
        var absorb: Double
        var land: Double
        var tilt: [Double]
        var lit: Double
        var streak: Double
        var streakAt: [Double]
        var rays: Double
        var ring: Double
        var ringR: Double
        var aurora: Double
        var lens: [Double]
        var fill: Double
        var drain: Double
        var drainAt: Double
        var idleT: Double
        var entered: [Double]
        var burst: Double
        var glow: Double
        var vortex: Double

        var selection: Double { selK }
        var cageAngle: Double { theta }
        var iconAlpha: [[Double]] { iconA }
        var stitchAge: Double { stitchT }
        var ticketAlpha: Double { ticketA }
        var claim: Double { claimK }
        var ringRadius: Double { ringR }
        var burstAge: Double { burst }
        var tiltSize: CGSize { CGSize(width: tilt[0], height: tilt[1]) }
    }

    struct Story {
        var from: Int
        var to: Int
        var reselect: Bool
        var tap: Double
        var tapOn: Int
        var touch: Bool
        var strip: Double?
        var tiltX: Double?
        var tiltY: Double?
        var pressX: Double?

        init(
            from: Int = 0,
            to: Int = 1,
            reselect: Bool = true,
            tap: Double = 0,
            tapOn: Int = -1,
            touch: Bool = false,
            strip: Double? = nil,
            tiltX: Double? = nil,
            tiltY: Double? = nil,
            pressX: Double? = nil
        ) {
            self.from = from
            self.to = to
            self.reselect = reselect
            self.tap = tap
            self.tapOn = tapOn
            self.touch = touch
            self.strip = strip
            self.tiltX = tiltX
            self.tiltY = tiltY
            self.pressX = pressX
        }
    }

    static let autoStory = Story()

    struct Driver {
        var mode: Int
        var t: Double
        var stop: Double
        var last: Double
        var from: Int
        var to: Int
        var spin: Double
        var spinV: Double
        var idle: Double
        var tap: Double
        var tapOn: Int
        var held: Int
        var strip: Double
        var stripV: Double
        var drag: Bool
        var free: Bool
        var goal: Double
        var tiltX: Double
        var tiltY: Double
        var tiltVX: Double
        var tiltVY: Double
        var tilting: Bool
        var pressX: Double
    }

    struct Baked {
        let t0: Double
        let count: Int
        let data: [Double]
    }

    private struct InsideBall {
        var x: Double
        var y: Double
        var z: Double
        var roll: Double
    }

    struct Projection {
        var x: Double
        var y: Double
        var z: Double
        var f: Double
    }

    static func clamp01(_ x: Double) -> Double {
        x < 0 ? 0 : x > 1 ? 1 : x
    }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        clamp01((t - a) / (b - a))
    }

    static func seg(_ t: Double, _ range: ClosedRange<Double>) -> Double {
        seg(t, range.lowerBound, range.upperBound)
    }

    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double {
        a + (b - a) * k
    }

    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        let cx = 3 * x1
        let bx = 3 * (x2 - x1) - cx
        let ax = 1 - cx - bx
        let cy = 3 * y1
        let by = 3 * (y2 - y1) - cy
        let ay = 1 - cy - by
        var u = x
        for _ in 0..<6 {
            let e = ((ax * u + bx) * u + cx) * u - x
            let d = (3 * ax * u + 2 * bx) * u + cx
            if abs(e) < 1e-5 { break }
            if abs(d) < 1e-6 {
                var lo = 0.0
                var hi = 1.0
                u = x
                for _ in 0..<20 {
                    let value = ((ax * u + bx) * u + cx) * u
                    if abs(value - x) < 1e-5 { break }
                    if value < x { lo = u } else { hi = u }
                    u = (lo + hi) / 2
                }
                break
            }
            u -= e / d
        }
        return ((ay * u + by) * u + cy) * u
    }

    static func easeOut(_ x: Double) -> Double { bezier(x, 0.23, 1, 0.32, 1) }
    static func easeLiquid(_ x: Double) -> Double { bezier(x, 0.45, 0, 0.3, 1) }
    static func easeInOut(_ x: Double) -> Double { bezier(x, 0.77, 0, 0.175, 1) }

    static func random(_ i: Int, _ salt: Double) -> Double {
        let x = sin(Double(i) * 127.1 + salt * 311.7) * 43_758.5453
        return x - floor(x)
    }

    static func project(_ x: Double, _ y: Double, _ z: Double, _ sx: Double) -> Projection {
        let cy = cos(camera.yaw)
        let sy = sin(camera.yaw)
        let x1 = cy * x + sy * z
        let z1 = -sy * x + cy * z
        let cp = cos(camera.pitch)
        let sp = sin(camera.pitch)
        let y2 = cp * y - sp * z1
        let z2 = sp * y + cp * z1
        let depth = camera.dist + z2
        let f = Layout.drumRadius * camera.dist * sx
        return Projection(x: Layout.drum.x * sx + (f * x1) / depth, y: Layout.drum.y * sx - (f * y2) / depth, z: depth, f: f)
    }

    private static func spinCurve(_ u: Double) -> Double {
        if u <= 0 { return 0 }
        if u >= 1 { return 1 }
        let k = 0.4
        let a = 3 / (2 * k + k * k)
        if u < k { return a * u * u }
        let v = (u - k) / (1 - k)
        return a * k * k + (1 - a * k * k) * (1 - (1 - v) * (1 - v) * (1 - v))
    }

    static func cageAngle(_ t: Double) -> Double {
        idleRate * t + spinExtra * spinCurve(seg(t, T.spin))
    }

    static func iconCentre(_ card: Int, _ k: Int, _ strip: Double, _ sx: Double, _ sy: Double) -> (x: Double, y: Double, r: Double) {
        let ball = iconBalls[card][k]
        return (
            (Layout.cardX + Double(card) * (Layout.cardWidth + Layout.cardGap) + ball.dx + strip) * sx,
            (Layout.cardY + 60) * sy,
            ball.radius * sx
        )
    }

    private static let simHz = 480
    private static let bakeHz = 120
    private static let gravity = 62.0
    private static let wallE = 0.32
    private static let ballE = 0.55
    private static let grip = 12.0
    private static let wires = 6.0
    private static let kick = 0.3

    private static func bake(_ n: Int) -> Baked {
        let t0 = T.fly.upperBound
        let t1 = T.back.upperBound + 2 * T.backStagger
        let count = Int(ceil((t1 - t0) * Double(bakeHz))) + 2
        var data = Array(repeating: 0.0, count: count * n * 4)
        var p: [[Double]] = []
        var v: [[Double]] = []
        var roll: [Double] = []
        var spinRate: [Double] = []
        for k in 0..<n {
            let phi = 0.16 * sin(1.3 * (T.fly.upperBound + Double(k) * T.flyStagger) + 2 * Double(k))
            p.append([(Double(k) - Double(n - 1) / 2) * 0.42, -inner * cos(phi), inner * sin(phi)])
            v.append([0, -1.6, 0])
            roll.append(Double(k) * 1.7)
            spinRate.append(0)
        }
        func live(_ k: Int, _ t: Double) -> Bool {
            if t < T.fly.upperBound + Double(k) * T.flyStagger { return false }
            if k == 0 { return t < T.eject.lowerBound }
            return t < T.back.lowerBound + Double(k - 1) * T.backStagger
        }
        let h = 1 / Double(simHz)
        let steps = simHz / bakeHz
        var t = t0
        var wirePrev = Array(repeating: 0, count: n)
        for s in 0..<count {
            for k in 0..<n {
                let o = (s * n + k) * 4
                data[o] = p[k][0]
                data[o + 1] = p[k][1]
                data[o + 2] = p[k][2]
                data[o + 3] = roll[k]
            }
            for _ in 0..<steps {
                let w = (cageAngle(t + h) - cageAngle(t - h)) / (2 * h)
                let ang = cageAngle(t)
                for k in 0..<n {
                    if !live(k, t) { continue }
                    v[k][1] -= gravity * h
                    p[k][0] += v[k][0] * h
                    p[k][1] += v[k][1] * h
                    p[k][2] += v[k][2] * h
                    let d = hypot(p[k][0], hypot(p[k][1], p[k][2]))
                    if d > inner {
                        let nx = p[k][0] / d
                        let ny = p[k][1] / d
                        let nz = p[k][2] / d
                        p[k][0] = nx * inner
                        p[k][1] = ny * inner
                        p[k][2] = nz * inner
                        let vn = v[k][0] * nx + v[k][1] * ny + v[k][2] * nz
                        if vn > 0 {
                            v[k][0] -= (1 + wallE) * vn * nx
                            v[k][1] -= (1 + wallE) * vn * ny
                            v[k][2] -= (1 + wallE) * vn * nz
                        }
                        let wy = -w * p[k][2]
                        let wz = w * p[k][1]
                        let g = min(1, grip * h)
                        v[k][1] += (wy - v[k][1]) * g
                        v[k][2] += (wz - v[k][2]) * g
                        v[k][0] *= 1 - 2.5 * h
                        let phase = Int(floor(((ang - atan2(p[k][2], -p[k][1])) * wires) / Double.pi))
                        if phase != wirePrev[k] {
                            let slideY = wy - v[k][1]
                            let slideZ = wz - v[k][2]
                            let bump = hypot(slideY, slideZ) * 0.35 + abs(w) * inner * 0.05
                            v[k][1] += slideY * kick - ny * bump
                            v[k][2] += slideZ * kick - nz * bump
                            v[k][0] += 0.25 * bump * sin(Double(phase) * 2.39 + Double(k) * 1.3)
                            wirePrev[k] = phase
                        }
                        spinRate[k] = (hypot(v[k][1] - wy, v[k][2] - wz) / ballRadius) * (w == 0 ? 1 : w.sign == .minus ? -1 : 1)
                    }
                    roll[k] += spinRate[k] * h
                    spinRate[k] *= 1 - 0.6 * h
                }
                for a in 0..<n {
                    if a + 1 >= n { continue }
                    for b in (a + 1)..<n {
                        if !live(a, t) || !live(b, t) { continue }
                        let dx = p[b][0] - p[a][0]
                        let dy = p[b][1] - p[a][1]
                        let dz = p[b][2] - p[a][2]
                        let dd = hypot(dx, hypot(dy, dz))
                        if dd >= 2 * ballRadius || dd < 1e-6 { continue }
                        let nx = dx / dd
                        let ny = dy / dd
                        let nz = dz / dd
                        let push = (2 * ballRadius - dd) / 2
                        p[a][0] -= nx * push
                        p[a][1] -= ny * push
                        p[a][2] -= nz * push
                        p[b][0] += nx * push
                        p[b][1] += ny * push
                        p[b][2] += nz * push
                        let relative = (v[b][0] - v[a][0]) * nx + (v[b][1] - v[a][1]) * ny + (v[b][2] - v[a][2]) * nz
                        if relative < 0 {
                            let impulse = (-(1 + ballE) * relative) / 2
                            v[a][0] -= impulse * nx
                            v[a][1] -= impulse * ny
                            v[a][2] -= impulse * nz
                            v[b][0] += impulse * nx
                            v[b][1] += impulse * ny
                            v[b][2] += impulse * nz
                        }
                    }
                }
                t += h
            }
        }
        return Baked(t0: t0, count: count, data: data)
    }

    static let baked = [bake(1), bake(2), bake(3)]

    private static func insideBall(_ k: Int, _ n: Int, _ t: Double, _ manualSpin: Double, _ manualSpeed: Double) -> InsideBall {
        let selectedBake = baked[n - 1]
        let f = max(0, min(Double(selectedBake.count) - 1.001, (t - selectedBake.t0) * Double(bakeHz)))
        let i = Int(floor(f))
        let u = f - Double(i)
        let o0 = (i * n + k) * 4
        let o1 = ((i + 1) * n + k) * 4
        let data = selectedBake.data
        var x = data[o0] + (data[o1] - data[o0]) * u
        var y = data[o0 + 1] + (data[o1 + 1] - data[o0 + 1]) * u
        var z = data[o0 + 2] + (data[o1 + 2] - data[o0 + 2]) * u
        let roll = data[o0 + 3] + (data[o1 + 3] - data[o0 + 3]) * u
        if manualSpeed > 0.001 {
            let a = 0.9 * manualSpeed * (manualSpin == 0 ? 1 : manualSpin.sign == .minus ? -1 : 1)
            let c = cos(a)
            let s = sin(a)
            let y2 = c * y - s * z
            z = s * y + c * z
            y = y2
            x += 0.04 * manualSpeed * sin(t * 9 + Double(k))
        }
        return InsideBall(x: x, y: y, z: z, roll: roll + manualSpin * 0.5)
    }

    private static func quad(_ a: Double, _ b: Double, _ c: Double, _ u: Double) -> Double {
        let v = 1 - u
        return v * v * a + 2 * v * u * b + u * u * c
    }

    static func focusAt(_ t: Double) -> Double {
        easeInOut(seg(t, T.focus)) * (1 - easeInOut(seg(t, T.focusOut)))
    }

    private static func stripOf(_ card: Int) -> Double {
        card == 2 ? stripMinimum : 0
    }

    static func stripForCard(_ card: Int) -> Double { stripOf(card) }

    static func rubber(_ x: Double) -> Double {
        let over = x > 0 ? x : x < stripMinimum ? x - stripMinimum : 0
        if over == 0 { return x }
        let d = referenceWidth * 0.55
        let r = (1 - 1 / ((abs(over) * 0.55) / d + 1)) * d
        return (x > 0 ? 0 : stripMinimum) + (over.sign == .minus ? -r : r)
    }

    static func rubberBand(_ value: Double) -> Double { rubber(value) }

    private static func stepStrip(_ driver: inout Driver, _ h: Double, _ goal: Double) {
        if driver.drag { return }
        var target = goal
        if target.isNaN {
            if driver.strip > 0 { target = 0 }
            else if driver.strip < stripMinimum { target = stripMinimum }
        }
        if target.isNaN {
            driver.strip += driver.stripV * h
            driver.stripV *= pow(0.998, h * 1000)
            if abs(driver.stripV) < 4 { driver.stripV = 0 }
            return
        }
        let w = 16.0
        let a = -w * w * (driver.strip - target) - 2 * w * driver.stripV
        driver.stripV += a * h
        driver.strip += driver.stripV * h
        if abs(driver.strip - target) < 0.2 && abs(driver.stripV) < 2 {
            driver.strip = target
            driver.stripV = 0
        }
    }

    static func stripAt(_ t: Double, _ story: Story) -> Double {
        let reselect = story.reselect && t >= T.reselect.lowerBound
        let from = reselect ? story.to : story.from
        let to = reselect ? story.from : story.to
        let window = reselect ? T.reselect : T.select
        return mix(stripOf(from), stripOf(to), easeOut(seg(t, window)))
    }

    static func frameAt(
        _ t: Double,
        sx: Double = 1,
        sy: Double = 1,
        story: Story = autoStory,
        manualSpin: Double = 0,
        manualSpeed: Double = 0,
        idle: Double = 0
    ) -> Frame {
        let reselect = story.reselect && t >= T.reselect.lowerBound
        let from = reselect ? story.to : story.from
        let to = reselect ? story.from : story.to
        let selectionWindow = reselect ? T.reselect : T.select
        let selK = easeOut(seg(t, selectionWindow))
        let sweep = seg(t, selectionWindow.lowerBound, selectionWindow.lowerBound + 0.5)
        let check = easeOut(seg(t, selectionWindow.lowerBound + 0.02, selectionWindow.lowerBound + 0.2))
        let strip = story.strip ?? stripAt(t, story)

        let theta = cageAngle(t) + manualSpin
        let omega = (cageAngle(t + 0.008) - cageAngle(t - 0.008)) / 0.016
        let speed = min(1, max(0, (omega - idleRate) / 14))

        let card = story.to
        let n = card + 1
        var iconA = [[1.0], [1.0, 1.0], [1.0, 1.0, 1.0]]
        var balls: [Ball] = []
        balls.reserveCapacity(n)
        var hero: Ball?
        let reveal = (x: ticket.x * sx, y: ticket.y * sy, r: 56 * sx)
        for k in 0..<n {
            let home = iconCentre(card, k, strip, sx, sy)
            let inside = insideBall(k, n, t, manualSpin, manualSpeed)
            let projected = project(inside.x, inside.y, inside.z, sx)
            let projectedRadius = (projected.f * ballRadius) / projected.z
            let flyStart = T.fly.lowerBound + Double(k) * T.flyStagger
            let flyEnd = T.fly.upperBound + Double(k) * T.flyStagger
            let winner = k == 0
            let backStart = T.back.lowerBound + Double(k - 1) * T.backStagger
            let backEnd = T.back.upperBound + Double(k - 1) * T.backStagger
            var ball = Ball(id: k, x: home.x, y: home.y, r: home.r, z: 3.2, rx: inside.roll, ry: 0.6 * sin(t * 1.7 + Double(k)), rz: 0.3 * Double(k), a: 0, glow: 0, sq: 1)
            let inPlay = winner ? t >= flyStart && t < T.morph.upperBound : t >= flyStart && t < backEnd
            if inPlay {
                iconA[card][k] = 0
                ball.a = 1
                if t < flyEnd {
                    let u = easeInOut(seg(t, flyStart, flyEnd))
                    ball.x = quad(home.x, mix(home.x, projected.x, 0.6), projected.x, u)
                    ball.y = quad(home.y, Layout.drum.y * sy - 150 * sy, projected.y, u)
                    ball.r = mix(home.r, projectedRadius, easeOut(u))
                    ball.z = u < 0.55 ? 3.2 : mix(3.2, projected.z, seg(u, 0.55, 1))
                } else {
                    ball.x = projected.x
                    ball.y = projected.y
                    ball.r = projectedRadius
                    ball.z = projected.z
                    let landingTime = t - flyEnd
                    if landingTime < T.landBounce {
                        ball.sq = 1 - 0.22 * exp(-landingTime * 14) * cos(landingTime * 30)
                    }
                }
                if winner && t >= T.eject.lowerBound {
                    let u = seg(t, T.eject)
                    let e = easeInOut(u)
                    ball.x = quad(projected.x, Layout.drum.x * sx, reveal.x, e)
                    ball.y = quad(projected.y, Layout.drum.y * sy - 170 * sy, reveal.y, e)
                    ball.r = mix(projectedRadius, reveal.r, easeOut(u))
                    ball.z = mix(projected.z, 3.1, seg(u, 0, 0.18))
                    ball.sq = u < 0.12 ? 1 - 0.25 * sin((u / 0.12) * Double.pi) : 1 + 0.1 * sin(seg(u, 0.12, 0.5) * Double.pi)
                    ball.glow = seg(u, 0.4, 1)
                    ball.rx += u * 4
                    if u >= T.handoff {
                        hero = ball
                        ball.a = 0
                    }
                }
                if !winner && t >= backStart {
                    let u = easeInOut(seg(t, backStart, backEnd))
                    ball.x = quad(projected.x, mix(projected.x, home.x, 0.5), home.x, u)
                    ball.y = quad(projected.y, min(projected.y, home.y) - 110 * sy, home.y, u)
                    ball.r = mix(projectedRadius, home.r, u)
                    ball.z = mix(projected.z, 3.2, seg(u, 0, 0.3))
                }
            }
            if winner && t >= T.iconBack.lowerBound && t < loop {
                iconA[card][k] = easeOut(seg(t, T.iconBack))
            } else if winner && t >= T.morph.upperBound {
                iconA[card][k] = 0
            }
            balls.append(ball)
        }

        if t >= T.eject.upperBound && t < T.morph.upperBound && hero == nil {
            hero = Ball(id: 0, x: reveal.x, y: reveal.y, r: reveal.r, z: 3.1, rx: 4 + (t - T.eject.upperBound) * 1.2, ry: 0.5, rz: 0, a: 1, glow: 1, sq: 1)
        }
        if var currentHero = hero, t >= T.eject.upperBound {
            let tt = min(t, T.morph.lowerBound)
            let u = seg(tt, T.antic.lowerBound, T.morph.lowerBound)
            let near = easeInOut(seg(t, T.antic.lowerBound, T.morph.lowerBound))
            currentHero.r = reveal.r * (1 + 0.08 * near)
            currentHero.glow = 1 + 0.5 * near
            currentHero.rx = 4 + (tt - T.eject.upperBound) * 1.2 + 6.5 * (u - sin(2 * Double.pi * u) / (2 * Double.pi))
            currentHero.ry += 0.35 * near
            let shake = seg(t, T.morph.lowerBound - 0.18, T.morph.lowerBound) * (1 - seg(t, T.morph.lowerBound, T.morph.lowerBound + 0.1))
            currentHero.x = reveal.x + sin(t * 97) * 1.6 * sx * shake
            currentHero.y = reveal.y + sin((t - T.eject.upperBound) * 3) * 3 * sy * (1 - near) + cos(t * 83) * 1.2 * sy * shake
            hero = currentHero
        }

        let pulse = story.tap
        let press = max(story.touch ? 0 : sin(Double.pi * seg(t, T.press)), story.tapOn == 3 ? pulse : 0)
        var cardPress = [0.0, 0.0, 0.0]
        if story.tapOn >= 0 && story.tapOn < 3 { cardPress[story.tapOn] = pulse }
        let shimmer = t > T.spin.lowerBound && t < T.spin.lowerBound + 0.9 ? seg(t, T.spin.lowerBound, T.spin.lowerBound + 0.9) : 0
        let focus = easeInOut(seg(t, T.focus)) * (1 - easeInOut(seg(t, T.focusOut)))
        let dim = 0.35 * focus
        let stitches = seg(t, T.stitches)
        let stitchT = t >= T.stitches.lowerBound && t < T.morph.upperBound + 0.4 ? t - T.stitches.lowerBound : -1
        let morph = seg(t, T.morph)
        let burn = seg(t, T.burn)
        let ticketA = t < T.morph.upperBound ? (t >= T.morph.lowerBound ? 1 : 0) : 1 - burn
        let absorb = sin(Double.pi * seg(t, T.burn.lowerBound + T.dust * 0.8, T.burn.upperBound + T.dust + 0.15))
        let land = t >= T.tear.upperBound && t < T.tear.upperBound + 0.6 ? t - T.tear.upperBound : -1
        let lit = easeOut(seg(t, T.morph.upperBound - 0.05, T.morph.upperBound + 0.35))
        let sway = seg(t, T.morph.upperBound, T.morph.upperBound + 0.4) * (1 - seg(t, T.claimPress.lowerBound - 0.25, T.claimPress.lowerBound + 0.1))
        let ht = t - T.morph.upperBound
        let tilt = [0.26 * sin(ht * 2.2) * sway + (story.tiltX ?? 0), 0.14 * sin(ht * 1.5 + 0.8) * sway + (story.tiltY ?? 0)]
        let number = easeOut(seg(t, T.number))
        let since = t + idle - T.sweep.lowerBound
        let sweepLength = T.sweep.upperBound - T.sweep.lowerBound
        let phase = since >= 0 && t < T.claimPress.lowerBound ? since.truncatingRemainder(dividingBy: 1.6) : -1
        let foil = phase >= 0 && phase < sweepLength ? phase / sweepLength : 0
        let glintPhase = t >= T.claim.upperBound + 0.3 && t < T.claimPress.lowerBound ? (t + idle - T.claim.upperBound - 0.3).truncatingRemainder(dividingBy: 1.6) : -1
        let claimGlint = glintPhase >= 0 && glintPhase < 0.6 ? glintPhase / 0.6 : 0
        let drainStart = T.claimPress.lowerBound + 0.05
        let claimK = easeOut(seg(t, T.claim)) * (1 - easeLiquid(seg(t, drainStart, drainStart + T.drain)))
        let fill = easeLiquid(seg(t, T.claim.lowerBound - 0.05, T.claim.upperBound + 0.05))
        let drain = easeLiquid(seg(t, drainStart, drainStart + T.drain))
        let pressX = story.pressX ?? Double.nan
        let drainAt = pressX.isNaN ? Layout.play.width / 2 : pressX
        let winningBall = hero ?? balls[0]
        let streakEase = seg(t, T.eject.lowerBound + 0.02, T.eject.lowerBound + 0.35)
        let streak = streakEase > 0 && streakEase < 1 ? pow(sin(Double.pi * streakEase), 1.3) : 0
        let streakAt = [winningBall.x, winningBall.y]
        let rays = easeOut(seg(t, T.morph.lowerBound + 0.02, T.morph.lowerBound + 0.22)) * (1 - easeInOut(seg(t, T.burst - 0.05, T.burst + 0.35)))
        let ringAge = t - T.burst
        let ring = ringAge >= 0 && ringAge < 0.5 ? pow(1 - ringAge / 0.5, 2) : 0
        let ringR = mix(40, 300, easeOut(max(0, min(1, ringAge / 0.5)))) * sx
        let aurora = easeInOut(seg(t, T.number.lowerBound, T.number.lowerBound + 0.65)) * (1 - easeOut(seg(t, T.burn.lowerBound, T.burn.upperBound - 0.1)))
        let lensFrom = Layout.cardX + Double(from) * (Layout.cardWidth + Layout.cardGap) + strip
        let lensTo = Layout.cardX + Double(to) * (Layout.cardWidth + Layout.cardGap) + strip
        let lensU = seg(t, selectionWindow)
        let lead = easeOut(min(1, lensU / 0.72))
        let trail = easeInOut(seg(lensU, 0.18, 1))
        let right = to > from
        let lensX0 = right ? mix(lensFrom, lensTo, trail) : mix(lensFrom, lensTo, lead)
        let lensX1 = (right ? mix(lensFrom, lensTo, lead) : mix(lensFrom, lensTo, trail)) + Layout.cardWidth
        let lensStrength = from != to && lensU > 0 && lensU < 1 ? pow(sin(Double.pi * lensU), 0.55) : 0
        let squeeze = 1 - 0.06 * sin(Double.pi * lensU) * (lensX1 - lensX0 - Layout.cardWidth) / (Layout.cardWidth + Layout.cardGap)
        let lensHeight = Layout.cardHeight * squeeze
        let lens = [lensX0, Layout.cardY + (Layout.cardHeight - lensHeight) / 2, lensX1 - lensX0, lensHeight, lensStrength]
        let claimPress = story.touch ? 0 : sin(Double.pi * seg(t, T.claimPress))
        let tear = t >= T.tear.lowerBound && t < T.reselect.lowerBound ? seg(t, T.tear) : 0
        var entered = [0.0, 0.0, 0.0]
        entered[card] = easeOut(seg(t, T.entered)) * (1 - seg(t, T.reselect.lowerBound, T.reselect.lowerBound + 0.3))
        let burstTime = t + (t >= T.burst ? idle : 0)
        let burst = burstTime >= T.burst && burstTime < T.burst + 2.4 ? burstTime - T.burst : -1
        let glow = 0.35 * min(1, speed * 1.4) + 0.4 * focus
        return Frame(
            t: t, theta: theta, omega: omega, speed: min(1, speed + manualSpeed), from: from, to: to,
            selK: selK, sweep: sweep, check: check, strip: strip, iconA: iconA, balls: balls, hero: hero,
            press: press, cardPress: cardPress, shimmer: shimmer, dim: dim, focus: focus, stitches: stitches,
            stitchT: stitchT, morph: morph, ticketA: ticketA, number: number, foil: foil, claimK: claimK,
            claimGlint: claimGlint, claimPress: claimPress, tear: tear, burn: burn, absorb: absorb, land: land,
            tilt: tilt, lit: lit, streak: streak, streakAt: streakAt, rays: rays, ring: ring, ringR: ringR,
            aurora: aurora, lens: lens, fill: fill, drain: drain, drainAt: drainAt, idleT: idle,
            entered: entered, burst: burst, glow: glow, vortex: speed
        )
    }

    static func makeDriver() -> Driver {
        Driver(mode: 0, t: 0, stop: loop, last: -100, from: 0, to: 1, spin: 0, spinV: 0, idle: 0, tap: 0, tapOn: -1, held: -1, strip: 0, stripV: 0, drag: false, free: false, goal: .nan, tiltX: 0, tiltY: 0, tiltVX: 0, tiltVY: 0, tilting: false, pressX: .nan)
    }

    static func stepDriver(_ driver: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool) {
        let step = min(dt, 0.05)
        let down = driver.held >= 0
        driver.tap += ((down ? 1 : 0) - driver.tap) * (1 - exp(-step / (down ? 0.035 : 0.065)))
        driver.spin += driver.spinV * step
        driver.spinV *= exp(-step * 1.6)
        if abs(driver.spinV) < 0.01 { driver.spinV = 0 }
        if driver.mode == 0 {
            // The loop follows the wall clock, not the clamped physics step: on a slow machine
            // (Xcode Cloud's simulators) frames longer than 50 ms would otherwise stretch the
            // 10 s loop, and the recording would not repeat every 10 s.
            driver.t = auto ? (driver.t + dt).truncatingRemainder(dividingBy: loop) : 0
            if auto && driver.t < dt {
                driver.from = 0
                driver.to = 1
            }
        } else if driver.mode == 1 {
            let next = driver.t + step
            if next >= driver.stop {
                driver.t = driver.stop
                driver.mode = 2
                driver.last = now
            } else {
                driver.t = next
            }
        } else if driver.mode == 2 {
            driver.idle = now - driver.last
            let waitingForClaim = driver.t >= T.hold - 0.01 && driver.t < T.claimPress.lowerBound
            if auto && driver.idle > resume && !waitingForClaim {
                driver.mode = 3
                driver.from = 0
                driver.idle = 0
            }
        } else if driver.mode == 3 {
            driver.t += step
            if driver.t >= loop {
                driver.mode = 0
                driver.t = 0
                driver.from = 0
                driver.to = 1
            }
        }
        if driver.mode != 2 { driver.idle = 0 }
        let autoStrip = driver.mode == 0 || driver.mode == 3
        let goal = autoStrip ? stripAt(driver.t, Story(from: driver.from, to: driver.to, reselect: true, tap: 0, tapOn: -1, touch: false)) : driver.goal
        for _ in 0..<4 { stepStrip(&driver, step / 4, goal) }
        if autoStrip && driver.free && !driver.drag && driver.strip == goal { driver.free = false }
        if !driver.tilting {
            let w = 11.0
            let z = 0.6
            for _ in 0..<4 {
                let h = step / 4
                driver.tiltVX += (-w * w * driver.tiltX - 2 * z * w * driver.tiltVX) * h
                driver.tiltVY += (-w * w * driver.tiltY - 2 * z * w * driver.tiltVY) * h
                driver.tiltX += driver.tiltVX * h
                driver.tiltY += driver.tiltVY * h
            }
            if abs(driver.tiltX) + abs(driver.tiltY) < 1e-4 && abs(driver.tiltVX) + abs(driver.tiltVY) < 1e-3 {
                driver.tiltX = 0
                driver.tiltY = 0
                driver.tiltVX = 0
                driver.tiltVY = 0
            }
        }
        if !driver.free { driver.strip = autoStrip ? goal : driver.strip }
    }

    static func storyOf(_ driver: Driver) -> Story {
        Story(
            from: driver.from,
            to: driver.to,
            reselect: driver.mode == 0 || driver.mode == 3,
            tap: driver.tap,
            tapOn: driver.tapOn,
            touch: driver.mode == 1 || driver.mode == 2,
            strip: driver.free ? driver.strip : nil,
            tiltX: driver.tiltX,
            tiltY: driver.tiltY,
            pressX: driver.mode == 1 || driver.mode == 2 ? driver.pressX : .nan
        )
    }

    static func pressIn(_ driver: inout Driver, _ on: Int, _ x: Double = .nan) {
        driver.held = on
        driver.tapOn = on
        if on == 3 { driver.pressX = x }
    }

    static func pressOut(_ driver: inout Driver) {
        driver.held = -1
    }

    static func selectCard(_ driver: inout Driver, _ card: Int, _ now: Double) -> Bool {
        if driver.mode != 0 && driver.t > T.press.lowerBound && driver.t < T.back.upperBound { return false }
        let before = driver.free ? driver.strip : stripAt(driver.t, storyOf(driver))
        let current = driver.mode == 0 || driver.mode == 3 ? (driver.t >= T.select.lowerBound && driver.t < T.reselect.lowerBound ? driver.to : driver.from) : driver.to
        if current == card && driver.mode != 0 { return false }
        driver.from = current
        driver.to = card
        driver.t = T.select.lowerBound
        driver.mode = 1
        driver.stop = T.press.lowerBound - 0.05
        driver.last = now
        driver.free = true
        driver.strip = before
        let left = Layout.cardX + Double(card) * (Layout.cardWidth + Layout.cardGap)
        let lo = 12 - left
        let hi = referenceWidth - 12 - Layout.cardWidth - left
        driver.goal = max(stripMinimum, min(0, min(hi, max(lo, driver.strip))))
        return true
    }

    static func pressPlay(_ driver: inout Driver, _ now: Double) -> Bool {
        driver.last = now
        if driver.mode == 0 || driver.mode == 3 {
            let chosen = driver.t >= T.select.lowerBound && driver.t < T.reselect.lowerBound ? driver.to : driver.from
            driver.from = chosen
            driver.to = chosen
            if driver.t >= T.reselect.lowerBound { driver.t = 0 }
        }
        if driver.t >= T.hold - 0.01 && driver.t < T.claimPress.lowerBound {
            driver.t = T.claimPress.lowerBound
            driver.mode = 1
            driver.stop = T.reselect.lowerBound - 0.05
            return true
        }
        if driver.t < T.press.lowerBound {
            if driver.t < T.fly.lowerBound { driver.t = T.fly.lowerBound - 0.02 }
            else if driver.t > T.fly.upperBound + 0.35 { driver.t = max(driver.t, T.press.lowerBound - 0.02) }
            driver.mode = 1
            driver.stop = T.hold
            return true
        }
        if driver.t < T.hold {
            driver.mode = 1
            driver.stop = T.hold
            return true
        }
        return false
    }

    static func touched(_ driver: inout Driver, _ now: Double) {
        driver.last = now
        if driver.mode == 0 || driver.mode == 3 {
            driver.mode = 2
            driver.to = driver.t >= T.select.lowerBound && driver.t < T.reselect.lowerBound ? driver.to : driver.from
            driver.from = driver.to
        }
    }

    static func grabStrip(_ driver: inout Driver, _ now: Double) {
        if !driver.free { driver.strip = stripAt(driver.t, storyOf(driver)) }
        touched(&driver, now)
        driver.free = true
        driver.drag = true
        driver.stripV = 0
        driver.goal = .nan
    }

    static func releaseStrip(_ driver: inout Driver, _ velocity: Double, _ now: Double) {
        driver.drag = false
        driver.stripV = velocity
        driver.last = now
    }

    static func canTilt(_ t: Double) -> Bool {
        t >= T.morph.upperBound && t < T.claimPress.lowerBound
    }

    static func grabTicket(_ driver: inout Driver, _ now: Double) {
        touched(&driver, now)
        driver.tilting = true
        driver.tiltVX = 0
        driver.tiltVY = 0
    }

    static func tiltTicket(_ driver: inout Driver, _ dx: Double, _ dy: Double, _ now: Double) {
        func soft(_ value: Double) -> Double { 0.55 * tanh(value / 0.55) }
        driver.tiltX = soft(dx / 170)
        driver.tiltY = soft(-dy / 140)
        driver.last = now
    }

    static func releaseTicket(_ driver: inout Driver, _ vx: Double, _ vy: Double, _ now: Double) {
        driver.tilting = false
        driver.tiltVX = max(-8, min(8, vx / 170))
        driver.tiltVY = max(-8, min(8, -vy / 140))
        driver.last = now
    }
}
