import Foundation

/// The banner: a verlet cloth of `cols` × `rows` points in 3D (x right, y down, z towards the
/// viewer, all in pt), its first column pinned to the pole. Each tick adds gravity and wind, moves
/// every free point by its own velocity (verlet: position minus last position), then pulls the
/// links back to length. Wind acts twice: as drag along the cloth towards the wind's velocity, and
/// as pressure on each triangle across its face, which is what makes a flag fly and flap. It stays
/// cheap: a modest grid, the straight links solved a few times and the diagonal and bending links
/// once per step, and tethers that stop the cloth stretching however few passes it gets. A
/// step-for-step port of the React Native simulation: plain arithmetic on preallocated buffers with
/// a fixed step, nothing allocated per step.
final class ClothFlagMeshCloth {
    static let cols = 20
    static let rows = 14
    static let count = cols * rows
    // Rest distance between neighbouring points, in pt: the banner is 285 × 195 pt.
    static let spacing = 15.0
    static let flagW = Double(cols - 1) * spacing
    static let flagH = Double(rows - 1) * spacing

    // pt/s². A little lighter than the real thing, so the fabric floats.
    private static let gravity = 1400.0
    // Wind at full strength, and what a gust of 1 adds to it, calm or not (pt/s along +x).
    private static let windSpeed = 1300.0
    private static let gustSpeed = 950.0
    // Swirl across the cloth (pt/s along z): waves that travel along the flag at `waveSpeed`.
    private static let swirl = 600.0
    private static let waveLength = 180.0
    private static let waveSpeed = 240.0
    // The readout calls full wind 22 knots.
    private static let knotsPerSpeed = 22 / windSpeed
    // Drag along the cloth towards the air's velocity (1/s) — also what slows a limp flag in still air.
    private static let skin = 2.2
    // Pressure across a triangle's face, per pt² and pt/s of wind into it.
    private static let pressure = 0.03
    // Velocity kept per step.
    private static let damping = 0.994
    // Passes per step: the straight links, then the diagonals and the bending links.
    private static let stretchPasses = 3
    private static let softPasses = 1
    // How strongly each kind of link holds: straight neighbours, diagonals, and two apart (bending).
    private static let stretch = 1.0
    private static let shear = 0.5
    private static let bend = 0.3
    // No point may be further from its neighbour toward the pole than the spacing times this.
    private static let tether = 1.03
    // A grabbed point follows the finger by this share of the gap each step, and loses depth.
    private static let grabFollow = 0.55
    private static let grabFlatten = 0.85

    private typealias C = ClothFlagMeshCloth

    private static func buffer(_ n: Int, _ value: Double = 0) -> UnsafeMutablePointer<Double> {
        let p = UnsafeMutablePointer<Double>.allocate(capacity: n)
        p.initialize(repeating: value, count: n)
        return p
    }

    let x = buffer(count), y = buffer(count), z = buffer(count)
    let px = buffer(count), py = buffer(count), pz = buffer(count)
    /// Where every point was a step ago, so a frame between steps can be drawn in between.
    let ox = buffer(count), oy = buffer(count), oz = buffer(count)
    private let ax = buffer(count), ay = buffer(count), az = buffer(count)
    /// The air's velocity at each point this step.
    private let wx = buffer(count), wy = buffer(count), wz = buffer(count)
    /// 0 for a pinned point, 1 otherwise.
    private let free = buffer(count)
    /// Links as pairs of point indices, their rest lengths and stiffness: the straight ones first.
    private let maxLinks = count * 6
    private let linkA: UnsafeMutablePointer<Int>
    private let linkB: UnsafeMutablePointer<Int>
    private let rest: UnsafeMutablePointer<Double>
    private let stiff: UnsafeMutablePointer<Double>
    private var straight = 0
    private var links = 0
    /// Where the pole holds the first column (pt).
    let originX: Double
    let originY: Double
    /// Seconds of wind so far, for the travelling swirl.
    private(set) var time = 0.0

    /// A flat banner held out from the pole at (originX, originY), its hoist edge pinned.
    init(originX: Double, originY: Double) {
        self.originX = originX
        self.originY = originY
        linkA = .allocate(capacity: maxLinks)
        linkB = .allocate(capacity: maxLinks)
        rest = .allocate(capacity: maxLinks)
        stiff = .allocate(capacity: maxLinks)
        linkA.initialize(repeating: 0, count: maxLinks)
        linkB.initialize(repeating: 0, count: maxLinks)
        rest.initialize(repeating: 0, count: maxLinks)
        stiff.initialize(repeating: 0, count: maxLinks)
        for r in 0..<C.rows {
            for c in 0..<C.cols {
                let i = r * C.cols + c
                x[i] = originX + Double(c) * C.spacing
                y[i] = originY + Double(r) * C.spacing
                // A faint, fixed ripple across the face: a perfectly flat sheet could never buckle into folds.
                z[i] = c == 0 ? 0 : 1.5 * sin(Double(c) * 1.7 + Double(r) * 0.9) * sin(Double(r) * 2.3 + 0.4)
                pz[i] = z[i]
                px[i] = x[i]
                py[i] = y[i]
                free[i] = c == 0 ? 0 : 1
            }
        }
        ox.update(from: x, count: C.count)
        oy.update(from: y, count: C.count)
        oz.update(from: z, count: C.count)
        func link(_ a: Int, _ b: Int, _ s: Double) {
            let k = links
            linkA[k] = a
            linkB[k] = b
            rest[k] = hypot(x[b] - x[a], y[b] - y[a])
            stiff[k] = s
            links = k + 1
        }
        // Straight neighbours first, row by row from the pole outwards, then the softer links.
        for r in 0..<C.rows {
            for c in 0..<C.cols {
                let i = r * C.cols + c
                if c + 1 < C.cols { link(i, i + 1, C.stretch) }
                if r + 1 < C.rows { link(i, i + C.cols, C.stretch) }
            }
        }
        straight = links
        for r in 0..<C.rows {
            for c in 0..<C.cols {
                let i = r * C.cols + c
                if c + 1 < C.cols && r + 1 < C.rows {
                    link(i, i + C.cols + 1, C.shear)
                    link(i + 1, i + C.cols, C.shear)
                }
                if c + 2 < C.cols { link(i, i + 2, C.bend) }
                if r + 2 < C.rows { link(i, i + 2 * C.cols, C.bend) }
            }
        }
    }

    deinit {
        for p in [x, y, z, px, py, pz, ox, oy, oz, ax, ay, az, wx, wy, wz, free, rest, stiff] { p.deallocate() }
        linkA.deallocate()
        linkB.deallocate()
    }

    /// Copies every point (position, velocity and last drawn position) of `from` into this cloth.
    func copy(from: ClothFlagMeshCloth) {
        for (to, src) in [(x, from.x), (y, from.y), (z, from.z), (px, from.px), (py, from.py), (pz, from.pz), (ox, from.ox), (oy, from.oy), (oz, from.oz)] {
            to.update(from: src, count: C.count)
        }
        time = from.time
    }

    /// Eases every point a share `k` (0…1) of the way to `to`'s, velocity included. Used to bring a
    /// cloth that has nearly come to rest exactly onto a known resting state.
    func blend(to: ClothFlagMeshCloth, _ k: Double) {
        for i in 0..<C.count {
            x[i] += (to.x[i] - x[i]) * k
            y[i] += (to.y[i] - y[i]) * k
            z[i] += (to.z[i] - z[i]) * k
            px[i] += (to.px[i] - px[i]) * k
            py[i] += (to.py[i] - py[i]) * k
            pz[i] += (to.pz[i] - pz[i]) * k
        }
    }

    /// Stops every point where it is.
    func still() {
        for (to, src) in [(px, x), (py, y), (pz, z), (ox, x), (oy, y), (oz, z)] {
            to.update(from: src, count: C.count)
        }
    }

    /// Pulls point `i` back to within `max` of point `j`, if it is further.
    @inline(__always) private func tetherTo(_ i: Int, _ j: Int, _ max: Double) {
        let dx = x[i] - x[j], dy = y[i] - y[j], dz = z[i] - z[j]
        let d2 = dx * dx + dy * dy + dz * dz
        if d2 <= max * max { return }
        let k = max / d2.squareRoot()
        x[i] = x[j] + dx * k
        y[i] = y[j] + dy * k
        z[i] = z[j] + dz * k
    }

    /// One step of `dt` seconds under air of strength `level` (0…1) plus `gust`. A point being
    /// dragged (`grab` ≥ 0) follows (grabX, grabY) and is held there; on release it keeps the
    /// velocity the finger gave it.
    func step(level: Double, gust: Double, dt: Double, grab: Int, grabX: Double, grabY: Double) {
        let n = C.count
        ox.update(from: x, count: n)
        oy.update(from: y, count: n)
        oz.update(from: z, count: n)
        time += dt
        let t = time

        // The air: a steady push along the flag and two travelling waves across it, at odd angles so
        // the ripples never line up into stripes; then gravity, and drag towards the air's velocity.
        let along = level * C.windSpeed + C.gustSpeed * gust
        let swirlAmp = C.swirl * (level + 0.6 * gust)
        let k1 = (2 * Double.pi) / C.waveLength
        let lift = along * 0.03
        for i in 0..<n {
            let u = x[i] - originX
            let v = y[i] - originY
            var swirl = 0.0
            if swirlAmp > 0 {
                swirl = sin(k1 * u - k1 * C.waveSpeed * t + 0.6 * sin(0.013 * v + 0.9 * t))
                    + 0.45 * sin(1.7 * k1 * u + 0.021 * v - 1.6 * k1 * C.waveSpeed * t + 1.3)
            }
            wx[i] = along
            wy[i] = lift == 0 ? 0 : lift * sin(0.8 * t + 0.01 * u)
            wz[i] = swirlAmp * swirl
            ax[i] = C.skin * (wx[i] - (x[i] - px[i]) / dt)
            ay[i] = C.gravity + C.skin * (wy[i] - (y[i] - py[i]) / dt)
            az[i] = C.skin * (wz[i] - (z[i] - pz[i]) / dt)
        }

        // Pressure on each triangle, across its face, shared by its three corners.
        if along > 1 {
            for r in 0..<(C.rows - 1) {
                for c in 0..<(C.cols - 1) {
                    let i = r * C.cols + c
                    for half in 0..<2 {
                        let a = half == 0 ? i : i + 1
                        let b = half == 0 ? i + 1 : i + C.cols + 1
                        let d = i + C.cols
                        let e1x = x[b] - x[a], e1y = y[b] - y[a], e1z = z[b] - z[a]
                        let e2x = x[d] - x[a], e2y = y[d] - y[a], e2z = z[d] - z[a]
                        // Normal scaled by twice the area.
                        let cx = e1y * e2z - e1z * e2y
                        let cy = e1z * e2x - e1x * e2z
                        let cz = e1x * e2y - e1y * e2x
                        let len = (cx * cx + cy * cy + cz * cz).squareRoot()
                        if len < 1e-6 { continue }
                        // The air against the triangle: its corners' wind less their velocity, averaged.
                        let rvx = (wx[a] + wx[b] + wx[d] - (x[a] - px[a] + x[b] - px[b] + x[d] - px[d]) / dt) / 3
                        let rvy = (wy[a] + wy[b] + wy[d] - (y[a] - py[a] + y[b] - py[b] + y[d] - py[d]) / dt) / 3
                        let rvz = (wz[a] + wz[b] + wz[d] - (z[a] - pz[a] + z[b] - pz[b] + z[d] - pz[d]) / dt) / 3
                        // (n̂ · w) n̂ · area / 3 per corner = (n · w) n / (6 |n|).
                        let push = (C.pressure * (cx * rvx + cy * rvy + cz * rvz)) / (6 * len)
                        let fx = push * cx, fy = push * cy, fz = push * cz
                        ax[a] += fx; ay[a] += fy; az[a] += fz
                        ax[b] += fx; ay[b] += fy; az[b] += fz
                        ax[d] += fx; ay[d] += fy; az[d] += fz
                    }
                }
            }
        }

        // Verlet: carry on at the last step's velocity, plus this step's push.
        let dt2 = dt * dt
        for i in 0..<n where free[i] != 0 {
            let nx = x[i] + (x[i] - px[i]) * C.damping + ax[i] * dt2
            let ny = y[i] + (y[i] - py[i]) * C.damping + ay[i] * dt2
            let nz = z[i] + (z[i] - pz[i]) * C.damping + az[i] * dt2
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i]
            x[i] = nx; y[i] = ny; z[i] = nz
        }

        // The grabbed point eases to the finger; its last position stays, so it carries the finger's speed.
        let held = grab >= 0 && grab < n && free[grab] == 1 ? grab : -1
        if held >= 0 {
            px[held] = x[held]
            py[held] = y[held]
            pz[held] = z[held]
            x[held] += (grabX - x[held]) * C.grabFollow
            y[held] += (grabY - y[held]) * C.grabFollow
            z[held] *= C.grabFlatten
        }

        // Pull the links back towards their length; a pinned or held end does not move. The straight
        // links every pass; the soft ones on the last pass only.
        let total = C.stretchPasses + C.softPasses
        for pass in 0..<total {
            let end = pass < total - C.softPasses ? straight : links
            for k in 0..<end {
                let a = linkA[k]
                let b = linkB[k]
                let wa = a == held ? 0 : free[a]
                let wb = b == held ? 0 : free[b]
                let sum = wa + wb
                if sum == 0 { continue }
                let dx = x[b] - x[a]
                let dy = y[b] - y[a]
                let dz = z[b] - z[a]
                let d = (dx * dx + dy * dy + dz * dz).squareRoot()
                if d < 1e-6 { continue }
                let diff = ((d - rest[k]) / d) * stiff[k]
                let sa = (diff * wa) / sum
                let sb = (diff * wb) / sum
                x[a] += dx * sa; y[a] += dy * sa; z[a] += dz * sa
                x[b] -= dx * sb; y[b] -= dy * sb; z[b] -= dz * sb
            }
        }

        // A held point drags the cloth with it, as a pinch does: no point may be further from it than
        // they lie apart on the flat banner, so the whole cloth drapes from the finger in broad folds
        // instead of one point being drawn out into a spike.
        if held >= 0 {
            let hc = held % C.cols
            let hr = held / C.cols
            for i in 0..<n {
                let c = i % C.cols
                if c == 0 || i == held { continue }
                let reach = C.spacing * C.tether * hypot(Double(c - hc), Double(i / C.cols - hr))
                tetherTo(i, held, reach)
            }
        }

        // Rows can not stretch: walking out from the pole, each point is kept within `tether` of the
        // one before it, and down each column within `tether` of the one above. Cheap, and it holds
        // the weight that a few passes of the links alone would not. The held point is kept too, so a
        // finger pulling past the cloth's reach draws it taut rather than tearing a point out of it.
        let max = C.spacing * C.tether
        for i in 0..<n {
            let c = i % C.cols
            if c == 0 { continue }
            tetherTo(i, i - 1, max)
            if i >= C.cols { tetherTo(i, i - C.cols, max) }
        }
    }

    /// Unit normals at every point of a mesh given as x, y, z buffers, from central differences across
    /// the grid, turned to face the viewer (+z): the back of the cloth is lit like the front.
    static func computeNormals(
        _ x: UnsafeMutablePointer<Double>, _ y: UnsafeMutablePointer<Double>, _ z: UnsafeMutablePointer<Double>,
        _ nx: UnsafeMutablePointer<Double>, _ ny: UnsafeMutablePointer<Double>, _ nz: UnsafeMutablePointer<Double>
    ) {
        for r in 0..<C.rows {
            for c in 0..<C.cols {
                let i = r * C.cols + c
                let l = c > 0 ? i - 1 : i
                let rt = c < C.cols - 1 ? i + 1 : i
                let u = r > 0 ? i - C.cols : i
                let d = r < C.rows - 1 ? i + C.cols : i
                let tx = x[rt] - x[l], ty = y[rt] - y[l], tz = z[rt] - z[l]
                let bx = x[d] - x[u], by = y[d] - y[u], bz = z[d] - z[u]
                var cx = ty * bz - tz * by
                var cy = tz * bx - tx * bz
                var cz = tx * by - ty * bx
                let len = (cx * cx + cy * cy + cz * cz).squareRoot()
                if len < 1e-9 {
                    nx[i] = 0
                    ny[i] = 0
                    nz[i] = 1
                    continue
                }
                let s = cz < 0 ? -1 / len : 1 / len
                cx *= s
                cy *= s
                cz *= s
                nx[i] = cx
                ny[i] = cy
                nz[i] = cz
            }
        }
    }

    /// Wind speed for the readout, in knots: its strength along the flag, with a little swirl.
    static func knots(level: Double, gust: Double, time: Double) -> Double {
        let ptPerSecond = level * windSpeed + gustSpeed * gust
        let knots = ptPerSecond * knotsPerSpeed
        return knots * (1 + 0.05 * sin(time * 1.7) + 0.03 * sin(time * 4.1))
    }
}
