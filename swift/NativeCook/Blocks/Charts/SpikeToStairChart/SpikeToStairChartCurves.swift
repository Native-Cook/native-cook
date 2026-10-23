import SwiftUI

/// The original cubic chains and their 64-sample arc tables, in the 402 × 874 design space.
enum SpikeToStairChartCurves {
    struct Sample {
        var u: Double
        var v: Double
        var x: Double
        var y: Double
        var a: Double
        var angle = 0.0
        var point: CGPoint { CGPoint(x: x, y: y) }
    }

    struct Curve {
        var points: [Sample]
        var joints: [Double]
        var path: Path
        var area: Path
    }

    struct Piece {
        let start: Double
        let end: Double
        let path: Path
        let cx: Double
        let cy: Double
        let bottom: Double
        let left: Double
        let right: Double
    }

    static let risers = [0.2, 0.405, 0.558, 0.688, 0.97]
    static let extrema = [0.28, 0.455, 0.595, 0.682, 0.75]
    static let treads = [0.13, 0.3, 0.482, 0.624, 0.8]
    static let shards: [[Double]] = [
        [332, 196, 64, 20, 1], [352, 266, 44, -35, 0.9],
        [300, 168, 30, 70, 0.8], [372, 150, 24, 10, 0.85],
        [318, 228, 18, -60, 0.8], [120, 214, 14, 30, 0.7]
    ]
    static let curves = [
        sample([
            [0.06,0.45,0.19,0.925,0.28,0.925],
            [0.345,0.925,0.37,0.52,0.455,0.52],
            [0.52,0.52,0.535,0.825,0.595,0.825],
            [0.63,0.825,0.65,0.718,0.682,0.718],
            [0.71,0.718,0.72,0.79,0.75,0.79],
            [0.81,0.79,0.87,0.42,0.957,0]
        ], limit: 0.957),
        sample([
            [0.03,0.045,0.06,0.085,0.1,0.085],
            [0.135,0.085,0.142,0.069,0.17,0.069],
            [0.21,0.069,0.185,0.24,0.235,0.24],
            [0.305,0.24,0.319,0.221,0.375,0.221],
            [0.415,0.221,0.39,0.397,0.44,0.397],
            [0.484,0.397,0.4928,0.419,0.528,0.431],
            [0.568,0.443,0.543,0.602,0.593,0.602],
            [0.6255,0.602,0.632,0.62,0.658,0.632],
            [0.698,0.644,0.673,0.835,0.723,0.835],
            [0.85,0.835,0.975,0.92,1.06,1.14],
            [1.105,1.26,1.15,1.45,1.185,1.66],
            [1.205,1.78,1.225,1.95,1.24,2.08]
        ], limit: 0.968)
    ]
    static let stairEnd = curves[1].joints[9]
    static let pieces: [Piece] = (0..<6).map { i in
        let curve = curves[0]
        let points = Array(curve.points[(i * 64)...(i * 64 + 64)])
        let ys = points.map(\.y)
        return Piece(start: curve.joints[i], end: curve.joints[i + 1], path: path(points),
                     cx: (points[0].x + points[64].x) / 2,
                     cy: (ys.min()! + ys.max()!) / 2, bottom: ys.max()!,
                     left: points[0].x, right: points[64].x)
    }
    static let grid: Path = {
        var p = Path()
        for i in 1...13 {
            let n = Double(i) * 274 / 14
            p.move(to: CGPoint(x: 64 + n, y: 236))
            p.addLine(to: CGPoint(x: 64 + n, y: 510))
            p.move(to: CGPoint(x: 64, y: 236 + n))
            p.addLine(to: CGPoint(x: 338, y: 236 + n))
        }
        return p
    }()
    static let dots: Path = {
        var p = Path()
        for i in 0..<90 {
            let x = 64 + (0.13 + 0.1 * Double(i % 9)) * 274
            let y = 510 - (0.04 + 0.1 * Double(i / 9)) * 274
            p.addEllipse(in: CGRect(x: x - 0.9, y: y - 0.9, width: 1.8, height: 1.8))
        }
        return p
    }()
    static let axes = line([CGPoint(x: 64, y: 236), CGPoint(x: 64, y: 510), CGPoint(x: 338, y: 510)])
    static let arrow: Path = {
        var p = line([.zero, CGPoint(x: -9, y: -4), CGPoint(x: -7, y: 0), CGPoint(x: -9, y: 4)])
        p.closeSubpath()
        return p
    }()
    static let glyphs = [
        line([CGPoint(x: -12,y: 7), CGPoint(x: -6,y: -7), .init(x: 0,y: 3), .init(x: 5,y: -2), .init(x: 12,y: 7)]),
        line([CGPoint(x: -12,y: 7), .init(x: -6,y: 7), .init(x: -6,y: 3), .init(x: 0,y: 3), .init(x: 0,y: -1), .init(x: 6,y: -1), .init(x: 6,y: -5), .init(x: 9,y: -5), .init(x: 12,y: -7)])
    ]

    static func clamp(_ n: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double {
        max(lo, min(hi, n))
    }

    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }

    static func nearestTread(_ u: Double) -> Double {
        treads.min(by: { abs($0 - u) < abs($1 - u) })!
    }

    static func atArc(_ d: Int, _ a: Double) -> Sample { lookup(curves[d].points, a, arc: true) }
    static func atU(_ d: Int, _ u: Double) -> Sample { lookup(curves[d].points, u, arc: false) }

    static func lookup(_ points: [Sample], _ value: Double, arc: Bool) -> Sample {
        var lo = 0
        var hi = points.count - 1
        func coordinate(_ i: Int) -> Double { arc ? points[i].a : points[i].u }
        let n = clamp(value, coordinate(0), coordinate(hi))
        while hi - lo > 1 {
            let m = (hi + lo) / 2
            if coordinate(m) < n { lo = m } else { hi = m }
        }
        let a = points[lo]
        let b = points[hi]
        let distance = coordinate(hi) - coordinate(lo)
        let p = (n - coordinate(lo)) / (distance == 0 ? 1 : distance)
        return Sample(u: mix(a.u,b.u,p), v: mix(a.v,b.v,p), x: mix(a.x,b.x,p),
                      y: mix(a.y,b.y,p), a: mix(a.a,b.a,p), angle: atan2(b.y-a.y,b.x-a.x))
    }

    static func line(_ points: [CGPoint]) -> Path {
        var p = Path()
        if let first = points.first { p.move(to: first) }
        for point in points.dropFirst() { p.addLine(to: point) }
        return p
    }

    static func path(_ samples: [Sample]) -> Path { line(samples.map(\.point)) }

    private static func sample(_ segments: [[Double]], limit: Double) -> Curve {
        var points = [Sample(u: 0, v: 0, x: 64, y: 510, a: 0)]
        var joints = [0.0]
        var start = [0.0, 0.0]
        var length = 0.0
        for s in segments {
            for j in 1...64 {
                let t = Double(j) / 64
                let q = 1 - t
                let u = q*q*q*start[0] + 3*q*q*t*s[0] + 3*q*t*t*s[2] + t*t*t*s[4]
                let v = q*q*q*start[1] + 3*q*q*t*s[1] + 3*q*t*t*s[3] + t*t*t*s[5]
                let x = 64 + u * 274
                let y = 510 - v * 274
                length += hypot(x - points.last!.x, y - points.last!.y)
                points.append(Sample(u: u, v: v, x: x, y: y, a: length))
            }
            joints.append(length)
            start = [s[4], s[5]]
        }
        for i in points.indices { points[i].a /= length }
        let end = lookup(points, limit, arc: false)
        var area = path(points.filter { $0.u <= limit } + [end])
        area.addLine(to: CGPoint(x: end.x, y: 510))
        area.addLine(to: CGPoint(x: 64, y: 510))
        area.closeSubpath()
        return Curve(points: points, joints: joints.map { $0 / length }, path: path(points), area: area)
    }
}
