import Foundation

// A fractal tree grown from a seed, its wind, its foliage through the seasons and what falls from
// it. A port, step for step, of the React Native `fractalTreeSim.ts`.
//
// The tree is built once from a seed: every branch splits into two (now and then three) shorter,
// thinner ones, a leader that keeps close to its parent's line and side shoots that spread, all
// leaning a little towards the light. Branches are stored parents first, so one pass lays them out.
// Growth runs depth by depth; the wind reaches the trunk first and each level after a lag, and every
// branch also sways on its own breeze. The season runs 0 spring, 1 summer, 2 autumn, 3 winter and
// back to 4; each site reads it a little late, so colours and foliage morph across the crown. Leaves
// fall and petals drift down as particles, snow falls in winter; everything is seeded and runs on a
// fixed tick.

enum FractalTreeBloomSimTuning {
    static let maxDepth = 7
    // The trunk, pt, and how much shorter and thinner each level is.
    static let trunkLength = 120.0
    static let trunkWidth = 15.0
    static let leaderRatio = 0.82
    static let sideRatio = 0.7
    static let widthRatio = 0.68
    // Spread of the side shoots, rad, and the pull of every branch back towards upright.
    static let spreadMin = 0.32
    static let spreadMax = 0.54
    static let lightPull = 0.16
    // Foliage on the last levels.
    static let leafLevels = 3
    static let maxParticles = 480
    // How far ahead of the season the earliest site runs (the latest runs exactly on it).
    static let siteLead = 0.35
    // Wind: how fast the trunk takes it up and each level after it (1/s), and how far it bends a twig.
    static let windTrunkRate = 5.0
    static let windLevelRate = 3.6
    static let bend = 0.32
    // The breeze every branch sways in: amplitude at the tips, rad, and its pace.
    static let breeze = 0.05
    static let breezeSpeed = 1.3

    // Particle kinds.
    static let petal: UInt8 = 0
    static let leaf: UInt8 = 1
    static let snow: UInt8 = 2
}

/// xorshift32 in [0, 1) on a seed held by the caller.
@inline(__always) func fractalTreeBloomRandom(_ state: inout UInt32) -> Double {
    var x = state
    x ^= x << 13
    x ^= x >> 17
    x ^= x << 5
    state = x
    return Double(x) / 4294967296
}

enum FractalTreeBloomEase {
    /// Ease out (cubic).
    static func out(_ t: Double) -> Double {
        let k = min(1, max(0, t))
        return 1 - (1 - k) * (1 - k) * (1 - k)
    }

    /// Ease in and out (cubic).
    static func inOut(_ t: Double) -> Double {
        let k = min(1, max(0, t))
        return k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2
    }

    /// Ease out with a little overshoot.
    static func outBack(_ t: Double) -> Double {
        let k = min(1, max(0, t))
        let u = k - 1
        return 1 + 2.4 * u * u * u + 1.4 * u * u
    }
}

/// The mound the tree stands on: a low hump on a flat ground line.
struct FractalTreeBloomGround {
    var base: Double
    var cx: Double
    var bump: Double
    var spread: Double

    /// Height of the ground at x.
    func y(_ x: Double) -> Double {
        let u = (x - cx) / spread
        return base - bump * exp(-u * u)
    }
}

final class FractalTreeBloomTree {
    private typealias T = FractalTreeBloomSimTuning

    private(set) var count = 0
    private(set) var parent: [Int] = []
    private(set) var depth: [Int] = []
    private(set) var length: [Double] = []
    private(set) var angle: [Double] = []
    private(set) var width: [Double] = []
    private(set) var phase: [Double] = []
    /// Laid out each frame: base, tip, absolute angle and drawn width at base and tip.
    var bx: [Double] = []
    var by: [Double] = []
    var tx: [Double] = []
    var ty: [Double] = []
    var abs: [Double] = []
    var w0: [Double] = []
    var w1: [Double] = []
    var shown: [Double] = []
    /// Foliage sites: branch, place along it (0…1), offset across it, size and look.
    private(set) var sites = 0
    private(set) var siteBranch: [Int] = []
    private(set) var siteAt: [Double] = []
    private(set) var siteSide: [Double] = []
    private(set) var siteSize: [Double] = []
    private(set) var siteSpin: [Double] = []
    /// How late the site reads the season (0…0.35), when its leaf falls, and its autumn colour (0…1).
    private(set) var siteLag: [Double] = []
    private(set) var siteFall: [Double] = []
    private(set) var siteHue: [Double] = []
    /// 0 or 1: which blossom it bears.
    private(set) var siteKind: [Int] = []
    /// Its season last tick (for leaves falling and petals dropping as it passes).
    var siteSeen: [Double] = []

    /// Builds the tree for a seed (the same seed always grows the same tree).
    init(seed: Int) {
        var state = UInt32(bitPattern: Int32(truncatingIfNeeded: seed | 1))
        let maxBranches = 1200
        parent = [Int](repeating: 0, count: maxBranches)
        depth = [Int](repeating: 0, count: maxBranches)
        length = [Double](repeating: 0, count: maxBranches)
        angle = [Double](repeating: 0, count: maxBranches)
        width = [Double](repeating: 0, count: maxBranches)
        phase = [Double](repeating: 0, count: maxBranches)
        // The trunk, slightly off upright.
        parent[0] = -1
        depth[0] = 0
        length[0] = T.trunkLength
        angle[0] = (fractalTreeBloomRandom(&state) - 0.5) * 0.08
        width[0] = T.trunkWidth
        phase[0] = 0
        var count = 1
        // Breadth first: parents always come before their children.
        var absAngle = [angle[0]]
        var i = 0
        while i < count && count < maxBranches - 3 {
            defer { i += 1 }
            let d = depth[i]
            if d >= T.maxDepth { continue }
            let three = d == 0 ? fractalTreeBloomRandom(&state) < 0.6 : fractalTreeBloomRandom(&state) < 0.06
            let n = three ? 3 : 2
            let spread = T.spreadMin + (T.spreadMax - T.spreadMin) * fractalTreeBloomRandom(&state)
            // The leader goes to one side a little, the others further the other way.
            let lean: Double = fractalTreeBloomRandom(&state) < 0.5 ? -1 : 1
            for k in 0..<n {
                let leader = k == 0
                var a: Double
                if leader {
                    a = lean * spread * (0.18 + 0.2 * fractalTreeBloomRandom(&state))
                } else if n == 2 {
                    a = -lean * spread * (0.85 + 0.3 * fractalTreeBloomRandom(&state))
                } else {
                    a = (k == 1 ? -lean : lean) * spread * (0.9 + 0.35 * fractalTreeBloomRandom(&state))
                }
                // Lean back towards the light.
                let absolute = absAngle[i] + a
                a -= absolute * T.lightPull
                let ratio = (leader ? T.leaderRatio : T.sideRatio) * (0.9 + 0.2 * fractalTreeBloomRandom(&state))
                parent[count] = i
                depth[count] = d + 1
                length[count] = length[i] * ratio
                angle[count] = a
                width[count] = width[i] * T.widthRatio * (leader ? 1 : 0.86)
                phase[count] = fractalTreeBloomRandom(&state) * Double.pi * 2
                absAngle.append(absAngle[i] + a)
                count += 1
            }
        }
        self.count = count
        // Foliage sites along the last levels.
        let maxSites = 900
        var sites = 0
        i = 0
        while i < count && sites < maxSites {
            defer { i += 1 }
            if depth[i] < T.maxDepth - T.leafLevels + 1 { continue }
            // A twig bears one at its tip; the boughs below it one now and then.
            if depth[i] < T.maxDepth && fractalTreeBloomRandom(&state) > 0.6 { continue }
            let n = depth[i] == T.maxDepth ? 2 : 1
            var k = 0
            while k < n && sites < maxSites {
                siteBranch.append(i)
                siteAt.append(depth[i] == T.maxDepth && k == 0 ? 0.85 + 0.15 * fractalTreeBloomRandom(&state) : 0.4 + 0.4 * fractalTreeBloomRandom(&state))
                siteSide.append((fractalTreeBloomRandom(&state) - 0.5) * 7)
                siteSize.append(0.75 + 0.5 * fractalTreeBloomRandom(&state))
                siteSpin.append(fractalTreeBloomRandom(&state) * Double.pi * 2)
                siteLag.append(fractalTreeBloomRandom(&state) * 0.35)
                // A few leaves let go during autumn, the rest as winter comes.
                if fractalTreeBloomRandom(&state) < 0.4 {
                    siteFall.append(1.85 + fractalTreeBloomRandom(&state) * 0.45)
                } else {
                    siteFall.append(2.3 + fractalTreeBloomRandom(&state) * 0.4)
                }
                siteHue.append(fractalTreeBloomRandom(&state))
                siteKind.append(fractalTreeBloomRandom(&state) < 0.62 ? 0 : 1)
                sites += 1
                k += 1
            }
        }
        self.sites = sites
        bx = [Double](repeating: 0, count: count)
        by = bx
        tx = bx
        ty = bx
        abs = bx
        w0 = bx
        w1 = bx
        shown = bx
        siteSeen = [Double](repeating: -1, count: sites)
    }

    /// How far a branch at `depth` has grown (0…1) at `growth` (0 … maxDepth + 1).
    static func grownAt(_ growth: Double, _ depth: Int) -> Double {
        FractalTreeBloomEase.out(growth - Double(depth))
    }

    /// Lays the tree out from its root at (x, y), bent by each level's share of the wind and its own breeze.
    func layout(_ weather: FractalTreeBloomWeather, x: Double, y: Double, growth: Double, sway: Double) {
        let t = weather.time
        let maxD = Double(T.maxDepth)
        for i in 0..<count {
            let d = depth[i]
            let p = parent[i]
            let dd = Double(d)
            let grown = Self.grownAt(growth, d)
            // Thin branches give more: the bend grows towards the tips.
            let flex = (dd + 1) / (maxD + 1)
            let lean = flex * flex * (T.bend * weather.levels[d] + T.breeze * sin(t * T.breezeSpeed * (1 + dd * 0.06) + phase[i] - dd * 0.5)) * sway
            let a = (p < 0 ? 0 : abs[p]) + angle[i] + lean * (d == 0 ? 0.25 : 1)
            abs[i] = a
            let bx = p < 0 ? x : tx[p]
            let by = p < 0 ? y : ty[p]
            let len = length[i] * grown
            self.bx[i] = bx
            self.by[i] = by
            tx[i] = bx + sin(a) * len
            ty[i] = by - cos(a) * len
            shown[i] = grown
            let w = width[i] * (0.35 + 0.65 * grown)
            w0[i] = p < 0 ? w : min(w, w1[p])
            w1[i] = w * T.widthRatio * 1.05
        }
    }

    /// Whether a site's leaf drops as a falling leaf (most do); the rest wither on the twig.
    func leafFalls(_ i: Int) -> Bool {
        siteSpin[i] < Double.pi * 1.4
    }

    /// The season (0…4) as a site reads it, wrapped into 0…4.
    static func localSeason(_ season: Double, _ lag: Double) -> Double {
        let s = season + T.siteLead - lag
        return s - 4 * (s / 4).rounded(.down)
    }
}

final class FractalTreeBloomWeather {
    /// The wind the finger (or the script) asks for, the wind now, its speed, and each level's share.
    var target = 0.0
    var wind = 0.0
    var windSpeed = 0.0
    var levels = [Double](repeating: 0, count: FractalTreeBloomSimTuning.maxDepth + 1)
    var time = 0.0
    var seed: UInt32
    var snowDue = 0.0

    init(seed: Int) {
        self.seed = UInt32(bitPattern: Int32(truncatingIfNeeded: seed))
    }

    /// One tick of the wind: it springs towards the target; each level follows the one below it, late.
    func step(_ dt: Double) {
        typealias T = FractalTreeBloomSimTuning
        time += dt
        // A soft, slightly underdamped spring, so a released bough swings back past rest once.
        let accel = 26 * (target - wind) - 6.5 * windSpeed
        windSpeed += accel * dt
        wind += windSpeed * dt
        levels[0] += (wind - levels[0]) * min(1, T.windTrunkRate * dt)
        for d in 1...T.maxDepth {
            levels[d] += (levels[d - 1] - levels[d]) * min(1, T.windLevelRate * dt)
        }
    }
}

/// A fixed pool of falling petals, leaves and snowflakes.
final class FractalTreeBloomParticles {
    private typealias T = FractalTreeBloomSimTuning
    private static let n = T.maxParticles

    var count = 0
    var kind = [UInt8](repeating: 0, count: n)
    var x = [Double](repeating: 0, count: n)
    var y = [Double](repeating: 0, count: n)
    var vx = [Double](repeating: 0, count: n)
    var vy = [Double](repeating: 0, count: n)
    var rot = [Double](repeating: 0, count: n)
    var spin = [Double](repeating: 0, count: n)
    var size = [Double](repeating: 0, count: n)
    var hue = [Double](repeating: 0, count: n)
    var phase = [Double](repeating: 0, count: n)
    /// Seconds since it came to rest on the ground (−1 while falling).
    var rest = [Double](repeating: 0, count: n)
    /// Next slot to reuse when the pool is full.
    var cursor = 0

    /// Adds a particle; when the pool is full the oldest one makes room.
    private func spawn(_ k: UInt8, _ px: Double, _ py: Double, _ s: Double, _ h: Double, _ state: inout UInt32) {
        var i = count
        if i >= T.maxParticles {
            i = cursor % T.maxParticles
            cursor = i + 1
        } else {
            count += 1
        }
        kind[i] = k
        x[i] = px
        y[i] = py
        vx[i] = (fractalTreeBloomRandom(&state) - 0.5) * 20
        vy[i] = k == T.snow ? 20 : -6 - fractalTreeBloomRandom(&state) * 10
        rot[i] = fractalTreeBloomRandom(&state) * Double.pi * 2
        spin[i] = (fractalTreeBloomRandom(&state) - 0.5) * 5
        size[i] = s
        hue[i] = h
        phase[i] = fractalTreeBloomRandom(&state) * Double.pi * 2
        rest[i] = -1
    }

    /// Leaves fall and petals drop as each site passes its moment in the season; snow falls in
    /// winter; every particle drifts down on the wind and rests on the ground a while, fading.
    func step(
        tree: FractalTreeBloomTree, weather: FractalTreeBloomWeather, season: Double, dt: Double,
        width: Double, ground: FractalTreeBloomGround, falling: Bool
    ) {
        var state = weather.seed
        for i in 0..<tree.sites {
            let s = FractalTreeBloomTree.localSeason(season, tree.siteLag[i])
            let before = tree.siteSeen[i]
            tree.siteSeen[i] = s
            if before < 0 || s < before || !falling { continue }
            let b = tree.siteBranch[i]
            if tree.shown[b] < 0.99 { continue }
            let px = tree.bx[b] + (tree.tx[b] - tree.bx[b]) * tree.siteAt[i]
            let py = tree.by[b] + (tree.ty[b] - tree.by[b]) * tree.siteAt[i]
            // A leaf lets go at its own moment in autumn.
            if tree.leafFalls(i) && before < tree.siteFall[i] && s >= tree.siteFall[i] {
                spawn(T.leaf, px, py, tree.siteSize[i], tree.siteHue[i], &state)
            }
            // About half the blossoms shed a petal as the leaves come.
            let shed = 0.5 + tree.siteHue[i] * 0.4
            if tree.siteKind[i] == 0 && tree.siteHue[i] < 0.55 && before < shed && s >= shed {
                spawn(T.petal, px, py, tree.siteSize[i], tree.siteHue[i], &state)
            }
        }
        // Snow from above the screen while it is winter.
        let s = season - 4 * (season / 4).rounded(.down)
        let snowing = falling && s > 2.85 && s < 3.85
        if snowing {
            weather.snowDue += dt * 24
            while weather.snowDue >= 1 {
                weather.snowDue -= 1
                let sx = fractalTreeBloomRandom(&state) * (width + 120) - 60
                let size = 0.5 + fractalTreeBloomRandom(&state) * 0.7
                let h = fractalTreeBloomRandom(&state)
                spawn(T.snow, sx, -10, size, h, &state)
            }
        } else {
            weather.snowDue = 0
        }
        weather.seed = state

        let wind = weather.wind
        for i in 0..<count {
            if rest[i] >= 0 {
                rest[i] += dt
                continue
            }
            let k = kind[i]
            // Each kind drifts down at its own pace, swept by the wind and fluttering side to side.
            let fall = k == T.snow ? 60 + size[i] * 30 : k == T.leaf ? 105 : 66
            let flutter: Double = k == T.snow ? 10 : 26
            let drift = wind * (k == T.snow ? 70 : 95) + sin(weather.time * 2.1 + phase[i]) * flutter
            vx[i] += (drift - vx[i]) * min(1, dt * 2.4)
            vy[i] += (fall - vy[i]) * min(1, dt * 1.8)
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            rot[i] += spin[i] * dt * (k == T.snow ? 0.3 : 1) + sin(weather.time * 3 + phase[i]) * dt
            let floor = ground.y(x[i]) - (k == T.snow ? 1 : 2)
            if y[i] >= floor {
                y[i] = floor
                rest[i] = 0
            }
        }
        // Drop what has rested long enough, keeping the pool packed.
        var n = 0
        for i in 0..<count {
            let keep = rest[i] < (kind[i] == T.snow ? 1.2 : 2) && x[i] > -80 && x[i] < width + 80
            if !keep { continue }
            if n != i {
                kind[n] = kind[i]
                x[n] = x[i]
                y[n] = y[i]
                vx[n] = vx[i]
                vy[n] = vy[i]
                rot[n] = rot[i]
                spin[n] = spin[i]
                size[n] = size[i]
                hue[n] = hue[i]
                phase[n] = phase[i]
                rest[n] = rest[i]
            }
            n += 1
        }
        count = n
    }
}
