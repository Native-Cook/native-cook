import CoreGraphics
import Foundation

/// The stamp's matrix (stampMatrix in StampDiaryCalendar.tsx) as a 3x3 homography from the stamp's
/// own centred points to reference points: scale, spin, tilt with perspective, shear, translate.
struct StampDiaryCalendarProjection {
    /// Row-major 3x3 mapping (x, y, 1) of the stamp to (X, Y, W) in reference points.
    private(set) var m: [Double]

    init(cx: Double, cy: Double, skew k: Double, rotX: Double, rotY: Double, rotZ: Double, sx: Double, sy: Double, depth: Double) {
        let d2r = Double.pi / 180
        let ax = rotX * d2r, ay = rotY * d2r, az = rotZ * d2r
        let cX = cos(ax), sX = sin(ax), cY = cos(ay), sY = sin(ay), cZ = cos(az), sZ = sin(az)
        // R = Rx . Ry . Rz applied to (x, y, 0); then perspective; then shear; then translate.
        let r00 = cY * cZ
        let r01 = -cY * sZ
        let r10 = sX * sY * cZ + cX * sZ
        let r11 = -sX * sY * sZ + cX * cZ
        let r20 = -cX * sY * cZ + sX * sZ
        let r21 = cX * sY * sZ + sX * cZ
        let a = r00 * sx
        let b = r01 * sy
        let c = r10 * sx
        let e = r11 * sy
        let g = r20 * sx
        let h = r21 * sy
        // Perspective: w = 1 - z / depth (z towards the viewer is +).
        let pz0 = -g / depth
        let pz1 = -h / depth
        m = [
            a + cx * pz0, b + cx * pz1, cx,
            c + k * a + cy * pz0, e + k * b + cy * pz1, cy,
            pz0, pz1, 1,
        ]
    }

    static func quad(_ q: StampDiaryCalendarQuad) -> StampDiaryCalendarProjection {
        .init(cx: q.x + q.w / 2, cy: q.y + q.h / 2 + q.dy / 2, skew: q.dy / q.w, rotX: 0, rotY: 0, rotZ: 0, sx: 1, sy: 1, depth: 1e6)
    }

    func point(_ x: Double, _ y: Double) -> (x: Double, y: Double) {
        let w = m[6] * x + m[7] * y + m[8]
        return ((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w)
    }

    /// Rows of the inverse (reference -> stamp), as three SIMD3 for the shader.
    func inverseRows() -> [SIMD3<Float>] {
        let a = m[0], b = m[1], c = m[2]
        let d = m[3], e = m[4], f = m[5]
        let g = m[6], h = m[7], i = m[8]
        let det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        let s = abs(det) < 1e-12 ? 0 : 1 / det
        var inv = [Double](repeating: 0, count: 9)
        inv[0] = (e * i - f * h) * s
        inv[1] = (c * h - b * i) * s
        inv[2] = (b * f - c * e) * s
        inv[3] = (f * g - d * i) * s
        inv[4] = (a * i - c * g) * s
        inv[5] = (c * d - a * f) * s
        inv[6] = (d * h - e * g) * s
        inv[7] = (b * g - a * h) * s
        inv[8] = (a * e - b * d) * s
        var rows: [SIMD3<Float>] = []
        for row in 0..<3 {
            let o = row * 3
            rows.append(SIMD3<Float>(Float(inv[o]), Float(inv[o + 1]), Float(inv[o + 2])))
        }
        return rows
    }

    /// Bounding box in reference points of a stamp-space rectangle centred on the origin.
    func bounds(halfWidth: Double, halfHeight: Double) -> CGRect {
        var minX = Double.infinity
        var minY = Double.infinity
        var maxX = -Double.infinity
        var maxY = -Double.infinity
        let signs: [(Double, Double)] = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
        for (sx, sy) in signs {
            let p = point(sx * halfWidth, sy * halfHeight)
            minX = min(minX, p.x); maxX = max(maxX, p.x)
            minY = min(minY, p.y); maxY = max(maxY, p.y)
        }
        return CGRect(x: minX, y: minY, width: maxX - minX, height: maxY - minY)
    }
}
