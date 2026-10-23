import CoreGraphics
import Foundation

/// Source-frame geometry. The hard cut and the two card passes deliberately use separate tracks.
enum NeonCardFlightMotion {
    struct Card {
        var points: [CGPoint]
        var visible = true
        var behindForeground = false
        var intro = false
        var trail = CGPoint.zero
    }
    struct Piece {
        let name: String
        let x: Double, y: Double, w: Double, h: Double
        let roll: Double, dx: Double, dy: Double, turn: Double
        let start: Double, end: Double
        let foreground: Bool
    }
    struct Pose {
        var x: Double, y: Double, scale: Double, roll: Double, alpha: Double
        var a: Double, b: Double, c: Double, d: Double, tx: Double, ty: Double
    }
    static let pieces: [Piece] = [
        Piece(name: "orange", x: 286, y: 520, w: 150, h: 160, roll: -5, dx: -5, dy: -30, turn: 30, start: 2.267, end: 3.12, foreground: false),
        Piece(name: "cap", x: 253, y: 331, w: 120, h: 110, roll: -3, dx: 42, dy: 8, turn: 19, start: 2.267, end: 3.10, foreground: false),
        Piece(name: "coin", x: 355, y: 383, w: 52, h: 52, roll: -25, dx: 0, dy: -5, turn: -80, start: 2.30, end: 3.12, foreground: false),
        Piece(name: "coin", x: 341, y: 413, w: 30, h: 30, roll: -15, dx: 0, dy: -5, turn: 70, start: 2.35, end: 3.18, foreground: false),
        Piece(name: "cup", x: 134, y: 370, w: 174, h: 180, roll: -6, dx: 0, dy: -10, turn: 25, start: 2.233, end: 2.95, foreground: false),
        Piece(name: "star", x: 51, y: 396, w: 110, h: 110, roll: -8, dx: -8, dy: 4, turn: -48, start: 3.017, end: 3.20, foreground: false),
        Piece(name: "links", x: 217, y: 480, w: 230, h: 310, roll: 0, dx: -12, dy: 34, turn: -8, start: 2.25, end: 3.20, foreground: true),
        Piece(name: "headphones", x: 92, y: 520, w: 140, h: 150, roll: -20, dx: -22, dy: 12, turn: 32, start: 2.267, end: 3.12, foreground: true),
        Piece(name: "locks", x: 295, y: 535, w: 125, h: 155, roll: 0, dx: 0, dy: -30, turn: -28, start: 2.267, end: 3.18, foreground: true),
        Piece(name: "shield", x: 179, y: 577, w: 140, h: 155, roll: -3, dx: 5, dy: -20, turn: 32, start: 2.30, end: 3.18, foreground: true)
    ]
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    static func ease(_ p: Double) -> Double { p * p * (3 - 2 * p) }
    static func out(_ p: Double) -> Double { 1 - pow(1 - p, 3) }
    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    /// Cubic Hermite interpolation through measured values; one clock drives every port.
    static func track(_ t: Double, _ keys: [[Double]], _ column: Int) -> Double {
        if t <= keys[0][0] { return keys[0][column] }
        if t >= keys[keys.count - 1][0] { return keys[keys.count - 1][column] }
        var i = 0
        while i + 1 < keys.count && keys[i + 1][0] < t { i += 1 }
        let a = keys[i], b = keys[i + 1], left = keys[max(0, i - 1)], right = keys[min(keys.count - 1, i + 2)]
        let dt = b[0] - a[0], u = (t - a[0]) / dt
        let m0 = (b[column] - left[column]) / (b[0] - left[0]) * dt
        let m1 = (right[column] - a[column]) / (right[0] - a[0]) * dt
        return (2*u*u*u - 3*u*u + 1)*a[column] + (u*u*u - 2*u*u + u)*m0 + (-2*u*u*u + 3*u*u)*b[column] + (u*u*u - u*u)*m1
    }
    // time, TLx,TLy, TRx,TRy, BRx,BRy, BLx,BLy, in normalized393×852 source coordinates.
    static let rush: [[Double]] = [
        [0,110,414,284,414,374,566,49,578], [0.1667,113,411,301,414,423,632,32,619],
        [0.3333,105,414,314,414,451,667,20,667], [0.5,108,414,327,414,475,692,6,690],
        [0.6667,102,420,333,414,500,718,-22,710], [0.8333,102,417,350,414,535,745,-40,731],
        [1,93,417,338,414,575,783,-55,772], [1.1667,84,417,334,414,625,840,-70,833],
        [1.3333,70,417,340,414,658,882,-88,880], [1.5,47,414,350,415,690,920,-110,915],
        [1.6667,25,409,357,409,725,960,-135,955], [1.8833,2,391,339,391,765,1015,-180,1015]
    ]
    static let left: [[Double]] = [
        [1.9,359,376,497,413,419,564,282,509], [1.9167,359,376,497,413,419,564,282,509], [1.9333,359,376,497,413,419,564,282,509],
        [1.95,285,347,423,394,354,544,218,480], [1.9833,246,338,383,373,325,524,189,477],
        [2.0167,244,338,380,374,320,524,189,477], [2.05,178,327,298,350,242,498,131,461],
        [2.0833,99,312,194,328,150,465,52,439], [2.1167,17,300,89,309,42,439,-24,429],
        [2.167,-180,280,-58,290,-102,421,-218,410]
    ]
    static let right: [[Double]] = [
        [2.266666667,-100,312,39,389,-70,459,-209,382], [2.283333333,-100,312,39,389,-70,459,-209,382],
        [2.30,-70,320,66,397,-20,499,-156,422], [2.3333,-70,320,66,397,-20,499,-156,422],
        [2.4167,26,376,127,420,83,508,-20,464], [2.5,112,411,204,443,169,536,77,504],
        [2.5833,223,435,322,447,310,552,210,537], [2.6667,272,444,369,449,366,544,273,540],
        [2.75,296,449,421,446,424,544,300,546], [2.867,435,449,563,446,566,544,439,546]
    ]
    // source time, centre x/y, projected width/height, roll, yaw.
    static let returning: [[Double]] = [
        [3.45,455,444,190,126,8,12], [3.5,430,444,190,126,7,10], [3.6,393,444,190,125,6,8],
        [3.7,365,444,190,123,5,6], [3.8,333,444,190,121,4,4], [3.9,294,444,190,120,3,2],
        [4,277,443,190,119,2.6,1], [4.1,257,442,188,118,2,0], [4.2,248,442,187,117,1.8,-1],
        [4.3,230,442,184,116,1.4,-2], [4.4,223,442,181,115,1,-3], [4.5,215,442,178,114,0.8,-4],
        [4.6,211,443,176,113,0.6,-4], [4.7,207,444,175,112,0.5,-4], [4.9,207,444,175,113,0.5,-4],
        [5.1,207,445,176,114,0.5,-4], [5.3,208,447,182,117,0.5,-4], [5.5,208,449,189,121,0.5,-4], [5.583,208,449,189,121,0.5,-4]
    ]
    static func points(_ s: Double, _ keys: [[Double]]) -> [CGPoint] {
        (0..<4).map { CGPoint(x: track(s, keys, $0 * 2 + 1), y: track(s, keys, $0 * 2 + 2)) }
    }
    static func quad(_ cx: Double, _ cy: Double, _ width: Double, _ height: Double, _ roll: Double, _ yaw: Double) -> [CGPoint] {
        let a = roll * .pi / 180, b = yaw * .pi / 180
        return [(-0.5,-0.5),(0.5,-0.5),(0.5,0.5),(-0.5,0.5)].map { x,y in
            let lx = x * width, perspective = 620 / (620 + lx * sin(b))
            let px = lx * cos(b) * perspective, py = y * height * perspective
            return CGPoint(x: cx + px*cos(a) - py*sin(a), y: cy + px*sin(a) + py*cos(a))
        }
    }
    static func card(_ t: Double) -> Card {
        let s = max(t - 0.65, 0)
        if s < 1.9 - 1e-9 { return Card(points: points(s, rush), intro: true) }
        let keys: [[Double]]
        let behind: Bool
        if s <= 2.167 { keys = left; behind = false }
        else if s >= 2.266666667 - 1e-9 && s < 2.867 - 1e-9 { keys = right; behind = true }
        else if s < 3.45 - 1e-9 { return Card(points: [], visible: false) }
        else {
            return Card(points: quad(track(s, returning, 1), track(s, returning, 2), track(s, returning, 3), track(s, returning, 4), track(s, returning, 5), track(s, returning, 6)))
        }
        let p = points(s, keys), previous = points(max(keys[0][0], s - 0.006), keys)
        var dx = zip(p,previous).reduce(0.0) { $0 + ($1.0.x - $1.1.x)/4 }, dy = zip(p,previous).reduce(0.0) { $0 + ($1.0.y - $1.1.y)/4 }
        let length = hypot(dx,dy)
        if length > 5 { dx *= 5/length; dy *= 5/length }
        return Card(points: p, behindForeground: behind, trail: CGPoint(x: dx,y: dy))
    }
    /// One opaque image is foreshortened continuously; two unaligned silhouettes never crossfade.
    static func pose(_ piece: Piece, _ t: Double) -> Pose {
        let s = max(t - 0.65,0), p = out(seg(s,piece.start,piece.end))
        var v = ease(seg(s,2.42,3.18))
        var sx = 1.0, sy = 1.0, shear = 0.0, axis = 0.0, pivotY = 0.0
        switch piece.name {
        case "links":
            v = ease(seg(s,2.45,3.15)); sx = mix(0.28,1,v); sy = mix(1.14,1,v)
            axis = -55; pivotY = -piece.h*0.18; shear = mix(-0.12,0,v)
        case "headphones": sx = mix(0.40,1,v); sy = mix(0.85,1,v); shear = mix(0.18,0,v)
        case "locks": sx = mix(1.15,1,v); sy = mix(0.22,1,v); shear = mix(0.35,0,v); pivotY = -piece.h*0.1
        case "shield": sx = mix(0.26,1,v); sy = mix(1.06,1,v); shear = mix(0.22,0,v)
        case "cup": sx = mix(0.88,1,v); sy = mix(0.80,1,v); shear = mix(-0.20,0,v); pivotY = -piece.h*0.1
        case "cap": sy = mix(0.28,1,v); shear = mix(0.25,0,v)
        case "star": v = ease(seg(s,3.05,3.18)); sx = mix(0.14,1,v); shear = mix(0.1,0,v)
        case "orange": sy = mix(0.65,1,v); shear = mix(-0.1,0,v)
        case "coin":
            let angle = s > 3.18 ? (s-3.18)*2 * Double.pi/1.45 + piece.y/35 : mix(1.4,0,v)
            sx = 0.2 + 0.8*abs(cos(angle))
        default: break
        }
        let angle = axis*Double.pi/180, co = cos(angle), si = sin(angle)
        let a = sx*co*co + sy*si*si, b = (sx-sy)*si*co, c = b + shear, d = sx*si*si + sy*co*co
        let life = s > 3.2 ? sin((s-3.2)*2 * Double.pi/2.4 + piece.x/80) : 0
        let drift = piece.name == "cup" ? track(s, [[3.2,0],[4.7,15],[4.9,15],[5.5,6]], 1)*p : 0
        return Pose(x: piece.x + piece.dx*(1-p), y: piece.y + piece.dy*(1-p) + drift,
                    scale: 0.001 + 0.999*pow(p,1.55), roll: piece.roll + piece.turn*(1-p) + life*0.6,
                    alpha: p > 0 ? 1 : 0, a: a,b: b,c: c,d: d,tx: -c*pivotY,ty: (1-d)*pivotY)
    }
    static let accent: [[Double]] = [[2.4,-20,620,-6],[2.8,105,615,-12],[3.1,180,600,-20],[3.45,270,540,-30],[3.7,330,472,-45],[4.1,354,400,-52],[4.7,377,340,-40],[5.4,385,340,-35]]
}
