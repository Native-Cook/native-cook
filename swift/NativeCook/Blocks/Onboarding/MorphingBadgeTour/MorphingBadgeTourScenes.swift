import Foundation

/// The artwork is one cast of `pieces` shapes. Every scene gives each of them a place, a size, a
/// corner radius and two colours, so a change of scene never cuts: each shape travels and turns
/// into what it is in the next scene.
///
/// A piece in a scene is ten numbers: x, y (from the stage's centre), width, height, corner
/// radius, opacity, the two colours of its gradient (0xRRGGBB), the gradient's direction (0 down,
/// 1 across) and the opacity of its glyph.
enum MorphingBadgeTourScenes {
    static let fields = 10

    // The cast, by place in a scene.
    static let capsule = 0
    static let token = 1
    static let tokens = 8
    static let extra = 9
    static let extras = 10
    static let hero = 19
    static let spark = 20
    static let sparks = 3
    static let diamond = 23
    static let outline = 24
    /// Not a shape: how much the discs bob (points) and breathe (share of their size) in a scene.
    static let idle = 25
    static let pieces = 26

    static let background = 0x0e0d10
    private static let green = 0x78ca4c
    private static let mint = 0x02ff9b
    private static let sky = 0x90d4f4
    private static let blue = 0x0f79fc
    private static let royal = 0x1f3dff
    private static let magenta = 0xd91efd
    private static let violet = 0xc586f9
    private static let yellow = 0xffd22d
    private static let lemon = 0xffe543
    private static let orange = 0xde9458
    private static let leaf = 0x32b951
    private static let cyan = 0x35d7e6
    static let sparkle = 0xffa62b
    static let heroRing = royal

    /// The two colours of each token, and whether its glyph is white (1) or black (0).
    private static let tokenInks = [
        [green, mint, 0], [magenta, blue, 1], [sky, leaf, 0], [lemon, mint, 0],
        [yellow, orange, 1], [blue, mint, 1], [royal, sky, 1], [violet, orange, 0],
    ]
    static let glyphWhite = tokenInks.map { $0[2] == 1 }
    private static let extraInks = [
        [magenta, violet], [blue, sky], [lemon, orange], [green, leaf], [royal, magenta],
        [mint, blue], [violet, sky], [yellow, green], [orange, magenta], [sky, royal],
    ]

    private static func disc(_ x: Double, _ y: Double, _ size: Double, _ a: Int, _ b: Int, alpha: Double = 1, glyph: Double = 0) -> [Double] {
        [x, y, size, size, size / 2, alpha, Double(a), Double(b), 0.5, glyph]
    }

    private static func plain(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double, _ alpha: Double) -> [Double] {
        [x, y, w, h, radius, alpha, 0, 0, 0, 0]
    }

    /// A scene from its parts: the capsule, where the tokens and the extras are, the hero and the rest.
    private static func scene(
        capsule: [Double], tokens: (Int) -> [Double], extras: (Int) -> [Double], hero: [Double], sparks: [[Double]],
        diamond: Double, outline: Double, idle: [Double]
    ) -> [[Double]] {
        var list = [capsule]
        list += (0..<Self.tokens).map(tokens)
        list += (0..<Self.extras).map(extras)
        list.append(plain(hero[0], hero[1], hero[2], hero[2], hero[2] / 2, hero[3]))
        list += sparks.map { plain($0[0], $0[1], $0[2], $0[2], 0, $0[3]) }
        // The diamond waits at the top of the beam and drops to its foot in the last scene.
        list.append(plain(0, diamond > 0 ? -42 : -140, 60, 60, 0, diamond))
        list.append(plain(0, 60, 172, 122, 6, outline))
        list.append([idle[0], idle[1], 0, 0, 0, 0, 0, 0, 0, 0])
        return list
    }

    private static func badge(_ index: Int, _ x: Double, _ y: Double, _ size: Double) -> [Double] {
        disc(x, y, size, tokenInks[index][0], tokenInks[index][1], alpha: 1, glyph: 1)
    }

    private static func small(_ index: Int, _ x: Double, _ y: Double, _ size: Double, _ alpha: Double) -> [Double] {
        disc(x, y, size, extraInks[index][0], extraInks[index][1], alpha: alpha)
    }

    /// Where the eight badges float round the capsule.
    private static let floating: [[Double]] = [[-118, -120], [-22, -192], [122, -150], [-142, 12], [134, 24], [-190, -62], [188, -74], [150, -218]]
    private static let sparksWelcome: [[Double]] = [[-92, -44, 18], [98, 84, 22], [66, -98, 14]]
    /// The grid's cells, as column and row from the middle one, which is the hero's.
    private static let cells: [[Double]] = [[-1, -1], [0, -1], [1, -1], [-1, 0], [1, 0], [-1, 1], [1, 1], [0, 1]]
    private static let cell = 88.0
    private static let pitch = 100.0
    private static let gridY = -10.0
    private static let scattered: [[Double]] = [
        [-108, -118, 56], [-18, -150, 44], [104, -128, 60], [-138, -22, 40], [118, -20, 48], [-112, 92, 58], [112, 96, 52], [10, 128, 36],
    ]
    private static let scatteredExtras: [[Double]] = [
        [-60, -70, 22], [52, -66, 18], [-70, 30, 26], [66, 40, 20], [-10, -78, 14],
        [0, 70, 16], [-150, -90, 18], [150, -70, 16], [-45, 150, 20], [150, 30, 24],
    ]
    private static let walletY = 60.0
    private static let stripe = 37.5
    private static let stripeInks = [[violet, yellow], [yellow, leaf], [leaf, cyan], [cyan, violet]]

    /// The hero badge on a capsule, badges floating round it.
    private static let welcome = scene(
        capsule: [0, 20, 78, 150, 39, 1, Double(0xb7c523), Double(yellow), 0, 0],
        tokens: { badge($0, floating[$0][0], floating[$0][1], 44) },
        extras: { small($0, 0, -16, 10, 0) },
        hero: [0, -16, 78, 1],
        sparks: sparksWelcome.map { [$0[0], $0[1], $0[2], 1] },
        diamond: 0, outline: 0, idle: [6, 0]
    )

    /// Three by three: the badges are the cells round the hero.
    private static let grid = scene(
        capsule: [0, gridY, 78, 0, 0, 0, Double(0xb7c523), Double(yellow), 0, 0],
        tokens: { badge($0, cells[$0][0] * pitch, gridY + cells[$0][1] * pitch, cell) },
        extras: { small($0, 0, gridY, 10, 0) },
        hero: [0, gridY, cell, 1],
        sparks: sparksWelcome.map { [$0[0], $0[1], 0, 0] },
        diamond: 0, outline: 0, idle: [0, 0.03]
    )

    /// A constellation: the badges and ten smaller discs scattered round a small hero.
    private static let constellation = scene(
        capsule: [0, 0, 78, 0, 0, 0, Double(0xb7c523), Double(yellow), 0, 0],
        tokens: { badge($0, scattered[$0][0], scattered[$0][1], scattered[$0][2]) },
        extras: { small($0, scatteredExtras[$0][0], scatteredExtras[$0][1], scatteredExtras[$0][2], 1) },
        hero: [0, 0, 40, 1],
        sparks: sparksWelcome.map { [$0[0], $0[1], 0, 0] },
        diamond: 0, outline: 0, idle: [3, 0]
    )

    /// A wallet: four badges are its stripes, a fifth its clasp, and the capsule is the beam a diamond drops along.
    private static let wallet = scene(
        capsule: [0, -70, 66, 110, 0, 1, Double(0xff8a1f), Double(yellow), 0, 0],
        tokens: { i in
            if i < 4 { return [0, walletY + (Double(i) - 1.5) * stripe, 200, stripe, 4, 1, Double(stripeInks[i][0]), Double(stripeInks[i][1]), 1, 0] }
            if i == 4 { return [88, walletY, 36, 24, 12, 1, Double(background), Double(background), 0.5, 1] }
            return disc(0, walletY, 10, tokenInks[i][0], tokenInks[i][1], alpha: 0)
        },
        extras: { small($0, 0, walletY, 6, 0) },
        hero: [0, walletY, 20, 0],
        sparks: [[-88, -62, 20, 1], [72, -24, 14, 1], [-58, -132, 12, 1]],
        diamond: 1, outline: 1, idle: [0, 0]
    )

    /// Off the stage: the capsule as a tall bar under it, the badges out past its edges.
    private static let offstage = scene(
        capsule: [0, 540, 78, 330, 39, 1, Double(0x8a3ffc), Double(cyan), 0, 0],
        tokens: { badge($0, floating[$0][0] * 2.6, floating[$0][1] * 2.6, 44) },
        extras: { small($0, 0, 414, 10, 0) },
        hero: [0, 414, 78, 1],
        sparks: sparksWelcome.map { [$0[0], $0[1], 0, 0] },
        diamond: 0, outline: 0, idle: [0, 0]
    )

    /// The scenes in order, and after them the place everything leaves to on the way back to the first.
    static let all = [welcome, grid, constellation, wallet, offstage]
    static let count = 4
    static let away = 4

    // The glyphs, in a box from -1 to 1. A part is a polygon (0, then its points), a rounded
    // rectangle (1: x, y, width, height, radius) or a circle (2: x, y, radius).
    static let glyphs: [[[Double]]] = [
        // A diamond.
        [[0, 0, -1, 0.85, 0, 0, 1, -0.85, 0]],
        // Three bars.
        [[1, -0.9, -0.85, 1.8, 0.42, 0.21], [1, -0.9, -0.21, 1.8, 0.42, 0.21], [1, -0.9, 0.43, 1.8, 0.42, 0.21]],
        // A face: two eyes and a mouth.
        [[2, -0.45, -0.3, 0.24], [2, 0.45, -0.3, 0.24], [1, -0.6, 0.3, 1.2, 0.3, 0.15]],
        // A star with four points.
        [[0, 0, -1, 0.25, -0.25, 1, 0, 0.25, 0.25, 0, 1, -0.25, 0.25, -1, 0, -0.25, -0.25]],
        // A coin, which is the dot of the wallet's clasp.
        [[2, 0, 0, 0.42]],
        // Two arrows, one up and one down.
        [
            [0, -0.45, -0.95, -0.9, -0.25, -0.6, -0.25, -0.6, 0.9, -0.3, 0.9, -0.3, -0.25, 0, -0.25],
            [0, 0.45, 0.95, 0.9, 0.25, 0.6, 0.25, 0.6, -0.9, 0.3, -0.9, 0.3, 0.25, 0, 0.25],
        ],
        // A lightning bolt.
        [[0, 0.2, -1, -0.6, 0.15, -0.05, 0.15, -0.25, 1, 0.6, -0.2, 0.05, -0.2]],
        // A speech bubble.
        [[1, -0.9, -0.75, 1.8, 1.2, 0.4], [0, -0.45, 0.3, -0.05, 0.3, -0.5, 0.95]],
    ]
    /// A glyph is this share of its disc's size, from its middle to its edge.
    static let glyphScale = 0.27

    /// A sparkle: a star with four thin points, in a box from -1 to 1.
    static let sparkShape: [Double] = [0, -1, 0.18, -0.18, 1, 0, 0.18, 0.18, 0, 1, -0.18, 0.18, -1, 0, -0.18, -0.18]

    /// The diamond's facets in a box from -1 to 1, back to front: a colour, then the points.
    static let diamondFacets: [[Double]] = [
        [Double(blue), -1, -0.35, -0.55, -0.85, 0.55, -0.85, 1, -0.35, 0, 1],
        [Double(sky), -1, -0.35, -0.55, -0.85, 0.55, -0.85, 1, -0.35],
        [Double(royal), -0.4, -0.35, 0.4, -0.35, 0, 1],
        [Double(0xddf1ff), -0.2, -0.85, 0.2, -0.85, 0.4, -0.35, -0.4, -0.35],
    ]
}

enum MorphingBadgeTourMotion {
    /// A change of scene takes `move` seconds; the pieces start `stagger` apart and land on a spring.
    static let move = 0.9
    static let stagger = 0.03
    static let stiffness = 170.0
    static let damping = 15.6
    /// The spring is taken as settled after this long.
    static let settle = 0.62
    /// Opacity, colours and glyphs change over this long, without a bounce.
    static let blend = 0.45
    /// From the last scene to the first, everything first leaves the stage: this long.
    static let leave = 0.4
    static let leaveStagger = 0.01
    /// The caption fades out in the first half of `caption` and the new one in over the second.
    static let caption = 0.5
    static let captionShift = 8.0
    /// A swipe further than this goes to the next or the previous scene.
    static let swipe = 40.0

    // At rest the discs bob or breathe and the sparkles pulse. The periods divide `loop`.
    static let bobPeriods = [2.5, 2, 10.0 / 3]
    static let breathPeriod = 2.0
    static let pulsePeriod = 1.25

    /// autoPlay: next scene at each of `nextAt`, every `loop` seconds; the last one leads back to the first.
    static let loop = 10.0
    static let nextAt = [2, 4.2, 6.2, 8.6]
    /// After a touch autoPlay waits this long before it starts over from the first scene.
    static let userHold = 3.0

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func ease(_ t: Double) -> Double {
        let u = clamp01(t)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    /// Where a spring of mass 1 that was let go at 0 is after `t` seconds, on its way to 1.
    static func spring(_ t: Double) -> Double {
        if t <= 0 { return 0 }
        if t >= settle { return 1 }
        let w = stiffness.squareRoot()
        let z = damping / (2 * w)
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
    }

    private static func mixColor(_ a: Double, _ b: Double, _ t: Double) -> Double {
        let from = Int(a)
        let to = Int(b)
        func channel(_ shift: Int) -> Int {
            let start = Double((from >> shift) & 0xff)
            return Int((start + (Double((to >> shift) & 0xff) - start) * t).rounded())
        }
        return Double((channel(16) << 16) | (channel(8) << 8) | channel(0))
    }

    /// How long the run to a scene takes when it starts by leaving the stage (`away`) or not.
    static func runLength(away: Bool) -> Double {
        away ? leave + move : move
    }

    /// Field `i` of piece `k`, `r` seconds into a run from where the pieces were (`from`, a flat list
    /// of pieces × fields numbers) to the scene `to`. With `away` set, everything first leaves the stage.
    static func pieceValue(_ from: [Double], to: Int, away: Bool, _ r: Double, _ k: Int, _ i: Int) -> Double {
        let scenes = MorphingBadgeTourScenes.all
        let start = from[k * MorphingBadgeTourScenes.fields + i]
        // The hero rides on the capsule: they leave together.
        let order = k == MorphingBadgeTourScenes.hero ? 0 : Double(k % 9)
        let colour = i == 6 || i == 7
        if away, r < leave {
            // Leaving speeds up: nothing lands.
            let u = clamp01((r - order * leaveStagger) / (leave - 8 * leaveStagger))
            let end = scenes[MorphingBadgeTourScenes.away][k][i]
            return colour ? mixColor(start, end, u * u) : start + (end - start) * u * u * u
        }
        let base = away ? scenes[MorphingBadgeTourScenes.away][k][i] : start
        let end = scenes[to][k][i]
        let t = (away ? r - leave : r) - order * stagger
        if colour { return mixColor(base, end, ease(t / blend)) }
        // Places and sizes land on the spring; the rest blends.
        let landing = i < 5 && k != MorphingBadgeTourScenes.idle
        return base + (end - base) * (landing ? spring(t) : ease(t / blend))
    }

    /// How far up a bobbing disc is, `clock` seconds after the block appeared.
    static func bob(_ k: Int, amount: Double, clock: Double) -> Double {
        amount * sin(2 * .pi * clock / bobPeriods[k % 3] + Double(k) * 1.7)
    }

    /// The size of a breathing disc at (x, y): the breath runs across the grid as a diagonal wave.
    static func breathe(x: Double, y: Double, amount: Double, clock: Double) -> Double {
        1 + amount * (0.5 + 0.5 * sin(2 * .pi * clock / breathPeriod - (x + y) * 0.006))
    }

    static func pulse(_ index: Int, clock: Double) -> Double {
        0.8 + 0.2 * sin(2 * .pi * clock / pulsePeriod + Double(index) * 2.1)
    }

    /// The caption that leaves (`leaving`) or arrives, `r` seconds into a run: opacity and offset down.
    static func captionMove(_ r: Double, leaving: Bool, calm: Bool) -> (opacity: Double, y: Double) {
        let half = caption / 2
        let shift = calm ? 0 : captionShift
        if leaving {
            let u = clamp01(r / half)
            return (1 - u, shift * u)
        }
        let u = clamp01((r - half) / half)
        return (u, shift * (1 - u))
    }
}
