import Foundation

/// The wall, its memories, the lens and the timeline of Fisheye Memory Wall. Every frame is a pure
/// function of time (autoPlay) or of the touch state.
enum FisheyeMemoryWallModel {
    // MARK: Loop

    /// One period of the autoPlay loop, in seconds.
    static let loop = 9.0

    // MARK: Wall

    /// One cell of the wall grid, the gutter between tiles, and the repeating period (7 × 10 cells).
    static let cell = 44.0
    static let gutter = 5.0
    static let columns = 7
    static let rows = 10
    static let periodW = cell * Double(columns)
    static let periodH = cell * Double(rows)
    /// Atlas pixels per point.
    static let atlasScale = 4.0

    /// The lens centre rests in the middle of the cake tile (index 11).
    static let restX = 132.0
    static let restY = 132.0

    struct Tile {
        let col: Int, row: Int, w: Int, h: Int
        let design: Int, tint: Int, flip: Bool, month: Int, day: Int

        var rect: CGRect {
            CGRect(x: Double(col) * cell + gutter / 2, y: Double(row) * cell + gutter / 2,
                   width: Double(w) * cell - gutter, height: Double(h) * cell - gutter)
        }
        var radius: Double { 5 + 2 * Double(min(w, h)) }
    }

    /// col, row, w, h, design, tint, flip, month (0 = Jan), day — packed once from a seeded greedy fill.
    static let layout: [[Int]] = [
        [0, 0, 1, 1, 0, 1, 1, 6, 15], [1, 0, 2, 1, 12, 3, 1, 6, 25], [3, 0, 1, 1, 11, 0, 0, 7, 12],
        [4, 0, 1, 1, 10, 2, 1, 2, 21], [5, 0, 2, 2, 6, 0, 1, 0, 17], [0, 1, 1, 1, 13, 2, 1, 1, 13],
        [1, 1, 1, 1, 1, 1, 1, 4, 18], [2, 1, 1, 1, 2, 0, 0, 11, 13], [3, 1, 2, 1, 3, 1, 0, 7, 17],
        [0, 2, 1, 1, 9, 1, 0, 1, 21], [1, 2, 1, 1, 7, 2, 0, 4, 16], [2, 2, 2, 2, 4, 2, 1, 4, 11],
        [4, 2, 1, 1, 5, 0, 0, 11, 22], [5, 2, 2, 2, 1, 2, 0, 11, 24], [0, 3, 2, 2, 0, 0, 1, 5, 21],
        [4, 3, 1, 1, 9, 0, 0, 9, 26], [2, 4, 1, 1, 3, 1, 1, 4, 16], [3, 4, 1, 1, 10, 0, 1, 1, 14],
        [4, 4, 1, 1, 11, 2, 1, 11, 26], [5, 4, 2, 2, 7, 0, 0, 10, 14], [0, 5, 2, 1, 5, 3, 1, 4, 8],
        [2, 5, 1, 1, 13, 3, 1, 9, 18], [3, 5, 2, 2, 8, 2, 1, 6, 7], [0, 6, 1, 1, 6, 0, 1, 0, 19],
        [1, 6, 1, 1, 4, 1, 1, 11, 27], [2, 6, 1, 2, 2, 0, 0, 7, 13], [5, 6, 2, 2, 12, 1, 0, 10, 5],
        [0, 7, 1, 1, 11, 2, 1, 6, 13], [1, 7, 1, 2, 5, 0, 1, 1, 18], [3, 7, 1, 1, 10, 1, 0, 5, 19],
        [4, 7, 1, 1, 6, 0, 1, 5, 19], [0, 8, 1, 2, 3, 1, 0, 2, 11], [2, 8, 2, 2, 13, 1, 0, 1, 5],
        [4, 8, 1, 1, 8, 3, 1, 11, 18], [5, 8, 2, 2, 2, 2, 1, 2, 18], [1, 9, 1, 1, 9, 1, 1, 11, 18],
        [4, 9, 1, 1, 12, 1, 0, 3, 15],
    ]

    static let tiles: [Tile] = layout.map {
        Tile(col: $0[0], row: $0[1], w: $0[2], h: $0[3], design: $0[4], tint: $0[5], flip: $0[6] == 1,
             month: $0[7], day: $0[8])
    }

    static let months = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]

    /// Title and place of each design.
    static let captions: [(String, String)] = [
        ("Sunday shakshuka", "Home, Alfama"),
        ("Last light", "Pelican Bay"),
        ("Ridge walk, 6.2 km", "Sella Pass"),
        ("Flat white, window seat", "Café Lune"),
        ("Mila turns seven", "Our kitchen"),
        ("Night tram home", "Old Town"),
        ("First tulips", "Market Street"),
        ("Beach day, finally", "Praia do Sol"),
        ("Golden hour", "Rooftop"),
        ("Blue hour", "Harbour walk"),
        ("A day worth marking", "Journal"),
        ("Up at dawn", "Valley of Mist"),
        ("Lemon tart, first try", "Home"),
        ("Camp under the stars", "Lake Vire"),
    ]

    /// Per-instance tints laid over a tile at 12 % (0 = none).
    static let tints: [UInt32] = [0x000000, 0xFF9A5A, 0x5AA0FF, 0xFF6F91]

    // MARK: Artwork
    //
    // Each design is a list of shape ops in a unit square (x right, y down), cover-fitted to the tile:
    // 0 gradient c1 c2 c3 (whole tile) · 1 circle x y r color alpha · 2 ellipse x y rx ry color alpha ·
    // 3 rect x y w h radius color alpha · 4 polygon color alpha x1 y1 x2 y2 … ·
    // 5 line x1 y1 x2 y2 width color alpha (round caps) · 6 blob x y r color alpha (radial fade) ·
    // 7 the tile's date (month over day).

    static let designs: [[[Double]]] = [
        // 0 shakshuka in a pan, on a wooden table
        [[0, 0xC9B79C, 0xB9A385, 0xA48C6E], [5, 0.84, 0.66, 1.05, 0.86, 0.09, 0x1E2228, 1],
         [1, 0.5, 0.53, 0.41, 0x14171C, 0.45], [1, 0.5, 0.5, 0.4, 0x2B3038, 1], [1, 0.5, 0.5, 0.34, 0xC8442C, 1],
         [6, 0.42, 0.4, 0.3, 0xE8683E, 0.6],
         [1, 0.37, 0.42, 0.095, 0xFFF6E8, 1], [1, 0.37, 0.42, 0.042, 0xF2B530, 1],
         [1, 0.61, 0.58, 0.095, 0xFFF6E8, 1], [1, 0.61, 0.58, 0.042, 0xF2B530, 1],
         [1, 0.59, 0.34, 0.028, 0x3F8A3A, 1], [1, 0.41, 0.65, 0.024, 0x3F8A3A, 1], [1, 0.68, 0.43, 0.022, 0x4FA048, 1],
         [1, 0.3, 0.58, 0.02, 0x4FA048, 1]],
        // 1 sunset over the sea
        [[0, 0x2B3A67, 0xE8735A, 0xF6B26B], [6, 0.5, 0.6, 0.45, 0xFFC27A, 0.7], [1, 0.5, 0.6, 0.15, 0xFFE3A8, 1],
         [3, 0, 0.62, 1, 0.4, 0, 0x1F3E5C, 1], [3, 0, 0.62, 1, 0.05, 0, 0x2C5476, 1],
         [3, 0.24, 0.66, 0.52, 0.022, 0.011, 0xFFD08A, 0.75], [3, 0.33, 0.72, 0.34, 0.018, 0.009, 0xFFD08A, 0.55],
         [3, 0.42, 0.78, 0.16, 0.014, 0.007, 0xFFD08A, 0.4]],
        // 2 mountain ridge
        [[0, 0x8DB8E2, 0xC6DDF0, 0xEAF2F6], [1, 0.78, 0.2, 0.07, 0xFFF7E0, 1],
         [4, 0x4B5D78, 1, 0, 0.8, 0.34, 0.3, 0.55, 0.58, 0.72, 0.4, 1, 0.76, 1, 1, 0, 1],
         [4, 0xF4F7FB, 1, 0.34, 0.3, 0.25, 0.43, 0.31, 0.41, 0.36, 0.46, 0.42, 0.4],
         [4, 0xF4F7FB, 1, 0.72, 0.4, 0.66, 0.48, 0.71, 0.47, 0.75, 0.5, 0.79, 0.47],
         [4, 0x2F4A3F, 1, 0, 0.86, 0.3, 0.68, 0.6, 0.84, 1, 0.7, 1, 1, 0, 1]],
        // 3 a flat white on a saucer
        [[0, 0xE9DCCB, 0xDCCAB4, 0xCDB79C], [2, 0.52, 0.64, 0.36, 0.14, 0xA8927A, 0.45],
         [2, 0.5, 0.6, 0.35, 0.14, 0xF7F3EE, 1], [5, 0.74, 0.5, 0.86, 0.5, 0.07, 0xFBF8F4, 1],
         [1, 0.5, 0.5, 0.26, 0xFBF8F4, 1], [1, 0.5, 0.5, 0.21, 0x8A5A3B, 1], [1, 0.5, 0.5, 0.18, 0xB07B52, 1],
         [1, 0.455, 0.47, 0.06, 0xF3E6D6, 1], [1, 0.545, 0.47, 0.06, 0xF3E6D6, 1],
         [4, 0xF3E6D6, 1, 0.398, 0.49, 0.602, 0.49, 0.5, 0.6]],
        // 4 a birthday cake with one candle
        [[0, 0xF7C9D2, 0xF2B3C2, 0xE89BB0], [6, 0.5, 0.28, 0.3, 0xFFF1C2, 0.8], [2, 0.5, 0.84, 0.42, 0.06, 0xFFFFFF, 1],
         [3, 0.2, 0.5, 0.6, 0.33, 0.05, 0xFFF4EC, 1], [3, 0.2, 0.5, 0.6, 0.09, 0.05, 0xE05A7A, 1],
         [1, 0.3, 0.585, 0.032, 0xE05A7A, 1], [1, 0.47, 0.6, 0.038, 0xE05A7A, 1], [1, 0.66, 0.585, 0.03, 0xE05A7A, 1],
         [3, 0.2, 0.7, 0.6, 0.03, 0, 0xF6B8C6, 1], [5, 0.5, 0.34, 0.5, 0.5, 0.036, 0x7FC6E8, 1],
         [2, 0.5, 0.28, 0.032, 0.055, 0xFFC74A, 1], [2, 0.5, 0.29, 0.014, 0.025, 0xFFF4D0, 1]],
        // 5 a skyline at night
        [[0, 0x0E1430, 0x1E2A5A, 0x3B3F7A], [6, 0.75, 0.22, 0.2, 0xF4F1DE, 0.35], [1, 0.75, 0.22, 0.06, 0xF4F1DE, 1],
         [3, 0, 0.55, 0.18, 0.45, 0, 0x121733, 1], [3, 0.16, 0.42, 0.16, 0.58, 0, 0x171C3D, 1],
         [3, 0.3, 0.6, 0.14, 0.4, 0, 0x10142C, 1], [3, 0.42, 0.35, 0.18, 0.65, 0, 0x1A2046, 1],
         [3, 0.58, 0.52, 0.15, 0.48, 0, 0x121733, 1], [3, 0.71, 0.45, 0.14, 0.55, 0, 0x161B3A, 1],
         [3, 0.83, 0.62, 0.17, 0.38, 0, 0x10142C, 1],
         [3, 0.2, 0.48, 0.03, 0.03, 0, 0xFFD27A, 1], [3, 0.26, 0.56, 0.03, 0.03, 0, 0xFFD27A, 0.8],
         [3, 0.47, 0.42, 0.03, 0.03, 0, 0xFFD27A, 1], [3, 0.53, 0.5, 0.03, 0.03, 0, 0xFFD27A, 0.7],
         [3, 0.47, 0.62, 0.03, 0.03, 0, 0xFFD27A, 0.9], [3, 0.62, 0.6, 0.03, 0.03, 0, 0xFFD27A, 0.8],
         [3, 0.75, 0.53, 0.03, 0.03, 0, 0xFFD27A, 1], [3, 0.88, 0.7, 0.03, 0.03, 0, 0xFFD27A, 0.8],
         [3, 0.05, 0.64, 0.03, 0.03, 0, 0xFFD27A, 0.7]],
        // 6 a tulip
        [[0, 0xF3EFD8, 0xE7E9C9, 0xD6E2B8], [6, 0.5, 0.42, 0.3, 0xFFFFFF, 0.6], [5, 0.5, 0.5, 0.5, 1.05, 0.04, 0x4E8B4A, 1],
         [4, 0x5E9E55, 1, 0.5, 0.88, 0.28, 0.62, 0.36, 0.82], [4, 0x5E9E55, 1, 0.5, 0.82, 0.74, 0.6, 0.66, 0.8],
         [1, 0.5, 0.43, 0.14, 0xE8506A, 1],
         [4, 0xE8506A, 1, 0.36, 0.44, 0.36, 0.25, 0.45, 0.35, 0.5, 0.23, 0.55, 0.35, 0.64, 0.25, 0.64, 0.44],
         [2, 0.45, 0.4, 0.03, 0.08, 0xFF8DA0, 0.7]],
        // 7 a beach umbrella
        [[0, 0x6EC0E8, 0xA9DDF0, 0xBFE6F2], [3, 0, 0.62, 1, 0.12, 0, 0x2E8FB8, 1], [3, 0, 0.72, 1, 0.28, 0, 0xF2D9A6, 1],
         [5, 0.5, 0.36, 0.56, 0.86, 0.025, 0xFFFFFF, 1],
         [4, 0xF25C54, 1, 0.16, 0.42, 0.24, 0.31, 0.36, 0.24, 0.5, 0.2, 0.64, 0.24, 0.76, 0.31, 0.84, 0.42],
         [4, 0xFFF4E6, 1, 0.42, 0.42, 0.44, 0.25, 0.5, 0.2, 0.56, 0.25, 0.58, 0.42],
         [3, 0.2, 0.82, 0.28, 0.08, 0.015, 0xF7B538, 1], [3, 0.2, 0.845, 0.28, 0.02, 0, 0xFFFFFF, 0.8]],
        // 8 golden-hour gradient
        [[0, 0xFF8A5B, 0xFFB86E, 0xFFE3A3], [6, 0.28, 0.72, 0.45, 0xFF5E7E, 0.55], [6, 0.78, 0.28, 0.4, 0xFFF1C2, 0.7]],
        // 9 blue-hour gradient
        [[0, 0x1F3B73, 0x4C6FB8, 0x9CB7E8], [6, 0.72, 0.76, 0.5, 0xB78CF2, 0.65], [6, 0.24, 0.24, 0.42, 0x7FE0E0, 0.5]],
        // 10 a date card
        [[0, 0xF4EFE6, 0xF4EFE6, 0xEAE2D3], [7]],
        // 11 a hot-air balloon at dawn
        [[0, 0xF6BF94, 0xF6D8C0, 0xE9E3E6], [6, 0.5, 0.95, 0.5, 0xFFE9B0, 0.6],
         [4, 0xC9A79E, 1, 0, 0.86, 0.35, 0.78, 0.7, 0.88, 1, 0.8, 1, 1, 0, 1],
         [1, 0.2, 0.62, 0.06, 0x4C8FD6, 1], [5, 0.2, 0.68, 0.2, 0.71, 0.01, 0x5A4636, 1],
         [5, 0.42, 0.5, 0.465, 0.66, 0.014, 0x5A4636, 1], [5, 0.58, 0.5, 0.535, 0.66, 0.014, 0x5A4636, 1],
         [1, 0.5, 0.35, 0.2, 0xE2574C, 1], [4, 0xE2574C, 1, 0.33, 0.45, 0.5, 0.56, 0.67, 0.45],
         [2, 0.5, 0.36, 0.075, 0.2, 0xF6C34A, 1], [3, 0.45, 0.65, 0.1, 0.07, 0.015, 0x7A5A3E, 1]],
        // 12 a lemon tart
        [[0, 0xBFD8D2, 0xA9CBC4, 0x93BCB4], [1, 0.52, 0.55, 0.37, 0x6F958D, 0.4], [1, 0.5, 0.52, 0.36, 0xD9A15B, 1],
         [1, 0.5, 0.52, 0.31, 0xF7D44C, 1], [2, 0.42, 0.42, 0.12, 0.06, 0xFFF0A0, 0.7],
         [1, 0.63, 0.6, 0.085, 0xFFFFFF, 1], [1, 0.63, 0.6, 0.068, 0xF2D23A, 1],
         [5, 0.63, 0.54, 0.63, 0.66, 0.006, 0xFFF6C8, 1], [5, 0.57, 0.6, 0.69, 0.6, 0.006, 0xFFF6C8, 1],
         [2, 0.39, 0.61, 0.04, 0.022, 0x5FAF6A, 1], [2, 0.44, 0.63, 0.035, 0.02, 0x4E9A5A, 1]],
        // 13 a tent under the stars
        [[0, 0x0B1026, 0x1C2550, 0x2E2F5E], [1, 0.2, 0.18, 0.012, 0xFFFFFF, 1], [1, 0.42, 0.1, 0.009, 0xFFFFFF, 0.8],
         [1, 0.66, 0.22, 0.012, 0xFFFFFF, 1], [1, 0.84, 0.12, 0.009, 0xFFFFFF, 0.8], [1, 0.32, 0.32, 0.008, 0xFFFFFF, 0.7],
         [1, 0.56, 0.36, 0.01, 0xFFFFFF, 0.9], [1, 0.9, 0.4, 0.008, 0xFFFFFF, 0.7], [1, 0.1, 0.42, 0.008, 0xFFFFFF, 0.7],
         [4, 0x151B3A, 1, 0, 0.72, 0.25, 0.55, 0.5, 0.7, 0.78, 0.5, 1, 0.68, 1, 1, 0, 1],
         [3, 0, 0.84, 1, 0.16, 0, 0x0D1430, 1], [6, 0.5, 0.78, 0.3, 0xF29E4C, 0.45],
         [4, 0xF29E4C, 1, 0.3, 0.86, 0.5, 0.52, 0.7, 0.86], [4, 0xFFE1A8, 1, 0.45, 0.86, 0.5, 0.66, 0.55, 0.86]],
    ]

    // MARK: Lens

    /// Inverse zoom at the centre: the bulge magnifies the middle by 1 + 0.6 k.
    static func centre(_ k: Double) -> Double { 1 / (1 + 0.6 * k) }

    /// Wall points per screen point along a ray at normalised radius d (wall = pan + (p − c) · f).
    static func f(_ d: Double, _ k: Double) -> Double {
        let d2 = d * d
        return centre(k) + k * (0.217 * d2 / 3 + 2.284 * d2 * d2 * d2 / 7)
    }

    // MARK: Timeline

    static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(max(x, a), b) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func wrap(_ v: Double, _ p: Double) -> Double {
        let r = v.truncatingRemainder(dividingBy: p)
        return r < 0 ? r + p : r
    }
    /// v moved by whole periods to lie within half a period of 0.
    static func nearest(_ v: Double, _ p: Double) -> Double { v - (v / p).rounded() * p }

    /// Travel 0 → 1 over u 0 → 1 like a dolly: a soft start (the first 20 %), a steady cruise, and a
    /// soft stop (the last 24 %); the speed eases in and out on half a cosine.
    static func cruise(_ u: Double) -> Double {
        let a = 0.2
        let b = 0.24
        let v = 1 / (1 - a / 2 - b / 2)
        if u <= 0 { return 0 }
        if u >= 1 { return 1 }
        if u < a { return v * (u / 2 - a / (2 * Double.pi) * sin(Double.pi * u / a)) }
        if u > 1 - b {
            let r = 1 - u
            return 1 - v * (r / 2 - b / (2 * Double.pi) * sin(Double.pi * r / b))
        }
        return v * (a / 2 + (u - a))
    }

    /// The autoPlay pose at loop time t: the pan offset from rest and the bulge strength.
    static func auto(_ t: Double) -> FisheyeMemoryWallPose {
        // Back at rest the wall is one period on, which is rest itself: use 0 so the frames match exactly.
        let travel = cruise(seg(t, 1.0, 7.6))
        let e = travel >= 1 ? 0 : travel
        // The bulge swells while the wall travels, relaxes a little past rest as it stops, and settles.
        let k = 1 + 0.42 * (easeInOut(seg(t, 1.0, 2.8)) - easeInOut(seg(t, 5.6, 7.6)))
            - 0.12 * sin(Double.pi * easeInOut(seg(t, 7.0, 8.6)))
        // The focus ring around the memory under the centre fades while the wall travels.
        let ring = 1 - easeInOut(seg(t, 1.0, 1.4)) + easeInOut(seg(t, 7.8, 8.4))
        // One whole period along x (the wall repeats), with an S-shaped bob in y.
        return FisheyeMemoryWallPose(x: periodW * e, y: 90 * sin(2 * Double.pi * e), k: k, ring: ring)
    }

    /// The memory under a wall point, and how deep (pt) the point is inside it (negative in a gutter).
    static func memory(atX x: Double, y: Double) -> (tile: Int, depth: Double) {
        let px = wrap(x, periodW)
        let py = wrap(y, periodH)
        var best = 0
        var bestDepth = -Double.infinity
        for (i, t) in tiles.enumerated() {
            let r = t.rect
            let depth = min(px - r.minX, r.maxX - px, py - r.minY, r.maxY - py)
            if depth > bestDepth {
                bestDepth = depth
                best = i
            }
        }
        return (best, bestDepth)
    }

    static func hexComponents(_ v: Double) -> (Double, Double, Double) {
        let n = UInt32(v)
        return (Double((n >> 16) & 0xFF) / 255, Double((n >> 8) & 0xFF) / 255, Double(n & 0xFF) / 255)
    }
}

/// Pan offset from rest (wall points) and bulge strength.
struct FisheyeMemoryWallPose: Equatable {
    var x = 0.0
    var y = 0.0
    var k = 1.0
    /// Opacity of the focus ring around the memory under the centre.
    var ring = 1.0
}

/// Touch, momentum and the return to rest. Not observed: the view reads it every frame.
final class FisheyeMemoryWallDriver {
    typealias M = FisheyeMemoryWallModel

    private enum Phase { case auto, touch, coast }

    private var phase = Phase.auto
    private var start: Double?
    private var lastFrame: Double?
    private var pose = FisheyeMemoryWallPose()
    // Drag and pinch.
    private var dragFrom = FisheyeMemoryWallPose()
    private var pinchFrom = 1.0
    private var dragging = false
    private var pinching = false
    // Momentum and return.
    private var releasePose = FisheyeMemoryWallPose()
    private var vx = 0.0
    private var vy = 0.0
    private var releaseAt = 0.0
    private var lastMemory = -1

    static let decay = 4.0
    static let idle = 2.0
    static let back = 1.2

    func reset() {
        phase = .auto
        start = nil
        lastFrame = nil
        pose = FisheyeMemoryWallPose()
    }

    func pose(at now: Double, auto: Bool) -> FisheyeMemoryWallPose {
        switch phase {
        case .auto:
            guard auto else { return FisheyeMemoryWallPose() }
            if start == nil { start = now }
            // A stall (the first frame compiling the shader, the app in the background) pauses the
            // clock instead of skipping part of the loop.
            if let last = lastFrame, now - last > 0.25 { start! += now - last }
            lastFrame = now
            return M.auto(M.wrap(now - start!, M.loop))
        case .touch:
            return pose
        case .coast:
            let dt = now - releaseAt
            if dt < Self.idle { return coast(dt) }
            let e = M.easeInOut(M.seg(dt, Self.idle, Self.idle + Self.back))
            if e >= 1 {
                phase = .auto
                start = now
                lastFrame = now
                return FisheyeMemoryWallPose()
            }
            // Ease from where the wall drifted to, moved by whole periods to the copy of rest nearest it.
            let from = coast(Self.idle)
            let fx = M.nearest(from.x, M.periodW)
            let fy = M.nearest(from.y, M.periodH)
            return FisheyeMemoryWallPose(x: fx * (1 - e), y: fy * (1 - e), k: from.k + (1 - from.k) * e, ring: e)
        }
    }

    /// The pose dt seconds after release: the throw decays exponentially.
    private func coast(_ dt: Double) -> FisheyeMemoryWallPose {
        let s = (1 - exp(-Self.decay * dt)) / Self.decay
        return FisheyeMemoryWallPose(x: releasePose.x + vx * s, y: releasePose.y + vy * s, k: releasePose.k, ring: 0)
    }

    /// True while something still moves without autoPlay.
    func live(at now: Double) -> Bool {
        if case .auto = phase { return false }
        return true
    }

    private func grab(at now: Double, auto: Bool) {
        if phase != .touch {
            pose = self.pose(at: now, auto: auto)
            // Keep the pan small so float precision never drifts.
            pose.x = M.nearest(pose.x, M.periodW)
            pose.y = M.nearest(pose.y, M.periodH)
            pose.ring = 0
            phase = .touch
        }
    }

    func beginDrag(at now: Double, auto: Bool) {
        grab(at: now, auto: auto)
        dragFrom = pose
        dragging = true
    }

    /// Moves the wall with the finger: the tile under the centre follows it 1 : 1.
    /// Returns true when a new memory reaches the centre.
    func drag(dx: Double, dy: Double) -> Bool {
        let z = M.centre(pose.k)
        pose.x = dragFrom.x - dx * z
        pose.y = dragFrom.y - dy * z
        return crossed()
    }

    func beginPinch(at now: Double, auto: Bool) {
        grab(at: now, auto: auto)
        pinchFrom = pose.k
        pinching = true
    }

    func pinch(_ scale: Double) {
        pose.k = M.clamp(pinchFrom * scale, 0.25, 1.8)
    }

    func endPinch(at now: Double) {
        pinching = false
        if !dragging { release(vx: 0, vy: 0, at: now) }
    }

    func endDrag(vx: Double, vy: Double, at now: Double) {
        dragging = false
        if pinching { return }
        let z = M.centre(pose.k)
        release(vx: -vx * z, vy: -vy * z, at: now)
    }

    private func release(vx: Double, vy: Double, at now: Double) {
        releasePose = pose
        self.vx = M.clamp(vx, -2400, 2400)
        self.vy = M.clamp(vy, -2400, 2400)
        releaseAt = now
        phase = .coast
    }

    private func crossed() -> Bool {
        let m = M.memory(atX: M.restX + pose.x, y: M.restY + pose.y)
        guard m.depth > 2, m.tile != lastMemory else { return false }
        lastMemory = m.tile
        return true
    }
}
