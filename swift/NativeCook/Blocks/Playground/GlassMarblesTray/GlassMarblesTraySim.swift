import Foundation

// Glass marbles on a wooden tray. A step-for-step port of the React Native `marblesSim.ts`, in
// Double, so all three stacks roll the same marbles. Everything lives in the tray's own frame
// (u right, v down, w up out of the floor, pt, origin at the tray's centre), so the physics never
// sees the perspective.
//
// Each tick: the tray's tilt and its small slide follow their targets on springs; gravity along
// the tilted floor, the slide's inertia and the carved cups (shallow bowls that pull a marble to
// their centre) push the marbles; rolling friction slows them; then a few passes of circle-circle
// collisions (with restitution and mass) and walls. Marbles can hop off the floor when the tray
// is shaken, and each one carries a 3D orientation that turns as it rolls, so its swirl rolls too.

enum GlassMarblesTrayTuning {
    static let tick = 1.0 / 60
    static let maxTicks = 3
    // Collisions run on `substeps` slices of each tick.
    static let substeps = 2

    // autoPlay period (s).
    static let loop = 12.0

    // Gravity along the floor (pt/s²), already scaled for a rolling ball (5/7 of free fall).
    static let gravity = 4300.0
    // A hop: gravity on a marble in the air, and how much of its fall speed a landing keeps.
    static let airGravity = 5200.0
    static let landing = 0.32
    // Rolling: speed kept per second, plus a small constant deceleration (pt/s²).
    static let rollKeep = 0.7
    static let rollFriction = 16.0
    // Cups: radius (pt), depth (pt) and the extra damping (1/s) of a marble rocking in one.
    static let cupR = 19.0
    static let cupDepth = 1.4
    static let cupDamp = 2.4
    // Restitution of glass on glass, and of glass on the wooden wall.
    static let bounce = 0.82
    static let wallBounce = 0.45
    // A collision faster than this (pt/s) clicks: a glint, and a haptic when the user is playing.
    static let clickSpeed = 170.0
    // Tray springs: the tilt (1/s², 1/s) and the slide.
    static let tiltK = 150.0
    static let tiltD = 17.0
    static let slideK = 260.0
    static let slideD = 24.0
    // How much of the slide's acceleration the marbles feel (they stay put while the tray moves).
    static let inertia = 1.0
    // The guided return at the end of an autoPlay loop: spring (1/s²) and damping (1/s) to home.
    static let guideK = 16.0
    static let guideD = 5.0

    static let flashes = 10
    static let flashLife = 0.22

    // autoPlay script (s): at rest, then the tilt traces a circle (one and a quarter turns), then a
    // shake, then the marbles settle and are led home.
    static let circleFrom = 1.0
    static let circleTo = 7.0
    static let circleTurns = 1.25
    static let circleTilt = 0.2
    static let shakeAt = 7.5
    static let shakeTime = 0.7
    static let shakeSlide = 16.0
    static let shakeRate = 5.0
    static let guideFrom = 8.7
    static let guideFull = 10.4
}

/// `Math.sign`.
@inline(__always)
private func sign(_ v: Double) -> Double { v > 0 ? 1 : (v < 0 ? -1 : v) }

struct GlassMarblesTrayMarble {
    let r: Double
    /// Glass tint, swirl colours (rgb 0…255) and how many vanes the swirl has.
    let glass: (Double, Double, Double)
    let vane: (Double, Double, Double)
    let vane2: (Double, Double, Double)
    let vanes: Int
}

struct GlassMarblesTrayTray {
    /// Inner floor half-size (pt) and the radius of its corners.
    let halfW: Double
    let halfH: Double
    let corner: Double
    /// Cup centres (pt).
    let cupX: [Double]
    let cupY: [Double]
    /// The cup each marble starts in.
    let home: [Int]

    /// The cups: a honeycomb of rows of five and four, centred on the floor.
    init(halfW: Double, halfH: Double, marbles: Int) {
        let pitch = max(8, min(62, ((halfW - 28) * 2) / 4))
        let rowGap = pitch * 0.866
        let rows = max(3, min(9, Int(floor(((halfH - 28) * 2) / rowGap)) + 1))
        var xs: [Double] = []
        var ys: [Double] = []
        for row in 0..<rows {
            let count = row % 2 == 0 ? 5 : 4
            let y = (Double(row) - Double(rows - 1) / 2) * rowGap
            for c in 0..<count {
                xs.append((Double(c) - Double(count - 1) / 2) * pitch)
                ys.append(y)
            }
        }
        // Homes: spread over the board, every other cup or so, in a fixed order.
        let step = Double(xs.count) / Double(marbles)
        var home: [Int] = []
        for i in 0..<marbles { home.append(min(xs.count - 1, Int(floor(Double(i) * step + step * 0.35)))) }
        self.halfW = halfW
        self.halfH = halfH
        corner = 20
        cupX = xs
        cupY = ys
        self.home = home
    }
}

struct GlassMarblesTrayInput {
    var tiltX = 0.0
    var tiltY = 0.0
    var slideX = 0.0
    var slideY = 0.0
    /// 0…1: how strongly each marble is led back to its home cup.
    var guide = 0.0
}

final class GlassMarblesTrayWorld {
    private typealias T = GlassMarblesTrayTuning

    let n: Int
    var x: [Double]
    var y: [Double]
    var vx: [Double]
    var vy: [Double]
    /// Height above the floor and its speed: non-zero only in a hop.
    var z: [Double]
    var vz: [Double]
    var r: [Double]
    var mass: [Double]
    /// Orientation of each marble: a 3×3 rotation, row-major, 9 numbers each.
    var rot: [Double]
    /// The tray: tilt about its x and y axes (rad) and their speeds; its slide (pt) and speeds.
    var tiltX = 0.0
    var tiltY = 0.0
    var tiltVX = 0.0
    var tiltVY = 0.0
    var slideX = 0.0
    var slideY = 0.0
    var slideVX = 0.0
    var slideVY = 0.0
    /// Glints where marbles clicked: position, age (s) and strength.
    var flashX = Array(repeating: 0.0, count: T.flashes)
    var flashY = Array(repeating: 0.0, count: T.flashes)
    var flashAge = Array(repeating: T.flashLife, count: T.flashes)
    var flashPower = Array(repeating: 0.0, count: T.flashes)
    var nextFlash = 0
    /// Clicks this tick (for the haptic), and the hardest one.
    var clicks = 0
    var hardest = 0.0
    /// Seed of the generator used by shakes and flicks.
    var seed: UInt32 = 7041

    init(tray: GlassMarblesTrayTray, set: [GlassMarblesTrayMarble]) {
        n = set.count
        x = Array(repeating: 0, count: n)
        y = Array(repeating: 0, count: n)
        vx = Array(repeating: 0, count: n)
        vy = Array(repeating: 0, count: n)
        z = Array(repeating: 0, count: n)
        vz = Array(repeating: 0, count: n)
        r = Array(repeating: 0, count: n)
        mass = Array(repeating: 0, count: n)
        rot = Array(repeating: 0, count: n * 9)
        for i in 0..<n {
            let cup = tray.home[i]
            x[i] = tray.cupX[cup]
            y[i] = tray.cupY[cup]
            r[i] = set[i].r
            mass[i] = set[i].r * set[i].r * set[i].r
            // A different resting turn for each, so the swirls do not line up.
            let a = Double(i) * 2.39996
            let b = Double(i) * 1.1
            let ca = cos(a)
            let sa = sin(a)
            let cb = cos(b)
            let sb = sin(b)
            // Rz(a)·Rx(b)
            let o = i * 9
            rot[o] = ca
            rot[o + 1] = -sa * cb
            rot[o + 2] = sa * sb
            rot[o + 3] = sa
            rot[o + 4] = ca * cb
            rot[o + 5] = -ca * sb
            rot[o + 6] = 0
            rot[o + 7] = sb
            rot[o + 8] = cb
        }
    }

    func copy(from: GlassMarblesTrayWorld) {
        x = from.x
        y = from.y
        vx = from.vx
        vy = from.vy
        z = from.z
        vz = from.vz
        rot = from.rot
        tiltX = from.tiltX
        tiltY = from.tiltY
        tiltVX = from.tiltVX
        tiltVY = from.tiltVY
        slideX = from.slideX
        slideY = from.slideY
        slideVX = from.slideVX
        slideVY = from.slideVY
        seed = from.seed
    }

    /// mulberry32: the same sequence on every platform.
    func random() -> Double {
        seed = seed &+ 0x6D2B_79F5
        var t = seed
        t = (t ^ (t >> 15)) &* (t | 1)
        t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
        return Double(t ^ (t >> 14)) / 4_294_967_296
    }

    /// Re-squares a marble's rotation (Gram–Schmidt on its rows) so rounding never skews it.
    private func orthonormalize(_ o: Int) {
        var l = (rot[o] * rot[o] + rot[o + 1] * rot[o + 1] + rot[o + 2] * rot[o + 2]).squareRoot()
        let l2 = (rot[o + 3] * rot[o + 3] + rot[o + 4] * rot[o + 4] + rot[o + 5] * rot[o + 5]).squareRoot()
        // A collapsed or non-finite matrix (never expected): start the marble's turn over.
        if !(l > 1e-9) || !(l2 > 1e-9) || !(l + l2).isFinite {
            for k in 0..<9 { rot[o + k] = k % 4 == 0 ? 1 : 0 }
            return
        }
        rot[o] /= l
        rot[o + 1] /= l
        rot[o + 2] /= l
        let d = rot[o] * rot[o + 3] + rot[o + 1] * rot[o + 4] + rot[o + 2] * rot[o + 5]
        rot[o + 3] -= d * rot[o]
        rot[o + 4] -= d * rot[o + 1]
        rot[o + 5] -= d * rot[o + 2]
        l = (rot[o + 3] * rot[o + 3] + rot[o + 4] * rot[o + 4] + rot[o + 5] * rot[o + 5]).squareRoot()
        rot[o + 3] /= l
        rot[o + 4] /= l
        rot[o + 5] /= l
        rot[o + 6] = rot[o + 1] * rot[o + 5] - rot[o + 2] * rot[o + 4]
        rot[o + 7] = rot[o + 2] * rot[o + 3] - rot[o] * rot[o + 5]
        rot[o + 8] = rot[o] * rot[o + 4] - rot[o + 1] * rot[o + 3]
    }

    /// Turns marble `i` by `angle` about the unit axis (ax, ay, az): R ← Rot·R.
    private func turn(_ i: Int, _ ax: Double, _ ay: Double, _ az: Double, _ angle: Double) {
        if angle == 0 { return }
        let c = cos(angle)
        let s = sin(angle)
        let t = 1 - c
        let r00 = t * ax * ax + c
        let r01 = t * ax * ay - s * az
        let r02 = t * ax * az + s * ay
        let r10 = t * ax * ay + s * az
        let r11 = t * ay * ay + c
        let r12 = t * ay * az - s * ax
        let r20 = t * ax * az - s * ay
        let r21 = t * ay * az + s * ax
        let r22 = t * az * az + c
        let o = i * 9
        for col in 0..<3 {
            let a = rot[o + col]
            let b = rot[o + 3 + col]
            let d = rot[o + 6 + col]
            rot[o + col] = r00 * a + r01 * b + r02 * d
            rot[o + 3 + col] = r10 * a + r11 * b + r12 * d
            rot[o + 6 + col] = r20 * a + r21 * b + r22 * d
        }
    }

    /// Eases every marble's rotation a share `k` towards `from`'s, then squares it again.
    func blendRotations(towards from: GlassMarblesTrayWorld, _ k: Double) {
        for i in 0..<rot.count { rot[i] += (from.rot[i] - rot[i]) * k }
        for i in 0..<n { orthonormalize(i * 9) }
    }

    /// Eases every marble a share `k` towards `from`'s position and speed.
    func blendPositions(towards from: GlassMarblesTrayWorld, _ k: Double) {
        for i in 0..<n {
            x[i] += (from.x[i] - x[i]) * k
            y[i] += (from.y[i] - y[i]) * k
            vx[i] += (from.vx[i] - vx[i]) * k
            vy[i] += (from.vy[i] - vy[i]) * k
            z[i] += (from.z[i] - z[i]) * k
            vz[i] += (from.vz[i] - vz[i]) * k
        }
    }

    private func addFlash(_ fx: Double, _ fy: Double, _ power: Double) {
        let f = nextFlash
        flashX[f] = fx
        flashY[f] = fy
        flashAge[f] = 0
        flashPower[f] = power
        nextFlash = (f + 1) % T.flashes
    }

    /// A shake: every marble is knocked in a random direction and hops.
    func scatter(_ strength: Double) {
        for i in 0..<n {
            let a = random() * Double.pi * 2
            let speed = strength * (220 + 260 * random())
            vx[i] += cos(a) * speed
            vy[i] += sin(a) * speed
            vz[i] = max(vz[i], strength * (160 + 180 * random()))
        }
    }

    /// A flick of marble `i` along (dx, dy) at `speed` pt/s, with a little hop.
    func flick(_ i: Int, _ dx: Double, _ dy: Double, _ speed: Double) {
        var l = hypot(dx, dy)
        if l == 0 || l.isNaN { l = 1 }
        vx[i] += (dx / l) * speed
        vy[i] += (dy / l) * speed
        vz[i] = max(vz[i], 90)
    }

    private func walls(_ tray: GlassMarblesTrayTray, _ i: Int) {
        let rad = r[i]
        let lx = tray.halfW - rad
        let ly = tray.halfH - rad
        // The rounded corners: a marble beyond a corner's centre is held on its arc.
        let cr = max(0, tray.corner - rad)
        let cx = lx - cr
        let cy = ly - cr
        let ax = abs(x[i])
        let ay = abs(y[i])
        if ax > cx && ay > cy && cr > 0 {
            let sx = sign(x[i])
            let sy = sign(y[i])
            let dx = ax - cx
            let dy = ay - cy
            let d = hypot(dx, dy)
            if d > cr {
                let nx = (dx / d) * sx
                let ny = (dy / d) * sy
                x[i] = sx * cx + nx * cr
                y[i] = sy * cy + ny * cr
                let vn = vx[i] * nx + vy[i] * ny
                if vn > 0 {
                    vx[i] -= (1 + T.wallBounce) * vn * nx
                    vy[i] -= (1 + T.wallBounce) * vn * ny
                    if vn > T.clickSpeed { addFlash(x[i] + nx * rad, y[i] + ny * rad, min(1, vn / 600)) }
                }
            }
            return
        }
        if x[i] > lx || x[i] < -lx {
            let s = sign(x[i])
            x[i] = s * lx
            if vx[i] * s > 0 {
                if abs(vx[i]) > T.clickSpeed { addFlash(s * (lx + rad), y[i], min(1, abs(vx[i]) / 600)) }
                vx[i] = -vx[i] * T.wallBounce
            }
        }
        if y[i] > ly || y[i] < -ly {
            let s = sign(y[i])
            y[i] = s * ly
            if vy[i] * s > 0 {
                if abs(vy[i]) > T.clickSpeed { addFlash(x[i], s * (ly + rad), min(1, abs(vy[i]) / 600)) }
                vy[i] = -vy[i] * T.wallBounce
            }
        }
    }

    private func collide() {
        for i in 0..<n {
            for j in (i + 1)..<max(i + 1, n) {
                let dx = x[j] - x[i]
                let dy = y[j] - y[i]
                let minD = r[i] + r[j]
                let d2 = dx * dx + dy * dy
                if d2 >= minD * minD || d2 == 0 { continue }
                // Marbles at very different heights pass over each other.
                if abs(z[i] - z[j]) > minD * 0.8 { continue }
                let d = d2.squareRoot()
                let nx = dx / d
                let ny = dy / d
                let mi = mass[i]
                let mj = mass[j]
                let total = mi + mj
                // Push apart in proportion to the other's mass.
                let overlap = minD - d
                x[i] -= nx * overlap * (mj / total)
                y[i] -= ny * overlap * (mj / total)
                x[j] += nx * overlap * (mi / total)
                y[j] += ny * overlap * (mi / total)
                let vn = (vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny
                if vn >= 0 { continue }
                let impulse = (-(1 + T.bounce) * vn) / (1 / mi + 1 / mj)
                vx[i] -= (impulse / mi) * nx
                vy[i] -= (impulse / mi) * ny
                vx[j] += (impulse / mj) * nx
                vy[j] += (impulse / mj) * ny
                if -vn > T.clickSpeed {
                    clicks += 1
                    hardest = max(hardest, -vn)
                    addFlash(x[i] + nx * r[i], y[i] + ny * r[i], min(1, -vn / 600))
                }
            }
        }
    }

    /// Gravity along the tilted floor, per unit of gravity: (sin ty, −cos ty · sin tx).
    static func floorPull(_ tiltX: Double, _ tiltY: Double) -> (x: Double, y: Double) {
        (x: sin(tiltY), y: -cos(tiltY) * sin(tiltX))
    }

    /// One tick of the tray and its marbles.
    func step(_ tray: GlassMarblesTrayTray, _ input: GlassMarblesTrayInput, _ dt: Double) {
        clicks = 0
        hardest = 0
        // The tray follows its targets on springs; the marbles feel how it accelerates.
        let tax = T.tiltK * (input.tiltX - tiltX) - T.tiltD * tiltVX
        let tay = T.tiltK * (input.tiltY - tiltY) - T.tiltD * tiltVY
        tiltVX += tax * dt
        tiltVY += tay * dt
        tiltX += tiltVX * dt
        tiltY += tiltVY * dt
        let sax = T.slideK * (input.slideX - slideX) - T.slideD * slideVX
        let say = T.slideK * (input.slideY - slideY) - T.slideD * slideVY
        slideVX += sax * dt
        slideVY += say * dt
        slideX += slideVX * dt
        slideY += slideVY * dt

        let pull = GlassMarblesTrayWorld.floorPull(tiltX, tiltY)
        let gx = T.gravity * pull.x - T.inertia * sax
        let gy = T.gravity * pull.y - T.inertia * say
        let h = dt / Double(T.substeps)
        let keep = pow(T.rollKeep, h)
        let cupK = (T.gravity * 2 * T.cupDepth) / (T.cupR * T.cupR)
        for _ in 0..<T.substeps {
            for i in 0..<n {
                var ax = 0.0
                var ay = 0.0
                let grounded = z[i] <= 0
                if grounded {
                    ax += gx
                    ay += gy
                    // The nearest cup pulls the marble to its centre; the home cup when it is being led home.
                    var best = -1
                    var bestD = T.cupR * T.cupR
                    for c in 0..<tray.cupX.count {
                        let dx = tray.cupX[c] - x[i]
                        let dy = tray.cupY[c] - y[i]
                        let d2 = dx * dx + dy * dy
                        if d2 < bestD {
                            bestD = d2
                            best = c
                        }
                    }
                    if best >= 0 && input.guide < 0.5 {
                        ax += cupK * (tray.cupX[best] - x[i]) - T.cupDamp * vx[i]
                        ay += cupK * (tray.cupY[best] - y[i]) - T.cupDamp * vy[i]
                    }
                } else {
                    // In the air: it keeps its speed and falls back.
                    vz[i] -= T.airGravity * h
                }
                if input.guide > 0 {
                    let cup = tray.home[i]
                    ax += input.guide * (T.guideK * (tray.cupX[cup] - x[i]) - T.guideD * vx[i])
                    ay += input.guide * (T.guideK * (tray.cupY[cup] - y[i]) - T.guideD * vy[i])
                }
                vx[i] += ax * h
                vy[i] += ay * h
                if grounded {
                    vx[i] *= keep
                    vy[i] *= keep
                    let speed = hypot(vx[i], vy[i])
                    if speed > 0 {
                        let slow = max(0, speed - T.rollFriction * h) / speed
                        vx[i] *= slow
                        vy[i] *= slow
                    }
                }
                let ox = x[i]
                let oy = y[i]
                x[i] += vx[i] * h
                y[i] += vy[i] * h
                if !grounded || vz[i] > 0 {
                    z[i] += vz[i] * h
                    if z[i] <= 0 {
                        let hit = -vz[i]
                        z[i] = 0
                        vz[i] = hit * T.landing > 60 ? hit * T.landing : 0
                        if hit > T.clickSpeed * 1.4 {
                            clicks += 1
                            hardest = max(hardest, hit * 0.5)
                        }
                    }
                }
                walls(tray, i)
                // Rolling turns the marble about the axis across its path, by distance over radius.
                let mx = x[i] - ox
                let my = y[i] - oy
                let dist = hypot(mx, my)
                if dist > 1e-6 && z[i] <= 0 { turn(i, -my / dist, mx / dist, 0, dist / r[i]) }
            }
            collide()
        }
        for i in 0..<n { orthonormalize(i * 9) }
        for f in 0..<T.flashes where flashAge[f] < T.flashLife { flashAge[f] += dt }
    }

    /// The tray's tilt and slide that the script asks for at loop time `t`.
    static func scriptPose(_ t: Double) -> GlassMarblesTrayInput {
        var tiltX = 0.0
        var tiltY = 0.0
        var slideX = 0.0
        if t > T.circleFrom && t < T.circleTo {
            let u = (t - T.circleFrom) / (T.circleTo - T.circleFrom)
            let bump = sin(Double.pi * u)
            let amp = T.circleTilt * (bump * bump)
            let a = Double.pi * 2 * T.circleTurns * u - Double.pi / 2
            tiltY = amp * cos(a)
            tiltX = amp * sin(a)
        }
        if t > T.shakeAt && t < T.shakeAt + T.shakeTime {
            let u = (t - T.shakeAt) / T.shakeTime
            slideX = T.shakeSlide * sin(Double.pi * 2 * T.shakeRate * (t - T.shakeAt)) * sin(Double.pi * u)
        }
        var guide = 0.0
        if t > T.guideFrom { guide = min(1, (t - T.guideFrom) / (T.guideFull - T.guideFrom)) }
        return GlassMarblesTrayInput(tiltX: tiltX, tiltY: tiltY, slideX: slideX, slideY: 0, guide: guide)
    }
}
