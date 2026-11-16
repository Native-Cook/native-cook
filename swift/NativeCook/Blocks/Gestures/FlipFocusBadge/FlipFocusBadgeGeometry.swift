import Foundation

/// Row-major matrices and atlas cell placement copied from ffb-geometry.ts.
enum FlipFocusBadgeGeometry {
    typealias Mat3 = [Double]
    typealias Vec3 = [Double]
    static let d2r = Double.pi / 180
    static let inkL: [Double] = [0.62, 0.5, 0, 0.12, 0, 0, 0, 0, 0, 0.12]
    static let inkR: [Double] = [76.25, 66.12, 90.75, 92.38, 93.88, 96.75, 91.12, 84.75, 91.88, 91.62]
    static let arcShift = 90 - (239.4 + 222.3 + 360) / 2
    static func rotX(_ a: Double) -> Mat3 { let c = cos(a), s = sin(a); return [1, 0, 0, 0, c, -s, 0, s, c] }
    static func rotY(_ a: Double) -> Mat3 { let c = cos(a), s = sin(a); return [c, 0, s, 0, 1, 0, -s, 0, c] }
    static func rotZ(_ a: Double) -> Mat3 { let c = cos(a), s = sin(a); return [c, -s, 0, s, c, 0, 0, 0, 1] }
    static func mul(_ a: Mat3, _ b: Mat3) -> Mat3 {
        (0..<9).map { i in let r = i / 3 * 3, c = i % 3; return a[r] * b[c] + a[r + 1] * b[c + 3] + a[r + 2] * b[c + 6] }
    }
    static func mulV(_ m: Mat3, _ v: Vec3) -> Vec3 { (0..<3).map { i in m[i * 3] * v[0] + m[i * 3 + 1] * v[1] + m[i * 3 + 2] * v[2] } }
    static func transpose(_ m: Mat3) -> Mat3 { [m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]] }
    static func ringBasis(_ yaw: Double, _ pitch: Double) -> (Mat3, Mat3) {
        let extra = -0.28 * pitch * d2r
        let wr = mul(rotX(pitch * d2r), mul(rotY(yaw * d2r), rotX(extra)))
        let lr = mul(rotZ(14.5 * d2r), mul(rotX(-76.4 * d2r), rotZ(-arcShift * d2r)))
        return (wr, lr)
    }
    static func ringPoint(_ degrees: Double, _ yaw: Double, _ pitch: Double) -> Vec3 {
        let (wr, lr) = ringBasis(yaw, pitch), t = (degrees + arcShift) * d2r
        let p = mulV(lr, [138.1 * cos(t), 138.1 * sin(t), 0])
        return mulV(wr, [p[0] - 7.53, p[1] - 19.62, p[2]])
    }
    static func frontLayout(_ value: Double) -> [Double] {
        let v = max(0, min(99, Int(floor(value + 0.5)))), tens = v / 10, ones = v % 10
        if v < 10 {
            let w = inkR[ones] - inkL[ones], ns = min(1, 124 / w)
            let x = -w * ns / 2 - 0.85 - inkL[ones] * ns
            return [-1, Double(ones), 0, x, ns, -42.8 + (1 - ns) * 90.5 / 2]
        }
        let wt = inkR[tens] - inkL[tens], wo = inkR[ones] - inkL[ones], total = wt * 0.68 + wo
        let ns = min(1, 124 / total), left = -total * ns / 2 - 0.85
        return [Double(tens), Double(ones), left - inkL[tens] * ns, left + wt * 0.68 * ns - inkL[ones] * ns,
                ns, -42.8 + (1 - ns) * 90.5 / 2]
    }
    static func backLayout(_ seconds: Double) -> [Double] {
        let total = max(0, min(5999, Int(floor(seconds)))), m = total / 60, s = total % 60
        let cells = [m / 10, m % 10, s / 10, s % 10]
        let widths = cells.map { (inkR[$0] - inkL[$0]) * 0.38 }
        let totalWidth = widths[0] * 0.92 + widths[1] * 0.92 + 8 + widths[2] * 0.92 + widths[3]
        var x = -totalWidth / 2
        var xs: [Double] = []
        xs.append(x - inkL[cells[0]] * 0.38); x += widths[0] * 0.92
        xs.append(x - inkL[cells[1]] * 0.38); x += widths[1] * 0.92
        let colon = x + 4; x += 8
        xs.append(x - inkL[cells[2]] * 0.38); x += widths[2] * 0.92
        xs.append(x - inkL[cells[3]] * 0.38)
        return cells.map(Double.init) + xs + [colon]
    }
    /// Packed in the exact declaration order of FlipFocusBadgeShader::Badge (68 floats).
    static func uniforms(_ l: FlipFocusBadgeMotion.Look, scale: Double, displayScale: Double, reduce: Bool) -> [Float] {
        let body = transpose(mul(rotX(l.pitch * d2r), rotY(l.yaw * d2r)))
        let (wr, lr) = ringBasis(l.ringYaw, l.pitch), inv = transpose(lr)
        let ring = mul(inv, transpose(wr)), c = mulV(inv, [-7.53, -19.62, 0])
        let front = frontLayout(l.value), back = backLayout(l.clock)
        var u: [Double] = [206.5 * scale, 150 * scale, scale, 1 / (scale * displayScale)]
        u += body; u += ring; u += c
        u += ringPoint(222.3, l.ringYaw, l.pitch)
        u += ringPoint(31.4 + 360 * (l.sat - 0.4), l.ringYaw, l.pitch)
        u += Array(front[0..<4]); u += Array(back[0..<4]); u += Array(back[4..<8])
        u += [l.glintPlus, l.glintHead, l.glintSat, back[8]]
        u += [l.depth, 0.22, l.env, l.press, 1, 0, reduce ? 1 : 0, 1]
        u += [l.popPlus, l.popHr, front[4], front[5]]
        u += [143.0 / 255, 166.0 / 255, 1, 241.0 / 255, 243.0 / 255, 250.0 / 255, 26.0 / 255, 26.0 / 255, 28.0 / 255]
        return u.map(Float.init)
    }
}

struct FlipFocusBadgeLayout {
    let width: Double, height: Double, insetTop: Double, insetBottom: Double
    var scale: Double { width / 402 }
    var sheetBottom: Double { height - max(26 * scale, insetBottom + 10) }
    var sheetTop: Double { sheetBottom - 220 * scale }
    func yt(_ y: Double) -> Double { insetTop + (y - 62) * scale }
    func yc(_ y: Double) -> Double { (yt(122) + sheetTop) / 2 - 6 * scale + (y - 369) * scale }
    func ys(_ y: Double) -> Double { sheetTop + (y - 628) * scale }
    var badgeTop: Double { yc(293.5) - 150 * scale }
    func backdrop(_ yaw: Double) -> [Float] {
        let c = abs(cos(yaw * .pi / 180))
        let values: [Double] = [width, height, 3 / 255, 8 / 255, 13 / 255,
                206.5 * scale, yc(398), 95 * scale * (0.4 + 0.6 * c), 26 * scale,
                0.35 + 0.1 * c, yc(150), yc(400), 1, 0, 0, 0]
        return values.map(Float.init)
    }
}
